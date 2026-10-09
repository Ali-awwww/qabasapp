package com.qabas.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * ImageEditorEngine.kt
 * محرك معالجة وتصدير الصور — موصول بعدة التصميم DesignDesignKit:
 * خلفيات (صورة/سادة/تدرّج/ذكاء اصطناعي/ضبابية)، فلاتر لونية، نصوص عربية متعددة
 * بخطوط مضمّنة، زخارف، أختيار الدقة والصيغة، وعلامة قبس القابلة للإيقاف.
 */

data class ImageAdjustmentParams(
    val brightness: Float = 0f,               // -50..50
    val contrast: Float = 1f,                 // 0.5..1.5
    val saturation: Float = 1f,               // 0..2
    val cropRatio: String = "9:16",
    val filterId: String = "original",
    val bgMode: String = "PHOTO",             // PHOTO / SOLID / GRADIENT / AI / BLUR
    val bgPresetHexes: List<String> = emptyList(),
    val aiImagePath: String? = null,
    val textBlocks: List<DesignTextBlock> = emptyList(),
    val ornamentId: String = "none",
    val showBrandWatermark: Boolean = true,
    val outputFormat: String = "JPEG",        // JPEG / PNG
    val resolutionScale: Float = 1f           // 1x=1080, 1.333f=2K, 2f=4K
)

object ImageEditorEngine {
    private const val TAG = "ImageEditorEngine"

    suspend fun processAndExportImage(
        context: Context,
        inputUri: Uri?,
        params: ImageAdjustmentParams,
        targetWidth: Int = 1080,
        targetHeight: Int = 1920
    ): File? = withContext(Dispatchers.IO) {
        var outputBitmap: Bitmap? = null
        try {
            val (finalW, finalH) = calculateTargetDimensions(params.cropRatio, targetWidth, targetHeight, params.resolutionScale)
            outputBitmap = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(outputBitmap)

            val matrixFilter = ColorMatrixColorFilter(
                designFilterMatrixArray(
                    designFilterById(params.filterId),
                    params.brightness,
                    params.contrast,
                    params.saturation
                )
            )

            // 1. الخلفية حسب الوضع المختار
            val srcBitmap = if (inputUri != null) decodeUri(context, inputUri) else null
            when (params.bgMode) {
                "SOLID", "GRADIENT" -> drawGradientBackground(canvas, finalW, finalH, params.bgPresetHexes)
                "AI" -> {
                    val aiPath = params.aiImagePath
                    val aiBitmap = if (!aiPath.isNullOrBlank() && File(aiPath).exists()) {
                        BitmapFactory.decodeFile(aiPath)
                    } else null
                    if (aiBitmap != null) {
                        drawCoverBitmap(canvas, aiBitmap, finalW, finalH, matrixFilter)
                        aiBitmap.recycle()
                    } else {
                        // بلا خلفية وهمية: تدرج احتياطي معتم على نفس هوية قبس
                        drawGradientBackground(canvas, finalW, finalH, listOf("#0B0F19", "#3B2A10", "#0B0F19"))
                    }
                }
                "BLUR" -> {
                    val base = srcBitmap
                    if (base != null) {
                        val blurred = Bitmap.createScaledBitmap(base, finalW, finalH, true)
                        val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            isFilterBitmap = true
                            maskFilter = BlurMaskFilter(finalW * 0.018f, BlurMaskFilter.Blur.NORMAL)
                            colorFilter = matrixFilter
                        }
                        canvas.drawBitmap(blurred, 0f, 0f, blurPaint)
                        blurred.recycle()
                        base.recycle()
                    } else {
                        drawGradientBackground(canvas, finalW, finalH, listOf("#0B0F19", "#1F2937"))
                    }
                }
                else -> {
                    // PHOTO (الافتراضي)
                    if (srcBitmap != null) {
                        val cropped = applyCrop(srcBitmap, params.cropRatio)
                        srcBitmap.recycle()
                        val scaled = Bitmap.createScaledBitmap(cropped, finalW, finalH, true)
                        cropped.recycle()
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            isFilterBitmap = true
                            colorFilter = matrixFilter
                        }
                        canvas.drawBitmap(scaled, 0f, 0f, paint)
                        scaled.recycle()
                    } else {
                        drawGradientBackground(canvas, finalW, finalH, listOf("#0B0F19", "#3B2A10", "#0B0F19"))
                    }
                }
            }

            // 2. تظليل سفلي خفيف لقراءة النص (للصور فقط)
            if (params.bgMode == "PHOTO" || params.bgMode == "BLUR") {
                val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, finalH * 0.45f, 0f, finalH.toFloat(),
                        intArrayOf(android.graphics.Color.TRANSPARENT, android.graphics.Color.argb(200, 8, 10, 18)),
                        null,
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, finalW.toFloat(), finalH.toFloat(), vignettePaint)
            }

            // 3. إطار ذهبي أساسي (9:16 / 1:1)
            if (params.cropRatio == "9:16" || params.cropRatio == "1:1") {
                val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb(70, 232, 197, 71)
                    style = Paint.Style.STROKE
                    strokeWidth = 4f * params.resolutionScale
                }
                val inset = 24f * params.resolutionScale
                canvas.drawRoundRect(RectF(inset, inset, finalW - inset, finalH - inset), 20f, 20f, framePaint)
            }

            // 4. الزخرفة المختارة
            DesignOrnamentRenderer.draw(canvas, finalW, finalH, designOrnamentById(params.ornamentId))

            // 5. نصوص التصميم (كل كتلة بخطها ولونها واتجاهها وموضعها)
            for (block in params.textBlocks) {
                DesignCanvasText.drawBlock(canvas, finalW, finalH, block, designFontTypeface(context, block.style.font))
            }

            // 6. علامة قبس (قابلة للإيقاف)
            if (params.showBrandWatermark) {
                drawBrandWatermark(canvas, finalW, finalH, params.resolutionScale)
            }

            // 7. الحفظ
            val isPng = params.outputFormat.equals("PNG", ignoreCase = true)
            val ext = if (isPng) "png" else "jpg"
            val outFile = File(context.cacheDir, "qabas_edited_${System.currentTimeMillis()}.$ext")
            val fos = FileOutputStream(outFile)
            if (isPng) {
                outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            } else {
                outputBitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
            }
            fos.flush()
            fos.close()

            saveImageToGallery(context, outputBitmap, "Qabas_Studio_${System.currentTimeMillis()}", isPng)
            return@withContext outFile
        } catch (e: Exception) {
            Log.e(TAG, "Error editing image: ${e.message}", e)
            return@withContext null
        } finally {
            outputBitmap?.recycle()
        }
    }

    private fun decodeUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bmp = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            bmp
        } catch (e: Exception) {
            Log.w(TAG, "decodeUri failed: ${e.message}")
            null
        }
    }

    private fun drawGradientBackground(canvas: Canvas, w: Int, h: Int, hexes: List<String>) {
        val colors = if (hexes.isNullOrEmpty()) intArrayOf(
            android.graphics.Color.parseColor("#0B0F19"),
            android.graphics.Color.parseColor("#3B2A10")
        ) else hexes.map { runCatching { android.graphics.Color.parseColor(it) }.getOrDefault(android.graphics.Color.rgb(11, 15, 25)) }.toIntArray()
        if (colors.size == 1) {
            val solidPaint = Paint().apply { this.color = colors[0] }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), solidPaint)
            return
        }
        val gradient = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), colors, null, Shader.TileMode.CLAMP)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gradient }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }

    private fun drawCoverBitmap(canvas: Canvas, src: Bitmap, w: Int, h: Int, filter: android.graphics.ColorFilter?) {
        val srcW = src.width
        val srcH = src.height
        if (srcW <= 0 || srcH <= 0) return
        val scale = maxOf(w.toFloat() / srcW, h.toFloat() / srcH)
        val dw = (srcW * scale).toInt()
        val dh = (srcH * scale).toInt()
        val left = ((w - dw) / 2f)
        val top = ((h - dh) / 2f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            colorFilter = filter
        }
        canvas.drawBitmap(src, null, RectF(left, top, left + dw, top + dh), paint)
    }

    private fun applyCrop(src: Bitmap, cropRatio: String): Bitmap {
        val srcW = src.width
        val srcH = src.height

        val targetRatio = when (cropRatio) {
            "9:16" -> 9f / 16f
            "1:1" -> 1f
            "16:9" -> 16f / 9f
            "4:5" -> 4f / 5f
            else -> return src
        }

        val srcRatio = srcW.toFloat() / srcH.toFloat()
        var cropW = srcW
        var cropH = srcH
        var cropX = 0
        var cropY = 0

        if (srcRatio > targetRatio) {
            cropW = (srcH * targetRatio).toInt()
            cropX = (srcW - cropW) / 2
        } else {
            cropH = (srcW / targetRatio).toInt()
            cropY = (srcH - cropH) / 2
        }

        cropW = cropW.coerceAtLeast(1).coerceAtMost(srcW)
        cropH = cropH.coerceAtLeast(1).coerceAtMost(srcH)

        return Bitmap.createBitmap(src, cropX, cropY, cropW, cropH)
    }

    private fun calculateTargetDimensions(cropRatio: String, defaultW: Int, defaultH: Int, scale: Float): Pair<Int, Int> {
        val (baseW, baseH) = when (cropRatio) {
            "9:16" -> Pair(1080, 1920)
            "1:1" -> Pair(1080, 1080)
            "16:9" -> Pair(1920, 1080)
            "4:5" -> Pair(1080, 1350)
            else -> Pair(defaultW, defaultH)
        }
        return Pair((baseW * scale).toInt(), (baseH * scale).toInt())
    }

    private fun drawBrandWatermark(canvas: Canvas, width: Int, height: Int, scale: Float) {
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(200, 232, 197, 71)
            textSize = (width * 0.028f).coerceIn(18f, 32f) * scale
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            setShadowLayer(4f, 1f, 1f, android.graphics.Color.BLACK)
        }
        canvas.drawText("✦ قَبَس | QABAS STUDIO ✦", width / 2f, height - (40f * scale), brandPaint)
    }

    private fun saveImageToGallery(context: Context, bitmap: Bitmap, title: String, isPng: Boolean) {
        val ext = if (isPng) "png" else "jpg"
        val mime = if (isPng) "image/png" else "image/jpeg"
        val filename = "$title.$ext"
        var fos: OutputStream? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Qabas")
            }
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)?.let { uri ->
                fos = context.contentResolver.openOutputStream(uri)
            }
        } else {
            val imagesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/Qabas"
            )
            if (!imagesDir.exists()) imagesDir.mkdir()
            fos = FileOutputStream(File(imagesDir, filename))
        }
        fos?.use {
            if (isPng) bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            else bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
        }
    }
}
