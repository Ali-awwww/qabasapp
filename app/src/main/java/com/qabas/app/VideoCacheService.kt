package com.qabas.app

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object VideoCacheService {
    @Volatile
    private var cache: SimpleCache? = null

    fun getCache(context: Context): SimpleCache {
        return cache ?: synchronized(this) {
            cache ?: run {
                val cacheDir = File(context.applicationContext.cacheDir, "media_cache")
                val evictor = LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024) // 100MB
                val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
                SimpleCache(cacheDir, evictor, databaseProvider).also { cache = it }
            }
        }
    }

    fun release() {
        synchronized(this) {
            try {
                cache?.release()
            } catch (e: Exception) {
                android.util.Log.w("VideoCacheService", "Error releasing cache: ${e.message}")
            } finally {
                cache = null
            }
        }
    }

    fun close() = release()
}
