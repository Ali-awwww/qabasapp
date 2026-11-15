package com.qabas.app

import android.content.Context
import android.util.Log
import com.qabas.app.SupabaseServices.ReelRow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** محرك الجلب المسبق الصامت لمقاطع الأثر. */
object SilentReelPrefetcher {
    private const val TAG = "SilentPrefetcher"
    private const val REFRESH_INTERVAL_MS = 15 * 60 * 1000L
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val RESERVED_DISK_BYTES = 32L * 1024L * 1024L

    private enum class DownloadOutcome { SAVED, STORAGE_LIMIT, FAILED }

    data class PrefetchState(
        val isSyncing: Boolean = false,
        val currentFileName: String = "",
        val downloadedBytes: Long = 0,
        val totalBytes: Long = 0,
        val speedBps: Long = 0,
        val storagePath: String = "",
        val lastMessage: String = "بانتظار بدء المزامنة",
        val downloadedCount: Int = 0,
        val totalStorageSize: Long = 0
    )

    private val _syncState = MutableStateFlow(PrefetchState())
    val syncState = _syncState.asStateFlow()

    // مستقل عن Composition/Activity حتى لا يتوقف التنزيل عند مغادرة شاشة الريلز.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private var syncJob: Job? = null

    /** يبدأ مرة واحدة لكل عملية تطبيق، بعد تهيئة حالة الشبكة ومصادقة Supabase. */
    fun startSync(context: Context) {
        if (syncJob?.isActive == true) return
        val appContext = context.applicationContext
        syncJob = appScope.launch {
            val refreshIntervals = flow {
                emit(Unit)
                while (currentCoroutineContext().isActive) {
                    delay(REFRESH_INTERVAL_MS)
                    emit(Unit)
                }
            }
            combine(
                UserPreferencesManager.networkStatusReady,
                UserPreferencesManager.networkStatus,
                refreshIntervals
            ) { ready, status, _ -> if (ready) status else null }
                .collectLatest { status ->
                    when (status) {
                        ConnectivityObserver.Status.Available -> performSync(appContext)
                        null -> updateIdleState(appContext, "جارٍ التحقق من حالة الاتصال")
                        else -> updateIdleState(appContext, "غير متصل؛ ستُستخدم المقاطع المحفوظة محلياً")
                    }
                }
        }
    }

    /** إجبار المزامنة الآن من النطاق نفسه ومن دون تداخل تنزيلين. */
    fun forceSync(context: Context) {
        val appContext = context.applicationContext
        appScope.launch { performSync(appContext) }
    }

    private fun updateIdleState(context: Context, message: String) {
        _syncState.value = _syncState.value.copy(
            isSyncing = false,
            lastMessage = message,
            downloadedCount = SilentReelStorageManager.getDownloadedCount(context),
            totalStorageSize = SilentReelStorageManager.getTotalStorageSize(context)
        )
    }

    private suspend fun performSync(context: Context) = syncMutex.withLock {
        performSyncLocked(context)
    }

    private suspend fun performSyncLocked(context: Context) {
        if (!ReelPreferencesManager.alAtharEnabled.value) {
            updateIdleState(context, "ميزة الأثر معطّلة")
            return
        }

        if (!UserPreferencesManager.networkStatusReady.value ||
            UserPreferencesManager.networkStatus.value != ConnectivityObserver.Status.Available
        ) {
            updateIdleState(context, "غير متصل؛ ستُستخدم المقاطع المحفوظة محلياً")
            return
        }

        _syncState.value = _syncState.value.copy(
            isSyncing = true,
            lastMessage = "جارٍ التحقق من مقاطع الأثر",
            downloadedCount = SilentReelStorageManager.getDownloadedCount(context),
            totalStorageSize = SilentReelStorageManager.getTotalStorageSize(context)
        )

        val remoteSeeds = withTimeoutOrNull(9_000L) {
            SupabaseServices.Profiles.getSilentSeedReels()
        } ?: run {
            _syncState.value = _syncState.value.copy(isSyncing = false, lastMessage = "تعذر جلب قائمة مقاطع الأثر")
            return
        }

        val eligibleSeeds = remoteSeeds
            .asSequence()
            .filter { SilentReelStorageManager.isEligibleForOfflinePlayback(it) }
            .distinctBy { it.id }
            .toList()

        // لا نحذف شيئاً عند فشل جلب السحابة، لكن بعد نتيجة ناجحة نزيل المقاطع المعطلة والمنتهية.
        SilentReelStorageManager.reconcileDownloadedReels(context, eligibleSeeds)
        val downloaded = eligibleSeeds
            .filter { SilentReelStorageManager.isReelDownloaded(context, it.id) }
            .toMutableList()
        SilentReelStorageManager.saveReelMetadata(context, downloaded)

        if (eligibleSeeds.isEmpty()) {
            updateIdleState(context, "لا توجد مقاطع أثر متاحة حالياً")
            return
        }

        val toDownload = eligibleSeeds.filterNot { SilentReelStorageManager.isReelDownloaded(context, it.id) }
        if (toDownload.isEmpty()) {
            updateIdleState(context, "كل مقاطع الأثر متاحة دون اتصال")
            return
        }

        var storageLimitedCount = 0
        var failedCount = 0
        for (reel in toDownload) {
            if (SilentReelStorageManager.getVideoStorageSize(context) >=
                SilentReelStorageManager.MAX_VIDEO_STORAGE_BYTES
            ) {
                storageLimitedCount += 1
                break
            }

            when (downloadVideo(context, reel)) {
                DownloadOutcome.SAVED -> {
                    downloaded.add(reel)
                    SilentReelStorageManager.saveReelMetadata(context, downloaded)
                }
                DownloadOutcome.STORAGE_LIMIT -> storageLimitedCount += 1
                DownloadOutcome.FAILED -> failedCount += 1
            }
        }

        val message = when {
            storageLimitedCount > 0 && failedCount > 0 -> "اكتملت المزامنة جزئياً؛ حد التخزين وبعض التنزيلات تعذّرت"
            storageLimitedCount > 0 -> "اكتملت المزامنة جزئياً؛ تم احترام حد تخزين الأثر"
            failedCount > 0 -> "اكتملت المزامنة مع تعذّر تنزيل بعض المقاطع"
            else -> "اكتملت مزامنة مقاطع الأثر"
        }
        updateIdleState(context, message)
    }

    private suspend fun downloadVideo(context: Context, reel: ReelRow): DownloadOutcome = withContext(Dispatchers.IO) {
        if (!SilentReelStorageManager.isValidReelId(reel.id)) return@withContext DownloadOutcome.FAILED
        val remoteUrl = runCatching { URL(reel.videoUrl) }.getOrNull()
            ?: return@withContext DownloadOutcome.FAILED
        if (!remoteUrl.protocol.equals("https", ignoreCase = true)) return@withContext DownloadOutcome.FAILED

        val destFile = SilentReelStorageManager.getLocalVideoFile(context, reel.id)
        val tempFile = File(destFile.parentFile, "${reel.id}.tmp")
        var connection: HttpURLConnection? = null

        try {
            tempFile.delete()
            val source = remoteUrl.openConnection() as? HttpURLConnection
                ?: return@withContext DownloadOutcome.FAILED
            connection = source
            source.connectTimeout = CONNECT_TIMEOUT_MS
            source.readTimeout = READ_TIMEOUT_MS
            source.instanceFollowRedirects = true
            source.connect()

            if (source.responseCode !in 200..299 ||
                !source.url.protocol.equals("https", ignoreCase = true)
            ) {
                throw IOException("Unexpected response while downloading an offline reel")
            }

            val totalSize = source.contentLength.toLong()
            val currentVideoBytes = SilentReelStorageManager.getVideoStorageSize(context)
            val freeDiskBytes = destFile.parentFile?.usableSpace ?: Long.MAX_VALUE
            if ((totalSize > 0 && currentVideoBytes + totalSize > SilentReelStorageManager.MAX_VIDEO_STORAGE_BYTES) ||
                (totalSize > 0 && freeDiskBytes < totalSize + RESERVED_DISK_BYTES)
            ) {
                return@withContext DownloadOutcome.STORAGE_LIMIT
            }

            _syncState.value = _syncState.value.copy(
                isSyncing = true,
                currentFileName = "${reel.id}.mp4",
                downloadedBytes = 0L,
                totalBytes = totalSize.coerceAtLeast(0L),
                speedBps = 0L,
                storagePath = destFile.absolutePath
            )

            var downloaded = 0L
            var exceedsLimit = false
            val startTime = System.currentTimeMillis()
            source.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break
                        val nextSize = currentVideoBytes + downloaded + bytesRead
                        val remainingDisk = (destFile.parentFile?.usableSpace ?: Long.MAX_VALUE) - RESERVED_DISK_BYTES
                        if (nextSize > SilentReelStorageManager.MAX_VIDEO_STORAGE_BYTES || remainingDisk < bytesRead) {
                            exceedsLimit = true
                            break
                        }

                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        val elapsedSeconds = ((System.currentTimeMillis() - startTime) / 1000L).coerceAtLeast(1L)
                        _syncState.value = _syncState.value.copy(
                            downloadedBytes = downloaded,
                            speedBps = downloaded / elapsedSeconds
                        )
                    }
                    output.flush()
                }
            }

            if (exceedsLimit) {
                tempFile.delete()
                return@withContext DownloadOutcome.STORAGE_LIMIT
            }
            if (downloaded <= 0L || (totalSize > 0 && downloaded != totalSize)) {
                throw IOException("Downloaded file was empty or incomplete")
            }

            if (destFile.exists() && !destFile.delete()) {
                throw IOException("Could not replace an invalid local reel file")
            }
            if (!tempFile.renameTo(destFile) || !destFile.isFile || destFile.length() != downloaded) {
                throw IOException("Could not finalize the downloaded reel file")
            }
            DownloadOutcome.SAVED
        } catch (e: CancellationException) {
            tempFile.delete()
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download offline reel ${reel.id}: ${e.message}")
            tempFile.delete()
            DownloadOutcome.FAILED
        } finally {
            connection?.disconnect()
        }
    }
}
