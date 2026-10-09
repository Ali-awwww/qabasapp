package com.qabas.app

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch

enum class ChartType {
    HOURLY_AREA,
    WEEKLY_BAR
}

@Composable
fun EngagementAnalyticsChart(
    platformId: String = "tiktok",
    modifier: Modifier = Modifier,
    onSelectHour: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var chartType by remember { mutableStateOf(ChartType.HOURLY_AREA) }
    var insight by remember { mutableStateOf<PlatformAnalyticsInsight?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedPointIndex by remember { mutableIntStateOf(-1) }

    fun loadData() {
        coroutineScope.launch {
            isLoading = true
            val data = SocialAccountManager.fetchPlatformEngagementAnalytics(context, platformId)
            insight = data
            // Default highlight to peak hour
            val peakIndex = data.hourlyData.indexOfMaxBy { it.engagementPercent }
            if (selectedPointIndex == -1 && peakIndex >= 0) {
                selectedPointIndex = peakIndex
            }
            isLoading = false
        }
    }

    LaunchedEffect(platformId) {
        loadData()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1527)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF151B2B))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Title & Cloud Source & Chart Type Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "مخطط التفاعل والأوقات المثالية 📈",
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (insight?.isFromFirestore == true) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF5D76E).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (insight?.isFromFirestore == true) Color(0xFF10B981) else Color(0xFFF5D76E)
                            )
                        ) {
                            Text(
                                if (insight?.isFromFirestore == true) "Firestore سحابي نشط 🔥" else "تحليل الجمهور الشرعي 🧠",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                color = if (insight?.isFromFirestore == true) Color(0xFF34D399) else Color(0xFFFBBF24),
                                fontFamily = CairoFont,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "مبني على تفاعل المتابعين",
                            color = Color.Gray,
                            fontFamily = CairoFont,
                            fontSize = 10.sp
                        )
                    }
                }

                // Toggle Area vs Bar
                Row(
                    modifier = Modifier
                        .background(Color(0xFF151B2B), RoundedCornerShape(8.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Surface(
                        onClick = { chartType = ChartType.HOURLY_AREA },
                        shape = RoundedCornerShape(6.dp),
                        color = if (chartType == ChartType.HOURLY_AREA) GoldPrimary else Color.Transparent
                    ) {
                        Text(
                            "24 ساعة 🕒",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = if (chartType == ChartType.HOURLY_AREA) DeepSlate else Color.LightGray,
                            fontFamily = CairoFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        onClick = { chartType = ChartType.WEEKLY_BAR },
                        shape = RoundedCornerShape(6.dp),
                        color = if (chartType == ChartType.WEEKLY_BAR) GoldPrimary else Color.Transparent
                    ) {
                        Text(
                            "الأسبوع 📅",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = if (chartType == ChartType.WEEKLY_BAR) DeepSlate else Color.LightGray,
                            fontFamily = CairoFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading || insight == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = GoldPrimary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            } else {
                val data = insight!!

                // Summary Stats Row (Recharts style metric chips)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MetricChip(
                        title = "توقيت الذروة 🔥",
                        value = data.peakHourLabel,
                        color = Color(0xFF10B981),
                        modifier = Modifier.weight(1f)
                    )
                    MetricChip(
                        title = "أفضل يوم 🕌",
                        value = data.bestDayLabel.split(" ").firstOrNull() ?: "الجمعة",
                        color = GoldPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricChip(
                        title = "متوسط التفاعل 📊",
                        value = "${data.averageEngagementRate.toInt()}%",
                        color = Color(0xFF60A5FA),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Chart Canvas Area
                if (chartType == ChartType.HOURLY_AREA) {
                    HourlyAreaChartCanvas(
                        hourlyData = data.hourlyData,
                        selectedIndex = selectedPointIndex,
                        onSelectIndex = { index ->
                            selectedPointIndex = index
                            val item = data.hourlyData.getOrNull(index)
                            if (item != null) {
                                onSelectHour?.invoke(item.hour)
                            }
                        }
                    )
                } else {
                    WeeklyBarChartCanvas(
                        weeklyData = data.weeklyData
                    )
                }

                // Interactive Tooltip Card for Selected Point
                if (chartType == ChartType.HOURLY_AREA && selectedPointIndex in data.hourlyData.indices) {
                    val activePoint = data.hourlyData[selectedPointIndex]
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF151B2B),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(GoldPrimary, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "الساعة: ${activePoint.label}",
                                        color = GoldPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                                Text(
                                    activePoint.description,
                                    color = Color.LightGray,
                                    fontFamily = CairoFont,
                                    fontSize = 10.sp
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (activePoint.engagementPercent >= 90) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF3B82F6).copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (activePoint.engagementPercent >= 90) Color(0xFF10B981) else Color(0xFF3B82F6)
                                )
                            ) {
                                Text(
                                    "${activePoint.engagementPercent}% تفاعل",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    color = if (activePoint.engagementPercent >= 90) Color(0xFF34D399) else Color(0xFF93C5FD),
                                    fontFamily = CairoFont,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricChip(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF131D33),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF151B2B)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, color = color, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun HourlyAreaChartCanvas(
    hourlyData: List<EngagementDataPoint>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit
) {
    if (hourlyData.isEmpty()) return

    val maxVal = 100f
    val minVal = 0f

    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 800),
        label = "chartAnim"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .pointerInput(hourlyData) {
                detectTapGestures { offset ->
                    val stepX = size.width / (hourlyData.size - 1).coerceAtLeast(1)
                    val clickedIndex = (offset.x / stepX).toInt().coerceIn(0, hourlyData.size - 1)
                    onSelectIndex(clickedIndex)
                }
            }
            .pointerInput(hourlyData) {
                detectDragGestures { change, _ ->
                    val stepX = size.width / (hourlyData.size - 1).coerceAtLeast(1)
                    val draggedIndex = (change.position.x / stepX).toInt().coerceIn(0, hourlyData.size - 1)
                    onSelectIndex(draggedIndex)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val bottomPadding = 24.dp.toPx()
            val graphHeight = height - bottomPadding
            val stepX = width / (hourlyData.size - 1).coerceAtLeast(1)

            // 1. Draw Grid Lines (Recharts subtle dotted grid)
            val gridLevels = listOf(0.25f, 0.5f, 0.75f, 1f)
            gridLevels.forEach { lvl ->
                val y = graphHeight * (1f - lvl)
                drawLine(
                    color = Color(0xFF151B2B),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
            }

            // 2. Build Bezier Path for Smooth Area Chart
            val path = Path()
            val fillPath = Path()

            val points = hourlyData.mapIndexed { index, point ->
                val x = index * stepX
                val normalizedY = (point.engagementPercent - minVal) / (maxVal - minVal)
                val y = graphHeight * (1f - (normalizedY * animProgress))
                Offset(x, y)
            }

            if (points.isNotEmpty()) {
                path.moveTo(points[0].x, points[0].y)
                fillPath.moveTo(points[0].x, graphHeight)
                fillPath.lineTo(points[0].x, points[0].y)

                for (i in 0 until points.size - 1) {
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    val cx = (p0.x + p1.x) / 2f
                    path.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                    fillPath.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                }

                fillPath.lineTo(points.last().x, graphHeight)
                fillPath.close()

                // Gradient Fill (Recharts Area Gradient)
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            GoldPrimary.copy(alpha = 0.35f),
                            GoldPrimary.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = graphHeight
                    )
                )

                // Smooth Golden Stroke Line
                drawPath(
                    path = path,
                    color = GoldPrimary,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // 3. Draw Peak Points and Indicator
            val peakIndex = hourlyData.indexOfMaxBy { it.engagementPercent }
            if (peakIndex in points.indices) {
                val peakPoint = points[peakIndex]
                // Outer Pulse Halo
                drawCircle(
                    color = Color(0xFF10B981).copy(alpha = 0.3f),
                    radius = 8.dp.toPx(),
                    center = peakPoint
                )
                // Inner Peak Dot
                drawCircle(
                    color = Color(0xFF10B981),
                    radius = 4.dp.toPx(),
                    center = peakPoint
                )
            }

            // 4. Draw Selected Scrubber Vertical Rule
            if (selectedIndex in points.indices) {
                val selPoint = points[selectedIndex]
                drawLine(
                    color = GoldPrimary.copy(alpha = 0.8f),
                    start = Offset(selPoint.x, 0f),
                    end = Offset(selPoint.x, graphHeight),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                )

                // Glow ring
                drawCircle(
                    color = GoldPrimary.copy(alpha = 0.4f),
                    radius = 7.dp.toPx(),
                    center = selPoint
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.5.dp.toPx(),
                    center = selPoint
                )
            }
        }

        // 5. Time Axis Labels at the bottom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val keyHours = listOf("12ص", "4ص", "8ص", "12م", "4م", "8م", "11م")
            keyHours.forEach { lbl ->
                Text(
                    text = lbl,
                    color = Color.Gray,
                    fontFamily = CairoFont,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun WeeklyBarChartCanvas(
    weeklyData: List<DayEngagementData>
) {
    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 700),
        label = "weeklyAnim"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val bottomPadding = 24.dp.toPx()
            val graphHeight = height - bottomPadding
            val count = weeklyData.size
            val barWidth = (width / count) * 0.45f
            val slotWidth = width / count

            weeklyData.forEachIndexed { index, day ->
                val x = index * slotWidth + (slotWidth - barWidth) / 2f
                val barHeight = (day.score / 100f) * graphHeight * animProgress
                val y = graphHeight - barHeight

                val isBest = day.isBestDay

                // Bar Background Slot
                drawRoundRect(
                    color = Color(0xFF151B2B).copy(alpha = 0.5f),
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, graphHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )

                // Filled Bar
                drawRoundRect(
                    brush = if (isBest) {
                        Brush.verticalGradient(
                            colors = listOf(GoldPrimary, Color(0xFFB8860B))
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
                        )
                    },
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }
        }

        // Day Labels Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            weeklyData.forEach { day ->
                Text(
                    text = day.shortName,
                    color = if (day.isBestDay) GoldPrimary else Color.LightGray,
                    fontFamily = CairoFont,
                    fontWeight = if (day.isBestDay) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private inline fun <T> List<T>.indexOfMaxBy(selector: (T) -> Int): Int {
    if (isEmpty()) return -1
    var maxVal = selector(this[0])
    var maxIndex = 0
    for (i in 1 until size) {
        val v = selector(this[i])
        if (v > maxVal) {
            maxVal = v
            maxIndex = i
        }
    }
    return maxIndex
}
