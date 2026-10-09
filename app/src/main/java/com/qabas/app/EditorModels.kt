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
    val isLocked: Boolean = false
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

data class MediaAsset(
    val id: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String,
    val durationSeconds: Int,
    val type: String // "video", "image", "audio"
)
