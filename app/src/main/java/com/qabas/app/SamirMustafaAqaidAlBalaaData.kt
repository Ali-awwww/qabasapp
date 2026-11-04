package com.qabas.app

/**
 * روابط مواد سلسلة «عقائد البلاء» كما تظهر في أرشيف صوتيات إسلام ويب.
 *
 * الروابط هنا صفحات المواد الرسمية وليست روابط ملفات MP3 مباشرة.
 * لذلك لا تُحوَّل إلى عناصر تشغيل أو تنزيل حتى يتوفر رابط ملف صوتي مباشر
 * ويتم التحقق منه بصورة مستقلة.
 */
object SamirMustafaAqaidAlBalaaData {

    const val SCHOLAR_ID = "sheikh_samir_mustafa"
    const val SCHOLAR_NAME = "سمير مصطفى فرج"
    const val SCHOLAR_TITLE = "الشيخ سمير مصطفى فرج"
    const val SERIES_TITLE = "سلسلة عقائد البلاء"
    const val CATEGORY_NAME = "الشيخ سمير مصطفى فرج"
    const val OFFICIAL_SERIES_URL =
        "https://audio.islamweb.com/audio/index.php?kh=0&lg=1886&page=lecview&read=0&sid=1074"

    data class BalaaLesson(
        val episodeNumber: Int,
        val title: String,
        val pageUrl: String
    )

    /**
     * المواد الاثنتا عشرة المدرجة في صفحة السلسلة على صوتيات إسلام ويب.
     * أبقينا أسماءها وروابط صفحاتها كما يعرضها المصدر، دون إنشاء مدد أو
     * ملخصات أو روابط تشغيل غير متحقق منها.
     */
    val LESSONS = listOf(
        BalaaLesson(
            episodeNumber = 1,
            title = "مقدمة من سلسلة عقائد البلاء 1",
            pageUrl = "https://audio.islamweb.com/audio/index.php?audioid=406375&kh=0&lg=1886&page=audioinfo&read=0"
        ),
        BalaaLesson(
            episodeNumber = 2,
            title = "مقدمة من سلسلة عقائد البلاء 2",
            pageUrl = "https://audio.islamweb.com/audio/index.php?audioid=406376&kh=0&lg=1886&page=audioinfo&read=0"
        ),
        BalaaLesson(
            episodeNumber = 3,
            title = "حقائق البلاء",
            pageUrl = "https://audio.islamweb.com/audio/index.php?audioid=406377&kh=0&lg=1886&page=audioinfo&read=0"
        ),
        BalaaLesson(
            episodeNumber = 4,
            title = "العناق الأحمر",
            pageUrl = "https://audio.islamweb.com/audio/index.php?audioid=406378&kh=0&lg=1886&page=audioinfo&read=0"
        ),
        BalaaLesson(
            episodeNumber = 5,
            title = "بطش الله",
            pageUrl = "https://audio.islamweb.com/audio/index.php?audioid=408911&kh=0&lg=1886&page=audioinfo&read=0"
        ),
        BalaaLesson(
            episodeNumber = 6,
            title = "مقدمة السلسلة 1",
            pageUrl = "https://audio.islamweb.com/audio/index.php?audioid=664174&kh=0&lg=1886&page=audioinfo&read=0"
        ),
        BalaaLesson(
            episodeNumber = 7,
            title = "العقيدة الأولى بطش الله",
            pageUrl = "https://audio.islamweb.com/audio/index.php?audioid=664177&kh=0&lg=1886&page=audioinfo&read=0"
        ),
        BalaaLesson(
            episodeNumber = 8,
            title = "العقيدة الثانية عقيدة الإيعاد",
            pageUrl = "https://audio.islamweb.com/audio/index.php?audioid=664178&kh=0&lg=1886&page=audioinfo&read=0"
        ),
        BalaaLesson(
            episodeNumber = 9,
            title = "العقيدة الثالثة وهو الغفور",
            pageUrl = "https://audio.islamweb.com/audio/index.php?audioid=664179&kh=0&lg=1886&page=audioinfo&read=0"
        ),
        BalaaLesson(
            episodeNumber = 10,
            title = "العقيدة الثالثة جـ 2 الله الودود",
            pageUrl = "https://audio.islamweb.com/audio/index.php?audioid=664203&kh=0&lg=1886&page=audioinfo&read=0"
        ),
        BalaaLesson(
            episodeNumber = 11,
            title = "الموت الرحيب",
            pageUrl = "https://audio.islamweb.com/audio/index.php?audioid=664204&kh=0&lg=1886&page=audioinfo&read=0"
        ),
        BalaaLesson(
            episodeNumber = 12,
            title = "العلماء ضمير الأمة",
            pageUrl = "https://audio.islamweb.com/audio/index.php?audioid=664205&kh=0&lg=1886&page=audioinfo&read=0"
        )
    )

    /**
     * لا تتوفر في المصدر الذي تحققنا منه روابط MP3 مباشرة؛ إبقاء القائمة
     * الصوتية خالية يمنع ظهور مواد بروابط تشغيل تجريبية أو صفحات HTML.
     */
    fun toLiveAudioItems(): List<LiveAudioItem> = emptyList()

    /**
     * لا تُضاف صفحات الويب كأنها ملفات صوتية إلى مكتبة Supabase.
     */
    fun toAudioTrackRows(): List<SupabaseServices.AudioTrackRow> = emptyList()

    /**
     * لا تُنشأ سجلات صوتية محلية من روابط صفحات الويب.
     */
    fun toAudioTrackEntities(): List<AudioTrackEntity> = emptyList()

    /**
     * عرض صفحات المواد الرسمية في تبويب الدروس المرتبطة بترجمة المحاضر.
     */
    fun toScholarLectures(): List<ScholarLecture> {
        return LESSONS.map { lesson ->
            ScholarLecture(
                id = "curated_samir_mustafa_balaa_${lesson.episodeNumber}",
                scholarId = SCHOLAR_ID,
                scholarName = SCHOLAR_NAME,
                title = lesson.title,
                speaker = SCHOLAR_NAME,
                url = lesson.pageUrl,
                type = "صوتي / بودكاست",
                category = SERIES_TITLE,
                synopsis = "",
                durationApprox = "",
                isOfficialDeveloperCurated = true,
                notes = "صفحة المادة في أرشيف صوتيات إسلام ويب؛ الرابط ليس ملف MP3 مباشراً."
            )
        }
    }

    /**
     * نبذة تعريفية محدودة بما يظهر في أرشيف صوتيات إسلام ويب.
     */
    val SHEIKH_BIOGRAPHY = ScholarBiography(
        id = SCHOLAR_ID,
        name = SCHOLAR_TITLE,
        brief = "تظهر في أرشيف صوتيات إسلام ويب صفحة للمحاضر باسم سمير مصطفى فرج، وتضم سلسلة «عقائد البلاء» اثنتي عشرة مادة صوتية. تقتصر هذه النبذة على ما يثبته الأرشيف المنشور.",
        imageRes = R.drawable.scholar_library_bg_1790092389658,
        period = "معاصر",
        tags = listOf("داعية معاصر", "سلاسل صوتية", "عقيدة وتزكية"),
        audioUrl = null,
        sacrificeSummary = null,
        enduranceQuote = null,
        travelDistance = null,
        globalImpact = null,
        messageToGrandson = null,
        modernChallenge = null,
        whoHarmedHimAndHow = null,
        howHeEndured = null,
        whyHeEndured = null,
        deathScene = null,
        lastWords = null,
        funeralImpact = null
    )
}
