package com.qabas.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 🛰️ NotificationPreferencesManager — مدير إعدادات وتفضيلات إشعارات خادم المسلم المتقدم
 * يدعم التخزين الفوري، وتحديث الحالة التفاعلية، وجدولة المواعيد الإيمانية تلقائياً.
 */
object NotificationPreferencesManager {
    private const val PREFS_NAME = "qabas_notification_settings"

    /** مخزن تفضيلات الصلاة — مشترك مع شاشة المحراب و PrayerAlarmManager */
    private const val PRAYER_PREFS_NAME = "qabas_prayer_prefs"

    // مفاتيح التفضيلات الأساسية
    private const val KEY_MASTER_ENABLED = "notif_master_enabled"
    private const val KEY_SUHUR_REMINDER = "notif_suhur_enabled"
    private const val KEY_FAJR_REMINDER = "notif_fajr_enabled"
    private const val KEY_DAILY_HADITH = "notif_daily_hadith_enabled"
    private const val KEY_MORNING_ATHKAR = "notif_morning_athkar_enabled"
    private const val KEY_EVENING_ATHKAR = "notif_evening_athkar_enabled"
    private const val KEY_TREND_ALERTS = "notif_trend_alerts_enabled"
    private const val KEY_SOUND_ENABLED = "notif_sound_enabled"
    private const val KEY_VIBRATION_ENABLED = "notif_vibration_enabled"

    // مفاتيح ساعات الهدوء (ق75): كتم التذكيرات الإيمانية ليلاً — الأذان لا يمسّه.
    private const val KEY_QUIET_ENABLED = "notif_quiet_enabled"
    private const val KEY_QUIET_START_HOUR = "notif_quiet_start_hour"
    private const val KEY_QUIET_START_MINUTE = "notif_quiet_start_minute"
    private const val KEY_QUIET_END_HOUR = "notif_quiet_end_hour"
    private const val KEY_QUIET_END_MINUTE = "notif_quiet_end_minute"
    
    // مفاتيح التوقيت
    private const val KEY_SUHUR_HOUR = "notif_suhur_hour"
    private const val KEY_SUHUR_MINUTE = "notif_suhur_minute"
    private const val KEY_FAJR_HOUR = "notif_fajr_hour"
    private const val KEY_FAJR_MINUTE = "notif_fajr_minute"
    private const val KEY_DAILY_HADITH_HOUR = "notif_daily_hadith_hour"
    private const val KEY_DAILY_HADITH_MINUTE = "notif_daily_hadith_minute"
    private const val KEY_MORNING_HOUR = "notif_morning_hour"
    private const val KEY_MORNING_MINUTE = "notif_morning_minute"
    private const val KEY_EVENING_HOUR = "notif_evening_hour"
    private const val KEY_EVENING_MINUTE = "notif_evening_minute"
    
    // مفاتيح تتبع الورد
    private const val KEY_WIRAD_STREAK = "notif_wirad_streak"
    private const val KEY_LAST_WIRAD_DATE = "notif_last_wirad_date"
    
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

    // review-r12: أُبقي مفتاح الترند — له مستهلِك حي: AppNotificationService
    // يمنع إشعارات isTrendAlert عند إيقافه، ويرسلها ContentGuard و
    // SocialAccountManager فعلاً. (أما مفتاح ردود المطور فحُذف: بلا مستهلِك.)
    private val _trendAlertsEnabled = MutableStateFlow(true)
    val trendAlertsEnabled: StateFlow<Boolean> = _trendAlertsEnabled.asStateFlow()

    private val _soundEnabled = MutableStateFlow(true)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _vibrationEnabled = MutableStateFlow(true)
    val vibrationEnabled: StateFlow<Boolean> = _vibrationEnabled.asStateFlow()

    // ساعات الهدوء: كتم التذكيرات الإيمانية في نافذة ليلية (الافتراضي
    // 23:00 حتى 5:00). الأذان ومواقيت الصلاة خارج نطاقها تماماً.
    private val _quietHoursEnabled = MutableStateFlow(false)
    val quietHoursEnabled: StateFlow<Boolean> = _quietHoursEnabled.asStateFlow()

    private val _quietStart = MutableStateFlow(Pair(23, 0))
    val quietStart: StateFlow<Pair<Int, Int>> = _quietStart.asStateFlow()

    private val _quietEnd = MutableStateFlow(Pair(5, 0))
    val quietEnd: StateFlow<Pair<Int, Int>> = _quietEnd.asStateFlow()

    // توقيت تنبيه السحر (ساعة، دقيقة)
    private val _suhurTime = MutableStateFlow(Pair(3, 30))
    val suhurTime: StateFlow<Pair<Int, Int>> = _suhurTime.asStateFlow()

    // توقيت تنبيه الفجر (ساعة، دقيقة)
    private val _fajrTime = MutableStateFlow(Pair(5, 0))
    val fajrTime: StateFlow<Pair<Int, Int>> = _fajrTime.asStateFlow()

    // توقيت الحديث اليومي (ساعة، دقيقة)
    private val _dailyHadithTime = MutableStateFlow(Pair(9, 0))
    val dailyHadithTime: StateFlow<Pair<Int, Int>> = _dailyHadithTime.asStateFlow()

    // توقيت أذكار الصباح والمساء
    private val _morningTime = MutableStateFlow(Pair(7, 0))
    val morningTime: StateFlow<Pair<Int, Int>> = _morningTime.asStateFlow()

    private val _eveningTime = MutableStateFlow(Pair(18, 0))
    val eveningTime: StateFlow<Pair<Int, Int>> = _eveningTime.asStateFlow()

    // متغيرات حالة تتبع الورد
    private val _wiradStreak = MutableStateFlow(0)
    val wiradStreak: StateFlow<Int> = _wiradStreak.asStateFlow()

    private var boundPrefs: SharedPreferences? = null
    private var boundPrayerPrefs: SharedPreferences? = null
    private var changeListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prayerPrefs = context.getSharedPreferences(PRAYER_PREFS_NAME, Context.MODE_PRIVATE)

        // تهيئة متغيرات الحالة من SharedPreferences
        _masterEnabled.value = prefs.getBoolean(KEY_MASTER_ENABLED, true)
        // المفتاح العام للصلاة يعيش في مخزن الصلاة (مصدر حقيقة واحد مع شاشة المحراب)
        _prayerAzanEnabled.value = prayerPrefs.getBoolean(KEY_GLOBAL_PRAYER_ENABLED, true)
        _suhurReminderEnabled.value = prefs.getBoolean(KEY_SUHUR_REMINDER, true)
        _fajrReminderEnabled.value = prefs.getBoolean(KEY_FAJR_REMINDER, true)
        _dailyHadithEnabled.value = prefs.getBoolean(KEY_DAILY_HADITH, true)
        _morningAthkarEnabled.value = prefs.getBoolean(KEY_MORNING_ATHKAR, true)
        _eveningAthkarEnabled.value = prefs.getBoolean(KEY_EVENING_ATHKAR, true)
        _trendAlertsEnabled.value = prefs.getBoolean(KEY_TREND_ALERTS, true)
        _soundEnabled.value = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        _vibrationEnabled.value = prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
        _quietHoursEnabled.value = prefs.getBoolean(KEY_QUIET_ENABLED, false)
        _quietStart.value = Pair(
            prefs.getInt(KEY_QUIET_START_HOUR, 23),
            prefs.getInt(KEY_QUIET_START_MINUTE, 0)
        )
        _quietEnd.value = Pair(
            prefs.getInt(KEY_QUIET_END_HOUR, 5),
            prefs.getInt(KEY_QUIET_END_MINUTE, 0)
        )

        _wiradStreak.value = prefs.getInt(KEY_WIRAD_STREAK, 0)
        // review-r12: سلسلة الورد كانت تُعرض كما هي حتى بعد انقطاع أيام — أي
        // رقم قديم يبقى معروضاً إلى أن يُسجَّل ورد جديد. إن كان آخر ورد مسجّل
        // ليس اليوم ولا أمس، فالسلسلة منقطعة فعلاً وتُصفَّر عند التحميل.
        if (_wiradStreak.value > 0) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val today = dateFormat.format(Date())
            val yesterday = dateFormat.format(Date(System.currentTimeMillis() - 24L * 60L * 60L * 1000L))
            val lastWiradDate = prefs.getString(KEY_LAST_WIRAD_DATE, null)
            if (lastWiradDate != null && lastWiradDate != today && lastWiradDate != yesterday) {
                _wiradStreak.value = 0
                prefs.edit().putInt(KEY_WIRAD_STREAK, 0).apply()
            }
        }

        // توقيت السحر والفجر
        _suhurTime.value = Pair(
            prefs.getInt(KEY_SUHUR_HOUR, 3),
            prefs.getInt(KEY_SUHUR_MINUTE, 30)
        )
        _fajrTime.value = Pair(
            prefs.getInt(KEY_FAJR_HOUR, 5),
            prefs.getInt(KEY_FAJR_MINUTE, 0)
        )
        _dailyHadithTime.value = Pair(
            prefs.getInt(KEY_DAILY_HADITH_HOUR, 9),
            prefs.getInt(KEY_DAILY_HADITH_MINUTE, 0)
        )
        _morningTime.value = Pair(
            prefs.getInt(KEY_MORNING_HOUR, 7),
            prefs.getInt(KEY_MORNING_MINUTE, 0)
        )
        _eveningTime.value = Pair(
            prefs.getInt(KEY_EVENING_HOUR, 18),
            prefs.getInt(KEY_EVENING_MINUTE, 0)
        )

        // التسجيل على التغييرات لتحديث UI في الوقت الفعلي (مرة واحدة فقط لكل مخزن)
        if (boundPrefs !== prefs) {
            changeListener?.let { listener ->
                boundPrefs?.unregisterOnSharedPreferenceChangeListener(listener)
                boundPrayerPrefs?.unregisterOnSharedPreferenceChangeListener(listener)
            }
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                when (key) {
                    KEY_MASTER_ENABLED -> _masterEnabled.value = prefs.getBoolean(key, true)
                    KEY_SUHUR_REMINDER -> _suhurReminderEnabled.value = prefs.getBoolean(key, true)
                    KEY_FAJR_REMINDER -> _fajrReminderEnabled.value = prefs.getBoolean(key, true)
                    KEY_DAILY_HADITH -> _dailyHadithEnabled.value = prefs.getBoolean(key, true)
                    KEY_MORNING_ATHKAR -> _morningAthkarEnabled.value = prefs.getBoolean(key, true)
                    KEY_EVENING_ATHKAR -> _eveningAthkarEnabled.value = prefs.getBoolean(key, true)
                    KEY_TREND_ALERTS -> _trendAlertsEnabled.value = prefs.getBoolean(key, true)
                    KEY_SOUND_ENABLED -> _soundEnabled.value = prefs.getBoolean(key, true)
                    KEY_VIBRATION_ENABLED -> _vibrationEnabled.value = prefs.getBoolean(key, true)
                    KEY_QUIET_ENABLED -> _quietHoursEnabled.value = prefs.getBoolean(key, false)
                    KEY_QUIET_START_HOUR, KEY_QUIET_START_MINUTE -> _quietStart.value = Pair(
                        prefs.getInt(KEY_QUIET_START_HOUR, 23),
                        prefs.getInt(KEY_QUIET_START_MINUTE, 0)
                    )
                    KEY_QUIET_END_HOUR, KEY_QUIET_END_MINUTE -> _quietEnd.value = Pair(
                        prefs.getInt(KEY_QUIET_END_HOUR, 5),
                        prefs.getInt(KEY_QUIET_END_MINUTE, 0)
                    )
                    KEY_WIRAD_STREAK -> _wiradStreak.value = prefs.getInt(key, 0)
                    KEY_SUHUR_HOUR, KEY_SUHUR_MINUTE -> _suhurTime.value = Pair(
                        prefs.getInt(KEY_SUHUR_HOUR, 3),
                        prefs.getInt(KEY_SUHUR_MINUTE, 30)
                    )
                    KEY_FAJR_HOUR, KEY_FAJR_MINUTE -> _fajrTime.value = Pair(
                        prefs.getInt(KEY_FAJR_HOUR, 5),
                        prefs.getInt(KEY_FAJR_MINUTE, 0)
                    )
                    KEY_DAILY_HADITH_HOUR, KEY_DAILY_HADITH_MINUTE -> _dailyHadithTime.value = Pair(
                        prefs.getInt(KEY_DAILY_HADITH_HOUR, 9),
                        prefs.getInt(KEY_DAILY_HADITH_MINUTE, 0)
                    )
                    KEY_MORNING_HOUR, KEY_MORNING_MINUTE -> _morningTime.value = Pair(
                        prefs.getInt(KEY_MORNING_HOUR, 7),
                        prefs.getInt(KEY_MORNING_MINUTE, 0)
                    )
                    KEY_EVENING_HOUR, KEY_EVENING_MINUTE -> _eveningTime.value = Pair(
                        prefs.getInt(KEY_EVENING_HOUR, 18),
                        prefs.getInt(KEY_EVENING_MINUTE, 0)
                    )
                    KEY_GLOBAL_PRAYER_ENABLED -> _prayerAzanEnabled.value =
                        prayerPrefs.getBoolean(key, true)
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            prayerPrefs.registerOnSharedPreferenceChangeListener(listener)
            changeListener = listener
            boundPrefs = prefs
            boundPrayerPrefs = prayerPrefs
        }

    }

    fun setMasterEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply()
        _masterEnabled.value = enabled
        if (enabled) scheduleAllConfiguredAlarms(context) else cancelAllConfiguredAlarms(context)
    }

    fun setPrayerAzanEnabled(context: Context, enabled: Boolean) {
        val prayerPrefs = context.getSharedPreferences(PRAYER_PREFS_NAME, Context.MODE_PRIVATE)
        prayerPrefs.edit().putBoolean(KEY_GLOBAL_PRAYER_ENABLED, enabled).apply()
        _prayerAzanEnabled.value = enabled
        if (enabled && _masterEnabled.value) {
            PrayerAlarmManager.scheduleAllPrayers(context)
        } else {
            PrayerAlarmManager.ALL_PRAYERS.forEach { PrayerAlarmManager.cancelPrayerAlarm(context, it) }
            runCatching { AzanCountdownService.stop(context) }
        }
    }

    fun setSuhurReminderEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SUHUR_REMINDER, enabled).apply()
        _suhurReminderEnabled.value = enabled
        if (enabled && _masterEnabled.value) {
            SpiritualNotificationManager.scheduleNotification(context, "suhur")
        } else {
            SpiritualNotificationManager.cancelNotification(context, "suhur")
        }
    }

    fun setFajrReminderEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FAJR_REMINDER, enabled).apply()
        _fajrReminderEnabled.value = enabled
        if (enabled && _masterEnabled.value) {
            SpiritualNotificationManager.scheduleNotification(context, "fajr")
        } else {
            SpiritualNotificationManager.cancelNotification(context, "fajr")
        }
    }

    fun setDailyHadithEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DAILY_HADITH, enabled).apply()
        _dailyHadithEnabled.value = enabled
        if (enabled && _masterEnabled.value) {
            AppNotificationService.scheduleDailyHadithAlarm(context)
        } else {
            AppNotificationService.cancelDailyHadithAlarm(context)
        }
    }

    fun setMorningAthkarEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_MORNING_ATHKAR, enabled).apply()
        _morningAthkarEnabled.value = enabled
        if (enabled && _masterEnabled.value) {
            SpiritualNotificationManager.scheduleNotification(context, "morning")
        } else {
            SpiritualNotificationManager.cancelNotification(context, "morning")
        }
    }

    fun setEveningAthkarEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_EVENING_ATHKAR, enabled).apply()
        _eveningAthkarEnabled.value = enabled
        if (enabled && _masterEnabled.value) {
            SpiritualNotificationManager.scheduleNotification(context, "evening")
        } else {
            SpiritualNotificationManager.cancelNotification(context, "evening")
        }
    }

    fun setTrendAlertsEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_TREND_ALERTS, enabled).apply()
        _trendAlertsEnabled.value = enabled
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

    fun setQuietHoursEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_QUIET_ENABLED, enabled).apply()
        _quietHoursEnabled.value = enabled
    }

    fun setQuietStart(context: Context, hour: Int, minute: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_QUIET_START_HOUR, hour).putInt(KEY_QUIET_START_MINUTE, minute).apply()
        _quietStart.value = Pair(hour, minute)
    }

    fun setQuietEnd(context: Context, hour: Int, minute: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_QUIET_END_HOUR, hour).putInt(KEY_QUIET_END_MINUTE, minute).apply()
        _quietEnd.value = Pair(hour, minute)
    }

    /**
     * هل الآن داخل ساعات الهدوء؟ نافذة ليلية عابرة لمنتصف الليل
     * (23:00–5:00) أو نهارية (13:00–15:00)؛ وبدء == انتهاء تعني
     * نافذة صفرية فلا هدوء. تُفحص لحظة الإطلاق لا لحظة الجدولة،
     * فتبديلها يسري على منبهات الغد دون إعادة جدولة.
     */
    fun isQuietNow(now: Calendar = Calendar.getInstance()): Boolean {
        if (!_quietHoursEnabled.value) return false
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val start = _quietStart.value.first * 60 + _quietStart.value.second
        val end = _quietEnd.value.first * 60 + _quietEnd.value.second
        if (start == end) return false
        return if (start < end) nowMinutes in start until end
        else nowMinutes >= start || nowMinutes < end
    }

    fun setSuhurTime(context: Context, hour: Int, minute: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_SUHUR_HOUR, hour).putInt(KEY_SUHUR_MINUTE, minute).apply()
        _suhurTime.value = Pair(hour, minute)
        if (_suhurReminderEnabled.value && _masterEnabled.value) {
            SpiritualNotificationManager.scheduleNotification(context, "suhur")
        }
    }

    fun setFajrTime(context: Context, hour: Int, minute: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_FAJR_HOUR, hour).putInt(KEY_FAJR_MINUTE, minute).apply()
        _fajrTime.value = Pair(hour, minute)
        if (_fajrReminderEnabled.value && _masterEnabled.value) {
            SpiritualNotificationManager.scheduleNotification(context, "fajr")
        }
    }

    fun setDailyHadithTime(context: Context, hour: Int, minute: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_DAILY_HADITH_HOUR, hour)
            .putInt(KEY_DAILY_HADITH_MINUTE, minute)
            .apply()
        _dailyHadithTime.value = Pair(hour, minute)
        if (_dailyHadithEnabled.value && _masterEnabled.value) {
            AppNotificationService.scheduleDailyHadithAlarm(context)
        }
    }

    fun setMorningTime(context: Context, hour: Int, minute: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_MORNING_HOUR, hour).putInt(KEY_MORNING_MINUTE, minute).apply()
        _morningTime.value = Pair(hour, minute)
        if (_morningAthkarEnabled.value && _masterEnabled.value) {
            SpiritualNotificationManager.scheduleNotification(context, "morning")
        }
    }

    fun setEveningTime(context: Context, hour: Int, minute: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_EVENING_HOUR, hour).putInt(KEY_EVENING_MINUTE, minute).apply()
        _eveningTime.value = Pair(hour, minute)
        if (_eveningAthkarEnabled.value && _masterEnabled.value) {
            SpiritualNotificationManager.scheduleNotification(context, "evening")
        }
    }

    fun setWiradStreak(context: Context, streak: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_WIRAD_STREAK, streak).apply()
        _wiradStreak.value = streak
    }

    /**
     * review-r12: كان كل ضغط على «تم إنجاز الورد» في أي إشعار يزيد السلسلة
     * (+1) بلا أي حارس — فالضغط 5 مرات في يوم واحد يصنع «5 أيام»، وانقطاع
     * أيام لا يُصفّرها لأن KEY_LAST_WIRAD_DATE كان يُكتب ولا يُقرأ أبداً.
     * الآن: مرة واحدة في اليوم؛ أمس → +1، وغير ذلك (انقطاع) → 1 من جديد.
     */
    fun recordWirdCompleted(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = dateFormat.format(Date())
        val lastDate = prefs.getString(KEY_LAST_WIRAD_DATE, null)
        if (lastDate == today) return // ورد اليوم مسجَّل من قبل — لا تضخيم
        val yesterday = dateFormat.format(Date(System.currentTimeMillis() - 24L * 60L * 60L * 1000L))
        // ق121: السلسلة الحالية تُقرأ من التفضيلات لا من تدفق الذاكرة —
        // التدفق يُزامَن في init() وحده، وQabasApplication لا يستدعيه،
        // فمستقبِل إشعار يعمل في عملية باردة (بلا MainActivity) كان
        // يرى 0 فيحوّل سلسلة 30 يوماً متصلة إلى 1 بصمت عند أول تسجيل.
        val currentStreak = prefs.getInt(KEY_WIRAD_STREAK, 0)
        val newStreak = if (lastDate == yesterday) currentStreak + 1 else 1
        setWiradStreak(context, newStreak)
        prefs.edit().putString(KEY_LAST_WIRAD_DATE, today).apply()
    }

    fun isGlobalPrayerEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PRAYER_PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_GLOBAL_PRAYER_ENABLED, true)
    }

    fun scheduleAllConfiguredAlarms(context: Context) {
        init(context)
        if (!_masterEnabled.value) {
            cancelAllConfiguredAlarms(context)
            return
        }
        if (_prayerAzanEnabled.value) {
            PrayerAlarmManager.scheduleAllPrayers(context)
        }
        if (_suhurReminderEnabled.value) {
            SpiritualNotificationManager.scheduleNotification(context, "suhur")
        } else {
            SpiritualNotificationManager.cancelNotification(context, "suhur")
        }
        if (_fajrReminderEnabled.value) {
            SpiritualNotificationManager.scheduleNotification(context, "fajr")
        } else {
            SpiritualNotificationManager.cancelNotification(context, "fajr")
        }
        if (_morningAthkarEnabled.value) {
            SpiritualNotificationManager.scheduleNotification(context, "morning")
        } else {
            SpiritualNotificationManager.cancelNotification(context, "morning")
        }
        if (_eveningAthkarEnabled.value) {
            SpiritualNotificationManager.scheduleNotification(context, "evening")
        } else {
            SpiritualNotificationManager.cancelNotification(context, "evening")
        }
        if (_dailyHadithEnabled.value) {
            AppNotificationService.scheduleDailyHadithAlarm(context)
        } else {
            AppNotificationService.cancelDailyHadithAlarm(context)
        }
        // (ر22) تنظيف أشباح التأجيل: نسخ قديمة كانت تعيد جدولة «snooze»
        // يومياً 7:00 — الإلغاء بـ FLAG_NO_CREATE فلا أثر له إن لم يوجد.
        SpiritualNotificationManager.cancelNotification(context, "snooze")
    }

    private fun cancelAllConfiguredAlarms(context: Context) {
        PrayerAlarmManager.ALL_PRAYERS.forEach { PrayerAlarmManager.cancelPrayerAlarm(context, it) }
        AppNotificationService.cancelDailyHadithAlarm(context)
        SpiritualNotificationManager.cancelNotification(context, "suhur")
        SpiritualNotificationManager.cancelNotification(context, "fajr")
        SpiritualNotificationManager.cancelNotification(context, "morning")
        SpiritualNotificationManager.cancelNotification(context, "evening")
        SpiritualNotificationManager.cancelNotification(context, "snooze")
        runCatching { AzanCountdownService.stop(context) }
    }

    /**
     * تأجيل لمرة واحدة: منبه واحد بعد [minutes] دقيقة يعرض [title]/[message]
     * كما هما، بلا إعادة جدولة يومية (ر22).
     */
    fun scheduleSnooze(context: Context, minutes: Int, title: String = "", message: String = "") {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
            val intent = android.content.Intent(context, SpiritualNotificationReceiver::class.java).apply {
                putExtra("notification_type", "snooze")
                putExtra("snooze_title", title)
                putExtra("snooze_message", message)
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

}
