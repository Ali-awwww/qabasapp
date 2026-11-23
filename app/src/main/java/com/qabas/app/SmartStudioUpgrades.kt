package com.qabas.app


/** أنماط الكابشن الفيروسية 2026: تُلحق بالوصف الأسلوبي تلقائياً. */
object CaptionStyles {
    const val VIRAL_DIRECTIVE =
        " Word-by-word kinetic captions, 3-5 words per screen, gold bold Cairo font with black stroke, " +
        "punch-in animation on keywords, emoji-free, always on screen."
}

/** إزالة كلمات الحشو من النص — على طريقة Descript. */
object SmartCut {
    // 🔑 نُدرج كلمات الحشو فقط في بداية/نهاية الجملة (لا نُفسد معنى الكلام في وسط الجملة)
    private val LeadingFillers = listOf(
        "يعني", "أمم", "امم", "آه", "اه", "همم", "زي ما قلت", "بصراحة يعني", "تمام؟"
    )

    fun clean(text: String): String {
        var t = " ${text.replace(Regex("\\s+"), " ").trim()} "

        // 🔑 إزالة الحشو في بداية الجملة فقط
        var changed = true
        while (changed) {
            changed = false
            for (f in LeadingFillers) {
                val leading = " $f "
                if (t.startsWith(leading) || t.contains(". $f ") || t.contains("؟ $f ")) {
                    t = t.replace(leading, " ")
                        .replace(". $f ", ". ")
                        .replace("؟ $f ", "؟ ")
                    changed = true
                }
            }
            t = t.replace(Regex("\\s+"), " ")
        }

        return t.trim()
    }
}
