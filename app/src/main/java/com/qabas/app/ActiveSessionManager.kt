package com.qabas.app

import android.content.Context
import java.util.UUID

/**
 * إدارة الجلسة النشطة للحساب: جلسة واحدة فقط لكل حساب على جميع الأجهزة.
 * - كل تثبيت يحصل على معرّف جلسة ثابت.
 * - عند الدخول نحجز الجلسة على السحابة (users.active_session_id).
 * - نبضة دورية تحافظ على ملكية الجلسة، وفقدانها يعني تسجيل خروج مُجبَر.
 */
object ActiveSessionManager {
    private const val PREFS_NAME = "qabas_prefs"
    private const val KEY_SESSION_ID = "active_device_session_id"

    fun sessionId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_SESSION_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_SESSION_ID, generated).apply()
        return generated
    }

    fun currentUserId(context: Context): String? {
        val fromAuth = SupabaseServices.Auth.getCurrentUserId()
        if (!fromAuth.isNullOrBlank()) return fromAuth
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("user_id", null)?.takeIf { it.isNotBlank() }
            ?: prefs.getString("user_email", null)?.takeIf { it.isNotBlank() }
    }

    suspend fun claim(context: Context, userId: String): Boolean =
        SupabaseServices.Database.claimActiveSession(userId, sessionId(context))

    suspend fun release(context: Context, userId: String) {
        SupabaseServices.Database.releaseActiveSession(userId, sessionId(context))
    }

    /** يحرّر الجلسة قدر الإمكان ويحذف بيانات الجلسة المحلية. */
    suspend fun endLocalSession(context: Context) {
        val userId = currentUserId(context)
        if (!userId.isNullOrBlank()) runCatching { release(context, userId) }
        runCatching { SupabaseServices.Auth.logout() }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove("is_logged_in")
            .remove("is_admin")
            .remove("is_developer")
            .remove("user_id")
            .remove("user_email")
            .remove("user_name")
            .remove("user_display_name")
            .remove("user_custom_id")
            .remove("user_tag")
            .remove("user_account_number")
            .apply()
    }
}
