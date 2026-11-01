package com.qabas.app

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

/**
 * Legacy Firestore access retained only for features not yet migrated to Supabase.
 * Account identity and authentication are owned by Supabase Auth.
 */
object FirebaseManager {
    private const val TAG = "FirebaseManager"
    private var firestoreInstance: FirebaseFirestore? = null

    fun getFirestore(context: Context): FirebaseFirestore {
        if (firestoreInstance == null) {
            val databaseId = try {
                context.getString(R.string.firestore_database_id)
            } catch (e: Exception) {
                Log.e(TAG, "Missing firestore_database_id in resources", e)
                ""
            }

            require(databaseId.isNotBlank()) {
                "firestore_database_id must be defined in resources"
            }

            val instance = FirebaseFirestore.getInstance(databaseId)
            try {
                instance.firestoreSettings = FirebaseFirestoreSettings.Builder(instance.firestoreSettings)
                    .setLocalCacheSettings(
                        PersistentCacheSettings.newBuilder()
                            .setSizeBytes(104857600L)
                            .build()
                    )
                    .build()
            } catch (_: Exception) {
                // Settings can only be configured before Firestore performs its first operation.
            }
            firestoreInstance = instance
        }
        return firestoreInstance!!
    }

    fun isConfigured(context: Context): Boolean = runCatching {
        context.getString(R.string.firestore_database_id).isNotBlank()
    }.getOrDefault(false)
}
