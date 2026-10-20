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

    /** مخزن تفضيلات الصلاة — مشترك مع شاشة المحراب و PrayerAlarmManager */
    private const val PRAYER_PREFS_NAME = "qabas_prayer_prefs"

    /**
     * أنماط الصوت المتاحة لصلاة واحدة.
     * القيم مطابقة تماماً لما يتعامل معه PrayerAzanReceiver — أي قيمة خارجها
     * تجعل الإشعار لا يُعرض ولا يُشغَّل الصوت (فشل صامت).
     */
    const val SOUND_FULL_AZAN = "FULL_AZAN"
    const val SOUND_TAKBEER_ONLY = "TAKBEER_ONLY"
    const val SOUND_NOTIFICATION_ONLY = "NOTIFICATION_ONLY"
    const val SOUND_SILENT = "SILENT"

    // مفاتيح التفضيلات الأساسية
    private const val KEY_MASTER_ENABLED = "notif_master_enabled"
    private const val KEY_PRAYER_AZAN = "notif_prayer_azan"
    private const val KEY_SUHUR_REMINDER = "notif_suhur_enabled"
    private const val KEY_FAJR_REMINDER = "notif_fajr_enabled"
    private const val KEY_DAILY_HADITH = "notif_daily_hadith_enabled"
    private const val KEY_MORNING_ATHKAR = "notif_morning_athkar_enabled"
    private const val KEY_EVENING_ATHKAR = "notif_evening_athkar_enabled"
    private const val KEY_TREND_ALERTS = "notif_trend_alerts_enabled"
    private const val KEY_DEV_CHAT_ALERTS = "notif_dev_chat_alerts_enabled"
    private const val KEY_SOUND_ENABLED = "notif_sound_enabled"
    private const val KEY_VIBRATION_ENABLED = "notif_vibration_enabled"
    
    // مفاتيح التوقيت
    private const val KEY_SUHUR_HOUR = "notif_suhur_hour"
    private const val KEY_SUHUR_MINUTE = "notif_suhur_minute"
    private const val KEY_FAJR_HOUR = "notif_fajr_hour"
    private const val KEY_FAJR_MINUTE = "notif_fajr_minute"
    
    // مفاتيح تتبع الورد
    private const val KEY_WIRAD_STREAK = "notif_wirad_streak"
    private const val KEY_LAST_WIRAD_DATE = "notif_last_wirad_date"
    
    // مفاتيح كل صلاة
    private const val KEY_PRAYER_FAJR = "notify_fajr"
    private const val KEY_PRAYER_SUNRISE = "notify_sunrise"
    private const val KEY_PRAYER_DHUHR = "notify_dhuhr"
    private const val KEY_PRAYER_ASR = "notify_asr"
    private const val KEY_PRAYER_MAGHRIB = "notify_maghrib"
    private const val KEY_PRAYER_ISHA = "notify_isha"
    private const val KEY_PRAYER_SUHUR = "notify_suhur"

    // مفاتيح إعداد الصوت لكل صلاة
    private const val KEY_SOUND_MODE_FAJR = "sound_mode_fajr"
    private const val KEY_SOUND_MODE_SUNRISE = "sound_mode_sunrise"
    private const val KEY_SOUND_MODE_DHUHR = "sound_mode_dhuhr"
    private const val KEY_SOUND_MODE_ASR = "sound_mode_asr"
    private const val KEY_SOUND_MODE_MAGHRIB = "sound_mode_maghrib"
    private const val KEY_SOUND_MODE_ISHA = "sound_mode_isha"
    private const val KEY_SOUND_MODE_SUHUR = "sound_mode_suhur"

    // مفاتيح معرف الصوت لكل صلاة
    private const val KEY_VOICE_ID_FAJR = "voice_id_fajr"
    private const val KEY_VOICE_ID_SUNRISE = "voice_id_sunrise"
    private const val KEY_VOICE_ID_DHUHR = "voice_id_dhuhr"
    private const val KEY_VOICE_ID_ASR = "voice_id_asr"
    private const val KEY_VOICE_ID_MAGHRIB = "voice_id_maghrib"
    private const val KEY_VOICE_ID_ISHA = "voice_id_isha"
    private const val KEY_VOICE_ID_SUHUR = "voice_id_suhur"

    // مفاتيح تفضيلات الصلاة العالمية
    private const val KEY_GLOBAL_PRAYER_ENABLED = "prayer_notifications_enabled"

    // متغيرات الحالة التفاعلية
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

    // متغيرات حالة تتبع الورد
    private val _wiradStreak = MutableStateFlow(0)
    val wiradStreak: StateFlow<Int> = _wiradStreak.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // تهيئة متغيرات الحالة من SharedPreferences
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
        
        _wiradStreak.value = prefs.getInt(KEY_WIRAD_STREAK, 0)
        
        // التسجيل على التغييرات لتحديث UI في الوقت الفعلي
        prefs.registerOnSharedPreferenceChangeListener { _, key ->
            when (key) {
                KEY_MASTER_ENABLED -> _masterEnabled.value = prefs.getBoolean(key, true)
                KEY_PRAYER_AZAN -> _prayerAzanEnabled.value = prefs.getBoolean(key, true)
                KEY_SUHUR_REMINDER -> _suhurReminderEnabled.value = prefs.getBoolean(key, true)
                KEY_FAJR_REMINDER -> _fajrReminderEnabled.value = prefs.getBoolean(key, true)
                KEY_DAILY_HADITH -> _dailyHadithEnabled.value = prefs.getBoolean(key, true)
                KEY_MORNING_ATHKAR -> _morningAthkarEnabled.value = prefs.getBoolean(key, true)
                KEY_EVENING_ATHKAR -> _eveningAthkarEnabled.value = prefs.getBoolean(key, true)
                KEY_TREND_ALERTS -> _trendAlertsEnabled.value = prefs.getBoolean(key, true)
                KEY_DEV_CHAT_ALERTS -> _devChatAlertsEnabled.value = prefs.getBoolean(key, true)
                KEY_SOUND_ENABLED -> _soundEnabled.value = prefs.getBoolean(key, true)
                KEY_VIBRATION_ENABLED -> _vibrationEnabled.value = prefs.getBoolean(key, true)
                KEY_WIRAD_STREAK -> _wiradStreak.value = prefs.getInt(key, 0)
            }
        }
    }

    fun setMasterEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply()
        _masterEnabled.value = enabled
    }

    fun setPrayerAzanEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PRAYER_AZAN, enabled).apply()
        _prayerAzanEnabled.value = enabled
    }

    fun setSuhurReminderEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SUHUR_REMINDER, enabled).apply()
        _suhurReminderEnabled.value = enabled
    }

    fun setFajrReminderEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FAJR_REMINDER, enabled).apply()
        _fajrReminderEnabled.value = enabled
    }

    fun setDailyHadithEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DAILY_HADITH, enabled).apply()
        _dailyHadithEnabled.value = enabled
    }

    fun setMorningAthkarEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_MORNING_ATHKAR, enabled).apply()
        _morningAthkarEnabled.value = enabled
    }

    fun setEveningAthkarEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_EVENING_ATHKAR, enabled).apply()
        _eveningAthkarEnabled.value = enabled
    }

    fun setTrendAlertsEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_TREND_ALERTS, enabled).apply()
        _trendAlertsEnabled.value = enabled
    }

    fun setDevChatAlertsEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DEV_CHAT_ALERTS, enabled).apply()
        _devChatAlertsEnabled.value = enabled
    }

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
        _soundEnabled.value = enabled
    }

    fun setVibrationEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
        _vibrationEnabled.value = enabled
    }

    fun setSuhurTime(context: Context, hour: Int, minute: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_SUHUR_HOUR, hour).putInt(KEY_SUHUR_MINUTE, minute).apply()
    }

    fun setFajrTime(context: Context, hour: Int, minute: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_FAJR_HOUR, hour).putInt(KEY_FAJR_MINUTE, minute).apply()
    }

    fun setWiradStreak(context: Context, streak: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_WIRAD_STREAK, streak).apply()
        _wiradStreak.value = streak
    }

    fun incrementWiradStreak(context: Context) {
        val newStreak = _wiradStreak.value + 1
        setWiradStreak(context, newStreak)
    }

    fun recordWirdCompleted(context: Context) {
        incrementWiradStreak(context)
        // تسجيل التاريخ لآخر ورد
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        prefs.edit().putString(KEY_LAST_WIRAD_DATE, dateFormat.format(Date())).apply()
    }

    /**
     * تفضيلات تنبيه صلاة واحدة.
     * ملاحظة مهمة: تُخزَّن في "qabas_prayer_prefs" — نفس المخزن الذي تستخدمه
     * شاشة المحراب و PrayerAlarmManager، حتى لا يوجد مصدران للحقيقة.
     */
    fun getPrayerPreferences(context: Context, prayerKey: String): Pair<Boolean, String> {
        val prefs = context.getSharedPreferences(PRAYER_PREFS_NAME, Context.MODE_PRIVATE)
        val prayerEnabled = prefs.getBoolean(getPrayerPrefKey(prayerKey), true)
        val soundMode = prefs.getString(getSoundModeKey(prayerKey), SOUND_FULL_AZAN) ?: SOUND_FULL_AZAN
        return Pair(prayerEnabled, soundMode)
    }

    fun getVoiceId(context: Context, prayerKey: String): String {
        val prefs = context.getSharedPreferences(PRAYER_PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(getVoiceIdKey(prayerKey), "") ?: ""
    }

    fun setPrayerPreferences(
        context: Context,
        prayerKey: String,
        enabled: Boolean,
        soundMode: String,
        voiceId: String
    ) {
        val prefs = context.getSharedPreferences(PRAYER_PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(getPrayerPrefKey(prayerKey), enabled)
            .putString(getSoundModeKey(prayerKey), soundMode)
            .putString(getVoiceIdKey(prayerKey), voiceId)
            .apply()
    }

    fun isGlobalPrayerEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PRAYER_PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_GLOBAL_PRAYER_ENABLED, true)
    }

    fun scheduleAllConfiguredAlarms(context: Context) {
        init(context)
        if (!_masterEnabled.value) return
        if (_suhurReminderEnabled.value) SpiritualNotificationManager.scheduleNotification(context, "suhur")
        if (_fajrReminderEnabled.value) SpiritualNotificationManager.scheduleNotification(context, "fajr")
        if (_dailyHadithEnabled.value) AppNotificationService.scheduleDailyHadithAlarm(context)
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

    private fun getPrayerPrefKey(prayerKey: String): String {
        return when (prayerKey) {
            "fajr" -> KEY_PRAYER_FAJR
            "sunrise" -> KEY_PRAYER_SUNRISE
            "dhuhr" -> KEY_PRAYER_DHUHR
            "asr" -> KEY_PRAYER_ASR
            "maghrib" -> KEY_PRAYER_MAGHRIB
            "isha" -> KEY_PRAYER_ISHA
            "suhur" -> KEY_PRAYER_SUHUR
            else -> prayerKey
        }
    }

    private fun getSoundModeKey(prayerKey: String): String {
        return when (prayerKey) {
            "fajr" -> KEY_SOUND_MODE_FAJR
            "sunrise" -> KEY_SOUND_MODE_SUNRISE
            "dhuhr" -> KEY_SOUND_MODE_DHUHR
            "asr" -> KEY_SOUND_MODE_ASR
            "maghrib" -> KEY_SOUND_MODE_MAGHRIB
            "isha" -> KEY_SOUND_MODE_ISHA
            "suhur" -> KEY_SOUND_MODE_SUHUR
            else -> "sound_mode_$prayerKey"
        }
    }

    private fun getVoiceIdKey(prayerKey: String): String {
        return when (prayerKey) {
            "fajr" -> KEY_VOICE_ID_FAJR
            "sunrise" -> KEY_VOICE_ID_SUNRISE
            "dhuhr" -> KEY_VOICE_ID_DHUHR
            "asr" -> KEY_VOICE_ID_ASR
            "maghrib" -> KEY_VOICE_ID_MAGHRIB
            "isha" -> KEY_VOICE_ID_ISHA
            "suhur" -> KEY_VOICE_ID_SUHUR
            else -> "voice_id_$prayerKey"
        }
    }
}
