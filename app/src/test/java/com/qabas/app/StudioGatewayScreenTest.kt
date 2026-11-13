package com.qabas.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.qabas.app.ui.theme.MyApplicationTheme
import org.junit.Assert.assertTrue
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
    fun departmentCardsRemainDiscoverableAndSwitchContent() {
        var higgsfieldRouteOpened = false
        composeTestRule.setContent {
            MyApplicationTheme {
                StudioGatewayScreen(
                    onBack = {},
                    onNavigateToHiggsfield = { higgsfieldRouteOpened = true },
                    onNavigateToMediaBrowser = {},
                    onNavigateToAiStudio = {},
                    onNavigateToProEditor = {},
                    onNavigateToMoneyPrinter = {},
                    onNavigateToPhotoStudio = {},
                    onNavigateToVoiceStudio = {},
                    onNavigateToYouTubeStudio = {},
                    onNavigateToTeleprompter = {},
                    onNavigateToStyleCloner = {},
                    onNavigateToHadithStudio = {},
                    onNavigateToStats = {},
                    onNavigateToAiStudioAgent = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("studio_department_video").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("studio_department_images").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("🎬 الفيديو", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("🎨 الصور والتصميم", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule
            .onNodeWithText("أداتان", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Higgsfield — فيديو من نص أو صورة").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Higgsfield — فيديو من نص أو صورة").performClick()
        assertTrue(higgsfieldRouteOpened)

        composeTestRule.onNodeWithTag("studio_department_images").performScrollTo().performClick()
        composeTestRule
            .onNodeWithText("الصور والتصميم")
            .performScrollTo()
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("studio_department_audio").performScrollTo().performClick()
        composeTestRule.onNodeWithText("VoiceStudio — توليد صوت محلي").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("studio_department_library").performScrollTo().performClick()
        composeTestRule.onNodeWithText("الصور والفيديو والصوت المحفوظ").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("studio_department_publishing").performScrollTo().performClick()
        composeTestRule.onNodeWithText("تجهيز محتوى يوتيوب").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("إحصاءات التواصل الاجتماعي").performScrollTo().assertIsDisplayed()
    }
}
