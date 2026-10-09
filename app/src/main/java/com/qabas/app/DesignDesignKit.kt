package com.qabas.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.res.ResourcesCompat

/**
 * DesignDesignKit.kt
 * عدة التصميم المشتركة بين استوديو الصور واستوديو بطاقات الحديث:
 * مكتبة خطوط عربية حقيقية (مضمّنة في res/font)، أنماط النص، الخلفيات،
 * الفلاتر اللونية، الزخارف، ورسم النص العربي داخل Canvas للتصدير
 * بما يطابق المعاينة على الشاشة — لا تصميم وهمي.
 */

// ---------- مكتبة الخطوط العربية (مضمّنة محلياً) ----------
enum class DesignFont(val displayName: String, val hint: String, val resId: Int) {
    AMIRI("أميري", "قرآني", R.font.amiri_regular),
    SCHEHERAZADE("شهرزاد", "نسخ", R.font.scheherazade_regular),
    AREF_RUQAA("عارف رقعة", "ديواني", R.font.aref_ruqaa_regular),
    REEM_KUFI("ريم كوفي", "كوفي", R.font.reem_kufi_regular),
    CAIRO("القاهرة", "عصري", R.font.cairo_regular),
    TAJAWAL("تجوّل", "واضح", R.font.tajawal_regular)
}

fun designFontFamily(font: DesignFont): FontFamily =
    FontFamily(Font(font.resId, weight = FontWeight.Normal))

fun designFontTypeface(context: Context, font: DesignFont): Typeface =
    ResourcesCompat.getFont(context, font.resId) ?: Typeface.DEFAULT

// ---------- أنماط النص ----------
data class DesignTextTheme(val id: String, val label: String, val color: Color, val hex: String)

val DesignTextThemes = listOf(
    DesignTextTheme("GOLD_DEEP", "ذهبي ملكي", Color(0xFFE8C547), "#E8C547"),
    DesignTextTheme("ROYAL_WHITE", "أبيض ناصع", Color(0xFFF8FAFC), "#F8FAFC"),
    DesignTextTheme("NEON_EMERALD", "زمردي إسلامي", Color(0xFF10B981), "#10B981"),
    DesignTextTheme("CYAN_AURA", "سماوي تقني", Color(0xFF22D3EE), "#22D3EE"),
    DesignTextTheme("PURPLE_ROYAL", "بنفسجي ملكي", Color(0xFF8B5CF6), "#8B5CF6"),
    DesignTextTheme("AMBER_GLOW", "كهرماني دافئ", Color(0xFFF5AC37), "#F5AC37")
)

fun designThemeById(id: String): DesignTextTheme =
    DesignTextThemes.firstOrNull { it.id == id } ?: DesignTextThemes[0]

enum class DesignAlign(val label: String) {
    CENTER("توسيط"), RIGHT("يمين"), LEFT("يسار")
}

fun designTextAlign(a: DesignAlign): TextAlign = when (a) {
    DesignAlign.CENTER -> TextAlign.Center
    DesignAlign.RIGHT -> TextAlign.Right
    DesignAlign.LEFT -> TextAlign.Left
}

data class DesignTextStyle(
    val font: DesignFont = DesignFont.AMIRI,
    val themeId: String = "GOLD_DEEP",
    val sizeSp: Float = 30f,
    val bold: Boolean = true,
    val align: DesignAlign = DesignAlign.CENTER,
    val rotation: Float = 0f,
    val glow: Boolean = true
)

data class DesignTextBlock(
    val id: Int,
    val text: String = "",
    val style: DesignTextStyle = DesignTextStyle(),
    val posX: Float = 0.5f,
    val posY: Float = 0.5f
)

// ---------- الخلفيات ----------
data class DesignBgPreset(val id: String, val label: String, val icon: String, val hexes: List<String>)

val DesignSolidPresets = listOf(
    DesignBgPreset("SOLID_BLACK", "ليل عميق", "🌑", listOf("#0B0F19")),
    DesignBgPreset("SOLID_GOLD", "ذهبي داكن", "✨", listOf("#3B2A10")),
    DesignBgPreset("SOLID_EMERALD", "زمردي", "💚", listOf("#0B3B2E")),
    DesignBgPreset("SOLID_NAVY", "كحلي", "🌊", listOf("#0B1B3B")),
    DesignBgPreset("SOLID_WALNUT", "بنّي أندلسي", "🕌", listOf("#2A1B0E"))
)

val DesignGradientPresets = listOf(
    DesignBgPreset("GRAD_NIGHT_GOLD", "ليل وذهب", "🌌", listOf("#0B0F19", "#3B2A10", "#0B0F19")),
    DesignBgPreset("GRAD_PURPLE_CYAN", "بنفسجي سماوي", "🔮", listOf("#1E1B4B", "#0B0F19", "#164E63")),
    DesignBgPreset("GRAD_EMERALD_GOLD", "زمرد ذهبي", "🟢", listOf("#064E3B", "#3B2A10")),
    DesignBgPreset("GRAD_DAWN", "فجر", "🌅", listOf("#431407", "#7C2D12", "#0B0F19")),
    DesignBgPreset("GRAD_MARBLE", "رخام غامق", "🏛", listOf("#111827", "#1F2937", "#030712"))
)

data class DesignBgMode(val id: String, val label: String, val icon: String)

val DesignBgModes = listOf(
    DesignBgMode("PHOTO", "الصورة", "🖼️"),
    DesignBgMode("SOLID", "لون سادة", "🎨"),
    DesignBgMode("GRADIENT", "تدرّج", "🌈"),
    DesignBgMode("AI", "ذكاء اصطناعي", "🧠"),
    DesignBgMode("BLUR", "ضبابية", "🌫️")
)

fun designBgColors(hexes: List<String>): List<Color> =
    hexes.map { hex -> runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF0B0F19)) }

// ---------- الفلاتر اللونية ----------
enum class DesignFilter(val id: String, val label: String) {
    ORIGINAL("original", "أصلي"),
    GOLDEN("golden", "ذهبي دافئ"),
    CINEMATIC("cinematic", "سينمائي"),
    VINTAGE("vintage", "فينتج"),
    BLACK_WHITE("bw", "أبيض وأسود"),
    EMERALD("emerald", "زمردي")
}

fun designFilterById(id: String): DesignFilter =
    DesignFilter.entries.firstOrNull { it.id == id } ?: DesignFilter.ORIGINAL

/**
 * مصفوفة لونية 4×5 موحّدة: تُستخدم في المعاينة (Compose) وفي التصدير (android Graphics)
 * بنفس القيم — لا اختلاف بين ما تراه وما يُصدَّر.
 */
fun designFilterMatrixArray(filter: DesignFilter, brightness: Float, contrast: Float, saturation: Float): FloatArray {
    val cm = android.graphics.ColorMatrix()
    when (filter) {
        DesignFilter.GOLDEN -> cm.set(floatArrayOf(
            1.08f, 0f, 0f, 0f, 6f,
            0f, 1.02f, 0f, 0f, 2f,
            0f, 0f, 0.88f, 0f, -6f,
            0f, 0f, 0f, 1f, 0f
        ))
        DesignFilter.CINEMATIC -> cm.set(floatArrayOf(
            1.04f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 0.96f, 0f, 2f,
            0f, 0f, 0f, 1f, 0f
        ))
        DesignFilter.VINTAGE -> cm.set(floatArrayOf(
            0.85f, 0.10f, 0f, 0f, 10f,
            0.06f, 0.90f, 0f, 0f, 9f,
            0f, 0f, 0.75f, 0f, 14f,
            0f, 0f, 0f, 1f, 0f
        ))
        DesignFilter.BLACK_WHITE -> cm.setSaturation(0f)
        DesignFilter.EMERALD -> cm.set(floatArrayOf(
            0.95f, 0f, 0f, 0f, -5f,
            0f, 1.08f, 0f, 0f, 4f,
            0f, 0f, 0.94f, 0f, 3f,
            0f, 0f, 0f, 1f, 0f
        ))
        else -> {}
    }
    val scale = contrast
    val translate = (-0.5f * scale + 0.5f) * 255f
    val adjust = android.graphics.ColorMatrix(floatArrayOf(
        scale, 0f, 0f, 0f, translate + brightness,
        0f, scale, 0f, 0f, translate + brightness,
        0f, 0f, scale, 0f, translate + brightness,
        0f, 0f, 0f, 1f, 0f
    ))
    cm.postConcat(adjust)
    return cm.array
}

fun composeColorMatrixFrom(array: FloatArray): ColorMatrix {
    return ColorMatrix(array)
}

// ---------- الزخارف ----------
enum class DesignOrnament(val id: String, val label: String, val icon: String) {
    NONE("none", "بدون", "🚫"),
    AYAT("ayat", "علامة ۞", "۞"),
    BRACKETS("brackets", "أقواس ﴾﴿", "﴾"),
    DIVIDER("divider", "فاصل ذهبي", "─"),
    STARS("stars", "نجوم ✦", "✦"),
    CRESCENT("crescent", "هلال ☾", "☾"),
    CORNERS("corners", "زوايا مذهّبة", "⌜"),
    DOUBLE_FRAME("frame", "إطار مزدوج", "▣")
}

fun designOrnamentById(id: String): DesignOrnament =
    DesignOrnament.entries.firstOrNull { it.id == id } ?: DesignOrnament.NONE

/**
 * يُرسم نفس الزخرفة في المعاينة والتصدير عبر نفس الدالة (nativeCanvas في المعاينة).
 */
object DesignOrnamentRenderer {
    fun draw(canvas: Canvas, w: Int, h: Int, ornament: DesignOrnament, colorHex: String = "#E8C547") {
        if (ornament == DesignOrnament.NONE || w <= 0 || h <= 0) return
        val color = runCatching { android.graphics.Color.parseColor(colorHex) }.getOrDefault(android.graphics.Color.rgb(232, 197, 71))
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            setShadowLayer(6f, 2f, 2f, android.graphics.Color.argb(140, 0, 0, 0))
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = w * 0.004f
        }
        when (ornament) {
            DesignOrnament.AYAT -> {
                textPaint.textSize = w * 0.10f
                canvas.drawText("۞", w / 2f, h * 0.085f, textPaint)
            }
            DesignOrnament.BRACKETS -> {
                textPaint.textSize = w * 0.11f
                canvas.drawText("﴾", w * 0.075f, h * 0.52f, textPaint)
                canvas.drawText("﴿", w * 0.925f, h * 0.52f, textPaint)
            }
            DesignOrnament.DIVIDER -> {
                val y = h * 0.16f
                canvas.drawLine(w * 0.14f, y, w * 0.86f, y, strokePaint)
                textPaint.textSize = w * 0.045f
                canvas.drawText("◈", w / 2f, y + w * 0.02f, textPaint)
            }
            DesignOrnament.STARS -> {
                textPaint.textSize = w * 0.038f
                canvas.drawText("✦ ✦ ✦", w / 2f, h * 0.92f, textPaint)
            }
            DesignOrnament.CRESCENT -> {
                textPaint.textSize = w * 0.095f
                canvas.drawText("☾", w * 0.12f, h * 0.11f, textPaint)
            }
            DesignOrnament.CORNERS -> {
                val r = w * 0.07f
                val gap = w * 0.05f
                strokePaint.strokeWidth = w * 0.008f
                canvas.drawArc(RectF(gap, gap, gap + 2 * r, gap + 2 * r), 180f, 90f, false, strokePaint)
                canvas.drawArc(RectF(w - gap - 2 * r, gap, w - gap, gap + 2 * r), 270f, 90f, false, strokePaint)
                canvas.drawArc(RectF(gap, h - gap - 2 * r, gap + 2 * r, h - gap), 90f, 90f, false, strokePaint)
                canvas.drawArc(RectF(w - gap - 2 * r, h - gap - 2 * r, w - gap, h - gap), 0f, 90f, false, strokePaint)
            }
            DesignOrnament.DOUBLE_FRAME -> {
                val outer = RectF(w * 0.03f, h * 0.03f, w * 0.97f, h * 0.97f)
                val inner = RectF(w * 0.085f, h * 0.085f, w * 0.915f, h * 0.915f)
                canvas.drawRoundRect(outer, w * 0.03f, w * 0.03f, strokePaint)
                val innerPaint = Paint(strokePaint).apply { strokeWidth = w * 0.0022f }
                canvas.drawRoundRect(inner, w * 0.05f, w * 0.05f, innerPaint)
            }
            else -> {}
        }
    }
}

// ---------- رسم النص العربي في التصدير ----------
object DesignCanvasText {
    fun drawBlock(canvas: Canvas, w: Int, h: Int, block: DesignTextBlock, typeface: Typeface) {
        if (block.text.isBlank()) return
        val scale = w / 1080f
        val textSize = block.style.sizeSp * scale
        val theme = designThemeById(block.style.themeId)
        val paint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            color = android.graphics.Color.parseColor(theme.hex)
            this.textSize = textSize
            isFakeBoldText = block.style.bold
            if (block.style.glow) {
                setShadowLayer(8f * scale, 2f * scale, 2f * scale, android.graphics.Color.argb(180, 0, 0, 0))
            }
        }
        val layout = android.text.StaticLayout.Builder
            .obtain(block.text, 0, block.text.length, paint, (w * 0.8f).toInt())
            .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.25f)
            .setIncludePad(true)
            .build()
        canvas.save()
        canvas.translate(block.posX * w, block.posY * h)
        canvas.rotate(block.style.rotation)
        val textW = layout.width.toFloat()
        val anchorX = when (block.style.align) {
            DesignAlign.CENTER -> -textW / 2f
            DesignAlign.RIGHT -> w * 0.10f - textW
            DesignAlign.LEFT -> -w * 0.10f
        }
        canvas.translate(anchorX, -(layout.height / 2f))
        layout.draw(canvas)
        canvas.restore()
    }
}