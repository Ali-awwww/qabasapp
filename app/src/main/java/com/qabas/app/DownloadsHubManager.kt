package com.qabas.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
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
    val usedFormatted: String get() = formatBytes(usedBytes)
    val llmModelsFormatted: String get() = formatBytes(llmModelsBytes)
    val reelsFormatted: String get() = formatBytes(reelsBytes)
    val otherMediaFormatted: String get() = formatBytes(otherMediaBytes)
    val updatesFormatted: String get() = formatBytes(updatesBytes)
    val totalQabasFormatted: String get() = formatBytes(totalQabasBytes)

    val usedPercentage: Float
        get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val qabasPercentage: Float
        get() = if (totalBytes > 0) (totalQabasBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

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
    val downloadedAt: Long = System.currentTimeMillis()
) {
    val formattedSize: String get() = StorageMetrics.formatBytes(fileSizeBytes)
    val fileExists: Boolean get() = File(localFilePath).exists()
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
 * عنصر وسائط أو مقطع صوتي منزّل
 */
data class DownloadedMediaItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val localFilePath: String,
    val fileSizeBytes: Long = 0L,
    val category: String = "audio",
    val downloadedAt: Long = System.currentTimeMillis()
) {
    val formattedSize: String get() = StorageMetrics.formatBytes(fileSizeBytes)
}

/**
 * المدير المركزي للتنزيلات، فيديوهات الريلز، وإدارة سعة الذاكرة
 */
object DownloadsHubManager {
    private const val TAG = "DownloadsHubManager"
    private const val PREFS_NAME = "qabas_downloads_hub_prefs"
    private const val KEY_DOWNLOADED_REELS = "downloaded_reels_json"
    private const val KEY_DOWNLOADED_MEDIA = "downloaded_media_json"
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

    private val activeDownloadJobs = ConcurrentHashMap<String, Job>()
    private var isListeningToUpdateManager = false

    /**
     * تهيئة المدير وتحميل قائمة الملفات وتحديث حسابات الذاكرة ومراقبة التحديثات
     */
    fun init(context: Context) {
        loadSavedDownloads(context)
        refreshStorageMetrics(context)
        setupUpdateObserver(context)
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
                    if (item.fileExists) {
                        list.add(item)
                    }
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
                        downloadedAt = obj.optLong("downloadedAt", System.currentTimeMillis())
                    )
                    if (File(item.localFilePath).exists()) {
                        list.add(item)
                    }
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
     * تنزيل فيديو ريل حقيقي مع تتبع البايتات وحفظه في الذاكرة
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

        // إلغاء أي مهمة سابقة لنفس المعرّف
        activeDownloadJobs[reelId]?.cancel()

        val job = scope.launch {
            val safeFileName = "reel_${reelId.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.mp4"
            val destFile = File(getReelsDirectory(context), safeFileName)
            val tempFile = File(getReelsDirectory(context), "${safeFileName}.tmp")

            _activeReelDownloads.value = _activeReelDownloads.value + (reelId to ReelDownloadProgress(
                reelId = reelId,
                title = title,
                progress = 0f
            ))

            try {
                // إذا كان الرابط محلياً أو assets، نسخه مباشرة
                if (videoUrl.startsWith("file://") || videoUrl.startsWith("/")) {
                    val src = File(videoUrl.removePrefix("file://"))
                    if (src.exists()) {
                        src.copyTo(destFile, overwrite = true)
                        val item = DownloadedReelItem(
                            id = reelId,
                            title = title,
                            author = author,
                            videoUrl = videoUrl,
                            localFilePath = destFile.absolutePath,
                            thumbnailUrl = thumbnailUrl,
                            fileSizeBytes = destFile.length()
                        )
                        addDownloadedReel(context, item)
                        _activeReelDownloads.value = _activeReelDownloads.value - reelId
                        refreshStorageMetrics(context)
                        withContext(Dispatchers.Main) { onComplete?.invoke(true) }
                        return@launch
                    }
                }

                val request = Request.Builder().url(videoUrl).build()
                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    val err = "فشل التنزيل: كود ${response.code}"
                    updateDownloadError(reelId, title, err)
                    withContext(Dispatchers.Main) { onComplete?.invoke(false) }
                    return@launch
                }

                val body = response.body ?: run {
                    updateDownloadError(reelId, title, "المحتوى فارغ")
                    withContext(Dispatchers.Main) { onComplete?.invoke(false) }
                    return@launch
                }

                val totalBytes = body.contentLength().takeIf { it > 0 } ?: 15_000_000L
                var downloadedBytes = 0L
                val startTime = System.currentTimeMillis()
                var lastUpdate = startTime

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read

                            val now = System.currentTimeMillis()
                            if (now - lastUpdate >= 200) {
                                val progress = (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                                val elapsedSec = (now - startTime) / 1000.0
                                val speed = if (elapsedSec > 0) ((downloadedBytes / 1024) / elapsedSec).toLong() else 0L

                                _activeReelDownloads.value = _activeReelDownloads.value + (reelId to ReelDownloadProgress(
                                    reelId = reelId,
                                    title = title,
                                    progress = progress,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                    speedKbps = speed
                                ))
                                lastUpdate = now
                            }
                        }
                    }
                }

                if (tempFile.exists() && tempFile.length() > 0) {
                    if (destFile.exists()) destFile.delete()
                    tempFile.renameTo(destFile)

                    val item = DownloadedReelItem(
                        id = reelId,
                        title = title,
                        author = author,
                        videoUrl = videoUrl,
                        localFilePath = destFile.absolutePath,
                        thumbnailUrl = thumbnailUrl,
                        fileSizeBytes = destFile.length()
                    )
                    addDownloadedReel(context, item)
                    _activeReelDownloads.value = _activeReelDownloads.value - reelId
                    refreshStorageMetrics(context)
                    withContext(Dispatchers.Main) { onComplete?.invoke(true) }
                } else {
                    updateDownloadError(reelId, title, "فشل حفظ الملف")
                    withContext(Dispatchers.Main) { onComplete?.invoke(false) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Reel download failed: ${e.message}", e)
                if (tempFile.exists()) tempFile.delete()
                updateDownloadError(reelId, title, e.localizedMessage ?: "حدث خطأ أثناء التنزيل")
                withContext(Dispatchers.Main) { onComplete?.invoke(false) }
            } finally {
                activeDownloadJobs.remove(reelId)
            }
        }

        activeDownloadJobs[reelId] = job
    }

    private fun updateDownloadError(reelId: String, title: String, message: String) {
        _activeReelDownloads.value = _activeReelDownloads.value + (reelId to ReelDownloadProgress(
            reelId = reelId,
            title = title,
            isError = true,
            errorMessage = message
        ))
    }

    private fun addDownloadedReel(context: Context, item: DownloadedReelItem) {
        val updated = _downloadedReels.value.filterNot { it.id == item.id } + item
        _downloadedReels.value = updated
        persistReelsList(context)
    }

    /**
     * إلغاء تنزيل ريل جاري
     */
    fun cancelReelDownload(reelId: String) {
        activeDownloadJobs[reelId]?.cancel()
        activeDownloadJobs.remove(reelId)
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
    ): String? = withContext(Dispatchers.IO) {
        try {
            // كاش موجود؟ أعده فوراً
            val existing = File(getMediaDirectory(context), "media_${mediaId.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.$extension")
            if (existing.exists() && existing.length() > 0) return@withContext existing.absolutePath
            // ملف محلي أصلاً (file://) — لا تنزيل needed
            if (fileUrl.startsWith("file://") || fileUrl.startsWith("/")) return@withContext fileUrl

            _activeMediaDownloads.value = _activeMediaDownloads.value + (mediaId to ReelDownloadProgress(
                reelId = mediaId, title = title, progress = 0f
            ))
            val tmp = File(getMediaDirectory(context), "${existing.name}.tmp")
            val request = Request.Builder().url(fileUrl).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                _activeMediaDownloads.value = _activeMediaDownloads.value + (mediaId to ReelDownloadProgress(
                    reelId = mediaId, title = title, isError = true, errorMessage = "كود ${response.code}"
                ))
                return@withContext null
            }
            val body = response.body ?: return@withContext null
            val total = body.contentLength().takeIf { it > 0 } ?: 5_000_000L
            var done = 0L
            var last = System.currentTimeMillis()
            val start = last
            body.byteStream().use { input ->
                FileOutputStream(tmp).use { output ->
                    val buf = ByteArray(32 * 1024)
                    var r: Int
                    while (input.read(buf).also { r = it } != -1) {
                        output.write(buf, 0, r)
                        done += r
                        onProgress?.invoke(done, total)
                        val now = System.currentTimeMillis()
                        if (now - last >= 250) {
                            val speed = if (now > start) ((done / 1024) / ((now - start) / 1000.0)).toLong() else 0L
                            _activeMediaDownloads.value = _activeMediaDownloads.value + (mediaId to ReelDownloadProgress(
                                reelId = mediaId, title = title,
                                progress = (done.toFloat() / total).coerceIn(0f, 1f),
                                downloadedBytes = done, totalBytes = total, speedKbps = speed
                            ))
                            last = now
                        }
                    }
                }
            }
            if (tmp.exists() && tmp.length() > 0) {
                if (existing.exists()) existing.delete()
                tmp.renameTo(existing)
                _activeMediaDownloads.value = _activeMediaDownloads.value - mediaId
                refreshStorageMetrics(context)
                existing.absolutePath
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Media download failed: ${e.message}")
            _activeMediaDownloads.value = _activeMediaDownloads.value + (mediaId to ReelDownloadProgress(
                reelId = mediaId, title = title, isError = true, errorMessage = e.localizedMessage
            ))
            null
        }
    }

    fun cancelMediaDownload(mediaId: String) {
        _activeMediaDownloads.value = _activeMediaDownloads.value - mediaId
    }

    /**
     * حذف ريل منزّل من الذاكرة المحلية
     */
    fun deleteDownloadedReel(context: Context, reelId: String): Boolean {
        val item = _downloadedReels.value.find { it.id == reelId } ?: return false
        val file = File(item.localFilePath)
        if (file.exists()) file.delete()
        _downloadedReels.value = _downloadedReels.value.filterNot { it.id == reelId }
        persistReelsList(context)
        refreshStorageMetrics(context)
        return true
    }

    /**
     * إضافة مقطع وسائط منزّل
     */
    fun addDownloadedMedia(context: Context, item: DownloadedMediaItem) {
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
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_DOWNLOADED_MEDIA, arr.toString()).apply()
    }

    /**
     * حذف مقطع وسائط
     */
    fun deleteDownloadedMedia(context: Context, mediaId: String): Boolean {
        val item = _downloadedMedia.value.find { it.id == mediaId } ?: return false
        val file = File(item.localFilePath)
        if (file.exists()) file.delete()
        _downloadedMedia.value = _downloadedMedia.value.filterNot { it.id == mediaId }
        persistMediaList(context)
        refreshStorageMetrics(context)
        return true
    }

    /**
     * تنظيف الكاش والملفات المؤقتة لتحرير المساحة
     */
    fun clearTempCache(context: Context): Long {
        var freedBytes = 0L
        runCatching {
            context.cacheDir?.walkTopDown()?.forEach { file ->
                if (file.isFile) {
                    val len = file.length()
                    if (file.delete()) freedBytes += len
                }
            }
            // حذف ملفات .tmp داخل مجلدات النماذج والريلز
            getReelsDirectory(context).walkTopDown().filter { it.extension == "tmp" }.forEach {
                val len = it.length()
                if (it.delete()) freedBytes += len
            }
            OnDeviceModelManager.getModelsDirectory(context).walkTopDown().filter { it.extension == "tmp" }.forEach {
                val len = it.length()
                if (it.delete()) freedBytes += len
            }
        }
        refreshStorageMetrics(context)
        return freedBytes
    }

    /**
     * التحقق من وجود إصدار جديد عبر GitHub Releases والبدء الفوري في تنزيله
     * وبث التقدم مباشرة في مركز التنزيلات
     */
    fun checkAndStartAutoUpdate(context: Context, force: Boolean = false) {
        if (_isCheckingUpdate.value) return
        val currentDownload = _appUpdateDownload.value
        if (currentDownload != null && currentDownload.isDownloading) return

        _isCheckingUpdate.value = true
        scope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔍 Checking for updates via GitHub Releases for auto-download...")
                val update = VersionManager.checkRemoteVersion(context, force = force)
                if (update != null) {
                    Log.d(TAG, "🚀 New update detected: ${update.versionName} - starting auto download!")
                    
                    // 🔔 إظهار نافذة التحديث HUD (للتأكد من ظهور الرسالة عند أول تشغيل)
                    withContext(Dispatchers.Main) {
                        UpdateManager.setPendingUpdate(update)
                    }
                    
                    // تحقق مما إذا كانت الحزمة منزلة وموجودة بالفعل
                    val existingApk = UpdateManager.getValidatedDownloadedApk(
                        context, update.versionName, update.versionCode, update.apkSize
                    )
                    if (existingApk != null && existingApk.exists() && existingApk.length() > 1_000_000) {
                        Log.d(TAG, "📦 Update package already exists: ${existingApk.absolutePath}")
                        _appUpdateDownload.value = AppUpdateDownloadItem(
                            versionName = update.versionName,
                            versionCode = update.versionCode,
                            releaseNotes = update.releaseNotes,
                            apkUrl = update.apkUrl,
                            deltaUrl = update.deltaUrl,
                            totalBytes = existingApk.length(),
                            expectedApkSize = update.apkSize,
                            downloadedBytes = existingApk.length(),
                            progress = 1f,
                            phase = "COMPLETED",
                            isDownloading = false,
                            isCompleted = true,
                            localApkPath = existingApk.absolutePath
                        )
                        // فتح المثبّت من الخلفية محجوب في أندرويد 10+ — إشعار
                        // «ثبّت الآن» يفتح النشاط في المقدمة ثم واجهة التثبيت.
                        UpdateDownloadService.notifyInstallReady(
                            context, update.versionName, update.versionCode, update.apkSize
                        )
                    } else {
                        // بدء التنزيل التلقائي فوراً
                        val targetSize = if (update.deltaUsable) update.deltaSize else update.apkSize
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
                        withContext(Dispatchers.Main) {
                            UpdateDownloadService.start(context, update)
                        }
                    }
                } else {
                    Log.d(TAG, "✅ App is already up to date.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in checkAndStartAutoUpdate: ${e.message}", e)
            } finally {
                _isCheckingUpdate.value = false
            }
        }
    }

    /**
     * بدء تنزيل التحديث يدوياً
     */
    fun startUpdateDownload(context: Context, update: UpdateManager.UpdateInfo, forceFull: Boolean = false) {
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
        UpdateDownloadService.start(context, update, forceFull)
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
     * فتح مثبّت النظام مباشرة من إشعار/اختصار — يجد الحزمة المنزّلة تلقائياً.
     * (يُستخدم من UpdateInstallReceiver)
     */
    fun installVersion(context: Context, versionName: String, versionCode: Int, apkSize: Long = 0L): Boolean {
        // الحزمة تُحدد بالاسم ورقم البناء معاً — لا نثبّت حزمة غير محددة بدقة
        val apk = UpdateManager.getValidatedDownloadedApk(context, versionName, versionCode, apkSize) ?: return false
        if (!apk.exists() || apk.length() <= 1_000_000L) return false
        UpdateManager.launchInstaller(context, apk)
        return true
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
