package com.qabas.app

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed class AzanPlaybackState {
    object Idle : AzanPlaybackState()
    data class Loading(val voiceId: String) : AzanPlaybackState()
    data class Playing(val voiceId: String, val progress: Float, val currentSeconds: Int, val totalSeconds: Int) : AzanPlaybackState()
    data class Paused(val voiceId: String) : AzanPlaybackState()
    data class Error(val message: String) : AzanPlaybackState()
}

object AzanAudioPlayer {
    private const val TAG = "AzanAudioPlayer"
    private var mediaPlayer: MediaPlayer? = null
    private var activeVoiceId: String? = null

    private val _playbackState = MutableStateFlow<AzanPlaybackState>(AzanPlaybackState.Idle)
    val playbackState: StateFlow<AzanPlaybackState> = _playbackState

    fun playVoice(context: Context, voice: AzanVoice) {
        if (activeVoiceId == voice.id && mediaPlayer?.isPlaying == true) {
            pause()
            return
        }

        stop()
        activeVoiceId = voice.id
        _playbackState.value = AzanPlaybackState.Loading(voice.id)

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(context, Uri.parse(voice.streamUrl))
                setOnPreparedListener { mp ->
                    mp.start()
                    _playbackState.value = AzanPlaybackState.Playing(
                        voiceId = voice.id,
                        progress = 0f,
                        currentSeconds = 0,
                        totalSeconds = mp.duration / 1000
                    )
                }
                setOnCompletionListener {
                    stop()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    _playbackState.value = AzanPlaybackState.Error("تعذر تشغيل صوت الأذان عبر الإنترنت. يرجى التحقق من اتصال الشبكة.")
                    stop()
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Azan audio: ${e.message}", e)
            _playbackState.value = AzanPlaybackState.Error("خطأ في تشغيل الصوت: ${e.localizedMessage}")
            stop()
        }
    }

    fun pause() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    activeVoiceId?.let { id ->
                        _playbackState.value = AzanPlaybackState.Paused(id)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing player", e)
        }
    }

    fun resume() {
        try {
            mediaPlayer?.let {
                if (!it.isPlaying) {
                    it.start()
                    activeVoiceId?.let { id ->
                        val dur = it.duration.coerceAtLeast(1)
                        _playbackState.value = AzanPlaybackState.Playing(
                            voiceId = id,
                            progress = if (it.duration > 0) (it.currentPosition.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f,
                            currentSeconds = it.currentPosition / 1000,
                            totalSeconds = dur / 1000
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming player", e)
        }
    }

    fun stop() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing player", e)
        } finally {
            mediaPlayer = null
            activeVoiceId = null
            _playbackState.value = AzanPlaybackState.Idle
        }
    }
}
