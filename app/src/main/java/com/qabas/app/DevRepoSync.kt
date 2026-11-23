package com.qabas.app

import android.content.Context
import android.util.Log
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

        companion object {
            fun fromJson(o: JSONObject) = DevRepoConfig(
                owner = o.optString("owner", ""),
                repo = o.optString("repo", ""),
                activeRepoId = o.optString("active_repo_id", "").ifBlank { null },
                vaultJson = o.optString("vault_json", "[]")
            )
        }
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

}
