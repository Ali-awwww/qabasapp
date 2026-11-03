package com.qabas.app

/**
 * «المخطط المحلي» — يولّد خطة برمجة عربية متكاملة لطلب تطبيق
 * **بلا إنترنت وبلا أي مفتاح API** — قوالب ذكية حسب نوع المشروع.
 *
 * يُستخدم كبديل تلقائي في [AppServices.generateDeveloperPrompts]
 * عندما لا يتوفر مفتاح Gemini أو فشل الاتصال — لا رسالة خطأ بعد الآن.
 */
object LocalPromptPlanner {

    enum class ProjectKind(
        val label: String,
        val model: String,
        val temperature: String,
        val systemInstructions: String
    ) {
        GAME(
            "ألعاب وتفاعل",
            "Gemini 1.5 Pro", "0.8",
            "أنت خبير تطوير ألعاب ومحركات. ركز على الأداء الجرافيكي والهندسة ثلاثية الأبعاد."
        ),
        CHAT(
            "محادثة وذكاء اصطناعي",
            "Gemini 1.5 Flash", "0.5",
            "أنت خبير تطبيقات المحادثة والذكاء الاصطناعي. ركز على سرعة الاستجابة وربط الواجهات البرمجية."
        ),
        STORE(
            "تجارة ومتاجر",
            "Gemini 1.5 Pro", "0.3",
            "أنت خبير تطبيقات التجارة الإلكترونية. ركز على أمان البيانات وبوابات الدفع وإدارة الحالة."
        ),
        GENERAL(
            "تطبيق عام",
            "Gemini 1.5 Pro", "0.7",
            "أنت مهندس برمجيات محترف. ركز على بناء هيكل متين وتجربة مستخدم ممتازة."
        )
    }

    fun classify(text: String): ProjectKind {
        val desc = text.lowercase()
        return when {
            desc.contains("ألعاب") || desc.contains("لعبة") || desc.contains("game") || desc.contains("3d") -> ProjectKind.GAME
            desc.contains("محادثة") || desc.contains("chat") || desc.contains("ذكاء") || desc.contains("مساعد") -> ProjectKind.CHAT
            desc.contains("تجارة") || desc.contains("متجر") || desc.contains("بيع") || desc.contains("e-commerce") || desc.contains("store") -> ProjectKind.STORE
            else -> ProjectKind.GENERAL
        }
    }

    /** خطة كاملة جاهزة للنسخ — تعمل offline تماماً. */
    fun generatePlan(title: String, description: String = ""): String {
        val kind = classify("$title $description")
        return buildString {
            append("🤖 خطة مولّدة محلياً — تعمل بلا مفاتيح API\n")
            append("━━━━━━━━━━━━━━\n\n")
            append("## 1️⃣ إعدادات AI Studio المقترحة\n")
            append("- النموذج: ${kind.model}\n")
            append("- الحرارة (Temperature): ${kind.temperature}\n")
            append("- تعليمات النظام: ${kind.systemInstructions}\n\n")
            append("## 2️⃣ تحليل المشروع\n")
            append("- العنوان: $title\n")
            append("- التفاصيل: ${description.ifBlank { "مشروع محدد بواسطة الوكيل الذكي" }}\n\n")
            append("## 3️⃣ خطوات التنفيذ المتسلسلة\n")
            append("1. إنشاء هيكل الواجهات كـ Composable Components.\n")
            append("4. ربط المطبوعات والخدمات الذكية بالوكيل الموحد.\n")
        }
    }
}
