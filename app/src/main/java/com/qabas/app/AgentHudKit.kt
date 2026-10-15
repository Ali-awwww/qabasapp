package com.qabas.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.RobotoMonoFont

/**
 * عتاد الواجهة السيبرانية لقسم الوكيل الذكي (Agent Spatial HUD Kit)
 * لغة تصميم هندسية نيونية موحّدة: شبكة خلفية، لوحات HUD بزوايا ليزر،
 * وصفوف قياس (telemetry) بخط أحادي المسافة. كل القيم المعروضة حقيقية ومحسوبة لحظياً.
 */

private val HudBase = Color(0xFF05070D)
private val HudPanelBg = Color(0xFF0A101E)

/**
 * خلفية شبكية سيبرانية مع توهج أفقى خفيف — تُرسم خلف المحتوى وتقف ثابتة أثناء التمرير.
 */
fun Modifier.cyberGridBackground(
    line: Color = Color(0xFF00E5FF),
    cellPx: Float = 44f
): Modifier = this.drawBehind {
    drawRect(HudBase)
    var x = 0f
    while (x <= size.width) {
        drawLine(line.copy(alpha = 0.05f), Offset(x, 0f), Offset(x, size.height), 1f)
        x += cellPx
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(line.copy(alpha = 0.05f), Offset(0f, y), Offset(size.width, y), 1f)
        y += cellPx
    }
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(line.copy(alpha = 0.10f), Color.Transparent),
            startY = 0f,
            endY = size.height * 0.4f
        )
    )
}

/**
 * لوحة HUD زجاجية بزوايا ليزر سيبرانية وعنوان بأيقونة.
 */
@Composable
fun HudPanel(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accent: Color = Color(0xFF00E5FF),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HudPanelBg.copy(alpha = 0.92f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
    ) {
        HudCornerDecor(modifier = Modifier.matchParentSize(), color = accent.copy(alpha = 0.75f))
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/**
 * صف قياس هندسي: تسمية عربية على اليمين وقيمة أحادية المسافة على اليسار.
 */
@Composable
fun HudMetric(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFF64748B),
            fontFamily = CairoFont,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(Color(0xFF1E293B))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            color = accent,
            fontFamily = RobotoMonoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * تعريف تبويب واحد في شريط الـ HUD.
 */
data class AgentTab(
    val code: String,
    val label: String,
    val icon: ImageVector,
    val accent: Color
)

/**
 * شريط تبويبات نيوني هندسي قابل للتمرير أفقيًا (Spatial HUD Tab Rail).
 * كل تبويب يحمل كودًا رقميًا، أيقونة، تسمية، وخيط نيون سفلي للتبويب النشط.
 */
@Composable
fun AgentHudTabRail(
    tabs: List<AgentTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEachIndexed { index, tab ->
            HudTabItem(
                tab = tab,
                selected = index == selectedIndex,
                onClick = { onSelect(index) }
            )
        }
    }
}

@Composable
private fun HudTabItem(
    tab: AgentTab,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) tab.accent.copy(alpha = 0.14f) else Color(0xFF0A101E).copy(alpha = 0.85f))
            .border(
                width = 1.dp,
                color = if (selected) tab.accent.copy(alpha = 0.7f) else Color(0xFF1E293B),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "[${tab.code}]",
                color = if (selected) tab.accent else Color(0xFF475569),
                fontFamily = RobotoMonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                maxLines = 1
            )
            Spacer(Modifier.width(7.dp))
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                tint = if (selected) tab.accent else Color(0xFF94A3B8),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = tab.label,
                color = if (selected) Color.White else Color(0xFF94A3B8),
                fontFamily = CairoFont,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    if (selected) {
                        Brush.horizontalGradient(listOf(tab.accent, tab.accent.copy(alpha = 0.05f)))
                    } else {
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                    }
                )
        )
    }
}
