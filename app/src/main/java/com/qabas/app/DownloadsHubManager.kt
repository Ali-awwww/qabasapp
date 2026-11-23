package com.qabas.app

import android.content.Context
import android.os.StatFs
import android.util.Log
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * بيانات وإحصائيات الذاكرة التخزينية في الهاتف
 */
data class StorageMetrics(
    val totalBytes: Long = 0L,
    val availableBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val llmModelsBytes: Long = 0L,
    val reelsBytes: Long = 0L,
    val otherMediaBytes: Long = 0L,
    val updatesBytes: Long = 0L,
    val totalQabasBytes: Long = 0L
) {
    val totalFormatted: String get() = formatBytes(totalBytes)
    val availableFormatted: String get() = formatBytes(availableBytes)
    val llmModelsFormatted: String get() = formatBytes(llmModelsBytes)
    val reelsFormatted: String get() = formatBytes(reelsBytes)
    val updatesFormatted: String get() = formatBytes(updatesBytes)
    val totalQabasFormatted: String get() = formatBytes(totalQabasBytes)



    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 بايت"
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format(java.util.Locale.US, "%.1f ج.ب", gb)
                mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f م.ب", mb)
                kb >= 1.0 -> String.format(java.util.Locale.US, "%.0f ك.ب", kb)
                else -> "$bytes بايت"
            }
        }
    }
}

/**
 * عنصر تقدم وحالة تنزيل تحديث التطبيق الرسمي في مركز التنزيلات
 */
data class AppUpdateDownloadItem(
    val versionName: String,
    val versionCode: Int,
    val releaseNotes: String = "",
    val apkUrl: String = "",
    val deltaUrl: String? = null,
    val totalBytes: Long = 0L,
    /** الحجم المتوقع للحزمة الكاملة من الريليز — ثابت ولا يتغير مع تقدم التنزيل */
    val expectedApkSize: Long = 0L,
    val downloadedBytes: Long = 0L,
    val progress: Float = 0f,
    val speedBytesPerSec: Long = 0L,
    val phase: String = "IDLE", // IDLE, CHECKING, DOWNLOADING_DELTA, APPLYING_PATCH, DOWNLOADING_FULL, COMPLETED, ERROR, CANCELLED
    val isDownloading: Boolean = false,
    val isCompleted: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String? = null,
    val localApkPath: String? = null,
    val detectedAt: Long = System.currentTimeMillis()
) {
    val formattedSize: String get() = StorageMetrics.formatBytes(totalBytes)
    val formattedDownloaded: String get() = StorageMetrics.formatBytes(downloadedBytes)
    val formattedSpeed: String get() = if (speedBytesPerSec > 0) "${StorageMetrics.formatBytes(speedBytesPerSec)}/ث" else "0 ك.ب/ث"
    val percentInt: Int get() = (progress * 100).toInt().coerceIn(0, 100)
    val fileExists: Boolean get() = localApkPath?.let { File(it).exists() } ?: false
}

/**
 * عنصر ريل منزّل ومحفوظ محلياً في الذاكرة
 */
data class DownloadedReelItem(
    val id: String,
    val title: String,
    val author: String,
    val videoUrl: String,
    val localFilePath: String,
    val thumbnailUrl: String? = null,
    val fileSizeBytes: Long = 0L,
    val downloadedAt: Long = System.currentTimeMillis(),
    val fileExistsAtLoad: Boolean = File(localFilePath).exists()
) {
    val formattedSize: String get() = StorageMetrics.formatBytes(fileSizeBytes)
    val fileExists: Boolean get() = fileExistsAtLoad && File(localFilePath).exists()
}

/**
 * تقدم تنزيل ريل فيديو
 */
data class ReelDownloadProgress(
    val reelId: String,
    val title: String,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedKbps: Long = 0L,
    val isCompleted: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String? = null
)

/**
 * نتيجة تنزيل قابل للاستئناف عبر HTTP Range.
 * totalBytes = 0 تعني أن السيرفر لم يرسل حجماً معروفاً (تقدّم غير محدد، لا رقم مخمّن).
 */
data class ResumableDownloadResult(
    val success: Boolean,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val errorMessage: String? = null
)

/**
 * عنصر وسائط أو مقطع صوتي منزّل
 */
data class DownloadedMediaItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val localFilePath: String,
    val fileSizeBytes: Long = 0L,
    val category: String = "audio",
    val downloadedAt: Long = System.currentTimeMillis(),
    val sourceUrl: String? = null,
    val fileExistsAtLoad: Boolean = File(localFilePath).exists()
) {
    val formattedSize: String get() = StorageMetrics.formatBytes(fileSizeBytes)
    val fileExists: Boolean get() = fileExistsAtLoad && File(localFilePath).exists()
}

/**
 * المدير المركزي للتنزيلات، فيديوهات الريلز، وإدارة سعة الذاكرة
 */
object DownloadsHubManager {
    private const val TAG = "DownloadsHubManager"
    private const val MIN_FREE_SPACE_FOR_REEL_BYTES = 60L * 1024L * 1024L
    private const val PREFS_NAME = "qabas_downloads_hub_prefs"
    private const val KEY_DOWNLOADED_REELS = "downloaded_reels_json"
    private const val KEY_DOWNLOADED_MEDIA = "downloaded_media_json"
    private const val KEY_WIFI_ONLY_DOWNLOADS = "wifi_only_downloads"
    private const val KEY_RESUME_META_PREFIX = "resume_meta_"
    private const val REELS_DIR_NAME = "downloads/reels"
    private const val MEDIA_DIR_NAME = "downloads/media"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .build()
    }

    private val _storageMetrics = MutableStateFlow(StorageMetrics())
    val storageMetrics: StateFlow<StorageMetrics> = _storageMetrics.asStateFlow()

    private val _downloadedReels = MutableStateFlow<List<DownloadedReelItem>>(emptyList())
    val downloadedReels: StateFlow<List<DownloadedReelItem>> = _downloadedReels.asStateFlow()

    private val _downloadedMedia = MutableStateFlow<List<DownloadedMediaItem>>(emptyList())
    val downloadedMedia: StateFlow<List<DownloadedMediaItem>> = _downloadedMedia.asStateFlow()

    private val _activeReelDownloads = MutableStateFlow<Map<String, ReelDownloadProgress>>(emptyMap())
    val activeReelDownloads: StateFlow<Map<String, ReelDownloadProgress>> = _activeReelDownloads.asStateFlow()

    // تنزيلات الوسائط العامة (مثل أصوات الأذان) — نفس بنية عدّاد الريلز، عبر مدير التنزيلات نفسه
    private val _activeMediaDownloads = MutableStateFlow<Map<String, ReelDownloadProgress>>(emptyMap())
    val activeMediaDownloads: StateFlow<Map<String, ReelDownloadProgress>> = _activeMediaDownloads.asStateFlow()

    private val _appUpdateDownload = MutableStateFlow<AppUpdateDownloadItem?>(null)
    val appUpdateDownload: StateFlow<AppUpdateDownloadItem?> = _appUpdateDownload.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private var isListeningToUpdateManager = false
    private var appContextRef: Context? = null

    /**
     * تهيئة المدير وتحميل قائمة الملفات وتحديث حسابات الذاكرة ومراقبة التحديثات
     */
    fun init(context: Context) {
        appContextRef = context.applicationContext
        loadSavedDownloads(context)
        refreshStorageMetrics(context)
        setupUpdateObserver(context.applicationContext)
        detectExistingUpdateFile(context)
    }

    /**
     * فحص ما إذا كان هناك ملف تحديث جاهز في الكاش
     */
    private fun detectExistingUpdateFile(context: Context) {
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (updatesDir.exists() && updatesDir.isDirectory) {
                val apkFiles = updatesDir.listFiles { f -> f.extension == "apk" && f.length() > 1_000_000 }
                val latestApk = apkFiles?.maxByOrNull { it.lastModified() }
                if (latestApk != null) {
                    val currentVersionCode = UpdateManager.getCurrentVersionCode(context)
                    if (_appUpdateDownload.value == null) {
                        val vName = latestApk.name
                            .removePrefix("qabas-")
                            .removeSuffix("-patched.apk")
                            .removeSuffix(".apk")
                        _appUpdateDownload.value = AppUpdateDownloadItem(
                            versionName = vName.ifBlank { "أحدث إصدار" },
                            versionCode = currentVersionCode + 1,
                            releaseNotes = "حزمة التحديث منزّلة وجاهزة للتثبيت الفوري على هاتفك 🚀",
                            totalBytes = latestApk.length(),
                            expectedApkSize = latestApk.length(),
                            downloadedBytes = latestApk.length(),
                            progress = 1f,
                            phase = "COMPLETED",
                            isDownloading = false,
                            isCompleted = true,
                            localApkPath = latestApk.absolutePath
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting existing update file: ${e.message}")
        }
    }

    /**
     * ربط تقدم التنزيل في الوقت الفعلي مع UpdateManager
     */
    private fun setupUpdateObserver(context: Context) {
        if (isListeningToUpdateManager) return
        isListeningToUpdateManager = true

        scope.launch {
            UpdateManager.downloadProgress.collect { p ->
                if (p != null) {
                    val current = _appUpdateDownload.value
                    if (current != null) {
                        val newProgress = if (p.totalBytes > 0) {
                            (p.bytesDownloaded.toFloat() / p.totalBytes).coerceIn(0f, 1f)
                        } else {
                            (p.percent / 100f).coerceIn(0f, 1f)
                        }
                        _appUpdateDownload.value = current.copy(
                            downloadedBytes = p.bytesDownloaded,
                            totalBytes = if (p.totalBytes > 0) p.totalBytes else current.totalBytes,
                            progress = newProgress,
                            speedBytesPerSec = p.speedBytesPerSec,
                            phase = p.phase.name,
                            isDownloading = true,
                            isCompleted = false,
                            isError = false
                        )
                    }
                }
            }
        }

        scope.launch {
            UpdateManager.downloadResult.collect { res ->
                if (res != null) {
                    val (_, ok, msg) = res
                    val current = _appUpdateDownload.value
                    if (current != null) {
                        val apkFile = UpdateManager.getValidatedDownloadedApk(
                            context, current.versionName, current.versionCode, current.expectedApkSize
                        )
                        if (ok && apkFile != null && apkFile.exists()) {
                            _appUpdateDownload.value = current.copy(
                                downloadedBytes = apkFile.length(),
                                totalBytes = apkFile.length(),
                                progress = 1f,
                                speedBytesPerSec = 0L,
                                phase = "COMPLETED",
                                isDownloading = false,
                                isCompleted = true,
                                isError = false,
                                localApkPath = apkFile.absolutePath
                            )
                            refreshStorageMetrics(context)
                        } else if (!ok) {
                            _appUpdateDownload.value = current.copy(
                                isDownloading = false,
                                isError = true,
                                errorMessage = msg,
                                phase = "ERROR"
                            )
                        }
                    }
                }
            }
        }
    }

    /** تسجيل التحديث الجاري حتى يظهر في مركز التنزيلات بصرف النظر عن مصدر بدئه. */
    fun registerAppUpdateDownload(
        context: Context,
        update: UpdateManager.UpdateInfo,
        forceFull: Boolean
    ) {
        setupUpdateObserver(context.applicationContext)
        val targetSize = if (!forceFull && update.deltaUsable) update.deltaSize else update.apkSize
        _appUpdateDownload.value = AppUpdateDownloadItem(
            versionName = update.versionName,
            versionCode = update.versionCode,
            releaseNotes = update.releaseNotes,
            apkUrl = update.apkUrl,
            deltaUrl = update.deltaUrl,
            totalBytes = targetSize,
            expectedApkSize = update.apkSize,
            downloadedBytes = 0L,
            progress = 0f,
            phase = "DOWNLOADING",
            isDownloading = true,
            isCompleted = false,
            isError = false
        )
    }

    /**
     * الحصول على مجلد ريلز المحفوظة
     */
    fun getReelsDirectory(context: Context): File {
        val dir = File(context.filesDir, REELS_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * الحصول على مجلد الوسائط الأخرى
     */
    fun getMediaDirectory(context: Context): File {
        val dir = File(context.filesDir, MEDIA_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * خيار «التنزيل عبر Wi-Fi فقط» للتنزيلات الخلفية (WorkManager).
     * لا يوجد إعداد سابق له في التطبيق، فيُحفظ هنا افتراضياً على «إيقاف»
     * (أي شبكة) ويحترمه عامل التنزيل عند تفعيله.
     */
    fun isWifiOnlyDownloads(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_WIFI_ONLY_DOWNLOADS, false)
    }

    /** ملف الوسائط الموحّد لمعرّف ما — نفس المفتاح لكل مسارات تنزيل الصوت. */
    internal fun mediaFileForId(context: Context, mediaId: String, extension: String): File {
        val safeId = mediaId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(getMediaDirectory(context), "media_$safeId.$extension")
    }

    /**
     * مفتاح إزالة التكرار للصوت: نفس الرابط يعطي دائماً نفس المعرّف ونفس الملف،
     * فلا يمكن «تنزيل» الصوت نفسه مرتين في مكانين مختلفين.
     */
    fun audioMediaIdForUrl(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(url.trim().toByteArray(Charsets.UTF_8))
        return "audio_" + digest.take(8).joinToString("") { "%02x".format(it) }
    }

    /** الملف الموحّد لصوت ما في مخزن التطبيق الداخلي (أو null إن لم يكن منزّلاً). */
    fun audioFileForUrl(context: Context, url: String): File? {
        val file = File(getMediaDirectory(context), "media_${audioMediaIdForUrl(url)}.mp3")
        return file.takeIf { it.exists() && it.length() > 0 }
    }

    // ---- حالة الاستئناف المحفوظة للملفات الجزئية (تبقى بعد موت العملية) ----

    private data class ResumeMeta(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val url: String,
        val etag: String?,
        val lastModified: String?
    )

    private fun loadResumeMeta(context: Context, fileKey: String): ResumeMeta? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RESUME_META_PREFIX + fileKey, null) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            ResumeMeta(
                downloadedBytes = obj.optLong("downloadedBytes", 0L),
                totalBytes = obj.optLong("totalBytes", 0L),
                url = obj.optString("url", ""),
                etag = obj.optString("etag").takeIf { it.isNotBlank() },
                lastModified = obj.optString("lastModified").takeIf { it.isNotBlank() }
            )
        }.getOrNull()
    }

    private fun saveResumeMeta(context: Context, fileKey: String, meta: ResumeMeta) {
        val obj = JSONObject().apply {
            put("downloadedBytes", meta.downloadedBytes)
            put("totalBytes", meta.totalBytes)
            put("url", meta.url)
            put("etag", meta.etag ?: "")
            put("lastModified", meta.lastModified ?: "")
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_RESUME_META_PREFIX + fileKey, obj.toString()).apply()
    }

    private fun clearResumeMeta(context: Context, fileKey: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_RESUME_META_PREFIX + fileKey).apply()
    }

    /**
     * محرك التنزيل القابل للاستئناف المشترك للريلز والوسائط (يستخدمه عامل
     * WorkManager والمسار المباشر معاً):
     * - يرسل `Range: bytes=<المنزّل>-` عند وجود ملف جزئي `.tmp`، ولا يستخدم
     *   `skip()` على مجرى الشبكة إطلاقاً.
     * - لا يُقبل الاستئناف إلا بردّ `206 Partial Content` مع `Content-Range`
     *   يبدأ من الإزاحة المطلوبة وبصمات مطابقة؛ وعند رفض السيرفر للنطاق
     *   (ردّ 200 أو غياب `Content-Range` أو تغيّر الملف) يعيد التنزيل كاملاً
     *   من الصفر بعد حذف الجزئي — إعادة صادقة، لا تخطٍّ صامت.
     * - التقدّم من (المنزّل سابقاً + المقروء) على الحجم الحقيقي؛ وإن غاب
     *   الحجم فالتقدّم غير محدد (total = 0) دون أي رقم مخمّن.
     * - الإلغاء أو الخطأ يُبقي الملف الجزئي وحالة الاستئناف؛ الحذف يتم فقط
     *   بإجراء حذف صريح من المستخدم.
     */
    internal suspend fun downloadFileResumable(
        context: Context,
        fileUrl: String,
        destFile: File,
        maxBytes: Long = Long.MAX_VALUE,
        onProgress: (suspend (downloaded: Long, total: Long) -> Unit)? = null
    ): ResumableDownloadResult = withContext(Dispatchers.IO) {
        if (!fileUrl.startsWith("https://", ignoreCase = true) &&
            !fileUrl.startsWith("http://", ignoreCase = true)
        ) {
            return@withContext ResumableDownloadResult(false, errorMessage = "رابط غير صالح")
        }
        val appContext = context.applicationContext
        val tmpFile = File(destFile.parentFile, "${destFile.name}.tmp")
        val fileKey = destFile.name
        val savedMeta = loadResumeMeta(appContext, fileKey)

        var resumeBytes = if (tmpFile.exists()) tmpFile.length() else 0L
        if (resumeBytes > 0 && savedMeta != null && savedMeta.url.isNotBlank() && savedMeta.url != fileUrl) {
            resumeBytes = 0L
        }
        if (resumeBytes > 0 && savedMeta != null && savedMeta.downloadedBytes > 0) {
            resumeBytes = minOf(resumeBytes, savedMeta.downloadedBytes)
        }
        var expectedTotal = savedMeta?.totalBytes ?: 0L

        // ملف جزئي مكتمل فعلاً — ثبّته دون إعادة تنزيل.
        if (resumeBytes > 0 && expectedTotal > 0 && resumeBytes >= expectedTotal && tmpFile.length() == expectedTotal) {
            if (destFile.exists()) destFile.delete()
            if (tmpFile.renameTo(destFile)) {
                clearResumeMeta(appContext, fileKey)
                return@withContext ResumableDownloadResult(true, destFile.length(), destFile.length())
            }
            resumeBytes = 0L
        }

        var downloadedBytes = resumeBytes
        var totalBytes = expectedTotal
        var responseEtag: String? = null
        var responseLastModified: String? = null
        try {
            val remaining = (expectedTotal - resumeBytes).coerceAtLeast(0L)
            if (expectedTotal > 0) {
                val available = StatFs(destFile.parentFile?.absolutePath ?: appContext.filesDir.absolutePath).availableBytes
                if (available < remaining + 16L * 1024L * 1024L) {
                    return@withContext ResumableDownloadResult(false, resumeBytes, expectedTotal, "المساحة الحرة لا تكفي لهذا الملف")
                }
            }

            val requestBuilder = Request.Builder().url(fileUrl)
            if (resumeBytes > 0) requestBuilder.header("Range", "bytes=$resumeBytes-")
            val response = httpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                saveResumeMeta(appContext, fileKey, ResumeMeta(resumeBytes, expectedTotal, fileUrl, savedMeta?.etag, savedMeta?.lastModified))
                return@withContext ResumableDownloadResult(false, resumeBytes, expectedTotal, "فشل التنزيل: كود ${response.code}")
            }
            val body = response.body ?: run {
                saveResumeMeta(appContext, fileKey, ResumeMeta(resumeBytes, expectedTotal, fileUrl, savedMeta?.etag, savedMeta?.lastModified))
                return@withContext ResumableDownloadResult(false, resumeBytes, expectedTotal, "المحتوى فارغ")
            }

            responseEtag = response.header("ETag")
            responseLastModified = response.header("Last-Modified")
            val contentRange = response.header("Content-Range")
            var appendMode = false
            downloadedBytes = resumeBytes
            if (resumeBytes > 0) {
                val rangeAccepted = response.code == 206 &&
                    contentRange != null &&
                    contentRange.startsWith("bytes $resumeBytes-") &&
                    (savedMeta?.etag == null || responseEtag == null || responseEtag == savedMeta.etag) &&
                    (savedMeta?.lastModified == null || responseLastModified == null || responseLastModified == savedMeta.lastModified)
                if (rangeAccepted) {
                    appendMode = true
                    totalBytes = contentRange.substringAfterLast('/').toLongOrNull()?.takeIf { it > 0 }
                        ?: (body.contentLength().takeIf { it > 0 }?.plus(resumeBytes) ?: expectedTotal)
                } else {
                    // السيرفر رفض النطاق — إعادة كاملة صادقة من الصفر بعد حذف الجزئي.
                    Log.w(TAG, "Server refused Range resume for ${destFile.name} (code ${response.code}); restarting full download from 0")
                    tmpFile.delete()
                    resumeBytes = 0L
                    downloadedBytes = 0L
                    totalBytes = body.contentLength().takeIf { it > 0 } ?: 0L
                }
            } else {
                totalBytes = body.contentLength().takeIf { it > 0 } ?: 0L
            }

            val startTime = System.currentTimeMillis()
            var lastUpdate = startTime
            try {
                body.byteStream().use { input ->
                    FileOutputStream(tmpFile, appendMode).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (downloadedBytes > maxBytes) {
                                throw IllegalStateException("حجم الملف يتجاوز الحد المسموح")
                            }
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate >= 250) {
                                onProgress?.invoke(downloadedBytes, totalBytes)
                                lastUpdate = now
                            }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // الإلغاء يُبقي الملف الجزئي وحالة الاستئناف — يُعاد رميه ليتوقف العمل نظيفاً.
                saveResumeMeta(appContext, fileKey, ResumeMeta(downloadedBytes, totalBytes, fileUrl, responseEtag, responseLastModified))
                throw e
            }
            onProgress?.invoke(downloadedBytes, totalBytes)

            // مجرى منقطع قبل اكتمال الحجم المعلن: لا يُثبَّت ملف ناقص على أنه
            // مكتمل — يُحفظ التقدم ويُعاد كفشل قابل للاستئناف/إعادة المحاولة.
            if (totalBytes > 0 && downloadedBytes < totalBytes) {
                saveResumeMeta(appContext, fileKey, ResumeMeta(downloadedBytes, totalBytes, fileUrl, responseEtag, responseLastModified))
                return@withContext ResumableDownloadResult(false, downloadedBytes, totalBytes, "اكتمل التنزيل ناقصاً — سيُستأنف تلقائياً")
            }

            if (tmpFile.exists() && tmpFile.length() > 0) {
                if (destFile.exists()) destFile.delete()
                tmpFile.renameTo(destFile)
                clearResumeMeta(appContext, fileKey)
                ResumableDownloadResult(true, destFile.length(), totalBytes.takeIf { it > 0 } ?: destFile.length())
            } else {
                ResumableDownloadResult(false, downloadedBytes, totalBytes, "فشل حفظ الملف")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Resumable download failed: ${e.message}", e)
            // يُحفظ التقدم أيضاً عند خطأ مجرى مفاجئ (لا الإلغاء فقط).
            saveResumeMeta(
                appContext, fileKey,
                ResumeMeta(if (tmpFile.exists()) tmpFile.length() else downloadedBytes, totalBytes, fileUrl, responseEtag, responseLastModified)
            )
            ResumableDownloadResult(false, if (tmpFile.exists()) tmpFile.length() else 0L, totalBytes.takeIf { it > 0 } ?: expectedTotal, e.localizedMessage ?: "حدث خطأ أثناء التنزيل")
        }
    }

    // ---- تقارير تقدّم عامل WorkManager إلى تدفقات الواجهة الحالية ----

    internal fun reportWorkerProgress(kind: String, id: String, title: String, downloaded: Long, total: Long, speedKbps: Long = 0L) {
        val progress = ReelDownloadProgress(
            reelId = id, title = title,
            progress = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f,
            downloadedBytes = downloaded, totalBytes = total, speedKbps = speedKbps
        )
        if (kind == DownloadWorker.KIND_MEDIA) {
            _activeMediaDownloads.value = _activeMediaDownloads.value + (id to progress)
        } else {
            _activeReelDownloads.value = _activeReelDownloads.value + (id to progress)
        }
    }

    internal fun reportWorkerError(kind: String, id: String, title: String, message: String) {
        val progress = ReelDownloadProgress(reelId = id, title = title, isError = true, errorMessage = message)
        if (kind == DownloadWorker.KIND_MEDIA) {
            _activeMediaDownloads.value = _activeMediaDownloads.value + (id to progress)
        } else {
            _activeReelDownloads.value = _activeReelDownloads.value + (id to progress)
        }
    }

    internal fun clearWorkerProgress(kind: String, id: String) {
        if (kind == DownloadWorker.KIND_MEDIA) {
            _activeMediaDownloads.value = _activeMediaDownloads.value - id
        } else {
            _activeReelDownloads.value = _activeReelDownloads.value - id
        }
    }

    /**
     * تحديث مقاييس وسعة الذاكرة الحقيقية
     */
    fun refreshStorageMetrics(context: Context) {
        scope.launch {
            try {
                val path = context.filesDir.absolutePath
                val stat = StatFs(path)
                val blockSize = stat.blockSizeLong
                val totalBlocks = stat.blockCountLong
                val availableBlocks = stat.availableBlocksLong

                val totalBytes = totalBlocks * blockSize
                val availableBytes = availableBlocks * blockSize
                val usedBytes = (totalBytes - availableBytes).coerceAtLeast(0L)

                val llmBytes = OnDeviceModelManager.getTotalModelsSizeBytes(context)
                val reelsBytes = calculateDirSize(getReelsDirectory(context))
                val mediaBytes = calculateDirSize(getMediaDirectory(context))
                val updatesDir = File(context.cacheDir, "updates")
                val updatesBytes = calculateDirSize(updatesDir)
                val totalQabas = llmBytes + reelsBytes + mediaBytes + updatesBytes

                _storageMetrics.value = StorageMetrics(
                    totalBytes = totalBytes,
                    availableBytes = availableBytes,
                    usedBytes = usedBytes,
                    llmModelsBytes = llmBytes,
                    reelsBytes = reelsBytes,
                    otherMediaBytes = mediaBytes,
                    updatesBytes = updatesBytes,
                    totalQabasBytes = totalQabas
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating storage metrics: ${e.message}", e)
            }
        }
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists() || !dir.isDirectory) return 0L
        return dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }

    /**
     * تحميل التنزيلات المحفوظة من SharedPreferences
     */
    private fun loadSavedDownloads(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // تحميل الريلز
        val reelsRaw = prefs.getString(KEY_DOWNLOADED_REELS, null)
        if (!reelsRaw.isNullOrBlank()) {
            try {
                val jsonArr = JSONArray(reelsRaw)
                val list = mutableListOf<DownloadedReelItem>()
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    val item = DownloadedReelItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        author = obj.optString("author", "صانع محتوى"),
                        videoUrl = obj.getString("videoUrl"),
                        localFilePath = obj.getString("localFilePath"),
                        thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                        fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
                        downloadedAt = obj.optLong("downloadedAt", System.currentTimeMillis())
                    )
                    // احتفظ بالسجل حتى لو حُذف الملف خارجيًا لعرض حالة «مفقود» وخياراتها.
                    list.add(item)
                }
                _downloadedReels.value = list
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing saved reels: ${e.message}")
            }
        }

        // تحميل الوسائط
        val mediaRaw = prefs.getString(KEY_DOWNLOADED_MEDIA, null)
        if (!mediaRaw.isNullOrBlank()) {
            try {
                val jsonArr = JSONArray(mediaRaw)
                val list = mutableListOf<DownloadedMediaItem>()
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    val item = DownloadedMediaItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        subtitle = obj.optString("subtitle", ""),
                        localFilePath = obj.getString("localFilePath"),
                        fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
                        category = obj.optString("category", "audio"),
                        downloadedAt = obj.optLong("downloadedAt", System.currentTimeMillis()),
                        sourceUrl = obj.optString("sourceUrl").takeIf { it.isNotBlank() }
                    )
                    list.add(item)
                }
                _downloadedMedia.value = list
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing saved media: ${e.message}")
            }
        }
    }

    private fun persistReelsList(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        _downloadedReels.value.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("author", item.author)
                put("videoUrl", item.videoUrl)
                put("localFilePath", item.localFilePath)
                put("thumbnailUrl", item.thumbnailUrl ?: "")
                put("fileSizeBytes", item.fileSizeBytes)
                put("downloadedAt", item.downloadedAt)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_DOWNLOADED_REELS, arr.toString()).apply()
    }

    /**
     * هل الريل منزّل بالفعل في الهاتف؟
     */
    fun isReelDownloaded(reelId: String): Boolean {
        return _downloadedReels.value.any { it.id == reelId && it.fileExists }
    }

    /**
     * تنزيل الريل عبر عامل WorkManager ([DownloadWorker]) بدل كوروتين في الذاكرة:
     * يبقى التنزيل حياً بعد موت العملية وإعادة تشغيل التطبيق، ويحترم قيود الشبكة
     * (Wi-Fi فقط عند تفعيل الخيار)، ويُستأنف عبر HTTP Range من الملف الجزئي.
     * إعادة المحاولة عند فشل الشبكة يتولاها WorkManager (تراجع خطي، 3 محاولات).
     */
    fun downloadReel(
        context: Context,
        reelId: String,
        title: String,
        videoUrl: String,
        thumbnailUrl: String? = null,
        author: String = "صانع محتوى",
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        if (isReelDownloaded(reelId)) {
            onComplete?.invoke(true)
            return
        }

        // فحص أمني: يُمنع جلب الملفات المحلية عبر رابط التحميل (منع ثغرات LFI / Path Traversal)
        if (videoUrl.startsWith("file://") || videoUrl.startsWith("/") || videoUrl.startsWith("content://")) {
            val err = "تم رفض المصدر: لا يمكن تحميل ملفات محلية كروابط ريلز. يجب استخدام روابط https آمنة حصراً."
            updateDownloadError(reelId, title, err)
            onComplete?.invoke(false)
            return
        }

        // الروابط الشبكية: يُشترط أن تبدأ بـ https:// حصراً لضمان التشفير والنقل الآمن
        if (!videoUrl.startsWith("https://", ignoreCase = true)) {
            val err = "تم حظر الرابط: يُشترط استخدام رابط مشفر وآمن (HTTPS)."
            updateDownloadError(reelId, title, err)
            onComplete?.invoke(false)
            return
        }

        if (StatFs(getReelsDirectory(context).path).availableBytes < MIN_FREE_SPACE_FOR_REEL_BYTES) {
            updateDownloadError(reelId, title, "المساحة الحرة غير كافية لتنزيل الريل")
            onComplete?.invoke(false)
            return
        }

        _activeReelDownloads.value = _activeReelDownloads.value + (reelId to ReelDownloadProgress(
            reelId = reelId,
            title = title,
            progress = 0f
        ))
        val workId = DownloadWorker.enqueueReel(context, reelId, title, videoUrl, thumbnailUrl, author)
        observeWorkCompletion(context, DownloadWorker.reelWorkName(reelId), workId, onComplete)
    }

    /**
     * تنزيل ملف وسائط عبر عامل WorkManager (مسار واجهة المركز) — نفس ضمانات
     * [downloadReel]: بقاء بعد موت العملية، قيود شبكة، واستئناف عبر Range.
     * المسار المتزامن [downloadMedia] يبقى للمنادين الذين يحتاجون المسار فوراً
     * (محلّل صوت الأذان)، وهو يستخدم محرك الاستئناف نفسه.
     */
    fun enqueueMediaDownload(
        context: Context,
        mediaId: String,
        title: String,
        fileUrl: String,
        extension: String = "mp3",
        subtitle: String = "",
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val existing = mediaFileForId(context, mediaId, extension)
        if (existing.exists() && existing.length() > 0) {
            onComplete?.invoke(true)
            return
        }
        val byUrl = _downloadedMedia.value.firstOrNull { it.sourceUrl == fileUrl && it.fileExists }
        if (byUrl != null) {
            onComplete?.invoke(true)
            return
        }
        _activeMediaDownloads.value = _activeMediaDownloads.value + (mediaId to ReelDownloadProgress(
            reelId = mediaId, title = title, progress = 0f
        ))
        val workId = DownloadWorker.enqueueMedia(context, mediaId, title, fileUrl, extension, subtitle)
        observeWorkCompletion(context, DownloadWorker.mediaWorkName(mediaId), workId, onComplete)
    }

    /** مراقبة اكتمال عمل WorkManager مرة واحدة واستدعاء onComplete بنتيجته. */
    private fun observeWorkCompletion(
        context: Context,
        workName: String,
        workId: UUID,
        onComplete: ((Boolean) -> Unit)?
    ) {
        if (onComplete == null) return
        scope.launch {
            val finished = WorkManager.getInstance(context.applicationContext)
                .getWorkInfosForUniqueWorkFlow(workName)
                .first { infos -> infos.any { it.id == workId && it.state.isFinished } }
            val info = finished.firstOrNull { it.id == workId }
            withContext(Dispatchers.Main) {
                onComplete.invoke(info?.state == WorkInfo.State.SUCCEEDED)
            }
        }
    }

    private fun updateDownloadError(reelId: String, title: String, message: String) {
        _activeReelDownloads.value = _activeReelDownloads.value + (reelId to ReelDownloadProgress(
            reelId = reelId,
            title = title,
            isError = true,
            errorMessage = message
        ))
    }

    internal fun addDownloadedReel(context: Context, item: DownloadedReelItem) {
        val updated = _downloadedReels.value.filterNot { it.id == item.id } + item
        _downloadedReels.value = updated
        persistReelsList(context)
    }

    /**
     * إلغاء تنزيل ريل جاري (عامل WorkManager) مع الإبقاء على الملف الجزئي
     * ليُستأنف لاحقاً — الإلغاء لا يحذف التقدم أبداً.
     */
    fun cancelReelDownload(reelId: String) {
        appContextRef?.let { DownloadWorker.cancelReel(it, reelId) }
        _activeReelDownloads.value = _activeReelDownloads.value - reelId
    }

    /**
     * تنزيل ملف وسائط عام (صوت أذان سحابي للتخزين المؤقت) مع عدّاد عبر مدير التنزيلات نفسه.
     * يُعيد المسار المحلي عند النجاح أو null.
     */
    suspend fun downloadMedia(
        context: Context,
        mediaId: String,
        title: String,
        fileUrl: String,
        extension: String = "mp3",
        onProgress: ((sent: Long, total: Long) -> Unit)? = null
    ): String? {
        val retryDelaysMillis = longArrayOf(2_000L, 5_000L, 10_000L)
        var result: String? = null
        repeat(3) { attempt ->
            result = downloadMediaOnce(context, mediaId, title, fileUrl, extension, onProgress)
            if (!result.isNullOrBlank()) return result
            if (attempt < retryDelaysMillis.lastIndex) {
                Log.w(TAG, "Adhan download failed; retry ${attempt + 1}/3 for $mediaId")
                // (ر21) لا يُصفَّر شريط التقدم عند إعادة المحاولة: الملف الجزئي
                // باقٍ على القرص والاستئناف يبدأ منه — كان progress = 0f فيكذب
                // الشريط (0٪) بينما البايتات المنزّلة محفوظة فعلاً.
                val prev = _activeMediaDownloads.value[mediaId]
                _activeMediaDownloads.value = _activeMediaDownloads.value + (mediaId to ReelDownloadProgress(
                    reelId = mediaId,
                    title = title,
                    progress = prev?.progress ?: 0f,
                    downloadedBytes = prev?.downloadedBytes ?: 0L,
                    totalBytes = prev?.totalBytes ?: 0L,
                    isError = false,
                    errorMessage = "إعادة المحاولة تلقائيًا (${attempt + 2}/3)…"
                ))
                delay(retryDelaysMillis[attempt])
            }
        }
        return result
    }

    private suspend fun downloadMediaOnce(
        context: Context,
        mediaId: String,
        title: String,
        fileUrl: String,
        extension: String = "mp3",
        onProgress: ((sent: Long, total: Long) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            // كاش موجود؟ أعده فوراً — نفس مفتاح الملف الموحّد لكل مسارات الصوت.
            val existing = mediaFileForId(context, mediaId, extension)
            if (existing.exists() && existing.length() > 0) {
                addDownloadedMedia(
                    context,
                    DownloadedMediaItem(
                        id = mediaId,
                        title = title,
                        subtitle = "صوت أذان",
                        localFilePath = existing.absolutePath,
                        fileSizeBytes = existing.length(),
                        sourceUrl = fileUrl
                    )
                )
                return@withContext existing.absolutePath
            }
            // إزالة تكرار: نفس الرابط منزّل فعلاً تحت معرّف آخر؟ أعده بدل تنزيل ثانٍ.
            val byUrl = _downloadedMedia.value.firstOrNull { it.sourceUrl == fileUrl && it.fileExists }
            if (byUrl != null) return@withContext byUrl.localFilePath
            // ملف محلي أصلاً (file://) — لا تنزيل needed
            if (fileUrl.startsWith("file://") || fileUrl.startsWith("/")) return@withContext fileUrl

            _activeMediaDownloads.value = _activeMediaDownloads.value + (mediaId to ReelDownloadProgress(
                reelId = mediaId, title = title, progress = 0f
            ))
            var lastStateUpdate = System.currentTimeMillis()
            val start = lastStateUpdate
            // محرك الاستئناف المشترك: Range + تقدّم حقيقي (أو غير محدد عند غياب الحجم).
            val result = downloadFileResumable(context, fileUrl, existing) { done, total ->
                onProgress?.invoke(done, total)
                val now = System.currentTimeMillis()
                if (now - lastStateUpdate >= 250) {
                    val speed = if (now > start) ((done / 1024) / ((now - start) / 1000.0)).toLong() else 0L
                    _activeMediaDownloads.value = _activeMediaDownloads.value + (mediaId to ReelDownloadProgress(
                        reelId = mediaId, title = title,
                        progress = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f,
                        downloadedBytes = done, totalBytes = total, speedKbps = speed
                    ))
                    lastStateUpdate = now
                }
            }
            if (result.success) {
                addDownloadedMedia(
                    context,
                    DownloadedMediaItem(
                        id = mediaId,
                        title = title,
                        subtitle = "صوت أذان",
                        localFilePath = existing.absolutePath,
                        fileSizeBytes = existing.length(),
                        sourceUrl = fileUrl
                    )
                )
                _activeMediaDownloads.value = _activeMediaDownloads.value - mediaId
                refreshStorageMetrics(context)
                existing.absolutePath
            } else {
                // الخطأ يُبقي الملف الجزئي ليُستأنف لاحقاً — لا حذف للتقدم.
                _activeMediaDownloads.value = _activeMediaDownloads.value + (mediaId to ReelDownloadProgress(
                    reelId = mediaId, title = title, isError = true,
                    errorMessage = result.errorMessage, downloadedBytes = result.downloadedBytes,
                    totalBytes = result.totalBytes
                ))
                null
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // الإلغاء يُبقي الملف الجزئي ليُستأنف لاحقاً.
            _activeMediaDownloads.value = _activeMediaDownloads.value - mediaId
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Media download failed: ${e.message}")
            _activeMediaDownloads.value = _activeMediaDownloads.value + (mediaId to ReelDownloadProgress(
                reelId = mediaId, title = title, isError = true, errorMessage = e.localizedMessage
            ))
            null
        }
    }

    /**
     * إلغاء تنزيل وسائط جارٍ (عامل WorkManager أو المسار المباشر) مع الإبقاء
     * على الملف الجزئي ليُستأنف لاحقاً — الإلغاء لا يحذف التقدم أبداً.
     */
    fun cancelMediaDownload(mediaId: String) {
        appContextRef?.let { DownloadWorker.cancelMedia(it, mediaId) }
        _activeMediaDownloads.value = _activeMediaDownloads.value - mediaId
    }

    /**
     * حذف ريل منزّل من الذاكرة المحلية — إجراء حذف صريح من المستخدم، فيحذف
     * معه الملف الجزئي (.tmp) وحالة الاستئناف إن وُجدا.
     */
    fun deleteDownloadedReel(context: Context, reelId: String): Boolean {
        val item = _downloadedReels.value.find { it.id == reelId } ?: return false
        val file = File(item.localFilePath)
        if (file.exists()) file.delete()
        File(file.parentFile, "${file.name}.tmp").delete()
        clearResumeMeta(context.applicationContext, file.name)
        _downloadedReels.value = _downloadedReels.value.filterNot { it.id == reelId }
        persistReelsList(context)
        refreshStorageMetrics(context)
        return true
    }

    /**
     * إضافة مقطع وسائط منزّل
     */
    internal fun addDownloadedMedia(context: Context, item: DownloadedMediaItem) {
        val updated = _downloadedMedia.value.filterNot { it.id == item.id } + item
        _downloadedMedia.value = updated
        persistMediaList(context)
        refreshStorageMetrics(context)
    }

    private fun persistMediaList(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        _downloadedMedia.value.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("subtitle", item.subtitle)
                put("localFilePath", item.localFilePath)
                put("fileSizeBytes", item.fileSizeBytes)
                put("category", item.category)
                put("downloadedAt", item.downloadedAt)
                put("sourceUrl", item.sourceUrl ?: "")
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_DOWNLOADED_MEDIA, arr.toString()).apply()
    }

    /**
     * حذف مقطع وسائط — إجراء حذف صريح من المستخدم، فيحذف معه الملف الجزئي
     * (.tmp) وحالة الاستئناف إن وُجدا.
     */
    fun deleteDownloadedMedia(context: Context, mediaId: String): Boolean {
        val item = _downloadedMedia.value.find { it.id == mediaId } ?: return false
        val file = File(item.localFilePath)
        if (file.exists()) file.delete()
        File(file.parentFile, "${file.name}.tmp").delete()
        clearResumeMeta(context.applicationContext, file.name)
        _downloadedMedia.value = _downloadedMedia.value.filterNot { it.id == mediaId }
        persistMediaList(context)
        refreshStorageMetrics(context)
        return true
    }

    /** إعادة قراءة السجل المحلي كي تظهر الملفات المحذوفة من خارج التطبيق كعناصر مفقودة. */
    fun refreshSavedDownloads(context: Context) {
        loadSavedDownloads(context.applicationContext)
        refreshStorageMetrics(context.applicationContext)
    }

    /**
     * تقدير الملفات الجزئية غير النشطة التي يمكن حذفها بأمان.
     */
    fun estimateSafeTempDownloadBytes(context: Context): Long =
        collectSafeTempDownloadFiles(context).sumOf { it.length() }

    private fun collectSafeTempDownloadFiles(context: Context): List<File> = buildList {
        // لا تلمس ملفات التحديث أو النماذج؛ قد تكون مكتملة أو قابلة للاستئناف.
        if (_activeReelDownloads.value.isEmpty()) {
            getReelsDirectory(context).walkTopDown()
                .filter { it.isFile && it.extension.equals("tmp", ignoreCase = true) }
                .forEach(::add)
        }
        if (_activeMediaDownloads.value.isEmpty()) {
            getMediaDirectory(context).walkTopDown()
                .filter { it.isFile && it.extension.equals("tmp", ignoreCase = true) }
                .forEach(::add)
        }
    }.distinct()

    /** تنظيف الملفات الجزئية غير النشطة في مجلدي الريلز والوسائط فقط. */
    fun clearTempCache(context: Context): Long {
        var freedBytes = 0L
        collectSafeTempDownloadFiles(context.applicationContext).forEach { file ->
            runCatching {
                val length = file.length()
                if (file.delete()) freedBytes += length
            }
        }
        refreshStorageMetrics(context.applicationContext)
        return freedBytes
    }

    /**
     * Supabase يحدد الإصدار المعتمد:
     * - الإجباري: يبدأ تنزيله في الخلفية تلقائياً.
     * - الاختياري: يعرض نافذة القرار فقط ولا يبدأ التنزيل قبل موافقة المستخدم.
     */
    fun checkAndStartAutoUpdate(context: Context, force: Boolean = false) {
        if (_isCheckingUpdate.value) return
        val currentDownload = _appUpdateDownload.value
        if (currentDownload != null && currentDownload.isDownloading) return

        val isEmulator = android.os.Build.FINGERPRINT.startsWith("generic") ||
            android.os.Build.MODEL.contains("google_sdk") ||
            android.os.Build.MODEL.contains("Emulator") ||
            android.os.Build.HARDWARE.contains("goldfish") ||
            android.os.Build.HARDWARE.contains("ranchu")
        if (!force && (BuildConfig.DEBUG || isEmulator)) {
            Log.d(TAG, "⏭️ تخطي الفحص التلقائي في بيئة التطوير")
            return
        }

        _isCheckingUpdate.value = true
        scope.launch(Dispatchers.IO) {
            try {
                // فحص مميِّز (من الجولة 24): كان null يعني «لا تحديث» و«تعذّر
                // الوصول» معاً، فيضغط المستخدم «فحص التحديثات» فيرى «جارٍ
                // الفحص» ثم صمتاً — بلا «أحدث نسخة» ولا «تعذّر الوصول».
                val update = when (val result = VersionManager.checkRemoteVersionDetailed(context, force = force)) {
                    is UpdateManager.UpdateCheckResult.Available -> result.info
                    UpdateManager.UpdateCheckResult.UpToDate -> {
                        Log.d(TAG, "✅ لا يوجد إصدار معتمد أحدث")
                        if (force) {
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "أنت تستخدم أحدث نسخة بالفعل ✓", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        return@launch
                    }
                    UpdateManager.UpdateCheckResult.Failed,
                    UpdateManager.UpdateCheckResult.Skipped -> {
                        Log.w(TAG, "⚠️ تعذّر فحص التحديثات (بلا وصول للخادم)")
                        if (force) {
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "تعذّر الوصول إلى خادم التحديثات، تحقق من اتصال الإنترنت وحاول لاحقاً.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                        return@launch
                    }
                }

                withContext(Dispatchers.Main) {
                    if (update.isForce || !UpdateManager.isUpdateDismissedForSession) {
                        UpdateManager.setPendingUpdate(update)
                    }
                }
                QabasNotificationHelper.showUpdateAvailable(
                    context,
                    update.versionName,
                    update.versionCode,
                    update.releaseNotes
                )

                if (!update.isForce) {
                    Log.d(TAG, "ℹ️ تحديث اختياري: بانتظار اختيار المستخدم")
                    return@launch
                }

                Log.d(TAG, "🚨 تحديث إجباري؛ بدء التنزيل الخلفي: ${update.versionName}")
                val existingApk = UpdateManager.getValidatedDownloadedApk(
                    context, update.versionName, update.versionCode, update.apkSize, update.apkSha256
                )
                if (existingApk != null && existingApk.exists() &&
                    existingApk.length() > 1_000_000L
                ) {
                    _appUpdateDownload.value = AppUpdateDownloadItem(
                        versionName = update.versionName,
                        versionCode = update.versionCode,
                        releaseNotes = update.releaseNotes,
                        apkUrl = update.apkUrl,
                        deltaUrl = null,
                        totalBytes = existingApk.length(),
                        expectedApkSize = update.apkSize,
                        downloadedBytes = existingApk.length(),
                        progress = 1f,
                        phase = "COMPLETED",
                        isDownloading = false,
                        isCompleted = true,
                        localApkPath = existingApk.absolutePath
                    )
                    UpdateDownloadService.notifyInstallReady(
                        context, update.versionName, update.versionCode, update.apkSize
                    )
                } else {
                    _appUpdateDownload.value = AppUpdateDownloadItem(
                        versionName = update.versionName,
                        versionCode = update.versionCode,
                        releaseNotes = update.releaseNotes,
                        apkUrl = update.apkUrl,
                        deltaUrl = null,
                        totalBytes = update.apkSize,
                        expectedApkSize = update.apkSize,
                        downloadedBytes = 0L,
                        progress = 0f,
                        phase = "DOWNLOADING",
                        isDownloading = true,
                        isCompleted = false,
                        isError = false
                    )
                    withContext(Dispatchers.Main) {
                        UpdateDownloadService.start(context, update, forceFull = true)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                Log.e(TAG, "❌ فشل فحص/بدء التحديث المعتمد: ${e.message}", e)
            } finally {
                _isCheckingUpdate.value = false
            }
        }
    }

    /**
     * إلغاء تنزيل التحديث الحالي
     */
    fun cancelUpdateDownload(context: Context) {
        UpdateDownloadService.cancel(context)
        val current = _appUpdateDownload.value
        if (current != null) {
            _appUpdateDownload.value = current.copy(
                isDownloading = false,
                phase = "CANCELLED"
            )
        }
    }

    /**
     * تشغيل تثبيت التحديث المحفوظ محلياً
     */
    fun installDownloadedUpdate(context: Context): Boolean {
        val item = _appUpdateDownload.value ?: return false
        val path = item.localApkPath ?: return false
        val file = File(path)
        if (file.exists() && file.length() > 1_000_000) {
            UpdateManager.launchInstaller(context, file)
            return true
        }
        return false
    }

    /**
     * حذف حزمة التحديث لتوفير الذاكرة
     */
    fun deleteDownloadedUpdate(context: Context): Boolean {
        val item = _appUpdateDownload.value ?: return false
        item.localApkPath?.let {
            val file = File(it)
            if (file.exists()) file.delete()
        }
        val updatesDir = File(context.cacheDir, "updates")
        if (updatesDir.exists()) {
            updatesDir.deleteRecursively()
        }
        _appUpdateDownload.value = null
        refreshStorageMetrics(context)
        return true
    }
}
