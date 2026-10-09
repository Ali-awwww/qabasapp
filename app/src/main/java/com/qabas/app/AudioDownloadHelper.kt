package com.qabas.app

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

data class DownloadProgress(
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val percent: Float = 0f, // 0.0 to 1.0
    val speedBytesPerSec: Long = 0L,
    val isCompleted: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String? = null
)

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
     * Downloads an audio track with live, byte-level progress reporting.
     */
    suspend fun downloadAudioWithProgress(
        context: Context,
        audioUrl: String,
        title: String,
        onProgress: (DownloadProgress) -> Unit
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val cleanTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "qabas_audio" }
                val fileName = "${cleanTitle}.mp3"

                when {
                    audioUrl.startsWith("assets/") -> {
                        val assetPath = audioUrl.removePrefix("assets/")
                        val fd = runCatching { context.assets.openFd(assetPath) }.getOrNull()
                        val total = fd?.length ?: 5_000_000L
                        fd?.close()

                        context.assets.open(assetPath).use { input ->
                            saveStreamWithProgress(context, input, fileName, total, onProgress)
                        }
                    }
                    audioUrl.startsWith("http://") || audioUrl.startsWith("https://") -> {
                        val url = URL(audioUrl)
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 15000
                        conn.readTimeout = 30000
                        conn.setRequestProperty("User-Agent", "Qabas-App/1.0")
                        conn.connect()

                        val total = conn.contentLengthLong.takeIf { it > 0 } ?: 8_000_000L
                        conn.inputStream.use { input ->
                            saveStreamWithProgress(context, input, fileName, total, onProgress)
                        }
                    }
                    audioUrl.startsWith("/") -> {
                        val file = File(audioUrl)
                        if (file.exists()) {
                            val total = file.length()
                            file.inputStream().use { input ->
                                saveStreamWithProgress(context, input, fileName, total, onProgress)
                            }
                        } else {
                            onProgress(DownloadProgress(isError = true, errorMessage = "الملف غير موجود"))
                            false
                        }
                    }
                    else -> {
                        // Fallback sample
                        val input = try {
                            context.assets.open("audio/recitation_yusuf.mp3")
                        } catch (_: Exception) {
                            null
                        }
                        if (input != null) {
                            input.use { saveStreamWithProgress(context, it, fileName, 6_000_000L, onProgress) }
                        } else {
                            onProgress(DownloadProgress(isError = true, errorMessage = "مصدر الصوت غير صالح"))
                            false
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AudioDownloadHelper", "Download failed: ${e.message}", e)
                onProgress(DownloadProgress(isError = true, errorMessage = e.localizedMessage ?: "فشل التنزيل"))
                false
            }
        }
    }

    /**
     * Backward-compatible simple download method.
     */
    suspend fun downloadAudio(context: Context, audioUrl: String, title: String): Boolean {
        return downloadAudioWithProgress(context, audioUrl, title) {}
    }

    private fun saveStreamWithProgress(
        context: Context,
        inputStream: InputStream,
        fileName: String,
        totalBytes: Long,
        onProgress: (DownloadProgress) -> Unit
    ): Boolean {
        var outputStream: OutputStream? = null
        var uri: Uri? = null
        val resolver = context.contentResolver

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri == null) return false
                outputStream = resolver.openOutputStream(uri)
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val targetFile = File(downloadsDir, fileName)
                outputStream = FileOutputStream(targetFile)
            }

            if (outputStream == null) return false

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
                    val percent = if (totalBytes > 0) (totalRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0.5f
                    onProgress(
                        DownloadProgress(
                            downloadedBytes = totalRead,
                            totalBytes = totalBytes,
                            percent = percent,
                            speedBytesPerSec = speed,
                            isCompleted = false
                        )
                    )
                    lastTime = now
                    bytesSinceLastTime = 0L
                }
            }

            outputStream.flush()
            outputStream.close()
            outputStream = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(uri, contentValues, null, null)
            }

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
        } catch (e: Exception) {
            outputStream?.close()
            onProgress(DownloadProgress(isError = true, errorMessage = e.message))
            false
        }
    }
}
