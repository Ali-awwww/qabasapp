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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.qabas.app.ui.theme.CardSurface
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.GoldSecondary
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.RobotoMonoFont
import com.qabas.app.ui.theme.StaticDeepSlate
import com.qabas.app.ui.theme.TextPrimary
import com.qabas.app.ui.theme.TextSecondary

/**
 * نظام التصميم الهندسي النقي — قسم المحراب (الصلاة والمواقيت)
 *
 * المبادئ:
 * 1. أشكال هندسية نقية موحدة (نصف قطر واحد للبطاقات، دائرة للأيقونات)
 * 2. تسلسل بصري واضح: هيرو ← عنوان ← محتوى، بلا تداخل أو ازدواج
 * 3. شبكة مسافات منتظمة (مضاعفات 4dp)
 * 4. ألوان عبر رموز الثيم (لا ألوان مثبّتة) حتى يعمل تبديل الثيم
 * 5. لا إيموجي في الواجهة — أيقونات Material فقط
 */

// ═══════════════════════════════════════════════
// 1. ثوابت الشبكة الهندسية
// ═══════════════════════════════════════════════
object Geo {
    // أنصاف الأقطار الموحدة
    val R_CARD = 18.dp
    val R_CARD_SM = 12.dp
    val R_CARD_LG = 26.dp
    val R_BUTTON = 12.dp
    val R_BUTTON_SM = 8.dp
    val R_CHIP = 100.dp

    // الشبكة (مضاعفات 4dp)
    val S_XS = 4.dp
    val S_SM = 8.dp
    val S_MD = 14.dp
    val S_LG = 20.dp
    val S_XL = 28.dp

    // الأيقونات
    val I_XS = 14.dp
    val I_SM = 18.dp
    val I_MD = 22.dp
    val I_LG = 28.dp
    val I_XL = 44.dp

    // الحدود
    val B_HAIRLINE = 1.dp
    val B_STRONG = 2.dp

    // الحلقات
    val RING_STROKE = 7.dp
    val RING_TRACK_STROKE = 5.dp
}

// ═══════════════════════════════════════════════
// 2. لوحة الألوان الهندسية (مرتبطة بالثيم)
// ═══════════════════════════════════════════════
object GeoColor {
    /** خلفيات الأسطح */
    fun surface(): Color = CardSurface
    fun surfaceRaised(): Color = CardSurface.copy(alpha = 0.94f)
    fun surfaceSunken(): Color = Color(0xFF0B1120)
    fun screen(): Color = Color(0xFF0A0F1C)

    /** الحدود */
    fun hairline(): Color = GoldPrimary.copy(alpha = 0.16f)
    fun outline(): Color = GoldPrimary.copy(alpha = 0.32f)
    fun outlineStrong(): Color = GoldPrimary.copy(alpha = 0.55f)

    /** الحالات */
    fun selectedBg(): Color = GoldPrimary.copy(alpha = 0.14f)
    fun pressed(): Color = GoldPrimary.copy(alpha = 0.10f)

    /** النصوص */
    fun textPrimary(): Color = TextPrimary
    fun textSecondary(): Color = TextSecondary
    fun textMuted(): Color = TextSecondary.copy(alpha = 0.62f)
    fun onGold(): Color = StaticDeepSlate

    /** ألوان الأقسام — ثابتة حتى لا تتشتت الهوية */
    val Prayer = Color(0xFF38BDF8)
    val Quran = Color(0xFF10B981)
    val Dhikr = Color(0xFFF59E0B)
    val Hadith = Color(0xFFA78BFA)
    val Dua = Color(0xFFF472B6)
    val Learn = Color(0xFF818CF8)
    val Rawatib = Color(0xFF2DD4BF)
    val Warning = Color(0xFFF87171)
    val Reward = Color(0xFFFBBF24)
    val Qibla = Color(0xFFA78BFA)

    /** تدرجات هندسية */
    val heroGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF1B2740), Color(0xFF0D1526))
    )
    val goldGradient = Brush.horizontalGradient(
        colors = listOf(GoldPrimary, GoldSecondary)
    )
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

/**
 * بطاقة هندسية موحّدة — نصف قطر واحد، حدود شعرية، خلفية مترقية.
 */
@Composable
fun GeoCard(
    modifier: Modifier = Modifier,
    accent: Color = GoldPrimary,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable (Modifier) -> Unit
) {
    val bg = if (selected) GeoColor.selectedBg() else GeoColor.surfaceRaised()
    val stroke = if (selected) GeoColor.outlineStrong() else GeoColor.hairline()
    val shape = GeoShape.Card

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .clip(shape),
        shape = shape,
        color = bg,
        border = BorderStroke(Geo.B_HAIRLINE, stroke),
        content = { content(Modifier.padding(Geo.S_MD)) }
    )
}

/**
 * حاوية أيقونة هندسية: دائرة + إطار hairline + خلفية شفافة.
 */
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
                if (filled) accent.copy(alpha = 0.18f) else accent.copy(alpha = 0.10f),
                GeoShape.Circle
            )
            .border(Geo.B_HAIRLINE, accent.copy(alpha = 0.34f), GeoShape.Circle),
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

/**
 * رأس قسم هندسي — مؤشر عمودي + عنوان + إجراء اختياري على اليسار.
 */
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
        // مؤشر هندسي
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 20.dp)
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
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.let {
                Text(
                    text = it,
                    color = GeoColor.textMuted(),
                    fontFamily = NotoSansFont,
                    fontSize = 10.5.sp,
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

/**
 * شريحة هندسية (Chip) — للوسوم والفلاتر.
 */
@Composable
fun GeoChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    accent: Color = GoldPrimary,
    onClick: (() -> Unit)? = null
) {
    val bg = if (selected) accent.copy(alpha = 0.16f) else GeoColor.surfaceRaised()
    val border = if (selected) accent.copy(alpha = 0.5f) else GeoColor.hairline()
    val fg = if (selected) accent else GeoColor.textSecondary()

    Surface(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        shape = GeoShape.Chip,
        color = bg,
        border = BorderStroke(Geo.B_HAIRLINE, border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Geo.S_MD, vertical = 7.dp),
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
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

/**
 * زر هندسي موحّد.
 */
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
        GeoButtonStyle.Outline -> Color.Transparent
        GeoButtonStyle.Ghost -> Color.Transparent
    }
    val fg = when (variant) {
        GeoButtonStyle.Filled -> GeoColor.onGold()
        else -> accent
    }

    Surface(
        modifier = modifier
            .clip(GeoShape.Button)
            .then(
                if (enabled) Modifier.clickable(onClick = onClick) else Modifier
            ),
        shape = GeoShape.Button,
        color = if (enabled) bg else bg.copy(alpha = 0.35f),
        border = if (variant == GeoButtonStyle.Outline)
            BorderStroke(Geo.B_HAIRLINE, if (enabled) accent else accent.copy(alpha = 0.3f))
        else BorderStroke(0.dp, Color.Transparent)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Geo.S_LG, vertical = 11.dp),
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
                fontSize = 13.sp,
                maxLines = 1
            )
        }
    }
}

enum class GeoButtonStyle { Filled, Outline, Ghost }

/**
 * حلقة تقدم هندسية نظيفة — بدون مؤثرات متحركة ثقيلة.
 */
@Composable
fun GeoProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    strokeWidth: Dp = Geo.RING_STROKE,
    trackColor: Color = Color.White.copy(alpha = 0.08f),
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

/**
 * صف إحصائي مضغوط (رقم + تسمية) — يُستخدم داخل شريط المؤشرات.
 */
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
            fontSize = 9.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * فاصل هندسي.
 */
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
            .height(1.dp)
            .background(color)
    )
}
