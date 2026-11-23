package com.qabas.app

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log

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


    private var mediaPlayer: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var fadeRunnable: Runnable? = null
    private var fadeOutStartRunnable: Runnable? = null



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
}
