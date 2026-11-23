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
 * الافتراضي أولاً ثم أسماء القائمة القديمة. لا يُعرض هنا إلا نموذج
 * موثق الاستخدام في الكود: gemini-2.5-flash (الافتراضي)،
 * gemini-2.5-pro، gemini-2.0-flash. حُذفت أسماء غير موجودة
 * كانت تظهر في منتقي الإعدادات.
 * حُذفا من قائمة OpenRouter بعد فحص حي 2026-11-23 (القائمة العامة 476
 * نموذجاً لا تحويهما): anthropic/claude-3.5-sonnet وmeta-llama/llama-3.3-70b
 * (استُبدل الثاني بالصيغة الموثقة meta-llama/llama-3.3-70b-instruct).
 * الموثقة حياً: anthropic/claude-sonnet-4.5، openai/gpt-4o،
 * openai/gpt-4o-mini، meta-llama/llama-3.3-70b-instruct.
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
        listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash")),
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
    val color: Color,
    /** تعليمات نظام مفصّلة لهذا التخصص تُرسل للنموذج مع كل مهمة (المرحلة 3). */
    val systemPrompt: String
) {
    TURBO(
        "turbo", "⚡ التيربو السريع", "تنفيذ سريع ومباشر لتعديلات الواجهة والوظائف البسيطة",
        Icons.Default.Bolt, "TURBO ⚡", Color(0xFF38BDF8),
        "نفّذ المطلوب مباشرة بأقل تعديل ممكن، بلا تخطيط مطوّل وبلا أسئلة إضافية. ركّز على التعديلات الصغيرة وتعديلات الواجهة، وإن كان الطلب كبيراً قسّمه لأصغر خطوة قابلة للتنفيذ وابدأ بها."
    ),
    ARCHITECT(
        "architect", "🧠 المعماري الشامل", "تحليل معماري مسبق، تقسيم المهام وتوليد اختبارات آلية",
        Icons.Default.Psychology, "ARCHITECT 🧠", Color(0xFFA855F7),
        "حلّل المطلوب أولاً ثم اكتب خطة عمل واضحة قبل أي كود: قسّم المهمة إلى خطوات مرتبة، حدّد الملفات المستهدفة بالتعديل أو الإنشاء لكل خطوة، وولّد اختبارات للمنطق الجديد. لا تكتب كوداً قبل عرض الخطة."
    ),
    BUG_HUNTER(
        "bug_hunter", "🩺 صياد ومصلح الأخطاء", "تشخيص وتحليل أسباب الانهيارات وأخطاء التجميع والترقيع الفوري",
        Icons.Default.Healing, "DEBUGGER 🩺", Color(0xFFEF4444),
        "شخّص السبب الجذري للخطأ أولاً قبل أي إصلاح: اقرأ رسالة الخطأ والتتبع كاملاً، حدّد الملف والسطر المسؤول، واشرح السبب بجملة واحدة. أصلح السبب نفسه لا العرض، ثم تحقق من أن الإصلاح يزيل الخطأ فعلاً ولا يكسر شيئاً آخر."
    ),
    CODE_REVIEWER(
        "code_reviewer", "🛡️ مدقق ومحسّن الجودة", "تدقيق الكود وفق معايير Clean Architecture و Compose M3",
        Icons.Default.VerifiedUser, "AUDITOR 🛡️", Color(0xFF10B981),
        "راجع الكود وفق مبادئ Clean Architecture وMaterial 3 في Compose: اذكر المخالفات أولاً مرتبة بالأهمية (فصل الطبقات، أسماء واضحة، بلا كود مكرر، بلا قيم وهمية)، ثم أصلحها بأقل تغيير ممكن مع شرح كل إصلاح بجملة واحدة."
    )
}

/**
 * كشف تخصص الوكيل تلقائياً من نص طلب المستخدم (المرحلة 3) — محلي ومجاني، بلا استدعاء API.
 *
 * الأولويات (بالترتيب، أول مطابقة تفوز):
 * 1. الخطأ/العطل/الانهيار → BUG_HUNTER (تشخيص السبب الجذري قبل الإصلاح).
 * 2. المراجعة/التدقيق/الجودة → CODE_REVIEWER.
 * 3. البناء الكبير/الميزة الجديدة → ARCHITECT.
 * 4. الافتراضي → TURBO (تنفيذ مباشر سريع).
 *
 * تشمل الكلمات المفتاحية الاختصارات السريعة الجاهزة («افحص الكود» → CODE_REVIEWER،
 * «صياد الأخطاء» → BUG_HUNTER).
 */
fun detectSpecialization(prompt: String): AgentSpecialization {
    val text = prompt.lowercase()
    val bugKeywords = listOf(
        "خطأ", "عطل", "انهيار", "يتعطل", "تعطل", "فشل", "يفشل",
        "فشل البناء", "فشل التجميع", "لا يعمل", "لا يشتغل", "لا يفتح",
        "صياد الأخطاء", "صيد الأخطاء", "أصلح الخطأ", "إصلاح الخطأ", "تصليح",
        "crash", "error", "exception", "bug", "broken", "fails", "failed",
        "not working", "stacktrace", "stack trace"
    )
    if (bugKeywords.any { it in text }) return AgentSpecialization.BUG_HUNTER
    val reviewKeywords = listOf(
        "راجع", "مراجعة", "تدقيق", "دقق", "جودة", "حسّن الكود", "تحسين الكود",
        "افحص الكود", "افحص", "فحص", "نظّف الكود", "تنظيف الكود",
        "review", "audit", "quality", "clean"
    )
    if (reviewKeywords.any { it in text }) return AgentSpecialization.CODE_REVIEWER
    val architectKeywords = listOf(
        "ابنِ", "ابني", "أنشئ", "انشئ", "صمّم", "صمم", "ميزة جديدة", "قسم جديد",
        "تطبيق كامل", "مشروع جديد", "أضف قسم", "أضف ميزة", "خطة", "معماري",
        "build", "create", "design", "new feature", "architecture", "scaffold"
    )
    if (architectKeywords.any { it in text }) return AgentSpecialization.ARCHITECT
    return AgentSpecialization.TURBO
}

/**
 * أنواع النماذج (المرحلة 4): نظام يربط نوع النموذج بالمهمة وبسيناريو مختلف.
 * كل نوع له دور وسيناريو يُعرضان في الواجهة.
 *
 * قيد موثّق: المهمة تحسم المزود من المفاتيح المخزنة
 * (AgentConnectivityEngine.resolveKeyWithSource: ‏Gemini ثم OpenRouter ثم Groq)،
 * وموجّه السرعة AiModelLatencyRouter لا يصل لمسار التنفيذ أصلاً — لذا يختار
 * النوعُ النموذجَ داخل المزود النشط، لا المزود نفسه. تبديل المزود حسب النوع
 * يتطلب إعادة كتابة تحسم المزود في المهمة — خارج نطاق هذه المرحلة.
 */
enum class AgentModelType(
    val id: String,
    val title: String,
    val description: String,
    val scenario: String,
    val icon: ImageVector,
    val color: Color
) {
    FAST(
        "fast", "السريع",
        "للتعديلات الصغيرة والأسئلة — Groq أو Gemini Flash",
        "تنفيذ مباشر بلا تخطيط مطوّل",
        Icons.Default.Bolt, Color(0xFF38BDF8)
    ),
    POWERFUL(
        "powerful", "القوي",
        "للمهام الكبيرة والبناء والمراجعة الدقيقة — Gemini Pro أو Llama 70B",
        "تخطيط أولاً ثم تنفيذ واختبارات",
        Icons.Default.Psychology, Color(0xFFA855F7)
    ),
    ON_DEVICE(
        "on_device", "على الجهاز",
        "يعمل دون إنترنت بنموذج GGUF محلي مثبّت",
        "تخطيط مختصر وتنفيذ محلي محدود",
        Icons.Default.Memory, Color(0xFF10B981)
    ),
    TEAM(
        "team", "الفريق",
        "للمهام المعقدة متعددة الأجزاء — قائد + مساعدون من أنواع مختلفة",
        "القائد يقسّم المهمة، الأعضاء ينفذون بالتوازي، ثم دمج ومراجعة",
        Icons.Default.Groups, Color(0xFFF59E0B)
    )
}

/** هل يبدو الطلب مهمة معقدة متعددة الأجزاء (مرشحة للفريق)؟ */
private fun isComplexPrompt(prompt: String): Boolean {
    val text = prompt.lowercase()
    if (prompt.length > 180) return true
    val multiPartKeywords = listOf(
        "عدة أقسام", "أقسام متعددة", "متعدد الأجزاء", "خطوات متعددة",
        "نظام كامل", "تطبيق كامل", "عدة مهام",
        "multi-part", "multiple parts", "multiple steps"
    )
    return multiPartKeywords.any { it in text }
}

/**
 * اختيار نوع النموذج تلقائياً حسب المهمة (المرحلة 4) — يستند إلى كشف
 * التخصص في المرحلة 3. يعيد النوع + سبب الاختيار لعرضه في المحادثة.
 *
 * الأولويات: دون اتصال → على الجهاز؛ خطأ/تعديل صغير → السريع؛
 * مراجعة → القوي؛ بناء كبير → الفريق إن كانت المهمة معقدة وفريق مهيأ
 * وإلا القوي؛ والافتراضي → السريع.
 */
fun detectModelType(
    prompt: String,
    isOfflineMode: Boolean,
    teamConfigured: Boolean
): Pair<AgentModelType, String> {
    if (isOfflineMode) {
        return AgentModelType.ON_DEVICE to "دون اتصال — النموذج المحلي"
    }
    return when (detectSpecialization(prompt)) {
        AgentSpecialization.BUG_HUNTER ->
            AgentModelType.FAST to "إصلاح خطأ — النموذج السريع يكفي"
        AgentSpecialization.CODE_REVIEWER ->
            AgentModelType.POWERFUL to "مراجعة دقيقة تحتاج نموذجاً قوياً"
        AgentSpecialization.ARCHITECT ->
            if (teamConfigured && isComplexPrompt(prompt)) {
                AgentModelType.TEAM to "مهمة كبيرة متعددة الأجزاء — الفريق يقسّم وينفذ بالتوازي"
            } else {
                AgentModelType.POWERFUL to "بناء كبير — تخطيط أولاً ثم تنفيذ"
            }
        AgentSpecialization.TURBO ->
            AgentModelType.FAST to "تعديل صغير — تنفيذ مباشر"
    }
}

/**
 * حلّ معرّف النموذج الفعّال لنوعٍ ما — داخل المزود النشط حصراً
 * (المهمة تحسم المزود من المفاتيح المخزنة، فلا يُمرَّر معرّف مزود آخر).
 * الفريق وعلى الجهاز يبقيان النموذج المخزون (أعضاء الفريق / النموذج
 * المحلي يقرران)، والسريع/القوي يختاران طبقة النموذج المناسبة.
 */
fun resolveModelForType(
    type: AgentModelType,
    provider: DetectedProvider,
    storedModel: String
): String {
    return when (type) {
        AgentModelType.FAST -> when (provider) {
            DetectedProvider.GEMINI -> "gemini-2.5-flash"
            DetectedProvider.GROQ -> "llama-3.1-8b-instant"
            DetectedProvider.OPENROUTER -> "openai/gpt-4o-mini"
            DetectedProvider.NONE -> storedModel
        }
        AgentModelType.POWERFUL -> when (provider) {
            DetectedProvider.GEMINI -> "gemini-2.5-pro"
            DetectedProvider.GROQ -> "llama-3.3-70b-versatile"
            DetectedProvider.OPENROUTER -> "anthropic/claude-sonnet-4.5"
            DetectedProvider.NONE -> storedModel
        }
        AgentModelType.TEAM, AgentModelType.ON_DEVICE -> storedModel
    }
}

/** اسم مقروء لمعرّف نموذج — لا يُعرض معرّف خام أبداً. */
fun readableModelName(modelId: String): String {
    val known = mapOf(
        "gemini-2.5-flash" to "Gemini 2.5 Flash",
        "gemini-2.5-pro" to "Gemini 2.5 Pro",
        "gemini-2.0-flash" to "Gemini 2.0 Flash",
        "llama-3.3-70b-versatile" to "Llama 3.3 70B",
        "llama-3.1-8b-instant" to "Llama 3.1 8B",
        "mixtral-8x7b" to "Mixtral 8x7B",
        "anthropic/claude-sonnet-4.5" to "Claude Sonnet 4.5",
        "openai/gpt-4o" to "GPT-4o",
        "openai/gpt-4o-mini" to "GPT-4o mini",
        "meta-llama/llama-3.3-70b-instruct" to "Llama 3.3 70B",
        "local_sandbox" to "وضع محلي",
        "local_san" to "وضع محلي"
    )
    known[modelId]?.let { return it }
    // تنظيف عام: بلا بادئة مزود وبلا لواحق تقنية، وبكلمات مفصولة.
    val cleaned = modelId.substringAfterLast("/")
        .removeSuffix(".gguf")
        .removeSuffix("-instruct")
        .removeSuffix("-versatile")
        .replace('_', ' ').replace('-', ' ').trim()
    if (cleaned.isBlank() || cleaned.startsWith("local ")) return "وضع محلي"
    return cleaned.split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercaseChar() }
    }
}

/** اسم مزود قصير للعرض (displayName الطويل لا يصلح في سطر مدمج). */
fun providerShortName(provider: DetectedProvider): String {
    return when (provider) {
        DetectedProvider.GEMINI -> "Gemini"
        DetectedProvider.GROQ -> "Groq"
        DetectedProvider.OPENROUTER -> "OpenRouter"
        DetectedProvider.NONE -> ""
    }
}

/**
 * هل الفريق مهيأ فعلاً؟ يلزم قائد واحد مفعّل + مساعد واحد مفعّل على الأقل.
 */
fun isTeamConfigured(team: List<AgentTeamMember>): Boolean {
    val enabled = team.filter { it.enabled && it.provider != DetectedProvider.NONE }
    val leaders = enabled.count { it.role == AgentTeamRole.LEADER }
    val assistants = enabled.count { it.role != AgentTeamRole.LEADER }
    return leaders == 1 && assistants >= 1
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
