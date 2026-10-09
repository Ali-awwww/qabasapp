package com.qabas.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.qabas.app.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LoginScreenRoboTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loginScreen_displaysElementsAndCapturesScreenshot() {
        composeTestRule.setContent {
            MyApplicationTheme {
                LoginScreen(
                    onLoginSuccess = {},
                    onNavigateToRegister = {},
                    onNavigateToForgotPassword = {}
                )
            }
        }

        // Verify key text nodes are present
        composeTestRule.onNodeWithText("بوابة الوصول الرقمية").assertIsDisplayed()

        // Capture snapshot with Roborazzi
        composeTestRule.onRoot().captureRoboImage()
    }
}
