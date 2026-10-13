package com.qabas.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * مزامنة مستودع الوكيل الخاص بكل مستخدم عبر Supabase (user_settings.preferences_json → dev_repo).
 *
 * القواعد الصارمة:
 * - التوكن لا يغادر الجهاز أبداً (يُجرَّد قبل الرفع، ويُحافَظ على المحلي عند التطبيق).
 * - الكتابة دمج read-modify-write حتى لا تمسح بقية تفضيلات المستخدم.
 * - بلا حساب مسجل (device id) → لا سحابة، محلي فقط.
 * - لا يوجد جدول/استعلام جديد — إعادة استخدام البنية القائمة.
 */
object DevRepoSync {
    private const val TAG = "DevRepoSync"
    private const val KEY = "dev_repo"
    const val OFFICIAL_OWNER = "Ali-awwww"
    const val OFFICIAL_REPO = "qabasapp"

    data class DevRepoConfig(
        val owner: String,
        val repo: String,
        val activeRepoId: String?,
        /** JSONArray نصية لمدخلات الخزانة — التوكنات مُجرَّدة دائماً قبل الحفظ */
        val vaultJson: String
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("owner", owner)
            .put("repo", repo)
            .put("active_repo_id", activeRepoId ?: "")
            .put("vault_json", vaultJson)

        fun isBlank() = owner.isBlank() || repo.isBlank()
        fun isOfficialDefault() = owner == OFFICIAL_OWNER && repo == OFFICIAL_REPO

        companion object {
            fun fromJson(o: JSONObject) = DevRepoConfig(
                owner = o.optString("owner", ""),
                repo = o.optString("repo", ""),
                activeRepoId = o.optString("active_repo_id", "").ifBlank { null },
                vaultJson = o.optString("vault_json", "[]")
            )
        }
    }

    /** جلب إعدادات المستودع السحابية للمستخدم الحالي — null تعني: لا بيانات (حالة فارغة). */
    suspend fun fetchDevRepo(context: Context): DevRepoConfig? = withContext(Dispatchers.IO) {
        runCatching {
            if (!SupabaseServices.isSupabaseAvailable) return@runCatching null
            val userId = UserPreferencesManager.getEffectiveUserId(context)
            if (userId.startsWith("dev_")) return@runCatching null
            val row = SupabaseServices.UserSettings.getUserSettings(userId) ?: return@runCatching null
            if (row.preferencesJson.isBlank()) return@runCatching null
            val root = JSONObject(row.preferencesJson)
            if (!root.has(KEY)) return@runCatching null
            DevRepoConfig.fromJson(root.getJSONObject(KEY)).takeIf { !it.isBlank() }
        }.getOrNull()
    }

    /** رفع إعدادات المستودع (بدون توكن) مع الحفاظ على بقية التفضيلات. */
    suspend fun pushDevRepo(context: Context, config: DevRepoConfig): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (!SupabaseServices.isSupabaseAvailable) return@runCatching false
            if (config.isBlank()) return@runCatching false
            val userId = UserPreferencesManager.getEffectiveUserId(context)
            if (userId.startsWith("dev_")) return@runCatching false
            val existing = SupabaseServices.UserSettings.getUserSettings(userId)?.preferencesJson.orEmpty()
            val root = if (existing.isBlank()) JSONObject() else JSONObject(existing)
            root.put(KEY, config.toJson())
            SupabaseServices.UserSettings.saveUserSettings(userId, root.toString())
        }.getOrDefault(false)
    }

    /** لقطة محلية جاهزة للرفع — التوكنات مُجرَّدة. null تعني: لا شيء يُزامَن. */
    fun snapshotLocal(context: Context): DevRepoConfig? {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val owner = prefs.getString("build_center_owner", "").orEmpty().trim()
        val repo = prefs.getString("build_center_repo", "").orEmpty().trim()
        if (owner.isBlank() || repo.isBlank()) return null
        val vaultArr = JSONArray()
        RepoVaultManager.getRepositories(context).forEach {
            vaultArr.put(it.copy(token = "").toJson())
        }
        return DevRepoConfig(owner, repo, RepoVaultManager.getActiveRepoId(context), vaultArr.toString())
    }

    /** تطبيق إعدادات السحابة محلياً مع الحفاظ على التوكنات المحلية لكل مدخل. */
    fun applyLocally(context: Context, config: DevRepoConfig) {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("build_center_owner", config.owner)
            .putString("build_center_repo", config.repo)
            .apply()
        runCatching {
            val arr = JSONArray(config.vaultJson)
            val localById = RepoVaultManager.getRepositories(context).associateBy { it.id }
            val list = (0 until arr.length()).map {
                val e = RepoVaultEntry.fromJson(arr.getJSONObject(it))
                e.copy(token = localById[e.id]?.token.orEmpty())
            }
            if (list.isNotEmpty()) {
                RepoVaultManager.saveRepositories(context, list)
                val activeId = config.activeRepoId?.takeIf { id -> list.any { it.id == id } }
                    ?: list.first().id
                RepoVaultManager.setActiveRepo(context, activeId)
            }
        }.onFailure { Log.w(TAG, "applyLocally vault failed: ${it.message}") }
    }

    /** مسح بقايا الافتراضي الموروث (مستودع المالك) لغير المطور — بداية نظيفة. */
    fun clearInheritedDefault(context: Context) {
        RepoVaultManager.clearAll(context)
    }
}
