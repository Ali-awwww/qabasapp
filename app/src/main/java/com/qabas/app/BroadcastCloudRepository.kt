package com.qabas.app

import android.content.Context
import android.util.Log
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

@Serializable
data class BroadcastRow(
    @SerialName("id") val id: String = UUID.randomUUID().toString(),
    @SerialName("title") val title: String,
    @SerialName("message") val message: String,
    @SerialName("target") val target: String = "all",
    @SerialName("created_at") val createdAt: String = java.time.Instant.now().toString(),
    @SerialName("sent_at") val sentAt: String? = null,
    @SerialName("status") val status: String = "pending"
)

/**
 * بث الإشعارات عبر Supabase:
 * - insertBroadcast: يكتب صفًّا في جدول broadcasts.
 * - fetchPendingBroadcasts: يجلب الإشعارات المعلقة ويعرضها.
 */
object BroadcastCloudRepository {
    private const val TAG = "BroadcastCloud"
    private const val TABLE = "broadcasts"

    private val _pendingBroadcast = MutableStateFlow<BroadcastRow?>(null)
    val pendingBroadcast: StateFlow<BroadcastRow?> = _pendingBroadcast.asStateFlow()

    suspend fun insertBroadcast(
        title: String,
        message: String,
        target: String = "all"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val client = SupabaseServices.client ?: return@withContext Result.failure(IllegalStateException("Supabase غير مهيأ"))
            val params = buildJsonObject {
                put("p_title", title)
                put("p_message", message)
                put("p_target", target)
            }
            val broadcastId = client.postgrest.rpc("create_broadcast", params).decodeAs<String>()
            Result.success(broadcastId)
        } catch (e: Exception) {
            Log.e(TAG, "insert failed", e)
            Result.failure(e)
        }
    }

    suspend fun checkPendingBroadcasts(context: Context) = withContext(Dispatchers.IO) {
        try {
            val client = SupabaseServices.client ?: return@withContext
            val pending = client.from(TABLE).select {
                filter { eq("status", "pending") }
            }.decodeList<BroadcastRow>()

            for (row in pending) {
                _pendingBroadcast.value = row
                deliverLocalNotification(context, row)
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkPendingBroadcasts failed", e)
        }
    }

    fun subscribe(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            checkPendingBroadcasts(context)
        }
    }

    fun unsubscribe() {
        _pendingBroadcast.value = null
    }

    private fun deliverLocalNotification(context: Context, row: BroadcastRow) {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val cloudRole = SupabaseServices.Auth.getUserRoleFromMetadata()
        val isDev = SupabaseServices.Auth.isDeveloperRole(cloudRole) || AdminGuard.isCloudUserOwner()
        val isPremium = prefs.getBoolean("is_premium", false)
        val matches = when (row.target) {
            "all" -> true
            "developers" -> isDev
            "premium" -> isPremium || isDev
            else -> true
        }
        if (!matches) return
        AppNotificationService.sendNotification(context, row.title, row.message)
        CoroutineScope(Dispatchers.IO).launch {
            markAsSent(row.id)
        }
    }

    suspend fun markAsSent(broadcastId: String) = withContext(Dispatchers.IO) {
        try {
            val client = SupabaseServices.client ?: return@withContext
            val params = buildJsonObject {
                put("p_broadcast_id", broadcastId)
            }
            client.postgrest.rpc("mark_broadcast_sent", params)
        } catch (e: Exception) {
            Log.e(TAG, "markAsSent failed", e)
        }
    }
}
