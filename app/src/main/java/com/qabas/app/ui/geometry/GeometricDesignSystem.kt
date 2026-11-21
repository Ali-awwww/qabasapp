package com.qabas.app.ui.geometry

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.AppleDarkBackground
import com.qabas.app.ui.theme.AppleDarkBlue
import com.qabas.app.ui.theme.AppleDarkGreen
import com.qabas.app.ui.theme.AppleDarkIndigo
import com.qabas.app.ui.theme.AppleDarkOrange
import com.qabas.app.ui.theme.AppleDarkRed
import com.qabas.app.ui.theme.AppleDarkSecondarySurface
import com.qabas.app.ui.theme.AppleDarkSeparator
import com.qabas.app.ui.theme.AppleDarkSurface
import com.qabas.app.ui.theme.AppleLightBackground
import com.qabas.app.ui.theme.AppleLightBlue
import com.qabas.app.ui.theme.AppleLightGreen
import com.qabas.app.ui.theme.AppleLightIndigo
import com.qabas.app.ui.theme.AppleLightOrange
import com.qabas.app.ui.theme.AppleLightRed
import com.qabas.app.ui.theme.AppleLightSecondarySurface
import com.qabas.app.ui.theme.AppleLightSeparator
import com.qabas.app.ui.theme.AppleLightSurface
import com.qabas.app.ui.theme.AppThemePreset
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.CyberDarkAccent
import com.qabas.app.ui.theme.CyberDarkBackground
import com.qabas.app.ui.theme.CyberDarkBlue
import com.qabas.app.ui.theme.CyberDarkGreen
import com.qabas.app.ui.theme.CyberDarkOrange
import com.qabas.app.ui.theme.CyberDarkRed
import com.qabas.app.ui.theme.CyberDarkSecondarySurface
import com.qabas.app.ui.theme.CyberDarkSeparator
import com.qabas.app.ui.theme.CyberDarkSurface
import com.qabas.app.ui.theme.CyberLightBackground
import com.qabas.app.ui.theme.CyberLightSecondarySurface
import com.qabas.app.ui.theme.CyberLightSeparator
import com.qabas.app.ui.theme.CyberLightSurface
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.GoldSecondary
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.RobotoMonoFont
import com.qabas.app.ui.theme.TextPrimary
import com.qabas.app.ui.theme.TextSecondary
import com.qabas.app.ui.theme.ThemeManager

/**
 * نظام التصميم الهندسي المتكيّف مع Apple HIG — قسم المحراب (الصلاة والمواقيت)
 * يدعم الوضع الفاتح والداكن بتباين مريح ومساحات لمس 48dp+
 */

// ═══════════════════════════════════════════════
// 1. ثوابت الشبكة الهندسية
// ═══════════════════════════════════════════════
object Geo {
    val R_CARD = 16.dp
    val R_CARD_SM = 12.dp
    val R_CARD_LG = 22.dp
    val R_BUTTON = 12.dp
    val R_BUTTON_SM = 10.dp
    val R_CHIP = 100.dp

    val S_XS = 4.dp
    val S_SM = 8.dp
    val S_MD = 14.dp
    val S_LG = 20.dp
    val S_XL = 28.dp

    val I_XS = 14.dp
    val I_SM = 18.dp
    val I_MD = 22.dp
    val I_LG = 28.dp
    val I_XL = 44.dp

    val B_HAIRLINE = 0.75.dp
    val B_STRONG = 1.5.dp

    val RING_STROKE = 7.dp
    val RING_TRACK_STROKE = 5.dp
}

// ═══════════════════════════════════════════════
// 2. لوحة الألوان الهندسية المتكيفة (Light & Dark Apple HIG)
// ═══════════════════════════════════════════════
object GeoColor {
    private val isDark: Boolean get() = ThemeManager.isDarkState
    private val isCyber: Boolean get() = ThemeManager.activePreset == AppThemePreset.CYBER_HUD_BLUE

    /** خلفيات الأسطح */
    fun surface(): Color = if (isCyber) {
        if (isDark) CyberDarkSurface else CyberLightSurface
    } else if (isDark) AppleDarkSurface else AppleLightSurface
    fun surfaceRaised(): Color = surface()
    fun surfaceSunken(): Color = if (isCyber) {
        if (isDark) CyberDarkSecondarySurface else CyberLightSecondarySurface.copy(alpha = 0.65f)
    } else if (isDark) AppleDarkSecondarySurface else AppleLightSecondarySurface.copy(alpha = 0.65f)
    fun screen(): Color = if (isCyber) {
        if (isDark) CyberDarkBackground else CyberLightBackground
    } else if (isDark) AppleDarkBackground else AppleLightBackground

    /** الحدود */
    fun hairline(): Color = if (isCyber) {
        if (isDark) CyberDarkSeparator else CyberLightSeparator.copy(alpha = 0.55f)
    } else if (isDark) AppleDarkSeparator else AppleLightSeparator.copy(alpha = 0.55f)
    fun outline(): Color = if (isDark) GoldPrimary.copy(alpha = 0.35f) else GoldPrimary.copy(alpha = 0.38f)
    fun outlineStrong(): Color = GoldPrimary.copy(alpha = 0.65f)

    /** الحالات */
    fun selectedBg(): Color = GoldPrimary.copy(alpha = if (isDark) 0.16f else 0.12f)
    fun pressed(): Color = GoldPrimary.copy(alpha = 0.10f)

    /** النصوص */
    fun textPrimary(): Color = TextPrimary
    fun textSecondary(): Color = TextSecondary
    fun textMuted(): Color = TextSecondary.copy(alpha = 0.78f)
    fun onGold(): Color = if (isCyber && isDark) Color(0xFF001A18) else Color.White

    /** ألوان الأقسام متكيّفة مع الوضع الفاتح والداكن */
    val Prayer: Color get() = if (isCyber) CyberDarkBlue else if (isDark) AppleDarkBlue else AppleLightBlue
    val Quran: Color get() = if (isCyber) CyberDarkGreen else if (isDark) AppleDarkGreen else AppleLightGreen
    val Dhikr: Color get() = if (isCyber) CyberDarkOrange else if (isDark) AppleDarkOrange else AppleLightOrange
    val Hadith: Color get() = if (isCyber) CyberDarkAccent else if (isDark) AppleDarkIndigo else AppleLightIndigo
    val Dua: Color get() = if (isCyber) CyberDarkRed else if (isDark) Color(0xFFFF375F) else Color(0xFFD81B60)
    val Learn: Color get() = if (isCyber) CyberDarkBlue else if (isDark) Color(0xFF64D2FF) else Color(0xFF0071A4)
    val Rawatib: Color get() = if (isCyber) com.qabas.app.ui.theme.CyberDarkPrimary else if (isDark) Color(0xFF40CBE0) else Color(0xFF0A8499)
    val Warning: Color get() = if (isCyber) CyberDarkRed else if (isDark) AppleDarkRed else AppleLightRed
    val Reward: Color get() = if (isCyber) CyberDarkOrange else if (isDark) Color(0xFFFFD60A) else Color(0xFFA66F00)
    val Qibla: Color get() = if (isCyber) CyberDarkAccent else if (isDark) AppleDarkIndigo else AppleLightIndigo

    /** تدرجات هندسية هادئة */
    val heroGradient: Brush
        get() = if (isCyber && isDark) {
            Brush.verticalGradient(colors = listOf(CyberDarkSecondarySurface, CyberDarkBackground))
        } else if (isDark) {
            Brush.verticalGradient(colors = listOf(Color(0xFF1C1C1E), Color(0xFF141416)))
        } else if (isCyber) {
            Brush.verticalGradient(colors = listOf(CyberLightSurface, CyberLightBackground))
        } else {
            Brush.verticalGradient(colors = listOf(Color(0xFFFFFFFF), Color(0xFFF6F7FB)))
        }

    val goldGradient: Brush
        get() = Brush.horizontalGradient(colors = listOf(GoldPrimary, GoldSecondary))
}

// ═══════════════════════════════════════════════
// 3. الأشكال الهندسية القياسية
// ═══════════════════════════════════════════════
object GeoShape {
    val Card = RoundedCornerShape(Geo.R_CARD)
    val CardSm = RoundedCornerShape(Geo.R_CARD_SM)
    val CardLg = RoundedCornerShape(Geo.R_CARD_LG)
    val Button = RoundedCornerShape(Geo.R_BUTTON)
    val ButtonSm = RoundedCornerShape(Geo.R_BUTTON_SM)
    val ButtonSmCompat = RoundedCornerShape(Geo.R_BUTTON_SM)
    val Chip = RoundedCornerShape(Geo.R_CHIP)
    val Circle = CircleShape
}

// ═══════════════════════════════════════════════
// 4. بطاقات هندسية أساسية
// ═══════════════════════════════════════════════
@Composable
fun GeoCard(
    modifier: Modifier = Modifier,
    accent: Color = GoldPrimary,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable (Modifier) -> Unit
) {
    val isDark = ThemeManager.isDarkState
    val bg = if (selected) GeoColor.selectedBg() else GeoColor.surfaceRaised()
    val stroke = if (selected) accent.copy(alpha = 0.65f) else GeoColor.hairline()
    val shape = GeoShape.Card

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .clip(shape),
        shape = shape,
        color = bg,
        shadowElevation = if (isDark) 0.dp else 1.5.dp,
        border = BorderStroke(Geo.B_HAIRLINE, stroke),
        content = { content(Modifier.padding(Geo.S_MD)) }
    )
}

@Composable
fun GeoIconBadge(
    icon: ImageVector,
    accent: Color,
    size: Dp = Geo.I_XL,
    iconSize: Dp = Geo.I_LG,
    filled: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(GeoShape.Circle)
            .background(
                if (filled) accent.copy(alpha = 0.16f) else accent.copy(alpha = 0.11f),
                GeoShape.Circle
            )
            .border(Geo.B_HAIRLINE, accent.copy(alpha = 0.28f), GeoShape.Circle),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun GeoHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accent: Color = GoldPrimary,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.5.dp, height = 20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Spacer(Modifier.width(Geo.S_SM))
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = title,
                color = GeoColor.textPrimary(),
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 15.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.let {
                Text(
                    text = it,
                    color = GeoColor.textMuted(),
                    fontFamily = NotoSansFont,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        action?.let {
            Spacer(Modifier.width(Geo.S_SM))
            it()
        }
    }
}

@Composable
fun GeoChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    accent: Color = GoldPrimary,
    onClick: (() -> Unit)? = null
) {
    val bg = if (selected) accent.copy(alpha = 0.14f) else GeoColor.surfaceRaised()
    val border = if (selected) accent.copy(alpha = 0.55f) else GeoColor.hairline()
    val fg = if (selected) accent else GeoColor.textSecondary()

    Surface(
        modifier = modifier
            .then(if (onClick != null) Modifier.defaultMinSize(minHeight = 40.dp).clickable(onClick = onClick) else Modifier),
        shape = GeoShape.Chip,
        color = bg,
        border = BorderStroke(Geo.B_HAIRLINE, border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Geo.S_MD, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            icon?.let {
                Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(Geo.I_XS))
            }
            Text(
                text = text,
                color = fg,
                fontFamily = CairoFont,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
fun GeoButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = GoldPrimary,
    variant: GeoButtonStyle = GeoButtonStyle.Filled,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val bg = when (variant) {
        GeoButtonStyle.Filled -> accent
        GeoButtonStyle.Outline -> GeoColor.surfaceRaised()
        GeoButtonStyle.Ghost -> Color.Transparent
    }
    val fg = when (variant) {
        GeoButtonStyle.Filled -> Color.White
        else -> accent
    }

    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(GeoShape.Button)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        shape = GeoShape.Button,
        color = if (enabled) bg else bg.copy(alpha = 0.35f),
        border = if (variant == GeoButtonStyle.Outline)
            BorderStroke(Geo.B_HAIRLINE, if (enabled) accent.copy(alpha = 0.65f) else accent.copy(alpha = 0.3f))
        else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Geo.S_LG, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Icon(
                    it,
                    contentDescription = null,
                    tint = if (enabled) fg else fg.copy(alpha = 0.4f),
                    modifier = Modifier.size(Geo.I_SM)
                )
                Spacer(Modifier.width(Geo.S_SM))
            }
            Text(
                text = text,
                color = if (enabled) fg else fg.copy(alpha = 0.4f),
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                maxLines = 1
            )
        }
    }
}

enum class GeoButtonStyle { Filled, Outline, Ghost }

@Composable
fun GeoProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    strokeWidth: Dp = Geo.RING_STROKE,
    trackColor: Color = GeoColor.hairline().copy(alpha = 0.45f),
    progressColor: Color = GoldPrimary,
    content: @Composable (() -> Unit)? = null
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(size)) {
            val sw = strokeWidth.toPx()
            val inset = sw / 2f
            val arcSize = androidx.compose.ui.geometry.Size(
                this.size.width - sw,
                this.size.height - sw
            )
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )

            val clamped = progress.coerceIn(0f, 1f)
            if (clamped > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * clamped,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = sw, cap = StrokeCap.Round)
                )
            }
        }
        content?.invoke()
    }
}

@Composable
fun GeoMiniStat(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            color = accent,
            fontFamily = RobotoMonoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            maxLines = 1
        )
        Text(
            text = label,
            color = GeoColor.textMuted(),
            fontFamily = NotoSansFont,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun GeoDivider(
    modifier: Modifier = Modifier,
    color: Color = GeoColor.hairline(),
    inset: Dp = 0.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = inset)
            .height(0.75.dp)
            .background(color)
    )
}
