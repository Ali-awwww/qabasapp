package com.qabas.app

import android.content.Context
import android.util.Log

/**
 * حارس صلاحيات المطور: مصدر رتبة المطور هو جلسة Supabase Auth
 * وبيانات app_metadata الموثوقة فقط؛ لا تُستخدم تفضيلات الجهاز أو بيانات الملف الشخصي.
 */
object AdminGuard {
    private const val TAG = "AdminGuard"

    /**
     * Checks if the user is the owner based on their email.
     * Replaces the old Firebase owner check.
     */
    fun isCloudUserOwner(): Boolean {
        return try {
            val email = SupabaseServices.Auth.getCurrentUserEmail()
            !email.isNullOrBlank() && email.equals(CloudServices.OWNER_EMAIL, ignoreCase = true)
        } catch (e: Exception) {
            Log.w(TAG, "isCloudUserOwner failed: ${e.message}")
            false
        }
    }

    /**
     * لوحة المطور تُفتح فقط عند وجود رتبة موثوقة من Supabase app_metadata.
     * بيانات users.type وuser_metadata والتفضيلات المحلية ليست مصادر صلاحية.
     */
    fun isDashboardAccessAllowed(context: Context): Boolean {
        val userId = SupabaseServices.Auth.getCurrentUserId() ?: return false
        val role = if (isCloudUserOwner()) "owner" else SupabaseServices.Auth.getUserRoleFromMetadata()
        RolePromotionNotifier.checkAndNotifyPromotion(context, userId, role)
        return SupabaseServices.Auth.isDeveloperRole(role)
    }

    /**
     * صلاحية مركز سجلات الانهيارات: تعتمد فقط على المالك الموثق أو app_metadata.role
     * الصادر من Supabase Auth، ولا تثق ببيانات الملف الشخصي أو التفضيلات المحلية.
     */
    fun isCrashTelemetryAccessAllowed(): Boolean {
        if (!SupabaseServices.isSupabaseAvailable) return false
        return isCloudUserOwner() ||
            SupabaseServices.Auth.isDeveloperRole(SupabaseServices.Auth.getCurrentUserAppRole())
    }

    /**
     * فحص صلاحيات الإدارة للمهام السحابية الحساسة بناءً على السحابة حصرياً.
     */
    suspend fun isAuthAdmin(): Boolean {
        return SupabaseServices.Auth.isAuthAdmin() || isCloudUserOwner()
    }

    fun currentIdentity(context: Context): String {
        val cloudEmail = SupabaseServices.Auth.getCurrentUserEmail()
        return cloudEmail ?: run {
            val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            prefs.getString("dev_display_name", null) ?: "local_device"
        }
    }
}
