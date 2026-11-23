package com.qabas.app

import android.content.Context
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * ⚡ محرك ومنظومة «MoneyPrinterTurbo» السيبرانية لإنتاج الفيديوهات القصيرة ذاتياً
 * يدعم نمطين:
 * 1) النمط السحابي (Remote FastAPI Server): يربط تطبيق خادم المسلم مع سيرفر MoneyPrinterTurbo عبر الشبكة.
 * 2) النمط المحلي المستقل (Autonomous Standalone Engine): يشغّل فلسفة ومحرك MoneyPrinterTurbo محلياً
 *    بدمج Edge-TTS المجاني العصبي، استخراج لقطات B-Roll الدلالية، ورندرة FFmpegKit بدقة 9:16 و 16:9.
 */
data class MoneyPrinterJobConfig(
    val subject: String,
    val customScript: String = "",
    val aspectRatio: String = "9:16", // "9:16" or "16:9"
    val voiceName: String = "ar-SA-HamedNeural",
    val speechRate: String = "+0%",
    val bgmVolume: Float = 0.18f,
    val subtitleEnabled: Boolean = true,
    val subtitleFontSize: Int = 48,
    val targetPlatform: String = "TikTok & Reels",
    val videoSource: String = "pixabay" // "pixabay"
)

data class MoneyPrinterServerStatus(
    val isOnline: Boolean,
    val serverUrl: String,
    val latencyMs: Long = 0L,
    val message: String = ""
)

data class MoneyPrinterTaskState(
    val taskId: String,
    val stage: String,
    val progress: Float,
    val isFinished: Boolean = false,
    val isFailed: Boolean = false,
    val outputVideoPath: String? = null,
    val errorMessage: String? = null
)

object MoneyPrinterTurboService {
    private const val TAG = "MoneyPrinterTurbo"
    const val PREFS_KEY_SERVER_URL = "money_printer_turbo_server_url"
    const val DEFAULT_LOCAL_SERVER = "http://10.0.2.2:8080" // للمحاكي الأندرويدي أو السيرفر المحلي

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun getSavedServerUrl(context: Context): String {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        return prefs.getString(PREFS_KEY_SERVER_URL, DEFAULT_LOCAL_SERVER) ?: DEFAULT_LOCAL_SERVER
    }

    fun saveServerUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(PREFS_KEY_SERVER_URL, url.trim().trimEnd('/')).apply()
    }

    /**
     * فحص الاتصال بسيرفر MoneyPrinterTurbo
     */
    suspend fun pingServer(serverUrl: String): MoneyPrinterServerStatus = withContext(Dispatchers.IO) {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        val start = System.currentTimeMillis()
        try {
            // ق45: واجهتا الفحص الحقيقيتان فقط تثبتان خادم MoneyPrinterTurbo؛
            // أما «/» فتستجيب لها أي صفحة ويب — كانت تجعل أي رابط
            // «متصلاً بالخادم السحابي» زوراً.
            for (url in listOf("$cleanUrl/api/v1/ping", "$cleanUrl/ping")) {
                try {
                    httpClient.newCall(Request.Builder().url(url).get().build()).execute().use { res ->
                        if (res.isSuccessful) {
                            val elapsed = System.currentTimeMillis() - start
                            return@withContext MoneyPrinterServerStatus(
                                isOnline = true,
                                serverUrl = cleanUrl,
                                latencyMs = elapsed,
                                message = "متصل بنجاح مع خادم أدوات دخل المبدعين ($elapsed ms)"
                            )
                        }
                    }
                } catch (_: Exception) {}
            }
            // بلا واجهة فحص: صفحة رئيسية تستجيب لا تعني خادم MoneyPrinterTurbo،
            // لكنها تعني أن العنوان حي — يُحفظ متصلاً (توافقاً مع إصدارات
            // قديمة بلا /ping) مع بيان أن الواجهة غير مؤكدة.
            try {
                httpClient.newCall(Request.Builder().url("$cleanUrl/").get().build()).execute().use { res ->
                    if (res.isSuccessful) {
                        val elapsed = System.currentTimeMillis() - start
                        return@withContext MoneyPrinterServerStatus(
                            isOnline = true,
                            serverUrl = cleanUrl,
                            latencyMs = elapsed,
                            message = "الخادم يستجيب لكن واجهة MoneyPrinterTurbo غير مؤكدة (بلا /api/v1/ping) — سيُجرَّب الإرسال للسيرفر"
                        )
                    }
                }
            } catch (_: Exception) {}
            MoneyPrinterServerStatus(
                isOnline = false,
                serverUrl = cleanUrl,
                latencyMs = 0,
                message = "تعذر الاتصال بالخادم. تأكد من تشغيل الحاوية أو السيرفر."
            )
        } catch (e: Exception) {
            MoneyPrinterServerStatus(
                isOnline = false,
                serverUrl = cleanUrl,
                latencyMs = 0,
                message = "خطأ في الشبكة: ${e.message}"
            )
        }
    }

    /**
     * إرسال مهمة توليد كاملة إلى خادم MoneyPrinterTurbo الخارجي
     */
    suspend fun submitRemoteJob(
        serverUrl: String,
        config: MoneyPrinterJobConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = serverUrl.trim().trimEnd('/')

            val jsonBody = JSONObject().apply {
                put("video_subject", config.subject)
                if (config.customScript.isNotBlank()) {
                    put("video_script", config.customScript)
                }
                put("video_aspect", config.aspectRatio)
                put("video_source", config.videoSource)
                put("voice_name", config.voiceName)
                put("bgm_volume", config.bgmVolume)
                put("subtitle_enabled", config.subtitleEnabled)
                put("font_size", config.subtitleFontSize)
            }

            // ق45: الإصدار الحالي من MoneyPrinterTurbo يستقبل POST /api/v1/videos،
            // والإصدار القديم POST /api/v1/video/generate — كان الكود يرسل
            // للقديم وحده فيرجع 404 من أي سيرفر حديث فيسقط للنمط المحلي بصمت.
            val endpoints = listOf("$cleanUrl/api/v1/videos", "$cleanUrl/api/v1/video/generate")
            var lastError: Exception? = null
            for (endpoint in endpoints) {
                try {
                    val request = Request.Builder()
                        .url(endpoint)
                        .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    httpClient.newCall(request).execute().use { response ->
                        val bodyString = response.body?.string().orEmpty()
                        if (response.isSuccessful) {
                            val json = JSONObject(bodyString)
                            val taskId = json.optJSONObject("data")?.optString("task_id")?.takeIf { it.isNotBlank() }
                                ?: json.optString("task_id", "").takeIf { it.isNotBlank() }
                            // ق45: بلا معرّف مهمة لا يُخترع واحد عشوائياً — كان
                            // معرّف UUID وهمي يُستعلَم دقائق بلا جدوى.
                            if (taskId == null) {
                                return@withContext Result.failure(Exception("السيرفر قبل المهمة لكنه لم يرجع معرّف مهمة"))
                            }
                            return@withContext Result.success(taskId)
                        }
                        if (response.code == 404) {
                            lastError = Exception("المسار غير موجود على السيرفر ($endpoint)")
                        } else {
                            return@withContext Result.failure(Exception("خطأ من السيرفر (${response.code}): $bodyString"))
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                    lastError = e
                }
            }
            Result.failure(lastError ?: Exception("تعذر الاتصال بالسيرفر"))
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * استعلام حالة مهمة قيد التنفيذ في السيرفر الخارجي
     */
    suspend fun pollRemoteTask(
        serverUrl: String,
        taskId: String
    ): Result<MoneyPrinterTaskState> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = serverUrl.trim().trimEnd('/')
            val endpoint = "$cleanUrl/api/v1/tasks/$taskId"
            val req = Request.Builder().url(endpoint).get().build()
            val res = httpClient.newCall(req).execute()
            val body = res.body?.string().orEmpty()

            if (res.isSuccessful) {
                val json = JSONObject(body)
                val data = json.optJSONObject("data") ?: json
                val state = data.optInt("state", 1)
                val progress = data.optDouble("progress", 0.0).toFloat().coerceIn(0f, 100f)
                // ق45: قد يأتي الفيديو في videos أو combined_videos (استجابة
                // المهمة في MoneyPrinterTurbo تحمل القائمتين).
                val videoList = data.optJSONArray("videos") ?: data.optJSONArray("combined_videos")
                val videoUrl = if (videoList != null && videoList.length() > 0) videoList.optString(0) else data.optString("video_url", null)

                // ق45: ترميز الحالة يختلف بين إصدارات MoneyPrinterTurbo —
                // الحالي: 1 مكتمل، 4 يعمل، ‎-1 فاشل؛ والقديم: 0 معلق،
                // 1 مكتمل، 2 فاشل، 3 قيد المعالجة. كان الكود يفترض 1 يعمل
                // و2 مكتمل و3 فاشل فلم تُعرف مهمة مكتملة أبداً (1 تُعامل
                // «قيد التنفيذ» حتى انتهاء المهلة) وعوملت مهمة قيد التنفيذ
                // (4) «فشلاً». وجود رابط فيديو مع حالة ليست من حالات
                // الانتظار المعروفة يحسم الغامض لصالح النجاح.
                val hasVideo = !videoUrl.isNullOrBlank()
                val isFinished = hasVideo && state != 0 && state != 3 && state != 4 && state != -1
                val isFailed = state == -1 || (state == 2 && !hasVideo)
                val stageDesc = when {
                    isFinished -> "تم اكتمال إنتاج الفيديو بنجاح ✓"
                    isFailed -> "فشل في مرحلة الرندرة"
                    state == 0 -> "في قائمة الانتظار للتحليل..."
                    else -> "جاري التوليد والمونتاج في أدوات دخل المبدعين ($progress%)..."
                }

                Result.success(
                    MoneyPrinterTaskState(
                        taskId = taskId,
                        stage = stageDesc,
                        progress = progress,
                        isFinished = isFinished,
                        isFailed = isFailed,
                        outputVideoPath = videoUrl
                    )
                )
            } else {
                Result.failure(Exception("كود الخطأ: ${res.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * استخراج كلمات مفتاحية بصرية ذكية متقدمة مستوحاة من MoneyPrinterTurbo
     * تترجم المفاهيم الإسلامية والقصصية إلى أفضل مصطلحات بصرية مطلوبة على Pixabay
     */
    fun extractSemanticVisualTerms(topic: String, scriptText: String = ""): List<String> {
        val combined = "$topic $scriptText".lowercase()
        val terms = mutableListOf<String>()

        if (combined.contains("قرآن") || combined.contains("تلاوة") || combined.contains("آية") || combined.contains("مصحف")) {
            terms.addAll(listOf("quran book", "ancient manuscript", "arabic calligraphy", "mosque interior golden light"))
        }
        if (combined.contains("صلاة") || combined.contains("مسجد") || combined.contains("محراب") || combined.contains("أذان")) {
            terms.addAll(listOf("grand mosque", "muslim praying", "minaret sunset", "geometric islamic arch"))
        }
        if (combined.contains("كعبة") || combined.contains("مكة") || combined.contains("حج") || combined.contains("عمرة")) {
            terms.addAll(listOf("kaaba mecca", "pilgrims praying", "spiritual night light"))
        }
        if (combined.contains("صبر") || combined.contains("ابتلاء") || combined.contains("فرج") || combined.contains("أمل")) {
            terms.addAll(listOf("sunrise over mountain", "calm ocean waves", "ray of sunlight clouds", "lone tree desert sunset"))
        }
        if (combined.contains("كون") || combined.contains("خلق") || combined.contains("سماء") || combined.contains("نجوم") || combined.contains("تدبر")) {
            terms.addAll(listOf("milky way galaxy", "deep starry cosmos", "northern lights aurora", "planet earth space"))
        }
        if (combined.contains("تاريخ") || combined.contains("صحابة") || combined.contains("معركة") || combined.contains("سيرة")) {
            terms.addAll(listOf("desert horse rider", "ancient stone city", "caravan sand dunes", "golden sunset dunes"))
        }
        if (combined.contains("ماء") || combined.contains("مطر") || combined.contains("نهر") || combined.contains("طبيعة")) {
            terms.addAll(listOf("waterfall rainforest", "flowing river serene", "rain drops on leaf green"))
        }

        // إضافات عامة فائقة الجودة لضمان وجود لقطات سينمائية دائماً
        if (terms.isEmpty()) {
            terms.addAll(listOf("nature drone 4k", "cinematic clouds sunset", "peaceful forest sunlight", "islamic arch architecture"))
        }

        return terms.distinct()
    }

    /**
     * توليد سكريبت سينمائي تلقائي مخصص للريلز (Hook, Story, Cliffhanger)
     */
    fun generateAutonomousScript(topic: String): String {
        return buildString {
            appendLine("هل تساءلت يوماً عن سر هذا المعنى العظيم في: $topic؟")
            appendLine("في تراثنا وحكمتنا الإيمانية، تكمن قوة حقيقية تغير نظرتنا للواقع.")
            appendLine("كل لحظة تفكر وتدبر تقربك خطوة نحو السكينة والطمأنينة.")
            appendLine("شارك هذا الأثر مع من تحب، واشترك في خادم المسلم للمزيد من الدرر الملهمة.")
        }
    }

    /**
     * تشغيل الأتمتة الكاملة محلياً (Standalone MoneyPrinter Engine)
     * تجمع Edge-TTS + Pixabay B-Roll + FFmpegKit بدقة 9:16
     */
    suspend fun executeAutonomousLocalPipeline(
        context: Context,
        config: MoneyPrinterJobConfig,
        onProgressUpdate: (stage: String, progress: Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val workingDir = File(context.cacheDir, "money_printer_${System.currentTimeMillis()}")
        workingDir.mkdirs()

        try {
            // المرحلة 1: إعداد السيناريو
            // ق45: السيناريو المحلي قالب جاهز لا ذكاء اصطناعي — كان النص
            // يدّعي «بالذكاء الاصطناعي» بينما لا يُستدعى أي نموذج هنا.
            onProgressUpdate("إعداد السيناريو...", 0.15f)
            delay(400)
            val script = if (config.customScript.isNotBlank()) config.customScript else generateAutonomousScript(config.subject)

            // المرحلة 2: توليد التعليق الصوتي (أنماط VoiceStudio المحفوظة — صوت Edge-Neural + نبرة/سرعة — وأصوات Edge-TTS العصبية)
            val audioFile = File(workingDir, "narration.mp3")
            val generatedAudio: File? = if (config.voiceName.startsWith("cloned:")) {
                val profileId = config.voiceName.removePrefix("cloned:")
                val profile = VoiceStudioService.getClonedProfiles(context).find { it.id == profileId }
                if (profile != null) {
                    onProgressUpdate("توليد التعليق الصوتي بالنمط الصوتي (${profile.name})...", 0.35f)
                    val vsRes = VoiceStudioService.synthesizeLocallyWithProfile(context, script, profile)
                    vsRes.getOrNull()
                } else {
                    onProgressUpdate("توليد التعليق الصوتي الفائق عبر محرك Edge-TTS...", 0.35f)
                    EdgeTtsService.synthesizeSpeech(context = context, text = script, voiceId = "ar-SA-HamedNeural", rate = config.speechRate, outFile = audioFile)
                }
            } else {
                onProgressUpdate("توليد التعليق الصوتي الفائق عبر محرك Edge-TTS العصبي (${config.voiceName})...", 0.35f)
                EdgeTtsService.synthesizeSpeech(context = context, text = script, voiceId = config.voiceName, rate = config.speechRate, outFile = audioFile)
            }

            val resolvedAudio: File? = if (generatedAudio != null && generatedAudio.exists() && generatedAudio.length() >= 1000) {
                generatedAudio
            } else {
                onProgressUpdate("الاستعانة بالمحرك الاحتياطي لتوليد الصوت...", 0.40f)
                val backupTtsPath = try { AndroidTTSService.synthesizeSpeech(script) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
                backupTtsPath?.let { File(it) }?.takeIf { it.exists() && it.length() >= 500 }
            }

            // المرحلة 3: جلب لقطات B-Roll السينمائية وتوليد ملف الترجمة (Subtitles)
            onProgressUpdate("استخراج الكلمات البصرية وتنزيل لقطات B-Roll عالية الدقة...", 0.60f)
            val visualTerms = extractSemanticVisualTerms(config.subject, script)
            val stockClips = FreeStockSources.searchFree(
                queryAr = config.subject
            )

            // محاولة تنزيل لقطة B-Roll حقيقية أو صورة خ خلفية وإلا توليد إطار زخرفي احترافي
            var downloadedBrollVideo: File? = null
            var downloadedBrollImage: File? = null
            val firstClip = stockClips.firstOrNull { it.videoUrl.startsWith("http") || it.thumbnailUrl.startsWith("http") }
            if (firstClip != null) {
                if (firstClip.videoUrl.startsWith("http")) {
                    try {
                        val tmpVid = File(workingDir, "broll_clip.mp4")
                        val req = Request.Builder().url(firstClip.videoUrl).build()
                        httpClient.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                resp.body?.byteStream()?.use { inp ->
                                    tmpVid.outputStream().use { out -> inp.copyTo(out) }
                                }
                                if (tmpVid.exists() && tmpVid.length() > 16_000L) {
                                    downloadedBrollVideo = tmpVid
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
                if (downloadedBrollVideo == null && firstClip.thumbnailUrl.startsWith("http")) {
                    try {
                        val tmpImg = File(workingDir, "broll_img.jpg")
                        val req = Request.Builder().url(firstClip.thumbnailUrl).build()
                        httpClient.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                resp.body?.byteStream()?.use { inp ->
                                    tmpImg.outputStream().use { out -> inp.copyTo(out) }
                                }
                                if (tmpImg.exists() && tmpImg.length() > 4_000L) {
                                    downloadedBrollImage = tmpImg
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // توليد ملف الترجمة المزامنة (SRT Subtitles) — ملف مرافق للفيديو
            // يُستخدم في برامج المونتاج، لا يُحرق داخل الصورة؛ ويُنشأ فقط
            // عند تفعيل خيار الترجمة (ق45: كان يُنشأ دائماً متجاهلاً المفتاح).
            val srtFile = File(workingDir, "subtitles.srt")
            // ق45: مدة الفيديو من التعليق الصوتي الفعلي لا من تقدير طول
            // النص — كان التقدير يقصّ السرد عند 30 ثانية للسكريبتات
            // الطويلة فيقطع ‎-shortest التعليق منتصف الجملة.
            val audioDurationSec = resolvedAudio?.let { audioDurationSeconds(it) }
            val durationSec = (audioDurationSec ?: ProductionPowerKit.estimateArabicSpeechDuration(script)).coerceIn(10, 90)
            if (config.subtitleEnabled) {
                try {
                    val lines = script.split("\n", "،", ".").map { it.trim() }.filter { it.isNotBlank() }
                    if (lines.isNotEmpty()) {
                        val secPerLine = (durationSec.toFloat() / lines.size).coerceAtLeast(2.0f)
                        val srtBuilder = StringBuilder()
                        lines.forEachIndexed { idx, line ->
                            val startSec = idx * secPerLine
                            val endSec = (idx + 1) * secPerLine
                            val startStr = formatSrtTime(startSec)
                            val endStr = formatSrtTime(endSec)
                            srtBuilder.appendLine("${idx + 1}")
                            srtBuilder.appendLine("$startStr --> $endStr")
                            srtBuilder.appendLine(line)
                            srtBuilder.appendLine()
                        }
                        srtFile.writeText(srtBuilder.toString())
                    }
                } catch (_: Exception) {}
            }

            // المرحلة 4: المعالجة والمونتاج بـ FFmpeg
            onProgressUpdate("تجميع اللقطات والرندرة النهائية عبر FFmpeg...", 0.80f)
            val finalOutput = File(workingDir, "money_printer_reel_${System.currentTimeMillis()}.mp4")
            val isLandscape = config.aspectRatio.trim() == "16:9"
            val outW = if (isLandscape) 1920 else 1080
            val outH = if (isLandscape) 1080 else 1920

            val brollVideo = downloadedBrollVideo
            val visualVideoPath: String? = when {
                brollVideo != null -> {
                    val normFile = File(workingDir, "broll_norm.mp4")
                    val scaleFilter = "scale=$outW:$outH:force_original_aspect_ratio=increase,crop=$outW:$outH,setsar=1,format=yuv420p"
                    val normCmd = "-y -stream_loop -1 -i \"${brollVideo.absolutePath}\" -t $durationSec -vf \"$scaleFilter\" -an -c:v libx264 -preset ultrafast -crf 26 \"${normFile.absolutePath}\""
                    val s = FFmpegKit.execute(normCmd)
                    if (ReturnCode.isSuccess(s.returnCode) && normFile.exists() && normFile.length() > 5000L) normFile.absolutePath else null
                }
                else -> null
            } ?: run {
                val frameImg = downloadedBrollImage ?: File(workingDir, "procedural_frame.png").also { outImg ->
                    ProceduralBackdropEngine.render(
                        filePath = outImg.absolutePath,
                        width = outW,
                        height = outH,
                        topHex = "#0B1628",
                        bottomHex = "#050A14",
                        accentHex = "#38BDF8",
                        caption = config.subject.ifBlank { script.lineSequence().firstOrNull().orEmpty() },
                        brandLine = "خادم المسلم | QABAS STUDIO",
                        footerText = visualTerms.firstOrNull() ?: "محتوى دعوي هادف"
                    )
                }
                val imgVideo = File(workingDir, "img_motion.mp4")
                val ok = VideoProcessor.generateVideoFromImage(
                    context = context,
                    imagePath = frameImg.absolutePath,
                    durationInSeconds = durationSec,
                    outputPath = imgVideo.absolutePath
                )
                if (ok && imgVideo.exists() && imgVideo.length() > 5000L) imgVideo.absolutePath else null
            }

            val audioPath = resolvedAudio?.absolutePath
            var renderedOk = false
            if (visualVideoPath != null) {
                if (audioPath != null && File(audioPath).exists()) {
                    val muxCmd = "-y -i \"$visualVideoPath\" -i \"$audioPath\" -c:v copy -c:a aac -shortest \"${finalOutput.absolutePath}\""
                    val session = FFmpegKit.execute(muxCmd)
                    renderedOk = ReturnCode.isSuccess(session.returnCode) && finalOutput.exists() && finalOutput.length() > 5000L
                }
                if (!renderedOk) {
                    File(visualVideoPath).copyTo(finalOutput, overwrite = true)
                    renderedOk = finalOutput.exists() && finalOutput.length() > 5000L
                }
            }

            if (renderedOk) {
                // ق45: تسمية ملف الترجمة باسم الفيديو نفسه ليجده محرر
                // المونتاج بجانبه تلقائياً؛ بلا ذلك يبقى subtitles.srt
                // مجهولاً في مجلد cache لا يصل إليه أحد.
                if (config.subtitleEnabled && srtFile.exists()) {
                    runCatching { srtFile.copyTo(File(workingDir, finalOutput.nameWithoutExtension + ".srt"), overwrite = true) }
                }
                // حفظ المشروع تلقائياً في قاعدة البيانات المحلية والسحابية
                try {
                    val projectService = ProjectService(context)
                    projectService.saveProject(
                        ProjectService.Project(
                            id = UUID.randomUUID().toString(),
                            title = config.subject,
                            idea = "تم إنتاجه عبر أدوات دخل المبدعين و VoiceStudio الموحدة",
                            script = script,
                            finalVideoPath = finalOutput.absolutePath,
                            settings = JSONObject().apply {
                                put("aspectRatio", config.aspectRatio)
                                put("voice", config.voiceName)
                                put("engine", "Turbo_Unified_v3")
                            }.toString(),
                            status = "completed"
                        )
                    )
                } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {}

                onProgressUpdate("تم اكتمال الفيديو الريلز بنجاح بنسبة 100% ✓", 1.0f)
                Result.success(finalOutput)
            } else {
                Result.failure(Exception("تعذر توليد الإطار البصري أو استكمال رندرة FFmpeg"))
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Log.e(TAG, "Autonomous pipeline failed", e)
            // ق45: مسح مجلد العمل الفاشل من cache — كان كل فشل يترك مجلداً.
            runCatching { workingDir.deleteRecursively() }
            Result.failure(e)
        }
    }

    /**
     * ق45: تنزيل فيديو مكتمل من السيرفر إلى ملف محلي؛ يقبل رابطاً كاملاً
     * أو مساراً نسبياً (/tasks/.../final-1.mp4) فيُحلّ مقابل رابط السيرفر.
     */
    suspend fun downloadRemoteVideo(serverUrl: String, videoUrl: String, destDir: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            val absolute = if (videoUrl.startsWith("http")) videoUrl
                else serverUrl.trim().trimEnd('/') + "/" + videoUrl.trimStart('/')
            destDir.mkdirs()
            val dest = File(destDir, "money_printer_remote_${System.currentTimeMillis()}.mp4")
            val req = Request.Builder().url(absolute).build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.failure(Exception("تنزيل الفيديو: HTTP ${resp.code}"))
                resp.body?.byteStream()?.use { inp ->
                    dest.outputStream().use { out -> inp.copyTo(out) }
                } ?: return@withContext Result.failure(Exception("تنزيل الفيديو: جسم فارغ"))
            }
            if (dest.exists() && dest.length() > 5000L) Result.success(dest)
            else Result.failure(Exception("تنزيل الفيديو: ملف غير مكتمل"))
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * ق45: مدة ملف صوتي فعلي بالثواني عبر MediaMetadataRetriever؛ null عند
     * تعذر القراءة فيُستخدم تقدير طول النص.
     */
    private fun audioDurationSeconds(file: File): Int? {
        if (!file.exists() || file.length() < 1000L) return null
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.let { (it / 1000L).toInt().takeIf { s -> s > 0 } }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun formatSrtTime(seconds: Float): String {
        val totalMillis = (seconds * 1000).toLong()
        val hours = totalMillis / 3600000
        val minutes = (totalMillis % 3600000) / 60000
        val secs = (totalMillis % 60000) / 1000
        val millis = totalMillis % 1000
        return String.format(java.util.Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, secs, millis)
    }
}
