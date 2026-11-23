package com.qabas.app

import android.app.Application
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.qabas.app.ui.components.*
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar

class ScholarBiographiesViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = ScholarPreferences(application)

    private val _scholars = MutableStateFlow<List<ScholarBiography>>(emptyList())
    val scholars: StateFlow<List<ScholarBiography>> = _scholars

    private val _selectedCategory = MutableStateFlow("الكل")
    val selectedCategory: StateFlow<String> = _selectedCategory

    private val _selectedEra = MutableStateFlow(ScholarEnrichmentData.ERA_ALL)
    val selectedEra: StateFlow<String> = _selectedEra

    private val _selectedTrialType = MutableStateFlow(ScholarEnrichmentData.TRIAL_ALL)
    val selectedTrialType: StateFlow<String> = _selectedTrialType

    private val _onlyFavorites = MutableStateFlow(false)
    val onlyFavorites: StateFlow<Boolean> = _onlyFavorites

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _favorites = MutableStateFlow<Set<String>>(prefs.getFavorites())
    val favorites: StateFlow<Set<String>> = _favorites

    private val _completedChallenges = MutableStateFlow<Set<String>>(prefs.getCompletedChallenges())
    val completedChallenges: StateFlow<Set<String>> = _completedChallenges

    private val _dailyScholar = MutableStateFlow<ScholarBiography?>(null)
    val dailyScholar: StateFlow<ScholarBiography?> = _dailyScholar

    private val _lecturesVersion = MutableStateFlow(0)
    val lecturesVersion: StateFlow<Int> = _lecturesVersion

    init {
        loadScholars()
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setEra(era: String) {
        _selectedEra.value = era
    }

    fun setTrialType(trial: String) {
        _selectedTrialType.value = trial
    }

    fun setOnlyFavorites(only: Boolean) {
        _onlyFavorites.value = only
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavorite(scholarId: String) {
        prefs.toggleFavorite(scholarId)
        _favorites.value = prefs.getFavorites()
    }

    fun toggleChallenge(scholarId: String) {
        prefs.toggleChallengeCompleted(scholarId)
        _completedChallenges.value = prefs.getCompletedChallenges()
    }

    private fun loadScholars() {
        val raw = SahabaData.sahabaList + ScholarsData.scholarsList
        val enriched = raw.map { ScholarEnrichmentData.enrich(it) }
        _scholars.value = enriched

        if (enriched.isNotEmpty()) {
            val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
            _dailyScholar.value = enriched[dayOfYear % enriched.size]
        }
    }

    fun getLectureCount(scholarId: String): Int {
        return prefs.getLectureCountForScholar(scholarId)
    }

    fun notifyLecturesChanged() {
        _lecturesVersion.value += 1
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScholarBiographiesScreen(
    onBack: () -> Unit,
    onScholarClick: (ScholarBiography) -> Unit,
    viewModel: ScholarBiographiesViewModel = viewModel()
) {
    val allScholars by viewModel.scholars.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedEra by viewModel.selectedEra.collectAsState()
    val selectedTrialType by viewModel.selectedTrialType.collectAsState()
    val onlyFavorites by viewModel.onlyFavorites.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val completedChallenges by viewModel.completedChallenges.collectAsState()
    val dailyScholar by viewModel.dailyScholar.collectAsState()
    var activeScholarForLectures by remember { mutableStateOf<ScholarBiography?>(null) }
    val lecturesVersion by viewModel.lecturesVersion.collectAsState()

    val categories = listOf(
        "الكل",
        "الصحابة الكرام",
        "أئمة المحنة والشهادة",
        "القادة والمجاهدون",
        "أئمة الفقه والحديث",
        "أطباء القلوب والزهد"
    )

    val filteredScholars = remember(
        allScholars, selectedCategory, selectedEra, selectedTrialType, onlyFavorites, favorites, searchQuery
    ) {
        allScholars.filter { scholar ->
            val matchesCategory = if (selectedCategory == "الكل") true else scholar.tags.contains(selectedCategory)
            val matchesEra = if (selectedEra == ScholarEnrichmentData.ERA_ALL) true else scholar.era == selectedEra
            val matchesFavorite = if (!onlyFavorites) true else favorites.contains(scholar.id)
            val matchesTrial = when (selectedTrialType) {
                ScholarEnrichmentData.TRIAL_ALL -> true
                ScholarEnrichmentData.TRIAL_PRISON ->
                    scholar.whoHarmedHimAndHow?.let { it.contains("سجن") || it.contains("جلد") || it.contains("سياط") || it.contains("حديد") } == true
                ScholarEnrichmentData.TRIAL_MARTYRDOM ->
                    scholar.tags.contains("القادة والمجاهدون") || scholar.tags.contains("أئمة المحنة والشهادة") || (scholar.deathScene?.contains("شهيد") == true)
                ScholarEnrichmentData.TRIAL_POVERTY ->
                    scholar.tags.contains("أطباء القلوب والزهد") || scholar.whoHarmedHimAndHow?.contains("جوع") == true || scholar.sacrificeSummary?.contains("جوع") == true || scholar.sacrificeSummary?.contains("فقر") == true
                ScholarEnrichmentData.TRIAL_RULERS ->
                    scholar.enduranceQuote != null || scholar.whyHeEndured != null
                ScholarEnrichmentData.TRIAL_EXILE ->
                    scholar.travelDistance?.let { it.contains("هاجر") || it.contains("الربذة") || it.contains("نفي") || it.contains("غربة") } == true
                else -> true
            }
            val matchesSearch = if (searchQuery.isBlank()) true else {
                scholar.name.contains(searchQuery, ignoreCase = true) ||
                scholar.brief.contains(searchQuery, ignoreCase = true) ||
                (scholar.era?.contains(searchQuery, ignoreCase = true) == true) ||
                (scholar.majorHistoricalEvent?.contains(searchQuery, ignoreCase = true) == true) ||
                (scholar.whoHarmedHimAndHow?.contains(searchQuery, ignoreCase = true) == true) ||
                (scholar.deathScene?.contains(searchQuery, ignoreCase = true) == true)
            }
            matchesCategory && matchesEra && matchesFavorite && matchesTrial && matchesSearch
        }
    }

    val theme = QabasTheme.current
    var sharingScholar by remember { mutableStateOf<ScholarBiography?>(null) }

    Scaffold(
        containerColor = theme.background,
        topBar = {
            AppleTopBar(
                title = "سِيَرُ الأَكَابِر والصالحين",
                subtitle = "سير تاريخية مع التمييز بين الثابت والمختلف فيه",
                onBack = onBack
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
                contentPadding = PaddingValues(bottom = 40.dp, start = 16.dp, end = 16.dp, top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Hero Banner & Daily Scholar Card
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AppleCard {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(PresetPrimary.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.MenuBook,
                                            contentDescription = null,
                                            tint = PresetPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "«رجالٌ صدقوا ما عاهدوا الله عليه»",
                                        color = theme.textPrimary,
                                        fontFamily = AmiriFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "نعرض أبرز الوقائع الثابتة في حياة الصحابة والعلماء، ونحذف التفاصيل التي لا يثبت نقلها، ونشير إلى الروايات المختلف فيها دون الجزم بها.",
                                    color = theme.textSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 12.5.sp,
                                    lineHeight = 19.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        dailyScholar?.let { daily ->
                            Spacer(modifier = Modifier.height(14.dp))
                            DailyFeaturedScholarCard(
                                scholar = daily,
                                isChallengeCompleted = completedChallenges.contains(daily.id),
                                completedCount = completedChallenges.size,
                                onToggleChallenge = { viewModel.toggleChallenge(daily.id) },
                                onReadClick = { onScholarClick(daily) }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        AppleSearchBar(
                            query = searchQuery,
                            onQueryChange = { viewModel.setSearchQuery(it) },
                            placeholder = "ابحث عن إمام، صحابي، عصر، أو محنة..."
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Timeline, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "الخط الزمني للعصور:",
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(ScholarEnrichmentData.ERAS_LIST) { era ->
                                val isSelected = era == selectedEra
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setEra(era) },
                                    label = {
                                        Text(
                                            era,
                                            fontFamily = CairoFont,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = theme.primary,
                                        selectedLabelColor = theme.onPrimary,
                                        containerColor = theme.cardSurface,
                                        labelColor = theme.textPrimary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = if (isSelected) theme.primary else theme.separator,
                                        enabled = true,
                                        selected = isSelected
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FilterAlt, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "المحن والتصنيفات والمفضلة:",
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                FilterChip(
                                    selected = onlyFavorites,
                                    onClick = { viewModel.setOnlyFavorites(!onlyFavorites) },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (onlyFavorites) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "المفضلة (${favorites.size})",
                                                fontFamily = CairoFont,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PresetPrimary,
                                        selectedLabelColor = Color.White,
                                        containerColor = theme.cardSurface,
                                        labelColor = theme.textPrimary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = if (onlyFavorites) PresetPrimary else theme.separator,
                                        enabled = true,
                                        selected = onlyFavorites
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                                )
                            }

                            items(ScholarEnrichmentData.TRIAL_TYPES) { trial ->
                                val isSelected = trial == selectedTrialType && !onlyFavorites
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.setOnlyFavorites(false)
                                        viewModel.setTrialType(trial)
                                    },
                                    label = {
                                        Text(
                                            trial,
                                            fontFamily = CairoFont,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = theme.primary,
                                        selectedLabelColor = theme.onPrimary,
                                        containerColor = theme.cardSurface,
                                        labelColor = theme.textPrimary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = if (isSelected) theme.primary else theme.separator,
                                        enabled = true,
                                        selected = isSelected
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "عرض ${filteredScholars.size} من أئمة الهدى والصحابة الكرام",
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 12.sp
                            )
                            if (onlyFavorites || selectedEra != ScholarEnrichmentData.ERA_ALL || selectedTrialType != ScholarEnrichmentData.TRIAL_ALL) {
                                TextButton(
                                    onClick = {
                                        viewModel.setOnlyFavorites(false)
                                        viewModel.setEra(ScholarEnrichmentData.ERA_ALL)
                                        viewModel.setTrialType(ScholarEnrichmentData.TRIAL_ALL)
                                        viewModel.setCategory("الكل")
                                    },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Text(
                                        "إعادة ضبط الفلاتر",
                                        color = theme.primary,
                                        fontFamily = CairoFont,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Scholars Cards
                items(filteredScholars, key = { it.id }) { scholar ->
                    ScholarBiographyCard(
                        scholar = scholar,
                        isBookmarked = favorites.contains(scholar.id),
                        onBookmarkToggle = { viewModel.toggleFavorite(scholar.id) },
                        lectureCount = remember(scholar.id, lecturesVersion) { viewModel.getLectureCount(scholar.id) },
                        onLecturesClick = { activeScholarForLectures = scholar },
                        onShareClick = { sharingScholar = scholar },
                        onClick = { onScholarClick(scholar) }
                    )
                }
            }
        }
    }

    activeScholarForLectures?.let { scholar ->
        ScholarLecturesSheet(
            scholar = scholar,
            onDismiss = { activeScholarForLectures = null },
            onLecturesCountChanged = { viewModel.notifyLecturesChanged() }
        )
    }

    sharingScholar?.let { scholar ->
        ScholarShareModal(
            scholar = scholar,
            onDismiss = { sharingScholar = null }
        )
    }
}

/**
 * بطاقة سيرة اليوم وموقف الثبات مع التحدي السلوكي العملي
 */
@Composable
fun DailyFeaturedScholarCard(
    scholar: ScholarBiography,
    isChallengeCompleted: Boolean,
    completedCount: Int,
    onToggleChallenge: () -> Unit,
    onReadClick: () -> Unit
) {
    val theme = QabasTheme.current
    AppleCard(cornerRadius = 18.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = PresetPrimary.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "سِيرَةُ اليَوْمِ وَمَوْقِفُ الثَّبَات",
                            color = PresetPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                    }
                }

                Surface(
                    color = theme.secondarySurface,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "أنجزت $completedCount اقتداءً",
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(54.dp),
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, PresetPrimary.copy(alpha = 0.7f))
                ) {
                    AsyncImage(
                        model = scholar.imageRes,
                        contentDescription = scholar.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = scholar.name,
                        color = theme.textPrimary,
                        fontFamily = AmiriFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                    scholar.period?.let {
                        Text(
                            text = "العصر: $it",
                            color = theme.primary,
                            fontFamily = CairoFont,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            scholar.enduranceQuote?.let { quote ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "« $quote »",
                    color = theme.textPrimary,
                    fontFamily = AmiriFont,
                    fontSize = 15.5.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp)
                )
            }

            scholar.modernChallenge?.let { challenge ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isChallengeCompleted) theme.success.copy(alpha = 0.12f) else theme.secondarySurface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        0.75.dp,
                        if (isChallengeCompleted) theme.success.copy(alpha = 0.6f) else theme.separator
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = if (isChallengeCompleted) theme.success else PresetPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "تحدي الاقتداء اليومي المعاصر:",
                                color = if (isChallengeCompleted) theme.success else PresetPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = challenge,
                            color = theme.textPrimary,
                            fontFamily = NotoSansFont,
                            fontSize = 12.5.sp,
                            lineHeight = 19.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onToggleChallenge,
                                modifier = Modifier
                                    .weight(1.3f)
                                    .heightIn(min = 48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isChallengeCompleted) theme.success else theme.primary,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (isChallengeCompleted) Icons.Default.CheckCircle else Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isChallengeCompleted) "أنجزتُه اليوم" else "عزمتُ على تطبيقه",
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }

                            OutlinedButton(
                                onClick = onReadClick,
                                modifier = Modifier
                                    .weight(0.9f)
                                    .heightIn(min = 48.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, theme.primary.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = "قراءة السيرة",
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
        }
    }
}

/**
 * بطاقة الإمام أو الصحابي الجليل مع محطات رحلة العلم والوسوم الذكية
 */
@Composable
fun ScholarBiographyCard(
    scholar: ScholarBiography,
    isBookmarked: Boolean,
    onBookmarkToggle: () -> Unit,
    lectureCount: Int,
    onLecturesClick: () -> Unit,
    onShareClick: () -> Unit,
    onClick: () -> Unit
) {
    val theme = QabasTheme.current
    AppleCard(
        onClick = onClick,
        cornerRadius = 18.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                AsyncImage(
                    model = scholar.imageRes,
                    contentDescription = scholar.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Adaptive Gradient Overlay blending smoothly into theme.cardSurface
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.25f), theme.cardSurface.copy(alpha = 0.96f)),
                                startY = 80f
                            )
                        )
                )

                // Period and Era Badges Top Start
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    scholar.era?.let { era ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            border = androidx.compose.foundation.BorderStroke(0.75.dp, PresetPrimary.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = era,
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    scholar.period?.let {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.55f)
                        ) {
                            Text(
                                text = it,
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontSize = 10.5.sp,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Top End Action Buttons: Bookmark + Lectures (48dp touch targets)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = onBookmarkToggle,
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = if (isBookmarked) PresetPrimary else theme.cardSurface.copy(alpha = 0.94f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isBookmarked) PresetPrimary else theme.separator),
                        shadowElevation = 2.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (isBookmarked) "إزالة من المفضلة" else "إضافة للمفضلة",
                                tint = if (isBookmarked) Color.White else PresetPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = onLecturesClick,
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = theme.cardSurface.copy(alpha = 0.94f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, theme.separator),
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = "الدروس والمحاضرات",
                                tint = theme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (lectureCount > 0) "$lectureCount دروس" else "+ درس",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Name Overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = scholar.name,
                        color = theme.textPrimary,
                        fontFamily = AmiriFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 23.sp
                    )
                }
            }

            // Card Body
            Column(modifier = Modifier.padding(16.dp)) {
                scholar.majorHistoricalEvent?.let { event ->
                    Surface(
                        color = PresetPrimary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Stars, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(14.dp))
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

                Text(
                    text = scholar.brief,
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 13.5.sp,
                    lineHeight = 21.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (scholar.journeyStops.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = theme.secondarySurface,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Explore, contentDescription = null, tint = theme.primary, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "المسار: ${scholar.journeyStops.first()} ← ${scholar.journeyStops.last()}${scholar.estimatedTravelKm?.let { " (~$it كم)" } ?: ""}",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                scholar.sacrificeSummary?.let { sacrifice ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = theme.secondarySurface,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                tint = PresetPrimary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    "المحنة والصمود:",
                                    color = PresetPrimary,
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = sacrifice,
                                    color = theme.textPrimary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 12.5.sp,
                                    lineHeight = 19.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                scholar.deathScene?.let { death ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = theme.secondarySurface,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.HourglassBottom,
                                contentDescription = null,
                                tint = theme.textSecondary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "مشهد الوداع الأخير:",
                                    color = theme.textSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = death,
                                    color = theme.textPrimary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                scholar.enduranceQuote?.let { quote ->
                    Text(
                        text = "« $quote »",
                        color = theme.primary,
                        fontFamily = AmiriFont,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.primary,
                            contentColor = theme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AutoStories, contentDescription = null, tint = theme.onPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "اقرأ المخطوطة كاملة",
                            color = theme.onPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    OutlinedButton(
                        onClick = onLecturesClick,
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, theme.separator)
                    ) {
                        Icon(
                            Icons.Default.VideoLibrary,
                            contentDescription = "الدروس والمحاضرات",
                            tint = theme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (lectureCount > 0) "$lectureCount" else "+ درس",
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    OutlinedButton(
                        onClick = onShareClick,
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, theme.separator),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "مشاركة سيرة الإمام",
                            tint = theme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
