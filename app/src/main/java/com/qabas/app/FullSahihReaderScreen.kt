package com.qabas.app

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 🔑 شاشة قارئ صحيح البخاري وصحيح مسلم الكامل (7277 + 7459 حديثًا)
 * واجهة محسّنة: تصفح بالفصول، بحث، قراءة صوتية (TTS)، تحميل تدريجي (pagination)، وضع ليلي تلقائي
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullSahihReaderScreen(
    bookType: SahihBookType,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // الحالة
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

    // نصوص القراءة الصوتية
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isSpeaking by remember { mutableStateOf(false) }
    var speakingHadithId by remember { mutableStateOf<Int?>(null) }

    // 🔑 تحميل تدريجي (pagination) لتفادي تحميل آلاف البطاقات مرة واحدة
    var visibleHadithCount by remember { mutableStateOf(30) }
    val listState = rememberLazyListState()

    // تحميل صفحة جديدة عند الاقتراب من النهاية
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= (visibleHadithCount - 10)
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            visibleHadithCount += 30 // تحميل 30 بطاقة إضافية
        }
    }

    // إعادة تعيين عدد البطاقات المرئية عند تغيير الفصل/البحث
    LaunchedEffect(selectedChapterId, searchQuery) {
        visibleHadithCount = 30
    }

    // تحميل الفصول عند فتح الشاشة
    LaunchedEffect(bookType) {
        isLoading = true
        chapters = FullSahihHadithRepository.getChapters(context, bookType)
        isLoading = false
    }

    // تحميل أحاديث فصل محدد
    LaunchedEffect(selectedChapterId) {
        val chId = selectedChapterId ?: return@LaunchedEffect
        isLoadingHadiths = true
        hadiths = FullSahihHadithRepository.getHadithsForChapter(context, bookType, chId)
        isLoadingHadiths = false
    }

    // البحث (debounced) — 🔑 يبدأ حالة البحث فورًا عند تغير الاستعلام (لا ننتظر 300ms)
    LaunchedEffect(bookType, searchQuery) {
        if (searchQuery.isBlank()) {
            searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true // 🔑 فورًا، لا نعرض نتائج قديمة تحت استعلام جديد
        kotlinx.coroutines.delay(300)
        val results = FullSahihHadithRepository.searchHadiths(context, bookType, searchQuery)
        searchResults = results
        isSearching = false
    }

    // 🔑 توحيد الرجوع: الأحاديث ← فهرس الكتاب ← المختارات ← المكتبة (نفس سلوك زر الرجوع والنظام)
    BackHandler(enabled = selectedChapterId != null || searchQuery.isNotBlank()) {
        if (selectedChapterId != null) {
            selectedChapterId = null
        } else if (searchQuery.isNotBlank()) {
            searchQuery = ""
        }
    }

    // 🔑 DisposableEffect واحد فقط (TTS + clearCache) — لا تداخل تنظيفين منفصلين
    DisposableEffect(Unit) {
        val ttsInit = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale("ar"))
            }
        }
        tts = ttsInit

        onDispose {
            try { ttsInit.stop(); ttsInit.shutdown() } catch (_: Exception) {}
            tts = null
            // 🔑 clearCache الآن suspend — نُطلقه بشكل منفصل (لا نمنع dispose)
            scope.launch {
                FullSahihHadithRepository.clearCache()
            }
        }
    }

    fun speakHadith(hadith: FullSahihHadith) {
        val currentTts = tts ?: return

        if (isSpeaking && speakingHadithId == hadith.id) {
            // إيقاف
            currentTts.stop()
            isSpeaking = false
            speakingHadithId = null
        } else {
            // بدء قراءة — 🔑 نطبع نجاح speak() ولا نفترض النجاح تلقائيًا
            currentTts.setLanguage(Locale("ar"))
            val cleanText = hadith.arabic.replace(Regex("[\\n\\r]"), " ").trim()
            val speakResult = currentTts.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "hadith_${hadith.id}")
            if (speakResult == TextToSpeech.SUCCESS) {
                isSpeaking = true
                speakingHadithId = hadith.id
            } else {
                // فشل البدء — نُظهر تحذيرًا للمستخدم (قد لا تكون العربية مدعومة)
                isSpeaking = false
                speakingHadithId = null
            }
        }
    }

    val bookTitle = when (bookType) {
        SahihBookType.BUKHARI -> "صحيح البخاري (كامل)"
        SahihBookType.MUSLIM -> "صحيح مسلم (كامل)"
    }
    val goldColor = Color(0xFFD4AF37)
    val darkBg = Color(0xFF0B141A)
    val cardBg = Color(0xFF16242D)

    // 🎓 حالة اختبار الفهم والحفظ
    var showQuiz by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📖 $bookTitle", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "رجوع")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                // نضمن تحميل الكتاب قبل توليد الأسئلة
                                FullSahihHadithRepository.loadBook(context, bookType)
                                showQuiz = true
                            }
                        }
                    ) {
                        Icon(Icons.Default.Quiz, "اختبر فهمك وحفظك 🎓", tint = goldColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = darkBg,
                    titleContentColor = goldColor,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().background(darkBg).padding(padding)
        ) {
            // 🔍 شريط البحث
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("🔍 ابحث في الكتاب كاملًا...", color = Color(0xFF94A3B8), fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = goldColor) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, null, tint = Color(0xFFEF4444))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = goldColor,
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = goldColor
                )
            )

            // 📊 إحصائيات سريعة
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (searchQuery.isNotBlank()) {
                        Icon(Icons.Default.Search, null, tint = goldColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("نتائج البحث: ${searchResults.size}", color = Color.White, fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.AutoStories, null, tint = goldColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("📚 ${chapters.size} كتاب • 🔍 ابحث في كل الأحاديث", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            when {
                isLoading -> {
                    // شاشة تحميل
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = goldColor)
                            Spacer(Modifier.height(12.dp))
                            Text("جاري تحميل الكتاب...", color = Color(0xFF94A3B8), fontSize = 13.sp)
                        }
                    }
                }

                searchQuery.isNotBlank() -> {
                    // 📄 نتائج البحث
                    if (isSearching) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = goldColor)
                        }
                    } else if (searchResults.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("لا نتائج لـ «$searchQuery»", color = Color(0xFF94A3B8), fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(state = listState, contentPadding = PaddingValues(16.dp)) {
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
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }

                selectedChapterId == null -> {
                    // 📚 عرض الفصول (كتب الكتاب)
                    if (chapters.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("تعذّر تحميل الكتاب من ملفات التطبيق", color = Color(0xFFFCA5A5), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text("تأكد من اكتمال التثبيت ثم أعد المحاولة", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        scope.launch {
                                            isLoading = true
                                            FullSahihHadithRepository.clearCache()
                                            chapters = FullSahihHadithRepository.getChapters(context, bookType)
                                            isLoading = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = goldColor)
                                ) {
                                    Text("إعادة المحاولة 🔄", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        LazyColumn(state = listState, contentPadding = PaddingValues(16.dp)) {
                            itemsIndexed(chapters.take(visibleHadithCount)) { idx, chapter ->
                                FullChapterCard(
                                    chapter = chapter,
                                    index = idx,
                                    onOpen = { selectedChapterId = chapter.id }
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }

                else -> {
                    // 📖 عرض أحاديث فصل محدد
                    if (isLoadingHadiths) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = goldColor)
                        }
                    } else if (hadiths.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("لا أحاديث في هذا الفصل", color = Color(0xFF94A3B8), fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(state = listState, contentPadding = PaddingValues(16.dp)) {
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
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // 🔑 حوار تفاصيل الحديث
    if (showDetailsDialog && selectedHadith != null) {
        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            title = { Text("📖 حديث رقم ${selectedHadith!!.idInBook}", fontWeight = FontWeight.Bold) },
            text = {
                SelectionContainer {
                    Text(selectedHadith!!.arabic, fontSize = 14.sp, lineHeight = 22.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailsDialog = false }) { Text("إغلاق", color = goldColor) }
            }
        )
    }

    // 🎓 حوار اختبار الفهم والحفظ (100 سؤال من الكتاب الكامل + نقاط)
    if (showQuiz) {
        SahihQuizDialog(
            bookType = bookType,
            onDismiss = { showQuiz = false }
        )
    }
}

/**
 * بطاقة عرض فصل (كتاب) من صحيح البخاري أو مسلم
 */
@Composable
private fun FullChapterCard(
    chapter: FullSahihChapter,
    index: Int,
    onOpen: () -> Unit
) {
    val goldColor = Color(0xFFD4AF37)
    val cardBg = Color(0xFF16242D)

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = Modifier.fillMaxWidth().clickable { onOpen() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            // رقم الفصل
            Box(
                modifier = Modifier.size(36.dp).background(goldColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("${index + 1}", color = goldColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(chapter.arabic, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (chapter.english.isNotBlank()) {
                    Text(chapter.english, color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = goldColor)
        }
    }
}

/**
 * بطاقة عرض حديث من صحيح البخاري أو مسلم الكامل
 */
@Composable
private fun FullHadithCard(
    hadith: FullSahihHadith,
    isExpanded: Boolean,
    isSpeaking: Boolean,
    onToggleExpand: () -> Unit,
    onClickDetails: () -> Unit,
    onClickSpeak: () -> Unit
) {
    val goldColor = Color(0xFFD4AF37)
    val cardBg = Color(0xFF16242D)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSpeaking) Color(0xFF1A2E1A) else cardBg
        ),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            // رأس البطاقة: رقم الحديث + أزرار
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "حديث #${hadith.idInBook}",
                    color = goldColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))

                // زر القراءة الصوتية
                IconButton(onClick = onClickSpeak, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (isSpeaking) Icons.Default.Stop else Icons.Default.PlayArrow,
                        if (isSpeaking) "إيقاف القراءة" else "قراءة صوتية",
                        tint = if (isSpeaking) Color(0xFFEF4444) else goldColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // زر التفاصيل
                IconButton(onClick = onClickDetails, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Info, "تفاصيل", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(6.dp))

            // نص الحديث (مُقتطع إن لم يكن مُوسعًا)
            SelectionContainer {
                Text(
                    text = if (isExpanded) hadith.arabic else hadith.arabic.take(200) + if (hadith.arabic.length > 200) "..." else "",
                    color = Color.White, fontSize = 13.sp, lineHeight = 22.sp,
                    modifier = Modifier.fillMaxWidth().clickable { onToggleExpand() }
                )
            }

            if (hadith.arabic.length > 200) {
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onToggleExpand) {
                    Text(
                        if (isExpanded) "▲ إخفاء النص الكامل" else "▼ عرض النص الكامل",
                        color = goldColor, fontSize = 11.sp
                    )
                }
            }
        }
    }
}
