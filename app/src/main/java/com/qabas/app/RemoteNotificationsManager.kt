package com.qabas.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * نظام الإشعارات والتنبيهات المحلية
 */
object RemoteNotificationsManager {
    private const val TAG = "RemoteNotificationsManager"

    private val _lastSendResult = MutableStateFlow<String?>(null)
    val lastSendResult: StateFlow<String?> = _lastSendResult.asStateFlow()

    /** إرسال إشعار محلي فوري للمستخدم */
    suspend fun sendBroadcast(
        context: Context,
        title: String,
        message: String,
        target: String = "all"
    ): String? {
        return try {
            AppNotificationService.sendNotification(context, title, message)
            AuditLogger.log(context, "notification_local", "«$title» إلى $target")
            _lastSendResult.value = "تم إرسال الإشعار بنجاح ✅"
            java.util.UUID.randomUUID().toString()
        } catch (e: Exception) {
            Log.e(TAG, "sendBroadcast failed: ${e.message}", e)
            _lastSendResult.value = "فشل الإرسال: ${e.message}"
            null
        }
    }

    fun startInbox(context: Context) {
        // Local notifications manager is active
    }
}
