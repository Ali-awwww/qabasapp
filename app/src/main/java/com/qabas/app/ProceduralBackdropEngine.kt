package com.qabas.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object ProceduralBackdropEngine {

    fun render(
        filePath: String,
        width: Int,
        height: Int,
        topHex: String = "#070B14",
        bottomHex: String = "#0B0F19",
        accentHex: String = "#22D3EE",
        caption: String? = null,
        brandLine: String = "قبس  |  Qabas",
        footerText: String = "Offline Cinematic Frame"
    ): Boolean {
        val w = width.coerceIn(360, 2160)
        val h = height.coerceIn(640, 3840)
        var bitmap: Bitmap? = null
        return try {
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val top = safeColor(topHex, "#070B14")
            val bottom = safeColor(bottomHex, "#0B0F19")
            val accent = safeColor(accentHex, "#22D3EE")

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

            val glowR = w * 0.55f
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    w / 2f, h * 0.30f, glowR,
                    intArrayOf(withAlpha(accent, 0x52), withAlpha(accent, 0x14), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(w / 2f, h * 0.30f, glowR, glowPaint)

            val counterR = w * 0.42f
            val counterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    w * 0.18f, h * 0.86f, counterR,
                    intArrayOf(withAlpha(accent, 0x2E), Color.TRANSPARENT),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(w * 0.18f, h * 0.86f, counterR, counterPaint)

            drawIslamicLattice(canvas, w, h, accent)
            drawMedallion(canvas, w, h, accent)
            drawGrain(canvas, w, h)

            val vignette = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    w / 2f, h / 2f, Math.max(w, h) * 0.72f,
                    intArrayOf(Color.TRANSPARENT, withAlpha(Color.BLACK, 0x8C)),
                    floatArrayOf(0.62f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), vignette)

            val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accent
                textSize = w * 0.042f
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(brandLine, w / 2f, h * 0.135f, brandPaint)

            if (!caption.isNullOrBlank()) {
                drawCaption(canvas, w, h, caption)
            }

            val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(accent, 0xB4)
                textSize = w * 0.022f
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            }
            canvas.drawText(footerText, w / 2f, h * 0.92f, footerPaint)

            FileOutputStream(File(filePath)).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            true
        } catch (t: Throwable) {
            false
        } finally {
            bitmap?.recycle()
        }
    }

    private fun drawIslamicLattice(canvas: Canvas, w: Int, h: Int, accent: Int) {
        val stepX = w * 0.125f
        val stepY = h * 0.11f
        val starR = w * 0.030f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(accent, 0x16)
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
        }
        var row = 1
        var y = h * 0.20f
        while (y < h * 0.84f && row < 14) {
            var x = if (row % 2 == 0) stepX / 2f else stepX
            var col = 0
            while (x < w - stepX * 0.4f && col < 12) {
                drawStar(canvas, x, y, starR, paint)
                x += stepX
                col++
            }
            y += stepY
            row++
        }
    }

    private fun drawMedallion(canvas: Canvas, w: Int, h: Int, accent: Int) {
        val cx = w / 2f
        val cy = h * 0.50f
        val ringR = w * 0.34f

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(accent, 0x2E)
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
        }
        canvas.drawCircle(cx, cy, ringR, ringPaint)

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(accent, 0x33)
            style = Paint.Style.FILL
        }
        for (i in 0 until 16) {
            val a = 2.0 * PI * i / 16
            val dx = (cos(a) * ringR).toFloat()
            val dy = (sin(a) * ringR).toFloat()
            canvas.drawCircle(cx + dx, cy + dy, w * 0.008f, dotPaint)
        }

        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(accent, 0x22)
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
        }
        drawStar(canvas, cx, cy, w * 0.22f, starPaint)
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(45f)
        drawStar(canvas, 0f, 0f, w * 0.22f, starPaint)
        canvas.restore()

        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(accent, 0x2A)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, w * 0.012f, centerPaint)
    }

    private fun drawGrain(canvas: Canvas, w: Int, h: Int) {
        val random = Random(7331L)
        val count = ((w * h) / 1400).coerceIn(500, 1500)
        val paint = Paint().apply { color = Color.argb(14, 255, 255, 255) }
        repeat(count) {
            canvas.drawPoint(random.nextFloat() * w, random.nextFloat() * h, paint)
        }
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
        canvas.save()
        canvas.translate(cx, cy)
        val rect = RectF(-r, -r, r, r)
        canvas.drawRect(rect, paint)
        canvas.rotate(45f)
        canvas.drawRect(rect, paint)
        canvas.restore()
    }

    @Suppress("DEPRECATION")
    private fun drawCaption(canvas: Canvas, w: Int, h: Int, caption: String) {
        val panelW = w * 0.84f
        val panelX = (w - panelW) / 2f
        val panelY = h * 0.403f

        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(241, 245, 249)
            textSize = w * 0.032f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        }
        val layout = StaticLayout(
            caption,
            tp,
            (panelW * 0.86f).toInt(),
            Layout.Alignment.ALIGN_CENTER,
            1.25f,
            0f,
            true
        )

        val panelBottom = panelY + layout.height.toFloat() + 40f
        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(0x59, 7, 11, 20)
        }
        canvas.drawRoundRect(RectF(panelX, panelY, panelX + panelW, panelBottom), 28f, 28f, panelPaint)

        canvas.save()
        canvas.translate((w - layout.width.toFloat()) / 2f, panelY + 20f)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun safeColor(hex: String, fallback: String): Int {
        return try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            Color.parseColor(fallback)
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}