package com.qabas.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64

object KeySyncService {

    private const val TAG = "KeySyncService"
    private const val PREFS_NAME = "qabas_prefs"
    private const val SYNC_TABLE = "user_api_keys"
    private const val ENCRYPTION_KEY_PREF = "key_sync_master"
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Serializable
    data class SyncedKey(
        val id: String = "",
        val user_id: String = "",
        val service: String = "",
        val encrypted_value: String = "",
        val updated_at: String = ""
    )

    private val KEY_FIELDS = listOf(
        "gemini_key", "groq_key", "openai_key", "openrouter_key", "huggingface_key",
        "azure_speech_key", "azure_speech_region", "elevenlabs_key",
        "pexels_key", "pixabay_key"
    )

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "qabas_keysync_master_v2"

    private fun getOrCreateSecretKey(context: Context): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGen.init(spec)
                keyGen.generateKey()
            } else {
                val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
                entry.secretKey
            }
        } catch (e: Exception) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            var salt = prefs.getString("key_sync_salt", null)
            if (salt == null) {
                val bytes = ByteArray(32)
                SecureRandom().nextBytes(bytes)
                salt = Base64.encodeToString(bytes, Base64.NO_WRAP)
                prefs.edit().putString("key_sync_salt", salt).apply()
            }
            val keyBytes = MessageDigest.getInstance("SHA-256").digest(salt.toByteArray(Charsets.UTF_8))
            SecretKeySpec(keyBytes, "AES")
        }
    }

    private fun encrypt(value: String, context: Context): String? {
        return try {
            val secretKey = getOrCreateSecretKey(context)
            val iv = ByteArray(GCM_IV_LENGTH_BYTES)
            SecureRandom().nextBytes(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
            val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val payload = iv + ciphertext
            Base64.encodeToString(payload, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Encrypt failed; refusing plaintext fallback", e)
            null
        }
    }

    private fun decrypt(encrypted: String, context: Context): String {
        return try {
            val secretKey = getOrCreateSecretKey(context)
            val decoded = Base64.decode(encrypted, Base64.NO_WRAP)

            if (decoded.size > GCM_IV_LENGTH_BYTES) {
                val iv = decoded.copyOfRange(0, GCM_IV_LENGTH_BYTES)
                val ciphertext = decoded.copyOfRange(GCM_IV_LENGTH_BYTES, decoded.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
                val decrypted = cipher.doFinal(ciphertext)
                String(decrypted, Charsets.UTF_8)
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Decrypt failed", e)
            ""
        }
    }

    private fun getUserId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("user_id", null)
            ?: prefs.getString("user_email", "anonymous")?.let { email ->
                val digest = MessageDigest.getInstance("SHA-256").digest(email.toByteArray())
                digest.take(16).joinToString("") { "%02x".format(it) }
            }
            ?: "anonymous"
    }

    fun pushToCloud(context: Context, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!SupabaseConfig.isConfigured) {
            onResult?.invoke(false, "Supabase غير مُعد")
            return
        }

        scope.launch {
            try {
                val keyPrefs = AgentPrefs.instance(context)
                val userId = getUserId(context)
                val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date())

                val keysToSync = KEY_FIELDS.mapNotNull { field ->
                    val value = keyPrefs.getString(field, "") ?: ""
                    if (value.isNotBlank() && !value.startsWith("your_")) {
                        val enc = encrypt(value, context)
                        if (enc != null) {
                            mapOf(
                                "user_id" to userId,
                                "service" to field,
                                "encrypted_value" to enc,
                                "updated_at" to timestamp
                            )
                        } else null
                    } else null
                }

                if (keysToSync.isEmpty()) {
                    withContext(Dispatchers.Main) { onResult?.invoke(false, "لا توجد مفاتيح لل-sync") }
                    return@launch
                }

                // Upsert each key
                keysToSync.forEach { keyData ->
                    SupabaseConfig.client.from(SYNC_TABLE).upsert(
                        json.parseToJsonElement(json.encodeToString(
                            kotlinx.serialization.serializer(),
                            keyData
                        )) as? kotlinx.serialization.json.JsonObject ?: return@forEach
                    ) {
                        onConflict = "user_id,service"
                    }
                }

                withContext(Dispatchers.Main) {
                    onResult?.invoke(true, "تم رفع ${keysToSync.size} مفتاح للسحابة ☁️✅")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Push failed", e)
                withContext(Dispatchers.Main) {
                    onResult?.invoke(false, "فشل الرفع: ${e.message ?: "خطأ غير معروف"}")
                }
            }
        }
    }

    fun pullFromCloud(context: Context, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!SupabaseConfig.isConfigured) {
            onResult?.invoke(false, "Supabase غير مُعد")
            return
        }

        scope.launch {
            try {
                val keyPrefs = AgentPrefs.instance(context)
                val userId = getUserId(context)

                val result = SupabaseConfig.client.from(SYNC_TABLE).select {
                    filter { eq("user_id", userId) }
                    order("updated_at", Order.DESCENDING)
                }.decodeList<SyncedKey>()

                if (result.isEmpty()) {
                    withContext(Dispatchers.Main) { onResult?.invoke(false, "لا توجد مفاتيح محفوظة في السحابة") }
                    return@launch
                }

                var imported = 0
                val editor = keyPrefs.edit()
                result.forEach { synced ->
                    val decrypted = decrypt(synced.encrypted_value, context)
                    if (decrypted.isNotBlank() && synced.service in KEY_FIELDS) {
                        editor.putString(synced.service, decrypted)
                        imported++
                    }
                }
                editor.apply()

                withContext(Dispatchers.Main) {
                    onResult?.invoke(true, "تم سحب $imported مفتاح من السحابة ☁️📥")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Pull failed", e)
                withContext(Dispatchers.Main) {
                    onResult?.invoke(false, "فشل السحب: ${e.message ?: "خطأ غير معروف"}")
                }
            }
        }
    }

    fun syncBidirectional(context: Context, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!SupabaseConfig.isConfigured) {
            onResult?.invoke(false, "Supabase غير مُعد")
            return
        }

        scope.launch {
            try {
                // First pull, then push (merge strategy: newest wins)
                pullFromCloud(context) { pullSuccess, pullMsg ->
                    if (pullSuccess) {
                        pushToCloud(context) { pushSuccess, pushMsg ->
                            onResult?.invoke(pushSuccess, "$pullMsg\n$pushMsg")
                        }
                    } else {
                        // Pull failed, just push local keys
                        pushToCloud(context) { pushSuccess, pushMsg ->
                            onResult?.invoke(pushSuccess, "سحب فاشل: $pullMsg\n$pushMsg")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult?.invoke(false, "خطأ في المزامنة: ${e.message}")
                }
            }
        }
    }
}
