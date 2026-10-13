package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DEFAULT_BROWSER_FALLBACKS = listOf(
    "com.android.chrome",
    "com.chrome.beta",
    "org.mozilla.firefox",
    "org.mozilla.firefox.beta",
    "com.opera.browser",
    "com.opera.mini.native",
    "com.sec.android.app.sbrowser",
    "com.microsoft.emmx",
    "com.brave.browser",
    "com.duckduckgo.mobile.android",
    "com.transsion.phoenix",
    "com.android.browser"
)

private fun openUrl(context: Context, url: String) {
    val uri = Uri.parse(url)
    val pm = context.packageManager
    try {
        val implicit = Intent(Intent.ACTION_VIEW, uri)
        if (implicit.resolveActivity(pm) != null) {
            context.startActivity(implicit)
            return
        }
    } catch (_: Exception) {
    }
    for (pkg in DEFAULT_BROWSER_FALLBACKS) {
        try {
            val explicit = Intent(Intent.ACTION_VIEW, uri).setPackage(pkg)
            if (explicit.resolveActivity(pm) != null) {
                context.startActivity(explicit)
                return
            }
        } catch (_: Exception) {
        }
    }
    Toast.makeText(context, "لا يوجد متصفح مثبت — يرجى فتح الرابط يدوياً:\n$url", Toast.LENGTH_LONG).show()
}

private val KEY_PATTERNS: Map<String, Regex> = mapOf(
    "gemini" to Regex("""AIzaSy[A-Za-z0-9_\-]{33}"""),
    "groq" to Regex("""(?:gsk_[A-Za-z0-9_\-]{10,}|xai-[A-Za-z0-9_\-]{10,})"""),
    "huggingface" to Regex("""hf_[A-Za-z0-9]{10,}"""),
    "elevenlabs" to Regex("""xi-[A-Za-z0-9_\-]{10,}"""),
    "openai" to Regex("""sk-[A-Za-z0-9_\-]{20,}"""),
    "openrouter" to Regex("""sk-or-v1-[A-Za-z0-9_\-]{10,}"""),
    "azure" to Regex("""(?i)[a-f0-9]{32}"""),
    "pexels" to Regex("""(?<![A-Za-z0-9_-])[A-Za-z0-9]{56}(?![A-Za-z0-9_-])"""),
    "pixabay" to Regex("""\d{7,10}-[a-f0-9]{16,32}"""),
    "higgsfield" to Regex("""(?:hfld_[A-Za-z0-9_\-]{16,}|[A-Za-z0-9_\-]{32,})""")
)

fun detectKeysForAutoFill(text: String): Map<String, String> {
    val found = linkedMapOf<String, String>()
    KEY_PATTERNS.forEach { (service, regex) ->
        val match = regex.find(text)?.value?.trim()
        if (!match.isNullOrBlank()) found[service] = match
    }
    return found
}

/**
 * كشف نوع المفتاح الواحد للصق الذكي — مرتب من الأدق للأعم:
 * البادئات المميزة أولاً، والأنماط العامة (hex المجرد) أخيراً حتى لا تسرق مفاتيح غيرها.
 */
private val ORDERED_SINGLE_KEY_PATTERNS: List<Pair<String, Regex>> = listOf(
    "gemini" to Regex("""AIzaSy[A-Za-z0-9_\-]{33}"""),
    "groq" to Regex("""(?:gsk_[A-Za-z0-9_\-]{10,}|xai-[A-Za-z0-9_\-]{10,})"""),
    "openrouter" to Regex("""sk-or-v1-[A-Za-z0-9_\-]{10,}"""),
    "openai" to Regex("""sk-[A-Za-z0-9_\-]{20,}"""),
    "huggingface" to Regex("""hf_[A-Za-z0-9]{10,}"""),
    "elevenlabs" to Regex("""xi-[A-Za-z0-9_\-]{10,}"""),
    "higgsfield" to Regex("""hfld_[A-Za-z0-9_\-]{16,}"""),
    "pixabay" to Regex("""\d{7,10}-[a-f0-9]{16,32}"""),
    "pexels" to Regex("""[A-Za-z0-9]{56}"""),
    "azure" to Regex("""(?i)[a-f0-9]{32}""")
)

/** تنظيف اللصق: مسافات وأسطر واقتباس وبادئة Bearer ومحارف خفية. */
fun normalizePastedKey(raw: String): String {
    var s = raw.trim()
        .replace(Regex("[\u200B\u200C\u200D\uFEFF]"), "")
        .replace(Regex("\\s+"), "")
    s = s.removeSurrounding("\"").removeSurrounding("'")
    if (s.startsWith("Bearer ", ignoreCase = true)) s = s.substring(7)
    return s.trim().removeSurrounding("\"").removeSurrounding("'")
}

/** أول تطابق كامل حسب الترتيب الدقيق — null تعني غير معروف. */
fun detectSingleKeyService(input: String): String? {
    val key = normalizePastedKey(input)
    if (key.length < 8) return null
    return ORDERED_SINGLE_KEY_PATTERNS.firstOrNull { (_, rx) -> rx.matches(key) }?.first
}

object ApiKeysBackupManager {
    fun generateExportJson(
        prefs: SharedPreferences,
        currentKeys: Map<String, String>
    ): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("appName", "Qabas")
        root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

        val keysObj = JSONObject()
        val allKeysToExport = listOf(
            "gemini_key",
            "groq_key",
            "openai_key",
            "openrouter_key",
            "pexels_key",
            "pixabay_key",
            "huggingface_key",
            "azure_speech_key",
            "azure_speech_region",
            "elevenlabs_key",
            "higgsfield_api_key"
        )

        for (k in allKeysToExport) {
            val v = currentKeys[k] ?: prefs.getString(k, "") ?: ""
            if (v.isNotBlank()) {
                keysObj.put(k, v.trim())
            }
        }

        root.put("keys", keysObj)
        return root.toString(4)
    }

    fun parseAndApplyImport(
        rawContent: String,
        prefs: SharedPreferences
    ): Pair<Int, Map<String, String>> {
        val importedMap = mutableMapOf<String, String>()

        try {
            val trimmed = rawContent.trim()
            if (trimmed.startsWith("{")) {
                val json = JSONObject(trimmed)
                val targetObj = if (json.has("keys")) json.optJSONObject("keys") ?: json else json

                val keyMappings = mapOf(
                    "gemini" to "gemini_key",
                    "gemini_key" to "gemini_key",
                    "gemini_api_key" to "gemini_key",
                    "groq" to "groq_key",
                    "groq_key" to "groq_key",
                    "groq_api_key" to "groq_key",
                    "azure" to "azure_speech_key",
                    "azure_speech_key" to "azure_speech_key",
                    "azure_speech_region" to "azure_speech_region",
                    "azure_region" to "azure_speech_region",
                    "elevenlabs" to "elevenlabs_key",
                    "elevenlabs_key" to "elevenlabs_key",
                    "pexels" to "pexels_key",
                    "pexels_key" to "pexels_key",
                    "pixabay" to "pixabay_key",
                    "pixabay_key" to "pixabay_key",
                    "huggingface" to "huggingface_key",
                    "huggingface_key" to "huggingface_key",
                    "openai" to "openai_key",
                    "openai_key" to "openai_key",
                    "openrouter" to "openrouter_key",
                    "openrouter_key" to "openrouter_key",
                    "higgsfield" to "higgsfield_api_key",
                    "higgsfield_key" to "higgsfield_api_key",
                    "higgsfield_api_key" to "higgsfield_api_key"
                )

                val keysIterator = targetObj.keys()
                while (keysIterator.hasNext()) {
                    val rawKey = keysIterator.next()
                    val normalizedKey = rawKey.lowercase().trim()
                    val prefKey = keyMappings[normalizedKey] ?: if (keyMappings.values.contains(normalizedKey)) normalizedKey else null

                    val value = targetObj.optString(rawKey, "").trim()
                    if (prefKey != null && value.isNotBlank()) {
                        importedMap[prefKey] = value
                    }
                }
            }
        } catch (_: Exception) {
        }

        if (importedMap.isEmpty()) {
            val lines = rawContent.lines()
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isBlank() || trimmed.startsWith("#") || trimmed.startsWith("//")) continue
                val cleanLine = trimmed.replace(Regex("""^(val|var|const|let|String|final|static|\"|\')\s*"""), "")
                val parts = cleanLine.split("=", ":", limit = 2)
                if (parts.size == 2) {
                    val keyName = parts[0].trim().lowercase().removeSurrounding("\"", "'")
                    val valStr = parts[1].trim().removeSurrounding("\"", "'").removeSuffix(";").removeSuffix(",")
                    if (valStr.isNotBlank()) {
                        when {
                            keyName.contains("openrouter") || valStr.startsWith("sk-or-") -> importedMap["openrouter_key"] = valStr
                            keyName.contains("gemini") -> importedMap["gemini_key"] = valStr
                            keyName.contains("groq") -> importedMap["groq_key"] = valStr
                            keyName.contains("azure_region") || keyName.endsWith("_region") -> importedMap["azure_speech_region"] = valStr
                            keyName.contains("azure") || keyName.contains("speech") -> importedMap["azure_speech_key"] = valStr
                            keyName.contains("eleven") -> importedMap["elevenlabs_key"] = valStr
                            keyName.contains("pexels") -> importedMap["pexels_key"] = valStr
                            keyName.contains("pixabay") -> importedMap["pixabay_key"] = valStr
                            keyName.contains("hugging") || keyName.contains("hf") -> importedMap["huggingface_key"] = valStr
                            keyName.contains("openai") -> importedMap["openai_key"] = valStr
                        }
                    }
                }
            }
        }

        if (importedMap.isNotEmpty()) {
            val editor = prefs.edit()
            importedMap.forEach { (k, v) ->
                editor.putString(k, v.trim())
            }
            editor.apply()
            // إعادة ضبط مسابح المستوردة على القيم الجديدة حتى لا يطغى قديم
            importedMap.forEach { (k, v) ->
                runCatching { KeyPoolManager.setSingleKey(k, v.trim()) }
            }
        }

        return Pair(importedMap.size, importedMap)
    }
}

data class ServiceUiConfig(
    val id: String,
    val name: String,
    val trackerName: String,
    val provider: String,
    val prefsKey: String,
    val serviceType: String,
    val dashboardUrl: String,
    val description: String,
    val placeholder: String,
    val freeFallback: String,
    val icon: ImageVector,
    val hasRegion: Boolean = false,
    val regionPrefsKey: String = ""
)

private val ALL_SERVICES = listOf(
    ServiceUiConfig(
        id = "gemini",
        name = "Gemini AI",
        trackerName = "Gemini",
        provider = "Google AI Studio",
        prefsKey = "gemini_key",
        serviceType = "gemini",
        dashboardUrl = "https://aistudio.google.com/app/apikey",
        description = "المحرك الأساسي لكتابة سيناريوهات الريلز، استخراج المعاني القرآنية، وصياغة الخطافات.",
        placeholder = "AIzaSy...",
        freeFallback = "المحلل البلاغي المحلي المدمج يعمل مجاناً 100% بدون أي مفتاح.",
        icon = Icons.Default.AutoFixHigh
    ),
    ServiceUiConfig(
        id = "groq",
        name = "Groq LPU",
        trackerName = "Groq",
        provider = "Groq Cloud",
        prefsKey = "groq_key",
        serviceType = "groq",
        dashboardUrl = "https://console.groq.com/keys",
        description = "معالج فائق السرعة لتوليد الخطافات والعبارات السينمائية الفورية بسرعة 500+ كلمة/ثانية.",
        placeholder = "gsk_...",
        freeFallback = "توليد النصوص المحلي يعمل فورياً دون الحاجة لمفتاح خارجي.",
        icon = Icons.Default.Speed
    ),
    ServiceUiConfig(
        id = "openai",
        name = "OpenAI",
        trackerName = "OpenAI",
        provider = "OpenAI Platform",
        prefsKey = "openai_key",
        serviceType = "openai",
        dashboardUrl = "https://platform.openai.com/api-keys",
        description = "محرك نماذج GPT-4o المتقدمة لتحليل النصوص والترجمة البلاغية الدقيقة.",
        placeholder = "sk-...",
        freeFallback = "المحرك المحلي و Gemini AI المدمجان بديلان جاهزان مجاناً.",
        icon = Icons.Default.Psychology
    ),
    ServiceUiConfig(
        id = "openrouter",
        name = "OpenRouter",
        trackerName = "OpenRouter",
        provider = "OpenRouter AI",
        prefsKey = "openrouter_key",
        serviceType = "openrouter",
        dashboardUrl = "https://openrouter.ai/keys",
        description = "بوابة موحدة تتيح لك الوصول إلى مئات النماذج التوليدية المجانية والمميزة بمفتاح واحد.",
        placeholder = "sk-or-v1-...",
        freeFallback = "المحركات المدمجة في التطبيق تعمل تلقائياً دون الحاجة للبوابة.",
        icon = Icons.Default.Hub
    ),
    ServiceUiConfig(
        id = "azure",
        name = "Azure Speech",
        trackerName = "Azure TTS",
        provider = "Microsoft Azure",
        prefsKey = "azure_speech_key",
        serviceType = "azure",
        dashboardUrl = "https://portal.azure.com/#create/Microsoft.CognitiveServicesSpeechServices",
        description = "نطق صوتي فصيح بنبرات عربية واقعية وإحساس سينمائي لمقاطع الريلز.",
        placeholder = "32 محرف hex (مثال: 1a2b3c...)",
        freeFallback = "محرك النطق المدمج في نظام أندرويد (TTS) يعمل مجاناً وافتراضياً.",
        icon = Icons.Default.RecordVoiceOver,
        hasRegion = true,
        regionPrefsKey = "azure_speech_region"
    ),
    ServiceUiConfig(
        id = "elevenlabs",
        name = "ElevenLabs",
        trackerName = "ElevenLabs",
        provider = "ElevenLabs AI",
        prefsKey = "elevenlabs_key",
        serviceType = "elevenlabs",
        dashboardUrl = "https://elevenlabs.io/app/settings/api-keys",
        description = "أصوات بشرية سينمائية فائقة الواقعية لنبرات السرد الوثائقي والتاريخي.",
        placeholder = "xi-...",
        freeFallback = "محرك النطق المحلي ومحرك الهاتف يعملان مجاناً دائماً.",
        icon = Icons.Default.Mic
    ),
    ServiceUiConfig(
        id = "huggingface",
        name = "Hugging Face",
        trackerName = "HuggingFace",
        provider = "Hugging Face",
        prefsKey = "huggingface_key",
        serviceType = "huggingface",
        dashboardUrl = "https://huggingface.co/settings/tokens",
        description = "توليد لوحات وخلفيات فنية توضيحية إسلامية بالذكاء الاصطناعي.",
        placeholder = "hf_...",
        freeFallback = "مكتبة الأنماط البصرية والخلفيات الإسلامية المدمجة جاهزة محلياً.",
        icon = Icons.Default.Image
    ),
    ServiceUiConfig(
        id = "pexels",
        name = "Pexels Video",
        trackerName = "Pexels",
        provider = "Pexels API",
        prefsKey = "pexels_key",
        serviceType = "pexels",
        dashboardUrl = "https://www.pexels.com/api/",
        description = "مكتبة مقاطع فيديو سينمائية حرة بدقة 4K و Full HD للدمج كمشاهد B-Roll.",
        placeholder = "56 محرف أبجدي رقمي",
        freeFallback = "كاش المشاهد السينمائي المحلي وقوالب الفيديو المدمجة.",
        icon = Icons.Default.VideoLibrary
    ),
    ServiceUiConfig(
        id = "pixabay",
        name = "Pixabay Media",
        trackerName = "Pixabay",
        provider = "Pixabay API",
        prefsKey = "pixabay_key",
        serviceType = "pixabay",
        dashboardUrl = "https://pixabay.com/api/docs/",
        description = "مكتبة لقطات ومقاطع فيديو طبيعية مساندة للمونتاج الفوري ومؤثرات الحركة.",
        placeholder = "1234567-abcdef123...",
        freeFallback = "المشاهد المدمجة محلياً في التطبيق تعمل دون اتصال بالإنترنت.",
        icon = Icons.Default.CameraRoll
    ),
    ServiceUiConfig(
        id = "higgsfield",
        name = "Higgsfield AI",
        trackerName = "Higgsfield",
        provider = "Higgsfield AI Studio",
        prefsKey = "higgsfield_api_key",
        serviceType = "higgsfield",
        dashboardUrl = "https://higgsfield.ai",
        description = "المحرك السينمائي العالمي الفائق لتوليد الفيديوهات التوليدية (Text-to-Video) وتحريك الصور والمشاهد الدعوية (Image-to-Video).",
        placeholder = "hfld_... أو مفتاح منصة Higgsfield",
        freeFallback = "قوالب الفيديو المدمجة ومكتبة مقاطع الـ B-Roll المحلية تعمل مجاناً.",
        icon = Icons.Default.MovieFilter
    )
)

enum class ApiKeysTab(val title: String, val icon: ImageVector) {
    KEYS("المفاتيح والاتصال الحي", Icons.Default.VpnKey),
    LOGS("سجل الطلبات والتشخيص", Icons.Default.History),
    SYNC("النسخ والمزامنة", Icons.Default.CloudSync)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeysScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }
    val mainScope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(ApiKeysTab.KEYS) }

    // Keys state map
    var keysState by remember {
        mutableStateOf(
            ALL_SERVICES.associate { svc ->
                svc.prefsKey to (prefs.getString(svc.prefsKey, "") ?: "")
            }
        )
    }
    var azureRegion by remember {
        mutableStateOf(prefs.getString("azure_speech_region", "") ?: "")
    }

    var visibilityState by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var testingState by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var validationResults by remember { mutableStateOf<Map<String, KeyValidationResult>>(emptyMap()) }
    var isTestingAll by remember { mutableStateOf(false) }

    var usageStats by remember { mutableStateOf<Map<String, ApiUsageTracker.ApiStat>>(emptyMap()) }
    var recentLogs by remember { mutableStateOf<List<ApiUsageTracker.RequestLogItem>>(emptyList()) }

    var autoCaptureMessage by remember { mutableStateOf<String?>(null) }
    var showSmartPasteDialog by remember { mutableStateOf(false) }
    var smartPasteText by remember { mutableStateOf("") }
    var lastGlobalClipboard by remember { mutableStateOf("") }

    // اللصق الذكي الموحد: حقل واحد يقبل أي صيغة ويكشف نوعها
    var smartInput by remember { mutableStateOf("") }
    var smartShowKey by remember { mutableStateOf(false) }
    var smartTesting by remember { mutableStateOf(false) }
    var smartLatencyMs by remember { mutableStateOf<Long?>(null) }
    val smartDetectedId = remember(smartInput) { detectSingleKeyService(smartInput) }
    val smartDetectedSvc = remember(smartDetectedId) { ALL_SERVICES.find { it.id == smartDetectedId } }

    fun refreshTelemetry() {
        mainScope.launch {
            usageStats = ApiUsageTracker.snapshot(context)
            recentLogs = ApiUsageTracker.getRecentLogs(context)
        }
    }

    LaunchedEffect(Unit) {
        refreshTelemetry()
    }

    // Auto-detect from clipboard
    fun autoDetectAndApply(text: String) {
        if (text.isBlank() || text == lastGlobalClipboard) return
        lastGlobalClipboard = text
        val detected = detectKeysForAutoFill(text)
        if (detected.isEmpty()) return

        val editor = prefs.edit()
        val newKeys = keysState.toMutableMap()
        val appliedNames = mutableListOf<String>()

        detected.forEach { (srv, key) ->
            val prefKey = when (srv) {
                "gemini" -> "gemini_key"
                "groq" -> "groq_key"
                "openai" -> "openai_key"
                "openrouter" -> "openrouter_key"
                "huggingface" -> "huggingface_key"
                "azure" -> "azure_speech_key"
                "elevenlabs" -> "elevenlabs_key"
                "pexels" -> "pexels_key"
                "pixabay" -> "pixabay_key"
                "higgsfield" -> "higgsfield_api_key"
                else -> null
            }
            if (prefKey != null) {
                newKeys[prefKey] = key
                editor.putString(prefKey, key)
                appliedNames.add(srv)
            }
        }
        editor.apply()
        keysState = newKeys
        if (appliedNames.isNotEmpty()) {
            autoCaptureMessage = "تم التقاط ${appliedNames.size} مفتاح من الحافظة تلقائياً: ${appliedNames.joinToString("، ")} ✅"
        }
    }

    val androidClipboard = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager }
    DisposableEffect(Unit) {
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            val text = androidClipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
            autoDetectAndApply(text)
        }
        androidClipboard?.addPrimaryClipChangedListener(listener)
        onDispose { androidClipboard?.removePrimaryClipChangedListener(listener) }
    }

    LaunchedEffect(autoCaptureMessage) {
        if (autoCaptureMessage != null) {
            delay(5000)
            autoCaptureMessage = null
        }
    }

    // Save individual key (single source of truth = pool; legacy mirrored)
    fun saveKey(prefKey: String, value: String) {
        KeyPoolManager.setSingleKey(prefKey, value.trim())
        keysState = keysState + (prefKey to value.trim())
        Toast.makeText(context, "تم حفظ المفتاح بنجاح 💾", Toast.LENGTH_SHORT).show()
    }

    // Test single key
    fun runTestSingleKey(svc: ServiceUiConfig) {
        val key = keysState[svc.prefsKey].orEmpty().trim()
        if (key.isBlank()) {
            Toast.makeText(context, "يرجى كتابة أو لصق المفتاح أولاً قبل إجراء الفحص", Toast.LENGTH_SHORT).show()
            return
        }
        val region = if (svc.hasRegion) azureRegion.trim() else null

        mainScope.launch {
            testingState = testingState + (svc.id to true)
            try {
                val res = ApiKeyValidator.validateKey(context, svc.serviceType, key, region)
                validationResults = validationResults + (svc.id to res)
                refreshTelemetry()
                if (res.isValid) {
                    Toast.makeText(context, "✅ ${svc.name}: الاتصال ناجح (${res.errorCode ?: 200})", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "❌ ${svc.name}: ${res.summary}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "خطأ أثناء الفحص: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                testingState = testingState + (svc.id to false)
            }
        }
    }

    // Test all configured keys
    fun runTestAllKeys() {
        mainScope.launch {
            val targets = ALL_SERVICES.mapNotNull { svc ->
                val key = keysState[svc.prefsKey].orEmpty().trim()
                if (key.isNotBlank()) {
                    val region = if (svc.hasRegion) azureRegion.trim() else null
                    Triple(svc, key, region)
                } else null
            }

            if (targets.isEmpty()) {
                Toast.makeText(context, "لم يتم إدخال أي مفتاح بعد — أدخل مفتاحاً واحداً على الأقل للبدء بالفحص الشامل", Toast.LENGTH_LONG).show()
                return@launch
            }

            isTestingAll = true
            targets.forEach { (svc, _, _) -> testingState = testingState + (svc.id to true) }

            coroutineScope {
                targets.map { (svc, key, region) ->
                    async(Dispatchers.IO) {
                        val res = ApiKeyValidator.validateKey(context, svc.serviceType, key, region)
                        withContext(Dispatchers.Main) {
                            validationResults = validationResults + (svc.id to res)
                            testingState = testingState + (svc.id to false)
                        }
                    }
                }.awaitAll()
            }

            isTestingAll = false
            refreshTelemetry()
            Toast.makeText(context, "اكتمل الفحص الشامل لجميع الخدمات الحية بنجاح ⚡", Toast.LENGTH_LONG).show()
        }
    }

    // اللصق الذكي: تعبئة الحقل الصحيح تلقائياً + تطبيق متطلباته + فحص فوري مع السرعة
    fun runSmartFillAndTest() {
        val clean = normalizePastedKey(smartInput)
        val svcId = detectSingleKeyService(clean)
        if (svcId == null) {
            Toast.makeText(context, "تعذر التعرف على نوع المفتاح — تأكد من نسخه كاملاً دون نقص", Toast.LENGTH_LONG).show()
            return
        }
        val svc = ALL_SERVICES.find { it.id == svcId } ?: return
        KeyPoolManager.setSingleKey(svc.prefsKey, clean)
        keysState = keysState + (svc.prefsKey to clean)
        if (svc.hasRegion && azureRegion.isBlank()) {
            Toast.makeText(context, "تم حفظ مفتاح ${svc.name} — أدخل المنطقة (Region) في بطاقته ثم افحص", Toast.LENGTH_LONG).show()
        }
        mainScope.launch {
            smartTesting = true
            smartLatencyMs = null
            try {
                val region = if (svc.hasRegion) azureRegion.trim().ifBlank { null } else null
                val t0 = System.nanoTime()
                val res = ApiKeyValidator.validateKey(context, svc.serviceType, clean, region)
                val ms = ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L)
                smartLatencyMs = ms
                validationResults = validationResults + (svc.id to res)
                refreshTelemetry()
                Toast.makeText(
                    context,
                    if (res.isValid) "✅ ${svc.name}: يعمل (${ms}ms)" else "❌ ${svc.name}: ${res.summary}",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(context, "خطأ أثناء الفحص: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                smartTesting = false
            }
        }
    }

    // File export/import launchers
    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val json = ApiKeysBackupManager.generateExportJson(prefs, keysState)
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                Toast.makeText(context, "تم تصدير ملف المفاتيح بنجاح 📁✨", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "فشل التصدير: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                val (count, map) = ApiKeysBackupManager.parseAndApplyImport(text, prefs)
                if (count > 0) {
                    val updated = keysState.toMutableMap()
                    map.forEach { (k, v) -> updated[k] = v }
                    keysState = updated
                    map["azure_speech_region"]?.let { azureRegion = it }
                    Toast.makeText(context, "تم استيراد $count مفتاح بنجاح! 📥✨", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "لم يتم العثور على مفاتيح صالحة داخل الملف", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "خطأ في قراءة الملف: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Calculate aggregated metrics
    val totalRequestsSent = remember(usageStats) { usageStats.values.sumOf { it.totalCalls } }
    val totalSuccessful = remember(usageStats) { usageStats.values.sumOf { it.successCalls } }
    val totalFailed = remember(usageStats) { usageStats.values.sumOf { it.failureCalls } }
    val overallSuccessRate = remember(totalRequestsSent, totalSuccessful) {
        if (totalRequestsSent > 0) (totalSuccessful.toFloat() / totalRequestsSent.toFloat() * 100f).toInt() else 0
    }
    val avgLatency = remember(usageStats) {
        val activeStats = usageStats.values.filter { it.totalCalls > 0 }
        if (activeStats.isNotEmpty()) (activeStats.map { it.avgLatencyMs }.average()).toLong() else 0L
    }

    val readinessReport = remember(keysState) {
        OperationalReadinessManager.calculateReadiness(context, keysState)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "لوحة تحكم المفاتيح والاتصال الحي",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "تشخيص مباشر لحركة الطلبات وأسباب النجاح والفشل",
                            fontFamily = CairoFont,
                            color = GoldPrimary,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = GoldPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { refreshTelemetry() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = GoldPrimary)
                    }
                    IconButton(onClick = { exportJsonLauncher.launch("qabas_api_keys.json") }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "تصدير", tint = TextSecondary)
                    }
                    IconButton(onClick = { importJsonLauncher.launch("*/*") }) {
                        Icon(Icons.Default.FileUpload, contentDescription = "استيراد", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSlate)
            )
        },
        containerColor = DeepSlate
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Live Connection Summary Bar
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1322)),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // KPI Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        KpiStatChip(
                            label = "الطلبات المرسلة",
                            value = "$totalRequestsSent",
                            color = Color(0xFF60A5FA),
                            icon = Icons.Default.Send,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        KpiStatChip(
                            label = "الناجحة ($overallSuccessRate%)",
                            value = "$totalSuccessful",
                            color = Color(0xFF10B981),
                            icon = Icons.Default.CheckCircle,
                            modifier = Modifier.weight(1.1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        KpiStatChip(
                            label = "الفاشلة",
                            value = "$totalFailed",
                            color = if (totalFailed > 0) Color(0xFFEF4444) else Color(0xFF94A3B8),
                            icon = Icons.Default.Cancel,
                            modifier = Modifier.weight(0.9f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        KpiStatChip(
                            label = "سرعة الاتصال",
                            value = if (avgLatency > 0) "$avgLatency ms" else "--",
                            color = GoldPrimary,
                            icon = Icons.Default.Speed,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Global Check All Button
                    Button(
                        onClick = { runTestAllKeys() },
                        enabled = !isTestingAll,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTestingAll) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DeepSlate, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جاري فحص جميع الخدمات الحية عبر الإنترنت...", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.FlashOn, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("⚡ فحص شامل لجميع الخدمات الحية الآن", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Navigation Tabs
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = Color(0xFF0F172A),
                contentColor = GoldPrimary
            ) {
                ApiKeysTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(tab.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(tab.title, fontFamily = CairoFont, fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal, fontSize = 11.sp)
                            }
                        },
                        selectedContentColor = GoldPrimary,
                        unselectedContentColor = TextSecondary
                    )
                }
            }

            // Banner for auto-capture
            AnimatedVisibility(
                visible = autoCaptureMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = autoCaptureMessage.orEmpty(),
                            color = Color(0xFF10B981),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { autoCaptureMessage = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "إغلاق", tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Main Content by Tab
            when (selectedTab) {
                ApiKeysTab.KEYS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Spatial HUD Live Diagnostics & Auto-Healing
                        item {
                            SpatialServiceDiagnosticsHub(
                                onNavigateToKeys = { /* already here */ }
                            )
                        }

                        // Notice of Free Fallback
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.08f)),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "قبس يعمل مجاناً 100% دون أي مفتاح إجباري 🎉",
                                            color = Color(0xFF10B981),
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "التحليل البلاغي المحلي ومحرك أندرويد الصوتي ومعالجة الفيديو المدمجة تعمل بالكامل. المفاتيح أدناه اختيارية لرفع الجودة لأقصى حدودها.",
                                            color = TextSecondary,
                                            fontFamily = CairoFont,
                                            fontSize = 11.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Services List
                        // Smart Paste Box: one field accepts any format, detects its type,
                        // applies its requirements, and tests it with live latency.
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1324)),
                                border = BorderStroke(1.2.dp, GoldPrimary.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Bolt, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "اللصق الذكي 🧠 — الصق أي مفتاح فيتعرف عليه تلقائياً",
                                            color = Color.White,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }

                                    OutlinedTextField(
                                        value = smartInput,
                                        onValueChange = { smartInput = it },
                                        label = { Text("الصق المفتاح هنا بأي صيغة (gsk_ ، sk- ، AIzaSy ، hf_ ...)", fontFamily = CairoFont, fontSize = 11.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        visualTransformation = if (smartShowKey) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            Row {
                                                IconButton(onClick = {
                                                    val clip = androidClipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                                    if (clip.isNotBlank()) smartInput = clip
                                                }) {
                                                    Icon(Icons.Default.ContentPaste, contentDescription = "لصق", tint = GoldPrimary, modifier = Modifier.size(18.dp))
                                                }
                                                IconButton(onClick = { smartShowKey = !smartShowKey }) {
                                                    Icon(
                                                        if (smartShowKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                        contentDescription = "إظهار",
                                                        tint = TextSecondary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                if (smartInput.isNotBlank()) {
                                                    IconButton(onClick = {
                                                        smartInput = ""
                                                        smartLatencyMs = null
                                                    }) {
                                                        Icon(Icons.Default.Clear, contentDescription = "مسح", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                            }
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = GoldPrimary,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedLabelColor = GoldPrimary,
                                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                                        )
                                    )

                                    // Live detection chip
                                    if (smartDetectedSvc != null) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF10B981).copy(alpha = 0.12f),
                                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(
                                                    text = "تم التعرف: ${smartDetectedSvc.name} (${smartDetectedSvc.provider}) ⚡",
                                                    color = Color(0xFF10B981),
                                                    fontFamily = CairoFont,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = if (smartDetectedSvc.hasRegion)
                                                        "المتطلبات: يحتاج المنطقة (Region) — املأها في بطاقته أدناه قبل الفحص"
                                                    else
                                                        "المتطلبات: لا شيء إضافي — جاهز للفحص المباشر",
                                                    color = TextSecondary,
                                                    fontFamily = CairoFont,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    } else if (normalizePastedKey(smartInput).length >= 8) {
                                        Text(
                                            text = "غير معروف بعد 🟡 — تأكد من لصق المفتاح كاملاً",
                                            color = Color(0xFFF59E0B),
                                            fontFamily = CairoFont,
                                            fontSize = 11.5.sp
                                        )
                                    }

                                    Button(
                                        onClick = { runSmartFillAndTest() },
                                        enabled = smartDetectedSvc != null && !smartTesting,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = GoldPrimary,
                                            disabledContainerColor = Color.White.copy(alpha = 0.12f),
                                            contentColor = DeepSlate,
                                            disabledContentColor = Color.White.copy(alpha = 0.4f)
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp)
                                    ) {
                                        if (smartTesting) {
                                            CircularProgressIndicator(color = DeepSlate, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("يفحص الاتصال ويقيس السرعة...", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                        } else {
                                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("تعبئة وفحص تلقائياً ⚡", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    }

                                    // Live result + latency
                                    val smartRes = smartDetectedId?.let { validationResults[it] }
                                    if (smartRes != null) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                if (smartRes.isValid) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                                contentDescription = null,
                                                tint = if (smartRes.isValid) Color(0xFF10B981) else Color(0xFFEF4444),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = smartRes.summary +
                                                    (smartLatencyMs?.let { " • سرعة الاتصال: ${it}ms" } ?: ""),
                                                color = Color.White,
                                                fontFamily = CairoFont,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        items(ALL_SERVICES, key = { it.id }) { service ->
                            val currentKey = keysState[service.prefsKey].orEmpty()
                            val stat = usageStats[service.trackerName] ?: ApiUsageTracker.ApiStat()
                            val validation = validationResults[service.id]
                            val isTesting = testingState[service.id] == true
                            val isVisible = visibilityState[service.id] == true

                            ProfessionalServiceCard(
                                service = service,
                                keyText = currentKey,
                                isVisible = isVisible,
                                isTesting = isTesting,
                                stat = stat,
                                validation = validation,
                                onKeyChange = { newKey ->
                                    keysState = keysState + (service.prefsKey to newKey)
                                },
                                onVisibilityToggle = {
                                    visibilityState = visibilityState + (service.id to !isVisible)
                                },
                                onSave = {
                                    saveKey(service.prefsKey, currentKey)
                                },
                                onPaste = {
                                    val clipText = androidClipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                    if (clipText.isNotBlank()) {
                                        keysState = keysState + (service.prefsKey to clipText.trim())
                                        saveKey(service.prefsKey, clipText.trim())
                                    }
                                },
                                onClear = {
                                    KeyPoolManager.clearAll(service.prefsKey)
                                    keysState = keysState + (service.prefsKey to "")
                                    Toast.makeText(context, "تم مسح المفتاح", Toast.LENGTH_SHORT).show()
                                },
                                onTest = {
                                    runTestSingleKey(service)
                                },
                                onOpenUrl = {
                                    openUrl(context, service.dashboardUrl)
                                },
                                hasRegion = service.hasRegion,
                                regionValue = azureRegion,
                                onRegionChange = {
                                    azureRegion = it
                                    prefs.edit().putString("azure_speech_region", it.trim()).apply()
                                },
                                onPoolActiveChanged = { activeKey ->
                                    keysState = keysState + (service.prefsKey to activeKey)
                                }
                            )
                        }
                    }
                }

                ApiKeysTab.LOGS -> {
                    LiveTrafficLogsView(
                        recentLogs = recentLogs,
                        onRefresh = { refreshTelemetry() },
                        onClearLogs = {
                            mainScope.launch {
                                ApiUsageTracker.clearLogs(context)
                                usageStats = ApiUsageTracker.snapshot(context)
                                recentLogs = ApiUsageTracker.getRecentLogs(context)
                                Toast.makeText(context, "تم مسح سجل الطلبات", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                ApiKeysTab.SYNC -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Operational Readiness Card
                        item {
                            ReadinessScoreCard(readinessReport)
                        }

                        // JSON Backup and Restore Card
                        item {
                            JsonBackupCard(
                                onExport = { exportJsonLauncher.launch("qabas_api_keys.json") },
                                onImport = { importJsonLauncher.launch("*/*") },
                                onSmartPaste = { showSmartPasteDialog = true }
                            )
                        }

                        // Cloud Sync Card (Supabase)
                        item {
                            SupabaseSyncCard(context)
                        }
                    }
                }
            }
        }
    }

    // Smart Paste Dialog
    if (showSmartPasteDialog) {
        AlertDialog(
            onDismissRequest = { showSmartPasteDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        val (count, map) = ApiKeysBackupManager.parseAndApplyImport(smartPasteText, prefs)
                        if (count > 0) {
                            val updated = keysState.toMutableMap()
                            map.forEach { (k, v) -> updated[k] = v }
                            keysState = updated
                            map["azure_speech_region"]?.let { azureRegion = it }
                            Toast.makeText(context, "تم استخراج وتوزيع $count مفاتيح بنجاح! 📋✨", Toast.LENGTH_LONG).show()
                            showSmartPasteDialog = false
                            smartPasteText = ""
                        } else {
                            Toast.makeText(context, "لم يتم العثور على صيغ مفاتيح صالحة في النص المدخل", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Text("استخراج وتوزيع المفاتيح", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSmartPasteDialog = false }) {
                    Text("إلغاء", color = TextSecondary, fontFamily = CairoFont)
                }
            },
            title = {
                Text("اللصق الذكي للـ API Keys 📋", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column {
                    Text(
                        "الصق هنا أي ملف .env أو كود JSON أو رسالة نصية تحتوي على مفاتيح متعددة، وسيقوم التطبيق بفرزها وتوزيعها في أماكنها الصحيحة تلقائياً:",
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = smartPasteText,
                        onValueChange = { smartPasteText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        placeholder = { Text("مثال:\nGEMINI_API_KEY=AIzaSy...\nGROQ_API_KEY=gsk_...", fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.5f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}

@Composable
private fun KpiStatChip(
    label: String,
    value: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF1E293B).copy(alpha = 0.8f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(label, color = TextSecondary, fontFamily = CairoFont, fontSize = 9.sp, maxLines = 1)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, color = color, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ProfessionalServiceCard(
    service: ServiceUiConfig,
    keyText: String,
    isVisible: Boolean,
    isTesting: Boolean,
    stat: ApiUsageTracker.ApiStat,
    validation: KeyValidationResult?,
    onKeyChange: (String) -> Unit,
    onVisibilityToggle: () -> Unit,
    onSave: () -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    onTest: () -> Unit,
    onOpenUrl: () -> Unit,
    hasRegion: Boolean = false,
    regionValue: String = "",
    onRegionChange: (String) -> Unit = {},
    onPoolActiveChanged: (String) -> Unit = {}
) {
    val configured = keyText.isNotBlank()

    // Determine Live Status Pill
    val isLiveSuccess = validation?.isValid == true || (validation == null && configured && stat.lastStatusCode in 200..299 && stat.totalCalls > 0)
    val isLiveFailure = validation?.isValid == false || (validation == null && configured && stat.lastStatusCode != null && stat.lastStatusCode !in 200..299)

    val pillColor = when {
        isTesting -> GoldPrimary
        isLiveSuccess -> Color(0xFF10B981)
        isLiveFailure -> Color(0xFFEF4444)
        !configured -> Color(0xFFF59E0B)
        stat.totalCalls == 0L -> Color(0xFF94A3B8)
        else -> Color(0xFF10B981)
    }

    val pillText = when {
        isTesting -> "جاري الفحص الحي... ⏳"
        isLiveSuccess -> "متصل حي (${validation?.errorCode ?: stat.lastStatusCode ?: 200}) 🟢"
        isLiveFailure -> "فشل الاتصال (${validation?.errorCode ?: stat.lastStatusCode ?: "خطأ"}) 🔴"
        !configured -> "غير مُعد (مجاني) 🟡"
        stat.totalCalls == 0L -> "جاهز للاختبار ⚪"
        else -> "متصل مستقر 🟢"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = BorderStroke(1.dp, pillColor.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().testTag("api_key_card_${service.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = pillColor.copy(alpha = 0.15f),
                    shape = CircleShape,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(service.icon, contentDescription = null, tint = pillColor, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(service.name, fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                service.provider,
                                color = TextSecondary,
                                fontFamily = CairoFont,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        service.description,
                        color = TextSecondary,
                        fontFamily = CairoFont,
                        fontSize = 10.sp,
                        maxLines = 2,
                        lineHeight = 14.sp
                    )
                }

                // Live Status Chip
                Surface(
                    color = pillColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, pillColor.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(10.dp), color = pillColor, strokeWidth = 1.5.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = pillText,
                            color = pillColor,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Input Row
            OutlinedTextField(
                value = keyText,
                onValueChange = onKeyChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(service.placeholder, color = Color(0xFF64748B), fontSize = 11.sp) },
                visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onVisibilityToggle, modifier = Modifier.size(32.dp)) {
                            Icon(
                                if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "إظهار/إخفاء",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (keyText.isNotEmpty()) {
                            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "مسح", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedContainerColor = Color(0xFF0B0F19),
                    unfocusedContainerColor = Color(0xFF0B0F19)
                ),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )

            // Region input for Azure
            if (hasRegion) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = regionValue,
                    onValueChange = onRegionChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("منطقة الخادم (Location/Region)", fontFamily = CairoFont, fontSize = 10.sp) },
                    placeholder = { Text("مثال: eastus أو westeurope أو uksouth", color = Color(0xFF64748B), fontSize = 11.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = Color(0xFF0B0F19),
                        unfocusedContainerColor = Color(0xFF0B0F19)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Live Test Button
                Button(
                    onClick = onTest,
                    enabled = !isTesting && configured,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1.3f).testTag("test_key_button_${service.id}")
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = GoldPrimary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.FlashOn, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("فحص حي ⚡", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                // Save Button
                OutlinedButton(
                    onClick = onSave,
                    enabled = configured,
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF0B0F19)),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f).testTag("save_key_button_${service.id}")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("حفظ", color = Color(0xFF10B981), fontFamily = CairoFont, fontSize = 11.sp)
                }

                // Paste Button
                OutlinedButton(
                    onClick = onPaste,
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF0B0F19)),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("لصق", color = TextSecondary, fontFamily = CairoFont, fontSize = 11.sp)
                }

                // Get Key Link
                OutlinedButton(
                    onClick = onOpenUrl,
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF0B0F19)),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(0.9f)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("المفتاح 🔗", color = Color(0xFF60A5FA), fontFamily = CairoFont, fontSize = 10.sp)
                }
            }

            // Key Pool: backup keys with manual switch + per-key test (auto-failover reads active)
            KeyPoolSection(
                service = service,
                regionValue = regionValue,
                onPoolActiveChanged = onPoolActiveChanged
            )

            // Real Live Request Telemetry Strip
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = Color(0xFF0B0F19),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الطلبات: ${stat.totalCalls} 📤",
                        color = Color(0xFF60A5FA),
                        fontFamily = CairoFont,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "الناجحة: ${stat.successCalls} 🟢",
                        color = Color(0xFF10B981),
                        fontFamily = CairoFont,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "الفاشلة: ${stat.failureCalls} 🔴",
                        color = if (stat.failureCalls > 0) Color(0xFFEF4444) else Color.Gray,
                        fontFamily = CairoFont,
                        fontSize = 10.sp
                    )
                    Text(
                        text = if (stat.lastLatencyMs > 0) "${stat.lastLatencyMs} ms ⚡" else "--",
                        color = GoldPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }

            // Diagnostic Reasons Box (Success Reason & Failure Reason)
            val successReason = validation?.takeIf { it.isValid }?.explanation
                ?: stat.lastSuccessReason

            val failureReason = validation?.takeIf { !it.isValid }?.explanation
                ?: stat.lastFailureReason

            val suggestedFix = validation?.suggestedFix

            if (!successReason.isNullOrBlank() || !failureReason.isNullOrBlank() || !configured) {
                Spacer(modifier = Modifier.height(8.dp))

                // Success Reason Box
                if (!successReason.isNullOrBlank()) {
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.09f),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "سبب النجاح وحالة الاتصال 🟢",
                                    color = Color(0xFF10B981),
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = successReason,
                                color = Color(0xFFD1FAE5),
                                fontFamily = CairoFont,
                                fontSize = 10.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                // Failure Reason & Suggested Fix Box
                if (!failureReason.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = Color(0xFFEF4444).copy(alpha = 0.09f),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "سبب الفشل وتشخيص الخطأ 🔴",
                                    color = Color(0xFFEF4444),
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = failureReason,
                                color = Color(0xFFFCA5A5),
                                fontFamily = CairoFont,
                                fontSize = 10.sp,
                                lineHeight = 15.sp
                            )
                            if (!suggestedFix.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "طريقة الحل الموصى بها: $suggestedFix",
                                        color = GoldPrimary,
                                        fontFamily = CairoFont,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Free fallback info if not configured
                if (!configured && successReason.isNullOrBlank() && failureReason.isNullOrBlank()) {
                    Surface(
                        color = Color(0xFF1E293B).copy(alpha = 0.4f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = GoldSecondary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "البديل المجاني: ${service.freeFallback}",
                                color = TextSecondary,
                                fontFamily = CairoFont,
                                fontSize = 9.5.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyPoolSection(
    service: ServiceUiConfig,
    regionValue: String,
    onPoolActiveChanged: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }
    var newKeyInput by remember { mutableStateOf("") }
    var testingIdx by remember { mutableStateOf<Int?>(null) }
    var poolResults by remember { mutableStateOf<Map<Int, KeyValidationResult>>(emptyMap()) }

    val pool = remember(refreshTick) { KeyPoolManager.getKeys(service.prefsKey) }
    val activeIdx = remember(refreshTick) { KeyPoolManager.getActiveIndex(service.prefsKey) }

    fun syncParent() {
        onPoolActiveChanged(KeyPoolManager.getActiveKey(service.prefsKey))
        refreshTick++
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF0B0F19),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = if (pool.size > 1) Color(0xFF10B981) else TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "مفاتيح احتياطية (${pool.size}) 🔑",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (pool.size > 1) {
                        Text(
                            text = "تجاوز تلقائي مفعّل ⚡",
                            color = Color(0xFF10B981),
                            fontFamily = CairoFont,
                            fontSize = 9.5.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(6.dp))
            // Add row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = newKeyInput,
                    onValueChange = { newKeyInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("الصق مفتاحاً احتياطياً...", color = Color(0xFF64748B), fontSize = 10.sp) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = Color(0xFF0B0F19),
                        unfocusedContainerColor = Color(0xFF0B0F19),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                Button(
                    onClick = {
                        val clean = normalizePastedKey(newKeyInput)
                        if (clean.isBlank()) return@Button
                        val detected = detectSingleKeyService(clean)
                        if (detected != null && detected != service.id) {
                            val other = ALL_SERVICES.find { it.id == detected }?.name ?: detected
                            Toast.makeText(context, "هذا المفتاح يبدو لـ $other — أضفه في بطاقتها", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        if (KeyPoolManager.addKey(service.prefsKey, clean)) {
                            newKeyInput = ""
                            syncParent()
                            Toast.makeText(context, "أُضيف المفتاح الاحتياطي ✅", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "المفتاح موجود مسبقاً أو غير صالح", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("إضافة", color = GoldPrimary, fontFamily = CairoFont, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            // Pool rows
            if (pool.isEmpty()) {
                Text(
                    text = "لا مفاتيح بعد — المفتاح المحفوظ أعلاه هو الأساسي. أضف بدائل هنا للتجاوز التلقائي.",
                    color = TextSecondary,
                    fontFamily = CairoFont,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            } else {
                pool.forEachIndexed { idx, key ->
                    val isActive = idx == activeIdx
                    val testing = testingIdx == idx
                    val res = poolResults[idx]
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isActive) Color(0xFF10B981).copy(alpha = 0.08f) else Color(0xFF0B0F19),
                        border = BorderStroke(
                            1.dp,
                            if (isActive) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFF334155)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isActive) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isActive) Color(0xFF10B981) else TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = maskKey(key),
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                if (!isActive) {
                                    TextButton(
                                        onClick = {
                                            if (KeyPoolManager.setActiveIndex(service.prefsKey, idx)) {
                                                syncParent()
                                                Toast.makeText(context, "تم التبديل اليدوي للمفتاح البديل 🔀", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text("تفعيل", fontSize = 10.sp, color = Color(0xFF00E5FF), fontFamily = CairoFont)
                                    }
                                } else {
                                    Text(
                                        "نشط ✅",
                                        fontSize = 9.5.sp,
                                        color = Color(0xFF10B981),
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        testingIdx = idx
                                        scope.launch {
                                            try {
                                                val region = if (service.hasRegion) regionValue.trim().ifBlank { null } else null
                                                val r = ApiKeyValidator.validateKey(context, service.serviceType, key, region)
                                                poolResults = poolResults + (idx to r)
                                            } finally {
                                                testingIdx = null
                                            }
                                        }
                                    },
                                    enabled = testingIdx == null,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    if (testing) {
                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = GoldPrimary, strokeWidth = 1.5.dp)
                                    } else {
                                        Icon(Icons.Default.FlashOn, contentDescription = "فحص", tint = GoldPrimary, modifier = Modifier.size(14.dp))
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        if (KeyPoolManager.removeKeyAt(service.prefsKey, idx)) {
                                            poolResults = poolResults - idx
                                            syncParent()
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                                }
                            }
                            if (res != null) {
                                Text(
                                    text = (if (res.isValid) "✅ " else "❌ ") + res.summary,
                                    color = if (res.isValid) Color(0xFF10B981) else Color(0xFFEF4444),
                                    fontFamily = CairoFont,
                                    fontSize = 9.5.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    if (idx < pool.size - 1) Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

private fun maskKey(key: String): String {
    val t = key.trim()
    return if (t.length <= 12) "••••••" else "${t.take(6)}…${t.takeLast(4)}"
}

@Composable
private fun LiveTrafficLogsView(
    recentLogs: List<ApiUsageTracker.RequestLogItem>,
    onRefresh: () -> Unit,
    onClearLogs: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        // Log Actions Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "سجل الطلبات الحية والتشخيص التفصيلي",
                    color = TextPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "عرض فوري لجميع استجابات الخوادم وأسباب النجاح وأسباب الفشل",
                    color = TextSecondary,
                    fontFamily = CairoFont,
                    fontSize = 10.sp
                )
            }
            Row {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = GoldPrimary)
                }
                if (recentLogs.isNotEmpty()) {
                    IconButton(onClick = onClearLogs) {
                        Icon(Icons.Default.Delete, contentDescription = "مسح", tint = Color(0xFFEF4444))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (recentLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, contentDescription = null, tint = TextSecondary.copy(alpha = 0.4f), modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "لا توجد طلبات مسجلة بعد",
                        color = TextPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "اضغط على زر «فحص شامل لجميع الخدمات الآن» أو افحص أي مفتاح لمشاهدة سجل الطلبات وأسباب الحالات مباشرة هنا.",
                        color = TextSecondary,
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(recentLogs, key = { it.id }) { logItem ->
                    val isSuccess = logItem.isSuccess
                    val statusColor = when {
                        isSuccess -> Color(0xFF10B981)
                        logItem.statusCode == 429 -> Color(0xFFF59E0B)
                        logItem.statusCode in 400..499 -> Color(0xFFEF4444)
                        logItem.statusCode in 500..599 -> Color(0xFFA855F7)
                        else -> Color(0xFFEF4444)
                    }

                    val timeStr = remember(logItem.timestamp) {
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(logItem.timestamp))
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Top Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = statusColor.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = logItem.apiName,
                                            color = statusColor,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = timeStr,
                                        color = TextSecondary,
                                        fontFamily = CairoFont,
                                        fontSize = 10.sp
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = statusColor.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "${logItem.statusCode ?: "Error"} ${logItem.statusText}",
                                            color = statusColor,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${logItem.latencyMs} ms",
                                        color = GoldPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Reason
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    contentDescription = null,
                                    tint = statusColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = if (isSuccess) "سبب النجاح:" else "سبب الفشل:",
                                        color = statusColor,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = logItem.reason,
                                        color = TextPrimary,
                                        fontFamily = CairoFont,
                                        fontSize = 10.5.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                            }

                            // Diagnostic or fix suggestion
                            if (!logItem.diagnostic.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = Color(0xFF1E293B).copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(6.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = logItem.diagnostic,
                                            color = TextSecondary,
                                            fontFamily = CairoFont,
                                            fontSize = 9.5.sp,
                                            lineHeight = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadinessScoreCard(readinessReport: ReadinessReport) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("نسبة جاهزية تشغيل التطبيق الحقيقية", color = TextSecondary, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(readinessReport.statusTitle, color = GoldPrimary, fontSize = 16.sp, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
                Surface(
                    color = GoldPrimary.copy(alpha = 0.15f),
                    shape = CircleShape,
                    border = BorderStroke(2.dp, GoldPrimary)
                ) {
                    Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        Text("${readinessReport.percentage}%", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { readinessReport.percentage / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = when {
                    readinessReport.percentage >= 80 -> Color(0xFF10B981)
                    readinessReport.percentage >= 40 -> GoldPrimary
                    else -> Color(0xFFF59E0B)
                },
                trackColor = Color(0xFF1E293B)
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(readinessReport.statusDescription, color = TextPrimary, fontSize = 11.sp, fontFamily = CairoFont, lineHeight = 16.sp)

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                readinessReport.services.forEach { srv: ServiceReadiness ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (srv.isConfigured) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (srv.isConfigured) Color(0xFF10B981) else Color.Gray,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${srv.name} (+${srv.weight}%)",
                                color = if (srv.isConfigured) Color.White else Color.Gray,
                                fontFamily = CairoFont,
                                fontSize = 11.sp,
                                fontWeight = if (srv.isConfigured) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        Text(
                            text = if (srv.isConfigured) "حقيقي 🟢" else "محاكاة محلي 🟡",
                            color = if (srv.isConfigured) Color(0xFF10B981) else Color(0xFFF5D76E),
                            fontFamily = CairoFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JsonBackupCard(
    onExport: () -> Unit,
    onImport: () -> Unit,
    onSmartPaste: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = GoldPrimary.copy(alpha = 0.15f), shape = CircleShape, modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Backup, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("النسخ الاحتياطي السريع (JSON) ⚡", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("تصدير واستيراد جميع مفاتيحك بضغطة زر لنقلها بين أجهزتك.", color = TextSecondary, fontFamily = CairoFont, fontSize = 10.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onExport,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تصدير JSON 📤", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                Button(
                    onClick = onImport,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    border = BorderStroke(1.dp, GoldPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("استيراد JSON 📥", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSmartPaste,
                border = BorderStroke(1.dp, Color(0xFF334155)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF0B0F19)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = GoldSecondary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("اللصق الذكي والفرز التلقائي 📋", color = GoldSecondary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SupabaseSyncCard(context: Context) {
    var syncState by remember { mutableStateOf(0) }
    var syncMessage by remember { mutableStateOf<String?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF8B5CF6).copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color(0xFF8B5CF6).copy(alpha = 0.15f), shape = CircleShape, modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("المزامنة السحابية (Supabase) ☁️", color = Color(0xFFC4B5FD), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("مزامنة مشفرة بين أجهزتك المختلفة تلقائياً.", color = TextSecondary, fontFamily = CairoFont, fontSize = 10.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (syncMessage != null) {
                Surface(
                    color = if (syncState == 0) Color(0xFF10B981).copy(alpha = 0.1f) else Color(0xFFEF4444).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = syncMessage.orEmpty(),
                        color = if (syncState == 0) Color(0xFF10B981) else Color(0xFFEF4444),
                        fontFamily = CairoFont,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        syncState = 1; syncMessage = null
                        KeySyncService.pushToCloud(context) { _, msg ->
                            syncMessage = msg; syncState = 0
                        }
                    },
                    enabled = syncState == 0 && SupabaseConfig.isConfigured,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (syncState == 1) CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                    else {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text("رفع ☁️", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                Button(
                    onClick = {
                        syncState = 2; syncMessage = null
                        KeySyncService.pullFromCloud(context) { _, msg ->
                            syncMessage = msg; syncState = 0
                        }
                    },
                    enabled = syncState == 0 && SupabaseConfig.isConfigured,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (syncState == 2) CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                    else {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text("سحب 📥", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                Button(
                    onClick = {
                        syncState = 3; syncMessage = null
                        KeySyncService.syncBidirectional(context) { _, msg ->
                            syncMessage = msg; syncState = 0
                        }
                    },
                    enabled = syncState == 0 && SupabaseConfig.isConfigured,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (syncState == 3) CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                    else {
                        Icon(Icons.Default.SyncAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text("مزامنة 🔄", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }

            if (!SupabaseConfig.isConfigured) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("⚠️ Supabase غير مُعد — أضف SUPABASE_URL و SUPABASE_ANON_KEY في .env للمزامنة السحابية", color = Color(0xFFF59E0B), fontFamily = CairoFont, fontSize = 9.sp)
            }
        }
    }
}
