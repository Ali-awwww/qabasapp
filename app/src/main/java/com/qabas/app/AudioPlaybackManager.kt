package com.qabas.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class PlayableAudioTrack(
    val id: String,
    val title: String,
    val artist: String = "قبس",
    val audioUrl: String,
    val duration: String = "0:00",
    val category: String = "تلاوات قرآنية",
    val badge: String = "سحابي",
    val youtubeUrl: String? = null
)

enum class AudioRepeatMode {
    OFF, ALL, ONE
}

object AudioPlaybackManager {
    private const val TAG = "AudioPlaybackManager"

    private val _currentTrack = MutableStateFlow<PlayableAudioTrack?>(null)
    val currentTrack: StateFlow<PlayableAudioTrack?> = _currentTrack.asStateFlow()

    private val _playlist = MutableStateFlow<List<PlayableAudioTrack>>(emptyList())
    val playlist: StateFlow<List<PlayableAudioTrack>> = _playlist.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _totalDurationMs = MutableStateFlow(1L)
    val totalDurationMs: StateFlow<Long> = _totalDurationMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _repeatMode = MutableStateFlow(AudioRepeatMode.ALL)
    val repeatMode: StateFlow<AudioRepeatMode> = _repeatMode.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _sleepTimerRemainingSeconds = MutableStateFlow(0)
    val sleepTimerRemainingSeconds: StateFlow<Int> = _sleepTimerRemainingSeconds.asStateFlow()

    private var sleepTimerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Internal bridge to the service
    internal var serviceInstance: AudioPlaybackService? = null

    fun playTrack(context: Context, track: PlayableAudioTrack, newPlaylist: List<PlayableAudioTrack>? = null) {
        val list = newPlaylist ?: if (_playlist.value.any { it.id == track.id }) _playlist.value else _playlist.value + track
        _playlist.value = list
        val idx = list.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: 0
        _currentIndex.value = idx
        _currentTrack.value = track

        startService(context, AudioPlaybackService.ACTION_PLAY)
    }

    fun playPause(context: Context) {
        if (_currentTrack.value == null && _playlist.value.isNotEmpty()) {
            playTrack(context, _playlist.value.first())
            return
        }
        startService(context, AudioPlaybackService.ACTION_PLAY_PAUSE)
    }

    /**
     * تبديل حالة التشغيل بين التشغيل والإيقاف المؤقت (togglePlayState)
     */
    fun togglePlayState(context: Context) = playPause(context)

    fun next(context: Context) {
        val list = _playlist.value
        if (list.isEmpty()) return
        val currentIdx = _currentIndex.value

        val nextIdx = if (_isShuffle.value && list.size > 1) {
            val randoms = list.indices.filter { it != currentIdx }
            randoms.randomOrNull() ?: 0
        } else {
            if (currentIdx + 1 < list.size) currentIdx + 1 else 0
        }

        playTrack(context, list[nextIdx])
    }

    fun previous(context: Context) {
        val list = _playlist.value
        if (list.isEmpty()) return
        val currentIdx = _currentIndex.value

        val prevIdx = if (currentIdx - 1 >= 0) currentIdx - 1 else list.size - 1
        playTrack(context, list[prevIdx])
    }

    fun seekTo(context: Context, positionMs: Long) {
        _currentPositionMs.value = positionMs
        serviceInstance?.seekTo(positionMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        serviceInstance?.setPlaybackSpeed(speed)
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    fun cycleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            AudioRepeatMode.OFF -> AudioRepeatMode.ALL
            AudioRepeatMode.ALL -> AudioRepeatMode.ONE
            AudioRepeatMode.ONE -> AudioRepeatMode.OFF
        }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerRemainingSeconds.value = 0
            return
        }

        _sleepTimerRemainingSeconds.value = minutes * 60
        sleepTimerJob = scope.launch {
            while (_sleepTimerRemainingSeconds.value > 0) {
                delay(1000)
                _sleepTimerRemainingSeconds.value = _sleepTimerRemainingSeconds.value - 1
            }
            // Stop playback on sleep timer expiry
            serviceInstance?.pausePlayback()
            _isPlaying.value = false
        }
    }

    fun addToPlaylist(track: PlayableAudioTrack) {
        if (_playlist.value.none { it.id == track.id }) {
            _playlist.value = _playlist.value + track
        }
    }

    fun removeFromPlaylist(trackId: String) {
        val current = _currentTrack.value
        val list = _playlist.value.filter { it.id != trackId }
        _playlist.value = list
        if (current?.id == trackId) {
            if (list.isNotEmpty()) {
                _currentIndex.value = 0
                _currentTrack.value = list[0]
            } else {
                _currentIndex.value = -1
                _currentTrack.value = null
                _isPlaying.value = false
            }
        } else {
            _currentIndex.value = list.indexOfFirst { it.id == current?.id }
        }
    }

    fun clearPlaylist() {
        _playlist.value = emptyList()
        _currentIndex.value = -1
        _currentTrack.value = null
        _isPlaying.value = false
    }

    private fun startService(context: Context, action: String) {
        val intent = Intent(context, AudioPlaybackService::class.java).apply {
            this.action = action
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    internal fun updateState(
        isPlaying: Boolean? = null,
        positionMs: Long? = null,
        durationMs: Long? = null
    ) {
        isPlaying?.let { _isPlaying.value = it }
        positionMs?.let { _currentPositionMs.value = it }
        durationMs?.let { _totalDurationMs.value = it }
    }
}

class AudioPlaybackService : Service(), AudioManager.OnAudioFocusChangeListener {

    companion object {
        const val CHANNEL_ID = "qabas_audio_playback_channel"
        const val NOTIFICATION_ID = 2026

        const val ACTION_PLAY = "com.qabas.app.action.PLAY"
        const val ACTION_PAUSE = "com.qabas.app.action.PAUSE"
        const val ACTION_PLAY_PAUSE = "com.qabas.app.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.qabas.app.action.NEXT"
        const val ACTION_PREV = "com.qabas.app.action.PREV"
        const val ACTION_STOP = "com.qabas.app.action.STOP"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var progressTickerJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        AudioPlaybackManager.serviceInstance = this
        createNotificationChannel()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Qabas:AudioWakeLock")?.apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val currentTrack = AudioPlaybackManager.currentTrack.value ?: PlayableAudioTrack(
            id = "default_track",
            title = "صوتيات قبس الدعوية",
            artist = "قبس",
            audioUrl = ""
        )
        try {
            startForeground(NOTIFICATION_ID, buildNotification(currentTrack, isPlaying = AudioPlaybackManager.isPlaying.value))
        } catch (e: Exception) {
            Log.w("AudioPlaybackService", "startForeground failed: ${e.message}")
        }

        when (intent?.action) {
            ACTION_PLAY -> {
                val track = AudioPlaybackManager.currentTrack.value
                if (track != null) {
                    playTrack(track)
                }
            }
            ACTION_PAUSE -> pausePlayback()
            ACTION_PLAY_PAUSE -> {
                if (mediaPlayer?.isPlaying == true) {
                    pausePlayback()
                } else {
                    resumePlayback()
                }
            }
            ACTION_NEXT -> AudioPlaybackManager.next(this)
            ACTION_PREV -> AudioPlaybackManager.previous(this)
            ACTION_STOP -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    private fun playTrack(track: PlayableAudioTrack) {
        try {
            requestAudioFocus()
            wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes timeout per acquire

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)

                when {
                    track.audioUrl.startsWith("assets/") -> {
                        val assetPath = track.audioUrl.removePrefix("assets/")
                        val afd = assets.openFd(assetPath)
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        afd.close()
                    }
                    track.audioUrl.startsWith("http://") || track.audioUrl.startsWith("https://") -> {
                        val headers = mapOf(
                            "User-Agent" to ExoPlayerFactory.USER_AGENT,
                            "Referer" to "https://www.islamweb.net/",
                            "Accept" to "*/*"
                        )
                        setDataSource(applicationContext, android.net.Uri.parse(track.audioUrl), headers)
                    }
                    track.audioUrl.startsWith("/") -> {
                        val file = File(track.audioUrl)
                        if (file.exists()) {
                            setDataSource(track.audioUrl)
                        } else {
                            val afd = assets.openFd("audio/recitation_yusuf.mp3")
                            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                            afd.close()
                        }
                    }
                    else -> {
                        val afd = assets.openFd("audio/recitation_yusuf.mp3")
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        afd.close()
                    }
                }

                setOnPreparedListener { mp ->
                    val totalDuration = mp.duration.toLong().coerceAtLeast(1000L)
                    AudioPlaybackManager.updateState(
                        isPlaying = true,
                        durationMs = totalDuration,
                        positionMs = 0L
                    )
                    setPlaybackSpeed(AudioPlaybackManager.playbackSpeed.value)
                    mp.start()
                    startProgressTicker()
                    startForeground(NOTIFICATION_ID, buildNotification(track, isPlaying = true))
                }

                setOnCompletionListener {
                    AudioPlaybackManager.updateState(isPlaying = false, positionMs = 0L)
                    when (AudioPlaybackManager.repeatMode.value) {
                        AudioRepeatMode.ONE -> {
                            seekTo(0L)
                            start()
                            AudioPlaybackManager.updateState(isPlaying = true)
                        }
                        AudioRepeatMode.ALL -> {
                            AudioPlaybackManager.next(this@AudioPlaybackService)
                        }
                        AudioRepeatMode.OFF -> {
                            val list = AudioPlaybackManager.playlist.value
                            val idx = AudioPlaybackManager.currentIndex.value
                            if (idx < list.size - 1) {
                                AudioPlaybackManager.next(this@AudioPlaybackService)
                            } else {
                                stopForeground(STOP_FOREGROUND_DETACH)
                                updateNotification(track, isPlaying = false)
                            }
                        }
                    }
                }

                setOnErrorListener { _, what, extra ->
                    Log.e("AudioPlaybackService", "MediaPlayer error: what=$what, extra=$extra")
                    AudioPlaybackManager.updateState(isPlaying = false)
                    serviceScope.launch(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            applicationContext,
                            "تعذر البث المباشر. جارٍ التوجيه لمشاهدة الدرس عبر يوتيوب 📺...",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        val targetYt = if (!track.youtubeUrl.isNullOrBlank()) {
                            track.youtubeUrl
                        } else {
                            val query = android.net.Uri.encode("${track.title} ${track.artist}")
                            "https://www.youtube.com/results?search_query=$query"
                        }
                        val ytIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(targetYt)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            startActivity(ytIntent)
                        } catch (_: Exception) {}
                    }
                    true
                }

                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("AudioPlaybackService", "Error playing track: ${e.message}", e)
        }
    }

    private fun resumePlayback() {
        requestAudioFocus()
        mediaPlayer?.start()
        AudioPlaybackManager.updateState(isPlaying = true)
        startProgressTicker()
        AudioPlaybackManager.currentTrack.value?.let { track ->
            startForeground(NOTIFICATION_ID, buildNotification(track, isPlaying = true))
        }
    }

    fun pausePlayback() {
        mediaPlayer?.pause()
        AudioPlaybackManager.updateState(isPlaying = false)
        progressTickerJob?.cancel()
        AudioPlaybackManager.currentTrack.value?.let { track ->
            updateNotification(track, isPlaying = false)
        }
    }

    private fun stopPlayback() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        AudioPlaybackManager.updateState(isPlaying = false, positionMs = 0L)
        progressTickerJob?.cancel()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.seekTo(positionMs.toInt())
        AudioPlaybackManager.updateState(positionMs = positionMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                mediaPlayer?.let { mp ->
                    val params = mp.playbackParams
                    params.speed = speed
                    mp.playbackParams = params
                }
            }
        }
    }

    private fun startProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = serviceScope.launch {
            while (isActive && mediaPlayer?.isPlaying == true) {
                val pos = mediaPlayer?.currentPosition?.toLong() ?: 0L
                AudioPlaybackManager.updateState(positionMs = pos)
                delay(50)
            }
        }
    }

    private fun buildNotification(track: PlayableAudioTrack, isPlaying: Boolean): android.app.Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val playPauseIntent = Intent(this, AudioPlaybackService::class.java).apply {
            action = ACTION_PLAY_PAUSE
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 1, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val prevIntent = Intent(this, AudioPlaybackService::class.java).apply { action = ACTION_PREV }
        val prevPendingIntent = PendingIntent.getService(
            this, 2, prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val nextIntent = Intent(this, AudioPlaybackService::class.java).apply { action = ACTION_NEXT }
        val nextPendingIntent = PendingIntent.getService(
            this, 3, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val stopIntent = Intent(this, AudioPlaybackService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 4, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(track.title)
            .setContentText("${track.artist} • ${track.category}")
            .setSmallIcon(R.drawable.ic_stat_qabas)
            .setColor(QabasNotificationHelper.BRAND_COLOR)
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .addAction(android.R.drawable.ic_media_previous, "السابق", prevPendingIntent)
            .addAction(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (isPlaying) "إيقاف مؤقت" else "تشغيل", playPausePendingIntent)
            .addAction(android.R.drawable.ic_media_next, "التالي", nextPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إغلاق", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(track: PlayableAudioTrack, isPlaying: Boolean) {
        val notification = buildNotification(track, isPlaying)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIFICATION_ID, notification)
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(this)
                .build()
            audioManager?.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> pausePlayback()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pausePlayback()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> mediaPlayer?.setVolume(0.2f, 0.2f)
            AudioManager.AUDIOFOCUS_GAIN -> {
                mediaPlayer?.setVolume(1.0f, 1.0f)
                if (AudioPlaybackManager.isPlaying.value) mediaPlayer?.start()
            }
        }
    }

    private fun createNotificationChannel() {
        QabasNotificationHelper.ensureChannel(this, QabasNotificationHelper.CHANNEL_AUDIO_PLAYBACK)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        AudioPlaybackManager.serviceInstance = null
        progressTickerJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }
}
