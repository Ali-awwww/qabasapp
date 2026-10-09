package com.qabas.app

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class ModelBenchmarkResult(
    val id: String,
    val name: String,
    val provider: String,
    val modelSpec: String,
    val latencyMs: Long,
    val isSuccess: Boolean,
    val message: String,
    val previewOutput: String = ""
)

/**
 * ⚡ مدير وموجّه نماذج الذكاء الاصطناعي الذكي حسب سرعة الاستجابة الحية (AI Model Latency Router)
 *
 * يسمح بالتبديل الفوري والتلقائي بين النماذج (مثل Gemini و Groq و OpenAI)
 * بناءً على أدنى سرعة استجابة (Latency) مقاسة فعلياً في اللحظة الحالية.
 */
object AiModelLatencyRouter {
    private const val PREFS_NAME = "qabas_ai_router_prefs"
    private const val KEY_AUTO_FASTEST = "auto_fastest_enabled"
    private const val KEY_MANUAL_ENGINE = "manual_selected_engine"
    private const val KEY_LAST_BENCHMARK_TIME = "last_benchmark_timestamp"

    private val client = SharedHttpClient.instance

    private val _activeEngine = MutableStateFlow("Groq")
    val activeEngine: StateFlow<String> = _activeEngine.asStateFlow()

    private val _isAutoFastest = MutableStateFlow(true)
    val isAutoFastest: StateFlow<Boolean> = _isAutoFastest.asStateFlow()

    private val _isBenchmarking = MutableStateFlow(false)
    val isBenchmarking: StateFlow<Boolean> = _isBenchmarking.asStateFlow()

    private val _benchmarkResults = MutableStateFlow<Map<String, ModelBenchmarkResult>>(emptyMap())
    val benchmarkResults: StateFlow<Map<String, ModelBenchmarkResult>> = _benchmarkResults.asStateFlow()

    private val _lastBenchmarkTime = MutableStateFlow(0L)
    val lastBenchmarkTime: StateFlow<Long> = _lastBenchmarkTime.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isAutoFastest.value = prefs.getBoolean(KEY_AUTO_FASTEST, true)
        val savedEngine = prefs.getString(KEY_MANUAL_ENGINE, "Groq") ?: "Groq"
        _activeEngine.value = savedEngine
        _lastBenchmarkTime.value = prefs.getLong(KEY_LAST_BENCHMARK_TIME, 0L)
    }

    fun setAutoFastest(context: Context, enabled: Boolean) {
        _isAutoFastest.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_FASTEST, enabled)
            .apply()

        if (enabled) {
            evaluateFastestEngine()
        }
        SystemLogsManager.addLog(
            "ROUTER",
            if (enabled) "تم تفعيل التوجيه التلقائي للمحرك الأسرع لحظياً ⚡" else "تم إيقاف التوجيه التلقائي وتثبيت الاختيار اليدوي 🔒",
            if (enabled) Color(0xFF10B981) else Color(0xFFF59E0B)
        )
    }

    fun setManualEngine(context: Context, engineName: String) {
        _activeEngine.value = engineName
        _isAutoFastest.value = false
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_FASTEST, false)
            .putString(KEY_MANUAL_ENGINE, engineName)
            .apply()

        SceneEngineMonitor.report(engineName)
        SystemLogsManager.addLog(
            "ROUTER",
            "تم تعيين المحرك الرئيسي يدوياً: $engineName ⚡",
            Color(0xFF8B5CF6)
        )
    }

    fun getEffectiveEngine(): String {
        return _activeEngine.value
    }

    private fun evaluateFastestEngine() {
        val currentResults = _benchmarkResults.value
        val successfulResults = currentResults.values
            .filter { it.isSuccess && it.latencyMs > 0 }
            .sortedBy { it.latencyMs }

        if (successfulResults.isNotEmpty()) {
            val fastest = successfulResults.first()
            _activeEngine.value = fastest.name
            SceneEngineMonitor.report(fastest.name)
            SystemLogsManager.addLog(
                "ROUTER",
                "التوجيه الذكي اختار المحرك الأسرع لحظياً: ${fastest.name} (${fastest.latencyMs}ms) 🚀",
                Color(0xFF10B981)
            )
        }
    }

    /**
     * تشغيل فحص سرعة الاستجابة الحية (Live Latency Benchmark) لجميع النماذج
     */
    suspend fun runFullBenchmark(context: Context): Map<String, ModelBenchmarkResult> = withContext(Dispatchers.IO) {
        _isBenchmarking.value = true
        val results = mutableMapOf<String, ModelBenchmarkResult>()

        try {
            // 1. فحص Groq LPU (Llama 3.3 70B)
            val groqResult = pingGroq()
            results["Groq"] = groqResult

            // 2. فحص Google Gemini (Gemini 2.5 Flash / 2.0 Flash)
            val geminiResult = pingGemini()
            results["Gemini"] = geminiResult

            // 3. فحص OpenAI (GPT-4o Mini)
            val openaiResult = pingOpenAI()
            results["OpenAI"] = openaiResult

            // 4. فحص Pollinations (النموذج المجاني بلا مفتاح)
            val pollResult = pingPollinations()
            results["Pollinations"] = pollResult

            _benchmarkResults.value = results
            val now = System.currentTimeMillis()
            _lastBenchmarkTime.value = now

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_BENCHMARK_TIME, now)
                .apply()

            if (_isAutoFastest.value) {
                evaluateFastestEngine()
            }
        } finally {
            _isBenchmarking.value = false
        }

        return@withContext results
    }

    private fun pingGroq(): ModelBenchmarkResult {
        val apiKey = KeyVault.groq
        if (apiKey.isBlank()) {
            return ModelBenchmarkResult(
                id = "groq",
                name = "Groq",
                provider = "Groq Cloud (LPU)",
                modelSpec = "llama-3.3-70b-versatile",
                latencyMs = -1L,
                isSuccess = false,
                message = "مفتاح Groq غير متوفر في الإعدادات"
            )
        }

        val startTime = System.currentTimeMillis()
        return try {
            val isXai = apiKey.startsWith("xai-", ignoreCase = true)
            val apiUrl = if (isXai) "https://api.x.ai/v1/chat/completions" else "https://api.groq.com/openai/v1/chat/completions"
            val modelName = if (isXai) "grok-2-latest" else "llama-3.3-70b-versatile"

            val jsonBody = JSONObject().apply {
                put("model", modelName)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "ping: قل كلمة واحدة فقط: جاهز")
                    })
                })
                put("max_tokens", 10)
                put("temperature", 0.1)
            }

            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime

            if (response.isSuccessful) {
                val resObj = JSONObject(response.body?.string() ?: "")
                val text = resObj.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content", "") ?: ""
                ModelBenchmarkResult(
                    id = "groq",
                    name = "Groq",
                    provider = "Groq Cloud (LPU)",
                    modelSpec = modelName,
                    latencyMs = latency,
                    isSuccess = true,
                    message = "استجابة سريعة جداً عبر رقاقات LPU",
                    previewOutput = text.trim()
                )
            } else {
                ModelBenchmarkResult(
                    id = "groq",
                    name = "Groq",
                    provider = "Groq Cloud (LPU)",
                    modelSpec = modelName,
                    latencyMs = latency,
                    isSuccess = false,
                    message = "خطأ HTTP ${response.code}"
                )
            }
        } catch (e: Exception) {
            ModelBenchmarkResult(
                id = "groq",
                name = "Groq",
                provider = "Groq Cloud (LPU)",
                modelSpec = "llama-3.3-70b-versatile",
                latencyMs = System.currentTimeMillis() - startTime,
                isSuccess = false,
                message = "فشل الاتصال: ${e.message ?: "خطأ شبكة"}"
            )
        }
    }

    private fun pingGemini(): ModelBenchmarkResult {
        val apiKey = KeyVault.gemini
        if (apiKey.isBlank()) {
            return ModelBenchmarkResult(
                id = "gemini",
                name = "Gemini",
                provider = "Google AI Studio",
                modelSpec = "gemini-2.5-flash",
                latencyMs = -1L,
                isSuccess = false,
                message = "مفتاح Gemini غير متوفر في الإعدادات"
            )
        }

        val startTime = System.currentTimeMillis()
        return try {
            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", "ping: قل كلمة واحدة فقط: جاهز") })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("maxOutputTokens", 10)
                    put("temperature", 0.1)
                })
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime

            if (response.isSuccessful) {
                val resObj = JSONObject(response.body?.string() ?: "")
                val text = resObj.optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text", "") ?: ""
                ModelBenchmarkResult(
                    id = "gemini",
                    name = "Gemini",
                    provider = "Google AI Studio",
                    modelSpec = "gemini-2.0-flash",
                    latencyMs = latency,
                    isSuccess = true,
                    message = "استجابة ممتازة من خوادم Google",
                    previewOutput = text.trim()
                )
            } else {
                ModelBenchmarkResult(
                    id = "gemini",
                    name = "Gemini",
                    provider = "Google AI Studio",
                    modelSpec = "gemini-2.0-flash",
                    latencyMs = latency,
                    isSuccess = false,
                    message = "خطأ HTTP ${response.code}"
                )
            }
        } catch (e: Exception) {
            ModelBenchmarkResult(
                id = "gemini",
                name = "Gemini",
                provider = "Google AI Studio",
                modelSpec = "gemini-2.0-flash",
                latencyMs = System.currentTimeMillis() - startTime,
                isSuccess = false,
                message = "فشل الاتصال: ${e.message ?: "خطأ شبكة"}"
            )
        }
    }

    private fun pingOpenAI(): ModelBenchmarkResult {
        val apiKey = KeyVault.openai
        if (apiKey.isBlank()) {
            return ModelBenchmarkResult(
                id = "openai",
                name = "OpenAI",
                provider = "OpenAI Platform",
                modelSpec = "gpt-4o-mini",
                latencyMs = -1L,
                isSuccess = false,
                message = "مفتاح OpenAI غير متوفر"
            )
        }

        val startTime = System.currentTimeMillis()
        return try {
            val jsonBody = JSONObject().apply {
                put("model", "gpt-4o-mini")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "ping: قل كلمة واحدة: جاهز")
                    })
                })
                put("max_tokens", 10)
            }

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime

            if (response.isSuccessful) {
                val resObj = JSONObject(response.body?.string() ?: "")
                val text = resObj.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content", "") ?: ""
                ModelBenchmarkResult(
                    id = "openai",
                    name = "OpenAI",
                    provider = "OpenAI Platform",
                    modelSpec = "gpt-4o-mini",
                    latencyMs = latency,
                    isSuccess = true,
                    message = "استجابة سحابية متزنة",
                    previewOutput = text.trim()
                )
            } else {
                ModelBenchmarkResult(
                    id = "openai",
                    name = "OpenAI",
                    provider = "OpenAI Platform",
                    modelSpec = "gpt-4o-mini",
                    latencyMs = latency,
                    isSuccess = false,
                    message = "خطأ HTTP ${response.code}"
                )
            }
        } catch (e: Exception) {
            ModelBenchmarkResult(
                id = "openai",
                name = "OpenAI",
                provider = "OpenAI Platform",
                modelSpec = "gpt-4o-mini",
                latencyMs = System.currentTimeMillis() - startTime,
                isSuccess = false,
                message = "فشل الاتصال: ${e.message ?: "خطأ شبكة"}"
            )
        }
    }

    private suspend fun pingPollinations(): ModelBenchmarkResult {
        val startTime = System.currentTimeMillis()
        return try {
            val text = PollinationsTextService.chat("ping: كلمة واحدة: جاهز")
            val latency = System.currentTimeMillis() - startTime
            if (!text.isNullOrBlank()) {
                ModelBenchmarkResult(
                    id = "pollinations",
                    name = "Pollinations",
                    provider = "مجاني بلا مفتاح (Free AI)",
                    modelSpec = "mistral-large / free",
                    latencyMs = latency,
                    isSuccess = true,
                    message = "يعمل بلا أي مفتاح API",
                    previewOutput = text.take(30).trim()
                )
            } else {
                ModelBenchmarkResult(
                    id = "pollinations",
                    name = "Pollinations",
                    provider = "مجاني بلا مفتاح",
                    modelSpec = "mistral-large / free",
                    latencyMs = latency,
                    isSuccess = false,
                    message = "لا يوجد رد متاح"
                )
            }
        } catch (e: Exception) {
            ModelBenchmarkResult(
                id = "pollinations",
                name = "Pollinations",
                provider = "مجاني بلا مفتاح",
                modelSpec = "mistral-large / free",
                latencyMs = System.currentTimeMillis() - startTime,
                isSuccess = false,
                message = "فشل الاتصال بالخادم المجاني"
            )
        }
    }

    /**
     * تشغيل مقارنة حية ومباشرة بين Gemini و Groq لنفس النص لمعاينة فارق السرعة بالميللي ثانية
     */
    suspend fun runLiveHeadToHead(prompt: String): HeadToHeadComparison = withContext(Dispatchers.IO) {
        val startGroq = System.currentTimeMillis()
        var groqText: String? = null
        var groqTime = -1L
        var groqOk = false

        try {
            groqText = RealGroqService.chatOrGenerate(prompt)
            groqTime = System.currentTimeMillis() - startGroq
            groqOk = !groqText.isNullOrBlank()
        } catch (e: Exception) {
            groqTime = System.currentTimeMillis() - startGroq
        }

        val startGemini = System.currentTimeMillis()
        var geminiText: String? = null
        var geminiTime = -1L
        var geminiOk = false

        try {
            geminiText = RealGeminiService.chatWithAssistant(listOf(Pair(true, prompt)), null)
            geminiTime = System.currentTimeMillis() - startGemini
            geminiOk = !geminiText.isNullOrBlank()
        } catch (e: Exception) {
            geminiTime = System.currentTimeMillis() - startGemini
        }

        HeadToHeadComparison(
            groqLatencyMs = groqTime,
            groqOutput = groqText ?: "فشل أو لم يتم إرجاع رد",
            groqSuccess = groqOk,
            geminiLatencyMs = geminiTime,
            geminiOutput = geminiText ?: "فشل أو لم يتم إرجاع رد",
            geminiSuccess = geminiOk,
            winner = when {
                groqOk && geminiOk -> if (groqTime < geminiTime) "Groq ⚡" else "Gemini ✨"
                groqOk -> "Groq ⚡"
                geminiOk -> "Gemini ✨"
                else -> "لا يوجد فائز"
            },
            timeSavedMs = if (groqOk && geminiOk) kotlin.math.abs(geminiTime - groqTime) else 0L
        )
    }

    data class HeadToHeadComparison(
        val groqLatencyMs: Long,
        val groqOutput: String,
        val groqSuccess: Boolean,
        val geminiLatencyMs: Long,
        val geminiOutput: String,
        val geminiSuccess: Boolean,
        val winner: String,
        val timeSavedMs: Long
    )
}
