package com.qabas.app

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * محرك أجواء السكينة الطبيعية ثلاثية الأبعاد لقسم الريلز (Spiritual Soundscape & Nature Ambience)
 * بديل راقٍ ومطمئن للضوضاء والموسيقى، يمنح تجربة استماع خشوعية فريدة ممزوجة بأصوات الطبيعة.
 */
object ReelAmbienceSoundscapeEngine {

    private const val TAG = "SoundscapeEngine"

    data class AmbienceTrack(
        val id: String,
        val name: String,
        val icon: String,
        val url: String,
        val description: String
    )


    private var mediaPlayer: MediaPlayer? = null

    private val _volume = MutableStateFlow(0.35f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    fun play(context: Context, track: AmbienceTrack) {
        stop()
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(track.url)
                isLooping = true
                val vol = _volume.value
                setVolume(vol, vol)
                setOnPreparedListener {
                    it.start()
                    _isPlaying.value = true
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "MediaPlayer error $what, $extra")
                    _isPlaying.value = false
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start soundscape: ${e.message}")
            _isPlaying.value = false
        }
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _volume.value = clamped
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.setVolume(clamped, clamped)
            }
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (ignored: Exception) {}
        mediaPlayer = null
        _isPlaying.value = false
    }
}
