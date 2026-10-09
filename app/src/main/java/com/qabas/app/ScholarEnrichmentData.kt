package com.qabas.app

/**
 * بيانات الإثراء التاريخي والجغرافي لسير الأكابر:
 * العصور التاريخية، الأحداث الكبرى، محطات الرحلات، والمسافات التقديرية.
 */
object ScholarEnrichmentData {

    const val ERA_ALL = "الكل"
    const val ERA_PROPHETIC = "العصر النبوي والمكي"
    const val ERA_RASHIDUN = "عصر الخلفاء الراشدين"
    const val ERA_UMAYYAD = "العصر الأموي والتابعون"
    const val ERA_ABBASID = "العصر العباسي والتدوين"
    const val ERA_MAMLUK = "عصر المماليك والحروب الصليبية"

    val ERAS_LIST = listOf(
        ERA_ALL,
        ERA_PROPHETIC,
        ERA_RASHIDUN,
        ERA_UMAYYAD,
        ERA_ABBASID,
        ERA_MAMLUK
    )

    const val TRIAL_ALL = "جميع المحن"
    const val TRIAL_PRISON = "⛓️ السجن والتعذيب"
    const val TRIAL_MARTYRDOM = "⚔️ الشهادة والجهاد"
    const val TRIAL_POVERTY = "💧 الفقر والجوع والزهد"
    const val TRIAL_RULERS = "🛡️ الثبات ومواجهة الطغيان"
    const val TRIAL_EXILE = "🌾 الغربة والتهجير"

    val TRIAL_TYPES = listOf(
        TRIAL_ALL,
        TRIAL_PRISON,
        TRIAL_MARTYRDOM,
        TRIAL_POVERTY,
        TRIAL_RULERS,
        TRIAL_EXILE
    )

    fun enrich(scholar: ScholarBiography): ScholarBiography {
        val (era, event, stops, distance) = getMetadataFor(scholar.id, scholar.name)
        val facets = if (scholar.customFacets.isNotEmpty()) scholar.customFacets else ScholarCustomFacetsData.getFacetsFor(scholar.id, scholar.name)
        return scholar.copy(
            era = scholar.era ?: era,
            majorHistoricalEvent = scholar.majorHistoricalEvent ?: event,
            journeyStops = if (scholar.journeyStops.isNotEmpty()) scholar.journeyStops else stops,
            estimatedTravelKm = scholar.estimatedTravelKm ?: distance,
            customFacets = facets
        )
    }

    private fun getMetadataFor(id: String, name: String): Quadruple<String, String, List<String>, Int> {
        return when {
            // كبار الصحابة - عصر النبوة والراشدين
            id == "sahabi_1" || name.contains("أبو بكر") -> Quadruple(
                ERA_RASHIDUN,
                "الهجرة النبوية وحروب الردة",
                listOf("مكة المكرمة", "غار ثور", "المدينة المنورة", "تبوك"),
                1450
            )
            id == "sahabi_2" || name.contains("عمر بن الخطاب") -> Quadruple(
                ERA_RASHIDUN,
                "فتح بيت المقدس وتأسيس الدولة",
                listOf("مكة المكرمة", "المدينة المنورة", "الجابية (الشام)", "القدس الشريف"),
                2800
            )
            id == "sahabi_3" || name.contains("عثمان بن عفان") -> Quadruple(
                ERA_RASHIDUN,
                "جمع المصحف الشريف وفتوح البحار",
                listOf("مكة المكرمة", "أرض الحبشة", "المدينة المنورة"),
                3200
            )
            id == "sahabi_4" || name.contains("علي بن أبي طالب") -> Quadruple(
                ERA_RASHIDUN,
                "فداء الهجرة، فتح خيبر، وخلافة الكوفة",
                listOf("مكة المكرمة", "المدينة المنورة", "اليمن", "الكوفة"),
                3600
            )
            id == "sahabi_5" || name.contains("أبو عبيدة") -> Quadruple(
                ERA_RASHIDUN,
                "معركة اليرموك وطاعون عمواس",
                listOf("مكة المكرمة", "الحبشة", "المدينة المنورة", "دمشق", "حمص", "عمواس"),
                4100
            )
            id == "sahabi_6" || name.contains("سعد بن أبي وقاص") -> Quadruple(
                ERA_RASHIDUN,
                "معركة القادسية وفتح المدائن وكبرى فتوح فارس",
                listOf("مكة المكرمة", "المدينة المنورة", "القادسية", "المدائن", "الكوفة"),
                3900
            )
            id == "sahabi_7" || name.contains("الزبير بن العوام") -> Quadruple(
                ERA_RASHIDUN,
                "أول سيف في الإسلام ومعركة اليرموك وحصن بابليون",
                listOf("مكة المكرمة", "الحبشة", "المدينة المنورة", "مصر (الفسطاط)", "اليرموك"),
                4800
            )
            id == "sahabi_8" || name.contains("طلحة بن عبيد الله") -> Quadruple(
                ERA_RASHIDUN,
                "صقر يوم أحد ووقاية النبي ﷺ بالنفس",
                listOf("مكة المكرمة", "بصرى الشام", "المدينة المنورة", "أحد"),
                2200
            )
            id == "sahabi_9" || name.contains("عبد الرحمن بن عوف") -> Quadruple(
                ERA_RASHIDUN,
                "هجرة الحبشة وتجهيز جيش العسرة",
                listOf("مكة المكرمة", "الحبشة", "المدينة المنورة", "تبوك"),
                3400
            )
            id == "sahabi_10" || name.contains("سعيد بن زيد") -> Quadruple(
                ERA_RASHIDUN,
                "حصار وفتح دمشق ومعركة أجنادين",
                listOf("مكة المكرمة", "المدينة المنورة", "أجنادين", "دمشق"),
                2600
            )
            id == "sahabi_11" || name.contains("حمزة بن عبد المطلب") -> Quadruple(
                ERA_PROPHETIC,
                "معركة بدر الكبرى ويوم أحد",
                listOf("مكة المكرمة", "المدينة المنورة", "بدر", "جبل أحد"),
                980
            )
            id == "sahabi_12" || name.contains("جعفر بن أبي طالب") -> Quadruple(
                ERA_PROPHETIC,
                "هجرة الحبشة ومعركة مؤتة الخالدة",
                listOf("مكة المكرمة", "مملكة أكسوم (الحبشة)", "المدينة المنورة", "مؤتة (الأردن)"),
                5200
            )
            id == "sahabi_13" || name.contains("خالد بن الوليد") -> Quadruple(
                ERA_RASHIDUN,
                "معارك الردة، اليرموك، وفتح دمشق وحمص",
                listOf("مكة المكرمة", "مؤتة", "اليمامة", "الحيرة (العراق)", "بادية السماوة", "اليرموك", "حمص"),
                8500
            )
            id == "sahabi_14" || name.contains("بلال بن رباح") -> Quadruple(
                ERA_RASHIDUN,
                "رمضاء مكة وأذان فتح بيت المقدس",
                listOf("مكة المكرمة", "المدينة المنورة", "القدس", "دمشق (داريا)"),
                2900
            )
            id == "sahabi_15" || name.contains("مصعب بن عمير") -> Quadruple(
                ERA_PROPHETIC,
                "سفارة يثرب الأولى وحمل اللواء في أحد",
                listOf("مكة المكرمة", "أرض الحبشة", "يثرب (المدينة المنورة)", "أحد"),
                2700
            )
            id == "sahabi_16" || name.contains("عبد الله بن مسعود") -> Quadruple(
                ERA_RASHIDUN,
                "الجهر بالقرآن عند الكعبة ومدرسة فقه الكوفة",
                listOf("مكة المكرمة", "الحبشة", "المدينة المنورة", "الكوفة"),
                3300
            )
            id == "sahabi_17" || name.contains("سلمان الفارسي") -> Quadruple(
                ERA_RASHIDUN,
                "حفر الخندق وإمارة المدائن الزاهدة",
                listOf("إصفهان (فارس)", "الشام", "الموصل", "عمورية", "يثرب", "المدائن"),
                6800
            )
            id == "sahabi_18" || name.contains("خبيب بن عدي") -> Quadruple(
                ERA_PROPHETIC,
                "سرية الرجيع وتأسيس ركعتي الشهادة",
                listOf("المدينة المنورة", "ماء الرجيع", "التنعيم (مكة المكرمة)"),
                1100
            )
            id == "sahabi_19" || name.contains("عبد الله بن الزبير") -> Quadruple(
                ERA_UMAYYAD,
                "الدفاع عن كعبة مكة وحصار الحرم",
                listOf("المدينة المنورة", "مصر", "إفريقية", "مكة المكرمة"),
                4600
            )
            id == "sahabi_20" || name.contains("الحسن بن علي") -> Quadruple(
                ERA_UMAYYAD,
                "عام الجماعة وحقن دماء المسلمين",
                listOf("المدينة المنورة", "الكوفة", "المدائن", "المدينة المنورة"),
                3100
            )
            id == "sahabi_21" || name.contains("الحسين بن علي") -> Quadruple(
                ERA_UMAYYAD,
                "ملحمة كربلاء ورفض الخنوع للاستبداد",
                listOf("المدينة المنورة", "مكة المكرمة", "كربلاء (العراق)"),
                2600
            )
            id == "sahabi_22" || name.contains("معاذ بن جبل") -> Quadruple(
                ERA_RASHIDUN,
                "قضاء اليمن وفقهاء الشام وطاعون عمواس",
                listOf("المدينة المنورة", "صنعاء (اليمن)", "فلسطين", "غور الأردن"),
                4500
            )
            id == "sahabi_23" || name.contains("أبو هريرة") -> Quadruple(
                ERA_RASHIDUN,
                "ملازمة أهل الصفة وحفظ السنة النبوية",
                listOf("دوس (اليمن)", "المدينة المنورة", "البحرين"),
                2900
            )
            id == "sahabi_24" || name.contains("عبد الله بن عباس") -> Quadruple(
                ERA_UMAYYAD,
                "مناظرة الخوارج وتأسيس مدرسة التفسير",
                listOf("مكة المكرمة", "الشعب", "المدينة المنورة", "البصرة", "الطائف"),
                2500
            )
            id == "sahabi_25" || name.contains("عبد الله بن عمر") -> Quadruple(
                ERA_UMAYYAD,
                "بيعة الرضوان واعتزال الفتن الدامية",
                listOf("مكة المكرمة", "المدينة المنورة", "مصر", "الشام", "مكة المكرمة"),
                3800
            )
            id == "sahabi_26" || name.contains("عمار بن ياسر") -> Quadruple(
                ERA_RASHIDUN,
                "بناء أول مسجد في قباء وموقعة صفين",
                listOf("مكة المكرمة", "الحبشة", "المدينة المنورة", "الكوفة", "صفين"),
                3700
            )
            id == "sahabi_27" || name.contains("أبو ذر الغفاري") -> Quadruple(
                ERA_RASHIDUN,
                "الجهر بالحق ومحاربة كنز الأموال وصحراء الربذة",
                listOf("غفار", "مكة المكرمة", "المدينة المنورة", "الشام", "الربذة"),
                3100
            )
            id == "sahabi_28" || name.contains("أبي بن كعب") -> Quadruple(
                ERA_RASHIDUN,
                "كتابة الوحي وتلاوة سورة البينة بأمر الله",
                listOf("يثرب (المدينة المنورة)", "بدر", "أحد", "المدينة المنورة"),
                950
            )
            id == "sahabi_29" || name.contains("سعد بن معاذ") -> Quadruple(
                ERA_PROPHETIC,
                "حكم بني قريظة واهتزاز عرش الرحمن",
                listOf("يثرب (المدينة المنورة)", "بدر", "أحد", "الخندق"),
                820
            )
            id == "sahabi_30" || name.contains("أسامة بن زيد") -> Quadruple(
                ERA_RASHIDUN,
                "إنفاذ جيش أسامة لقتال الروم",
                listOf("مكة المكرمة", "المدينة المنورة", "البلقاء (مؤتة)", "وادي القرى"),
                2100
            )
            id == "sahabi_31" || name.contains("البراء بن مالك") -> Quadruple(
                ERA_RASHIDUN,
                "اقتحام حديقة الموت باليمامة وفتح تستر",
                listOf("المدينة المنورة", "اليمامة", "الأهواز", "تستر (فارس)"),
                3800
            )
            id == "sahabi_32" || name.contains("القعقاع بن عمرو") -> Quadruple(
                ERA_RASHIDUN,
                "معركة اليرموك والقادسية وليلة الهرير",
                listOf("نجد", "الحيرة", "اليرموك", "القادسية", "جلولاء", "الكوفة"),
                4900
            )
            id == "sahabi_33" || name.contains("زيد بن ثابت") -> Quadruple(
                ERA_RASHIDUN,
                "لجنة تدوين وجمع المصحف الشريف",
                listOf("يثرب (المدينة المنورة)", "الخندق", "مكة", "المدينة"),
                1100
            )
            id == "sahabi_34" || name.contains("حذيفة بن اليمان") -> Quadruple(
                ERA_RASHIDUN,
                "ليلة الأحزاب ونهاوند وإمارة المدائن",
                listOf("المدينة المنورة", "أحد", "الخندق", "أذربيجان", "نهاوند", "المدائن"),
                4200
            )
            id == "sahabi_35" || name.contains("أبو الدرداء") -> Quadruple(
                ERA_RASHIDUN,
                "تأسيس مقارئ القرآن بالجامع الأموي بدمشق",
                listOf("المدينة المنورة", "أحد", "صفين", "دمشق الشام"),
                2300
            )
            id == "sahabi_36" || name.contains("زيد بن حارثة") -> Quadruple(
                ERA_PROPHETIC,
                "قيادة جيش مؤتة والشهادة المجيدة",
                listOf("مكة المكرمة", "الطائف", "المدينة المنورة", "مؤتة (الأردن)"),
                2400
            )
            id == "sahabi_37" || name.contains("ثابت بن قيس") -> Quadruple(
                ERA_RASHIDUN,
                "خطابة الإسلام والشهادة في معركة اليمامة",
                listOf("المدينة المنورة", "أحد", "الخندق", "اليمامة (نجد)"),
                1700
            )
            id == "sahabi_38" || name.contains("عمرو بن العاص") -> Quadruple(
                ERA_RASHIDUN,
                "فتح مصر وبناء جامع عمرو بالفسطاط",
                listOf("مكة المكرمة", "عُمان", "فلسطين", "سيناء", "حصن بابليون", "الإسكندرية"),
                5600
            )
            id == "sahabi_39" || name.contains("عبد الله بن حذافة") -> Quadruple(
                ERA_RASHIDUN,
                "الصمود أمام قيصر الروم وقدور الزيت المغلي",
                listOf("مكة المكرمة", "المدائن (فارس)", "القسطنطينية (بلاد الروم)", "مصر"),
                6200
            )
            id == "sahabi_40" || name.contains("سعيد بن عامر") -> Quadruple(
                ERA_RASHIDUN,
                "إمارة حمص الزاهدة وخدمة الفقراء",
                listOf("مكة المكرمة", "خيبر", "المدينة المنورة", "حمص (الشام)"),
                2400
            )

            // أئمة التابعين والعلماء
            name.contains("سعيد بن المسيب") -> Quadruple(
                ERA_UMAYYAD,
                "سيد التابعين والصمود أمام سياط بني أمية",
                listOf("المدينة المنورة", "مكة المكرمة"),
                900
            )
            name.contains("سعيد بن جبير") -> Quadruple(
                ERA_UMAYYAD,
                "مواجهة الحجاج بن يوسف والشهادة الصادعة",
                listOf("الكوفة", "مكة المكرمة", "واسط"),
                2100
            )
            name.contains("الحسن البصري") -> Quadruple(
                ERA_UMAYYAD,
                "سيد الزهاد والموعظة الصادعة في وجه الأمراء",
                listOf("المدينة المنورة", "البصرة", "واسط"),
                2300
            )
            name.contains("عمر بن عبد العزيز") -> Quadruple(
                ERA_UMAYYAD,
                "الخامس الراشد، رد المظالم وإحياء العدل",
                listOf("المدينة المنورة", "دمشق", "دير سمعان"),
                2800
            )
            name.contains("أبو حنيفة") -> Quadruple(
                ERA_ABBASID,
                "رفض قضاء المنصور والسجن والسم",
                listOf("الكوفة", "مكة المكرمة", "المدينة المنورة", "بغداد"),
                3200
            )
            name.contains("مالك بن أنس") -> Quadruple(
                ERA_ABBASID,
                "إمام دار الهجرة ومحنة الضرب والخلع",
                listOf("المدينة المنورة", "مكة المكرمة"),
                1000
            )
            name.contains("الشافعي") -> Quadruple(
                ERA_ABBASID,
                "تأسيس علم أصول الفقه والرحلة إلى مصر",
                listOf("غزة", "مكة المكرمة", "المدينة المنورة", "صنعاء (اليمن)", "بغداد", "الفسطاط (مصر)"),
                6500
            )
            name.contains("أحمد بن حنبل") -> Quadruple(
                ERA_ABBASID,
                "محنة خلق القرآن والصبر تحت مائة سوط",
                listOf("بغداد", "الكوفة", "البصرة", "مكة المكرمة", "المدينة المنورة", "طرسوس"),
                4800
            )
            name.contains("البخاري") -> Quadruple(
                ERA_ABBASID,
                "تدوين الجامع الصحيح ومحنة النفي",
                listOf("بخارى", "مكة المكرمة", "المدينة المنورة", "بغداد", "البصرة", "نيسابور", "خرتنك"),
                14000
            )
            name.contains("مسلم") -> Quadruple(
                ERA_ABBASID,
                "صحيح مسلم والوفاء للبخاري في محنته",
                listOf("نيسابور", "العراق", "الحجاز", "مصر", "الري"),
                7800
            )
            name.contains("العز بن عبد السلام") -> Quadruple(
                ERA_MAMLUK,
                "سلطان العلماء، بيع الأمراء ومعركة عين جالوت",
                listOf("دمشق", "القدس الشريف", "الكرك", "القاهرة (مصر)"),
                3100
            )
            name.contains("ابن تيمية") -> Quadruple(
                ERA_MAMLUK,
                "معركة شقحب، محنة قلعة دمشق وكتابة الفتاوى",
                listOf("حران", "دمشق", "القاهرة", "الإسكندرية", "قلعة دمشق"),
                4400
            )
            name.contains("النووي") -> Quadruple(
                ERA_MAMLUK,
                "مواجهة الظاهر بيبرس وتأليف رياض الصالحين",
                listOf("نوى (حوران)", "دمشق الشام", "القدس الشريف"),
                1200
            )
            name.contains("ابن القيم") -> Quadruple(
                ERA_MAMLUK,
                "محنة السجن في قلعة دمشق ومؤلفات مدارج السالكين",
                listOf("دمشق", "القدس", "مكة المكرمة"),
                2400
            )
            id == "sheikh_samir_mustafa" || name.contains("سمير مصطفى") -> Quadruple(
                "العصر الحديث والمعاصر",
                "سلسلة عقائد البلاء وتربية الأمة على التوحيد والثبات",
                listOf("القاهرة", "الجيزة", "المحافظات المصرية", "منصات ومجالس العالم الإسلامي"),
                5200
            )

            // الافتراضي للصحابة أو العلماء
            id.startsWith("sahabi") -> Quadruple(
                ERA_RASHIDUN,
                "نصرة الإسلام والجهاد في سبيل الله",
                listOf("مكة المكرمة", "المدينة المنورة"),
                1200
            )
            else -> Quadruple(
                ERA_ABBASID,
                "حفظ ونقل علوم الشريعة للأمة",
                listOf("المدينة المنورة", "بغداد", "دمشق"),
                2500
            )
        }
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
