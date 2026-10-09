package com.qabas.app

import android.content.Context
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
 * Single AI entry point for application code.
 * Supports Edge gateway proxy, direct custom keys (OpenRouter, DeepSeek, Groq, OpenAI), and registered models.
 */
object GatewayAiService {

    private val directClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun gemini(payload: JSONObject): JSONObject? =
        PlatformGatewayClient.proxyGemini(payload)

    suspend fun groq(payload: JSONObject): JSONObject? =
        PlatformGatewayClient.proxyGroq(payload)

    suspend fun openai(payload: JSONObject): JSONObject? =
        PlatformGatewayClient.proxyOpenAI(payload)

    suspend fun openrouter(payload: JSONObject): JSONObject? =
        PlatformGatewayClient.proxyOpenRouter(payload)

    suspend fun geminiText(prompt: String, system: String? = null): String? = withContext(Dispatchers.IO) {
        val contents = JSONArray().put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", prompt)))
        })
        val payload = JSONObject().apply {
            put("contents", contents)
            if (!system.isNullOrBlank()) {
                put("systemInstruction", JSONObject().put(
                    "parts", JSONArray().put(JSONObject().put("text", system))
                ))
            }
            put("generationConfig", JSONObject().put("temperature", 0.7))
        }
        val response = gemini(payload) ?: return@withContext null
        extractGeminiText(response)
    }

    suspend fun chatText(provider: String, prompt: String, system: String? = null): String? =
        withContext(Dispatchers.IO) {
            val messages = JSONArray()
            if (!system.isNullOrBlank()) {
                messages.put(JSONObject().put("role", "system").put("content", system))
            }
            messages.put(JSONObject().put("role", "user").put("content", prompt))
            val payload = JSONObject().apply {
                put("messages", messages)
                put("temperature", 0.7)
            }
            val response = when (provider.lowercase()) {
                "groq" -> groq(payload)
                "openai" -> openai(payload)
                "openrouter" -> openrouter(payload)
                else -> null
            } ?: return@withContext null
            extractChoiceText(response)
        }

    /**
     * إرسال طلب محادثة عبر النموذج النشط المختار في إعدادات التطبيق
     */
    suspend fun chatWithActiveModel(
        context: Context,
        prompt: String,
        system: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val config = AiModelRegistry.getActiveModel(context)
        val sysPrompt = system ?: config.systemPrompt.takeIf { it.isNotBlank() }

        // إذا كان النموذج هو Gemini
        if (config.provider == AiProviderType.GEMINI) {
            val res = geminiText(prompt, sysPrompt)
            if (!res.isNullOrBlank()) return@withContext res
        }

        // إذا كان لديه مفتاح API مخصص أو عنوان endpoint مخصص، نرسله مباشرة
        if (config.apiKey.isNotBlank() || config.customEndpoint.isNotBlank() || config.provider == AiProviderType.OPENROUTER || config.provider == AiProviderType.DEEPSEEK || config.provider == AiProviderType.GROQ) {
            val endpoint = config.effectiveEndpoint
            if (endpoint.isNotBlank() && (config.apiKey.isNotBlank() || config.provider == AiProviderType.POLLINATIONS)) {
                try {
                    val messages = JSONArray()
                    if (!sysPrompt.isNullOrBlank()) {
                        messages.put(JSONObject().put("role", "system").put("content", sysPrompt))
                    }
                    messages.put(JSONObject().put("role", "user").put("content", prompt))

                    val payload = JSONObject().apply {
                        put("model", config.modelIdentifier)
                        put("messages", messages)
                        put("temperature", config.temperature)
                        put("max_tokens", config.maxTokens)
                    }

                    val reqBuilder = Request.Builder()
                        .url(endpoint)
                        .header("Content-Type", "application/json")
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))

                    if (config.apiKey.isNotBlank()) {
                        reqBuilder.header("Authorization", "Bearer ${config.apiKey.trim()}")
                    }
                    if (config.provider == AiProviderType.OPENROUTER) {
                        reqBuilder.header("HTTP-Referer", "https://qabas.app")
                        reqBuilder.header("X-Title", "Qabas Studio")
                    }

                    val response = directClient.newCall(reqBuilder.build()).execute()
                    if (response.isSuccessful) {
                        val raw = response.body?.string().orEmpty()
                        val text = extractChoiceText(JSONObject(raw))
                        if (!text.isNullOrBlank()) return@withContext text
                    }
                } catch (_: Exception) {}
            }
        }

        // محاولة عبر البوابة السحابية Gateway
        val fallbackText = chatText(config.provider.name.lowercase(), prompt, sysPrompt)
        if (!fallbackText.isNullOrBlank()) return@withContext fallbackText

        // ملاذ أخير: Gemini Text
        geminiText(prompt, sysPrompt)
    }

    private fun extractGeminiText(json: JSONObject): String? {
        val candidates = json.optJSONArray("candidates") ?: return null
        val parts = candidates.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?: return null
        return parts.optJSONObject(0)?.optString("text")?.takeIf { it.isNotBlank() }
    }

    private fun extractChoiceText(json: JSONObject): String? {
        return json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.takeIf { it.isNotBlank() }
    }
}

