package com.qabas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ق90: سجل تنبيهات المطور — الملخص والمرشح والعرض المجمّع.
 * السجل كان قائمة مسطحة للتنبيهات المُطلقة فقط: التقييم الذي لم
 * يُطلق شيئاً اختفى تماماً، وبلا ملخص اتجاهات ولا مرشح شدة.
 * هذه الاختبارات تربط الدالتين الخالصتين اللتين يبني عليهما العرض.
 */
class DevAlertHistoryTest {

    private val now = 1_800_000_000_000L

    private fun alert(
        type: AlertType,
        severity: AlertSeverity,
        timeMs: Long = now
    ) = DevAlert(
        type = type,
        severity = severity,
        message = "${type.label} تجريبي",
        measuredValue = "1",
        thresholdValue = "0",
        timeMs = timeMs
    )

    private fun evaluation(
        timeMs: Long,
        fired: List<DevAlert> = emptyList(),
        unevaluated: List<String> = emptyList()
    ) = StoredAlertEvaluation(
        timeMs = timeMs,
        consecutiveGatewayFailures = 0,
        fired = fired,
        unevaluated = unevaluated
    )

    @Test
    fun `summary counts evaluations alerts last24h and per type`() {
        val stored = listOf(
            evaluation(now, fired = listOf(alert(AlertType.GATEWAY_ERROR, AlertSeverity.CRITICAL, now))),
            evaluation(
                now - 3_600_000L,
                fired = listOf(
                    alert(AlertType.API_KEY_FAILURE, AlertSeverity.CRITICAL, now - 3_600_000L),
                    alert(AlertType.SIGNUP_SURGE, AlertSeverity.WARNING, now - 3_600_000L)
                )
            ),
            // أقدم من 24 ساعة: يُحسب في الإجمالي لا في آخر 24 ساعة.
            evaluation(now - 90_000_000L, fired = listOf(alert(AlertType.CRASH_SPIKE, AlertSeverity.CRITICAL, now - 90_000_000L))),
            // تقييم بلا تنبيهات: يُحسب تقييماً لا تنبيهاً.
            evaluation(now - 1_800_000L)
        )
        val summary = summarizeAlertHistory(stored, now)
        assertEquals(4, summary.totalEvaluations)
        assertEquals(4, summary.totalFired)
        assertEquals(3, summary.firedLast24h)
        assertEquals(1, summary.perType[AlertType.GATEWAY_ERROR])
        assertEquals(1, summary.perType[AlertType.API_KEY_FAILURE])
        assertEquals(1, summary.perType[AlertType.SIGNUP_SURGE])
        assertEquals(1, summary.perType[AlertType.CRASH_SPIKE])
    }

    @Test
    fun `no filter keeps evaluations with no alerts`() {
        val stored = listOf(
            evaluation(now - 1_000L),
            evaluation(now, fired = listOf(alert(AlertType.GATEWAY_ERROR, AlertSeverity.WARNING, now)))
        )
        val visible = visibleAlertHistory(stored, null)
        assertEquals(2, visible.size)
        assertTrue(visible[0].second.isEmpty())
        assertEquals(1, visible[1].second.size)
    }

    @Test
    fun `critical filter keeps only critical alerts and drops empty evaluations`() {
        val stored = listOf(
            evaluation(
                now,
                fired = listOf(
                    alert(AlertType.GATEWAY_ERROR, AlertSeverity.CRITICAL, now),
                    alert(AlertType.SIGNUP_SURGE, AlertSeverity.WARNING, now)
                )
            ),
            evaluation(now - 1_000L, fired = listOf(alert(AlertType.SIGNUP_SURGE, AlertSeverity.WARNING, now - 1_000L))),
            evaluation(now - 2_000L)
        )
        val visible = visibleAlertHistory(stored, AlertSeverity.CRITICAL)
        assertEquals(1, visible.size)
        assertEquals(1, visible[0].second.size)
        assertEquals(AlertSeverity.CRITICAL, visible[0].second[0].severity)
    }

    @Test
    fun `warning filter on history with no warnings is empty`() {
        val stored = listOf(
            evaluation(now, fired = listOf(alert(AlertType.GATEWAY_ERROR, AlertSeverity.CRITICAL, now)))
        )
        assertTrue(visibleAlertHistory(stored, AlertSeverity.WARNING).isEmpty())
        assertTrue(visibleAlertHistory(emptyList(), null).isEmpty())
        assertEquals(0, summarizeAlertHistory(emptyList(), now).totalFired)
    }
}
