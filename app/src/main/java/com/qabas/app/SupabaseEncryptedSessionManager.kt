package com.qabas.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Stores Supabase refresh/access tokens in Android Keystore-backed encrypted
 * preferences. The session is device-local and is never shared between users.
 */
class SupabaseEncryptedSessionManager(context: Context) : SessionManager {
    private val appContext = context.applicationContext
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Volatile
    private var cachedPreferences: SharedPreferences? = null

    private fun preferences(): SharedPreferences {
        cachedPreferences?.let { return it }
        return synchronized(this) {
            cachedPreferences ?: run {
                val masterKey = MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    appContext,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                ).also { cachedPreferences = it }
            }
        }
    }

    override suspend fun saveSession(session: UserSession) {
        val serialized = json.encodeToString(session)
        check(preferences().edit().putString(SESSION_KEY, serialized).commit()) {
            "Unable to persist the Supabase authentication session"
        }
    }

    override suspend fun loadSession(): UserSession {
        val serialized = preferences().getString(SESSION_KEY, null)
            ?: throw NoSessionFoundException()
        return try {
            json.decodeFromString<UserSession>(serialized)
        } catch (error: Exception) {
            // A corrupt or obsolete encrypted payload must not be treated as an
            // authenticated session. Clear only this app's Supabase session.
            preferences().edit().remove(SESSION_KEY).commit()
            Log.w(TAG, "Discarded an unreadable persisted Supabase session", error)
            throw NoSessionFoundException()
        }
    }

    override suspend fun deleteSession() {
        check(preferences().edit().remove(SESSION_KEY).commit()) {
            "Unable to clear the Supabase authentication session"
        }
    }

    private companion object {
        const val TAG = "SupabaseSessionStore"
        const val PREFS_FILE = "qabas_supabase_auth_secure"
        const val SESSION_KEY = "supabase_auth_session_v1"
    }
}
