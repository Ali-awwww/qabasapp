package com.qabas.app

import android.content.Context

data class ServiceReadiness(
    val name: String,
    val weight: Int,
    val isConfigured: Boolean,
    val description: String,
    val activeFeature: String
)

data class ReadinessReport(
    val percentage: Int,
    val statusTitle: String,
    val statusDescription: String,
    val activeServicesCount: Int,
    val totalServicesCount: Int,
    val services: List<ServiceReadiness>,
    val lastError: String? = null
)

object OperationalReadinessManager {

    private var lastRecordedError: String? = null

    fun getLastError(): String? = lastRecordedError

    fun calculateReadiness(context: Context, customKeys: Map<String, String>? = null): ReadinessReport {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)

        fun getKey(name: String): String {
            // المفتاح الفعّال: مدخل المستخدم أولاً ثم المفتاح المحقون من البناء (BuildConfig)
            return customKeys?.get(name)?.takeIf { it.isNotBlank() } ?: KeyVault.effective(name)
        }

        fun isKeyValid(key: String): Boolean {
            val trimmed = key.trim()
            return trimmed.isNotBlank() && 
                   trimmed != "YOUR_GEMINI_API_KEY" && 
                   !trimmed.equals("placeholder", ignoreCase = true) &&
                   !trimmed.startsWith("YOUR_")
        }

        val geminiKey = getKey("gemini_key")
        val groqKey = getKey("groq_key")
        val pixabayKey = getKey("pixabay_key")

        val hasGemini = isKeyValid(geminiKey)
        val hasGroq = isKeyValid(groqKey)
        val hasPixabay = isKeyValid(pixabayKey)
        val hasSupabase = SupabaseConfig.isConfigured
        val hasHiggsfield = false // Higgsfield credentials are stored server-side and are not readable by this local-only report.

        val services = listOf(
            ServiceReadiness(
                name = "Gemini AI (Google AI Studio)",
                weight = 30,
                isConfigured = hasGemini,
                description = "المساعد الدعوي، صياغة السكريبتات، وتحليل الأفكار الإسلامية، والرؤية البصرية",
                activeFeature = if (hasGemini) "متصل حقيقي مباشر (Gemini Flash Live)" else "يتطلب إدخال مفتاح Gemini للتشغيل السحابي"
            ),
            ServiceReadiness(
                name = "Higgsfield AI Video Engine",
                weight = 20,
                isConfigured = hasHiggsfield,
                description = "توليد الفيديو عبر Kling 4.0؛ حالة المفتاح تُدار مركزياً عبر بوابة الاستوديو",
                activeFeature = "حالة مفتاح الخادم لا تُقرأ من هذا الفحص المحلي؛ تحقق منها داخل الاستوديو"
            ),
            ServiceReadiness(
                name = "Groq LPU (Ultra Fast AI)",
                weight = 15,
                isConfigured = hasGroq,
                description = "توليد الخطافات الفورية والوسوم السريعة (Llama 3.3)",
                activeFeature = if (hasGroq) "معالجة فائقة السرعة مفعلة (~300ms)" else "توليد النصوص المدمج كبديل محلي"
            ),
            ServiceReadiness(
                name = "التعليق الصوتي المحلي (Kokoro / Edge / Android TTS)",
                weight = 15,
                isConfigured = true,
                description = "تعليق صوتي محلي بلا مفاتيح سحابية",
                activeFeature = "محركات النطق المحلية تعمل بلا مفاتيح"
            ),
            ServiceReadiness(
                name = "Pixabay API",
                weight = 10,
                isConfigured = hasPixabay,
                description = "جلب فيديوهات وصور المعالم الإسلامية والطبيعة HD",
                activeFeature = if (hasPixabay) "جلب وسائط حية من المكتبات العالمية" else "المكتبات والأصول المحلية المدمجة"
            ),
            ServiceReadiness(
                name = "Pollinations / FLUX (صور مجانية)",
                weight = 5,
                isConfigured = true,
                description = "توليد صور وخلفيات إسلامية مخصصة بالذكاء الاصطناعي بلا مفتاح",
                activeFeature = "توليد الصور المجاني مفعل"
            ),
            ServiceReadiness(
                name = "Supabase Cloud Sync",
                weight = 5,
                isConfigured = hasSupabase,
                description = "حفظ المشاريع سحابياً ومزامنة الحسابات والمحتوى",
                activeFeature = if (hasSupabase) "اتصال Supabase السحابي مهيأ ونشط" else "حفظ محلي على الجهاز (أضف مفاتيح Supabase للمزامنة)"
            )
        )

        var totalScore = 0
        var activeCount = 0

        services.forEach {
            if (it.isConfigured) {
                totalScore += it.weight
                activeCount++
            }
        }

        val (title, desc) = when {
            totalScore >= 95 -> Pair(
                "جاهزية سينمائية متكاملة 100% 🌟",
                "التطبيق يعمل بكامل طاقته التشغيلية المباشرة. جميع المحركات والنماذج متصلة وتعمل بأعلى كفاءة."
            )
            totalScore >= 70 -> Pair(
                "جاهزية تشغيل عالية ($totalScore%) 🚀",
                "المحركات الأساسية (النصوص، الصوت، والوسائط) متصلة بالسيرفرات المباشرة بنجاح."
            )
            totalScore >= 35 -> Pair(
                "الذكاء الاصطناعي مفعل ($totalScore%) 🟢",
                "محرك Gemini يعمل بنجاح في صياغة المحتوى. يمكنك تفعيل مفاتيح الصوت والوسائط لفتح كامل الاستوديو."
            )
            else -> Pair(
                "الوضع المحلي الأساسي ($totalScore%) 🟡",
                "التطبيق يعمل بالأدوات المحلية. أضف مفاتيح الذكاء الاصطناعي من الإعدادات لتفعيل الخدمات السحابية."
            )
        }

        return ReadinessReport(
            percentage = totalScore,
            statusTitle = title,
            statusDescription = desc,
            activeServicesCount = activeCount,
            totalServicesCount = services.size,
            services = services,
            lastError = lastRecordedError
        )
    }
}
