package com.qabas.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.qabas.app.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * اختبارات جودة قسم الوكيل الذكي (Agent Quality Tests):
 * validateTeam، cleanDisplayName، resolveKeyWithSource، AgentPrefs، وتنقل تبويبات شاشة الوكيل.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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

    @Test
    fun `extractTargetFile does not guess a source file when no path is provided`() {
        val target = extractTargetFile("أضف ميزة جديدة للمشروع", "fun newFeature() = Unit")
        assertEquals("AgentProposal.kt", target)
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
    // Compose UI — اختبارات واجهة الوكيل بعد إعادة تصميم شاشة الوكيل في PR #107
    // ═══════════════════════════════════════════════════════════════════

    // ملاحظة: حُذفت 4 اختبارات لم يعد لها مقابل في الإنتاج بعد أن أصبحت
    // AiStudioAgentScreen شاشة محادثة بلا أقسام Hub:
    //  - اختبار deck/sub-tabs (deck_module_01..06 وsub_tab_readiness وsub_tab_tools).
    //  - اختبار نظرة Hub (agent_hub_overview).
    //  - اختبار مساحة العمل (agent_hub_page_02).
    //  - اختبار الأدوات (agent_hub_page_06).
    // وبقي قسم المشاريع في AgentHubProjectsSection (يُعرض من VibeCodingScreen)،
    // وقسما الكونسول والسجل في AgentLiveConsoleTab وTaskHistoryLogTab،
    // فأُعيد توجيه اختباراتها إلى تلك المكوّنات مباشرة بدل شاشة الوكيل.
    @Test
    fun `projects section renders the saved repositories list`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                AgentHubProjectsSection(
                    repositories = emptyList(),
                    activeRepositoryId = null,
                    onSelectRepository = {},
                    onAddRepository = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("agent_hub_projects").assertExists()
        composeTestRule.onNodeWithText("لا توجد مستودعات محفوظة").assertExists()
    }

    @Test
    fun `console section uses the shared hub page header`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                AgentHubPageIntro(
                    index = "03",
                    title = "كونسول التنفيذ",
                    description = "مراقبة الاتصال والطابور والخطوات والنتائج العملية",
                    icon = Icons.Default.Terminal
                )
            }
        }
        composeTestRule.onNodeWithTag("agent_hub_page_03").assertExists()
        composeTestRule.onNodeWithText("كونسول التنفيذ").assertExists()
    }

    @Test
    fun `history section shows recorded runs`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        AgentRunLog.clear(context)
        composeTestRule.setContent {
            MyApplicationTheme {
                TaskHistoryLogTab(context)
            }
        }
        composeTestRule.onNodeWithTag("agent_hub_page_05").assertExists()
        composeTestRule.onNodeWithText("إجمالي العمليات").assertExists()
        composeTestRule.onNodeWithText("0 عملية").assertExists()
        composeTestRule.onNodeWithTag("agent_history_success_rate").assertTextEquals("—")
    }

    @Test
    fun `empty history does not claim active runs or a perfect success rate`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        AgentRunLog.clear(context)
        composeTestRule.setContent {
            MyApplicationTheme {
                TaskHistoryLogTab(context)
            }
        }
        composeTestRule.onNodeWithText("إجمالي العمليات").assertExists()
        composeTestRule.onNodeWithText("0 عملية").assertExists()
        composeTestRule.onNodeWithTag("agent_history_success_rate").assertTextEquals("—")
        assertTrue(composeTestRule.onAllNodesWithText("قيد التنفيذ").fetchSemanticsNodes().isEmpty())
    }
}
