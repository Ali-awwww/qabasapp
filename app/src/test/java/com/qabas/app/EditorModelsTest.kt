package com.qabas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        assertEquals(null, findAdjacentEditorClip(state, forward = false))
        assertEquals(null, findAdjacentEditorClip(state, forward = true))
    }
}
