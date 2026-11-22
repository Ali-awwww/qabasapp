package com.qabas.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import java.util.Locale
import kotlin.math.sin

val EmeraldGreen = Color(0xFF10B981)

/**
 * حوار التنزيل الحقيقي بنمط Apple HIG — يعرض النسبة والسرعة والحجم الفعلي.
 */
@Composable
fun RealDownloadProgressDialog(
    trackTitle: String,
    progress: DownloadProgress,
    onCancelOrDismiss: () -> Unit
) {
    val theme = QabasTheme.current
    val animatedPercent by animateFloatAsState(
        targetValue = progress.percent.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 200, easing = LinearEasing),
        label = "downloadProgressAnimation"
    )

    AlertDialog(
        onDismissRequest = {
            if (progress.isCompleted || progress.isError) onCancelOrDismiss()
        },
        containerColor = theme.cardSurface,
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
                        .background(
                            when {
                                progress.isCompleted -> theme.success.copy(alpha = 0.16f)
                                progress.isError -> theme.destructive.copy(alpha = 0.16f)
                                else -> theme.primary.copy(alpha = 0.16f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            progress.isCompleted -> Icons.Default.CheckCircle
                            progress.isError -> Icons.Default.ErrorOutline
                            else -> Icons.Default.FileDownload
                        },
                        contentDescription = null,
                        tint = when {
                            progress.isCompleted -> theme.success
                            progress.isError -> theme.destructive
                            else -> theme.primary
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (progress.isCompleted) "اكتمل التنزيل بنجاح" else if (progress.isError) "تعذر التنزيل" else "جاري تنزيل الملف الصوتي",
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = trackTitle,
                        color = theme.primary,
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(theme.secondarySurface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedPercent)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(theme.primary, theme.accent)
                                )
                            )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (progress.isTotalUnknown && !progress.isCompleted) "…" else "${(animatedPercent * 100).toInt()}%",
                        color = theme.primary,
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    val downloadedFormatted = AudioDownloadHelper.formatFileSize(progress.downloadedBytes)
                    val totalText = if (progress.isTotalUnknown && !progress.isCompleted) {
                        "الحجم غير معروف"
                    } else {
                        AudioDownloadHelper.formatFileSize(progress.totalBytes.takeIf { it > 0 } ?: progress.downloadedBytes)
                    }
                    Text(
                        text = "$downloadedFormatted / $totalText",
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 11.sp
                    )

                    if (!progress.isCompleted && !progress.isError && progress.speedBytesPerSec > 0) {
                        val speedFormatted = AudioDownloadHelper.formatFileSize(progress.speedBytesPerSec) + "/s"
                        Surface(
                            color = theme.success.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = speedFormatted,
                                color = theme.success,
                                fontFamily = RobotoMonoFont,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                val statusMessage = when {
                    progress.isCompleted -> "تم حفظ المقطع في مجلد التنزيلات (Downloads) بجهازك."
                    progress.isError -> progress.errorMessage ?: "حدث خطأ غير متوقع أثناء التنزيل."
                    else -> "جاري استقبال حزم الصوت وحفظها في ذاكرة الهاتف..."
                }
                Text(
                    text = statusMessage,
                    color = when {
                        progress.isError -> theme.destructive
                        progress.isCompleted -> theme.success
                        else -> theme.textSecondary
                    },
                    fontFamily = CairoFont,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            if (progress.isCompleted || progress.isError) {
                Button(
                    onClick = onCancelOrDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (progress.isCompleted) theme.success else theme.primary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        text = "تم",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onCancelOrDismiss,
                    border = BorderStroke(1.dp, theme.destructive.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        text = "إلغاء التنزيل",
                        color = theme.destructive,
                        fontFamily = CairoFont,
                        fontSize = 12.sp
                    )
                }
            }
        }
    )
}

/**
 * شريط الرفع الحقيقي بنمط Apple HIG.
 */
@Composable
fun RealUploadProgressBar(
    progressPercent: Float,
    stageText: String,
    uploadedBytes: Long = 0L,
    totalBytes: Long = 0L,
    speedBytesPerSec: Long = 0L,
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
    val animatedPercent by animateFloatAsState(
        targetValue = progressPercent.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 250, easing = LinearEasing),
        label = "uploadProgressAnimation"
    )

    Surface(
        color = theme.cardSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.6f)),
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
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(theme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stageText,
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Text(
                    text = "${(animatedPercent * 100).toInt()}%",
                    color = theme.primary,
                    fontFamily = RobotoMonoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(theme.secondarySurface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedPercent)
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(theme.primary, theme.accent)))
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
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 11.sp
                    )

                    if (speedBytesPerSec > 0L) {
                        val spdFormatted = AudioDownloadHelper.formatFileSize(speedBytesPerSec) + "/s"
                        Text(
                            text = "سرعة الرفع: $spdFormatted",
                            color = theme.success,
                            fontFamily = NotoSansFont,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * التايم لاين التفاعلي للموجات الصوتية متوافق مع الوضعين الفاتح والداكن.
 */
@Composable
fun LuxuryAudioTimelineBar(
    currentPositionMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
    var isDragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableLongStateOf(0L) }

    val effectivePosition = if (isDragging) dragPositionMs else currentPositionMs
    val progressRatio = if (totalDurationMs > 0) (effectivePosition.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progressRatio,
        animationSpec = if (isDragging) snap() else spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "smoothProgress"
    )

    val waveformPoints = remember(totalDurationMs) {
        val count = 48
        List(count) { i ->
            val base = sin(i.toDouble() * 0.45).toFloat() * 0.35f + 0.55f
            (base * (if (i % 3 == 0) 1.2f else if (i % 2 == 0) 0.8f else 0.95f)).coerceIn(0.2f, 1.0f)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "wavePulse")
    val wavePulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wavePulse"
    )

    val trackBgColor = theme.secondarySurface
    val playedColor = theme.primary
    val activeHeadColor = theme.success
    val unplayedBarColor = theme.separator.copy(alpha = 0.55f)
    val trackLineColor = theme.separator.copy(alpha = 0.35f)
    val knobCenterColor = theme.cardSurface

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(trackBgColor)
                .border(0.5.dp, theme.separator.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                .pointerInput(totalDurationMs) {
                    detectTapGestures { offset ->
                        val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onSeek((ratio * totalDurationMs).toLong())
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
                        onDragCancel = { isDragging = false },
                        onDrag = { change, _ ->
                            change.consume()
                            val ratio = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            dragPositionMs = (ratio * totalDurationMs).toLong()
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {
                val barCount = waveformPoints.size
                val spacing = 3.dp.toPx()
                val totalSpacing = spacing * (barCount - 1)
                val barWidth = ((size.width - totalSpacing) / barCount).coerceAtLeast(2.dp.toPx())
                val activeWidth = size.width * animatedProgress

                drawLine(
                    color = trackLineColor,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 2.dp.toPx()
                )

                drawLine(
                    brush = Brush.horizontalGradient(listOf(playedColor, activeHeadColor)),
                    start = Offset(0f, size.height / 2f),
                    end = Offset(activeWidth, size.height / 2f),
                    strokeWidth = 3.dp.toPx()
                )

                for (i in 0 until barCount) {
                    val x = i * (barWidth + spacing)
                    val rawHeight = waveformPoints[i] * size.height * 0.72f
                    val pulse = if (isPlaying && x <= activeWidth) wavePulse else 1f
                    val barHeight = (rawHeight * pulse).coerceIn(4.dp.toPx(), size.height)
                    val y = (size.height - barHeight) / 2f

                    val isPlayed = (x + barWidth / 2f) <= activeWidth
                    val barColor = if (isPlayed) {
                        if (x > activeWidth - 22.dp.toPx()) activeHeadColor else playedColor
                    } else {
                        unplayedBarColor
                    }

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }

                drawCircle(
                    color = playedColor,
                    radius = 5.dp.toPx(),
                    center = Offset(activeWidth, size.height / 2f)
                )
                drawCircle(
                    color = knobCenterColor,
                    radius = 2.5.dp.toPx(),
                    center = Offset(activeWidth, size.height / 2f)
                )
            }

            if (isDragging) {
                Surface(
                    color = theme.primary,
                    shape = RoundedCornerShape(8.dp),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Schedule, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatAudioDuration(dragPositionMs),
                            color = Color.White,
                            fontFamily = RobotoMonoFont,
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
 * شريط التوقيت الرقمي الدقيق المتوافق مع Apple HIG.
 */
@Composable
fun AccurateAudioCountdownHud(
    currentPositionMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
    val remainingMs = (totalDurationMs - currentPositionMs).coerceAtLeast(0L)
    val progressPercent = if (totalDurationMs > 0) ((currentPositionMs.toFloat() / totalDurationMs.toFloat()) * 100).toInt().coerceIn(0, 100) else 0

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = theme.secondarySurface,
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatAudioDuration(currentPositionMs),
                        color = theme.primary,
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            Surface(
                color = theme.secondarySurface,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "$progressPercent%",
                    color = theme.textSecondary,
                    fontFamily = RobotoMonoFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                )
            }
        }

        Surface(
            color = if (isPlaying) theme.success.copy(alpha = 0.14f) else theme.secondarySurface,
            shape = RoundedCornerShape(6.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.HourglassBottom,
                    contentDescription = "الوقت المتبقي",
                    tint = if (isPlaying) theme.success else theme.textSecondary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "-${formatAudioDuration(remainingMs)}",
                    color = if (isPlaying) theme.success else theme.textSecondary,
                    fontFamily = RobotoMonoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

fun formatAudioDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
