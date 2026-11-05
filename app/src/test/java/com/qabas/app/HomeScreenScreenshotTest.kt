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
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures the real HomeScreen composable in the valid empty-recent-projects state.
 * This avoids MainActivity's live auth-navigation bootstrap, which requires Supabase
 * session settings unavailable to Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun captureHomeScreenEmptyRecentProjects() {
        val context = RuntimeEnvironment.getApplication<android.app.Application>()
        AppServices.appContext = context
        HomeScreenStyleManager.setStyle(context, HomeScreenStyle.CLASSIC_STUDIO)

        composeRule.setContent {
            MyApplicationTheme {
                HomeScreen(
                    recentProjects = emptyList(),
                    onNewProject = { initialText ->
                        AppNavigator.navigateToInputWithText(initialText.orEmpty())
                    },
                    onViewProjects = { AppNavigator.navigateTo(AppState.PROJECTS) },
                    onViewSettings = { AppNavigator.navigateTo(AppState.SETTINGS) },
                    onOpenProject = { project ->
                        val destination = runCatching {
                            AppState.valueOf(project.lastScreen)
                        }.getOrDefault(AppState.INPUT)
                        AppNavigator.navigateTo(destination)
                    },
                    bottomBar = {
                        QabasBottomNavigation(
                            currentRoute = AppState.HOME,
                            onNavigate = AppNavigator::navigateTo
                        )
                    }
                )
            }
        }

        composeRule.onNodeWithText("قَبَس").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage()
    }
}
