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

    private const val BRAND_COLOR = 0xFFD4AF37.toInt() // Qabas Gold

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
     * تهيئة وإنشاء كافة قنوات الإشعارات في النظام
     */
    fun initializeChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val channels = listOf(
            NotificationChannel(
                CHANNEL_SPIRITUAL,
                "نفحات السحر والتنبيهات الإيمانية",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات خاشعة لآيات السحر والفجر والورد القرآني"
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_PRAYER,
                "الأذان ومواقيت الصلاة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "نداء الأذان والتنبيه بدخول أوقات الصلاة المفروضة"
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_HADITH,
                "الحديث النبوي اليومي الشريف",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "إشعار يومي بأحاديث صحيحة من صحيح البخاري ومسلم"
            },
            NotificationChannel(
                CHANNEL_DEV_CHAT,
                "محادثات المطور والدعم المباشر",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات فورية عند وصول رد من فريق التطوير والإشراف"
            },
            NotificationChannel(
                CHANNEL_SYSTEM,
                "استوديو قبس والتحديثات",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "تنبيهات تقدم المعالجة، تنزيل المقاطع، وتريندات المجتمع"
            }
        )

        channels.forEach { manager.createNotificationChannel(it) }
    }

    /**
     * بناء كائن NotificationCompat.Builder الأساسي مع أيقونة التطبيق بجانب الإشعار
     */
    fun createBaseBuilder(
        context: Context,
        channelId: String,
        title: String,
        message: String
    ): NotificationCompat.Builder {
        initializeChannels(context)
        NotificationPreferencesManager.init(context)

        val appIcon = getAppIconBitmap(context)
        val soundEnabled = NotificationPreferencesManager.soundEnabled.value
        val vibrationEnabled = NotificationPreferencesManager.vibrationEnabled.value

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() % 10000).toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher) // أيقونة شريط الحالة
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
     * إضافة أداة "مشاركة المحتوى" إلى الإشعار
     */
    fun addActionShare(
        builder: NotificationCompat.Builder,
        context: Context,
        notifId: Int,
        shareText: String
    ) {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SHARE
            putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId)
            putExtra(NotificationActionReceiver.EXTRA_SHARE_TEXT, shareText)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notifId * 10 + 2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(android.R.drawable.ic_menu_share, "مشاركة 📤", pendingIntent)
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
     * إرسال إشعار تجريبي فوري تفاعلي كامل لإثبات ظهور الأيقونة وعمل الأدوات مباشرة
     */
    fun sendInteractiveTestNotification(context: Context) {
        NotificationPreferencesManager.init(context)
        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val notifId = (System.currentTimeMillis() % 10000).toInt()
        val title = "قبس • تجربة الإشعارات التفاعلية 🔔"
        val verseText = "﴿أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ﴾\nتم تفعيل ظهور أيقونة قبس بجانب الإشعار بنجاح! جرب أدوات الإشعار التفاعلية بالأسفل الآن:"

        val builder = createBaseBuilder(context, CHANNEL_SPIRITUAL, title, verseText)

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
        Toast.makeText(context, "تم إرسال الإشعار التفاعلي! تفقّد شريط الإشعارات بالأعلى 🔔", Toast.LENGTH_LONG).show()
    }
}
