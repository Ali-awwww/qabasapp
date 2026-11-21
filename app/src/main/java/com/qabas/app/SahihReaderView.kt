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
import com.qabas.app.ui.components.*
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SahihReaderView(
    initialBookType: SahihBookType = SahihBookType.BUKHARI,
    onBack: () -> Unit,
    onNavigateToVideoCreation: (quoteText: String) -> Unit = {},
    onOpenFullBook: (SahihBookType) -> Unit = {}
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
    var showFullBookDialog by remember { mutableStateOf(false) } // 🔑 حوار فتح الكتاب الكامل
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

    val theme = QabasTheme.current
    val bookAccent = if (activeBook == SahihBookType.BUKHARI) {
        if (theme.isDark) GoldPrimary else GoldPrimary
    } else {
        theme.success
    }

    Scaffold(
        containerColor = theme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            AppleTopBar(
                title = activeBook.titleAr,
                subtitle = activeBook.badge,
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = { showQuranicHarmonyDialog = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = "تكامل الوحيين (القرآن والسنة)",
                            tint = theme.primary
                        )
                    }
                    IconButton(
                        onClick = { showChallengeDialog = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = "تحدي وتدبر الصحيحين",
                            tint = bookAccent
                        )
                    }
                    IconButton(
                        onClick = {
                            fontSizeSp = if (fontSizeSp >= 22f) 15f else fontSizeSp + 2f
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.FormatSize,
                            contentDescription = "تكبير الخط",
                            tint = theme.textPrimary
                        )
                    }
                }
            )
        }
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
                AppleCard(cornerRadius = 16.dp) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        AppleSegmentedControl(
                            items = listOf(
                                SahihBookType.BUKHARI to "صحيح البخاري 📖",
                                SahihBookType.MUSLIM to "صحيح مسلم 📜"
                            ),
                            selectedItem = activeBook,
                            onSelect = {
                                activeBook = it
                                selectedChapterId = null
                            }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // فتح الكتاب الكامل (صحيح البخاري / مسلم — كامل)
                        OutlinedButton(
                            onClick = { onOpenFullBook(activeBook) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, bookAccent.copy(alpha = 0.55f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = bookAccent,
                                containerColor = bookAccent.copy(alpha = if (theme.isDark) 0.12f else 0.08f)
                            )
                        ) {
                            Icon(Icons.Default.LibraryBooks, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "📖 الكتاب كامل (7277 حديثًا للبخاري • 7459 لمسلم)",
                                fontSize = 12.sp,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Book Author Quote
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.secondarySurface.copy(alpha = if (theme.isDark) 0.7f else 0.55f))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.FormatQuote, contentDescription = null, tint = bookAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = activeBook.quote,
                                color = theme.textPrimary,
                                fontFamily = NotoSansFont,
                                fontSize = 11.5.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }

            // 2. Spotlight: حديث اليوم المختار (Daily Wisdom Spotlight)
            if (searchQuery.isBlank() && selectedChapterId == null && !showOnlyBookmarks) {
                item {
                    AppleCard(cornerRadius = 16.dp) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            theme.cardSurface,
                                            bookAccent.copy(alpha = if (theme.isDark) 0.10f else 0.06f)
                                        )
                                    )
                                )
                                .padding(14.dp)
                        ) {
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
                                            .background(bookAccent.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = bookAccent, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            "حديث اليوم من الصحيحين 🌟",
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            color = bookAccent
                                        )
                                        Text(
                                            "${dailyHadith.bookType.titleAr} • ${dailyHadith.chapterTitle}",
                                            fontFamily = NotoSansFont,
                                            fontSize = 11.sp,
                                            color = theme.textSecondary
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        onNavigateToVideoCreation("${dailyHadith.matn} — ${dailyHadith.reference}")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Icon(Icons.Default.MovieCreation, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("صناعة فيديو", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = dailyHadith.matn,
                                fontFamily = AmiriFont,
                                fontSize = 15.5.sp,
                                lineHeight = 25.sp,
                                color = theme.textPrimary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = bookAccent, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = dailyHadith.wisdomCapsule,
                                    fontFamily = NotoSansFont,
                                    fontSize = 11.5.sp,
                                    color = theme.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Search & Filter Bar
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppleSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = "ابحث بالمتن، الراوي، رقم الحديث، أو الموضوع..."
                    )

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
                                        "مدعوم بالقرآن 📖",
                                        fontFamily = CairoFont,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (showOnlyQuranEvidence) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.MenuBook,
                                        contentDescription = null,
                                        tint = if (showOnlyQuranEvidence) theme.primary else theme.textSecondary,
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
                                    selected = showOnlyQuranEvidence,
                                    borderColor = if (showOnlyQuranEvidence) theme.primary else theme.separator
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
                                        fontSize = 11.5.sp,
                                        fontWeight = if (showOnlyBookmarks) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (showOnlyBookmarks) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = null,
                                        tint = if (showOnlyBookmarks) bookAccent else theme.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = bookAccent.copy(alpha = 0.16f),
                                    selectedLabelColor = bookAccent,
                                    containerColor = theme.cardSurface,
                                    labelColor = theme.textSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = showOnlyBookmarks,
                                    borderColor = if (showOnlyBookmarks) bookAccent else theme.separator
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
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = bookAccent.copy(alpha = 0.16f),
                                    selectedLabelColor = bookAccent,
                                    containerColor = theme.cardSurface,
                                    labelColor = theme.textSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isAllSelected,
                                    borderColor = if (isAllSelected) bookAccent else theme.separator
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
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = bookAccent.copy(alpha = 0.16f),
                                    selectedLabelColor = bookAccent,
                                    containerColor = theme.cardSurface,
                                    labelColor = theme.textSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (isSelected) bookAccent else theme.separator
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
                        fontSize = 14.5.sp,
                        color = theme.textPrimary
                    )
                    Text(
                        text = "${hadiths.size} حديث موثق",
                        fontFamily = NotoSansFont,
                        fontSize = 11.5.sp,
                        color = bookAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 5. Empty State
            if (hadiths.isEmpty()) {
                item {
                    AppleCard(cornerRadius = 16.dp) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.SearchOff, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "لم نجد أحاديث مطابقة للبحث",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "جرّب البحث بكلمة أخرى أو تصفح الأبواب المختلفة",
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 12.sp
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
                        val text = "حديث شريف من ${hadith.bookType.titleAr}:\n\n${hadith.narrator}:\n«${hadith.matn}»\n\n📌 التخريج: ${hadith.reference}\n💡 الفائدة: ${hadith.wisdomCapsule}$quranPart\n\n✨ تطبيق مساعد المسلم — مشكاة الهدى"
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
    val theme = QabasTheme.current
    val bookAccent = if (hadith.bookType == SahihBookType.BUKHARI) {
        if (theme.isDark) GoldPrimary else GoldPrimary
    } else {
        theme.success
    }
    val resolvedReadingFont = CairoFont
    var isBookmarked by remember(hadith.id) { mutableStateOf(SahihHadithsRepository.isBookmarked(context, hadith.id)) }
    var isExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedOption by remember(hadith.id) { mutableIntStateOf(-1) }
    var isAnswered by remember(hadith.id) { mutableStateOf(false) }

    Surface(
        color = theme.cardSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            if (isSpeaking) 1.dp else 0.5.dp,
            if (isSpeaking) bookAccent else theme.separator.copy(alpha = 0.45f)
        ),
        shadowElevation = if (theme.isDark) 0.dp else 1.5.dp,
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
                        color = bookAccent.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "حديث #${hadith.hadithNumber}",
                            color = bookAccent,
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
                        fontSize = 11.5.sp,
                        color = theme.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleSpeech,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                            contentDescription = "استماع صوتي",
                            tint = if (isSpeaking) bookAccent else theme.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            isBookmarked = SahihHadithsRepository.toggleBookmark(context, hadith.id)
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "حفظ في المفضلة",
                            tint = if (isBookmarked) bookAccent else theme.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Hadith Title / Chapter Title
            Text(
                text = hadith.title,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp,
                color = theme.textPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Matn
            Surface(
                color = theme.secondarySurface.copy(alpha = if (theme.isDark) 0.72f else 0.45f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = hadith.matn,
                    fontFamily = resolvedReadingFont,
                    fontWeight = FontWeight.Normal,
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.65f).sp,
                    color = theme.textPrimary,
                    modifier = Modifier.padding(14.dp),
                    textAlign = TextAlign.Justify
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Wisdom Capsule
            Surface(
                color = bookAccent.copy(alpha = if (theme.isDark) 0.12f else 0.08f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(0.5.dp, bookAccent.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = bookAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = hadith.wisdomCapsule,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp,
                        color = theme.textPrimary,
                        lineHeight = 17.sp
                    )
                }
            }

            // Quranic Evidence Spotlight Banner
            hadith.quranEvidence?.let { qe ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = theme.primary.copy(alpha = if (theme.isDark) 0.12f else 0.07f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, theme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = theme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "الدليل من القرآن الكريم",
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    color = theme.primary
                                )
                            }
                            Surface(
                                color = theme.primary.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${qe.surahName} [آية ${qe.ayahNumber}]",
                                    fontFamily = NotoSansFont,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.primary,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "« ${qe.verseText} »",
                            fontFamily = AmiriFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = (fontSizeSp * 0.98f).sp,
                            lineHeight = (fontSizeSp * 1.55f).sp,
                            color = theme.textPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "🔗 وجه التكامل: ${qe.relationType}",
                            fontFamily = NotoSansFont,
                            fontSize = 11.sp,
                            color = theme.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Actions Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.secondarySurface)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = theme.textPrimary, modifier = Modifier.size(18.dp))
                }

                IconButton(
                    onClick = onShare,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.secondarySurface)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = theme.textPrimary, modifier = Modifier.size(18.dp))
                }

                Button(
                    onClick = onConvertToVideo,
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.MovieCreation, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("صناعة فيديو", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { isExpanded = !isExpanded },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, bookAccent.copy(alpha = 0.65f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        if (isExpanded) "إخفاء ▲" else "الشرح ▼",
                        color = bookAccent,
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
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
                    HorizontalDivider(color = theme.separator.copy(alpha = 0.45f), thickness = 0.5.dp)

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secondary Sub-tabs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TabButton(
                            title = "الشرح",
                            isSelected = selectedTab == 0,
                            accentColor = bookAccent,
                            onClick = { selectedTab = 0 },
                            modifier = Modifier.weight(1f)
                        )
                        TabButton(
                            title = "الفوائد",
                            isSelected = selectedTab == 1,
                            accentColor = bookAccent,
                            onClick = { selectedTab = 1 },
                            modifier = Modifier.weight(1f)
                        )
                        if (hadith.quranEvidence != null) {
                            TabButton(
                                title = "القرآن",
                                isSelected = selectedTab == 2,
                                accentColor = theme.primary,
                                onClick = { selectedTab = 2 },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (hadith.quiz != null) {
                            TabButton(
                                title = "اختبار",
                                isSelected = selectedTab == 3,
                                accentColor = bookAccent,
                                onClick = { selectedTab = 3 },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    when (selectedTab) {
                        0 -> {
                            Text(
                                text = hadith.explanation,
                                fontFamily = NotoSansFont,
                                fontSize = 12.5.sp,
                                lineHeight = 20.sp,
                                color = theme.textPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "المصدر: ${hadith.reference}",
                                fontFamily = NotoSansFont,
                                fontSize = 11.sp,
                                color = theme.textSecondary
                            )
                        }
                        1 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                hadith.practicalBenefits.forEach { benefit ->
                                    Row(verticalAlignment = Alignment.Top) {
                                        Text("✔", color = theme.success, fontSize = 12.sp, modifier = Modifier.padding(end = 6.dp))
                                        Text(
                                            text = benefit,
                                            fontFamily = NotoSansFont,
                                            fontSize = 12.5.sp,
                                            lineHeight = 19.sp,
                                            color = theme.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                        2 -> {
                            hadith.quranEvidence?.let { qe ->
                                Surface(
                                    color = theme.secondarySurface.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(0.5.dp, theme.primary.copy(alpha = 0.35f)),
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
                                                color = theme.primary
                                            )
                                            Surface(
                                                color = theme.primary.copy(alpha = 0.14f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "تكامل الوحيين",
                                                    color = theme.primary,
                                                    fontSize = 10.sp,
                                                    fontFamily = CairoFont,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = "« ${qe.verseText} »",
                                            fontFamily = AmiriFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            lineHeight = 24.sp,
                                            color = theme.textPrimary,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        HorizontalDivider(color = theme.separator.copy(alpha = 0.45f), thickness = 0.5.dp)

                                        Text(
                                            text = "🔹 وجه الدلالة والتكامل:\n${qe.relationType}",
                                            fontFamily = NotoSansFont,
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp,
                                            color = theme.textPrimary
                                        )

                                        Text(
                                            text = "💡 البيان والتفسير:\n${qe.commentary}",
                                            fontFamily = NotoSansFont,
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp,
                                            color = theme.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                        3 -> {
                            hadith.quiz?.let { quiz ->
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = quiz.question,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = theme.textPrimary
                                    )

                                    quiz.options.forEachIndexed { index, option ->
                                        val isChosen = selectedOption == index
                                        val isCorrect = quiz.correctIndex == index
                                        val containerBg = when {
                                            !isAnswered -> if (isChosen) bookAccent.copy(alpha = 0.16f) else theme.secondarySurface
                                            isCorrect -> theme.success.copy(alpha = 0.18f)
                                            isChosen && !isCorrect -> theme.destructive.copy(alpha = 0.18f)
                                            else -> theme.secondarySurface
                                        }
                                        val borderCol = when {
                                            !isAnswered -> if (isChosen) bookAccent else theme.separator
                                            isCorrect -> theme.success
                                            isChosen && !isCorrect -> theme.destructive
                                            else -> theme.separator
                                        }

                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .defaultMinSize(minHeight = 48.dp)
                                                .clickable(enabled = !isAnswered) {
                                                    selectedOption = index
                                                    isAnswered = true
                                                },
                                            shape = RoundedCornerShape(10.dp),
                                            color = containerBg,
                                            border = BorderStroke(1.dp, borderCol)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${('أ'.code + index).toChar()}.",
                                                    fontFamily = CairoFont,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = bookAccent,
                                                    modifier = Modifier.padding(end = 8.dp)
                                                )
                                                Text(
                                                    text = option,
                                                    fontFamily = NotoSansFont,
                                                    fontSize = 12.sp,
                                                    color = theme.textPrimary,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (isAnswered) {
                                                    Icon(
                                                        imageVector = if (isCorrect) Icons.Default.CheckCircle else if (isChosen) Icons.Default.Cancel else Icons.Default.Circle,
                                                        contentDescription = null,
                                                        tint = if (isCorrect) theme.success else if (isChosen) theme.destructive else Color.Transparent,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (isAnswered) {
                                        Surface(
                                            color = theme.primary.copy(alpha = 0.10f),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "💡 التوجيه والبيان: ${quiz.explanation}",
                                                fontFamily = NotoSansFont,
                                                fontSize = 11.5.sp,
                                                color = theme.primary,
                                                modifier = Modifier.padding(10.dp)
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
    val theme = QabasTheme.current
    Surface(
        modifier = modifier
            .heightIn(min = 42.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) accentColor.copy(alpha = 0.16f) else theme.secondarySurface,
        border = BorderStroke(0.8.dp, if (isSelected) accentColor else theme.separator.copy(alpha = 0.5f))
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp)) {
            Text(
                text = title,
                fontFamily = CairoFont,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 11.sp,
                color = if (isSelected) accentColor else theme.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SahihChallengeDialog(
    activeBook: SahihBookType,
    onDismiss: () -> Unit
) {
    val theme = QabasTheme.current
    val bookAccent = if (activeBook == SahihBookType.BUKHARI) {
        if (theme.isDark) GoldPrimary else GoldPrimary
    } else {
        theme.success
    }

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
                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = bookAccent)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "تحدي وتدبر ${activeBook.titleAr}",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = theme.textPrimary
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
                            .background(bookAccent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MilitaryTech, contentDescription = null, tint = bookAccent, modifier = Modifier.size(40.dp))
                    }
                    Text(
                        text = "ما شاء الله! مبارك إتمام التحدي",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = bookAccent,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "حققت $score من أصل ${hadiths.size} إجابات صحيحة في تدبر وفهم الأحاديث الشريفة.",
                        fontFamily = NotoSansFont,
                        fontSize = 12.5.sp,
                        color = theme.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Surface(
                        color = theme.secondarySurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "«نضّر الله امرأً سمع مقالتي فوعاها فأداها كما سمعها» — زادك الله علماً وهدى وبركة.",
                            color = theme.primary,
                            fontFamily = CairoFont,
                            fontSize = 11.5.sp,
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
                            color = theme.primary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                        Text(
                            "النقاط: $score",
                            color = bookAccent,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                    }

                    Surface(
                        color = theme.secondarySurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = currentHadith.matn.take(120) + "...",
                            fontFamily = CairoFont,
                            fontSize = 11.5.sp,
                            color = theme.textSecondary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Text(
                        text = quiz.question,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = theme.textPrimary
                    )

                    quiz.options.forEachIndexed { index, option ->
                        val isChosen = selectedOption == index
                        val isCorrect = quiz.correctIndex == index
                        val bg = when {
                            !isSubmitted -> if (isChosen) theme.primary.copy(alpha = 0.16f) else theme.secondarySurface
                            isCorrect -> theme.success.copy(alpha = 0.2f)
                            isChosen && !isCorrect -> theme.destructive.copy(alpha = 0.2f)
                            else -> theme.secondarySurface
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clickable(enabled = !isSubmitted) {
                                    selectedOption = index
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = bg,
                            border = BorderStroke(1.dp, if (isChosen) theme.primary else theme.separator)
                        ) {
                            Text(
                                text = option,
                                fontFamily = NotoSansFont,
                                fontSize = 12.sp,
                                color = theme.textPrimary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    if (isSubmitted) {
                        Text(
                            text = "💡 ${quiz.explanation}",
                            fontFamily = NotoSansFont,
                            fontSize = 11.5.sp,
                            color = theme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isFinished) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("تم", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
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
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("تحقق من الإجابة", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
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
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(if (currentIndex + 1 < hadiths.size) "السؤال التالي" else "عرض النتيجة", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!isFinished) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("إلغاء", color = theme.textSecondary, fontFamily = CairoFont)
                }
            }
        },
        containerColor = theme.cardSurface,
        shape = RoundedCornerShape(18.dp)
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
    val theme = QabasTheme.current

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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = theme.primary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "تكامل الوحيين: القرآن والسنة",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = theme.textPrimary
                        )
                        Text(
                            text = "أحاديث الصحيحين المقترنة بأدلتها من كتاب الله",
                            fontFamily = NotoSansFont,
                            fontSize = 10.5.sp,
                            color = theme.textSecondary
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = theme.textSecondary, modifier = Modifier.size(18.dp))
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
                AppleSegmentedControl(
                    items = listOf(
                        null to "الكل (${SahihHadithsRepository.getHadithsWithQuranEvidence().size})",
                        SahihBookType.BUKHARI to "البخاري (${SahihHadithsRepository.getHadithsWithQuranEvidence(SahihBookType.BUKHARI).size})",
                        SahihBookType.MUSLIM to "مسلم (${SahihHadithsRepository.getHadithsWithQuranEvidence(SahihBookType.MUSLIM).size})"
                    ),
                    selectedItem = selectedBookFilter,
                    onSelect = { selectedBookFilter = it }
                )

                AppleSearchBar(
                    query = harmonyQuery,
                    onQueryChange = { harmonyQuery = it },
                    placeholder = "ابحث بالسورة أو الآية أو موضوع الحديث..."
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(allEvidenceHadiths, key = { it.id }) { hadith ->
                        val qe = hadith.quranEvidence ?: return@items
                        val bookColor = if (hadith.bookType == SahihBookType.BUKHARI) {
                            if (theme.isDark) GoldPrimary else GoldPrimary
                        } else {
                            theme.success
                        }

                        Surface(
                            color = theme.secondarySurface.copy(alpha = if (theme.isDark) 0.7f else 0.45f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(0.5.dp, theme.separator),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = theme.primary.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "📖 ${qe.surahName} [آية ${qe.ayahNumber}]",
                                            color = theme.primary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = CairoFont,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }

                                    Surface(
                                        color = bookColor.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "${hadith.bookType.titleAr} #${hadith.hadithNumber}",
                                            color = bookColor,
                                            fontSize = 10.sp,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Surface(
                                    color = theme.primary.copy(alpha = if (theme.isDark) 0.12f else 0.07f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(0.5.dp, theme.primary.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "« ${qe.verseText} »",
                                        fontFamily = AmiriFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp,
                                        lineHeight = 23.sp,
                                        color = theme.textPrimary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }

                                Surface(
                                    color = theme.cardSurface,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "${hadith.narrator}:",
                                            fontFamily = CairoFont,
                                            fontSize = 11.sp,
                                            color = theme.textSecondary
                                        )
                                        Text(
                                            text = "«${hadith.matn}»",
                                            fontFamily = AmiriFont,
                                            fontSize = 13.5.sp,
                                            lineHeight = 22.sp,
                                            color = theme.textPrimary
                                        )
                                    }
                                }

                                Text(
                                    text = "🔗 وجه التكامل والدلالة: ${qe.relationType}",
                                    fontFamily = NotoSansFont,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = theme.primary
                                )

                                Text(
                                    text = "💡 ${qe.commentary}",
                                    fontFamily = NotoSansFont,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    color = theme.textSecondary
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            val text = "قال تعالى: «${qe.verseText}» [${qe.surahName}: ${qe.ayahNumber}]\n\nوقال النبي ﷺ في ${hadith.bookType.titleAr}:\n«${hadith.matn}» [${hadith.reference}]\n\n🔗 وجه التكامل: ${qe.relationType}\n\n✨ تطبيق مساعد المسلم"
                                            clipboardManager.setText(AnnotatedString(text))
                                            Toast.makeText(context, "تم نسخ الآية والحديث بنجاح ✅", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = theme.textSecondary, modifier = Modifier.size(17.dp))
                                    }

                                    IconButton(
                                        onClick = {
                                            val text = "📖 تكامل الوحيين (القرآن والسنة):\n\nقال الله تعالى:\n«${qe.verseText}» [${qe.surahName}: ${qe.ayahNumber}]\n\nوقال رسول الله ﷺ:\n«${hadith.matn}» [${hadith.reference}]\n\n🔗 وجه الدلالة والتكامل:\n${qe.relationType}\n\n💡 البيان: ${qe.commentary}\n\n✨ استوديو مساعد المسلم — مشكاة الهدى"
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, text)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "مشاركة تكامل الوحيين"))
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = theme.textSecondary, modifier = Modifier.size(17.dp))
                                    }

                                    Button(
                                        onClick = {
                                            val quote = "قال تعالى: «${qe.verseText}» [${qe.surahName}: ${qe.ayahNumber}] — وقال ﷺ: «${hadith.matn}»"
                                            onNavigateToVideoCreation(quote)
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Default.MovieCreation, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("صناعة فيديو", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
                colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("إغلاق", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = theme.cardSurface,
        shape = RoundedCornerShape(18.dp)
    )
}
