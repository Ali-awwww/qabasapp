package com.qabas.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * مزودات الذكاء الاصطناعي المدعومة في النظام
 */
enum class AiProviderType(val displayName: String, val defaultEndpoint: String, val icon: String) {
    OPENROUTER("OpenRouter (Multi-Model Hub)", "https://openrouter.ai/api/v1/chat/completions", "🌐"),
    DEEPSEEK("DeepSeek AI (Official)", "https://api.deepseek.com/chat/completions", "🧠"),
    GROQ("Groq (Ultra-Fast Inference)", "https://api.groq.com/openai/v1/chat/completions", "⚡"),
    OPENAI("OpenAI (GPT-4o / Mini)", "https://api.openai.com/v1/chat/completions", "💠"),
    GEMINI("Google Gemini Gateway", "https://generativelanguage.googleapis.com", "✨"),
    POLLINATIONS("Pollinations AI (Free Edge)", "https://text.pollinations.ai", "🌸"),
    CUSTOM_OPENAI("Custom OpenAI-Compatible Endpoint", "", "⚙️")
}

/**
 * نموذج إعدادات النموذج المسجل
 */
data class AiModelConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val provider: AiProviderType,
    val modelIdentifier: String,
    val apiKey: String = "",
    val customEndpoint: String = "",
    val temperature: Double = 0.7,
    val maxTokens: Int = 4096,
    val systemPrompt: String = "",
    val isCustom: Boolean = false,
    val dateAdded: Long = System.currentTimeMillis()
) {

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("provider", provider.name)
            put("modelIdentifier", modelIdentifier)
            put("apiKey", apiKey)
            put("customEndpoint", customEndpoint)
            put("temperature", temperature)
            put("maxTokens", maxTokens)
            put("systemPrompt", systemPrompt)
            put("isCustom", isCustom)
            put("dateAdded", dateAdded)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): AiModelConfig {
            val provName = json.optString("provider", AiProviderType.OPENROUTER.name)
            val prov = try {
                AiProviderType.valueOf(provName)
            } catch (_: Exception) {
                AiProviderType.OPENROUTER
            }
            return AiModelConfig(
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.optString("name", "نموذج افتراضي"),
                provider = prov,
                modelIdentifier = json.optString("modelIdentifier", "openrouter/auto"),
                apiKey = json.optString("apiKey", ""),
                customEndpoint = json.optString("customEndpoint", ""),
                temperature = json.optDouble("temperature", 0.7),
                maxTokens = json.optInt("maxTokens", 4096),
                systemPrompt = json.optString("systemPrompt", ""),
                isCustom = json.optBoolean("isCustom", false),
                dateAdded = json.optLong("dateAdded", System.currentTimeMillis())
            )
        }
    }
}

/**
 * سجل وإدارة نماذج الذكاء الاصطناعي المسجلة (OpenRouter, DeepSeek, Groq, OpenAI, Gemini, Custom)
 */
object AiModelRegistry {
    private const val PREFS_NAME = "qabas_ai_registry_prefs"
    private const val KEY_MODELS = "registered_models_v2"
    private const val KEY_ACTIVE_MODEL_ID = "active_ai_model_id"

    private val _activeModelIdFlow = MutableStateFlow("")
    val activeModelIdFlow = _activeModelIdFlow.asStateFlow()

    fun init(context: Context) {
        _activeModelIdFlow.value = getActiveModelId(context)
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** النماذج الجاهزة مسبقاً في النظام */
    val PRESET_MODELS = listOf(
        AiModelConfig(
            id = "preset_openrouter_auto",
            name = "OpenRouter Auto Router",
            provider = AiProviderType.OPENROUTER,
            modelIdentifier = "openrouter/auto",
            systemPrompt = "أنت مهندس ذكاء اصطناعي متخصص في كود Android و Kotlin وتطبيق خادم المسلم الإسلامي."
        ),
        AiModelConfig(
            id = "preset_openrouter_claude",
            name = "Claude 3.7 Sonnet (via OpenRouter)",
            provider = AiProviderType.OPENROUTER,
            modelIdentifier = "anthropic/claude-3.7-sonnet",
            systemPrompt = "أنت مساعد برمجي خبير بكود Android و Jetpack Compose."
        ),
        AiModelConfig(
            id = "preset_deepseek_v3",
            name = "DeepSeek V3 (Chat & Code)",
            provider = AiProviderType.DEEPSEEK,
            modelIdentifier = "deepseek-chat",
            systemPrompt = "أنت مهندس برمجيات ذكي دقيق في فحص وحل أخطاء Kotlin و Compose."
        ),
        AiModelConfig(
            id = "preset_deepseek_r1",
            name = "DeepSeek R1 (Deep Reasoning)",
            provider = AiProviderType.DEEPSEEK,
            modelIdentifier = "deepseek-reasoner",
            systemPrompt = "أنت عقل برمجي استدلالي عميق لحل المعضلات البرمجية المعقدة."
        ),
        AiModelConfig(
            id = "preset_groq_llama70b",
            name = "Groq Llama 3.3 70B (Fast)",
            provider = AiProviderType.GROQ,
            modelIdentifier = "llama-3.3-70b-versatile",
            systemPrompt = "أنت مهندس دعم ومطور سريع وفائق الاستجابة."
        ),
        AiModelConfig(
            id = "preset_openai_gpt4o",
            name = "OpenAI GPT-4o Mini",
            provider = AiProviderType.OPENAI,
            modelIdentifier = "gpt-4o-mini",
            systemPrompt = "أنت مساعد برمجي متقن لتطبيقات الأندرويد والمنظومة الفضائية."
        ),
        AiModelConfig(
            id = "preset_gemini_flash",
            name = "Google Gemini 2.5 Flash",
            provider = AiProviderType.GEMINI,
            modelIdentifier = "gemini-2.5-flash",
            systemPrompt = "أنت نواة الذكاء الاصطناعي الرسمية لمشروع خادم المسلم."
        )
    )

    /** جلب قائمة النماذج المسجلة (المسبقة + المخصصة) */
    fun getRegisteredModels(context: Context): List<AiModelConfig> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_MODELS, null)
        val customList = mutableListOf<AiModelConfig>()

        if (!raw.isNullOrBlank()) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    customList.add(AiModelConfig.fromJson(arr.getJSONObject(i)))
                }
            } catch (_: Exception) {}
        }

        // دمج النماذج المسبقة مع المخصصة
        val allModels = mutableListOf<AiModelConfig>()
        allModels.addAll(PRESET_MODELS)
        for (custom in customList) {
            val existingIdx = allModels.indexOfFirst { it.id == custom.id }
            if (existingIdx >= 0) {
                allModels[existingIdx] = custom
            } else {
                allModels.add(custom)
            }
        }
        return allModels
    }

    /** حذف نموذج مخصص */
    fun deleteModel(context: Context, modelId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val all = getRegisteredModels(context).filter { it.id != modelId && (it.isCustom || it.apiKey.isNotBlank()) }
        val arr = JSONArray()
        all.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_MODELS, arr.toString()).apply()

        if (getActiveModelId(context) == modelId) {
            setActiveModel(context, PRESET_MODELS.first().id)
        }
    }

    /** معرف النموذج النشط */
    fun getActiveModelId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVE_MODEL_ID, PRESET_MODELS.first().id) ?: PRESET_MODELS.first().id
    }

    /** النموذج النشط حالياً */
    fun getActiveModel(context: Context): AiModelConfig {
        val activeId = getActiveModelId(context)
        return getRegisteredModels(context).find { it.id == activeId } ?: PRESET_MODELS.first()
    }

    /** تعيين النموذج النشط */
    fun setActiveModel(context: Context, modelId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACTIVE_MODEL_ID, modelId).apply()
        _activeModelIdFlow.value = modelId
    }

}
