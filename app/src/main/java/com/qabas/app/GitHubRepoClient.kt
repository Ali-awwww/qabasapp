package com.qabas.app

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class RepoFile(
    val name: String,
    val path: String,
    val type: String,
    val sha: String,
    val size: Long = 0,
    val html_url: String = "",
    val download_url: String = "",
    val branch: String = "main",
) {
    val isDirectory: Boolean get() = type == "tree"
    val isFile: Boolean get() = type == "blob"
}

data class RepoCommitInfo(
    val sha: String,
    val message: String,
    val authorName: String,
    val date: String,
)

data class RepoBranchInfo(
    val name: String,
    val sha: String,
)

data class RepoContentResponse(
    val name: String,
    val path: String,
    val sha: String,
    val content: String,
    val encoding: String,
)

data class RepoForkResult(
    val fullName: String,
    val owner: String,
    val name: String,
    val htmlUrl: String,
    val cloneUrl: String,
    val defaultBranch: String = "main"
)

class GitHubRepoClient(
    private val owner: String,
    private val repo: String,
    private val token: String,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun authHeader(): String = if (token.isNotBlank()) "Bearer $token" else ""

    private suspend fun <T> get(path: String, deserialize: (String) -> T): T? = withContext(Dispatchers.IO) {
        try {
            val builder = Request.Builder()
                .url("https://api.github.com$path")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Qabas-Studio")
            if (authHeader().isNotBlank()) builder.header("Authorization", authHeader())
            val response = client.newCall(builder.build()).execute()
            if (!response.isSuccessful) return@withContext null
            deserialize(response.body?.string() ?: return@withContext null)
        } catch (e: Exception) { null }
    }

    private suspend fun post(path: String, body: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://api.github.com$path")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Qabas-Studio")
                .header("Authorization", authHeader())
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { it.code == 201 || it.code == 200 }
        } catch (e: Exception) { false }
    }

    private suspend fun put(path: String, body: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://api.github.com$path")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Qabas-Studio")
                .header("Authorization", authHeader())
                .put(body.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { it.code == 200 || it.code == 201 }
        } catch (e: Exception) { false }
    }

    private suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://api.github.com$path")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Qabas-Studio")
                .header("Authorization", authHeader())
                .delete()
                .build()
            client.newCall(req).execute().use { it.code == 200 || it.code == 204 }
        } catch (e: Exception) { false }
    }

    private fun encodePath(p: String): String = URLEncoder.encode(p.trim(), "UTF-8").replace("+", "%20")

    suspend fun getTree(branch: String = "main", path: String = "", recursive: Boolean = true): List<RepoFile>? {
        val p = if (path.isBlank()) "" else "/$path"
        val url = "/repos/$owner/$repo/git/trees/$branch?recursive=${if (recursive) 1 else 0}$p"
        return get(url) { raw ->
            val obj = JSONObject(raw)
            val treeArr = obj.optJSONArray("tree") ?: return@get emptyList()
            val result = mutableListOf<RepoFile>()
            for (i in 0 until treeArr.length()) {
                val e = treeArr.getJSONObject(i)
                result.add(
                    RepoFile(
                        name = e.optString("name"),
                        path = e.optString("path"),
                        type = e.optString("type"),
                        sha = e.optString("sha"),
                        size = e.optLong("size", 0),
                        html_url = e.optString("html_url"),
                        download_url = e.optString("download_url", ""),
                        branch = branch,
                    )
                )
            }
            result
        }
    }

    suspend fun getCommits(branch: String = "main", perPage: Int = 30): List<RepoCommitInfo>? {
        val path = "/repos/$owner/$repo/commits?sha=${encodePath(branch)}&per_page=$perPage"
        return get(path) { raw ->
            val arr = JSONArray(raw)
            val result = mutableListOf<RepoCommitInfo>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val sha = o.optString("sha", "")
                val c = o.optJSONObject("commit") ?: continue
                val a = c.optJSONObject("author")
                result.add(
                    RepoCommitInfo(
                        sha = sha,
                        message = c.optString("message", "").takeLines(1),
                        authorName = a?.optString("name") ?: "Unknown",
                        date = a?.optString("date")?.take(10) ?: "",
                    )
                )
            }
            result
        }
    }

    suspend fun getBranches(): List<RepoBranchInfo>? {
        val path = "/repos/$owner/$repo/branches?per_page=50"
        return get(path) { raw ->
            val arr = JSONArray(raw)
            val result = mutableListOf<RepoBranchInfo>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val c = o.optJSONObject("commit")
                result.add(
                    RepoBranchInfo(
                        name = o.optString("name"),
                        sha = c?.optString("sha") ?: "",
                    )
                )
            }
            result
        }
    }

    suspend fun createBranch(branchName: String, fromSha: String): Boolean {
        val body = JSONObject()
            .put("ref", "refs/heads/$branchName")
            .put("sha", fromSha)
            .toString()
        return post("/repos/$owner/$repo/git/refs", body)
    }

    /** فرع السحب الأساسي (قد يكون main أو master حسب حساب المستخدم). */
    suspend fun createPullRequest(head: String, title: String, bodyText: String, base: String = "main"): Int? =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject()
                    .put("title", title)
                    .put("head", head)
                    .put("base", base)
                    .put("body", bodyText)
                    .toString()
                    .toRequestBody("application/json".toMediaType())
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                val req = Request.Builder()
                    .url("https://api.github.com/repos/$owner/$repo/pulls")
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "Qabas-Studio")
                    .header("Authorization", "Bearer $token")
                    .post(body)
                    .build()
                client.newCall(req).execute().use {
                    if (it.code != 201) return@withContext null
                    JSONObject(it.body?.string().orEmpty()).optInt("number", -1)
                        .takeIf { n -> n > 0 }
                }
            } catch (_: Exception) {
                null
            }
        }

    /** يدمج سحباً برقمه في main — يُستخدم بعد مراجعة الفرق. */
    suspend fun mergePullRequest(number: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("merge_method", "squash").toString()
                .toRequestBody("application/json".toMediaType())
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/pulls/$number/merge")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Qabas-Studio")
                .header("Authorization", "Bearer $token")
                .put(body)
                .build()
            client.newCall(req).execute().use { it.code == 200 }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getFileContent(path: String, branch: String = "main"): RepoContentResponse? {
        val url = "/repos/$owner/$repo/contents/${encodePath(path)}?ref=$branch"
        return get(url) { raw ->
            val obj = JSONObject(raw)
            RepoContentResponse(
                name = obj.optString("name"),
                path = obj.optString("path"),
                sha = obj.optString("sha"),
                content = obj.optString("content", ""),
                encoding = obj.optString("encoding", "base64"),
            )
        }
    }

    suspend fun createFile(path: String, branch: String, message: String, content: String, sha: String? = null): Boolean {
        val obj = JSONObject()
            .put("message", message)
            .put("content", Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            .put("branch", branch)
        if (sha != null) obj.put("sha", sha)
        return put("/repos/$owner/$repo/contents/${encodePath(path)}", obj.toString())
    }

    suspend fun updateFile(path: String, branch: String, message: String, content: String): Boolean {
        val existing = getFileContent(path, branch)
        return createFile(path, branch, message, content, existing?.sha)
    }

    suspend fun deleteFile(path: String, branch: String, message: String, sha: String): Boolean {
        val enc = URLEncoder.encode(message, "UTF-8")
        val url = "/repos/$owner/$repo/contents/${encodePath(path)}?message=$enc&sha=${URLEncoder.encode(sha, "UTF-8")}&branch=${URLEncoder.encode(branch, "UTF-8")}"
        return delete(url)
    }
    suspend fun getBranchSha(branch: String = "main"): String? {
        val path = "/repos/$owner/$repo/git/refs/heads/${encodePath(branch)}"
        return get(path) { raw ->
            val obj = JSONObject(raw)
            val o = obj.optJSONObject("object")
            o?.optString("sha")
        }
    }

    /** حماية الفرع الأساسي: الدمج عبر السحب فقط + مراجعة واحدة. يحتاج صلاحية إدارة. */
    suspend fun protectBranch(branch: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("required_status_checks", JSONObject.NULL)
                .put("enforce_admins", false)
                .put("required_pull_request_reviews", JSONObject().put("required_approving_review_count", 1))
                .put("restrictions", JSONObject.NULL)
                .toString()
                .toRequestBody("application/json".toMediaType())
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/branches/${URLEncoder.encode(branch, "UTF-8")}/protection")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Qabas-Studio")
                .header("Authorization", "Bearer $token")
                .put(body)
                .build()
            client.newCall(req).execute().use { it.code == 200 }
        } catch (_: Exception) {
            false
        }
    }

    /** السحوبات المفتوحة/المغلقة — ليراجعها الوكيل قبل الدمج. */
    suspend fun listPullRequests(state: String = "open"): List<String> =
        get("/repos/$owner/$repo/pulls?state=$state&per_page=20") { raw ->
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                "#${o.optInt("number")} ${o.optString("title")} [${o.optJSONObject("head")?.optString("ref")}]"
            }
        } ?: emptyList()

    /** ملفات سحب معين — ليراجع الوكيل الفرق قبل الدمج. */
    suspend fun getPullFiles(number: Int): List<String> =
        get("/repos/$owner/$repo/pulls/$number/files?per_page=30") { raw ->
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                "${o.optString("filename")} (+${o.optInt("additions")}/-${o.optInt("deletions")})"
            }
        } ?: emptyList()

    /** فتح قضية — لتتبع المهام والعيوب من داخل الوكيل. */
    suspend fun createIssue(title: String, body: String): Boolean {
        if (title.isBlank()) return false
        val obj = org.json.JSONObject().put("title", title.take(200)).put("body", body.take(4000))
        return post("/repos/$owner/$repo/issues", obj.toString())
    }

    /** آخر القضايا المفتوحة. */
    suspend fun listIssues(): List<String> =
        get("/repos/$owner/$repo/issues?state=open&per_page=20") { raw ->
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                "#${o.optInt("number")} ${o.optString("title")}"
            }
        } ?: emptyList()

    /** اسم مستخدم صاحب الرمز (للتحقق من صلاحيته) — null عند الفشل. */
    suspend fun getAuthUser(): String? = withContext(Dispatchers.IO) {        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder()
                .url("https://api.github.com/user")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Qabas-Studio")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            client.newCall(req).execute().use {
                if (!it.isSuccessful) return@withContext null
                JSONObject(it.body?.string().orEmpty()).optString("login").takeIf { l -> l.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** الفرع الافتراضي الحقيقي للمستودع (main أو master) — لا تفترض main أبداً. */
    suspend fun getDefaultBranch(): String {
        val path = "/repos/$owner/$repo"
        return get(path) { raw ->
            JSONObject(raw).optString("default_branch", "main").takeIf { it.isNotBlank() } ?: "main"
        } ?: "main"
    }

    /**
     * 🍴 تفريع المستودع وعمل Fork إلى حساب المستخدم أو منظمة
     */
    suspend fun forkRepository(organization: String? = null): RepoForkResult? = withContext(Dispatchers.IO) {
        try {
            if (token.isBlank()) return@withContext null
            val bodyObj = JSONObject()
            if (!organization.isNullOrBlank()) {
                bodyObj.put("organization", organization.trim())
            }
            bodyObj.put("default_branch_only", true)

            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .build()

            val req = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/forks")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Qabas-Studio")
                .header("Authorization", "Bearer $token")
                .post(bodyObj.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().use { response ->
                if (response.code != 202 && response.code != 200 && response.code != 201) return@withContext null
                val raw = response.body?.string().orEmpty()
                val json = JSONObject(raw)
                val fn = json.optString("full_name")
                val parts = fn.split("/")
                RepoForkResult(
                    fullName = fn,
                    owner = parts.firstOrNull() ?: "",
                    name = json.optString("name", repo),
                    htmlUrl = json.optString("html_url", "https://github.com/$fn"),
                    cloneUrl = json.optString("clone_url", "https://github.com/$fn.git"),
                    defaultBranch = json.optString("default_branch", "main")
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 📦 نسخ كامل محتويات وملفات المستودع الحالي إلى مستودع جديد بالكامل في حساب المستخدم
     */
    suspend fun duplicateRepository(
        targetRepoName: String,
        isPrivate: Boolean = true,
        onProgress: (String) -> Unit = {}
    ): String? = withContext(Dispatchers.IO) {
        try {
            onProgress("إنشاء المستودع الهدف على GitHub...")
            val newFullName = createUserRepo(token, targetRepoName, description = "نسخة مستنسخة من $owner/$repo عبر مساعد قبس الفضائي", private = isPrivate)
                ?: return@withContext null

            val newParts = newFullName.split("/")
            val newOwner = newParts[0]
            val newRepo = newParts[1]
            val newClient = GitHubRepoClient(owner = newOwner, repo = newRepo, token = token)

            onProgress("جلب شجرة ملفات المستودع الأصلي...")
            val defaultBranch = getDefaultBranch()
            val tree = getTree(branch = defaultBranch, recursive = true) ?: return@withContext newFullName

            val filesOnly = tree.filter { it.isFile }
            val total = filesOnly.size

            filesOnly.forEachIndexed { idx, file ->
                onProgress("نسخ الملف (${idx + 1}/$total): ${file.name}")
                val contentResp = getFileContent(file.path, defaultBranch)
                if (contentResp != null) {
                    val decoded = try {
                        String(Base64.decode(contentResp.content.replace("\n", ""), Base64.DEFAULT), Charsets.UTF_8)
                    } catch (_: Exception) { "" }

                    if (decoded.isNotBlank()) {
                        newClient.createFile(
                            path = file.path,
                            branch = "main",
                            message = "Initial clone from $owner/$repo: ${file.path}",
                            content = decoded
                        )
                    }
                }
            }

            onProgress("تم اكتمال نسخ كافة الملفات بنجاح!")
            newFullName
        } catch (_: Exception) {
            null
        }
    }

    /** رابط تحميل أرشيف zipball للمستودع */
    fun getZipballUrl(branch: String = "main"): String {
        return "https://api.github.com/repos/$owner/$repo/zipball/$branch"
    }

    companion object {
        /** ينشئ مستودعاً جديداً في حساب صاحب الرمز — يُعيد الاسم الكامل أو null. */
        suspend fun createUserRepo(
            token: String,
            name: String,
            description: String = "",
            private: Boolean = true
        ): String? = withContext(Dispatchers.IO) {
            try {
                val clean = name.trim()
                if (clean.isBlank() || token.isBlank()) return@withContext null
                val body = JSONObject()
                    .put("name", clean)
                    .put("description", description)
                    .put("private", private)
                    .put("auto_init", true)
                    .toString()
                    .toRequestBody("application/json".toMediaType())
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                val req = Request.Builder()
                    .url("https://api.github.com/user/repos")
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "Qabas-Studio")
                    .header("Authorization", "Bearer $token")
                    .post(body)
                    .build()
                client.newCall(req).execute().use {
                    if (it.code != 201) return@withContext null
                    JSONObject(it.body?.string().orEmpty()).optString("full_name")
                        .takeIf { n -> n.contains("/") }
                }
            } catch (_: Exception) {
                null
            }
        }

        /** اسم مستودع صالح من عنوان طلب عربي: حروف/أرقام/شرطات فقط. */
        fun sanitizeRepoName(title: String): String {
            var s = title.trim().lowercase()
                .replace(Regex("[\\s_]+"), "-")
                .replace(Regex("[^a-z0-9\\-\\u0600-\\u06FF]"), "")
                .replace(Regex("-+"), "-")
                .trim('-')
            if (s.isBlank()) s = "client-app"
            return s.take(50)
        }

        /** جلب مستودعات المستخدم من حسابه في GitHub */
        suspend fun listUserRepos(token: String): List<UserRepoSummary> = withContext(Dispatchers.IO) {
            try {
                if (token.isBlank()) return@withContext emptyList()
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                val req = Request.Builder()
                    .url("https://api.github.com/user/repos?per_page=100&sort=updated")
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "Qabas-Studio")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
                client.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    val arr = JSONArray(response.body?.string().orEmpty())
                    val list = mutableListOf<UserRepoSummary>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val fn = obj.optString("full_name")
                        val parts = fn.split("/")
                        list.add(
                            UserRepoSummary(
                                name = obj.optString("name"),
                                fullName = fn,
                                owner = parts.firstOrNull() ?: "",
                                description = obj.optString("description", ""),
                                isPrivate = obj.optBoolean("private", false),
                                defaultBranch = obj.optString("default_branch", "main"),
                                updatedAt = obj.optString("updated_at", ""),
                                htmlUrl = obj.optString("html_url", "")
                            )
                        )
                    }
                    list
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}

data class UserRepoSummary(
    val name: String,
    val fullName: String,
    val owner: String,
    val description: String,
    val isPrivate: Boolean,
    val defaultBranch: String,
    val updatedAt: String,
    val htmlUrl: String
)

private fun String.takeLines(n: Int): String {
    val parts = this.lines()
    return parts.take(n).joinToString("\n")
}
