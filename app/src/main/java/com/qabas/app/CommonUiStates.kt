package com.qabas.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*

/**
 * Subtle Apple-like spring press interaction
 */
@Composable
fun Modifier.bouncingClickable(
    enabled: Boolean = true,
    scaleDown: Float = 0.97f,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val theme = QabasTheme.current

    LaunchedEffect(isPressed) {
        if (isPressed) {
            AppSoundManager.playClick()
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bouncingClickableScale"
    )
    return this
        .graphicsLayer(scaleX = scale, scaleY = scale)
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(color = theme.primary.copy(alpha = 0.14f)),
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * Clean Apple HIG elevated card surface modifier (replaces dark neon card styling)
 */
fun Modifier.luxuryCardStyle(
    shapeRadius: Dp = 16.dp,
    borderAlpha: Float = 0.35f,
    glowElevation: Dp = 2.dp
): Modifier = composed {
    val theme = QabasTheme.current
    val shape = RoundedCornerShape(shapeRadius)
    val effectiveElevation = if (theme.isDark) 0.dp else glowElevation.coerceAtMost(3.dp)
    val borderColor = if (theme.isDark) {
        theme.separator.copy(alpha = 0.7f)
    } else {
        theme.separator.copy(alpha = borderAlpha.coerceAtMost(0.45f))
    }

    this
        .shadow(
            elevation = effectiveElevation,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.08f),
            spotColor = Color.Black.copy(alpha = 0.10f)
        )
        .border(
            width = 0.5.dp,
            color = borderColor,
            shape = shape
        )
}

/**
 * Adaptive Light/Dark Shimmer Brush
 */
@Composable
fun rememberQabasShimmerBrush(): Brush {
    val theme = QabasTheme.current
    val transition = rememberInfiniteTransition(label = "qabas_shimmer_transition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "qabas_shimmer_anim"
    )

    val baseColor = if (theme.isDark) Color(0xFF1C1C1E) else Color(0xFFE5E5EA)
    val highlightColor = if (theme.isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)

    return Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )
}

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

@Composable
fun QabasHeroSkeleton(modifier: Modifier = Modifier) {
    val theme = QabasTheme.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .luxuryCardStyle(shapeRadius = 18.dp),
        colors = CardDefaults.cardColors(containerColor = theme.cardSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                QabasSkeletonBox(modifier = Modifier.size(width = 110.dp, height = 28.dp), shape = RoundedCornerShape(10.dp))
                QabasSkeletonBox(modifier = Modifier.size(width = 80.dp, height = 28.dp), shape = RoundedCornerShape(10.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            QabasSkeletonBox(modifier = Modifier.fillMaxWidth(0.75f).height(22.dp))
            Spacer(modifier = Modifier.height(8.dp))
            QabasSkeletonBox(modifier = Modifier.fillMaxWidth(0.9f).height(14.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QabasSkeletonBox(modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(12.dp))
                QabasSkeletonBox(modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(12.dp))
            }
        }
    }
}

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

@Composable
fun QabasReelFeedSkeleton(modifier: Modifier = Modifier) {
    val theme = QabasTheme.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(360.dp)
            .luxuryCardStyle(shapeRadius = 18.dp),
        colors = CardDefaults.cardColors(containerColor = theme.cardSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            QabasSkeletonBox(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(18.dp))
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
            QabasSkeletonBox(modifier = Modifier.weight(1f).height(74.dp), shape = RoundedCornerShape(14.dp))
            QabasSkeletonBox(modifier = Modifier.weight(1f).height(74.dp), shape = RoundedCornerShape(14.dp))
            QabasSkeletonBox(modifier = Modifier.weight(1f).height(74.dp), shape = RoundedCornerShape(14.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        QabasSkeletonBox(modifier = Modifier.fillMaxWidth().height(120.dp), shape = RoundedCornerShape(16.dp))
    }
}

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
    val theme = QabasTheme.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = theme.cardSurface,
            shape = RoundedCornerShape(20.dp),
            shadowElevation = if (theme.isDark) 0.dp else 2.dp,
            border = BorderStroke(0.5.dp, theme.separator.copy(alpha = if (theme.isDark) 0.7f else 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(theme.primary.copy(alpha = if (theme.isDark) 0.16f else 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = title,
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = description,
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )

                if (actionButtonText != null && onActionClick != null) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onActionClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.primary,
                            contentColor = theme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.heightIn(min = 46.dp)
                    ) {
                        if (actionButtonIcon != null) {
                            Icon(
                                imageVector = actionButtonIcon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = actionButtonText,
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

@Composable
fun QabasLoadingState(
    modifier: Modifier = Modifier,
    message: String = "جاري التحميل والمعالجة...",
    subMessage: String = "يرجى الانتظار قليلاً"
) {
    val theme = QabasTheme.current
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
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = theme.primary,
                trackColor = theme.secondarySurface,
                strokeWidth = 3.dp,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = message,
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subMessage,
                color = theme.textSecondary,
                fontFamily = NotoSansFont,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun QabasErrorState(
    modifier: Modifier = Modifier,
    title: String = "تعذر إتمام العملية",
    message: String = "حدث خطأ غير متوقع، يرجى المحاولة مرة أخرى.",
    onRetry: () -> Unit
) {
    val theme = QabasTheme.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = theme.cardSurface,
            shape = RoundedCornerShape(20.dp),
            shadowElevation = if (theme.isDark) 0.dp else 2.dp,
            border = BorderStroke(0.5.dp, theme.destructive.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(theme.destructive.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = theme.destructive,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = title,
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = message,
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.primary,
                        contentColor = theme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.heightIn(min = 46.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "إعادة المحاولة",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun QabasShimmerCard(modifier: Modifier = Modifier) {
    val theme = QabasTheme.current
    val shimmerBrush = rememberQabasShimmerBrush()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp),
        color = theme.cardSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.5.dp, theme.separator.copy(alpha = if (theme.isDark) 0.7f else 0.4f))
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
                    .height(15.dp)
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

@Composable
fun QabasGlowingSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    strokeWidth: Dp = 3.dp,
    color: Color = QabasTheme.current.primary
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = color,
        trackColor = QabasTheme.current.secondarySurface,
        strokeWidth = strokeWidth,
        strokeCap = StrokeCap.Round
    )
}

@Composable
fun QabasProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    label: String? = null,
    subLabel: String? = null,
    height: Dp = 6.dp,
    showPercentage: Boolean = true
) {
    val theme = QabasTheme.current
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "progress_anim"
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
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
                if (showPercentage) {
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        color = theme.primary,
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(theme.secondarySurface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(height / 2))
                    .background(theme.primary)
            )
        }

        if (subLabel != null) {
            Text(
                text = subLabel,
                color = theme.textSecondary,
                fontFamily = NotoSansFont,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun QabasIndeterminateProgressBar(
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    label: String? = null
) {
    val theme = QabasTheme.current
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
                    color = theme.primary,
                    strokeWidth = 1.5.dp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    color = theme.textSecondary,
                    fontFamily = CairoFont,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(height / 2)),
            color = theme.primary,
            trackColor = theme.secondarySurface
        )
    }
}

@Composable
fun QabasStepProgressBar(
    currentStep: Int,
    totalSteps: Int,
    stepLabels: List<String>,
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
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
                                    isCompleted -> theme.primary
                                    isCurrent -> theme.primary.copy(alpha = 0.16f)
                                    else -> theme.secondarySurface
                                }
                            )
                            .border(
                                1.dp,
                                when {
                                    isCompleted || isCurrent -> theme.primary
                                    else -> theme.separator
                                },
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCompleted) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = theme.onPrimary, modifier = Modifier.size(14.dp))
                        } else if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(theme.primary)
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                color = theme.textSecondary,
                                fontSize = 10.sp,
                                fontFamily = RobotoMonoFont,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = label,
                        color = if (isCompleted || isCurrent) theme.primary else theme.textSecondary,
                        fontFamily = CairoFont,
                        fontSize = 10.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }

        val targetProg = (currentStep.toFloat() / (totalSteps - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
        QabasProgressBar(
            progress = targetProg,
            height = 4.dp,
            showPercentage = false
        )
    }
}
