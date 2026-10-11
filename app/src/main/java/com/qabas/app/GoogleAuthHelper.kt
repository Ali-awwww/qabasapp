package com.qabas.app

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * نتيجة تسجيل الدخول عبر حساب Google
 */
data class GoogleSignInResult(
    val email: String,
    val displayName: String?,
    val idToken: String
)

/**
 * مساعد تسجيل الدخول عبر Google باستخدام Credential Manager و Supabase Auth
 */
object GoogleAuthHelper {
    private const val TAG = "GoogleAuthHelper"
    private const val PREF_KEY_WEB_CLIENT_ID = "google_web_client_id"

    val SUPABASE_GOOGLE_CALLBACK_URL: String get() = "${SupabaseConfig.url}/auth/v1/callback"

    /**
     * استخراج معرف العميل (Web Client ID)
     * يبحث في موارد Firebase أولاً (R.string.default_web_client_id)، ثم في SharedPreferences أو BuildConfig
     */
    fun getWebClientId(context: Context): String {
        val fromRes = runCatching {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) context.getString(resId) else ""
        }.getOrDefault("")
        if (fromRes.isNotBlank()) return fromRes

        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getString(PREF_KEY_WEB_CLIENT_ID, "")?.trim() ?: ""
        if (saved.isNotBlank() && !saved.startsWith("your_", ignoreCase = true)) return saved

        // Fallback to BuildConfig if provided via .env
        val fromBuild = runCatching {
            val field = BuildConfig::class.java.getField("GOOGLE_WEB_CLIENT_ID")
            field.get(null) as? String
        }.getOrNull()?.trim()

        if (!fromBuild.isNullOrBlank() && !fromBuild.startsWith("your_", ignoreCase = true)) {
            return fromBuild
        }

        return ""
    }

    /**
     * حفظ معرف العميل في إعدادات التطبيق
     */
    fun saveWebClientId(context: Context, clientId: String) {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_KEY_WEB_CLIENT_ID, clientId.trim()).apply()
    }

    /**
     * التحقق مما إذا كان معرّف العميل صالحاً
     */
    fun isWebClientIdConfigured(context: Context): Boolean {
        val id = getWebClientId(context)
        return id.isNotBlank() && id.length > 10
    }

    /**
     * تنفيذ طلب تسجيل الدخول بحساب Google عبر Credential Manager واستخراج ID Token
     */
    suspend fun signInWithGoogle(
        context: Context,
        serverClientId: String = getWebClientId(context)
    ): Result<GoogleSignInResult> = withContext(Dispatchers.IO) {
        if (serverClientId.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("لم يتم ضبط Google Web Client ID بعد. يرجى إدخال المعرف لتفعيل التسجيل.")
            )
        }

        try {
            val credentialManager = CredentialManager.create(context)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            val credential = result.credential
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)

            val email = googleIdTokenCredential.id
            val displayName = googleIdTokenCredential.displayName
            val idToken = googleIdTokenCredential.idToken

            Log.d(TAG, "Google Sign-In successful for $email")

            Result.success(
                GoogleSignInResult(
                    email = email,
                    displayName = displayName,
                    idToken = idToken
                )
            )
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "User cancelled Google Sign-In")
            Result.failure(e)
        } catch (e: androidx.credentials.exceptions.NoCredentialException) {
            Log.e(TAG, "No Google accounts found or Client ID mismatch: ${e.message}")
            val sha1 = "B9:4C:DE:BF:D3:73:F2:F1:D6:8E:6D:0B:5B:14:65:43:E4:F6:06:49"
            Result.failure(
                IllegalStateException("لم يتم العثور على حسابات Google متاحة. تأكد من أن معرّف العميل (Client ID) مسجل في Google Cloud بهذا البصمة (SHA-1): $sha1")
            )
        } catch (e: GetCredentialException) {
            Log.e(TAG, "CredentialManager error: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected Google Sign-In error: ${e.message}", e)
            Result.failure(e)
        }
    }
}
