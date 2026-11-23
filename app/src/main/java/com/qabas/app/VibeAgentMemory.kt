package com.qabas.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * ذاكرة الوكيل الخفيفة لكل مشروع فايب كودينج (12.12): ملخص الجلسة +
 * قرارات المشروع + ملفات مهمة.
 *
 * التخزين على مخزن Room الموجود لا مخزن جديد: حقل `memory` في
 * [VibeCodingProject] (يُحفظ JSON داخل workspaceJson عبر ProjectService).
 * التمرير للنموذج: تُلحق كتلة سياق بموجه التخطيط في
 * `executeAgentMissionInner` (المسار السحابي والمحلي) — فالتركيب
 * (synthesis) يرثها عبر الخطة المولدة. لا إعادة بناء للمحادثة.
 */
data class VibeAgentMemory(
    val summary: String = "",
    val decisions: List<String> = emptyList(),
    val importantFiles: List<String> = emptyList(),
    val updatedAt: Long = 0L
) {
    val isEmpty: Boolean
        get() = summary.isBlank() && decisions.isEmpty() && importantFiles.isEmpty()

    fun toJson(): String = JSONObject().apply {
        put("summary", summary)
        put("decisions", JSONArray(decisions))
        put("importantFiles", JSONArray(importantFiles))
        put("updatedAt", updatedAt)
    }.toString()

    companion object {
        val EMPTY = VibeAgentMemory()

        fun fromJson(raw: String?): VibeAgentMemory {
            if (raw.isNullOrBlank()) return EMPTY
            return runCatching {
                val obj = JSONObject(raw)
                fun strings(key: String): List<String> {
                    val arr = obj.optJSONArray(key) ?: return emptyList()
                    return (0 until arr.length()).map { arr.optString(it) }
                        .filter { it.isNotBlank() }
                }
                VibeAgentMemory(
                    summary = obj.optString("summary", ""),
                    decisions = strings("decisions"),
                    importantFiles = strings("importantFiles"),
                    updatedAt = obj.optLong("updatedAt", 0L)
                )
            }.getOrDefault(EMPTY)
        }
    }
}

object VibeAgentMemoryStore {

    /**
     * ذاكرة مشروع الجلسة النشطة — إن وجد مشروع فايب كودينج مرتبط
     * بالجلسة النشطة وله ذاكرة غير فارغة؛ وإلا null.
     */
    suspend fun loadForActiveSession(context: Context): VibeAgentMemory? =
        withContext(Dispatchers.IO) {
            val sessionId = AgentChatSessionManager.activeSessionId.value
                ?: return@withContext null
            val project = VibeCodingProjects.list(context)
                .firstOrNull { it.sessionId == sessionId }
                ?: return@withContext null
            project.memory.takeIf { !it.isEmpty }
        }

    /** كتلة سياق تُلحق بموجه المهمة قبل إرساله للنموذج؛ فارغة إن لا ذاكرة.
     * محدودة الحجم: الملخص حتى 2000 محرف، وأول 20 قراراً و20 ملفاً —
     * بلا حد كان الملخص (بلا حد أقصى في المحرر) يضخّم الموجه بلا سقف. */
    fun contextBlock(memory: VibeAgentMemory): String {
        if (memory.isEmpty) return ""
        return buildString {
            append("— سياق المشروع المحفوظ (ذاكرة الوكيل) —\n")
            if (memory.summary.isNotBlank()) {
                append("ملخص الجلسة: ${memory.summary.take(2000)}")
                if (memory.summary.length > 2000) append("…")
                append("\n")
            }
            if (memory.decisions.isNotEmpty()) {
                append("قرارات المشروع:\n")
                memory.decisions.take(20).forEach { append("• $it\n") }
                if (memory.decisions.size > 20) append("… (و${memory.decisions.size - 20} قرارات إضافية)\n")
            }
            if (memory.importantFiles.isNotEmpty()) {
                append("ملفات مهمة: ${memory.importantFiles.take(20).joinToString(", ")}")
                if (memory.importantFiles.size > 20) append(" (و${memory.importantFiles.size - 20} ملفات إضافية)")
                append("\n")
            }
            append("— نهاية السياق المحفوظ —")
        }
    }
}
