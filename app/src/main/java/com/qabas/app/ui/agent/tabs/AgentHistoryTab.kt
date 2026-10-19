package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat


private enum class TaskHistoryFilter(val label: String, val color: Color) {
    ALL("الكل", GoldPrimary),
    COMPLETED("تم بنجاح ✓", Color(0xFF10B981)),
    IN_PROGRESS("قيد التنفيذ ⚡", Color(0xFF00F0FF)),
    FAILED("فشل ✗", Color(0xFFEF4444))
}

@Composable
internal fun TaskHistoryLogTab(context: Context) {
    val clipboard = LocalClipboardManager.current
    var runs by remember { mutableStateOf(AgentRunLog.load(context)) }
    var expandedTime by remember { mutableStateOf<Long?>(null) }
    var selectedFilter by remember { mutableStateOf(TaskHistoryFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val okCount = runs.count { it.ok }
    val failCount = runs.count { !it.ok }
    val totalCount = runs.size
    val successRate = if (totalCount > 0) (okCount * 100) / totalCount else 100

    val infiniteTransition = rememberInfiniteTransition(label = "TaskHistorySync")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "PulseAlpha"
    )

    // تصفية السجل حسب الحالة والبحث
    val filteredRuns = remember(runs, selectedFilter, searchQuery) {
        runs.filter { run ->
            val matchesFilter = when (selectedFilter) {
                TaskHistoryFilter.ALL -> true
                TaskHistoryFilter.COMPLETED -> run.ok
                TaskHistoryFilter.FAILED -> !run.ok
                TaskHistoryFilter.IN_PROGRESS -> false
            }
            val matchesQuery = searchQuery.isBlank() ||
                run.title.contains(searchQuery, ignoreCase = true) ||
                run.summary.contains(searchQuery, ignoreCase = true) ||
                run.tools.any { it.contains(searchQuery, ignoreCase = true) }
            matchesFilter && matchesQuery
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ==========================================
        // 📊 لوحة المؤشرات البصرية والإحصائيات الهندسية المتناغمة (Spatial Telemetry HUD)
        // ==========================================
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xEE090F1F),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF00F0FF).copy(alpha = 0.6f),
                            Color(0xFF10B981).copy(alpha = 0.4f),
                            Color(0xFF1E2D4A).copy(alpha = 0.5f)
                        )
                    )
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00F0FF))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "MISSION TELEMETRY LOG",
                                    color = Color(0xFF00F0FF),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "سجل العمليات والمهام البرمجية المستقلة",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }

                        if (runs.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    AgentRunLog.clear(context)
                                    runs = emptyList()
                                    expandedTime = null
                                    Toast.makeText(context, "تم مسح سجل العمليات بالكامل 🗑️", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "مسح السجل", tint = Color(0xFFEF4444))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ==========================================
                    // 📐 شبكة متناسقة هندسياً 2x2 للكبسولات الإحصائية
                    // ==========================================
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // الصف الأول: مكتملة + قيد التنفيذ
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // كبسولة 1: تم بنجاح (COMPLETED)
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = Color(0xFF041B13),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF10B981)))
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text("تم بنجاح", color = Color(0xFF10B981), fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text("$okCount مهمة", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                                }
                            }

                            // كبسولة 2: قيد التنفيذ (IN PROGRESS)
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = Color(0xFF05182C),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF00F0FF).copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF00F0FF).copy(alpha = pulseAlpha))
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text("قيد التنفيذ", color = Color(0xFF00F0FF), fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text("0 نشطة", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF00F0FF), modifier = Modifier.size(20.dp))
                                }
                            }
                        }

                        // الصف الثاني: فشل + نسبة النجاح
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // كبسولة 3: أخطاء (FAILED)
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = Color(0xFF22080D),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text("أخطاء", color = Color(0xFFEF4444), fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text("$failCount خطأ", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                                }
                            }

                            // كبسولة 4: معدل الإنجاز (SUCCESS RATE)
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = Color(0xFF1A1505),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(GoldPrimary))
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text("نسبة الإنجاز", color = GoldPrimary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text("$successRate%", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Icon(Icons.Default.TrendingUp, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 🔍 شريط البحث وفلاتر الحالة الهندسية (Search & Filter Deck)
        // ==========================================
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("ابحث في سجل المهام أو الأدوات...", color = Color(0xFF64748B), fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = CairoFont, fontSize = 12.sp, color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF060B16),
                        unfocusedContainerColor = Color(0xFF060B16),
                        focusedBorderColor = Color(0xFF00F0FF),
                        unfocusedBorderColor = Color(0xFF1E2D4A)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                // فلاتر الحالة الأربعة بتوزيع متناسق
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TaskHistoryFilter.values().forEach { filter ->
                        val isSelected = selectedFilter == filter
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedFilter = filter },
                            color = if (isSelected) filter.color.copy(alpha = 0.18f) else Color(0xFF060B16),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isSelected) filter.color else Color(0xFF1E2D4A))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelected) {
                                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(filter.color))
                                    Spacer(modifier = Modifier.width(5.dp))
                                }
                                Text(
                                    filter.label,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    fontFamily = CairoFont,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 📋 قائمة بطاقات سجل المهام بتصميم Spatial HUD Glassmorphic
        // ==========================================
        if (filteredRuns.isEmpty()) {
            item {
                Surface(
                    color = Color(0xDD080E1C),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E2D4A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.AssignmentLate, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (runs.isEmpty()) "لا توجد مهام مسجلة حتى الآن."
                            else "لا توجد نتائج تطابق خيارات التصفية والبحث الحالية.",
                            color = Color(0xFFCBD5E1),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "يمكنك تشغيل مهمة جديدة من تبويب «[01] ⚡ التشغيل المباشر» لتسجيلها ومتابعتها هنا تلقائياً.",
                            color = Color(0xFF64748B),
                            fontFamily = CairoFont,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        } else {
            items(filteredRuns) { run ->
                val isOk = run.ok
                val statusColor = if (isOk) Color(0xFF10B981) else Color(0xFFEF4444)
                val statusBg = if (isOk) Color(0xFF041610) else Color(0xFF1D060A)
                val statusLabel = if (isOk) "مكتمل ✓" else "فشل ✗"
                val statusIcon = if (isOk) Icons.Default.CheckCircle else Icons.Default.ErrorOutline
                val isExpanded = expandedTime == run.time

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { expandedTime = if (isExpanded) null else run.time },
                    color = statusBg.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(
                                statusColor.copy(alpha = 0.6f),
                                statusColor.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        )
                    )
                ) {
                    Column(modifier = Modifier.padding(13.dp)) {
                        // شريط رأس البطاقة: العنوان والمؤشر البصري الواضح للحالة
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        run.title,
                                        color = Color.White,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US).format(Date(run.time)),
                                        color = Color(0xFF64748B),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = statusColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f))
                                ) {
                                    Text(
                                        statusLabel,
                                        color = statusColor,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                IconButton(
                                    onClick = {
                                        AgentRunLog.deleteRun(context, run.time)
                                        runs = AgentRunLog.load(context)
                                        if (expandedTime == run.time) expandedTime = null
                                        Toast.makeText(context, "تم حذف هذه الجولة من السجل 🗑️", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "حذف هذه الجولة",
                                        tint = Color(0xFFEF4444).copy(alpha = 0.8f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                Icon(
                                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // شرائح الأدوات المستخدمة
                        if (run.tools.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                run.tools.forEach { t ->
                                    Surface(
                                        color = Color(0xFF0D1527),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(0.5.dp, Color(0xFF1E2D4A))
                                    ) {
                                        Text(
                                            t,
                                            color = Color(0xFF94A3B8),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // التفاصيل الموسعة عند الضغط على البطاقة
                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color(0xFF1E2D4A).copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.height(8.dp))

                            if (run.summary.isNotBlank()) {
                                Text("ملخص نتيجة العملية:", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(3.dp))
                                Surface(
                                    color = Color(0xFF030712),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        run.summary,
                                        color = Color(0xFFCBD5E1),
                                        fontFamily = CairoFont,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }

                            if (!run.ok && run.error.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("نص الخطأ التشخيصي (Diagnostic Error):", color = Color(0xFFEF4444), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(3.dp))
                                Surface(
                                    color = Color(0xFF030712),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        run.error,
                                        color = Color(0xFFFCA5A5),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = {
                                        val text = buildString {
                                            appendLine("تقرير عملية الوكيل البرمجي:")
                                            appendLine("المهمة: ${run.title}")
                                            appendLine("الحالة: $statusLabel")
                                            appendLine("التاريخ: ${SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(run.time))}")
                                            appendLine("الأدوات: ${run.tools.joinToString(", ")}")
                                            appendLine("الملخص: ${run.summary}")
                                            if (run.error.isNotBlank()) appendLine("الخطأ: ${run.error}")
                                        }
                                        clipboard.setText(AnnotatedString(text))
                                        Toast.makeText(context, "تم نسخ تقرير المهمة إلى الحافظة 📋", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF152238)),
                                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("نسخ التقرير 📋", color = Color.White, fontFamily = CairoFont, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

