package com.qabas.app

import java.util.Locale

data class BRollItem(
    val id: String,
    val title: String,
    val category: String,
    val keywords: List<String>,
    val thumbnailUrl: String,
    val videoUrl: String,
    val suggestedTransition: String = "Fade",
    val description: String = ""
)

data class TransitionEffect(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val iconName: String,
)

object BRollEngine {

    /**
     * تطبيع كلمة عربية للمطابقة: بلا تشكيل، وأشكال الألف موحدة،
     * والتاء المربوطة هاء والألف المقصورة ياء.
     */
    private fun normalizeArabicWord(word: String): String =
        word.replace(Regex("\\p{M}+"), "")
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
            .replace('ة', 'ه').replace('ى', 'ي')

    /**
     * أشكال الكلمة بعد تجريد «ال» التعريف (مع سابقة و/ب/ف/ل/ك
     * اختيارية: والمسجد، بالصلاة، للقرآن) وحرف جر/عطف واحد
     * (بمسجد). ق100: بلا هذه الأشكال كانت المطابقة بحدود الكلمة
     * تفقد «المسجد» و«بالدعاء» فلا يصح الاقتصار على المساواة الخام.
     */
    private fun arabicWordForms(token: String): Set<String> {
        val forms = mutableSetOf(token)
        val withoutArticle = token.replaceFirst(Regex("^(و|ب|ف|ل|ك)?ال"), "")
        if (withoutArticle.isNotEmpty() && withoutArticle != token) forms += withoutArticle
        if (token.length > 2 && token[0] in "وبفلك") forms += token.substring(1)
        return forms
    }

    /** مجموعة كلمات النص المطبعة بأشكالها — للمطابقة بحدود الكلمة. */
    private fun arabicWords(text: String): Set<String> =
        normalizeArabicWord(text).split(Regex("[^\\p{L}]+"))
            .filter { it.isNotEmpty() }
            .flatMap { arabicWordForms(it) }
            .toSet()

    /** هل تطابق الكلمة المفتاحية أي كلمة في النص بحدودها؟ */
    private fun matchesKeyword(words: Set<String>, keyword: String): Boolean {
        val normalized = normalizeArabicWord(keyword.lowercase(Locale.ROOT))
        return arabicWordForms(normalized).any { it in words }
    }

    /** هل يحوي النص أياً من الكلمات المرشحة بحدودها؟ */
    private fun hasArabicWord(words: Set<String>, vararg candidates: String): Boolean =
        candidates.any { matchesKeyword(words, it) }

    val availableTransitions = listOf(
        TransitionEffect("fade", "تلاشي ناعم (Fade)", "Smooth Fade", "Opacity"),
        TransitionEffect("dissolve", "ذوبان سينمائي (Dissolve)", "Dissolve", "AutoAwesome"),
        TransitionEffect("slideleft", "انزلاق لليسار (Slide Left)", "Slide Left", "ArrowForward"),
        TransitionEffect("slideright", "انزلاق لليمين (Slide Right)", "Slide Right", "ArrowBack"),
        TransitionEffect("wipeleft", "مسح متدرج (Wipe Left)", "Wipe Left", "Swipe"),
        TransitionEffect("wiperight", "مسح يمين (Wipe Right)", "Wipe Right", "Swipe"),
        TransitionEffect("zoomin", "تكبير سينمائي (Zoom In)", "Zoom In", "ZoomIn"),
        TransitionEffect("circleopen", "تأطير دائري (Circle Open)", "Circle Open", "Circle"),
        TransitionEffect("fadeblack", "تلاشي بالأسود (Fade to Black)", "Fade to Black", "Contrast")
    )

    val categories = listOf(
        "الجميع",
        "مساجد وعمارة إسلامية",
        "قرآن ومخطوطات",
        "طبيعة وكون وتفكر",
        "تاريخ وحضارة",
        "روحانيات وتضرع",
        "علوم ومعرفة"
    )

    val library = listOf(
        BRollItem(
            id = "broll_1",
            title = "المسجد الحرام والأنوار الذهبية",
            category = "مساجد وعمارة إسلامية",
            keywords = listOf("مسجد", "مكة", "الكعبة", "الحرم", "طواف", "صلاة", "قباب", "مآذن", "إسلام"),
            thumbnailUrl = "https://images.unsplash.com/photo-1564769625905-50e93615e769?w=600&q=80",
            videoUrl = "https://assets.mixkit.co/videos/preview/mixkit-mosque-illuminated-at-night-42284-large.mp4",
            suggestedTransition = "Dissolve",
            description = "مشهد سينمائي فاخر للمسجد الحرام تحت الأنوار الليليّة."
        ),
        BRollItem(
            id = "broll_2",
            title = "المسجد النبوي الشريف",
            category = "مساجد وعمارة إسلامية",
            keywords = listOf("المدينة", "النبوي", "الرسول", "النبي", "قبة", "مسجد", "سلام"),
            thumbnailUrl = "https://images.unsplash.com/photo-1591604466107-ec97de577aff?w=600&q=80",
            videoUrl = "https://assets.mixkit.co/videos/preview/mixkit-view-of-a-mosque-dome-and-minaret-42285-large.mp4",
            suggestedTransition = "Fade",
            description = "إخراج مهيب للقبة الخضراء والمآذن الشامخة."
        ),
        BRollItem(
            id = "broll_3",
            title = "مصحف شريف ومخطوطة مذهبة",
            category = "قرآن ومخطوطات",
            keywords = listOf("قرآن", "آية", "مصحف", "تلاوة", "تفسير", "تدبر", "كتاب", "آيات", "وحي"),
            thumbnailUrl = "https://images.unsplash.com/photo-1609599006353-e629aaabfeae?w=600&q=80",
            videoUrl = "https://assets.mixkit.co/videos/preview/mixkit-hands-holding-an-open-quran-42286-large.mp4",
            suggestedTransition = "Dissolve",
            description = "تقريب سينمائي على صفحات المصحف بالخط العثماني والذهب."
        ),
        BRollItem(
            id = "broll_4",
            title = "نجوم السماء والليل الساجد",
            category = "طبيعة وكون وتفكر",
            keywords = listOf("سماء", "كون", "نجوم", "ليل", "خلق", "تفكر", "عظمة", "قمر", "أرض", "قدرة"),
            thumbnailUrl = "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=600&q=80",
            videoUrl = "https://assets.mixkit.co/videos/preview/mixkit-starry-sky-time-lapse-4008-large.mp4",
            suggestedTransition = "ZoomIn",
            description = "حركة نجوم السماء بأسلوب Time-Lapse يعكس عظمة الخالق."
        ),
        BRollItem(
            id = "broll_5",
            title = "شروق الشمس فوق الكثبان الذهبية",
            category = "طبيعة وكون وتفكر",
            keywords = listOf("صحراء", "شمس", "أمل", "نور", "فجر", "شروق", "رمال", "دعوة", "طريق"),
            thumbnailUrl = "https://images.unsplash.com/photo-1509316975850-ff9c5deb0cd9?w=600&q=80",
            videoUrl = "https://assets.mixkit.co/videos/preview/mixkit-desert-sand-dunes-under-a-clear-blue-sky-42526-large.mp4",
            suggestedTransition = "WipeLeft",
            description = "نور الفجر يشرق فوق صحراء التراث الإسلامي."
        ),
        BRollItem(
            id = "broll_6",
            title = "زخارف ومقرنصات معمارية أندلسية",
            category = "تاريخ وحضارة",
            keywords = listOf("تاريخ", "حضارة", "أندلس", "عمارة", "فن", "زخرفة", "قديم", "تراث", "معالم"),
            thumbnailUrl = "https://images.unsplash.com/photo-1548013146-72479768bada?w=600&q=80",
            videoUrl = "https://assets.mixkit.co/videos/preview/mixkit-intricate-details-of-a-mosque-interior-42287-large.mp4",
            suggestedTransition = "Dissolve",
            description = "لقطة وثائقية بطيئة تشرح إبداع الهندسة الإسلامية."
        ),
        BRollItem(
            id = "broll_7",
            title = "رفع اليدين بالدعاء والتضرع",
            category = "روحانيات وتضرع",
            keywords = listOf("دعاء", "تضرع", "استغفار", "خشوع", "عبادة", "إيمان", "رجاء", "قلب", "توبة"),
            thumbnailUrl = "https://images.unsplash.com/photo-1574267432553-4b4628081c31?w=600&q=80",
            videoUrl = "https://assets.mixkit.co/videos/preview/mixkit-man-praying-in-a-mosque-42288-large.mp4",
            suggestedTransition = "FadeToBlack",
            description = "لقطة مؤثرة لشخص يتضرع في المحراب."
        ),
        BRollItem(
            id = "broll_8",
            title = "مخطوطات علمية وفلكية إسلامية",
            category = "علوم ومعرفة",
            keywords = listOf("علم", "علماء", "مخطوطة", "فلك", "طب", "حكمة", "معرفة", "كتب", "بحث"),
            thumbnailUrl = "https://images.unsplash.com/photo-1456513080510-7bf3a84b82f8?w=600&q=80",
            videoUrl = "https://assets.mixkit.co/videos/preview/mixkit-turning-pages-of-an-old-book-41558-large.mp4",
            suggestedTransition = "SlideLeft",
            description = "تصفح مخطوطات أئمة العلم والحضارة."
        ),
        BRollItem(
            id = "broll_9",
            title = "أمطار الرحمة وقطرات الندى على الشجر",
            category = "طبيعة وكون وتفكر",
            keywords = listOf("مطر", "ماء", "غيث", "رحمة", "سحاب", "سماء", "ندى", "طبيعة", "حياة", "رزق"),
            thumbnailUrl = "https://images.unsplash.com/photo-1534274988757-a28bf1a57c17?w=600&q=80",
            videoUrl = "https://assets.mixkit.co/videos/preview/mixkit-rain-drops-falling-on-leaves-42795-large.mp4",
            suggestedTransition = "Dissolve",
            description = "لقطة سينمائية لقطرات المطر الصافية على أوراق الشجر الخضراء."
        ),
        BRollItem(
            id = "broll_10",
            title = "قلم الخط العربي والمحبرة الذهبية",
            category = "قرآن ومخطوطات",
            keywords = listOf("خط", "قلم", "كتابة", "عربي", "تخطيط", "رسم", "حبر", "حروف", "تراث"),
            thumbnailUrl = "https://images.unsplash.com/photo-1583484963886-ede2be473011?w=600&q=80",
            videoUrl = "https://assets.mixkit.co/videos/preview/mixkit-artist-drawing-with-calligraphy-pen-43615-large.mp4",
            suggestedTransition = "SlideLeft",
            description = "حركة بطيئة لقلم الخط العربي يسطر الكلمات النورانية."
        ),
        BRollItem(
            id = "broll_11",
            title = "قمم الجبال الشامخة والسحب المتدفقة",
            category = "طبيعة وكون وتفكر",
            keywords = listOf("جبال", "جبل", "قمة", "سحاب", "أرض", "عظمة", "خلق", "صمود", "ثبات"),
            thumbnailUrl = "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=600&q=80",
            videoUrl = "https://assets.mixkit.co/videos/preview/mixkit-clouds-and-fog-over-the-mountain-peaks-42616-large.mp4",
            suggestedTransition = "ZoomIn",
            description = "مشهد بانورامي مهيب لقمم الجبال والسحب البيضاء."
        ),
        BRollItem(
            id = "broll_12",
            title = "شعلة النور وسراج البصيرة في العتمة",
            category = "روحانيات وتضرع",
            keywords = listOf("نور", "سراج", "شمعة", "ظلام", "هدى", "بصيرة", "إيمان", "حق", "يقين"),
            thumbnailUrl = "https://images.unsplash.com/photo-1517411032315-54ef2cb783bb?w=600&q=80",
            videoUrl = "https://assets.mixkit.co/videos/preview/mixkit-burning-candle-flame-in-the-dark-42880-large.mp4",
            suggestedTransition = "FadeToBlack",
            description = "شمعة مضيئة في الظلام ترمز لتبديد الجهل بنور الهداية."
        ),
        BRollItem(
            id = "broll_13",
            title = "أمواج البحر الهادئة عند الغروب",
            category = "طبيعة وكون وتفكر",
            keywords = listOf("بحر", "موج", "غروب", "سكينة", "شاطئ", "هدوء", "سماء", "أفق", "سلام"),
            thumbnailUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600&q=80",
            videoUrl = "https://assets.mixkit.co/videos/preview/mixkit-sea-waves-crashing-on-the-shore-at-sunset-42866-large.mp4",
            suggestedTransition = "Fade",
            description = "أمواج هادئة تعكس ألوان الشفق الذهبي والسكينة الروحية."
        ),
        BRollItem(
            id = "broll_14",
            title = "تفتح الأزهار في فجر ربيعي",
            category = "طبيعة وكون وتفكر",
            keywords = listOf("زهر", "نبات", "ربيع", "تفتح", "حياة", "خلق", "جمال", "فجر", "أمل", "تجدد"),
            thumbnailUrl = "https://images.unsplash.com/photo-1490750967868-88aa4486c946?w=600&q=80",
            videoUrl = "https://assets.mixkit.co/videos/preview/mixkit-red-flowers-in-the-field-at-sunrise-42817-large.mp4",
            suggestedTransition = "Dissolve",
            description = "تفتح الأزهار مع أول خيوط الفجر يعكس بديع صنع الله."
        ),
        BRollItem(
            id = "broll_15",
            title = "طريق السفر الطويل والرحلة الإيمانية",
            category = "روحانيات وتضرع",
            keywords = listOf("طريق", "سفر", "رحلة", "هجرة", "سعي", "أمل", "مستقبل", "حياة", "يقين"),
            thumbnailUrl = "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=600&q=80",
            videoUrl = "https://assets.mixkit.co/videos/preview/mixkit-car-driving-on-a-road-at-sunset-42867-large.mp4",
            suggestedTransition = "WipeRight",
            description = "طريق ممتد يعبر عن رحلة الإنسان نحو ربه."
        ),
        BRollItem(
            id = "broll_16",
            title = "تلاوة القرآن في خلوة السحر",
            category = "قرآن ومخطوطات",
            keywords = listOf("خلوة", "سحر", "ليل", "قرآن", "تلاوة", "عبادة", "سكينة", "إيمان"),
            thumbnailUrl = "https://images.unsplash.com/photo-1594474038222-12967f682490?w=600&q=80",
            videoUrl = "https://assets.mixkit.co/videos/preview/mixkit-person-reading-a-book-in-the-dim-light-42881-large.mp4",
            suggestedTransition = "FadeToBlack",
            description = "مشهد تأملي للقراءة في هدوء الليل."
        )
    )

    /**
     * Translates common Arabic visual concepts to English search keywords for the Pixabay API.
     * ق100: مطابقة بحدود الكلمة كـ matchBRoll — كانت contains() الخام
     * تجعل «ماء» داخل «سماء» تلوّث استعلام Pixabay بكلمات مطر
     * لمشهد سماء صافية.
     */
    fun mapArabicToVisualKeywords(arabicText: String): String {
        val words = arabicWords(arabicText.lowercase(Locale.ROOT))
        val matches = mutableListOf<String>()

        if (hasArabicWord(words, "مسجد", "مساجد", "جامع", "مئذنة", "قبة")) {
            matches.add("mosque minaret islamic architecture")
        }
        if (hasArabicWord(words, "مكة", "كعبة", "طواف", "حرم", "حج", "عمرة")) {
            matches.add("mecca kaaba pilgrims holy mosque")
        }
        if (hasArabicWord(words, "مدينة", "نبوي", "الرسول", "النبي")) {
            matches.add("medina prophet mosque dome")
        }
        if (hasArabicWord(words, "قرآن", "مصحف", "آية", "تلاوة", "تدبر")) {
            matches.add("holy quran open book arabic calligraphy")
        }
        if (hasArabicWord(words, "سماء", "نجوم", "فضاء", "كون", "قمر", "ليل")) {
            matches.add("night sky stars milky way galaxy universe")
        }
        if (hasArabicWord(words, "شمس", "شروق", "فجر", "صباح", "نور")) {
            matches.add("sunrise golden hour sun rays landscape")
        }
        if (hasArabicWord(words, "صحراء", "رمال", "كثبان", "واحة")) {
            matches.add("desert sand dunes cinematic landscape")
        }
        if (hasArabicWord(words, "مطر", "ماء", "غيث", "سحاب", "غيوم", "رعد")) {
            matches.add("rain clouds storm droplets nature")
        }
        if (hasArabicWord(words, "دعاء", "تضرع", "خشوع", "صلاة", "سجود", "استغفار")) {
            matches.add("man praying mosque dramatic light spiritual")
        }
        if (hasArabicWord(words, "جبال", "جبل", "طبيعة", "أشجار", "غابة", "أنهار")) {
            matches.add("mountain peak green valley nature cinematic")
        }
        if (hasArabicWord(words, "بحر", "أمواج", "محيط", "غروب")) {
            matches.add("ocean sea waves sunset golden light")
        }
        if (hasArabicWord(words, "علم", "كتاب", "قراءة", "مخطوطة", "حكمة")) {
            matches.add("ancient manuscript old book library candle")
        }
        if (hasArabicWord(words, "تاريخ", "أندلس", "حضارة", "قلاع", "آثار")) {
            matches.add("historical architecture arabesque intricate pattern")
        }

        return if (matches.isNotEmpty()) {
            matches.joinToString(" ")
        } else {
            "nature landscape cinematic dramatic light"
        }
    }

    /**
     * Smart AI Matcher: Matches text description with the most relevant B-Roll item.
     * ق100: مطابقة بحدود الكلمة لا contains() الخام — كانت «ماء»
     * داخل «سماء» تمنح لقطة المطر 3 نقاط إضافية فتفوز (6 مقابل 3)
     * بمشهد سماء صافية، و«ليل» داخل «دليل»، و«خط» داخل «خطوة».
     */
    fun matchBRoll(sceneDescription: String): BRollItem {
        val words = arabicWords(sceneDescription.lowercase(Locale.ROOT))
        var bestItem: BRollItem? = null
        var maxScore = 0

        library.forEach { item ->
            var score = 0
            item.keywords.forEach { kw ->
                if (matchesKeyword(words, kw)) {
                    score += 3
                }
            }
            if (score > maxScore) {
                maxScore = score
                bestItem = item
            }
        }

        // بلا مطابقة: الأول (الأعم — المسجد الحرام) لا عشوائي (ق25):
        // library.random() كان يعطي لقطة غير ذات صلة (مطر/صحراء/شمعة)
        // لمشهد لا يمتّ لها بصلة، وبنتيجة مختلفة في كل مرة.
        return bestItem ?: library.first()
    }

    fun getBRollForCategory(category: String): List<BRollItem> {
        if (category == "الجميع" || category.isBlank()) return library
        return library.filter { it.category == category }
    }

    fun searchBRoll(query: String): List<BRollItem> {
        if (query.isBlank()) return library
        val q = query.lowercase(Locale.ROOT)
        return library.filter { item ->
            item.title.lowercase(Locale.ROOT).contains(q) ||
            item.category.lowercase(Locale.ROOT).contains(q) ||
            item.keywords.any { it.lowercase(Locale.ROOT).contains(q) }
        }
    }
}
