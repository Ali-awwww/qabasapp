package com.qabas.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * تتبع التكلفة والحدود (ق44):
 * - إجمالي العمر التراكمي لا يسقط بتقادم سجل الـ 300 قيد.
 * - حد التكلفة يقرأ إجمالي العمر لا السجل المقطوع.
 * - الأسعار الجديدة موثقة حياً بتاريخ 2026-11-23.
 * - الاستدعاءات خارج مسار الوكيل تُحصى «غير مقاسة» لا بلا أثر.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VibeCostTrackerTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        VibeCostTracker.reset(context)
        AgentPrefs.putCostLimitUsd(context, null)
    }

    private fun measuredResponse(promptTokens: Int = 1_000_000, completionTokens: Int = 0) =
        RealAgentResponse("x", promptTokens + completionTokens, promptTokens, completionTokens)

    @Test
    fun lifetimeTotalsSurviveThe300EntryCap() {
        // 305 استدعاءات مقاسة بـ 0.05 دولار لكل منها (llama-3.1-8b-instant:
        // مليون رمز إدخال × 0.05/مليون).
        repeat(305) {
            VibeCostTracker.record(context, "groq", "llama-3.1-8b-instant", measuredResponse(), null)
        }
        // السجل المعروض مقطوع عند 300، لكن إجمالي العمر كامل.
        assertEquals(300, VibeCostTracker.entries(context).size)
        val lifetime = VibeCostTracker.lifetimeTotals(context)
        assertEquals(305, lifetime.calls)
        assertEquals(305_000_000L, lifetime.tokens)
        assertEquals(15.25, lifetime.spentUsd, 1e-6)
        assertEquals(15.25, VibeCostTracker.spentUsd(context), 1e-6)
        val snapshot = VibeCostTracker.snapshot(context)
        assertEquals(300, snapshot.totalCalls)
        assertEquals(305, snapshot.lifetimeCalls)
        assertEquals(15.25, snapshot.lifetimeSpentUsd, 1e-6)
    }

    @Test
    fun checkLimitReadsLifetimeNotTruncatedHistory() {
        AgentPrefs.putCostLimitUsd(context, 0.10)
        assertNull(VibeCostTracker.checkLimit(context))
        VibeCostTracker.record(context, "groq", "llama-3.1-8b-instant", measuredResponse(), null)
        assertNull(VibeCostTracker.checkLimit(context))
        VibeCostTracker.record(context, "groq", "llama-3.1-8b-instant", measuredResponse(), null)
        // 0.10 بلغ الحد فيوقف.
        assertNotNull(VibeCostTracker.checkLimit(context))
    }

    @Test
    fun resetClearsEntriesAndLifetimeTogether() {
        VibeCostTracker.record(context, "groq", "llama-3.1-8b-instant", measuredResponse(), null)
        assertEquals(1, VibeCostTracker.lifetimeTotals(context).calls)
        VibeCostTracker.reset(context)
        assertEquals(0, VibeCostTracker.entries(context).size)
        assertEquals(0, VibeCostTracker.lifetimeTotals(context).calls)
        assertEquals(0.0, VibeCostTracker.spentUsd(context), 1e-9)
    }

    @Test
    fun estimatedCallsCountAsUnmeasured() {
        val entry = VibeCostTracker.recordEstimated(
            context, "gemini", "gemini-3.8-flash", "prompt", "response text", "دردشة المخرج"
        )
        assertNull(entry.costUsd)
        assertEquals("رموز تقديرية", entry.costUnknownReason)
        assertTrue(entry.totalTokens > 0)
        val lifetime = VibeCostTracker.lifetimeTotals(context)
        assertEquals(1, lifetime.calls)
        assertEquals(1, lifetime.unmeasuredCalls)
        assertEquals(0.0, lifetime.spentUsd, 1e-9)
    }

    @Test
    fun newPricesAreVerifiedValues() {
        assertEquals(0.59, VibeModelPrices.priceFor("llama-3.3-70b-versatile")?.inputUsdPerMillion ?: -1.0, 1e-9)
        assertEquals(0.79, VibeModelPrices.priceFor("llama-3.3-70b-versatile")?.outputUsdPerMillion ?: -1.0, 1e-9)
        assertEquals(0.05, VibeModelPrices.priceFor("llama-3.1-8b-instant")?.inputUsdPerMillion ?: -1.0, 1e-9)
        assertEquals(0.08, VibeModelPrices.priceFor("llama-3.1-8b-instant")?.outputUsdPerMillion ?: -1.0, 1e-9)
        assertEquals(0.15, VibeModelPrices.priceFor("openai/gpt-4o-mini")?.inputUsdPerMillion ?: -1.0, 1e-9)
        assertEquals(0.60, VibeModelPrices.priceFor("openai/gpt-4o-mini")?.outputUsdPerMillion ?: -1.0, 1e-9)
        assertEquals(0.71, VibeModelPrices.priceFor("meta-llama/llama-3.3-70b-instruct")?.inputUsdPerMillion ?: -1.0, 1e-9)
        assertEquals(0.71, VibeModelPrices.priceFor("meta-llama/llama-3.3-70b-instruct")?.outputUsdPerMillion ?: -1.0, 1e-9)
        assertEquals(0.10, VibeModelPrices.priceFor("gemini-2.0-flash")?.inputUsdPerMillion ?: -1.0, 1e-9)
        assertEquals(0.40, VibeModelPrices.priceFor("gemini-2.0-flash")?.outputUsdPerMillion ?: -1.0, 1e-9)
        // بلا سعر موثق يبقى null لا رقماً مخمّناً.
        assertNull(VibeModelPrices.priceFor("gemini-3.5-pro"))
        assertNull(VibeModelPrices.priceFor("mixtral-8x7b"))
        assertNull(VibeModelPrices.priceFor("unknown-model"))
    }
}
