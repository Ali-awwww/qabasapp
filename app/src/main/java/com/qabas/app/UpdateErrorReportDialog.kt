package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

/**
 * 🚨 UpdateErrorReportDialog — نافذة تشخيص أخطاء التحديث والدلتا الفورية.
 * تقضي تماماً على الفشل الصامت، وتعرض تفاصيل الاستثناء المنقّى بواسطة smart-exception
 * مع إمكانية نسخ التقرير البرمجي والتحويل المباشر للتنزيل الكامل.
 */
@Composable
fun UpdateErrorReportDialog(
    report: UpdateTelemetryManager.UpdateErrorReport,
    onDismiss: () -> Unit,
    onFallbackFullDownload: () -> Unit
) {
    val context = LocalContext.current
    var showTrace by remember { mutableStateOf(false) }

    val pulse by rememberInfiniteTransition(label = "errPulse").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        GlassmorphicSurface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .wrapContentHeight()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            shape = RoundedCornerShape(28.dp),
            backgroundColor = CardSurface.copy(alpha = 0.92f),
            borderColor = AiDanger.copy(alpha = 0.65f)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // HUD Subsystem Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(AiDanger.copy(alpha = pulse))
                        )
                        Text(
                            "// OTA_DIAGNOSTIC_SUBSYSTEM",
                            color = AiDanger,
                            fontSize = 11.sp,
                            fontFamily = RobotoMonoFont,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AiDanger.copy(alpha = 0.15f))
                            .border(1.dp, AiDanger.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "فشل: ${report.failureCount}x",
                            color = AiDanger,
                            fontSize = 10.sp,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Error Icon & Title
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(AiDanger.copy(alpha = 0.12f))
                        .border(1.5.dp, AiDanger.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.BugReport,
                        contentDescription = "Report",
                        tint = AiDanger,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    "تعذّر تطبيق التحديث التفاضلي",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    "تم التقاط وتصنيف الخطأ تلقائياً بواسطة محرك التشخيص الذكي لمنع الفشل الصامت.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = CairoFont,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                // HUD Metadata Grid
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("طور الفشل (Phase):", color = TextSecondary, fontSize = 11.sp, fontFamily = CairoFont)
                        Text(report.phase, color = GoldSecondary, fontSize = 11.sp, fontFamily = RobotoMonoFont, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("الإصدار الحالي / المستهدف:", color = TextSecondary, fontSize = 11.sp, fontFamily = CairoFont)
                        Text("v${report.currentVersionName} ➔ v${report.targetVersionName}", color = TextPrimary, fontSize = 11.sp, fontFamily = RobotoMonoFont)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("معمارية المعالج (ABI):", color = TextSecondary, fontSize = 11.sp, fontFamily = CairoFont)
                        Text(report.deviceAbi, color = AiCyan, fontSize = 11.sp, fontFamily = RobotoMonoFont)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("معرّف الجهاز المسجل:", color = TextSecondary, fontSize = 11.sp, fontFamily = CairoFont)
                        Text(report.deviceId.take(16) + "...", color = TextSecondary, fontSize = 10.sp, fontFamily = RobotoMonoFont)
                    }
                }

                // Error Message Box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(AiDanger.copy(alpha = 0.08f))
                        .border(1.dp, AiDanger.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AiDanger, modifier = Modifier.size(16.dp))
                        Text("السبب المباشر (Root Cause):", color = AiDanger, fontSize = 11.sp, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        report.errorMessage,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = CairoFont,
                        lineHeight = 16.sp
                    )
                }

                // Expandable Smart Stack Trace
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF070A11))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTrace = !showTrace }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Code,
                                contentDescription = null,
                                tint = AiCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "المسار البرمجي المنقّى (Smart Trace)",
                                color = AiCyanLight,
                                fontSize = 11.sp,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Icon(
                            if (showTrace) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    AnimatedVisibility(visible = showTrace) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .horizontalScroll(rememberScrollState())
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                report.cleanStackTrace,
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontFamily = RobotoMonoFont,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                // Copy Diagnostic Report Button
                OutlinedButton(
                    onClick = {
                        val clip = ClipData.newPlainText("Qabas Update Diagnostic", UpdateTelemetryManager.formatMarkdownReport(report))
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(clip)
                        Toast.makeText(context, "تم نسخ تقرير التشخيص بالكامل 📋", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AiCyan),
                    border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(AiCyan.copy(alpha = 0.5f), AiCyanLight.copy(alpha = 0.3f))))
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("نسخ تقرير العطل للمطور (Markdown)", fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Fallback action button & Dismiss
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Dismiss
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) {
                        Text("إغلاق", fontFamily = CairoFont, fontSize = 13.sp)
                    }

                    // Fallback to full download
                    Button(
                        onClick = {
                            onDismiss()
                            onFallbackFullDownload()
                        },
                        modifier = Modifier
                            .weight(1.6f)
                            .height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldPrimary,
                            contentColor = DeepSlate
                        )
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "تنزيل الحزمة الكاملة",
                            fontFamily = CairoFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
