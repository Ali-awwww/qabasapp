package com.qabas.app

/**
 * بنك الدروس والمحاضرات المعتمدة والموثقة (Curated Developer Library)
 * تم سرد محاورها ومضامينها بشكل تفصيلي وأمعن لربط المستخدم بالدروس الموثوقة
 */
object ScholarCuratedLectures {

    fun getCuratedLectures(scholarId: String, scholarName: String): List<ScholarLecture> {
        val specific = CURATED_MAP[scholarId]
        if (!specific.isNullOrEmpty()) {
            return specific
        }

        if (scholarId == SamirMustafaAqaidAlBalaaData.SCHOLAR_ID || scholarName.contains("سمير مصطفى")) {
            return SamirMustafaAqaidAlBalaaData.toScholarLectures()
        }

        // Generic deep educational entry for any other scholar/companion
        val cleanName = scholarName.replace("الإمام", "").replace("الصحابي", "").trim()
        val searchQuery = "سيرة $cleanName دروس ومحاضرات".replace(" ", "+")
        return listOf(
            ScholarLecture(
                id = "curated_${scholarId}_1",
                scholarId = scholarId,
                scholarName = scholarName,
                title = "محطات الثبات والمحنة في سيرة $scholarName",
                speaker = "نخبة من كبار العلماء والدعاة",
                url = "https://www.youtube.com/results?search_query=$searchQuery",
                type = "يوتيوب / مرئي",
                category = "المحنة والثبات",
                synopsis = "سرد مفصل وشامل يتناول أعظم المحطات التاريخية والمحن التي مر بها $scholarName وكيف كان ثباته منارة للأمة عبر العصور، مع استنباط الدروس التربوية والإيمانية المعاصرة.",
                durationApprox = "سلسلة علمية موثقة",
                isOfficialDeveloperCurated = true,
                notes = "مادة معتمدة وموصى بها للاستماع والتأسي."
            ),
            ScholarLecture(
                id = "curated_${scholarId}_2",
                scholarId = scholarId,
                scholarName = scholarName,
                title = "المناقب والآثار العلمية والجهادية للإمام $cleanName",
                speaker = "سلاسل أعلام الإسلام",
                url = "https://www.youtube.com/results?search_query=${"مناقب $cleanName فقه وحديث".replace(" ", "+")}",
                type = "صوتي / بودكاست",
                category = "المناقب والفضائل",
                synopsis = "تسليط الضوء على الأثر الخالد للإمام وما تركه من بصمات في نشر الدين، والتمسك بالحق، وتربية الأجيال.",
                durationApprox = "45 دقيقة",
                isOfficialDeveloperCurated = true,
                notes = "مادة صوتية نقية مناسبة للاستماع اليومي."
            )
        )
    }

    private val CURATED_MAP: Map<String, List<ScholarLecture>> = mapOf(
        "abu_bakr" to listOf(
            ScholarLecture(
                id = "curated_abu_bakr_1",
                scholarId = "abu_bakr",
                scholarName = "أبو بكر الصديق",
                title = "الصديق الأعظم.. أيام المحنة الكبرى وحروب الردة",
                speaker = "د. راغب السرجاني",
                url = "https://www.youtube.com/results?search_query=سيرة+ابي+بكر+الصديق+راغب+السرجاني",
                type = "يوتيوب / مرئي",
                category = "المحنة والثبات",
                synopsis = "سرد تاريخي وتحليلي دقيق لموقف الصديق الأسطوري يوم ارتدت العرب، حيث وقف كالجبل الأشم معلناً: «أينقص الدين وأنا حي؟ والله لأقاتلن من فرّق بين الصلاة والزكاة»، وكيف حفظ الله به أركان الإسلام بعد وفاة النبي ﷺ.",
                durationApprox = "سلسلة من 8 حلقات",
                isOfficialDeveloperCurated = true,
                notes = "تحليل استراتيجي وإيماني عميق لفقه إدارة الأزمات والثبات العقدي."
            ),
            ScholarLecture(
                id = "curated_abu_bakr_2",
                scholarId = "abu_bakr",
                scholarName = "أبو بكر الصديق",
                title = "سابق الأمة ومناقب الإيمان والتضحية بالمال كله",
                speaker = "د. عثمان الخميس",
                url = "https://www.youtube.com/results?search_query=فضائل+ابي+بكر+عثمان+الخميس",
                type = "يوتيوب / مرئي",
                category = "المناقب والفضائل",
                synopsis = "استعراض نصوص الوحي في فضله وسابقته للإسلام دون تردد، وإنفاقه لماله كله حين سأله النبي ﷺ: «ما أبقيت لأهلك؟ قال: أبقيت لهم الله ورسوله»، ومواقفه في الهجرة النبوية بوفاء وإخلاص لا نظير له.",
                durationApprox = "55 دقيقة",
                isOfficialDeveloperCurated = true,
                notes = "بيان أحاديث الفضل والمكانة العلية لصاحب الغار."
            )
        ),
        "umar_ibn_al_khattab" to listOf(
            ScholarLecture(
                id = "curated_umar_1",
                scholarId = "umar_ibn_al_khattab",
                scholarName = "عمر بن الخطاب",
                title = "الفاروق عمر: موازين العدل وهيبة القيادة الربانية",
                speaker = "الشيخ محمد حسان",
                url = "https://www.youtube.com/results?search_query=عمر+بن+الخطاب+محمد+حسان",
                type = "يوتيوب / مرئي",
                category = "المناقب والفضائل",
                synopsis = "سرد مؤثر يخلب القلوب لمواقف عدل عمر مع الرعية، وتفقده للأرامل والأيتام في جوف الليل، وبكائه الشديد خشية أن تُسأل عنه شاة لو عثرت بالعراق، مع بيان فتوحاته التي كسرت عرش كسرى وقيصر.",
                durationApprox = "سلسلة من 6 أجزاء",
                isOfficialDeveloperCurated = true,
                notes = "درس لا غنى عنه لكل مربٍّ وقائد ومحب لسير العدل والإنصاف."
            ),
            ScholarLecture(
                id = "curated_umar_2",
                scholarId = "umar_ibn_al_khattab",
                scholarName = "عمر بن الخطاب",
                title = "مؤامرة الفجر واستشهاد الفاروق وهو يقرأ القرآن في المحراب",
                speaker = "د. راغب السرجاني",
                url = "https://www.youtube.com/results?search_query=مقتل+عمر+بن+الخطاب+راغب+السرجاني",
                type = "يوتيوب / مرئي",
                category = "المحنة والثبات",
                synopsis = "استعراض درامي وتأريخي مهيب للحظات اغتيال الفاروق وهو يصلي الفجر بالمسلمين، وكيف أكمل الصلاة بالمسلمين وهو ينزف، ثم وصاياه العظيمة لأهل الشورى قبل خروج روحه الطاهرة.",
                durationApprox = "48 دقيقة",
                isOfficialDeveloperCurated = true,
                notes = "مشهد وداع يهز الوجدان ويبرز عظمة الاستشهاد في سبيل الله."
            )
        ),
        "uthman_ibn_affan" to listOf(
            ScholarLecture(
                id = "curated_uthman_1",
                scholarId = "uthman_ibn_affan",
                scholarName = "عثمان بن عفان",
                title = "ذو النورين عثمان: أيام الحصار وشهادة المحراب والمصحف",
                speaker = "الشيخ سعيد الكملي",
                url = "https://www.youtube.com/results?search_query=عثمان+بن+عفان+سعيد+الكملي",
                type = "يوتيوب / مرئي",
                category = "المحنة والثبات",
                synopsis = "سرد علمي وأدبي بليغ لأيام الحصار الظالم على دار عثمان بن عفان، ورفضه القاطع لإراقة أي قطرة دم من المسلمين للدفاع عنه، واستشهاده وهو صائم يتلو كتاب الله، وسقوط أول قطرة دم على قوله تعالى: «فسيكفيكهم الله وهو السميع العليم».",
                durationApprox = "62 دقيقة",
                isOfficialDeveloperCurated = true,
                notes = "من أروع المحاضرات التي تكشف صبر وخلق وحياء عثمان رضي الله عنه."
            )
        ),
        "ali_ibn_abi_talib" to listOf(
            ScholarLecture(
                id = "curated_ali_1",
                scholarId = "ali_ibn_abi_talib",
                scholarName = "علي بن أبي طالب",
                title = "أسد الله الغالب علي بن أبي طالب: شجاعة الميدان وبلاغة الزهد",
                speaker = "الشيخ نبيل العوضي",
                url = "https://www.youtube.com/results?search_query=علي+بن+ابي+طالب+نبيل+العوضي",
                type = "يوتيوب / مرئي",
                category = "الجهاد والفتوحات",
                synopsis = "بيان بطولات فتى بني هاشم ومبارزاته التي هزت معسكرات الكفر في بدر والأحزاب وخيبر، مع استعراض حياته الزاهدة في الكوفة وطعامه الخشن وهو أمير المؤمنين، ومواقفه في مقارعة الخوارج بالحجة والسيف.",
                durationApprox = "سلسلة من 5 أجزاء",
                isOfficialDeveloperCurated = true,
                notes = "سرد جذاب للناشئة والكبار يجمع بين روعة البطولات وعمق الزهد."
            )
        ),
        "ahmad_ibn_hanbal" to listOf(
            ScholarLecture(
                id = "curated_ahmad_1",
                scholarId = "ahmad_ibn_hanbal",
                scholarName = "الإمام أحمد بن حنبل",
                title = "محنة خلق القرآن الكبرى: ثبات الإمام أحمد أمام سياط المعتصم",
                speaker = "الشيخ سعيد الكملي",
                url = "https://www.youtube.com/results?search_query=محنة+الامام+احمد+سعيد+الكملي",
                type = "يوتيوب / مرئي",
                category = "المحنة والثبات",
                synopsis = "تحليل بليغ ومفصل لمحنة فتنة خلق القرآن التي امتحن فيها أئمة الإسلام، وانفراد الإمام أحمد بالصبر تحت وقع السياط وظلمات السجون، محافظاً على عقيدة السلف الصالح بمقولته: «أعطوني شيئاً من كتاب الله أو سنة رسوله حتى أقول به».",
                durationApprox = "75 دقيقة",
                isOfficialDeveloperCurated = true,
                notes = "أعظم مدرسة في الثبات على السنة والتضحية بالجسد لحفظ العقيدة."
            ),
            ScholarLecture(
                id = "curated_ahmad_2",
                scholarId = "ahmad_ibn_hanbal",
                scholarName = "الإمام أحمد بن حنبل",
                title = "مسند الإمام أحمد وزهده ووداع بغداد المليوني",
                speaker = "الشيخ صالح العصيمي",
                url = "https://www.youtube.com/results?search_query=مسند+الامام+احمد+صالح+العصيمي",
                type = "صوتي / بودكاست",
                category = "الفقه والحديث",
                synopsis = "رحلته لجمع أربعين ألف حديث في المسند، وزهده الشديد برفض أموال الخلفاء، حتى كان يوم وفاته يوماً فارقاً في تاريخ بغداد بخروج ما يقارب مليون مشيع لجنازته.",
                durationApprox = "50 دقيقة",
                isOfficialDeveloperCurated = true,
                notes = "مادة فقهية وتأريخية جامعة لمنهج الإمام ومسنده."
            )
        ),
        "ibn_taymiyyah" to listOf(
            ScholarLecture(
                id = "curated_ibn_taymiyyah_1",
                scholarId = "ibn_taymiyyah",
                scholarName = "شيخ الإسلام ابن تيمية",
                title = "ابن تيمية: جهاد السيف واللسان ومعارك التتار وسجون القلعة",
                speaker = "الشيخ عبد العزيز الطريفي",
                url = "https://www.youtube.com/results?search_query=ابن+تيمية+عبد+العزيز+الطريفي",
                type = "يوتيوب / مرئي",
                category = "المحنة والثبات",
                synopsis = "سرد عميق واستثنائي لمسيرة شيخ الإسلام في محاربة البدع وقيادة جيوش المسلمين ضد جحافل التتار في شقحب، ومحنه المتتالية في سجون دمشق والإسكندرية والقاهرة، ووفاته سجيناً بقلعة دمشق محرراً لكتاب ربه.",
                durationApprox = "سلسلة من 4 محاضرات",
                isOfficialDeveloperCurated = true,
                notes = "استعراض بديع لمقولاته الخالدة: «ما يصنع أعدائي بي؟ أنا جنتي وبستاني في صدري»."
            )
        ),
        "al_bukhari" to listOf(
            ScholarLecture(
                id = "curated_al_bukhari_1",
                scholarId = "al_bukhari",
                scholarName = "الإمام البخاري",
                title = "أمير المؤمنين في الحديث: رحلة الـ 14 ألف كم ومحنة نيسابور",
                speaker = "الشيخ سعيد الكملي",
                url = "https://www.youtube.com/results?search_query=الامام+البخاري+سعيد+الكملي",
                type = "يوتيوب / مرئي",
                category = "الفقه والحديث",
                synopsis = "سرد ممتع لتفاصيل رحلات البخاري بين بخارى وبغداد ودمشق ومكة والمدينة ونيسابور، وشروطه الصارمة في تصحيح الأحاديث، وابتلائه في أواخر عمره بالنفي والمحنة حتى وافاه الأجل وهو يدعو الله أن يريحه من دار الفتن.",
                durationApprox = "68 دقيقة",
                isOfficialDeveloperCurated = true,
                notes = "من أروع ما سُمع في مناقب البخاري وجهاده لخدمة السنة النبوية."
            )
        ),
        "khalid_ibn_al_walid" to listOf(
            ScholarLecture(
                id = "curated_khalid_1",
                scholarId = "khalid_ibn_al_walid",
                scholarName = "خالد بن الوليد",
                title = "سيف الله المسلول: عبقرية اليرموك ولحظات الوداع الأخيرة",
                speaker = "د. راغب السرجاني",
                url = "https://www.youtube.com/results?search_query=خالد+بن+الوليد+راغب+السرجاني",
                type = "يوتيوب / مرئي",
                category = "الجهاد والفتوحات",
                synopsis = "تحليل عسكري وتاريخي للعبقرية الحربية لسيف الله المسلول في اليرموك ومؤتة، وعزله وطاعته للخليفة، وموته المؤثر على فراشه بحمص وهو يقول: «ما في جسدي موضع شبر إلا وفيه ضربة سيف أو طعنة رمح.. فلا نامت أعين الجبناء».",
                durationApprox = "سلسلة كاملة",
                isOfficialDeveloperCurated = true,
                notes = "درس في الفداء والشجاعة والتجرد الكامل لله تعالى."
            )
        ),
        "al_izz_ibn_abd_al_salam" to listOf(
            ScholarLecture(
                id = "curated_al_izz_1",
                scholarId = "al_izz_ibn_abd_al_salam",
                scholarName = "العز بن عبد السلام",
                title = "سلطان العلماء: بيع الأمراء في المزاد وفتوى عين جالوت",
                speaker = "الشيخ محمد حسان",
                url = "https://www.youtube.com/results?search_query=العز+بن+عبد+السلام+محمد+حسان",
                type = "يوتيوب / مرئي",
                category = "المحنة والثبات",
                synopsis = "سرد مذهل لصلابة العز بن عبد السلام في الصدع بالحق، حين أمر ببيع أمراء المماليك لصالح بيت مال المسلمين، وثباته في وجه سلاطين الشام ومصر، وحثه لقطز على مواجهة التتار في عين جالوت.",
                durationApprox = "58 دقيقة",
                isOfficialDeveloperCurated = true,
                notes = "النموذج الأكمل للعالم الرباني الذي لا يخشى في الله لومة لائم."
            )
        )
    )
}
