package com.qabas.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    private const val CHANNEL_ID = "qabas_general_notifications"
    private const val CHANNEL_NAME = "إشعارات قبس العامة"
    private const val CHANNEL_DESC = "تنبيهات العمليات والتحديثات والنشاط العام"

    fun showNotification(
        context: Context,
        title: String,
        message: String,
        shareText: String? = null
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notifId = (System.currentTimeMillis() % 10000).toInt()
        val builder = QabasNotificationHelper.createBaseBuilder(context, CHANNEL_ID, title, message)

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
