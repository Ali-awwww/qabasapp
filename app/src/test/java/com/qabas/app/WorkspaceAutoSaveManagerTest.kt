package com.qabas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkspaceAutoSaveManagerTest {

    @Test
    fun restoresPersistedTrackVisibilityAndMixerSettings() {
        val json = """
            {
              "projectId": "project-1",
              "title": "Mixer project",
              "tracks": [{
                "id": "audio-1",
                "type": "AUDIO",
                "name": "Voice",
                "isMuted": true,
                "isLocked": true,
                "isVisible": false,
                "volume": 0.35,
                "isSolo": true,
                "clips": []
              }]
            }
        """.trimIndent()

        val restored = WorkspaceAutoSaveManager.deserializeProState(json)
        val track = restored?.tracks?.single()

        assertEquals("project-1", restored?.projectId)
        assertEquals("audio-1", track?.id)
        assertTrue(track?.isMuted == true)
        assertTrue(track?.isLocked == true)
        assertFalse(track?.isVisible ?: true)
        assertEquals(0.35f, track?.volume ?: -1f, 0.001f)
        assertTrue(track?.isSolo == true)
    }

    @Test
    fun legacyDraftDefaultsNewTrackSettingsSafely() {
        val json = """
            {
              "projectId": "legacy-project",
              "title": "Legacy",
              "tracks": [{
                "id": "video-1",
                "type": "VIDEO",
                "name": "Video",
                "clips": []
              }]
            }
        """.trimIndent()

        val track = WorkspaceAutoSaveManager.deserializeProState(json)?.tracks?.single()

        assertTrue(track?.isVisible == true)
        assertEquals(1.0f, track?.volume ?: -1f, 0.001f)
        assertFalse(track?.isMuted ?: true)
        assertFalse(track?.isLocked ?: true)
        assertFalse(track?.isSolo ?: true)
    }
}
