package com.qabas.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*

/**
 * 🛰️ NotificationSettingsScreen — مركز التحكم السيبراني بإعدادات وتفضيلات إشعارات قبس
 * بتصميم: Cybernetic Glassmorphism + Linear Minimalist (Spatial HUD Tech)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        NotificationPreferencesManager.init(context)
    }

    // Collect States
    val masterEnabled by NotificationPreferencesManager.masterEnabled.collectAsState()
    val prayerAzanEnabled by NotificationPreferencesManager.prayerAzanEnabled.collectAsState()
    val suhurReminderEnabled by NotificationPreferencesManager.suhurReminderEnabled.collectAsState()
    val fajrReminderEnabled by NotificationPreferencesManager.fajrReminderEnabled.collectAsState()
    val dailyHadithEnabled by NotificationPreferencesManager.dailyHadithEnabled.collectAsState()
    val morningAthkarEnabled by NotificationPreferencesManager.morningAthkarEnabled.collectAsState()
    val eveningAthkarEnabled by NotificationPreferencesManager.eveningAthkarEnabled.collectAsState()
    val trendAlertsEnabled by NotificationPreferencesManager.trendAlertsEnabled.collectAsState()
    val devChatAlertsEnabled by NotificationPreferencesManager.devChatAlertsEnabled.collectAsState()
    val soundEnabled by NotificationPreferencesManager.soundEnabled.collectAsState()
    val vibrationEnabled by NotificationPreferencesManager.vibrationEnabled.collectAsState()
    val suhurTime by NotificationPreferencesManager.suhurTime.collectAsState()
    val fajrTime by NotificationPreferencesManager.fajrTime.collectAsState()
    val streakCount by NotificationPreferencesManager.wiradStreak.collectAsState()

    // Permission Check & Request
    var hasPostNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPostNotificationPermission = isGranted
        if (isGranted) {
            Toast.makeText(context, "تم منح إذن الإشعارات بنجاح 🔔", Toast.LENGTH_SHORT).show()
            AppSoundManager.playSuccess()
        } else {
            Toast.makeText(context, "لم يتم منح الإذن، لن تظهر التنبيهات في شريط النظام", Toast.LENGTH_LONG).show()
        }
    }

    // Pulse animation for HUD status
    val infiniteTransition = rememberInfiniteTransition(label = "hudPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (masterEnabled && hasPostNotificationPermission) Color(0xFF10B981) else Color(0xFFEF4444))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "إعدادات الإشعارات والتنبيهات 🛰️",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        AppSoundManager.playClick()
                        onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "الرجوع",
                            tint = GoldPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSlate)
            )
        },
        containerColor = DeepSlate
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp)
        ) {
            // 1. HUD Telemetry Status Card
            item {
                GlassmorphicSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = Color(0xFF0F172A).copy(alpha = 0.85f),
                    borderColor = if (masterEnabled) GoldPrimary.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Sensors,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "حالة رادار التنبيهات الإيماني",
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = GoldPrimary
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (masterEnabled && hasPostNotificationPermission) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, if (masterEnabled && hasPostNotificationPermission) Color(0xFF10B981).copy(alpha = pulseAlpha) else Color(0xFFEF4444))
                            ) {
                                Text(
                                    text = if (masterEnabled && hasPostNotificationPermission) "متصل ونشط 🟢" else "معطّل / يحتاج إذناً 🔴",
                                    fontFamily = CairoFont,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (masterEnabled && hasPostNotificationPermission) Color(0xFF10B981) else Color(0xFFEF4444),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatusMiniBadge(label = "الأيقونة المرفقة", value = "أيقونة قبس الرسمية 🛡️")
                            StatusMiniBadge(label = "الأدوات التفاعلية", value = "استماع • مشاركة • إتمام")
                            StatusMiniBadge(label = "سلسلة الورد", value = "$streakCount أيام متتالية 🏆")
                        }

                        // Permission request banner if not granted
                        if (!hasPostNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFEF4444).copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "إذن الإشعارات غير مفعل في نظام أندرويد",
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color(0xFFEF4444)
                                        )
                                        Text(
                                            text = "انقر لمنح الصلاحية لتلقي تنبيهات الأذان والسحر في الوقت الفعلي.",
                                            fontFamily = CairoFont,
                                            fontSize = 10.5.sp,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("منح الإذن", fontFamily = CairoFont, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Interactive Test Action Card
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = GoldPrimary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = GoldPrimary,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        tint = DeepSlate,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "اختبار الإشعار التفاعلي المباشر 🔔",
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "أرسل إشعاراً فورياً للتأكد من ظهور أيقونة قبس وعمل أدوات الاستماع والمشاركة والقراءة.",
                                    fontFamily = CairoFont,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                AppSoundManager.playClick()
                                if (!hasPostNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    QabasNotificationHelper.sendInteractiveTestNotification(context)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = DeepSlate),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "إرسال إشعار تجريبي الآن 🚀",
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // 3. Master Switch
            item {
                GlassmorphicSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    backgroundColor = Color(0xFF131B2E).copy(alpha = 0.85f),
                    borderColor = if (masterEnabled) Color(0xFF10B981).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "التشغيل العام لكافة التنبيهات الإيمانية",
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Text(
                                text = "التحكم الرئيسي بتشغيل أو إيقاف جميع إشعارات قبس بضغطة واحدة",
                                fontFamily = CairoFont,
                                fontSize = 11.5.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(
                            checked = masterEnabled,
                            onCheckedChange = {
                                AppSoundManager.playToggle()
                                NotificationPreferencesManager.setMasterEnabled(context, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF10B981)
                            )
                        )
                    }
                }
            }

            // 4. Section: Prayer & Azan
            item {
                SectionHeader(title = "مواقيت الصلاة والأذان", icon = Icons.Default.Mosque)
            }
            item {
                NotificationToggleItem(
                    title = "نداء الأذان والتنبيه بدخول الوقت",
                    subtitle = "تنبيه بأوقات الصلوات الخمس مع زر إيقاف الأذان وأداة تأكيد الصلاة",
                    icon = Icons.Default.VolumeUp,
                    checked = prayerAzanEnabled && masterEnabled,
                    enabled = masterEnabled,
                    onCheckedChange = {
                        AppSoundManager.playToggle()
                        NotificationPreferencesManager.setPrayerAzanEnabled(context, it)
                    }
                )
            }

            // 5. Section: Suhur & Qiyam
            item {
                SectionHeader(title = "نفحات السحر وقيام الليل", icon = Icons.Default.NightlightRound)
            }
            item {
                Column {
                    NotificationToggleItem(
                        title = "تنبيه وقت السحر والاستغفار",
                        subtitle = "تذكير خاشع بآيات الاستغفار والقيام مع زر استماع فوري لتلاوة هادئة",
                        icon = Icons.Default.Alarm,
                        checked = suhurReminderEnabled && masterEnabled,
                        enabled = masterEnabled,
                        onCheckedChange = {
                            AppSoundManager.playToggle()
                            NotificationPreferencesManager.setSuhurReminderEnabled(context, it)
                        }
                    )
                    if (suhurReminderEnabled && masterEnabled) {
                        Spacer(modifier = Modifier.height(6.dp))
                        TimePresetSelector(
                            currentHour = suhurTime.first,
                            currentMinute = suhurTime.second,
                            presets = listOf(
                                Pair(3, 0),
                                Pair(3, 30),
                                Pair(4, 0),
                                Pair(4, 30)
                            ),
                            onSelect = { h, m ->
                                AppSoundManager.playClick()
                                NotificationPreferencesManager.setSuhurTime(context, h, m)
                                Toast.makeText(context, "تم ضبط وقت تنبيه السحر: ${String.format("%02d:%02d", h, m)} ص 🌙", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // 6. Section: Fajr & Wird
            item {
                SectionHeader(title = "الفجر والورد القرآني", icon = Icons.Default.MenuBook)
            }
            item {
                Column {
                    NotificationToggleItem(
                        title = "نور الفجر والورد اليومي",
                        subtitle = "تنبيه فضائل قرآن الفجر لافتتاح يومك بالبركة مع أدوات تأكيد الورد",
                        icon = Icons.Default.WbSunny,
                        checked = fajrReminderEnabled && masterEnabled,
                        enabled = masterEnabled,
                        onCheckedChange = {
                            AppSoundManager.playToggle()
                            NotificationPreferencesManager.setFajrReminderEnabled(context, it)
                        }
                    )
                    if (fajrReminderEnabled && masterEnabled) {
                        Spacer(modifier = Modifier.height(6.dp))
                        TimePresetSelector(
                            currentHour = fajrTime.first,
                            currentMinute = fajrTime.second,
                            presets = listOf(
                                Pair(4, 30),
                                Pair(5, 0),
                                Pair(5, 30)
                            ),
                            onSelect = { h, m ->
                                AppSoundManager.playClick()
                                NotificationPreferencesManager.setFajrTime(context, h, m)
                                Toast.makeText(context, "تم ضبط وقت تنبيه الفجر: ${String.format("%02d:%02d", h, m)} ص 🌅", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // 7. Section: Daily Hadith
            item {
                SectionHeader(title = "الحديث النبوي الشريف", icon = Icons.Default.AutoStories)
            }
            item {
                NotificationToggleItem(
                    title = "حديث اليوم النبوي الصحيح",
                    subtitle = "حديث صحيح يومياً من البخاري ومسلم في الثامنة صباحاً مع أداة مشاركة فورية",
                    icon = Icons.Default.Book,
                    checked = dailyHadithEnabled && masterEnabled,
                    enabled = masterEnabled,
                    onCheckedChange = {
                        AppSoundManager.playToggle()
                        NotificationPreferencesManager.setDailyHadithEnabled(context, it)
                    }
                )
            }

            // 8. Section: Athkar & Fortification
            item {
                SectionHeader(title = "أذكار الصباح والمساء", icon = Icons.Default.Shield)
            }
            item {
                NotificationToggleItem(
                    title = "أذكار الصباح (حصن المسلم)",
                    subtitle = "تذكير يومي بأذكار الصباح في بداية النهار لتمام الحفظ والسكينة",
                    icon = Icons.Default.Brightness5,
                    checked = morningAthkarEnabled && masterEnabled,
                    enabled = masterEnabled,
                    onCheckedChange = {
                        AppSoundManager.playToggle()
                        NotificationPreferencesManager.setMorningAthkarEnabled(context, it)
                    }
                )
            }
            item {
                NotificationToggleItem(
                    title = "أذكار المساء",
                    subtitle = "تذكير يومي بأذكار المساء قبل الغروب للحفظ والطمأنينة",
                    icon = Icons.Default.Brightness4,
                    checked = eveningAthkarEnabled && masterEnabled,
                    enabled = masterEnabled,
                    onCheckedChange = {
                        AppSoundManager.playToggle()
                        NotificationPreferencesManager.setEveningAthkarEnabled(context, it)
                    }
                )
            }

            // 9. Section: Developer Chat & Direct Alerts
            item {
                SectionHeader(title = "مرصد الثغور وردود المطور", icon = Icons.Default.Chat)
            }
            item {
                NotificationToggleItem(
                    title = "تنبيهات ردود المطور والدعم 💬",
                    subtitle = "إشعار فوري عند قيام المطور بالرد على رسائلك أو متابعة بلاغاتك",
                    icon = Icons.Default.SupportAgent,
                    checked = devChatAlertsEnabled && masterEnabled,
                    enabled = masterEnabled,
                    onCheckedChange = {
                        AppSoundManager.playToggle()
                        NotificationPreferencesManager.setDevChatAlertsEnabled(context, it)
                    }
                )
            }
            item {
                NotificationToggleItem(
                    title = "مرصد الثغور والتريندات 🛡️",
                    subtitle = "تنبيهات عاجلة عند رصد حملات تستوجب مدافعة ونشر مقاطع إيمانية",
                    icon = Icons.Default.Security,
                    checked = trendAlertsEnabled && masterEnabled,
                    enabled = masterEnabled,
                    onCheckedChange = {
                        AppSoundManager.playToggle()
                        NotificationPreferencesManager.setTrendAlertsEnabled(context, it)
                    }
                )
            }

            // 10. Sound & Vibration customization
            item {
                SectionHeader(title = "تخصيص نمط التنبيه", icon = Icons.Default.Tune)
            }
            item {
                NotificationToggleItem(
                    title = "تشغيل الصوت مع الإشعار",
                    subtitle = "إصدار نغمة التنبيه المعتمدة عند وصول الإشعارات الإيمانية",
                    icon = Icons.Default.MusicNote,
                    checked = soundEnabled,
                    enabled = true,
                    onCheckedChange = {
                        AppSoundManager.playToggle()
                        NotificationPreferencesManager.setSoundEnabled(context, it)
                    }
                )
            }
            item {
                NotificationToggleItem(
                    title = "الاهتزاز اللمسي (Haptic Vibration)",
                    subtitle = "اهتزاز الجهاز للتنبيه في الأوضاع الصامتة والليلية",
                    icon = Icons.Default.Vibration,
                    checked = vibrationEnabled,
                    enabled = true,
                    onCheckedChange = {
                        AppSoundManager.playToggle()
                        NotificationPreferencesManager.setVibrationEnabled(context, it)
                    }
                )
            }
        }
    }
}

@Composable
private fun StatusMiniBadge(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(label, fontFamily = CairoFont, fontSize = 9.5.sp, color = Color.White.copy(alpha = 0.5f))
            Text(value, fontFamily = CairoFont, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            fontFamily = CairoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = GoldPrimary
        )
    }
}

@Composable
private fun NotificationToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    GlassmorphicSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        backgroundColor = Color(0xFF111827).copy(alpha = 0.8f),
        borderColor = if (checked && enabled) GoldPrimary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (checked && enabled) GoldPrimary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (checked && enabled) GoldPrimary else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f)
                )
                Text(
                    text = subtitle,
                    fontFamily = CairoFont,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.55f),
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = GoldPrimary,
                    uncheckedThumbColor = Color.LightGray,
                    uncheckedTrackColor = Color.DarkGray
                )
            )
        }
    }
}

@Composable
private fun TimePresetSelector(
    currentHour: Int,
    currentMinute: Int,
    presets: List<Pair<Int, Int>>,
    onSelect: (Int, Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEach { (h, m) ->
            val isSelected = currentHour == h && currentMinute == m
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) GoldPrimary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f),
                border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.1f)),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(h, m) }
            ) {
                Text(
                    text = String.format("%02d:%02d ص", h, m),
                    fontFamily = CairoFont,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}
