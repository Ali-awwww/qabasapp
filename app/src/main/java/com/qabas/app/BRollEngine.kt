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
    val ffmpegFilterName: String,
    val iconName: String,
    val defaultDurationSeconds: Double = 0.8
)

object BRollEngine {

    val availableTransitions = listOf(
        TransitionEffect("fade", "تلاشي ناعم (Fade)", "Smooth Fade", "fade", "Opacity", 0.8),
        TransitionEffect("dissolve", "ذوبان سينمائي (Dissolve)", "Dissolve", "dissolve", "AutoAwesome", 1.0),
        TransitionEffect("slideleft", "انزلاق لليسار (Slide Left)", "Slide Left", "slideleft", "ArrowForward", 0.6),
        TransitionEffect("slideright", "انزلاق لليمين (Slide Right)", "Slide Right", "slideright", "ArrowBack", 0.6),
        TransitionEffect("wipeleft", "مسح متدرج (Wipe Left)", "Wipe Left", "wipeleft", "Swipe", 0.7),
        TransitionEffect("wiperight", "مسح يمين (Wipe Right)", "Wipe Right", "wiperight", "Swipe", 0.7),
        TransitionEffect("zoomin", "تكبير سينمائي (Zoom In)", "Zoom In", "zoomin", "ZoomIn", 0.8),
        TransitionEffect("circleopen", "تأطير دائري (Circle Open)", "Circle Open", "circleopen", "Circle", 0.9),
        TransitionEffect("fadeblack", "تلاشي بالأسود (Fade to Black)", "Fade to Black", "fadeblack", "Contrast", 1.0)
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
     * Translates common Arabic visual concepts to English search keywords for Pexels & Pixabay APIs.
     */
    fun mapArabicToVisualKeywords(arabicText: String): String {
        val lower = arabicText.lowercase(Locale.ROOT)
        val matches = mutableListOf<String>()

        if (lower.contains("مسجد") || lower.contains("مساجد") || lower.contains("جامع") || lower.contains("مئذنة") || lower.contains("قبة")) {
            matches.add("mosque minaret islamic architecture")
        }
        if (lower.contains("مكة") || lower.contains("كعبة") || lower.contains("طواف") || lower.contains("حرم") || lower.contains("حج") || lower.contains("عمرة")) {
            matches.add("mecca kaaba pilgrims holy mosque")
        }
        if (lower.contains("مدينة") || lower.contains("نبوي") || lower.contains("الرسول") || lower.contains("النبي")) {
            matches.add("medina prophet mosque dome")
        }
        if (lower.contains("قرآن") || lower.contains("مصحف") || lower.contains("آية") || lower.contains("تلاوة") || lower.contains("تدبر")) {
            matches.add("holy quran open book arabic calligraphy")
        }
        if (lower.contains("سماء") || lower.contains("نجوم") || lower.contains("فضاء") || lower.contains("كون") || lower.contains("قمر") || lower.contains("ليل")) {
            matches.add("night sky stars milky way galaxy universe")
        }
        if (lower.contains("شمس") || lower.contains("شروق") || lower.contains("فجر") || lower.contains("صباح") || lower.contains("نور")) {
            matches.add("sunrise golden hour sun rays landscape")
        }
        if (lower.contains("صحراء") || lower.contains("رمال") || lower.contains("كثبان") || lower.contains("واحة")) {
            matches.add("desert sand dunes cinematic landscape")
        }
        if (lower.contains("مطر") || lower.contains("ماء") || lower.contains("غيث") || lower.contains("سحاب") || lower.contains("غيوم") || lower.contains("رعد")) {
            matches.add("rain clouds storm droplets nature")
        }
        if (lower.contains("دعاء") || lower.contains("تضرع") || lower.contains("خشوع") || lower.contains("صلاة") || lower.contains("سجود") || lower.contains("استغفار")) {
            matches.add("man praying mosque dramatic light spiritual")
        }
        if (lower.contains("جبال") || lower.contains("جبل") || lower.contains("طبيعة") || lower.contains("أشجار") || lower.contains("غابة") || lower.contains("أنهار")) {
            matches.add("mountain peak green valley nature cinematic")
        }
        if (lower.contains("بحر") || lower.contains("أمواج") || lower.contains("محيط") || lower.contains("غروب")) {
            matches.add("ocean sea waves sunset golden light")
        }
        if (lower.contains("علم") || lower.contains("كتاب") || lower.contains("قراءة") || lower.contains("مخطوطة") || lower.contains("حكمة")) {
            matches.add("ancient manuscript old book library candle")
        }
        if (lower.contains("تاريخ") || lower.contains("أندلس") || lower.contains("حضارة") || lower.contains("قلاع") || lower.contains("آثار")) {
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
     */
    fun matchBRoll(sceneDescription: String): BRollItem {
        val lower = sceneDescription.lowercase(Locale.ROOT)
        var bestItem: BRollItem? = null
        var maxScore = 0

        library.forEach { item ->
            var score = 0
            item.keywords.forEach { kw ->
                if (lower.contains(kw.lowercase(Locale.ROOT))) {
                    score += 3
                }
            }
            if (score > maxScore) {
                maxScore = score
                bestItem = item
            }
        }

        return bestItem ?: library.random()
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
