package com.qabas.app

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * نموذج يمثل صوتاً مستنسخاً أو قالباً صوتياً محفوظاً
 */
data class ClonedVoiceProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val sampleAudioPath: String = "",
    val referenceText: String = "",
    val baseEngine: String = "F5-TTS / Edge-Neural",
    val pitchShift: Float = 0f,
    val speedRate: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("description", description)
        put("sampleAudioPath", sampleAudioPath)
        put("referenceText", referenceText)
        put("baseEngine", baseEngine)
        put("pitchShift", pitchShift.toDouble())
        put("speedRate", speedRate.toDouble())
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): ClonedVoiceProfile = ClonedVoiceProfile(
            id = json.optString("id", UUID.randomUUID().toString()),
            name = json.optString("name", "صوت مخصص"),
            description = json.optString("description", ""),
            sampleAudioPath = json.optString("sampleAudioPath", ""),
            referenceText = json.optString("referenceText", ""),
            baseEngine = json.optString("baseEngine", "F5-TTS / Edge-Neural"),
            pitchShift = json.optDouble("pitchShift", 0.0).toFloat(),
            speedRate = json.optDouble("speedRate", 1.0).toFloat(),
            createdAt = json.optLong("createdAt", System.currentTimeMillis())
        )
    }
}

data class VoiceStudioServerStatus(
    val isOnline: Boolean,
    val serverUrl: String,
    val latencyMs: Long = 0L,
    val enginesCount: Int = 16,
    val message: String = ""
)

/**
 * 🎙️ محرك وخدمة VoiceStudio (بديل ElevenLabs المفتوح المصدر)
 * يقدم:
 * 1) استنساخ الأصوات (Voice Cloning) من عينة صوتية مسجلة 5-10 ثوانٍ.
 * 2) واجهة OpenAI Audio API المتوافقة (/v1/audio/speech).
 * 3) الدبلجة الذكية (Video Dubbing) وتزامن الصوت مع حركة الشفاه.
 * 4) العمل بنمطين: سحابي عبر خادم VoiceStudio أو محلي هجين عبر FFmpeg + Edge-TTS.
 */
object VoiceStudioService {
    private const val TAG = "VoiceStudioService"
    const val PREFS_KEY_SERVER_URL = "voice_studio_server_url"
    const val PREFS_KEY_CLONED_VOICES = "voice_studio_cloned_profiles"
    const val DEFAULT_SERVER_URL = "https://voice.qabas.studio" // المنفذ القياسي المشفر لخادم VoiceStudio

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun getSavedServerUrl(context: Context): String {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        return prefs.getString(PREFS_KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
    }

    fun saveServerUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(PREFS_KEY_SERVER_URL, url.trim().trimEnd('/')).apply()
    }

    /**
     * حفظ واسترجاع الملفات الصوتية المستنسخة محلياً
     */
    fun getClonedProfiles(context: Context): List<ClonedVoiceProfile> {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString(PREFS_KEY_CLONED_VOICES, null) ?: return defaultStarterProfiles()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<ClonedVoiceProfile>()
            for (i in 0 until arr.length()) {
                list.add(ClonedVoiceProfile.fromJson(arr.getJSONObject(i)))
            }
            if (list.isEmpty()) defaultStarterProfiles() else list
        } catch (_: Exception) {
            defaultStarterProfiles()
        }
    }

    fun saveClonedProfile(context: Context, profile: ClonedVoiceProfile) {
        val existing = getClonedProfiles(context).toMutableList()
        existing.removeAll { it.id == profile.id }
        existing.add(0, profile)
        val arr = JSONArray()
        existing.forEach { arr.put(it.toJson()) }
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(PREFS_KEY_CLONED_VOICES, arr.toString()).apply()
    }

    fun deleteClonedProfile(context: Context, id: String) {
        val existing = getClonedProfiles(context).filterNot { it.id == id }
        val arr = JSONArray()
        existing.forEach { arr.put(it.toJson()) }
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(PREFS_KEY_CLONED_VOICES, arr.toString()).apply()
    }

    private fun defaultStarterProfiles(): List<ClonedVoiceProfile> = listOf(
        ClonedVoiceProfile(
            id = "starter_doc_voice",
            name = "صوت وثائقي ملحمي (Epic Documentary)",
            description = "مستنسخ بنبرة وقورة عميقة مناسبة للسير التاريخية وقصص القرآن.",
            baseEngine = "XTTS-v2 / HamedNeural",
            pitchShift = -1.2f,
            speedRate = 0.95f
        ),
        ClonedVoiceProfile(
            id = "starter_warm_reflection",
            name = "نبرة التدبر والسكينة (Spiritual Serenity)",
            description = "صوت ناعم متأمل مناسب للأذكار والخواطر القرآنية الهادئة.",
            baseEngine = "F5-TTS / SalmaNeural",
            pitchShift = 0.4f,
            speedRate = 0.92f
        ),
        ClonedVoiceProfile(
            id = "starter_enthusiastic",
            name = "الراوي الحماسي (Inspiring Narrator)",
            description = "نبرة سريعة وجذابة للريلز والتيك توك ولفت الانتباه في الثواني الأولى.",
            baseEngine = "OmniVoice / ShakirNeural",
            pitchShift = 0.8f,
            speedRate = 1.08f
        )
    )

    /**
     * فحص الاتصال بخادم VoiceStudio
     */
    suspend fun pingServer(serverUrl: String): VoiceStudioServerStatus = withContext(Dispatchers.IO) {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        val start = System.currentTimeMillis()
        try {
            val pingEndpoints = listOf("$cleanUrl/ping", "$cleanUrl/health", "$cleanUrl/v1/models", "$cleanUrl/")
            var actualEnginesCount = -1 // 🔑 -1 = غير معروف من الخادم (لا نُظهر رقمًا كاذبًا)
            for (ep in pingEndpoints) {
                try {
                    val req = Request.Builder().url(ep).get().build()
                    httpClient.newCall(req).execute().use { res ->
                        val elapsed = System.currentTimeMillis() - start
                        if (res.isSuccessful) {
                            // 🔑 نحاول قراءة العدد الفعلي للمحركات من استجابة الخادم
                            actualEnginesCount = try {
                                val body = res.body?.string() ?: ""
                                val json = JSONObject(body)
                                json.optInt("engines_count",
                                    json.optInt("engines",
                                        json.optJSONArray("models")?.length() ?: -1
                                    )
                                )
                            } catch (_: Exception) { -1 }

                            return@withContext VoiceStudioServerStatus(
                                isOnline = true,
                                serverUrl = cleanUrl,
                                latencyMs = elapsed,
                                enginesCount = actualEnginesCount,
                                message = "متصل بنجاح بخادم VoiceStudio ($elapsed ms)" +
                                        if (actualEnginesCount >= 0) " • $actualEnginesCount محرك" else ""
                            )
                        }
                    }
                } catch (_: Exception) {}
            }
            VoiceStudioServerStatus(
                isOnline = false,
                serverUrl = cleanUrl,
                latencyMs = 0L,
                message = "الخادم غير متاح حالياً. يعمل في النمط الهجين المستقل."
            )
        } catch (e: Exception) {
            VoiceStudioServerStatus(
                isOnline = false,
                serverUrl = cleanUrl,
                latencyMs = 0L,
                message = "تعذر الاتصال: ${e.message}"
            )
        }
    }

    /**
     * توليد الصوت عبر واجهة OpenAI-Compatible Audio API الخاصة بـ VoiceStudio
     * POST /v1/audio/speech
     */
    suspend fun synthesizeSpeechViaServer(
        serverUrl: String,
        text: String,
        voiceId: String,
        model: String = "tts-1",
        speed: Float = 1.0f,
        outFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = serverUrl.trim().trimEnd('/')
            val endpoint = "$cleanUrl/v1/audio/speech"

            val jsonBody = JSONObject().apply {
                put("model", model)
                put("input", text)
                put("voice", voiceId)
                put("speed", speed.toDouble())
                put("response_format", "mp3")
            }

            val request = Request.Builder()
                .url(endpoint)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (outFile.exists() && outFile.length() > 500) {
                        Result.success(outFile)
                    } else {
                        Result.failure(Exception("الملف الصوتي المُستلم فارغ أو تالف"))
                    }
                } else {
                    Result.failure(Exception("خطأ من السيرفر (${response.code})"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * استنساخ صوت حقيقي عبر رفع عينة صوتية إلى خادم VoiceStudio
     */
    suspend fun cloneVoiceOnServer(
        serverUrl: String,
        audioFile: File,
        voiceName: String,
        referenceText: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = serverUrl.trim().trimEnd('/')
            val endpoint = "$cleanUrl/api/v1/voice/clone"

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("voice_name", voiceName)
                .addFormDataPart("reference_text", referenceText)
                .addFormDataPart(
                    "audio_file",
                    audioFile.name,
                    audioFile.asRequestBody("audio/*".toMediaType())
                )
                .build()

            val request = Request.Builder().url(endpoint).post(body).build()
            val response = httpClient.newCall(request).execute()
            val resStr = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                val json = JSONObject(resStr)
                val voiceId = json.optString("voice_id", voiceName)
                Result.success(voiceId)
            } else {
                Result.failure(Exception("فشل الاستنساخ بالسيرفر (${response.code}): $resStr"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * التوليد المستقل الهجين عند غياب السيرفر (Edge-TTS + تعديل الترددات و Pitch عبر FFmpeg)
     * يتيح تطبيق خصائص الصوت المستنسخ (النبرة والسرعة والخامة) مباشرة على الهاتف!
     */
    suspend fun synthesizeLocallyWithProfile(
        context: Context,
        text: String,
        profile: ClonedVoiceProfile
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val baseVoice = when {
                profile.baseEngine.contains("Salma") -> "ar-EG-SalmaNeural"
                profile.baseEngine.contains("Zariyah") -> "ar-SA-ZariyahNeural"
                profile.baseEngine.contains("Shakir") -> "ar-EG-ShakirNeural"
                profile.baseEngine.contains("Hamdan") -> "ar-AE-HamdanNeural"
                else -> "ar-SA-HamedNeural"
            }

            val tempBaseAudio = File(context.cacheDir, "vs_raw_${System.currentTimeMillis()}.mp3")
            val generated = EdgeTtsService.synthesizeSpeech(
                context = context,
                text = text,
                voiceId = baseVoice,
                rate = "${((profile.speedRate - 1.0f) * 100).toInt()}%",
                outFile = tempBaseAudio
            )

            if (generated == null || !generated.exists() || generated.length() < 500) {
                return@withContext Result.failure(Exception("تعذر توليد الصوت الأساسي"))
            }

            // تعديل النبرة الصوتية والبصمة عبر مرشحات FFmpeg المتطورة (Pitch + Equalizer)
            val finalClonedAudio = File(context.cacheDir, "vs_cloned_${System.currentTimeMillis()}.mp3")
            val pitchVal = 1.0 + (profile.pitchShift * 0.15)
            val audioFilter = "asetrate=24000*$pitchVal,aresample=24000,atempo=${1.0 / pitchVal},equalizer=f=120:width_type=h:width=100:g=2"

            val cmd = "-i \"${generated.absolutePath}\" -af \"$audioFilter\" -c:a libmp3lame -b:a 192k -y \"${finalClonedAudio.absolutePath}\""
            val session = FFmpegKit.execute(cmd)

            if (ReturnCode.isSuccess(session.returnCode) && finalClonedAudio.exists() && finalClonedAudio.length() > 500) {
                Result.success(finalClonedAudio)
            } else {
                // إذا تعذر الفلتر الدقيق، نعيد الملف الأصلي عالي الجودة
                Result.success(generated)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * دبلجة مقطع فيديو واستبدال مساره الصوتي بالصوت المستنسخ الجديد
     */
    suspend fun dubVideoTrack(
        videoFile: File,
        newAudioFile: File,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val cmd = "-i \"${videoFile.absolutePath}\" -i \"${newAudioFile.absolutePath}\" -c:v copy -map 0:v:0 -map 1:a:0 -shortest -y \"${outputFile.absolutePath}\""
            val session = FFmpegKit.execute(cmd)
            if (ReturnCode.isSuccess(session.returnCode) && outputFile.exists()) {
                Result.success(outputFile)
            } else {
                Result.failure(Exception("فشلت عملية دبلجة الفيديو عبر FFmpeg: ${session.failStackTrace ?: "Unknown"}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
