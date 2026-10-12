package com.qabas.app

import androidx.compose.ui.graphics.Color

/**
 * بيانات منصة توفير مفاتيح نماذج البرمجة المجانية
 */
data class CodingPlatformGuide(
    val id: String,
    val rank: Int,
    val name: String,
    val provider: DetectedProvider,
    val badge: String,
    val badgeColor: Color,
    val powerScore: String,
    val speedScore: String,
    val keyModels: List<String>,
    val description: String,
    val apiKeyUrl: String,
    val freeTierInfo: String,
    val steps: List<String>
)

/**
 * دليل المنصات المعتمدة لمفاتيح نماذج البرمجة مرتبة حسب القوة والسرعة
 */
object CodingPlatformDirectory {

    val platforms: List<CodingPlatformGuide> = listOf(
        CodingPlatformGuide(
            id = "google_ai_studio",
            rank = 1,
            name = "Google AI Studio",
            provider = DetectedProvider.GEMINI,
            badge = "الأعلى ذكاءً ومعمارية 🧠",
            badgeColor = Color(0xFF00E5FF),
            powerScore = "9.9 / 10",
            speedScore = "9.4 / 10",
            keyModels = listOf("gemini-3.5-flash", "gemini-3.1-pro-preview"),
            description = "المنصة الرسمية من Google لبيئة Gemini. توفر أقوى قدرات التفكير المعماري، تحليل كود Android و Kotlin، ونافذة سياق ضخمة تتجاوز مليون رمز.",
            apiKeyUrl = "https://aistudio.google.com/app/apikey",
            freeTierInfo = "مجاني تماماً للاستخدام الشخصي والمطورين (حتى 15 طلب/دقيقة) بدون اشتراط بطاقة بنكية.",
            steps = listOf(
                "افتح رابط Google AI Studio وسجّل الدخول بحساب Google الخاص بك.",
                "اضغط على زر «Create API key» في الزاوية العلوية.",
                "اختر مشروع Google Cloud افتراضي أو أنشئ مشروعاً جديداً بنقرة واحدة.",
                "انسخ المفتاح الذي يبدأ بـ «AIza...» والصقه في التطبيق، ثم أجرِ الفحص الحي."
            )
        ),
        CodingPlatformGuide(
            id = "groq_cloud",
            rank = 2,
            name = "Groq Cloud Console",
            provider = DetectedProvider.GROQ,
            badge = "الأسرع عالمياً ⚡",
            badgeColor = Color(0xFFF59E0B),
            powerScore = "9.3 / 10",
            speedScore = "10 / 10 (300+ tps)",
            keyModels = listOf("llama-3.3-70b-versatile", "qwen-2.5-coder-32b"),
            description = "أسرع منصة استدلال ذكاء اصطناعي على الإطلاق تعمل بوحدات LPU الفائقة. تولد مئات الرموز البرمجية في الثانية لواجهات فورية وبناء سريع.",
            apiKeyUrl = "https://console.groq.com/keys",
            freeTierInfo = "حصة مجانية ممتازة للمطورين دون قيود مالية مع سرعة استجابة آنية.",
            steps = listOf(
                "ادخل إلى صفحة Groq Console وسجل بحساب Google أو GitHub.",
                "توجه إلى قسم «API Keys» من القائمة الجانبية.",
                "اضغط على «Create API Key» وسمِّ المفتاح (مثلاً: Qabas-Agent).",
                "انسخ المفتاح الذي يبدأ بـ «gsk_...» والصقه في التطبيق لتفعيل السرعة القصوى."
            )
        ),
        CodingPlatformGuide(
            id = "openrouter",
            rank = 3,
            name = "OpenRouter AI Gateway",
            provider = DetectedProvider.OPENROUTER,
            badge = "بوابة النماذج المتعددة 🌐",
            badgeColor = Color(0xFFE5B842),
            powerScore = "9.7 / 10",
            speedScore = "8.9 / 10",
            keyModels = listOf("deepseek/deepseek-r1:free", "qwen/qwen-2.5-coder-32b:free"),
            description = "بوابة شاملة تتيح الوصول لأحدث وأقوى نماذج البرمجة في العالم مثل DeepSeek R1 و Qwen Coder مع توفير نماذج مجانية بدون أي تكلفة.",
            apiKeyUrl = "https://openrouter.ai/keys",
            freeTierInfo = "نماذج مجانية كاملة ومحددة بوسم (:free) متاحة فور إنشاء الحساب.",
            steps = listOf(
                "افتح موقع OpenRouter وسجّل الدخول بحساب Google أو المحفظة الرقمية.",
                "انقر على صورة حسابك ثم اختر «Keys».",
                "اضغط على «Create Key»، حدد اسماً للمفتاح ولا تضع حداً مالياً (Free Tier).",
                "انسخ المفتاح الذي يبدأ بـ «sk-or-...» والصقه هنا للاستفادة من النماذج المتعددة."
            )
        ),
        CodingPlatformGuide(
            id = "mistral_ai",
            rank = 4,
            name = "Mistral AI (Codestral)",
            provider = DetectedProvider.OPENROUTER,
            badge = "متخصص كود حصرياً 💻",
            badgeColor = Color(0xFFA855F7),
            powerScore = "9.2 / 10",
            speedScore = "9.1 / 10",
            keyModels = listOf("codestral-latest", "mistral-small-latest"),
            description = "منصة فرنسية رائدة، وموديل Codestral مدرب خصيصاً على أكثر من 80 لغة برمجة ومحسن لإكمال الأكواد المعقدة وبناء هياكل Compose.",
            apiKeyUrl = "https://console.mistral.ai/api-keys/",
            freeTierInfo = "ترخيص استخدام مجاني للأبحاث والبرمجة الشخصية عبر La Plateforme.",
            steps = listOf(
                "انتقل إلى Mistral Console وسجل الدخول بحساب المطورين.",
                "توجه إلى تبويب «API Keys» في لوحة التحكم.",
                "اضغط على «Create new key» وانسخ الرمز السري.",
                "يمكن استخدام المفتاح مباشرة أو توجيهه للوكيل الذكي."
            )
        ),
        CodingPlatformGuide(
            id = "github_models",
            rank = 5,
            name = "GitHub Models Marketplace",
            provider = DetectedProvider.OPENAI,
            badge = "بيئة مطوري GitHub 🐙",
            badgeColor = Color(0xFF10B981),
            powerScore = "9.4 / 10",
            speedScore = "8.6 / 10",
            keyModels = listOf("gpt-4o", "meta-llama-3.3-70b-instruct"),
            description = "خدمة رسمية من GitHub توفر وصولاً مجانياً لأقوى نماذج العالم (GPT-4o, Llama 3) لاختبار وبرمجة التطبيقات باستخدام حساب GitHub فقط.",
            apiKeyUrl = "https://github.com/marketplace/models",
            freeTierInfo = "متاح مجاناً لجميع حسابات GitHub دون الحاجة لأي اشتراك مدفوع.",
            steps = listOf(
                "افتح صفحة GitHub Models وسجل دخولك بحساب GitHub.",
                "اختر النموذج البرمجي المطلوب (مثل GPT-4o أو Llama 3.3).",
                "اضغط على «Get API Key» أو استخرج Personal Access Token (PAT).",
                "استخدم الرمز في استدعاءات الوكيل البرمجي."
            )
        ),
        CodingPlatformGuide(
            id = "huggingface",
            rank = 6,
            name = "Hugging Face Hub",
            provider = DetectedProvider.HUGGINGFACE,
            badge = "أكبر مستودع مفتوح المصدر 🤗",
            badgeColor = Color(0xFFFFD21E),
            powerScore = "8.8 / 10",
            speedScore = "8.0 / 10",
            keyModels = listOf("Qwen/Qwen2.5-Coder-32B", "bigcode/starcoder2-15b"),
            description = "منصة مجتمع الذكاء الاصطناعي الأكبر، توفر واجهة Serverless Inference API مجانية لتشغيل نماذج البرمجة المفتوحة المصدر.",
            apiKeyUrl = "https://huggingface.co/settings/tokens",
            freeTierInfo = "رموز وصول شخصية (User Access Tokens) مجانية دائمة للاستدلال السريع.",
            steps = listOf(
                "سجل حساباً مجانياً على منصة Hugging Face.",
                "اذهب إلى «Settings» ثم «Access Tokens».",
                "اضغط «New token»، اختر نوع الصلاحية «Read».",
                "انسخ الرمز الذي يبدأ بـ «hf_...» والصقه في إعدادات الوكيل."
            )
        )
    )
}
