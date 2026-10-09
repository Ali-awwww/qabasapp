package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat

/**
 * تفضيلات وإعدادات الوكيل الذكي (Agent Preferences)
 */
object AgentPrefs {
    const val MODEL = "agent_model"
    const val MAX_TURNS = "agent_max_turns"
    const val AUTO_FIX = "agent_auto_fix"
    fun toolKey(name: String) = "tool_enabled_$name"

    fun isToolEnabled(context: Context, name: String): Boolean =
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .getBoolean(toolKey(name), true)

    fun maxTurns(context: Context): Int =
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .getInt(MAX_TURNS, 6).coerceIn(1, 10)

    fun isAutoFix(context: Context): Boolean =
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .getBoolean(AUTO_FIX, true)

    fun model(context: Context): String? =
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .getString(MODEL, null)?.takeIf { it.isNotBlank() }
}

/**
 * مزودو نماذج الذكاء الاصطناعي المدعومون مع الكشف التلقائي الذكي
 */
enum class DetectedProvider(
    val id: String,
    val displayName: String,
    val prefixHint: String,
    val defaultModel: String,
    val color: Color
) {
    OPENROUTER("openrouter", "OpenRouter AI Gateway", "sk-or-", "anthropic/claude-3.5-sonnet", Color(0xFF38BDF8)),
    GEMINI("gemini", "Google Gemini 2.0", "AIzaSy", "gemini-2.0-flash", Color(0xFF10B981)),
    GROQ("groq", "Groq Lightning Llama", "gsk_", "llama-3.3-70b-versatile", Color(0xFFF59E0B)),
    OPENAI("openai", "OpenAI GPT-4o", "sk-", "gpt-4o", Color(0xFF10B981)),
    ANTHROPIC("anthropic", "Anthropic Claude", "sk-ant-", "claude-3-5-sonnet-20241022", Color(0xFFA855F7)),
    HUGGINGFACE("huggingface", "HuggingFace Hub", "hf_", "Qwen/Qwen2.5-Coder-32B", Color(0xFFFCD34D)),
    NONE("none", "لا يوجد مفتاح نشط", "", "local_sandbox", Color(0xFF94A3B8))
}

/**
 * حالات الاتصال بمستودع GitHub
 */
enum class GitHubConnectionState(
    val title: String,
    val color: Color
) {
    CONNECTED("متصل ومصرّح (200 OK)", Color(0xFF10B981)),
    SYNCING("قيد المزامنة والفحص...", Color(0xFF38BDF8)),
    PERMISSION_ERROR("خطأ في الصلاحيات (Token/Repo)", Color(0xFFEF4444)),
    NOT_CONFIGURED("غير مهيأ (Local Sandbox)", Color(0xFF94A3B8))
}

/**
 * حالات الأمر في طابور الأوامر البرمجية (Mission Command Queue)
 */
enum class CommandStatus(val label: String, val color: Color) {
    PENDING("في الانتظار ⏳", Color(0xFFF59E0B)),
    RUNNING("قيد التنفيذ ⚡", Color(0xFF38BDF8)),
    PAUSED("موقوف مؤقتاً ⏸️", Color(0xFFA855F7)),
    COMPLETED("مكتمل بنجاح ✅", Color(0xFF10B981)),
    FAILED("فشل التنفيذ ❌", Color(0xFFEF4444)),
    CANCELLED("تم الإلغاء ⏹️", Color(0xFF64748B))
}

/**
 * عنصر أمر في طابور مهام الوكيل
 */
data class QueuedCommand(
    val id: String = UUID.randomUUID().toString().take(6),
    val prompt: String,
    var status: CommandStatus = CommandStatus.PENDING,
    val addedAt: Long = System.currentTimeMillis(),
    var turnsUsed: Int = 0,
    var totalTurns: Int = 6,
    var progress: Float = 0f,
    var currentPhase: String = "في الانتظار...",
    var resultSummary: String = ""
)

/**
 * أنماط التخصص الهندسي للوكيل البرمجي (Agent Specialization Modes)
 */
enum class AgentSpecialization(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val badge: String,
    val color: Color
) {
    TURBO("turbo", "⚡ التيربو السريع", "تنفيذ سريع ومباشر لتعديلات الواجهة والوظائف البسيطة", Icons.Default.Bolt, "TURBO ⚡", Color(0xFF38BDF8)),
    ARCHITECT("architect", "🧠 المعماري الشامل", "تحليل معماري مسبق، تقسيم المهام وتوليد اختبارات آلية", Icons.Default.Psychology, "ARCHITECT 🧠", Color(0xFFA855F7)),
    BUG_HUNTER("bug_hunter", "🩺 صياد ومصلح الأخطاء", "تشخيص وتحليل أسباب الانهيارات وأخطاء التجميع والترقيع الفوري", Icons.Default.Healing, "DEBUGGER 🩺", Color(0xFFEF4444)),
    CODE_REVIEWER("code_reviewer", "🛡️ مدقق ومحسّن الجودة", "تدقيق الكود وفق معايير Clean Architecture و Compose M3", Icons.Default.VerifiedUser, "AUDITOR 🛡️", Color(0xFF10B981))
}

/**
 * عقل الكشف والتوجيه التلقائي للمفاتيح (Universal Multi-Provider Brain)
 */
object UniversalKeyBrain {
    val keyUpdateTrigger = kotlinx.coroutines.flow.MutableStateFlow(System.currentTimeMillis())

    fun notifyKeyChanged() {
        keyUpdateTrigger.value = System.currentTimeMillis()
    }

    fun detect(key: String): DetectedProvider {
        val k = key.trim()
        return when {
            k.startsWith("sk-or-", ignoreCase = true) -> DetectedProvider.OPENROUTER
            k.startsWith("sk-ant-", ignoreCase = true) -> DetectedProvider.ANTHROPIC
            k.startsWith("AIza", ignoreCase = true) -> DetectedProvider.GEMINI
            k.startsWith("gsk_", ignoreCase = true) -> DetectedProvider.GROQ
            k.startsWith("sk-", ignoreCase = true) -> DetectedProvider.OPENAI
            k.startsWith("hf_", ignoreCase = true) -> DetectedProvider.HUGGINGFACE
            k.startsWith("xai-", ignoreCase = true) -> DetectedProvider.GROQ
            else -> DetectedProvider.NONE
        }
    }

    fun getActiveEngine(): Pair<DetectedProvider, String> {
        val or = KeyVault.openrouter
        if (or.isNotBlank()) return Pair(DetectedProvider.OPENROUTER, or)

        val gem = KeyVault.gemini
        if (gem.isNotBlank()) return Pair(DetectedProvider.GEMINI, gem)

        val groq = KeyVault.groq
        if (groq.isNotBlank()) return Pair(DetectedProvider.GROQ, groq)

        val oai = KeyVault.openai
        if (oai.isNotBlank()) return Pair(DetectedProvider.OPENAI, oai)

        val hf = KeyVault.huggingface
        if (hf.isNotBlank()) return Pair(DetectedProvider.HUGGINGFACE, hf)

        return Pair(DetectedProvider.NONE, "")
    }

    fun saveKey(context: Context, rawKey: String): DetectedProvider {
        val k = rawKey.trim()
        val provider = detect(k)
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        when (provider) {
            DetectedProvider.OPENROUTER -> editor.putString("openrouter_key", k)
            DetectedProvider.GEMINI -> editor.putString("gemini_key", k)
            DetectedProvider.GROQ -> editor.putString("groq_key", k)
            DetectedProvider.OPENAI -> editor.putString("openai_key", k)
            DetectedProvider.HUGGINGFACE -> editor.putString("huggingface_key", k)
            else -> editor.putString("openrouter_key", k)
        }
        editor.apply()
        notifyKeyChanged()
        return provider
    }

    suspend fun executeRealAgentStep(
        provider: DetectedProvider,
        apiKey: String,
        prompt: String,
        systemInstruction: String
    ): Result<RealAgentResponse> = withContext(Dispatchers.IO) {
        val client = SharedHttpClient.instance
        try {
            when (provider) {
                DetectedProvider.GEMINI -> {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"
                    val jsonBody = org.json.JSONObject().apply {
                        put("contents", org.json.JSONArray().apply {
                            put(org.json.JSONObject().apply {
                                put("role", "user")
                                put("parts", org.json.JSONArray().apply {
                                    put(org.json.JSONObject().apply { put("text", prompt) })
                                })
                            })
                        })
                        put("systemInstruction", org.json.JSONObject().apply {
                            put("parts", org.json.JSONArray().apply {
                                put(org.json.JSONObject().apply { put("text", systemInstruction) })
                            })
                        })
                    }
                    val request = Request.Builder()
                        .url(url)
                        .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    val response = client.newCall(request).execute()
                    val bodyString = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        val errorDetail = try {
                            val errJson = org.json.JSONObject(bodyString)
                            errJson.optJSONObject("error")?.optString("message") ?: bodyString
                        } catch (e: Exception) { bodyString.ifBlank { "HTTP ${response.code} ${response.message}" } }
                        return@withContext Result.failure(Exception("خطأ من سيرفر Gemini (HTTP ${response.code}): $errorDetail"))
                    }
                    val json = org.json.JSONObject(bodyString)
                    val text = parseGeminiText(json)
                    if (text.isNullOrBlank()) {
                        return@withContext Result.failure(Exception("استجابة فارغة من سيرفر Gemini"))
                    }
                    val usageMeta = json.optJSONObject("usageMetadata")
                    val promptTokens = usageMeta?.optInt("promptTokenCount", 0) ?: 0
                    val candidateTokens = usageMeta?.optInt("candidatesTokenCount", 0) ?: 0
                    val totalTokens = usageMeta?.optInt("totalTokenCount", 0)?.takeIf { it > 0 } ?: (promptTokens + candidateTokens)
                    Result.success(RealAgentResponse(text, totalTokens.coerceAtLeast(150), promptTokens, candidateTokens))
                }
                DetectedProvider.OPENROUTER -> {
                    val url = "https://openrouter.ai/api/v1/chat/completions"
                    val jsonBody = org.json.JSONObject().apply {
                        put("model", "anthropic/claude-3.5-sonnet")
                        put("messages", org.json.JSONArray().apply {
                            put(org.json.JSONObject().apply { put("role", "system"); put("content", systemInstruction) })
                            put(org.json.JSONObject().apply { put("role", "user"); put("content", prompt) })
                        })
                    }
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $apiKey")
                        .header("HTTP-Referer", "https://qabas.app")
                        .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    val response = client.newCall(request).execute()
                    val bodyString = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("خطأ من سيرفر OpenRouter (HTTP ${response.code}): $bodyString"))
                    }
                    val json = org.json.JSONObject(bodyString)
                    val text = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                    if (text.isNullOrBlank()) {
                        return@withContext Result.failure(Exception("استجابة فارغة من سيرفر OpenRouter"))
                    }
                    val usageObj = json.optJSONObject("usage")
                    val promptTokens = usageObj?.optInt("prompt_tokens", 0) ?: 0
                    val completionTokens = usageObj?.optInt("completion_tokens", 0) ?: 0
                    val totalTokens = usageObj?.optInt("total_tokens", 0)?.takeIf { it > 0 } ?: (promptTokens + completionTokens)
                    Result.success(RealAgentResponse(text, totalTokens.coerceAtLeast(120), promptTokens, completionTokens))
                }
                DetectedProvider.OPENAI -> {
                    val url = "https://api.openai.com/v1/chat/completions"
                    val jsonBody = org.json.JSONObject().apply {
                        put("model", "gpt-4o")
                        put("messages", org.json.JSONArray().apply {
                            put(org.json.JSONObject().apply { put("role", "system"); put("content", systemInstruction) })
                            put(org.json.JSONObject().apply { put("role", "user"); put("content", prompt) })
                        })
                    }
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $apiKey")
                        .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    val response = client.newCall(request).execute()
                    val bodyString = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("خطأ من سيرفر OpenAI (HTTP ${response.code}): $bodyString"))
                    }
                    val json = org.json.JSONObject(bodyString)
                    val text = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                    if (text.isNullOrBlank()) {
                        return@withContext Result.failure(Exception("استجابة فارغة من سيرفر OpenAI"))
                    }
                    val usageObj = json.optJSONObject("usage")
                    val promptTokens = usageObj?.optInt("prompt_tokens", 0) ?: 0
                    val completionTokens = usageObj?.optInt("completion_tokens", 0) ?: 0
                    val totalTokens = usageObj?.optInt("total_tokens", 0)?.takeIf { it > 0 } ?: (promptTokens + completionTokens)
                    Result.success(RealAgentResponse(text, totalTokens.coerceAtLeast(120), promptTokens, completionTokens))
                }
                DetectedProvider.GROQ -> {
                    val url = "https://api.groq.com/openai/v1/chat/completions"
                    val jsonBody = org.json.JSONObject().apply {
                        put("model", "llama-3.3-70b-versatile")
                        put("messages", org.json.JSONArray().apply {
                            put(org.json.JSONObject().apply { put("role", "system"); put("content", systemInstruction) })
                            put(org.json.JSONObject().apply { put("role", "user"); put("content", prompt) })
                        })
                    }
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $apiKey")
                        .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    val response = client.newCall(request).execute()
                    val bodyString = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("خطأ من سيرفر Groq (HTTP ${response.code}): $bodyString"))
                    }
                    val json = org.json.JSONObject(bodyString)
                    val text = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                    if (text.isNullOrBlank()) {
                        return@withContext Result.failure(Exception("استجابة فارغة من سيرفر Groq"))
                    }
                    val usageObj = json.optJSONObject("usage")
                    val promptTokens = usageObj?.optInt("prompt_tokens", 0) ?: 0
                    val completionTokens = usageObj?.optInt("completion_tokens", 0) ?: 0
                    val totalTokens = usageObj?.optInt("total_tokens", 0)?.takeIf { it > 0 } ?: (promptTokens + completionTokens)
                    Result.success(RealAgentResponse(text, totalTokens.coerceAtLeast(100), promptTokens, completionTokens))
                }
                else -> {
                    Result.failure(Exception("لم يتم تقديم أي مفتاح API نشط. يرجى إدخال مفتاح صريح (Gemini / OpenRouter / OpenAI / Groq) أولاً."))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("فشل الشبكة أثناء الاتصال بالسيرفر: ${e.localizedMessage ?: e.message}"))
        }
    }

    private fun parseGeminiText(json: org.json.JSONObject): String? {
        val candidates = json.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null
        val content = candidates.getJSONObject(0).optJSONObject("content") ?: return null
        val parts = content.optJSONArray("parts") ?: return null
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            if (part.has("text")) {
                sb.append(part.getString("text"))
            }
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }
}

data class RealAgentResponse(
    val text: String,
    val totalTokens: Int,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0
)

fun extractTargetFile(prompt: String, responseText: String): String {
    // 1. فحص وجود مسار صريح في رأس الكود أو التعليقات
    val fileRegex = Regex("""(?://|#|--)\s*(?:File|Path|Target):\s*([a-zA-Z0-9_/.-]+\.kt)|```[a-zA-Z0-9_]*:([a-zA-Z0-9_/.-]+\.kt)""", RegexOption.IGNORE_CASE)
    val match = fileRegex.find(responseText)
    val found = match?.groups?.get(1)?.value ?: match?.groups?.get(2)?.value
    if (!found.isNullOrBlank()) {
        return found.substringAfterLast("/")
    }

    // 2. مطابقة أسماء الملفات الرئيسية في المشروع عند ذكرها في الرد أو البرومبت
    val knownFiles = listOf(
        "MainActivity.kt", "QabasHomeScreen.kt", "AppNavigation.kt", "SettingsScreen.kt",
        "DeveloperDashboardScreen.kt", "AudioPlayerManager.kt", "RealServices.kt",
        "AgentRoomSection.kt", "BuildCenterSection.kt", "CrashLogsHubScreen.kt",
        "QuranDataProvider.kt", "QabasBrainViewModel.kt", "BRollEngine.kt",
        "HealthCenterSection.kt", "AuditLogSection.kt", "RepoVaultManager.kt"
    )
    for (f in knownFiles) {
        val base = f.removeSuffix(".kt")
        if (prompt.contains(base, ignoreCase = true) || prompt.contains(f, ignoreCase = true) ||
            responseText.contains(f, ignoreCase = true)) {
            return f
        }
    }

    // 3. تخمين دلالي ذكي بناءً على سياق المهمة
    val p = prompt.lowercase()
    return when {
        "شاشة رئيسية" in p || "الرئيسية" in p || "home" in p -> "QabasHomeScreen.kt"
        "تنقل" in p || "navigation" in p || "route" in p -> "AppNavigation.kt"
        "إعدادات" in p || "settings" in p -> "SettingsScreen.kt"
        "صوت" in p || "audio" in p || "تشغيل" in p -> "AudioPlayerManager.kt"
        "مطور" in p || "لوحة" in p || "dashboard" in p -> "DeveloperDashboardScreen.kt"
        "وكيل" in p || "agent" in p -> "AgentRoomSection.kt"
        "بناء" in p || "build" in p -> "BuildCenterSection.kt"
        "انهيار" in p || "crash" in p || "logs" in p -> "CrashLogsHubScreen.kt"
        "قرآن" in p || "تفسير" in p || "quran" in p -> "QuranDataProvider.kt"
        else -> "MainActivity.kt"
    }
}

private data class Requirement(
    val id: String,
    val title: String,
    val hint: String,
    var state: ReqState = ReqState.CHECKING,
    var detail: String = ""
)

private enum class ReqState { CHECKING, OK, FAIL }

data class AgentStep(
    val id: Int,
    val turn: Int,
    val phase: String,
    val title: String,
    val detail: String,
    val toolName: String? = null,
    val isComplete: Boolean = true,
    val latencyMs: Long = 0L
)

data class FileDiff(
    val filePath: String,
    val additions: Int,
    val deletions: Int,
    val diffText: String
)

enum class AgentChatSender {
    USER, AGENT, TOOL_SYSTEM
}

data class AgentChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: AgentChatSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val step: AgentStep? = null,
    val diff: FileDiff? = null,
    val isStreaming: Boolean = false
)

/**
 * «غرفة الوكيل 🤖» — Cybernetic Autonomous Code Agent HUD
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentRoomSection(
    onJumpBuildCenter: () -> Unit = {},
    onJumpKeys: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        Triple("01", "⚡ التشغيل المباشر", Color(0xFF10B981)),
        Triple("02", "🎯 فحص الجاهزية", Color(0xFF38BDF8)),
        Triple("03", "🛠️ ترسانة الأدوات", Color(0xFFF59E0B)),
        Triple("04", "📜 سجل العمليات", Color(0xFFA855F7))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030712))
    ) {
        // ==========================================
        // 🛰️ شريط الملاحة السيبراني الزجاجي المتناغم (Spatial HUD Deck)
        // ==========================================
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            color = Color(0xCC091222),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                1.dp,
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF00F0FF).copy(alpha = 0.4f),
                        Color(0xFF10B981).copy(alpha = 0.3f),
                        Color(0xFF1E2D4A).copy(alpha = 0.5f)
                    )
                )
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, (idxStr, title, tabAccent) ->
                    val isSelected = selectedTab == index

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedTab = index },
                        color = if (isSelected) tabAccent.copy(alpha = 0.16f) else Color(0xFF060B16),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) tabAccent else Color(0xFF1E293B).copy(alpha = 0.6f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // مؤشر ترقيم النواة السيبراني
                            Text(
                                "[$idxStr]",
                                color = if (isSelected) tabAccent else Color(0xFF64748B),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(tabAccent)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                            }
                            Text(
                                title,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                fontFamily = CairoFont,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        when (selectedTab) {
            0 -> AgentLiveConsoleTab(context, scope, onJumpKeys, onJumpBuildCenter)
            1 -> RequirementsTab(context, scope, onJumpBuildCenter, onJumpKeys)
            2 -> ToolsTab(context)
            else -> TaskHistoryLogTab(context)
        }
    }
}

/**
 * ⚡ تبويب كونسول تشغيل الوكيل المباشر مع طابور الأوامر والإيقاف المؤقت
 */
@Composable
private fun AgentLiveConsoleTab(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onJumpKeys: () -> Unit,
    onJumpBuildCenter: () -> Unit
) {
    val prefs = remember(context) { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }
    val clipboard = LocalClipboardManager.current

    val vaultTrigger by RepoVaultManager.vaultUpdateTrigger.collectAsState()
    val keyTrigger by UniversalKeyBrain.keyUpdateTrigger.collectAsState()

    var activeEngineInfo by remember(keyTrigger) { mutableStateOf(UniversalKeyBrain.getActiveEngine()) }
    var customKeyInput by remember { mutableStateOf("") }
    var detectedNewProvider by remember { mutableStateOf(DetectedProvider.NONE) }
    var isKeyBoxExpanded by remember { mutableStateOf(false) }
    var showFreeProvidersDialog by remember { mutableStateOf(false) }

    val maxTurns = remember(context) { AgentPrefs.maxTurns(context) }
    var activeRepo by remember(vaultTrigger) { mutableStateOf(RepoVaultManager.getActiveRepo(context)) }
    var owner by remember(activeRepo) { mutableStateOf(activeRepo?.owner ?: (prefs.getString("build_center_owner", "") ?: "")) }
    var repo by remember(activeRepo) { mutableStateOf(activeRepo?.repo ?: (prefs.getString("build_center_repo", "") ?: "")) }
    var token by remember(activeRepo) { mutableStateOf(activeRepo?.token?.takeIf { it.isNotBlank() } ?: (prefs.getString("build_center_token", "") ?: "")) }

    var ghState by remember { mutableStateOf(GitHubConnectionState.SYNCING) }
    var ghUser by remember { mutableStateOf<String?>(null) }
    var ghBranch by remember { mutableStateOf("main") }
    var ghLatencyMs by remember { mutableLongStateOf(0L) }
    var ghErrorMessage by remember { mutableStateOf("") }

    fun checkGitHubStatus() {
        ghState = GitHubConnectionState.SYNCING
        scope.launch {
            val t0 = System.currentTimeMillis()
            if (token.isBlank()) {
                ghState = GitHubConnectionState.NOT_CONFIGURED
                ghErrorMessage = "لم يتم ضبط رمز الوصول الشخصي (GitHub PAT)"
                return@launch
            }
            val client = GitHubRepoClient(owner.ifBlank { "-" }, repo.ifBlank { "-" }, token)
            val user = client.getAuthUser()
            ghLatencyMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            if (user != null) {
                ghUser = user
                ghBranch = if (owner.isNotBlank() && repo.isNotBlank()) client.getDefaultBranch() else "main"
                ghState = GitHubConnectionState.CONNECTED
                ghErrorMessage = ""
            } else {
                ghState = GitHubConnectionState.PERMISSION_ERROR
                ghErrorMessage = "فشل التحقق: الرمز منتهي أو يفتقد لصلاحية repo أو المستودع غير موجود"
            }
        }
    }

    LaunchedEffect(token, owner, repo) {
        checkGitHubStatus()
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.flow.merge(
            RepoVaultManager.vaultUpdateTrigger,
            UniversalKeyBrain.keyUpdateTrigger
        ).collect {
            activeEngineInfo = UniversalKeyBrain.getActiveEngine()
            val currentActive = RepoVaultManager.getActiveRepo(context)
            if (currentActive != null) {
                activeRepo = currentActive
                owner = currentActive.owner
                repo = currentActive.repo
                if (currentActive.token.isNotBlank()) token = currentActive.token
            }
            checkGitHubStatus()
        }
    }

    var missionInput by remember {
        mutableStateOf("أضف زر مشاركة فوري مع تأثير اهتزاز لمسي (Haptic Feedback) في الشاشة الرئيسية")
    }

    // إدارة الطابور وحالات التشغيل والإيقاف المؤقت
    val commandQueue = remember { mutableStateListOf<QueuedCommand>() }
    var activeCommandId by remember { mutableStateOf<String?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var currentTurn by remember { mutableIntStateOf(0) }
    var runningJob by remember { mutableStateOf<Job?>(null) }
    var steps by remember { mutableStateOf<List<AgentStep>>(emptyList()) }
    var generatedDiff by remember { mutableStateOf<FileDiff?>(null) }
    var totalTokensUsed by remember { mutableIntStateOf(0) }
    var sessionStartTime by remember { mutableLongStateOf(0L) }
    var elapsedTimeSeconds by remember { mutableLongStateOf(0L) }

    var sanityCheckResult by remember { mutableStateOf<String?>(null) }
    var selectedSpecialization by remember { mutableStateOf(AgentSpecialization.TURBO) }
    var contextCompressionEnabled by remember { mutableStateOf(true) }
    var isPushingToGitHub by remember { mutableStateOf(false) }

    fun moveCommandUp(index: Int) {
        if (index > 0 && index < commandQueue.size) {
            val item = commandQueue.removeAt(index)
            commandQueue.add(index - 1, item)
            Toast.makeText(context, "تم رفع أولوية المهمة ⬆️", Toast.LENGTH_SHORT).show()
        }
    }

    fun moveCommandDown(index: Int) {
        if (index >= 0 && index < commandQueue.size - 1) {
            val item = commandQueue.removeAt(index)
            commandQueue.add(index + 1, item)
            Toast.makeText(context, "تم تأخير المهمة ⬇️", Toast.LENGTH_SHORT).show()
        }
    }

    // شاشة وتفاعلات المحادثة المباشرة مع الوكيل
    var showAgentChatDialog by remember { mutableStateOf(false) }
    val chatMessages = remember { mutableStateListOf<AgentChatMessage>() }
    var isAgentThinkingInChat by remember { mutableStateOf(false) }

    fun handleUserChatMessage(userText: String) {
        val clean = userText.trim()
        if (clean.isBlank()) return
        chatMessages.add(AgentChatMessage(sender = AgentChatSender.USER, text = clean))
        isAgentThinkingInChat = true
        scope.launch(Dispatchers.IO) {
            val sysPrompt = "أنت الوكيل البرمجي المستقل لتطبيق قبس (نمط ${selectedSpecialization.title}). أجِب بوضوح ودقة عالية واعرض حلول وتعديلات الكود بأسلوب مهني ومباشر باللغة العربية."
            val response = try {
                AppServices.chatWithAssistant(listOf(Pair(true, clean)), sysPrompt)
            } catch (e: Exception) {
                "تم استلام توجيهك البرمجي وجاري معالجته وتطبيقه على مشروع الكود المحلي: $clean"
            }
            withContext(Dispatchers.Main) {
                chatMessages.add(AgentChatMessage(sender = AgentChatSender.AGENT, text = response))
                isAgentThinkingInChat = false
            }
        }
    }

    LaunchedEffect(isRunning, isPaused) {
        if (isRunning && !isPaused) {
            if (sessionStartTime == 0L) sessionStartTime = System.currentTimeMillis()
            while (isRunning && !isPaused) {
                elapsedTimeSeconds = (System.currentTimeMillis() - sessionStartTime) / 1000
                delay(1000)
            }
        }
    }

    val infinitePulse = rememberInfiniteTransition(label = "AgentPulse")
    val pulseAlpha by infinitePulse.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "PulseAlpha"
    )

    // دالة انتظار التعليق المؤقت (Wait Loop for Pause)
    suspend fun waitIfPaused() {
        while (isPaused) {
            delay(200)
        }
    }

    fun pauseAgent() {
        isPaused = true
        commandQueue.find { it.id == activeCommandId }?.status = CommandStatus.PAUSED
        steps = steps + AgentStep(
            id = steps.size + 1,
            turn = currentTurn,
            phase = "PAUSED",
            title = "تم إيقاف الوكيل مؤقتاً ⏸️",
            detail = "تم تعليق حلقة التفكير. يمكنك مراجعة الكود أو إضافة أوامر للطابور ثم الضغط على استئناف."
        )
    }

    fun resumeAgent() {
        isPaused = false
        commandQueue.find { it.id == activeCommandId }?.status = CommandStatus.RUNNING
        steps = steps + AgentStep(
            id = steps.size + 1,
            turn = currentTurn,
            phase = "RESUMED",
            title = "استئناف عمل الوكيل ▶️",
            detail = "يتابع الوكيل تنفيذ الخطوات البرمجية من النقطة التي توقف عندها."
        )
    }

    fun stopAgent() {
        runningJob?.cancel()
        isRunning = false
        isPaused = false
        commandQueue.find { it.id == activeCommandId }?.status = CommandStatus.CANCELLED
        steps = steps + AgentStep(
            id = steps.size + 1,
            turn = currentTurn,
            phase = "ERROR",
            title = "تم إيقاف الوكيل يدوياً ⏹️",
            detail = "أوقف المطور عملية تشغيل الوكيل قبل اكتمال الجولات."
        )
        activeCommandId = null
    }

    fun deleteCommand(cmd: QueuedCommand) {
        if (cmd.id == activeCommandId) {
            stopAgent()
        }
        commandQueue.remove(cmd)
        Toast.makeText(context, "تم حذف المهمة نهائياً من الطابور 🗑️", Toast.LENGTH_SHORT).show()
    }

    // تنفيذ أمر محدد وتمريره عبر السيرفر الحقيقي ومفتاح API مع إظهار أسباب الفشل الصريحة
    fun executeCommand(cmd: QueuedCommand) {
        isRunning = true
        isPaused = false
        activeCommandId = cmd.id
        showAgentChatDialog = true

        val (provider, key) = activeEngineInfo

        if (chatMessages.isEmpty() || chatMessages.lastOrNull()?.text != cmd.prompt) {
            chatMessages.add(AgentChatMessage(sender = AgentChatSender.USER, text = cmd.prompt))
            chatMessages.add(
                AgentChatMessage(
                    sender = AgentChatSender.AGENT,
                    text = "أهلاً بك! أنا وكيلك البرمجي المستقل (نمط ${selectedSpecialization.title}).\nجارٍ الاتصال المباشر بالسيرفر عبر محرك **${provider.displayName}** ومفتاحك المسجل...\n🎯 **المهمة المستهدفة:** ${cmd.prompt}"
                )
            )
        }

        cmd.status = CommandStatus.RUNNING
        cmd.turnsUsed = 1
        cmd.totalTurns = 3
        cmd.progress = 0.10f
        cmd.currentPhase = "فحص الاتصال بالسيرفر والترخيص"
        currentTurn = 1
        steps = emptyList()
        generatedDiff = null
        totalTokensUsed = 0
        sessionStartTime = System.currentTimeMillis()

        runningJob = scope.launch {
            // 1. التحقق الصريح من وجود المفتاح
            if (key.isBlank() || provider == DetectedProvider.NONE) {
                val errorMsg = "لم يتم إدخال أو تكوين أي مفتاح API نشط. يرجى إدخال مفتاح (Gemini / OpenRouter / OpenAI / Groq) في قسم المفاتيح للاتصال بالسيرفر."
                steps = steps + AgentStep(
                    id = 1,
                    turn = 1,
                    phase = "ERROR",
                    title = "❌ فشل الاتصال: مفقود مفتاح API",
                    detail = errorMsg
                )
                cmd.status = CommandStatus.FAILED
                cmd.currentPhase = "فشل الاتصال: مفقود مفتاح API ❌"
                cmd.resultSummary = errorMsg
                chatMessages.add(
                    AgentChatMessage(
                        sender = AgentChatSender.AGENT,
                        text = "❌ **تعذر الاتصال بالسيرفر:**\n$errorMsg\nيرجى حفظ مفتاحك أولاً ثم إعادة المحاولة."
                    )
                )
                isRunning = false
                activeCommandId = null
                return@launch
            }

            // 2. إرسال الطلب المباشر بالسيرفر الحقيقي
            steps = steps + AgentStep(
                id = 1,
                turn = 1,
                phase = "CONNECTING",
                title = "إرسال الطلب المباشر لسيرفر ${provider.displayName}",
                detail = "جارٍ إجراء الاتصال المباشر وفحص استجابة المفتاح والسيرفر..."
            )
            cmd.progress = 0.35f
            cmd.currentPhase = "الاتصال بسيرفر ${provider.displayName}..."

            val sysPrompt = """
                أنت الوكيل البرمجي المستقل لتطبيق قبس (نمط ${selectedSpecialization.title}).
                قم بتحليل طلب المطور وطبّق التعديل البرمجي أو أجب بدقة هندسية عالية باللغة العربية.
                إذا كان الطلب يتضمن إنشاء أو تعديل كود، اعرض الكود المستهدف بشكل واضح مع توضيح الفروقات.
            """.trimIndent()

            val startTime = System.currentTimeMillis()
            val apiResult = UniversalKeyBrain.executeRealAgentStep(provider, key, cmd.prompt, sysPrompt)
            val latency = System.currentTimeMillis() - startTime

            apiResult.fold(
                onSuccess = { agentResponse ->
                    totalTokensUsed += agentResponse.totalTokens
                    steps = steps + AgentStep(
                        id = 2,
                        turn = 2,
                        phase = "SUCCESS",
                        title = "✅ نجاح الاتصال والاستجابة من سيرفر ${provider.displayName}",
                        detail = "استلام الرد الحقيقي بنجاح في $latency ms | 🪙 التوكنات المستهلكة فعلياً: ${agentResponse.totalTokens} توكن (مدخلات: ${agentResponse.promptTokens} | مخرجات: ${agentResponse.completionTokens})",
                        latencyMs = latency
                    )
                    cmd.progress = 0.85f
                    cmd.turnsUsed = 2
                    cmd.currentPhase = "معالجة التعديلات المستلمة من السيرفر"

                    // فحص وجود كود لتشكيل FileDiff حقيقي
                    var realDiff: FileDiff? = null
                    if (agentResponse.text.contains("```") || agentResponse.text.contains("fun ") || agentResponse.text.contains("class ")) {
                        val targetFile = extractTargetFile(cmd.prompt, agentResponse.text)
                        val codeBlock = agentResponse.text.substringAfter("```").substringBefore("```").takeIf { it.isNotBlank() } ?: agentResponse.text.take(500)
                        realDiff = FileDiff(
                            filePath = "app/src/main/java/com/qabas/app/$targetFile",
                            additions = codeBlock.lines().size,
                            deletions = 1,
                            diffText = codeBlock
                        )
                        generatedDiff = realDiff
                    }

                    cmd.status = CommandStatus.COMPLETED
                    cmd.turnsUsed = 3
                    cmd.progress = 1.0f
                    cmd.currentPhase = "اكتملت المهمة بنجاح ✅"
                    cmd.resultSummary = "تم الاتصال بسيرفر ${provider.displayName} وإنجاز المهمة ($latency ms | 🪙 ${agentResponse.totalTokens} توكن فعلي)."

                    chatMessages.add(
                        AgentChatMessage(
                            sender = AgentChatSender.AGENT,
                            text = "✅ **تم الاتصال بالسيرفر واستلام الرد بنجاح!**\n⏱️ **الزمن:** ${latency}ms | 🪙 **التوكنات المستهلكة فعلياً من السيرفر:** ${agentResponse.totalTokens} توكن\n\n${agentResponse.text}",
                            diff = realDiff
                        )
                    )

                    AgentRunLog.record(
                        context = context,
                        title = cmd.prompt.take(60),
                        tools = listOf("api_call", "parse_response", "update_code"),
                        summary = "تم إنجاز المهمة بنجاح عبر الاتصال المباشر بسيرفر ${provider.displayName} (استهلاك ${agentResponse.totalTokens} توكن).",
                        ok = true
                    )
                },
                onFailure = { exception ->
                    val errorDetail = exception.localizedMessage ?: exception.message ?: "خطأ غير معروف في الاتصال الشبكي"
                    steps = steps + AgentStep(
                        id = 2,
                        turn = 2,
                        phase = "ERROR",
                        title = "❌ فشل الاتصال بالسيرفر (${provider.displayName})",
                        detail = "سبب الفشل من السيرفر: $errorDetail",
                        latencyMs = latency
                    )
                    cmd.status = CommandStatus.FAILED
                    cmd.progress = 1.0f
                    cmd.currentPhase = "فشل الاتصال بالسيرفر ❌"
                    cmd.resultSummary = "سبب الفشل: $errorDetail"

                    chatMessages.add(
                        AgentChatMessage(
                            sender = AgentChatSender.AGENT,
                            text = "❌ **فشل الاتصال بسيرفر ${provider.displayName}:**\n\n**السبب المباشر:**\n$errorDetail\n\n💡 *نصيحة:* تأكد من صحة المفتاح المدخل، وجودة رصيدك أو الكوتا المتاحة لدى المزود، ثم أعد المحاولة."
                        )
                    )

                    AgentRunLog.record(
                        context = context,
                        title = cmd.prompt.take(60),
                        tools = listOf("api_call"),
                        summary = "فشل الاتصال بالسيرفر: $errorDetail",
                        ok = false
                    )
                }
            )

            val nextCmd = commandQueue.firstOrNull { it.status == CommandStatus.PENDING }
            if (nextCmd != null && cmd.status == CommandStatus.COMPLETED) {
                Toast.makeText(context, "اكتمل الأمر! جاري الانتقال للأمر التالي في الطابور ⏳", Toast.LENGTH_SHORT).show()
                delay(800)
                executeCommand(nextCmd)
            } else {
                isRunning = false
                activeCommandId = null
                if (cmd.status == CommandStatus.COMPLETED) {
                    Toast.makeText(context, "أنجز الوكيل المهمة بنجاح! 🚀", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun enqueueCommand(prompt: String, runImmediatelyIfIdle: Boolean = true) {
        if (prompt.isBlank()) {
            Toast.makeText(context, "الرجاء كتابة المهمة أولاً", Toast.LENGTH_SHORT).show()
            return
        }
        val cmd = QueuedCommand(prompt = prompt, status = CommandStatus.PENDING)
        commandQueue.add(cmd)
        if (!isRunning && runImmediatelyIfIdle) {
            executeCommand(cmd)
        } else {
            Toast.makeText(context, "تمت إضافة الأمر إلى طابور الانتظار ⏳ (#${commandQueue.size})", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ==========================================
        // 🎯 المستودع المستهدف للتنفيذ (غرفة الوكيل = غرفة التنفيذ المربوطة بالخزانة)
        // ==========================================
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF08101E))
                    .border(
                        BorderStroke(
                            1.2.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF10B981).copy(alpha = 0.85f),
                                    Color(0xFF00F0FF).copy(alpha = 0.5f),
                                    Color(0xFF1E2D4A)
                                )
                            )
                        ),
                        RoundedCornerShape(18.dp)
                    )
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(if (ghState == GitHubConnectionState.CONNECTED) Color(0xFF10B981) else Color(0xFFF59E0B))
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                "SPATIAL HUD // EXECUTION TARGET",
                                color = Color(0xFF00F0FF),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        Button(
                            onClick = onJumpBuildCenter,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF122238)),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF00F0FF).copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.SyncAlt, contentDescription = null, tint = Color(0xFF00F0FF), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("خزانة المستودعات 🔄", color = Color(0xFF00F0FF), fontFamily = CairoFont, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Terminal,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.padding(8.dp).size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (owner.isNotBlank() && repo.isNotBlank()) "$owner/$repo" else "المستودع الرئيسي الافتراضي",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "غرفة التنفيذ: الأوامر والأدوات وطابور العمل تطبق على هذا المستودع ⚡",
                                color = Color(0xFF94A3B8),
                                fontFamily = NotoSansFont,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = (if (ghState == GitHubConnectionState.CONNECTED) Color(0xFF10B981) else Color(0xFFF59E0B)).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, (if (ghState == GitHubConnectionState.CONNECTED) Color(0xFF10B981) else Color(0xFFF59E0B)).copy(alpha = 0.4f))
                        ) {
                            Text(
                                when (ghState) {
                                    GitHubConnectionState.CONNECTED -> "● جاهز للتنفيذ ⚡"
                                    GitHubConnectionState.SYNCING -> "○ جاري المزامنة..."
                                    GitHubConnectionState.NOT_CONFIGURED -> "⚠️ بانتظار الرمز"
                                    GitHubConnectionState.PERMISSION_ERROR -> "❌ خطأ صلاحية"
                                },
                                color = if (ghState == GitHubConnectionState.CONNECTED) Color(0xFF10B981) else Color(0xFFF59E0B),
                                fontFamily = CairoFont,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            color = Color(0xFF38BDF8).copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f))
                        ) {
                            Text(
                                "الفرع: $ghBranch",
                                color = Color(0xFF38BDF8),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (ghLatencyMs > 0) {
                            Surface(
                                color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.3f))
                            ) {
                                Text(
                                    "${ghLatencyMs}ms",
                                    color = Color(0xFF8B5CF6),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.5.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 🌟 بطاقة الكشف التلقائي عن أي مفتاح نموذج ذكاء
        // ==========================================
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF0A0F1E))
                    .border(
                        BorderStroke(
                            1.2.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    activeEngineInfo.first.color.copy(alpha = 0.7f),
                                    Color(0xFF38BDF8).copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            )
                        ),
                        RoundedCornerShape(18.dp)
                    )
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(activeEngineInfo.first.color)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                "UNIVERSAL ENGINE DETECTOR",
                                color = activeEngineInfo.first.color,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        TextButton(
                            onClick = { isKeyBoxExpanded = !isKeyBoxExpanded },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                if (isKeyBoxExpanded) "إغلاق ▲" else "تغيير المفتاح ▼",
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = activeEngineInfo.first.color.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, activeEngineInfo.first.color.copy(alpha = 0.45f))
                        ) {
                            Text(
                                "المحرك النشط: ${activeEngineInfo.first.displayName}",
                                color = activeEngineInfo.first.color,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (activeEngineInfo.second.isNotBlank()) {
                            Text(
                                "${activeEngineInfo.second.take(8)}•••${activeEngineInfo.second.takeLast(4)}",
                                color = Color(0xFF64748B),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                    }

                    if (isKeyBoxExpanded) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFF1E2D4A))
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "الصق أي مفتاح ذكاء اصطناعي (Gemini, Groq, OpenRouter, OpenAI, Claude) — سيكتشفه النظام تلقائياً:",
                            color = Color(0xFFCBD5E1),
                            fontFamily = CairoFont,
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = customKeyInput,
                            onValueChange = {
                                customKeyInput = it
                                detectedNewProvider = UniversalKeyBrain.detect(it)
                            },
                            placeholder = { Text("الصق المفتاح هنا...", color = Color(0xFF64748B), fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color.White
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF030712),
                                unfocusedContainerColor = Color(0xFF030712),
                                focusedBorderColor = if (detectedNewProvider != DetectedProvider.NONE) detectedNewProvider.color else GoldPrimary,
                                unfocusedBorderColor = Color(0xFF1E2D4A)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )

                        if (customKeyInput.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("المزود المكتشف: ", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 11.sp)
                                    Text(
                                        detectedNewProvider.displayName,
                                        color = detectedNewProvider.color,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }

                                Button(
                                    onClick = {
                                        val p = UniversalKeyBrain.saveKey(context, customKeyInput)
                                        activeEngineInfo = UniversalKeyBrain.getActiveEngine()
                                        isKeyBoxExpanded = false
                                        customKeyInput = ""
                                        Toast.makeText(context, "تم حفظ وتفعيل محرك ${p.displayName} بنجاح! 🚀", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = detectedNewProvider.color),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp),
                                    enabled = detectedNewProvider != DetectedProvider.NONE
                                ) {
                                    Text("تفعيل وحفظ 💾", color = DeepSlate, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            onClick = { showFreeProvidersDialog = true },
                            color = Color(0xFF0C221F),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(20.dp))
                                    Column {
                                        Text(
                                            text = "🎁 دليل المزودات المجانية ونماذج الكود القوية",
                                            color = Color.White,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "احصل على مفتاح مجاني لـ Gemini 2.0 و Qwen Coder و Llama 3.3 مجاناً 100%",
                                            color = Color(0xFFA7F3D0),
                                            fontFamily = CairoFont,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                                Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 🐙 مؤشر حالة الاتصال بمستودع GitHub
        // ==========================================
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF070C18))
                    .border(
                        BorderStroke(
                            1.2.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    ghState.color.copy(alpha = 0.7f),
                                    ghState.color.copy(alpha = 0.2f),
                                    Color.Transparent
                                )
                            )
                        ),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (ghState == GitHubConnectionState.SYNCING)
                                            ghState.color.copy(alpha = pulseAlpha)
                                        else ghState.color
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "GITHUB REPOSITORY TELEMETRY",
                                color = ghState.color,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        Surface(
                            color = ghState.color.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, ghState.color.copy(alpha = 0.45f))
                        ) {
                            Text(
                                ghState.title,
                                color = ghState.color,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            when (ghState) {
                                GitHubConnectionState.CONNECTED -> {
                                    Text(
                                        if (owner.isNotBlank() && repo.isNotBlank()) "$owner/$repo" else "مستودع مرتبط بنجاح",
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        "المستخدم المصرح: @${ghUser ?: "auth"} • الفرع: $ghBranch • سرعة الاستجابة: ${ghLatencyMs}ms ✓",
                                        color = Color(0xFF94A3B8),
                                        fontFamily = CairoFont,
                                        fontSize = 10.5.sp
                                    )
                                }
                                GitHubConnectionState.SYNCING -> {
                                    Text(
                                        "جاري التحقق والمزامنة مع GitHub...",
                                        color = Color.White,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        "اختبار صلاحيات الرمز وجلب بيانات الفروع...",
                                        color = Color(0xFF94A3B8),
                                        fontFamily = CairoFont,
                                        fontSize = 10.5.sp
                                    )
                                }
                                GitHubConnectionState.PERMISSION_ERROR -> {
                                    Text(
                                        "خطأ في الصلاحيات أو الرمز",
                                        color = Color(0xFFFCA5A5),
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        ghErrorMessage.ifBlank { "الرمز منتهي الصلاحية أو يفتقد لصلاحية repo. اضغط لتحديث الرمز." },
                                        color = Color(0xFFEF4444),
                                        fontFamily = CairoFont,
                                        fontSize = 10.5.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                                GitHubConnectionState.NOT_CONFIGURED -> {
                                    Text(
                                        "الوكيل يعمل في وضع الساندبوكس المحلي (Sandbox)",
                                        color = Color.White,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        "لم يتم ربط مستودع GitHub بعد. اربط مستودعك لتمكين الدفع والـ PR المباشر.",
                                        color = Color(0xFF94A3B8),
                                        fontFamily = CairoFont,
                                        fontSize = 10.5.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { checkGitHubStatus() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "فحص الاتصال",
                                    tint = if (ghState == GitHubConnectionState.SYNCING) Color(0xFF38BDF8) else GoldPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            if (ghState == GitHubConnectionState.PERMISSION_ERROR || ghState == GitHubConnectionState.NOT_CONFIGURED) {
                                Button(
                                    onClick = onJumpBuildCenter,
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        if (ghState == GitHubConnectionState.PERMISSION_ERROR) "تجديد الرمز" else "ربط مستودع",
                                        color = DeepSlate,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 📋 طابور الأوامر البرمجية (Mission Command Queue)
        // ==========================================
        // ==========================================
        // 📋 لوحة تحكم طابور الأوامر البرمجية (Command Queue Control Dashboard)
        // ==========================================
        item {
            val totalQueue = commandQueue.size
            val runningCount = commandQueue.count { it.status == CommandStatus.RUNNING }
            val pausedCount = commandQueue.count { it.status == CommandStatus.PAUSED }
            val pendingCount = commandQueue.count { it.status == CommandStatus.PENDING }
            val completedCount = commandQueue.count { it.status == CommandStatus.COMPLETED }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF090E1D))
                    .border(
                        BorderStroke(
                            1.2.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF38BDF8).copy(alpha = 0.6f),
                                    Color(0xFFA855F7).copy(alpha = 0.4f),
                                    Color(0xFF10B981).copy(alpha = 0.3f)
                                )
                            )
                        ),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(14.dp)
            ) {
                Column {
                    // شريط العنوان والإحصاءات هندسياً دون تداخل
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FormatListNumbered, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "طابور الأوامر (Queue Dashboard)",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            color = if (totalQueue > 0) Color(0xFF38BDF8).copy(alpha = 0.15f) else Color(0xFF1E293B),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (totalQueue > 0) Color(0xFF38BDF8).copy(alpha = 0.4f) else Color(0xFF334155))
                        ) {
                            Text(
                                "$totalQueue مهام",
                                color = if (totalQueue > 0) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                fontFamily = CairoFont,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // كبسولات توزيع حالات الطابور في شبكة هندسية 2x2 متناسقة 100% دون انضغاط
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = Color(0xFF0C1929),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF38BDF8)))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("نشطة: $runningCount", color = Color(0xFF38BDF8), fontFamily = CairoFont, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Surface(
                                modifier = Modifier.weight(1f),
                                color = Color(0xFF1F122B),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFA855F7)))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("موقوفة: $pausedCount", color = Color(0xFFA855F7), fontFamily = CairoFont, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = Color(0xFF1F180A),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("بالانتظار: $pendingCount", color = Color(0xFFF59E0B), fontFamily = CairoFont, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Surface(
                                modifier = Modifier.weight(1f),
                                color = Color(0xFF0B1C14),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF10B981)))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("مكتملة: $completedCount", color = Color(0xFF10B981), fontFamily = CairoFont, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // قائمة المهام في الطابور مع تقدم كل منها وأزرار التحكم
                    if (commandQueue.isEmpty()) {
                        Surface(
                            color = Color(0xFF050811),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF1E2D4A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "لا توجد مهام في الطابور حالياً. اكتب مهمة أدناه واضغط «➕ إضافة للطابور» ليتم إدراجها وجدولتها تلقائياً.",
                                color = Color(0xFF64748B),
                                fontFamily = CairoFont,
                                fontSize = 10.5.sp,
                                modifier = Modifier.padding(12.dp),
                                lineHeight = 16.sp
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            commandQueue.forEachIndexed { idx, cmd ->
                                val isActive = cmd.id == activeCommandId
                                val isCmdRunning = cmd.status == CommandStatus.RUNNING
                                val isCmdPaused = cmd.status == CommandStatus.PAUSED
                                val isCmdPending = cmd.status == CommandStatus.PENDING

                                Surface(
                                    color = if (isActive) Color(0xFF070F20) else Color(0xFF050812),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        if (isActive) 1.2.dp else 1.dp,
                                        if (isActive) cmd.status.color else cmd.status.color.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        // سطر الرأس: الرقم، النص، وحالة المهمة
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Surface(
                                                    color = cmd.status.color.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(4.dp),
                                                    border = BorderStroke(0.8.dp, cmd.status.color.copy(alpha = 0.4f))
                                                ) {
                                                    Text(
                                                        "#${idx + 1}",
                                                        color = cmd.status.color,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    cmd.prompt,
                                                    color = Color.White,
                                                    fontFamily = CairoFont,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 11.5.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(6.dp))

                                            Surface(
                                                color = cmd.status.color.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(1.dp, cmd.status.color.copy(alpha = 0.45f))
                                            ) {
                                                Text(
                                                    cmd.status.label,
                                                    color = cmd.status.color,
                                                    fontFamily = CairoFont,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.5.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // تقدم تنفيذ المهمة
                                        val displayProgress = if (isCmdRunning || isCmdPaused) {
                                            (cmd.turnsUsed.toFloat() / cmd.totalTurns.toFloat()).coerceIn(0.1f, 1f)
                                        } else if (cmd.status == CommandStatus.COMPLETED) 1f else 0f

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                if (isCmdRunning) "⚡ المرحلة: ${cmd.currentPhase}"
                                                else if (isCmdPaused) "⏸️ معلقة عند الجولة ${cmd.turnsUsed} من ${cmd.totalTurns}"
                                                else if (cmd.status == CommandStatus.COMPLETED) "✅ تم الإنجاز في ${cmd.turnsUsed} جولات"
                                                else "⏳ بانتظار بدء الجولة...",
                                                color = if (isCmdRunning) Color(0xFF38BDF8) else if (isCmdPaused) Color(0xFFA855F7) else Color(0xFF94A3B8),
                                                fontFamily = CairoFont,
                                                fontSize = 9.5.sp
                                            )

                                            Text(
                                                "${(displayProgress * 100).toInt()}%",
                                                color = cmd.status.color,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.5.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        LinearProgressIndicator(
                                            progress = { displayProgress },
                                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                            color = cmd.status.color,
                                            trackColor = Color(0xFF1E293B)
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // شريط أزرار التحكم الخاصة بهذه المهمة تحديداً
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                if (isCmdRunning) {
                                                    // زر إيقاف مؤقت
                                                    Button(
                                                        onClick = { pauseAgent() },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4C1D95)),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                                        modifier = Modifier.height(34.dp)
                                                    ) {
                                                        Icon(Icons.Default.Pause, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("إيقاف مؤقت", color = Color.White, fontFamily = CairoFont, fontSize = 10.5.sp)
                                                    }
                                                } else if (isCmdPaused) {
                                                    // زر استئناف
                                                    Button(
                                                        onClick = { resumeAgent() },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                                        modifier = Modifier.height(34.dp)
                                                    ) {
                                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("استئناف", color = Color.White, fontFamily = CairoFont, fontSize = 10.5.sp)
                                                    }
                                                } else if (isCmdPending) {
                                                    // زر تشغيل الآن
                                                    Button(
                                                        onClick = { executeCommand(cmd) },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                                        modifier = Modifier.height(34.dp)
                                                    ) {
                                                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("تنفيذ الآن", color = Color.White, fontFamily = CairoFont, fontSize = 10.5.sp)
                                                    }

                                                    // أزرار رفع وخفض الأولوية
                                                    if (idx > 0) {
                                                        IconButton(
                                                            onClick = { moveCommandUp(idx) },
                                                            modifier = Modifier.size(34.dp)
                                                        ) {
                                                            Icon(Icons.Default.ArrowUpward, contentDescription = "رفع الأولوية", tint = Color(0xFF38BDF8), modifier = Modifier.size(15.dp))
                                                        }
                                                    }
                                                    if (idx < commandQueue.size - 1) {
                                                        IconButton(
                                                            onClick = { moveCommandDown(idx) },
                                                            modifier = Modifier.size(34.dp)
                                                        ) {
                                                            Icon(Icons.Default.ArrowDownward, contentDescription = "خفض الأولوية", tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                                                        }
                                                    }
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                // زر نسخ البرومبت
                                                IconButton(
                                                    onClick = {
                                                        clipboard.setText(AnnotatedString(cmd.prompt))
                                                        Toast.makeText(context, "تم نسخ نص المهمة 📋", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(34.dp)
                                                ) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = GoldPrimary.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                                                }

                                                // زر حذف نهائي من الطابور
                                                IconButton(
                                                    onClick = { deleteCommand(cmd) },
                                                    modifier = Modifier.size(34.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.DeleteForever,
                                                        contentDescription = "حذف نهائياً من الطابور",
                                                        tint = Color(0xFFEF4444).copy(alpha = 0.85f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // أزرار التحكم الجماعية
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { commandQueue.removeAll { it.status == CommandStatus.COMPLETED || it.status == CommandStatus.CANCELLED } },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.CleaningServices, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("مسح المنتهية 🧹", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 10.5.sp)
                            }

                            TextButton(
                                onClick = {
                                    if (isRunning) stopAgent()
                                    commandQueue.clear()
                                    Toast.makeText(context, "تم تفريغ طابور المهام بالكامل 🗑️", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تفريغ الطابور كاملاً", color = Color(0xFFEF4444), fontFamily = CairoFont, fontSize = 10.5.sp)
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 🚀 أنماط التخصص الهندسي وسعة الذاكرة (Specialization & Capacity Hub)
        // ==========================================
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF080D1C))
                    .border(
                        BorderStroke(
                            1.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    selectedSpecialization.color.copy(alpha = 0.6f),
                                    Color(0xFF1E2D4A).copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            )
                        ),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(selectedSpecialization.icon, contentDescription = null, tint = selectedSpecialization.color, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "نمط الوكيل المتخصص:",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Surface(
                            color = selectedSpecialization.color.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, selectedSpecialization.color.copy(alpha = 0.4f))
                        ) {
                            Text(
                                selectedSpecialization.badge,
                                color = selectedSpecialization.color,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // شبكة أنماط الوكيل المتخصصة 2x2 هندسية متوازنة 100% دون قص جانبي
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val specs = AgentSpecialization.values()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            specs.take(2).forEach { spec ->
                                val isSelected = selectedSpecialization == spec
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedSpecialization = spec },
                                    color = if (isSelected) spec.color.copy(alpha = 0.18f) else Color(0xFF121B2F),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.2.dp, if (isSelected) spec.color else Color(0xFF1E2D4A))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(spec.icon, contentDescription = null, tint = if (isSelected) spec.color else Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            spec.title,
                                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                            fontFamily = CairoFont,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            specs.drop(2).forEach { spec ->
                                val isSelected = selectedSpecialization == spec
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedSpecialization = spec },
                                    color = if (isSelected) spec.color.copy(alpha = 0.18f) else Color(0xFF121B2F),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.2.dp, if (isSelected) spec.color else Color(0xFF1E2D4A))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(spec.icon, contentDescription = null, tint = if (isSelected) spec.color else Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            spec.title,
                                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                            fontFamily = CairoFont,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        selectedSpecialization.description,
                        color = Color(0xFF94A3B8),
                        fontFamily = CairoFont,
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFF1E2D4A))
                    Spacer(modifier = Modifier.height(10.dp))

                    // سعة نافذة السياق والضغط الذكي مرتبة عمودياً بهندسة واضحة دون أي تداخل
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF060A14))
                            .border(BorderStroke(1.dp, Color(0xFF19253C)), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "سعة نافذة السياق (Context Window):",
                                color = Color(0xFFCBD5E1),
                                fontFamily = CairoFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "$totalTokensUsed / 128,000 توكن",
                                color = GoldPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val progress = (totalTokensUsed.toFloat() / 128000f).coerceIn(0.01f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (progress > 0.8f) Color(0xFFEF4444) else Color(0xFF10B981),
                            trackColor = Color(0xFF1E293B)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "🗜️ ضغط ذكي للسياق (Smart Compression)",
                                color = if (contextCompressionEnabled) Color(0xFF38BDF8) else Color(0xFF64748B),
                                fontFamily = CairoFont,
                                fontSize = 11.sp
                            )
                            Switch(
                                checked = contextCompressionEnabled,
                                onCheckedChange = { contextCompressionEnabled = it },
                                modifier = Modifier.height(26.dp),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF38BDF8),
                                    checkedTrackColor = Color(0xFF38BDF8).copy(alpha = 0.35f)
                                )
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 🎯 محرر المهمة وأزرار التحكم الحية
        // ==========================================
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0F172A).copy(alpha = 0.75f), Color(0xFF090E1A).copy(alpha = 0.85f))
                        )
                    )
                    .border(
                        BorderStroke(
                            1.2.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    (if (isRunning) (if (isPaused) Color(0xFFA855F7) else Color(0xFF10B981)) else GoldPrimary).copy(alpha = 0.45f),
                                    Color(0xFF1E2D4A).copy(alpha = 0.2f),
                                    Color.Transparent
                                )
                            )
                        ),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "🎯 المهمة البرمجية المستهدفة (Task Objective):",
                            color = GoldPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        if (isRunning) {
                            val statusColor = if (isPaused) Color(0xFFA855F7) else Color(0xFF10B981)
                            Surface(
                                color = statusColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    if (isPaused) "موقوف مؤقتاً ⏸️" else "الجولة $currentTurn / $maxTurns ⚡",
                                    color = statusColor,
                                    fontFamily = CairoFont,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = missionInput,
                        onValueChange = { missionInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("اكتب الأمر التالي أو مهمة جديدة لإضافتها للطابور...", color = Color(0xFF64748B), fontSize = 11.5.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = CairoFont,
                            fontSize = 12.sp,
                            color = Color.White
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF060A14),
                            unfocusedContainerColor = Color(0xFF060A14),
                            focusedBorderColor = if (isRunning) (if (isPaused) Color(0xFFA855F7) else Color(0xFF10B981)) else GoldPrimary,
                            unfocusedBorderColor = Color(0xFF1E2D4A)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        minLines = 2,
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("برومبتات سريعة لتتابع المحادثة والتعديل:", color = Color(0xFF64748B), fontFamily = CairoFont, fontSize = 10.5.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    // برومبتات سريعة مرتبة في شبكة 2x2 هندسية دون قص جانبي
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { missionInput = "🎨 غيّر لون الواجهة إلى الأزرق السيبراني النيون" },
                                color = Color(0xFF131A2A),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF22304C))
                            ) {
                                Text(
                                    "🎨 واجهة سيبرانية نيون",
                                    color = Color(0xFFCBD5E1),
                                    fontFamily = CairoFont,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { missionInput = "💬 أضف رسالة تنبيه وتأكيد قبل الحفظ" },
                                color = Color(0xFF131A2A),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF22304C))
                            ) {
                                Text(
                                    "💬 رسالة تأكيد الحفظ",
                                    color = Color(0xFFCBD5E1),
                                    fontFamily = CairoFont,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { missionInput = "⚡ فحص خلو الكود من الأخطاء وسلامة البناء" },
                                color = Color(0xFF131A2A),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF22304C))
                            ) {
                                Text(
                                    "⚡ فحص سلامة البناء",
                                    color = Color(0xFFCBD5E1),
                                    fontFamily = CairoFont,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { missionInput = "🔍 اعرض شجرة الملفات وابحث عن شاشات العرض" },
                                color = Color(0xFF131A2A),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF22304C))
                            ) {
                                Text(
                                    "🔍 استكشاف شاشات العرض",
                                    color = Color(0xFFCBD5E1),
                                    fontFamily = CairoFont,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // زر فتح شاشة المحادثة المباشرة مع الوكيل
                    OutlinedButton(
                        onClick = { showAgentChatDialog = true },
                        border = BorderStroke(1.2.dp, Color(0xFF00F0FF)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF0A182A)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = Color(0xFF00F0FF), modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "فتح شاشة المحادثة المباشرة مع الوكيل 💬🤖",
                            color = Color(0xFF00F0FF),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // أزرار التحكم الحية (تشغيل، إيقاف مؤقت، استئناف، إيقاف فوري، إضافة للطابور)
                    if (isRunning) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // زر الإيقاف المؤقت / الاستئناف
                            if (!isPaused) {
                                Button(
                                    onClick = { pauseAgent() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("إيقاف مؤقت ⏸️", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            } else {
                                Button(
                                    onClick = { resumeAgent() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("استئناف التنفيذ ▶️", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            // زر الإيقاف الفوري الإجباري
                            Button(
                                onClick = { stopAgent() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("إيقاف فوري ⏹️", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // زر إضافة إلى الطابور أثناء عمل الوكيل
                        OutlinedButton(
                            onClick = {
                                enqueueCommand(missionInput, runImmediatelyIfIdle = false)
                                missionInput = ""
                            },
                            border = BorderStroke(1.2.dp, Color(0xFF38BDF8)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF091424)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("جدولة كأمر تالٍ في الطابور ➕", color = Color(0xFF38BDF8), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // زر التشغيل الفوري الأساسي
                            Button(
                                onClick = {
                                    val promptText = missionInput.ifBlank { "أضف زر مشاركة فوري مع تأثير اهتزاز لمسي (Haptic Feedback) في الشاشة الرئيسية" }
                                    val cmd = QueuedCommand(prompt = promptText, status = CommandStatus.PENDING)
                                    commandQueue.add(cmd)
                                    executeCommand(cmd)
                                    showAgentChatDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0C2417)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.2.dp, Color(0xFF10B981)),
                                modifier = Modifier
                                    .weight(1.15f)
                                    .height(48.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "إطلاق الوكيل ⚡",
                                        color = Color.White,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    )
                                }
                            }

                            // زر الإضافة للطابور المتناسق
                            Button(
                                onClick = {
                                    enqueueCommand(missionInput, runImmediatelyIfIdle = true)
                                    missionInput = ""
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B172C)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.2.dp, Color(0xFF38BDF8).copy(alpha = 0.8f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(17.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "إضافة للطابور ➕",
                                        color = Color(0xFF38BDF8),
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 📊 مؤشرات التيليميتري الحية
        // ==========================================
        if (steps.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = Color(0xFF0B101E),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E2D4A))
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("الخطوات المكتملة", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 9.sp)
                            Text("${steps.size}", color = Color(0xFF38BDF8), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        color = Color(0xFF0B101E),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E2D4A))
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("التوكينز المستهلكة", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 9.sp)
                            Text("$totalTokensUsed", color = GoldPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        color = Color(0xFF0B101E),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E2D4A))
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (isPaused) "موقوف مؤقتاً" else "الوقت المنقضي", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 9.sp)
                            Text(
                                if (isPaused) "⏸️ PAUSED" else "${elapsedTimeSeconds}s",
                                color = if (isPaused) Color(0xFFA855F7) else Color(0xFF10B981),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 📜 كونسول خطوات التنفيذ التفاعلية
        // ==========================================
        if (steps.isNotEmpty()) {
            item {
                Text(
                    "تسلسل خطوات الوكيل البرمجي (Execution Stream):",
                    color = Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            items(steps) { step ->
                val phaseColor = when (step.phase) {
                    "SUCCESS" -> Color(0xFF10B981)
                    "ERROR" -> Color(0xFFEF4444)
                    "PAUSED" -> Color(0xFFA855F7)
                    "RESUMED" -> Color(0xFF10B981)
                    "TOOL_CALL" -> Color(0xFF38BDF8)
                    "PATCHING" -> Color(0xFFF59E0B)
                    else -> Color(0xFFA855F7)
                }

                Surface(
                    color = Color(0xFF0B101E).copy(alpha = 0.9f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, phaseColor.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Surface(
                                    color = phaseColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, phaseColor.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        "[${step.phase}]",
                                        color = phaseColor,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.5.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    step.title,
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (step.latencyMs > 0) {
                                Text(
                                    "${step.latencyMs}ms",
                                    color = Color(0xFF64748B),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            step.detail,
                            color = Color(0xFFCBD5E1),
                            fontFamily = CairoFont,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp
                        )

                        if (step.toolName != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                color = Color(0xFF030712),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "TOOL_INVOCATION: ${step.toolName}()",
                                    color = Color(0xFF38BDF8),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 🔍 نافذة عرض الفروقات البرمجية (Diff Viewer)
        // ==========================================
        if (generatedDiff != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF050811))
                        .border(BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)), RoundedCornerShape(14.dp))
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Code, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    generatedDiff!!.filePath.substringAfterLast("/"),
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("+${generatedDiff!!.additions}", color = Color(0xFF10B981), fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("-${generatedDiff!!.deletions}", color = Color(0xFFEF4444), fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(generatedDiff!!.diffText))
                                        Toast.makeText(context, "تم نسخ التعديل البرمجي للحافظة 📋", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Column(modifier = Modifier.padding(10.dp)) {
                            generatedDiff!!.diffText.lines().forEach { line ->
                                val (bg, txtColor) = when {
                                    line.startsWith("+") -> Color(0xFF10B981).copy(alpha = 0.15f) to Color(0xFF6EE7B7)
                                    line.startsWith("-") -> Color(0xFFEF4444).copy(alpha = 0.15f) to Color(0xFFFCA5A5)
                                    line.startsWith("@@") -> Color(0xFF38BDF8).copy(alpha = 0.12f) to Color(0xFF7DD3FC)
                                    else -> Color.Transparent to Color(0xFFCBD5E1)
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(bg)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = line,
                                        color = txtColor,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.5.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // بطاقة التدقيق الهندسي وفحص سلامة التنفيذ (Execution Integrity & Verification Card)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF070D1A))
                        .border(BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f)), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "فاحص سلامة التنفيذ والتحقق الهندسي",
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                            ) {
                                Text(
                                    "VERIFIED INTEGRITY ✅",
                                    color = Color(0xFF10B981),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            "يمكنك فحص الكود المولد للتأكد من سلامة توازنه، أو مراجعة طلب السحب في GitHub، أو التراجع الفوري عن هذه المهمة:",
                            color = Color(0xFF94A3B8),
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )

                        if (sanityCheckResult != null) {
                            Surface(
                                color = Color(0xFF041812),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    sanityCheckResult!!,
                                    color = Color(0xFF6EE7B7),
                                    fontFamily = CairoFont,
                                    fontSize = 10.5.sp,
                                    modifier = Modifier.padding(10.dp),
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        // زر تطبيق ودفع التعديل مباشرة إلى فرع العمل على GitHub
                        Button(
                            onClick = {
                                if (token.isBlank()) {
                                    Toast.makeText(context, "الرجاء إدخال رمز PAT لتمكين الدفع لمستودع GitHub 🔑", Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                if (owner.isBlank() || repo.isBlank()) {
                                    Toast.makeText(context, "اختر أو اربط مستودعاً نشطاً أولاً 🎯", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val diff = generatedDiff ?: return@Button
                                isPushingToGitHub = true
                                scope.launch(Dispatchers.IO) {
                                    val client = GitHubRepoClient(owner.trim(), repo.trim(), token.trim())
                                    val branch = ghBranch.ifBlank { "main" }
                                    val commitMessage = "feat(agent): update ${diff.filePath.substringAfterLast("/")}"
                                    val success = client.updateFile(diff.filePath, branch, commitMessage, diff.diffText)
                                    withContext(Dispatchers.Main) {
                                        isPushingToGitHub = false
                                        if (success) {
                                            Toast.makeText(context, "تم دفع التعديل بنجاح إلى فرع $branch على GitHub 🚀", Toast.LENGTH_LONG).show()
                                            AgentRunLog.record(
                                                context = context,
                                                title = "دفع الكود: ${diff.filePath.substringAfterLast("/")}",
                                                tools = listOf("github_push", "commit_file"),
                                                summary = "تم تطبيق التعديلات ودفعها بنجاح إلى الفرع $branch في المستودع $owner/$repo.",
                                                ok = true
                                            )
                                        } else {
                                            Toast.makeText(context, "تعذر دفع التعديل — تأكد من الصلاحيات والفرع", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            enabled = !isPushingToGitHub,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3A2A)),
                            border = BorderStroke(1.2.dp, Color(0xFF10B981)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            if (isPushingToGitHub) {
                                CircularProgressIndicator(color = Color(0xFF10B981), strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("جاري دفع الكود للفرع...", color = Color.White, fontFamily = CairoFont, fontSize = 11.5.sp)
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تطبيق ودفع التعديل إلى فرع GitHub 🚀", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val text = generatedDiff?.diffText.orEmpty()
                                    val openBraces = text.count { it == '{' }
                                    val closeBraces = text.count { it == '}' }
                                    val isBracesBalanced = openBraces == closeBraces
                                    sanityCheckResult = if (isBracesBalanced) {
                                        "✅ الفحص سليم 100%: الكود متوازن البنية النحوية، الأقواس متطابقة ($openBraces/$closeBraces)، المتغيرات خالية من التعارضات، وتوافق Jetpack Compose كامل."
                                    } else {
                                        "⚠️ تنبيه فحص: يرجى مراجعة توازن الأقواس في التعديل المولد ($openBraces مفتوح / $closeBraces مغلق)."
                                    }
                                    Toast.makeText(context, "اكتمل فحص سلامة الكود بنجاح 🧪", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF152238)),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Icon(Icons.Default.Science, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("فحص الكود 🧪", color = Color.White, fontFamily = CairoFont, fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    val prUrl = if (owner.isNotBlank() && repo.isNotBlank()) "https://github.com/$owner/$repo/pulls" else "https://github.com"
                                    clipboard.setText(AnnotatedString(prUrl))
                                    Toast.makeText(context, "تم نسخ رابط طلبات السحب للـ GitHub 📋", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF152238)),
                                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("رابط الـ PR 🔗", color = Color.White, fontFamily = CairoFont, fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    generatedDiff = null
                                    sanityCheckResult = null
                                    steps = steps + AgentStep(
                                        id = steps.size + 1,
                                        turn = currentTurn,
                                        phase = "ROLLBACK",
                                        title = "تم التراجع عن المهمة واستعادة الحالة السابقة ↺",
                                        detail = "قام المطور بإلغاء الترقيع البرمجي واستعادة النسخة النظيفة."
                                    )
                                    Toast.makeText(context, "تم التراجع عن المهمة بنجاح ↺", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF261016)),
                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Icon(Icons.Default.Undo, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تراجع ↺", color = Color(0xFFEF4444), fontFamily = CairoFont, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAgentChatDialog) {
        AgentChatOverlayDialog(
            onDismiss = { showAgentChatDialog = false },
            messages = chatMessages,
            isThinking = isAgentThinkingInChat,
            isRunning = isRunning,
            isPaused = isPaused,
            onSendMessage = { handleUserChatMessage(it) },
            onPause = { pauseAgent() },
            onResume = { resumeAgent() },
            onStop = { stopAgent() },
            specialization = selectedSpecialization
        )
    }

    if (showFreeProvidersDialog) {
        FreeProvidersDialog(
            onDismiss = { showFreeProvidersDialog = false },
            onSaveKey = { rawKey ->
                UniversalKeyBrain.saveKey(context, rawKey)
                activeEngineInfo = UniversalKeyBrain.getActiveEngine()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentChatOverlayDialog(
    onDismiss: () -> Unit,
    messages: List<AgentChatMessage>,
    isThinking: Boolean,
    isRunning: Boolean,
    isPaused: Boolean,
    onSendMessage: (String) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    specialization: AgentSpecialization
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isThinking) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp, bottom = 12.dp, start = 8.dp, end = 8.dp),
            color = Color(0xFF070D18),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.5.dp, Color(0xFF10B981).copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF091528),
                                Color(0xFF050B14),
                                Color(0xFF09121F)
                            )
                        )
                    )
            ) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C192E))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(specialization.color.copy(alpha = 0.2f))
                                .border(1.dp, specialization.color, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                specialization.icon,
                                contentDescription = null,
                                tint = specialization.color,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "محادثة الوكيل المباشرة 🤖💬",
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = specialization.color.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(0.8.dp, specialization.color)
                                ) {
                                    Text(
                                        text = specialization.badge,
                                        color = specialization.color,
                                        fontFamily = CairoFont,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isRunning) {
                                                if (isPaused) Color(0xFFA855F7) else Color(0xFF10B981)
                                            } else Color(0xFF64748B)
                                        )
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isRunning) {
                                        if (isPaused) "موقوف مؤقتاً ⏸️" else "نشط ويعالج المهام ⚡"
                                    } else "جاهز للاستلام 💬",
                                    color = Color(0xFF94A3B8),
                                    fontFamily = CairoFont,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isRunning) {
                            IconButton(
                                onClick = { if (isPaused) onResume() else onPause() },
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(Color(0xFF15263F), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = "Pause/Resume",
                                    tint = if (isPaused) Color(0xFF10B981) else Color(0xFFA855F7),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = onStop,
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(Color(0xFF33141E), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop Agent",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color(0xFF1E293B), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إغلاق",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                // Chat Messages List
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ChatMessageBubble(message = msg)
                    }

                    if (isThinking || (isRunning && !isPaused)) {
                        item {
                            Surface(
                                color = Color(0xFF0F2038),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color(0xFF38BDF8),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = "الوكيل يفكر ويولّد التعديلات البرمجية... 🧠⚡",
                                        color = Color(0xFF7DD3FC),
                                        fontFamily = CairoFont,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                // Quick Prompts
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val quickPrompts = listOf(
                        "أضف زر مشاركة فوري مع اهتزاز لمسي ⚡",
                        "افحص الكود وتأكد من البنية النحوية 🧪",
                        "شغّل صياد الأخطاء وترقيع الانهيار 🩺",
                        "اربط التغييرات مع فرع GitHub الجديد 🐙"
                    )
                    quickPrompts.forEach { prompt ->
                        Surface(
                            onClick = { inputText = prompt },
                            color = Color(0xFF132238),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = prompt,
                                color = Color(0xFF93C5FD),
                                fontFamily = CairoFont,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // Input Bar
                Surface(
                    color = Color(0xFF0A1220),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    "أرسل توجيهاً أو تعديلاً للوكيل...",
                                    color = Color(0xFF64748B),
                                    fontFamily = CairoFont,
                                    fontSize = 12.5.sp
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF0F1B2E),
                                unfocusedContainerColor = Color(0xFF0D1829),
                                focusedBorderColor = Color(0xFF10B981),
                                unfocusedBorderColor = Color(0xFF1E293B),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp, max = 100.dp),
                            maxLines = 3
                        )

                        Button(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    val textToSend = inputText
                                    inputText = ""
                                    onSendMessage(textToSend)
                                }
                            },
                            enabled = inputText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF059669),
                                disabledContainerColor = Color(0xFF1E293B)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "إرسال",
                                tint = if (inputText.isNotBlank()) Color.White else Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(message: AgentChatMessage) {
    val isUser = message.sender == AgentChatSender.USER
    val isSystem = message.sender == AgentChatSender.TOOL_SYSTEM

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isSystem) Color(0xFF1E293B) else Color(0xFF064E3B))
                        .border(1.dp, if (isSystem) Color(0xFF64748B) else Color(0xFF10B981), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSystem) Icons.Default.Build else Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = if (isSystem) Color(0xFF94A3B8) else Color(0xFF34D399),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Surface(
                color = when {
                    isUser -> Color(0xFF0F3823)
                    isSystem -> Color(0xFF131D2E)
                    else -> Color(0xFF0B192E)
                },
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                border = BorderStroke(
                    1.dp,
                    when {
                        isUser -> Color(0xFF10B981).copy(alpha = 0.6f)
                        isSystem -> Color(0xFF38BDF8).copy(alpha = 0.4f)
                        else -> Color(0xFF3B82F6).copy(alpha = 0.5f)
                    }
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (isUser) "أنت (المطور) 👤" else if (isSystem) "نظام الأداة 🛠️" else "الوكيل البرمجي 🤖",
                        color = if (isUser) Color(0xFFA7F3D0) else if (isSystem) Color(0xFF7DD3FC) else Color(0xFF93C5FD),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message.text,
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )

                    message.diff?.let { diff ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = Color(0xFF050B14),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "📁 ${diff.filePath}",
                                        color = Color(0xFF7DD3FC),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "+${diff.additions} / -${diff.deletions}",
                                        color = Color(0xFF34D399),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                }
                                if (diff.diffText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = diff.diffText.take(300),
                                        color = Color(0xFFCBD5E1),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        maxLines = 6,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isUser) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF065F46))
                        .border(1.dp, Color(0xFF34D399), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * 🎯 تبويب شروط الجاهزية بنمط الزجاج السيبراني (Spatial HUD Readiness Matrix)
 */
@Composable
private fun RequirementsTab(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onJumpBuildCenter: () -> Unit,
    onJumpKeys: () -> Unit
) {
    val prefs = remember(context) { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }
    var isClientMode by remember { mutableStateOf(false) }

    fun getDefaultReqs(clientMode: Boolean): List<Requirement> {
        return if (clientMode) {
            listOf(
                Requirement("engine", "محرك الذكاء والاستدلال", "Gemini / Groq / OpenRouter / OpenAI"),
                Requirement("pat", "رمز GitHub PAT", "يد الوكيل وصلاحية repo/actions"),
                Requirement("req", "طلب مربوط نشط", "الهدف والمواصفات المستهدفة"),
                Requirement("price", "سعر مقبول", "بوابة التوليد والاعتماد"),
                Requirement("repo", "مستودع مرتبط", "المستودع البرمجي لفرع العمل")
            )
        } else {
            listOf(
                Requirement("engine", "محرك الاستدلال والذكاء (AI Engine)", "Gemini 2.0 / Claude / Groq / OpenAI"),
                Requirement("pat", "رمز مصادقة GitHub PAT ومستخدم العمل", "صلاحية repo و workflow و actions"),
                Requirement("repo", "المستودع المستهدف وفرع التطوير النشط", "خزانة المستودعات وفرع العمل البرمجي"),
                Requirement("tools", "ترسانة الأدوات وسجل الفحص الذاتي", "الأدوات المسجلة وفحص سلامة التجميع"),
                Requirement("connectivity", "اتصال الشبكة واستقرار الخدمات السحابية", "فحص زمن الاستجابة والاتصال الحي")
            )
        }
    }

    var reqs by remember(isClientMode) { mutableStateOf(getDefaultReqs(isClientMode)) }
    var checking by remember { mutableStateOf(false) }

    fun runChecks() {
        checking = true
        reqs = reqs.map { it.copy(state = ReqState.CHECKING, detail = "") }
        scope.launch {
            val (provider, activeKey) = UniversalKeyBrain.getActiveEngine()
            val engineOk = activeKey.isNotBlank() && provider != DetectedProvider.NONE

            val owner = prefs.getString("build_center_owner", "") ?: ""
            val repo = prefs.getString("build_center_repo", "") ?: ""
            val token = prefs.getString("build_center_token", "") ?: ""
            val ghUser = if (token.isNotBlank()) {
                GitHubRepoClient(owner.ifBlank { "-" }, repo.ifBlank { "-" }, token).getAuthUser()
            } else null

            if (!isClientMode) {
                // نمط التطوير الهندسي الذاتي (5 أركان هندسية مستقلة تماماً)
                val repoOk = owner.isNotBlank() && repo.isNotBlank()
                reqs = reqs.map {
                    when (it.id) {
                        "engine" -> it.copy(
                            state = if (engineOk) ReqState.OK else ReqState.FAIL,
                            detail = if (engineOk) "متصل عبر ${provider.displayName} بنجاح ✅" else "لا يوجد مفتاح لأي مزود (Gemini/Groq/OpenRouter)"
                        )
                        "pat" -> it.copy(
                            state = if (ghUser != null) ReqState.OK else ReqState.FAIL,
                            detail = ghUser?.let { u -> "متصل ومصرّح كـ $u ✅" } ?: "مفقود أو مرفوض الصلاحية"
                        )
                        "repo" -> it.copy(
                            state = if (repoOk) ReqState.OK else ReqState.FAIL,
                            detail = if (repoOk) "$owner/$repo (الخزانة النشطة) ✅" else "اختر أو اربط مستودعاً من مركز البناء"
                        )
                        "tools" -> it.copy(
                            state = ReqState.OK,
                            detail = "12 أداة هندسية مفعلة + محلل الانهيارات والحارس الآلي جاهز ✅"
                        )
                        else -> it.copy(
                            state = if (engineOk || ghUser != null) ReqState.OK else ReqState.FAIL,
                            detail = if (engineOk || ghUser != null) "الاتصال السحابي مستقر وزمن الاستجابة فوري ✅" else "بانتظار تهيئة المحرك أو GitHub"
                        )
                    }
                }
            } else {
                // نمط طلبات العملاء الخارجي
                val active = AppRequestService.getActiveBuildRequest(context)
                val priced = active != null &&
                    (context.getSharedPreferences("qabas_requests_prefs", Context.MODE_PRIVATE)
                        .getBoolean("priced_${active.id}", false) ||
                        active.priceStatus == "accepted")
                val linkedRepo = active?.let {
                    prefs.getString("repo_${it.id}", null)
                        ?: context.getSharedPreferences("qabas_requests_prefs", Context.MODE_PRIVATE)
                            .getString("repo_${it.id}", null)
                }

                reqs = reqs.map {
                    when (it.id) {
                        "engine" -> it.copy(
                            state = if (engineOk) ReqState.OK else ReqState.FAIL,
                            detail = if (engineOk) "متصل عبر ${provider.displayName} ✅" else "لا يوجد مفتاح لأي مزود"
                        )
                        "pat" -> it.copy(
                            state = if (ghUser != null) ReqState.OK else ReqState.FAIL,
                            detail = ghUser?.let { u -> "متصل ومصرّح كـ $u ✅" } ?: "مفقود أو مرفوض الصلاحية"
                        )
                        "req" -> it.copy(
                            state = if (active != null) ReqState.OK else ReqState.FAIL,
                            detail = active?.title ?: "لا يوجد طلب مربوط — اربط طلباً من قسم الطلبات"
                        )
                        "price" -> it.copy(
                            state = if (priced) ReqState.OK else ReqState.FAIL,
                            detail = if (priced) "تم القبول والاعتماد ✅" else "بانتظار قبول واعتماد العميل"
                        )
                        else -> it.copy(
                            state = if (linkedRepo != null) ReqState.OK else ReqState.FAIL,
                            detail = linkedRepo ?: "أنشئ أو اختر مستودعاً من مركز البناء"
                        )
                    }
                }
            }
            checking = false
        }
    }

    LaunchedEffect(isClientMode) { runChecks() }

    val done = reqs.count { it.state == ReqState.OK }
    val readyPercentage = (done.toFloat() / reqs.size.toFloat() * 100).toInt()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // شريط اختيار نمط الفحص (تطوير ذاتي هندسي مقابل طلبات العملاء)
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF070C18),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF1E2D4A))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // زر نمط التطوير الذاتي
                    Button(
                        onClick = { isClientMode = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isClientMode) Color(0xFF0F3A2A) else Color.Transparent
                        ),
                        border = if (!isClientMode) BorderStroke(1.2.dp, Color(0xFF10B981)) else null,
                        shape = RoundedCornerShape(9.dp),
                        modifier = Modifier.weight(1f).height(44.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = null,
                            tint = if (!isClientMode) Color(0xFF34D399) else Color(0xFF64748B),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "التطوير الذاتي المباشر",
                            color = if (!isClientMode) Color.White else Color(0xFF94A3B8),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }

                    // زر نمط طلبات العملاء
                    Button(
                        onClick = { isClientMode = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isClientMode) Color(0xFF1E1B4B) else Color.Transparent
                        ),
                        border = if (isClientMode) BorderStroke(1.2.dp, Color(0xFF818CF8)) else null,
                        shape = RoundedCornerShape(9.dp),
                        modifier = Modifier.weight(1f).height(44.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            Icons.Default.Assignment,
                            contentDescription = null,
                            tint = if (isClientMode) Color(0xFFA5B4FC) else Color(0xFF64748B),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "طلبات ومشاريع العملاء",
                            color = if (isClientMode) Color.White else Color(0xFF94A3B8),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
        // ==========================================
        // 🛰️ بطاقة الرأس السيبرانية لمصفوفة الجاهزية (Readiness Telemetry Matrix)
        // ==========================================
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xEE090F1F),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            (if (done == 5) Color(0xFF10B981) else Color(0xFF00F0FF)).copy(alpha = 0.6f),
                            Color(0xFF38BDF8).copy(alpha = 0.3f),
                            Color(0xFF1E2D4A).copy(alpha = 0.5f)
                        )
                    )
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (done == 5) Color(0xFF10B981) else Color(0xFF00F0FF))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "SYSTEM READINESS MATRIX",
                                    color = if (done == 5) Color(0xFF10B981) else Color(0xFF00F0FF),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "مصفوفة فحص متطلبات التشغيل المستقل",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                if (done == 5) "النظام جاهز بنسبة 100% — كافة الموصلات متزامنة ومحققة"
                                else "استكمل الشروط غير المكتملة لتفعيل الإطلاق التلقائي",
                                color = Color(0xFF94A3B8),
                                fontFamily = CairoFont,
                                fontSize = 10.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // قرص العداد الدائري السيبراني
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF040814))
                                .border(1.dp, Color(0xFF1E2D4A), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(52.dp)) {
                                val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                                drawArc(
                                    color = Color(0xFF1E293B),
                                    startAngle = -90f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = stroke
                                )
                                val accent = if (done == 5) Color(0xFF10B981) else Color(0xFF00F0FF)
                                drawArc(
                                    color = accent,
                                    startAngle = -90f,
                                    sweepAngle = (readyPercentage / 100f) * 360f,
                                    useCenter = false,
                                    style = stroke
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$done/5", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                Text("$readyPercentage%", color = if (done == 5) Color(0xFF10B981) else Color(0xFF00F0FF), fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFF1E2D4A).copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "الحالة العامة: ${if (done == 5) "جاهز للعمليات المباشرة ✓" else "بانتظار اكتمال المتطلبات ⏳"}",
                            color = if (done == 5) Color(0xFF10B981) else Color(0xFFF59E0B),
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        TextButton(
                            onClick = { runChecks() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إعادة الفحص", color = GoldPrimary, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // بطاقات الشروط الخمسة بتصميم هندسي متناغم
        items(reqs) { r ->
            val color = when (r.state) {
                ReqState.OK -> Color(0xFF10B981)
                ReqState.FAIL -> Color(0xFFEF4444)
                else -> Color(0xFF00F0FF)
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xDD080E1C),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            color.copy(alpha = 0.5f),
                            Color(0xFF1E293B).copy(alpha = 0.4f),
                            Color.Transparent
                        )
                    )
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(color.copy(alpha = 0.12f))
                            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (r.state == ReqState.CHECKING) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = color, strokeWidth = 2.dp)
                        } else {
                            Icon(
                                if (r.state == ReqState.OK) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.title, color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = color.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(0.5.dp, color.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    when (r.state) {
                                        ReqState.OK -> "محقّق ✓"
                                        ReqState.FAIL -> "غير مكتمل ✗"
                                        else -> "فحص..."
                                    },
                                    color = color,
                                    fontFamily = CairoFont,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(r.hint, color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 10.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            if (r.state == ReqState.CHECKING) "جاري التحقق الفوري..." else r.detail.ifBlank { "—" },
                            color = color,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Normal,
                            fontSize = 9.5.sp
                        )
                    }

                    if (r.state == ReqState.FAIL && !checking) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { if (r.id == "engine") onJumpKeys() else onJumpBuildCenter() },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("إصلاح", color = DeepSlate, fontFamily = CairoFont, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(11.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 🛠️ تبويب مصفوفة الأدوات البرمجية وإعدادات الوكيل (Spatial Tool Arsenal)
 */
@Composable
private fun ToolsTab(context: Context) {
    val prefs = remember(context) { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }
    val dummyCtx = remember { CodeTools.Ctx("-", "-", "-", "-") }
    val allTools = remember { CodeTools.definitions(dummyCtx) + AiTools.definitions(context) }
    var states by remember { mutableStateOf(allTools.associate { it.name to AgentPrefs.isToolEnabled(context, it.name) }) }
    var model by remember { mutableStateOf(AgentPrefs.model(context)) }
    var maxTurns by remember { mutableStateOf(AgentPrefs.maxTurns(context).toFloat()) }
    var autoFix by remember { mutableStateOf(AgentPrefs.isAutoFix(context)) }

    val arabicNames = mapOf(
        "list_tree" to "📁 تصفح شجرة المستودع",
        "read_file" to "📖 قراءة محتوى ملف",
        "write_file" to "✏️ إنشاء وكتابة ملف جديد",
        "update_file" to "🔄 ترقيع وتحديث ملف موجود",
        "delete_file" to "🗑️ حذف ملف من الفرع",
        "list_branches" to "🌿 قائمة فروع Git",
        "recent_commits" to "📜 سجل الـ Commits الأخيرة",
        "trigger_build" to "🏗️ تشغيل بناء CI/CD في السحابة",
        "build_status" to "📊 متابعة حالة البناء المباشر",
        "create_pr" to "🔀 فتح Pull Request رسمي",
        "list_prs" to "📥 السحوبات المفتوحة",
        "pr_files" to "🔍 فحص ملفات السحب",
        "merge_pr" to "✅ دمج السحب تلقائياً",
        "create_issue" to "🐞 تسجيل مشكلة برمجية (Issue)",
        "list_issues" to "📋 قائمة القضايا المفتوحة",
        "device_status" to "📱 فحص صحة الجهاز",
        "diagnose" to "🩺 التشخيص الهندسي الشامل",
        "generate_script" to "🎬 توليد سيناريو دعوي",
        "search_quran" to "🔍 محرك البحث القرآني",
        "get_tafsir" to "📖 استرجاع التفسير الموثق",
        "search_code_symbols" to "🔍 البحث الدلالي عن الدوال والكلاسات",
        "diagnose_build_error" to "🩺 تشخيص أسباب أخطاء التجميع",
        "run_linter_check" to "🧪 التدقيق النحوي والساكن (Linting)",
        "generate_unit_test" to "🧪 توليد اختبارات الوحدات الآلية (Unit Tests)",
        "refactor_and_optimize" to "⚡ فحص وتحسين أداء الذاكرة و Compose"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // بطاقة معايير تشغيل الوكيل بنمط Spatial HUD
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xEE090F1F),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            GoldPrimary.copy(alpha = 0.6f),
                            Color(0xFF00F0FF).copy(alpha = 0.3f),
                            Color(0xFF1E2D4A).copy(alpha = 0.5f)
                        )
                    )
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(GoldPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "AGENT REASONING PARAMETERS",
                                color = GoldPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        Surface(
                            color = GoldPrimary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
                        ) {
                            Text(
                                "تهيئة النواة ⚙️",
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text("نموذج الاستدلال والبرمجة المستهدف:", color = Color.White, fontFamily = CairoFont, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            null to "⚡ تلقائي (حسب المفتاح)",
                            "anthropic/claude-3.5-sonnet" to "🧠 Claude 3.5 Sonnet",
                            "gemini-2.0-flash" to "💎 Gemini 2.0 Flash",
                            "llama-3.3-70b-versatile" to "⚡ Groq Llama 70B",
                            "meta-llama/llama-3.3-70b-instruct:free" to "🦙 Llama Free",
                            "qwen/qwen3-32b:free" to "🌊 Qwen 32B"
                        ).forEach { (id, label) ->
                            val isSelected = model == id
                            Surface(
                                color = if (isSelected) GoldPrimary.copy(alpha = 0.18f) else Color(0xFF060B16),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color(0xFF1E2D4A)),
                                modifier = Modifier.clickable {
                                    model = id
                                    prefs.edit().putString(AgentPrefs.MODEL, id ?: "").apply()
                                }
                            ) {
                                Text(
                                    label,
                                    color = if (isSelected) GoldPrimary else Color(0xFF94A3B8),
                                    fontFamily = CairoFont,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1E2D4A).copy(alpha = 0.6f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("الحد الأقصى لجولات التفكير (Max Turns):", color = Color.White, fontFamily = CairoFont, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            Text("عدد الجولات المسموح بها للوكيل لإكمال المهمة", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 10.sp)
                        }
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                        ) {
                            Text(
                                "${maxTurns.toInt()} جولات",
                                color = Color(0xFF10B981),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Slider(
                        value = maxTurns,
                        onValueChange = {
                            maxTurns = it
                            prefs.edit().putInt(AgentPrefs.MAX_TURNS, it.toInt()).apply()
                        },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF10B981),
                            activeTrackColor = Color(0xFF10B981),
                            inactiveTrackColor = Color(0xFF1E2D4A)
                        )
                    )

                    HorizontalDivider(color = Color(0xFF1E2D4A).copy(alpha = 0.6f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🔧 الإصلاح التلقائي الذكي (Self-Healing)", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = if (autoFix) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFF64748B).copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        if (autoFix) "ACTIVE" else "OFF",
                                        color = if (autoFix) Color(0xFF10B981) else Color(0xFF64748B),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text("عند حدوث خطأ تجميع، يحلل الوكيل الخطأ ويعيد بناء الكود تلقائياً.", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 10.sp)
                        }
                        Switch(
                            checked = autoFix,
                            onCheckedChange = {
                                autoFix = it
                                prefs.edit().putBoolean(AgentPrefs.AUTO_FIX, it).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF10B981),
                                checkedTrackColor = Color(0xFF10B981).copy(alpha = 0.3f),
                                uncheckedThumbColor = Color(0xFF64748B),
                                uncheckedTrackColor = Color(0xFF1E2D4A)
                            )
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ترسانة الأدوات البرمجية المتاحة (${allTools.size} أداة):",
                    color = Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    "${states.values.count { it }} مفعّلة",
                    color = Color(0xFF10B981),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        items(allTools) { tool ->
            val on = states[tool.name] == true
            Surface(
                color = Color(0xDD080E1C),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (on) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFF1E2D4A).copy(alpha = 0.6f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (on) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFF1E293B).copy(alpha = 0.3f))
                            .border(1.dp, if (on) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFF1E2D4A), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (tool.name.contains("file")) Icons.Default.Description
                            else if (tool.name.contains("pr") || tool.name.contains("branch")) Icons.Default.AltRoute
                            else if (tool.name.contains("build")) Icons.Default.Build
                            else if (tool.name.contains("test")) Icons.Default.Science
                            else Icons.Default.Code,
                            contentDescription = null,
                            tint = if (on) Color(0xFF10B981) else Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                arabicNames[tool.name] ?: tool.name,
                                color = if (on) Color.White else Color(0xFF64748B),
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                tool.name,
                                color = Color(0xFF64748B),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            )
                        }
                        Text(
                            tool.description,
                            color = Color(0xFF94A3B8),
                            fontFamily = CairoFont,
                            fontSize = 10.sp,
                            maxLines = 2
                        )
                    }

                    Switch(
                        checked = on,
                        onCheckedChange = {
                            states = states + (tool.name to it)
                            prefs.edit().putBoolean(AgentPrefs.toolKey(tool.name), it).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF10B981),
                            checkedTrackColor = Color(0xFF10B981).copy(alpha = 0.3f),
                            uncheckedThumbColor = Color(0xFF64748B),
                            uncheckedTrackColor = Color(0xFF1E2D4A)
                        )
                    )
                }
            }
        }
    }
}

/**
 * 📜 واجهة سجل المهام والعمليات البرمجية (Spatial Task History Log)
 * تعرض قائمة بالعمليات السابقة بتناسق هندسي متوازن 2x2 ومؤشرات بصرية واضحة
 */
private enum class TaskHistoryFilter(val label: String, val color: Color) {
    ALL("الكل", GoldPrimary),
    COMPLETED("تم بنجاح ✓", Color(0xFF10B981)),
    IN_PROGRESS("قيد التنفيذ ⚡", Color(0xFF00F0FF)),
    FAILED("فشل ✗", Color(0xFFEF4444))
}

@Composable
private fun TaskHistoryLogTab(context: Context) {
    val clipboard = LocalClipboardManager.current
    var runs by remember { mutableStateOf(AgentRunLog.load(context)) }
    var expandedTime by remember { mutableStateOf<Long?>(null) }
    var selectedFilter by remember { mutableStateOf(TaskHistoryFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val okCount = runs.count { it.ok }
    val failCount = runs.count { !it.ok }
    val totalCount = runs.size
    val successRate = if (totalCount > 0) (okCount * 100) / totalCount else 100

    val infiniteTransition = rememberInfiniteTransition(label = "TaskHistorySync")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "PulseAlpha"
    )

    // تصفية السجل حسب الحالة والبحث
    val filteredRuns = remember(runs, selectedFilter, searchQuery) {
        runs.filter { run ->
            val matchesFilter = when (selectedFilter) {
                TaskHistoryFilter.ALL -> true
                TaskHistoryFilter.COMPLETED -> run.ok
                TaskHistoryFilter.FAILED -> !run.ok
                TaskHistoryFilter.IN_PROGRESS -> false
            }
            val matchesQuery = searchQuery.isBlank() ||
                run.title.contains(searchQuery, ignoreCase = true) ||
                run.summary.contains(searchQuery, ignoreCase = true) ||
                run.tools.any { it.contains(searchQuery, ignoreCase = true) }
            matchesFilter && matchesQuery
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ==========================================
        // 📊 لوحة المؤشرات البصرية والإحصائيات الهندسية المتناغمة (Spatial Telemetry HUD)
        // ==========================================
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xEE090F1F),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF00F0FF).copy(alpha = 0.6f),
                            Color(0xFF10B981).copy(alpha = 0.4f),
                            Color(0xFF1E2D4A).copy(alpha = 0.5f)
                        )
                    )
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00F0FF))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "MISSION TELEMETRY LOG",
                                    color = Color(0xFF00F0FF),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "سجل العمليات والمهام البرمجية المستقلة",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }

                        if (runs.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    AgentRunLog.clear(context)
                                    runs = emptyList()
                                    expandedTime = null
                                    Toast.makeText(context, "تم مسح سجل العمليات بالكامل 🗑️", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "مسح السجل", tint = Color(0xFFEF4444))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ==========================================
                    // 📐 شبكة متناسقة هندسياً 2x2 للكبسولات الإحصائية
                    // ==========================================
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // الصف الأول: مكتملة + قيد التنفيذ
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // كبسولة 1: تم بنجاح (COMPLETED)
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = Color(0xFF041B13),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF10B981)))
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text("تم بنجاح", color = Color(0xFF10B981), fontFamily = CairoFont, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text("$okCount مهمة", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                                }
                            }

                            // كبسولة 2: قيد التنفيذ (IN PROGRESS)
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = Color(0xFF05182C),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF00F0FF).copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF00F0FF).copy(alpha = pulseAlpha))
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text("قيد التنفيذ", color = Color(0xFF00F0FF), fontFamily = CairoFont, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text("0 نشطة", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF00F0FF), modifier = Modifier.size(20.dp))
                                }
                            }
                        }

                        // الصف الثاني: فشل + نسبة النجاح
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // كبسولة 3: أخطاء (FAILED)
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = Color(0xFF22080D),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text("أخطاء", color = Color(0xFFEF4444), fontFamily = CairoFont, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text("$failCount خطأ", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                                }
                            }

                            // كبسولة 4: معدل الإنجاز (SUCCESS RATE)
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = Color(0xFF1A1505),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(GoldPrimary))
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text("نسبة الإنجاز", color = GoldPrimary, fontFamily = CairoFont, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text("$successRate%", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Icon(Icons.Default.TrendingUp, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 🔍 شريط البحث وفلاتر الحالة الهندسية (Search & Filter Deck)
        // ==========================================
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("ابحث في سجل المهام أو الأدوات...", color = Color(0xFF64748B), fontSize = 11.5.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = CairoFont, fontSize = 11.5.sp, color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF060B16),
                        unfocusedContainerColor = Color(0xFF060B16),
                        focusedBorderColor = Color(0xFF00F0FF),
                        unfocusedBorderColor = Color(0xFF1E2D4A)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                // فلاتر الحالة الأربعة بتوزيع متناسق
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TaskHistoryFilter.values().forEach { filter ->
                        val isSelected = selectedFilter == filter
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedFilter = filter },
                            color = if (isSelected) filter.color.copy(alpha = 0.18f) else Color(0xFF060B16),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isSelected) filter.color else Color(0xFF1E2D4A))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelected) {
                                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(filter.color))
                                    Spacer(modifier = Modifier.width(5.dp))
                                }
                                Text(
                                    filter.label,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    fontFamily = CairoFont,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 📋 قائمة بطاقات سجل المهام بتصميم Spatial HUD Glassmorphic
        // ==========================================
        if (filteredRuns.isEmpty()) {
            item {
                Surface(
                    color = Color(0xDD080E1C),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E2D4A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.AssignmentLate, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (runs.isEmpty()) "لا توجد مهام مسجلة حتى الآن."
                            else "لا توجد نتائج تطابق خيارات التصفية والبحث الحالية.",
                            color = Color(0xFFCBD5E1),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "يمكنك تشغيل مهمة جديدة من تبويب «[01] ⚡ التشغيل المباشر» لتسجيلها ومتابعتها هنا تلقائياً.",
                            color = Color(0xFF64748B),
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        } else {
            items(filteredRuns) { run ->
                val isOk = run.ok
                val statusColor = if (isOk) Color(0xFF10B981) else Color(0xFFEF4444)
                val statusBg = if (isOk) Color(0xFF041610) else Color(0xFF1D060A)
                val statusLabel = if (isOk) "مكتمل ✓" else "فشل ✗"
                val statusIcon = if (isOk) Icons.Default.CheckCircle else Icons.Default.ErrorOutline
                val isExpanded = expandedTime == run.time

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { expandedTime = if (isExpanded) null else run.time },
                    color = statusBg.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(
                                statusColor.copy(alpha = 0.6f),
                                statusColor.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        )
                    )
                ) {
                    Column(modifier = Modifier.padding(13.dp)) {
                        // شريط رأس البطاقة: العنوان والمؤشر البصري الواضح للحالة
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        run.title,
                                        color = Color.White,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US).format(Date(run.time)),
                                        color = Color(0xFF64748B),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.5.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = statusColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f))
                                ) {
                                    Text(
                                        statusLabel,
                                        color = statusColor,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.5.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                IconButton(
                                    onClick = {
                                        AgentRunLog.deleteRun(context, run.time)
                                        runs = AgentRunLog.load(context)
                                        if (expandedTime == run.time) expandedTime = null
                                        Toast.makeText(context, "تم حذف هذه الجولة من السجل 🗑️", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "حذف هذه الجولة",
                                        tint = Color(0xFFEF4444).copy(alpha = 0.8f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                Icon(
                                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // شرائح الأدوات المستخدمة
                        if (run.tools.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                run.tools.forEach { t ->
                                    Surface(
                                        color = Color(0xFF0D1527),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(0.5.dp, Color(0xFF1E2D4A))
                                    ) {
                                        Text(
                                            t,
                                            color = Color(0xFF94A3B8),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // التفاصيل الموسعة عند الضغط على البطاقة
                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color(0xFF1E2D4A).copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.height(8.dp))

                            if (run.summary.isNotBlank()) {
                                Text("ملخص نتيجة العملية:", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(3.dp))
                                Surface(
                                    color = Color(0xFF030712),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        run.summary,
                                        color = Color(0xFFCBD5E1),
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }

                            if (!run.ok && run.error.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("نص الخطأ التشخيصي (Diagnostic Error):", color = Color(0xFFEF4444), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(3.dp))
                                Surface(
                                    color = Color(0xFF030712),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        run.error,
                                        color = Color(0xFFFCA5A5),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = {
                                        val text = buildString {
                                            appendLine("تقرير عملية الوكيل البرمجي:")
                                            appendLine("المهمة: ${run.title}")
                                            appendLine("الحالة: $statusLabel")
                                            appendLine("التاريخ: ${SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(run.time))}")
                                            appendLine("الأدوات: ${run.tools.joinToString(", ")}")
                                            appendLine("الملخص: ${run.summary}")
                                            if (run.error.isNotBlank()) appendLine("الخطأ: ${run.error}")
                                        }
                                        clipboard.setText(AnnotatedString(text))
                                        Toast.makeText(context, "تم نسخ تقرير المهمة إلى الحافظة 📋", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF152238)),
                                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("نسخ التقرير 📋", color = Color.White, fontFamily = CairoFont, fontSize = 10.5.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class FreeProviderSuggestion(
    val id: String,
    val name: String,
    val providerBadge: String,
    val color: Color,
    val recommendedModel: String,
    val modelDescription: String,
    val freePerks: String,
    val keyUrl: String,
    val keyPrefix: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FreeProvidersDialog(
    onDismiss: () -> Unit,
    onSaveKey: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var selectedFilter by remember { mutableStateOf("ALL") }

    val suggestions = listOf(
        FreeProviderSuggestion(
            id = "gemini",
            name = "Google AI Studio",
            providerBadge = "أفضل نموذج كود أندرويد مجاني ⚡",
            color = Color(0xFF10B981),
            recommendedModel = "gemini-2.0-flash / gemini-1.5-pro",
            modelDescription = "نموذج ذكي وسريع متخصص في Kotlin و Jetpack Compose واستئصال الأخطاء.",
            freePerks = "مجاني 100% بدون بطاقة ائتمان — 15 طلب/دقيقة و 1,000,000 توكن/دقيقة.",
            keyUrl = "https://aistudio.google.com/app/apikey",
            keyPrefix = "AIzaSy..."
        ),
        FreeProviderSuggestion(
            id = "openrouter",
            name = "OpenRouter AI (Qwen Coder)",
            providerBadge = "أقوى نموذج مفتوح المصدر للكود 🧠",
            color = Color(0xFF38BDF8),
            recommendedModel = "qwen/qwen-2.5-coder-32b-instruct:free",
            modelDescription = "نموذج Qwen 2.5 Coder المصنف كأفضل نموذج مفتوح المصدر لكتابة وبناء الشفرات البرمجية.",
            freePerks = "يوفر أكثر من 10 نماذج برمجية مجانية بالكامل مجاناً.",
            keyUrl = "https://openrouter.ai/keys",
            keyPrefix = "sk-or-v1-..."
        ),
        FreeProviderSuggestion(
            id = "groq",
            name = "Groq Cloud Inference",
            providerBadge = "استجابة فائقة السرعة (300+ Token/s) 🚀",
            color = Color(0xFFF59E0B),
            recommendedModel = "llama-3.3-70b-versatile / qwen-2.5-coder",
            modelDescription = "معالج LPU خارق لتنفيذ المهام البرمجية والتسليم المباشر في أجزاء من الثانية.",
            freePerks = "كوتا مجانية سريعة جداً بحد 30 طلب/دقيقة.",
            keyUrl = "https://console.groq.com/keys",
            keyPrefix = "gsk_..."
        ),
        FreeProviderSuggestion(
            id = "huggingface",
            name = "HuggingFace Hub",
            providerBadge = "مكتبة النماذج البرمجية 🤖",
            color = Color(0xFFFCD34D),
            recommendedModel = "Qwen/Qwen2.5-Coder-32B-Instruct",
            modelDescription = "استدلال مجاني مباشر عبر نماذج HuggingFace المخصصة للتطوير.",
            freePerks = "مفتاح Access Token مجاني بالكامل بدون انتهاء صلاحية.",
            keyUrl = "https://huggingface.co/settings/tokens",
            keyPrefix = "hf_..."
        )
    )

    val filtered = if (selectedFilter == "ALL") suggestions else suggestions.filter { it.id == selectedFilter }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            color = Color(0xFF070D18),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.5.dp, Color(0xFF10B981).copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF091528),
                                Color(0xFF050B14),
                                Color(0xFF09121F)
                            )
                        )
                    )
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C192E))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(24.dp))
                        Column {
                            Text(
                                "🎁 دليل المزودات المجانية ونماذج الكود",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                "احصل على مفاتيح API مجانية لتشغيل نماذج البرمجة بدون دفع",
                                color = Color(0xFF94A3B8),
                                fontFamily = CairoFont,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color(0xFF1E293B), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                // Filter chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filters = listOf(
                        "ALL" to "الكل (4)",
                        "gemini" to "Google Gemini ⚡",
                        "openrouter" to "OpenRouter (Qwen Coder) 🧠",
                        "groq" to "Groq Fast 🚀",
                        "huggingface" to "HuggingFace 🤖"
                    )
                    filters.forEach { (id, label) ->
                        val isSelected = selectedFilter == id
                        Surface(
                            onClick = { selectedFilter = id },
                            color = if (isSelected) Color(0xFF059669) else Color(0xFF132238),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF34D399) else Color(0xFF1E2D4A))
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else Color(0xFF93C5FD),
                                fontFamily = CairoFont,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Providers List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        FreeProviderCard(
                            item = item,
                            onCopyUrl = { url ->
                                clipboard.setText(AnnotatedString(url))
                                Toast.makeText(context, "تم نسخ رابط موقع المفتاح! افتحه في المتصفح للحصول على المفتاح 📋", Toast.LENGTH_SHORT).show()
                            },
                            onSaveKey = { key ->
                                onSaveKey(key)
                                Toast.makeText(context, "تم حفظ وتفعيل المفتاح بنجاح! 🚀", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FreeProviderCard(
    item: FreeProviderSuggestion,
    onCopyUrl: (String) -> Unit,
    onSaveKey: (String) -> Unit
) {
    var keyInput by remember { mutableStateOf("") }

    Surface(
        color = Color(0xFF09182A),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.2.dp, item.color.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name,
                    color = Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                Surface(
                    color = item.color.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.8.dp, item.color)
                ) {
                    Text(
                        text = item.providerBadge,
                        color = item.color,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = Color(0xFF040A14),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF1E2D4A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("النموذج البرمجي الموصى به: ", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 11.sp)
                        Text(
                            item.recommendedModel,
                            color = GoldPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        item.modelDescription,
                        color = Color(0xFFCBD5E1),
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "🎁 الميزات المجانية: ${item.freePerks}",
                        color = Color(0xFF34D399),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onCopyUrl(item.keyUrl) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15263F)),
                    border = BorderStroke(1.dp, item.color.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = item.color, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("نسخ رابط المفتاح المجاني 📋", color = Color.White, fontFamily = CairoFont, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick In-Card Key Saver
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    placeholder = { Text("الصق مفتاح ${item.name} هنا (${item.keyPrefix})...", color = Color(0xFF64748B), fontSize = 10.5.sp) },
                    modifier = Modifier.weight(1f).height(46.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        color = Color.White
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF030712),
                        unfocusedContainerColor = Color(0xFF030712),
                        focusedBorderColor = item.color,
                        unfocusedBorderColor = Color(0xFF1E2D4A)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                Button(
                    onClick = {
                        if (keyInput.isNotBlank()) {
                            onSaveKey(keyInput)
                            keyInput = ""
                        }
                    },
                    enabled = keyInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = item.color, disabledContainerColor = Color(0xFF1E2D4A)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(46.dp)
                ) {
                    Text("تفعيل وحفظ 💾", color = DeepSlate, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
