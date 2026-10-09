package com.qabas.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun HudCornerDecor(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier.padding(16.dp)) {
        val lineLen = 35.dp.toPx()
        val thickness = 1.8.dp.toPx()
        val w = this.size.width
        val h = this.size.height
        val secondaryLen = 10.dp.toPx()
        val secondaryOffset = 6.dp.toPx()
        
        // Top Left
        drawLine(color, Offset(0f, 0f), Offset(lineLen, 0f), thickness)
        drawLine(color, Offset(0f, 0f), Offset(0f, lineLen), thickness)
        drawLine(color.copy(alpha = 0.5f), Offset(secondaryOffset, secondaryOffset), Offset(secondaryOffset + secondaryLen, secondaryOffset), thickness / 2)
        drawLine(color.copy(alpha = 0.5f), Offset(secondaryOffset, secondaryOffset), Offset(secondaryOffset, secondaryOffset + secondaryLen), thickness / 2)
        
        // Top Right
        drawLine(color, Offset(w, 0f), Offset(w - lineLen, 0f), thickness)
        drawLine(color, Offset(w, 0f), Offset(w, lineLen), thickness)
        drawLine(color.copy(alpha = 0.5f), Offset(w - secondaryOffset, secondaryOffset), Offset(w - secondaryOffset - secondaryLen, secondaryOffset), thickness / 2)
        drawLine(color.copy(alpha = 0.5f), Offset(w - secondaryOffset, secondaryOffset), Offset(w - secondaryOffset, secondaryOffset + secondaryLen), thickness / 2)
        
        // Bottom Left
        drawLine(color, Offset(0f, h), Offset(lineLen, h), thickness)
        drawLine(color, Offset(0f, h), Offset(0f, h - lineLen), thickness)
        drawLine(color.copy(alpha = 0.5f), Offset(secondaryOffset, h - secondaryOffset), Offset(secondaryOffset + secondaryLen, h - secondaryOffset), thickness / 2)
        drawLine(color.copy(alpha = 0.5f), Offset(secondaryOffset, h - secondaryOffset), Offset(secondaryOffset, h - secondaryOffset - secondaryLen), thickness / 2)
        
        // Bottom Right
        drawLine(color, Offset(w, h), Offset(w - lineLen, h), thickness)
        drawLine(color, Offset(w, h), Offset(w, h - lineLen), thickness)
        drawLine(color.copy(alpha = 0.5f), Offset(w - secondaryOffset, h - secondaryOffset), Offset(w - secondaryOffset - secondaryLen, h - secondaryOffset), thickness / 2)
        drawLine(color.copy(alpha = 0.5f), Offset(w - secondaryOffset, h - secondaryOffset), Offset(w - secondaryOffset, h - secondaryOffset - secondaryLen), thickness / 2)
    }
}
