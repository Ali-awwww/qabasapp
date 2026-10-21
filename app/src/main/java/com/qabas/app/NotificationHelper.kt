package com.qabas.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    private val CHANNEL_ID get() = QabasNotificationHelper.CHANNEL_GENERAL

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
}
