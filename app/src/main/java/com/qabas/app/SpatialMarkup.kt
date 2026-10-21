package com.qabas.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * 🎯 محرّك التحديد الهندسي على لقطات الشاشة (Spatial Markup Engine).
 *
 * الهدف: تمكين المستخدم من وسم مكان الخلل أو موضع التحسين المطلوب باستخدام
 * أشكال هندسية (مستطيل / دائرة / سهم / خط / توقيع حر / ملاحظة نصية)، مع إمكانية
 * تحريك الأشكال بعد وضعها، ثم دمجها في الصورة النهائية قبل إرسالها للمطور.
 *
 * جميع الإحداثيات تُخزَّن **نسبية** (0..1) نسبةً إلى أبعاد الصورة الأصلية،
 * لذلك تبقى الأشكال في مكانها الصحيح مهما اختلف حجم العرض على الشاشة.
 */
enum class MarkupTool(val label: String, val hint: String) {
    SELECT("تحريك", "اضغط على الشكل ثم اسحبه لتغيير موضعه"),
    RECTANGLE("مستطيل", "اسحب لتحديد منطقة مستطيلة حول المشكلة"),
    ELLIPSE("دائرة", "اسحب لتحديد منطقة دائرية حول المشكلة"),
    ARROW("سهم", "اسحب للإشارة إلى الموضع بدقّة"),
    LINE("خط", "اسحب لربط نقطتين وربط المشكلة بسببها"),
    FREEHAND("توقيع", "ارسم بحرية لتحديد التفاصيل الدقيقة"),
    TEXT("ملاحظة", "انقر على الصورة لإضافة ملاحظة نصية")
}

/**
 * شكل واحد على الصورة.
 * @param points إحداثيات نسبية (0..1): نقطتان للأشكال الثنائية، نقاط متعددة للتوقيع الحر، ونقطة واحدة للملاحظة النصية.
 */
data class MarkupShape(
    val id: Long,
    val tool: MarkupTool,
    val color: Color,
    val strokeWidth: Float, // نسبة من عرض الصورة (0.006 ≈ 6 بكسل على شاشة 1000px)
    val points: List<Offset>,
    val label: String = ""
)

/** ألوان قلم التحديد المتاحة للمستخدم. */
val MarkupPalette: List<Color> = listOf(
    Color(0xFFFF3B30), // أحمر — الأخطاء
    Color(0xFFFFC107), // ذهبي — تحذيرات
    Color(0xFF00E5FF), // سماوي — اقتراحات
    Color(0xFF10B981), // أخضر — تم الإصلاح
    Color(0xFFFFFFFF)  // أبيض — تباين عالٍ
)

/** يعيد الركن العلوي الأيسر والركن الأيمن السفلي للشكل المستطيل. */
internal fun markupRectOf(shape: MarkupShape): Pair<Offset, Offset> {
    val a = shape.points.firstOrNull() ?: Offset.Zero
    val b = shape.points.getOrNull(1) ?: a
    return Offset(min(a.x, b.x), min(a.y, b.y)) to Offset(max(a.x, b.x), max(a.y, b.y))
}

/** المسافة بين نقطة ومقطع مستقيم. */
internal fun markupDistanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val lenSq = dx * dx + dy * dy
    if (lenSq <= 0.0000001f) return hypot(p.x - a.x, p.y - a.y)
    var t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / lenSq
    t = t.coerceIn(0f, 1f)
    val projX = a.x + t * dx
    val projY = a.y + t * dy
    return hypot(p.x - projX, p.y - projY)
}

/** هل النقطة (نسبية) تقع داخل/على الشكل؟ يُستخدم لاختيار الشكل وسحبه. */
internal fun markupHitTest(shape: MarkupShape, p: Offset): Boolean {
    return when (shape.tool) {
        MarkupTool.RECTANGLE -> {
            val (tl, br) = markupRectOf(shape)
            p.x >= tl.x && p.x <= br.x && p.y >= tl.y && p.y <= br.y
        }
        MarkupTool.ELLIPSE -> {
            val (tl, br) = markupRectOf(shape)
            val cx = (tl.x + br.x) / 2f
            val cy = (tl.y + br.y) / 2f
            val rx = max((br.x - tl.x) / 2f, 0.0001f)
            val ry = max((br.y - tl.y) / 2f, 0.0001f)
            val nx = (p.x - cx) / rx
            val ny = (p.y - cy) / ry
            (nx * nx + ny * ny) <= 1f
        }
        MarkupTool.ARROW, MarkupTool.LINE -> {
            val a = shape.points.getOrNull(0) ?: return false
            val b = shape.points.getOrNull(1) ?: return false
            markupDistanceToSegment(p, a, b) <= 0.03f
        }
        MarkupTool.FREEHAND -> {
            if (shape.points.size < 2) return false
            for (i in 0 until shape.points.size - 1) {
                if (markupDistanceToSegment(p, shape.points[i], shape.points[i + 1]) <= 0.03f) return true
            }
            false
        }
        MarkupTool.TEXT -> {
            val a = shape.points.firstOrNull() ?: return false
            hypot(p.x - a.x, p.y - a.y) <= 0.06f
        }
        MarkupTool.SELECT -> false
    }
}

/**
 * 🎨 يدمج الأشكال الهندسية داخل الصورة النقطية (Bitmap)،
 * والنتيجة تُستخدم للحفظ بالمعرض والمشاركة والإرسال للمطور.
 */
object SpatialMarkupRenderer {

    fun render(bitmap: Bitmap, shapes: List<MarkupShape>): Bitmap {
        if (shapes.isEmpty()) return bitmap
        val out = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return bitmap
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        shapes.forEach { s ->
            val argb = s.color.toArgb()
            stroke.color = argb
            fill.color = argb
            stroke.strokeWidth = max(3f, s.strokeWidth * w)

            when (s.tool) {
                MarkupTool.RECTANGLE -> {
                    val (tl, br) = markupRectOf(s)
                    val r = android.graphics.RectF(tl.x * w, tl.y * h, br.x * w, br.y * h)
                    fill.color = android.graphics.Color.argb(0x30, android.graphics.Color.red(argb), android.graphics.Color.green(argb), android.graphics.Color.blue(argb))
                    canvas.drawRect(r, fill)
                    canvas.drawRect(r, stroke)
                }
                MarkupTool.ELLIPSE -> {
                    val (tl, br) = markupRectOf(s)
                    val r = android.graphics.RectF(tl.x * w, tl.y * h, br.x * w, br.y * h)
                    fill.color = android.graphics.Color.argb(0x30, android.graphics.Color.red(argb), android.graphics.Color.green(argb), android.graphics.Color.blue(argb))
                    canvas.drawOval(r, fill)
                    canvas.drawOval(r, stroke)
                }
                MarkupTool.LINE -> {
                    val a = s.points.getOrNull(0) ?: return@forEach
                    val b = s.points.getOrNull(1) ?: return@forEach
                    canvas.drawLine(a.x * w, a.y * h, b.x * w, b.y * h, stroke)
                }
                MarkupTool.ARROW -> {
                    val a = s.points.getOrNull(0) ?: return@forEach
                    val b = s.points.getOrNull(1) ?: return@forEach
                    val x0 = a.x * w; val y0 = a.y * h
                    val x1 = b.x * w; val y1 = b.y * h
                    canvas.drawLine(x0, y0, x1, y1, stroke)
                    val angle = Math.toDegrees(kotlin.math.atan2((y1 - y0).toDouble(), (x1 - x0).toDouble())).toFloat()
                    val head = max(18f, stroke.strokeWidth * 5f)
                    val spread = Math.toRadians(28.0).toFloat()
                    for (sign in listOf(1f, -1f)) {
                        val a1 = angle + 180f + sign * spread
                        canvas.drawLine(
                            x1, y1,
                            x1 + head * kotlin.math.cos(a1.toDouble()).toFloat(),
                            y1 + head * kotlin.math.sin(a1.toDouble()).toFloat(),
                            stroke
                        )
                    }
                }
                MarkupTool.FREEHAND -> {
                    if (s.points.size < 2) return@forEach
                    val path = Path()
                    path.moveTo(s.points[0].x * w, s.points[0].y * h)
                    for (i in 1 until s.points.size) {
                        path.lineTo(s.points[i].x * w, s.points[i].y * h)
                    }
                    canvas.drawPath(path, stroke)
                }
                MarkupTool.TEXT -> {
                    val p = s.points.firstOrNull() ?: return@forEach
                    textPaint.color = argb
                    textPaint.textSize = w * 0.032f
                    textPaint.setShadowLayer(w * 0.006f, 0f, w * 0.002f, android.graphics.Color.BLACK)
                    canvas.drawText(s.label, p.x * w, p.y * h, textPaint)
                }
                MarkupTool.SELECT -> Unit
            }
        }
        return out
    }
}
