package com.qabas.app

import android.content.Context
import org.json.JSONObject

/**
 * سجل زمن استجابة كل مفتاح: يخزن آخر latency وصلاحية الاتصال لكل مفتاح
 * (ببصمة hash حتى لا تُحفظ المفاتيح نفسها)، ويُستخدم لترتيب المسبح حسب الأسرع المتصل.
 */
object KeyLatencyTracker {
    private const val PREFS = "qabas_prefs"
    private const val SUFFIX = "_key_latency"

    data class Entry(val latencyMs: Long, val connected: Boolean, val checkedAt: Long)

    private fun prefs() =
        AppServices.appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun fp(key: String): String = key.trim().hashCode().toString()

    fun record(prefsKey: String, apiKey: String, latencyMs: Long, connected: Boolean) {
        try {
            val p = prefs()
            val raw = p.getString(prefsKey + SUFFIX, "{}") ?: "{}"
            val obj = JSONObject(raw)
            obj.put(fp(apiKey), JSONObject().apply {
                put("ms", latencyMs)
                put("ok", connected)
                put("at", System.currentTimeMillis())
            })
            p.edit().putString(prefsKey + SUFFIX, obj.toString()).apply()
        } catch (_: Exception) {}
    }

    fun get(prefsKey: String, apiKey: String): Entry? {
        return try {
            val obj = JSONObject(prefs().getString(prefsKey + SUFFIX, "{}") ?: "{}")
            val e = obj.optJSONObject(fp(apiKey)) ?: return null
            Entry(e.optLong("ms", Long.MAX_VALUE), e.optBoolean("ok", false), e.optLong("at", 0))
        } catch (_: Exception) { null }
    }

    /** ترتيب المفاتيح: المتصل الأسرع أولاً، ثم المتصل الأبطأ، ثم غير المفحوص، ثم الفاشل. */
    fun ranked(prefsKey: String, keys: List<String>): List<String> {
        return keys.sortedWith(compareBy(
            { get(prefsKey, it)?.connected != true },
            { get(prefsKey, it)?.latencyMs ?: Long.MAX_VALUE }
        ))
    }
}
