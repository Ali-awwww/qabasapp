package com.qabas.app

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.UUID

/**
 * نماذج الوكيل الذكي الموحدة (Agent Models):
 * كل الـ enums والـ data classes الخاصة بالوكيل في مكان واحد —
 * الدمج لتخفيف التشتت، والحزمة نفسها فلا حاجة لأي import جديد.
 */

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
    OPENROUTER("openrouter", "OpenRouter AI Gateway", "sk-or-", "anthropic/claude-sonnet-4.5", Color(0xFF38BDF8)),
    GEMINI("gemini", "Google Gemini 3.5 Flash", "AIza", "gemini-3.5-flash", Color(0xFF00E5FF)),
    GROQ("groq", "Groq Lightning Llama", "gsk_", "llama-3.3-70b-versatile", Color(0xFFF59E0B)),
    OPENAI("openai", "OpenAI GPT-4o", "sk-", "gpt-4o", Color(0xFF10B981)),
    ANTHROPIC("anthropic", "Anthropic Claude", "sk-ant-", "claude-3-5-sonnet-20241022", Color(0xFFA855F7)),
    HUGGINGFACE("huggingface", "HuggingFace Hub", "hf_", "Qwen/Qwen2.5-Coder-32B", Color(0xFFFCD34D)),
    OPENCODE("opencode", "OpenCode Zen (125 نموذجاً)", "oc-", "big-pickle", Color(0xFF7C3AED)),
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

data class RealAgentResponse(
    val text: String,
    val totalTokens: Int,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0
)

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

enum class AgentTaskStatus(val label: String, val color: Color) {
    PLANNING("جاري التفكير والتخطيط 🧠", Color(0xFF38BDF8)),
    INSPECTING("فحص شجرة وملفات الكود 🔍", Color(0xFFF59E0B)),
    WRITING("صياغة وتعديل الكود ✍️", Color(0xFFA855F7)),
    BUILDING("تشغيل البناء السحابي 🚀", Color(0xFF00E5FF)),
    CANCELLED("أُلغيت المهمة ⏹️", Color(0xFFF59E0B)),
    COMPLETED("اكتمل التنفيذ بنجاح ✅", Color(0xFF10B981)),
    FAILED("تعذر الإكمال ❌", Color(0xFFEF4444))
}

data class AgentExecutionStep(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val toolName: String = "",
    val outputSnippet: String = "",
    val isRunning: Boolean = false,
    val isSuccess: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

data class AgentMissionSession(
    val id: String = UUID.randomUUID().toString().take(8),
    val userPrompt: String,
    val specialization: AgentSpecialization,
    val status: AgentTaskStatus,
    val steps: List<AgentExecutionStep> = emptyList(),
    val finalReport: String = "",
    val modifiedFiles: List<String> = emptyList(),
    val proposedDiffs: List<FileDiff> = emptyList(),
    val appliedPaths: List<String> = emptyList(),
    val buildTriggered: Boolean = false,
    val apkUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
