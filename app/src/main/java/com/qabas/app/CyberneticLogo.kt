package com.qabas.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qabas.app.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * 🌌 أنميشن لوجو قبس الفضائي الخارق المطور (Cybernetic Spatial HUD Quantum Logo)
 *
 * يتكون نظام الحركة الهندسية الفاخرة للوجو من 9 طبقات تفاعلية متزامنة فائقة السلاسة:
 * 1. Zero-G Levitation (الطفو الفضائي بالجاذبية الصفرية): ارتقاء ناعم يعطي وزناً هولوغرافياً حقيقياً.
 * 2. Cosmic Breathing Aura (الهالة الكونية المتنفسة): نبض إشعاعي ثلاثي الطبقات متناغم مع نظام الألوان النشط.
 * 3. Magnetic Pulse Shockwave (موجات النبض المغناطيسي المتسعة): أمواج طاقة كروية تتمدد وتتلاشى بنعومة.
 * 4. Quantum Spores & Particle Dust (جزيئات النور والغبار الكوانتي): 8 جسيمات ضوئية تسبح بحرية مدارية حول المركز.
 * 5. Dual Orbital Quantum Rings (حلقات المدار الكوانتي المزدوجة): مسارات متقطعة تدور باتجاهات متعاكسة مع عقد طاقية.
 * 6. Dual Energy Satellite Nodes (عقدتان طاقيتان دوارتان بنوى بيضاء مشعة).
 * 7. Spatial HUD Corner Brackets (أقواس الليزر الزاوية): زوايا تحديد استهداف سيبرانية دقيقة.
 * 8. Prismatic Light Sweep (مسح الشيمر الزجاجي الكريستالي): موجة بريق ضوئي بزاوية 45 درجة بانكسار متعدد الألوان.
 * 9. Reactive Holographic Glass Frame (إطار الزجاج المتفاعل): إطار متدرج يعكس هوية التصميم الحالية.
 */
@Composable
fun CyberneticAnimatedLogo(
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    showOrbitalHud: Boolean = true,
    showCosmicGlow: Boolean = true,
    showLightSweep: Boolean = true,
    showLevitation: Boolean = true,
    showCornerBrackets: Boolean = true,
    showParticles: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "CyberLogoMasterLoop")

    val primaryColor = qabasPrimary()
    val accentColor = qabasAccent()
    val isCyberHud = QabasTheme.current.isCyberHud

    // 1. حركة الطفو العمودي (Zero-G Levitation)
    val levitationOffset by if (showLevitation) {
        infiniteTransition.animateFloat(
            initialValue = -6f,
            targetValue = 6f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2800, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "levitationOffset"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    // 2. نبض الهالة الكونية (Cosmic Glow Pulse)
    val glowScale by if (showCosmicGlow) {
        infiniteTransition.animateFloat(
            initialValue = 0.88f,
            targetValue = 1.20f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowScale"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    val glowAlpha by if (showCosmicGlow) {
        infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.92f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowAlpha"
        )
    } else {
        remember { mutableFloatStateOf(0.5f) }
    }

    // 3. موجة الصدمة الكهرومغناطيسية المتوسعة (Magnetic Shockwave)
    val shockwaveProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shockwaveProgress"
    )

    // 4. دوران المدارات الهيدروديناميكية الفضائية (Orbital HUD Rotation)
    val outerOrbitAngle by if (showOrbitalHud) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 8000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "outerOrbitAngle"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val innerOrbitAngle by if (showOrbitalHud) {
        infiniteTransition.animateFloat(
            initialValue = 360f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 11500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "innerOrbitAngle"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    // 5. مسح الضوء الزجاجي الماسي (Prismatic Shimmer Sweep)
    val shimmerOffset by if (showLightSweep) {
        infiniteTransition.animateFloat(
            initialValue = -350f,
            targetValue = 800f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 3600
                    -350f at 0 using LinearEasing
                    800f at 1500 using FastOutSlowInEasing
                    800f at 3600
                },
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmerOffset"
        )
    } else {
        remember { mutableFloatStateOf(-500f) }
    }

    // 6. ذبذبة الجسيمات الكوانتية (Quantum Spore Oscillation)
    val particlePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particlePhase"
    )

    // 7. دوران هادئ لأقواس التحديد (Subtle Bracket Rotation)
    val bracketRotation by if (showCornerBrackets) {
        infiniteTransition.animateFloat(
            initialValue = -1.5f,
            targetValue = 1.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3500, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bracketRotation"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    // مقاس الحاوية الكلية ليتسع للمدارات والأقواس والجسيمات
    val totalCanvasSize = if (showOrbitalHud) {
        if (size <= 44.dp) size + 16.dp else size + 56.dp
    } else {
        size + 4.dp
    }

    Box(
        modifier = modifier
            .size(totalCanvasSize)
            .offset(y = levitationOffset.dp),
        contentAlignment = Alignment.Center
    ) {
        // --- الطبقة 1: الهالة النورانية الكونية المتنفسة (Cosmic Glow Aura) ---
        if (showCosmicGlow) {
            Box(
                modifier = Modifier
                    .size(size * 1.45f)
                    .scale(glowScale)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.45f * glowAlpha),
                                accentColor.copy(alpha = 0.28f * glowAlpha),
                                if (isCyberHud) AiCyan.copy(alpha = 0.20f * glowAlpha) else Color(0xFF6366F1).copy(alpha = 0.12f * glowAlpha),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }

        // --- الطبقة 2: موجة الصدمة الكهرومغناطيسية المتوسعة (Expanding Shockwave) ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseR = (size.toPx() / 2f) + 4.dp.toPx()
            val maxWaveR = baseR + 24.dp.toPx()
            val currentWaveR = baseR + (maxWaveR - baseR) * shockwaveProgress
            val waveAlpha = ((1f - shockwaveProgress) * 0.45f).coerceIn(0f, 1f)

            if (waveAlpha > 0.01f) {
                drawCircle(
                    color = primaryColor.copy(alpha = waveAlpha),
                    radius = currentWaveR,
                    center = centerOffset,
                    style = Stroke(
                        width = 1.5.dp.toPx() * (1f - shockwaveProgress * 0.5f)
                    )
                )
            }
        }

        // --- الطبقة 3: مدارات الـ HUD الكوانتية، الجسيمات الدوارة، وأقواس الاستهداف ---
        if (showOrbitalHud) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerOffset = Offset(this.size.width / 2f, this.size.height / 2f)
                val orbitMargin = if (size <= 44.dp) 6.dp.toPx() else 18.dp.toPx()
                val innerMargin = if (size <= 44.dp) 3.dp.toPx() else 9.dp.toPx()
                val outerRadius = (size.toPx() / 2f) + orbitMargin
                val innerRadius = (size.toPx() / 2f) + innerMargin

                // أ) المدار الخارجي المتقطع (Segmented Outer Ring)
                val segmentSweep = 50f
                val gap = 40f
                var currentAngle = outerOrbitAngle
                for (i in 0 until 4) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.80f),
                                accentColor.copy(alpha = 0.90f),
                                primaryColor.copy(alpha = 0.80f)
                            ),
                            center = centerOffset
                        ),
                        startAngle = currentAngle,
                        sweepAngle = segmentSweep,
                        useCenter = false,
                        topLeft = Offset(centerOffset.x - outerRadius, centerOffset.y - outerRadius),
                        size = Size(outerRadius * 2f, outerRadius * 2f),
                        style = Stroke(
                            width = 2.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )
                    currentAngle += (segmentSweep + gap)
                }

                // ب) عقدة الطاقة المدارية 1 (Orbital Satellite Node 1)
                val sat1AngleRad = Math.toRadians(outerOrbitAngle.toDouble())
                val sat1X = centerOffset.x + outerRadius * cos(sat1AngleRad).toFloat()
                val sat1Y = centerOffset.y + outerRadius * sin(sat1AngleRad).toFloat()

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White,
                            primaryColor,
                            Color.Transparent
                        ),
                        center = Offset(sat1X, sat1Y),
                        radius = 13.dp.toPx()
                    ),
                    radius = 9.5.dp.toPx(),
                    center = Offset(sat1X, sat1Y)
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.2.dp.toPx(),
                    center = Offset(sat1X, sat1Y)
                )

                // ج) عقدة الطاقة المدارية 2 المعاكسة (Orbital Satellite Node 2)
                val sat2AngleRad = Math.toRadians(outerOrbitAngle.toDouble() + 180.0)
                val sat2X = centerOffset.x + outerRadius * cos(sat2AngleRad).toFloat()
                val sat2Y = centerOffset.y + outerRadius * sin(sat2AngleRad).toFloat()

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White,
                            accentColor,
                            Color.Transparent
                        ),
                        center = Offset(sat2X, sat2Y),
                        radius = 11.dp.toPx()
                    ),
                    radius = 7.5.dp.toPx(),
                    center = Offset(sat2X, sat2Y)
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.4.dp.toPx(),
                    center = Offset(sat2X, sat2Y)
                )

                // د) المدار الداخلي النقطي المضاد (Counter Inner Micro Ring)
                drawCircle(
                    color = primaryColor.copy(alpha = 0.40f),
                    radius = innerRadius,
                    center = centerOffset,
                    style = Stroke(
                        width = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 16f), 0f)
                    )
                )

                // هـ) مؤشرات البوصلة الفضائية التكنولوجية (HUD Crosshair Ticks at 4 poles)
                val tickLen = 7.dp.toPx()
                val tickAngles = listOf(0.0, 90.0, 180.0, 270.0)
                for (deg in tickAngles) {
                    val rad = Math.toRadians(deg + innerOrbitAngle.toDouble())
                    val x1 = centerOffset.x + (innerRadius - tickLen / 2) * cos(rad).toFloat()
                    val y1 = centerOffset.y + (innerRadius - tickLen / 2) * sin(rad).toFloat()
                    val x2 = centerOffset.x + (innerRadius + tickLen / 2) * cos(rad).toFloat()
                    val y2 = centerOffset.y + (innerRadius + tickLen / 2) * sin(rad).toFloat()

                    drawLine(
                        color = primaryColor.copy(alpha = 0.75f),
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // و) أقواس الليزر الزاوية (Corner HUD Brackets)
                if (showCornerBrackets) {
                    val halfW = (size.toPx() / 2f) + 5.dp.toPx()
                    val bLen = 9.dp.toPx()
                    val bStroke = 1.6.dp.toPx()
                    val bColor = primaryColor.copy(alpha = 0.65f)

                    rotate(bracketRotation, centerOffset) {
                        // Top-Left
                        drawLine(bColor, Offset(centerOffset.x - halfW, centerOffset.y - halfW), Offset(centerOffset.x - halfW + bLen, centerOffset.y - halfW), bStroke)
                        drawLine(bColor, Offset(centerOffset.x - halfW, centerOffset.y - halfW), Offset(centerOffset.x - halfW, centerOffset.y - halfW + bLen), bStroke)

                        // Top-Right
                        drawLine(bColor, Offset(centerOffset.x + halfW, centerOffset.y - halfW), Offset(centerOffset.x + halfW - bLen, centerOffset.y - halfW), bStroke)
                        drawLine(bColor, Offset(centerOffset.x + halfW, centerOffset.y - halfW), Offset(centerOffset.x + halfW, centerOffset.y - halfW + bLen), bStroke)

                        // Bottom-Left
                        drawLine(bColor, Offset(centerOffset.x - halfW, centerOffset.y + halfW), Offset(centerOffset.x - halfW + bLen, centerOffset.y + halfW), bStroke)
                        drawLine(bColor, Offset(centerOffset.x - halfW, centerOffset.y + halfW), Offset(centerOffset.x - halfW, centerOffset.y + halfW - bLen), bStroke)

                        // Bottom-Right
                        drawLine(bColor, Offset(centerOffset.x + halfW, centerOffset.y + halfW), Offset(centerOffset.x + halfW - bLen, centerOffset.y + halfW), bStroke)
                        drawLine(bColor, Offset(centerOffset.x + halfW, centerOffset.y + halfW), Offset(centerOffset.x + halfW, centerOffset.y + halfW - bLen), bStroke)
                    }
                }

                // ز) جسيمات النور الكوانتية السابحة (Quantum Floating Particles)
                if (showParticles) {
                    val particleAngles = listOf(25.0, 70.0, 115.0, 160.0, 205.0, 250.0, 295.0, 340.0)
                    particleAngles.forEachIndexed { idx, pAngle ->
                        val dynamicRadius = outerRadius + 6.dp.toPx() + (sin(particlePhase + idx).toFloat() * 5.dp.toPx())
                        val pRad = Math.toRadians(pAngle + (outerOrbitAngle * (if (idx % 2 == 0) 0.5 else -0.4)))
                        val px = centerOffset.x + dynamicRadius * cos(pRad).toFloat()
                        val py = centerOffset.y + dynamicRadius * sin(pRad).toFloat()
                        val pAlpha = (0.35f + 0.45f * sin(particlePhase + idx * 1.2f).coerceIn(0f, 1f))

                        drawCircle(
                            color = if (idx % 2 == 0) primaryColor.copy(alpha = pAlpha) else accentColor.copy(alpha = pAlpha),
                            radius = (1.5f + (idx % 3) * 0.6f).dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                }
            }
        }

        // --- الطبقة 4: إطار الشعار الفاخر واللوجو الزجاجي (The Crystal Glass Logo) ---
        val shape = RoundedCornerShape(28.dp)

        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            qabasCardSurface().copy(alpha = 0.88f),
                            DeepSlate.copy(alpha = 0.96f),
                            Color(0xFF030712).copy(alpha = 0.98f)
                        )
                    )
                )
                .border(
                    BorderStroke(
                        1.8.dp,
                        Brush.linearGradient(
                            listOf(
                                primaryColor.copy(alpha = 0.95f),
                                accentColor.copy(alpha = 0.75f),
                                primaryColor.copy(alpha = 0.45f),
                                if (isCyberHud) AiCyan.copy(alpha = 0.80f) else GoldSecondary.copy(alpha = 0.85f)
                            )
                        )
                    ),
                    shape
                )
                .clickable(
                    enabled = onClick != null,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = primaryColor)
                ) {
                    onClick?.invoke()
                }
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            // صورة الشعار الأصلية الرسمية لقَبَس
            Image(
                painter = painterResource(id = R.drawable.img_qabas_logo),
                contentDescription = "شعار قبس ستوديو",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
            )

            // --- الطبقة 5: مسح الشيمر الزجاجي الكريستالي الفائق (Prismatic Light Shimmer) ---
            if (showLightSweep) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                ) {
                    val brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.0f),
                            Color.White.copy(alpha = 0.38f),
                            primaryColor.copy(alpha = 0.55f),
                            if (isCyberHud) AiCyan.copy(alpha = 0.35f) else GoldSecondary.copy(alpha = 0.30f),
                            Color.White.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        start = Offset(shimmerOffset - 140f, shimmerOffset - 140f),
                        end = Offset(shimmerOffset + 140f, shimmerOffset + 140f)
                    )

                    drawRect(brush = brush)
                }
            }
        }
    }
}
