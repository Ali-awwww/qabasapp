package com.qabas.app

import android.content.Intent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.qabas.app.ui.theme.*
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HasanatHubScreen(
    onBack: () -> Unit,
    onNavigate: (AppState) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    viewModel: HasanatHubViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    // ViewModel state
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val tasbeehCount by viewModel.tasbeehCount.collectAsState()
    val vibrateEvery33 by viewModel.vibrateEvery33.collectAsState()
    val prayerTimeInfo by viewModel.prayerTimeInfo.collectAsState()
    val dateInfo by viewModel.dateInfo.collectAsState()
    val filteredSections by viewModel.filteredSections.collectAsState()
    
    val quranStats by viewModel.quranStats.collectAsState()
    val hadithStats by viewModel.hadithStats.collectAsState()
    val azkarStats by viewModel.azkarStats.collectAsState()
    
    val isWirdDone by viewModel.isWirdDone.collectAsState()
    val isMorningAzkarDone by viewModel.isMorningAzkarDone.collectAsState()
    val isEveningAzkarDone by viewModel.isEveningAzkarDone.collectAsState()
    val isTasbeehDone by viewModel.isTasbeehDone.collectAsState()
    val dailyProgressPercent by viewModel.dailyProgressPercent.collectAsState()
    
    val recentDesign by viewModel.recentDesign.collectAsState()
    
    val currentAyah by viewModel.currentAyah.collectAsState()
    val isAutoRotating by viewModel.isAutoRotating.collectAsState()
    val autoRotateProgress by viewModel.autoRotateProgress.collectAsState()
    val activeToolDialog by viewModel.activeToolDialog.collectAsState()
    val engine = viewModel.muslimToolsEngine

    // TTS for Pearl
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    // وضع الاستماع: عند تفعيله لمسُ أي نص ينطقه صوتياً لمن لا يحسن القراءة
    var listenMode by remember { mutableStateOf(false) }
    var showVoicePicker by remember { mutableStateOf(false) }
    val voicePrefs = remember { context.getSharedPreferences("mishkat_voice_prefs", android.content.Context.MODE_PRIVATE) }

    fun applySavedVoice() {
        val engine = tts ?: return
        try {
            engine.language = Locale("ar")
            engine.setSpeechRate(voicePrefs.getFloat("rate", 0.9f))
            engine.setPitch(voicePrefs.getFloat("pitch", 1.0f))
            val savedName = voicePrefs.getString("voice_name", null)
            if (!savedName.isNullOrBlank()) {
                engine.voices?.firstOrNull { it.name == savedName }?.let { engine.voice = it }
            }
        } catch (_: Exception) { }
    }

    fun speakText(text: String, utteranceId: String = "mishkat_listen") {
        if (text.isBlank()) return
        applySavedVoice()
        if (!isTtsReady || tts == null) {
            try {
                context.startActivity(
                    Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                Toast.makeText(context, "ثبّت الصوت العربي من النافذة ثم عُد والمس النص مجدداً 🔊", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                Toast.makeText(context, "محرك الصوت غير متوفر في جهازك", Toast.LENGTH_SHORT).show()
            }
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        Unit
    }

    DisposableEffect(context) {
        var speech: TextToSpeech? = null
        speech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val res = speech?.setLanguage(Locale(Translator.currentLanguage))
                isTtsReady = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
        tts = speech
        onDispose {
            speech?.stop()
            speech?.shutdown()
        }
    }

    // تطبيق الصوت المحفوظ فور جهوزية المحرك
    LaunchedEffect(isTtsReady) {
        if (isTtsReady) applySavedVoice()
    }

    var isSearchExpanded by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary.copy(alpha = 0.15f))
                            .border(1.dp, GoldPrimary.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        Translator.tr("حول مِشكاة الهُدى"),
                        color = GoldPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        Translator.tr("مِشكاة الهُدى هو ركنك الإيماني المتكامل ضمن تطبيق قبس، يهدف إلى تيسير الوصول إلى كنوز الوحيين الشريفين وعلوم سلف الأمة."),
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                    Text(
                        Translator.tr("يحتوي هذا الركن على المصحف المرتل، شروحات الأحاديث، مواقيت الصلاة، الأذكار، وسير العلماء، لتكون لك عوناً في رحلتك الإيمانية اليومية."),
                        color = qabasTextSecondary(),
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    Divider(color = Color(0xFF1E2D4A))
                    Text(
                        Translator.tr("نسخة التطبيق: 1.2.5 (الإصدار الذهبي)"),
                        color = GoldPrimary,
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showHelpDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = CutCornerShape(8.dp)
                ) {
                    Text(Translator.tr("حسناً"), color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF141C2B),
            shape = CutCornerShape(16.dp)
        )
    }

    val upcomingEvents by viewModel.upcomingEvents.collectAsState()

    // Muslim Tools Dialogs & Direct Routes
    when (activeToolDialog) {
        "zakat" -> ZakatCalculatorDialog(onDismiss = { viewModel.dismissToolDialog() }, engine = engine)
        "fasting" -> FastingTrackerDialog(onDismiss = { viewModel.dismissToolDialog() }, engine = engine)
        "khatma" -> QuranKhatmaDialog(onDismiss = { viewModel.dismissToolDialog() }, engine = engine)
        "hajj" -> HajjUmrahDialog(onDismiss = { viewModel.dismissToolDialog() })
        "hijri" -> HijriCalendarDialog(onDismiss = { viewModel.dismissToolDialog() }, engine = engine)
        "tasbeeh" -> SmartTasbeehDialog(onDismiss = { viewModel.dismissToolDialog() }, engine = engine)
        "habits" -> HabitsTrackerDialog(onDismiss = { viewModel.dismissToolDialog() }, engine = engine)
        "stats" -> WorshipAnalyticsDialog(onDismiss = { viewModel.dismissToolDialog() }, engine = engine)
        "qibla" -> {
            LaunchedEffect(activeToolDialog) {
                onNavigate(AppState.PRAYER_TIMES)
                viewModel.dismissToolDialog()
            }
        }
        "wird" -> {
            LaunchedEffect(activeToolDialog) {
                onNavigate(AppState.QURAN_HUB)
                viewModel.dismissToolDialog()
            }
        }
        "hadith" -> {
            LaunchedEffect(activeToolDialog) {
                onNavigate(AppState.ISLAMIC_LIBRARY)
                viewModel.dismissToolDialog()
            }
        }
        "learn" -> {
            LaunchedEffect(activeToolDialog) {
                onNavigate(AppState.SCHOLAR_BIOGRAPHIES)
                viewModel.dismissToolDialog()
            }
        }
        "share" -> {
            LaunchedEffect(activeToolDialog) {
                onNavigate(AppState.PHOTO_STUDIO)
                viewModel.dismissToolDialog()
            }
        }
        "reward" -> {
            LaunchedEffect(activeToolDialog) {
                onNavigate(AppState.REWARDS)
                viewModel.dismissToolDialog()
            }
        }
    }

    // Pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Scaffold(
        containerColor = qabasBackground(),
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GoldPrimary.copy(alpha = 0.15f))
                                .border(1.dp, GoldPrimary.copy(alpha = 0.45f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Mosque, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                Translator.tr("مِشكاة الهُدى"),
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                maxLines = 1
                            )
                            Text(
                                Translator.tr("رحاب القرآن والسنة وعلوم سلف الأمة"),
                                color = qabasTextSecondary(),
                                fontFamily = NotoSansFont,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Translator.tr("رجوع"), tint = GoldPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                        Icon(
                            if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = Translator.tr("بحث"),
                            tint = if (isSearchExpanded) GoldPrimary else qabasTextSecondary()
                        )
                    }
                    IconButton(onClick = { viewModel.toggleViewMode() }) {
                        Icon(
                            if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = Translator.tr("نمط العرض"),
                            tint = GoldPrimary
                        )
                    }
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = Translator.tr("حول مشكاة الهدى"), tint = qabasTextSecondary())
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = qabasBackground())
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Search Bar
            if (isSearchExpanded) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = { Text(Translator.tr("ابحث في أبواب ومحتويات مشكاة الهدى..."), color = GoldSecondary.copy(alpha = 0.7f), fontSize = 12.sp, fontFamily = CairoFont) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CutCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GoldPrimary) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = null, tint = GoldSecondary)
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = GoldPrimary.copy(alpha = 0.35f),
                            focusedContainerColor = Color(0xFF0F172A).copy(alpha = 0.95f),
                            unfocusedContainerColor = Color(0xFF0F172A).copy(alpha = 0.8f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )
                }
            }

            // Hero Header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CutCornerShape(topStart = 24.dp, bottomEnd = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = qabasCardSurface().copy(alpha = 0.95f)),
                    border = BorderStroke(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(GoldPrimary.copy(alpha = 0.55f), Color(0x33B89758), GoldSecondary.copy(alpha = 0.35f))
                        )
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF162035), Color(0xFF0F1626), Color(0xFF090D18))
                                )
                            )
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = dateInfo.hijriStr,
                                        color = GoldPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                Surface(
                                    color = GoldPrimary.copy(alpha = 0.15f),
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Icon(Icons.Default.Verified, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(Translator.tr("مشكاة الهدى"), color = GoldPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Next Prayer
                            // Next Prayer & Countdown Timer (Notifications Screen Style)
                            Surface(
                                color = Color(0xFF0A0F1D).copy(alpha = 0.9f),
                                shape = CutCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF0EA5E9).copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (listenMode) speakText(
                                            "الصلاة القادمة: صلاة ${prayerTimeInfo.nextName} في ${prayerTimeInfo.nextTime}. متبقي على النداء: ${prayerTimeInfo.countdown}.",
                                            "prayer_info"
                                        )
                                        else onNavigate(AppState.PRAYER_TIMES)
                                    }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF0EA5E9).copy(alpha = 0.15f))
                                                    .border(1.dp, Color(0xFF0EA5E9).copy(alpha = 0.4f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(17.dp))
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        Translator.tr("الصلاة القادمة: صلاة") + " ${Translator.tr(prayerTimeInfo.nextName)}",
                                                        color = Color.White,
                                                        fontFamily = CairoFont,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Canvas(modifier = Modifier.size(7.dp)) {
                                                        drawCircle(color = Color(0xFF10B981).copy(alpha = pulseAlpha))
                                                    }
                                                }
                                                Text(
                                                    "(${prayerTimeInfo.nextTime})",
                                                    color = Color(0xFF38BDF8),
                                                    fontFamily = NotoSansFont,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Countdown Timer HUD Surface (Matching Notifications Section Style)
                                    Surface(
                                        shape = CutCornerShape(8.dp),
                                        color = Color.Black.copy(alpha = 0.35f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = Translator.tr("المتبقي حتى الأذان"),
                                                fontFamily = CairoFont,
                                                fontSize = 11.sp,
                                                color = Color.White.copy(alpha = 0.65f)
                                            )
                                            Text(
                                                text = prayerTimeInfo.countdown,
                                                fontFamily = RobotoMonoFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 22.sp,
                                                color = Color.White,
                                                letterSpacing = 1.sp
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Metrics
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DeepSlate.copy(alpha = 0.75f), CutCornerShape(10.dp))
                                    .padding(horizontal = 6.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(Modifier.weight(1f)) {
                                    QuickMishkatMetric(Icons.Default.MenuBook, Translator.tr("القرآن"), quranStats, Color(0xFF10B981))
                                }
                                Box(Modifier.weight(1f)) {
                                    QuickMishkatMetric(Icons.Default.AutoStories, Translator.tr("الأحاديث"), hadithStats, GoldPrimary)
                                }
                                Box(Modifier.weight(1f)) {
                                    QuickMishkatMetric(Icons.Default.TouchApp, Translator.tr("الأذكار"), azkarStats, Color(0xFFF59E0B))
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // زر وضع الاستماع — لمن لا يحسنون القراءة: فعّله ثم المس أي نص لسماعه 👆🔊
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        listenMode = !listenMode
                                        if (!listenMode) tts?.stop()
                                        else Toast.makeText(context, "وضع الاستماع مفعّل 👆 المس أي نص في المشكاة لسماعه صوتياً", Toast.LENGTH_LONG).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (listenMode) Color(0xFF10B981) else Color(0xFF0EA5E9)
                                    ),
                                    shape = CutCornerShape(10.dp)
                                ) {
                                    Icon(
                                        if (listenMode) Icons.Default.Hearing else Icons.Default.TouchApp,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (listenMode) Translator.tr("وضع الاستماع مفعّل — المس أي نص 👆") else Translator.tr("تفعيل وضع الاستماع 🔊"),
                                        color = Color.White,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        // قراءة الشاشة كاملة دفعة واحدة
                                        val screenText = buildString {
                                            append("السلام عليكم. أنت في مشكاة الهدى. ")
                                            append("اليوم ${dateInfo.hijriStr}. ")
                                            append("الصلاة القادمة: صلاة ${prayerTimeInfo.nextName} في ${prayerTimeInfo.nextTime}، متبقي ${prayerTimeInfo.countdown}. ")
                                            currentAyah?.let {
                                                append("آية اليوم من سورة ${it.surahName}: ${it.text}. ")
                                            }
                                        }
                                        speakText(screenText, "mishkat_screen_reader")
                                    },
                                    border = BorderStroke(1.dp, Color(0xFF0EA5E9).copy(alpha = 0.5f)),
                                    shape = CutCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = Translator.tr("قراءة الشاشة كاملة"), tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                }
                                OutlinedButton(
                                    onClick = { showVoicePicker = true },
                                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                                    shape = CutCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.RecordVoiceOver, contentDescription = Translator.tr("تغيير الصوت"), tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                }
                            }

                            // نافذة اختيار الصوت العربي 🎙️
                            if (showVoicePicker) {
                                val arabicVoices = remember(tts, isTtsReady) {
                                    runCatching {
                                        tts?.voices?.filter { it.locale.language == "ar" }
                                            ?.sortedBy { it.name }.orEmpty()
                                    }.getOrDefault(emptyList())
                                }
                                var rate by remember { mutableFloatStateOf(voicePrefs.getFloat("rate", 0.9f)) }
                                var pitch by remember { mutableFloatStateOf(voicePrefs.getFloat("pitch", 1.0f)) }
                                AlertDialog(
                                    onDismissRequest = { showVoicePicker = false },
                                    confirmButton = {
                                        TextButton(onClick = { showVoicePicker = false }) { Text(Translator.tr("تم"), fontFamily = CairoFont) }
                                    },
                                    title = { Text(Translator.tr("اختر صوت القراءة 🎙️"), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
                                    text = {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            if (arabicVoices.isEmpty()) {
                                                Text(
                                                    Translator.tr("لا توجد أصوات عربية مثبتة — ثبّت حزمة الصوت العربي من إعدادات تحويل النص إلى كلام في جهازك ثم عُد."),
                                                    color = qabasTextSecondary(),
                                                    fontFamily = CairoFont,
                                                    fontSize = 12.sp
                                                )
                                            } else {
                                                LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                                                    items(arabicVoices) { voice ->
                                                        val selected = voicePrefs.getString("voice_name", null) == voice.name
                                                        Surface(
                                                            onClick = {
                                                                voicePrefs.edit().putString("voice_name", voice.name).apply()
                                                                applySavedVoice()
                                                                tts?.speak("السلام عليكم، هذا صوتي الجديد للقراءة", TextToSpeech.QUEUE_FLUSH, null, "voice_preview")
                                                            },
                                                            shape = RoundedCornerShape(10.dp),
                                                            color = if (selected) GoldPrimary.copy(alpha = 0.18f) else Color(0xFF1E293B),
                                                            border = BorderStroke(1.dp, if (selected) GoldPrimary else Color.Transparent),
                                                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                                        ) {
                                                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(
                                                                    if (selected) Icons.Default.CheckCircle else Icons.Default.RecordVoiceOver,
                                                                    null,
                                                                    tint = if (selected) GoldPrimary else qabasTextSecondary(),
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Text(
                                                                    voice.name.take(40),
                                                                    color = Color.White,
                                                                    fontFamily = NotoSansFont,
                                                                    fontSize = 11.sp,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(Translator.tr("سرعة النطق: ") + String.format("%.1f", rate), color = GoldPrimary, fontFamily = CairoFont, fontSize = 12.sp)
                                            Slider(
                                                value = rate,
                                                onValueChange = {
                                                    rate = it
                                                    voicePrefs.edit().putFloat("rate", it).apply()
                                                    applySavedVoice()
                                                },
                                                valueRange = 0.5f..1.5f,
                                                onValueChangeFinished = {
                                                    tts?.speak("هذه سرعة النطق الجديدة", TextToSpeech.QUEUE_FLUSH, null, "rate_preview")
                                                }
                                            )
                                            Text(Translator.tr("طبقة الصوت: ") + String.format("%.1f", pitch), color = GoldPrimary, fontFamily = CairoFont, fontSize = 12.sp)
                                            Slider(
                                                value = pitch,
                                                onValueChange = {
                                                    pitch = it
                                                    voicePrefs.edit().putFloat("pitch", it).apply()
                                                    applySavedVoice()
                                                },
                                                valueRange = 0.5f..1.5f,
                                                onValueChangeFinished = {
                                                    tts?.speak("هذه طبقة الصوت الجديدة", TextToSpeech.QUEUE_FLUSH, null, "pitch_preview")
                                                }
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // آية وتفسيرها الميسر مع التدوير التلقائي والعشوائي
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CutCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = qabasCardSurface().copy(alpha = 0.95f)),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.45f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // الرأس: شارة الآية والتفسير + سورة وآية + أزرار التحكم
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = GoldPrimary.copy(alpha = 0.15f),
                                    shape = CutCornerShape(6.dp),
                                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.45f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "آية وتفسيرها",
                                            color = GoldPrimary,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }

                                Surface(
                                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                                    shape = CutCornerShape(6.dp),
                                    border = BorderStroke(0.8.dp, Color(0xFF10B981).copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        text = "سورة ${currentAyah.surahName} • آية ${currentAyah.ayahNumber}",
                                        color = Color(0xFF34D399),
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // زر تبديل التدوير التلقائي (إيقاف مؤقت / استئناف)
                                IconButton(
                                    onClick = {
                                        viewModel.toggleAutoRotate()
                                        Toast.makeText(
                                            context,
                                            if (!isAutoRotating) Translator.tr("تم تفعيل التغيير التلقائي للآيات ⏱️")
                                            else Translator.tr("تم إيقاف التغيير التلقائي مؤقتاً ⏸️"),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isAutoRotating) Icons.Default.Autorenew else Icons.Default.PauseCircle,
                                        contentDescription = Translator.tr("التدوير التلقائي"),
                                        tint = if (isAutoRotating) GoldPrimary else qabasTextSecondary(),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // قراءة صوتية
                                IconButton(
                                    onClick = {
                                        if (isTtsReady && tts != null) {
                                            tts?.speak(currentAyah.text, TextToSpeech.QUEUE_FLUSH, null, "ayah_tts")
                                            Toast.makeText(context, Translator.tr("جاري تلاوة الآية الكريمة 🔊"), Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, Translator.tr("محرك الصوت غير متوفر في جهازك"), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.VolumeUp, contentDescription = Translator.tr("استماع"), tint = GoldPrimary, modifier = Modifier.size(18.dp))
                                }

                                // نسخ الآية مع تفسيرها
                                IconButton(
                                    onClick = {
                                        val fullText = "«${currentAyah.text}»\nسورة ${currentAyah.surahName} • آية ${currentAyah.ayahNumber}\n\n💡 التفسير الميسر:\n${currentAyah.tafsir}\n\n✨ تطبيق قبس — مشكاة الهدى"
                                        clipboardManager.setText(AnnotatedString(fullText))
                                        Toast.makeText(context, Translator.tr("تم نسخ الآية وتفسيرها إلى الحافظة 📋"), Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = Translator.tr("نسخ"), tint = qabasTextSecondary(), modifier = Modifier.size(18.dp))
                                }

                                // زر التبديل العشوائي الفوري
                                IconButton(
                                    onClick = {
                                        viewModel.nextRandomAyah()
                                        Toast.makeText(context, Translator.tr("آية عشوائية جديدة مع التفسير ✨"), Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.Shuffle, contentDescription = Translator.tr("آية عشوائية"), tint = GoldPrimary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // نص الآية الكريمة — اللمس في وضع الاستماع يتلوها صوتياً
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF090D18).copy(alpha = 0.65f), CutCornerShape(10.dp))
                                .border(1.dp, GoldPrimary.copy(alpha = 0.2f), CutCornerShape(10.dp))
                                .clickable(enabled = listenMode) {
                                    speakText("قال الله تعالى من سورة ${currentAyah.surahName}: ${currentAyah.text}", "ayah_tts")
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "« ${currentAyah.text} »",
                                color = Color(0xFFFDE68A),
                                fontFamily = AmiriFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.5.sp,
                                lineHeight = 28.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // قسم التفسير الميسر — اللمس في وضع الاستماع يقرؤه صوتياً
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0D1424).copy(alpha = 0.75f), CutCornerShape(10.dp))
                                .border(0.8.dp, Color(0xFF1E2D4A), CutCornerShape(10.dp))
                                .clickable(enabled = listenMode) {
                                    speakText("التفسير الميسر: ${currentAyah.tafsir}", "tafsir_tts")
                                }
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(15.dp))
                                Text(
                                    text = "التفسير الميسر:",
                                    color = GoldPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = currentAyah.tafsir,
                                color = qabasTextPrimary(),
                                fontFamily = NotoSansFont,
                                fontSize = 12.5.sp,
                                lineHeight = 21.sp,
                                textAlign = TextAlign.Start
                            )
                        }

                        // شريط تقدم التدوير التلقائي الناعم
                        if (isAutoRotating) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { autoRotateProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.5.dp)
                                    .clip(CircleShape),
                                color = GoldPrimary.copy(alpha = 0.85f),
                                trackColor = GoldPrimary.copy(alpha = 0.15f)
                            )
                        }
                    }
                }
            }

            // Tasbeeh
            item {
                Surface(
                    color = qabasCardSurface().copy(alpha = 0.9f),
                    shape = CutCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E2B44)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEAB308).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.TouchApp, contentDescription = null, tint = Color(0xFFEAB308), modifier = Modifier.size(15.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    Translator.tr("السبحة الذكية المدمجة"),
                                    color = qabasTextPrimary(),
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                // Vibration Toggle
                                Icon(
                                    imageVector = if (vibrateEvery33) Icons.Default.Vibration else Icons.Default.DoNotDisturbOn,
                                    contentDescription = null,
                                    tint = if (vibrateEvery33) GoldPrimary else qabasTextSecondary().copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { viewModel.toggleVibrateEvery33() }
                                )
                            }
                            TextButton(onClick = {
                                AppSoundManager.playToggle()
                                viewModel.resetTasbeeh()
                            }) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, tint = qabasTextSecondary(), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(Translator.tr("تصفير"), color = qabasTextSecondary(), fontFamily = CairoFont, fontSize = 10.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                val nextCount = tasbeehCount + 1
                                if (vibrateEvery33 && (nextCount % 33 == 0 || nextCount % 100 == 0)) {
                                    AppSoundManager.playSuccess()
                                } else {
                                    AppSoundManager.playDhikr()
                                }
                                viewModel.incrementTasbeeh()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF152035)),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                            shape = CutCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(Translator.tr("اضغط للسبحة"), color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                VerticalDivider(modifier = Modifier.height(20.dp), color = GoldPrimary.copy(alpha = 0.3f))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(tasbeehCount.toString(), color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            }
                        }
                    }
                }
            }

            // Daily Progress
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CutCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.85f)),
                    border = BorderStroke(1.dp, Color(0xFF1E2D4A))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TrackChanges, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    Translator.tr("إنجاز اليوم الإيماني"),
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Text(
                                "$dailyProgressPercent%",
                                color = GoldPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                fontFamily = CairoFont
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        LinearProgressIndicator(
                            progress = { dailyProgressPercent / 100f },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            color = GoldPrimary,
                            trackColor = Color(0xFF1E2D4A)
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DailyGoalChip(
                                    modifier = Modifier.weight(1f),
                                    label = Translator.tr("الورد اليومي"),
                                    isDone = isWirdDone,
                                    onClick = { viewModel.toggleWirdDone() }
                                )
                                DailyGoalChip(
                                    modifier = Modifier.weight(1f),
                                    label = Translator.tr("أذكار الصباح"),
                                    isDone = isMorningAzkarDone,
                                    onClick = { viewModel.toggleMorningAzkarDone() }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DailyGoalChip(
                                    modifier = Modifier.weight(1f),
                                    label = Translator.tr("أذكار المساء"),
                                    isDone = isEveningAzkarDone,
                                    onClick = { viewModel.toggleEveningAzkarDone() }
                                )
                                val tasbeehLabel = if (tasbeehCount >= 33) {
                                    "${Translator.tr("تسبيح")} ($tasbeehCount)"
                                } else {
                                    "${Translator.tr("تسبيح")} ($tasbeehCount/33)"
                                }
                                DailyGoalChip(
                                    modifier = Modifier.weight(1f),
                                    label = tasbeehLabel,
                                    isDone = isTasbeehDone,
                                    onClick = { viewModel.toggleTasbeehGoal() }
                                )
                            }
                        }
                    }
                }
            }

            // Upcoming Islamic Events Banner
            item {
                IslamicEventsBanner(events = upcomingEvents)
            }

            // --- Muslim Tools Section (15 Tools Grid) ---
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            Translator.tr("أدوات المسلم الذكية"),
                            color = GoldPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldPrimary.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Grid of tools (3 columns)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        viewModel.muslimToolsList.chunked(3).forEach { rowItems ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                rowItems.forEach { item ->
                                    MuslimToolCard(
                                        title = item.title,
                                        icon = item.icon,
                                        color = item.color,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            when (item.id) {
                                                "qibla" -> onNavigate(AppState.PRAYER_TIMES)
                                                "wird" -> onNavigate(AppState.QURAN_HUB)
                                                "hadith" -> onNavigate(AppState.ISLAMIC_LIBRARY)
                                                "learn" -> onNavigate(AppState.SCHOLAR_BIOGRAPHIES)
                                                "share" -> onNavigate(AppState.PHOTO_STUDIO)
                                                "reward" -> onNavigate(AppState.REWARDS)
                                                else -> viewModel.showToolDialog(item.id)
                                            }
                                        }
                                    )
                                }
                                if (rowItems.size < 3) {
                                    repeat(3 - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Sections
            if (filteredSections.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.SearchOff, contentDescription = null, tint = qabasTextSecondary(), modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(Translator.tr("لا توجد نتائج بحث تطابق استفسارك"), color = qabasTextPrimary(), fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                        Text(Translator.tr("جرب البحث بكلمات أخرى أو تصفح الأقسام"), color = qabasTextSecondary(), fontSize = 12.sp)
                    }
                }
            } else {
                if (isGridView) {
                    item {
                        // We use a custom grid layout here within LazyColumn item to maintain the scroll position
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            filteredSections.chunked(2).forEach { rowItems ->
                                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    rowItems.forEach { item ->
                                        HasanatGridItem(item, modifier = Modifier.weight(1f)) {
                                            if (listenMode) speakText("${item.titleKey}. ${item.subtitleKey}", "section_title")
                                            else onNavigate(item.route)
                                        }
                                    }
                                    if (rowItems.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(filteredSections) { item ->
                        HasanatListItem(item) {
                            if (listenMode) speakText("${item.titleKey}. ${item.subtitleKey}", "section_title")
                            else onNavigate(item.route)
                        }
                    }
                }
            }

            // Recent Design Section
            recentDesign?.let { design ->
                item {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            Translator.tr("آخر تصاميمك الإبداعية"),
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .clickable { onNavigate(AppState.PROJECTS) },
                            shape = CutCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF151B2B)),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black)
                                ) {
                                    AsyncImage(
                                        model = design.imagePath,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(design.text, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                                    Text(design.styleName, color = GoldPrimary, fontSize = 10.sp)
                                    Text(Translator.tr("منذ ") + java.text.SimpleDateFormat("d MMMM", java.util.Locale.getDefault()).format(java.util.Date(design.createdAt)), color = qabasTextSecondary(), fontSize = 9.sp)
                                }
                                Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = qabasTextSecondary())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DailyGoalChip(
    label: String,
    isDone: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        color = if (isDone) GoldPrimary.copy(alpha = 0.15f) else Color(0xFF1E2D4A).copy(alpha = 0.4f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (isDone) GoldPrimary.copy(alpha = 0.45f) else Color(0xFF1E2D4A)),
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.clickable {
                    AppSoundManager.playToggle()
                    onClick()
                }
            } else Modifier
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Icon(
                if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isDone) GoldPrimary else qabasTextSecondary(),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                label,
                color = if (isDone) GoldPrimary else qabasTextSecondary(),
                fontSize = 11.sp,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun QuickMishkatMetric(icon: ImageVector, label: String, value: String, accent: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp, fontFamily = CairoFont)
        Text(value, color = accent, fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = NotoSansFont)
    }
}

@Composable
fun HasanatGridItem(item: HasanatSectionItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.height(160.dp).clickable {
            AppSoundManager.playClick()
            onClick()
        },
        shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp),
        colors = CardDefaults.cardColors(containerColor = qabasCardSurface().copy(alpha = 0.8f)),
        border = BorderStroke(1.dp, item.accentColor.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(
                modifier = Modifier.size(40.dp).background(item.accentColor.copy(alpha = 0.15f), CutCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(item.icon, contentDescription = null, tint = item.accentColor, modifier = Modifier.size(24.dp))
            }
            Column {
                Text(Translator.tr(item.titleKey), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = CairoFont, maxLines = 2)
                Spacer(modifier = Modifier.height(2.dp))
                Text(Translator.tr(item.badgeKey), color = item.accentColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun HasanatListItem(item: HasanatSectionItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable {
            AppSoundManager.playClick()
            onClick()
        },
        shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
        colors = CardDefaults.cardColors(containerColor = qabasCardSurface().copy(alpha = 0.8f)),
        border = BorderStroke(1.dp, item.accentColor.copy(alpha = 0.2f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).background(item.accentColor.copy(alpha = 0.15f), CutCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(item.icon, contentDescription = null, tint = item.accentColor, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(Translator.tr(item.titleKey), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = CairoFont)
                Text(Translator.tr(item.subtitleKey), color = qabasTextSecondary(), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                Text(Translator.tr(item.badgeKey), color = item.accentColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = qabasTextSecondary().copy(alpha = 0.5f))
        }
    }
}

@Composable
fun MuslimToolCard(
    title: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = qabasCardSurface().copy(alpha = 0.8f),
        shape = CutCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f))
                    .border(1.dp, color.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                Translator.tr(title),
                color = Color.White,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                minLines = 2
            )
        }
    }
}

@Composable
fun IslamicEventsBanner(events: List<MuslimToolsEngine.IslamicEvent>) {
    Text(
        Translator.tr("المناسبات الإسلامية القادمة"),
        color = GoldPrimary,
        fontFamily = CairoFont,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(events) { event ->
            Surface(
                color = Color(0xFF0F172A).copy(alpha = 0.8f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f)),
                modifier = Modifier.width(180.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Celebration, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(event.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont, maxLines = 1)
                        Text("${event.daysRemaining} ${Translator.tr("يوم متبقي")}", color = GoldPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(event.dateHijri, color = qabasTextSecondary(), fontSize = 9.sp)
                    }
                }
            }
        }
    }
}
