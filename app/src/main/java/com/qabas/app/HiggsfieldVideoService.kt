package com.qabas.app

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
import java.net.URL

/** Higgsfield Kling 4.0 integration through the server-side Qabas gateway. */
object HiggsfieldVideoService {
    private const val TAG = "HiggsfieldVideoService"
    private const val MAX_POLL_ATTEMPTS = 120
    private const val POLL_INTERVAL_MS = 3_000L

    suspend fun isConfigured(context: Context): Boolean =
        PlatformGatewayClient.providerConfiguredSuspend("higgsfield")

    /** Checks server-side registration only; it does not claim provider credentials are valid. */
    suspend fun testConnection(context: Context): Pair<Boolean, String> {
        val configured = PlatformGatewayClient.providerConfiguredSuspend("higgsfield")
        return if (configured) {
            true to "مفتاح Higgsfield محفوظ في الخادم. صلاحية المفتاح الفعلية تُثبت عند أول طلب توليد."
        } else {
            false to "لا يوجد مفتاح Higgsfield محفوظ في بوابة الخادم، أو تعذر الوصول إليها."
        }
    }

    suspend fun generateTextToVideo(
        context: Context,
        prompt: String,
        cameraMotion: String = "cinematic",
        durationSeconds: Int = 5,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): File? = withContext(Dispatchers.IO) {
        if (prompt.isBlank()) return@withContext null

        val enrichedPrompt = QabasIslamicContentPolicy.forVisualGeneration(
            buildString {
                append(prompt.trim())
                append(", cinematic lighting, photorealistic, reverent atmosphere, steady camera movement: ")
                append(cameraMotion.trim().ifBlank { "cinematic" })
            }
        )
        val payload = buildTextToVideoPayload(enrichedPrompt, durationSeconds).apply {
            put("operation", "text_to_video")
        }

        try {
            onProgress(0.12f, "إرسال طلب Kling 4.0 عبر بوابة قبس الآمنة...")
            SystemLogsManager.addLog("INFO", "جاري إرسال طلب فيديو إلى Kling 4.0 عبر الخادم الآمن...", Color(0xFF38BDF8))

            val response = PlatformGatewayClient.proxyHiggsfieldSuspend(payload)
                ?: return@withContext gatewayUnavailable()
            if (!response.isSuccessful) {
                Log.w(TAG, "Kling 4.0 submission rejected with HTTP ${response.statusCode}")
                SystemLogsManager.addLog("WARN", "رفضت بوابة Higgsfield طلب التوليد (HTTP ${response.statusCode}).", Color(0xFFEF4444))
                return@withContext null
            }

            val requestId = response.body?.let(::extractRequestId).orEmpty()
            if (requestId.isBlank()) {
                Log.w(TAG, "Kling 4.0 response did not include request_id")
                SystemLogsManager.addLog("WARN", "لم يُرجع Kling 4.0 معرّف الطلب المتوقع.", Color(0xFFEF4444))
                return@withContext null
            }

            pollRequestUntilFinished(context, requestId, onProgress)
        } catch (e: Exception) {
            Log.e(TAG, "Kling 4.0 text-to-video failed", e)
            SystemLogsManager.addLog("WARN", "تعذر إكمال توليد الفيديو عبر Kling 4.0.", Color(0xFFEF4444))
            null
        }
    }

    /** Uploads the source image through Higgsfield's signed-upload flow before Kling 4.0 animation. */
    suspend fun animateImage(
        context: Context,
        imageFile: File,
        prompt: String = "subtle holy atmosphere, smooth cinematic camera movement, particles in air",
        cameraMotion: String = "orbit",
        durationSeconds: Int = 5,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): File? = withContext(Dispatchers.IO) {
        if (!imageFile.exists() || imageFile.length() <= 0L || prompt.isBlank()) return@withContext null

        var bitmap: Bitmap? = null
        try {
            onProgress(0.08f, "تهيئة الصورة للرفع الآمن...")
            bitmap = BitmapFactory.decodeFile(imageFile.absolutePath) ?: return@withContext null
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
            val imageBytes = output.toByteArray()
            bitmap.recycle()
            bitmap = null

            val uploadPayload = JSONObject().apply {
                put("operation", "upload_image")
                put("content_type", "image/jpeg")
                put("image_base64", Base64.encodeToString(imageBytes, Base64.NO_WRAP))
            }
            onProgress(0.18f, "رفع الصورة إلى تخزين Higgsfield المؤقت...")
            val uploadResponse = PlatformGatewayClient.proxyHiggsfieldSuspend(uploadPayload)
                ?: return@withContext gatewayUnavailable()
            if (!uploadResponse.isSuccessful) {
                Log.w(TAG, "Higgsfield signed upload failed with HTTP ${uploadResponse.statusCode}")
                return@withContext null
            }
            val imageUrl = uploadResponse.body?.optString("public_url").orEmpty()
            if (!imageUrl.startsWith("https://")) return@withContext null

            val imagePrompt = QabasIslamicContentPolicy.forVisualGeneration(
                "${prompt.trim()}, camera movement: ${cameraMotion.trim().ifBlank { "cinematic" }}"
            )
            val generationPayload = JSONObject().apply {
                put("operation", "image_to_video")
                put("image_url", imageUrl)
                put("prompt", imagePrompt)
                put("duration", durationSeconds.coerceIn(3, 30))
                put("resolution", "1080p")
            }

            onProgress(0.28f, "إرسال الصورة إلى Kling 4.0...")
            val generationResponse = PlatformGatewayClient.proxyHiggsfieldSuspend(generationPayload)
                ?: return@withContext gatewayUnavailable()
            if (!generationResponse.isSuccessful) {
                Log.w(TAG, "Kling 4.0 image-to-video rejected with HTTP ${generationResponse.statusCode}")
                return@withContext null
            }
            val requestId = generationResponse.body?.let(::extractRequestId).orEmpty()
            if (requestId.isBlank()) return@withContext null

            pollRequestUntilFinished(context, requestId, onProgress)
        } catch (e: Exception) {
            Log.e(TAG, "Kling 4.0 image-to-video failed", e)
            null
        } finally {
            bitmap?.recycle()
        }
    }

    private suspend fun pollRequestUntilFinished(
        context: Context,
        requestId: String,
        onProgress: (Float, String) -> Unit
    ): File? {
        repeat(MAX_POLL_ATTEMPTS) { attempt ->
            delay(POLL_INTERVAL_MS)
            onProgress(
                (0.30f + 0.55f * ((attempt + 1).toFloat() / MAX_POLL_ATTEMPTS)).coerceAtMost(0.88f),
                "ينفذ Kling 4.0 عملية الرندرة... (${(attempt + 1) * 3} ثانية)"
            )

            val response = PlatformGatewayClient.proxyHiggsfieldSuspend(
                JSONObject().put("operation", "status").put("request_id", requestId)
            ) ?: return null

            if (!response.isSuccessful || response.body == null) {
                Log.w(TAG, "Kling 4.0 status request returned HTTP ${response.statusCode}")
                return null
            }

            val statusJson = response.body
            val status = statusJson.optString("status").lowercase()
            when {
                status == "completed" -> {
                    val videoUrl = extractVideoUrl(statusJson)
                    if (!videoUrl.startsWith("https://")) {
                        Log.w(TAG, "Completed Kling 4.0 request did not include video.url")
                        return null
                    }
                    onProgress(0.93f, "اكتمل التوليد. جارٍ تنزيل ملف الفيديو...")
                    val file = downloadVideoToCache(context, videoUrl) ?: return null
                    onProgress(1f, "اكتمل تنزيل فيديو Kling 4.0.")
                    SystemLogsManager.addLog("SUCCESS", "اكتمل توليد وتنزيل الفيديو عبر Kling 4.0.", Color(0xFF10B981))
                    return file
                }
                status in setOf("failed", "nsfw", "canceled") -> {
                    Log.w(TAG, "Kling 4.0 request ended with status: $status")
                    SystemLogsManager.addLog("WARN", "انتهى طلب Kling 4.0 بالحالة: $status.", Color(0xFFEF4444))
                    return null
                }
                status !in setOf("queued", "in_progress") -> {
                    Log.w(TAG, "Kling 4.0 returned an unknown request status")
                    return null
                }
            }
        }

        SystemLogsManager.addLog("WARN", "انتهت مهلة انتظار Kling 4.0 قبل اكتمال الطلب.", Color(0xFFF59E0B))
        return null
    }

    private fun downloadVideoToCache(context: Context, downloadUrl: String): File? {
        return try {
            val url = URL(downloadUrl)
            require(url.protocol.equals("https", ignoreCase = true))
            val connection = url.openConnection()
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.getInputStream().use { input ->
                val outFile = File(context.cacheDir, "kling4_video_${System.currentTimeMillis()}.mp4")
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
                if (outFile.exists() && outFile.length() > 0L) outFile else {
                    outFile.delete()
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kling 4.0 video download failed", e)
            null
        }
    }

    private fun gatewayUnavailable(): File? {
        SystemLogsManager.addLog("WARN", "بوابة Higgsfield غير متاحة أو لم يُضبط مفتاح المزود على الخادم.", Color(0xFFF59E0B))
        return null
    }

    internal fun buildTextToVideoPayload(prompt: String, durationSeconds: Int): JSONObject =
        JSONObject().apply {
            put("prompt", prompt.trim())
            put("duration", durationSeconds.coerceIn(3, 30))
            put("resolution", "1080p")
            put("aspect_ratio", "9:16")
        }

    internal fun extractRequestId(response: JSONObject): String =
        response.optString("request_id").trim()

    internal fun extractVideoUrl(response: JSONObject): String =
        response.optJSONObject("video")?.optString("url").orEmpty().trim()

    internal fun isTerminalStatus(status: String): Boolean =
        status.lowercase() in setOf("completed", "failed", "nsfw", "canceled")
}