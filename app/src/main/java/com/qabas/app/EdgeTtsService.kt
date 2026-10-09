package com.qabas.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * 🎙️ محرك أصوات Edge-TTS العصبي المجاني (المستوحى من MoneyPrinterTurbo)
 * يوفر أصواتاً طبيعية نقية جداً باللغة العربية ومجاناً 100% بدون الحاجة لأي مفتاح API.
 */
data class EdgeTtsVoice(
    val id: String,
    val name: String,
    val gender: String,
    val locale: String,
    val description: String,
    val isArabic: Boolean = true
)

object EdgeTtsService {
    private const val TAG = "EdgeTtsService"
    private const val WSS_URL = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?trustedclienttoken=6A5AA1D4EA654941A3D4EAD6815FA26E"

    val DEFAULT_VOICES = listOf(
        EdgeTtsVoice(
            id = "ar-SA-HamedNeural",
            name = "حامد (السعودية - نبرة وقورة ورزينة)",
            gender = "Male",
            locale = "ar-SA",
            description = "الصوت القياسي الموصى به للمحتوى الديني والوثائقي وقصص القرآن."
        ),
        EdgeTtsVoice(
            id = "ar-EG-SalmaNeural",
            name = "سلمى (مصر - نبرة دافئة وواضحة)",
            gender = "Female",
            locale = "ar-EG",
            description = "مثالي للمقاطع التوعوية، الخواطر، والمحتوى التربوي الهادئ."
        ),
        EdgeTtsVoice(
            id = "ar-SA-ZariyahNeural",
            name = "زارية (السعودية - فصيحة وهادئة)",
            gender = "Female",
            locale = "ar-SA",
            description = "أداء لغوي متقن ومناسب للشعر والتدبر والدروس العلمية."
        ),
        EdgeTtsVoice(
            id = "ar-AE-HamdanNeural",
            name = "حمدان (الإمارات - نبرة فخمة ورسمية)",
            gender = "Male",
            locale = "ar-AE",
            description = "نبرة إذاعية عميقة وقوية تناسب الفيديوهات الملحمية والريلز."
        ),
        EdgeTtsVoice(
            id = "ar-AE-FatimaNeural",
            name = "فاطمة (الإمارات - صوت نقي وعصري)",
            gender = "Female",
            locale = "ar-AE",
            description = "صوت نقي ومشرق مناسب للإعلانات والخواطر القصيرة."
        ),
        EdgeTtsVoice(
            id = "ar-EG-ShakirNeural",
            name = "شاكر (مصر - نبرة وثائقية وحماسية)",
            gender = "Male",
            locale = "ar-EG",
            description = "صوت معبر ومؤثر للمعارك التاريخية وسير العظماء."
        ),
        EdgeTtsVoice(
            id = "ar-QA-MoazNeural",
            name = "معاذ (قطر - صوت إخباري جهوري)",
            gender = "Male",
            locale = "ar-QA",
            description = "صوت إعلامي مهيب ومناسب للتقارير والبيانات الرسمية."
        ),
        EdgeTtsVoice(
            id = "en-US-ChristopherNeural",
            name = "Christopher (US - Storyteller / Documentary)",
            gender = "Male",
            locale = "en-US",
            description = "Deep cinematic storytelling voice for global English content.",
            isArabic = false
        ),
        EdgeTtsVoice(
            id = "en-US-JennyNeural",
            name = "Jenny (US - Warm & Natural)",
            gender = "Female",
            locale = "en-US",
            description = "Versatile, clear, and engaging voice for social media clips.",
            isArabic = false
        )
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * تحويل النص إلى ملف MP3 صوتي عصبي عالي الجودة
     */
    suspend fun synthesizeSpeech(
        context: Context,
        text: String,
        voiceId: String = "ar-SA-HamedNeural",
        rate: String = "+0%",
        pitch: String = "+0Hz",
        outFile: File? = null
    ): File? = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return@withContext null

        val targetFile = outFile ?: File(
            context.cacheDir,
            "edge_tts_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.mp3"
        )

        withTimeoutOrNull(45_000L) {
            try {
                val audioData = executeWebsocketSynthesis(trimmed, voiceId, rate, pitch)
                if (audioData != null && audioData.isNotEmpty()) {
                    FileOutputStream(targetFile).use { fos ->
                        fos.write(audioData)
                    }
                    if (targetFile.exists() && targetFile.length() > 500) {
                        Log.i(TAG, "Edge-TTS synthesis success: ${targetFile.length()} bytes -> ${targetFile.name}")
                        return@withTimeoutOrNull targetFile
                    }
                }
                null
            } catch (e: Exception) {
                Log.e(TAG, "Edge-TTS error: ${e.message}", e)
                null
            }
        }
    }

    private suspend fun executeWebsocketSynthesis(
        text: String,
        voiceId: String,
        rate: String,
        pitch: String
    ): ByteArray? = suspendCancellableCoroutine { continuation ->
        val reqId = UUID.randomUUID().toString().replace("-", "")
        val audioStream = ByteArrayOutputStream()

        val request = Request.Builder()
            .url(WSS_URL)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0")
            .addHeader("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .addHeader("Pragma", "no-cache")
            .addHeader("Cache-Control", "no-cache")
            .build()

        var wsInstance: WebSocket? = null

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                wsInstance = webSocket
                try {
                    // 1) إرسال إعدادات التهيئة الصوتية
                    val configPayload = "Content-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n" +
                            "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}"
                    webSocket.send(configPayload)

                    // 2) إرسال رسالة الـ SSML مع النص
                    val cleanText = text
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;")
                        .replace("'", "&apos;")

                    val ssmlPayload = "X-RequestId:$reqId\r\nContent-Type:application/ssml+xml\r\nPath:ssml\r\n\r\n" +
                            "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='ar-SA'>" +
                            "<voice name='$voiceId'>" +
                            "<prosody pitch='$pitch' rate='$rate' volume='+0%'>$cleanText</prosody>" +
                            "</voice></speak>"
                    webSocket.send(ssmlPayload)
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending SSML payload: ${e.message}")
                    webSocket.close(1000, "Error")
                    if (continuation.isActive) continuation.resume(null)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                try {
                    // بروتوكول Edge-TTS الثنائي:
                    // أول 2 بايت هما طول ترويسة الرسالة النصية
                    val data = bytes.toByteArray()
                    if (data.size >= 2) {
                        val headerLen = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                        if (data.size > 2 + headerLen) {
                            val headerStr = String(data, 2, headerLen, Charsets.UTF_8)
                            if (headerStr.contains("Path:audio")) {
                                val audioOffset = 2 + headerLen
                                audioStream.write(data, audioOffset, data.size - audioOffset)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing audio packet: ${e.message}")
                }
            }

            override fun onMessage(webSocket: WebSocket, textMsg: String) {
                if (textMsg.contains("Path:turn.end")) {
                    webSocket.close(1000, "Completed")
                    if (continuation.isActive) {
                        continuation.resume(audioStream.toByteArray())
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure: ${t.message}")
                if (continuation.isActive) {
                    if (audioStream.size() > 0) {
                        continuation.resume(audioStream.toByteArray())
                    } else {
                        continuation.resume(null)
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (continuation.isActive) {
                    continuation.resume(if (audioStream.size() > 0) audioStream.toByteArray() else null)
                }
            }
        }

        wsInstance = httpClient.newWebSocket(request, listener)

        continuation.invokeOnCancellation {
            try {
                wsInstance?.cancel()
            } catch (_: Exception) {}
        }
    }
}
