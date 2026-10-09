package com.qabas.app

import android.content.Context
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * مصنع مركزي لبناء مشغل الوسائط ExoPlayer مع ترويسة User-Agent حقيقية
 * وإعدادات مهلة آمنة تمنع أخطاء الرفض HTTP 403 Forbidden من خوادم الوسائط والسحابة.
 */
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

    fun createPlayer(context: Context): ExoPlayer {
        val appContext = context.applicationContext
        val mediaSourceFactory = DefaultMediaSourceFactory(createDataSourceFactory(appContext))
        return ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }
}
