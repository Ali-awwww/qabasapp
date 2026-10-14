package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import android.net.Uri
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * 🌟 استوديو العمل المتقن الشامل (Unified Masterpiece Studio)
 * يدمج جميع إمكانيات قبس (السيناريو + التعليق الصوتي + لقطات B-Roll + صور FLUX 8K + الكابشنز الحركية + المصغرة + الـ SEO)
 * في تدفق عمل واحد متكامل ينتج عملاً دعوياً متقناً بضغطة زر واحدة.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedMasterStudioScreen(
    onBack: () -> Unit,
    onOpenInProEditor: (List<Scene>, String) -> Unit = { _, _ -> },
    onExportVideo: (List<Scene>, String) -> Unit = { _, _ -> },
    initialTopic: String = ""
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val primaryColor = qabasPrimary()
    val accentColor = qabasAccent()

    // 1. مدخلات المشروع الموحد — 🔑 نستخدم rememberSaveable لحفظ الحالة عند تدوير الشاشة
    var topicText by rememberSaveable { mutableStateOf(initialTopic.ifBlank { "فضل الصلاة على النبي ﷺ وبركتها في تفريج الهموم" }) }
    var selectedRatio by rememberSaveable { mutableStateOf("9:16") } // 9:16, 16:9, 1:1
    var selectedVoice by rememberSaveable { mutableStateOf("ar-SA-HamedNeural") }
    var selectedTone by rememberSaveable { mutableStateOf("خاشع ومؤثر") }
    var selectedVisualModel by rememberSaveable { mutableStateOf("flux") } // flux, broll, hybrid

    // 2. حالة التوليد الموحد
    var isGeneratingMasterpiece by rememberSaveable { mutableStateOf(false) }
    var generationStage by rememberSaveable { mutableStateOf("") }
    var generationProgress by rememberSaveable { mutableFloatStateOf(0f) }

    // 3. المخرجات الموحدة للعمل المتقن
    var generatedScenes by remember { mutableStateOf<List<Scene>>(emptyList()) }
    var voiceAudioPath by remember { mutableStateOf<String?>(null) }
    var posterImageUrl by remember { mutableStateOf<String?>(null) }
    var seoTitle by remember { mutableStateOf("") }
    var seoDescription by remember { mutableStateOf("") }
    var seoHashtags by remember { mutableStateOf("") }
    var isPlayingPreview by remember { mutableStateOf(false) }
    var activePreviewSceneIndex by remember { mutableIntStateOf(0) }

    // 4. التبويب النشط في قمرة العرض (0: العمل المتكامل, 1: المشاهد والمونتاج, 2: البوستر والمصغرة, 3: الصوت, 4: النشر والـ SEO)
    var selectedCockpitTab by rememberSaveable { mutableIntStateOf(0) }

    // 5. محرك التوليد البصري وحركة الكاميرا (FLUX.1 vs Higgsfield AI Video)
    var selectedVisualEngine by rememberSaveable { mutableStateOf("flux") } // "flux" or "higgsfield"
    var selectedCameraMotion by rememberSaveable { mutableStateOf("cinematic") } // "cinematic", "orbit", "drone", "push_in"
    val isHiggsfieldConfigured = remember { HiggsfieldVideoService.isConfigured(context) }

    val voiceOptions = remember {
        listOf(
            "ar-SA-HamedNeural" to "حامد (خاشع سعودي)",
            "ar-EG-ShakirNeural" to "شاكر (رصين مصري)",
            "ar-SA-ZariyahNeural" to "زارية (هادئ ووقور)",
            "ar-AE-HamdanNeural" to "حمدان (إماراتي فخم)"
        )
    }

    val toneOptions = remember {
        listOf("خاشع ومؤثر", "وثائقي تاريخي", "حماسي دعوي", "قصصي تأملي")
    }

    val ratioOptions = remember {
        listOf("9:16" to "ريلز وشورتس (9:16)", "16:9" to "يوتيوب عريض (16:9)", "1:1" to "مربع إنستغرام (1:1)")
    }

    // الدالة المركزية لإنتاج العمل المتقن بالكامل
    val launchMasterpieceGeneration = {
        scope.launch {
            if (topicText.isBlank()) {
                Toast.makeText(context, "يرجى كتابة موضوع أو نص العمل الدعوي", Toast.LENGTH_SHORT).show()
                return@launch
            }

            isGeneratingMasterpiece = true
            generationProgress = 0.05f
            generationStage = "1/5 كتابة السيناريو وتقسيم المشاهد بالذكاء الاصطناعي..."

            try {
                // 1. توليد السيناريو والمشاهد
                delay(600)
                val scriptPrompt = "قم بإخراج عمل دعوي سينمائي متقن بعنوان: $topicText. النبرة: $selectedTone. النسبة: $selectedRatio."
                val rawScenes = PollinationsTextService.generateScript(
                    idea = topicText,
                    style = "Islamic Cinematic Slate & Gold Luxury #0B132B #E8C547",
                    contentType = "ريلز دعوي متقن",
                    tone = selectedTone
                )

                val scenes = if (!rawScenes.isNullOrEmpty()) {
                    rawScenes
                } else {
                    listOf(
                        Scene("المقدمة الخاشعة", "Opening aerial shot of serene mosque at sunrise", 5, transitionType = "Fade"),
                        Scene("العبرة والجوهر", "Close-up of golden calligraphy and light rays", 7, transitionType = "ZoomIn"),
                        Scene("النداء والعمل", "Peaceful silhouette praying at night, deep blue dusk", 6, transitionType = "Crossfade"),
                        Scene("الخاتمة والدعاء", "Atmospheric golden dust particles in ray of light", 4, transitionType = "Fade")
                    )
                }

                generationProgress = 0.30f
                generationStage = "2/5 توليد التعليق الصوتي العصبي النقي (Edge-TTS)..."

                // 2. توليد الصوت
                val fullNarrativeText = scenes.joinToString(" ") { it.title }
                val audioFile = AppServices.generateVoiceover(fullNarrativeText, selectedVoice)
                voiceAudioPath = audioFile

                generationProgress = 0.55f
                if (selectedVisualEngine == "higgsfield") {
                    generationStage = "3/5 توليد لقطات الفيديو السينمائية بالذكاء الاصطناعي (Higgsfield AI)..."
                    val prompt = "Cinematic holy Islamic footage, $topicText, majestic ambient light, 8k, slow motion luxury aesthetic"
                    val higgsfieldClip = if (isHiggsfieldConfigured) {
                        HiggsfieldVideoService.generateTextToVideo(
                            context = context,
                            prompt = prompt,
                            cameraMotion = selectedCameraMotion,
                            durationSeconds = 5,
                            onProgress = { p, msg ->
                                generationProgress = 0.55f + (p * 0.20f)
                                generationStage = "Higgsfield AI: $msg"
                            }
                        )
                    } else null

                    if (higgsfieldClip != null && higgsfieldClip.exists()) {
                        val videoUri = Uri.fromFile(higgsfieldClip).toString()
                        val enrichedScenes = scenes.mapIndexed { idx, sc ->
                            sc.copy(mediaUrl = videoUri)
                        }
                        generatedScenes = enrichedScenes
                    } else {
                        // Fallback smoothly to FLUX.1
                        val enrichedScenes = scenes.mapIndexed { idx, sc ->
                            val p = "Cinematic Islamic footage, ${sc.description.ifBlank { topicText }}, 8k resolution, golden ambient light, bokeh, luxury masterwork"
                            val media = PollinationsAiService.buildImageUrl(p, if (selectedRatio == "9:16") 1080 else 1920, if (selectedRatio == "9:16") 1920 else 1080, "flux")
                            sc.copy(mediaUrl = media)
                        }
                        generatedScenes = enrichedScenes
                    }
                } else {
                    generationStage = "3/5 توليد اللقطات والرسوم الفائقة بدقة 8K (FLUX.1)..."
                    // 3. توليد الخلفيات واللقطات لكل مشهد
                    val enrichedScenes = scenes.mapIndexed { idx, sc ->
                        val prompt = "Cinematic Islamic footage, ${sc.description.ifBlank { topicText }}, 8k resolution, golden ambient light, bokeh, luxury masterwork"
                        val media = PollinationsAiService.buildImageUrl(prompt, if (selectedRatio == "9:16") 1080 else 1920, if (selectedRatio == "9:16") 1920 else 1080, "flux")
                        sc.copy(mediaUrl = media)
                    }
                    generatedScenes = enrichedScenes
                }

                generationProgress = 0.75f
                generationStage = "4/5 تصميم البوستر والمصغرة الفورية 8K..."

                // 4. توليد البوستر والمصغرة
                val posterPrompt = "Islamic Masterpiece Poster Cover, $topicText, majestic mosque dome, golden rays, dark teal slate aesthetic, 8k typography space"
                posterImageUrl = PollinationsAiService.buildImageUrl(posterPrompt, 1080, 1920, "flux")

                generationProgress = 0.90f
                generationStage = "5/5 صياغة حزمة الانتشار والـ SEO والهاشتاغات الفيروسية..."

                // 5. حزمة الـ SEO
                seoTitle = "✨ $topicText | قبس"
                seoDescription = "عمل دعوي سينمائي متقن يتناول $topicText.\n\nإنتاج: استوديو قبس لصناعة المحتوى الإسلامي والقرآن الكريم.\nنفع الله به وجعله في ميزان حسناتكم."
                seoHashtags = "#قبس #محتوى_إسلامي #ريلز #شورتس #دعوة #أثر_لا_ينقطع #اكسبلور #خواطر_إيمانية"

                generationProgress = 1.0f
                generationStage = "اكتمل إنتاج العمل المتقن بالكامل! ✨"
                delay(400)
                isGeneratingMasterpiece = false
                Toast.makeText(context, "تم إنجاز العمل المتقن بنجاح! 🎬✨", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                isGeneratingMasterpiece = false
                Toast.makeText(context, "تم الإنتاج بنمط الأمان: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFF050811),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CutCornerShape(8.dp))
                                .background(Brush.linearGradient(listOf(primaryColor.copy(alpha = 0.25f), primaryColor.copy(alpha = 0.05f))))
                                .border(1.dp, primaryColor.copy(alpha = 0.5f), CutCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Stars, contentDescription = null, tint = primaryColor, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "قمرة الإنتاج المتقن الشامل",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                lineHeight = 15.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(4.dp).background(primaryColor, CircleShape))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "MASTER_ENGINE_X1 // STATUS: NOMINAL",
                                    color = primaryColor.copy(alpha = 0.7f),
                                    fontFamily = RobotoMonoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 7.5.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                },
                actions = {
                    if (generatedScenes.isNotEmpty()) {
                        IconButton(onClick = { launchMasterpieceGeneration() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "إعادة التوليد", tint = primaryColor, modifier = Modifier.size(20.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF030712).copy(alpha = 0.95f))
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // =========================================================================
            // 1. المدخل الموحد والتحكم الأساسي
            // =========================================================================
            GlassmorphicSurface(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFF0B132B).copy(alpha = 0.85f),
                borderColor = primaryColor.copy(alpha = 0.45f),
                shape = CutCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    Row(modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.EditNote, contentDescription = null, tint = primaryColor, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "موضوع أو نص العمل الدعوي",
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                        Surface(
                            color = primaryColor.copy(alpha = 0.15f),
                            shape = CutCornerShape(4.dp),
                            border = BorderStroke(0.6.dp, primaryColor.copy(alpha = 0.5f))
                        ) {
                            Text("ZERO-KEY FLUX + TTS", color = primaryColor, fontFamily = RobotoMonoFont, fontSize = 8.5.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }

                    OutlinedTextField(
                        value = topicText,
                        onValueChange = { topicText = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CutCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedContainerColor = Color(0xFF070C18),
                            unfocusedContainerColor = Color(0xFF070C18),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        placeholder = { Text("اكتب فكرة، آية، حديث، أو موضوع موعظة...", color = Color.Gray, fontFamily = CairoFont, fontSize = 12.sp) },
                        maxLines = 3
                    )

                    // Quick Inspiration Prompts
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val presets = listOf(
                            "فضل سورة الكهف يوم الجمعة",
                            "أثر الاستغفار في تفريج الكربات",
                            "بر الوالدين وأثره في بركة العمر",
                            "عظمة خلق السماوات والأرض",
                            "الصبر عند الشدائد وحسن الظن بالله"
                        )
                        items(presets) { preset ->
                            Surface(
                                onClick = { topicText = preset },
                                shape = CutCornerShape(4.dp),
                                color = Color(0xFF131D33),
                                border = BorderStroke(0.7.dp, Color(0xFF1E293B))
                            ) {
                                Text(
                                    preset,
                                    color = Color(0xFFCBD5E1),
                                    fontFamily = CairoFont,
                                    fontSize = 10.5.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Configuration Pills (Aspect Ratio & Voice)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Aspect Ratio Picker
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📐 أبعاد الفيديو:", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                ratioOptions.forEach { (ratio, _) ->
                                    val isSelected = selectedRatio == ratio
                                    Surface(
                                        onClick = { selectedRatio = ratio },
                                        shape = CutCornerShape(4.dp),
                                        color = if (isSelected) primaryColor else Color(0xFF131D33),
                                        modifier = Modifier.weight(1f).height(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                ratio,
                                                color = if (isSelected) DeepSlate else Color.White,
                                                fontFamily = RobotoMonoFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Voice Selector Picker
                        Column(modifier = Modifier.weight(1.2f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🎙️ القارئ والتعليق:", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(voiceOptions) { (vId, vLabel) ->
                                    val isSelected = selectedVoice == vId
                                    Surface(
                                        onClick = { selectedVoice = vId },
                                        shape = CutCornerShape(4.dp),
                                        color = if (isSelected) primaryColor.copy(alpha = 0.2f) else Color(0xFF131D33),
                                        border = BorderStroke(1.dp, if (isSelected) primaryColor else Color(0xFF1E293B)),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                            Text(
                                                vLabel.split(" ")[0] + " 🔊",
                                                color = if (isSelected) primaryColor else Color.White,
                                                fontFamily = CairoFont,
                                                fontSize = 10.5.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Visual & Video Engine Selection (Cybernetic Glassmorphism)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0C1424), CutCornerShape(6.dp))
                            .border(0.8.dp, Color(0xFF1E293B), CutCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🎬 محرك الإنتاج البصري والفيديو:", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 11.sp)
                            Surface(
                                shape = CutCornerShape(4.dp),
                                color = if (isHiggsfieldConfigured) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f),
                                border = BorderStroke(0.6.dp, if (isHiggsfieldConfigured) Color(0xFF10B981) else Color(0xFFF59E0B))
                            ) {
                                Text(
                                    if (isHiggsfieldConfigured) "Higgsfield متصل 🟢" else "Higgsfield يحتاج مفتاح 🔑",
                                    color = if (isHiggsfieldConfigured) Color(0xFF10B981) else Color(0xFFF59E0B),
                                    fontFamily = CairoFont,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Option 1: FLUX.1
                            val isFluxSelected = selectedVisualEngine == "flux"
                            Surface(
                                onClick = { selectedVisualEngine = "flux" },
                                shape = CutCornerShape(4.dp),
                                color = if (isFluxSelected) primaryColor.copy(alpha = 0.2f) else Color(0xFF131D33),
                                border = BorderStroke(1.dp, if (isFluxSelected) primaryColor else Color(0xFF1E293B)),
                                modifier = Modifier.weight(1f).height(34.dp).testTag("visual_engine_flux")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, tint = if (isFluxSelected) primaryColor else Color.Gray, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("FLUX.1 8K (مجاني)", color = if (isFluxSelected) primaryColor else Color.White, fontFamily = CairoFont, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Option 2: Higgsfield AI
                            val isHiggsSelected = selectedVisualEngine == "higgsfield"
                            Surface(
                                onClick = { selectedVisualEngine = "higgsfield" },
                                shape = CutCornerShape(4.dp),
                                color = if (isHiggsSelected) Color(0xFF00F2FE).copy(alpha = 0.2f) else Color(0xFF131D33),
                                border = BorderStroke(1.dp, if (isHiggsSelected) Color(0xFF00F2FE) else Color(0xFF1E293B)),
                                modifier = Modifier.weight(1.3f).height(34.dp).testTag("visual_engine_higgsfield")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.MovieFilter, contentDescription = null, tint = if (isHiggsSelected) Color(0xFF00F2FE) else Color.Gray, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Higgsfield AI (فيديو سينمائي 🛸)", color = if (isHiggsSelected) Color(0xFF00F2FE) else Color.White, fontFamily = CairoFont, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Camera motion controls if Higgsfield is selected
                        if (selectedVisualEngine == "higgsfield") {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("🎥 حركة الكاميرا السينمائية:", color = Color(0xFF64748B), fontFamily = CairoFont, fontSize = 10.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                val motions = listOf(
                                    "cinematic" to "سينمائي 🎬",
                                    "orbit" to "مداري 🛰️",
                                    "drone" to "درون 🛸",
                                    "push_in" to "تقريب 🔍"
                                )
                                motions.forEach { (mKey, mLabel) ->
                                    val isMSelected = selectedCameraMotion == mKey
                                    Surface(
                                        onClick = { selectedCameraMotion = mKey },
                                        shape = CutCornerShape(3.dp),
                                        color = if (isMSelected) Color(0xFF00F2FE).copy(alpha = 0.15f) else Color(0xFF080D1A),
                                        border = BorderStroke(0.7.dp, if (isMSelected) Color(0xFF00F2FE) else Color(0xFF1E293B)),
                                        modifier = Modifier.weight(1f).height(28.dp).testTag("camera_motion_$mKey")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(mLabel, color = if (isMSelected) Color(0xFF00F2FE) else Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 9.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Master Action Launch Button
                    Button(
                        onClick = { launchMasterpieceGeneration() },
                        enabled = !isGeneratingMasterpiece,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("masterpiece_launch_button"),
                        shape = CutCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor, disabledContainerColor = Color(0xFF1E293B))
                    ) {
                        if (isGeneratingMasterpiece) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = DeepSlate, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جاري بناء العمل المتقن...", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (generatedScenes.isEmpty()) "⚡ توليد العمل المتقن الشامل (فيديو + صوت + بوستر + SEO)" else "🔄 إعادة إنتاج العمل بالكامل",
                                color = DeepSlate,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Live Progress Bar during generation
                    if (isGeneratingMasterpiece) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LinearProgressIndicator(
                                progress = { generationProgress },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = primaryColor,
                                trackColor = Color(0xFF151B2B)
                            )
                            Text(
                                generationStage,
                                color = primaryColor,
                                fontFamily = CairoFont,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // 2. قمرة العرض والتحكم المتكامل في مخرجات العمل (Masterpiece Cockpit)
            // =========================================================================
            if (generatedScenes.isNotEmpty()) {
                // Cockpit Tab Selector
                val tabs = listOf(
                    "✨ العمل الكامل" to Icons.Default.AllInclusive,
                    "🎬 المشاهد والمونتاج" to Icons.Default.MovieFilter,
                    "🖼️ البوستر والمصغرة" to Icons.Default.Image,
                    "🎙️ الصوت والتعليق" to Icons.Default.GraphicEq,
                    "🚀 النشر والـ SEO" to Icons.Default.Share
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(tabs) { idx, (label, icon) ->
                        val isSelected = selectedCockpitTab == idx
                        Surface(
                            onClick = { selectedCockpitTab = idx },
                            shape = CutCornerShape(4.dp),
                            color = if (isSelected) primaryColor else Color(0xFF0B132B).copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, if (isSelected) primaryColor else Color(0xFF1E293B)),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = if (isSelected) DeepSlate else primaryColor.copy(alpha = 0.8f),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    label,
                                    color = if (isSelected) DeepSlate else Color.White.copy(alpha = 0.85f),
                                    fontFamily = CairoFont,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Tab Content Rendering
                when (selectedCockpitTab) {
                    0 -> MasterFullPackageTab(
                        topic = topicText,
                        scenes = generatedScenes,
                        posterUrl = posterImageUrl,
                        seoTitle = seoTitle,
                        seoHashtags = seoHashtags,
                        onOpenProEditor = { onOpenInProEditor(generatedScenes, topicText) },
                        onExportVideo = { onExportVideo(generatedScenes, topicText) },
                        primaryColor = primaryColor,
                        accentColor = accentColor
                    )
                    1 -> MasterScenesEditorTab(
                        scenes = generatedScenes,
                        onUpdateScenes = { generatedScenes = it },
                        onOpenProEditor = { onOpenInProEditor(generatedScenes, topicText) },
                        primaryColor = primaryColor
                    )
                    2 -> MasterPosterThumbnailTab(
                        topic = topicText,
                        posterUrl = posterImageUrl,
                        ratio = selectedRatio,
                        onRegeneratePoster = {
                            val prompt = "Islamic Masterpiece Poster Cover, $topicText, majestic mosque dome, golden rays, dark slate luxury, 8k"
                            posterImageUrl = PollinationsAiService.buildImageUrl(prompt, 1080, 1920, "flux", System.currentTimeMillis())
                            Toast.makeText(context, "جاري تحديث البوستر 🔄", Toast.LENGTH_SHORT).show()
                        },
                        primaryColor = primaryColor
                    )
                    3 -> MasterVoiceTrackTab(
                        scenes = generatedScenes,
                        audioPath = voiceAudioPath,
                        selectedVoice = selectedVoice,
                        onReSynthesize = { voice ->
                            scope.launch {
                                selectedVoice = voice
                                val fullText = generatedScenes.joinToString(" ") { it.title }
                                voiceAudioPath = AppServices.generateVoiceover(fullText, voice)
                                Toast.makeText(context, "تم تحديث الصوت بنجاح ✓", Toast.LENGTH_SHORT).show()
                            }
                        },
                        primaryColor = primaryColor
                    )
                    4 -> MasterViralSeoTab(
                        title = seoTitle,
                        description = seoDescription,
                        hashtags = seoHashtags,
                        onCopyAll = {
                            val pack = "$seoTitle\n\n$seoDescription\n\n$seoHashtags"
                            clipboardManager.setText(AnnotatedString(pack))
                            Toast.makeText(context, "تم نسخ حزمة النشر بالكامل للحافظة 📋✨", Toast.LENGTH_SHORT).show()
                        },
                        primaryColor = primaryColor
                    )
                }
            }
        }
    }
}

// =============================================================================
// SUB-TABS FOR THE MASTERPIECE COCKPIT
// =============================================================================

@Composable
private fun MasterFullPackageTab(
    topic: String,
    scenes: List<Scene>,
    posterUrl: String?,
    seoTitle: String,
    seoHashtags: String,
    onOpenProEditor: () -> Unit,
    onExportVideo: () -> Unit,
    primaryColor: Color,
    accentColor: Color
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Master Live Preview Card
        GlassmorphicSurface(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = Color(0xFF090F1E),
            borderColor = primaryColor.copy(alpha = 0.5f),
            shape = CutCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("معاينة العمل النهائي المتقن", color = primaryColor, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        shape = CutCornerShape(4.dp),
                        border = BorderStroke(0.6.dp, Color(0xFF10B981))
                    ) {
                        Text("READY TO EXPORT ✦", color = Color(0xFF10B981), fontFamily = RobotoMonoFont, fontSize = 8.5.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scene Gallery Preview Row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(scenes) { index, scene ->
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(170.dp)
                                .clip(CutCornerShape(8.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, if (index == 0) primaryColor else Color(0xFF1E293B), CutCornerShape(8.dp))
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(scene.mediaUrl).crossfade(true).build(),
                                contentDescription = scene.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                            )
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp)
                            ) {
                                Text("مشهد ${index + 1}", color = primaryColor, fontSize = 9.sp, fontFamily = RobotoMonoFont, fontWeight = FontWeight.Bold)
                                Text(scene.title, color = Color.White, fontSize = 10.sp, fontFamily = CairoFont, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Summary Stats Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF101726), CutCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("عدد المشاهد", color = Color.Gray, fontSize = 10.sp, fontFamily = CairoFont)
                        Text("${scenes.size} مشاهد", color = Color.White, fontSize = 12.sp, fontFamily = RobotoMonoFont, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("المدة الإجمالية", color = Color.Gray, fontSize = 10.sp, fontFamily = CairoFont)
                        Text("${scenes.sumOf { it.durationInSeconds }} ثانية", color = primaryColor, fontSize = 12.sp, fontFamily = RobotoMonoFont, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("جودة الإنتاج", color = Color.Gray, fontSize = 10.sp, fontFamily = CairoFont)
                        Text(if (scenes.any { it.mediaUrl?.contains("mp4") == true || it.mediaUrl?.contains("higgsfield") == true }) "Higgsfield 🎬" else "8K FLUX.1", color = Color(0xFF00F2FE), fontSize = 11.5.sp, fontFamily = RobotoMonoFont, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Primary Quick Action Buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onExportVideo,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = CutCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تصدير ورندرة الفيديو 🎬", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onOpenProEditor,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = CutCornerShape(8.dp),
                        border = BorderStroke(1.dp, accentColor),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF0F172A))
                    ) {
                        Icon(Icons.Default.MovieFilter, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("المحرر والتايم لاين ✂️", color = accentColor, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MasterScenesEditorTab(
    scenes: List<Scene>,
    onUpdateScenes: (List<Scene>) -> Unit,
    onOpenProEditor: () -> Unit,
    primaryColor: Color
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("المشاهد المقسمة والكابشنز الحركية", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            TextButton(onClick = onOpenProEditor) {
                Text("فتح في التايم لاين المتقدم ↗", color = primaryColor, fontFamily = CairoFont, fontSize = 11.sp)
            }
        }

        scenes.forEachIndexed { index, scene ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = CutCornerShape(8.dp),
                color = Color(0xFF0C1424),
                border = BorderStroke(0.8.dp, Color(0xFF1E293B))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CutCornerShape(6.dp))
                            .background(Color(0xFF060B14))
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(scene.mediaUrl).crossfade(true).build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("المشهد ${index + 1}", color = primaryColor, fontFamily = RobotoMonoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("(${scene.durationInSeconds} ثوانٍ)", color = Color.Gray, fontFamily = RobotoMonoFont, fontSize = 10.sp)
                        }
                        Text(
                            text = scene.title,
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        color = Color(0xFF1E293B),
                        shape = CutCornerShape(4.dp)
                    ) {
                        Text(scene.transitionType, color = Color(0xFF94A3B8), fontFamily = RobotoMonoFont, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MasterPosterThumbnailTab(
    topic: String,
    posterUrl: String?,
    ratio: String,
    onRegeneratePoster: () -> Unit,
    primaryColor: Color
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("البوستر والغلاف الفوري بدقة 8K", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            OutlinedButton(
                onClick = onRegeneratePoster,
                shape = CutCornerShape(6.dp),
                border = BorderStroke(0.8.dp, primaryColor),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = primaryColor, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("توليد غلاف آخر", color = primaryColor, fontFamily = CairoFont, fontSize = 11.sp)
            }
        }

        Box(
            modifier = Modifier
                .width(if (ratio == "16:9") 320.dp else 220.dp)
                .height(if (ratio == "16:9") 180.dp else 320.dp)
                .clip(CutCornerShape(12.dp))
                .background(Color(0xFF090E1A))
                .border(1.2.dp, primaryColor.copy(alpha = 0.6f), CutCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (!posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(posterUrl).crossfade(true).build(),
                    contentDescription = topic,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text("جاري تحميل البوستر...", color = Color.Gray, fontFamily = CairoFont, fontSize = 12.sp)
            }
        }

        Text("تم توليد هذا الغلاف بتناغم بصري كامل مع ألوان وموضوع الفيديو لنشره كمصغرة يوتيوب أو بوستر إنستغرام.", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 11.5.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MasterVoiceTrackTab(
    scenes: List<Scene>,
    audioPath: String?,
    selectedVoice: String,
    onReSynthesize: (String) -> Unit,
    primaryColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("مسار التعليق الصوتي العصبي النقي", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CutCornerShape(10.dp),
            color = Color(0xFF0C1424),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = primaryColor, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("القارئ: $selectedVoice", color = Color.White, fontFamily = RobotoMonoFont, fontSize = 12.sp)
                    }
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        shape = CutCornerShape(4.dp)
                    ) {
                        Text("STUDIO QUALITY", color = Color(0xFF10B981), fontFamily = RobotoMonoFont, fontSize = 8.5.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }

                Text(
                    text = scenes.joinToString(" ") { it.title },
                    color = Color(0xFFCBD5E1),
                    fontFamily = CairoFont,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun MasterViralSeoTab(
    title: String,
    description: String,
    hashtags: String,
    onCopyAll: () -> Unit,
    primaryColor: Color
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("حزمة النشر والـ SEO والانتشار", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Button(
                onClick = onCopyAll,
                shape = CutCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("نسخ الحزمة كاملة", color = DeepSlate, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Title Box
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CutCornerShape(8.dp),
            color = Color(0xFF0C1424),
            border = BorderStroke(0.8.dp, Color(0xFF1E293B))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text("العنوان المقترح للريلز والشورتس:", color = primaryColor, fontFamily = CairoFont, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(title, color = Color.White, fontFamily = CairoFont, fontSize = 13.sp)
            }
        }

        // Hashtags Box
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CutCornerShape(8.dp),
            color = Color(0xFF0C1424),
            border = BorderStroke(0.8.dp, Color(0xFF1E293B))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text("الهاشتاغات الفيروسية المتوافقة مع خوارزميات النشر:", color = primaryColor, fontFamily = CairoFont, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(hashtags, color = Color(0xFF38BDF8), fontFamily = RobotoMonoFont, fontSize = 11.5.sp)
            }
        }
    }
}
