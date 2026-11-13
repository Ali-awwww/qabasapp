package com.qabas.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performClick
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
    fun categoryTabsShowReadableLabelsAndSwitchContent() {
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

        composeTestRule.onNodeWithText("🎬 الفيديو").assertIsDisplayed()
        composeTestRule.onNodeWithText("🎨 الصور والتصميم").assertIsDisplayed()
        composeTestRule.onNodeWithText("Higgsfield — فيديو من نص أو صورة").assertIsDisplayed()
        composeTestRule.onNodeWithText("Higgsfield — فيديو من نص أو صورة").performClick()
        assertTrue(higgsfieldRouteOpened)

        composeTestRule.onNodeWithText("🎨 الصور والتصميم").performClick()
        composeTestRule
            .onNodeWithText("الصور والتصميم")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("studio_category_row").performScrollToIndex(2)
        composeTestRule.onNodeWithText("🎙️ الصوت").performClick()
        composeTestRule.onNodeWithText("VoiceStudio — توليد صوت محلي").assertIsDisplayed()

        composeTestRule.onNodeWithTag("studio_category_row").performScrollToIndex(3)
        composeTestRule.onNodeWithText("🗂️ المكتبة").performClick()
        composeTestRule.onNodeWithText("الصور والفيديو والصوت المحفوظ").assertIsDisplayed()

        composeTestRule.onNodeWithTag("studio_category_row").performScrollToIndex(4)
        composeTestRule.onNodeWithText("📣 النشر والتقارير").performClick()
        composeTestRule.onNodeWithText("تجهيز محتوى يوتيوب").assertIsDisplayed()
        composeTestRule.onNodeWithText("إحصاءات التواصل الاجتماعي").assertIsDisplayed()
    }
}
