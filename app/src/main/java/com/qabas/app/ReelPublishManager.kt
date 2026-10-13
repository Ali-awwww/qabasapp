package com.qabas.app

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * مدير قيود وضوابط النشر والرقابة الشرعية وإمكانيات المطور الحصرية لقسم الريلز في تطبيق قبس
 */
object ReelPublishManager {

    private const val TAG = "ReelPublishManager"
    private const val PREFS_NAME = "qabas_reels_publish_prefs"

    // Quotas & Limits
    const val DAILY_LIMIT_REGULAR = 3
    const val DAILY_LIMIT_DEVELOPER = 999
    const val COOLDOWN_REGULAR_MS = 15 * 60 * 1000L // 15 minutes between posts
    const val MAX_REGULAR_VIDEO_SECONDS = 90
    const val MAX_DEVELOPER_VIDEO_SECONDS = 600

    // Keys
    private const val KEY_LAST_PUBLISH_TS = "last_publish_timestamp"
    private const val KEY_TODAY_DATE = "today_date_str"
    private const val KEY_TODAY_COUNT = "today_publish_count"

    sealed class PublishStatus {
        data class Allowed(val remainingToday: Int, val isDeveloper: Boolean) : PublishStatus()
        data class DailyLimitExceeded(val maxLimit: Int) : PublishStatus()
        data class CooldownActive(val remainingMinutes: Int) : PublishStatus()
        data class Error(val message: String) : PublishStatus()
    }

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String, val detailedAyah: String = "") : ValidationResult()
    }

    /**
     * التحقق مما إذا كان المستخدم يملك صلاحيات المطور (Developer / SuperUser)
     */
    fun isDeveloperUser(context: Context, email: String?): Boolean {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val devFlag = prefs.getBoolean("is_developer", false) || prefs.getBoolean("is_admin", false)
        val cleanEmail = email?.trim()?.lowercase().orEmpty()
        val isDevEmail = cleanEmail == "xman88371@gmail.com" ||
            cleanEmail == "aly750834@gmail.com" ||
            cleanEmail.startsWith("dev@") ||
            cleanEmail.contains("developer") ||
            cleanEmail.contains("admin") ||
            CloudServices.isOwnerAccount(cleanEmail)

        return devFlag || isDevEmail
    }

    /**
     * التحقق مما إذا كان المستخدم الحالي هو صاحب وناشر هذا المقطع
     */
    fun isReelOwner(context: Context, email: String?, userId: String?, reel: SupabaseServices.ReelRow): Boolean {
        val cleanEmail = email?.trim()?.lowercase().orEmpty()
        val cleanUserId = userId?.trim().orEmpty()
        val reelUserId = reel.userId.trim()

        if (cleanUserId.isNotEmpty() && reelUserId == cleanUserId) return true
        if (cleanEmail.isNotEmpty() && (reelUserId.equals(cleanEmail, ignoreCase = true) || reelUserId == cleanEmail)) return true

        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val savedName = prefs.getString("user_name", "")?.trim().orEmpty()
        if (savedName.isNotEmpty() && !reel.userName.isNullOrBlank() && reel.userName.trim().equals(savedName, ignoreCase = true)) {
            return true
        }

        val myPublishedReelIds = prefs.getStringSet("my_published_reel_ids", emptySet()) ?: emptySet()
        if (myPublishedReelIds.contains(reel.id)) return true

        return false
    }

    /**
     * التحقق من أهلية حذف المقطع: مسموح لصاحب الفيديو، للمطور، ولأي مقطع تجريبي/اختباري
     */
    fun canDeleteReel(context: Context, email: String?, userId: String?, reel: SupabaseServices.ReelRow): Boolean {
        // الفيديوهات التجريبية والاختبارية مسموح حذفها دائماً لتنظيف البيئة
        val id = reel.id.lowercase()
        val isTestReel = id.startsWith("mock_") ||
            id.startsWith("demo_") ||
            id.startsWith("starter_") ||
            id.startsWith("test_") ||
            id.startsWith("pub_") ||
            id.startsWith("authentic_") ||
            reel.userId.startsWith("qabas_") ||
            reel.userId == "guest" ||
            reel.userId == "system" ||
            reel.videoUrl.contains("mixkit.co")

        return isTestReel || isDeveloperUser(context, email) || isReelOwner(context, email, userId, reel)
    }

    /**
     * فحص إمكانية النشر بناءً على الحصة اليومية والفاصل الزمني
     */
    fun checkPublishEligibility(context: Context, email: String?): PublishStatus {
        val isDev = isDeveloperUser(context, email)
        if (isDev) {
            return PublishStatus.Allowed(remainingToday = DAILY_LIMIT_DEVELOPER, isDeveloper = true)
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val savedDate = prefs.getString(KEY_TODAY_DATE, "") ?: ""

        val todayCount = if (savedDate == todayStr) {
            prefs.getInt(KEY_TODAY_COUNT, 0)
        } else {
            0
        }

        // 1. فحص الحد اليومي
        if (todayCount >= DAILY_LIMIT_REGULAR) {
            return PublishStatus.DailyLimitExceeded(DAILY_LIMIT_REGULAR)
        }

        // 2. فحص الفاصل الزمني (Cooldown)
        val lastPublish = prefs.getLong(KEY_LAST_PUBLISH_TS, 0L)
        val elapsed = System.currentTimeMillis() - lastPublish
        if (lastPublish > 0 && elapsed < COOLDOWN_REGULAR_MS) {
            val remainingMin = ((COOLDOWN_REGULAR_MS - elapsed) / 60000L).toInt() + 1
            return PublishStatus.CooldownActive(remainingMin)
        }

        val remaining = (DAILY_LIMIT_REGULAR - todayCount).coerceAtLeast(0)
        return PublishStatus.Allowed(remainingToday = remaining, isDeveloper = false)
    }

    /**
     * تدقيق وفحص محتوى المقطع قبل الإرسال بالرقابة الشرعية الصارمة
     */
    fun validateContent(
        title: String,
        description: String?,
        category: String,
        videoUriOrUrl: String,
        hasAcceptedPledge: Boolean,
        isDeveloper: Boolean
    ): ValidationResult {
        // 1. التعهد الشرعي الإلزامي
        if (!hasAcceptedPledge) {
            return ValidationResult.Invalid(
                reason = "يجب تأكيد التعهد الشرعي بأن المقطع خالص لوجه الله وخالٍ تماماً من الموسيقى والمعازف والمنكرات قبل النشر."
            )
        }

        val cleanTitle = title.trim()
        if (cleanTitle.length < 3) {
            return ValidationResult.Invalid("عنوان المقطع قصير جداً، يرجى كتابة عنوان معبر (3 أحرف على الأقل).")
        }
        if (cleanTitle.length > 100) {
            return ValidationResult.Invalid("عنوان المقطع طويل جداً (الحد الأقصى 100 حرف).")
        }

        val cleanPath = videoUriOrUrl.trim()
        if (cleanPath.isBlank()) {
            return ValidationResult.Invalid("يرجى اختيار مقطع فيديو من المعرض أو تحديد مقطع إيماني.")
        }

        // 2. فحص الرقابة الشرعية الشامل ضد الموسيقى والآثام والمعاصي
        val auditResult = SpiritualSafetyGuard.performSpiritualAudit(
            title = cleanTitle,
            description = description,
            tags = listOf(category),
            category = category
        )

        if (auditResult is SpiritualSafetyGuard.ModerationResult.Rejected) {
            return ValidationResult.Invalid(
                reason = auditResult.reason,
                detailedAyah = auditResult.detailedAyah
            )
        }

        return ValidationResult.Valid
    }

    /**
     * تسجيل عملية نشر ناجحة وتحديث العدادات
     */
    fun recordSuccessfulPublish(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val savedDate = prefs.getString(KEY_TODAY_DATE, "") ?: ""

        val currentCount = if (savedDate == todayStr) prefs.getInt(KEY_TODAY_COUNT, 0) else 0

        prefs.edit()
            .putString(KEY_TODAY_DATE, todayStr)
            .putInt(KEY_TODAY_COUNT, currentCount + 1)
            .putLong(KEY_LAST_PUBLISH_TS, System.currentTimeMillis())
            .apply()
    }

    /**
     * رفع المقطع السحابي أو المحلي وتخزينه في Supabase
     */
    suspend fun publishReelWithUpload(
        context: Context,
        userId: String,
        userName: String,
        email: String?,
        title: String,
        description: String?,
        videoUri: Uri?,
        directUrl: String?,
        thumbnailBytes: ByteArray?,
        category: String,
        isFeaturedByDev: Boolean = false,
        userAvatar: String? = null,
        onProgressUpdate: (String) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val isDev = isDeveloperUser(context, email)

        var finalVideoUrl = directUrl?.trim().orEmpty()
        var finalThumbUrl: String? = null

        // 1. في حال تم اختيار ملف فيديو محلي من المعرض
        if (videoUri != null) {
            onProgressUpdate("جاري تجهيز وقراءة ملف الفيديو من الجهاز...")
            val videoBytes = runCatching {
                context.contentResolver.openInputStream(videoUri)?.use { it.readBytes() }
            }.getOrNull()

            if (videoBytes != null && videoBytes.isNotEmpty()) {
                val timestamp = System.currentTimeMillis()
                val fileName = "reel_${userId.take(8)}_$timestamp.mp4"
                val thumbFileName = "thumb_${userId.take(8)}_$timestamp.jpg"

                // حفظ نسخة محلية دائمة في مساحة التطبيق لضمان التشغيل الفوري حتى دون إنترنت
                val localReelDir = File(context.filesDir, "published_reels")
                if (!localReelDir.exists()) localReelDir.mkdirs()
                val localVideoFile = File(localReelDir, fileName)
                FileOutputStream(localVideoFile).use { it.write(videoBytes) }
                finalVideoUrl = localVideoFile.toURI().toString()

                // رفع الفيديو لسحابة Supabase Storage إن كانت متاحة
                if (SupabaseServices.isSupabaseAvailable) {
                    onProgressUpdate("جاري رفع الفيديو إلى سحابة قبس الموثوقة...")
                    val uploadedVideoUrl = SupabaseServices.Storage.uploadFile(
                        bucketName = "user-uploads",
                        path = "reels/$userId/$fileName",
                        data = videoBytes
                    ) ?: SupabaseServices.Storage.uploadFile(
                        bucketName = "media-assets",
                        path = "reels/$userId/$fileName",
                        data = videoBytes
                    )

                    if (!uploadedVideoUrl.isNullOrBlank()) {
                        finalVideoUrl = uploadedVideoUrl
                    }

                    // رفع الصورة المصغرة إن وجدت
                    if (thumbnailBytes != null && thumbnailBytes.isNotEmpty()) {
                        onProgressUpdate("جاري رفع غلاف المقطع...")
                        finalThumbUrl = SupabaseServices.Storage.uploadFile(
                            bucketName = "media-assets",
                            path = "thumbnails/$userId/$thumbFileName",
                            data = thumbnailBytes
                        )
                    }
                }
            }
        }

        if (finalVideoUrl.isBlank()) {
            Log.e(TAG, "Publish failed: video url is empty")
            return@withContext false
        }

        onProgressUpdate("جاري توثيق المقطع في مجتمع قبس الإيماني...")

        val finalTags = mutableListOf(category, "مفحوص_شرعياً")
        if (isDev) {
            finalTags.add("موثق_مطور")
            if (isFeaturedByDev) {
                finalTags.add("مميز_مثبت")
            }
        }

        val cleanTitle = if (isDev && isFeaturedByDev) "⭐ $title" else title

        // حلّ الصورة الرمزية للناشر: المعامل الممرر أولاً، ثم المحفوظة محلياً، ثم حساب Firebase
        val resolvedAvatar: String? = userAvatar?.takeIf { it.isNotBlank() }
            ?: runCatching {
                context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
                    .getString("user_avatar_uri", null)?.takeIf { it.isNotBlank() }
            }.getOrNull()
            ?: runCatching { FirebaseManager.getAuth().currentUser?.photoUrl?.toString() }.getOrNull()

        val success = SupabaseServices.Profiles.saveReel(
            userId = userId,
            title = cleanTitle,
            description = description,
            videoUrl = finalVideoUrl,
            thumbnailUrl = finalThumbUrl,
            tags = finalTags,
            userName = userName,
            userAvatar = resolvedAvatar
        )

        // حفظ المقطع أيضاً في السجل المحلي للمستخدم
        ReelPreferencesManager.recordWatch(
            context = context,
            reelId = "pub_${System.currentTimeMillis()}",
            title = cleanTitle,
            userName = userName,
            userAvatar = resolvedAvatar,
            videoUrl = finalVideoUrl,
            category = category,
            durationSec = 10
        )

        if (success) {
            val pubPrefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            val currentMyReels = pubPrefs.getStringSet("my_published_reel_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
            currentMyReels.add("pub_${System.currentTimeMillis()}")
            pubPrefs.edit().putStringSet("my_published_reel_ids", currentMyReels).apply()

            if (!isDev) {
                recordSuccessfulPublish(context)
            }
        }

        return@withContext success
    }

    /**
     * حذف مقطع من المنصة: متاح لصاحب الفيديو وللمطور
     */
    suspend fun deleteReel(
        context: Context,
        email: String?,
        userId: String?,
        reel: SupabaseServices.ReelRow
    ): Boolean = withContext(Dispatchers.IO) {
        if (!canDeleteReel(context, email, userId, reel)) {
            Log.w(TAG, "deleteReel denied: neither owner nor developer for reel ${reel.id}")
            return@withContext false
        }

        var remoteSuccess = false
        if (SupabaseServices.isSupabaseAvailable) {
            remoteSuccess = SupabaseServices.Profiles.deleteReel(reel.id)
        }

        // إزالة الملف المحلي إن وجد
        runCatching {
            if (reel.videoUrl.startsWith("file:") || reel.videoUrl.startsWith("/")) {
                val path = reel.videoUrl.removePrefix("file://")
                val f = File(path)
                if (f.exists()) f.delete()
            }
        }

        // إزالة من قائمة مقاطعي المنشورة والسجل
        val pubPrefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val currentMyReels = pubPrefs.getStringSet("my_published_reel_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        currentMyReels.remove(reel.id)
        pubPrefs.edit().putStringSet("my_published_reel_ids", currentMyReels).apply()

        ReelPreferencesManager.removeFromWatchHistory(context, reel.id)

        remoteSuccess || !SupabaseServices.isSupabaseAvailable
    }

    /**
     * [صلاحية خاصة للمطور] حذف أي مقطع من سحابة Supabase مباشرة
     */
    suspend fun deleteReelAsDev(context: Context, email: String?, reelId: String): Boolean {
        if (!isDeveloperUser(context, email)) return false
        return SupabaseServices.Profiles.deleteReel(reelId)
    }

    /**
     * [صلاحية خاصة للمطور] تفعيل / حظر مقطع من الظهور للعامة
     */
    suspend fun toggleReelActiveAsDev(context: Context, email: String?, reelId: String, isActive: Boolean): Boolean {
        if (!isDeveloperUser(context, email)) return false
        return SupabaseServices.Profiles.toggleReelActive(reelId, isActive)
    }
}
