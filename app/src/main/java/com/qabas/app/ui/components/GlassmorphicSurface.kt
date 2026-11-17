package com.qabas.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.QabasTheme
import com.qabas.app.ui.theme.ThemeManager

/**
 * Clean Apple HIG Adaptive Frosted / Elevated Surface
 * Automatically adapts cleanly to Light and Dark Mode while preserving legacy helper signatures.
 */
fun Modifier.glassmorphicBackground(
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = if (ThemeManager.isDarkState) {
        Color(0xFF1C1C1E).copy(alpha = 0.88f)
    } else {
        Color.White.copy(alpha = 0.92f)
    },
    borderColor: Color = if (ThemeManager.isDarkState) {
        Color(0xFF38383A).copy(alpha = 0.85f)
    } else {
        Color(0xFFC6C6C8).copy(alpha = 0.45f)
    },
    borderWidth: Dp = 0.5.dp,
    blurRadius: Dp = 0.dp
): Modifier = this
    .clip(shape)
    .background(backgroundColor, shape)
    .border(width = borderWidth, color = borderColor, shape = shape)

@Composable
fun GlassmorphicSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = QabasTheme.current.cardSurface.copy(alpha = if (QabasTheme.current.isDark) 0.92f else 0.96f),
    borderColor: Color = QabasTheme.current.separator.copy(alpha = if (QabasTheme.current.isDark) 0.7f else 0.4f),
    borderWidth: Dp = 0.5.dp,
    content: @Composable BoxScope.() -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = shape,
        color = backgroundColor,
        shadowElevation = if (QabasTheme.current.isDark) 0.dp else 2.dp,
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Box(content = content)
    }
}

@Composable
fun GoldGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = QabasTheme.current.cardSurface,
    accentColor: Color = GoldPrimary,
    content: @Composable BoxScope.() -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = shape,
        color = backgroundColor,
        shadowElevation = if (QabasTheme.current.isDark) 0.dp else 2.dp,
        border = BorderStroke(0.8.dp, accentColor.copy(alpha = if (QabasTheme.current.isDark) 0.45f else 0.35f))
    ) {
        Box(content = content)
    }
}
