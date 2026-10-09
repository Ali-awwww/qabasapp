package com.qabas.app

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorderHelper(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    var outputFile: File? = null
        private set

    var isRecording = false
        private set

    var isPlaying = false
        private set

    fun startRecording(): Boolean {
        return try {
            stopRecording()
            val file = File(context.cacheDir, "tajweed_recitation_${System.currentTimeMillis()}.m4a")
            outputFile = file

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
            false
        }
    }

    fun getMaxAmplitude(): Int {
        return try {
            mediaRecorder?.maxAmplitude ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) return outputFile
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
            isRecording = false
        }
        return outputFile
    }

    fun startPlayback(onComplete: () -> Unit): Boolean {
        val file = outputFile ?: return false
        if (!file.exists() || file.length() == 0L) return false
        
        return try {
            stopPlayback()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    this@AudioRecorderHelper.isPlaying = false
                    onComplete()
                }
                start()
            }
            isPlaying = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            isPlaying = false
            false
        }
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
            isPlaying = false
        }
    }

    fun release() {
        stopRecording()
        stopPlayback()
    }
}
