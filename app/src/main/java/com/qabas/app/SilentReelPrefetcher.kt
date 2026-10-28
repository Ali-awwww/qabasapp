package com.qabas.app

import android.content.Context
import android.util.Log
import com.qabas.app.SupabaseServices.ReelRow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.URL

/**
 * ⚡ SilentReelPrefetcher
 * محرك الجلب المسبق الصامت. يقوم بفحص السحابة وتحميل الفيديوهات الناقصة.
 */
object SilentReelPrefetcher {
    private const val TAG = "SilentPrefetcher"
    
    data class PrefetchState(
        val isSyncing: Boolean = false,
        val currentFileName: String = "",
        val downloadedBytes: Long = 0,
        val totalBytes: Long = 0,
        val speedBps: Long = 0,
        val storagePath: String = "",
        val lastMessage: String = "Idle",
        val downloadedCount: Int = 0,
        val totalStorageSize: Long = 0
    )

    private val _syncState = MutableStateFlow(PrefetchState())
    val syncState = _syncState.asStateFlow()

    private var syncJob: Job? = null

    /** بدء المزامنة الدورية */
    fun startSync(context: Context, scope: CoroutineScope) {
        if (syncJob?.isActive == true) return
        
        syncJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                performSync(context)
                // فحص كل 15 دقيقة
                delay(15 * 60 * 1000)
            }
        }
    }

    /** إجبار المزامنة الآن */
    fun forceSync(context: Context) {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            performSync(context)
        }
    }

    private suspend fun performSync(context: Context) {
        _syncState.value = _syncState.value.copy(
            lastMessage = "Checking Cloud...",
            downloadedCount = SilentReelStorageManager.getDownloadedCount(context),
            totalStorageSize = SilentReelStorageManager.getTotalStorageSize(context)
        )
        
        val remoteSeeds = SupabaseServices.Profiles.getSilentSeedReels() ?: run {
            _syncState.value = _syncState.value.copy(lastMessage = "Cloud Fetch Failed")
            return
        }
        
        if (remoteSeeds.isEmpty()) {
            _syncState.value = _syncState.value.copy(lastMessage = "No Silent Seeds Found")
            return
        }

        val downloaded = SilentReelStorageManager.getDownloadedReels(context).toMutableList()
        val toDownload = remoteSeeds.filter { !SilentReelStorageManager.isReelDownloaded(context, it.id) }

        if (toDownload.isEmpty()) {
            _syncState.value = _syncState.value.copy(
                isSyncing = false, 
                lastMessage = "All Synced (${remoteSeeds.size} reels)",
                downloadedCount = remoteSeeds.size,
                totalStorageSize = SilentReelStorageManager.getTotalStorageSize(context)
            )
            return
        }

        _syncState.value = _syncState.value.copy(lastMessage = "Syncing ${toDownload.size} new reels...")

        toDownload.forEach { reel ->
            downloadVideo(context, reel)
            downloaded.add(reel)
            SilentReelStorageManager.saveReelMetadata(context, downloaded)
        }
        
        _syncState.value = _syncState.value.copy(
            isSyncing = false, 
            lastMessage = "Sync Complete",
            downloadedCount = SilentReelStorageManager.getDownloadedCount(context),
            totalStorageSize = SilentReelStorageManager.getTotalStorageSize(context)
        )
    }

    private suspend fun downloadVideo(context: Context, reel: ReelRow) {
        withContext(Dispatchers.IO) {
            val destFile = SilentReelStorageManager.getLocalVideoFile(context, reel.id)
            val tempFile = File(destFile.parent, "${reel.id}.tmp")
            
            try {
                val url = URL(reel.videoUrl)
                val connection = url.openConnection()
                connection.connect()
                
                val totalSize = connection.contentLength.toLong()
                var downloaded = 0L
                val startTime = System.currentTimeMillis()

                _syncState.value = PrefetchState(
                    isSyncing = true,
                    currentFileName = "${reel.id}.mp4",
                    totalBytes = totalSize,
                    storagePath = destFile.absolutePath
                )

                url.openStream().use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            
                            val elapsed = (System.currentTimeMillis() - startTime) / 1000
                            val speed = if (elapsed > 0) downloaded / elapsed else 0
                            
                            _syncState.value = _syncState.value.copy(
                                downloadedBytes = downloaded,
                                speedBps = speed
                            )
                        }
                    }
                }
                
                if (tempFile.exists() && tempFile.length() > 0) {
                    tempFile.renameTo(destFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download ${reel.id}: ${e.message}")
                if (tempFile.exists()) tempFile.delete()
            }
        }
    }
}
