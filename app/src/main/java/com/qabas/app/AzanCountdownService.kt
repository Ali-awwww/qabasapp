package com.qabas.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * إشعار العدّاد التنازلي للأذان: يعرض الوقت المتبقي للصلاة القادمة ويُحدّث كل ثانية.
 * الأذان نفسه يتولّاه المنبه الدقيق (PrayerAzanReceiver) لتفادي الازدواج.
 * يعمل كـ ForegroundService ليصمد أمام Doze وإدارة البطارية.
 */
class AzanCountdownService : Service() {

    companion object {
        const val CHANNEL_ID = "qabas_azan_countdown_channel"
        const val NOTIF_ID = 8855
        const val ACTION_START = "com.qabas.app.COUNTDOWN_START"
        const val ACTION_STOP = "com.qabas.app.COUNTDOWN_STOP"

        fun start(context: Context) {
            val i = Intent(context, AzanCountdownService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AzanCountdownService::class.java).apply { action = ACTION_STOP })
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopTicking(); stopSelf(); return START_NOT_STICKY }
            else -> startTicking()
        }
        return START_STICKY
    }

    private fun startTicking() {
        createChannel()
        startForeground(NOTIF_ID, buildNotif("مواقيت الصلاة", "جارٍ حساب الصلاة القادمة…"))
        job?.cancel()
        job = scope.launch {
            var lastPrayerName: String? = null
            var lastTriggerMillis: Long? = null
            var showingNoPrayer = false
            while (true) {
                val next = findNextPrayer(applicationContext)
                if (next == null) {
                    if (!showingNoPrayer) {
                        updateNotif("مواقيت الصلاة", "فعّل مواقيت الصلاة من الإعدادات")
                        showingNoPrayer = true
                    }
                    lastPrayerName = null
                    lastTriggerMillis = null
                    delay(60_000)
                    continue
                }
                showingNoPrayer = false
                val remaining = next.triggerMillis - System.currentTimeMillis()
                if (remaining <= 0) {
                    // لا نشغّل الأذان هنا لتجنّب الازدواج مع المنبه الدقيق.
                    PrayerAlarmManager.scheduleAllPrayers(applicationContext)
                    lastPrayerName = null
                    lastTriggerMillis = null
                    delay(5_000)
                    continue
                }

                // أندرويد يتولى تحديث الثواني عبر Chronometer؛ لا نعيد بناء الإشعار كل ثانية.
                if (lastPrayerName != next.prayerName || lastTriggerMillis != next.triggerMillis) {
                    updateCountdownNotif("المتبقي على ${next.prayerName}", next.triggerMillis)
                    lastPrayerName = next.prayerName
                    lastTriggerMillis = next.triggerMillis
                }
                // نعيد حساب الصلاة القادمة دورياً فقط لمواكبة تغيّر الإعدادات أو الوقت.
                delay(60_000)
            }
        }
    }

    private fun stopTicking() {
        job?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun updateNotif(title: String, text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotif(title, text))
    }

    private fun updateCountdownNotif(title: String, targetMillis: Long) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotif(title, "الوقت المتبقي للصلاة", targetMillis))
    }

    private fun buildNotif(
        title: String,
        text: String,
        countdownTargetMillis: Long? = null
    ): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "prayer_times")
        }
        val openPI = PendingIntent.getActivity(this, 8861, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopPI = PendingIntent.getService(
            this, 8862,
            Intent(this, AzanCountdownService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_qabas)
            .setColor(QabasNotificationHelper.BRAND_COLOR)
            .setContentTitle(title)
            .setContentText(text)
            // يحدّث النظام العدّ التنازلي كل ثانية دون إعادة إرسال الإشعار من الخدمة.
            .setShowWhen(countdownTargetMillis != null)
            .apply {
                if (countdownTargetMillis != null) {
                    setWhen(countdownTargetMillis)
                    setUsesChronometer(true)
                    setChronometerCountDown(true)
                }
            }
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openPI)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إخفاء العدّاد", stopPI)
            .build()
    }

    private fun createChannel() {
        QabasNotificationHelper.ensureChannel(this, QabasNotificationHelper.CHANNEL_AZAN_COUNTDOWN)
    }

    private data class NextPrayer(val prayerName: String, val triggerMillis: Long, val voiceId: String)

    private fun findNextPrayer(context: Context): NextPrayer? {
        return try {
            val prefs = context.getSharedPreferences("qabas_prayer_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("prayer_notifications_enabled", true)) return null
            val city = PrayerCalculationHelper.getSavedCity(context)
            val nowMillis = System.currentTimeMillis()
            val now = Calendar.getInstance(PrayerCalculationHelper.timeZoneFor(city)).apply {
                timeInMillis = nowMillis
            }
            val today = PrayerCalculationHelper.calculatePrayerTimes(now, city, city.defaultMethod)
            val tomorrowCalendar = (now.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, 1)
            }
            val tomorrow = PrayerCalculationHelper.calculatePrayerTimes(tomorrowCalendar, city, city.defaultMethod)
            val pairs = listOf(
                Triple(PrayerAlarmManager.PRAYER_FAJR, "صلاة الفجر", today.fajr to tomorrow.fajr),
                Triple(PrayerAlarmManager.PRAYER_SUNRISE, "الشروق", today.sunrise to tomorrow.sunrise),
                Triple(PrayerAlarmManager.PRAYER_DHUHR, "صلاة الظهر", today.dhuhr to tomorrow.dhuhr),
                Triple(PrayerAlarmManager.PRAYER_ASR, "صلاة العصر", today.asr to tomorrow.asr),
                Triple(PrayerAlarmManager.PRAYER_MAGHRIB, "صلاة المغرب", today.maghrib to tomorrow.maghrib),
                Triple(PrayerAlarmManager.PRAYER_ISHA, "صلاة العشاء", today.isha to tomorrow.isha)
            )
            val defaultVoice = MuezzinVoiceManager.getDefaultVoiceId(context)
            var best: NextPrayer? = null
            for ((key, prayerName, times) in pairs) {
                if (!prefs.getBoolean("notify_$key", true)) continue
                val todayCal = runCatching {
                    PrayerCalculationHelper.calendarForPrayerTime(times.first, nowMillis, city)
                }.getOrNull() ?: continue
                val triggerMillis = if (todayCal.timeInMillis > nowMillis) {
                    todayCal.timeInMillis
                } else {
                    runCatching {
                        PrayerCalculationHelper.calendarForPrayerTime(times.second, nowMillis, city, dayOffset = 1)
                            .timeInMillis
                    }.getOrNull() ?: continue
                }
                val voice = prefs.getString("voice_id_$key", defaultVoice) ?: defaultVoice
                val candidate = NextPrayer(prayerName, triggerMillis, voice)
                if (best == null || candidate.triggerMillis < best.triggerMillis) best = candidate
            }
            best
        } catch (_: Exception) { null }
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
