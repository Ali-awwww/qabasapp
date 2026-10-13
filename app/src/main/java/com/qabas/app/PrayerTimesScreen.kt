package com.qabas.app

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerTimesScreen(
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val prefs = remember { context.getSharedPreferences("qabas_prayer_prefs", Context.MODE_PRIVATE) }

    // City & Method state
    var selectedCityIndex by remember {
        mutableIntStateOf(prefs.getInt("selected_city_index", 0).coerceIn(0, PrayerCalculationHelper.popularCities.size - 1))
    }
    val currentCity = PrayerCalculationHelper.popularCities[selectedCityIndex]

    var selectedMethod by remember {
        mutableStateOf(currentCity.defaultMethod)
    }

    // Audio Playback State
    val audioPlaybackState by AzanAudioPlayer.playbackState.collectAsState()

    // Tabs
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "⏱️ المواقيت",
        "📿 السجل والسنن",
        "⚠️ مرآة النذير",
        "🌟 رياض الجنة",
        "🧭 القبلة والخشوع"
    )

    // Live clock ticker
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    // Recalculate prayer times dynamically
    val prayerTimes = remember(selectedCityIndex, selectedMethod, currentTimeMillis) {
        val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
        PrayerCalculationHelper.calculatePrayerTimes(cal, currentCity, selectedMethod)
    }

    // Dialog & Sheet states
    var showCityDialog by remember { mutableStateOf(false) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var selectedPrayerForConfig by remember { mutableStateOf<String?>(null) }
    var shareTextContent by remember { mutableStateOf<String?>(null) }

    // Clean up audio on leave
    DisposableEffect(Unit) {
        onDispose {
            AzanAudioPlayer.stop()
        }
    }

    Scaffold(
        containerColor = Color(0xFF0D121F),
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(GoldPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Mosque, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "محراب الصلاة والمواقيت",
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "${currentCity.nameAr} • ${currentCity.countryAr}",
                                color = Color.White.copy(alpha = 0.7f),
                                fontFamily = NotoSansFont,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = GoldPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showCityDialog = true }) {
                        Icon(Icons.Default.LocationOn, contentDescription = "تغيير المدينة", tint = GoldPrimary)
                    }
                    IconButton(onClick = { showAudioSheet = true }) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "أصوات الأذان", tint = GoldPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D121F))
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Hero Live Countdown & Next Prayer Ring
            HeroPrayerCountdownCard(
                prayerTimes = prayerTimes,
                cityName = currentCity.nameAr,
                onOpenVoices = { showAudioSheet = true },
                onShareTimetable = {
                    val timetableText = """
                        🕌 مواقيت الصلاة اليوم في ${currentCity.nameAr} (${currentCity.countryAr}):
                        • الفجر: ${prayerTimes.fajr}
                        • الشروق: ${prayerTimes.sunrise}
                        • الظهر: ${prayerTimes.dhuhr}
                        • العصر: ${prayerTimes.asr}
                        • المغرب: ${prayerTimes.maghrib}
                        • العشاء: ${prayerTimes.isha}
                        • الثلث الأخير وقيام الليل: ${prayerTimes.qiyamStart}
                        
                        ⏱️ الصلاة القادمة: صلاة ${prayerTimes.nextPrayerName} بعد ${PrayerCalculationHelper.formatCountdown(prayerTimes.millisUntilNextPrayer)}
                        ﴿إِنَّ الصَّلَاةَ كَانَتْ عَلَى الْمُؤْمِنِينَ كِتَابًا مَّوْقُوتًا﴾
                        تطبيق قبس — مشكاة الهدى
                    """.trimIndent()
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, timetableText)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "مشاركة مواقيت الصلاة"))
                }
            )

            // Tabs Header
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF131B2E),
                contentColor = GoldPrimary,
                edgePadding = 12.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = GoldPrimary,
                        height = 3.dp
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontFamily = CairoFont,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                                color = if (selectedTab == index) GoldPrimary else Color.White.copy(alpha = 0.6f)
                            )
                        }
                    )
                }
            }

            // Tab Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (selectedTab) {
                    0 -> TimetableAndControlsTab(
                        prayerTimes = prayerTimes,
                        prefs = prefs,
                        onConfigPrayer = { prayerKey -> selectedPrayerForConfig = prayerKey },
                        onOpenVoices = { showAudioSheet = true }
                    )
                    1 -> PrayerHabitTrackerTab(prefs = prefs)
                    2 -> SpiritualTopicsTab(
                        topics = PrayerRepository.warningTopics,
                        headerTitle = "⚠️ مرآة النذير وعقوبات التهاون والتأخير",
                        headerDesc = "نصوص قاطعة وآثار نبوية تزلزل الغفلة وتوقظ القلب لحفظ الصلاة في وقتها",
                        onShareTopic = { topic ->
                            val text = """
                                ⚠️ ${topic.title}
                                ${topic.subtitle}
                                
                                ${topic.quranAyah?.let { "$it\n(${topic.quranSurahAndVerse})\n\n" } ?: ""}
                                ${topic.hadithText?.let { "$it\n— ${topic.hadithSource}\n\n" } ?: ""}
                                📌 الشرح والبيان:
                                ${topic.detailedTafsirAndLesson}
                                
                                💡 الخطوة العملية:
                                ${topic.practicalStep}
                                
                                ✨ تطبيق قبس — مشكاة الهدى
                            """.trimIndent()
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, text)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "مشاركة درس الصلاة"))
                        }
                    )
                    3 -> SpiritualTopicsTab(
                        topics = PrayerRepository.rewardTopics,
                        headerTitle = "🌟 رياض الجنة: ثواب المحافظة وحسن المقام",
                        headerDesc = "بشائر النور التام، تكفير الخطايا، رؤية وجه الله الكريم ورفقة النبي ﷺ في الفردوس",
                        onShareTopic = { topic ->
                            val text = """
                                🌟 ${topic.title}
                                ${topic.subtitle}
                                
                                ${topic.quranAyah?.let { "$it\n(${topic.quranSurahAndVerse})\n\n" } ?: ""}
                                ${topic.hadithText?.let { "$it\n— ${topic.hadithSource}\n\n" } ?: ""}
                                📌 الشرح والبيان:
                                ${topic.detailedTafsirAndLesson}
                                
                                💡 الخطوة العملية:
                                ${topic.practicalStep}
                                
                                ✨ تطبيق قبس — مشكاة الهدى
                            """.trimIndent()
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, text)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "مشاركة درس الصلاة"))
                        }
                    )
                    4 -> QiblaAndKhushooTab(
                        prayerTimes = prayerTimes,
                        cityName = currentCity.nameAr,
                        onShareTopic = { topic ->
                            val text = """
                                🧭 ${topic.title}
                                
                                ${topic.hadithText?.let { "$it\n— ${topic.hadithSource}\n\n" } ?: ""}
                                📌 ${topic.detailedTafsirAndLesson}
                                
                                💡 ${topic.practicalStep}
                                
                                ✨ تطبيق قبس — مشكاة الهدى
                            """.trimIndent()
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, text)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "مشاركة فقه الصلاة"))
                        }
                    )
                }
            }
        }
    }

    // City Selection Dialog
    if (showCityDialog) {
        AlertDialog(
            onDismissRequest = { showCityDialog = false },
            title = {
                Text(
                    "اختر المدينة والموقع",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    items(PrayerCalculationHelper.popularCities.indices.toList()) { index ->
                        val city = PrayerCalculationHelper.popularCities[index]
                        val isSelected = index == selectedCityIndex
                        Surface(
                            onClick = {
                                selectedCityIndex = index
                                selectedMethod = city.defaultMethod
                                prefs.edit().putInt("selected_city_index", index).apply()
                                showCityDialog = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) GoldPrimary.copy(alpha = 0.15f) else Color(0xFF1E293B),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) GoldPrimary else Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.LocationCity,
                                    contentDescription = null,
                                    tint = if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "${city.nameAr} (${city.countryAr})",
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) GoldPrimary else Color.White,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "${city.nameEn} • طريقة: ${city.defaultMethod.displayName}",
                                        fontFamily = NotoSansFont,
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCityDialog = false }) {
                    Text("إغلاق", color = GoldPrimary, fontFamily = CairoFont)
                }
            },
            containerColor = Color(0xFF151B2B)
        )
    }

    // Azan Voices Modal Sheet / Dialog
    if (showAudioSheet) {
        AzanVoicesDialog(
            playbackState = audioPlaybackState,
            onDismiss = {
                showAudioSheet = false
                AzanAudioPlayer.stop()
            },
            onPlayVoice = { voice ->
                AzanAudioPlayer.playVoice(context, voice)
            },
            onStopVoice = {
                AzanAudioPlayer.stop()
            }
        )
    }
}

// --- Hero Countdown Card with Ring ---
@Composable
fun HeroPrayerCountdownCard(
    prayerTimes: CalculatedPrayerTimes,
    cityName: String,
    onOpenVoices: () -> Unit,
    onShareTimetable: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF151D30),
        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f))
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1E293B),
                            Color(0xFF0F172A)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "الصلاة القادمة: صلاة ${prayerTimes.nextPrayerName}",
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    IconButton(
                        onClick = onShareTimetable,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = GoldPrimary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Circular Progress Ring & Digital Timer
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(150.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 10.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val radius = diameter / 2
                        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)

                        // Background Ring
                        drawArc(
                            color = Color.White.copy(alpha = 0.08f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(diameter, diameter),
                            style = Stroke(width = strokeWidth)
                        )

                        // Glowing Active Arc
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(
                                    GoldPrimary.copy(alpha = 0.5f),
                                    GoldPrimary,
                                    Color(0xFF10B981)
                                )
                            ),
                            startAngle = -90f,
                            sweepAngle = 360f * prayerTimes.progressToNextPrayer,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(diameter, diameter),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = PrayerCalculationHelper.formatCountdown(prayerTimes.millisUntilNextPrayer),
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "الأذان: ${prayerTimes.nextPrayerTimeFormatted}",
                            color = GoldPrimary.copy(alpha = glowAlpha),
                            fontFamily = NotoSansFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Bar: Listen to Adhan button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = onOpenVoices,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("أصوات الأذان", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onShareTimetable,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).padding(start = 6.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مشاركة الموعد", color = Color.White, fontFamily = CairoFont, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// --- Tab 1: Timetable & Notification Controls ---
@Composable
fun TimetableAndControlsTab(
    prayerTimes: CalculatedPrayerTimes,
    prefs: android.content.SharedPreferences,
    onConfigPrayer: (String) -> Unit,
    onOpenVoices: () -> Unit
) {
    val prayers = listOf(
        PrayerRowItem("fajr", "الفجر", prayerTimes.fajr, Icons.Default.Brightness3, Color(0xFF6366F1)),
        PrayerRowItem("sunrise", "الشروق", prayerTimes.sunrise, Icons.Default.WbSunny, Color(0xFFF59E0B)),
        PrayerRowItem("dhuhr", "الظهر", prayerTimes.dhuhr, Icons.Default.LightMode, Color(0xFFEAB308)),
        PrayerRowItem("asr", "العصر", prayerTimes.asr, Icons.Default.WbTwilight, Color(0xFFF97316)),
        PrayerRowItem("maghrib", "المغرب", prayerTimes.maghrib, Icons.Default.NightsStay, Color(0xFFEC4899)),
        PrayerRowItem("isha", "العشاء", prayerTimes.isha, Icons.Default.Bedtime, Color(0xFF8B5CF6)),
        PrayerRowItem("qiyam", "قيام الليل والثلث الأخير", prayerTimes.qiyamStart, Icons.Default.Star, GoldPrimary)
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // بطاقة فحص وإعداد أذونات العمل بالخلفية للأذان
        item {
            val context = LocalContext.current
            var showBgDialog by remember { mutableStateOf(false) }
            val isBgReady = remember { BackgroundPermissionManager.isAllBackgroundPermissionsGranted(context) }

            Surface(
                onClick = { showBgDialog = true },
                shape = RoundedCornerShape(14.dp),
                color = if (isBgReady) Color(0xFF10B981).copy(alpha = 0.12f) else GoldPrimary.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, if (isBgReady) Color(0xFF10B981).copy(alpha = 0.4f) else GoldPrimary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (isBgReady) Icons.Default.VerifiedUser else Icons.Default.BatterySaver,
                            contentDescription = null,
                            tint = if (isBgReady) Color(0xFF10B981) else GoldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isBgReady) "حماية الأذان بالخلفية: مفعّلة بالكامل ✅" else "تحسين دقة الأذان: تفعيل العمل بالخلفية ⚡",
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isBgReady) Color(0xFF10B981) else GoldPrimary
                            )
                            Text(
                                text = if (isBgReady) "التطبيق مستثنى من قيود البطارية لرفع الأذان في موعده الدقيق" else "انقر لمنح أذونات البطارية والمنبهات الدقيقة لضمان سماع الأذان",
                                fontFamily = NotoSansFont,
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = if (isBgReady) Color(0xFF10B981) else GoldPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (showBgDialog) {
                BackgroundPermissionsDialog(
                    isBatteryIgnored = BackgroundPermissionManager.isBatteryOptimizationIgnored(context),
                    canExactAlarm = BackgroundPermissionManager.canScheduleExactAlarms(context),
                    isNotificationGranted = BackgroundPermissionManager.areNotificationsEnabled(context),
                    onDismiss = { showBgDialog = false },
                    onRequestBattery = {
                        BackgroundPermissionManager.requestIgnoreBatteryOptimizations(context)
                    },
                    onRequestExactAlarm = {
                        BackgroundPermissionManager.requestScheduleExactAlarm(context)
                    },
                    onRequestNotifications = {
                        BackgroundPermissionManager.openNotificationSettings(context)
                    },
                    onGrantAll = {
                        BackgroundPermissionManager.requestIgnoreBatteryOptimizations(context)
                        BackgroundPermissionManager.requestScheduleExactAlarm(context)
                        BackgroundPermissionManager.refreshBackgroundSchedulers(context)
                        showBgDialog = false
                    }
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "مواقيت صلوات اليوم",
                    color = Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    "اضغط لتخصيص التنبيه",
                    color = Color.White.copy(alpha = 0.5f),
                    fontFamily = NotoSansFont,
                    fontSize = 11.sp
                )
            }
        }

        items(prayers) { prayer ->
            val isNext = prayerTimes.nextPrayerName.contains(prayer.nameAr)
            var isNotifyEnabled by remember {
                mutableStateOf(prefs.getBoolean("notify_${prayer.key}", true))
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isNext) Color(0xFF1E293B) else Color(0xFF151B2B),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isNext) GoldPrimary.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(prayer.color.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = prayer.icon,
                                contentDescription = null,
                                tint = prayer.color,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = prayer.nameAr,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isNext) GoldPrimary else Color.White,
                                    fontSize = 15.sp
                                )
                                if (isNext) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = GoldPrimary.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            "الصلاة القادمة",
                                            color = GoldPrimary,
                                            fontFamily = CairoFont,
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (prayer.key == "qiyam") "يبدأ وقت النزول الإلهي" else "وقت النداء",
                                fontFamily = NotoSansFont,
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = prayer.time,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Black,
                            color = if (isNext) GoldPrimary else Color.White,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                isNotifyEnabled = !isNotifyEnabled
                                prefs.edit().putBoolean("notify_${prayer.key}", isNotifyEnabled).apply()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isNotifyEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                contentDescription = "تنبيه",
                                tint = if (isNotifyEnabled) GoldPrimary else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

data class PrayerRowItem(
    val key: String,
    val nameAr: String,
    val time: String,
    val icon: ImageVector,
    val color: Color
)

// --- Tab 2: Prayer Habit & Sunan Tracker ---
@Composable
fun PrayerHabitTrackerTab(prefs: android.content.SharedPreferences) {
    val todayDateKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var streakCount by remember { mutableIntStateOf(prefs.getInt("prayer_streak_count", 7)) }

    var fajrDone by remember { mutableStateOf(prefs.getBoolean("${todayDateKey}_fajr", false)) }
    var fajrJamaah by remember { mutableStateOf(prefs.getBoolean("${todayDateKey}_fajr_jamaah", false)) }
    var dhuhrDone by remember { mutableStateOf(prefs.getBoolean("${todayDateKey}_dhuhr", false)) }
    var dhuhrJamaah by remember { mutableStateOf(prefs.getBoolean("${todayDateKey}_dhuhr_jamaah", false)) }
    var asrDone by remember { mutableStateOf(prefs.getBoolean("${todayDateKey}_asr", false)) }
    var asrJamaah by remember { mutableStateOf(prefs.getBoolean("${todayDateKey}_asr_jamaah", false)) }
    var maghribDone by remember { mutableStateOf(prefs.getBoolean("${todayDateKey}_maghrib", false)) }
    var maghribJamaah by remember { mutableStateOf(prefs.getBoolean("${todayDateKey}_maghrib_jamaah", false)) }
    var ishaDone by remember { mutableStateOf(prefs.getBoolean("${todayDateKey}_isha", false)) }
    var ishaJamaah by remember { mutableStateOf(prefs.getBoolean("${todayDateKey}_isha_jamaah", false)) }

    var rawatibDone by remember { mutableStateOf(prefs.getBoolean("${todayDateKey}_rawatib", false)) }
    var duhaDone by remember { mutableStateOf(prefs.getBoolean("${todayDateKey}_duha", false)) }
    var witrDone by remember { mutableStateOf(prefs.getBoolean("${todayDateKey}_witr", false)) }

    val completedCount = (if (fajrDone) 1 else 0) + (if (dhuhrDone) 1 else 0) + (if (asrDone) 1 else 0) + (if (maghribDone) 1 else 0) + (if (ishaDone) 1 else 0)
    val totalSunanCount = (if (rawatibDone) 1 else 0) + (if (duhaDone) 1 else 0) + (if (witrDone) 1 else 0)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Streak Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E293B),
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(GoldPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🔥", fontSize = 24.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "سلسلة المحافظة على الصلاة",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                "$streakCount أيام متتالية في طاعة الله",
                                color = GoldPrimary,
                                fontFamily = NotoSansFont,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "$completedCount/5",
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                        Text(
                            "فرائض اليوم",
                            color = Color.White.copy(alpha = 0.5f),
                            fontFamily = NotoSansFont,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // Section: Mandatory Prayers
        item {
            Text(
                "الصلوات المكتوبة (الفرائض الخمس)",
                color = GoldPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        item {
            PrayerCheckCard("صلاة الفجر 🌅", fajrDone, fajrJamaah,
                onToggleDone = {
                    fajrDone = it
                    prefs.edit().putBoolean("${todayDateKey}_fajr", it).apply()
                },
                onToggleJamaah = {
                    fajrJamaah = it
                    prefs.edit().putBoolean("${todayDateKey}_fajr_jamaah", it).apply()
                }
            )
        }
        item {
            PrayerCheckCard("صلاة الظهر ☀️", dhuhrDone, dhuhrJamaah,
                onToggleDone = {
                    dhuhrDone = it
                    prefs.edit().putBoolean("${todayDateKey}_dhuhr", it).apply()
                },
                onToggleJamaah = {
                    dhuhrJamaah = it
                    prefs.edit().putBoolean("${todayDateKey}_dhuhr_jamaah", it).apply()
                }
            )
        }
        item {
            PrayerCheckCard("صلاة العصر ⛅ (حفظ الصلاة الوسطى)", asrDone, asrJamaah,
                onToggleDone = {
                    asrDone = it
                    prefs.edit().putBoolean("${todayDateKey}_asr", it).apply()
                },
                onToggleJamaah = {
                    asrJamaah = it
                    prefs.edit().putBoolean("${todayDateKey}_asr_jamaah", it).apply()
                }
            )
        }
        item {
            PrayerCheckCard("صلاة المغرب 🌇", maghribDone, maghribJamaah,
                onToggleDone = {
                    maghribDone = it
                    prefs.edit().putBoolean("${todayDateKey}_maghrib", it).apply()
                },
                onToggleJamaah = {
                    maghribJamaah = it
                    prefs.edit().putBoolean("${todayDateKey}_maghrib_jamaah", it).apply()
                }
            )
        }
        item {
            PrayerCheckCard("صلاة العشاء 🌌", ishaDone, ishaJamaah,
                onToggleDone = {
                    ishaDone = it
                    prefs.edit().putBoolean("${todayDateKey}_isha", it).apply()
                },
                onToggleJamaah = {
                    ishaJamaah = it
                    prefs.edit().putBoolean("${todayDateKey}_isha_jamaah", it).apply()
                }
            )
        }

        // Section: Sunan & Nawafil
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "السنن والنوافل اليومية (قصر الجنة وصلاة الأوابين)",
                color = Color(0xFF10B981),
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        item {
            SunnahCheckCard(
                title = "السنن الرواتب (12 ركعة)",
                subtitle = "بنى الله له بيتاً في الجنة (2 فجر، 4 قبل الظهر + 2 بعده، 2 مغرب، 2 عشاء)",
                isChecked = rawatibDone,
                color = Color(0xFF10B981),
                onToggle = {
                    rawatibDone = it
                    prefs.edit().putBoolean("${todayDateKey}_rawatib", it).apply()
                }
            )
        }

        item {
            SunnahCheckCard(
                title = "صلاة الضحى (صلاة الأوابين)",
                subtitle = "تجزئ عن 360 صدقة عن مفاصل وعظام جسدك يومياً",
                isChecked = duhaDone,
                color = Color(0xFFF59E0B),
                onToggle = {
                    duhaDone = it
                    prefs.edit().putBoolean("${todayDateKey}_duha", it).apply()
                }
            )
        }

        item {
            SunnahCheckCard(
                title = "صلاة الوتر وقيام الليل 🌙",
                subtitle = "شرف المؤمن وسياج النور وبركة اليوم",
                isChecked = witrDone,
                color = Color(0xFF8B5CF6),
                onToggle = {
                    witrDone = it
                    prefs.edit().putBoolean("${todayDateKey}_witr", it).apply()
                }
            )
        }
    }
}

@Composable
fun PrayerCheckCard(
    title: String,
    isDone: Boolean,
    isJamaah: Boolean,
    onToggleDone: (Boolean) -> Unit,
    onToggleJamaah: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF151B2B),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isDone) GoldPrimary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = isDone,
                    onCheckedChange = onToggleDone,
                    colors = CheckboxDefaults.colors(
                        checkedColor = GoldPrimary,
                        uncheckedColor = Color.White.copy(alpha = 0.4f),
                        checkmarkColor = DeepSlate
                    )
                )
                Text(
                    text = title,
                    color = if (isDone) GoldPrimary else Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            // Jamaah Chip
            FilterChip(
                selected = isJamaah,
                onClick = { onToggleJamaah(!isJamaah) },
                label = {
                    Text(
                        "في المسجد 🕌",
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        color = if (isJamaah) DeepSlate else Color.White.copy(alpha = 0.7f)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = GoldPrimary,
                    containerColor = Color(0xFF1E293B)
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isJamaah,
                    borderColor = if (isJamaah) GoldPrimary else Color.White.copy(alpha = 0.2f)
                )
            )
        }
    }
}

@Composable
fun SunnahCheckCard(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    color: Color,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF151B2B),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isChecked) color.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = onToggle,
                colors = CheckboxDefaults.colors(
                    checkedColor = color,
                    uncheckedColor = Color.White.copy(alpha = 0.4f),
                    checkmarkColor = Color.White
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    color = if (isChecked) color else Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.5f),
                    fontFamily = NotoSansFont,
                    fontSize = 10.sp
                )
            }
        }
    }
}

// --- Tab 3 & 4: Spiritual Topics (Warnings / Rewards) ---
@Composable
fun SpiritualTopicsTab(
    topics: List<SpiritualTopic>,
    headerTitle: String,
    headerDesc: String,
    onShareTopic: (SpiritualTopic) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                Text(
                    headerTitle,
                    color = Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    headerDesc,
                    color = Color.White.copy(alpha = 0.65f),
                    fontFamily = NotoSansFont,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        items(topics) { topic ->
            SpiritualTopicCard(topic = topic, onShare = { onShareTopic(topic) })
        }
    }
}

@Composable
fun SpiritualTopicCard(
    topic: SpiritualTopic,
    onShare: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF151B2B),
        border = androidx.compose.foundation.BorderStroke(1.dp, topic.primaryColor.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            // Header tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = topic.primaryColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        topic.tag,
                        color = topic.primaryColor,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "مشاركة",
                        tint = topic.primaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = topic.title,
                color = Color.White,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )

            Text(
                text = topic.subtitle,
                color = Color.White.copy(alpha = 0.6f),
                fontFamily = NotoSansFont,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            // Quran or Hadith Box
            topic.quranAyah?.let { ayah ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F172A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = ayah,
                            color = GoldPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            lineHeight = 22.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        topic.quranSurahAndVerse?.let { ref ->
                            Text(
                                text = "[$ref]",
                                color = Color.White.copy(alpha = 0.5f),
                                fontFamily = NotoSansFont,
                                fontSize = 10.sp,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            topic.hadithText?.let { hadith ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F172A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, topic.primaryColor.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = hadith,
                            color = Color.White.copy(alpha = 0.9f),
                            fontFamily = CairoFont,
                            fontSize = 12.sp,
                            lineHeight = 20.sp
                        )
                        topic.hadithSource?.let { src ->
                            Text(
                                text = "— $src",
                                color = topic.primaryColor,
                                fontFamily = NotoSansFont,
                                fontSize = 10.sp,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // Lesson & Tafsir
            Text(
                text = "📌 الشرح والبيان:",
                color = Color.White,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Text(
                text = topic.detailedTafsirAndLesson,
                color = Color.White.copy(alpha = 0.8f),
                fontFamily = NotoSansFont,
                fontSize = 11.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            // Practical Step
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = topic.primaryColor.copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("💡", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            "الخطوة العملية للتثبيت:",
                            color = topic.primaryColor,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Text(
                            topic.practicalStep,
                            color = Color.White.copy(alpha = 0.85f),
                            fontFamily = NotoSansFont,
                            fontSize = 10.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

// --- Tab 5: Qibla & Khushoo' ---
@Composable
fun QiblaAndKhushooTab(
    prayerTimes: CalculatedPrayerTimes,
    cityName: String,
    onShareTopic: (SpiritualTopic) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Qibla Compass Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF151B2B),
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "اتجاه القبلة نحو الكعبة المشرفة",
                        color = GoldPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        "من $cityName • ${prayerTimes.qiblaDirectionDeg.toInt()}° درجة من الشمال",
                        color = Color.White.copy(alpha = 0.6f),
                        fontFamily = NotoSansFont,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Compass Dial
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(170.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF0F172A),
                            border = androidx.compose.foundation.BorderStroke(2.dp, GoldPrimary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxSize()
                        ) {}

                        // Degree Ring
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2, size.height / 2)
                            val radius = size.minDimension / 2 - 12.dp.toPx()
                            drawCircle(
                                color = GoldPrimary.copy(alpha = 0.1f),
                                radius = radius,
                                center = center
                            )
                        }

                        // Arrow Pointer to Kaaba
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = "اتجاه القبلة",
                            tint = GoldPrimary,
                            modifier = Modifier
                                .size(64.dp)
                                .rotate(prayerTimes.qiblaDirectionDeg)
                        )

                        // Center Kaaba icon
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.Black)
                                .border(1.dp, GoldPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🕋", fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        "المسافة إلى مكة المكرمة: ${prayerTimes.distanceToMakkahKm} كم تقريباً",
                        color = Color.White.copy(alpha = 0.8f),
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Fiqh & Khushoo' Articles
        item {
            Text(
                "فقه الخشوع وطرد وسواس الشيطان",
                color = Color.White,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        items(PrayerRepository.fiqhAndKhushooTips) { topic ->
            SpiritualTopicCard(topic = topic, onShare = { onShareTopic(topic) })
        }
    }
}

// --- Azan Audio Voices Player Sheet Dialog ---
@Composable
fun AzanVoicesDialog(
    playbackState: AzanPlaybackState,
    onDismiss: () -> Unit,
    onPlayVoice: (AzanVoice) -> Unit,
    onStopVoice: () -> Unit
) {
    val context = LocalContext.current
    var voicesList by remember { mutableStateOf(MuezzinVoiceManager.getAllVoices(context)) }
    var defaultVoiceId by remember { mutableStateOf(MuezzinVoiceManager.getDefaultVoiceId(context)) }
    var showAddMuezzinDialog by remember { mutableStateOf(false) }
    var showDevUploadDialog by remember { mutableStateOf(false) }
    var isSyncingCloud by remember { mutableStateOf(false) }
    val isDeveloper = remember { AdminGuard.isDashboardAccessAllowed(context) }
    val scope = rememberCoroutineScope()
    val mediaDownloads by DownloadsHubManager.activeMediaDownloads.collectAsState()

    fun reloadVoices() {
        voicesList = MuezzinVoiceManager.getAllVoices(context)
        defaultVoiceId = MuezzinVoiceManager.getDefaultVoiceId(context)
    }

    // المستخدم العادي: اختيار ملف من الهاتف (محلي فقط — لا يُرفع للسحابة أبداً)
    val localPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val localUrl = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    MuezzinVoiceManager.copyLocalAzanFile(context, uri, "azan_user.mp3")
                }
                if (localUrl != null) {
                    val v = AzanVoice(
                        id = "custom_" + System.currentTimeMillis(),
                        nameAr = "أذان من هاتفي 📱",
                        muezzinAr = "ملف محلي خاص بي",
                        description = "صوت أذان محفوظ على جهازك فقط — لا يُشارك ولا يُرفع للسحابة.",
                        streamUrl = localUrl
                    )
                    MuezzinVoiceManager.addCustomVoice(context, v)
                    reloadVoices()
                    Toast.makeText(context, "تمت إضافة صوتك من الهاتف ✅ (محلي فقط)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "تعذر نسخ الملف", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // مزامنة أصوات السحابة (قراءة للجميع)
    LaunchedEffect(Unit) {
        if (SupabaseServices.isSupabaseAvailable) {
            isSyncingCloud = true
            val cloud = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                AzanCloudRepository.fetchCloudVoices()
            }
            if (cloud.isNotEmpty()) {
                MuezzinVoiceManager.cacheCloudVoices(context, cloud)
                reloadVoices()
            }
            isSyncingCloud = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "أصوات المؤذنين الحية",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary,
                        fontSize = 16.sp
                    )
                }

                Surface(
                    onClick = { localPicker.launch("audio/*") },
                    shape = RoundedCornerShape(8.dp),
                    color = GoldPrimary.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("من الهاتف 📱", color = GoldPrimary, fontSize = 11.sp, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    }
                }
                if (isDeveloper) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        onClick = { showDevUploadDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("رفع سحابي ☁️", color = Color(0xFF10B981), fontSize = 11.sp, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "أصوات الأذان الحية المعتمدة حصراً بصوت كبار المؤذنين، ويمكنك تعيين المؤذن الافتراضي أو إضافة أصوات أخرى لاحقاً:",
                    color = Color.White.copy(alpha = 0.75f),
                    fontFamily = CairoFont,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                voicesList.forEach { voice ->
                    val isDefault = defaultVoiceId == voice.id
                    val isCurrentVoice = when (playbackState) {
                        is AzanPlaybackState.Loading -> playbackState.voiceId == voice.id
                        is AzanPlaybackState.Playing -> playbackState.voiceId == voice.id
                        is AzanPlaybackState.Paused -> playbackState.voiceId == voice.id
                        else -> false
                    }

                    val isPlaying = playbackState is AzanPlaybackState.Playing && isCurrentVoice
                    val isLoading = playbackState is AzanPlaybackState.Loading && isCurrentVoice
                    val isCustom = voice.id !in MuezzinVoiceManager.OFFICIAL_DEFAULT_MUEZZINS.map { it.id }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDefault) GoldPrimary.copy(alpha = 0.14f) else Color(0xFF1E293B),
                        border = BorderStroke(
                            1.dp,
                            if (isDefault) GoldPrimary.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(if (isDefault) GoldPrimary.copy(alpha = 0.25f) else Color(0xFF0F172A))
                                            .border(1.dp, if (isDefault) GoldPrimary else Color(0xFF334155), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Mosque,
                                            contentDescription = null,
                                            tint = if (isDefault) GoldPrimary else Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                voice.nameAr,
                                                color = if (isDefault) GoldPrimary else Color.White,
                                                fontFamily = CairoFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp
                                            )
                                            if (isDefault) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = Color(0xFF10B981).copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(4.dp),
                                                    border = BorderStroke(0.6.dp, Color(0xFF10B981))
                                                ) {
                                                    Text(
                                                        "الافتراضي 🕋",
                                                        color = Color(0xFF10B981),
                                                        fontSize = 9.sp,
                                                        fontFamily = CairoFont,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            voice.muezzinAr,
                                            color = if (isDefault) GoldSecondary else Color.White.copy(alpha = 0.65f),
                                            fontFamily = NotoSansFont,
                                            fontSize = 10.5.sp
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Play/Pause button
                                    IconButton(
                                        onClick = {
                                            if (isPlaying) {
                                                onStopVoice()
                                            } else {
                                                onPlayVoice(voice)
                                            }
                                        },
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = GoldPrimary,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Default.StopCircle else Icons.Default.PlayCircle,
                                                contentDescription = "تشغيل",
                                                tint = GoldPrimary,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }

                                    if (isCustom) {
                                        IconButton(
                                            onClick = {
                                                MuezzinVoiceManager.deleteCustomVoice(context, voice.id)
                                                reloadVoices()
                                                Toast.makeText(context, "تم حذف المؤذن المخصص", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = voice.description,
                                color = Color.White.copy(alpha = 0.55f),
                                fontFamily = CairoFont,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // عدّاد تنزيل الأذان السحابي — عبر مدير التنزيلات نفسه
                            val isCloudVoice = voice.id.startsWith("cloud_") && voice.streamUrl.startsWith("http")
                            val dlProgress = mediaDownloads[voice.id]
                            if (isCloudVoice) {
                                if (dlProgress != null && !dlProgress.isError && !dlProgress.isCompleted) {
                                    TransferCounterBar(
                                        label = "تنزيل الأذان ⬇️",
                                        sentBytes = dlProgress.downloadedBytes,
                                        totalBytes = dlProgress.totalBytes,
                                        isUpload = false,
                                        speedText = if (dlProgress.speedKbps > 0) "${dlProgress.speedKbps} KB/ث" else null
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                DownloadsHubManager.init(context)
                                                val local = DownloadsHubManager.downloadMedia(
                                                    context, voice.id, voice.nameAr, voice.streamUrl
                                                )
                                                if (local != null) Toast.makeText(context, "اكتمل تنزيل الأذان ✅", Toast.LENGTH_SHORT).show()
                                                else Toast.makeText(context, "تعذر التنزيل — تحقق من الاتصال", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(0.8.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.fillMaxWidth().height(32.dp)
                                    ) {
                                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("تنزيل للاستماع دون إنترنت ⬇️", color = Color(0xFF10B981), fontSize = 10.5.sp, fontFamily = CairoFont)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }

                            // Set as default Azan button
                            if (!isDefault) {
                                OutlinedButton(
                                    onClick = {
                                        MuezzinVoiceManager.setDefaultVoiceId(context, voice.id)
                                        reloadVoices()
                                        BackgroundPermissionManager.refreshBackgroundSchedulers(context)
                                        Toast.makeText(context, "تم تعيين ${voice.muezzinAr} كأذان افتراضي 🕋", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(0.8.dp, GoldPrimary.copy(alpha = 0.4f)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.fillMaxWidth().height(32.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("تعيين كأذان افتراضي للصلوات", color = GoldPrimary, fontSize = 10.5.sp, fontFamily = CairoFont)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFF151B2B)
    )

    // نافذة إضافة مؤذن جديد ديناميكياً
    if (showAddMuezzinDialog) {
        AddCustomMuezzinDialog(
            onDismiss = { showAddMuezzinDialog = false },
            onVoiceAdded = { newVoice ->
                val success = MuezzinVoiceManager.addCustomVoice(context, newVoice)
                if (success) {
                    reloadVoices()
                    Toast.makeText(context, "تمت إضافة المؤذن الجديد بنجاح ✅", Toast.LENGTH_SHORT).show()
                }
                showAddMuezzinDialog = false
            }
        )
    }

    // نافذة رفع المطور إلى Supabase (تظهر للمطور فقط)
    if (showDevUploadDialog && isDeveloper) {
        DevAzanUploadDialog(
            onDismiss = { showDevUploadDialog = false },
            onUploaded = { reloadVoices(); showDevUploadDialog = false }
        )
    }
}

/**
 * نافذة إضافة صوت مؤذن جديد مخصص
 */
@Composable
fun AddCustomMuezzinDialog(
    onDismiss: () -> Unit,
    onVoiceAdded: (AzanVoice) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var muezzinName by remember { mutableStateOf("") }
    var streamUrl by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isFajrOnly by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AddCircle, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("إضافة صوت مؤذن جديد", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "أدخل بيانات ورابط الملف الصوتي للأذان (بصيغة MP3 أو رابط صوتي مباشر):",
                    color = Color.Gray,
                    fontFamily = CairoFont,
                    fontSize = 11.sp
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان الأذان (مثال: أذان الحرم المكي)", fontFamily = CairoFont, fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GoldPrimary, focusedLabelColor = GoldPrimary),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = muezzinName,
                    onValueChange = { muezzinName = it },
                    label = { Text("اسم المؤذن والشيخ", fontFamily = CairoFont, fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GoldPrimary, focusedLabelColor = GoldPrimary),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = it },
                    label = { Text("رابط الصوت المباشر (URL / MP3)", fontFamily = CairoFont, fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GoldPrimary, focusedLabelColor = GoldPrimary),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("وصف مختصر", fontFamily = CairoFont, fontSize = 11.sp) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GoldPrimary, focusedLabelColor = GoldPrimary),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("أذان مخصص لصلاة الفجر فقط", color = Color.White, fontFamily = CairoFont, fontSize = 11.sp)
                    Switch(
                        checked = isFajrOnly,
                        onCheckedChange = { isFajrOnly = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && muezzinName.isNotBlank() && streamUrl.isNotBlank()) {
                        val voice = AzanVoice(
                            id = "custom_" + System.currentTimeMillis(),
                            nameAr = title.trim(),
                            muezzinAr = muezzinName.trim(),
                            description = description.ifBlank { "أذان مخصص بصوت ${muezzinName.trim()}" },
                            streamUrl = streamUrl.trim(),
                            isFajrSpecific = isFajrOnly
                        )
                        onVoiceAdded(voice)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                shape = RoundedCornerShape(10.dp),
                enabled = title.isNotBlank() && muezzinName.isNotBlank() && streamUrl.isNotBlank()
            ) {
                Text("حفظ وإضافة ➕", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = Color.Gray, fontFamily = CairoFont, fontSize = 11.5.sp)
            }
        }
    )
}

/**
 * نافذة رفع المطور: تختار ملفاً من الهاتف فيُرفع إلى Supabase Storage (azan-voices)
 * ويُحفظ في جدول azan_voices ليصل لكل المستخدمين. لا تظهر إلا للمطور.
 */
@Composable
fun DevAzanUploadDialog(onDismiss: () -> Unit, onUploaded: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var muezzinName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isFajrOnly by remember { mutableStateOf(false) }
    var pickedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var uploadSent by remember { mutableStateOf(0L) }
    var uploadTotal by remember { mutableStateOf(0L) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> pickedUri = uri }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("رفع أذان للسحابة ☁️ (مطور)", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("يُرفع الملف إلى Supabase ويظهر لجميع المستخدمين فوراً.", color = Color.Gray, fontFamily = CairoFont, fontSize = 11.sp)
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("عنوان الأذان", fontFamily = CairoFont, fontSize = 11.sp) }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF10B981), focusedLabelColor = Color(0xFF10B981)), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = muezzinName, onValueChange = { muezzinName = it }, label = { Text("اسم المؤذن", fontFamily = CairoFont, fontSize = 11.sp) }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF10B981), focusedLabelColor = Color(0xFF10B981)), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("وصف مختصر", fontFamily = CairoFont, fontSize = 11.sp) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF10B981), focusedLabelColor = Color(0xFF10B981)), modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("مخصص للفجر فقط", color = Color.White, fontFamily = CairoFont, fontSize = 11.sp)
                    Switch(checked = isFajrOnly, onCheckedChange = { isFajrOnly = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF10B981)))
                }
                OutlinedButton(onClick = { picker.launch("audio/*") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AudioFile, contentDescription = null, tint = Color(0xFF10B981))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (pickedUri == null) "اختر ملف الصوت (MP3)" else "تم اختيار الملف ✅", color = Color.White, fontFamily = CairoFont, fontSize = 11.sp)
                }
                if (uploading || uploadTotal > 0) {
                    TransferCounterBar(
                        label = "رفع الأذان للسحابة ☁️",
                        sentBytes = uploadSent,
                        totalBytes = uploadTotal,
                        isUpload = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val uri = pickedUri ?: return@Button
                    uploading = true
                    uploadSent = 0L; uploadTotal = 0L
                    scope.launch {
                        val res = AzanCloudRepository.uploadAzanVoice(context, uri, title, muezzinName, description.ifBlank { "أذان سحابي بصوت $muezzinName" }, isFajrOnly) { sent, total ->
                            uploadSent = sent; uploadTotal = total
                        }
                        uploading = false
                        if (res.isSuccess) {
                            Toast.makeText(context, "تم الرفع للسحابة بنجاح ☁️✅", Toast.LENGTH_SHORT).show()
                            onUploaded()
                        } else {
                            Toast.makeText(context, "فشل الرفع: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                shape = RoundedCornerShape(10.dp),
                enabled = title.isNotBlank() && muezzinName.isNotBlank() && pickedUri != null && !uploading
            ) { Text("رفع ☁️", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = Color.Gray, fontFamily = CairoFont) } }
    )
}
