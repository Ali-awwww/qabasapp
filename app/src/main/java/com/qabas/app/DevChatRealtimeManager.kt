package com.qabas.app

import android.content.Context
import android.util.Log
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * مراقبة ردود المطور عبر Supabase Realtime.
 * لا تعتمد على Firebase، وتعمل طوال حياة عملية التطبيق مع إعادة اتصال تلقائية.
 */
object DevChatRealtimeManager {
    private const val TAG = "DevChatRealtime"
    private const val PREFS = "qabas_dev_chat_realtime"
    private const val KEY_NOTIFIED_PREFIX = "notified_"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var subscriptionJob: Job? = null
    @Volatile private var chatOpen = false
    private val _unreadReplies = MutableStateFlow(0)
    val unreadReplies: StateFlow<Int> = _unreadReplies.asStateFlow()

    fun setChatOpen(open: Boolean) {
        chatOpen = open
        if (open) _unreadReplies.value = 0
    }

    fun start(context: Context, userId: String, userEmail: String) {
        if (userId.isBlank() || userEmail.isBlank() || !SupabaseServices.isSupabaseAvailable) return
        subscriptionJob?.cancel()
        val appContext = context.applicationContext
        subscriptionJob = scope.launch {
            val knownIds = DevChatRepository.getLocalMessages(appContext, userEmail)
                .mapTo(mutableSetOf()) { it.id }
            while (isActive) {
                try {
                    val client = SupabaseServices.client ?: return@launch
                    val channel = client.realtime.channel("dev-chat:$userId")
                    val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "dev_chat_messages"
                    }
                    try {
                        channel.subscribe(blockUntilSubscribed = true)
                        knownIds.addAll(
                            DevChatRepository.syncMessages(appContext, userEmail).map { it.id }
                        )
                        changes.collect {
                            val before = knownIds.toSet()
                            val synced = DevChatRepository.syncMessages(appContext, userEmail)
                            synced.forEach { message ->
                                knownIds.add(message.id)
                                if (!before.contains(message.id) &&
                                    message.sender.equals("developer", ignoreCase = true) &&
                                    !wasNotified(appContext, message.id)
                                ) {
                                    markNotified(appContext, message.id)
                                    if (!chatOpen) {
                                        _unreadReplies.value = _unreadReplies.value + 1
                                        NotificationHelper.showDeveloperReplyNotification(
                                            appContext,
                                            message.id,
                                            message.message
                                        )
                                    }
                                }
                            }
                        }
                    } finally {
                        client.realtime.removeChannel(channel)
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "Realtime disconnected; retrying: ${error.message}")
                }
                delay(5_000)
            }
        }
    }

    fun stop() {
        subscriptionJob?.cancel()
        subscriptionJob = null
    }

    private fun wasNotified(context: Context, messageId: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIFIED_PREFIX + messageId, false)

    private fun markNotified(context: Context, messageId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_NOTIFIED_PREFIX + messageId, true).apply()
    }
}
