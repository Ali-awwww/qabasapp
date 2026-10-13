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

    /** العلم المحلي يُمنح فقط لمن كُتب له صراحةً — الجديد لا يحصل عليه. */
    private fun localDeveloperFlag(context: Context): Boolean {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        return prefs.contains("is_developer") && prefs.getBoolean("is_developer", false)
    }

    fun isDashboardAccessAllowed(context: Context): Boolean =
        isCloudUserOwner() || localDeveloperFlag(context) || (SupabaseServices.Auth.verifiedRole.value in setOf("admin", "developer", "owner"))

    /**
     * Checks if the user is an admin based on Supabase Auth metadata.
     * This is the preferred method for modern security checks.
     */
    suspend fun isAuthAdmin(): Boolean {
        return SupabaseServices.Auth.isAuthAdmin()
    }

    fun currentIdentity(context: Context): String {
        val cloudEmail = SupabaseServices.Auth.currentUser.value?.email
        return cloudEmail ?: run {
            val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            prefs.getString("dev_display_name", null) ?: "local_device"
        }
    }
}
