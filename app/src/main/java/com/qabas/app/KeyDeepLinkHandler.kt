package com.qabas.app

import android.content.Context
import android.content.Intent
import android.net.Uri

object KeyDeepLinkHandler {

    private const val TAG = "KeyDeepLinkHandler"
    private const val SCHEME = "qabas"
    private const val HOST_KEYS = "keys"

    // qabas://keys/import?gemini=AIzaSy...&groq=gsk_...
    // qabas://keys/sync?direction=push|pull|bidirectional
    // qabas://keys/validate (triggers validation for all keys)

    data class DeepLinkResult(
        val action: String,
        val keys: Map<String, String> = emptyMap(),
        val syncDirection: String = "bidirectional",
        val isValid: Boolean = false
    )

    fun parseIntent(intent: Intent): DeepLinkResult? {
        val uri = intent.data ?: return null
        return parseUri(uri)
    }

    fun parseUri(uri: Uri): DeepLinkResult? {
        if (uri.scheme != SCHEME || uri.host != HOST_KEYS) return null

        val path = uri.pathSegments.firstOrNull() ?: return null

        return when (path) {
            "import" -> parseImport(uri)
            "sync" -> parseSync(uri)
            "validate" -> DeepLinkResult(action = "validate", isValid = true)
            else -> null
        }
    }

    private fun parseImport(uri: Uri): DeepLinkResult {
        val keyMap = mutableMapOf<String, String>()

        val paramNames = mapOf(
            "gemini" to "gemini_key",
            "groq" to "groq_key",
            "openrouter" to "openrouter_key",
            "pixabay" to "pixabay_key"
        )

        paramNames.forEach { (param, field) ->
            uri.getQueryParameter(param)?.takeIf { it.isNotBlank() }?.let { value ->
                keyMap[field] = value
            }
        }

        return DeepLinkResult(
            action = "import",
            keys = keyMap,
            isValid = keyMap.isNotEmpty()
        )
    }

    private fun parseSync(uri: Uri): DeepLinkResult {
        val direction = uri.getQueryParameter("direction") ?: "bidirectional"
        val validDirections = listOf("push", "pull", "bidirectional")
        return DeepLinkResult(
            action = "sync",
            syncDirection = if (direction in validDirections) direction else "bidirectional",
            isValid = true
        )
    }

    fun applyImportedKeys(context: Context, keys: Map<String, String>): Pair<Boolean, String> {
        if (keys.isEmpty()) return false to "لا توجد مفاتيح"

        // كان الاستيراد يكتب التفضيل المفرد مباشرة بلا تنظيف وبلا
        // مرور بالمسبح — وKeyVault يقرأ المسبح النشط أولاً، فكان
        // المفتاح المستورَد بلا أثر صامتاً عند وجود مسبح، ومحارف النسخ
        // الخفية تمر بلا تنظيف. المسار الواحد الصحيح هو setSingleKey.
        var imported = 0
        val names = mutableListOf<String>()
        keys.forEach { (field, value) ->
            val clean = KeyPoolManager.sanitizeApiKey(value)
            if (clean.isNotBlank()) {
                KeyPoolManager.setSingleKey(field, clean)
                imported++
                names.add(
                    when (field) {
                        "gemini_key" -> "Gemini"
                        "groq_key" -> "Groq"
                        "openrouter_key" -> "OpenRouter"
                        "pixabay_key" -> "Pixabay"
                        else -> field
                    }
                )
            }
        }

        if (imported == 0) return false to "لا توجد مفاتيح صالحة للاستيراد"
        return true to "تم استيراد $imported مفتاح: ${names.joinToString("، ")} ✅"
    }

}
