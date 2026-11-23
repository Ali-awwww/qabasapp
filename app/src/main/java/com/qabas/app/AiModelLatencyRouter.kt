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
 * يسمح بالتبديل الفوري والتلقائي بين النماذج (مثل Gemini و Groq)
 * بناءً على أدنى سرعة استجابة (Latency) مقاسة فعلياً في اللحظة الحالية.
 */
object AiModelLatencyRouter {
    private const val PREFS_NAME = "qabas_ai_router_prefs"
    private const val KEY_AUTO_FASTEST = "auto_fastest_enabled"
    private const val KEY_MANUAL_ENGINE = "manual_selected_engine"
    // ق93: اختيار التوجيه التلقائي نفسه (لا اليدوي) — كان يُحفظ في
    // الذاكرة فقط فيُنسى عند إعادة التشغيل فيعود المحرك لآخر اختيار
    // يدوي حتى يكتمل فحص جديد.
    private const val KEY_AUTO_ENGINE = "auto_routed_engine"
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
        val autoFastest = prefs.getBoolean(KEY_AUTO_FASTEST, true)
        _isAutoFastest.value = autoFastest
        val savedEngine = prefs.getString(KEY_MANUAL_ENGINE, "Groq") ?: "Groq"
        // ق93: في الوضع التلقائي يُستعاد آخر محرك وجّهه الفحص نفسه لا
        // آخر اختيار يدوي (كان يُنسى عند إعادة التشغيل).
        val savedAutoEngine = prefs.getString(KEY_AUTO_ENGINE, null)
        _activeEngine.value = if (autoFastest && !savedAutoEngine.isNullOrBlank()) savedAutoEngine else savedEngine
        _lastBenchmarkTime.value = prefs.getLong(KEY_LAST_BENCHMARK_TIME, 0L)
    }

    fun setAutoFastest(context: Context, enabled: Boolean) {
        _isAutoFastest.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_FASTEST, enabled)
            .apply()

        if (enabled) {
            evaluateFastestEngine(context)
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

    // ق93: التوجيه يختار معرّف المحرك (مفتاح خريطة النتائج: Groq /
    // Gemini / Pollinations) لا اسم العرض — كان يأخذ fastest.name
    // فصار المحرك «Gemini 3.8 Flash (AI Logic)» عند فوز مسار Firebase،
    // فلا تطابقه أي بطاقة (isActive) ويقع وصف البطل على فرع else
    // «المحرك المجاني بلا مفتاح» مع أيقونة Public. ويُحفظ الاختيار
    // التلقائي ليبقى بعد إعادة التشغيل.
    private fun evaluateFastestEngine(context: Context) {
        val currentResults = _benchmarkResults.value
        val fastestEntry = currentResults.entries
            .filter { it.value.isSuccess && it.value.latencyMs > 0 }
            .minByOrNull { it.value.latencyMs }

        if (fastestEntry != null) {
            val fastest = fastestEntry.value
            _activeEngine.value = fastestEntry.key
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_AUTO_ENGINE, fastestEntry.key)
                .apply()
            SceneEngineMonitor.report(fastestEntry.key)
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

            // 2. فحص Google Gemini (النموذج الافتراضي للمزود)
            val geminiResult = pingGemini()
            results["Gemini"] = geminiResult

            // 3. فحص Pollinations (النموذج المجاني بلا مفتاح)
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
                evaluateFastestEngine(context)
            }
        } finally {
            _isBenchmarking.value = false
        }

        return@withContext results
    }

    private fun pingGroq(): ModelBenchmarkResult {
        // تجاوز تلقائي: جرّب مفاتيح المسبح بالترتيب ورقِّ الفائز
        val keys = KeyPoolManager.orderedKeys("groq_key", KeyVault.groq)
        var last: ModelBenchmarkResult? = null
        for (k in keys) {
            last = tryPingGroq(k)
            if (last.isSuccess) {
                KeyPoolManager.promoteKey("groq_key", k)
                return last
            }
        }
        return last ?: ModelBenchmarkResult(
            id = "groq",
            name = "Groq",
            provider = "Groq Cloud (LPU)",
            modelSpec = "llama-3.3-70b-versatile",
            latencyMs = -1L,
            isSuccess = false,
            message = "مفتاح Groq غير متوفر في الإعدادات"
        )
    }

    private fun tryPingGroq(apiKey: String): ModelBenchmarkResult {
        val startTime = System.currentTimeMillis()
        return try {
            val apiUrl = "https://api.groq.com/openai/v1/chat/completions"
            val modelName = "llama-3.3-70b-versatile"

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

            // ق93: الاستجابة تُغلق في كل المسارات (use) — كان مسار
            // الخطأ HTTP يرجع دون إغلاق فيتسرب الاتصال مع كل فحص فاشل.
            return response.use { resp ->
                if (resp.isSuccessful) {
                    val resObj = JSONObject(resp.body?.string() ?: "")
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
                        message = "خطأ HTTP ${resp.code}"
                    )
                }
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
        // 1. Try Firebase AI Logic (Gemini 3.8 Flash via App Check)
        try {
            val startTime = System.currentTimeMillis()
            val text = kotlinx.coroutines.runBlocking {
                FirebaseAiService.generateText("ping: قل كلمة واحدة: جاهز")
            }
            val latency = System.currentTimeMillis() - startTime
            if (text.isNotBlank()) {
                return ModelBenchmarkResult(
                    id = "gemini",
                    name = "Gemini 3.8 Flash (AI Logic)",
                    provider = "Google AI Studio",
                    modelSpec = "gemini-3.8-flash",
                    latencyMs = latency,
                    isSuccess = true,
                    message = "متصل مباشرة عبر Firebase AI Logic",
                    previewOutput = text.trim()
                )
            }
        } catch (_: Exception) { }

        val keys = KeyPoolManager.orderedKeys("gemini_key", KeyVault.gemini)
        var last: ModelBenchmarkResult? = null
        for (k in keys) {
            last = tryPingGemini(k)
            if (last.isSuccess) {
                KeyPoolManager.promoteKey("gemini_key", k)
                return last
            }
        }
        return last ?: ModelBenchmarkResult(
            id = "gemini",
            name = "Gemini",
            provider = "Google AI Studio",
            modelSpec = "gemini-2.5-flash",
            latencyMs = -1L,
            isSuccess = false,
            message = "مفتاح Gemini غير متوفر في الإعدادات"
        )
    }

    private fun tryPingGemini(apiKey: String): ModelBenchmarkResult {

        val startTime = System.currentTimeMillis()
        // ق93: يُقاس النموذج الافتراضي للمزود (المصدر الوحيد
        // DetectedProvider.GEMINI.defaultModel) — كان الفحص يطلب
        // gemini-2.0-flash، نموذج لا يستعمله التطبيق أصلاً (ولا في
        // قائمة المزود)، فزمن الاستجابة المقاس لم يكن زمن النموذج
        // المستعمل فعلاً.
        val modelName = DetectedProvider.GEMINI.defaultModel
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
                .url("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent")
                .header("x-goog-api-key", apiKey)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime

            // ق93: الاستجابة تُغلق في كل المسارات (use) — كان مسار
            // الخطأ HTTP يرجع دون إغلاق فيتسرب الاتصال مع كل فحص فاشل.
            return response.use { resp ->
                if (resp.isSuccessful) {
                    val resObj = JSONObject(resp.body?.string() ?: "")
                    val text = resObj.optJSONArray("candidates")?.optJSONObject(0)
                        ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text", "") ?: ""
                    // ق160: فحص زمن الاستجابة استدعاء مدفوع حقيقي (ولو 10 رموز
                    // كحد أقصى) — كان يمر بلا أثر في تتبع التكلفة.
                    runCatching {
                        if (text.isNotBlank()) {
                            VibeCostTracker.recordEstimated(AppServices.appContext, "gemini", modelName, "ping: قل كلمة واحدة فقط: جاهز", text, "قياس زمن الاستجابة")
                        }
                    }
                    ModelBenchmarkResult(
                        id = "gemini",
                        name = "Gemini",
                        provider = "Google AI Studio",
                        modelSpec = modelName,
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
                        modelSpec = modelName,
                        latencyMs = latency,
                        isSuccess = false,
                        message = "خطأ HTTP ${resp.code}"
                    )
                }
            }
        } catch (e: Exception) {
            ModelBenchmarkResult(
                id = "gemini",
                name = "Gemini",
                provider = "Google AI Studio",
                modelSpec = modelName,
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
                    modelSpec = "default / free", // ق93: الخدمة لا ترسل معامل نموذج أصلاً فيدّعي «mistral-large» نموذجاً لم يُطلب
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
                    modelSpec = "default / free", // ق93: الخدمة لا ترسل معامل نموذج أصلاً فيدّعي «mistral-large» نموذجاً لم يُطلب
                    latencyMs = latency,
                    isSuccess = false,
                    message = "لا يوجد رد متاح"
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            ModelBenchmarkResult(
                id = "pollinations",
                name = "Pollinations",
                provider = "مجاني بلا مفتاح",
                modelSpec = "default / free", // ق93: الخدمة لا ترسل معامل نموذج أصلاً فيدّعي «mistral-large» نموذجاً لم يُطلب
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
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
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
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
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
