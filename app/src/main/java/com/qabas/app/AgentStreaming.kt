package com.qabas.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * استخراج مقترحات الفروقات من نص الكود المولّد:
 * كل كتلة ``` تصبح FileDiff قابلاً للمعاينة والاعتماد (دفع GitHub) أو التجاهل (تراجع).
 */
object AgentPatchKit {
    fun extractDiffs(prompt: String, codeText: String): List<FileDiff> {
        val blocks = Regex("```(\\w*)\\n?(.*?)```", RegexOption.DOT_MATCHES_ALL)
            .findAll(codeText)
            .map { it.groupValues[2].trim() }
            .filter { it.length > 20 }
            .toList()
        if (blocks.isEmpty()) {
            if (codeText.contains("fun ") || codeText.contains("class ")) {
                val target = runCatching { extractTargetFile(prompt, codeText) }.getOrDefault("مقترح_عام.kt")
                return listOf(
                    FileDiff(
                        filePath = "app/src/main/java/com/qabas/app/$target",
                        additions = codeText.lines().size,
                        deletions = 0,
                        diffText = codeText.take(4000)
                    )
                )
            }
            return emptyList()
        }
        val mainTarget = runCatching { extractTargetFile(prompt, codeText) }
            .getOrDefault("مقترح_عام.kt")
        return blocks.take(5).mapIndexed { idx, code ->
            val name = if (idx == 0) mainTarget else "مقترح_${idx + 1}.kt"
            FileDiff(
                filePath = "app/src/main/java/com/qabas/app/$name",
                additions = code.lines().size,
                deletions = 0,
                diffText = code.take(4000)
            )
        }
    }
}
object AgentStreaming {

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
                DetectedProvider.OPENROUTER, DetectedProvider.OPENAI, DetectedProvider.GROQ ->
                    streamOpenAiCompat(provider, apiKey, model, prompt, systemInstruction, onPartial)
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
        val m = model?.takeIf { it.isNotBlank() } ?: "gemini-3.5-flash"
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$m:streamGenerateContent?alt=sse&key=$apiKey"
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
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        val resp = SharedHttpClient.instance.newCall(req).execute()
        try {
            if (!resp.isSuccessful) {
                val err = resp.body?.string().orEmpty()
                return@withContext Result.failure(Exception("خطأ Gemini (HTTP ${resp.code}): ${err.take(200)}"))
            }
            val acc = StringBuilder()
            resp.body?.byteStream()?.bufferedReader()?.useLines { lines ->
                lines.forEach { line ->
                    ensureActive()
                    val t = line.trim()
                    if (!t.startsWith("data:")) return@forEach
                    val payload = t.removePrefix("data:").trim()
                    if (payload.isBlank() || payload == "[DONE]") return@forEach
                    val chunk = runCatching {
                        val j = JSONObject(payload)
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
            Result.success(RealAgentResponse(full, (full.length / 4).coerceAtLeast(150)))
        } finally {
            resp.close()
        }
    }

    private suspend fun streamOpenAiCompat(
        provider: DetectedProvider,
        apiKey: String,
        model: String?,
        prompt: String,
        systemInstruction: String,
        onPartial: (String) -> Unit
    ): Result<RealAgentResponse> = withContext(Dispatchers.IO) {
        val (url, defaultModel) = when (provider) {
            DetectedProvider.OPENROUTER -> "https://openrouter.ai/api/v1/chat/completions" to "anthropic/claude-sonnet-4.5"
            DetectedProvider.GROQ -> "https://api.groq.com/openai/v1/chat/completions" to "llama-3.3-70b-versatile"
            else -> "https://api.openai.com/v1/chat/completions" to "gpt-4o"
        }
        val body = JSONObject().apply {
            put("model", model?.takeIf { it.isNotBlank() } ?: defaultModel)
            put("stream", true)
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", systemInstruction) })
                put(JSONObject().apply { put("role", "user"); put("content", prompt) })
            })
        }
        val builder = Request.Builder().url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        if (provider == DetectedProvider.OPENROUTER) builder.header("HTTP-Referer", "https://qabas.app")
        val resp = SharedHttpClient.instance.newCall(builder.build()).execute()
        try {
            if (!resp.isSuccessful) {
                val err = resp.body?.string().orEmpty()
                return@withContext Result.failure(Exception("خطأ ${provider.displayName} (HTTP ${resp.code}): ${err.take(200)}"))
            }
            val acc = StringBuilder()
            var totalTokens = 0
            resp.body?.byteStream()?.bufferedReader()?.useLines { lines ->
                lines.forEach { line ->
                    ensureActive()
                    val t = line.trim()
                    if (!t.startsWith("data:")) return@forEach
                    val payload = t.removePrefix("data:").trim()
                    if (payload.isBlank() || payload == "[DONE]") return@forEach
                    runCatching {
                        val j = JSONObject(payload)
                        j.optJSONObject("usage")?.optInt("total_tokens", 0)
                            ?.takeIf { it > 0 }?.let { totalTokens = it }
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
            Result.success(RealAgentResponse(full, totalTokens.takeIf { it > 0 } ?: (full.length / 4).coerceAtLeast(120)))
        } finally {
            resp.close()
        }
    }
}
