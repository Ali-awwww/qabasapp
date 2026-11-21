package com.qabas.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qabas.app.ui.theme.CardSurface
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.QabasTheme
import com.qabas.app.ui.theme.QabasShapeTokens
import com.qabas.app.ui.theme.ThemeManager

/**
 * Clean Apple HIG Adaptive Frosted / Elevated Surface
 * Automatically adapts cleanly to Light and Dark Mode while preserving legacy helper signatures.
 */
fun Modifier.glassmorphicBackground(
    shape: Shape = QabasShapeTokens.card,
    backgroundColor: Color = CardSurface.copy(alpha = if (ThemeManager.isDarkState) 0.88f else 0.92f),
    borderColor: Color = GoldPrimary.copy(alpha = if (ThemeManager.isDarkState) 0.6f else 0.35f),
    borderWidth: Dp = 0.5.dp,
    blurRadius: Dp = 0.dp
): Modifier = this
    .clip(shape)
    .background(backgroundColor, shape)
    .border(width = borderWidth, color = borderColor, shape = shape)

@Composable
fun GlassmorphicSurface(
    modifier: Modifier = Modifier,
    shape: Shape = QabasShapeTokens.card,
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
    shape: Shape = QabasShapeTokens.card,
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
