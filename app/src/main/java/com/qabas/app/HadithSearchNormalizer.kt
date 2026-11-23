package com.qabas.app

/**
 * 🔑 تطبيع نص البحث العربي — يزيل التشكيل ويوحد أشكال الألف
 * يُستخدم لفهرسة البحث فقط؛ النص الأصلي يُعرض ويُنسخ كما هو (مع التشكيل).
 *
 * المشكلة التي يحلها: `contains("النيات")` لا يجد "بِالنِّيَّاتِ" لأن التشكيل يفصل الحروف.
 * بتطبيع كلا النصين (الاستعلام + الفهرس)، يصبح البحث متسقًا.
 */
object HadithSearchNormalizer {

    // 🔑 نزع التشكيل (Arabic diacritics/tashkeel)
    private val DIACRITICS = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06DC\\u06DF-\\u06E8\\u06EA-\\u06ED]")

    // 🔑 توحيد أشكال الألف (إلى أ)
    private val ALEF_VARIANTS = Regex("[\\u0622\\u0623\\u0625\\u0671]") // آ أ إ ٱ

    // 🔑 توحيد أشكال الهمزة
    private val HAMZA_VARIANTS = Regex("[\\u0624\\u0626]") // ؤ ئ → همزة

    // 🔑 إزالة التطويل (ـ)
    private val TATWEEL = Regex("\\u0640")

    // 🔑 إزالة الأسطر الجديدة والمسافات الزائدة
    private val WHITESPACE = Regex("\\s+")

    /**
     * يطبع النص العربي للبحث:
     * 1) يزيل التشكيل
     * 2) يوحد أشكال الألف إلى أ
     * 3) يوحد أشكال الهمزة
     * 4) يزيل التطويل
     * 5) يوحّد المسافات
     */
    fun normalize(text: String): String {
        return text
            .let { DIACRITICS.replace(it, "") }      // إزالة التشكيل
            .let { ALEF_VARIANTS.replace(it, "ا") } // آأإٱ → ا
            .let { HAMZA_VARIANTS.replace(it, "ء") } // ؤئ → ء
            .let { TATWEEL.replace(it, "") }           // إزالة ـ
            .let { WHITESPACE.replace(it, " ") }       // توحيد المسافات
            .trim()
            .lowercase()
    }

    /**
     * البحث: يُطبع النص والاستعلام ثم يقارن contains.
     * يُستخدم للفهرسة فقط؛ النص الأصلي يُعرض كما هو.
     *
     * ق82: الاستعلام متعدد الكلمات كان عبارةً واحدة (contains على النص
     * كاملاً) — «الأعمال هجرته» لا يجد حديث النية رغم وجود الكلمتين
     * فيه غير متجاورتين. صار الاستعلام AND على الكلمات: كل كلمة يجب
     * أن تظهر في النص، بأي موضع. كلمة واحدة تعمل كما قبل تماماً.
     */
    fun matches(haystackArabic: String, needle: String): Boolean {
        if (needle.isBlank()) return false
        val normHay = normalize(haystackArabic)
        val tokens = normalize(needle).split(" ").filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false
        return tokens.all { normHay.contains(it) }
    }

    /**
     * إنشاء فهرس بحث مُطبّع مسبقًا (لتسريع عمليات البحث المتتالية).
     * يُطبّع مرة واحدة عند فتح الكتاب، ثم البحث يصبح contains سريع.
     */
    fun buildSearchIndex(hadiths: List<FullSahihHadith>): Map<Int, String> {
        return hadiths.associate { h -> h.id to normalize(h.arabic) }
    }

    /**
     * البحث عبر فهرس مُطبّع مسبقًا — أسرع بكثير من normalize() لكل حديث كل مرة.
     * ق82: AND على كلمات الاستعلام (انظر matches) بدل العبارة الواحدة.
     */
    fun searchWithIndex(index: Map<Int, String>, query: String, limit: Int = 50): List<Int> {
        if (query.isBlank()) return emptyList()
        val tokens = normalize(query).split(" ").filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        return index.entries.asSequence()
            .filter { (_, text) -> tokens.all { token -> text.contains(token) } }
            .take(limit)
            .map { it.key }
            .toList()
    }
}
