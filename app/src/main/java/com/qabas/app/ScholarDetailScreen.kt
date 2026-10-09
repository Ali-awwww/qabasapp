package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qabas.app.R
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScholarDetailScreen(
    scholar: ScholarBiography,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { ScholarPreferences(context) }
    val enrichedScholar = remember(scholar) { ScholarEnrichmentData.enrich(scholar) }
    var isBookmarked by remember { mutableStateOf(prefs.isFavorite(scholar.id)) }
    var isChallengeDone by remember { mutableStateOf(prefs.isChallengeCompleted(scholar.id)) }

    var showLecturesSheet by remember { mutableStateOf(false) }
    var lecturesCount by remember { mutableIntStateOf(prefs.getLectureCountForScholar(scholar.id)) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showShareModal by remember { mutableStateOf(false) }

    val hasCustomFacets = enrichedScholar.customFacets.isNotEmpty()
    val facets = enrichedScholar.customFacets

    val tabs = remember(enrichedScholar, lecturesCount) {
        if (hasCustomFacets) {
            val list = mutableListOf<String>()
            list.add("📜 المخطوطة الجامعة")
            facets.forEach { facet ->
                list.add("⭐ ${facet.title}")
            }
            list.add("📚 الدروس والمحاضرات ($lecturesCount)")
            list.add("🗺️ رحلة العلم")
            list.add("💌 الوصية والتحدي")
            list
        } else {
            listOf(
                "📜 المخطوطة",
                "📚 الدروس والمحاضرات ($lecturesCount)",
                "🗺️ رحلة العلم",
                "⛓️ المحنة والأذى",
                "🛡️ الصبر والثبات",
                "🌅 مشهد الوفاة",
                "💌 الوصية للأمة"
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Full Page Manuscript Background
        AsyncImage(
            model = R.drawable.scholar_manuscript_bg_1790092377371,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.18f
        )
        Box(modifier = Modifier.fillMaxSize().background(AntiqueParchment.copy(alpha = 0.90f)))

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            enrichedScholar.name,
                            color = OldInk,
                            fontFamily = AmiriFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = OldInk)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            isBookmarked = prefs.toggleFavorite(scholar.id)
                            Toast.makeText(
                                context,
                                if (isBookmarked) "تمت الإضافة للمفضلة ⭐" else "تمت الإزالة من المفضلة",
                                Toast.LENGTH_SHORT
                            ).show()
                        }) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "المفضلة",
                                tint = if (isBookmarked) ManuscriptGold else OldInk.copy(alpha = 0.7f)
                            )
                        }
                        IconButton(onClick = { showLecturesSheet = true }) {
                            BadgedBox(
                                badge = {
                                    if (lecturesCount > 0) {
                                        Badge(containerColor = ManuscriptGold, contentColor = Color.White) {
                                            Text("$lecturesCount", fontSize = 10.sp)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideoLibrary,
                                    contentDescription = "الدروس والمحاضرات والمراجع",
                                    tint = ManuscriptGold
                                )
                            }
                        }
                        IconButton(onClick = { showShareModal = true }) {
                            Icon(Icons.Default.Share, contentDescription = "مشاركة السيرة كاملة", tint = ManuscriptGold)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Quick Category Tab Bar
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = ManuscriptGold,
                    edgePadding = 16.dp,
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    title,
                                    fontFamily = CairoFont,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.5.sp,
                                    color = if (selectedTab == index) OldInk else OldInk.copy(alpha = 0.6f)
                                )
                            }
                        )
                    }
                }

                Divider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = ManuscriptGold.copy(alpha = 0.3f),
                    thickness = 1.dp
                )

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Portrait & Hero Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = AgedPaper,
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(2.dp, ManuscriptGold.copy(alpha = 0.4f)),
                        shadowElevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                modifier = Modifier.size(140.dp),
                                shape = CircleShape,
                                border = androidx.compose.foundation.BorderStroke(3.dp, ManuscriptGold),
                                shadowElevation = 6.dp
                            ) {
                                AsyncImage(
                                    model = enrichedScholar.imageRes,
                                    contentDescription = enrichedScholar.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = enrichedScholar.name,
                                color = OldInk,
                                fontFamily = AmiriFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 26.sp,
                                textAlign = TextAlign.Center
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                enrichedScholar.era?.let { era ->
                                    Surface(
                                        color = ManuscriptGold.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.35f))
                                    ) {
                                        Text(
                                            text = era,
                                            color = ManuscriptGold,
                                            fontFamily = CairoFont,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                enrichedScholar.period?.let {
                                    Text(
                                        text = it,
                                        color = OldInk.copy(alpha = 0.7f),
                                        fontFamily = CairoFont,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            enrichedScholar.majorHistoricalEvent?.let { event ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = AntiqueParchment,
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Stars, contentDescription = null, tint = ManuscriptGold, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "الحدث الأبرز: $event",
                                            color = OldInk,
                                            fontFamily = CairoFont,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Endurance Quote Pill
                            enrichedScholar.enduranceQuote?.let { quote ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = ManuscriptGold.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.25f))
                                ) {
                                    Text(
                                        text = "« $quote »",
                                        color = OldInk,
                                        fontFamily = AmiriFont,
                                        fontSize = 17.sp,
                                        lineHeight = 26.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Dynamic Section Display based on tab
                    if (hasCustomFacets) {
                        when (selectedTab) {
                            0 -> {
                                ManuscriptIntroSection(enrichedScholar)
                                Spacer(modifier = Modifier.height(16.dp))
                                CharacterFacetsOverviewSection(
                                    facets = facets,
                                    onSelectFacet = { index -> selectedTab = index + 1 }
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                ScholarLecturesDetailSection(
                                    scholar = enrichedScholar,
                                    onAddClick = { showLecturesSheet = true }
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                SpatialJourneySection(enrichedScholar)
                                Spacer(modifier = Modifier.height(16.dp))
                                LegacyAndMessageSection(
                                    context = context,
                                    scholar = enrichedScholar,
                                    isChallengeCompleted = isChallengeDone,
                                    onToggleChallenge = {
                                        isChallengeDone = prefs.toggleChallengeCompleted(scholar.id)
                                        Toast.makeText(
                                            context,
                                            if (isChallengeDone) "تقبل الله طاعتكم وجعلكم من المقتدين الصادقين! ✨" else "تم إلغاء الإنجاز",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            }
                            in 1..facets.size -> {
                                val currentFacetIndex = selectedTab - 1
                                val currentFacet = facets[currentFacetIndex]
                                val nextFacetAction: (() -> Unit)? = if (currentFacetIndex + 1 < facets.size) {
                                    { selectedTab = selectedTab + 1 }
                                } else null

                                CharacterFacetDetailSection(
                                    context = context,
                                    facet = currentFacet,
                                    scholarName = enrichedScholar.name,
                                    onBackToOverview = { selectedTab = 0 },
                                    onNextFacet = nextFacetAction
                                )
                            }
                            facets.size + 1 -> {
                                ScholarLecturesDetailSection(
                                    scholar = enrichedScholar,
                                    onAddClick = { showLecturesSheet = true }
                                )
                            }
                            facets.size + 2 -> {
                                SpatialJourneySection(enrichedScholar)
                            }
                            facets.size + 3 -> {
                                LegacyAndMessageSection(
                                    context = context,
                                    scholar = enrichedScholar,
                                    isChallengeCompleted = isChallengeDone,
                                    onToggleChallenge = {
                                        isChallengeDone = prefs.toggleChallengeCompleted(scholar.id)
                                        Toast.makeText(
                                            context,
                                            if (isChallengeDone) "تقبل الله طاعتكم وجعلكم من المقتدين الصادقين! ✨" else "تم إلغاء الإنجاز",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            }
                        }
                    } else {
                        when (selectedTab) {
                            0 -> {
                                ManuscriptIntroSection(enrichedScholar)
                                Spacer(modifier = Modifier.height(16.dp))
                                ScholarLecturesDetailSection(
                                    scholar = enrichedScholar,
                                    onAddClick = { showLecturesSheet = true }
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                SpatialJourneySection(enrichedScholar)
                                Spacer(modifier = Modifier.height(16.dp))
                                HarmedAndPersecutionSection(context, enrichedScholar)
                                Spacer(modifier = Modifier.height(16.dp))
                                EnduranceAndPurposeSection(context, enrichedScholar)
                                Spacer(modifier = Modifier.height(16.dp))
                                DeathSceneAndFarewellSection(context, enrichedScholar)
                                Spacer(modifier = Modifier.height(16.dp))
                                LegacyAndMessageSection(
                                    context = context,
                                    scholar = enrichedScholar,
                                    isChallengeCompleted = isChallengeDone,
                                    onToggleChallenge = {
                                        isChallengeDone = prefs.toggleChallengeCompleted(scholar.id)
                                        Toast.makeText(
                                            context,
                                            if (isChallengeDone) "تقبل الله طاعتكم وجعلكم من المقتدين الصادقين! ✨" else "تم إلغاء الإنجاز",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            }
                            1 -> {
                                ScholarLecturesDetailSection(
                                    scholar = enrichedScholar,
                                    onAddClick = { showLecturesSheet = true }
                                )
                            }
                            2 -> {
                                SpatialJourneySection(enrichedScholar)
                            }
                            3 -> {
                                HarmedAndPersecutionSection(context, enrichedScholar)
                            }
                            4 -> {
                                EnduranceAndPurposeSection(context, enrichedScholar)
                            }
                            5 -> {
                                DeathSceneAndFarewellSection(context, enrichedScholar)
                            }
                            6 -> {
                                LegacyAndMessageSection(
                                    context = context,
                                    scholar = enrichedScholar,
                                    isChallengeCompleted = isChallengeDone,
                                    onToggleChallenge = {
                                        isChallengeDone = prefs.toggleChallengeCompleted(scholar.id)
                                        Toast.makeText(
                                            context,
                                            if (isChallengeDone) "تقبل الله طاعتكم وجعلكم من المقتدين الصادقين! ✨" else "تم إلغاء الإنجاز",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Universal Share Button
                    Button(
                        onClick = { showShareModal = true },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ManuscriptGold),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "مشاركة السيرة كاملة (صورة أو نص)",
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }

    // Lectures Bottom Sheet Dialog
    if (showLecturesSheet) {
        ScholarLecturesSheet(
            scholar = enrichedScholar,
            onDismiss = { showLecturesSheet = false },
            onLecturesCountChanged = { lecturesCount = it }
        )
    }

    // Share Bottom Sheet Dialog
    if (showShareModal) {
        ScholarShareModal(
            scholar = enrichedScholar,
            onDismiss = { showShareModal = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScholarShareModal(
    scholar: ScholarBiography,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedFormat by remember { mutableIntStateOf(0) } // 0: صورة كاملة, 1: نص شامل
    var selectedStyle by remember { mutableStateOf(ScholarCardImageGenerator.CardStyle.FULL_EPIC) }
    var selectedTheme by remember { mutableStateOf(ScholarCardImageGenerator.CardTheme.CLASSIC_MANUSCRIPT) }
    var renderedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGeneratingImage by remember { mutableStateOf(false) }
    var isSavingToGallery by remember { mutableStateOf(false) }

    // Generate bitmap whenever selected style, theme, or dialog opens
    LaunchedEffect(selectedStyle, selectedTheme) {
        isGeneratingImage = true
        try {
            renderedBitmap = ScholarCardImageGenerator.generateScholarBitmap(context, scholar, selectedStyle, selectedTheme)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "حدث خطأ أثناء إعداد الصورة", Toast.LENGTH_SHORT).show()
        } finally {
            isGeneratingImage = false
        }
    }

    val fullText = remember(scholar) {
        ScholarCardImageGenerator.buildFullBiographyText(scholar)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AgedPaper,
        dragHandle = { BottomSheetDefaults.DragHandle(color = ManuscriptGold) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = OldInk)
                }

                Text(
                    text = "مشاركة سيرة الإمام ${scholar.name}",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = OldInk
                )

                Icon(
                    Icons.Default.AutoStories,
                    contentDescription = null,
                    tint = ManuscriptGold,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tab Selector: صورة عالية الدقة / نص شامل
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedFormat = 0 },
                    color = if (selectedFormat == 0) ManuscriptGold else Color.Transparent,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            tint = if (selectedFormat == 0) Color.White else OldInk,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "بطاقة مصورة (تفاصيل كاملة)",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (selectedFormat == 0) Color.White else OldInk
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedFormat = 1 },
                    color = if (selectedFormat == 1) ManuscriptGold else Color.Transparent,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Article,
                            contentDescription = null,
                            tint = if (selectedFormat == 1) Color.White else OldInk,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "نص السيرة الشامل",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (selectedFormat == 1) Color.White else OldInk
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedFormat == 0) {
                // Image Share Content
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Style Selector (الملحمية الكاملة vs قصة سريعة)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedStyle == ScholarCardImageGenerator.CardStyle.FULL_EPIC,
                            onClick = { selectedStyle = ScholarCardImageGenerator.CardStyle.FULL_EPIC },
                            label = { Text("المخطوطة الملحمية الكاملة", fontFamily = CairoFont, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ManuscriptGold,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedStyle == ScholarCardImageGenerator.CardStyle.HIGHLIGHT_STORY,
                            onClick = { selectedStyle = ScholarCardImageGenerator.CardStyle.HIGHLIGHT_STORY },
                            label = { Text("بطاقة الحالة والقصة (9:16)", fontFamily = CairoFont, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ManuscriptGold,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Theme Selector (مخطوطة كلاسيكية / ديباج زمردي / رخام أسود)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedTheme == ScholarCardImageGenerator.CardTheme.CLASSIC_MANUSCRIPT,
                            onClick = { selectedTheme = ScholarCardImageGenerator.CardTheme.CLASSIC_MANUSCRIPT },
                            label = { Text("📜 مخطوطة ذهب", fontFamily = CairoFont, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ManuscriptGold,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedTheme == ScholarCardImageGenerator.CardTheme.EMERALD_GOLD,
                            onClick = { selectedTheme = ScholarCardImageGenerator.CardTheme.EMERALD_GOLD },
                            label = { Text("🌿 ديباج زمردي", fontFamily = CairoFont, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF065F46),
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedTheme == ScholarCardImageGenerator.CardTheme.OBSIDIAN_LUXURY,
                            onClick = { selectedTheme = ScholarCardImageGenerator.CardTheme.OBSIDIAN_LUXURY },
                            label = { Text("🖤 رخام ملكي", fontFamily = CairoFont, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF1E293B),
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live Image Preview Card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        color = Color.White.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, ManuscriptGold.copy(alpha = 0.4f))
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (isGeneratingImage) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = ManuscriptGold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "جاري رسم وتجهيز بطاقة السيرة بجودة عالية...",
                                        fontFamily = CairoFont,
                                        fontSize = 12.sp,
                                        color = OldInk
                                    )
                                }
                            } else if (renderedBitmap != null) {
                                Image(
                                    bitmap = renderedBitmap!!.asImageBitmap(),
                                    contentDescription = "معاينة البطاقة المصورة",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons Row (Share Image + Save to Gallery)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val bmp = renderedBitmap ?: return@launch
                                    val uri = ScholarCardImageGenerator.saveBitmapToCache(context, bmp, scholar.name)
                                    ScholarCardImageGenerator.shareImageUri(context, uri, scholar)
                                }
                            },
                            modifier = Modifier
                                .weight(1.3f)
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ManuscriptGold),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isGeneratingImage && renderedBitmap != null
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "مشاركة الصورة",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val bmp = renderedBitmap ?: return@launch
                                    isSavingToGallery = true
                                    val success = ScholarCardImageGenerator.saveBitmapToGallery(context, bmp, scholar.name)
                                    isSavingToGallery = false
                                    if (success) {
                                        Toast.makeText(context, "تم حفظ بطاقة السيرة في المعرض بنجاح ✨", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "تعذر حفظ الصورة في المعرض", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, ManuscriptGold),
                            enabled = !isGeneratingImage && renderedBitmap != null && !isSavingToGallery
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = ManuscriptGold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (isSavingToGallery) "جاري الحفظ.." else "حفظ بالمعرض",
                                color = ManuscriptGold,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                // Text Share Content
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Scrollable Text Preview Box
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        color = Color.White.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, ManuscriptGold.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = fullText,
                                color = OldInk,
                                fontFamily = AmiriFont,
                                fontSize = 16.sp,
                                lineHeight = 26.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons Row (Share Text + Copy Text)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                ScholarCardImageGenerator.shareText(context, scholar)
                            },
                            modifier = Modifier
                                .weight(1.2f)
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ManuscriptGold),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "مشاركة النص كاملاً",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                copyToClipboard(context, "سيرة الإمام ${scholar.name}", fullText)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, ManuscriptGold)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = ManuscriptGold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "نسخ النص",
                                color = ManuscriptGold,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * قسم استعراض الدروس والمحاضرات المعتمدة ومقترحات المستخدمين لسيرة الإمام
 */
@Composable
fun ScholarLecturesDetailSection(
    scholar: ScholarBiography,
    onAddClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { ScholarPreferences(context) }
    val officialLectures = remember(scholar.id) { prefs.getOfficialLectures(scholar.id, scholar.name) }
    val userProposals = remember(scholar.id) { prefs.getUserProposals(scholar.id) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AgedPaper,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, ManuscriptGold.copy(alpha = 0.35f)),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = ManuscriptGold,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "الدروس المعتمدة والمقترحات",
                        color = ManuscriptGold,
                        fontFamily = CairoFont,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = ManuscriptGold.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "${officialLectures.size} دروس معتمدة",
                        color = OldInk,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "دروس موثقة ومعتمدة من المنصة للدخول والمشاهدة، مع إمكانية اقتراح عناوين ومحاور علمية جديدة لطلاب العلم.",
                color = OldInk.copy(alpha = 0.85f),
                fontFamily = NotoSansFont,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Official Curated Lectures Preview
            Text(
                text = "🌟 الدروس المعتمدة للمشاهدة المباشرة:",
                fontFamily = CairoFont,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = OldInk
            )
            Spacer(modifier = Modifier.height(6.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                officialLectures.take(3).forEach { lecture ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = AntiqueParchment,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = ManuscriptGold.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = lecture.category,
                                        fontFamily = CairoFont,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ManuscriptGold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }

                                if (lecture.speaker.isNotBlank()) {
                                    Text(
                                        text = "🎙️ ${lecture.speaker}",
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp,
                                        color = OldInk.copy(alpha = 0.75f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = lecture.title,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = OldInk
                            )

                            if (lecture.synopsis.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = lecture.synopsis,
                                    fontFamily = NotoSansFont,
                                    fontSize = 11.5.sp,
                                    color = OldInk.copy(alpha = 0.8f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 17.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { openLectureUrl(context, lecture.url) },
                                    modifier = Modifier.weight(1.3f).height(38.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("مشاهدة واستماع 🚀", fontFamily = CairoFont, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }

                                Button(
                                    onClick = { shareLecture(context, lecture) },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ManuscriptGold),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "مشاركة", modifier = Modifier.size(14.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("مشاركة 📤", fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // User proposals preview
            if (userProposals.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💡 مقترحات المستمعين (${userProposals.size}):",
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = OldInk
                    )
                    Text(
                        text = "أعلى تأييد: ${userProposals.first().upvotesCount} 👍",
                        fontFamily = CairoFont,
                        fontSize = 10.5.sp,
                        color = ManuscriptGold,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = Color.Black.copy(alpha = 0.03f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "« ${userProposals.first().suggestedTitle} » — ${userProposals.first().suggestedSpeaker}",
                        fontFamily = CairoFont,
                        fontSize = 11.5.sp,
                        color = OldInk.copy(alpha = 0.85f),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: فتح المكتبة / اقتراح درس
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAddClick,
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ManuscriptGold),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.LibraryBooks, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "استعراض المكتبة والمقترحات",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                OutlinedButton(
                    onClick = onAddClick,
                    modifier = Modifier.weight(0.9f).height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, ManuscriptGold)
                ) {
                    Icon(Icons.Default.EditNote, contentDescription = null, tint = ManuscriptGold, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "+ اقترح عنواناً",
                        color = ManuscriptGold,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

fun getFacetIcon(iconKey: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (iconKey) {
        "shield" -> Icons.Default.Shield
        "sword", "military", "gavel" -> Icons.Default.Gavel
        "hand" -> Icons.Default.VolunteerActivism
        "scale" -> Icons.Default.Balance
        "mosque" -> Icons.Default.Mosque
        "pray" -> Icons.Default.SelfImprovement
        "quran", "scroll", "book", "poetry" -> Icons.Default.MenuBook
        "blood", "healing" -> Icons.Default.Healing
        "light" -> Icons.Default.Lightbulb
        "heart" -> Icons.Default.Favorite
        "star" -> Icons.Default.Star
        "tent" -> Icons.Default.Home
        "bow", "fortress" -> Icons.Default.AccountBalance
        "market" -> Icons.Default.Storefront
        "crown" -> Icons.Default.MilitaryTech
        "speech" -> Icons.Default.RecordVoiceOver
        "wings" -> Icons.Default.Air
        "bed" -> Icons.Default.Hotel
        "rock" -> Icons.Default.Landscape
        "tear", "water" -> Icons.Default.WaterDrop
        "sparkle" -> Icons.Default.AutoAwesome
        "compass" -> Icons.Default.Explore
        "pickaxe" -> Icons.Default.Build
        "palm" -> Icons.Default.Park
        "smile" -> Icons.Default.EmojiEmotions
        "poison", "science" -> Icons.Default.Science
        "brain" -> Icons.Default.Psychology
        "prison" -> Icons.Default.Lock
        "path" -> Icons.Default.DirectionsWalk
        "crowd" -> Icons.Default.Groups
        "perfume" -> Icons.Default.AutoFixHigh
        "unite" -> Icons.Default.GroupWork
        "pen" -> Icons.Default.EditNote
        "link" -> Icons.Default.Link
        "sun" -> Icons.Default.WbSunny
        "library" -> Icons.Default.LocalLibrary
        "eye" -> Icons.Default.Visibility
        else -> Icons.Default.AutoStories
    }
}

@Composable
fun CharacterFacetsOverviewSection(
    facets: List<CharacterFacet>,
    onSelectFacet: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AgedPaper,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, ManuscriptGold.copy(alpha = 0.45f)),
        shadowElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = ManuscriptGold,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "محاور العبقرية والسيرة الخالدة:",
                        color = ManuscriptGold,
                        fontFamily = CairoFont,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = ManuscriptGold.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "${facets.size} محاور متفردة",
                        color = OldInk,
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "كُتبت هذه المحاور خصيصاً وفق فرادة العَلَم ومجالات أثره وعبقريته في أمة الإسلام:",
                color = OldInk.copy(alpha = 0.75f),
                fontFamily = CairoFont,
                fontSize = 12.5.sp,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                facets.forEachIndexed { index, facet ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectFacet(index) },
                        color = AntiqueParchment,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = ManuscriptGold.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.35f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = getFacetIcon(facet.iconKey),
                                        contentDescription = null,
                                        tint = ManuscriptGold,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                if (facet.badge.isNotBlank()) {
                                    Text(
                                        text = facet.badge,
                                        color = ManuscriptGold,
                                        fontFamily = CairoFont,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = facet.title,
                                    color = OldInk,
                                    fontFamily = AmiriFont,
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (facet.subtitle.isNotBlank()) {
                                    Text(
                                        text = facet.subtitle,
                                        color = OldInk.copy(alpha = 0.7f),
                                        fontFamily = CairoFont,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "قراءة المحور",
                                tint = ManuscriptGold.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CharacterFacetDetailSection(
    context: Context,
    facet: CharacterFacet,
    scholarName: String,
    onBackToOverview: () -> Unit,
    onNextFacet: (() -> Unit)?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AgedPaper,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, ManuscriptGold.copy(alpha = 0.45f)),
        shadowElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header with badge and icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = ManuscriptGold.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = getFacetIcon(facet.iconKey),
                            contentDescription = null,
                            tint = ManuscriptGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (facet.badge.isNotBlank()) facet.badge else "محور السيرة",
                            color = ManuscriptGold,
                            fontFamily = CairoFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(
                    onClick = {
                        val shareText = "🌟 من سيرة $scholarName:\n\n【${facet.title}】\n\n${facet.narrative}\n\n${facet.keyQuote ?: ""}\n\n💡 الأثر والتطبيق: ${facet.practicalInsight ?: ""}\n\n— عبر تطبيق قبس"
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("قبس", shareText))
                        Toast.makeText(context, "تم نسخ هذا المحور للمشاركة 📋", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "نسخ المحور",
                        tint = ManuscriptGold.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Title
            Text(
                text = facet.title,
                color = OldInk,
                fontFamily = AmiriFont,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 32.sp
            )

            if (facet.subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = facet.subtitle,
                    color = ManuscriptGold,
                    fontFamily = CairoFont,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = ManuscriptGold.copy(alpha = 0.25f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // Narrative
            Text(
                text = facet.narrative,
                color = OldInk,
                fontFamily = AmiriFont,
                fontSize = 19.sp,
                lineHeight = 33.sp,
                textAlign = TextAlign.Justify
            )

            // Key Quote Callout (if available)
            facet.keyQuote?.let { quote ->
                Spacer(modifier = Modifier.height(18.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = AntiqueParchment,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, ManuscriptGold.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.FormatQuote,
                                contentDescription = null,
                                tint = ManuscriptGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "موقف وكلمة خالدة:",
                                color = ManuscriptGold,
                                fontFamily = CairoFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = quote,
                            color = OldInk,
                            fontFamily = AmiriFont,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 28.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Practical Insight Callout (if available)
            facet.practicalInsight?.let { insight ->
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = ManuscriptGold.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = ManuscriptGold,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "الأثر الإيماني والتطبيق المعاصر:",
                                color = ManuscriptGold,
                                fontFamily = CairoFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = insight,
                                color = OldInk,
                                fontFamily = CairoFont,
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Navigation Row: Back to Overview & Next Facet
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onBackToOverview,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold)
                ) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = ManuscriptGold, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("المخطوطة الجامعة", color = ManuscriptGold, fontFamily = CairoFont, fontSize = 12.sp)
                }

                if (onNextFacet != null) {
                    Button(
                        onClick = onNextFacet,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ManuscriptGold)
                    ) {
                        Text("المحور التالي", color = Color.White, fontFamily = CairoFont, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ManuscriptIntroSection(scholar: ScholarBiography) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AgedPaper,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.3f)),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MenuBook, contentDescription = null, tint = ManuscriptGold, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "تاريخُ الإمام ومكانته في الأمة:",
                    color = ManuscriptGold,
                    fontFamily = CairoFont,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = scholar.brief,
                color = OldInk,
                fontFamily = AmiriFont,
                fontSize = 18.sp,
                lineHeight = 30.sp,
                textAlign = TextAlign.Justify
            )

            scholar.travelDistance?.let { travel ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Explore, contentDescription = null, tint = ManuscriptGold.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "الرحلة والطلب: $travel",
                        color = OldInk.copy(alpha = 0.8f),
                        fontFamily = CairoFont,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SpatialJourneySection(scholar: ScholarBiography) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AgedPaper,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, ManuscriptGold.copy(alpha = 0.35f)),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Explore,
                        contentDescription = null,
                        tint = ManuscriptGold,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "خريطة رحلات العلم والهجرة والجهاد",
                        color = ManuscriptGold,
                        fontFamily = CairoFont,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                scholar.estimatedTravelKm?.let { km ->
                    Surface(
                        color = ManuscriptGold.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "~$km كم",
                            color = OldInk,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            scholar.travelDistance?.let { travel ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = travel,
                    color = OldInk,
                    fontFamily = NotoSansFont,
                    fontSize = 13.5.sp,
                    lineHeight = 22.sp
                )
            }

            if (scholar.journeyStops.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    scholar.journeyStops.forEachIndexed { index, stop ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                modifier = Modifier.size(28.dp),
                                shape = CircleShape,
                                color = if (index == 0 || index == scholar.journeyStops.size - 1) ManuscriptGold else AntiqueParchment,
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, ManuscriptGold)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        color = if (index == 0 || index == scholar.journeyStops.size - 1) Color.White else OldInk,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stop,
                                color = OldInk,
                                fontFamily = CairoFont,
                                fontWeight = if (index == 0 || index == scholar.journeyStops.size - 1) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }

                        if (index < scholar.journeyStops.size - 1) {
                            Box(
                                modifier = Modifier
                                    .padding(start = 13.dp)
                                    .width(2.dp)
                                    .height(16.dp)
                                    .background(ManuscriptGold.copy(alpha = 0.45f))
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = AntiqueParchment.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "«مَنْ سَلَكَ طَرِيقاً يَلْتَمِسُ فِيهِ عِلْماً، سَهَّلَ اللَّهُ لَهُ بِهِ طَرِيقاً إِلَى الجَنَّةِ»",
                    color = ManuscriptGold,
                    fontFamily = AmiriFont,
                    fontSize = 13.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

@Composable
fun HarmedAndPersecutionSection(context: Context, scholar: ScholarBiography) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AgedPaper,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFB91C1C).copy(alpha = 0.4f)),
        shadowElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Gavel,
                        contentDescription = null,
                        tint = Color(0xFFB91C1C),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "من الذين آذوه وكيف؟ (المحنة الكبرى)",
                        color = Color(0xFFB91C1C),
                        fontFamily = CairoFont,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = {
                        scholar.whoHarmedHimAndHow?.let { copyToClipboard(context, "محنة ${scholar.name}", it) }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = Color(0xFFB91C1C).copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = scholar.whoHarmedHimAndHow ?: "واجه ابتلاءات شديدة في سبيل الله وصبر عليها.",
                color = OldInk,
                fontFamily = AmiriFont,
                fontSize = 17.5.sp,
                lineHeight = 28.sp,
                textAlign = TextAlign.Justify
            )
        }
    }
}

@Composable
fun EnduranceAndPurposeSection(context: Context, scholar: ScholarBiography) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // How he endured
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AgedPaper,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF047857).copy(alpha = 0.4f)),
            shadowElevation = 3.dp
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFF047857),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "كيف تحمّل؟ وسر الصمود الإيماني",
                            color = Color(0xFF047857),
                            fontFamily = CairoFont,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = {
                            scholar.howHeEndured?.let { copyToClipboard(context, "صبر ${scholar.name}", it) }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = Color(0xFF047857).copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = scholar.howHeEndured ?: "استعان بالله تعالى واحتسب الأجر في كل لحظة.",
                    color = OldInk,
                    fontFamily = AmiriFont,
                    fontSize = 17.5.sp,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Justify
                )
            }
        }

        // Why he endured
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AgedPaper,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, ManuscriptGold.copy(alpha = 0.4f)),
            shadowElevation = 3.dp
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Flag,
                            contentDescription = null,
                            tint = ManuscriptGold,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "لماذا تحمّل؟ القضية الكبرى والغاية",
                            color = ManuscriptGold,
                            fontFamily = CairoFont,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = {
                            scholar.whyHeEndured?.let { copyToClipboard(context, "ثبات ${scholar.name}", it) }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = ManuscriptGold.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = scholar.whyHeEndured ?: "لحفظ دين الله وصيانة شريعة الإسلام للأجيال.",
                    color = OldInk,
                    fontFamily = AmiriFont,
                    fontSize = 17.5.sp,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Justify
                )
            }
        }
    }
}

@Composable
fun DeathSceneAndFarewellSection(context: Context, scholar: ScholarBiography) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // The Death Scene
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AgedPaper,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF6B21A8).copy(alpha = 0.4f)),
            shadowElevation = 3.dp
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.HourglassBottom,
                        contentDescription = null,
                        tint = Color(0xFF6B21A8),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "مشهد الوفاة واللحظات الأخيرة المؤثرة:",
                        color = Color(0xFF6B21A8),
                        fontFamily = CairoFont,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = scholar.deathScene ?: "فاضت روحه في طاعة الله وذكره.",
                    color = OldInk,
                    fontFamily = AmiriFont,
                    fontSize = 18.sp,
                    lineHeight = 30.sp,
                    textAlign = TextAlign.Justify
                )
            }
        }

        // Highlight Box: Last Words
        scholar.lastWords?.let { lastWords ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.05f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, ManuscriptGold)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.FormatQuote,
                        contentDescription = null,
                        tint = ManuscriptGold,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "آخر كلماته قبل خروج الروح:",
                        color = ManuscriptGold,
                        fontFamily = CairoFont,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "« $lastWords »",
                        color = OldInk,
                        fontFamily = AmiriFont,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 32.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Funeral and Aftermath
        scholar.funeralImpact?.let { funeral ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = AgedPaper,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Groups, contentDescription = null, tint = ManuscriptGold, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "مشهد الجنازة وأثر الفقد على الأمة:",
                            color = ManuscriptGold,
                            fontFamily = CairoFont,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = funeral,
                        color = OldInk,
                        fontFamily = AmiriFont,
                        fontSize = 17.5.sp,
                        lineHeight = 28.sp,
                        textAlign = TextAlign.Justify
                    )
                }
            }
        }
    }
}

@Composable
fun LegacyAndMessageSection(
    context: Context,
    scholar: ScholarBiography,
    isChallengeCompleted: Boolean = false,
    onToggleChallenge: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Global Impact
        scholar.globalImpact?.let { impact ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = AgedPaper,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Public, contentDescription = null, tint = ManuscriptGold, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "الأثر التاريخي الخالد:",
                            color = ManuscriptGold,
                            fontFamily = CairoFont,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = impact,
                        color = OldInk,
                        fontFamily = NotoSansFont,
                        fontSize = 14.5.sp,
                        lineHeight = 24.sp
                    )
                }
            }
        }

        // Message to Grandson
        scholar.messageToGrandson?.let { message ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = ManuscriptGold.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, ManuscriptGold.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.HistoryEdu,
                        contentDescription = null,
                        tint = ManuscriptGold,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "وصيةُ الإمامِ لكَ يا حفيدَهُ:",
                        color = ManuscriptGold,
                        fontFamily = CairoFont,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "« $message »",
                        color = OldInk,
                        fontFamily = AmiriFont,
                        fontSize = 20.sp,
                        lineHeight = 32.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Interactive Modern Daily Challenge
        scholar.modernChallenge?.let { challenge ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (isChallengeCompleted) Color(0xFF10B981).copy(alpha = 0.12f) else Color.White.copy(alpha = 0.75f),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, if (isChallengeCompleted) Color(0xFF10B981) else ManuscriptGold.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = if (isChallengeCompleted) Color(0xFF10B981) else ManuscriptGold,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "تحدي الاقتداء المعاصر اليوم:",
                                color = if (isChallengeCompleted) Color(0xFF047857) else ManuscriptGold,
                                fontFamily = CairoFont,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (isChallengeCompleted) {
                            Surface(
                                color = Color(0xFF10B981),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "مُنجز بحمد الله ✨",
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = challenge,
                        color = OldInk,
                        fontFamily = NotoSansFont,
                        fontSize = 13.5.sp,
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onToggleChallenge,
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isChallengeCompleted) Color(0xFF10B981) else ManuscriptGold
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isChallengeCompleted) Icons.Default.CheckCircle else Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isChallengeCompleted) "أتممتُ الاقتداء اليوم • انقر للإلغاء" else "عزمتُ على تطبيقه اليوم ✅",
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    }
                }
            }
        }
    }
}
