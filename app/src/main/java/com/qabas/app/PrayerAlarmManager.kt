package com.qabas.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

/**
 * مدير جدولة وتفعيل تنبيهات الأذان ومواقيت الصلاة الحقيقية (Real Exact Alarms & Azan Scheduler)
 */
object PrayerAlarmManager {

    private const val TAG = "PrayerAlarmManager"

    const val PRAYER_FAJR = "fajr"
    const val PRAYER_SUNRISE = "sunrise"
    const val PRAYER_DHUHR = "dhuhr"
    const val PRAYER_ASR = "asr"
    const val PRAYER_MAGHRIB = "maghrib"
    const val PRAYER_ISHA = "isha"
    const val PRAYER_SUHUR = "suhur"

    val ALL_PRAYERS = listOf(
        PRAYER_FAJR,
        PRAYER_SUNRISE,
        PRAYER_DHUHR,
        PRAYER_ASR,
        PRAYER_MAGHRIB,
        PRAYER_ISHA,
        PRAYER_SUHUR
    )

    private fun getRequestCode(prayerKey: String, isPreReminder: Boolean = false): Int {
        val base = when (prayerKey) {
            PRAYER_FAJR -> 2001
            PRAYER_SUNRISE -> 2002
            PRAYER_DHUHR -> 2003
            PRAYER_ASR -> 2004
            PRAYER_MAGHRIB -> 2005
            PRAYER_ISHA -> 2006
            PRAYER_SUHUR -> 2007
            else -> 2000
        }
        return if (isPreReminder) base + 500 else base
    }

    /**
     * جدولة جميع الصلوات الخمس والتنبيهات لليوم والغد بناءً على المدينة المحفوظة
     */
    fun scheduleAllPrayers(context: Context) {
        // قراءة الإعدادات من NotificationPreferencesManager لضمان التكوين الموحد
        NotificationPreferencesManager.init(context)
        
        if (!NotificationPreferencesManager.masterEnabled.value) {
            Log.d(TAG, "Prayer notifications are globally disabled in NotificationPreferencesManager.")
            return
        }

        // التحقق مما إذا كانت صلوات الأذان العامة مفعلة (وليس فقط التنبيهات)
        val globalPrayerEnabled = NotificationPreferencesManager.isGlobalPrayerEnabled(context)
        if (!globalPrayerEnabled) {
            Log.d(TAG, "Global prayer notifications are disabled.")
            return
        }

        val city = PrayerCalculationHelper.getSavedCity(context)
        val cityTimeZone = PrayerCalculationHelper.timeZoneFor(city)
        val now = Calendar.getInstance(cityTimeZone)
        val todayTimes = PrayerCalculationHelper.calculatePrayerTimes(now, city, city.defaultMethod)

        val tomorrowCal = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tomorrowTimes = PrayerCalculationHelper.calculatePrayerTimes(tomorrowCal, city, city.defaultMethod)

        // جدول كل الصلوات — التحقق من تمكين كل صلاة يتم داخل scheduleSinglePrayer
        // (مصدر الحقيقة الوحيد: qabas_prayer_prefs / notify_<key>) مع إلغاء المنبه المعطّل.
        scheduleSinglePrayer(context, PRAYER_FAJR, todayTimes.fajr, tomorrowTimes.fajr, "صلاة الفجر", city)
        scheduleSinglePrayer(context, PRAYER_SUNRISE, todayTimes.sunrise, tomorrowTimes.sunrise, "شروق الشمس", city)
        scheduleSinglePrayer(context, PRAYER_DHUHR, todayTimes.dhuhr, tomorrowTimes.dhuhr, "صلاة الظهر", city)
        scheduleSinglePrayer(context, PRAYER_ASR, todayTimes.asr, tomorrowTimes.asr, "صلاة العصر", city)
        scheduleSinglePrayer(context, PRAYER_MAGHRIB, todayTimes.maghrib, tomorrowTimes.maghrib, "صلاة المغرب", city)
        scheduleSinglePrayer(context, PRAYER_ISHA, todayTimes.isha, tomorrowTimes.isha, "صلاة العشاء", city)
        scheduleSinglePrayer(
            context,
            PRAYER_SUHUR,
            todayTimes.qiyamStart,
            tomorrowTimes.qiyamStart,
            "قيام الليل والسحر",
            city,
            isOvernight = true
        )

        // شغّل عدّاد الأذان الثابت بعد كل جدولة ناجحة
        try { AzanCountdownService.start(context) } catch (_: Exception) {}

        Log.d(TAG, "Scheduled all real prayer alarms successfully for city: ${city.nameAr}")
    }

    /**
     * هل تنبيه هذه الصلاة مفعّل؟ (مصدر الحقيقة: qabas_prayer_prefs)
     */
    fun isPrayerEnabled(context: Context, prayerKey: String): Boolean {
        val prefs = context.getSharedPreferences("qabas_prayer_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("notify_$prayerKey", true)
    }

    private fun scheduleSinglePrayer(
        context: Context,
        prayerKey: String,
        todayTimeStr: String,
        tomorrowTimeStr: String,
        prayerArabicName: String,
        city: CityLocation,
        isOvernight: Boolean = false
    ) {
        val prayerPrefs = context.getSharedPreferences("qabas_prayer_prefs", Context.MODE_PRIVATE)
        val isPrayerEnabled = prayerPrefs.getBoolean("notify_$prayerKey", true)
        if (!isPrayerEnabled) {
            cancelPrayerAlarm(context, prayerKey)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val nowMillis = System.currentTimeMillis()

        val triggerMillis = runCatching {
            if (isOvernight) {
                PrayerCalculationHelper.calendarForPrayerTime(todayTimeStr, nowMillis, city, dayOffset = 1)
                    .timeInMillis
            } else {
                val targetCal = PrayerCalculationHelper.calendarForPrayerTime(todayTimeStr, nowMillis, city)
                if (targetCal.timeInMillis > nowMillis) {
                    targetCal.timeInMillis
                } else {
                    PrayerCalculationHelper.calendarForPrayerTime(
                        tomorrowTimeStr,
                        nowMillis,
                        city,
                        dayOffset = 1
                    ).timeInMillis
                }
            }
        }.onFailure {
            Log.e(TAG, "Could not resolve $prayerKey prayer time in ${city.nameAr}", it)
            cancelPrayerAlarm(context, prayerKey)
        }.getOrNull() ?: return

        val soundMode = prayerPrefs.getString("sound_mode_$prayerKey", "FULL_AZAN") ?: "FULL_AZAN"
        val defaultVoice = MuezzinVoiceManager.getDefaultVoiceId(context)
        val voiceId = prayerPrefs.getString("voice_id_$prayerKey", defaultVoice) ?: defaultVoice

        val intent = Intent(context, PrayerAzanReceiver::class.java).apply {
            action = "com.qabas.app.ACTION_PRAYER_ALARM"
            putExtra("prayer_key", prayerKey)
            putExtra("prayer_name", prayerArabicName)
            putExtra("sound_mode", soundMode)
            putExtra("voice_id", voiceId)
            putExtra("trigger_time_millis", triggerMillis)
        }

        val requestCode = getRequestCode(prayerKey, isPreReminder = false)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 🔑 showIntent يجب أن يكون Activity (يفتح عند النقر على أيقونة الساعة في شريط الحالة)
        val showActivityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("navigate_to", "prayer_times")
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            requestCode + 10000, // معرّف مختلف عن PendingIntent البث لتجنب التصادم
            showActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    // 🔑 أقوى مستوى: setAlarmClock - يظهر كأيقونة ساعة في شريط الحالة ولا يتأثر بـ Doze
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(triggerMillis, showPendingIntent),
                        pendingIntent
                    )
                } else {
                    Log.w(TAG, "Exact alarm permission NOT granted for $prayerKey. Falling back to inexact.")
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                }
            } else {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerMillis, showPendingIntent),
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm permission missing: ${e.message}, falling back to setAndAllowWhileIdle")
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm for $prayerKey: ${e.message}")
        }
    }

    fun cancelPrayerAlarm(context: Context, prayerKey: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, PrayerAzanReceiver::class.java).apply {
            action = "com.qabas.app.ACTION_PRAYER_ALARM"
        }
        val requestCode = getRequestCode(prayerKey, isPreReminder = false)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }

}
