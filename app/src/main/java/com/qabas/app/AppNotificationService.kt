package com.qabas.app

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlinx.coroutines.*

object AppNotificationService {
    data class AppNotification(
        val id: String = java.util.UUID.randomUUID().toString(),
        val title: String,
        val message: String,
        val isTrendAlert: Boolean = false,
        val timestamp: Long = System.currentTimeMillis(),
        val isRead: Boolean = false,
        val category: String = CATEGORY_SYSTEM
    )

    const val CATEGORY_SYSTEM = "system"
    const val CATEGORY_PRAYER = "prayer"
    const val CATEGORY_SPIRITUAL = "spiritual"
    const val CATEGORY_HADITH = "hadith"
    const val CATEGORY_TREND = "trend"

    private const val PREFS_NAME = "qabas_notifications"
    private const val KEY_NOTIFICATIONS = "notifications"
    private const val SETTINGS_PREFS = "qabas_notification_settings"
    private const val KEY_DAILY_HADITH_HOUR = "notif_daily_hadith_hour"
    private const val KEY_DAILY_HADITH_MINUTE = "notif_daily_hadith_minute"

    // حالة حيّة تدفع الواجهة فور أي تغيير (إضافة / قراءة / حذف)
    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    private var boundPrefs: SharedPreferences? = null
    private var boundContext: Context? = null
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_NOTIFICATIONS) {
            boundContext?.let { reload(it) }
        }
    }

    /** مصدر الحقيقة الوحيد: إعدادات الإشعارات الموحّدة */
    fun isDailyHadithEnabled(context: Context): Boolean {
        NotificationPreferencesManager.init(context)
        return NotificationPreferencesManager.masterEnabled.value &&
            NotificationPreferencesManager.dailyHadithEnabled.value
    }

    fun setDailyHadithEnabled(context: Context, enabled: Boolean) {
        NotificationPreferencesManager.setDailyHadithEnabled(context, enabled)
    }

    fun getDailyHadithTime(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        return Pair(
            prefs.getInt(KEY_DAILY_HADITH_HOUR, 9),
            prefs.getInt(KEY_DAILY_HADITH_MINUTE, 0)
        )
    }

    fun setDailyHadithTime(context: Context, hour: Int, minute: Int) {
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_DAILY_HADITH_HOUR, hour)
            .putInt(KEY_DAILY_HADITH_MINUTE, minute)
            .apply()
        if (isDailyHadithEnabled(context)) scheduleDailyHadithAlarm(context)
    }

    suspend fun triggerDailyHadithNow(context: Context, force: Boolean = false): String {
        val calendar = Calendar.getInstance()
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        
        // الحديث من المخزن المحلي الموثّق مع احتياطي مضمّن — لا «سحابة»
        // ولا قائمة احتياطية مولّدة: إن لم يوجد مصدر موثّق لا يُرسَل
        // حديث أبداً بدل اختلاق نص منسوب للنبي ﷺ (القائمة المحذوفة
        // كانت تحوي آيات قرآنية ونصوصاً مشوّهة على أنها أحاديث).
        val cloudHadith = DailyHadithRepository.getHadithForDay(context, dayOfYear)
            ?: return ""

        val hadithText = "قال رسول الله ﷺ: «${cloudHadith.text}»\n— ${cloudHadith.narrator} (${cloudHadith.book})\n💡 ومضة: ${cloudHadith.benefit}"

        if (!force && !isDailyHadithEnabled(context)) return hadithText

        // ساعات الهدوء (ق75): يُكتم إشعار الحديث الليلي دون مساس
        // بالنص المعاد لعرضه داخل التطبيق؛ والاستدعاء اليدوي (force)
        // لا يُكتم أبداً — والمنبه يُعاد جدولته من المستقبِل كالمعتاد.
        if (!force && NotificationPreferencesManager.isQuietNow()) return hadithText

        val title = "📖 حديث اليوم النبوي الشريف"
        recordNotification(context, title, hadithText, category = CATEGORY_HADITH)
        postHadithSystemNotification(context, title, hadithText)
        return hadithText
    }

    fun scheduleDailyHadithAlarm(context: Context) {
        if (!isDailyHadithEnabled(context)) {
            cancelDailyHadithAlarm(context)
            return
        }
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, DailyHadithReceiver::class.java).apply {
                action = "com.qabas.app.ACTION_DAILY_HADITH"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val (hour, minute) = getDailyHadithTime(context)
            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val showIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("navigate_to", "notifications")
            }
            val showPendingIntent = PendingIntent.getActivity(
                context,
                1101,
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else true
            if (canExact) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(calendar.timeInMillis, showPendingIntent),
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancelDailyHadithAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, DailyHadithReceiver::class.java).apply {
                action = "com.qabas.app.ACTION_DAILY_HADITH"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun postHadithSystemNotification(context: Context, title: String, message: String) {
        try {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager != null) {
                val notifId = QabasNotificationHelper.nextNotificationId(context) ?: return
                val builder = QabasNotificationHelper.createBaseBuilder(
                    context = context,
                    channelId = QabasNotificationHelper.CHANNEL_HADITH,
                    title = title,
                    message = message
                ) ?: return

                // أداة 1: مشاركة الحديث الشريف
                QabasNotificationHelper.addActionShare(
                    builder = builder,
                    context = context,
                    notifId = notifId,
                    shareText = "$title\n$message\n📖 من استوديو خادم المسلم للحديث الشريف"
                )

                // أداة 2: استماع للحديث بصوت قارئ
                QabasNotificationHelper.addActionListen(
                    builder = builder,
                    context = context,
                    notifId = notifId,
                    audioTitle = title
                )

                // أداة 3: تأكيد قراءة الحديث
                QabasNotificationHelper.addActionMarkDone(
                    builder = builder,
                    context = context,
                    notifId = notifId,
                    confirmationMessage = "جزاكم الله خيراً 🌿 تم تسجيل قراءة حديث اليوم"
                )

                notificationManager.notify(notifId, builder.build())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** يربط الواجهة بالحالة الحيّة ويطلق التحديثات لحظة تغيّر المخزن */
    fun observeNotifications(context: Context): StateFlow<List<AppNotification>> {
        init(context)
        return notifications
    }

    fun init(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (boundContext === appContext && boundPrefs === prefs) {
            reload(appContext)
            return
        }
        boundPrefs?.unregisterOnSharedPreferenceChangeListener(prefsListener)
        boundContext = appContext
        boundPrefs = prefs
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        purgeLegacySampleNotifications(appContext)
        reload(appContext)
    }

    /** إزالة بيانات العرض التجريبية القديمة التي كانت تُحقن تلقائياً (مرة واحدة) */
    private fun purgeLegacySampleNotifications(context: Context) {
        val legacyTitles = setOf(
            "🚨 تنبيه عاجل من مرصد الثغور!",
            "⚡ تريند يحتاج مدافعة سريعة"
        )
        val current = getNotifications(context)
        val cleaned = current.filterNot { it.title in legacyTitles }
        if (cleaned.size != current.size) persist(context, cleaned)
    }

    private fun reload(context: Context) {
        _notifications.value = getNotifications(context)
    }

    fun getNotifications(context: Context): List<AppNotification> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_NOTIFICATIONS, "[]") ?: "[]"
        val list = mutableListOf<AppNotification>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    AppNotification(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        message = obj.getString("message"),
                        isTrendAlert = obj.optBoolean("isTrendAlert", false),
                        timestamp = obj.getLong("timestamp"),
                        isRead = obj.optBoolean("isRead", true),
                        category = obj.optString("category", if (obj.optBoolean("isTrendAlert", false)) CATEGORY_TREND else CATEGORY_SYSTEM)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    /** حدّ سجل مركز الإشعارات: بلا حد كان JSON التفضيلات ينمو بلا نهاية
     * (كل إشعار يُضاف ويُعاد تحليل الملف كاملاً عند كل قراءة). */
    private const val MAX_STORED_NOTIFICATIONS = 200

    private fun persist(context: Context, list: List<AppNotification>) {
        val array = JSONArray()
        for (notif in list) {
            val obj = JSONObject()
            obj.put("id", notif.id)
            obj.put("title", notif.title)
            obj.put("message", notif.message)
            obj.put("isTrendAlert", notif.isTrendAlert)
            obj.put("timestamp", notif.timestamp)
            obj.put("isRead", notif.isRead)
            obj.put("category", notif.category)
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_NOTIFICATIONS, array.toString()).apply()
        _notifications.value = list
    }

    fun sendNotification(
        context: Context,
        title: String,
        message: String,
        isTrendAlert: Boolean = false,
        category: String = CATEGORY_SYSTEM
    ) {
        NotificationPreferencesManager.init(context)
        if (!NotificationPreferencesManager.masterEnabled.value) return
        if (isTrendAlert && !NotificationPreferencesManager.trendAlertsEnabled.value) return

        recordNotification(context, title, message, isTrendAlert, category)

        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            postSystemNotification(context, title, message)
        }
    }

    /** يسجّل الإشعار في المركز الحيّ فقط (للإشعارات التي تم عرضها بنظام مخصص) */
    fun recordNotification(
        context: Context,
        title: String,
        message: String,
        isTrendAlert: Boolean = false,
        category: String = CATEGORY_SYSTEM
    ) {
        val list = getNotifications(context).toMutableList()
        list.add(
            0,
            AppNotification(
                title = title,
                message = message,
                isTrendAlert = isTrendAlert,
                category = category,
                isRead = false
            )
        )
        if (list.size > MAX_STORED_NOTIFICATIONS) {
            list.subList(MAX_STORED_NOTIFICATIONS, list.size).clear()
        }
        persist(context, list)
    }

    fun markRead(context: Context, id: String) {
        val list = getNotifications(context).map { if (it.id == id) it.copy(isRead = true) else it }
        persist(context, list)
    }

    fun markAllRead(context: Context) {
        val list = getNotifications(context).map { it.copy(isRead = true) }
        persist(context, list)
    }

    fun deleteNotification(context: Context, id: String) {
        persist(context, getNotifications(context).filterNot { it.id == id })
    }

    fun clearNotifications(context: Context) {
        persist(context, emptyList())
    }

    fun unreadCount(context: Context): Int = getNotifications(context).count { !it.isRead }

    private fun postSystemNotification(context: Context, title: String, message: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager != null) {
                val notifId = QabasNotificationHelper.nextNotificationId(context) ?: return
                val builder = QabasNotificationHelper.createBaseBuilder(
                    context = context,
                    channelId = QabasNotificationHelper.CHANNEL_SYSTEM,
                    title = title,
                    message = message
                ) ?: return

                QabasNotificationHelper.addActionShare(
                    builder = builder,
                    context = context,
                    notifId = notifId,
                    shareText = "$title\n$message\n🛡️ مرصد خادم المسلم"
                )

                QabasNotificationHelper.addActionMarkDone(
                    builder = builder,
                    context = context,
                    notifId = notifId,
                    confirmationMessage = "تم تأكيد قراءة التنبيه بنجاح"
                )

                notificationManager.notify(notifId, builder.build())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class DailyHadithReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (AppNotificationService.isDailyHadithEnabled(context)) {
                    AppNotificationService.triggerDailyHadithNow(context)
                    // 🔑 إعادة جدولة تنبيه الغد (منبه لمرة واحدة بدل التكرار غير الدقيق)
                    AppNotificationService.scheduleDailyHadithAlarm(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class ProgressNotificationService(val context: Context) {
    companion object {
        private const val PROGRESS_NOTIF_ID = 990_020
    }

    /**
     * إشعار تقدّم حقيقي (كان دالة فارغة رغم أن ProcessingScreen
     * تستدعيها عند كل نسبة تقدّم): يُبنى عبر createProgressBuilder
     * الموحّد ويُحدَّث بالمعرّف الثابت نفسه.
     */
    fun showProgressNotification(progress: Int, max: Int, message: String) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val safeMax = max.coerceAtLeast(1)
            val builder = QabasNotificationHelper.createProgressBuilder(
                context,
                QabasNotificationHelper.CHANNEL_SYSTEM,
                "خادم المسلم",
                message
            ).setProgress(safeMax, progress.coerceIn(0, safeMax), false)
            manager.notify(PROGRESS_NOTIF_ID, builder.build())
        } catch (e: Throwable) {
            // Silently ignore notification failure to prevent process death
        }
    }
    fun hideProgressNotification() {
        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.cancel(PROGRESS_NOTIF_ID)
        } catch (e: Throwable) {
            // Silently ignore
        }
    }
    fun showCompletionNotification() {
        try {
            AppNotificationService.sendNotification(context, "اكتملت المعالجة", "تم الانتهاء من معالجة الفيديو بنجاح.")
        } catch (e: Throwable) {
            // Silently ignore notification failure to prevent process death
        }
    }
    fun showErrorNotification() {
        try {
            AppNotificationService.sendNotification(context, "خطأ في المعالجة", "حدث خطأ أثناء معالجة الفيديو.")
        } catch (e: Throwable) {
            // Silently ignore notification failure to prevent process death
        }
    }
}

