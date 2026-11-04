package com.qabas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorModelsTest {

    @Test
    fun blankEditorProjectStartsWithoutFabricatedMedia() {
        val state = createBlankEditorProjectState()

        assertEquals("مشروع جديد", state.title)
        assertFalse(state.isPlaying)
        assertTrue(state.tracks.isNotEmpty())
        assertTrue(state.tracks.all { it.clips.isEmpty() })
    }

    @Test
    fun blankEditorProjectHasNoAdjacentClipsToNavigateTo() {
        val state = createBlankEditorProjectState()

        assertNull(findAdjacentEditorClip(state, forward = false))
        assertNull(findAdjacentEditorClip(state, forward = true))
    }

    @Test
    fun layerControlsUpdateOnlyTheTargetTrackAndCanBeReversed() {
        val state = createBlankEditorProjectState()
        val target = state.tracks.first()
        val other = state.tracks.last()

        val hidden = toggleEditorTrackVisibility(state, target.id)
        assertFalse(hidden.tracks.first { it.id == target.id }.isVisible)
        assertTrue(hidden.tracks.first { it.id == other.id }.isVisible)

        val muted = toggleEditorTrackMute(hidden, target.id)
        assertTrue(muted.tracks.first { it.id == target.id }.isMuted)

        val locked = toggleEditorTrackLock(muted, target.id)
        assertTrue(locked.tracks.first { it.id == target.id }.isLocked)

        val restored = toggleEditorTrackLock(
            toggleEditorTrackMute(
                toggleEditorTrackVisibility(locked, target.id),
                target.id
            ),
            target.id
        )
        assertTrue(restored.tracks.first { it.id == target.id }.isVisible)
        assertFalse(restored.tracks.first { it.id == target.id }.isMuted)
        assertFalse(restored.tracks.first { it.id == target.id }.isLocked)
    }
}
