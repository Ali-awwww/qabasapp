package com.qabas.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * كيان «مشروع فايب كودينج» الرفيع (12.10): يربط ما هو موجود — جلسة
 * محادثة + مستودع نشط + ملفات معدلة من المهمة النشطة
 * (AgentMissionSession.proposedDiffs/appliedPaths عبر modifiedFiles
 * المحفوظة في active_mission_json) — دون إعادة كتابة تخزين الجلسات
 * ولا مكتبة المحتوى.
 *
 * التخزين على مخزن Room الموجود (ProjectService) لا مخزن خامس:
 * مشروع ProjectService.Project بحالة vibe_coding، ورابطه JSON في
 * workspaceJson — وهو يُحفظ في عمود settings عبر
 * ProjectWorkspaceCodec.encodeForLocalStorage، ويعود سليماً لأن
 * decodeStored يعيد أي نص JSON كما هو.
 */
data class VibeCodingProject(
    val id: String,
    val title: String,
    val prompt: String,
    val sessionId: String?,
    val repoFullName: String?,
    val branch: String?,
    val modifiedFiles: List<String>,
    val appliedPaths: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
    /** ذاكرة الوكيل الخفيفة (12.12): ملخص + قرارات + ملفات مهمة. */
    val memory: VibeAgentMemory = VibeAgentMemory.EMPTY,
    /** ملفات قالب بادئ (12.15): مسار → محتوى أولي فعلي، تُخزن مع المشروع. */
    val starterFiles: Map<String, String> = emptyMap()
) {
    fun toJson(): String = JSONObject().apply {
        put("kind", "vibe_coding")
        put("id", id)
        put("title", title)
        put("prompt", prompt)
        put("sessionId", sessionId)
        put("repoFullName", repoFullName)
        put("branch", branch)
        put("modifiedFiles", JSONArray(modifiedFiles))
        put("appliedPaths", JSONArray(appliedPaths))
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("memory", memory.toJson())
        put("starterFiles", JSONObject().apply {
            starterFiles.forEach { (path, content) -> put(path, content) }
        })
    }.toString()

    companion object {
        fun fromJson(raw: String): VibeCodingProject? = runCatching {
            val obj = JSONObject(raw)
            if (obj.optString("kind") != "vibe_coding") return null
            fun strings(key: String): List<String> {
                val arr = obj.optJSONArray(key) ?: return emptyList()
                return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
            }
            fun stringMap(key: String): Map<String, String> {
                val mapObj = obj.optJSONObject(key) ?: return emptyMap()
                val out = mutableMapOf<String, String>()
                val keys = mapObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    mapObj.optString(k).takeIf { it.isNotBlank() }?.let { out[k] = it }
                }
                return out
            }
            VibeCodingProject(
                id = obj.optString("id"),
                title = obj.optString("title"),
                prompt = obj.optString("prompt"),
                sessionId = obj.optString("sessionId").takeIf { it.isNotBlank() },
                repoFullName = obj.optString("repoFullName").takeIf { it.isNotBlank() },
                branch = obj.optString("branch").takeIf { it.isNotBlank() },
                modifiedFiles = strings("modifiedFiles"),
                appliedPaths = strings("appliedPaths"),
                createdAt = obj.optLong("createdAt", 0L),
                updatedAt = obj.optLong("updatedAt", 0L),
                memory = VibeAgentMemory.fromJson(obj.optString("memory")),
                starterFiles = stringMap("starterFiles")
            )
        }.getOrNull()
    }
}

object VibeCodingProjects {

    const val STATUS = "vibe_coding"

    fun fromServiceProject(project: ProjectService.Project): VibeCodingProject? {
        if (project.status != STATUS) return null
        return VibeCodingProject.fromJson(project.workspaceJson)
    }

    fun toServiceProject(vibe: VibeCodingProject): ProjectService.Project =
        ProjectService.Project(
            id = vibe.id,
            title = vibe.title,
            idea = vibe.prompt,
            status = STATUS,
            workspaceJson = vibe.toJson(),
            createdAt = vibe.createdAt,
            updatedAt = vibe.updatedAt
        )

    suspend fun list(context: Context): List<VibeCodingProject> = withContext(Dispatchers.IO) {
        runCatching { ProjectService(context).getLocalProjects() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .getOrDefault(emptyList())
            .mapNotNull { fromServiceProject(it) }
            .sortedByDescending { it.updatedAt }
    }

    /**
     * نتيجة الإنشاء: null القديمة كانت تخلط «لا شيء يربط» بفشل الحفظ
     * فتظهر رسالة «لا توجد جلسة» حتى حين توجد جلسة ويفشل الحفظ.
     */
    sealed interface CreateResult {
        data class Created(val project: VibeCodingProject) : CreateResult
        object NothingToLink : CreateResult
        object SaveFailed : CreateResult
    }

    /**
     * ينشئ مشروع فايب كودينج من الجلسة النشطة + المستودع النشط + المهمة
     * النشطة المحفوظة (active_mission_json: الموجه + الملفات المعدلة).
     * يُعيد NothingToLink إن لم توجد جلسة ولا مهمة ولا مستودع يربطها — لا مشروع فارغ.
     */
    suspend fun createFromActiveSession(context: Context): CreateResult =
        withContext(Dispatchers.IO) {
            val sessionId = AgentChatSessionManager.activeSessionId.value
            val session = AgentChatSessionManager.sessions.value
                .firstOrNull { it.id == sessionId }
            val repo = RepoVaultManager.getActiveRepo(context)
            var prompt = ""
            val modified = mutableListOf<String>()
            val applied = mutableListOf<String>()
            val missionRaw = AgentPrefs.loadActiveMission(context)
            if (missionRaw.isNotBlank()) {
                runCatching {
                    val obj = JSONObject(missionRaw)
                    prompt = obj.optString("userPrompt", "")
                    obj.optJSONArray("modifiedFiles")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            arr.optString(i).takeIf { it.isNotBlank() }?.let { modified += it }
                        }
                    }
                    obj.optJSONArray("appliedPaths")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            arr.optString(i).takeIf { it.isNotBlank() }?.let { applied += it }
                        }
                    }
                }
            }
            if (session == null && prompt.isBlank() && repo == null) return@withContext CreateResult.NothingToLink
            val now = System.currentTimeMillis()
            val title = session?.title
                ?.takeIf { it.isNotBlank() && !it.startsWith("محادثة جديدة") }
                ?: prompt.take(40).ifBlank { "مشروع فايب كودينج" }
            val vibe = VibeCodingProject(
                id = UUID.randomUUID().toString(),
                title = title,
                prompt = prompt,
                sessionId = sessionId,
                repoFullName = repo?.fullName,
                branch = repo?.defaultBranch,
                modifiedFiles = modified,
                appliedPaths = applied,
                createdAt = now,
                updatedAt = now
            )
            val saved = runCatching { ProjectService(context).saveProject(toServiceProject(vibe)) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                .isSuccess
            if (!saved) return@withContext CreateResult.SaveFailed
            CreateResult.Created(vibe)
        }

    /** يحذف المشروع، ويُعيد true فقط إن اختفى فعلاً من Room بعدها. */
    suspend fun delete(context: Context, id: String): Boolean = withContext(Dispatchers.IO) {
        val service = ProjectService(context)
        val deleted = runCatching { service.deleteProject(id) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.isSuccess
        if (!deleted) return@withContext false
        // deleteProject يبتلع التأجيل داخلياً (يعيد Unit حتى حين يتأجل
        // الحذف) — فالتحقق هنا بقراءة الصف لا بنجاح الاستدعاء.
        runCatching { service.getProject(id) }.fold(
            onSuccess = { it == null },
            onFailure = { false }
        )
    }

    /**
     * ينشئ مشروع فايب كودينج من قالب بادئ (12.15): ملفات القالب
     * الأولية تُخزن مع المشروع، وموجه البدء يصبح موجه المشروع
     * (يُنسخ أو يُرسل من تبويب المحادثة). بلا جلسة ولا مستودع بعد —
     * يربطهما الوكيل عند تنفيذ أول مهمة.
     */
    suspend fun createFromTemplate(
        context: Context,
        template: VibeTemplate
    ): VibeCodingProject? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val vibe = VibeCodingProject(
            id = UUID.randomUUID().toString(),
            title = template.title,
            prompt = template.startPrompt,
            sessionId = null,
            repoFullName = null,
            branch = null,
            // (ق66) ملفات القالب بادئة لا معدّلة: وضع أسمائها في
            // modifiedFiles كان يعرض «ملفات معدلة: 3» لمشروع جديد لم
            // يُعدَّل فيه شيء، ويجعل التصدير يحاول جلبها من GitHub
            // (404)، وقد ينسب مهمة غريبة للمشروع بمطابقة الاسم. البادئة
            // مكانها starterFiles وحدها.
            modifiedFiles = emptyList(),
            appliedPaths = emptyList(),
            createdAt = now,
            updatedAt = now,
            starterFiles = template.files
        )
        runCatching { ProjectService(context).saveProject(toServiceProject(vibe)) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .getOrNull() ?: return@withContext null
        vibe
    }

    /** يحفظ ذاكرة الوكيل لمشروع فايب كودينج (12.12) على مخزن Room الموجود. */
    suspend fun updateMemory(
        context: Context,
        projectId: String,
        memory: VibeAgentMemory
    ): Boolean = withContext(Dispatchers.IO) {
        val service = ProjectService(context)
        val current = runCatching { service.getProject(projectId) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
            ?: return@withContext false
        val vibe = fromServiceProject(current) ?: return@withContext false
        val updated = vibe.copy(
            memory = memory,
            updatedAt = System.currentTimeMillis()
        )
        runCatching { service.saveProject(toServiceProject(updated)) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.isSuccess
    }

    /**
     * تصدير مشروع فايب كودينج (12.17): حزمة JSON واحدة تضم المشروع
     * (جلسة + مستودع + ملفات معدلة) + المهمة المحفوظة + رسائل الجلسة
     * + محتوى الملفات المعدلة مجلوباً من GitHub (أول 8 ملفات، حتى 30 ألف
     * محرف لكل ملف؛ ما تعذر جلبه يُذكر كذلك بصدق لا بمحتوى مختلق).
     *
     * لا تُصدر مفاتيح API أبداً: لا يُقرأ أي مفتاح هنا أصلاً، ونصوص
     * المحادثة والملفات تُمرر عبر [redactSecrets] الذي يخفي صيغ المفاتيح
     * الشائعة إن لصقها المستخدم يوماً في المحادثة أو الكود.
     */
    suspend fun buildExportJson(context: Context, vibe: VibeCodingProject): String =
        withContext(Dispatchers.IO) {
            val root = JSONObject()
            root.put("kind", "vibe_coding_export")
            root.put("version", 1)
            root.put("exportedAt", System.currentTimeMillis())
            // ق110: موجه المشروع وذاكرته وملفاته البادئة نصوص حرة قد تحوي
            // مفاتيح لُصقت — كانت تُصدَّر خاماً داخل "project" رغم تنقيح
            // نسخة starterFiles المنفصلة؛ تُنقَّح هنا قبل الإدراج.
            val projectObj = JSONObject(vibe.toJson())
            projectObj.put("prompt", redactSecrets(vibe.prompt))
            projectObj.put("memory", JSONObject().apply {
                put("summary", redactSecrets(vibe.memory.summary))
                put("decisions", JSONArray(vibe.memory.decisions.map { redactSecrets(it) }))
                put("importantFiles", JSONArray(vibe.memory.importantFiles))
                put("updatedAt", vibe.memory.updatedAt)
            })
            if (vibe.starterFiles.isNotEmpty()) {
                val starterObj = JSONObject()
                vibe.starterFiles.forEach { (path, content) -> starterObj.put(path, redactSecrets(content)) }
                projectObj.put("starterFiles", starterObj)
            }
            root.put("project", projectObj)
            if (vibe.starterFiles.isNotEmpty()) {
                val starterArr = JSONArray()
                vibe.starterFiles.forEach { (path, content) ->
                    starterArr.put(
                        JSONObject().apply {
                            put("path", path)
                            put("content", redactSecrets(content))
                        }
                    )
                }
                root.put("starterFiles", starterArr)
            }
            // المهمة النشطة عمومية لا مشروعية: بلا رابط جلسة/مشروع في
            // JSON، وقد تخص مشروعاً آخر تماماً. تُضمَّن فقط حين يطابق
            // موجهها موجه المشروع أو تتداخل ملفاتها مع ملفاته؛ وإلا
            // تُحذف بملاحظة صادقة بدل نسبة مهمة غريبة للمشروع.
            val missionRaw = AgentPrefs.loadActiveMission(context)
            if (missionRaw.isNotBlank()) {
                runCatching {
                    val missionObj = JSONObject(missionRaw)
                    val missionPrompt = missionObj.optString("userPrompt", "")
                    val missionFiles = mutableListOf<String>()
                    missionObj.optJSONArray("modifiedFiles")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            arr.optString(i).takeIf { it.isNotBlank() }?.let { missionFiles += it }
                        }
                    }
                    val belongsToProject =
                        (missionPrompt.isNotBlank() && missionPrompt == vibe.prompt) ||
                            missionFiles.any { it in vibe.modifiedFiles || it in vibe.appliedPaths }
                    if (belongsToProject) {
                        root.put("mission", redactMission(missionObj))
                    } else {
                        root.put("missionNote", "المهمة النشطة حالياً تخص مشروعاً آخر فلم تُضمَّن في هذا التصدير.")
                    }
                }
            }
            val messagesArr = JSONArray()
            vibe.sessionId?.let { sessionId ->
                val allMessages = runCatching { AgentChatSessionManager.loadSessionMessages(context, sessionId) }
                    .getOrDefault(emptyList())
                // ق110: اقتطاع صامت — فوق 50 رسالة كانت الأقدم تسقط بلا
                // أي إشارة فيُقرأ التصدير كاملاً وهو ناقص.
                if (allMessages.size > 50) {
                    root.put("sessionMessagesNote", "عُرضت أحدث 50 رسالة من ${allMessages.size} — الأقدم لم تُضمَّن.")
                }
                allMessages
                    // الرسائل زمنية (الأقدم أولاً) فـ take(50) القديمة كانت
                    // تأخذ الأقدم وتسقط الأحدث — الأهم في السياق.
                    .takeLast(50)
                    .forEach { msg ->
                        messagesArr.put(
                            JSONObject().apply {
                                put("sender", msg.sender.name)
                                put("text", redactSecrets(msg.text.take(2000)))
                            }
                        )
                    }
            }
            root.put("sessionMessages", messagesArr)
            val filesArr = JSONArray()
            val repo = RepoVaultManager.getActiveRepo(context)
            val parts = vibe.repoFullName?.split("/")?.filter { it.isNotBlank() }
            if (repo != null && parts != null && parts.size == 2 && repo.fullName == vibe.repoFullName) {
                val client = GitHubRepoClient(repo.owner, repo.repo, repo.token)
                val allPaths = (vibe.modifiedFiles + vibe.appliedPaths).distinct()
                // ق110: اقتطاع صامت — فوق 8 ملفات كانت الباقية تسقط بلا
                // أي إشارة فيُقرأ التصدير كاملاً وهو ناقص.
                if (allPaths.size > 8) {
                    root.put("filesNote", "عُرضت أول 8 ملفات من ${allPaths.size} — باقي الملفات لم تُضمَّن.")
                }
                val paths = allPaths.take(8)
                for (path in paths) {
                    val obj = JSONObject().apply { put("path", path) }
                    val content = runCatching {
                        client.getFileContent(path, vibe.branch ?: repo.defaultBranch)
                            ?.content
                            ?.let {
                                String(
                                    android.util.Base64.decode(
                                        it.replace("\\s".toRegex(), ""),
                                        android.util.Base64.DEFAULT
                                    ),
                                    Charsets.UTF_8
                                )
                            }
                    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
                    if (content.isNullOrBlank()) {
                        obj.put("error", "تعذر جلب المحتوى من المستودع.")
                    } else {
                        obj.put("content", redactSecrets(content.take(30_000)))
                    }
                    filesArr.put(obj)
                }
            } else if (vibe.modifiedFiles.isNotEmpty()) {
                filesArr.put(
                    JSONObject().apply {
                        put("error", "لا يوجد مستودع نشط مطابق لجلب محتوى الملفات.")
                    }
                )
            }
            root.put("files", filesArr)
            root.toString()
        }

    /**
     * ق110: المهمة نصوص حرة (الموجه، التقرير النهائي، عناوين الخطوات
     * ومقتطفات مخرجاتها، نصوص الفروقات) — كانت تُدرج خاماً في التصدير
     * رغم أن الرسائل والملفات تُنقَّح؛ تُنسخ وتُنقَّح هنا قبل الإدراج.
     */
    private fun redactMission(missionObj: JSONObject): JSONObject {
        val out = JSONObject(missionObj.toString())
        out.optString("userPrompt").takeIf { it.isNotBlank() }?.let { out.put("userPrompt", redactSecrets(it)) }
        out.optString("finalReport").takeIf { it.isNotBlank() }?.let { out.put("finalReport", redactSecrets(it)) }
        out.optJSONArray("steps")?.let { steps ->
            for (i in 0 until steps.length()) {
                val step = steps.optJSONObject(i) ?: continue
                step.optString("title").takeIf { it.isNotBlank() }?.let { step.put("title", redactSecrets(it)) }
                step.optString("description").takeIf { it.isNotBlank() }?.let { step.put("description", redactSecrets(it)) }
                step.optString("outputSnippet").takeIf { it.isNotBlank() }?.let { step.put("outputSnippet", redactSecrets(it)) }
            }
        }
        out.optJSONArray("proposedDiffs")?.let { diffs ->
            for (i in 0 until diffs.length()) {
                val diff = diffs.optJSONObject(i) ?: continue
                diff.optString("diffText").takeIf { it.isNotBlank() }?.let { diff.put("diffText", redactSecrets(it)) }
            }
        }
        return out
    }

    /** يخفي صيغ مفاتيح API الشائعة من نص قبل تصديره. */
    fun redactSecrets(text: String): String {
        var out = text
        val patterns = listOf(
            Regex("sk-or-[A-Za-z0-9_\\-]{8,}"),
            Regex("sk-ant-[A-Za-z0-9_\\-]{8,}"),
            Regex("gsk_[A-Za-z0-9_\\-]{8,}"),
            Regex("hf_[A-Za-z0-9_\\-]{8,}"),
            Regex("AIza[A-Za-z0-9_\\-]{10,}"),
            Regex("ghp_[A-Za-z0-9_\\-]{8,}"),
            Regex("github_pat_[A-Za-z0-9_]{8,}"),
            Regex("sk-[A-Za-z0-9_\\-]{8,}"),
            // مفتاح خدمة Supabase (service_role) بصيغة sbp_ — كان يُصدَّر
            // واضحاً إن لُصق في محادثة أو كود.
            Regex("sbp_[A-Za-z0-9]{20,}"),
            // رمز JWT (مفتاح Supabase المجهول/الخدمة بصيغة eyJ...) — كان
            // يُصدَّر واضحاً إن لُصق في محادثة أو كود.
            Regex("eyJ[A-Za-z0-9_\\-]+\\.eyJ[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+"),
            // مفتاح خاص PEM: يُخفى الترويسة والجسم كاملاً لا السطر الأول فقط.
            Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----")
        )
        for (pattern in patterns) {
            out = pattern.replace(out, "[مفتاح مخفي]")
        }
        return out
    }

    /** مشاركة حزمة التصدير عبر Intent (نص JSON) — بلا مفاتيح إطلاقاً. */
    fun shareExport(context: Context, json: String, title: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "مشروع فايب كودينج: $title")
            putExtra(android.content.Intent.EXTRA_TEXT, json)
        }
        // بلا حماية كان فشل المشاركة (لا تطبيق يستقبل، أو حزمة تتجاوز
        // حد Binder ~1MB) يسقط التطبيق؛ الآن رسالة صادقة بدل الانهيار.
        runCatching {
            context.startActivity(
                android.content.Intent.createChooser(intent, "مشاركة مشروع فايب كودينج")
            )
        }.onFailure {
            android.widget.Toast.makeText(
                context,
                "تعذرت المشاركة — جرّب تطبيق مشاركة آخر، أو صدّر مشروعاً أصغر.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
}
