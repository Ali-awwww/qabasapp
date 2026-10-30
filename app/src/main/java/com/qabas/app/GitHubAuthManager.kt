package com.qabas.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * مدير مصادقة GitHub (OAuth 2.0): التعامل مع تسجيل الدخول واستبدال الرموز.
 * يدعم القراءة المرنة والتلقائية لأي من المفاتيح التالية:
 * - AW_CLIENT_ID / AW_CLIENT_SECRET
 * - QF_CLIENT_ID / QF_CLIENT_SECRET
 * - GITHUB_CLIENT_ID / GITHUB_CLIENT_SECRET
 */
object GitHubAuthManager {

    private fun getSecret(vararg keyNames: String): String {
        for (key in keyNames) {
            try {
                val field = BuildConfig::class.java.getField(key)
                val value = field.get(null) as? String
                if (!value.isNullOrBlank()) {
                    return value
                }
            } catch (_: Exception) {}
        }
        return ""
    }

    private val CLIENT_ID: String
        get() = getSecret("AW_CLIENT_ID", "QF_CLIENT_ID", "GITHUB_CLIENT_ID")

    private val CLIENT_SECRET: String
        get() = getSecret("AW_CLIENT_SECRET", "QF_CLIENT_SECRET", "GITHUB_CLIENT_SECRET")

    private const val REDIRECT_URI = "qabas://github-callback"
    private const val AUTH_URL = "https://github.com/login/oauth/authorize"
    private const val TOKEN_URL = "https://github.com/login/oauth/access_token"

    /**
     * هل تم تهيئة مفاتيح OAuth في ملف .env أو الأسرار؟
     */
    fun isConfigured(): Boolean = CLIENT_ID.isNotBlank() && CLIENT_SECRET.isNotBlank()

    /**
     * بدء عملية تسجيل الدخول عبر GitHub باستخدام Chrome Custom Tabs.
     */
    fun launchAuth(context: Context) {
        val activeClientId = CLIENT_ID
        if (activeClientId.isBlank()) {
            android.widget.Toast.makeText(
                context,
                "خطأ: يرجى إضافة مفتاح AW_CLIENT_ID أو QF_CLIENT_ID في لوحة الأسرار (Secrets)",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }
        val uri = Uri.parse(AUTH_URL)
            .buildUpon()
            .appendQueryParameter("client_id", activeClientId)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", "repo,user")
            .appendQueryParameter("state", java.util.UUID.randomUUID().toString())
            .build()

        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.launchUrl(context, uri)
    }

    /**
     * اسم مستعار لبدء المصادقة.
     */
    fun startAuth(context: Context) = launchAuth(context)

    /**
     * استبدال الرمز المؤقت (Code) برموز الوصول (Access Token).
     */
    suspend fun exchangeCodeForToken(code: String): String? = withContext(Dispatchers.IO) {
        val activeClientId = CLIENT_ID
        val activeClientSecret = CLIENT_SECRET
        if (activeClientId.isBlank() || activeClientSecret.isBlank()) return@withContext null

        try {
            val client = OkHttpClient()
            val formBody = FormBody.Builder()
                .add("client_id", activeClientId)
                .add("client_secret", activeClientSecret)
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .build()

            val request = Request.Builder()
                .url(TOKEN_URL)
                .header("Accept", "application/json")
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body?.string().orEmpty())
                json.optString("access_token").takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * تخزين رمز الوصول في الإعدادات المشتركة بشكل آمن.
     */
    fun saveAccessToken(context: Context, token: String) {
        val prefs = context.getSharedPreferences("qabas_github_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("github_oauth_token", token).apply()
    }

    /**
     * استرجاع رمز الوصول المخزن.
     */
    fun getAccessToken(context: Context): String? {
        val prefs = context.getSharedPreferences("qabas_github_prefs", Context.MODE_PRIVATE)
        return prefs.getString("github_oauth_token", null)
    }

    /**
     * مسح رمز الوصول (تسجيل الخروج).
     */
    fun clearAccessToken(context: Context) {
        val prefs = context.getSharedPreferences("qabas_github_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("github_oauth_token").apply()
    }
}
