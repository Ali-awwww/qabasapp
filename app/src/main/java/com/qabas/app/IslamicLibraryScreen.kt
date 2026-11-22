package com.qabas.app

import android.content.Intent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.qabas.app.ui.TestTags
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleSearchBar
import com.qabas.app.ui.components.AppleTopBar
import com.qabas.app.ui.theme.*
import java.util.Locale

@Composable
fun IslamicLibraryScreen(
    onBack: () -> Unit,
    onNavigateToVideoCreation: (quoteText: String) -> Unit = {},
    // فتح كتاب أو حديث محدد من رابط بحث عميق (الدفعة 5 — الجزء C).
    // معرّف غير معروف يُتجاهل وتُعرض المكتبة كما هي — لا يُفتح مكان خاطئ.
    initialBookId: String? = null,
    initialHadithId: String? = null
) {
    val theme = QabasTheme.current
    val context = LocalContext.current

    var selectedAuthorId by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf("الكل") }
    var searchQuery by remember { mutableStateOf("") }
    var activeBookForReading by remember { mutableStateOf<IslamicBook?>(null) }
    var activeBookReadingPosition by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var activeSahihBookType by remember { mutableStateOf<SahihBookType?>(null) }
    var openFullSahihBook by remember { mutableStateOf(false) }
    var showProgressionDialog by remember { mutableStateOf(false) }
    var authorBio by remember { mutableStateOf<IslamicAuthor?>(null) }

    // حالة القراءة المحلية (آخر موضع + نسبة + إشارات)؛ تُعاد قراءتها كلما
    // أُغلق القارئ حتى تنعكس «أكمل القراءة» ونسب البطاقات فوراً.
    var readingStatesVersion by remember { mutableIntStateOf(0) }
    var readingStates by remember { mutableStateOf<List<LibraryReadingState>>(emptyList()) }
    LaunchedEffect(readingStatesVersion) {
        readingStates = LibraryReadingStateStore.fetchAndMergeCloudStates(context)
    }
    val readingStateByBookId = remember(readingStates) {
        readingStates.associateBy { it.bookId }
    }

    // رابط عميق لمرة واحدة: كتاب → قارئه، حديث → قارئ الصحيحين عنده.
    LaunchedEffect(initialBookId, initialHadithId) {
        if (initialHadithId != null) {
            SahihHadithsRepository.getHadithById(initialHadithId)?.let { item ->
                activeSahihBookType = item.bookType
            }
        } else if (initialBookId != null) {
            IslamicLibraryRepository.books.firstOrNull { it.id == initialBookId }?.let { book ->
                if (book.id == "sahih_al_bukhari") {
                    activeSahihBookType = SahihBookType.BUKHARI
                } else if (book.id == "sahih_muslim") {
                    activeSahihBookType = SahihBookType.MUSLIM
                } else {
                    activeBookForReading = book
                }
            }
        }
    }

    BackHandler(enabled = activeSahihBookType != null || activeBookForReading != null) {
        if (activeSahihBookType != null) {
            activeSahihBookType = null
        } else if (activeBookForReading != null) {
            activeBookForReading = null
            activeBookReadingPosition = null
            readingStatesVersion++
        }
    }

    if (activeSahihBookType != null) {
        if (openFullSahihBook) {
            FullSahihReaderScreen(
                bookType = activeSahihBookType!!,
                onBack = {
                    openFullSahihBook = false
                    activeSahihBookType = null
                }
            )
            return
        }

        SahihReaderView(
            initialBookType = activeSahihBookType!!,
            initialHadithId = initialHadithId,
            onBack = { activeSahihBookType = null },
            onNavigateToVideoCreation = onNavigateToVideoCreation,
            onOpenFullBook = { bookType ->
                activeSahihBookType = bookType
                openFullSahihBook = true
            }
        )
        return
    }

    val filteredBooks = remember(selectedAuthorId, selectedCategory, searchQuery) {
        IslamicLibraryRepository.books.filter { book ->
            val matchAuthor = selectedAuthorId == null || book.authorId == selectedAuthorId
            val matchCategory = selectedCategory == "الكل" || book.category == selectedCategory
            val matchQuery = searchQuery.isBlank() ||
                book.title.contains(searchQuery, ignoreCase = true) ||
                book.authorName.contains(searchQuery, ignoreCase = true) ||
                book.description.contains(searchQuery, ignoreCase = true) ||
                book.hookTitle.contains(searchQuery, ignoreCase = true)
            matchAuthor && matchCategory && matchQuery
        }
    }

    // بحث نصي كامل داخل نص الكتب (لا العناوين فقط): يطابق نص المقاطع
    // أيضاً، وكل نتيجة تفتح القارئ على المقطع المطابق نفسه. يُحترم
    // فلترا المؤلف والتصنيف المختاران كما في قائمة الكتب.
    val fullTextResults = remember(searchQuery, selectedAuthorId, selectedCategory) {
        if (searchQuery.isBlank()) emptyList()
        else IslamicLibraryRepository.searchBooks(searchQuery).filter { result ->
            val matchAuthor = selectedAuthorId == null || result.book.authorId == selectedAuthorId
            val matchCategory = selectedCategory == "الكل" || result.book.category == selectedCategory
            matchAuthor && matchCategory
        }
    }

    // كتب «أكمل القراءة»: لها موضع محفوظ وتقدم أكبر من صفر، الأحدث أولاً.
    val continueReading = remember(readingStates) {
        readingStates.filter { it.progressPercent > 0 }
            .mapNotNull { state -> IslamicLibraryRepository.findBook(state.bookId)?.let { it to state } }
    }
    val bookmarkedBookIds = remember(readingStates) {
        readingStates.filter { it.bookmarked }.map { it.bookId }.toSet()
    }

    Scaffold(
        modifier = Modifier.testTag(TestTags.ISLAMIC_LIBRARY_SCREEN),
        containerColor = theme.background,
        topBar = {
            AppleTopBar(
                title = "المكتبة الإسلامية الجامعة",
                subtitle = "أمهات كتب الأئمة والسلف • قراءة واستماع صوتي",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = { showProgressionDialog = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.School, contentDescription = "سلسلة التدرج الفقهي", tint = theme.primary)
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Single unified Apple Search Bar with preserved TestTag
                item {
                    AppleSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = "ابحث في كتب السلف، المجلدات، أو أسماء الأئمة...",
                        modifier = Modifier.testTag(TestTags.SEARCH_FIELD)
                    )
                }

                // Featured Sahihayn Banner (البخاري ومسلم)
                item {
                    AppleCard(cornerRadius = 18.dp) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(theme.primary.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = theme.primary, modifier = Modifier.size(22.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("الصحيحان: البخاري ومسلم", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text("أصح كتابين في الإسلام بعد كتاب الله", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.5.sp)
                                    }
                                }

                                Surface(
                                    color = theme.success.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        "محقق",
                                        color = theme.success,
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                "عيون السنة النبوية المطهرة بأصح الأسانيد المتصلة، مع استماع صوتي واختبارات تدبر وتحويل مباشر لريلز دعوي ملهم.",
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 12.5.sp,
                                lineHeight = 19.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { activeSahihBookType = SahihBookType.BUKHARI },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("صحيح البخاري", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                OutlinedButton(
                                    onClick = { activeSahihBookType = SahihBookType.MUSLIM },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.success),
                                    border = BorderStroke(1.dp, theme.success),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.HistoryEdu, contentDescription = null, tint = theme.success, modifier = Modifier.size(17.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("صحيح مسلم", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                // Daily Wisdom Capsule Spotlight
                item {
                    AppleCard(cornerRadius = 16.dp) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(theme.warning.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = theme.warning, modifier = Modifier.size(19.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text("كبسولة الحكمة اليومية", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                        Text("من درر الإمام ابن قيم الجوزية", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.sp)
                                    }
                                }

                                TextButton(
                                    onClick = {
                                        onNavigateToVideoCreation("إضاعة الوقت أشد من الموت؛ لأن إضاعة الوقت تقطعك عن الله والدار الآخرة، والموت يقطعك عن الدنيا وأهلها. — ابن القيم")
                                    },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Icon(Icons.Default.MovieCreation, contentDescription = null, tint = theme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("صناعة فيديو", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                "«إضاعة الوقت أشد من الموت؛ لأن إضاعة الوقت تقطعك عن الله والدار الآخرة، والموت يقطعك عن الدنيا وأهلها».",
                                color = theme.textPrimary,
                                fontFamily = AmiriFont,
                                fontWeight = FontWeight.Medium,
                                fontSize = 17.sp,
                                lineHeight = 28.sp
                            )
                        }
                    }
                }

                // Ibn Qudamah Special Progression Banner
                item {
                    AppleCard(onClick = { showProgressionDialog = true }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(theme.accent.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.School, contentDescription = null, tint = theme.accent, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "سلسلة ابن قدامة الفقهية الأربع الكبرى",
                                        color = theme.textPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp
                                    )
                                    Text(
                                        "منهج التدرج الفقهي في الحفظ والدراسة",
                                        color = theme.textSecondary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "فتح", tint = theme.accent, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Author Filter Chips
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "أعلام وأئمة المكتبة",
                            color = theme.textSecondary,
                            fontFamily = CairoFont,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                val isAll = selectedAuthorId == null
                                Surface(
                                    onClick = {
                                        selectedAuthorId = null
                                        selectedCategory = "الكل"
                                    },
                                    modifier = Modifier.defaultMinSize(minHeight = 42.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isAll) theme.primary else theme.cardSurface,
                                    border = BorderStroke(0.75.dp, if (isAll) theme.primary else theme.separator.copy(alpha = 0.5f))
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                        Text(
                                            "كافة الأئمة (${IslamicLibraryRepository.authors.size})",
                                            color = if (isAll) theme.onPrimary else theme.textPrimary,
                                            fontFamily = CairoFont,
                                            fontSize = 12.sp,
                                            fontWeight = if (isAll) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                            items(IslamicLibraryRepository.authors) { author ->
                                val isSelected = selectedAuthorId == author.id
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        onClick = { selectedAuthorId = author.id },
                                        modifier = Modifier.defaultMinSize(minHeight = 42.dp),
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (isSelected) theme.primary else theme.cardSurface,
                                        border = BorderStroke(0.75.dp, if (isSelected) theme.primary else theme.separator.copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                author.name,
                                                color = if (isSelected) theme.onPrimary else theme.textPrimary,
                                                fontFamily = CairoFont,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "(${IslamicLibraryRepository.bookCountForAuthor(author.id)})",
                                                color = if (isSelected) theme.onPrimary.copy(alpha = 0.85f) else theme.primary,
                                                fontFamily = NotoSansFont,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    // السيرة المكتوبة أصلاً (النبذة والعصر ومجال الاهتمام)
                                    // كانت لا تُعرض في أي مكان؛ زر المعلومات يفتحها الآن.
                                    IconButton(
                                        onClick = { authorBio = author },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = "سيرة ${author.name}",
                                            tint = theme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // «أكمل القراءة»: كتب بدأت قراءتها ولها موضع محفوظ.
                // يظهر فقط بلا بحث، ويفتح كل كتاب على آخر موضع وصل إليه القارئ.
                if (searchQuery.isBlank() && continueReading.isNotEmpty()) {
                    item {
                        AppleCard(cornerRadius = 16.dp) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "أكمل القراءة",
                                        color = theme.textPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                continueReading.take(3).forEach { (book, state) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                book.title,
                                                color = theme.textPrimary,
                                                fontFamily = CairoFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                LinearProgressIndicator(
                                                    progress = { state.progressPercent / 100f },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(6.dp)
                                                        .clip(RoundedCornerShape(3.dp)),
                                                    color = theme.primary,
                                                    trackColor = theme.separator.copy(alpha = 0.4f)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "${state.progressPercent}٪",
                                                    color = theme.textSecondary,
                                                    fontFamily = NotoSansFont,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Button(
                                            onClick = {
                                                if (book.id == "sahih_al_bukhari") {
                                                    activeSahihBookType = SahihBookType.BUKHARI
                                                } else if (book.id == "sahih_muslim") {
                                                    activeSahihBookType = SahihBookType.MUSLIM
                                                } else {
                                                    activeBookReadingPosition =
                                                        state.lastChapterIndex to state.lastSectionIndex
                                                    activeBookForReading = book
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                            modifier = Modifier.heightIn(min = 40.dp)
                                        ) {
                                            Text("متابعة", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Books Header — بلا نص «استماع صوتي» الزخرفي: كان أيقونة
                // وتسمية بلا معالج نقر. الاستماع الحقيقي زر TTS داخل القارئ.
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (searchQuery.isBlank()) "المصنفات المحققة (${filteredBooks.size})"
                            else "نتائج البحث النصي (${fullTextResults.size})",
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp
                        )
                    }
                }

                // نتائج البحث النصي الكامل: كل نتيجة تفتح القارئ على
                // المقطع المطابق نفسه، مع معاينة من موضع المطابقة.
                if (searchQuery.isNotBlank()) {
                    if (fullTextResults.isEmpty()) {
                        item {
                            AppleCard(cornerRadius = 16.dp) {
                                Text(
                                    "لا نتائج مطابقة في نص الكتب لهذا البحث.",
                                    color = theme.textSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 12.5.sp,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(fullTextResults, key = { "${it.book.id}:${it.chapterIndex}:${it.sectionIndex}" }) { result ->
                            AppleCard(
                                onClick = {
                                    activeBookReadingPosition = result.chapterIndex to result.sectionIndex
                                    activeBookForReading = result.book
                                }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            result.book.title,
                                            color = theme.textPrimary,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (result.book.isExcerpt) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                color = theme.warning.copy(alpha = 0.14f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    "مقتطفات",
                                                    color = theme.warning,
                                                    fontFamily = CairoFont,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "${result.book.authorName} • ${result.book.chapters.getOrNull(result.chapterIndex)?.sections?.getOrNull(result.sectionIndex)?.sectionTitle ?: ""}",
                                        color = theme.primary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                    if (result.matchPreview.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            result.matchPreview,
                                            color = theme.textSecondary,
                                            fontFamily = NotoSansFont,
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Books Cards
                    items(filteredBooks, key = { it.id }) { book ->
                        IslamicBookCard(
                            book = book,
                            progressPercent = readingStateByBookId[book.id]?.progressPercent ?: 0,
                            isBookmarked = bookmarkedBookIds.contains(book.id),
                            onToggleBookmark = {
                                LibraryReadingStateStore.toggleBookmark(context, book.id)
                                readingStatesVersion++
                            },
                            onOpenReader = {
                                if (book.id == "sahih_al_bukhari") {
                                    activeSahihBookType = SahihBookType.BUKHARI
                                } else if (book.id == "sahih_muslim") {
                                    activeSahihBookType = SahihBookType.MUSLIM
                                } else {
                                    val state = readingStateByBookId[book.id]
                                    activeBookReadingPosition =
                                        if (state != null) state.lastChapterIndex to state.lastSectionIndex
                                        else null
                                    activeBookForReading = book
                                }
                            },
                            onReelDirect = {
                                val firstSection = book.chapters.firstOrNull()?.sections?.firstOrNull()
                                val quote = firstSection?.wisdomCapsule?.ifBlank { firstSection.keyRuleOrBenefit } ?: book.description
                                onNavigateToVideoCreation(quote)
                            }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }

    activeBookForReading?.let { book ->
        InteractiveBookReaderDialog(
            book = book,
            initialChapterIndex = activeBookReadingPosition?.first ?: 0,
            initialSectionIndex = activeBookReadingPosition?.second ?: 0,
            onPositionChanged = { chapterIndex, sectionIndex ->
                LibraryReadingStateStore.savePosition(
                    context = context,
                    bookId = book.id,
                    chapterIndex = chapterIndex,
                    sectionIndex = sectionIndex,
                    progressPercent = IslamicLibraryRepository.progressPercent(book, chapterIndex, sectionIndex)
                )
            },
            onDismiss = {
                activeBookForReading = null
                activeBookReadingPosition = null
                readingStatesVersion++
            },
            onConvertToReel = { quote ->
                activeBookForReading = null
                activeBookReadingPosition = null
                readingStatesVersion++
                onNavigateToVideoCreation(quote)
            }
        )
    }

    // سيرة المؤلف: النبذة والعصر ومجال الاهتمام المكتوبة أصلاً في
    // IslamicLibraryRepository.authors — كانت لا تُعرض في أي مكان.
    authorBio?.let { author ->
        AlertDialog(
            onDismissRequest = { authorBio = null },
            containerColor = theme.cardSurface,
            shape = RoundedCornerShape(18.dp),
            title = {
                Text(author.name, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = theme.textPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(author.title, fontFamily = CairoFont, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, color = theme.primary)
                    Text("العصر: ${author.era}", fontFamily = NotoSansFont, fontSize = 12.sp, color = theme.textSecondary)
                    Text("مجال الاهتمام: ${author.focusArea}", fontFamily = NotoSansFont, fontSize = 12.sp, color = theme.textSecondary)
                    Text(
                        "كتب المؤلف في المكتبة: ${IslamicLibraryRepository.bookCountForAuthor(author.id)}",
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp,
                        color = theme.textSecondary
                    )
                    Text(author.bio, fontFamily = NotoSansFont, fontSize = 12.5.sp, lineHeight = 19.sp, color = theme.textPrimary)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedAuthorId = author.id
                        authorBio = null
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("عرض كتبه", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { authorBio = null },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("إغلاق", color = theme.textSecondary, fontFamily = CairoFont)
                }
            }
        )
    }

    if (showProgressionDialog) {
        // الكتب الأربعة كانت كلها في `archivedBooks` لا يفتحها شيء؛
        // بعد إطلاقها يفتح كل عنصر كتابه مباشرة من هذا الحوار.
        val openProgressionBook: (String) -> Unit = { bookId ->
            IslamicLibraryRepository.findBook(bookId)?.let { book ->
                showProgressionDialog = false
                activeBookReadingPosition = null
                activeBookForReading = book
            }
        }
        AlertDialog(
            onDismissRequest = { showProgressionDialog = false },
            containerColor = theme.cardSurface,
            shape = RoundedCornerShape(18.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.School, contentDescription = null, tint = theme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("منهج التدرج الفقهي عند ابن قدامة", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = theme.textPrimary)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "وضع الإمام موفق الدين بن قدامة رحمه الله أربعة كتب فقهية متدرجة تُعد أعظم منهج تربوي فقهي في تاريخ الإسلام:",
                        fontFamily = NotoSansFont,
                        color = theme.textSecondary,
                        fontSize = 12.sp
                    )
                    ProgressionExplainerItem("1. «عمدة الفقه» (للمبتدئين)", "يقتصر على قول واحد معتمد في المذهب، بلا تفريع ولا ذكر أدلة، لتثبيت الفروع في ذهن المبتدئ.", theme.primary, onClick = { openProgressionBook("umdat_al_fiqh") })
                    ProgressionExplainerItem("2. «المقنع» (للمتوسطين)", "يذكر فيه الروايات والأوجه في المذهب لتدريب الطالب على الخلاف المذهبي وملكة الترجيح.", theme.accent, onClick = { openProgressionBook("al_muqni") })
                    ProgressionExplainerItem("3. «الكافي» (للمتقدمين)", "يقرن المسائل الفقهية بأدلتها التفصيلية من الكتاب والسنة مع بسط العلل الشرعية.", theme.success, onClick = { openProgressionBook("al_kafi") })
                    ProgressionExplainerItem("4. «المغني» (للمجتهدين والمتخصصين)", "موسوعة الفقه المقارن الكبرى، يورد أدلة المذاهب الأربعة وأقوال الصحابة والتابعين والترجيح المنصف.", Color(0xFFAF52DE), onClick = { openProgressionBook("al_mughni") })
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showProgressionDialog = false },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("فهمت المنهج", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun InteractiveBookReaderDialog(
    book: IslamicBook,
    initialChapterIndex: Int = 0,
    initialSectionIndex: Int = 0,
    onPositionChanged: ((chapterIndex: Int, sectionIndex: Int) -> Unit)? = null,
    onDismiss: () -> Unit,
    onConvertToReel: (quoteText: String) -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val clipboardManager = LocalClipboardManager.current

    // تُعاد التهيئة لكل كتاب وموضع افتتاح (نتائج البحث تفتح على المقطع
    // المطابق، و«أكمل القراءة» على آخر موضع)، لا على أول الكتاب دائماً.
    var selectedChapterIndex by remember(book.id, initialChapterIndex) {
        mutableIntStateOf(initialChapterIndex.coerceIn(0, (book.chapters.size - 1).coerceAtLeast(0)))
    }
    var selectedSectionIndex by remember(book.id, initialChapterIndex, initialSectionIndex) {
        val maxSection =
            (book.chapters.getOrNull(initialChapterIndex)?.sections?.size ?: 1) - 1
        mutableIntStateOf(initialSectionIndex.coerceIn(0, maxSection.coerceAtLeast(0)))
    }
    var fontSizeSp by remember { mutableFloatStateOf(15.5f) }

    var isSpeaking by remember { mutableStateOf(false) }
    val ttsRate by remember { mutableFloatStateOf(1.0f) }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }

    // إشارة مرجعية على مستوى الكتاب (لا الأحاديث فقط).
    var bookmarked by remember(book.id) {
        mutableStateOf(LibraryReadingStateStore.isBookmarked(context, book.id))
    }

    val currentChapter = book.chapters.getOrNull(selectedChapterIndex) ?: book.chapters.first()
    val currentSection = currentChapter.sections.getOrNull(selectedSectionIndex) ?: currentChapter.sections.first()

    var selectedQuizOption by remember { mutableStateOf<Int?>(null) }
    var isQuizAnswered by remember { mutableStateOf(false) }

    LaunchedEffect(currentSection.id) {
        selectedQuizOption = null
        isQuizAnswered = false
        ttsEngine?.stop()
        isSpeaking = false
        // حفظ آخر موضع قراءة عند كل انتقال بين المقاطع.
        onPositionChanged?.invoke(selectedChapterIndex, selectedSectionIndex)
    }

    DisposableEffect(Unit) {
        var initializedEngine: TextToSpeech? = null
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                initializedEngine?.language = Locale("ar")
            }
        }
        initializedEngine = tts
        ttsEngine = tts
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    fun toggleSpeech(text: String) {
        val tts = ttsEngine ?: return
        if (isSpeaking) {
            tts.stop()
            isSpeaking = false
        } else {
            tts.setSpeechRate(ttsRate)
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reader_tts")
            isSpeaking = true
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = theme.background,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(0.75.dp, theme.separator.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(color = theme.cardSurface, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = theme.primary)
                                }
                                Column {
                                    Text(
                                        book.title,
                                        color = theme.textPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${book.authorName} • ${currentSection.readTimeMinutes} دقيقة",
                                        color = theme.textSecondary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        bookmarked = LibraryReadingStateStore.toggleBookmark(context, book.id)
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = if (bookmarked) "إزالة الإشارة المرجعية" else "إشارة مرجعية",
                                        tint = if (bookmarked) theme.primary else theme.textSecondary
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val fullReadingText = "${currentSection.sectionTitle}. ${currentSection.wisdomCapsule}. ${currentSection.content}. ${currentSection.keyRuleOrBenefit}"
                                        toggleSpeech(fullReadingText)
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        if (isSpeaking) Icons.Default.Pause else Icons.Default.VolumeUp,
                                        contentDescription = "استماع",
                                        tint = if (isSpeaking) theme.destructive else theme.primary
                                    )
                                }

                                IconButton(
                                    onClick = { if (fontSizeSp > 12f) fontSizeSp -= 1.5f },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Text("A-", color = theme.textSecondary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                IconButton(
                                    onClick = { if (fontSizeSp < 23f) fontSizeSp += 1.5f },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Text("A+", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = theme.separator.copy(alpha = 0.5f))
                    }
                }

                // Chapter Tabs
                if (book.chapters.size > 1) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.cardSurface)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(book.chapters.indices.toList()) { index ->
                            val ch = book.chapters[index]
                            val isSelected = selectedChapterIndex == index
                            Surface(
                                onClick = {
                                    selectedChapterIndex = index
                                    selectedSectionIndex = 0
                                },
                                modifier = Modifier.defaultMinSize(minHeight = 38.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) theme.primary else theme.secondarySurface
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    Text(
                                        ch.chapterTitle,
                                        color = if (isSelected) theme.onPrimary else theme.textPrimary,
                                        fontFamily = CairoFont,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Section Tabs
                if (currentChapter.sections.size > 1) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.secondarySurface.copy(alpha = 0.35f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(currentChapter.sections.indices.toList()) { sIdx ->
                            val sec = currentChapter.sections[sIdx]
                            val isSelected = selectedSectionIndex == sIdx
                            Surface(
                                onClick = { selectedSectionIndex = sIdx },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) theme.cardSurface else Color.Transparent
                            ) {
                                Text(
                                    sec.sectionTitle,
                                    color = if (isSelected) theme.primary else theme.textSecondary,
                                    fontFamily = CairoFont,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Reader Body
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Text(
                            currentSection.sectionTitle,
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = (fontSizeSp + 3).sp
                        )
                    }

                    if (currentSection.wisdomCapsule.isNotBlank()) {
                        item {
                            AppleCard {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = theme.warning, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("كبسولة الفائدة", color = theme.warning, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(currentSection.wisdomCapsule, color = theme.textPrimary, fontFamily = NotoSansFont, fontSize = 12.5.sp, lineHeight = 19.sp)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        AppleCard {
                            Text(
                                text = currentSection.content,
                                color = theme.textPrimary,
                                fontFamily = AmiriFont,
                                fontSize = fontSizeSp.sp,
                                lineHeight = (fontSizeSp * 1.8).sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                    if (currentSection.keyRuleOrBenefit.isNotBlank()) {
                        item {
                            AppleCard {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Verified, contentDescription = null, tint = theme.success, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("القاعدة والضابط الشرعي المستنبط", color = theme.success, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        currentSection.keyRuleOrBenefit,
                                        color = theme.textPrimary,
                                        fontFamily = NotoSansFont,
                                        fontSize = (fontSizeSp - 1).sp,
                                        lineHeight = (fontSizeSp * 1.6).sp
                                    )
                                }
                            }
                        }
                    }

                    currentSection.quiz?.let { quiz ->
                        item {
                            AppleCard {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.HelpOutline, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("اختبر استيعابك للفائدة", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                    }

                                    Text(quiz.question, color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Medium, fontSize = 13.sp)

                                    quiz.options.forEachIndexed { idx, opt ->
                                        val isSelected = selectedQuizOption == idx
                                        val isCorrect = idx == quiz.correctIndex
                                        val optionBg = when {
                                            !isQuizAnswered -> if (isSelected) theme.primary.copy(alpha = 0.14f) else theme.secondarySurface.copy(alpha = 0.55f)
                                            isCorrect -> theme.success.copy(alpha = 0.16f)
                                            isSelected && !isCorrect -> theme.destructive.copy(alpha = 0.16f)
                                            else -> theme.secondarySurface.copy(alpha = 0.45f)
                                        }

                                        Surface(
                                            color = optionBg,
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(
                                                0.75.dp,
                                                when {
                                                    isQuizAnswered && isCorrect -> theme.success
                                                    isQuizAnswered && isSelected -> theme.destructive
                                                    else -> theme.separator.copy(alpha = 0.4f)
                                                }
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .defaultMinSize(minHeight = 44.dp)
                                                .clickable(enabled = !isQuizAnswered) {
                                                    selectedQuizOption = idx
                                                    isQuizAnswered = true
                                                }
                                        ) {
                                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    opt,
                                                    color = if (isQuizAnswered && isCorrect) theme.success else theme.textPrimary,
                                                    fontFamily = NotoSansFont,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }

                                    if (isQuizAnswered) {
                                        Text(
                                            "التوضيح: ${quiz.explanation}",
                                            color = theme.primary,
                                            fontFamily = NotoSansFont,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (currentSection.references.isNotBlank()) {
                        item {
                            Text(
                                "المصدر والتوثيق: ${currentSection.references}",
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Bottom Bar Actions
                Surface(color = theme.cardSurface, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        HorizontalDivider(thickness = 0.5.dp, color = theme.separator.copy(alpha = 0.5f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(
                                    onClick = {
                                        val fullText = "${currentSection.sectionTitle}\n\n${currentSection.content}\n\n${currentSection.keyRuleOrBenefit}\n\n[المصدر: ${book.title} - ${book.authorName}]"
                                        clipboardManager.setText(AnnotatedString(fullText))
                                        Toast.makeText(context, "تم نسخ النص والمصدر بالكامل", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(theme.secondarySurface)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ النص", tint = theme.primary, modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = {
                                        val shareText = "${currentSection.sectionTitle}\n\n${currentSection.content}\n\n${currentSection.keyRuleOrBenefit}\n\nمن كتاب: ${book.title} (المؤلف: ${book.authorName})\nتطبيق خادم المسلم"
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "مشاركة الفائدة الفقهية"))
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(theme.secondarySurface)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = theme.primary, modifier = Modifier.size(18.dp))
                                }
                            }

                            Button(
                                onClick = {
                                    val reelText = if (currentSection.wisdomCapsule.isNotBlank()) currentSection.wisdomCapsule
                                    else if (currentSection.keyRuleOrBenefit.isNotBlank()) currentSection.keyRuleOrBenefit
                                    else currentSection.content.take(160)
                                    onConvertToReel(reelText)
                                },
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.MovieCreation, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تحويل إلى فيديو", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressionExplainerItem(
    stage: String,
    desc: String,
    color: Color,
    onClick: () -> Unit = {}
) {
    val theme = QabasTheme.current
    Surface(
        onClick = onClick,
        color = theme.secondarySurface.copy(alpha = 0.55f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(stage, color = color, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            Spacer(modifier = Modifier.height(3.dp))
            Text(desc, color = theme.textPrimary, fontFamily = NotoSansFont, fontSize = 11.5.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
fun IslamicBookCard(
    book: IslamicBook,
    progressPercent: Int = 0,
    isBookmarked: Boolean = false,
    onToggleBookmark: (() -> Unit)? = null,
    onOpenReader: () -> Unit,
    onReelDirect: () -> Unit
) {
    val theme = QabasTheme.current
    AppleCard(onClick = onOpenReader) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        book.title,
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp
                    )
                    Text(
                        "تأليف: ${book.authorName}",
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 11.5.sp
                    )
                }

                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = theme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            book.levelBadge,
                            color = theme.primary,
                            fontFamily = CairoFont,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    // الكتب التسعة المُطلَقة مقتطفات (فصل واحد ومقطع أو مقطعان)
                    // لا كتب كاملة: شارة صريحة على كل بطاقة منها.
                    if (book.isExcerpt) {
                        Surface(
                            color = theme.warning.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "مقتطفات",
                                color = theme.warning,
                                fontFamily = CairoFont,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (book.hookTitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    book.hookTitle,
                    color = theme.primary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.5.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                book.description,
                color = theme.textSecondary,
                fontFamily = NotoSansFont,
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // نسبة إتمام القراءة المحفوظة محلياً (ومزامَنة عند الدخول).
            if (progressPercent > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { progressPercent / 100f },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = theme.primary,
                        trackColor = theme.separator.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "أكملت $progressPercent٪",
                        color = theme.primary,
                        fontFamily = NotoSansFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (book.scholarQuote.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = theme.secondarySurface.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FormatQuote, contentDescription = null, tint = theme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            book.scholarQuote,
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onToggleBookmark != null) {
                        IconButton(
                            onClick = onToggleBookmark,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (isBookmarked) "إزالة الإشارة المرجعية" else "إشارة مرجعية",
                                tint = if (isBookmarked) theme.primary else theme.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Icon(Icons.Default.Timer, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "~ ${book.totalEstimatedMinutes} دقيقة",
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 11.5.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onReelDirect,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.primary),
                        border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.MovieCreation, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("فيديو", fontFamily = CairoFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onOpenReader,
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("قراءة", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
