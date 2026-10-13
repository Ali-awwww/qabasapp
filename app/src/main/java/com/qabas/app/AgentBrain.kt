package com.qabas.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object UniversalKeyBrain {
    val keyUpdateTrigger = kotlinx.coroutines.flow.MutableStateFlow(System.currentTimeMillis())

    fun notifyKeyChanged() {
        keyUpdateTrigger.value = System.currentTimeMillis()
    }

    fun detect(key: String): DetectedProvider {
        val k = key.trim()
        return when {
            k.startsWith("sk-or-", ignoreCase = true) -> DetectedProvider.OPENROUTER
            k.startsWith("sk-ant-", ignoreCase = true) -> DetectedProvider.ANTHROPIC
            k.startsWith("AIza", ignoreCase = true) -> DetectedProvider.GEMINI
            k.startsWith("gsk_", ignoreCase = true) -> DetectedProvider.GROQ
            k.startsWith("sk-", ignoreCase = true) -> DetectedProvider.OPENAI
            k.startsWith("hf_", ignoreCase = true) -> DetectedProvider.HUGGINGFACE
            k.startsWith("xai-", ignoreCase = true) -> DetectedProvider.GROQ
            else -> DetectedProvider.NONE
        }
    }

    fun getActiveEngine(): Pair<DetectedProvider, String> {
        val or = KeyVault.openrouter
        if (or.isNotBlank()) return Pair(DetectedProvider.OPENROUTER, or)

        val gem = KeyVault.gemini
        if (gem.isNotBlank()) return Pair(DetectedProvider.GEMINI, gem)

        val groq = KeyVault.groq
        if (groq.isNotBlank()) return Pair(DetectedProvider.GROQ, groq)

        val oai = KeyVault.openai
        if (oai.isNotBlank()) return Pair(DetectedProvider.OPENAI, oai)

        val hf = KeyVault.huggingface
        if (hf.isNotBlank()) return Pair(DetectedProvider.HUGGINGFACE, hf)

        return Pair(DetectedProvider.NONE, "")
    }

    fun saveKey(context: Context, rawKey: String): DetectedProvider {
        val k = normalizePastedKey(rawKey)
        val provider = detect(k)
        when (provider) {
            DetectedProvider.OPENROUTER -> KeyPoolManager.setSingleKey("openrouter_key", k)
            DetectedProvider.GEMINI -> KeyPoolManager.setSingleKey("gemini_key", k)
            DetectedProvider.GROQ -> KeyPoolManager.setSingleKey("groq_key", k)
            DetectedProvider.OPENAI -> KeyPoolManager.setSingleKey("openai_key", k)
            DetectedProvider.HUGGINGFACE -> KeyPoolManager.setSingleKey("huggingface_key", k)
            else -> KeyPoolManager.setSingleKey("openrouter_key", k)
        }
        notifyKeyChanged()
        return provider
    }

    fun prefsKeyFor(provider: DetectedProvider): String = when (provider) {
        DetectedProvider.GEMINI -> "gemini_key"
        DetectedProvider.GROQ -> "groq_key"
        DetectedProvider.OPENAI -> "openai_key"
        DetectedProvider.OPENROUTER -> "openrouter_key"
        else -> ""
    }

    /**
     * تنفيذ خطوة الوكيل مع تجاوز تلقائي: يجرّب مفاتيح المسبح مرتبة حسب الأسرع المتصل،
     * وعند فشل المفتاح النشط ينتقل للبديل فوراً ويرقّي الفائز — دون تدخل منك.
     * يُسجّل زمن كل محاولة لتحسين الترتيب القادم.
     */
    suspend fun executeRealAgentStepWithFailover(
        provider: DetectedProvider,
        prompt: String,
        systemInstruction: String,
        fallbackSingleKey: String = "",
        model: String? = null,
        stream: Boolean = false,
        onPartial: ((String) -> Unit)? = null
    ): Result<RealAgentResponse> = withContext(Dispatchers.IO) {
        val prefsKey = prefsKeyFor(provider)
        val keys = if (prefsKey.isNotBlank()) {
            KeyPoolManager.orderedKeysBySpeed(prefsKey, fallbackSingleKey)
        } else if (fallbackSingleKey.isNotBlank()) {
            listOf(fallbackSingleKey)
        } else emptyList()
        if (keys.isEmpty()) {
            return@withContext Result.failure(Exception("لا يوجد مفتاح — أدخل مفتاحاً من شاشة مفاتيح API"))
        }
        var lastError: Exception? = null
        for (k in keys) {
            val start = System.currentTimeMillis()
            val res = if (stream && onPartial != null) {
                AgentStreaming.stream(provider, k, model, prompt, systemInstruction, onPartial)
            } else {
                executeRealAgentStep(provider, k, prompt, systemInstruction, model)
            }
            val ms = System.currentTimeMillis() - start
            if (res.isSuccess) {
                if (prefsKey.isNotBlank()) {
                    KeyLatencyTracker.record(prefsKey, k, ms, true)
                    KeyPoolManager.promoteKey(prefsKey, k)
                }
                return@withContext res
            }
            if (prefsKey.isNotBlank()) {
                KeyLatencyTracker.record(prefsKey, k, ms, false)
                KeyPoolManager.rotateOnFailure(prefsKey)
            }
            lastError = res.exceptionOrNull() as? Exception ?: Exception("فشل المفتاح")
        }
        Result.failure(lastError ?: Exception("فشلت كل المفاتيح (${keys.size}) — تحقق من الاتصال والأرصدة"))
    }

    suspend fun executeRealAgentStep(
        provider: DetectedProvider,
        apiKey: String,
        prompt: String,
        systemInstruction: String,
        model: String? = null
    ): Result<RealAgentResponse> = withContext(Dispatchers.IO) {
        val client = SharedHttpClient.instance
        try {
            when (provider) {
                DetectedProvider.GEMINI -> {
                    val geminiModel = model?.takeIf { it.isNotBlank() } ?: "gemini-3.5-flash"
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$geminiModel:generateContent?key=$apiKey"
                    val jsonBody = org.json.JSONObject().apply {
                        put("contents", org.json.JSONArray().apply {
                            put(org.json.JSONObject().apply {
                                put("role", "user")
                                put("parts", org.json.JSONArray().apply {
                                    put(org.json.JSONObject().apply { put("text", prompt) })
                                })
                            })
                        })
                        put("systemInstruction", org.json.JSONObject().apply {
                            put("parts", org.json.JSONArray().apply {
                                put(org.json.JSONObject().apply { put("text", systemInstruction) })
                            })
                        })
                    }
                    val request = Request.Builder()
                        .url(url)
                        .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    val response = client.newCall(request).execute()
                    val bodyString = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        val errorDetail = try {
                            val errJson = org.json.JSONObject(bodyString)
                            errJson.optJSONObject("error")?.optString("message") ?: bodyString
                        } catch (e: Exception) { bodyString.ifBlank { "HTTP ${response.code} ${response.message}" } }
                        return@withContext Result.failure(Exception("خطأ من سيرفر Gemini (HTTP ${response.code}): $errorDetail"))
                    }
                    val json = org.json.JSONObject(bodyString)
                    val text = parseGeminiText(json)
                    if (text.isNullOrBlank()) {
                        return@withContext Result.failure(Exception("استجابة فارغة من سيرفر Gemini"))
                    }
                    val usageMeta = json.optJSONObject("usageMetadata")
                    val promptTokens = usageMeta?.optInt("promptTokenCount", 0) ?: 0
                    val candidateTokens = usageMeta?.optInt("candidatesTokenCount", 0) ?: 0
                    val totalTokens = usageMeta?.optInt("totalTokenCount", 0)?.takeIf { it > 0 } ?: (promptTokens + candidateTokens)
                    Result.success(RealAgentResponse(text, totalTokens.coerceAtLeast(150), promptTokens, candidateTokens))
                }
                DetectedProvider.OPENROUTER -> {
                    val url = "https://openrouter.ai/api/v1/chat/completions"
                    val jsonBody = org.json.JSONObject().apply {
                        put("model", model?.takeIf { it.isNotBlank() } ?: "anthropic/claude-sonnet-4.5")
                        put("messages", org.json.JSONArray().apply {
                            put(org.json.JSONObject().apply { put("role", "system"); put("content", systemInstruction) })
                            put(org.json.JSONObject().apply { put("role", "user"); put("content", prompt) })
                        })
                    }
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $apiKey")
                        .header("HTTP-Referer", "https://qabas.app")
                        .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    val response = client.newCall(request).execute()
                    val bodyString = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("خطأ من سيرفر OpenRouter (HTTP ${response.code}): $bodyString"))
                    }
                    val json = org.json.JSONObject(bodyString)
                    val text = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                    if (text.isNullOrBlank()) {
                        return@withContext Result.failure(Exception("استجابة فارغة من سيرفر OpenRouter"))
                    }
                    val usageObj = json.optJSONObject("usage")
                    val promptTokens = usageObj?.optInt("prompt_tokens", 0) ?: 0
                    val completionTokens = usageObj?.optInt("completion_tokens", 0) ?: 0
                    val totalTokens = usageObj?.optInt("total_tokens", 0)?.takeIf { it > 0 } ?: (promptTokens + completionTokens)
                    Result.success(RealAgentResponse(text, totalTokens.coerceAtLeast(120), promptTokens, completionTokens))
                }
                DetectedProvider.OPENAI -> {
                    val url = "https://api.openai.com/v1/chat/completions"
                    val jsonBody = org.json.JSONObject().apply {
                        put("model", "gpt-4o")
                        put("messages", org.json.JSONArray().apply {
                            put(org.json.JSONObject().apply { put("role", "system"); put("content", systemInstruction) })
                            put(org.json.JSONObject().apply { put("role", "user"); put("content", prompt) })
                        })
                    }
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $apiKey")
                        .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    val response = client.newCall(request).execute()
                    val bodyString = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("خطأ من سيرفر OpenAI (HTTP ${response.code}): $bodyString"))
                    }
                    val json = org.json.JSONObject(bodyString)
                    val text = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                    if (text.isNullOrBlank()) {
                        return@withContext Result.failure(Exception("استجابة فارغة من سيرفر OpenAI"))
                    }
                    val usageObj = json.optJSONObject("usage")
                    val promptTokens = usageObj?.optInt("prompt_tokens", 0) ?: 0
                    val completionTokens = usageObj?.optInt("completion_tokens", 0) ?: 0
                    val totalTokens = usageObj?.optInt("total_tokens", 0)?.takeIf { it > 0 } ?: (promptTokens + completionTokens)
                    Result.success(RealAgentResponse(text, totalTokens.coerceAtLeast(120), promptTokens, completionTokens))
                }
                DetectedProvider.GROQ -> {
                    val url = "https://api.groq.com/openai/v1/chat/completions"
                    val jsonBody = org.json.JSONObject().apply {
                        put("model", "llama-3.3-70b-versatile")
                        put("messages", org.json.JSONArray().apply {
                            put(org.json.JSONObject().apply { put("role", "system"); put("content", systemInstruction) })
                            put(org.json.JSONObject().apply { put("role", "user"); put("content", prompt) })
                        })
                    }
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $apiKey")
                        .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    val response = client.newCall(request).execute()
                    val bodyString = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("خطأ من سيرفر Groq (HTTP ${response.code}): $bodyString"))
                    }
                    val json = org.json.JSONObject(bodyString)
                    val text = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                    if (text.isNullOrBlank()) {
                        return@withContext Result.failure(Exception("استجابة فارغة من سيرفر Groq"))
                    }
                    val usageObj = json.optJSONObject("usage")
                    val promptTokens = usageObj?.optInt("prompt_tokens", 0) ?: 0
                    val completionTokens = usageObj?.optInt("completion_tokens", 0) ?: 0
                    val totalTokens = usageObj?.optInt("total_tokens", 0)?.takeIf { it > 0 } ?: (promptTokens + completionTokens)
                    Result.success(RealAgentResponse(text, totalTokens.coerceAtLeast(100), promptTokens, completionTokens))
                }
                else -> {
                    Result.failure(Exception("لم يتم تقديم أي مفتاح API نشط. يرجى إدخال مفتاح صريح (Gemini / OpenRouter / OpenAI / Groq) أولاً."))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("فشل الشبكة أثناء الاتصال بالسيرفر: ${e.localizedMessage ?: e.message}"))
        }
    }

    private fun parseGeminiText(json: org.json.JSONObject): String? {
        val candidates = json.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null
        val content = candidates.getJSONObject(0).optJSONObject("content") ?: return null
        val parts = content.optJSONArray("parts") ?: return null
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            if (part.has("text")) {
                sb.append(part.getString("text"))
            }
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    suspend fun pingActiveEngine(context: Context): KeyValidationResult = withContext(Dispatchers.IO) {
        val (provider, key) = getActiveEngine()
        if (provider == DetectedProvider.NONE || key.isBlank()) {
            return@withContext KeyValidationResult(
                isValid = false,
                summary = "غير متصل 🔴",
                explanation = "لا يوجد أي مفتاح API نشط أو مدخل في النظام حالياً.",
                suggestedFix = "أدخل مفتاحك (Gemini, OpenRouter, Groq, OpenAI) لتفعيل الذكاء الاصطناعي الفعلي."
            )
        }
        val serviceType = when (provider) {
            DetectedProvider.GEMINI -> "gemini"
            DetectedProvider.OPENROUTER -> "openrouter"
            DetectedProvider.OPENAI -> "openai"
            DetectedProvider.GROQ -> "groq"
            DetectedProvider.HUGGINGFACE -> "huggingface"
            else -> "gemini"
        }
        ApiKeyValidator.validateKey(context, serviceType, key)
    }

    suspend fun pingKey(context: Context, rawKey: String): KeyValidationResult = withContext(Dispatchers.IO) {
        val provider = detect(rawKey)
        if (provider == DetectedProvider.NONE || rawKey.isBlank()) {
            return@withContext KeyValidationResult(
                isValid = false,
                summary = "مفتاح فارغ ⚪",
                explanation = "يرجى لصق مفتاح API للتحقق من صحته.",
                suggestedFix = "تأكد من نسخ المفتاح كاملاً."
            )
        }
        val serviceType = when (provider) {
            DetectedProvider.GEMINI -> "gemini"
            DetectedProvider.OPENROUTER -> "openrouter"
            DetectedProvider.OPENAI -> "openai"
            DetectedProvider.GROQ -> "groq"
            DetectedProvider.HUGGINGFACE -> "huggingface"
            else -> "gemini"
        }
        ApiKeyValidator.validateKey(context, serviceType, rawKey)
    }
}
