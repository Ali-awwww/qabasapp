package com.qabas.app

import org.json.JSONObject

/**
 * Thin adapters around PlatformGatewayClient.
 *
 * These helpers normalize the gateway response shape and let the existing
 * RealServices code switch from direct provider calls to the Supabase gateway
 * without changing the rest of their parsing logic too much.
 */
object AiGatewayAdapters {

    private fun normalizeResponse(response: JSONObject): JSONObject {
        if (response.has("data") && response.opt("data") is JSONObject) {
            return response.optJSONObject("data") ?: response
        }
        if (response.has("result") && response.opt("result") is JSONObject) {
            return response.optJSONObject("result") ?: response
        }
        if (response.has("payload") && response.opt("payload") is JSONObject) {
            return response.optJSONObject("payload") ?: response
        }
        return response
    }

    fun proxyGemini(payload: JSONObject): JSONObject? =
        PlatformGatewayClient.proxy("gemini", payload)?.let(::normalizeResponse)

    fun proxyGroq(payload: JSONObject): JSONObject? =
        PlatformGatewayClient.proxy("groq", payload)?.let(::normalizeResponse)

    fun proxyOpenAI(payload: JSONObject): JSONObject? =
        PlatformGatewayClient.proxy("openai", payload)?.let(::normalizeResponse)

    fun proxyOpenRouter(payload: JSONObject): JSONObject? =
        PlatformGatewayClient.proxy("openrouter", payload)?.let(::normalizeResponse)

    fun extractTextFromStandardProviderJson(responseJson: JSONObject): String? {
        val candidates = responseJson.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null
        val parts = candidates.getJSONObject(0)
            .optJSONObject("content")
            ?.optJSONArray("parts") ?: return null
        if (parts.length() == 0) return null
        return parts.getJSONObject(0).optString("text", "").trim()
            .takeIf { it.isNotBlank() }
    }

    fun extractChoiceText(responseJson: JSONObject): String? {
        val choices = responseJson.optJSONArray("choices") ?: return null
        if (choices.length() == 0) return null
        val message = choices.getJSONObject(0).optJSONObject("message") ?: return null
        return message.optString("content", "").trim().takeIf { it.isNotBlank() }
    }
}
