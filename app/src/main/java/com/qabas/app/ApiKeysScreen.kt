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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.*
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
)

fun detectKeysForAutoFill(text: String): Map<String, String> {
    val found = linkedMapOf<String, String>()
    KEY_PATTERNS.forEach { (service, regex) ->
        val match = regex.find(text)?.value?.trim()
        if (!match.isNullOrBlank()) found[service] = match
    }
    return found
}

private val ORDERED_SINGLE_KEY_PATTERNS: List<Pair<String, Regex>> = listOf(
    "gemini" to Regex("""AIzaSy[A-Za-z0-9_\-]{33}"""),
    "groq" to Regex("""(?:gsk_[A-Za-z0-9_\-]{10,}|xai-[A-Za-z0-9_\-]{10,})"""),
    "openrouter" to Regex("""sk-or-v1-[A-Za-z0-9_\-]{10,}"""),
    "openai" to Regex("""sk-[A-Za-z0-9_\-]{20,}"""),
    "huggingface" to Regex("""hf_[A-Za-z0-9]{10,}"""),
    "elevenlabs" to Regex("""xi-[A-Za-z0-9_\-]{10,}"""),
    "pixabay" to Regex("""\d{7,10}-[a-f0-9]{16,32}"""),
    "pexels" to Regex("""[A-Za-z0-9]{56}"""),
    "azure" to Regex("""(?i)[a-f0-9]{32}""")
)

fun normalizePastedKey(raw: String): String {
    var s = KeyPoolManager.sanitizeApiKey(raw)
        .replace(Regex("\\s+"), "")
    s = s.removeSurrounding("\"").removeSurrounding("'")
    if (s.startsWith("Bearer ", ignoreCase = true)) s = s.substring(7)
    return KeyPoolManager.sanitizeApiKey(s).removeSurrounding("\"").removeSurrounding("'")
}

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
            "leonardo_api_key",
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
                    "leonardo" to "leonardo_api_key",
                    "leonardo_api_key" to "leonardo_api_key",
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
                val cleanLine = trimmed.replace(Regex("""^(val|var|const|let|String|final|static|"|')\s*"""), "")
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
                            keyName.contains("leonardo") -> importedMap["leonardo_api_key"] = valStr
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
        freeFallback = "تظل الميزات السحابية لـ Gemini معطلة حتى إضافة مفتاح API.",
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
        description = "معالج فائق السرعة لتوليد الخطافات والعبارات الفورية.",
        placeholder = "gsk_...",
        freeFallback = "يتطلب مفتاح Groq للتشغيل السريع.",
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
        description = "محرك نماذج GPT المتقدمة لتحليل النصوص والترجمة البلاغية الدقيقة.",
        placeholder = "sk-...",
        freeFallback = "يمكن استخدام Gemini أو Groq بديلاً عند إضافة مفاتيحهما.",
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
        description = "بوابة موحدة تتيح الوصول إلى مئات النماذج بمفتاح واحد.",
        placeholder = "sk-or-v1-...",
        freeFallback = "يتطلب مفتاح OpenRouter لتوجيه النماذج عبره.",
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
        description = "نطق صوتي فصيح بنبرات عربية واقعية لمقاطع الريلز.",
        placeholder = "32 محرف hex",
        freeFallback = "محرك النطق المدمج في نظام أندرويد (TTS) متاح محلياً.",
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
        description = "أصوات بشرية واقعية لنبرات السرد الوثائقي والتاريخي.",
        placeholder = "xi-...",
        freeFallback = "محرك النطق المدمج في النظام متاح محلياً.",
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
        freeFallback = "الخلفيات المحلية الجاهزة متوفرة داخل التطبيق.",
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
        description = "مكتبة مقاطع فيديو سينمائية بدقة عالية لمشاهد B-Roll.",
        placeholder = "56 محرف أبجدي رقمي",
        freeFallback = "الخلفيات المحلية والأصول المدمجة تعمل بدون اتصال.",
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
        description = "مكتبة لقطات ومقاطع فيديو طبيعية مساندة للمونتاج.",
        placeholder = "1234567-abcdef123...",
        freeFallback = "الأصول المدمجة محلياً تعمل بدون إنترنت.",
        icon = Icons.Default.CameraRoll
    ),
    ServiceUiConfig(
        id = "leonardo",
        name = "Leonardo.ai",
        trackerName = "Leonardo",
        provider = "Leonardo AI",
        prefsKey = "leonardo_api_key",
        serviceType = "leonardo",
        dashboardUrl = "https://app.leonardo.ai/api-access",
        description = "توليد الصور والشخصيات التشكيلية والتصاميم البصرية فائقة الدقة.",
        placeholder = "مفتاح API من app.leonardo.ai",
        freeFallback = "تتوقف ميزة التوليد السحابي حتى إدخال مفتاح Leonardo أو HuggingFace.",
        icon = Icons.Default.Palette
    )
)

enum class ApiKeysTab(val title: String, val icon: ImageVector) {
    KEYS("المفاتيح والاتصال", Icons.Default.VpnKey),
    LOGS("سجل الطلبات", Icons.Default.History),
    SYNC("النسخ والمزامنة", Icons.Default.CloudSync)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeysScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val prefs = remember { AgentPrefs.instance(context) }
    val mainScope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(ApiKeysTab.KEYS) }

    fun reloadKeysFromStorage(): Map<String, String> =
        ALL_SERVICES.associate { svc ->
            svc.prefsKey to (KeyPoolManager.getActiveKey(svc.prefsKey).ifBlank { prefs.getString(svc.prefsKey, "") ?: "" })
        }

    var keysState by remember { mutableStateOf(reloadKeysFromStorage()) }
    var azureRegion by remember { mutableStateOf(prefs.getString("azure_speech_region", "") ?: "") }

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
                else -> null
            }
            if (prefKey != null) {
                val clean = key.trim()
                newKeys[prefKey] = clean
                editor.putString(prefKey, clean)
                runCatching { KeyPoolManager.setSingleKey(prefKey, clean) }
                appliedNames.add(srv)
            }
        }
        editor.apply()
        keysState = newKeys
        if (appliedNames.isNotEmpty()) {
            autoCaptureMessage = "تم التقاط ${appliedNames.size} مفتاح من الحافظة تلقائياً: ${appliedNames.joinToString("، ")}"
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

    fun saveKey(prefKey: String, value: String) {
        val clean = value.trim()
        KeyPoolManager.setSingleKey(prefKey, clean)
        keysState = keysState + (prefKey to clean)
        Toast.makeText(context, "تم حفظ المفتاح بنجاح", Toast.LENGTH_SHORT).show()
    }

    fun runTestSingleKey(svc: ServiceUiConfig) {
        val key = keysState[svc.prefsKey].orEmpty().trim()
        if (key.isBlank()) {
            Toast.makeText(context, "يرجى إدخال أو لصق المفتاح أولاً قبل الفحص", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, "${svc.name}: الاتصال ناجح (${res.errorCode ?: 200})", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "${svc.name}: ${res.summary}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "خطأ أثناء الفحص: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                testingState = testingState + (svc.id to false)
            }
        }
    }

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
                Toast.makeText(context, "لم يتم إدخال أي مفتاح بعد — أدخل مفتاحاً واحداً على الأقل للفحص", Toast.LENGTH_LONG).show()
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
            Toast.makeText(context, "اكتمل الفحص الشامل لجميع المفاتيح المدخلة", Toast.LENGTH_LONG).show()
        }
    }

    fun runSmartFillAndTest() {
        val clean = normalizePastedKey(smartInput)
        val svcId = detectSingleKeyService(clean)
        if (svcId == null) {
            Toast.makeText(context, "تعذر التعرف على نوع المفتاح — تأكد من نسخه كاملاً", Toast.LENGTH_LONG).show()
            return
        }
        val svc = ALL_SERVICES.find { it.id == svcId } ?: return
        KeyPoolManager.setSingleKey(svc.prefsKey, clean)
        keysState = keysState + (svc.prefsKey to clean)
        if (svc.hasRegion && azureRegion.isBlank()) {
            Toast.makeText(context, "تم حفظ مفتاح ${svc.name} — أدخل المنطقة (Region) أدناه ثم افحص", Toast.LENGTH_LONG).show()
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
                    if (res.isValid) "${svc.name}: يعمل (${ms}ms)" else "${svc.name}: ${res.summary}",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(context, "خطأ أثناء الفحص: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                smartTesting = false
            }
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val json = ApiKeysBackupManager.generateExportJson(prefs, keysState)
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                Toast.makeText(context, "تم تصدير ملف المفاتيح بنجاح", Toast.LENGTH_LONG).show()
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
                    keysState = keysState + map
                    map["azure_speech_region"]?.let { azureRegion = it }
                    Toast.makeText(context, "تم استيراد $count مفتاح بنجاح", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "لم يتم العثور على مفاتيح صالحة داخل الملف", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "خطأ في قراءة الملف: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
        containerColor = theme.background,
        topBar = {
            AppleTopBar(
                title = "مفاتيح API والاتصال",
                subtitle = "إدارة المفاتيح، الفحص المباشر، والمزامنة",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { refreshTelemetry() }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = theme.primary)
                    }
                    IconButton(onClick = { exportJsonLauncher.launch("qabas_api_keys.json") }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.FileDownload, contentDescription = "تصدير", tint = theme.textPrimary)
                    }
                    IconButton(onClick = { importJsonLauncher.launch("*/*") }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.FileUpload, contentDescription = "استيراد", tint = theme.textPrimary)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Compact KPI & Test-All Bar
            Surface(
                color = theme.cardSurface,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KpiStatChip(
                            label = "الطلبات",
                            value = "$totalRequestsSent",
                            color = theme.primary,
                            icon = Icons.Default.Send,
                            modifier = Modifier.weight(1f)
                        )
                        KpiStatChip(
                            label = "ناجحة ($overallSuccessRate%)",
                            value = "$totalSuccessful",
                            color = theme.success,
                            icon = Icons.Default.CheckCircle,
                            modifier = Modifier.weight(1.1f)
                        )
                        KpiStatChip(
                            label = "فاشلة",
                            value = "$totalFailed",
                            color = if (totalFailed > 0) theme.destructive else theme.textSecondary,
                            icon = Icons.Default.Cancel,
                            modifier = Modifier.weight(0.9f)
                        )
                        KpiStatChip(
                            label = "السرعة",
                            value = if (avgLatency > 0) "$avgLatency ms" else "--",
                            color = if (theme.isDark) AppleDarkGold else AppleLightGold,
                            icon = Icons.Default.Speed,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    ApplePrimaryButton(
                        text = if (isTestingAll) "جاري فحص جميع الخدمات الحية..." else "فحص شامل لجميع المفاتيح الآن",
                        icon = Icons.Default.FlashOn,
                        isLoading = isTestingAll,
                        onClick = { runTestAllKeys() }
                    )

                    AppleSegmentedControl(
                        items = ApiKeysTab.entries.map { it to it.title },
                        selectedItem = selectedTab,
                        onSelect = { selectedTab = it }
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = theme.separator.copy(alpha = 0.45f))

            AnimatedVisibility(
                visible = autoCaptureMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    color = theme.success.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = theme.success, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = autoCaptureMessage.orEmpty(),
                            color = theme.success,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { autoCaptureMessage = null }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "إغلاق", tint = theme.success, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            when (selectedTab) {
                ApiKeysTab.KEYS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            SpatialServiceDiagnosticsHub(onNavigateToKeys = null)
                        }

                        // Smart Paste Card
                        item {
                            AppleCard {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Bolt, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "اللصق الذكي — التعرف التلقائي على المفاتيح",
                                            color = theme.textPrimary,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }

                                    OutlinedTextField(
                                        value = smartInput,
                                        onValueChange = { smartInput = it },
                                        label = { Text("الصق المفتاح هنا (AIzaSy, gsk_, sk-, hf_ ...)", fontFamily = NotoSansFont, fontSize = 12.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        visualTransformation = if (smartShowKey) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            Row {
                                                IconButton(onClick = {
                                                    val clip = androidClipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                                    if (clip.isNotBlank()) smartInput = clip
                                                }) {
                                                    Icon(Icons.Default.ContentPaste, contentDescription = "لصق", tint = theme.primary, modifier = Modifier.size(18.dp))
                                                }
                                                IconButton(onClick = { smartShowKey = !smartShowKey }) {
                                                    Icon(
                                                        if (smartShowKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                        contentDescription = "إظهار/إخفاء",
                                                        tint = theme.textSecondary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                if (smartInput.isNotBlank()) {
                                                    IconButton(onClick = {
                                                        smartInput = ""
                                                        smartLatencyMs = null
                                                    }) {
                                                        Icon(Icons.Default.Clear, contentDescription = "مسح", tint = theme.textSecondary, modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    if (smartDetectedSvc != null) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = theme.success.copy(alpha = 0.10f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(
                                                    text = "تم التعرف: ${smartDetectedSvc.name} (${smartDetectedSvc.provider})",
                                                    color = theme.success,
                                                    fontFamily = CairoFont,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.5.sp
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = if (smartDetectedSvc.hasRegion)
                                                        "يتطلب إدخال المنطقة (Region) في بطاقته أدناه قبل الفحص"
                                                    else
                                                        "جاهز للحفظ والفحص المباشر",
                                                    color = theme.textSecondary,
                                                    fontFamily = NotoSansFont,
                                                    fontSize = 11.5.sp
                                                )
                                            }
                                        }
                                    } else if (normalizePastedKey(smartInput).length >= 8) {
                                        Text(
                                            text = "صيغة غير معروفة بعد — تأكد من لصق المفتاح كاملاً",
                                            color = theme.warning,
                                            fontFamily = CairoFont,
                                            fontSize = 12.sp
                                        )
                                    }

                                    ApplePrimaryButton(
                                        text = if (smartTesting) "يفحص الاتصال..." else "تعبئة وفحص تلقائياً",
                                        icon = Icons.Default.Speed,
                                        enabled = smartDetectedSvc != null,
                                        isLoading = smartTesting,
                                        onClick = { runSmartFillAndTest() }
                                    )

                                    val smartRes = smartDetectedId?.let { validationResults[it] }
                                    if (smartRes != null) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                if (smartRes.isValid) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                                contentDescription = null,
                                                tint = if (smartRes.isValid) theme.success else theme.destructive,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = smartRes.summary + (smartLatencyMs?.let { " • سرعة الاتصال: ${it}ms" } ?: ""),
                                                color = theme.textPrimary,
                                                fontFamily = CairoFont,
                                                fontSize = 12.sp,
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
                                onSave = { saveKey(service.prefsKey, currentKey) },
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
                                onTest = { runTestSingleKey(service) },
                                onOpenUrl = { openUrl(context, service.dashboardUrl) },
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
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            ReadinessScoreCard(readinessReport)
                        }

                        item {
                            JsonBackupCard(
                                onExport = { exportJsonLauncher.launch("qabas_api_keys.json") },
                                onImport = { importJsonLauncher.launch("*/*") },
                                onSmartPaste = { showSmartPasteDialog = true }
                            )
                        }

                        item {
                            SupabaseSyncCard(
                                context = context,
                                onSyncedFromCloud = {
                                    keysState = reloadKeysFromStorage()
                                    azureRegion = prefs.getString("azure_speech_region", "") ?: azureRegion
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSmartPasteDialog) {
        AlertDialog(
            onDismissRequest = { showSmartPasteDialog = false },
            containerColor = theme.cardSurface,
            shape = RoundedCornerShape(18.dp),
            title = {
                Text("اللصق الذكي لمفاتيح API", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = theme.textPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "الصق محتوى ملف .env أو كود JSON أو نصاً يحتوي على مفاتيح متعددة، وسيقوم التطبيق بفرزها وتخزينها تلقائياً:",
                        fontFamily = NotoSansFont,
                        fontSize = 12.5.sp,
                        color = theme.textSecondary,
                        lineHeight = 18.sp
                    )
                    OutlinedTextField(
                        value = smartPasteText,
                        onValueChange = { smartPasteText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        placeholder = {
                            Text(
                                "GEMINI_API_KEY=AIzaSy...\nGROQ_API_KEY=gsk_...",
                                fontSize = 12.sp,
                                color = theme.textSecondary.copy(alpha = 0.6f)
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val (count, map) = ApiKeysBackupManager.parseAndApplyImport(smartPasteText, prefs)
                        if (count > 0) {
                            keysState = keysState + map
                            map["azure_speech_region"]?.let { azureRegion = it }
                            Toast.makeText(context, "تم استخراج وتوزيع $count مفاتيح بنجاح", Toast.LENGTH_LONG).show()
                            showSmartPasteDialog = false
                            smartPasteText = ""
                        } else {
                            Toast.makeText(context, "لم يتم العثور على صيغ مفاتيح صالحة في النص المدخل", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("استخراج وتوزيع", fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSmartPasteDialog = false }) {
                    Text("إلغاء", color = theme.textSecondary, fontFamily = CairoFont)
                }
            }
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
    val theme = QabasTheme.current
    Surface(
        color = theme.secondarySurface,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(label, color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, color = color, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
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
    val theme = QabasTheme.current
    val configured = keyText.isNotBlank()

    val isLiveSuccess = validation?.isValid == true || (validation == null && configured && stat.lastStatusCode in 200..299 && stat.totalCalls > 0)
    val isLiveFailure = validation?.isValid == false || (validation == null && configured && stat.lastStatusCode != null && stat.lastStatusCode !in 200..299)

    val pillColor = when {
        isTesting -> theme.primary
        isLiveSuccess -> theme.success
        isLiveFailure -> theme.destructive
        !configured -> theme.warning
        stat.totalCalls == 0L -> theme.textSecondary
        else -> theme.success
    }

    val pillText = when {
        isTesting -> "جاري الفحص..."
        isLiveSuccess -> "متصل (${validation?.errorCode ?: stat.lastStatusCode ?: 200})"
        isLiveFailure -> "فشل (${validation?.errorCode ?: stat.lastStatusCode ?: "خطأ"})"
        !configured -> "غير مُعد"
        stat.totalCalls == 0L -> "جاهز للاختبار"
        else -> "متصل مستقر"
    }

    AppleCard(
        modifier = Modifier.testTag("api_key_card_${service.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(pillColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(service.icon, contentDescription = null, tint = pillColor, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(service.name, fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = theme.textPrimary, fontSize = 15.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = theme.secondarySurface,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                service.provider,
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        service.description,
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 11.5.sp,
                        maxLines = 2,
                        lineHeight = 16.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = pillColor.copy(alpha = 0.14f),
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
                            fontSize = 10.5.sp
                        )
                    }
                }
            }

            OutlinedTextField(
                value = keyText,
                onValueChange = onKeyChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(service.placeholder, color = theme.textSecondary.copy(alpha = 0.7f), fontSize = 12.sp) },
                visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onVisibilityToggle, modifier = Modifier.size(36.dp)) {
                            Icon(
                                if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "إظهار/إخفاء",
                                tint = theme.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (keyText.isNotEmpty()) {
                            IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "مسح", tint = theme.destructive, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            if (hasRegion) {
                OutlinedTextField(
                    value = regionValue,
                    onValueChange = onRegionChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("منطقة الخادم (Region)", fontFamily = NotoSansFont, fontSize = 11.sp) },
                    placeholder = { Text("مثال: eastus أو westeurope", color = theme.textSecondary.copy(alpha = 0.7f), fontSize = 12.sp) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onTest,
                    enabled = !isTesting && configured,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.primary,
                        contentColor = theme.onPrimary,
                        disabledContainerColor = theme.secondarySurface,
                        disabledContentColor = theme.textSecondary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .heightIn(min = 48.dp)
                        .testTag("test_key_button_${service.id}")
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = theme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("فحص", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                OutlinedButton(
                    onClick = onSave,
                    enabled = configured,
                    border = BorderStroke(1.dp, theme.success.copy(alpha = if (configured) 0.6f else 0.25f)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("save_key_button_${service.id}")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = if (configured) theme.success else theme.textSecondary, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("حفظ", color = if (configured) theme.success else theme.textSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onPaste,
                    border = BorderStroke(1.dp, theme.separator.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(0.95f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("لصق", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onOpenUrl,
                    border = BorderStroke(1.dp, theme.separator.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(0.95f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, tint = theme.primary, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("جلب", color = theme.primary, fontFamily = CairoFont, fontSize = 12.sp)
                }
            }

            KeyPoolSection(
                service = service,
                regionValue = regionValue,
                onPoolActiveChanged = onPoolActiveChanged
            )

            Surface(
                color = theme.secondarySurface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("الطلبات: ${stat.totalCalls}", color = theme.primary, fontFamily = NotoSansFont, fontSize = 11.sp)
                    Text("الناجحة: ${stat.successCalls}", color = theme.success, fontFamily = NotoSansFont, fontSize = 11.sp)
                    Text("الفاشلة: ${stat.failureCalls}", color = if (stat.failureCalls > 0) theme.destructive else theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.sp)
                    Text(
                        text = if (stat.lastLatencyMs > 0) "${stat.lastLatencyMs} ms" else "--",
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            val successReason = validation?.takeIf { it.isValid }?.explanation ?: stat.lastSuccessReason
            val failureReason = validation?.takeIf { !it.isValid }?.explanation ?: stat.lastFailureReason
            val suggestedFix = validation?.suggestedFix

            if (!successReason.isNullOrBlank()) {
                Surface(
                    color = theme.success.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("حالة الاتصال", color = theme.success, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(successReason, color = theme.textPrimary, fontFamily = NotoSansFont, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }
            }

            if (!failureReason.isNullOrBlank()) {
                Surface(
                    color = theme.destructive.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("سبب الفشل وتشخيص الخطأ", color = theme.destructive, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(failureReason, color = theme.textPrimary, fontFamily = NotoSansFont, fontSize = 11.sp, lineHeight = 16.sp)
                        if (!suggestedFix.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("الحل الموصى به: $suggestedFix", color = theme.warning, fontFamily = NotoSansFont, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }

            if (!configured && successReason.isNullOrBlank() && failureReason.isNullOrBlank()) {
                Surface(
                    color = theme.secondarySurface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = service.freeFallback,
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
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
    val theme = QabasTheme.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var newKeyInput by remember { mutableStateOf("") }
    var testingIdx by remember { mutableStateOf<Int?>(null) }
    var poolResults by remember { mutableStateOf<Map<Int, KeyValidationResult>>(emptyMap()) }
    var ranking by remember { mutableStateOf(false) }
    var rankSummary by remember { mutableStateOf<String?>(null) }

    val pool = remember(refreshTick) { KeyPoolManager.getKeys(service.prefsKey) }
    val activeIdx = remember(refreshTick) { KeyPoolManager.getActiveIndex(service.prefsKey) }

    fun syncParent() {
        onPoolActiveChanged(KeyPoolManager.getActiveKey(service.prefsKey))
        refreshTick++
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = theme.secondarySurface,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = if (pool.size > 1) theme.success else theme.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "المفاتيح الاحتياطية (${pool.size})",
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (pool.size > 1) {
                        Text(
                            text = "تبديل تلقائي مفعّل",
                            color = theme.success,
                            fontFamily = NotoSansFont,
                            fontSize = 10.5.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "طي" else "توسيع",
                        tint = theme.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (expanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newKeyInput,
                    onValueChange = { newKeyInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("الصق مفتاحاً احتياطياً...", color = theme.textSecondary, fontSize = 11.sp) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(10.dp)
                )
                Button(
                    onClick = {
                        val clean = normalizePastedKey(newKeyInput)
                        if (clean.isBlank()) return@Button
                        val detected = detectSingleKeyService(clean)
                        if (detected != null && detected != service.id) {
                            val other = ALL_SERVICES.find { it.id == detected }?.name ?: detected
                            Toast.makeText(context, "هذا المفتاح يتبع $other — أضفه في بطاقته", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        if (KeyPoolManager.addKey(service.prefsKey, clean)) {
                            newKeyInput = ""
                            syncParent()
                            Toast.makeText(context, "أُضيف المفتاح الاحتياطي", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "المفتاح موجود مسبقاً أو غير صالح", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("إضافة", fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (pool.size > 1) {
                AppleSecondaryButton(
                    text = if (ranking) "يفحص السرعة..." else "ترتيب حسب الأسرع المتصل",
                    icon = Icons.Default.Bolt,
                    tint = theme.success,
                    enabled = !ranking && testingIdx == null,
                    onClick = {
                        ranking = true
                        rankSummary = null
                        scope.launch {
                            try {
                                val provider = AgentConnectivityEngine.prefsKeyToProvider(service.prefsKey)
                                if (provider == DetectedProvider.NONE) {
                                    rankSummary = "الترتيب التلقائي يدعم Gemini/Groq/OpenAI/OpenRouter فقط"
                                } else {
                                    val (ok, n, best) = AgentConnectivityEngine.rankPoolBySpeed(service.prefsKey, provider)
                                    rankSummary = if (ok) "تم ترتيب $n مفاتيح — الأسرع المتصل أولاً (${best}ms)"
                                    else "لا يوجد مفتاح متصل — تحقق من المفاتيح والشبكة"
                                    syncParent()
                                }
                            } finally {
                                ranking = false
                            }
                        }
                    }
                )
                if (rankSummary != null) {
                    Text(rankSummary!!, color = theme.success, fontFamily = NotoSansFont, fontSize = 11.sp)
                }
            }

            if (pool.isEmpty()) {
                Text(
                    text = "لا توجد مفاتيح احتياطية بعد — أضف بدائل هنا للتجاوز التلقائي عند امتلاء الحصة.",
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            } else {
                pool.forEachIndexed { idx, key ->
                    val isActive = idx == activeIdx
                    val testing = testingIdx == idx
                    val res = poolResults[idx]
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isActive) theme.success.copy(alpha = 0.08f) else theme.secondarySurface,
                        border = if (isActive) BorderStroke(1.dp, theme.success.copy(alpha = 0.45f)) else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isActive) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isActive) theme.success else theme.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = maskKey(key),
                                    color = theme.textPrimary,
                                    fontFamily = RobotoMonoFont,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                if (!isActive) {
                                    TextButton(
                                        onClick = {
                                            if (KeyPoolManager.setActiveIndex(service.prefsKey, idx)) {
                                                syncParent()
                                                Toast.makeText(context, "تم تفعيل المفتاح البديل", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) {
                                        Text("تفعيل", fontSize = 11.sp, color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Text(
                                        "نشط",
                                        fontSize = 11.sp,
                                        color = theme.success,
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
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    if (testing) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = theme.primary, strokeWidth = 1.5.dp)
                                    } else {
                                        Icon(Icons.Default.FlashOn, contentDescription = "فحص", tint = theme.primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        if (KeyPoolManager.removeKeyAt(service.prefsKey, idx)) {
                                            poolResults = poolResults - idx
                                            syncParent()
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = theme.destructive, modifier = Modifier.size(16.dp))
                                }
                            }
                            if (res != null) {
                                Text(
                                    text = res.summary,
                                    color = if (res.isValid) theme.success else theme.destructive,
                                    fontFamily = NotoSansFont,
                                    fontSize = 10.5.sp,
                                    maxLines = 1
                                )
                            }
                            val lat = remember(refreshTick, key) { KeyLatencyTracker.get(service.prefsKey, key) }
                            if (lat != null) {
                                Text(
                                    text = if (lat.connected) "${lat.latencyMs}ms • متصل" else "آخر فحص: غير متصل",
                                    color = if (lat.connected) theme.primary else theme.warning,
                                    fontFamily = NotoSansFont,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
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
    val theme = QabasTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "سجل الطلبات الحية والتشخيص",
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "عرض مباشر لاستجابات الخوادم وأسباب النجاح والفشل",
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 11.5.sp
                )
            }
            Row {
                IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = theme.primary)
                }
                if (recentLogs.isNotEmpty()) {
                    IconButton(onClick = onClearLogs, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "مسح السجل", tint = theme.destructive)
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
                    Icon(Icons.Default.History, contentDescription = null, tint = theme.textSecondary.copy(alpha = 0.45f), modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "لا توجد طلبات مسجلة بعد",
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "اضغط على «فحص شامل لجميع المفاتيح الآن» أو افحص أي مفتاح لمشاهدة سجل الطلبات هنا.",
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center,
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
                        isSuccess -> theme.success
                        logItem.statusCode == 429 -> theme.warning
                        logItem.statusCode in 400..499 -> theme.destructive
                        logItem.statusCode in 500..599 -> Color(0xFFAF52DE)
                        else -> theme.destructive
                    }

                    val timeStr = remember(logItem.timestamp) {
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(logItem.timestamp))
                    }

                    AppleCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(color = statusColor.copy(alpha = 0.14f), shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            text = logItem.apiName,
                                            color = statusColor,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = timeStr, color = theme.textSecondary, fontFamily = RobotoMonoFont, fontSize = 11.sp)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(color = statusColor.copy(alpha = 0.14f), shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            text = "${logItem.statusCode ?: "Error"} ${logItem.statusText}",
                                            color = statusColor,
                                            fontFamily = RobotoMonoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.5.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${logItem.latencyMs} ms",
                                        color = theme.primary,
                                        fontFamily = RobotoMonoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    contentDescription = null,
                                    tint = statusColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = if (isSuccess) "سبب النجاح:" else "سبب الفشل:",
                                        color = statusColor,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                    Text(
                                        text = logItem.reason,
                                        color = theme.textPrimary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 11.5.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }

                            if (!logItem.diagnostic.isNullOrBlank()) {
                                Surface(
                                    color = theme.secondarySurface,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = theme.warning, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = logItem.diagnostic,
                                            color = theme.textSecondary,
                                            fontFamily = NotoSansFont,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
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
    val theme = QabasTheme.current
    val scoreColor = when {
        readinessReport.percentage >= 80 -> theme.success
        readinessReport.percentage >= 40 -> theme.primary
        else -> theme.warning
    }
    AppleCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("نسبة جاهزية الخدمات الحقيقية", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(readinessReport.statusTitle, color = theme.textPrimary, fontSize = 16.sp, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
                Surface(
                    color = scoreColor.copy(alpha = 0.14f),
                    shape = CircleShape,
                    border = BorderStroke(1.5.dp, scoreColor)
                ) {
                    Box(modifier = Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                        Text("${readinessReport.percentage}%", color = scoreColor, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }

            LinearProgressIndicator(
                progress = { readinessReport.percentage / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = scoreColor,
                trackColor = theme.secondarySurface
            )

            Text(readinessReport.statusDescription, color = theme.textSecondary, fontSize = 12.sp, fontFamily = NotoSansFont, lineHeight = 17.sp)

            HorizontalDivider(thickness = 0.5.dp, color = theme.separator.copy(alpha = 0.45f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                readinessReport.services.forEach { srv ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (srv.isConfigured) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (srv.isConfigured) theme.success else theme.textSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${srv.name} (+${srv.weight}%)",
                                color = if (srv.isConfigured) theme.textPrimary else theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 12.sp,
                                fontWeight = if (srv.isConfigured) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                        Text(
                            text = if (srv.isConfigured) "مفعّل" else "غير معد",
                            color = if (srv.isConfigured) theme.success else theme.warning,
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
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
    val theme = QabasTheme.current
    AppleCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Backup, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("النسخ الاحتياطي السريع (JSON)", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("تصدير واستيراد المفاتيح لنقلها بين أجهزتك.", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.5.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ApplePrimaryButton(
                    text = "تصدير JSON",
                    icon = Icons.Default.FileDownload,
                    modifier = Modifier.weight(1f),
                    onClick = onExport
                )
                AppleSecondaryButton(
                    text = "استيراد JSON",
                    icon = Icons.Default.FileUpload,
                    modifier = Modifier.weight(1f),
                    onClick = onImport
                )
            }

            AppleSecondaryButton(
                text = "اللصق الذكي والفرز التلقائي",
                icon = Icons.Default.AutoFixHigh,
                onClick = onSmartPaste
            )
        }
    }
}

@Composable
private fun SupabaseSyncCard(
    context: Context,
    onSyncedFromCloud: () -> Unit
) {
    val theme = QabasTheme.current
    var syncState by remember { mutableIntStateOf(0) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var syncSuccess by remember { mutableStateOf(true) }

    AppleCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = theme.accent, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("المزامنة السحابية (Supabase)", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("مزامنة المفاتيح المشفرة عبر حسابك.", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.5.sp)
                }
            }

            if (syncMessage != null) {
                Surface(
                    color = (if (syncSuccess) theme.success else theme.destructive).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = syncMessage.orEmpty(),
                        color = if (syncSuccess) theme.success else theme.destructive,
                        fontFamily = NotoSansFont,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (!SupabaseConfig.isConfigured) {
                AppleKeyRequiredBanner(
                    serviceName = "سحابة Supabase",
                    reason = "أضف SUPABASE_URL و SUPABASE_ANON_KEY في إعدادات البناء لتفعيل المزامنة السحابية."
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AppleSecondaryButton(
                        text = if (syncState == 1) "يرفع..." else "رفع",
                        icon = Icons.Default.CloudUpload,
                        enabled = syncState == 0,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            syncState = 1; syncMessage = null
                            KeySyncService.pushToCloud(context) { ok, msg ->
                                syncSuccess = ok; syncMessage = msg; syncState = 0
                            }
                        }
                    )
                    AppleSecondaryButton(
                        text = if (syncState == 2) "يسحب..." else "سحب",
                        icon = Icons.Default.CloudDownload,
                        enabled = syncState == 0,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            syncState = 2; syncMessage = null
                            KeySyncService.pullFromCloud(context) { ok, msg ->
                                syncSuccess = ok; syncMessage = msg; syncState = 0
                                if (ok) onSyncedFromCloud()
                            }
                        }
                    )
                    ApplePrimaryButton(
                        text = "مزامنة",
                        icon = Icons.Default.SyncAlt,
                        enabled = syncState == 0,
                        isLoading = syncState == 3,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            syncState = 3; syncMessage = null
                            KeySyncService.syncBidirectional(context) { ok, msg ->
                                syncSuccess = ok; syncMessage = msg; syncState = 0
                                if (ok) onSyncedFromCloud()
                            }
                        }
                    )
                }
            }
        }
    }
}
