package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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

/**
 * شاشة اختيار وصهر الأنماط الفنية (StyleSelectionScreen)
 * تتيح للمستخدم استعراض كائنات الأنماط المخزنة في Firestore ومحلياً،
 * واختيار عدة أنماط مع موازنة أوزانها ومعاينة الـ Master Style الناتج فورياً
 * قبل تطبيقه على مشروع جديد أو حفظه.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleSelectionScreen(
    onBack: () -> Unit,
    onApplyMasterStyleToProject: (MasterStyle) -> Unit,
    onCreateCustomStyle: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // Observe local styles and master styles
    val localAbsorbedStyles by StyleBrain.absorbedStyles.collectAsState()
    val localMasterStyles by StyleManager.masterStyles.collectAsState()
    val activeMasterStyle by StyleManager.activeMasterStyle.collectAsState()

    // Cloud Style Objects
    var cloudStyleObjects by remember { mutableStateOf<List<StyleObject>>(emptyList()) }
    var isSyncingCloud by remember { mutableStateOf(false) }

    // Synchronize and fetch from Cloud on launch
    LaunchedEffect(Unit) {
        if (CloudServices.isFirebaseInitialized) {
            isSyncingCloud = true
            try {
                cloudStyleObjects = CloudServices.Database.getCloudStyleObjects()
                StyleManager.syncWithCloud(context)
            } catch (e: Exception) {
                // Ignore sync errors gracefully
            } finally {
                isSyncingCloud = false
            }
        }
    }

    // Combine local absorbed styles and cloud style objects without duplicate IDs
    val allStyleObjects = remember(localAbsorbedStyles, cloudStyleObjects) {
        val list = mutableListOf<StyleObject>()
        
        // Add cloud styles first
        cloudStyleObjects.forEach { cloudObj ->
            if (list.none { it.id == cloudObj.id }) {
                list.add(cloudObj)
            }
        }

        // Add local absorbed styles converted to StyleObjects
        localAbsorbedStyles.forEach { absorbed ->
            if (list.none { it.id == absorbed.id }) {
                list.add(StyleObject.fromAbsorbedStyle(absorbed))
            }
        }

        // Add default Qabas core style if empty
        if (list.isEmpty()) {
            val core = StyleBrain.getCoreStyle()
            list.add(
                StyleObject(
                    id = "qabas_core_default",
                    name = "هوية قبس الأساسية الفاخرة",
                    userId = "system",
                    visualStyle = VisualStylePattern(
                        dominantColors = "DeepSlate (#0B0F19) مع لمسات ذهبية (#E8C547)",
                        primaryColorHex = "#E8C547",
                        backgroundColorHex = "#0B0F19",
                        lightingAndContrast = "إضاءة دافئة وتباين عالي 1:10",
                        visualTraits = core.visualTraits
                    ),
                    motionRhythm = MotionRhythmPattern(
                        transitionSpeed = "انسيابية متوازنة (0.6 ثانية)",
                        transitionType = "Dissolve",
                        movementPatterns = "زووم بطيء سينمائي متدرج",
                        overallRhythm = "تصاعدي وقور ومؤثر",
                        motionTraits = core.motionTraits
                    ),
                    contentTone = ContentTonePattern(
                        tone = "وقور ومؤثر ودعوي هادف",
                        targetAudience = "الجمهور العام",
                        typographyStyle = "خط كوفي عريض مع إبراز الكلمات بالذهبي",
                        captionAnimation = "WordByWord",
                        textTraits = core.textTraits
                    ),
                    overallScore = core.strengthScore,
                    analysisSummary = core.analysis,
                    tags = listOf("QabasCore", "Cinematic", "Gold", "Islamic")
                )
            )
        }

        list
    }

    // Filter & Search states
    var searchQuery by remember { mutableStateOf("") }
    var selectedTabFilter by remember { mutableIntStateOf(0) } // 0: الكل, 1: سحابية, 2: محلية, 3: الأعلى تقييماً

    // Selection map: StyleObject.id -> Weight (0.1f to 1.0f)
    val selectedStylesWeights = remember { mutableStateMapOf<String, Float>() }

    // Project Requirements for intelligent merging
    var projectTopic by remember { mutableStateOf("") }
    var selectedTone by remember { mutableStateOf("وقور ومؤثر") }
    var selectedPacing by remember { mutableStateOf("متوازن") }
    var selectedAudience by remember { mutableStateOf("الجمهور العام") }
    var selectedAspectRatio by remember { mutableStateOf("9:16") }
    var customMasterName by remember { mutableStateOf("") }
    var showRequirementDetails by remember { mutableStateOf(false) }

    // Filtered list of styles
    val filteredStyles = remember(allStyleObjects, searchQuery, selectedTabFilter) {
        allStyleObjects.filter { style ->
            val matchesSearch = searchQuery.isBlank() ||
                    style.name.contains(searchQuery, ignoreCase = true) ||
                    style.analysisSummary.contains(searchQuery, ignoreCase = true) ||
                    style.tags.any { it.contains(searchQuery, ignoreCase = true) } ||
                    style.contentTone.tone.contains(searchQuery, ignoreCase = true)

            val matchesTab = when (selectedTabFilter) {
                1 -> style.isCloudSynced
                2 -> !style.isCloudSynced
                3 -> style.overallScore >= 92
                else -> true
            }

            matchesSearch && matchesTab
        }
    }

    // Prepare weighted list for Live Preview
    val selectedWeightedPairs = remember(selectedStylesWeights.toMap(), allStyleObjects) {
        selectedStylesWeights.mapNotNull { (id, weight) ->
            val styleObj = allStyleObjects.find { it.id == id }
            if (styleObj != null) styleObj to weight else null
        }
    }

    // Current Project Requirements Object
    val currentRequirements = remember(projectTopic, selectedTone, selectedPacing, selectedAudience, selectedAspectRatio) {
        ProjectStyleRequirements(
            ideaOrTopic = projectTopic.ifBlank { "مشروع دعوي قرآني هادف" },
            durationSeconds = if (selectedAspectRatio == "9:16") 30 else 60,
            aspectRatio = selectedAspectRatio,
            targetAudience = selectedAudience,
            desiredTone = selectedTone,
            pacingPreference = selectedPacing,
            visualPalettePreference = "ألوان داكنة سينمائية وذهب قبس"
        )
    }

    // Real-time Live Master Style Preview calculation
    val liveMasterPreview = remember(selectedWeightedPairs, currentRequirements, customMasterName) {
        StyleManager.previewMerge(
            weightedStyles = selectedWeightedPairs,
            requirements = currentRequirements,
            customName = customMasterName
        )
    }

    var isSavingMasterToCloud by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = Translator.tr("مختبر صهر واختيار الأنماط"),
                            color = GoldPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = Translator.tr("Style Objects Fusion & Master Preview"),
                            color = TextSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = Translator.tr("رجوع"),
                            tint = GoldPrimary
                        )
                    }
                },
                actions = {
                    // Cloud Sync Button
                    IconButton(
                        onClick = {
                            if (!CloudServices.isFirebaseInitialized) {
                                Toast.makeText(context, Translator.tr("Firebase غير متصل حالياً"), Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            scope.launch {
                                isSyncingCloud = true
                                try {
                                    cloudStyleObjects = CloudServices.Database.getCloudStyleObjects()
                                    StyleManager.syncWithCloud(context)
                                    Toast.makeText(context, Translator.tr("تمت مزامنة الأنماط الفنية من السحابة ✨"), Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "${Translator.tr("خطأ في المزامنة:")} ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isSyncingCloud = false
                                }
                            }
                        }
                    ) {
                        if (isSyncingCloud) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = GoldPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = Translator.tr("مزامنة السحابة"),
                                tint = if (CloudServices.isFirebaseInitialized) GoldPrimary else Color.Gray
                            )
                        }
                    }

                    // Create Custom Style Object Button
                    IconButton(onClick = onCreateCustomStyle) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = Translator.tr("إنشاء نمط مخصص"),
                            tint = GoldPrimary
                        )
                    }

                    // Reset selection
                    if (selectedStylesWeights.isNotEmpty()) {
                        IconButton(onClick = { selectedStylesWeights.clear() }) {
                            Icon(
                                imageVector = Icons.Default.ClearAll,
                                contentDescription = Translator.tr("إلغاء التحديد"),
                                tint = Color(0xFFEF4444)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0B0F19),
                    navigationIconContentColor = GoldPrimary,
                    titleContentColor = GoldPrimary
                )
            )
        },
        containerColor = DeepSlate
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 0. Create Custom Style Action Card
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onCreateCustomStyle() },
                    shape = RoundedCornerShape(16.dp),
                    color = CardSurface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Brush.horizontalGradient(listOf(GoldPrimary.copy(alpha = 0.6f), GoldSecondary.copy(alpha = 0.2f)))
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(GoldPrimary.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = Translator.tr("إنشاء نمط فني مخصص (Custom Style Object)"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = Translator.tr("صمم النبرة والهوية البصرية والأسلوب الصوتي واحفظه في Firestore"),
                                fontSize = 11.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 1. Search Bar
            item {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = Translator.tr("ابحث بالاسم، النبرة، الكلمات المفتاحية..."),
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontFamily = CairoFont
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = GoldPrimary)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = TextSecondary)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color(0xFF1E293B),
                        focusedContainerColor = CardSurface,
                        unfocusedContainerColor = CardSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
            }

            // 2. Filter Tabs
            item {
                val filterTabs = listOf(
                    Translator.tr("الكل") to allStyleObjects.size,
                    Translator.tr("سحابية ☁️") to allStyleObjects.count { it.isCloudSynced },
                    Translator.tr("محلية 💾") to allStyleObjects.count { !it.isCloudSynced },
                    Translator.tr("الأعلى توافقاً 🌟") to allStyleObjects.count { it.overallScore >= 92 }
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filterTabs.size) { index ->
                        val (label, count) = filterTabs[index]
                        val isSelected = selectedTabFilter == index

                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedTabFilter = index },
                            label = {
                                Text(
                                    text = "$label ($count)",
                                    fontFamily = CairoFont,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp,
                                    color = if (isSelected) DeepSlate else TextPrimary
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldPrimary,
                                containerColor = CardSurface
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) GoldPrimary else Color(0xFF1E293B)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // 3. Selection Summary Banner
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CardSurface,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            listOf(
                                GoldPrimary.copy(alpha = 0.6f),
                                GoldSecondary.copy(alpha = 0.3f),
                                Color(0xFF1E293B)
                            )
                        )
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (selectedStylesWeights.isNotEmpty()) Color(0xFF10B981) else GoldPrimary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (selectedStylesWeights.isEmpty())
                                        Translator.tr("حدد أنماطاً فنية لدمجها وصهرها")
                                    else
                                        "${Translator.tr("تم تحديد")} ${selectedStylesWeights.size} ${Translator.tr("أنماط فنية للصهر")}",
                                    color = TextPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = Translator.tr("المعاينة الحية في الأسفل تتفاعل وتتغير فوراً مع كل نسبة ونمط تختاره."),
                                color = TextSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 11.sp
                            )
                        }

                        // Select all / Toggle details
                        IconButton(
                            onClick = { showRequirementDetails = !showRequirementDetails }
                        ) {
                            Icon(
                                imageVector = if (showRequirementDetails) Icons.Default.Tune else Icons.Default.Tune,
                                contentDescription = Translator.tr("تخصيص متطلبات المشروع"),
                                tint = if (showRequirementDetails) GoldPrimary else TextSecondary
                            )
                        }
                    }
                }
            }

            // 4. Expandable Project Requirements Panel
            item {
                AnimatedVisibility(
                    visible = showRequirementDetails,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = Translator.tr("⚙️ متطلبات الإخراج وصهر الماستر:"),
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )

                            // Tone Selector
                            Text(
                                text = Translator.tr("نبرة المحتوى المستهدفة:"),
                                color = TextSecondary,
                                fontFamily = CairoFont,
                                fontSize = 12.sp
                            )
                            val tones = listOf("وقور ومؤثر", "حماسي دعوي", "هادئ وتدبر", "وثائقي تاريخي")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(tones.size) { i ->
                                    val tone = tones[i]
                                    val isSel = selectedTone == tone
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { selectedTone = tone },
                                        label = { Text(tone, fontSize = 11.sp, fontFamily = CairoFont) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = GoldPrimary,
                                            containerColor = Color(0xFF0B0F19)
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }

                            // Pacing Selector
                            Text(
                                text = Translator.tr("إيقاع ووتيرة الانتقالات:"),
                                color = TextSecondary,
                                fontFamily = CairoFont,
                                fontSize = 12.sp
                            )
                            val pacings = listOf("سريع وحيوي (0.4 ثانية)", "متوازن (0.6 ثانية)", "وقور متأني (0.9 ثانية)")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(pacings.size) { i ->
                                    val pacing = pacings[i]
                                    val isSel = selectedPacing == pacing
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { selectedPacing = pacing },
                                        label = { Text(pacing, fontSize = 11.sp, fontFamily = CairoFont) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = GoldPrimary,
                                            containerColor = Color(0xFF0B0F19)
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }

                            // Ratio Selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = Translator.tr("أبعاد الفيديو المستهدف:"),
                                    color = TextSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = selectedAspectRatio == "9:16",
                                        onClick = { selectedAspectRatio = "9:16" },
                                        label = { Text("9:16 ريلز / شورتس", fontSize = 11.sp, fontFamily = CairoFont) }
                                    )
                                    FilterChip(
                                        selected = selectedAspectRatio == "16:9",
                                        onClick = { selectedAspectRatio = "16:9" },
                                        label = { Text("16:9 أفقي يوتيوب", fontSize = 11.sp, fontFamily = CairoFont) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Section Header: Available Style Objects
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Translator.tr("قائمة كائنات الأنماط المتاحة:"),
                        color = GoldPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "${filteredStyles.size} ${Translator.tr("نمط")}",
                        color = TextSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp
                    )
                }
            }

            // 6. Style Object Cards List
            if (filteredStyles.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Style,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = Translator.tr("لم يتم العثور على أنماط مطابقة لبحثك"),
                                color = TextSecondary,
                                fontFamily = CairoFont,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else {
                items(filteredStyles, key = { it.id }) { styleObj ->
                    val isSelected = selectedStylesWeights.containsKey(styleObj.id)
                    val weight = selectedStylesWeights[styleObj.id] ?: 0.5f

                    StyleObjectSelectionCard(
                        style = styleObj,
                        isSelected = isSelected,
                        weight = weight,
                        onToggleSelect = {
                            if (isSelected) {
                                selectedStylesWeights.remove(styleObj.id)
                            } else {
                                selectedStylesWeights[styleObj.id] = 0.5f
                            }
                        },
                        onWeightChange = { newWeight ->
                            selectedStylesWeights[styleObj.id] = newWeight
                        }
                    )
                }
            }

            // 7. SECTION: LIVE MASTER STYLE PREVIEW CARD
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Translator.tr("المعاينة الحية للملف الماستر المدمج"),
                            color = GoldPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981))
                    ) {
                        Text(
                            text = "${liveMasterPreview.islamicAestheticComplianceScore}% ${Translator.tr("توافق جمالي")}",
                            color = Color(0xFF10B981),
                            fontFamily = NotoSansFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 8. The Live Master Profile Component
            item {
                LiveMasterPreviewCard(
                    masterStyle = liveMasterPreview,
                    customName = customMasterName,
                    onCustomNameChange = { customMasterName = it },
                    onCopyFfmpeg = {
                        clipboardManager.setText(AnnotatedString(liveMasterPreview.ffmpegFilterDirective))
                        Toast.makeText(context, Translator.tr("تم نسخ فلتر FFmpeg إلى الحافظة 📋"), Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // 9. Primary Action Buttons
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Button 1: Apply to New Project
                    Button(
                        onClick = {
                            StyleManager.setActiveMasterStyle(context, liveMasterPreview)
                            onApplyMasterStyleToProject(liveMasterPreview)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(GoldSecondary, GoldPrimary, Color(0xFFD4AF37))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = DeepSlate,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = Translator.tr("اعتماد الماستر وبدء الإنتاج فوراً"),
                                    color = DeepSlate,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }

                    // Secondary Actions Row: Save to Cloud + Set Default
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Button 2: Save Master Style to Firestore
                        OutlinedButton(
                            onClick = {
                                if (!CloudServices.isFirebaseInitialized) {
                                    Toast.makeText(context, Translator.tr("Firebase غير متصل. سيتم الحفظ محلياً فقط."), Toast.LENGTH_SHORT).show()
                                }
                                scope.launch {
                                    isSavingMasterToCloud = true
                                    try {
                                        StyleManager.mergeExplicitStyleObjects(
                                            context = context,
                                            name = customMasterName.ifBlank { liveMasterPreview.name },
                                            weightedStyles = selectedWeightedPairs,
                                            requirements = currentRequirements
                                        )
                                        if (CloudServices.isFirebaseInitialized) {
                                            CloudServices.Database.saveMasterStyleToCloud(liveMasterPreview)
                                        }
                                        Toast.makeText(context, Translator.tr("تم حفظ النمط الماستر في السحابة والذاكرة بنجاح! ☁️✨"), Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "${Translator.tr("خطأ أثناء الحفظ:")} ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isSavingMasterToCloud = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CardSurface,
                                contentColor = GoldPrimary
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
                        ) {
                            if (isSavingMasterToCloud) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = GoldPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = Translator.tr("حفظ في السحابة"),
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        // Button 3: Set as active system style
                        OutlinedButton(
                            onClick = {
                                StyleManager.setActiveMasterStyle(context, liveMasterPreview)
                                Toast.makeText(context, Translator.tr("تم تعيين النمط كمرجع إنتاج نشط 🎯"), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CardSurface,
                                contentColor = TextPrimary
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircleOutline,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = Translator.tr("تعيين كافتراضي"),
                                    fontFamily = CairoFont,
                                    fontSize = 13.sp
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
 * بطاقة تمثيل كائن النمط الفني الواحد (StyleObject)
 */
@Composable
fun StyleObjectSelectionCard(
    style: StyleObject,
    isSelected: Boolean,
    weight: Float,
    onToggleSelect: () -> Unit,
    onWeightChange: (Float) -> Unit
) {
    val borderColor = if (isSelected) GoldPrimary else Color(0xFF1E293B)
    val containerColor = if (isSelected) Color(0xFF182032) else CardSurface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleSelect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Checkbox, Name, and Cloud/Score badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = GoldPrimary,
                            checkmarkColor = DeepSlate,
                            uncheckedColor = TextSecondary
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = style.name,
                            color = if (isSelected) GoldPrimary else TextPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (style.sourceVideoPathOrUrl.isNotBlank()) {
                            Text(
                                text = style.sourceVideoPathOrUrl,
                                color = TextSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (style.isCloudSynced) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF3B82F6).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF3B82F6))
                        ) {
                            Text(
                                text = "☁️ Firestore",
                                color = Color(0xFF60A5FA),
                                fontSize = 10.sp,
                                fontFamily = NotoSansFont,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = GoldPrimary.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, GoldPrimary.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "${style.overallScore}%",
                            color = GoldSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            fontFamily = NotoSansFont,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Visual Characteristics summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Color swatch
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            try {
                                Color(android.graphics.Color.parseColor(style.visualStyle.primaryColorHex))
                            } catch (e: Exception) {
                                GoldPrimary
                            }
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                )

                Text(
                    text = "${Translator.tr("النبرة:")} ${style.contentTone.tone}",
                    color = TextSecondary,
                    fontFamily = CairoFont,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "⚡ ${style.motionRhythm.transitionSpeed}",
                    color = TextSecondary,
                    fontFamily = CairoFont,
                    fontSize = 11.sp
                )
            }

            // Tags / Traits Chips
            val traits = (style.visualStyle.visualTraits.take(2) + style.motionRhythm.motionTraits.take(1)).filter { it.isNotBlank() }
            if (traits.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    traits.forEach { trait ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF0B0F19),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF1E293B))
                        ) {
                            Text(
                                text = trait,
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontFamily = CairoFont,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // If Selected: Show Interactive Weight Slider
            AnimatedVisibility(
                visible = isSelected,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0B0F19), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = Translator.tr("نسبة تأثير هذا النمط في الصهر:"),
                            color = GoldSecondary,
                            fontFamily = CairoFont,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "${(weight * 100).toInt()}%",
                            color = GoldPrimary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = NotoSansFont,
                            fontSize = 12.sp
                        )
                    }

                    Slider(
                        value = weight,
                        onValueChange = { onWeightChange(it) },
                        valueRange = 0.1f..1.0f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = GoldPrimary,
                            activeTrackColor = GoldPrimary,
                            inactiveTrackColor = Color(0xFF1E293B)
                        )
                    )
                }
            }
        }
    }
}

/**
 * بطاقة المعاينة التفاعلية للملف الماستر المدمج (Live Master Style Profile Card)
 */
@Composable
fun LiveMasterPreviewCard(
    masterStyle: MasterStyle,
    customName: String,
    onCustomNameChange: (String) -> Unit,
    onCopyFfmpeg: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            Brush.verticalGradient(
                listOf(GoldPrimary, GoldSecondary.copy(alpha = 0.4f), Color(0xFF1E293B))
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF172033), DeepSlate)
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Master Style Name input / preview
            OutlinedTextField(
                value = customName,
                onValueChange = onCustomNameChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = masterStyle.name,
                        color = GoldSecondary.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        fontFamily = CairoFont
                    )
                },
                label = {
                    Text(
                        text = Translator.tr("اسم النمط الماستر المركب:"),
                        color = GoldPrimary,
                        fontSize = 11.sp,
                        fontFamily = CairoFont
                    )
                },
                trailingIcon = {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = Color(0xFF1E293B),
                    focusedContainerColor = Color(0xFF0B0F19),
                    unfocusedContainerColor = Color(0xFF0B0F19),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Fusion Summary Description
            Text(
                text = masterStyle.description.ifBlank { masterStyle.fusionSummary },
                color = TextSecondary,
                fontFamily = NotoSansFont,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )

            HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)

            // Core Profile Breakdown Matrix (Visual, Motion, Tone)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 1. Visual Matrix
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                try {
                                    Color(android.graphics.Color.parseColor(masterStyle.visualStyle.primaryColorHex))
                                } catch (e: Exception) {
                                    GoldPrimary
                                }
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Translator.tr("الهوية البصرية والألوان:"),
                            color = GoldSecondary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = masterStyle.visualStyle.dominantColors,
                            color = TextPrimary,
                            fontFamily = NotoSansFont,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 2. Motion & Pacing Matrix
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3B82F6).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Translator.tr("الإيقاع وحركة الكاميرا:"),
                            color = GoldSecondary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "${masterStyle.motionRhythm.movementPatterns} • ${masterStyle.motionRhythm.transitionSpeed}",
                            color = TextPrimary,
                            fontFamily = NotoSansFont,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 3. Typography & Tone Matrix
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TextFields,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Translator.tr("النبرة والتيبوغرافيا:"),
                            color = GoldSecondary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "${masterStyle.contentTone.tone} • ${masterStyle.contentTone.typographyStyle}",
                            color = TextPrimary,
                            fontFamily = NotoSansFont,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Real FFmpeg Filter Directive Snippet Box
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF070A10),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FFmpeg Filter Directive",
                            color = TextSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = masterStyle.ffmpegFilterDirective,
                            color = GoldSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onCopyFfmpeg,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = Translator.tr("نسخ"),
                            tint = GoldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Blended Source Contributors
            if (masterStyle.sourceStyleNames.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = Translator.tr("الأنماط المساهمة في هذا الماستر:"),
                        color = TextSecondary,
                        fontFamily = CairoFont,
                        fontSize = 11.sp
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        masterStyle.sourceStyleNames.forEach { name ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = GoldPrimary.copy(alpha = 0.1f),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, GoldPrimary.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = "✨ $name",
                                    color = GoldPrimary,
                                    fontSize = 10.sp,
                                    fontFamily = CairoFont,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
