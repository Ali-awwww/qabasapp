package com.qabas.app

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 🛰️ زر الكاميرا الفضائي العائم القابل للتحريك والسحب لأي زاوية (Spatial HUD Draggable Camera FAB)
 * مزروع فوق جميع شاشات التطبيق لتمكين صناعة المحتوى والتصوير الفوري في أي لحظة.
 *
 * المواصفات الفنية:
 * - تحريك حر 2D Dragging مع تذكر الإحداثيات في ذاكرة الجهاز (SharedPreferences).
 * - تصميم زجاجي سيبراني فائق النقاء (Cybernetic Glassmorphism + Linear Minimalist).
 * - حلقة رادار نيونية دوارة ومؤشر ضوئي ذكي لحالة الكاميرا.
 * - التثبيت المغناطيسي في الزوايا الأربع بضغطة زر أو بالسحب الذكي.
 * - قائمة HUD مدمجة سريعة (تصوير فوري، استوديو الكاميرا، تثبيت الزاوية).
 */
@Composable
fun SpatialDraggableCameraButton(
    modifier: Modifier = Modifier,
    onOpenFullCamera: () -> Unit,
    onOpenMediaVault: () -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val btnSizeDp = 64.dp
    val btnSizePx = with(density) { btnSizeDp.toPx() }
    val marginPx = with(density) { 16.dp.toPx() }
    val bottomNavSafetyMarginPx = with(density) { 90.dp.toPx() }
    val topBarSafetyMarginPx = with(density) { 60.dp.toPx() }

    val prefs = remember(context) { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }

    // Load saved coordinates or initialize at Bottom-Left / Bottom-Right
    val initialX = remember(screenWidthPx) {
        val savedX = prefs.getFloat("spatial_cam_offset_x", -1f)
        if (savedX >= 0f && savedX <= screenWidthPx - btnSizePx) savedX
        else (screenWidthPx - btnSizePx - marginPx)
    }

    val initialY = remember(screenHeightPx) {
        val savedY = prefs.getFloat("spatial_cam_offset_y", -1f)
        if (savedY >= 0f && savedY <= screenHeightPx - btnSizePx) savedY
        else (screenHeightPx - btnSizePx - bottomNavSafetyMarginPx - marginPx)
    }

    var offsetX by remember { mutableFloatStateOf(initialX) }
    var offsetY by remember { mutableFloatStateOf(initialY) }
    var isDragging by remember { mutableStateOf(false) }
    var showQuickMenu by remember { mutableStateOf(false) }
    var showCornerSnapDialog by remember { mutableStateOf(false) }

    // Cybernetic Hologram Radar Rotation
    val infiniteTransition = rememberInfiniteTransition(label = "SpatialCamFabTransition")
    val radarAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarAngle"
    )

    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloPulse"
    )

    // Save position helper
    fun savePosition(x: Float, y: Float) {
        prefs.edit()
            .putFloat("spatial_cam_offset_x", x)
            .putFloat("spatial_cam_offset_y", y)
            .apply()
    }

    // Snap to corner functions
    fun snapToCorner(corner: CornerPosition) {
        AppSoundManager.playClick()
        val targetX: Float
        val targetY: Float
        when (corner) {
            CornerPosition.TOP_LEFT -> {
                targetX = marginPx
                targetY = topBarSafetyMarginPx + marginPx
            }
            CornerPosition.TOP_RIGHT -> {
                targetX = screenWidthPx - btnSizePx - marginPx
                targetY = topBarSafetyMarginPx + marginPx
            }
            CornerPosition.BOTTOM_LEFT -> {
                targetX = marginPx
                targetY = screenHeightPx - btnSizePx - bottomNavSafetyMarginPx
            }
            CornerPosition.BOTTOM_RIGHT -> {
                targetX = screenWidthPx - btnSizePx - marginPx
                targetY = screenHeightPx - btnSizePx - bottomNavSafetyMarginPx
            }
        }
        offsetX = targetX
        offsetY = targetY
        savePosition(targetX, targetY)
        showCornerSnapDialog = false
        Toast.makeText(context, "تم تثبيت الكاميرا في ${corner.title} 📍", Toast.LENGTH_SHORT).show()
    }

    // Floating Draggable HUD Widget
    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(btnSizeDp)
                .testTag("spatial_draggable_camera_button")
                .pointerInput(screenWidthPx, screenHeightPx) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            AppSoundManager.playClick()
                        },
                        onDragEnd = {
                            isDragging = false
                            // Keep safely within screen edges
                            val minX = marginPx
                            val maxX = screenWidthPx - btnSizePx - marginPx
                            val minY = topBarSafetyMarginPx
                            val maxY = screenHeightPx - btnSizePx - bottomNavSafetyMarginPx

                            val clampedX = offsetX.coerceIn(minX, maxX)
                            val clampedY = offsetY.coerceIn(minY, maxY)

                            offsetX = clampedX
                            offsetY = clampedY
                            savePosition(clampedX, clampedY)
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val newX = offsetX + dragAmount.x
                            val newY = offsetY + dragAmount.y

                            val minX = 0f
                            val maxX = screenWidthPx - btnSizePx
                            val minY = 0f
                            val maxY = screenHeightPx - btnSizePx

                            offsetX = newX.coerceIn(minX, maxX)
                            offsetY = newY.coerceIn(minY, maxY)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            AppSoundManager.playClick()
                            showQuickMenu = !showQuickMenu
                        },
                        onLongPress = {
                            AppSoundManager.playActivate()
                            Toast.makeText(context, "فتح استوديو الكاميرا المباشر 🎥", Toast.LENGTH_SHORT).show()
                            onOpenFullCamera()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Glowing Cybernetic Ambient Aura
            Box(
                modifier = Modifier
                    .size(btnSizeDp + 18.dp)
                    .scale(if (isDragging) 1.25f else haloPulse)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                GoldPrimary.copy(alpha = if (isDragging) 0.55f else 0.35f),
                                Color(0xFF00E5FF).copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Outer Radar Ring with High-Tech Rotation
            Box(
                modifier = Modifier
                    .size(btnSizeDp + 6.dp)
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

            // Inner Capsule Core (Glassmorphic Titanium Core)
            Box(
                modifier = Modifier
                    .size(btnSizeDp)
                    .shadow(
                        elevation = if (isDragging) 16.dp else 8.dp,
                        shape = CircleShape,
                        spotColor = GoldPrimary
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF0F172A),
                                Color(0xFF1E293B),
                                Color(0xFF0B1120)
                            )
                        )
                    )
                    .border(
                        1.6.dp,
                        Brush.linearGradient(
                            listOf(
                                GoldPrimary,
                                Color(0xFF38BDF8),
                                GoldPrimary.copy(alpha = 0.7f)
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
                        contentDescription = "زر الكاميرا الفضائي",
                        tint = GoldPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        text = "HUD CAM",
                        color = Color(0xFF38BDF8),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CairoFont
                    )
                }

                // Green/Neon Status LED Indicator (Top Right of core)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 6.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                        .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                )
            }
        }

        // Quick Radial/Mini HUD Menu Dialog when clicked
        if (showQuickMenu) {
            SpatialCameraQuickLauncherDialog(
                onDismiss = { showQuickMenu = false },
                onOpenFullCamera = {
                    showQuickMenu = false
                    onOpenFullCamera()
                },
                onOpenCornerSnapper = {
                    showQuickMenu = false
                    showCornerSnapDialog = true
                },
                onOpenMediaVault = {
                    showQuickMenu = false
                    onOpenMediaVault()
                }
            )
        }

        // Corner Pinning Dialog
        if (showCornerSnapDialog) {
            SpatialCornerSnapDialog(
                onDismiss = { showCornerSnapDialog = false },
                onSelectCorner = { corner -> snapToCorner(corner) }
            )
        }
    }
}

/**
 * 🛰️ نافذة خيارات الكاميرا الفضائية السريعة (Spatial Camera Quick Launcher)
 */
@Composable
private fun SpatialCameraQuickLauncherDialog(
    onDismiss: () -> Unit,
    onOpenFullCamera: () -> Unit,
    onOpenCornerSnapper: () -> Unit,
    onOpenMediaVault: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                DeepSlate.copy(alpha = 0.95f),
                                CardSurface.copy(alpha = 0.98f)
                            )
                        )
                    )
                    .border(
                        1.5.dp,
                        Brush.linearGradient(listOf(GoldPrimary, Color(0xFF00E5FF), GoldPrimary)),
                        RoundedCornerShape(26.dp)
                    )
                    .clickable(enabled = false) {}
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White.copy(alpha = 0.7f))
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "الكاميرا الفضائية السريعة 🎥",
                                color = GoldPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = CairoFont
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                        }
                    }

                    Text(
                        text = "الزر العائم مزروع في جميع الشاشات. يمكنك سحبه لأي مكان أو تثبيته في زاويتك المفضلة.",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        fontFamily = NotoSansFont,
                        lineHeight = 18.sp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = GoldPrimary.copy(alpha = 0.2f), thickness = 0.8.dp)

                    // Action 1: Open Full Camera Studio
                    QuickActionCard(
                        icon = Icons.Default.Videocam,
                        title = "استوديو الكاميرا والريلز 4K",
                        subtitle = "تسجيل فيديو ريلز 9:16 أو التقاط صور إيمانية",
                        accentColor = GoldPrimary,
                        onClick = onOpenFullCamera
                    )

                    // Action 2: Open Media Vault
                    QuickActionCard(
                        icon = Icons.Default.VideoLibrary,
                        title = "معرض وسائط وفيديوهات قبس",
                        subtitle = "تصفح الفيديوهات المصدّرة واللقطات الجاهزة",
                        accentColor = Color(0xFF38BDF8),
                        onClick = onOpenMediaVault
                    )

                    // Action 3: Snap to Corner Pin
                    QuickActionCard(
                        icon = Icons.Default.NearMe,
                        title = "تثبيت الزر في زاوية الشاشة 🧲",
                        subtitle = "انقل الزر فوراً إلى إحدى زوايا الشاشة الأربع",
                        accentColor = Color(0xFFA855F7),
                        onClick = onOpenCornerSnapper
                    )

                    // Close Button
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.8f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Text("إغلاق القائمة", fontFamily = CairoFont, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * 🧲 نافذة اختيار وتثبيت الزاوية (Corner Magnet Pinning Dialog)
 */
@Composable
private fun SpatialCornerSnapDialog(
    onDismiss: () -> Unit,
    onSelectCorner: (CornerPosition) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(DeepSlate)
                .border(1.2.dp, GoldPrimary.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "تثبيت زر الكاميرا في الزاوية 🧲",
                    color = GoldPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CairoFont
                )

                Text(
                    text = "اختر الزاوية التي تناسب استخدامك ليبقى زر الكاميرا دائماً في متناول يدك:",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    fontFamily = NotoSansFont,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                // 2x2 Grid for Corners
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CornerSelectionButton(
                            title = "أعلى اليمين ↗️",
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectCorner(CornerPosition.TOP_RIGHT) }
                        )
                        CornerSelectionButton(
                            title = "أعلى اليسار ↖️",
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectCorner(CornerPosition.TOP_LEFT) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CornerSelectionButton(
                            title = "أسفل اليمين ↘️",
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectCorner(CornerPosition.BOTTOM_RIGHT) }
                        )
                        CornerSelectionButton(
                            title = "أسفل اليسار ↙️",
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectCorner(CornerPosition.BOTTOM_LEFT) }
                        )
                    }
                }

                TextButton(onClick = onDismiss) {
                    Text("إلغاء", color = Color.White.copy(alpha = 0.6f), fontFamily = CairoFont)
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E293B).copy(alpha = 0.7f))
            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
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
                    .background(accentColor.copy(alpha = 0.15f))
                    .border(1.dp, accentColor.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CairoFont
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    fontFamily = NotoSansFont
                )
            }

            Icon(
                Icons.Default.ChevronLeft,
                contentDescription = null,
                tint = accentColor.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CornerSelectionButton(
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, GoldPrimary.copy(alpha = 0.5f))
    ) {
        Text(
            text = title,
            color = GoldPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = CairoFont
        )
    }
}

enum class CornerPosition(val title: String) {
    TOP_LEFT("أعلى اليسار"),
    TOP_RIGHT("أعلى اليمين"),
    BOTTOM_LEFT("أسفل اليسار"),
    BOTTOM_RIGHT("أسفل اليمين")
}
