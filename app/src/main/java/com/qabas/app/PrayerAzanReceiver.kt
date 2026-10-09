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
                    showPrayerNotification(context, prayerKey, prayerName, "حان الآن موعد $prayerName، استمع للأذان وتقبل الله طاعتكم 🕋")
                }
                "NOTIFICATION_ONLY" -> {
                    showPrayerNotification(context, prayerKey, prayerName, "حان الآن موعد $prayerName حسب توقيت مدينتك 🕌")
                }
                "SILENT" -> {
                    showPrayerNotification(context, prayerKey, prayerName, "حان الآن موعد $prayerName (تنبيه صامت) 🌙")
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
        message: String
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

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(appIcon)
            .setColor(0xFFD4AF37.toInt())
            .setContentTitle("الله أكبر • $prayerName")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف الأذان", stopAzanPendingIntent)

        QabasNotificationHelper.addActionMarkDone(
            builder = builder,
            context = context,
            notifId = notifId,
            confirmationMessage = "تقبل الله صلاتكم وطاعتكم 🌿"
        )

        builder.setAutoCancel(true)

        notificationManager.notify(notifId, builder.build())
    }
}
