package com.qabas.app

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeleprompterScreen(
    onBack: () -> Unit,
    initialScript: String? = null,
    onNavigateToEditor: ((script: String, audioPath: String?) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Default sample text
    val defaultScript = Translator.tr(
        "بسم الله الرحمن الرحيم\n" +
        "السلام عليكم ورحمة الله وبركاته.\n\n" +
        "مرحباً بكم في هذه الحلقة الجديدة.\n" +
        "اليوم سنتحدث عن موضوع يهم كل مسلم، ألا وهو أثر الكلمة الطيبة في بناء المجتمع المترابط...\n\n" +
        "إن الكلمة الطيبة صدقة، كما علمنا نبينا الكريم ﷺ، وهي مفتاح للقلوب ومغلاق للشرور.\n" +
        "لذا دعونا نحرص دائماً على انتقاء كلماتنا، لنكون بلسماً لمن حولنا.\n\n" +
        "شاركنا رأيك وتدبرك في التعليقات، ولا تنسَ نشر المقطع ليعم الأجر.\n" +
        "دمتم في رعاية الله وحفظه."
    )

    var scriptText by remember { mutableStateOf(initialScript?.takeIf { it.isNotBlank() } ?: defaultScript) }
    var scrollSpeed by remember { mutableFloatStateOf(3.5f) } // 1.0 to 10.0
    var fontSize by remember { mutableFloatStateOf(42f) } // 24 to 72
    var isPlaying by remember { mutableStateOf(false) }
    var isMirrored by remember { mutableStateOf(false) }
    var textAlign by remember { mutableStateOf(TextAlign.Center) }
    var showSettings by remember { mutableStateOf(true) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showCountdown by remember { mutableStateOf(false) }
    var countdownValue by remember { mutableIntStateOf(3) }
    var showFocusGuide by remember { mutableStateOf(true) }

    // Check SharedPreferences on init if no initialScript provided
    LaunchedEffect(Unit) {
        if (initialScript.isNullOrBlank()) {
            val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            val savedInput = prefs.getString("input", null)
            if (!savedInput.isNullOrBlank() && scriptText == defaultScript) {
                scriptText = savedInput
            }
        }
    }

    // Audio recording state
    val recorderHelper = remember { AudioRecorderHelper(context) }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var recordDurationSeconds by remember { mutableIntStateOf(0) }
    var hasRecordedTrack by remember { mutableStateOf(false) }
    var isPlayingRecordedAudio by remember { mutableStateOf(false) }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val started = recorderHelper.startRecording()
            if (started) {
                isRecordingAudio = true
                recordDurationSeconds = 0
                hasRecordedTrack = false
                Toast.makeText(context, Translator.tr("بدأ تسجيل الصوت المتزامن 🎙️"), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, Translator.tr("تعذر بدء التسجيل الصوتي"), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, Translator.tr("يلزم إذن الميكروفون لتسجيل الصوت"), Toast.LENGTH_SHORT).show()
        }
    }

    // Recording duration timer
    LaunchedEffect(isRecordingAudio) {
        if (isRecordingAudio) {
            while (isRecordingAudio) {
                delay(1000)
                recordDurationSeconds++
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (recorderHelper.isRecording) {
                recorderHelper.stopRecording()
            }
            if (recorderHelper.isPlaying) {
                recorderHelper.stopPlayback()
            }
        }
    }

    val scrollState = rememberScrollState()

    // Smooth Hardware-Synced Auto-scrolling engine (60fps/120fps VSYNC)
    LaunchedEffect(isPlaying, scrollSpeed) {
        if (isPlaying) {
            scrollState.scroll {
                var lastFrameNanos = withFrameNanos { it }
                while (isPlaying && isActive) {
                    withFrameNanos { currentFrameNanos ->
                        val deltaSeconds = (currentFrameNanos - lastFrameNanos) / 1_000_000_000f
                        lastFrameNanos = currentFrameNanos
                        // Base velocity: speed multiplier * 35 dp equivalent in pixels
                        val pixelsToScroll = (scrollSpeed * 35f) * deltaSeconds
                        scrollBy(pixelsToScroll)
                        if (scrollState.value >= scrollState.maxValue && scrollState.maxValue > 0) {
                            isPlaying = false
                        }
                    }
                }
            }
        }
    }

    // Start with 3s countdown function
    fun startPlayWithCountdown() {
        if (isPlaying) {
            isPlaying = false
            showCountdown = false
        } else {
            showCountdown = true
            countdownValue = 3
            coroutineScope.launch {
                while (countdownValue > 1) {
                    delay(800)
                    countdownValue--
                }
                delay(600)
                showCountdown = false
                isPlaying = true
                showSettings = false
            }
        }
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = !isPlaying || showSettings,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                Translator.tr("المُلقن الذكي"),
                                color = GoldPrimary,
                                fontWeight = FontWeight.Bold,
                                fontFamily = CairoFont,
                                fontSize = 18.sp
                            )
                            Text(
                                if (isMirrored) Translator.tr("وضع المرآة العاكسة مفعّل 🪞") else Translator.tr("وضع العرض المباشر"),
                                color = if (isMirrored) GoldPrimary else TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = NotoSansFont
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (isRecordingAudio) {
                                recorderHelper.stopRecording()
                                isRecordingAudio = false
                            }
                            onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Translator.tr("العودة"), tint = GoldPrimary)
                        }
                    },
                    actions = {
                        // Import Project Script Button
                        IconButton(onClick = { showImportDialog = true }) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = "استيراد السكريبت",
                                tint = GoldPrimary
                            )
                        }
                        // Mirror Mode Toggle
                        IconButton(onClick = {
                            isMirrored = !isMirrored
                            Toast.makeText(
                                context,
                                if (isMirrored) "تم تفعيل وضع المرآة (لأجهزة التلقين الزجاجية)" else "تم إيقاف وضع المرآة",
                                Toast.LENGTH_SHORT
                            ).show()
                        }) {
                            Icon(
                                Icons.Default.Flip,
                                contentDescription = "عكس الشاشة",
                                tint = if (isMirrored) GoldPrimary else Color.White
                            )
                        }
                        // Focus Guide Toggle
                        IconButton(onClick = { showFocusGuide = !showFocusGuide }) {
                            Icon(
                                Icons.Default.RemoveRedEye,
                                contentDescription = "خط التركيز",
                                tint = if (showFocusGuide) GoldPrimary else Color.Gray
                            )
                        }
                        // Text Alignment Toggle
                        IconButton(onClick = {
                            textAlign = when (textAlign) {
                                TextAlign.Center -> TextAlign.Right
                                TextAlign.Right -> TextAlign.Left
                                else -> TextAlign.Center
                            }
                        }) {
                            Icon(Icons.Default.FormatAlignJustify, contentDescription = "محاذاة", tint = Color.White)
                        }
                        // Settings Drawer Toggle
                        IconButton(onClick = { showSettings = !showSettings }) {
                            Icon(Icons.Default.Tune, contentDescription = "التحكم", tint = if (showSettings) GoldPrimary else Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0F1D))
                )
            }
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            // Text Viewport Area with Tap Gestures
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                showSettings = !showSettings
                            },
                            onDoubleTap = {
                                if (isPlaying) {
                                    isPlaying = false
                                } else {
                                    startPlayWithCountdown()
                                }
                            }
                        )
                    }
            ) {
                // Mirrored or Normal Text Container
                OutlinedTextField(
                    value = scriptText,
                    onValueChange = { scriptText = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 28.dp, vertical = 80.dp)
                        .graphicsLayer {
                            if (isMirrored) {
                                scaleX = -1f
                            }
                        },
                    textStyle = LocalTextStyle.current.copy(
                        color = Color.White,
                        fontSize = fontSize.sp,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        textAlign = textAlign,
                        lineHeight = (fontSize * 1.55).sp
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = GoldPrimary,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )

                // Eye-Level Focus Guide (Center Guide Line)
                if (showFocusGuide) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(GoldPrimary.copy(alpha = 0.45f))
                            .align(Alignment.Center)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .align(Alignment.Center),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = GoldPrimary.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = GoldPrimary.copy(alpha = 0.8f),
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer { scaleX = -1f }
                        )
                    }
                }

                // Countdown Overlay (3, 2, 1)
                if (showCountdown) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.75f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = GoldPrimary,
                            modifier = Modifier.size(120.dp),
                            shadowElevation = 16.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$countdownValue",
                                    color = DeepSlate,
                                    fontSize = 64.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = CairoFont
                                )
                            }
                        }
                    }
                }

                // Mirror Mode Banner Badge
                if (isMirrored && !isPlaying) {
                    Surface(
                        color = GoldPrimary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Flip, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "وضع المرآة نشط (معكوس للزجاج العاكس)",
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Audio Recording Floating Indicator
                if (isRecordingAudio) {
                    Surface(
                        color = Color.Red.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "REC ${recordDurationSeconds / 60}:${String.format("%02d", recordDurationSeconds % 60)}",
                                color = Color.White,
                                fontFamily = NotoSansFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Controls & Floating Control Sheet
            AnimatedVisibility(
                visible = !isPlaying || showSettings,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.96f))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Quick Action Buttons Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rewind to top
                        IconButton(
                            onClick = {
                                coroutineScope.launch { scrollState.animateScrollTo(0) }
                                isPlaying = false
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF1E293B), CircleShape)
                        ) {
                            Icon(Icons.Default.Replay, contentDescription = "إعادة للبداية", tint = Color.White)
                        }

                        // Play/Pause Floating Action Button
                        Button(
                            onClick = { startPlayWithCountdown() },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(68.dp)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "إيقاف" else "تشغيل",
                                tint = DeepSlate,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Audio Recording Action
                        IconButton(
                            onClick = {
                                if (isRecordingAudio) {
                                    val file = recorderHelper.stopRecording()
                                    isRecordingAudio = false
                                    hasRecordedTrack = (file != null && file.exists())
                                    Toast.makeText(context, Translator.tr("تم حفظ التسجيل الصوتي بنجاح!"), Toast.LENGTH_SHORT).show()
                                } else {
                                    recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (isRecordingAudio) Color.Red else Color(0xFF1E293B),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                if (isRecordingAudio) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "تسجيل الصوت",
                                tint = Color.White
                            )
                        }
                    }

                    // Recorded Audio Playback Bar if recorded
                    if (hasRecordedTrack) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = Color(0xFF151B2B),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Audiotrack, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(Translator.tr("التسجيل الصوتي جاهز للمراجعة"), color = Color.White, fontSize = 12.sp, fontFamily = CairoFont)
                                    }
                                    IconButton(
                                        onClick = {
                                            if (isPlayingRecordedAudio) {
                                                recorderHelper.stopPlayback()
                                                isPlayingRecordedAudio = false
                                            } else {
                                                val ok = recorderHelper.startPlayback {
                                                    isPlayingRecordedAudio = false
                                                }
                                                if (ok) isPlayingRecordedAudio = true
                                            }
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            if (isPlayingRecordedAudio) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "استماع",
                                            tint = GoldPrimary
                                        )
                                    }
                                }

                                if (onNavigateToEditor != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            if (isRecordingAudio) {
                                                recorderHelper.stopRecording()
                                                isRecordingAudio = false
                                            }
                                            val audioPath = recorderHelper.outputFile?.absolutePath
                                            onNavigateToEditor(scriptText, audioPath)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().height(42.dp)
                                    ) {
                                        Icon(Icons.Default.Movie, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("نقل إلى المونتاج المتقدم 🎬", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    } else if (onNavigateToEditor != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                onNavigateToEditor(scriptText, null)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("فتح السكريبت في استوديو المونتاج 🎬", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Speed Slider & Presets
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "سرعة التمرير: ${String.format("%.1f", scrollSpeed)}x",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(1.5f to "بطيء", 3.5f to "معتدل", 6.0f to "سريع").forEach { (speed, label) ->
                                    FilterChip(
                                        selected = (scrollSpeed == speed),
                                        onClick = { scrollSpeed = speed },
                                        label = { Text(label, fontSize = 10.sp, fontFamily = CairoFont) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = GoldPrimary,
                                            selectedLabelColor = DeepSlate,
                                            containerColor = Color(0xFF1E293B),
                                            labelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                        Slider(
                            value = scrollSpeed,
                            onValueChange = { scrollSpeed = it },
                            valueRange = 1.0f..10.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = GoldPrimary,
                                activeTrackColor = GoldPrimary,
                                inactiveTrackColor = Color(0xFF334155)
                            )
                        )
                    }

                    // Font Size Slider & Presets
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "حجم الخط: ${fontSize.toInt()}sp",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(32f to "عادي", 44f to "كبير", 58f to "ضخم").forEach { (size, label) ->
                                    FilterChip(
                                        selected = (fontSize == size),
                                        onClick = { fontSize = size },
                                        label = { Text(label, fontSize = 10.sp, fontFamily = CairoFont) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = GoldPrimary,
                                            selectedLabelColor = DeepSlate,
                                            containerColor = Color(0xFF1E293B),
                                            labelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                        Slider(
                            value = fontSize,
                            onValueChange = { fontSize = it },
                            valueRange = 24f..72f,
                            colors = SliderDefaults.colors(
                                thumbColor = GoldPrimary,
                                activeTrackColor = GoldPrimary,
                                inactiveTrackColor = Color(0xFF334155)
                            )
                        )
                    }

                    // Quick Tip
                    Text(
                        "💡 نقرة واحدة لإخفاء/إظهار التحكم • نقرتين للتشغيل والإيقاف المباشر",
                        color = Color.Gray,
                        fontFamily = NotoSansFont,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }

    // Script Import Dialog
    if (showImportDialog) {
        TeleprompterScriptImportDialog(
            context = context,
            onDismiss = { showImportDialog = false },
            onSelectScript = { newScript ->
                if (newScript.isNotBlank()) {
                    scriptText = newScript
                    coroutineScope.launch { scrollState.scrollTo(0) }
                    showImportDialog = false
                    Toast.makeText(context, "تم استيراد السكريبت بنجاح! 📜", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

/**
 * Dialog allowing instant import from:
 * 1. Current Active Project Idea / Generated Script
 * 2. Saved Database Projects
 * 3. Saved Reel Scripts
 * 4. Ready Islamic Content Templates
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeleprompterScriptImportDialog(
    context: Context,
    onDismiss: () -> Unit,
    onSelectScript: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("المشروع الحالي", "المشاريع المحفوظة", "نماذج جاهزة")

    val prefs = remember { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }
    val currentInput = remember { prefs.getString("input", "") ?: "" }

    val db = remember { AppDatabase.getDatabase(context) }
    val savedProjects by db.projectDao().getAllProjects().collectAsState(initial = emptyList())

    val readyTemplates = listOf(
        "خطبة الجمعة (التقوى والعمل الصالح)" to "الحمد لله رب العالمين، وأشهد أن لا إله إلا الله وحده لا شريك له، وأشهد أن محمداً عبده ورسوله.\nأوصيكم عباد الله ونفسي بتقوى الله عز وجل، فإن تقوى الله نجاة في الدنيا وفوز في الآخرة.\nقال تعالى: {يَا أَيُّهَا الَّذِينَ آمَنُوا اتَّقُوا اللَّهَ حَقَّ تُقَاتِهِ وَلَا تَمُوتُنَّ إِلَّا وَأَنتُم مُّسْلِمُونَ}...",
        "تدبر آية (ولا تيأسوا من روح الله)" to "السلام عليكم ورحمة الله.\nتدبر معنا هذا النداء الرباني العظيم: {وَلَا تَيْأَسُوا مِن رَّوْحِ اللَّهِ ۖ إِنَّهُ لَا يَيْأَسُ مِن رَّوْحِ اللَّهِ إِلَّا الْقَوْمُ الْكَافِرُونَ}.\nمهما بلغت بك الكروب واشتدت عليك الصعاب، تذكر أن فرج الله أقرب مما تتخيل...",
        "قصة من السيرة النبوية (رحمة النبي ﷺ)" to "في يوم الطائف، حين اشتد الأذى على النبي ﷺ، جاءه ملك الجبال يطلب إذنه ليطبق عليهم الأخشبين.\nفماذا كان جوابه؟ قال بأبي هو وأمي: «بل أرجو أن يخرج الله من أصلابهم من يعبد الله وحده لا يشرك به شيئاً»...",
        "حديث شريف (أحب الأعمال إلى الله)" to "عن ابن مسعود رضي الله عنه قال: سألت النبي ﷺ: أي العمل أحب إلى الله؟\nقال: «الصلاة على وقتها»\nقلت: ثم أي؟ قال: «ثم بر الوالدين»\nقلت: ثم أي؟ قال: «الجهاد في سبيل الله»..."
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("استيراد نص للمُلقن", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.Gray)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF1E293B),
                    contentColor = GoldPrimary,
                    edgePadding = 8.dp
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    title,
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == index) GoldPrimary else Color.LightGray
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(modifier = Modifier.height(300.dp)) {
                    when (selectedTab) {
                        0 -> {
                            // Current Active Project Input
                            Column(modifier = Modifier.fillMaxSize()) {
                                if (currentInput.isNotBlank()) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("نص المشروع الحالي قيد العمل:", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                currentInput,
                                                color = Color.White,
                                                fontFamily = CairoFont,
                                                fontSize = 12.sp,
                                                maxLines = 6,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(
                                                onClick = { onSelectScript(currentInput) },
                                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("استخدام هذا النص في المُلقن", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                } else {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("لا يوجد نص مشروع نشط حالياً.\nيمكنك كتابة فكرة من الشاشة الرئيسية.", color = Color.Gray, textAlign = TextAlign.Center, fontFamily = CairoFont)
                                    }
                                }
                            }
                        }
                        1 -> {
                            // Saved Projects
                            if (savedProjects.isNotEmpty()) {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(savedProjects) { project ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val text = project.idea.ifBlank { project.title }
                                                    onSelectScript(text)
                                                }
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(project.title.ifBlank { "مشروع بدون عنوان" }, color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(project.idea, color = Color.LightGray, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, fontFamily = NotoSansFont)
                                            }
                                        }
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("لا توجد مشاريع محفوظة في السجل.", color = Color.Gray, fontFamily = CairoFont)
                                }
                            }
                        }
                        2 -> {
                            // Ready Islamic Templates
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(readyTemplates) { (title, content) ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSelectScript(content) }
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(title, color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(content, color = Color.LightGray, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, fontFamily = NotoSansFont)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = GoldPrimary, fontFamily = CairoFont)
            }
        }
    )
}
