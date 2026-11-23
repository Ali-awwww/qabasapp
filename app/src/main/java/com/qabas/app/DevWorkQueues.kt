package com.qabas.app

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

/**
 * قوائم عمل لوحة المطور (الدفعة 5 — قسم 3-و من تقرير الرئيسية ولوحة المطور).
 *
 * ثلاث قوائم بحالات بدل «رسائل بلا سير عمل»:
 *  1) إشراف المحتوى: ريلز بانتظار المراجعة (موافقة/رفض مع سبب).
 *  2) الدعم: محادثات `dev_chat_messages` بحالة (جديد/قيد المعالجة/تم) وأولوية.
 *  3) الأعطال: مجموعات أعطال مرتبة حسب عدد المتأثرين، بحالة (جديد/قيد الإصلاح/تم)
 *     وربط بالإصدار الذي أصلحها.
 *
 * قاعدة ذهبية (من التقرير): لا أرقام مخترعة. كل قراءة تفشل بلطف إلى قائمة
 * فارغة عند غياب الجدول أو انقطاع الشبكة، وكل عدّاد معلَّق يُحسب من صفوف
 * حقيقية. كل تغيير حالة يُسجَّل في [AuditLogger].
 *
 * البوابة: هذه الشاشات للمطورين المعتمدين فقط (رتبة سحابية عبر AdminGuard)؛
 * وRLS في الترحيل `20261122020000_dev_queues.sql` يقيّد الجداول الجديدة
 * بالمسؤولين عبر `qabas_is_support_admin()` أصلاً.
 */

// ============================================================================
// نماذج الصفوف (تطابق ترحيل 20261122020000_dev_queues.sql)
// ============================================================================

@Serializable
data class ModerationQueueRow(
    val id: String = UUID.randomUUID().toString(),
    @SerialName("reel_id") val reelId: String = "",
    @SerialName("submitted_by") val submittedBy: String? = null,
    val status: String = "pending", // pending | approved | rejected
    val reason: String? = null,
    @SerialName("reviewed_by") val reviewedBy: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class SupportMessageRow(
    val id: String = "",
    @SerialName("user_id") val userId: String? = null,
    @SerialName("user_email") val userEmail: String = "",
    val sender: String = "user",
    val message: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    /** حالة المحادثة — عمود جديد في dev_chat_messages (الترحيل). */
    val status: String = "new", // new | in_progress | done
    /** الأولوية — عمود جديد في dev_chat_messages (الترحيل). */
    val priority: String = "normal", // low | normal | high | urgent
    @SerialName("assigned_to") val assignedTo: String? = null
)

@Serializable
data class CrashQueueRow(
    val id: String = UUID.randomUUID().toString(),
    @SerialName("crash_signature") val crashSignature: String = "",
    @SerialName("affected_users") val affectedUsers: Int = 0,
    val status: String = "new", // new | fixing | fixed
    @SerialName("fixing_release") val fixingRelease: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/** محادثة دعم واحدة (خيط) مجمّعة من رسائل dev_chat_messages. */
data class SupportThread(
    val key: String,
    val userId: String?,
    val userEmail: String,
    val lastMessage: String,
    val lastAtMs: Long,
    val messageCount: Int,
    val status: String,
    val priority: String,
    val latestMessageId: String
)

/** مجموعة أعطال واحدة: توقيع + عدد المتأثرين + حالة سير العمل. */
data class CrashGroupItem(
    val signature: String,
    val affectedUsers: Int,
    val reportCount: Int,
    val latestMs: Long,
    val sampleAppVersion: String,
    val status: String,
    val fixingRelease: String?
)

/** العدّادات المعلّقة لبطاقات الشبكة الرئيسية في لوحة المطور. */
data class DevQueuePendingCounts(
    val moderationPending: Int = 0,
    val supportNew: Int = 0,
    val crashNew: Int = 0
) {
    val totalPending: Int get() = moderationPending + supportNew + crashNew
}

private const val TAG = "DevWorkQueues"

// ============================================================================
// المستودع — قراءات تفشل بلطف إلى فارغ (على نمط getMishkatDaily/getStudioJobs)
// ============================================================================

object DevWorkQueuesRepository {

    // ---- (أ) إشراف المحتوى -------------------------------------------------
    //
    // جدول `reels` بلا أي عمود حالة مراجعة (فُحصت الترحيلات وReelRow:
    // الحقول هي is_active وpublish_type وis_featured_by_dev فقط، والنشر
    // في ReelPublishManager يدرج المقطع مباشرة بلا مراجعة). لذا تعيش حالة
    // المراجعة في جدول `moderation_queue` الجديد، ويضبط إجراء المراجع
    // حالة المقطع نفسه عبر toggleReelActive: موافقة -> ظاهر (is_active=true)،
    // رفض -> مخفي (is_active=false). لا يوجد «حالة مراجعة» على المقطع نفسه
    // حتى يُضاف عمود review_status إلى reels في ترحيل لاحق.

    /** صفوف moderation_queue المعلّقة، الأقدم أولاً. غياب الجدول -> فارغ. */
    suspend fun getPendingModeration(): List<ModerationQueueRow> = runCatching {
        val client = SupabaseServices.client ?: return emptyList()
        client.postgrest.from("moderation_queue").select {
            filter { eq("status", "pending") }
            order("created_at", Order.ASCENDING)
            limit(100)
        }.decodeList<ModerationQueueRow>()
    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.onFailure { Log.w(TAG, "getPendingModeration failed: ${it.message}") }.getOrDefault(emptyList())

    /** عناوين الريلز للعرض: خريطة reel_id -> عنوان (من الريلز النشطة). */
    suspend fun getReelTitlesById(): Map<String, String> = runCatching {
        SupabaseServices.Profiles.getActiveReels(limit = 100).orEmpty()
            .associate { it.id to it.title }
    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(emptyMap())

    /**
     * يدرج في قائمة المراجعة كل ريلز نشط ليس له صف في moderation_queue بعد.
     * يُستخدم مرة واحدة بعد تطبيق الترحيل لملء القائمة بالريلز المنشورة
     * أصلاً (النشر اليوم لا يدرج صفاً تلقائياً — انظر patches.md: على مسار
     * النشر استدعاء [enqueueReelForModeration] بعد نجاح الإدراج).
     */
    suspend fun enqueueUnreviewedReels(): Int = runCatching {
        val client = SupabaseServices.client ?: return 0
        val existing = runCatching {
            client.postgrest.from("moderation_queue").select { limit(500) }
                .decodeList<ModerationQueueRow>()
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(emptyList())
        val knownIds = existing.map { it.reelId }.toSet()
        val reels = SupabaseServices.Profiles.getActiveReels(limit = 100).orEmpty()
        var inserted = 0
        reels.filter { it.id !in knownIds }.forEach { reel ->
            val row = ModerationQueueRow(
                reelId = reel.id,
                submittedBy = reel.userId.takeIf { it.isNotBlank() }
            )
            runCatching { client.postgrest.from("moderation_queue").insert(row) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                .onSuccess { inserted++ }
                .onFailure { Log.w(TAG, "enqueue reel ${reel.id} failed: ${it.message}") }
        }
        inserted
    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(0)

    /** يدرج ريلز واحداً في قائمة المراجعة (يُستدعى من مسار النشر). */
    suspend fun enqueueReelForModeration(reelId: String, submittedBy: String?): Boolean = runCatching {
        val client = SupabaseServices.client ?: return false
        client.postgrest.from("moderation_queue")
            .insert(ModerationQueueRow(reelId = reelId, submittedBy = submittedBy))
        true
    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.onFailure { Log.w(TAG, "enqueueReelForModeration failed: ${it.message}") }.getOrDefault(false)

    /**
     * إجراء المراجع. الموافقة تُظهر المقطع، والرفض يخفيه ويتطلب سبباً.
     * يُسجَّل الإجراء في التدقيق باسم الفاعل ووقته.
     */
    suspend fun reviewModeration(
        context: Context,
        queueId: String,
        reelId: String,
        approve: Boolean,
        reason: String
    ): Boolean {
        if (!approve && reason.isBlank()) return false
        val reviewer = SupabaseServices.Auth.getCurrentUserId()
        val ok = runCatching {
            val client = SupabaseServices.client ?: return false
            client.postgrest.from("moderation_queue").update(
                buildJsonObject {
                    put("status", if (approve) "approved" else "rejected")
                    put("reason", reason.trim().takeIf { it.isNotBlank() })
                    put("reviewed_by", reviewer)
                    put("reviewed_at", Instant.now().toString())
                }
            ) { filter { eq("id", queueId) } }
            // حالة المقطع نفسها يضبطها إجراء المراجع (لا عمود مراجعة في reels).
            SupabaseServices.Profiles.toggleReelActive(reelId, approve)
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.onFailure { Log.w(TAG, "reviewModeration failed: ${it.message}") }.getOrDefault(false)
        if (ok) {
            AuditLogger.log(
                context, "moderation_review",
                "reel=$reelId -> ${if (approve) "approved" else "rejected"} reason=${reason.trim()}"
            )
        }
        return ok
    }

    // ---- (ب) الدعم ----------------------------------------------------------
    //
    // dev_chat_messages موجود أصلاً (بلا status/priority). الترحيل يضيف
    // الأعمدة إليه بدل جدول حالة منفصل، وتغيير الحالة يتم حصرياً عبر
    // دالتي RPC (qabas_set_dev_chat_thread_status / qabas_set_dev_chat_message_status)
    // المحروستين بـ qabas_is_support_admin() — على نمط دوال الإدارة الموجودة
    // (qabas_edit_dev_chat_message). لا تُمنح سياسة UPDATE مباشرة.

    /** كل رسائل الدعم، ثم تُجمَّع خيوطاً حسب المستخدم. غياب الجدول -> فارغ. */
    suspend fun getSupportThreads(): List<SupportThread> = runCatching {
        val client = SupabaseServices.client ?: return emptyList()
        val rows = client.postgrest.from("dev_chat_messages").select {
            order("created_at", Order.DESCENDING)
            limit(200)
        }.decodeList<SupportMessageRow>()
        rows.groupBy { (it.userId?.takeIf { id -> id.isNotBlank() }) ?: it.userEmail.lowercase().trim() }
            .mapNotNull { (key, threadRows) ->
                val latest = threadRows.maxByOrNull { parseMs(it.createdAt) } ?: return@mapNotNull null
                // الحالة تعيش على أحدث رسالة في الخيط: رسالة مستخدم جديدة
                // تُدرج بالحالة الافتراضية 'new' فتعيد فتح الخيط تلقائياً.
                SupportThread(
                    key = key,
                    userId = latest.userId,
                    userEmail = latest.userEmail,
                    lastMessage = latest.message,
                    lastAtMs = parseMs(latest.createdAt),
                    messageCount = threadRows.size,
                    status = latest.status,
                    priority = latest.priority,
                    latestMessageId = latest.id
                )
            }
            .sortedByDescending { it.lastAtMs }
    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.onFailure { Log.w(TAG, "getSupportThreads failed: ${it.message}") }.getOrDefault(emptyList())

    /** يغيّر حالة خيط كامل (وأولويته/مسؤوله اختيارياً) ويسجّل في التدقيق. */
    suspend fun setSupportThreadStatus(
        context: Context,
        thread: SupportThread,
        status: String,
        priority: String? = null,
        assignedTo: String? = null
    ): Boolean {
        require(status in setOf("new", "in_progress", "done")) { "invalid support status" }
        if (priority != null) require(priority in setOf("low", "normal", "high", "urgent")) { "invalid priority" }
        val ok = runCatching {
            val client = SupabaseServices.client ?: return false
            val params = buildJsonObject {
                if (!thread.userId.isNullOrBlank()) {
                    put("p_user_id", thread.userId)
                } else {
                    put("p_message_id", thread.latestMessageId)
                }
                put("p_status", status)
                priority?.let { put("p_priority", it) }
                assignedTo?.let { put("p_assigned_to", it) }
            }
            val rpc = if (!thread.userId.isNullOrBlank()) {
                "qabas_set_dev_chat_thread_status"
            } else {
                "qabas_set_dev_chat_message_status"
            }
            client.postgrest.rpc(rpc, params).decodeAs<Boolean>()
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.onFailure { Log.w(TAG, "setSupportThreadStatus failed: ${it.message}") }.getOrDefault(false)
        if (ok) {
            AuditLogger.log(
                context, "support_status_change",
                "thread=${maskEmail(thread.userEmail)} -> $status priority=${priority ?: thread.priority}"
            )
        }
        return ok
    }

    // ---- (ج) الأعطال ---------------------------------------------------------
    //
    // المصدر: تقارير قياس الأعطال السحابية (dev_bug_reports حيث
    // description يحمل report_category=CRASH_TELEMETRY)، مجمّعة حسب
    // exception_type كما في CrashTelemetryCloudScreen. حالة سير العمل
    // (new/fixing/fixed) والإصدار المُصلح يعيشان في crash_queue الجديد
    // مفتاحاً بالتوقيع؛ مجموعة بلا صف تُعرض «جديدة».

    /** مجموعات الأعطال مرتبة حسب عدد المتأثرين (مستخدمون مميزون) تنازلياً. */
    suspend fun getCrashQueue(): List<CrashGroupItem> = runCatching {
        val reports = runCatching {
            SupabaseServices.Database.getCrashTelemetryReports()
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(emptyList())
        if (reports.isEmpty()) return emptyList()

        val stored = runCatching {
            val client = SupabaseServices.client ?: return@runCatching emptyMap<String, CrashQueueRow>()
            client.postgrest.from("crash_queue").select { limit(500) }
                .decodeList<CrashQueueRow>().associateBy { it.crashSignature }
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(emptyMap())

        reports.groupBy { report ->
            report.description.lineSequence()
                .firstOrNull { it.startsWith("exception_type=") }
                ?.substringAfter("=")
                ?.trim()?.takeIf { it.isNotBlank() } ?: "نوع غير محدد"
        }.map { (signature, group) ->
            val affected = group.mapNotNull { it.userId?.takeIf { id -> id.isNotBlank() } }
                .toSet().size.let { distinct ->
                    // بلا user_id في أي تقرير: المتأثرون = عدد التقارير (حد أدنى صادق).
                    if (distinct == 0) group.size else distinct
                }
            val latest = group.mapNotNull { report ->
                report.createdAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            }.maxOrNull() ?: 0L
            val row = stored[signature]
            CrashGroupItem(
                signature = signature,
                affectedUsers = affected,
                reportCount = group.size,
                latestMs = latest,
                sampleAppVersion = group.firstOrNull()?.appVersion.orEmpty(),
                status = row?.status ?: "new",
                fixingRelease = row?.fixingRelease
            )
        }.sortedByDescending { it.affectedUsers }
    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.onFailure { Log.w(TAG, "getCrashQueue failed: ${it.message}") }.getOrDefault(emptyList())

    /** يغيّر حالة مجموعة عطل ويربطها بإصدار الإصلاح، ويسجّل في التدقيق. */
    suspend fun setCrashStatus(
        context: Context,
        item: CrashGroupItem,
        status: String,
        fixingRelease: String?
    ): Boolean {
        require(status in setOf("new", "fixing", "fixed")) { "invalid crash status" }
        val ok = runCatching {
            val client = SupabaseServices.client ?: return false
            val row = CrashQueueRow(
                crashSignature = item.signature,
                affectedUsers = item.affectedUsers,
                status = status,
                fixingRelease = fixingRelease?.trim()?.takeIf { it.isNotBlank() },
                updatedAt = Instant.now().toString()
            )
            client.postgrest.from("crash_queue").upsert(row) {
                onConflict = "crash_signature"
            }
            true
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.onFailure { Log.w(TAG, "setCrashStatus failed: ${it.message}") }.getOrDefault(false)
        if (ok) {
            AuditLogger.log(
                context, "crash_status_change",
                "signature=${item.signature} -> $status fixing_release=${fixingRelease?.trim().orEmpty()}"
            )
        }
        return ok
    }

    /** العدّادات المعلّقة للشبكة الرئيسية — كل قراءة تفشل إلى صفر. */
    suspend fun loadPendingCounts(): DevQueuePendingCounts {
        val moderation = getPendingModeration().size
        val support = getSupportThreads().count { it.status == "new" }
        val crash = getCrashQueue().count { it.status == "new" }
        return DevQueuePendingCounts(moderation, support, crash)
    }

    private fun parseMs(raw: String?): Long =
        raw?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
}

// ============================================================================
// الواجهة — شاشة قوائم العمل بثلاثة تبويبات، كل بطاقة تعرض عددها المعلق
// ============================================================================

private enum class QueueTab(val label: String) {
    MODERATION("الإشراف"),
    SUPPORT("الدعم"),
    CRASH("الأعطال")
}

@Composable
fun DevWorkQueuesScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(QueueTab.MODERATION) }
    var counts by remember { mutableStateOf(DevQueuePendingCounts()) }
    var moderation by remember { mutableStateOf<List<ModerationQueueRow>>(emptyList()) }
    var reelTitles by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var support by remember { mutableStateOf<List<SupportThread>>(emptyList()) }
    var crashes by remember { mutableStateOf<List<CrashGroupItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        loading = true
        scope.launch {
            counts = DevWorkQueuesRepository.loadPendingCounts()
            moderation = DevWorkQueuesRepository.getPendingModeration()
            reelTitles = DevWorkQueuesRepository.getReelTitlesById()
            support = DevWorkQueuesRepository.getSupportThreads()
            crashes = DevWorkQueuesRepository.getCrashQueue()
            loading = false
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { refresh() }

    if (!AdminGuard.isDashboardAccessAllowed(context)) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("قوائم العمل مقفلة", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
            Text("هذه الصفحة للمطورين المعتمدين فقط (رتبة سحابية).", color = TextSecondary)
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row {
            TextButton(onClick = onBack) { Text("← رجوع") }
        }
        Text("قوائم العمل", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = TextPrimary)
        Text(
            "الإشراف ${counts.moderationPending} بانتظار المراجعة • الدعم ${counts.supportNew} جديد • الأعطال ${counts.crashNew} جديدة",
            color = TextSecondary, fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QueueTab.values().forEach { t ->
                val pending = when (t) {
                    QueueTab.MODERATION -> counts.moderationPending
                    QueueTab.SUPPORT -> counts.supportNew
                    QueueTab.CRASH -> counts.crashNew
                }
                if (t == tab) {
                    Button(onClick = {}) { Text("${t.label} ($pending)") }
                } else {
                    OutlinedButton(onClick = { tab = t }) { Text("${t.label} ($pending)") }
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = ::refresh, enabled = !loading) {
                Text(if (loading) "تحديث…" else "تحديث")
            }
        }
        notice?.let { Text(it, color = ThemeWarning, fontSize = 12.sp) }
        Spacer(Modifier.height(8.dp))
        when (tab) {
            QueueTab.MODERATION -> ModerationQueueContent(
                items = moderation,
                reelTitles = reelTitles,
                onReviewed = { refresh() },
                onBackfill = {
                    scope.launch {
                        val n = DevWorkQueuesRepository.enqueueUnreviewedReels()
                        notice = if (n > 0) "أُدرج $n ريلز في قائمة المراجعة." else "لا ريلز غير مراجَع لإدراجه (أو الجدول غير مطبَّق بعد)."
                        refresh()
                    }
                }
            )
            QueueTab.SUPPORT -> SupportQueueContent(items = support, onChanged = { refresh() })
            QueueTab.CRASH -> CrashQueueContent(items = crashes, onChanged = { refresh() })
        }
    }
}

@Composable
private fun ModerationQueueContent(
    items: List<ModerationQueueRow>,
    reelTitles: Map<String, String>,
    onReviewed: () -> Unit,
    onBackfill: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedButton(onClick = onBackfill, modifier = Modifier.fillMaxWidth()) {
                Text("إدراج الريلز المنشورة غير المراجَعة في القائمة")
            }
            Text(
                "الريلز بلا عمود حالة مراجعة في جدول reels؛ الحالة هنا في moderation_queue، " +
                    "وإجراء المراجع يضبط ظهور المقطع نفسه (موافقة ← ظاهر، رفض ← مخفي).",
                color = TextSecondary, fontSize = 11.sp
            )
        }
        if (items.isEmpty()) {
            item { Text("لا ريلز بانتظار المراجعة.", color = TextSecondary) }
        }
        items(items, key = { it.id }) { item ->
            var reason by remember(item.id) { mutableStateOf("") }
            var busy by remember(item.id) { mutableStateOf(false) }
            Card(colors = CardDefaults.cardColors(containerColor = ThemeSecondarySurface)) {
                Column(Modifier.padding(12.dp)) {
                    Text(reelTitles[item.reelId] ?: "ريلز ${item.reelId}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("معرّف المقطع: ${item.reelId}", color = TextSecondary, fontSize = 11.sp)
                    item.submittedBy?.let { Text("أرسله: ${maskEmail(it)}", color = TextSecondary, fontSize = 11.sp) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("سبب القرار (إلزامي عند الرفض)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                busy = true
                                scope.launch {
                                    DevWorkQueuesRepository.reviewModeration(context, item.id, item.reelId, true, reason)
                                    busy = false
                                    onReviewed()
                                }
                            },
                            enabled = !busy
                        ) { Text("موافقة") }
                        OutlinedButton(
                            onClick = {
                                busy = true
                                scope.launch {
                                    DevWorkQueuesRepository.reviewModeration(context, item.id, item.reelId, false, reason)
                                    busy = false
                                    onReviewed()
                                }
                            },
                            enabled = !busy && reason.isNotBlank()
                        ) { Text("رفض") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SupportQueueContent(items: List<SupportThread>, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (items.isEmpty()) {
            item { Text("لا محادثات دعم.", color = TextSecondary) }
        }
        items(items, key = { it.key }) { thread ->
            var busy by remember(thread.key) { mutableStateOf(false) }
            Card(colors = CardDefaults.cardColors(containerColor = ThemeSecondarySurface)) {
                Column(Modifier.padding(12.dp)) {
                    MaskedEmailText(email = thread.userEmail, reason = "عرض خيط دعم في قائمة العمل")
                    Text(thread.lastMessage, color = TextPrimary, fontSize = 13.sp, maxLines = 3)
                    Text(
                        "الحالة: ${supportStatusLabel(thread.status)} • الأولوية: ${priorityLabel(thread.priority)} • ${thread.messageCount} رسالة",
                        color = TextSecondary, fontSize = 11.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                busy = true
                                scope.launch {
                                    DevWorkQueuesRepository.setSupportThreadStatus(context, thread, "in_progress")
                                    busy = false
                                    onChanged()
                                }
                            },
                            enabled = !busy && thread.status != "in_progress"
                        ) { Text("قيد المعالجة") }
                        Button(
                            onClick = {
                                busy = true
                                scope.launch {
                                    DevWorkQueuesRepository.setSupportThreadStatus(context, thread, "done")
                                    busy = false
                                    onChanged()
                                }
                            },
                            enabled = !busy && thread.status != "done"
                        ) { Text("تمت المعالجة") }
                        OutlinedButton(
                            onClick = {
                                busy = true
                                scope.launch {
                                    val next = when (thread.priority) {
                                        "normal" -> "high"
                                        "high" -> "urgent"
                                        "urgent" -> "low"
                                        else -> "normal"
                                    }
                                    DevWorkQueuesRepository.setSupportThreadStatus(context, thread, thread.status, priority = next)
                                    busy = false
                                    onChanged()
                                }
                            },
                            enabled = !busy
                        ) { Text("الأولوية: ${priorityLabel(thread.priority)}") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CrashQueueContent(items: List<CrashGroupItem>, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(
                "مرتبة حسب عدد المتأثرين. المجموعات من تقارير قياس الأعطال السحابية " +
                    "(dev_bug_reports)؛ مجموعة بلا صف في crash_queue تُعرض «جديدة».",
                color = TextSecondary, fontSize = 11.sp
            )
        }
        if (items.isEmpty()) {
            item { Text("لا مجموعات أعطال مسجلة.", color = TextSecondary) }
        }
        items(items, key = { it.signature }) { item ->
            var release by remember(item.signature) { mutableStateOf(item.fixingRelease.orEmpty()) }
            var busy by remember(item.signature) { mutableStateOf(false) }
            Card(colors = CardDefaults.cardColors(containerColor = ThemeSecondarySurface)) {
                Column(Modifier.padding(12.dp)) {
                    Text(item.signature, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "${item.affectedUsers} مستخدم متأثر • ${item.reportCount} تقرير" +
                            (if (item.sampleAppVersion.isNotBlank()) " • إصدار ${item.sampleAppVersion}" else "") +
                            " • الحالة: ${crashStatusLabel(item.status)}" +
                            (item.fixingRelease?.let { " • الإصلاح في $it" } ?: ""),
                        color = TextSecondary, fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = release,
                        onValueChange = { release = it },
                        label = { Text("إصدار الإصلاح (مثال: v1.3.8)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                busy = true
                                scope.launch {
                                    DevWorkQueuesRepository.setCrashStatus(context, item, "fixing", release)
                                    busy = false
                                    onChanged()
                                }
                            },
                            enabled = !busy && item.status != "fixing"
                        ) { Text("قيد الإصلاح") }
                        Button(
                            onClick = {
                                busy = true
                                scope.launch {
                                    DevWorkQueuesRepository.setCrashStatus(context, item, "fixed", release)
                                    busy = false
                                    onChanged()
                                }
                            },
                            enabled = !busy && item.status != "fixed"
                        ) { Text("تم الإصلاح") }
                    }
                }
            }
        }
    }
}

private fun supportStatusLabel(status: String): String = when (status) {
    "new" -> "جديد"
    "in_progress" -> "قيد المعالجة"
    "done" -> "تمت المعالجة"
    else -> status
}

private fun priorityLabel(priority: String): String = when (priority) {
    "low" -> "منخفضة"
    "normal" -> "عادية"
    "high" -> "عالية"
    "urgent" -> "عاجلة"
    else -> priority
}

private fun crashStatusLabel(status: String): String = when (status) {
    "new" -> "جديد"
    "fixing" -> "قيد الإصلاح"
    "fixed" -> "تم الإصلاح"
    else -> status
}
