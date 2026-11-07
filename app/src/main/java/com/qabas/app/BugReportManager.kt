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
        return try {
            // Compose content may be hosted inside a child View. Capture the laid-out root
            // so the screenshot uses the complete visible content rather than a partial host.
            val captureView = view.rootView
            val width = captureView.width
            val height = captureView.height

            if (width <= 0 || height <= 0 || !captureView.isLaidOut) {
                Log.e(TAG, "Screenshot capture skipped: root view is not laid out (width=$width, height=$height)")
                return null
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            captureView.draw(canvas)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture screenshot", e)
            null
        }
    }

    /**
     * يحفظ الـ Bitmap كملف مؤقت.
     */
    suspend fun saveBitmapToCache(context: Context, bitmap: Bitmap): File? = withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, "bug_report_${System.currentTimeMillis()}.jpg")
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.flush()
            out.close()
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bitmap to cache: ${e.message}")
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
