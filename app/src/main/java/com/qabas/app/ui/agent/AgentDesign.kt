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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.CardSurface
import com.qabas.app.ui.theme.PresetPrimary
import com.qabas.app.ui.theme.PresetAccent
import com.qabas.app.ui.theme.TextPrimary as ThemeTextPrimary
import com.qabas.app.ui.theme.TextSecondary as ThemeTextSecondary
import com.qabas.app.ui.theme.ThemeDestructive
import com.qabas.app.ui.theme.ThemeSuccess
import com.qabas.app.ui.theme.ThemeWarning

/**
 * AgentDesign — نظام التصميم الهندسي النيوني الموحد لقسم الوكيل الذكي.
 * مصدر وحيد للألوان والمسافات والخطوط والمكوّنات المشتركة،
 * يحفظ هوية Cybernetic Glassmorphism لكن منضبطة الهندسة.
 */
object AgentDesign {
    // ▬▬▬▬▬ الأسطح ▬▬▬▬▬
    val SurfaceBase: Color get() = CardSurface

    // ▬▬▬▬▬ النيون الدلالي ▬▬▬▬▬
    val Primary: Color get() = PresetPrimary       // سماوي — عمل وجاري
    val Secondary: Color get() = PresetAccent   // بنفسجي — أدوات وهوية
    val Success: Color get() = ThemeSuccess      // أخضر — نجاح وجاهز
    val Warning: Color get() = ThemeWarning      // كهرماني — تنبيه ويحتاج إجراء
    val Danger: Color get() = ThemeDestructive   // أحمر — فشل
    val Info: Color get() = PresetPrimary          // أزرق/سماوي — معلومات

    // ▬▬▬▬▬ النصوص ▬▬▬▬▬
    val TextPrimary: Color get() = ThemeTextPrimary
    val TextSecondary: Color get() = ThemeTextSecondary

    // ▬▬▬▬▬ المسافات (شبكة 4) ▬▬▬▬▬
    val SpaceXs = 4.dp
    val SpaceSm = 8.dp
    val SpaceMd = 12.dp
    val SpaceLg = 16.dp

    // ▬▬▬▬▬ الحواف ▬▬▬▬▬
    val RadiusSm = 10.dp
    val RadiusMd = 14.dp

    // ▬▬▬▬▬ الخطوط ▬▬▬▬▬
    val FontXs = 12.sp   // الحد الأدنى — وصولية
    val FontSm = 14.sp
    val FontMd = 16.sp

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

/** بطاقة عرض التغييرات البرمجية (Diff) بتصميم منفصل وقابل للطي بنقرة واحدة. */
@Composable
fun AgentDiffCard(
    filePath: String,
    additions: Int,
    deletions: Int,
    diffText: String,
    modifier: Modifier = Modifier
) {
    var isExpanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, AgentDesign.Primary.copy(alpha = 0.35f), RoundedCornerShape(AgentDesign.RadiusSm)),
        shape = RoundedCornerShape(AgentDesign.RadiusSm),
        color = Color(0xFF060912)
    ) {
        Column(Modifier.padding(AgentDesign.SpaceMd)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Code,
                        contentDescription = null,
                        tint = AgentDesign.Primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(AgentDesign.SpaceSm))
                    Text(
                        text = filePath,
                        color = AgentDesign.TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (additions > 0) {
                        Surface(
                            color = AgentDesign.Success.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.8.dp, AgentDesign.Success.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "+$additions",
                                color = AgentDesign.Success,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    if (deletions > 0) {
                        Surface(
                            color = AgentDesign.Danger.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.8.dp, AgentDesign.Danger.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "-$deletions",
                                color = AgentDesign.Danger,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "طي" else "توسيع",
                        tint = AgentDesign.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isExpanded && diffText.isNotBlank(),
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
            ) {
                Column {
                    Spacer(Modifier.height(AgentDesign.SpaceSm))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .border(0.8.dp, Color(0xFF1E2D4A), RoundedCornerShape(6.dp))
                            .padding(AgentDesign.SpaceSm)
                    ) {
                        Text(
                            text = diffText.take(2000),
                            color = Color(0xFF93C5FD),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 15.sp
                        )
                    }
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
        AgentStatusKind.READY -> AgentDesign.Success to "متصل"
        AgentStatusKind.NEEDS_ACTION -> AgentDesign.Warning to "إجراء"
        AgentStatusKind.RUNNING -> AgentDesign.Primary to "نشط"
        AgentStatusKind.FAILED -> AgentDesign.Danger to "عطل"
    }
    
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        color = dot.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, dot.copy(alpha = 0.25f))
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dot)
                    .then(if (kind == AgentStatusKind.RUNNING) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.6f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scale"
                        )
                        Modifier.graphicsLayer(scaleX = scale, scaleY = scale, alpha = 1f / scale)
                    } else Modifier)
            )
            
            Text(
                text = text,
                color = AgentDesign.TextPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                maxLines = 1
            )
            
            if (onClick != null) {
                VerticalDivider(modifier = Modifier.height(10.dp), thickness = 1.dp, color = dot.copy(alpha = 0.3f))
                Text(
                    label,
                    color = dot,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.sp
                )
            }
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
