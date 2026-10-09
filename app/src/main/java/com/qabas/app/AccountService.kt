package com.qabas.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AccountService(private val context: Context) {
    private val prefs = context.getSharedPreferences("account_prefs", Context.MODE_PRIVATE)

    companion object {
        const val MAX_DAILY_VIDEOS = 5
        const val MAX_DAILY_IMAGES = 5
        const val MAX_STRIKES = 5
    }

    private fun getCurrentDateStr(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    val isDeveloperOrAdmin: Boolean
        get() {
            val qabasPrefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            val isAdmin = qabasPrefs.getBoolean("is_admin", false)
            val isDev = qabasPrefs.getBoolean("is_developer", false) // لا يُمنح افتراضياً — فقط من كُتب له (المالك/العلامة المكتوبة مسبقاً)
            return isAdmin || isDev
        }

    val hasCustomKeys: Boolean
        get() {
            val qabasPrefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            val hasGemini = !qabasPrefs.getString("gemini_key", "").isNullOrBlank()
            val hasSettingsGemini = !context.getSharedPreferences("qabas_settings", Context.MODE_PRIVATE)
                .getString("gemini_key", "").isNullOrBlank()
            return hasGemini || hasSettingsGemini
        }

    var isPremium: Boolean
        get() = prefs.getBoolean("is_premium", false) || isDeveloperOrAdmin || hasCustomKeys
        set(value) {
            prefs.edit().putBoolean("is_premium", value).apply()
        }

    
    var walletBalance: Int
        get() = prefs.getInt("wallet_balance", 0)
        set(value) {
            prefs.edit().putInt("wallet_balance", value).apply()
        }

    var dailyVideoCount: Int
        get() {
            if (isDeveloperOrAdmin || hasCustomKeys) return 0
            val lastDate = prefs.getString("last_video_date", "")
            return if (lastDate == getCurrentDateStr()) {
                prefs.getInt("daily_video_count", 0)
            } else {
                0
            }
        }
        set(value) {
            prefs.edit()
                .putInt("daily_video_count", value)
                .putString("last_video_date", getCurrentDateStr())
                .apply()
        }

    var dailyImageCount: Int
        get() {
            if (isDeveloperOrAdmin || hasCustomKeys) return 0
            val lastDate = prefs.getString("last_image_date", "")
            return if (lastDate == getCurrentDateStr()) {
                prefs.getInt("daily_image_count", 0)
            } else {
                0
            }
        }
        set(value) {
            prefs.edit()
                .putInt("daily_image_count", value)
                .putString("last_image_date", getCurrentDateStr())
                .apply()
        }

    
    var lastCheckinDate: String
        get() = prefs.getString("last_checkin_date", "") ?: ""
        set(value) {
            prefs.edit().putString("last_checkin_date", value).apply()
        }

    var strikeCount: Int
        get() {
            if (isDeveloperOrAdmin) return 0
            val lastDate = prefs.getString("last_strike_date", "")
            return if (lastDate == getCurrentDateStr()) {
                prefs.getInt("strike_count", 0)
            } else {
                0
            }
        }
        set(value) {
            prefs.edit()
                .putInt("strike_count", value)
                .putString("last_strike_date", getCurrentDateStr())
                .apply()
        }

    val isSuspended: Boolean
        get() = !isDeveloperOrAdmin && strikeCount >= MAX_STRIKES

    val isMaintenanceMode: Boolean
        get() = AppRemoteConfig.current(context).maintenanceMode

    var isDevWatermarkEnabled: Boolean
        get() {
            val qabasPrefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            return qabasPrefs.getBoolean("dev_watermark_enabled", false)
        }
        set(value) {
            val qabasPrefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            qabasPrefs.edit().putBoolean("dev_watermark_enabled", value).apply()
        }

    var devWatermarkText: String
        get() {
            val qabasPrefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            return qabasPrefs.getString("dev_watermark_text", "✦ قبس | Qabas.Official ✦")
                ?: "✦ قبس | Qabas.Official ✦"
        }
        set(value) {
            val qabasPrefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            qabasPrefs.edit().putString("dev_watermark_text", value).apply()
        }

    var devWatermarkStyle: String
        get() {
            val qabasPrefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            return qabasPrefs.getString("dev_watermark_style", "ختم ذهبي سفلي (Golden Master Bar)")
                ?: "ختم ذهبي سفلي (Golden Master Bar)"
        }
        set(value) {
            val qabasPrefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            qabasPrefs.edit().putString("dev_watermark_style", value).apply()
        }

    var isDevExclusiveStudioEnabled: Boolean
        get() {
            val qabasPrefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            return qabasPrefs.getBoolean("dev_exclusive_studio_enabled", false)
        }
        set(value) {
            val qabasPrefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            qabasPrefs.edit().putBoolean("dev_exclusive_studio_enabled", value).apply()
        }

    val isAutoAiReplyEnabled: Boolean
        get() = AppRemoteConfig.current(context).autoAiReply

    val maintenanceMessage: String
        get() = AppRemoteConfig.current(context).maintenanceMessage

    fun canGenerateVideo(): Boolean {
        if (isDeveloperOrAdmin || hasCustomKeys) return true
        if (isMaintenanceMode || !isAutoAiReplyEnabled) return false
        if (isSuspended) return false
        return dailyVideoCount < MAX_DAILY_VIDEOS
    }

    fun incrementVideoUsage(): Boolean {
        if (isDeveloperOrAdmin || hasCustomKeys) return true
        if (!canGenerateVideo()) return false
        dailyVideoCount++
        return true
    }

    fun canGenerateImage(): Boolean {
        if (isDeveloperOrAdmin || hasCustomKeys) return true
        if (isMaintenanceMode || !isAutoAiReplyEnabled) return false
        if (isSuspended) return false
        return dailyImageCount < MAX_DAILY_IMAGES
    }

    fun incrementImageUsage(): Boolean {
        if (isDeveloperOrAdmin || hasCustomKeys) return true
        if (!canGenerateImage()) return false
        dailyImageCount++
        return true
    }

    fun addStrike(reason: String) {
        if (!isDeveloperOrAdmin) {
            strikeCount++
        }
    }
}

