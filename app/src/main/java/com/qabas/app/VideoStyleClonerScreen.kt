package com.qabas.app

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class ClonedStylePreset(
    val id: String,
    val title: String,
    val sourcePlatform: String, // "YouTube Shorts", "TikTok", "Instagram", "Custom", "Fused"
    val description: String,
    val analysis: VideoStyleAnalysis,
    val isPreset: Boolean = false
)

object StyleVaultManager {
    private const val PREFS_NAME = "qabas_cloned_styles_vault"
    val vaultUpdateTrigger = MutableStateFlow(System.currentTimeMillis())

    fun notifyVaultChanged() {
        vaultUpdateTrigger.value = System.currentTimeMillis()
    }

    val defaultPresets = listOf(
        ClonedStylePreset(
            id = "preset_epic_doc",
            title = "أسلوب الوثائقيات الملحمية 🏛️",
            sourcePlatform = "YouTube Shorts",
            description = "ألوان داكنة دافئة مع تباين ذهبي سينمائي، صوت وثائقي عميق، وزووم ناعم وتأثيرات أثرية.",
            analysis = VideoStyleAnalysis(
                detectedStyle = "وثائقي ملحمي",
                dominantColors = "أسود سينمائي عميق + ذهبي دافئ (Contrast 1:10)",
                transitionSpeed = "بطيئة متوازنة (3-4 ثوان لكل مشهد)",
                movementPatterns = "Slow Cinematic Zoom-In & Pan",
                overallRhythm = "ملحمي، خشوع، تصاعدي",
                audioStyle = "صوت وثائقي عميق + مؤكدات صدى ناعمة بدون إيقاع",
                typographyStyle = "خط كوفي عريض متوهج مع ظهور كلمة بكلمة",
                contentTone = "مؤثر ومهيب",
                targetAudience = "المهتمون بالتاريخ والتدبر",
                keywords = listOf("epic", "history", "cinematic", "gold")
            ),
            isPreset = true
        ),
        ClonedStylePreset(
            id = "preset_viral_3nvus",
            title = "أسلوب الفيديوهات السريعة (3nvus Style) ⚡",
            sourcePlatform = "TikTok",
            description = "قصات خاطفة كل 1.5 ثانية، كابشن أصفر نيون في المنتصف، خطاف صادم في أول ثانية مع مؤثرات Swoosh.",
            analysis = VideoStyleAnalysis(
                detectedStyle = "سريع فيروسي (3nvus)",
                dominantColors = "أسود داكن + أصفر نيون شاشات",
                transitionSpeed = "خاطفة جداً (1.2 - 1.8 ثانية لكل مشهد)",
                movementPatterns = "Glitch / Whip Zoom / Speed Ramp",
                overallRhythm = "حماسي مشدود لخطف الانتباه",
                audioStyle = "صوت جهوري حماسي + مؤثرات حركة خاطفة Swoosh",
                typographyStyle = "خط أصفر عريض متمركز بمنتصف الشاشة animated",
                contentTone = "حماسي ومباشر",
                targetAudience = "جيل الشباب والجمهور السريع",
                keywords = listOf("fast", "viral", "neon", "tiktok")
            ),
            isPreset = true
        ),
        ClonedStylePreset(
            id = "preset_sunset_quran",
            title = "أسلوب تلاوات الغروب والسكينة 🌅",
            sourcePlatform = "Instagram Reels",
            description = "خلفيات طبيعة وغروب دافئة، خط قرآن تجويدي فاخر باللون الأبيض، ونبرة تلاوة خاشعة مرققة.",
            analysis = VideoStyleAnalysis(
                detectedStyle = "تلاوة وروحانيات الغروب",
                dominantColors = "ألوان غروب برتقالية دافئة وأسود خفيف",
                transitionSpeed = "سلسة جداً وناعمة (Slow Fade Out)",
                movementPatterns = "Horizontal Smooth Pan (طبيعة)",
                overallRhythm = "سكينة وراحة نفسية",
                audioStyle = "تلاوة خاشعة ناعمة + صوت عصافير/مطر محيطي",
                typographyStyle = "خط عثماني/أميري فاخر باللون الأبيض الناصع",
                contentTone = "روحاني هادئ",
                targetAudience = "مُحبو التدبر والتلاوات الخاشعة",
                keywords = listOf("nature", "quran", "peaceful", "sunset")
            ),
            isPreset = true
        ),
        ClonedStylePreset(
            id = "preset_cinematic_mystery",
            title = "أسلوب الغموض والتحقيق الوثائقي 🕵️",
            sourcePlatform = "YouTube",
            description = "ألوان تركواز كئيب داكن (Teal & Amber)، إضاءة ظلال قوية، حركة تصوير متلصصة وإيقاع توتر تصاعدي.",
            analysis = VideoStyleAnalysis(
                detectedStyle = "غموض وتحقيق سينمائي",
                dominantColors = "كحلي داكن + نيلي + عنبري خافت (Moody Noir)",
                transitionSpeed = "متفاوتة ذكية مع توقفات مفاجئة للتشويق",
                movementPatterns = "Tracking Shot & Subdued Slow Push-In",
                overallRhythm = "ترقب وغموض وتشويق متصاعد",
                audioStyle = "مؤثرات بصرية عميقة وSub-bass مشحون",
                typographyStyle = "خط تايبوغرافي رفيع متباعد بخلفية شفافة",
                contentTone = "غامض ومثير للفضول",
                targetAudience = "عشاق الألغاز والقصص التاريخية العميقة",
                keywords = listOf("mystery", "noir", "cinematic", "investigation")
            ),
            isPreset = true
        ),
        ClonedStylePreset(
            id = "preset_minimal_podcast",
            title = "أسلوب البودكاست النخبوي والمحاضرات 🎙️",
            sourcePlatform = "YouTube Podcasts",
            description = "إضاءة استوديو دافئة مركّزة، تبديل ناعم بين لقطات المتحدث، خط واضح ومقروء، وتركيز كامل على الفكرة.",
            analysis = VideoStyleAnalysis(
                detectedStyle = "بودكاست معرفي هادئ",
                dominantColors = "رمادي فحمي + خشب دافئ + إضاءة حافة ناعمة",
                transitionSpeed = "هادئة (4-6 ثوان مع تبديل زوايا الحديث)",
                movementPatterns = "ثابتة مع حركة كاميرا دقيقة جداً (Micro-Dolly)",
                overallRhythm = "تأملي فكري عميق ورصين",
                audioStyle = "صوت نقي ومضغوط استوديو خالي من الصدى",
                typographyStyle = "خط حديث San-Serif بنقاط اقتباس واضحة",
                contentTone = "تعليمي وناضج",
                targetAudience = "رواد الفكر والتطوير الشخصي",
                keywords = listOf("podcast", "interview", "minimal", "knowledge")
            ),
            isPreset = true
        )
    )

    fun getVaultStyles(context: Context): List<ClonedStylePreset> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("custom_styles", null)
        val list = mutableListOf<ClonedStylePreset>()
        list.addAll(defaultPresets)

        if (!jsonStr.isNullOrBlank()) {
            try {
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val analysisObj = obj.getJSONObject("analysis")
                    val titleStr = obj.optString("title", "أسلوب مخصص")
                    val analysis = VideoStyleAnalysis(
                        detectedStyle = analysisObj.optString("detectedStyle", titleStr),
                        dominantColors = analysisObj.optString("dominantColors"),
                        transitionSpeed = analysisObj.optString("transitionSpeed"),
                        movementPatterns = analysisObj.optString("movementPatterns"),
                        overallRhythm = analysisObj.optString("overallRhythm"),
                        audioStyle = analysisObj.optString("audioStyle"),
                        typographyStyle = analysisObj.optString("typographyStyle"),
                        contentTone = analysisObj.optString("contentTone"),
                        targetAudience = analysisObj.optString("targetAudience")
                    )
                    list.add(
                        ClonedStylePreset(
                            id = obj.getString("id"),
                            title = titleStr,
                            sourcePlatform = obj.optString("sourcePlatform", "Custom"),
                            description = obj.optString("description", "أسلوب مستنسخ محفوظ"),
                            analysis = analysis,
                            isPreset = false
                        )
                    )
                }
            } catch (_: Exception) {}
        }
        return list
    }

    fun saveCustomStyle(context: Context, style: ClonedStylePreset) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentCustoms = getVaultStyles(context).filter { !it.isPreset && it.id != style.id }.toMutableList()
        currentCustoms.add(0, style)

        val jsonArray = JSONArray()
        for (item in currentCustoms) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("sourcePlatform", item.sourcePlatform)
                put("description", item.description)
                put("analysis", JSONObject().apply {
                    put("detectedStyle", item.analysis.detectedStyle.ifEmpty { item.title })
                    put("dominantColors", item.analysis.dominantColors)
                    put("transitionSpeed", item.analysis.transitionSpeed)
                    put("movementPatterns", item.analysis.movementPatterns)
                    put("overallRhythm", item.analysis.overallRhythm)
                    put("audioStyle", item.analysis.audioStyle)
                    put("typographyStyle", item.analysis.typographyStyle)
                    put("contentTone", item.analysis.contentTone)
                    put("targetAudience", item.analysis.targetAudience)
                })
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("custom_styles", jsonArray.toString()).apply()
        notifyVaultChanged()
    }

    fun deleteCustomStyle(context: Context, styleId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentCustoms = getVaultStyles(context).filter { !it.isPreset && it.id != styleId }

        val jsonArray = JSONArray()
        for (item in currentCustoms) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("sourcePlatform", item.sourcePlatform)
                put("description", item.description)
                put("analysis", JSONObject().apply {
                    put("detectedStyle", item.analysis.detectedStyle.ifEmpty { item.title })
                    put("dominantColors", item.analysis.dominantColors)
                    put("transitionSpeed", item.analysis.transitionSpeed)
                    put("movementPatterns", item.analysis.movementPatterns)
                    put("overallRhythm", item.analysis.overallRhythm)
                    put("audioStyle", item.analysis.audioStyle)
                    put("typographyStyle", item.analysis.typographyStyle)
                    put("contentTone", item.analysis.contentTone)
                    put("targetAudience", item.analysis.targetAudience)
                })
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("custom_styles", jsonArray.toString()).apply()
        notifyVaultChanged()
    }
}

/**
 * محلل الأساليب الفني متعدد المحركات (Universal Multi-Engine Video Style Dissector)
 */
suspend fun analyzeVideoStyleMultiProvider(context: Context, input: String): VideoStyleAnalysis = withContext(Dispatchers.IO) {
    val (provider, apiKey) = UniversalKeyBrain.getActiveEngine()
    val client = SharedHttpClient.instance

    val prompt = """
        أنت خبير مونتاج سينمائي ومحلل فني للفيديوهات (Art Director & Video Editor).
        المستخدم قدم هذا المدخل (قد يكون رابط فيديو تيك توك/يوتيوب شورتس/ريلز، أو وصفاً كتابياً مفصلاً للأسلوب):
        "$input"

        مهمتك تفكيك البصمة الفنية (Style Blueprint) بدقة تامة:
        1. إذا كان رابطاً: استنتج الأسلوب والمونتاج المتوقع لهذا النمط بناءً على الرابط أو خوارزميات المنصة المذكورة.
        2. إذا كان وصفاً: فكك الكلمات إلى معايير مونتاج هندسية صريحة.
        3. أخرج النتيجة بصيغة JSON حصراً بهذا المفتاح والمحتوى:
        {
            "detectedStyle": "اسم الأسلوب الفني بدقة",
            "dominantColors": "لوحة الألوان والتباين (مثال: تباين عالي مع ذهبي دافئ وأسود عميق)",
            "transitionSpeed": "سرعة التقطيع والانتقالات (مثال: خاطفة 1.5 ثانية لكل مشهد)",
            "movementPatterns": "حركة الكاميرا (مثال: زووم بطيء متصاعد مع هزة ناعمة)",
            "overallRhythm": "الإيقاع العام (مثال: ملحمي تصاعدي)",
            "audioStyle": "الهوية الصوتية (مثال: صوت وثائقي عميق ومؤثرات صدمة)",
            "typographyStyle": "نمط الكابشن والخط (مثال: خط عريض متوهج متمركز)",
            "contentTone": "نبرة المحتوى والعاطفة (مثال: مؤثر وملهم)",
            "targetAudience": "الجمهور المستهدف (مثال: الشباب وصناع التغيير)",
            "keywords": ["cinematic", "epic", "viral", "gold"]
        }
    """.trimIndent()

    try {
        // Proactively try official Firebase AI Logic (Gemini 3.8 Flash) via App Check attestation
        try {
            val fbText = FirebaseAiService.generateText(prompt)
            if (fbText.isNotBlank()) {
                val clean = fbText.replace("```json", "").replace("```", "").trim()
                return@withContext parseJsonToAnalysis(clean)
            }
        } catch (_: Exception) { }

        if (apiKey.isNotBlank() && provider != DetectedProvider.NONE) {
            when (provider) {
                DetectedProvider.GEMINI -> {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=$apiKey"
                    val jsonBody = JSONObject().apply {
                        put("contents", JSONArray().put(JSONObject().apply {
                            put("parts", JSONArray().put(JSONObject().apply { put("text", prompt) }))
                        }))
                        put("generationConfig", JSONObject().apply { put("response_mime_type", "application/json") })
                    }
                    val request = Request.Builder()
                        .url(url)
                        .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val text = parseGeminiTextFromResponse(body)
                        if (!text.isNullOrBlank()) {
                            return@withContext parseJsonToAnalysis(text)
                        }
                    }
                }
                DetectedProvider.OPENROUTER, DetectedProvider.OPENAI, DetectedProvider.GROQ -> {
                    val url = when (provider) {
                        DetectedProvider.OPENROUTER -> "https://openrouter.ai/api/v1/chat/completions"
                        DetectedProvider.OPENAI -> "https://api.openai.com/v1/chat/completions"
                        else -> "https://api.groq.com/openai/v1/chat/completions"
                    }
                    val model = when (provider) {
                        DetectedProvider.OPENROUTER -> "anthropic/claude-sonnet-4.5"
                        DetectedProvider.OPENAI -> "gpt-4o"
                        else -> "llama-3.3-70b-versatile"
                    }
                    val jsonBody = JSONObject().apply {
                        put("model", model)
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply { put("role", "system"); put("content", "أنت مخرج مونتاج خبير. أجب دوماً بصيغة JSON نقية ومباشرة بدون مقدمات.") })
                            put(JSONObject().apply { put("role", "user"); put("content", prompt) })
                        })
                    }
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $apiKey")
                        .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val json = JSONObject(body)
                        val text = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                        if (!text.isNullOrBlank()) {
                            return@withContext parseJsonToAnalysis(text)
                        }
                    }
                }
                else -> {}
            }
        }
    } catch (_: Exception) {}

    // Fallback: تحليل دلالي ذكي وسريع محلياً بناءً على الكلمات المفتاحية
    return@withContext deduceHeuristicStyle(input)
}

private fun parseGeminiTextFromResponse(body: String): String? {
    return try {
        val json = JSONObject(body)
        val candidates = json.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null
        val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: return null
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            sb.append(parts.getJSONObject(i).optString("text"))
        }
        sb.toString()
    } catch (_: Exception) { null }
}

private fun parseJsonToAnalysis(text: String): VideoStyleAnalysis {
    return try {
        val clean = text.substringAfter("{").substringBeforeLast("}")
        val obj = JSONObject("{$clean}")
        val kwList = mutableListOf<String>()
        obj.optJSONArray("keywords")?.let { arr ->
            for (i in 0 until arr.length()) kwList.add(arr.optString(i))
        }
        VideoStyleAnalysis(
            detectedStyle = obj.optString("detectedStyle", "أسلوب سينمائي مخصص"),
            dominantColors = obj.optString("dominantColors", "أسود عميق وذهب دافئ"),
            transitionSpeed = obj.optString("transitionSpeed", "سريعة ومتوازنة"),
            movementPatterns = obj.optString("movementPatterns", "حركة زووم سينمائية متصاعدة"),
            overallRhythm = obj.optString("overallRhythm", "ملحمي تصاعدي مؤثر"),
            audioStyle = obj.optString("audioStyle", "صوت جهوري عميق ومؤثرات صوتية ناعمة"),
            typographyStyle = obj.optString("typographyStyle", "خط كوفي عريض متوهج"),
            contentTone = obj.optString("contentTone", "ملهم وجذاب"),
            targetAudience = obj.optString("targetAudience", "صناع المحتوى والشباب"),
            keywords = kwList.ifEmpty { listOf("cinematic", "qabas", "custom") }
        )
    } catch (_: Exception) {
        deduceHeuristicStyle(text)
    }
}

private fun deduceHeuristicStyle(input: String): VideoStyleAnalysis {
    val low = input.lowercase()
    return when {
        "سريع" in low || "tiktok" in low || "تيك" in low || "3nvus" in low || "قصير" in low -> {
            VideoStyleAnalysis(
                detectedStyle = "أسلوب فيروسي خاطف (Fast Viral Hook)",
                dominantColors = "أسود داكن + أصفر وأخضر نيون متوهج",
                transitionSpeed = "خاطفة جداً (1.2 إلى 1.6 ثانية للمشهد)",
                movementPatterns = "Speed Ramp & Dynamic Zoom",
                overallRhythm = "حماسي مشدود ومفاجئ",
                audioStyle = "صوت حماسي جهوري + ضربات Swoosh خاطفة",
                typographyStyle = "خط أصفر عريض متمركز بمنتصف الشاشة بكلمات مفردة",
                contentTone = "حماسي ومشوق",
                targetAudience = "الجمهور السريع ومستخدمو منصات الفيديو القصير",
                keywords = listOf("fast", "viral", "neon", "hook")
            )
        }
        "قرآن" in low || "تلاوة" in low || "سكينة" in low || "هدوء" in low || "غروب" in low -> {
            VideoStyleAnalysis(
                detectedStyle = "أسلوب التلاوات الخاشعة وسكينة الغروب",
                dominantColors = "ألوان الشفق الذهبي والبرتقالي الهادئ",
                transitionSpeed = "انسيابية ناعمة وبطيئة (3.5 - 4 ثوان)",
                movementPatterns = "Horizontal Smooth Drone Pan",
                overallRhythm = "سكينة وخشوع وتأمل",
                audioStyle = "صوت ترتيل نقي مع صدى استوديو رحب",
                typographyStyle = "خط أميري/عثماني مذهب بحواف ناعمة",
                contentTone = "روحاني خاشع ومطمئن",
                targetAudience = "جمهور التدبر والتأمل القرآني",
                keywords = listOf("quran", "peace", "sunset", "reels")
            )
        }
        "غموض" in low || "تحقيق" in low || "جريمة" in low || "وثائقي" in low -> {
            VideoStyleAnalysis(
                detectedStyle = "أسلوب التحقيق الوثائقي والتشويق السينمائي",
                dominantColors = "كحلي كئيب وأسود فاحم مع عنبري خافت",
                transitionSpeed = "متفاوتة ذكية تخلق الترقب والفضول",
                movementPatterns = "Slow Push-In مع اهتزازات يدوية خفيفة",
                overallRhythm = "ترقب وغموض متصاعد",
                audioStyle = "نبضات خافتة ومؤثرات هواء بارد وأصداء عميقة",
                typographyStyle = "خط تقني متباعد بحروف بيضاء صافية",
                contentTone = "جدي وغامض واستقصائي",
                targetAudience = "عشاق التاريخ والقصص البوليسية والتحقيقية",
                keywords = listOf("mystery", "docu", "investigation", "noir")
            )
        }
        else -> {
            VideoStyleAnalysis(
                detectedStyle = "أسلوب قبس السينمائي الفاخر",
                dominantColors = "أسود فحمي DeepSlate مع ذهبي نقي",
                transitionSpeed = "متوازنة احترافية (2.5 ثانية لكل لقطة)",
                movementPatterns = "Cinematic Push-In & Smooth Slide",
                overallRhythm = "ملهم ومؤثر ومتزن",
                audioStyle = "صوت وقور دافئ مع مؤثرات محيطية خافتة",
                typographyStyle = "خط كوفي عريض أنيق باللون الذهبي والأبيض",
                contentTone = "وقور وملهم وهادف",
                targetAudience = "جمهور المحتوى الهادف والمتميز",
                keywords = listOf("cinematic", "gold", "qabas", "premium")
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoStyleClonerScreen(
    onBack: () -> Unit,
    onGenerateProjectWithStyle: (topic: String, style: VideoStyleAnalysis) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    // الرجوع الذكي
    BackHandler { onBack() }

    // المزامنة الحية مع خزانة الأساليب وعقل الذكاء
    val vaultTrigger by StyleVaultManager.vaultUpdateTrigger.collectAsState()
    val vaultList by remember(vaultTrigger) { derivedStateOf { StyleVaultManager.getVaultStyles(context) } }

    val (engineProvider, _) = remember { UniversalKeyBrain.getActiveEngine() }

    // التبويب النشط (0: الاستنساخ | 1: خزانة الأساليب | 2: مختبر الدمج الهجين)
    var selectedPortalTab by remember { mutableIntStateOf(0) }
    val portalTabs = listOf("🧬 تفكيك واستنساخ", "🏛️ بنك الأساليب (${vaultList.size})", "⚗️ مختبر الدمج الهجين")

    // مدخلات الاستنساخ
    var inputMode by remember { mutableIntStateOf(0) } // 0: رابط/نص • 1: فيديو محلي
    var inputUrlOrDesc by remember { mutableStateOf("") }
    var selectedLocalVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedLocalVideoName by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }

    // النتيجة النشطة والمعاينة
    var activeAnalysisResult by remember { mutableStateOf<VideoStyleAnalysis?>(null) }
    var selectedPreset by remember { mutableStateOf<ClonedStylePreset?>(vaultList.firstOrNull()) }

    // نافذة إطلاق المشروع
    var showLaunchProjectDialog by remember { mutableStateOf(false) }
    var newProjectTopic by remember { mutableStateOf("") }
    var targetRatio by remember { mutableStateOf("9:16") }

    // مختبر الدمج الهجين
    var fusionStyle1 by remember { mutableStateOf(vaultList.firstOrNull()) }
    var fusionStyle2 by remember { mutableStateOf(vaultList.getOrNull(1) ?: vaultList.firstOrNull()) }
    var fusionRatio by remember { mutableFloatStateOf(0.5f) }
    var fusionNameInput by remember { mutableStateOf("") }
    var isFusing by remember { mutableStateOf(false) }

    // منتقي الفيديو المحلي المتوافق مع معايير Google Play (Photo Picker)
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedLocalVideoUri = uri
            selectedLocalVideoName = uri.lastPathSegment ?: "مقطع فيديو محلي"
            inputUrlOrDesc = "فيديو محلي: $selectedLocalVideoName"
            Toast.makeText(context, "تم اختيار الفيديو بنجاح: $selectedLocalVideoName 🎬", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(Color(0xFF040711))) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF00F0FF).copy(alpha = 0.12f))
                                    .border(1.dp, Color(0xFF00F0FF).copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoFixHigh,
                                    contentDescription = null,
                                    tint = Color(0xFF00F0FF),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    "مستنسخ الأنماط والمونتاج HUD",
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    "تفكيك البصمات البصرية، استنساخ الفيديوهات، ودمج الأساليب",
                                    color = Color(0xFF94A3B8),
                                    fontFamily = CairoFont,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF040711))
                )

                // ══════════════════════════════════════════════════════════════
                // 🛰️ شريط التحكم والجاهزية السيبراني (SPATIAL TELEMETRY HUD)
                // ══════════════════════════════════════════════════════════════
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    color = Color(0xEE060C18),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF00F0FF).copy(alpha = 0.45f),
                                Color(0xFF8B5CF6).copy(alpha = 0.3f),
                                Color(0xFF10B981).copy(alpha = 0.45f)
                            )
                        )
                    )
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        HudCornerDecor(modifier = Modifier.matchParentSize(), color = Color(0xFF00F0FF).copy(alpha = 0.2f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // مؤشر المحرك الفعال
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (engineProvider != DetectedProvider.NONE) Color(0xFF10B981) else Color(0xFFF59E0B))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (engineProvider != DetectedProvider.NONE) "محرك: ${engineProvider.displayName}" else "المحرك: استدلال ذكي",
                                    color = if (engineProvider != DetectedProvider.NONE) Color(0xFF6EE7B7) else Color(0xFFFDE68A),
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }

                            // مؤشر عدد الأساليب الجاهزة
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Inventory2, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "${vaultList.size} أسلوب متاح",
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // حالة الجاهزية
                            Surface(
                                color = Color(0xFF0F3A2A),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.8.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                            ) {
                                Text(
                                    "جاهز للاستنساخ ⚡",
                                    color = Color(0xFF34D399),
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // ══════════════════════════════════════════════════════════════
                // 🎛️ شريط التبويبات السيبراني (Cybernetic Tabs Selector)
                // ══════════════════════════════════════════════════════════════
                TabRow(
                    selectedTabIndex = selectedPortalTab,
                    containerColor = Color(0xFF040711),
                    contentColor = Color(0xFF00F0FF),
                    divider = { HorizontalDivider(color = Color(0xFF1E2D4A).copy(alpha = 0.5f)) }
                ) {
                    portalTabs.forEachIndexed { index, title ->
                        val isSelected = selectedPortalTab == index
                        Tab(
                            selected = isSelected,
                            onClick = { selectedPortalTab = index },
                            text = {
                                Text(
                                    text = title,
                                    color = if (isSelected) Color(0xFF00F0FF) else Color(0xFF94A3B8),
                                    fontFamily = CairoFont,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.5.sp
                                )
                            }
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFF03060E)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedPortalTab) {
                // ══════════════════════════════════════════════════════════════
                // 🧬 البوابة 0: مصفوفة التفكيك والاستنساخ (Clone Matrix)
                // ══════════════════════════════════════════════════════════════
                0 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. بطاقة الرأس التوضيحية
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xEE090F1E),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFF1E2D4A))
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00F0FF).copy(alpha = 0.15f))
                                            .border(1.dp, Color(0xFF00F0FF).copy(alpha = 0.4f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFF00F0FF), modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            "هضم وتفكيك خلطات الفيديوهات الناجحة 🪄",
                                            color = Color.White,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            "زوّد النظام برابط، وصف، أو اختر فيديو من هاتفك؛ وسيقوم الذكاء الاصطناعي بتفكيك إيقاعه، ألوانه، وتوليد بصمة فنية جاهزة للإنتاج الفوري.",
                                            color = Color(0xFF94A3B8),
                                            fontFamily = CairoFont,
                                            fontSize = 10.5.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }

                        // 2. كرت إدخال المصدر (رابط أو فيديو محلي)
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF080D1A),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color(0xFF1E2D4A).copy(alpha = 0.7f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    // محول نمط الإدخال (رابط مقابل فيديو محلي)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Button(
                                            onClick = { inputMode = 0 },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (inputMode == 0) Color(0xFF00F0FF).copy(alpha = 0.15f) else Color.Transparent
                                            ),
                                            border = if (inputMode == 0) BorderStroke(1.dp, Color(0xFF00F0FF)) else BorderStroke(0.8.dp, Color(0xFF1E293B)),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f).height(40.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Icon(Icons.Default.Link, contentDescription = null, tint = if (inputMode == 0) Color(0xFF00F0FF) else Color.Gray, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("رابط / وصف نصي", color = if (inputMode == 0) Color.White else Color.Gray, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = { inputMode = 1 },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (inputMode == 1) Color(0xFF10B981).copy(alpha = 0.15f) else Color.Transparent
                                            ),
                                            border = if (inputMode == 1) BorderStroke(1.dp, Color(0xFF10B981)) else BorderStroke(0.8.dp, Color(0xFF1E293B)),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f).height(40.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Icon(Icons.Default.VideoFile, contentDescription = null, tint = if (inputMode == 1) Color(0xFF34D399) else Color.Gray, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("فيديو من الهاتف 📱", color = if (inputMode == 1) Color.White else Color.Gray, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    if (inputMode == 0) {
                                        // حقل إدخال الرابط أو الوصف
                                        OutlinedTextField(
                                            value = inputUrlOrDesc,
                                            onValueChange = { inputUrlOrDesc = it },
                                            placeholder = {
                                                Text(
                                                    "ضع رابط (TikTok / YouTube Shorts / Reels) أو صف الأسلوب (مثال: ألوان داكنة مع ذهبي متوهج وتقطيع سريع)...",
                                                    color = Color(0xFF64748B),
                                                    fontFamily = CairoFont,
                                                    fontSize = 11.sp
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF00F0FF),
                                                unfocusedBorderColor = Color(0xFF1E293B),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedContainerColor = Color(0xFF040813),
                                                unfocusedContainerColor = Color(0xFF040813)
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            minLines = 2,
                                            maxLines = 4
                                        )

                                        // أزرار أمثلة سريعة
                                        Text("أمثلة مقترحة للاختبار السريع:", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 10.sp)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            listOf(
                                                "وثائقي ملحمي" to "أسلوب وثائقي ملحمي داكن مع ذهبي وإضاءة أثرية",
                                                "سريع تيك توك" to "أسلوب 3nvus سريع كابشن أصفر نيون وقطع خاطف",
                                                "تلاوة الغروب" to "أسلوب تلاوات خاشعة هادئة مع ألوان غروب وزووم ناعم"
                                            ).forEach { (label, desc) ->
                                                Surface(
                                                    color = Color(0xFF131D2E),
                                                    shape = RoundedCornerShape(6.dp),
                                                    border = BorderStroke(0.6.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                                                    modifier = Modifier.clickable { inputUrlOrDesc = desc }
                                                ) {
                                                    Text(
                                                        label,
                                                        color = Color(0xFF7DD3FC),
                                                        fontFamily = CairoFont,
                                                        fontSize = 9.5.sp,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        // اختيار مقطع فيديو من الهاتف
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    videoPickerLauncher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                                    )
                                                },
                                            color = Color(0xFF060B17),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.2.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(32.dp))
                                                Text(
                                                    if (selectedLocalVideoName.isNotBlank()) "تم اختيار: $selectedLocalVideoName"
                                                    else "انقر لاختيار مقطع فيديو محلي من الهاتف (Photo Picker)",
                                                    color = if (selectedLocalVideoName.isNotBlank()) Color(0xFFA7F3D0) else Color.White,
                                                    fontFamily = CairoFont,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    "يدعم MP4, MOV, WEBM • آمن 100% ومتوافق مع سياسات الأندرويد",
                                                    color = Color(0xFF64748B),
                                                    fontFamily = CairoFont,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }

                                    // زر إطلاق التفكيك والاستنساخ
                                    Button(
                                        onClick = {
                                            val query = inputUrlOrDesc.trim()
                                            if (query.isBlank() && selectedLocalVideoUri == null) {
                                                Toast.makeText(context, "الرجاء كتابة وصف، وضع رابط، أو اختيار فيديو أولاً!", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            isAnalyzing = true
                                            coroutineScope.launch {
                                                try {
                                                    val finalInput = if (inputMode == 1 && selectedLocalVideoName.isNotBlank()) {
                                                        "فيديو محلي للهاتف ($selectedLocalVideoName) يتضمن أسلوب مونتاج احترافي بحاجة لتفكيك ألوانه وحركته"
                                                    } else query

                                                    val analysis = analyzeVideoStyleMultiProvider(context, finalInput)
                                                    activeAnalysisResult = analysis

                                                    // استيعاب وحفظ في StyleBrain و StyleVault
                                                    val styleTitle = "أسلوب مستنسخ: " + query.take(16).ifBlank { "مقطع محلي" }
                                                    StyleBrain.absorbStyleFromAnalysis(context, styleTitle, finalInput, analysis)

                                                    val newPreset = ClonedStylePreset(
                                                        id = "cloned_${System.currentTimeMillis()}",
                                                        title = styleTitle,
                                                        sourcePlatform = if (inputMode == 1) "Local Video 📱" else if (query.contains("tiktok", true)) "TikTok ⚡" else "Custom 🪄",
                                                        description = "${analysis.dominantColors} • ${analysis.transitionSpeed}",
                                                        analysis = analysis,
                                                        isPreset = false
                                                    )
                                                    StyleVaultManager.saveCustomStyle(context, newPreset)
                                                    selectedPreset = newPreset

                                                    Toast.makeText(context, "تم تفكيك الأسلوب واستيعابه في عقل الأساليب بنجاح! 🧠✨", Toast.LENGTH_LONG).show()
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "حدث خطأ: ${e.message} — تم تطبيق النموذج المحسن", Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    isAnalyzing = false
                                                }
                                            }
                                        },
                                        enabled = !isAnalyzing,
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F0FF)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        if (isAnalyzing) {
                                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("جاري تفكيك وهضم الأسلوب بالذكاء الاصطناعي...", color = Color.Black, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        } else {
                                            Icon(Icons.Default.Analytics, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("تفكيك وهضم أسلوب الفيديو الآن 🪄", color = Color.Black, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // 3. بطاقة عرض البصمة المستخرجة (Style Blueprint HUD)
                        activeAnalysisResult?.let { analysis ->
                            item {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xEE060C18),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.2.dp, Color(0xFF00F0FF).copy(alpha = 0.6f))
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        HudCornerDecor(modifier = Modifier.matchParentSize(), color = Color(0xFF00F0FF).copy(alpha = 0.2f))

                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Color(0xFF00F0FF), modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        "بصمة الأسلوب المستخرج (STYLE DNA):",
                                                        color = Color(0xFF00F0FF),
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.5.sp
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        val dnaText = buildString {
                                                            appendLine("=== QABAS STYLE DNA BLUEPRINT ===")
                                                            appendLine("Style: ${analysis.detectedStyle}")
                                                            appendLine("Colors: ${analysis.dominantColors}")
                                                            appendLine("Pacing: ${analysis.transitionSpeed}")
                                                            appendLine("Motion: ${analysis.movementPatterns}")
                                                            appendLine("Audio: ${analysis.audioStyle}")
                                                            appendLine("Typography: ${analysis.typographyStyle}")
                                                            appendLine("Tone: ${analysis.contentTone}")
                                                        }
                                                        clipboard.setText(AnnotatedString(dnaText))
                                                        Toast.makeText(context, "تم نسخ كود الـ DNA للحافظة 📋", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(40.dp)
                                                ) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                                }
                                            }

                                            // عناصر البصمة
                                            StyleBlueprintRow("🎨 لوحة الألوان والتباين", analysis.dominantColors, Color(0xFFF59E0B))
                                            StyleBlueprintRow("⚡ سرعة المونتاج والقطع", analysis.transitionSpeed, Color(0xFFEF4444))
                                            StyleBlueprintRow("🎥 حركة الكاميرا", analysis.movementPatterns, Color(0xFF38BDF8))
                                            StyleBlueprintRow("🎙️ الهوية الصوتية", analysis.audioStyle, Color(0xFFA855F7))
                                            StyleBlueprintRow("✍️ نمط الكابشن والخط", analysis.typographyStyle, Color(0xFF10B981))
                                            StyleBlueprintRow("🎯 النبرة والجمهور", "${analysis.contentTone} • ${analysis.targetAudience}", Color(0xFFF43F5E))

                                            Spacer(modifier = Modifier.height(4.dp))

                                            // زر الإطلاق المباشر لمشروع جديد بهذا الأسلوب
                                            Button(
                                                onClick = {
                                                    selectedPreset = ClonedStylePreset(
                                                        id = "active",
                                                        title = analysis.detectedStyle,
                                                        sourcePlatform = "Active",
                                                        description = analysis.dominantColors,
                                                        analysis = analysis
                                                    )
                                                    showLaunchProjectDialog = true
                                                },
                                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3A2A)),
                                                border = BorderStroke(1.2.dp, Color(0xFF10B981)),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("إطلاق فيديو جديد بهذا الأسلوب المستخرج 🚀", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ══════════════════════════════════════════════════════════════
                // 🏛️ البوابة 1: بنك وخزانة الأساليب (Style Vault HUD)
                // ══════════════════════════════════════════════════════════════
                1 -> {
                    var searchQuery by remember { mutableStateOf("") }
                    val filteredStyles by remember(searchQuery, vaultList) {
                        derivedStateOf {
                            if (searchQuery.isBlank()) vaultList
                            else vaultList.filter {
                                it.title.contains(searchQuery, true) ||
                                it.description.contains(searchQuery, true) ||
                                it.sourcePlatform.contains(searchQuery, true)
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // شريط البحث في الخزانة
                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("بحث في مكتبة الأساليب بالاسم أو المنصة...", color = Color.Gray, fontFamily = CairoFont, fontSize = 11.5.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF00F0FF), modifier = Modifier.size(16.dp)) },
                                trailingIcon = {
                                    if (searchQuery.isNotBlank()) {
                                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "مسح", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF00F0FF),
                                    unfocusedBorderColor = Color(0xFF1E2D4A),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0xFF060B17),
                                    unfocusedContainerColor = Color(0xFF060B17)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                        }

                        // قائمة كروت الأساليب
                        items(filteredStyles, key = { it.id }) { preset ->
                            var isExpanded by remember { mutableStateOf(false) }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = if (selectedPreset?.id == preset.id) Color(0xFF071224) else Color(0xFF050914),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(
                                    if (selectedPreset?.id == preset.id) 1.5.dp else 1.dp,
                                    if (selectedPreset?.id == preset.id) Color(0xFF00F0FF) else Color(0xFF1E2D4A)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Surface(
                                                color = if (preset.isPreset) Color(0xFF1E1B4B) else Color(0xFF0F3A2A),
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(0.8.dp, if (preset.isPreset) Color(0xFF818CF8) else Color(0xFF10B981))
                                            ) {
                                                Text(
                                                    if (preset.isPreset) "قالب أصلي 🏛️" else "مستنسخ 🧬",
                                                    color = if (preset.isPreset) Color(0xFFA5B4FC) else Color(0xFF34D399),
                                                    fontFamily = CairoFont,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Text(
                                                preset.title,
                                                color = Color.White,
                                                fontFamily = CairoFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Surface(
                                            color = Color(0xFF10192C),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                preset.sourcePlatform,
                                                color = Color(0xFF38BDF8),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 9.5.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        preset.description,
                                        color = Color(0xFF94A3B8),
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    )

                                    // تفاصيل الـ Blueprint الموسعة
                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF030712), RoundedCornerShape(8.dp))
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            StyleBlueprintRow("الألوان", preset.analysis.dominantColors, Color(0xFFF59E0B))
                                            StyleBlueprintRow("السرعة", preset.analysis.transitionSpeed, Color(0xFFEF4444))
                                            StyleBlueprintRow("الحركة", preset.analysis.movementPatterns, Color(0xFF38BDF8))
                                            StyleBlueprintRow("الصوت", preset.analysis.audioStyle, Color(0xFFA855F7))
                                            StyleBlueprintRow("الكابشن", preset.analysis.typographyStyle, Color(0xFF10B981))
                                        }
                                    }

                                    HorizontalDivider(color = Color(0xFF1E2D4A).copy(alpha = 0.5f))

                                    // شريط أزرار التحكم
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = { isExpanded = !isExpanded },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Icon(
                                                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = null,
                                                tint = Color(0xFF00F0FF),
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                if (isExpanded) "إخفاء البصمة" else "عرض البصمة",
                                                color = Color(0xFF00F0FF),
                                                fontFamily = CairoFont,
                                                fontSize = 11.sp
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            // زر الحذف للأساليب المخصصة
                                            if (!preset.isPreset) {
                                                IconButton(
                                                    onClick = {
                                                        StyleVaultManager.deleteCustomStyle(context, preset.id)
                                                        Toast.makeText(context, "تم حذف الأسلوب من الخزانة 🗑️", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                                }
                                            }

                                            // زر نسخ الـ DNA
                                            IconButton(
                                                onClick = {
                                                    val dnaText = "Style: ${preset.title} | Colors: ${preset.analysis.dominantColors} | Speed: ${preset.analysis.transitionSpeed}"
                                                    clipboard.setText(AnnotatedString(dnaText))
                                                    Toast.makeText(context, "تم نسخ كود الأسلوب 📋", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = GoldPrimary, modifier = Modifier.size(15.dp))
                                            }

                                            // زر التطبيق والإنتاج
                                            Button(
                                                onClick = {
                                                    selectedPreset = preset
                                                    activeAnalysisResult = preset.analysis
                                                    showLaunchProjectDialog = true
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(34.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("تطبيق الأسلوب", color = Color.Black, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ══════════════════════════════════════════════════════════════
                // ⚗️ البوابة 2: مختبر الدمج الهجين (Style Fusion Lab)
                // ══════════════════════════════════════════════════════════════
                2 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xEE1A0E2A),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.5f))
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Science, contentDescription = null, tint = Color(0xFFC084FC), modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text("مختبر تهجين وخلط الأساليب (Style Fusion) ⚗️", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("امزج بين نمطين مختلفين (مثلاً: ألوان الوثائقيات + سرعة 3nvus) لإنتاج أسلوب سينمائي هجين فريد لم يسبقك إليه أحد!", color = Color(0xFFD8B4FE), fontFamily = CairoFont, fontSize = 10.5.sp)
                                    }
                                }
                            }
                        }

                        // اختيار الأسلوب الأول
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF090D1C),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF1E2D4A))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("1. الأسلوب الأساسي (المصدر أ):", color = Color(0xFF38BDF8), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                        vaultList.take(4).forEach { p ->
                                            val isSel = fusionStyle1?.id == p.id
                                            Surface(
                                                color = if (isSel) Color(0xFF0284C7) else Color(0xFF131F33),
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(0.8.dp, if (isSel) Color(0xFF38BDF8) else Color(0xFF1E293B)),
                                                modifier = Modifier.weight(1f).clickable { fusionStyle1 = p }
                                            ) {
                                                Text(
                                                    p.title.split(" ").take(2).joinToString(" "),
                                                    color = if (isSel) Color.White else Color(0xFF94A3B8),
                                                    fontFamily = CairoFont,
                                                    fontSize = 10.sp,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                    modifier = Modifier.padding(vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // اختيار الأسلوب الثاني
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF090D1C),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF1E2D4A))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("2. الأسلوب الثانوي (المصدر ب):", color = Color(0xFFA855F7), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                        vaultList.takeLast(4).forEach { p ->
                                            val isSel = fusionStyle2?.id == p.id
                                            Surface(
                                                color = if (isSel) Color(0xFF7C3AED) else Color(0xFF131F33),
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(0.8.dp, if (isSel) Color(0xFFA855F7) else Color(0xFF1E293B)),
                                                modifier = Modifier.weight(1f).clickable { fusionStyle2 = p }
                                            ) {
                                                Text(
                                                    p.title.split(" ").take(2).joinToString(" "),
                                                    color = if (isSel) Color.White else Color(0xFF94A3B8),
                                                    fontFamily = CairoFont,
                                                    fontSize = 10.sp,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                    modifier = Modifier.padding(vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // نسبة الخلط والدمج
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF090D1C),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF1E2D4A))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("نسبة المزج:", color = Color.White, fontFamily = CairoFont, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        Text("${(fusionRatio * 100).toInt()}% أ  |  ${((1f - fusionRatio) * 100).toInt()}% ب", color = Color(0xFF00F0FF), fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Slider(
                                        value = fusionRatio,
                                        onValueChange = { fusionRatio = it },
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFF00F0FF),
                                            activeTrackColor = Color(0xFF38BDF8),
                                            inactiveTrackColor = Color(0xFF7C3AED)
                                        )
                                    )
                                }
                            }
                        }

                        // تسمية الأسلوب المدمج وزر الإنشاء
                        item {
                            OutlinedTextField(
                                value = fusionNameInput,
                                onValueChange = { fusionNameInput = it },
                                placeholder = { Text("اسم الأسلوب الهجين الجديد (مثال: وثائقي فيروسي 3nvus)...", color = Color.Gray, fontFamily = CairoFont, fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFA855F7),
                                    unfocusedBorderColor = Color(0xFF1E2D4A),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0xFF060B17),
                                    unfocusedContainerColor = Color(0xFF060B17)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                        }

                        item {
                            Button(
                                onClick = {
                                    val s1 = fusionStyle1
                                    val s2 = fusionStyle2
                                    if (s1 == null || s2 == null) {
                                        Toast.makeText(context, "اختر أسلوبين مختلفين للمزج أولاً!", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isFusing = true
                                    coroutineScope.launch {
                                        val newName = fusionNameInput.ifBlank { "هجين: ${s1.title.take(10)} × ${s2.title.take(10)}" }
                                        val fusedAnalysis = VideoStyleAnalysis(
                                            detectedStyle = newName,
                                            dominantColors = if (fusionRatio >= 0.5f) s1.analysis.dominantColors else s2.analysis.dominantColors,
                                            transitionSpeed = if (fusionRatio < 0.5f) s2.analysis.transitionSpeed else s1.analysis.transitionSpeed,
                                            movementPatterns = "${s1.analysis.movementPatterns} + ${s2.analysis.movementPatterns}",
                                            overallRhythm = "${s1.analysis.overallRhythm} مدمج مع ${s2.analysis.overallRhythm}",
                                            audioStyle = s1.analysis.audioStyle,
                                            typographyStyle = s2.analysis.typographyStyle,
                                            contentTone = "${s1.analysis.contentTone} وحيوي",
                                            targetAudience = "الجمهور المتطلع لأسلوب مبتكر",
                                            keywords = (s1.analysis.keywords + s2.analysis.keywords).distinct()
                                        )

                                        val fusedPreset = ClonedStylePreset(
                                            id = "fused_${System.currentTimeMillis()}",
                                            title = newName,
                                            sourcePlatform = "Hybrid Fusion ⚗️",
                                            description = "دمج هجين بنسبة ${(fusionRatio * 100).toInt()}% من ${s1.title} مع ${s2.title}",
                                            analysis = fusedAnalysis,
                                            isPreset = false
                                        )

                                        StyleVaultManager.saveCustomStyle(context, fusedPreset)
                                        selectedPreset = fusedPreset
                                        activeAnalysisResult = fusedAnalysis
                                        isFusing = false
                                        selectedPortalTab = 1 // الانتقال للخزانة
                                        Toast.makeText(context, "تم تخليق الأسلوب الهجين وحفظه في الخزانة! ⚗️✨", Toast.LENGTH_LONG).show()
                                    }
                                },
                                enabled = !isFusing,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("تخليق وحفظ الأسلوب الهجين الجديد في الخزانة ⚗️", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 🚀 نافذة تأكيد إطلاق المشروع بالأسلوب المختار
    // ══════════════════════════════════════════════════════════════
    if (showLaunchProjectDialog) {
        val styleToApply = selectedPreset?.analysis ?: activeAnalysisResult ?: StyleVaultManager.defaultPresets.first().analysis

        AlertDialog(
            onDismissRequest = { showLaunchProjectDialog = false },
            containerColor = Color(0xFF070D1C),
            shape = RoundedCornerShape(18.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إطلاق فيديو جديد بالأسلوب المستنسخ", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("الأسلوب المطبق: ${selectedPreset?.title ?: styleToApply.detectedStyle}", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("الألوان: ${styleToApply.dominantColors}", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 10.5.sp)
                    Text("السرعة والتقطيع: ${styleToApply.transitionSpeed}", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 10.5.sp)

                    HorizontalDivider(color = Color(0xFF1E2D4A))

                    Text("اكتب موضوع أو فكرة الفيديو الجديد:", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp)
                    OutlinedTextField(
                        value = newProjectTopic,
                        onValueChange = { newProjectTopic = it },
                        placeholder = { Text("مثال: قصة أصحاب الكهف وعجائب التدبر...", color = Color.Gray, fontFamily = CairoFont, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color(0xFF1E2D4A),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    // اختيار الأبعاد
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("9:16 (Shorts/Reels)", "16:9 (Landscape)").forEach { ratio ->
                            val isSel = targetRatio == ratio.substringBefore(" ")
                            Surface(
                                color = if (isSel) Color(0xFF0F3A2A) else Color(0xFF131D2E),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.8.dp, if (isSel) Color(0xFF10B981) else Color(0xFF1E293B)),
                                modifier = Modifier.weight(1f).clickable { targetRatio = ratio.substringBefore(" ") }
                            ) {
                                Text(
                                    ratio,
                                    color = if (isSel) Color(0xFF34D399) else Color(0xFF94A3B8),
                                    fontFamily = CairoFont,
                                    fontSize = 10.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newProjectTopic.isBlank()) {
                            Toast.makeText(context, "أدخل موضوع الفيديو الجديد أولاً!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        showLaunchProjectDialog = false
                        Toast.makeText(context, "جاري تهيئة المشروع بالخلطة المستنسخة 🚀", Toast.LENGTH_SHORT).show()
                        onGenerateProjectWithStyle(newProjectTopic, styleToApply)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("بدء الإنتاج الآن 🔥", color = Color.Black, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLaunchProjectDialog = false }) {
                    Text("إلغاء", color = Color.Gray, fontFamily = CairoFont, fontSize = 12.sp)
                }
            }
        )
    }
}

@Composable
private fun StyleBlueprintRow(label: String, value: String, accentColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(accentColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            "$label: ",
            color = Color(0xFFCBD5E1),
            fontFamily = CairoFont,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            value.ifBlank { "افتراضي سينمائي" },
            color = Color.White,
            fontFamily = CairoFont,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
