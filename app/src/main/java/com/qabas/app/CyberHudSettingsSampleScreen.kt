package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleGroupedSection
import com.qabas.app.ui.components.AppleInsetDivider
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.components.AppleSecondaryButton
import com.qabas.app.ui.components.AppleSegmentedControl
import com.qabas.app.ui.components.AppleTopBar
import com.qabas.app.ui.theme.AppThemeMode
import com.qabas.app.ui.theme.AppThemePreset
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.QabasTheme
import com.qabas.app.ui.theme.ThemeManager

/**
 * شاشة نظام الألوان والمظهر (Theme & Appearance Studio — Apple HIG)
 * تدعم الوضع الفاتح والداكن والتلقائي حسب النظام، مع ألوان الهوية المتزنة.
 */
@Composable
fun ThemeDesignStudioScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val prefs = remember { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }

    BackHandler { onBack() }

    val globallyActivePreset by ThemeManager.currentPreset.collectAsState()
    val currentThemeMode by ThemeManager.themeMode.collectAsState()
    var previewPreset by remember(globallyActivePreset) { mutableStateOf(globallyActivePreset) }

    var isHudGridEnabled by remember {
        mutableStateOf(prefs.getBoolean("pref_hud_grid", false))
    }
    var isHudCornersEnabled by remember {
        mutableStateOf(prefs.getBoolean("pref_hud_corners", false))
    }
    val isSoundFxEnabled = remember { AppSoundManager.clickEnabled.value }
    var isOledHighContrast by remember {
        mutableStateOf(prefs.getBoolean("pref_oled_contrast", false))
    }

    val previewPrimary = if (theme.isDark) previewPreset.darkPrimaryColor else previewPreset.lightPrimaryColor
    val previewAccent = if (theme.isDark) previewPreset.darkAccentColor else previewPreset.lightAccentColor
    val previewBg = if (isOledHighContrast && theme.isDark) Color.Black else theme.background
    val previewCard = if (isOledHighContrast && theme.isDark) Color(0xFF141416) else theme.cardSurface

    fun applyThemeGlobally(preset: AppThemePreset) {
        ThemeManager.applyPreset(context, preset)
        Toast.makeText(
            context,
            "تم تفعيل نمط «${preset.title}» على كامل التطبيق",
            Toast.LENGTH_SHORT
        ).show()
    }

    Scaffold(
        containerColor = previewBg,
        topBar = {
            AppleTopBar(
                title = "المظهر والألوان",
                subtitle = if (globallyActivePreset == previewPreset) "النمط المطبّق حالياً" else "معاينة فورية للنمط",
                onBack = onBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Light / Dark / System Mode Selector (Apple HIG Appearance Control)
            AppleGroupedSection(
                header = "مظهر التطبيق",
                footer = "يتبدل مظهر جميع الشاشات فوراً بين الوضع الفاتح والداكن أو يتبع إعدادات جهازك."
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppleSegmentedControl(
                        items = listOf(
                            AppThemeMode.SYSTEM to "تلقائي",
                            AppThemeMode.LIGHT to "فاتح",
                            AppThemeMode.DARK to "داكن"
                        ),
                        selectedItem = currentThemeMode,
                        onSelect = { mode ->
                            ThemeManager.setThemeMode(context, mode)
                        }
                    )
                    Text(
                        text = currentThemeMode.subtitle,
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp
                    )
                }
            }

            // 2. Live Preview Deck
            AppleGroupedSection(header = "معاينة النمط المختار") {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(previewPrimary.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Palette, contentDescription = null, tint = previewPrimary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = previewPreset.title,
                                    color = theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "الصوت المربوط: ${previewPreset.soundTitle}",
                                    color = theme.textSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = { AppSoundManager.playDemoSound(previewPreset.soundPreset) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "استماع للصوت",
                                tint = previewPrimary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ColorSwatchItem("الرئيسي", previewPrimary, Modifier.weight(1f))
                        ColorSwatchItem("الثانوي", previewAccent, Modifier.weight(1f))
                        ColorSwatchItem("السطح", previewCard, Modifier.weight(1f))
                        ColorSwatchItem("الخلفية", previewBg, Modifier.weight(1f))
                    }
                }
            }

            // 3. Color Preset Matrix
            AppleGroupedSection(header = "مجموعات الألوان") {
                AppThemePreset.entries.forEachIndexed { index, preset ->
                    if (index > 0) AppleInsetDivider()
                    val isGloballyActive = globallyActivePreset == preset
                    val isSelectedForPreview = previewPreset == preset
                    val presetPrimary = if (theme.isDark) preset.darkPrimaryColor else preset.lightPrimaryColor
                    val presetAccent = if (theme.isDark) preset.darkAccentColor else preset.lightAccentColor
                    val presetIcon: ImageVector = when (preset) {
                        AppThemePreset.CYBER_HUD_BLUE -> Icons.Default.Palette
                        AppThemePreset.GOLD_ISLAMIC -> Icons.Default.WorkspacePremium
                        AppThemePreset.ROYAL_AMETHYST -> Icons.Default.AutoAwesome
                        AppThemePreset.EMERALD_OASIS -> Icons.Default.Mosque
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 60.dp)
                            .clickable {
                                previewPreset = preset
                                if (isSoundFxEnabled) {
                                    AppSoundManager.playDemoSound(preset.soundPreset)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(presetPrimary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(presetIcon, contentDescription = null, tint = presetPrimary, modifier = Modifier.size(20.dp))
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = preset.title,
                                    color = theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isGloballyActive) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = presetPrimary.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "نشط",
                                            color = presetPrimary,
                                            fontFamily = CairoFont,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = preset.subtitle,
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(presetPrimary))
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(presetAccent))
                        }

                        Spacer(modifier = Modifier.width(6.dp))
                        RadioButton(
                            selected = isSelectedForPreview,
                            onClick = {
                                previewPreset = preset
                                if (isSoundFxEnabled) AppSoundManager.playDemoSound(preset.soundPreset)
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = presetPrimary)
                        )
                    }
                }
            }

            // 4. Action Dock
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppleSecondaryButton(
                    text = "سماع الصوت",
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    tint = previewAccent,
                    onClick = { AppSoundManager.playDemoSound(previewPreset.soundPreset) },
                    modifier = Modifier.weight(0.9f)
                )

                ApplePrimaryButton(
                    text = if (globallyActivePreset == previewPreset) "النمط مطبّق حالياً" else "تطبيق النمط",
                    icon = if (globallyActivePreset == previewPreset) Icons.Default.CheckCircle else Icons.Default.Check,
                    onClick = { applyThemeGlobally(previewPreset) },
                    modifier = Modifier.weight(1.1f)
                )
            }

            // 5. Additional Visual Preferences
            AppleGroupedSection(header = "خيارات العرض المتقدمة") {
                StudioSwitchRow(
                    title = "وضع السواد المطلق في الوضع الداكن (OLED)",
                    subtitle = "خلفية سوداء نقية لراحة العين وتوفير البطارية على شاشات OLED",
                    checked = isOledHighContrast,
                    onCheckedChange = {
                        isOledHighContrast = it
                        prefs.edit().putBoolean("pref_oled_contrast", it).apply()
                    },
                    icon = Icons.Default.DarkMode,
                    primaryColor = previewPrimary
                )
                AppleInsetDivider()
                StudioSwitchRow(
                    title = "الشبكة المساعدة في أدوات التصميم",
                    subtitle = "إظهار خطوط محاذاة خفيفة في معاينات التصميم",
                    checked = isHudGridEnabled,
                    onCheckedChange = {
                        isHudGridEnabled = it
                        prefs.edit().putBoolean("pref_hud_grid", it).apply()
                    },
                    icon = Icons.Default.Grid4x4,
                    primaryColor = previewPrimary
                )
                AppleInsetDivider()
                StudioSwitchRow(
                    title = "تأكيد حواف البطاقات",
                    subtitle = "إبراز حدود خفيفة حول البطاقات عند الحاجة لتباين أعلى",
                    checked = isHudCornersEnabled,
                    onCheckedChange = {
                        isHudCornersEnabled = it
                        prefs.edit().putBoolean("pref_hud_corners", it).apply()
                    },
                    icon = Icons.Default.CropFree,
                    primaryColor = previewPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ColorSwatchItem(
    title: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
    Surface(
        color = theme.secondarySurface.copy(alpha = 0.65f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(0.5.dp, theme.separator, CircleShape)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StudioSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    primaryColor: Color
) {
    val theme = QabasTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(primaryColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) primaryColor else theme.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp
                )
                Text(
                    text = subtitle,
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = theme.onPrimary,
                checkedTrackColor = theme.success
            )
        )
    }
}
