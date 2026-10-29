package com.qabas.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * مدير جلسات المحادثة المتعددة مع الوكيل البرمجي (Agent Chat Session Manager)
 * يتيح حفظ الجلسات، التبديل بين المحادثات القديمة، وبدء محادثة جديدة بنقرة واحدة.
 */
object AgentChatSessionManager {

    private const val PREFS_KEY = "agent_chat_sessions_v1"
    private const val ACTIVE_SESSION_ID_KEY = "active_agent_session_id"

    data class SessionMeta(
        val id: String,
        val title: String,
        val timestamp: Long,
        val messageCount: Int
    )

    private val _sessions = MutableStateFlow<List<SessionMeta>>(emptyList())
    val sessions: StateFlow<List<SessionMeta>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    fun init(context: Context) {
        loadSessions(context)
    }

    private fun loadSessions(context: Context) {
        val prefs = AgentPrefs.instance(context)
        val raw = prefs.getString(PREFS_KEY, null)
        val list = mutableListOf<SessionMeta>()
        if (!raw.isNullOrBlank()) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        SessionMeta(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            messageCount = obj.optInt("count", 0)
                        )
                    )
                }
            } catch (_: Exception) {}
        }
        _sessions.value = list.sortedByDescending { it.timestamp }
        _activeSessionId.value = prefs.getString(ACTIVE_SESSION_ID_KEY, null)
            ?: list.firstOrNull()?.id
    }

    fun startNewSession(context: Context): String {
        val prefs = AgentPrefs.instance(context)
        val newId = UUID.randomUUID().toString()
        val meta = SessionMeta(
            id = newId,
            title = "محادثة جديدة 💬",
            timestamp = System.currentTimeMillis(),
            messageCount = 0
        )
        val updated = listOf(meta) + _sessions.value.filterNot { it.id == newId }
        saveSessionMetas(context, updated)
        _activeSessionId.value = newId
        prefs.edit().putString(ACTIVE_SESSION_ID_KEY, newId).apply()
        return newId
    }

    fun switchSession(context: Context, sessionId: String) {
        val prefs = AgentPrefs.instance(context)
        _activeSessionId.value = sessionId
        prefs.edit().putString(ACTIVE_SESSION_ID_KEY, sessionId).apply()
    }

    fun saveSessionMessages(context: Context, sessionId: String, messages: List<AgentChatMessage>) {
        if (sessionId.isBlank()) return
        val prefs = AgentPrefs.instance(context)
        val key = "agent_session_messages_$sessionId"
        val arr = JSONArray()
        messages.forEach { msg ->
            val obj = JSONObject()
            obj.put("id", msg.id)
            obj.put("sender", msg.sender.name)
            obj.put("text", msg.text)
            obj.put("time", msg.timestamp)
            msg.diff?.let { d ->
                val dObj = JSONObject()
                dObj.put("file", d.filePath)
                dObj.put("add", d.additions)
                dObj.put("del", d.deletions)
                dObj.put("diff", d.diffText)
                obj.put("diff", dObj)
            }
            arr.put(obj)
        }
        prefs.edit().putString(key, arr.toString()).apply()

        // تحديث عنوان الجلسة بناءً على أول رسالة للمستخدم
        val firstUserMsg = messages.firstOrNull { it.sender == AgentChatSender.USER }?.text?.take(30)
        val currentTitle = if (!firstUserMsg.isNullOrBlank()) firstUserMsg else "محادثة سابقة"

        val updatedMetas = _sessions.value.map {
            if (it.id == sessionId) {
                it.copy(title = currentTitle, messageCount = messages.size, timestamp = System.currentTimeMillis())
            } else it
        }
        saveSessionMetas(context, updatedMetas)
    }

    fun loadSessionMessages(context: Context, sessionId: String): List<AgentChatMessage> {
        val prefs = AgentPrefs.instance(context)
        val key = "agent_session_messages_$sessionId"
        val raw = prefs.getString(key, null) ?: return emptyList()
        val list = mutableListOf<AgentChatMessage>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val diff = if (obj.has("diff")) {
                    val dObj = obj.getJSONObject("diff")
                    FileDiff(
                        filePath = dObj.getString("file"),
                        additions = dObj.optInt("add", 0),
                        deletions = dObj.optInt("del", 0),
                        diffText = dObj.getString("diff")
                    )
                } else null

                list.add(
                    AgentChatMessage(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        sender = AgentChatSender.valueOf(obj.getString("sender")),
                        text = obj.getString("text"),
                        diff = diff,
                        timestamp = obj.optLong("time", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun deleteSession(context: Context, sessionId: String) {
        val prefs = AgentPrefs.instance(context)
        prefs.edit().remove("agent_session_messages_$sessionId").apply()
        val updated = _sessions.value.filterNot { it.id == sessionId }
        saveSessionMetas(context, updated)
        if (_activeSessionId.value == sessionId) {
            val nextId = updated.firstOrNull()?.id ?: startNewSession(context)
            switchSession(context, nextId)
        }
    }

    private fun saveSessionMetas(context: Context, list: List<SessionMeta>) {
        val prefs = AgentPrefs.instance(context)
        val arr = JSONArray()
        list.forEach {
            val o = JSONObject()
            o.put("id", it.id)
            o.put("title", it.title)
            o.put("timestamp", it.timestamp)
            o.put("count", it.messageCount)
            arr.put(o)
        }
        prefs.edit().putString(PREFS_KEY, arr.toString()).apply()
        _sessions.value = list.sortedByDescending { it.timestamp }
    }
}
