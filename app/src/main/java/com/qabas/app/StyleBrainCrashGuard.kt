package com.qabas.app

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * حماية ضد انهيار التطبيق أثناء استخراج الإطارات + تصحيح تقدم القوة الضئيل.
 */
object StyleBrainCrashGuard {

    suspend fun extractKeyFramesSafe(context: Context, videoPath: String): List<File> =
        withContext(Dispatchers.IO) {
            if (videoPath.isBlank()) return@withContext emptyList()
            val videoFile = File(videoPath)
            if (!videoFile.exists() || videoFile.length() == 0L) return@withContext emptyList()

            val out = mutableListOf<File>()
            try {
                val framesDir = File(context.cacheDir, "stylebrain_keyframes").apply { mkdirs() }
                try {
                    framesDir.listFiles()?.forEach { f ->
                        if (System.currentTimeMillis() - f.lastModified() > 900_000) f.delete()
                    }
                } catch (_: Exception) {}

                var durationSeconds = 6.0
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(videoPath)
                    val durationMs = retriever.extractMetadata(
                        android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
                    )?.toLongOrNull() ?: 0L
                    if (durationMs > 500) durationSeconds = durationMs / 1000.0

                    val targetCount = 6
                    val step = durationSeconds / (targetCount + 1)
                    val ts = System.currentTimeMillis()

                    for (i in 1..targetCount) {
                        try {
                            val timeUs = ((step * i) * 1_000_000).toLong()
                            val bmp = retriever.getFrameAtTime(
                                timeUs,
                                android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                            ) ?: retriever.getFrameAtTime(timeUs)

                            if (bmp != null) {
                                val scaled = scaleDown(bmp, 640)
                                val outFile = File(framesDir, "safe_frame_${ts}_$i.jpg")
                                outFile.outputStream().use { os ->
                                    scaled.compress(Bitmap.CompressFormat.JPEG, 80, os)
                                }
                                if (scaled !== bmp) {
                                    try { scaled.recycle() } catch (_: Exception) {}
                                }
                                try { bmp.recycle() } catch (_: Exception) {}
                                if (outFile.exists() && outFile.length() > 0) out.add(outFile)
                            }
                        } catch (e: Exception) {
                            Log.w("StyleBrainCrashGuard", "frame $i failed: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w("StyleBrainCrashGuard", "retriever failed: ${e.message}")
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.e("StyleBrainCrashGuard", "extractKeyFramesSafe: ${e.message}", e)
            }
            out
        }

    private fun scaleDown(src: Bitmap, maxWidth: Int): Bitmap {
        if (src.width <= maxWidth) return src
        val ratio = maxWidth.toFloat() / src.width
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        return try {
            Bitmap.createScaledBitmap(src, maxWidth, h, true)
        } catch (_: Exception) {
            src
        }
    }

    /**
     * بعد كل امتصاص: اضبط القوة لتكون زيادة ضئيلة فقط (0..3).
     * يصحّح القفزة القديمة إلى 80 عند أول امتصاص.
     *
     * القاعدة:
     * - عدد الأنماط = N
     * - القوة المستهدفة ≈ مجموع gains (كل امتصاص ≤3) ≤ N*3
     * - لا تتجاوز 100
     */
    fun applyTinyStrengthGain(context: Context, overallScore: Int) {
        try {
            val prefs = context.getSharedPreferences("qabas_style_brain_prefs", Context.MODE_PRIVATE)
            val stylesJson = prefs.getString("absorbed_styles_list", "[]") ?: "[]"
            val stylesArr = JSONArray(stylesJson)
            val styleCount = stylesArr.length()

            // احسب القوة من مجموع الأنماط (كل واحد +0..+3 حسب تقييمه)
            var computed = 0
            for (i in 0 until stylesArr.length()) {
                val score = stylesArr.getJSONObject(i).optInt("overallScore", 85)
                computed += StyleBrainLoadFix.computeStrengthGain(score, 0)
            }
            computed = computed.coerceIn(0, 100)

            // أو من آخر امتصاص فقط إن أردنا زيادة على الحالي المخزّن سابقاً بضآلة
            val coreJson = prefs.getString("qabas_core_style_json", null)
            val obj = if (coreJson.isNullOrBlank()) {
                JSONObject().apply {
                    put("visualTraits", JSONArray())
                    put("motionTraits", JSONArray())
                    put("textTraits", JSONArray())
                    put("analysis", "عقل فارغ — يرجى إضافة فيديوهات مرجعية للتعلم والاستفادة")
                    put("strengthScore", 0)
                    put("lastUpdated", System.currentTimeMillis())
                }
            } else JSONObject(coreJson)

            val oldStrength = obj.optInt("strengthScore", 0)
            // استخدم المحسوب من الأنماط (يمنع القفز إلى 80)
            val finalStrength = computed.coerceIn(0, 100)

            obj.put("strengthScore", finalStrength)
            obj.put("lastUpdated", System.currentTimeMillis())
            if (finalStrength == 0 && styleCount == 0) {
                obj.put("analysis", "عقل فارغ — يرجى إضافة فيديوهات مرجعية للتعلم والاستفادة")
                obj.put("visualTraits", JSONArray())
                obj.put("motionTraits", JSONArray())
                obj.put("textTraits", JSONArray())
            } else if (finalStrength > 0) {
                obj.put(
                    "analysis",
                    "عقل قبس التراكمي (القوة: $finalStrength% | $styleCount أساليب ممتصة) — تقدم ضئيل +0…+3 لكل امتصاص."
                )
            }

            prefs.edit().putString("qabas_core_style_json", obj.toString()).apply()
            StyleBrain.init(context)
            Log.d(
                "StyleBrainCrashGuard",
                "strength corrected: $oldStrength → $finalStrength (styles=$styleCount, lastScore=$overallScore)"
            )
        } catch (e: Exception) {
            Log.w("StyleBrainCrashGuard", "applyTinyStrengthGain: ${e.message}")
        }
    }
}
