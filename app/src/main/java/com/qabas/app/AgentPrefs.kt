package com.qabas.app

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * تفضيلات الوكيل الذكي المخزنة بشكل مشفر (Encrypted Agent Preferences).
 * كل مفاتيح API ورموز GitHub PAT تُخزّن مشفرة عبر EncryptedSharedPreferences.
 * تتضمن هجرة تلقائية مرة واحدة: تقرأ النص الصريح القديم، تُشفّره، ثم تمسحه.
 */
object AgentPrefs {
    private const val TAG = "AgentPrefs"
    const val MODEL = "agent_model"
    const val MAX_TURNS = "agent_max_turns"
    const val AUTO_FIX = "agent_auto_fix"
    const val PREFS_NAME = "qabas_agent_encrypted"
    private const val LEGACY_PREFS_NAME = "qabas_prefs"

    // مفاتيح API per-provider (تُخزّن مشفرة)
    val GEMINI_KEY = "gemini_key"
    val GROQ_KEY = "groq_key"
    val OPENAI_KEY = "openai_key"
    val OPENROUTER_KEY = "openrouter_key"
    val ANTHROPIC_KEY = "anthropic_key"
    val HUGGINGFACE_KEY = "huggingface_key"
    val OPENCODE_KEY = "opencode_key"

    // رموز GitHub PAT (تُخزّن مشفرة)
    val GITHUB_PAT = "build_center_token"
    val GITHUB_OWNER = "build_center_owner"
    val GITHUB_REPO = "build_center_repo"

    // حالة المهمة النشطة (تُخزّن مشفرة)
    val ACTIVE_MISSION_JSON = "active_mission_json"

    // تكوين فريق النماذج (يُخزّن مشفراً)
    val TEAM_CONFIG_JSON = "team_config_json"

    /** كل المفاتيح الحساسة التي تُنقل من النص الصريح إلى المخزن المشفر. */
    private val sensitiveKeys = listOf(
        GEMINI_KEY, GROQ_KEY, OPENAI_KEY, OPENROUTER_KEY, ANTHROPIC_KEY,
        HUGGINGFACE_KEY, OPENCODE_KEY,
        "azure_speech_key", "azure_speech_region", "elevenlabs_key",
        "pexels_key", "pixabay_key", "coverr_key", "higgsfield_api_key",
        "leonardo_api_key",
        "supabase_anon_key", "api_server_key",
        GITHUB_PAT, GITHUB_OWNER, GITHUB_REPO,
        ACTIVE_MISSION_JSON, TEAM_CONFIG_JSON
    )

    @Volatile
    private var prefs: SharedPreferences? = null

    /** تهيئة التشفير وهجرة البيانات النصية الصريحة مرة واحدة عند أول استخدام. */
    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val appContext = context.applicationContext
        prefs = try {
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                "qabas_agent_master",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
            val masterKeyAlias = MasterKeys.getOrCreate(keyGenParameterSpec)
            EncryptedSharedPreferences.create(
                PREFS_NAME,
                masterKeyAlias,
                appContext,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // بعض الأجهزة القديمة قد تفشل في إنشاء مخزن المفاتيح. لا نُسقط التطبيق:
            // نعود للمخزن القديم ونُسجّل الخطأ دون طباعة أي قيمة سرية.
            Log.e(TAG, "تعذر إنشاء المخزن المشفر، تم الرجوع للمخزن الافتراضي", e)
            appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        }
        migratePlainTextPrefs(appContext)
    }

    /** الوصول إلى كائن SharedPreferences المشفر (بعد التهيئة). آمن للاستخدام كبديل مباشر. */
    fun instance(context: Context): SharedPreferences {
        val current = prefs
        if (current != null) return current
        init(context)
        return prefs!!
    }

    /** هجرة تلقائية مرة واحدة: تقرأ كل مفتاح نص صريح من "qabas_prefs"، تُشفّره، ثم تمسح النص الصريح. */
    private fun migratePlainTextPrefs(context: Context) {
        val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val editor = instance(context).edit()
        var migratedAny = false
        for (key in sensitiveKeys) {
            val value = legacy.getString(key, null)
            if (value != null && value.isNotBlank()) {
                editor.putString(key, value)
                legacy.edit().remove(key).apply()
                migratedAny = true
            }
        }
        if (migratedAny) editor.apply()
    }

    /** تخزين قيمة نصية مشفرة. لا تُسجل القيمة مطلقاً في السجلات. */
    fun putString(context: Context, key: String, value: String) {
        instance(context).edit().putString(key, value).apply()
    }

    /** قراءة قيمة نصية مشفرة. */
    fun getString(context: Context, key: String, default: String = ""): String =
        instance(context).getString(key, default) ?: default

    /** مسح مفتاح مشفر (حذف آمن). */
    fun remove(context: Context, key: String) {
        instance(context).edit().remove(key).apply()
    }

    fun putBoolean(context: Context, key: String, value: Boolean) {
        instance(context).edit().putBoolean(key, value).apply()
    }

    fun getBoolean(context: Context, key: String, default: Boolean): Boolean =
        instance(context).getBoolean(key, default)

    fun putInt(context: Context, key: String, value: Int) {
        instance(context).edit().putInt(key, value).apply()
    }

    fun getInt(context: Context, key: String, default: Int): Int =
        instance(context).getInt(key, default)

    /** إظهار آخر 4 محارف فقط من أي سر — يُستخدم في العروض والسجلات بأمان. */
    fun maskSecret(secret: String): String {
        val s = secret.trim()
        return if (s.length <= 4) "••••" else "••••${s.takeLast(4)}"
    }

    fun toolKey(name: String) = "tool_enabled_$name"

    fun isToolEnabled(context: Context, name: String): Boolean =
        getBoolean(context, toolKey(name), true)

    fun maxTurns(context: Context): Int =
        getInt(context, MAX_TURNS, 6).coerceIn(1, 10)

    fun isAutoFix(context: Context): Boolean =
        getBoolean(context, AUTO_FIX, true)

    fun model(context: Context): String? =
        getString(context, MODEL).takeIf { it.isNotBlank() }

    fun putModel(context: Context, model: String?) {
        if (model.isNullOrBlank()) remove(context, MODEL) else putString(context, MODEL, model)
    }

    fun putMaxTurns(context: Context, turns: Int) {
        putInt(context, MAX_TURNS, turns)
    }

    fun putAutoFix(context: Context, enabled: Boolean) {
        putBoolean(context, AUTO_FIX, enabled)
    }

    /** prefered key storage helper for GitHub PAT. */
    fun putGitHubPat(context: Context, token: String) {
        putString(context, GITHUB_PAT, token)
    }

    fun getGitHubPat(context: Context): String = getString(context, GITHUB_PAT)

    fun putGitHubRepo(context: Context, owner: String, repo: String) {
        putString(context, GITHUB_OWNER, owner)
        putString(context, GITHUB_REPO, repo)
    }

    fun getGitHubOwner(context: Context): String = getString(context, GITHUB_OWNER)
    fun getGitHubRepo(context: Context): String = getString(context, GITHUB_REPO)

    fun saveTeamConfig(context: Context, json: String) {
        putString(context, TEAM_CONFIG_JSON, json)
    }

    fun loadTeamConfig(context: Context): String = getString(context, TEAM_CONFIG_JSON)

    fun saveActiveMission(context: Context, json: String) {
        putString(context, ACTIVE_MISSION_JSON, json)
    }

    fun loadActiveMission(context: Context): String = getString(context, ACTIVE_MISSION_JSON)

    fun clearActiveMission(context: Context) {
        remove(context, ACTIVE_MISSION_JSON)
    }
}
