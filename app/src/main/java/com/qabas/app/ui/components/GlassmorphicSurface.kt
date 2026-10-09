package com.qabas.app.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qabas.app.ui.theme.GoldPrimary

/**
 * Modern Frosted Glass (Glassmorphic) Modifier & Composable Wrapper
 * Applies a blurred, semi-transparent background with an elegant subtle border gradient
 * to give cards, dialogs, and navigation surfaces a modern, premium look.
 */

fun Modifier.glassmorphicBackground(
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = Color(0xFF151B28).copy(alpha = 0.72f),
    borderColor: Color = Color(0xFFFFFFFF).copy(alpha = 0.12f),
    borderWidth: Dp = 1.dp,
    blurRadius: Dp = 16.dp
): Modifier = this
    .clip(shape)
    .then(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.blur(blurRadius)
        } else {
            Modifier
        }
    )
    .background(backgroundColor, shape)
    .border(
        width = borderWidth,
        brush = Brush.linearGradient(
            colors = listOf(
                borderColor,
                borderColor.copy(alpha = 0.03f)
            )
        ),
        shape = shape
    )

/**
 * Premium Glassmorphic Surface Composable Wrapper
 * Encapsulates the layered frosted glass effect for cards and navigation bars.
 */
@Composable
fun GlassmorphicSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = Color(0xFF121826).copy(alpha = 0.75f),
    borderColor: Color = Color(0xFFFFFFFF).copy(alpha = 0.14f),
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor, shape)
            .border(
                width = borderWidth,
                brush = Brush.linearGradient(
                    colors = listOf(
                        borderColor,
                        borderColor.copy(alpha = 0.04f)
                    )
                ),
                shape = shape
            ),
        content = content
    )
}

/**
 * Golden Accent Glassmorphic Surface
 * Specifically tuned for Islamic Art & Royal Qabas elements.
 */
@Composable
fun GoldGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = Color(0xFF171B26).copy(alpha = 0.82f),
    accentColor: Color = GoldPrimary,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor, shape)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.45f),
                        accentColor.copy(alpha = 0.08f)
                    )
                ),
                shape = shape
            ),
        content = content
    )
}
