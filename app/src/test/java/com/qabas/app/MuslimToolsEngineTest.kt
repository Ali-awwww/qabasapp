package com.qabas.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MuslimToolsEngineTest {

    private lateinit var engine: MuslimToolsEngine

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("muslim_tools_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        engine = MuslimToolsEngine(context)
    }

    @Test
    fun `zakat uses supplied market prices and gold purity`() {
        val result = engine.calculateZakat(
            cash = 8_500.0,
            goldGrams = 10.0,
            goldCarat = 21,
            silverGrams = 50.0,
            tradeGoods = 1_000.0,
            goldPricePerGram24k = 100.0,
            silverPricePerGram = 2.0
        )

        assertEquals(10_475.0, result.totalWealth, 0.001)
        assertEquals(8_500.0, result.nisabValueGold, 0.001)
        assertTrue(result.isNisabReached)
        assertEquals(261.875, result.zakatAmount, 0.001)
    }

    @Test
    fun `gold nisab threshold is calculated from the supplied price`() {
        val result = engine.calculateZakat(
            cash = 8_500.0,
            goldGrams = 0.0,
            goldCarat = 24,
            silverGrams = 0.0,
            tradeGoods = 0.0,
            goldPricePerGram24k = 100.0,
            silverPricePerGram = 0.0
        )

        assertTrue(result.isNisabReached)
        assertEquals(212.5, result.zakatAmount, 0.001)
    }

    @Test
    fun `zakat remains zero below the gold nisab`() {
        val result = engine.calculateZakat(
            cash = 8_499.0,
            goldGrams = 0.0,
            goldCarat = 24,
            silverGrams = 0.0,
            tradeGoods = 0.0,
            goldPricePerGram24k = 100.0,
            silverPricePerGram = 0.0
        )

        assertFalse(result.isNisabReached)
        assertEquals(0.0, result.zakatAmount, 0.001)
    }

    @Test
    fun `silver holdings require a positive supplied silver price`() {
        assertIllegalArgument {
            engine.calculateZakat(
                cash = 0.0,
                goldGrams = 0.0,
                goldCarat = 24,
                silverGrams = 1.0,
                tradeGoods = 0.0,
                goldPricePerGram24k = 100.0,
                silverPricePerGram = 0.0
            )
        }
    }

    @Test
    fun `zakat rejects negative asset values and invalid gold prices`() {
        assertIllegalArgument {
            engine.calculateZakat(
                cash = -1.0,
                goldGrams = 0.0,
                goldCarat = 24,
                silverGrams = 0.0,
                tradeGoods = 0.0,
                goldPricePerGram24k = 100.0,
                silverPricePerGram = 0.0
            )
        }
        assertIllegalArgument {
            engine.calculateZakat(
                cash = 0.0,
                goldGrams = 0.0,
                goldCarat = 24,
                silverGrams = 0.0,
                tradeGoods = 0.0,
                goldPricePerGram24k = 0.0,
                silverPricePerGram = 0.0
            )
        }
    }

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected validation guard.
        }
    }
}
