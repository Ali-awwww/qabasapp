package com.qabas.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.qabas.app.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HealthCenterNavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun diagnosticsBackButtonInvokesNavigationCallback() {
        var backPressed = false

        composeTestRule.setContent {
            MyApplicationTheme {
                DiagnosticsDashboardSection(onBack = { backPressed = true })
            }
        }

        composeTestRule.onNodeWithContentDescription("رجوع").performClick()

        assertTrue(backPressed)
    }

    @Test
    fun selectingDoctorTabUpdatesSelectionAndPersistsIt() {
        val previousTab = HealthCenterState.lastTab
        var selectedTab = HealthTab.DIAGNOSTICS.ordinal

        try {
            selectHealthTab(HealthTab.DOCTOR) { selectedTab = it }

            assertEquals(HealthTab.DOCTOR.ordinal, selectedTab)
            assertEquals(HealthTab.DOCTOR.ordinal, HealthCenterState.lastTab)
        } finally {
            HealthCenterState.lastTab = previousTab
        }
    }
}
