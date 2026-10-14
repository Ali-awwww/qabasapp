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
            .let { ALEF_VARIANTS.replace(it, "\\u0627") } // آأإٱ → ا
            .let { HAMZA_VARIANTS.replace(it, "\\u0621") } // ؤئ → ء
            .let { TATWEEL.replace(it, "") }           // إزالة ـ
            .let { WHITESPACE.replace(it, " ") }       // توحيد المسافات
            .trim()
            .lowercase()
    }

    /**
     * البحث: يُطبع النص والاستعلام ثم يقارن contains.
     * يُستخدم للفهرسة فقط؛ النص الأصلي يُعرض كما هو.
     */
    fun matches(haystackArabic: String, needle: String): Boolean {
        if (needle.isBlank()) return false
        val normHay = normalize(haystackArabic)
        val normNeedle = normalize(needle)
        return normHay.contains(normNeedle)
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
     */
    fun searchWithIndex(index: Map<Int, String>, query: String, limit: Int = 50): List<Int> {
        if (query.isBlank()) return emptyList()
        val normQuery = normalize(query)
        if (normQuery.isBlank()) return emptyList()
        return index.entries.asSequence()
            .filter { it.value.contains(normQuery) }
            .take(limit)
            .map { it.key }
            .toList()
    }
}
