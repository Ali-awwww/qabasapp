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
import kotlinx.coroutines.cancelAndJoin
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

    /**
     * (ق65) عدّاد يتقدم مع كل تغيير يصل من جدول الرسائل (إدراج/تعديل/
     * حذف) — لوحة المطور كانت لا تعلم برسائل المستخدمين الجديدة إلا
     * بزر التحديث اليدوي، رغم أن الاشتراك اللحظي يعمل أصلاً.
     */
    private val _inboxChanged = MutableStateFlow(0L)
    val inboxChanged: StateFlow<Long> = _inboxChanged.asStateFlow()

    fun setChatOpen(open: Boolean) {
        chatOpen = open
        if (open) _unreadReplies.value = 0
    }

    fun start(context: Context, userId: String, userEmail: String) {
        if (userId.isBlank() || userEmail.isBlank() || !SupabaseServices.isSupabaseAvailable) return
        val appContext = context.applicationContext
        // ق69: ألغِ الاشتراك السابق وانتظر إنهاءه (وفك قناته) قبل فتح
        // قناة جديدة بالاسم نفسه؛ فالإلغاء وحده غير متزامن، وكان بدء
        // سريع متكرر (تبديل حساب) يترك قناتين متوازيتين فيُضاعف
        // الإشعارات وعدّاد غير المقروء.
        val previous = subscriptionJob
        subscriptionJob = scope.launch {
            previous?.cancelAndJoin()
            val knownIds = DevChatRepository.getLocalMessages(appContext, userEmail)
                .mapTo(mutableSetOf()) { it.id }
            // ق69: تراجع أسّي لإعادة الاتصال (5ث ← 60ث كحد أقصى) بدل
            // المحاولة كل 5 ثوانٍ إلى الأبد عند انقطاع الخادم؛ يُصفَّر
            // عند نجاح كل اشتراك.
            var backoffMs = 5_000L
            while (isActive) {
                try {
                    val client = SupabaseServices.client ?: return@launch
                    val channel = client.realtime.channel("dev-chat:$userId")
                    val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "dev_chat_messages"
                    }
                    try {
                        channel.subscribe(blockUntilSubscribed = true)
                        backoffMs = 5_000L
                        knownIds.addAll(
                            DevChatRepository.syncMessages(appContext, userEmail).map { it.id }
                        )
                        changes.collect {
                            // (ق65) أي تغيير في الجدول يقدّم عدّاد اللوحة
                            // فتعيد شاشة المطور تحميل محادثاتها حياً.
                            _inboxChanged.value = _inboxChanged.value + 1
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
                    Log.w(TAG, "Realtime disconnected; retrying in ${backoffMs}ms: ${error.message}")
                    delay(backoffMs)
                    backoffMs = minOf(backoffMs * 2, 60_000L)
                    continue
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
