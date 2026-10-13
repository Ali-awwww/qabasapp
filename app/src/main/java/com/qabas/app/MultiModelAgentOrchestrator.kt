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
    val enabled: Boolean = true
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

/** التقرير النهائي للموزع */
data class MultiModelRunReport(
    val overallPrompt: String,
    val team: List<AgentTeamMember>,
    val leaderPlan: LeaderPlan? = null,
    val memberResults: List<MemberRunResult> = emptyList(),
    val leaderSynthesis: String = "",
    val totalLatencyMs: Long = 0,
    val overallSuccess: Boolean = false,
    val warnings: List<String> = emptyList()
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
    fun loadTeam(context: Context, onlyEnabled: Boolean = false): List<AgentTeamMember> {
        val prefs = context.getSharedPreferences("qabas_agent_team", Context.MODE_PRIVATE)
        val teamJson = prefs.getString("team_config_json", null) ?: return defaultTeam()
        return try {
            val arr = org.json.JSONArray(teamJson)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                AgentTeamMember(
                    id = o.optString("id"),
                    displayName = o.optString("name"),
                    provider = DetectedProvider.entries.firstOrNull { it.name == o.optString("provider") } ?: DetectedProvider.NONE,
                    modelId = o.optString("model"),
                    role = AgentTeamRole.entries.firstOrNull { it.name == o.optString("role") } ?: AgentTeamRole.EXECUTOR,
                    rank = o.optInt("rank", 99),
                    enabled = o.optBoolean("enabled", true)
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
                put("name", m.displayName)
                put("provider", m.provider.name)
                put("model", m.modelId)
                put("role", m.role.name)
                put("rank", m.rank)
                put("enabled", m.enabled)
            })
        }
        prefs.edit().putString("team_config_json", arr.toString()).apply()
    }

    /** فريق افتراضي (يُعاد استخدامه إذا لم تكن هناك إعدادات محفوظة) */
    fun defaultTeam(): List<AgentTeamMember> = listOf(
        AgentTeamMember("leader_1", "القائد (Claude)", DetectedProvider.OPENROUTER, "anthropic/claude-sonnet-4.5", AgentTeamRole.LEADER, rank = 1),
        AgentTeamMember("exec_1", "المساعد 1 (Gemini)", DetectedProvider.GEMINI, "gemini-3.5-flash", AgentTeamRole.EXECUTOR, rank = 2),
        AgentTeamMember("exec_2", "المساعد 2 (Groq)", DetectedProvider.GROQ, "llama-3.3-70b-versatile", AgentTeamRole.EXECUTOR, rank = 3)
    )

    /**
     * تنفيذ مهمة باستخدام فريق متعدد النماذج
     * التسلسل: (1) القائد يُصدر خطة → (2) المساعدون ينفذون بالتوازي → (3) القائد يُجمع النتيجة
     *
     * @param context سياق التطبيق لقراءة المفاتيح
     * @param prompt المهمة من المستخدم
     * @param team فريق الوكيل (يجب أن يحتوي على قائد واحد على الأقل)
     * @param onUpdate استدعاء متكرر يمرر نص حالة تقدم حي (سطر لكل عضو)
     * @return تقرير شامل عن النتيجة
     */
    suspend fun executeWithTeam(
        context: Context,
        prompt: String,
        teamRaw: List<AgentTeamMember>,
        onUpdate: (String) -> Unit = {}
    ): MultiModelRunReport = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val warnings = mutableListOf<String>()

        // 🔑 نستخدم الأعضاء المفعّلين فقط للتنفيذ
        val team = teamRaw.filter { it.enabled }

        // 🔑 حل مفتاح لكل مزود على حدة (لا نمرر مفتاح مزود لمزود آخر)
        val providerKeys = mutableMapOf<DetectedProvider, String>()
        team.map { it.provider }.distinct().forEach { p ->
            val prefsKey = UniversalKeyBrain.prefsKeyFor(p)
            if (prefsKey.isNotBlank()) {
                val keys = KeyPoolManager.orderedKeysBySpeed(prefsKey)
                if (keys.isNotEmpty()) providerKeys[p] = keys.first()
            }
        }

        val leader = team.firstOrNull { it.role == AgentTeamRole.LEADER }
        if (leader == null) {
            return@withContext MultiModelRunReport(
                overallPrompt = prompt,
                team = team,
                totalLatencyMs = System.currentTimeMillis() - startTime,
                overallSuccess = false,
                warnings = listOf("لا يوجد نموذج قائد في الفريق! يجب تعيين LEADER واحد على الأقل.")
            )
        }

        val assistants = team.filter { it.id != leader.id && it.role != AgentTeamRole.LEADER }.sortedBy { it.rank }
        if (assistants.isEmpty()) {
            warnings.add("لا يوجد مساعدون في الفريق — سيقوم القائد بتنفيذ المهمة وحده.")
        }

        // ═══ المرحلة 1: القائد يُصدر خطة عمل ═══
        currentCoroutineContext().ensureActive() // 🔑 تحقق من عدم إلغاء المهمة قبل البدء
        onUpdate("👑 ${leader.displayName}: جاري التخطيط...")
        val leaderSystemPrompt = """
            أنت قائد فريق مهندسين ذكاء اصطناعي (Leader AI Model).
            لديك ${assistants.size} مساعد${if (assistants.size == 1) "" else "ين"}:
            ${assistants.joinToString("\n") { "- معرف المساعد: `${it.id}` — الاسم: ${it.displayName} — الدور: ${it.role.label} — الرتبة: #${it.rank} — المزود: ${it.provider.displayName} — النموذج: ${it.modelId}" }}.
            مهمتك: توزيع المهمة التالية على المساعدين بناءً على قدراتهم.

            المهمة من المستخدم: "$prompt"

            أخرج خطتك بصيغة JSON فقط (بدون markdown code fence) بالشكل:
            {"overall_task":"...","rationale":"...","subtasks":[{"id":"st1","title":"...","description":"...","assignee":"<معرف المساعد الحقيقي>"}]}

            يجب أن يحتوي assignee على المعرف الحقيقي للمساعد من القائمة أعلاه (مثل `${assistants.firstOrNull()?.id ?: "exec_1"}`). لا تضع مهمة لنفسك كقائد، فقط للمساعدين.
        """.trimIndent()

        val planResult = callModel(leader, leaderSystemPrompt, prompt, providerKeys[leader.provider] ?: "")
        val leaderPlan: LeaderPlan? = if (planResult.isSuccess) {
            parseLeaderPlan(planResult.outputText, team)
        } else null

        if (leaderPlan == null) {
            warnings.add("تعذر توليد خطة صالحة من القائد — سيتم تنفيذ المساعدين مباشرة على المهمة الأصلية.")
            onUpdate("⚠️ ${leader.displayName}: تعذر توليد خطة صالحة — سيتم تنفيذ المساعدين على المهمة الأصلية.")
        } else {
            onUpdate("✅ ${leader.displayName}: تم توليد الخطة (${leaderPlan.subtasks.size} مهمة فرعية).")
        }

        // ═══ المرحلة 2: تنفيذ المساعدين (بالتوازي أو بالتسلسل حسب التبعيات) ═══
        currentCoroutineContext().ensureActive() // 🔑 تحقق من عدم إلغاء المهمة قبل المرحلة الثانية
        val memberResults = mutableListOf<MemberRunResult>()
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

            // مراعاة التبعيات: ننفذ على دفعات (batch) حسب dependsOn
            // 🔑 نستخدم succeededIds للمهام الناجحة فقط — المهمة الفاشلة تُوقف تبعاتها
            val succeededIds = mutableSetOf<String>()
            val failedIds = mutableSetOf<String>()
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

                // تنفيذ الدفعة بالتوازي
                onUpdate("⚙️ تنفيذ ${batch.size} مهمة فرعية بالتوازي: ${batch.joinToString("، ") { it.title.take(30) }}")

                coroutineScope {
                    val deferreds = batch.map { st ->
                        val member = memberById[st.assigneeMemberId]!!
                        async {
                            onUpdate("⚡ ${member.displayName}: جاري تنفيذ «${st.title.take(40)}»...")
                            val r = callModel(
                                member,
                                "أنت مساعد${if (member.role == AgentTeamRole.REVIEWER) " مراجع جودة" else " تنفيذي"} في فريق مهندسين AI. نفّذ المهمة المطلوبة بدقة وكفاءة، وقدّم مخرجات كاملة قابلة للاستخدام.",
                                buildMemberPrompt(st, prompt, succeededIds, memberResults),
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
                        onUpdate("$statusIcon ${member.displayName}: اكتمل «${st.title.take(30)}» (${r.latencyMs}ms)")
                    }
                }
            }
        }

        // ═══ المرحلة 3: القائد يجمع النتيجة النهائية ═══
        currentCoroutineContext().ensureActive() // 🔑 تحقق من عدم إلغاء المهمة قبل المرحلة الثالثة
        onUpdate("👑 ${leader.displayName}: جاري صياغة الخلاصة النهائية...")
        val successfulOutputs = memberResults.filter { it.isSuccess && it.role != AgentTeamRole.LEADER }
            .joinToString("\n\n---\n\n") { "[${it.memberId}] ${it.outputText}" }

        val synthesisResult = if (successfulOutputs.isNotBlank()) {
            callModel(
                leader,
                "أنت القائد — اجمع مخرجات المساعدين التالية في تقرير نهائي واحد متسق ومُحكم يحقق المهمة الأصلية. لا تكرر نفس النص، بل ادمج وأعد صياغة لتقديم إجابة نهائية موحدة.",
                "المهمة الأصلية: $prompt\n\nمخرجات المساعدين:\n$successfulOutputs\n\nأنتج التقرير النهائي الآن.",
                providerKeys[leader.provider] ?: ""
            )
        } else {
            MemberCallResult(outputText = "تعذر إنتاج مخرجات من أي مساعد.", isSuccess = false)
        }

        // 🔑 النجاح يتطلب نجاح مساعد واحد على الأقل (لا نكتفي بنجاح خطة القائد)
        val assistantSuccessCount = memberResults.count { it.isSuccess && it.role != AgentTeamRole.LEADER }
        val anySuccess = assistantSuccessCount > 0 && synthesisResult.isSuccess

        MultiModelRunReport(
            overallPrompt = prompt,
            team = team,
            leaderPlan = leaderPlan,
            memberResults = memberResults,
            leaderSynthesis = synthesisResult.outputText,
            totalLatencyMs = System.currentTimeMillis() - startTime,
            overallSuccess = anySuccess,
            warnings = warnings
        )
    }

    /** بناء prompt لكل مساعد — يتضمن سياق المهمة + مخرجات من سبقه إن وُجدت */
    private fun buildMemberPrompt(
        st: LeaderSubtask,
        overallPrompt: String,
        succeededIds: Set<String>,
        priorResults: List<MemberRunResult>
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
