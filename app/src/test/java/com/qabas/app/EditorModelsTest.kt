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

    @Test
    fun navigationSkipsLockedTracks() {
        val lockedClip = EditorClip(
            id = "locked",
            title = "Locked",
            resourceUri = "content://locked",
            startOffsetMs = 1000L,
            durationMs = 5000L,
            trackIndex = 0,
            type = TrackType.VIDEO,
            color = androidx.compose.ui.graphics.Color.Blue
        )
        val availableClip = lockedClip.copy(
            id = "available",
            title = "Available",
            startOffsetMs = 2000L
        )
        val state = EditorProjectState(
            projectId = "navigation-test",
            title = "Test",
            tracks = listOf(
                EditorTrack(type = TrackType.VIDEO, name = "Locked", clips = listOf(lockedClip), isLocked = true),
                EditorTrack(type = TrackType.VIDEO, name = "Available", clips = listOf(availableClip))
            )
        )

        assertEquals("available", findAdjacentEditorClip(state, forward = true)?.id)
    }

    @Test
    fun soloIncludesOnlySoloTracksAndMuteAlwaysExcludesTrack() {
        val first = EditorTrack(type = TrackType.AUDIO, name = "Voice", isSolo = true)
        val second = EditorTrack(type = TrackType.AUDIO, name = "Music")
        val third = EditorTrack(type = TrackType.AUDIO, name = "Muted", isSolo = true, isMuted = true)
        val state = EditorProjectState(
            projectId = "mixer-test",
            title = "Mixer",
            tracks = listOf(first, second, third)
        )

        assertTrue(isEditorTrackIncludedInMix(state, first))
        assertFalse(isEditorTrackIncludedInMix(state, second))
        assertFalse(isEditorTrackIncludedInMix(state, third))
    }
}
