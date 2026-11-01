package com.qabas.app

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
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

        val channel = client.realtime.createChannel("saved-items:$userId")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = TABLE
            filter = "user_id=eq.$userId"
        }

        try {
            client.realtime.connect()
            channel.join(blockUntilJoined = true)
            emit(fetchSavedItems(userId))
            changes.collect {
                emit(fetchSavedItems(userId))
            }
        } finally {
            client.realtime.removeChannel(channel)
        }
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

    suspend fun isItemSaved(userId: String, itemId: String): Boolean {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        require(itemId.isNotBlank()) { "Item ID must not be blank" }
        return client.postgrest.from(TABLE).select {
            filter {
                eq("user_id", userId)
                eq("id", itemId)
            }
            limit(1)
        }.decodeList<SavedItem>().isNotEmpty()
    }

    suspend fun toggleSaved(userId: String, item: SavedItem): Boolean {
        return if (isItemSaved(userId, item.id)) {
            deleteItem(userId, item.id)
            false
        } else {
            saveItem(item.copy(userId = userId))
            true
        }
    }

    private companion object {
        const val TABLE = "saved_items"
    }
}
