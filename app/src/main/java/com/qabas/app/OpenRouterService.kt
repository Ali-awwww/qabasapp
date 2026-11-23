package com.qabas.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * OpenRouter — بوابة نماذج مجانية حقيقية (Llama/Gemma/Qwen بصيغة `:free`).
 * المفتاح يُقرأ من `qabas_prefs` ثم BuildConfig — ويُستخدم في توليد خطط
 * الطلبات قبل السقوط على المخطط المحلي. أي فشل = null (لا نجاح وهمي).
 */
object OpenRouterService {

    private const val TAG = "OpenRouterService"
    const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"

    /** نماذج مجانية تُجرَّب بالترتيب حتى ينجح أحدها. */
    val FREE_MODELS = listOf(
        "poolside/laguna-s-2.1:free",
        "google/gemma-4-31b-it:free",
        "cohere/north-mini-code:free"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** يولّد خطة برمجة عربية عبر OpenRouter — يُعيد null عند أي فشل. */
    suspend fun generatePlan(title: String, description: String, apiKey: String): String? =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext null
            val prompt = """
                أنت خبير هندسة برمجيات Android (Kotlin + Jetpack Compose).
                فكرة المشروع: العنوان: $title | التفاصيل: $description
                اكتب خطة عمل عربية احترافية: إعدادات مقترحة، تحليل المشروع، الهندسة والشاشات،
                ثم 3 برومبتات جاهزة للنسخ (بيانات/واجهة/منطق)، وقائمة اختبار قبل التسليم.
            """.trimIndent()
            for (model in FREE_MODELS) {
                val text = tryModel(apiKey, model, prompt)
                if (!text.isNullOrBlank()) {
                    Log.d(TAG, "Plan via $model (${text.length} chars)")
                    return@withContext "☁️ خطة OpenRouter ($model)\n━━━━━━━━━━━━━━\n\n$text"
                }
            }
            null
        }

    private fun tryModel(apiKey: String, model: String, prompt: String): String? {
        return try {
            val body = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("temperature", 0.7)
                put("max_tokens", 3000)
            }.toString().toRequestBody("application/json".toMediaType())
            val httpRequest = Request.Builder()
                .url(ENDPOINT)
                .header("Authorization", "Bearer $apiKey")
                .header("HTTP-Referer", "https://qabas.app")
                .header("X-Title", "Muslim AI Assistant")
                .post(body)
                .build()
            val response = client.newCall(httpRequest).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "$model HTTP ${response.code}")
                return null
            }
            val json = JSONObject(response.body?.string().orEmpty())
            json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content", "")
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "$model failed: ${e.message}")
            null
        }
    }

    /** محادثة متعددة الأدوار بتاريخ كامل — تُستخدم في المساعد العام. */
    suspend fun chatWithHistory(
        apiKey: String,
        system: String,
        history: List<Pair<Boolean, String>>,
        model: String? = null
    ): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || history.isEmpty()) return@withContext null
        val models = if (model.isNullOrBlank()) FREE_MODELS else listOf(model)
        for (m in models) {
            val text = tryHistory(apiKey, m, system, history)
            if (!text.isNullOrBlank()) return@withContext text
        }
        null
    }

    private fun tryHistory(
        apiKey: String,
        model: String,
        system: String,
        history: List<Pair<Boolean, String>>
    ): String? {
        return try {
            val body = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", system)
                    })
                    history.takeLast(10).forEach { (isUser, text) ->
                        put(JSONObject().apply {
                            put("role", if (isUser) "user" else "assistant")
                            put("content", text.take(2000))
                        })
                    }
                })
                put("temperature", 0.7)
                put("max_tokens", 2000)
            }.toString().toRequestBody("application/json".toMediaType())
            val httpRequest = Request.Builder()
                .url(ENDPOINT)
                .header("Authorization", "Bearer $apiKey")
                .header("HTTP-Referer", "https://qabas.app")
                .header("X-Title", "Muslim AI Assistant")
                .post(body)
                .build()
            val response = client.newCall(httpRequest).execute()
            if (!response.isSuccessful) return null
            JSONObject(response.body?.string().orEmpty())
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content", "")
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    /** محادثة عامة (تُستخدم في المحرر): system + prompt — تُعيد النص أو null. */
    suspend fun chat(apiKey: String, system: String, prompt: String, maxTokens: Int = 6000): String? =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext null
            for (model in FREE_MODELS) {
                val text = tryChat(apiKey, model, system, prompt, maxTokens)
                if (!text.isNullOrBlank()) return@withContext text
            }
            null
        }

    private fun tryChat(apiKey: String, model: String, system: String, prompt: String, maxTokens: Int): String? {
        return try {
            val body = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", system)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("temperature", 0.7)
                put("max_tokens", maxTokens)
            }.toString().toRequestBody("application/json".toMediaType())
            val httpRequest = Request.Builder()
                .url(ENDPOINT)
                .header("Authorization", "Bearer $apiKey")
                .header("HTTP-Referer", "https://qabas.app")
                .header("X-Title", "Muslim AI Assistant")
                .post(body)
                .build()
            val response = client.newCall(httpRequest).execute()
            if (!response.isSuccessful) return null
            JSONObject(response.body?.string().orEmpty())
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content", "")
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }


    /** يفكك رد النموذج إلى (مسار ← كود) حسب صيغة FILE:. */

    /** محادثة بالأدوات: النموذج يستدعي دوال التطبيق الحقيقية حتى 4 جولات.
     * (12.6) عند تمرير [context] تُفرض مفاتيح تفعيل الأدوات: المعطّلة لا
     * تُرسل إلى النموذج أصلاً، وأي استدعاء يمر عبر [AgentToolGate] —
     * والأدوات المدمرة ترفض بلا موافقة بشرية مسبقة من تبويب الأدوات.
     */
    suspend fun chatWithTools(
        apiKey: String,
        system: String,
        history: List<Pair<Boolean, String>>,
        tools: List<AiTools.ToolDef>,
        model: String? = null,
        maxTurns: Int = 4,
        context: Context? = null
    ): Pair<String, List<String>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || history.isEmpty()) return@withContext Pair("", emptyList())
        val useModel = model?.takeIf { it.isNotBlank() } ?: FREE_MODELS.first()
        // الأدوات المعطّلة من تبويب الأدوات لا تُعرض على النموذج أصلاً.
        val enabledTools = if (context != null) {
            tools.filter { AgentPrefs.isToolEnabled(context, it.name) }
        } else tools
        val usedTools = mutableListOf<String>()
        val convo = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", system))
            history.takeLast(10).forEach { (isUser, text) ->
                put(JSONObject().put("role", if (isUser) "user" else "assistant").put("content", text.take(2000)))
            }
        }
        var finalText = ""
        repeat(maxTurns) {
            val body = JSONObject().apply {
                put("model", useModel)
                put("messages", convo)
                put("tools", AiTools.toApiJson(enabledTools))
                put("temperature", 0.7)
                put("max_tokens", 2000)
            }.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url(ENDPOINT)
                .header("Authorization", "Bearer $apiKey")
                .header("HTTP-Referer", "https://qabas.app")
                .header("X-Title", "Muslim AI Assistant")
                .post(body)
                .build()
            val resp = try {
                client.newCall(req).execute()
            } catch (_: Exception) {
                return@withContext Pair("", usedTools)
            }
            if (!resp.isSuccessful) return@withContext Pair("", usedTools)
            val msg = JSONObject(resp.body?.string().orEmpty())
                .optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                ?: return@withContext Pair("", usedTools)
            val calls = msg.optJSONArray("tool_calls")
            if (calls == null || calls.length() == 0) {
                finalText = msg.optString("content", "")
                return@withContext Pair(finalText, usedTools)
            }
            convo.put(JSONObject().apply {
                put("role", "assistant")
                put("content", msg.optString("content", ""))
                put("tool_calls", calls)
            })
            for (i in 0 until calls.length()) {
                val call = calls.getJSONObject(i)
                val fn = call.optJSONObject("function") ?: continue
                val name = fn.optString("name")
                val args = runCatching { JSONObject(fn.optString("arguments", "{}")) }.getOrDefault(JSONObject())
                val def = enabledTools.find { it.name == name }
                val result = def?.let {
                    // (12.6/12.16) فحص ما قبل التنفيذ: تفعيل + نطاق + موافقة مدمرة.
                    val refusal = context?.let { ctx ->
                        AgentToolGate.checkBeforeRun(
                            ctx, name,
                            path = args.optString("path").takeIf { it.isNotBlank() }
                        )
                    }
                    if (refusal != null) refusal else {
                        usedTools.add(name)
                        runCatching { it.run(args) }.getOrDefault("تعذر تنفيذ الأداة $name.")
                    }
                } ?: "أداة غير معروفة: $name."
                convo.put(JSONObject().apply {
                    put("role", "tool")
                    put("tool_call_id", call.optString("id"))
                    put("content", result.take(3000))
                })
            }
        }
        Pair(finalText.ifBlank { "نفّذت الأدوات ولم يتبقَّ رد نصي." }, usedTools)
    }

    /** فحص سريع لصحة المفتاح — GET /models (خفيف بلا تكلفة). */
    suspend fun validateKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val httpRequest = Request.Builder()
                .url("https://openrouter.ai/api/v1/models")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            val response = client.newCall(httpRequest).execute()
            response.isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    // ── متغيرات مُجمّعة: تجاوز تلقائي لمفاتيح المسبح (الفائز يُرقَّى) ──

    suspend fun chatWithHistoryPooled(
        system: String,
        history: List<Pair<Boolean, String>>,
        model: String? = null
    ): String? = KeyPoolManager.withFailover("openrouter_key", KeyVault.openrouter) { k ->
        chatWithHistory(k, system, history, model)
    }

    suspend fun generatePlanPooled(title: String, description: String = ""): String? =
        KeyPoolManager.withFailover("openrouter_key", KeyVault.openrouter) { k ->
            generatePlan(title, description, k)
        }

    suspend fun chatWithToolsPooled(
        system: String,
        history: List<Pair<Boolean, String>>,
        tools: List<AiTools.ToolDef>,
        model: String? = null,
        maxTurns: Int = 4,
        context: Context? = null
    ): Pair<String, List<String>> =
        KeyPoolManager.withFailover("openrouter_key", KeyVault.openrouter) { k ->
            chatWithTools(k, system, history, tools, model, maxTurns, context)
                .takeIf { it.first.isNotBlank() }
        } ?: Pair("", emptyList())
}
