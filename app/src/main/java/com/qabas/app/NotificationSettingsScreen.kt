package com.qabas.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleGroupedSection
import com.qabas.app.ui.components.AppleInsetDivider
import com.qabas.app.ui.components.AppleListRow
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.components.AppleTopBar
import com.qabas.app.ui.theme.*
import java.util.Locale

/**
 * NotificationSettingsScreen — Apple HIG Light & Dark Settings
 */
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current

    LaunchedEffect(Unit) {
        NotificationPreferencesManager.init(context)
    }

    val masterEnabled by NotificationPreferencesManager.masterEnabled.collectAsState()
    val prayerAzanEnabled by NotificationPreferencesManager.prayerAzanEnabled.collectAsState()
    val suhurReminderEnabled by NotificationPreferencesManager.suhurReminderEnabled.collectAsState()
    val fajrReminderEnabled by NotificationPreferencesManager.fajrReminderEnabled.collectAsState()
    val dailyHadithEnabled by NotificationPreferencesManager.dailyHadithEnabled.collectAsState()
    val morningAthkarEnabled by NotificationPreferencesManager.morningAthkarEnabled.collectAsState()
    val eveningAthkarEnabled by NotificationPreferencesManager.eveningAthkarEnabled.collectAsState()
    val trendAlertsEnabled by NotificationPreferencesManager.trendAlertsEnabled.collectAsState()
    val soundEnabled by NotificationPreferencesManager.soundEnabled.collectAsState()
    val vibrationEnabled by NotificationPreferencesManager.vibrationEnabled.collectAsState()
    val suhurTime by NotificationPreferencesManager.suhurTime.collectAsState()
    val fajrTime by NotificationPreferencesManager.fajrTime.collectAsState()
    val dailyHadithTime by NotificationPreferencesManager.dailyHadithTime.collectAsState()
    val morningTime by NotificationPreferencesManager.morningTime.collectAsState()
    val eveningTime by NotificationPreferencesManager.eveningTime.collectAsState()
    val streakCount by NotificationPreferencesManager.wiradStreak.collectAsState()

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
    var sendTestNotificationAfterPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPostNotificationPermission = isGranted
        if (isGranted) {
            Toast.makeText(context, "تم منح إذن الإشعارات بنجاح", Toast.LENGTH_SHORT).show()
            AppSoundManager.playSuccess()
            if (sendTestNotificationAfterPermission) {
                sendTestNotificationAfterPermission = false
                QabasNotificationHelper.sendInteractiveTestNotification(context)
            }
        } else {
            sendTestNotificationAfterPermission = false
            Toast.makeText(context, "لم يتم منح الإذن، لن تظهر التنبيهات في شريط النظام", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            AppleTopBar(
                title = "إعدادات الإشعارات",
                subtitle = if (masterEnabled && hasPostNotificationPermission) "نشط • سلسلة الورد $streakCount أيام" else "يحتاج تفعيل أو إذن النظام",
                onBack = {
                    AppSoundManager.playClick()
                    onBack()
                },
                backTestTag = "notification_settings_back_btn"
            )
        },
        containerColor = theme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 36.dp)
        ) {
            // Status & Android Permission Banner
            item {
                AppleCard(cornerRadius = 16.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = theme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "حالة التنبيهات الإيمانية",
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = theme.textPrimary
                                )
                            }

                            val isReady = masterEnabled && hasPostNotificationPermission
                            Surface(
                                shape = CircleShape,
                                color = (if (isReady) theme.success else theme.destructive).copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = if (isReady) "مفعّل" else "معطّل",
                                    fontFamily = CairoFont,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isReady) theme.success else theme.destructive,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatusMiniBadge(
                                modifier = Modifier.weight(1f),
                                label = "سلسلة الورد",
                                value = "$streakCount أيام"
                            )
                            StatusMiniBadge(
                                modifier = Modifier.weight(1f),
                                label = "التفاعل السريع",
                                value = "استماع • مشاركة"
                            )
                        }

                        if (!hasPostNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = theme.destructive.copy(alpha = 0.10f),
                                border = BorderStroke(0.5.dp, theme.destructive.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "إذن الإشعارات غير ممنوح",
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = theme.destructive
                                        )
                                        Text(
                                            text = "اسمح بالإشعارات لتصلك تنبيهات الأذان والسحر في وقتها.",
                                            fontFamily = NotoSansFont,
                                            fontSize = 11.sp,
                                            color = theme.textSecondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = theme.destructive),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("grant_notification_permission_btn")
                                    ) {
                                        Text(
                                            "منح الإذن",
                                            fontFamily = CairoFont,
                                            fontSize = 12.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        ApplePrimaryButton(
                            text = "إرسال إشعار تجريبي",
                            icon = Icons.Default.Send,
                            onClick = {
                                AppSoundManager.playClick()
                                if (!hasPostNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    sendTestNotificationAfterPermission = true
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    QabasNotificationHelper.sendInteractiveTestNotification(context)
                                }
                            },
                            modifier = Modifier.testTag("send_test_notification_btn")
                        )
                    }
                }
            }

            // Master Switch
            item {
                AppleGroupedSection(header = "التحكم العام") {
                    NotificationSwitchRow(
                        title = "تفعيل جميع التنبيهات",
                        subtitle = "تشغيل أو إيقاف كافة تنبيهات خادم المسلم بضغطة واحدة",
                        icon = Icons.Default.Notifications,
                        iconTint = theme.success,
                        checked = masterEnabled,
                        enabled = true,
                        onCheckedChange = {
                            AppSoundManager.playToggle()
                            NotificationPreferencesManager.setMasterEnabled(context, it)
                        }
                    )
                }
            }

            // Prayer, Suhur & Fajr
            item {
                AppleGroupedSection(header = "الصلاة والسحر والقرآن") {
                    NotificationSwitchRow(
                        title = "نداء الأذان ودخول الوقت",
                        subtitle = "تنبيه بأوقات الصلوات الخمس مع أدوات الإيقاف والتأكيد",
                        icon = Icons.Default.Mosque,
                        iconTint = theme.primary,
                        checked = prayerAzanEnabled && masterEnabled,
                        enabled = masterEnabled,
                        onCheckedChange = {
                            AppSoundManager.playToggle()
                            NotificationPreferencesManager.setPrayerAzanEnabled(context, it)
                        }
                    )
                    AppleInsetDivider()
                    NotificationSwitchRow(
                        title = "تنبيه وقت السحر والاستغفار",
                        subtitle = "تذكير بآيات الاستغفار والقيام مع زر استماع لتلاوة هادئة",
                        icon = Icons.Default.NightlightRound,
                        iconTint = PresetAccent,
                        checked = suhurReminderEnabled && masterEnabled,
                        enabled = masterEnabled,
                        onCheckedChange = {
                            AppSoundManager.playToggle()
                            NotificationPreferencesManager.setSuhurReminderEnabled(context, it)
                        }
                    )
                    if (suhurReminderEnabled && masterEnabled) {
                        TimePresetSelector(
                            currentHour = suhurTime.first,
                            currentMinute = suhurTime.second,
                            presets = listOf(3 to 0, 3 to 30, 4 to 0, 4 to 30),
                            onSelect = { h, m ->
                                AppSoundManager.playClick()
                                NotificationPreferencesManager.setSuhurTime(context, h, m)
                                Toast.makeText(
                                    context,
                                    "تم ضبط وقت تنبيه السحر: ${String.format(Locale.US, "%02d:%02d", h, m)} ص",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                    AppleInsetDivider()
                    NotificationSwitchRow(
                        title = "نور الفجر والورد اليومي",
                        subtitle = "تنبيه بقرآن الفجر لافتتاح يومك بالبركة",
                        icon = Icons.Default.WbSunny,
                        iconTint = theme.warning,
                        checked = fajrReminderEnabled && masterEnabled,
                        enabled = masterEnabled,
                        onCheckedChange = {
                            AppSoundManager.playToggle()
                            NotificationPreferencesManager.setFajrReminderEnabled(context, it)
                        }
                    )
                    if (fajrReminderEnabled && masterEnabled) {
                        TimePresetSelector(
                            currentHour = fajrTime.first,
                            currentMinute = fajrTime.second,
                            presets = listOf(4 to 30, 5 to 0, 5 to 30),
                            onSelect = { h, m ->
                                AppSoundManager.playClick()
                                NotificationPreferencesManager.setFajrTime(context, h, m)
                                Toast.makeText(
                                    context,
                                    "تم ضبط وقت تنبيه الفجر: ${String.format(Locale.US, "%02d:%02d", h, m)} ص",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }
            }

            // Hadith & Athkar
            item {
                AppleGroupedSection(header = "الحديث النبوي والأذكار") {
                    NotificationSwitchRow(
                        title = "حديث اليوم الصحيح",
                        subtitle = "حديث يومي موثق من البخاري ومسلم في الوقت المحدد",
                        icon = Icons.Default.AutoStories,
                        iconTint = theme.accent,
                        checked = dailyHadithEnabled && masterEnabled,
                        enabled = masterEnabled,
                        onCheckedChange = {
                            AppSoundManager.playToggle()
                            NotificationPreferencesManager.setDailyHadithEnabled(context, it)
                        }
                    )
                    if (dailyHadithEnabled && masterEnabled) {
                        TimePresetSelector(
                            currentHour = dailyHadithTime.first,
                            currentMinute = dailyHadithTime.second,
                            presets = listOf(7 to 0, 9 to 0, 12 to 0, 16 to 0, 21 to 0),
                            onSelect = { h, m ->
                                AppSoundManager.playClick()
                                NotificationPreferencesManager.setDailyHadithTime(context, h, m)
                                Toast.makeText(
                                    context,
                                    "تم ضبط وقت حديث اليوم: ${String.format(Locale.US, "%02d:%02d", h, m)}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                    AppleInsetDivider()
                    NotificationSwitchRow(
                        title = "أذكار الصباح",
                        subtitle = "تذكير يومي بأذكار الصباح في بداية النهار",
                        icon = Icons.Default.Brightness5,
                        iconTint = theme.warning,
                        checked = morningAthkarEnabled && masterEnabled,
                        enabled = masterEnabled,
                        onCheckedChange = {
                            AppSoundManager.playToggle()
                            NotificationPreferencesManager.setMorningAthkarEnabled(context, it)
                        }
                    )
                    if (morningAthkarEnabled && masterEnabled) {
                        TimePresetSelector(
                            currentHour = morningTime.first,
                            currentMinute = morningTime.second,
                            presets = listOf(6 to 0, 7 to 0, 8 to 0, 9 to 0),
                            onSelect = { h, m ->
                                AppSoundManager.playClick()
                                NotificationPreferencesManager.setMorningTime(context, h, m)
                            }
                        )
                    }
                    AppleInsetDivider()
                    NotificationSwitchRow(
                        title = "أذكار المساء",
                        subtitle = "تذكير يومي بأذكار المساء قبل الغروب",
                        icon = Icons.Default.Brightness4,
                        iconTint = PresetAccent,
                        checked = eveningAthkarEnabled && masterEnabled,
                        enabled = masterEnabled,
                        onCheckedChange = {
                            AppSoundManager.playToggle()
                            NotificationPreferencesManager.setEveningAthkarEnabled(context, it)
                        }
                    )
                    if (eveningAthkarEnabled && masterEnabled) {
                        TimePresetSelector(
                            currentHour = eveningTime.first,
                            currentMinute = eveningTime.second,
                            presets = listOf(17 to 0, 18 to 0, 19 to 0, 20 to 0),
                            onSelect = { h, m ->
                                AppSoundManager.playClick()
                                NotificationPreferencesManager.setEveningTime(context, h, m)
                            }
                        )
                    }
                }
            }

            // review-r12: حُذف صف «ردود المطور» فقط — مفتاح يحفظ قيمة لا
            // يقرأها أي مستدعٍ: لا يوجد دفع فوري لردود المطور، فكان يعد
            // بإشعار لا يمكن أن يصل أبداً (الدستور §3). أما صف «مرصد الثغور»
            // فأُبقي: له مستهلِك حي — AppNotificationService يمنع إشعارات
            // الترند عند إيقافه، ويرسلها ContentGuard وSocialAccountManager.

            // Community & Support
            item {
                AppleGroupedSection(header = "مرصد الثغور والدعم") {
                    NotificationSwitchRow(
                        title = "مرصد الثغور والتريندات",
                        subtitle = "تنبيهات عند رصد موضوعات تحتاج مساهمة ونشر مقاطع دعوية",
                        icon = Icons.Default.Shield,
                        iconTint = theme.destructive,
                        checked = trendAlertsEnabled && masterEnabled,
                        enabled = masterEnabled,
                        onCheckedChange = {
                            AppSoundManager.playToggle()
                            NotificationPreferencesManager.setTrendAlertsEnabled(context, it)
                        }
                    )
                }
            }

            // Sound & Vibration
            item {
                AppleGroupedSection(header = "الصوت والاهتزاز") {
                    NotificationSwitchRow(
                        title = "صوت الإشعار",
                        subtitle = "تشغيل نغمة التنبيه المعتمدة عند وصول الإشعار",
                        icon = Icons.Default.MusicNote,
                        iconTint = theme.primary,
                        checked = soundEnabled,
                        enabled = true,
                        onCheckedChange = {
                            AppSoundManager.playToggle()
                            NotificationPreferencesManager.setSoundEnabled(context, it)
                        }
                    )
                    AppleInsetDivider()
                    NotificationSwitchRow(
                        title = "الاهتزاز اللمسي",
                        subtitle = "اهتزاز خفيف للتنبيه في الأوضاع الصامتة",
                        icon = Icons.Default.Vibration,
                        iconTint = theme.accent,
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
}

@Composable
private fun StatusMiniBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = theme.secondarySurface.copy(alpha = if (theme.isDark) 0.7f else 0.55f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, fontFamily = CairoFont, fontSize = 11.sp, color = theme.textSecondary)
            Text(value, fontFamily = CairoFont, fontSize = 13.sp, color = theme.textPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NotificationSwitchRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    AppleListRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconBackground = iconTint,
        enabled = enabled,
        showChevron = false,
        trailingContent = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange
            )
        }
    )
}

@Composable
private fun TimePresetSelector(
    currentHour: Int,
    currentMinute: Int,
    presets: List<Pair<Int, Int>>,
    onSelect: (Int, Int) -> Unit
) {
    val theme = QabasTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEach { (h, m) ->
            val isSelected = currentHour == h && currentMinute == m
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) theme.primary.copy(alpha = 0.14f) else theme.secondarySurface.copy(alpha = 0.55f),
                border = if (isSelected) BorderStroke(1.dp, theme.primary) else null,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelect(h, m) }
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = String.format(Locale.US, "%02d:%02d %s", h, m, if (h < 12) "ص" else "م"),
                        fontFamily = CairoFont,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) theme.primary else theme.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
