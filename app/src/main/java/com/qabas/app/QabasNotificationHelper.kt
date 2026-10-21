package com.qabas.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat

/**
 * 🛰️ QabasNotificationHelper — المساعد الشامل لبناء وإدارة إشعارات قبس التفاعلية
 * يضمن ظهور أيقونة التطبيق بجانب كل إشعار، ويدمج أدوات تفاعلية عاملة بالكامل
 * (استماع فوري، مشاركة، تأكيد القراءة، والتأجيل).
 */
object QabasNotificationHelper {

    const val CHANNEL_SPIRITUAL = "qabas_spiritual_reminders"
    const val CHANNEL_PRAYER = "qabas_prayer_azan"
    const val CHANNEL_HADITH = "qabas_daily_hadith"
    const val CHANNEL_SYSTEM = "qabas_system_alerts"
    const val CHANNEL_DEV_CHAT = "qabas_dev_chat"

    // ─── قنوات مركزية إضافية (تُنشأ بالمعرّفات نفسها الموجودة مسبقاً) ───
    const val CHANNEL_GENERAL = "qabas_general_notifications"
    const val CHANNEL_UPDATES = "qabas_updates"
    const val CHANNEL_MODELS = "qabas_models"
    const val CHANNEL_TRENDS = "qabas_trend_alerts"
    const val CHANNEL_PUBLISH_SCHEDULER = "qabas_publish_scheduler"
    const val CHANNEL_PRAYER_LEGACY = "qabas_prayer_notifications_channel"
    const val CHANNEL_AZAN_ALARM = "qabas_azan_alarm_channel"
    const val CHANNEL_AZAN_PLAYBACK = "qabas_azan_playback_channel"
    const val CHANNEL_AZAN_COUNTDOWN = "qabas_azan_countdown_channel"
    const val CHANNEL_AUDIO_PLAYBACK = "qabas_audio_playback_channel"

    const val BRAND_COLOR = 0xFFD4AF37.toInt() // Qabas Gold

    /** مخزن عدّاد المعرّفات الديناميكية الدائم (محفوظ على القرص) */
    private const val ID_PREFS = "qabas_notification_ids"
    private const val KEY_NEXT_DYNAMIC_ID = "next_dynamic_id"

    /**
     * قاعدة أرقام الإشعارات الديناميكية — أعلى بكثير من أي معرّف خدمة ثابت
     * في التطبيق (مثل 2001 و9923) فلا يحدث تصادم معها أبداً.
     */
    private const val DYNAMIC_ID_BASE = 10_000_000

    /** قفل تخصيص المعرّفات الديناميكية (سلامة تعدد الخيوط) */
    private val idAllocLock = Any()

    /**
     * تخصيص معرّف إشعار ديناميكي شبه فريد عبر إعادة تشغيل العملية نفسها:
     * - عدّاد دائم محفوظ على القرص يُكتب بشكل متزامن (commit) قبل إعادة الرقم
     * - إعادة تشغيل العملية تستأنف من آخر قيمة محفوظة فلا يتكرر المعرّف في المسار الطبيعي
     * - إن فشل الحفظ المتزامن تعيد null — ومسار الإرسال يتجاوز الإشعار المتأثر مع
     *   تسجيل السبب (لا معرّف مضمون الفردية بلا حفظ ناجح، ولا يُسقط التطبيق)
     * - معرفات الخدمات الثابتة (مثل 2001 و9923) أعلى منها القاعدة بكثير فلا تصادم
     */
    fun nextNotificationId(context: Context): Int? {
        val prefs = context.applicationContext
            .getSharedPreferences(ID_PREFS, Context.MODE_PRIVATE)
        synchronized(idAllocLock) {
            val next = prefs.getInt(KEY_NEXT_DYNAMIC_ID, DYNAMIC_ID_BASE) + 1
            val persisted = prefs.edit().putInt(KEY_NEXT_DYNAMIC_ID, next).commit()
            if (!persisted) {
                android.util.Log.w(
                    "QabasNotificationHelper",
                    "تعذّر حفظ عدّاد معرّفات الإشعارات — سيُتجاوز هذا الإشعار"
                )
                return null
            }
            return next
        }
    }

    /**
     * سجل القنوات المركزي — تعريف كل قناة بمحدداتها الدقيقة (الأهمية، الوصف،
     * الصوت، الاهتزاز، الرؤية) مطابقاً لما كان يُنشئه كل مكون لنفسه، حتى لا
     * تتغير إعدادات المستخدمين عند المركزية. الإنشاء idempotent ولا يعدّل
     * قناة قائمة (خصائص القنوات غير قابلة للتعديل بعد الإنشاء).
     */
    private fun channelDefinition(context: Context, channelId: String): NotificationChannel? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return when (channelId) {
            CHANNEL_SPIRITUAL -> NotificationChannel(
                CHANNEL_SPIRITUAL, "نفحات السحر والتنبيهات الإيمانية", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات خاشعة لآيات السحر والفجر والورد القرآني"
                enableVibration(true)
            }
            CHANNEL_PRAYER -> NotificationChannel(
                CHANNEL_PRAYER, "الأذان ومواقيت الصلاة", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "نداء الأذان والتنبيه بدخول أوقات الصلاة المفروضة"
                enableVibration(true)
            }
            CHANNEL_HADITH -> NotificationChannel(
                CHANNEL_HADITH, "الحديث النبوي اليومي الشريف", NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "إشعار يومي بأحاديث صحيحة من صحيح البخاري ومسلم"
            }
            CHANNEL_DEV_CHAT -> NotificationChannel(
                CHANNEL_DEV_CHAT, "محادثات المطور والدعم المباشر", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات فورية عند وصول رد من فريق التطوير والإشراف"
            }
            CHANNEL_SYSTEM -> NotificationChannel(
                CHANNEL_SYSTEM, "استوديو قبس والتحديثات", NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "تنبيهات تقدم المعالجة، تنزيل المقاطع، وتريندات المجتمع"
            }
            CHANNEL_GENERAL -> NotificationChannel(
                CHANNEL_GENERAL, "إشعارات قبس العامة", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات العمليات والتحديثات والنشاط العام"
                enableVibration(true)
            }
            CHANNEL_UPDATES -> NotificationChannel(
                CHANNEL_UPDATES, "تحديثات قبس", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "تقدّم تنزيل تحديثات التطبيق"
            }
            CHANNEL_MODELS -> NotificationChannel(
                CHANNEL_MODELS, "تنزيلات نماذج الذكاء", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "عداد تقدم تنزيل النماذج اللغوية"
            }
            CHANNEL_TRENDS -> NotificationChannel(
                CHANNEL_TRENDS, "تريندات المجتمع", NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "تنبيهات التريندات والنشاطات المجتمعية"
            }
            CHANNEL_PUBLISH_SCHEDULER -> NotificationChannel(
                CHANNEL_PUBLISH_SCHEDULER, "جدولة النشر الذكية (Smart Publish)", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات التوقيت المثالي لنشر الفيديوهات لزيادة التفاعل والانتشار"
            }
            CHANNEL_PRAYER_LEGACY -> NotificationChannel(
                CHANNEL_PRAYER_LEGACY, "مواقيت الصلاة والأذان", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات حية لمواقيت الصلاة الخمس والأذان الشريف"
                enableVibration(true)
                setShowBadge(true)
            }
            CHANNEL_AZAN_ALARM -> NotificationChannel(
                CHANNEL_AZAN_ALARM, "منبه الأذان (شاشة كاملة)", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعار الأذان الذي يفتح شاشة كاملة فوق أي تطبيق أو قفل"
                setSound(null, null)
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            CHANNEL_AZAN_PLAYBACK -> NotificationChannel(
                CHANNEL_AZAN_PLAYBACK, "صوت الأذان المباشر", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تشغيل صوت الأذان في أوقات الصلاة مع شاشة كاملة"
                setSound(null, null)
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            CHANNEL_AZAN_COUNTDOWN -> NotificationChannel(
                CHANNEL_AZAN_COUNTDOWN, "عدّاد اقتراب الأذان", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعار ثابت يعرض الوقت المتبقي على الأذان القادم"
            }
            CHANNEL_AUDIO_PLAYBACK -> NotificationChannel(
                CHANNEL_AUDIO_PLAYBACK, "تشغيل الصوت في الخلفية - قبس", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعارات وأزرار التحكم في الصوت والتلاوات في الخلفية"
                setShowBadge(false)
            }
            else -> null
        }
    }

    /** إنشاء قناة إن لم تكن موجودة — من التعريف المركزي الدقيق */
    fun ensureChannel(context: Context, channelId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channel = channelDefinition(context, channelId) ?: return
        manager.createNotificationChannel(channel)
    }

    /**
     * استخراج أيقونة التطبيق الحقيقية بصيغة Bitmap لوضعها بجانب نص الإشعار (Large Icon)
     */
    fun getAppIconBitmap(context: Context): Bitmap {
        return try {
            val appDrawable = context.packageManager.getApplicationIcon(context.packageName)
            if (appDrawable is BitmapDrawable && appDrawable.bitmap != null) {
                appDrawable.bitmap
            } else {
                val width = appDrawable.intrinsicWidth.coerceAtLeast(128)
                val height = appDrawable.intrinsicHeight.coerceAtLeast(128)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                appDrawable.setBounds(0, 0, canvas.width, canvas.height)
                appDrawable.draw(canvas)
                bitmap
            }
        } catch (e: Exception) {
            try {
                BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
                    ?: BitmapFactory.decodeResource(context.resources, R.drawable.img_qabas_logo)
            } catch (e2: Exception) {
                Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            }
        }
    }

    /**
     * تهيئة وإنشاء كافة قنوات الإشعارات الأساسية في النظام
     */
    fun initializeChannels(context: Context) {
        listOf(
            CHANNEL_SPIRITUAL, CHANNEL_PRAYER, CHANNEL_HADITH,
            CHANNEL_DEV_CHAT, CHANNEL_SYSTEM
        ).forEach { ensureChannel(context, it) }
    }

    /**
     * بناء كائن NotificationCompat.Builder الأساسي مع أيقونة التطبيق بجانب الإشعار
     */
    fun createBaseBuilder(
        context: Context,
        channelId: String,
        title: String,
        message: String
    ): NotificationCompat.Builder? {
        initializeChannels(context)
        NotificationPreferencesManager.init(context)

        val appIcon = getAppIconBitmap(context)
        val soundEnabled = NotificationPreferencesManager.soundEnabled.value
        val vibrationEnabled = NotificationPreferencesManager.vibrationEnabled.value

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "notifications")
        }
        val reqCode = nextNotificationId(context) ?: run {
            android.util.Log.w(
                "QabasNotificationHelper",
                "تعذّر تخصيص معرّف إشعار — تُتجاهل بناءة هذا الإشعار"
            )
            return null
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            reqCode,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_qabas) // جذوة قبس — أيقونة الحالة الموحّدة
            .setLargeIcon(appIcon) // أيقونة التطبيق تظهر بجانب عنوان ونص الإشعار
            .setColor(BRAND_COLOR)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (!soundEnabled) {
            builder.setSilent(true)
        }
        if (!vibrationEnabled) {
            builder.setVibrate(longArrayOf(0L))
        }

        return builder
    }

    /**
     * إضافة أداة "الاستماع الفوري" إلى الإشعار
     */
    fun addActionListen(
        builder: NotificationCompat.Builder,
        context: Context,
        notifId: Int,
        audioTitle: String,
        audioUrl: String? = null
    ) {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_LISTEN
            putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId)
            putExtra(NotificationActionReceiver.EXTRA_AUDIO_TITLE, audioTitle)
            if (audioUrl != null) {
                putExtra(NotificationActionReceiver.EXTRA_AUDIO_URL, audioUrl)
            }
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notifId * 10 + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(android.R.drawable.ic_media_play, "استماع 🎧", pendingIntent)
    }

    /**
     * إضافة أداة "مشاركة المحتوى" إلى الإشعار — مباشرة عبر نشاطي chooser
     * (بدون قفزة BroadcastReceiver: قفزات الإشعارات محجوبة في أندرويد 12+).
     */
    fun addActionShare(
        builder: NotificationCompat.Builder,
        context: Context,
        notifId: Int,
        shareText: String
    ) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        val chooser = Intent.createChooser(shareIntent, "مشاركة عبر")
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId * 10 + 2,
            chooser,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(android.R.drawable.ic_menu_share, "مشاركة", pendingIntent)
    }

    /**
     * إضافة أداة "تمت القراءة / تسجيل الورد" إلى الإشعار
     */
    fun addActionMarkDone(
        builder: NotificationCompat.Builder,
        context: Context,
        notifId: Int,
        confirmationMessage: String = "تقبل الله طاعتكم 🌿 تم تسجيل إتمام الورد اليومي بنجاح"
    ) {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_DONE
            putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId)
            putExtra(NotificationActionReceiver.EXTRA_MESSAGE, confirmationMessage)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notifId * 10 + 3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(android.R.drawable.checkbox_on_background, "تمت القراءة ✅", pendingIntent)
    }

    /**
     * إضافة أداة "التأجيل 15 دقيقة" إلى الإشعار
     */
    fun addActionSnooze(
        builder: NotificationCompat.Builder,
        context: Context,
        notifId: Int
    ) {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE
            putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notifId * 10 + 4,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(android.R.drawable.ic_lock_idle_alarm, "تأجيل 15 دقيقة ⏰", pendingIntent)
    }

    /**
     * بنّاء إشعارات التقدّم الموحّد (تنزيلات وعمليات) — جذوة قبس + لون ذهبي
     * + تحديث صامت (لا يرن عند كل نسبة تقدّم) + إمكانية إضافة زر إلغاء.
     */
    fun createProgressBuilder(
        context: Context,
        channelId: String,
        title: String,
        text: String
    ): NotificationCompat.Builder {
        ensureChannel(context, channelId)
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_qabas)
            .setLargeIcon(getAppIconBitmap(context))
            .setColor(BRAND_COLOR)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
    }

    /**
     * إرسال إشعار تجريبي فوري تفاعلي كامل لإثبات ظهور الأيقونة وعمل الأدوات مباشرة
     */
    fun sendInteractiveTestNotification(context: Context) {
        NotificationPreferencesManager.init(context)
        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val notifId = nextNotificationId(context) ?: return
        val title = "قبس • تجربة الإشعارات التفاعلية 🔔"
        val verseText = "﴿أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ﴾\nتم تفعيل ظهور أيقونة قبس بجانب الإشعار بنجاح! جرب أدوات الإشعار التفاعلية بالأسفل الآن:"

        val builder = createBaseBuilder(context, CHANNEL_SPIRITUAL, title, verseText) ?: return

        // أداة 1: استماع فوري
        addActionListen(
            builder = builder,
            context = context,
            notifId = notifId,
            audioTitle = "سورة الرعد - آية الطمأنينة",
            audioUrl = "https://server8.mp3quran.net/afs/013.mp3"
        )

        // أداة 2: مشاركة الآية الكريمة
        addActionShare(
            builder = builder,
            context = context,
            notifId = notifId,
            shareText = "﴿أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ﴾ 🌿 من تطبيق قبس"
        )

        // أداة 3: تأكيد القراءة وتسجيل الورد
        addActionMarkDone(
            builder = builder,
            context = context,
            notifId = notifId,
            confirmationMessage = "تقبل الله طاعتكم 🌿 تم تسجيل إتمام الورد في تطبيق قبس بنجاح!"
        )

        notifManager.notify(notifId, builder.build())
        AppNotificationService.recordNotification(
            context = context,
            title = title,
            message = verseText,
            category = AppNotificationService.CATEGORY_SPIRITUAL
        )
        Toast.makeText(context, "تم إرسال الإشعار التفاعلي! تفقّد شريط الإشعارات بالأعلى 🔔", Toast.LENGTH_LONG).show()
    }
}
