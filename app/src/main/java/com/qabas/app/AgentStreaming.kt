package com.qabas.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * البثّ التدريجي الحقيقي لاستجابة النموذج (Streaming SSE):
 * يعرض النص لحظة بلحظة بدل انتظار الدفعة الكاملة، مع دعم الإلغاء بين المقاطع.
 * - Gemini: streamGenerateContent?alt=sse
 * - Groq/OpenRouter: chat/completions مع stream:true (صيغة data: ... و [DONE])
 */
object AgentStreaming {

    // ق161: سياق تتبع الاستخدام — البثّ بلا سياق ممرَّر، فيُقرأ سياق
    // التطبيق كمسار Groq؛ وبلاه يبقى السلوك القديم بلا تتبع.
    private fun appCtx(): android.content.Context? =
        runCatching { AppServices.appContext }.getOrNull()

    private suspend fun trackedCall(
        apiName: String,
        request: Request
    ): okhttp3.Response {
        val ctx = appCtx()
        return if (ctx != null) ApiUsageTracker.track(ctx, apiName) { SharedHttpClient.instance.newCall(request).execute() }
        else SharedHttpClient.instance.newCall(request).execute()
    }

    suspend fun stream(
        provider: DetectedProvider,
        apiKey: String,
        model: String?,
        prompt: String,
        systemInstruction: String,
        onPartial: (String) -> Unit
    ): Result<RealAgentResponse> = withContext(Dispatchers.IO) {
        try {
            when (provider) {
                DetectedProvider.GEMINI -> streamGemini(apiKey, model, prompt, systemInstruction, onPartial)
                DetectedProvider.OPENROUTER, DetectedProvider.GROQ ->
                    streamChatCompat(provider, apiKey, model, prompt, systemInstruction, onPartial)
                else -> Result.failure(Exception("البثّ غير مدعوم لهذا المزود"))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception("فشل البثّ: ${e.localizedMessage ?: e.message}"))
        }
    }

    private suspend fun streamGemini(
        apiKey: String,
        model: String?,
        prompt: String,
        systemInstruction: String,
        onPartial: (String) -> Unit
    ): Result<RealAgentResponse> = withContext(Dispatchers.IO) {
        val m = model?.takeIf { it.isNotBlank() } ?: "gemini-2.5-flash"
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$m:streamGenerateContent?alt=sse"
        val body = JSONObject().apply {
            put("contents", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", org.json.JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("systemInstruction", JSONObject().apply {
                put("parts", org.json.JSONArray().apply {
                    put(JSONObject().apply { put("text", systemInstruction) })
                })
            })
        }
        val req = Request.Builder().url(url)
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        // ق161: بثّ Gemini كان يمر بلا ApiUsageTracker.track فيغيب عن لوحة المطور.
        val resp = trackedCall("Gemini", req)
        try {
            if (!resp.isSuccessful) {
                val err = resp.body?.string().orEmpty()
                return@withContext Result.failure(Exception("خطأ Gemini (HTTP ${resp.code}): ${err.take(200)}"))
            }
            val acc = StringBuilder()
            // ق44: تفصيل الرموز الحقيقي من usageMetadata في مقاطع SSE
            // (المقطع الأخير يحمله عادةً) — بلا ذلك يبقى كل بث Gemini
            // «غير مقاس» رغم أن الخادم يرسل الأعداد.
            var promptTokens = 0
            var completionTokens = 0
            var totalTokens = 0
            resp.body?.byteStream()?.bufferedReader()?.useLines { lines ->
                lines.forEach { line ->
                    ensureActive()
                    val t = line.trim()
                    if (!t.startsWith("data:")) return@forEach
                    val payload = t.removePrefix("data:").trim()
                    if (payload.isBlank() || payload == "[DONE]") return@forEach
                    val chunk = runCatching {
                        val j = JSONObject(payload)
                        j.optJSONObject("usageMetadata")?.let { usage ->
                            usage.optInt("totalTokenCount", 0).takeIf { it > 0 }?.let { totalTokens = it }
                            promptTokens = usage.optInt("promptTokenCount", promptTokens)
                            completionTokens = usage.optInt("candidatesTokenCount", completionTokens)
                        }
                        val cands = j.optJSONArray("candidates") ?: return@runCatching null
                        if (cands.length() == 0) return@runCatching null
                        val parts = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                            ?: return@runCatching null
                        buildString {
                            for (i in 0 until parts.length()) {
                                val p = parts.getJSONObject(i)
                                if (p.has("text")) append(p.getString("text"))
                            }
                        }
                    }.getOrNull()
                    if (!chunk.isNullOrEmpty()) {
                        acc.append(chunk)
                        onPartial(acc.toString())
                    }
                }
            }
            val full = acc.toString()
            if (full.isBlank()) return@withContext Result.failure(Exception("استجابة بثّ فارغة من Gemini"))
            // ق44: صار usageMetadata يُقرأ من المقاطع عند إرساله، فالبثّ
            // المقاس يُحسب بتكلفته الحقيقية؛ وبلاه يبقى تقديرياً (طول/4).
            val estimated = totalTokens <= 0
            Result.success(
                RealAgentResponse(
                    full,
                    totalTokens.takeIf { it > 0 } ?: (full.length / 4).coerceAtLeast(150),
                    promptTokens = promptTokens,
                    completionTokens = completionTokens,
                    tokensEstimated = estimated
                )
            )
        } finally {
            resp.close()
        }
    }

    private suspend fun streamChatCompat(
        provider: DetectedProvider,
        apiKey: String,
        model: String?,
        prompt: String,
        systemInstruction: String,
        onPartial: (String) -> Unit
    ): Result<RealAgentResponse> = withContext(Dispatchers.IO) {
        val (url, defaultModel) = when (provider) {
            DetectedProvider.OPENROUTER -> "https://openrouter.ai/api/v1/chat/completions" to "anthropic/claude-sonnet-4.5"
            else -> "https://api.groq.com/openai/v1/chat/completions" to "llama-3.3-70b-versatile"
        }
        val body = JSONObject().apply {
            put("model", model?.takeIf { it.isNotBlank() } ?: defaultModel)
            put("stream", true)
            // ق44: طلب usage في المقطع الأخير — بلا هذا الخيار لا يرسل
            // المزود تفصيل الرموز في البثّ فيبقى كل بثّ «غير مقاس».
            put("stream_options", JSONObject().apply { put("include_usage", true) })
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", systemInstruction) })
                put(JSONObject().apply { put("role", "user"); put("content", prompt) })
            })
        }
        val builder = Request.Builder().url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        if (provider == DetectedProvider.OPENROUTER) builder.header("HTTP-Referer", "https://qabas.app")
        // ق161: بثّ Groq/OpenRouter كان يمر بلا ApiUsageTracker.track فيغيب عن لوحة المطور.
        val resp = trackedCall(if (provider == DetectedProvider.OPENROUTER) "OpenRouter" else "Groq", builder.build())
        try {
            if (!resp.isSuccessful) {
                val err = resp.body?.string().orEmpty()
                return@withContext Result.failure(Exception("خطأ ${provider.displayName} (HTTP ${resp.code}): ${err.take(200)}"))
            }
            val acc = StringBuilder()
            var totalTokens = 0
            var promptTokens = 0
            var completionTokens = 0
            resp.body?.byteStream()?.bufferedReader()?.useLines { lines ->
                lines.forEach { line ->
                    ensureActive()
                    val t = line.trim()
                    if (!t.startsWith("data:")) return@forEach
                    val payload = t.removePrefix("data:").trim()
                    if (payload.isBlank() || payload == "[DONE]") return@forEach
                    runCatching {
                        val j = JSONObject(payload)
                        // (12.14) قراءة تفصيل الرموز من usage عند إرساله —
                        // بدونه تبقى التكلفة «غير مقاسة» (بلا تفصيل رموز).
                        j.optJSONObject("usage")?.let { usage ->
                            usage.optInt("total_tokens", 0).takeIf { it > 0 }?.let { totalTokens = it }
                            promptTokens = usage.optInt("prompt_tokens", promptTokens)
                            completionTokens = usage.optInt("completion_tokens", completionTokens)
                        }
                        val choices = j.optJSONArray("choices") ?: return@runCatching
                        if (choices.length() == 0) return@runCatching
                        val delta = choices.getJSONObject(0).optJSONObject("delta")?.optString("content").orEmpty()
                        if (delta.isNotEmpty()) {
                            acc.append(delta)
                            onPartial(acc.toString())
                        }
                    }
                }
            }
            val full = acc.toString()
            if (full.isBlank()) return@withContext Result.failure(Exception("استجابة بثّ فارغة من ${provider.displayName}"))
            // (12.14) تقديرية فقط عند غياب usage (لا stream_options.include_usage
            // في الطلب، فغالباً يرسل المزود البثّ بلا usage).
            val estimated = totalTokens <= 0
            Result.success(
                RealAgentResponse(
                    full,
                    totalTokens.takeIf { it > 0 } ?: (full.length / 4).coerceAtLeast(120),
                    promptTokens = promptTokens,
                    completionTokens = completionTokens,
                    tokensEstimated = estimated
                )
            )
        } finally {
            resp.close()
        }
    }
}
