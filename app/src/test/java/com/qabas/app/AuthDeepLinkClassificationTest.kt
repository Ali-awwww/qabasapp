package com.qabas.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ق85: رابط qabas://auth كان يُصنَّف استعادةً دائماً — فرابط
 * تأكيد البريد كان يهبط على شاشة «نسيت كلمة المرور». التصنيف
 * الآن بنوع الرابط الصريح أو بنية استعادة مسجلة عند الطلب.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AuthDeepLinkClassificationTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    // ── تصنيف الرابط (دالة خالصة) ──

    @Test
    fun `pkce code with a recent recovery request is recovery`() {
        assertTrue(classifyQabasAuthLink("qabas://auth?code=abc123", true))
    }

    @Test
    fun `pkce code without a recovery request is email confirmation`() {
        assertFalse(classifyQabasAuthLink("qabas://auth?code=abc123", false))
    }

    @Test
    fun `explicit signup type wins over a recovery request`() {
        assertFalse(
            classifyQabasAuthLink(
                "qabas://auth#access_token=tok&token_type=bearer&type=signup",
                true
            )
        )
    }

    @Test
    fun `explicit recovery type wins without a recovery request`() {
        assertTrue(
            classifyQabasAuthLink(
                "qabas://auth#access_token=tok&token_type=bearer&type=recovery",
                false
            )
        )
    }

    @Test
    fun `explicit recovery type in query wins`() {
        assertTrue(classifyQabasAuthLink("qabas://auth?code=abc&type=recovery", false))
    }

    @Test
    fun `error link without a recovery request is confirmation`() {
        assertFalse(
            classifyQabasAuthLink("qabas://auth?error=access_denied&error_description=x", false)
        )
    }

    // ── علم نية الاستعادة ──

    @Test
    fun `recovery request flag is set and consumed`() {
        assertFalse(PasswordRecoveryManager.isRecoveryRequested(context))
        PasswordRecoveryManager.markRequested(context)
        assertTrue(PasswordRecoveryManager.isRecoveryRequested(context))
        PasswordRecoveryManager.clearRequested(context)
        assertFalse(PasswordRecoveryManager.isRecoveryRequested(context))
    }

    @Test
    fun `stale recovery request older than 24h is not a request`() {
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .edit()
            .putLong(
                "supabase_password_recovery_requested_at",
                System.currentTimeMillis() - 25L * 60 * 60 * 1000
            )
            .commit()
        assertFalse(PasswordRecoveryManager.isRecoveryRequested(context))
    }
}
