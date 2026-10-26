package com.qabas.app

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import com.qabas.app.ui.TestTags
import androidx.compose.ui.platform.testTag
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IslamicLibraryScreen(
    onBack: () -> Unit,
    onNavigateToVideoCreation: (quoteText: String) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var selectedAuthorId by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf("الكل") }
    var searchQuery by remember { mutableStateOf("") }
    var activeBookForReading by remember { mutableStateOf<IslamicBook?>(null) }
    var activeSahihBookType by remember { mutableStateOf<SahihBookType?>(null) }
    var openFullSahihBook by remember { mutableStateOf(false) } // 🔑 فتح الكتاب الكامل (7277 حديثًا للبخاري)
    var showProgressionDialog by remember { mutableStateOf(false) }

    // Handling Back Button for sub-screens (The identified "Back Point" weak point)
    BackHandler(enabled = activeSahihBookType != null || activeBookForReading != null) {
        if (activeSahihBookType != null) {
            activeSahihBookType = null
        } else if (activeBookForReading != null) {
            activeBookForReading = null
        }
    }

    // If a Sahih Book (Bukhari or Muslim) is selected, show the dedicated beautiful SahihReaderView
    if (activeSahihBookType != null) {
        // 🔑 إذا كان المستخدم طلب الكتاب الكامل، نفتح FullSahihReaderScreen
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

    Scaffold(
        modifier = Modifier.testTag(TestTags.ISLAMIC_LIBRARY_SCREEN),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocalLibrary, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("المكتبة الإسلامية الجامعة", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                                Text("أمهات كتب الأئمة والسلف • قراءة ممتعة واستماع صوتي", fontFamily = CairoFont, fontSize = 10.5.sp, color = GoldSecondary.copy(alpha = 0.85f))
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "الرجوع من المكتبة الإسلامية الجامعة", tint = GoldPrimary)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showProgressionDialog = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.School, contentDescription = "سلسلة التدرج الفقهي", tint = GoldPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = qabasCardSurface().copy(alpha = 0.95f))
                )
                HorizontalDivider(
                    color = GoldPrimary.copy(alpha = 0.35f),
                    thickness = 1.dp
                )
            }
        },
        containerColor = DeepSlate
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
            // 🌟 Search & Filter Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TestTags.SEARCH_FIELD),
                        placeholder = { Text("ابحث في كتب السلف، المجلدات، أو الدرر...", color = Color.Gray, fontFamily = CairoFont, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GoldPrimary) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "مسح", tint = Color.Gray)
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedBorderColor = GoldPrimary,
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedContainerColor = Color(0xFF0F172A),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }

            // 🌟 Top-Level Tab Switcher: الصحيحان (البخاري ومسلم) | أمهات كتب السلف
            item {
                Surface(
                    color = Color(0xFF151B2B),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clickable { activeSahihBookType = SahihBookType.BUKHARI },
                            shape = RoundedCornerShape(10.dp),
                            color = GoldPrimary.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(Icons.Default.AutoStories, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("الصحيحان (البخاري ومسلم) 🌟", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clickable {
                                    selectedCategory = "الكل"
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0B0F19),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(Icons.Default.LocalLibrary, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("أمهات كتب الأئمة 📚", color = TextSecondary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Daily Wisdom Capsule Spotlight (كبسولة الحكمة التفاعلية اليومية)
            item {
                Surface(
                    color = Color(0xFF151B2B),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF1E293B),
                                        Color(0xFF0F172A)
                                    )
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(GoldPrimary.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("كبسولة الحكمة اليومية ⏳ (في 20 ثانية)", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("من درر الإمام ابن قيم الجوزية", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 10.sp)
                                    }
                                }

                                Button(
                                    onClick = {
                                        onNavigateToVideoCreation("إضاعة الوقت أشد من الموت؛ لأن إضاعة الوقت تقطعك عن الله والدار الآخرة، والموت يقطعك عن الدنيا وأهلها. — ابن القيم")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Icon(Icons.Default.MovieCreation, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("صناعة فيديو", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                "«إضاعة الوقت أشد من الموت؛ لأن إضاعة الوقت تقطعك عن الله والدار الآخرة، والموت يقطعك عن الدنيا وأهلها».",
                                color = TextPrimary,
                                fontFamily = AmiriFont,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                lineHeight = 24.sp
                            )
                        }
                    }
                }
            }

            // Featured Sahihayn Spotlight Banner (درة دواوين الإسلام: صحيح البخاري وصحيح مسلم)
            item {
                Surface(
                    color = Color(0xFF151B2B),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.2.dp, GoldPrimary.copy(alpha = 0.75f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF28180A),
                                        Color(0xFF131D31),
                                        Color(0xFF0F172A)
                                    )
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(GoldPrimary, GoldSecondary)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(24.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("الصحيحان: البخاري ومسلم 🌟", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                        Text("أصح كتابين في الإسلام بعد كتاب الله عز وجل", color = Color(0xFFCBD5E1), fontFamily = NotoSansFont, fontSize = 10.5.sp)
                                    }
                                }

                                Surface(
                                    color = Color(0xFF10B981).copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("محقق ومضبوط بالشكل", color = Color(0xFF34D399), fontFamily = CairoFont, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                "عُيون السنة النبوية المطهرة بأصح الأسانيد المتصلة، مع استماع صوتي واختبارات تدبر وتحويل مباشر لريلز دعوي ملهم.",
                                color = TextPrimary.copy(alpha = 0.9f),
                                fontFamily = CairoFont,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { activeSahihBookType = SahihBookType.BUKHARI },
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Default.AutoStories, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("صحيح البخاري 📖", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                Button(
                                    onClick = { activeSahihBookType = SahihBookType.MUSLIM },
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.8f)),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Default.HistoryEdu, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("صحيح مسلم 📜", color = Color(0xFF10B981), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Ibn Qudamah Special Progression Banner
            item {
                Surface(
                    color = Color(0xFF151B2B),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showProgressionDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF38BDF8).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.School, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    "سلسلة ابن قدامة الفقهية الأربع الكبرى",
                                    color = Color(0xFF38BDF8),
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    "من حفظها وقرأها وتدرج فيها صار فقيهاً وعالماً",
                                    color = TextSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "فتح", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Search Box
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("ابحث في عناوين الكتب، المسائل، أو أسماء الأئمة...", color = TextSecondary, fontSize = 12.sp, fontFamily = NotoSansFont) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GoldPrimary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "مسح", tint = TextSecondary)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF151B2B),
                        unfocusedContainerColor = Color(0xFF151B2B),
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color(0xFF2A344A),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // Author Filter Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("أعلام وأئمة المكتبة:", color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            val isAll = selectedAuthorId == null
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isAll) GoldPrimary else Color(0xFF151B2B))
                                    .border(1.dp, if (isAll) GoldPrimary else Color(0xFF2A344A), RoundedCornerShape(20.dp))
                                    .clickable { selectedAuthorId = null }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("كافة الأئمة (${IslamicLibraryRepository.books.size})", color = if (isAll) DeepSlate else TextPrimary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = if (isAll) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                        items(IslamicLibraryRepository.authors) { author ->
                            val isSelected = selectedAuthorId == author.id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isSelected) GoldPrimary else Color(0xFF151B2B))
                                    .border(1.dp, if (isSelected) GoldPrimary else Color(0xFF2A344A), RoundedCornerShape(20.dp))
                                    .clickable { selectedAuthorId = author.id }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(author.name, color = if (isSelected) DeepSlate else TextPrimary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("(${author.bookCount})", color = if (isSelected) DeepSlate.copy(alpha = 0.8f) else GoldPrimary, fontFamily = NotoSansFont, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Books Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("المصنفات المحققة (${filteredBooks.size} كتب)", color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ميزة الاستماع الصوتي 🎧", color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 11.sp)
                    }
                }
            }

            // Books Cards
            items(filteredBooks, key = { it.id }) { book ->
                IslamicBookCard(
                    book = book,
                    onOpenReader = {
                        if (book.id == "sahih_al_bukhari") {
                            activeSahihBookType = SahihBookType.BUKHARI
                        } else if (book.id == "sahih_muslim") {
                            activeSahihBookType = SahihBookType.MUSLIM
                        } else {
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

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

    // Interactive Full Screen Book Reader Modal
    activeBookForReading?.let { book ->
        InteractiveBookReaderDialog(
            book = book,
            onDismiss = { activeBookForReading = null },
            onConvertToReel = { quote ->
                activeBookForReading = null
                onNavigateToVideoCreation(quote)
            }
        )
    }

    // 4-Stages Progression Dialog
    if (showProgressionDialog) {
        AlertDialog(
            onDismissRequest = { showProgressionDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.School, contentDescription = null, tint = GoldPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("منهج التدرج الفقهي عند ابن قدامة", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
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
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    ProgressionExplainerItem(
                        stage = "1. «عمدة الفقه» (للمبتدئين)",
                        desc = "يقتصر على قول واحد معتمد في المذهب، بلا تفريع ولا ذكر أدلة، لتثبيت الفروع في ذهن المبتدئ.",
                        color = GoldPrimary
                    )
                    ProgressionExplainerItem(
                        stage = "2. «المقنع» (للمتوسطين)",
                        desc = "يذكر فيه الروايات والأوجه في المذهب لتدريب الطالب على الخلاف المذهبي وملكة الترجيح.",
                        color = Color(0xFF38BDF8)
                    )
                    ProgressionExplainerItem(
                        stage = "3. «الكافي» (للمتقدمين)",
                        desc = "يقرن المسائل الفقهية بأدلتها التفصيلية من الكتاب والسنة مع بسط العلل الشرعية.",
                        color = Color(0xFF10B981)
                    )
                    ProgressionExplainerItem(
                        stage = "4. «المغني» (للمجتهدين والمتخصصين)",
                        desc = "موسوعة الفقه المقارن الكبرى، يورد أدلة المذاهب الأربعة وأقوال الصحابة والتابعين والترجيح المنصف.",
                        color = Color(0xFFA855F7)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showProgressionDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("فهمت المنهج", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF151B2B),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveBookReaderDialog(
    book: IslamicBook,
    onDismiss: () -> Unit,
    onConvertToReel: (quoteText: String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var selectedChapterIndex by remember { mutableIntStateOf(0) }
    var selectedSectionIndex by remember { mutableIntStateOf(0) }
    var fontSizeSp by remember { mutableFloatStateOf(14.5f) }

    // TTS Narrator State
    var isSpeaking by remember { mutableStateOf(false) }
    var ttsRate by remember { mutableFloatStateOf(1.0f) }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }

    val currentChapter = book.chapters.getOrNull(selectedChapterIndex) ?: book.chapters.first()
    val currentSection = currentChapter.sections.getOrNull(selectedSectionIndex) ?: currentChapter.sections.first()

    // Quiz State
    var selectedQuizOption by remember { mutableStateOf<Int?>(null) }
    var isQuizAnswered by remember { mutableStateOf(false) }

    // Reset quiz on section change
    LaunchedEffect(currentSection.id) {
        selectedQuizOption = null
        isQuizAnswered = false
        ttsEngine?.stop()
        isSpeaking = false
    }

    // Initialize TTS
    DisposableEffect(Unit) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsEngine?.language = Locale("ar")
            }
        }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        content = {
            Surface(
                color = DeepSlate,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Bar
                    Surface(
                        color = Color(0xFF0F172A),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                IconButton(onClick = onDismiss) {
                                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = GoldPrimary)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        book.title,
                                        color = GoldPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        "${book.authorName} • ⏱️ ${currentSection.readTimeMinutes} دقيقة للقراءة",
                                        color = TextSecondary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            // Controls: Font Size & TTS
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = {
                                        val fullReadingText = "${currentSection.sectionTitle}. ${currentSection.wisdomCapsule}. ${currentSection.content}. ${currentSection.keyRuleOrBenefit}"
                                        toggleSpeech(fullReadingText)
                                    },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (isSpeaking) GoldPrimary else Color(0xFF1E293B))
                                ) {
                                    Icon(
                                        if (isSpeaking) Icons.Default.Pause else Icons.Default.VolumeUp,
                                        contentDescription = "استماع",
                                        tint = if (isSpeaking) DeepSlate else GoldPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { if (fontSizeSp > 12f) fontSizeSp -= 1.5f },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Text("A-", color = TextSecondary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                IconButton(
                                    onClick = { if (fontSizeSp < 22f) fontSizeSp += 1.5f },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Text("A+", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Chapter Tabs
                    if (book.chapters.size > 1) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF151B2B))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(book.chapters.indices.toList()) { index ->
                                val ch = book.chapters[index]
                                val isSelected = selectedChapterIndex == index
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) GoldPrimary else Color(0xFF1E293B))
                                        .clickable {
                                            selectedChapterIndex = index
                                            selectedSectionIndex = 0
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        ch.chapterTitle,
                                        color = if (isSelected) DeepSlate else TextPrimary,
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    // Section Selector Tabs
                    if (currentChapter.sections.size > 1) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0B0F19))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(currentChapter.sections.indices.toList()) { sIdx ->
                                val sec = currentChapter.sections[sIdx]
                                val isSelected = selectedSectionIndex == sIdx
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) Color(0xFF334155) else Color.Transparent)
                                        .clickable { selectedSectionIndex = sIdx }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        sec.sectionTitle,
                                        color = if (isSelected) GoldPrimary else TextSecondary,
                                        fontFamily = CairoFont,
                                        fontSize = 10.sp
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
                        // Title
                        item {
                            Text(
                                currentSection.sectionTitle,
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = (fontSizeSp + 3).sp
                            )
                        }

                        // Wisdom Capsule (الكبسولة التشويقية)
                        if (currentSection.wisdomCapsule.isNotBlank()) {
                            item {
                                Surface(
                                    color = Color(0xFF0F172A),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(Icons.Default.Bolt, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("كبسولة الفائدة (في 20 ثانية):", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(currentSection.wisdomCapsule, color = TextPrimary, fontFamily = NotoSansFont, fontSize = 12.sp, lineHeight = 17.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // Original Full Text Content
                        item {
                            Surface(
                                color = Color(0xFF151B2B),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF2A344A)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = currentSection.content,
                                    color = TextPrimary,
                                    fontFamily = AmiriFont,
                                    fontSize = fontSizeSp.sp,
                                    lineHeight = (fontSizeSp * 1.75).sp,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }

                        // Rule / Benefit Highlight
                        if (currentSection.keyRuleOrBenefit.isNotBlank()) {
                            item {
                                Surface(
                                    color = Color(0xFF0F172A),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("القاعدة والضابط الشرعي المستنبط:", color = Color(0xFF10B981), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            currentSection.keyRuleOrBenefit,
                                            color = TextPrimary,
                                            fontFamily = NotoSansFont,
                                            fontSize = (fontSizeSp - 1).sp,
                                            lineHeight = (fontSizeSp * 1.6).sp
                                        )
                                    }
                                }
                            }
                        }

                        // Interactive Mini Quiz (❓ اختبر استيعابك وتثبيت الفائدة)
                        currentSection.quiz?.let { quiz ->
                            item {
                                Surface(
                                    color = Color(0xFF1A2234),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.HelpOutline, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("اختبر استيعابك للفائدة 🧠", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }

                                        Text(quiz.question, color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Medium, fontSize = 12.sp)

                                        quiz.options.forEachIndexed { idx, opt ->
                                            val isSelected = selectedQuizOption == idx
                                            val isCorrect = idx == quiz.correctIndex
                                            val optionBg = when {
                                                !isQuizAnswered -> if (isSelected) Color(0xFF334155) else Color(0xFF0F172A)
                                                isCorrect -> Color(0xFF10B981).copy(alpha = 0.25f)
                                                isSelected && !isCorrect -> Color(0xFFEF4444).copy(alpha = 0.25f)
                                                else -> Color(0xFF0F172A)
                                            }

                                            Surface(
                                                color = optionBg,
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isQuizAnswered && isCorrect) Color(0xFF10B981)
                                                    else if (isQuizAnswered && isSelected) Color(0xFFEF4444)
                                                    else Color(0xFF2A344A)
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable(enabled = !isQuizAnswered) {
                                                        selectedQuizOption = idx
                                                        isQuizAnswered = true
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        opt,
                                                        color = if (isQuizAnswered && isCorrect) Color(0xFF10B981) else TextPrimary,
                                                        fontFamily = NotoSansFont,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                        }

                                        if (isQuizAnswered) {
                                            Text(
                                                "💡 التوضيح: ${quiz.explanation}",
                                                color = Color(0xFF38BDF8),
                                                fontFamily = NotoSansFont,
                                                fontSize = 11.sp
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
                                    color = TextSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    // Bottom Bar Actions
                    Surface(
                        color = Color(0xFF0F172A),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(
                                    onClick = {
                                        val fullText = "${currentSection.sectionTitle}\n\n${currentSection.content}\n\n${currentSection.keyRuleOrBenefit}\n\n[المصدر: ${book.title} - ${book.authorName}]"
                                        clipboardManager.setText(AnnotatedString(fullText))
                                        Toast.makeText(context, "تم نسخ النص والمصدر بالكامل 📋", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E293B))
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ النص", tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                }

                                IconButton(
                                    onClick = {
                                        val shareText = "${currentSection.sectionTitle}\n\n${currentSection.content}\n\n${currentSection.keyRuleOrBenefit}\n\nمن كتاب: ${book.title} (المؤلف: ${book.authorName})\nتطبيق قبس"
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "مشاركة الفائدة الفقهية"))
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E293B))
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                }
                            }

                            Button(
                                onClick = {
                                    val reelText = if (currentSection.wisdomCapsule.isNotBlank()) currentSection.wisdomCapsule
                                    else if (currentSection.keyRuleOrBenefit.isNotBlank()) currentSection.keyRuleOrBenefit
                                    else currentSection.content.take(160)
                                    onConvertToReel(reelText)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.MovieCreation, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تحويل الفائدة إلى مشروع فيديو 🎬", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun ProgressionExplainerItem(
    stage: String,
    desc: String,
    color: Color
) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(stage, color = color, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(desc, color = TextPrimary, fontFamily = NotoSansFont, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
fun IslamicBookCard(
    book: IslamicBook,
    onOpenReader: () -> Unit,
    onReelDirect: () -> Unit
) {
    Surface(
        color = Color(0xFF151B2B),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenReader() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        book.title,
                        color = GoldPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        "تأليف: ${book.authorName}",
                        color = TextSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 11.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(GoldPrimary.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        book.levelBadge,
                        color = GoldPrimary,
                        fontFamily = CairoFont,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (book.hookTitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "✨ ${book.hookTitle}",
                    color = Color(0xFF38BDF8),
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                book.description,
                color = TextPrimary,
                fontFamily = NotoSansFont,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            if (book.scholarQuote.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FormatQuote, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            book.scholarQuote,
                            color = TextSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "~ ${book.totalEstimatedMinutes} دقيقة للقراءة",
                        color = TextSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 11.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onReelDirect,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.MovieCreation, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("صناعة فيديو", fontFamily = CairoFont, fontSize = 11.sp)
                    }

                    Button(
                        onClick = onOpenReader,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.AutoStories, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("قراءة المتن", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

