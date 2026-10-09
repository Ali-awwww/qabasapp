package com.qabas.app

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlin.math.sin

val EmeraldGreen = Color(0xFF10B981)

/**
 * شريط وحوار التنزيل الحقيقي — تفاعلي بنسبة مئوية وسرعة وحجم بالبايت.
 */
@Composable
fun RealDownloadProgressDialog(
    trackTitle: String,
    progress: DownloadProgress,
    onCancelOrDismiss: () -> Unit
) {
    val animatedPercent by animateFloatAsState(
        targetValue = progress.percent.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 200, easing = LinearEasing),
        label = "downloadProgressAnimation"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    AlertDialog(
        onDismissRequest = {
            if (progress.isCompleted || progress.isError) onCancelOrDismiss()
        },
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (progress.isCompleted) EmeraldGreen.copy(alpha = 0.2f) else GoldPrimary.copy(alpha = 0.2f))
                        .border(1.dp, if (progress.isCompleted) EmeraldGreen else GoldPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            progress.isCompleted -> Icons.Default.CheckCircle
                            progress.isError -> Icons.Default.ErrorOutline
                            else -> Icons.Default.FileDownload
                        },
                        contentDescription = null,
                        tint = if (progress.isCompleted) EmeraldGreen else if (progress.isError) Color(0xFFEF4444) else GoldPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (progress.isCompleted) "اكتمل التنزيل بنجاح ✅" else if (progress.isError) "تعذر التنزيل ⚠️" else "جاري تنزيل الملف الصوتي",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = trackTitle,
                        color = GoldPrimary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // شريط التقدم الفاخر
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF1E293B))
                ) {
                    // شريط التعبئة المتدرج
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedPercent)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(GoldSecondary, GoldPrimary, Color(0xFFFBBF24))
                                )
                            )
                    )
                }

                // سطر الأرقام والإحصائيات الحية
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${(animatedPercent * 100).toInt()}%",
                        color = GoldPrimary,
                        fontFamily = NotoSansFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    val downloadedFormatted = AudioDownloadHelper.formatFileSize(progress.downloadedBytes)
                    val totalFormatted = AudioDownloadHelper.formatFileSize(progress.totalBytes.takeIf { it > 0 } ?: progress.downloadedBytes)
                    Text(
                        text = "$downloadedFormatted / $totalFormatted",
                        color = TextSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 11.sp
                    )

                    if (!progress.isCompleted && !progress.isError && progress.speedBytesPerSec > 0) {
                        val speedFormatted = AudioDownloadHelper.formatFileSize(progress.speedBytesPerSec) + "/s"
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "⚡ $speedFormatted",
                                color = EmeraldGreen,
                                fontFamily = NotoSansFont,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // رسالة الحالة
                val statusMessage = when {
                    progress.isCompleted -> "تم حفظ المقطع في مجلد التنزيلات (Downloads) بجهازك."
                    progress.isError -> progress.errorMessage ?: "حدث خطأ غير متوقع أثناء التنزيل."
                    else -> "جاري استقبال حزم الصوت وحفظها في ذاكرة الهاتف..."
                }
                Text(
                    text = statusMessage,
                    color = if (progress.isError) Color(0xFFF87171) else if (progress.isCompleted) EmeraldGreen else TextSecondary,
                    fontFamily = CairoFont,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            if (progress.isCompleted || progress.isError) {
                Button(
                    onClick = onCancelOrDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = if (progress.isCompleted) EmeraldGreen else GoldPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "تم",
                        color = DeepSlate,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onCancelOrDismiss,
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "إلغاء التنزيل",
                        color = Color(0xFFEF4444),
                        fontFamily = CairoFont,
                        fontSize = 11.sp
                    )
                }
            }
        }
    )
}

/**
 * شريط الرفع الحقيقي والحي — تفاعلي مع مراحل التحميل وحساب النسبة والحجم.
 */
@Composable
fun RealUploadProgressBar(
    progressPercent: Float, // 0.0 to 1.0
    stageText: String,
    uploadedBytes: Long = 0L,
    totalBytes: Long = 0L,
    speedBytesPerSec: Long = 0L,
    modifier: Modifier = Modifier
) {
    val animatedPercent by animateFloatAsState(
        targetValue = progressPercent.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 250, easing = LinearEasing),
        label = "uploadProgressAnimation"
    )

    Surface(
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stageText,
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Text(
                    text = "${(animatedPercent * 100).toInt()}%",
                    color = GoldPrimary,
                    fontFamily = NotoSansFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // شريط التقدم الفاخر
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF1E293B))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedPercent)
                        .background(
                            Brush.horizontalGradient(
                                listOf(GoldSecondary, GoldPrimary, Color(0xFFFBBF24))
                            )
                        )
                )
            }

            if (totalBytes > 0L) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val upFormatted = AudioDownloadHelper.formatFileSize(uploadedBytes)
                    val totFormatted = AudioDownloadHelper.formatFileSize(totalBytes)
                    Text(
                        text = "$upFormatted / $totFormatted",
                        color = TextSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 10.sp
                    )

                    if (speedBytesPerSec > 0L) {
                        val spdFormatted = AudioDownloadHelper.formatFileSize(speedBytesPerSec) + "/s"
                        Text(
                            text = "سرعة الرفع: $spdFormatted",
                            color = EmeraldGreen,
                            fontFamily = NotoSansFont,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * التايم لاين الاحترافي الفاخر مع الموجات الصوتية، شريط التقدم الحي والمؤقت التنازلي فائق الدقة
 */
@Composable
fun LuxuryAudioTimelineBar(
    currentPositionMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableLongStateOf(0L) }

    val effectivePosition = if (isDragging) dragPositionMs else currentPositionMs
    val progressRatio = if (totalDurationMs > 0) (effectivePosition.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progressRatio,
        animationSpec = if (isDragging) snap() else spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "smoothProgress"
    )

    // موجات صوتية تحاكي ترددات التسجيل الحي
    val waveformPoints = remember(totalDurationMs) {
        val count = 48
        List(count) { i ->
            val base = sin(i.toDouble() * 0.45).toFloat() * 0.35f + 0.55f
            (base * (if (i % 3 == 0) 1.2f else if (i % 2 == 0) 0.8f else 0.95f)).coerceIn(0.2f, 1.0f)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "wavePulse")
    val wavePulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wavePulse"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // رسم الموجات الصوتية والتايم لاين التفاعلي
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0B1120))
                .border(0.8.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                .pointerInput(totalDurationMs) {
                    detectTapGestures { offset ->
                        val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val targetMs = (ratio * totalDurationMs).toLong()
                        onSeek(targetMs)
                    }
                }
                .pointerInput(totalDurationMs) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            dragPositionMs = (ratio * totalDurationMs).toLong()
                        },
                        onDragEnd = {
                            isDragging = false
                            onSeek(dragPositionMs)
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val ratio = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            dragPositionMs = (ratio * totalDurationMs).toLong()
                        }
                    )
                }
        ) {
            // رسم الموجات الصوتية بواسطة Canvas
            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {
                val barCount = waveformPoints.size
                val spacing = 3.dp.toPx()
                val totalSpacing = spacing * (barCount - 1)
                val barWidth = ((size.width - totalSpacing) / barCount).coerceAtLeast(2.dp.toPx())

                val activeWidth = size.width * animatedProgress

                // Background track line
                drawLine(
                    color = Color(0xFF1E293B),
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 2.dp.toPx()
                )

                // Active glowing progress track line
                drawLine(
                    brush = Brush.horizontalGradient(listOf(GoldSecondary, GoldPrimary, Color(0xFF10B981))),
                    start = Offset(0f, size.height / 2f),
                    end = Offset(activeWidth, size.height / 2f),
                    strokeWidth = 3.dp.toPx()
                )

                for (i in 0 until barCount) {
                    val x = i * (barWidth + spacing)
                    val rawHeight = waveformPoints[i] * size.height * 0.75f
                    val pulse = if (isPlaying && x <= activeWidth) wavePulse else 1f
                    val barHeight = (rawHeight * pulse).coerceIn(4.dp.toPx(), size.height)
                    val y = (size.height - barHeight) / 2f

                    val isPlayed = (x + barWidth / 2f) <= activeWidth
                    val barColor = if (isPlayed) {
                        if (x > activeWidth - 25.dp.toPx()) Color(0xFF10B981) else GoldPrimary
                    } else {
                        Color(0xFF1E293B)
                    }

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }

                // خط ومؤشر الموضع الحالي (Playhead Knob & Halo)
                if (isPlaying) {
                    drawCircle(
                        color = Color(0xFF10B981).copy(alpha = glowAlpha * 0.4f),
                        radius = 8.dp.toPx(),
                        center = Offset(activeWidth, size.height / 2f)
                    )
                }

                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = Offset(activeWidth, size.height / 2f)
                )
                drawCircle(
                    color = GoldPrimary,
                    radius = 2.5.dp.toPx(),
                    center = Offset(activeWidth, size.height / 2f)
                )
            }

            // مؤشر السحب المنبثق عند السحب (Scrub Tooltip Bubble)
            if (isDragging) {
                Surface(
                    color = GoldPrimary,
                    shape = RoundedCornerShape(6.dp),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Schedule, null, tint = DeepSlate, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatAudioDuration(dragPositionMs),
                            color = DeepSlate,
                            fontFamily = NotoSansFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * لوحة العداد الرقمي المزدوج والمؤقت التنازلي فائق الدقة (Accurate Live Countdown HUD)
 */
@Composable
fun AccurateAudioCountdownHud(
    currentPositionMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val remainingMs = (totalDurationMs - currentPositionMs).coerceAtLeast(0L)
    val progressPercent = if (totalDurationMs > 0) ((currentPositionMs.toFloat() / totalDurationMs.toFloat()) * 100).toInt().coerceIn(0, 100) else 0

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_dot")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. الوقت المنقضي (Elapsed Time)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = dotAlpha))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = formatAudioDuration(currentPositionMs),
                        color = GoldPrimary,
                        fontFamily = NotoSansFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            // نسبة الإنجاز المئوية الحية
            Surface(
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "$progressPercent%",
                    color = Color.White.copy(alpha = 0.8f),
                    fontFamily = RobotoMonoFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.5.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        // 2. المؤقت التنازلي التفاعلي الدقيق (Countdown Remaining Timer)
        Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, if (isPlaying) Color(0xFF10B981).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.15f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.HourglassBottom,
                    contentDescription = "الوقت المتبقي",
                    tint = if (isPlaying) Color(0xFF10B981) else TextSecondary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "متبقي -${formatAudioDuration(remainingMs)}",
                    color = if (isPlaying) Color(0xFF10B981) else TextSecondary,
                    fontFamily = NotoSansFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * تنسيق الوقت بالدقائق والثواني بدقة
 */
fun formatAudioDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
}
