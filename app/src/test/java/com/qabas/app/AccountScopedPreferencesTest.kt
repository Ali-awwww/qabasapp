package com.qabas.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccountScopedPreferencesTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun profileValuesAreIsolatedBetweenAccounts() {
        val firstAccount = AccountScopedPreferences.forEmail(context, "first@example.com")
        val secondAccount = AccountScopedPreferences.forEmail(context, "second@example.com")

        firstAccount.edit().putString("user_avatar_uri", "https://cdn.example/first.jpg").commit()
        secondAccount.edit().remove("user_avatar_uri").commit()

        assertTrue(firstAccount.getString("user_avatar_uri", null)?.contains("first") == true)
        assertFalse(secondAccount.contains("user_avatar_uri"))
    }

    @Test
    fun emailNormalizationUsesTheSameAccountStore() {
        val lower = AccountScopedPreferences.forEmail(context, "owner@example.com")
        val mixedCase = AccountScopedPreferences.forEmail(context, " Owner@Example.COM ")

        lower.edit().putString("user_name", "صاحب الحساب").commit()

        assertTrue(mixedCase.getString("user_name", null) == "صاحب الحساب")
    }
}
