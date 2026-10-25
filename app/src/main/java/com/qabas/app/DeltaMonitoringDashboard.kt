package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 🎛️ DeltaMonitoringDashboard — لوحة مراقبة وتحليل أعطال تحديثات الدلتا (خاصة بالمطور).
 * تتصل بمجموعة Firestore /update_failures وتستعرض:
 * 1) الأخطاء الأكثر شيوعاً مجمعة مع عدد مرات التكرار وعدد الأجهزة المتأثرة.
 * 2) مسارات الأخطاء المنقّاة بواسطة smart-exception.
 * 3) تتبع versionCode لكل جهاز ومرحلة الفشل.
 */
@Composable
fun DeltaMonitoringDashboard(
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDeveloper = remember { AdminGuard.isDashboardAccessAllowed(context) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Common Errors, 1: Live Stream
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var commonErrors by remember { mutableStateOf<List<UpdateTelemetryManager.DeltaErrorSummary>>(emptyList()) }
    var recentFailures by remember { mutableStateOf<List<UpdateTelemetryManager.UpdateErrorReport>>(emptyList()) }

    fun refreshData() {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            val commonResult = UpdateTelemetryManager.fetchCommonDeltaFailures(context)
            val recentResult = UpdateTelemetryManager.fetchRecentFailures(context, 40)

            if (commonResult.isSuccess) {
                commonErrors = commonResult.getOrDefault(emptyList())
            }
            if (recentResult.isSuccess) {
                recentFailures = recentResult.getOrDefault(emptyList())
            } else {
                errorMessage = recentResult.exceptionOrNull()?.localizedMessage ?: "تعذر جلب سجلات المراقبة من Firestore"
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        if (isDeveloper) {
            refreshData()
        }
    }

    if (!isDeveloper) {
        GlassmorphicSurface(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            backgroundColor = CardSurface.copy(alpha = 0.85f),
            borderColor = AiDanger.copy(alpha = 0.4f)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = AiDanger, modifier = Modifier.size(36.dp))
                Text("لوحة مراقبة التحديثات مخصصة للمطور فقط", color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("يتطلب حساب مطور أو مالك مسجل في صلاحيات التطبيق للوصول إلى تحليلات Firestore.", color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp)
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Dashboard Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(AiCyan)
                )
                Text(
                    "// OTA_DELTA_MONITORING_DASHBOARD",
                    color = AiCyan,
                    fontSize = 11.sp,
                    fontFamily = RobotoMonoFont,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { refreshData() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = GoldPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (onClose != null) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Summary KPI Metrics Strip
        val totalFailures = commonErrors.sumOf { it.count }
        val affectedDevicesCount = recentFailures.map { it.deviceId }.toSet().size
        val topPhase = commonErrors.firstOrNull()?.phase ?: "N/A"

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiMetricCard(
                title = "إجمالي الأعطال",
                value = "$totalFailures",
                accentColor = if (totalFailures > 0) AiDanger else AiNeonGreen,
                modifier = Modifier.weight(1f)
            )
            KpiMetricCard(
                title = "الأجهزة المتأثرة",
                value = "$affectedDevicesCount",
                accentColor = AiCyan,
                modifier = Modifier.weight(1f)
            )
            KpiMetricCard(
                title = "الطور الأكثر تعثراً",
                value = topPhase,
                accentColor = GoldPrimary,
                modifier = Modifier.weight(1.2f)
            )
        }

        // Navigation Tabs (Linear Minimalist HUD)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val tabs = listOf("الأخطاء الأكثر شيوعاً (${commonErrors.size})", "السجل الحي (${recentFailures.size})")
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) GoldPrimary.copy(alpha = 0.15f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (isSelected) GoldPrimary.copy(alpha = 0.5f) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedTab = index }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        title,
                        color = if (isSelected) GoldPrimary else TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = CairoFont,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Content Area
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = GoldPrimary, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
        } else if (errorMessage != null && recentFailures.isEmpty() && commonErrors.isEmpty()) {
            GlassmorphicSurface(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = CardSurface.copy(alpha = 0.7f),
                borderColor = AiDanger.copy(alpha = 0.4f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(errorMessage ?: "", color = AiDanger, fontSize = 12.sp, fontFamily = CairoFont)
                    Button(onClick = { refreshData() }, colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)) {
                        Text("إعادة المحاولة", color = DeepSlate, fontFamily = CairoFont, fontSize = 12.sp)
                    }
                }
            }
        } else {
            when (selectedTab) {
                0 -> CommonErrorsList(commonErrors)
                1 -> LiveFailuresList(recentFailures)
            }
        }
    }
}

@Composable
private fun KpiMetricCard(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    GlassmorphicSurface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        backgroundColor = CardSurface.copy(alpha = 0.75f),
        borderColor = accentColor.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, color = TextSecondary, fontSize = 10.sp, fontFamily = CairoFont)
            Text(
                value,
                color = accentColor,
                fontSize = 14.sp,
                fontFamily = RobotoMonoFont,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CommonErrorsList(errors: List<UpdateTelemetryManager.DeltaErrorSummary>) {
    if (errors.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("لا توجد أعطال مسجلة حتى الآن — جميع عمليات الدلتا مستقرة ✅", color = AiNeonGreen, fontFamily = CairoFont, fontSize = 12.sp)
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(errors) { error ->
            CommonErrorItem(error)
        }
    }
}

@Composable
private fun CommonErrorItem(error: UpdateTelemetryManager.DeltaErrorSummary) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }

    GlassmorphicSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = CardSurface.copy(alpha = 0.85f),
        borderColor = AiDanger.copy(alpha = 0.4f)
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .clickable { expanded = !expanded },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(GoldPrimary.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(error.phase, color = GoldPrimary, fontSize = 10.sp, fontFamily = RobotoMonoFont, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "${error.count} تكرار",
                        color = AiDanger,
                        fontSize = 11.sp,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    "${error.deviceCount} أجهزة مختلفة",
                    color = AiCyan,
                    fontSize = 10.sp,
                    fontFamily = CairoFont
                )
            }

            Text(
                error.sampleErrorMessage,
                color = TextPrimary,
                fontSize = 12.sp,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Medium,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "الإصدارات: ${error.affectedTargetVersions.joinToString(", ")}",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = RobotoMonoFont
                )
                Text(
                    "آخر رصد: ${dateFmt.format(error.lastSeen)}",
                    color = TextSecondary.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    fontFamily = RobotoMonoFont
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF090D16))
                            .padding(8.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            error.sampleStackTrace,
                            color = Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            fontFamily = RobotoMonoFont
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val clip = ClipData.newPlainText("Error Trace", "${error.sampleErrorMessage}\n\n${error.sampleStackTrace}")
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(clip)
                            Toast.makeText(context, "تم نسخ مسار العطل 📋", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("نسخ التتبع الكامل", fontFamily = CairoFont, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveFailuresList(failures: List<UpdateTelemetryManager.UpdateErrorReport>) {
    if (failures.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("لا توجد سجلات حية مسجلة حالياً", color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp)
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(failures) { item ->
            LiveFailureCard(item)
        }
    }
}

@Composable
private fun LiveFailureCard(item: UpdateTelemetryManager.UpdateErrorReport) {
    val context = LocalContext.current
    var showTrace by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.US) }

    GlassmorphicSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        backgroundColor = CardSurface.copy(alpha = 0.7f),
        borderColor = Color.White.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(item.phase, color = GoldSecondary, fontSize = 10.sp, fontFamily = RobotoMonoFont, fontWeight = FontWeight.Bold)
                    Text("• ${item.deviceAbi}", color = AiCyan, fontSize = 10.sp, fontFamily = RobotoMonoFont)
                }
                Text(dateFmt.format(Date(item.timestamp)), color = TextSecondary, fontSize = 9.sp, fontFamily = RobotoMonoFont)
            }

            Text(
                "جهاز: ${item.deviceId.take(14)}... | v${item.currentVersionCode} ➔ v${item.targetVersionCode} (فشل ${item.failureCount}x)",
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = RobotoMonoFont
            )

            Text(
                item.errorMessage,
                color = TextPrimary,
                fontSize = 11.sp,
                fontFamily = CairoFont,
                maxLines = if (showTrace) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (showTrace) "إخفاء التفاصيل" else "عرض تفاصيل الخطأ",
                    color = AiCyanLight,
                    fontSize = 10.sp,
                    fontFamily = CairoFont,
                    modifier = Modifier.clickable { showTrace = !showTrace }
                )

                IconButton(
                    onClick = {
                        val clip = ClipData.newPlainText("Qabas Error", UpdateTelemetryManager.formatMarkdownReport(item))
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(clip)
                        Toast.makeText(context, "تم نسخ التقرير 📋", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextSecondary, modifier = Modifier.size(14.dp))
                }
            }

            AnimatedVisibility(visible = showTrace) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF090D16))
                        .padding(6.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(item.cleanStackTrace, color = Color(0xFF94A3B8), fontSize = 9.sp, fontFamily = RobotoMonoFont)
                }
            }
        }
    }
}
