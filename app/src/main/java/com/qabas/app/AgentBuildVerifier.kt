package com.qabas.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 🤖 حلقة التنفيذ الفعلي (Execution & Verification Loop):
 * تُطلق بناءً حقيقياً على GitHub Actions، تراقبه حتى الاكتمال، وتُرجع النتيجة —
 * فتُغلق الفجوة بين «توليد الكود» و«معرفة أنه يعمل فعلاً».
 *
 * كل الدوال النقية (classify / parseRuns / pickRunForCommit / describe) قابلة للاختبار
 * دون شبكة، والشبكة معزولة في fetchRuns/triggerWorkflow فحسب.
 */
enum class BuildRunOutcome { QUEUED, RUNNING, SUCCESS, FAILURE, CANCELLED, UNKNOWN }

data class BuildRunSnapshot(
    val id: Long,
    val name: String,
    val headSha: String,
    val status: String,
    val conclusion: String?,
    val htmlUrl: String
) {
    val outcome: BuildRunOutcome get() = AgentBuildVerifier.classify(status, conclusion)
}

data class BuildVerification(
    val outcome: BuildRunOutcome,
    val run: BuildRunSnapshot?,
    val commitSha: String,
    val message: String,
    val failedLogs: String = "",
    val timedOut: Boolean = false
) {
    val isSuccess: Boolean get() = outcome == BuildRunOutcome.SUCCESS
}

object AgentBuildVerifier {
    private const val TAG = "AgentBuildVerifier"
    const val DEFAULT_WORKFLOW = "android-ci.yml"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // ───────────────────────── دوال نقية (قابلة للاختبار) ─────────────────────────

    /** تصنيف حالة تشغيل GitHub إلى نتيجة معيارية موحّدة. */
    fun classify(status: String, conclusion: String?): BuildRunOutcome {
        val s = status.trim().lowercase()
        val c = conclusion?.trim()?.lowercase().orEmpty()
        if (s != "completed") {
            return when (s) {
                "queued", "requested", "waiting", "pending" -> BuildRunOutcome.QUEUED
                "in_progress", "running" -> BuildRunOutcome.RUNNING
                else -> BuildRunOutcome.RUNNING
            }
        }
        return when (c) {
            "success" -> BuildRunOutcome.SUCCESS
            "failure", "timed_out", "startup_failure", "action_required" -> BuildRunOutcome.FAILURE
            "cancelled", "neutral", "skipped", "stale" -> BuildRunOutcome.CANCELLED
            else -> BuildRunOutcome.UNKNOWN
        }
    }

    /** تفكيك استجابة `workflow_runs` إلى قائمة لقطات. */
    fun parseRuns(json: String): List<BuildRunSnapshot> {
        return try {
            val arr = JSONObject(json).optJSONArray("workflow_runs") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                BuildRunSnapshot(
                    id = o.optLong("id"),
                    name = o.optString("name", "CI"),
                    headSha = o.optString("head_sha", ""),
                    status = o.optString("status", ""),
                    conclusion = o.optString("conclusion").takeIf { it.isNotBlank() },
                    htmlUrl = o.optString("html_url", "")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** أحدث تشغيل يطابق كوميت رأس الفرع (أو الأحدث عموماً عند غياب الـSHA). */
    fun pickRunForCommit(runs: List<BuildRunSnapshot>, headSha: String): BuildRunSnapshot? {
        if (headSha.isBlank()) return runs.firstOrNull()
        return runs.firstOrNull { it.headSha == headSha }
    }

    /** هل انتهى التشغيل (نجاح أو فشل أو إلغاء)؟ */
    fun isFinished(outcome: BuildRunOutcome): Boolean =
        outcome == BuildRunOutcome.SUCCESS ||
            outcome == BuildRunOutcome.FAILURE ||
            outcome == BuildRunOutcome.CANCELLED

    /** وصف عربي موجز لحالة التشغيل. */
    fun describe(run: BuildRunSnapshot?): String {
        val name = run?.name ?: "CI"
        return when (run?.outcome) {
            BuildRunOutcome.SUCCESS -> "✅ نجح البناء الفعلي على GitHub ($name)"
            BuildRunOutcome.FAILURE -> "❌ فشل البناء الفعلي على GitHub ($name)"
            BuildRunOutcome.CANCELLED -> "⏹️ أُلغي البناء ($name)"
            BuildRunOutcome.RUNNING -> "⚡ البناء قيد التنفيذ الآن ($name)"
            BuildRunOutcome.QUEUED -> "⏳ البناء في قائمة الانتظار ($name)"
            else -> "❔ لم يبدأ أي تشغيل بعد"
        }
    }

    // ───────────────────────── شبكة GitHub ─────────────────────────

    private fun authGet(owner: String, repo: String, token: String, path: String): String? {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo$path")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Qabas-Studio")
            .header("Authorization", "Bearer ${token.trim()}")
            .get()
            .build()
        return try {
            client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
        } catch (_: Exception) {
            null
        }
    }

    /** كوميت رأس الفرع الحالي — لتثبيت المراقبة على النسخة الصحيحة. */
    suspend fun fetchHeadCommitSha(owner: String, repo: String, token: String, branch: String): String? =
        withContext(Dispatchers.IO) {
            val raw = authGet(owner, repo, token, "/commits/${branch.ifBlank { "main" }}") ?: return@withContext null
            runCatching { JSONObject(raw).optString("sha").takeIf { it.isNotBlank() } }.getOrNull()
        }

    /** آخر تشغيلات سير العمل (يُفكّك عبر parseRuns النقية). */
    suspend fun fetchRuns(owner: String, repo: String, token: String, perPage: Int = 20): List<BuildRunSnapshot> =
        withContext(Dispatchers.IO) {
            val raw = authGet(owner, repo, token, "/actions/runs?per_page=$perPage") ?: return@withContext emptyList()
            parseRuns(raw)
        }

    /** إطلاق سير البناء عبر workflow_dispatch. يُعيد true عند القبول (204). */
    suspend fun triggerWorkflow(
        owner: String, repo: String, token: String, branch: String, workflowFile: String = DEFAULT_WORKFLOW
    ): Boolean = withContext(Dispatchers.IO) {
        if (owner.isBlank() || repo.isBlank() || token.isBlank()) return@withContext false
        try {
            val body = JSONObject().put("ref", branch.ifBlank { "main" }).toString()
                .toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowFile/dispatches")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Qabas-Studio")
                .header("Authorization", "Bearer ${token.trim()}")
                .post(body)
                .build()
            client.newCall(req).execute().use { it.code == 204 }
        } catch (_: Exception) {
            false
        }
    }

    /** سجلات الوظيفة الفاشلة (منقّحة عبر BuildVerificationEngine.sanitizeForPrompt). */
    suspend fun fetchFailedLogs(owner: String, repo: String, token: String, runId: Long, maxChars: Int = 3000): String =
        withContext(Dispatchers.IO) {
            try {
                val jobsRaw = authGet(owner, repo, token, "/actions/runs/$runId/jobs") ?: return@withContext ""
                val jobs = JSONObject(jobsRaw).optJSONArray("jobs") ?: return@withContext ""
                var failedJobId = -1L
                for (i in 0 until jobs.length()) {
                    val j = jobs.optJSONObject(i) ?: continue
                    if (j.optString("conclusion") == "failure") { failedJobId = j.optLong("id"); break }
                }
                if (failedJobId <= 0) return@withContext ""
                val req = Request.Builder()
                    .url("https://api.github.com/repos/$owner/$repo/actions/jobs/$failedJobId/logs")
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Qabas-Studio")
                    .header("Authorization", "Bearer ${token.trim()}")
                    .build()
                val log = client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string().orEmpty() else "" }
                BuildVerificationEngine.sanitizeForPrompt(log, maxChars)
            } catch (_: Exception) {
                ""
            }
        }

    /**
     * 🔁 الحلقة الكاملة: أطلق البناء → راقب حتى الاكتمال → أعد النتيجة مع السجل عند الفشل.
     * @param onTick يُستدعى عند كل استطلاع مع آخر لقطة تشغيل (لتحديث الواجهة حياً).
     */
    suspend fun triggerAndAwait(
        owner: String,
        repo: String,
        token: String,
        branch: String,
        workflowFile: String = DEFAULT_WORKFLOW,
        pollIntervalMs: Long = 15_000L,
        timeoutMs: Long = 480_000L,
        onTick: suspend (BuildRunSnapshot?) -> Unit = {}
    ): BuildVerification = withContext(Dispatchers.IO) {
        val b = branch.ifBlank { "main" }
        if (owner.isBlank() || repo.isBlank() || token.isBlank()) {
            return@withContext BuildVerification(
                BuildRunOutcome.UNKNOWN, null, "", "لا يوجد مستودع مربوط أو رمز صالح لإطلاق البناء."
            )
        }

        val shaBefore = fetchHeadCommitSha(owner, repo, token, b).orEmpty()

        val triggered = triggerWorkflow(owner, repo, token, b, workflowFile)
        onTick(null)
        if (!triggered) {
            Log.w(TAG, "workflow_dispatch rejected (missing workflow or actions:write scope)")
        }

        val deadline = System.currentTimeMillis() + timeoutMs
        var lastRun: BuildRunSnapshot? = null

        while (System.currentTimeMillis() < deadline) {
            delay(pollIntervalMs)
            val runs = fetchRuns(owner, repo, token)
            val run = pickRunForCommit(runs, shaBefore) ?: runs.firstOrNull()
            if (run != null) {
                lastRun = run
                onTick(run)
                if (isFinished(run.outcome)) {
                    val logs = if (run.outcome == BuildRunOutcome.FAILURE) {
                        fetchFailedLogs(owner, repo, token, run.id)
                    } else ""
                    return@withContext BuildVerification(
                        outcome = run.outcome,
                        run = run,
                        commitSha = run.headSha.ifBlank { shaBefore },
                        message = describe(run),
                        failedLogs = logs
                    )
                }
            } else {
                onTick(null)
            }
        }

        BuildVerification(
            outcome = lastRun?.outcome ?: BuildRunOutcome.UNKNOWN,
            run = lastRun,
            commitSha = shaBefore,
            message = if (lastRun != null) "${describe(lastRun)} — لم ينتهِ خلال المهلة المحددة."
            else "لم يبدأ أي تشغيل خلال المهلة — تحقق من وجود ملف $workflowFile وصلاحية actions:write.",
            timedOut = true
        )
    }
}
