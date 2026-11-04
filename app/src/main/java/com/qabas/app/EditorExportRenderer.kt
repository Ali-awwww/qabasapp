package com.qabas.app

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.util.Locale

internal data class EditorExportPlan(
    val clip: EditorClip,
    val track: EditorTrack,
    val width: Int,
    val height: Int,
    val fps: Int,
    val durationMs: Long,
    val includeAudio: Boolean
)

internal data class EditorExportPlanResult(
    val plan: EditorExportPlan? = null,
    val error: String? = null
)

/**
 * First real export slice: a single visible video clip, including its source audio.
 * Unsupported compositing is rejected explicitly rather than silently omitted.
 */
internal fun createEditorExportPlan(
    state: EditorProjectState,
    width: Int,
    height: Int,
    fps: Int
): EditorExportPlanResult {
    if (width !in setOf(720, 1080, 2160) || height !in setOf(1280, 1920, 3840)) {
        return EditorExportPlanResult(error = "دقة التصدير غير مدعومة")
    }
    if (width * 16 != height * 9 || fps !in setOf(24, 30, 60)) {
        return EditorExportPlanResult(error = "نسبة الأبعاد أو معدل الإطارات غير مدعوم")
    }

    val visibleTracks = state.tracks.filter { it.isVisible }
    val visualClips = visibleTracks.flatMap { track ->
        track.clips.filter { it.type == TrackType.VIDEO || it.type == TrackType.OVERLAY || it.type == TrackType.TEXT }
            .map { track to it }
    }
    if (visualClips.size != 1 || visualClips.single().second.type != TrackType.VIDEO) {
        return EditorExportPlanResult(
            error = "التصدير الحالي يتطلب مقطع فيديو مرئياً واحداً فقط؛ الطبقات المتعددة والنصوص تحتاج محرك تركيب متعدد المسارات."
        )
    }

    val (track, clip) = visualClips.single()
    if (visibleTracks.any { candidate ->
            candidate.type == TrackType.AUDIO && candidate.clips.isNotEmpty()
        }) {
        return EditorExportPlanResult(error = "مسارات الصوت المنفصلة غير مدعومة بعد؛ أزلها أو انتظر دعم الميكس متعدد المسارات")
    }
    if (track.type != TrackType.VIDEO || visibleTracks.flatMap { it.clips }.size != 1) {
        return EditorExportPlanResult(error = "لا يمكن تصدير مسارات صوت أو طبقات إضافية قبل دعم الميكس والتركيب متعدد المسارات")
    }
    if (clip.resourceUri.isBlank() || clip.durationMs <= 0L || state.totalDurationMs <= 0L) {
        return EditorExportPlanResult(error = "المقطع أو مدة المشروع غير صالحين للتصدير")
    }
    if (clip.speed !in 0.1f..5f || clip.sourceStartMs < 0L || clip.startOffsetMs < 0L) {
        return EditorExportPlanResult(error = "توقيت المصدر أو سرعة المقطع خارج النطاق المدعوم")
    }
    val numericValues = listOf(
        clip.volume, track.volume, clip.transform.scaleX, clip.transform.scaleY,
        clip.transform.rotation, clip.transform.posX, clip.transform.posY,
        clip.filters.brightness, clip.filters.contrast, clip.filters.saturation,
        clip.filters.temperature, clip.filters.vignette, clip.filters.sharpness
    )
    if (numericValues.any { !it.isFinite() }) {
        return EditorExportPlanResult(error = "تحتوي إعدادات المقطع على قيمة رقمية غير صالحة")
    }
    if (clip.sourceDurationMs > 0L && clip.sourceDurationMs != Long.MAX_VALUE &&
        clip.sourceStartMs + (clip.durationMs * clip.speed).toLong() > clip.sourceDurationMs
    ) {
        return EditorExportPlanResult(error = "نطاق القص يتجاوز مدة ملف المصدر")
    }
    if (clip.transitionIn != null || clip.blendMode != "Normal") {
        return EditorExportPlanResult(error = "الانتقالات وأنماط المزج غير مدعومة في إصدار الرندر الحالي")
    }
    if (clip.transform.posX != 0f || clip.transform.posY != 0f) {
        return EditorExportPlanResult(error = "إزاحة موضع المقطع غير مدعومة في إصدار الرندر الحالي")
    }
    if (clip.filters.temperature != 0f || clip.filters.vignette != 0f) {
        return EditorExportPlanResult(error = "تأثيرا حرارة اللون والتظليل غير مدعومين في إصدار الرندر الحالي")
    }

    return EditorExportPlanResult(
        plan = EditorExportPlan(
            clip = clip,
            track = track,
            width = width,
            height = height,
            fps = fps,
            durationMs = maxOf(1000L, clip.startOffsetMs + clip.durationMs),
            includeAudio = isEditorTrackIncludedInMix(state, track)
        )
    )
}

internal fun buildEditorExportCommand(
    plan: EditorExportPlan,
    inputPath: String,
    outputPath: String,
    hasAudio: Boolean
): String {
    fun number(value: Float) = String.format(Locale.US, "%.4f", value)
    fun quote(value: String): String {
        require(value.none { it == '"' || it == '\n' || it == '\r' }) { "مسار ملف غير صالح" }
        return "\"$value\""
    }

    val clip = plan.clip
    val speed = clip.speed.coerceIn(0.1f, 5f)
    val sourceDurationSeconds = clip.durationMs * speed / 1000.0
    val filters = mutableListOf<String>()
    filters += "setpts=PTS/${number(speed)}"
    filters += "scale=iw*${number(clip.transform.scaleX.coerceIn(0.1f, 10f))}:ih*${number(clip.transform.scaleY.coerceIn(0.1f, 10f))}"
    if (clip.transform.rotation != 0f) {
        filters += "rotate=${number(clip.transform.rotation)}*PI/180:ow=rotw(iw):oh=roth(ih):c=black"
    }
    filters += "scale=${plan.width}:${plan.height}:force_original_aspect_ratio=decrease"
    filters += "pad=${plan.width}:${plan.height}:(ow-iw)/2:(oh-ih)/2:color=black"
    filters += "setsar=1"
    if (clip.filters.brightness != 1f || clip.filters.contrast != 1f || clip.filters.saturation != 1f) {
        filters += "eq=brightness=${number(clip.filters.brightness - 1f)}:contrast=${number(clip.filters.contrast)}:saturation=${number(clip.filters.saturation)}"
    }
    if (clip.filters.sharpness > 0f) {
        filters += "unsharp=5:5:${number(clip.filters.sharpness.coerceIn(0f, 5f))}"
    }
    filters += "fps=${plan.fps}"
    filters += "tpad=start_mode=add:start_duration=${clip.startOffsetMs / 1000.0}:stop_mode=add:stop_duration=${(plan.durationMs - clip.startOffsetMs - clip.durationMs).coerceAtLeast(0L) / 1000.0}:color=black"
    filters += "trim=duration=${plan.durationMs / 1000.0}"
    filters += "format=yuv420p"

    val command = StringBuilder()
        .append("-y -ss ${clip.sourceStartMs / 1000.0} -t $sourceDurationSeconds -i ")
        .append(quote(inputPath))
        .append(" -vf ")
        .append(quote(filters.joinToString(",")))
        .append(" -map 0:v:0 ")
    if (hasAudio && plan.includeAudio && clip.volume > 0f) {
        val atempo = buildList {
            var remaining = speed
            while (remaining > 2f) { add("atempo=2.0"); remaining /= 2f }
            while (remaining < 0.5f) { add("atempo=0.5"); remaining /= 0.5f }
            add("atempo=${number(remaining)}")
        }.joinToString(",")
        command.append("-map 0:a:0 -af ")
            .append(quote("$atempo,volume=${number((clip.volume * plan.track.volume).coerceIn(0f, 1f))} "))
            .append(" -c:a aac -b:a 192k ")
    } else {
        command.append("-an ")
    }
    command.append("-c:v libx264 -preset medium -crf 20 -r ${plan.fps} ")
        .append("-movflags +faststart ")
        .append(quote(outputPath))
    return command.toString()
}

internal object EditorExportRenderer {
    suspend fun render(
        context: Context,
        state: EditorProjectState,
        width: Int,
        height: Int,
        fps: Int
    ): File {
        val result = createEditorExportPlan(state, width, height, fps)
        val plan = result.plan ?: throw IllegalArgumentException(result.error ?: "تعذر إعداد التصدير")

        val source = resolveSourceFile(context, plan.clip.resourceUri)
        val outputDirectory = File(context.getExternalFilesDir("Movies") ?: context.filesDir, "QabasExports")
            .apply { mkdirs() }
        val output = File(outputDirectory, "qabas_edit_${System.currentTimeMillis()}.mp4")
        val hasAudio = hasAudioTrack(source)
        val command = buildEditorExportCommand(plan, source.absolutePath, output.absolutePath, hasAudio)

        val timeout = (plan.durationMs * 12L).coerceIn(180_000L, 1_800_000L)
        val success = VideoProcessor.executeCommand(
            command = command,
            operationDescription = "تصدير المحرر الاحترافي",
            timeoutMs = timeout
        )
        if (!success ||
            !VideoProcessor.isValidVideoFile(output.absolutePath, VideoProcessor.MIN_OUTPUT_SIZE) ||
            !matchesExportMetadata(output, plan)
        ) {
            output.delete()
            throw IllegalStateException("لم ينتج محرك FFmpeg ملف MP4 صالحاً بالمواصفات المطلوبة")
        }
        return output
    }

    private fun resolveSourceFile(context: Context, rawUri: String): File {
        val uri = Uri.parse(rawUri)
        if (uri.scheme == null) {
            return File(rawUri).takeIf { it.isFile } ?: throw IllegalArgumentException("ملف المصدر غير موجود")
        }
        if (uri.scheme == "file") {
            return File(requireNotNull(uri.path)).takeIf { it.isFile }
                ?: throw IllegalArgumentException("ملف المصدر غير موجود")
        }
        if (uri.scheme != "content") {
            throw IllegalArgumentException("نوع رابط الوسيط غير مدعوم: ${uri.scheme}")
        }
        val destination = File(context.cacheDir, "editor_source_${rawUri.hashCode().toUInt()}.media")
        context.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("تعذر قراءة ملف الوسائط المحدد")
        if (!destination.isFile || destination.length() == 0L) {
            destination.delete()
            throw IllegalArgumentException("ملف الوسائط فارغ أو غير قابل للقراءة")
        }
        return destination
    }

    private fun matchesExportMetadata(file: File, plan: EditorExportPlan): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            width == plan.width && height == plan.height && duration != null &&
                kotlin.math.abs(duration - plan.durationMs) <= maxOf(1500L, 2000L / plan.fps)
        } catch (_: Exception) {
            false
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun hasAudioTrack(file: File): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            when (val audioFlag = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)) {
                "yes" -> true
                "no" -> false
                else -> throw IllegalStateException("قيمة وجود المسار الصوتي غير متاحة: $audioFlag")
            }
        } catch (error: Exception) {
            throw IllegalStateException("تعذر فحص المسار الصوتي لملف المصدر", error)
        } finally {
            runCatching { retriever.release() }
        }
    }
}
