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
     * ينشئ مشروع فايب كودينج من الجلسة النشطة + المستودع النشط + المهمة
     * النشطة المحفوظة (active_mission_json: الموجه + الملفات المعدلة).
     * يُعيد null إن لم توجد جلسة ولا مهمة ولا مستودع يربطها — لا مشروع فارغ.
     */
    suspend fun createFromActiveSession(context: Context): VibeCodingProject? =
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
            if (session == null && prompt.isBlank() && repo == null) return@withContext null
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
            runCatching { ProjectService(context).saveProject(toServiceProject(vibe)) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                .getOrNull() ?: return@withContext null
            vibe
        }

    suspend fun delete(context: Context, id: String) = withContext(Dispatchers.IO) {
        runCatching { ProjectService(context).deleteProject(id) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
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
            modifiedFiles = template.files.keys.toList(),
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
            root.put("project", JSONObject(vibe.toJson()))
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
            val missionRaw = AgentPrefs.loadActiveMission(context)
            if (missionRaw.isNotBlank()) {
                runCatching { root.put("mission", JSONObject(missionRaw)) }
            }
            val messagesArr = JSONArray()
            vibe.sessionId?.let { sessionId ->
                runCatching { AgentChatSessionManager.loadSessionMessages(context, sessionId) }
                    .getOrDefault(emptyList())
                    .take(50)
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
                val paths = (vibe.modifiedFiles + vibe.appliedPaths).distinct().take(8)
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
            Regex("sk-[A-Za-z0-9_\\-]{8,}")
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
        context.startActivity(
            android.content.Intent.createChooser(intent, "مشاركة مشروع فايب كودينج")
        )
    }
}
