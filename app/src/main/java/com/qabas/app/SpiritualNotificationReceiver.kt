package com.qabas.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.util.Calendar

class SpiritualNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val qabasPrefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val isEnabled = qabasPrefs.getBoolean("spiritual_notifications_enabled", true)
        if (!isEnabled) return

        val type = intent.getStringExtra("notification_type") ?: "general"
        val (title, message) = when (type) {
            "suhur" -> {
                "قبس السحر ✨" to (getRandomVerse(context) ?: "«أَمَّنْ هُوَ قَانِتٌ آنَاءَ اللَّيْلِ سَاجِدًا وَقَائِمًا يَحْذَرُ الْآخِرَةَ وَيَرْجُو رَحْمَةَ رَبِّهِ»")
            }
            "fajr" -> {
                "نور الفجر 🌅" to (getRandomVirtue(context) ?: "«وقرآن الفجر إن قرآن الفجر كان مشهودا» - اجعل وردك الآن بداية لفتح عظيم.")
            }
            else -> {
                "تذكير إيماني 🌿" to "«وذكر فإن الذكرى تنفع المؤمنين» - حان وقت وردك القرآني."
            }
        }

        showNotification(context, title, message)
        
        // Reschedule for next day
        SpiritualNotificationManager.scheduleNotification(context, type)
    }

    private fun getRandomVerse(context: Context): String? {
        // Simple logic to get a random famous verse
        return QuranDataProvider.surahs.flatMap { it.famousVerses }.randomOrNull()
    }

    private fun getRandomVirtue(context: Context): String? {
        return QuranDataProvider.surahs.mapNotNull { it.virtue }.randomOrNull()
    }

    private fun showNotification(context: Context, title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = (System.currentTimeMillis() % 10000).toInt()

        val builder = QabasNotificationHelper.createBaseBuilder(
            context = context,
            channelId = QabasNotificationHelper.CHANNEL_SPIRITUAL,
            title = title,
            message = message
        )

        // أداة 1: استماع فوري للآية / الورد
        QabasNotificationHelper.addActionListen(
            builder = builder,
            context = context,
            notifId = notifId,
            audioTitle = title
        )

        // أداة 2: مشاركة المحتوى
        QabasNotificationHelper.addActionShare(
            builder = builder,
            context = context,
            notifId = notifId,
            shareText = "$title\n$message\n🌿 عبر استوديو قبس"
        )

        // أداة 3: تأكيد قراءة الورد
        QabasNotificationHelper.addActionMarkDone(
            builder = builder,
            context = context,
            notifId = notifId,
            confirmationMessage = "تقبل الله طاعتكم 🌿 تم تسجيل إتمام الورد الإيماني بنجاح"
        )

        // أداة 4: تأجيل التنبيه
        QabasNotificationHelper.addActionSnooze(
            builder = builder,
            context = context,
            notifId = notifId
        )

        notificationManager.notify(notifId, builder.build())
    }
}

object SpiritualNotificationManager {
    fun initialize(context: Context) {
        val qabasPrefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val isEnabled = qabasPrefs.getBoolean("spiritual_notifications_enabled", true)
        if (isEnabled) {
            scheduleNotification(context, "suhur")
            scheduleNotification(context, "fajr")
        }
    }

    fun scheduleNotification(context: Context, type: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(context, SpiritualNotificationReceiver::class.java).apply {
            putExtra("notification_type", type)
        }

        val requestCode = if (type == "suhur") 1001 else 1002
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 🔑 قراءة الوقت المحفوظ من إعدادات الإشعارات بدل الأوقات الثابتة
        val notifPrefs = context.getSharedPreferences("qabas_notification_settings", Context.MODE_PRIVATE)
        val hour: Int
        val minute: Int
        if (type == "suhur") {
            hour = notifPrefs.getInt("notif_suhur_hour", 3)
            minute = notifPrefs.getInt("notif_suhur_minute", 30)
        } else {
            hour = notifPrefs.getInt("notif_fajr_hour", 5)
            minute = notifPrefs.getInt("notif_fajr_minute", 0)
        }

        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        if (calendar.timeInMillis <= now) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        // 🔑 منبه دقيق (setAlarmClock) لا يتأثر بـ Doze لضمان الوصول في وقته
        val showIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("navigate_to", "prayer_times")
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            requestCode + 10000,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setAlarmClock(
                android.app.AlarmManager.AlarmClockInfo(calendar.timeInMillis, showPendingIntent),
                pendingIntent
            )
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }
}
