package com.qabas.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 🛰️ NotificationPreferencesManager — مدير إعدادات وتفضيلات إشعارات قبس المتقدم
 * يدعم التخزين الفوري، وتحديث الحالة التفاعلية، وجدولة المواعيد الإيمانية تلقائياً.
 */
object NotificationPreferencesManager {
    private const val PREFS_NAME = "qabas_notification_settings"

    private const val KEY_MASTER_ENABLED = "notif_master_enabled"
    private const val KEY_PRAYER_AZAN = "notif_prayer_azan_enabled"
    private const val KEY_SUHUR_REMINDER = "notif_suhur_enabled"
    private const val KEY_FAJR_REMINDER = "notif_fajr_enabled"
    private const val KEY_DAILY_HADITH = "notif_daily_hadith_enabled"
    private const val KEY_MORNING_ATHKAR = "notif_morning_athkar_enabled"
    private const val KEY_EVENING_ATHKAR = "notif_evening_athkar_enabled"
    private const val KEY_TREND_ALERTS = "notif_trend_alerts_enabled"
    private const val KEY_DEV_CHAT_ALERTS = "notif_dev_chat_enabled"
    private const val KEY_SOUND_ENABLED = "notif_sound_enabled"
    private const val KEY_VIBRATION_ENABLED = "notif_vibration_enabled"
    private const val KEY_SUHUR_HOUR = "notif_suhur_hour"
    private const val KEY_SUHUR_MINUTE = "notif_suhur_minute"
    private const val KEY_FAJR_HOUR = "notif_fajr_hour"
    private const val KEY_FAJR_MINUTE = "notif_fajr_minute"
    private const val KEY_WIRAD_STREAK = "notif_wirad_streak"
    private const val KEY_LAST_WIRAD_DATE = "notif_last_wirad_date"

    private val _masterEnabled = MutableStateFlow(true)
    val masterEnabled: StateFlow<Boolean> = _masterEnabled.asStateFlow()

    private val _prayerAzanEnabled = MutableStateFlow(true)
    val prayerAzanEnabled: StateFlow<Boolean> = _prayerAzanEnabled.asStateFlow()

    private val _suhurReminderEnabled = MutableStateFlow(true)
    val suhurReminderEnabled: StateFlow<Boolean> = _suhurReminderEnabled.asStateFlow()

    private val _fajrReminderEnabled = MutableStateFlow(true)
    val fajrReminderEnabled: StateFlow<Boolean> = _fajrReminderEnabled.asStateFlow()

    private val _dailyHadithEnabled = MutableStateFlow(true)
    val dailyHadithEnabled: StateFlow<Boolean> = _dailyHadithEnabled.asStateFlow()

    private val _morningAthkarEnabled = MutableStateFlow(true)
    val morningAthkarEnabled: StateFlow<Boolean> = _morningAthkarEnabled.asStateFlow()

    private val _eveningAthkarEnabled = MutableStateFlow(true)
    val eveningAthkarEnabled: StateFlow<Boolean> = _eveningAthkarEnabled.asStateFlow()

    private val _trendAlertsEnabled = MutableStateFlow(true)
    val trendAlertsEnabled: StateFlow<Boolean> = _trendAlertsEnabled.asStateFlow()

    private val _devChatAlertsEnabled = MutableStateFlow(true)
    val devChatAlertsEnabled: StateFlow<Boolean> = _devChatAlertsEnabled.asStateFlow()

    private val _soundEnabled = MutableStateFlow(true)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _vibrationEnabled = MutableStateFlow(true)
    val vibrationEnabled: StateFlow<Boolean> = _vibrationEnabled.asStateFlow()

    private val _suhurTime = MutableStateFlow(Pair(3, 30))
    val suhurTime: StateFlow<Pair<Int, Int>> = _suhurTime.asStateFlow()

    private val _fajrTime = MutableStateFlow(Pair(5, 0))
    val fajrTime: StateFlow<Pair<Int, Int>> = _fajrTime.asStateFlow()

    private val _wiradStreak = MutableStateFlow(0)
    val wiradStreak: StateFlow<Int> = _wiradStreak.asStateFlow()

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _masterEnabled.value = prefs.getBoolean(KEY_MASTER_ENABLED, true)
        _prayerAzanEnabled.value = prefs.getBoolean(KEY_PRAYER_AZAN, true)
        _suhurReminderEnabled.value = prefs.getBoolean(KEY_SUHUR_REMINDER, true)
        _fajrReminderEnabled.value = prefs.getBoolean(KEY_FAJR_REMINDER, true)
        _dailyHadithEnabled.value = prefs.getBoolean(KEY_DAILY_HADITH, true)
        _morningAthkarEnabled.value = prefs.getBoolean(KEY_MORNING_ATHKAR, true)
        _eveningAthkarEnabled.value = prefs.getBoolean(KEY_EVENING_ATHKAR, true)
        _trendAlertsEnabled.value = prefs.getBoolean(KEY_TREND_ALERTS, true)
        _devChatAlertsEnabled.value = prefs.getBoolean(KEY_DEV_CHAT_ALERTS, true)
        _soundEnabled.value = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        _vibrationEnabled.value = prefs.getBoolean(KEY_VIBRATION_ENABLED, true)

        val sH = prefs.getInt(KEY_SUHUR_HOUR, 3)
        val sM = prefs.getInt(KEY_SUHUR_MINUTE, 30)
        _suhurTime.value = Pair(sH, sM)

        val fH = prefs.getInt(KEY_FAJR_HOUR, 5)
        val fM = prefs.getInt(KEY_FAJR_MINUTE, 0)
        _fajrTime.value = Pair(fH, fM)

        _wiradStreak.value = prefs.getInt(KEY_WIRAD_STREAK, 1)

        isInitialized = true
    }

    private fun getPrefs(context: Context): SharedPreferences {
        init(context)
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun setMasterEnabled(context: Context, enabled: Boolean) {
        _masterEnabled.value = enabled
        getPrefs(context).edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply()
        if (enabled) {
            scheduleAllConfiguredAlarms(context)
        }
    }

    fun setPrayerAzanEnabled(context: Context, enabled: Boolean) {
        _prayerAzanEnabled.value = enabled
        getPrefs(context).edit().putBoolean(KEY_PRAYER_AZAN, enabled).apply()
    }

    fun setSuhurReminderEnabled(context: Context, enabled: Boolean) {
        _suhurReminderEnabled.value = enabled
        getPrefs(context).edit().putBoolean(KEY_SUHUR_REMINDER, enabled).apply()
        if (enabled) {
            SpiritualNotificationManager.scheduleNotification(context, "suhur")
        }
    }

    fun setFajrReminderEnabled(context: Context, enabled: Boolean) {
        _fajrReminderEnabled.value = enabled
        getPrefs(context).edit().putBoolean(KEY_FAJR_REMINDER, enabled).apply()
        if (enabled) {
            SpiritualNotificationManager.scheduleNotification(context, "fajr")
        }
    }

    fun setDailyHadithEnabled(context: Context, enabled: Boolean) {
        _dailyHadithEnabled.value = enabled
        getPrefs(context).edit().putBoolean(KEY_DAILY_HADITH, enabled).apply()
        AppNotificationService.setDailyHadithEnabled(context, enabled)
    }

    fun setMorningAthkarEnabled(context: Context, enabled: Boolean) {
        _morningAthkarEnabled.value = enabled
        getPrefs(context).edit().putBoolean(KEY_MORNING_ATHKAR, enabled).apply()
    }

    fun setEveningAthkarEnabled(context: Context, enabled: Boolean) {
        _eveningAthkarEnabled.value = enabled
        getPrefs(context).edit().putBoolean(KEY_EVENING_ATHKAR, enabled).apply()
    }

    fun setTrendAlertsEnabled(context: Context, enabled: Boolean) {
        _trendAlertsEnabled.value = enabled
        getPrefs(context).edit().putBoolean(KEY_TREND_ALERTS, enabled).apply()
    }

    fun setDevChatAlertsEnabled(context: Context, enabled: Boolean) {
        _devChatAlertsEnabled.value = enabled
        getPrefs(context).edit().putBoolean(KEY_DEV_CHAT_ALERTS, enabled).apply()
    }

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        _soundEnabled.value = enabled
        getPrefs(context).edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
    }

    fun setVibrationEnabled(context: Context, enabled: Boolean) {
        _vibrationEnabled.value = enabled
        getPrefs(context).edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
    }

    fun setSuhurTime(context: Context, hour: Int, minute: Int) {
        _suhurTime.value = Pair(hour, minute)
        getPrefs(context).edit()
            .putInt(KEY_SUHUR_HOUR, hour)
            .putInt(KEY_SUHUR_MINUTE, minute)
            .apply()
        SpiritualNotificationManager.scheduleNotification(context, "suhur")
    }

    fun setFajrTime(context: Context, hour: Int, minute: Int) {
        _fajrTime.value = Pair(hour, minute)
        getPrefs(context).edit()
            .putInt(KEY_FAJR_HOUR, hour)
            .putInt(KEY_FAJR_MINUTE, minute)
            .apply()
        SpiritualNotificationManager.scheduleNotification(context, "fajr")
    }

    fun recordWirdCompleted(context: Context) {
        val prefs = getPrefs(context)
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val lastDate = prefs.getString(KEY_LAST_WIRAD_DATE, "")
        val currentStreak = prefs.getInt(KEY_WIRAD_STREAK, 0)
        val newStreak = if (lastDate == today) currentStreak else currentStreak + 1
        _wiradStreak.value = newStreak
        prefs.edit()
            .putString(KEY_LAST_WIRAD_DATE, today)
            .putInt(KEY_WIRAD_STREAK, newStreak)
            .apply()
    }

    fun scheduleSnooze(context: Context, minutes: Int) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
            val intent = android.content.Intent(context, SpiritualNotificationReceiver::class.java).apply {
                putExtra("notification_type", "snooze")
            }
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                9999,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val triggerTime = System.currentTimeMillis() + minutes * 60 * 1000L
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun scheduleAllConfiguredAlarms(context: Context) {
        init(context)
        if (!_masterEnabled.value) return
        if (_suhurReminderEnabled.value) SpiritualNotificationManager.scheduleNotification(context, "suhur")
        if (_fajrReminderEnabled.value) SpiritualNotificationManager.scheduleNotification(context, "fajr")
        if (_dailyHadithEnabled.value) AppNotificationService.scheduleDailyHadithAlarm(context)
    }
}
