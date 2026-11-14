package com.qabas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthNavigationObserverTest {

    @Test
    fun guestStateDoesNotTriggerUnauthenticatedRedirect() {
        var loginRedirects = 0
        var guestEvents = 0

        dispatchAuthNavigationState(
            state = AuthState.Guest("ضيف قبس"),
            onAuthenticated = {},
            onUnauthenticated = { loginRedirects++ },
            onGuest = { guestEvents++ }
        )

        assertEquals("Guest browsing must not be treated as a failed authentication session", 0, loginRedirects)
        assertEquals("Guest state should be handled as its own navigation case", 1, guestEvents)
    }

    @Test
    fun unauthenticatedStateStillTriggersLoginRedirect() {
        var loginRedirects = 0
        var guestEvents = 0

        dispatchAuthNavigationState(
            state = AuthState.Unauthenticated,
            onAuthenticated = {},
            onUnauthenticated = { loginRedirects++ },
            onGuest = { guestEvents++ }
        )

        assertEquals(1, loginRedirects)
        assertEquals(0, guestEvents)
    }

    @Test
    fun loadingStateDoesNotTriggerNavigation() {
        var loginRedirects = 0
        var guestEvents = 0

        dispatchAuthNavigationState(
            state = AuthState.Loading,
            onAuthenticated = {},
            onUnauthenticated = { loginRedirects++ },
            onGuest = { guestEvents++ }
        )

        assertEquals(0, loginRedirects)
        assertEquals(0, guestEvents)
    }

    @Test
    fun authErrorDoesNotPretendTheSessionWasLost() {
        var loginRedirects = 0

        dispatchAuthNavigationState(
            state = AuthState.Error("Temporary profile load failure"),
            onAuthenticated = {},
            onUnauthenticated = { loginRedirects++ },
            onGuest = {}
        )

        assertEquals("A recoverable auth/profile error must not force a logout navigation", 0, loginRedirects)
    }

    @Test
    fun loadingAndErrorAreNotTreatedAsGuestLoginPrompts() {
        assertFalse(AuthState.Loading.requiresInteractiveLogin())
        assertFalse(AuthState.Error("Temporary profile failure").requiresInteractiveLogin())
        assertTrue(AuthState.Guest().requiresInteractiveLogin())
        assertTrue(AuthState.Unauthenticated.requiresInteractiveLogin())
    }

    @Test
    fun authenticatedSnapshotCarriesUidAndTrustedRoleTogether() {
        val snapshot = AuthState.Authenticated(
            userRow = SupabaseServices.UserRow(
                id = "test-user",
                email = "test@example.invalid",
                name = "Test User",
                customId = "test-user",
                accountNumber = "QBS-1001"
            ),
            isAdmin = true,
            role = "developer"
        ).authenticatedSessionSnapshot()

        assertEquals("test-user", snapshot?.userId)
        assertEquals("test@example.invalid", snapshot?.email)
        assertEquals("developer", snapshot?.role)
        assertTrue(snapshot?.isAdmin == true)
    }

    @Test
    fun unverifiedStatesHaveNoAuthenticatedSnapshot() {
        assertNull(AuthState.Loading.authenticatedSessionSnapshot())
        assertNull(AuthState.Error("Temporary profile failure").authenticatedSessionSnapshot())
        assertNull(AuthState.Unauthenticated.authenticatedSessionSnapshot())
    }

    @Test
    fun authenticatedNavigationWaitsForInteractiveAuthFinalization() {
        var homeNavigations = 0
        val authenticatedState = AuthState.Authenticated(
            userRow = SupabaseServices.UserRow(
                id = "test-user",
                customId = "test-user",
                accountNumber = "QBS-1001"
            )
        )

        dispatchAuthNavigationState(
            state = authenticatedState,
            onAuthenticated = { homeNavigations++ },
            onUnauthenticated = {},
            onGuest = {},
            suppressAuthenticatedNavigation = true
        )
        assertEquals("Do not leave Login/Register while profile and active-session checks are pending", 0, homeNavigations)

        dispatchAuthNavigationState(
            state = authenticatedState,
            onAuthenticated = { homeNavigations++ },
            onUnauthenticated = {},
            onGuest = {},
            suppressAuthenticatedNavigation = false
        )
        assertEquals("Automatic session restoration remains available after the interactive flow ends", 1, homeNavigations)
    }

    @Test
    fun staleAuthResultCannotCommitAfterSessionOrStateRevisionChanges() {
        val captured = AuthSessionFingerprint("account-a", "test-token-a")

        assertTrue(
            canCommitAuthSessionResult(
                expected = captured,
                active = AuthSessionFingerprint("account-a", "test-token-a"),
                expectedStateRevision = 4L,
                activeStateRevision = 4L
            )
        )
        assertFalse(
            canCommitAuthSessionResult(
                expected = captured,
                active = AuthSessionFingerprint("account-b", "test-token-b"),
                expectedStateRevision = 4L,
                activeStateRevision = 4L
            )
        )
        assertFalse(
            canCommitAuthSessionResult(
                expected = captured,
                active = AuthSessionFingerprint("account-a", "rotated-token-a"),
                expectedStateRevision = 4L,
                activeStateRevision = 4L
            )
        )
        assertFalse(
            canCommitAuthSessionResult(
                expected = captured,
                active = AuthSessionFingerprint("account-a", "test-token-a"),
                expectedStateRevision = 4L,
                activeStateRevision = 5L
            )
        )
        assertFalse(
            canCommitAuthSessionResult(
                expected = captured,
                active = null,
                expectedStateRevision = 4L,
                activeStateRevision = 4L
            )
        )
    }
}
