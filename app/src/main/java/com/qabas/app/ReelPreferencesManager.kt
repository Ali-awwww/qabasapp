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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
    val category: String = "عام"
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

/**
 * مدير إعدادات وسجل قسم الريلز لتطبيق قبس
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
    private const val KEY_SHOW_CAPTIONS = "reels_show_captions"
    private const val KEY_CONTENT_CATEGORY = "reels_content_category"
    private const val KEY_DOUBLE_TAP_ACTION = "reels_double_tap_action" // "LIKE", "SAVE", "PAUSE"
    private const val KEY_WATCH_HISTORY = "reels_watch_history_json"
    private const val KEY_SAVED_REELS = "reels_saved_reels_json"
    private const val KEY_LIKED_REELS = "reels_liked_reels_json"
    private const val KEY_STATS = "reels_stats_json"

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

    private val _showCaptions = MutableStateFlow(true)
    val showCaptions: StateFlow<Boolean> = _showCaptions.asStateFlow()

    private val _contentCategory = MutableStateFlow("ALL")
    val contentCategory: StateFlow<String> = _contentCategory.asStateFlow()

    private val _doubleTapAction = MutableStateFlow("LIKE")
    val doubleTapAction: StateFlow<String> = _doubleTapAction.asStateFlow()

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
        _showCaptions.value = prefs.getBoolean(KEY_SHOW_CAPTIONS, true)
        _contentCategory.value = prefs.getString(KEY_CONTENT_CATEGORY, "ALL") ?: "ALL"
        _doubleTapAction.value = prefs.getString(KEY_DOUBLE_TAP_ACTION, "LIKE") ?: "LIKE"

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

    fun setShowCaptions(context: Context, enabled: Boolean) {
        _showCaptions.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SHOW_CAPTIONS, enabled).apply()
    }

    fun setContentCategory(context: Context, category: String) {
        _contentCategory.value = category
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_CONTENT_CATEGORY, category).apply()
    }

    fun setDoubleTapAction(context: Context, action: String) {
        _doubleTapAction.value = action
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_DOUBLE_TAP_ACTION, action).apply()
    }

    // --- Watch History Operations ---

    /**
     * تسجيل مشاهدة مقطع ريلز في السجل
     */
    fun recordWatch(
        context: Context,
        reelId: String,
        title: String,
        userName: String?,
        userAvatar: String?,
        videoUrl: String,
        category: String = "عام",
        durationSec: Int = 1
    ) {
        init(context)
        val newItem = ReelHistoryItem(
            reelId = reelId,
            title = title,
            userName = userName ?: "صانع محتوى قبس",
            userAvatar = userAvatar,
            videoUrl = videoUrl,
            timestamp = System.currentTimeMillis(),
            watchedDurationSec = durationSec,
            category = category
        )

        // Keep most recent first, prevent immediate duplicates, enforce strict quota
        val currentList = _watchHistory.value.toMutableList()
        currentList.removeAll { it.reelId == reelId }
        currentList.add(0, newItem)
        while (currentList.size > ReelSafetyGuardrails.MAX_WATCH_HISTORY_COUNT) {
            currentList.removeAt(currentList.lastIndex)
        }
        _watchHistory.value = currentList

        // Save
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_WATCH_HISTORY, serializeItemsJson(currentList)).apply()

        // Update stats
        val curStats = _userStats.value
        val updatedStats = curStats.copy(
            totalWatched = curStats.totalWatched + 1,
            totalWatchSeconds = curStats.totalWatchSeconds + durationSec
        )
        _userStats.value = updatedStats
        saveStats(context, updatedStats)
    }

    fun removeFromWatchHistory(context: Context, reelId: String) {
        init(context)
        val currentList = _watchHistory.value.filterNot { it.reelId == reelId }
        _watchHistory.value = currentList
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_WATCH_HISTORY, serializeItemsJson(currentList)).apply()
    }

    fun clearWatchHistory(context: Context) {
        init(context)
        _watchHistory.value = emptyList()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_WATCH_HISTORY, "[]").apply()
    }

    // --- Saved / Bookmarks Operations ---

    fun isSaved(reelId: String): Boolean {
        return _savedReels.value.any { it.reelId == reelId }
    }

    fun toggleSave(
        context: Context,
        reelId: String,
        title: String,
        userName: String?,
        userAvatar: String?,
        videoUrl: String,
        category: String = "عام"
    ): Boolean {
        init(context)
        val currentList = _savedReels.value.toMutableList()
        val existing = currentList.find { it.reelId == reelId }
        val isNowSaved: Boolean

        if (existing != null) {
            currentList.remove(existing)
            isNowSaved = false
        } else {
            val item = ReelHistoryItem(
                reelId = reelId,
                title = ReelSafetyGuardrails.cleanText(title, 80),
                userName = ReelSafetyGuardrails.cleanText(userName ?: "صانع محتوى قبس", 40),
                userAvatar = userAvatar,
                videoUrl = videoUrl,
                timestamp = System.currentTimeMillis(),
                category = category
            )
            currentList.add(0, item)
            while (currentList.size > ReelSafetyGuardrails.MAX_SAVED_REELS_COUNT) {
                currentList.removeAt(currentList.lastIndex)
            }
            isNowSaved = true
        }
        _savedReels.value = currentList
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_SAVED_REELS, serializeItemsJson(currentList)).apply()

        // Update stats
        val curStats = _userStats.value
        val updatedStats = curStats.copy(totalSaved = currentList.size)
        _userStats.value = updatedStats
        saveStats(context, updatedStats)

        triggerHaptic(context)
        return isNowSaved
    }

    fun clearSavedReels(context: Context) {
        init(context)
        _savedReels.value = emptyList()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_SAVED_REELS, "[]").apply()
    }

    // --- Liked Reels Operations ---

    fun isLiked(reelId: String): Boolean {
        return _likedReels.value.any { it.reelId == reelId }
    }

    fun toggleLiked(
        context: Context,
        reelId: String,
        title: String,
        userName: String?,
        userAvatar: String?,
        videoUrl: String,
        category: String = "عام"
    ): Boolean {
        init(context)
        val currentList = _likedReels.value.toMutableList()
        val existing = currentList.find { it.reelId == reelId }
        val isNowLiked: Boolean

        if (existing != null) {
            currentList.remove(existing)
            isNowLiked = false
        } else {
            val item = ReelHistoryItem(
                reelId = reelId,
                title = ReelSafetyGuardrails.cleanText(title, 80),
                userName = ReelSafetyGuardrails.cleanText(userName ?: "صانع محتوى قبس", 40),
                userAvatar = userAvatar,
                videoUrl = videoUrl,
                timestamp = System.currentTimeMillis(),
                category = category
            )
            currentList.add(0, item)
            while (currentList.size > ReelSafetyGuardrails.MAX_LIKED_REELS_COUNT) {
                currentList.removeAt(currentList.lastIndex)
            }
            isNowLiked = true
        }
        _likedReels.value = currentList
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_LIKED_REELS, serializeItemsJson(currentList)).apply()

        // Update stats
        val curStats = _userStats.value
        val updatedStats = curStats.copy(totalLikes = currentList.size)
        _userStats.value = updatedStats
        saveStats(context, updatedStats)

        triggerHaptic(context)
        return isNowLiked
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
                        title = obj.optString("title", "ريلز قبس"),
                        userName = obj.optString("user_name", "صانع محتوى"),
                        userAvatar = obj.optString("user_avatar", "").ifBlank { null },
                        videoUrl = obj.optString("video_url", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
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
}
