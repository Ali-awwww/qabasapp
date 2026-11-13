package com.qabas.app

import org.junit.Assert.assertEquals
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
}
