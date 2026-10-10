package com.qabas.app

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * مدير التكامل مع Firebase Firestore والمصادقة
 * يعمل بالتوازي مع Supabase دون تعارض أو فقدان للبيانات
 */
object FirebaseManager {
    private const val TAG = "FirebaseManager"
    private var firestoreInstance: FirebaseFirestore? = null

    /**
     * الحصول على نسخة Firestore المهيأة بقاعدة البيانات المخصصة
     */
    fun getFirestore(context: Context): FirebaseFirestore {
        if (firestoreInstance == null) {
            val databaseId = try {
                context.getString(R.string.firestore_database_id)
            } catch (e: Exception) {
                Log.w(TAG, "Missing firestore_database_id in resources, using default", e)
                ""
            }

            firestoreInstance = if (databaseId.isNotBlank()) {
                FirebaseFirestore.getInstance(databaseId)
            } else {
                FirebaseFirestore.getInstance()
            }
        }
        return firestoreInstance!!
    }

    /**
     * الحصول على مصادقة Firebase
     */
    fun getAuth(): FirebaseAuth = Firebase.auth

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

    /**
     * مزامنة بيانات المستخدم مع Firestore (بالتوازي مع Supabase)
     */
    suspend fun syncUserToFirestore(context: Context, userId: String, displayName: String?, email: String?) {
        try {
            val db = getFirestore(context)
            val userRef = db.collection("users").document(userId)

            val payload = hashMapOf<String, Any>(
                "userId" to userId,
                "has_reward" to false,
                "total_wins" to 0,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!displayName.isNullOrBlank()) {
                payload["displayName"] = displayName
            }

            // الدمج دون الكتابة فوق الحقول الحالية إذا كانت موجودة
            userRef.set(payload, SetOptions.merge()).await()
            Log.d(TAG, "User $userId synced to Firestore successfully")
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
        try {
            val db = getFirestore(context)
            val entryRef = db.collection("leaderboard").document(userId)

            val payload = hashMapOf<String, Any>(
                "userId" to userId,
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
        try {
            val db = getFirestore(context)
            val projectRef = db.collection("users").document(userId).collection("projects").document(projectId)

            val payload = hashMapOf<String, Any>(
                "projectId" to projectId,
                "userId" to userId,
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
