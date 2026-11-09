package com.qabas.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 👑 RolePromotionNotifier — يرصد ترقية رتبة المستخدم في السحابة
 * ويطلق إشعار نظام أندرويد قياسي عالي الأهمية مع إشعار احتفالي داخل التطبيق.
 */
object RolePromotionNotifier {
    private const val TAG = "RolePromotionNotifier"
    private const val PREFS_NAME = "qabas_role_prefs"
    private const val KEY_ROLE_PREFIX = "cloud_role_for_uid_"
    private val elevatedRoles = setOf("developer", "admin", "owner", "مطور", "مكور")

    data class CelebrationInfo(
        val roleTitle: String,
        val email: String?,
        val message: String
    )

    private val _celebrationEvent = MutableStateFlow<CelebrationInfo?>(null)
    val celebrationEvent: StateFlow<CelebrationInfo?> = _celebrationEvent.asStateFlow()

    fun dismissCelebration() {
        _celebrationEvent.value = null
    }

    /**
     * First observation for a UID is a silent baseline. Celebration is emitted only
     * when a previously observed non-elevated cloud role changes to an elevated role.
     */
    fun checkAndNotifyPromotion(context: Context, userId: String?, newCloudRole: String?) {
        if (userId.isNullOrBlank() || newCloudRole.isNullOrBlank()) return

        val normalizedRole = newCloudRole.trim().lowercase(java.util.Locale.ROOT)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val roleKey = KEY_ROLE_PREFIX + userId
        val previousRole = prefs.getString(roleKey, null)
        prefs.edit().putString(roleKey, normalizedRole).apply()

        val isElevated = normalizedRole in elevatedRoles
        val wasElevated = previousRole?.lowercase(java.util.Locale.ROOT) in elevatedRoles
        if (previousRole == null || !isElevated || wasElevated) return

        Log.i(TAG, "Cloud role changed from $previousRole to elevated $normalizedRole for UID $userId.")
        val roleDisplayName = when (normalizedRole) {
            "owner" -> "مالك المنظومة"
            "admin" -> "مشرف إداري"
            "developer", "مطور", "مكور" -> "مطور معتمد (Developer)"
            else -> normalizedRole
        }
        val title = "🎉 مبارك! تم تفعيل رتبة المطور لحسابك سحابياً"
        val message = "تهانينا! تم التحقق من هويتك كـ $roleDisplayName في سحابة قبس. تم فتح لوحة التحكم السحابية وأدوات التطوير المتقدمة بنجاح."

        try {
            NotificationHelper.showNotification(context = context, title = title, message = message)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to post system notification: ${e.message}")
        }

        val userEmail = SupabaseServices.Auth.getCurrentUserEmail()
            ?: SupabaseServices.Auth.currentUser.value?.email
        _celebrationEvent.value = CelebrationInfo(
            roleTitle = roleDisplayName,
            email = userEmail,
            message = message
        )
    }

}
