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
            k.startsWith("oc-", ignoreCase = true) -> DetectedProvider.OPENCODE
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

        val anthropic = KeyVault.anthropic
        if (anthropic.isNotBlank()) return Pair(DetectedProvider.ANTHROPIC, anthropic)

        val hf = KeyVault.huggingface
        if (hf.isNotBlank()) return Pair(DetectedProvider.HUGGINGFACE, hf)

        val oc = KeyVault.opencode
        if (oc.isNotBlank()) return Pair(DetectedProvider.OPENCODE, oc)

        return Pair(DetectedProvider.NONE, "")
    }

    /**
     * حفظ مفتاح (يُستدعى فقط بعد نجاح الفحص الحي عبر saveKeyIfLive):
     * يُلحقه بمسبح المزود كاحتياطي ويرقّيه نشطاً — لا يمسح المفاتيح السابقة أبداً.
     * @return Triple(المزود، عدد مفاتيح المسبح بعد الإضافة، هل كان مكرراً)
     */
    fun saveKey(context: Context, rawKey: String, forcedProvider: DetectedProvider? = null): Triple<DetectedProvider, Int, Boolean> {
        val k = normalizePastedKey(rawKey)
        val provider = forcedProvider?.takeIf { it != DetectedProvider.NONE } ?: detect(k)
        val prefsKey = prefsKeyFor(provider).ifBlank { "openrouter_key" }
        val before = KeyPoolManager.getKeys(prefsKey)
        val added = KeyPoolManager.addKey(prefsKey, k)
        if (added) KeyPoolManager.promoteKey(prefsKey, k)
        notifyKeyChanged()
        return Triple(provider, KeyPoolManager.getKeys(prefsKey).size, !added && before.any { it == k })
    }

    /** نتيجة الحفظ الذكي: يُحفظ المفتاح فقط بعد نجاح الاتصال الحي */
    sealed interface SmartSaveResult {
        data class Saved(
            val provider: DetectedProvider,
            val latencyMs: Long,
            val modelName: String,
            val poolSize: Int,
            val wasDuplicate: Boolean
        ) : SmartSaveResult
        data class Unsupported(val reason: String) : SmartSaveResult
        data class Failed(val provider: DetectedProvider, val reason: String) : SmartSaveResult
    }

    /**
     * حفظ ذكي: يتعرّف على المزود من صيغة المفتاح، يفحص الاتصال الحي بسيرفره،
     * ولا يحفظ إلا إذا كان صحيحاً ومتصلاً فعلاً — المفتاح الخاطئ يُرفض بسبب حقيقي.
     */
    suspend fun saveKeyIfLive(context: Context, rawKey: String, forcedProvider: DetectedProvider? = null): SmartSaveResult {
        val k = normalizePastedKey(rawKey)
        if (k.isBlank()) return SmartSaveResult.Unsupported("المفتاح فارغ — الصق مفتاحاً أولاً.")
        val provider = forcedProvider?.takeIf { it != DetectedProvider.NONE } ?: detect(k)
        if (provider == DetectedProvider.NONE) {
            return SmartSaveResult.Unsupported("تعذّر التعرّف على المفتاح — الصيغ المدعومة: AIza (Gemini)، gsk_ (Groq)، sk-or- (OpenRouter)، sk- (OpenAI)، sk-ant- (Anthropic)، hf_ (HuggingFace)، oc- (OpenCode Zen) — أو اختر المزود يدوياً.")
        }
        val health = AgentConnectivityEngine.pingProviderLive(provider, k, KeySource.USER_INPUT)
        if (!health.isConnected) {
            return SmartSaveResult.Failed(provider, health.statusDetail.ifBlank { "فشل الاتصال الحي (${health.latencyMs}ms)" })
        }
        val (_, poolSize, wasDuplicate) = saveKey(context, k, provider)
        return SmartSaveResult.Saved(provider, health.latencyMs, health.modelName, poolSize, wasDuplicate)
    }

    fun prefsKeyFor(provider: DetectedProvider): String = when (provider) {
        DetectedProvider.GEMINI -> "gemini_key"
        DetectedProvider.GROQ -> "groq_key"
        DetectedProvider.OPENAI -> "openai_key"
        DetectedProvider.OPENROUTER -> "openrouter_key"
        DetectedProvider.ANTHROPIC -> "anthropic_key"
        DetectedProvider.HUGGINGFACE -> "huggingface_key"
        DetectedProvider.OPENCODE -> "opencode_key"
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
        // 1. Proactively try official Firebase AI Logic (Gemini 3.8 Flash) for Gemini provider
        if (provider == DetectedProvider.GEMINI || provider == DetectedProvider.NONE) {
            try {
                val responseText = FirebaseAiService.generateText(prompt, systemInstruction)
                if (responseText.isNotBlank()) {
                    return@withContext Result.success(RealAgentResponse(responseText, 500, 250, 250))
                }
            } catch (e: Exception) {
                android.util.Log.w("UniversalKeyBrain", "Firebase AI Logic attempt failed: ${e.message}")
            }
        }

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
                    val oaiModel = model?.takeIf { it.isNotBlank() } ?: DetectedProvider.OPENAI.defaultModel
                    val url = "https://api.openai.com/v1/chat/completions"
                    val jsonBody = org.json.JSONObject().apply {
                        put("model", oaiModel)
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
                DetectedProvider.OPENCODE -> {
                    // OpenCode Zen — بوابة OpenAI-compatible: النموذج من عضو الفريق (بدون بادئة opencode/)
                    val zenModel = model?.takeIf { it.isNotBlank() }?.removePrefix("opencode/") ?: "big-pickle"
                    val url = "https://opencode.ai/inference/v1/chat/completions"
                    val jsonBody = org.json.JSONObject().apply {
                        put("model", zenModel)
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
                        return@withContext Result.failure(Exception("خطأ من سيرفر OpenCode (HTTP ${response.code}): $bodyString"))
                    }
                    val json = org.json.JSONObject(bodyString)
                    val text = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                    if (text.isNullOrBlank()) {
                        return@withContext Result.failure(Exception("استجابة فارغة من سيرفر OpenCode"))
                    }
                    val usageObj = json.optJSONObject("usage")
                    val promptTokens = usageObj?.optInt("prompt_tokens", 0) ?: 0
                    val completionTokens = usageObj?.optInt("completion_tokens", 0) ?: 0
                    val totalTokens = usageObj?.optInt("total_tokens", 0)?.takeIf { it > 0 } ?: (promptTokens + completionTokens)
                    Result.success(RealAgentResponse(text, totalTokens.coerceAtLeast(100), promptTokens, completionTokens))
                }
                DetectedProvider.GROQ -> {
                    // 🔑 مفتاح xai- يُصنَّف GROQ لكنه يحتاج بوابة x.ai الفعلية لا بوابة Groq
                    val isXai = apiKey.startsWith("xai-", ignoreCase = true)
                    val gatewayName = if (isXai) "xAI (Grok)" else "Groq"
                    val url = if (isXai) "https://api.x.ai/v1/chat/completions"
                    else "https://api.groq.com/openai/v1/chat/completions"
                    val groqModel = model?.takeIf { it.isNotBlank() }?.let { chosen ->
                        if (isXai && !chosen.startsWith("grok", ignoreCase = true)) "grok-3" else chosen
                    } ?: (if (isXai) "grok-3" else DetectedProvider.GROQ.defaultModel)
                    val jsonBody = org.json.JSONObject().apply {
                        put("model", groqModel)
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
                        return@withContext Result.failure(Exception("خطأ من سيرفر $gatewayName (HTTP ${response.code}): $bodyString"))
                    }
                    val json = org.json.JSONObject(bodyString)
                    val text = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                    if (text.isNullOrBlank()) {
                        return@withContext Result.failure(Exception("استجابة فارغة من سيرفر $gatewayName"))
                    }
                    val usageObj = json.optJSONObject("usage")
                    val promptTokens = usageObj?.optInt("prompt_tokens", 0) ?: 0
                    val completionTokens = usageObj?.optInt("completion_tokens", 0) ?: 0
                    val totalTokens = usageObj?.optInt("total_tokens", 0)?.takeIf { it > 0 } ?: (promptTokens + completionTokens)
                    Result.success(RealAgentResponse(text, totalTokens.coerceAtLeast(100), promptTokens, completionTokens))
                }
                DetectedProvider.ANTHROPIC -> {
                    val antModel = model?.takeIf { it.isNotBlank() } ?: DetectedProvider.ANTHROPIC.defaultModel
                    val url = "https://api.anthropic.com/v1/messages"
                    val jsonBody = org.json.JSONObject().apply {
                        put("model", antModel)
                        put("max_tokens", 4096)
                        put("system", systemInstruction)
                        put("messages", org.json.JSONArray().apply {
                            put(org.json.JSONObject().apply { put("role", "user"); put("content", prompt) })
                        })
                    }
                    val request = Request.Builder()
                        .url(url)
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", "2023-06-01")
                        .header("User-Agent", "Qabas-Studio")
                        .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    val response = client.newCall(request).execute()
                    val bodyString = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("خطأ من سيرفر Anthropic (HTTP ${response.code}): $bodyString"))
                    }
                    val json = org.json.JSONObject(bodyString)
                    val text = json.optJSONArray("content")?.let { arr ->
                        (0 until arr.length()).joinToString("") { i ->
                            val block = arr.optJSONObject(i)
                            if (block != null && block.optString("type") == "text") block.optString("text", "") else ""
                        }
                    }
                    if (text.isNullOrBlank()) {
                        return@withContext Result.failure(Exception("استجابة فارغة من سيرفر Anthropic"))
                    }
                    val usage = json.optJSONObject("usage")
                    val promptTokens = usage?.optInt("input_tokens", 0) ?: 0
                    val completionTokens = usage?.optInt("output_tokens", 0) ?: 0
                    Result.success(
                        RealAgentResponse(text, (promptTokens + completionTokens).coerceAtLeast(150), promptTokens, completionTokens)
                    )
                }
                DetectedProvider.HUGGINGFACE -> {
                    val hfModel = model?.takeIf { it.isNotBlank() }?.removePrefix("hf/") ?: DetectedProvider.HUGGINGFACE.defaultModel
                    val url = "https://router.huggingface.co/v1/chat/completions"
                    val jsonBody = org.json.JSONObject().apply {
                        put("model", hfModel)
                        put("messages", org.json.JSONArray().apply {
                            put(org.json.JSONObject().apply { put("role", "system"); put("content", systemInstruction) })
                            put(org.json.JSONObject().apply { put("role", "user"); put("content", prompt) })
                        })
                    }
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $apiKey")
                        .header("User-Agent", "Qabas-Studio")
                        .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    val response = client.newCall(request).execute()
                    val bodyString = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("خطأ من سيرفر HuggingFace (HTTP ${response.code}): $bodyString"))
                    }
                    val json = org.json.JSONObject(bodyString)
                    val text = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                    if (text.isNullOrBlank()) {
                        return@withContext Result.failure(Exception("استجابة فارغة من سيرفر HuggingFace"))
                    }
                    val usageObj = json.optJSONObject("usage")
                    val promptTokens = usageObj?.optInt("prompt_tokens", 0) ?: 0
                    val completionTokens = usageObj?.optInt("completion_tokens", 0) ?: 0
                    val totalTokens = usageObj?.optInt("total_tokens", 0)?.takeIf { it > 0 } ?: (promptTokens + completionTokens)
                    Result.success(RealAgentResponse(text, totalTokens.coerceAtLeast(120), promptTokens, completionTokens))
                }
                else -> {
                    Result.failure(
                        Exception(
                            "المزود «${provider.displayName}» غير مدعوم للتنفيذ — اختر مزوداً من: Gemini / OpenRouter / OpenAI / Groq / Anthropic / HuggingFace / OpenCode."
                        )
                    )
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
