package com.qabas.app

import android.content.Intent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleSearchBar
import com.qabas.app.ui.components.AppleTopBar
import com.qabas.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HasanatHubScreen(
    onBack: () -> Unit,
    onNavigate: (AppState) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    viewModel: HasanatHubViewModel = viewModel()
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val clipboardManager = LocalClipboardManager.current
    val hapticFeedback = LocalHapticFeedback.current

    val searchQuery by viewModel.searchQuery.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val tasbeehCount by viewModel.tasbeehCount.collectAsState()
    val vibrateEvery33 by viewModel.vibrateEvery33.collectAsState()
    val prayerTimeInfo by viewModel.prayerTimeInfo.collectAsState()
    val dateInfo by viewModel.dateInfo.collectAsState()
    val filteredSections by viewModel.filteredSections.collectAsState()

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
    val upcomingEvents by viewModel.upcomingEvents.collectAsState()

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    var listenMode by remember { mutableStateOf(false) }
    var hasShownTtsInstallPrompt by remember { mutableStateOf(false) }
    var showVoicePicker by remember { mutableStateOf(false) }
    val voicePrefs = remember { context.getSharedPreferences("mishkat_voice_prefs", android.content.Context.MODE_PRIVATE) }

    fun applySavedVoice() {
        val ttsEngine = tts ?: return
        try {
            ttsEngine.language = Locale("ar")
            ttsEngine.setSpeechRate(voicePrefs.getFloat("rate", 0.9f))
            ttsEngine.setPitch(voicePrefs.getFloat("pitch", 1.0f))
            val savedName = voicePrefs.getString("voice_name", null)
            if (!savedName.isNullOrBlank()) {
                ttsEngine.voices?.firstOrNull { it.name == savedName }?.let { ttsEngine.voice = it }
            }
        } catch (_: Exception) {}
    }

    fun speakText(text: String, utteranceId: String = "mishkat_listen") {
        if (text.isBlank()) return
        applySavedVoice()
        if (!isTtsReady || tts == null) {
            if (!hasShownTtsInstallPrompt) {
                hasShownTtsInstallPrompt = true
                try {
                    context.startActivity(
                        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    Toast.makeText(context, "ثبّت الصوت العربي من النافذة ثم عُد والمس النص مجدداً", Toast.LENGTH_LONG).show()
                } catch (_: Exception) {
                    Toast.makeText(context, "محرك الصوت غير متوفر في جهازك", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "ثبّت محرك الصوت العربي أولاً ثم أعد المحاولة.", Toast.LENGTH_SHORT).show()
            }
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
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

    LaunchedEffect(isTtsReady) {
        if (isTtsReady) {
            hasShownTtsInstallPrompt = false
            applySavedVoice()
        }
    }

    var isSearchExpanded by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            containerColor = theme.cardSurface,
            shape = RoundedCornerShape(18.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(theme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        Translator.tr("حول مِشكاة الهُدى"),
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        Translator.tr("مِشكاة الهُدى هو ركنك الإيماني المتكامل ضمن تطبيق خادم المسلم، يهدف إلى تيسير الوصول إلى كنوز الوحيين الشريفين وعلوم سلف الأمة."),
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontSize = 13.5.sp,
                        lineHeight = 21.sp
                    )
                    Text(
                        Translator.tr("يحتوي هذا الركن على المصحف المرتل، شروحات الأحاديث، مواقيت الصلاة، الأذكار، وسير العلماء، لتكون لك عوناً في رحلتك الإيمانية اليومية."),
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp
                    )
                    HorizontalDivider(color = theme.separator.copy(alpha = 0.5f))
                    Text(
                        Translator.tr("نسخة التطبيق: 1.3.7 (الإصدار الذهبي)"),
                        color = theme.primary,
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showHelpDialog = false },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(Translator.tr("حسناً"), color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    when (activeToolDialog) {
        "zakat" -> ZakatCalculatorDialog(onDismiss = { viewModel.dismissToolDialog() }, engine = engine)
        "fasting" -> FastingTrackerDialog(onDismiss = { viewModel.dismissToolDialog() }, engine = engine)
        "khatma" -> QuranKhatmaDialog(onDismiss = { viewModel.dismissToolDialog() }, engine = engine)
        "hajj" -> HajjUmrahDialog(onDismiss = { viewModel.dismissToolDialog() })
        "hijri" -> HijriCalendarDialog(onDismiss = { viewModel.dismissToolDialog() }, engine = engine)
        "habits" -> HabitsTrackerDialog(onDismiss = { viewModel.dismissToolDialog() }, engine = engine)
        "stats" -> WorshipAnalyticsDialog(onDismiss = { viewModel.dismissToolDialog() }, engine = engine)
    }

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
        containerColor = theme.background,
        bottomBar = bottomBar,
        topBar = {
            AppleTopBar(
                title = Translator.tr("مِشكاة الهُدى"),
                subtitle = Translator.tr("رحاب القرآن والسنة وعلوم سلف الأمة"),
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = { isSearchExpanded = !isSearchExpanded },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = Translator.tr("بحث"),
                            tint = if (isSearchExpanded) theme.primary else theme.textSecondary
                        )
                    }
                    IconButton(
                        onClick = { viewModel.toggleViewMode() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = Translator.tr("نمط العرض"),
                            tint = theme.primary
                        )
                    }
                    IconButton(
                        onClick = { showHelpDialog = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = Translator.tr("حول مشكاة الهدى"),
                            tint = theme.textSecondary
                        )
                    }
                }
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "mishkat_reminder") {
                    MishkatReminderCard()
                }

                if (isSearchExpanded) {
                    item {
                        AppleSearchBar(
                            query = searchQuery,
                            onQueryChange = { viewModel.updateSearchQuery(it) },
                            placeholder = Translator.tr("ابحث في أبواب ومحتويات مشكاة الهدى...")
                        )
                    }
                }

                // Hero Card: Date + Next Prayer + Audio Assist
                item {
                    AppleCard(cornerRadius = 18.dp) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = theme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = dateInfo.hijriStr,
                                        color = theme.primary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Surface(
                                    color = theme.primary.copy(alpha = 0.12f),
                                    shape = CircleShape
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Verified, contentDescription = null, tint = theme.primary, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            Translator.tr("مشكاة الهدى"),
                                            color = theme.primary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = CairoFont
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Surface(
                                color = theme.secondarySurface.copy(alpha = if (theme.isDark) 0.7f else 0.45f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (listenMode) speakText(
                                            "الصلاة القادمة: صلاة ${prayerTimeInfo.nextName} في ${prayerTimeInfo.nextTime}. متبقي على النداء: ${prayerTimeInfo.countdown}.",
                                            "prayer_info"
                                        ) else onNavigate(AppState.PRAYER_TIMES)
                                    }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(theme.primary.copy(alpha = 0.14f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.AccessTime, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        Translator.tr("الصلاة القادمة: صلاة") + " ${Translator.tr(prayerTimeInfo.nextName)}",
                                                        color = theme.textPrimary,
                                                        fontFamily = CairoFont,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Canvas(modifier = Modifier.size(7.dp)) {
                                                        drawCircle(color = theme.success.copy(alpha = pulseAlpha))
                                                    }
                                                }
                                                Text(
                                                    prayerTimeInfo.nextTime,
                                                    color = theme.primary,
                                                    fontFamily = RobotoMonoFont,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(18.dp))
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = theme.cardSurface,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = Translator.tr("المتبقي حتى الأذان"),
                                                fontFamily = CairoFont,
                                                fontSize = 12.sp,
                                                color = theme.textSecondary
                                            )
                                            Text(
                                                text = prayerTimeInfo.countdown,
                                                fontFamily = RobotoMonoFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp,
                                                color = theme.textPrimary,
                                                letterSpacing = 1.sp
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(theme.secondarySurface.copy(alpha = if (theme.isDark) 0.5f else 0.35f))
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(Modifier.weight(1f)) {
                                    QuickMishkatMetric(Icons.Default.AutoStories, Translator.tr("الأحاديث"), hadithStats, theme.primary)
                                }
                                Box(Modifier.weight(1f)) {
                                    QuickMishkatMetric(Icons.Default.TouchApp, Translator.tr("الأذكار"), azkarStats, theme.warning)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        listenMode = !listenMode
                                        if (!listenMode) tts?.stop()
                                        else Toast.makeText(context, "وضع الاستماع مفعّل — المس أي نص لسماعه", Toast.LENGTH_LONG).show()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (listenMode) theme.success else theme.primary,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        if (listenMode) Icons.Default.Hearing else Icons.Default.TouchApp,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (listenMode) Translator.tr("وضع الاستماع مفعّل") else Translator.tr("وضع الاستماع"),
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        val screenText = buildString {
                                            append("السلام عليكم. أنت في مشكاة الهدى. ")
                                            append("اليوم ${dateInfo.hijriStr}. ")
                                            append("الصلاة القادمة: صلاة ${prayerTimeInfo.nextName} في ${prayerTimeInfo.nextTime}، متبقي ${prayerTimeInfo.countdown}. ")
                                            append("آية اليوم من سورة ${currentAyah.surahName}: ${currentAyah.text}. ")
                                        }
                                        speakText(screenText, "mishkat_screen_reader")
                                    },
                                    modifier = Modifier.size(48.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = Translator.tr("قراءة الشاشة كاملة"), tint = theme.primary, modifier = Modifier.size(20.dp))
                                }
                                OutlinedButton(
                                    onClick = { showVoicePicker = true },
                                    modifier = Modifier.size(48.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    border = BorderStroke(1.dp, theme.separator),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.RecordVoiceOver, contentDescription = Translator.tr("تغيير الصوت"), tint = theme.primary, modifier = Modifier.size(20.dp))
                                }
                            }

                            if (showVoicePicker) {
                                val arabicVoices = remember(tts, isTtsReady) {
                                    runCatching {
                                        tts?.voices?.filter { it.locale.language == "ar" }?.sortedBy { it.name }.orEmpty()
                                    }.getOrDefault(emptyList())
                                }
                                var rate by remember { mutableFloatStateOf(voicePrefs.getFloat("rate", 0.9f)) }
                                var pitch by remember { mutableFloatStateOf(voicePrefs.getFloat("pitch", 1.0f)) }
                                AlertDialog(
                                    onDismissRequest = { showVoicePicker = false },
                                    containerColor = theme.cardSurface,
                                    shape = RoundedCornerShape(18.dp),
                                    confirmButton = {
                                        TextButton(
                                            onClick = { showVoicePicker = false },
                                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                        ) {
                                            Text(Translator.tr("تم"), fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = theme.primary)
                                        }
                                    },
                                    title = {
                                        Text(
                                            Translator.tr("اختر صوت القراءة"),
                                            color = theme.textPrimary,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    },
                                    text = {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            if (arabicVoices.isEmpty()) {
                                                Text(
                                                    Translator.tr("لا توجد أصوات عربية مثبتة — ثبّت حزمة الصوت العربي من إعدادات تحويل النص إلى كلام في جهازك ثم عُد."),
                                                    color = theme.textSecondary,
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
                                                            color = if (selected) theme.primary.copy(alpha = 0.14f) else theme.secondarySurface.copy(alpha = 0.6f),
                                                            border = BorderStroke(1.dp, if (selected) theme.primary else Color.Transparent),
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(vertical = 3.dp)
                                                        ) {
                                                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(
                                                                    if (selected) Icons.Default.CheckCircle else Icons.Default.RecordVoiceOver,
                                                                    contentDescription = null,
                                                                    tint = if (selected) theme.primary else theme.textSecondary,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Text(
                                                                    voice.name.take(40),
                                                                    color = theme.textPrimary,
                                                                    fontFamily = NotoSansFont,
                                                                    fontSize = 11.5.sp,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(Translator.tr("سرعة النطق: ") + String.format("%.1f", rate), color = theme.primary, fontFamily = CairoFont, fontSize = 12.sp)
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
                                            Text(Translator.tr("طبقة الصوت: ") + String.format("%.1f", pitch), color = theme.primary, fontFamily = CairoFont, fontSize = 12.sp)
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

                // Ayah & Tafsir Card
                item {
                    AppleCard(cornerRadius = 16.dp) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        color = theme.primary.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = theme.primary, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "آية وتفسيرها",
                                                color = theme.primary,
                                                fontFamily = CairoFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp
                                            )
                                        }
                                    }

                                    Crossfade(targetState = currentAyah, label = "surah_badge_transition") { ayah ->
                                        Surface(
                                            color = theme.success.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "سورة ${ayah.surahName} • ${ayah.ayahNumber}",
                                                color = theme.success,
                                                fontFamily = CairoFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            AppSoundManager.playToggle()
                                            viewModel.toggleAutoRotate()
                                            Toast.makeText(
                                                context,
                                                if (!isAutoRotating) Translator.tr("تم تفعيل التغيير التلقائي للآيات")
                                                else Translator.tr("تم إيقاف التغيير التلقائي مؤقتاً"),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isAutoRotating) Icons.Default.Autorenew else Icons.Default.PauseCircle,
                                            contentDescription = Translator.tr("التدوير التلقائي"),
                                            tint = if (isAutoRotating) theme.primary else theme.textSecondary,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            AppSoundManager.playClick()
                                            speakText("قال الله تعالى من سورة ${currentAyah.surahName}: ${currentAyah.text}", "ayah_tts")
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.VolumeUp, contentDescription = Translator.tr("استماع"), tint = theme.primary, modifier = Modifier.size(19.dp))
                                    }

                                    IconButton(
                                        onClick = {
                                            AppSoundManager.playClick()
                                            val fullText = "«${currentAyah.text}»\nسورة ${currentAyah.surahName} • آية ${currentAyah.ayahNumber}\n\nالتفسير الميسر:\n${currentAyah.tafsir}\n\nتطبيق خادم المسلم"
                                            clipboardManager.setText(AnnotatedString(fullText))
                                            Toast.makeText(context, Translator.tr("تم نسخ الآية وتفسيرها إلى الحافظة"), Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = Translator.tr("نسخ"), tint = theme.textSecondary, modifier = Modifier.size(19.dp))
                                    }

                                    IconButton(
                                        onClick = {
                                            AppSoundManager.playClick()
                                            viewModel.nextRandomAyah()
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.Shuffle, contentDescription = Translator.tr("آية عشوائية"), tint = theme.primary, modifier = Modifier.size(19.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Crossfade(targetState = currentAyah, label = "ayah_content_transition") { ayah ->
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(theme.secondarySurface.copy(alpha = if (theme.isDark) 0.65f else 0.4f))
                                            .clickable(enabled = listenMode) {
                                                speakText("قال الله تعالى من سورة ${ayah.surahName}: ${ayah.text}", "ayah_tts")
                                            }
                                            .padding(horizontal = 14.dp, vertical = 12.dp)
                                    ) {
                                        Text(
                                            text = "« ${ayah.text} »",
                                            color = theme.textPrimary,
                                            fontFamily = AmiriFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            lineHeight = 31.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(theme.secondarySurface.copy(alpha = if (theme.isDark) 0.4f else 0.25f))
                                            .clickable(enabled = listenMode) {
                                                speakText("التفسير الميسر: ${ayah.tafsir}", "tafsir_tts")
                                            }
                                            .padding(12.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = theme.primary, modifier = Modifier.size(15.dp))
                                            Text(
                                                text = "التفسير الميسر:",
                                                color = theme.primary,
                                                fontFamily = CairoFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = ayah.tafsir,
                                            color = theme.textPrimary,
                                            fontFamily = NotoSansFont,
                                            fontSize = 13.sp,
                                            lineHeight = 21.sp,
                                            textAlign = TextAlign.Start
                                        )
                                    }
                                }
                            }

                            if (isAutoRotating) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { autoRotateProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(CircleShape),
                                    color = theme.primary,
                                    trackColor = theme.separator.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }

                // Embedded Smart Tasbeeh
                item {
                    AppleCard(cornerRadius = 16.dp) {
                        Column(modifier = Modifier.padding(14.dp)) {
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
                                            .background(theme.warning.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.TouchApp, contentDescription = null, tint = theme.warning, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        Translator.tr("السبحة الذكية المدمجة"),
                                        color = theme.textPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp
                                    )
                                    IconButton(
                                        onClick = {
                                            AppSoundManager.playClick()
                                            viewModel.toggleVibrateEvery33()
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (vibrateEvery33) Icons.Default.Vibration else Icons.Default.DoNotDisturbOn,
                                            contentDescription = Translator.tr("الاهتزاز"),
                                            tint = if (vibrateEvery33) theme.primary else theme.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = {
                                        AppSoundManager.playClick()
                                        viewModel.resetTasbeeh()
                                    },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Icon(Icons.Default.RestartAlt, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(Translator.tr("تصفير"), color = theme.textSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Button(
                                onClick = {
                                    val nextCount = tasbeehCount + 1
                                    val reached33 = nextCount % 33 == 0
                                    if (vibrateEvery33 && reached33) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    if (reached33 || nextCount % 100 == 0) {
                                        AppSoundManager.playSuccess()
                                    } else {
                                        AppSoundManager.playDhikr()
                                    }
                                    viewModel.incrementTasbeeh()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = theme.primary,
                                    contentColor = theme.onPrimary
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 50.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(Translator.tr("اضغط للتسبيح"), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    VerticalDivider(modifier = Modifier.height(20.dp), color = theme.onPrimary.copy(alpha = 0.35f))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(tasbeehCount.toString(), fontFamily = RobotoMonoFont, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                                }
                            }
                        }
                    }
                }

                // Daily Progress
                item {
                    val animatedProgress by animateFloatAsState(
                        targetValue = dailyProgressPercent / 100f,
                        animationSpec = tween(800, easing = FastOutSlowInEasing),
                        label = "daily_progress_anim"
                    )
                    AppleCard(cornerRadius = 16.dp) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.TrackChanges, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        Translator.tr("إنجاز اليوم الإيماني"),
                                        color = theme.textPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Text(
                                    "$dailyProgressPercent%",
                                    color = theme.primary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    fontFamily = RobotoMonoFont
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(CircleShape),
                                color = theme.primary,
                                trackColor = theme.separator.copy(alpha = 0.45f)
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

                // Unified Hub Header
                item {
                    Text(
                        Translator.tr("استكشف رحاب المشكاة"),
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (filteredSections.isEmpty() && searchQuery.isNotBlank()) {
                    item {
                        AppleCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp, horizontal = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.MenuBook, contentDescription = null, tint = theme.primary, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = Translator.tr("لم نجد ما تبحث عنه في رياض المشكاة"),
                                    color = theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = Translator.tr("جرب البحث بكلمات أخرى أو تصفح كافة الأبواب أدناه"),
                                    color = theme.textSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedButton(
                                    onClick = {
                                        AppSoundManager.playClick()
                                        viewModel.updateSearchQuery("")
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.6f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(Translator.tr("عرض جميع الأبواب"), color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                } else {
                    val combinedItems = mutableListOf<Any>()
                    combinedItems.addAll(filteredSections)
                    val filteredTools = if (searchQuery.isBlank()) {
                        viewModel.muslimToolsList
                    } else {
                        viewModel.muslimToolsList.filter {
                            Translator.tr(it.title).contains(searchQuery, ignoreCase = true)
                        }
                    }
                    combinedItems.addAll(filteredTools)

                    if (isGridView) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                combinedItems.chunked(2).forEach { rowItems ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                        rowItems.forEach { item ->
                                            when (item) {
                                                is HasanatSectionItem -> {
                                                    UnifiedHasanatGridItem(
                                                        title = item.titleKey,
                                                        subtitle = item.badgeKey,
                                                        icon = item.icon,
                                                        color = item.accentColor,
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        if (listenMode) speakText("${item.titleKey}. ${item.subtitleKey}", "section_title")
                                                        else onNavigate(item.route)
                                                    }
                                                }
                                                is HasanatHubViewModel.MuslimToolItem -> {
                                                    UnifiedHasanatGridItem(
                                                        title = item.title,
                                                        subtitle = "أداة ذكية مفعّلة",
                                                        icon = item.icon,
                                                        color = item.color,
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        if (listenMode) speakText(item.title, "tool_title")
                                                        else {
                                                            when (item.id) {
                                                                "qibla" -> onNavigate(AppState.PRAYER_TIMES)
                                                                "hadith" -> onNavigate(AppState.ISLAMIC_LIBRARY)
                                                                "learn" -> onNavigate(AppState.SCHOLAR_BIOGRAPHIES)
                                                                "share" -> onNavigate(AppState.PHOTO_STUDIO)
                                                                "reward" -> onNavigate(AppState.REWARDS)
                                                                else -> viewModel.showToolDialog(item.id)
                                                            }
                                                        }
                                                    }
                                                }
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
                        items(combinedItems) { item ->
                            when (item) {
                                is HasanatSectionItem -> {
                                    UnifiedHasanatListItem(
                                        title = item.titleKey,
                                        subtitle = item.subtitleKey,
                                        badge = item.badgeKey,
                                        icon = item.icon,
                                        color = item.accentColor
                                    ) {
                                        if (listenMode) speakText("${item.titleKey}. ${item.subtitleKey}", "section_title")
                                        else onNavigate(item.route)
                                    }
                                }
                                is HasanatHubViewModel.MuslimToolItem -> {
                                    UnifiedHasanatListItem(
                                        title = item.title,
                                        subtitle = "أداة ذكية ميسرة للعبادات",
                                        badge = "مفعلة بالكامل",
                                        icon = item.icon,
                                        color = item.color
                                    ) {
                                        if (listenMode) speakText(item.title, "tool_title")
                                        else {
                                            when (item.id) {
                                                "qibla" -> onNavigate(AppState.PRAYER_TIMES)
                                                "hadith" -> onNavigate(AppState.ISLAMIC_LIBRARY)
                                                "learn" -> onNavigate(AppState.SCHOLAR_BIOGRAPHIES)
                                                "share" -> onNavigate(AppState.PHOTO_STUDIO)
                                                "reward" -> onNavigate(AppState.REWARDS)
                                                else -> viewModel.showToolDialog(item.id)
                                            }
                                        }
                                    }
                                }
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
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                            AppleCard(onClick = { onNavigate(AppState.PROJECTS) }) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(68.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(theme.secondarySurface)
                                    ) {
                                        AsyncImage(
                                            model = design.imagePath,
                                            contentDescription = design.text,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(design.text, color = theme.textPrimary, fontFamily = CairoFont, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                                        Text(design.styleName, color = theme.primary, fontFamily = NotoSansFont, fontSize = 11.sp)
                                        Text(
                                            Translator.tr("منذ ") + SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(design.createdAt)),
                                            color = theme.textSecondary,
                                            fontFamily = NotoSansFont,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = theme.textSecondary)
                                }
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
    val theme = QabasTheme.current
    Surface(
        color = if (isDone) theme.primary.copy(alpha = 0.14f) else theme.secondarySurface.copy(alpha = 0.55f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = 0.75.dp,
            color = if (isDone) theme.primary else theme.separator.copy(alpha = 0.45f)
        ),
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        AppSoundManager.playClick()
                        onClick()
                    }
                } else Modifier
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Icon(
                if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isDone) theme.primary else theme.textSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                label,
                color = if (isDone) theme.primary else theme.textPrimary,
                fontSize = 12.sp,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun QuickMishkatMetric(icon: ImageVector, label: String, value: String, accent: Color) {
    val theme = QabasTheme.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = theme.textSecondary, fontSize = 10.sp, fontFamily = CairoFont)
        Text(value, color = accent, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = NotoSansFont)
    }
}

@Composable
fun UnifiedHasanatGridItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val theme = QabasTheme.current
    AppleCard(
        modifier = modifier.heightIn(min = 148.dp),
        onClick = {
            AppSoundManager.playClick()
            onClick()
        }
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 148.dp)
                .padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            }
            Column {
                Text(
                    text = Translator.tr(title),
                    color = theme.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    fontFamily = CairoFont,
                    maxLines = 2,
                    lineHeight = 19.sp
                )
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = Translator.tr(subtitle),
                        color = color,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = CairoFont
                    )
                }
            }
        }
    }
}

@Composable
fun UnifiedHasanatListItem(
    title: String,
    subtitle: String,
    badge: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    val theme = QabasTheme.current
    AppleCard(
        onClick = {
            AppSoundManager.playClick()
            onClick()
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(Translator.tr(title), color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, fontFamily = CairoFont)
                Text(Translator.tr(subtitle), color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (badge.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(Translator.tr(badge), color = color, fontFamily = CairoFont, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = theme.textSecondary)
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
    UnifiedHasanatGridItem(
        title = title,
        subtitle = "",
        icon = icon,
        color = color,
        modifier = modifier,
        onClick = onClick
    )
}

@Composable
fun IslamicEventsBanner(events: List<MuslimToolsEngine.IslamicEvent>) {
    val theme = QabasTheme.current
    Column {
        Text(
            Translator.tr("المناسبات الإسلامية القادمة"),
            color = theme.textPrimary,
            fontFamily = CairoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(events) { event ->
                AppleCard(modifier = Modifier.width(190.dp)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(theme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Celebration, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(event.name, color = theme.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont, maxLines = 1)
                            Text("${event.daysRemaining} ${Translator.tr("يوم متبقي")}", color = theme.primary, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                            Text(event.dateHijri, color = theme.textSecondary, fontSize = 10.sp, fontFamily = NotoSansFont)
                        }
                    }
                }
            }
        }
    }
}
