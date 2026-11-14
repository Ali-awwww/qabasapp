package com.qabas.app

import android.content.Context
import android.net.Uri
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

    data class Selection(
        val type: String = TYPE_PRESET,
        val value: String = DEFAULT_PRESET,
        val dim: Float = 0.35f
    )

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

    fun saveMedia(context: Context, type: String, sourceUri: Uri): Boolean {
        if (type != TYPE_IMAGE && type != TYPE_VIDEO) return false
        return runCatching {
            val extension = if (type == TYPE_VIDEO) "mp4" else "jpg"
            val directory = File(context.filesDir, "adhan_background").apply { mkdirs() }
            val destination = File(directory, "background.$extension")
            val temporary = File(directory, "background.$extension.tmp")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            } ?: return false
            if (!temporary.isFile || temporary.length() == 0L) return false
            if (destination.exists()) destination.delete()
            if (!temporary.renameTo(destination)) return false
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_TYPE, type)
                .putString(KEY_VALUE, destination.absolutePath)
                .apply()
            true
        }.getOrDefault(false)
    }

    fun setDim(context: Context, dim: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_DIM, dim.coerceIn(0f, 0.8f))
            .apply()
    }
}
