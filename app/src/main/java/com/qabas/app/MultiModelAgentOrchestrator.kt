package com.qabas.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 🔑 الموزع المنسي (Multi-Model Agent Orchestrator):
 * يدير عدة نماذج ذكاء اصطناعي في نفس المهمة الواحدة بنمط «قائد + مساعدين» —
 * النموذج القائد (Leader) يوزع الخطة على المساعدين، يجمع نتائجهم، ثم يُصدر الخلاصة النهائية.
 *
 * يستخدم مع UniversalKeyBrain.executeRealAgentStepWithFailover كطبقة واحدة لاستدعاء أي نموذج.
 */

private const val TAG_ORCH = "MultiModelOrchestrator"

/** إعدادات مشارك واحد في فريق الوكيل */
data class AgentTeamMember(
    val id: String,                  // معرف فريد داخل الفريق
    val displayName: String,          // اسم عرض عربي
    val provider: DetectedProvider,   // المزود
    val modelId: String,             // اسم النموذج
    val role: AgentTeamRole,         // الدور (قائد/مساعد/مدقق)
    val rank: Int = 0,               // الرتبة — رقم أصغر = أعلى سلطة
    val enabled: Boolean = true,
    val customInstruction: String = "" // تعليمات تخصيص اختيارية (مهمة العضو/أسلوبه)
)

enum class AgentTeamRole(val label: String) {
    LEADER("قائد 👑"),
    EXECUTOR("منفّذ ⚙️"),
    REVIEWER("مراجع 🛡️")
}

/** خطة عمل مُنتجة من النموذج القائد */
data class LeaderPlan(
    val overallTask: String,
    val subtasks: List<LeaderSubtask>,
    val rationale: String = ""
)

data class LeaderSubtask(
    val id: String,
    val title: String,
    val description: String,
    val assigneeMemberId: String,   // معرف العضو المكلف بهذه المهمة الفرعية
    val dependsOn: List<String> = emptyList() // معرفات المهام الفرعية التي يجب إكمالها أولاً
)

/** نتيجة مشارك واحد */
data class MemberRunResult(
    val memberId: String,
    val role: AgentTeamRole,
    val rank: Int,
    val subtaskId: String = "",     // 🔑 معرف المهمة الفرعية المرتبطة
    val outputText: String = "",
    val latencyMs: Long = 0,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null,
    val tokensUsed: Int = 0
)

/** نتيجة تشغيل الفريق (تقرير شامل) */
data class MultiModelRunReport(
    val overallPrompt: String,
    val team: List<AgentTeamMember>,
    val leaderPlan: LeaderPlan? = null,
    val memberResults: List<MemberRunResult> = emptyList(),
    val leaderSynthesis: String = "",
    val totalLatencyMs: Long = 0,
    val overallSuccess: Boolean = false,
    val warnings: List<String> = emptyList(),
    // الدفع حكر على القائد وبإذن المستخدم: تُستخرج حصراً من خلاصته، ومخرجات
    // المساعدين نتائج تُسلَّم للقائد فقط ولا تتحول لدفع أبداً.
    val pushRequested: Boolean = false,
    val leaderDiffs: List<FileDiff> = emptyList(),
    val preflight: TeamPreflight = TeamPreflight(ready = true, source = "تخطي")
)

/** نموذج مطلوبات البدء: ما يلزم قبل أن يعمل الفريق */
data class TeamPreflight(
    val ready: Boolean,
    val missing: List<String> = emptyList(),
    val questions: List<String> = emptyList(),
    val source: String = ""
)

object MultiModelAgentOrchestrator {

    /** الحد الأقصى لعدد أعضاء الفريق */
    const val MAX_TEAM_SIZE = 5

    /** مهلة النموذج الواحد بالمللي ثانية (2 دقيقة) */
    private const val PER_CALL_TIMEOUT_MS = 120_000L

    /**
     * تحميل فريق الوكيل من الإعدادات المحفوظة
     * يستخدم SharedPreferences للتخزين الدائم بين الجلسات
     * @param onlyEnabled إذا true يعيد الأعضاء المفعّلين فقط (للتنفيذ)، وإلا يعيد الكل (للتعديل)
     */
    /**
     * تنظيف اسم العرض: الاسم للعرض فقط — تُحذف أي قوانين اتصال/تنزيل
     * علقت به (مثل "• ..."، "يلزم ..."، "نزّل ..."، "مفتاح ...") ويُستخدم
     * بديل نظيف حسب الدور عند الفراغ.
     */
    fun cleanDisplayName(raw: String, role: AgentTeamRole): String {
        var name = raw.substringBefore("•").trim()
        val ruleMarkers = listOf("يلزم", "نزّل", "نزل", "حمّل", "حمل", "مفتاح", "اتصال", "تنزيل", "تحميل", "API", "http", "🔑", "📥", "🔌")
        val cut = ruleMarkers.mapNotNull { m ->
            val idx = name.indexOf(m)
            if (idx >= 0) idx else null
        }.minOrNull()
        if (cut != null) name = name.substring(0, cut).trim(*" —:-•".toCharArray())
        if (name.isBlank()) {
            name = when (role) {
                AgentTeamRole.LEADER -> "القائد"
                AgentTeamRole.EXECUTOR -> "مساعد"
                AgentTeamRole.REVIEWER -> "مراجع"
            }
        }
        return name.take(40)
    }

    /** تطبيع التخصيص للمقارنة (إهمال الفراغات الزائدة) */
    fun normalizeInstruction(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ")

    /**
     * هل هذا التخصيص محجوز لعضو آخر؟ (الفصل والنظام: تخصيص واحد = عضو واحد)
     * الفارغ مباح للجميع ولا يُعد محجوزاً.
     */
    fun isInstructionTaken(team: List<AgentTeamMember>, selfId: String, instruction: String): Boolean {
        val norm = normalizeInstruction(instruction)
        if (norm.isBlank()) return false
        return team.any { it.id != selfId && normalizeInstruction(it.customInstruction) == norm }
    }

    /** تخصيصات افتراضية قابلة للاختيار حسب الدور */
    fun rolePresets(role: AgentTeamRole): List<String> = when (role) {
        AgentTeamRole.LEADER -> listOf(
            "مخطط صارم: قسّم أي مهمة لتخطيط ثم تنفيذ ثم مراجعة",
            "موزّع متوازن: وزّع الحمل بالتساوي ولا تُثقل عضواً واحداً",
            "قائد تدقيق: راجع كل مخرج قبل الدمج في التقرير النهائي"
        )
        AgentTeamRole.EXECUTOR -> listOf(
            "متخصص واجهات Jetpack Compose الداكنة",
            "متخصص منطق البيانات وإدارة الحالة",
            "متخصص إصلاح الأخطاء وتحسين الأداء"
        )
        AgentTeamRole.REVIEWER -> listOf(
            "مراجع Null Safety ومعالجة الاستثناءات",
            "مراجع معمارية وفصل المسؤوليات",
            "مراجع أداء واستهلاك الذاكرة"
        )
    }

    /** نتيجة فحص دستور الفريق: أخطاء مانعة + تحذيرات توصية */
    data class TeamValidation(
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList()
    ) {
        val isValid: Boolean get() = errors.isEmpty()
    }

    /**
     * دستور الفريق — يُفحص قبل الحفظ والتنفيذ:
     * مواد مانعة: قائد واحد مفعّل • مساعد واحد على الأقل • ≤5 أعضاء •
     * رتب فريدة • لا تكرار تخصيص.
     * المادة 6 (معمارية، مطبقة بالاستخراج لا بالفحص): الدفع حكر على القائد
     * وبإذن المستخدم — مخرجات المساعدين نتائج تُسلَّم للقائد ولا تُدفع.
     * توصيات: تخصيص معبأ لكل مفعّل • أسماء مميزة.
     */
    fun validateTeam(team: List<AgentTeamMember>): TeamValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val enabled = team.filter { it.enabled }

        val leaders = enabled.count { it.role == AgentTeamRole.LEADER }
        if (leaders == 0) errors.add("المادة 1: لا يوجد قائد مفعّل — عيّن قائداً واحداً.")
        if (leaders > 1) errors.add("المادة 1: يوجد $leaders قادة — القائد واحد فقط.")

        val helpers = enabled.count { it.role != AgentTeamRole.LEADER }
        if (helpers == 0) errors.add("المادة 2: لا يوجد مساعد/مراجع مفعّل — الفريق قائد + مساعد على الأقل.")

        if (team.size > MAX_TEAM_SIZE) errors.add("المادة 3: تجاوز الحد الأقصى ($MAX_TEAM_SIZE أعضاء) — احذف ${team.size - MAX_TEAM_SIZE}.")

        val rankDupes = enabled.groupBy { it.rank }.filter { it.value.size > 1 }
        if (rankDupes.isNotEmpty()) errors.add("المادة 4: رتب مكررة (${rankDupes.keys.joinToString("،") { "#$it" }}) — كل عضو رتبته فريدة.")

        val seen = mutableSetOf<String>()
        val dupes = mutableSetOf<String>()
        enabled.forEach {
            val n = normalizeInstruction(it.customInstruction)
            if (n.isNotBlank() && !seen.add(n)) dupes.add(it.displayName)
        }
        if (dupes.isNotEmpty()) errors.add("المادة 5: تخصيص مكرر بين (${dupes.joinToString("، ")}) — التخصيص الواحد لعضو واحد.")

        if (enabled.any { normalizeInstruction(it.customInstruction).isBlank() }) {
            warnings.add("يُستحسن تعبئة تخصيص كل عضو المفعّل ليوزع القائد بدقة.")
        }
        val nameDupes = enabled.groupBy { it.displayName.trim() }.filter { it.value.size > 1 }.keys
        if (nameDupes.isNotEmpty()) warnings.add("أسماء مكررة: ${nameDupes.joinToString("، ")} — ميّز الأسماء لتسهيل المتابعة.")

        return TeamValidation(errors, warnings)
    }

    /**
     * إصلاح تلقائي لما يمكن إصلاحه: رتب متسلسلة فريدة حسب الترتيب الحالي،
     * وإبقاء التخصيص المكرر للأول وإخلاء الباقي.
     */
    fun autoFixTeam(team: List<AgentTeamMember>): List<AgentTeamMember> {
        val seen = mutableSetOf<String>()
        return team.sortedBy { it.rank }.mapIndexed { idx, m ->
            val norm = normalizeInstruction(m.customInstruction)
            val fixedInstruction =
                if (norm.isNotBlank() && !seen.add(norm)) "" else m.customInstruction
            m.copy(rank = idx + 1, customInstruction = fixedInstruction)
        }
    }

    /** قوالب فرق جاهزة بنقرة واحدة (تُطبق فوق التشكيلة الحالية بعد التأكيد) */
    fun teamTemplates(): Map<String, List<AgentTeamMember>> = mapOf(
        "⚖️ متوازن" to listOf(
            AgentTeamMember("leader_1", "القائد", DetectedProvider.OPENROUTER, "anthropic/claude-sonnet-4.5", AgentTeamRole.LEADER, 1, true, rolePresets(AgentTeamRole.LEADER)[0]),
            AgentTeamMember("exec_1", "المنفذ", DetectedProvider.GEMINI, "gemini-3.5-flash", AgentTeamRole.EXECUTOR, 2, true, rolePresets(AgentTeamRole.EXECUTOR)[0]),
            AgentTeamMember("rev_1", "المراجع", DetectedProvider.GROQ, "llama-3.3-70b-versatile", AgentTeamRole.REVIEWER, 3, true, rolePresets(AgentTeamRole.REVIEWER)[0])
        ),
        "⚡ تنفيذي" to listOf(
            AgentTeamMember("leader_1", "القائد", DetectedProvider.OPENROUTER, "anthropic/claude-sonnet-4.5", AgentTeamRole.LEADER, 1, true, rolePresets(AgentTeamRole.LEADER)[1]),
            AgentTeamMember("exec_1", "منفذ الواجهات", DetectedProvider.GEMINI, "gemini-3.5-flash", AgentTeamRole.EXECUTOR, 2, true, rolePresets(AgentTeamRole.EXECUTOR)[0]),
            AgentTeamMember("exec_2", "منفذ المنطق", DetectedProvider.GROQ, "llama-3.3-70b-versatile", AgentTeamRole.EXECUTOR, 3, true, rolePresets(AgentTeamRole.EXECUTOR)[1])
        ),
        "🛡️ تدقيق" to listOf(
            AgentTeamMember("leader_1", "القائد", DetectedProvider.OPENROUTER, "anthropic/claude-sonnet-4.5", AgentTeamRole.LEADER, 1, true, rolePresets(AgentTeamRole.LEADER)[2]),
            AgentTeamMember("exec_1", "المنفذ", DetectedProvider.GEMINI, "gemini-3.5-flash", AgentTeamRole.EXECUTOR, 2, true, rolePresets(AgentTeamRole.EXECUTOR)[2]),
            AgentTeamMember("rev_1", "مراجع الأمان", DetectedProvider.GROQ, "llama-3.3-70b-versatile", AgentTeamRole.REVIEWER, 3, true, rolePresets(AgentTeamRole.REVIEWER)[0]),
            AgentTeamMember("rev_2", "مراجع الأداء", DetectedProvider.OPENROUTER, "openai/gpt-4o", AgentTeamRole.REVIEWER, 4, true, rolePresets(AgentTeamRole.REVIEWER)[2])
        ),
        "🏗️ بناء متكامل" to listOf(
            AgentTeamMember("leader_1", "القائد المعماري", DetectedProvider.OPENROUTER, "anthropic/claude-sonnet-4.5", AgentTeamRole.LEADER, 1, true, "مخطط صارم: قسّم أي تطبيق كامل لواجهات ومنطق ومراجعة، ثم ادمج واطلب الدفع."),
            AgentTeamMember("exec_ui", "منفذ الواجهات", DetectedProvider.GEMINI, "gemini-3.5-flash", AgentTeamRole.EXECUTOR, 2, true, rolePresets(AgentTeamRole.EXECUTOR)[0]),
            AgentTeamMember("exec_logic", "منفذ المنطق والبيانات", DetectedProvider.GROQ, "llama-3.3-70b-versatile", AgentTeamRole.EXECUTOR, 3, true, rolePresets(AgentTeamRole.EXECUTOR)[1]),
            AgentTeamMember("rev_1", "مراجع الجودة", DetectedProvider.OPENAI, "gpt-4o", AgentTeamRole.REVIEWER, 4, true, rolePresets(AgentTeamRole.REVIEWER)[0])
        ),
        "⚡ OpenCode" to listOf(
            AgentTeamMember("leader_1", "القائد سبارك", DetectedProvider.OPENCODE, "muse-spark-1.3", AgentTeamRole.LEADER, 1, true, "مخطط صارم: قسّم العمل لمنفذين سريعين ومراجع دقيق، ثم ادمج واطلب الدفع."),
            AgentTeamMember("exec_1", "منفذ بيج بيكل", DetectedProvider.OPENCODE, "big-pickle", AgentTeamRole.EXECUTOR, 2, true, rolePresets(AgentTeamRole.EXECUTOR)[2]),
            AgentTeamMember("exec_2", "منفذ كيمي", DetectedProvider.OPENCODE, "kimi-k2.7-code", AgentTeamRole.EXECUTOR, 3, true, rolePresets(AgentTeamRole.EXECUTOR)[0]),
            AgentTeamMember("rev_1", "مراجع هايكو", DetectedProvider.OPENCODE, "claude-haiku-4-5", AgentTeamRole.REVIEWER, 4, true, rolePresets(AgentTeamRole.REVIEWER)[0])
        )
    )

    /**
     * فحص توافر القالب: هل لكل عضو مفتاح صالح في مسبح مزوده؟
     * يُرجع قائمة المزودات الناقصة (فارغة = جاهز للتطبيق).
     */
    fun templateMissingProviders(template: List<AgentTeamMember>): List<String> {
        return template.map { it.provider }.distinct().filter { p ->
            val pk = UniversalKeyBrain.prefsKeyFor(p)
            pk.isBlank() || KeyPoolManager.orderedKeysBySpeed(pk).isEmpty()
        }.map { it.displayName }
    }

    /**
     * نموذج تعبئة المطلوبات: قبل أي عمل، القائد يحدد ما ينقصه لبدء المهمة
     * (مستلزمات + أسئلة توضيحية) بصيغة JSON. إن فشل استجواب القائد لأي سبب،
     * التطبيق ذاته يطلب المستلزمات من فحصه الحتمي — لا يبدأ العمل ناقصاً أبداً.
     */
    private suspend fun checkPreflight(
        context: Context,
        prompt: String,
        team: List<AgentTeamMember>,
        leader: AgentTeamMember?,
        materials: String,
        leaderKey: String
    ): TeamPreflight {
        // الفحص الحتمي للتطبيق (يعمل دائماً — وهو الغطاء عند فشل القائد)
        val appMissing = mutableListOf<String>()
        val appQuestions = mutableListOf<String>()
        if (prompt.isBlank()) appQuestions.add("ما المطلوب تنفيذه بالضبط؟ اكتب أمراً واضحاً.")
        else if (prompt.trim().length < 10) appQuestions.add("الأمر مختصر جداً («$prompt») — وضّح الهدف والشاشة أو الملف المعني.")
        val providers = team.filter { it.enabled }.map { it.provider }.distinct()
        providers.forEach { p ->
            val pk = UniversalKeyBrain.prefsKeyFor(p)
            val keys = if (pk.isNotBlank()) KeyPoolManager.orderedKeysBySpeed(pk) else emptyList()
            if (keys.isEmpty()) appMissing.add("مفتاح ${p.displayName} — أدخله من خانة المفاتيح (يُكتشف ويُفحص تلقائياً).")
        }
        if (RepoVaultManager.getActiveRepo(context) == null) {
            appMissing.add("ربط المستودع — اربط مستودع GitHub من مركز البناء ليعرف القائد وجهة الدفع.")
        }

        // استجواب القائد: مستلزمات خاصة بالمهمة
        if (leader != null) {
            val sys = """
                أنت ${leader.displayName} — قائد فريق. قبل بدء العمل على المهمة أدناه، حدد مستلزمات البدء فقط.
                أخرج JSON فقط بهذا الشكل تماماً بلا أي نص آخر:
                {"ready":true/false,"missing":["..."],"questions":["..."]}
                • ready=true فقط إذا كانت المهمة واضحة ولا ينقصها شيء من جهتك.
                • missing: مستلزمات موضوعية (مثال: مسار ملف، صلاحية، قرار تصميم).
                • questions: أسئلة للمستخدم عند الغموض (بحد أقصى 3، موجزة).
                $materials
            """.trimIndent()
            val res = callModel(leader, sys, "المهمة المطلوبة: \"$prompt\"\nحدد مستلزمات البدء الآن.", leaderKey)
            val parsed = if (res.isSuccess) parsePreflight(res.outputText) else null
            if (parsed != null) {
                val allMissing = (appMissing + parsed.missing).distinct()
                val allQuestions = (appQuestions + parsed.questions).distinct().take(5)
                return TeamPreflight(
                    ready = parsed.ready && allMissing.isEmpty() && allQuestions.isEmpty(),
                    missing = allMissing,
                    questions = allQuestions,
                    source = "القائد ${leader.displayName} + فحص التطبيق"
                )
            }
        }
        // غطاء التطبيق عند فشل القائد
        return TeamPreflight(
            ready = appMissing.isEmpty() && appQuestions.isEmpty(),
            missing = appMissing,
            questions = appQuestions,
            source = "فحص التطبيق الذاتي (تعذّر استجواب القائد)"
        )
    }

    /** تحليل JSON المطلوبات بتسامح (يُستخرج من أي نص محيط) */
    private fun parsePreflight(raw: String): TeamPreflight? {
        return try {
            val s = raw.indexOf('{')
            val e = raw.lastIndexOf('}')
            if (s < 0 || e <= s) return null
            val obj = org.json.JSONObject(raw.substring(s, e + 1))
            val missing = obj.optJSONArray("missing")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            val questions = obj.optJSONArray("questions")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            TeamPreflight(
                ready = obj.optBoolean("ready", false),
                missing = missing.filter { it.isNotBlank() },
                questions = questions.filter { it.isNotBlank() },
                source = "القائد"
            )
        } catch (_: Exception) {
            null
        }
    }
    /**
     * رؤية المستودع — بوابة مانعة لا مجرد ملاحظة: شجرة الملفات الحقيقية تُجلب
     * عبر GitHub API وتُحقن في البرومبتات. الفشل هنا يُرجع null فيتوقف العمل
     * بصدق قبل أي تخطيط — العمل الأعمى تخبيص مرفوض، لا عشوائية.
     */
    private suspend fun buildRepoSight(repo: RepoVaultEntry?): String? {
        if (repo == null) return null
        return try {
            val client = GitHubRepoClient(repo.owner, repo.repo, repo.token)
            val tree = client.getTree(repo.defaultBranch.ifBlank { "main" }, recursive = true)
                ?: return null
            val paths = tree.filter { it.isFile }
                .map { it.path }
                .filter { p ->
                    p.startsWith("app/src/main") &&
                        (p.endsWith(".kt") || p.endsWith(".xml") || p.endsWith(".kts") || p.endsWith(".gradle"))
                }
                .take(150)
            if (paths.isEmpty()) return null
            "\n═══ شجرة المستودع الحقيقية (${paths.size} ملفاً) ═══\n" + paths.joinToString("\n")
        } catch (_: Exception) {
            null
        }
    }

    /**
     * محتوى الملفات المعنية: تُستخرج المسارات من الخطة والأمر، وتُجلب محتوياتها
     * (بحد أقصى 5 ملفات × 2500 حرف) لتُحقن في برومبت المنفذ — يعمل على كود حقيقي.
     */
    private suspend fun fetchTaskFiles(repo: RepoVaultEntry?, texts: List<String>): String {
        if (repo == null) return ""
        return try {
            val pathRegex = Regex("app/[\\w\\-/\$.]+\\.(kt|xml|kts)")
            val paths = texts.flatMap { pathRegex.findAll(it).map { m -> m.value } }
                .distinct().take(5)
            if (paths.isEmpty()) return ""
            val client = GitHubRepoClient(repo.owner, repo.repo, repo.token)
            val branch = repo.defaultBranch.ifBlank { "main" }
            val blocks = paths.mapNotNull { p ->
                val resp = runCatching { client.getFileContent(p, branch) }.getOrNull() ?: return@mapNotNull null
                val decoded = runCatching {
                    val clean = resp.content.replace("\\s".toRegex(), "")
                    if (clean.isBlank()) return@mapNotNull null
                    String(android.util.Base64.decode(clean, android.util.Base64.DEFAULT), Charsets.UTF_8)
                }.getOrNull()?.take(2500) ?: return@mapNotNull null
                if (decoded.isBlank()) return@mapNotNull null
                "─── $p ───\n$decoded"
            }
            if (blocks.isEmpty()) "" else "\n═══ محتوى الملفات المعنية ═══\n" + blocks.joinToString("\n\n")
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * مواد المهمة: المستودع النشط + أدوات البناء — تُحقن في برومبتات القائد
     * والمساعدين حتى يعرفوا أين سيُدفع الكود وكيف سيُختبر بناؤه.
     */
    private fun buildMissionMaterials(context: Context): String {
        val repo = RepoVaultManager.getActiveRepo(context)
        val repoLine = if (repo != null) {
            "المستودع النشط: ${repo.owner}/${repo.repo} — الفرع: ${repo.defaultBranch.ifBlank { "main" }}."
        } else {
            "لا يوجد مستودع مربوط حالياً — اطلب من المستخدم ربط مستودعه من مركز البناء قبل اقتراح دفع."
        }
        return """
            ═══ مواد المستودع والدفع ═══
            $repoLine
            • مسار الكود: app/src/main/java/com/qabas/app/ — أي ملف جديد أو معدل يجب ذكر مساره الكامل.
            • دورة الدفع: مخرجاتك تتحول لمقترحات (FileDiff) يراجعها المستخدم، ثم تُدفع عبر GitHub API (updateFile) إلى الفرع أعلاه برسالة commit واضحة.
            • قاعدة أمنية صارمة: لن تحصل على توكن المستودع أبداً — التوكن يبقى في التطبيق وهو من ينفذ الدفع. لا تطلبه ولا تُدخله في أي مخرج.
            • لا تقترح تعديل ملفات خارج app/src/main/ إلا لسبب وجيه تذكره صراحة.
            ═══ أداة اختبار البناء ═══
            • بعد الدفع يُشغَّل سير العمل android-ci.yml عبر (trigger_build) على GitHub Actions، ويفحص BuildVerificationEngine سلامة الكود وحالة الـ CI.
            • أي كود غير قابل للترجمة يُفشل البناء — التزم: Kotlin سليم، imports موجودة، Jetpack Compose مستقر، ولا صلاحيات/اعتماديات جديدة دون مبرر.
        """.trimIndent()
    }

    /**
     * تحميل فريق الوكيل من الإعدادات المحفوظة
     * يستخدم SharedPreferences للتخزين الدائم بين الجلسات
     * @param onlyEnabled إذا true يعيد الأعضاء المفعّلين فقط (للتنفيذ)، وإلا يعيد الكل (للتعديل)
     */
    fun loadTeam(context: Context, onlyEnabled: Boolean = false): List<AgentTeamMember> {
        val prefs = context.getSharedPreferences("qabas_agent_team", Context.MODE_PRIVATE)
        val teamJson = prefs.getString("team_config_json", null) ?: return defaultTeam()
        return try {
            val arr = org.json.JSONArray(teamJson)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val role = AgentTeamRole.entries.firstOrNull { it.name == o.optString("role") } ?: AgentTeamRole.EXECUTOR
                AgentTeamMember(
                    id = o.optString("id"),
                    displayName = cleanDisplayName(o.optString("name"), role),
                    provider = DetectedProvider.entries.firstOrNull { it.name == o.optString("provider") } ?: DetectedProvider.NONE,
                    modelId = o.optString("model"),
                    role = role,
                    rank = o.optInt("rank", 99),
                    enabled = o.optBoolean("enabled", true),
                    customInstruction = o.optString("instruction", "")
                )
            }.filter { (!onlyEnabled || it.enabled) && it.provider != DetectedProvider.NONE }
        } catch (e: Exception) {
            Log.e(TAG_ORCH, "Failed to load team: ${e.message}")
            defaultTeam()
        }
    }

    /** حفظ فريق الوكيل */
    fun saveTeam(context: Context, team: List<AgentTeamMember>) {
        val prefs = context.getSharedPreferences("qabas_agent_team", Context.MODE_PRIVATE)
        val arr = org.json.JSONArray()
        team.forEach { m ->
            arr.put(org.json.JSONObject().apply {
                put("id", m.id)
                put("name", cleanDisplayName(m.displayName, m.role))
                put("provider", m.provider.name)
                put("model", m.modelId)
                put("role", m.role.name)
                put("rank", m.rank)
                put("enabled", m.enabled)
                put("instruction", m.customInstruction)
            })
        }
        prefs.edit().putString("team_config_json", arr.toString()).apply()
    }

    /** فريق افتراضي (يُعاد استخدامه إذا لم تكن هناك إعدادات محفوظة) */
    fun defaultTeam(): List<AgentTeamMember> = listOf(
        AgentTeamMember("leader_1", "القائد (Claude)", DetectedProvider.OPENROUTER, "anthropic/claude-sonnet-4.5", AgentTeamRole.LEADER, rank = 1, enabled = true),
        AgentTeamMember("exec_1", "المساعد 1 (Gemini)", DetectedProvider.GEMINI, "gemini-3.5-flash", AgentTeamRole.EXECUTOR, rank = 2, enabled = true),
        AgentTeamMember("exec_2", "المساعد 2 (Groq)", DetectedProvider.GROQ, "llama-3.3-70b-versatile", AgentTeamRole.EXECUTOR, rank = 3, enabled = true)
    )

    /**
     * تنفيذ مهمة باستخدام فريق متعدد النماذج
     * التسلسل: (1) القائد يُصدر خطة → (2) المساعدون ينفذون بالتوازي → (3) القائد يُجمع النتيجة
     *
     * @param context سياق التطبيق لقراءة المفاتيح
     * @param prompt المهمة من المستخدم
     * @param team فريق الوكيل (يجب أن يحتوي على قائد واحد على الأقل + مساعد واحد مفعّل)
     * @param onUpdate استدعاء متكرر يُمرر على الخيط الرئيسي (Main thread) لتحديث واجهة المستخدم
     * @return تقرير شامل عن النتيجة
     */
    suspend fun executeWithTeam(
        context: Context,
        prompt: String,
        team: List<AgentTeamMember>,
        onUpdate: (String) -> Unit = {}
    ): MultiModelRunReport = withContext(Dispatchers.IO) {
        // 🔑 نُسلسل onUpdate إلى الخيط الرئيسي (Main) لتحديث UI بأمان من worker threads
        val safeOnUpdate: suspend (String) -> Unit = { msg ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onUpdate(msg)
            }
        }
        val startTime = System.currentTimeMillis()
        val warnings = mutableListOf<String>()

        // دستور الفريق يُطبق قبل أي استدعاء: المخالفة المانعة تُفشل بصدق ولا يُنفذ شيء
        val validation = validateTeam(team)
        if (!validation.isValid) {
            return@withContext MultiModelRunReport(
                overallPrompt = prompt,
                team = team.filter { it.enabled },
                totalLatencyMs = System.currentTimeMillis() - startTime,
                overallSuccess = false,
                warnings = validation.errors
            )
        }
        warnings.addAll(validation.warnings)

        // ═══ المرحلة 0 — البوابة: مستودع مربوط؟ مفاتيح؟ ═══
        // (الدستور فُحص أعلاه؛ البوابة هنا للموارد الخارجية)

        // ═══ المرحلة 1 — الرؤية: شجرة المستودع بوابة مانعة ═══
        safeOnUpdate("👁️ جاري جلب شجرة المستودع...")
        val materials = buildMissionMaterials(context)
        val sight = buildRepoSight(RepoVaultManager.getActiveRepo(context))
        if (sight == null) {
            return@withContext MultiModelRunReport(
                overallPrompt = prompt,
                team = team.filter { it.enabled },
                totalLatencyMs = System.currentTimeMillis() - startTime,
                overallSuccess = false,
                warnings = listOf("تعذّر جلب شجرة المستودع (توكن؟ شبكة؟ فرع؟) — توقف العمل عمداً بدل التخبيص الأعمى. تحقق من الربط ثم أعد التنفيذ.")
            )
        }
        val sightedMaterials = materials + sight
        safeOnUpdate("👁️ تمت الرؤية — القائد يرى الشجرة الحقيقية.")

        // 🔑 حل مفتاح لكل مزود على حدة (لا نمرر مفتاح مزود لمزود آخر) — مبكراً لأجل فحص المطلوبات
        val providerKeys = mutableMapOf<DetectedProvider, String>()
        team.map { it.provider }.distinct().forEach { p ->
            val prefsKey = UniversalKeyBrain.prefsKeyFor(p)
            if (prefsKey.isNotBlank()) {
                val keys = KeyPoolManager.orderedKeysBySpeed(prefsKey)
                if (keys.isNotEmpty()) providerKeys[p] = keys.first()
            }
        }

        // نموذج مطلوبات البدء: القائد يستجوب أولاً، والتطبيق يغطي عند فشله
        safeOnUpdate("📋 المرحلة 2 — المطلوبات: استيفاء مستلزمات البدء...")
        val enabledTeamEarly = team.filter { it.enabled }
        val leaderEarly = enabledTeamEarly.firstOrNull { it.role == AgentTeamRole.LEADER }
        val preflight = checkPreflight(context, prompt, team, leaderEarly, sightedMaterials, providerKeys[leaderEarly?.provider] ?: "")
        if (!preflight.ready) {
            return@withContext MultiModelRunReport(
                overallPrompt = prompt,
                team = enabledTeamEarly,
                totalLatencyMs = System.currentTimeMillis() - startTime,
                overallSuccess = false,
                warnings = preflight.missing + preflight.questions,
                preflight = preflight
            )
        }

        // 🔑 نستخدم الأعضاء المفعّلين فقط للتنفيذ
        val enabledTeam = team.filter { it.enabled }

        val leader = enabledTeam.firstOrNull { it.role == AgentTeamRole.LEADER }
        if (leader == null) {
            return@withContext MultiModelRunReport(
                overallPrompt = prompt,
                team = enabledTeam,
                totalLatencyMs = System.currentTimeMillis() - startTime,
                overallSuccess = false,
                warnings = listOf("لا يوجد نموذج قائد في الفريق! يجب تعيين LEADER واحد على الأقل.")
            )
        }

        val assistants = enabledTeam.filter { it.id != leader.id && it.role != AgentTeamRole.LEADER }.sortedBy { it.rank }
        if (assistants.isEmpty()) {
            // 🔑 وضع «فريق» بلا مساعدين غير قابل للتنفيذ — نُفشل بوضوح بدلًا من جعل القائد يحاول وحده
            return@withContext MultiModelRunReport(
                overallPrompt = prompt,
                team = enabledTeam,
                totalLatencyMs = System.currentTimeMillis() - startTime,
                overallSuccess = false,
                warnings = listOf(
                    "لا يوجد مساعد مفعّل في الفريق! وضع الفريق يتطلب قائد + مساعد واحد على الأقل. " +
                            "إذا كنت تريد تشغيل نموذج واحد فقط، استخدم زر «فردي» بدلًا من وضع الفريق."
                )
            )
        }

        // ═══ المرحلة 1: القائد يُصدر خطة عمل ═══
        currentCoroutineContext().ensureActive() // 🔑 تحقق من عدم إلغاء المهمة قبل البدء
        safeOnUpdate("👑 ${leader.displayName}: جاري التخطيط...")
        val leaderSystemPrompt = buildLeaderSystemPrompt(leader, assistants, prompt, sightedMaterials)

        val planResult = callModel(leader, leaderSystemPrompt, prompt, providerKeys[leader.provider] ?: "")
        val leaderPlan: LeaderPlan? = if (planResult.isSuccess) {
            parseLeaderPlan(planResult.outputText, enabledTeam)
        } else null

        if (leaderPlan == null) {
            warnings.add("تعذر توليد خطة صالحة من القائد — سيتم تنفيذ المساعدين مباشرة على المهمة الأصلية.")
            safeOnUpdate("⚠️ ${leader.displayName}: تعذر توليد خطة صالحة — سيتم تنفيذ المساعدين على المهمة الأصلية.")
        } else {
            safeOnUpdate("✅ ${leader.displayName}: تم توليد الخطة (${leaderPlan.subtasks.size} مهمة فرعية).")
        }

        // ═══ المرحلة 2: تنفيذ المساعدين (بالتوازي أو بالتسلسل حسب التبعيات) ═══
        currentCoroutineContext().ensureActive() // 🔑 تحقق من عدم إلغاء المهمة قبل المرحلة الثانية
        val memberResults = mutableListOf<MemberRunResult>()
        // 🔑 نستخدم succeededIds للمهام الناجحة فقط — المهمة الفاشلة تُوقف تبعاتها
        val succeededIds = mutableSetOf<String>()
        val failedIds = mutableSetOf<String>()
        memberResults.add(
            MemberRunResult(
                memberId = leader.id, role = AgentTeamRole.LEADER, rank = leader.rank,
                outputText = planResult.outputText, latencyMs = planResult.latencyMs,
                isSuccess = planResult.isSuccess, errorMessage = planResult.errorMessage,
                tokensUsed = planResult.tokensUsed
            )
        )

        if (assistants.isNotEmpty()) {
            // استخراج المهام الفرعية للقائد — أو إنشاء مهام افتراضية إذا فشل القائد
            val subtasks: List<LeaderSubtask> = leaderPlan?.subtasks
                ?.filter { st -> assistants.any { it.id == st.assigneeMemberId } }
                ?.takeIf { it.isNotEmpty() }
                ?: assistants.map { exec ->
                    LeaderSubtask(
                        id = "st_${exec.id}",
                        title = "تنفيذ المهمة الأصلية",
                        description = prompt,
                        assigneeMemberId = exec.id
                    )
                }

            // بناء خريطة العضو حسب معرفه
            val memberById = assistants.associateBy { it.id }

            // محتوى الملفات المعنية من المستودع الحقيقي — يعمل عليها المنفذون
            val taskFiles = fetchTaskFiles(
                RepoVaultManager.getActiveRepo(context),
                listOf(prompt) + subtasks.flatMap { listOf(it.title, it.description) }
            )

            // مراعاة التبعيات: ننفذ على دفعات (batch) حسب dependsOn
            val remaining = subtasks.toMutableList()

            while (remaining.isNotEmpty()) {
                val batch = remaining.filter { st ->
                    st.dependsOn.all { dep -> succeededIds.contains(dep) } &&
                            memberById.containsKey(st.assigneeMemberId)
                }
                if (batch.isEmpty()) {
                    // لا توجد مهام قابلة للتنفيذ (تبعيات دائرية أو غير موجودة) — تجاهل البقية
                    warnings.add("تم تجاهل ${remaining.size} مهمة فرعية بسبب تبعيات غير قابلة للتنفيذ.")
                    break
                }
                remaining.removeAll(batch.toSet())

                // السلسلة المنظمة: موجة البناة (منفذون) أولاً بالتوازي، ثم موجة
                // المراجعين بعدها ليراجعوا مخرجات فعلية لا فراغاً — لا عشوائية.
                suspend fun runWave(wave: List<LeaderSubtask>, waveLabel: String) {
                    if (wave.isEmpty()) return
                    safeOnUpdate("⚙️ $waveLabel (${wave.size}): ${wave.joinToString("، ") { it.title.take(30) }}")
                    coroutineScope {
                        val deferreds = wave.map { st ->
                            val member = memberById[st.assigneeMemberId]!!
                            async {
                                safeOnUpdate("⚡ ${member.displayName}: جاري تنفيذ «${st.title.take(40)}»...")
                                val r = callModel(
                                    member,
                                    buildAssistantSystemPrompt(member, sightedMaterials),
                                    buildMemberPrompt(st, prompt, member, succeededIds, memberResults, taskFiles),
                                    providerKeys[member.provider] ?: ""
                                )
                                Pair(st, r)
                            }
                        }
                        deferreds.awaitAll().forEach { (st, r) ->
                            val member = memberById[st.assigneeMemberId]!!
                            memberResults.add(
                                MemberRunResult(
                                    memberId = member.id, role = member.role, rank = member.rank,
                                    subtaskId = st.id,
                                    outputText = r.outputText, latencyMs = r.latencyMs,
                                    isSuccess = r.isSuccess, errorMessage = r.errorMessage,
                                    tokensUsed = r.tokensUsed
                                )
                            )
                            if (r.isSuccess) succeededIds.add(st.id) else failedIds.add(st.id)
                            // 🔑 تحديث حالة التقدم بعد اكتمال كل مهمة فرعية
                            val statusIcon = if (r.isSuccess) "✅" else "❌"
                            safeOnUpdate("$statusIcon ${member.displayName}: اكتمل «${st.title.take(30)}» (${r.latencyMs}ms)")
                        }
                    }
                }

                val (reviewWave, buildWave) = batch.partition { memberById[it.assigneeMemberId]?.role == AgentTeamRole.REVIEWER }
                runWave(buildWave, "موجة التنفيذ")
                runWave(reviewWave, "موجة المراجعة")
            }
        }

        // ═══ المرحلة 3: القائد يجمع النتيجة النهائية ═══
        currentCoroutineContext().ensureActive() // 🔑 تحقق من عدم إلغاء المهمة قبل المرحلة الثالثة
        safeOnUpdate("👑 ${leader.displayName}: جاري صياغة الخلاصة النهائية...")
        val successfulOutputs = memberResults.filter { it.isSuccess && it.role != AgentTeamRole.LEADER }
            .joinToString("\n\n---\n\n") { "[${it.memberId}] ${it.outputText}" }

        val synthesisResult = if (successfulOutputs.isNotBlank()) {
            callModel(
                leader,
                "أنت القائد — اجمع مخرجات المساعدين التالية في تقرير نهائي واحد متسق ومُحكم يحقق المهمة الأصلية. لا تكرر نفس النص، بل ادمج وأعد صياغة لتقديم إجابة نهائية موحدة.\n" +
                    "أنت الوحيد المخول بطلب الدفع: مخرجات المساعدين نتائج تُسلَّم لك فقط ولا تُدفع مباشرة أبداً.\n" +
                    "اختم تقريرك بقسم طلب إذن الدفع بهذا الشكل حرفياً:\n" +
                    "[طلب إذن الدفع]\nالملفات المقترحة للدفع:\n- path: <المسار الكامل> (<سبب التعديل>)\n" +
                    "ثم لكل ملف كتلة بالشكل:\n### FILE: <المسار الكامل>\n```kotlin\n<المحتوى الكامل للملف>\n```\n" +
                    "إن لم يستلزم العمل أي تعديل ملفات، اكتب: [لا دفع مطلوب].\n$sightedMaterials",
                "المهمة الأصلية: $prompt\n\nمخرجات المساعدين:\n$successfulOutputs\n\nأنتج التقرير النهائي الآن.",
                providerKeys[leader.provider] ?: ""
            )
        } else {
            MemberCallResult(outputText = "تعذر إنتاج مخرجات من أي مساعد.", isSuccess = false)
        }

        // 🔑 النجاح يتطلب نجاح مساعد واحد على الأقل (لا نكتفي بنجاح خطة القائد)
        val assistantSuccessCount = memberResults.count { it.isSuccess && it.role != AgentTeamRole.LEADER }
        val anySuccess = assistantSuccessCount > 0 && synthesisResult.isSuccess

        // 🔑 الإنجاز الجزئي: توجد مهام لم تُنفذ (حُظرت بسبب تبعياتها) رغم أن المهمة "نجحت"
        // نُدرجها في التحذيرات ليُعرف المستخدم أن مهمة فرعية لم تُنفذ
        val executedSubtaskIds = succeededIds + failedIds
        val assistantIds = assistants.map { it.id }.toSet()
        val blockedSubtasks = leaderPlan?.subtasks
            ?.filter { st -> !executedSubtaskIds.contains(st.id) && assistantIds.contains(st.assigneeMemberId) }
            ?: emptyList()

        if (blockedSubtasks.isNotEmpty()) {
            warnings.add(
                "⚠️ تم تجاهل ${blockedSubtasks.size} مهمة فرعية (حُظرت بسبب فشل تبعياتها): " +
                        blockedSubtasks.joinToString("، ") { it.title.take(40) }
            )
        }

        // استخراج طلب الدفع من خلاصة القائد حصراً — المساعدون خارج الدفع تماماً
        val pushRequested = "[طلب إذن الدفع]" in synthesisResult.outputText
        val leaderDiffs = if (pushRequested) extractLeaderDiffs(synthesisResult.outputText) else emptyList()

        MultiModelRunReport(
            overallPrompt = prompt,
            team = enabledTeam,
            leaderPlan = leaderPlan,
            memberResults = memberResults,
            leaderSynthesis = synthesisResult.outputText,
            totalLatencyMs = System.currentTimeMillis() - startTime,
            overallSuccess = anySuccess,
            warnings = warnings,
            pushRequested = pushRequested,
            leaderDiffs = leaderDiffs,
            preflight = preflight
        )
    }

    /**
     * مستخرج ملفات القائد: يقرأ كتل `### FILE: <المسار>` + كود fence من خلاصة
     * القائد فقط. أي نص من مساعد لا يمر هنا أبداً — الدفع حكر على القائد.
     */
    fun extractLeaderDiffs(synthesis: String): List<FileDiff> {
        val diffs = mutableListOf<FileDiff>()
        // ### FILE: path ... ```lang ... ```
        val headerRegex = Regex("^###\\s*FILE:\\s*(\\S+)\\s*$", RegexOption.MULTILINE)
        val headers = headerRegex.findAll(synthesis).toList()
        headers.forEachIndexed { idx, h ->
            val path = h.groupValues[1].trim()
            if (path.isBlank() || ".." in path || path.startsWith("/")) return@forEachIndexed
            val bodyStart = h.range.last + 1
            val bodyEnd = headers.getOrNull(idx + 1)?.range?.first ?: synthesis.length
            val body = synthesis.substring(bodyStart, bodyEnd)
            val fence = Regex("```\\w*\\n([\\s\\S]*?)```").find(body)?.groupValues?.getOrNull(1)
                ?: return@forEachIndexed
            val content = fence.trim()
            if (content.isBlank()) return@forEachIndexed
            val lines = content.lines().size.coerceAtLeast(1)
            diffs.add(FileDiff(filePath = path, additions = lines, deletions = 0, diffText = content))
        }
        return diffs.take(10) // سقف أمان: 10 ملفات كحد أقصى لكل دفعة
    }

    /**
     * برومبت القائد الخاص: يعرّف دوره القيادي، يلزمه بقواعد التوزيع الصارمة،
     * ويمنعه من تنفيذ المهام بنفسه أو إسناد مهمة لمعرف غير موجود.
     */
    private fun buildLeaderSystemPrompt(
        leader: AgentTeamMember,
        assistants: List<AgentTeamMember>,
        prompt: String,
        materials: String
    ): String {
        val roster = assistants.joinToString("\n") {
            "- المعرف: `${it.id}` — الاسم: ${it.displayName} — الدور: ${it.role.label}" +
                (if (it.customInstruction.isNotBlank()) " — تخصصه: ${it.customInstruction.take(200)}" else "")
        }
        val leaderExtra = if (leader.customInstruction.isNotBlank())
            "\nتعليمات إضافية خاصة بك كقائد: ${leader.customInstruction.take(300)}\n" else ""
        return """
            أنت ${leader.displayName} — القائد الأعلى لفريق مهندسي ذكاء اصطناعي داخل تطبيق أندرويد.
            شخصيتك: حازم، منظم، لا تنفّذ المهام بنفسك أبداً — عملك الوحيد هو التخطيط والتوزيع والدمج.
            $leaderExtra
            فريقك (${assistants.size} ${if (assistants.size == 1) "مساعد" else "مساعدين"}):
            $roster

            المهمة المطلوب إنجازها: "$prompt"

            قواعد صارمة:
            1. وزّع العمل على المساعدين حسب تخصص كل منهم ودوره (المنفّذ للبناء، المراجع للتدقيق) — لا تُسند مهمة مراجعة لمنفّذ والعكس إلا عند الضرورة.
            2. كل مهمة فرعية: عنوان واضح + وصف دقيق قابل للتنفيذ المستقل + assignee واحد فقط.
            3. حقل assignee يجب أن يحتوي المعرف الحقيقي للمساعد من القائمة أعلاه حرفياً (مثال: `${assistants.firstOrNull()?.id ?: "exec_1"}`) — أي معرف غير موجود يُبطل الخطة.
            4. لا تُسند أي مهمة لنفسك كقائد إطلاقاً.
            5. راعِ التبعيات: إن اعتمدت مهمة على أخرى اذكر ذلك في depends_on بمعرفات المهام.
            6. أخرج JSON فقط بهذا الشكل تماماً، بدون أي نص قبله أو بعده وبدون markdown code fence:
            {"overall_task":"...","rationale":"...","subtasks":[{"id":"st1","title":"...","description":"...","assignee":"<المعرف الحقيقي>","depends_on":[]}]}
            7. كل مهمة فرعية تذكر في وصفها مسار الملف المستهدف (مثال: app/src/main/java/com/qabas/app/...) — بلا مسار لا دفع ولا بناء.
            8. التزم بمواد المستودع والبناء أدناه عند التوزيع والدمج.

            $materials
        """.trimIndent()
    }

    /**
     * برومبت المساعد الخاص: يعرّفه بدوره ومهمته المخصصة من خانة التخصيص،
     * فيعرف ما مهمته قبل أن تصله المهمة الفرعية.
     */
    private fun buildAssistantSystemPrompt(member: AgentTeamMember, materials: String = ""): String {
        val roleDesc = when (member.role) {
            AgentTeamRole.REVIEWER -> "مراجع جودة: تدقق مخرجات المنفذين، تكشف الأخطاء والثغرات، وتقترح الإصلاح الدقيق — لا تعيد تنفيذ العمل من الصفر."
            AgentTeamRole.LEADER -> "قائد مساند."
            AgentTeamRole.EXECUTOR -> "منفّذ: تنجز المهمة المسندة إليك كاملة وقابلة للاستخدام المباشر."
        }
        val custom = if (member.customInstruction.isNotBlank())
            "\nمهمتك وتخصصك المحدد من مدير الفريق: ${member.customInstruction}\nالتزم بهذا التخصص في كل مخرجاتك.\n" else ""
        return """
            أنت ${member.displayName} — ${roleDesc}
            تعمل ضمن فريق يقوده قائد أعلى؛ نفّذ ما يُسند إليك بدقة وكفاءة وقدّم مخرجات كاملة.
            $custom
            أخرج الكود كاملاً مع ذكر مسار الملف المستهدف — مخرجاتك ستُدفع للمستودع وتُختبر بالبناء.
            $materials
        """.trimIndent()
    }

    /** بناء prompt لكل مساعد — يتضمن سياق المهمة + مخرجات من سبقه إن وُجدت */
    private fun buildMemberPrompt(
        st: LeaderSubtask,
        overallPrompt: String,
        member: AgentTeamMember,
        succeededIds: Set<String>,
        priorResults: List<MemberRunResult>,
        fileContext: String = ""
    ): String {
        // 🔑 نُدرج مخرجات المهام الفرعية التي اعتمدت عليها هذه المهمة فقط
        val deps = st.dependsOn
        val relevantPrior = priorResults.filter { r ->
            r.role != AgentTeamRole.LEADER && r.isSuccess &&
                    (deps.isEmpty() || deps.contains(r.subtaskId))
        }.joinToString("\n") { "[${it.subtaskId}|${it.memberId}]: ${it.outputText.take(500)}" }

        return buildString {
            append("السياق العام للمهمة من المستخدم: $overallPrompt\n\n")
            append("المهمة الفرعية: ${st.title}\n")
            append("الوصف: ${st.description}\n")
            if (fileContext.isNotBlank()) {
                append("$fileContext\n")
            }
            if (relevantPrior.isNotBlank()) {
                append("\nمخرجات المهام السابقة التي تعتمد عليها:\n$relevantPrior\n")
            }
            append("\nقدّم مخرجاتك الآن.")
        }
    }

    /** استدعاء نموذج واحد عبر UniversalKeyBrain مع مهلة زمنية — يمرر مفتاح المزود المطابق فقط */
    private suspend fun callModel(
        member: AgentTeamMember,
        systemPrompt: String,
        userPrompt: String,
        providerKey: String = ""
    ): MemberCallResult {
        val start = System.currentTimeMillis()
        val res = withTimeoutOrNull(PER_CALL_TIMEOUT_MS) {
            UniversalKeyBrain.executeRealAgentStepWithFailover(
                provider = member.provider,
                prompt = userPrompt,
                systemInstruction = systemPrompt,
                fallbackSingleKey = providerKey,
                model = member.modelId
            )
        }
        val latency = System.currentTimeMillis() - start

        return if (res == null) {
            MemberCallResult(outputText = "", latencyMs = latency, isSuccess = false, errorMessage = "انتهت المهلة الزمنية ($PER_CALL_TIMEOUT_MS ms)")
        } else if (res.isSuccess) {
            val data = res.getOrNull()!!
            MemberCallResult(
                outputText = data.text,
                latencyMs = latency,
                isSuccess = true,
                tokensUsed = data.totalTokens
            )
        } else {
            MemberCallResult(
                outputText = "",
                latencyMs = latency,
                isSuccess = false,
                errorMessage = res.exceptionOrNull()?.localizedMessage ?: "فشل استدعاء النموذج"
            )
        }
    }

    /** تحليل خطة القائد من JSON */
    private fun parseLeaderPlan(raw: String, team: List<AgentTeamMember>): LeaderPlan? {
        return try {
            // 🔑 إزالة أي علامات code fence + استخراج JSON من نص قد يكون محاطًا بجمل
            val cleaned = raw.trim()
                .removePrefix("```json").removePrefix("```\n").removePrefix("```")
                .removeSuffix("\n```").removeSuffix("```")
                .trim()

            // استخراج JSON من نص محاط (قد يُسبق بجملة مثل "هذه خطتي:")
            val jsonStart = cleaned.indexOf('{')
            val jsonEnd = cleaned.lastIndexOf('}')
            val jsonCandidate = if (jsonStart >= 0 && jsonEnd > jsonStart) {
                cleaned.substring(jsonStart, jsonEnd + 1)
            } else cleaned

            val obj = org.json.JSONObject(jsonCandidate)
            val subtasksArr = obj.optJSONArray("subtasks") ?: return null
            val subtasks = (0 until subtasksArr.length()).mapNotNull { i ->
                val o = subtasksArr.optJSONObject(i) ?: return@mapNotNull null
                LeaderSubtask(
                    id = o.optString("id", "st$i"),
                    title = o.optString("title"),
                    description = o.optString("description"),
                    assigneeMemberId = o.optString("assignee", o.optString("assignee_member_id")),
                    dependsOn = o.optJSONArray("depends_on")?.let { arr ->
                        (0 until arr.length()).map { arr.optString(it) }
                    } ?: emptyList()
                )
            }
            if (subtasks.isEmpty()) null
            else LeaderPlan(
                overallTask = obj.optString("overall_task"),
                subtasks = subtasks,
                rationale = obj.optString("rationale")
            )
        } catch (e: Exception) {
            Log.w(TAG_ORCH, "Failed to parse leader plan: ${e.message}")
            null
        }
    }
}

/** نتيجة استدعاء نموذج واحد داخلي (لا تُستخدم خارج Orchestrator) */
private data class MemberCallResult(
    val outputText: String,
    val latencyMs: Long = 0,
    val isSuccess: Boolean,
    val errorMessage: String? = null,
    val tokensUsed: Int = 0
)
