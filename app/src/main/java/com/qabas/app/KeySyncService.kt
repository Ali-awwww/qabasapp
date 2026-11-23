package com.qabas.app

import android.content.Context
import android.util.Log
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object KeySyncService {

    private const val TAG = "KeySyncService"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Serializable
    data class VaultKey(
        val service: String = "",
        val value: String = "",
        val updated_at: String = ""
    )

    @Serializable
    data class VaultResponse(
        val ok: Boolean = false,
        val count: Int = 0,
        val keys: List<VaultKey> = emptyList(),
        val error: String? = null
    )

    private val KEY_FIELDS = listOf(
        "gemini_key", "groq_key", "openrouter_key", "pixabay_key"
    )

    /** Cloud key sync is strictly account-scoped; never derive ownership from email/device prefs. */
    private fun getUserId(): String? = SupabaseServices.Auth.getCurrentUserId()

    private suspend fun callVault(payload: JsonObject): VaultResponse {
        val response = SupabaseConfig.client.functions.invoke(
            function = "qabas-key-vault",
            body = payload
        )
        return response.body()
    }

    private fun parseUpdatedAt(iso: String): Long =
        runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull() ?: 0L

    /**
     * حالة السحابة (خدمة → صفها بما فيه updated_at) أو null عند فشل الجلب.
     * الدالة الخادم تُعيد updated_at لكل مفتاح (انظر index.ts: put يختم
     * الآن، وget يختار service, encrypted_value, updated_at) — كان
     * التطبيق يتجاهل الحقل تماماً.
     */
    private suspend fun fetchCloudState(): Map<String, VaultKey>? {
        val result = runCatching { callVault(buildJsonObject { put("action", "get") }) }.getOrNull()
        if (result == null || !result.ok) return null
        return result.keys.associateBy { it.service }
    }

    /**
     * دمج السحب: يستورد من السحابة فقط ما هو أحدث من المحلي (أو ما لا
     * محلي له). المفتاح المحلي الأحدث يُحفظ ولا يُمس؛ والمفتاح المحلي
     * السابق لتتبع الطوابع (updated_at = 0) ومختلف القيمة يُبقى محلياً
     * ويُختم الآن — فالنسخة التي بين يدي المستخدم أولى من نسخة سحابية
     * قد تكون أقدم ولا دليل على عكسها. المستورد يُختم بطابع السحابة
     * نفسه لا بالآن، ليبقى المقارن مستقراً في المزامنة التالية.
     */
    private fun mergePull(cloud: Map<String, VaultKey>): Int {
        var imported = 0
        cloud.values.forEach { synced ->
            if (synced.service !in KEY_FIELDS) return@forEach
            val cloudValue = KeyPoolManager.sanitizeApiKey(synced.value)
            if (cloudValue.isBlank()) return@forEach
            val localValue = KeyVault.effective(synced.service)
            val localUpdated = KeyPoolManager.getUpdatedAtMillis(synced.service)
            val cloudUpdated = parseUpdatedAt(synced.updated_at)
            when {
                localValue.isBlank() -> {
                    KeyPoolManager.setSingleKey(synced.service, cloudValue, cloudUpdated)
                    imported++
                }
                localValue == cloudValue -> {
                    if (cloudUpdated > localUpdated) {
                        KeyPoolManager.setUpdatedAtMillis(synced.service, cloudUpdated)
                    }
                }
                localUpdated == 0L -> {
                    // مفتاح سابق للتتبع: لا دليل على قدمه — يُبقى ويُختم الآن.
                    KeyPoolManager.setUpdatedAtMillis(synced.service, System.currentTimeMillis())
                }
                cloudUpdated > localUpdated -> {
                    KeyPoolManager.setSingleKey(synced.service, cloudValue, cloudUpdated)
                    imported++
                }
                // else: المحلي أحدث — يُترك، والرفع سينقله للسحابة.
            }
        }
        return imported
    }

    /**
     * ما يجب رفعه: المفاتيح الفعّالة محلياً التي تغيب عن السحابة أو
     * تختلف قيمتها عنها وكان طابعها المحلي أحدث من طابع السحابة.
     * المفتاح السابق للتتبع (طابع 0) ومختلف القيمة يُرفع — النسخة
     * المحلية هي التي بين يدي المستخدم (انظر mergePull).
     */
    private fun keysToPush(cloud: Map<String, VaultKey>): List<Pair<String, String>> {
        return KEY_FIELDS.mapNotNull { field ->
            val value = KeyVault.effective(field)
            if (value.isBlank()) return@mapNotNull null
            val cloudKey = cloud[field] ?: return@mapNotNull field to value
            val cloudValue = KeyPoolManager.sanitizeApiKey(cloudKey.value)
            if (value == cloudValue) return@mapNotNull null
            val localUpdated = KeyPoolManager.getUpdatedAtMillis(field)
            val cloudUpdated = parseUpdatedAt(cloudKey.updated_at)
            if (localUpdated == 0L || localUpdated > cloudUpdated) field to value else null
        }
    }

    private suspend fun putKeys(keysToSync: List<Pair<String, String>>): VaultResponse {
        return callVault(buildJsonObject {
            put("action", "put")
            put("keys", buildJsonArray {
                keysToSync.forEach { (service, value) ->
                    add(buildJsonObject {
                        put("service", service)
                        put("value", value)
                    })
                }
            })
        })
    }

    fun pushToCloud(context: Context, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!SupabaseConfig.isConfigured) {
            onResult?.invoke(false, "Supabase غير مُعد")
            return
        }

        scope.launch {
            try {
                getUserId() ?: run {
                    withContext(Dispatchers.Main) { onResult?.invoke(false, "سجّل الدخول أولاً لمزامنة المفاتيح بأمان") }
                    return@launch
                }
                // حالة السحابة أولاً لرفع الأحدث محلياً فقط؛ وعند تعذر
                // جلبها يُرفع كل المحلي (سلوك الرفع الصريح القديم).
                val cloud = fetchCloudState()
                val keysToSync = if (cloud == null) {
                    KEY_FIELDS.mapNotNull { field ->
                        val value = KeyVault.effective(field)
                        if (value.isNotBlank()) field to value else null
                    }
                } else {
                    keysToPush(cloud)
                }

                if (keysToSync.isEmpty()) {
                    withContext(Dispatchers.Main) { onResult?.invoke(true, "المفاتيح متزامنة أصلاً — لا جديد للرفع ☁️✅") }
                    return@launch
                }

                val result = putKeys(keysToSync)
                if (!result.ok) {
                    throw IllegalStateException(result.error ?: "فشل خزنة المفاتيح")
                }

                withContext(Dispatchers.Main) {
                    onResult?.invoke(true, "تم رفع ${keysToSync.size} مفتاحاً إلى الخزنة المشفرة ☁️✅")
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                Log.e(TAG, "Push failed", e)
                withContext(Dispatchers.Main) {
                    onResult?.invoke(false, "فشل الرفع: ${e.message ?: "خطأ غير معروف"}")
                }
            }
        }
    }

    fun pullFromCloud(context: Context, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!SupabaseConfig.isConfigured) {
            onResult?.invoke(false, "Supabase غير مُعد")
            return
        }

        scope.launch {
            try {
                getUserId() ?: run {
                    withContext(Dispatchers.Main) { onResult?.invoke(false, "سجّل الدخول أولاً لاستعادة المفاتيح بأمان") }
                    return@launch
                }

                val cloud = fetchCloudState()
                if (cloud == null || cloud.isEmpty()) {
                    withContext(Dispatchers.Main) { onResult?.invoke(false, "لا توجد مفاتيح محفوظة في السحابة") }
                    return@launch
                }

                val imported = mergePull(cloud)

                withContext(Dispatchers.Main) {
                    onResult?.invoke(
                        true,
                        if (imported > 0) "تم سحب $imported مفتاح من السحابة ☁️📥"
                        else "السحابة بلا مفاتيح أحدث من مفاتيحك — لم يُستبدل شيء"
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                Log.e(TAG, "Pull failed", e)
                withContext(Dispatchers.Main) {
                    onResult?.invoke(false, "فشل السحب: ${e.message ?: "خطأ غير معروف"}")
                }
            }
        }
    }

    fun syncBidirectional(context: Context, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!SupabaseConfig.isConfigured) {
            onResult?.invoke(false, "Supabase غير مُعد")
            return
        }

        scope.launch {
            try {
                getUserId() ?: run {
                    withContext(Dispatchers.Main) { onResult?.invoke(false, "سجّل الدخول أولاً لمزامنة المفاتيح بأمان") }
                    return@launch
                }
                // دمج حقيقي «الأحدث يفوز» في جلب واحد: سحب الأحدث من
                // السحابة أولاً، ثم رفع الأحدث محلياً — متسلسلاً في
                // كوروتين واحد (لا استدعاءات متداخلة بلا انتظار).
                val cloud = fetchCloudState()
                if (cloud == null) {
                    withContext(Dispatchers.Main) { onResult?.invoke(false, "تعذر جلب حالة السحابة — تحقق من الاتصال") }
                    return@launch
                }
                val imported = mergePull(cloud)
                val toPush = keysToPush(cloud)
                var pushed = 0
                if (toPush.isNotEmpty()) {
                    val result = putKeys(toPush)
                    if (!result.ok) {
                        throw IllegalStateException(result.error ?: "فشل خزنة المفاتيح")
                    }
                    pushed = toPush.size
                }
                withContext(Dispatchers.Main) {
                    onResult?.invoke(
                        true,
                        "تمت المزامنة: سُحب $imported مفتاحاً أحدث من السحابة، ورُفع $pushed مفتاحاً أحدث محلياً ☁️✅"
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
                withContext(Dispatchers.Main) {
                    onResult?.invoke(false, "خطأ في المزامنة: ${e.message}")
                }
            }
        }
    }
}
