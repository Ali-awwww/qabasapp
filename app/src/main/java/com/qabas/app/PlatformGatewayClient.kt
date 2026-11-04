package com.qabas.app

import android.util.Log
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun getFunctionUrl(): String? {
        val url = SupabaseConfig.url.trim().trimEnd('/')
        return if (url.isBlank()) null else "$url/functions/v1/qabas-gateway"
    }

    private fun currentAccessToken(): String? {
        val session = runCatching { SupabaseConfig.client.auth.currentSessionOrNull() }.getOrNull() ?: return null
        val methods = session.javaClass.methods.filter { it.parameterTypes.isEmpty() }
        val tokenMethod = methods.firstOrNull { method ->
            method.name.lowercase().replace("_", "").contains("accesstoken")
        } ?: return null
        return runCatching { tokenMethod.invoke(session) as? String }.getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private suspend fun callGatewaySuspend(body: JSONObject): PlatformGatewayResponse? = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext null
        val token = currentAccessToken() ?: run {
            Log.w(TAG, "No active Supabase session for gateway request")
            return@withContext null
        }
        val functionUrl = getFunctionUrl() ?: return@withContext null

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
                PlatformGatewayResponse(response.code, json)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gateway request failed", e)
            null
        }
    }

    suspend fun proxySuspend(provider: String, payload: JSONObject): JSONObject? {
        val response = callGatewaySuspend(JSONObject().apply {
            put("action", "proxy")
            put("provider", provider.trim().lowercase())
            put("payload", payload)
        }) ?: return null
        return response.body?.takeIf { response.isSuccessful }
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

    fun proxy(provider: String, payload: JSONObject): JSONObject? =
        runCatching { runBlocking { proxySuspend(provider, payload) } }.getOrNull()

    suspend fun proxyGemini(payload: JSONObject): JSONObject? = proxySuspend("gemini", payload)
    suspend fun proxyGroq(payload: JSONObject): JSONObject? = proxySuspend("groq", payload)
    suspend fun proxyOpenAI(payload: JSONObject): JSONObject? = proxySuspend("openai", payload)
    suspend fun proxyOpenRouter(payload: JSONObject): JSONObject? = proxySuspend("openrouter", payload)
}