package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 📸 زر الكاميرا الفضائي العائم المخصص لمساعدة المستخدم والمطور (Spatial HUD Dev & User Camera Lens)
 * يركز تماماً على مهمته الأساسية:
 * 1. التقاط لقطات الشاشة بنقاء وحفظها أو مشاركتها فورياً.
 * 2. تمييز وتحديد أماكن الخلل على اللقطة (Quick Canvas Focus Marker).
 * 3. فحص مقاييس الشاشة وأبعاد الـ DP الحية للمطور (Live DP & Screen Metrics Inspector).
 * 4. إرسال البلاغات التقنية ومتابعة سجل الطلبات ومحادثة الدعم.
 * 5. حل مشكلة أبعاد الـ DP عبر BoxWithConstraints وقياسات متجاوبة متوافقة مع معايير Material 3.
 */
@Composable
fun SpatialDraggableDevCameraButton(
    modifier: Modifier = Modifier,
    onScreenshotCaptured: (Bitmap, File?) -> Unit,
    onOpenDevChat: () -> Unit,
    onOpenMyReports: () -> Unit,
    onOpenDiagnosticReport: (PrecisionDiagnosticEngine.DiagnosticTelemetry, Bitmap?, File?) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val prefs = remember(context) { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }

    // Settings
    var isCompactMode by remember { mutableStateOf(prefs.getBoolean("spatial_cam_compact_mode", false)) }
    var isDockedMode by remember { mutableStateOf(prefs.getBoolean("spatial_cam_docked_mode", false)) }
    var singleTapInstantSnap by remember {
        mutableStateOf(prefs.getBoolean("spatial_cam_tap_instant_snap", false))
    }

    // Dialog state
    var showMainHubDialog by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var showMetricsInspectorDialog by remember { mutableStateOf(false) }
    var showPrecisionDiagnosticDialog by remember { mutableStateOf(false) }
    var currentTelemetry by remember { mutableStateOf<PrecisionDiagnosticEngine.DiagnosticTelemetry?>(null) }

    var latestScreenshotBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var latestScreenshotFile by remember { mutableStateOf<File?>(null) }

    var isCapturing by remember { mutableStateOf(false) }
    var showFlashEffect by remember { mutableStateOf(false) }

    // Animation
    val infiniteTransition = rememberInfiniteTransition(label = "DevCamTransition")
    val radarAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "devCamRadarAngle"
    )
    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "devCamHaloPulse"
    )

    // Using BoxWithConstraints to solve the DP measurement and coordinate issues accurately!
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerWidthDp = maxWidth
        val containerHeightDp = maxHeight
        val containerWidthPx = constraints.maxWidth.toFloat()
        val containerHeightPx = constraints.maxHeight.toFloat()

        // Responsive button sizes adhering to Material 3
        val btnSizeDp = if (isCompactMode) 46.dp else 54.dp
        val btnSizePx = with(density) { btnSizeDp.toPx() }
        val marginPx = with(density) { 14.dp.toPx() }
        val bottomSafetyMarginPx = with(density) { 88.dp.toPx() }
        val topSafetyMarginPx = with(density) { 52.dp.toPx() }

        // Initial coordinates with boundary safety
        val initialX = remember(containerWidthPx) {
            val savedX = prefs.getFloat("spatial_dev_cam_offset_x", -1f)
            if (savedX in 0f..(containerWidthPx - btnSizePx)) savedX
            else (containerWidthPx - btnSizePx - marginPx).coerceAtLeast(0f)
        }

        val initialY = remember(containerHeightPx) {
            val savedY = prefs.getFloat("spatial_dev_cam_offset_y", -1f)
            if (savedY in 0f..(containerHeightPx - btnSizePx)) savedY
            else (containerHeightPx - btnSizePx - bottomSafetyMarginPx).coerceAtLeast(0f)
        }

        var offsetX by remember { mutableFloatStateOf(initialX) }
        var offsetY by remember { mutableFloatStateOf(initialY) }
        var isDragging by remember { mutableStateOf(false) }

        fun savePosition(x: Float, y: Float) {
            prefs.edit()
                .putFloat("spatial_dev_cam_offset_x", x)
                .putFloat("spatial_dev_cam_offset_y", y)
                .apply()
        }

        fun snapToCorner(corner: DevCamCorner) {
            AppSoundManager.playClick()
            val targetX = when (corner) {
                DevCamCorner.TOP_LEFT, DevCamCorner.BOTTOM_LEFT -> marginPx
                DevCamCorner.TOP_RIGHT, DevCamCorner.BOTTOM_RIGHT -> containerWidthPx - btnSizePx - marginPx
            }
            val targetY = when (corner) {
                DevCamCorner.TOP_LEFT, DevCamCorner.TOP_RIGHT -> topSafetyMarginPx + marginPx
                DevCamCorner.BOTTOM_LEFT, DevCamCorner.BOTTOM_RIGHT -> containerHeightPx - btnSizePx - bottomSafetyMarginPx
            }
            offsetX = targetX.coerceIn(0f, (containerWidthPx - btnSizePx).coerceAtLeast(0f))
            offsetY = targetY.coerceIn(0f, (containerHeightPx - btnSizePx).coerceAtLeast(0f))
            savePosition(offsetX, offsetY)
            Toast.makeText(context, "تم تثبيت الكاميرا في ${corner.title} 📍", Toast.LENGTH_SHORT).show()
        }

        fun captureScreenshot(onDone: (Bitmap, File?) -> Unit) {
            if (isCapturing) return
            AppSoundManager.playClick()
            scope.launch {
                isCapturing = true
                showFlashEffect = true
                delay(100) // Hide button briefly so it is never captured in the screenshot
                val bitmap = BugReportManager.captureScreenshot(view)
                showFlashEffect = false
                isCapturing = false

                if (bitmap != null) {
                    val file = withContext(Dispatchers.IO) {
                        BugReportManager.saveBitmapToCache(context, bitmap)
                    }
                    latestScreenshotBitmap = bitmap
                    latestScreenshotFile = file
                    onDone(bitmap, file)
                } else {
                    Toast.makeText(context, "تعذر التقاط صورة الشاشة الحالية", Toast.LENGTH_SHORT).show()
                }
            }
        }

        fun handleButtonClick() {
            if (singleTapInstantSnap) {
                captureScreenshot { _, _ ->
                    showPreviewDialog = true
                }
            } else {
                AppSoundManager.playClick()
                showMainHubDialog = true
            }
        }

        // Shutter flash effect
        if (showFlashEffect) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.55f))
            )
        }

        // Floating Lens Button
        if (!isCapturing) {
            val isNearRightEdge = offsetX > (containerWidthPx / 2)

            if (isDockedMode) {
                // Docked Edge Tab: Provides 48dp minimum touch target while visually slim (22dp)
                val dockedX = if (isNearRightEdge) containerWidthPx - with(density) { 22.dp.toPx() } else 0f
                val clampedDockedY = offsetY.coerceIn(topSafetyMarginPx, (containerHeightPx - with(density) { 64.dp.toPx() } - bottomSafetyMarginPx).coerceAtLeast(topSafetyMarginPx))

                Box(
                    modifier = Modifier
                        .offset { IntOffset(dockedX.roundToInt(), clampedDockedY.roundToInt()) }
                        .size(width = 48.dp, height = 64.dp)
                        .testTag("spatial_cam_docked_pill")
                        .clickable {
                            AppSoundManager.playActivate()
                            isDockedMode = false
                            prefs.edit().putBoolean("spatial_cam_docked_mode", false).apply()
                            showMainHubDialog = true
                        },
                    contentAlignment = if (isNearRightEdge) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    // Visible Pill Frame
                    Box(
                        modifier = Modifier
                            .width(22.dp)
                            .height(60.dp)
                            .clip(
                                if (isNearRightEdge) RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)
                                else RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp)
                            )
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF0F172A),
                                        Color(0xFF1E293B),
                                        GoldPrimary.copy(alpha = 0.85f)
                                    )
                                )
                            )
                            .border(
                                1.2.dp,
                                Brush.verticalGradient(listOf(GoldPrimary, Color(0xFF00E5FF))),
                                if (isNearRightEdge) RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)
                                else RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E5FF))
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "زر الكاميرا الفضائي",
                                tint = GoldPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            } else {
                // Floating Draggable HUD Capsule
                Box(
                    modifier = Modifier
                        .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                        .size(btnSizeDp)
                        .testTag("spatial_draggable_dev_camera_button")
                        .pointerInput(containerWidthPx, containerHeightPx) {
                            detectDragGestures(
                                onDragStart = {
                                    isDragging = true
                                    AppSoundManager.playClick()
                                },
                                onDragEnd = {
                                    isDragging = false
                                    val minX = marginPx
                                    val maxX = (containerWidthPx - btnSizePx - marginPx).coerceAtLeast(minX)
                                    val minY = topSafetyMarginPx
                                    val maxY = (containerHeightPx - btnSizePx - bottomSafetyMarginPx).coerceAtLeast(minY)

                                    val clampedX = offsetX.coerceIn(minX, maxX)
                                    val clampedY = offsetY.coerceIn(minY, maxY)

                                    offsetX = clampedX
                                    offsetY = clampedY
                                    savePosition(clampedX, clampedY)
                                },
                                onDragCancel = { isDragging = false },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val newX = offsetX + dragAmount.x
                                    val newY = offsetY + dragAmount.y
                                    val maxX = (containerWidthPx - btnSizePx).coerceAtLeast(0f)
                                    val maxY = (containerHeightPx - btnSizePx).coerceAtLeast(0f)

                                    offsetX = newX.coerceIn(0f, maxX)
                                    offsetY = newY.coerceIn(0f, maxY)
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { handleButtonClick() },
                                onLongPress = {
                                    AppSoundManager.playActivate()
                                    showMetricsInspectorDialog = true
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Ambient Glow Halo
                    Box(
                        modifier = Modifier
                            .size(btnSizeDp + 12.dp)
                            .scale(if (isDragging) 1.2f else haloPulse)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        GoldPrimary.copy(alpha = if (isDragging) 0.5f else 0.35f),
                                        Color(0xFF00E5FF).copy(alpha = 0.2f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Rotating Cybernetic Ring
                    Box(
                        modifier = Modifier
                            .size(btnSizeDp + 4.dp)
                            .rotate(radarAngle)
                            .clip(CircleShape)
                            .border(
                                width = 1.4.dp,
                                brush = Brush.sweepGradient(
                                    listOf(
                                        GoldPrimary,
                                        Color(0xFF00E5FF),
                                        GoldPrimary.copy(alpha = 0.2f),
                                        Color(0xFF00E5FF),
                                        GoldPrimary
                                    )
                                ),
                                shape = CircleShape
                            )
                    )

                    // Core Button Capsule
                    Box(
                        modifier = Modifier
                            .size(btnSizeDp)
                            .shadow(
                                elevation = if (isDragging) 12.dp else 8.dp,
                                shape = CircleShape,
                                spotColor = GoldPrimary
                            )
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF0F172A),
                                        Color(0xFF1E293B),
                                        Color(0xFF0A0F1D)
                                    )
                                )
                            )
                            .border(
                                1.4.dp,
                                Brush.linearGradient(
                                    listOf(
                                        GoldPrimary,
                                        Color(0xFF38BDF8),
                                        GoldPrimary.copy(alpha = 0.85f)
                                    )
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "كاميرا وتوثيق قبس",
                                tint = GoldPrimary,
                                modifier = Modifier.size(if (isCompactMode) 20.dp else 22.dp)
                            )
                            Text(
                                text = "SNAP",
                                color = Color(0xFF38BDF8),
                                fontSize = if (isCompactMode) 9.sp else 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = CairoFont
                            )
                        }

                        // Status Dot Indicator
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp, end = 4.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                                .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                        )
                    }
                }
            }
        }

        // 1. Main Action Hub Dialog (مركز توثيق ومساعدة المستخدم والمطور)
        if (showMainHubDialog) {
            SpatialDevCamHubDialog(
                containerWidthDp = containerWidthDp.value,
                containerHeightDp = containerHeightDp.value,
                isCompact = isCompactMode,
                isDocked = isDockedMode,
                singleTapInstantSnap = singleTapInstantSnap,
                onDismiss = { showMainHubDialog = false },
                onInstantCapture = {
                    showMainHubDialog = false
                    captureScreenshot { _, _ ->
                        showPreviewDialog = true
                    }
                },
                onOpenInspector = {
                    showMainHubDialog = false
                    showMetricsInspectorDialog = true
                },
                onOpenPrecisionDiagnostics = {
                    showMainHubDialog = false
                    currentTelemetry = PrecisionDiagnosticEngine.harvestTelemetry(
                        context = context,
                        screenName = BugReportManager.currentScreen,
                        widthDp = containerWidthDp.value,
                        heightDp = containerHeightDp.value
                    )
                    showPrecisionDiagnosticDialog = true
                },
                onDirectDiagnosticReport = {
                    showMainHubDialog = false
                    captureScreenshot { bmp, file ->
                        val telemetry = PrecisionDiagnosticEngine.harvestTelemetry(
                            context = context,
                            screenName = BugReportManager.currentScreen,
                            widthDp = containerWidthDp.value,
                            heightDp = containerHeightDp.value
                        )
                        onOpenDiagnosticReport(telemetry, bmp, file)
                    }
                },
                onOpenDevChat = {
                    showMainHubDialog = false
                    onOpenDevChat()
                },
                onOpenMyReports = {
                    showMainHubDialog = false
                    onOpenMyReports()
                },
                onToggleCompact = { compact ->
                    isCompactMode = compact
                    prefs.edit().putBoolean("spatial_cam_compact_mode", compact).apply()
                },
                onToggleDocked = { docked ->
                    isDockedMode = docked
                    prefs.edit().putBoolean("spatial_cam_docked_mode", docked).apply()
                },
                onToggleInstantSnap = { instant ->
                    singleTapInstantSnap = instant
                    prefs.edit().putBoolean("spatial_cam_tap_instant_snap", instant).apply()
                },
                onSelectCorner = { corner -> snapToCorner(corner) }
            )
        }

        // 2. Snapshot Preview & Annotation Modal (معاينة اللقطة وحفظها ومشاركتها وتمييز الخلل)
        if (showPreviewDialog && latestScreenshotBitmap != null) {
            SpatialSnapshotPreviewDialog(
                bitmap = latestScreenshotBitmap!!,
                file = latestScreenshotFile,
                onDismiss = { showPreviewDialog = false },
                onSendToDev = { annotatedBmp, annotatedFile ->
                    showPreviewDialog = false
                    onScreenshotCaptured(annotatedBmp, annotatedFile)
                }
            )
        }

        // 3. Live DP & Screen Metrics Inspector Dialog (مفتش مقاييس وأحجام الـ DP الحية للمطور)
        if (showMetricsInspectorDialog) {
            SpatialDpMetricsInspectorDialog(
                containerWidthDp = containerWidthDp.value,
                containerHeightDp = containerHeightDp.value,
                densityFactor = density.density,
                densityDpi = (density.density * 160).roundToInt(),
                onDismiss = { showMetricsInspectorDialog = false }
            )
        }

        // 4. Precision Diagnostic Lens Dialog (مسبار التشخيص الدقيق الفضائي)
        if (showPrecisionDiagnosticDialog && currentTelemetry != null) {
            SpatialPrecisionDiagnosticDialog(
                telemetry = currentTelemetry!!,
                onDismiss = { showPrecisionDiagnosticDialog = false },
                onOpenReportWithDiagnostics = { telemetry ->
                    showPrecisionDiagnosticDialog = false
                    captureScreenshot { bmp, file ->
                        onOpenDiagnosticReport(telemetry, bmp, file)
                    }
                }
            )
        }
    }
}

/**
 * 🛰️ نافذة مركز توثيق وفحص الشاشة للمستخدم والمطور
 */
@Composable
private fun SpatialDevCamHubDialog(
    containerWidthDp: Float,
    containerHeightDp: Float,
    isCompact: Boolean,
    isDocked: Boolean,
    singleTapInstantSnap: Boolean,
    onDismiss: () -> Unit,
    onInstantCapture: () -> Unit,
    onOpenInspector: () -> Unit,
    onOpenPrecisionDiagnostics: () -> Unit,
    onDirectDiagnosticReport: () -> Unit,
    onOpenDevChat: () -> Unit,
    onOpenMyReports: () -> Unit,
    onToggleCompact: (Boolean) -> Unit,
    onToggleDocked: (Boolean) -> Unit,
    onToggleInstantSnap: (Boolean) -> Unit,
    onSelectCorner: (DevCamCorner) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable { onDismiss() }
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                DeepSlate.copy(alpha = 0.98f),
                                CardSurface.copy(alpha = 0.98f)
                            )
                        )
                    )
                    .border(
                        1.2.dp,
                        Brush.linearGradient(listOf(GoldPrimary, Color(0xFF00E5FF), GoldPrimary)),
                        RoundedCornerShape(24.dp)
                    )
                    .clickable(enabled = false) {}
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header HUD
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White.copy(alpha = 0.8f))
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "عدسة الكاميرا وتوثيق النظام 📸",
                                color = GoldPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = CairoFont
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E5FF))
                            )
                        }
                    }

                    // Active Screen Badge
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E293B).copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "الشاشة الحالية: ${BugReportManager.currentScreen}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${containerWidthDp.roundToInt()}×${containerHeightDp.roundToInt()} dp",
                                color = GoldPrimary,
                                fontSize = 11.5.sp,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    HorizontalDivider(color = GoldPrimary.copy(alpha = 0.2f), thickness = 0.8.dp)

                    // 1. Precision Diagnostic Lens (التشخيص الدقيق الفوري الشامل - الدور الجوهري)
                    DevCamActionCard(
                        icon = Icons.Default.Troubleshoot,
                        title = "فحص وتشخيص دقيق للنظام 🔬",
                        subtitle = "مسح فوري لأبعاد الشاشة، خط النظام، الذاكرة، والشبكة وتحديد العطل آلياً",
                        accentColor = Color(0xFF00E5FF),
                        badge = "AI DIAGNOSTICS",
                        onClick = onOpenPrecisionDiagnostics
                    )

                    // 2. Direct Diagnostic Report with Screenshot
                    DevCamActionCard(
                        icon = Icons.Default.BugReport,
                        title = "بلاغ مشخص فورياً مع اللقطة ⚡",
                        subtitle = "التقاط فوري للشاشة وإرفاق البصمة التقنية الكاملة لتسهيل مهمة المطور",
                        accentColor = GoldPrimary,
                        badge = "SMART REPORT",
                        onClick = onDirectDiagnosticReport
                    )

                    // 3. Instant Screen Capture (الميزة الأساسية)
                    DevCamActionCard(
                        icon = Icons.Default.CameraAlt,
                        title = "التقاط لقطة شاشة وحفظها 📸",
                        subtitle = "توثيق الشاشة الحالية بدقة، تمييز موضع الخلل، أو حفظها بالمعرض ومشاركتها",
                        accentColor = Color(0xFF10B981),
                        badge = "SCREENSHOT",
                        onClick = onInstantCapture
                    )

                    // 4. DP & Screen Metrics Inspector (مفتش مقاييس الشاشة للمطور)
                    DevCamActionCard(
                        icon = Icons.Default.Analytics,
                        title = "مفتش أبعاد الشاشة ومقاييس الـ DP 📏",
                        subtitle = "فحص الأبعاد الدقيقة (dp/px)، كثافة الشاشة، ونسخ التقرير التقني للمطور",
                        accentColor = Color(0xFFA855F7),
                        badge = "DEV METRICS",
                        onClick = onOpenInspector
                    )

                    // 5. User & Dev Feedback Channels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenMyReports,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.ListAlt, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("طلباتي السابقة 📋", color = Color(0xFF38BDF8), fontFamily = CairoFont, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onOpenDevChat,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("محادثة الدعم 💬", color = Color(0xFFF59E0B), fontFamily = CairoFont, fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f), thickness = 0.8.dp)

                    // Quick Preferences
                    Text(
                        text = "خيارات تخصيص الزر العائم ⚙️",
                        color = GoldPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CairoFont,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )

                    // Toggle Instant Snap on Single Tap
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "التقاط فوري بنقرة واحدة (بدون فتح القائمة)",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = CairoFont
                        )
                        Switch(
                            checked = singleTapInstantSnap,
                            onCheckedChange = onToggleInstantSnap,
                            colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary)
                        )
                    }

                    // Compact Size Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "الحجم المدمج الخفيف (46dp بدلاً من 54dp)",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = CairoFont
                        )
                        Switch(
                            checked = isCompact,
                            onCheckedChange = onToggleCompact,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF38BDF8))
                        )
                    }

                    // Dock to Edge Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "وضع الالتصاق بالحافة (شريط جانبي نحيف)",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = CairoFont
                        )
                        Switch(
                            checked = isDocked,
                            onCheckedChange = onToggleDocked,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF))
                        )
                    }

                    // 4-Corner Pinning
                    Text(
                        text = "تثبيت الزر في إحدى زوايا الشاشة 🧲",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontFamily = CairoFont,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CornerPinButton("أعلى اليمين ↗️", Modifier.weight(1f)) { onSelectCorner(DevCamCorner.TOP_RIGHT) }
                        CornerPinButton("أعلى اليسار ↖️", Modifier.weight(1f)) { onSelectCorner(DevCamCorner.TOP_LEFT) }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CornerPinButton("أسفل اليمين ↘️", Modifier.weight(1f)) { onSelectCorner(DevCamCorner.BOTTOM_RIGHT) }
                        CornerPinButton("أسفل اليسار ↙️", Modifier.weight(1f)) { onSelectCorner(DevCamCorner.BOTTOM_LEFT) }
                    }
                }
            }
        }
    }
}

/**
 * 📸 نافذة معاينة اللقطة مع ميزة التحديد وحفظها بالمعرض ومشاركتها وإرسالها للمطور
 */
@Composable
private fun SpatialSnapshotPreviewDialog(
    bitmap: Bitmap,
    file: File?,
    onDismiss: () -> Unit,
    onSendToDev: (Bitmap, File?) -> Unit
) {
    val context = LocalContext.current
    var isSaving by remember { mutableStateOf(false) }

    // Markup / Annotation state: lets user tap to place a focus marker on a bug or UI element
    var markerOffset by remember { mutableStateOf<Offset?>(null) }
    var currentBitmap by remember { mutableStateOf(bitmap) }

    fun addAnnotationMarker(tapRelativeX: Float, tapRelativeY: Float) {
        val mutableBmp = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBmp)
        val paintCircle = Paint().apply {
            color = android.graphics.Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 8f
            isAntiAlias = true
        }
        val paintFill = Paint().apply {
            color = android.graphics.Color.argb(80, 255, 0, 0)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val pxX = tapRelativeX * mutableBmp.width
        val pxY = tapRelativeY * mutableBmp.height
        val radius = 48f

        canvas.drawCircle(pxX, pxY, radius, paintFill)
        canvas.drawCircle(pxX, pxY, radius, paintCircle)

        currentBitmap = mutableBmp
        markerOffset = Offset(tapRelativeX, tapRelativeY)
        AppSoundManager.playClick()
        Toast.makeText(context, "تم تمييز موضع الخلل على اللقطة 🎯", Toast.LENGTH_SHORT).show()
    }

    fun saveToGallery() {
        isSaving = true
        AppSoundManager.playActivate()
        try {
            val filename = "Qabas_Capture_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Qabas")
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    currentBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                Toast.makeText(context, "تم حفظ لقطة الشاشة في مجلد الصور بنجاح 💾", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "تعذر الحفظ في المعرض", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "خطأ أثناء الحفظ: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            isSaving = false
        }
    }

    fun shareImage() {
        if (file == null) {
            Toast.makeText(context, "الملف المؤقت غير جاهز للمشاركة", Toast.LENGTH_SHORT).show()
            return
        }
        AppSoundManager.playClick()
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "تم توثيق الشاشة عبر عدسة قبس الفضائية 📸 - شاشة: ${BugReportManager.currentScreen}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "مشاركة لقطة الشاشة 🚀"))
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر المشاركة: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(DeepSlate)
                    .border(1.2.dp, GoldPrimary.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White.copy(alpha = 0.8f))
                    }
                    Text(
                        text = "معاينة اللقطة وتوثيق الشاشة 📸",
                        color = GoldPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CairoFont
                    )
                }

                // Interactive Annotation Thumbnail: tap anywhere to place a red focus target!
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 250.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val relX = (offset.x / size.width).coerceIn(0f, 1f)
                                val relY = (offset.y / size.height).coerceIn(0f, 1f)
                                addAnnotationMarker(relX, relY)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = currentBitmap.asImageBitmap(),
                        contentDescription = "معاينة لقطة الشاشة",
                        modifier = Modifier.fillMaxSize()
                    )

                    // Helper overlay badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = if (markerOffset == null) "المس أي مكان بالصورة لتحديد موضع الخلل 🎯" else "تم تحديد موضع الخلل بنجاح ✓",
                            color = if (markerOffset == null) Color.White.copy(alpha = 0.85f) else Color(0xFF10B981),
                            fontSize = 10.5.sp,
                            fontFamily = CairoFont,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Primary User Actions: Save & Share
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { saveToGallery() },
                        enabled = !isSaving,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("حفظ بالمعرض 💾", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { shareImage() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مشاركة فورية 🚀", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                // Send to Developer Button
                Button(
                    onClick = { onSendToDev(currentBitmap, file) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إرسال تقرير للمطور مع اللقطة 🛠️", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * 📏 مفتش أبعاد الشاشة ومقاييس الـ DP الحية للمطور (Live DP & Screen Metrics Inspector)
 * يحل ويوضح مسألة أحجام الـ DP بالكامل للمطور مع إمكانية نسخ التقرير التقني بنقرة واحدة!
 */
@Composable
private fun SpatialDpMetricsInspectorDialog(
    containerWidthDp: Float,
    containerHeightDp: Float,
    densityFactor: Float,
    densityDpi: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val windowClass = when {
        containerWidthDp < 600 -> "Compact (هاتف عمودي)"
        containerWidthDp < 840 -> "Medium (شاشة متوسطة / جهاز قابل للطي)"
        else -> "Expanded (تابلت أو شاشة أفقية)"
    }

    val totalRamMb = runCatching {
        val memInfo = android.app.ActivityManager.MemoryInfo()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        am.getMemoryInfo(memInfo)
        memInfo.totalMem / (1024 * 1024)
    }.getOrDefault(0L)

    val availRamMb = runCatching {
        val memInfo = android.app.ActivityManager.MemoryInfo()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        am.getMemoryInfo(memInfo)
        memInfo.availMem / (1024 * 1024)
    }.getOrDefault(0L)

    fun copyDiagnostics() {
        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val report = """
            📱 **تقرير مقاييس واجهة قبس التقنية (Qabas DP & UI Diagnostics)**
            -------------------------------------------------------------
            • الشاشة الحالية: ${BugReportManager.currentScreen}
            • عرض الشاشة: ${containerWidthDp.roundToInt()} dp (${(containerWidthDp * densityFactor).roundToInt()} px)
            • ارتفاع الشاشة: ${containerHeightDp.roundToInt()} dp (${(containerHeightDp * densityFactor).roundToInt()} px)
            • كثافة الشاشة: ${densityDpi} DPI (عامل الكثافة: ${"%.2f".format(densityFactor)}x)
            • تصنيف النافذة (Window Size Class): $windowClass
            • طراز الجهاز: ${Build.MANUFACTURER} ${Build.MODEL}
            • إصدار أندرويد: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            • الذاكرة الحية (RAM): $availRamMb MB متاح من أصل $totalRamMb MB
            • التاريخ: ${SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date())}
        """.trimIndent()

        cb.setPrimaryClip(ClipData.newPlainText("Qabas Diagnostics", report))
        AppSoundManager.playSuccess()
        Toast.makeText(context, "تم نسخ التقرير التقني لحافظة الجهاز بنجاح 📋", Toast.LENGTH_SHORT).show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .clickable { onDismiss() }
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(DeepSlate)
                    .border(1.2.dp, Color(0xFF00E5FF).copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                    .clickable(enabled = false) {}
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White.copy(alpha = 0.8f))
                        }
                        Text(
                            text = "مفتش أبعاد الشاشة ومقاييس الـ DP 📐",
                            color = Color(0xFF00E5FF),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CairoFont
                        )
                    }

                    Text(
                        text = "فحص مباشر للأبعاد وكثافة البكسلات لضمان توافق التصميم مع معايير Material 3 وشاشات الهواتف والتابلت:",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.5.sp,
                        fontFamily = NotoSansFont,
                        lineHeight = 17.sp,
                        textAlign = TextAlign.Center
                    )

                    // Metrics Grid Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            label = "عرض الشاشة (Width)",
                            value = "${containerWidthDp.roundToInt()} dp",
                            subValue = "${(containerWidthDp * densityFactor).roundToInt()} px",
                            color = GoldPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            label = "ارتفاع الشاشة (Height)",
                            value = "${containerHeightDp.roundToInt()} dp",
                            subValue = "${(containerHeightDp * densityFactor).roundToInt()} px",
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            label = "كثافة الشاشة (Density)",
                            value = "${densityDpi} DPI",
                            subValue = "${"%.2f".format(densityFactor)}x factor",
                            color = Color(0xFF10B981),
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            label = "فئة النافذة (Size Class)",
                            value = if (containerWidthDp < 600) "Compact" else "Medium+",
                            subValue = windowClass,
                            color = Color(0xFFA855F7),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Device Telemetry Row
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E293B),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            DiagnosticInfoRow("الشاشة المفتوحة", BugReportManager.currentScreen)
                            DiagnosticInfoRow("الجهاز", "${Build.MANUFACTURER} ${Build.MODEL}")
                            DiagnosticInfoRow("النظام", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                            if (totalRamMb > 0) {
                                DiagnosticInfoRow("الذاكرة", "$availRamMb MB متاح / $totalRamMb MB")
                            }
                        }
                    }

                    // Action Buttons
                    Button(
                        onClick = { copyDiagnostics() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("نسخ التقرير التقني الفوري للحافظة 📋", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                    ) {
                        Text("إغلاق", color = Color.White, fontFamily = CairoFont)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    subValue: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E293B),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 10.5.sp, fontFamily = CairoFont, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subValue, color = Color.White.copy(alpha = 0.5f), fontSize = 9.5.sp, fontFamily = CairoFont, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun DiagnosticInfoRow(title: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White.copy(alpha = 0.6f), fontSize = 11.5.sp, fontFamily = CairoFont)
        Text(detail, color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, fontFamily = CairoFont)
    }
}

@Composable
private fun DevCamActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    badge: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E293B).copy(alpha = 0.75f))
            .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f))
                    .border(1.2.dp, accentColor.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CairoFont,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentColor.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = badge,
                            color = accentColor,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CairoFont,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontFamily = NotoSansFont,
                    lineHeight = 16.sp
                )
            }

            Icon(
                Icons.Default.ChevronLeft,
                contentDescription = null,
                tint = accentColor.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CornerPinButton(
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.45f))
    ) {
        Text(
            text = title,
            color = GoldPrimary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = CairoFont
        )
    }
}

enum class DevCamCorner(val title: String) {
    TOP_LEFT("أعلى اليسار"),
    TOP_RIGHT("أعلى اليمين"),
    BOTTOM_LEFT("أسفل اليسار"),
    BOTTOM_RIGHT("أسفل اليمين")
}
