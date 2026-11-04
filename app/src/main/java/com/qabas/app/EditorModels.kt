package com.qabas.app

import androidx.compose.ui.graphics.Color
import java.util.UUID

/**
 * Core models for the Manual Pro Studio Editor
 */

enum class TrackType {
    VIDEO, AUDIO, TEXT, OVERLAY
}

data class EditorClip(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val resourceUri: String,
    val startOffsetMs: Long,
    val durationMs: Long,
    val trackIndex: Int,
    val sourceStartMs: Long = 0L,
    val sourceDurationMs: Long = 0L,
    val type: TrackType,
    val color: Color,
    val volume: Float = 1.0f,
    val speed: Float = 1.0f, // New: Speed control
    val isSelected: Boolean = false,
    val thumbnail: String? = null,
    val keyframes: List<EditorKeyframe> = emptyList(),
    val hasSmartCaptions: Boolean = false,
    val filters: FilterParams = FilterParams(),
    val transitionIn: String? = null,
    val blendMode: String = "Normal", // New: PC-like blend modes
    val transform: TransformParams = TransformParams() // New: Precise transform
)

data class TransformParams(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val rotation: Float = 0f
)

data class FilterParams(
    val brightness: Float = 1f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val temperature: Float = 0f,
    val vignette: Float = 0f,
    val sharpness: Float = 0f // New
)

data class EditorKeyframe(
    val timeMs: Long,
    val property: String, // "scale", "position", "opacity"
    val value: Float
)

data class EditorTrack(
    val id: String = UUID.randomUUID().toString(),
    val type: TrackType,
    val name: String,
    val clips: List<EditorClip> = emptyList(),
    val isMuted: Boolean = false,
    val isLocked: Boolean = false,
    val isVisible: Boolean = true,
    val volume: Float = 1.0f,
    val isSolo: Boolean = false
)

data class EditorProjectState(
    val projectId: String,
    val title: String,
    val tracks: List<EditorTrack> = listOf(
        EditorTrack(type = TrackType.TEXT, name = "نصوص"),
        EditorTrack(type = TrackType.VIDEO, name = "فيديو"),
        EditorTrack(type = TrackType.AUDIO, name = "صوت")
    ),
    val currentTimeMs: Long = 0,
    val totalDurationMs: Long = 30000,
    val isPlaying: Boolean = false,
    val zoomLevel: Float = 1.0f, // For timeline scaling
    val selectedClipId: String? = null,
    val isSmartCaptionsEnabled: Boolean = false,
    val isMagneticTimeline: Boolean = true, // New: PC-like magnetic timeline
    val workspaceMode: WorkspaceMode = WorkspaceMode.EDIT // New: PC-like layouts
)

enum class WorkspaceMode {
    EDIT, COLOR, AUDIO, EXPORT
}

/** Stable autosave fingerprint that ignores rapidly changing playback position and play state. */
internal fun editorDraftContentHash(state: EditorProjectState): Int =
    state.copy(currentTimeMs = 0L, isPlaying = false).hashCode()

/** Applies a focused update to one existing timeline track. */
internal fun updateEditorTrack(
    state: EditorProjectState,
    trackId: String,
    update: (EditorTrack) -> EditorTrack
): EditorProjectState = state.copy(
    tracks = state.tracks.map { track -> if (track.id == trackId) update(track) else track }
)

internal fun toggleEditorTrackVisibility(state: EditorProjectState, trackId: String) =
    updateEditorTrack(state, trackId) { it.copy(isVisible = !it.isVisible) }

internal fun toggleEditorTrackMute(state: EditorProjectState, trackId: String) =
    updateEditorTrack(state, trackId) { it.copy(isMuted = !it.isMuted) }

internal fun toggleEditorTrackLock(state: EditorProjectState, trackId: String) =
    updateEditorTrack(state, trackId) { it.copy(isLocked = !it.isLocked) }

/** Indicates whether a track is included in the configured mixer state. */
internal fun isEditorTrackIncludedInMix(state: EditorProjectState, track: EditorTrack): Boolean {
    val audioTracks = state.tracks.filter { it.type == TrackType.AUDIO || it.type == TrackType.VIDEO }
    val hasSoloTrack = audioTracks.any { it.isSolo }
    return !track.isMuted && (!hasSoloTrack || track.isSolo)
}

/**
 * Adds or replaces scale/opacity keyframes at the playhead on an unlocked visual clip.
 * Returns null when the clip is missing, locked, outside its active range, or no valid values remain.
 */
internal fun addEditorKeyframesAtPlayhead(
    state: EditorProjectState,
    clipId: String,
    values: Map<String, Float>
): EditorProjectState? {
    val validValues = values.filter { (property, value) ->
        (property == "scale" || property == "opacity") && value.isFinite()
    }
    if (validValues.isEmpty()) return null

    val targetTrack = state.tracks.firstOrNull { track -> track.clips.any { it.id == clipId } } ?: return null
    if (targetTrack.isLocked) return null

    val clip = targetTrack.clips.first { it.id == clipId }
    val relativeTimeMs = state.currentTimeMs - clip.startOffsetMs
    if (relativeTimeMs < 0L || relativeTimeMs > clip.durationMs) return null

    val additions = validValues.map { (property, rawValue) ->
        val value = when (property) {
            "scale" -> rawValue.coerceIn(0.01f, 10f)
            else -> rawValue.coerceIn(0f, 1f)
        }
        EditorKeyframe(relativeTimeMs, property, value)
    }

    val updatedClip = clip.copy(
        keyframes = (clip.keyframes.filterNot { existing ->
            additions.any { it.timeMs == existing.timeMs && it.property == existing.property }
        } + additions).sortedWith(compareBy<EditorKeyframe> { it.timeMs }.thenBy { it.property })
    )
    return updateEditorTrack(state, targetTrack.id) { track ->
        track.copy(clips = track.clips.map { if (it.id == clipId) updatedClip else it })
    }
}

/** Adds a text clip to the first unlocked text track, creating a real track if necessary. */
internal fun addEditorTextClip(state: EditorProjectState, rawText: String): EditorProjectState? {
    val content = rawText.trim()
    if (content.isEmpty()) return null

    val startTime = state.currentTimeMs.coerceAtLeast(0L)
    val existingTrackIndex = state.tracks.indexOfFirst {
        it.type == TrackType.TEXT && !it.isLocked
    }
    val targetIndex = if (existingTrackIndex >= 0) existingTrackIndex else state.tracks.size
    val clip = EditorClip(
        title = content,
        resourceUri = "",
        startOffsetMs = startTime,
        durationMs = 5000L,
        trackIndex = targetIndex,
        sourceDurationMs = Long.MAX_VALUE,
        type = TrackType.TEXT,
        color = Color(0xFFFACC15)
    )
    val updatedTracks = if (existingTrackIndex >= 0) {
        state.tracks.mapIndexed { index, track ->
            if (index == existingTrackIndex) track.copy(clips = track.clips + clip) else track
        }
    } else {
        state.tracks + EditorTrack(type = TrackType.TEXT, name = "نصوص", clips = listOf(clip))
    }
    return state.copy(
        tracks = updatedTracks,
        currentTimeMs = startTime,
        totalDurationMs = maxOf(state.totalDurationMs, clip.startOffsetMs + clip.durationMs),
        selectedClipId = clip.id
    )
}

/** Updates an existing text clip only when its owning track is unlocked. */
internal fun updateEditorTextClip(
    state: EditorProjectState,
    clipId: String,
    rawText: String
): EditorProjectState? {
    val content = rawText.trim()
    if (content.isEmpty()) return null
    val track = state.tracks.firstOrNull { candidate ->
        candidate.type == TrackType.TEXT && candidate.clips.any { it.id == clipId }
    } ?: return null
    if (track.isLocked) return null

    return updateEditorTrack(state, track.id) { current ->
        current.copy(clips = current.clips.map { clip ->
            if (clip.id == clipId) clip.copy(title = content) else clip
        })
    }
}

private const val EDITOR_MIN_CLIP_DURATION_MS = 100L

/** Snap a requested timeline position to the nearest other clip edge when magnetic mode is enabled. */
private fun snapEditorTimelinePosition(
    state: EditorProjectState,
    clipId: String,
    requestedMs: Long,
    toleranceMs: Long
): Long {
    if (!state.isMagneticTimeline || toleranceMs <= 0L) return requestedMs.coerceAtLeast(0L)
    val boundaries = state.tracks.asSequence()
        .flatMap { it.clips.asSequence() }
        .filterNot { it.id == clipId }
        .flatMap { sequenceOf(it.startOffsetMs, it.startOffsetMs + it.durationMs) }
        .filter { it >= 0L }
        .toList()
    val nearest = boundaries.minByOrNull { kotlin.math.abs(it - requestedMs) }
    return if (nearest != null && kotlin.math.abs(nearest - requestedMs) <= toleranceMs) nearest else requestedMs.coerceAtLeast(0L)
}

/** Move a clip horizontally without changing its source duration or track. */
internal fun moveEditorClip(
    state: EditorProjectState,
    clipId: String,
    requestedStartMs: Long,
    snapToleranceMs: Long = 120L
): EditorProjectState? {
    val track = state.tracks.firstOrNull { candidate -> candidate.clips.any { it.id == clipId } } ?: return null
    if (track.isLocked) return null
    val clip = track.clips.first { it.id == clipId }
    val snappedStart = snapEditorTimelinePosition(state, clipId, requestedStartMs, snapToleranceMs)
    val updatedClip = clip.copy(startOffsetMs = snappedStart)
    val updatedTracks = state.tracks.map { current ->
        if (current.id == track.id) current.copy(clips = current.clips.map { if (it.id == clipId) updatedClip else it }) else current
    }
    return state.copy(
        tracks = updatedTracks,
        totalDurationMs = maxOf(state.totalDurationMs, snappedStart + clip.durationMs),
        selectedClipId = clipId
    )
}

/** Trim the leading edge while preserving the clip's original ending time. */
internal fun trimEditorClipStart(
    state: EditorProjectState,
    clipId: String,
    requestedStartMs: Long,
    snapToleranceMs: Long = 120L
): EditorProjectState? {
    val track = state.tracks.firstOrNull { candidate -> candidate.clips.any { it.id == clipId } } ?: return null
    if (track.isLocked) return null
    val clip = track.clips.first { it.id == clipId }
    val originalEnd = clip.startOffsetMs + clip.durationMs
    val speed = clip.speed.coerceIn(0.1f, 5f)
    val earliestAllowedStart = (clip.startOffsetMs - (clip.sourceStartMs / speed).toLong()).coerceAtLeast(0L)
    val snappedStart = snapEditorTimelinePosition(state, clipId, requestedStartMs, snapToleranceMs)
        .coerceIn(earliestAllowedStart, (originalEnd - EDITOR_MIN_CLIP_DURATION_MS).coerceAtLeast(earliestAllowedStart))
    val removedTimelineMs = snappedStart - clip.startOffsetMs
    val updatedClip = clip.copy(
        startOffsetMs = snappedStart,
        durationMs = originalEnd - snappedStart,
        sourceStartMs = clip.sourceStartMs +
            (removedTimelineMs * clip.speed.coerceIn(0.1f, 5f)).toLong(),
        keyframes = clip.keyframes
            .filter { it.timeMs >= removedTimelineMs }
            .map { it.copy(timeMs = it.timeMs - removedTimelineMs) }
    )
    return updateEditorTrack(state, track.id) { current ->
        current.copy(clips = current.clips.map { if (it.id == clipId) updatedClip else it })
    }.copy(selectedClipId = clipId)
}

/** Trim the trailing edge while preserving the clip's start time. */
internal fun trimEditorClipEnd(
    state: EditorProjectState,
    clipId: String,
    requestedEndMs: Long,
    snapToleranceMs: Long = 120L
): EditorProjectState? {
    val track = state.tracks.firstOrNull { candidate -> candidate.clips.any { it.id == clipId } } ?: return null
    if (track.isLocked) return null
    val clip = track.clips.first { it.id == clipId }
    val speed = clip.speed.coerceIn(0.1f, 5f)
    val sourceEndMs = if (clip.sourceDurationMs > 0L && clip.sourceDurationMs != Long.MAX_VALUE) {
        clip.sourceDurationMs
    } else {
        clip.sourceStartMs + (clip.durationMs * speed).toLong()
    }
    val maximumTimelineDurationMs = ((sourceEndMs - clip.sourceStartMs).coerceAtLeast(0L) / speed)
        .toLong().coerceAtLeast(EDITOR_MIN_CLIP_DURATION_MS)
    val maximumEndMs = clip.startOffsetMs + maximumTimelineDurationMs
    val snappedEnd = snapEditorTimelinePosition(state, clipId, requestedEndMs, snapToleranceMs)
        .coerceIn(
            clip.startOffsetMs + EDITOR_MIN_CLIP_DURATION_MS,
            maximumEndMs.coerceAtLeast(clip.startOffsetMs + EDITOR_MIN_CLIP_DURATION_MS)
        )
    val updatedDurationMs = snappedEnd - clip.startOffsetMs
    val updatedClip = clip.copy(
        durationMs = updatedDurationMs,
        keyframes = clip.keyframes.filter { it.timeMs <= updatedDurationMs }
    )
    return updateEditorTrack(state, track.id) { current ->
        current.copy(clips = current.clips.map { if (it.id == clipId) updatedClip else it })
    }.copy(
        totalDurationMs = maxOf(state.totalDurationMs, snappedEnd),
        selectedClipId = clipId
    )
}

/** Add a selected library asset to an unlocked track, creating the required media layer when absent. */
internal fun addEditorMediaClip(
    state: EditorProjectState,
    title: String,
    resourceUri: String,
    type: TrackType,
    durationMs: Long
): EditorProjectState? {
    if (title.isBlank() || resourceUri.isBlank() || durationMs <= 0L) return null
    val trackIndex = state.tracks.indexOfFirst { it.type == type && !it.isLocked }
        .takeIf { it >= 0 } ?: state.tracks.size
    val clip = EditorClip(
        title = title.trim(),
        resourceUri = resourceUri,
        startOffsetMs = state.currentTimeMs.coerceAtLeast(0L),
        durationMs = durationMs,
        trackIndex = trackIndex,
        sourceDurationMs = if (type == TrackType.OVERLAY) Long.MAX_VALUE else durationMs,
        type = type,
        color = when (type) {
            TrackType.VIDEO -> Color(0xFF3B82F6)
            TrackType.AUDIO -> Color(0xFF10B981)
            TrackType.TEXT -> Color(0xFFFACC15)
            TrackType.OVERLAY -> Color(0xFF8B5CF6)
        }
    )
    val updatedTracks = if (trackIndex < state.tracks.size) {
        state.tracks.mapIndexed { index, track ->
            if (index == trackIndex) track.copy(clips = track.clips + clip) else track
        }
    } else {
        state.tracks + EditorTrack(
            type = type,
            name = when (type) {
                TrackType.VIDEO -> "فيديو"
                TrackType.AUDIO -> "صوت"
                TrackType.TEXT -> "نصوص"
                TrackType.OVERLAY -> "طبقات وصور"
            },
            clips = listOf(clip)
        )
    }
    return state.copy(
        tracks = updatedTracks,
        totalDurationMs = maxOf(state.totalDurationMs, clip.startOffsetMs + clip.durationMs),
        selectedClipId = clip.id
    )
}

/** Change playback speed while preserving the selected source range and recalculating timeline duration. */
internal fun setEditorClipSpeed(
    state: EditorProjectState,
    clipId: String,
    requestedSpeed: Float
): EditorProjectState? {
    val track = state.tracks.firstOrNull { candidate -> candidate.clips.any { it.id == clipId } } ?: return null
    if (track.isLocked) return null
    val clip = track.clips.first { it.id == clipId }
    val newSpeed = requestedSpeed.coerceIn(0.1f, 5f)
    val oldSpeed = clip.speed.coerceIn(0.1f, 5f)
    // Preserve the clip's currently selected source range; do not restore trimmed-away media.
    val selectedSourceDurationMs = (clip.durationMs * oldSpeed).toLong()
        .coerceAtLeast(EDITOR_MIN_CLIP_DURATION_MS)
    val newDurationMs = (selectedSourceDurationMs / newSpeed).toLong()
        .coerceAtLeast(EDITOR_MIN_CLIP_DURATION_MS)
    val updatedClip = clip.copy(
        speed = newSpeed,
        durationMs = newDurationMs,
        keyframes = clip.keyframes.map { keyframe ->
            keyframe.copy(timeMs = (keyframe.timeMs * oldSpeed / newSpeed).toLong())
        }.filter { it.timeMs <= newDurationMs }
    )
    val updated = updateEditorTrack(state, track.id) { current ->
        current.copy(clips = current.clips.map { if (it.id == clipId) updatedClip else it })
    }
    val timelineEndMs = updated.tracks.flatMap { it.clips }.maxOfOrNull { it.startOffsetMs + it.durationMs } ?: 1000L
    return updated.copy(totalDurationMs = maxOf(1000L, timelineEndMs), selectedClipId = clipId)
}

/** Split an unlocked clip at the playhead, keeping keyframes in each segment's local time. */
internal fun splitEditorClip(
    state: EditorProjectState,
    clipId: String,
    playheadMs: Long = state.currentTimeMs
): EditorProjectState? {
    val track = state.tracks.firstOrNull { candidate -> candidate.clips.any { it.id == clipId } } ?: return null
    if (track.isLocked) return null
    val clip = track.clips.first { it.id == clipId }
    val splitOffsetMs = playheadMs - clip.startOffsetMs
    if (splitOffsetMs < EDITOR_MIN_CLIP_DURATION_MS ||
        splitOffsetMs > clip.durationMs - EDITOR_MIN_CLIP_DURATION_MS
    ) return null

    val firstPart = clip.copy(
        durationMs = splitOffsetMs,
        keyframes = clip.keyframes.filter { it.timeMs <= splitOffsetMs }
    )
    val secondPart = clip.copy(
        id = UUID.randomUUID().toString(),
        startOffsetMs = playheadMs,
        durationMs = clip.durationMs - splitOffsetMs,
        sourceStartMs = clip.sourceStartMs +
            (splitOffsetMs * clip.speed.coerceIn(0.1f, 5f)).toLong(),
        keyframes = clip.keyframes
            .filter { it.timeMs > splitOffsetMs }
            .map { it.copy(timeMs = it.timeMs - splitOffsetMs) },
        transitionIn = null
    )
    val updated = updateEditorTrack(state, track.id) { current ->
        current.copy(clips = current.clips.flatMap { if (it.id == clipId) listOf(firstPart, secondPart) else listOf(it) })
    }
    return updated.copy(selectedClipId = secondPart.id)
}

/** Delete one clip; magnetic ripple closes only the deleted clip's gap on its own track. */
internal fun deleteEditorClip(
    state: EditorProjectState,
    clipId: String,
    ripple: Boolean = state.isMagneticTimeline
): EditorProjectState? {
    val track = state.tracks.firstOrNull { candidate -> candidate.clips.any { it.id == clipId } } ?: return null
    if (track.isLocked) return null
    val clip = track.clips.first { it.id == clipId }
    val deletedEndMs = clip.startOffsetMs + clip.durationMs
    val updatedClips = track.clips.filterNot { it.id == clipId }.map { candidate ->
        if (ripple && candidate.startOffsetMs >= deletedEndMs) {
            candidate.copy(startOffsetMs = (candidate.startOffsetMs - clip.durationMs).coerceAtLeast(0L))
        } else candidate
    }
    val updated = updateEditorTrack(state, track.id) { it.copy(clips = updatedClips) }
    val remainingEndMs = updated.tracks.flatMap { it.clips }.maxOfOrNull { it.startOffsetMs + it.durationMs } ?: 1000L
    return updated.copy(
        totalDurationMs = maxOf(1000L, remainingEndMs),
        selectedClipId = if (state.selectedClipId == clipId) null else state.selectedClipId
    )
}

/** Returns the nearest clip boundary in the requested direction, or null when none exists. */
internal fun findAdjacentEditorClip(
    projectState: EditorProjectState,
    forward: Boolean
): EditorClip? {
    val clips = projectState.tracks
        .filterNot { it.isLocked }
        .flatMap { it.clips }
    return if (forward) {
        clips.filter {
            it.startOffsetMs > projectState.currentTimeMs ||
                (projectState.selectedClipId == null && it.startOffsetMs == projectState.currentTimeMs)
        }.minByOrNull { it.startOffsetMs }
    } else {
        clips.filter { it.startOffsetMs < projectState.currentTimeMs }
            .maxByOrNull { it.startOffsetMs }
    }
}

/** Creates a genuinely empty editor project; media is added only from the user's library. */
internal fun createBlankEditorProjectState(): EditorProjectState =
    EditorProjectState(
        projectId = UUID.randomUUID().toString(),
        title = "مشروع جديد",
        totalDurationMs = 1000L
    )

data class MediaAsset(
    val id: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String,
    val durationSeconds: Int,
    val type: String // "video", "image", "audio"
)
