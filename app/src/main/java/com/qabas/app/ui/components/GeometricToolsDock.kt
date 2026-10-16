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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.DeepSlate
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.GoldSecondary

/** Geometric hexagon used for the dock handle. */
val HexagonShape: Shape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w * 0.5f, 0f)
    lineTo(w, h * 0.25f)
    lineTo(w, h * 0.75f)
    lineTo(w * 0.5f, h)
    lineTo(0f, h * 0.75f)
    lineTo(0f, h * 0.25f)
    close()
}

/** A single tool exposed inside the geometric dock. */
data class DockTool(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val active: Boolean = false,
    val accent: Color = GoldPrimary
)

/**
 * «مرصد قبس الهندسي» — a collapsible HUD dock holding every screen tool.
 * Collapsed: a single hexagonal handle. Expanded: an octagonal glass panel
 * with a geometric grid of engraved tiles.
 */
@Composable
fun GeometricToolsDock(
    expanded: Boolean,
    onToggle: () -> Unit,
    tools: List<DockTool>,
    modifier: Modifier = Modifier
) {
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
                animationSpec = tween(260)
            ),
            exit = fadeOut(tween(160)) + shrinkVertically(
                shrinkTowards = Alignment.Bottom,
                animationSpec = tween(200)
            )
        ) {
            DockPanel(tools = tools)
        }

        Surface(
            shape = HexagonShape,
            color = Color(0xFF0B1324).copy(alpha = 0.92f),
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.55f)),
            modifier = Modifier
                .size(54.dp)
                .clip(HexagonShape)
                .clickable { onToggle() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "أدوات الشاشة",
                    tint = GoldPrimary,
                    modifier = Modifier
                        .size(26.dp)
                        .rotate(handleRotation)
                )
            }
        }
    }
}

@Composable
private fun DockPanel(tools: List<DockTool>) {
    Surface(
        shape = CutCornerShape(20.dp),
        color = Color(0xFF0B1324).copy(alpha = 0.94f),
        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "أدوات الشاشة",
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = GoldSecondary,
                textAlign = TextAlign.Center
            )

            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                GoldPrimary.copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
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
    val tileShape = CutCornerShape(12.dp)
    val tileColor = if (tool.active) tool.accent else Color.White.copy(alpha = 0.06f)
    val borderColor = if (tool.active) tool.accent else Color.White.copy(alpha = 0.18f)
    val iconTint = if (tool.active) DeepSlate else Color.White.copy(alpha = 0.92f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(58.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(tileShape)
                .background(tileColor)
                .border(1.dp, borderColor, tileShape)
                .clickable { tool.onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = tool.label,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = tool.label,
            fontFamily = CairoFont,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
