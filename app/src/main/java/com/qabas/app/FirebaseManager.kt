package com.qabas.app

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * مدير التكامل مع Firebase Firestore والمصادقة
 * يدعم التخزين المحلي التلقائي والمزامنة اللحظية
 */
object FirebaseManager {
    private const val TAG = "FirebaseManager"
    private var firestoreInstance: FirebaseFirestore? = null

    /**
     * الحصول على نسخة Firestore المهيأة حصراً بقاعدة البيانات المخصصة
     */
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
                            .setSizeBytes(104857600L) // 100 MB persistent offline cache
                            .build()
                    )
                    .build()
            } catch (_: Exception) {
                // Settings can only be configured before any other operation
            }
            firestoreInstance = instance
        }
        return firestoreInstance!!
    }

    /**
     * الحصول على مصادقة Firebase
     */
    fun getAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    /**
     * التحقق من تفعيل وتهيئة Firebase
     */
    fun isConfigured(context: Context): Boolean {
        return try {
            val dbId = context.getString(R.string.firestore_database_id)
            dbId.isNotBlank()
        } catch (_: Exception) {
            false
        }
    }

    private fun isSyntheticUserId(userId: String?): Boolean {
        if (userId.isNullOrBlank()) return true
        return userId == "local_user" || userId.startsWith("local_") || userId.startsWith("dev_") || userId.startsWith("anon_")
    }

    /**
     * مزامنة بيانات المستخدم مع Firestore
     */
    suspend fun syncUserToFirestore(context: Context, userId: String, displayName: String?, email: String?) {
        val auth = FirebaseAuth.getInstance().currentUser
        val targetUid = auth?.uid ?: userId
        if (isSyntheticUserId(targetUid) || auth == null) {
            Log.d(TAG, "syncUserToFirestore skipped for synthetic or unauthenticated user: $targetUid")
            return
        }
        try {
            val db = getFirestore(context)
            val userRef = db.collection("users").document(targetUid)

            val payload = hashMapOf<String, Any>(
                "userId" to targetUid,
                "has_reward" to false,
                "total_wins" to 0,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!displayName.isNullOrBlank()) {
                payload["displayName"] = displayName
            }
            if (!email.isNullOrBlank()) {
                payload["email"] = email
            }

            userRef.set(payload, SetOptions.merge()).await()
            Log.d(TAG, "User $targetUid synced to Firestore successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync user to Firestore: ${e.message}")
        }
    }

    /**
     * مزامنة إدخال الدوري الأسبوعي مع Firestore
     */
    suspend fun syncLeaderboardToFirestore(
        context: Context,
        userId: String,
        name: String,
        points: Int,
        rank: Int,
        badge: String
    ) {
        val auth = FirebaseAuth.getInstance().currentUser
        val targetUid = auth?.uid ?: userId
        if (isSyntheticUserId(targetUid) || auth == null) {
            Log.d(TAG, "syncLeaderboardToFirestore skipped for synthetic or unauthenticated user: $targetUid")
            return
        }
        try {
            val db = getFirestore(context)
            val entryRef = db.collection("leaderboard").document(targetUid)

            val payload = hashMapOf<String, Any>(
                "userId" to targetUid,
                "name" to name,
                "points" to points,
                "rank" to rank,
                "badge" to badge,
                "is_eligible" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            entryRef.set(payload, SetOptions.merge()).await()
            Log.d(TAG, "Leaderboard entry synced to Firestore successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync leaderboard to Firestore: ${e.message}")
        }
    }

    /**
     * مزامنة مشروع مرئي مع Firestore
     */
    suspend fun saveProjectToFirestore(
        context: Context,
        userId: String,
        projectId: String,
        title: String,
        script: String,
        hasVoiceover: Boolean
    ) {
        val auth = FirebaseAuth.getInstance().currentUser
        val targetUid = auth?.uid ?: userId
        if (isSyntheticUserId(targetUid) || auth == null) {
            Log.d(TAG, "saveProjectToFirestore skipped for synthetic or unauthenticated user: $targetUid")
            return
        }
        try {
            val db = getFirestore(context)
            val projectRef = db.collection("users").document(targetUid).collection("projects").document(projectId)

            val payload = hashMapOf<String, Any>(
                "projectId" to projectId,
                "userId" to targetUid,
                "title" to title,
                "ai_script" to script,
                "has_voiceover" to hasVoiceover,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            projectRef.set(payload, SetOptions.merge()).await()
            Log.d(TAG, "Project $projectId synced to Firestore successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync project to Firestore: ${e.message}")
        }
    }
}
