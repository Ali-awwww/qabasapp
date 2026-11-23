package com.qabas.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * أوامر محفوظة يضيفها المستخدم فعلاً (12.15): إضافة/حذف/حفظ في
 * SharedPreferences عادية «qabas_vibe_commands_v1» (لا أسرار فيها).
 * تحل محل القائمة الثابتة «أوامر جاهزة» في AiStudioAgentScreen —
 * الأوامر الأربعة السابقة تُزرع مرة واحدة عند أول تشغيل فلا يفقد
 * المستخدم ما اعتاده، وتصبح قابلة للحذف كغيرها.
 */
data class VibeSavedCommand(
    val id: String,
    val label: String,
    val prompt: String,
    val createdAt: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("prompt", prompt)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): VibeSavedCommand? = runCatching {
            VibeSavedCommand(
                id = json.optString("id"),
                label = json.optString("label"),
                prompt = json.optString("prompt"),
                createdAt = json.optLong("createdAt", 0L)
            ).takeIf { it.id.isNotBlank() && it.label.isNotBlank() && it.prompt.isNotBlank() }
        }.getOrNull()
    }
}

object VibeSavedCommands {
    private const val PREFS_NAME = "qabas_vibe_commands_v1"
    private const val KEY_COMMANDS = "commands_json"
    private const val KEY_SEEDED = "seeded_v1"

    /** الأوامر الأربعة الجاهزة السابقة — تُزرع مرة واحدة فقط. */
    val SEED_COMMANDS: List<Pair<String, String>> = listOf(
        "تحليل معماري شامل 🏛️" to "قم بتحليل بنية التطبيق المعمارية وتقديم تقرير بالتحسينات الممكنة",
        "فحص أمني وثغرات 🛡️" to "افحص الكود بحثاً عن أي تسريبات للبيانات أو ثغرات أمنية",
        "تحسين سرعة الأداء ⚡" to "اقترح تحسينات لتقليل وقت التجميع واستهلاك الذاكرة",
        "إصلاح أخطاء التجميع 🩺" to "قم بتشغيل الفحص التلقائي وإصلاح أي استدعاءات مفقودة"
    )

    fun list(context: Context): List<VibeSavedCommand> {
        ensureSeeded(context)
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_COMMANDS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                runCatching { VibeSavedCommand.fromJson(arr.getJSONObject(i)) }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    /** يضيف أمراً ويعيده، أو null إن كانت التسمية أو الأمر فارغين. */
    fun add(context: Context, label: String, prompt: String): VibeSavedCommand? {
        val trimmedLabel = label.trim()
        val trimmedPrompt = prompt.trim()
        if (trimmedLabel.isBlank() || trimmedPrompt.isBlank()) return null
        val command = VibeSavedCommand(
            id = java.util.UUID.randomUUID().toString(),
            label = trimmedLabel,
            prompt = trimmedPrompt,
            createdAt = System.currentTimeMillis()
        )
        save(context, list(context) + command)
        return command
    }

    fun delete(context: Context, id: String) {
        save(context, list(context).filterNot { it.id == id })
    }

    private fun ensureSeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        val now = System.currentTimeMillis()
        val seeded = SEED_COMMANDS.map { (label, prompt) ->
            VibeSavedCommand(
                id = java.util.UUID.randomUUID().toString(),
                label = label,
                prompt = prompt,
                createdAt = now
            )
        }
        save(context, seeded)
        prefs.edit().putBoolean(KEY_SEEDED, true).apply()
    }

    private fun save(context: Context, all: List<VibeSavedCommand>) {
        val arr = JSONArray()
        all.forEach { arr.put(it.toJson()) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_COMMANDS, arr.toString()).apply()
    }
}
