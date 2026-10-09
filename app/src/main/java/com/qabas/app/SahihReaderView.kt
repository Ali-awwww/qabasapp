package com.qabas.app

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SahihReaderView(
    initialBookType: SahihBookType = SahihBookType.BUKHARI,
    onBack: () -> Unit,
    onNavigateToVideoCreation: (quoteText: String) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var activeBook by remember { mutableStateOf(initialBookType) }
    var selectedChapterId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showOnlyBookmarks by remember { mutableStateOf(false) }
    var showOnlyQuranEvidence by remember { mutableStateOf(false) }
    var showChallengeDialog by remember { mutableStateOf(false) }
    var showQuranicHarmonyDialog by remember { mutableStateOf(false) }
    val userSavedFontSize by UserPreferencesManager.quranFontSize.collectAsState()
    var fontSizeSp by remember(userSavedFontSize) { mutableFloatStateOf(userSavedFontSize) }

    // TTS Narrator
    var speakingHadithId by remember { mutableStateOf<String?>(null) }
    var ttsRate by remember { mutableFloatStateOf(1.0f) }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }

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

    fun toggleSpeech(hadith: SahihHadithItem) {
        val tts = ttsEngine ?: return
        if (speakingHadithId == hadith.id) {
            tts.stop()
            speakingHadithId = null
        } else {
            tts.stop()
            tts.setSpeechRate(ttsRate)
            val textToSpeak = "${hadith.narrator}. قال رسول الله صلى الله عليه وسلم: ${hadith.matn}"
            tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, hadith.id)
            speakingHadithId = hadith.id
        }
    }

    // Chapters list for active book
    val chapters = remember(activeBook) {
        SahihHadithsRepository.getChaptersForBook(activeBook)
    }

    // Filtered hadiths
    val hadiths = remember(activeBook, selectedChapterId, searchQuery, showOnlyBookmarks, showOnlyQuranEvidence) {
        val list = if (showOnlyBookmarks) {
            SahihHadithsRepository.getBookmarkedHadiths(context).filter { it.bookType == activeBook }
        } else {
            SahihHadithsRepository.searchHadiths(activeBook, selectedChapterId, searchQuery)
        }
        if (showOnlyQuranEvidence) {
            list.filter { it.quranEvidence != null }
        } else {
            list
        }
    }

    // Daily Hadith for spotlight
    val dailyHadith = remember {
        SahihHadithsRepository.getRandomDailyHadith()
    }

    val themeColor = activeBook.accentColor

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (activeBook == SahihBookType.BUKHARI) Icons.Default.AutoStories else Icons.Default.HistoryEdu,
                                contentDescription = null,
                                tint = themeColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = activeBook.titleAr,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = themeColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (activeBook == SahihBookType.BUKHARI) "الجامع الصحيح" else "المسند الصحيح",
                                    color = themeColor,
                                    fontSize = 10.sp,
                                    fontFamily = NotoSansFont,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = activeBook.badge,
                            fontFamily = NotoSansFont,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = themeColor)
                    }
                },
                actions = {
                    // Quranic Evidence Harmony Button (تكامل الوحيين)
                    IconButton(onClick = { showQuranicHarmonyDialog = true }) {
                        Icon(Icons.Default.MenuBook, contentDescription = "تكامل الوحيين (القرآن والسنة)", tint = Color(0xFF38BDF8))
                    }
                    // Challenge Quiz Button
                    IconButton(onClick = { showChallengeDialog = true }) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = "تحدي وتدبر الصحيحين", tint = GoldPrimary)
                    }
                    // Font Size Toggle Button
                    IconButton(onClick = {
                        fontSizeSp = if (fontSizeSp >= 20f) 14.5f else fontSizeSp + 2f
                    }) {
                        Icon(Icons.Default.FormatSize, contentDescription = "تكبير الخط", tint = themeColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0B0F19))
            )
        },
        containerColor = DeepSlate
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Book Switcher Header (صحيح البخاري vs صحيح مسلم)
            item {
                Surface(
                    color = Color(0xFF151B2B),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Bukhari Tab
                            val isBukhari = activeBook == SahihBookType.BUKHARI
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clickable {
                                        activeBook = SahihBookType.BUKHARI
                                        selectedChapterId = null
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isBukhari) Color(0xFFF59E0B).copy(alpha = 0.2f) else Color(0xFF0B0F19),
                                border = BorderStroke(
                                    if (isBukhari) 1.5.dp else 1.dp,
                                    if (isBukhari) Color(0xFFF59E0B) else Color(0xFF334155)
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.AutoStories,
                                        contentDescription = null,
                                        tint = if (isBukhari) Color(0xFFF59E0B) else TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "صحيح البخاري 📖",
                                        color = if (isBukhari) Color(0xFFF59E0B) else TextSecondary,
                                        fontFamily = CairoFont,
                                        fontWeight = if (isBukhari) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            // Muslim Tab
                            val isMuslim = activeBook == SahihBookType.MUSLIM
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clickable {
                                        activeBook = SahihBookType.MUSLIM
                                        selectedChapterId = null
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isMuslim) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF0B0F19),
                                border = BorderStroke(
                                    if (isMuslim) 1.5.dp else 1.dp,
                                    if (isMuslim) Color(0xFF10B981) else Color(0xFF334155)
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.HistoryEdu,
                                        contentDescription = null,
                                        tint = if (isMuslim) Color(0xFF10B981) else TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "صحيح مسلم 📜",
                                        color = if (isMuslim) Color(0xFF10B981) else TextSecondary,
                                        fontFamily = CairoFont,
                                        fontWeight = if (isMuslim) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Book Author Quote
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0B0F19), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.FormatQuote, contentDescription = null, tint = themeColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = activeBook.quote,
                                color = TextPrimary.copy(alpha = 0.85f),
                                fontFamily = NotoSansFont,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // 2. Spotlight: حديث اليوم المختار (Daily Wisdom Spotlight)
            if (searchQuery.isBlank() && selectedChapterId == null && !showOnlyBookmarks) {
                item {
                    Surface(
                        color = Color(0xFF151B2B),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
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
                                .padding(14.dp)
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
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(GoldPrimary.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Star, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                "حديث اليوم من الصحيحين 🌟",
                                                fontFamily = CairoFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = GoldPrimary
                                            )
                                            Text(
                                                "${dailyHadith.bookType.titleAr} • ${dailyHadith.chapterTitle}",
                                                fontFamily = NotoSansFont,
                                                fontSize = 10.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            onNavigateToVideoCreation("${dailyHadith.matn} — ${dailyHadith.reference}")
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(Icons.Default.MovieCreation, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("صناعة فيديو", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = dailyHadith.matn,
                                    fontFamily = CairoFont,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    color = Color.White,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = dailyHadith.wisdomCapsule,
                                        fontFamily = NotoSansFont,
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Search & Filter Bar
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "ابحث بالمتن، الراوي، رقم الحديث، أو الموضوع...",
                                fontFamily = NotoSansFont,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = themeColor)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "مسح", tint = TextSecondary)
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = themeColor,
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedContainerColor = Color(0xFF151B2B),
                            unfocusedContainerColor = Color(0xFF151B2B),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    // Filter Chips Row (Bookmarks toggle, Quranic Evidence & Chapters)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Quranic Evidence Filter Toggle (تكامل الوحيين)
                        item {
                            FilterChip(
                                selected = showOnlyQuranEvidence,
                                onClick = {
                                    showOnlyQuranEvidence = !showOnlyQuranEvidence
                                    if (showOnlyQuranEvidence) showOnlyBookmarks = false
                                },
                                label = {
                                    Text(
                                        "مدعوم بالقرآن 📖✨",
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp,
                                        fontWeight = if (showOnlyQuranEvidence) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.MenuBook,
                                        contentDescription = null,
                                        tint = if (showOnlyQuranEvidence) Color(0xFF38BDF8) else TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF38BDF8).copy(alpha = 0.2f),
                                    selectedLabelColor = Color(0xFF38BDF8),
                                    containerColor = Color(0xFF151B2B),
                                    labelColor = TextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = showOnlyQuranEvidence,
                                    borderColor = if (showOnlyQuranEvidence) Color(0xFF38BDF8) else Color(0xFF1E293B)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        // Bookmark Filter Toggle
                        item {
                            FilterChip(
                                selected = showOnlyBookmarks,
                                onClick = {
                                    showOnlyBookmarks = !showOnlyBookmarks
                                    if (showOnlyBookmarks) showOnlyQuranEvidence = false
                                },
                                label = {
                                    Text(
                                        "المحفوظات ⭐",
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp,
                                        fontWeight = if (showOnlyBookmarks) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (showOnlyBookmarks) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = null,
                                        tint = if (showOnlyBookmarks) GoldPrimary else TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GoldPrimary.copy(alpha = 0.2f),
                                    selectedLabelColor = GoldPrimary,
                                    containerColor = Color(0xFF151B2B),
                                    labelColor = TextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = showOnlyBookmarks,
                                    borderColor = if (showOnlyBookmarks) GoldPrimary else Color(0xFF1E293B)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        // "All Chapters" chip
                        item {
                            val isAllSelected = selectedChapterId == null && !showOnlyBookmarks
                            FilterChip(
                                selected = isAllSelected,
                                onClick = {
                                    selectedChapterId = null
                                    showOnlyBookmarks = false
                                },
                                label = {
                                    Text(
                                        "كافة الأبواب (${SahihHadithsRepository.getHadithsForBook(activeBook).size})",
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp,
                                        fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = themeColor.copy(alpha = 0.2f),
                                    selectedLabelColor = themeColor,
                                    containerColor = Color(0xFF151B2B),
                                    labelColor = TextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isAllSelected,
                                    borderColor = if (isAllSelected) themeColor else Color(0xFF1E293B)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        // Individual chapters
                        items(chapters) { (chId, chTitle) ->
                            val isSelected = selectedChapterId == chId && !showOnlyBookmarks
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedChapterId = chId
                                    showOnlyBookmarks = false
                                },
                                label = {
                                    Text(
                                        chTitle,
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = themeColor.copy(alpha = 0.2f),
                                    selectedLabelColor = themeColor,
                                    containerColor = Color(0xFF151B2B),
                                    labelColor = TextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (isSelected) themeColor else Color(0xFF1E293B)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }

            // 4. Results Header & Count
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (showOnlyBookmarks) "الأحاديث المحفوظة" else (selectedChapterId?.let { id -> chapters.find { it.first == id }?.second } ?: "الأحاديث النبوية الصحيحة"),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "${hadiths.size} حديث موثق",
                        fontFamily = NotoSansFont,
                        fontSize = 11.sp,
                        color = themeColor
                    )
                }
            }

            // 5. Empty State
            if (hadiths.isEmpty()) {
                item {
                    Surface(
                        color = Color(0xFF151B2B),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.SearchOff, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(42.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "لم نجد أحاديث مطابقة للبحث",
                                color = TextPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "جرّب البحث بكلمة أخرى أو تصفح الأبواب المختلفة",
                                color = TextSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // 6. Hadiths List
            items(hadiths, key = { it.id }) { hadith ->
                SahihHadithCard(
                    hadith = hadith,
                    fontSizeSp = fontSizeSp,
                    isSpeaking = speakingHadithId == hadith.id,
                    onToggleSpeech = { toggleSpeech(hadith) },
                    onCopy = {
                        val quranPart = hadith.quranEvidence?.let { qe ->
                            "\n\n📖 الدليل القرآني [${qe.surahName} - آية ${qe.ayahNumber}]:\n«${qe.verseText}»\n• وجه التكامل: ${qe.relationType}"
                        } ?: ""
                        val text = "${hadith.narrator}:\n${hadith.matn}\n[${hadith.reference}]$quranPart"
                        clipboardManager.setText(AnnotatedString(text))
                        Toast.makeText(context, "تم نسخ الحديث والدليل القرآني بنجاح ✅", Toast.LENGTH_SHORT).show()
                    },
                    onShare = {
                        val quranPart = hadith.quranEvidence?.let { qe ->
                            "\n\n📖 الدليل القرآني [${qe.surahName} - آية ${qe.ayahNumber}]:\n«${qe.verseText}»\n• وجه التكامل: ${qe.relationType}\n• البيان: ${qe.commentary}"
                        } ?: ""
                        val text = "حديث شريف من ${hadith.bookType.titleAr}:\n\n${hadith.narrator}:\n«${hadith.matn}»\n\n📌 التخريج: ${hadith.reference}\n💡 الفائدة: ${hadith.wisdomCapsule}$quranPart\n\n✨ تطبيق قبس — مشكاة الهدى"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(intent, "مشاركة الحديث الشريف والدليل القرآني"))
                    },
                    onConvertToVideo = {
                        val quranPart = hadith.quranEvidence?.let { qe ->
                            " — قال تعالى: «${qe.verseText}» [${qe.surahName}: ${qe.ayahNumber}]"
                        } ?: ""
                        val quote = "${hadith.matn}$quranPart — ${hadith.reference}"
                        onNavigateToVideoCreation(quote)
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // Interactive Sahih Challenge Modal (مسابقة وتحدي الصحيحين)
    if (showChallengeDialog) {
        SahihChallengeDialog(
            activeBook = activeBook,
            onDismiss = { showChallengeDialog = false }
        )
    }

    // Interactive Quranic Harmony Modal (تكامل الوحيين: القرآن والسنة)
    if (showQuranicHarmonyDialog) {
        QuranicHarmonyDialog(
            activeBook = activeBook,
            onDismiss = { showQuranicHarmonyDialog = false },
            onNavigateToVideoCreation = onNavigateToVideoCreation
        )
    }
}

@Composable
fun SahihHadithCard(
    hadith: SahihHadithItem,
    fontSizeSp: Float,
    isSpeaking: Boolean,
    onToggleSpeech: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onConvertToVideo: () -> Unit
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: الشرح والبيان, 1: الفوائد, 2: تدبر واختبار
    var isBookmarked by remember { mutableStateOf(SahihHadithsRepository.isBookmarked(context, hadith.id)) }

    // Quiz State
    var selectedOption by remember { mutableStateOf<Int?>(null) }
    var isAnswered by remember { mutableStateOf(false) }

    val preferredFontKey by UserPreferencesManager.readingFontFamily.collectAsState()
    val resolvedReadingFont = remember(preferredFontKey) {
        UserPreferencesManager.getResolvedFontFamily(preferredFontKey)
    }

    val themeColor = hadith.bookType.accentColor

    Surface(
        color = Color(0xFF151B2B),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isSpeaking) themeColor else Color(0xFF1E293B)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Hadith Number, Narrator & Bookmark
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        color = themeColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "حديث #${hadith.hadithNumber}",
                            color = themeColor,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = hadith.narrator,
                        fontFamily = NotoSansFont,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Audio TTS Button
                    IconButton(
                        onClick = onToggleSpeech,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                            contentDescription = "استماع صوتي",
                            tint = if (isSpeaking) themeColor else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Bookmark Button
                    IconButton(
                        onClick = {
                            isBookmarked = SahihHadithsRepository.toggleBookmark(context, hadith.id)
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "حفظ في المفضلة",
                            tint = if (isBookmarked) GoldPrimary else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Hadith Title / Chapter Title
            Text(
                text = hadith.title,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Matn (نص الحديث الشريف المشكول بخط واضح وفاخر)
            Surface(
                color = Color(0xFF0B0F19),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = hadith.matn,
                    fontFamily = resolvedReadingFont,
                    fontWeight = FontWeight.Normal,
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.6f).sp,
                    color = Color(0xFFF8FAFC),
                    modifier = Modifier.padding(14.dp),
                    textAlign = TextAlign.Justify
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Wisdom Capsule (كبسولة الحكمة)
            Surface(
                color = themeColor.copy(alpha = 0.08f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, themeColor.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = themeColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = hadith.wisdomCapsule,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp,
                        color = Color.White.copy(alpha = 0.95f),
                        lineHeight = 16.sp
                    )
                }
            }

            // Quranic Evidence Spotlight Banner (الدليل القرآني وتكامل الوحيين)
            hadith.quranEvidence?.let { qe ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0xFF0F2332),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "الدليل من القرآن الكريم",
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                            Surface(
                                color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${qe.surahName} [آية ${qe.ayahNumber}]",
                                    fontFamily = NotoSansFont,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF7DD3FC),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "« ${qe.verseText} »",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = (fontSizeSp * 0.95f).sp,
                            lineHeight = (fontSizeSp * 1.5f).sp,
                            color = Color(0xFFE0F2FE),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "🔗 وجه التكامل: ${qe.relationType}",
                            fontFamily = NotoSansFont,
                            fontSize = 10.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Actions Bar (نسخ، مشاركة، ريلز، وتفاصيل)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Copy Button
                    FilledTonalButton(
                        onClick = onCopy,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("نسخ", color = TextSecondary, fontFamily = CairoFont, fontSize = 11.sp)
                    }

                    // Share Button
                    FilledTonalButton(
                        onClick = onShare,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مشاركة", color = TextSecondary, fontFamily = CairoFont, fontSize = 11.sp)
                    }

                    // Video Creator Button
                    Button(
                        onClick = onConvertToVideo,
                        colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.MovieCreation, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("صناعة فيديو", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                // Expand/Collapse Details Button
                TextButton(
                    onClick = { isExpanded = !isExpanded },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        if (isExpanded) "إخفاء البيان ▲" else "الشرح والتدبر ▼",
                        color = themeColor,
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Expandable Drawer for Explanation, Benefits & Quiz
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secondary Sub-tabs: الشرح والبيان | الفوائد والضوابط | الدليل القرآني | تدبر واختبار
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TabButton(
                            title = "الشرح 📖",
                            isSelected = selectedTab == 0,
                            accentColor = themeColor,
                            onClick = { selectedTab = 0 },
                            modifier = Modifier.weight(1f)
                        )
                        TabButton(
                            title = "الفوائد 💎",
                            isSelected = selectedTab == 1,
                            accentColor = themeColor,
                            onClick = { selectedTab = 1 },
                            modifier = Modifier.weight(1f)
                        )
                        if (hadith.quranEvidence != null) {
                            TabButton(
                                title = "القرآن 📜",
                                isSelected = selectedTab == 2,
                                accentColor = Color(0xFF38BDF8),
                                onClick = { selectedTab = 2 },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (hadith.quiz != null) {
                            TabButton(
                                title = "اختبار ❓",
                                isSelected = selectedTab == 3,
                                accentColor = themeColor,
                                onClick = { selectedTab = 3 },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    when (selectedTab) {
                        0 -> {
                            // Explanation
                            Text(
                                text = hadith.explanation,
                                fontFamily = NotoSansFont,
                                fontSize = 12.sp,
                                lineHeight = 19.sp,
                                color = TextPrimary.copy(alpha = 0.9f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "المصدر: ${hadith.reference}",
                                fontFamily = NotoSansFont,
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                        1 -> {
                            // Practical Benefits
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                hadith.practicalBenefits.forEach { benefit ->
                                    Row(verticalAlignment = Alignment.Top) {
                                        Text("✔", color = themeColor, fontSize = 12.sp, modifier = Modifier.padding(end = 6.dp))
                                        Text(
                                            text = benefit,
                                            fontFamily = NotoSansFont,
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp,
                                            color = TextPrimary.copy(alpha = 0.9f)
                                        )
                                    }
                                }
                            }
                        }
                        2 -> {
                            // Quranic Evidence Deep Dive
                            hadith.quranEvidence?.let { qe ->
                                Surface(
                                    color = Color(0xFF0B1B2B),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "📖 ${qe.surahName} (الآية ${qe.ayahNumber})",
                                                fontFamily = CairoFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color(0xFF38BDF8)
                                            )
                                            Surface(
                                                color = Color(0xFF38BDF8).copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "تكامل الوحيين",
                                                    color = Color(0xFF7DD3FC),
                                                    fontSize = 10.sp,
                                                    fontFamily = CairoFont,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = "« ${qe.verseText} »",
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            lineHeight = 22.sp,
                                            color = Color.White,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)

                                        Text(
                                            text = "🔹 وجه الدلالة والتكامل:\n${qe.relationType}",
                                            fontFamily = NotoSansFont,
                                            fontSize = 11.5.sp,
                                            lineHeight = 17.sp,
                                            color = TextPrimary.copy(alpha = 0.9f)
                                        )

                                        Text(
                                            text = "💡 البيان والتفسير:\n${qe.commentary}",
                                            fontFamily = NotoSansFont,
                                            fontSize = 11.5.sp,
                                            lineHeight = 17.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        }
                        3 -> {
                            // Interactive Quiz
                            hadith.quiz?.let { quiz ->
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = quiz.question,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = TextPrimary
                                    )

                                    quiz.options.forEachIndexed { index, option ->
                                        val isChosen = selectedOption == index
                                        val isCorrect = quiz.correctIndex == index
                                        val containerBg = when {
                                            !isAnswered -> if (isChosen) themeColor.copy(alpha = 0.2f) else Color(0xFF0B0F19)
                                            isCorrect -> Color(0xFF10B981).copy(alpha = 0.25f)
                                            isChosen && !isCorrect -> Color(0xFFEF4444).copy(alpha = 0.25f)
                                            else -> Color(0xFF0B0F19)
                                        }
                                        val borderBorder = when {
                                            !isAnswered -> if (isChosen) themeColor else Color(0xFF1E293B)
                                            isCorrect -> Color(0xFF10B981)
                                            isChosen && !isCorrect -> Color(0xFFEF4444)
                                            else -> Color(0xFF1E293B)
                                        }

                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = !isAnswered) {
                                                    selectedOption = index
                                                    isAnswered = true
                                                },
                                            shape = RoundedCornerShape(8.dp),
                                            color = containerBg,
                                            border = BorderStroke(1.dp, borderBorder)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${('أ'.code + index).toChar()}.",
                                                    fontFamily = CairoFont,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = themeColor,
                                                    modifier = Modifier.padding(end = 8.dp)
                                                )
                                                Text(
                                                    text = option,
                                                    fontFamily = NotoSansFont,
                                                    fontSize = 11.5.sp,
                                                    color = TextPrimary,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (isAnswered) {
                                                    Icon(
                                                        imageVector = if (isCorrect) Icons.Default.CheckCircle else if (isChosen) Icons.Default.Cancel else Icons.Default.Circle,
                                                        contentDescription = null,
                                                        tint = if (isCorrect) Color(0xFF10B981) else if (isChosen) Color(0xFFEF4444) else Color.Transparent,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (isAnswered) {
                                        Surface(
                                            color = Color(0xFF0B0F19),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "💡 التوجيه والبيان: ${quiz.explanation}",
                                                fontFamily = NotoSansFont,
                                                fontSize = 11.sp,
                                                color = Color(0xFF38BDF8),
                                                modifier = Modifier.padding(8.dp)
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
    }
}

@Composable
fun TabButton(
    title: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(30.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = if (isSelected) accentColor.copy(alpha = 0.2f) else Color(0xFF0B0F19),
        border = BorderStroke(1.dp, if (isSelected) accentColor else Color(0xFF1E293B))
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = title,
                fontFamily = CairoFont,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 10.5.sp,
                color = if (isSelected) accentColor else TextSecondary
            )
        }
    }
}

@Composable
fun SahihChallengeDialog(
    activeBook: SahihBookType,
    onDismiss: () -> Unit
) {
    val hadiths = remember(activeBook) {
        SahihHadithsRepository.getHadithsForBook(activeBook).filter { it.quiz != null }.shuffled().take(5)
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var selectedOption by remember { mutableStateOf<Int?>(null) }
    var isSubmitted by remember { mutableStateOf(false) }
    var isFinished by remember { mutableStateOf(false) }

    val currentHadith = hadiths.getOrNull(currentIndex)
    val quiz = currentHadith?.quiz

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = GoldPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "تحدي وتدبر ${activeBook.titleAr}",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
            }
        },
        text = {
            if (isFinished) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MilitaryTech, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(40.dp))
                    }
                    Text(
                        text = "ما شاء الله! مبارك إتمام التحدي",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = GoldPrimary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "حققت $score من أصل ${hadiths.size} إجابات صحيحة في تدبر وفهم الأحاديث الشريفة.",
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Surface(
                        color = Color(0xFF0B0F19),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "«نضّر الله امرأً سمع مقالتي فوعاها فأداها كما سمعها» — زادك الله علماً وهدى وبركة.",
                            color = Color(0xFF38BDF8),
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            } else if (quiz != null && currentHadith != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "السؤال ${currentIndex + 1} من ${hadiths.size}",
                            color = activeBook.accentColor,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Text(
                            "النقاط: $score",
                            color = GoldPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }

                    Surface(
                        color = Color(0xFF0B0F19),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = currentHadith.matn.take(120) + "...",
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    Text(
                        text = quiz.question,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary
                    )

                    quiz.options.forEachIndexed { index, option ->
                        val isChosen = selectedOption == index
                        val isCorrect = quiz.correctIndex == index
                        val bg = when {
                            !isSubmitted -> if (isChosen) activeBook.accentColor.copy(alpha = 0.2f) else Color(0xFF0B0F19)
                            isCorrect -> Color(0xFF10B981).copy(alpha = 0.25f)
                            isChosen && !isCorrect -> Color(0xFFEF4444).copy(alpha = 0.25f)
                            else -> Color(0xFF0B0F19)
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isSubmitted) {
                                    selectedOption = index
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = bg,
                            border = BorderStroke(1.dp, if (isChosen) activeBook.accentColor else Color(0xFF1E293B))
                        ) {
                            Text(
                                text = option,
                                fontFamily = NotoSansFont,
                                fontSize = 11.5.sp,
                                color = TextPrimary,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    if (isSubmitted) {
                        Text(
                            text = "💡 ${quiz.explanation}",
                            fontFamily = NotoSansFont,
                            fontSize = 11.sp,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isFinished) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("تم", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            } else if (!isSubmitted) {
                Button(
                    onClick = {
                        if (selectedOption != null) {
                            isSubmitted = true
                            if (selectedOption == quiz?.correctIndex) {
                                score++
                            }
                        }
                    },
                    enabled = selectedOption != null,
                    colors = ButtonDefaults.buttonColors(containerColor = activeBook.accentColor),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("تحقق من الإجابة", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = {
                        if (currentIndex + 1 < hadiths.size) {
                            currentIndex++
                            selectedOption = null
                            isSubmitted = false
                        } else {
                            isFinished = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = activeBook.accentColor),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (currentIndex + 1 < hadiths.size) "السؤال التالي" else "عرض النتيجة", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!isFinished) {
                TextButton(onClick = onDismiss) {
                    Text("إلغاء", color = TextSecondary, fontFamily = CairoFont)
                }
            }
        },
        containerColor = Color(0xFF151B2B),
        shape = RoundedCornerShape(16.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranicHarmonyDialog(
    activeBook: SahihBookType,
    onDismiss: () -> Unit,
    onNavigateToVideoCreation: (quoteText: String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var selectedBookFilter by remember { mutableStateOf<SahihBookType?>(null) }
    var harmonyQuery by remember { mutableStateOf("") }

    val allEvidenceHadiths = remember(selectedBookFilter, harmonyQuery) {
        val base = SahihHadithsRepository.getHadithsWithQuranEvidence(selectedBookFilter)
        if (harmonyQuery.isBlank()) {
            base
        } else {
            base.filter { h ->
                val qe = h.quranEvidence
                h.title.contains(harmonyQuery, ignoreCase = true) ||
                        h.matn.contains(harmonyQuery, ignoreCase = true) ||
                        (qe != null && (qe.verseText.contains(harmonyQuery, ignoreCase = true) ||
                                qe.surahName.contains(harmonyQuery, ignoreCase = true) ||
                                qe.relationType.contains(harmonyQuery, ignoreCase = true)))
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "تكامل الوحيين: القرآن والسنة 📖✨",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "أحاديث الصحيحين المقترنة بأدلتها من كتاب الله",
                            fontFamily = NotoSansFont,
                            fontSize = 10.5.sp,
                            color = Color(0xFF7DD3FC)
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Book Filter Tabs (الكل | البخاري | مسلم)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isAll = selectedBookFilter == null
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clickable { selectedBookFilter = null },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isAll) Color(0xFF38BDF8).copy(alpha = 0.2f) else Color(0xFF0B0F19),
                        border = BorderStroke(1.dp, if (isAll) Color(0xFF38BDF8) else Color(0xFF1E293B))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("كافة الأحاديث (${SahihHadithsRepository.getHadithsWithQuranEvidence().size})", color = if (isAll) Color(0xFF38BDF8) else TextSecondary, fontFamily = CairoFont, fontSize = 10.5.sp)
                        }
                    }

                    val isBukhari = selectedBookFilter == SahihBookType.BUKHARI
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clickable { selectedBookFilter = SahihBookType.BUKHARI },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isBukhari) Color(0xFFF59E0B).copy(alpha = 0.2f) else Color(0xFF0B0F19),
                        border = BorderStroke(1.dp, if (isBukhari) Color(0xFFF59E0B) else Color(0xFF1E293B))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("البخاري (${SahihHadithsRepository.getHadithsWithQuranEvidence(SahihBookType.BUKHARI).size})", color = if (isBukhari) Color(0xFFF59E0B) else TextSecondary, fontFamily = CairoFont, fontSize = 10.5.sp)
                        }
                    }

                    val isMuslim = selectedBookFilter == SahihBookType.MUSLIM
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clickable { selectedBookFilter = SahihBookType.MUSLIM },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isMuslim) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF0B0F19),
                        border = BorderStroke(1.dp, if (isMuslim) Color(0xFF10B981) else Color(0xFF1E293B))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("مسلم (${SahihHadithsRepository.getHadithsWithQuranEvidence(SahihBookType.MUSLIM).size})", color = if (isMuslim) Color(0xFF10B981) else TextSecondary, fontFamily = CairoFont, fontSize = 10.5.sp)
                        }
                    }
                }

                // Search field inside dialog
                OutlinedTextField(
                    value = harmonyQuery,
                    onValueChange = { harmonyQuery = it },
                    placeholder = { Text("ابحث بالسورة أو الآية أو موضوع الحديث...", fontSize = 11.sp, color = TextSecondary) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0B0F19),
                        unfocusedContainerColor = Color(0xFF0B0F19),
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF1E293B),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                // Pairs List
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(allEvidenceHadiths, key = { it.id }) { hadith ->
                        val qe = hadith.quranEvidence ?: return@items
                        val bookColor = hadith.bookType.accentColor

                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF1E293B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                // Header: Surah Tag + Book Tag
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = Color(0xFF38BDF8).copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "📖 ${qe.surahName} [آية ${qe.ayahNumber}]",
                                            color = Color(0xFF7DD3FC),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = CairoFont,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    Surface(
                                        color = bookColor.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "${hadith.bookType.titleAr} #${hadith.hadithNumber}",
                                            color = bookColor,
                                            fontSize = 10.sp,
                                            fontFamily = CairoFont,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                // Noble Ayah
                                Surface(
                                    color = Color(0xFF0B1B2B),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(0.5.dp, Color(0xFF38BDF8).copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "« ${qe.verseText} »",
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp,
                                        color = Color(0xFFE0F2FE),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }

                                // Corresponding Hadith snippet
                                Surface(
                                    color = Color(0xFF0B0F19),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = "${hadith.narrator}:",
                                            fontFamily = CairoFont,
                                            fontSize = 10.5.sp,
                                            color = TextSecondary
                                        )
                                        Text(
                                            text = "«${hadith.matn}»",
                                            fontFamily = CairoFont,
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp,
                                            color = TextPrimary
                                        )
                                    }
                                }

                                // Relationship & Commentary
                                Text(
                                    text = "🔗 وجه التكامل والدلالة: ${qe.relationType}",
                                    fontFamily = NotoSansFont,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF38BDF8)
                                )

                                Text(
                                    text = "💡 ${qe.commentary}",
                                    fontFamily = NotoSansFont,
                                    fontSize = 10.5.sp,
                                    lineHeight = 15.sp,
                                    color = Color(0xFF94A3B8)
                                )

                                // Action Buttons (Reels + Copy + Share)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            val text = "قال تعالى: «${qe.verseText}» [${qe.surahName}: ${qe.ayahNumber}]\n\nوقال النبي ﷺ في ${hadith.bookType.titleAr}:\n«${hadith.matn}» [${hadith.reference}]\n\n🔗 وجه التكامل: ${qe.relationType}\n\n✨ تطبيق قبس"
                                            clipboardManager.setText(AnnotatedString(text))
                                            Toast.makeText(context, "تم نسخ الآية والحديث بنجاح ✅", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = TextSecondary, modifier = Modifier.size(15.dp))
                                    }

                                    IconButton(
                                        onClick = {
                                            val text = "📖 تكامل الوحيين (القرآن والسنة):\n\nقال الله تعالى:\n«${qe.verseText}» [${qe.surahName}: ${qe.ayahNumber}]\n\nوقال رسول الله ﷺ:\n«${hadith.matn}» [${hadith.reference}]\n\n🔗 وجه الدلالة والتكامل:\n${qe.relationType}\n\n💡 البيان: ${qe.commentary}\n\n✨ استوديو قبس — مشكاة الهدى"
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, text)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "مشاركة تكامل الوحيين"))
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = TextSecondary, modifier = Modifier.size(15.dp))
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    Button(
                                        onClick = {
                                            val quote = "قال تعالى: «${qe.verseText}» [${qe.surahName}: ${qe.ayahNumber}] — وقال ﷺ: «${hadith.matn}»"
                                            onNavigateToVideoCreation(quote)
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(Icons.Default.MovieCreation, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("صناعة فيديو الوحيين", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("إغلاق", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFF151B2B),
        shape = RoundedCornerShape(16.dp)
    )
}
