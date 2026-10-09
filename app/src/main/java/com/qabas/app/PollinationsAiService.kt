package com.qabas.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * PollinationsAiService — محرك توليد الصور والبوسترات الفورية بالذكاء الاصطناعي (FLUX.1 / Turbo)
 * بدون مفاتيح API نهائياً (Zero API Key).
 * يعمل بشكل مباشر ومجاني 100% لتوليد خلفيات سينمائية، بوسترات، ومصغرات يوتيوب.
 */
object PollinationsAiService {
    private const val TAG = "PollinationsAiService"
    private const val BASE_IMAGE_URL = "https://image.pollinations.ai/prompt"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * بناء رابط مباشر للصورة من Pollinations
     */
    fun buildImageUrl(
        prompt: String,
        width: Int = 1080,
        height: Int = 1920,
        model: String = "flux",
        seed: Long = System.currentTimeMillis() % 1000000L
    ): String {
        val sanitizedPrompt = prompt.trim()
            .replace("\n", " ")
            .take(500)
        val encodedPrompt = URLEncoder.encode(sanitizedPrompt, "UTF-8")
        return "$BASE_IMAGE_URL/$encodedPrompt?width=$width&height=$height&model=$model&seed=$seed&nologo=true&enhance=true"
    }

    /**
     * توليد وتحميل الصورة محلياً وحفظها في التخزين المؤقت للتطبيق
     * ترجع المسار المحلي للملف (مثلاً /data/user/0/.../cache/ai_bg_xxx.jpg)
     */
    suspend fun generateAndDownload(
        context: Context,
        prompt: String,
        width: Int = 1080,
        height: Int = 1920,
        model: String = "flux"
    ): String? = withContext(Dispatchers.IO) {
        val enrichedPrompt = if (prompt.any { it in '\u0600'..'\u06FF' }) {
            // إضافة كلمات مفتاحية لتعزيز جودة FLUX مع الأسلوب الإسلامي والسينمائي
            "$prompt, masterpiece, ultra high quality, cinematic lighting, photorealistic, 8k resolution, fine details"
        } else {
            "$prompt, masterpiece, cinematic lighting, photorealistic, 8k resolution, elegant composition"
        }

        val directUrl = buildImageUrl(enrichedPrompt, width, height, model)
        Log.d(TAG, "Requesting Pollinations image: $directUrl")

        try {
            val request = Request.Builder()
                .url(directUrl)
                .header("User-Agent", "QabasStudio/1.2")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Pollinations request failed with code: ${response.code}")
                return@withContext null
            }

            val body = response.body ?: return@withContext null
            val bytes = body.bytes()
            if (bytes.size < 1024) { // أقل من 1KB ليس صورة صالحة
                Log.w(TAG, "Pollinations response too small (${bytes.size} bytes)")
                return@withContext null
            }

            val targetDir = File(context.cacheDir, "pollinations_ai").apply { mkdirs() }
            val targetFile = File(targetDir, "flux_${UUID.randomUUID().toString().take(8)}.jpg")

            FileOutputStream(targetFile).use { out ->
                out.write(bytes)
                out.flush()
            }

            Log.i(TAG, "Pollinations image saved successfully: ${targetFile.absolutePath} (${bytes.size / 1024} KB)")
            targetFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate image via Pollinations", e)
            null
        }
    }
}
