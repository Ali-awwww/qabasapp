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
        NotificationPreferencesManager.init(context)
        if (!NotificationPreferencesManager.masterEnabled.value) return

        val type = intent.getStringExtra("notification_type") ?: "general"
        val typeEnabled = when (type) {
            "suhur" -> NotificationPreferencesManager.suhurReminderEnabled.value
            "fajr" -> NotificationPreferencesManager.fajrReminderEnabled.value
            "morning" -> NotificationPreferencesManager.morningAthkarEnabled.value
            "evening" -> NotificationPreferencesManager.eveningAthkarEnabled.value
            else -> true
        }
        if (!typeEnabled) return
        val (title, message) = when (type) {
            "suhur" -> {
                "قبس السحر ✨" to (getRandomVerse(context) ?: "«أَمَّنْ هُوَ قَانِتٌ آنَاءَ اللَّيْلِ سَاجِدًا وَقَائِمًا يَحْذَرُ الْآخِرَةَ وَيَرْجُو رَحْمَةَ رَبِّهِ»")
            }
            "fajr" -> {
                "نور الفجر 🌅" to (getRandomVirtue(context) ?: "«وقرآن الفجر إن قرآن الفجر كان مشهودا» - اجعل وردك الآن بداية لفتح عظيم.")
            }
            "morning" -> {
                "أذكار الصباح ☀️" to "«أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ» - حصّن يومك بذكر الله."
            }
            "evening" -> {
                "أذكار المساء 🌙" to "«أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ» - اختم يومك بالطمأنينة."
            }
            else -> {
                "تذكير إيماني 🌿" to "«وذكر فإن الذكرى تنفع المؤمنين» - حان وقت وردك القرآني."
            }
        }

        showNotification(context, title, message)
        AppNotificationService.recordNotification(
            context = context,
            title = title,
            message = message,
            category = AppNotificationService.CATEGORY_SPIRITUAL
        )

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
    private const val PREFS = "qabas_notification_settings"
    private const val KEY_SUHUR_HOUR = "notif_suhur_hour"
    private const val KEY_SUHUR_MINUTE = "notif_suhur_minute"
    private const val KEY_FAJR_HOUR = "notif_fajr_hour"
    private const val KEY_FAJR_MINUTE = "notif_fajr_minute"
    private const val KEY_MORNING_HOUR = "notif_morning_hour"
    private const val KEY_MORNING_MINUTE = "notif_morning_minute"
    private const val KEY_EVENING_HOUR = "notif_evening_hour"
    private const val KEY_EVENING_MINUTE = "notif_evening_minute"

    private fun requestCodeFor(type: String): Int = when (type) {
        "suhur" -> 1001
        "fajr" -> 1002
        "morning" -> 1003
        "evening" -> 1004
        else -> 1005
    }

    fun initialize(context: Context) {
        NotificationPreferencesManager.init(context)
        if (!NotificationPreferencesManager.masterEnabled.value) return
        if (NotificationPreferencesManager.suhurReminderEnabled.value) {
            scheduleNotification(context, "suhur")
        } else {
            cancelNotification(context, "suhur")
        }
        if (NotificationPreferencesManager.fajrReminderEnabled.value) {
            scheduleNotification(context, "fajr")
        } else {
            cancelNotification(context, "fajr")
        }
        if (NotificationPreferencesManager.morningAthkarEnabled.value) {
            scheduleNotification(context, "morning")
        } else {
            cancelNotification(context, "morning")
        }
        if (NotificationPreferencesManager.eveningAthkarEnabled.value) {
            scheduleNotification(context, "evening")
        } else {
            cancelNotification(context, "evening")
        }
    }

    fun cancelNotification(context: Context, type: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
        val requestCode = requestCodeFor(type)
        val intent = Intent(context, SpiritualNotificationReceiver::class.java).apply {
            putExtra("notification_type", type)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) alarmManager.cancel(pendingIntent)
    }

    fun scheduleNotification(context: Context, type: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(context, SpiritualNotificationReceiver::class.java).apply {
            putExtra("notification_type", type)
        }

        val requestCode = requestCodeFor(type)
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 🔑 قراءة الوقت المحفوظ من إعدادات الإشعارات بدل الأوقات الثابتة
        val notifPrefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val (hour, minute) = when (type) {
            "suhur" -> Pair(
                notifPrefs.getInt(KEY_SUHUR_HOUR, 3),
                notifPrefs.getInt(KEY_SUHUR_MINUTE, 30)
            )
            "fajr" -> Pair(
                notifPrefs.getInt(KEY_FAJR_HOUR, 5),
                notifPrefs.getInt(KEY_FAJR_MINUTE, 0)
            )
            "morning" -> Pair(
                notifPrefs.getInt(KEY_MORNING_HOUR, 7),
                notifPrefs.getInt(KEY_MORNING_MINUTE, 0)
            )
            "evening" -> Pair(
                notifPrefs.getInt(KEY_EVENING_HOUR, 18),
                notifPrefs.getInt(KEY_EVENING_MINUTE, 0)
            )
            else -> Pair(7, 0)
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
