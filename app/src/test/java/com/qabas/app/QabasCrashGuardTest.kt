package com.qabas.app

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QabasCrashGuardTest {

    @Test
    fun `watchdog ANR remains probable until Android confirms the exit reason`() {
        val diagnostic = QabasCrashGuard.ANRDiagnosticException(
            freezeDurationMs = 4_500L,
            mainThreadState = "BLOCKED",
            deadlockSummary = "monitor wait observed",
            concurrentOpsSummary = "one runnable worker",
            mainThreadStack = emptyArray(),
            allThreadsDump = "test thread dump"
        )

        assertEquals(
            QabasCrashGuard.ConfidenceLevel.PROBABLE,
            QabasCrashGuard.confidenceFor(diagnostic, diagnostic, isAppCode = true)
        )
    }

    @Test
    fun `recognized app null pointer retains confirmed classification`() {
        val failure = NullPointerException("test")

        assertEquals(
            QabasCrashGuard.ConfidenceLevel.CONFIRMED,
            QabasCrashGuard.confidenceFor(failure, failure, isAppCode = true)
        )
    }

    @Test
    fun `external failure without app frame remains undetermined`() {
        val failure = IllegalArgumentException("external")

        assertEquals(
            QabasCrashGuard.ConfidenceLevel.UNDETERMINED,
            QabasCrashGuard.confidenceFor(failure, failure, isAppCode = false)
        )
    }
}
