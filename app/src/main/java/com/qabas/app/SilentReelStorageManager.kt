package com.qabas.app

import android.content.Context
import android.net.Uri
import android.util.Log
import com.qabas.app.SupabaseServices.ReelRow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * إدارة التخزين المحلي لمقاطع الأثر داخل مساحة التطبيق الخاصة.
 * لا يُعرض المقطع إلا إذا كان ملفه موجوداً وصالحاً وضمن نافذة النشر.
 */
object SilentReelStorageManager {
    private const val TAG = "SilentReelStorage"
    private const val SILENT_DIR_NAME = "silent_reels"
    private const val METADATA_FILE = "metadata.json"
    const val MAX_VIDEO_STORAGE_BYTES = 300L * 1024L * 1024L

    private val safeIdPattern = Regex("^[A-Za-z0-9_-]{1,128}$")

    private fun getSilentDir(context: Context): File {
        val dir = File(context.filesDir, SILENT_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun isValidReelId(reelId: String): Boolean = safeIdPattern.matches(reelId)

    private fun readMetadata(context: Context): List<ReelRow> {
        val metadataFile = File(getSilentDir(context), METADATA_FILE)
        if (!metadataFile.isFile) return emptyList()
        return Json.decodeFromString<List<ReelRow>>(metadataFile.readText())
    }

    private fun parseTimestamp(value: String): Instant? {
        val normalized = value.trim()
        if (normalized.isEmpty()) return null
        return runCatching { Instant.parse(normalized) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(normalized).toInstant() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(normalized).toInstant(ZoneOffset.UTC) }.getOrNull()
    }

    /** تحقق مشترك من أن السجل ما زال مقطع أثر منشوراً الآن. */
    fun isEligibleForOfflinePlayback(reel: ReelRow, now: Instant = Instant.now()): Boolean {
        if (!isValidReelId(reel.id) || !reel.isActive) return false
        if (!reel.isSilentSeed && "silent_seed" !in reel.tags) return false

        val publishAtValue = reel.publishAt
        val publishAt = publishAtValue?.let(::parseTimestamp)
        if (publishAtValue != null && publishAt == null) return false
        if (publishAt != null && publishAt.isAfter(now)) return false

        val expiryAtValue = reel.expiryAt
        val expiryAt = expiryAtValue?.let(::parseTimestamp)
        if (expiryAtValue != null && expiryAt == null) return false
        if (expiryAt != null && !expiryAt.isAfter(now)) return false
        return true
    }

    /** قائمة المقاطع التي يمكن تشغيل ملفاتها المحلية حالياً فقط. */
    fun getDownloadedReels(context: Context): List<ReelRow> {
        return runCatching {
            val dir = getSilentDir(context)
            readMetadata(context).filter { reel ->
                val localFile = File(dir, "${reel.id}.mp4")
                isEligibleForOfflinePlayback(reel) && localFile.isFile && localFile.length() > 0
            }
        }.getOrElse {
            Log.e(TAG, "Failed to read silent metadata: ${it.message}")
            emptyList()
        }
    }

    /** كتابة آمنة نسبياً للبيانات الوصفية عبر ملف مؤقت قبل استبدال النسخة الحالية. */
    fun saveReelMetadata(context: Context, reels: List<ReelRow>) {
        runCatching {
            val dir = getSilentDir(context)
            val metadataFile = File(dir, METADATA_FILE)
            val tempFile = File(dir, "$METADATA_FILE.tmp")
            val safeReels = reels.filter { reel ->
                isValidReelId(reel.id) && File(dir, "${reel.id}.mp4").let { it.isFile && it.length() > 0 }
            }.distinctBy { it.id }
            tempFile.writeText(Json.encodeToString(safeReels))
            if (!tempFile.renameTo(metadataFile)) {
                metadataFile.writeText(tempFile.readText())
                tempFile.delete()
            }
        }.onFailure {
            Log.e(TAG, "Failed to save silent metadata: ${it.message}")
        }
    }

    /** حذف الملفات المؤقتة والمقاطع التي لم تعد فعالة أو منشورة بعد مزامنة سحابية ناجحة. */
    fun reconcileDownloadedReels(context: Context, eligibleReels: List<ReelRow>) {
        runCatching {
            val dir = getSilentDir(context)
            val eligibleIds = eligibleReels
                .asSequence()
                .filter { isEligibleForOfflinePlayback(it) }
                .map { it.id }
                .toSet()

            val previousById = runCatching { readMetadata(context).associateBy { it.id } }.getOrDefault(emptyMap())
            eligibleReels.forEach { reel ->
                val previousUrl = previousById[reel.id]?.videoUrl?.trim()
                if (reel.id in eligibleIds && !previousUrl.isNullOrBlank() && previousUrl != reel.videoUrl.trim()) {
                    File(dir, "${reel.id}.mp4").delete()
                }
            }

            dir.listFiles()?.forEach { file ->
                when {
                    file.name.endsWith(".tmp", ignoreCase = true) -> file.delete()
                    file.extension.equals("mp4", ignoreCase = true) &&
                        (file.nameWithoutExtension !in eligibleIds || file.length() <= 0) -> file.delete()
                }
            }

            val currentRecords = eligibleReels.filter { reel ->
                reel.id in eligibleIds && isReelDownloaded(context, reel.id)
            }
            saveReelMetadata(context, currentRecords)
        }.onFailure {
            Log.e(TAG, "Failed to reconcile silent downloads: ${it.message}")
        }
    }

    /** وجود ملف فيديو محلي غير فارغ. */
    fun isReelDownloaded(context: Context, reelId: String): Boolean {
        if (!isValidReelId(reelId)) return false
        val localFile = File(getSilentDir(context), "$reelId.mp4")
        return localFile.isFile && localFile.length() > 0
    }

    /** إجمالي أحجام ملفات الفيديو فقط، لاستعماله في فحص الحصة. */
    fun getVideoStorageSize(context: Context): Long =
        getSilentDir(context).listFiles { _, name -> name.endsWith(".mp4", ignoreCase = true) }
            ?.filter { it.isFile }
            ?.sumOf { it.length() }
            ?: 0L

    /** جلب المسار المحلي للفيديو. */
    fun getLocalVideoFile(context: Context, reelId: String): File {
        require(isValidReelId(reelId)) { "Invalid reel id" }
        return File(getSilentDir(context), "$reelId.mp4")
    }

    /** جلب Uri محلي للتشغيل عبر ExoPlayer. */
    fun getLocalVideoUri(context: Context, reelId: String): Uri? {
        val file = runCatching { getLocalVideoFile(context, reelId) }.getOrNull() ?: return null
        return if (file.isFile && file.length() > 0) Uri.fromFile(file) else null
    }

    /** حساب إجمالي المساحة المستخدمة لوضع الأثر بما فيها البيانات والملفات المؤقتة. */
    fun getTotalStorageSize(context: Context): Long =
        getSilentDir(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** عدد المقاطع الصالحة للعرض، لا عدد الملفات اليتيمة. */
    fun getDownloadedCount(context: Context): Int = getDownloadedReels(context).size

    /** حذف مقطع معين وبياناته الوصفية. */
    fun deleteReel(context: Context, reelId: String) {
        runCatching {
            getLocalVideoFile(context, reelId).delete()
            saveReelMetadata(context, getDownloadedReels(context).filterNot { it.id == reelId })
        }.onFailure { Log.w(TAG, "Failed to delete a silent reel: ${it.message}") }
    }

    /** تفريغ كل محتويات وضع الأثر. */
    fun clearAllSilentReels(context: Context) {
        runCatching {
            getSilentDir(context).deleteRecursively()
            getSilentDir(context).mkdirs()
        }
    }
}
