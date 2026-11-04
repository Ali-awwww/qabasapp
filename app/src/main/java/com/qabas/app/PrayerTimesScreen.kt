package com.qabas.app

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qabas.app.ui.geometry.*
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

private data class PrayerSectionEntry(
    val title: String,
    val desc: String,
    val icon: ImageVector,
    val color: Color
)

/**
 * بوابة أقسام المحراب — شبكة هندسية منتظمة (عمودان) بلا إيموجي.
 */
@Composable
private fun PrayerSectionsHub(
    entries: List<PrayerSectionEntry>,
    onOpen: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Geo.S_MD, vertical = Geo.S_MD),
        verticalArrangement = Arrangement.spacedBy(Geo.S_MD)
    ) {
        GeoHeader(
            title = "أقسام المحراب",
            subtitle = "خمسة أبواب متكاملة للصلاة والسنن والخشوع",
            accent = GoldPrimary
        )

        entries.chunked(2).forEachIndexed { rowIndex, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Geo.S_MD)
            ) {
                rowItems.forEachIndexed { colIndex, entry ->
                    val index = rowIndex * 2 + colIndex
                    Box(modifier = Modifier.weight(1f)) {
                        GeoSectionTile(
                            title = entry.title,
                            desc = entry.desc,
                            icon = entry.icon,
                            accent = entry.color,
                            onClick = { onOpen(index) }
                        )
                    }
                }
                // حشو متوازن للصف الأخير الفردي
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * بلاطة قسم هندسية — أيقونة دائرية + عنوان + وصف + سهم.
 */
@Composable
private fun GeoSectionTile(
    title: String,
    desc: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = GeoShape.Card,
        color = GeoColor.surfaceRaised(),
        border = BorderStroke(Geo.B_HAIRLINE, accent.copy(alpha = 0.26f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Geo.S_MD),
            verticalArrangement = Arrangement.spacedBy(Geo.S_SM)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                GeoIconBadge(
                    icon = icon,
                    accent = accent,
                    size = 42.dp,
                    iconSize = Geo.I_MD
                )
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = accent.copy(alpha = 0.65f),
                    modifier = Modifier.size(Geo.I_XS)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    color = GeoColor.textPrimary(),
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = desc,
                    color = GeoColor.textMuted(),
                    fontFamily = NotoSansFont,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * ترويسة الشاشة الكاملة للقسم — أيقونة كبيرة + عنوان + وصف + رجوع لبوابة المحراب.
 */
@Composable
private fun SectionFullScreenHeader(
    entry: PrayerSectionEntry,
    onBack: () -> Unit
) {
    Surface(
        shape = GeoShape.Card,
        color = Color.Transparent,
        border = BorderStroke(Geo.B_HAIRLINE, entry.color.copy(alpha = 0.30f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Geo.S_MD, vertical = Geo.S_SM)
    ) {
        Box(
            modifier = Modifier.background(
                Brush.horizontalGradient(
                    listOf(entry.color.copy(alpha = 0.16f), Color.Transparent)
                )
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Geo.S_MD),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(GeoShape.Circle)
                        .background(entry.color.copy(alpha = 0.10f), GeoShape.Circle)
                        .border(Geo.B_HAIRLINE, entry.color.copy(alpha = 0.34f), GeoShape.Circle)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "رجوع إلى أقسام المحراب",
                        tint = entry.color,
                        modifier = Modifier.size(Geo.I_SM)
                    )
                }

                Spacer(Modifier.width(Geo.S_MD))

                GeoIconBadge(
                    icon = entry.icon,
                    accent = entry.color,
                    size = 46.dp,
                    iconSize = Geo.I_LG
                )

                Spacer(Modifier.width(Geo.S_MD))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        color = GeoColor.textPrimary(),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = entry.desc,
                        color = GeoColor.textMuted(),
                        fontFamily = NotoSansFont,
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

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

    // -1 = بوابة الأقسام (بطاقات دخول)، وإلا قسم مفتوح بشاشة كاملة
    var openedSection by remember { mutableIntStateOf(-1) }
    BackHandler(enabled = openedSection != -1) {
        AppSoundManager.playClick()
        openedSection = -1
    }
    // عناوين نظيفة بلا إيموجي — الأيقونة هي الحامل البصري الوحيد
    val sectionEntries = listOf(
        PrayerSectionEntry("المواقيت", "جدول الصلوات اليومي والتنبيهات", Icons.Default.Schedule, GeoColor.Prayer),
        PrayerSectionEntry("السجل والسنن", "سجل المحافظة والسنن الرواتب", Icons.Default.MenuBook, GeoColor.Quran),
        PrayerSectionEntry("مرآة النذير", "عقوبات التهاون والتأخير", Icons.Default.Warning, GeoColor.Warning),
        PrayerSectionEntry("رياض الجنة", "ثواب المحافظة وحسن المقام", Icons.Default.Star, GeoColor.Reward),
        PrayerSectionEntry("القبلة والخشوع", "اتجاه القبلة وفقه الخشوع", Icons.Default.Explore, GeoColor.Qibla)
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
        containerColor = GeoColor.screen(),
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GeoIconBadge(
                            icon = Icons.Default.Mosque,
                            accent = GoldPrimary,
                            size = 36.dp,
                            iconSize = Geo.I_SM
                        )
                        Spacer(Modifier.width(Geo.S_SM))
                        Column {
                            Text(
                                if (openedSection == -1) "محراب الصلاة والمواقيت"
                                else sectionEntries[openedSection].title,
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (openedSection == -1) "${currentCity.nameAr} • ${currentCity.countryAr}"
                                else sectionEntries[openedSection].desc,
                                color = GeoColor.textMuted(),
                                fontFamily = NotoSansFont,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (openedSection != -1) {
                            AppSoundManager.playClick()
                            openedSection = -1
                        } else onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = GoldPrimary
                        )
                    }
                },
                actions = {
                    // أزرار دائرية هندسية
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(GeoShape.Circle)
                            .background(GoldPrimary.copy(alpha = 0.10f), GeoShape.Circle)
                            .border(Geo.B_HAIRLINE, GeoColor.outline(), GeoShape.Circle)
                            .clickable { showCityDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = "تغيير المدينة",
                            tint = GoldPrimary,
                            modifier = Modifier.size(Geo.I_SM)
                        )
                    }
                    Spacer(Modifier.width(Geo.S_SM))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(GeoShape.Circle)
                            .background(GoldPrimary.copy(alpha = 0.10f), GeoShape.Circle)
                            .border(Geo.B_HAIRLINE, GeoColor.outline(), GeoShape.Circle)
                            .clickable { showAudioSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = "أصوات الأذان",
                            tint = GoldPrimary,
                            modifier = Modifier.size(Geo.I_SM)
                        )
                    }
                    Spacer(Modifier.width(Geo.S_SM))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GeoColor.screen())
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (openedSection == -1) {
                // ── بوابة المحراب: العدّاد الحي + بطاقات الأقسام ──
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = Geo.S_LG)
                ) {
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

                    PrayerSectionsHub(
                        entries = sectionEntries,
                        onOpen = {
                            AppSoundManager.playClick()
                            openedSection = it
                        }
                    )
                }
            } else {
                // ── شاشة كاملة مستقلة للقسم المفتوح (أيقونة + عنوان + محتوى) ──
                SectionFullScreenHeader(
                    entry = sectionEntries[openedSection],
                    onBack = {
                        AppSoundManager.playClick()
                        openedSection = -1
                    }
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                when (openedSection) {
                    0 -> TimetableAndControlsTab(
                        prayerTimes = prayerTimes,
                        prefs = prefs,
                        onConfigPrayer = { prayerKey -> selectedPrayerForConfig = prayerKey },
                        onOpenVoices = { showAudioSheet = true }
                    )
                    1 -> PrayerHabitTrackerTab(prefs = prefs)
                    2 -> SpiritualTopicsTab(
                        topics = PrayerRepository.warningTopics,
                        headerTitle = "مرآة النذير وعقوبات التهاون والتأخير",
                        headerDesc = "نصوص قاطعة وآثار نبوية تزلزل الغفلة وتوقظ القلب لحفظ الصلاة في وقتها",
                        accent = GeoColor.Warning,
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
                        headerTitle = "رياض الجنة: ثواب المحافظة وحسن المقام",
                        headerDesc = "بشائر النور التام، تكفير الخطايا، رؤية وجه الله الكريم ورفقة النبي ﷺ في الفردوس",
                        accent = GeoColor.Reward,
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
            } // end if (openedSection != -1)
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

    // نافذة تخصيص تنبيه صلاة — تُفتح عند الضغط على أي صف صلاة
    selectedPrayerForConfig?.let { prayerKey ->
        PrayerNotificationConfigDialog(
            prayerKey = prayerKey,
            prayerLabel = prayerKeyLabel(prayerKey),
            onDismiss = { selectedPrayerForConfig = null },
            onSaved = {
                selectedPrayerForConfig = null
                // إعادة الجدولة فوراً لتطبيق الصوت/التنبيه الجديد
                runCatching { PrayerAlarmManager.scheduleAllPrayers(context) }
            },
            onOpenVoices = {
                selectedPrayerForConfig = null
                showAudioSheet = true
            }
        )
    }
}

/** اسم الصلاة بالعربية من المفتاح */
private fun prayerKeyLabel(key: String): String = when (key) {
    "fajr" -> "الفجر"
    "sunrise" -> "الشروق"
    "dhuhr" -> "الظهر"
    "asr" -> "العصر"
    "maghrib" -> "المغرب"
    "isha" -> "العشاء"
    "qiyam" -> "قيام الليل"
    else -> key
}

/**
 * نافذة تخصيص تنبيه صلاة — هندسية، تكتب في qabas_prayer_prefs
 * (نفس المخزن الذي يقرأه PrayerAlarmManager ← مصدر حقيقة واحد).
 */
@Composable
private fun PrayerNotificationConfigDialog(
    prayerKey: String,
    prayerLabel: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onOpenVoices: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("qabas_prayer_prefs", Context.MODE_PRIVATE) }

    var isEnabled by remember {
        mutableStateOf(prefs.getBoolean("notify_$prayerKey", true))
    }
    var soundMode by remember {
        mutableStateOf(prefs.getString("sound_mode_$prayerKey", "FULL_AZAN") ?: "FULL_AZAN")
    }

    // أنماط الصوت — مطابقة تماماً لما يتعامل معه PrayerAzanReceiver
    data class SoundOption(val value: String, val label: String, val icon: ImageVector, val hint: String)

    val soundOptions = listOf(
        SoundOption("FULL_AZAN", "أذان كامل", Icons.Default.RecordVoiceOver, "تلاوة الأذان كاملة"),
        SoundOption("TAKBEER_ONLY", "تكبير فقط", Icons.Default.GraphicEq, "التكبير دون التلاوة"),
        SoundOption("NOTIFICATION_ONLY", "تنبيه فقط", Icons.Default.NotificationsActive, "إشعار بلا تشغيل الأذان"),
        SoundOption("SILENT", "صامت", Icons.Default.NotificationsNone, "إشعار صامت بلا صوت")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        shape = GeoShape.CardLg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GeoIconBadge(
                    icon = Icons.Default.NotificationsActive,
                    accent = GoldPrimary,
                    size = 36.dp,
                    iconSize = Geo.I_SM
                )
                Spacer(Modifier.width(Geo.S_MD))
                Column {
                    Text(
                        text = "تنبيه $prayerLabel",
                        color = GoldPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "خصّص نداء الأذان لهذه الصلاة",
                        color = GeoColor.textMuted(),
                        fontFamily = NotoSansFont,
                        fontSize = 10.sp
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Geo.S_MD)) {
                // تفعيل/تعطيل التنبيه
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "تفعيل تنبيه الأذان",
                            color = GeoColor.textPrimary(),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "عند تعطيله لن يُجدوَل منبه هذه الصلاة",
                            color = GeoColor.textMuted(),
                            fontFamily = NotoSansFont,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = GoldPrimary,
                            uncheckedThumbColor = Color.LightGray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }

                GeoDivider()

                // نمط الصوت
                Text(
                    text = "نمط الصوت",
                    color = GeoColor.textPrimary(),
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                soundOptions.forEach { option ->
                    val isSelected = soundMode == option.value
                    Surface(
                        onClick = { soundMode = option.value },
                        shape = GeoShape.CardSm,
                        color = if (isSelected) GeoColor.selectedBg() else Color.Transparent,
                        border = BorderStroke(
                            Geo.B_HAIRLINE,
                            if (isSelected) GeoColor.outlineStrong() else GeoColor.hairline()
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = Geo.S_MD, vertical = Geo.S_SM),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null,
                                tint = if (isSelected) GoldPrimary else GeoColor.textMuted(),
                                modifier = Modifier.size(Geo.I_SM)
                            )
                            Spacer(Modifier.width(Geo.S_MD))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.label,
                                    color = if (isSelected) GoldPrimary else GeoColor.textSecondary(),
                                    fontFamily = CairoFont,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = option.hint,
                                    color = GeoColor.textMuted(),
                                    fontFamily = NotoSansFont,
                                    fontSize = 9.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(Geo.I_SM)
                                )
                            }
                        }
                    }
                }

                // اختيار صوت المؤذن
                GeoButton(
                    text = "اختيار صوت المؤذن",
                    icon = Icons.Default.RecordVoiceOver,
                    accent = GoldPrimary,
                    variant = GeoButtonStyle.Outline,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenVoices
                )
            }
        },
        confirmButton = {
            GeoButton(
                text = "حفظ",
                icon = Icons.Default.Check,
                accent = GoldPrimary,
                variant = GeoButtonStyle.Filled,
                onClick = {
                    prefs.edit()
                        .putBoolean("notify_$prayerKey", isEnabled)
                        .putString("sound_mode_$prayerKey", soundMode)
                        .apply()
                    onSaved()
                }
            )
        },
        dismissButton = {
            GeoButton(
                text = "إلغاء",
                accent = GeoColor.textSecondary(),
                variant = GeoButtonStyle.Ghost,
                onClick = onDismiss
            )
        }
    )
}

// --- Hero Countdown Card — هندسي نقي ---
@Composable
fun HeroPrayerCountdownCard(
    prayerTimes: CalculatedPrayerTimes,
    cityName: String,
    onOpenVoices: () -> Unit,
    onShareTimetable: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Geo.S_MD, vertical = Geo.S_SM)
            .clip(GeoShape.CardLg)
            .background(GeoColor.heroGradient, GeoShape.CardLg)
            .border(Geo.B_HAIRLINE, GeoColor.outline(), GeoShape.CardLg)
    ) {
        Column(
            modifier = Modifier.padding(Geo.S_LG),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── ترويسة: الصلاة القادمة + المدينة + مشاركة ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "الصلاة القادمة",
                        color = GeoColor.textMuted(),
                        fontFamily = NotoSansFont,
                        fontSize = 10.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "صلاة ${prayerTimes.nextPrayerName}",
                        color = GeoColor.textPrimary(),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(Geo.S_SM))

                // زر مشاركة هندسي دائري
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(GeoShape.Circle)
                        .background(GoldPrimary.copy(alpha = 0.12f), GeoShape.Circle)
                        .border(Geo.B_HAIRLINE, GeoColor.outline(), GeoShape.Circle)
                        .clickable(onClick = onShareTimetable),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "مشاركة المواقيت",
                        tint = GoldPrimary,
                        modifier = Modifier.size(Geo.I_SM)
                    )
                }
            }

            Spacer(Modifier.height(Geo.S_LG))

            // ── الحلقة الهندسية + العدّاد ──
            GeoProgressRing(
                progress = prayerTimes.progressToNextPrayer,
                size = 172.dp,
                strokeWidth = Geo.RING_STROKE,
                trackColor = Color.White.copy(alpha = 0.07f),
                progressColor = GoldPrimary
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = PrayerCalculationHelper.formatCountdown(prayerTimes.millisUntilNextPrayer),
                        color = GeoColor.textPrimary(),
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        letterSpacing = 1.sp,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "الأذان ${prayerTimes.nextPrayerTimeFormatted}",
                        color = GoldPrimary,
                        fontFamily = NotoSansFont,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            Spacer(Modifier.height(Geo.S_LG))

            // ── شريط الأرقام السريعة ──
            Surface(
                shape = GeoShape.CardSm,
                color = Color.White.copy(alpha = 0.04f),
                border = BorderStroke(Geo.B_HAIRLINE, GeoColor.hairline()),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Geo.S_SM),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GeoMiniStat(
                        value = prayerTimes.fajr,
                        label = "الفجر",
                        accent = GeoColor.Dhikr,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 1.dp, height = 26.dp)
                            .background(GeoColor.hairline())
                    )
                    GeoMiniStat(
                        value = prayerTimes.dhuhr,
                        label = "الظهر",
                        accent = GeoColor.Prayer,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 1.dp, height = 26.dp)
                            .background(GeoColor.hairline())
                    )
                    GeoMiniStat(
                        value = prayerTimes.maghrib,
                        label = "المغرب",
                        accent = GeoColor.Dua,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 1.dp, height = 26.dp)
                            .background(GeoColor.hairline())
                    )
                    GeoMiniStat(
                        value = prayerTimes.isha,
                        label = "العشاء",
                        accent = GeoColor.Qibla,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(Geo.S_MD))

            // ── أزرار الإجراءات ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Geo.S_SM)
            ) {
                GeoButton(
                    text = "أصوات الأذان",
                    icon = Icons.Default.VolumeUp,
                    accent = GoldPrimary,
                    variant = GeoButtonStyle.Filled,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenVoices
                )
                GeoButton(
                    text = "مشاركة الموعد",
                    icon = Icons.Default.Share,
                    accent = GeoColor.textSecondary(),
                    variant = GeoButtonStyle.Outline,
                    modifier = Modifier.weight(1f),
                    onClick = onShareTimetable
                )
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
        PrayerRowItem("fajr", "الفجر", prayerTimes.fajr, Icons.Default.Brightness3, GeoColor.Dhikr),
        PrayerRowItem("sunrise", "الشروق", prayerTimes.sunrise, Icons.Default.WbSunny, GeoColor.Reward),
        PrayerRowItem("dhuhr", "الظهر", prayerTimes.dhuhr, Icons.Default.LightMode, GeoColor.Prayer),
        PrayerRowItem("asr", "العصر", prayerTimes.asr, Icons.Default.WbTwilight, Color(0xFFFB923C)),
        PrayerRowItem("maghrib", "المغرب", prayerTimes.maghrib, Icons.Default.NightsStay, GeoColor.Dua),
        PrayerRowItem("isha", "العشاء", prayerTimes.isha, Icons.Default.Bedtime, GeoColor.Qibla),
        PrayerRowItem("qiyam", "قيام الليل والثلث الأخير", prayerTimes.qiyamStart, Icons.Default.Star, GoldPrimary)
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Geo.S_MD, vertical = Geo.S_SM),
        verticalArrangement = Arrangement.spacedBy(Geo.S_SM)
    ) {
        // بطاقة فحص وإعداد أذونات العمل بالخلفية للأذان
        item {
            val context = LocalContext.current
            var showBgDialog by remember { mutableStateOf(false) }
            val isBgReady = remember { BackgroundPermissionManager.isAllBackgroundPermissionsGranted(context) }
            val statusAccent = if (isBgReady) GeoColor.Quran else GoldPrimary

            Surface(
                onClick = { showBgDialog = true },
                shape = GeoShape.Card,
                color = statusAccent.copy(alpha = 0.08f),
                border = BorderStroke(Geo.B_HAIRLINE, statusAccent.copy(alpha = 0.32f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(Geo.S_MD),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GeoIconBadge(
                        icon = if (isBgReady) Icons.Default.VerifiedUser else Icons.Default.BatterySaver,
                        accent = statusAccent,
                        size = 40.dp,
                        iconSize = Geo.I_MD
                    )
                    Spacer(Modifier.width(Geo.S_MD))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isBgReady) "حماية الأذان بالخلفية مفعّلة" else "تفعيل العمل بالخلفية",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = statusAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (isBgReady) "التطبيق مستثنى من قيود البطارية لرفع الأذان في موعده"
                            else "انقر لمنح أذونات البطارية والمنبهات الدقيقة",
                            fontFamily = NotoSansFont,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = GeoColor.textMuted(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(Geo.S_SM))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = statusAccent.copy(alpha = 0.7f),
                        modifier = Modifier.size(Geo.I_XS)
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
            GeoHeader(
                title = "مواقيت صلوات اليوم",
                subtitle = "اضغط على أي صلاة لتخصيص تنبيهها",
                accent = GoldPrimary,
                modifier = Modifier.padding(top = Geo.S_SM, bottom = Geo.S_XS)
            )
        }

        items(prayers) { prayer ->
            val isNext = prayerTimes.nextPrayerName.contains(prayer.nameAr)
            var isNotifyEnabled by remember {
                mutableStateOf(prefs.getBoolean("notify_${prayer.key}", true))
            }

            Surface(
                onClick = { onConfigPrayer(prayer.key) },
                shape = GeoShape.Card,
                color = if (isNext) GeoColor.selectedBg() else GeoColor.surfaceRaised(),
                border = BorderStroke(
                    Geo.B_HAIRLINE,
                    if (isNext) GeoColor.outlineStrong() else GeoColor.hairline()
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = Geo.S_MD, vertical = Geo.S_SM + 2.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GeoIconBadge(
                        icon = prayer.icon,
                        accent = prayer.color,
                        size = 42.dp,
                        iconSize = Geo.I_MD
                    )
                    Spacer(Modifier.width(Geo.S_MD))

                    // اسم الصلاة + وقتها — مع weight حتى لا ينسحق النص
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = prayer.nameAr,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                color = if (isNext) GoldPrimary else GeoColor.textPrimary(),
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isNext) {
                                Spacer(Modifier.width(Geo.S_SM))
                                GeoChip(
                                    text = "القادمة",
                                    selected = true,
                                    accent = GoldPrimary
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (prayer.key == "qiyam") "يبدأ وقت النزول الإلهي" else "وقت النداء",
                            fontFamily = NotoSansFont,
                            color = GeoColor.textMuted(),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(Geo.S_SM))

                    // الوقت — ثابت لا ينضغط
                    Text(
                        text = prayer.time,
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold,
                        color = if (isNext) GoldPrimary else GeoColor.textPrimary(),
                        fontSize = 17.sp,
                        maxLines = 1
                    )

                    Spacer(Modifier.width(Geo.S_XS))

                    // زر التنبيه
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(GeoShape.Circle)
                            .clickable {
                                isNotifyEnabled = !isNotifyEnabled
                                prefs.edit().putBoolean("notify_${prayer.key}", isNotifyEnabled).apply()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isNotifyEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                            contentDescription = if (isNotifyEnabled) "تعطيل تنبيه ${prayer.nameAr}" else "تفعيل تنبيه ${prayer.nameAr}",
                            tint = if (isNotifyEnabled) prayer.color else GeoColor.textMuted(),
                            modifier = Modifier.size(Geo.I_MD)
                        )
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
    val totalObligatory = 5
    val totalSunanCount = (if (rawatibDone) 1 else 0) + (if (duhaDone) 1 else 0) + (if (witrDone) 1 else 0)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Geo.S_MD, vertical = Geo.S_SM),
        verticalArrangement = Arrangement.spacedBy(Geo.S_SM)
    ) {
        // ── بطاقة السلسلة + حلقة التقدم ──
        item {
            Surface(
                shape = GeoShape.Card,
                color = GeoColor.surfaceRaised(),
                border = BorderStroke(Geo.B_HAIRLINE, GeoColor.outline()),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(Geo.S_MD),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // حلقة تقدم اليوم (الفرائض الخمس)
                    GeoProgressRing(
                        progress = if (totalObligatory > 0) completedCount.toFloat() / totalObligatory else 0f,
                        size = 76.dp,
                        strokeWidth = Geo.RING_TRACK_STROKE,
                        trackColor = Color.White.copy(alpha = 0.08f),
                        progressColor = GoldPrimary
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$completedCount/$totalObligatory",
                                color = GeoColor.textPrimary(),
                                fontFamily = RobotoMonoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(Modifier.width(Geo.S_MD))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "سلسلة المحافظة على الصلاة",
                            color = GeoColor.textPrimary(),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "$streakCount يوماً متتالياً في طاعة الله",
                            color = GoldPrimary,
                            fontFamily = NotoSansFont,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // ── عنوان قسم الفرائض ──
        item {
            GeoHeader(
                title = "الصلوات المكتوبة",
                subtitle = "الفرائض الخمس — اضغط الدائرة لأداء الصلاة",
                accent = GoldPrimary,
                modifier = Modifier.padding(top = Geo.S_SM, bottom = Geo.S_XS)
            )
        }

        item {
            PrayerCheckCard("صلاة الفجر", Icons.Default.Brightness3, fajrDone, fajrJamaah,
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
            PrayerCheckCard("صلاة الظهر", Icons.Default.LightMode, dhuhrDone, dhuhrJamaah,
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
            PrayerCheckCard("صلاة العصر", Icons.Default.WbTwilight, asrDone, asrJamaah,
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
            PrayerCheckCard("صلاة المغرب", Icons.Default.NightsStay, maghribDone, maghribJamaah,
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
            PrayerCheckCard("صلاة العشاء", Icons.Default.Bedtime, ishaDone, ishaJamaah,
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

        // ── عنوان قسم السنن ──
        item {
            GeoHeader(
                title = "السنن والنوافل",
                subtitle = "قصر الجنة وصلاة الأوابين",
                accent = GeoColor.Quran,
                modifier = Modifier.padding(top = Geo.S_SM, bottom = Geo.S_XS)
            )
        }

        item {
            SunnahCheckCard(
                title = "السنن الرواتب",
                subtitle = "12 ركعة: بنى الله له بيتاً في الجنة (2 فجر، 4 قبل الظهر و2 بعده، 2 مغرب، 2 عشاء)",
                icon = Icons.Default.AutoAwesome,
                isChecked = rawatibDone,
                color = GeoColor.Quran,
                onToggle = {
                    rawatibDone = it
                    prefs.edit().putBoolean("${todayDateKey}_rawatib", it).apply()
                }
            )
        }

        item {
            SunnahCheckCard(
                title = "صلاة الضحى",
                subtitle = "صلاة الأوابين — تجزئ عن 360 صدقة عن مفاصل وعظام جسدك يومياً",
                icon = Icons.Default.WbSunny,
                isChecked = duhaDone,
                color = GeoColor.Reward,
                onToggle = {
                    duhaDone = it
                    prefs.edit().putBoolean("${todayDateKey}_duha", it).apply()
                }
            )
        }

        item {
            SunnahCheckCard(
                title = "صلاة الوتر وقيام الليل",
                subtitle = "شرف المؤمن وسياج النور وبركة اليوم",
                icon = Icons.Default.Nightlight,
                isChecked = witrDone,
                color = GeoColor.Qibla,
                onToggle = {
                    witrDone = it
                    prefs.edit().putBoolean("${todayDateKey}_witr", it).apply()
                }
            )
        }
    }
}

/**
 * بطاقة صلاة — هندسية، مع وزن مرن للنص حتى لا ينسحق عند الشاشات الضيقة.
 */
@Composable
fun PrayerCheckCard(
    title: String,
    icon: ImageVector,
    isDone: Boolean,
    isJamaah: Boolean,
    onToggleDone: (Boolean) -> Unit,
    onToggleJamaah: (Boolean) -> Unit
) {
    Surface(
        shape = GeoShape.Card,
        color = if (isDone) GeoColor.selectedBg() else GeoColor.surfaceRaised(),
        border = BorderStroke(
            Geo.B_HAIRLINE,
            if (isDone) GeoColor.outlineStrong() else GeoColor.hairline()
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = Geo.S_MD, vertical = Geo.S_SM)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // زر دائري هندسي للتحقق
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(GeoShape.Circle)
                    .background(
                        if (isDone) GoldPrimary else Color.White.copy(alpha = 0.06f),
                        GeoShape.Circle
                    )
                    .border(
                        Geo.B_HAIRLINE,
                        if (isDone) GoldPrimary else GeoColor.outline(),
                        GeoShape.Circle
                    )
                    .clickable { onToggleDone(!isDone) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDone) Icons.Default.Check else icon,
                    contentDescription = if (isDone) "تم أداء $title" else "تسجيل أداء $title",
                    tint = if (isDone) GeoColor.onGold() else GeoColor.textMuted(),
                    modifier = Modifier.size(Geo.I_SM)
                )
            }

            Spacer(Modifier.width(Geo.S_MD))

            // النص — weight حتى لا يضغطه الشريحة
            Text(
                text = title,
                color = if (isDone) GoldPrimary else GeoColor.textPrimary(),
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            Spacer(Modifier.width(Geo.S_SM))

            // شريحة المسجد — ثابتة لا تنضغط
            GeoChip(
                text = "في المسجد",
                icon = Icons.Default.Mosque,
                selected = isJamaah,
                accent = GoldPrimary,
                onClick = { onToggleJamaah(!isJamaah) }
            )
        }
    }
}

/**
 * بطاقة سنة — هندسية، عنوان ووصف مرنانان.
 */
@Composable
fun SunnahCheckCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isChecked: Boolean,
    color: Color,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        shape = GeoShape.Card,
        color = if (isChecked) color.copy(alpha = 0.08f) else GeoColor.surfaceRaised(),
        border = BorderStroke(
            Geo.B_HAIRLINE,
            if (isChecked) color.copy(alpha = 0.4f) else GeoColor.hairline()
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(Geo.S_MD)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // زر دائري هندسي
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(GeoShape.Circle)
                    .background(
                        if (isChecked) color else Color.White.copy(alpha = 0.06f),
                        GeoShape.Circle
                    )
                    .border(
                        Geo.B_HAIRLINE,
                        if (isChecked) color else GeoColor.outline(),
                        GeoShape.Circle
                    )
                    .clickable { onToggle(!isChecked) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isChecked) Icons.Default.Check else icon,
                    contentDescription = if (isChecked) "تم تسجيل $title" else "تسجيل $title",
                    tint = if (isChecked) Color.White else GeoColor.textMuted(),
                    modifier = Modifier.size(Geo.I_SM)
                )
            }

            Spacer(Modifier.width(Geo.S_MD))

            // النص — وزن مرن
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (isChecked) color else GeoColor.textPrimary(),
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    color = GeoColor.textMuted(),
                    fontFamily = NotoSansFont,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
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
    accent: Color = GoldPrimary,
    onShareTopic: (SpiritualTopic) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Geo.S_MD, vertical = Geo.S_SM),
        verticalArrangement = Arrangement.spacedBy(Geo.S_SM)
    ) {
        item {
            GeoHeader(
                title = headerTitle,
                subtitle = headerDesc,
                accent = accent,
                modifier = Modifier.padding(top = Geo.S_SM, bottom = Geo.S_XS)
            )
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
        shape = GeoShape.Card,
        color = GeoColor.surfaceRaised(),
        border = BorderStroke(Geo.B_HAIRLINE, topic.primaryColor.copy(alpha = 0.28f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Geo.S_MD),
            verticalArrangement = Arrangement.spacedBy(Geo.S_SM)
        ) {
            // ── ترويسة: الوسم + زر المشاركة ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GeoChip(
                    text = topic.tag,
                    selected = true,
                    accent = topic.primaryColor
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(GeoShape.Circle)
                        .clickable(onClick = onShare),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "مشاركة ${topic.title}",
                        tint = topic.primaryColor,
                        modifier = Modifier.size(Geo.I_SM)
                    )
                }
            }

            // ── العنوان والوصف ──
            Column {
                Text(
                    text = topic.title,
                    color = GeoColor.textPrimary(),
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    lineHeight = 21.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = topic.subtitle,
                    color = GeoColor.textMuted(),
                    fontFamily = NotoSansFont,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }

            // ── صندوق الآية ──
            topic.quranAyah?.let { ayah ->
                Surface(
                    shape = GeoShape.CardSm,
                    color = GeoColor.surfaceSunken(),
                    border = BorderStroke(Geo.B_HAIRLINE, GeoColor.outline()),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(Geo.S_MD)) {
                        Text(
                            text = ayah,
                            color = GoldPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            lineHeight = 21.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        topic.quranSurahAndVerse?.let { ref ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "[$ref]",
                                color = GeoColor.textMuted(),
                                fontFamily = NotoSansFont,
                                fontSize = 10.sp,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // ── صندوق الحديث ──
            topic.hadithText?.let { hadith ->
                Surface(
                    shape = GeoShape.CardSm,
                    color = GeoColor.surfaceSunken(),
                    border = BorderStroke(Geo.B_HAIRLINE, topic.primaryColor.copy(alpha = 0.24f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(Geo.S_MD)) {
                        Text(
                            text = hadith,
                            color = GeoColor.textPrimary().copy(alpha = 0.9f),
                            fontFamily = CairoFont,
                            fontSize = 12.sp,
                            lineHeight = 20.sp
                        )
                        topic.hadithSource?.let { src ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "— $src",
                                color = topic.primaryColor,
                                fontFamily = NotoSansFont,
                                fontSize = 10.sp,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // ── الشرح والبيان ──
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = topic.primaryColor,
                        modifier = Modifier.size(Geo.I_XS)
                    )
                    Text(
                        text = "الشرح والبيان",
                        color = GeoColor.textPrimary(),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = topic.detailedTafsirAndLesson,
                    color = GeoColor.textSecondary(),
                    fontFamily = NotoSansFont,
                    fontSize = 11.sp,
                    lineHeight = 18.sp
                )
            }

            // ── الخطوة العملية ──
            Surface(
                shape = GeoShape.CardSm,
                color = topic.primaryColor.copy(alpha = 0.08f),
                border = BorderStroke(Geo.B_HAIRLINE, topic.primaryColor.copy(alpha = 0.22f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(Geo.S_MD),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.TipsAndUpdates,
                        contentDescription = null,
                        tint = topic.primaryColor,
                        modifier = Modifier.size(Geo.I_SM)
                    )
                    Spacer(Modifier.width(Geo.S_SM))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "الخطوة العملية للتثبيت",
                            color = topic.primaryColor,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = topic.practicalStep,
                            color = GeoColor.textSecondary(),
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
            .padding(horizontal = Geo.S_MD, vertical = Geo.S_SM),
        verticalArrangement = Arrangement.spacedBy(Geo.S_SM)
    ) {
        // ── بطاقة البوصلة الهندسية ──
        item {
            Surface(
                shape = GeoShape.Card,
                color = GeoColor.surfaceRaised(),
                border = BorderStroke(Geo.B_HAIRLINE, GeoColor.outline()),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(Geo.S_LG),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ── ترويسة البوصلة ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Geo.S_MD)
                    ) {
                        GeoIconBadge(
                            icon = Icons.Default.Explore,
                            accent = GeoColor.Qibla,
                            size = 38.dp,
                            iconSize = Geo.I_MD
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "اتجاه القبلة",
                                color = GeoColor.textPrimary(),
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "$cityName • ${prayerTimes.qiblaDirectionDeg.toInt()}° من الشمال",
                                color = GeoColor.textMuted(),
                                fontFamily = NotoSansFont,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.height(Geo.S_LG))

                    // ── قرص البوصلة ──
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(168.dp)
                    ) {
                        // الحلقة الخارجية
                        Surface(
                            shape = CircleShape,
                            color = GeoColor.surfaceSunken(),
                            border = BorderStroke(Geo.B_STRONG, GeoColor.outline()),
                            modifier = Modifier.fillMaxSize()
                        ) {}

                        // شبكة الدرجات
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val outerRadius = size.minDimension / 2f - 4.dp.toPx()
                            val innerRadius = outerRadius - 22.dp.toPx()

                            // حلقة داخلية
                            drawCircle(
                                color = GoldPrimary.copy(alpha = 0.08f),
                                radius = innerRadius,
                                center = center
                            )

                            // علامات الدرجات الأربع
                            listOf(0f, 90f, 180f, 270f).forEach { deg ->
                                val rad = Math.toRadians((deg - 90f).toDouble())
                                val start = Offset(
                                    center.x + (innerRadius + 2.dp.toPx()) * cos(rad).toFloat(),
                                    center.y + (innerRadius + 2.dp.toPx()) * sin(rad).toFloat()
                                )
                                val end = Offset(
                                    center.x + outerRadius * cos(rad).toFloat(),
                                    center.y + outerRadius * sin(rad).toFloat()
                                )
                                drawLine(
                                    color = GoldPrimary.copy(alpha = 0.35f),
                                    start = start,
                                    end = end,
                                    strokeWidth = 1.5.dp.toPx()
                                )
                            }
                        }

                        // سهم القبلة
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = "اتجاه القبلة",
                            tint = GoldPrimary,
                            modifier = Modifier
                                .size(60.dp)
                                .rotate(prayerTimes.qiblaDirectionDeg)
                        )

                        // رمز الكعبة — أيقونة بدل الإيموجي
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(GeoColor.surfaceSunken())
                                .border(Geo.B_HAIRLINE, GoldPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mosque,
                                contentDescription = "الكعبة المشرفة",
                                tint = GoldPrimary,
                                modifier = Modifier.size(Geo.I_SM)
                            )
                        }
                    }

                    Spacer(Modifier.height(Geo.S_MD))

                    // ── شريط المسافة ──
                    Surface(
                        shape = GeoShape.CardSm,
                        color = Color.White.copy(alpha = 0.04f),
                        border = BorderStroke(Geo.B_HAIRLINE, GeoColor.hairline()),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Geo.S_MD, vertical = Geo.S_SM),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "المسافة إلى مكة المكرمة",
                                color = GeoColor.textSecondary(),
                                fontFamily = CairoFont,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(Geo.S_SM))
                            Text(
                                text = "${prayerTimes.distanceToMakkahKm} كم",
                                color = GoldPrimary,
                                fontFamily = RobotoMonoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // ── عنوان فقه الخشوع ──
        item {
            GeoHeader(
                title = "فقه الخشوع وطرد وسواس الشيطان",
                subtitle = "نصائح عملية لإدامة صفاء القلب",
                accent = GeoColor.Qibla,
                modifier = Modifier.padding(top = Geo.S_SM, bottom = Geo.S_XS)
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

    // نافذة مقيّدة الارتفاع: رأس ثابت + جسم قابل للتمرير + تذييل ثابت
    // (AlertDialog كان يتجاوز حدود الشاشة فيتداخل التذييل مع المحتوى)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f),
            shape = GeoShape.CardLg,
            color = GeoColor.surfaceSunken(),
            border = BorderStroke(Geo.B_HAIRLINE, GeoColor.outline())
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── رأس ثابت ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Geo.S_MD, vertical = Geo.S_MD),
                    verticalArrangement = Arrangement.spacedBy(Geo.S_SM)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "أصوات المؤذنين الحية",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            color = GoldPrimary,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }

                    // شريط الأدوات — صف قابل للتمرير أفقياً حتى لا يتداخل مع العنوان
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            onClick = { localPicker.launch("audio/*") },
                            shape = GeoShape.Chip,
                            color = GoldPrimary.copy(alpha = 0.14f),
                            border = BorderStroke(Geo.B_HAIRLINE, GeoColor.outline())
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(Geo.I_XS)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    "من الهاتف",
                                    color = GoldPrimary,
                                    fontSize = 11.sp,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                        if (isDeveloper) {
                            Surface(
                                onClick = { showDevUploadDialog = true },
                                shape = GeoShape.Chip,
                                color = GeoColor.Quran.copy(alpha = 0.14f),
                                border = BorderStroke(Geo.B_HAIRLINE, GeoColor.Quran.copy(alpha = 0.42f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        tint = GeoColor.Quran,
                                        modifier = Modifier.size(Geo.I_XS)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        "رفع سحابي",
                                        color = GeoColor.Quran,
                                        fontSize = 11.sp,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                GeoDivider()

                // ── جسم قابل للتمرير ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Geo.S_MD, vertical = Geo.S_SM),
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
                                        label = "تنزيل الأذان",
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
                                        shape = GeoShape.ButtonSmCompat,
                                        border = BorderStroke(Geo.B_HAIRLINE, GeoColor.Quran.copy(alpha = 0.36f)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.fillMaxWidth().height(34.dp)
                                    ) {
                                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = GeoColor.Quran, modifier = Modifier.size(Geo.I_XS))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text("تنزيل للاستماع دون إنترنت", color = GeoColor.Quran, fontSize = 10.5.sp, fontFamily = CairoFont, maxLines = 1)
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
                                        Toast.makeText(context, "تم تعيين ${voice.muezzinAr} كأذان افتراضي", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = GeoShape.ButtonSmCompat,
                                    border = BorderStroke(Geo.B_HAIRLINE, GeoColor.outline()),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.fillMaxWidth().height(34.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(Geo.I_XS))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("تعيين كأذان افتراضي للصلوات", color = GoldPrimary, fontSize = 10.5.sp, fontFamily = CairoFont, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

                // ── تذييل ثابت ──
                GeoDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Geo.S_MD, vertical = Geo.S_SM),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GeoButton(
                        text = "إغلاق",
                        icon = Icons.Default.Close,
                        accent = GoldPrimary,
                        variant = GeoButtonStyle.Outline,
                        onClick = onDismiss
                    )
                }
            }
        }
    }

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
 * نافذة إضافة صوت مؤذن جديد مخصص — هندسية
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
    val isValid = title.isNotBlank() && muezzinName.isNotBlank() && streamUrl.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GeoColor.surfaceSunken(),
        shape = GeoShape.CardLg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GeoIconBadge(
                    icon = Icons.Default.AddCircle,
                    accent = GoldPrimary,
                    size = 36.dp,
                    iconSize = Geo.I_SM
                )
                Spacer(Modifier.width(Geo.S_MD))
                Column {
                    Text(
                        text = "إضافة صوت مؤذن جديد",
                        color = GeoColor.textPrimary(),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "يُحفظ محلياً على جهازك",
                        color = GeoColor.textMuted(),
                        fontFamily = NotoSansFont,
                        fontSize = 10.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Geo.S_SM)
            ) {
                Text(
                    "أدخل بيانات ورابط الملف الصوتي للأذان (MP3 أو رابط صوتي مباشر):",
                    color = GeoColor.textMuted(),
                    fontFamily = NotoSansFont,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان الأذان", fontFamily = CairoFont, fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        focusedLabelColor = GoldPrimary,
                        unfocusedBorderColor = GeoColor.hairline(),
                        focusedTextColor = GeoColor.textPrimary(),
                        unfocusedTextColor = GeoColor.textPrimary()
                    ),
                    shape = GeoShape.CardSm,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = muezzinName,
                    onValueChange = { muezzinName = it },
                    label = { Text("اسم المؤذن والشيخ", fontFamily = CairoFont, fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        focusedLabelColor = GoldPrimary,
                        unfocusedBorderColor = GeoColor.hairline(),
                        focusedTextColor = GeoColor.textPrimary(),
                        unfocusedTextColor = GeoColor.textPrimary()
                    ),
                    shape = GeoShape.CardSm,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = it },
                    label = { Text("رابط الصوت المباشر", fontFamily = CairoFont, fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        focusedLabelColor = GoldPrimary,
                        unfocusedBorderColor = GeoColor.hairline(),
                        focusedTextColor = GeoColor.textPrimary(),
                        unfocusedTextColor = GeoColor.textPrimary()
                    ),
                    shape = GeoShape.CardSm,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("وصف مختصر", fontFamily = CairoFont, fontSize = 11.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        focusedLabelColor = GoldPrimary,
                        unfocusedBorderColor = GeoColor.hairline(),
                        focusedTextColor = GeoColor.textPrimary(),
                        unfocusedTextColor = GeoColor.textPrimary()
                    ),
                    shape = GeoShape.CardSm,
                    modifier = Modifier.fillMaxWidth()
                )

                GeoDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "أذان مخصص لصلاة الفجر فقط",
                            color = GeoColor.textPrimary(),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "يُستخدم هذا الصوت في الفجر دون بقية الصلوات",
                            color = GeoColor.textMuted(),
                            fontFamily = NotoSansFont,
                            fontSize = 9.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(Geo.S_SM))
                    Switch(
                        checked = isFajrOnly,
                        onCheckedChange = { isFajrOnly = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = GoldPrimary,
                            uncheckedThumbColor = Color.LightGray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }
            }
        },
        confirmButton = {
            GeoButton(
                text = "حفظ وإضافة",
                icon = Icons.Default.Check,
                accent = GoldPrimary,
                variant = GeoButtonStyle.Filled,
                enabled = isValid,
                onClick = {
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
            )
        },
        dismissButton = {
            GeoButton(
                text = "إلغاء",
                accent = GeoColor.textSecondary(),
                variant = GeoButtonStyle.Ghost,
                onClick = onDismiss
            )
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

    val canUpload = title.isNotBlank() && muezzinName.isNotBlank() && pickedUri != null && !uploading

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GeoColor.surfaceSunken(),
        shape = GeoShape.CardLg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GeoIconBadge(
                    icon = Icons.Default.CloudUpload,
                    accent = GeoColor.Quran,
                    size = 36.dp,
                    iconSize = Geo.I_SM
                )
                Spacer(Modifier.width(Geo.S_MD))
                Column {
                    Text(
                        text = "رفع أذان للسحابة",
                        color = GeoColor.textPrimary(),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "للمطور فقط — يظهر لجميع المستخدمين",
                        color = GeoColor.textMuted(),
                        fontFamily = NotoSansFont,
                        fontSize = 10.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Geo.S_SM)
            ) {
                Text(
                    "يُرفع الملف إلى Supabase ويظهر لجميع المستخدمين فوراً.",
                    color = GeoColor.textMuted(),
                    fontFamily = NotoSansFont,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان الأذان", fontFamily = CairoFont, fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GeoColor.Quran,
                        focusedLabelColor = GeoColor.Quran,
                        unfocusedBorderColor = GeoColor.hairline(),
                        focusedTextColor = GeoColor.textPrimary(),
                        unfocusedTextColor = GeoColor.textPrimary()
                    ),
                    shape = GeoShape.CardSm,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = muezzinName,
                    onValueChange = { muezzinName = it },
                    label = { Text("اسم المؤذن", fontFamily = CairoFont, fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GeoColor.Quran,
                        focusedLabelColor = GeoColor.Quran,
                        unfocusedBorderColor = GeoColor.hairline(),
                        focusedTextColor = GeoColor.textPrimary(),
                        unfocusedTextColor = GeoColor.textPrimary()
                    ),
                    shape = GeoShape.CardSm,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("وصف مختصر", fontFamily = CairoFont, fontSize = 11.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GeoColor.Quran,
                        focusedLabelColor = GeoColor.Quran,
                        unfocusedBorderColor = GeoColor.hairline(),
                        focusedTextColor = GeoColor.textPrimary(),
                        unfocusedTextColor = GeoColor.textPrimary()
                    ),
                    shape = GeoShape.CardSm,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "مخصص للفجر فقط",
                        color = GeoColor.textPrimary(),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = isFajrOnly,
                        onCheckedChange = { isFajrOnly = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = GeoColor.Quran,
                            uncheckedThumbColor = Color.LightGray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }

                GeoButton(
                    text = if (pickedUri == null) "اختر ملف الصوت" else "تم اختيار الملف",
                    icon = if (pickedUri == null) Icons.Default.AudioFile else Icons.Default.CheckCircle,
                    accent = GeoColor.Quran,
                    variant = GeoButtonStyle.Outline,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { picker.launch("audio/*") }
                )

                if (uploading || uploadTotal > 0) {
                    TransferCounterBar(
                        label = "رفع الأذان للسحابة",
                        sentBytes = uploadSent,
                        totalBytes = uploadTotal,
                        isUpload = true
                    )
                }
            }
        },
        confirmButton = {
            GeoButton(
                text = if (uploading) "جارٍ الرفع" else "رفع",
                icon = Icons.Default.CloudUpload,
                accent = GeoColor.Quran,
                variant = GeoButtonStyle.Filled,
                enabled = canUpload,
                onClick = {
                    val uri = pickedUri ?: return@GeoButton
                    uploading = true
                    uploadSent = 0L; uploadTotal = 0L
                    scope.launch {
                        val res = AzanCloudRepository.uploadAzanVoice(context, uri, title, muezzinName, description.ifBlank { "أذان سحابي بصوت $muezzinName" }, isFajrOnly) { sent, total ->
                            uploadSent = sent; uploadTotal = total
                        }
                        uploading = false
                        if (res.isSuccess) {
                            Toast.makeText(context, "تم الرفع للسحابة بنجاح", Toast.LENGTH_SHORT).show()
                            onUploaded()
                        } else {
                            Toast.makeText(context, "فشل الرفع: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        },
        dismissButton = {
            GeoButton(
                text = "إلغاء",
                accent = GeoColor.textSecondary(),
                variant = GeoButtonStyle.Ghost,
                onClick = onDismiss
            )
        }
    )
}
