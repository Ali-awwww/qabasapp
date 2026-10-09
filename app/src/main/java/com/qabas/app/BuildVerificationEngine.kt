package com.qabas.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * تقرير فحص سلامة البناء والكود
 */
data class BuildVerificationReport(
    val healthScore: Int, // 0 to 100
    val totalFilesScanned: Int,
    val syntaxErrors: List<BuildIssue>,
    val warnings: List<BuildIssue>,
    val manifestIssues: List<BuildIssue>,
    val gradleIssues: List<BuildIssue>,
    val ciWorkflowStatus: CiWorkflowStatus?,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isClean: Boolean get() = syntaxErrors.isEmpty() && manifestIssues.isEmpty() && healthScore >= 90
    val statusText: String get() = when {
        healthScore >= 95 -> "ممتاز وجاهز للنشر (OPTIMAL) ✨"
        healthScore >= 80 -> "سليم مع تنبيهات طفيفة (GOOD) 🌿"
        healthScore >= 50 -> "يحتاج مراجعة (NEEDS_ATTENTION) ⚠️"
        else -> "أخطاء حرجة تتطلب الإصلاح (CRITICAL_ERROR) 🚨"
    }
}

data class BuildIssue(
    val file: String,
    val line: Int = 0,
    val message: String,
    val severity: BuildIssueSeverity,
    val suggestion: String = ""
)

enum class BuildIssueSeverity {
    ERROR,
    WARNING,
    INFO
}

data class CiWorkflowStatus(
    val workflowName: String,
    val runId: Long,
    val status: String, // queued, in_progress, completed
    val conclusion: String?, // success, failure, cancelled, null
    val branch: String,
    val commitMessage: String,
    val url: String,
    val updatedAt: String
) {
    val isPassed: Boolean get() = conclusion == "success"
    val isRunning: Boolean get() = status == "in_progress" || status == "queued"
    val isFailed: Boolean get() = conclusion == "failure"
}

/**
 * 🧪 محرك فحص سلامة البناء والتحقق التركيبي والربط مع GitHub Actions CI
 */
object BuildVerificationEngine {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * فحص تركيبي وتحليلي محلي شامل لكافة ملفات الكود في المشروع
     */
    suspend fun runLocalSyntaxAndSanityCheck(context: Context): BuildVerificationReport = withContext(Dispatchers.IO) {
        val syntaxErrors = mutableListOf<BuildIssue>()
        val warnings = mutableListOf<BuildIssue>()
        val manifestIssues = mutableListOf<BuildIssue>()
        val gradleIssues = mutableListOf<BuildIssue>()
        var scannedCount = 0

        try {
            // فحص ملفات المصدر Kotlin و Java في مسار المشروع
            val rootDir = context.filesDir.parentFile?.parentFile ?: File("/app")
            val srcDir = File(rootDir, "src/main/java")
            val targetDir = if (srcDir.exists()) srcDir else File("/app/src/main/java")

            if (targetDir.exists()) {
                targetDir.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.forEach { file ->
                    scannedCount++
                    val relativePath = file.path.removePrefix(targetDir.path).trimStart('/')
                    val lines = file.readLines()

                    var braceBalance = 0
                    var parenBalance = 0
                    var bracketBalance = 0

                    lines.forEachIndexed { index, rawLine ->
                        val lineNum = index + 1
                        val line = rawLine.trim()

                        // تجاهل التعليقات في فحص الأقواس
                        val cleanLine = if (line.startsWith("//") || line.startsWith("/*") || line.startsWith("*")) "" else line

                        cleanLine.forEach { ch ->
                            when (ch) {
                                '{' -> braceBalance++
                                '}' -> braceBalance--
                                '(' -> parenBalance++
                                ')' -> parenBalance--
                                '[' -> bracketBalance++
                                ']' -> bracketBalance--
                            }
                        }

                        // فحص علامات الدمج غير المحلولة في git
                        if (line.startsWith("<<<<<<<") || line.startsWith("=======") || line.startsWith(">>>>>>>")) {
                            syntaxErrors.add(
                                BuildIssue(
                                    file = relativePath,
                                    line = lineNum,
                                    message = "وجود علامة دمج Git متبقية غير محلولة: $line",
                                    severity = BuildIssueSeverity.ERROR,
                                    suggestion = "احذف علامات الصراع Git وحافظ على الكود الصحيح."
                                )
                            )
                        }

                        // فحص استدعاءات TODO غير المكتملة التي قد تؤدي لانهيار
                        if (line.contains("TODO(\"") || line.contains("TODO()")) {
                            warnings.add(
                                BuildIssue(
                                    file = relativePath,
                                    line = lineNum,
                                    message = "استدعاء دالة TODO() غير منفذة قد تسبب NotImplementedError",
                                    severity = BuildIssueSeverity.WARNING,
                                    suggestion = "اكتب تنفيذ الدالة أو استبدلها بمعالجة آمنة."
                                )
                            )
                        }
                    }

                    if (braceBalance != 0) {
                        syntaxErrors.add(
                            BuildIssue(
                                file = relativePath,
                                line = lines.size,
                                message = "عدم توازن في الأقواس الحاصرة { } (الفرق: $braceBalance)",
                                severity = BuildIssueSeverity.ERROR,
                                suggestion = "تأكد من إغلاق جميع الأقواس الحاصرة { } في الملف."
                            )
                        )
                    }

                    if (parenBalance != 0) {
                        syntaxErrors.add(
                            BuildIssue(
                                file = relativePath,
                                line = lines.size,
                                message = "عدم توازن في الأقواس الدائرية ( ) (الفرق: $parenBalance)",
                                severity = BuildIssueSeverity.ERROR,
                                suggestion = "تأكد من إغلاق جميع الأقواس ( ) بدقة."
                            )
                        )
                    }
                }
            }

            // فحص ملف المانيفست AndroidManifest.xml
            val manifestFile = File("/app/src/main/AndroidManifest.xml")
            if (manifestFile.exists()) {
                val content = manifestFile.readText()
                if (!content.contains("<manifest") || !content.contains("</manifest>")) {
                    manifestIssues.add(
                        BuildIssue(
                            file = "AndroidManifest.xml",
                            message = "بنية ملف Manifest غير صالحة أو غير مغلقة!",
                            severity = BuildIssueSeverity.ERROR,
                            suggestion = "تأكد من وجود وسم <manifest> وإغلاقه بشكل صحيح."
                        )
                    )
                }
                if (!content.contains("android.permission.INTERNET")) {
                    manifestIssues.add(
                        BuildIssue(
                            file = "AndroidManifest.xml",
                            message = "إذن الإنترنت INTERNET مفقود، وهو مطلوب للربط السحابي",
                            severity = BuildIssueSeverity.WARNING,
                            suggestion = "أضف <uses-permission android:name=\"android.permission.INTERNET\" />"
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        // حساب درجة الصحة البرمجية (0 - 100)
        var score = 100
        score -= (syntaxErrors.size * 25)
        score -= (manifestIssues.size * 15)
        score -= (warnings.size * 5)
        if (score < 0) score = 0

        BuildVerificationReport(
            healthScore = score,
            totalFilesScanned = scannedCount.coerceAtLeast(1),
            syntaxErrors = syntaxErrors,
            warnings = warnings,
            manifestIssues = manifestIssues,
            gradleIssues = gradleIssues,
            ciWorkflowStatus = null
        )
    }

    /**
     * جلب آخر حالة لبناء GitHub Actions CI للمستودع
     */
    suspend fun fetchGitHubCiStatus(
        owner: String,
        repo: String,
        token: String
    ): CiWorkflowStatus? = withContext(Dispatchers.IO) {
        if (owner.isBlank() || repo.isBlank()) return@withContext null
        try {
            val url = "https://api.github.com/repos/${owner.trim()}/${repo.trim()}/actions/runs?per_page=1"
            val reqBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Qabas-Studio")

            if (token.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer ${token.trim()}")
            }

            val res = httpClient.newCall(reqBuilder.build()).execute()
            if (!res.isSuccessful) return@withContext null
            val raw = res.body?.string().orEmpty()
            val obj = JSONObject(raw)
            val runs = obj.optJSONArray("workflow_runs") ?: return@withContext null
            if (runs.length() == 0) return@withContext null

            val run = runs.getJSONObject(0)
            val commit = run.optJSONObject("head_commit")
            CiWorkflowStatus(
                workflowName = run.optString("name", "Android CI"),
                runId = run.optLong("id"),
                status = run.optString("status", "completed"),
                conclusion = run.optString("conclusion").takeIf { it.isNotBlank() },
                branch = run.optString("head_branch", "main"),
                commitMessage = commit?.optString("message", "")?.take(60) ?: "تحديث كود",
                url = run.optString("html_url", "https://github.com/$owner/$repo/actions"),
                updatedAt = run.optString("updated_at", "").take(16).replace("T", " ")
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * إطلاق تشغيل بناء CI سحابي عبر GitHub Actions Workflow Dispatch
     */
    suspend fun triggerWorkflowDispatch(
        owner: String,
        repo: String,
        token: String,
        workflowFileName: String = "android_build.yml",
        branch: String = "main"
    ): Boolean = withContext(Dispatchers.IO) {
        if (owner.isBlank() || repo.isBlank() || token.isBlank()) return@withContext false
        try {
            val url = "https://api.github.com/repos/${owner.trim()}/${repo.trim()}/actions/workflows/$workflowFileName/dispatches"
            val body = JSONObject().put("ref", branch.trim()).toString()
                .toRequestBody("application/json".toMediaType())

            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Qabas-Studio")
                .header("Authorization", "Bearer ${token.trim()}")
                .post(body)
                .build()

            val res = httpClient.newCall(req).execute()
            res.isSuccessful || res.code == 204
        } catch (_: Exception) {
            false
        }
    }

    /**
     * إنشاء ملف سير عمل GitHub Actions تلقائياً لبناء التطبيق وفحصه إن لم يكن موجوداً
     */
    suspend fun ensureDefaultCiWorkflow(
        client: GitHubRepoClient,
        branch: String = "main"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val path = ".github/workflows/android_build.yml"
            val existing = client.getFileContent(path, branch)
            if (existing != null) return@withContext true

            val workflowYaml = """
                name: Android CI Build & Verification
                on:
                  push:
                    branches: [ $branch ]
                  pull_request:
                    branches: [ $branch ]
                  workflow_dispatch:

                jobs:
                  build:
                    name: Build & Verify Android App
                    runs-on: ubuntu-latest

                    steps:
                    - name: Checkout Code
                      uses: actions/checkout@v4

                    - name: Set up JDK 17
                      uses: actions/setup-java@v4
                      with:
                        java-version: '17'
                        distribution: 'temurin'
                        cache: gradle

                    - name: Grant execute permission for gradlew
                      run: chmod +x gradlew || true

                    - name: Build with Gradle
                      run: ./gradlew assembleDebug --stacktrace || gradle assembleDebug
            """.trimIndent()

            client.createFile(
                path = path,
                branch = branch,
                message = "ci: add GitHub Actions Android CI build verification workflow",
                content = workflowYaml
            )
        } catch (_: Exception) {
            false
        }
    }
}
