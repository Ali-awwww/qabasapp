package com.qabas.app.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import com.qabas.app.ui.theme.CairoFont

/**
 * AgentDesign — نظام التصميم الهندسي النيوني الموحد لقسم الوكيل الذكي.
 * مصدر وحيد للألوان والمسافات والخطوط والمكوّنات المشتركة،
 * يحفظ هوية Cybernetic Glassmorphism لكن منضبطة الهندسة.
 */
object AgentDesign {
    // ▬▬▬▬▬ الأسطح ▬▬▬▬▬
    val Backdrop = Color(0xFF05070F)
    val BackdropAlt = Color(0xFF070B16)
    val SurfaceBase = Color(0xFF0B1220)
    val SurfaceRaised = Color(0xFF0F1626)
    val Hairline = Color(0xFF1E2D4A)

    // ▬▬▬▬▬ النيون الدلالي ▬▬▬▬▬
    val Primary = Color(0xFF22D3EE)       // سماوي — عمل وجاري
    val Secondary = Color(0xFF8B5CF6)     // بنفسجي — أدوات وهوية
    val Success = Color(0xFF10B981)       // أخضر — نجاح وجاهز
    val Warning = Color(0xFFF59E0B)       // كهرماني — تنبيه ويحتاج إجراء
    val Danger = Color(0xFFF87171)        // أحمر — فشل
    val Info = Color(0xFF3B82F6)          // أزرق — معلومات

    // ▬▬▬▬▬ النصوص ▬▬▬▬▬
    val TextPrimary = Color(0xFFE6EDF7)
    val TextSecondary = Color(0xFF8A94A6)
    val TextTertiary = Color(0xFF64748B)

    // ▬▬▬▬▬ المسافات (شبكة 4) ▬▬▬▬▬
    val SpaceXs = 4.dp
    val SpaceSm = 8.dp
    val SpaceMd = 12.dp
    val SpaceLg = 16.dp
    val SpaceXl = 24.dp
    val SpaceXxl = 32.dp

    // ▬▬▬▬▬ الحواف ▬▬▬▬▬
    val RadiusSm = 10.dp
    val RadiusMd = 14.dp
    val RadiusLg = 18.dp
    val RadiusXl = 24.dp

    // ▬▬▬▬▬ الخطوط ▬▬▬▬▬
    val FontXs = 12.sp   // الحد الأدنى — وصولية
    val FontSm = 14.sp
    val FontMd = 16.sp
    val FontLg = 20.sp
    val FontXl = 28.sp

    /** حجم لمسي 48dp إلزامي لأي عنصر تفاعلي. */
    val TouchTarget = 48.dp
}

/**
 * بطاقة الوكيل الزجاجية الهندسية — السطح القياسي لكل محتوى القسم.
 */
@Composable
fun AgentNeonCard(
    modifier: Modifier = Modifier,
    accent: Color = AgentDesign.Primary,
    shape: RoundedCornerShape = RoundedCornerShape(AgentDesign.RadiusMd),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, accent.copy(alpha = 0.28f), shape),
        shape = shape,
        color = AgentDesign.SurfaceBase.copy(alpha = 0.92f)
    ) {
        Column(Modifier.padding(AgentDesign.SpaceLg)) { content() }
    }
}

/** بطاقة عرض التغييرات البرمجية (Diff) بتصميم منفصل ونظيف. */
@Composable
fun AgentDiffCard(
    filePath: String,
    additions: Int,
    deletions: Int,
    diffText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, AgentDesign.Primary.copy(alpha = 0.35f), RoundedCornerShape(AgentDesign.RadiusSm)),
        shape = RoundedCornerShape(AgentDesign.RadiusSm),
        color = Color(0xFF060912)
    ) {
        Column(Modifier.padding(AgentDesign.SpaceMd)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Code,
                        contentDescription = null,
                        tint = AgentDesign.Primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(AgentDesign.SpaceSm))
                    Text(
                        text = filePath,
                        color = AgentDesign.TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Row {
                    if (additions > 0) {
                        Text(
                            text = "+$additions",
                            color = AgentDesign.Success,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(AgentDesign.SpaceSm))
                    }
                    if (deletions > 0) {
                        Text(
                            text = "-$deletions",
                            color = AgentDesign.Danger,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            if (diffText.isNotBlank()) {
                Spacer(Modifier.height(AgentDesign.SpaceSm))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .padding(AgentDesign.SpaceSm)
                ) {
                    Text(
                        text = diffText.take(500),
                        color = AgentDesign.TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

/** رأس قسم: مؤشر رقمي هندسي [01] + عنوان + خط رفيع متوهج. */
@Composable
fun AgentSectionHeader(
    index: String,
    title: String,
    accent: Color = AgentDesign.Primary,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "[$index]",
                color = accent,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = AgentDesign.FontXs
            )
            Spacer(Modifier.width(AgentDesign.SpaceSm))
            Text(
                title,
                color = AgentDesign.TextPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = AgentDesign.FontMd
            )
        }
        Spacer(Modifier.height(AgentDesign.SpaceXs))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.6f), accent.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )
    }
}

/** حالة الوكيل — شريحة واحدة تختصر كل شيء: جاهز/يحتاج/يعمل/فشل. */
enum class AgentStatusKind { READY, NEEDS_ACTION, RUNNING, FAILED }

@Composable
fun AgentStatusChip(
    kind: AgentStatusKind,
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val (dot, label) = when (kind) {
        AgentStatusKind.READY -> AgentDesign.Success to "جاهز"
        AgentStatusKind.NEEDS_ACTION -> AgentDesign.Warning to "يلزم إجراء"
        AgentStatusKind.RUNNING -> AgentDesign.Primary to "يعمل الآن"
        AgentStatusKind.FAILED -> AgentDesign.Danger to "توقف"
    }
    Surface(
        modifier = modifier
            .widthIn(max = 230.dp)
            .defaultMinSize(minHeight = AgentDesign.TouchTarget)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(AgentDesign.RadiusSm),
        color = dot.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, dot.copy(alpha = 0.45f))
    ) {
        Row(
            Modifier.padding(horizontal = AgentDesign.SpaceMd, vertical = AgentDesign.SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dot)
            )
            Spacer(Modifier.width(AgentDesign.SpaceXs))
            Text(
                text,
                color = AgentDesign.TextPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = AgentDesign.FontXs,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            if (onClick != null) {
                Spacer(Modifier.width(AgentDesign.SpaceXs))
                Text(
                    label,
                    color = dot,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = AgentDesign.FontXs
                )
            }
        }
    }
}

/** حالة فارغة ودّية بأسلوب هندسي — بشرح وزر اختياري. */
@Composable
fun AgentEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(AgentDesign.SpaceXl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(AgentDesign.Primary.copy(alpha = 0.08f))
                .border(1.dp, AgentDesign.Primary.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = AgentDesign.Primary, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(AgentDesign.SpaceMd))
        Text(title, color = AgentDesign.TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = AgentDesign.FontMd)
        Spacer(Modifier.height(AgentDesign.SpaceXs))
        Text(
            description,
            color = AgentDesign.TextSecondary,
            fontFamily = CairoFont,
            fontSize = AgentDesign.FontXs,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(AgentDesign.SpaceMd))
            action()
        }
    }
}

/** زر التوحيد الرئيسي — لمسة نيون على الغلاف الداكن. */
@Composable
fun AgentNeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = AgentDesign.Primary,
    icon: ImageVector? = Icons.Default.Bolt
) {
    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = AgentDesign.TouchTarget)
            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(AgentDesign.RadiusSm)),
        shape = RoundedCornerShape(AgentDesign.RadiusSm),
        color = accent.copy(alpha = 0.14f)
    ) {
        Row(
            Modifier.padding(horizontal = AgentDesign.SpaceLg, vertical = AgentDesign.SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(AgentDesign.SpaceXs))
            }
            Text(
                text,
                color = accent,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = AgentDesign.FontSm
            )
        }
    }
}

/** رسالة خطأ قابلة للقراءة مع سبب فعلي وإجراء إصلاح. */
@Composable
fun AgentErrorCard(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    AgentNeonCard(modifier = modifier, accent = AgentDesign.Danger) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AgentDesign.Danger, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(AgentDesign.SpaceSm))
            Text(title, color = AgentDesign.TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = AgentDesign.FontSm)
        }
        Spacer(Modifier.height(AgentDesign.SpaceXs))
        Text(detail, color = AgentDesign.TextSecondary, fontFamily = CairoFont, fontSize = AgentDesign.FontXs)
        if (action != null) {
            Spacer(Modifier.height(AgentDesign.SpaceSm))
            action()
        }
    }
}

/** صف حالة جاهزية: أيقونة + عنوان + شرح + إجراء (48dp كحد أدنى). */
@Composable
fun AgentCheckRow(
    icon: ImageVector,
    title: String,
    description: String,
    isReady: Boolean,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    val statusColor = if (isReady) AgentDesign.Success else AgentDesign.Warning
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AgentDesign.TouchTarget)
            .padding(vertical = AgentDesign.SpaceSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isReady) Icons.Default.CheckCircle else Icons.Default.HourglassEmpty,
            contentDescription = if (isReady) "جاهز" else "غير جاهز",
            tint = statusColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(AgentDesign.SpaceSm))
        Column(Modifier.weight(1f)) {
            Text(title, color = AgentDesign.TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.SemiBold, fontSize = AgentDesign.FontSm)
            Text(description, color = AgentDesign.TextSecondary, fontFamily = CairoFont, fontSize = AgentDesign.FontXs)
        }
        if (trailing != null) trailing()
    }
}
