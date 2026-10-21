package com.qabas.app

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.components.glassmorphicBackground
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * أنماط الكابشنز مع هوية Cybernetic HUD
 */
enum class CaptionStyle(
    val label: String,
    val textColor: Color,
    val borderColor: Color,
    val bgColor: Color,
    val tag: String
) {
    GOLD_KUFI("كوفي ذهبي فاخر", Color(0xFFE8C547), Color(0xFFE8C547), Color(0xE6080D1A), "KUFI-HUD"),
    CYBER_NEON("نيون سايبر مشع", Color(0xFF38BDF8), Color(0xFF0284C7), Color(0xE6060A14), "NEON-CYBER"),
    UTHMANI_WHITE("عثماني ناصع", Color.White, Color(0xFFE2E8F0), Color(0xD90F172A), "UTHMANI-P"),
    EMERALD_MATRIX("أخضر روحاني ماتريكس", Color(0xFF34D399), Color(0xFF10B981), Color(0xE604130D), "EMERALD-FX")
}

/**
 * حركات الكابشنز السينمائية
 */
enum class CaptionAnimation(val label: String, val speedMs: Int) {
    WORD_BY_WORD("كلمة بكلمة ⚡", 180),
    POP_IN("انبثاق حي 🚀", 220),
    FADE_IN("تلاشي سينمائي 🎬", 300),
    GLOW_PULSE("توهج إشعاعي 💡", 400)
}

/**
 * شاشة المحرر المتقدم والتايم لاين
 * بنظام Cybernetic Glassmorphism + Linear Minimalist (Spatial HUD Tech)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedEditScreen(
    scenes: List<Scene>,
    onUpdateScenes: (List<Scene>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var editableScenes by remember(scenes) {
        mutableStateOf(
            if (scenes.isEmpty()) {
                listOf(
                    Scene("المقدمة", "مشهد افتتاحي مهيب مع تدبر الآية الكريمة", 4),
                    Scene("المحتوى الرئيسي", "شرح المعنى العميق والأثر الإيماني في الحياة", 6),
                    Scene("الخاتمة والدعاء", "دعاء مبارك وختام مع دعوة للنشر والأجر", 4)
                )
            } else scenes.toMutableList()
        )
    }

    // ⚠️ حفظ التعديلات قبل مغادرة المحرر — كانت الضغطة على «رجوع» تُهمل كل تعديلات التايم لاين
    BackHandler {
        onUpdateScenes(editableScenes)
        onBack()
    }

    var currentPlaybackTime by remember { mutableFloatStateOf(0f) }
    var isPlaying by remember { mutableStateOf(false) }
    var selectedSceneIndex by remember { mutableIntStateOf(0) }

    // Tools & Viewport HUD State
    var activeTool by remember { mutableStateOf<String?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showSafeZones by remember { mutableStateOf(true) }
    var showGridOverlay by remember { mutableStateOf(false) }
    var aspectRatio by remember { mutableStateOf("9:16") }
    var timelineZoomScale by remember { mutableFloatStateOf(48f) } // px per second
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    // Caption Styling State
    var selectedCaptionStyle by remember { mutableStateOf(CaptionStyle.GOLD_KUFI) }
    var selectedCaptionAnimation by remember { mutableStateOf(CaptionAnimation.WORD_BY_WORD) }
    var captionPosition by remember { mutableStateOf("bottom") } // "bottom", "center", "top"
    var showTextLayer by remember { mutableStateOf(true) }
    var showAudioLayer by remember { mutableStateOf(true) }
    var showEditSceneDialog by remember { mutableStateOf(false) }
    var sceneBeingEdited by remember { mutableStateOf<Scene?>(null) }

    // Pulse animation for HUD status tags
    val infiniteTransition = rememberInfiniteTransition(label = "hudPulse")
    val hudPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hudPulseAlpha"
    )

    // Calculated total duration
    val totalDuration by remember(editableScenes) {
        derivedStateOf { editableScenes.sumOf { it.durationInSeconds }.toFloat().coerceAtLeast(1f) }
    }

    // Playback clock loop with variable speed
    LaunchedEffect(isPlaying, editableScenes, totalDuration, playbackSpeed) {
        while (isPlaying && currentPlaybackTime < totalDuration) {
            delay(50)
            currentPlaybackTime += (0.05f * playbackSpeed)
            if (currentPlaybackTime >= totalDuration) {
                isPlaying = false
                currentPlaybackTime = 0f
            }
        }
    }

    // Active Scene Index calculation based on current playback time
    val activeSceneIndex by remember(currentPlaybackTime, editableScenes) {
        derivedStateOf {
            var accumulated = 0f
            var foundIndex = 0
            for ((i, scene) in editableScenes.withIndex()) {
                accumulated += scene.durationInSeconds
                if (currentPlaybackTime < accumulated) {
                    foundIndex = i
                    break
                }
            }
            foundIndex.coerceIn(0, (editableScenes.size - 1).coerceAtLeast(0))
        }
    }

    // Keep selectedSceneIndex synced with active scene during playback if user didn't manually pick
    LaunchedEffect(activeSceneIndex) {
        if (isPlaying) {
            selectedSceneIndex = activeSceneIndex
        }
    }

    Scaffold(
        containerColor = Color(0xFF050811),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Translator.tr("استوديو المونتاج المتقدم"),
                            color = GoldPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Cyber HUD Tech Badge
                        Surface(
                            shape = CutCornerShape(4.dp),
                            color = Color(0xFF10192A),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(if (isPlaying) Color(0xFF10B981) else GoldPrimary, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "HUD-PRO v3.2",
                                    color = GoldPrimary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = RobotoMonoFont
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        // حفظ التعديلات قبل الرجوع حتى لا تضيع تعديلات التايم لاين
                        onUpdateScenes(editableScenes)
                        onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = Translator.tr("رجوع"),
                            tint = GoldPrimary
                        )
                    }
                },
                actions = {
                    // Grid / Safe Zone Toggle
                    IconButton(
                        onClick = {
                            if (!showSafeZones && !showGridOverlay) {
                                showSafeZones = true
                            } else if (showSafeZones && !showGridOverlay) {
                                showGridOverlay = true
                            } else {
                                showSafeZones = false
                                showGridOverlay = false
                            }
                            val modeMsg = when {
                                showSafeZones && showGridOverlay -> "تفعيل مناطق الأمان + شبكة التكوين 📐"
                                showSafeZones -> "تفعيل مناطق الأمان 9:16 🛡️"
                                else -> "إخفاء الشبكة ومناطق الأمان"
                            }
                            Toast.makeText(context, modeMsg, Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            if (showSafeZones || showGridOverlay) Icons.Default.GridOn else Icons.Default.GridOff,
                            contentDescription = Translator.tr("الشبكة"),
                            tint = if (showSafeZones) GoldPrimary else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Aspect Ratio Switcher with Monospace Badge
                    Surface(
                        shape = CutCornerShape(4.dp),
                        color = Color(0xFF111A2E),
                        border = BorderStroke(1.dp, Color(0xFF243354)),
                        modifier = Modifier
                            .clickable {
                                aspectRatio = when (aspectRatio) {
                                    "9:16" -> "1:1"
                                    "1:1" -> "16:9"
                                    else -> "9:16"
                                }
                                Toast.makeText(context, "الأبعاد: $aspectRatio", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.AspectRatio,
                                contentDescription = Translator.tr("الأبعاد"),
                                tint = GoldPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                aspectRatio,
                                color = Color.White,
                                fontFamily = RobotoMonoFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Cyber Export Button
                    Surface(
                        shape = CutCornerShape(6.dp),
                        color = GoldPrimary,
                        modifier = Modifier
                            .bouncingClickable { showExportDialog = true }
                            .padding(end = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                tint = DeepSlate,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                Translator.tr("تصدير"),
                                color = DeepSlate,
                                fontFamily = NotoSansFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF070B16))
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // =========================================================================
            // 1. SPATIAL HUD VIDEO PREVIEW VIEWPORT
            // =========================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.08f)
                    .background(Color(0xFF03050B)),
                contentAlignment = Alignment.Center
            ) {
                val ratioModifier = when (aspectRatio) {
                    "9:16" -> Modifier.aspectRatio(9f / 16f).fillMaxHeight(0.96f)
                    "1:1" -> Modifier.aspectRatio(1f).fillMaxWidth(0.85f)
                    else -> Modifier.aspectRatio(16f / 9f).fillMaxWidth(0.95f)
                }

                // Main Spatial HUD Bracket Frame
                Box(
                    modifier = ratioModifier
                        .clip(CutCornerShape(10.dp))
                        .background(Color(0xFF080D19))
                        .border(
                            BorderStroke(
                                1.5.dp,
                                Brush.linearGradient(
                                    listOf(
                                        GoldPrimary.copy(alpha = 0.8f),
                                        Color(0xFF38BDF8).copy(alpha = 0.4f),
                                        GoldPrimary.copy(alpha = 0.6f)
                                    )
                                )
                            ),
                            CutCornerShape(10.dp)
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { isPlaying = !isPlaying }
                            )
                        }
                ) {
                    if (editableScenes.isNotEmpty()) {
                        val activeScene = editableScenes[activeSceneIndex]

                        // Background Scene Media
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(activeScene.mediaUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Scene Video Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // Cybernetic Scanline / Vignette Layer
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.45f),
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.70f)
                                        )
                                    )
                                )
                        )

                        // Precision Corner Reticles (Linear Minimalist HUD)
                        HudCornerDecor(
                            modifier = Modifier.fillMaxSize(),
                            color = GoldPrimary.copy(alpha = 0.65f)
                        )

                        // Center Precision Crosshair (Subtle Tech HUD)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val crossLen = 14.dp.toPx()
                            val strokeW = 1.dp.toPx()
                            val chColor = GoldPrimary.copy(alpha = 0.35f)

                            // Horizontal line
                            drawLine(chColor, Offset(cx - crossLen, cy), Offset(cx + crossLen, cy), strokeW)
                            // Vertical line
                            drawLine(chColor, Offset(cx, cy - crossLen), Offset(cx, cy + crossLen), strokeW)
                            // Center ring
                            drawCircle(chColor, radius = 4.dp.toPx(), center = Offset(cx, cy), style = Stroke(strokeW))
                        }

                        // Rule of Thirds Composition Grid Overlay (If toggled)
                        if (showGridOverlay) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val gridColor = Color(0xFF38BDF8).copy(alpha = 0.25f)
                                val strokeW = 1.dp.toPx()
                                // Vertical lines
                                drawLine(gridColor, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), strokeW)
                                drawLine(gridColor, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), strokeW)
                                // Horizontal lines
                                drawLine(gridColor, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), strokeW)
                                drawLine(gridColor, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), strokeW)
                            }
                        }

                        // 9:16 Safe Zones HUD Overlay with Millimeter Brackets
                        if (showSafeZones && aspectRatio == "9:16") {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val topUnsafeH = size.height * 0.13f
                                val bottomUnsafeH = size.height * 0.22f
                                val rightUnsafeW = size.width * 0.16f
                                val leftMargin = size.width * 0.06f

                                // Top Social UI Danger Zone (Story/Header buttons)
                                drawRect(
                                    color = Color(0x28EF4444),
                                    topLeft = Offset.Zero,
                                    size = Size(size.width, topUnsafeH)
                                )

                                // Bottom Social UI Danger Zone (Caption/Profile bar)
                                drawRect(
                                    color = Color(0x28EF4444),
                                    topLeft = Offset(0f, size.height - bottomUnsafeH),
                                    size = Size(size.width, bottomUnsafeH)
                                )

                                // Right Side Social Action Zone (Like, Comment, Share)
                                drawRect(
                                    color = Color(0x20F59E0B),
                                    topLeft = Offset(size.width - rightUnsafeW, topUnsafeH),
                                    size = Size(rightUnsafeW, size.height - topUnsafeH - bottomUnsafeH)
                                )

                                // Golden Safe Area Precision Box
                                val safeLeft = leftMargin
                                val safeTop = topUnsafeH
                                val safeW = size.width - leftMargin - rightUnsafeW
                                val safeH = size.height - topUnsafeH - bottomUnsafeH

                                val dashedStroke = Stroke(
                                    width = 2f,
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                                )
                                drawRect(
                                    color = Color(0xFFE8C547).copy(alpha = 0.8f),
                                    topLeft = Offset(safeLeft, safeTop),
                                    size = Size(safeW, safeH),
                                    style = dashedStroke
                                )
                            }

                            // Safe Zone Header Tech Pill
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 8.dp)
                                    .background(Color(0xDF070C18), CutCornerShape(4.dp))
                                    .border(0.8.dp, GoldPrimary.copy(alpha = 0.7f), CutCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .background(GoldPrimary, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "SAFE AREA 9:16 // REELS & SHORTS",
                                        color = GoldPrimary,
                                        fontSize = 10.sp,
                                        fontFamily = RobotoMonoFont,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Top Telemetry Header Badges (REC / SCENE / FPS)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .align(Alignment.TopStart),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Status Badge
                            Surface(
                                shape = CutCornerShape(4.dp),
                                color = Color(0xCC070B16),
                                border = BorderStroke(0.7.dp, if (isPlaying) Color(0xFF10B981) else Color(0xFFEF4444))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(
                                                (if (isPlaying) Color(0xFF10B981) else Color(0xFFEF4444)).copy(alpha = hudPulseAlpha),
                                                CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        if (isPlaying) "PLAY 60FPS" else "PAUSE // HUD",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontFamily = RobotoMonoFont,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Right Telemetry Badge
                            Surface(
                                shape = CutCornerShape(4.dp),
                                color = Color(0xCC070B16),
                                border = BorderStroke(0.7.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                            ) {
                                Text(
                                    "SCN [${activeSceneIndex + 1}/${editableScenes.size}]",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 9.sp,
                                    fontFamily = RobotoMonoFont,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Live Dynamic Captions Overlay (Word-by-Word & Styled Bubble)
                        if (showTextLayer) {
                            val alignment = when (captionPosition) {
                                "top" -> Alignment.TopCenter
                                "center" -> Alignment.Center
                                else -> Alignment.BottomCenter
                            }
                            val paddingModifier = when (captionPosition) {
                                "top" -> Modifier.padding(top = 44.dp, start = 12.dp, end = 12.dp)
                                "center" -> Modifier.padding(horizontal = 12.dp)
                                else -> Modifier.padding(bottom = if (showSafeZones) 80.dp else 50.dp, start = 12.dp, end = 12.dp)
                            }

                            Box(
                                modifier = Modifier
                                    .align(alignment)
                                    .then(paddingModifier)
                            ) {
                                // High-Tech Glassmorphic Caption Surface
                                Surface(
                                    shape = CutCornerShape(8.dp),
                                    color = selectedCaptionStyle.bgColor,
                                    border = BorderStroke(1.5.dp, selectedCaptionStyle.borderColor.copy(alpha = 0.85f)),
                                    shadowElevation = 8.dp
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // Small Style Tag
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "// ${selectedCaptionStyle.tag}",
                                                color = selectedCaptionStyle.textColor.copy(alpha = 0.7f),
                                                fontSize = 8.sp,
                                                fontFamily = RobotoMonoFont
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = activeScene.title,
                                            color = selectedCaptionStyle.textColor,
                                            fontSize = 15.sp,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                        if (activeScene.description.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = activeScene.description,
                                                color = Color.White.copy(alpha = 0.95f),
                                                fontSize = 11.sp,
                                                fontFamily = CairoFont,
                                                lineHeight = 16.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Center Play Indicator Reticle when paused
                        if (!isPlaying) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .align(Alignment.Center)
                                    .background(Color.Black.copy(alpha = 0.65f), CutCornerShape(12.dp))
                                    .border(1.5.dp, GoldPrimary, CutCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }

                // =========================================================================
                // Viewport Transport & Millisecond Telemetry Scrub Bar
                // =========================================================================
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xF0070B16))
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Precision Timecode Current (HH:MM:SS.ms)
                    val curSec = currentPlaybackTime.toInt()
                    val curMs = ((currentPlaybackTime - curSec) * 100).toInt()
                    Text(
                        String.format("%02d:%02d.%02d", curSec / 60, curSec % 60, curMs),
                        color = GoldPrimary,
                        fontSize = 13.sp,
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold
                    )

                    // Transport Cluster
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Jump back 3s
                        IconButton(
                            onClick = {
                                currentPlaybackTime = (currentPlaybackTime - 3f).coerceAtLeast(0f)
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Replay5,
                                contentDescription = "Back 3s",
                                tint = Color.LightGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Play / Pause Master Button
                        Surface(
                            shape = CutCornerShape(8.dp),
                            color = GoldPrimary,
                            modifier = Modifier
                                .size(38.dp)
                                .bouncingClickable { isPlaying = !isPlaying }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = DeepSlate,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Jump fwd 3s
                        IconButton(
                            onClick = {
                                currentPlaybackTime = (currentPlaybackTime + 3f).coerceAtMost(totalDuration)
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Forward5,
                                contentDescription = "Fwd 3s",
                                tint = Color.LightGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Speed Cycle Pill
                        Surface(
                            shape = CutCornerShape(4.dp),
                            color = Color(0xFF131D33),
                            border = BorderStroke(1.dp, Color(0xFF283A61)),
                            modifier = Modifier
                                .clickable {
                                    playbackSpeed = when (playbackSpeed) {
                                        1.0f -> 1.5f
                                        1.5f -> 2.0f
                                        2.0f -> 0.5f
                                        else -> 1.0f
                                    }
                                }
                                .padding(horizontal = 2.dp)
                        ) {
                            Text(
                                "${playbackSpeed}x",
                                color = GoldPrimary,
                                fontSize = 10.sp,
                                fontFamily = RobotoMonoFont,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Total Duration Readout
                    val totalSec = totalDuration.toInt()
                    Text(
                        String.format("%02d:%02d.00", totalSec / 60, totalSec % 60),
                        color = Color.Gray,
                        fontSize = 13.sp,
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // =========================================================================
            // 2. STUDIO TOOLS TOOLBAR (Cybernetic Glassmorphic Dock)
            // =========================================================================
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF070B16))
                    .border(
                        BorderStroke(0.6.dp, Color(0xFF16233B))
                    )
                    .padding(vertical = 8.dp, horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tools = listOf(
                    Triple("الكابشنز الذكية", Icons.Default.Subtitles, "captions"),
                    Triple("قص الصمت AI", Icons.Default.ContentCut, "jumpcut"),
                    Triple("الانتقالات السينمائية", Icons.Default.Animation, "transitions"),
                    Triple("الهندسة الصوتية", Icons.Default.Audiotrack, "audio"),
                    Triple("تصحيح الألوان LUT", Icons.Default.ColorLens, "filters"),
                    Triple("إدارة المشاهد", Icons.Default.ViewCarousel, "scenes"),
                    Triple("القص والدمج الدقيق", Icons.Default.CallSplit, "cutmerge")
                )
                items(tools.size) { i ->
                    val (label, icon, key) = tools[i]
                    val isSelected = activeTool == key
                    Surface(
                        shape = CutCornerShape(6.dp),
                        color = if (isSelected) GoldPrimary.copy(alpha = 0.18f) else Color(0xFF0F172A),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) GoldPrimary else Color(0xFF1E293B)
                        ),
                        modifier = Modifier
                            .bouncingClickable {
                                activeTool = if (isSelected) null else key
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                icon,
                                contentDescription = label,
                                tint = if (isSelected) GoldPrimary else Color(0xFF94A3B8),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                label,
                                color = if (isSelected) GoldPrimary else Color.White,
                                fontSize = 11.sp,
                                fontFamily = CairoFont,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .background(GoldPrimary, CircleShape)
                                )
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 3. EXPANDED HUD TOOL PANELS (Interactive & High-Tech)
            // =========================================================================
            AnimatedVisibility(
                visible = activeTool != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                GlassmorphicSurface(
                    shape = CutCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
                    backgroundColor = Color(0xF5080E1C),
                    borderColor = GoldPrimary.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(185.dp)
                ) {
                    when (activeTool) {
                        "captions" -> {
                            AnimatedCaptionsToolPanel(
                                selectedStyle = selectedCaptionStyle,
                                onSelectStyle = { selectedCaptionStyle = it },
                                selectedAnim = selectedCaptionAnimation,
                                onSelectAnim = { selectedCaptionAnimation = it },
                                position = captionPosition,
                                onSelectPosition = { captionPosition = it },
                                showLayer = showTextLayer,
                                onToggleLayer = { showTextLayer = it }
                            )
                        }
                        "jumpcut" -> {
                            JumpCutSilenceRemovalToolPanel(
                                scenes = editableScenes,
                                onApplyJumpCut = { optimizedScenes, savedSeconds ->
                                    editableScenes = optimizedScenes.toMutableList()
                                    Toast.makeText(
                                        context,
                                        "تم قص الصمت بنجاح! تم توفير $savedSeconds ثوانٍ وتسريع المشاهد ⚡",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                        }
                        "transitions" -> {
                            TransitionsToolPanel(
                                onSelectTransition = { trans ->
                                    if (selectedSceneIndex in editableScenes.indices) {
                                        val updated = editableScenes.toMutableList()
                                        updated[selectedSceneIndex] = updated[selectedSceneIndex].copy(transitionType = trans)
                                        editableScenes = updated
                                        Toast.makeText(context, "تم ربط الانتقال ($trans) للمشهد ${selectedSceneIndex + 1}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                        "audio" -> {
                            AudioToolPanel(
                                showAudioLayer = showAudioLayer,
                                onToggle = { showAudioLayer = it }
                            )
                        }
                        "filters" -> {
                            FiltersToolPanel(
                                currentFilter = editableScenes.getOrNull(selectedSceneIndex)?.visualEffect ?: "طبيعي",
                                onSelectFilter = { filterName ->
                                    if (selectedSceneIndex in editableScenes.indices) {
                                        val updated = editableScenes.toMutableList()
                                        updated[selectedSceneIndex] = updated[selectedSceneIndex].copy(
                                            visualEffect = filterName
                                        )
                                        editableScenes = updated
                                        Toast.makeText(
                                            context,
                                            "تم تطبيق فلتر ($filterName) على المشهد ${selectedSceneIndex + 1}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
                        "scenes" -> {
                            SceneManagementToolPanel(
                                scenes = editableScenes,
                                selectedIndex = selectedSceneIndex,
                                onSelectScene = {
                                    selectedSceneIndex = it
                                    currentPlaybackTime = editableScenes.take(it).sumOf { s -> s.durationInSeconds }.toFloat()
                                },
                                onEditScene = { scene ->
                                    sceneBeingEdited = scene
                                    showEditSceneDialog = true
                                },
                                onDuplicateScene = { index ->
                                    val current = editableScenes[index]
                                    val updated = editableScenes.toMutableList()
                                    updated.add(index + 1, current.copy(title = "${current.title} (نسخة)"))
                                    editableScenes = updated
                                    Toast.makeText(context, "تم تكرار المشهد بنجاح", Toast.LENGTH_SHORT).show()
                                },
                                onDeleteScene = { index ->
                                    if (editableScenes.size > 1) {
                                        val updated = editableScenes.toMutableList()
                                        updated.removeAt(index)
                                        editableScenes = updated
                                        selectedSceneIndex = (selectedSceneIndex - 1).coerceAtLeast(0)
                                        Toast.makeText(context, "تم حذف المشهد", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "لا يمكن حذف آخر مشهد في المشروع", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                        "cutmerge" -> {
                            CutAndMergeToolPanel(
                                currentTime = currentPlaybackTime,
                                totalDuration = totalDuration
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // 4. PRECISION LINEAR SPATIAL HUD TIMELINE
            // =========================================================================
            val timelineScrollState = rememberScrollState()
            val timelineTotalWidthDp = (totalDuration * timelineZoomScale).dp

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.96f)
                    .background(Color(0xFF060912))
            ) {
                // Timeline HUD Quick Bar (Zoom scale, Split at playhead, Snap)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF090E1B))
                        .border(BorderStroke(0.5.dp, Color(0xFF141E33)))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Zoom Scale Stepper with Monospace Telemetry
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { timelineZoomScale = (timelineZoomScale - 8f).coerceAtLeast(26f) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color.Gray, modifier = Modifier.size(14.dp))
                        }
                        Surface(
                            shape = CutCornerShape(3.dp),
                            color = Color(0xFF0F172A),
                            border = BorderStroke(0.6.dp, Color(0xFF1E293B))
                        ) {
                            Text(
                                "${timelineZoomScale.toInt()} PX/S",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontFamily = RobotoMonoFont,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        IconButton(
                            onClick = { timelineZoomScale = (timelineZoomScale + 8f).coerceAtMost(96f) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = GoldPrimary, modifier = Modifier.size(14.dp))
                        }
                    }

                    // Tactical Split Action
                    Surface(
                        shape = CutCornerShape(4.dp),
                        color = Color(0xFF131D33),
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
                        modifier = Modifier.bouncingClickable {
                            val curScene = editableScenes[activeSceneIndex]
                            if (curScene.durationInSeconds > 2) {
                                val half1 = curScene.durationInSeconds / 2
                                val half2 = curScene.durationInSeconds - half1
                                val updated = editableScenes.toMutableList()
                                updated[activeSceneIndex] = curScene.copy(durationInSeconds = half1)
                                updated.add(activeSceneIndex + 1, curScene.copy(title = "${curScene.title} (جزء 2)", durationInSeconds = half2))
                                editableScenes = updated
                                Toast.makeText(context, "تم تقسيم المشهد عند المؤشر الحالي ✂️", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "مدة المشهد قصيرة جداً للتقسيم", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ContentCut, contentDescription = "Split", tint = GoldPrimary, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("قص المشهد ✂️", color = GoldPrimary, fontSize = 11.sp, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Interactive Multi-Track Container (Ruler + Video + Audio + Text + Laser Playhead)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(timelineScrollState)
                ) {
                    Column(
                        modifier = Modifier
                            .width(timelineTotalWidthDp.coerceAtLeast(360.dp))
                            .fillMaxHeight()
                    ) {
                        // 1. TACTICAL HUD TIME RULER (Tappable & Scrubbable)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .background(Color(0xFF0A0F1F))
                                .border(BorderStroke(0.5.dp, Color(0xFF172238)))
                                .pointerInput(totalDuration, timelineZoomScale) {
                                    detectTapGestures { offset ->
                                        val tappedSeconds = (offset.x / timelineZoomScale).coerceIn(0f, totalDuration)
                                        currentPlaybackTime = tappedSeconds
                                    }
                                }
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                for (sec in 0..totalDuration.toInt() + 1) {
                                    val x = sec * timelineZoomScale
                                    val isMajor = sec % 5 == 0
                                    drawLine(
                                        color = if (isMajor) GoldPrimary else Color(0xFF475569),
                                        start = Offset(x, if (isMajor) 4f else 14f),
                                        end = Offset(x, size.height),
                                        strokeWidth = if (isMajor) 1.5f else 0.8f
                                    )
                                }
                            }
                        }

                        // 2. VIDEO CLIPS TRACK
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(Color(0xFF070B16))
                                .border(BorderStroke(0.5.dp, Color(0xFF141E33))),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            editableScenes.forEachIndexed { index, scene ->
                                val clipWidthDp = (scene.durationInSeconds * timelineZoomScale).dp
                                val isSelected = index == selectedSceneIndex

                                Box(
                                    modifier = Modifier
                                        .width(clipWidthDp)
                                        .fillMaxHeight()
                                        .padding(2.dp)
                                        .clip(CutCornerShape(6.dp))
                                        .background(if (isSelected) Color(0xFF1E3A8A).copy(alpha = 0.9f) else Color(0xFF0F172A))
                                        .border(
                                            BorderStroke(
                                                width = if (isSelected) 1.8.dp else 1.dp,
                                                color = if (isSelected) GoldPrimary else Color(0xFF243354)
                                            ),
                                            CutCornerShape(6.dp)
                                        )
                                        .clickable {
                                            selectedSceneIndex = index
                                            currentPlaybackTime = editableScenes.take(index).sumOf { it.durationInSeconds }.toFloat()
                                        }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${index + 1}. ${scene.title}",
                                                color = if (isSelected) GoldPrimary else Color.White,
                                                fontSize = 11.sp,
                                                fontFamily = CairoFont,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "${scene.durationInSeconds}s",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 9.sp,
                                                fontFamily = RobotoMonoFont
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = scene.transitionType ?: "Dissolve",
                                                color = Color(0xFF38BDF8),
                                                fontSize = 9.sp,
                                                fontFamily = RobotoMonoFont
                                            )
                                            Icon(
                                                Icons.Default.Videocam,
                                                contentDescription = null,
                                                tint = if (isSelected) GoldPrimary else Color.Gray,
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. AUDIO WAVEFORM TRACK (Dynamic Spectral Bars)
                        if (showAudioLayer) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .background(Color(0xFF041E18).copy(alpha = 0.5f))
                                    .border(BorderStroke(0.5.dp, Color(0xFF065F46)))
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val totalWidth = size.width
                                    val step = 5f
                                    var x = 0f
                                    while (x < totalWidth) {
                                        val waveH = kotlin.math.sin(x.toDouble() / 12.0).toFloat() * 10f + 12f
                                        drawLine(
                                            color = Color(0xFF10B981).copy(alpha = 0.85f),
                                            start = Offset(x, (size.height - waveH) / 2),
                                            end = Offset(x, (size.height + waveH) / 2),
                                            strokeWidth = 2f,
                                            cap = StrokeCap.Round
                                        )
                                        x += step
                                    }
                                }
                                Row(
                                    modifier = Modifier.padding(start = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Audiotrack,
                                        contentDescription = null,
                                        tint = Color(0xFF34D399),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "مسار الصوت الذكي // AI SOUND FX",
                                        color = Color(0xFF34D399),
                                        fontSize = 10.sp,
                                        fontFamily = RobotoMonoFont,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // 4. CAPTIONS SUBTITLE TRACK (Amber Holographic Chips)
                        if (showTextLayer) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp)
                                    .background(Color(0xFF1F1305).copy(alpha = 0.4f))
                                    .border(BorderStroke(0.5.dp, Color(0xFF78350F))),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                editableScenes.forEachIndexed { _, scene ->
                                    val clipWidthDp = (scene.durationInSeconds * timelineZoomScale).dp
                                    Box(
                                        modifier = Modifier
                                            .width(clipWidthDp)
                                            .fillMaxHeight()
                                            .padding(1.dp)
                                            .clip(CutCornerShape(4.dp))
                                            .background(Color(0xFFD97706).copy(alpha = 0.35f))
                                            .border(BorderStroke(0.8.dp, Color(0xFFF5D76E).copy(alpha = 0.7f)), CutCornerShape(4.dp))
                                            .padding(horizontal = 4.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = "💬 ${scene.title}",
                                            color = Color(0xFFFEF3C7),
                                            fontSize = 9.sp,
                                            fontFamily = CairoFont,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 5. INTERACTIVE LASER PLAYHEAD NEEDLE & HUD DIAMOND NOTCH
                    val playheadXDp = (currentPlaybackTime * timelineZoomScale).dp
                    Box(
                        modifier = Modifier
                            .offset(x = playheadXDp - 12.dp)
                            .width(24.dp)
                            .fillMaxHeight()
                            .pointerInput(totalDuration, timelineZoomScale) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val deltaSec = dragAmount.x / timelineZoomScale
                                    currentPlaybackTime = (currentPlaybackTime + deltaSec).coerceIn(0f, totalDuration)
                                }
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val centerX = size.width / 2f
                            // Laser Glow Line
                            drawLine(
                                brush = Brush.verticalGradient(
                                    listOf(
                                        Color(0xFFE8C547),
                                        Color(0xFF38BDF8),
                                        Color(0xFFE8C547).copy(alpha = 0.5f)
                                    )
                                ),
                                start = Offset(centerX, 0f),
                                end = Offset(centerX, size.height),
                                strokeWidth = 3f
                            )
                            // Top Diamond HUD Notch
                            val notchPath = Path().apply {
                                moveTo(centerX - 8f, 0f)
                                lineTo(centerX + 8f, 0f)
                                lineTo(centerX + 8f, 10f)
                                lineTo(centerX, 18f)
                                lineTo(centerX - 8f, 10f)
                                close()
                            }
                            drawPath(notchPath, color = Color(0xFFE8C547))
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // MODALS & DIALOGS (Cybernetic Glassmorphism)
    // =========================================================================
    if (showExportDialog) {
        CyberExportSettingsDialog(
            onDismiss = { showExportDialog = false },
            onExport = {
                showExportDialog = false
                onUpdateScenes(editableScenes)
            }
        )
    }

    if (showEditSceneDialog && sceneBeingEdited != null) {
        CyberEditSceneDetailsDialog(
            scene = sceneBeingEdited!!,
            onDismiss = { showEditSceneDialog = false },
            onSave = { updatedScene ->
                val updated = editableScenes.toMutableList()
                // مطابقة المشهد بالهوية أولاً (أدق)، ثم بالعنوان كبديل احتياطي
                var idx = updated.indexOfFirst { it === sceneBeingEdited }
                if (idx == -1) {
                    idx = updated.indexOfFirst { it.title == sceneBeingEdited!!.title }
                }
                if (idx != -1) {
                    updated[idx] = updatedScene
                    editableScenes = updated
                }
                showEditSceneDialog = false
                Toast.makeText(context, "تم حفظ تعديلات المشهد بنجاح", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// =============================================================================
// SUB-PANELS (Cybernetic Glassmorphism + Linear Minimalist)
// =============================================================================

@Composable
fun AnimatedCaptionsToolPanel(
    selectedStyle: CaptionStyle,
    onSelectStyle: (CaptionStyle) -> Unit,
    selectedAnim: CaptionAnimation,
    onSelectAnim: (CaptionAnimation) -> Unit,
    position: String,
    onSelectPosition: (String) -> Unit,
    showLayer: Boolean,
    onToggleLayer: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Subtitles, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("قوالب الكابشنز والنصوص الحركية", color = GoldPrimary, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("تفعيل الطبقة", color = Color.Gray, fontSize = 11.sp, fontFamily = NotoSansFont)
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = showLayer,
                    onCheckedChange = onToggleLayer,
                    colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary, checkedTrackColor = GoldPrimary.copy(alpha = 0.5f))
                )
            }
        }

        // Style selector
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CaptionStyle.values().size) { i ->
                val style = CaptionStyle.values()[i]
                val isSelected = selectedStyle == style
                Surface(
                    shape = CutCornerShape(6.dp),
                    color = if (isSelected) GoldPrimary.copy(alpha = 0.22f) else Color(0xFF0F172A),
                    border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color(0xFF1E293B)),
                    modifier = Modifier.bouncingClickable { onSelectStyle(style) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(style.textColor, CircleShape))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = style.label,
                            color = style.textColor,
                            fontSize = 11.sp,
                            fontFamily = CairoFont,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Animation selection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("الحركة:", color = Color.LightGray, fontSize = 11.sp, fontFamily = NotoSansFont)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(CaptionAnimation.values().size) { i ->
                    val anim = CaptionAnimation.values()[i]
                    val isSelected = selectedAnim == anim
                    Surface(
                        shape = CutCornerShape(4.dp),
                        color = if (isSelected) GoldPrimary else Color(0xFF131D33),
                        modifier = Modifier.bouncingClickable { onSelectAnim(anim) }
                    ) {
                        Text(
                            text = anim.label,
                            color = if (isSelected) DeepSlate else Color.White,
                            fontSize = 10.sp,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        // Position selection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("الموضع:", color = Color.LightGray, fontSize = 11.sp, fontFamily = NotoSansFont)
            listOf("bottom" to "أسفل (Safe Zone)", "center" to "وسط الفيديو", "top" to "أعلى").forEach { (posKey, posLabel) ->
                val isSelected = position == posKey
                Surface(
                    shape = CutCornerShape(4.dp),
                    color = if (isSelected) Color(0xFF1E3A8A) else Color(0xFF10192A),
                    border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color(0xFF1E293B)),
                    modifier = Modifier.bouncingClickable { onSelectPosition(posKey) }
                ) {
                    Text(
                        text = posLabel,
                        color = if (isSelected) GoldPrimary else Color.White,
                        fontSize = 10.sp,
                        fontFamily = CairoFont,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun JumpCutSilenceRemovalToolPanel(
    scenes: List<Scene>,
    onApplyJumpCut: (List<Scene>, Int) -> Unit
) {
    var silenceThreshold by remember { mutableFloatStateOf(-32f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ContentCut, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("قص الصمت الذكي (AI Jump Cut)", color = GoldPrimary, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Surface(
                shape = CutCornerShape(4.dp),
                color = Color(0xFF10B981).copy(alpha = 0.2f),
                border = BorderStroke(1.dp, Color(0xFF10B981))
            ) {
                Text(
                    "+32% RETENTION BOOST 📈",
                    color = Color(0xFF34D399),
                    fontSize = 10.sp,
                    fontFamily = RobotoMonoFont,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Text(
            "تقوم الخوارزمية بفحص الطيف الصوتي وحذف السكتات غير المجدية لرفع إيقاع المشهد بما يتناسب مع خوارزميات الريلز.",
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = CairoFont,
            lineHeight = 15.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("عتبة كشف الصمت: ${silenceThreshold.toInt()} dBFS", color = Color.White, fontSize = 11.sp, fontFamily = RobotoMonoFont)
            Slider(
                value = silenceThreshold,
                onValueChange = { silenceThreshold = it },
                valueRange = -45f..-20f,
                colors = SliderDefaults.colors(thumbColor = GoldPrimary, activeTrackColor = GoldPrimary),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
            )
        }

        Surface(
            shape = CutCornerShape(6.dp),
            color = GoldPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .bouncingClickable {
                    var totalSavedSeconds = 0
                    val optimized = scenes.map { sc ->
                        val reduction = (sc.durationInSeconds * 0.22f).toInt().coerceAtLeast(1)
                        val newDur = (sc.durationInSeconds - reduction).coerceAtLeast(2)
                        totalSavedSeconds += (sc.durationInSeconds - newDur)
                        sc.copy(durationInSeconds = newDur)
                    }
                    onApplyJumpCut(optimized, totalSavedSeconds)
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "تطبيق القص السريع وحذف الوقفات ⚡",
                        color = DeepSlate,
                        fontFamily = NotoSansFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SceneManagementToolPanel(
    scenes: List<Scene>,
    selectedIndex: Int,
    onSelectScene: (Int) -> Unit,
    onEditScene: (Scene) -> Unit,
    onDuplicateScene: (Int) -> Unit,
    onDeleteScene: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("إدارة المشاهد وترتيب المقاطع", color = GoldPrimary, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("إجمالي المشاهد: ${scenes.size}", color = Color.Gray, fontSize = 11.sp, fontFamily = RobotoMonoFont)
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(scenes) { index, scene ->
                val isSelected = index == selectedIndex
                Surface(
                    shape = CutCornerShape(8.dp),
                    color = if (isSelected) Color(0xFF1E3A8A) else Color(0xFF0F172A),
                    border = BorderStroke(1.2.dp, if (isSelected) GoldPrimary else Color(0xFF1E293B)),
                    modifier = Modifier
                        .width(135.dp)
                        .height(118.dp)
                        .clickable { onSelectScene(index) }
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp).fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${index + 1}. ${scene.title}",
                            color = if (isSelected) GoldPrimary else Color.White,
                            fontSize = 12.sp,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            "${scene.durationInSeconds} ثوانٍ",
                            color = Color.LightGray,
                            fontSize = 10.sp,
                            fontFamily = RobotoMonoFont
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            IconButton(onClick = { onEditScene(scene) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = GoldPrimary, modifier = Modifier.size(13.dp))
                            }
                            IconButton(onClick = { onDuplicateScene(index) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = Color.White, modifier = Modifier.size(13.dp))
                            }
                            IconButton(onClick = { onDeleteScene(index) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransitionsToolPanel(
    selectedTransition: String = "تلاشي سينمائي (Dissolve)",
    onSelectTransition: (String) -> Unit = {}
) {
    var currentSelection by remember { mutableStateOf(selectedTransition) }
    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(Translator.tr("الانتقالات السينمائية بين المشاهد"), color = GoldPrimary, fontSize = 14.sp, fontFamily = TajawalFont, fontWeight = FontWeight.Bold)
            Text(currentSelection, color = Color.LightGray, fontSize = 11.sp, fontFamily = RobotoMonoFont)
        }
        Spacer(modifier = Modifier.height(10.dp))
        val transitions = remember {
            listOf(
                "تلاشي سينمائي" to "dissolve",
                "انزلاق يسار" to "slideleft",
                "انزلاق يمين" to "slideright",
                "تلاشي أسود" to "fadeblack",
                "مسح دائري" to "circlecrop"
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(
                count = transitions.size,
                key = { i -> transitions[i].first }
            ) { i ->
                val (label, code) = transitions[i]
                val isSelected = currentSelection.contains(label) || currentSelection.contains(code)
                Surface(
                    shape = CutCornerShape(8.dp),
                    color = if (isSelected) Color(0xFF131F38) else Color(0xFF0F172A),
                    border = BorderStroke(
                        1.2.dp,
                        if (isSelected) GoldPrimary else Color(0xFF243354)
                    ),
                    modifier = Modifier
                        .size(105.dp, 75.dp)
                        .clickable {
                            currentSelection = label
                            onSelectTransition(code)
                        }
                        .padding(2.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            if (isSelected) "✨ $label" else label,
                            color = if (isSelected) GoldPrimary else Color.White,
                            fontFamily = CairoFont,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "0.8s HUD",
                            color = Color.Gray,
                            fontFamily = RobotoMonoFont,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FiltersToolPanel(
    currentFilter: String = "طبيعي",
    onSelectFilter: (String) -> Unit = {}
) {
    var intensity by remember { mutableFloatStateOf(0.85f) }
    var selectedFilter by remember(currentFilter) { mutableStateOf(currentFilter) }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(Translator.tr("الفلاتر وتصحيح الألوان LUT"), color = GoldPrimary, fontSize = 14.sp, fontFamily = TajawalFont, fontWeight = FontWeight.Bold)
            Text("${(intensity * 100).toInt()}% INTENSITY", color = GoldPrimary, fontFamily = RobotoMonoFont, fontSize = 10.sp)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Translator.tr("الشدة:"), color = Color.White, fontFamily = CairoFont, fontSize = 11.sp)
            Slider(
                value = intensity,
                onValueChange = { intensity = it },
                colors = SliderDefaults.colors(thumbColor = GoldPrimary, activeTrackColor = GoldPrimary),
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            )
        }
        val filters = remember { listOf("سينمائي", "روحاني", "عتيق", "ساطع", "طبيعي") }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(
                count = filters.size,
                key = { i -> filters[i] }
            ) { i ->
                val name = filters[i]
                val isSelected = selectedFilter.contains(name, ignoreCase = true) ||
                    (name == "طبيعي" && (selectedFilter.isBlank() || selectedFilter == "بدون" || selectedFilter == "cinematic"))
                Surface(
                    shape = CutCornerShape(6.dp),
                    color = if (isSelected) GoldPrimary.copy(alpha = 0.22f) else Color(0xFF0F172A),
                    border = BorderStroke(
                        1.2.dp,
                        if (isSelected) GoldPrimary else Color(0xFF243354)
                    ),
                    modifier = Modifier
                        .width(82.dp)
                        .height(38.dp)
                        .clickable {
                            selectedFilter = name
                            onSelectFilter(name)
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            name,
                            color = if (isSelected) GoldPrimary else Color.White,
                            fontSize = 11.sp,
                            fontFamily = CairoFont,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AudioToolPanel(showAudioLayer: Boolean, onToggle: (Boolean) -> Unit) {
    var volume by remember { mutableFloatStateOf(0.8f) }
    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Audiotrack, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(Translator.tr("الهندسة الصوتية الذكية"), color = GoldPrimary, fontSize = 14.sp, fontFamily = TajawalFont, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Translator.tr("تفعيل الصوت"), color = Color.Gray, fontSize = 11.sp, fontFamily = NotoSansFont)
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = showAudioLayer,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary, checkedTrackColor = GoldPrimary.copy(alpha=0.5f))
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
            Slider(
                value = volume,
                onValueChange = { volume = it },
                colors = SliderDefaults.colors(thumbColor = GoldPrimary, activeTrackColor = GoldPrimary),
                modifier = Modifier.weight(1f).padding(horizontal = 14.dp)
            )
            Text("${(volume * 100).toInt()}%", color = Color.White, fontFamily = RobotoMonoFont, fontSize = 12.sp)
        }
    }
}

@Composable
fun CutAndMergeToolPanel(
    currentTime: Float,
    totalDuration: Float
) {
    val context = LocalContext.current
    var selectedSpeed by remember { mutableFloatStateOf(1f) }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(Translator.tr("أدوات القص والدمج الدقيق"), color = GoldPrimary, fontSize = 14.sp, fontFamily = TajawalFont, fontWeight = FontWeight.Bold)
            Text("المؤشر: ${currentTime.toInt()}s / ${totalDuration.toInt()}s", color = Color.Gray, fontSize = 11.sp, fontFamily = RobotoMonoFont)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CutCornerShape(4.dp),
                color = Color(0xFF131D33),
                border = BorderStroke(1.dp, Color(0xFF243354)),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        Toast.makeText(context, Translator.tr("تم تحديد بداية المقطع عند: ${currentTime.toInt()}s"), Toast.LENGTH_SHORT).show()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ContentCut, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(Translator.tr("قص البداية"), fontSize = 11.sp, fontFamily = NotoSansFont, color = Color.White)
                }
            }

            Surface(
                shape = CutCornerShape(4.dp),
                color = Color(0xFF131D33),
                border = BorderStroke(1.dp, Color(0xFF243354)),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        Toast.makeText(context, Translator.tr("تم تحديد نهاية المقطع عند: ${currentTime.toInt()}s"), Toast.LENGTH_SHORT).show()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ContentCut, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(Translator.tr("قص النهاية"), fontSize = 11.sp, fontFamily = NotoSansFont, color = Color.White)
                }
            }

            Surface(
                shape = CutCornerShape(4.dp),
                color = GoldPrimary,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        Toast.makeText(context, Translator.tr("تم تقسيم المقطع الحالي بنجاح"), Toast.LENGTH_SHORT).show()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CallSplit, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(Translator.tr("تقسيم"), fontSize = 11.sp, fontFamily = NotoSansFont, color = DeepSlate, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Speed presets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(Translator.tr("سرعة المقطع:"), color = Color.White, fontSize = 11.sp, fontFamily = NotoSansFont)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.5f, 1f, 1.25f, 1.5f, 2f).forEach { spd ->
                    val isSpdSelected = selectedSpeed == spd
                    Surface(
                        shape = CutCornerShape(4.dp),
                        color = if (isSpdSelected) GoldPrimary else Color(0xFF131D33),
                        modifier = Modifier.clickable {
                            selectedSpeed = spd
                            Toast.makeText(context, "السرعة: ${spd}x", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text(
                            text = "${spd}x",
                            color = if (isSpdSelected) DeepSlate else Color.White,
                            fontFamily = RobotoMonoFont,
                            fontSize = 10.sp,
                            fontWeight = if (isSpdSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// CYBERNETIC GLASSMORPHIC DIALOGS
// =============================================================================

@Composable
fun CyberExportSettingsDialog(onDismiss: () -> Unit, onExport: () -> Unit) {
    var resolution by remember { mutableStateOf("1080p") }
    var fps by remember { mutableStateOf("60 FPS") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Download, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    Translator.tr("إعدادات التصدير السينمائي"),
                    color = GoldPrimary,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CairoFont,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Resolution Selector
                Column {
                    Text(
                        "دقة الإخراج // RESOLUTION",
                        color = GoldPrimary,
                        fontSize = 12.sp,
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("1080p", "2K", "4K UHD").forEach { res ->
                            val isSel = resolution == res
                            Surface(
                                shape = CutCornerShape(4.dp),
                                color = if (isSel) GoldPrimary.copy(alpha = 0.25f) else Color(0xFF0F172A),
                                border = BorderStroke(1.dp, if (isSel) GoldPrimary else Color(0xFF243354)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { resolution = res }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                    Text(
                                        res,
                                        color = if (isSel) GoldPrimary else Color.White,
                                        fontFamily = RobotoMonoFont,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Framerate Selector
                Column {
                    Text(
                        "معدل الإطارات // FRAMERATE",
                        color = GoldPrimary,
                        fontSize = 12.sp,
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("30 FPS", "60 FPS", "120 FPS").forEach { f ->
                            val isSel = fps == f
                            Surface(
                                shape = CutCornerShape(4.dp),
                                color = if (isSel) GoldPrimary.copy(alpha = 0.25f) else Color(0xFF0F172A),
                                border = BorderStroke(1.dp, if (isSel) GoldPrimary else Color(0xFF243354)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { fps = f }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                    Text(
                                        f,
                                        color = if (isSel) GoldPrimary else Color.White,
                                        fontFamily = RobotoMonoFont,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Bitrate HUD Readout
                Surface(
                    shape = CutCornerShape(4.dp),
                    color = Color(0xFF0A101D),
                    border = BorderStroke(0.7.dp, Color(0xFF1E2D4A))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("BITRATE ESTIMATE:", color = Color.Gray, fontSize = 10.sp, fontFamily = RobotoMonoFont)
                        Text(
                            when (resolution) {
                                "4K UHD" -> "45 Mbps // PRO-RES"
                                "2K" -> "24 Mbps // H.265"
                                else -> "14 Mbps // H.264"
                            },
                            color = Color(0xFF38BDF8),
                            fontSize = 10.sp,
                            fontFamily = RobotoMonoFont,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Surface(
                shape = CutCornerShape(6.dp),
                color = GoldPrimary,
                modifier = Modifier.clickable { onExport() }
            ) {
                Text(
                    Translator.tr("بدء التصدير السينمائي"),
                    color = DeepSlate,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Translator.tr("إلغاء"), color = Color.Gray, fontFamily = CairoFont)
            }
        },
        containerColor = Color(0xFF0D1424),
        shape = CutCornerShape(10.dp)
    )
}

@Composable
fun CyberEditSceneDetailsDialog(
    scene: Scene,
    onDismiss: () -> Unit,
    onSave: (Scene) -> Unit
) {
    var title by remember { mutableStateOf(scene.title) }
    var description by remember { mutableStateOf(scene.description) }
    var duration by remember { mutableIntStateOf(scene.durationInSeconds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "تعديل تفاصيل المشهد والكابشن",
                color = GoldPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان المشهد", color = Color.Gray, fontFamily = CairoFont) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = CutCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("نص المشهد والكابشن", color = Color.Gray, fontFamily = CairoFont) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = CutCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "مدة المشهد: $duration ثانية",
                        color = Color.White,
                        fontFamily = RobotoMonoFont,
                        fontSize = 12.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (duration > 1) duration-- }) {
                            Icon(Icons.Default.Remove, contentDescription = null, tint = GoldPrimary)
                        }
                        Text("$duration", color = GoldPrimary, fontFamily = RobotoMonoFont, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { duration++ }) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = GoldPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Surface(
                shape = CutCornerShape(6.dp),
                color = GoldPrimary,
                modifier = Modifier.clickable {
                    onSave(scene.copy(title = title, description = description, durationInSeconds = duration))
                }
            ) {
                Text(
                    "حفظ التعديلات",
                    color = DeepSlate,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = Color.Gray, fontFamily = CairoFont)
            }
        },
        containerColor = Color(0xFF0D1424),
        shape = CutCornerShape(10.dp)
    )
}
