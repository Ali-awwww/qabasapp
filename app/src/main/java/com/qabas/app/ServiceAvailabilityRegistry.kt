package com.qabas.app

import android.content.Context

/**
 * Central Source of Truth for external API keys, local models, cloud backend, and Play Billing readiness.
 * UI actions that require an external credential/integration query this registry to disable/gate cleanly.
 */
object ServiceAvailabilityRegistry {

    data class ServiceStatus(
        val id: String,
        val displayName: String,
        val isAvailable: Boolean,
        val modeLabel: String,
        val missingReason: String
    )

    fun isSupabaseReady(): Boolean = SupabaseConfig.isConfigured

    fun isUserSignedIn(): Boolean = SupabaseServices.Auth.getCurrentUserId() != null

    fun hasCloudLlmKey(context: Context): Boolean {
        return KeyVault.gemini.isNotBlank() ||
            KeyVault.groq.isNotBlank() ||
            KeyVault.openai.isNotBlank() ||
            KeyVault.openrouter.isNotBlank() ||
            KeyVault.huggingface.isNotBlank() ||
            KeyVault.anthropic.isNotBlank() ||
            KeyVault.opencode.isNotBlank()
    }

    fun hasLocalAiModel(context: Context): Boolean = OnDeviceModelManager.hasAnyInstalledModel(context)

    fun hasAnyAiAssistantEngine(context: Context): Boolean {
        return hasCloudLlmKey(context) || hasLocalAiModel(context) || (isSupabaseReady() && isUserSignedIn())
    }

    fun hasCloudTtsKey(): Boolean {
        return KeyVault.elevenlabs.isNotBlank() || KeyVault.azureSpeechKey.isNotBlank()
    }

    fun hasStockMediaKey(): Boolean {
        return KeyVault.pexels.isNotBlank() ||
            KeyVault.pixabay.isNotBlank() ||
            KeyVault.coverr.isNotBlank()
    }

    fun hasLeonardoKey(context: Context): Boolean = ApiKeyManager.getLeonardoKey(context).isNotBlank()

    fun hasHiggsfieldGateway(): Boolean = isSupabaseReady() && isUserSignedIn()

    fun isGooglePlayBillingReady(context: Context): Boolean {
        val manager = BillingManager.getInstance(context)
        return manager.isReady.value && manager.products.value.isNotEmpty()
    }

    fun isGitHubOAuthConfigured(): Boolean = GitHubAuthManager.isConfigured()

    fun canPublishDirectlyViaSocialApi(): Boolean {
        // Direct REST upload requires verified per-platform OAuth client credentials & upload endpoints.
        return false
    }

    fun getAllServiceStatuses(context: Context): List<ServiceStatus> {
        val billingReady = isGooglePlayBillingReady(context)
        return listOf(
            ServiceStatus(
                id = "supabase",
                displayName = "السحابة والمزامنة (Supabase)",
                isAvailable = isSupabaseReady(),
                modeLabel = if (isSupabaseReady()) "متصل" else "يتطلب إعداد",
                missingReason = "مفاتيح SUPABASE_URL و SUPABASE_ANON_KEY غير مضافة."
            ),
            ServiceStatus(
                id = "cloud_llm",
                displayName = "الذكاء الاصطناعي السحابي (LLM)",
                isAvailable = hasCloudLlmKey(context),
                modeLabel = if (hasCloudLlmKey(context)) "مفتاح API نشط" else "غير مفعّل",
                missingReason = "أدخل مفتاح Gemini أو Groq أو OpenAI أو OpenRouter في شاشة مفاتيح API."
            ),
            ServiceStatus(
                id = "local_ai",
                displayName = "الذكاء المحلي بدون إنترنت (GGUF)",
                isAvailable = hasLocalAiModel(context),
                modeLabel = if (hasLocalAiModel(context)) "نموذج مثبت" else "غير محمّل",
                missingReason = "قم بتنزيل نموذج محلي من إعدادات الذكاء الأوفلاين للعمل بدون إنترنت."
            ),
            ServiceStatus(
                id = "stock_media",
                displayName = "مكتبات الفيديو والصور (Pexels / Pixabay)",
                isAvailable = hasStockMediaKey(),
                modeLabel = if (hasStockMediaKey()) "مفعّل" else "يتطلب مفتاح API",
                missingReason = "أدخل مفتاح Pexels أو Pixabay أو Coverr في شاشة مفاتيح API."
            ),
            ServiceStatus(
                id = "leonardo",
                displayName = "توليد الصور (Leonardo AI)",
                isAvailable = hasLeonardoKey(context),
                modeLabel = if (hasLeonardoKey(context)) "مفعّل" else "يتطلب مفتاح API",
                missingReason = "أدخل مفتاح Leonardo AI من شاشة مفاتيح API."
            ),
            ServiceStatus(
                id = "cloud_tts",
                displayName = "الأصوات العصبية السحابية (ElevenLabs / Azure)",
                isAvailable = hasCloudTtsKey(),
                modeLabel = if (hasCloudTtsKey()) "مفعّل" else "محرك النظام فقط",
                missingReason = "التعليق الصوتي السحابي يتطلب مفتاح ElevenLabs أو Azure Speech، مع توفر نطق النظام المحلي دائماً."
            ),
            ServiceStatus(
                id = "play_billing",
                displayName = "فوترة Google Play",
                isAvailable = billingReady,
                modeLabel = if (billingReady) "جاهز" else "غير متاح",
                missingReason = "متجر Google Play Billing غير متصل أو لم يتم جلب المنتجات على هذا الجهاز."
            ),
            ServiceStatus(
                id = "github",
                displayName = "تكامل GitHub OAuth",
                isAvailable = isGitHubOAuthConfigured(),
                modeLabel = if (isGitHubOAuthConfigured()) "مُهيّأ" else "غير مهيأ",
                missingReason = "يتطلب مفاتيح AW_CLIENT_ID و AW_CLIENT_SECRET في أسرار البناء."
            )
        )
    }
}
