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
        QuranReciterInfo("ghamadi", "الشيخ سعد الغامدي", "Ghamadi_40kbps"),
        // المرحلة 2 — 8 قراء إضافيين: كل مجلد تحقق منه برأس HTTP على
        // EveryAyah (001001.mp3 + فحص موضعي داخل المصحف) فأعاد 200.
        // المجلدات التي أعادت 404 لم تُضف (العجمي، الحصري مجوّد،
        // عبد الباسط مجوّد، المعيقلي 64kbps).
        QuranReciterInfo("shatri", "الشيخ أبو بكر الشاطري", "Abu_Bakr_Ash-Shaatree_128kbps"),
        QuranReciterInfo("shuraym", "الشيخ سعود الشريم", "Saood_ash-Shuraym_128kbps"),
        QuranReciterInfo("hudhaify", "الشيخ علي الحذيفي", "Hudhaify_32kbps"),
        QuranReciterInfo("ayyoub", "الشيخ محمد أيوب", "Muhammad_Ayyoub_128kbps"),
        QuranReciterInfo("akhdar", "الشيخ إبراهيم الأخضر", "Ibrahim_Akhdar_32kbps"),
        QuranReciterInfo("basfar", "الشيخ عبد الله بصفر", "Abdullah_Basfar_32kbps"),
        QuranReciterInfo("jibreel", "الشيخ محمد جبريل", "Muhammad_Jibreel_128kbps"),
        QuranReciterInfo("parhizgar", "الشيخ شهريار برهيزكار", "Parhizgar_48kbps")
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

    /**
     * توقيت كلمة داخل آية — من Quran Foundation فقط، وحقيقي فقط عند وصوله.
     * حقل `segments` في استجابة QF هو مصفوفة [?, position, startMs, endMs]
     * (تحققنا منه على api.quran.com:‏ verses/by_chapter?audio=7&words=true
     * يعيد `audio: {url, segments: [[0,1,60,610], ...]}`)؛ position رقم
     * الكلمة داخل الآية بدءاً من 1.
     */
    data class QuranWordTiming(val position: Int, val startMs: Long, val endMs: Long)

    /**
     * آية صوتية مع توقيت كلماتها الاختياري. timings فارغة دائماً على مسار
     * EveryAyah (ملفات mp3 بلا بيانات توقيت) — التمييز على مستوى الآية
     * فقط هناك؛ أما تمييز الكلمة فيُفعَّل فقط عند وصول segments حقيقية.
     */
    data class QuranTimedAyahAudio(
        val surah: Int,
        val ayah: Int,
        val url: String,
        val timings: List<QuranWordTiming> = emptyList()
    )

    /**
     * روابط صفحة مع توقيت كلماتها من Quran Foundation (words=true).
     * يعمل فقط للعفاسي عند توفر مفاتيح QF؛ أي فشل (لا مفاتيح، خطأ شبكة،
     * أو نطاق pre-live المحدود بالفاتحة والبقرة) يعيد null فيعود المتصل
     * إلى getPageAudioUrls بلا توقيت — لا توقيت مخمّن ولا مزيف أبداً.
     */
    suspend fun getPageAudioWithTimings(context: Context, page: Int, reciterKey: String = "alafasy"): List<QuranTimedAyahAudio>? = withContext(Dispatchers.IO) {
        if (!isConfigured() || reciterKey != "alafasy") return@withContext null
        val token = ensureToken(context) ?: return@withContext null
        try {
            val url = "$API_BASE/verses/by_page/$page?words=true&audio=$RECITER_ALAFASY&mushaf=$MUSHAF_QCF_V2&per_page=all"
            val req = Request.Builder().url(url)
                .header("x-auth-token", token)
                .header("x-client-id", clientId())
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val root = JSONObject(resp.body?.string() ?: return@withContext null)
                val verses = root.optJSONArray("verses") ?: return@withContext null
                val result = ArrayList<QuranTimedAyahAudio>(verses.length())
                for (i in 0 until verses.length()) {
                    val verse = verses.optJSONObject(i) ?: continue
                    val key = verse.optString("verse_key") // "surah:ayah"
                    val parts = key.split(":")
                    if (parts.size != 2) continue
                    val surah = parts[0].toIntOrNull() ?: continue
                    val ayah = parts[1].toIntOrNull() ?: continue
                    val audio = verse.optJSONObject("audio") ?: continue
                    val rel = audio.optString("url")
                    if (rel.isBlank()) continue
                    val fullUrl = if (rel.startsWith("http")) rel else VERSE_AUDIO_BASE + rel
                    val timings = ArrayList<QuranWordTiming>()
                    val segments = audio.optJSONArray("segments")
                    if (segments != null) {
                        for (j in 0 until segments.length()) {
                            val seg = segments.optJSONArray(j) ?: continue
                            if (seg.length() < 4) continue
                            val position = seg.optInt(1, 0)
                            val start = seg.optLong(2, -1L)
                            val end = seg.optLong(3, -1L)
                            if (position >= 1 && start >= 0 && end > start) {
                                timings += QuranWordTiming(position, start, end)
                            }
                        }
                    }
                    result += QuranTimedAyahAudio(surah, ayah, fullUrl, timings)
                }
                if (result.isEmpty()) null else result
            }
        } catch (e: Exception) {
            Log.w(TAG, "QF timings failed, caller falls back: ${e.message}")
            null
        }
    }

    /**
     * روابط آيات سورة كاملة (لتكرار السورة) من EveryAyah مباشرة.
     * العدد من QuranDataProvider.surahs الحقيقي — لا رقم مخترع.
     */
    fun getSurahAudioUrls(surah: Int, reciterKey: String = "alafasy"): List<QuranTimedAyahAudio> {
        val count = QuranDataProvider.surahs.firstOrNull { it.id == surah }?.versesCount
            ?: return emptyList()
        if (surah !in 1..114 || count < 1) return emptyList()
        return (1..count).map { ayah ->
            QuranTimedAyahAudio(surah, ayah, getAyahAudioUrl(surah, ayah, reciterKey))
        }
    }

    /**
     * إن كانت الآية منزّلة في مخزن التنزيلات الموحّد يعيد مسار ملفها
     * المحلي، وإلا يعيد الرابط البعيد كما هو (تشغيل مختلط محلي/بعيد).
     */
    fun resolvePlaybackUri(context: Context, url: String): String {
        val local = runCatching {
            DownloadsHubManager.audioFileForUrl(context, url)
        }.getOrNull()
        return if (local != null && local.exists()) {
            android.net.Uri.fromFile(local).toString()
        } else {
            url
        }
    }
}

/**
 * أوضاع تكرار التلاوة (المرحلة 2): تُطبَّق على مشغّل ExoPlayer نفسه،
 * لا على مؤقّتات: الآية = REPEAT_MODE_ONE، والصفحة/السورة =
 * REPEAT_MODE_ALL على قائمة التشغيل المبنية لها.
 */
enum class QuranRepeatMode { OFF, VERSE, PAGE, SURAH }

/**
 * مشغّل قائمة آيات (Media3 ExoPlayer) — المرحلة 2.
 *
 * - التمييز على مستوى الآية حقيقي دائماً: كل آية عنصر وسائط مستقل،
 *   فيُبلَّغ onAyahChanged برقم العنصر الحالي عند كل انتقال فعلي.
 * - تمييز الكلمة يأتي من onAyahChanged + currentPositionMs() الذي
 *   تقرأه الشاشة وتطابقه مع QuranWordTiming — بلا أي تخمين زمني.
 * - خطأ التشغيل (شبكة، ملف) يُبلَّغ عبر onError منفصلاً عن onEnded:
 *   لا توقف صامت بعد اليوم.
 * - السرعة 0.5–2.0 (نطاق ExoPlayer/Sonic نفسه، كما في
 *   UnifiedVideoPlayerSettings)؛ مؤقت النوم يوقف التشغيل بعد المدة
 *   المختارة ويُبلَّغ كنهاية طبيعية.
 */
object QuranAudioPlayer {

    private var player: ExoPlayer? = null
    private var onEndedCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    private var onAyahChangedCallback: ((Int) -> Unit)? = null
    private var sleepHandler: android.os.Handler? = null
    private var sleepRunnable: Runnable? = null
    private var sleepDeadlineMs: Long = 0L
    private var currentSpeed: Float = 1f

    fun playAyahs(
        context: Context,
        ayahs: List<QuranAudioService.QuranTimedAyahAudio>,
        startIndex: Int = 0,
        repeatMode: QuranRepeatMode = QuranRepeatMode.OFF,
        speed: Float = 1f,
        onAyahChanged: ((Int) -> Unit)? = null,
        onEnded: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        // مؤقت النوم للجلسة لا للتشغيلة الواحدة: إيقاف المشغّل
        // الداخلي هنا كان يلغي المؤقت بصمت مع بقاء عدّاده التنازلي
        // ظاهراً في الشاشة؛ نحفظ المتبقي ونعيد تسليحه بعد بناء
        // المشغّل الجديد، فينجو المؤقت من تبديل الصفحة/السورة
        // وإعادة بناء القائمة عند تبديل التكرار.
        val sleepLeftMs = sleepRemainingMs()
        stop()
        if (ayahs.isEmpty()) return
        onEndedCallback = onEnded
        onErrorCallback = onError
        onAyahChangedCallback = onAyahChanged
        currentSpeed = speed.coerceIn(0.5f, 2f)
        val p = try {
            ExoPlayerFactory.createPlayer(context.applicationContext)
        } catch (_: Exception) {
            ExoPlayer.Builder(context.applicationContext).build()
        }
        val items = ayahs.map { ayah ->
            MediaItem.Builder()
                .setUri(QuranAudioService.resolvePlaybackUri(context, ayah.url))
                .setMediaId("${ayah.surah}:${ayah.ayah}")
                .build()
        }
        p.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
        p.repeatMode = when (repeatMode) {
            QuranRepeatMode.VERSE -> Player.REPEAT_MODE_ONE
            QuranRepeatMode.PAGE, QuranRepeatMode.SURAH -> Player.REPEAT_MODE_ALL
            QuranRepeatMode.OFF -> Player.REPEAT_MODE_OFF
        }
        p.setPlaybackSpeed(currentSpeed)
        p.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = p.currentMediaItemIndex
                if (index >= 0) onAyahChangedCallback?.invoke(index)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val ended = onEndedCallback
                    onEndedCallback = null
                    ended?.invoke()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.w("QuranAudioPlayer", "Audio playback error: ${error.message}")
                val message = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "تعذّر الاتصال بخادم التلاوة — تحقق من الإنترنت وحاول مجدداً."
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "ملف التلاوة غير متوفر لهذه الآية."
                    else -> "تعذّر تشغيل التلاوة — حاول مجدداً."
                }
                val report = onErrorCallback
                onErrorCallback = null
                report?.invoke(message)
            }
        })
        p.prepare()
        p.play()
        player = p
        if (sleepLeftMs != null && sleepLeftMs > 0) armSleepTimerMs(sleepLeftMs)
    }

    fun toggle(): Boolean {
        val p = player ?: return false
        if (p.isPlaying) p.pause() else p.play()
        return p.isPlaying
    }

    fun isPlaying(): Boolean = player?.isPlaying == true

    /** موضع التشغيل الحالي داخل الآية الجارية (مللي ثانية) — لتطابق الكلمة. */
    fun currentPositionMs(): Long = try {
        player?.currentPosition ?: 0L
    } catch (_: Exception) {
        0L
    }

    fun setSpeed(rate: Float) {
        currentSpeed = rate.coerceIn(0.5f, 2f)
        try {
            player?.setPlaybackSpeed(currentSpeed)
        } catch (_: Exception) {
        }
    }

    fun setRepeatMode(mode: QuranRepeatMode) {
        try {
            player?.repeatMode = when (mode) {
                QuranRepeatMode.VERSE -> Player.REPEAT_MODE_ONE
                QuranRepeatMode.PAGE, QuranRepeatMode.SURAH -> Player.REPEAT_MODE_ALL
                QuranRepeatMode.OFF -> Player.REPEAT_MODE_OFF
            }
        } catch (_: Exception) {
        }
    }

    /**
     * مؤقت النوم: يوقف التشغيل بعد minutes دقيقة ويُبلَّغ كنهاية طبيعية.
     * null أو <= 0 يلغي المؤقت الجاري.
     */
    fun setSleepTimer(minutes: Int?) {
        cancelSleepTimer()
        if (minutes == null || minutes <= 0) return
        armSleepTimerMs(minutes * 60_000L)
    }

    /** تسليح مؤقت النوم بمدة دقيقة بالمللي ثانية (للحفاظ عليه عبر إعادة التشغيل). */
    private fun armSleepTimerMs(ms: Long) {
        cancelSleepTimer()
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        sleepDeadlineMs = System.currentTimeMillis() + ms
        val runnable = Runnable {
            sleepDeadlineMs = 0L
            val ended = onEndedCallback
            stop() // يصفّر onEndedCallback — لذا نلتقطه قبله.
            ended?.invoke()
        }
        sleepHandler = handler
        sleepRunnable = runnable
        handler.postDelayed(runnable, ms)
    }

    /** المللي ثانية المتبقية على مؤقت النوم، أو null بلا مؤقت. */
    fun sleepRemainingMs(): Long? {
        if (sleepDeadlineMs == 0L) return null
        val remaining = sleepDeadlineMs - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0L
    }

    private fun cancelSleepTimer() {
        try {
            sleepRunnable?.let { sleepHandler?.removeCallbacks(it) }
        } catch (_: Exception) {
        }
        sleepHandler = null
        sleepRunnable = null
        sleepDeadlineMs = 0L
    }

    fun stop() {
        cancelSleepTimer()
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        onEndedCallback = null
        onErrorCallback = null
        onAyahChangedCallback = null
    }
}
