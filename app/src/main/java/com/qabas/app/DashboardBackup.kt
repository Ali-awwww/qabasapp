package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class BackupRestoreCounts(
    val configOk: Boolean,
    val promoCount: Int,
    val giftCount: Int,
    val userCount: Int,
    /** موقوفون في الملف — وحدهم من يُستعاد شيء لهم (الإيقاف عبر RPC الخادم). */
    val suspendedCount: Int
)

/** نسخة مُتحقق منها بانتظار التأكيد: نص JSON نفسه لا URI يُعاد قراءته. */
private data class PendingRestore(val json: String, val counts: BackupRestoreCounts)

/**
 * قسم النسخ الاحتياطي والاسترجاع (DashboardBackup):
 * - تصدير JSON للمستخدمين + الأكواد + الإعدادات.
 * - استيراد JSON واستعادة الإعدادات + الأكواد + المستخدمين مع معاينة وتأكيد.
 * - نسخ تلقائي يومي صامت إلى مجلد التطبيق الخاص (AutoBackup).
 */
@Composable
fun DashboardBackupSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var lastExportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var isRestoring by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<PendingRestore?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            isExporting = true
            lastExportUri = uri
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val (json, counts) = readBackupForRestore(context, uri)
            if (json == null || counts == null) {
                Toast.makeText(context, "الملف غير صالح أو ليس نسخة احتياطية خادم المسلم", Toast.LENGTH_LONG).show()
                return@launch
            }
            pendingRestore = PendingRestore(json, counts)
        }
    }

    LaunchedEffect(isExporting, lastExportUri) {
        if (isExporting && lastExportUri != null) {
            val uri = lastExportUri!!
            // فشل التصدير كان يمرّ صامتاً: دفق null يُظهر «نجاحاً» زائفاً،
            // واستثناء في البناء/الكتابة يترك isExporting=true فيتعطّل الزر
            // نهائياً (وقد ينهار التأثير بلا التقاط). الآن نجاح حقيقي فقط.
            var ok = false
            var usersWarn = false
            try {
                val (json, usersFetched) = buildExportJson(context)
                usersWarn = !usersFetched
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: throw java.io.IOException("تعذّر فتح ملف التصدير")
                    stream.use { it.write(json.toByteArray()) }
                }
                ok = true
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                android.util.Log.e("DashboardBackup", "export failed", e)
            } finally {
                isExporting = false
            }
            if (ok) {
                Toast.makeText(
                    context,
                    if (usersWarn) "تم التصدير مع تحذير: تعذّر جلب المستخدمين — النسخة بلا مستخدمين"
                    else "تم التصدير بنجاح ✅",
                    Toast.LENGTH_LONG
                ).show()
                AuditLogger.log(context, "backup_export", "تصدير نسخة احتياطية كاملة JSON")
            } else {
                Toast.makeText(context, "فشل التصدير — لم تُحفظ النسخة", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ---- معاينة الاسترجاع وتأكيد المستخدم ----
    pendingRestore?.let { pending ->
        val counts = pending.counts
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("تأكيد الاسترجاع", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الملف يحتوي على:", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    if (counts.configOk) Text("✅ إعدادات النظام (${AppRemoteConfig.KEY_MAINTENANCE} …)", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 13.sp)
                    else Text("⚠️ لا توجد إعدادات في الملف", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 13.sp)
                    if (counts.promoCount > 0) Text("✅ $counts.promoCount كود ترقية", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 13.sp)
                    if (counts.giftCount > 0) Text("✅ $counts.giftCount بطاقة هدايا", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 13.sp)
                    // صفوف المستخدمين يملكها الخادم: saveUser يرفض كتابة غير
                    // صف الموقّع نفسه، فـ «دمج المستخدمين» كان وعداً كاذباً.
                    // ما يُستعاد فعلاً منهم هو الإيقاف وحده عبر RPC الخادم.
                    if (counts.userCount > 0) Text("ℹ️ ${counts.userCount} مستخدم في الملف — يُستعاد إيقاف ${counts.suspendedCount} منهم فقط؛ صفوف المستخدمين يملكها الخادم ولا تُعاد كتابتها من التطبيق", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("⚠️ الاسترجاع يدمج الإعدادات والأكواد فقط (لا يحذف)؛ ومن المستخدمين يُستعاد الإيقاف وحده عبر الخادم.", color = PresetAccent, fontFamily = NotoSansFont, fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val job = scope.launch {
                            isRestoring = true
                            val result = restoreFromJson(context, pending.json, counts)
                            isRestoring = false
                            pendingRestore = null
                            val failed = result.codesFailed + result.suspensionsFailed +
                                if (counts.configOk && !result.configRestored) 1 else 0
                            val summary = buildString {
                                if (result.configRestored) append("الإعدادات ✅ ")
                                append("الأكواد: ${result.promoRestored + result.giftRestored}")
                                if (result.codesFailed > 0) append(" (فشل ${result.codesFailed})")
                                append(" ✅ ")
                                if (counts.suspendedCount > 0) {
                                    append("إيقاف المستخدمين: ${result.suspensionsRestored}/${counts.suspendedCount}")
                                    if (result.suspensionsFailed > 0) append(" (فشل ${result.suspensionsFailed})")
                                    append(" ✅")
                                }
                            }
                            Toast.makeText(
                                context,
                                if (failed == 0) "تم الاسترجاع بنجاح\n$summary"
                                else "تم الاسترجاع جزئياً (فشل $failed)\n$summary",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    enabled = !isRestoring,
                    colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary)
                ) {
                    Text("تأكيد الاسترجاع ✓", color = Color.Black, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("إلغاء", color = Color.Gray) }
            },
            containerColor = DeepSlate
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("نسخ احتياطي واسترجاع", color = PresetPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)

        // ---- النسخ التلقائي اليومي ----
        AutoBackupCard(context = context)

        // ---- بطاقة التصدير ----
        Card(
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("تصدير نسخة احتياطية كاملة (JSON)", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                Text("يشمل: المستخدمين + الأكواد + الإعدادات البعيدة. لا يشمل الملفات أو الصور.", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                Button(
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        exportLauncher.launch("qabas_backup_$stamp.json")
                    },
                    enabled = !isExporting && !isRestoring,
                    colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    if (isExporting) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = DeepSlate, strokeWidth = 2.dp)
                    else Text("تصدير نسخة احتياطية 📦", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ---- بطاقة الاسترجاع ----
        Card(
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("استرجاع نسخة احتياطية (JSON)", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                Text("يُعيد الإعدادات + الأكواد؛ ومن المستخدمين يُستعاد الإيقاف وحده (صفوف المستخدمين يملكها الخادم). يظهر معاينة أولاً.", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                Button(
                    onClick = {
                        importLauncher.launch(arrayOf("application/json"))
                    },
                    enabled = !isExporting && !isRestoring,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    if (isRestoring) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("جارٍ الاسترجاع…", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    } else {
                        Text("استرجاع نسخة احتياطية 📂", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─── منطق الاسترجاع ───────────────────────────────────────────────

private data class RestoreResult(
    val configRestored: Boolean = false,
    val promoRestored: Int = 0,
    val giftRestored: Int = 0,
    val codesFailed: Int = 0,
    /** إيقافات أُعيد تطبيقها عبر RPC الخادم (وحده ما يُستعاد من المستخدمين). */
    val suspensionsRestored: Int = 0,
    val suspensionsFailed: Int = 0
)

@Composable
private fun AutoBackupCard(context: Context) {
    var enabled by remember { mutableStateOf(AutoBackup.isEnabled(context)) }
    var lastBackup by remember { mutableStateOf(AutoBackup.lastBackupTime(context)) }
    val scope = rememberCoroutineScope()

    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("النسخ التلقائي اليومي", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    Text(
                        if (lastBackup > 0) {
                            "آخر نسخة: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastBackup))}"
                        } else "لم تُؤخذ أي نسخة تلقائية بعد",
                        color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp
                    )
                    Text(
                        "تُحفظ في مجلد التطبيق الخاص (آخر 7 نسخ) — بلا تدخل منك.",
                        color = TextSecondary, fontFamily = NotoSansFont, fontSize = 11.sp
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        AutoBackup.setEnabled(context, it)
                        lastBackup = AutoBackup.lastBackupTime(context)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF8B5CF6))
                )
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val ok = AutoBackup.runNow(context)
                        lastBackup = AutoBackup.lastBackupTime(context)
                        Toast.makeText(
                            context,
                            // ق171: نسخة بلا مستخدمين (تعذّر الجلب) كانت
                            // تُعرض «✅» كاملة — والنسخة ناقصة فعلاً؛
                            // يُعلن النقص هنا كما في التصدير اليدوي.
                            if (ok) {
                                if (AutoBackup.lastBackupHadUsers(context)) "أُخذت نسخة تلقائية الآن ✅"
                                else "أُخذت نسخة تلقائية — بلا مستخدمين (تعذّر الجلب) ⚠️"
                            } else "فشل النسخ التلقائي",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("نسخ الآن", color = Color(0xFF8B5CF6), fontFamily = CairoFont, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── النسخ التلقائي اليومي ─────────────────────────────────────────

object AutoBackup {
    private const val PREFS = "qabas_prefs"
    private const val KEY_ENABLED = "auto_backup_enabled"
    private const val KEY_LAST = "auto_backup_last_ms"
    /** ق171: هل جُلب المستخدمون في آخر نسخة تلقائية؟ الافتراضي true
     * للنسخ السابقة لهذا الإصلاح (بلا علم مخزّن فلا يُتَّهم نقصاً). */
    private const val KEY_LAST_USERS = "auto_backup_last_users"
    private const val MAX_KEEP = 7
    private const val REQUEST_CODE = 2002

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun lastBackupTime(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST, 0L)

    fun lastBackupHadUsers(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LAST_USERS, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) {
            scheduleDaily(context)
            AuditLogger.log(context, "auto_backup_enabled", "تفعيل النسخ التلقائي اليومي")
        } else {
            cancelDaily(context)
            AuditLogger.log(context, "auto_backup_disabled", "إيقاف النسخ التلقائي اليومي")
        }
    }

    private fun scheduleDaily(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
            val intent = android.content.Intent(context, AutoBackupReceiver::class.java)
            val pi = android.app.PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) android.app.PendingIntent.FLAG_IMMUTABLE else 0)
            )
            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(java.util.Calendar.HOUR_OF_DAY, 3) // 3:00 فجراً
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            alarmManager.setInexactRepeating(
                android.app.AlarmManager.RTC_WAKEUP, cal.timeInMillis,
                android.app.AlarmManager.INTERVAL_DAY, pi
            )
        } catch (e: Exception) {
            android.util.Log.w("AutoBackup", "schedule failed: ${e.message}")
        }
    }

    private fun cancelDaily(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
            val intent = android.content.Intent(context, AutoBackupReceiver::class.java)
            val pi = android.app.PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                android.app.PendingIntent.FLAG_NO_CREATE or
                    (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) android.app.PendingIntent.FLAG_IMMUTABLE else 0)
            )
            if (pi != null) alarmManager.cancel(pi)
        } catch (e: Exception) {
            android.util.Log.w("AutoBackup", "cancel failed: ${e.message}")
        }
    }

    /** تنفيذ نسخة صامتة الآن (من الزر أو من المنبه). */
    suspend fun runNow(context: Context): Boolean {
        return try {
            val (json, usersFetched) = buildExportJson(context)
            val dir = java.io.File(context.filesDir, "backups").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            java.io.File(dir, "auto_$stamp.json").writeText(json)
            // الاحتفاظ بآخر 7 فقط
            dir.listFiles { f -> f.name.startsWith("auto_") }
                ?.sortedBy { it.name }?.dropLast(MAX_KEEP)
                ?.forEach { runCatching { it.delete() } }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_LAST, System.currentTimeMillis())
                .putBoolean(KEY_LAST_USERS, usersFetched).apply()
            AuditLogger.log(
                context, "auto_backup_run",
                if (usersFetched) "نسخة تلقائية صامتة"
                else "نسخة تلقائية صامتة — بلا مستخدمين (تعذّر الجلب)"
            )
            true
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            android.util.Log.e("AutoBackup", "runNow failed: ${e.message}", e)
            false
        }
    }
}

/** مستقبل منبه النسخ التلقائي — يعمل حتى لو التطبيق مغلق. */
class AutoBackupReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: android.content.Intent) {
        if (!AutoBackup.isEnabled(context)) return
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                AutoBackup.runNow(context)
            } finally {
                pending.finish()
            }
        }
    }
}

private suspend fun readBackupForRestore(context: Context, uri: android.net.Uri): Pair<String?, BackupRestoreCounts?> = withContext(Dispatchers.IO) {
    try {
        val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return@withContext null to null
        val root = JSONObject(json)
        if (!root.has("config") && !root.has("promoCodes") && !root.has("users")) return@withContext null to null

        val codesArr = root.optJSONArray("promoCodes") ?: JSONArray()
        var promoCount = 0
        var giftCount = 0
        for (i in 0 until codesArr.length()) {
            val c = codesArr.optJSONObject(i) ?: continue
            if (c.optString("type") == "GIFT") giftCount++ else promoCount++
        }

        val usersArr = root.optJSONArray("users")
        var suspendedCount = 0
        if (usersArr != null) {
            for (i in 0 until usersArr.length()) {
                val u = usersArr.optJSONObject(i) ?: continue
                if (u.optBoolean("isSuspended", false) || u.optString("status") == "suspended") suspendedCount++
            }
        }

        val counts = BackupRestoreCounts(
            configOk = root.has("config"),
            promoCount = promoCount,
            giftCount = giftCount,
            userCount = usersArr?.length() ?: 0,
            suspendedCount = suspendedCount
        )
        json to counts
    } catch (e: Exception) {
        null to null
    }
}

/**
 * الاسترجاع من نص JSON مُتحقق منه عند المعاينة — لا إعادة قراءة للملف
 * (كان التأكيد يقرأ الملف من جديد، فإن تغيّر بين المعاينة والتأكيد كذبت
 * المعاينة). نص JSON المُمرَّر هنا هو نفسه ما عاينه المستخدم.
 */
private suspend fun restoreFromJson(context: Context, json: String, counts: BackupRestoreCounts): RestoreResult = withContext(Dispatchers.IO) {
    val root = JSONObject(json)
    var result = RestoreResult()

    // 1) استعادة الإعدادات البعيدة
    if (counts.configOk) {
        try {
            val c = root.getJSONObject("config")
            val data = AppRemoteConfig.ConfigData(
                maintenanceMode = c.optBoolean(AppRemoteConfig.KEY_MAINTENANCE, false),
                acceptRequests = c.optBoolean(AppRemoteConfig.KEY_ACCEPT_REQUESTS, true),
                autoAiReply = c.optBoolean(AppRemoteConfig.KEY_AUTO_AI_REPLY, true),
                maintenanceMessage = c.optString(AppRemoteConfig.KEY_MAINTENANCE_MESSAGE, AppRemoteConfig.ConfigData.DEFAULT_MESSAGE)
            )
            AppRemoteConfig.saveLocal(context, data)
            result = result.copy(configRestored = true)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { }
    }

    // 2) استعادة الأكواد — تُحتسب النجاحات الفعلية فقط: addCustom* تُرجع
    // Boolean، وكان الكود يعدّ كل محاولة نجاحاً حتى عند فشل الكتابة السحابية.
    val gm = GiftManager(context)
    var promoRestored = 0
    var giftRestored = 0
    var codesFailed = 0
    root.optJSONArray("promoCodes")?.let { arr ->
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val code = obj.optString("code", "")
            val type = obj.optString("type", "PROMO")
            if (code.isBlank()) { codesFailed++; continue }
            runCatching {
                val ok = if (type == "GIFT") {
                    val pts = obj.optInt("points", obj.optLong("points", 0).toInt())
                    gm.addCustomGiftCard(code, pts)
                } else {
                    val daysMs = obj.optLong("days", 0)
                    val days = if (daysMs > 100_000) (daysMs / 86_400_000).coerceAtLeast(1).toInt() else obj.optInt("days", 1).coerceAtLeast(1)
                    gm.addCustomPromoCode(code, days)
                }
                if (ok) {
                    if (type == "GIFT") giftRestored++ else promoRestored++
                } else {
                    codesFailed++
                }
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it else codesFailed++ }
        }
    }
    result = result.copy(promoRestored = promoRestored, giftRestored = giftRestored, codesFailed = codesFailed)

    // 3) المستخدمون: إيقاف فقط — إعادة كتابة الصفوف مستحيلة من التطبيق:
    // saveUser يرفض أي id غير الموقّع نفسه (صفوف يملكها الخادم، والهجرة
    // 202611150001 تمنع الكتابة المباشرة)، فكان الاسترجاع يعدّ N/N «ناجحة»
    // دون أن يكتب صفاً واحداً. الإيقاف وحده له RPC خادم مخوّل للمطوّر.
    var suspensionsRestored = 0
    var suspensionsFailed = 0
    root.optJSONArray("users")?.let { arr ->
        for (i in 0 until arr.length()) {
            val u = arr.optJSONObject(i) ?: continue
            val suspended = u.optBoolean("isSuspended", false) || u.optString("status") == "suspended"
            if (!suspended) continue
            val id = u.optString("id", "")
            val email = u.optString("email", "")
            if (id.isBlank() || email.isBlank()) { suspensionsFailed++; continue }
            runCatching {
                if (CloudServices.Database.updateUserSuspension(id, email, true)) suspensionsRestored++
                else suspensionsFailed++
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it else suspensionsFailed++ }
        }
    }
    result = result.copy(suspensionsRestored = suspensionsRestored, suspensionsFailed = suspensionsFailed)

    // تسجيل تدقيق لعملية الاسترجاع
    AuditLogger.log(
        context,
        "backup_restore",
        "استرجاع: إعدادات=${result.configRestored}, أكواد=${result.promoRestored + result.giftRestored} (فشل ${result.codesFailed}), إيقاف مستخدمين=${result.suspensionsRestored}/${counts.suspendedCount} (فشل ${result.suspensionsFailed})"
    )

    result
}

// ─── منطق التصدير ───────────────────────────────────────────────

/** يبني JSON التصدير؛ ويُرجع معه ما إذا جُلب المستخدمون فعلاً. */
private suspend fun buildExportJson(context: Context): Pair<String, Boolean> = withContext(Dispatchers.IO) {
    val root = JSONObject()
    root.put("app", "qabas")
    root.put("exportedAt", System.currentTimeMillis())
    root.put("exportedBy", AdminGuard.currentIdentity(context))

    // Config (cloud + local)
    val config = AppRemoteConfig.readLocal(context)
    root.put("config", JSONObject().apply {
        put(AppRemoteConfig.KEY_MAINTENANCE, config.maintenanceMode)
        put(AppRemoteConfig.KEY_ACCEPT_REQUESTS, config.acceptRequests)
        put(AppRemoteConfig.KEY_AUTO_AI_REPLY, config.autoAiReply)
        put(AppRemoteConfig.KEY_MAINTENANCE_MESSAGE, config.maintenanceMessage)
    })

    // Users (best-effort: try Supabase, fallback empty)
    // — الإيقاف جزء من النسخة: كان التصدير يسقط status فيستعيد الاسترجاع
    // موقوفين نشطين بعد مسح الجدول (والاسترجاع يقرأ isSuspended أصلاً).
    val usersArr = JSONArray()
    var usersFetched = false
    runCatching {
        if (SupabaseServices.isSupabaseAvailable) {
            val users = SupabaseServices.Database.getAllUsers()
            usersFetched = true
            users.forEach { u ->
                usersArr.put(JSONObject().apply {
                    put("id", u.id)
                    put("name", u.name)
                    put("email", u.email)
                    put("type", u.type)
                    put("status", u.status)
                    put("isSuspended", u.status == "suspended")
                })
            }
        }
    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
    root.put("users", usersArr)

    // Promo codes
    val codesArr = JSONArray()
    runCatching {
        val gm = GiftManager(context)
        gm.loadValidCodesFromCloud()
        gm.getCustomPromoCodes().forEach { (code, days) ->
            codesArr.put(JSONObject().apply { put("code", code); put("days", days); put("type", "PROMO") })
        }
        gm.getCustomGiftCards().forEach { (code, pts) ->
            codesArr.put(JSONObject().apply { put("code", code); put("points", pts); put("type", "GIFT") })
        }
    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
    root.put("promoCodes", codesArr)

    root.toString() to usersFetched
}
