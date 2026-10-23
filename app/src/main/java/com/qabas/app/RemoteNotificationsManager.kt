package com.qabas.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * نظام الإشعارات السحابية + المحلية
 */
object RemoteNotificationsManager {
    private const val TAG = "RemoteNotificationsManager"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _lastSendResult = MutableStateFlow<String?>(null)
    val lastSendResult: StateFlow<String?> = _lastSendResult.asStateFlow()

    /** إرسال بث سحابي — يكتب في Supabase، و Realtime يوصل لجميع الأجهزة */
    suspend fun sendBroadcast(
        context: Context,
        title: String,
        message: String,
        target: String = "all"
    ): String? {
        return try {
            val result = BroadcastCloudRepository.insertBroadcast(title, message, target)
            result.getOrElse {
                throw it
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendBroadcast failed: ${e.message}", e)
            _lastSendResult.value = "فشل الإرسال السحابي: ${e.message}"
            // fallback محلي
            AppNotificationService.sendNotification(context, title, message)
            AuditLogger.log(context, "notification_local_fallback", "«$title» إلى $target")
            null
        }
    }

    fun startInbox(context: Context) {
        BroadcastCloudRepository.subscribe(context)
    }
}
