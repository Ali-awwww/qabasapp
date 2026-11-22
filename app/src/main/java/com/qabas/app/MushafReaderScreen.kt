package com.qabas.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleTopBar
import com.qabas.app.ui.theme.AmiriFont
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.QabasTheme
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

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

@Composable
fun MushafReaderScreen(
    initialSurah: Int? = null,
    initialAyah: Int? = null,
    initialPage: Int? = null,
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val theme = QabasTheme.current

    var quranReady by remember { mutableStateOf(false) }
    var pageMapReady by remember { mutableStateOf(false) }
    var selectedSurah by remember { mutableStateOf(initialSurah?.takeIf { it in 1..114 } ?: 1) }
    var targetAyah by remember { mutableStateOf<Int?>(null) }
    var targetConsumed by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf<Int?>(null) }

    // --- التلاوة الصوتية (QuranAudioService — الدفعة: ربط صوت القرآن) ---
    // تشغيل تلاوة الصفحة الحالية فقط؛ الخدمة لا توفّر توقيت كلمات، فلا تزامن
    // كلمة بكلمة ولا تمييز آية أثناء التشغيل — زر تشغيل/إيقاف واختيار قارئ فقط.
    var selectedReciter by remember { mutableStateOf("alafasy") }
    var isPlayingAudio by remember { mutableStateOf(false) }
    var isLoadingAudio by remember { mutableStateOf(false) }
    var playingPage by remember { mutableStateOf<Int?>(null) }
    var audioMessage by remember { mutableStateOf<String?>(null) }
    var reciterMenuOpen by remember { mutableStateOf(false) }
    val audioScope = rememberCoroutineScope()

    fun stopAudio() {
        QuranAudioPlayer.stop()
        isPlayingAudio = false
        playingPage = null
    }

    fun togglePageAudio() {
        if (isLoadingAudio) return
        if (playingPage != null) {
            // استئناف/إيقاف التلاوة الجارية لصفحتها دون إعادة تحميل.
            isPlayingAudio = QuranAudioPlayer.toggle()
            return
        }
        val page = currentPage ?: return
        audioMessage = null
        isLoadingAudio = true
        audioScope.launch {
            val urls = QuranAudioService.getPageAudioUrls(context, page, selectedReciter)
            isLoadingAudio = false
            if (urls.isEmpty()) {
                audioMessage = "تعذّر تحميل تلاوة هذه الصفحة. تحقق من الاتصال وحاول مجدداً."
                return@launch
            }
            QuranAudioPlayer.playUrls(context, urls) {
                isPlayingAudio = false
                playingPage = null
            }
            playingPage = page
            isPlayingAudio = true
        }
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
    var navDialogOpen by remember { mutableStateOf(false) }
    var bookmarksDialogOpen by remember { mutableStateOf(false) }
    var editingBookmark by remember { mutableStateOf<UserPreferencesManager.MushafBookmark?>(null) }

    fun refreshBookmarks() {
        bookmarks = UserPreferencesManager.getMushafBookmarks(context)
    }

    // قفزة واحدة لكل مسارات الانتقال (بحث، جزء، صفحة، علامة): توقف التلاوة
    // وتفتح السورة عند الآية المطلوبة عبر آلية targetAyah نفسها.
    fun jumpTo(surah: Int, ayah: Int) {
        if (surah !in 1..114 || ayah < 1) return
        stopAudio()
        audioMessage = null
        selectedSurah = surah
        targetAyah = ayah
        targetConsumed = false
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
    LaunchedEffect(Unit) {
        refreshBookmarks()
        quranReady = QuranDataProvider.ensureQuranLoaded(context)
        QuranDataProvider.loadTafsirFromAssets(context)
        pageMapReady = MushafPageData.loadPageMap(context)
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

    val verses = remember(selectedSurah, quranReady) {
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

    // تتبّع أول آية ظاهرة مع مهلة — لا حفظ عند كل إطار تمرير.
    LaunchedEffect(listState, selectedSurah, verses) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .debounce(800L)
            .collect { index ->
                verses.getOrNull(index)?.let { verse ->
                    recordPosition(selectedSurah, verse.verseNumber)
                }
            }
    }

    // حفظ آخر موضع عند مغادرة الشاشة (رجوع أو تنقّل) + إيقاف التلاوة.
    DisposableEffect(Unit) {
        onDispose {
            QuranAudioPlayer.stop()
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

    Scaffold(
        containerColor = theme.background,
        topBar = {
            AppleTopBar(
                title = "المصحف الشريف",
                subtitle = "سورة $surahName" +
                    (currentPage?.let { " • صفحة $it" } ?: "") + juzLabel,
                onBack = onBack,
                actions = {
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
            LazyRow(
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
                                    stopAudio()
                                    audioMessage = null
                                    selectedSurah = surah.id
                                    targetAyah = 1
                                    targetConsumed = false
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

            // شريط التلاوة: تشغيل/إيقاف تلاوة الصفحة الحالية + اختيار القارئ.
            // الصوت حقيقي من QuranAudioService (EveryAyah CDN، وQF API للعفاسي
            // عند توفر المفاتيح) — بلا توقيت كلمات، فلا تزامن ولا تمييز آية.
            if (currentPage != null || playingPage != null) {
                val reciterName = QuranAudioService.getReciterInfo(selectedReciter).nameAr
                val audioLabel = when {
                    audioMessage != null -> audioMessage!!
                    isLoadingAudio -> "جارٍ تحميل التلاوة…"
                    isPlayingAudio && playingPage != null -> "تشغيل تلاوة صفحة $playingPage"
                    playingPage != null -> "تلاوة صفحة $playingPage متوقفة مؤقتاً"
                    else -> "تلاوة صفحة $currentPage"
                }
                AppleCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
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
                                text = "القارئ: $reciterName",
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
                                        onClick = {
                                            reciterMenuOpen = false
                                            if (reciter.id != selectedReciter) {
                                                stopAudio()
                                                audioMessage = null
                                                selectedReciter = reciter.id
                                            }
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
                    CircularProgressIndicator(color = theme.primary)
                }
                return@Column
            }

            if (verses.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "تعذّر تحميل آيات سورة $surahName. تحقق من الاتصال وحاول مجدداً.",
                        color = theme.textSecondary,
                        fontFamily = CairoFont,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                return@Column
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(verses, key = { it.verseNumber }) { verse ->
                    AppleCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(theme.primary.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = verse.verseNumber.toString(),
                                    color = theme.primary,
                                    fontFamily = NotoSansFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = verse.verseText,
                                    color = theme.textPrimary,
                                    fontFamily = AmiriFont,
                                    fontSize = 19.sp,
                                    lineHeight = 32.sp,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (verse.tafseer.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = verse.tafseer,
                                        color = theme.textSecondary,
                                        fontFamily = CairoFont,
                                        fontSize = 12.sp,
                                        lineHeight = 19.sp,
                                        maxLines = 3
                                    )
                                }
                            }
                            // علامة الآية: نقرة تحفظها فوراً باللون الذهبي،
                            // ونقرة العلامة المحفوظة تفتح محررها (ملاحظة/لون/حذف).
                            val verseBookmark = bookmarks.firstOrNull {
                                it.surah == selectedSurah && it.ayah == verse.verseNumber
                            }
                            IconButton(
                                onClick = {
                                    if (verseBookmark != null) {
                                        editingBookmark = verseBookmark
                                    } else {
                                        UserPreferencesManager.saveMushafBookmark(
                                            context,
                                            UserPreferencesManager.MushafBookmark(
                                                surah = selectedSurah,
                                                ayah = verse.verseNumber,
                                                colorArgb = UserPreferencesManager.MUSHAF_BOOKMARK_DEFAULT_COLOR,
                                                note = "",
                                                createdAtMillis = System.currentTimeMillis()
                                            )
                                        )
                                        refreshBookmarks()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (verseBookmark != null) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = if (verseBookmark != null) Translator.tr("تعديل العلامة") else Translator.tr("حفظ علامة"),
                                    tint = if (verseBookmark != null) Color(verseBookmark.colorArgb) else theme.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }

        // حوارات المرحلة 1: الانتقال السريع، وقائمة العلامات، ومحرر العلامة.
        if (navDialogOpen) {
            MushafNavigationDialog(
                pageMapReady = pageMapReady,
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
                    UserPreferencesManager.removeMushafBookmark(context, bookmark.surah, bookmark.ayah)
                    refreshBookmarks()
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
                    UserPreferencesManager.removeMushafBookmark(context, bookmark.surah, bookmark.ayah)
                    refreshBookmarks()
                    editingBookmark = null
                }
            )
        }
    }
}

// ============================================================================
// حوارات المرحلة 1 من خارطة المصحف (الأساسيات) — الانتقال السريع والعلامات.
// كل القفزات تمر عبر onJumpTo في الشاشة؛ لا نص قرآني جديد هنا، والبيانات
// كلها من QuranDataProvider وMushafPageData الموثوقتين أصلاً.
// ============================================================================

@Composable
private fun MushafNavigationDialog(
    pageMapReady: Boolean,
    onDismiss: () -> Unit,
    onJumpTo: (Int, Int) -> Unit
) {
    val theme = QabasTheme.current
    var surahQuery by remember { mutableStateOf("") }
    var pageText by remember { mutableStateOf("") }
    var pageError by remember { mutableStateOf<String?>(null) }
    val surahResults = remember(surahQuery) { QuranDataProvider.searchSurahs(surahQuery) }
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
                                text = "${surah.type} • ${surah.versesCount} آية",
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
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(theme.background)
                                .clickable { jumpToPage(MushafPageData.getPageForJuz(juz)) }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = juz.toString(),
                                color = theme.textPrimary,
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
