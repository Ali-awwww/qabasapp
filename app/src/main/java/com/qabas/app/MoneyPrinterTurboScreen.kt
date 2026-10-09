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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * 🛸 شاشة «MoneyPrinter Turbo» السيبرانية (Spatial HUD Tech)
 * مصنع إنتاج الريلز والشورتس المؤتمت ذاتياً بدمج أحدث معايير MoneyPrinterTurbo
 * وأصوات Edge-TTS العصبية المجانية عالية النقاء.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyPrinterTurboScreen(
    onBack: () -> Unit,
    onOpenInProEditor: (String) -> Unit = {},
    onOpenInYouTubeStudio: (String) -> Unit = {},
    onNavigateToVoiceCloning: () -> Unit = {},
    onSaveProject: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    BackHandler { onBack() }

    // إعدادات الموضوع
    var subjectText by remember { mutableStateOf("عجائب الكون وخلق السماوات") }
    var customScriptText by remember { mutableStateOf("") }
    var selectedRatio by remember { mutableStateOf("9:16") }
    var selectedVoice by remember { mutableStateOf("ar-SA-HamedNeural") }
    var speechRate by remember { mutableStateOf("+0%") }
    var enableSubtitles by remember { mutableStateOf(true) }
    var bgmVolume by remember { mutableFloatStateOf(0.18f) }

    // حالة الخادم
    var serverUrl by remember { mutableStateOf(MoneyPrinterTurboService.getSavedServerUrl(context)) }
    var showServerConfigDialog by remember { mutableStateOf(false) }
    var isCheckingServer by remember { mutableStateOf(false) }
    var serverStatus by remember {
        mutableStateOf<MoneyPrinterServerStatus?>(null)
    }

    // حالة التوليد والتشغيل
    var isGenerating by remember { mutableStateOf(false) }
    var currentStage by remember { mutableStateOf("جاهز للأمر البرمجي...") }
    var currentProgress by remember { mutableFloatStateOf(0f) }
    var finishedVideoPath by remember { mutableStateOf<String?>(null) }
    var statusLogs by remember { mutableStateOf(listOf("⚡ تم تهيئة مصفوفة MoneyPrinterTurbo بنجاح.", "🎙️ محرك Edge-TTS العصبي المجاني نشط.")) }

    // نبضات الإضاءة التفاعلية
    val infiniteTransition = rememberInfiniteTransition(label = "hudGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // الألوان السيبرانية المعتمدة
    val hudCyan = Color(0xFF00F2FE)
    val hudBlue = Color(0xFF4FACFE)
    val hudGold = Color(0xFFE8C547)
    val hudObsidian = Color(0xFF060913)
    val hudGlassBg = Color(0x800B132B)
    val hudBorder = hudCyan.copy(alpha = glowAlpha * 0.5f)

    // فحص مبدئي سريع للسيرفر في الخلفية
    LaunchedEffect(Unit) {
        val check = MoneyPrinterTurboService.pingServer(serverUrl)
        serverStatus = check
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "MONEYPRINTER TURBO",
                                color = hudCyan,
                                fontFamily = RobotoMonoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(if (serverStatus?.isOnline == true) Color(0xFF10B981) else hudGold, CircleShape)
                            )
                        }
                        Text(
                            text = if (serverStatus?.isOnline == true) "متصل بالخادم السحابي (${serverStatus?.latencyMs}ms)" else "النمط المحلي المستقل (Edge-TTS + FFmpeg)",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontFamily = CairoFont
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showServerConfigDialog = true }) {
                        Icon(Icons.Default.Dns, contentDescription = "إعدادات الخادم", tint = hudCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = hudObsidian
                )
            )
        },
        containerColor = hudObsidian
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // كارت شريط التيليمتري العلوي (HUD Telemetry Matrix)
            item {
                Spacer(Modifier.height(4.dp))
                HudCard(
                    borderColor = hudBorder,
                    backgroundColor = hudGlassBg
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = hudGold, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "محرك الإنتاج الآلي الذاتي",
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Text(
                                "v2.6 TURBO",
                                color = hudCyan,
                                fontFamily = RobotoMonoFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            "يستبدل العمليات اليدوية المعقدة بمحرك إنتاج فوري: يولد السكريبت، ويستخرج لقطات B-Roll السينمائية، ويسجل بصوت Edge-TTS العصبي المجاني 100%، ويرندر ريلز كامل بدقة 9:16 بضغطة زر.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            fontFamily = CairoFont,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // قسم إدخال الموضوع والمقترحات السريعة
            item {
                HudCard(
                    borderColor = hudCyan.copy(alpha = 0.35f),
                    backgroundColor = hudGlassBg
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "موضوع الفيديو أو الكلمات المفتاحية",
                            color = hudCyan,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = subjectText,
                            onValueChange = { subjectText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("مثال: عجائب سورة الكهف، فضل الاستغفار، سيرة خالد بن الوليد...", color = Color.Gray, fontSize = 12.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = hudCyan,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF0F172A).copy(alpha = 0.6f),
                                unfocusedContainerColor = Color(0xFF0F172A).copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(Modifier.height(10.dp))
                        Text("مقترحات سريعة ملهمة:", color = Color(0xFF94A3B8), fontSize = 11.sp, fontFamily = CairoFont)
                        Spacer(Modifier.height(6.dp))

                        val presets = listOf(
                            "🌙 فضائل الأذكار وطمأنينة القلب",
                            "✨ قصة أصحاب الكهف والعبر المستفادة",
                            "🌌 عظمة خلق الكون وتدبر آيات الله",
                            "⚔️ سيرة البطل القعقاع بن عمرو",
                            "💎 حِكم الشافعي ونفحات الصبر"
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(presets) { preset ->
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f)),
                                    modifier = Modifier.clickable {
                                        subjectText = preset.substringAfter(" ")
                                    }
                                ) {
                                    Text(
                                        preset,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 11.sp,
                                        fontFamily = CairoFont
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // قسم إعدادات الصوت العصبي المجاني (Edge-TTS)
            item {
                HudCard(
                    borderColor = hudGold.copy(alpha = 0.35f),
                    backgroundColor = hudGlassBg
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = hudGold, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "الصوت العصبي (Edge-TTS المجاني 100%)",
                                    color = hudGold,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Text(
                                "FREE NEURAL",
                                color = Color(0xFF10B981),
                                fontFamily = RobotoMonoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        val clonedProfiles = remember { VoiceStudioService.getClonedProfiles(context) }
                        if (clonedProfiles.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "🧬 أصواتك المستنسخة (VoiceStudio):",
                                    color = Color(0xFFA855F7),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = CairoFont
                                )
                                TextButton(
                                    onClick = onNavigateToVoiceCloning,
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("+ استنساخ جديد", color = Color(0xFFA855F7), fontSize = 11.sp, fontFamily = CairoFont)
                                }
                            }

                            clonedProfiles.forEach { profile ->
                                val voiceKey = "cloned:${profile.id}"
                                val isSelected = selectedVoice == voiceKey
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clickable { selectedVoice = voiceKey },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) Color(0xFF2E1065) else Color(0xFF0F172A).copy(alpha = 0.5f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) Color(0xFFA855F7) else Color(0xFF334155).copy(alpha = 0.4f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { selectedVoice = voiceKey },
                                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFA855F7))
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(profile.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                                                Spacer(Modifier.width(6.dp))
                                                Surface(
                                                    color = Color(0xFFA855F7).copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text("مستنسخ AI", color = Color(0xFFA855F7), fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontFamily = CairoFont)
                                                }
                                            }
                                            Text(profile.description, color = Color(0xFF94A3B8), fontSize = 10.sp, fontFamily = CairoFont)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        Text(
                            "🎙️ الأصوات العصبية الجاهزة (Edge-TTS المجاني):",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CairoFont
                        )
                        Spacer(Modifier.height(4.dp))

                        val availableVoices = EdgeTtsService.DEFAULT_VOICES.filter { it.isArabic }
                        availableVoices.forEach { voice ->
                            val isSelected = selectedVoice == voice.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable { selectedVoice = voice.id },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFF1E293B) else Color(0xFF0F172A).copy(alpha = 0.5f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) hudGold else Color(0xFF334155).copy(alpha = 0.4f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedVoice = voice.id },
                                        colors = RadioButtonDefaults.colors(selectedColor = hudGold)
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(voice.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                                        Text(voice.description, color = Color(0xFF94A3B8), fontSize = 10.sp, fontFamily = CairoFont)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // قسم أبعاد الفيديو والمونتاج (Aspect Ratio & Style)
            item {
                HudCard(
                    borderColor = hudBlue.copy(alpha = 0.35f),
                    backgroundColor = hudGlassBg
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "أبعاد الفيديو ونظام المونتاج",
                            color = hudBlue,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // 9:16
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedRatio = "9:16" },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selectedRatio == "9:16") Color(0xFF1E293B) else Color(0xFF0F172A),
                                border = BorderStroke(1.dp, if (selectedRatio == "9:16") hudCyan else Color(0xFF334155))
                            ) {
                                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.StayCurrentPortrait, contentDescription = null, tint = if (selectedRatio == "9:16") hudCyan else Color.Gray)
                                    Spacer(Modifier.height(4.dp))
                                    Text("9:16 عمودي", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                                    Text("TikTok / Reels / Shorts", color = Color(0xFF94A3B8), fontSize = 10.sp, fontFamily = RobotoMonoFont)
                                }
                            }

                            // 16:9
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedRatio = "16:9" },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selectedRatio == "16:9") Color(0xFF1E293B) else Color(0xFF0F172A),
                                border = BorderStroke(1.dp, if (selectedRatio == "16:9") hudCyan else Color(0xFF334155))
                            ) {
                                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.StayCurrentLandscape, contentDescription = null, tint = if (selectedRatio == "16:9") hudCyan else Color.Gray)
                                    Spacer(Modifier.height(4.dp))
                                    Text("16:9 أفقي", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                                    Text("YouTube / Landscape", color = Color(0xFF94A3B8), fontSize = 10.sp, fontFamily = RobotoMonoFont)
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("توليد ترجمة نصية متزامنة (Auto Subtitles)", color = Color.White, fontSize = 12.sp, fontFamily = CairoFont)
                            Switch(
                                checked = enableSubtitles,
                                onCheckedChange = { enableSubtitles = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = hudCyan, checkedTrackColor = Color(0xFF0F766E))
                            )
                        }
                    }
                }
            }

            // زر التوليد السيبراني الأساسي
            item {
                Button(
                    onClick = {
                        if (isGenerating) return@Button
                        isGenerating = true
                        currentProgress = 0.05f
                        currentStage = "بدء تشغيل منظومة MoneyPrinterTurbo..."
                        finishedVideoPath = null

                        val config = MoneyPrinterJobConfig(
                            subject = subjectText,
                            customScript = customScriptText,
                            aspectRatio = selectedRatio,
                            voiceName = selectedVoice,
                            speechRate = speechRate,
                            bgmVolume = bgmVolume,
                            subtitleEnabled = enableSubtitles
                        )

                        coroutineScope.launch {
                            try {
                                statusLogs = statusLogs + "⚡ إطلاق مهمة التوليد الآلي لـ: $subjectText"

                                if (serverStatus?.isOnline == true) {
                                    // النمط السحابي عبر سيرفر MoneyPrinterTurbo
                                    currentStage = "إرسال المهمة لخادم MoneyPrinterTurbo..."
                                    val submitRes = MoneyPrinterTurboService.submitRemoteJob(serverUrl, config)
                                    if (submitRes.isSuccess) {
                                        val taskId = submitRes.getOrThrow()
                                        statusLogs = statusLogs + "📡 تم تخصيص معرّف المهمة: $taskId"

                                        var finished = false
                                        var attempts = 0
                                        while (!finished && attempts < 60) {
                                            delay(3000)
                                            attempts++
                                            val pollRes = MoneyPrinterTurboService.pollRemoteTask(serverUrl, taskId)
                                            if (pollRes.isSuccess) {
                                                val state = pollRes.getOrThrow()
                                                currentProgress = state.progress / 100f
                                                currentStage = state.stage
                                                if (state.isFinished) {
                                                    finished = true
                                                    finishedVideoPath = state.outputVideoPath
                                                    statusLogs = statusLogs + "🎉 اكتمل الفيديو بنجاح عبر السيرفر!"
                                                } else if (state.isFailed) {
                                                    finished = true
                                                    Toast.makeText(context, "فشل السيرفر في الرندرة", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    } else {
                                        statusLogs = statusLogs + "⚠️ تعذر التواصل مع السيرفر، التحويل الفوري للنمط المحلي المستقل..."
                                        runLocalEngine(context, config) { stage, prog ->
                                            currentStage = stage
                                            currentProgress = prog
                                        }
                                    }
                                } else {
                                    // النمط المحلي المستقل فائق السرعة
                                    runLocalEngine(context, config) { stage, prog ->
                                        currentStage = stage
                                        currentProgress = prog
                                    }
                                }
                            } catch (e: Exception) {
                                statusLogs = statusLogs + "❌ خطأ أثناء المعالجة: ${e.message}"
                                Toast.makeText(context, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isGenerating = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = hudCyan
                    ),
                    enabled = !isGenerating && subjectText.isNotBlank()
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(color = hudObsidian, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("جاري الإنتاج السيبراني ($currentStage)...", color = hudObsidian, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                    } else {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = hudObsidian)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "⚡ إنتاج ريلز كامل ذاتياً (Autonomous Turbo Render)",
                            color = hudObsidian,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            fontFamily = CairoFont
                        )
                    }
                }
            }

            // رادار التيليمتري وحالة المهمة الحالية (Progress HUD)
            if (isGenerating || finishedVideoPath != null) {
                item {
                    HudCard(
                        borderColor = if (finishedVideoPath != null) Color(0xFF10B981) else hudCyan,
                        backgroundColor = Color(0x990A1128)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "رادار المعالجة السيبراني (Live Pipeline Radar)",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = CairoFont
                                )
                                Text(
                                    "${(currentProgress * 100).toInt()}%",
                                    color = if (finishedVideoPath != null) Color(0xFF10B981) else hudCyan,
                                    fontFamily = RobotoMonoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }

                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { currentProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (finishedVideoPath != null) Color(0xFF10B981) else hudCyan,
                                trackColor = Color(0xFF1E293B)
                            )

                            Spacer(Modifier.height(10.dp))
                            Text(currentStage, color = Color(0xFFE2E8F0), fontSize = 12.sp, fontFamily = CairoFont)

                            // في حال انتهاء الفيديو
                            if (finishedVideoPath != null) {
                                Spacer(Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            finishedVideoPath?.let { onOpenInProEditor(it) }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("فتح بالمحرر", fontSize = 11.sp, fontFamily = CairoFont)
                                    }

                                    Button(
                                        onClick = {
                                            finishedVideoPath?.let { onOpenInYouTubeStudio(it) }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("استوديو يوتيوب", fontSize = 11.sp, fontFamily = CairoFont)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // سجل العمليات الطرفية (Terminal Radar Logs)
            item {
                HudCard(
                    borderColor = Color(0xFF334155),
                    backgroundColor = Color(0xFF020617).copy(alpha = 0.85f)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = hudCyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("سجل أحداث MoneyPrinter:", color = hudCyan, fontSize = 11.sp, fontFamily = RobotoMonoFont)
                        }
                        Spacer(Modifier.height(8.dp))
                        statusLogs.takeLast(5).forEach { log ->
                            Text(
                                log,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontFamily = CairoFont,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // حوار إعدادات الخادم الخارجي
    if (showServerConfigDialog) {
        AlertDialog(
            onDismissRequest = { showServerConfigDialog = false },
            title = {
                Text("ربط خادم MoneyPrinterTurbo", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "أدخل رابط خادم FastAPI لـ MoneyPrinterTurbo (مثل Docker أو VPS محلي أو سحابي):",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        fontFamily = CairoFont
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = hudCyan,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            isCheckingServer = true
                            coroutineScope.launch {
                                val res = MoneyPrinterTurboService.pingServer(serverUrl)
                                serverStatus = res
                                isCheckingServer = false
                                Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isCheckingServer) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = hudCyan)
                        } else {
                            Text("📡 فحص الاتصال بالخادم", color = hudCyan, fontSize = 12.sp, fontFamily = CairoFont)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        MoneyPrinterTurboService.saveServerUrl(context, serverUrl)
                        showServerConfigDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = hudCyan)
                ) {
                    Text("حفظ", color = Color.Black, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerConfigDialog = false }) {
                    Text("إلغاء", color = Color.Gray, fontFamily = CairoFont)
                }
            },
            containerColor = Color(0xFF0B132B)
        )
    }
}

/**
 * تشغيل المحرك المحلي المستقل
 */
private suspend fun runLocalEngine(
    context: Context,
    config: MoneyPrinterJobConfig,
    onProgress: (String, Float) -> Unit
) {
    val res = MoneyPrinterTurboService.executeAutonomousLocalPipeline(context, config) { stage, prog ->
        onProgress(stage, prog)
    }
    if (res.isSuccess) {
        val file = res.getOrThrow()
        onProgress("تم اكتمال الفيديو الريلز بنجاح بنسبة 100% ✓", 1.0f)
        Toast.makeText(context, "تم توليد الفيديو بنجاح: ${file.name}", Toast.LENGTH_LONG).show()
    } else {
        throw res.exceptionOrNull() ?: Exception("فشل التوليد المحلي")
    }
}

/**
 * كرت سيبراني بأسلوب الزجاج والحدود المضيئة المتطورة (HUD Spatial Glass Card)
 */
@Composable
fun HudCard(
    borderColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                // رسم خطوط هولوغرافية دقيقة في الزوايا
                val strokeW = 2.dp.toPx()
                val cornerL = 12.dp.toPx()
                // زاوية عليا يسرى
                drawLine(borderColor, Offset(0f, 0f), Offset(cornerL, 0f), strokeW)
                drawLine(borderColor, Offset(0f, 0f), Offset(0f, cornerL), strokeW)
                // زاوية عليا يمنى
                drawLine(borderColor, Offset(size.width, 0f), Offset(size.width - cornerL, 0f), strokeW)
                drawLine(borderColor, Offset(size.width, 0f), Offset(size.width, cornerL), strokeW)
            },
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        content()
    }
}
