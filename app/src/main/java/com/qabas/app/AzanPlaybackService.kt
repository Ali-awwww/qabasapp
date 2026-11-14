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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * خدمة تشغيل صوت الأذان في الخلفية مع FullScreenIntent (تظهر شاشة الأذان فوق أي تطبيق)
 * تستخدم USAGE_ALARM لضمان سماع الأذان حتى مع Do Not Disturb وإعدادات مستوى الصوت المنخفضة.
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
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start AzanPlaybackService: ${e.message}")
                // fallback: try startService on API < 31 even if FGS restriction blocked it
                try { context.startService(intent) } catch (_: Exception) {}
            }
        }

        fun stopAzan(context: Context) {
            val intent = Intent(context, AzanPlaybackService::class.java).apply {
                action = ACTION_STOP_AZAN
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: android.media.AudioFocusRequest? = null
    private var audioManager: android.media.AudioManager? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Qabas:AzanPlaybackWakeLock")?.apply {
            setReferenceCounted(false)
        }
    }

    /**
     * 🔑 طلب تركيز الصوت (Audio Focus) — يوقف الموسيقى/الفيديو الأخرى أثناء الأذان
     */
    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    if (focusChange == android.media.AudioManager.AUDIOFOCUS_LOSS) {
                        stopPlayingAndSelf()
                    }
                }
                .build()
            audioFocusRequest = request
            am.requestAudioFocus(request) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(null, android.media.AudioManager.STREAM_ALARM, android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) ==
                    android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
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
            // نرفع الخدمة فورًا قبل أي اتصال أو تنزيل حتى لا يقتل النظام الخدمة أثناء التجهيز.
            startForegroundForAzan(prayerName, "جاري تجهيز صوت الأذان")
            playAzanAudio(voiceId, prayerName)
        }

        return START_NOT_STICKY
    }

    private fun playAzanAudio(voiceId: String, prayerName: String) {
        serviceScope.launch {
            val selectedVoice = AzanVoiceResolver.resolveVoice(applicationContext, voiceId)
            if (selectedVoice == null) {
                Log.w(TAG, "No adhan voice is available for id=$voiceId")
                stopPlayingAndSelf()
                return@launch
            }

            val source = if (selectedVoice.id.startsWith("usercloud_")) {
                withContext(Dispatchers.IO) {
                    UserAzanCloudRepository.resolveStreamUrl(selectedVoice.id)
                }
            } else {
                // لصوت المطور السحابي: تنزيل تلقائي إلى كاش دائم عند غيابه.
                AzanVoiceResolver.ensurePlayableSource(applicationContext, selectedVoice)
            }

            if (source.isNullOrBlank()) {
                Log.e(TAG, "No playable source for adhan voice ${selectedVoice.id}")
                stopPlayingAndSelf()
            } else {
                playAzanAudioResolved(selectedVoice, prayerName, source)
            }
        }
    }

    private fun playAzanAudioResolved(selectedVoice: AzanVoice, prayerName: String, streamUrl: String) {
        // 🔑 طلب تركيز الصوت قبل التشغيل — إذا فشل، نكمل مع تحذير
        val gotFocus = requestAudioFocus()
        if (!gotFocus) {
            Log.w(TAG, "Failed to get audio focus — playing anyway (alarm usage may override)")
        }

        wakeLock?.acquire(300000L) // 5 minutes max

        startForegroundForAzan(prayerName, selectedVoice.nameAr)

        val downloadedFile = if (!streamUrl.startsWith("http")) java.io.File(streamUrl) else null
        val legacyFile = java.io.File(getExternalFilesDir(null), "azan_${selectedVoice.id}.mp3")
        val dataSource: String = if (downloadedFile?.isFile == true && downloadedFile.length() > 0) {
            Log.d(TAG, "Using downloaded azan file: ${downloadedFile.absolutePath}")
            downloadedFile.absolutePath
        } else if (legacyFile.exists() && legacyFile.length() > 0) {
            Log.d(TAG, "Using legacy cached azan file: ${legacyFile.absolutePath}")
            legacyFile.absolutePath
        } else {
            Log.d(TAG, "Streaming configured adhan source")
            streamUrl
        }

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
                if (dataSource.startsWith("http")) {
                    setDataSource(applicationContext, Uri.parse(dataSource))
                } else {
                    setDataSource(dataSource)
                }
                setOnPreparedListener { mp -> mp.start() }
                setOnCompletionListener { stopPlayingAndSelf() }
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

    private fun startForegroundForAzan(prayerName: String, muezzinName: String) {
        val notification = buildForegroundNotification(prayerName, muezzinName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
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

        abandonAudioFocus()

        if (wakeLock?.isHeld == true) {
            try { wakeLock?.release() } catch (_: Exception) {}
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * إشعار Foreground مع FullScreenIntent - يفتح شاشة الأذان كاملة حتى لو كان الجهاز مقفلاً
     */
    private fun buildForegroundNotification(prayerName: String, muezzinName: String): Notification {
        val stopIntent = Intent(this, AzanPlaybackService::class.java).apply {
            action = ACTION_STOP_AZAN
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 9922, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenIntent = Intent(this, AdhanFullScreenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("full_screen_azan", true)
            putExtra("prayer_name", prayerName)
            putExtra("prayer_notification_id", NOTIFICATION_ID)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 9923, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_qabas)
            .setLargeIcon(QabasNotificationHelper.getAppIconBitmap(this))
            .setColor(QabasNotificationHelper.BRAND_COLOR)
            .setContentTitle("🕌 حان الآن وقت $prayerName")
            .setContentText("صوت $muezzinName")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(fullScreenPendingIntent)
            // 🔑 هذا هو المفتاح: يجعل النظام يفتح النشاط كملء الشاشة فورًا عند وصول الإشعار
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف الأذان ⏹️", stopPendingIntent)
            .addAction(android.R.drawable.ic_menu_compass, "فتح التطبيق 🕋", fullScreenPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        QabasNotificationHelper.ensureChannel(this, QabasNotificationHelper.CHANNEL_AZAN_PLAYBACK)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        stopPlayingAndSelf()
        super.onDestroy()
    }
}
