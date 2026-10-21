package com.qabas.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * مستقبل تنبيهات الأذان والصلوات الخمس (Real Prayer Alarm & Azan Broadcast Receiver)
 */
class PrayerAzanReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PrayerAzanReceiver"
        const val CHANNEL_ID = "qabas_prayer_notifications_channel"
        const val NOTIFICATION_ID_BASE = 5000
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prayerKey = intent.getStringExtra("prayer_key") ?: PrayerAlarmManager.PRAYER_FAJR
        val prayerName = intent.getStringExtra("prayer_name") ?: "الصلاة"
        val soundMode = intent.getStringExtra("sound_mode") ?: "FULL_AZAN"
        val voiceId = intent.getStringExtra("voice_id") ?: "makkah_mulla"

        Log.d(TAG, "Prayer Alarm fired: $prayerName ($prayerKey), Sound: $soundMode, Voice: $voiceId")

        // WakeLock قصير لضمان اكتمال المعالجة
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Qabas:PrayerAlarmWakeLock")
        wakeLock?.acquire(15000L)

        try {
            // إظهار الإشعار والتعامل مع الصوت بناءً على النمط
            when (soundMode) {
                "FULL_AZAN", "TAKBEER_ONLY" -> {
                    // تشغيل خدمة الأذان المباشرة
                    AzanPlaybackService.startAzan(context, voiceId, prayerName)
                    showPrayerNotification(context, prayerKey, prayerName, "حان الآن موعد $prayerName، استمع للأذان وتقبل الله طاعتكم 🕋", soundMode)
                }
                "NOTIFICATION_ONLY" -> {
                    showPrayerNotification(context, prayerKey, prayerName, "حان الآن موعد $prayerName حسب توقيت مدينتك 🕌", soundMode)
                }
                "SILENT" -> {
                    showPrayerNotification(context, prayerKey, prayerName, "حان الآن موعد $prayerName (تنبيه صامت) 🌙", soundMode)
                }
            }

            // إعادة جدولة الصلوات التالية تلقائياً
            PrayerAlarmManager.scheduleAllPrayers(context)
        } finally {
            if (wakeLock?.isHeld == true) {
                wakeLock.release()
            }
        }
    }

    private fun showPrayerNotification(
        context: Context,
        prayerKey: String,
        prayerName: String,
        message: String,
        soundMode: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "مواقيت الصلاة والأذان",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات حية لمواقيت الصلاة الخمس والأذان الشريف"
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "prayer_times")
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            prayerKey.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // زر إيقاف الأذان في شريط الإشعارات
        val stopAzanIntent = Intent(context, AzanPlaybackService::class.java).apply {
            action = AzanPlaybackService.ACTION_STOP_AZAN
        }
        val stopAzanPendingIntent = PendingIntent.getService(
            context,
            9991,
            stopAzanIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notifId = NOTIFICATION_ID_BASE + prayerKey.hashCode().let { if (it < 0) -it else it } % 1000

        val appIcon = QabasNotificationHelper.getAppIconBitmap(context)

        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "prayer_times")
            putExtra("full_screen_azan", true)
            putExtra("prayer_name", prayerName)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            9930,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 🔑 نُظهر الشاشة الكاملة فقط في أوضاع الصوت الفعّالة (وليس الوضع الصامت)
        val isSilentMode = soundMode == "SILENT" || soundMode == "OFF" || soundMode.isBlank()
        val isStrongSound = soundMode == "FULL_AZAN" || soundMode == "TAKBEER_ONLY"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(appIcon)
            .setColor(0xFFD4AF37.toInt())
            .setContentTitle("الله أكبر • $prayerName")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openPendingIntent)
            // 🔑 يفتح شاشة الأذان كاملة فقط في أوضاع الصوت الفعّالة (تجاوز الوضع الصامت)
            .setFullScreenIntent(fullScreenPendingIntent, isStrongSound)
            .setAutoCancel(true)
            .setOngoing(isStrongSound)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف الأذان", stopAzanPendingIntent)

        QabasNotificationHelper.addActionMarkDone(
            builder = builder,
            context = context,
            notifId = notifId,
            confirmationMessage = "تقبل الله صلاتكم وطاعتكم 🌿"
        )

        builder.setAutoCancel(true)

        // 🔑 استخدام الاستيرادية القصوى للقناة الجديدة (Azan Alarm) حتى تظهر فوق شاشة القفل
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val azanAlarmChannel = NotificationChannel(
                "qabas_azan_alarm_channel",
                "منبه الأذان (شاشة كاملة)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعار الأذان الذي يفتح شاشة كاملة فوق أي تطبيق أو قفل"
                setSound(null, null)
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(azanAlarmChannel)
            builder.setChannelId("qabas_azan_alarm_channel")
        }

        notificationManager.notify(notifId, builder.build())

        AppNotificationService.recordNotification(
            context = context,
            title = "الله أكبر • $prayerName",
            message = message,
            category = AppNotificationService.CATEGORY_PRAYER
        )
    }
}
