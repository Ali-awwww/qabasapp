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
import com.qabas.app.ui.theme.*

/**
 * عتاد الواجهة السيبرانية لقسم الوكيل الذكي (Agent Spatial HUD Kit)
 * لغة تصميم هندسية نيونية موحّدة: شبكة خلفية، لوحات HUD بزوايا ليزر،
 * وصفوف قياس (telemetry) بخط أحادي المسافة. كل القيم المعروضة حقيقية ومحسوبة لحظياً.
 */

/**
 * خلفية شبكية سيبرانية مع توهج أفقى خفيف — تُرسم خلف المحتوى وتقف ثابتة أثناء التمرير.
 */
fun Modifier.cyberGridBackground(
    line: Color = PresetPrimary,
    cellPx: Float = 44f
): Modifier = this.drawBehind {
    drawRect(DeepSlate)
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
 * تعريف تبويب واحد في شريط الـ HUD.
 */
data class AgentTab(
    val code: String,
    val label: String,
    val icon: ImageVector,
    val accent: Color
)

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
    val theme = QabasTheme.current
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
            theme.primary.copy(alpha = 0.75f),
            theme.success.copy(alpha = 0.85f),
            theme.primary.copy(alpha = 0.35f)
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.background.copy(alpha = 0.92f))
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
                                isPinging -> theme.primary.copy(alpha = pulseAlpha)
                                isConnected -> theme.success.copy(alpha = pulseAlpha)
                                else -> theme.destructive
                            }
                        )
                )
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(
                        text = "الاستجابة",
                        fontSize = 12.sp,
                        fontFamily = CairoFont,
                        color = theme.textSecondary,
                        maxLines = 1
                    )
                    Text(
                        text = if (isPinging) "فحص..." else if (isConnected) "${latencyMs}ms" else "مفصول",
                        fontSize = 12.sp,
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected) theme.success else theme.destructive,
                        maxLines = 1
                    )
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(26.dp)
                    .background(theme.secondarySurface)
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
                    tint = theme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(5.dp))
                Column {
                    Text(
                        text = if (isOfflineMode) "نموذج محلي" else "النموذج النشط",
                        fontSize = 12.sp,
                        fontFamily = CairoFont,
                        color = theme.textSecondary,
                        maxLines = 1
                    )
                    Text(
                        text = activeModelName,
                        fontSize = 12.sp,
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold,
                        color = theme.primary,
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
                    .background(theme.secondarySurface)
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
                    tint = theme.success,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(5.dp))
                Column {
                    Text(
                        text = "الذاكرة الحية",
                        fontSize = 12.sp,
                        fontFamily = CairoFont,
                        color = theme.textSecondary,
                        maxLines = 1
                    )
                    Text(
                        text = "${usedMemoryMb}/${maxMemoryMb}M",
                        fontSize = 12.sp,
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold,
                        color = theme.success,
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
                    .background(theme.cardSurface)
                    .border(1.dp, theme.primary.copy(alpha = 0.35f), CircleShape)
                    .testTag("telemetry_refresh_btn")
            ) {
                if (isPinging) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = theme.primary,
                        strokeWidth = 1.5.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "تحديث التيليميتري اللحظي",
                        tint = theme.primary,
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
    val theme = QabasTheme.current
    val modules = listOf(
        AgentTab("01", "نظرة عامة", Icons.Default.Dashboard, theme.primary),
        AgentTab("02", "مساحة العمل", Icons.Default.Code, theme.accent),
        AgentTab("03", "الكونسول", Icons.Default.Terminal, theme.success),
        AgentTab("04", "المشاريع", Icons.Default.FolderOpen, theme.primary),
        AgentTab("05", "النشاط", Icons.Default.History, theme.accent),
        AgentTab("06", "الأدوات", Icons.Default.Handyman, theme.success)
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
                                theme.success.copy(alpha = animatedBorderAlpha)
                            )
                        )
                    } else {
                        Brush.horizontalGradient(
                            listOf(
                                theme.secondarySurface,
                                theme.secondarySurface
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
                            color = if (isSelected) module.accent else theme.textTertiary
                        )
                        Spacer(Modifier.width(3.dp))
                        Icon(
                            imageVector = module.icon,
                            contentDescription = null,
                            tint = if (isSelected) module.accent else theme.textSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = module.label,
                        fontSize = 12.sp,
                        fontFamily = CairoFont,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) theme.textPrimary else theme.textSecondary,
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
 * شريط مراحل التفكير الحي (Reasoning Flow HUD)
 * يوضح بدقة المرحلة الحالية: تحليل -> تخطيط -> كود -> فحص
 */
@Composable
fun CyberReasoningFlowHud(
    currentPhase: String, // "ANALYZE", "PLAN", "CODE", "VERIFY", "IDLE"
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
    val phases = listOf(
        Triple("ANALYZE", "تحليل 🔍", theme.primary),
        Triple("PLAN", "تخطيط 📐", theme.accent),
        Triple("CODE", "تعديل 💻", theme.success),
        Triple("VERIFY", "تدقيق 🧪", theme.warning)
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = theme.background,
        border = BorderStroke(1.dp, theme.secondarySurface)
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
                        isCompleted -> theme.success.copy(alpha = 0.12f)
                        else -> Color.Transparent
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        when {
                            isActive -> accent
                            isCompleted -> theme.success.copy(alpha = 0.5f)
                            else -> theme.secondarySurface
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
                            color = if (isActive || isCompleted) accent else theme.separator,
                            fontFamily = RobotoMonoFont,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = label,
                            color = if (isActive) theme.textPrimary else if (isCompleted) theme.success else theme.textTertiary,
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

