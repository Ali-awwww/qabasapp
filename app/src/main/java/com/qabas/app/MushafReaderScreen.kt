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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

    // حفظ آخر موضع عند مغادرة الشاشة (رجوع أو تنقّل).
    DisposableEffect(Unit) {
        onDispose {
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
