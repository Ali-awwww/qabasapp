package com.qabas.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * FFmpeg-backed implementations of the YouTube-studio tools that used to be
 * stubs. Each function takes a source video and an output path on disk;
 * all of them shell out through VideoProcessor so progress and error
 * reporting follow the same path as the rest of the app.
 */
object YouTubeStudioEngine {

    private const val TAG = "YouTubeStudioEngine"

    private fun outFile(context: Context, base: String, suffix: String): File =
        File(context.cacheDir, "${base}_${suffix}.mp4")

    /**
     * Strip silent passages longer than 0.7 seconds using FFmpeg's
     * `silenceremove` filter. Audio-only chain keeps the original video
     * stream copy so encoding is fast and lossless.
     */
    suspend fun removeSilence(
        context: Context,
        inputPath: String,
        outputPath: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val out = outputPath ?: outFile(context, "yt_silence", "removed").absolutePath
        val cmd = "-y -i \"$inputPath\" -af \"silenceremove=stop_periods=-1:stop_duration=0.7:stop_threshold=-30dB\" -c:v copy \"$out\""
        VideoProcessor.executeCommand(cmd, "إزالة الصمتات الطويلة")
    }

    /**
     * Clean up podcast-style voice audio: high-pass to kill rumble,
     * low-pass to tame hiss, and FFT-denoise to suppress residual noise.
     */
    suspend fun cleanupAudio(
        context: Context,
        inputPath: String,
        outputPath: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val out = outputPath ?: outFile(context, "yt_audio", "cleaned").absolutePath
        val cmd = "-y -i \"$inputPath\" -af \"highpass=f=80,lowpass=f=15000,afftdn\" -c:v copy \"$out\""
        VideoProcessor.executeCommand(cmd, "تصفية وتحسين الصوت")
    }

    /**
     * Produce a sidecar `.txt` listing chapter timestamps generated from
     * scene-change detection. The CLI consumer (e.g. the YouTube Studio
     * uploader) reads this to populate the chapter list.
     */
    suspend fun generateChapters(
        context: Context,
        inputPath: String,
        outputPath: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val out = outputPath ?: File(context.cacheDir, "yt_chapters.txt").absolutePath
        val tmp = File(context.cacheDir, "yt_chapter_probe.txt")
        val probe = "-i \"$inputPath\" -filter:v \"select='gt(scene,0.35)',showinfo\" -f null - 2>\"${tmp.absolutePath}\""
        val ok = VideoProcessor.executeCommand(probe, "توليد فصول زمنية")
        if (!ok) return@withContext null

        val lines = File(tmp.absolutePath).readLines()
        val chapters = lines.mapNotNull { line ->
            val match = Regex("pts_time:([0-9]+\\.?[0-9]*)").find(line) ?: return@mapNotNull null
            match.groupValues[1].toFloat()
        }.filter { it > 1f }.distinct().take(20)

        if (chapters.isEmpty()) {
            Log.w(TAG, "No chapter boundaries detected")
            return@withContext null
        }

        val text = buildString {
            chapters.forEachIndexed { idx, t ->
                val mm = (t.toInt() / 60).toString().padStart(2, '0')
                val ss = (t.toInt() % 60).toString().padStart(2, '0')
                append("$mm:$ss  ").append("الفصل ${idx + 1}").append('\n')
            }
        }
        File(out).writeText(text)
        out
    }

    /**
     * Burn a [caption] line into the lower third of the video for the
     * language lesson use case. The text is drawn with a contrasting
     * outline so it survives on dark *and* light footage. Re-encodes the
     * video stream — pass [outputPath] to control where the file lands.
     */
    suspend fun burnSubtitles(
        context: Context,
        inputPath: String,
        caption: String,
        outputPath: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (caption.isBlank()) return@withContext false
        val out = outputPath ?: outFile(context, "yt_subs", "burned").absolutePath
        // Escape single quotes and colons so drawtext does not choke.
        val escaped = caption.replace("'", "'\\''").replace(":", "\\:")
        val filter =
            "drawtext=text='$escaped':fontcolor=white:fontsize=42:" +
            "box=1:boxcolor=black@0.55:boxborderw=18:" +
            "x=(w-text_w)/2:y=h-text_h-60:" +
            "shadowcolor=black:shadowx=2:shadowy=2"
        val cmd = "-y -i \"$inputPath\" -vf \"$filter\" -c:v libx264 -preset fast -crf 23 -c:a copy \"$out\""
        VideoProcessor.executeCommand(cmd, "إضافة ترجمة نصية")
    }
}
