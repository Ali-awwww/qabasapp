package com.qabas.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
            fontSize = 12.sp,
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
            fontSize = 12.sp,
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
                fontSize = 12.sp,
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

/**
 * 🛰️ شريط التيليميتري النيوني اللحظي الموحّد (Unified Live Telemetry HUD Strip)
 * يربط بيانات الشبكة وزمن الاستجابة الحقيقي، النموذج والمزود النشط، واستهلاك الذاكرة الحية للجهاز.
 * مصمم بنمط زجاجي هولوغرافي متقدم بألوان السيان والزمرد (Cyber Cyan & Emerald).
 */
@Composable
fun LiveTelemetryHudStrip(
    latencyMs: Long,
    isConnected: Boolean,
    isPinging: Boolean,
    activeModelName: String,
    isOfflineMode: Boolean,
    usedMemoryMb: Long,
    maxMemoryMb: Long,
    onRefresh: () -> Unit,
    onModelClick: () -> Unit = {},
    onMemoryClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hud_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val spinAngle by animateFloatAsState(
        targetValue = if (isPinging) 360f else 0f,
        animationSpec = if (isPinging) {
            infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        } else {
            spring()
        },
        label = "spin_angle"
    )

    val holographicBorder = Brush.horizontalGradient(
        listOf(
            Color(0xFF00E5FF).copy(alpha = 0.75f),
            Color(0xFF10B981).copy(alpha = 0.85f),
            Color(0xFF00E5FF).copy(alpha = 0.35f)
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF060B18).copy(alpha = 0.92f))
            .border(1.dp, holographicBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Latency & Ping
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onRefresh)
                    .padding(end = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isPinging -> Color(0xFF00E5FF).copy(alpha = pulseAlpha)
                                isConnected -> Color(0xFF10B981).copy(alpha = pulseAlpha)
                                else -> Color(0xFFEF4444)
                            }
                        )
                )
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(
                        text = "الاستجابة",
                        fontSize = 12.sp,
                        fontFamily = CairoFont,
                        color = Color(0xFF94A3B8),
                        maxLines = 1
                    )
                    Text(
                        text = if (isPinging) "فحص..." else if (isConnected) "${latencyMs}ms" else "مفصول",
                        fontSize = 12.sp,
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected) Color(0xFF10B981) else Color(0xFFEF4444),
                        maxLines = 1
                    )
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(26.dp)
                    .background(Color(0xFF1E293B))
            )

            // 2. Active Model
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1.3f)
                    .clickable(onClick = onModelClick)
                    .padding(horizontal = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(5.dp))
                Column {
                    Text(
                        text = if (isOfflineMode) "نموذج محلي" else "النموذج النشط",
                        fontSize = 12.sp,
                        fontFamily = CairoFont,
                        color = Color(0xFF94A3B8),
                        maxLines = 1
                    )
                    Text(
                        text = activeModelName,
                        fontSize = 12.sp,
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(26.dp)
                    .background(Color(0xFF1E293B))
            )

            // 3. Live Device Memory Allocation
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1.1f)
                    .clickable(onClick = onMemoryClick)
                    .padding(horizontal = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(5.dp))
                Column {
                    Text(
                        text = "الذاكرة الحية",
                        fontSize = 12.sp,
                        fontFamily = CairoFont,
                        color = Color(0xFF94A3B8),
                        maxLines = 1
                    )
                    Text(
                        text = "${usedMemoryMb}/${maxMemoryMb}M",
                        fontSize = 12.sp,
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        maxLines = 1
                    )
                }
            }

            // 4. Refresh Button
            IconButton(
                onClick = onRefresh,
                enabled = !isPinging,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f), CircleShape)
                    .testTag("telemetry_refresh_btn")
            ) {
                if (isPinging) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = Color(0xFF00E5FF),
                        strokeWidth = 1.5.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "تحديث التيليميتري اللحظي",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(spinAngle)
                    )
                }
            }
        }
    }
}

/**
 * 🎛️ منصة التحكم الهندسية الموحدة (Unified Geometric Control Deck)
 * ستة أقسام رئيسية لمركز التطوير:
 * [01 نظرة عامة] · [02 مساحة العمل] · [03 الكونسول] · [04 المشاريع] · [05 النشاط] · [06 الأدوات]
 */
@Composable
fun AgentGeometricControlDeck(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val modules = listOf(
        AgentTab("01", "نظرة عامة", Icons.Default.Dashboard, Color(0xFF00E5FF)),
        AgentTab("02", "مساحة العمل", Icons.Default.Code, Color(0xFF8B5CF6)),
        AgentTab("03", "الكونسول", Icons.Default.Terminal, Color(0xFF10B981)),
        AgentTab("04", "المشاريع", Icons.Default.FolderOpen, Color(0xFF00E5FF)),
        AgentTab("05", "النشاط", Icons.Default.History, Color(0xFF8B5CF6)),
        AgentTab("06", "الأدوات", Icons.Default.Handyman, Color(0xFF10B981))
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        modules.forEachIndexed { index, module ->
            val isSelected = selectedIndex == index
            val animatedBgAlpha by animateFloatAsState(
                targetValue = if (isSelected) 0.18f else 0.05f,
                animationSpec = spring(),
                label = "deck_bg_alpha"
            )
            val animatedBorderAlpha by animateFloatAsState(
                targetValue = if (isSelected) 0.85f else 0.2f,
                animationSpec = spring(),
                label = "deck_border_alpha"
            )

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = module.accent.copy(alpha = animatedBgAlpha),
                border = BorderStroke(
                    width = 1.dp,
                    brush = if (isSelected) {
                        Brush.horizontalGradient(
                            listOf(
                                module.accent.copy(alpha = animatedBorderAlpha),
                                Color(0xFF10B981).copy(alpha = animatedBorderAlpha)
                            )
                        )
                    } else {
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF1E293B),
                                Color(0xFF1E293B)
                            )
                        )
                    }
                ),
                modifier = Modifier
                    .width(104.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelect(index) }
                    .testTag("deck_module_${module.code}")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "[${module.code}]",
                            fontSize = 8.sp,
                            fontFamily = RobotoMonoFont,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) module.accent else Color(0xFF64748B)
                        )
                        Spacer(Modifier.width(3.dp))
                        Icon(
                            imageVector = module.icon,
                            contentDescription = null,
                            tint = if (isSelected) module.accent else Color(0xFF94A3B8),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = module.label,
                        fontSize = 12.sp,
                        fontFamily = CairoFont,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (isSelected) 0.8f else 0.2f)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (isSelected) module.accent else Color.Transparent)
                    )
                }
            }
        }
    }
}

/**
 * شريط المراقبة الحية للرموز والسرعة (Cyber Live Telemetry HUD)
 * يعرض استهلاك الرموز، السرعة اللحظية، وزمن الاستجابة مع مؤشر نبضي.
 */
@Composable
fun CyberLiveTelemetryHud(
    tokensIn: Int,
    tokensOut: Int,
    latencyMs: Long,
    tokensPerSec: Float,
    isThinking: Boolean,
    onRunDiagnostic: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulseTransition = rememberInfiniteTransition(label = "hud_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        color = Color(0xFF091220),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isThinking) Color(0xFF10B981).copy(alpha = pulseAlpha)
                                else Color(0xFF00E5FF)
                            )
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isThinking) "SYSTEM PROCESSING ⚡" else "TELEMETRY LIVE 🟢",
                        color = if (isThinking) Color(0xFF10B981) else Color(0xFF00E5FF),
                        fontFamily = RobotoMonoFont,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    onClick = onRunDiagnostic,
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Troubleshoot, contentDescription = null, tint = Color(0xFFC084FC), modifier = Modifier.size(13.dp))
                        Text(
                            text = "تشخيص سريع 🔬",
                            color = Color(0xFFE9D5FF),
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Tokens In / Out
                Column(modifier = Modifier.weight(1f)) {
                    Text("الرموز (Tokens)", color = Color(0xFF64748B), fontFamily = CairoFont, fontSize = 10.sp)
                    Text(
                        "${tokensIn + tokensOut}",
                        color = Color.White,
                        fontFamily = RobotoMonoFont,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Tokens / Sec Speed
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("السرعة (T/s)", color = Color(0xFF64748B), fontFamily = CairoFont, fontSize = 10.sp)
                    Text(
                        if (tokensPerSec > 0f) "%.1f".format(tokensPerSec) else "--",
                        color = Color(0xFF10B981),
                        fontFamily = RobotoMonoFont,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Latency
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("الاستجابة (Latency)", color = Color(0xFF64748B), fontFamily = CairoFont, fontSize = 10.sp)
                    Text(
                        if (latencyMs > 0) "${latencyMs}ms" else "--",
                        color = Color(0xFF38BDF8),
                        fontFamily = RobotoMonoFont,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * شريط مراحل التفكير الحي (Reasoning Flow HUD)
 * يوضح بدقة المرحلة الحالية: تحليل -> تخطيط -> كود -> فحص
 */
@Composable
fun CyberReasoningFlowHud(
    currentPhase: String, // "ANALYZE", "PLAN", "CODE", "VERIFY", "IDLE"
    modifier: Modifier = Modifier
) {
    val phases = listOf(
        Triple("ANALYZE", "تحليل 🔍", Color(0xFF38BDF8)),
        Triple("PLAN", "تخطيط 📐", Color(0xFF8B5CF6)),
        Triple("CODE", "تعديل 💻", Color(0xFF10B981)),
        Triple("VERIFY", "تدقيق 🧪", Color(0xFFF59E0B))
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = Color(0xFF070C16),
        border = BorderStroke(1.dp, Color(0xFF1E2D4A))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            phases.forEachIndexed { index, (phaseCode, label, accent) ->
                val isActive = currentPhase == phaseCode
                val isCompleted = phases.indexOfFirst { it.first == currentPhase } > index

                Surface(
                    color = when {
                        isActive -> accent.copy(alpha = 0.2f)
                        isCompleted -> Color(0xFF10B981).copy(alpha = 0.12f)
                        else -> Color.Transparent
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        when {
                            isActive -> accent
                            isCompleted -> Color(0xFF10B981).copy(alpha = 0.5f)
                            else -> Color(0xFF1E293B)
                        }
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "[0${index + 1}]",
                            color = if (isActive || isCompleted) accent else Color(0xFF475569),
                            fontFamily = RobotoMonoFont,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = label,
                            color = if (isActive) Color.White else if (isCompleted) Color(0xFFA7F3D0) else Color(0xFF64748B),
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

