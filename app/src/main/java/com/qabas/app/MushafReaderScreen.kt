package com.qabas.app

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
// لم يكن في التطبيق أي قارئ مصحف قائم بذاته قبل هذه الدفعة: QURAN_HUB كان
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

    // تحميل النص وخريطة الصفحات، ثم تحديد موضع البداية:
    // 1) صفحة محددة (من «أكمل» أو البحث) → أول آية في تلك الصفحة.
    // 2) سورة/آية محددة (من نتيجة بحث) → تلك الآية.
    // 3) آخر قراءة محفوظة.
    // 4) الفاتحة.
    LaunchedEffect(Unit) {
        quranReady = QuranDataProvider.ensureQuranLoaded(context)
        QuranDataProvider.loadTafsirFromAssets(context)
        pageMapReady = MushafPageData.loadPageMap(context)
        val start: Pair<Int, Int>? = when {
            initialPage != null && pageMapReady -> {
                MushafPageData.getPageRefs(initialPage).firstOrNull()
            }
            initialSurah != null && initialAyah != null -> initialSurah to initialAyah
            else -> UserPreferencesManager.getLastReadQuran(context)
                ?.let { it.surah to it.ayah }
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
                onBack = onBack
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
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}
