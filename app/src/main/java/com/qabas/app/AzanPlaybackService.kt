package com.qabas.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * خدمة تشغيل صوت الأذان في الخلفية (Foreground Azan Audio Service)
 */
class AzanPlaybackService : Service() {

    companion object {
        private const val TAG = "AzanPlaybackService"
        const val ACTION_START_AZAN = "com.qabas.app.ACTION_START_AZAN"
        const val ACTION_STOP_AZAN = "com.qabas.app.ACTION_STOP_AZAN"
        const val CHANNEL_ID = "qabas_azan_playback_channel"
        const val NOTIFICATION_ID = 8844

        fun startAzan(context: Context, voiceId: String, prayerName: String) {
            val intent = Intent(context, AzanPlaybackService::class.java).apply {
                action = ACTION_START_AZAN
                putExtra("voice_id", voiceId)
                putExtra("prayer_name", prayerName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopAzan(context: Context) {
            val intent = Intent(context, AzanPlaybackService::class.java).apply {
                action = ACTION_STOP_AZAN
            }
            context.startService(intent)
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Qabas:AzanPlaybackWakeLock").apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_AZAN) {
            stopPlayingAndSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_START_AZAN) {
            val voiceId = intent.getStringExtra("voice_id") ?: "makkah_mulla"
            val prayerName = intent.getStringExtra("prayer_name") ?: "الصلاة"
            playAzanAudio(voiceId, prayerName)
        }

        return START_NOT_STICKY
    }

    private fun playAzanAudio(voiceId: String, prayerName: String) {
        val selectedVoice = MuezzinVoiceManager.getVoiceById(applicationContext, voiceId)

        wakeLock?.acquire(300000L) // 5 minutes max

        val notification = buildForegroundNotification(prayerName, selectedVoice.nameAr)
        startForeground(NOTIFICATION_ID, notification)

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
                setDataSource(applicationContext, Uri.parse(selectedVoice.streamUrl))
                setOnPreparedListener { mp ->
                    mp.start()
                }
                setOnCompletionListener {
                    stopPlayingAndSelf()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    stopPlayingAndSelf()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing azan: ${e.message}", e)
            stopPlayingAndSelf()
        }
    }

    private fun stopPlayingAndSelf() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping media player: ${e.message}")
        }

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildForegroundNotification(prayerName: String, muezzinName: String): Notification {
        val stopIntent = Intent(this, AzanPlaybackService::class.java).apply {
            action = ACTION_STOP_AZAN
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 9922, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 9923, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🕌 حان الآن وقت $prayerName")
            .setContentText("صوت $muezzinName")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف الأذان ⏹️", stopPendingIntent)
            .addAction(android.R.drawable.ic_menu_compass, "فتح التطبيق 🕋", openPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "صوت الأذان المباشر",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تشغيل صوت الأذان في أوقات الصلاة"
                setSound(null, null)
                enableVibration(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopPlayingAndSelf()
        super.onDestroy()
    }
}
