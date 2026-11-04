package com.qabas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HiggsfieldVideoServiceTest {

    @Test
    fun textToVideoPayloadUsesKlingFourSchemaAndClampsDuration() {
        val payload = HiggsfieldVideoService.buildTextToVideoPayload("  cinematic mosque  ", 45)

        assertEquals("cinematic mosque", payload.getString("prompt"))
        assertEquals(30, payload.getInt("duration"))
        assertEquals("1080p", payload.getString("resolution"))
        assertEquals("9:16", payload.getString("aspect_ratio"))
        assertFalse(payload.has("model"))
        assertFalse(payload.has("camera_motion"))
    }

    @Test
    fun textToVideoPayloadKeepsMinimumDuration() {
        val payload = HiggsfieldVideoService.buildTextToVideoPayload("prompt", 1)
        assertEquals(3, payload.getInt("duration"))
    }

    @Test
    fun parsesRequestIdAndNestedVideoUrlFromOfficialResponseShape() {
        val submission = JSONObject("""{"request_id":"req_123","status":"queued","status_url":"https://api.higgsfield.ai/requests/req_123/status"}""")
        val completed = JSONObject("""{"status":"completed","video":{"url":"https://cdn.example/video.mp4"}}""")

        assertEquals("req_123", HiggsfieldVideoService.extractRequestId(submission))
        assertEquals("https://cdn.example/video.mp4", HiggsfieldVideoService.extractVideoUrl(completed))
    }

    @Test
    fun recognizesOnlyDocumentedTerminalStatuses() {
        assertTrue(HiggsfieldVideoService.isTerminalStatus("completed"))
        assertTrue(HiggsfieldVideoService.isTerminalStatus("failed"))
        assertTrue(HiggsfieldVideoService.isTerminalStatus("nsfw"))
        assertTrue(HiggsfieldVideoService.isTerminalStatus("canceled"))
        assertFalse(HiggsfieldVideoService.isTerminalStatus("in_progress"))
        assertFalse(HiggsfieldVideoService.isTerminalStatus("queued"))
    }
}