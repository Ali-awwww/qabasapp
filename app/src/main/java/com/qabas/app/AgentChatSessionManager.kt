package com.qabas.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * مدير جلسات المحادثة المتعددة مع الوكيل البرمجي (Agent Chat Session Manager)
 * يتيح حفظ الجلسات، التبديل بين المحادثات القديمة، وبدء محادثة جديدة بنقرة واحدة.
 */
object AgentChatSessionManager {

    private const val PREFS_KEY = "agent_chat_sessions_v1"
    private const val ACTIVE_SESSION_ID_KEY = "active_agent_session_id"
    private const val SESSION_FILES_DIR = "agent_sessions"

    /**
     * حدود الاحتفاظ: بلا حد كانت الجلسة تنمو بلا سقف (والملف كاملاً
     * يُعاد كتابته عند كل رسالة)، فتثقل الكتابة والقراءة مع طول المحادثة.
     */
    const val MAX_SESSION_MESSAGES = 200
    const val MAX_SESSIONS = 50

    /**
     * كتلة سياق من آخر تبادلات الجلسة تُلحق بموجه النموذج — فالاستدعاءات
     * كانت بلا أي سجل محادثة (كل دور منفصل تماماً) فينسى الوكيل ما قيل
     * قبله في الجلسة نفسها. محدودة الحجم (آخر 12 رسالة، حتى 6000 محرف،
     * والأحدث أولاً عند الاقتطاع)، وبلا نصوص الفروقات الكاملة (يُذكر
     * مسار الملف وعدد الأسطر فقط). رسالة الطلب الحالية لا تُدرج هنا؛
     * مواضع الاستدعاء تمرر السجل السابق لها.
     */
    fun historyBlock(
        messages: List<AgentChatMessage>,
        maxChars: Int = 6000,
        maxMessages: Int = 12
    ): String {
        if (messages.isEmpty()) return ""
        val picked = mutableListOf<String>()
        var used = 0
        for (m in messages.takeLast(maxMessages).asReversed()) {
            val who = when (m.sender) {
                AgentChatSender.USER -> "المستخدم"
                AgentChatSender.AGENT -> "الوكيل"
                AgentChatSender.TOOL_SYSTEM -> "النظام"
            }
            val diffNote = m.diff?.let {
                " [تعديل مقترح: ${it.filePath} (+${it.additions}/−${it.deletions})]"
            }.orEmpty()
            val line = "$who: ${m.text.take(800)}$diffNote"
            if (picked.isNotEmpty() && used + line.length > maxChars) break
            picked.add(line)
            used += line.length
        }
        if (picked.isEmpty()) return ""
        return buildString {
            append("— سجل المحادثة الأخيرة في هذه الجلسة (للسياق فقط) —\n")
            picked.asReversed().forEach { append(it).append('\n') }
            append("— نهاية السجل —")
        }
    }

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
                    // إدخال تالف واحد كان يُسقط قائمة الجلسات كلها؛ يُتخطى وحده.
                    runCatching {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optString("id", "")
                        if (id.isBlank()) return@runCatching
                        list.add(
                            SessionMeta(
                                id = id,
                                title = obj.optString("title", "محادثة سابقة").ifBlank { "محادثة سابقة" },
                                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                                messageCount = obj.optInt("count", 0)
                            )
                        )
                    }
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
        // يُحفظ آخر 200 رسالة فقط — بلا حد كان الملف كاملاً يُعاد
        // كتابته عند كل رسالة فيكبر بلا سقف (مع نصوص فروقات ضخمة).
        val capped = messages.takeLast(MAX_SESSION_MESSAGES)
        val arr = JSONArray()
        capped.forEach { msg ->
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
        if (writeSessionFile(context, sessionId, arr.toString())) {
            // إزالة النسخة القديمة بعد نجاح الكتابة إلى التخزين الداخلي.
            prefs.edit().remove("agent_session_messages_$sessionId").apply()
        }

        // تحديث عنوان الجلسة بناءً على أول رسالة للمستخدم
        val firstUserMsg = capped.firstOrNull { it.sender == AgentChatSender.USER }?.text?.take(30)
        val currentTitle = if (!firstUserMsg.isNullOrBlank()) firstUserMsg else "محادثة سابقة"

        val updatedMetas = _sessions.value.map {
            if (it.id == sessionId) {
                it.copy(title = currentTitle, messageCount = capped.size, timestamp = System.currentTimeMillis())
            } else it
        }
        saveSessionMetas(context, updatedMetas)
    }

    fun loadSessionMessages(context: Context, sessionId: String): List<AgentChatMessage> {
        val prefs = AgentPrefs.instance(context)
        val key = "agent_session_messages_$sessionId"
        val file = sessionFile(context, sessionId)
        val legacyRaw = prefs.getString(key, null)
        val raw = file.takeIf { it.isFile }?.runCatching { readText() }?.getOrNull()
            ?: legacyRaw
            ?: return emptyList()
        if (!file.isFile && legacyRaw != null) {
            if (writeSessionFile(context, sessionId, legacyRaw)) {
                prefs.edit().remove(key).apply()
            }
        }
        val list = mutableListOf<AgentChatMessage>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                // رسالة تالفة واحدة (مرسل مجهول بعد تحديث، أو نص مفقود)
                // كانت تُسقط الجلسة كلها بإرجاع قائمة فارغة؛ تُتخطى وحدها.
                runCatching {
                    val obj = arr.getJSONObject(i)
                    val sender = runCatching {
                        AgentChatSender.valueOf(obj.optString("sender", ""))
                    }.getOrNull() ?: return@runCatching
                    val text = obj.optString("text", "")
                    if (text.isBlank()) return@runCatching
                    val diff = if (obj.has("diff")) {
                        val dObj = obj.getJSONObject("diff")
                        FileDiff(
                            filePath = dObj.optString("file", ""),
                            additions = dObj.optInt("add", 0),
                            deletions = dObj.optInt("del", 0),
                            diffText = dObj.optString("diff", "")
                        )
                    } else null

                    list.add(
                        AgentChatMessage(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            sender = sender,
                            text = text,
                            diff = diff,
                            timestamp = obj.optLong("time", System.currentTimeMillis())
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun deleteSession(context: Context, sessionId: String) {
        val prefs = AgentPrefs.instance(context)
        prefs.edit().remove("agent_session_messages_$sessionId").apply()
        sessionFile(context, sessionId).delete()
        val updated = _sessions.value.filterNot { it.id == sessionId }
        saveSessionMetas(context, updated)
        if (_activeSessionId.value == sessionId) {
            val nextId = updated.firstOrNull()?.id ?: startNewSession(context)
            switchSession(context, nextId)
        }
    }

    private fun saveSessionMetas(context: Context, list: List<SessionMeta>) {
        val prefs = AgentPrefs.instance(context)
        // أحدث 50 جلسة فقط؛ ملفات الجلسات المسقَطة تُحذف حتى لا تتراكم
        // يتيمة على القرص. الجلسة النشطة لا تُسقَط أبداً (قد تكون قديمة
        // التاريخ بعد تبديل إليها) وإلا صار المعرّف النشط يشير لملف محذوف.
        val sorted = list.sortedByDescending { it.timestamp }
        val trimmed = sorted.take(MAX_SESSIONS).toMutableList()
        val activeId = _activeSessionId.value
        if (activeId != null && trimmed.none { it.id == activeId }) {
            sorted.firstOrNull { it.id == activeId }?.let { trimmed.add(it) }
        }
        val keptIds = trimmed.map { it.id }.toSet()
        list.filter { it.id !in keptIds }.forEach { dropped ->
            runCatching { sessionFile(context, dropped.id).delete() }
            prefs.edit().remove("agent_session_messages_${dropped.id}").apply()
        }
        val arr = JSONArray()
        trimmed.forEach {
            val o = JSONObject()
            o.put("id", it.id)
            o.put("title", it.title)
            o.put("timestamp", it.timestamp)
            o.put("count", it.messageCount)
            arr.put(o)
        }
        prefs.edit().putString(PREFS_KEY, arr.toString()).apply()
        _sessions.value = trimmed
    }

    private fun sessionFile(context: Context, sessionId: String): File {
        val safeId = sessionId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(File(context.filesDir, SESSION_FILES_DIR), "$safeId.json")
    }

    private fun writeSessionFile(context: Context, sessionId: String, raw: String): Boolean =
        runCatching {
            val target = sessionFile(context, sessionId)
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, ".${target.name}.tmp")
            temp.writeText(raw, Charsets.UTF_8)
            if (!temp.renameTo(target)) {
                target.writeText(raw, Charsets.UTF_8)
                temp.delete()
            }
            true
        }.getOrDefault(false)
}
