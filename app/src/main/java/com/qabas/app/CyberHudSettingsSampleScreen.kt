package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.CornerRadius
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
import com.qabas.app.HudCornerDecor
import com.qabas.app.ui.theme.AppThemePreset
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.RobotoMonoFont
import com.qabas.app.ui.theme.ThemeManager

/**
 * شاشة نظام الألوان والديزاين (Theme & Design Studio)
 * Cybernetic Glassmorphism + Linear Minimalist (Spatial HUD Tech)
 * تصميم هندسي متوازن خالي من أي بيانات تجريبية أو أزرار مكررة،
 * مع ربط حقيقي وكامل بين الألوان، المؤثرات البصرية، والأصوات التفاعلية.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeDesignStudioScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }

    // معالجة الرجوع بنظام BackHandler
    BackHandler { onBack() }

    // النمط المفعل حالياً على التطبيق بالكامل
    val globallyActivePreset by ThemeManager.currentPreset.collectAsState()

    // النمط المختار للمعاينة الحية
    var previewPreset by remember(globallyActivePreset) { mutableStateOf(globallyActivePreset) }

    // إعدادات الواجهة الفضائية الحقيقية المخزنة محلياً
    var isHudGridEnabled by remember {
        mutableStateOf(prefs.getBoolean("pref_hud_grid", true))
    }
    var isHudCornersEnabled by remember {
        mutableStateOf(prefs.getBoolean("pref_hud_corners", true))
    }
    val isSoundFxEnabled = remember { AppSoundManager.clickEnabled.value }
    var isOledHighContrast by remember {
        mutableStateOf(prefs.getBoolean("pref_oled_contrast", false))
    }

    // ألوان النمط المختار للمعاينة الحية
    val primaryColor = previewPreset.primaryColor
    val accentColor = previewPreset.accentColor
    val bgColor = if (isOledHighContrast && previewPreset.isDark) Color(0xFF02040A) else previewPreset.backgroundColor
    val cardColor = if (isOledHighContrast && previewPreset.isDark) Color(0xFF090D18) else previewPreset.cardSurfaceColor

    // أنيميشن نبض النيون الهندسي
    val infiniteTransition = rememberInfiniteTransition(label = "theme_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    fun applyThemeGlobally(preset: AppThemePreset) {
        ThemeManager.applyPreset(context, preset)
        Toast.makeText(
            context,
            "✅ تم تفعيل نمط «${preset.title}» على كامل التطبيق بنجاح!",
            Toast.LENGTH_LONG
        ).show()
    }

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        // Spatial HUD Grid Canvas (خلفية شبكية هندسية عند التفعيل)
        if (isHudGridEnabled) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 32.dp.toPx()
                val gridColor = primaryColor.copy(alpha = 0.035f)
                for (x in 0..(size.width / step).toInt()) {
                    drawLine(
                        color = gridColor,
                        start = Offset(x * step, 0f),
                        end = Offset(x * step, size.height),
                        strokeWidth = 1f
                    )
                }
                for (y in 0..(size.height / step).toInt()) {
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y * step),
                        end = Offset(size.width, y * step),
                        strokeWidth = 1f
                    )
                }
            }
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(cardColor.copy(alpha = 0.95f), bgColor)
                            )
                        )
                        .drawBehind {
                            drawLine(
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        primaryColor.copy(alpha = 0.85f),
                                        accentColor,
                                        Color.Transparent
                                    )
                                ),
                                start = Offset(0f, size.height),
                                end = Offset(size.width, size.height),
                                strokeWidth = 1.5.dp.toPx()
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(cardColor)
                                    .border(1.dp, primaryColor.copy(alpha = 0.45f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "رجوع",
                                    tint = primaryColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(primaryColor.copy(alpha = pulseAlpha))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "نظام الألوان والديزاين",
                                        color = if (previewPreset.isDark) Color.White else Color(0xFF0F172A),
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                                Text(
                                    text = "تخصيص الهوية البصرية والمؤثرات التفاعلية",
                                    color = primaryColor.copy(alpha = 0.9f),
                                    fontFamily = NotoSansFont,
                                    fontSize = 10.5.sp
                                )
                            }
                        }

                        // شارة حالة النمط
                        Surface(
                            color = primaryColor.copy(alpha = 0.15f),
                            shape = CutCornerShape(6.dp),
                            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = if (globallyActivePreset == previewPreset) "مُطبّق حالياً ✓" else "معاينة حية 👁️",
                                color = primaryColor,
                                fontFamily = CairoFont,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── 1. منصة المعاينة الحية التفاعلية للمكونات (Live Component Preview Deck) ──
                StudioSectionHeader(
                    title = "المعاينة البصرية المباشرة للنمط",
                    color = primaryColor
                )

                StudioGlassPanel(
                    borderColor = primaryColor.copy(alpha = 0.6f),
                    containerColor = cardColor,
                    isNeon = previewPreset.isNeon,
                    showCorners = isHudCornersEnabled
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CutCornerShape(8.dp))
                                        .background(primaryColor.copy(alpha = 0.15f))
                                        .border(1.dp, primaryColor.copy(alpha = 0.4f), CutCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Palette,
                                        contentDescription = null,
                                        tint = primaryColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = previewPreset.title,
                                        color = if (previewPreset.isDark) Color.White else Color(0xFF0F172A),
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp
                                    )
                                    Text(
                                        text = "${previewPreset.soundEmoji} الصوت المربوط: ${previewPreset.soundTitle}",
                                        color = accentColor,
                                        fontFamily = NotoSansFont,
                                        fontSize = 10.5.sp
                                    )
                                }
                            }

                            // زر معاينة الصوت السريع
                            IconButton(
                                onClick = { AppSoundManager.playDemoSound(previewPreset.soundPreset) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(primaryColor.copy(alpha = 0.12f))
                                    .border(1.dp, primaryColor.copy(alpha = 0.35f), CircleShape)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "استماع للصوت",
                                    tint = primaryColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // عينات ألوان النمط الحية
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ColorSwatchItem(
                                title = "الرئيسي",
                                color = primaryColor,
                                modifier = Modifier.weight(1f)
                            )
                            ColorSwatchItem(
                                title = "المميز",
                                color = accentColor,
                                modifier = Modifier.weight(1f)
                            )
                            ColorSwatchItem(
                                title = "السطح",
                                color = cardColor,
                                modifier = Modifier.weight(1f)
                            )
                            ColorSwatchItem(
                                title = "الخلفية",
                                color = bgColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // ── 2. مصفوفة أنماط الديزاين (Design Presets Matrix) ──
                StudioSectionHeader(
                    title = "مصفوفة أنماط الديزاين المتاحة",
                    color = primaryColor
                )

                AppThemePreset.entries.forEach { preset ->
                    val isGloballyActive = globallyActivePreset == preset
                    val isSelectedForPreview = previewPreset == preset

                    val presetIcon: ImageVector = when (preset) {
                        AppThemePreset.CYBER_HUD_BLUE -> Icons.Default.Sensors
                        AppThemePreset.GOLD_ISLAMIC -> Icons.Default.WorkspacePremium
                        AppThemePreset.ROYAL_AMETHYST -> Icons.Default.AutoAwesome
                        AppThemePreset.EMERALD_OASIS -> Icons.Default.Mosque
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CutCornerShape(12.dp))
                            .clickable {
                                previewPreset = preset
                                if (isSoundFxEnabled) {
                                    AppSoundManager.playDemoSound(preset.soundPreset)
                                }
                            },
                        shape = CutCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelectedForPreview) preset.cardSurfaceColor else preset.cardSurfaceColor.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(
                            if (isSelectedForPreview) 1.8.dp else 1.dp,
                            if (isSelectedForPreview) preset.primaryColor else preset.primaryColor.copy(alpha = 0.22f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                // أيقونة النمط المميزة
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CutCornerShape(8.dp))
                                        .background(preset.primaryColor.copy(alpha = 0.16f))
                                        .border(1.dp, preset.primaryColor.copy(alpha = 0.45f), CutCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = presetIcon,
                                        contentDescription = null,
                                        tint = preset.primaryColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = preset.title,
                                            color = if (preset.isDark) Color.White else Color(0xFF0F172A),
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isGloballyActive) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = preset.primaryColor.copy(alpha = 0.2f),
                                                shape = CutCornerShape(4.dp),
                                                border = BorderStroke(0.8.dp, preset.primaryColor)
                                            ) {
                                                Text(
                                                    text = "المُطبّق ✓",
                                                    color = preset.primaryColor,
                                                    fontFamily = CairoFont,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = preset.subtitle,
                                        color = if (preset.isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                                        fontFamily = NotoSansFont,
                                        fontSize = 10.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // شارة المؤثر الصوتي المربوط بالنمط
                                    Text(
                                        text = "${preset.soundEmoji} الصوت: ${preset.soundTitle}",
                                        color = preset.accentColor,
                                        fontFamily = NotoSansFont,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // عينات ألوان النمط ومؤشر الاختيار
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // ثلاث دوائر ألوان متناسقة
                                Row(
                                    modifier = Modifier
                                        .clip(CutCornerShape(6.dp))
                                        .background(preset.backgroundColor)
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(modifier = Modifier.size(13.dp).clip(CircleShape).background(preset.primaryColor))
                                    Box(modifier = Modifier.size(13.dp).clip(CircleShape).background(preset.accentColor))
                                    Box(modifier = Modifier.size(13.dp).clip(CircleShape).background(preset.cardSurfaceColor).border(0.5.dp, Color.Gray, CircleShape))
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                RadioButton(
                                    selected = isSelectedForPreview,
                                    onClick = {
                                        previewPreset = preset
                                        if (isSoundFxEnabled) {
                                            AppSoundManager.playDemoSound(preset.soundPreset)
                                        }
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = preset.primaryColor,
                                        unselectedColor = Color.Gray.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                // ── 3. شريط التحكم والإجراء الموحد (Unified Action Dock) ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // زر تجربة الصوت المربوط
                    OutlinedButton(
                        onClick = { AppSoundManager.playDemoSound(previewPreset.soundPreset) },
                        modifier = Modifier
                            .weight(0.85f)
                            .height(48.dp),
                        shape = CutCornerShape(8.dp),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "سماع الصوت 🔊",
                            color = accentColor,
                            fontFamily = CairoFont,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // زر تطبيق النمط الكامل
                    Button(
                        onClick = { applyThemeGlobally(previewPreset) },
                        modifier = Modifier
                            .weight(1.15f)
                            .height(48.dp),
                        shape = CutCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (globallyActivePreset == previewPreset) primaryColor.copy(alpha = 0.25f) else primaryColor
                        ),
                        border = BorderStroke(1.2.dp, primaryColor)
                    ) {
                        Icon(
                            imageVector = if (globallyActivePreset == previewPreset) Icons.Default.CheckCircle else Icons.Default.FlashOn,
                            contentDescription = null,
                            tint = if (globallyActivePreset == previewPreset) primaryColor else (if (previewPreset.isDark) Color.Black else Color.White),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (globallyActivePreset == previewPreset) "النمط مُطبّق حالياً ✓" else "تطبيق النمط ⚡",
                            color = if (globallyActivePreset == previewPreset) primaryColor else (if (previewPreset.isDark) Color.Black else Color.White),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // ── 4. تخصيص المؤثرات البصرية التفاعلية الحقيقية (Spatial HUD Customization) ──
                StudioSectionHeader(
                    title = "تخصيص المؤثرات والواجهة الفضائية",
                    color = primaryColor
                )

                StudioGlassPanel(
                    borderColor = primaryColor.copy(alpha = 0.35f),
                    containerColor = cardColor,
                    isNeon = previewPreset.isNeon,
                    showCorners = isHudCornersEnabled
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. شبكة الـ HUD الفضائية
                        StudioSwitchRow(
                            title = "شبكة الخطوط الفضائية (Spatial Grid)",
                            subtitle = "إظهار شبكة الخطوط الهندسية المتوهجة في خلفية الشاشات",
                            checked = isHudGridEnabled,
                            onCheckedChange = {
                                isHudGridEnabled = it
                                prefs.edit().putBoolean("pref_hud_grid", it).apply()
                            },
                            icon = Icons.Default.Grid4x4,
                            primaryColor = primaryColor,
                            accentColor = accentColor,
                            cardColor = cardColor,
                            isDark = previewPreset.isDark
                        )

                        HorizontalDivider(color = primaryColor.copy(alpha = 0.12f))

                        // 2. زخارف الأركان الهندسية
                        StudioSwitchRow(
                            title = "زخارف الزوايا الرقمية (HUD Corners)",
                            subtitle = "إبراز علامات الزوايا الهندسية للبطاقات والألواح الشفافة",
                            checked = isHudCornersEnabled,
                            onCheckedChange = {
                                isHudCornersEnabled = it
                                prefs.edit().putBoolean("pref_hud_corners", it).apply()
                            },
                            icon = Icons.Default.CropFree,
                            primaryColor = primaryColor,
                            accentColor = accentColor,
                            cardColor = cardColor,
                            isDark = previewPreset.isDark
                        )

                        HorizontalDivider(color = primaryColor.copy(alpha = 0.12f))

                        // 3. تباين فائق لشاشات OLED
                        StudioSwitchRow(
                            title = "وضع السواد المطلق (OLED Pure Black)",
                            subtitle = "خلفية سوداء نقية 100% لتوفير الطاقة وزيادة حدة التباين",
                            checked = isOledHighContrast,
                            onCheckedChange = {
                                isOledHighContrast = it
                                prefs.edit().putBoolean("pref_oled_contrast", it).apply()
                            },
                            icon = Icons.Default.DarkMode,
                            primaryColor = primaryColor,
                            accentColor = accentColor,
                            cardColor = cardColor,
                            isDark = previewPreset.isDark
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ColorSwatchItem(
    title: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF070B14).copy(alpha = 0.65f),
        shape = CutCornerShape(6.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.85f),
                fontFamily = CairoFont,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StudioGlassPanel(
    borderColor: Color,
    containerColor: Color,
    isNeon: Boolean = false,
    showCorners: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "panel_glow")
    val glowAnim by if (isNeon) {
        infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowAnim"
        )
    } else {
        remember { mutableStateOf(1.0f) }
    }

    val finalBorderAlpha = if (isNeon) 0.8f * glowAnim else 1.0f
    val finalBorderWidth = if (isNeon) 1.5.dp else 1.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CutCornerShape(12.dp))
            .background(containerColor)
            .drawBehind {
                if (isNeon) {
                    drawRoundRect(
                        color = borderColor.copy(alpha = 0.25f * glowAnim),
                        size = size,
                        cornerRadius = CornerRadius(14.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx())
                    )
                }
            }
            .border(
                BorderStroke(
                    finalBorderWidth,
                    Brush.linearGradient(
                        listOf(
                            borderColor.copy(alpha = finalBorderAlpha),
                            borderColor.copy(alpha = finalBorderAlpha * 0.3f),
                            Color.Transparent
                        )
                    )
                ),
                CutCornerShape(12.dp)
            )
    ) {
        if (showCorners) {
            HudCornerDecor(modifier = Modifier.matchParentSize(), color = borderColor.copy(alpha = 0.35f))
        }
        content()
    }
}

@Composable
private fun StudioSectionHeader(title: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.5.dp)
                .height(16.dp)
                .background(color, CutCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = Color.White,
            fontFamily = CairoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp
        )
    }
}

@Composable
private fun StudioSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    primaryColor: Color,
    accentColor: Color,
    cardColor: Color,
    isDark: Boolean
) {
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
                    .size(38.dp)
                    .clip(CutCornerShape(8.dp))
                    .background(cardColor)
                    .border(
                        1.dp,
                        if (checked) primaryColor.copy(alpha = 0.7f) else Color.Gray.copy(alpha = 0.3f),
                        CutCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) primaryColor else Color.Gray,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = if (isDark) Color.White else Color(0xFF0F172A),
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                    fontFamily = NotoSansFont,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = primaryColor,
                checkedTrackColor = accentColor.copy(alpha = 0.5f),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = cardColor
            )
        )
    }
}
