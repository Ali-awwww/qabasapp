package com.qabas.app

import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.DeepSlate
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 🖌️ لوحة التحديد الهندسي فوق لقطة الشاشة.
 *
 * السلوك:
 * - وضع "رسم": السحب على الصورة ينشئ الشكل المختار (مستطيل/دائرة/سهم/خط/توقيع حر).
 * - وضع "تحريك": الضغط على شكل يحدّده، ثم السحب ينقله إلى موضع جديد.
 * - أداة "ملاحظة": النقر يطلب نصاً ويضعه على الصورة.
 */
@Composable
fun SpatialMarkupCanvas(
    bitmap: Bitmap,
    shapes: List<MarkupShape>,
    activeTool: MarkupTool,
    activeColor: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 0.006f,
    onShapesChange: (List<MarkupShape>) -> Unit,
    onShapeSelected: (Long?) -> Unit = {}
) {
    val currentShapes by rememberUpdatedState(shapes)
    val currentShapesChange by rememberUpdatedState(onShapesChange)
    val currentSelectionCallback by rememberUpdatedState(onShapeSelected)
    val currentTool by rememberUpdatedState(activeTool)
    val currentColor by rememberUpdatedState(activeColor)
    val currentStroke by rememberUpdatedState(strokeWidth)

    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    var nextShapeId by remember { mutableLongStateOf(1L) }
    var pendingTextPosition by remember { mutableStateOf<Offset?>(null) }
    var pendingTextValue by remember { mutableStateOf("") }

    fun imageRect(containerWidth: Float, containerHeight: Float): Rect {
        if (bitmap.width <= 0 || bitmap.height <= 0) return Rect(0f, 0f, containerWidth, containerHeight)
        val scale = min(containerWidth / bitmap.width, containerHeight / bitmap.height)
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        val ox = (containerWidth - w) / 2f
        val oy = (containerHeight - h) / 2f
        return Rect(ox, oy, ox + w, oy + h)
    }

    fun toNormalized(rect: Rect, pos: Offset): Offset {
        if (rect.width <= 0f || rect.height <= 0f) return Offset.Zero
        val nx = ((pos.x - rect.left) / rect.width).coerceIn(0f, 1f)
        val ny = ((pos.y - rect.top) / rect.height).coerceIn(0f, 1f)
        return Offset(nx, ny)
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(activeTool, activeColor) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val rect = imageRect(size.width.toFloat(), size.height.toFloat())
                        val start = toNormalized(rect, down.position)

                        val hit = if (currentTool == MarkupTool.SELECT) {
                            currentShapes.lastOrNull { markupHitTest(it, start) }
                        } else {
                            null
                        }
                        if (currentTool == MarkupTool.SELECT) {
                            currentSelectionCallback(hit?.id)
                        }
                        val isMove = hit != null
                        val originalPoints = hit?.points ?: emptyList()
                        var moved = false
                        val drawPoints = mutableListOf(start)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            if (hypot(change.position.x - down.position.x, change.position.y - down.position.y) > 6f) {
                                moved = true
                            }

                            if (isMove && hit != null) {
                                val current = toNormalized(rect, change.position)
                                val delta = current - start
                                val updated = hit.copy(points = originalPoints.map { it + delta })
                                currentShapesChange(
                                    currentShapes.map { if (it.id == hit.id) updated else it }
                                )
                                change.consume()
                            } else if (currentTool != MarkupTool.SELECT) {
                                drawPoints.add(toNormalized(rect, change.position))
                                change.consume()
                            }
                        }

                        if (isMove) return@awaitEachGesture

                        when (currentTool) {
                            MarkupTool.TEXT -> {
                                if (!moved) {
                                    pendingTextPosition = start
                                    pendingTextValue = ""
                                }
                            }
                            MarkupTool.FREEHAND -> {
                                if (moved && drawPoints.size > 1) {
                                    val shape = MarkupShape(
                                        id = nextShapeId++,
                                        tool = MarkupTool.FREEHAND,
                                        color = currentColor,
                                        strokeWidth = currentStroke,
                                        points = drawPoints.toList()
                                    )
                                    currentShapesChange(currentShapes + shape)
                                }
                            }
                            MarkupTool.RECTANGLE, MarkupTool.ELLIPSE, MarkupTool.ARROW, MarkupTool.LINE -> {
                                if (moved) {
                                    val shape = MarkupShape(
                                        id = nextShapeId++,
                                        tool = currentTool,
                                        color = currentColor,
                                        strokeWidth = currentStroke,
                                        points = listOf(start, drawPoints.last())
                                    )
                                    currentShapesChange(currentShapes + shape)
                                }
                            }
                            MarkupTool.SELECT -> Unit
                        }
                    }
                }
        ) {
            val scale = if (bitmap.width > 0 && bitmap.height > 0) {
                min(size.width / bitmap.width, size.height / bitmap.height)
            } else {
                1f
            }
            val imgW = bitmap.width * scale
            val imgH = bitmap.height * scale
            val ox = (size.width - imgW) / 2f
            val oy = (size.height - imgH) / 2f

            drawImage(
                image = imageBitmap,
                dstOffset = IntOffset(ox.toInt(), oy.toInt()),
                dstSize = IntSize(max(1, imgW.toInt()), max(1, imgH.toInt()))
            )

            currentShapes.forEach { shape ->
                drawMarkupShape(shape, ox, oy, imgW, imgH)
            }
        }
    }

    // نافذة إدخال الملاحظة النصية
    val textPos = pendingTextPosition
    if (textPos != null) {
        AlertDialog(
            onDismissRequest = { pendingTextPosition = null },
            containerColor = DeepSlate,
            title = {
                Text(
                    text = "📝 ملاحظة للمطور",
                    color = GoldPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = pendingTextValue,
                    onValueChange = { pendingTextValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "اكتب وصف المشكلة أو التحسين المطلوب…",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val label = pendingTextValue.trim()
                    if (label.isNotEmpty()) {
                        currentShapesChange(
                            currentShapes + MarkupShape(
                                id = nextShapeId++,
                                tool = MarkupTool.TEXT,
                                color = currentColor,
                                strokeWidth = currentStroke,
                                points = listOf(textPos),
                                label = label
                            )
                        )
                    }
                    pendingTextPosition = null
                }) {
                    Text("إضافة", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTextPosition = null }) {
                    Text("إلغاء", color = Color.White.copy(alpha = 0.7f), fontFamily = CairoFont)
                }
            }
        )
    }
}

/** رسم شكل هندسي واحد على الـ Canvas. */
private fun DrawScope.drawMarkupShape(
    shape: MarkupShape,
    ox: Float,
    oy: Float,
    imgW: Float,
    imgH: Float
) {
    fun px(p: Offset) = Offset(ox + p.x * imgW, oy + p.y * imgH)
    val strokePx = max(2f, shape.strokeWidth * imgW)

    when (shape.tool) {
        MarkupTool.RECTANGLE -> {
            val (tl, br) = markupRectOf(shape)
            val topLeft = px(tl)
            val boxSize = Size((br.x - tl.x) * imgW, (br.y - tl.y) * imgH)
            drawRect(
                color = shape.color.copy(alpha = shape.color.alpha * 0.16f),
                topLeft = topLeft,
                size = boxSize
            )
            drawRect(
                color = shape.color,
                topLeft = topLeft,
                size = boxSize,
                style = Stroke(width = strokePx)
            )
        }
        MarkupTool.ELLIPSE -> {
            val (tl, br) = markupRectOf(shape)
            val topLeft = px(tl)
            val boxSize = Size((br.x - tl.x) * imgW, (br.y - tl.y) * imgH)
            drawOval(
                color = shape.color.copy(alpha = shape.color.alpha * 0.16f),
                topLeft = topLeft,
                size = boxSize
            )
            drawOval(
                color = shape.color,
                topLeft = topLeft,
                size = boxSize,
                style = Stroke(width = strokePx)
            )
        }
        MarkupTool.LINE -> {
            val a = shape.points.getOrNull(0) ?: return
            val b = shape.points.getOrNull(1) ?: return
            drawLine(
                color = shape.color,
                start = px(a),
                end = px(b),
                strokeWidth = strokePx,
                cap = StrokeCap.Round
            )
        }
        MarkupTool.ARROW -> {
            val a = shape.points.getOrNull(0) ?: return
            val b = shape.points.getOrNull(1) ?: return
            val start = px(a)
            val end = px(b)
            drawLine(color = shape.color, start = start, end = end, strokeWidth = strokePx, cap = StrokeCap.Round)
            val angle = Math.toDegrees(atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())).toFloat()
            val head = max(14f, strokePx * 5f)
            val spread = Math.toRadians(28.0).toFloat()
            for (sign in listOf(1f, -1f)) {
                val ang = angle + 180f + sign * spread
                drawLine(
                    color = shape.color,
                    start = end,
                    end = Offset(
                        end.x + head * cos(ang.toDouble()).toFloat(),
                        end.y + head * sin(ang.toDouble()).toFloat()
                    ),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
            }
        }
        MarkupTool.FREEHAND -> {
            if (shape.points.size < 2) return
            val path = Path()
            path.moveTo(px(shape.points[0]).x, px(shape.points[0]).y)
            for (i in 1 until shape.points.size) {
                val p = px(shape.points[i])
                path.lineTo(p.x, p.y)
            }
            drawPath(path = path, color = shape.color, style = Stroke(width = strokePx, cap = StrokeCap.Round))
        }
        MarkupTool.TEXT -> {
            val p = shape.points.firstOrNull() ?: return
            val anchor = px(p)
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = shape.color.toArgb()
                    textSize = max(18f, imgW * 0.032f)
                    typeface = Typeface.DEFAULT_BOLD
                    setShadowLayer(6f, 0f, 2f, android.graphics.Color.BLACK)
                }
                canvas.nativeCanvas.drawText(shape.label, anchor.x, anchor.y, paint)
            }
        }
        MarkupTool.SELECT -> Unit
    }
}

/**
 * 🎛️ شريط أدوات التحديد الهندسي: اختيار الأداة، اللون، التراجع، والمسح.
 */
@Composable
fun SpatialMarkupToolbar(
    activeTool: MarkupTool,
    activeColor: Color,
    hasShapes: Boolean,
    hasSelection: Boolean = false,
    onToolChange: (MarkupTool) -> Unit,
    onColorChange: (Color) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onDeleteSelected: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MarkupTool.entries.forEach { tool ->
                MarkupToolButton(
                    icon = markupToolIcon(tool),
                    label = tool.label,
                    selected = tool == activeTool,
                    onClick = { onToolChange(tool) }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MarkupPalette.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (color == activeColor) 3.dp else 1.dp,
                            color = if (color == activeColor) Color.White else Color.White.copy(alpha = 0.35f),
                            shape = CircleShape
                        )
                        .clickable { onColorChange(color) }
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onUndo,
                enabled = hasShapes,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Undo,
                    contentDescription = "تراجع",
                    tint = if (hasShapes) GoldPrimary else Color.White.copy(alpha = 0.3f)
                )
            }

            IconButton(
                onClick = onClear,
                enabled = hasShapes,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "مسح كل التحديدات",
                    tint = if (hasShapes) Color(0xFFFF3B30) else Color.White.copy(alpha = 0.3f)
                )
            }
        }

        if (hasSelection) {
            TextButton(
                onClick = onDeleteSelected,
                modifier = Modifier.height(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = Color(0xFFFF3B30),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "حذف الشكل المحدد",
                    color = Color(0xFFFF3B30),
                    fontSize = 11.sp,
                    fontFamily = CairoFont,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

private fun markupToolIcon(tool: MarkupTool): ImageVector = when (tool) {
    MarkupTool.SELECT -> Icons.Default.CompareArrows
    MarkupTool.RECTANGLE -> Icons.Default.CropFree
    MarkupTool.ELLIPSE -> Icons.Default.Circle
    MarkupTool.ARROW -> Icons.Default.ArrowUpward
    MarkupTool.LINE -> Icons.Default.Remove
    MarkupTool.FREEHAND -> Icons.Default.Edit
    MarkupTool.TEXT -> Icons.Default.TextFields
}

@Composable
private fun MarkupToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(min = 46.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (selected) GoldPrimary.copy(alpha = 0.22f) else Color(0xFF1E293B).copy(alpha = 0.8f)
                )
                .border(
                    width = 1.dp,
                    color = if (selected) GoldPrimary else Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) GoldPrimary else Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = if (selected) GoldPrimary else Color.White.copy(alpha = 0.55f),
            fontSize = 8.5.sp,
            fontFamily = CairoFont,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
