package com.qabas.app

import android.content.Context
import java.util.UUID

enum class ActiveSessionClaimResult {
    CLAIMED,
    ACTIVE_ELSEWHERE,
    PROFILE_MISSING,
    UNAVAILABLE
}

internal fun parseActiveSessionClaimStatus(status: String?): ActiveSessionClaimResult =
    when (status?.trim()?.lowercase()) {
        "claimed" -> ActiveSessionClaimResult.CLAIMED
        "active_elsewhere" -> ActiveSessionClaimResult.ACTIVE_ELSEWHERE
        "profile_missing" -> ActiveSessionClaimResult.PROFILE_MISSING
        else -> ActiveSessionClaimResult.UNAVAILABLE
    }

/**
 * إدارة الجلسة النشطة للحساب: جلسة واحدة فقط لكل حساب على جميع الأجهزة.
 * - كل تثبيت يحصل على معرّف جلسة ثابت.
 * - الحجز والنبضة الذرّية يُنفّذان في Supabase بساعة الخادم.
 * - تعذّر التحقق حالة مستقلة ولا يُعامل كنجاح أو كتعرض لجهاز آخر.
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
        // Active-session leases are cloud authorization records. Never release
        // or claim one using a locally cached ID/email after Auth has expired.
        return SupabaseServices.Auth.getCurrentUserId()
            ?.takeIf { it.isNotBlank() }
    }

    suspend fun claim(context: Context, userId: String): ActiveSessionClaimResult =
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
            .remove("is_guest")
            .remove("is_admin")
            .remove("is_developer")
            .remove("user_id")
            .remove("user_email")
            .remove("user_name")
            .remove("user_display_name")
            .remove("user_custom_id")
            .remove("user_tag")
            .remove("user_account_number")
            .remove("custom_id")
            .apply()
        RolePromotionNotifier.dismissCelebration()
        SupabaseServices.Auth.clearVerifiedRole()
    }
}
