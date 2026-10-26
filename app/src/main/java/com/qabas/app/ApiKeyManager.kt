package com.qabas.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 🔑 مدير مفاتيح الـ API المشفر (BYOK - Bring Your Own Key)
 * يقوم بتخزين مفاتيح Leonardo.ai و Higgsfield AI بشكل مشفر ومحلي.
 */
object ApiKeyManager {
    fun saveLeonardoKey(context: Context, key: String) {
        AgentPrefs.putString(context, "leonardo_api_key", key)
    }

    fun getLeonardoKey(context: Context): String {
        return AgentPrefs.getString(context, "leonardo_api_key")
    }

    fun saveHiggsfieldKey(context: Context, key: String) {
        AgentPrefs.putString(context, "higgsfield_api_key", key)
    }

    fun getHiggsfieldKey(context: Context): String {
        return AgentPrefs.getString(context, "higgsfield_api_key")
    }

    fun clearKeys(context: Context) {
        AgentPrefs.remove(context, "leonardo_api_key")
        AgentPrefs.remove(context, "higgsfield_api_key")
    }
}
