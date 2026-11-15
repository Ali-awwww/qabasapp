package com.qabas.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DeveloperDashboardFormattersTest {

    @Test
    fun latencyFormatterDoesNotInventValues() {
        assertEquals("--", DevDashboardFormatters.formatLatency(0))
        assertEquals("250ms", DevDashboardFormatters.formatLatency(250))
        assertEquals("1.5s", DevDashboardFormatters.formatLatency(1500))
    }

    @Test
    fun registrationDateFormatterHandlesMissingAndEpochValues() {
        assertEquals("غير معروف", DevDashboardFormatters.formatRegDate(null))
        assertEquals("غير معروف", DevDashboardFormatters.formatRegDate("not-a-date"))
        assertEquals("1970/01/01", DevDashboardFormatters.formatRegDate(0L + 1L))
    }
}
