package com.qabas.app

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * مصدر وتوثيق مفتاح الـ API النشط
 */
enum class KeySource(val label: String, val color: Color) {
    BUILD_CONFIG("بيئة النظام (BuildConfig)", Color(0xFF38BDF8)),
    KEY_VAULT("التخزين المحلي (Prefs)", Color(0xFF10B981)),
    USER_INPUT("مدخل يدوي نشط", Color(0xFFE5B842)),
    NONE("غير متصل - يلزم إدخال المفتاح", Color(0xFFEF4444))
}

/**
 * نتيجة الفحص الحي والاتصال المباشر بالسيرفرات
 */
data class LiveHealthStatus(
    val serviceName: String,
    val isConnected: Boolean,
    val latencyMs: Long,
    val statusDetail: String,
    val keySource: KeySource,
    val httpCode: Int = 0,
    val modelName: String = "",
    val lastCheckedTimestamp: Long = System.currentTimeMillis()
)

/**
 * محرك الفحص والتشخيص الحي لخدمات الوكيل الذكي (Live Agent Diagnostic Engine)
 * خالي تماماً من المحاكاة والتأخيرات المصطنعة — فحص شبكي حقيقي لقياس زمن الاستجابة والاتصال
 */
object AgentConnectivityEngine {

    /**
     * تحديد مصدر المفتاح بشفافية تامة
     */
    fun resolveKeyWithSource(context: Context): Triple<DetectedProvider, String, KeySource> {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        fun clean(v: String?) = KeyPoolManager.sanitizeApiKey(v ?: "")

        // 1. فحص التخزين المحلي للمستخدم أولاً (بتنظيف صارم للمحارف الخفية)
        val geminiPrefs = clean(prefs.getString("gemini_key", ""))
        if (geminiPrefs.isNotEmpty() && !geminiPrefs.lowercase().startsWith("your_")) {
            return Triple(DetectedProvider.GEMINI, geminiPrefs, KeySource.KEY_VAULT)
        }

        val openrouterPrefs = clean(prefs.getString("openrouter_key", ""))
        if (openrouterPrefs.isNotEmpty() && !openrouterPrefs.lowercase().startsWith("your_")) {
            return Triple(DetectedProvider.OPENROUTER, openrouterPrefs, KeySource.KEY_VAULT)
        }

        val groqPrefs = clean(prefs.getString("groq_key", ""))
        if (groqPrefs.isNotEmpty() && !groqPrefs.lowercase().startsWith("your_")) {
            return Triple(DetectedProvider.GROQ, groqPrefs, KeySource.KEY_VAULT)
        }

        val openaiPrefs = clean(prefs.getString("openai_key", ""))
        if (openaiPrefs.isNotEmpty() && !openaiPrefs.lowercase().startsWith("your_")) {
            return Triple(DetectedProvider.OPENAI, openaiPrefs, KeySource.KEY_VAULT)
        }

        // 2. فحص BuildConfig إذا لم يقم المستخدم بإدخال مفتاح في الواجهة
        val geminiBuild = runCatching {
            BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String
        }.getOrNull()?.trim() ?: ""
        if (geminiBuild.isNotEmpty() && !geminiBuild.lowercase().startsWith("your_")) {
            return Triple(DetectedProvider.GEMINI, geminiBuild, KeySource.BUILD_CONFIG)
        }

        val groqBuild = runCatching { BuildConfig.GROQ_API_KEY }.getOrNull()?.trim() ?: ""
        if (groqBuild.isNotEmpty() && !groqBuild.lowercase().startsWith("your_")) {
            return Triple(DetectedProvider.GROQ, groqBuild, KeySource.BUILD_CONFIG)
        }

        val openrouterBuild = runCatching { BuildConfig.OPENROUTER_API_KEY }.getOrNull()?.trim() ?: ""
        if (openrouterBuild.isNotEmpty() && !openrouterBuild.lowercase().startsWith("your_")) {
            return Triple(DetectedProvider.OPENROUTER, openrouterBuild, KeySource.BUILD_CONFIG)
        }

        return Triple(DetectedProvider.NONE, "", KeySource.NONE)
    }

    /**
     * موزّع الفحص الحي: يوجّه المفتاح إلى سيرفر مزوده الحقيقي بدل إرسال الكل إلى Gemini.
     * (كان زر الفحص يرسل مفاتيح OpenRouter/Groq إلى Google فيفشل دائماً بـ 400).
     */
    suspend fun pingProviderLive(
        provider: DetectedProvider,
        apiKey: String,
        keySource: KeySource
    ): LiveHealthStatus = withContext(Dispatchers.IO) {
        val clean = apiKey.trim().replace(Regex("\\s+"), "")
        if (clean.isBlank()) {
            return@withContext LiveHealthStatus(
                serviceName = provider.displayName,
                isConnected = false,
                latencyMs = 0L,
                statusDetail = "غير متصل - يلزم إدخال مفتاح API",
                keySource = KeySource.NONE,
                httpCode = 0,
                modelName = provider.defaultModel
            )
        }
        when (provider) {
            DetectedProvider.GEMINI -> pingGeminiLive(clean, keySource)
            DetectedProvider.OPENROUTER -> pingOpenRouterLive(clean, keySource)
            DetectedProvider.GROQ -> pingGenericBearerLive(
                provider = provider,
                apiKey = clean,
                keySource = keySource,
                url = if (clean.startsWith("xai-", ignoreCase = true))
                    "https://api.x.ai/v1/models" else "https://api.groq.com/openai/v1/models"
            )
            DetectedProvider.OPENAI -> pingGenericBearerLive(
                provider = provider,
                apiKey = clean,
                keySource = keySource,
                url = "https://api.openai.com/v1/models"
            )
            DetectedProvider.OPENCODE -> pingGenericBearerLive(
                provider = provider,
                apiKey = clean,
                keySource = keySource,
                url = "https://opencode.ai/inference/v1/models"
            )
            else -> LiveHealthStatus(
                serviceName = provider.displayName,
                isConnected = false,
                latencyMs = 0L,
                statusDetail = "المزود غير مدعوم للفحص الحي — احفظ المفتاح وسيُستخدم مباشرة",
                keySource = keySource,
                httpCode = 0,
                modelName = provider.defaultModel
            )
        }
    }

    /**
     * فحص OpenRouter + جلب هوية المفتاح (label/usage) لعرض اسم صاحب المفتاح.
     */
    suspend fun pingOpenRouterLive(apiKey: String, keySource: KeySource): LiveHealthStatus = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val modelsReq = Request.Builder()
                .url("https://openrouter.ai/api/v1/models")
                .header("Authorization", "Bearer $apiKey")
                .header("User-Agent", "Qabas-Studio")
                .get()
                .build()
            val modelsResp = SharedHttpClient.instance.newCall(modelsReq).execute()
            val latency = System.currentTimeMillis() - startTime
            val code = modelsResp.code
            modelsResp.close()
            if (!modelsResp.isSuccessful) {
                val detail = when (code) {
                    401 -> "المفتاح مرفوض (401) — غير صالح أو محذوف من openrouter.ai/keys"
                    402 -> "لا رصيد كافٍ (402) — اشحن حساب OpenRouter"
                    429 -> "تجاوز الحد (429) — انتظر دقيقة وأعد الفحص"
                    else -> "رفض الخادم (HTTP $code)"
                }
                return@withContext LiveHealthStatus(
                    serviceName = "OpenRouter AI Gateway",
                    isConnected = false,
                    latencyMs = latency,
                    statusDetail = detail,
                    keySource = keySource,
                    httpCode = code,
                    modelName = DetectedProvider.OPENROUTER.defaultModel
                )
            }
            // هوية المفتاح: الاسم والاستهلاك
            var identity = ""
            runCatching {
                val keyReq = Request.Builder()
                    .url("https://openrouter.ai/api/v1/auth/key")
                    .header("Authorization", "Bearer $apiKey")
                    .header("User-Agent", "Qabas-Studio")
                    .get()
                    .build()
                SharedHttpClient.instance.newCall(keyReq).execute().use { keyResp ->
                    if (keyResp.isSuccessful) {
                        val data = JSONObject(keyResp.body?.string().orEmpty()).optJSONObject("data")
                        val label = data?.optString("label").orEmpty().ifBlank { "مفتاح OpenRouter" }
                        val usage = data?.optDouble("usage", -1.0) ?: -1.0
                        val limit = data?.optDouble("limit", -1.0)
                        identity = if (usage >= 0) {
                            val limTxt = if (limit != null && limit > 0) " / $limit" else ""
                            "$label • الاستهلاك: $usage$limTxt"
                        } else label
                    }
                }
            }
            LiveHealthStatus(
                serviceName = "OpenRouter AI Gateway",
                isConnected = true,
                latencyMs = latency,
                statusDetail = "متصل بنجاح (${latency}ms) • استجابة حية${if (identity.isNotBlank()) " • $identity" else ""}",
                keySource = keySource,
                httpCode = code,
                modelName = identity.ifBlank { DetectedProvider.OPENROUTER.defaultModel }
            )
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            LiveHealthStatus(
                serviceName = "OpenRouter AI Gateway",
                isConnected = false,
                latencyMs = latency,
                statusDetail = "تعذر الاتصال بالشبكة: ${e.localizedMessage ?: e.message}",
                keySource = keySource,
                httpCode = -1,
                modelName = DetectedProvider.OPENROUTER.defaultModel
            )
        }
    }

    /**
     * فحص عام لمزودي Bearer (Groq/OpenAI): GET /models + عرض النموذج الافتراضي.
     */
    suspend fun pingGenericBearerLive(
        provider: DetectedProvider,
        apiKey: String,
        keySource: KeySource,
        url: String
    ): LiveHealthStatus = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .header("User-Agent", "Qabas-Studio")
                .get()
                .build()
            val response = SharedHttpClient.instance.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val code = response.code
            response.close()
            if (response.isSuccessful) {
                LiveHealthStatus(
                    serviceName = provider.displayName,
                    isConnected = true,
                    latencyMs = latency,
                    statusDetail = "متصل بنجاح (${latency}ms) • استجابة حية",
                    keySource = keySource,
                    httpCode = code,
                    modelName = provider.defaultModel
                )
            } else {
                val detail = when (code) {
                    401 -> "المفتاح مرفوض (401) — غير صالح أو منتهي"
                    403 -> "مرفوض (403) — رصيد أو صلاحيات"
                    429 -> "تجاوز الحد (429) — انتظر وأعد الفحص"
                    else -> "رفض الخادم (HTTP $code)"
                }
                LiveHealthStatus(
                    serviceName = provider.displayName,
                    isConnected = false,
                    latencyMs = latency,
                    statusDetail = detail,
                    keySource = keySource,
                    httpCode = code,
                    modelName = provider.defaultModel
                )
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            LiveHealthStatus(
                serviceName = provider.displayName,
                isConnected = false,
                latencyMs = latency,
                statusDetail = "تعذر الاتصال بالشبكة: ${e.localizedMessage ?: e.message}",
                keySource = keySource,
                httpCode = -1,
                modelName = provider.defaultModel
            )
        }
    }

    /**
     * فحص حي حقيقي لسيرفرات Google Gemini دون أي تأخير مصطنع
     */
    suspend fun pingGeminiLive(apiKey: String, keySource: KeySource): LiveHealthStatus = withContext(Dispatchers.IO) {        if (apiKey.isBlank()) {
            return@withContext LiveHealthStatus(
                serviceName = "Google Gemini",
                isConnected = false,
                latencyMs = 0L,
                statusDetail = "غير متصل - يلزم إدخال مفتاح API",
                keySource = KeySource.NONE,
                httpCode = 0,
                modelName = "gemini-3.5-flash"
            )
        }

        val startTime = System.currentTimeMillis()
        try {
            // استعلام خفيف لمعلومات النموذج gemini-3.5-flash
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "Qabas-Studio")
                .build()

            val response = SharedHttpClient.instance.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val code = response.code
            val bodyString = response.body?.string() ?: ""

            if (response.isSuccessful) {
                LiveHealthStatus(
                    serviceName = "Google Gemini",
                    isConnected = true,
                    latencyMs = latency,
                    statusDetail = "متصل بنجاح (${latency}ms) • استجابة حية",
                    keySource = keySource,
                    httpCode = code,
                    modelName = "gemini-3.5-flash"
                )
            } else {
                val errorMsg = try {
                    JSONObject(bodyString).optJSONObject("error")?.optString("message")
                        ?: "HTTP $code"
                } catch (e: Exception) {
                    "HTTP $code"
                }
                LiveHealthStatus(
                    serviceName = "Google Gemini",
                    isConnected = false,
                    latencyMs = latency,
                    statusDetail = "فشل المصادقة (HTTP $code): $errorMsg",
                    keySource = keySource,
                    httpCode = code,
                    modelName = "gemini-3.5-flash"
                )
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            LiveHealthStatus(
                serviceName = "Google Gemini",
                isConnected = false,
                latencyMs = latency,
                statusDetail = "تعذر الاتصال بالشبكة: ${e.localizedMessage ?: e.message}",
                keySource = keySource,
                httpCode = -1,
                modelName = "gemini-3.5-flash"
            )
        }
    }

    /**
     * فحص حي حقيقي لخدمات GitHub (مستودع أو سيرفر عام)
     */
    suspend fun pingGitHubLive(token: String, repoFullName: String = ""): LiveHealthStatus = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val builder = Request.Builder()
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Qabas-Studio")

            if (token.isNotBlank()) {
                val url = if (repoFullName.isNotBlank() && "/" in repoFullName) {
                    "https://api.github.com/repos/$repoFullName"
                } else {
                    "https://api.github.com/user"
                }
                builder.url(url).header("Authorization", "Bearer $token")
            } else {
                builder.url("https://api.github.com/zen")
            }

            val response = SharedHttpClient.instance.newCall(builder.build()).execute()
            val latency = System.currentTimeMillis() - startTime
            val code = response.code
            val bodyString = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val detail = if (token.isNotBlank()) {
                    if (repoFullName.isNotBlank() && "/" in repoFullName) {
                        val repoName = runCatching { JSONObject(bodyString).optString("full_name") }.getOrNull() ?: repoFullName
                        "متصل بنجاح (${latency}ms) • المستودع: $repoName"
                    } else {
                        val login = runCatching { JSONObject(bodyString).optString("login") }.getOrNull() ?: "User OK"
                        "متصل بنجاح (${latency}ms) • الحساب: $login"
                    }
                } else {
                    "سيرفر GitHub نشط (${latency}ms) • لم يتم ضبط Token المستودع"
                }
                LiveHealthStatus(
                    serviceName = "GitHub Engine",
                    isConnected = true,
                    latencyMs = latency,
                    statusDetail = detail,
                    keySource = if (token.isNotBlank()) KeySource.KEY_VAULT else KeySource.NONE,
                    httpCode = code,
                    modelName = "REST API v3"
                )
            } else {
                val detail = when (code) {
                    401 -> "رمز الوصول (Token) غير صالح أو منتهي الصلاحية (HTTP 401)"
                    404 -> "المستودع أو المورد غير موجود (HTTP 404)"
                    403 -> "تجاوز حد الاستعلامات المسموح به لـ GitHub (HTTP 403)"
                    else -> "استجابة غير ناجحة من GitHub (HTTP $code)"
                }
                LiveHealthStatus(
                    serviceName = "GitHub Engine",
                    isConnected = false,
                    latencyMs = latency,
                    statusDetail = detail,
                    keySource = if (token.isNotBlank()) KeySource.KEY_VAULT else KeySource.NONE,
                    httpCode = code,
                    modelName = "REST API v3"
                )
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            LiveHealthStatus(
                serviceName = "GitHub Engine",
                isConnected = false,
                latencyMs = latency,
                statusDetail = "تعذر الاتصال بـ GitHub: ${e.localizedMessage ?: e.message}",
                keySource = KeySource.NONE,
                httpCode = -1,
                modelName = "REST API v3"
            )
        }
    }

    /**
     * فحص كل مفاتيح المسبح لمزود معين، وتسجيل زمن الاستجابة، ثم ترتيب المسبح
     * حسب الأسرع المتصل وتفعيله تلقائياً. يُعيد (متصل؟، عدد المفحوص، أسرع latency).
     * يُستخدم من زر "ترتيب حسب الأسرع ⚡" في شاشة المفاتيح.
     */
    suspend fun rankPoolBySpeed(
        prefsKey: String,
        provider: DetectedProvider,
        keySource: KeySource = KeySource.KEY_VAULT
    ): Triple<Boolean, Int, Long> = withContext(Dispatchers.IO) {
        val keys = KeyPoolManager.getKeys(prefsKey)
        if (keys.isEmpty()) return@withContext Triple(false, 0, 0L)
        var bestMs = Long.MAX_VALUE
        var anyConnected = false
        for (k in keys) {
            val st = pingProviderLive(provider, k, keySource)
            KeyLatencyTracker.record(prefsKey, k, st.latencyMs, st.isConnected)
            if (st.isConnected) {
                anyConnected = true
                if (st.latencyMs < bestMs) bestMs = st.latencyMs
            }
        }
        if (anyConnected) {
            KeyPoolManager.sortPoolByLatency(prefsKey)
            UniversalKeyBrain.notifyKeyChanged()
        }
        Triple(anyConnected, keys.size, if (bestMs == Long.MAX_VALUE) 0L else bestMs)
    }

    fun prefsKeyToProvider(prefsKey: String): DetectedProvider = when (prefsKey) {
        "gemini_key" -> DetectedProvider.GEMINI
        "groq_key" -> DetectedProvider.GROQ
        "openai_key" -> DetectedProvider.OPENAI
        "openrouter_key" -> DetectedProvider.OPENROUTER
        else -> DetectedProvider.NONE
    }

    /**
     * توليد الكود الفعلي وحل المهمة برمجياً عبر استدعاء حي لنموذج الذكاء الاصطناعي
     */
    suspend fun executeRealCodeSynthesis(
        provider: DetectedProvider,
        apiKey: String,
        userPrompt: String,
        architecturalPlan: String,
        model: String? = null,
        stream: Boolean = false,
        onPartial: ((String) -> Unit)? = null
    ): Result<RealAgentResponse> = withContext(Dispatchers.IO) {
        val synthesisInstruction = """
            أنت مهندس برمجيات متخصص في نظام Android (Kotlin + Jetpack Compose + Material 3).
            بناءً على خطة العمل التالية:
            $architecturalPlan
            
            قم بكتابة الكود البرمجي الدقيق أو كود التعديل المطلوب لتنفيذ الأمر:
            «$userPrompt»
            
            يجب أن يكون الكود حقيقياً، مكتوباً بلغة Kotlin النظيفة، ومتوافقاً مع Material Design 3 وقواعد عدم تداخل النصوص.
            قدم الكود داخل كتل برمجية واضحة مع شرح دقيق وموجز للتغييرات.
        """.trimIndent()

        UniversalKeyBrain.executeRealAgentStepWithFailover(
            provider = provider,
            prompt = "اكتب الكود التنفيذي الكامل للتعديل المطلوب بناءً على الخطة المعتمدة.",
            systemInstruction = synthesisInstruction,
            fallbackSingleKey = apiKey,
            model = model,
            stream = stream,
            onPartial = onPartial
        )
    }
}
