package com.qabas.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import com.qabas.app.ui.theme.AppThemePreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.roundToInt

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
            ACTION_STOP -> {
                stopTicking()
                stopSelf()
                return START_NOT_STICKY
            }
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
            var lastProgressUpdateMinute = Long.MIN_VALUE
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
                    lastProgressUpdateMinute = Long.MIN_VALUE
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
                    lastProgressUpdateMinute = Long.MIN_VALUE
                    delay(5_000)
                    continue
                }

                // يعرض النظام الثواني عبر Chronometer؛ نحدّث الإشعار مرة كل دقيقة
                // لتجديد شريط التقدم دون إعادة بنائه كل ثانية.
                val currentMinute = System.currentTimeMillis() / 60_000L
                if (
                    lastPrayerName != next.prayerName ||
                    lastTriggerMillis != next.triggerMillis ||
                    lastProgressUpdateMinute != currentMinute
                ) {
                    updateCountdownNotif(next)
                    lastPrayerName = next.prayerName
                    lastTriggerMillis = next.triggerMillis
                    lastProgressUpdateMinute = currentMinute
                }
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

    private fun updateCountdownNotif(nextPrayer: NextPrayer) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            NOTIF_ID,
            buildNotif(
                title = "العداد اللحظي • ${nextPrayer.prayerName}",
                text = "المتبقي حتى الأذان",
                countdownTargetMillis = nextPrayer.triggerMillis,
                nextPrayer = nextPrayer
            )
        )
    }

    private fun buildNotif(
        title: String,
        text: String,
        countdownTargetMillis: Long? = null,
        nextPrayer: NextPrayer? = null
    ): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "prayer_times")
        }
        val openPI = PendingIntent.getActivity(
            this,
            8861,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPI = PendingIntent.getService(
            this,
            8862,
            Intent(this, AzanCountdownService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_qabas)
            .setColor(QabasNotificationHelper.BRAND_COLOR)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openPI)
            .setShowWhen(false)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إخفاء العدّاد", stopPI)

        if (countdownTargetMillis != null && nextPrayer != null) {
            builder
                .setWhen(countdownTargetMillis)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(createCountdownRemoteViews(R.layout.notification_azan_countdown_compact, nextPrayer, expanded = false))
                .setCustomBigContentView(createCountdownRemoteViews(R.layout.notification_azan_countdown_expanded, nextPrayer, expanded = true))
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }

        return builder.build()
    }

    private fun createCountdownRemoteViews(
        layoutResId: Int,
        nextPrayer: NextPrayer,
        expanded: Boolean
    ): RemoteViews {
        val views = RemoteViews(packageName, layoutResId)
        val accentColor = activeThemeAccentColor()
        val chronometerId = R.id.azan_countdown_chronometer
        val remainingMillis = (nextPrayer.triggerMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val elapsedRealtimeTarget = SystemClock.elapsedRealtime() + remainingMillis

        views.setTextViewText(
            R.id.azan_countdown_title,
            if (expanded) "العداد اللحظي • ${nextPrayer.prayerName}" else nextPrayer.prayerName
        )
        views.setTextColor(R.id.azan_countdown_title, accentColor)
        views.setTextViewText(R.id.azan_countdown_live, "مباشر")
        views.setTextViewText(R.id.azan_countdown_label, "المتبقي حتى الأذان")
        views.setTextViewText(chronometerId, "--:--:--")
        views.setTextColor(chronometerId, android.graphics.Color.WHITE)
        views.setChronometer(chronometerId, elapsedRealtimeTarget, "%s", true)
        views.setChronometerCountDown(chronometerId, true)
        views.setContentDescription(
            chronometerId,
            "${nextPrayer.prayerName}، المتبقي حتى الأذان"
        )

        if (expanded) {
            views.setTextViewText(
                R.id.azan_countdown_detail,
                "${nextPrayer.prayerTimeFormatted} • 📍 ${nextPrayer.cityName}"
            )
            views.setProgressBar(
                R.id.azan_countdown_progress,
                1_000,
                (nextPrayer.progressToNextPrayer * 1_000).roundToInt().coerceIn(0, 1_000),
                false
            )
        }

        return views
    }

    private fun activeThemeAccentColor(): Int {
        val savedPresetId = getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .getString("app_theme_preset", AppThemePreset.CYBER_HUD_BLUE.id)
        val preset = AppThemePreset.entries.firstOrNull { it.id == savedPresetId }
            ?: AppThemePreset.CYBER_HUD_BLUE
        return preset.primaryColor.toArgb()
    }

    private fun createChannel() {
        QabasNotificationHelper.ensureChannel(this, QabasNotificationHelper.CHANNEL_AZAN_COUNTDOWN)
    }

    private data class NextPrayer(
        val prayerName: String,
        val triggerMillis: Long,
        val prayerTimeFormatted: String,
        val cityName: String,
        val progressToNextPrayer: Float
    )

    private data class PrayerDefinition(
        val key: String,
        val name: String,
        val timeFor: (CalculatedPrayerTimes) -> String
    )

    private data class PrayerEvent(
        val prayerName: String,
        val prayerTimeFormatted: String,
        val triggerMillis: Long
    )

    private fun findNextPrayer(context: Context): NextPrayer? {
        return try {
            val prefs = context.getSharedPreferences("qabas_prayer_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("prayer_notifications_enabled", true)) return null

            val city = PrayerCalculationHelper.getSavedCity(context)
            val nowMillis = System.currentTimeMillis()
            val cityTimeZone = PrayerCalculationHelper.timeZoneFor(city)
            val now = Calendar.getInstance(cityTimeZone).apply { timeInMillis = nowMillis }

            val yesterdayCalendar = (now.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -1)
            }
            val tomorrowCalendar = (now.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, 1)
            }
            val yesterday = PrayerCalculationHelper.calculatePrayerTimes(yesterdayCalendar, city, city.defaultMethod)
            val today = PrayerCalculationHelper.calculatePrayerTimes(now, city, city.defaultMethod)
            val tomorrow = PrayerCalculationHelper.calculatePrayerTimes(tomorrowCalendar, city, city.defaultMethod)

            val prayerDefinitions = listOf(
                PrayerDefinition(PrayerAlarmManager.PRAYER_FAJR, "صلاة الفجر") { it.fajr },
                PrayerDefinition(PrayerAlarmManager.PRAYER_SUNRISE, "الشروق") { it.sunrise },
                PrayerDefinition(PrayerAlarmManager.PRAYER_DHUHR, "صلاة الظهر") { it.dhuhr },
                PrayerDefinition(PrayerAlarmManager.PRAYER_ASR, "صلاة العصر") { it.asr },
                PrayerDefinition(PrayerAlarmManager.PRAYER_MAGHRIB, "صلاة المغرب") { it.maghrib },
                PrayerDefinition(PrayerAlarmManager.PRAYER_ISHA, "صلاة العشاء") { it.isha }
            )
            val prayerDays = listOf(-1 to yesterday, 0 to today, 1 to tomorrow)
            val events = buildList {
                for (definition in prayerDefinitions) {
                    if (!prefs.getBoolean("notify_${definition.key}", true)) continue
                    for ((dayOffset, times) in prayerDays) {
                        val timeText = definition.timeFor(times)
                        val triggerMillis = runCatching {
                            PrayerCalculationHelper.calendarForPrayerTime(
                                timeText,
                                nowMillis,
                                city,
                                dayOffset = dayOffset
                            ).timeInMillis
                        }.getOrNull() ?: continue
                        add(PrayerEvent(definition.name, timeText, triggerMillis))
                    }
                }
            }

            val nextEvent = events.asSequence()
                .filter { it.triggerMillis > nowMillis }
                .minByOrNull { it.triggerMillis }
                ?: return null
            val previousEventMillis = events.asSequence()
                .filter { it.triggerMillis <= nowMillis }
                .maxOfOrNull { it.triggerMillis }
            val progress = previousEventMillis?.let { previousMillis ->
                val intervalMillis = (nextEvent.triggerMillis - previousMillis)
                    .coerceAtLeast(30 * 60 * 1000L)
                ((nowMillis - previousMillis).toFloat() / intervalMillis.toFloat())
                    .coerceIn(0f, 1f)
            } ?: 0f

            NextPrayer(
                prayerName = nextEvent.prayerName,
                triggerMillis = nextEvent.triggerMillis,
                prayerTimeFormatted = nextEvent.prayerTimeFormatted,
                cityName = city.nameAr,
                progressToNextPrayer = progress
            )
        } catch (_: Exception) {
            null
        }
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
