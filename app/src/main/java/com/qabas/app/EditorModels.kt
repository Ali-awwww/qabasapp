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

    val endLimit = state.totalDurationMs.coerceAtLeast(1L)
    val startTime = state.currentTimeMs.coerceIn(0L, endLimit - 1L)
    val existingTrackIndex = state.tracks.indexOfFirst {
        it.type == TrackType.TEXT && !it.isLocked
    }
    val targetIndex = if (existingTrackIndex >= 0) existingTrackIndex else state.tracks.size
    val clip = EditorClip(
        title = content,
        resourceUri = "",
        startOffsetMs = startTime,
        durationMs = minOf(5000L, (endLimit - startTime).coerceAtLeast(1L)),
        trackIndex = targetIndex,
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
    return state.copy(tracks = updatedTracks, currentTimeMs = startTime, selectedClipId = clip.id)
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
    val snappedStart = snapEditorTimelinePosition(state, clipId, requestedStartMs, snapToleranceMs)
        .coerceIn(0L, (originalEnd - EDITOR_MIN_CLIP_DURATION_MS).coerceAtLeast(0L))
    val updatedClip = clip.copy(
        startOffsetMs = snappedStart,
        durationMs = originalEnd - snappedStart
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
    val snappedEnd = snapEditorTimelinePosition(state, clipId, requestedEndMs, snapToleranceMs)
        .coerceAtLeast(clip.startOffsetMs + EDITOR_MIN_CLIP_DURATION_MS)
    val updatedClip = clip.copy(durationMs = snappedEnd - clip.startOffsetMs)
    return updateEditorTrack(state, track.id) { current ->
        current.copy(clips = current.clips.map { if (it.id == clipId) updatedClip else it })
    }.copy(
        totalDurationMs = maxOf(state.totalDurationMs, snappedEnd),
        selectedClipId = clipId
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
        title = "مشروع جديد"
    )

data class MediaAsset(
    val id: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String,
    val durationSeconds: Int,
    val type: String // "video", "image", "audio"
)
