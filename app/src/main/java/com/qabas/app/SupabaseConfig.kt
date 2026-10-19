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
                    autoLoadFromStorage = false
                    enableLifecycleCallbacks = false
                    alwaysAutoRefresh = false
                    sessionManager = io.github.jan.supabase.auth.MemorySessionManager()
                }
                install(Storage)
                install(Functions)
            }
        }

        return createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = key
        ) {
            install(Postgrest) {
                defaultSchema = "public"
            }
            install(Auth) {
                autoLoadFromStorage = false
                enableLifecycleCallbacks = false
                alwaysAutoRefresh = false
                sessionManager = io.github.jan.supabase.auth.MemorySessionManager()
            }
            install(Storage)
            install(Functions)
        }
    }

    /** Current auth session, if any. */
    fun currentSession(): UserSession? = runCatching {
        if (!isConfigured) null else client.auth.currentSessionOrNull()
    }.getOrNull()
}
