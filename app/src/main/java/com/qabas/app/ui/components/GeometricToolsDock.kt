package com.qabas.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.QabasTheme

/** A single tool exposed inside the screen tools dock. */
data class DockTool(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val active: Boolean = false,
    val accent: Color = GoldPrimary
)

/**
 * قائمة أدوات الشاشة بأسلوب Apple Control Center المدمج.
 */
@Composable
fun GeometricToolsDock(
    expanded: Boolean,
    onToggle: () -> Unit,
    tools: List<DockTool>,
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
    val handleRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "dock_handle_rotation"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(220)) + expandVertically(
                expandFrom = Alignment.Bottom,
                animationSpec = tween(240)
            ),
            exit = fadeOut(tween(160)) + shrinkVertically(
                shrinkTowards = Alignment.Bottom,
                animationSpec = tween(200)
            )
        ) {
            DockPanel(tools = tools)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
            // Preserve compact width while keeping 48.dp touch target
        ) {
            Surface(
                shape = CircleShape,
                color = theme.cardSurface.copy(alpha = if (theme.isDark) 0.88f else 0.94f),
                border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.65f)),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { onToggle() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "أدوات الشاشة",
                        tint = theme.primary,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(handleRotation)
                    )
                }
            }
            Text(
                text = "الأدوات",
                color = Color.White,
                fontFamily = CairoFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

@Composable
private fun DockPanel(tools: List<DockTool>) {
    val theme = QabasTheme.current
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = theme.cardSurface.copy(alpha = if (theme.isDark) 0.94f else 0.97f),
        border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.65f)),
        shadowElevation = 8.dp,
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "أدوات الشاشة",
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = theme.textPrimary,
                textAlign = TextAlign.Center
            )

            HorizontalDivider(
                modifier = Modifier.width(120.dp),
                thickness = 0.5.dp,
                color = theme.separator.copy(alpha = 0.5f)
            )

            tools.chunked(3).forEach { rowTools ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    rowTools.forEach { tool ->
                        DockTile(tool = tool)
                    }
                }
            }
        }
    }
}

@Composable
private fun DockTile(tool: DockTool) {
    val theme = QabasTheme.current
    val activeAccent = if (tool.accent == GoldPrimary) theme.primary else tool.accent
    val tileColor = if (tool.active) activeAccent else theme.secondarySurface
    val iconTint = if (tool.active) theme.onPrimary else theme.textPrimary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(62.dp)
    ) {
        Surface(
            onClick = tool.onClick,
            shape = RoundedCornerShape(14.dp),
            color = tileColor,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = tool.label,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = tool.label,
            fontFamily = CairoFont,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            color = theme.textPrimary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

