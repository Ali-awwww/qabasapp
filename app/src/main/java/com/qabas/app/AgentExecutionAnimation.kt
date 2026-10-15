package com.qabas.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.RobotoMonoFont
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * ⚙️ أنميشن تنفيذ المهام (Mission Execution HUD) — رسم هندسي نيوني حي.
 * يعكس التقدم الحقيقي للمهمة (progress) وحالتها الفعلية (phaseLabel) فقط،
 * عبر حلقات مدارية دوّارة ومسح راداري ونواة نابضة. لا أرقام وهمية.
 * يحترم إعدادات تقليل الحركة في النظام.
 */
@Composable
fun AgentExecutionAnimation(
    progress: Float,
    accent: Color,
    phaseLabel: String,
    modifier: Modifier = Modifier,
    active: Boolean = true
) {
    val context = LocalContext.current
    val motionOn = remember {
        val scale = try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        } catch (_: Exception) { 1f }
        scale != 0f
    }

    // 🔑 لا نُشغّل محرك الأنيميشن إطلاقاً عند تقليل الحركة — لا مجرد تجميد القيم
    val motion = if (motionOn) {
        val t = rememberInfiniteTransition(label = "mission-exec")
        listOf(
            t.animateFloat(
                0f, 360f,
                infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "outer-rot"
            ),
            t.animateFloat(
                360f, 0f,
                infiniteRepeatable(tween(11000, easing = LinearEasing)), label = "inner-rot"
            ),
            t.animateFloat(
                0.35f, 1f,
                infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "core-pulse"
            ),
            t.animateFloat(
                0f, 360f,
                infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "radar-sweep"
            ),
            t.animateFloat(
                0f, 1f,
                infiniteRepeatable(tween(2800, easing = LinearEasing)), label = "scan-line"
            )
        )
    } else null

    val rot1 = motion?.get(0)?.value ?: 0f
    val rot2 = motion?.get(1)?.value ?: 0f
    val pulseV = motion?.get(2)?.value ?: 0.8f
    val sweepV = motion?.get(3)?.value ?: 45f
    val scanV = motion?.get(4)?.value ?: 0.5f
    val clamped = progress.coerceIn(0f, 1f)
    val percent = (clamped * 100f).toInt()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF05070D))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        HudCornerDecor(modifier = Modifier.matchParentSize(), color = accent.copy(alpha = 0.7f))

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxR = min(cx, cy) - 18.dp.toPx()
            val center = Offset(cx, cy)

            // حلقات ثابتة خافتة
            drawCircle(accent.copy(alpha = 0.10f), maxR, center, style = Stroke(1.dp.toPx()))
            drawCircle(accent.copy(alpha = 0.07f), maxR * 0.72f, center, style = Stroke(1.dp.toPx()))
            drawCircle(accent.copy(alpha = 0.05f), maxR * 0.48f, center, style = Stroke(1.dp.toPx()))

            // حلقات متقطعة دوّارة (مداران متعاكسان)
            drawCircle(
                color = accent.copy(alpha = 0.55f),
                radius = maxR,
                center = center,
                style = Stroke(
                    width = 1.6.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), rot1)
                )
            )
            drawCircle(
                color = accent.copy(alpha = 0.35f),
                radius = maxR * 0.72f,
                center = center,
                style = Stroke(
                    width = 1.4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 14f), rot2)
                )
            )

            // قوس التقدم الحقيقي
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * clamped,
                useCenter = false,
                topLeft = Offset(cx - maxR, cy - maxR),
                size = Size(maxR * 2f, maxR * 2f),
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )

            // مسح راداري (شعاع دوّار)
            val sa = Math.toRadians(sweepV.toDouble())
            drawLine(
                color = accent.copy(alpha = 0.55f),
                start = center,
                end = Offset(cx + maxR * cos(sa).toFloat(), cy + maxR * sin(sa).toFloat()),
                strokeWidth = 2.dp.toPx()
            )
            drawArc(
                color = accent.copy(alpha = 0.10f),
                startAngle = sweepV - 42f,
                sweepAngle = 42f,
                useCenter = true,
                topLeft = Offset(cx - maxR, cy - maxR),
                size = Size(maxR * 2f, maxR * 2f)
            )

            // عقد مدارية على الحلقة الخارجية
            val orbitR = maxR
            listOf(0f, 90f, 180f, 270f).forEachIndexed { i, base ->
                val a = Math.toRadians((rot1 + base).toDouble())
                val ox = cx + orbitR * cos(a).toFloat()
                val oy = cy + orbitR * sin(a).toFloat()
                val node = if (i % 2 == 0) accent else accent.copy(alpha = 0.7f)
                drawCircle(node.copy(alpha = 0.28f * pulseV), 10.dp.toPx(), Offset(ox, oy))
                drawCircle(node, 3.5.dp.toPx(), Offset(ox, oy))
            }

            // خط مسح أفقي متحرك
            val sy = cy - maxR + (2f * maxR) * scanV
            drawLine(
                color = accent.copy(alpha = 0.22f),
                start = Offset(cx - maxR, sy),
                end = Offset(cx + maxR, sy),
                strokeWidth = 1.4.dp.toPx()
            )

            // علامات HUD على الحلقة + خطوط التقاطع
            drawLine(accent.copy(alpha = 0.25f), Offset(cx - maxR, cy), Offset(cx - maxR * 0.82f, cy), 1.2.dp.toPx())
            drawLine(accent.copy(alpha = 0.25f), Offset(cx + maxR, cy), Offset(cx + maxR * 0.82f, cy), 1.2.dp.toPx())
            drawLine(accent.copy(alpha = 0.25f), Offset(cx, cy - maxR), Offset(cx, cy - maxR * 0.82f), 1.2.dp.toPx())
            drawLine(accent.copy(alpha = 0.25f), Offset(cx, cy + maxR), Offset(cx, cy + maxR * 0.82f), 1.2.dp.toPx())

            // النواة النابضة
            drawCircle(accent.copy(alpha = 0.20f * pulseV), 22.dp.toPx(), center)
            drawCircle(accent.copy(alpha = 0.85f), (6.dp.toPx()) * (0.75f + 0.5f * pulseV), center)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$percent%",
                color = accent,
                fontFamily = RobotoMonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (active) phaseLabel else "جاهز للتنفيذ",
                color = Color(0xFF94A3B8),
                fontFamily = CairoFont,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
