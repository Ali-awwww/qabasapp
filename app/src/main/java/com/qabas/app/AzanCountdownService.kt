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
import java.util.concurrent.TimeUnit

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
            while (true) {
                val next = findNextPrayer(applicationContext)
                if (next == null) {
                    updateNotif("مواقيت الصلاة", "فعّل مواقيت الصلاة من الإعدادات")
                    delay(60_000)
                    continue
                }
                val remaining = next.triggerMillis - System.currentTimeMillis()
                if (remaining <= 0) {
                    // 🔑 تجاوزنا الموعد: لا نشغّل الأذان هنا لتجنّب الازدواج مع المنبه الدقيق
                    // (PrayerAlarmManager/PrayerAzanReceiver هو المسؤول الوحيد عن تشغيل الأذان)
                    PrayerAlarmManager.scheduleAllPrayers(applicationContext)
                    delay(5_000)
                    continue
                }
                updateNotif("المتبقي على ${next.prayerName}", formatRemaining(remaining))
                // عدّاد لحظي حقيقي: تحديث كل ثانية ليعرض الثواني بدقة
                delay(1_000)
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

    private fun buildNotif(title: String, text: String): android.app.Notification {
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
            .setContentText(digitalSpan(text))
            // صف واحد مضغوط: العدّاد يتغيّر كل ثانية فلا يليق بنص موسّع ولا بختم زمني متجدّد
            .setShowWhen(false)
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
            val now = Calendar.getInstance()
            val today = PrayerCalculationHelper.calculatePrayerTimes(now, city, city.defaultMethod)
            val pairs = listOf(
                PrayerAlarmManager.PRAYER_FAJR to ("صلاة الفجر" to today.fajr),
                PrayerAlarmManager.PRAYER_SUNRISE to ("الشروق" to today.sunrise),
                PrayerAlarmManager.PRAYER_DHUHR to ("صلاة الظهر" to today.dhuhr),
                PrayerAlarmManager.PRAYER_ASR to ("صلاة العصر" to today.asr),
                PrayerAlarmManager.PRAYER_MAGHRIB to ("صلاة المغرب" to today.maghrib),
                PrayerAlarmManager.PRAYER_ISHA to ("صلاة العشاء" to today.isha)
            )
            val defaultVoice = MuezzinVoiceManager.getDefaultVoiceId(context)
            var best: NextPrayer? = null
            for ((key, named) in pairs) {
                if (!prefs.getBoolean("notify_$key", true)) continue
                val cal = parseTime(named.second) ?: continue
                if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
                val voice = prefs.getString("voice_id_$key", defaultVoice) ?: defaultVoice
                val candidate = NextPrayer(named.first, cal.timeInMillis, voice)
                if (best == null || candidate.triggerMillis < best.triggerMillis) best = candidate
            }
            best
        } catch (_: Exception) { null }
    }

    private fun parseTime(t: String): Calendar? {
        return try {
            val p = t.trim().split(":")
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, p[0].toInt())
                set(Calendar.MINUTE, p[1].toInt())
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
        } catch (_: Exception) { null }
    }

    private fun formatRemaining(millis: Long): String {
        val h = TimeUnit.MILLISECONDS.toHours(millis)
        val m = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val s = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        // Locale.US: أرقام لاتينية دائماً بدل الأرقام الهندية الافتراضية في لغة الجهاز،
        // وشكل رقمي ثابت HH:MM:SS — قراءة عدّاد إلكتروني حديث
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    /** خط أحادي المسافة (Monospace) للأرقام فقط — مظهر شاشة رقمية */
    private fun digitalSpan(text: String): CharSequence {
        val isDigital = text.isNotEmpty() && text.all { it.isDigit() || it == ':' }
        if (!isDigital) return text
        return android.text.SpannableString(text).apply {
            setSpan(
                android.text.style.TypefaceSpan("monospace"),
                0, length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
