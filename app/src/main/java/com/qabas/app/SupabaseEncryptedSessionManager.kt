package com.qabas.app

import android.content.Context
import android.util.Base64
import android.util.Log
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Persists Supabase sessions as AES-256-GCM ciphertext.
 * The non-exportable AES key is generated and held by Android Keystore.
 * Only the encrypted payload is stored in the app's private preferences.
 */
class SupabaseEncryptedSessionManager(context: Context) : SessionManager {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    override suspend fun saveSession(session: UserSession) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(json.encodeToString(session).toByteArray(Charsets.UTF_8))
        val payload = ByteBuffer.allocate(cipher.iv.size + ciphertext.size)
            .put(cipher.iv)
            .put(ciphertext)
            .array()
        check(preferences.edit()
            .putString(SESSION_KEY, Base64.encodeToString(payload, Base64.NO_WRAP))
            .commit()) {
            "Unable to persist the Supabase authentication session"
        }
    }

    override suspend fun loadSession(): UserSession {
        val encoded = preferences.getString(SESSION_KEY, null)
            ?: throw NoSessionFoundException()
        return try {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            require(payload.size > IV_LENGTH + GCM_TAG_LENGTH_BYTES) { "Invalid encrypted session payload" }
            val buffer = ByteBuffer.wrap(payload)
            val iv = ByteArray(IV_LENGTH).also { buffer.get(it) }
            val ciphertext = ByteArray(buffer.remaining()).also { buffer.get(it) }

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            val plaintext = cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
            json.decodeFromString<UserSession>(plaintext)
        } catch (error: Exception) {
            // A missing/invalidated key or corrupt payload must never be treated
            // as a valid login. Clear the stale blob and require fresh sign-in.
            preferences.edit().remove(SESSION_KEY).commit()
            Log.w(TAG, "Discarded an unreadable persisted Supabase session", error)
            throw NoSessionFoundException()
        }
    }

    override suspend fun deleteSession() {
        check(preferences.edit().remove(SESSION_KEY).commit()) {
            "Unable to clear the Supabase authentication session"
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val TAG = "SupabaseSessionStore"
        const val PREFS_FILE = "qabas_supabase_auth_secure"
        const val SESSION_KEY = "supabase_auth_session_v1"
        const val KEY_ALIAS = "qabas_supabase_auth_aes_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val GCM_TAG_LENGTH_BITS = 128
        const val GCM_TAG_LENGTH_BYTES = GCM_TAG_LENGTH_BITS / 8
    }
}
