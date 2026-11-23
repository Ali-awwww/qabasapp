package com.qabas.app

import android.speech.tts.TextToSpeech
import android.widget.Toast
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
    // ق82: مرشّح «في هذا الباب فقط» — يقتصر البحث على الباب المفتوح
    // بدل الكتاب كاملاً؛ الافتراضي الكتاب كاملاً كما كان (والعبارة
    // النائبة في شريط البحث صادقة على ذلك).
    var searchChapterOnly by remember { mutableStateOf(false) }
    var expandedHadithIds by remember { mutableStateOf(setOf<Int>()) }
    var selectedHadith by remember { mutableStateOf<FullSahihHadith?>(null) }
    var showDetailsDialog by remember { mutableStateOf(false) }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isSpeaking by remember { mutableStateOf(false) }
    var speakingHadithId by remember { mutableStateOf<Int?>(null) }

    var visibleHadithCount by remember { mutableStateOf(30) }
    // حالة تمرير مستقلة لكل قائمة: حالة واحدة مشتركة كانت تجعل فتح
    // باب بعد التمرير في قائمة الأبواب يهبط بقائمة الأحاديث إلى
    // أسفلها (الموضع القديم يتجاوز 30 عنصراً فيُثبَّت عند النهاية).
    val chaptersListState = rememberLazyListState()
    val hadithsListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val activeListState = when {
        searchQuery.isNotBlank() -> searchListState
        selectedChapterId != null -> hadithsListState
        else -> chaptersListState
    }

    val shouldLoadMore by remember(activeListState) {
        derivedStateOf {
            val lastVisible = activeListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
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
        // استئناف آخر باب فُتح (قبل هذه الجولة كان القارئ الكامل يبدأ
        // من قائمة الأبواب كل مرة بلا حفظ، خلاف قارئ المقتطفات).
        val savedChapterId = LibraryReadingStateStore.getSahihChapterId(context, bookType.id)
        if (savedChapterId != null && chapters.any { it.id == savedChapterId }) {
            selectedChapterId = savedChapterId
        }
        isLoading = false
    }

    LaunchedEffect(selectedChapterId) {
        val chId = selectedChapterId ?: return@LaunchedEffect
        hadithsListState.scrollToItem(0)
        LibraryReadingStateStore.saveSahihChapterId(context, bookType.id, chId)
        isLoadingHadiths = true
        hadiths = FullSahihHadithRepository.getHadithsForChapter(context, bookType, chId)
        isLoadingHadiths = false
    }

    LaunchedEffect(bookType, searchQuery, searchChapterOnly, selectedChapterId) {
        if (searchQuery.isBlank()) {
            searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        searchListState.scrollToItem(0)
        kotlinx.coroutines.delay(300)
        searchResults = FullSahihHadithRepository.searchHadiths(
            context,
            bookType,
            searchQuery,
            chapterId = if (searchChapterOnly) selectedChapterId else null
        )
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
        // ttsInit متغير لا قيمة: الإشارة إليه داخل مستمع التهيئة
        // نفسه مستحيلة مع val (المتغير خارج نطاق مهيّئه) — فيُعلَن
        // أولاً ثم يُسند، فيبقى داخل المستمع وخارجه بالاسم نفسه.
        lateinit var ttsInit: TextToSpeech
        ttsInit = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsInit.language = Locale("ar")
            }
        }
        // مستمع اكتمال واحد: بلا مستمع كان isSpeaking يبقى عالقاً بعد
        // انتهاء الحديث طبيعياً (البطاقة خضراء وأيقونة إيقاف إلى الأبد).
        // تُصفّى الحالة فقط إن طابق معرّف المنطوق الحديث الجاري نطقه،
        // حتى لا يمسح خطأ نطقٍ مُطفأ حالة نطق جديد بدأ بعده.
        ttsInit.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == speakingHadithId?.let { "hadith_$it" }) {
                    isSpeaking = false
                    speakingHadithId = null
                }
            }
            override fun onError(utteranceId: String?) {
                if (utteranceId == speakingHadithId?.let { "hadith_$it" }) {
                    isSpeaking = false
                    speakingHadithId = null
                }
            }
        })
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
        // ق145: زر القراءة كان يفشل بصمت — TTS غير مهيأ بعد أو غير
        // متاح على الجهاز فيُتجاهل النقر بلا أي رد.
        val currentTts = tts ?: run {
            Toast.makeText(context, "القراءة الصوتية غير جاهزة بعد — أعد المحاولة", Toast.LENGTH_SHORT).show()
            return
        }
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
                Toast.makeText(context, "تعذّرت القراءة الصوتية على هذا الجهاز", Toast.LENGTH_SHORT).show()
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
    // ق82: خريطة اسم الباب لكل نتيجة بحث — بطاقة النتيجة كانت بلا
    // أي سياق (رقم الحديث فقط) فلا يعرف المستخدم من أي باب جاءت.
    val chapterNames = remember(chapters) {
        chapters.associate { it.id to it.arabic }
    }

    var showQuiz by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = theme.background,
        topBar = {
            AppleTopBar(
                title = bookTitle,
                subtitle = selectedChapter?.arabic ?: "${chapters.size} كتاباً",
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

                // ق82: مرشّح نطاق البحث — يظهر فقط أثناء البحث وباب مفتوح.
                if (searchQuery.isNotBlank() && selectedChapter != null) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = searchChapterOnly,
                            onClick = { searchChapterOnly = !searchChapterOnly },
                            label = {
                                Text(
                                    "في هذا الباب فقط",
                                    fontFamily = CairoFont,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (searchChapterOnly) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.FilterList,
                                    contentDescription = null,
                                    tint = if (searchChapterOnly) theme.primary else theme.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = theme.primary.copy(alpha = 0.16f),
                                selectedLabelColor = theme.primary,
                                containerColor = theme.cardSurface,
                                labelColor = theme.textSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = searchChapterOnly,
                                borderColor = if (searchChapterOnly) theme.primary else theme.separator
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
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
                                if (searchResults.size >= 50) "أول 50 نتيجة — قد توجد المزيد؛ ضيّق كلمات البحث"
                                else if (searchChapterOnly && selectedChapter != null) "نتائج البحث في «${selectedChapter.arabic}»: ${searchResults.size}"
                                else "نتائج البحث: ${searchResults.size}",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        } else {
                            Icon(Icons.Default.AutoStories, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                // ق126: موضع الكتاب داخل الصحيح — كان
                                // الشريط يعرض الاسم وحده بلا «الكتاب X من Y».
                                if (selectedChapter != null) {
                                    val idx = chapters.indexOfFirst { it.id == selectedChapterId }
                                    val base = if (idx >= 0) "الكتاب ${idx + 1} من ${chapters.size} • ${selectedChapter.arabic}" else selectedChapter.arabic
                                    // ق145: تقدّم القراءة داخل الباب — كان الشريط يعرض
                                    // موضع الباب وحده بلا عدد أحاديثه، فلا يعرف القارئ
                                    // حجم الباب؛ يُعرض العدد بعد اكتمال التحميل فقط
                                    // حتى لا يكذّب «0 حديثاً» أثناء التحميل أو يعرض
                                    // عدّاد الباب السابق.
                                    if (!isLoadingHadiths && hadiths.isNotEmpty()) "$base • ${hadiths.size} حديثاً" else base
                                } else "${chapters.size} كتاب • تصفح أو ابحث في جميع الأحاديث",
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
                                state = searchListState,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                itemsIndexed(searchResults.take(visibleHadithCount)) { _, hadith ->
                                    FullHadithCard(
                                        hadith = hadith,
                                        chapterName = chapterNames[hadith.chapterId],
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
                                state = chaptersListState,
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
                                state = hadithsListState,
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
                Column {
                    Text(
                        "حديث رقم ${selectedHadith!!.idInBook}",
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold
                    )
                    // ق130: حوار التفاصيل كان يعرض الرقم والنص وحدهما —
                    // بلا كتاب ولا باب، رغم أن الترويسة خلفه تحملهما
                    // ويغطيها الحوار عند فتحه.
                    val dialogChapterName = chapterNames[selectedHadith!!.chapterId]
                    Text(
                        text = if (dialogChapterName != null) "$bookTitle • باب: $dialogChapterName" else bookTitle,
                        color = theme.textSecondary,
                        fontFamily = CairoFont,
                        fontSize = 12.sp
                    )
                }
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
    onClickSpeak: () -> Unit,
    // ق82: اسم الباب — يُمرَّر في نتائج البحث فقط؛ في تصفح الباب
    // السياق معلوم أصلاً فلا يُعرض.
    chapterName: String? = null
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

            // ق82: باب النتيجة في وضع البحث — بلا هذا السطر كانت
            // النتيجة رقماً بلا مصدر.
            if (chapterName != null) {
                Text(
                    text = chapterName,
                    color = theme.textSecondary,
                    fontFamily = CairoFont,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
            }

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
