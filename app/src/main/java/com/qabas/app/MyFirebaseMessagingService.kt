package com.qabas.app

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * 🛰️ خدمة استقبال الإشعارات السحابية (FCM) لتطبيق قبس
 * تتعامل مع التنبيهات الفورية، التحديثات، ورسائل النظام لضمان البقاء في الخلفية.
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "MyFirebaseMessagingService"

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // التحقق من وجود بيانات أو إشعار
        Log.d(TAG, "من: ${remoteMessage.from}")

        // إذا كانت الرسالة تحتوي على بيانات (Data payload)
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "بيانات الرسالة: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }

        // إذا كانت الرسالة تحتوي على إشعار (Notification payload)
        remoteMessage.notification?.let {
            Log.d(TAG, "عنوان الإشعار: ${it.title}, نص الإشعار: ${it.body}")
            NotificationHelper.showNotification(
                context = applicationContext,
                title = it.title ?: "تنبيه جديد من قبس",
                message = it.body ?: ""
            )
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "تم تجديد الرمز: $token")
        // هنا يمكن إرسال الرمز الجديد للخادم أو Supabase إذا لزم الأمر
        sendRegistrationToServer(token)
    }

    private fun handleDataMessage(data: Map<String, String>) {
        val title = data["title"] ?: "تحديث من قبس"
        val message = data["message"] ?: ""
        val shareText = data["share_text"]
        
        NotificationHelper.showNotification(
            context = applicationContext,
            title = title,
            message = message,
            shareText = shareText
        )
    }

    private fun sendRegistrationToServer(token: String) {
        // TODO: تنفيذ الربط مع الخادم لحفظ الرمز (FCM Token)
    }
}
