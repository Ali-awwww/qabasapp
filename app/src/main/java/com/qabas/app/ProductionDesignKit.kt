package com.qabas.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*

private val ProdCard: Color get() = CardSurface
private val ProdHair: Color get() = PresetPrimary.copy(alpha = 0.15f)

@Composable
fun ProdLabel(text: String) {
    Text(text, color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun ProdChipRow(options: List<String>, selected: String, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { opt ->
                    val sel = selected == opt || selected.contains(opt.take(6))
                    Box(
                        modifier = Modifier.weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) PresetPrimary.copy(alpha = 0.18f) else ProdCard.copy(alpha = 0.7f))
                            .border(1.dp, if (sel) PresetPrimary else ProdHair, RoundedCornerShape(10.dp))
                            .clickable { onPick(opt) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(opt, color = if (sel) PresetPrimary else TextSecondary,
                            fontFamily = CairoFont, fontSize = 13.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun ProdPrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    val breathing = rememberBreathingScale()
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp).scale(breathing)
            // ق53: الزر المعطّل يُبهَت — كان نص «اكتب الفكرة أولاً» زراً
            // قابلاً للنقر بلا أي أثر، فيخالف §3.
            .alpha(if (enabled) 1f else 0.45f),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Brush.horizontalGradient(listOf(PresetAccent, PresetPrimary))),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
    }
}

/**
 * لوحة زجاجية سيبرانية تفاعلية (Spatial HUD Glass Panel)
 */
@Composable
fun SpatialHudCard(
    modifier: Modifier = Modifier,
    borderColor: Color = PresetPrimary.copy(alpha = 0.4f),
    containerColor: Color = CardSurface.copy(alpha = 0.88f),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        borderColor,
                        borderColor.copy(alpha = 0.15f),
                        borderColor
                    )
                ),
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        content()
    }
}

// ── لغة التنفس: نبض هادئ + دخول متدرج، تحترم إعدادات تقليل الحركة ──

@Composable
fun rememberBreathingScale(): Float {
    val context = LocalContext.current
    val motionScale = remember {
        try { android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
        catch (_: Exception) { 1f }
    }
    if (motionScale == 0f) return 1f
    val t = rememberInfiniteTransition(label = "breathe")
    val s by t.animateFloat(1f, 1.04f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    return s
}

@Composable
fun BreatheIn(index: Int = 0, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val motionScale = remember {
        try { android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
        catch (_: Exception) { 1f }
    }
    if (motionScale == 0f) { content(); return }
    val t = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index * 40).toLong().coerceAtMost(400))
        t.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
    }
    Box(Modifier.scale(0.96f + 0.04f * t.value)) { content() }
}
