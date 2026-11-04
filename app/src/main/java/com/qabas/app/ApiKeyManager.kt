package com.qabas.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 🔑 مدير مفاتيح Leonardo.ai المحلية المشفرة.
 * مفاتيح Higgsfield تُدار حصرياً عبر بوابة الخادم المشفرة.
 */
object ApiKeyManager {
    fun saveLeonardoKey(context: Context, key: String) {
        AgentPrefs.putString(context, "leonardo_api_key", key)
    }

    fun getLeonardoKey(context: Context): String {
        return AgentPrefs.getString(context, "leonardo_api_key")
    }

    fun clearKeys(context: Context) {
        AgentPrefs.remove(context, "leonardo_api_key")
        AgentPrefs.remove(context, "higgsfield_api_key")
    }
}
