package com.qabas.app.ui.input

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.util.Locale

/**
 * مساعدات مشتركة لاستخراج بيانات الملفات (اسم، حجم، مدة).
 * تُستخدم في InputScreen لتقليل التكرار.
 */
object MediaPickerHelpers {

    data class MediaMeta(
        val fileName: String,
        val durationFormatted: String? = null,
        val sizeFormatted: String? = null
    ) {
        fun label(): String {
            val parts = listOfNotNull(durationFormatted, sizeFormatted)
            return if (parts.isNotEmpty()) "$fileName (${parts.joinToString(" • ")})" else fileName
        }
    }

    fun extractAudioMeta(context: Context, uri: Uri, defaultName: String = "مقطع_صوتي.mp3"): MediaMeta {
        var fileName = defaultName
        var sizeFormatted: String? = null
        var durationFormatted: String? = null

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    val name = cursor.getString(nameIndex)
                    if (!name.isNullOrBlank()) fileName = name
                }
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    val size = cursor.getLong(sizeIndex)
                    if (size > 0) {
                        val sizeMb = size / (1024.0 * 1024.0)
                        sizeFormatted = if (sizeMb >= 1.0) {
                            String.format(Locale.US, "%.1f ميغابايت", sizeMb)
                        } else {
                            val sizeKb = size / 1024.0
                            String.format(Locale.US, "%.0f كيلوبايت", sizeKb)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            retriever.release()
            if (durationMs != null && durationMs > 0) {
                val totalSeconds = durationMs / 1000
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                durationFormatted = String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        } catch (_: Exception) {}

        return MediaMeta(fileName, durationFormatted, sizeFormatted)
    }

    fun extractVideoMeta(context: Context, uri: Uri, defaultName: String = "فيديو_مستورد.mp4"): MediaMeta {
        var fileName = defaultName
        var durationFormatted: String? = null

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    val name = cursor.getString(nameIndex)
                    if (!name.isNullOrBlank()) fileName = name
                }
            }
        } catch (_: Exception) {}

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            retriever.release()
            if (durationMs != null && durationMs > 0) {
                val totalSeconds = durationMs / 1000
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                durationFormatted = String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        } catch (_: Exception) {}

        return MediaMeta(fileName, durationFormatted, null)
    }
}
