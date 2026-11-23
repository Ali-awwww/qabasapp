package com.qabas.app

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
 * مزودو نماذج الذكاء الاصطناعي المدعومون مع الكشف التلقائي الذكي.
 *
 * [models] هو مصدر أسماء النماذج السحابية الوحيد (12.x): أُلغيت القائمة
 * المتوازية agentModelsFor من AiStudioAgentScreen ودُمجت هنا — النموذج
 * الافتراضي أولاً ثم أسماء القائمة القديمة. أسماء لم يُتحقق من وجودها
 * حياً في هذا الفحص تُبقى في القائمة لكنها غير موثّقة (انظر تقرير التسليم):
 * o1-preview، llama-3.3-70b-versatile،
 * llama-3.1-8b-instant، mixtral-8x7b، claude-3.5-sonnet، claude-3.5-haiku،
 * claude-3-5-sonnet-20241022، gemini-2.5-flash، gemini-3.5-pro،
 * Qwen/Qwen2.5-Coder-32B، meta-llama/Llama-3.3-70B-Instruct،
 * big-pickle، opencode/grok-code.
 * حُذفا من قائمة OpenRouter بعد فحص حي 2026-11-23 (القائمة العامة 476
 * نموذجاً لا تحويهما): anthropic/claude-3.5-sonnet وmeta-llama/llama-3.3-70b
 * (استُبدل الثاني بالصيغة الموثقة meta-llama/llama-3.3-70b-instruct).
 * الموثقة حياً: anthropic/claude-sonnet-4.5، openai/gpt-4o،
 * openai/gpt-4o-mini، meta-llama/llama-3.3-70b-instruct،
 * gemini-3.8-flash، gemini-3.5-flash (قائمة OpenRouter العامة 2026-11-22
 * ووثائق Google Cloud).
 */
enum class DetectedProvider(
    val id: String,
    val displayName: String,
    val prefixHint: String,
    val defaultModel: String,
    val color: Color,
    val models: List<String> = listOf(defaultModel)
) {
    OPENROUTER("openrouter", "OpenRouter AI Gateway", "sk-or-", "anthropic/claude-sonnet-4.5", Color(0xFF38BDF8),
        listOf("anthropic/claude-sonnet-4.5", "openai/gpt-4o", "meta-llama/llama-3.3-70b-instruct")),
    GEMINI("gemini", "Google Gemini 2.5 Flash", "AIza / Aq", "gemini-2.5-flash", Color(0xFF00E5FF),
        listOf("gemini-2.5-flash", "gemini-3.8-flash", "gemini-3.5-flash", "gemini-3.5-pro")),
    GROQ("groq", "Groq Lightning Llama", "gsk_", "llama-3.3-70b-versatile", Color(0xFFF59E0B),
        listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b")),
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
    val completionTokens: Int = 0,
    /**
     * true عندما تكون أعداد الرموز تقديرية لا حقيقية من المزود
     * (بثّ بلا usage، أو مسار Firebase الذي يعيد 500/250/250 ثابتة) —
     * (12.14) هذه الاستدعاءات تبقى «غير مقاسة» للتكلفة مهما كان سعر
     * النموذج معروفاً.
     */
    val tokensEstimated: Boolean = false
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

data class AgentPlanStep(
    val number: Int,
    val title: String,
    val description: String,
    val targetFiles: List<String> = emptyList()
)

data class AgentPlanCardData(
    val prompt: String,
    val summary: String,
    val steps: List<AgentPlanStep>,
    var isApproved: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

enum class AgentChatSender {
    USER, AGENT, TOOL_SYSTEM
}

data class AgentErrorDetails(
    val reason: String,
    val responseCode: Int? = null,
    val cloudStatus: String? = null,
    val isNetworkError: Boolean = false,
    val connectivitySpeed: String? = null
)

data class AgentChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: AgentChatSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val step: AgentStep? = null,
    val diff: FileDiff? = null,
    val isStreaming: Boolean = false,
    val planCard: AgentPlanCardData? = null,
    val teamReport: MultiModelRunReport? = null,
    val teamSteps: List<TeamStepEvent> = emptyList(),
    val errorInfo: AgentErrorDetails? = null
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
