package com.qabas.app

import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.components.AppleSearchBar
import com.qabas.app.ui.components.AppleTopBar
import com.qabas.app.ui.theme.AmiriFont
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.QabasTheme
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * شاشة قارئ صحيح البخاري وصحيح مسلم الكامل بتصميم Apple HIG للوضعين الفاتح والداكن
 * تدعم التصفح بالفصول، البحث، القراءة الصوتية، التحميل التدريجي، واختبار الفهم
 */
@Composable
fun FullSahihReaderScreen(
    bookType: SahihBookType,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val theme = QabasTheme.current

    var chapters by remember { mutableStateOf<List<FullSahihChapter>>(emptyList()) }
    var hadiths by remember { mutableStateOf<List<FullSahihHadith>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingHadiths by remember { mutableStateOf(false) }
    var selectedChapterId by remember { mutableStateOf<Int?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<FullSahihHadith>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var expandedHadithIds by remember { mutableStateOf(setOf<Int>()) }
    var selectedHadith by remember { mutableStateOf<FullSahihHadith?>(null) }
    var showDetailsDialog by remember { mutableStateOf(false) }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isSpeaking by remember { mutableStateOf(false) }
    var speakingHadithId by remember { mutableStateOf<Int?>(null) }

    var visibleHadithCount by remember { mutableStateOf(30) }
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= (visibleHadithCount - 10)
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            visibleHadithCount += 30
        }
    }

    LaunchedEffect(selectedChapterId, searchQuery) {
        visibleHadithCount = 30
    }

    LaunchedEffect(bookType) {
        isLoading = true
        chapters = FullSahihHadithRepository.getChapters(context, bookType)
        isLoading = false
    }

    LaunchedEffect(selectedChapterId) {
        val chId = selectedChapterId ?: return@LaunchedEffect
        isLoadingHadiths = true
        hadiths = FullSahihHadithRepository.getHadithsForChapter(context, bookType, chId)
        isLoadingHadiths = false
    }

    LaunchedEffect(bookType, searchQuery) {
        if (searchQuery.isBlank()) {
            searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        kotlinx.coroutines.delay(300)
        searchResults = FullSahihHadithRepository.searchHadiths(context, bookType, searchQuery)
        isSearching = false
    }

    BackHandler(enabled = selectedChapterId != null || searchQuery.isNotBlank()) {
        if (selectedChapterId != null) {
            selectedChapterId = null
        } else if (searchQuery.isNotBlank()) {
            searchQuery = ""
        }
    }

    DisposableEffect(Unit) {
        val ttsInit = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ar")
            }
        }
        tts = ttsInit

        onDispose {
            try {
                ttsInit.stop()
                ttsInit.shutdown()
            } catch (_: Exception) {}
            tts = null
            scope.launch {
                FullSahihHadithRepository.clearCache()
            }
        }
    }

    fun speakHadith(hadith: FullSahihHadith) {
        val currentTts = tts ?: return
        if (isSpeaking && speakingHadithId == hadith.id) {
            currentTts.stop()
            isSpeaking = false
            speakingHadithId = null
        } else {
            currentTts.language = Locale("ar")
            val cleanText = hadith.arabic.replace(Regex("[\\n\\r]"), " ").trim()
            val speakResult = currentTts.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "hadith_${hadith.id}")
            if (speakResult == TextToSpeech.SUCCESS) {
                isSpeaking = true
                speakingHadithId = hadith.id
            } else {
                isSpeaking = false
                speakingHadithId = null
            }
        }
    }

    val bookTitle = when (bookType) {
        SahihBookType.BUKHARI -> "صحيح البخاري (كامل)"
        SahihBookType.MUSLIM -> "صحيح مسلم (كامل)"
    }
    val selectedChapter = remember(selectedChapterId, chapters) {
        chapters.firstOrNull { it.id == selectedChapterId }
    }

    var showQuiz by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = theme.background,
        topBar = {
            AppleTopBar(
                title = bookTitle,
                subtitle = selectedChapter?.arabic ?: "${chapters.size} كتاباً وفصلاً",
                onBack = {
                    when {
                        selectedChapterId != null -> selectedChapterId = null
                        searchQuery.isNotBlank() -> searchQuery = ""
                        else -> onBack()
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                FullSahihHadithRepository.loadBook(context, bookType)
                                showQuiz = true
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Quiz, contentDescription = "اختبار الفهم والحفظ", tint = theme.primary)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
            ) {
                // Search Bar
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    AppleSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = "ابحث في الكتاب كاملًا..."
                    )
                }

                // Quick Stats Banner
                AppleCard(
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (searchQuery.isNotBlank()) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "نتائج البحث: ${searchResults.size}",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        } else {
                            Icon(Icons.Default.AutoStories, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (selectedChapter != null) selectedChapter.arabic
                                else "${chapters.size} كتاب • تصفح أو ابحث في جميع الأحاديث",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                when {
                    isLoading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = theme.primary)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "جاري تحميل الكتاب...",
                                    color = theme.textSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    searchQuery.isNotBlank() -> {
                        if (isSearching) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = theme.primary)
                            }
                        } else if (searchResults.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "لا نتائج لـ «$searchQuery»",
                                    color = theme.textSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                itemsIndexed(searchResults.take(visibleHadithCount)) { _, hadith ->
                                    FullHadithCard(
                                        hadith = hadith,
                                        isExpanded = expandedHadithIds.contains(hadith.id),
                                        isSpeaking = speakingHadithId == hadith.id,
                                        onToggleExpand = {
                                            expandedHadithIds = if (expandedHadithIds.contains(hadith.id)) expandedHadithIds - hadith.id else expandedHadithIds + hadith.id
                                        },
                                        onClickDetails = { selectedHadith = hadith; showDetailsDialog = true },
                                        onClickSpeak = { speakHadith(hadith) }
                                    )
                                }
                            }
                        }
                    }

                    selectedChapterId == null -> {
                        if (chapters.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "تعذّر تحميل الكتاب من ملفات التطبيق",
                                        color = theme.destructive,
                                        fontFamily = CairoFont,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "تأكد من اكتمال التثبيت ثم أعد المحاولة",
                                        color = theme.textSecondary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 12.sp
                                    )
                                    Spacer(Modifier.height(14.dp))
                                    ApplePrimaryButton(
                                        text = "إعادة المحاولة",
                                        icon = Icons.Default.Refresh,
                                        onClick = {
                                            scope.launch {
                                                isLoading = true
                                                FullSahihHadithRepository.clearCache()
                                                chapters = FullSahihHadithRepository.getChapters(context, bookType)
                                                isLoading = false
                                            }
                                        },
                                        modifier = Modifier.widthIn(max = 240.dp)
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(chapters.take(visibleHadithCount)) { idx, chapter ->
                                    FullChapterCard(
                                        chapter = chapter,
                                        index = idx,
                                        onOpen = { selectedChapterId = chapter.id }
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        if (isLoadingHadiths) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = theme.primary)
                            }
                        } else if (hadiths.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "لا أحاديث في هذا الفصل",
                                    color = theme.textSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                itemsIndexed(hadiths.take(visibleHadithCount)) { _, hadith ->
                                    FullHadithCard(
                                        hadith = hadith,
                                        isExpanded = expandedHadithIds.contains(hadith.id),
                                        isSpeaking = speakingHadithId == hadith.id,
                                        onToggleExpand = {
                                            expandedHadithIds = if (expandedHadithIds.contains(hadith.id)) expandedHadithIds - hadith.id else expandedHadithIds + hadith.id
                                        },
                                        onClickDetails = { selectedHadith = hadith; showDetailsDialog = true },
                                        onClickSpeak = { speakHadith(hadith) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDetailsDialog && selectedHadith != null) {
        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            containerColor = theme.cardSurface,
            shape = RoundedCornerShape(18.dp),
            title = {
                Text(
                    "حديث رقم ${selectedHadith!!.idInBook}",
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                SelectionContainer {
                    Text(
                        selectedHadith!!.arabic,
                        color = theme.textPrimary,
                        fontFamily = AmiriFont,
                        fontSize = 16.sp,
                        lineHeight = 28.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showDetailsDialog = false },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("إغلاق", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showQuiz) {
        SahihQuizDialog(
            bookType = bookType,
            onDismiss = { showQuiz = false }
        )
    }
}

@Composable
private fun FullChapterCard(
    chapter: FullSahihChapter,
    index: Int,
    onOpen: () -> Unit
) {
    val theme = QabasTheme.current
    AppleCard(onClick = onOpen) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 60.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(theme.primary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${index + 1}",
                    color = theme.primary,
                    fontFamily = CairoFont,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    chapter.arabic,
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (chapter.english.isNotBlank()) {
                    Text(
                        chapter.english,
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 11.5.sp
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = theme.textSecondary
            )
        }
    }
}

@Composable
private fun FullHadithCard(
    hadith: FullSahihHadith,
    isExpanded: Boolean,
    isSpeaking: Boolean,
    onToggleExpand: () -> Unit,
    onClickDetails: () -> Unit,
    onClickSpeak: () -> Unit
) {
    val theme = QabasTheme.current
    val containerColor = if (isSpeaking) {
        theme.success.copy(alpha = if (theme.isDark) 0.18f else 0.08f)
    } else {
        theme.cardSurface
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        shadowElevation = if (theme.isDark) 0.dp else 1.5.dp,
        border = BorderStroke(
            width = if (isSpeaking) 1.dp else 0.5.dp,
            color = if (isSpeaking) theme.success else theme.separator.copy(alpha = 0.45f)
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = theme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "حديث #${hadith.idInBook}",
                        color = theme.primary,
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.weight(1f))

                IconButton(
                    onClick = onClickSpeak,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        if (isSpeaking) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isSpeaking) "إيقاف القراءة" else "قراءة صوتية",
                        tint = if (isSpeaking) theme.destructive else theme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onClickDetails,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "تفاصيل الحديث",
                        tint = theme.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            SelectionContainer {
                Text(
                    text = if (isExpanded) hadith.arabic else hadith.arabic.take(200) + if (hadith.arabic.length > 200) "..." else "",
                    color = theme.textPrimary,
                    fontFamily = AmiriFont,
                    fontSize = 16.5.sp,
                    lineHeight = 28.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleExpand() }
                )
            }

            if (hadith.arabic.length > 200) {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(
                        if (isExpanded) "إخفاء النص الكامل" else "عرض النص الكامل",
                        color = theme.primary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
