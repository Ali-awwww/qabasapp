package com.qabas.app

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
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

    /** مهلة تجهيز الصوت — بعدها نُفشل بدل الانتظار الأبد */
    private const val PREPARE_TIMEOUT_MS = 20_000L
    private const val PROGRESS_TICK_MS = 500L

    private var mediaPlayer: MediaPlayer? = null
    private var activeVoiceId: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private val prepareTimeout = Runnable { onPrepareTimeout() }
    private val progressTicker = object : Runnable {
        override fun run() {
            val player = mediaPlayer ?: return
            val id = activeVoiceId ?: return
            try {
                if (player.isPlaying) {
                    val dur = player.duration.coerceAtLeast(1)
                    _playbackState.value = AzanPlaybackState.Playing(
                        voiceId = id,
                        progress = (player.currentPosition.toFloat() / dur.toFloat()).coerceIn(0f, 1f),
                        currentSeconds = player.currentPosition / 1000,
                        totalSeconds = dur / 1000
                    )
                    handler.postDelayed(this, PROGRESS_TICK_MS)
                }
            } catch (e: Exception) {
                Log.w(TAG, "progress tick stopped: ${e.message}")
            }
        }
    }

    private val _playbackState = MutableStateFlow<AzanPlaybackState>(AzanPlaybackState.Idle)
    val playbackState: StateFlow<AzanPlaybackState> = _playbackState

    fun playVoice(context: Context, voice: AzanVoice) {
        // إعادة التشغيل لنفس الصوت بعد الإيقاف المؤقت → استئناف
        val current = _playbackState.value
        if (current is AzanPlaybackState.Paused && current.voiceId == voice.id) {
            resume()
            return
        }
        // نفس الصوت قيد التشغيل → إيقاف مؤقت
        if (activeVoiceId == voice.id && mediaPlayer?.isPlaying == true) {
            pause()
            return
        }

        releasePlayer()
        activeVoiceId = voice.id
        _playbackState.value = AzanPlaybackState.Loading(voice.id)

        try {
            val player = MediaPlayer()
            // ⚠️ نُسنِد المشغّل قبل prepareAsync حتى يصل مستمع الخطأ إلى المشغّل الصحيح
            mediaPlayer = player

            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )

            player.setOnPreparedListener { mp ->
                handler.removeCallbacks(prepareTimeout)
                try {
                    mp.start()
                    val dur = mp.duration.coerceAtLeast(1)
                    _playbackState.value = AzanPlaybackState.Playing(
                        voiceId = voice.id,
                        progress = 0f,
                        currentSeconds = 0,
                        totalSeconds = dur / 1000
                    )
                    handler.removeCallbacks(progressTicker)
                    handler.postDelayed(progressTicker, PROGRESS_TICK_MS)
                } catch (e: Exception) {
                    fail(voice.id, "تعذّر بدء التشغيل: ${e.localizedMessage}")
                }
            }

            player.setOnCompletionListener {
                handler.removeCallbacks(progressTicker)
                releasePlayer()
                activeVoiceId = null
                _playbackState.value = AzanPlaybackState.Idle
            }

            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                // ⚠️ لا نستدعي stop() هنا — كانت تمسح حالة الخطأ وتُخفيها عن المستخدم
                fail(voice.id, "تعذّر تشغيل صوت الأذان عبر الإنترنت. تأكّد من الاتصال بالإنترنت ثم أعد المحاولة.")
                true
            }

            handler.postDelayed(prepareTimeout, PREPARE_TIMEOUT_MS)
            player.setDataSource(context, Uri.parse(voice.streamUrl))
            player.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Azan audio: ${e.message}", e)
            fail(voice.id, "تعذّر تشغيل الصوت: ${e.localizedMessage ?: "رابط الصوت غير صالح"}")
        }
    }

    private fun onPrepareTimeout() {
        val id = activeVoiceId ?: return
        if (_playbackState.value is AzanPlaybackState.Loading) {
            fail(id, "انتهت مهلة تحميل الصوت — تحقّق من الاتصال بالإنترنت")
        }
    }

    /**
     * إنهاء التشغيل مع **الاحتفاظ** بحالة الخطأ (العيب القديم كان يمسحها بـ stop()).
     */
    private fun fail(voiceId: String, message: String) {
        handler.removeCallbacks(prepareTimeout)
        handler.removeCallbacks(progressTicker)
        releasePlayer()
        activeVoiceId = null
        _playbackState.value = AzanPlaybackState.Error(message)
    }

    fun pause() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    handler.removeCallbacks(progressTicker)
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
                    val id = activeVoiceId
                    if (id != null) {
                        val dur = it.duration.coerceAtLeast(1)
                        _playbackState.value = AzanPlaybackState.Playing(
                            voiceId = id,
                            progress = (it.currentPosition.toFloat() / dur.toFloat()).coerceIn(0f, 1f),
                            currentSeconds = it.currentPosition / 1000,
                            totalSeconds = dur / 1000
                        )
                        handler.removeCallbacks(progressTicker)
                        handler.postDelayed(progressTicker, PROGRESS_TICK_MS)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming player", e)
        }
    }

    /** إيقاف صريح من المستخدم → الحالة تصبح Idle */
    fun stop() {
        handler.removeCallbacks(prepareTimeout)
        handler.removeCallbacks(progressTicker)
        releasePlayer()
        activeVoiceId = null
        _playbackState.value = AzanPlaybackState.Idle
    }

    /**
     * تحرير آمن للمشغّل — يتجنّب استدعاء stop() على مشغّل في حالة خطأ
     * (كان يسبّب استثناءً يمنع reset/release ويترك المشغّل معلّقاً).
     */
    private fun releasePlayer() {
        try {
            mediaPlayer?.let { player ->
                runCatching { player.reset() }
                runCatching { player.release() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "release player: ${e.message}")
        } finally {
            mediaPlayer = null
        }
    }
}
