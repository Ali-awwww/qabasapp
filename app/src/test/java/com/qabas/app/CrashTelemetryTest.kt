package com.qabas.app

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CrashTelemetryTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `payload satisfies anonymous insert policy without assigning a user id`() {
        val payload = CrashTelemetry.buildPayload(
            JSONObject()
                .put("id", "event-123")
                .put("screen_name", "Home")
                .put("description", "test report")
                .put("app_version", "1.0 (1)")
                .put("device_info", "Android test")
        )

        assertEquals("event-123", payload["id"]?.jsonPrimitive?.content)
        assertEquals("open", payload["status"]?.jsonPrimitive?.content)
        assertEquals("", payload["developer_reply"]?.jsonPrimitive?.content)
        assertNull(payload["user_id"])
        assertNotNull(payload["user_email"])
    }

    @Test
    fun `sanitize redacts credentials bearer tokens and email addresses`() {
        val sanitized = CrashTelemetry.sanitize(
            "token=abc123 Bearer eyJhbGciOiJIUzI1NiJ9 user@example.com",
            500
        )

        assertFalse(sanitized.contains("abc123"))
        assertFalse(sanitized.contains("eyJhbGciOiJIUzI1NiJ9"))
        assertFalse(sanitized.contains("user@example.com"))
        assertTrue(sanitized.contains("[REDACTED]"))
        assertTrue(sanitized.contains("[EMAIL_REDACTED]"))
    }

    @Test
    fun `persistRecord writes a durable json file visible in pending queue`() {
        val eventId = UUID.randomUUID().toString()
        val record = JSONObject()
            .put("id", eventId)
            .put("description", "persisted report")

        val file = CrashTelemetry.persistRecord(context, record)
        try {
            assertTrue(file.isFile)
            assertEquals(eventId, JSONObject(file.readText()).getString("id"))
            assertTrue(CrashTelemetry.pendingFiles(context).contains(file))
        } finally {
            file.delete()
        }
    }
}
