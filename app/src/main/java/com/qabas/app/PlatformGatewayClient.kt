package com.qabas.app

import android.util.Log
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class PlatformGatewayResponse(
    val statusCode: Int,
    val body: JSONObject?
) {
    val isSuccessful: Boolean get() = statusCode in 200..299
}

/** Secure client for the Supabase Edge Function qabas-gateway. */
object PlatformGatewayClient {
    private const val TAG = "PlatformGatewayClient"

    /**
     * ق71: سبب آخر فشل — كان callGatewaySuspend يرجع null لكل الأسباب
     * (بلا إعداد/بلا جلسة/انقطاع شبكة/خطأ خادم) فيكذب المنادون على
     * المستخدم («لم يُضبط مفتاح» بينما السبب انقطاع شبكة أو خروج من
     * الحساب). null هنا تعني «لم يصل جواب قابل للاستعمال» فقط؛ أما
     * 200 مع إجابة «لا» (مثل configured=false) فيُبقيه null.
     */
    @Volatile
    var lastError: String? = null
        private set

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        // ق71: بلا writeTimeout كان الافتراضي 10 ثوانٍ — رفع صورة
        // base64 (ميغابايتات) على اتصال بطيء يفشل قبل اكتمال الكتابة.
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun getFunctionUrl(): String? {
        val url = SupabaseConfig.url.trim().trimEnd('/')
        return if (url.isBlank()) null else "$url/functions/v1/qabas-gateway"
    }

    // ق71: قراءة الرمز مباشرة من الجلسة — كان انعكاساً (reflection)
    // يبحث عن أي دالة بلا معاملات اسمها يحوي accesstoken؛ هشّ أمام
    // أي تغيير في صنف الجلسة، والخاصية المباشرة مستعملة أصلاً في
    // SupabaseRepository على العميل نفسه (client.auth
    // .currentSessionOrNull()?.accessToken).
    private fun currentAccessToken(): String? =
        runCatching { SupabaseConfig.client.auth.currentSessionOrNull()?.accessToken }
            .getOrNull()?.takeIf { it.isNotBlank() }

    private suspend fun callGatewaySuspend(body: JSONObject): PlatformGatewayResponse? = withContext(Dispatchers.IO) {
        lastError = null
        if (!SupabaseConfig.isConfigured) {
            lastError = "لم تُضبط خدمة Supabase في التطبيق"
            return@withContext null
        }
        val token = currentAccessToken() ?: run {
            Log.w(TAG, "No active Supabase session for gateway request")
            lastError = "لا توجد جلسة دخول نشطة — سجّل الدخول أولاً"
            return@withContext null
        }
        val functionUrl = getFunctionUrl() ?: run {
            lastError = "تعذّر تحديد عنوان بوابة خادم المسلم"
            return@withContext null
        }

        try {
            val request = Request.Builder()
                .url(functionUrl)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val json = raw.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
                if (!response.isSuccessful) {
                    lastError = "رفضت بوابة خادم المسلم الطلب (HTTP ${response.code})"
                }
                PlatformGatewayResponse(response.code, json)
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Log.w(TAG, "Gateway request failed", e)
            lastError = if (e is java.io.IOException) {
                "تعذّر الاتصال ببوابة خادم المسلم — تحقق من الشبكة"
            } else {
                "تعذّر طلب بوابة خادم المسلم"
            }
            null
        }
    }

    suspend fun proxyHiggsfieldSuspend(payload: JSONObject): PlatformGatewayResponse? =
        callGatewaySuspend(JSONObject().apply {
            put("action", "proxy")
            put("provider", "higgsfield")
            put("payload", payload)
        })

    suspend fun saveProviderKeySuspend(provider: String, key: String): Boolean {
        if (provider.isBlank() || key.isBlank()) return false
        val response = callGatewaySuspend(JSONObject().apply {
            put("action", "save")
            put("keys", JSONObject().put(provider.trim().lowercase(), key.trim()))
        }) ?: return false
        return response.isSuccessful && response.body?.optBoolean("ok", false) == true
    }

    suspend fun removeProviderKeySuspend(provider: String): Boolean {
        if (provider.isBlank()) return false
        val response = callGatewaySuspend(JSONObject().apply {
            put("action", "delete")
            put("service", provider.trim().lowercase())
        }) ?: return false
        return response.isSuccessful && response.body?.optBoolean("ok", false) == true
    }

    suspend fun providerConfiguredSuspend(provider: String): Boolean {
        if (provider.isBlank()) return false
        val response = callGatewaySuspend(JSONObject().apply {
            put("action", "provider_status")
            put("provider", provider.trim().lowercase())
        }) ?: return false
        return response.isSuccessful && response.body?.optBoolean("configured", false) == true
    }

    // (ق38) حُذفت الواجهة العامة proxy()/proxySuspend() من هنا: فرع البروكسي
    // العام (gemini/groq/openrouter) قُتل في الدالة نفسها فصارتا تستدعيان
    // فرعاً ميتاً يرجع 400 دائماً — إبقاؤهما فخ صامت لمستدعٍ مستقبلي.
}
