package com.qabas.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.qabas.app.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * اختبارات جودة قسم الوكيل الذكي (Agent Quality Tests):
 * validateTeam، cleanDisplayName، resolveKeyWithSource، AgentPrefs، وتنقل تبويبات شاشة الوكيل.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AgentQualityTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        AppServices.appContext = context
        AgentPrefs.init(context)
    }

    // ═══════════════════════════════════════════════════════════════════
    // validateTeam — دستور الفريق
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `validateTeam rejects team with no leader`() {
        val team = listOf(
            AgentTeamMember("e1", "مساعد", DetectedProvider.GEMINI, "gemini-3.5-flash", AgentTeamRole.EXECUTOR, 1, true, "")
        )
        val result = MultiModelAgentOrchestrator.validateTeam(team)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("قائد") })
    }

    @Test
    fun `validateTeam rejects team with no helper`() {
        val team = listOf(
            AgentTeamMember("l1", "القائد", DetectedProvider.OPENROUTER, "anthropic/claude-sonnet-4.5", AgentTeamRole.LEADER, 1, true, "")
        )
        val result = MultiModelAgentOrchestrator.validateTeam(team)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("مساعد") })
    }

    @Test
    fun `validateTeam accepts valid team`() {
        val team = listOf(
            AgentTeamMember("l1", "القائد", DetectedProvider.OPENROUTER, "anthropic/claude-sonnet-4.5", AgentTeamRole.LEADER, 1, true, "مخطط صارم"),
            AgentTeamMember("e1", "مساعد", DetectedProvider.GEMINI, "gemini-3.5-flash", AgentTeamRole.EXECUTOR, 2, true, "متخصص واجهات")
        )
        val result = MultiModelAgentOrchestrator.validateTeam(team)
        assertTrue(result.isValid)
    }

    // ═══════════════════════════════════════════════════════════════════
    // cleanDisplayName — تنظيف اسم العرض
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `cleanDisplayName strips rule markers`() {
        val name = MultiModelAgentOrchestrator.cleanDisplayName("القائد • يلزم مفتاح API • اتصال", AgentTeamRole.LEADER)
        assertEquals("القائد", name)
    }

    @Test
    fun `cleanDisplayName falls back to role when blank`() {
        val name = MultiModelAgentOrchestrator.cleanDisplayName("يلزم تنزيل • API", AgentTeamRole.REVIEWER)
        assertEquals("مراجع", name)
    }

    // ═══════════════════════════════════════════════════════════════════
    // AgentPrefs.maskSecret — إظهار آخر 4 محارف فقط
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `maskSecret shows last four characters only`() {
        assertEquals("••••abcd", AgentPrefs.maskSecret("sk-or-1234abcd"))
        assertEquals("••••", AgentPrefs.maskSecret("ab"))
        assertEquals("••••wxyz", AgentPrefs.maskSecret("1234wxyz"))
    }

    // ═══════════════════════════════════════════════════════════════════
    // resolveKeyWithSource — لا مفتاح مخزن نصاً صريحاً بعد الهجرة
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `resolveKeyWithSource returns NONE when no keys are stored`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val result = AgentConnectivityEngine.resolveKeyWithSource(context)
        assertNotNull(result)
        assertEquals(DetectedProvider.NONE, result.first)
        assertEquals("", result.second)
        assertEquals(KeySource.NONE, result.third)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Compose UI — تنقل تبويبات شاشة الوكيل (testTags)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `agent screen renders deck modules and sub-tabs`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                AiStudioAgentScreen(onBack = {}, initialTab = 5)
            }
        }

        composeTestRule.onNodeWithTag("deck_module_01").assertExists()
        composeTestRule.onNodeWithTag("deck_module_02").assertExists()
        composeTestRule.onNodeWithTag("deck_module_03").assertExists()
        composeTestRule.onNodeWithTag("deck_module_04").assertExists()
        composeTestRule.onNodeWithTag("deck_module_05").assertExists()
        composeTestRule.onNodeWithTag("deck_module_06").assertExists()
        composeTestRule.onNodeWithTag("sub_tab_readiness").assertExists()
        composeTestRule.onNodeWithTag("sub_tab_tools").assertExists()
        composeTestRule.onNodeWithTag("sub_tab_readiness").performClick()
        composeTestRule.onNodeWithTag("sub_tab_tools").performClick()
    }

    @Test
    fun `agent screen opens on developer hub overview`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                AiStudioAgentScreen(onBack = {}, initialTab = 0)
            }
        }
        composeTestRule.onNodeWithTag("agent_hub_overview").assertExists()
    }
}
