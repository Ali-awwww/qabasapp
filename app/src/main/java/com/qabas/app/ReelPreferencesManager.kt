package com.qabas.app

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

/**
 * عنصر في سجل الريلز (مشاهدات أو محفوظات أو معجبات)
 */
@Immutable
data class ReelHistoryItem(
    val reelId: String,
    val title: String,
    val userName: String,
    val userAvatar: String?,
    val videoUrl: String,
    val timestamp: Long = System.currentTimeMillis(),
    val watchedDurationSec: Int = 0,
    val category: String = "عام",
    val thumbnailUrl: String? = null
)

/**
 * إحصائيات قسم الريلز للمستخدم
 */
@Immutable
data class ReelUserStats(
    val totalWatched: Int = 0,
    val totalWatchSeconds: Long = 0L,
    val totalLikes: Int = 0,
    val totalSaved: Int = 0,
    val totalShares: Int = 0
)

@Immutable
data class ReelActivitySnapshot(
    val watchHistory: List<ReelHistoryItem> = emptyList(),
    val savedReels: List<ReelHistoryItem> = emptyList(),
    val likedReels: List<ReelHistoryItem> = emptyList(),
    val totalWatchSeconds: Long = 0L,
    val watchHistoryCount: Int = watchHistory.size,
    val savedReelsCount: Int = savedReels.size,
    val likedReelsCount: Int = likedReels.size,
    val watchHistoryTruncated: Boolean = false,
    val savedReelsTruncated: Boolean = false,
    val likedReelsTruncated: Boolean = false
)

/**
 * مدير إعدادات وسجل قسم الريلز لتطبيق خادم المسلم
 * يوفر تحكماً كاملاً بالتشغيل، التمرير، توفير البيانات، السجل، والمحفوظات.
 */
object ReelPreferencesManager {

    private const val PREFS_NAME = "qabas_reels_preferences"

    // Keys
    private const val KEY_AUTOPLAY_WIFI_ONLY = "reels_autoplay_wifi_only"
    private const val KEY_START_MUTED = "reels_start_muted"
    private const val KEY_DATA_SAVER = "reels_data_saver"
    private const val KEY_HAPTIC_FEEDBACK = "reels_haptic_feedback"
    private const val KEY_AUTO_SCROLL = "reels_auto_scroll"
    private const val KEY_PLAYBACK_SPEED = "reels_playback_speed"
    private const val KEY_LAST_VOLUME = "reels_last_volume"
    private const val KEY_RESUME_POSITIONS = "reels_resume_positions_json"
    private const val KEY_SHOW_CAPTIONS = "reels_show_captions"
    private const val KEY_DOUBLE_TAP_ACTION = "reels_double_tap_action" // "LIKE", "SAVE", "PAUSE"
    private const val KEY_WATCH_HISTORY = "reels_watch_history_json"
    private const val KEY_SAVED_REELS = "reels_saved_reels_json"
    private const val KEY_LIKED_REELS = "reels_liked_reels_json"
    private const val KEY_STATS = "reels_stats_json"
    private const val KEY_CACHED_REELS = "reels_cached_reels_json"
    private const val KEY_AL_ATHAR_ENABLED = "reels_al_athar_enabled"
    private const val KEY_HIDDEN_REELS = "reels_hidden_ids"

    // StateFlows for UI observation
    private val _autoplayWifiOnly = MutableStateFlow(false)
    val autoplayWifiOnly: StateFlow<Boolean> = _autoplayWifiOnly.asStateFlow()

    private val _startMuted = MutableStateFlow(false)
    val startMuted: StateFlow<Boolean> = _startMuted.asStateFlow()

    private val _dataSaver = MutableStateFlow(false)
    val dataSaver: StateFlow<Boolean> = _dataSaver.asStateFlow()

    private val _hapticFeedback = MutableStateFlow(true)
    val hapticFeedback: StateFlow<Boolean> = _hapticFeedback.asStateFlow()

    private val _autoScrollOnFinish = MutableStateFlow(false)
    val autoScrollOnFinish: StateFlow<Boolean> = _autoScrollOnFinish.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // مستوى الصوت الأخير غير الصفري (ق76): الكتم يستعيده بدل القفز
    // إلى 100%، ويُحفظ عند مغادرة الشاشة فيبقى بين الجلسات.
    private val _lastVolume = MutableStateFlow(1.0f)
    val lastVolume: StateFlow<Float> = _lastVolume.asStateFlow()

    // مواضع استئناف المشاهدة لكل ريل (ق165): كانت خريطة ذاكرة
    // فقط في شاشة الريلز، فتضيع بمغادرة الشاشة ويعيد كل ريل من
    // البداية؛ تُحفظ الآن في التفضيلات وتُستعاد عند الدخول.
    private const val MAX_RESUME_POSITIONS = 50
    private val resumePositions = LinkedHashMap<String, Long>()

    private val _showCaptions = MutableStateFlow(true)
    val showCaptions: StateFlow<Boolean> = _showCaptions.asStateFlow()


    private val _doubleTapAction = MutableStateFlow("LIKE")
    val doubleTapAction: StateFlow<String> = _doubleTapAction.asStateFlow()

    private val _alAtharEnabled = MutableStateFlow(true) // Default to true as it's a key feature
    val alAtharEnabled: StateFlow<Boolean> = _alAtharEnabled.asStateFlow()

    // History and Lists
    private val _watchHistory = MutableStateFlow<List<ReelHistoryItem>>(emptyList())
    val watchHistory: StateFlow<List<ReelHistoryItem>> = _watchHistory.asStateFlow()

    private val _savedReels = MutableStateFlow<List<ReelHistoryItem>>(emptyList())
    val savedReels: StateFlow<List<ReelHistoryItem>> = _savedReels.asStateFlow()

    private val _likedReels = MutableStateFlow<List<ReelHistoryItem>>(emptyList())
    val likedReels: StateFlow<List<ReelHistoryItem>> = _likedReels.asStateFlow()

    private val _userStats = MutableStateFlow(ReelUserStats())
    val userStats: StateFlow<ReelUserStats> = _userStats.asStateFlow()

    private var isInitialized = false

    /**
     * تهيئة الإعدادات وقراءة السجل من التخزين المحلي
     */
    fun init(context: Context) {
        if (isInitialized) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        _autoplayWifiOnly.value = prefs.getBoolean(KEY_AUTOPLAY_WIFI_ONLY, false)
        _startMuted.value = prefs.getBoolean(KEY_START_MUTED, false)
        _dataSaver.value = prefs.getBoolean(KEY_DATA_SAVER, false)
        _hapticFeedback.value = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true)
        _autoScrollOnFinish.value = prefs.getBoolean(KEY_AUTO_SCROLL, false)
        _playbackSpeed.value = prefs.getFloat(KEY_PLAYBACK_SPEED, 1.0f)
        _lastVolume.value = prefs.getFloat(KEY_LAST_VOLUME, 1.0f).coerceIn(0.05f, 1.0f)
        runCatching {
            val raw = prefs.getString(KEY_RESUME_POSITIONS, "{}") ?: "{}"
            val obj = JSONObject(raw)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val pos = obj.optLong(id, 0L)
                if (id.isNotBlank() && pos > 0L) resumePositions[id] = pos
            }
        }
        _showCaptions.value = prefs.getBoolean(KEY_SHOW_CAPTIONS, true)
        _doubleTapAction.value = prefs.getString(KEY_DOUBLE_TAP_ACTION, "LIKE") ?: "LIKE"
        _alAtharEnabled.value = prefs.getBoolean(KEY_AL_ATHAR_ENABLED, true)

        // Load History safely & purge any legacy mock/demo data
        fun isMock(item: ReelHistoryItem): Boolean {
            val id = item.reelId.lowercase()
            val url = item.videoUrl.lowercase()
            return id.startsWith("authentic_quran") || id.startsWith("mock_") || id.startsWith("demo_") || url.contains("mixkit.co")
        }
        val cleanHistory = parseItemsJson(prefs.getString(KEY_WATCH_HISTORY, "[]") ?: "[]").filterNot { isMock(it) }
        val cleanSaved = parseItemsJson(prefs.getString(KEY_SAVED_REELS, "[]") ?: "[]").filterNot { isMock(it) }
        val cleanLiked = parseItemsJson(prefs.getString(KEY_LIKED_REELS, "[]") ?: "[]").filterNot { isMock(it) }

        _watchHistory.value = cleanHistory
        _savedReels.value = cleanSaved
        _likedReels.value = cleanLiked
        _userStats.value = parseStatsJson(prefs.getString(KEY_STATS, "{}") ?: "{}")

        prefs.edit()
            .putString(KEY_WATCH_HISTORY, serializeItemsJson(cleanHistory))
            .putString(KEY_SAVED_REELS, serializeItemsJson(cleanSaved))
            .putString(KEY_LIKED_REELS, serializeItemsJson(cleanLiked))
            .apply()

        isInitialized = true

        // Enforce storage quotas in background
        QabasCrashGuard.rootScope.launch {
            ReelSafetyGuardrails.enforceStorageQuotas(context)
        }
    }

    // --- Settings Setters ---

    fun setAutoplayWifiOnly(context: Context, enabled: Boolean) {
        _autoplayWifiOnly.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTOPLAY_WIFI_ONLY, enabled).apply()
    }

    fun setStartMuted(context: Context, enabled: Boolean) {
        _startMuted.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_START_MUTED, enabled).apply()
    }

    fun setDataSaver(context: Context, enabled: Boolean) {
        _dataSaver.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_DATA_SAVER, enabled).apply()
    }

    fun setHapticFeedback(context: Context, enabled: Boolean) {
        _hapticFeedback.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_HAPTIC_FEEDBACK, enabled).apply()
    }

    fun setAutoScrollOnFinish(context: Context, enabled: Boolean) {
        _autoScrollOnFinish.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTO_SCROLL, enabled).apply()
    }

    fun setPlaybackSpeed(context: Context, speed: Float) {
        _playbackSpeed.value = speed
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_PLAYBACK_SPEED, speed).apply()
    }

    fun setLastVolume(context: Context, volume: Float) {
        val safe = volume.coerceIn(0.05f, 1.0f)
        _lastVolume.value = safe
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_LAST_VOLUME, safe).apply()
    }

    /**
     * ق165: مواضع الاستئناف المحفوظة (نسخة) — تُزرع بها خريطة
     * الشاشة عند الدخول، فيستأنف كل ريل من موضعه لا من البداية.
     */
    fun getResumePositions(context: Context): Map<String, Long> {
        init(context)
        return resumePositions.toMap()
    }

    /**
     * ق165: حفظ مواضع الاستئناف عند مغادرة الشاشة (مرة واحدة،
     * لا مع كل تحديث 500ms أثناء التشغيل). تُحذف المواضع دون
     * ثانيتين (لا قيمة لاستئنافها)، ويُحتفظ بأحدث 50 ريلاً فقط.
     */
    fun saveResumePositions(context: Context, positions: Map<String, Long>) {
        init(context)
        val pruned = positions.entries
            .filter { it.key.isNotBlank() && it.value >= 2000L }
            .takeLast(MAX_RESUME_POSITIONS)
        resumePositions.clear()
        pruned.forEach { resumePositions[it.key] = it.value }
        val obj = JSONObject()
        pruned.forEach { obj.put(it.key, it.value) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_RESUME_POSITIONS, obj.toString()).apply()
    }

    fun setShowCaptions(context: Context, enabled: Boolean) {
        _showCaptions.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SHOW_CAPTIONS, enabled).apply()
    }

    fun setDoubleTapAction(context: Context, action: String) {
        _doubleTapAction.value = action
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_DOUBLE_TAP_ACTION, action).apply()
    }

    fun setAlAtharEnabled(context: Context, enabled: Boolean) {
        _alAtharEnabled.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AL_ATHAR_ENABLED, enabled).apply()
    }

    fun getHiddenReelIds(context: Context): Set<String> = context
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getStringSet(KEY_HIDDEN_REELS, emptySet())?.toSet() ?: emptySet()

    fun hideReel(context: Context, reelId: String): Set<String> {
        val hidden = getHiddenReelIds(context) + reelId
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_HIDDEN_REELS, hidden).apply()
        return hidden
    }

    fun clearHiddenReels(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_HIDDEN_REELS).apply()
    }

    // --- Watch History Operations ---
    // (حُذف recordWatch: كان مستدعاه الوحيد نشرَ الريلز، فيسجّل كل نشر
    // كمشاهدة وهمية 10 ثوانٍ تضخّم الإحصائيات؛ وسجل المشاهدة الحقيقي
    // مصدره السحابة عبر replaceActivityFromCloud.)

    fun removeFromWatchHistory(context: Context, reelId: String) {
        init(context)
        val removedSeconds = _watchHistory.value
            .filter { it.reelId == reelId }
            .sumOf { it.watchedDurationSec.coerceAtLeast(0).toLong() }
        val currentList = _watchHistory.value.filterNot { it.reelId == reelId }
        _watchHistory.value = currentList
        val stats = _userStats.value.copy(
            totalWatched = currentList.size,
            totalWatchSeconds = (_userStats.value.totalWatchSeconds - removedSeconds).coerceAtLeast(0L)
        )
        _userStats.value = stats
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_WATCH_HISTORY, serializeItemsJson(currentList)).apply()
        saveStats(context, stats)
    }

    // ق156: مسح نشاط الريلز الخاص بالحساب (مشاهدات/محفوظات/
    // إعجابات وإحصائياتها) عند حذف الحساب — كان «مسح بيانات
    // الحساب محلياً» يتركها في qabas_reels_preferences، فيبقى
    // نشاط حساب محذوف على الجهاز وقد يظهر لحساب آخر يدخل بعده
    // قبل أول مزامنة سحابية. المشاركات جهازية فتبقى، والمقاطع
    // المخفية والمخزن العام ليسا نشاط حساب فيبقيان.
    fun clearAccountActivity(context: Context) {
        init(context)
        _watchHistory.value = emptyList()
        _savedReels.value = emptyList()
        _likedReels.value = emptyList()
        resumePositions.clear()
        val stats = _userStats.value.copy(
            totalWatched = 0,
            totalWatchSeconds = 0L,
            totalLikes = 0,
            totalSaved = 0
        )
        _userStats.value = stats
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_WATCH_HISTORY, "[]")
            .putString(KEY_SAVED_REELS, "[]")
            .putString(KEY_LIKED_REELS, "[]")
            .remove(KEY_RESUME_POSITIONS)
            .apply()
        saveStats(context, stats)
    }

    fun clearWatchHistory(context: Context) {
        init(context)
        _watchHistory.value = emptyList()
        val stats = _userStats.value.copy(totalWatched = 0, totalWatchSeconds = 0L)
        _userStats.value = stats
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_WATCH_HISTORY, "[]").apply()
        saveStats(context, stats)
    }

    /** Replace the cached activity with the rows returned by Supabase for the signed-in UID. */
    fun replaceActivityFromCloud(context: Context, snapshot: ReelActivitySnapshot) {
        init(context)
        val history = snapshot.watchHistory.take(ReelSafetyGuardrails.MAX_WATCH_HISTORY_COUNT)
        val saved = snapshot.savedReels.take(ReelSafetyGuardrails.MAX_SAVED_REELS_COUNT)
        val liked = snapshot.likedReels.take(ReelSafetyGuardrails.MAX_LIKED_REELS_COUNT)

        _watchHistory.value = history
        _savedReels.value = saved
        _likedReels.value = liked

        val stats = ReelUserStats(
            totalWatched = snapshot.watchHistoryCount.coerceAtLeast(history.size),
            totalWatchSeconds = snapshot.totalWatchSeconds.coerceAtLeast(0L),
            totalLikes = snapshot.likedReelsCount.coerceAtLeast(liked.size),
            totalSaved = snapshot.savedReelsCount.coerceAtLeast(saved.size),
            // Share events are currently device-local; keep that count separate from cloud totals.
            totalShares = _userStats.value.totalShares
        )
        _userStats.value = stats

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_WATCH_HISTORY, serializeItemsJson(history))
            .putString(KEY_SAVED_REELS, serializeItemsJson(saved))
            .putString(KEY_LIKED_REELS, serializeItemsJson(liked))
            .apply()
        saveStats(context, stats)
    }

    // --- Saved / Bookmarks Operations ---

    fun isSaved(reelId: String): Boolean {
        return _savedReels.value.any { it.reelId == reelId }
    }

    // ق131: إزالة محلية فورية (محفوظة) من المحفوظات — كان نظيرها
    // موجوداً للسجل (removeFromWatchHistory) وحده، فإزالة محفوظ
    // من الورقة كانت تعتمد على إعادة جلب لاحقة، وإن فشلت عاد
    // العنصر للظهور رغم نجاح حذفه في السحابة.
    fun removeFromSavedReels(context: Context, reelId: String) {
        init(context)
        val currentList = _savedReels.value.filterNot { it.reelId == reelId }
        _savedReels.value = currentList
        val stats = _userStats.value.copy(totalSaved = currentList.size)
        _userStats.value = stats
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_SAVED_REELS, serializeItemsJson(currentList)).apply()
        saveStats(context, stats)
    }

    // --- Liked Reels Operations ---

    fun isLiked(reelId: String): Boolean {
        return _likedReels.value.any { it.reelId == reelId }
    }

    // ق131: نظير removeFromSavedReels للإعجابات — للسبب نفسه.
    fun removeFromLikedReels(context: Context, reelId: String) {
        init(context)
        val currentList = _likedReels.value.filterNot { it.reelId == reelId }
        _likedReels.value = currentList
        val stats = _userStats.value.copy(totalLikes = currentList.size)
        _userStats.value = stats
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_LIKED_REELS, serializeItemsJson(currentList)).apply()
        saveStats(context, stats)
    }

    fun recordShare(context: Context) {
        if (!ReelSafetyGuardrails.canRecordShare()) return
        init(context)
        val curStats = _userStats.value
        val updatedStats = curStats.copy(totalShares = curStats.totalShares + 1)
        _userStats.value = updatedStats
        saveStats(context, updatedStats)
    }

    // --- Cache Clearing Utility ---

    /**
     * مسح كاش الفيديو المحفوظ على الجهاز وإرجاع الحجم المحرر
     */
    fun clearVideoCache(context: Context): String {
        return runCatching {
            var bytesFreed = 0L
            val cacheDir = context.cacheDir
            if (cacheDir != null && cacheDir.exists()) {
                val mediaFiles = cacheDir.listFiles() ?: emptyArray()
                for (file in mediaFiles) {
                    if (file.name.contains("exo", ignoreCase = true) ||
                        file.name.endsWith(".mp4") ||
                        file.name.contains("media")
                    ) {
                        bytesFreed += file.length()
                        file.delete()
                    }
                }
            }
            val mb = bytesFreed / (1024.0 * 1024.0)
            if (mb > 0.05) {
                String.format(java.util.Locale.US, "%.1f MB", mb)
            } else {
                "تم مسح الذاكرة بنجاح"
            }
        }.getOrDefault("تم مسح الذاكرة المؤقتة")
    }

    /**
     * تشغيل اهتزاز خفيف عند التفاعل
     */
    fun triggerHaptic(context: Context) {
        if (!_hapticFeedback.value) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(35)
            }
        }
    }

    // --- JSON Helpers ---

    private fun serializeItemsJson(items: List<ReelHistoryItem>): String {
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("id", item.reelId)
                put("title", item.title)
                put("user_name", item.userName)
                put("user_avatar", item.userAvatar ?: "")
                put("video_url", item.videoUrl)
                put("thumbnail_url", item.thumbnailUrl ?: "")
                put("timestamp", item.timestamp)
                put("duration", item.watchedDurationSec)
                put("category", item.category)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun parseItemsJson(jsonStr: String): List<ReelHistoryItem> {
        val list = mutableListOf<ReelHistoryItem>()
        runCatching {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ReelHistoryItem(
                        reelId = obj.optString("id", ""),
                        title = obj.optString("title", "ريلز خادم المسلم"),
                        userName = obj.optString("user_name", "صانع محتوى"),
                        userAvatar = obj.optString("user_avatar", "").ifBlank { null },
                        videoUrl = obj.optString("video_url", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        thumbnailUrl = obj.optString("thumbnail_url", "").ifBlank { null },
                        watchedDurationSec = obj.optInt("duration", 0),
                        category = obj.optString("category", "عام")
                    )
                )
            }
        }
        return list
    }

    private fun saveStats(context: Context, stats: ReelUserStats) {
        val obj = JSONObject().apply {
            put("watched", stats.totalWatched)
            put("seconds", stats.totalWatchSeconds)
            put("likes", stats.totalLikes)
            put("saved", stats.totalSaved)
            put("shares", stats.totalShares)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_STATS, obj.toString()).apply()
    }

    private fun parseStatsJson(jsonStr: String): ReelUserStats {
        return runCatching {
            val obj = JSONObject(jsonStr)
            ReelUserStats(
                totalWatched = obj.optInt("watched", 0),
                totalWatchSeconds = obj.optLong("seconds", 0L),
                totalLikes = obj.optInt("likes", 0),
                totalSaved = obj.optInt("saved", 0),
                totalShares = obj.optInt("shares", 0)
            )
        }.getOrDefault(ReelUserStats())
    }

    private val reelsJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * حفظ قائمة الريلز في الكاش المحلي للعمل بسلاسة في حال انقطاع الإنترنت (Offline-First)
     */
    fun saveCachedReels(context: Context, reels: List<SupabaseServices.ReelRow>) {
        if (reels.isEmpty()) return
        runCatching {
            val jsonStr = reelsJson.encodeToString(reels)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CACHED_REELS, jsonStr)
                .apply()
        }
    }

    /**
     * استرجاع قائمة الريلز المحفوظة في الكاش المحلي عند انقطاع الإنترنت
     */
    fun getCachedReels(context: Context): List<SupabaseServices.ReelRow> {
        return runCatching {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_CACHED_REELS, null) ?: return emptyList()
            reelsJson.decodeFromString<List<SupabaseServices.ReelRow>>(jsonStr)
        }.getOrElse { emptyList() }
    }
}
