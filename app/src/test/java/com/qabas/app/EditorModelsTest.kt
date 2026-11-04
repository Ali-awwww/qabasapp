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

    @Test
    fun draftFingerprintIgnoresPlaybackTicksButIncludesTrackSettings() {
        val state = createBlankEditorProjectState()
        val playbackUpdate = state.copy(currentTimeMs = 1200L, isPlaying = true)
        val mixerUpdate = state.copy(
            tracks = state.tracks.mapIndexed { index, track ->
                if (index == 0) track.copy(volume = 0.4f, isMuted = true) else track
            }
        )

        assertEquals(editorDraftContentHash(state), editorDraftContentHash(playbackUpdate))
        assertFalse(editorDraftContentHash(state) == editorDraftContentHash(mixerUpdate))
    }

    @Test
    fun keyframeInsertionStoresScaleAndOpacityAtPlayheadAndReplacesDuplicates() {
        val clip = EditorClip(
            id = "visual-clip",
            title = "Title",
            resourceUri = "",
            startOffsetMs = 1000L,
            durationMs = 4000L,
            trackIndex = 0,
            type = TrackType.TEXT,
            color = androidx.compose.ui.graphics.Color.White
        )
        val state = EditorProjectState(
            projectId = "keyframe-test",
            title = "Keyframes",
            tracks = listOf(EditorTrack(type = TrackType.TEXT, name = "Text", clips = listOf(clip))),
            currentTimeMs = 2500L
        )

        val first = addEditorKeyframesAtPlayhead(
            state,
            clip.id,
            mapOf("scale" to 1.5f, "opacity" to 0.75f)
        )!!
        val updated = addEditorKeyframesAtPlayhead(
            first,
            clip.id,
            mapOf("scale" to 2f, "opacity" to 0.5f)
        )!!
        val keyframes = updated.tracks.single().clips.single().keyframes

        assertEquals(2, keyframes.size)
        assertEquals(1500L, keyframes.first { it.property == "scale" }.timeMs)
        assertEquals(2f, keyframes.first { it.property == "scale" }.value, 0.001f)
        assertEquals(0.5f, keyframes.first { it.property == "opacity" }.value, 0.001f)
    }

    @Test
    fun keyframeInsertionRejectsLockedOrInactiveClips() {
        val clip = EditorClip(
            id = "locked-clip",
            title = "Locked",
            resourceUri = "",
            startOffsetMs = 1000L,
            durationMs = 1000L,
            trackIndex = 0,
            type = TrackType.VIDEO,
            color = androidx.compose.ui.graphics.Color.Blue
        )
        val lockedState = EditorProjectState(
            projectId = "locked-keyframe",
            title = "Locked",
            tracks = listOf(EditorTrack(type = TrackType.VIDEO, name = "Video", clips = listOf(clip), isLocked = true)),
            currentTimeMs = 1500L
        )
        val inactiveState = lockedState.copy(
            tracks = listOf(EditorTrack(type = TrackType.VIDEO, name = "Video", clips = listOf(clip))),
            currentTimeMs = 2500L
        )

        assertNull(addEditorKeyframesAtPlayhead(lockedState, clip.id, mapOf("scale" to 1f)))
        assertNull(addEditorKeyframesAtPlayhead(inactiveState, clip.id, mapOf("scale" to 1f)))
    }

    @Test
    fun textClipCreationUsesUnlockedTextTrackAndSelectsNewClip() {
        val state = createBlankEditorProjectState().copy(currentTimeMs = 12000L)
        val updated = addEditorTextClip(state, "  نص دعوي  ")!!

        val textTrack = updated.tracks.single { it.type == TrackType.TEXT }
        val clip = textTrack.clips.single()

        assertEquals("نص دعوي", clip.title)
        assertEquals(12000L, clip.startOffsetMs)
        assertEquals(5000L, clip.durationMs)
        assertEquals(clip.id, updated.selectedClipId)
        assertEquals(clip.id, updated.tracks.flatMap { it.clips }.single().id)
    }

    @Test
    fun textClipCreationAddsUnlockedTrackWhenExistingTextTrackIsLocked() {
        val state = createBlankEditorProjectState().copy(
            tracks = listOf(EditorTrack(type = TrackType.TEXT, name = "Locked", isLocked = true))
        )
        val updated = addEditorTextClip(state, "عنوان جديد")!!

        assertEquals(2, updated.tracks.size)
        assertTrue(updated.tracks.first().isLocked)
        assertEquals("عنوان جديد", updated.tracks.last().clips.single().title)
        assertFalse(updated.tracks.last().isLocked)
    }

    @Test
    fun textClipEditingRejectsLockedTracksAndBlankContent() {
        val clip = EditorClip(
            id = "text-clip",
            title = "قبل",
            resourceUri = "",
            startOffsetMs = 0L,
            durationMs = 5000L,
            trackIndex = 0,
            type = TrackType.TEXT,
            color = androidx.compose.ui.graphics.Color.White
        )
        val unlocked = EditorProjectState(
            projectId = "text-edit",
            title = "Text",
            tracks = listOf(EditorTrack(type = TrackType.TEXT, name = "Text", clips = listOf(clip)))
        )
        val locked = unlocked.copy(
            tracks = listOf(unlocked.tracks.single().copy(isLocked = true))
        )

        assertEquals("بعد", updateEditorTextClip(unlocked, clip.id, " بعد ")?.tracks?.single()?.clips?.single()?.title)
        assertNull(updateEditorTextClip(locked, clip.id, "بعد"))
        assertNull(updateEditorTextClip(unlocked, clip.id, "   "))
        assertNull(addEditorTextClip(unlocked, "   "))
    }
}
