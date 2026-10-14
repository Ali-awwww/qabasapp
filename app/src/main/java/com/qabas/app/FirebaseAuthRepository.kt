package com.qabas.app

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * مستودع مصادقة Firebase وإدارة حساب المستخدم عبر Google Credential Manager
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth = Firebase.auth,
    private val db: FirebaseFirestore? = null
) {
    companion object {
        private const val TAG = "FirebaseAuthRepo"

        fun create(context: Context): FirebaseAuthRepository {
            val database = FirebaseManager.getFirestore(context.applicationContext)
            return FirebaseAuthRepository(Firebase.auth, database)
        }
    }

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    /**
     * تدفق يراقب حالة المصادقة في الوقت الحقيقي
     */
    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * تنفيذ تسجيل الدخول التفاعلي عبر حساب Google باستخدام Jetpack Credential Manager
     */
    suspend fun signInWithGoogle(context: Context): Result<FirebaseUser> {
        val serverClientId = try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) context.getString(resId) else ""
        } catch (_: Exception) {
            ""
        }

        if (serverClientId.isBlank()) {
            return Result.failure(IllegalStateException("معرف العميل (default_web_client_id) غير معرف في الموارد"))
        }

        return try {
            val credentialManager = CredentialManager.create(context)
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
            val idToken = googleIdTokenCredential.idToken

            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val user = checkNotNull(authResult.user) { "FirebaseUser is null after sign-in" }

            // مزامنة الملف الشخصي مع Firestore تلقائياً
            syncUserProfile(user)

            Result.success(user)
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "Google Sign-In was cancelled by user")
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            Result.failure(e)
        }
    }

    /**
     * تسجيل الخروج
     */
    fun signOut() {
        auth.signOut()
    }

    /**
     * مزامنة أو إنشاء ملف تعريف المستخدم في Firestore
     */
    suspend fun syncUserProfile(
        user: FirebaseUser, 
        fcmToken: String? = null,
        customId: String? = null,
        accountNumber: String? = null
    ) {
        val firestore = db ?: return
        val docRef = firestore.collection("users").document(user.uid)
        try {
            val payload = hashMapOf<String, Any>(
                "userId" to user.uid,
                "displayName" to (user.displayName ?: "مستخدم قبس"),
                "email" to (user.email ?: ""),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            user.photoUrl?.let { payload["photoUrl"] = it.toString() }
            if (!fcmToken.isNullOrBlank()) {
                payload["fcmToken"] = fcmToken
            }
            if (!customId.isNullOrBlank()) {
                payload["customId"] = customId
            }
            if (!accountNumber.isNullOrBlank()) {
                payload["accountNumber"] = accountNumber
            }

            // Create if not exists with createdAt
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                payload["createdAt"] = FieldValue.serverTimestamp()
                payload["has_reward"] = false
                payload["total_wins"] = 0
            }

            docRef.set(payload, SetOptions.merge()).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Log.w(TAG, "Failed to sync user profile: ${e.message}")
        }
    }

    /**
     * مراقبة ملف تعريف المستخدم في الوقت الفعلي
     */
    fun observeUserProfile(userId: String): Flow<UserProfile?> = callbackFlow {
        val firestore = db
        if (firestore == null || userId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val docRef = firestore.collection("users").document(userId)
        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.GET, docRef.path)
                trySend(null)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val profile = snapshot.toObject(UserProfile::class.java)
                trySend(profile)
            } else {
                trySend(null)
            }
        }

        awaitClose { registration.remove() }
    }
}
