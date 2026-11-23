package com.qabas.app

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 🛰️ خدمة استقبال الإشعارات السحابية (FCM) لتطبيق خادم المسلم
 * تتعامل مع التنبيهات الفورية، التحديثات، ورسائل النظام لضمان البقاء في الخلفية.
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "MyFirebaseMessagingService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
                title = it.title ?: "تنبيه جديد من خادم المسلم",
                message = it.body ?: ""
            )
        }
    }

    override fun onNewToken(token: String) {
        // لا يُسجَّل الرمز نفسه أبداً (انظر MainActivity): الطول فقط للتشخيص.
        Log.d(TAG, "تم تجديد رمز FCM (الطول: ${token.length})")
        // هنا يمكن إرسال الرمز الجديد للخادم أو Supabase إذا لزم الأمر
        sendRegistrationToServer(token)
    }

    private fun handleDataMessage(data: Map<String, String>) {
        if (data["type"] == "developer_reply") {
            NotificationHelper.showDeveloperReplyNotification(
                context = applicationContext,
                messageId = data["message_id"].orEmpty(),
                message = data["message"].orEmpty()
            )
            return
        }
        val title = data["title"] ?: "تحديث من خادم المسلم"
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
        serviceScope.launch {
            runCatching { PushTokenManager.onTokenRefreshed(applicationContext, token) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                .onFailure { Log.w(TAG, "تعذر ربط رمز FCM بالحساب: ${it.message}") }
        }
    }
}
