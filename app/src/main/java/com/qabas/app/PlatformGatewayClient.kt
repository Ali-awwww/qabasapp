package com.qabas.app

import android.util.Log
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Safe gateway client for the Supabase Edge Function `qabas-gateway`.
 *
 * This layer ensures the app never sends API keys directly to external providers.
 * Instead, it authenticates with the Supabase session and lets the edge function
 * own the provider credentials and secrets.
 */
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

    /**
     * Reads the current Supabase JWT from the active session using reflection to avoid
     * depending on the exact Jan Supabase property name across versions.
     */
    private fun currentAccessToken(): String? {
        val session = runCatching { SupabaseConfig.client.auth.currentSessionOrNull() }.getOrNull() ?: return null
        val methods = session.javaClass.methods.filter { it.parameterTypes.isEmpty() }
        val tokenMethod = methods.firstOrNull { method ->
            val name = method.name.lowercase().replace("_", "")
            name.contains("accesstoken")
        } ?: return null

        val token = runCatching { tokenMethod.invoke(session) as? String }.getOrNull()
        return token?.takeIf { it.isNotBlank() }
    }

    /**
     * Safe coroutine-enabled gateway call.
     */
    suspend fun proxySuspend(provider: String, payload: JSONObject): JSONObject? {
        if (!SupabaseConfig.isConfigured) return null

        val normalizedProvider = provider.trim().lowercase()
        if (normalizedProvider.isBlank()) return null

        val token = currentAccessToken() ?: run {
            Log.w(TAG, "No active Supabase session for gateway request")
            return null
        }

        val functionUrl = getFunctionUrl() ?: run {
            Log.w(TAG, "Supabase function URL is unavailable")
            return null
        }

        return try {
            val body = JSONObject().apply {
                put("action", "proxy")
                put("provider", normalizedProvider)
                put("payload", payload)
            }

            val request = Request.Builder()
                .url(functionUrl)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Gateway ${normalizedProvider} failed with HTTP ${response.code}")
                    return null
                }
                val raw = response.body?.string() ?: return null
                if (raw.isBlank()) return null
                JSONObject(raw)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gateway proxy failed for $normalizedProvider", e)
            null
        }
    }

    /**
     * Backwards-compatible blocking wrapper for non-suspend call sites.
     */
    fun proxy(provider: String, payload: JSONObject): JSONObject? =
        runCatching { runBlocking { proxySuspend(provider, payload) } }.getOrNull()

    /**
     * Convenience wrappers for provider-specific edge routing.
     */
    suspend fun proxyGemini(payload: JSONObject): JSONObject? = proxySuspend("gemini", payload)
    suspend fun proxyGroq(payload: JSONObject): JSONObject? = proxySuspend("groq", payload)
    suspend fun proxyOpenAI(payload: JSONObject): JSONObject? = proxySuspend("openai", payload)
    suspend fun proxyOpenRouter(payload: JSONObject): JSONObject? = proxySuspend("openrouter", payload)
}
