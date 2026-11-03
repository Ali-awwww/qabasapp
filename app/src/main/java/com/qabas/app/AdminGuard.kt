package com.qabas.app

import android.content.Context
import android.util.Log

/**
 * حارس الوصول للوحة المطور (AdminGuard):
 * 1) الوصول المحلي «الراثي»: العلم `is_developer` يُحترَم فقط إذا كان مكتوباً فعلياً
 *    في التفضيلات (يساوي true) — لا يُمنح افتراضياً لأي جهاز جديد (إغلاق الثغرة).
 * 2) الوصول الحاسم: بريد مستخدم Firebase Auth الحالي == بريد المالك.
 * 3) يوفر هوية الجالس للاستخدام في سجل التدقيق.
 */
object AdminGuard {
    private const val TAG = "AdminGuard"

    /**
     * Checks if the user is the owner based on their email.
     * Replaces the old Firebase owner check.
     */
    fun isCloudUserOwner(): Boolean {
        return try {
            val email = SupabaseServices.Auth.currentUser.value?.email
            email != null && email.equals(CloudServices.OWNER_EMAIL, ignoreCase = true)
        } catch (e: Exception) {
            Log.w(TAG, "isCloudUserOwner failed: ${e.message}")
            false
        }
    }

    /**
     * هل الوصول إلى لوحة المطور مسموح؟
     * محصور حصرياً بالسحابة (Supabase RBAC) دون أي تجاوزات محلية:
     * 1) أن يكون مالك التطبيق السحابي.
     * 2) أو أن تكون رتبته في السحابة (app_metadata.role أو user_metadata أو users.type)
     *    ضمن الرتب المعتمدة: developer, admin, owner, مطور, مكور.
     */
    fun isDashboardAccessAllowed(context: Context): Boolean {
        if (isCloudUserOwner()) {
            RolePromotionNotifier.checkAndNotifyPromotion(context, "owner")
            return true
        }

        val appRole = SupabaseServices.Auth.getCurrentUserAppRole()
        if (SupabaseServices.Auth.isDeveloperRole(appRole)) {
            RolePromotionNotifier.checkAndNotifyPromotion(context, appRole)
            return true
        }

        val metaRole = SupabaseServices.Auth.getUserRoleFromMetadata()
        if (SupabaseServices.Auth.isDeveloperRole(metaRole)) {
            RolePromotionNotifier.checkAndNotifyPromotion(context, metaRole)
            return true
        }

        val verifiedRole = SupabaseServices.Auth.verifiedRole.value
        if (SupabaseServices.Auth.isDeveloperRole(verifiedRole)) {
            RolePromotionNotifier.checkAndNotifyPromotion(context, verifiedRole)
            return true
        }

        val dbType = SupabaseServices.Auth.currentUser.value?.type
        if (SupabaseServices.Auth.isDeveloperRole(dbType)) {
            RolePromotionNotifier.checkAndNotifyPromotion(context, dbType)
            return true
        }

        return false
    }

    /**
     * فحص صلاحيات الإدارة للمهام السحابية الحساسة بناءً على السحابة حصرياً.
     */
    suspend fun isAuthAdmin(): Boolean {
        return SupabaseServices.Auth.isAuthAdmin() || isCloudUserOwner()
    }

    fun currentIdentity(context: Context): String {
        val cloudEmail = SupabaseServices.Auth.currentUser.value?.email
        return cloudEmail ?: run {
            val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            prefs.getString("dev_display_name", null) ?: "local_device"
        }
    }
}
