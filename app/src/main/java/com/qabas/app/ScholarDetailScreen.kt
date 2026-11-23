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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qabas.app.ui.components.*
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

    val theme = QabasTheme.current
    val goldAccent = PresetPrimary

    Scaffold(
        containerColor = theme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            AppleTopBar(
                title = enrichedScholar.name,
                subtitle = enrichedScholar.era,
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = {
                            isBookmarked = prefs.toggleFavorite(scholar.id)
                            Toast.makeText(
                                context,
                                if (isBookmarked) "تمت الإضافة للمفضلة ⭐" else "تمت الإزالة من المفضلة",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "المفضلة",
                            tint = if (isBookmarked) goldAccent else theme.textSecondary
                        )
                    }
                    IconButton(
                        onClick = { showLecturesSheet = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        BadgedBox(
                            badge = {
                                if (lecturesCount > 0) {
                                    Badge(containerColor = goldAccent, contentColor = DeepSlate) {
                                        Text("$lecturesCount", fontSize = 10.sp)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = "الدروس والمحاضرات والمراجع",
                                tint = goldAccent
                            )
                        }
                    }
                    IconButton(
                        onClick = { showShareModal = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "مشاركة السيرة كاملة",
                            tint = theme.primary
                        )
                    }
                }
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
                containerColor = theme.cardSurface,
                contentColor = goldAccent,
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
                                color = if (selectedTab == index) goldAccent else theme.textSecondary
                            )
                        }
                    )
                }
            }

            HorizontalDivider(
                color = theme.separator.copy(alpha = 0.45f),
                thickness = 0.5.dp
            )

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Portrait & Hero Card
                    AppleCard(cornerRadius = 20.dp) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                modifier = Modifier.size(132.dp),
                                shape = CircleShape,
                                border = androidx.compose.foundation.BorderStroke(2.5.dp, goldAccent.copy(alpha = 0.75f)),
                                shadowElevation = if (theme.isDark) 0.dp else 4.dp
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
                                color = theme.textPrimary,
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
                                        color = goldAccent.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = era,
                                            color = goldAccent,
                                            fontFamily = CairoFont,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                enrichedScholar.period?.let {
                                    Text(
                                        text = it,
                                        color = theme.textSecondary,
                                        fontFamily = CairoFont,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            enrichedScholar.majorHistoricalEvent?.let { event ->
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    color = theme.secondarySurface,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Stars, contentDescription = null, tint = goldAccent, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "الحدث الأبرز: $event",
                                            color = theme.textPrimary,
                                            fontFamily = CairoFont,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Endurance Quote Pill
                            enrichedScholar.enduranceQuote?.let { quote ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = goldAccent.copy(alpha = if (theme.isDark) 0.12f else 0.08f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, goldAccent.copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        text = "« $quote »",
                                        color = theme.textPrimary,
                                        fontFamily = AmiriFont,
                                        fontSize = 17.sp,
                                        lineHeight = 27.sp,
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
                    ApplePrimaryButton(
                        text = "مشاركة السيرة كاملة (صورة أو نص)",
                        icon = Icons.Default.Share,
                        onClick = { showShareModal = true }
                    )

                    Spacer(modifier = Modifier.height(40.dp))
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
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "حدث خطأ أثناء إعداد الصورة", Toast.LENGTH_SHORT).show()
        } finally {
            isGeneratingImage = false
        }
    }

    val fullText = remember(scholar) {
        ScholarCardImageGenerator.buildFullBiographyText(scholar)
    }

    val theme = QabasTheme.current
    val goldAccent = PresetPrimary

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = theme.cardSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = theme.separator) }
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
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = theme.textPrimary)
                }

                Text(
                    text = "مشاركة سيرة الإمام ${scholar.name}",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = theme.textPrimary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )

                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AutoStories,
                        contentDescription = null,
                        tint = goldAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tab Selector: صورة عالية الدقة / نص شامل
            AppleSegmentedControl(
                items = listOf(0 to "بطاقة مصورة", 1 to "نص السيرة الشامل"),
                selectedItem = selectedFormat,
                onSelect = { selectedFormat = it }
            )

            Spacer(modifier = Modifier.height(14.dp))

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
                            label = { Text("المخطوطة الملحمية", fontFamily = CairoFont, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = theme.secondarySurface,
                                labelColor = theme.textPrimary,
                                selectedContainerColor = goldAccent,
                                selectedLabelColor = DeepSlate
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedStyle == ScholarCardImageGenerator.CardStyle.HIGHLIGHT_STORY,
                            onClick = { selectedStyle = ScholarCardImageGenerator.CardStyle.HIGHLIGHT_STORY },
                            label = { Text("بطاقة القصة (9:16)", fontFamily = CairoFont, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = theme.secondarySurface,
                                labelColor = theme.textPrimary,
                                selectedContainerColor = goldAccent,
                                selectedLabelColor = DeepSlate
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Theme Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedTheme == ScholarCardImageGenerator.CardTheme.CLASSIC_MANUSCRIPT,
                            onClick = { selectedTheme = ScholarCardImageGenerator.CardTheme.CLASSIC_MANUSCRIPT },
                            label = { Text("📜 مخطوطة ذهب", fontFamily = CairoFont, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = theme.secondarySurface,
                                labelColor = theme.textPrimary,
                                selectedContainerColor = goldAccent,
                                selectedLabelColor = DeepSlate
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedTheme == ScholarCardImageGenerator.CardTheme.EMERALD_GOLD,
                            onClick = { selectedTheme = ScholarCardImageGenerator.CardTheme.EMERALD_GOLD },
                            label = { Text("🌿 ديباج زمردي", fontFamily = CairoFont, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = theme.secondarySurface,
                                labelColor = theme.textPrimary,
                                selectedContainerColor = theme.success,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedTheme == ScholarCardImageGenerator.CardTheme.OBSIDIAN_LUXURY,
                            onClick = { selectedTheme = ScholarCardImageGenerator.CardTheme.OBSIDIAN_LUXURY },
                            label = { Text("🖤 رخام ملكي", fontFamily = CairoFont, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = theme.secondarySurface,
                                labelColor = theme.textPrimary,
                                selectedContainerColor = theme.primary,
                                selectedLabelColor = theme.onPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live Image Preview Card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp),
                        color = theme.secondarySurface.copy(alpha = if (theme.isDark) 0.7f else 0.55f),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, theme.separator)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (isGeneratingImage) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = theme.primary)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "جاري رسم وتجهيز بطاقة السيرة بجودة عالية...",
                                        fontFamily = CairoFont,
                                        fontSize = 12.sp,
                                        color = theme.textSecondary
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
                        ApplePrimaryButton(
                            text = "مشاركة الصورة",
                            icon = Icons.Default.Share,
                            enabled = !isGeneratingImage && renderedBitmap != null,
                            onClick = {
                                scope.launch {
                                    val bmp = renderedBitmap ?: return@launch
                                    val uri = ScholarCardImageGenerator.saveBitmapToCache(context, bmp, scholar.name)
                                    ScholarCardImageGenerator.shareImageUri(context, uri, scholar)
                                }
                            },
                            modifier = Modifier.weight(1.25f)
                        )

                        AppleSecondaryButton(
                            text = if (isSavingToGallery) "جاري الحفظ.." else "حفظ بالمعرض",
                            icon = Icons.Default.Download,
                            tint = goldAccent,
                            enabled = !isGeneratingImage && renderedBitmap != null && !isSavingToGallery,
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
                            modifier = Modifier.weight(1f)
                        )
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
                            .height(250.dp),
                        color = theme.secondarySurface.copy(alpha = if (theme.isDark) 0.7f else 0.55f),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, theme.separator)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = fullText,
                                color = theme.textPrimary,
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
                        ApplePrimaryButton(
                            text = "مشاركة النص كاملاً",
                            icon = Icons.Default.Share,
                            onClick = { ScholarCardImageGenerator.shareText(context, scholar) },
                            modifier = Modifier.weight(1.25f)
                        )

                        AppleSecondaryButton(
                            text = "نسخ النص",
                            icon = Icons.Default.ContentCopy,
                            tint = goldAccent,
                            onClick = { copyToClipboard(context, "سيرة الإمام ${scholar.name}", fullText) },
                            modifier = Modifier.weight(1f)
                        )
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

    val theme = QabasTheme.current
    val goldAccent = PresetPrimary

    AppleCard(cornerRadius = 16.dp) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = goldAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "الدروس المعتمدة والمقترحات",
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = goldAccent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${officialLectures.size} دروس معتمدة",
                        color = goldAccent,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "دروس موثقة ومعتمدة من المنصة للدخول والمشاهدة، مع إمكانية اقتراح عناوين ومحاور علمية جديدة لطلاب العلم.",
                color = theme.textSecondary,
                fontFamily = NotoSansFont,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "🌟 الدروس المعتمدة للمشاهدة المباشرة:",
                fontFamily = CairoFont,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = theme.textPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                officialLectures.take(3).forEach { lecture ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = theme.secondarySurface.copy(alpha = if (theme.isDark) 0.75f else 0.55f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = goldAccent.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = lecture.category,
                                        fontFamily = CairoFont,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = goldAccent,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }

                                if (lecture.speaker.isNotBlank()) {
                                    Text(
                                        text = "🎙️ ${lecture.speaker}",
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp,
                                        color = theme.textSecondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = lecture.title,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = theme.textPrimary
                            )

                            if (lecture.synopsis.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = lecture.synopsis,
                                    fontFamily = NotoSansFont,
                                    fontSize = 12.sp,
                                    color = theme.textSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 18.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { openLectureUrl(context, lecture.url) },
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .heightIn(min = 48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = theme.destructive),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(17.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("مشاهدة واستماع", fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }

                                Button(
                                    onClick = { shareLecture(context, lecture) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "مشاركة", modifier = Modifier.size(15.dp), tint = theme.onPrimary)
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("مشاركة", fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.onPrimary)
                                }
                            }
                        }
                    }
                }
            }

            if (userProposals.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💡 مقترحات المستمعين (${userProposals.size}):",
                        fontFamily = CairoFont,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary
                    )
                    Text(
                        text = "أعلى تأييد: ${userProposals.first().upvotesCount} 👍",
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        color = goldAccent,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = theme.secondarySurface.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "« ${userProposals.first().suggestedTitle} » — ${userProposals.first().suggestedSpeaker}",
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        color = theme.textPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAddClick,
                    modifier = Modifier
                        .weight(1.1f)
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.LibraryBooks, contentDescription = null, tint = theme.onPrimary, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "المكتبة والمقترحات",
                        color = theme.onPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                OutlinedButton(
                    onClick = onAddClick,
                    modifier = Modifier
                        .weight(0.9f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.primary)
                ) {
                    Icon(Icons.Default.EditNote, contentDescription = null, tint = theme.primary, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "+ اقترح عنواناً",
                        color = theme.primary,
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
    val theme = QabasTheme.current
    val goldAccent = PresetPrimary

    AppleCard(cornerRadius = 16.dp) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = goldAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "محاور العبقرية والسيرة الخالدة:",
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = goldAccent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "${facets.size} محاور متفردة",
                        color = goldAccent,
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "كُتبت هذه المحاور خصيصاً وفق فرادة العَلَم ومجالات أثره وعبقريته في أمة الإسلام:",
                color = theme.textSecondary,
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
                            .defaultMinSize(minHeight = 56.dp)
                            .clickable { onSelectFacet(index) },
                        color = theme.secondarySurface.copy(alpha = if (theme.isDark) 0.75f else 0.55f),
                        shape = RoundedCornerShape(12.dp)
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
                                color = goldAccent.copy(alpha = 0.14f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = getFacetIcon(facet.iconKey),
                                        contentDescription = null,
                                        tint = goldAccent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                if (facet.badge.isNotBlank()) {
                                    Text(
                                        text = facet.badge,
                                        color = goldAccent,
                                        fontFamily = CairoFont,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = facet.title,
                                    color = theme.textPrimary,
                                    fontFamily = AmiriFont,
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (facet.subtitle.isNotBlank()) {
                                    Text(
                                        text = facet.subtitle,
                                        color = theme.textSecondary,
                                        fontFamily = CairoFont,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Icon(
                                Icons.Default.ChevronLeft,
                                contentDescription = "قراءة المحور",
                                tint = theme.textSecondary,
                                modifier = Modifier.size(20.dp)
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
    val theme = QabasTheme.current
    val goldAccent = PresetPrimary

    AppleCard(cornerRadius = 18.dp) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header with badge and icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = goldAccent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = getFacetIcon(facet.iconKey),
                            contentDescription = null,
                            tint = goldAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (facet.badge.isNotBlank()) facet.badge else "محور السيرة",
                            color = goldAccent,
                            fontFamily = CairoFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(
                    onClick = {
                        val shareText = "🌟 من سيرة $scholarName:\n\n【${facet.title}】\n\n${facet.narrative}\n\n${facet.keyQuote ?: ""}\n\n💡 الأثر والتطبيق: ${facet.practicalInsight ?: ""}\n\n— عبر تطبيق خادم المسلم"
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("خادم المسلم", shareText))
                        Toast.makeText(context, "تم نسخ هذا المحور للمشاركة 📋", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "نسخ المحور",
                        tint = theme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Title
            Text(
                text = facet.title,
                color = theme.textPrimary,
                fontFamily = AmiriFont,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 32.sp
            )

            if (facet.subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = facet.subtitle,
                    color = goldAccent,
                    fontFamily = CairoFont,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = theme.separator.copy(alpha = 0.45f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(14.dp))

            // Narrative
            Text(
                text = facet.narrative,
                color = theme.textPrimary,
                fontFamily = AmiriFont,
                fontSize = 19.sp,
                lineHeight = 33.sp,
                textAlign = TextAlign.Justify
            )

            // Key Quote Callout
            facet.keyQuote?.let { quote ->
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = theme.secondarySurface.copy(alpha = if (theme.isDark) 0.8f else 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, goldAccent.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.FormatQuote,
                                contentDescription = null,
                                tint = goldAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "موقف وكلمة خالدة:",
                                color = goldAccent,
                                fontFamily = CairoFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = quote,
                            color = theme.textPrimary,
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

            // Practical Insight Callout
            facet.practicalInsight?.let { insight ->
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = goldAccent.copy(alpha = if (theme.isDark) 0.12f else 0.08f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, goldAccent.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = goldAccent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "الأثر الإيماني والتطبيق المعاصر:",
                                color = goldAccent,
                                fontFamily = CairoFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = insight,
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Navigation Row: Back to Overview & Next Facet
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onBackToOverview,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.primary)
                ) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = theme.primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("المخطوطة الجامعة", color = theme.primary, fontFamily = CairoFont, fontSize = 12.sp)
                }

                if (onNextFacet != null) {
                    Button(
                        onClick = onNextFacet,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
                    ) {
                        Text("المحور التالي", color = theme.onPrimary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = theme.onPrimary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ManuscriptIntroSection(scholar: ScholarBiography) {
    val theme = QabasTheme.current
    val goldAccent = PresetPrimary

    AppleCard(cornerRadius = 16.dp) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MenuBook, contentDescription = null, tint = goldAccent, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "تاريخُ الإمام ومكانته في الأمة:",
                    color = goldAccent,
                    fontFamily = CairoFont,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = scholar.brief,
                color = theme.textPrimary,
                fontFamily = AmiriFont,
                fontSize = 18.sp,
                lineHeight = 30.sp,
                textAlign = TextAlign.Justify
            )

            scholar.travelDistance?.let { travel ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Explore, contentDescription = null, tint = theme.primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "الرحلة والطلب: $travel",
                        color = theme.textSecondary,
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
    val theme = QabasTheme.current
    val goldAccent = PresetPrimary

    AppleCard(cornerRadius = 16.dp) {
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
                        tint = goldAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "محطات السيرة والتنقل",
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                scholar.estimatedTravelKm?.let { km ->
                    Surface(
                        color = goldAccent.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "~$km كم",
                            color = goldAccent,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            scholar.travelDistance?.let { travel ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = travel,
                    color = theme.textPrimary,
                    fontFamily = NotoSansFont,
                    fontSize = 13.5.sp,
                    lineHeight = 22.sp
                )
            }

            if (scholar.journeyStops.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    scholar.journeyStops.forEachIndexed { index, stop ->
                        val isEndpoint = index == 0 || index == scholar.journeyStops.size - 1
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                modifier = Modifier.size(28.dp),
                                shape = CircleShape,
                                color = if (isEndpoint) goldAccent else theme.secondarySurface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, goldAccent.copy(alpha = 0.7f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        color = if (isEndpoint) DeepSlate else theme.textPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stop,
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = if (isEndpoint) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }

                        if (index < scholar.journeyStops.size - 1) {
                            Box(
                                modifier = Modifier
                                    .padding(start = 13.dp)
                                    .width(2.dp)
                                    .height(16.dp)
                                    .background(goldAccent.copy(alpha = 0.45f))
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = theme.secondarySurface.copy(alpha = 0.65f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "«مَنْ سَلَكَ طَرِيقاً يَلْتَمِسُ فِيهِ عِلْماً، سَهَّلَ اللَّهُ لَهُ بِهِ طَرِيقاً إِلَى الجَنَّةِ»",
                    color = goldAccent,
                    fontFamily = AmiriFont,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

@Composable
fun HarmedAndPersecutionSection(context: Context, scholar: ScholarBiography) {
    val theme = QabasTheme.current
    AppleCard(cornerRadius = 16.dp) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Default.Gavel,
                        contentDescription = null,
                        tint = theme.destructive,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "من الذين آذوه وكيف؟ (المحنة الكبرى)",
                        color = theme.destructive,
                        fontFamily = CairoFont,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = {
                        scholar.whoHarmedHimAndHow?.let { copyToClipboard(context, "محنة ${scholar.name}", it) }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = theme.destructive.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = scholar.whoHarmedHimAndHow ?: "لا تتوافر في المصادر التي اعتمدناها تفاصيل كافية لإثبات محنة خاصة أو تعيين من آذاه.",
                color = theme.textPrimary,
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
    val theme = QabasTheme.current
    val goldAccent = PresetPrimary

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // How he endured
        AppleCard(cornerRadius = 16.dp) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = theme.success,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "كيف تحمّل؟ وسر الصمود الإيماني",
                            color = theme.success,
                            fontFamily = CairoFont,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = {
                            scholar.howHeEndured?.let { copyToClipboard(context, "صبر ${scholar.name}", it) }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = theme.success.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = scholar.howHeEndured ?: "لا تتوافر رواية موثقة كافية لوصف تفاصيل صبره في هذه المحنة.",
                    color = theme.textPrimary,
                    fontFamily = AmiriFont,
                    fontSize = 17.5.sp,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Justify
                )
            }
        }

        // Why he endured
        AppleCard(cornerRadius = 16.dp) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Default.Flag,
                            contentDescription = null,
                            tint = goldAccent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "لماذا تحمّل؟ القضية الكبرى والغاية",
                            color = goldAccent,
                            fontFamily = CairoFont,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = {
                            scholar.whyHeEndured?.let { copyToClipboard(context, "ثبات ${scholar.name}", it) }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = goldAccent.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = scholar.whyHeEndured ?: "هذه فقرة تفسيرية تحريرية، وليست قولاً منسوباً إلى صاحب الترجمة.",
                    color = theme.textPrimary,
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
    val theme = QabasTheme.current
    val goldAccent = PresetPrimary
    val indigoAccent = PresetAccent

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // The Death Scene
        AppleCard(cornerRadius = 16.dp) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.HourglassBottom,
                        contentDescription = null,
                        tint = indigoAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "الوفاة في المصادر التاريخية:",
                        color = indigoAccent,
                        fontFamily = CairoFont,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = scholar.deathScene ?: "لا نورد وصفاً تفصيلياً للحظاته الأخيرة أو آخر كلماته دون نقل تاريخي واضح يمكن مراجعته.",
                    color = theme.textPrimary,
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
                color = goldAccent.copy(alpha = if (theme.isDark) 0.14f else 0.08f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, goldAccent.copy(alpha = 0.55f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.FormatQuote,
                        contentDescription = null,
                        tint = goldAccent,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "آخر كلماته قبل خروج الروح:",
                        color = goldAccent,
                        fontFamily = CairoFont,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "« $lastWords »",
                        color = theme.textPrimary,
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
            AppleCard(cornerRadius = 16.dp) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Groups, contentDescription = null, tint = goldAccent, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "مشهد الجنازة وأثر الفقد على الأمة:",
                            color = goldAccent,
                            fontFamily = CairoFont,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = funeral,
                        color = theme.textPrimary,
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
    val theme = QabasTheme.current
    val goldAccent = PresetPrimary

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Global Impact
        scholar.globalImpact?.let { impact ->
            AppleCard(cornerRadius = 16.dp) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Public, contentDescription = null, tint = goldAccent, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "الأثر التاريخي الخالد:",
                            color = goldAccent,
                            fontFamily = CairoFont,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = impact,
                        color = theme.textPrimary,
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
                color = goldAccent.copy(alpha = if (theme.isDark) 0.12f else 0.08f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(0.8.dp, goldAccent.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.HistoryEdu,
                        contentDescription = null,
                        tint = goldAccent,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "عبرة مستفادة من سيرته:",
                        color = goldAccent,
                        fontFamily = CairoFont,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        color = theme.textPrimary,
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
                color = if (isChallengeCompleted) theme.success.copy(alpha = if (theme.isDark) 0.16f else 0.10f) else theme.cardSurface,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    0.8.dp,
                    if (isChallengeCompleted) theme.success else theme.separator.copy(alpha = 0.55f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = if (isChallengeCompleted) theme.success else goldAccent,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "تحدي الاقتداء المعاصر اليوم:",
                                color = if (isChallengeCompleted) theme.success else goldAccent,
                                fontFamily = CairoFont,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (isChallengeCompleted) {
                            Surface(
                                color = theme.success,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "مُنجز بحمد الله ✨",
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = challenge,
                        color = theme.textPrimary,
                        fontFamily = NotoSansFont,
                        fontSize = 13.5.sp,
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onToggleChallenge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isChallengeCompleted) theme.success else theme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
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
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
