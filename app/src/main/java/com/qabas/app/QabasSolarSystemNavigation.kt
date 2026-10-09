package com.qabas.app

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.qabas.app.ui.theme.AiCyan
import com.qabas.app.ui.theme.AiNeonGreen
import com.qabas.app.ui.theme.AiTeal
import com.qabas.app.ui.theme.AiViolet
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.CardSurface
import com.qabas.app.ui.theme.DeepSlate
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.GoldSecondary
import com.qabas.app.ui.theme.TextPrimary
import com.qabas.app.ui.theme.TextSecondary
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val FULL_REVOLUTION_DEGREES = 360f
private const val SEGMENTS_COUNT = 5
private const val SEGMENT_SPACING_DEGREES = FULL_REVOLUTION_DEGREES / SEGMENTS_COUNT
private const val THRONE_DEGREES = 270f
private const val DRIFT_SPEED_DEG_PER_SEC = 8f

private data class SolarNavItem(
    val route: AppState,
    val icon: ImageVector,
    val label: String,
    val color: Color
)

@Composable
fun QabasSolarSystemNavigation(
    currentRoute: AppState,
    onNavigate: (AppState) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val navItems = remember {
        listOf(
            SolarNavItem(AppState.HOME, Icons.Default.Home, "الرئيسية", GoldPrimary),
            SolarNavItem(AppState.HADITH_STUDIO, Icons.Default.FormatQuote, "بطاقة حديث", AiTeal),
            SolarNavItem(AppState.HASANAT_HUB, Icons.Filled.AutoAwesome, "طريق الحسنات", AiNeonGreen),
            SolarNavItem(AppState.PROJECTS, Icons.Default.Folder, "مشاريعي", AiViolet),
            SolarNavItem(AppState.SETTINGS, Icons.Default.Settings, "الإعدادات", AiCyan)
        )
    }

    val initialRouteIndex = remember(currentRoute) {
        navItems.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    }
    val initialShellDegrees = remember {
        (((THRONE_DEGREES - initialRouteIndex * SEGMENT_SPACING_DEGREES) % FULL_REVOLUTION_DEGREES) + FULL_REVOLUTION_DEGREES) % FULL_REVOLUTION_DEGREES
    }

    var pendingRoute by remember { mutableStateOf<AppState?>(null) }

    val animatorScale = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE) }
            .getOrDefault(1f)
    }
    val motionAvailable = animatorScale > 0f

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var lifecycleActive by remember { mutableStateOf(true) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            lifecycleActive = when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> false
                else -> true
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val frozen = !lifecycleActive || pendingRoute != null
    val running = motionAvailable && !frozen

    var shellDegrees by remember { mutableFloatStateOf(initialShellDegrees) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var previousNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (previousNanos != 0L) {
                    val dtSeconds = (now - previousNanos) / 1_000_000_000.0f
                    shellDegrees = (shellDegrees + dtSeconds * DRIFT_SPEED_DEG_PER_SEC * animatorScale) % FULL_REVOLUTION_DEGREES
                }
                previousNanos = now
            }
        }
    }

    if (pendingRoute != null) {
        val target = navItems.firstOrNull { it.route == pendingRoute }
        AlertDialog(
            onDismissRequest = { pendingRoute = null },
            containerColor = CardSurface,
            title = {
                Text(
                    text = Translator.tr("الدخول إلى ${target?.label.orEmpty()}؟"),
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = Translator.tr("سيتم الانتقال إلى قسم «${target?.label.orEmpty()}»."),
                    fontFamily = CairoFont,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    val routeToGo = pendingRoute ?: return@TextButton
                    pendingRoute = null
                    onNavigate(routeToGo)
                }) {
                    Text(
                        text = Translator.tr("تأكيد ✓"),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary,
                        fontSize = 14.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRoute = null }) {
                    Text(
                        text = Translator.tr("إلغاء"),
                        fontFamily = CairoFont,
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                ambientColor = GoldPrimary.copy(alpha = 0.10f),
                spotColor = GoldPrimary.copy(alpha = 0.15f)
            )
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(0xFF1E293B).copy(alpha = 0.4f),
                        GoldPrimary.copy(alpha = 0.5f),
                        GoldSecondary.copy(alpha = 0.3f),
                        Color(0xFF1E293B).copy(alpha = 0.4f)
                    )
                ),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ),
        color = Color(0xFF0B1120)
    ) {
        BoxWithConstraints(modifier = Modifier
            .fillMaxSize()
            .height(108.dp)) {
            val density = LocalDensity.current
            val centerX = with(density) { maxWidth.toPx() / 2f }
            val centerY = with(density) { maxHeight.toPx() / 2f }
            val orbitRadius = with(density) { 34.dp.toPx() }

            val sunGlow = rememberSolarGlow()

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = GoldPrimary.copy(alpha = 0.08f),
                    radius = orbitRadius,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 1.dp.toPx())
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, GoldSecondary, GoldPrimary, GoldPrimary.copy(alpha = 0f)),
                        center = Offset(centerX, centerY),
                        radius = 30.dp.toPx()
                    ),
                    radius = 30.dp.toPx() * sunGlow,
                    center = Offset(centerX, centerY)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(GoldSecondary, GoldPrimary, Color.Transparent),
                        center = Offset(centerX, centerY),
                        radius = 14.dp.toPx()
                    ),
                    radius = 14.dp.toPx(),
                    center = Offset(centerX, centerY)
                )
                drawCircle(
                    color = GoldPrimary,
                    radius = 8.dp.toPx(),
                    center = Offset(centerX, centerY)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.9f),
                    radius = 3.5.dp.toPx(),
                    center = Offset(centerX, centerY)
                )
            }

            navItems.forEachIndexed { index, item ->
                val isSelected = currentRoute == item.route
                val label = Translator.tr(item.label)
                val planetModifier = Modifier
                    .width(62.dp)
                    .graphicsLayer {
                        val angleDegrees = index * SEGMENT_SPACING_DEGREES + shellDegrees
                        val angleRadians = (angleDegrees * (Math.PI / 180.0)).toFloat()
                        translationX = centerX + orbitRadius * cos(angleRadians) - size.width / 2f
                        translationY = centerY + orbitRadius * sin(angleRadians) - size.height / 2f

                        val distanceFromThrone = angularDistance(angleDegrees, THRONE_DEGREES)
                        val emphasis = (cos((distanceFromThrone * (Math.PI / 180.0)).toFloat()) + 1f) / 2f
                        val scale = 0.82f + (1.24f - 0.82f) * emphasis
                        scaleX = scale
                        scaleY = scale
                        alpha = 0.55f + (1f - 0.55f) * emphasis
                    }
                    .pointerInput(item.route) {
                        detectTapGestures(
                            onTap = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                pendingRoute = item.route
                            }
                        )
                    }

                Column(
                    modifier = planetModifier,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        item.color,
                                        item.color.copy(alpha = 0.55f),
                                        Color(0xFF1E293B)
                                    )
                                ),
                                shape = CircleShape
                            )
                            .border(
                                width = if (isSelected) 1.6.dp else 1.dp,
                                color = if (isSelected) GoldPrimary else item.color.copy(alpha = 0.35f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = label,
                            tint = DeepSlate,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = label,
                        color = if (isSelected) GoldPrimary else TextSecondary,
                        fontFamily = CairoFont,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 8.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberSolarGlow(): Float {
    val transition = rememberInfiniteTransition(label = "qabas_sun_glow")
    return transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "qabas_sun_glow_value"
    ).value
}

private fun angularDistance(a: Float, b: Float): Float {
    val delta = abs(a - b) % 360f
    return if (delta > 180f) 360f - delta else delta
}