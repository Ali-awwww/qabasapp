package com.qabas.app

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class SocialPlatformAccount(
    val id: String,
    val name: String,
    val handle: String,
    val isConnected: Boolean,
    val followers: Int,
    val totalLikes: Int,
    val totalViews: Int,
    val publishedCount: Int,
    val lastSync: String
)

data class PublishResult(
    val platformId: String,
    val platformName: String,
    val isSuccess: Boolean,
    val postUrl: String,
    val message: String
)

data class SmartPublishSlot(
    val id: String,
    val platformId: String,
    val platformName: String,
    val timeLabel: String,
    val hourOfDay: Int,
    val minute: Int,
    val rationale: String,
    val isRecommended: Boolean = false
)

data class ScheduledPublishItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val platformId: String,
    val platformName: String,
    val scheduledTimeMillis: Long,
    val formattedTime: String,
    val hashtags: String = "",
    // ق159: ساعة/دقيقة جدار الساعة المحفوظتان لإعادة الحساب عند
    // تغيير المنطقة الزمنية — لحظة مطلقة وحدها تُطلق التذكير في
    // وقت جدارٍ خاطئ بعد السفر. ‎−1 لعناصر قديمة قبل إضافة الحقلين.
    val hourOfDay: Int = -1,
    val minute: Int = -1
)

object SocialAccountManager {

    private const val PREFS_NAME = "qabas_social_accounts"

    /** المنصات الثابتة — تُحذف المخصصة فقط، الثابتة تُعدَّل روابطها. */

    private val secureTokenKeys = mapOf(
        "youtube" to "social_yt_token",
        "tiktok" to "social_tiktok_token",
        "instagram" to "social_insta_token",
        "twitter" to "social_twitter_token",
        "facebook" to "social_fb_token"
    )

    /** One-time migration from the legacy social SharedPreferences file. */
    private fun secureToken(context: Context, platformId: String): String {
        val secureKey = secureTokenKeys[platformId] ?: return ""
        val securePrefs = AgentPrefs.instance(context)
        val existing = securePrefs.getString(secureKey, "").orEmpty()
        if (existing.isNotBlank()) return existing
        val legacyKey = when (platformId) {
            "youtube" -> "yt_token"
            "tiktok" -> "tiktok_token"
            "instagram" -> "insta_token"
            "twitter" -> "twitter_token"
            "facebook" -> "fb_token"
            else -> return ""
        }
        val legacyPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val legacy = legacyPrefs.getString(legacyKey, "").orEmpty()
        if (legacy.isNotBlank()) {
            AgentPrefs.putString(context, secureKey, legacy)
            legacyPrefs.edit().remove(legacyKey).apply()
        }
        return legacy
    }

    fun openProfileUrl(context: Context, platformId: String, handle: String) {
        val url = publicProfileUrl(platformId, handle)
        if (url != null) {
            val prefPkg = when (platformId) {
                "youtube" -> "com.google.android.youtube"
                "facebook" -> "com.facebook.katana"
                "instagram" -> "com.instagram.android"
                else -> null
            }
            QabasLinkOpener.openUrl(context, url, "الملف الشخصي ($handle)", preferredPackage = prefPkg)
        } else {
            android.widget.Toast.makeText(context, "لم يتم تحديد معرف أو رابط صالح للملف الشخصي", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun copyToClipboard(context: Context, text: String, label: String = "Qabas Link") {
        QabasLinkOpener.copyToClipboard(context, text, label)
        android.widget.Toast.makeText(context, "تم نسخ $label بنجاح! 📋", android.widget.Toast.LENGTH_SHORT).show()
    }


    /** رابط الملف العام للحساب حسب المنصة (يُستخدم للتحقق والعرض). */
    fun publicProfileUrl(platformId: String, handle: String): String? {
        val h = handle.trim().removePrefix("@").removeSuffix("@").trim()
        if (h.isBlank()) return null
        return when (platformId) {
            "youtube" -> "https://www.youtube.com/@$h"
            "tiktok" -> "https://www.tiktok.com/@$h"
            "instagram" -> "https://www.instagram.com/$h/"
            "twitter" -> "https://x.com/$h"
            "facebook" -> "https://www.facebook.com/$h"
            else -> null
        }
    }

    fun getAccounts(context: Context): List<SocialPlatformAccount> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val ytConnected = prefs.getBoolean("yt_connected", false)
        val tiktokConnected = prefs.getBoolean("tiktok_connected", false)
        val instaConnected = prefs.getBoolean("insta_connected", false)
        val twitterConnected = prefs.getBoolean("twitter_connected", false)
        val fbConnected = prefs.getBoolean("fb_connected", false)

        // إحصائيات حقيقية فقط — تبدأ من 0 وتتزايد عند النشر الفعلي
        val ytFollowers = prefs.getInt("yt_followers", 0)
        val ytLikes = prefs.getInt("yt_likes", 0)
        val ytViews = prefs.getInt("yt_views", 0)
        val ytPublished = prefs.getInt("yt_published", 0)

        val tiktokFollowers = prefs.getInt("tiktok_followers", 0)
        val tiktokLikes = prefs.getInt("tiktok_likes", 0)
        val tiktokViews = prefs.getInt("tiktok_views", 0)
        val tiktokPublished = prefs.getInt("tiktok_published", 0)

        val instaFollowers = prefs.getInt("insta_followers", 0)
        val instaLikes = prefs.getInt("insta_likes", 0)
        val instaViews = prefs.getInt("insta_views", 0)
        val instaPublished = prefs.getInt("insta_published", 0)

        val twitterFollowers = prefs.getInt("twitter_followers", 0)
        val twitterLikes = prefs.getInt("twitter_likes", 0)
        val twitterViews = prefs.getInt("twitter_views", 0)
        val twitterPublished = prefs.getInt("twitter_published", 0)

        val fbFollowers = prefs.getInt("fb_followers", 0)
        val fbLikes = prefs.getInt("fb_likes", 0)
        val fbViews = prefs.getInt("fb_views", 0)
        val fbPublished = prefs.getInt("fb_published", 0)

        val now = SimpleDateFormat("HH:mm - yyyy/MM/dd", Locale.getDefault()).format(Date())

        return listOf(
            SocialPlatformAccount(
                id = "youtube",
                name = "YouTube Shorts",
                handle = prefs.getString("yt_handle", "@your_channel") ?: "@your_channel",
                isConnected = ytConnected,
                followers = ytFollowers,
                totalLikes = ytLikes,
                totalViews = ytViews,
                publishedCount = ytPublished,
                lastSync = prefs.getString("yt_last_sync", now) ?: now
            ),
            SocialPlatformAccount(
                id = "tiktok",
                name = "TikTok Studio",
                handle = prefs.getString("tiktok_handle", "@your_account") ?: "@your_account",
                isConnected = tiktokConnected,
                followers = tiktokFollowers,
                totalLikes = tiktokLikes,
                totalViews = tiktokViews,
                publishedCount = tiktokPublished,
                lastSync = prefs.getString("tiktok_last_sync", now) ?: now
            ),
            SocialPlatformAccount(
                id = "instagram",
                name = "Instagram",
                handle = prefs.getString("insta_handle", "@your_account") ?: "@your_account",
                isConnected = instaConnected,
                followers = instaFollowers,
                totalLikes = instaLikes,
                totalViews = instaViews,
                publishedCount = instaPublished,
                lastSync = prefs.getString("insta_last_sync", now) ?: now
            ),
            SocialPlatformAccount(
                id = "facebook",
                name = "Facebook",
                handle = prefs.getString("fb_handle", "Qabas.Official") ?: "Qabas.Official",
                isConnected = fbConnected,
                followers = fbFollowers,
                totalLikes = fbLikes,
                totalViews = fbViews,
                publishedCount = fbPublished,
                lastSync = prefs.getString("fb_last_sync", now) ?: now
            ),
            SocialPlatformAccount(
                id = "twitter",
                name = "X (Twitter)",
                handle = prefs.getString("twitter_handle", "@QabasApp") ?: "@QabasApp",
                isConnected = twitterConnected,
                followers = twitterFollowers,
                totalLikes = twitterLikes,
                totalViews = twitterViews,
                publishedCount = twitterPublished,
                lastSync = prefs.getString("twitter_last_sync", now) ?: now
            )
        )
    }

    fun toggleConnection(context: Context, platformId: String, isConnected: Boolean, handle: String = "", apiToken: String = "") {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        secureTokenKeys[platformId]?.let { secureKey ->
            when {
                apiToken.isNotBlank() -> AgentPrefs.putString(context, secureKey, apiToken.trim())
                !isConnected -> AgentPrefs.remove(context, secureKey)
            }
        }
        when (platformId) {
            "youtube" -> {
                editor.putBoolean("yt_connected", isConnected)
                if (handle.isNotBlank()) editor.putString("yt_handle", handle)
            }
            "tiktok" -> {
                editor.putBoolean("tiktok_connected", isConnected)
                if (handle.isNotBlank()) editor.putString("tiktok_handle", handle)
            }
            "instagram" -> {
                editor.putBoolean("insta_connected", isConnected)
                if (handle.isNotBlank()) editor.putString("insta_handle", handle)
            }
            "twitter" -> {
                editor.putBoolean("twitter_connected", isConnected)
                if (handle.isNotBlank()) editor.putString("twitter_handle", handle)
            }
            "facebook" -> {
                editor.putBoolean("fb_connected", isConnected)
                if (handle.isNotBlank()) editor.putString("fb_handle", handle)
            }
        }
        editor.apply()
    }

    fun getAccessToken(context: Context, platformId: String): String {
        return secureToken(context, platformId)
    }

    /** تحديث وقت المزامنة فقط — لا أرقام وهمية. الإحصائيات تتزايد فقط من النشر الفعلي. */
    fun syncAnalytics(context: Context): List<SocialPlatformAccount> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val now = SimpleDateFormat("HH:mm - yyyy/MM/dd", Locale.getDefault()).format(Date())

        editor.putString("yt_last_sync", now)
            .putString("tiktok_last_sync", now)
            .putString("insta_last_sync", now)
            .putString("twitter_last_sync", now)
            .putString("fb_last_sync", now)
            .apply()

        return getAccounts(context)
    }

    suspend fun publishVideoToPlatforms(
        context: Context,
        title: String,
        description: String,
        hashtags: String,
        selectedPlatformIds: List<String>,
        onProgressUpdate: (String) -> Unit = {}
    ): List<PublishResult> {
        val results = mutableListOf<PublishResult>()
        val accountsMap = getAccounts(context).associateBy { it.id }

        for (platformId in selectedPlatformIds) {
            val account = accountsMap[platformId] ?: continue
            onProgressUpdate("التحقق من صلاحية اتصال API لـ ${account.name}...")

            val hasToken = getAccessToken(context, platformId)?.isNotBlank() == true
            val reason = if (!hasToken) {
                "الحساب غير مربوط برمز وصول OAuth صالح."
            } else {
                "النشر الآلي المباشر يتطلب تفعيل واجهة رفع الفيديو الرسمية (Client ID / API Secret) — استخدم المشاركة الأصلية للنظام أدناه."
            }

            results.add(
                PublishResult(
                    platformId = platformId,
                    platformName = account.name,
                    isSuccess = false,
                    postUrl = "",
                    message = reason
                )
            )
        }

        return results
    }

    fun shareVideoNatively(context: Context, videoFile: File?, text: String) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_SUBJECT, "Qabas Video Release")
                putExtra(Intent.EXTRA_TEXT, text)

                if (videoFile != null && videoFile.exists()) {
                    val authority = "${context.packageName}.fileprovider"
                    val videoUri: Uri = try {
                        FileProvider.getUriForFile(context, authority, videoFile)
                    } catch (e: Exception) {
                        Uri.fromFile(videoFile)
                    }
                    putExtra(Intent.EXTRA_STREAM, videoUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            val chooser = Intent.createChooser(shareIntent, "نشر الفيديو عبر منصات التواصل:")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Saves exported MP4 video to device MediaStore / Movies directory for instant Gallery access.
     */
    fun saveVideoToGallery(context: Context, videoFile: File?): Boolean {
        if (videoFile == null || !videoFile.exists()) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "QABAS_VIDEO_${System.currentTimeMillis()}.mp4")
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Movies/Qabas_Studio")
                }
                val uri = context.contentResolver.insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { outStream ->
                        videoFile.inputStream().use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    true
                } else {
                    false
                }
            } else {
                val moviesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)
                val targetDir = File(moviesDir, "Qabas_Studio").apply { mkdirs() }
                val targetFile = File(targetDir, "QABAS_VIDEO_${System.currentTimeMillis()}.mp4")
                videoFile.copyTo(targetFile, overwrite = true)
                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                mediaScanIntent.data = Uri.fromFile(targetFile)
                context.sendBroadcast(mediaScanIntent)
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ==========================================
    // Smart Scheduling & Optimal Time Engine
    // ==========================================
    private const val KEY_SCHEDULED_ITEMS = "scheduled_publish_items"

    /**
     * ق159: مواعيد مقترحة عامة لأوقات ذروة معتادة لكل منصة — ليست
     * «data-driven» ولا من تحليلات حساب (لا مصدر تحليلات في التطبيق)،
     * فلا نسب تفاعل ولا ادعاء قياس جمهور.
     */
    fun getOptimalPublishSlots(context: Context, platformId: String = "tiktok"): List<SmartPublishSlot> {
        // ق159: كانت هنا نسب «تفاعل» مختلقة (98% و91% و87%...) تُعرض
        // على أنها قياس جمهور («بناءً على تفاعل الجمهور») بلا أي مصدر
        // تحليلات — وكان جلب الحسابات أدناه ميتاً (account بلا استعمال).
        // المواعيد الآن اقتراحات عامة لأوقات ذروة معتادة، بلا أرقام وهمية.
        val baseSlots = when (platformId) {
            "tiktok" -> listOf(
                SmartPublishSlot(
                    id = "tt_evening_peak",
                    platformId = "tiktok",
                    platformName = "TikTok",
                    timeLabel = "8:30 م (ذروة المساء)",
                    hourOfDay = 20,
                    minute = 30,
                    rationale = "أعلى تفاعل لخوارزمية For You بعد صلاة العشاء والراحة المسائية 🌙",
                    isRecommended = true
                ),
                SmartPublishSlot(
                    id = "tt_fajr_reflection",
                    platformId = "tiktok",
                    platformName = "TikTok",
                    timeLabel = "5:30 ص (ساعة البكور)",
                    hourOfDay = 5,
                    minute = 30,
                    rationale = "ذروة المقاطع الروحانية وتدبر القرآن مع بداية اليوم ✨",
                    isRecommended = false
                ),
                SmartPublishSlot(
                    id = "tt_afternoon_boost",
                    platformId = "tiktok",
                    platformName = "TikTok",
                    timeLabel = "4:15 م (بعد العصر)",
                    hourOfDay = 16,
                    minute = 15,
                    rationale = "وقت فراغ الطلاب والموظفين ونشاط مقاطع التذكير القصيرة ⚡",
                    isRecommended = false
                )
            )
            "youtube" -> listOf(
                SmartPublishSlot(
                    id = "yt_prime_time",
                    platformId = "youtube",
                    platformName = "YouTube Shorts",
                    timeLabel = "7:00 م (الذروة الأسبوعية)",
                    hourOfDay = 19,
                    minute = 0,
                    rationale = "أقوى وقت لفهرسة يوتيوب Shorts واقتراح الفيديوهات في الصفحة الرئيسية 🚀",
                    isRecommended = true
                ),
                SmartPublishSlot(
                    id = "yt_friday_special",
                    platformId = "youtube",
                    platformName = "YouTube Shorts",
                    timeLabel = "11:30 ص (قبل الجمعة / الظهر)",
                    hourOfDay = 11,
                    minute = 30,
                    rationale = "مناسب جداً للخطب القصيرة وتلاوات الكهف ومحتوى الجمعة 🕌",
                    isRecommended = false
                ),
                SmartPublishSlot(
                    id = "yt_night_chill",
                    platformId = "youtube",
                    platformName = "YouTube Shorts",
                    timeLabel = "10:00 م (هدوء الليل)",
                    hourOfDay = 22,
                    minute = 0,
                    rationale = "تفاعل مرتفع على قصص الأنبياء والتأملات الوجدانية الطويلة 📖",
                    isRecommended = false
                )
            )
            "instagram" -> listOf(
                SmartPublishSlot(
                    id = "ig_reels_golden",
                    platformId = "instagram",
                    platformName = "Instagram Reels",
                    timeLabel = "9:00 م (التوقيت الذهبي)",
                    hourOfDay = 21,
                    minute = 0,
                    rationale = "أعلى معدل للحفظ (Saves) والمشاركة في الرسائل المباشرة (DMs) 💬",
                    isRecommended = true
                ),
                SmartPublishSlot(
                    id = "ig_morning_quote",
                    platformId = "instagram",
                    platformName = "Instagram Reels",
                    timeLabel = "6:45 ص (إشراقة الصباح)",
                    hourOfDay = 6,
                    minute = 45,
                    rationale = "بطاقات الأحاديث والخواطر التحفيزية الصباحية ☀️",
                    isRecommended = false
                )
            )
            "twitter" -> listOf(
                SmartPublishSlot(
                    id = "x_noon_pulse",
                    platformId = "twitter",
                    platformName = "X (Twitter)",
                    timeLabel = "1:15 م (استراحة الظهيرة)",
                    hourOfDay = 13,
                    minute = 15,
                    rationale = "أعلى حركة إعادة نشر وتفاعل نقاشي خلال يوم العمل 🔄",
                    isRecommended = true
                ),
                SmartPublishSlot(
                    id = "x_night_pulse",
                    platformId = "twitter",
                    platformName = "X (Twitter)",
                    timeLabel = "9:30 م (المساحة المسائية)",
                    hourOfDay = 21,
                    minute = 30,
                    rationale = "تفاعل كبير مع السلاسل (Threads) والمقاطع الدعوية المركزة 📜",
                    isRecommended = false
                )
            )
            else -> listOf(
                SmartPublishSlot(
                    id = "fb_evening",
                    platformId = "facebook",
                    platformName = "Facebook Reels",
                    timeLabel = "8:00 م (التجمع العائلي)",
                    hourOfDay = 20,
                    minute = 0,
                    rationale = "أعلى نسبة وصول عضوي ومشاركات عائلية للفيديوهات الهادفة 👥",
                    isRecommended = true
                )
            )
        }

        return baseSlots
    }

    /**
     * Schedules a local smart reminder and notification for ideal publish timing.
     */
    fun isPublishRemindersEnabled(context: Context): Boolean {
        return context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .getBoolean("auto_publish", true)
    }

    /** يضبط منبه النظام لعنصر مجدول (يُستخدم عند الإنشاء وعند الإقلاع). */
    fun rescheduleItemAlarm(context: Context, item: ScheduledPublishItem): Boolean {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return false
            val intent = Intent(context, SmartPublishReminderReceiver::class.java).apply {
                putExtra("item_id", item.id)
                putExtra("title", item.title)
                putExtra("platform_name", item.platformName)
                putExtra("hashtags", item.hashtags)
            }
            val requestCode = (item.id.hashCode() and 0x7FFFFFFF)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                // بلا إذن المنبه الدقيق: منبه تقريبي يفي بالغرض بدل الفشل الصامت
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    item.scheduledTimeMillis,
                    pendingIntent
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    item.scheduledTimeMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    item.scheduledTimeMillis,
                    pendingIntent
                )
            }
            return true
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * ق159: إعادة تأسيس التذكيرات على جدار الساعة بعد تغيير المنطقة
     * الزمنية أو ضبط الساعة: اللحظة المطلقة المخزنة تُطلق التذكير في
     * وقت جدارٍ خاطئ بعد السفر (منبه 20:30 يرن 23:30 بعد ‎+3 ساعات)،
     * وكان مستقبل الإقلاع يستمع لـ BOOT_COMPLETED وحده. كل عنصر له
     * ساعة/دقيقة محفوظتان يُعاد حساب أقرب وقوع مستقبلي لهما في
     * المنطقة الجديدة وتُحفظ الصيغة واللحظة ويُعاد ضبط المنبه؛ وعنصر
     * قديم بلا ساعة/دقيقة (‎−1) يُعاد ضبط منبهه على لحظته كما هي.
     */
    fun rebaseScheduledItemsForTimezoneChange(context: Context) {
        val timeFormat = SimpleDateFormat("h:mm a (EEEE)", Locale.getDefault())
        val now = System.currentTimeMillis()
        val rebased = getScheduledPublishItems(context).map { item ->
            if (item.hourOfDay !in 0..23 || item.minute !in 0..59) {
                item
            } else {
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = now
                    set(Calendar.HOUR_OF_DAY, item.hourOfDay)
                    set(Calendar.MINUTE, item.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
                }
                item.copy(
                    scheduledTimeMillis = calendar.timeInMillis,
                    formattedTime = timeFormat.format(calendar.time)
                )
            }
        }
        saveScheduledList(context, rebased)
        rebased.forEach { rescheduleItemAlarm(context, it) }
    }

    fun schedulePublishReminder(
        context: Context,
        title: String,
        platformId: String,
        platformName: String,
        hourOfDay: Int,
        minute: Int,
        hashtags: String = ""
    ): ScheduledPublishItem {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val timeFormat = SimpleDateFormat("h:mm a (EEEE)", Locale.getDefault())
        val formattedTime = timeFormat.format(calendar.time)

        val scheduledItem = ScheduledPublishItem(
            title = title,
            platformId = platformId,
            platformName = platformName,
            scheduledTimeMillis = calendar.timeInMillis,
            formattedTime = formattedTime,
            hashtags = hashtags,
            hourOfDay = hourOfDay,
            minute = minute
        )

        // Save to preferences
        val scheduledList = getScheduledPublishItems(context).toMutableList()
        scheduledList.add(0, scheduledItem)
        saveScheduledList(context, scheduledList)

        // Set AlarmManager alarm (exact when allowed, inexact fallback otherwise)
        rescheduleItemAlarm(context, scheduledItem)

        return scheduledItem
    }

    /** العناصر المستقبلية فقط — المنتهية تُحذف مع منبهاتها تلقائياً. */
    fun getActiveScheduledPublishItems(context: Context): List<ScheduledPublishItem> {
        val now = System.currentTimeMillis()
        val all = getScheduledPublishItems(context)
        val expired = all.filter { it.scheduledTimeMillis <= now }
        if (expired.isEmpty()) return all
        expired.forEach { cancelScheduledPublish(context, it.id) }
        return getScheduledPublishItems(context)
    }

    fun getScheduledPublishItems(context: Context): List<ScheduledPublishItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SCHEDULED_ITEMS, "[]") ?: "[]"
        val list = mutableListOf<ScheduledPublishItem>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ScheduledPublishItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        platformId = obj.getString("platformId"),
                        platformName = obj.getString("platformName"),
                        scheduledTimeMillis = obj.getLong("scheduledTimeMillis"),
                        formattedTime = obj.getString("formattedTime"),
                        hashtags = obj.optString("hashtags", ""),
                        hourOfDay = obj.optInt("hourOfDay", -1),
                        minute = obj.optInt("minute", -1)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun saveScheduledList(context: Context, list: List<ScheduledPublishItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("platformId", item.platformId)
                put("platformName", item.platformName)
                put("scheduledTimeMillis", item.scheduledTimeMillis)
                put("formattedTime", item.formattedTime)
                put("hashtags", item.hashtags)
                put("hourOfDay", item.hourOfDay)
                put("minute", item.minute)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_SCHEDULED_ITEMS, array.toString()).apply()
    }

    fun cancelScheduledPublish(context: Context, itemId: String) {
        val list = getScheduledPublishItems(context).toMutableList()
        list.removeAll { it.id == itemId }
        saveScheduledList(context, list)

        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, SmartPublishReminderReceiver::class.java)
            val requestCode = (itemId.hashCode() and 0x7FFFFFFF)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun triggerPublishNotification(context: Context, title: String, platformName: String, hashtags: String) {
        if (!isPublishRemindersEnabled(context)) return
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager != null) {
                QabasNotificationHelper.ensureChannel(context, QabasNotificationHelper.CHANNEL_PUBLISH_SCHEDULER)

                val notificationTitle = "⏰ حان موعد النشر المثالي على $platformName!"
                // ق159: كان النص يدّعي أن «الخوارزمية في قمة نشاطها
                // الآن» — قياس غير موجود. التذكير موعد اختاره المستخدم.
                val notificationBody = "«$title» — حان الموعد الذي حددته للنشر"

                val builder = NotificationCompat.Builder(context, QabasNotificationHelper.CHANNEL_PUBLISH_SCHEDULER)
                    .setSmallIcon(R.drawable.ic_stat_qabas)
                    .setLargeIcon(QabasNotificationHelper.getAppIconBitmap(context))
                    .setColor(QabasNotificationHelper.BRAND_COLOR)
                    .setContentTitle(notificationTitle)
                    .setContentText(notificationBody)
                    .setStyle(NotificationCompat.BigTextStyle().bigText("$notificationBody\n\nالوسوم المقترحة: $hashtags"))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)

                val notifId = QabasNotificationHelper.nextNotificationId(context) ?: return
                notificationManager.notify(notifId, builder.build())
                
                // Add to internal app notification feed as well
                AppNotificationService.sendNotification(
                    context,
                    notificationTitle,
                    notificationBody,
                    isTrendAlert = true
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}

class SmartPublishReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val title = intent?.getStringExtra("title") ?: "فيديو دعوي جديد"
        val platformName = intent?.getStringExtra("platform_name") ?: "منصات التواصل"
        val hashtags = intent?.getStringExtra("hashtags") ?: "#خادم_المسلم #أثر_لا_ينقطع"
        SocialAccountManager.triggerPublishNotification(context, title, platformName, hashtags)
    }
}

/** يعيد ضبط منبهات التذكيرات المستقبلية بعد إعادة تشغيل الجهاز، ويعيد تأسيسها على جدار الساعة بعد تغيير المنطقة الزمنية أو ضبط الساعة (ق159). */
class PublishScheduleBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            when (intent?.action) {
                Intent.ACTION_BOOT_COMPLETED -> {
                    val items = SocialAccountManager.getActiveScheduledPublishItems(context)
                    items.forEach { SocialAccountManager.rescheduleItemAlarm(context, it) }
                }
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED ->
                    SocialAccountManager.rebaseScheduledItemsForTimezoneChange(context)
            }
        } catch (_: Exception) { }
    }
}
