package com.qabas.app

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UserPreferencesManagerSyncTest {

    @Test
    fun overlappingSyncOperationsAreSerialized() = runTest {
        var activeOperations = 0
        var maximumConcurrentOperations = 0
        var completedOperations = 0

        coroutineScope {
            repeat(3) {
                launch {
                    UserPreferencesManager.withSyncLock {
                        activeOperations += 1
                        maximumConcurrentOperations = maxOf(
                            maximumConcurrentOperations,
                            activeOperations
                        )
                        delay(25)
                        activeOperations -= 1
                        completedOperations += 1
                    }
                }
            }
        }

        assertEquals(1, maximumConcurrentOperations)
        assertEquals(3, completedOperations)
    }

    @Test
    fun successfulSyncKeepsPendingWhenLocalPreferencesChangedDuringWrite() {
        assertTrue(
            UserPreferencesManager.shouldKeepPendingChanges(
                saveSucceeded = true,
                revisionAtSyncStart = 4L,
                currentRevision = 5L
            )
        )
    }

    @Test
    fun successfulSyncClearsPendingWhenNoNewLocalChangeOccurred() {
        assertFalse(
            UserPreferencesManager.shouldKeepPendingChanges(
                saveSucceeded = true,
                revisionAtSyncStart = 4L,
                currentRevision = 4L
            )
        )
    }

    @Test
    fun syncSuccessIsNotReportedWhileLatestChangesRemainPending() {
        assertFalse(
            UserPreferencesManager.shouldReportSyncSuccess(
                saveSucceeded = true,
                hasPendingChanges = true
            )
        )
    }

    @Test
    fun syncSuccessRequiresBackendSaveAndNoPendingChanges() {
        assertTrue(
            UserPreferencesManager.shouldReportSyncSuccess(
                saveSucceeded = true,
                hasPendingChanges = false
            )
        )
        assertFalse(
            UserPreferencesManager.shouldReportSyncSuccess(
                saveSucceeded = false,
                hasPendingChanges = false
            )
        )
    }

    @Test
    fun failedSyncAlwaysKeepsPending() {
        assertTrue(
            UserPreferencesManager.shouldKeepPendingChanges(
                saveSucceeded = false,
                revisionAtSyncStart = 4L,
                currentRevision = 4L
            )
        )
    }
}
