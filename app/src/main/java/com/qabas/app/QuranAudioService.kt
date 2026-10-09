package com.qabas.app

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class QuranReciterInfo(
    val id: String,
    val nameAr: String,
    val folder: String,
    val riwayah: String = "حفص عن عاصم"
)

/**
 * QuranAudioService.kt
 *
 * تلاوة صفحات وآيات المصحف الشريف بدقة متناهية وبأصوات كبار القراء.
 * - دعم البث الفوري المباشر عبر EveryAyah CDN و Quran.com CDN بدون أي مفاتيح أو تعقيدات.
 * - دعم الربط المتقدم مع Quran Foundation API عند توفر المفاتيح.
 */
object QuranAudioService {

    private const val TAG = "QuranAudioService"
    private const val TOKEN_URL = "https://oauth2.quran.foundation/oauth2/token"
    private const val API_BASE = "https://apis.quran.foundation/content/api/v4"
    private const val VERSE_AUDIO_BASE = "https://verses.quran.foundation/"
    private const val RECITER_ALAFASY = 7
    private const val MUSHAF_QCF_V2 = 1

    val availableReciters = listOf(
        QuranReciterInfo("alafasy", "الشيخ مشاري راشد العفاسي", "Alafasy_128kbps"),
        QuranReciterInfo("abdulbasit", "الشيخ عبد الباسط عبد الصمد (مرتل)", "Abdul_Basit_Murattal_192kbps"),
        QuranReciterInfo("minshawy", "الشيخ محمد صديق المنشاوي (مرتل)", "Minshawy_Murattal_128kbps"),
        QuranReciterInfo("husary", "الشيخ محمود خليل الحصري", "Husary_128kbps"),
        QuranReciterInfo("muaiqly", "الشيخ ماهر المعيقلي", "MaherAlMuaiqly128kbps"),
        QuranReciterInfo("ghamadi", "الشيخ سعد الغامدي", "Ghamadi_40kbps")
    )

    private val client = OkHttpClient()

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var tokenExpiryMs: Long = 0L

    fun getReciterInfo(id: String): QuranReciterInfo {
        return availableReciters.find { it.id == id } ?: availableReciters.first()
    }

    fun isConfigured(): Boolean {
        val id = buildConfigKey("QF_CLIENT_ID")
        val secret = buildConfigKey("QF_CLIENT_SECRET")
        return !id.isNullOrBlank() && id != "your_key" &&
            !secret.isNullOrBlank() && secret != "your_key"
    }

    private fun buildConfigKey(name: String): String? = try {
        BuildConfig::class.java.getField(name).get(null) as? String
    } catch (_: Exception) {
        null
    }

    private fun clientId(): String = buildConfigKey("QF_CLIENT_ID") ?: ""
    private fun clientSecret(): String = buildConfigKey("QF_CLIENT_SECRET") ?: ""

    /**
     * توكن OAuth2 اختياري لـ Quran Foundation
     */
    private suspend fun ensureToken(context: Context): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedToken != null && now < tokenExpiryMs - 60_000) return@withContext cachedToken
        val t0 = System.nanoTime()
        var ok = false
        try {
            val body = FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("scope", "content")
                .build()
            var token = requestToken(body, Credentials.basic(clientId(), clientSecret()))
            if (token == null) {
                val body2 = FormBody.Builder()
                    .add("grant_type", "client_credentials")
                    .add("scope", "content")
                    .add("client_id", clientId())
                    .add("client_secret", clientSecret())
                    .build()
                token = requestToken(body2, null)
            }
            ok = token != null
            return@withContext token
        } catch (e: Exception) {
            Log.w(TAG, "Token failed: ${e.message}")
            return@withContext null
        } finally {
            try {
                ApiUsageTracker.recordCall(context, "Quran Foundation", (System.nanoTime() - t0) / 1_000_000, ok)
            } catch (_: Exception) {
            }
        }
    }

    private fun requestToken(body: FormBody, authHeader: String?): String? {
        val builder = Request.Builder().url(TOKEN_URL).post(body)
        if (authHeader != null) builder.header("Authorization", authHeader)
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val json = JSONObject(resp.body?.string() ?: return null)
            val token = json.optString("access_token")
            if (token.isBlank()) return null
            val expiresIn = json.optLong("expires_in", 3600)
            cachedToken = token
            tokenExpiryMs = System.currentTimeMillis() + expiresIn * 1000
            return token
        }
    }

    /**
     * بناء رابط الآية الصوتي مباشرة
     */
    fun getAyahAudioUrl(surah: Int, ayah: Int, reciterKey: String = "alafasy"): String {
        val reciter = getReciterInfo(reciterKey)
        val s = String.format(java.util.Locale.US, "%03d", surah)
        val a = String.format(java.util.Locale.US, "%03d", ayah)
        return "https://everyayah.com/data/${reciter.folder}/$s$a.mp3"
    }

    /**
     * روابط mp3 لآيات صفحة معينة بالترتيب.
     * تبدأ بالبث المباشر الفوري عبر EveryAyah CDN لكل قارئ، مع دعم QF عند التفعيل.
     */
    suspend fun getPageAudioUrls(context: Context, page: Int, reciterKey: String = "alafasy"): List<String> = withContext(Dispatchers.IO) {
        // إذا كان هناك مفاتيح رسمية لـ QF والقارئ هو العفاسي، نجرب الـ API أولاً
        if (isConfigured() && reciterKey == "alafasy") {
            val token = ensureToken(context)
            if (token != null) {
                try {
                    val urls = ArrayList<String>()
                    val url = "$API_BASE/verses/by_page/$page?words=false&audio=$RECITER_ALAFASY&mushaf=$MUSHAF_QCF_V2&per_page=all"
                    val req = Request.Builder().url(url)
                        .header("x-auth-token", token)
                        .header("x-client-id", clientId())
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val root = JSONObject(resp.body?.string() ?: "")
                            val verses = root.optJSONArray("verses")
                            if (verses != null && verses.length() > 0) {
                                for (i in 0 until verses.length()) {
                                    val audio = verses.optJSONObject(i)?.optJSONObject("audio") ?: continue
                                    val rel = audio.optString("url")
                                    if (rel.isNotBlank()) {
                                        urls.add(if (rel.startsWith("http")) rel else VERSE_AUDIO_BASE + rel)
                                    }
                                }
                                if (urls.isNotEmpty()) return@withContext urls
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "QF fallback to EveryAyah CDN: ${e.message}")
                }
            }
        }

        // الطريقة المباشرة الموثوقة: توليد روابط الآيات من بيانات الصفحة
        val refs = MushafPageData.getPageRefs(page)
        if (refs.isNotEmpty()) {
            return@withContext refs.map { (surah, ayah) ->
                getAyahAudioUrl(surah, ayah, reciterKey)
            }
        }

        return@withContext emptyList()
    }
}

/**
 * مشغّل قائمة آيات الصفحة (Media3 ExoPlayer).
 */
object QuranAudioPlayer {

    private var player: ExoPlayer? = null
    private var onCompletionCallback: (() -> Unit)? = null

    fun playUrls(context: Context, urls: List<String>, onEnded: (() -> Unit)? = null) {
        stop()
        if (urls.isEmpty()) return
        onCompletionCallback = onEnded
        val p = try {
            ExoPlayerFactory.createPlayer(context.applicationContext)
        } catch (_: Exception) {
            ExoPlayer.Builder(context.applicationContext).build()
        }
        p.setMediaItems(urls.map { MediaItem.fromUri(it) })
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onCompletionCallback?.invoke()
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.w("QuranAudioPlayer", "Audio playback error handled: ${error.message}")
            }
        })
        p.prepare()
        p.play()
        player = p
    }

    fun toggle(): Boolean {
        val p = player ?: return false
        if (p.isPlaying) p.pause() else p.play()
        return p.isPlaying
    }

    fun isPlaying(): Boolean = player?.isPlaying == true

    fun hasPlayer(): Boolean = player != null

    fun stop() {
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        onCompletionCallback = null
    }
}
