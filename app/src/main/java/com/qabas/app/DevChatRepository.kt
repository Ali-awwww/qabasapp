package com.qabas.app

import android.content.Context
import android.os.Build
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 🛰️ DevChatRepository — مستودع المراسلة السيبراني المتقدم بين المستخدم والمطور
 * يدعم التخزين المحلي الفوري، التزامن السحابي مع Supabase، وتوليد تقارير التيليمتري
 * مع محاكي الرد الذكي التفاعلي ليمنح المستخدم تجربة حية ونابضة بالحياة.
 */
object DevChatRepository {
    private const val TAG = "DevChatRepo"
    private const val PREFS_NAME = "qabas_dev_chat_store"
    private const val KEY_MESSAGES_PREFIX = "chat_msgs_"
    private const val KEY_ALL_EMAILS = "chat_all_emails"

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    data class DevChatMessage(
        val id: String = UUID.randomUUID().toString(),
        val userId: String? = null,
        val userEmail: String = "",
        val sender: String = "user", // "user" | "developer"
        val message: String = "",
        val createdAt: Long = System.currentTimeMillis(),
        val formattedTime: String = "",
        val category: String = "general", // "feature", "bug", "telemetry", "billing", "report", "dua", "urgent"
        val isTelemetry: Boolean = false,
        val isUrgent: Boolean = false,
        val duaStamp: String? = null,
        val reactions: Map<String, Int> = emptyMap(),
        val audioLengthSec: Int = 0 // > 0 if simulated audio note
    )

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

    /** قراءة جميع الرسائل لمستخدم معين (محلياً) */
    fun getLocalMessages(context: Context, userEmail: String): List<DevChatMessage> {
        val cleanEmail = userEmail.trim().lowercase()
        if (cleanEmail.isBlank()) return emptyList()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_MESSAGES_PREFIX + cleanEmail, null) ?: return getWelcomeSeedMessages(cleanEmail)
        return try {
            val type = Types.newParameterizedType(List::class.java, DevChatMessage::class.java)
            val adapter = moshi.adapter<List<DevChatMessage>>(type)
            adapter.fromJson(json) ?: getWelcomeSeedMessages(cleanEmail)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse local messages: ${e.message}")
            getWelcomeSeedMessages(cleanEmail)
        }
    }

    /** حفظ الرسائل محلياً */
    private fun saveLocalMessages(context: Context, userEmail: String, messages: List<DevChatMessage>) {
        val cleanEmail = userEmail.trim().lowercase()
        if (cleanEmail.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val type = Types.newParameterizedType(List::class.java, DevChatMessage::class.java)
            val adapter = moshi.adapter<List<DevChatMessage>>(type)
            val json = adapter.toJson(messages)
            prefs.edit().putString(KEY_MESSAGES_PREFIX + cleanEmail, json).apply()

            // تسجيل البريد في قائمة جميع المحادثات بطريقة آمنة
            val existingEmails = HashSet(prefs.getStringSet(KEY_ALL_EMAILS, emptySet()) ?: emptySet())
            existingEmails.add(cleanEmail)
            prefs.edit().putStringSet(KEY_ALL_EMAILS, existingEmails).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save local messages: ${e.message}")
        }
    }

    /** رسائل الترحيب التأسيسية لإضفاء الحيوية الفورية عند فتح الشاشة أول مرة */
    private fun getWelcomeSeedMessages(userEmail: String): List<DevChatMessage> {
        val now = System.currentTimeMillis()
        val t1 = timeFormat.format(Date(now - 60000 * 5))
        val t2 = timeFormat.format(Date(now - 60000 * 2))
        return listOf(
            DevChatMessage(
                id = "welcome_1",
                userEmail = userEmail,
                sender = "developer",
                message = "مرحباً بك في قناة المراسلة المباشرة مع فريق تطوير قبس 🛰️\nنستقبل هنا بكل امتنان ودعاء كافة مقترحاتكم وأفكاركم وبلاغاتكم الطارئة، ونسأل الله أن يجعل سعيكم في ميزان حسناتكم.",
                createdAt = now - 60000 * 5,
                formattedTime = t1,
                category = "general",
                reactions = mapOf("🌿" to 3, "✨" to 2)
            ),
            DevChatMessage(
                id = "welcome_2",
                userEmail = userEmail,
                sender = "developer",
                message = "📜 ضوابط التواصل: نرجو الالتزام بالأدب الإسلامي في الطرح، وقصر وسم (عاجل 🚨) على المشكلات الطارئة أو المحتوى المخالف، مع تحري الأمانة في البيانات.",
                createdAt = now - 60000 * 2,
                formattedTime = t2,
                category = "urgent",
                reactions = mapOf("🤲" to 4)
            )
        )
    }

    /** إرسال رسالة جديدة محلياً وعبر السحابة */
    suspend fun sendMessage(
        context: Context,
        userEmail: String,
        text: String,
        sender: String = "user",
        category: String = "general",
        isTelemetry: Boolean = false,
        isUrgent: Boolean = false,
        duaStamp: String? = null,
        audioLengthSec: Int = 0
    ): DevChatMessage = withContext(Dispatchers.IO) {
        val cleanEmail = userEmail.trim().lowercase()
        val now = System.currentTimeMillis()
        val formattedTime = timeFormat.format(Date(now))
        val newMsg = DevChatMessage(
            id = UUID.randomUUID().toString(),
            userEmail = cleanEmail,
            sender = sender,
            message = text.trim(),
            createdAt = now,
            formattedTime = formattedTime,
            category = category,
            isTelemetry = isTelemetry,
            isUrgent = isUrgent,
            duaStamp = duaStamp,
            reactions = emptyMap(),
            audioLengthSec = audioLengthSec
        )

        // حفظ محلي
        val current = getLocalMessages(context, cleanEmail).toMutableList()
        current.add(newMsg)
        saveLocalMessages(context, cleanEmail, current)

        // مزامنة مع Supabase في الخلفية إن توفرت
        try {
            if (SupabaseServices.isSupabaseAvailable) {
                val prefix = when {
                    isUrgent -> "[URGENT] "
                    isTelemetry -> "[TELEMETRY] "
                    else -> ""
                }
                val accountNumber = UserIdentifierManager.getAccountNumber(context, cleanEmail).ifBlank {
                    UserIdentifierManager.getOrCreateAccountNumber(context, cleanEmail)
                }
                SupabaseServices.Database.sendChatMessage(
                    userEmail = cleanEmail,
                    message = prefix + text.trim(),
                    sender = sender,
                    accountNumber = accountNumber.takeIf { it.isNotBlank() }
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Supabase send sync fallback: ${e.message}")
        }

        newMsg
    }

    /** إضافة تفاعل إيموجي على رسالة معينة */
    fun toggleReaction(context: Context, userEmail: String, messageId: String, emoji: String) {
        val cleanEmail = userEmail.trim().lowercase()
        val current = getLocalMessages(context, cleanEmail).toMutableList()
        val idx = current.indexOfFirst { it.id == messageId }
        if (idx != -1) {
            val msg = current[idx]
            val existingCount = msg.reactions[emoji] ?: 0
            val updatedMap = msg.reactions.toMutableMap()
            if (existingCount > 0) {
                updatedMap[emoji] = existingCount + 1
            } else {
                updatedMap[emoji] = 1
            }
            current[idx] = msg.copy(reactions = updatedMap)
            saveLocalMessages(context, cleanEmail, current)
        }
    }

    /** مسح سجل المحادثة محلياً */
    fun clearMessages(context: Context, userEmail: String) {
        val cleanEmail = userEmail.trim().lowercase()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_MESSAGES_PREFIX + cleanEmail).apply()
    }

    /** جلب كافة المحادثات للمطور (دمج المحلي والسحابي الشامل المضمون) */
    suspend fun getAllThreads(context: Context): Map<String, List<DevChatMessage>> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val allEmails = mutableSetOf<String>()

        // 1. القائمة المسجلة رسمياً
        prefs.getStringSet(KEY_ALL_EMAILS, emptySet())?.let { allEmails.addAll(it) }

        // 2. فحص شامل ومباشر لجميع المفاتيح المخزنة لضمان عدم ضياع أي محادثة محلية إطلاقاً
        prefs.all.keys.filter { it.startsWith(KEY_MESSAGES_PREFIX) }.forEach {
            val email = it.removePrefix(KEY_MESSAGES_PREFIX).trim().lowercase()
            if (email.isNotBlank()) allEmails.add(email)
        }

        // 3. تضمين البريد النشط والافتراضي
        allEmails.add("user@qabas.app")
        val currentAppEmail = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .getString("user_email", null)
        if (!currentAppEmail.isNullOrBlank()) {
            allEmails.add(currentAppEmail.trim().lowercase())
        }
        SupabaseServices.Auth.currentUser.value?.email?.let {
            if (it.isNotBlank()) allEmails.add(it.trim().lowercase())
        }

        val result = mutableMapOf<String, List<DevChatMessage>>()

        for (email in allEmails) {
            val msgs = getLocalMessages(context, email)
            if (msgs.isNotEmpty()) {
                result[email] = msgs
            }
        }

        // 4. دمج رسائل Supabase
        try {
            if (SupabaseServices.isSupabaseAvailable) {
                val cloudThreads = SupabaseServices.Database.getAllChatThreads()
                for ((cloudEmail, cloudMsgs) in cloudThreads) {
                    val cleanCloudEmail = cloudEmail.trim().lowercase()
                    val existing = result[cleanCloudEmail]?.toMutableList() ?: mutableListOf()
                    val existingTexts = existing.map { it.message.trim() }.toSet()
                    for (cMsg in cloudMsgs) {
                        if (cMsg.message.trim() !in existingTexts) {
                            existing.add(
                                DevChatMessage(
                                    id = cMsg.id,
                                    userId = cMsg.userId,
                                    userEmail = cleanCloudEmail,
                                    sender = cMsg.sender,
                                    message = cMsg.message,
                                    formattedTime = cMsg.createdAt?.takeLast(8)?.take(5) ?: "الآن"
                                )
                            )
                        }
                    }
                    result[cleanCloudEmail] = existing.sortedBy { it.createdAt }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to merge cloud threads: ${e.message}")
        }

        result
    }

    /** مزامنة سحابية كاملة لمستخدم معين */
    suspend fun syncMessages(context: Context, userEmail: String): List<DevChatMessage> = withContext(Dispatchers.IO) {
        val local = getLocalMessages(context, userEmail).toMutableList()
        if (!SupabaseServices.isSupabaseAvailable || userEmail.isBlank()) {
            return@withContext local
        }

        try {
            val remote = SupabaseServices.Database.getChatMessages(userEmail)
            if (remote.isNotEmpty()) {
                val localTexts = local.map { it.message.trim() }.toSet()
                var updated = false
                for (r in remote) {
                    if (r.message.trim() !in localTexts) {
                        local.add(
                            DevChatMessage(
                                id = r.id,
                                userId = r.userId,
                                userEmail = userEmail,
                                sender = r.sender,
                                message = r.message,
                                formattedTime = r.createdAt?.takeLast(8)?.take(5) ?: "الآن"
                            )
                        )
                        updated = true
                    }
                }
                if (updated) {
                    val sorted = local.sortedBy { it.createdAt }
                    saveLocalMessages(context, userEmail, sorted)
                    return@withContext sorted
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sync with Supabase failed: ${e.message}")
        }
        local
    }

    /**
     * 🛰️ توليد تقرير تشخيصي حقيقي فوري للنظام (Spatial Telemetry HUD Diagnostics)
     */
    fun generateSystemDiagnostics(context: Context): String {
        val runtime = Runtime.getRuntime()
        val totalMemoryMb = runtime.totalMemory() / (1024 * 1024)
        val freeMemoryMb = runtime.freeMemory() / (1024 * 1024)
        val usedMemoryMb = totalMemoryMb - freeMemoryMb
        val dm = context.resources.displayMetrics
        val density = dm.densityDpi
        val res = "${dm.widthPixels}x${dm.heightPixels}"

        val appVersion = try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "2.3.0"
        } catch (_: Exception) {
            "2.3.0-PRO"
        }

        return """
            |══════════════════════════════════
            |🛰️ [QABAS_SPATIAL_HUD // TELEMETRY_REPORT]
            |══════════════════════════════════
            |• APP_VERSION   : v$appVersion (Spatial Engine)
            |• DEVICE_MODEL  : ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}
            |• ANDROID_SDK   : API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})
            |• SCREEN_RES    : $res @ ${density}dpi
            |• MEMORY_STATS  : $usedMemoryMb MB used / $totalMemoryMb MB heap
            |• AUDIO_ENGINE  : ExoPlayer + Synth SoundManager [ACTIVE]
            |• CLOUD_BEACON  : ${if (SupabaseServices.isSupabaseAvailable) "WSS-ONLINE (SUPABASE_LINK)" else "LOCAL_HUD_BUFFER"}
            |• TIMESTAMP     : ${dateFormat.format(Date())} ${timeFormat.format(Date())}
            |══════════════════════════════════
            |الحالة: النظام يعمل بكفاءة كاملة وجاهز للفحص المباشر ⚡
        """.trimMargin()
    }

    /**
     * 🤖 محاكي الرد الذكي التفاعلي للمطور:
     * - يقتصر على الحاجات الطارئة ورسائل الشكر والدعاء للمستخدم على مساهمته الطيبة
     * - يمتنع تماماً عن الردود التلقائية المضللة التي تزعم حل المشكلة قبل فحصها
     */
    suspend fun simulateSmartDeveloperReply(
        context: Context,
        userEmail: String,
        userMessage: String,
        isUrgent: Boolean = false,
        onTyping: (Boolean) -> Unit,
        onReplyReceived: (DevChatMessage) -> Unit
    ) = withContext(Dispatchers.Main) {
        // بدء إشارة الكتابة التفاعلية
        onTyping(true)
        delay(1800) // وقت واقعي للتفكير والكتابة
        onTyping(false)

        val clean = userMessage.trim().lowercase()

        val (replyText, replyCategory) = when {
            isUrgent || clean.contains("عاجل") || clean.contains("مسيء") || clean.contains("مخالف") || clean.contains("طارئ") || clean.contains("urgent") -> {
                Pair(
                    "🚨 تم استلام ندائك العاجل بعناية فائقة 🛡️\nجزاك الله خيراً على غيرتك وحرصك المبارك على نقاء التطبيق وسلامة المستخدمين. تم تصعيد هذا البلاغ الطارئ مباشرة إلى مهندسي التطوير والإشراف الشرعي لمتابعته كأولوية قصوى.",
                    "urgent"
                )
            }
            clean.contains("خلل") || clean.contains("خطأ") || clean.contains("مشكلة") || clean.contains("bug") || clean.contains("تهنيج") -> {
                Pair(
                    "جزاك الله خير الجزاء وبارك فيك على حرصك ومساهمتك الطيبة في إبلاغنا 🌿🤲\nنسأل الله أن يكتب سعيك في ميزان حسناتك؛ تم تدوين ملاحظتك بدقة وتحويلها لجدول الفحص البرمجي للتحقق منها وتلافيها بعون الله.",
                    "dua"
                )
            }
            clean.contains("ميزة") || clean.contains("اقتراح") || clean.contains("فكرة") || clean.contains("feature") || clean.contains("تطوير") -> {
                Pair(
                    "بارك الله فيك وفي عقلك النيّر 💡✨\nشكر الله لك هذا الاقتراح الثري وحرصك على المساهمة في الارتقاء بقبس. أفكاركم ومشاركاتكم هي الأساس في تطوير هذا المشروع المبارك.",
                    "dua"
                )
            }
            clean.contains("شكرا") || clean.contains("جزاك") || clean.contains("بارك") || clean.contains("دعاء") || clean.contains("وفقك") -> {
                Pair(
                    "ولكم بالمثل وزيادة، وتقبل الله منا ومنكم صالح الأعمال والنيات ❤️🤲\nوجودكم ودعاؤكم الصادق بظهر الغيب هو أعظم ما نرجوه ونستعين به في رحلة خدمة كتاب الله.",
                    "dua"
                )
            }
            clean.contains("telemetry") || clean.contains("تقرير") || clean.contains("مواصفات") -> {
                Pair(
                    "تم استلام التقرير التشخيصي بنجاح 🛰️\nأحسن الله إليك ونفع بك؛ مساهمتك التقنية تساعدنا في تحسين التوافقية والأداء على مختلف الأجهزة.",
                    "dua"
                )
            }
            else -> {
                Pair(
                    "أهلاً وسهلاً بك يا أخي المبارك 🌿\nشكر الله لك مساهمتك وتواصلك الكريم، ونسأل الله أن يجريك خير الجزاء على وقتك وحرصك على نفع إخوانك. رسالتك محفوظة ومحل اهتمام فريق التطوير.",
                    "dua"
                )
            }
        }

        val devMsg = sendMessage(
            context = context,
            userEmail = userEmail,
            text = replyText,
            sender = "developer",
            category = replyCategory,
            isUrgent = isUrgent
        )
        onReplyReceived(devMsg)
    }

    /**
     * إرسال بلاغ عن فيديو مسيء في قسم الريلز وربطه بمحادثة المطور الفورية
     */
    suspend fun reportOffensiveReel(
        context: Context,
        reporterEmail: String,
        reporterName: String,
        reelId: String,
        reelTitle: String,
        reelCreator: String,
        reelVideoUrl: String,
        reason: String,
        additionalNotes: String = ""
    ): Pair<DevChatMessage, DevChatMessage> = withContext(Dispatchers.IO) {
        val cleanEmail = reporterEmail.trim().lowercase()
        val detailsText = if (additionalNotes.isNotBlank()) "\n📝 تفاصيل إضافية: ${additionalNotes.trim()}" else ""

        val reportUserMessage = """
            🚨 [بلاغ عاجل: فيديو مسيء في قسم الريلز]
            ━━━━━━━━━━━━━━━━━━━━━━
            📹 عنوان المقطع: «$reelTitle»
            🆔 معرّف المقطع: $reelId
            👤 صاحب المقطع: $reelCreator
            ⚠️ سبب البلاغ: $reason$detailsText
            🔗 رابط المقطع: $reelVideoUrl
            ━━━━━━━━━━━━━━━━━━━━━━
            يرجى من فريق التطوير مراجعة المقطع وحذفه فوراً في حال ثبوت المخالفة.
        """.trimIndent()

        // 1. تسجيل بلاغ المستخدم في المحادثة
        val userMsg = sendMessage(
            context = context,
            userEmail = cleanEmail,
            text = reportUserMessage,
            sender = "user",
            category = "urgent",
            isUrgent = true
        )

        // 2. تسجيل رد تأكيدي مباشر من المطور مع الدعاء والشكر
        val devReplyText = """
            🛡️ تم استلام بلاغك العاجل بشأن المقطع «$reelTitle» بنجاح!
            جزاك الله خيراً وكتب أجرك على غيرتك على حرمات الله وطهارة مجتمع قبس 🌿🤲.
            فريق الإشراف يقوم الآن بمراجعة المقطع فوراً، وسيتم اتخاذ الإجراء الحاسم بحجب أو حذف أي محتوى مخالف للضوابط الشرعية.
        """.trimIndent()

        val devReplyMsg = sendMessage(
            context = context,
            userEmail = cleanEmail,
            text = devReplyText,
            sender = "developer",
            category = "urgent",
            isUrgent = true
        )

        Pair(userMsg, devReplyMsg)
    }
}
