package com.qabas.app

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import java.time.Instant

/**
 * Supabase repository for saved Quran verses, inspirations, and adhkar.
 * Row ownership is enforced by PostgreSQL RLS using auth.uid().
 */
class SavedItemsRepository(
    private val client: SupabaseClient = SupabaseConfig.client
) {
    fun observeSavedItems(userId: String): Flow<List<SavedItem>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }

        // RLS limits visible rows to the authenticated owner. A user-scoped
        // channel avoids the unsupported filter-property API in supabase-kt 3.6.
        val channel = client.realtime.channel("saved-items:$userId")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = TABLE
        }

        try {
            channel.subscribe(blockUntilSubscribed = true)
            emit(fetchSavedItems(userId))
            changes.collect {
                emit(fetchSavedItems(userId))
            }
        } finally {
            client.realtime.removeChannel(channel)
        }
    }.retryWhen { cause, attempt ->
        // ق69: بلا إعادة محاولة كان أي انقطاع عابر (اشتراك أو جلب)
        // ينهي التدفق نهائياً فيتحول ViewModel إلى حالة خطأ بلا زر
        // إعادة محاولة؛ فتموت التحديثات الحية لبقية الجلسة بينما
        // محادثة المطور (قناة اللحظي الأخرى) تعيد الاتصال تلقائياً.
        // الإلغاء وخطأ البرمجة (معرّف فارغ) لا يُعادان أبداً.
        if (cause is kotlinx.coroutines.CancellationException) return@retryWhen false
        if (cause is IllegalArgumentException) return@retryWhen false
        delay(minOf(2_000L * (1L shl attempt.coerceAtMost(4).toInt()), 30_000L))
        true
    }

    suspend fun fetchSavedItems(userId: String): List<SavedItem> {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        return client.postgrest.from(TABLE).select {
            filter { eq("user_id", userId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<SavedItem>()
    }

    suspend fun saveItem(item: SavedItem) {
        require(item.userId.isNotBlank()) { "User ID must not be blank" }
        require(item.id.isNotBlank()) { "Item ID must not be blank" }
        val now = Instant.now().toString()
        val row = item.copy(
            createdAt = item.createdAt ?: now,
            updatedAt = now
        )
        client.postgrest.from(TABLE).upsert(row) {
            onConflict = "user_id,id"
        }
    }

    suspend fun deleteItem(userId: String, itemId: String) {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        require(itemId.isNotBlank()) { "Item ID must not be blank" }
        client.postgrest.from(TABLE).delete {
            filter {
                eq("user_id", userId)
                eq("id", itemId)
            }
        }
    }

    private companion object {
        const val TABLE = "saved_items"
    }
}
