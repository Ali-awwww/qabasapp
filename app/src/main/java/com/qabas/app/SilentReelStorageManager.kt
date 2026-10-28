package com.qabas.app

import android.content.Context
import android.net.Uri
import android.util.Log
import com.qabas.app.SupabaseServices.ReelRow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 📁 SilentReelStorageManager
 * إدارة التخزين المحلي الصارم لساندبوكس التطبيق (Internal Storage).
 * جميع الملفات هنا تُحذف تلقائياً عند مسح بيانات التطبيق أو إلغاء التثبيت.
 */
object SilentReelStorageManager {
    private const val TAG = "SilentReelStorage"
    private const val SILENT_DIR_NAME = "silent_reels"
    private const val METADATA_FILE = "metadata.json"

    private fun getSilentDir(context: Context): File {
        val dir = File(context.filesDir, SILENT_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** يجلب قائمة المقاطع المحملة فعلياً مع بياناتها الوصفية */
    fun getDownloadedReels(context: Context): List<ReelRow> {
        return runCatching {
            val dir = getSilentDir(context)
            val metadataFile = File(dir, METADATA_FILE)
            if (!metadataFile.exists()) return emptyList()
            
            val json = metadataFile.readText()
            Json.decodeFromString<List<ReelRow>>(json).filter { reel ->
                File(dir, "${reel.id}.mp4").exists()
            }
        }.getOrElse {
            Log.e(TAG, "Failed to read silent metadata: ${it.message}")
            emptyList()
        }
    }

    /** يحفظ المقطع والبيانات الوصفية */
    fun saveReelMetadata(context: Context, reels: List<ReelRow>) {
        runCatching {
            val dir = getSilentDir(context)
            val metadataFile = File(dir, METADATA_FILE)
            val json = Json.encodeToString(reels)
            metadataFile.writeText(json)
        }.onFailure {
            Log.e(TAG, "Failed to save silent metadata: ${it.message}")
        }
    }

    /** التحقق من وجود المقطع محلياً */
    fun isReelDownloaded(context: Context, reelId: String): Boolean {
        val dir = getSilentDir(context)
        return File(dir, "$reelId.mp4").exists()
    }

    /** جلب المسار المحلي للفيديو */
    fun getLocalVideoFile(context: Context, reelId: String): File {
        return File(getSilentDir(context), "$reelId.mp4")
    }

    /** جلب Uri محلي للتشغيل عبر ExoPlayer */
    fun getLocalVideoUri(context: Context, reelId: String): Uri? {
        val file = getLocalVideoFile(context, reelId)
        return if (file.exists()) Uri.fromFile(file) else null
    }

    /** حساب إجمالي المساحة المستخدمة لوضع الأثر */
    fun getTotalStorageSize(context: Context): Long {
        return getSilentDir(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
    
    /** حذف مقطع معين */
    fun deleteReel(context: Context, reelId: String) {
        runCatching {
            val file = getLocalVideoFile(context, reelId)
            if (file.exists()) file.delete()
            val current = getDownloadedReels(context).filterNot { it.id == reelId }
            saveReelMetadata(context, current)
        }
    }
}
