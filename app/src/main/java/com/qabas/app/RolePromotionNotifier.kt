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
    private const val KEY_LAST_NOTIFIED_ROLE = "last_notified_cloud_role"

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
     * تفحص الرتبة السحابية الحالية وتقارنها بآخر رتبة مُعلَن عنها.
     * إذا تحول المستخدم إلى مطور أو تم ترقيته سحابياً، يتم إرسال إشعار تهنئة فوري.
     */
    fun checkAndNotifyPromotion(context: Context, newCloudRole: String?) {
        if (newCloudRole.isNullOrBlank()) return
        val normalizedRole = newCloudRole.trim().lowercase(java.util.Locale.ROOT)
        val isDev = normalizedRole in setOf("developer", "admin", "owner", "مطور", "مكور")
        if (!isDev) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastNotifiedRole = prefs.getString(KEY_LAST_NOTIFIED_ROLE, null)

        // إذا كانت الرتبة السابقة غير مطور، أو رتبة جديدة تماماً لم يُخطر بها بعد
        if (lastNotifiedRole == null || !lastNotifiedRole.equals(normalizedRole, ignoreCase = true)) {
            Log.i(TAG, "🎉 User role promoted to $normalizedRole! Sending celebration notification.")
            prefs.edit().putString(KEY_LAST_NOTIFIED_ROLE, normalizedRole).apply()

            val roleDisplayName = when (normalizedRole) {
                "owner" -> "مالك المنظومة"
                "admin" -> "مشرف إداري"
                "developer", "مطور", "مكور" -> "مطور معتمد (Developer)"
                else -> normalizedRole
            }

            val title = "🎉 مبارك! تم تفعيل رتبة المطور لحسابك سحابياً"
            val message = "تهانينا! تم التحقق من هويتك كـ $roleDisplayName في سحابة قبس. تم فتح لوحة التحكم السحابية وأدوات التطوير المتقدمة بنجاح."

            // 1. إرسال إشعار نظام Android
            try {
                NotificationHelper.showNotification(
                    context = context,
                    title = title,
                    message = message
                )
            } catch (e: Throwable) {
                Log.w(TAG, "⚠️ Failed to post system notification: ${e.message}")
            }

            // 2. تفعيل الحدث الاحتفالي للواجهة
            val userEmail = SupabaseServices.Auth.getCurrentUserEmail() ?: SupabaseServices.Auth.currentUser.value?.email
            _celebrationEvent.value = CelebrationInfo(
                roleTitle = roleDisplayName,
                email = userEmail,
                message = message
            )
        }
    }
}
