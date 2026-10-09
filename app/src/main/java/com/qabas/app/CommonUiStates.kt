package com.qabas.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.composed
import com.qabas.app.ui.theme.*

/**
 * Micro-interaction scale modifier on click / press for luxury feel
 */
@Composable
fun Modifier.bouncingClickable(
    enabled: Boolean = true,
    scaleDown: Float = 0.96f,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Play sound when pressed
    LaunchedEffect(isPressed) {
        if (isPressed) {
            AppSoundManager.playClick()
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bouncingClickableScale"
    )
    return this
        .graphicsLayer(scaleX = scale, scaleY = scale)
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(color = AiCyan.copy(alpha = 0.25f)),
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * Dynamic subtle glow and refined border for dark surface cards that follows the active theme preset
 */
fun Modifier.luxuryCardStyle(
    shapeRadius: Dp = 20.dp,
    borderAlpha: Float = 0.25f,
    glowElevation: Dp = 6.dp
): Modifier = composed {
    val preset = ThemeManager.currentPreset.collectAsState().value
    val primary = preset.primaryColor
    val accent = preset.accentColor
    val isNeon = preset.isNeon
    val shape = RoundedCornerShape(shapeRadius)
    
    val infiniteTransition = rememberInfiniteTransition(label = "neon_glow")
    val glowAnim by if (isNeon) {
        infiniteTransition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowAnim"
        )
    } else {
        remember { mutableStateOf(1.0f) }
    }

    val finalBorderAlpha = if (isNeon) 0.85f * glowAnim else borderAlpha
    val finalGlowElevation = if (isNeon) 12.dp * glowAnim else glowElevation

    this
        .shadow(
            elevation = finalGlowElevation,
            shape = shape,
            ambientColor = primary.copy(alpha = if (isNeon) 0.45f else 0.15f),
            spotColor = accent.copy(alpha = if (isNeon) 0.55f else 0.22f)
        )
        .border(
            width = if (isNeon) 1.8.dp else 1.dp,
            brush = Brush.linearGradient(
                listOf(
                    primary.copy(alpha = finalBorderAlpha),
                    accent.copy(alpha = finalBorderAlpha * 0.8f),
                    primary.copy(alpha = finalBorderAlpha * 0.4f)
                )
            ),
            shape = shape
        )
}

/**
 * Shared animated Shimmer Brush for Skeleton Loading
 */
@Composable
fun rememberQabasShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "qabas_shimmer_transition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "qabas_shimmer_anim"
    )

    return Brush.linearGradient(
        colors = listOf(
            Color(0xFF131D31),
            Color(0xFF151B2B),
            Color(0xFF1E293B).copy(alpha = 0.6f),
            Color(0xFF151B2B),
            Color(0xFF131D31)
        ),
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )
}

/**
 * Skeleton Primitive Box
 */
@Composable
fun QabasSkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    val brush = rememberQabasShimmerBrush()
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}

/**
 * Luxury Skeleton Hero Banner
 */
@Composable
fun QabasHeroSkeleton(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .luxuryCardStyle(shapeRadius = 24.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                QabasSkeletonBox(modifier = Modifier.size(width = 110.dp, height = 28.dp), shape = RoundedCornerShape(12.dp))
                QabasSkeletonBox(modifier = Modifier.size(width = 80.dp, height = 28.dp), shape = RoundedCornerShape(12.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            QabasSkeletonBox(modifier = Modifier.fillMaxWidth(0.75f).height(22.dp))
            Spacer(modifier = Modifier.height(8.dp))
            QabasSkeletonBox(modifier = Modifier.fillMaxWidth(0.9f).height(14.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QabasSkeletonBox(modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp))
                QabasSkeletonBox(modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp))
            }
        }
    }
}

/**
 * Luxury Skeleton List for Studios & Feeds
 */
@Composable
fun QabasListSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 3
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(itemCount) {
            QabasShimmerCard()
        }
    }
}

/**
 * Luxury Skeleton Reel Card
 */
@Composable
fun QabasReelFeedSkeleton(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(360.dp)
            .luxuryCardStyle(shapeRadius = 20.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            QabasSkeletonBox(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(20.dp))
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                QabasSkeletonBox(modifier = Modifier.fillMaxWidth(0.6f).height(20.dp))
                Spacer(modifier = Modifier.height(8.dp))
                QabasSkeletonBox(modifier = Modifier.fillMaxWidth(0.85f).height(14.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    QabasSkeletonBox(modifier = Modifier.size(width = 80.dp, height = 16.dp))
                    QabasSkeletonBox(modifier = Modifier.size(width = 100.dp, height = 24.dp), shape = RoundedCornerShape(8.dp))
                }
            }
        }
    }
}

/**
 * Luxury Skeleton Profile
 */
@Composable
fun QabasProfileSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        QabasSkeletonBox(modifier = Modifier.size(96.dp), shape = CircleShape)
        QabasSkeletonBox(modifier = Modifier.size(width = 160.dp, height = 22.dp))
        QabasSkeletonBox(modifier = Modifier.size(width = 120.dp, height = 14.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QabasSkeletonBox(modifier = Modifier.weight(1f).height(74.dp), shape = RoundedCornerShape(16.dp))
            QabasSkeletonBox(modifier = Modifier.weight(1f).height(74.dp), shape = RoundedCornerShape(16.dp))
            QabasSkeletonBox(modifier = Modifier.weight(1f).height(74.dp), shape = RoundedCornerShape(16.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        QabasSkeletonBox(modifier = Modifier.fillMaxWidth().height(120.dp), shape = RoundedCornerShape(18.dp))
    }
}

/**
 * Standard Empty State Component for Qabas App
 */
@Composable
fun QabasEmptyState(
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.FolderOpen,
    title: String,
    description: String,
    actionButtonText: String? = null,
    actionButtonIcon: ImageVector? = null,
    onActionClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, AiCyan.copy(alpha = 0.2f), RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    AiCyan.copy(alpha = 0.28f),
                                    DeepSlate
                                )
                            )
                        )
                        .border(1.5.dp, AiViolet.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AiCyan,
                        modifier = Modifier.size(42.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = title,
                    color = Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = description,
                    color = TextSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )

                if (actionButtonText != null && onActionClick != null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onActionClick,
                        colors = ButtonDefaults.buttonColors(containerColor = AiCyan),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(46.dp)
                    ) {
                        if (actionButtonIcon != null) {
                            Icon(
                                imageVector = actionButtonIcon,
                                contentDescription = null,
                                tint = DeepSlate,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = actionButtonText,
                            color = DeepSlate,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Standard Loading State Component for Qabas App
 */
@Composable
fun QabasLoadingState(
    modifier: Modifier = Modifier,
    message: String = "جاري التحميل والمعالجة...",
    subMessage: String = "يتم إعداد البيانات بأعلى معايير الإتقان"
) {
    val infiniteTransition = rememberInfiniteTransition(label = "qabas_loading_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(AiCyan.copy(alpha = pulseAlpha * 0.2f))
                    .border(2.dp, AiCyan.copy(alpha = pulseAlpha), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = AiCyanLight,
                    trackColor = DeepSlate,
                    strokeWidth = 3.5.dp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = message,
                color = AiCyanLight,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = subMessage,
                color = TextSecondary,
                fontFamily = NotoSansFont,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Standard Error State Component for Qabas App with Retry Option
 */
@Composable
fun QabasErrorState(
    modifier: Modifier = Modifier,
    title: String = "حدث خطأ غير متوقع",
    message: String = "تعذر إكمال العملية، يرجى المحاولة مرة أخرى.",
    onRetry: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF7F1D1D).copy(alpha = 0.3f))
                        .border(1.5.dp, Color(0xFFEF4444).copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = title,
                    color = Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = message,
                    color = TextSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = AiCyan),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "إعادة المحاولة",
                        color = DeepSlate,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

/**
 * Shimmer Loading Card for Lists
 */
@Composable
fun QabasShimmerCard(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_anim"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF151B2B),
            Color(0xFF1E293B).copy(alpha = 0.6f),
            Color(0xFF151B2B)
        ),
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(95.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .width(70.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
                Box(
                    modifier = Modifier
                        .width(50.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
            }
        }
    }
}

/**
 * High-Polish Visual Glowing Spinner for Qabas
 */
@Composable
fun QabasGlowingSpinner(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 3.5.dp,
    color: Color = AiCyan
) {
    val infiniteTransition = rememberInfiniteTransition(label = "qabas_spinner_anim")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinner_rotation"
    )
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "spinner_glow"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing aura
        Box(
            modifier = Modifier
                .size(size * 0.95f)
                .graphicsLayer(scaleX = glowScale, scaleY = glowScale)
                .background(color.copy(alpha = 0.15f), CircleShape)
        )
        // Background track ring
        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.size(size),
            color = Color(0xFF151B2B),
            strokeWidth = strokeWidth,
            trackColor = Color.Transparent
        )
        // Active glowing rotating arc
        CircularProgressIndicator(
            modifier = Modifier
                .size(size)
                .graphicsLayer(rotationZ = rotation),
            color = color,
            trackColor = Color.Transparent,
            strokeWidth = strokeWidth,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

/**
 * Animated Linear Visual Progress Bar with Gold Gradient & Percentage
 */
@Composable
fun QabasProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    label: String? = null,
    subLabel: String? = null,
    height: androidx.compose.ui.unit.Dp = 8.dp,
    showPercentage: Boolean = true
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "progress_anim"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "bar_shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    val techBarBrush = Brush.horizontalGradient(
        colors = listOf(AiViolet, AiCyan, AiCyanLight),
        startX = shimmerOffset - 300f,
        endX = shimmerOffset + 300f
    )

    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null || showPercentage) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (label != null) {
                    Text(
                        text = label,
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                if (showPercentage) {
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        color = AiCyanLight,
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Progress Bar Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(Color(0xFF151B2B))
                .border(0.5.dp, Color(0xFF1E293B), RoundedCornerShape(height / 2))
        ) {
            // Filled portion
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(height / 2))
                    .background(techBarBrush)
            )
        }

        if (subLabel != null) {
            Text(
                text = subLabel,
                color = TextSecondary,
                fontFamily = NotoSansFont,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * Indeterminate Glowing Shimmer Line (for instant AI / fetch feedback)
 */
@Composable
fun QabasIndeterminateProgressBar(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 4.dp,
    label: String? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "indet_bar")
    val slideAnim by infiniteTransition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "slide_anim"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = AiCyan,
                    strokeWidth = 1.5.dp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    color = AiCyanLight,
                    fontFamily = CairoFont,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(Color(0xFF151B2B))
        ) {
            val startRatio = (slideAnim - 0.3f).coerceIn(0f, 1f)
            val endRatio = (slideAnim + 0.3f).coerceIn(0f, 1f)
            if (endRatio > startRatio) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    AiViolet.copy(alpha = 0.55f),
                                    AiCyanLight,
                                    AiCyan.copy(alpha = 0.55f),
                                    Color.Transparent
                                ),
                                startX = slideAnim * 800f - 200f,
                                endX = slideAnim * 800f + 200f
                            )
                        )
                )
            }
        }
    }
}

/**
 * Multi-Step Processing Stepper Bar
 */
@Composable
fun QabasStepProgressBar(
    currentStep: Int,
    totalSteps: Int,
    stepLabels: List<String>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            stepLabels.forEachIndexed { index, label ->
                val isCompleted = index < currentStep
                val isCurrent = index == currentStep
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isCompleted -> AiCyan
                                    isCurrent -> AiCyan.copy(alpha = 0.25f)
                                    else -> Color(0xFF151B2B)
                                }
                            )
                            .border(
                                1.5.dp,
                                if (isCompleted) AiCyan else if (isCurrent) AiViolet else Color(0xFF1E293B),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCompleted) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(14.dp))
                        } else if (isCurrent) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = AiCyan,
                                strokeWidth = 1.5.dp
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontFamily = RobotoMonoFont,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = label,
                        color = if (isCompleted) AiCyanLight else if (isCurrent) AiVioletLight else Color.Gray,
                        fontFamily = CairoFont,
                        fontSize = 10.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }

        // Connecting Progress Line
        val targetProg = (currentStep.toFloat() / (totalSteps - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
        QabasProgressBar(
            progress = targetProg,
            height = 4.dp,
            showPercentage = false
        )
    }
}

