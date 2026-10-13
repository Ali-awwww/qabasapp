package com.qabas.app

import android.content.Context
import android.util.Log
import org.json.JSONArray

/**
 * مسبح مفاتيح كل خدمة: عدة مفاتيح احتياطية + تبديل يدوي + تجاوز تلقائي.
 *
 * - التخزين: "<prefsKey>_pool" (JSONArray) + "<prefsKey>_active_idx" في qabas_prefs.
 * - المفتاح المفرد القديم يُهاجَر تلقائياً كأول عنصر — بلا كسر لأي قارئ قديم.
 * - كل طفرة تعكس المفتاح النشط على المفتاح المفرد (write-through) لتوافق النسخ الاحتياطي.
 * - التوكنات هنا مفاتيح AI مدخلة من المستخدم نفسه — لا أسرار نظام.
 */
object KeyPoolManager {
    private const val TAG = "KeyPoolManager"
    private const val PREFS = "qabas_prefs"
    private const val POOL_SUFFIX = "_pool"
    private const val ACTIVE_SUFFIX = "_active_idx"

    private fun prefs() =
        AppServices.appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun isReal(value: String?): Boolean {
        val t = value?.trim() ?: return false
        return t.isNotEmpty() && !t.lowercase().startsWith("your_") && t.length >= 5
    }

    /**
     * تنظيف صارم للمفاتيح: يزيل كل المحارف غير المرئية التي يلتقطها النسخ
     * (علامات اتجاه RTL/LTR ‏U+200E ‏U+200F، مسافات صلبة U+00A0، كل فئة Cf)
     * والتي يتجاهلها trim() فتفسد ترويسة Authorization بصمت.
     */
    fun sanitizeApiKey(raw: String): String {
        return raw
            .replace(Regex("[\\p{Cf}\\u00A0\\u2000-\\u200B]"), "")
            .trim()
    }

    /** كل المفاتيح: النشط أولاً ثم البقية (بدون تكرار)، ثم الإرثي، ثم BuildConfig. */
    fun orderedKeys(prefsKey: String, legacyFallback: String = ""): List<String> {
        val pool = getKeys(prefsKey).toMutableList()
        val active = getActiveKey(prefsKey)
        val ordered = linkedSetOf<String>()
        if (isReal(active)) ordered.add(active.trim())
        pool.forEach { if (isReal(it)) ordered.add(it.trim()) }
        if (isReal(legacyFallback)) ordered.add(legacyFallback.trim())
        return ordered.toList()
    }

    fun getKeys(prefsKey: String): List<String> {
        val p = prefs()
        val raw = p.getString(prefsKey + POOL_SUFFIX, null)
        if (!raw.isNullOrBlank()) {
            return runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { arr.optString(it, "").trim() }.filter { it.isNotBlank() }
            }.getOrDefault(emptyList())
        }
        // ترحيل: المفرد القديم يصبح أول عنصر
        val legacy = p.getString(prefsKey, "").orEmpty().trim()
        if (isReal(legacy)) {
            savePool(prefsKey, listOf(legacy), 0)
            return listOf(legacy)
        }
        return emptyList()
    }

    fun getActiveIndex(prefsKey: String): Int {
        val keys = getKeys(prefsKey)
        if (keys.isEmpty()) return 0
        return prefs().getInt(prefsKey + ACTIVE_SUFFIX, 0).coerceIn(0, keys.size - 1)
    }

    /** المفتاح النشط — أو "" عند غيابه (لا يقرأ الإرثي هنا؛ KeyVault يتكفل بالسلسلة). */
    fun getActiveKey(prefsKey: String): String {
        val keys = getKeys(prefsKey)
        if (keys.isEmpty()) return ""
        return keys[getActiveIndex(prefsKey)]
    }

    /** تعيين المفرد (حفظ/لصق/استيراد): يكتب الإرثي ويعيد ضبط المسبح عليه. */
    fun setSingleKey(prefsKey: String, value: String) {
        val p = prefs()
        val clean = value.trim()
        p.edit().putString(prefsKey, clean).apply()
        if (isReal(clean)) {
            savePool(prefsKey, listOf(clean), 0)
        } else {
            p.edit().remove(prefsKey + POOL_SUFFIX).remove(prefsKey + ACTIVE_SUFFIX).apply()
        }
    }

    fun clearAll(prefsKey: String) {
        prefs().edit()
            .remove(prefsKey)
            .remove(prefsKey + POOL_SUFFIX)
            .remove(prefsKey + ACTIVE_SUFFIX)
            .apply()
    }

    /** إضافة مفتاح احتياطي — false عند التكرار أو الفراغ. */
    fun addKey(prefsKey: String, value: String): Boolean {
        val clean = value.trim()
        if (!isReal(clean)) return false
        val keys = getKeys(prefsKey).toMutableList()
        if (keys.any { it == clean }) return false
        keys.add(clean)
        savePool(prefsKey, keys, getActiveIndex(prefsKey))
        return true
    }

    fun removeKeyAt(prefsKey: String, index: Int): Boolean {
        val keys = getKeys(prefsKey).toMutableList()
        if (index !in keys.indices) return false
        keys.removeAt(index)
        val active = getActiveIndex(prefsKey).coerceAtMost((keys.size - 1).coerceAtLeast(0))
        savePool(prefsKey, keys, active)
        // انعكاس النشط الجديد على المفرد
        val p = prefs()
        if (keys.isEmpty()) {
            p.edit().remove(prefsKey).apply()
        } else {
            p.edit().putString(prefsKey, keys[active.coerceIn(keys.indices)]).apply()
        }
        return true
    }

    fun setActiveIndex(prefsKey: String, index: Int): Boolean {
        val keys = getKeys(prefsKey)
        if (index !in keys.indices) return false
        savePool(prefsKey, keys, index)
        prefs().edit().putString(prefsKey, keys[index]).apply()
        return true
    }

    /** ترقية الفائز: اجعله النشط لتبدأ به المحاولات القادمة. */
    fun promoteKey(prefsKey: String, key: String) {
        val idx = getKeys(prefsKey).indexOf(key.trim())
        if (idx >= 0) setActiveIndex(prefsKey, idx)
    }

    /**
     * التدوير التلقائي عند الفشل: يتقدم للبديل التالي دائرياً ويعيده.
     * "" تعني لا بديل متاحاً.
     */
    fun rotateOnFailure(prefsKey: String): String {
        val keys = getKeys(prefsKey)
        if (keys.size < 2) return ""
        val next = (getActiveIndex(prefsKey) + 1) % keys.size
        setActiveIndex(prefsKey, next)
        Log.i(TAG, "rotated $prefsKey to index $next")
        return keys[next]
    }

    /**
     * التجاوز التلقائي العام: يجرب النشط ثم البقية، ويرقّي الفائز.
     * null تعني فشل الجميع — يترك القرار للمنادي (fallback محلي).
     */
    suspend fun <T> withFailover(
        prefsKey: String,
        legacyFallback: String = "",
        block: suspend (String) -> T?
    ): T? {
        for (key in orderedKeys(prefsKey, legacyFallback)) {
            val result = try { block(key) } catch (_: Exception) { null }
            if (result != null) {
                promoteKey(prefsKey, key)
                return result
            }
        }
        return null
    }

    private fun savePool(prefsKey: String, keys: List<String>, activeIdx: Int) {
        val arr = JSONArray()
        keys.forEach { arr.put(it) }
        prefs().edit()
            .putString(prefsKey + POOL_SUFFIX, arr.toString())
            .putInt(prefsKey + ACTIVE_SUFFIX, activeIdx.coerceAtLeast(0))
            .apply()
    }
}
