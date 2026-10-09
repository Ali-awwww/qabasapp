package com.qabas.app

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * صوت السبلاش — متزامن مع الآية المعروضة:
 * ﴿ ادْعُ إِلَىٰ سَبِيلِ رَبِّكَ بِالْحِكْمَةِ وَالْمَوْعِظَةِ الْحَسَنَةِ ﴾
 * سورة النحل — الآية 125
 *
 * ضع الملف في: app/src/main/assets/audio/entry_ayah_nahl_125.m4a
 * (أو .mp3 بنفس الاسم مع تعديل الامتداد أدناه إن لزم)
 */
object AudioPlayerManager {
    private const val TAG = "AudioPlayerManager"
    private const val PREFS_NAME = "qabas_audio_prefs"
    private const val KEY_STARTUP_AUDIO_ENABLED = "startup_audio_enabled"
    private const val KEY_HAS_PLAYED_FIRST_TIME = "has_played_first_time"

    /** آية النحل 125 — ادع إلى سبيل ربك بالحكمة والموعظة الحسنة */
    private const val ENTRY_AYAH_ASSET = "audio/entry_ayah_nahl_125.m4a"
    /** احتياطي قديم إن لم يوجد الملف الجديد */
    private const val ENTRY_AYAH_FALLBACK = "audio/entry_ayah_ruj3a.m4a"

    private var mediaPlayer: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var fadeRunnable: Runnable? = null
    private var fadeOutStartRunnable: Runnable? = null

    private val _isStartupAudioEnabled = MutableStateFlow(true)
    val isStartupAudioEnabled: StateFlow<Boolean> = _isStartupAudioEnabled

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isStartupAudioEnabled.value = prefs.getBoolean(KEY_STARTUP_AUDIO_ENABLED, true)
    }

    fun toggleStartupAudio(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_STARTUP_AUDIO_ENABLED, enabled).apply()
        _isStartupAudioEnabled.value = enabled
    }

    fun playPreLoginAudio(context: Context) {
        playEntryAyah(context, respectFirstLaunchGate = true)
    }

    fun playPostLoginAudio(context: Context) {
        playEntryAyah(context, respectFirstLaunchGate = false)
    }

    private fun playEntryAyah(context: Context, respectFirstLaunchGate: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(KEY_STARTUP_AUDIO_ENABLED, true)
        val hasPlayedFirstTime = prefs.getBoolean(KEY_HAS_PLAYED_FIRST_TIME, false)

        if (!isEnabled && hasPlayedFirstTime) return
        if (respectFirstLaunchGate && hasPlayedFirstTime && !isEnabled) return

        val assetPath = when {
            assetExists(context, ENTRY_AYAH_ASSET) -> ENTRY_AYAH_ASSET
            assetExists(context, ENTRY_AYAH_FALLBACK) -> {
                Log.i(TAG, "Using fallback ayah until nahl_125 is added")
                ENTRY_AYAH_FALLBACK
            }
            else -> {
                Log.i(TAG, "No entry ayah asset found — skip audio")
                return
            }
        }

        stopAudio()
        try {
            val afd = context.assets.openFd(assetPath)
            if (afd.length <= 0L) {
                afd.close()
                return
            }
            val player = MediaPlayer()
            player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            player.setVolume(0f, 0f)
            player.prepare()
            player.setOnCompletionListener { stopAudio() }
            mediaPlayer = player
            player.start()
            startFadeIn(player, fadeInMs = 900)
            scheduleFadeOut(player, fadeOutMs = 1100)
            prefs.edit().putBoolean(KEY_HAS_PLAYED_FIRST_TIME, true).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Entry ayah playback failed: ${e.message}")
            stopAudio()
        }
    }

    private fun startFadeIn(player: MediaPlayer, fadeInMs: Int) {
        cancelFade()
        val steps = 18
        val stepMs = (fadeInMs / steps).coerceAtLeast(30)
        var step = 0
        val runnable = object : Runnable {
            override fun run() {
                try {
                    if (mediaPlayer !== player) return
                    step++
                    val v = (step.toFloat() / steps).coerceIn(0f, 1f)
                    player.setVolume(v, v)
                    if (step < steps) mainHandler.postDelayed(this, stepMs.toLong())
                } catch (_: Exception) {
                }
            }
        }
        fadeRunnable = runnable
        mainHandler.post(runnable)
    }

    private fun scheduleFadeOut(player: MediaPlayer, fadeOutMs: Int) {
        val duration = try {
            player.duration
        } catch (_: Exception) {
            0
        }
        if (duration <= 0) return
        val startFadeAt = (duration - fadeOutMs).coerceAtLeast(0)
        fadeOutStartRunnable?.let { mainHandler.removeCallbacks(it) }
        val starter = Runnable { fadeOut(player, fadeOutMs) }
        fadeOutStartRunnable = starter
        mainHandler.postDelayed(starter, startFadeAt.toLong())
    }

    private fun fadeOut(player: MediaPlayer, fadeOutMs: Int) {
        cancelFade()
        val steps = 16
        val stepMs = (fadeOutMs / steps).coerceAtLeast(30)
        var step = 0
        val runnable = object : Runnable {
            override fun run() {
                try {
                    if (mediaPlayer !== player) return
                    step++
                    val v = (1f - step.toFloat() / steps).coerceIn(0f, 1f)
                    player.setVolume(v, v)
                    if (step < steps) mainHandler.postDelayed(this, stepMs.toLong())
                } catch (_: Exception) {
                }
            }
        }
        fadeRunnable = runnable
        mainHandler.post(runnable)
    }

    fun stopAudio() {
        cancelFade()
        try {
            mediaPlayer?.let {
                try {
                    if (it.isPlaying) it.stop()
                } catch (_: Exception) {
                }
                try {
                    it.release()
                } catch (_: Exception) {
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping audio: ${e.message}")
        } finally {
            mediaPlayer = null
        }
    }

    private fun cancelFade() {
        fadeRunnable?.let { mainHandler.removeCallbacks(it) }
        fadeRunnable = null
        fadeOutStartRunnable?.let { mainHandler.removeCallbacks(it) }
        fadeOutStartRunnable = null
    }

    private fun assetExists(context: Context, assetPath: String): Boolean {
        return try {
            val dir = assetPath.substringBeforeLast('/', missingDelimiterValue = "")
            val fileName = assetPath.substringAfterLast('/')
            val files = context.assets.list(dir) ?: emptyArray()
            files.contains(fileName)
        } catch (_: Exception) {
            false
        }
    }
}
