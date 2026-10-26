package com.qabas.app

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PhotoStudioEngine {
    private const val TAG = "PhotoStudioEngine"

    suspend fun generateVideoFromImages(
        context: Context,
        imageUris: List<Uri>,
        textOverlay: String?,
        audioPath: String?,
        outputPath: String,
        onProgress: ((Float, String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting Photo to Video generation...")
            onProgress?.invoke(0.10f, "جاري تحضير ملفات الصور في الذاكرة المؤقتة...")
            // 1. Copy URIs to local cache files
            val cachedImages = mutableListOf<File>()
            for ((index, uri) in imageUris.withIndex()) {
                val file = File(context.cacheDir, "photo_img_$index.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (file.exists()) {
                    cachedImages.add(file)
                }
            }

            if (cachedImages.isEmpty()) {
                Log.e(TAG, "No valid images found.")
                onProgress?.invoke(0f, "تعذر قراءة ملفات الصور")
                return@withContext false
            }

            // 2. Create individual video segments with Ken Burns effect for each image
            val segmentFiles = mutableListOf<File>()
            for ((index, imgFile) in cachedImages.withIndex()) {
                val prog = 0.15f + (0.50f * (index.toFloat() / cachedImages.size))
                onProgress?.invoke(prog, "تحريك الصورة ${index + 1} من ${cachedImages.size} (تأثير Ken Burns 🎬)...")
                val segFile = File(context.cacheDir, "photo_seg_$index.mp4")
                
                // Ken Burns effect: slight zoom in or zoom out to make images dynamic but simple
                // We generate a 3-second segment per image
                val zoomDirection = if (index % 2 == 0) "zoompan=z='min(zoom+0.0015,1.1)':d=90:x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)'" // zoom in
                else "zoompan=z='1.1-0.0015*in':d=90:x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)'" // zoom out
                
                // We first scale/crop the image to 9:16 (1080x1920) before applying the zoompan
                val scaleFilter = "scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,setsar=1"
                
                // We loop the image (-loop 1), apply scale -> zoompan, and stop after 3 seconds (-t 3)
                // -framerate 30 is used for smoother motion
                val segCmd = "-y -loop 1 -framerate 30 -i \"${imgFile.absolutePath}\" -vf \"$scaleFilter,$zoomDirection\" -c:v libx264 -preset fast -crf 22 -t 3 -pix_fmt yuv420p \"${segFile.absolutePath}\""
                
                val segSuccess = VideoProcessor.executeCommand(segCmd, "معالجة وتحريك الصورة ${index + 1}")
                if (segSuccess && segFile.exists()) {
                    segmentFiles.add(segFile)
                }
            }

            if (segmentFiles.isEmpty()) {
                Log.e(TAG, "Failed to create any video segments.")
                onProgress?.invoke(0f, "فشلت معالجة مقاطع الصور عبر FFmpeg")
                return@withContext false
            }

            onProgress?.invoke(0.70f, "دمج المقاطع السينمائية بتسلسل انسيابي...")
            // 3. Concatenate the segments (with crossfade transition if possible, else simple concat)
            // Using a simple concat file for robust merging
            val concatFile = File(context.cacheDir, "photo_concat.txt")
            val concatContent = StringBuilder()
            for (seg in segmentFiles) {
                concatContent.append("file '${seg.absolutePath}'\n")
            }
            concatFile.writeText(concatContent.toString())

            val baseVideoFile = File(context.cacheDir, "photo_base_video.mp4")
            val generateCmd = "-y -f concat -safe 0 -i \"${concatFile.absolutePath}\" -c:v copy \"${baseVideoFile.absolutePath}\""
            
            var success = VideoProcessor.executeCommand(generateCmd, "تجميع الفيديوهات المتحركة")
            if (!success || !baseVideoFile.exists()) {
                Log.e(TAG, "Failed to generate base video.")
                onProgress?.invoke(0f, "فشل دمج المقاطع")
                return@withContext false
            }

            var currentVideo = baseVideoFile.absolutePath

            // 4. Add Text Overlay if present
            if (!textOverlay.isNullOrBlank()) {
                onProgress?.invoke(0.85f, "تركيب النصوص والطبقات الجمالية...")
                val textVideoFile = File(context.cacheDir, "photo_text_video.mp4")
                success = VideoProcessor.addTextOverlay(context, currentVideo, textOverlay, textVideoFile.absolutePath)
                if (success && textVideoFile.exists()) {
                    currentVideo = textVideoFile.absolutePath
                }
            }

            // 5. Add Audio if present
            if (!audioPath.isNullOrBlank() && File(audioPath).exists()) {
                onProgress?.invoke(0.92f, "مزامنة الصوت المحيطي والدمج النهائي...")
                val finalVideoFile = File(outputPath)
                val audioCmd = "-y -i \"$currentVideo\" -stream_loop -1 -i \"$audioPath\" -c:v copy -c:a aac -b:a 128k -map 0:v:0 -map 1:a:0 -shortest \"${finalVideoFile.absolutePath}\""
                success = VideoProcessor.executeCommand(audioCmd, "إضافة الصوت للفيديو")
                if (success && finalVideoFile.exists()) {
                    // done
                } else {
                    File(currentVideo).copyTo(finalVideoFile, overwrite = true)
                }
            } else {
                File(currentVideo).copyTo(File(outputPath), overwrite = true)
            }

            onProgress?.invoke(1.0f, "اكتمل إنتاج الفيديو بنجاح!")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Exception in PhotoStudioEngine", e)
            false
        }
    }
}
