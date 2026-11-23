package com.qabas.app

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * يحفظ رمز FCM مؤقتاً عند تجديده، ثم يربطه بحساب Supabase بعد اكتمال تسجيل الدخول.
 */
object PushTokenManager {
    private const val TAG = "PushTokenManager"
    private const val PREFS = "qabas_push_tokens"
    private const val KEY_PENDING_TOKEN = "pending_fcm_token"

    suspend fun captureAndSync(context: Context) {
        runCatching {
            val token = suspendCancellableCoroutine<String> { continuation ->
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) continuation.resume(task.result)
                    else continuation.resumeWithException(task.exception ?: IllegalStateException("FCM token unavailable"))
                }
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_PENDING_TOKEN, token).apply()
            syncPending(context)
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.onFailure {
            Log.w(TAG, "Unable to obtain FCM token: ${it.message}")
        }
    }

    suspend fun syncPending(context: Context) {
        val userId = SupabaseServices.Auth.getCurrentUserId() ?: return
        val token = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PENDING_TOKEN, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return
        if (!SupabaseServices.Database.upsertPushToken(userId, token)) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_PENDING_TOKEN).apply()
    }

    suspend fun onTokenRefreshed(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PENDING_TOKEN, token).apply()
        syncPending(context)
    }
}
