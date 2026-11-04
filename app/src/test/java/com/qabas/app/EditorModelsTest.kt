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
        val state = createBlankEditorProjectState().copy(currentTimeMs = 12000L, totalDurationMs = 20000L)
        val updated = addEditorTextClip(state, "  نص دعوي  ")!!

        val textTrack = updated.tracks.single { it.type == TrackType.TEXT }
        val clip = textTrack.clips.single()

        assertEquals("نص دعوي", clip.title)
        assertEquals(12000L, clip.startOffsetMs)
        assertEquals(5000L, clip.durationMs)
        assertEquals(Long.MAX_VALUE, clip.sourceDurationMs)
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

    @Test
    fun clipMoveSnapsToNearbyClipBoundariesAndPreservesDuration() {
        val moving = EditorClip(
            id = "moving",
            title = "Moving",
            resourceUri = "content://moving",
            startOffsetMs = 1000L,
            durationMs = 2000L,
            trackIndex = 0,
            type = TrackType.VIDEO,
            color = androidx.compose.ui.graphics.Color.Blue
        )
        val anchor = moving.copy(id = "anchor", startOffsetMs = 7000L, durationMs = 3000L)
        val state = EditorProjectState(
            projectId = "move-test",
            title = "Move",
            tracks = listOf(EditorTrack(type = TrackType.VIDEO, name = "Video", clips = listOf(moving, anchor)))
        )

        val updated = moveEditorClip(state, moving.id, 10080L)!!

        assertEquals(10000L, updated.tracks.single().clips.first { it.id == moving.id }.startOffsetMs)
        assertEquals(2000L, updated.tracks.single().clips.first { it.id == moving.id }.durationMs)
        assertEquals(moving.id, updated.selectedClipId)
    }

    @Test
    fun clipTrimmingPreservesOppositeEdgeAndEnforcesMinimumDuration() {
        val clip = EditorClip(
            id = "trim",
            title = "Trim",
            resourceUri = "content://trim",
            startOffsetMs = 1000L,
            durationMs = 4000L,
            trackIndex = 0,
            type = TrackType.VIDEO,
            color = androidx.compose.ui.graphics.Color.Blue,
            keyframes = listOf(
                EditorKeyframe(500L, "scale", 1f),
                EditorKeyframe(2000L, "scale", 1.5f),
                EditorKeyframe(3500L, "opacity", 0.5f)
            )
        )
        val state = EditorProjectState(
            projectId = "trim-test",
            title = "Trim",
            tracks = listOf(EditorTrack(type = TrackType.VIDEO, name = "Video", clips = listOf(clip))),
            isMagneticTimeline = false
        )

        val leadingTrim = trimEditorClipStart(state, clip.id, 2500L)!!
            .tracks.single().clips.single()
        assertEquals(2500L, leadingTrim.startOffsetMs)
        assertEquals(2500L, leadingTrim.durationMs)
        assertEquals(1500L, leadingTrim.sourceStartMs)
        assertEquals(2, leadingTrim.keyframes.size)
        assertEquals(500L, leadingTrim.keyframes.first { it.property == "scale" && it.value == 1.5f }.timeMs)

        val trailingTrim = trimEditorClipEnd(state, clip.id, 3800L)!!
            .tracks.single().clips.single()
        assertEquals(1000L, trailingTrim.startOffsetMs)
        assertEquals(2800L, trailingTrim.durationMs)
        assertEquals(2, trailingTrim.keyframes.size)

        val minimum = trimEditorClipEnd(state, clip.id, 1050L)!!
            .tracks.single().clips.single()
        assertEquals(100L, minimum.durationMs)
    }

    @Test
    fun clipTimelineOperationsRejectLockedTracksAndUnknownClips() {
        val clip = EditorClip(
            id = "locked-trim",
            title = "Locked",
            resourceUri = "content://locked",
            startOffsetMs = 0L,
            durationMs = 1000L,
            trackIndex = 0,
            type = TrackType.VIDEO,
            color = androidx.compose.ui.graphics.Color.Blue
        )
        val state = EditorProjectState(
            projectId = "locked-timeline",
            title = "Locked",
            tracks = listOf(EditorTrack(type = TrackType.VIDEO, name = "Locked", clips = listOf(clip), isLocked = true))
        )

        assertNull(moveEditorClip(state, clip.id, 2000L))
        assertNull(trimEditorClipStart(state, clip.id, 200L))
        assertNull(trimEditorClipEnd(state, clip.id, 800L))
        assertNull(moveEditorClip(state, "missing", 2000L))
    }

    @Test
    fun splitClipKeepsKeyframesInEachSegmentLocalTime() {
        val clip = EditorClip(
            id = "split",
            title = "Split",
            resourceUri = "content://split",
            startOffsetMs = 1000L,
            durationMs = 5000L,
            trackIndex = 0,
            type = TrackType.VIDEO,
            color = androidx.compose.ui.graphics.Color.Blue,
            keyframes = listOf(
                EditorKeyframe(500L, "scale", 1f),
                EditorKeyframe(2500L, "scale", 2f),
                EditorKeyframe(4500L, "opacity", 0.5f)
            )
        )
        val state = EditorProjectState(
            projectId = "split-test",
            title = "Split",
            tracks = listOf(EditorTrack(type = TrackType.VIDEO, name = "Video", clips = listOf(clip)))
        )

        val updated = splitEditorClip(state, clip.id, 3000L)!!
        val parts = updated.tracks.single().clips.sortedBy { it.startOffsetMs }

        assertEquals(2, parts.size)
        assertEquals(2000L, parts[0].durationMs)
        assertEquals(4000L, parts[1].startOffsetMs)
        assertEquals(3000L, parts[1].durationMs)
        assertEquals(2000L, parts[1].sourceStartMs)
        assertEquals(0L, parts[0].sourceStartMs)
        assertEquals(1, parts[0].keyframes.size)
        assertEquals(2, parts[1].keyframes.size)
        assertEquals(500L, parts[1].keyframes.first { it.property == "scale" }.timeMs)
        assertEquals(parts[1].id, updated.selectedClipId)
        assertNull(parts[1].transitionIn)
    }

    @Test
    fun splitRejectsClipEdgesAndLockedTracks() {
        val clip = EditorClip(
            id = "split-invalid",
            title = "Split",
            resourceUri = "",
            startOffsetMs = 1000L,
            durationMs = 1000L,
            trackIndex = 0,
            type = TrackType.VIDEO,
            color = androidx.compose.ui.graphics.Color.Blue
        )
        val state = EditorProjectState(
            projectId = "split-invalid",
            title = "Split",
            tracks = listOf(EditorTrack(type = TrackType.VIDEO, name = "Video", clips = listOf(clip)))
        )
        val locked = state.copy(tracks = listOf(state.tracks.single().copy(isLocked = true)))

        assertNull(splitEditorClip(state, clip.id, 1050L))
        assertNull(splitEditorClip(state, clip.id, 1950L))
        assertNull(splitEditorClip(locked, clip.id, 1500L))
    }

    @Test
    fun rippleDeleteClosesOnlyTheSelectedTrackGapAndPreservesEarlierOffsets() {
        val first = EditorClip(
            id = "first",
            title = "First",
            resourceUri = "",
            startOffsetMs = 0L,
            durationMs = 1000L,
            trackIndex = 0,
            type = TrackType.VIDEO,
            color = androidx.compose.ui.graphics.Color.Blue
        )
        val removed = first.copy(id = "removed", startOffsetMs = 3000L, durationMs = 2000L)
        val later = first.copy(id = "later", startOffsetMs = 7000L)
        val otherTrackClip = first.copy(id = "audio", startOffsetMs = 7000L, type = TrackType.AUDIO, trackIndex = 1)
        val state = EditorProjectState(
            projectId = "ripple-test",
            title = "Ripple",
            tracks = listOf(
                EditorTrack(type = TrackType.VIDEO, name = "Video", clips = listOf(first, removed, later)),
                EditorTrack(type = TrackType.AUDIO, name = "Audio", clips = listOf(otherTrackClip))
            ),
            selectedClipId = removed.id
        )

        val updated = deleteEditorClip(state, removed.id)!!

        assertEquals(0L, updated.tracks[0].clips.first { it.id == first.id }.startOffsetMs)
        assertEquals(5000L, updated.tracks[0].clips.first { it.id == later.id }.startOffsetMs)
        assertEquals(7000L, updated.tracks[1].clips.single().startOffsetMs)
        assertNull(updated.selectedClipId)
    }

    @Test
    fun mediaImportCreatesMissingOverlayTrackAndSelectsTheAsset() {
        val state = createBlankEditorProjectState().copy(currentTimeMs = 4200L, totalDurationMs = 5000L)
        val updated = addEditorMediaClip(
            state = state,
            title = "صورة الغلاف",
            resourceUri = "content://media/cover",
            type = TrackType.OVERLAY,
            durationMs = 5000L
        )!!

        val overlayTrack = updated.tracks.single { it.type == TrackType.OVERLAY }
        val clip = overlayTrack.clips.single()
        assertEquals("صورة الغلاف", clip.title)
        assertEquals(4200L, clip.startOffsetMs)
        assertEquals(5000L, clip.durationMs)
        assertEquals(clip.id, updated.selectedClipId)
        assertEquals(9200L, updated.totalDurationMs)
    }

    @Test
    fun mediaImportUsesAnUnlockedTrackAndRejectsMissingMediaOrDuration() {
        val state = createBlankEditorProjectState().copy(
            tracks = listOf(EditorTrack(type = TrackType.VIDEO, name = "Locked", isLocked = true))
        )
        val updated = addEditorMediaClip(
            state, "Video", "content://media/video", TrackType.VIDEO, 8000L
        )!!

        assertEquals(2, updated.tracks.size)
        assertTrue(updated.tracks.first().isLocked)
        assertEquals(1, updated.tracks.last().clips.size)
        assertNull(addEditorMediaClip(state, "", "content://media/video", TrackType.VIDEO, 8000L))
        assertNull(addEditorMediaClip(state, "Video", "", TrackType.VIDEO, 8000L))
        assertNull(addEditorMediaClip(state, "Video", "content://media/video", TrackType.VIDEO, 0L))
    }

    @Test
    fun sourceInPointScalesWithPlaybackSpeedWhenTrimmingAndSplitting() {
        val clip = EditorClip(
            id = "fast-clip",
            title = "Fast",
            resourceUri = "content://fast",
            startOffsetMs = 1000L,
            durationMs = 4000L,
            trackIndex = 0,
            type = TrackType.VIDEO,
            color = androidx.compose.ui.graphics.Color.Blue,
            speed = 2f
        )
        val state = EditorProjectState(
            projectId = "fast-edit",
            title = "Fast",
            tracks = listOf(EditorTrack(type = TrackType.VIDEO, name = "Video", clips = listOf(clip))),
            isMagneticTimeline = false
        )

        val trimmed = trimEditorClipStart(state, clip.id, 2000L)!!.tracks.single().clips.single()
        assertEquals(2000L, trimmed.sourceStartMs)
        assertEquals(3000L, trimmed.durationMs)

        val split = splitEditorClip(state, clip.id, 2500L)!!.tracks.single().clips
        val second = split.single { it.startOffsetMs == 2500L }
        assertEquals(3000L, second.sourceStartMs)
        assertEquals(2500L, second.durationMs)
    }

    @Test
    fun newTextClipUsesFiveSecondDefaultAndExtendsTheProjectTimeline() {
        val state = createBlankEditorProjectState()
        val updated = addEditorTextClip(state, "عنوان")!!
        val clip = updated.tracks.single { it.type == TrackType.TEXT }.clips.single()

        assertEquals(5000L, clip.durationMs)
        assertEquals(5000L, updated.totalDurationMs)
        assertEquals(Long.MAX_VALUE, clip.sourceDurationMs)
    }

    @Test
    fun trimStartCanExtendOnlyIntoAvailableSourceAndTrimEndCannotPassSourceEnd() {
        val clip = EditorClip(
            id = "bounded",
            title = "Bounded",
            resourceUri = "content://bounded",
            startOffsetMs = 2000L,
            durationMs = 3000L,
            trackIndex = 0,
            sourceStartMs = 2000L,
            sourceDurationMs = 10000L,
            type = TrackType.VIDEO,
            color = androidx.compose.ui.graphics.Color.Blue,
            speed = 2f
        )
        val state = EditorProjectState(
            projectId = "bounded-trim",
            title = "Bounded",
            tracks = listOf(EditorTrack(type = TrackType.VIDEO, name = "Video", clips = listOf(clip))),
            isMagneticTimeline = false
        )

        val extended = trimEditorClipStart(state, clip.id, 0L)!!.tracks.single().clips.single()
        assertEquals(1000L, extended.startOffsetMs)
        assertEquals(0L, extended.sourceStartMs)
        assertEquals(4000L, extended.durationMs)

        val sourceEndClip = clip.copy(
            id = "source-end",
            startOffsetMs = 1000L,
            durationMs = 2000L,
            sourceStartMs = 4000L,
            sourceDurationMs = 5000L,
            speed = 1f
        )
        val sourceEndState = state.copy(
            tracks = listOf(EditorTrack(type = TrackType.VIDEO, name = "Video", clips = listOf(sourceEndClip)))
        )
        val outTrim = trimEditorClipEnd(sourceEndState, sourceEndClip.id, 9000L)!!
            .tracks.single().clips.single()
        assertEquals(1000L, outTrim.durationMs)
        assertEquals(5000L, outTrim.sourceStartMs + outTrim.durationMs)
    }

    @Test
    fun speedChangesPreserveSourceRangeAndRespectLockedTracks() {
        val clip = EditorClip(
            id = "speed-range",
            title = "Speed",
            resourceUri = "content://speed",
            startOffsetMs = 1000L,
            durationMs = 8000L,
            trackIndex = 0,
            sourceStartMs = 2000L,
            sourceDurationMs = 10000L,
            type = TrackType.VIDEO,
            color = androidx.compose.ui.graphics.Color.Blue,
            keyframes = listOf(EditorKeyframe(4000L, "scale", 1.5f))
        )
        val state = EditorProjectState(
            projectId = "speed-range",
            title = "Speed",
            tracks = listOf(EditorTrack(type = TrackType.VIDEO, name = "Video", clips = listOf(clip)))
        )

        val faster = setEditorClipSpeed(state, clip.id, 2f)!!
            .tracks.single().clips.single()
        assertEquals(4000L, faster.durationMs)
        assertEquals(2f, faster.speed)
        assertEquals(2000L, faster.keyframes.single().timeMs)
        assertEquals(10000L, faster.sourceStartMs + (faster.durationMs * faster.speed).toLong())

        val slower = setEditorClipSpeed(state, clip.id, 0.5f)!!
            .tracks.single().clips.single()
        assertEquals(16000L, slower.durationMs)
        assertEquals(8000L, slower.keyframes.single().timeMs)
        assertEquals(10000L, slower.sourceStartMs + (slower.durationMs * slower.speed).toLong())

        val locked = state.copy(
            tracks = listOf(state.tracks.single().copy(isLocked = true))
        )
        assertNull(setEditorClipSpeed(locked, clip.id, 2f))
    }

    @Test
    fun movingAndTrimmingTheLastClipRecalculateProjectDuration() {
        val clip = EditorClip(
            id = "duration",
            title = "Duration",
            resourceUri = "content://duration",
            startOffsetMs = 10000L,
            durationMs = 5000L,
            sourceDurationMs = 15000L,
            trackIndex = 0,
            type = TrackType.VIDEO,
            color = androidx.compose.ui.graphics.Color.Blue
        )
        val state = EditorProjectState(
            projectId = "duration-test",
            title = "Duration",
            tracks = listOf(EditorTrack(type = TrackType.VIDEO, name = "Video", clips = listOf(clip))),
            totalDurationMs = 30000L,
            isMagneticTimeline = false
        )

        val moved = moveEditorClip(state, clip.id, 2000L)!!
        assertEquals(7000L, moved.totalDurationMs)

        val trimmed = trimEditorClipEnd(state, clip.id, 12000L)!!
        assertEquals(12000L, trimmed.totalDurationMs)
        assertEquals(2000L, trimmed.tracks.single().clips.single().durationMs)
    }
}
