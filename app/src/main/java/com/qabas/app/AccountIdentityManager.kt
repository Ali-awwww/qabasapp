package com.qabas.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * مصدر الهوية الموحد داخل التطبيق.
 * Supabase هو المصدر النهائي للرتبة والملف السحابي، بينما تُستخدم
 * qabas_prefs كذاكرة محلية احتياطية لتفادي وميض الواجهة عند بدء التشغيل.
 */
data class AccountIdentity(
    val userId: String? = null,
    val email: String = "",
    val displayName: String = "المستخدم",
    val avatarUrl: String? = null,
    val role: String = "user",
    val accountNumber: String? = null
) {
    val isDeveloper: Boolean
        get() = SupabaseServices.Auth.isDeveloperRole(role)
}

object AccountIdentityManager {
    private val _identity = MutableStateFlow(AccountIdentity())
    val identity: StateFlow<AccountIdentity> = _identity.asStateFlow()

    /** يقرأ الكاش فورًا ثم يجلب النسخة الموثوقة من الجلسة والملف السحابي. */
    suspend fun refresh(context: Context): AccountIdentity = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val authUser = SupabaseServices.Auth.currentUser.value
        val userId = SupabaseServices.Auth.getCurrentUserId()
        val email = authUser?.email?.trim()?.takeIf { it.isNotBlank() }
            ?: prefs.getString("user_email", "").orEmpty()
        val localName = prefs.getString("user_name", null)?.takeIf { it.isNotBlank() }
            ?: prefs.getString("user_display_name", null)?.takeIf { it.isNotBlank() }
            ?: email.substringBefore("@").ifBlank { "المستخدم" }
        val localAvatar = prefs.getString("user_avatar_uri", null)?.takeIf { it.isNotBlank() }

        val trustedRole = if (!userId.isNullOrBlank()) {
            SupabaseServices.Auth.getUserRoleFromMetadata()
        } else {
            "user"
        }
        val profile = userId?.let { SupabaseServices.Database.getUserProfile(it) }
        val displayName = profile?.fullName?.trim()?.takeIf { it.isNotBlank() } ?: localName
        val avatarUrl = profile?.avatarUrl?.trim()?.takeIf { it.isNotBlank() } ?: localAvatar
        val cleanEmail = email.trim().lowercase().takeIf { it.isNotBlank() }.orEmpty()
        val accountNumber = cleanEmail.takeIf { it.isNotBlank() }?.let { clean ->
            val cloudNumber = if (userId != null && SupabaseServices.isSupabaseAvailable) {
                UserIdentifierManager.ensureCloudAccountNumber(appContext, clean, displayName)
            } else {
                ""
            }
            cloudNumber.ifBlank {
                UserIdentifierManager.getAccountNumber(appContext, clean).orEmpty()
            }
        }

        val result = AccountIdentity(
            userId = userId,
            email = cleanEmail,
            displayName = displayName,
            avatarUrl = avatarUrl,
            role = trustedRole,
            accountNumber = accountNumber
        )
        prefs.edit()
            .putString("user_email", result.email)
            .putString("user_name", result.displayName)
            .apply()
        if (!result.avatarUrl.isNullOrBlank() && !result.avatarUrl.startsWith("content://")) {
            prefs.edit().putString("user_avatar_uri", result.avatarUrl).apply()
        }
        _identity.value = result
        result
    }

    fun cached(context: Context): AccountIdentity {
        val prefs = context.applicationContext.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val email = prefs.getString("user_email", "").orEmpty()
        return AccountIdentity(
            email = email,
            displayName = prefs.getString("user_name", null)?.takeIf { it.isNotBlank() }
                ?: prefs.getString("user_display_name", null)?.takeIf { it.isNotBlank() }
                ?: email.substringBefore("@").ifBlank { "المستخدم" },
            avatarUrl = prefs.getString("user_avatar_uri", null)?.takeIf { it.isNotBlank() }
        )
    }
}
