package com.qabas.app

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

object AdhanBackgroundPreferences {
    private const val PREFS = "qabas_adhan_background"
    private const val KEY_TYPE = "type"
    private const val KEY_VALUE = "value"
    private const val KEY_DIM = "dim"

    const val TYPE_PRESET = "preset"
    const val TYPE_IMAGE = "image"
    const val TYPE_VIDEO = "video"
    const val DEFAULT_PRESET = "night_blue"
    const val MAX_VIDEO_BYTES = 50L * 1024L * 1024L
    const val MAX_VIDEO_DURATION_MS = 30_000L
    const val MAX_VIDEO_DIMENSION = 1920

    data class Selection(
        val type: String = TYPE_PRESET,
        val value: String = DEFAULT_PRESET,
        val dim: Float = 0.35f
    )

    data class MediaInfo(val sizeBytes: Long, val durationMs: Long = 0L)

    fun get(context: Context): Selection {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Selection(
            type = prefs.getString(KEY_TYPE, TYPE_PRESET) ?: TYPE_PRESET,
            value = prefs.getString(KEY_VALUE, DEFAULT_PRESET) ?: DEFAULT_PRESET,
            dim = prefs.getFloat(KEY_DIM, 0.35f).coerceIn(0f, 0.8f)
        )
    }

    fun savePreset(context: Context, preset: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TYPE, TYPE_PRESET)
            .putString(KEY_VALUE, preset)
            .apply()
    }

    /**
     * ينسخ الصورة أو يضغط الفيديو إلى نسخة صامتة قصيرة مناسبة للخلفية.
     * لا يغيّر الخلفية الحالية إلا بعد نجاح كل الفحوصات والترميز.
     */
    fun saveMedia(context: Context, type: String, sourceUri: Uri): Boolean {
        if (type != TYPE_IMAGE && type != TYPE_VIDEO) return false
        return runCatching {
            val directory = File(context.filesDir, "adhan_background").apply { mkdirs() }
            val extension = if (type == TYPE_VIDEO) "mp4" else "jpg"
            val sourceTemp = File(directory, "background.source.tmp")
            val outputTemp = File(directory, "background.$extension.tmp")
            val destination = File(directory, "background.$extension")

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                sourceTemp.outputStream().use { output -> input.copyTo(output) }
            } ?: return false
            if (!sourceTemp.isFile || sourceTemp.length() == 0L) return false

            if (type == TYPE_VIDEO) {
                if (!prepareVideo(sourceTemp, outputTemp)) return false
            } else {
                if (sourceTemp.length() > MAX_VIDEO_BYTES) return false
                if (outputTemp.exists()) outputTemp.delete()
                if (!sourceTemp.renameTo(outputTemp)) return false
            }

            if (!outputTemp.isFile || outputTemp.length() == 0L || outputTemp.length() > MAX_VIDEO_BYTES) return false
            if (destination.exists()) destination.delete()
            if (!outputTemp.renameTo(destination)) return false
            sourceTemp.delete()

            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_TYPE, type)
                .putString(KEY_VALUE, destination.absolutePath)
                .apply()
            true
        }.also {
            File(context.filesDir, "adhan_background/background.source.tmp").delete()
            File(context.filesDir, "adhan_background/background.mp4.tmp").delete()
            File(context.filesDir, "adhan_background/background.jpg.tmp").delete()
        }.getOrDefault(false)
    }

    private fun prepareVideo(source: File, output: File): Boolean {
        val metadata = MediaMetadataRetriever()
        return try {
            metadata.setDataSource(source.absolutePath)
            val duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return false
            val width = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return false
            val height = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return false
            if (source.length() > 200L * 1024L * 1024L || duration > MAX_VIDEO_DURATION_MS || maxOf(width, height) > MAX_VIDEO_DIMENSION) return false
            val input = source.absolutePath.replace("'", "'\\''")
            val target = output.absolutePath.replace("'", "'\\''")
            val command = "-y -i '$input' -t 30 -an -vf \"scale='min(720,iw)':-2\" -c:v libx264 -preset veryfast -crf 28 -movflags +faststart '$target'"
            val session = FFmpegKit.execute(command)
            ReturnCode.isSuccess(session.returnCode) && output.isFile && output.length() > 0L
        } catch (_: Exception) {
            false
        } finally {
            metadata.release()
        }
    }

    fun currentMediaInfo(context: Context): MediaInfo? {
        val selection = get(context)
        if (selection.type != TYPE_IMAGE && selection.type != TYPE_VIDEO) return null
        val file = File(selection.value)
        if (!file.isFile || file.length() == 0L) return null
        if (selection.type == TYPE_IMAGE) return MediaInfo(file.length())
        val metadata = MediaMetadataRetriever()
        return try {
            metadata.setDataSource(file.absolutePath)
            MediaInfo(
                sizeBytes = file.length(),
                durationMs = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            )
        } catch (_: Exception) {
            MediaInfo(file.length())
        } finally {
            metadata.release()
        }
    }

    fun deleteCustomMedia(context: Context) {
        val selection = get(context)
        if (selection.type == TYPE_IMAGE || selection.type == TYPE_VIDEO) {
            File(selection.value).delete()
        }
        savePreset(context, DEFAULT_PRESET)
    }

}
