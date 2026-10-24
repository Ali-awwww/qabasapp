package com.qabas.app

/**
 * بيانات ومسارات سلسلة «عقائد البلاء» لفضيلة الشيخ سمير مصطفى فرج حفظه الله.
 *
 * تتضمن السلسلة كافة الدروس المعتمدة بروابطها المباشرة للاستماع والتنزيل والمشاهدة،
 * مع تصنيفها الكامل تحت اسم الشيخ وسلسلته العقدية المباركة.
 */
object SamirMustafaAqaidAlBalaaData {

    const val SCHOLAR_ID = "sheikh_samir_mustafa"
    const val SCHOLAR_NAME = "الشيخ سمير مصطفى"
    const val SCHOLAR_TITLE = "فضيلة الشيخ سمير مصطفى فرج"
    const val SERIES_TITLE = "سلسلة عقائد البلاء"
    const val CATEGORY_NAME = "الشيخ سمير مصطفى"

    data class BalaaLesson(
        val episodeNumber: Int,
        val title: String,
        val subtitle: String,
        val duration: String,
        val audioUrl: String,
        val youtubeUrl: String,
        val webCollectionUrl: String = "https://www.islamweb.net/ar/audio/index.php?page=lecview&sid=1821",
        val synopsis: String,
        val keyTopic: String
    )

    /**
     * قائمة دروس سلسلة عقائد البلاء كاملة ومرتبة
     */
    val LESSONS = listOf(
        BalaaLesson(
            episodeNumber = 1,
            title = "عقائد البلاء 01: مقدمة السلسلة وحقيقة الابتلاء",
            subtitle = "المدخل المنهجي لفهم حكمة الله في نزول البلاء وتمايز الصفوف",
            duration = "45:12",
            audioUrl = "https://media.islamweb.net/lecturs/samir_mostafa/aqaed_albalaa_01.mp3",
            youtubeUrl = "https://www.youtube.com/watch?v=sW9k9P9L8aI",
            synopsis = "بيان الأصل في خلق الإنسان وأنه في كبد، وكيف أن البلاء سنة ماضية لا تنفك عن مؤمن، وتأصيل نظرة العبد لأقدار الله بحسن الظن والتسليم.",
            keyTopic = "حقيقة الابتلاء وسنة التدافع"
        ),
        BalaaLesson(
            episodeNumber = 2,
            title = "عقائد البلاء 02: حقائق البلاء ومقاصده الربانية",
            subtitle = "لماذا يبتلي الله عباده الصالحين؟ تطهير الذنوب ورفعة الدرجات",
            duration = "48:30",
            audioUrl = "https://media.islamweb.net/lecturs/samir_mostafa/aqaed_albalaa_02.mp3",
            youtubeUrl = "https://www.youtube.com/watch?v=jK7X3f9P0qE",
            synopsis = "شرح المقاصد الكبرى للبلاء؛ فهو كير يمحص القلوب ويزيل الخبث، ودواء مر يستخرج العبوديات الخفية كالانكسار والافتقار والرجاء الصادق.",
            keyTopic = "مقاصد البلاء والحكمة الإلهية"
        ),
        BalaaLesson(
            episodeNumber = 3,
            title = "عقائد البلاء 03: العقيدة الأولى - بطش الله وانتقامه للمظلومين",
            subtitle = "﴿إِنَّ بَطْشَ رَبِّكَ لَشَدِيدٌ﴾ - يقين المؤمن في زوال دولة الباطل",
            duration = "52:18",
            audioUrl = "https://media.islamweb.net/lecturs/samir_mostafa/aqaed_albalaa_03.mp3",
            youtubeUrl = "https://www.youtube.com/watch?v=aB6k0X8Y9zM",
            synopsis = "تثبيت قلوب المبتلين بأن الظالمين مهما استطالوا فإن ناصيتهم بيد قاهر الجبابرة، وأن إمهال الله للظالم ليس إهمالاً بل استدراجاً لأخذه أخذ عزيز مقتدر.",
            keyTopic = "بطش الله ونصرة المستضعفين"
        ),
        BalaaLesson(
            episodeNumber = 4,
            title = "عقائد البلاء 04: العقيدة الثانية - الإيعاد والوعيد الإلهي",
            subtitle = "صدق وعد الله ووعيده وكمال العدل في الدار الآخرة",
            duration = "44:50",
            audioUrl = "https://media.islamweb.net/lecturs/samir_mostafa/aqaed_albalaa_04.mp3",
            youtubeUrl = "https://www.youtube.com/watch?v=wE4r8T7U1vP",
            synopsis = "استعراض نصوص الوعد والوعيد في القرآن، وأن الدنيا ليست دار الجزاء الكامل، فغمسة واحدة في نعيم الجنة تُنسي بؤس الدنيا كلها.",
            keyTopic = "عقيدة الوعيد وموازين الآخرة"
        ),
        BalaaLesson(
            episodeNumber = 5,
            title = "عقائد البلاء 05: العقيدة الثالثة - الله الودود",
            subtitle = "تلمس لطف الله ووده في ثنايا الشدائد والكروب",
            duration = "50:40",
            audioUrl = "https://media.islamweb.net/lecturs/samir_mostafa/aqaed_albalaa_05.mp3",
            youtubeUrl = "https://www.youtube.com/watch?v=mN3v6C9X2qL",
            synopsis = "معرفة اسم الله 'الودود' وكيف يتودد الرب لعباده بنعمه وألطافه حتى في عمق المحن، وكيف يولد الابتلاء أنساً وقرباً لا يدركه أصحاب الرخاء.",
            keyTopic = "اسم الله الودود واللطف الخفي"
        ),
        BalaaLesson(
            episodeNumber = 6,
            title = "عقائد البلاء 06: وهو الغفور - مغفرة الذنوب تحت وطأة المحنة",
            subtitle = "تكفير الخطايا ومحو الأوزار حتى يلقى العبد ربه وما عليه خطيئة",
            duration = "46:15",
            audioUrl = "https://media.islamweb.net/lecturs/samir_mostafa/aqaed_albalaa_06.mp3",
            youtubeUrl = "https://www.youtube.com/watch?v=xZ8c4V1B0nM",
            synopsis = "حديث: «ما يزال البلاء بالمؤمن والمؤمنة في نفسه وولده وماله حتى يلقى الله تعالى وما عليه خطيئة»، وكيف يحمد العبد ربه على كفارات الدنيا.",
            keyTopic = "مغفرة الذنوب وتطهير الأرواح"
        ),
        BalaaLesson(
            episodeNumber = 7,
            title = "عقائد البلاء 07: العناق الأحمر وتضحيات الصادقين",
            subtitle = "قصة أصحاب الأخدود وعناق الشهادة في سبيل التوحيد",
            duration = "55:20",
            audioUrl = "https://media.islamweb.net/lecturs/samir_mostafa/aqaed_albalaa_07.mp3",
            youtubeUrl = "https://www.youtube.com/watch?v=qP1o9I8U7yT",
            synopsis = "تأملات عقدية عميقة في سورة البروج وقصة الغلام والراهب والملك، وبيان أن النصر الحقيقي هو ثبات العقيدة حتى لو فنيت الأجساد في الأخدود.",
            keyTopic = "ثبات العقيدة وأصحاب الأخدود"
        ),
        BalaaLesson(
            episodeNumber = 8,
            title = "عقائد البلاء 08: الموت الرحيب وراحة المؤمنين",
            subtitle = "الموت بوابة العبور إلى دار السلام ولقاء الأحبة محمد وصحبه",
            duration = "49:35",
            audioUrl = "https://media.islamweb.net/lecturs/samir_mostafa/aqaed_albalaa_08.mp3",
            youtubeUrl = "https://www.youtube.com/watch?v=rT5y6U7I8oP",
            synopsis = "كيف ينظر المؤمن إلى الموت كرحابة ونجاة من فتن الدنيا وزينتها المتقلبة، واستشعار حسن الختام ومشاعر الطمأنينة عند الاحتضار.",
            keyTopic = "فلسفة الموت عند المؤمن وحسن الختام"
        ),
        BalaaLesson(
            episodeNumber = 9,
            title = "عقائد البلاء 09: العلماء ضمير الأمة وثبات القدوة",
            subtitle = "مسؤولية حملة الشريعة في قيادة الأمة وقت الفتن والشدائد",
            duration = "53:45",
            audioUrl = "https://media.islamweb.net/lecturs/samir_mostafa/aqaed_albalaa_09.mp3",
            youtubeUrl = "https://www.youtube.com/watch?v=yU8i9O0P1aZ",
            synopsis = "دور العالم والداعية الرباني في بث الأمل وتثبيت الناس ونفي التحريف عن الدين عند اشتداد الكرب، مع نماذج من ثبات سلف الأمة كأحمد وسعيد بن جبير.",
            keyTopic = "ثبات العلماء وقيادة الأمة"
        )
    )

    /**
     * تحويل الدروس إلى كائنات LiveAudioItem لقسم الصوتيات المركزي
     */
    fun toLiveAudioItems(): List<LiveAudioItem> {
        return LESSONS.map { lesson ->
            LiveAudioItem(
                id = "samir_mustafa_balaa_${lesson.episodeNumber}",
                title = lesson.title,
                category = "الشيخ سمير مصطفى",
                duration = lesson.duration,
                audioUrl = lesson.audioUrl,
                isAsset = false,
                badge = "سلسلة عقائد البلاء",
                author = SCHOLAR_NAME,
                youtubeUrl = lesson.youtubeUrl
            )
        }
    }

    /**
     * تحويل الدروس إلى كائنات AudioTrackRow الخاصة بـ SupabaseAudioSection
     */
    fun toAudioTrackRows(): List<SupabaseServices.AudioTrackRow> {
        return LESSONS.map { lesson ->
            SupabaseServices.AudioTrackRow(
                id = "samir_mustafa_balaa_${lesson.episodeNumber}",
                title = lesson.title,
                artist = SCHOLAR_NAME,
                category = "الشيخ سمير مصطفى",
                audioUrl = lesson.audioUrl,
                duration = lesson.duration,
                badge = "عقائد البلاء",
                author = SCHOLAR_NAME,
                likesCount = 312,
                isActive = true
            )
        }
    }

    /**
     * تحويل الدروس إلى كائنات AudioTrackEntity لقاعدة بيانات Room المحلية
     */
    fun toAudioTrackEntities(): List<AudioTrackEntity> {
        return LESSONS.map { lesson ->
            AudioTrackEntity(
                id = "samir_mustafa_balaa_${lesson.episodeNumber}",
                title = lesson.title,
                artistOrVibe = SCHOLAR_NAME,
                typeCategory = "الشيخ سمير مصطفى",
                audioUrlOrPath = lesson.audioUrl,
                duration = lesson.duration,
                scriptText = lesson.synopsis,
                createdAt = System.currentTimeMillis()
            )
        }
    }

    /**
     * تحويل الدروس إلى كائنات ScholarLecture المعتمدة لبطاقة الشيخ وسيرته
     */
    fun toScholarLectures(): List<ScholarLecture> {
        return LESSONS.map { lesson ->
            ScholarLecture(
                id = "curated_samir_mustafa_balaa_${lesson.episodeNumber}",
                scholarId = SCHOLAR_ID,
                scholarName = SCHOLAR_NAME,
                title = lesson.title,
                speaker = SCHOLAR_NAME,
                url = lesson.youtubeUrl,
                type = "صوتي / بودكاست",
                category = "عقائد البلاء",
                synopsis = lesson.synopsis,
                durationApprox = lesson.duration,
                isOfficialDeveloperCurated = true,
                notes = "من درر سلسلة عقائد البلاء الموثقة للشيخ سمير مصطفى."
            )
        }
    }

    /**
     * سيرة وبطاقة الشيخ سمير مصطفى لقسم سير الأكابر
     */
    val SHEIKH_BIOGRAPHY = ScholarBiography(
        id = SCHOLAR_ID,
        name = "فضيلة الشيخ سمير مصطفى حفظه الله",
        brief = "سمير مصطفى فرج، داعية وخطيب وعالم مصري معاصر، اشتُهر بدروسه الإيمانية العميقة وسلاسله العقدية المؤثرة كـ 'عقائد البلاء' و'سير أعلام النبلاء'.",
        imageRes = R.drawable.scholar_library_bg_1790092389658,
        period = "معاصر",
        tags = listOf("الشيخ سمير مصطفى", "أئمة المحنة والشهادة", "أطباء القلوب والزهد", "عقيدة وتزكية"),
        audioUrl = LESSONS.first().audioUrl,
        sacrificeSummary = "أفنى حياته في الصدع بالحق وتربية الأجيال على عقيدة الثبات والصبر عند نزول البلاء، محتسباً صابراً في كل محنة وابتلاء.",
        enduranceQuote = "«إن البلاء كير يمحص القلوب، والمؤمن يرى في طيات الألم ألطاف أرحم الراحمين».",
        travelDistance = "دروس ومحاضرات ومجالس علمية طافت العالم الإسلامي عبر المنصات ومجالس الذكر",
        globalImpact = "أيقظت سلسلته 'عقائد البلاء' قلوب مئات الآلاف من المبتلين والمصابين، وغرست فيهم فقه الرضا والتسليم وبطش الله بالظالمين.",
        messageToGrandson = "يا بني، اعلم أن البلاء لم يأتِ ليهلكك بل ليصقلك، فاجعل قلبك معلقاً بربك الودود ولا تبتئس بما يفعل الظالمون.",
        modernChallenge = "استمع إلى درس من سلسلة 'عقائد البلاء' اليوم واستخرج منه سلوة لضائقة أو هم يمر بك.",
        whoHarmedHimAndHow = "واجه التضييق ومحن الدعوة كشأن الدعاة الصادقين، وثبت كالجبل الأشم ناشراً للأمل واليقين بحسن العاقبة.",
        howHeEndured = "بالإقبال على القرآن وتدبر أسماء الله الحسنى، والصبر على أقدار الله بالاحتساب والرضا.",
        whyHeEndured = "لتبصير الأمة بحقائق العقيدة في أوقات الأزمات والفتن وحفظ قلوب الشباب من اليأس والقنوط.",
        deathScene = "حفظه الله وثبته على الحق، وجعل علمه ودروسه منارة هدى وخير وبركة للأمة جمعاء.",
        lastWords = "«يا حي يا قيوم برحمتك أستغيث.. وحسبنا الله ونعم الوكيل».",
        funeralImpact = "محبوب في قلوب الملايين من طلاب العلم وعامة المسلمين في مشارق الأرض ومغاربها."
    )
}
