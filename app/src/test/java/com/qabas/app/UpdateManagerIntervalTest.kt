package com.qabas.app

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The interval policy is pure Kotlin so these checks do not need Robolectric or Android TLS setup.
 */
class UpdateManagerIntervalTest {

    @Test
    fun `default interval is the two hour floor`() {
        assertEquals(2, AutoUpdateIntervalPolicy.DEFAULT_INTERVAL_HOURS)
        assertEquals(2, AutoUpdateIntervalPolicy.clampHours(AutoUpdateIntervalPolicy.DEFAULT_INTERVAL_HOURS))
        assertEquals(2L * 60L * 60L * 1000L, AutoUpdateIntervalPolicy.hoursToMillis(2))
    }

    @Test
    fun `values below the floor are clamped to two hours`() {
        assertEquals(2, AutoUpdateIntervalPolicy.clampHours(0))
        assertEquals(2, AutoUpdateIntervalPolicy.clampHours(-5))
        assertEquals(2, AutoUpdateIntervalPolicy.clampHours(1))
    }

    @Test
    fun `chosen interval converts to milliseconds`() {
        assertEquals(6, AutoUpdateIntervalPolicy.clampHours(6))
        assertEquals(6L * 60L * 60L * 1000L, AutoUpdateIntervalPolicy.hoursToMillis(6))
    }

    @Test
    fun `minimum interval constant remains two hours`() {
        assertEquals(2, AutoUpdateIntervalPolicy.MIN_INTERVAL_HOURS)
    }
}
