package com.qabas.app

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import java.io.FileOutputStream

/**
 * 🎙️ استوديو استنساخ ودبلجة الأصوات السيبراني «VoiceStudio»
 * (Spatial HUD Tech + Cybernetic Glassmorphism)
 * البديل المفتوح المصدر لـ ElevenLabs: استنساخ، دبلجة، وتوليد نصوص بأصوات عصبية.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceStudioScreen(
    onBack: () -> Unit,
    onUseAudioInProject: (String) -> Unit = {},
    onOpenInProEditor: (String) -> Unit = {},
    onNavigateToMoneyPrinterWithVoice: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    BackHandler { onBack() }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Clone, 1: TTS, 2: Dubbing
    val tabs = listOf("🧬 استنساخ الأصوات", "🎙️ توليد الكلام", "🎬 دبلجة الفيديوهات")

    // خادم VoiceStudio
    var serverUrl by remember { mutableStateOf(VoiceStudioService.getSavedServerUrl(context)) }
    var serverStatus by remember { mutableStateOf<VoiceStudioServerStatus?>(null) }
    var showServerDialog by remember { mutableStateOf(false) }
    var isPingingServer by remember { mutableStateOf(false) }

    // ملفات الأصوات المستنسخة
    var clonedProfiles by remember { mutableStateOf(VoiceStudioService.getClonedProfiles(context)) }
    var selectedProfileId by remember { mutableStateOf(clonedProfiles.firstOrNull()?.id ?: "") }

    // حالة الاستنساخ الجديد
    var newVoiceName by remember { mutableStateOf("") }
    var newVoiceDesc by remember { mutableStateOf("") }
    var pitchSlider by remember { mutableFloatStateOf(0f) }
    var speedSlider by remember { mutableFloatStateOf(1.0f) }
    var isRecordingSample by remember { mutableStateOf(false) }
    var recordedSampleFile by remember { mutableStateOf<File?>(null) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }

    // حالة توليد الكلام (TTS)
    var synthesisText by remember { mutableStateOf("إن في خلق السماوات والأرض واختلاف الليل والنهار لآيات لأولي الألباب.") }
    var isSynthesizing by remember { mutableStateOf(false) }
    var lastGeneratedAudioPath by remember { mutableStateOf<String?>(null) }
    var isAudioPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // حالة الدبلجة
    var dubbingVideoUri by remember { mutableStateOf<Uri?>(null) }
    var dubbingScript by remember { mutableStateOf("مرحباً بكم في قبس، المنصة الرائدة في إنتاج الفيديوهات السينمائية.") }
    var isDubbing by remember { mutableStateOf(false) }
    var dubbedVideoPath by remember { mutableStateOf<String?>(null) }

    // أنيميشن التوهج السيبراني
    val infiniteTransition = rememberInfiniteTransition(label = "hudPurpleGlow")
    val glowIntensity by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val hudPurple = Color(0xFFA855F7)
    val hudCyan = Color(0xFF00F2FE)
    val hudGold = Color(0xFFE8C547)
    val hudObsidian = Color(0xFF060913)
    val hudGlassBg = Color(0x800B132B)
    val hudBorder = hudPurple.copy(alpha = glowIntensity * 0.6f)

    // فحص الاتصال بالخادم عند فتح الشاشة
    LaunchedEffect(Unit) {
        serverStatus = VoiceStudioService.pingServer(serverUrl)
    }

    // تنظيف الميديا بلاير عند الخروج
    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaRecorder?.release()
            } catch (_: Exception) {}
        }
    }

    // لاقط ملفات الصوت للاستنساخ
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                try {
                    val temp = File(context.cacheDir, "sample_voice_${System.currentTimeMillis()}.mp3")
                    context.contentResolver.openInputStream(it)?.use { input ->
                        FileOutputStream(temp).use { output -> input.copyTo(output) }
                    }
                    recordedSampleFile = temp
                    Toast.makeText(context, "تم تحميل العينة الصوتية بنجاح بنسبة 100%", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "خطأ في قراءة الملف: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // لاقط ملفات الفيديو للدبلجة
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        dubbingVideoUri = uri
        if (uri != null) {
            Toast.makeText(context, "تم تحديد الفيديو للدبلجة بنجاح", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "VOICESTUDIO // CLONER",
                                color = hudPurple,
                                fontFamily = RobotoMonoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                letterSpacing = 1.1.sp
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(if (serverStatus?.isOnline == true) Color(0xFF10B981) else hudGold, CircleShape)
                            )
                        }
                        Text(
                            text = if (serverStatus?.isOnline == true) "متصل بخادم VoiceStudio المحلي (${serverStatus?.latencyMs}ms)" else "النمط الهجين (16 محرك + F5-TTS + Edge)",
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
                    IconButton(onClick = { showServerDialog = true }) {
                        Icon(Icons.Default.Dns, contentDescription = "إعدادات الخادم", tint = hudPurple)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = hudObsidian)
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
            // شريط التنقل بين التبويبات الفضائية (Spatial HUD Tabs)
            item {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A), RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) hudPurple else Color.Transparent)
                                .clickable { selectedTab = index }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                title,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = CairoFont
                            )
                        }
                    }
                }
            }

            // ==================== تبويب 0: استنساخ الأصوات (Voice Cloner) ====================
            if (selectedTab == 0) {
                item {
                    HudCard(borderColor = hudBorder, backgroundColor = hudGlassBg) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = hudPurple, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("بصمة الصوت واستنساخ النبرة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = CairoFont)
                                }
                                Text("ZERO-SHOT AI", color = hudCyan, fontSize = 10.sp, fontFamily = RobotoMonoFont, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "سجل مقطعاً صوتياً مدته 5 إلى 10 ثوانٍ بصوتك أو صوت أي متحدث، وسيقوم المحرك باستخلاص الخصائص الصوتية لإنشاء بصمة صوتية دائمة.",
                                color = Color(0xFFCBD5E1),
                                fontSize = 12.sp,
                                fontFamily = CairoFont,
                                lineHeight = 18.sp
                            )

                            Spacer(Modifier.height(14.dp))

                            // خيارات رفع أو تسجيل العينة
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { audioPickerLauncher.launch("audio/*") },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFF334155))
                                ) {
                                    Icon(Icons.Default.UploadFile, contentDescription = null, tint = hudPurple, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("رفع ملف صوتي", fontSize = 11.sp, fontFamily = CairoFont, color = Color.White)
                                }

                                Button(
                                    onClick = {
                                        if (isRecordingSample) {
                                            try {
                                                mediaRecorder?.stop()
                                                mediaRecorder?.release()
                                                mediaRecorder = null
                                                isRecordingSample = false
                                                Toast.makeText(context, "تم حفظ التسجيل كعينة بنجاح", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                isRecordingSample = false
                                            }
                                        } else {
                                            try {
                                                val file = File(context.cacheDir, "rec_voice_${System.currentTimeMillis()}.mp3")
                                                val recorder = MediaRecorder().apply {
                                                    setAudioSource(MediaRecorder.AudioSource.MIC)
                                                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                                    setOutputFile(file.absolutePath)
                                                    prepare()
                                                    start()
                                                }
                                                mediaRecorder = recorder
                                                recordedSampleFile = file
                                                isRecordingSample = true
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "يتطلب إذن الميكروفون للتسجيل", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isRecordingSample) Color(0xFFEF4444) else Color(0xFF1E293B)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, if (isRecordingSample) Color(0xFFEF4444) else Color(0xFF334155))
                                ) {
                                    Icon(if (isRecordingSample) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (isRecordingSample) "إيقاف التسجيل" else "تسجيل الآن", fontSize = 11.sp, fontFamily = CairoFont, color = Color.White)
                                }
                            }

                            if (recordedSampleFile != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "✓ تم التقاط العينة: ${recordedSampleFile?.name} (${recordedSampleFile?.length()?.div(1024)} KB)",
                                    color = Color(0xFF10B981),
                                    fontSize = 11.sp,
                                    fontFamily = RobotoMonoFont
                                )
                            }

                            Spacer(Modifier.height(14.dp))
                            OutlinedTextField(
                                value = newVoiceName,
                                onValueChange = { newVoiceName = it },
                                label = { Text("اسم الصوت المستنسخ (مثال: صوتي الشخصي، القارئ المفضل)", color = Color.Gray, fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = hudPurple,
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(Modifier.height(10.dp))
                            Text("نبرة الصوت (Pitch Shift):", color = Color(0xFFCBD5E1), fontSize = 11.sp, fontFamily = CairoFont)
                            Slider(
                                value = pitchSlider,
                                onValueChange = { pitchSlider = it },
                                valueRange = -3f..3f,
                                colors = SliderDefaults.colors(thumbColor = hudPurple, activeTrackColor = hudPurple)
                            )

                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    if (newVoiceName.isBlank()) {
                                        Toast.makeText(context, "الرجاء إدخال اسم للصوت المستنسخ", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    val profile = ClonedVoiceProfile(
                                        name = newVoiceName,
                                        description = "صوت مستنسخ بواسطة VoiceStudio AI",
                                        sampleAudioPath = recordedSampleFile?.absolutePath ?: "",
                                        pitchShift = pitchSlider,
                                        speedRate = speedSlider
                                    )
                                    VoiceStudioService.saveClonedProfile(context, profile)
                                    clonedProfiles = VoiceStudioService.getClonedProfiles(context)
                                    selectedProfileId = profile.id
                                    newVoiceName = ""
                                    recordedSampleFile = null
                                    Toast.makeText(context, "تم حفظ الصوت المستنسخ في الخزانة الصوتية بنجاح ✓", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = hudPurple),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("⚡ حفظ واستنساخ البصمة الصوتية", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                            }
                        }
                    }
                }

                // قائمة الأصوات المستنسخة في الخزانة
                item {
                    Text("الأصوات المستنسخة المحفوظة:", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                }

                items(clonedProfiles) { profile ->
                    HudCard(
                        borderColor = if (selectedProfileId == profile.id) hudPurple else Color(0xFF334155),
                        backgroundColor = Color(0xFF0F172A).copy(alpha = 0.8f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedProfileId == profile.id,
                                onClick = { selectedProfileId = profile.id },
                                colors = RadioButtonDefaults.colors(selectedColor = hudPurple)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(profile.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                                Text(profile.description, color = Color(0xFF94A3B8), fontSize = 11.sp, fontFamily = CairoFont)
                                Text("المحرك: ${profile.baseEngine}", color = hudCyan, fontSize = 10.sp, fontFamily = RobotoMonoFont)
                            }
                            Button(
                                onClick = { onNavigateToMoneyPrinterWithVoice(profile.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F2FE)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("إنتاج ريلز", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                            }
                            IconButton(onClick = {
                                VoiceStudioService.deleteClonedProfile(context, profile.id)
                                clonedProfiles = VoiceStudioService.getClonedProfiles(context)
                            }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = Color(0xFFEF4444))
                            }
                        }
                    }
                }
            }

            // ==================== تبويب 1: توليد الكلام وقراءة النصوص (Speech Synthesizer) ====================
            if (selectedTab == 1) {
                item {
                    HudCard(borderColor = hudCyan, backgroundColor = hudGlassBg) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "توليد النطق بالصوت المستنسخ المختار",
                                color = hudCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = CairoFont
                            )
                            Spacer(Modifier.height(8.dp))

                            OutlinedTextField(
                                value = synthesisText,
                                onValueChange = { synthesisText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 110.dp),
                                placeholder = { Text("أدخل النص المراد قراءته بصوت الذكاء الاصطناعي المستنسخ...", color = Color.Gray, fontSize = 12.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = hudCyan,
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(Modifier.height(10.dp))
                            Text("الصوت المفعل للتوليد:", color = Color(0xFFCBD5E1), fontSize = 11.sp, fontFamily = CairoFont)
                            val currentActiveProfile = clonedProfiles.find { it.id == selectedProfileId } ?: clonedProfiles.firstOrNull()

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, hudCyan.copy(alpha = 0.5f))
                            ) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = hudCyan)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(currentActiveProfile?.name ?: "الصوت القياسي", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                                        Text(currentActiveProfile?.baseEngine ?: "F5-TTS Engine", color = Color(0xFF94A3B8), fontSize = 10.sp, fontFamily = RobotoMonoFont)
                                    }
                                }
                            }

                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    if (isSynthesizing || synthesisText.isBlank()) return@Button
                                    isSynthesizing = true
                                    coroutineScope.launch {
                                        try {
                                            val profile = currentActiveProfile ?: return@launch
                                            val res = VoiceStudioService.synthesizeLocallyWithProfile(
                                                context = context,
                                                text = synthesisText,
                                                profile = profile
                                            )
                                            if (res.isSuccess) {
                                                val file = res.getOrThrow()
                                                lastGeneratedAudioPath = file.absolutePath
                                                Toast.makeText(context, "تم توليد الصوت بنجاح ✓", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "تعذر التوليد: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        } finally {
                                            isSynthesizing = false
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = hudCyan),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isSynthesizing && synthesisText.isNotBlank()
                            ) {
                                if (isSynthesizing) {
                                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("جاري استنساخ ونطق الكلام...", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                                } else {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                    Spacer(Modifier.width(8.dp))
                                    Text("🎙️ توليد التعليق الصوتي الفوري", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                                }
                            }

                            // مشغل الصوت للنتيجة
                            if (lastGeneratedAudioPath != null) {
                                Spacer(Modifier.height(14.dp))
                                Surface(
                                    color = Color(0xFF0F172A),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFF10B981)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = {
                                                val path = lastGeneratedAudioPath ?: return@IconButton
                                                if (isAudioPlaying) {
                                                    mediaPlayer?.stop()
                                                    isAudioPlaying = false
                                                } else {
                                                    try {
                                                        mediaPlayer?.release()
                                                        mediaPlayer = MediaPlayer().apply {
                                                            setDataSource(path)
                                                            prepare()
                                                            start()
                                                            setOnCompletionListener { isAudioPlaying = false }
                                                        }
                                                        isAudioPlaying = true
                                                    } catch (_: Exception) {}
                                                }
                                            }) {
                                                Icon(
                                                    if (isAudioPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                                    contentDescription = null,
                                                    tint = Color(0xFF10B981),
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                            Spacer(Modifier.width(6.dp))
                                            Column {
                                                Text("تم التوليد بنجاح ✓", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                                                Text("جاهز للاستخدام في مشاريع قبس", color = Color(0xFF94A3B8), fontSize = 10.sp, fontFamily = CairoFont)
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                lastGeneratedAudioPath?.let { onUseAudioInProject(it) }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("استخدام", color = Color.Black, fontSize = 11.sp, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==================== تبويب 2: دبلجة الفيديو (Video Dubbing) ====================
            if (selectedTab == 2) {
                item {
                    HudCard(borderColor = hudGold, backgroundColor = hudGlassBg) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MovieCreation, contentDescription = null, tint = hudGold, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("دبلجة مقاطع الفيديو بالصوت المستنسخ", color = hudGold, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = CairoFont)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "حدد مقطع فيديو، واكتب النص الجديد أو الترجمة العربية ليتم توليدها بالصوت المستنسخ واستبدال مسار الصوت الأصلي للفيديو بالكامل.",
                                color = Color(0xFFCBD5E1),
                                fontSize = 12.sp,
                                fontFamily = CairoFont,
                                lineHeight = 18.sp
                            )

                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = { videoPickerLauncher.launch("video/*") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Icon(Icons.Default.VideoFile, contentDescription = null, tint = hudGold)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (dubbingVideoUri != null) "✓ تم تحديد مقطع الفيديو" else "اختيار الفيديو المراد دبلجته",
                                    fontSize = 12.sp,
                                    fontFamily = CairoFont,
                                    color = Color.White
                                )
                            }

                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = dubbingScript,
                                onValueChange = { dubbingScript = it },
                                label = { Text("النص المدبلج الجديد", color = Color.Gray, fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = hudGold,
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    val uri = dubbingVideoUri
                                    if (uri == null) {
                                        Toast.makeText(context, "الرجاء اختيار فيديو أولاً", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isDubbing = true
                                    coroutineScope.launch {
                                        try {
                                            // حفظ الفيديو في كاش مؤقت
                                            val tempVideo = File(context.cacheDir, "input_dub_${System.currentTimeMillis()}.mp4")
                                            context.contentResolver.openInputStream(uri)?.use { inStream ->
                                                FileOutputStream(tempVideo).use { outStream -> inStream.copyTo(outStream) }
                                            }

                                            // توليد الصوت المستنسخ
                                            val profile = clonedProfiles.firstOrNull() ?: ClonedVoiceProfile(name = "Standard Voice")
                                            val audioRes = VoiceStudioService.synthesizeLocallyWithProfile(context, dubbingScript, profile)
                                            if (audioRes.isSuccess) {
                                                val audioFile = audioRes.getOrThrow()
                                                val outDubbed = File(context.cacheDir, "dubbed_final_${System.currentTimeMillis()}.mp4")
                                                val dubRes = VoiceStudioService.dubVideoTrack(tempVideo, audioFile, outDubbed)
                                                if (dubRes.isSuccess) {
                                                    dubbedVideoPath = dubRes.getOrThrow().absolutePath
                                                    Toast.makeText(context, "تمت دبلجة الفيديو بنجاح 100% ✓", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } finally {
                                            isDubbing = false
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = hudGold),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isDubbing && dubbingVideoUri != null
                            ) {
                                if (isDubbing) {
                                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("جاري معالجة ودبلجة الفيديو بـ FFmpeg...", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                                } else {
                                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color.Black)
                                    Spacer(Modifier.width(8.dp))
                                    Text("🎬 بدء الدبلجة بالصوت المستنسخ", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                                }
                            }

                            if (dubbedVideoPath != null) {
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        dubbedVideoPath?.let { onOpenInProEditor(it) }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("فتح الفيديو المدبلج في المحرر الاحترافي", fontFamily = CairoFont)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // نافذة إعدادات خادم VoiceStudio
    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("ربط خادم VoiceStudio (Local / Remote)", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "أدخل رابط خادم VoiceStudio (المحلي عبر 10.0.2.2:8000 أو خادمك الخاص):",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        fontFamily = CairoFont
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = hudPurple,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            isPingingServer = true
                            coroutineScope.launch {
                                val s = VoiceStudioService.pingServer(serverUrl)
                                serverStatus = s
                                isPingingServer = false
                                Toast.makeText(context, s.message, Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isPingingServer) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = hudPurple)
                        } else {
                            Text("📡 فحص الاتصال بالخادم", color = hudPurple, fontSize = 12.sp, fontFamily = CairoFont)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        VoiceStudioService.saveServerUrl(context, serverUrl)
                        showServerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = hudPurple)
                ) {
                    Text("حفظ", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerDialog = false }) {
                    Text("إلغاء", color = Color.Gray, fontFamily = CairoFont)
                }
            },
            containerColor = Color(0xFF0B132B)
        )
    }
}
