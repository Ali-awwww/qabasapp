package com.qabas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات منطق حلقة التنفيذ والتحقق (AgentBuildVerifier) — دوال نقية بلا شبكة.
 */
class AgentBuildVerifierTest {

    @Test
    fun `classify maps queued and in_progress correctly`() {
        assertEquals(BuildRunOutcome.QUEUED, AgentBuildVerifier.classify("queued", null))
        assertEquals(BuildRunOutcome.QUEUED, AgentBuildVerifier.classify("requested", null))
        assertEquals(BuildRunOutcome.QUEUED, AgentBuildVerifier.classify("waiting", null))
        assertEquals(BuildRunOutcome.RUNNING, AgentBuildVerifier.classify("in_progress", null))
    }

    @Test
    fun `classify maps completed conclusions correctly`() {
        assertEquals(BuildRunOutcome.SUCCESS, AgentBuildVerifier.classify("completed", "success"))
        assertEquals(BuildRunOutcome.FAILURE, AgentBuildVerifier.classify("completed", "failure"))
        assertEquals(BuildRunOutcome.FAILURE, AgentBuildVerifier.classify("completed", "timed_out"))
        assertEquals(BuildRunOutcome.FAILURE, AgentBuildVerifier.classify("completed", "startup_failure"))
        assertEquals(BuildRunOutcome.CANCELLED, AgentBuildVerifier.classify("completed", "cancelled"))
        assertEquals(BuildRunOutcome.UNKNOWN, AgentBuildVerifier.classify("completed", null))
        assertEquals(BuildRunOutcome.UNKNOWN, AgentBuildVerifier.classify("completed", ""))
    }

    @Test
    fun `classify is case and whitespace tolerant`() {
        assertEquals(BuildRunOutcome.SUCCESS, AgentBuildVerifier.classify(" COMPLETED ", " Success "))
    }

    @Test
    fun `parseRuns extracts snapshots from workflow_runs json`() {
        val json = """
        {
          "total_count": 2,
          "workflow_runs": [
            {"id": 111, "name": "Build & Sign APK", "head_sha": "abc123", "status": "completed", "conclusion": "success", "html_url": "https://x/111"},
            {"id": 222, "name": "CodeQL", "head_sha": "def456", "status": "in_progress", "conclusion": "", "html_url": "https://x/222"}
          ]
        }
        """.trimIndent()
        val runs = AgentBuildVerifier.parseRuns(json)
        assertEquals(2, runs.size)
        assertEquals(111L, runs[0].id)
        assertEquals("abc123", runs[0].headSha)
        assertEquals(BuildRunOutcome.SUCCESS, runs[0].outcome)
        assertNull(runs[1].conclusion)
        assertEquals(BuildRunOutcome.RUNNING, runs[1].outcome)
    }

    @Test
    fun `parseRuns tolerates malformed or empty input`() {
        assertTrue(AgentBuildVerifier.parseRuns("").isEmpty())
        assertTrue(AgentBuildVerifier.parseRuns("not json").isEmpty())
        assertTrue(AgentBuildVerifier.parseRuns("{}").isEmpty())
        assertTrue(AgentBuildVerifier.parseRuns("""{"workflow_runs": []}""").isEmpty())
    }

    @Test
    fun `pickRunForCommit matches sha and falls back to first when blank`() {
        val runs = listOf(
            BuildRunSnapshot(1, "A", "sha-a", "completed", "success", ""),
            BuildRunSnapshot(2, "B", "sha-b", "completed", "failure", "")
        )
        assertEquals(2L, AgentBuildVerifier.pickRunForCommit(runs, "sha-b")?.id)
        assertEquals(1L, AgentBuildVerifier.pickRunForCommit(runs, "")?.id)
        assertNull(AgentBuildVerifier.pickRunForCommit(runs, "missing"))
    }

    @Test
    fun `isFinished only true for terminal outcomes`() {
        assertTrue(AgentBuildVerifier.isFinished(BuildRunOutcome.SUCCESS))
        assertTrue(AgentBuildVerifier.isFinished(BuildRunOutcome.FAILURE))
        assertTrue(AgentBuildVerifier.isFinished(BuildRunOutcome.CANCELLED))
        assertFalse(AgentBuildVerifier.isFinished(BuildRunOutcome.RUNNING))
        assertFalse(AgentBuildVerifier.isFinished(BuildRunOutcome.QUEUED))
        assertFalse(AgentBuildVerifier.isFinished(BuildRunOutcome.UNKNOWN))
    }

    @Test
    fun `describe produces distinct messages per outcome`() {
        val ok = BuildRunSnapshot(1, "CI", "s", "completed", "success", "")
        val fail = BuildRunSnapshot(2, "CI", "s", "completed", "failure", "")
        assertTrue(AgentBuildVerifier.describe(ok).contains("نجح"))
        assertTrue(AgentBuildVerifier.describe(fail).contains("فشل"))
        assertTrue(AgentBuildVerifier.describe(null).contains("لم يبدأ"))
    }
}
