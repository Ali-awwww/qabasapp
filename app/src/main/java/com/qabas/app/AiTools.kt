package com.qabas.app

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * صندوق أدوات النموذج 🛠️ — دوال حقيقية داخل التطبيق يستدعيها النموذج
 * عبر OpenRouter function-calling بدل التخمين من ذاكرته.
 *
 * الأدوات: حالة الجهاز • تشخيص شامل • توليد سكريبت • بحث قرآني • تفسير آية
 * • توليد صورة • توليد فيديو • توليد صوت • نشر كريلز (الدفعة 4).
 * كل أداة تُنفّذ فعلياً أو تُرجع رسالة فشل صادقة — لا نتائج وهمية.
 */
object AiTools {

    data class ToolDef(
        val name: String,
        val description: String,
        val properties: JSONObject,
        val required: List<String>,
        val run: suspend (args: JSONObject) -> String
    )

    private fun strProp(desc: String) = JSONObject().put("type", "string").put("description", desc)
    private fun intProp(desc: String) = JSONObject().put("type", "integer").put("description", desc)

    fun definitions(context: Context): List<ToolDef> = listOf(
        ToolDef(
            name = "device_status",
            description = "تقرير حقيقي عن الجهاز: الطراز والأندرويد والذاكرة والتخزين وحالة المساحة.",
            properties = JSONObject(),
            required = emptyList()
        ) { _ ->
            AiOperatorRouter.execute(context, OperatorIntent(OperatorKind.STATUS, "")) { _ -> }
        },
        ToolDef(
            name = "diagnose",
            description = "فحص شامل حقيقي للتطبيق والخدمات والمفاتيح عبر AppSelfDoctor.",
            properties = JSONObject(),
            required = emptyList()
        ) { _ ->
            AiOperatorRouter.execute(context, OperatorIntent(OperatorKind.DIAGNOSE, "")) { _ -> }
        },
        ToolDef(
            name = "generate_script",
            description = "توليد سكريبت فيديو حقيقي (مشاهد + عناوين + أوصاف) عن موضوع معطى.",
            properties = JSONObject().put("topic", strProp("موضوع الفيديو، مثال: الصبر")),
            required = listOf("topic")
        ) { args ->
            val topic = args.optString("topic").ifBlank { "موضوع عام" }
            AiOperatorRouter.execute(context, OperatorIntent(OperatorKind.SCRIPT, topic, topic)) { _ -> }
        },
        ToolDef(
            name = "search_quran",
            description = "بحث حقيقي في النص العثماني (6236 آية). يُرجع حتى 5 آيات: السورة ورقم الآية والنص.",
            properties = JSONObject().put("query", strProp("كلمة البحث (3 أحرف فأكثر)، مثال: الكرسي")),
            required = listOf("query")
        ) { args ->
            withContext(Dispatchers.IO) {
                val query = args.optString("query")
                QuranDataProvider.loadFromAssets(context)
                val hits = QuranDataProvider.searchVerses(query, 5)
                if (hits.isEmpty()) "لا نتائج لـ «$query» في النص العثماني."
                else buildString {
                    hits.forEach { v ->
                        append("﴿${v.surahName}:${v.verseNumber}﴾ ${v.verseText}\n")
                    }
                }
            }
        },
        ToolDef(
            name = "get_tafsir",
            description = "التفسير الميسر الحقيقي (مجمع الملك فهد) لآية محددة برقم السورة والآية.",
            properties = JSONObject()
                .put("surah", intProp("رقم السورة 1-114"))
                .put("ayah", intProp("رقم الآية")),
            required = listOf("surah", "ayah")
        ) { args ->
            withContext(Dispatchers.IO) {
                val s = args.optInt("surah", -1)
                val a = args.optInt("ayah", -1)
                if (s !in 1..114 || a < 1) return@withContext "أرقام غير صالحة: سورة 1-114 وآية ≥ 1."
                QuranDataProvider.loadTafsirFromAssets(context)
                QuranDataProvider.getTafsirForVerse(s, a)
                    ?.let { "تفسير $s:$a (الميسر): $it" }
                    ?: "لا يتوفر تفسير ميسر لـ $s:$a في النسخة المدمجة."
            }
        },
        ToolDef(
            name = "generate_image",
            description = "توليد صورة حقيقية من وصف نصي عبر AppServices.generateAiImage. " +
                "تُرجع مسار الملف المحلي عند النجاح، أو رسالة فشل تبدأ بـ «تعذّر».",
            properties = JSONObject().put("prompt", strProp("وصف الصورة المطلوبة")),
            required = listOf("prompt")
        ) { args ->
            val prompt = args.optString("prompt").ifBlank { "مشهد دعوي هادئ" }
            val path = AppServices.generateAiImage(prompt)
            if (path.isNullOrBlank()) {
                "تعذّر توليد الصورة: لا مزود صور متاح (تحقق من مفاتيحك أو الاتصال)."
            } else {
                runCatching {
                    SupabaseServices.Database.insertAiGeneration(
                        SupabaseServices.AiGenerationRow(
                            id = UUID.randomUUID().toString(),
                            type = "image",
                            // سلسلة AppServices.generateAiImage الفعلية: HuggingFace ثم Pollinations/FLUX.
                            provider = "huggingface_pollinations",
                            prompt = prompt,
                            filePath = path,
                            // التكلفة الفعلية 0 حتى تُقاس من المزود (خارج نطاق الدفعة 4).
                            costUsd = 0.0
                        )
                    )
                }
                path
            }
        },
        ToolDef(
            name = "generate_video",
            description = "توليد فيديو حقيقي نص-إلى-فيديو عبر Higgsfield/Kling. " +
                "متزامن داخل الأداة (استطلاع حتى ~6 دقائق) — الطابور السحابي يسجّل " +
                "النتيجة المنتهية فقط ولا يحرّك تنفيذاً غير متزامن. تُرجع مسار الملف " +
                "عند النجاح، أو رسالة فشل تبدأ بـ «تعذّر».",
            properties = JSONObject()
                .put("prompt", strProp("وصف الفيديو المطلوب"))
                .put("duration_seconds", intProp("المدة بالثواني (3-30، الافتراضي 5)")),
            required = listOf("prompt")
        ) { args ->
            val prompt = args.optString("prompt").ifBlank { "مشهد دعوي سينمائي" }
            val duration = args.optInt("duration_seconds", 5).coerceIn(3, 30)
            val file = HiggsfieldVideoService.generateTextToVideo(
                context, prompt, durationSeconds = duration
            )
            if (file == null || !file.exists()) {
                runCatching {
                    SupabaseServices.Database.insertStudioJob(
                        SupabaseServices.StudioJobRow(
                            id = UUID.randomUUID().toString(),
                            type = "video",
                            status = "failed",
                            progress = 0f,
                            prompt = prompt,
                            error = "فشل توليد الفيديو عبر Higgsfield"
                        )
                    )
                }
                "تعذّر توليد الفيديو عبر Higgsfield — تحقق من بوابة المنصة والاتصال."
            } else {
                runCatching {
                    SupabaseServices.Database.insertStudioJob(
                        SupabaseServices.StudioJobRow(
                            id = UUID.randomUUID().toString(),
                            type = "video",
                            status = "completed",
                            progress = 1f,
                            prompt = prompt,
                            filePath = file.absolutePath
                        )
                    )
                    SupabaseServices.Database.insertAiGeneration(
                        SupabaseServices.AiGenerationRow(
                            id = UUID.randomUUID().toString(),
                            type = "video",
                            provider = "higgsfield_kling",
                            prompt = prompt,
                            filePath = file.absolutePath,
                            costUsd = 0.0
                        )
                    )
                }
                file.absolutePath
            }
        },
        ToolDef(
            name = "generate_voice",
            description = "توليد تعليق صوتي حقيقي من نص عبر AppServices.generateVoiceover " +
                "(Kokoro ثم Edge TTS ثم Azure/ElevenLabs ثم Android TTS). تُرجع مسار " +
                "الملف الصوتي عند النجاح، أو رسالة فشل تبدأ بـ «تعذّر».",
            properties = JSONObject()
                .put("text", strProp("نص التعليق الصوتي"))
                .put("tone", strProp("النبرة، مثال: وثائقي"))
                .put("voice_id", strProp("معرّف الصوت، مثال: ar-SA-HamedNeural")),
            required = listOf("text")
        ) { args ->
            val text = args.optString("text")
            if (text.isBlank()) {
                "تعذّر توليد الصوت: النص فارغ."
            } else {
                val tone = args.optString("tone").ifBlank { "وثائقي" }
                val voiceId = args.optString("voice_id").ifBlank { "ar-SA-HamedNeural" }
                val path = AppServices.generateVoiceover(text, tone = tone, voiceId = voiceId)
                if (path.isNullOrBlank()) {
                    "تعذّر توليد الصوت: لا محرك صوتي متاح."
                } else {
                    runCatching {
                        SupabaseServices.Database.insertAiGeneration(
                            SupabaseServices.AiGenerationRow(
                                id = UUID.randomUUID().toString(),
                                type = "voice",
                                provider = "auto_kokoro_edge_azure_elevenlabs",
                                prompt = text.take(500),
                                filePath = path,
                                costUsd = 0.0
                            )
                        )
                    }
                    path
                }
            }
        },
        ToolDef(
            name = "publish_to_reels",
            description = "نشر فيديو كريلز بعد فحص أهلية النشر (الحد اليومي والفاصل الزمني). " +
                "الدالة الحالية publishReelWithUpload بلا معامل tags، فالوسوم تُضمَّن في الوصف.",
            properties = JSONObject()
                .put("video_path", strProp("مسار ملف الفيديو المحلي أو رابطه https"))
                .put("title", strProp("عنوان المقطع (3 أحرف فأكثر)"))
                .put("description", strProp("وصف المقطع"))
                .put("tags", strProp("وسوم مفصولة بفواصل — تُضمَّن في الوصف")),
            required = listOf("video_path", "title")
        ) { args ->
            val videoPath = args.optString("video_path")
            val title = args.optString("title").ifBlank { "ريلز دعوي" }
            val description = args.optString("description")
            val tags = args.optString("tags")
            if (videoPath.isBlank()) {
                "تعذّر النشر: لا مسار فيديو."
            } else {
                val email = SupabaseServices.Auth.getCurrentUserEmail()
                val userId = SupabaseServices.Auth.getCurrentUserId()
                if (userId.isNullOrBlank()) {
                    "تعذّر النشر: سجّل الدخول أولاً."
                } else when (val eligibility = ReelPublishManager.checkPublishEligibility(context, email)) {
                    is ReelPublishManager.PublishStatus.Allowed -> {
                        val fullDescription = buildString {
                            append(description)
                            if (tags.isNotBlank()) {
                                if (isNotEmpty()) append("\n")
                                append("الوسوم: $tags")
                            }
                        }.takeIf { it.isNotBlank() }
                        val videoUri = if (videoPath.startsWith("https://")) null
                        else Uri.fromFile(File(videoPath))
                        val directUrl = videoPath.takeIf { it.startsWith("https://") }
                        when (val result = ReelPublishManager.publishReelWithUpload(
                            context = context,
                            userId = userId,
                            userName = email?.substringBefore("@") ?: "مستخدم خادم المسلم",
                            email = email,
                            title = title,
                            description = fullDescription,
                            videoUri = videoUri,
                            directUrl = directUrl,
                            thumbnailBytes = null,
                            category = "دعوي"
                        )) {
                            is ReelPublishManager.PublishResult.Success ->
                                "تم نشر الريلز: ${result.videoUrl}"
                            is ReelPublishManager.PublishResult.Failed ->
                                "تعذّر النشر (${result.stage}): ${result.devMessage}"
                        }
                    }
                    is ReelPublishManager.PublishStatus.DailyLimitExceeded ->
                        "تعذّر النشر: تجاوزت الحد اليومي (${eligibility.maxLimit})."
                    is ReelPublishManager.PublishStatus.CooldownActive ->
                        "تعذّر النشر: انتظر ${eligibility.remainingMinutes} دقيقة قبل النشر التالي."
                    is ReelPublishManager.PublishStatus.Error ->
                        "تعذّر النشر: ${eligibility.message}"
                }
            }
        }
    )

    /** يحوّل التعريفات لصيغة tools في OpenRouter/OpenAI. */
    fun toApiJson(defs: List<ToolDef>): JSONArray = JSONArray().apply {
        defs.forEach { d ->
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", d.name)
                    put("description", d.description)
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", d.properties)
                        put("required", JSONArray(d.required))
                    })
                })
            })
        }
    }
}
