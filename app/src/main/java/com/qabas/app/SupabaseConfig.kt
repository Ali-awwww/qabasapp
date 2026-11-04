@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.qabas.app

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.realtime.Realtime
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single Supabase client configured once per process.
 *
 * Credentials come from BuildConfig (populated by the secrets gradle plugin
 * via local.properties / .env). When keys are missing the client is replaced
 * with a no-op sentinel — every caller must still check
 * [SupabaseServices.isSupabaseAvailable] before using the API so the app
 * degrades gracefully on devices where Supabase is not yet wired up.
 */
object SupabaseConfig {

    private const val TAG = "SupabaseConfig"

    /** Production fixed credentials */
    const val DEFAULT_SUPABASE_URL = "https://egkgygbvoeevakwkbvfu.supabase.co"
    const val DEFAULT_SUPABASE_KEY = "sb_publishable_5iEyi_WtX-OvpXkygh0OUQ_Rf7mAZvQ"
    const val PASSWORD_RESET_SCHEME = "qabas"
    const val PASSWORD_RESET_HOST = "auth"
    const val PASSWORD_RESET_REDIRECT_URL = "qabas://auth"

    @Volatile
    private var _client: SupabaseClient? = null

    /**
     * True when a valid Supabase URL + key are present.
     */
    val isConfigured: Boolean
        get() = isRealUrl(url) && isRealKey(key)

    private fun isRealUrl(u: String): Boolean =
        u.isNotBlank() &&
            u.startsWith("https://") &&
            u.contains("supabase.co") &&
            !containsPlaceholder(u)

    private fun isRealKey(k: String): Boolean =
        k.isNotBlank() && !containsPlaceholder(k)

    private fun containsPlaceholder(v: String): Boolean {
        val l = v.lowercase()
        return l.contains("your_") ||
            l.contains("example") ||
            l.contains("placeholder") ||
            l.contains("replace") ||
            l.contains("invalid")
    }

    /** Reads SUPABASE_URL: from SharedPreferences, BuildConfig, or default fixed URL. */
    val url: String
        get() {
            val fromPrefs = runCatching {
                AgentPrefs.getString(AppServices.appContext, "supabase_url")
            }.getOrDefault("")
            if (isRealUrl(fromPrefs)) return fromPrefs.trim()
            val fromBuild = runCatching { BuildConfig.SUPABASE_URL }.getOrNull()?.takeIf { !it.isNullOrBlank() }?.trim()
            if (fromBuild != null && isRealUrl(fromBuild)) return fromBuild
            return DEFAULT_SUPABASE_URL
        }

    /** Reads SUPABASE_ANON_KEY: from SharedPreferences, BuildConfig, or default fixed Key. */
    val key: String
        get() {
            val fromPrefs = runCatching {
                AgentPrefs.getString(AppServices.appContext, "supabase_anon_key")
            }.getOrDefault("")
            if (isRealKey(fromPrefs)) return fromPrefs.trim()
            val fromBuild = runCatching { BuildConfig.SUPABASE_ANON_KEY }.getOrNull()?.takeIf { !it.isNullOrBlank() }?.trim()
            if (fromBuild != null && isRealKey(fromBuild)) return fromBuild
            return DEFAULT_SUPABASE_KEY
        }

    /** Lazily created on first access. Safe to call from any thread. */
    val client: SupabaseClient
        get() = _client ?: synchronized(this) {
            _client ?: build().also { _client = it }
        }

    fun resetClient() {
        synchronized(this) {
            _client = null
        }
    }

    private fun build(): SupabaseClient {
        if (!isConfigured) {
            // Build a placeholder client that will fail loudly on every call.
            // This keeps the public surface stable when keys are missing.
            android.util.Log.w(
                TAG,
                "Supabase not configured. Set SUPABASE_URL and SUPABASE_ANON_KEY in .env. " +
                    "Cloud features that depend on Supabase will be skipped at runtime."
            )
            return createSupabaseClient(
                supabaseUrl = "https://invalid.supabase.co",
                supabaseKey = "invalid"
            ) {
                install(Postgrest)
                install(Auth) {
                    scheme = PASSWORD_RESET_SCHEME
                    host = PASSWORD_RESET_HOST
                    flowType = io.github.jan.supabase.auth.FlowType.PKCE
                    autoLoadFromStorage = false
                    enableLifecycleCallbacks = false
                    alwaysAutoRefresh = false
                    sessionManager = io.github.jan.supabase.auth.MemorySessionManager()
                }
                install(Storage)
                install(Functions)
                install(Realtime)
            }
        }

        return createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = key
        ) {
            defaultSerializer = io.github.jan.supabase.serializer.KotlinXSerializer(
                kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                    encodeDefaults = true
                }
            )
            install(Postgrest) {
                defaultSchema = "public"
            }
            install(Auth) {
                scheme = PASSWORD_RESET_SCHEME
                host = PASSWORD_RESET_HOST
                flowType = io.github.jan.supabase.auth.FlowType.PKCE
                // Let supabase-kt restore the platform session and refresh tokens automatically.
                // MemorySessionManager loses the refresh token when the process is recreated.
                autoLoadFromStorage = true
                autoSaveToStorage = true
                enableLifecycleCallbacks = true
                alwaysAutoRefresh = true
                sessionManager = SupabaseEncryptedSessionManager(AppServices.appContext)
            }
            install(Storage)
            install(Functions)
            install(Realtime)
        }
    }

    /** Current auth session, if any. */
    fun currentSession(): UserSession? = runCatching {
        if (!isConfigured) null else client.auth.currentSessionOrNull()
    }.getOrNull()
}
}

/**
 * Tracks a password-recovery session only after Supabase has exchanged the
 * callback code and imported a valid session. A pending callback suppresses
 * the normal login-to-home navigation while the PKCE exchange is running.
 */
object PasswordRecoveryManager {
    private const val PREFS_NAME = "qabas_prefs"
    private const val PREF_VERIFIED = "supabase_password_recovery_verified"

    private val _isRecoveryInProgress = MutableStateFlow(false)
    val isRecoveryInProgress: StateFlow<Boolean> = _isRecoveryInProgress.asStateFlow()

    private val _isRecoveryVerified = MutableStateFlow(false)
    val isRecoveryVerified: StateFlow<Boolean> = _isRecoveryVerified.asStateFlow()

    fun restore(context: Context) {
        val verified = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_VERIFIED, false)
        _isRecoveryInProgress.value = verified
        _isRecoveryVerified.value = verified
    }

    fun begin() {
        _isRecoveryInProgress.value = true
        _isRecoveryVerified.value = false
    }

    fun markVerified(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_VERIFIED, true)
            .apply()
        _isRecoveryInProgress.value = true
        _isRecoveryVerified.value = true
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_VERIFIED)
            .apply()
        _isRecoveryInProgress.value = false
        _isRecoveryVerified.value = false
    }
}
