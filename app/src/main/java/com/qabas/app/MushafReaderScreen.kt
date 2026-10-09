package com.qabas.app

/**
 * MushafReaderScreen.kt
 *
 * قارئ المصحف بالصفحات — تصميم أصلي لقبس (لا كود منقول من أي مشروع).
 * - واجهة عربية RTL، صفحة في المنتصف، سحب أفقي بين 604 صفحات (مصحف المدينة).
 * - شريط علوي: السورة + الجزء. شريط سفلي: رقم الصفحة + تنقل + علامة + اختيار.
 * - نافذة اختيار: سورة / جزء / صفحة / علاماتي.
 * - حفظ آخر صفحة + العلامات في "qabas_prefs" (نفس تخزين التطبيق).
 * - النصوص من `uthmani.json` المحلي حصراً — لا شبكة ولا مفاتيح.
 * - هيكل مفتوح لاحقاً: onOpenTafseer / onPlayAudio (اختياريان، يُمرَّران عند الجاهزية).
 */

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS = "qabas_prefs"
private const val KEY_LAST_PAGE = "last_read_page"
private const val KEY_BOOKMARKS = "mushaf_bookmarks"
private const val KEY_FONT_SIZE = "mushaf_font_size"

private fun easternDigits(n: Int): String {
    val eastern = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
    return n.toString().map { c -> if (c in '0'..'9') eastern[c - '0'] else c }.joinToString("")
}

private fun getJuzName(juz: Int): String {
    val names = listOf(
        "الأول", "الثاني", "الثالث", "الرابع", "الخامس", "السادس", "السابع", "الثامن", "التاسع", "العاشر",
        "الحادي عشر", "الثاني عشر", "الثالث عشر", "الرابع عشر", "الخامس عشر", "السادس عشر", "السابع عشر", "الثامن عشر", "التاسع عشر", "العشرون",
        "الحادي والعشرون", "الثاني والعشرون", "الثالث والعشرون", "الرابع والعشرون", "الخامس والعشرون", "السادس والعشرون", "السابع والعشرون", "الثامن والعشرون", "التاسع والعشرون", "الثلاثون"
    )
    return if (juz in 1..30) "الجزء ${names[juz - 1]}" else "الجزء ${easternDigits(juz)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MushafReaderScreen(
    onClose: () -> Unit,
    onOpenTafseer: (surahId: Int, ayah: Int) -> Unit = { _, _ -> },
    initialPage: Int? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    var dataReady by remember { mutableStateOf(false) }
    var bookmarks by remember {
        mutableStateOf(prefs.getStringSet(KEY_BOOKMARKS, emptySet())?.mapNotNull { it.toIntOrNull() }?.sorted() ?: emptyList())
    }
    var fontScale by remember { mutableFloatStateOf(prefs.getFloat(KEY_FONT_SIZE, 1f)) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            QuranDataProvider.loadFromAssets(context)
            QuranDataProvider.loadTafsirFromAssets(context)
            MushafPageData.loadPageMap(context)
        }
        dataReady = MushafPageData.isLoaded()
        if (!dataReady) {
            Toast.makeText(context, "تعذر تحميل خريطة الصفحات — تحقق من ملف madani_pages.json", Toast.LENGTH_LONG).show()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        // التحكم في شريط الحالة ليكون فاتحاً مع أيقونات داكنة في هذه الشاشة حصراً
        val view = androidx.compose.ui.platform.LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as android.app.Activity).window
                androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            }
        }

        if (!dataReady) {
            Box(Modifier.fillMaxSize().background(Color(0xFF0B0F19)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = GoldPrimary)
                    Spacer(Modifier.height(12.dp))
                    Text("جاري تجهيز صفحات المصحف…", color = TextSecondary, fontFamily = CairoFont)
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = onClose) { Text("رجوع", color = GoldPrimary, fontFamily = CairoFont) }
                }
            }
        } else {
            MushafReaderContent(
                startPage = (initialPage ?: prefs.getInt(KEY_LAST_PAGE, 1)).coerceIn(1, MushafPageData.PAGE_COUNT),
                bookmarks = bookmarks,
                onBookmarksChange = { updated ->
                    bookmarks = updated
                    prefs.edit().putStringSet(KEY_BOOKMARKS, updated.map { it.toString() }.toSet()).apply()
                },
                onSavePage = { page -> prefs.edit().putInt(KEY_LAST_PAGE, page).apply() },
                fontScale = fontScale,
                onFontScaleChange = { v ->
                    fontScale = v
                    prefs.edit().putFloat(KEY_FONT_SIZE, v).apply()
                },
                onClose = {
                    onClose()
                },
                onOpenTafseer = onOpenTafseer
            )
        }
    }
}

@Composable
private fun MushafReaderContent(
    startPage: Int,
    bookmarks: List<Int>,
    onBookmarksChange: (List<Int>) -> Unit,
    onSavePage: (Int) -> Unit,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    onClose: () -> Unit,
    onOpenTafseer: (Int, Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = startPage - 1, pageCount = { MushafPageData.PAGE_COUNT })
    var showPicker by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var selectedAyahForAction by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    
    // Audio state: 0 idle, 1 loading, 2 playing, 3 paused
    var audioState by remember { mutableIntStateOf(0) }
    val currentReciterKey by UserPreferencesManager.quranReciter.collectAsState()

    fun stopAudio() {
        QuranAudioPlayer.stop()
        audioState = 0
    }

    // حفظ آخر صفحة عند كل تنقل + إيقاف التلاوة عند تغيير الصفحة
    LaunchedEffect(pagerState.currentPage) {
        onSavePage(pagerState.currentPage + 1)
        stopAudio()
    }

    val currentPage = pagerState.currentPage + 1
    val surahsOnPage = remember(currentPage) { MushafPageData.getSurahsOnPage(currentPage) }
    val juz = remember(currentPage) { MushafPageData.getJuzForPage(currentPage) }
    val hizb = remember(currentPage) { MushafPageData.getHizbForPage(currentPage) }
    val isBookmarked = currentPage in bookmarks
    var showSheet by remember { mutableStateOf(false) }
    // أول آية ظاهرة في الصفحة الحالية (شارة «آية X»)
    var visibleAyah by remember(currentPage) {
        mutableIntStateOf(MushafPageData.getPageRefs(currentPage).firstOrNull()?.second ?: 1)
    }

    fun toggleBookmark() {
        val updated = if (isBookmarked) bookmarks - currentPage else (bookmarks + currentPage).sorted()
        onBookmarksChange(updated)
        Toast.makeText(
            context,
            if (isBookmarked) "أُزيلت العلامة من صفحة $currentPage 🔖" else "حُفظت صفحة $currentPage في علاماتك 🔖",
            Toast.LENGTH_SHORT
        ).show()
    }

    Column(Modifier.fillMaxSize().background(MushafCream)) {
        // ── الشريط العلوي ──
        MushafTopBar(
            surahNames = surahsOnPage.map { QuranDataProvider.surahNameOf(it) },
            juz = juz,
            isBookmarked = isBookmarked,
            onToggleBookmark = ::toggleBookmark,
            onOpenPicker = { showPicker = true },
            onOpenSearch = { showSearchDialog = true },
            onPrevSurah = {
                scope.launch {
                    val prevPage = (currentPage - 2).coerceAtLeast(0)
                    pagerState.animateScrollToPage(prevPage)
                }
            },
            onNextSurah = {
                scope.launch {
                    val nextPage = currentPage.coerceAtMost(MushafPageData.PAGE_COUNT - 1)
                    pagerState.animateScrollToPage(nextPage)
                }
            },
            onClose = {
                stopAudio()
                onSavePage(currentPage)
                onClose()
            }
        )
        // ── صفحة المصحف (سحب أفقي) ──
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { index ->
            MushafPageCard(
                page = index + 1,
                fontScale = fontScale,
                onVisibleAyah = { _, ayah ->
                    if (index + 1 == pagerState.currentPage + 1) visibleAyah = ayah
                },
                onOpenTafseer = { surah, ayah ->
                    selectedAyahForAction = surah to ayah
                    onOpenTafseer(surah, ayah)
                }
            )
        }
        // ── الشريط السفلي ──
        MushafBottomBar(
            page = currentPage,
            onPrev = { scope.launch { pagerState.animateScrollToPage((currentPage - 2).coerceAtLeast(0)) } },
            onNext = { scope.launch { pagerState.animateScrollToPage((currentPage).coerceAtMost(MushafPageData.PAGE_COUNT - 1)) } },
            onPageNumberClick = { showSheet = true }
        )
    }

    if (showSheet) {
        MushafPageSheet(
            currentPage = currentPage,
            surahName = surahsOnPage.firstOrNull()?.let { QuranDataProvider.surahNameOf(it) } ?: "المصحف الشريف",
            hizb = hizb,
            visibleAyah = visibleAyah,
            audioState = audioState,
            fontScale = fontScale,
            selectedReciter = currentReciterKey,
            onReciterSelect = { reciterId ->
                UserPreferencesManager.setQuranReciter(context, reciterId)
                stopAudio()
            },
            onFontScaleChange = onFontScaleChange,
            onAudioClick = {
                when (audioState) {
                    2 -> {
                        QuranAudioPlayer.toggle()
                        audioState = 3
                    }
                    3 -> {
                        QuranAudioPlayer.toggle()
                        audioState = 2
                    }
                    else -> {
                        audioState = 1
                        scope.launch(Dispatchers.IO) {
                            val urls = QuranAudioService.getPageAudioUrls(context, currentPage, currentReciterKey)
                            withContext(Dispatchers.Main) {
                                if (urls.isEmpty()) {
                                    audioState = 0
                                    Toast.makeText(context, "تعذر جلب تلاوة الصفحة — تحقق من الاتصال بالشبكة", Toast.LENGTH_SHORT).show()
                                } else {
                                    QuranAudioPlayer.playUrls(context, urls) {
                                        audioState = 0
                                    }
                                    audioState = 2
                                }
                            }
                        }
                    }
                }
            },
            onGoToPage = { page ->
                showSheet = false
                scope.launch { pagerState.scrollToPage((page - 1).coerceIn(0, MushafPageData.PAGE_COUNT - 1)) }
            },
            onDismiss = { showSheet = false }
        )
    }

    if (showPicker) {
        MushafPickerDialog(
            currentPage = currentPage,
            lastPage = currentPage,
            bookmarks = bookmarks,
            onGoToPage = { page ->
                showPicker = false
                scope.launch { pagerState.scrollToPage((page - 1).coerceIn(0, MushafPageData.PAGE_COUNT - 1)) }
            },
            onRemoveBookmark = { page ->
                onBookmarksChange(bookmarks - page)
            },
            onDismiss = { showPicker = false }
        )
    }

    if (showSearchDialog) {
        MushafSearchDialog(
            onGoToAyah = { surah, ayah ->
                val page = MushafPageData.getPageForAyah(surah, ayah)
                if (page > 0) {
                    showSearchDialog = false
                    scope.launch { pagerState.scrollToPage(page - 1) }
                }
            },
            onDismiss = { showSearchDialog = false }
        )
    }

    if (selectedAyahForAction != null) {
        val (sId, aId) = selectedAyahForAction!!
        MushafAyahActionSheet(
            surahId = sId,
            ayah = aId,
            reciterKey = currentReciterKey,
            onDismiss = { selectedAyahForAction = null }
        )
    }
}

@Composable
private fun MushafTopBar(
    surahNames: List<String>,
    juz: Int,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onOpenPicker: () -> Unit,
    onOpenSearch: () -> Unit,
    onPrevSurah: () -> Unit,
    onNextSurah: () -> Unit,
    onClose: () -> Unit
) {
    Surface(color = MushafCream, shadowElevation = 0.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // اليسار: علامة + الجزء
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleBookmark, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "علامة",
                        tint = if (isBookmarked) MushafDeepRed else Color.Black.copy(alpha = 0.7f)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.05f),
                    modifier = Modifier.clickable { onOpenPicker() }
                ) {
                    Text(
                        getJuzName(juz),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.Black, fontFamily = CairoFont, fontSize = 13.sp, fontWeight = FontWeight.Medium
                    )
                }
            }

            // المنتصف: أيقونة الشبكة + البحث
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenPicker) {
                    Icon(Icons.Default.GridView, contentDescription = "الفهرس", tint = Color.Black.copy(alpha = 0.7f))
                }
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Default.Search, contentDescription = "بحث في القرآن", tint = Color.Black.copy(alpha = 0.7f))
                }
            }

            // اليمين: السورة + أسهم التنقل
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevSurah, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Black.copy(alpha = 0.5f))
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.05f),
                    modifier = Modifier.clickable { onOpenPicker() }
                ) {
                    Text(
                        surahNames.firstOrNull() ?: "المصحف",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.Black, fontFamily = CairoFont, fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onNextSurah, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = Color.Black.copy(alpha = 0.5f))
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "رجوع", tint = MushafDeepRed)
                }
            }
        }
    }
}

@Composable
private fun MushafPageCard(
    page: Int,
    fontScale: Float,
    onVisibleAyah: (surahId: Int, ayah: Int) -> Unit,
    onOpenTafseer: (Int, Int) -> Unit
) {
    val refs = remember(page) { MushafPageData.getPageRefs(page) }
    // عناصر العرض: رأس سورة عند بدايتها + آية آية (لتتبع الظاهر منها)
    val items = remember(page) {
        val list = ArrayList<MushafLineItem>()
        var lastSurah = -1
        for ((s, a) in refs) {
            if (a == 1 && s != lastSurah) {
                list.add(MushafLineItem.Header(s))
                lastSurah = s
            }
            list.add(MushafLineItem.Ayah(s, a))
        }
        list
    }
    val listState = remember(page) { androidx.compose.foundation.lazy.LazyListState() }
    // الإبلاغ عن أول آية ظاهرة (شارة «آية X»)
    LaunchedEffect(page, listState.firstVisibleItemIndex) {
        val idx = listState.firstVisibleItemIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        if (items.isNotEmpty()) {
            when (val it = items[idx]) {
                is MushafLineItem.Ayah -> onVisibleAyah(it.surah, it.ayah)
                is MushafLineItem.Header -> onVisibleAyah(it.surah, 1)
            }
        }
    }
    Box(
        Modifier.fillMaxSize()
            .background(MushafCream)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا تتوفر بيانات هذه الصفحة", color = Color.Gray, fontFamily = CairoFont)
            }
            return@Box
        }
        val fontSize = (22 * fontScale).sp
        val lineH = (44 * fontScale).sp
        androidx.compose.foundation.lazy.LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            items(
                count = items.size,
                key = { i ->
                    when (val it = items[i]) {
                        is MushafLineItem.Header -> "h-${it.surah}"
                        is MushafLineItem.Ayah -> "a-${it.surah}-${it.ayah}"
                    }
                }
            ) { i ->
                when (val it = items[i]) {
                    is MushafLineItem.Header -> {
                        SurahDecorativeHeader(surahName = QuranDataProvider.surahNameOf(it.surah), fontSize = fontSize)
                        if (it.surah != 9) {
                            Text(
                                "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ",
                                color = Color.Black, fontFamily = AmiriFont,
                                fontSize = (fontSize.value * 1.1).sp, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                            )
                        }
                    }
                    is MushafLineItem.Ayah -> {
                        Text(
                            buildAnnotatedString {
                                append(QuranDataProvider.getVerseText(it.surah, it.ayah) ?: "")
                                append(" ")
                                // شارة الآية (دائرة مزخرفة)
                                append(" ﴿${easternDigits(it.ayah)}﴾ ")
                            },
                            fontFamily = AmiriFont,
                            fontSize = fontSize,
                            lineHeight = lineH,
                            color = Color.Black,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .clickable { onOpenTafseer(it.surah, it.ayah) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SurahDecorativeHeader(surahName: String, fontSize: androidx.compose.ui.unit.TextUnit) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        // رسم برواز بسيط يشبه لقطة الشاشة
        Surface(
            color = Color(0xFFF5EEDC),
            shape = RoundedCornerShape(4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MushafHeaderGold),
            modifier = Modifier.fillMaxWidth(0.85f).height(54.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                // زخرفة جانبية (وهمية)
                Text(
                    "سُورَةُ $surahName",
                    color = Color.Black, fontFamily = AmiriFont,
                    fontWeight = FontWeight.Bold, fontSize = (fontSize.value * 1.2f).sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private sealed interface MushafLineItem {
    data class Header(val surah: Int) : MushafLineItem
    data class Ayah(val surah: Int, val ayah: Int) : MushafLineItem
}

@Composable
private fun MushafBottomBar(
    page: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPageNumberClick: () -> Unit
) {
    Surface(color = MushafCream, shadowElevation = 0.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // سهم التنقل لليسار
            IconButton(onClick = onPrev, modifier = Modifier.size(36.dp)) {
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.05f)) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "السابق", tint = Color.Black, modifier = Modifier.padding(4.dp))
                }
            }

            // رقم الصفحة على اليمين
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.05f),
                modifier = Modifier.clickable { onPageNumberClick() }
            ) {
                Text(
                    easternDigits(page),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    color = Color.Black, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MushafPageSheet(
    currentPage: Int,
    surahName: String,
    hizb: Int,
    visibleAyah: Int,
    audioState: Int,
    fontScale: Float,
    selectedReciter: String,
    onReciterSelect: (String) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onAudioClick: () -> Unit,
    onGoToPage: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sliderValue by remember(currentPage) { mutableFloatStateOf(currentPage.toFloat()) }
    val reciterInfo = remember(selectedReciter) { QuranAudioService.getReciterInfo(selectedReciter) }
    var showReciterMenu by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF151B2B)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (hizb > 0) "الحزب ${easternDigits(hizb)}" else "",
                    color = TextSecondary, fontFamily = CairoFont, fontSize = 13.sp
                )
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = GoldPrimary.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
                ) {
                    Text(
                        "آية ${easternDigits(visibleAyah)}",
                        color = GoldPrimary, fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
                Text(
                    surahName,
                    color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "صفحة ${easternDigits(currentPage)}",
                color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 28.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onGoToPage(sliderValue.toInt().coerceIn(1, MushafPageData.PAGE_COUNT)) },
                valueRange = 1f..MushafPageData.PAGE_COUNT.toFloat(),
                steps = MushafPageData.PAGE_COUNT - 2,
                colors = SliderDefaults.colors(thumbColor = GoldPrimary, activeTrackColor = GoldPrimary)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ((currentPage - 3)..(currentPage + 3))
                    .filter { it in 1..MushafPageData.PAGE_COUNT }
                    .forEach { p ->
                        val selected = p == currentPage
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp))
                                .background(if (selected) GoldPrimary else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (selected) GoldPrimary else Color.White.copy(alpha = 0.15f),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onGoToPage(p) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                easternDigits(p),
                                color = if (selected) Color.Black else TextSecondary,
                                fontFamily = CairoFont,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = if (selected) 18.sp else 14.sp
                            )
                        }
                    }
            }
            Spacer(Modifier.height(14.dp))

            // ── مشغل التلاوة الصوتي + اختيار القارئ ──
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0B1120),
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onAudioClick, enabled = audioState != 1) {
                        when (audioState) {
                            1 -> CircularProgressIndicator(
                                color = GoldPrimary,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            2 -> Icon(Icons.Default.Pause, contentDescription = "إيقاف مؤقت", tint = GoldPrimary)
                            else -> Icon(Icons.Default.Headset, contentDescription = "تشغيل التلاوة", tint = GoldPrimary)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showReciterMenu = true }
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = if (audioState == 2) "جاري الاستماع الآن 🔊" else "تلاوة الصفحة بصوت:",
                            color = if (audioState == 2) GoldPrimary else TextSecondary,
                            fontFamily = CairoFont,
                            fontSize = 11.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = reciterInfo.nameAr,
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                        }
                    }

                    DropdownMenu(
                        expanded = showReciterMenu,
                        onDismissRequest = { showReciterMenu = false },
                        modifier = Modifier.background(Color(0xFF151B2B))
                    ) {
                        QuranAudioService.availableReciters.forEach { r ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        r.nameAr,
                                        color = if (r.id == selectedReciter) GoldPrimary else Color.White,
                                        fontFamily = CairoFont,
                                        fontWeight = if (r.id == selectedReciter) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    showReciterMenu = false
                                    onReciterSelect(r.id)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("أ", color = TextSecondary, fontFamily = CairoFont, fontSize = 14.sp)
                Slider(
                    value = fontScale,
                    onValueChange = onFontScaleChange,
                    valueRange = 0.8f..1.6f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = GoldPrimary, activeTrackColor = GoldPrimary)
                )
                Text("أ", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MushafPickerDialog(
    currentPage: Int,
    lastPage: Int,
    bookmarks: List<Int>,
    onGoToPage: (Int) -> Unit,
    onRemoveBookmark: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("سورة", "جزء", "صفحة", "🔖 علاماتي")
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            Modifier.fillMaxWidth().heightIn(max = 520.dp),
            shape = RoundedCornerShape(20.dp), color = Color(0xFF151B2B)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "الانتقال في المصحف",
                    color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold,
                    fontSize = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                    tabs.forEachIndexed { i, title ->
                        Tab(
                            selected = tab == i, onClick = { tab = i },
                            text = { Text(title, fontFamily = CairoFont, fontSize = 12.sp, color = if (tab == i) GoldPrimary else TextSecondary) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.weight(1f, fill = false)) {
                    when (tab) {
                        0 -> {
                            Column(Modifier.heightIn(max = 420.dp)) {
                                // بطاقة فاصل الصفحة: آخر موضع قراءة
                                val lastRefs = remember(lastPage) { MushafPageData.getPageRefs(lastPage) }
                                if (lastRefs.isNotEmpty()) {
                                    val (ls, la) = lastRefs.first()
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color(0xFF0B1120))
                                            .border(1.dp, GoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                            .clickable { onGoToPage(lastPage) }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                            Text(
                                                "فاصل الصفحة",
                                                color = Color.White, fontFamily = CairoFont,
                                                fontWeight = FontWeight.Bold, fontSize = 15.sp
                                            )
                                            Text(
                                                "${QuranDataProvider.surahNameOf(ls)} - الآية ${easternDigits(la)} - صفحة ${easternDigits(lastPage)}",
                                                color = GoldSecondary, fontFamily = CairoFont, fontSize = 12.sp
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Text("➤", color = GoldPrimary, fontSize = 20.sp)
                                    }
                                    Spacer(Modifier.height(10.dp))
                                }
                                LazyColumn(Modifier.weight(1f, fill = false)) {
                                    items(QuranDataProvider.surahs) { surah ->
                                        val page = remember { MushafPageData.getPageForSurah(surah.id) }
                                        val selected = surah.id in MushafPageData.getSurahsOnPage(currentPage)
                                        Row(
                                            Modifier.fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(if (selected) Color(0xFF6B5A1E) else Color.Transparent)
                                                .clickable { if (page > 0) onGoToPage(page) }
                                                .padding(vertical = 10.dp, horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                Modifier.size(44.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(GoldPrimary.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    easternDigits(page.takeIf { it > 0 } ?: 0),
                                                    color = GoldPrimary, fontFamily = CairoFont,
                                                    fontWeight = FontWeight.Bold, fontSize = 14.sp
                                                )
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                                Text(
                                                    surah.name, color = Color.White, fontFamily = CairoFont,
                                                    fontWeight = FontWeight.Bold, fontSize = 16.sp
                                                )
                                                Text(
                                                    "آياتها ${easternDigits(surah.versesCount)} - ${surah.type}",
                                                    color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp
                                                )
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Box(
                                                Modifier.size(44.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(GoldPrimary.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    easternDigits(surah.id),
                                                    color = GoldPrimary, fontFamily = CairoFont,
                                                    fontWeight = FontWeight.Bold, fontSize = 15.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(5), Modifier.heightIn(max = 380.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items((1..MushafPageData.JUZ_COUNT).toList()) { juz ->
                                    Box(
                                        Modifier.clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF0B1120))
                                            .border(1.dp, GoldPrimary.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                            .clickable {
                                                val page = MushafPageData.getPageForJuz(juz)
                                                if (page > 0) onGoToPage(page)
                                            }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("${easternDigits(juz)}", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        2 -> {
                            var input by remember { mutableStateOf(currentPage.toString()) }
                            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                OutlinedTextField(
                                    value = input,
                                    onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 3) input = v },
                                    label = { Text("رقم الصفحة (١–٦٠٤)", fontFamily = CairoFont) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true, modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        val p = input.toIntOrNull()
                                        if (p != null && p in 1..MushafPageData.PAGE_COUNT) onGoToPage(p)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color.Black)
                                ) {
                                    Text("انتقال", fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        3 -> {
                            if (bookmarks.isEmpty()) {
                                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                    Text("لا علامات بعد — استخدم 🔖 أسفل أي صفحة لحفظها", color = TextSecondary, fontFamily = CairoFont, textAlign = TextAlign.Center)
                                }
                            } else {
                                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                                    items(bookmarks) { page ->
                                        Row(
                                            Modifier.fillMaxWidth().clickable { onGoToPage(page) }
                                                .padding(vertical = 10.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "صفحة ${easternDigits(page)}",
                                                color = Color.White, fontFamily = CairoFont, modifier = Modifier.weight(1f)
                                            )
                                            IconButton(onClick = { onRemoveBookmark(page) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Gray)
                                            }
                                        }
                                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("إغلاق", color = GoldPrimary, fontFamily = CairoFont)
                }
            }
        }
    }
}

/**
 * نافذة البحث الذكي في القرآن الكريم
 */
@Composable
private fun MushafSearchDialog(
    onGoToAyah: (surahId: Int, ayah: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) {
        if (query.trim().length >= 2) {
            QuranDataProvider.searchVerses(query, limit = 40)
        } else {
            emptyList()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 580.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF151B2B)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondary)
                    }
                    Text(
                        "البحث في القرآن الكريم",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("ابحث عن أي كلمة أو آية…", fontFamily = CairoFont, color = TextSecondary) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GoldPrimary) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "مسح", tint = TextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color(0xFF2A3447),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                if (query.isBlank()) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = GoldPrimary.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("اكتب كلمة للبحث في آيات القرآن الكريم الـ 6236", color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                    }
                } else if (results.isEmpty()) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (query.trim().length < 2) "اكتب حرفين على الأقل للبحث" else "لم يتم العثور على نتائج لـ «$query»",
                            color = TextSecondary,
                            fontFamily = CairoFont,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Text(
                        "عدد النتائج: ${easternDigits(results.size)} آية",
                        color = GoldSecondary,
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    LazyColumn(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(results) { item ->
                            val pageNum = remember(item.surahNumber, item.verseNumber) {
                                MushafPageData.getPageForAyah(item.surahNumber, item.verseNumber)
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF0B1120),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A3447)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onGoToAyah(item.surahNumber, item.verseNumber) }
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (pageNum > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = GoldPrimary.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    "ص ${easternDigits(pageNum)}",
                                                    color = GoldPrimary,
                                                    fontFamily = CairoFont,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            "${item.surahName} • آية ${easternDigits(item.verseNumber)}",
                                            color = GoldPrimary,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        item.verseText,
                                        color = Color.White,
                                        fontFamily = AmiriFont,
                                        fontSize = 16.sp,
                                        lineHeight = 26.sp,
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * ورقة تفاعلية للآية: التفسير الميسر، التلاوة الصوتية للآية، والنسخ
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MushafAyahActionSheet(
    surahId: Int,
    ayah: Int,
    reciterKey: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val surahName = remember(surahId) { QuranDataProvider.surahNameOf(surahId) }
    val verseText = remember(surahId, ayah) { QuranDataProvider.getVerseText(surahId, ayah) ?: "" }
    var tafsirText by remember { mutableStateOf<String?>(QuranDataProvider.getTafsirForVerse(surahId, ayah)) }
    var isPlayingSingleAyah by remember { mutableStateOf(false) }

    LaunchedEffect(surahId, ayah) {
        if (tafsirText == null) {
            tafsirText = QuranDataProvider.fetchRemoteTafsir(surahId, ayah)
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (isPlayingSingleAyah) QuranAudioPlayer.stop()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = Color(0xFF151B2B)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = GoldPrimary.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
                ) {
                    Text(
                        "آية ${easternDigits(ayah)}",
                        color = GoldPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
                Text(
                    surahName,
                    color = Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            // نص الآية
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0B1120),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A3447)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "$verseText ﴿${easternDigits(ayah)}﴾",
                    color = Color.White,
                    fontFamily = AmiriFont,
                    fontSize = 18.sp,
                    lineHeight = 32.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(14.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // أزرار الإجراءات (تلاوة الآية / نسخ)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (isPlayingSingleAyah) {
                            QuranAudioPlayer.stop()
                            isPlayingSingleAyah = false
                        } else {
                            val audioUrl = QuranAudioService.getAyahAudioUrl(surahId, ayah, reciterKey)
                            QuranAudioPlayer.playUrls(context, listOf(audioUrl)) {
                                isPlayingSingleAyah = false
                            }
                            isPlayingSingleAyah = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlayingSingleAyah) MushafDeepRed else GoldPrimary,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        if (isPlayingSingleAyah) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isPlayingSingleAyah) "إيقاف التلاوة" else "استماع للآية",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                OutlinedButton(
                    onClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString("$verseText ($surahName: $ayah)"))
                        Toast.makeText(context, "تم نسخ الآية الكريمة بنجاح 📋", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("نسخ الآية", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(14.dp))

            // التفسير الميسر
            Text(
                "التفسير الميسر 📖",
                color = GoldSecondary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0B1120),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
            ) {
                Box(Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        text = tafsirText ?: "جاري جلب التفسير الميسر…",
                        color = Color.White.copy(alpha = 0.9f),
                        fontFamily = CairoFont,
                        fontSize = 13.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Right
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}
