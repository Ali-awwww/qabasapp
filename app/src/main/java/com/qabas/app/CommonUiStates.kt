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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import com.qabas.app.ui.components.QabasDesignCard
import com.qabas.app.ui.components.QabasPrimaryButton

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
        QabasDesignCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = theme.cardSurface
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
                    QabasPrimaryButton(
                        text = actionButtonText,
                        onClick = onActionClick,
                        modifier = Modifier.fillMaxWidth(0.78f),
                        icon = actionButtonIcon
                    )
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
            QabasGlowingSpinner(
                modifier = Modifier,
                size = 40.dp,
                strokeWidth = 3.dp,
                color = theme.primary
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
        QabasDesignCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = theme.cardSurface
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

                QabasPrimaryButton(
                    text = "إعادة المحاولة",
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(0.78f),
                    icon = Icons.Default.Refresh
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
