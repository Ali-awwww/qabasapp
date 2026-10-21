package com.qabas.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * خدمة أمامية لتنزيل نماذج الذكاء الاصطناعي: إشعار عدّاد دائم + WakeLock،
 * الخروج من التطبيق لا يلغي التنزيل — يكتمل أو يُلغى بزر الإلغاء فقط.
 */
class ModelDownloadService : Service() {

    companion object {
        const val ACTION_START = "com.qabas.app.MODEL_START"
        const val ACTION_CANCEL = "com.qabas.app.MODEL_CANCEL"
        const val EXTRA_MODEL_ID = "model_id"
        private val CHANNEL_ID get() = QabasNotificationHelper.CHANNEL_MODELS
        private const val NOTIF_ID = 2002
        private const val WAKE_TAG = "Qabas:ModelDownload"

        fun start(context: Context, modelId: String) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODEL_ID, modelId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancel(context: Context) {
            context.startService(
                Intent(context, ModelDownloadService::class.java).apply { action = ACTION_CANCEL }
            )
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var activeModel: OnDeviceModelInfo? = null
    private var lastPercent = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                activeModel?.let { OnDeviceModelManager.cancelDownload(this, it) }
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val modelId = intent.getStringExtra(EXTRA_MODEL_ID) ?: return START_NOT_STICKY
                val model = OnDeviceModelManager.availableModels.find { it.id == modelId }
                    ?: return START_NOT_STICKY
                startDownload(model)
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownload(model: OnDeviceModelInfo) {
        activeModel = model
        lastPercent = -1
        ensureChannel()
        acquireWakeLock()
        startForegroundWithType(NOTIF_ID, buildNotification(model.name, "بدء التنزيل…", null, null))
        scope.launch(Dispatchers.IO) {
            val ok = OnDeviceModelManager.downloadModel(this@ModelDownloadService, model)
            finishDownload(model, ok)
        }
        // مرآة التقدم الحي من حالة المدير إلى الإشعار
        scope.launch {
            OnDeviceModelManager.downloadState.collect { state ->
                val m = activeModel ?: return@collect
                when (state) {
                    is ModelDownloadState.Downloading -> {
                        if (state.modelId != m.id) return@collect
                        updateNotification(m, state)
                    }
                    is ModelDownloadState.Interrupted -> {
                        if (state.modelId != m.id) return@collect
                        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        nm?.notify(NOTIF_ID, buildDoneNotification("توقف التنزيل مؤقتاً", "انقطع الاتصال — استكمل من التطبيق ▶"))
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun updateNotification(model: OnDeviceModelInfo, s: ModelDownloadState.Downloading) {
        val pct = (s.progress * 100).toInt().coerceIn(0, 100)
        if (pct == lastPercent) return
        lastPercent = pct
        val mb = "${s.downloadedBytes / (1024 * 1024)}MB / ${s.totalBytes / (1024 * 1024)}MB"
        val speed = if (s.speedKbps > 0) " • ${s.speedKbps}KB/s" else ""
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIF_ID, buildNotification(model.name, "$mb$speed", pct, model))
    }

    private fun finishDownload(model: OnDeviceModelInfo, ok: Boolean) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(
            NOTIF_ID,
            buildDoneNotification(
                if (ok) "اكتمل تنزيل ${model.name}" else "تعذر التنزيل",
                if (ok) "النموذج جاهز في التطبيق ✅" else "أعد المحاولة من مركز التنزيل"
            )
        )
        stopForeground(STOP_FOREGROUND_DETACH)
        releaseWakeLock()
        activeModel = null
        stopSelf()
    }

    private fun buildNotification(
        modelName: String,
        text: String,
        percent: Int?,
        model: OnDeviceModelInfo?
    ): android.app.Notification {
        val cancelIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ModelDownloadService::class.java).apply { action = ACTION_CANCEL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = QabasNotificationHelper.createProgressBuilder(
            this, CHANNEL_ID, "تنزيل $modelName",
            if (percent != null) "$text • $percent٪" else text
        )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إلغاء", cancelIntent)
        if (percent != null) {
            builder.setProgress(100, percent, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        if (model != null) {
            builder.setSubText("${model.sizeFormatted} • يعمل في الخلفية")
        }
        return builder.build()
    }

    private fun buildDoneNotification(title: String, text: String): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_qabas)
            .setLargeIcon(QabasNotificationHelper.getAppIconBitmap(this))
            .setColor(QabasNotificationHelper.BRAND_COLOR)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
    }

    private fun ensureChannel() {
        QabasNotificationHelper.ensureChannel(this, QabasNotificationHelper.CHANNEL_MODELS)
    }

    private fun startForegroundWithType(id: Int, notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                id, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(id, notification)
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG).apply {
            acquire(60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        activeModel?.let { OnDeviceModelManager.cancelDownload(this, it) }
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }
}
