package com.qabas.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.qabas.app.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StudioGatewayScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun categoryTabsShowReadableLabelsAndSwitchContent() {
        composeTestRule.setContent {
            MyApplicationTheme {
                StudioGatewayScreen(
                    onBack = {},
                    onNavigateToAiStudio = {},
                    onNavigateToProEditor = {},
                    onNavigateToStats = {}
                )
            }
        }

        composeTestRule.onNodeWithText("🎬 الفيديوهات").assertIsDisplayed()
        composeTestRule.onNodeWithText("🎨 الصور").assertIsDisplayed()
        composeTestRule.onNodeWithText("🎙️ الصوت").assertIsDisplayed()

        composeTestRule.onNodeWithText("🎨 الصور").performClick()
        composeTestRule
            .onNodeWithText("استوديو الصور والتصاميم (Leonardo.ai)")
            .assertIsDisplayed()
    }
}
