package com.qabas.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * تحليل محلي للإطارات + Optical Flow حقيقي عبر OpenCV (Farneback).
 *
 * عند توفر OpenCV:
 *   - calcOpticalFlowFarneback بين كل زوج إطارات
 *   - mean magnitude, mean(u,v), radial correlation → motionType دقيق
 *
 * عند غياب OpenCV:
 *   - سقوط آمن إلى Frame Differencing
 *
 * المخرجات تُحقن في visualTraits:
 *   primary:#RRGGBB | bg:#… | filter=… | motion=static|slow_zoom|pan|punch_in
 */
object LocalImageAnalyzer {

    private const val TAG = "LocalImageAnalyzer"

    @Volatile
    private var openCvReady: Boolean? = null

    data class FrameMetrics(
        val dominantHex: List<String>,
        val primaryHex: String,
        val secondaryHex: String,
        val avgBrightness: Float,
        val contrast: Float,
        val sharpness: Float,
        val filterHint: String
    )

    data class VideoLocalStyle(
        val primaryHex: String,
        val secondaryHex: String,
        val backgroundHex: String,
        val filterHint: String,
        val avgBrightness: Float,
        val avgContrast: Float,
        val avgSharpness: Float,
        val motionType: String,
        val motionScore: Float,
        /** true إن استُخدم Farneback فعلياً */
        val usedRealOpticalFlow: Boolean,
        val visualTraits: List<String>,
        val framesUsed: Int,
        val dominantPalette: List<String> = emptyList()
    )

    /** تهيئة كسولة لـ OpenCV — تُستدعى مرة واحدة */
    fun ensureOpenCv(): Boolean {
        openCvReady?.let { return it }
        return try {
            val cls = Class.forName("org.opencv.android.OpenCVLoader")
            val method = cls.getMethod("initLocal")
            val ok = method.invoke(null) as Boolean
            openCvReady = ok
            Log.i(TAG, if (ok) "OpenCV loaded (real Optical Flow enabled)" else "OpenCV initLocal returned false")
            ok
        } catch (e: Throwable) {
            openCvReady = false
            Log.w(TAG, "OpenCV not available — fallback to frame differencing: ${e.message}")
            false
        }
    }

    fun analyzeFrames(frameFiles: List<File>, maxFrames: Int = 8): VideoLocalStyle? {
        if (frameFiles.isEmpty()) return null
        val bitmaps = mutableListOf<Bitmap>()
        val metrics = mutableListOf<FrameMetrics>()

        try {
            for (f in frameFiles.take(maxFrames)) {
                if (!f.exists() || f.length() < 100) continue
                try {
                    val bmp = decodeSampled(f, maxSide = 320) ?: continue
                    bitmaps += bmp
                    metrics += analyzeBitmap(bmp)
                } catch (e: Exception) {
                    Log.w(TAG, "frame analyze failed: ${f.name}: ${e.message}")
                }
            }
            if (metrics.isEmpty()) return null

            val usable = if (metrics.size >= 3) {
                metrics.sortedByDescending { it.sharpness }.take(max(2, metrics.size - 1))
            } else metrics

            val primary = mostCommonHex(usable.map { it.primaryHex }) ?: usable.first().primaryHex
            val secondary = mostCommonHex(usable.map { it.secondaryHex }) ?: usable.first().secondaryHex
            val avgB = usable.map { it.avgBrightness }.average().toFloat()
            val avgC = usable.map { it.contrast }.average().toFloat()
            val avgS = usable.map { it.sharpness }.average().toFloat()
            val filter = majorityFilter(usable.map { it.filterHint }, avgB, avgC, primary)
            val bg = if (avgB < 0.35f) darken(primary, 0.82f) else darken(primary, 0.72f)

            val useCv = ensureOpenCv()
            val (motionScore, motionType, realFlow) = if (useCv && bitmaps.size >= 2) {
                try {
                    val r = estimateMotionFarneback(bitmaps)
                    Triple(r.first, r.second, true)
                } catch (e: Exception) {
                    Log.w(TAG, "Farneback failed, fallback: ${e.message}")
                    val r = estimateMotionDiff(bitmaps)
                    Triple(r.first, r.second, false)
                }
            } else {
                val r = estimateMotionDiff(bitmaps)
                Triple(r.first, r.second, false)
            }

            val traits = buildList {
                add("primary:$primary")
                add("bg:$bg")
                add("secondary:$secondary")
                add("filter=$filter")
                add("motion=$motionType")
                add("motionScore:" + "%.3f".format(motionScore))
                if (realFlow) add("opticalFlow=farneback")
                add("سطوع محلي ${(avgB * 100).toInt()}%")
                add("تباين محلي ${(avgC * 100).toInt()}%")
                when (filter) {
                    "soft_desert" -> {
                        add("صحراء / دفء رملي")
                        add("إضاءة غروب ناعمة")
                    }
                    "cool_emerald" -> {
                        add("أخضر زمردي")
                        add("أجواء مقدسة داكنة")
                    }
                    "high_contrast_dark" -> {
                        add("تباين عالٍ داكن")
                        add("دراما بصرية")
                    }
                    else -> {
                        add("لمسات ذهبية دافئة")
                        add("تدرج سينمائي")
                    }
                }
                when (motionType) {
                    "static" -> add("كاميرا ثابتة خاشعة")
                    "slow_zoom" -> add("اقتراب بطيء (slow zoom)")
                    "pan" -> add("حركة أفقية ناعمة")
                    "punch_in" -> add("قطع سريع / punch-in")
                }
            }

            Log.d(
                TAG,
                "Local style: $filter | primary=$primary | motion=$motionType ($motionScore) | realOF=$realFlow"
            )

            return VideoLocalStyle(
                primaryHex = primary,
                secondaryHex = secondary,
                backgroundHex = bg,
                filterHint = filter,
                avgBrightness = avgB,
                avgContrast = avgC,
                avgSharpness = avgS,
                motionType = motionType,
                motionScore = motionScore,
                usedRealOpticalFlow = realFlow,
                visualTraits = traits,
                framesUsed = usable.size
            )
        } finally {
            bitmaps.forEach { it.recycle() }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Optical Flow الحقيقي — Farneback (OpenCV)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Farneback dense optical flow بين كل زوج إطارات متتالية.
     * يستخرج mean magnitude, mean u/v, radial correlation لتمييز Zoom عن Pan.
     */
    private fun estimateMotionFarneback(frames: List<Bitmap>): Pair<Float, String> {
        val matClass = Class.forName("org.opencv.core.Mat")
        val cvType = Class.forName("org.opencv.core.CvType")
        val imgproc = Class.forName("org.opencv.imgproc.Imgproc")
        val video = Class.forName("org.opencv.video.Video")
        val utils = Class.forName("org.opencv.android.Utils")

        val colorBgr2Gray = imgproc.getField("COLOR_BGR2GRAY").getInt(null)
        val bitmapToMat = utils.getMethod("bitmapToMat", Bitmap::class.java, matClass)
        val cvtColor = imgproc.getMethod(
            "cvtColor", matClass, matClass, Int::class.javaPrimitiveType
        )
        val calcFlow = video.getMethod(
            "calcOpticalFlowFarneback",
            matClass, matClass, matClass,
            Double::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        val farnebackGaussian = try {
            video.getField("OPTFLOW_FARNEBACK_GAUSSIAN").getInt(null)
        } catch (_: Exception) {
            0
        }

        var sumMag = 0.0
        var sumU = 0.0
        var sumV = 0.0
        var sumRadial = 0.0
        var sumRadialW = 0.0
        var pairs = 0
        var sampleCount = 0

        for (i in 0 until frames.size - 1) {
            val prevMat = matClass.getDeclaredConstructor().newInstance()
            val nextMat = matClass.getDeclaredConstructor().newInstance()
            val prevGray = matClass.getDeclaredConstructor().newInstance()
            val nextGray = matClass.getDeclaredConstructor().newInstance()
            val flow = matClass.getDeclaredConstructor().newInstance()

            try {
                bitmapToMat.invoke(null, frames[i], prevMat)
                bitmapToMat.invoke(null, frames[i + 1], nextMat)
                cvtColor.invoke(null, prevMat, prevGray, colorBgr2Gray)
                cvtColor.invoke(null, nextMat, nextGray, colorBgr2Gray)

                // pyr_scale=0.5, levels=3, winsize=15, iterations=3, poly_n=5, poly_sigma=1.2
                calcFlow.invoke(
                    null,
                    prevGray, nextGray, flow,
                    0.5, 3, 15, 3, 5, 1.2, farnebackGaussian
                )

                val rows = flow.javaClass.getMethod("rows").invoke(flow) as Int
                val cols = flow.javaClass.getMethod("cols").invoke(flow) as Int
                val getMethod = flow.javaClass.getMethod(
                    "get", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, FloatArray::class.java
                )
                val buf = FloatArray(2)
                val cx = cols / 2.0
                val cy = rows / 2.0
                val step = max(1, min(rows, cols) / 40)

                var y = 0
                while (y < rows) {
                    var x = 0
                    while (x < cols) {
                        getMethod.invoke(flow, y, x, buf)
                        val u = buf[0].toDouble()
                        val v = buf[1].toDouble()
                        val mag = sqrt(u * u + v * v)
                        sumMag += mag
                        sumU += u
                        sumV += v
                        sampleCount++

                        val dx = x - cx
                        val dy = y - cy
                        val r = sqrt(dx * dx + dy * dy)
                        if (r > 4.0 && mag > 0.15) {
                            val radialDot = (u * dx + v * dy) / (mag * r)
                            sumRadial += radialDot
                            sumRadialW += 1.0
                        }
                        x += step
                    }
                    y += step
                }
                pairs++
            } finally {
                for (m in listOf(prevMat, nextMat, prevGray, nextGray, flow)) {
                    try {
                        m.javaClass.getMethod("release").invoke(m)
                    } catch (_: Exception) {
                    }
                }
            }
        }

        if (sampleCount == 0 || pairs == 0) return 0f to "static"

        val meanMag = (sumMag / sampleCount).toFloat()
        val meanU = sumU / sampleCount
        val meanV = sumV / sampleCount
        val radial = if (sumRadialW > 0) (sumRadial / sumRadialW) else 0.0
        val score = (meanMag / 6.0f).coerceIn(0f, 1f)
        val type = classifyFlow(meanMag, meanU, meanV, radial)

        Log.d(
            TAG,
            "Farneback: mag=" + "%.3f".format(meanMag) +
                " u=" + "%.3f".format(meanU) +
                " v=" + "%.3f".format(meanV) +
                " radial=" + "%.3f".format(radial) +
                " type=" + type
        )
        return score to type
    }

    private fun classifyFlow(
        meanMag: Float,
        meanU: Double,
        meanV: Double,
        radial: Double
    ): String {
        if (meanMag < 0.35f) return "static"

        val absU = abs(meanU)
        val absV = abs(meanV)
        val horizontalDominant = absU > absV * 1.6 && absU > 0.4

        return when {
            meanMag > 3.2f -> "punch_in"
            abs(radial) > 0.35 && meanMag in 0.4f..3.0f -> "slow_zoom"
            horizontalDominant && abs(radial) < 0.28 -> "pan"
            meanMag > 1.8f -> "punch_in"
            meanMag > 0.7f -> "slow_zoom"
            else -> "static"
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Fallback: Frame Differencing
    // ═══════════════════════════════════════════════════════════════

    private fun estimateMotionDiff(frames: List<Bitmap>): Pair<Float, String> {
        if (frames.size < 2) return 0f to "static"

        var totalDiff = 0.0
        var pairs = 0
        val step = 4

        for (i in 0 until frames.size - 1) {
            val a = frames[i]
            val b = frames[i + 1]
            val w = min(a.width, b.width)
            val h = min(a.height, b.height)
            if (w < 16 || h < 16) continue

            var sum = 0L
            var count = 0
            var y = 0
            while (y < h) {
                var x = 0
                while (x < w) {
                    val pa = a.getPixel(x, y)
                    val pb = b.getPixel(x, y)
                    sum += abs(Color.red(pa) - Color.red(pb)) +
                        abs(Color.green(pa) - Color.green(pb)) +
                        abs(Color.blue(pa) - Color.blue(pb))
                    count++
                    x += step
                }
                y += step
            }
            if (count > 0) {
                totalDiff += (sum.toDouble() / count) / 255.0
                pairs++
            }
        }

        if (pairs == 0) return 0f to "static"
        val score = (totalDiff / pairs).toFloat().coerceIn(0f, 1f)
        val type = when {
            score < 0.045f -> "static"
            score < 0.12f -> "slow_zoom"
            score < 0.22f -> "pan"
            else -> "punch_in"
        }
        return score to type
    }

    private fun analyzeBitmap(bmp: Bitmap): FrameMetrics {
        val w = bmp.width
        val h = bmp.height
        val step = max(1, min(w, h) / 48)
        val buckets = IntArray(512)
        var sumLuma = 0.0
        var sumLumaSq = 0.0
        var count = 0
        var edgeAcc = 0.0
        var edgeN = 0

        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val p = bmp.getPixel(x, y)
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)
                val br = (r shr 5)
                val bg = (g shr 5)
                val bb = (b shr 5)
                val idx = (br shl 6) or (bg shl 3) or bb
                buckets[idx]++

                val luma = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                sumLuma += luma
                sumLumaSq += luma * luma
                count++

                if (x + step < w) {
                    val p2 = bmp.getPixel(x + step, y)
                    val l2 = (0.299 * Color.red(p2) + 0.587 * Color.green(p2) + 0.114 * Color.blue(p2)) / 255.0
                    edgeAcc += abs(luma - l2)
                    edgeN++
                }
                x += step
            }
            y += step
        }

        val avgB = if (count > 0) (sumLuma / count).toFloat() else 0.5f
        val variance = if (count > 0) {
            ((sumLumaSq / count) - (sumLuma / count) * (sumLuma / count)).toFloat()
        } else 0f
        val contrast = sqrt(variance.coerceAtLeast(0f)).coerceIn(0f, 1f)
        val sharpness = if (edgeN > 0) (edgeAcc / edgeN).toFloat() else 0f

        val top = buckets.withIndex().sortedByDescending { it.value }.take(4)
        fun bucketToHex(idx: Int): String {
            val br = (idx shr 6) and 7
            val bg = (idx shr 3) and 7
            val bb = idx and 7
            val r = (br * 36 + 18).coerceIn(0, 255)
            val g = (bg * 36 + 18).coerceIn(0, 255)
            val b = (bb * 36 + 18).coerceIn(0, 255)
            return "#%02X%02X%02X".format(r, g, b)
        }
        val primary = if (top.isNotEmpty()) bucketToHex(top[0].index) else "#D4AF37"
        val secondary = if (top.size > 1) bucketToHex(top[1].index) else primary
        val dominant = top.take(3).map { bucketToHex(it.index) }
        val filter = inferFilter(primary, avgB, contrast)

        return FrameMetrics(
            dominantHex = dominant,
            primaryHex = primary,
            secondaryHex = secondary,
            avgBrightness = avgB,
            contrast = contrast,
            sharpness = sharpness,
            filterHint = filter
        )
    }

    private fun inferFilter(primaryHex: String, brightness: Float, contrast: Float): String {
        val rgb = parseHex(primaryHex) ?: return "warm_gold"
        val (r, g, b) = rgb
        return when {
            contrast > 0.55f && brightness < 0.42f -> "high_contrast_dark"
            g > r + 15 && g > b + 10 -> "cool_emerald"
            r > g + 20 && r > b + 15 && brightness > 0.35f -> "soft_desert"
            r > 160 && g > 120 && b < 100 -> "soft_desert"
            r > 180 && g > 140 && b < 120 -> "warm_gold"
            else -> if (brightness < 0.35f) "high_contrast_dark" else "warm_gold"
        }
    }

    private fun majorityFilter(hints: List<String>, avgB: Float, avgC: Float, primary: String): String {
        if (hints.isEmpty()) return inferFilter(primary, avgB, avgC)
        return hints.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            ?: inferFilter(primary, avgB, avgC)
    }

    private fun mostCommonHex(list: List<String>): String? {
        if (list.isEmpty()) return null
        fun quant(h: String): String {
            val p = parseHex(h) ?: return h
            val r = (p.first / 32) * 32
            val g = (p.second / 32) * 32
            val b = (p.third / 32) * 32
            return "#%02X%02X%02X".format(r, g, b)
        }
        val q = list.map { quant(it) }
        val bestQ = q.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: return list.first()
        return list.minByOrNull { hexDistance(it, bestQ) }
    }

    private fun hexDistance(a: String, b: String): Int {
        val pa = parseHex(a) ?: return 999
        val pb = parseHex(b) ?: return 999
        return abs(pa.first - pb.first) + abs(pa.second - pb.second) + abs(pa.third - pb.third)
    }

    private fun parseHex(hex: String): Triple<Int, Int, Int>? {
        val h = hex.removePrefix("#").trim()
        if (h.length != 6) return null
        return try {
            Triple(h.substring(0, 2).toInt(16), h.substring(2, 4).toInt(16), h.substring(4, 6).toInt(16))
        } catch (_: Exception) {
            null
        }
    }

    private fun darken(hex: String, factor: Float): String {
        val p = parseHex(hex) ?: return "#0B0F19"
        val r = (p.first * (1f - factor)).toInt().coerceIn(0, 255)
        val g = (p.second * (1f - factor)).toInt().coerceIn(0, 255)
        val b = (p.third * (1f - factor)).toInt().coerceIn(0, 255)
        return "#%02X%02X%02X".format(r, g, b)
    }

    private fun decodeSampled(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        val maxDim = max(bounds.outWidth, bounds.outHeight)
        while (maxDim / sample > maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }
}
