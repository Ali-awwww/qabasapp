package com.qabas.app

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioAttributes
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * مصنع مركزي لبناء مشغل الوسائط ExoPlayer مع ترويسة User-Agent حقيقية
 * وإعدادات مهلة آمنة تمنع أخطاء الرفض HTTP 403 Forbidden من خوادم الوسائط والسحابة.
 *
 * خيارات اختيارية (يفعّلها مشغّل الريلز فقط):
 * - useCache: قراءة/كتابة كاش الفيديو على القرص — لا تُعاد تنزيلات المشاهدات المتكررة
 * - useAudioFocus: طلب تركيز الصوت (إيقاف مؤقت عند مكالمة أو تشغيل تطبيق آخر)
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object ExoPlayerFactory {

    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 QabasMediaEngine/1.0"

    fun createHttpDataSourceFactory(): DefaultHttpDataSource.Factory {
        return DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(25_000)
            .setDefaultRequestProperties(
                mapOf(
                    "Accept" to "*/*",
                    "Accept-Encoding" to "identity;q=1, *;q=0",
                    "Accept-Language" to "ar,en-US;q=0.9,en;q=0.8",
                    "Referer" to "https://quran.com/",
                    "Sec-Fetch-Mode" to "no-cors"
                )
            )
    }

    fun createDataSourceFactory(context: Context): DefaultDataSource.Factory {
        val appContext = context.applicationContext
        return DefaultDataSource.Factory(appContext, createHttpDataSourceFactory())
    }

    fun createPlayer(
        context: Context,
        useCache: Boolean = false,
        useAudioFocus: Boolean = false
    ): ExoPlayer {
        val appContext = context.applicationContext

        val builder = ExoPlayer.Builder(appContext)

        // ذاكرة بفر متوازنة للمقاطع القصيرة: بداية سريعة (2.5 ثانية) وسقف معقول (30 ثانية)
        builder.setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 15_000,
                    /* maxBufferMs = */ 30_000,
                    /* bufferForPlaybackMs = */ 2_500,
                    /* bufferForPlaybackAfterRebufferMs = */ 5_000
                )
                .build()
        )

        if (useCache) {
            // قراءة من كاش قبس إن وُجد المقطع، والتنزيل من الشبكة (بنفس ترويسات HTTP)
            // مع الكتابة للكاش. FLAG_IGNORE_CACHE_ON_ERROR: أي خطأ في الكاش لا يُسقط
            // التشغيل بل يتجاوز إلى الشبكة مباشرة.
            val cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(VideoCacheService.getCache(appContext))
                .setUpstreamDataSourceFactory(createDataSourceFactory(appContext))
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            builder.setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
        } else {
            builder.setMediaSourceFactory(DefaultMediaSourceFactory(createDataSourceFactory(appContext)))
        }

        if (useAudioFocus) {
            // إعلان سمة الوسائط + طلب تركيز الصوت تلقائياً — سلوك المشغّلات الاحترافي
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            builder.setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
        }

        val player = builder.build()

        // إيقاف مؤقت تلقائي عند فصل السماعة (صوت صاخب مفاجئ) أو تغيّر جهاز الإخراج
        player.setHandleAudioBecomingNoisy(true)

        return player
    }
}
