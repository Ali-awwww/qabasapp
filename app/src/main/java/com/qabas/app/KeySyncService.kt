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
        "gemini_key", "groq_key", "openai_key", "openrouter_key", "huggingface_key",
        "azure_speech_key", "azure_speech_region", "elevenlabs_key",
        "pexels_key", "pixabay_key"
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

    fun pushToCloud(context: Context, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!SupabaseConfig.isConfigured) {
            onResult?.invoke(false, "Supabase غير مُعد")
            return
        }

        scope.launch {
            try {
                val keyPrefs = AgentPrefs.instance(context)
                getUserId() ?: run {
                    withContext(Dispatchers.Main) { onResult?.invoke(false, "سجّل الدخول أولاً لمزامنة المفاتيح بأمان") }
                    return@launch
                }
                val keysToSync = KEY_FIELDS.mapNotNull { field ->
                    val value = keyPrefs.getString(field, "") ?: ""
                    if (value.isNotBlank() && !value.startsWith("your_")) {
                        field to value
                    } else null
                }

                if (keysToSync.isEmpty()) {
                    withContext(Dispatchers.Main) { onResult?.invoke(false, "لا توجد مفاتيح لل-sync") }
                    return@launch
                }

                val result = callVault(buildJsonObject {
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
                if (!result.ok) {
                    throw IllegalStateException(result.error ?: "فشل خزنة المفاتيح")
                }

                withContext(Dispatchers.Main) {
                    onResult?.invoke(true, "تم رفع ${result.count} مفتاحاً إلى الخزنة المشفرة ☁️✅")
                }
            } catch (e: Exception) {
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
                val keyPrefs = AgentPrefs.instance(context)
                getUserId() ?: run {
                    withContext(Dispatchers.Main) { onResult?.invoke(false, "سجّل الدخول أولاً لاستعادة المفاتيح بأمان") }
                    return@launch
                }

                val result = callVault(buildJsonObject { put("action", "get") })

                if (!result.ok || result.keys.isEmpty()) {
                    withContext(Dispatchers.Main) { onResult?.invoke(false, "لا توجد مفاتيح محفوظة في السحابة") }
                    return@launch
                }

                var imported = 0
                val editor = keyPrefs.edit()
                result.keys.forEach { synced ->
                    if (synced.value.isNotBlank() && synced.service in KEY_FIELDS) {
                        editor.putString(synced.service, synced.value)
                        imported++
                    }
                }
                editor.apply()

                withContext(Dispatchers.Main) {
                    onResult?.invoke(true, "تم سحب $imported مفتاح من السحابة ☁️📥")
                }
            } catch (e: Exception) {
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
                // First pull, then push (merge strategy: newest wins)
                pullFromCloud(context) { pullSuccess, pullMsg ->
                    if (pullSuccess) {
                        pushToCloud(context) { pushSuccess, pushMsg ->
                            onResult?.invoke(pushSuccess, "$pullMsg\n$pushMsg")
                        }
                    } else {
                        // Pull failed, just push local keys
                        pushToCloud(context) { pushSuccess, pushMsg ->
                            onResult?.invoke(pushSuccess, "سحب فاشل: $pullMsg\n$pushMsg")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult?.invoke(false, "خطأ في المزامنة: ${e.message}")
                }
            }
        }
    }
}
