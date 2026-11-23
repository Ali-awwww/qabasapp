package com.qabas.app

import android.media.MediaPlayer
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * 🎙️ استوديو الأنماط الصوتية والدبلجة «VoiceStudio»
 * (Spatial HUD Tech + Cybernetic Glassmorphism)
 * استوديو أنماط صوتية ودبلجة: أنماط مخصصة من أصوات Edge-Neural، دبلجة، وتوليد نصوص بأصوات عصبية.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceStudioScreen(
    onBack: () -> Unit,
    onUseAudioInProject: (String) -> Unit = {},
    onOpenInProEditor: (String) -> Unit = {},
    onNavigateToMoneyPrinterWithVoice: (String) -> Unit = {}
) {
    val theme = QabasTheme.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    BackHandler { onBack() }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: أنماط صوتية، 1: TTS، 2: دبلجة
    val tabs = listOf("🎚️ أنماط صوتية", "🎙️ توليد الكلام", "🎬 دبلجة الفيديوهات")

    // خادم VoiceStudio
    var serverUrl by remember { mutableStateOf(VoiceStudioService.getSavedServerUrl(context)) }
    var serverStatus by remember { mutableStateOf<VoiceStudioServerStatus?>(null) }
    var showServerDialog by remember { mutableStateOf(false) }
    var isPingingServer by remember { mutableStateOf(false) }

    // الأنماط الصوتية المحفوظة
    var clonedProfiles by remember { mutableStateOf(VoiceStudioService.getClonedProfiles(context)) }
    var selectedProfileId by remember { mutableStateOf(clonedProfiles.firstOrNull()?.id ?: "") }

    // حالة النمط الصوتي الجديد: صوت أساسي + نبرة + سرعة.
    // (كانت هنا عينة مسجلة تُطلب وتُحفظ ثم لا تُستعمل في التوليد أبداً.)
    var newVoiceName by remember { mutableStateOf("") }
    var selectedBaseVoiceId by remember { mutableStateOf("ar-SA-HamedNeural") }
    var pitchSlider by remember { mutableFloatStateOf(0f) }
    var speedSlider by remember { mutableFloatStateOf(1.0f) }

    // حالة توليد الكلام (TTS)
    var synthesisText by remember { mutableStateOf("إن في خلق السماوات والأرض واختلاف الليل والنهار لآيات لأولي الألباب.") }
    var isSynthesizing by remember { mutableStateOf(false) }
    var lastGeneratedAudioPath by remember { mutableStateOf<String?>(null) }
    var isAudioPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // حالة الدبلجة
    var dubbingVideoUri by remember { mutableStateOf<Uri?>(null) }
    var dubbingScript by remember { mutableStateOf("مرحباً بكم في خادم المسلم، المنصة الرائدة في إنتاج الفيديوهات السينمائية.") }
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

    // لوحة الاستوديو الصوتي تتبع الهوية المركزية بدل ألوان HUD مستقلة.
    val hudPurple = PresetAccent
    val hudCyan = PresetPrimary
    val hudGold = ThemeWarning
    val hudObsidian = DeepSlate
    val hudGlassBg = CardSurface.copy(alpha = 0.78f)
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
            } catch (_: Exception) {}
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
                                    .background(if (serverStatus?.isOnline == true) theme.success else hudGold, CircleShape)
                            )
                        }
                        Text(
                            text = if (serverStatus?.isOnline == true) "متصل بخادم VoiceStudio المحلي (${serverStatus?.latencyMs}ms)" else "النمط المحلي (Edge-Neural + FFmpeg)",
                            color = theme.textSecondary,
                            fontSize = 11.sp,
                            fontFamily = CairoFont
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = theme.textPrimary)
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
                        .background(theme.cardSurface, RoundedCornerShape(14.dp))
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
                                color = if (isSelected) theme.textPrimary else theme.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = CairoFont
                            )
                        }
                    }
                }
            }

            // ==================== تبويب 0: أنماط صوتية مخصصة ====================
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
                                    Icon(Icons.Default.Tune, contentDescription = null, tint = hudPurple, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("نمط صوتي مخصص", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = CairoFont)
                                }
                                Text("EDGE-NEURAL", color = hudCyan, fontSize = 10.sp, fontFamily = RobotoMonoFont, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "اختر صوتاً أساسياً من أصوات Edge-Neural، واضبط نبرته وسرعته — يُحفظ النمط ويُستخدم في التوليد والدبلجة. النمط يختار صوتاً جاهزاً ويعدّل نبرته وسرعته فقط؛ لا يحلّل عينة صوتية ولا ينسخ بصمة أحد.",
                                color = theme.textSecondary,
                                fontSize = 12.sp,
                                fontFamily = CairoFont,
                                lineHeight = 18.sp
                            )

                            Spacer(Modifier.height(14.dp))

                            // الصوت الأساسي: يُبنى النمط عليه مع النبرة والسرعة.
                            Text("الصوت الأساسي (Edge-Neural):", color = theme.textSecondary, fontSize = 11.sp, fontFamily = CairoFont)
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "ar-SA-HamedNeural" to "حامد (سعودي)",
                                    "ar-SA-ZariyahNeural" to "سلمى (سعودية)",
                                    "ar-EG-ShakirNeural" to "شاكر (مصري)",
                                    "ar-EG-SalmaNeural" to "سلمى (مصرية)",
                                    "ar-AE-HamdanNeural" to "حمدان (إماراتي)",
                                    "ar-SA-NaayfNeural" to "نايف (سعودي)"
                                ).forEach { (voiceId, voiceName) ->
                                    val isVoiceSelected = selectedBaseVoiceId == voiceId
                                    Surface(
                                        onClick = { selectedBaseVoiceId = voiceId },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isVoiceSelected) hudPurple.copy(alpha = 0.2f) else theme.secondarySurface,
                                        border = BorderStroke(1.dp, if (isVoiceSelected) hudPurple else theme.cardBorder)
                                    ) {
                                        Text(
                                            voiceName,
                                            color = theme.textPrimary,
                                            fontFamily = CairoFont,
                                            fontSize = 12.sp,
                                            fontWeight = if (isVoiceSelected) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(14.dp))
                            OutlinedTextField(
                                value = newVoiceName,
                                onValueChange = { newVoiceName = it },
                                label = { Text("اسم النمط الصوتي (مثال: تعليق وثائقي، قراءة هادئة)", color = theme.textTertiary, fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = hudPurple,
                                    unfocusedBorderColor = theme.cardBorder,
                                    focusedTextColor = theme.textPrimary,
                                    unfocusedTextColor = theme.textPrimary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(Modifier.height(10.dp))
                            Text("نبرة الصوت (Pitch Shift): ${String.format(Locale.US, "%.1f", pitchSlider)}", color = theme.textSecondary, fontSize = 11.sp, fontFamily = CairoFont)
                            Slider(
                                value = pitchSlider,
                                onValueChange = { pitchSlider = it },
                                valueRange = -3f..3f,
                                colors = SliderDefaults.colors(thumbColor = hudPurple, activeTrackColor = hudPurple)
                            )

                            Spacer(Modifier.height(10.dp))
                            Text("سرعة الإلقاء: ${String.format(Locale.US, "%.2f", speedSlider)}x", color = theme.textSecondary, fontSize = 11.sp, fontFamily = CairoFont)
                            Slider(
                                value = speedSlider,
                                onValueChange = { speedSlider = it },
                                valueRange = 0.7f..1.3f,
                                colors = SliderDefaults.colors(thumbColor = hudPurple, activeTrackColor = hudPurple)
                            )

                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    if (newVoiceName.isBlank()) {
                                        Toast.makeText(context, "الرجاء إدخال اسم للنمط الصوتي", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    val profile = ClonedVoiceProfile(
                                        name = newVoiceName,
                                        description = "نمط صوتي مخصص — Edge-Neural مع تعديل النبرة والسرعة",
                                        baseEngine = "Edge-Neural / $selectedBaseVoiceId",
                                        pitchShift = pitchSlider,
                                        speedRate = speedSlider
                                    )
                                    VoiceStudioService.saveClonedProfile(context, profile)
                                    clonedProfiles = VoiceStudioService.getClonedProfiles(context)
                                    selectedProfileId = profile.id
                                    newVoiceName = ""
                                    Toast.makeText(context, "تم حفظ النمط الصوتي بنجاح ✓", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = hudPurple),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, tint = theme.textPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text("حفظ النمط الصوتي", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                            }
                        }
                    }
                }

                // قائمة الأنماط الصوتية المحفوظة
                item {
                    Text("الأنماط الصوتية المحفوظة:", color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                }

                items(clonedProfiles) { profile ->
                    HudCard(
                        borderColor = if (selectedProfileId == profile.id) hudPurple else theme.cardBorder,
                        backgroundColor = theme.cardSurface.copy(alpha = 0.8f)
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
                                Text(profile.name, color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                                Text(profile.description, color = theme.textSecondary, fontSize = 11.sp, fontFamily = CairoFont)
                                Text("المحرك: ${profile.baseEngine}", color = hudCyan, fontSize = 10.sp, fontFamily = RobotoMonoFont)
                            }
                            Button(
                                onClick = { onNavigateToMoneyPrinterWithVoice(profile.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = theme.background, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("إنتاج ريلز", color = theme.background, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                            }
                            IconButton(onClick = {
                                VoiceStudioService.deleteClonedProfile(context, profile.id)
                                clonedProfiles = VoiceStudioService.getClonedProfiles(context)
                            }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = theme.destructive)
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
                                "توليد النطق بالنمط الصوتي المختار",
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
                                placeholder = { Text("أدخل النص المراد قراءته بالنمط الصوتي المختار...", color = theme.textTertiary, fontSize = 12.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = hudCyan,
                                    unfocusedBorderColor = theme.cardBorder,
                                    focusedTextColor = theme.textPrimary,
                                    unfocusedTextColor = theme.textPrimary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(Modifier.height(10.dp))
                            Text("الصوت المفعل للتوليد:", color = theme.textSecondary, fontSize = 11.sp, fontFamily = CairoFont)
                            val currentActiveProfile = clonedProfiles.find { it.id == selectedProfileId } ?: clonedProfiles.firstOrNull()

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = theme.secondarySurface,
                                border = BorderStroke(1.dp, hudCyan.copy(alpha = 0.5f))
                            ) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = hudCyan)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(currentActiveProfile?.name ?: "الصوت القياسي", color = theme.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                                        Text(currentActiveProfile?.baseEngine ?: "Edge-Neural", color = theme.textSecondary, fontSize = 10.sp, fontFamily = RobotoMonoFont)
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
                                    CircularProgressIndicator(color = theme.background, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("جاري توليد الكلام...", color = theme.background, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                                } else {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = theme.background)
                                    Spacer(Modifier.width(8.dp))
                                    Text("🎙️ توليد التعليق الصوتي الفوري", color = theme.background, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                                }
                            }

                            // مشغل الصوت للنتيجة
                            if (lastGeneratedAudioPath != null) {
                                Spacer(Modifier.height(14.dp))
                                Surface(
                                    color = theme.cardSurface,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, theme.success),
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
                                                    tint = theme.success,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                            Spacer(Modifier.width(6.dp))
                                            Column {
                                                Text("تم التوليد بنجاح ✓", color = theme.success, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                                                Text("جاهز للاستخدام في مشاريع خادم المسلم", color = theme.textSecondary, fontSize = 10.sp, fontFamily = CairoFont)
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                lastGeneratedAudioPath?.let { onUseAudioInProject(it) }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = theme.success),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("استخدام", color = theme.background, fontSize = 11.sp, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
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
                                Text("دبلجة مقاطع الفيديو بالنمط الصوتي المختار", color = hudGold, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = CairoFont)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "حدد مقطع فيديو، واكتب النص الجديد أو الترجمة العربية ليتم توليدها بالنمط الصوتي المختار واستبدال مسار الصوت الأصلي للفيديو بالكامل.",
                                color = theme.textSecondary,
                                fontSize = 12.sp,
                                fontFamily = CairoFont,
                                lineHeight = 18.sp
                            )

                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = { videoPickerLauncher.launch("video/*") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = theme.secondarySurface),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, theme.cardBorder)
                            ) {
                                Icon(Icons.Default.VideoFile, contentDescription = null, tint = hudGold)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (dubbingVideoUri != null) "✓ تم تحديد مقطع الفيديو" else "اختيار الفيديو المراد دبلجته",
                                    fontSize = 12.sp,
                                    fontFamily = CairoFont,
                                    color = theme.textPrimary
                                )
                            }

                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = dubbingScript,
                                onValueChange = { dubbingScript = it },
                                label = { Text("النص المدبلج الجديد", color = theme.textTertiary, fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = hudGold,
                                    unfocusedBorderColor = theme.cardBorder,
                                    focusedTextColor = theme.textPrimary,
                                    unfocusedTextColor = theme.textPrimary
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

                                            // توليد الصوت بالنمط المختار فعلاً — لا الأول في
                                            // القائمة: كان الزر يتجاهل اختيار المستخدم.
                                            val profile = clonedProfiles.find { it.id == selectedProfileId }
                                                ?: clonedProfiles.firstOrNull()
                                                ?: ClonedVoiceProfile(name = "Standard Voice")
                                            val audioRes = VoiceStudioService.synthesizeLocallyWithProfile(context, dubbingScript, profile)
                                            if (audioRes.isSuccess) {
                                                val audioFile = audioRes.getOrThrow()
                                                val outDubbed = File(context.cacheDir, "dubbed_final_${System.currentTimeMillis()}.mp4")
                                                val dubRes = VoiceStudioService.dubVideoTrack(tempVideo, audioFile, outDubbed)
                                                if (dubRes.isSuccess) {
                                                    dubbedVideoPath = dubRes.getOrThrow().absolutePath
                                                    Toast.makeText(context, "تمت دبلجة الفيديو بنجاح ✓", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "تعذرت الدبلجة: ${dubRes.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "تعذر توليد الصوت: ${audioRes.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
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
                                    CircularProgressIndicator(color = theme.background, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("جاري معالجة ودبلجة الفيديو بـ FFmpeg...", color = theme.background, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                                } else {
                                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = theme.background)
                                    Spacer(Modifier.width(8.dp))
                                    Text("🎬 بدء الدبلجة بالنمط الصوتي", color = theme.background, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                                }
                            }

                            if (dubbedVideoPath != null) {
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        dubbedVideoPath?.let { onOpenInProEditor(it) }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
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
            title = { Text("ربط خادم VoiceStudio (Local / Remote)", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "أدخل رابط خادم VoiceStudio (المحلي عبر 10.0.2.2:8000 أو خادمك الخاص):",
                        color = theme.textSecondary,
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
                            unfocusedBorderColor = theme.cardBorder,
                            focusedTextColor = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary
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
                        colors = ButtonDefaults.buttonColors(containerColor = theme.secondarySurface),
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
                    Text("حفظ", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerDialog = false }) {
                    Text("إلغاء", color = theme.textTertiary, fontFamily = CairoFont)
                }
            },
            containerColor = theme.background
        )
    }
}
