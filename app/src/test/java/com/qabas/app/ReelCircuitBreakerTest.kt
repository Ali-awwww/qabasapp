package com.qabas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * اختبارات انحدار لقاطع دائرة الريلز (ReelSafetyGuardrails).
 *
 * القاطع يغلق نداءات Supabase للريلز 25 ثانية بعد 3 إخفاقات شبكية قابلة لإعادة
 * المحاولة. هذه الاختبارات تثبّت: الإغلاق بعد 3 إخفاقات، وعدم إغلاقه أبداً على
 * الأخطاء غير القابلة لإعادة المحاولة (401/تسلسل...)، وتصفير العدّاد عند النجاح،
 * وإعادة فتحه بإعادة الضبط اليدوية، وأن فشلاً واحداً بعد انتهاء التهدئة لا يعيد
 * إغلاقه (القفل الدائم الذي كان يجعل الريلز تعرض 429 إلى الأبد).
 *
 * يستخدم Robolectric لأن الحارس يستدعي android.util.Log.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReelCircuitBreakerTest {

    @Before
    fun setUp() {
        ReelSafetyGuardrails.forceResetCircuitBreaker()
    }

    @Test
    fun threeRetryableFailuresTripTheBreaker() {
        assertTrue(ReelSafetyGuardrails.isSupabaseCallsAllowed())

        repeat(2) { ReelSafetyGuardrails.recordNetworkFailure() }
        assertTrue(ReelSafetyGuardrails.isSupabaseCallsAllowed())

        ReelSafetyGuardrails.recordNetworkFailure()
        assertFalse(ReelSafetyGuardrails.isSupabaseCallsAllowed())
        assertTrue(ReelSafetyGuardrails.getCircuitRemainingCooldownSeconds() > 0)
    }

    @Test
    fun nonRetryableFailuresNeverTripTheBreaker() {
        repeat(10) { ReelSafetyGuardrails.recordNetworkFailure(isRetryable = false) }

        assertTrue(ReelSafetyGuardrails.isSupabaseCallsAllowed())
        assertEquals(0, ReelSafetyGuardrails.getCircuitRemainingCooldownSeconds())
    }

    @Test
    fun successResetsFailureCount() {
        repeat(2) { ReelSafetyGuardrails.recordNetworkFailure() }
        ReelSafetyGuardrails.recordNetworkSuccess()

        repeat(2) { ReelSafetyGuardrails.recordNetworkFailure() }
        assertTrue(ReelSafetyGuardrails.isSupabaseCallsAllowed())
    }

    @Test
    fun forceResetReopensTheBreaker() {
        repeat(3) { ReelSafetyGuardrails.recordNetworkFailure() }
        assertFalse(ReelSafetyGuardrails.isSupabaseCallsAllowed())

        ReelSafetyGuardrails.forceResetCircuitBreaker()
        assertTrue(ReelSafetyGuardrails.isSupabaseCallsAllowed())
        assertEquals(0, ReelSafetyGuardrails.getCircuitRemainingCooldownSeconds())
    }

    @Test
    fun singleFailureAfterCooldownExpiryDoesNotRetripTheBreaker() {
        repeat(3) { ReelSafetyGuardrails.recordNetworkFailure() }
        assertFalse(ReelSafetyGuardrails.isSupabaseCallsAllowed())

        // انتهاء التهدئة دون تصفير العدّاد (خطاف اختبار فقط).
        ReelSafetyGuardrails.expireCooldownForTest()
        assertTrue(ReelSafetyGuardrails.isSupabaseCallsAllowed())

        // فشل واحد يبدأ نافذة عد جديدة ولا يعيد الإغلاق.
        ReelSafetyGuardrails.recordNetworkFailure()
        assertTrue(ReelSafetyGuardrails.isSupabaseCallsAllowed())
    }
}
