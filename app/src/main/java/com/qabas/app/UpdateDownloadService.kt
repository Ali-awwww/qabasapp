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
import kotlinx.serialization.json.Json

/**
 * خدمة أمامية لتنزيل التحديثات: تُبقي التنزيل حيّاً في الخلفية
 * (إشعار دائم + WakeLock) حتى يكتمل أو يُلغيه المستخدم فقط.
 */
class UpdateDownloadService : Service() {

    companion object {
        const val ACTION_START = "com.qabas.app.UPDATE_START"
        const val ACTION_CANCEL = "com.qabas.app.UPDATE_CANCEL"
        const val EXTRA_INFO = "info_json"
        const val EXTRA_FORCE_FULL = "force_full"
        private const val NOTIF_ID = 2001
        private const val WAKE_TAG = "Qabas:UpdateDownload"

        fun start(context: Context, info: UpdateManager.UpdateInfo, forceFull: Boolean = false) {
            val intent = Intent(context, UpdateDownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_INFO, Json.encodeToString(UpdateManager.UpdateInfo.serializer(), info))
                putExtra(EXTRA_FORCE_FULL, forceFull)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancel(context: Context) {
            context.startService(
                Intent(context, UpdateDownloadService::class.java).apply { action = ACTION_CANCEL }
            )
        }

        /**
         * إشعار «اكتمل التنزيل — ثبّت الآن» لحزمة جاهزة للتثبيت.
         * يُستخدم عند وجود حزمة محمّلة مسبقاً عند الإقلاع: فتح المثبّت مباشرة من
         * الخلفية محجوب في أندرويد 10+، بينما نقرة الإشعار تفتح النشاط في
         * المقدمة ويفتح المثبّت بعدها (وإن رُفضت الإشعارات تبقى الحزمة
         * متاحة من مركز التنزيلات داخل التطبيق).
         */
        fun notifyInstallReady(context: Context, versionName: String, versionCode: Int, apkSize: Long = 0L) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            QabasNotificationHelper.ensureChannel(context, QabasNotificationHelper.CHANNEL_UPDATES)
            val installPending = UpdateInstallReceiver.pendingIntent(context, versionName, versionCode, apkSize)
            val notif = NotificationCompat.Builder(context, QabasNotificationHelper.CHANNEL_UPDATES)
                .setSmallIcon(R.drawable.ic_stat_qabas)
                .setLargeIcon(QabasNotificationHelper.getAppIconBitmap(context))
                .setColor(QabasNotificationHelper.BRAND_COLOR)
                .setContentTitle("اكتمل التنزيل")
                .setContentText("التحديث $versionName جاهز للتثبيت")
                .setOngoing(false)
                .setAutoCancel(true)
                .addAction(android.R.drawable.stat_sys_upload_done, "تثبيت الآن", installPending)
                .setContentIntent(installPending)
                .build()
            nm.notify(NOTIF_ID, notif)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var activeKey: String? = null
    private var lastPercent = -1
    private var activeVersionName: String = ""
    private var activeVersionCode: Int = -1
    private var activeApkSize: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                activeKey?.let { UpdateManager.cancelDownload(it) }
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val json = intent.getStringExtra(EXTRA_INFO) ?: return START_NOT_STICKY
                val forceFull = intent.getBooleanExtra(EXTRA_FORCE_FULL, false)
                val info = runCatching {
                    Json.decodeFromString(UpdateManager.UpdateInfo.serializer(), json)
                }.getOrNull() ?: return START_NOT_STICKY
                startDownload(info, forceFull)
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownload(info: UpdateManager.UpdateInfo, forceFull: Boolean) {
        activeKey = "${info.versionName}|${info.versionCode}"
        activeVersionName = info.versionName
        activeVersionCode = info.versionCode
        activeApkSize = info.apkSize
        lastPercent = -1
        ensureChannel()
        acquireWakeLock()
        startForegroundWithType(NOTIF_ID, buildNotification("بدء تنزيل التحديث…", null))
        scope.launch(Dispatchers.IO) {
            UpdateManager.downloadAndInstall(
                this@UpdateDownloadService, info,
                onProgress = { p -> updateNotification(p) },
                onDone = { ok, msg -> finishDownload(ok, msg) },
                forceFull = forceFull
            )
        }
    }

    private fun updateNotification(p: UpdateManager.DownloadProgress) {
        if (p.percent == lastPercent) return
        lastPercent = p.percent
        val label = when (p.phase) {
            UpdateManager.Phase.DOWNLOADING_DELTA -> "تنزيل تحديث صغير ${p.percent}٪"
            UpdateManager.Phase.APPLYING_PATCH -> "تطبيق التحديث الصغير…"
            UpdateManager.Phase.DOWNLOADING_FULL -> "تنزيل التحديث الكامل ${p.percent}٪"
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIF_ID, buildNotification(label, p.percent))
    }

    private fun finishDownload(ok: Boolean, msg: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val done = buildDoneNotification(
            if (ok) "اكتمل التنزيل" else "تعذّر التنزيل",
            msg,
            installable = ok
        )
        nm?.notify(NOTIF_ID, done)
        stopForeground(STOP_FOREGROUND_DETACH)
        releaseWakeLock()
        activeKey = null
        stopSelf()
    }

    private fun buildNotification(text: String, percent: Int?): android.app.Notification {
        val cancelIntent = PendingIntent.getService(
            this, 0,
            Intent(this, UpdateDownloadService::class.java).apply { action = ACTION_CANCEL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = QabasNotificationHelper.createProgressBuilder(
            this, QabasNotificationHelper.CHANNEL_UPDATES, "تحديث قبس", text
        )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إلغاء", cancelIntent)
        if (percent != null) {
            builder.setProgress(100, percent, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun buildDoneNotification(title: String, text: String, installable: Boolean): android.app.Notification {
        val builder = NotificationCompat.Builder(this, QabasNotificationHelper.CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_qabas)
            .setLargeIcon(QabasNotificationHelper.getAppIconBitmap(this))
            .setColor(QabasNotificationHelper.BRAND_COLOR)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(false)
            .setAutoCancel(!installable)

        if (installable) {
            // زر «تثبيت الآن» يفتح مثبّت النظام مباشرة من الإشعار
            val installPending = UpdateInstallReceiver.pendingIntent(this, activeVersionName, activeVersionCode, activeApkSize)
            builder.addAction(
                android.R.drawable.stat_sys_upload_done,
                "تثبيت الآن",
                installPending
            )
            builder.setContentIntent(installPending)
        }
        return builder.build()
    }

    private fun ensureChannel() {
        QabasNotificationHelper.ensureChannel(this, QabasNotificationHelper.CHANNEL_UPDATES)
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
            acquire(30 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        activeKey?.let { UpdateManager.cancelDownload(it) }
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }
}
