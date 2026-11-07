package com.qabas.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.util.Log
import android.view.View
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * BugReportManager: مسئول عن التقاط الشاشة، حفظها مؤقتاً، ورفعها إلى Supabase.
 */
object BugReportManager {
    private const val TAG = "BugReportManager"
    var currentScreen: String = "Unknown"

    /**
     * يلتقط صورة لواجهة المستخدم الحالية.
     */
    fun captureScreenshot(view: View): Bitmap? {
        if (view.width <= 0 || view.height <= 0 || !view.isLaidOut) {
            Log.w(TAG, "Screenshot skipped: view has no measured dimensions")
            return null
        }

        return try {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                view.draw(canvas)
                bitmap
            } catch (e: Exception) {
                bitmap.recycle()
                throw e
            }
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Screenshot capture ran out of memory", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture screenshot", e)
            null
        }
    }

    /**
     * يحفظ الـ Bitmap كملف مؤقت.
     */
    suspend fun saveBitmapToCache(context: Context, bitmap: Bitmap): File? = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "bug_report_${UUID.randomUUID()}.jpg")
        try {
            FileOutputStream(file).use { output ->
                val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
                if (!compressed) {
                    throw java.io.IOException("Bitmap compression returned false")
                }
                output.flush()
            }
            file
        } catch (e: Exception) {
            runCatching { file.delete() }
            Log.e(TAG, "Failed to save bitmap to cache", e)
            null
        }
    }

    /**
     * يرفع ويسجل البلاغ محلياً وسحابياً عبر BugReportRepository.
     */
    suspend fun submitReport(
        context: Context,
        screenName: String,
        description: String,
        reportType: String,
        screenshotFile: File?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = BugReportRepository.submitReport(
                context = context,
                screenName = screenName,
                description = description,
                reportType = reportType,
                screenshotFile = screenshotFile
            )
            screenshotFile?.delete()
            result.isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Failed to submit bug report: ${e.message}")
            false
        }
    }
}
