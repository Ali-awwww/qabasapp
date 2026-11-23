package com.qabas.app

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * عامل WorkManager لتنزيلات الريلز والوسائط في مركز التنزيلات.
 *
 * تقسيم المسؤولية (مقصود وموثّق):
 * - تنزيلات النماذج تبقى على الخدمة الأمامية [ModelDownloadService] مع
 *   الاستئناف عبر HTTP Range في [OnDeviceModelManager] — النماذج ملفات ضخمة
 *   تحتاج إشعار عدّاد دائم وWakeLock، والخدمة هي المسار الم established لها.
 * - تنزيلات الريلز والوسائط فقط تمرّ بهذا العامل: يبقى التنزيل حياً بعد موت
 *   العملية وإعادة تشغيل التطبيق، ويحترم قيود الشبكة، ويُستأنف عبر Range.
 *
 * التقدّم يُبلَّغ عبر [setProgress] (يُراقَب عبر WorkManager) وعبر تدفقات
 * [DownloadsHubManager] الحالية التي تعرضها الواجهة — لا يوجد نظام إشعارات
 * ثانٍ للتنزيلات؛ إشعارات النماذج مملوكة لخدمتها الأمامية وحدها.
 *
 * الإيقاف المؤقت: WorkManager لا يملك إيقافاً أصلياً؛ الإيقاف هنا إلغاءٌ للعمل
 * مع الإبقاء على الملف الجزئي (.tmp)، وإعادة الإدراج تستأنفه عبر Range.
 * الإلغاء لا يحذف التقدم أبداً؛ الحذف يتم فقط بإجراء حذف صريح من المستخدم.
 */
class DownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val kind = inputData.getString(KEY_KIND) ?: return Result.failure()
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: id
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val context = applicationContext

        if (!url.startsWith("https://", ignoreCase = true)) {
            DownloadsHubManager.reportWorkerError(kind, id, title, "تم حظر الرابط: يُشترط استخدام رابط مشفر وآمن (HTTPS).")
            return Result.failure()
        }

        return try {
            // تحميل السجلات المحفوظة أولاً: تسجيل الإكمال يكتب القائمة كاملة،
            // فلا يجوز الكتابة فوقها قبل تحميلها (وإلا ضاعت العناصر السابقة).
            DownloadsHubManager.init(context)
            when (kind) {
                KIND_REEL -> downloadReel(context, id, title, url)
                KIND_MEDIA -> downloadMedia(context, id, title, url)
                else -> Result.failure()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // إلغاء/إيقاف من المستخدم: يُبقي المحرك الملف الجزئي وحالة الاستئناف.
            throw e
        } catch (e: Exception) {
            DownloadsHubManager.reportWorkerError(kind, id, title, e.localizedMessage ?: "حدث خطأ أثناء التنزيل")
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private suspend fun downloadReel(context: Context, reelId: String, title: String, videoUrl: String): Result {
        val thumbnailUrl = inputData.getString(KEY_THUMBNAIL_URL)
        val author = inputData.getString(KEY_AUTHOR) ?: "صانع محتوى"
        if (DownloadsHubManager.isReelDownloaded(reelId)) return Result.success()

        val safeFileName = "reel_${reelId.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.mp4"
        val destFile = File(DownloadsHubManager.getReelsDirectory(context), safeFileName)
        val startTime = System.currentTimeMillis()
        val result = DownloadsHubManager.downloadFileResumable(
            context = context,
            fileUrl = videoUrl,
            destFile = destFile,
            maxBytes = MAX_REEL_DOWNLOAD_BYTES
        ) { done, total ->
            val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
            val speedKbps = if (elapsedSec > 0) ((done / 1024) / elapsedSec).toLong() else 0L
            setProgress(
                workDataOf(
                    KEY_PROGRESS to if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f,
                    KEY_DOWNLOADED_BYTES to done,
                    KEY_TOTAL_BYTES to total
                )
            )
            DownloadsHubManager.reportWorkerProgress(KIND_REEL, reelId, title, done, total, speedKbps)
        }

        return if (result.success) {
            DownloadsHubManager.addDownloadedReel(
                context,
                DownloadedReelItem(
                    id = reelId,
                    title = title,
                    author = author,
                    videoUrl = videoUrl,
                    localFilePath = destFile.absolutePath,
                    thumbnailUrl = thumbnailUrl,
                    fileSizeBytes = destFile.length()
                )
            )
            DownloadsHubManager.clearWorkerProgress(KIND_REEL, reelId)
            DownloadsHubManager.refreshStorageMetrics(context)
            Result.success()
        } else {
            // ق168: خطأ العامل يحمل رابط الريل — بدونه كان إدخال الريل
            // الفاشل في اللوحة بلا «إعادة» (إزالة فقط، كالوسائط في ق119).
            DownloadsHubManager.reportWorkerError(KIND_REEL, reelId, title, result.errorMessage ?: "فشل التنزيل", videoUrl)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private suspend fun downloadMedia(context: Context, mediaId: String, title: String, fileUrl: String): Result {
        val extension = inputData.getString(KEY_EXTENSION) ?: "mp3"
        val subtitle = inputData.getString(KEY_SUBTITLE) ?: ""
        val destFile = DownloadsHubManager.mediaFileForId(context, mediaId, extension)
        if (destFile.exists() && destFile.length() > 0) return Result.success()

        val startTime = System.currentTimeMillis()
        val result = DownloadsHubManager.downloadFileResumable(
            context = context,
            fileUrl = fileUrl,
            destFile = destFile
        ) { done, total ->
            val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
            val speedKbps = if (elapsedSec > 0) ((done / 1024) / elapsedSec).toLong() else 0L
            setProgress(
                workDataOf(
                    KEY_PROGRESS to if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f,
                    KEY_DOWNLOADED_BYTES to done,
                    KEY_TOTAL_BYTES to total
                )
            )
            DownloadsHubManager.reportWorkerProgress(KIND_MEDIA, mediaId, title, done, total, speedKbps)
        }

        return if (result.success) {
            DownloadsHubManager.addDownloadedMedia(
                context,
                DownloadedMediaItem(
                    id = mediaId,
                    title = title,
                    subtitle = subtitle,
                    localFilePath = destFile.absolutePath,
                    fileSizeBytes = destFile.length(),
                    sourceUrl = fileUrl
                )
            )
            DownloadsHubManager.clearWorkerProgress(KIND_MEDIA, mediaId)
            DownloadsHubManager.refreshStorageMetrics(context)
            Result.success()
        } else {
            DownloadsHubManager.reportWorkerError(KIND_MEDIA, mediaId, title, result.errorMessage ?: "فشل التنزيل", fileUrl, subtitle)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KIND_REEL = "reel"
        const val KIND_MEDIA = "media"

        const val KEY_KIND = "kind"
        const val KEY_ID = "id"
        const val KEY_TITLE = "title"
        const val KEY_URL = "url"
        const val KEY_THUMBNAIL_URL = "thumbnail_url"
        const val KEY_AUTHOR = "author"
        const val KEY_EXTENSION = "extension"
        const val KEY_SUBTITLE = "subtitle"

        /** مفاتيح تقدّم WorkManager ([setProgress]). */
        const val KEY_PROGRESS = "progress"
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"

        private const val MAX_ATTEMPTS = 3
        private const val MAX_REEL_DOWNLOAD_BYTES = 250L * 1024L * 1024L

        fun reelWorkName(reelId: String): String = "qabas_download_reel_$reelId"
        fun mediaWorkName(mediaId: String): String = "qabas_download_media_$mediaId"

        /**
         * قيود الشبكة: Wi-Fi فقط عند تفعيل خيار [DownloadsHubManager.isWifiOnlyDownloads]،
         * وإلا أي اتصال. شرط «البطارية غير منخفضة» غير مفروض عمداً (اختياري في
         * التقرير) حتى لا تُحجب التنزيلات الكبيرة عن أجهزة تعمل على البطارية.
         */
        private fun constraints(context: Context): Constraints {
            val networkType = if (DownloadsHubManager.isWifiOnlyDownloads(context)) {
                NetworkType.UNMETERED
            } else {
                NetworkType.CONNECTED
            }
            return Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()
        }

        fun enqueueReel(
            context: Context,
            reelId: String,
            title: String,
            videoUrl: String,
            thumbnailUrl: String? = null,
            author: String = "صانع محتوى"
        ): UUID {
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(constraints(context))
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                .setInputData(
                    workDataOf(
                        KEY_KIND to KIND_REEL,
                        KEY_ID to reelId,
                        KEY_TITLE to title,
                        KEY_URL to videoUrl,
                        KEY_THUMBNAIL_URL to thumbnailUrl,
                        KEY_AUTHOR to author
                    )
                )
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                reelWorkName(reelId),
                ExistingWorkPolicy.KEEP,
                request
            )
            return request.id
        }

        fun enqueueMedia(
            context: Context,
            mediaId: String,
            title: String,
            fileUrl: String,
            extension: String = "mp3",
            subtitle: String = ""
        ): UUID {
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(constraints(context))
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                .setInputData(
                    workDataOf(
                        KEY_KIND to KIND_MEDIA,
                        KEY_ID to mediaId,
                        KEY_TITLE to title,
                        KEY_URL to fileUrl,
                        KEY_EXTENSION to extension,
                        KEY_SUBTITLE to subtitle
                    )
                )
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                mediaWorkName(mediaId),
                ExistingWorkPolicy.KEEP,
                request
            )
            return request.id
        }

        fun cancelReel(context: Context, reelId: String) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(reelWorkName(reelId))
        }

        fun cancelMedia(context: Context, mediaId: String) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(mediaWorkName(mediaId))
        }
    }
}
