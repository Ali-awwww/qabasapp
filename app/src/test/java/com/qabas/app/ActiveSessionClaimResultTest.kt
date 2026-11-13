package com.qabas.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveSessionClaimResultTest {
    @Test
    fun mapsClaimedStatus() {
        assertEquals(
            ActiveSessionClaimResult.CLAIMED,
            parseActiveSessionClaimStatus("claimed")
        )
    }

    @Test
    fun mapsAnotherDeviceStatus() {
        assertEquals(
            ActiveSessionClaimResult.ACTIVE_ELSEWHERE,
            parseActiveSessionClaimStatus("active_elsewhere")
        )
    }

    @Test
    fun mapsMissingProfileStatus() {
        assertEquals(
            ActiveSessionClaimResult.PROFILE_MISSING,
            parseActiveSessionClaimStatus("profile_missing")
        )
    }

    @Test
    fun failsClosedForUnknownOrMissingStatus() {
        assertEquals(
            ActiveSessionClaimResult.UNAVAILABLE,
            parseActiveSessionClaimStatus("unexpected")
        )
        assertEquals(
            ActiveSessionClaimResult.UNAVAILABLE,
            parseActiveSessionClaimStatus(null)
        )
    }
}
