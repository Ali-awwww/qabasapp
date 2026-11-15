package com.qabas.app

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

/** تفضيلات محلية معزولة حسب الحساب، لمنع انتقال الهوية بين الحسابات على الجهاز نفسه. */
object AccountScopedPreferences {
    private const val PREFIX = "qabas_profile_"

    fun forEmail(context: Context, email: String): SharedPreferences {
        val normalized = email.trim().lowercase()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return context.applicationContext.getSharedPreferences(
            PREFIX + digest,
            Context.MODE_PRIVATE
        )
    }

    fun forCurrentAccount(context: Context): SharedPreferences? {
        val email = SupabaseServices.Auth.getCurrentUserEmail()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: context.applicationContext
                .getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
                .getString("user_email", null)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        return email?.let { forEmail(context, it) }
    }
}
