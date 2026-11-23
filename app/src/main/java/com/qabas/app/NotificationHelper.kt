package com.qabas.app

import android.app.NotificationManager
import android.content.Context

object NotificationHelper {
    private val CHANNEL_ID get() = QabasNotificationHelper.CHANNEL_GENERAL
    private const val DEV_REPLY_EVENT_PREFS = "qabas_dev_reply_notifications"

    fun showNotification(
        context: Context,
        title: String,
        message: String,
        shareText: String? = null
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        QabasNotificationHelper.ensureChannel(context, QabasNotificationHelper.CHANNEL_GENERAL)

        // معرّف مضمون الحفظ قبل الإرسال — وإلا يُتجاوز الإشعار مع تسجيل السبب
        val notifId = QabasNotificationHelper.nextNotificationId(context) ?: return
        val builder = QabasNotificationHelper.createBaseBuilder(context, CHANNEL_ID, title, message) ?: return

        if (!shareText.isNullOrBlank()) {
            QabasNotificationHelper.addActionShare(builder, context, notifId, shareText)
        }

        QabasNotificationHelper.addActionMarkDone(
            builder = builder,
            context = context,
            notifId = notifId,
            confirmationMessage = "تم تأكيد وقراءة الإشعار بنجاح"
        )

        notificationManager.notify(notifId, builder.build())
    }

    fun showDeveloperReplyNotification(context: Context, messageId: String, message: String) {
        val appContext = context.applicationContext
        val key = messageId.trim().ifBlank { message.trim().hashCode().toString() }
        val prefs = appContext.getSharedPreferences(DEV_REPLY_EVENT_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean("shown_$key", false)) return
        prefs.edit().putBoolean("shown_$key", true).apply()

        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        QabasNotificationHelper.ensureChannel(appContext, QabasNotificationHelper.CHANNEL_DEV_CHAT)
        val notificationId = QabasNotificationHelper.nextNotificationId(appContext) ?: return
        val safeMessage = message.trim().ifBlank { "لديك رد جديد من المطور" }
        val builder = QabasNotificationHelper.createBaseBuilder(
            context = appContext,
            channelId = QabasNotificationHelper.CHANNEL_DEV_CHAT,
            title = "رد جديد من المطور",
            message = safeMessage,
            navigateTo = "request_chat"
        ) ?: return
        manager.notify(notificationId, builder.build())
        AppNotificationService.recordNotification(
            context = appContext,
            title = "رد جديد من المطور",
            message = safeMessage,
            category = AppNotificationService.CATEGORY_SYSTEM
        )
    }
}
