package com.qabas.app

import android.content.Context

/**
 * Legacy Firestore access retained only for features not yet migrated to Supabase.
 * Account identity and authentication are owned by Supabase Auth.
 */
object FirebaseManager {
    private const val TAG = "FirebaseManager"

    fun isConfigured(context: Context): Boolean = runCatching {
        context.getString(R.string.firestore_database_id).isNotBlank()
    }.getOrDefault(false)
}
