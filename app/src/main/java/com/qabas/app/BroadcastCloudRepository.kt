package com.qabas.app

import android.content.Context
import android.util.Log
import io.github.jan.supabase.Realtime
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
 * - insertBroadcast: يكتب صفًّا في جدول broadcasts (RLS: مطور فقط).
 * - subscribe: يفتح قناة Realtime ويعرض إشعارًا محليًّا عند وصول بث جديد.
 */
object BroadcastCloudRepository {
    private const val TAG = "BroadcastCloud"
    private const val TABLE = "broadcasts"

    private val _pendingBroadcast = MutableStateFlow<BroadcastRow?>(null)
    val pendingBroadcast: StateFlow<BroadcastRow?> = _pendingBroadcast.asStateFlow()

    private var realtimeChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null

    suspend fun insertBroadcast(
        title: String,
        message: String,
        target: String = "all"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val client = SupabaseServices.client ?: return@withContext Result.failure(IllegalStateException("Supabase غير مهيأ"))
            val row = BroadcastRow(title = title, message = message, target = target)
            client.from(TABLE).insert(row)
            Result.success(row.id)
        } catch (e: Exception) {
            Log.e(TAG, "insert failed", e)
            Result.failure(e)
        }
    }

    fun subscribe(context: Context) {
        if (realtimeChannel != null) return
        try {
            val client = SupabaseServices.client ?: return
            val channel = client.realtime.channel("broadcasts") {
                filter { eq("status", "pending") }
            }
            channel.onPostgresChanges(
                event = io.github.jan.supabase.realtime.PostgresChangeEvent.INSERT,
                schema = "public",
                table = TABLE,
                filter = io.github.jan.supabase.realtime.PostgresChangeFilter.eq("status", "pending")
            ) { payload ->
                try {
                    val record = payload.record
                    if (record != null) {
                        val json = kotlinx.serialization.json.Json {
                            ignoreUnknownKeys = true
                        }
                        val row = json.decodeFromString<BroadcastRow>(record.toString())
                        _pendingBroadcast.value = row
                        // عرض إشعار محلي
                        deliverLocalNotification(context, row)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "realtime payload parse failed", e)
                }
            }
            channel.subscribe()
            realtimeChannel = channel
            Log.d(TAG, "Realtime channel subscribed")
        } catch (e: Exception) {
            Log.e(TAG, "subscribe failed", e)
        }
    }

    fun unsubscribe() {
        realtimeChannel?.unsubscribe()
        realtimeChannel = null
    }

    private fun deliverLocalNotification(context: Context, row: BroadcastRow) {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val isDev = prefs.getBoolean("is_developer", false)
        val isPremium = prefs.getBoolean("is_premium", false)
        val matches = when (row.target) {
            "all" -> true
            "developers" -> isDev
            "premium" -> isPremium || isDev
            else -> true
        }
        if (!matches) return
        AppNotificationService.sendNotification(context, row.title, row.message)
        // تحديث الحالة إلى sent
        markAsSent(row.id)
    }

    suspend fun markAsSent(broadcastId: String) = withContext(Dispatchers.IO) {
        try {
            val client = SupabaseServices.client ?: return@withContext
            client.from(TABLE).update(mapOf("status" to "sent", "sent_at" to java.time.Instant.now().toString())) {
                filter { eq("id", broadcastId) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "markAsSent failed", e)
        }
    }
}