package com.qabas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectSyncRevisionTest {
    @Test
    fun localRevisionAdvancesWhenClockAndIncomingTimestampDoNotChange() {
        assertEquals(
            501L,
            nextProjectRevision(incomingUpdatedAt = 500L, existingUpdatedAt = 500L, now = 500L)
        )
    }

    @Test
    fun localRevisionDoesNotMoveBackWhenDeviceClockIsBehind() {
        assertEquals(
            9_001L,
            nextProjectRevision(incomingUpdatedAt = 1_000L, existingUpdatedAt = 9_000L, now = 500L)
        )
    }

    @Test
    fun rejectedCloudWriteNeverMarksProjectSynchronized() {
        assertFalse(shouldMarkProjectSynced(cloudAccepted = false, uploadedRevision = 10L, latestRevision = 10L))
    }

    @Test
    fun olderUploadCannotMarkNewerLocalEditSynchronized() {
        assertFalse(shouldMarkProjectSynced(cloudAccepted = true, uploadedRevision = 10L, latestRevision = 11L))
    }

    @Test
    fun acceptedCurrentRevisionCanBeMarkedSynchronized() {
        assertTrue(shouldMarkProjectSynced(cloudAccepted = true, uploadedRevision = 11L, latestRevision = 11L))
    }
}
