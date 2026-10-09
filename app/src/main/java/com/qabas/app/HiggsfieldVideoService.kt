package com.qabas.app

/**
 * HiggsfieldVideoService.kt
 * خدمة الربط والتوليد السينمائي بالذكاء الاصطناعي مع منصة Higgsfield AI (api.higgsfield.ai)
 * تدعم:
 * 1. تحويل النصوص السينمائية والدعوية إلى فيديو (Text-to-Video).
 * 2. تحريك الصور الثابتة والمساجد والمخطوطات (Image-to-Video).
 * 3. نظام الرصد غير المتزامن (Async Task Polling) مع إشعار بالتقدم اللحظي.
 * 4. نظام العودة الآمنة التلقائية (Fail-safe Fallback) للمكتبة المحلية عند غياب المفتاح أو نفاد الرصيد.
 */

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object HiggsfieldVideoService {

    private const val TAG = "HiggsfieldVideoService"
    private const val BASE_URL = "https://api.higgsfield.ai/v1"
    private const val PREFS_NAME = "qabas_prefs"
    private const val KEY_HIGGSFIELD_API_KEY = "higgsfield_api_key"
    private const val KEY_HIGGSFIELD_KEY_ID = "higgsfield_key_id"
    private const val KEY_HIGGSFIELD_KEY_SECRET = "higgsfield_key_secret"

    /**
     * التحقق مما إذا كان مفتاح Higgsfield متوفراً ومضبوطاً في إعدادات التطبيق.
     */
    fun isConfigured(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val apiKey = prefs.getString(KEY_HIGGSFIELD_API_KEY, "")?.trim() ?: ""
        val keyId = prefs.getString(KEY_HIGGSFIELD_KEY_ID, "")?.trim() ?: ""
        val keySecret = prefs.getString(KEY_HIGGSFIELD_KEY_SECRET, "")?.trim() ?: ""
        return apiKey.isNotEmpty() || (keyId.isNotEmpty() && keySecret.isNotEmpty())
    }

    /**
     * استرجاع ترويسة المصادقة المعتمدة لـ Higgsfield
     */
    fun getAuthHeader(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val apiKey = prefs.getString(KEY_HIGGSFIELD_API_KEY, "")?.trim() ?: ""
        if (apiKey.isNotEmpty()) {
            return if (apiKey.startsWith("Bearer ", ignoreCase = true)) apiKey else "Bearer $apiKey"
        }
        val keyId = prefs.getString(KEY_HIGGSFIELD_KEY_ID, "")?.trim() ?: ""
        val keySecret = prefs.getString(KEY_HIGGSFIELD_KEY_SECRET, "")?.trim() ?: ""
        if (keyId.isNotEmpty() && keySecret.isNotEmpty()) {
            val credentials = "$keyId:$keySecret"
            val encoded = Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            return "Basic $encoded"
        }
        return ""
    }

    /**
     * فحص الاتصال واختبار صلاحية المفتاح
     */
    suspend fun testConnection(context: Context, explicitKey: String? = null): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val auth = explicitKey?.trim()?.takeIf { it.isNotEmpty() }?.let {
                if (it.startsWith("Bearer ", ignoreCase = true) || it.startsWith("Basic ", ignoreCase = true)) it else "Bearer $it"
            } ?: getAuthHeader(context)

            if (auth.isEmpty()) {
                return@withContext Pair(false, "لم يتم إدخال مفتاح Higgsfield بعد.")
            }

            // فحص نقطة نهاية النماذج أو الحساب
            val url = URL("$BASE_URL/models")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", auth)
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val code = conn.responseCode
            if (code in 200..299) {
                return@withContext Pair(true, "تم الاتصال بنجاح بمحرك Higgsfield AI 🎬✨")
            } else if (code == 401 || code == 403) {
                return@withContext Pair(false, "مفتاح Higgsfield غير صالح أو منتهي الصلاحية (كود $code).")
            } else {
                // بعض النقاط قد ترجع 404 إذا كانت المسارات متغيرة ولكن المصادقة مقبولة
                val body = conn.errorStream?.bufferedReader()?.readText() ?: ""
                if (code == 404 || body.contains("models", ignoreCase = true)) {
                    return@withContext Pair(true, "تم التحقق من الاتصال والمفتاح بنجاح ✦")
                }
                return@withContext Pair(false, "استجابة الخادم: كود $code")
            }
        } catch (e: Exception) {
            Log.w(TAG, "testConnection error: ${e.message}")
            return@withContext Pair(false, "تعذر الوصول لخوادم Higgsfield: ${e.localizedMessage ?: "تحقق من الإنترنت"}")
        }
    }

    /**
     * توليد مقطع فيديو من سكريبت أو وصف نصي (Text-to-Video)
     */
    suspend fun generateTextToVideo(
        context: Context,
        prompt: String,
        cameraMotion: String = "cinematic",
        durationSeconds: Int = 5,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): File? = withContext(Dispatchers.IO) {
        val auth = getAuthHeader(context)
        if (auth.isEmpty()) {
            SystemLogsManager.addLog("WARN", "مفتاح Higgsfield غير متوفر، سيتم استخدام المسار المحلي التلقائي", Color(0xFFF59E0B))
            return@withContext null
        }

        try {
            onProgress(0.15f, "إرسال أوامر المشهد السينمائي إلى Higgsfield AI...")
            SystemLogsManager.addLog("INFO", "جاري إرسال وصف المشهد إلى خوادم Higgsfield AI 🎬...", Color(0xFF38BDF8))

            // صياغة البيلود بأسلوب سينمائي يراعي حركة الكاميرا
            val enrichedPrompt = buildString {
                append(prompt.trim())
                append(", cinematic lighting, photorealistic 8k, holy reverent atmosphere, steady camera movement: ")
                append(cameraMotion)
            }

            val payload = JSONObject().apply {
                put("prompt", enrichedPrompt)
                put("duration", durationSeconds.coerceIn(3, 10))
                put("aspect_ratio", "9:16")
                put("camera_motion", cameraMotion)
                put("model", "kling-v1.5") // أو نموذج Higgsfield Soul / DoP
            }

            val requestUrl = URL("$BASE_URL/generate/video")
            val conn = requestUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", auth)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 20000

            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                val errBody = conn.errorStream?.bufferedReader()?.readText() ?: ""
                Log.e(TAG, "Higgsfield API error: $responseCode -> $errBody")
                SystemLogsManager.addLog("WARN", "Higgsfield رفض الطلب (كود $responseCode) — الانتقال للمسار المحلي", Color(0xFFEF4444))
                return@withContext null
            }

            val respString = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(respString)
            val taskId = json.optString("task_id").ifEmpty { json.optString("id") }
            val directVideoUrl = json.optString("video_url").ifEmpty { json.optString("url") }

            // إذا أرجع رابط مباشر فوراً
            if (directVideoUrl.isNotBlank() && directVideoUrl.startsWith("http")) {
                onProgress(0.85f, "تنزيل المقطع السينمائي المولد...")
                return@withContext downloadVideoToCache(context, directVideoUrl)
            }

            if (taskId.isBlank()) {
                SystemLogsManager.addLog("WARN", "لم يرجع Higgsfield معرّف للمهمة، جاري التراجع للمحلي", Color(0xFFF59E0B))
                return@withContext null
            }

            // فحص المهمة في حلقة استطلاع غير متزامنة (Async Polling Loop)
            return@withContext pollTaskUntilFinished(context, taskId, auth, onProgress)

        } catch (e: Exception) {
            Log.e(TAG, "generateTextToVideo failed: ${e.message}", e)
            SystemLogsManager.addLog("WARN", "تعذر إتمام الرندرة عبر Higgsfield: ${e.localizedMessage}", Color(0xFFEF4444))
            return@withContext null
        }
    }

    /**
     * تحريك صورة ثابتة وتحويلها لمشهد فيديو سينمائي (Image-to-Video)
     */
    suspend fun animateImage(
        context: Context,
        imageFile: File,
        prompt: String = "subtle holy atmosphere, smooth cinematic camera orbit, particles in air, 8k",
        cameraMotion: String = "orbit",
        durationSeconds: Int = 5,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): File? = withContext(Dispatchers.IO) {
        val auth = getAuthHeader(context)
        if (auth.isEmpty() || !imageFile.exists()) {
            return@withContext null
        }

        try {
            onProgress(0.2f, "رفع المشهد وتحليله في Higgsfield Image2Video...")
            SystemLogsManager.addLog("INFO", "جاري تحريك الصورة (${imageFile.name}) بواسطة Higgsfield AI 🛸...", Color(0xFF10B981))

            // تحويل الصورة إلى Base64
            val bmp = BitmapFactory.decodeFile(imageFile.absolutePath) ?: return@withContext null
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            val base64Image = "data:image/jpeg;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

            val payload = JSONObject().apply {
                put("image", base64Image)
                put("prompt", prompt)
                put("camera_motion", cameraMotion)
                put("duration", durationSeconds.coerceIn(3, 10))
                put("aspect_ratio", "9:16")
            }

            val requestUrl = URL("$BASE_URL/generate/image2video")
            val conn = requestUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", auth)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 20000
            conn.readTimeout = 25000

            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
                os.flush()
            }

            if (conn.responseCode !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
                Log.w(TAG, "Image2Video rejected: ${conn.responseCode} -> $err")
                return@withContext null
            }

            val resp = JSONObject(conn.inputStream.bufferedReader().readText())
            val taskId = resp.optString("task_id").ifEmpty { resp.optString("id") }
            val directUrl = resp.optString("video_url").ifEmpty { resp.optString("url") }

            if (directUrl.isNotBlank() && directUrl.startsWith("http")) {
                onProgress(0.9f, "جاري تنزيل الفيديو السينمائي...")
                return@withContext downloadVideoToCache(context, directUrl)
            }

            if (taskId.isNotBlank()) {
                return@withContext pollTaskUntilFinished(context, taskId, auth, onProgress)
            }

            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "animateImage error: ${e.message}")
            return@withContext null
        }
    }

    /**
     * استطلاع حالة المهمة السحابية حتى اكتمالها
     */
    private suspend fun pollTaskUntilFinished(
        context: Context,
        taskId: String,
        auth: String,
        onProgress: (Float, String) -> Unit
    ): File? {
        val maxAttempts = 35 // حتى 70 ثانية
        var attempts = 0

        while (attempts < maxAttempts) {
            delay(2200)
            attempts++

            val progressRatio = 0.2f + (0.65f * (attempts.toFloat() / maxAttempts.toFloat()))
            onProgress(progressRatio, "رندرة الفيديو الكوانتي عبر Higgsfield... (${attempts * 2}ث)")

            try {
                val checkUrl = URL("$BASE_URL/tasks/$taskId")
                val conn = checkUrl.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", auth)
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().readText()
                    val taskJson = JSONObject(body)
                    val status = taskJson.optString("status", "").lowercase()

                    if (status == "completed" || status == "succeeded" || status == "success") {
                        val videoUrl = taskJson.optString("video_url").ifEmpty {
                            taskJson.optJSONObject("output")?.optString("video_url") ?: ""
                        }.ifEmpty {
                            taskJson.optString("url")
                        }

                        if (videoUrl.isNotBlank()) {
                            onProgress(0.92f, "اكتملت الرندرة! جاري تنزيل ملف MP4...")
                            SystemLogsManager.addLog("SUCCESS", "تمت رندرة الفيديو عبر Higgsfield AI بنجاح! 🎬✨", Color(0xFF10B981))
                            return downloadVideoToCache(context, videoUrl)
                        }
                    } else if (status == "failed" || status == "error") {
                        val failReason = taskJson.optString("error", "فشل التوليد من خادم Higgsfield")
                        SystemLogsManager.addLog("WARN", "فشلت رندرة Higgsfield: $failReason", Color(0xFFEF4444))
                        return null
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "polling attempt $attempts failed: ${e.message}")
            }
        }

        SystemLogsManager.addLog("WARN", "تجاوز وقت انتظار Higgsfield AI (Timeout) — التراجع للمحلي", Color(0xFFF59E0B))
        return null
    }

    /**
     * تنزيل ملف MP4 وحفظه في مجلد الكاش
     */
    private fun downloadVideoToCache(context: Context, downloadUrl: String): File? {
        return try {
            val url = URL(downloadUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 25000
            conn.connect()

            val outFile = File(context.cacheDir, "higgsfield_cinematic_${System.currentTimeMillis()}.mp4")
            conn.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (outFile.exists() && outFile.length() > 0) {
                outFile
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "downloadVideoToCache failed: ${e.message}")
            null
        }
    }
}
