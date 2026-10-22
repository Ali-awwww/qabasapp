package com.qabas.app

import android.content.Context
import android.util.Log
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * خدمة كاش الفيديو المركزية (SimpleCache, 100MB, LRU)
 * - يُقرأ منها كاش الريلز عبر ExoPlayerFactory(useCache = true) — تُخزن المقاطع
 *   التي يشاهدها المستخدم فلا تُعاد تنزيلها في كل مرة.
 * - إدارة مرجعية آمنة: لا يُغلق الكاش أبداً وهناك مشغّلات نشطة تستخدمه؛
 *   طلبات الإغلاق (onLowMemory) تُؤجَّل حتى تحرر آخر مشغّل.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object VideoCacheService {
    private const val TAG = "VideoCacheService"
    private const val CACHE_DIR = "qabas_video_cache"
    private const val MAX_CACHE_BYTES = 100L * 1024 * 1024

    @Volatile
    private var cachedCache: SimpleCache? = null

    // عدد المشغّلات النشطة التي تستخدم الكاش حالياً
    private val activePlayers = AtomicInteger(0)

    // طلب إغلاق مؤجل (وصل أثناء وجود مشغّلات نشطة)
    @Volatile
    private var releasePending = false

    fun getCache(context: Context): SimpleCache {
        synchronized(this) {
            cachedCache?.let {
                return it
            }

            val cacheDir = File(context.applicationContext.cacheDir, CACHE_DIR)

            return runCatching {
                val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
                val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
                val cache = SimpleCache(cacheDir, evictor, databaseProvider)
                cache
            }.onFailure { Log.e(TAG, "Failed to initialize Video Cache", it) }
                .getOrThrow()
        }
    }

    /** يحتفظ بمرجع استخدام للكاش — يقابلها استدعاء releasePlayer() عند التخلص من المشغّل */
    fun retainPlayer() {
        activePlayers.incrementAndGet()
    }

    /** تحرير مرجع استخدام لمشغّل — إن صار العدد صفراً وكان الإغلاق مؤجلاً نُفّذ الآن */
    fun releasePlayer() {
        if (activePlayers.decrementAndGet() <= 0) {
            activePlayers.set(0)
            if (releasePending) {
                releasePending = false
                closeCacheInternal()
            }
        }
    }

    /**
     * إغلاق الكاش — آمن دائماً: إن كان هناك مشغّلات نشطة يُؤجل إلى تحرر آخر مشغّل،
     * وLRU يتكفل بتقليص المحتوى ذاتياً في الأثناء. لا يُستخدم إلا عند الضرورة (onLowMemory).
     */
    fun release() {
        if (activePlayers.get() == 0) {
            closeCacheInternal()
        } else {
            releasePending = true
            Log.i(TAG, "Cache release deferred — active cached players: ${activePlayers.get()}")
        }
    }

    private fun closeCacheInternal() {
        synchronized(this) {
            runCatching {
                cachedCache?.release()
            }.onSuccess {
                cachedCache = null
                releasePending = false
            }.onFailure {
                Log.e(TAG, "Failed to close cache", it)
            }
        }
    }
}
