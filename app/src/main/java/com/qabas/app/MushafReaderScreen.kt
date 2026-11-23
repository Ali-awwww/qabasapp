package com.qabas.app

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleTopBar
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.QabasDesignTheme
import com.qabas.app.ui.theme.QabasTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ============================================================================
// MushafReaderScreen.kt — قارئ المصحف (الدفعة 5 — الجزء C)
//
// لم يكن في التطبيق أي قارئ مصحف قائم بذاته قبل هذه الدفعة: كان قسم القرآن
// يعيد التوجيه إلى المشكاة، والمشكاة تعرض آيات مختارة تدور تلقائياً لا
// قراءةً يتصفحها المستخدم، وMushafPageData كانت تُستخدم للصوت فقط. لذلك
// هذا القارئ جديد: قائمة السور الـ 114 + آيات السورة المختارة، والصفحة
// رقم حقيقي من خريطة المصحف المدني (MushafPageData.getPageForAyah) لا
// رقم مخترع — وإن تعذّر تحميل الخريطة يُحفظ الموضع بالسورة والآية فقط.
//
// تتبّع آخر قراءة (UserPreferencesManager.saveLastReadQuran):
// - عند تغيّر أول آية ظاهرة، بعد مهلة 800ms (debounce) — لا حفظ عند كل إطار.
// - عند مغادرة الشاشة (onDispose) يُحفظ آخر موضع معروف فوراً.
// - عند دخول سورة جديدة يُحفظ موضعها الأول ضمن المهلة نفسها.
// ============================================================================

// ق148: لوحة الدراسة تنجو من التدوير — كانت studyVerse وpendingStudyVerse
// وstudyIrabBookOverride بـ remember عادٍ، فيغلق التدويرُ اللوحةَ كلها
// وتضيع الآية والمنتقى (والدرويش المفروض من المكتبة، وهو لا يُحفظ في
// التفضيلات عمداً)؛ والمنتقيان أنفسهما ينجوان عبر التفضيلات لأن كل نقرة
// تحفظ الكتاب فوراً. الحافظتان على سابقة DirectorChatScreen
// (Saver<…, List<String>>) — كل الحقول نصوص أو أعداد فتُرمَّز نصاً.
private val mushafStudyVerseSaver = Saver<MutableState<QuranVerseDetail?>, List<String>>(
    save = { state ->
        state.value?.let {
            listOf(
                it.surahNumber.toString(),
                it.surahName,
                it.verseNumber.toString(),
                it.verseText,
                it.tafseer,
                it.tajweedNotes
            )
        } ?: emptyList()
    },
    restore = { list ->
        mutableStateOf(
            if (list.size != 6) null else QuranVerseDetail(
                surahNumber = list[0].toIntOrNull() ?: return@Saver null,
                surahName = list[1],
                verseNumber = list[2].toIntOrNull() ?: return@Saver null,
                verseText = list[3],
                tafseer = list[4],
                tajweedNotes = list[5]
            )
        )
    }
)

private val mushafPendingStudyVerseSaver = Saver<MutableState<Pair<Int, Int>?>, List<String>>(
    save = { state -> state.value?.let { listOf(it.first.toString(), it.second.toString()) } ?: emptyList() },
    restore = { list ->
        mutableStateOf(
            if (list.size != 2) null else {
                val surah = list[0].toIntOrNull() ?: return@Saver null
                val ayah = list[1].toIntOrNull() ?: return@Saver null
                surah to ayah
            }
        )
    }
)

// ق153: الآيات المكشوفة في وضع الحفظ كانت بـ remember عادٍ فتضيع
// بالتدوير فيُعاد إخفاء ما كشفه المستخدم للتسميع — والحافظة على
// سابقة ق148 نفسها (زوج «سورة:آية» نصاً)؛ وتبقى تُصفَّر عند
// إيقاف الوضع كما هو موثق، فالنجاة من التدوير لا من إيقاف الوضع.
private val mushafRevealedVersesSaver = Saver<MutableState<Set<Pair<Int, Int>>>, List<String>>(
    save = { state -> state.value.map { "${it.first}:${it.second}" } },
    restore = { list ->
        mutableStateOf(
            list.mapNotNull { item ->
                val parts = item.split(":")
                if (parts.size != 2) null else {
                    val surah = parts[0].toIntOrNull() ?: return@mapNotNull null
                    val ayah = parts[1].toIntOrNull() ?: return@mapNotNull null
                    surah to ayah
                }
            }.toSet()
        )
    }
)

@Composable
fun MushafReaderScreen(
    initialSurah: Int? = null,
    initialAyah: Int? = null,
    initialPage: Int? = null,
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    // سمة القراءة وحجم الخط (خارطة المصحف — المرحلة 5): لوحة السمة
    // المختارة في الإعدادات تُطبَّق هنا فعلاً على سطح القراءة (خلفية
    // وبطاقات ونص وتمييز) عبر نسخة من سمة التطبيق — أما الشريط العلوي
    // والحوارات فتبقى بسمة التطبيق. قبل هذا الإصلاح كان منتقي السمة
    // في الإعدادات يغيّر معاينته فقط ولا يمس القارئ؛ وحجم الخط في
    // الإعدادات (14..30) كان يُهمل والقارئ مثبَّت على 19sp.
    val readingThemeKey by UserPreferencesManager.readingTheme.collectAsState()
    val readerPalette = remember(readingThemeKey) {
        UserPreferencesManager.getResolvedReadingThemePalette(readingThemeKey)
    }
    val theme = QabasTheme.current.copy(
        background = readerPalette.background,
        cardSurface = readerPalette.cardBackground,
        textPrimary = readerPalette.textColor,
        textSecondary = readerPalette.textSecondary,
        primary = readerPalette.accentColor,
        accent = readerPalette.accentColor,
        onPrimary = readerPalette.background
    )
    val quranFontSize by UserPreferencesManager.quranFontSize.collectAsState()
    val verseFontSize = quranFontSize.coerceIn(14f, 30f)
    // خط المصحف المختار في الإعدادات: كان القارئ يثبّت الأميري ويتجاهل
    // الاختيار كلياً (المنتقي لم يكن يغيّر إلا معاينة الإعدادات).
    val readingFontFamily by UserPreferencesManager.readingFontFamily.collectAsState()
    val verseFontFamily = remember(readingFontFamily) {
        UserPreferencesManager.getResolvedFontFamily(readingFontFamily)
    }
    // ألوان التجويد حسب إضاءة خلفية سمة القراءة الفعلية (فاتحة/داكنة):
    // اللوحة الواحدة كانت تفشل تبايناً على أحد النوعين حتماً.
    val tajweedColors = remember(readerPalette) {
        QuranDataProvider.tajweedRuleColors(readerPalette.background.luminance() > 0.5f)
    }

    var quranReady by remember { mutableStateOf(false) }
    // فشل تحميل المصحف: كان ensureQuranLoaded=false يترك القارئ على مؤشر
    // تحميل عارٍ للأبد بلا رسالة ولا إعادة محاولة. quranLoadAttempt يعيد
    // تشغيل التحميل كاملاً (أصول ← كاش ← شبكة) عند الضغط على إعادة المحاولة.
    var quranLoadFailed by remember { mutableStateOf(false) }
    var quranLoadAttempt by remember { mutableStateOf(0) }
    var pageMapReady by remember { mutableStateOf(false) }
    var selectedSurah by remember { mutableStateOf(initialSurah?.takeIf { it in 1..114 } ?: 1) }
    var targetAyah by remember { mutableStateOf<Int?>(null) }
    var targetConsumed by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf<Int?>(null) }

    // --- التلاوة الصوتية الاحترافية (خارطة المصحف — المرحلة 2) ---
    // قائمة التشغيل آياتٌ مستقلة (لا ملف صفحة واحد): تمييز الآية الجارية
    // حقيقي عبر onAyahChanged من ExoPlayer، وتمييز الكلمة حقيقي فقط
    // عند وصول segments من QF (العفاسي + مفاتيح)، وإلا تمييز الآية فقط.
    // السرعة والتكرار والقارئ محفوظة محلياً؛ مؤقت النوم للجلسة فقط.
    var selectedReciter by remember {
        mutableStateOf(
            UserPreferencesManager.getMushafAudioReciter(context)
                .takeIf { id -> QuranAudioService.availableReciters.any { it.id == id } }
                ?: "alafasy"
        )
    }
    var audioSpeed by remember { mutableStateOf(UserPreferencesManager.getMushafAudioSpeed(context)) }
    var repeatMode by remember {
        mutableStateOf(
            runCatching { QuranRepeatMode.valueOf(UserPreferencesManager.getMushafAudioRepeat(context)) }
                .getOrDefault(QuranRepeatMode.OFF)
        )
    }
    var isPlayingAudio by remember { mutableStateOf(false) }
    var isLoadingAudio by remember { mutableStateOf(false) }
    var playingPage by remember { mutableStateOf<Int?>(null) }
    var playingSurah by remember { mutableStateOf<Int?>(null) }
    var playingAyahs by remember { mutableStateOf<List<QuranAudioService.QuranTimedAyahAudio>>(emptyList()) }
    var playingAyahIndex by remember { mutableStateOf(-1) }
    var playingTimings by remember { mutableStateOf<List<QuranAudioService.QuranWordTiming>>(emptyList()) }
    var activeWordPosition by remember { mutableStateOf<Int?>(null) }
    var audioMessage by remember { mutableStateOf<String?>(null) }
    var reciterMenuOpen by rememberSaveable { mutableStateOf(false) }
    var speedMenuOpen by rememberSaveable { mutableStateOf(false) }
    var sleepMenuOpen by rememberSaveable { mutableStateOf(false) }
    var downloadMenuOpen by rememberSaveable { mutableStateOf(false) }
    var sleepMinutes by remember { mutableStateOf<Int?>(null) }
    var sleepRemainingText by remember { mutableStateOf<String?>(null) }
    var downloadStatus by remember { mutableStateOf(MushafDownloadStatus.IDLE) }
    var downloadDone by remember { mutableStateOf(0) }
    var downloadTotal by remember { mutableStateOf(0) }
    var downloadBytes by remember { mutableStateOf(0L) }
    var downloadScopeLabel by remember { mutableStateOf<String?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    // ق113: مقبض مهمة التنزيل — بلا مقبض كان الإلغاء مستحيلاً من القارئ
    // نفسه (صنف ق109)؛ والمخرج الوحيد مغادرة الشاشة بلا إخبار.
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    val audioScope = rememberCoroutineScope()

    fun resetAudioPlaybackState(preserveSleepTimer: Boolean = false) {
        isPlayingAudio = false
        playingPage = null
        playingSurah = null
        playingAyahs = emptyList()
        playingAyahIndex = -1
        playingTimings = emptyList()
        activeWordPosition = null
        // ق43: إعادة بناء القائمة أو القفز داخل الجلسة لا يمسّان
        // عدّاد المؤقت الظاهر؛ الإيقاف الصريح وحده يصفّره.
        if (!preserveSleepTimer) {
            sleepMinutes = null
            sleepRemainingText = null
        }
    }

    fun stopAudio(preserveSleepTimer: Boolean = false) {
        if (preserveSleepTimer) QuranAudioPlayer.stopKeepingSleepTimer() else QuranAudioPlayer.stop()
        resetAudioPlaybackState(preserveSleepTimer)
    }

    /** تشغيل قائمة آيات (صفحة أو سورة) مع ربط التمييز والخطأ بالواجهة. */
    fun startPlayback(
        ayahs: List<QuranAudioService.QuranTimedAyahAudio>,
        page: Int?,
        surah: Int?,
        startIndex: Int = 0
    ) {
        // ق188: «تكرار الصفحة» بلا صفحة كذب على التسمية — يحصل من
        // حالة محفوظة (PAGE من جلسة سابقة) مع تشغيل سورة حين تغيب
        // خريطة الصفحات: المشغّل يكرر السورة كلها (REPEAT_MODE_ALL
        // على قائمة السورة) والتسمية تقول صفحة؛ فتُطبَّع الحالة
        // هنا إلى SURAH مع حفظها، فيطابق الشريط المشغّل دائماً.
        if (repeatMode == QuranRepeatMode.PAGE && page == null && surah != null) {
            repeatMode = QuranRepeatMode.SURAH
            UserPreferencesManager.saveMushafAudioRepeat(context, QuranRepeatMode.SURAH.name)
        }
        val safeStart = startIndex.coerceIn(0, (ayahs.size - 1).coerceAtLeast(0))
        playingAyahs = ayahs
        playingAyahIndex = safeStart
        playingTimings = ayahs.getOrNull(safeStart)?.timings ?: emptyList()
        activeWordPosition = null
        QuranAudioPlayer.playAyahs(
            context = context,
            ayahs = ayahs,
            startIndex = safeStart,
            repeatMode = repeatMode,
            speed = audioSpeed,
            page = page,
            surah = surah,
            onAyahChanged = { index ->
                playingAyahIndex = index
                playingTimings = ayahs.getOrNull(index)?.timings ?: emptyList()
                activeWordPosition = null
            },
            onEnded = { resetAudioPlaybackState() },
            onError = { message ->
                resetAudioPlaybackState()
                audioMessage = message
            }
        )
        playingPage = page
        playingSurah = surah
        isPlayingAudio = true
    }

    // ق186: إعادة ربط جلسة التلاوة بعد التدوير — حالة الصوت في
    // الشاشة remember فتموت بالتدوير، بينما المشغّل singleton
    // ينجو ويواصل التشغيل؛ عند أول تركيب تُستعاد القائمة والمؤشر
    // والصفحة/السورة والسرعة والتكرار وحالة التشغيل من لقطته،
    // وتُعاد تسجيل الردود نفسها (تمييز الآية/النهاية/الخطأ) دون
    // إعادة بناء القائمة أو إيقاف الصوت. بلا جلسة جارية (دخول أول
    // أو إيقاف صريح أو نهاية) لا شيء يُستعاد. مؤقت النوم المسلّح
    // يُستعاد عدّاده أيضاً من بقيّته الحيّة فيعيد حلقة العدّ التنازلي
    // ويبقى زر إلغائه عاملاً.
    LaunchedEffect(Unit) {
        if (playingAyahs.isEmpty()) {
            QuranAudioPlayer.currentSession()?.let { session ->
                playingAyahs = session.ayahs
                playingAyahIndex = session.index
                playingTimings = session.ayahs.getOrNull(session.index)?.timings ?: emptyList()
                activeWordPosition = null
                playingPage = session.page
                playingSurah = session.surah
                audioSpeed = session.speed
                repeatMode = session.repeatMode
                isPlayingAudio = session.playing
                QuranAudioPlayer.sleepRemainingMs()?.let { remaining ->
                    sleepMinutes = ((remaining + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
                }
                QuranAudioPlayer.reattachCallbacks(
                    onAyahChanged = { index ->
                        playingAyahIndex = index
                        playingTimings = session.ayahs.getOrNull(index)?.timings ?: emptyList()
                        activeWordPosition = null
                    },
                    onEnded = { resetAudioPlaybackState() },
                    onError = { message ->
                        resetAudioPlaybackState()
                        audioMessage = message
                    }
                )
            }
        }
    }

    fun togglePageAudio() {
        if (isLoadingAudio) return
        if (playingPage != null || playingSurah != null) {
            // استئناف/إيقاف التلاوة الجارية دون إعادة تحميل.
            isPlayingAudio = QuranAudioPlayer.toggle()
            return
        }
        // بلا صفحة معروفة (خريطة الصفحات غير جاهزة): شغّل تلاوة
        // السورة المعروضة كاملة بدل زرٍّ لا يفعل شيئاً — فتلاوة
        // السورة لا تحتاج خريطة الصفحات إطلاقاً.
        if (currentPage == null) {
            audioMessage = null
            isLoadingAudio = true
            audioScope.launch {
                val ayahs = QuranAudioService.getSurahAudioUrls(selectedSurah, selectedReciter)
                isLoadingAudio = false
                if (ayahs.isEmpty()) {
                    audioMessage = "تعذّر تحميل تلاوة هذه السورة."
                    return@launch
                }
                startPlayback(ayahs, page = null, surah = selectedSurah)
            }
            return
        }
        val page = currentPage ?: return
        audioMessage = null
        isLoadingAudio = true
        audioScope.launch {
            // QF (بتوقيت الكلمات) أولاً للعفاسي بمكالمة واحدة؛ وإلا
            // روابط EveryAyah المباشرة بلا توقيت (تمييز الآية فقط).
            val timed = QuranAudioService.getPageAudioWithTimings(context, page, selectedReciter)
            val ayahs: List<QuranAudioService.QuranTimedAyahAudio> = if (timed != null) {
                timed
            } else {
                val urls = QuranAudioService.getPageAudioUrls(context, page, selectedReciter)
                val refs = MushafPageData.getPageRefs(page)
                urls.mapIndexed { index, url ->
                    val (s, a) = refs.getOrNull(index) ?: (selectedSurah to 0)
                    QuranAudioService.QuranTimedAyahAudio(s, a, url)
                }
            }
            isLoadingAudio = false
            if (ayahs.isEmpty()) {
                audioMessage = "تعذّر تحميل تلاوة هذه الصفحة. تحقق من الاتصال وحاول مجدداً."
                return@launch
            }
            startPlayback(ayahs, page = page, surah = null)
        }
    }

    /** تنزيل تلاوة صفحة (آية آية، مع تقدّم حقيقي) عبر مخزن التنزيلات الموحّد. */
    fun downloadPageAudio(page: Int) {
        if (downloadStatus == MushafDownloadStatus.DOWNLOADING) return
        // ق113: حارس المقبض مع حارس الحالة — نقرة مزدوجة قبل تعيين الحالة
        // كانت تطلق حلقتين على الملفات نفسها.
        if (downloadJob?.isActive == true) return
        val refs = MushafPageData.getPageRefs(page)
        if (refs.isEmpty()) {
            downloadError = "تعذّر تحديد آيات الصفحة."
            return
        }
        downloadError = null
        downloadScopeLabel = "صفحة $page"
        downloadStatus = MushafDownloadStatus.DOWNLOADING
        downloadTotal = refs.size
        downloadDone = 0
        downloadBytes = 0L
        downloadJob = audioScope.launch {
            var done = 0
            var bytes = 0L
            try {
                for ((s, a) in refs) {
                    val url = QuranAudioService.getAyahAudioUrl(s, a, selectedReciter)
                    val ok = AudioDownloadHelper.downloadAudio(context, url, "تلاوة صفحة $page — آية $a", subtitle = "تلاوة قرآنية")
                    if (ok) {
                        done++
                        bytes += DownloadsHubManager.audioFileForUrl(context, url)?.length() ?: 0L
                        downloadDone = done
                        downloadBytes = bytes
                    }
                }
                downloadStatus = when {
                    done == refs.size -> MushafDownloadStatus.DONE
                    done > 0 -> MushafDownloadStatus.PARTIAL
                    else -> MushafDownloadStatus.ERROR
                }
                if (downloadStatus == MushafDownloadStatus.ERROR) {
                    downloadError = "تعذّر تنزيل التلاوة — تحقق من الاتصال."
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // ق113: إلغاء صريح (زر الإلغاء) ليس خطأ اتصال — كتلة finally
                // كانت تصنّف الإلغاء ERROR مع «تحقق من الاتصال» المضللة؛
                // والجزئي يبقى جزئياً صادقاً فيُتاح حذفه وإكماله.
                downloadDone = done
                downloadBytes = bytes
                if (done > 0) {
                    downloadStatus = MushafDownloadStatus.PARTIAL
                    downloadError = null
                } else {
                    downloadStatus = MushafDownloadStatus.ERROR
                    downloadError = "تم إلغاء التنزيل — يمكنك إعادة المحاولة لاحقاً."
                }
                throw e
            } finally {
                // إن أُلغي التنزيل (مغادرة الشاشة مثلاً) لا يبقى
                // «جارٍ التنزيل» عالقاً: يُغلق بالحالة الفعلية.
                if (downloadStatus == MushafDownloadStatus.DOWNLOADING) {
                    downloadDone = done
                    downloadBytes = bytes
                    downloadStatus = when {
                        done == refs.size -> MushafDownloadStatus.DONE
                        done > 0 -> MushafDownloadStatus.PARTIAL
                        else -> MushafDownloadStatus.ERROR
                    }
                    if (downloadStatus == MushafDownloadStatus.ERROR) {
                        downloadError = "تعذّر تنزيل التلاوة — تحقق من الاتصال."
                    }
                }
            }
        }
        downloadJob?.invokeOnCompletion { downloadJob = null }
    }

    /** تنزيل تلاوة سورة كاملة (كل آياتها) — التقدّم بعدد الملفات لا بحجم مخمّن. */
    fun downloadSurahAudio(surah: Int) {
        if (downloadStatus == MushafDownloadStatus.DOWNLOADING) return
        // ق113: حارس المقبض مع حارس الحالة (كما في تنزيل الصفحة).
        if (downloadJob?.isActive == true) return
        val count = QuranDataProvider.surahs.firstOrNull { it.id == surah }?.versesCount ?: 0
        if (count < 1) {
            downloadError = "تعذّر تحديد آيات السورة."
            return
        }
        downloadError = null
        downloadScopeLabel = "سورة ${QuranDataProvider.surahNameOf(surah)}"
        downloadStatus = MushafDownloadStatus.DOWNLOADING
        downloadTotal = count
        downloadDone = 0
        downloadBytes = 0L
        downloadJob = audioScope.launch {
            var done = 0
            var bytes = 0L
            try {
                for (ayah in 1..count) {
                    val url = QuranAudioService.getAyahAudioUrl(surah, ayah, selectedReciter)
                    val ok = AudioDownloadHelper.downloadAudio(context, url, "تلاوة سورة ${QuranDataProvider.surahNameOf(surah)} — آية $ayah", subtitle = "تلاوة قرآنية")
                    if (ok) {
                        done++
                        bytes += DownloadsHubManager.audioFileForUrl(context, url)?.length() ?: 0L
                        downloadDone = done
                        downloadBytes = bytes
                    }
                }
                downloadStatus = when {
                    done == count -> MushafDownloadStatus.DONE
                    done > 0 -> MushafDownloadStatus.PARTIAL
                    else -> MushafDownloadStatus.ERROR
                }
                if (downloadStatus == MushafDownloadStatus.ERROR) {
                    downloadError = "تعذّر تنزيل التلاوة — تحقق من الاتصال."
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // ق113: إلغاء صريح ليس خطأ اتصال (كما في تنزيل الصفحة).
                downloadDone = done
                downloadBytes = bytes
                if (done > 0) {
                    downloadStatus = MushafDownloadStatus.PARTIAL
                    downloadError = null
                } else {
                    downloadStatus = MushafDownloadStatus.ERROR
                    downloadError = "تم إلغاء التنزيل — يمكنك إعادة المحاولة لاحقاً."
                }
                throw e
            } finally {
                // إن أُلغي التنزيل (مغادرة الشاشة مثلاً) لا يبقى
                // «جارٍ التنزيل» عالقاً: يُغلق بالحالة الفعلية.
                if (downloadStatus == MushafDownloadStatus.DOWNLOADING) {
                    downloadDone = done
                    downloadBytes = bytes
                    downloadStatus = when {
                        done == count -> MushafDownloadStatus.DONE
                        done > 0 -> MushafDownloadStatus.PARTIAL
                        else -> MushafDownloadStatus.ERROR
                    }
                    if (downloadStatus == MushafDownloadStatus.ERROR) {
                        downloadError = "تعذّر تنزيل التلاوة — تحقق من الاتصال."
                    }
                }
            }
        }
        downloadJob?.invokeOnCompletion { downloadJob = null }
    }

    /** حذف ملفات تلاوة الصفحة المنزّلة من مخزن التنزيلات الموحّد. */
    fun deletePageAudio(page: Int) {
        val refs = MushafPageData.getPageRefs(page)
        // ق150: الحذف كان لقارئ واحد فقط (الحالي) رغم أن عنصر القائمة
        // «حذف تنزيل الصفحة» بلا اسم قارئ — فمن نزّل بالقارئ أ ثم بدّل
        // إلى ب وحذف، لم يُحذف شيء وبقيت الحالة IDLE كاذبة (ملفات أ
        // موجودة وتشغل التخزين وتُستعمل دون اتصال) حتى إعادة الحساب.
        // التنزيل لكل قارئ على حدة، والحذف لكل القراء معاً فيُصدق.
        for (reciter in QuranAudioService.availableReciters) {
            for ((s, a) in refs) {
                val url = QuranAudioService.getAyahAudioUrl(s, a, reciter.id)
                DownloadsHubManager.deleteDownloadedMedia(context, DownloadsHubManager.audioMediaIdForUrl(url))
            }
        }
        downloadStatus = MushafDownloadStatus.IDLE
        downloadDone = 0
        downloadTotal = 0
        downloadBytes = 0L
        downloadScopeLabel = null
        downloadError = null
    }

    // آخر موضع معروف — يُقرأ في onDispose حتى لو غادر المستخدم قبل المهلة.
    val latestPosition = remember { mutableStateOf<UserPreferencesManager.LastReadQuran?>(null) }

    fun pageFor(surah: Int, ayah: Int): Int? =
        if (pageMapReady) {
            MushafPageData.getPageForAyah(surah, ayah).takeIf { it in 1..MushafPageData.PAGE_COUNT }
        } else {
            null
        }

    fun recordPosition(surah: Int, ayah: Int) {
        val page = pageFor(surah, ayah)
        val position = UserPreferencesManager.LastReadQuran(
            surah = surah,
            ayah = ayah,
            page = page,
            atMillis = System.currentTimeMillis()
        )
        latestPosition.value = position
        if (page != null) currentPage = page
        UserPreferencesManager.saveLastReadQuran(context, surah, ayah, page)
    }

    // --- علامات المصحف + الانتقال السريع (خارطة المصحف — المرحلة 1) ---
    // العلامات محلية فقط (UserPreferencesManager) — بلا مزامنة سحابية.
    var bookmarks by remember { mutableStateOf<List<UserPreferencesManager.MushafBookmark>>(emptyList()) }
    // ق77: فهرس العلامات بالمرجع — كان كل بطاقة آية تفحص قائمة
    // العلامات كاملة (firstOrNull) في كل تركيب، فصار البحث خريطة O(1)
    // تُبنى مرة واحدة عند تغيّر العلامات فقط.
    val bookmarkByRef = remember(bookmarks) {
        bookmarks.associateBy { it.surah to it.ayah }
    }
    // ق105: فتح حوار الانتقال محفوظ عبر التدوير مع استعلاماته.
    var navDialogOpen by rememberSaveable { mutableStateOf(false) }
    var bookmarksDialogOpen by remember { mutableStateOf(false) }
    var editingBookmark by remember { mutableStateOf<UserPreferencesManager.MushafBookmark?>(null) }
    // ق87: علامة بانتظار تأكيد الحذف — الحذف كان فورياً بلا تأكيد ولا
    // تراجع من القائمة والمحرر معاً، فأي نقرة سهو على أيقونة الحذف
    // (بجوار أيقونة التعديل مباشرة) كانت تُفقد الملاحظة نهائياً.
    var bookmarkPendingDelete by remember { mutableStateOf<UserPreferencesManager.MushafBookmark?>(null) }

    // --- الدراسة (خارطة المصحف — المرحلة 3) ---
    // الترجمة الإنجليزية (Saheeh International من QuranEnc) تحت كل آية،
    // تُجلب مرة لكل سورة وتُحفظ في ذاكرة QuranDataProvider؛ ولوحة الدراسة
    // تفتح بنقرة على أي آية. بلا اتصال: الترجمة والكلمات غير متاحتين
    // (رسالة صادقة)، أما التفسير الميسر فمن ملف محلي فيعمل دون اتصال.
    var showTranslation by remember { mutableStateOf(UserPreferencesManager.getMushafShowTranslation(context)) }
    // الترجمة الإنجليزية المختارة (قسم القرآن — الكتب): محفوظة محلياً،
    // وتبديلها يعيد جلب الترجمة السطرية بالمعرّف الجديد.
    var mushafTranslationId by remember { mutableStateOf(UserPreferencesManager.getMushafTranslation(context)) }
    // ق96: اسم الترجمة النشطة للافتتي التحميل والخطأ — كان النص
    // عاماً («الترجمة الإنجليزية») فلا يعرف القارئ أي ترجمة تُجلب
    // أو فشل جلبها عند التبديل بين الثلاث.
    val activeTranslationName = remember(mushafTranslationId) {
        QuranDataProvider.MUSHAF_TRANSLATIONS.firstOrNull { it.id == mushafTranslationId }?.arabicName
            ?: QuranDataProvider.MUSHAF_TRANSLATIONS[0].arabicName
    }
    var translationMaps by remember { mutableStateOf<Map<Int, Map<Int, String>>>(emptyMap()) }
    var translationLoading by remember { mutableStateOf(false) }
    var translationError by remember { mutableStateOf<String?>(null) }
    var translationRetryTick by remember { mutableStateOf(0) }
    var studyVerse by rememberSaveable(saver = mushafStudyVerseSaver) {
        mutableStateOf<QuranVerseDetail?>(null)
    }
    // آية معلّقة للدراسة من ورقة المكتبة: حين تكون سورتها غير
    // السورة المعروضة، ننتقل إليها أولاً ثم نفتح لوحتها عند
    // وصول آياتها — لا نفتح دراسة آية أخرى برقمها نفسه.
    var pendingStudyVerse by rememberSaveable(saver = mushafPendingStudyVerseSaver) {
        mutableStateOf<Pair<Int, Int>?>(null)
    }
    // كتاب الإعراب المفروض عند فتح الدراسة من فئة البلاغة في المكتبة
    // (الدرويش، لعرض قسم البلاغة)؛ يُمسح عند إغلاق اللوحة.
    var studyIrabBookOverride by rememberSaveable { mutableStateOf<String?>(null) }

    // --- الحفظ والختمة (خارطة المصحف — المرحلة 4) ---
    // وضع الحفظ إخفاء عرض فقط (النص لا يُغيَّر أبداً)؛ والآيات
    // المكشوفة في الجلسة مجموعة في الذاكرة تُصفَّر عند إيقاف الوضع.
    // خطة الختمة محلية، وتقدّمها من موضع القراءة الفعلي وحده —
    // لا عدّاد مستقل ولا تقدّم يُسجَّل يدوياً.
    var memorizationMode by remember {
        mutableStateOf(UserPreferencesManager.getMushafMemorizationMode(context))
    }
    var revealedVerses by rememberSaveable(saver = mushafRevealedVersesSaver) {
        mutableStateOf<Set<Pair<Int, Int>>>(emptySet())
    }
    var khatmahDialogOpen by remember { mutableStateOf(false) }
    var khatmahPlan by remember { mutableStateOf(UserPreferencesManager.getMushafKhatmahPlan(context)) }
    var khatmahLastRead by remember { mutableStateOf<UserPreferencesManager.LastReadQuran?>(null) }

    // --- عرض المصحف (خارطة المصحف — المرحلة 5) ---
    // التجويد والرسم البديل يُجلبان لكل سورة من QF العام وتُحفظان في
    // ذاكرة QuranDataProvider؛ بلا اتصال يبقى النص المحلي مع رسالة
    // صادقة. لا رواية أخرى: النص المحلي وكل ما فُحص حفص وحده.
    var showTajweed by remember { mutableStateOf(UserPreferencesManager.getMushafTajweed(context)) }
    var mushafScript by remember { mutableStateOf(UserPreferencesManager.getMushafScript(context)) }
    var twoPageSpread by remember { mutableStateOf(UserPreferencesManager.getMushafTwoPageSpread(context)) }
    var displaySettingsOpen by remember { mutableStateOf(false) }
    var tajweedLegendOpen by remember { mutableStateOf(false) }
    var booksLibraryOpen by remember { mutableStateOf(false) }
    var tajweedMaps by remember { mutableStateOf<Map<Int, Map<Int, String>>>(emptyMap()) }
    var tajweedLoading by remember { mutableStateOf(false) }
    var tajweedError by remember { mutableStateOf<String?>(null) }
    var tajweedRetryTick by remember { mutableStateOf(0) }
    var scriptMaps by remember { mutableStateOf<Map<Int, Map<Int, String>>>(emptyMap()) }
    var scriptLoading by remember { mutableStateOf(false) }
    var scriptError by remember { mutableStateOf<String?>(null) }
    var scriptRetryTick by remember { mutableStateOf(0) }

    // --- الصفحتان (خارطة المصحف — المرحلة 5) ---
    // يعمل في الوضع الأفقي فقط: عمودان لصفحتين متتاليتين من خريطة
    // المصحف المدني (صفحة فردية يميناً والتالية يساراً في RTL)،
    // والقفز بفارق صفحتين، والتلاوة وآخر قراءة تتبعان الظاهر.
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val spreadActive = twoPageSpread && isLandscape
    var spreadPage by remember { mutableStateOf<Int?>(null) }
    var spreadTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val firstPageState = rememberLazyListState()
    val secondPageState = rememberLazyListState()
    val spreadRefs = remember(spreadPage, pageMapReady) {
        val start = spreadPage ?: return@remember Pair(
            emptyList<Pair<Int, Int>>(),
            emptyList<Pair<Int, Int>>()
        )
        MushafPageData.getPageRefs(start) to MushafPageData.getPageRefs(start + 1)
    }
    val spreadSurahs = remember(spreadRefs) {
        (spreadRefs.first + spreadRefs.second).map { it.first }.distinct().toSet()
    }
    // ق77: تفاصيل الصفحتين تُبنى من مراجع الصفحتين فقط (~15 آية)
    // عبر getVerseText/getTafsirForVerse — لا من سور كاملة: كان
    // getVersesForSurah يرتّب ويبني 286 كائناً للبقرة مثلاً في كل
    // قلب صفحة بينما المعروض 15 آية فقط.
    val spreadDetails = remember(spreadRefs, quranReady) {
        if (!quranReady) {
            emptyMap<Pair<Int, Int>, QuranVerseDetail>()
        } else {
            val map = LinkedHashMap<Pair<Int, Int>, QuranVerseDetail>()
            for (ref in spreadRefs.first + spreadRefs.second) {
                val text = QuranDataProvider.getVerseText(ref.first, ref.second) ?: continue
                map[ref] = QuranVerseDetail(
                    surahNumber = ref.first,
                    surahName = QuranDataProvider.surahNameOf(ref.first),
                    verseNumber = ref.second,
                    verseText = text,
                    tafseer = QuranDataProvider.getTafsirForVerse(ref.first, ref.second) ?: "",
                    tajweedNotes = ""
                )
            }
            map
        }
    }
    // السور المعروضة الآن: الحالية في العرض العادي، وسورتا الصفحتين
    // (مع الحالية) في العرض المزدوج — تجلب لها الترجمة والتجويد والرسم.
    val displaySurahs = remember(selectedSurah, spreadActive, spreadSurahs) {
        if (spreadActive) spreadSurahs + selectedSurah else setOf(selectedSurah)
    }

    // جلب ترجمة السور المعروضة عند تفعيل العرض أو تبديلها أو تبديل
    // الترجمة المختارة أو إعادة المحاولة.
    LaunchedEffect(showTranslation, mushafTranslationId, displaySurahs, translationRetryTick) {
        if (!showTranslation) {
            translationMaps = emptyMap()
            translationError = null
            translationLoading = false
            return@LaunchedEffect
        }
        val ready = LinkedHashMap<Int, Map<Int, String>>()
        val missing = ArrayList<Int>()
        for (surah in displaySurahs) {
            val cached = QuranDataProvider.getCachedSurahTranslationFor(mushafTranslationId, surah)
            if (cached != null) ready[surah] = cached else missing += surah
        }
        if (missing.isEmpty()) {
            translationMaps = ready
            translationError = null
            translationLoading = false
            return@LaunchedEffect
        }
        // ق96: تُنشر الخرائط المخزنة فوراً قبل الجلب — كان نص
        // الترجمة القديمة (لمعرّف آخر لنفس السور) يبقى معروضاً
        // أثناء جلب الجديدة فيُقرأ كأنه الترجمة المختارة.
        translationMaps = ready
        translationLoading = true
        translationError = null
        var failed = false
        for (surah in missing) {
            val fetched = QuranDataProvider.fetchSurahTranslationFor(mushafTranslationId, surah)
            if (fetched != null) ready[surah] = fetched else failed = true
        }
        translationMaps = ready
        translationError = if (failed) {
            "تعذّر تحميل ترجمة $activeTranslationName — تحقق من الاتصال وحاول مجدداً."
        } else {
            null
        }
        translationLoading = false
    }

    // جلب نص التجويد عند تفعيله (الرسم العثماني فقط) أو تبديل السور المعروضة.
    LaunchedEffect(showTajweed, mushafScript, displaySurahs, tajweedRetryTick) {
        if (!showTajweed || mushafScript != "uthmani") {
            tajweedMaps = emptyMap()
            tajweedError = null
            tajweedLoading = false
            return@LaunchedEffect
        }
        val ready = LinkedHashMap<Int, Map<Int, String>>()
        val missing = ArrayList<Int>()
        for (surah in displaySurahs) {
            val cached = QuranDataProvider.getCachedSurahTajweed(surah)
            if (cached != null) ready[surah] = cached else missing += surah
        }
        if (missing.isEmpty()) {
            tajweedMaps = ready
            tajweedError = null
            tajweedLoading = false
            return@LaunchedEffect
        }
        tajweedLoading = true
        tajweedError = null
        var failed = false
        for (surah in missing) {
            val fetched = QuranDataProvider.fetchSurahTajweed(surah)
            if (fetched != null) ready[surah] = fetched else failed = true
        }
        tajweedMaps = ready
        tajweedError = if (failed) {
            "تعذّر تحميل ألوان التجويد — تحقق من الاتصال وحاول مجدداً."
        } else {
            null
        }
        tajweedLoading = false
    }

    // جلب نص الرسم البديل عند اختياره أو تبديل السور المعروضة.
    LaunchedEffect(mushafScript, displaySurahs, scriptRetryTick) {
        if (mushafScript == "uthmani") {
            scriptMaps = emptyMap()
            scriptError = null
            scriptLoading = false
            return@LaunchedEffect
        }
        val ready = LinkedHashMap<Int, Map<Int, String>>()
        val missing = ArrayList<Int>()
        for (surah in displaySurahs) {
            val cached = QuranDataProvider.getCachedSurahScript(mushafScript, surah)
            if (cached != null) ready[surah] = cached else missing += surah
        }
        if (missing.isEmpty()) {
            scriptMaps = ready
            scriptError = null
            scriptLoading = false
            return@LaunchedEffect
        }
        // ق96: كالترجمة — تُنشر الخرائط المخزنة فوراً قبل الجلب،
        // وإلا بقي نص الرسم القديم معروضاً كأنه الرسم المختار.
        scriptMaps = ready
        scriptLoading = true
        scriptError = null
        var failed = false
        for (surah in missing) {
            val fetched = QuranDataProvider.fetchSurahScript(mushafScript, surah)
            if (fetched != null) ready[surah] = fetched else failed = true
        }
        scriptMaps = ready
        scriptError = if (failed) {
            "تعذّر تحميل نص الرسم البديل — تحقق من الاتصال وحاول مجدداً."
        } else {
            null
        }
        scriptLoading = false
    }

    fun refreshBookmarks() {
        bookmarks = UserPreferencesManager.getMushafBookmarks(context)
    }

    // قفزة واحدة لكل مسارات الانتقال (بحث، جزء، صفحة، علامة): توقف التلاوة
    // وتفتح السورة عند الآية المطلوبة عبر آلية targetAyah نفسها.
    fun jumpTo(surah: Int, ayah: Int) {
        if (surah !in 1..114 || ayah < 1) return
        // قيد رقم الآية بعدد آيات سورتها الحقيقي: آية خارج النطاق كانت
        // تترك القارئ أعلى السورة بصمت — بلا قفزة ولا رسالة.
        val safeAyah = QuranDataProvider.surahs.firstOrNull { it.id == surah }
            ?.versesCount?.let { ayah.coerceIn(1, it) } ?: ayah
        // ق43: القفز داخل الجلسة لا يلغي مؤقت نوم مسلّحاً — كان
        // stopAudio() هنا يلغيه بصمت، فمن يعيد التشغيل بعد القفز
        // يكمل بلا مؤقت ولا عدّاد ظاهر.
        stopAudio(preserveSleepTimer = true)
        audioMessage = null
        selectedSurah = surah
        targetAyah = safeAyah
        targetConsumed = false
        // في العرض المزدوج: التقط الصفحة الفردية المحيطة بالآية،
        // وأخبر العمود الحاوي أن يتمركز عليها.
        if (spreadActive) {
            val page = MushafPageData.getPageForAyah(surah, safeAyah)
            if (page > 0) spreadPage = mushafSpreadStartPage(page)
            spreadTarget = surah to safeAyah
        }
    }

    /** تلاوة السورة كاملة من آيتها الأولى (لتكرار السورة)، مع قفزة القارئ إليها. */
    fun startSurahAudio(surah: Int) {
        if (isLoadingAudio) return
        // ق43: إعادة بناء لا إيقاف صريح — مؤقت النوم ينجو مع الجلسة.
        stopAudio(preserveSleepTimer = true)
        audioMessage = null
        isLoadingAudio = true
        audioScope.launch {
            val ayahs = QuranAudioService.getSurahAudioUrls(surah, selectedReciter)
            isLoadingAudio = false
            if (ayahs.isEmpty()) {
                audioMessage = "تعذّر تحميل تلاوة هذه السورة."
                return@launch
            }
            jumpTo(surah, 1)
            startPlayback(ayahs, page = null, surah = surah)
        }
    }

    /** تبديل وضع التكرار؛ الصفحة/السورة تعيد بناء قائمة التشغيل المناسبة. */
    fun cycleRepeatMode() {
        // ق43: أثناء تحميل قائمة تُبنى يُتجاهل التدوير — بلا حارس كان
        // تدوير سريع يطلق إعادة بناء متداخلة فيفوز آخرها بقائمة لا
        // تطابق التسمية المعروضة (صفحة تُعاد بتسمية «تكرار السورة»).
        if (isLoadingAudio) return
        // ق188: بلا صفحة معروفة (خريطة الصفحات غائبة فيُشغَّل صوت
        // السورة) كانت خطوة «تكرار الصفحة» تعيّن الحالة والتسمية
        // وتُحفظ، بينما فرع PAGE لا يجد صفحة فلا يستدعي setRepeatMode
        // ولا يعيد البناء — فيبقى المشغّل على وضع الآية السابق
        // والتسمية كاذبة؛ فتُتخطى خطوة الصفحة في التدوير حين لا
        // صفحة: إيقاف ← آية ← سورة ← إيقاف.
        val hasPage = (playingPage ?: currentPage) != null
        val next = when (repeatMode) {
            QuranRepeatMode.OFF -> QuranRepeatMode.VERSE
            QuranRepeatMode.VERSE -> if (hasPage) QuranRepeatMode.PAGE else QuranRepeatMode.SURAH
            QuranRepeatMode.PAGE -> QuranRepeatMode.SURAH
            QuranRepeatMode.SURAH -> QuranRepeatMode.OFF
        }
        repeatMode = next
        UserPreferencesManager.saveMushafAudioRepeat(context, next.name)
        when {
            next == QuranRepeatMode.VERSE || next == QuranRepeatMode.OFF ->
                QuranAudioPlayer.setRepeatMode(next)
            next == QuranRepeatMode.PAGE -> {
                val page = playingPage ?: currentPage
                if (page != null && playingSurah == null) {
                    QuranAudioPlayer.setRepeatMode(next)
                } else if (page != null) {
                    // قائمة سورة جارية: أعد البناء كصفحة لتكرار الصفحة فعلاً.
                    // ق43: الإيقاف هنا إعادة بناء لا إيقاف صريح — مؤقت
                    // النوم ينجو مع الجلسة.
                    stopAudio(preserveSleepTimer = true)
                    audioMessage = null
                    isLoadingAudio = true
                    audioScope.launch {
                        val urls = QuranAudioService.getPageAudioUrls(context, page, selectedReciter)
                        val refs = MushafPageData.getPageRefs(page)
                        val ayahs = urls.mapIndexed { index, url ->
                            val (s, a) = refs.getOrNull(index) ?: (selectedSurah to 0)
                            QuranAudioService.QuranTimedAyahAudio(s, a, url)
                        }
                        isLoadingAudio = false
                        if (ayahs.isNotEmpty()) startPlayback(ayahs, page = page, surah = null)
                    }
                }
            }
            next == QuranRepeatMode.SURAH -> startSurahAudio(selectedSurah)
        }
    }

    // تحميل النص وخريطة الصفحات، ثم تحديد موضع البداية (خارطة المصحف —
    // المرحلة 1: الاستئناف التلقائي مكتمل): الآية المحددة أدق من الصفحة،
    // فالترتيب: 1) سورة/آية محددتان (من نتيجة بحث أو «أكمل») → تلك الآية
    // تماماً. 2) صفحة محددة بلا آية (من البحث) → أول آية في تلك الصفحة.
    // 3) سورة محددة بلا آية → أول آياتها. 4) آخر قراءة محفوظة (آيتها
    // مقيّدة بعدد آيات سورتها الحقيقي). 5) الفاتحة.
    // (قبل هذا الإصلاح كان الصفحة تتقدم على الآية، فبطاقة «أكمل» التي
    // تمرر السورة والآية والصفحة معاً كانت تفتح أول آية في الصفحة لا
    // الآية المحفوظة نفسها؛ وكانت سورة بلا آية تُهمل وتفتح آخر قراءة.)
    LaunchedEffect(quranLoadAttempt) {
        refreshBookmarks()
        quranLoadFailed = false
        quranReady = QuranDataProvider.ensureQuranLoaded(context)
        quranLoadFailed = !quranReady
        QuranDataProvider.loadTafsirFromAssets(context)
        pageMapReady = MushafPageData.loadPageMap(context)
        // اشتقاق موضع البدء مرة واحدة فقط: إعادة المحاولة بعد فشل يجب ألا
        // تعيد المستخدم إلى السورة الابتدائية بعد أن تنقّل منها.
        if (quranLoadAttempt == 0) {
        val validInitialSurah = initialSurah?.takeIf { it in 1..114 }
        val maxAyahOf = { surah: Int ->
            QuranDataProvider.surahs.firstOrNull { it.id == surah }?.versesCount
        }
        val start: Pair<Int, Int>? = when {
            validInitialSurah != null && initialAyah != null ->
                validInitialSurah to initialAyah.coerceIn(1, maxAyahOf(validInitialSurah) ?: initialAyah)
            initialPage != null && pageMapReady -> {
                MushafPageData.getPageRefs(initialPage).firstOrNull()
            }
            validInitialSurah != null -> validInitialSurah to 1
            else -> UserPreferencesManager.getLastReadQuran(context)
                ?.let { saved ->
                    saved.surah to saved.ayah.coerceIn(1, maxAyahOf(saved.surah) ?: saved.ayah)
                }
        }
        if (start != null) {
            selectedSurah = start.first
            targetAyah = start.second
            latestPosition.value = UserPreferencesManager.LastReadQuran(
                surah = start.first,
                ayah = start.second,
                page = pageFor(start.first, start.second),
                atMillis = System.currentTimeMillis()
            )
            currentPage = latestPosition.value?.page
        }
        }
    }

    val verses = remember(selectedSurah, quranReady, quranLoadAttempt) {
        if (!quranReady) {
            emptyList()
        } else {
            QuranDataProvider.getVersesForSurah(
                selectedSurah,
                QuranDataProvider.surahNameOf(selectedSurah)
            )
        }
    }
    val surahName = QuranDataProvider.surahNameOf(selectedSurah)
    val listState = rememberLazyListState()

    // قفزة لمرة واحدة إلى آية البداية بعد تحميل آيات السورة.
    LaunchedEffect(verses, targetAyah, targetConsumed) {
        if (targetConsumed || targetAyah == null || verses.isEmpty()) return@LaunchedEffect
        val index = verses.indexOfFirst { it.verseNumber == targetAyah }
        if (index >= 0) {
            listState.scrollToItem(index)
            recordPosition(selectedSurah, targetAyah!!)
        }
        targetConsumed = true
    }

    // فتح دراسة الآية المعلّقة من ورقة المكتبة حال وصول آيات سورتها.
    LaunchedEffect(verses, pendingStudyVerse) {
        val pending = pendingStudyVerse ?: return@LaunchedEffect
        if (verses.isEmpty()) return@LaunchedEffect
        val detail = verses.firstOrNull {
            it.surahNumber == pending.first && it.verseNumber == pending.second
        }
        if (detail != null) {
            pendingStudyVerse = null
            studyVerse = detail
        }
    }

    // تتبّع أول آية ظاهرة مع مهلة — لا حفظ عند كل إطار تمرير.
    // (في العرض المزدوج يتعطل هذا الأثر؛ أثر التتبع المزدوج أدنى يقوم به.)
    LaunchedEffect(listState, selectedSurah, verses, spreadActive) {
        if (spreadActive) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .debounce(800L)
            .collect { index ->
                verses.getOrNull(index)?.let { verse ->
                    recordPosition(selectedSurah, verse.verseNumber)
                }
            }
    }

    // --- مزامنة العرض المزدوج (خارطة المصحف — المرحلة 5) ---
    // تهيئة صفحة البدء عند تفعيل العرض، وإلغاؤها عند إيقافه.
    LaunchedEffect(spreadActive) {
        if (spreadActive) {
            if (spreadPage == null) {
                spreadPage = mushafSpreadStartPage(currentPage ?: 1)
            }
        } else {
            spreadPage = null
            spreadTarget = null
        }
    }

    // إعادة التقط عند عبور الصفحة الحالية حدود الصفحتين الظاهرتين.
    LaunchedEffect(spreadActive, currentPage) {
        if (!spreadActive) return@LaunchedEffect
        val page = currentPage ?: return@LaunchedEffect
        val start = spreadPage ?: return@LaunchedEffect
        if (page !in start..start + 1) {
            spreadPage = mushafSpreadStartPage(page)
        }
    }

    // إعادة العمودين إلى الأعلى عند تبديل الصفحتين — قبل قفزة الهدف
    // حتى تفوز قفزة الهدف إن وُجدت.
    LaunchedEffect(spreadPage) {
        if (!spreadActive) return@LaunchedEffect
        firstPageState.scrollToItem(0)
        secondPageState.scrollToItem(0)
    }

    // قفزة الهدف في العرض المزدوج: تمركز العمود الحاوي على الآية
    // (مع إزاحة الترويسة بمقدار 1) ثم نسيان الهدف.
    LaunchedEffect(spreadTarget, spreadRefs) {
        val target = spreadTarget ?: return@LaunchedEffect
        if (!spreadActive) {
            spreadTarget = null
            return@LaunchedEffect
        }
        val firstIndex = spreadRefs.first.indexOf(target)
        if (firstIndex >= 0) {
            firstPageState.scrollToItem(firstIndex + 1)
        } else {
            val secondIndex = spreadRefs.second.indexOf(target)
            if (secondIndex >= 0) {
                secondPageState.scrollToItem(secondIndex + 1)
            }
        }
        spreadTarget = null
    }

    // تتبّع آخر قراءة في العرض المزدوج: أول آية ظاهرة في أي عمود.
    LaunchedEffect(spreadActive, spreadPage, spreadRefs) {
        if (!spreadActive) return@LaunchedEffect
        snapshotFlow {
            val first = spreadRefs.first.getOrNull(
                (firstPageState.firstVisibleItemIndex - 1).coerceAtLeast(0)
            )
            val second = spreadRefs.second.getOrNull(
                (secondPageState.firstVisibleItemIndex - 1).coerceAtLeast(0)
            )
            listOfNotNull(first, second).minByOrNull { (s, a) ->
                UserPreferencesManager.globalAyahIndex(s, a)
            }
        }
            .distinctUntilChanged()
            .debounce(800L)
            .collect { earliest ->
                if (earliest != null) recordPosition(earliest.first, earliest.second)
            }
    }

    // متابعة التلاوة في العرض المزدوج: إن خرجت الآية الجارية من
    // الصفحتين الظاهرتين التقط صفحتها، وإلا مرّر عمودها إليها.
    LaunchedEffect(playingAyahIndex, playingAyahs, spreadActive) {
        if (!spreadActive) return@LaunchedEffect
        if (playingAyahIndex < 0) return@LaunchedEffect
        val ref = playingAyahs.getOrNull(playingAyahIndex) ?: return@LaunchedEffect
        val start = spreadPage ?: return@LaunchedEffect
        val page = MushafPageData.getPageForAyah(ref.surah, ref.ayah)
        if (page <= 0) return@LaunchedEffect
        if (page !in start..start + 1) {
            spreadPage = mushafSpreadStartPage(page)
            spreadTarget = ref.surah to ref.ayah
            return@LaunchedEffect
        }
        val key = ref.surah to ref.ayah
        val firstIndex = spreadRefs.first.indexOf(key)
        if (firstIndex >= 0) {
            val visible = firstPageState.layoutInfo.visibleItemsInfo.map { it.index }
            if (firstIndex + 1 !in visible) firstPageState.scrollToItem(firstIndex + 1)
        } else {
            val secondIndex = spreadRefs.second.indexOf(key)
            if (secondIndex >= 0) {
                val visible = secondPageState.layoutInfo.visibleItemsInfo.map { it.index }
                if (secondIndex + 1 !in visible) secondPageState.scrollToItem(secondIndex + 1)
            }
        }
    }

    // تمرير تلقائي للآية الجارية أثناء التلاوة — فقط إن خرجت من الشاشة،
    // فلا يصارع تمرير المستخدم بيده.
    // (في العرض المزدوج يتعطل؛ أثر المتابعة المزدوج أعلاه يقوم به.)
    LaunchedEffect(playingAyahIndex, playingAyahs, spreadActive) {
        if (spreadActive) return@LaunchedEffect
        if (playingAyahIndex < 0) return@LaunchedEffect
        val ref = playingAyahs.getOrNull(playingAyahIndex) ?: return@LaunchedEffect
        if (ref.surah != selectedSurah) return@LaunchedEffect
        val verseIndex = verses.indexOfFirst { it.verseNumber == ref.ayah }
        if (verseIndex < 0) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }
        if (verseIndex !in visible) {
            listState.scrollToItem(verseIndex)
        }
    }

    // تمييز الكلمة أثناء التشغيل (المرحلة 2): قراءة موضع ExoPlayer كل
    // 120ms ومطابقته مع توقيت الكلمات الحقيقي للآية الجارية — فقط
    // عندما يكون عدد المقاطع مساوياً لعدد كلمات النص المعروض، وإلا
    // يبقى تمييز الآية وحده. لا تخمين زمني ولا مزامنة وهمية.
    LaunchedEffect(isPlayingAudio, playingAyahIndex) {
        while (isPlayingAudio && playingAyahIndex >= 0) {
            val timings = playingTimings
            val ref = playingAyahs.getOrNull(playingAyahIndex)
            if (timings.isNotEmpty() && ref != null && (ref.surah == selectedSurah || spreadActive)) {
                // في العرض المزدوج قد تكون الآية الجارية في سورة العمود
                // الآخر؛ كلماتها تؤخذ من تفاصيل الصفحتين لا من السورة المختارة.
                // نص الكلمات المطابَق هو النص المعروض نفسه (بطاقة
                // الآية): نص التجويد الملوّن عند تفعيله، وإلا الرسم
                // البديل، وإلا النص المحلي — لا النص المحلي دائماً،
                // فعدّ كلماته قد يخالف عدّ كلمات نص التجويد فيُطفئ
                // تمييز الكلمة بصمت كلما فُعّلت ألوان التجويد.
                val baseDetail = if (spreadActive) {
                    spreadDetails[ref.surah to ref.ayah]
                } else {
                    verses.firstOrNull { it.verseNumber == ref.ayah }
                }
                val displayedText = when {
                    baseDetail == null -> null
                    showTajweed && mushafScript == "uthmani" -> tajweedMaps[ref.surah]?.get(ref.ayah)
                        ?.let { markup ->
                            QuranDataProvider.parseTajweedMarkup(markup)
                                .joinToString("") { it.text }
                        } ?: baseDetail.verseText
                    mushafScript != "uthmani" -> scriptMaps[ref.surah]?.get(ref.ayah)
                        ?: baseDetail.verseText
                    else -> baseDetail.verseText
                }
                val tokens = displayedText?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
                activeWordPosition = if (timings.size == tokens.size) {
                    val pos = QuranAudioPlayer.currentPositionMs()
                    timings.firstOrNull { pos in it.startMs until it.endMs }?.position
                } else {
                    null
                }
            } else {
                activeWordPosition = null
            }
            delay(120L)
        }
        if (!isPlayingAudio) activeWordPosition = null
    }

    // عدّاد مؤقت النوم التنازلي (عرض فقط — الإيقاف نفسه في المشغّل).
    LaunchedEffect(sleepMinutes) {
        while (sleepMinutes != null) {
            val remaining = QuranAudioPlayer.sleepRemainingMs()
            if (remaining == null || remaining <= 0L) {
                sleepMinutes = null
                sleepRemainingText = null
                break
            }
            val minutes = remaining / 60_000L
            val seconds = (remaining % 60_000L) / 1000L
            sleepRemainingText = "مؤقت النوم: $minutes:${"%02d".format(java.util.Locale.US, seconds)}"
            delay(1000L)
        }
    }

    // حالة تنزيل الصفحة الحالية (من وجود الملفات فعلاً في المخزن الموحّد).
    // ق98: مفاتيح الأثر الصفحة والقارئ وحدهما — كانت حالة التنزيل
    // نفسها مفتاحاً فأعاد الأثر تصنيفها فور تعيينها: الجزئي والخطأ
    // كانا يُمحيان إلى IDLE لحظة تعيينهما فمات فرعا «منزّلة جزئياً»
    // و«تعذّر التنزيل» في اللافتة، والتصنيف ثنائي فلا جزئي أصلاً.
    // دوال التنزيل/الحذف تعيّن الحالة النهائية بنفسها فلا يحتاج
    // الأثر لإعادة التشغيل عند تغيّرها؛ وحارس التنزيل الجاري يبقى
    // فلا يطمس الأثر حالة DOWNLOADING عند تبديل الصفحة أثنائه.
    LaunchedEffect(currentPage, selectedReciter) {
        if (downloadStatus == MushafDownloadStatus.DOWNLOADING) return@LaunchedEffect
        val page = currentPage ?: return@LaunchedEffect
        val refs = MushafPageData.getPageRefs(page)
        if (refs.isEmpty()) return@LaunchedEffect
        var done = 0
        var bytes = 0L
        for ((s, a) in refs) {
            val url = QuranAudioService.getAyahAudioUrl(s, a, selectedReciter)
            DownloadsHubManager.audioFileForUrl(context, url)?.let {
                done++
                bytes += it.length()
            }
        }
        downloadDone = done
        downloadTotal = refs.size
        downloadBytes = bytes
        downloadStatus = when {
            done == refs.size -> MushafDownloadStatus.DONE
            done > 0 -> MushafDownloadStatus.PARTIAL
            else -> MushafDownloadStatus.IDLE
        }
    }

    // حفظ آخر موضع عند مغادرة الشاشة (رجوع أو تنقّل) + إيقاف التلاوة.
    DisposableEffect(Unit) {
        onDispose {
            // ق186: التدوير يعيد إنشاء النشاط فيُستدعى onDispose أيضاً —
            // إيقاف المشغّل هنا كان يقتل التلاوة الجارية عند كل تدوير؛
            // يُتخطى الإيقاف عند تغيير الإعدادات فتكمل التلاوة وتُعاد
            // ربط حالتها من لقطة المشغّل عند أول تركيب (أعلاه)، بينما
            // المغادرة الحقيقية ما زالت توقفه. حفظ آخر موضع يبقى دائماً.
            if ((context as? Activity)?.isChangingConfigurations != true) {
                QuranAudioPlayer.stop()
            }
            latestPosition.value?.let { position ->
                UserPreferencesManager.saveLastReadQuran(
                    context,
                    position.surah,
                    position.ayah,
                    position.page
                )
            }
        }
    }

    val juzLabel = currentPage?.let { page ->
        MushafPageData.getJuzForPage(page).takeIf { it in 1..30 }?.let { " • الجزء $it" } ?: ""
    } ?: ""

    // بطاقة آية واحدة (خارطة المصحف — المرحلة 5): مسار التحليل الوحيد
    // للقائمة العمودية وعمودي العرض المزدوج — التمييز والتجويد والرسم
    // والترجمة والعلامة والحفظ تُحلّ هنا لكل آية على حدة.
    val renderVerseCard: @Composable (QuranVerseDetail) -> Unit = { detail ->
        // تمييز الآية الجارية (المرحلة 2): خلفية ملوّنة للبطاقة.
        // تمييز الكلمة داخلها فقط عند وصول توقيت حقيقي وعدد
        // مقاطعه مساوٍ لعدد كلمات النص المعروض — وإلا الآية فقط.
        val isPlayingVerse = playingAyahIndex >= 0 &&
            playingAyahs.getOrNull(playingAyahIndex)?.let {
                it.surah == detail.surahNumber && it.ayah == detail.verseNumber
            } == true
        // نص الآية المعروض (المرحلة 5): التجويد بألوان أحكامه
        // عند تفعيله (عثماني فقط)، وإلا الرسم البديل المختار،
        // وإلا النص المحلي. تمييز الكلمة الجارية يُبنى على
        // كلمات النص المعروض نفسه — لا على نص آخر.
        val tajweedMarkup = if (showTajweed && mushafScript == "uthmani") {
            tajweedMaps[detail.surahNumber]?.get(detail.verseNumber)
        } else {
            null
        }
        val displayPlainText = if (mushafScript != "uthmani") {
            scriptMaps[detail.surahNumber]?.get(detail.verseNumber) ?: detail.verseText
        } else {
            detail.verseText
        }
        // ق77: النص المُنسَّق يُحفظ بـ remember لكل بطاقة — كان
        // buildMushafVerseAnnotatedString (مسح وسوم التجويد حرفاً
        // حرفاً + تقسيم النص + indexOf لكل كلمة) يُعاد بناؤه لكل
        // آية ظاهرة في كل تركيب للشاشة، فكل تحديث لكلمة التلاوة
        // كان يعيد تحليل تجويد كل الآيات الظاهرة. والآن لا تُقرأ
        // حالة التوقيت إلا في الآية الجارية وحدها، فتحديث الكلمة
        // لا يعيد تركيب بقية البطاقات أصلاً.
        val timingsForVerse = if (isPlayingVerse) playingTimings else emptyList()
        val activeWordForVerse = if (isPlayingVerse) activeWordPosition else null
        val verseText: AnnotatedString = remember(
            detail.surahNumber,
            detail.verseNumber,
            displayPlainText,
            tajweedMarkup,
            tajweedColors,
            timingsForVerse,
            activeWordForVerse,
            theme.primary
        ) {
            buildMushafVerseAnnotatedString(
                plainText = displayPlainText,
                tajweedMarkup = tajweedMarkup,
                tajweedColors = tajweedColors,
                timings = timingsForVerse,
                activeWordPosition = activeWordForVerse,
                wordHighlightColor = theme.primary.copy(alpha = 0.30f)
            )
        }
        val isHiddenVerse = memorizationMode &&
            (detail.surahNumber to detail.verseNumber) !in revealedVerses
        val verseBookmark = bookmarkByRef[detail.surahNumber to detail.verseNumber]
        MushafVerseCard(
            ayah = detail.verseNumber,
            verseText = verseText,
            tafseer = detail.tafseer,
            translation = translationMaps[detail.surahNumber]?.get(detail.verseNumber),
            showTranslation = showTranslation,
            isPlayingVerse = isPlayingVerse,
            isHiddenVerse = isHiddenVerse,
            onVerseClick = {
                if (memorizationMode) {
                    val key = detail.surahNumber to detail.verseNumber
                    revealedVerses = if (key in revealedVerses) {
                        revealedVerses - key
                    } else {
                        revealedVerses + key
                    }
                } else {
                    studyVerse = detail
                }
            },
            bookmark = verseBookmark,
            onBookmarkClick = {
                if (verseBookmark != null) {
                    editingBookmark = verseBookmark
                } else {
                    UserPreferencesManager.saveMushafBookmark(
                        context,
                        UserPreferencesManager.MushafBookmark(
                            surah = detail.surahNumber,
                            ayah = detail.verseNumber,
                            colorArgb = UserPreferencesManager.MUSHAF_BOOKMARK_DEFAULT_COLOR,
                            note = "",
                            createdAtMillis = System.currentTimeMillis()
                        )
                    )
                    refreshBookmarks()
                }
            },
            verseFontSize = verseFontSize,
            verseFontFamily = verseFontFamily,
            theme = theme
        )
    }

    Scaffold(
        containerColor = theme.background,
        topBar = {
            AppleTopBar(
                title = "المصحف الشريف",
                subtitle = "سورة $surahName" +
                    (currentPage?.let { " • صفحة $it" } ?: "") + juzLabel,
                onBack = onBack,
                actions = {
                    // الترجمة الإنجليزية تحت الآيات (المرحلة 3) — محفوظة محلياً.
                    IconButton(onClick = {
                        showTranslation = !showTranslation
                        UserPreferencesManager.saveMushafShowTranslation(context, showTranslation)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = Translator.tr(
                                if (showTranslation) "إخفاء الترجمة" else "إظهار الترجمة"
                            ),
                            tint = if (showTranslation) theme.primary else theme.textPrimary
                        )
                    }
                    IconButton(onClick = { navDialogOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = Translator.tr("انتقال سريع"),
                            tint = theme.textPrimary
                        )
                    }
                    IconButton(onClick = { bookmarksDialogOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = Translator.tr("علاماتي"),
                            tint = theme.textPrimary
                        )
                    }
                    // إعدادات العرض (المرحلة 5): السمة والصفحتان والتجويد والرسم.
                    IconButton(onClick = { displaySettingsOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = Translator.tr("إعدادات العرض"),
                            tint = theme.textPrimary
                        )
                    }
                }
            )
        },
        bottomBar = bottomBar
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // شريط السور: اختيار سورة يعيد التمرير لأول آياتها ويُسجَّل موضعها.
            // ق138: حالة تمرير + تمرير للشريحة المختارة — كان الصف بلا حالة
            // تمرير إطلاقاً، فبعد قفزة من الحوار أو استئناف موضع محفوظ
            // (أو تقدم التلاوة لسورة تالية) تبقى الشريحة المختارة خارج
            // الشاشة ويعرض الصف سوراً أخرى؛ والتمرير اليدوي للصف لا يغيّر
            // selectedSurah فلا يصارع هذا الأثرُ تمريرَ المستخدم.
            val surahRowState = rememberLazyListState()
            LaunchedEffect(selectedSurah) {
                val index = QuranDataProvider.surahs.indexOfFirst { it.id == selectedSurah }
                if (index >= 0) surahRowState.scrollToItem(index)
            }
            LazyRow(
                state = surahRowState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(QuranDataProvider.surahs, key = { it.id }) { surah ->
                    val isSelected = surah.id == selectedSurah
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (isSelected) theme.primary else theme.cardSurface
                            )
                            .clickable {
                                if (surah.id != selectedSurah) {
                                    // عبر jumpTo لا بالتعيين المباشر: التعيين
                                    // المباشر كان يترك العرض المزدوج على
                                    // صفحتيه القديمتين بينما يتبدل عنوان
                                    // الشريط العلوي إلى السورة الجديدة.
                                    jumpTo(surah.id, 1)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${surah.id}. ${surah.name}",
                            color = if (isSelected) theme.background else theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.5.sp
                        )
                    }
                }
            }

            // صف الحفظ والختمة (المرحلة 4): وضع الحفظ إخفاء عرض فقط،
            // ومخطط الختمة يفتح حوار الخطة؛ والنسبة فيه من القراءة الفعلية.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (memorizationMode) theme.primary else theme.cardSurface
                        )
                        .clickable {
                            val next = !memorizationMode
                            memorizationMode = next
                            UserPreferencesManager.saveMushafMemorizationMode(context, next)
                            if (!next) revealedVerses = emptySet()
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (memorizationMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = if (memorizationMode) theme.background else theme.textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = Translator.tr("وضع الحفظ"),
                            color = if (memorizationMode) theme.background else theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = if (memorizationMode) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.5.sp
                        )
                    }
                }
                val khatmahPercent = khatmahPlan?.let { plan ->
                    val progress = computeMushafKhatmahProgress(
                        plan = plan,
                        currentSurah = latestPosition.value?.surah ?: selectedSurah,
                        currentAyah = latestPosition.value?.ayah ?: 1,
                        currentPage = currentPage,
                        pageMapReady = pageMapReady,
                        nowMillis = System.currentTimeMillis()
                    )
                    (progress.fraction * 100).toInt()
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(theme.cardSurface)
                        .clickable {
                            khatmahPlan = UserPreferencesManager.getMushafKhatmahPlan(context)
                            khatmahLastRead = UserPreferencesManager.getLastReadQuran(context)
                            khatmahDialogOpen = true
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null,
                            tint = theme.textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = Translator.tr("مخطط الختمة") +
                                (khatmahPercent?.let { " • $it%" } ?: ""),
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.5.sp
                        )
                    }
                }
            }

            // تلميح الدراسة (المرحلة 3): نقرة على أي آية تفتح لوحتها.
            // في وضع الحفظ تتغير النقرة إلى كشف/إخفاء الآية.
            Text(
                text = if (memorizationMode) {
                    Translator.tr("وضع الحفظ: اضغط أي آية لإظهارها ثم اضغطها مجدداً لإخفائها — والتلاوة تضيء الآية دون كشف نصها")
                } else {
                    Translator.tr("اضغط أي آية لفتح لوحة الدراسة — التفسير والترجمة وكلمة بكلمة")
                },
                color = theme.textSecondary,
                fontFamily = CairoFont,
                fontSize = 11.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            )

            // حالة ترجمة السورة (المرحلة 3): تحميل أو خطأ صادق مع إعادة المحاولة.
            if (showTranslation && (translationLoading || translationError != null)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (translationLoading) {
                        CircularProgressIndicator(
                            color = theme.primary,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Translator.trf("جارٍ تحميل ترجمة {0}…", activeTranslationName),
                            color = theme.textSecondary,
                            fontFamily = CairoFont,
                            fontSize = 12.sp
                        )
                    } else if (translationError != null) {
                        Text(
                            text = translationError!!,
                            color = Color(0xFFEF4444),
                            fontFamily = CairoFont,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { translationRetryTick++ }) {
                            Text(
                                text = Translator.tr("إعادة المحاولة"),
                                color = theme.primary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // حالة التجويد والرسم البديل (المرحلة 5): تحميل أو خطأ صادق
            // مع إعادة المحاولة — وبلا اتصال يبقى النص المحلي معروضاً.
            val tajweedBusy = showTajweed && mushafScript == "uthmani" &&
                (tajweedLoading || tajweedError != null)
            val scriptBusy = mushafScript != "uthmani" &&
                (scriptLoading || scriptError != null)
            if (tajweedBusy || scriptBusy) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (tajweedLoading || scriptLoading) {
                        CircularProgressIndicator(
                            color = theme.primary,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Translator.tr(
                                if (tajweedLoading) "جارٍ تحميل ألوان التجويد…"
                                else "جارٍ تحميل نص الرسم البديل…"
                            ),
                            color = theme.textSecondary,
                            fontFamily = CairoFont,
                            fontSize = 12.sp
                        )
                    } else {
                        Text(
                            text = tajweedError ?: scriptError ?: "",
                            color = Color(0xFFEF4444),
                            fontFamily = CairoFont,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            if (tajweedError != null) tajweedRetryTick++ else scriptRetryTick++
                        }) {
                            Text(
                                text = Translator.tr("إعادة المحاولة"),
                                color = theme.primary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // شريط التلاوة الاحترافي (المرحلة 2): تشغيل/إيقاف + تمييز الآية
            // والكلمة أثناء التشغيل + تكرار وسرعة ومؤقت نوم وتنزيل دون اتصال.
            // الصوت حقيقي من QuranAudioService (EveryAyah CDN، وQF API للعفاسي
            // عند توفر المفاتيح مع توقيت الكلمات words=true).
            // يظهر الشريط ما دامت آيات السورة محمّلة ولو غابت خريطة
            // الصفحات (فزر التشغيل ينتقل حينها لتلاوة السورة) — لا
            // يختفي كلياً بفشل الخريطة.
            if (currentPage != null || verses.isNotEmpty() || playingPage != null || playingSurah != null) {
                val reciterName = QuranAudioService.getReciterInfo(selectedReciter).nameAr
                val audioLabel = when {
                    audioMessage != null -> audioMessage!!
                    isLoadingAudio -> "جارٍ تحميل التلاوة…"
                    isPlayingAudio && playingSurah != null -> "تشغيل تلاوة سورة ${QuranDataProvider.surahNameOf(playingSurah!!)}"
                    isPlayingAudio && playingPage != null -> "تشغيل تلاوة صفحة $playingPage"
                    playingSurah != null -> "تلاوة سورة ${QuranDataProvider.surahNameOf(playingSurah!!)} متوقفة مؤقتاً"
                    playingPage != null -> "تلاوة صفحة $playingPage متوقفة مؤقتاً"
                    currentPage != null -> "تلاوة صفحة $currentPage"
                    else -> "تلاوة سورة $surahName"
                }
                val audioSubLabel = buildString {
                    append("القارئ: $reciterName")
                    // ق98: تقدّم التلاوة في الشريط نفسه — كانت التسمية
                    // ثابتة بلا أي موضع داخل القائمة، فالآية الجارية
                    // تُرى بتمييزها في القائمة فقط.
                    if (playingAyahs.isNotEmpty() && playingAyahIndex >= 0) {
                        append(" • آية ${playingAyahIndex + 1} من ${playingAyahs.size}")
                    }
                    sleepRemainingText?.let { append(" • $it") }
                    when (downloadStatus) {
                        MushafDownloadStatus.DOWNLOADING ->
                            append(" • تنزيل ${downloadScopeLabel ?: ""}: $downloadDone/$downloadTotal")
                        MushafDownloadStatus.DONE ->
                            append(" • محفوظة (${AudioDownloadHelper.formatFileSize(downloadBytes)})")
                        MushafDownloadStatus.PARTIAL ->
                            append(" • منزّلة جزئياً $downloadDone/$downloadTotal")
                        MushafDownloadStatus.ERROR ->
                            append(" • ${downloadError ?: "تعذّر التنزيل"}")
                        MushafDownloadStatus.IDLE -> {}
                    }
                }
                val speedLabel = { s: Float ->
                    if (s % 1f == 0f) "${s.toInt()}×" else "$s×"
                }
                val repeatLabel = when (repeatMode) {
                    QuranRepeatMode.OFF -> "تكرار: إيقاف"
                    QuranRepeatMode.VERSE -> "تكرار الآية"
                    QuranRepeatMode.PAGE -> "تكرار الصفحة"
                    QuranRepeatMode.SURAH -> "تكرار السورة"
                }
                val repeatIcon = when (repeatMode) {
                    QuranRepeatMode.OFF -> Icons.Default.Repeat
                    QuranRepeatMode.VERSE -> Icons.Default.RepeatOne
                    QuranRepeatMode.PAGE -> Icons.Default.Repeat
                    QuranRepeatMode.SURAH -> Icons.Default.Loop
                }
                AppleCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    containerColor = theme.cardSurface
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(theme.primary)
                                    .clickable(enabled = !isLoadingAudio) { togglePageAudio() },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoadingAudio) {
                                    CircularProgressIndicator(
                                        color = theme.background,
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isPlayingAudio) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlayingAudio) "إيقاف التلاوة مؤقتاً" else "تشغيل التلاوة",
                                        tint = theme.background,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = audioLabel,
                                    color = theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = audioSubLabel,
                                    color = theme.textSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Box {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(theme.cardSurface)
                                        .clickable { reciterMenuOpen = true }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "تغيير القارئ",
                                            color = theme.textPrimary,
                                            fontFamily = CairoFont,
                                            fontSize = 12.sp
                                        )
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = theme.textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = reciterMenuOpen,
                                    onDismissRequest = { reciterMenuOpen = false },
                                    modifier = Modifier.background(theme.cardSurface)
                                ) {
                                    QuranAudioService.availableReciters.forEach { reciter ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    reciter.nameAr,
                                                    fontFamily = CairoFont,
                                                    color = theme.textPrimary
                                                )
                                            },
                                            // ق123: علامة القارئ الحالي داخل القائمة —
                                            // كانت القائمة بلا أي تمييز للمختار.
                                            trailingIcon = {
                                                if (reciter.id == selectedReciter) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = theme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            },
                                            onClick = {
                                                reciterMenuOpen = false
                                                if (reciter.id != selectedReciter) {
                                                    // ق150: تبديل القارئ أثناء التلاوة كان
                                                    // يوقف التشغيل فقط بلا إعادة بناء — رغم
                                                    // أن تعليق ق123 يسميه «إعادة بناء» —
                                                    // فيضطر المستخدم لإعادة التشغيل من أول
                                                    // الصفحة/السورة ويفقد موضعه؛ والآن تُعاد
                                                    // بناء القائمة بالقارئ الجديد وتُستأنف
                                                    // من الآية نفسها (مؤقت النوم يبقى مسلّحاً
                                                    // بموعدِه كما في ق123/ق43).
                                                    val wasPlaying = isPlayingAudio &&
                                                        (playingPage != null || playingSurah != null)
                                                    val resumeIndex = playingAyahIndex.coerceAtLeast(0)
                                                    val resumePage = playingPage
                                                    val resumeSurah = playingSurah
                                                    val newReciterId = reciter.id
                                                    stopAudio(preserveSleepTimer = true)
                                                    audioMessage = null
                                                    selectedReciter = newReciterId
                                                    UserPreferencesManager.saveMushafAudioReciter(context, newReciterId)
                                                    if (wasPlaying) {
                                                        isLoadingAudio = true
                                                        audioScope.launch {
                                                            val ayahs: List<QuranAudioService.QuranTimedAyahAudio> = when {
                                                                resumeSurah != null ->
                                                                    QuranAudioService.getSurahAudioUrls(resumeSurah, newReciterId)
                                                                resumePage != null -> {
                                                                    val timed = QuranAudioService.getPageAudioWithTimings(context, resumePage, newReciterId)
                                                                    timed ?: run {
                                                                        val urls = QuranAudioService.getPageAudioUrls(context, resumePage, newReciterId)
                                                                        val refs = MushafPageData.getPageRefs(resumePage)
                                                                        urls.mapIndexed { index, url ->
                                                                            val (s, a) = refs.getOrNull(index) ?: (selectedSurah to 0)
                                                                            QuranAudioService.QuranTimedAyahAudio(s, a, url)
                                                                        }
                                                                    }
                                                                }
                                                                else -> emptyList()
                                                            }
                                                            isLoadingAudio = false
                                                            if (ayahs.isEmpty()) {
                                                                audioMessage = \"تعذّر تحميل تلاوة هذا القارئ.\"
                                                            } else {
                                                                startPlayback(
                                                                    ayahs,
                                                                    page = resumePage,
                                                                    surah = resumeSurah,
                                                                    startIndex = resumeIndex.coerceIn(0, ayahs.lastIndex)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        // صف التحكم: تكرار، سرعة، مؤقت نوم، تنزيل.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // التكرار: نقرة تدوّر إيقاف ← آية ← صفحة ← سورة.
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(theme.cardSurface)
                                    .clickable { cycleRepeatMode() }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = repeatIcon,
                                        contentDescription = repeatLabel,
                                        tint = if (repeatMode == QuranRepeatMode.OFF) theme.textSecondary else theme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = repeatLabel,
                                        color = if (repeatMode == QuranRepeatMode.OFF) theme.textSecondary else theme.primary,
                                        fontFamily = CairoFont,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                            // السرعة: 0.5–2.0 (نطاق ExoPlayer الحقيقي).
                            Box {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(theme.cardSurface)
                                        .clickable { speedMenuOpen = true }
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Speed,
                                            contentDescription = "سرعة التشغيل",
                                            tint = theme.textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = speedLabel(audioSpeed),
                                            color = theme.textPrimary,
                                            fontFamily = CairoFont,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = speedMenuOpen,
                                    onDismissRequest = { speedMenuOpen = false },
                                    modifier = Modifier.background(theme.cardSurface)
                                ) {
                                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = speedLabel(speed) + if (speed == 1f) " (طبيعية)" else "",
                                                    fontFamily = CairoFont,
                                                    color = if (speed == audioSpeed) theme.primary else theme.textPrimary
                                                )
                                            },
                                            onClick = {
                                                speedMenuOpen = false
                                                audioSpeed = speed
                                                UserPreferencesManager.saveMushafAudioSpeed(context, speed)
                                                QuranAudioPlayer.setSpeed(speed)
                                            }
                                        )
                                    }
                                }
                            }
                            // مؤقت النوم: إيقاف التشغيل بعد المدة المختارة.
                            Box {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(theme.cardSurface)
                                        .clickable { sleepMenuOpen = true }
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Timer,
                                            contentDescription = "مؤقت النوم",
                                            tint = if (sleepMinutes == null) theme.textSecondary else theme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = sleepMinutes?.let { "$it د" } ?: "مؤقت النوم",
                                            color = if (sleepMinutes == null) theme.textSecondary else theme.primary,
                                            fontFamily = CairoFont,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = sleepMenuOpen,
                                    onDismissRequest = { sleepMenuOpen = false },
                                    modifier = Modifier.background(theme.cardSurface)
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "إيقاف المؤقت",
                                                fontFamily = CairoFont,
                                                color = theme.textPrimary
                                            )
                                        },
                                        onClick = {
                                            sleepMenuOpen = false
                                            sleepMinutes = null
                                            sleepRemainingText = null
                                            QuranAudioPlayer.setSleepTimer(null)
                                        }
                                    )
                                    listOf(5, 10, 15, 30, 60).forEach { minutes ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "$minutes دقائق",
                                                    fontFamily = CairoFont,
                                                    color = if (minutes == sleepMinutes) theme.primary else theme.textPrimary
                                                )
                                            },
                                            onClick = {
                                                sleepMenuOpen = false
                                                sleepMinutes = minutes
                                                QuranAudioPlayer.setSleepTimer(minutes)
                                            }
                                        )
                                    }
                                }
                            }
                            // التنزيل: صفحة أو سورة، وحذف تنزيل الصفحة.
                            Box {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(theme.cardSurface)
                                        .clickable { downloadMenuOpen = true }
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (downloadStatus == MushafDownloadStatus.DOWNLOADING) {
                                            CircularProgressIndicator(
                                                color = theme.primary,
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (downloadStatus == MushafDownloadStatus.DONE) Icons.Default.DownloadDone else Icons.Default.Download,
                                                contentDescription = "تنزيل التلاوة",
                                                tint = if (downloadStatus == MushafDownloadStatus.DONE) theme.primary else theme.textSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = when (downloadStatus) {
                                                MushafDownloadStatus.DOWNLOADING -> "$downloadDone/$downloadTotal"
                                                MushafDownloadStatus.DONE -> "منزّلة"
                                                MushafDownloadStatus.PARTIAL -> "جزئياً"
                                                else -> "تنزيل"
                                            },
                                            color = theme.textPrimary,
                                            fontFamily = CairoFont,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = downloadMenuOpen,
                                    onDismissRequest = { downloadMenuOpen = false },
                                    modifier = Modifier.background(theme.cardSurface)
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                // ق113: بعد خطأ أو تنزيل جزئي يصير العنصر
                                                // «إعادة/إكمال» — فإعادة النقر تتخطى المنزّل
                                                // (إزالة التكرار في المساعد) لا تعيد الكل.
                                                // ق123: التنزيل لكل قارئ على حدة (حالة
                                                // الزر والأثر بمفتاحي الصفحة والقارئ) —
                                                // كان العنصر بلا اسم قارئ فينقلب زر «منزّلة»
                                                // إلى «تنزيل» عند التبديل بلا أي تفسير.
                                                text = when (downloadStatus) {
                                                    MushafDownloadStatus.PARTIAL -> "إكمال تنزيل تلاوة الصفحة • $reciterName"
                                                    MushafDownloadStatus.ERROR -> "إعادة تنزيل تلاوة الصفحة • $reciterName"
                                                    else -> "تنزيل تلاوة الصفحة • $reciterName"
                                                },
                                                fontFamily = CairoFont,
                                                color = theme.textPrimary
                                            )
                                        },
                                        enabled = downloadStatus != MushafDownloadStatus.DOWNLOADING && currentPage != null,
                                        onClick = {
                                            downloadMenuOpen = false
                                            currentPage?.let { downloadPageAudio(it) }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = when (downloadStatus) {
                                                    MushafDownloadStatus.PARTIAL -> "إكمال تنزيل تلاوة السورة • $reciterName"
                                                    MushafDownloadStatus.ERROR -> "إعادة تنزيل تلاوة السورة • $reciterName"
                                                    else -> "تنزيل تلاوة السورة • $reciterName"
                                                },
                                                fontFamily = CairoFont,
                                                color = theme.textPrimary
                                            )
                                        },
                                        enabled = downloadStatus != MushafDownloadStatus.DOWNLOADING,
                                        onClick = {
                                            downloadMenuOpen = false
                                            downloadSurahAudio(selectedSurah)
                                        }
                                    )
                                    // ق113: إلغاء التنزيل الجاري من القارئ نفسه —
                                    // كان بلا زر إطلاقاً والمخرج الوحيد مغادرة الشاشة.
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "إلغاء التنزيل",
                                                fontFamily = CairoFont,
                                                color = theme.textPrimary
                                            )
                                        },
                                        enabled = downloadStatus == MushafDownloadStatus.DOWNLOADING,
                                        onClick = {
                                            downloadMenuOpen = false
                                            downloadJob?.cancel()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "حذف تنزيل الصفحة",
                                                fontFamily = CairoFont,
                                                color = theme.textPrimary
                                            )
                                        },
                                        enabled = (downloadStatus == MushafDownloadStatus.DONE || downloadStatus == MushafDownloadStatus.PARTIAL) && currentPage != null,
                                        onClick = {
                                            downloadMenuOpen = false
                                            currentPage?.let { deletePageAudio(it) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!quranReady) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (quranLoadFailed) {
                        QabasErrorState(
                            title = "تعذّر تحميل المصحف",
                            message = "تعذّر تحميل نص القرآن من ملفات التطبيق. تحقق من اكتمال التثبيت واتصالك ثم حاول مجدداً.",
                            onRetry = { quranLoadAttempt++ }
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = theme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "جاري تحميل المصحف...",
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                return@Column
            }

            if (verses.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    QabasErrorState(
                        title = "تعذّر تحميل آيات سورة $surahName",
                        message = "تحقق من الاتصال وحاول مجدداً.",
                        onRetry = { quranLoadAttempt++ }
                    )
                }
                return@Column
            }

            if (spreadActive && spreadPage != null) {
                val startPage = spreadPage!!
                // شريط التنقل بين الصفحتين: السابق يميناً والتالي يساراً في RTL.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            spreadPage = mushafSpreadStartPage((startPage - 2).coerceAtLeast(1))
                        },
                        enabled = startPage > 1
                    ) {
                        Text(
                            text = "‹ السابق",
                            fontFamily = CairoFont,
                            color = theme.primary
                        )
                    }
                    Text(
                        text = "صفحتا $startPage و ${startPage + 1}",
                        color = theme.textSecondary,
                        fontFamily = CairoFont,
                        fontSize = 13.sp
                    )
                    TextButton(
                        onClick = {
                            spreadPage = mushafSpreadStartPage((startPage + 2).coerceAtMost(603))
                        },
                        enabled = startPage < 603
                    ) {
                        Text(
                            text = "التالي ›",
                            fontFamily = CairoFont,
                            color = theme.primary
                        )
                    }
                }
                // عمود صفحة واحدة: ترويسة الصفحة ثم بطاقات آياتها.
                val spreadColumn: @Composable (Int, List<Pair<Int, Int>>, LazyListState) -> Unit =
                    { page, refs, state ->
                        LazyColumn(
                            state = state,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item(key = "header-$page") {
                                // ق133: حارس -1 كما في ترويسة الشريط العلوي — كان
                                // العرض المزدوج يعرض «الجزء -1» قبل اكتمال تحميل
                                // خريطة الصفحات (أو عند فشل قراءة الأصل).
                                val pageJuzLabel = MushafPageData.getJuzForPage(page)
                                    .takeIf { it in 1..30 }?.let { " • الجزء $it" } ?: ""
                                Text(
                                    text = "صفحة $page$pageJuzLabel",
                                    color = theme.textSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            items(refs, key = { "${it.first}:${it.second}" }) { ref ->
                                spreadDetails[ref]?.let { detail -> renderVerseCard(detail) }
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                Row(modifier = Modifier.fillMaxSize()) {
                    // في RTL الطفل الأول يميناً: الصفحة الأولى ثم الثانية.
                    spreadColumn(startPage, spreadRefs.first, firstPageState)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(theme.textSecondary.copy(alpha = 0.25f))
                    )
                    spreadColumn(startPage + 1, spreadRefs.second, secondPageState)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(verses, key = { it.verseNumber }) { verse ->
                        renderVerseCard(verse)
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }

        // حوارات المرحلة 1: الانتقال السريع، وقائمة العلامات، ومحرر العلامة.
        if (navDialogOpen) {
            MushafNavigationDialog(
                pageMapReady = pageMapReady,
                currentPage = currentPage,
                quranReady = quranReady,
                verseFontFamily = verseFontFamily,
                onDismiss = { navDialogOpen = false },
                onJumpTo = { surah, ayah ->
                    navDialogOpen = false
                    jumpTo(surah, ayah)
                }
            )
        }
        if (bookmarksDialogOpen) {
            MushafBookmarksDialog(
                bookmarks = bookmarks,
                pageMapReady = pageMapReady,
                onDismiss = { bookmarksDialogOpen = false },
                onJumpTo = { surah, ayah ->
                    bookmarksDialogOpen = false
                    jumpTo(surah, ayah)
                },
                onEdit = { bookmark ->
                    bookmarksDialogOpen = false
                    editingBookmark = bookmark
                },
                onDelete = { bookmark ->
                    // ق87: تأكيد قبل الحذف بدل الحذف الفوري — تبقى القائمة
                    // مفتوحة خلف حوار التأكيد، فيعود إليها المستخدم عند الإلغاء.
                    bookmarkPendingDelete = bookmark
                }
            )
        }
        editingBookmark?.let { bookmark ->
            MushafBookmarkEditorDialog(
                bookmark = bookmark,
                onDismiss = { editingBookmark = null },
                onSave = { updated ->
                    UserPreferencesManager.saveMushafBookmark(context, updated)
                    refreshBookmarks()
                    editingBookmark = null
                },
                onDelete = {
                    // ق87: من المحرر أيضاً عبر التأكيد نفسه — يُغلق المحرر
                    // ويُسأل المستخدم، فالإلغاء يُبقي العلامة (يُعاد فتح
                    // محررها من القائمة أو من بطاقة الآية).
                    bookmarkPendingDelete = bookmark
                    editingBookmark = null
                }
            )
        }
        // ق87: حوار تأكيد الحذف — النقطة الوحيدة التي يُحذف فيها
        // فعلياً؛ كل مسارات الحذف (القائمة والمحرر) تمر به.
        bookmarkPendingDelete?.let { doomed ->
            MushafBookmarkDeleteConfirmDialog(
                bookmark = doomed,
                onConfirm = {
                    UserPreferencesManager.removeMushafBookmark(context, doomed.surah, doomed.ayah)
                    refreshBookmarks()
                    bookmarkPendingDelete = null
                },
                onDismiss = { bookmarkPendingDelete = null }
            )
        }
        studyVerse?.let { verse ->
            MushafStudyDialog(
                verse = verse,
                verseFontFamily = verseFontFamily,
                onDismiss = {
                    studyVerse = null
                    studyIrabBookOverride = null
                },
                initialIrabBookId = studyIrabBookOverride,
                onOpenStudyVerse = { surah, ayah ->
                    // الآية المشابهة من لوحة الدراسة: إن كانت معروضة
                    // تُفتح لوحتها فوراً؛ وإلا ننتقل إلى سورتها وتفتحها
                    // آلية الآية المعلّقة عند وصول آياتها — الآلية نفسها
                    // المستعملة من مكتبة الكتب (بلا فرض كتاب إعراب هنا).
                    val detail = verses.firstOrNull {
                        it.surahNumber == surah && it.verseNumber == ayah
                    }
                    if (detail != null) {
                        studyVerse = detail
                    } else {
                        pendingStudyVerse = surah to ayah
                        jumpTo(surah, ayah)
                    }
                }
            )
        }
        if (khatmahDialogOpen) {
            MushafKhatmahDialog(
                plan = khatmahPlan,
                pageMapReady = pageMapReady,
                lastRead = khatmahLastRead,
                currentPage = currentPage,
                onDismiss = { khatmahDialogOpen = false },
                onSavePlan = { plan ->
                    UserPreferencesManager.saveMushafKhatmahPlan(context, plan)
                    khatmahPlan = plan
                    khatmahDialogOpen = false
                },
                onClearPlan = {
                    UserPreferencesManager.clearMushafKhatmahPlan(context)
                    khatmahPlan = null
                    khatmahDialogOpen = false
                },
                onJumpTo = { surah, ayah ->
                    khatmahDialogOpen = false
                    jumpTo(surah, ayah)
                }
            )
        }
        if (displaySettingsOpen) {
            MushafDisplaySettingsDialog(
                currentThemeKey = readingThemeKey,
                onThemeSelected = { key ->
                    UserPreferencesManager.setReadingTheme(context, key)
                },
                twoPageSpread = twoPageSpread,
                onSpreadChanged = { enabled ->
                    twoPageSpread = enabled
                    UserPreferencesManager.saveMushafTwoPageSpread(context, enabled)
                },
                showTajweed = showTajweed,
                onTajweedChanged = { enabled ->
                    showTajweed = enabled
                    UserPreferencesManager.saveMushafTajweed(context, enabled)
                },
                script = mushafScript,
                onScriptChanged = { next ->
                    mushafScript = next
                    UserPreferencesManager.saveMushafScript(context, next)
                },
                onShowTajweedLegend = { tajweedLegendOpen = true },
                onOpenLibrary = { booksLibraryOpen = true },
                onDismiss = { displaySettingsOpen = false }
            )
        }
        if (booksLibraryOpen) {
            MushafBooksLibrarySheet(
                currentSurah = latestPosition.value?.surah ?: selectedSurah,
                currentAyah = latestPosition.value?.ayah ?: 1,
                onDismiss = { booksLibraryOpen = false },
                onTranslationSelected = { id ->
                    UserPreferencesManager.saveMushafTranslation(context, id)
                    mushafTranslationId = id
                },
                onOpenTajweedLegend = {
                    booksLibraryOpen = false
                    tajweedLegendOpen = true
                },
                onOpenStudyVerse = { surah, ayah ->
                    // الآية الحالية من موضع القراءة المسجّل. إن كانت
                    // سورتها هي المعروضة تُفتح لوحتها فوراً؛ وإلا
                    // ننتقل إلى سورتها أولاً وتفتحها آلية الآية
                    // المعلّقة عند وصول آياتها — بلا احتياط برقم
                    // الآية وحده (كان يفتح آية سورة أخرى خطأً).
                    val detail = verses.firstOrNull {
                        it.surahNumber == surah && it.verseNumber == ayah
                    }
                    if (detail != null) {
                        booksLibraryOpen = false
                        studyVerse = detail
                        studyIrabBookOverride = "darwish"
                    } else {
                        booksLibraryOpen = false
                        studyIrabBookOverride = "darwish"
                        pendingStudyVerse = surah to ayah
                        jumpTo(surah, ayah)
                    }
                }
            )
        }
        if (tajweedLegendOpen) {
            MushafTajweedLegendDialog(
                tajweedColors = tajweedColors,
                onDismiss = { tajweedLegendOpen = false }
            )
        }
    }
}

// ============================================================================
// حالة تنزيل تلاوة (صفحة/سورة) — المرحلة 2: تُحسب من وجود الملفات فعلاً
// في مخزن التنزيلات الموحّد، لا من رقم مخمّن.
// ============================================================================

private enum class MushafDownloadStatus { IDLE, DOWNLOADING, DONE, PARTIAL, ERROR }

// ============================================================================
// حوارات المرحلة 1 من خارطة المصحف (الأساسيات) — الانتقال السريع والعلامات.
// كل القفزات تمر عبر onJumpTo في الشاشة؛ لا نص قرآني جديد هنا، والبيانات
// كلها من QuranDataProvider وMushafPageData الموثوقتين أصلاً.
// ============================================================================

@Composable
private fun MushafNavigationDialog(
    pageMapReady: Boolean,
    currentPage: Int?,
    quranReady: Boolean,
    verseFontFamily: FontFamily,
    onDismiss: () -> Unit,
    onJumpTo: (Int, Int) -> Unit
) {
    val theme = QabasTheme.current
    // ق105: استعلامات البحث محفوظة عبر التدوير — كانت remember عادياً
    // فيغلق التدوير الحوار ويمسح ما كُتب.
    var surahQuery by rememberSaveable { mutableStateOf("") }
    var pageText by rememberSaveable { mutableStateOf("") }
    var pageError by rememberSaveable { mutableStateOf<String?>(null) }
    val surahResults = remember(surahQuery) { QuranDataProvider.searchSurahs(surahQuery) }
    // بحث نصي كامل في آيات المصحف (الدالة موجودة أصلاً وكانت بلا
    // واجهة داخل القارئ): 3 أحرف على الأقل، و20 نتيجة حداً أقصى.
    var verseQuery by rememberSaveable { mutableStateOf("") }
    // ق105: يُطلب 21 نتيجة ويُعرض 20 — كان الحد 20 صامتاً فيُخفى
    // باقي المطابقات بلا أي إشارة للمستخدم.
    val verseResults = remember(verseQuery, quranReady) {
        if (!quranReady || verseQuery.trim().length < 3) emptyList()
        else QuranDataProvider.searchVerses(verseQuery, limit = 21)
    }
    val hasMoreVerseResults = verseResults.size > 20
    val shownVerseResults = verseResults.take(20)
    // ق133: الجزء والحزب الحاليان لتمييز شريحتيهما في صفّي الانتقال —
    // كانتا أرقاماً مجردة بلا أي علامة للموضع الحالي. يُعاد حسابهما
    // عند اكتمال تحميل الخريطة (قبلها -1 فيُصفَّى بـ takeIf).
    val currentJuz = remember(currentPage, pageMapReady) {
        currentPage?.let { MushafPageData.getJuzForPage(it) }?.takeIf { it in 1..30 }
    }
    val currentHizb = remember(currentPage, pageMapReady) {
        currentPage?.let { MushafPageData.getHizbForPage(it) }?.takeIf { it in 1..60 }
    }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = theme.primary,
        unfocusedBorderColor = theme.textSecondary.copy(alpha = 0.4f),
        focusedTextColor = theme.textPrimary,
        unfocusedTextColor = theme.textPrimary,
        cursorColor = theme.primary,
        focusedPlaceholderColor = theme.textSecondary,
        unfocusedPlaceholderColor = theme.textSecondary
    )

    fun jumpToPage(page: Int) {
        if (!pageMapReady) {
            pageError = Translator.tr("خريطة الصفحات غير جاهزة بعد — حاول مجدداً.")
            return
        }
        val first = MushafPageData.getPageRefs(page).firstOrNull()
        if (first == null) {
            pageError = Translator.tr("تعذّر تحديد أول آية في هذه الصفحة.")
            return
        }
        onJumpTo(first.first, first.second)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.cardSurface,
        shape = RoundedCornerShape(18.dp),
        title = {
            Text(
                text = Translator.tr("انتقال سريع"),
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = surahQuery,
                    onValueChange = { surahQuery = it },
                    placeholder = {
                        Text(
                            text = Translator.tr("ابحث باسم السورة أو رقمها…"),
                            color = theme.textSecondary,
                            fontFamily = CairoFont,
                            fontSize = 12.5.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = theme.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(surahResults, key = { it.id }) { surah ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onJumpTo(surah.id, 1) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${surah.id}. ${surah.name}",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                // ق133: جزء بدء السورة — كان حقل surah.juz
                                // (صحيح في الـ 114 كلها) ميتاً لا يقرؤه أي موضع.
                                text = "${surah.type} • ${surah.versesCount} آية • الجزء ${surah.juz}",
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                if (surahResults.isEmpty()) {
                    Text(
                        text = Translator.tr("لا توجد سورة بهذا الاسم أو الرقم."),
                        color = theme.textSecondary,
                        fontFamily = CairoFont,
                        fontSize = 12.5.sp
                    )
                }
                Text(
                    text = Translator.tr("بحث في نص الآيات"),
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = verseQuery,
                    onValueChange = { verseQuery = it },
                    placeholder = {
                        Text(
                            text = Translator.tr("اكتب كلمة أو أكثر من نص الآية…"),
                            color = theme.textSecondary,
                            fontFamily = CairoFont,
                            fontSize = 12.5.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = theme.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                when {
                    !quranReady && verseQuery.isNotBlank() -> {
                        Text(
                            text = Translator.tr("نص المصحف غير جاهز بعد — انتظر تحميله ثم ابحث."),
                            color = theme.textSecondary,
                            fontFamily = CairoFont,
                            fontSize = 12.5.sp
                        )
                    }
                    verseQuery.trim().length in 1..2 -> {
                        Text(
                            text = Translator.tr("اكتب 3 أحرف على الأقل للبحث في الآيات."),
                            color = theme.textSecondary,
                            fontFamily = CairoFont,
                            fontSize = 12.5.sp
                        )
                    }
                    verseQuery.trim().length >= 3 && verseResults.isEmpty() -> {
                        Text(
                            text = Translator.tr("لا توجد آية تطابق هذا النص."),
                            color = theme.textSecondary,
                            fontFamily = CairoFont,
                            fontSize = 12.5.sp
                        )
                    }
                    verseResults.isNotEmpty() -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(shownVerseResults, key = { "${it.surahNumber}:${it.verseNumber}" }) { verse ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onJumpTo(verse.surahNumber, verse.verseNumber) }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "سورة ${verse.surahName} • آية ${verse.verseNumber}",
                                        color = theme.primary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        // ق105: مقتطف حول المطابقة لا أول
                                        // 90 حرفاً من الآية (كانت الكلمة
                                        // المطابقة تُخفى في الآيات الطويلة).
                                        text = QuranDataProvider.verseSnippet(verse.verseText, verseQuery),
                                        color = theme.textPrimary,
                                        fontFamily = verseFontFamily,
                                        fontSize = 13.sp,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                        if (hasMoreVerseResults) {
                            Text(
                                text = Translator.tr("عُرضت أول 20 نتيجة — ضيّق البحث لرؤية المزيد."),
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                Text(
                    text = Translator.tr("الجزء"),
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items((1..30).toList(), key = { it }) { juz ->
                        // ق133: الشريحة الحالية بنمط شرائح السور نفسه في
                        // هذا الملف (خلفية theme.primary + نص theme.background).
                        val isCurrentJuz = juz == currentJuz
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isCurrentJuz) theme.primary else theme.background)
                                .clickable {
                                    // ق175: القفز لأول آية في الجزء نفسه لا لأول
                                    // آية في صفحتها — 4 أجزاء من 30 تبدأ منتصف
                                    // الصفحة (مثلاً الجزء 7 يبدأ 5:82 وصفحته
                                    // تبدأ 5:77) فكان الانتقال يفتح أعلى الصفحة
                                    // ويترك المستخدم يبحث عن البداية يدوياً.
                                    val first = MushafPageData.getJuzFirstRef(juz)
                                    if (first != null) onJumpTo(first.first, first.second)
                                    else jumpToPage(MushafPageData.getPageForJuz(juz))
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = juz.toString(),
                                color = if (isCurrentJuz) theme.background else theme.textPrimary,
                                fontFamily = NotoSansFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                        }
                    }
                }
                Text(
                    text = Translator.tr("الحزب"),
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items((1..60).toList(), key = { it }) { hizb ->
                        val isCurrentHizb = hizb == currentHizb
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isCurrentHizb) theme.primary else theme.background)
                                .clickable {
                                    // ق175: كالجزء أعلاه — 22 حزباً من 60 تبدأ
                                    // منتصف الصفحة، فيُقفز لأول آية في الحزب
                                    // نفسها لا لأول آية في صفحتها.
                                    val first = MushafPageData.getHizbFirstRef(hizb)
                                    if (first != null) onJumpTo(first.first, first.second)
                                    else jumpToPage(MushafPageData.getPageForHizb(hizb))
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = hizb.toString(),
                                color = if (isCurrentHizb) theme.background else theme.textPrimary,
                                fontFamily = NotoSansFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = pageText,
                        onValueChange = {
                            pageText = it.filter { ch -> ch.isDigit() }.take(3)
                            pageError = null
                        },
                        placeholder = {
                            Text(
                                text = Translator.tr("رقم الصفحة 1–604"),
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 12.5.sp
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            val page = pageText.toIntOrNull()
                            if (page == null || page !in 1..MushafPageData.PAGE_COUNT) {
                                pageError = Translator.tr("أدخل رقم صفحة بين 1 و 604.")
                                return@TextButton
                            }
                            jumpToPage(page)
                        }
                    ) {
                        Text(
                            text = Translator.tr("اذهب"),
                            color = theme.primary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
                if (pageError != null) {
                    Text(
                        text = pageError!!,
                        color = Color(0xFFEF4444),
                        fontFamily = CairoFont,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = Translator.tr("إغلاق"),
                    color = theme.primary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    )
}

@Composable
private fun MushafBookmarksDialog(
    bookmarks: List<UserPreferencesManager.MushafBookmark>,
    pageMapReady: Boolean,
    onDismiss: () -> Unit,
    onJumpTo: (Int, Int) -> Unit,
    onEdit: (UserPreferencesManager.MushafBookmark) -> Unit,
    onDelete: (UserPreferencesManager.MushafBookmark) -> Unit
) {
    val theme = QabasTheme.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.cardSurface,
        shape = RoundedCornerShape(18.dp),
        title = {
            Text(
                text = Translator.tr("علاماتي"),
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            if (bookmarks.isEmpty()) {
                Text(
                    text = Translator.tr("لا توجد علامات بعد — اضغط أيقونة العلامة بجانب أي آية لحفظها هنا."),
                    color = theme.textSecondary,
                    fontFamily = CairoFont,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(bookmarks, key = { "${it.surah}:${it.ayah}" }) { bookmark ->
                        val surahName = QuranDataProvider.surahNameOf(bookmark.surah)
                        val page = if (pageMapReady) {
                            MushafPageData.getPageForAyah(bookmark.surah, bookmark.ayah)
                                .takeIf { it in 1..MushafPageData.PAGE_COUNT }
                        } else {
                            null
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onJumpTo(bookmark.surah, bookmark.ayah) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(bookmark.colorArgb))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "سورة $surahName • آية ${bookmark.ayah}" +
                                        (page?.let { " • صفحة $it" } ?: ""),
                                    color = theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                if (bookmark.note.isNotBlank()) {
                                    Text(
                                        text = bookmark.note,
                                        color = theme.textSecondary,
                                        fontFamily = CairoFont,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(onClick = { onEdit(bookmark) }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = Translator.tr("تعديل العلامة"),
                                    tint = theme.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = { onDelete(bookmark) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = Translator.tr("حذف العلامة"),
                                    tint = theme.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = Translator.tr("إغلاق"),
                    color = theme.primary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    )
}

@Composable
private fun MushafBookmarkEditorDialog(
    bookmark: UserPreferencesManager.MushafBookmark,
    onDismiss: () -> Unit,
    onSave: (UserPreferencesManager.MushafBookmark) -> Unit,
    onDelete: () -> Unit
) {
    val theme = QabasTheme.current
    var noteDraft by remember { mutableStateOf(bookmark.note) }
    var colorDraft by remember { mutableStateOf(bookmark.colorArgb) }
    val surahName = QuranDataProvider.surahNameOf(bookmark.surah)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.cardSurface,
        shape = RoundedCornerShape(18.dp),
        title = {
            Text(
                text = "علامة: سورة $surahName • آية ${bookmark.ayah}",
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = Translator.tr("اللون"),
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    UserPreferencesManager.MUSHAF_BOOKMARK_COLORS.forEach { (argb, name) ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(
                                    width = if (colorDraft == argb) 3.dp else 1.dp,
                                    color = if (colorDraft == argb) theme.primary else theme.textSecondary.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                                .clickable { colorDraft = argb },
                            contentAlignment = Alignment.Center
                        ) {
                            if (colorDraft == argb) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = name,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = {
                        if (it.length <= UserPreferencesManager.MUSHAF_BOOKMARK_MAX_NOTE_LENGTH) {
                            noteDraft = it
                        }
                    },
                    placeholder = {
                        Text(
                            text = Translator.tr("ملاحظة اختيارية…"),
                            color = theme.textSecondary,
                            fontFamily = CairoFont,
                            fontSize = 12.5.sp
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.primary,
                        unfocusedBorderColor = theme.textSecondary.copy(alpha = 0.4f),
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary,
                        cursorColor = theme.primary,
                        focusedPlaceholderColor = theme.textSecondary,
                        unfocusedPlaceholderColor = theme.textSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${noteDraft.length} / ${UserPreferencesManager.MUSHAF_BOOKMARK_MAX_NOTE_LENGTH}",
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(bookmark.copy(note = noteDraft.trim(), colorArgb = colorDraft)) }
            ) {
                Text(
                    text = Translator.tr("حفظ"),
                    color = theme.primary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text(
                        text = Translator.tr("حذف العلامة"),
                        color = Color(0xFFEF4444),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        text = Translator.tr("إلغاء"),
                        color = theme.primary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    )
}

/**
 * ق87: حوار تأكيد حذف علامة مصحف — يصرّح بالمرجع وبأن الملاحظة
 * تُحذف معها بلا تراجع، فلا يفقد المستخدم ملاحظته بنقرة سهو.
 */
@Composable
private fun MushafBookmarkDeleteConfirmDialog(
    bookmark: UserPreferencesManager.MushafBookmark,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = QabasTheme.current
    val surahName = QuranDataProvider.surahNameOf(bookmark.surah)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.cardSurface,
        shape = RoundedCornerShape(18.dp),
        title = {
            Text(
                text = Translator.tr("حذف العلامة؟"),
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Text(
                text = "سورة $surahName • آية ${bookmark.ayah}" +
                    if (bookmark.note.isNotBlank()) " — ستُحذف ملاحظتك معها ولا يمكن التراجع." else " — لا يمكن التراجع عن الحذف.",
                color = theme.textSecondary,
                fontFamily = CairoFont,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = Translator.tr("حذف"),
                    color = Color(0xFFEF4444),
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = Translator.tr("إلغاء"),
                    color = theme.primary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    )
}

// ============================================================================
// لوحة دراسة الآية (خارطة المصحف — المرحلة 3 + قسم القرآن — الكتب).
//
// الأقسام، لكل قسم مصدر حقيقي موثّق:
// 1) التفسير — منتقٍ بين ثمانية كتب: الميسر ملف محلي
//    (tafsir_muyassar.json، 6236 مدخلاً)، والسعدي وابن كثير والطبري من
//    spa5k/tafsir_api (MIT)، والجلالين من alquran.cloud. الكتاب المختار
//    محفوظ محلياً، واللوحة تسمّي المصدر الفعلي ولا تخلط بين الكتب؛ وملاذ
//    السعدي عند فشل spa5k هو QuranEnc (مفتاح arabic_saadi، فُحص حياً).
// 2) الترجمة الإنجليزية — ثلاث ترجمات من QuranEnc (Saheeh الافتراضية،
//    وHilali-Khan، وRowwad) يختار المستخدم بينها من مكتبة الكتب؛
//    وتُجلب السورة كاملة وتُخزَّن بمفتاح الترجمة.
// 3) كلمة بكلمة — واجهة Quran.com العامة (words=true): نقل صوتي وترجمة
//    إنجليزية لكل كلمة؛ علامات نهاية الآية مستبعدة.
// 4) الإعراب — منتقٍ بين كتابين من spa5k/tafsir_api (MIT): الإعراب
//    الميسر وإعراب الدرويش (مفصّل).
// 5) أسباب النزول — مستودع asbab-al-nuzul-dataset (MIT): «صحيح أسباب
//    النزول» لإبراهيم محمد العلي؛ التغطية 50 سورة فقط.
// 6) الآيات المشابهة — مستودع Quran_Mutashabihat_Data (CC0): فهرس
//    بالأرقام العالمية؛ ليست قائمة شاملة ولا تغطي العالميات 6,201–6,236.
// الصرف: لا مصدر موثّق له في التطبيق ولا في هذه الواجهات، فلا لوحة له.
// كل قسم يعالج حالاته بصراحة: تحميل / نص / خطأ دون اتصال / غياب بيانات.
// ============================================================================

@Composable
private fun MushafStudySectionTitle(text: String) {
    val theme = QabasTheme.current
    Text(
        text = text,
        color = theme.textPrimary,
        fontFamily = CairoFont,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        modifier = Modifier.padding(top = 10.dp)
    )
}

@Composable
private fun MushafStudySectionCaption(text: String) {
    val theme = QabasTheme.current
    Text(
        text = text,
        color = theme.textSecondary,
        fontFamily = CairoFont,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 2.dp)
    )
}

// زر إعادة المحاولة الموحّد لأقسام لوحة الدراسة عند فشل الجلب —
// يعيد جلب القسم الفاشل وحده عبر studyRetryTick (بلا إغلاق اللوحة).
@Composable
private fun MushafStudyRetryButton(onRetry: () -> Unit) {
    val theme = QabasTheme.current
    TextButton(onClick = onRetry) {
        Text(
            text = Translator.tr("إعادة المحاولة"),
            color = theme.primary,
            fontFamily = CairoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp
        )
    }
}

@Composable
private fun MushafStudyDialog(
    verse: QuranVerseDetail,
    verseFontFamily: FontFamily,
    onDismiss: () -> Unit,
    initialIrabBookId: String? = null,
    onOpenStudyVerse: (Int, Int) -> Unit
) {
    val theme = QabasTheme.current

    // إعادة المحاولة: كل قسم فاشل يعيد جلبه وحده عبر هذا العدّاد —
    // قبلها كان الفشل طريقاً مسدوداً لا عودة منه إلا بإغلاق اللوحة
    // وإعادة فتحها (فيُعاد جلب الأقسام الستة كلها).
    var studyRetryTick by remember(verse.surahNumber, verse.verseNumber) {
        mutableStateOf(0)
    }

    // التفسير: منتقٍ بين كتب MUSHAF_TAFSIR_BOOKS؛ الكتاب المختار محفوظ
    // محلياً (getMushafTafsirBook)، والجلب عبر fetchTafsirForBook عند
    // فتح اللوحة أو تبديل الكتاب. للميسر يُستعمل نص بطاقة الآية أولاً
    // (الميسر المحلي نفسه) وإلا getTafsirForVerse؛ ولا ملاذ صامت لكتاب
    // آخر عند غياب الميسر. التسمية تتبع الكتاب المختار بصراحة.
    val context = LocalContext.current
    var tafsirBookId by remember(verse.surahNumber, verse.verseNumber) {
        mutableStateOf(UserPreferencesManager.getMushafTafsirBook(context))
    }
    val selectedTafsirBook =
        QuranDataProvider.MUSHAF_TAFSIR_BOOKS.firstOrNull { it.id == tafsirBookId }
            ?: QuranDataProvider.MUSHAF_TAFSIR_BOOKS[0]
    var tafsirText by remember(verse.surahNumber, verse.verseNumber) {
        mutableStateOf<String?>(null)
    }
    var tafsirLoading by remember(verse.surahNumber, verse.verseNumber) {
        mutableStateOf(true)
    }
    var tafsirFailed by remember(verse.surahNumber, verse.verseNumber) {
        mutableStateOf(false)
    }
    LaunchedEffect(verse.surahNumber, verse.verseNumber, tafsirBookId, studyRetryTick) {
        tafsirLoading = true
        tafsirFailed = false
        tafsirText = null
        val fetched = if (tafsirBookId == "muyassar" && verse.tafseer.isNotBlank()) {
            verse.tafseer
        } else {
            QuranDataProvider.fetchTafsirForBook(tafsirBookId, verse.surahNumber, verse.verseNumber)
        }
        if (fetched.isNullOrBlank()) {
            // الميسر: غياب بيانات محلية (لا ملاذ)؛ غيره: فشل شبكي.
            tafsirFailed = tafsirBookId != "muyassar"
        } else {
            tafsirText = fetched
        }
        tafsirLoading = false
    }

    // الترجمة: من ذاكرة السورة بالترجمة المختارة، وإلا جلب السورة
    // كاملة (مكالمة واحدة). الترجمة المختارة محفوظة محلياً وتُقرأ عند
    // فتح اللوحة؛ وتبديلها من مكتبة الكتب يسري على الفتح التالي.
    // ق96: معرّف الترجمة مفتاح للأثر — كان الاسم المعروض يتحدث
    // مع أي إعادة تركيب بينما النص يبقى من الترجمة القديمة.
    val studyTranslationId = UserPreferencesManager.getMushafTranslation(context)
    val selectedTranslation =
        QuranDataProvider.MUSHAF_TRANSLATIONS.firstOrNull { it.id == studyTranslationId }
            ?: QuranDataProvider.MUSHAF_TRANSLATIONS[0]
    var translationText by remember(verse.surahNumber, verse.verseNumber) {
        mutableStateOf<String?>(null)
    }
    var translationLoading by remember(verse.surahNumber, verse.verseNumber) {
        mutableStateOf(true)
    }
    LaunchedEffect(verse.surahNumber, verse.verseNumber, studyTranslationId, studyRetryTick) {
        translationLoading = true
        val cached = QuranDataProvider.getCachedSurahTranslationFor(
            studyTranslationId, verse.surahNumber
        )
        translationText = cached?.get(verse.verseNumber)
            ?: QuranDataProvider.fetchSurahTranslationFor(studyTranslationId, verse.surahNumber)
                ?.get(verse.verseNumber)
        translationLoading = false
    }

    // الكلمات: جلب عند الطلب من واجهة Quran.com العامة.
    var words by remember(verse.surahNumber, verse.verseNumber) {
        mutableStateOf<List<QuranDataProvider.QuranWordStudy>?>(null)
    }
    var wordsLoading by remember(verse.surahNumber, verse.verseNumber) {
        mutableStateOf(true)
    }
    LaunchedEffect(verse.surahNumber, verse.verseNumber, studyRetryTick) {
        wordsLoading = true
        words = QuranDataProvider.fetchVerseWords(verse.surahNumber, verse.verseNumber)
        wordsLoading = false
    }

    // الإعراب: منتقٍ بين كتابين من spa5k؛ يُجلب عند الطلب أو تبديل الكتاب.
    // initialIrabBookId (من فئة البلاغة في المكتبة) يفرض الدرويش مبدئياً
    // لعرض قسم البلاغة، وإلا يُقرأ الكتاب المحفوظ؛ ولا يُكتب التفضيل
    // إلا عند نقر المستخدم على شريحة كتاب.
    var irabBookId by remember(verse.surahNumber, verse.verseNumber, initialIrabBookId) {
        mutableStateOf(initialIrabBookId ?: UserPreferencesManager.getMushafIrabBook(context))
    }
    var irabText by remember(verse.surahNumber, verse.verseNumber) {
        mutableStateOf<String?>(null)
    }
    var irabLoading by remember(verse.surahNumber, verse.verseNumber) {
        mutableStateOf(true)
    }
    LaunchedEffect(verse.surahNumber, verse.verseNumber, irabBookId, studyRetryTick) {
        irabLoading = true
        irabText = QuranDataProvider.fetchIrab(irabBookId, verse.surahNumber, verse.verseNumber)
        irabLoading = false
    }

    // البلاغة: قسم مستقل داخل نص الدرويش وحده — يُقسم النص المجلوب
    // عند أول ورود «البلاغة:»؛ ما قبله إعراب وما بدءاً منها بلاغة.
    // التقسيم عرضٌ للنص نفسه بلا تغيير كلمة. لا واسم يعني لا قسم
    // بلاغة مسجّلاً لهذه الآية في هذا المصدر.
    val balaghaMarker = "البلاغة:"
    val balaghaIndex = if (irabBookId == "darwish" && !irabText.isNullOrBlank()) {
        irabText!!.indexOf(balaghaMarker)
    } else {
        -1
    }
    val irabDisplayText = if (balaghaIndex >= 0) {
        irabText!!.substring(0, balaghaIndex).trimEnd().ifBlank { null }
    } else {
        irabText
    }
    val balaghaText = if (balaghaIndex >= 0) {
        irabText!!.substring(balaghaIndex).trim()
    } else {
        null
    }

    // أسباب النزول: تُجلب خريطة السورة مرة واحدة وتُحفظ في ذاكرة
    // المزوّد؛ null هنا تعني فشل الشبكة، والقائمة الفارغة تعني غياب
    // سبب مسجّل لهذه الآية في المصدر.
    var asbabList by remember(verse.surahNumber, verse.verseNumber) {
        mutableStateOf<List<String>?>(null)
    }
    var asbabLoading by remember(verse.surahNumber, verse.verseNumber) {
        mutableStateOf(true)
    }
    LaunchedEffect(verse.surahNumber, verse.verseNumber, studyRetryTick) {
        asbabLoading = true
        asbabList = QuranDataProvider.fetchAsbabForVerse(verse.surahNumber, verse.verseNumber)
        asbabLoading = false
    }

    // الآيات المشابهة: ملف المتشابهات يُجلب مرة واحدة ويُحفظ في ذاكرة
    // المزوّد؛ null من fetchMutashabihat تعني فشل الشبكة (وتُعرض رسالة
    // فشل)، والقائمة الفارغة تعني غياب متشابهات مسجّلة لهذه الآية.
    var mutashabihatList by remember(verse.surahNumber, verse.verseNumber) {
        mutableStateOf<List<Pair<Int, Int>>?>(null)
    }
    var mutashabihatFailed by remember(verse.surahNumber, verse.verseNumber) {
        mutableStateOf(false)
    }
    var mutashabihatLoading by remember(verse.surahNumber, verse.verseNumber) {
        mutableStateOf(true)
    }
    LaunchedEffect(verse.surahNumber, verse.verseNumber, studyRetryTick) {
        mutashabihatLoading = true
        mutashabihatFailed = false
        val data = QuranDataProvider.fetchMutashabihat()
        if (data == null) {
            mutashabihatFailed = true
            mutashabihatList = null
        } else {
            mutashabihatList = QuranDataProvider.fetchMutashabihatForVerse(
                verse.surahNumber, verse.verseNumber
            )
        }
        mutashabihatLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.cardSurface,
        shape = RoundedCornerShape(18.dp),
        title = {
            Text(
                text = "سورة ${verse.surahName} • آية ${verse.verseNumber}",
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // نص الآية كما في المصحف (بلا أي تغيير).
                item {
                    Text(
                        text = verse.verseText,
                        color = theme.textPrimary,
                        fontFamily = verseFontFamily,
                        fontSize = 18.sp,
                        lineHeight = 30.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                // 1) التفسير — منتقٍ بين كتب التفسير الثمانية.
                item {
                    MushafStudySectionTitle(
                        Translator.tr(selectedTafsirBook.arabicName)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        items(QuranDataProvider.MUSHAF_TAFSIR_BOOKS, key = { it.id }) { book ->
                            val isSelected = book.id == tafsirBookId
                            Text(
                                text = book.arabicName,
                                color = if (isSelected) theme.primary else theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) theme.primary.copy(alpha = 0.15f)
                                        else theme.cardSurface
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) theme.primary
                                        else theme.textSecondary.copy(alpha = 0.4f),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        if (book.id != tafsirBookId) {
                                            UserPreferencesManager.saveMushafTafsirBook(context, book.id)
                                            tafsirBookId = book.id
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                    when {
                        tafsirLoading -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = theme.primary,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = Translator.tr("جارٍ تحميل التفسير…"),
                                    color = theme.textSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                        !tafsirText.isNullOrBlank() -> {
                            Text(
                                text = tafsirText!!,
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontSize = 13.sp,
                                lineHeight = 21.sp
                            )
                            MushafStudySectionCaption(
                                Translator.trf("المصدر: {0} — {1}", selectedTafsirBook.source, selectedTafsirBook.author)
                            )
                        }
                        else -> {
                            Text(
                                text = if (tafsirFailed) {
                                    Translator.tr("تعذّر تحميل التفسير — تحقق من الاتصال وحاول مجدداً.")
                                } else {
                                    Translator.tr("لا يوجد تفسير محفوظ لهذه الآية.")
                                },
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 12.5.sp,
                                lineHeight = 20.sp
                            )
                            if (tafsirFailed) {
                                MushafStudyRetryButton(onRetry = { studyRetryTick++ })
                            }
                        }
                    }
                }
                // 2) الترجمة الإنجليزية — بالترجمة المختارة من مكتبة الكتب.
                item {
                    MushafStudySectionTitle(
                        Translator.trf("الترجمة الإنجليزية — {0}", selectedTranslation.label)
                    )
                    when {
                        translationLoading -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = theme.primary,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = Translator.tr("جارٍ تحميل الترجمة…"),
                                    color = theme.textSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                        translationText != null -> {
                            Text(
                                text = translationText!!,
                                color = theme.textPrimary,
                                fontFamily = NotoSansFont,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                textAlign = TextAlign.Left,
                                modifier = Modifier.fillMaxWidth()
                            )
                            MushafStudySectionCaption(
                                Translator.trf("المصدر: QuranEnc — {0}", selectedTranslation.label)
                            )
                        }
                        else -> {
                            Text(
                                text = Translator.tr("تعذّر تحميل الترجمة — تحقق من الاتصال وحاول مجدداً."),
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 12.5.sp,
                                lineHeight = 20.sp
                            )
                            MushafStudyRetryButton(onRetry = { studyRetryTick++ })
                        }
                    }
                }
                // 3) كلمة بكلمة: النقل الصوتي والترجمة الإنجليزية لكل كلمة.
                item {
                    MushafStudySectionTitle(Translator.tr("كلمة بكلمة"))
                    when {
                        wordsLoading -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = theme.primary,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = Translator.tr("جارٍ تحميل الكلمات…"),
                                    color = theme.textSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                        !words.isNullOrEmpty() -> {
                            MushafStudySectionCaption(
                                Translator.tr("المصدر: Quran.com — كلمات الآية مع النقل الصوتي والترجمة")
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        else -> {
                            Text(
                                text = Translator.tr("تعذّر تحميل الكلمات — تحقق من الاتصال وحاول مجدداً."),
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 12.5.sp,
                                lineHeight = 20.sp
                            )
                            MushafStudyRetryButton(onRetry = { studyRetryTick++ })
                        }
                    }
                }
                val loadedWords = words
                if (!loadedWords.isNullOrEmpty()) {
                    items(loadedWords, key = { it.position }) { word ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = word.textUthmani,
                                color = theme.textPrimary,
                                fontFamily = verseFontFamily,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = word.transliteration ?: "—",
                                    color = theme.textPrimary,
                                    fontFamily = NotoSansFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    textAlign = TextAlign.Left,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = word.translation ?: "—",
                                    color = theme.textSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Left,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
                // 4) الإعراب — منتقٍ بين الإعراب الميسر والدرويش (مفصّل).
                item {
                    MushafStudySectionTitle(Translator.tr("الإعراب"))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        items(
                            listOf(
                                "muyassar" to "الإعراب الميسر",
                                "darwish" to "الدرويش (مفصّل)"
                            ),
                            key = { it.first }
                        ) { (bookId, label) ->
                            val isSelected = bookId == irabBookId
                            Text(
                                text = label,
                                color = if (isSelected) theme.primary else theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) theme.primary.copy(alpha = 0.15f)
                                        else theme.cardSurface
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) theme.primary
                                        else theme.textSecondary.copy(alpha = 0.4f),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        if (bookId != irabBookId) {
                                            UserPreferencesManager.saveMushafIrabBook(context, bookId)
                                            irabBookId = bookId
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                    when {
                        irabLoading -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = theme.primary,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = Translator.tr("جارٍ تحميل الإعراب…"),
                                    color = theme.textSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                        !irabDisplayText.isNullOrBlank() -> {
                            Text(
                                text = irabDisplayText!!,
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontSize = 13.sp,
                                lineHeight = 21.sp
                            )
                            MushafStudySectionCaption(
                                Translator.tr(
                                    "المصدر: spa5k/tafsir_api (MIT) — " +
                                        if (irabBookId == "darwish") "إعراب القرآن للدرويش"
                                        else "الإعراب الميسر"
                                )
                            )
                        }
                        else -> {
                            Text(
                                text = if (!irabText.isNullOrBlank()) {
                                    Translator.tr(
                                        "لا يوجد نص إعراب قبل قسم البلاغة لهذه الآية في هذا المصدر."
                                    )
                                } else {
                                    Translator.tr("تعذّر تحميل الإعراب — تحقق من الاتصال وحاول مجدداً.")
                                },
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 12.5.sp,
                                lineHeight = 20.sp
                            )
                            if (irabText.isNullOrBlank()) {
                                MushafStudyRetryButton(onRetry = { studyRetryTick++ })
                            }
                        }
                    }
                }
                // 4-ب) البلاغة — قسم مستقل داخل نص الدرويش (لا كتاب
                // بلاغة منفصل): يُعرض ما بدءاً من أول «البلاغة:» في نص
                // الدرويش المجلوب؛ وبلا الواسم لا قسم بلاغة مسجّلاً.
                item {
                    MushafStudySectionTitle(Translator.tr("البلاغة"))
                    when {
                        irabLoading -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = theme.primary,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = Translator.tr("جارٍ تحميل البلاغة…"),
                                    color = theme.textSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                        irabBookId != "darwish" -> {
                            Text(
                                text = Translator.tr(
                                    "قسم البلاغة من كتاب الدرويش — اختر «الدرويش (مفصّل)» " +
                                        "في قسم الإعراب أعلاه لعرضه لهذه الآية."
                                ),
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 12.5.sp,
                                lineHeight = 20.sp
                            )
                        }
                        irabText.isNullOrBlank() -> {
                            Text(
                                text = Translator.tr("تعذّر تحميل البلاغة — تحقق من الاتصال وحاول مجدداً."),
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 12.5.sp,
                                lineHeight = 20.sp
                            )
                            MushafStudyRetryButton(onRetry = { studyRetryTick++ })
                        }
                        !balaghaText.isNullOrBlank() -> {
                            Text(
                                text = balaghaText!!,
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontSize = 13.sp,
                                lineHeight = 21.sp
                            )
                            MushafStudySectionCaption(
                                Translator.tr(
                                    "قسم البلاغة من كتاب «إعراب القرآن وبيانه» للدرويش — " +
                                        "لا كتاب بلاغة منفصل"
                                )
                            )
                        }
                        else -> {
                            Text(
                                text = Translator.tr("لا يوجد قسم بلاغة مسجّل لهذه الآية في هذا المصدر."),
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 12.5.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
                // 5) أسباب النزول — فقرات منفصلة، بلا اختلاق عند الغياب.
                item {
                    MushafStudySectionTitle(Translator.tr("أسباب النزول"))
                    when {
                        asbabLoading -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = theme.primary,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = Translator.tr("جارٍ تحميل أسباب النزول…"),
                                    color = theme.textSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                        asbabList == null -> {
                            Text(
                                text = Translator.tr("تعذّر تحميل أسباب النزول — تحقق من الاتصال وحاول مجدداً."),
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 12.5.sp,
                                lineHeight = 20.sp
                            )
                            MushafStudyRetryButton(onRetry = { studyRetryTick++ })
                        }
                        asbabList!!.isEmpty() -> {
                            Text(
                                text = Translator.tr("لا يوجد سبب نزول مسجّل لهذه الآية في المصدر."),
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 12.5.sp,
                                lineHeight = 20.sp
                            )
                        }
                        else -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                asbabList!!.forEach { reason ->
                                    Text(
                                        text = reason,
                                        color = theme.textPrimary,
                                        fontFamily = CairoFont,
                                        fontSize = 13.sp,
                                        lineHeight = 21.sp
                                    )
                                }
                            }
                            MushafStudySectionCaption(
                                Translator.tr(
                                    "المصدر: «صحيح أسباب النزول» لإبراهيم محمد العلي — " +
                                        "عبر مستودع asbab-al-nuzul-dataset (MIT)؛ التغطية 50 سورة فقط"
                                )
                            )
                        }
                    }
                }
                // 6) الآيات المشابهة — النقر على أي آية مشابهة يفتح لوحة دراستها.
                item {
                    MushafStudySectionTitle(Translator.tr("الآيات المشابهة"))
                    when {
                        mutashabihatLoading -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = theme.primary,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = Translator.tr("جارٍ تحميل الآيات المشابهة…"),
                                    color = theme.textSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                        mutashabihatFailed -> {
                            Text(
                                text = Translator.tr("تعذّر تحميل الآيات المشابهة — تحقق من الاتصال وحاول مجدداً."),
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 12.5.sp,
                                lineHeight = 20.sp
                            )
                            MushafStudyRetryButton(onRetry = { studyRetryTick++ })
                        }
                        mutashabihatList.isNullOrEmpty() -> {
                            Text(
                                text = Translator.tr("لا توجد متشابهات مسجّلة لهذه الآية."),
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 12.5.sp,
                                lineHeight = 20.sp
                            )
                        }
                        else -> {
                            MushafStudySectionCaption(
                                Translator.tr("انقر أي آية مشابهة لفتح لوحة دراستها")
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                mutashabihatList!!.forEach { (similarSurah, similarAyah) ->
                                    val similarSurahName =
                                        QuranDataProvider.surahNameOf(similarSurah)
                                    val similarText = QuranDataProvider.getVerseText(
                                        similarSurah, similarAyah
                                    )
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onOpenStudyVerse(similarSurah, similarAyah)
                                            }
                                    ) {
                                        Text(
                                            text = Translator.trf("سورة {0} • آية {1}", similarSurahName, similarAyah),
                                            color = theme.textPrimary,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp
                                        )
                                        if (similarText != null) {
                                            Text(
                                                text = similarText,
                                                color = theme.textPrimary,
                                                fontFamily = verseFontFamily,
                                                fontSize = 16.sp,
                                                lineHeight = 28.sp,
                                                textAlign = TextAlign.Right,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                            MushafStudySectionCaption(
                                Translator.tr(
                                    "المصدر: Quran_Mutashabihat_Data (CC0) — ليست قائمة " +
                                        "شاملة، بل الأكثر إرباكاً للحفّاظ؛ ولا تغطي العالميات 6,201–6,236"
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = Translator.tr("إغلاق"),
                    color = theme.primary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    )
}

// ============================================================================
// مخطط الختمة ووضع الحفظ (خارطة المصحف — المرحلة 4).
//
// الختمة: الهدف أيام أو تاريخ، والحساب كله من البيانات الحقيقية
// (604 صفحات / 114 سورة / 6236 آية — من MushafPageData و
// QuranDataProvider.surahs، لا رقم مخترع). التقدّم من موضع القراءة
// الفعلي وحده (last_read_*) — لا عدّاد مستقل ولا زر «أنجزت» يدوي،
// فلا تقدّم وهمي. الوحدة صفحات عند جاهزية خريطة الصفحات، وإلا آيات
// (من عدد آيات السور الحقيقي). وضع الحفظ: إخفاء عرض فقط — النص
// القرآني لا يُغيَّر، والكشف يعيده كما هو حرفاً حرفاً.
// ============================================================================

private const val MUSHAF_KHATMAH_TOTAL_AYAH_COUNT = UserPreferencesManager.KHATMAH_TOTAL_AYAH_COUNT

// ============================================================================
// بناء نص الآية المعروض (خارطة المصحف — المرحلة 5).
// - التجويد: مقاطع <tajweed> بألوان أحكامها (تحليل مكدّس في
//   QuranDataProvider.parseTajweedMarkup — متداخلة أحياناً).
// - تمييز الكلمة الجارية: خلفية فوق الكلمة فقط عندما يكون عدد مقاطع
//   التوقيت مساوياً لعدد كلمات النص المعروض نفسه (تجويداً كان أو رسماً
//   بديلاً أو محلياً) — وإلا يبقى تمييز الآية وحده. لا تخمين زمني.
// ============================================================================

private fun buildMushafVerseAnnotatedString(
    plainText: String,
    tajweedMarkup: String?,
    tajweedColors: Map<String, Color> = QuranDataProvider.TAJWEED_RULE_COLORS,
    timings: List<QuranAudioService.QuranWordTiming>,
    activeWordPosition: Int?,
    wordHighlightColor: Color
): AnnotatedString {
    val segments = tajweedMarkup?.let { QuranDataProvider.parseTajweedMarkup(it) }
    val displayText = segments?.joinToString("") { it.text } ?: plainText
    // حدود الكلمات في النص المعروض (لمطابقة توقيت الكلمات).
    val tokenRanges = ArrayList<IntRange>()
    var searchFrom = 0
    for (token in displayText.split(" ").filter { it.isNotBlank() }) {
        val start = displayText.indexOf(token, searchFrom)
        if (start < 0) break
        tokenRanges += start until start + token.length
        searchFrom = start + token.length
    }
    val activeTokenRange = if (activeWordPosition != null && timings.isNotEmpty() &&
        timings.size == tokenRanges.size && activeWordPosition in 1..tokenRanges.size
    ) {
        tokenRanges[activeWordPosition - 1]
    } else {
        null
    }
    return buildAnnotatedString {
        append(displayText)
        if (segments != null) {
            var offset = 0
            for (segment in segments) {
                val ruleColor = segment.ruleClass?.let { tajweedColors[it] }
                if (ruleColor != null && segment.text.isNotEmpty()) {
                    addStyle(SpanStyle(color = ruleColor), offset, offset + segment.text.length)
                }
                offset += segment.text.length
            }
        }
        if (activeTokenRange != null) {
            addStyle(
                SpanStyle(background = wordHighlightColor),
                activeTokenRange.first,
                activeTokenRange.last + 1
            )
        }
    }
}

/** عكس دالة الترتيب العام: ترتيب عام ← (سورة، آية). */
private fun mushafRefForGlobalIndex(index: Int): Pair<Int, Int> {
    var remaining = index.coerceIn(1, MUSHAF_KHATMAH_TOTAL_AYAH_COUNT)
    for (item in QuranDataProvider.surahs) {
        if (remaining <= item.versesCount) return item.id to remaining
        remaining -= item.versesCount
    }
    return 114 to 6
}

private data class MushafKhatmahProgress(
    val fraction: Float,
    val completed: Boolean,
    val overdue: Boolean,
    val daysElapsed: Int,
    val daysLeft: Int,
    val totalUnits: Int,
    val readUnits: Int,
    val leftUnits: Int,
    val suggestedDailyUnits: Int,
    val unitWord: String,
    val todayStart: Pair<Int, Int>,
    val todayEnd: Pair<Int, Int>,
    val todayStartPage: Int?,
    val todayEndPage: Int?
)

/**
 * تقدّم خطة ختمة من موضع القراءة الفعلي الحي. النواة (النسبة،
 * المقروء، المتبقي، المقترح اليوم) من الحساب المشترك في
 * UserPreferencesManager.computeKhatmahProgress؛ هنا يُضاف إليها
 * نطاق «ورد اليوم» (من–إلى) للقفز إليه. الوحدة صفحات عند جاهزية
 * خريطة الصفحات، وإلا آيات.
 */
private fun computeMushafKhatmahProgress(
    plan: UserPreferencesManager.MushafKhatmahPlan,
    currentSurah: Int,
    currentAyah: Int,
    currentPage: Int?,
    pageMapReady: Boolean,
    nowMillis: Long
): MushafKhatmahProgress {
    val usePages = pageMapReady && plan.startPage != null && currentPage != null
    val core = UserPreferencesManager.computeKhatmahProgress(
        plan = plan,
        currentSurah = currentSurah,
        currentAyah = currentAyah,
        currentPage = currentPage,
        usePages = usePages,
        nowMillis = nowMillis
    )
    val daily = core.suggestedDailyUnits
    return if (usePages) {
        val todayStartPage = currentPage!!
        val todayEndPage = (currentPage + daily - 1)
            .coerceIn(1, MushafPageData.PAGE_COUNT)
        MushafKhatmahProgress(
            fraction = core.fraction,
            completed = core.completed,
            overdue = core.overdue,
            daysElapsed = core.daysElapsed,
            daysLeft = core.daysLeft,
            totalUnits = core.totalUnits,
            readUnits = core.readUnits,
            leftUnits = core.leftUnits,
            suggestedDailyUnits = core.suggestedDailyUnits,
            unitWord = core.unitWord,
            todayStart = MushafPageData.getPageRefs(todayStartPage).firstOrNull()
                ?: (currentSurah to currentAyah),
            todayEnd = MushafPageData.getPageRefs(todayEndPage).lastOrNull()
                ?: (currentSurah to currentAyah),
            todayStartPage = todayStartPage,
            todayEndPage = todayEndPage
        )
    } else {
        val currentGlobal = UserPreferencesManager.globalAyahIndex(currentSurah, currentAyah)
        MushafKhatmahProgress(
            fraction = core.fraction,
            completed = core.completed,
            overdue = core.overdue,
            daysElapsed = core.daysElapsed,
            daysLeft = core.daysLeft,
            totalUnits = core.totalUnits,
            readUnits = core.readUnits,
            leftUnits = core.leftUnits,
            suggestedDailyUnits = core.suggestedDailyUnits,
            unitWord = core.unitWord,
            todayStart = mushafRefForGlobalIndex(currentGlobal),
            todayEnd = mushafRefForGlobalIndex(
                (currentGlobal + daily - 1).coerceAtMost(MUSHAF_KHATMAH_TOTAL_AYAH_COUNT)
            ),
            todayStartPage = null,
            todayEndPage = null
        )
    }
}

private fun formatMushafKhatmahDate(millis: Long): String =
    SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(millis))

/** بداية اليوم (00:00) بتوقيت الجهاز — لحساب الأيام تقويمياً لا بـ 24 ساعة. */
private fun startOfMushafDay(millis: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

// ق153: حساب الأيام تقويمياً لا بقسمة المللي ثوانٍ — القسمة على
// 24 ساعة تُخطئ عبر تغيير التوقيت الصيفي (يوم 23/25 ساعة):
// فتاريخ الانتهاء المعروض يسبق يوماً، وعدد الأيام من تاريخ
// منتقى ينقص يوماً في المناطق غرب UTC لأن المنتقي يعيد منتصف
// الليل UTC. التاريخ المحلي من المللي ثوانٍ، والتاريخ المنتقى
// من حقول UTC، وبداية اليوم المحلي لأي منهما.
/** التاريخ المحلي (تقويم الجهاز) للحظة ما. */
private fun mushafLocalDateOf(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

/** التاريخ الذي اختاره المستخدم في منتقي التاريخ (منتصف الليل UTC). */
private fun mushafPickedLocalDate(utcMillis: Long): LocalDate =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

/** بداية اليوم المحلي لتاريخ — لعرض تاريخ بصيغة التاريخ. */
private fun mushafStartOfLocalDate(date: LocalDate): Long =
    date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MushafKhatmahDialog(
    plan: UserPreferencesManager.MushafKhatmahPlan?,
    pageMapReady: Boolean,
    lastRead: UserPreferencesManager.LastReadQuran?,
    currentPage: Int?,
    onDismiss: () -> Unit,
    onSavePlan: (UserPreferencesManager.MushafKhatmahPlan) -> Unit,
    onClearPlan: () -> Unit,
    onJumpTo: (Int, Int) -> Unit
) {
    val theme = QabasTheme.current

    // بعد إنهاء خطة مكتملة نعرض نموذج إنشاء جديد دون إغلاق الحوار؛
    // حفظ الجديدة يستبدل القديمة، فلا حاجة لمسحها أولاً.
    var showCreateForm by remember(plan) { mutableStateOf(plan == null) }
    var confirmClear by remember { mutableStateOf(false) }

    // تعديل هدف خطة نشطة: يغيّر عدد الأيام فقط ويُبقي بداية الخطة
    // وتاريخها؛ بلا حذف وإعادة إنشاء يفقدان التقدّم.
    var editingPlan by remember(plan) { mutableStateOf(false) }

    // --- حالة نموذج الإنشاء (تُستخدم فقط عند غياب الخطة) ---
    var targetMode by remember { mutableStateOf(0) } // 0 = عدد أيام، 1 = تاريخ محدد
    var daysText by remember { mutableStateOf("30") }
    var datePickerOpen by remember { mutableStateOf(false) }
    var targetDateMillis by remember {
        // ق153: اليوم + 29 كتاريخ لا كلحظة — لحظة «الآن + 29 يوماً»
        // يفسّرها المنتقي بتوقيت UTC فيفتح على يوم أسبق أحياناً.
        mutableStateOf(
            LocalDate.now().plusDays(29).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
    }
    var startFromCurrent by remember { mutableStateOf(true) }

    val lastReadCompleted = lastRead?.let {
        it.surah == 114 &&
            it.ayah == (QuranDataProvider.surahs.firstOrNull { s -> s.id == 114 }?.versesCount ?: 6)
    } == true
    val canStartFromCurrent = lastRead != null && !lastReadCompleted
    val effectiveStartFromCurrent = startFromCurrent && canStartFromCurrent
    val startSurah = if (effectiveStartFromCurrent) lastRead!!.surah else 1
    val startAyah = if (effectiveStartFromCurrent) lastRead!!.ayah else 1
    val startPage = if (pageMapReady) {
        MushafPageData.getPageForAyah(startSurah, startAyah)
            .takeIf { it in 1..MushafPageData.PAGE_COUNT }
    } else {
        lastRead?.page
    }
    val todayStart = startOfMushafDay(System.currentTimeMillis())
    val targetDays = when (targetMode) {
        0 -> daysText.toIntOrNull()
        // ق153: فرق تقويمي بين تاريخ اليوم والتاريخ المنتقى (لا قسمة
        // مللي ثوانٍ) — وإلا نقص يوم غرب UTC وعبر التوقيت الصيفي.
        else -> (ChronoUnit.DAYS.between(
            mushafLocalDateOf(todayStart),
            mushafPickedLocalDate(targetDateMillis)
        ).toInt() + 1)
    }
    val daysValid = targetDays != null && targetDays in 1..3650
    val formUnitWord = if (pageMapReady && startPage != null) "صفحة" else "آية"
    val formTotalUnits = if (pageMapReady && startPage != null) {
        MushafPageData.PAGE_COUNT - startPage + 1
    } else {
        MUSHAF_KHATMAH_TOTAL_AYAH_COUNT - UserPreferencesManager.globalAyahIndex(startSurah, startAyah) + 1
    }
    val formSuggestedDaily = if (daysValid) {
        (formTotalUnits + targetDays!! - 1) / targetDays
    } else {
        0
    }

    val effectivePage = lastRead?.page ?: currentPage
    val progress = plan?.let {
        computeMushafKhatmahProgress(
            plan = it,
            currentSurah = lastRead?.surah ?: it.startSurah,
            currentAyah = lastRead?.ayah ?: it.startAyah,
            currentPage = effectivePage,
            pageMapReady = pageMapReady,
            nowMillis = System.currentTimeMillis()
        )
    }

    // في وضع التعديل: المقترح من المتبقي الفعلي والأيام المتبقية
    // من الهدف الجديد، لا من إجمالي الخطة.
    val editSuggestedDaily = if (editingPlan && plan != null && progress != null && daysValid) {
        val remainingDays = (targetDays!! - progress.daysElapsed + 1).coerceAtLeast(1)
        (progress.leftUnits + remainingDays - 1) / remainingDays
    } else {
        0
    }

    if (datePickerOpen) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = targetDateMillis)
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { targetDateMillis = it }
                    datePickerOpen = false
                }) {
                    Text(
                        text = Translator.tr("اختيار"),
                        color = theme.primary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { datePickerOpen = false }) {
                    Text(
                        text = Translator.tr("إلغاء"),
                        color = theme.primary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.cardSurface,
        shape = RoundedCornerShape(18.dp),
        title = {
            Text(
                text = Translator.tr("مخطط الختمة"),
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showCreateForm || plan == null) {
                    item {
                        Text(
                            text = Translator.tr(
                                if (editingPlan) "غيّر عدد أيام الخطة؛ تبقى بدايتها وتقدّمك كما هما."
                                else "اختر هدفك وبدايتك؛ التقدّم يُحسب من قراءتك الفعلية فقط، لا من زر تأكيد يدوي."
                            ),
                            color = theme.textSecondary,
                            fontFamily = CairoFont,
                            fontSize = 12.5.sp,
                            lineHeight = 20.sp
                        )
                    }
                    // نوع الهدف: عدد أيام أو تاريخ محدد — لا يظهر في وضع التعديل (الهدف أيام فقط).
                    if (!editingPlan) {
                        item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("عدد الأيام" to 0, "تاريخ محدد" to 1).forEach { (label, mode) ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (targetMode == mode) theme.primary else theme.background
                                        )
                                        .clickable { targetMode = mode }
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = Translator.tr(label),
                                        color = if (targetMode == mode) theme.background else theme.textPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = if (targetMode == mode) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.5.sp
                                    )
                                }
                            }
                        }
                        }
                    }
                    if (targetMode == 0) {
                        item {
                            OutlinedTextField(
                                value = daysText,
                                onValueChange = { daysText = it.filter { ch -> ch.isDigit() }.take(4) },
                                label = {
                                    Text(
                                        text = Translator.tr("عدد الأيام"),
                                        fontFamily = CairoFont,
                                        fontSize = 12.sp
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = theme.primary,
                                    unfocusedBorderColor = theme.textSecondary.copy(alpha = 0.4f),
                                    focusedTextColor = theme.textPrimary,
                                    unfocusedTextColor = theme.textPrimary,
                                    cursorColor = theme.primary,
                                    focusedLabelColor = theme.primary,
                                    unfocusedLabelColor = theme.textSecondary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(7, 30, 90, 180, 365).forEach { days ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(
                                                if (daysText == days.toString()) theme.primary.copy(alpha = 0.2f)
                                                else theme.background
                                            )
                                            .clickable { daysText = days.toString() }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$days",
                                            color = theme.textPrimary,
                                            fontFamily = NotoSansFont,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(theme.background)
                                    .clickable { datePickerOpen = true }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = Translator.tr("تاريخ الانتهاء: ") +
                                        formatMushafKhatmahDate(
                                            // ق153: التاريخ المنتقى لا اللحظة UTC —
                                            // وإلا عُرض يوم أسبق غرب UTC.
                                            mushafStartOfLocalDate(mushafPickedLocalDate(targetDateMillis))
                                        ),
                                    color = theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        if (daysValid) {
                            item {
                                Text(
                                    text = Translator.trf("أي {0} يوماً من اليوم.", targetDays),
                                    color = theme.textSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                    // نقطة البداية: من حيث توقفت أو من أول المصحف — لا تظهر في وضع التعديل (البداية ثابتة).
                    if (!editingPlan) {
                        item {
                        Text(
                            text = Translator.tr("نقطة البداية:"),
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (effectiveStartFromCurrent) theme.primary.copy(alpha = 0.2f)
                                        else theme.background
                                    )
                                    .clickable(enabled = canStartFromCurrent) {
                                        startFromCurrent = true
                                    }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (canStartFromCurrent) {
                                        Translator.tr("من حيث توقفت: ") +
                                            "سورة ${QuranDataProvider.surahNameOf(lastRead!!.surah)}" +
                                            " • آية ${lastRead.ayah}" +
                                            (lastRead.page?.let { " • صفحة $it" } ?: "")
                                    } else if (lastReadCompleted) {
                                        Translator.tr("أتممت المصحف — ابدأ من أوله")
                                    } else {
                                        Translator.tr("من حيث توقفت (لا قراءة محفوظة بعد)")
                                    },
                                    color = if (canStartFromCurrent) theme.textPrimary else theme.textSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (!effectiveStartFromCurrent) theme.primary.copy(alpha = 0.2f)
                                        else theme.background
                                    )
                                    .clickable { startFromCurrent = false }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = Translator.tr("من أول المصحف (صفحة 1)"),
                                    color = theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        }
                    }
                    // معاينة حيّة من البيانات الحقيقية.
                    if (daysValid) {
                        item {
                            Text(
                                text = if (editingPlan && progress != null) {
                                    Translator.tr("المتبقي: ") +
                                        "${progress.leftUnits} ${progress.unitWord}" +
                                        Translator.tr(" — نحو ") +
                                        "$editSuggestedDaily ${progress.unitWord}" +
                                        Translator.tr(" يومياً في الأيام المتبقية.")
                                } else {
                                    Translator.tr("المتبقي من المصحف: ") +
                                        "$formTotalUnits $formUnitWord" +
                                        Translator.tr(" — نحو ") +
                                        "$formSuggestedDaily $formUnitWord" +
                                        Translator.tr(" يومياً.")
                                },
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                lineHeight = 21.sp,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                    } else {
                        item {
                            Text(
                                text = if (targetMode == 0) {
                                    Translator.tr("أدخل عدد أيام بين 1 و 3650.")
                                } else {
                                    Translator.tr("اختر تاريخ اليوم أو بعده.")
                                },
                                color = Color(0xFFEF4444),
                                fontFamily = CairoFont,
                                fontSize = 12.5.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                } else if (progress != null) {
                    // --- عرض حالة الخطة القائمة ---
                    if (progress.completed) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = theme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = Translator.tr("أتممت الختمة — تقبّل الله منك"),
                                    color = theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        item {
                            Text(
                                text = Translator.trf("بدأت خطتك في {0}.", formatMushafKhatmahDate(plan!!.startMillis)),
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 12.5.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    } else {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 6.dp)
                            ) {
                                Text(
                                    text = "${(progress.fraction * 100).toInt()}%",
                                    color = theme.primary,
                                    fontFamily = NotoSansFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                LinearProgressIndicator(
                                    progress = { progress.fraction },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = theme.primary,
                                    trackColor = theme.background
                                )
                            }
                        }
                        item {
                            Text(
                                text = Translator.tr("قرأت ") +
                                    "${progress.readUnits} ${progress.unitWord}" +
                                    Translator.tr(" من أصل ") +
                                    "${progress.totalUnits} ${progress.unitWord}" +
                                    Translator.tr(" — المتبقي ") +
                                    "${progress.leftUnits} ${progress.unitWord}" +
                                    Translator.tr(" في ") +
                                    "${progress.daysLeft}" +
                                    Translator.tr(" يوم."),
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontSize = 13.sp,
                                lineHeight = 21.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        item {
                            Text(
                                text = Translator.tr("ورد اليوم: ") +
                                    (if (progress.todayStartPage != null) {
                                        "من صفحة ${progress.todayStartPage} إلى صفحة ${progress.todayEndPage}"
                                    } else {
                                        "من سورة ${QuranDataProvider.surahNameOf(progress.todayStart.first)} آية ${progress.todayStart.second}" +
                                            " إلى سورة ${QuranDataProvider.surahNameOf(progress.todayEnd.first)} آية ${progress.todayEnd.second}"
                                    }) +
                                    " (" + "${progress.suggestedDailyUnits} ${progress.unitWord}" + ")",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                lineHeight = 21.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        item {
                            Text(
                                text = Translator.tr("اليوم ") +
                                    "${progress.daysElapsed}" +
                                    Translator.tr(" من ") +
                                    "${plan!!.targetDays}" +
                                    Translator.tr(" • بدأت في ") +
                                    formatMushafKhatmahDate(plan!!.startMillis) +
                                    Translator.tr(" • تنتهي في ") +
                                    formatMushafKhatmahDate(
                                        // ق153: نهاية تقويمية لا «بداية + أيام × 24 ساعة» —
                                        // فالضرب يسبق يوماً عبر تغيير التوقيت الصيفي.
                                        mushafStartOfLocalDate(
                                            mushafLocalDateOf(plan!!.startMillis)
                                                .plusDays((plan!!.targetDays - 1).toLong())
                                        )
                                    ) +
                                    ".",
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 12.5.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        if (progress.overdue) {
                            item {
                                Text(
                                    text = Translator.trf("انتهت المدة وبقي {0} {1} — المقترح الجديد: {2} {3} يومياً لإنهائها.", progress.leftUnits, progress.unitWord, progress.suggestedDailyUnits, progress.unitWord),
                                    color = Color(0xFFEF4444),
                                    fontFamily = CairoFont,
                                    fontSize = 12.5.sp,
                                    lineHeight = 20.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        if (!pageMapReady) {
                            item {
                                Text(
                                    text = Translator.tr("خريطة الصفحات غير جاهزة — التقدّم بعدد الآيات؛ يظهر بالصفحات بعد تحميلها."),
                                    color = theme.textSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp,
                                    lineHeight = 19.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                showCreateForm || plan == null -> {
                    TextButton(
                        onClick = {
                            if (!daysValid) return@TextButton
                            if (editingPlan && plan != null) {
                                // تعديل الهدف فقط — البداية وتاريخها يبقيان كما هما.
                                onSavePlan(plan!!.copy(targetDays = targetDays!!))
                            } else {
                                onSavePlan(
                                    UserPreferencesManager.MushafKhatmahPlan(
                                        startSurah = startSurah,
                                        startAyah = startAyah,
                                        startPage = startPage,
                                        startMillis = todayStart,
                                        targetDays = targetDays!!,
                                        createdAtMillis = System.currentTimeMillis()
                                    )
                                )
                            }
                        },
                        enabled = daysValid
                    ) {
                        Text(
                            text = Translator.tr(if (editingPlan) "حفظ التعديل" else "بدء الخطة"),
                            color = theme.primary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
                progress?.completed == true -> {
                    TextButton(onClick = { showCreateForm = true }) {
                        Text(
                            text = Translator.tr("ابدأ ختمة جديدة"),
                            color = theme.primary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
                progress != null -> {
                    TextButton(onClick = { onJumpTo(progress.todayStart.first, progress.todayStart.second) }) {
                        Text(
                            text = Translator.tr("الانتقال لورد اليوم"),
                            color = theme.primary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        },
        dismissButton = {
            Row {
                if (!showCreateForm && plan != null) {
                    if (progress?.completed != true) {
                        TextButton(onClick = {
                            daysText = plan!!.targetDays.toString()
                            targetMode = 0
                            editingPlan = true
                            showCreateForm = true
                        }) {
                            Text(
                                text = Translator.tr("تعديل الهدف"),
                                color = theme.primary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                    TextButton(onClick = {
                        if (confirmClear) {
                            onClearPlan()
                        } else {
                            confirmClear = true
                        }
                    }) {
                        Text(
                            text = if (confirmClear) {
                                Translator.tr("تأكيد إنهاء الخطة؟")
                            } else {
                                Translator.tr("إنهاء الخطة")
                            },
                            color = Color(0xFFEF4444),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        text = Translator.tr("إغلاق"),
                        color = theme.primary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    )
}

// ============================================================================
// حوارات عرض المصحف (خارطة المصحف — المرحلة 5): السمة والصفحتان
// والتجويد والرسم. كل خيار فيها يعمل فعلاً — لا خيار شكلي.
// ============================================================================

@Composable
private fun MushafDisplaySettingsDialog(
    currentThemeKey: String,
    onThemeSelected: (String) -> Unit,
    twoPageSpread: Boolean,
    onSpreadChanged: (Boolean) -> Unit,
    showTajweed: Boolean,
    onTajweedChanged: (Boolean) -> Unit,
    script: String,
    onScriptChanged: (String) -> Unit,
    onShowTajweedLegend: () -> Unit,
    onOpenLibrary: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = QabasTheme.current
    val tajweedAvailable = script == "uthmani"
    val themeOptions = listOf("dark_luxury", "sepia_warm", "oled_black", "light_clean")
    val scriptOptions = listOf(
        Triple("uthmani", "عثماني (الرسم الأصلي)", "نص التطبيق المحلي — يعمل دون اتصال"),
        Triple("uthmani_simple", "عثماني مبسّط", "بلا تشكيل — يُجلب عند الطلب"),
        Triple("imlaei", "إملائي", "بالرسم الإملائي — يُجلب عند الطلب")
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = Translator.tr("إعدادات عرض المصحف"),
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    MushafStudySectionTitle(Translator.tr("سمة القراءة"))
                }
                items(themeOptions, key = { it }) { key ->
                    val palette = UserPreferencesManager.getResolvedReadingThemePalette(key)
                    val isSelected = key == currentThemeKey
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) theme.primary.copy(alpha = 0.12f) else theme.cardSurface)
                            .clickable { onThemeSelected(key) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(palette.background)
                                .border(1.dp, palette.accentColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(palette.cardBackground)
                                .border(1.dp, palette.accentColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(palette.accentColor)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = palette.name,
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    MushafStudySectionTitle(Translator.tr("العرض"))
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(theme.cardSurface)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = Translator.tr("صفحتان جنباً إلى جنب"),
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = Translator.tr("يعمل في الوضع الأفقي فقط"),
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 11.5.sp
                            )
                        }
                        Switch(
                            checked = twoPageSpread,
                            onCheckedChange = onSpreadChanged
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(theme.cardSurface)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = Translator.tr("ألوان التجويد"),
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = Translator.tr(
                                    if (tajweedAvailable) "أحكام التجويد بألوانها — يتطلب اتصالاً"
                                    else "متاح مع الرسم العثماني فقط"
                                ),
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 11.5.sp
                            )
                        }
                        Switch(
                            checked = showTajweed,
                            onCheckedChange = onTajweedChanged,
                            enabled = tajweedAvailable
                        )
                    }
                }
                item {
                    TextButton(onClick = onShowTajweedLegend) {
                        Text(
                            text = Translator.tr("دليل ألوان التجويد"),
                            color = theme.primary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(theme.cardSurface)
                            .clickable { onOpenLibrary() }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LibraryBooks,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = Translator.tr("مكتبة الكتب والترجمات"),
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = Translator.tr("كتب التفسير والإعراب وأسباب النزول والمتشابهات والترجمات"),
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 11.5.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = null,
                            tint = theme.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    MushafStudySectionTitle(Translator.tr("الرسم والطبعة"))
                }
                items(scriptOptions, key = { it.first }) { (key, label, subtitle) ->
                    val isSelected = key == script
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) theme.primary.copy(alpha = 0.12f) else theme.cardSurface)
                            .clickable { onScriptChanged(key) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = subtitle,
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 11.5.sp
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = Translator.tr(
                            "النص: مصحف المدينة المنورة — رواية حفص عن عاصم " +
                                "(طبعة مجمع الملك فهد). لا رواية أخرى متاحة في التطبيق."
                        ),
                        color = theme.textSecondary,
                        fontFamily = CairoFont,
                        fontSize = 11.5.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = Translator.tr("إغلاق"),
                    color = theme.primary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    )
}

// ============================================================================
// ورقة مكتبة الكتب والترجمات (قسم القرآن — الكتب).
//
// ورقة سفلية (ModalBottomSheet) على نمط أوراق المشروع القائمة
// (AudioPlaylistBottomSheet) وبروح خادم المسلم الداكنة NEO COSMIC — رموز سمة
// التطبيق (QabasTheme) لا ألوان المرجع؛ والهيكل فقط مأخوذ من اللقطة
// المرجعية (ورقة + صفوف فئات + فتح كتب الفئة) دون نسخ شكلها
// (لا بطاقات بيضاء على بيج، ولا أيقونات كتب ملوّنة منسوخة).
// الفئات الموثّقة فقط: التفسير (8 كتب)، أسباب النزول (مصدر واحد)،
// الإعراب (كتابان)، المتشابهات اللفظية (فهرس)، التجويد (دليل التطبيق
// القائم — 16 قاعدة + كتاب الوجيز الكامل)، الترجمات (3)، الفوائد
// (كتاب كامل 1727)، البلاغة (قسم الدرويش للآية + كتاب 439 الكامل).
// لا صف للصرف (تدقيق قانوني معلّق). صف «إضافة كتب أخرى» يعرض
// القرطبي/البغوي/الوسيط — عيناتها 1:1 فُحصت حياً (HTTP 200 + نص حقيقي).
// الشارات صادقة: "دون اتصال" للميسر وحده (مضمّن في APK)، و"شبكي" لغيره.
// النقر على كتاب يحفظه افتراضياً ويعلّمه مختاراً؛ ومصدرا الأسباب
// والمتشابهات بطاقتا معلومات (بلا اختيار — لا كتاب افتراضي لهما).
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MushafBooksLibrarySheet(
    currentSurah: Int,
    currentAyah: Int,
    onDismiss: () -> Unit,
    onTranslationSelected: (String) -> Unit,
    onOpenTajweedLegend: () -> Unit,
    onOpenStudyVerse: (Int, Int) -> Unit
) {
    val theme = QabasTheme.current
    val context = LocalContext.current
    var openCategory by remember { mutableStateOf<String?>(null) }
    var openBook by remember { mutableStateOf<QuranDataProvider.QuranPediaBookInfo?>(null) }
    var selectedTafsir by remember { mutableStateOf(UserPreferencesManager.getMushafTafsirBook(context)) }
    var selectedIrab by remember { mutableStateOf(UserPreferencesManager.getMushafIrabBook(context)) }
    var selectedTranslation by remember { mutableStateOf(UserPreferencesManager.getMushafTranslation(context)) }
    // الكتب الكاملة الثلاثة من quranpedia (بطاقات وصفية فقط، بلا نص).
    val fawaidBook = QuranDataProvider.MUSHAF_QURANPEDIA_BOOKS.firstOrNull { it.id == 1727 }
    val balaghaBook = QuranDataProvider.MUSHAF_QURANPEDIA_BOOKS.firstOrNull { it.id == 439 }
    val tajweedBook = QuranDataProvider.MUSHAF_QURANPEDIA_BOOKS.firstOrNull { it.id == 89 }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = theme.background,
        contentColor = theme.textPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp),
                shape = CircleShape,
                color = theme.separator.copy(alpha = 0.7f)
            ) {}
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .heightIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = Translator.tr("كتب التفسير والترجمات"),
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = Translator.tr("كتب موثّقة المصدر — «دون اتصال» للميسر وحده، والباقي شبكي"),
                    color = theme.textSecondary,
                    fontFamily = CairoFont,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (openCategory == null) {
                item {
                    MushafLibraryCategoryRow(
                        icon = Icons.Default.MenuBook,
                        tint = theme.primary,
                        title = "التفسير",
                        subtitle = "8 كتب — الميسر دون اتصال والباقي شبكي",
                        onClick = { openCategory = "tafsir" }
                    )
                }
                item {
                    MushafLibraryCategoryRow(
                        icon = Icons.Default.History,
                        tint = theme.accent,
                        title = "أسباب النزول",
                        subtitle = "«صحيح أسباب النزول» — 50 سورة فقط",
                        onClick = { openCategory = "asbab" }
                    )
                }
                item {
                    MushafLibraryCategoryRow(
                        icon = Icons.Default.Edit,
                        tint = theme.success,
                        title = "الإعراب",
                        subtitle = "كتابان — الميسر والدرويش",
                        onClick = { openCategory = "irab" }
                    )
                }
                item {
                    MushafLibraryCategoryRow(
                        icon = Icons.Default.CompareArrows,
                        tint = theme.warning,
                        title = "المتشابهات اللفظية",
                        subtitle = "فهرس — 1,348 موضعاً",
                        onClick = { openCategory = "mutashabihat" }
                    )
                }
                item {
                    MushafLibraryCategoryRow(
                        icon = Icons.Default.Lightbulb,
                        tint = theme.warning,
                        title = "الفوائد",
                        subtitle = "«فوائد منتقاة من تفسير ابن كثير» — كتاب كامل",
                        onClick = { openCategory = "fawaid" }
                    )
                }
                item {
                    MushafLibraryCategoryRow(
                        icon = Icons.Default.AutoStories,
                        tint = theme.primary,
                        title = "البلاغة",
                        subtitle = "قسم البلاغة للآية + كتاب «من بلاغة القرآن»",
                        onClick = { openCategory = "balagha" }
                    )
                }
                item {
                    MushafLibraryCategoryRow(
                        icon = Icons.Default.Palette,
                        tint = theme.primary,
                        title = "التجويد",
                        subtitle = "دليل ألوان التجويد — 16 قاعدة",
                        onClick = { openCategory = "tajweed" }
                    )
                }
                item {
                    MushafLibraryCategoryRow(
                        icon = Icons.Default.Translate,
                        tint = theme.accent,
                        title = "الترجمات",
                        subtitle = "3 ترجمات إنجليزية — شبكية",
                        onClick = { openCategory = "translations" }
                    )
                }
                item {
                    MushafLibraryCategoryRow(
                        icon = Icons.Default.Add,
                        tint = theme.success,
                        title = "إضافة كتب أخرى +",
                        subtitle = "القرطبي • البغوي • الوسيط — تحققت عيناتها حياً",
                        onClick = { openCategory = "more-books" }
                    )
                }
            } else {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { openCategory = null }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = Translator.tr("رجوع إلى الفئات"),
                            color = theme.primary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
                when (openCategory) {
                    "tafsir" -> {
                        item {
                            MushafStudySectionTitle(Translator.tr("كتب التفسير"))
                        }
                        items(QuranDataProvider.MUSHAF_TAFSIR_BOOKS, key = { it.id }) { book ->
                            MushafLibraryBookRow(
                                name = book.arabicName,
                                detail = "${book.author} • ${book.source}",
                                badge = if (book.offline) "دون اتصال" else "شبكي",
                                isSelected = book.id == selectedTafsir,
                                onClick = {
                                    UserPreferencesManager.saveMushafTafsirBook(context, book.id)
                                    selectedTafsir = book.id
                                }
                            )
                        }
                    }
                    "asbab" -> {
                        item {
                            MushafLibraryInfoCard(
                                title = "صحيح أسباب النزول",
                                lines = listOf(
                                    "المؤلف: إبراهيم محمد العلي",
                                    "المصدر: مستودع asbab-al-nuzul-dataset (MIT)",
                                    "التغطية: 50 سورة فقط — السور الأخرى بلا ملف في المصدر",
                                    "يُعرض في لوحة دراسة الآية عند الضغط على آية."
                                )
                            )
                        }
                    }
                    "irab" -> {
                        item {
                            MushafStudySectionTitle(Translator.tr("كتب الإعراب"))
                        }
                        items(
                            listOf(
                                Triple("muyassar", "الإعراب الميسر", "spa5k/tafsir_api (MIT)"),
                                Triple("darwish", "إعراب القرآن للدرويش", "spa5k/tafsir_api (MIT)")
                            ),
                            key = { it.first }
                        ) { (bookId, name, source) ->
                            MushafLibraryBookRow(
                                name = name,
                                detail = source,
                                badge = "شبكي",
                                isSelected = bookId == selectedIrab,
                                onClick = {
                                    UserPreferencesManager.saveMushafIrabBook(context, bookId)
                                    selectedIrab = bookId
                                }
                            )
                        }
                    }
                    "mutashabihat" -> {
                        item {
                            MushafLibraryInfoCard(
                                title = "فهرس المتشابهات اللفظية",
                                lines = listOf(
                                    "المصدر: Quran_Mutashabihat_Data (CC0)",
                                    "1,348 موضعاً — ليست قائمة شاملة، بل الأكثر إرباكاً للحفّاظ",
                                    "لا تغطي العالميات 6,201–6,236",
                                    "تُعرض في لوحة دراسة الآية عند الضغط على آية."
                                )
                            )
                        }
                    }
                    "fawaid" -> {
                        item {
                            MushafStudySectionTitle(Translator.tr("الفوائد"))
                        }
                        item {
                            MushafLibraryInfoCard(
                                title = "كتاب كامل — لا محتوى لكل آية",
                                lines = listOf(
                                    "«فوائد منتقاة من تفسير ابن كثير» — عبد الله زقيل",
                                    "المصدر: quranpedia.net — يُجلب شبكياً عند الطلب فقط",
                                    "لا يُضمَّن نص الكتاب في التطبيق؛ يُقرأ فصلاً فصلاً."
                                )
                            )
                        }
                        fawaidBook?.let { book ->
                            item {
                                MushafLibraryBookRow(
                                    name = book.arabicTitle,
                                    detail = "${book.author} • ${book.source}",
                                    badge = "شبكي",
                                    isSelected = false,
                                    onClick = { openBook = book }
                                )
                            }
                        }
                    }
                    "balagha" -> {
                        item {
                            MushafStudySectionTitle(Translator.tr("البلاغة"))
                        }
                        item {
                            MushafLibraryCategoryRow(
                                icon = Icons.Default.AutoStories,
                                tint = theme.primary,
                                title = "قسم البلاغة للآية الحالية",
                                subtitle = "سورة ${QuranDataProvider.surahNameOf(currentSurah)}" +
                                    " • آية $currentAyah — من كتاب الدرويش",
                                onClick = { onOpenStudyVerse(currentSurah, currentAyah) }
                            )
                        }
                        item {
                            MushafLibraryInfoCard(
                                title = "تنبيه صادق",
                                lines = listOf(
                                    "قسم البلاغة للآية من كتاب «إعراب القرآن وبيانه» للدرويش — لا كتاب بلاغة منفصل.",
                                    "الكتاب أدناه كتاب بلاغة كامل مستقل يُقرأ فصلاً فصلاً."
                                )
                            )
                        }
                        balaghaBook?.let { book ->
                            item {
                                MushafLibraryBookRow(
                                    name = book.arabicTitle,
                                    detail = "${book.author} • ${book.source}",
                                    badge = "شبكي",
                                    isSelected = false,
                                    onClick = { openBook = book }
                                )
                            }
                        }
                    }
                    "tajweed" -> {
                        item {
                            MushafLibraryCategoryRow(
                                icon = Icons.Default.Palette,
                                tint = theme.primary,
                                title = "دليل ألوان التجويد",
                                subtitle = "16 قاعدة — أحكام التجويد بألوانها",
                                onClick = onOpenTajweedLegend
                            )
                        }
                        tajweedBook?.let { book ->
                            item {
                                MushafLibraryBookRow(
                                    name = book.arabicTitle,
                                    detail = "${book.author} • ${book.source}",
                                    badge = "شبكي",
                                    isSelected = false,
                                    onClick = { openBook = book }
                                )
                            }
                        }
                    }
                    "translations" -> {
                        item {
                            MushafStudySectionTitle(Translator.tr("الترجمات الإنجليزية"))
                        }
                        items(QuranDataProvider.MUSHAF_TRANSLATIONS, key = { it.id }) { translation ->
                            MushafLibraryBookRow(
                                name = "${translation.arabicName} — ${translation.label}",
                                detail = translation.source,
                                badge = "شبكي",
                                isSelected = translation.id == selectedTranslation,
                                onClick = {
                                    selectedTranslation = translation.id
                                    onTranslationSelected(translation.id)
                                }
                            )
                        }
                    }
                    "more-books" -> {
                        item {
                            MushafStudySectionTitle(Translator.tr("كتب تفسير إضافية"))
                        }
                        item {
                            MushafLibraryInfoCard(
                                title = "كتب تحققت عيناتها حياً فقط",
                                lines = listOf(
                                    "كل كتاب أدناه فُحصت عينته للآية 1:1 (HTTP 200 + نص حقيقي).",
                                    "الاختيار يعيّن الكتاب كتابَ التفسير للوحة الدراسة."
                                )
                            )
                        }
                        items(QuranDataProvider.MUSHAF_EXTRA_TAFSIR_BOOKS, key = { it.id }) { book ->
                            MushafLibraryBookRow(
                                name = book.arabicName,
                                detail = "${book.author} • ${book.source}",
                                badge = "شبكي",
                                isSelected = book.id == selectedTafsir,
                                onClick = {
                                    UserPreferencesManager.saveMushafTafsirBook(context, book.id)
                                    selectedTafsir = book.id
                                }
                            )
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
    // قارئ الكتب الكاملة: حوار فوق الورقة، يُفتح من فئات الفوائد
    // والبلاغة والتجويد؛ ويُغلق بالرجوع إلى الورقة.
    openBook?.let { book ->
        MushafBookReaderDialog(
            book = book,
            onDismiss = { openBook = null }
        )
    }
}

/** صف فئة في ورقة المكتبة: أيقونة مادية بلون من السمة + عنوان + سهم. */
@Composable
private fun MushafLibraryCategoryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val theme = QabasTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.cardSurface)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = subtitle,
                color = theme.textSecondary,
                fontFamily = CairoFont,
                fontSize = 11.5.sp
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronLeft,
            contentDescription = null,
            tint = theme.textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** صف كتاب في ورقة المكتبة: الاسم والتفاصيل وشارة الاتصال وعلامة المختار. */
@Composable
private fun MushafLibraryBookRow(
    name: String,
    detail: String,
    badge: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val theme = QabasTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) theme.primary.copy(alpha = 0.12f) else theme.cardSurface
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp
            )
            Text(
                text = detail,
                color = theme.textSecondary,
                fontFamily = CairoFont,
                fontSize = 11.5.sp,
                lineHeight = 17.sp
            )
            Text(
                text = badge,
                color = if (badge == "دون اتصال") theme.success else theme.textSecondary,
                fontFamily = CairoFont,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = theme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** بطاقة معلومات لمصدر بلا كتاب افتراضي (الأسباب والمتشابهات): عرض فقط. */
@Composable
private fun MushafLibraryInfoCard(
    title: String,
    lines: List<String>
) {
    val theme = QabasTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.cardSurface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            color = theme.textPrimary,
            fontFamily = CairoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp
        )
        lines.forEach { line ->
            Text(
                text = line,
                color = theme.textSecondary,
                fontFamily = CairoFont,
                fontSize = 12.sp,
                lineHeight = 19.sp
            )
        }
    }
}

/**
 * قارئ الكتب الكاملة من quranpedia (الفوائد/البلاغة/التجويد): يُجلب
 * الكتاب شبكياً عند الطلب فقط — بلا تضمين لنصه في التطبيق — ثم تنقّل
 * ثلاثي المستويات: فصول ← مقاطع الفصل ← نص المقطع. حالات تحميل
 * وخطأ وفراغ صريحة، وتسمية «المصدر: quranpedia.net — العنوان — المؤلف»
 * على مستوى النص. نصوص المقاطع تُعرض بعد نزع وسوم HTML وحدها.
 */
@Composable
private fun MushafBookReaderDialog(
    book: QuranDataProvider.QuranPediaBookInfo,
    onDismiss: () -> Unit
) {
    val theme = QabasTheme.current
    var bookData by remember(book.id) { mutableStateOf<QuranDataProvider.QuranPediaBook?>(null) }
    var bookLoading by remember(book.id) { mutableStateOf(true) }
    var bookFailed by remember(book.id) { mutableStateOf(false) }
    var selectedChapterId by remember(book.id) { mutableStateOf<Int?>(null) }
    var selectedContentId by remember(book.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(book.id) {
        bookLoading = true
        bookFailed = false
        bookData = null
        selectedChapterId = null
        selectedContentId = null
        val fetched = QuranDataProvider.fetchQuranPediaBook(book.id)
        bookData = fetched
        if (fetched == null) bookFailed = true
        bookLoading = false
    }

    val data = bookData
    val selectedChapter = data?.chapters?.firstOrNull { it.id == selectedChapterId }
    val chapterContents = selectedChapter?.let { chapter ->
        data?.contents?.filter { it.chapterId == chapter.id }
    }.orEmpty()
    val selectedContent = chapterContents.firstOrNull { it.id == selectedContentId }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.cardSurface,
        shape = RoundedCornerShape(18.dp),
        title = {
            Text(
                text = book.arabicTitle,
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when {
                    bookLoading -> {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = theme.primary,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = Translator.tr("جارٍ تحميل الكتاب…"),
                                    color = theme.textSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }
                    bookFailed || data == null -> {
                        item {
                            Text(
                                text = Translator.tr("تعذّر تحميل الكتاب — تحقق من الاتصال وحاول مجدداً."),
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 12.5.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                    selectedContent != null -> {
                        item {
                            MushafBookReaderBackRow(
                                label = Translator.tr("رجوع إلى مقاطع الفصل"),
                                onClick = { selectedContentId = null }
                            )
                        }
                        item {
                            Text(
                                text = selectedContent.text,
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontSize = 13.sp,
                                lineHeight = 21.sp
                            )
                            MushafStudySectionCaption(
                                Translator.trf("المصدر: quranpedia.net — {0} — {1}", book.arabicTitle, book.author)
                            )
                        }
                    }
                    selectedChapter != null -> {
                        item {
                            MushafBookReaderBackRow(
                                label = Translator.tr("رجوع إلى الفصول"),
                                onClick = {
                                    selectedChapterId = null
                                    selectedContentId = null
                                }
                            )
                        }
                        item {
                            MushafStudySectionTitle(selectedChapter.name)
                        }
                        if (chapterContents.isEmpty()) {
                            item {
                                Text(
                                    text = Translator.tr("لا توجد مقاطع في هذا الفصل."),
                                    color = theme.textSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 12.5.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        } else {
                            items(chapterContents, key = { it.id }) { content ->
                                val position = chapterContents.indexOf(content) + 1
                                val preview = content.text
                                    .lineSequence()
                                    .firstOrNull { it.isNotBlank() }
                                    ?.trim()
                                    .orEmpty()
                                MushafBookReaderRow(
                                    title = if (preview.length > 120) {
                                        preview.take(120) + "…"
                                    } else {
                                        preview.ifBlank { Translator.tr("مقطع بلا نص ظاهر") }
                                    },
                                    subtitle = "مقطع $position من ${chapterContents.size}",
                                    onClick = { selectedContentId = content.id }
                                )
                            }
                        }
                    }
                    else -> {
                        item {
                            MushafStudySectionTitle(Translator.tr("فصول الكتاب"))
                        }
                        items(data.chapters, key = { it.id }) { chapter ->
                            val count = data.contents.count { it.chapterId == chapter.id }
                            MushafBookReaderRow(
                                title = chapter.name,
                                subtitle = "$count مقطعاً",
                                onClick = {
                                    selectedChapterId = chapter.id
                                    selectedContentId = null
                                }
                            )
                        }
                        item {
                            MushafStudySectionCaption(
                                Translator.trf("المصدر: quranpedia.net — {0} — {1}؛ كتاب كامل يُجلب شبكياً عند الطلب ولا يُضمَّن نصه في التطبيق. تجميع المقاطع في الفصول يتبع ترتيب الكتاب حيث لا يسمّي المصدر الفصل بدقة.", book.arabicTitle, book.author)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = Translator.tr("إغلاق"),
                    color = theme.primary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

/** صف رجوع في قارئ الكتب: سهم + تسمية، يعيد مستوى واحداً إلى الوراء. */
@Composable
private fun MushafBookReaderBackRow(
    label: String,
    onClick: () -> Unit
) {
    val theme = QabasTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = theme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = theme.primary,
            fontFamily = CairoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

/** صف فصل أو مقطع في قارئ الكتب: عنوان + سطر ثانوي، على خلفية الحوار. */
@Composable
private fun MushafBookReaderRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val theme = QabasTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.background)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = theme.textSecondary,
                fontFamily = CairoFont,
                fontSize = 11.5.sp
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronLeft,
            contentDescription = null,
            tint = theme.textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun MushafTajweedLegendDialog(
    tajweedColors: Map<String, Color>,
    onDismiss: () -> Unit
) {
    val theme = QabasTheme.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = Translator.tr("دليل ألوان التجويد"),
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    QuranDataProvider.TAJWEED_RULE_NAMES_AR.entries.toList(),
                    key = { it.key }
                ) { (ruleClass, nameAr) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    tajweedColors[ruleClass]
                                        ?: theme.textSecondary
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = nameAr,
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontSize = 13.5.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = Translator.tr("إغلاق"),
                    color = theme.primary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    )
}

// ============================================================================
// بطاقة آية المصحف (خارطة المصحف — المرحلة 5): مستخرجة من القائمة
// العمودية لتُستخدم في العرض المزدوج أيضاً بنفس السلوك تماماً —
// التمييز والكلمة الجارية والحفظ والعلامة والدراسة والترجمة.
// النص المعروض (تجويد/رسم/محلي) يُبنى عند الاستدعاء.
// ============================================================================

@Composable
private fun MushafVerseCard(
    ayah: Int,
    verseText: AnnotatedString,
    tafseer: String,
    translation: String?,
    showTranslation: Boolean,
    isPlayingVerse: Boolean,
    isHiddenVerse: Boolean,
    onVerseClick: () -> Unit,
    bookmark: UserPreferencesManager.MushafBookmark?,
    onBookmarkClick: () -> Unit,
    verseFontSize: Float,
    verseFontFamily: FontFamily,
    theme: QabasDesignTheme
) {
    AppleCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (isPlayingVerse) {
            theme.primary.copy(alpha = 0.12f)
        } else {
            theme.cardSurface
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPlayingVerse) theme.primary
                        else theme.primary.copy(alpha = 0.14f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ayah.toString(),
                    color = if (isPlayingVerse) theme.background else theme.primary,
                    fontFamily = NotoSansFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            // نقرة على نص الآية تفتح لوحة الدراسة (المرحلة 3)؛
            // أيقونة العلامة بجانبها مستقلة ولا تتأثر بالنقرة.
            // في وضع الحفظ (المرحلة 4) تتغير النقرة إلى
            // كشف/إخفاء الآية — والتفسير والترجمة مخفيان
            // مع النص حتى لا يكشفا الآية قبل تسميعها.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onVerseClick() }
            ) {
                if (isHiddenVerse) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = theme.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Translator.tr("آية مخفية للحفظ — اضغط لإظهارها"),
                            color = theme.textSecondary,
                            fontFamily = CairoFont,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Text(
                        text = verseText,
                        color = theme.textPrimary,
                        fontFamily = verseFontFamily,
                        fontSize = verseFontSize.sp,
                        lineHeight = (verseFontSize * 1.6f).sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (tafseer.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = tafseer,
                            color = theme.textSecondary,
                            fontFamily = CairoFont,
                            fontSize = 12.sp,
                            lineHeight = 19.sp,
                            maxLines = 3
                        )
                    }
                    // الترجمة الإنجليزية تحت الآية (المرحلة 3) —
                    // تظهر فقط عند تفعيلها ووصول ترجمة السورة.
                    if (showTranslation && translation != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = translation,
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 12.5.sp,
                            lineHeight = 19.sp,
                            textAlign = TextAlign.Left,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            // علامة الآية: نقرة تحفظها فوراً باللون الذهبي،
            // ونقرة العلامة المحفوظة تفتح محررها (ملاحظة/لون/حذف).
            IconButton(onClick = onBookmarkClick) {
                Icon(
                    imageVector = if (bookmark != null) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = if (bookmark != null) Translator.tr("تعديل العلامة") else Translator.tr("حفظ علامة"),
                    tint = if (bookmark != null) Color(bookmark.colorArgb) else theme.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * بداية زوج الصفحتين في العرض المزدوج: الصفحة الفردية المحيطة بصفحة
 * معطاة (الفردية نفسها، أو السابقة لها إن كانت زوجية)، مقيّدة
 * بالمجال 1..603 حتى يبقى للصفحة التالية مكان دائماً.
 */
private fun mushafSpreadStartPage(page: Int): Int {
    val clamped = page.coerceIn(1, 604)
    val odd = if (clamped % 2 == 0) clamped - 1 else clamped
    return odd.coerceIn(1, 603)
}
