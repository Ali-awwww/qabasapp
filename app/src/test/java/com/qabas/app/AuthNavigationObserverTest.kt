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
}
