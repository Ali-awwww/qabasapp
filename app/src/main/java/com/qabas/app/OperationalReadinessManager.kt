package com.qabas.app

import android.content.Context
import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private var lastRecordedTime: String? = null

    fun recordLastError(service: String, message: String) {
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timeStr = timeFormat.format(Date())
        val formatted = "[$service $timeStr] $message"
        lastRecordedError = formatted
        lastRecordedTime = timeStr
        SystemLogsManager.addLog("ERROR", formatted, Color(0xFFEF4444))
    }

    fun recordLastError(message: String) {
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timeStr = timeFormat.format(Date())
        val formatted = "[$timeStr] $message"
        lastRecordedError = formatted
        lastRecordedTime = timeStr
        SystemLogsManager.addLog("ERROR", formatted, Color(0xFFEF4444))
    }

    fun getLastError(): String? = lastRecordedError

    fun clearLastError() {
        lastRecordedError = null
        lastRecordedTime = null
    }

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
        val huggingfaceKey = getKey("huggingface_key")
        val pexelsKey = getKey("pexels_key")
        val pixabayKey = getKey("pixabay_key")
        val firebaseKey = getKey("firebase_key")
        val higgsfieldKey = getKey("higgsfield_api_key")
        val azureKey = getKey("azure_speech_key")
        val elevenlabsKey = getKey("elevenlabs_key")

        val hasGemini = isKeyValid(geminiKey)
        val hasGroq = isKeyValid(groqKey)
        val hasHuggingFace = isKeyValid(huggingfaceKey)
        val hasPexels = isKeyValid(pexelsKey)
        val hasPixabay = isKeyValid(pixabayKey)
        val hasFirebase = isKeyValid(firebaseKey)
        val hasHiggsfield = isKeyValid(higgsfieldKey)
        val hasAudioCloud = isKeyValid(azureKey) || isKeyValid(elevenlabsKey)

        val services = listOf(
            ServiceReadiness(
                name = "Gemini AI (Google AI Studio)",
                weight = 30,
                isConfigured = hasGemini,
                description = "المساعد الدعوي، صياغة السكريبتات، وتحليل الأفكار الإسلامية، والرؤية البصرية",
                activeFeature = if (hasGemini) "متصل حقيقي مباشر (Gemini Flash Live)" else "التحليل البلاغي المحلي الصادق (أضف المفتاح للتشغيل السحابي)"
            ),
            ServiceReadiness(
                name = "Higgsfield AI Video Engine",
                weight = 20,
                isConfigured = hasHiggsfield,
                description = "توليد الفيديو السينمائي الفائق وتحريك الصور بالذكاء الاصطناعي (Text/Image to Video)",
                activeFeature = if (hasHiggsfield) "توليد سينمائي مباشر مفعل (Higgsfield Cloud)" else "قوالب ومحركات الرندرة والمونتاج المدمجة محلياً"
            ),
            ServiceReadiness(
                name = "Groq LPU (Ultra Fast AI)",
                weight = 15,
                isConfigured = hasGroq,
                description = "توليد الخطافات الفورية والوسوم السريعة (Llama 3.3)",
                activeFeature = if (hasGroq) "معالجة فائقة السرعة مفعلة (~300ms)" else "توليد النصوص المدمج و Gemini كبديل صادق"
            ),
            ServiceReadiness(
                name = "Azure / ElevenLabs Audio",
                weight = 15,
                isConfigured = hasAudioCloud,
                description = "التعليق الصوتي العصبي الفصيح ونبرات السرد الوثائقي",
                activeFeature = if (hasAudioCloud) "التعليق الصوتي العصبي عالي النقاء متصل" else "محرك Edge-TTS الصوتي والمحلي المدمج مجاناً"
            ),
            ServiceReadiness(
                name = "Pexels & Pixabay APIs",
                weight = 10,
                isConfigured = hasPexels || hasPixabay,
                description = "جلب فيديوهات وصور المعالم الإسلامية والطبيعة HD",
                activeFeature = if (hasPexels || hasPixabay) "جلب وسائط حية من المكتبات العالمية" else "أرشيف Wikimedia و NASA والمشاهد المحلية المخزنة"
            ),
            ServiceReadiness(
                name = "Hugging Face / FLUX 8K",
                weight = 5,
                isConfigured = hasHuggingFace,
                description = "توليد صور وخلفيات إسلامية مخصصة بالذكاء الاصطناعي",
                activeFeature = if (hasHuggingFace) "توليد صور الذكاء الاصطناعي مفعل" else "محرك FLUX.1 المدمج والتصاميم الجاهزة"
            ),
            ServiceReadiness(
                name = "Firebase / Cloud Sync",
                weight = 5,
                isConfigured = hasFirebase,
                description = "حفظ المشاريع سحابياً ومزامنة مجتمع الإلهام",
                activeFeature = if (hasFirebase) "مزامنة سحابية حقيقية مفعلة" else "حفظ محلي فائق السرعة في قاعدة بيانات Room"
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
                "وضع الاستجابة المحلية ($totalScore%) 🟡",
                "التطبيق يعمل بالوضع المحلي المحاكي. أضف مفتاح Gemini المجاني من الإعدادات للبدء الفوري بالذكاء الاصطناعي."
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
