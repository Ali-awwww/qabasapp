package com.qabas.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * نظام الإشعارات والتنبيهات المحلية
 */
object RemoteNotificationsManager {
    private const val TAG = "RemoteNotificationsManager"
    private const val SEEN_PREFS = "remote_notif_seen_ids"

    data class Broadcast(
        val id: String,
        val title: String,
        val message: String,
        val target: String,
        val createdAt: Long,
        val sendAt: Long = 0L
    ) {
        val dueAt: Long get() = if (sendAt > 0) sendAt else createdAt
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _lastSendResult = MutableStateFlow<String?>(null)
    val lastSendResult: StateFlow<String?> = _lastSendResult.asStateFlow()

    /** إرسال إشعار محلي فوري للمستخدم */
    suspend fun sendBroadcast(
        context: Context,
        title: String,
        message: String,
        target: String = "all",
        sendAt: Long = 0L
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

    /** تدفق الإشعارات */
    fun observeBroadcasts(): Flow<List<Broadcast>> = flowOf(emptyList())

    /** هل ينتمي هذا الجهاز للشريحة المستهدفة؟ */
    fun deviceMatchesTarget(context: Context, target: String): Boolean {
        if (target == "all") return true
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        return when (target) {
            "developers" -> prefs.getBoolean("is_developer", false)
            "premium" -> prefs.getBoolean("is_premium", false) ||
                prefs.getBoolean("is_developer", false)
            else -> true
        }
    }

    suspend fun cancelBroadcast(docId: String): Boolean = true

    fun startInbox(context: Context) {
        // Local notifications manager is active
    }
}

