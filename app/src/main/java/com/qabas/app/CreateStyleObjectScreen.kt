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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * شاشة إنشاء وتخصيص كائنات الأنماط الفنية (Create Custom Style Object)
 * تتيح للمستخدم ضبط النبرة، الهوية البصرية، والأسلوب الصوتي، وحفظ النمط في Supabase وسحابة قبس.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateStyleObjectScreen(
    onBack: () -> Unit,
    onStyleSaved: (StyleObject) -> Unit = {},
    onApplyDirectlyToProject: (StyleObject) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 1. Basic Info
    var styleName by remember { mutableStateOf("") }
    var analysisSummary by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("عام ودعوي") }

    // 2. Content Tone & Typography
    var selectedTone by remember { mutableStateOf("وقور وروحاني") }
    var targetAudience by remember { mutableStateOf("الجمهور العام والشباب") }
    var typographyStyle by remember { mutableStateOf("خط كوفي عريض في المنتصف مع إبراز الكلمات بالذهبي") }
    var captionAnimation by remember { mutableStateOf("WordByWord") }

    // 3. Visual Aesthetic
    var primaryColorHex by remember { mutableStateOf("#E8C547") }
    var backgroundColorHex by remember { mutableStateOf("#0B0F19") }
    var lightingAndContrast by remember { mutableStateOf("إضاءة سينمائية دافئة مع تباين عالي 1:10") }
    var selectedVisualTraits by remember {
        mutableStateOf(
            setOf(
                "ألوان داكنة DeepSlate #0B0F19",
                "لمسات ذهبية متوهجة Gold #E8C547",
                "نقاء بصري 4K فائق",
                "حبيبات سينمائية (Film Grain)"
            )
        )
    }

    // 4. Voice & Audio Style
    var voiceStyle by remember { mutableStateOf("صوت رخيم هادئ وتأملي") }
    var voiceEngine by remember { mutableStateOf("ElevenLabs Neural HD") }
    var audioMood by remember { mutableStateOf("مؤثرات طبيعية هادئة (خرير ماء ونسيم هواء)") }
    var voicePitchRate by remember { mutableFloatStateOf(1.0f) }

    // 5. Motion Rhythm & Camera
    var transitionSpeed by remember { mutableStateOf("متوازنة وانسيابية (0.6 ثانية)") }
    var transitionType by remember { mutableStateOf("Dissolve") }
    var movementPatterns by remember { mutableStateOf("زووم بطيء متصاعد (Slow Cinematic ZoomIn)") }
    var selectedMotionTraits by remember {
        mutableStateOf(
            setOf(
                "زووم بطيء متصاعد ناعم",
                "انتقالات تلاشي ناعمة Smooth Dissolve"
            )
        )
    }

    // Tags & State
    var customTagInput by remember { mutableStateOf("") }
    var tagsList by remember {
        mutableStateOf(listOf("مخصص", "دعوي", "4K", "سينمائي"))
    }
    var syncToCloud by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var showAiAssistDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf<StyleObject?>(null) }

    // حساب درجة التوافق الجمالي الإسلامي ديناميكياً
    val complianceScore = remember(primaryColorHex, selectedTone, typographyStyle, selectedVisualTraits) {
        var score = 88
        if (primaryColorHex == "#E8C547" || primaryColorHex == "#F5D76E" || primaryColorHex == "#10B981") score += 5
        if (selectedTone.contains("وقور") || selectedTone.contains("روحاني") || selectedTone.contains("خاشع")) score += 4
        if (typographyStyle.contains("كوفي") || typographyStyle.contains("عربي")) score += 3
        score.coerceIn(75, 100)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = Translator.tr("صانع الأنماط المخصصة"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = GoldPrimary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "Supabase Sync",
                                    color = GoldPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = Translator.tr("تصميم وحفظ كائن نمط فني جديد بالذكاء الاصطناعي"),
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    // زر التوليد السريع بالذكاء الاصطناعي
                    IconButton(
                        onClick = { showAiAssistDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Auto Fill",
                            tint = GoldPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSlate)
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
            contentPadding = PaddingValues(bottom = 36.dp)
        ) {
            // 1. AI Quick Presets Bar
            item {
                QuickPresetsSection(
                    onSelectPreset = { preset ->
                        styleName = preset.name
                        analysisSummary = preset.description
                        selectedTone = preset.tone
                        typographyStyle = preset.typography
                        captionAnimation = preset.captionAnim
                        primaryColorHex = preset.primaryColor
                        backgroundColorHex = preset.bgColor
                        lightingAndContrast = preset.lighting
                        voiceStyle = preset.voice
                        audioMood = preset.sfx
                        transitionSpeed = preset.transitionSpeed
                        movementPatterns = preset.cameraMovement
                        selectedVisualTraits = preset.visualTraits.toSet()
                        selectedMotionTraits = preset.motionTraits.toSet()
                        tagsList = (preset.tags + listOf("مخصص", "قبس")).distinct()
                        Toast.makeText(context, "تم تطبيق قالب: ${preset.name}", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // 2. Interactive Live Style Preview Card
            item {
                LiveStyleObjectPreviewCard(
                    styleName = styleName.ifBlank { "نمط فني جديد" },
                    tone = selectedTone,
                    typography = typographyStyle,
                    captionAnimation = captionAnimation,
                    primaryColorHex = primaryColorHex,
                    backgroundColorHex = backgroundColorHex,
                    voiceStyle = voiceStyle,
                    audioMood = audioMood,
                    movementPatterns = movementPatterns,
                    complianceScore = complianceScore,
                    tags = tagsList
                )
            }

            // 3. Section: Basic Identity & Name
            item {
                SectionCard(
                    title = "1. هوية النمط الأساسية",
                    icon = Icons.Default.Badge,
                    accentColor = GoldPrimary
                ) {
                    OutlinedTextField(
                        value = styleName,
                        onValueChange = { styleName = it },
                        label = { Text("اسم النمط الفني (مثال: وثائقي إيماني خاشع)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = outlinedTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Style, contentDescription = null, tint = GoldPrimary)
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = analysisSummary,
                        onValueChange = { analysisSummary = it },
                        label = { Text("الوصف الإخراجي وفلسفة الأسلوب") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = outlinedTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 2,
                        maxLines = 3
                    )
                }
            }

            // 4. Section: Content Tone & Typography
            item {
                SectionCard(
                    title = "2. النبرة، التيبوغرافيا، والجمهور",
                    icon = Icons.Default.RecordVoiceOver,
                    accentColor = Color(0xFF38BDF8)
                ) {
                    Text(
                        text = "نبرة المحتوى الدعوي:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val tones = listOf(
                        "وقور وروحاني", "حماسي وتحفيزي", "قصصي درامي",
                        "وثائقي تأملي", "تعليمي منهجي", "دعوي رقيق ومؤثر"
                    )
                    ChipSelector(
                        options = tones,
                        selected = selectedTone,
                        onSelect = { selectedTone = it }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "الجمهور المستهدف:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val audiences = listOf(
                        "الجمهور العام والشباب", "طلاب العلم والدعاة", "الأسرة والناشئة", "متابعي المقاطع القصيرة"
                    )
                    ChipSelector(
                        options = audiences,
                        selected = targetAudience,
                        onSelect = { targetAudience = it }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "نمط الخط والتيبوغرافيا (Typography):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val typographyOptions = listOf(
                        "خط كوفي عريض في المنتصف مع إبراز الكلمات بالذهبي",
                        "خط رقعة فني فاخر مع توهج ناعم",
                        "خط نسخ حديث عالي الوضوح للقراءة السريعة",
                        "خط عربي سينمائي مع تدرج داكن خلفي"
                    )
                    typographyOptions.forEach { typo ->
                        val isSelected = typographyStyle == typo
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { typographyStyle = typo },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) GoldPrimary.copy(alpha = 0.12f) else CardSurface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.08f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { typographyStyle = typo },
                                    colors = RadioButtonDefaults.colors(selectedColor = GoldPrimary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = typo,
                                    fontSize = 13.sp,
                                    color = if (isSelected) GoldPrimary else TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "تحريك النصوص التوضيحية (Caption Animation):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val animOptions = listOf(
                        "WordByWord" to "كلمة بكلمة (تزامن دقيق)",
                        "PopUp" to "انبثاق مرن (Pop-up)",
                        "FadeInOut" to "تلاشي تدريجي (Fade)",
                        "HighlightGold" to "تظليل ذهبي مستمر"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        animOptions.forEach { (key, label) ->
                            val isSel = captionAnimation == key
                            FilterChip(
                                selected = isSel,
                                onClick = { captionAnimation = key },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GoldPrimary.copy(alpha = 0.2f),
                                    selectedLabelColor = GoldPrimary,
                                    containerColor = CardSurface,
                                    labelColor = TextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = if (isSel) GoldPrimary else Color.White.copy(alpha = 0.1f),
                                    selectedBorderColor = GoldPrimary,
                                    enabled = true,
                                    selected = isSel
                                )
                            )
                        }
                    }
                }
            }

            // 5. Section: Visual Aesthetic & Color Palette
            item {
                SectionCard(
                    title = "3. الهوية البصرية ولوحة الألوان",
                    icon = Icons.Default.Palette,
                    accentColor = Color(0xFF10B981)
                ) {
                    Text(
                        text = "اللون الأساسي المميز (Accent Color):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val primaryColors = listOf(
                        "#E8C547" to "ذهب قبس",
                        "#F5D76E" to "ذهب فاتح",
                        "#10B981" to "زمردي إسلامي",
                        "#38BDF8" to "أزرق سماوي",
                        "#F97316" to "غروب دافئ",
                        "#A855F7" to "أرجواني مهيب",
                        "#F8FAFC" to "فضة نقية"
                    )
                    ColorPaletteSelector(
                        colors = primaryColors,
                        selectedHex = primaryColorHex,
                        onColorSelected = { primaryColorHex = it }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "درجة الخلفية والظلال (Background Tone):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val bgColors = listOf(
                        "#0B0F19" to "سليت قبس الداكن",
                        "#06090E" to "أسود أوبسيديان",
                        "#0F291E" to "ليل زمردي",
                        "#0B1120" to "غسق ملكي",
                        "#1C0F13" to "عنابي فحمي"
                    )
                    ColorPaletteSelector(
                        colors = bgColors,
                        selectedHex = backgroundColorHex,
                        onColorSelected = { backgroundColorHex = it }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "الإضاءة والتباين السينمائي:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val lightingOptions = listOf(
                        "إضاءة سينمائية دافئة مع تباين عالي 1:10",
                        "إضاءة نورانية هادئة مع توهج محيطي ناعم",
                        "تباين درامي داكن مع تركيز مسرحي",
                        "إضاءة وثائقية طبيعية واقعية"
                    )
                    ChipSelector(
                        options = lightingOptions,
                        selected = lightingAndContrast,
                        onSelect = { lightingAndContrast = it }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "السمات البصرية المتقدمة (Multi-Select):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val availableVisualTraits = listOf(
                        "حبيبات سينمائية (Film Grain)",
                        "تدرجات ذهبية متوهجة",
                        "إطارات إسلامية هندسية رقيقة",
                        "زوايا داكنة سينمائية (Vignette)",
                        "نقاء بصري 4K فائق",
                        "إضاءة حواف ذهبية (Rim Light)",
                        "ضبابية عمق الميدان (Depth of Field)"
                    )
                    MultiChipSelector(
                        options = availableVisualTraits,
                        selectedSet = selectedVisualTraits,
                        onToggle = { trait ->
                            selectedVisualTraits = if (selectedVisualTraits.contains(trait)) {
                                selectedVisualTraits - trait
                            } else {
                                selectedVisualTraits + trait
                            }
                        }
                    )
                }
            }

            // 6. Section: Voice & Audio Style
            item {
                SectionCard(
                    title = "4. الأسلوب الصوتي ومحرك الذكاء الاصطناعي",
                    icon = Icons.Default.GraphicEq,
                    accentColor = Color(0xFFA855F7)
                ) {
                    Text(
                        text = "نبرة الصوت المولد:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val voiceTones = listOf(
                        "صوت رخيم هادئ وتأملي",
                        "صوت جهوري فخم ومؤثر",
                        "صوت قصصي دافئ وسلس",
                        "تلاوة ترتيلية خاشعة",
                        "صوت شبابي حيوي وواضح"
                    )
                    ChipSelector(
                        options = voiceTones,
                        selected = voiceStyle,
                        onSelect = { voiceStyle = it }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "محرك توليد الصوت المدعوم:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val engines = listOf(
                        "ElevenLabs Neural HD",
                        "Azure Speech Islamic",
                        "محرك قبس الصوتي المدمج"
                    )
                    ChipSelector(
                        options = engines,
                        selected = voiceEngine,
                        onSelect = { voiceEngine = it }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "المؤثرات الصوتية والمحيط (Audio Mood / SFX):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val moods = listOf(
                        "مؤثرات طبيعية هادئة (خرير ماء ونسيم هواء)",
                        "مؤثرات سينمائية تصاعدية وقورة",
                        "صدى مسجدي روحاني ووقور",
                        "مؤثرات صوتية حركية سريعة"
                    )
                    ChipSelector(
                        options = moods,
                        selected = audioMood,
                        onSelect = { audioMood = it }
                    )
                }
            }

            // 7. Section: Motion Rhythm & Transitions
            item {
                SectionCard(
                    title = "5. حركة الكاميرا وإيقاع المونتاج",
                    icon = Icons.Default.SlowMotionVideo,
                    accentColor = Color(0xFFF59E0B)
                ) {
                    Text(
                        text = "سرعة الانتقالات:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val speeds = listOf(
                        "سريعة وحيوية (0.4 ثانية)",
                        "متوازنة وانسيابية (0.6 ثانية)",
                        "وقورة وسينمائية متمهلة (0.9 ثانية)"
                    )
                    ChipSelector(
                        options = speeds,
                        selected = transitionSpeed,
                        onSelect = { transitionSpeed = it }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "نوع الانتقال الأساسي:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val transTypes = listOf("Dissolve", "FadeToBlack", "WhipZoom", "Slide")
                    ChipSelector(
                        options = transTypes,
                        selected = transitionType,
                        onSelect = { transitionType = it }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "حركة الكاميرا والتكبير (Camera Movement):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val cameraMoves = listOf(
                        "زووم بطيء متصاعد (Slow Cinematic ZoomIn)",
                        "حركة عائمة هادئة (Parallax Drift)",
                        "ثبات سينمائي مع تركيز",
                        "بانوراما سينمائية أفقية"
                    )
                    ChipSelector(
                        options = cameraMoves,
                        selected = movementPatterns,
                        onSelect = { movementPatterns = it }
                    )
                }
            }

            // 8. Tags & Cloud Sync Checkbox
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = CardSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "الوسوم والتصنيف:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tagsList.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = GoldPrimary.copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("#$tag", color = GoldPrimary, fontSize = 11.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove tag",
                                            tint = GoldPrimary,
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable {
                                                    tagsList = tagsList - tag
                                                }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customTagInput,
                                onValueChange = { customTagInput = it },
                                placeholder = { Text("أضف وسماً جديداً...", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                colors = outlinedTextFieldColors(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (customTagInput.isNotBlank()) {
                                        val clean = customTagInput.trim().replace("#", "")
                                        if (!tagsList.contains(clean)) {
                                            tagsList = tagsList + clean
                                        }
                                        customTagInput = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("إضافة", color = DeepSlate, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "مزامنة فورية مع سحابة Supabase",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "حفظ كائن النمط في قاعدة البيانات السحابية ليكون متاحاً لجميع مشروعاتك",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = syncToCloud,
                                onCheckedChange = { syncToCloud = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = GoldPrimary,
                                    checkedTrackColor = GoldPrimary.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }
                }
            }

            // 9. Save & Action Buttons
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            val nameToSave = styleName.ifBlank { "نمط قبس [${selectedTone.take(10)}]" }
                            isSaving = true
                            coroutineScope.launch {
                                val newStyleObject = StyleObject(
                                    id = "style_${UUID.randomUUID().toString().take(8)}",
                                    name = nameToSave,
                                    userId = SupabaseServices.Auth.getCurrentUserId() ?: "creator_local",
                                    sourceVideoPathOrUrl = "Custom Designer Studio",
                                    visualStyle = VisualStylePattern(
                                        dominantColors = "أسطح داكنة ($backgroundColorHex) مع تمييز ذهبي ($primaryColorHex)",
                                        primaryColorHex = primaryColorHex,
                                        backgroundColorHex = backgroundColorHex,
                                        lightingAndContrast = lightingAndContrast,
                                        visualTraits = selectedVisualTraits.toList()
                                    ),
                                    motionRhythm = MotionRhythmPattern(
                                        transitionSpeed = transitionSpeed,
                                        transitionType = transitionType,
                                        movementPatterns = movementPatterns,
                                        overallRhythm = "إيقاع تصاعدي وقور متزن",
                                        motionTraits = selectedMotionTraits.toList() + listOf(transitionSpeed, movementPatterns)
                                    ),
                                    contentTone = ContentTonePattern(
                                        tone = selectedTone,
                                        targetAudience = targetAudience,
                                        typographyStyle = typographyStyle,
                                        captionAnimation = captionAnimation,
                                        textTraits = listOf(selectedTone, targetAudience, typographyStyle)
                                    ),
                                    overallScore = complianceScore,
                                    analysisSummary = analysisSummary.ifBlank {
                                        "نمط فني مصمم خصيصاً بنبرة ($selectedTone)، وأسلوب صوتي ($voiceStyle)، وتيبوغرافيا ($typographyStyle)."
                                    },
                                    tags = tagsList,
                                    createdAt = System.currentTimeMillis(),
                                    isCloudSynced = syncToCloud && SupabaseServices.isSupabaseAvailable
                                )

                                // تسجيل محلي وفي Supabase
                                val success = StyleManager.registerOrUpdateStyleObject(
                                    context = context,
                                    styleObject = newStyleObject,
                                    syncToCloud = syncToCloud
                                )

                                isSaving = false
                                if (success) {
                                    showSuccessDialog = newStyleObject
                                } else {
                                    Toast.makeText(context, "تم حفظ النمط محلياً في عقل قبس", Toast.LENGTH_SHORT).show()
                                    onStyleSaved(newStyleObject)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = DeepSlate, strokeWidth = 2.dp)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = DeepSlate)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = Translator.tr("حفظ كائن النمط في Supabase والذاكرة"),
                                    color = DeepSlate,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: AI Auto Fill / Quick Assistant
    if (showAiAssistDialog) {
        AiStyleAssistantDialog(
            onDismiss = { showAiAssistDialog = false },
            onApplyGeneratedStyle = { generated ->
                styleName = generated.name
                analysisSummary = generated.description
                selectedTone = generated.tone
                typographyStyle = generated.typography
                captionAnimation = generated.captionAnim
                primaryColorHex = generated.primaryColor
                backgroundColorHex = generated.bgColor
                lightingAndContrast = generated.lighting
                voiceStyle = generated.voice
                audioMood = generated.sfx
                transitionSpeed = generated.transitionSpeed
                movementPatterns = generated.cameraMovement
                selectedVisualTraits = generated.visualTraits.toSet()
                selectedMotionTraits = generated.motionTraits.toSet()
                tagsList = (generated.tags + listOf("ذكاء_اصطناعي", "قبس")).distinct()
                showAiAssistDialog = false
                Toast.makeText(context, "تم توليد وتطبيق الأسلوب بالذكاء الاصطناعي بنجاح!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Dialog: Success & Apply to Project
    showSuccessDialog?.let { savedObj ->
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = null
                onStyleSaved(savedObj)
            },
            containerColor = CardSurface,
            icon = {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(GoldPrimary.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(32.dp))
                }
            },
            title = {
                Text(
                    text = "تم حفظ كائن النمط بنجاح!",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "تم تسجيل نمط \"${savedObj.name}\" في سحابة Supabase وذاكرة StyleBrain بنجاح بمؤشر جودة (${savedObj.overallScore}%).",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = null
                        onApplyDirectlyToProject(savedObj)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("تطبيق فوراً على مشروع جديد", color = DeepSlate, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSuccessDialog = null
                        onStyleSaved(savedObj)
                    }
                ) {
                    Text("العودة لمكتبة الأنماط", color = TextSecondary)
                }
            }
        )
    }
}

// =============================================================================
// Helper Composables & Subcomponents
// =============================================================================

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun LiveStyleObjectPreviewCard(
    styleName: String,
    tone: String,
    typography: String,
    captionAnimation: String,
    primaryColorHex: String,
    backgroundColorHex: String,
    voiceStyle: String,
    audioMood: String,
    movementPatterns: String,
    complianceScore: Int,
    tags: List<String>
) {
    val primaryColor = remember(primaryColorHex) {
        try { Color(android.graphics.Color.parseColor(primaryColorHex)) } catch (e: Exception) { GoldPrimary }
    }
    val bgColor = remember(backgroundColorHex) {
        try { Color(android.graphics.Color.parseColor(backgroundColorHex)) } catch (e: Exception) { DeepSlate }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            Brush.horizontalGradient(
                listOf(primaryColor.copy(alpha = 0.8f), primaryColor.copy(alpha = 0.2f), Color.White.copy(alpha = 0.1f))
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(primaryColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "معاينة حية لكائن النمط (Live Preview)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                }

                Surface(
                    color = primaryColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = primaryColor, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "توافق إسلامي $complianceScore%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = styleName,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Simulated typography / subtitle box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF000000).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .border(1.dp, primaryColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "﴿ إِنَّ مَعَ الْعُسْرِ يُسْرًا ﴾",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "محاكاة تيبوغرافيا: $typography | تحريك: $captionAnimation",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Badges row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PreviewBadge(icon = Icons.Default.Mic, label = voiceStyle.take(18), color = Color(0xFFA855F7))
                PreviewBadge(icon = Icons.Default.MusicNote, label = audioMood.take(18), color = Color(0xFF10B981))
                PreviewBadge(icon = Icons.Default.Videocam, label = movementPatterns.take(18), color = Color(0xFF38BDF8))
                PreviewBadge(icon = Icons.Default.Psychology, label = tone, color = primaryColor)
            }
        }
    }
}

@Composable
private fun PreviewBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ColorPaletteSelector(
    colors: List<Pair<String, String>>,
    selectedHex: String,
    onColorSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        colors.forEach { (hex, name) ->
            val colorObj = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { GoldPrimary }
            val isSelected = selectedHex.equals(hex, ignoreCase = true)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onColorSelected(hex) }
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(colorObj, CircleShape)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.2f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = if (hex == "#F8FAFC" || hex == "#F5D76E") DeepSlate else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = name,
                    fontSize = 10.sp,
                    color = if (isSelected) GoldPrimary else TextSecondary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun ChipSelector(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val isSelected = selected == option
            Surface(
                modifier = Modifier.clickable { onSelect(option) },
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) GoldPrimary.copy(alpha = 0.18f) else CardSurface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.1f)
                )
            ) {
                Text(
                    text = option,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) GoldPrimary else TextSecondary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun MultiChipSelector(
    options: List<String>,
    selectedSet: Set<String>,
    onToggle: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { rowOptions ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowOptions.forEach { option ->
                    val isSelected = selectedSet.contains(option)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onToggle(option) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) GoldPrimary.copy(alpha = 0.16f) else CardSurface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.08f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) GoldPrimary else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = option,
                                fontSize = 11.sp,
                                color = if (isSelected) GoldPrimary else TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (rowOptions.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickPresetsSection(onSelectPreset: (StylePresetData) -> Unit) {
    val presets = remember {
        listOf(
            StylePresetData(
                name = "وثائقي إيماني خاشع",
                description = "أسلوب تأملي وقور مصمم لقصص الأنبياء والتأملات القرآنية بألوان هادئة وسكينة بصرية عالية.",
                tone = "وقور وروحاني",
                typography = "خط كوفي عريض في المنتصف مع إبراز الكلمات بالذهبي",
                captionAnim = "WordByWord",
                primaryColor = "#E8C547",
                bgColor = "#0B0F19",
                lighting = "إضاءة سينمائية دافئة مع تباين عالي 1:10",
                voice = "صوت رخيم هادئ وتأملي",
                sfx = "مؤثرات طبيعية هادئة (خرير ماء ونسيم هواء)",
                transitionSpeed = "وقورة وسينمائية متمهلة (0.9 ثانية)",
                cameraMovement = "زووم بطيء متصاعد (Slow Cinematic ZoomIn)",
                visualTraits = listOf("ألوان داكنة DeepSlate #0B0F19", "لمسات ذهبية متوهجة Gold #E8C547", "حبيبات سينمائية (Film Grain)"),
                motionTraits = listOf("زووم بطيء متصاعد ناعم", "انتقالات تلاشي ناعمة Smooth Dissolve"),
                tags = listOf("وثائقي", "قرآني", "إيماني", "خاشع")
            ),
            StylePresetData(
                name = "حماسي دعوي سريع",
                description = "إيقاع حركي خاطف وسريع مصمم للمواعظ الشبابية والمقاطع القصيرة الأكثر انتشاراً.",
                tone = "حماسي وتحفيزي",
                typography = "خط نسخ حديث عالي الوضوح للقراءة السريعة",
                captionAnim = "PopUp",
                primaryColor = "#F97316",
                bgColor = "#06090E",
                lighting = "تباين درامي داكن مع تركيز مسرحي",
                voice = "صوت جهوري فخم ومؤثر",
                sfx = "مؤثرات صوتية حركية سريعة",
                transitionSpeed = "سريعة وحيوية (0.4 ثانية)",
                cameraMovement = "حركة عائمة هادئة (Parallax Drift)",
                visualTraits = listOf("نقاء بصري 4K فائق", "إضاءة حواف ذهبية (Rim Light)", "زوايا داكنة سينمائية (Vignette)"),
                motionTraits = listOf("انتقالات سريعة", "حركة كاميرا خاطفة"),
                tags = listOf("حماسي", "ريلز", "شبابي", "تفاعل")
            ),
            StylePresetData(
                name = "زمردي إسلامي ملكي",
                description = "هوية بصرية إسلامية فاخرة باللون الأخضر الزمردي والزخارف الهندسية الرقيقة.",
                tone = "دعوي رقيق ومؤثر",
                typography = "خط رقعة فني فاخر مع توهج ناعم",
                captionAnim = "FadeInOut",
                primaryColor = "#10B981",
                bgColor = "#0F291E",
                lighting = "إضاءة نورانية هادئة مع توهج محيطي ناعم",
                voice = "صوت قصصي دافئ وسلس",
                sfx = "صدى مسجدي روحاني ووقور",
                transitionSpeed = "متوازنة وانسيابية (0.6 ثانية)",
                cameraMovement = "ثبات سينمائي مع تركيز",
                visualTraits = listOf("إطارات إسلامية هندسية رقيقة", "تدرجات ذهبية متوهجة", "ضبابية عمق الميدان (Depth of Field)"),
                motionTraits = listOf("انتقالات انسيابية ناعمة", "ثبات وقور"),
                tags = listOf("زمردي", "ملكي", "فاخر", "دعوي")
            )
        )
    }

    Column {
        Text(
            text = "قوالب سريعة للبدء الفوري:",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { preset ->
                Surface(
                    modifier = Modifier.clickable { onSelectPreset(preset) },
                    shape = RoundedCornerShape(12.dp),
                    color = CardSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(preset.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }
            }
        }
    }
}

private data class StylePresetData(
    val name: String,
    val description: String,
    val tone: String,
    val typography: String,
    val captionAnim: String,
    val primaryColor: String,
    val bgColor: String,
    val lighting: String,
    val voice: String,
    val sfx: String,
    val transitionSpeed: String,
    val cameraMovement: String,
    val visualTraits: List<String>,
    val motionTraits: List<String>,
    val tags: List<String>
)

@Composable
private fun outlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = GoldPrimary,
    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = GoldPrimary,
    unfocusedLabelColor = TextSecondary,
    cursorColor = GoldPrimary
)

@Composable
private fun AiStyleAssistantDialog(
    onDismiss: () -> Unit,
    onApplyGeneratedStyle: (StylePresetData) -> Unit
) {
    var promptInput by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        icon = {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(28.dp))
        },
        title = {
            Text("المولد الذكي لكائنات الأنماط", fontWeight = FontWeight.Bold, color = TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "صف أسلوبك الدعوي أو الفكرة في سطر واحد، وسيقوم ذكاء قبس بضبط جميع الخصائص البصرية والصوتية والحركية فوراً:",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    placeholder = { Text("مثال: ريلز سريع عن فضل الصلاة على النبي بأسلوب درامي مؤثر", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isGenerating = true
                    val prompt = promptInput.lowercase()
                    val generated = if (prompt.contains("سريع") || prompt.contains("ريلز") || prompt.contains("حماسي")) {
                        StylePresetData(
                            name = "أسلوب ذكي: حماسي تفاعلي",
                            description = "نمط ذكي مولد تلقائياً للمقاطع السريعة ذات التأثير الحماسي والتفاعل العالي.",
                            tone = "حماسي وتحفيزي",
                            typography = "خط نسخ حديث عالي الوضوح للقراءة السريعة",
                            captionAnim = "PopUp",
                            primaryColor = "#F97316",
                            bgColor = "#06090E",
                            lighting = "تباين درامي داكن مع تركيز مسرحي",
                            voice = "صوت جهوري فخم ومؤثر",
                            sfx = "مؤثرات صوتية حركية سريعة",
                            transitionSpeed = "سريعة وحيوية (0.4 ثانية)",
                            cameraMovement = "حركة عائمة هادئة (Parallax Drift)",
                            visualTraits = listOf("نقاء بصري 4K فائق", "إضاءة حواف ذهبية (Rim Light)", "حبيبات سينمائية (Film Grain)"),
                            motionTraits = listOf("انتقالات سريعة", "حركة كاميرا خاطفة"),
                            tags = listOf("ذكاء_اصطناعي", "حماسي", "ريلز")
                        )
                    } else if (prompt.contains("تلاوة") || prompt.contains("قرآن") || prompt.contains("خاشع") || prompt.contains("تأمل")) {
                        StylePresetData(
                            name = "أسلوب ذكي: نوراني خاشع",
                            description = "نمط نوراني روحاني يجمع بين الهدوء والسكينة والوقار في التلاوات والتأملات.",
                            tone = "وقور وروحاني",
                            typography = "خط كوفي عريض في المنتصف مع إبراز الكلمات بالذهبي",
                            captionAnim = "WordByWord",
                            primaryColor = "#E8C547",
                            bgColor = "#0B0F19",
                            lighting = "إضاءة نورانية هادئة مع توهج محيطي ناعم",
                            voice = "تلاوة ترتيلية خاشعة",
                            sfx = "مؤثرات طبيعية هادئة (خرير ماء ونسيم هواء)",
                            transitionSpeed = "وقورة وسينمائية متمهلة (0.9 ثانية)",
                            cameraMovement = "زووم بطيء متصاعد (Slow Cinematic ZoomIn)",
                            visualTraits = listOf("ألوان داكنة DeepSlate #0B0F19", "لمسات ذهبية متوهجة Gold #E8C547", "ضبابية عمق الميدان (Depth of Field)"),
                            motionTraits = listOf("زووم بطيء متصاعد ناعم", "انتقالات تلاشي ناعمة Smooth Dissolve"),
                            tags = listOf("ذكاء_اصطناعي", "قرآني", "خاشع", "نوراني")
                        )
                    } else {
                        StylePresetData(
                            name = "أسلوب ذكي: وثائقي دعوي راقي",
                            description = "نمط متزن وفاخر يجمع بين الفخامة والوضوح والمصداقية الدعوية العالية.",
                            tone = "وثائقي تأملي",
                            typography = "خط رقعة فني فاخر مع توهج ناعم",
                            captionAnim = "FadeInOut",
                            primaryColor = "#10B981",
                            bgColor = "#0F291E",
                            lighting = "إضاءة سينمائية دافئة مع تباين عالي 1:10",
                            voice = "صوت قصصي دافئ وسلس",
                            sfx = "صدى مسجدي روحاني ووقور",
                            transitionSpeed = "متوازنة وانسيابية (0.6 ثانية)",
                            cameraMovement = "ثبات سينمائي مع تركيز",
                            visualTraits = listOf("إطارات إسلامية هندسية رقيقة", "تدرجات ذهبية متوهجة", "نقاء بصري 4K فائق"),
                            motionTraits = listOf("انتقالات انسيابية ناعمة", "ثبات وقور"),
                            tags = listOf("ذكاء_اصطناعي", "وثائقي", "دعوي", "فاخر")
                        )
                    }
                    onApplyGeneratedStyle(generated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("توليد وتطبيق", color = DeepSlate, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = TextSecondary)
            }
        }
    )
}
