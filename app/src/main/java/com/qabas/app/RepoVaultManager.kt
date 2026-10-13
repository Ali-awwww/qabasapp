package com.qabas.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * نموذج يمثل مستودعاً محفوظاً في خزانة المستودعات المتعددة
 */
data class RepoVaultEntry(
    val id: String = UUID.randomUUID().toString(),
    val owner: String,
    val repo: String,
    val token: String = "",
    val customName: String = "",
    val isPrivate: Boolean = false,
    val defaultBranch: String = "main",
    val description: String = "",
    val lastUsed: Long = System.currentTimeMillis()
) {
    val fullName: String get() = "$owner/$repo"
    val displayName: String get() = customName.ifBlank { repo }
    val githubUrl: String get() = "https://github.com/$owner/$repo"

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("owner", owner)
            put("repo", repo)
            put("token", token)
            put("customName", customName)
            put("isPrivate", isPrivate)
            put("defaultBranch", defaultBranch)
            put("description", description)
            put("lastUsed", lastUsed)
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): RepoVaultEntry {
            return RepoVaultEntry(
                id = obj.optString("id", UUID.randomUUID().toString()),
                owner = obj.optString("owner", ""),
                repo = obj.optString("repo", ""),
                token = obj.optString("token", ""),
                customName = obj.optString("customName", ""),
                isPrivate = obj.optBoolean("isPrivate", false),
                defaultBranch = obj.optString("defaultBranch", "main"),
                description = obj.optString("description", ""),
                lastUsed = obj.optLong("lastUsed", System.currentTimeMillis())
            )
        }
    }
}

/**
 * مدير خزانة المستودعات المتعددة - يحفظ ويدير ويبدل بين مشاريع GitHub لمركز البناء وغرفة الوكيل
 */
object RepoVaultManager {
    private const val PREFS_KEY = "multi_repo_vault_json_v2"
    private const val ACTIVE_REPO_ID_KEY = "active_repo_vault_id"

    private val _vaultUpdateTrigger = kotlinx.coroutines.flow.MutableStateFlow(System.currentTimeMillis())
    val vaultUpdateTrigger: kotlinx.coroutines.flow.StateFlow<Long> = _vaultUpdateTrigger

    fun notifyVaultChanged() {
        _vaultUpdateTrigger.value = System.currentTimeMillis()
    }

    fun getRepositories(context: Context): List<RepoVaultEntry> {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString(PREFS_KEY, null)
        val list = mutableListOf<RepoVaultEntry>()

        if (!raw.isNullOrBlank()) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    list.add(RepoVaultEntry.fromJson(arr.getJSONObject(i)))
                }
            } catch (_: Exception) {}
        }

        // إذا كانت الخزانة فارغة، أضف المستودع الرسمي المعتمد
        if (list.isEmpty()) {
            val curOwner = prefs.getString("build_center_owner", "Ali-awwww")?.ifBlank { "Ali-awwww" } ?: "Ali-awwww"
            val curRepo = prefs.getString("build_center_repo", "qabasapp")?.ifBlank { "qabasapp" } ?: "qabasapp"
            val curToken = prefs.getString("build_center_token", "") ?: ""
            val defaultEntry = RepoVaultEntry(
                id = "repo_ali_awwww_qabasapp",
                owner = curOwner,
                repo = curRepo,
                token = curToken,
                customName = "مستودع قبس الرسمي (Ali-awwww/qabasapp)",
                description = "مستودع GitHub المباشر لتحديثات xdelta وبناء الـ APK"
            )
            list.add(defaultEntry)
            saveRepositories(context, list)
            setActiveRepo(context, defaultEntry.id)
        }

        return list
    }

    fun saveRepositories(context: Context, repos: List<RepoVaultEntry>) {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val arr = JSONArray()
        repos.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(PREFS_KEY, arr.toString()).apply()
        notifyVaultChanged()
    }

    fun getActiveRepoId(context: Context): String? {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        return prefs.getString(ACTIVE_REPO_ID_KEY, null)
    }

    fun getActiveRepo(context: Context): RepoVaultEntry? {
        val repos = getRepositories(context)
        val activeId = getActiveRepoId(context)
        return repos.find { it.id == activeId } ?: repos.firstOrNull()
    }

    fun setActiveRepo(context: Context, id: String): RepoVaultEntry? {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val repos = getRepositories(context)
        val target = repos.find { it.id == id } ?: return null

        val updated = repos.map {
            if (it.id == id) it.copy(lastUsed = System.currentTimeMillis()) else it
        }
        saveRepositories(context, updated)

        prefs.edit()
            .putString(ACTIVE_REPO_ID_KEY, id)
            .putString("build_center_owner", target.owner)
            .putString("build_center_repo", target.repo)
            .putString("build_center_token", target.token)
            .apply()

        return target
    }

    fun clearAll(context: Context) {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .remove(PREFS_KEY)
            .remove(ACTIVE_REPO_ID_KEY)
            .remove("build_center_owner")
            .remove("build_center_repo")
            .remove("build_center_token")
            .apply()
        notifyVaultChanged()
    }

    fun addOrUpdateRepo(context: Context, entry: RepoVaultEntry, setAsActive: Boolean = true): List<RepoVaultEntry> {
        val current = getRepositories(context).toMutableList()
        val index = current.indexOfFirst { it.fullName.equals(entry.fullName, ignoreCase = true) || it.id == entry.id }
        if (index >= 0) {
            current[index] = entry
        } else {
            current.add(0, entry)
        }
        saveRepositories(context, current)
        if (setAsActive) {
            setActiveRepo(context, entry.id)
        }
        return current
    }

    fun deleteRepo(context: Context, id: String): List<RepoVaultEntry> {
        val current = getRepositories(context).filter { it.id != id }
        saveRepositories(context, current)
        if (getActiveRepoId(context) == id) {
            current.firstOrNull()?.let { setActiveRepo(context, it.id) }
        }
        return current
    }

    /**
     * يحلل أي رابط مستودع GitHub أو نص مدخل ويستخرج المالك واسم المستودع بدقة.
     * يدعم:
     * - https://github.com/owner/repo
     * - github.com/owner/repo
     * - git@github.com:owner/repo.git
     * - owner/repo
     */
    fun parseRepoUrl(input: String): Pair<String, String>? {
        val clean = input.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("git@github.com:")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')

        val parts = clean.split("/").filter { it.isNotBlank() }
        if (parts.size >= 2) {
            val owner = parts[0]
            val repo = parts[1]
            return Pair(owner, repo)
        }
        return null
    }

    /**
     * رابط التجهيز المسبق لإنشاء رمز GitHub PAT بالصلاحيات الدقيقة المطلوبة للوكيل والبناء.
     */
    const val GITHUB_TOKEN_GENERATION_URL =
        "https://github.com/settings/tokens/new?description=Qabas+Studio+DevOps+Agent&scopes=repo,workflow,read:user"
}
