package com.qabas.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

data class DownloadProgress(
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val percent: Float = 0f, // 0.0 to 1.0
    val speedBytesPerSec: Long = 0L,
    val isCompleted: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String? = null,
    /** true عندما لم يرسل السيرفر حجماً معروفاً — التقدّم غير محدد («الحجم غير معروف»). */
    val isTotalUnknown: Boolean = false
)

/**
 * مساعد تنزيل الصوت — يفوّض إلى المخزن الداخلي الموحّد في [DownloadsHubManager]
 * (مجلد ملفات التطبيق `downloads/media`): نفس الدليل ونفس مفتاح إزالة التكرار
 * (الرابط نفسه يعطي الملف نفسه) ونفس مصدر شارة «منزّل».
 *
 * لا يوجد هنا أي مسار كتابة ثانٍ (حُذف مسار MediaStore العام): لا يمكن تنزيل
 * الصوت نفسه مرتين في مكانين مختلفين. التقدّم من `Content-Length` الحقيقي،
 * وإن غاب الحجم فالتقدّم غير محدد — لا رقم مخمّن.
 */
object AudioDownloadHelper {

    /**
     * Helper to format bytes into readable KB/MB string.
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(java.util.Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    /**
     * Downloads an audio track with live, byte-level progress reporting,
     * into the single internal audio store owned by [DownloadsHubManager].
     */
    suspend fun downloadAudioWithProgress(
        context: Context,
        audioUrl: String,
        title: String,
        onProgress: (DownloadProgress) -> Unit
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // إزالة تكرار أولاً: نفس الرابط منزّل فعلاً في المخزن الموحّد؟
                val alreadyDownloaded = DownloadsHubManager.audioFileForUrl(context, audioUrl)
                if (alreadyDownloaded != null) {
                    val size = alreadyDownloaded.length()
                    onProgress(
                        DownloadProgress(
                            downloadedBytes = size,
                            totalBytes = size,
                            percent = 1f,
                            speedBytesPerSec = 0L,
                            isCompleted = true
                        )
                    )
                    return@withContext true
                }

                val mediaId = DownloadsHubManager.audioMediaIdForUrl(audioUrl)
                when {
                    audioUrl.startsWith("assets/") -> {
                        val assetPath = audioUrl.removePrefix("assets/")
                        val fd = runCatching { context.assets.openFd(assetPath) }.getOrNull()
                        val total = fd?.length ?: 0L
                        fd?.close()
                        val destFile = DownloadsHubManager.mediaFileForId(context, mediaId, "mp3")
                        context.assets.open(assetPath).use { input ->
                            copyStreamToHubFile(context, input, destFile, mediaId, title, audioUrl, total, onProgress)
                        }
                    }
                    audioUrl.startsWith("http://") || audioUrl.startsWith("https://") -> {
                        val startTime = System.currentTimeMillis()
                        var lastSpeedBytes = 0L
                        var lastSpeedAt = startTime
                        val localPath = DownloadsHubManager.downloadMedia(
                            context = context,
                            mediaId = mediaId,
                            title = title,
                            fileUrl = audioUrl,
                            extension = "mp3"
                        ) { sent, total ->
                            val now = System.currentTimeMillis()
                            val speed = if (now > lastSpeedAt) {
                                ((sent - lastSpeedBytes) * 1000L / (now - lastSpeedAt)).coerceAtLeast(0L)
                            } else 0L
                            lastSpeedBytes = sent
                            lastSpeedAt = now
                            onProgress(
                                DownloadProgress(
                                    downloadedBytes = sent,
                                    totalBytes = total,
                                    percent = if (total > 0) (sent.toFloat() / total).coerceIn(0f, 1f) else 0f,
                                    speedBytesPerSec = speed,
                                    isCompleted = false,
                                    isTotalUnknown = total <= 0
                                )
                            )
                        }
                        if (!localPath.isNullOrBlank()) {
                            val size = File(localPath).length()
                            onProgress(
                                DownloadProgress(
                                    downloadedBytes = size,
                                    totalBytes = size,
                                    percent = 1f,
                                    speedBytesPerSec = 0L,
                                    isCompleted = true
                                )
                            )
                            true
                        } else {
                            onProgress(DownloadProgress(isError = true, errorMessage = "تعذر التنزيل — تحقق من الاتصال"))
                            false
                        }
                    }
                    audioUrl.startsWith("/") -> {
                        val file = File(audioUrl)
                        if (file.exists()) {
                            val destFile = DownloadsHubManager.mediaFileForId(context, mediaId, "mp3")
                            if (file.canonicalPath == destFile.canonicalPath) {
                                onProgress(
                                    DownloadProgress(
                                        downloadedBytes = file.length(),
                                        totalBytes = file.length(),
                                        percent = 1f,
                                        speedBytesPerSec = 0L,
                                        isCompleted = true
                                    )
                                )
                                true
                            } else {
                                file.inputStream().use { input ->
                                    copyStreamToHubFile(context, input, destFile, mediaId, title, audioUrl, file.length(), onProgress)
                                }
                            }
                        } else {
                            onProgress(DownloadProgress(isError = true, errorMessage = "الملف غير موجود"))
                            false
                        }
                    }
                    else -> {
                        onProgress(DownloadProgress(isError = true, errorMessage = "رابط الملف الصوتي غير صالح أو غير متاح للتنزيل"))
                        false
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("AudioDownloadHelper", "Download failed: ${e.message}", e)
                onProgress(DownloadProgress(isError = true, errorMessage = e.localizedMessage ?: "فشل التنزيل"))
                false
            }
        }
    }

    /**
     * عناوين الصوتيات المنزّلة فعلاً — تُقرأ من المخزن الداخلي الموحّد وحده
     * (سجل [DownloadsHubManager] + وجود الملف)، لا من أي مخزن ثانٍ.
     */
    fun getDownloadedAudioTitles(context: Context, titles: Collection<String>): Set<String> {
        if (titles.isEmpty()) return emptySet()
        DownloadsHubManager.init(context.applicationContext)
        return DownloadsHubManager.downloadedMedia.value
            .filter { it.title in titles && it.fileExists }
            .map { it.title }
            .toSet()
    }

    /**
     * Backward-compatible simple download method.
     */
    suspend fun downloadAudio(context: Context, audioUrl: String, title: String): Boolean {
        return downloadAudioWithProgress(context, audioUrl, title) {}
    }

    /**
     * نسخ مجرى (أصل أو ملف محلي) إلى ملف المخزن الداخلي الموحّد مع تقدّم
     * حقيقي؛ الحجم المجهول (total = 0) يُبلَّغ غير محدد لا برقم مخمّن.
     * يُسجَّل الملف في سجل الوسائط الموحّد لتظهر شارة «منزّل» من المصدر نفسه.
     */
    private fun copyStreamToHubFile(
        context: Context,
        inputStream: InputStream,
        destFile: File,
        mediaId: String,
        title: String,
        sourceUrl: String,
        totalBytes: Long,
        onProgress: (DownloadProgress) -> Unit
    ): Boolean {
        val tmpFile = File(destFile.parentFile, "${destFile.name}.tmp")
        var outputStream: OutputStream? = null
        return try {
            outputStream = FileOutputStream(tmpFile)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalRead = 0L
            var lastTime = System.currentTimeMillis()
            var bytesSinceLastTime = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                bytesSinceLastTime += bytesRead

                val now = System.currentTimeMillis()
                val diffTime = now - lastTime
                if (diffTime >= 100) {
                    val speed = if (diffTime > 0) (bytesSinceLastTime * 1000L) / diffTime else 0L
                    onProgress(
                        DownloadProgress(
                            downloadedBytes = totalRead,
                            totalBytes = totalBytes,
                            percent = if (totalBytes > 0) (totalRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f,
                            speedBytesPerSec = speed,
                            isCompleted = false,
                            isTotalUnknown = totalBytes <= 0
                        )
                    )
                    lastTime = now
                    bytesSinceLastTime = 0L
                }
            }

            outputStream.flush()
            outputStream.close()
            outputStream = null

            if (destFile.exists()) destFile.delete()
            tmpFile.renameTo(destFile)
            DownloadsHubManager.addDownloadedMedia(
                context,
                DownloadedMediaItem(
                    id = mediaId,
                    title = title,
                    subtitle = "صوت",
                    localFilePath = destFile.absolutePath,
                    fileSizeBytes = destFile.length(),
                    sourceUrl = sourceUrl
                )
            )
            DownloadsHubManager.refreshStorageMetrics(context)

            onProgress(
                DownloadProgress(
                    downloadedBytes = totalRead,
                    totalBytes = totalRead,
                    percent = 1f,
                    speedBytesPerSec = 0L,
                    isCompleted = true
                )
            )
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            runCatching { outputStream?.close() }
            throw e
        } catch (e: Exception) {
            runCatching { outputStream?.close() }
            runCatching { tmpFile.delete() }
            onProgress(DownloadProgress(isError = true, errorMessage = e.message))
            false
        }
    }
}
