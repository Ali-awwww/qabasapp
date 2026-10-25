package com.qabas.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.SystemUpdate
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
import com.qabas.app.ui.theme.*
import com.qabas.app.ui.components.GlassmorphicSurface

/**
 * 📢 UpdateNotificationDialog — نافذة تنبيه بوجود تحديث جديد.
 * تظهر عند الإقلاع وتوفر خيارات التحديث الفوري أو التذكير لاحقاً.
 */
@Composable
fun UpdateNotificationDialog(
    update: UpdateManager.UpdateInfo,
    downloadProgress: UpdateManager.DownloadProgress?,
    onDismiss: () -> Unit,
    onInstall: () -> Unit
) {
    val patchState by PatchManager.patchState.collectAsState()
    
    val pulse by rememberInfiniteTransition(label = "updatePulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    // منع الإغلاق لو كان التحديث إجبارياً
    val canDismiss = !update.isForce

    Dialog(
        onDismissRequest = { if (canDismiss) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = canDismiss,
            dismissOnClickOutside = canDismiss
        )
    ) {
        GlassmorphicSurface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .wrapContentHeight()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            shape = RoundedCornerShape(32.dp),
            backgroundColor = CardSurface.copy(alpha = 0.85f),
            borderColor = if (update.isForce) Color(0xFFEF4444).copy(alpha = 0.7f) else GoldPrimary.copy(alpha = 0.6f)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with pulsing indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (update.isForce) Color(0xFFEF4444).copy(alpha = pulse) else GoldPrimary.copy(alpha = pulse))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        if (update.isForce) "// تحديث إجباري مطلوب" else "// تحديث النظام متاح",
                        color = if (update.isForce) Color(0xFFEF4444) else GoldPrimary.copy(alpha = 0.9f),
                        fontSize = 10.sp,
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }

                // Icon Box
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(if (update.isForce) Color(0xFFEF4444).copy(alpha = 0.15f) else GoldPrimary.copy(alpha = 0.15f))
                        .border(1.5.dp, if (update.isForce) Color(0xFFEF4444).copy(alpha = 0.5f) else GoldPrimary.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = if (update.isForce) Color(0xFFEF4444) else GoldPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Title & Description
                Text(
                    if (update.isForce) "تحديث أمني هامة" else "إصدار جديد متوفر",
                    color = if (update.isForce) Color(0xFFEF4444) else GoldPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    if (update.isForce) 
                        "يتطلب هذا الإصدار (v${update.versionName}) التحديث فوراً لمتابعة استخدام التطبيق. يتضمن إصلاحات أمنية واستقرار ضرورية."
                    else
                        "إصدار v${update.versionName} جاهز للتنزيل الآن. يتضمن تحسينات هامة وإصلاحات لضمان أفضل تجربة.",
                    color = TextSecondary,
                    fontFamily = CairoFont,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                // Release Notes Section
                if (update.releaseNotes.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, TextSecondary.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "ما الجديد في هذا الإصدار:",
                            color = GoldSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CairoFont
                        )
                        Text(
                            update.releaseNotes,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            fontFamily = CairoFont,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Progress Indicator
                if (downloadProgress != null || patchState !is PatchManager.PatchState.Idle) {
                    UpdateProgressSection(downloadProgress, patchState)
                } else {
                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Remind Me Later (Only if not force)
                        if (canDismiss) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.dp, TextSecondary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                    .clickable(onClick = onDismiss),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "تذكيري لاحقاً",
                                    color = TextSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Update Now
                        Box(
                            modifier = Modifier
                                .weight(1.5f)
                                .height(50.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Brush.horizontalGradient(listOf(GoldPrimary, GoldSecondary)))
                                .clickable(onClick = onInstall),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = DeepSlate,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    if (update.isForce) "تحديث الآن (إلزامي)" else "تحديث الآن",
                                    color = DeepSlate,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }

                // Footer Size Info
                val sizeText = if (update.deltaUrl != null) {
                    "حجم التحديث: ${UpdateManager.formatSize(update.deltaSize)} (دلتا) / ${UpdateManager.formatSize(update.apkSize)} (كامل)"
                } else {
                    "حجم التحديث: ${UpdateManager.formatSize(update.apkSize)}"
                }
                Text(
                    sizeText,
                    color = TextSecondary.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontFamily = CairoFont,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun UpdateProgressSection(
    progress: UpdateManager.DownloadProgress?,
    patchState: PatchManager.PatchState
) {
    val phaseLabel = when {
        patchState is PatchManager.PatchState.Verifying -> "جاري التحقق من سلامة الملف..."
        patchState is PatchManager.PatchState.Patching -> "جاري معالجة وتحسين التحديث (Patching)..."
        patchState is PatchManager.PatchState.Success -> "اكتملت المعالجة بنجاح ✅"
        patchState is PatchManager.PatchState.Error -> patchState.message
        progress?.phase == UpdateManager.Phase.DOWNLOADING_DELTA -> "جاري تحميل التحديث الصغير..."
        progress?.phase == UpdateManager.Phase.DOWNLOADING_FULL -> "جاري تحميل الحزمة الكاملة..."
        else -> "جاري التحضير..."
    }
    
    val isPatching = patchState is PatchManager.PatchState.Patching || 
                     patchState is PatchManager.PatchState.Verifying ||
                     progress?.phase == UpdateManager.Phase.APPLYING_PATCH

    val isDeterminate = progress != null && !isPatching

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                phaseLabel,
                color = if (isPatching) GoldSecondary else TextSecondary,
                fontSize = 11.sp,
                fontFamily = CairoFont,
                fontWeight = if (isPatching) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isDeterminate) {
                Text(
                    "${progress?.percent}%",
                    color = GoldPrimary,
                    fontSize = 12.sp,
                    fontFamily = RobotoMonoFont,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(TextSecondary.copy(alpha = 0.15f))
        ) {
            if (isPatching) {
                // Indeterminate animation for patching
                val infiniteTransition = rememberInfiniteTransition(label = "patching")
                val offset by infiniteTransition.animateFloat(
                    initialValue = -0.5f,
                    targetValue = 1.5f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "offset"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.3f)
                        .fillMaxHeight()
                        .align(Alignment.CenterStart)
                        .offset(x = 300.dp * offset)
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(GoldSecondary, GoldPrimary, GoldSecondary)))
                )
            } else if (progress != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((progress.percent / 100f).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(GoldPrimary, GoldSecondary)))
                )
            }
        }
        
        if (isDeterminate && progress!!.speedBytesPerSec > 0) {
            Text(
                "السرعة: ${UpdateManager.formatSize(progress.speedBytesPerSec)}/ث",
                color = TextSecondary.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontFamily = CairoFont,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}
