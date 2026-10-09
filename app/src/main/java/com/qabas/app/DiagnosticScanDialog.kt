package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.delay

/**
 * 🛰️ نافذة الماسح والتشخيص الدقيق الفضائي (Spatial Precision Diagnostic Lens Dialog)
 * تصميم وفق دستور Cybernetic Glassmorphism + Linear Minimalist (Spatial HUD Tech).
 * ينفذ دور الكاميرا المحوري:
 * 1. مسح فوري بكسلي وبرمجي شامل لكافة مؤشرات الشاشة والنظام والموارد.
 * 2. رصد وتصنيف مواضع العطل وتداخل النصوص وضغط الذاكرة وانقطاع الشبكة.
 * 3. خيارات فورية: معالجة سريعة مقترحة، نسخ التقرير المكتمل بنسبة 100%، أو إرساله مباشرة للمطور مع اللقطة.
 */
@Composable
fun SpatialPrecisionDiagnosticDialog(
    telemetry: PrecisionDiagnosticEngine.DiagnosticTelemetry,
    onDismiss: () -> Unit,
    onOpenReportWithDiagnostics: (PrecisionDiagnosticEngine.DiagnosticTelemetry) -> Unit
) {
    val context = LocalContext.current
    var isScanning by remember { mutableStateOf(true) }
    var scanProgress by remember { mutableFloatStateOf(0f) }

    // HUD Scanning Animation
    LaunchedEffect(Unit) {
        AppSoundManager.playActivate()
        for (i in 1..100) {
            delay(12)
            scanProgress = i / 100f
        }
        isScanning = false
        AppSoundManager.playSuccess()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "DiagRadarTransition")
    val radarRotate by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "diagRadarRotate"
    )

    fun copyDiagnosticsReport() {
        AppSoundManager.playClick()
        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val markdown = PrecisionDiagnosticEngine.formatDiagnosticMarkdown(telemetry)
        cb.setPrimaryClip(ClipData.newPlainText("Qabas Precision Diagnostics", markdown))
        Toast.makeText(context, "تم نسخ التقرير التشخيصي الدقيق بنجاح 📋", Toast.LENGTH_SHORT).show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable { onDismiss() }
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassmorphicSurface(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth(0.96f)
                    .fillMaxHeight(0.92f)
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(26.dp),
                borderColor = Color(0xFF00E5FF).copy(alpha = 0.6f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp)
                ) {
                    // HUD Top Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White.copy(alpha = 0.75f))
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "مسبار التشخيص الدقيق 🔬",
                                color = Color(0xFF00E5FF),
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E5FF).copy(alpha = 0.2f))
                                    .border(1.dp, Color(0xFF00E5FF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier
                                        .size(13.dp)
                                        .rotate(if (isScanning) radarRotate else 0f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isScanning) {
                        // Scanning HUD animation
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(90.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E5FF).copy(alpha = 0.12f))
                                        .border(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Radar,
                                        contentDescription = null,
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier
                                            .size(46.dp)
                                            .rotate(radarRotate)
                                    )
                                }
                                Text(
                                    text = "جاري مسح البصمة التقنية للشاشة...",
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                LinearProgressIndicator(
                                    progress = { scanProgress },
                                    modifier = Modifier
                                        .width(200.dp)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = Color(0xFF00E5FF),
                                    trackColor = Color.White.copy(alpha = 0.15f)
                                )
                                Text(
                                    text = "${(scanProgress * 100).toInt()}% اكتمل فحص الذاكرة والأبعاد والشبكة",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontFamily = CairoFont,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else {
                        // Diagnostic Results Content
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Primary Diagnosis Banner Card
                            val issue = telemetry.detectedProbableIssue
                            val bannerColor = when (issue) {
                                PrecisionDiagnosticEngine.ProbableIssue.UI_OVERFLOW -> Color(0xFF00E5FF)
                                PrecisionDiagnosticEngine.ProbableIssue.MEMORY_PRESSURE -> Color(0xFFEF4444)
                                PrecisionDiagnosticEngine.ProbableIssue.NETWORK_UNAVAILABLE -> Color(0xFFF59E0B)
                                PrecisionDiagnosticEngine.ProbableIssue.AUDIO_STREAM_ISSUE -> Color(0xFFA855F7)
                                PrecisionDiagnosticEngine.ProbableIssue.GENERAL_BUG -> GoldPrimary
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = bannerColor.copy(alpha = 0.12f)),
                                border = BorderStroke(1.2.dp, bannerColor.copy(alpha = 0.65f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = bannerColor.copy(alpha = 0.25f),
                                            border = BorderStroke(0.8.dp, bannerColor)
                                        ) {
                                            Text(
                                                text = "النتيجة: ${issue.code}",
                                                color = bannerColor,
                                                fontFamily = CairoFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = issue.title,
                                                color = Color.White,
                                                fontFamily = CairoFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp
                                            )
                                            Text(issue.iconEmoji, fontSize = 16.sp)
                                        }
                                    }

                                    Text(
                                        text = issue.reason,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontFamily = NotoSansFont,
                                        fontSize = 11.5.sp,
                                        lineHeight = 17.sp
                                    )
                                }
                            }

                            // 1. Screen & Layout Matrix
                            SectionHeader(title = "مصفوفة الشاشة والأبعاد (Layout & DP)", icon = Icons.Default.AspectRatio, color = Color(0xFF00E5FF))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF131B2E).copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    DiagMetricRow("الشاشة الحالية", telemetry.screenName)
                                    DiagMetricRow(
                                        "الأبعاد (عرض × ارتفاع)",
                                        "${telemetry.widthDp}×${telemetry.heightDp} dp (${(telemetry.widthDp * (telemetry.densityDpi / 160f)).toInt()}×${(telemetry.heightDp * (telemetry.densityDpi / 160f)).toInt()} px)"
                                    )
                                    DiagMetricRow(
                                        "كثافة الشاشة (DPI)",
                                        "${telemetry.densityDpi} DPI (${"%.2f".format(telemetry.densityDpi / 160f)}x scale)"
                                    )
                                    DiagMetricRow(
                                        "مقياس خط النظام (Font Scale)",
                                        "${telemetry.fontScale}x ${if (telemetry.fontScale > 1.15f) "⚠️ (مكبّر قد يزاحم العناصر)" else "✓"}"
                                    )
                                    DiagMetricRow("تصنيف الشاشة واتجاهها", "${telemetry.windowSizeClass} - ${telemetry.orientation}")
                                }
                            }

                            // 2. System & Memory Health
                            SectionHeader(title = "حالة موارد الجهاز والذاكرة (System Health)", icon = Icons.Default.Memory, color = GoldPrimary)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF131B2E).copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    DiagMetricRow(
                                        "استهلاك الذاكرة (RAM)",
                                        "${telemetry.usedRamMb} MB من أصل ${telemetry.maxRamMb} MB ${if (telemetry.isLowMemory) "⚠️ (ضغط ذاكرة)" else "✓"}"
                                    )
                                    DiagMetricRow("التخزين الداخلي الحر", "${telemetry.freeInternalStorageMb} MB")
                                    DiagMetricRow("حالة الشبكة", telemetry.networkType)
                                    DiagMetricRow("الجهاز والنظام", "${telemetry.deviceModel} - ${telemetry.cpuAbi}")
                                    DiagMetricRow("إصدار تطبيق قبس", telemetry.appVersion)
                                }
                            }

                            // 3. Audio & Media State (if relevant)
                            if (telemetry.isAudioPlaying || !telemetry.activeAudioTrack.isNullOrBlank()) {
                                SectionHeader(title = "مشغل الوسائط والصوت", icon = Icons.Default.GraphicEq, color = Color(0xFFA855F7))
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF131B2E).copy(alpha = 0.85f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        DiagMetricRow("حالة المشغل", if (telemetry.isAudioPlaying) "يعمل بنشاط 🔊" else "متوقف مؤقتاً ⏸️")
                                        telemetry.activeAudioTrack?.let { DiagMetricRow("المقطع الحالي", it) }
                                    }
                                }
                            }

                            // 4. Recent Breadcrumbs Activity
                            if (telemetry.recentBreadcrumbs.isNotEmpty()) {
                                SectionHeader(title = "سجل التفاعلات الأخيرة (Breadcrumbs)", icon = Icons.Default.Timeline, color = Color(0xFF38BDF8))
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF0F172A),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        telemetry.recentBreadcrumbs.takeLast(5).forEach { crumb ->
                                            Text(
                                                text = "• $crumb",
                                                color = Color.White.copy(alpha = 0.75f),
                                                fontSize = 10.sp,
                                                fontFamily = CairoFont,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Bottom Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { copyDiagnosticsReport() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f))
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("نسخ التقرير 📋", color = Color(0xFF00E5FF), fontFamily = CairoFont, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    AppSoundManager.playClick()
                                    onDismiss()
                                    onOpenReportWithDiagnostics(telemetry)
                                },
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("إرسال البلاغ المشخص 🚀", color = DeepSlate, fontFamily = CairoFont, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        Text(
            text = title,
            color = color,
            fontFamily = CairoFont,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DiagMetricRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.65f),
            fontFamily = CairoFont,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            color = Color.White,
            fontFamily = CairoFont,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End
        )
    }
}
