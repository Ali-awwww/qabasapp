package com.qabas.app

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleGroupedSection
import com.qabas.app.ui.components.AppleInsetDivider
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.components.AppleSecondaryButton
import com.qabas.app.ui.components.AppleTopBar
import com.qabas.app.ui.theme.*

/**
 * شاشة تخصيص نمط ونظام المؤثرات الصوتية (Sound Effects Studio — Apple HIG)
 */
@Composable
fun SoundEffectsSubScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current

    val masterEnabled by AppSoundManager.masterEnabled.collectAsState()
    val currentPreset by AppSoundManager.currentPreset.collectAsState()
    val currentVolume by AppSoundManager.volume.collectAsState()
    val clickEnabled by AppSoundManager.clickEnabled.collectAsState()
    val animEnabled by AppSoundManager.animEnabled.collectAsState()
    val dhikrEnabled by AppSoundManager.dhikrEnabled.collectAsState()
    val hapticEnabled by AppSoundManager.hapticEnabled.collectAsState()

    Scaffold(
        topBar = {
            AppleTopBar(
                title = "المؤثرات الصوتية",
                subtitle = "التحكم في الأصوات التفاعلية والاهتزاز",
                onBack = onBack
            )
        },
        containerColor = theme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppleCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (masterEnabled) theme.primary.copy(alpha = 0.14f) else theme.secondarySurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (masterEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = null,
                                tint = if (masterEnabled) theme.primary else theme.textSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    Translator.tr("المؤثرات الصوتية العامة"),
                                    color = theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = (if (masterEnabled) theme.success else theme.destructive).copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (masterEnabled) "نشط" else "معطّل",
                                        color = if (masterEnabled) theme.success else theme.destructive,
                                        fontFamily = CairoFont,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (masterEnabled) Translator.tr("تشغيل أصوات النقر والتحولات والمسبحة") else Translator.tr("كتم كافة المؤثرات التفاعلية"),
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                    Switch(
                        checked = masterEnabled,
                        onCheckedChange = {
                            AppSoundManager.setMasterEnabled(context, it)
                            if (it) AppSoundManager.playActivate()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = theme.onPrimary, checkedTrackColor = theme.success)
                    )
                }
            }

            AppleGroupedSection(header = "مستوى الصوت") {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (currentVolume > 0.5f) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeDown,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Slider(
                            value = currentVolume,
                            onValueChange = { AppSoundManager.setVolume(context, it) },
                            onValueChangeFinished = { AppSoundManager.playClick() },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = theme.primary,
                                activeTrackColor = theme.primary,
                                inactiveTrackColor = theme.secondarySurface
                            )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "${(currentVolume * 100).toInt()}%",
                            color = theme.primary,
                            fontFamily = NotoSansFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.width(42.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0.25f to "25%", 0.50f to "50%", 0.75f to "75%", 1.0f to "100%").forEach { (vol, label) ->
                            val isCurrent = (currentVolume * 100).toInt() == (vol * 100).toInt()
                            Surface(
                                onClick = {
                                    AppSoundManager.setVolume(context, vol)
                                    AppSoundManager.playClick()
                                },
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCurrent) theme.primary.copy(alpha = 0.14f) else theme.secondarySurface.copy(alpha = 0.6f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        label,
                                        color = if (isCurrent) theme.primary else theme.textSecondary,
                                        fontSize = 12.sp,
                                        fontFamily = NotoSansFont,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AppleGroupedSection(header = "تفاصيل المؤثرات") {
                SoundToggleItem(
                    icon = Icons.Default.TouchApp,
                    tint = theme.primary,
                    title = Translator.tr("أصوات النقر واللمس"),
                    subtitle = Translator.tr("صدى صوتي خفيف عند الضغط على الأزرار"),
                    checked = clickEnabled,
                    onCheckedChange = {
                        AppSoundManager.setClickEnabled(context, it)
                        if (it) AppSoundManager.playClick()
                    }
                )
                AppleInsetDivider()
                SoundToggleItem(
                    icon = Icons.Default.AutoAwesomeMotion,
                    tint = theme.primary,
                    title = Translator.tr("أصوات انتقال الشاشات"),
                    subtitle = Translator.tr("مؤثر انسيابي عند التنقل بين الشاشات"),
                    checked = animEnabled,
                    onCheckedChange = {
                        AppSoundManager.setAnimEnabled(context, it)
                        if (it) AppSoundManager.playAnimationSound()
                    }
                )
                AppleInsetDivider()
                SoundToggleItem(
                    icon = Icons.Default.Mosque,
                    tint = theme.warning,
                    title = Translator.tr("أصوات السبحة والأذكار"),
                    subtitle = Translator.tr("نقرة سبحة مريحة مع كل تسبيحة"),
                    checked = dhikrEnabled,
                    onCheckedChange = {
                        AppSoundManager.setDhikrEnabled(context, it)
                        if (it) AppSoundManager.playDhikr()
                    }
                )
                AppleInsetDivider()
                SoundToggleItem(
                    icon = Icons.Default.Vibration,
                    tint = Color(0xFF5856D6),
                    title = Translator.tr("الاهتزاز التفاعلي (Haptic)"),
                    subtitle = Translator.tr("نبضة اهتزاز خفيفة مع كل تفاعل"),
                    checked = hapticEnabled,
                    onCheckedChange = {
                        AppSoundManager.setHapticEnabled(context, it)
                        if (it) AppSoundManager.playClick()
                    }
                )
            }

            AppleGroupedSection(header = "أنماط الصوت") {
                AppSoundPreset.entries.forEachIndexed { idx, preset ->
                    if (idx > 0) AppleInsetDivider()
                    val isSelected = currentPreset == preset
                    Surface(
                        onClick = {
                            AppSoundManager.setPreset(context, preset)
                            AppSoundManager.playDemoSound(preset)
                            Toast.makeText(context, "تم تفعيل نمط: ${preset.title}", Toast.LENGTH_SHORT).show()
                        },
                        color = if (isSelected) theme.primary.copy(alpha = 0.08f) else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(preset.iconEmoji, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(preset.title, color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(preset.subtitle, color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.5.sp)
                            }
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "محدد", tint = theme.primary, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppleSecondaryButton(
                    text = "تجربة النقر",
                    onClick = { AppSoundManager.playClick() },
                    modifier = Modifier.weight(1f)
                )
                AppleSecondaryButton(
                    text = "تجربة الإنجاز",
                    tint = theme.success,
                    onClick = { AppSoundManager.playSuccess() },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SoundToggleItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val theme = QabasTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 54.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, color = theme.textPrimary, fontFamily = CairoFont, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = theme.onPrimary, checkedTrackColor = theme.success)
        )
    }
}

/**
 * شاشة اختيار لغة التطبيق (Language Selection Studio — Apple HIG)
 */
@Composable
fun LanguageSelectionSubScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val currentLang by UserPreferencesManager.appLanguage.collectAsState()

    Scaffold(
        topBar = {
            AppleTopBar(
                title = Translator.tr("لغة التطبيق"),
                onBack = onBack
            )
        },
        containerColor = theme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppleCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Translate, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = Translator.tr("تخصيص لغة الواجهة"),
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Text(
                        text = Translator.tr("اختر اللغة المفضلة لاستخدام التطبيق. سيتم تحديث كافة المسميات والأزرار فوراً دون الحاجة لإعادة التشغيل."),
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            AppleGroupedSection(header = "اللغات المتاحة") {
                listOf(
                    Triple("ar", "العربية (Arabic)", "🇪🇬"),
                    Triple("en", "English (الإنجليزية)", "🇺🇸")
                ).forEachIndexed { idx, (langCode, langName, flag) ->
                    if (idx > 0) AppleInsetDivider()
                    val isSelected = currentLang == langCode
                    Surface(
                        onClick = {
                            UserPreferencesManager.setAppLanguage(context, langCode)
                            AppSoundManager.playSuccess()
                            Toast.makeText(context, Translator.tr("تم تغيير اللغة بنجاح"), Toast.LENGTH_SHORT).show()
                        },
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(flag, fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = langName,
                                        color = if (isSelected) theme.primary else theme.textPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 15.sp
                                    )
                                    if (isSelected) {
                                        Text(
                                            text = Translator.tr("اللغة النشطة حالياً"),
                                            color = theme.primary,
                                            fontFamily = NotoSansFont,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = theme.primary, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }

            ApplePrimaryButton(
                text = Translator.tr("العودة للإعدادات"),
                onClick = onBack
            )
        }
    }
}

/**
 * شاشة اختيار نمط الشاشة الرئيسية (Home Screen Style Mode SubScreen — Apple HIG)
 */
@Composable
fun HomeScreenStyleSubScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val currentStyle = HomeScreenStyleManager.currentStyle.collectAsState().value

    SubScreenScaffold(
        title = "نمط الشاشة الرئيسية",
        icon = Icons.Default.DashboardCustomize,
        onBack = onBack
    ) {
        AppleCard {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(theme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = theme.primary, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("تخصيص الشاشة الرئيسية", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                    Text(
                        "اختر التخطيط الذي يناسب طريقة عملك مع الحفاظ على كافة المشاريع والبيانات.",
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        HomeScreenStyle.entries.forEach { style ->
            val isSelected = currentStyle == style
            AppleCard(
                containerColor = if (isSelected) theme.primary.copy(alpha = 0.08f) else theme.cardSurface,
                onClick = {
                    HomeScreenStyleManager.setStyle(context, style)
                    Toast.makeText(context, "تم تفعيل ${style.title} بنجاح", Toast.LENGTH_SHORT).show()
                }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(42.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) theme.primary.copy(alpha = 0.16f) else theme.secondarySurface
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(style.iconEmoji, fontSize = 20.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(style.title, color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                                Surface(
                                    color = if (isSelected) theme.primary.copy(alpha = 0.14f) else theme.secondarySurface,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = style.badge,
                                        color = if (isSelected) theme.primary else theme.textSecondary,
                                        fontFamily = CairoFont,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        if (isSelected) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "محدد", tint = theme.primary, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(style.subtitle, color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}

/**
 * شاشة تخصيص أنميشن وحركات التنقل (Transition Animations Sub-Screen — Apple HIG)
 */
@Composable
fun TransitionAnimationsSubScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current

    val currentAnim by AppAnimationManager.currentAnimation.collectAsState()
    val currentSpeed by AppAnimationManager.currentSpeed.collectAsState()
    val reducedMotion by UserPreferencesManager.reducedMotion.collectAsState()

    var simulatorStateIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            AppleTopBar(
                title = "أنميشن وحركات التنقل",
                onBack = onBack
            )
        },
        containerColor = theme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppleCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("معاينة الحركة المباشرة", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("اختبر تأثير الانتقال الحالي بين الشاشات", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.5.sp)
                        }
                        Surface(
                            color = theme.primary.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${currentSpeed.badge} • ${currentAnim.iconEmoji}",
                                color = theme.primary,
                                fontFamily = NotoSansFont,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(126.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(theme.secondarySurface.copy(alpha = 0.65f))
                            .border(0.5.dp, theme.separator.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = simulatorStateIndex,
                            transitionSpec = {
                                AppAnimationManager.createTransitionSpec(
                                    initialState = if (initialState == 0) AppState.HOME else AppState.HASANAT_HUB,
                                    targetState = if (targetState == 0) AppState.HOME else AppState.HASANAT_HUB,
                                    reducedMotion = reducedMotion
                                )
                            },
                            label = "SimulatorTransition"
                        ) { stateIdx ->
                            if (stateIdx == 0) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp)) {
                                    Icon(Icons.Default.Home, contentDescription = null, tint = theme.primary, modifier = Modifier.size(30.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("الشاشة الرئيسية", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                    Text("الاستوديو والمحتوى الإيماني", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.sp)
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp)) {
                                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = theme.success, modifier = Modifier.size(30.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("مِشكاة الهُدى", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                    Text("القرآن الكريم والأذكار", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    ApplePrimaryButton(
                        text = "اختبار تأثير الحركة",
                        icon = Icons.Default.PlayArrow,
                        onClick = { simulatorStateIndex = if (simulatorStateIndex == 0) 1 else 0 }
                    )
                }
            }

            AppleGroupedSection(header = "سرعة وتوقيت الحركة") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnimationSpeedOption.entries.forEach { speedOpt ->
                        val isSpeedSel = currentSpeed == speedOpt
                        Surface(
                            onClick = {
                                AppAnimationManager.setSpeed(context, speedOpt)
                                Toast.makeText(context, "تم ضبط السرعة: ${speedOpt.title}", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSpeedSel) theme.primary.copy(alpha = 0.14f) else theme.secondarySurface.copy(alpha = 0.6f),
                            border = BorderStroke(if (isSpeedSel) 1.5.dp else 0.5.dp, if (isSpeedSel) theme.primary else theme.separator.copy(alpha = 0.4f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(speedOpt.badge, color = if (isSpeedSel) theme.primary else theme.textSecondary, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(speedOpt.title.substringBefore(" ("), color = if (isSpeedSel) theme.textPrimary else theme.textSecondary, fontFamily = CairoFont, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }

            AppleGroupedSection(header = "أنماط الانتقال") {
                AppTransitionAnimation.entries.forEachIndexed { idx, animOption ->
                    if (idx > 0) AppleInsetDivider()
                    val isSelected = currentAnim == animOption
                    Surface(
                        onClick = {
                            AppAnimationManager.setAnimation(context, animOption)
                            Toast.makeText(context, "تم تفعيل نمط: ${animOption.title}", Toast.LENGTH_SHORT).show()
                        },
                        color = Color.Transparent
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text(animOption.iconEmoji, fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(animOption.title, color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(animOption.badge, color = if (isSelected) theme.primary else theme.textSecondary, fontFamily = CairoFont, fontSize = 10.5.sp)
                                    }
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "محدد", tint = theme.primary, modifier = Modifier.size(22.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(animOption.subtitle, color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }
    }
}
