package com.qabas.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.qabas.app.ui.theme.AmiriFont
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.TajawalFont
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

/**
 * مدير التفضيلات والإعدادات المركزية لتطبيق قبس (مشكاة الهدى).
 * يتحكم في تخصيص القراءة، الأذان والمواقيت، علامة المونتاج المائية، وتوفير البيانات والنسخ الاحتياطي.
 */
object UserPreferencesManager {

    private const val PREFS_NAME = "qabas_user_preferences"

    // ملف التفضيلات الرئيسي للتطبيق (الهوية، الموقع، الصورة، التفضيلات المتنوعة)
    private const val QABAS_PREFS_NAME = "qabas_prefs"

    // المفاتيح في qabas_prefs التي تُزامَن سحابياً مع بقية التفضيلات.
    // استُثنيت عمداً: التوكينات/المفاتيح، أعلام الصلاحية، إزاحات المواضع، والمسودات والمؤقتات.
    private val CLOUD_SYNCED_QABAS_KEYS = setOf(
        "selected_prayer_city_name",
        "selected_city_index",
        "user_avatar_uri",
        "user_avatar_preset",
        "user_bio",
        "selected_reciter",
        "selected_riwaya",
        "last_read_surah",
        "sakina_mode",
        "offline_ai_mode",
        "selected_local_model_id",
        "pref_hud_grid",
        "pref_hud_corners",
        "pref_oled_contrast",
        "spatial_cam_docked_mode",
        "spatial_cam_compact_mode",
        "mishkat_tasbeeh_count",
        "custom_styles"
    )

    // مرجع قوي لمستمع تغييرات qabas_prefs (WeakReference داخل Android)
    private var qabasPrefsListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null

    // يمنع إعادة المزامنة عند تطبيق تغييرات قادمة من السحابة (تجنّب الحلقة اللانهائية)
    @Volatile
    private var isApplyingRemoteChanges = false

    // ── 1. Reading & Islamic Text Preferences ──
    private val _quranFontSize = MutableStateFlow(20f)
    val quranFontSize: StateFlow<Float> = _quranFontSize.asStateFlow()

    private val _readingFontFamily = MutableStateFlow("amiri") // "amiri", "uthmani", "cairo", "tajawal", "naskh"
    val readingFontFamily: StateFlow<String> = _readingFontFamily.asStateFlow()

    private val _readingTheme = MutableStateFlow("dark_luxury") // "dark_luxury", "sepia_warm", "oled_black", "light_clean"
    val readingTheme: StateFlow<String> = _readingTheme.asStateFlow()

    private val _showTashkeel = MutableStateFlow(true)
    val showTashkeel: StateFlow<Boolean> = _showTashkeel.asStateFlow()

    private val _quranReciter = MutableStateFlow("alafasy") // "alafasy", "abdulbasit", "minshawy", "husary", "muaiqly", "ghamadi"
    val quranReciter: StateFlow<String> = _quranReciter.asStateFlow()

    // ── 2. Prayer & Spiritual Reminders ──
    private val _calculationMethod = MutableStateFlow("MAKKAH") // "MAKKAH", "MWL", "EGYPT", "KARACHI", "ISNA"
    val calculationMethod: StateFlow<String> = _calculationMethod.asStateFlow()

    private val _athanVoice = MutableStateFlow("makkah") // "makkah", "madinah", "aqsa", "alafasy", "takbeerat", "silent"
    val athanVoice: StateFlow<String> = _athanVoice.asStateFlow()

    private val _morningAthkar = MutableStateFlow(true)
    val morningAthkar: StateFlow<Boolean> = _morningAthkar.asStateFlow()

    private val _eveningAthkar = MutableStateFlow(true)
    val eveningAthkar: StateFlow<Boolean> = _eveningAthkar.asStateFlow()

    private val _qiyamReminder = MutableStateFlow(true)
    val qiyamReminder: StateFlow<Boolean> = _qiyamReminder.asStateFlow()

    private val _kahfFridayReminder = MutableStateFlow(true)
    val kahfFridayReminder: StateFlow<Boolean> = _kahfFridayReminder.asStateFlow()

    // ── 3. Content Creation & Watermark ──
    private val _watermarkEnabled = MutableStateFlow(true)
    val watermarkEnabled: StateFlow<Boolean> = _watermarkEnabled.asStateFlow()

    private val _watermarkText = MutableStateFlow("@qabas.studio")
    val watermarkText: StateFlow<String> = _watermarkText.asStateFlow()

    private val _watermarkPosition = MutableStateFlow("BOTTOM_RIGHT") // "BOTTOM_RIGHT", "BOTTOM_LEFT", "TOP_RIGHT", "TOP_LEFT"
    val watermarkPosition: StateFlow<String> = _watermarkPosition.asStateFlow()

    private val _defaultAspectRatio = MutableStateFlow("9:16") // "9:16", "16:9", "1:1"
    val defaultAspectRatio: StateFlow<String> = _defaultAspectRatio.asStateFlow()

    private val _autoSaveToGallery = MutableStateFlow(true)
    val autoSaveToGallery: StateFlow<Boolean> = _autoSaveToGallery.asStateFlow()

    // ── 4. Data Saver & Storage ──
    private val _dataSaverMode = MutableStateFlow(false)
    val dataSaverMode: StateFlow<Boolean> = _dataSaverMode.asStateFlow()

    private val _cacheLimitMb = MutableStateFlow(500)
    val cacheLimitMb: StateFlow<Int> = _cacheLimitMb.asStateFlow()

    private val _appLanguage = MutableStateFlow("ar")
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    // ── 5. Supabase Cloud Sync States ──
    private val _cloudSyncEnabled = MutableStateFlow(true)
    val cloudSyncEnabled: StateFlow<Boolean> = _cloudSyncEnabled.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _networkStatus = MutableStateFlow(ConnectivityObserver.Status.Available)
    val networkStatus: StateFlow<ConnectivityObserver.Status> = _networkStatus.asStateFlow()

    private var hasPendingChanges = false
    private var networkObserverStarted = false

    fun init(context: Context) {
        _networkStatus.value = if (NetworkUtils.isNetworkAvailable(context)) {
            ConnectivityObserver.Status.Available
        } else {
            ConnectivityObserver.Status.Unavailable
        }
        // Register the connectivity observer exactly once per process. Starting a new
        // collector on every Activity recreation leaked network callbacks and caused
        // TooManyRequestsException crashes on Android 14+.
        if (!networkObserverStarted) {
            networkObserverStarted = true
            val appContext = context.applicationContext
            val observer = NetworkConnectivityObserver(appContext)
            CoroutineScope(Dispatchers.Main).launch {
                observer.observe().collect { status ->
                    _networkStatus.value = status
                    if (status == ConnectivityObserver.Status.Available && hasPendingChanges) {
                        syncToSupabase(appContext)
                    }
                }
            }
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _quranFontSize.value = prefs.getFloat("quran_font_size", 20f)
        _readingFontFamily.value = prefs.getString("reading_font_family", "amiri") ?: "amiri"
        _readingTheme.value = prefs.getString("reading_theme", "dark_luxury") ?: "dark_luxury"
        _showTashkeel.value = prefs.getBoolean("show_tashkeel", true)
        _quranReciter.value = prefs.getString("quran_reciter", "alafasy") ?: "alafasy"

        _calculationMethod.value = prefs.getString("calculation_method", "MAKKAH") ?: "MAKKAH"
        _athanVoice.value = prefs.getString("athan_voice", "makkah") ?: "makkah"
        _morningAthkar.value = prefs.getBoolean("morning_athkar", true)
        _eveningAthkar.value = prefs.getBoolean("evening_athkar", true)
        _qiyamReminder.value = prefs.getBoolean("qiyam_reminder", true)
        _kahfFridayReminder.value = prefs.getBoolean("kahf_friday_reminder", true)

        _watermarkEnabled.value = prefs.getBoolean("watermark_enabled", true)
        _watermarkText.value = prefs.getString("watermark_text", "@qabas.studio") ?: "@qabas.studio"
        _watermarkPosition.value = prefs.getString("watermark_position", "BOTTOM_RIGHT") ?: "BOTTOM_RIGHT"
        _defaultAspectRatio.value = prefs.getString("default_aspect_ratio", "9:16") ?: "9:16"
        _autoSaveToGallery.value = prefs.getBoolean("auto_save_to_gallery", true)

        _dataSaverMode.value = prefs.getBoolean("data_saver_mode", false)
        _cacheLimitMb.value = prefs.getInt("cache_limit_mb", 500)

        val lang = prefs.getString("app_language", "ar") ?: "ar"
        _appLanguage.value = lang
        Translator.setLanguage(lang)

        _cloudSyncEnabled.value = prefs.getBoolean("cloud_sync_enabled", true)
        _lastSyncTime.value = prefs.getLong("last_sync_timestamp", 0L)

        // أي تغيير على مفتاح مُزامَن في qabas_prefs (الموقع/الصورة/القراءة/المظهر...) يطلق مزامنة تلقائية
        if (qabasPrefsListener == null) {
            val appContext = context.applicationContext
            val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key != null && key in CLOUD_SYNCED_QABAS_KEYS && !isApplyingRemoteChanges) {
                    triggerAutoSync(appContext)
                }
            }
            appContext.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(listener)
            qabasPrefsListener = listener
        }

        // Best-effort cloud sync fetch on startup if enabled and Supabase is available
        if (_cloudSyncEnabled.value && SupabaseServices.isSupabaseAvailable) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                runCatching { fetchAndApplyFromSupabase(context) }
            }
        }
    }

    // ── Supabase Cloud Sync Operations ──

    fun setCloudSyncEnabled(context: Context, enabled: Boolean) {
        _cloudSyncEnabled.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("cloud_sync_enabled", enabled).apply()
        if (enabled) {
            triggerAutoSync(context)
        }
    }

    fun getEffectiveUserId(context: Context): String {
        val authUid = SupabaseServices.Auth.getCurrentUserId() ?: CloudServices.Auth.getCurrentUserId()
        if (!authUid.isNullOrBlank()) return authUid
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var deviceId = prefs.getString("device_sync_id", null)
        if (deviceId.isNullOrBlank()) {
            deviceId = "dev_" + UUID.randomUUID().toString().take(12)
            prefs.edit().putString("device_sync_id", deviceId).apply()
        }
        return deviceId
    }

    suspend fun syncToSupabase(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!_cloudSyncEnabled.value || !SupabaseServices.isSupabaseAvailable) {
            return@withContext false
        }
        _isSyncing.value = true
        val userId = getEffectiveUserId(context)

        // ارفع الصورة الشخصية المحلية أولاً حتى يتضمّن الملف المصدَّر رابطها السحابي
        val avatarUrl = uploadLocalAvatarIfNeeded(context, userId)

        val jsonPayload = exportBackupJson(context) ?: run {
            _isSyncing.value = false
            return@withContext false
        }
        val success = SupabaseServices.UserSettings.saveUserSettings(userId, jsonPayload)

        // Sync with 'profiles' table as well — اقرأ الهوية من الملف الصحيح (qabas_prefs)
        val identityPrefs = context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE)
        val legacyPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedName = identityPrefs.getString("user_name", null)
            ?: legacyPrefs.getString("user_name", null)
            ?: legacyPrefs.getString("saved_user_name", null)
        val savedEmail = identityPrefs.getString("user_email", null)
            ?: legacyPrefs.getString("user_email", null)
            ?: legacyPrefs.getString("saved_login_email", null)
        SupabaseRepository.getInstance().syncUserProfile(
            userId = userId,
            displayName = savedName,
            email = savedEmail,
            avatarUrl = avatarUrl ?: identityPrefs.getString("user_avatar_uri", null),
            preferencesJson = jsonPayload
        )

        if (success) {
            hasPendingChanges = false
            val now = System.currentTimeMillis()
            _lastSyncTime.value = now
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putLong("last_sync_timestamp", now).apply()
        }
        _isSyncing.value = false
        success
    }

    suspend fun fetchAndApplyFromSupabase(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!SupabaseServices.isSupabaseAvailable) {
            return@withContext false
        }
        _isSyncing.value = true
        val userId = getEffectiveUserId(context)
        val row = SupabaseServices.UserSettings.getUserSettings(userId)
        var applied = false
        if (row != null && row.preferencesJson.isNotBlank()) {
            val ok = importBackupJson(context, row.preferencesJson)
            if (ok) {
                _lastSyncTime.value = row.updatedAtMillis
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putLong("last_sync_timestamp", row.updatedAtMillis).apply()
                applied = true
            }
        }

        // استرجاع الاسم/البريد/الصورة من جدول profiles عند غيابها محلياً (مهم على جهاز جديد)
        runCatching {
            val profile = SupabaseServices.Profiles.getUserProfile(userId) ?: return@runCatching
            val identityPrefs = context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE)
            val editor = identityPrefs.edit()
            var changed = false
            val localAvatar = identityPrefs.getString("user_avatar_uri", null)
            if ((localAvatar.isNullOrBlank() || localAvatar.startsWith("content")) && !profile.avatarUrl.isNullOrBlank()) {
                editor.putString("user_avatar_uri", profile.avatarUrl); changed = true
            }
            if (identityPrefs.getString("user_name", null).isNullOrBlank() && !profile.fullName.isNullOrBlank()) {
                editor.putString("user_name", profile.fullName); changed = true
            }
            if (identityPrefs.getString("user_email", null).isNullOrBlank() && !profile.email.isNullOrBlank()) {
                editor.putString("user_email", profile.email); changed = true
            }
            if (changed) {
                isApplyingRemoteChanges = true
                try { editor.commit() } finally { isApplyingRemoteChanges = false }
                applied = true
            }
        }.onFailure { Log.e("UserPreferencesManager", "profile restore failed: ${it.message}") }
        _isSyncing.value = false
        applied
    }

    /**
     * إن كانت الصورة الشخصية محلياً (content:// أو ملف) ترفع إلى Supabase Storage
     * وتُستبدل في التفضيلات بالرابط العام. تُعيد الرابط السحابي أو null.
     */
    private suspend fun uploadLocalAvatarIfNeeded(context: Context, userId: String): String? =
        withContext(Dispatchers.IO) {
            val identityPrefs = context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE)
            val uriStr = identityPrefs.getString("user_avatar_uri", null)?.takeIf { it.isNotBlank() }
                ?: return@withContext null
            if (uriStr.startsWith("http://") || uriStr.startsWith("https://")) return@withContext uriStr
            runCatching {
                val uri = Uri.parse(uriStr)
                val bytes = if (uri.scheme == "content") {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } else {
                    val path = uri.path ?: uriStr
                    val f = java.io.File(path)
                    if (f.exists() && f.length() > 0) f.readBytes() else null
                } ?: return@runCatching null
                if (bytes.isEmpty()) return@runCatching null
                val ext = when {
                    uriStr.endsWith(".png", true) -> "png"
                    uriStr.endsWith(".webp", true) -> "webp"
                    else -> "jpg"
                }
                val url = SupabaseServices.Storage.uploadFile(
                    bucketName = "avatars",
                    path = "$userId.$ext",
                    data = bytes,
                    upsert = true
                )
                if (!url.isNullOrBlank()) {
                    isApplyingRemoteChanges = true
                    try {
                        identityPrefs.edit().putString("user_avatar_uri", url).commit()
                    } finally {
                        isApplyingRemoteChanges = false
                    }
                }
                url
            }.onFailure { Log.e("UserPreferencesManager", "uploadLocalAvatarIfNeeded failed: ${it.message}") }
                .getOrNull()
        }

    fun triggerAutoSync(context: Context) {
        if (_cloudSyncEnabled.value && SupabaseServices.isSupabaseAvailable) {
            if (_networkStatus.value == ConnectivityObserver.Status.Available) {
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    runCatching { syncToSupabase(context) }
                }
            } else {
                hasPendingChanges = true
            }
        }
    }

    // Setters
    fun setQuranFontSize(context: Context, size: Float) {
        _quranFontSize.value = size
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putFloat("quran_font_size", size).apply()
        triggerAutoSync(context)
    }

    fun setReadingFontFamily(context: Context, font: String) {
        _readingFontFamily.value = font
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("reading_font_family", font).apply()
        triggerAutoSync(context)
    }

    fun setReadingTheme(context: Context, theme: String) {
        _readingTheme.value = theme
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("reading_theme", theme).apply()
        triggerAutoSync(context)
    }

    fun setShowTashkeel(context: Context, show: Boolean) {
        _showTashkeel.value = show
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("show_tashkeel", show).apply()
        triggerAutoSync(context)
    }

    fun setQuranReciter(context: Context, reciter: String) {
        _quranReciter.value = reciter
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("quran_reciter", reciter).apply()
        triggerAutoSync(context)
    }

    fun setCalculationMethod(context: Context, method: String) {
        _calculationMethod.value = method
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("calculation_method", method).apply()
        triggerAutoSync(context)
    }

    fun setAthanVoice(context: Context, voice: String) {
        _athanVoice.value = voice
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("athan_voice", voice).apply()
        triggerAutoSync(context)
    }

    fun setMorningAthkar(context: Context, enable: Boolean) {
        _morningAthkar.value = enable
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("morning_athkar", enable).apply()
        triggerAutoSync(context)
    }

    fun setEveningAthkar(context: Context, enable: Boolean) {
        _eveningAthkar.value = enable
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("evening_athkar", enable).apply()
        triggerAutoSync(context)
    }

    fun setQiyamReminder(context: Context, enable: Boolean) {
        _qiyamReminder.value = enable
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("qiyam_reminder", enable).apply()
        triggerAutoSync(context)
    }

    fun setKahfFridayReminder(context: Context, enable: Boolean) {
        _kahfFridayReminder.value = enable
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("kahf_friday_reminder", enable).apply()
        triggerAutoSync(context)
    }

    fun setWatermarkEnabled(context: Context, enable: Boolean) {
        _watermarkEnabled.value = enable
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("watermark_enabled", enable).apply()
        triggerAutoSync(context)
    }

    fun setWatermarkText(context: Context, text: String) {
        _watermarkText.value = text
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("watermark_text", text).apply()
        triggerAutoSync(context)
    }

    fun setWatermarkPosition(context: Context, pos: String) {
        _watermarkPosition.value = pos
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("watermark_position", pos).apply()
        triggerAutoSync(context)
    }

    fun setDefaultAspectRatio(context: Context, ratio: String) {
        _defaultAspectRatio.value = ratio
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("default_aspect_ratio", ratio).apply()
        triggerAutoSync(context)
    }

    fun setAutoSaveToGallery(context: Context, autoSave: Boolean) {
        _autoSaveToGallery.value = autoSave
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("auto_save_to_gallery", autoSave).apply()
        triggerAutoSync(context)
    }

    fun setDataSaverMode(context: Context, enable: Boolean) {
        _dataSaverMode.value = enable
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("data_saver_mode", enable).apply()
        triggerAutoSync(context)
    }

    fun setCacheLimitMb(context: Context, limit: Int) {
        _cacheLimitMb.value = limit
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt("cache_limit_mb", limit).apply()
        triggerAutoSync(context)
    }

    fun setAppLanguage(context: Context, lang: String) {
        _appLanguage.value = lang
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("app_language", lang).apply()
        Translator.setLanguage(lang)
        triggerAutoSync(context)
    }

    // Helper to resolve font family
    fun getResolvedFontFamily(key: String = _readingFontFamily.value): FontFamily {
        return when (key) {
            "amiri" -> AmiriFont
            "cairo" -> CairoFont
            "tajawal" -> TajawalFont
            else -> NotoSansFont
        }
    }

    // Helper to resolve reading background colors
    @Immutable
    data class ReadingThemePalette(
        val background: Color,
        val cardBackground: Color,
        val textColor: Color,
        val textSecondary: Color,
        val accentColor: Color,
        val name: String
    )

    fun getResolvedReadingThemePalette(key: String = _readingTheme.value): ReadingThemePalette {
        return when (key) {
            "sepia_warm" -> ReadingThemePalette(
                background = Color(0xFFFBF0D9),
                cardBackground = Color(0xFFF3E5C8),
                textColor = Color(0xFF2C2214),
                textSecondary = Color(0xFF6E5D47),
                accentColor = Color(0xFF96631E),
                name = "ورق دافئ (مريح للعين)"
            )
            "oled_black" -> ReadingThemePalette(
                background = Color(0xFF000000),
                cardBackground = Color(0xFF0C0C0E),
                textColor = Color(0xFFECEFF1),
                textSecondary = Color(0xFF90A4AE),
                accentColor = Color(0xFFD4AF37),
                name = "أسود مطبق (OLED موفر للبطارية)"
            )
            "light_clean" -> ReadingThemePalette(
                background = Color(0xFFF8FAFC),
                cardBackground = Color(0xFFFFFFFF),
                textColor = Color(0xFF0F172A),
                textSecondary = Color(0xFF475569),
                accentColor = Color(0xFF1E3A8A),
                name = "أبيض ناصع وواضح"
            )
            else -> ReadingThemePalette(
                background = Color(0xFF0B0F19),
                cardBackground = Color(0xFF151B2B),
                textColor = Color(0xFFF1F5F9),
                textSecondary = Color(0xFF94A3B8),
                accentColor = Color(0xFFD4AF37),
                name = "الوضع الملكي الداكن"
            )
        }
    }

    // ── 5. Backup & Export Engine ──
    suspend fun exportBackupJson(context: Context): String? = withContext(Dispatchers.IO) {
        runCatching {
            val db = AppDatabase.getDatabase(context)
            val hadithDao = db.hadithCardDao()
            val audioDao = db.audioTrackDao()

            val root = JSONObject()
            root.put("app", "Qabas Islamic Studio")
            root.put("version", "1.2.1")
            root.put("timestamp", System.currentTimeMillis())
            root.put("date_formatted", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

            // User Preferences
            val prefsObj = JSONObject()
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.all.forEach { (k, v) ->
                prefsObj.put(k, v)
            }
            root.put("preferences", prefsObj)

            // تفضيلات الهوية والموقع والقراءة والمظهر (من qabas_prefs) — المفاتيح المزامَنة فقط
            val identityObj = JSONObject()
            val identityPrefs = context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE)
            CLOUD_SYNCED_QABAS_KEYS.forEach { key ->
                val v = identityPrefs.all[key] ?: return@forEach
                when (v) {
                    is Boolean, is Int, is Long, is Float, is String -> identityObj.put(key, v)
                }
            }
            root.put("identity_preferences", identityObj)

            // Bookmarks & Hadiths
            val hadiths = hadithDao.getAllHadithCardsOnce()
            val hadithArr = JSONArray()
            hadiths.forEach { h ->
                val hObj = JSONObject()
                hObj.put("id", h.id)
                hObj.put("text", h.text)
                hObj.put("narrator", h.narrator)
                hObj.put("source", h.source)
                hObj.put("isFavorite", h.isFavorite)
                hadithArr.put(hObj)
            }
            root.put("hadith_bookmarks", hadithArr)

            // Saved Audio Tracks
            val audios = audioDao.getAllAudioTracksOnce()
            val audioArr = JSONArray()
            audios.forEach { a ->
                val aObj = JSONObject()
                aObj.put("id", a.id)
                aObj.put("title", a.title)
                aObj.put("category", a.typeCategory)
                aObj.put("url", a.audioUrlOrPath)
                audioArr.put(aObj)
            }
            root.put("audio_tracks", audioArr)

            root.toString(2)
        }.getOrNull()
    }

    suspend fun importBackupJson(context: Context, jsonString: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(jsonString)
            if (root.has("preferences")) {
                val prefsObj = root.getJSONObject("preferences")
                val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                val keys = prefsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    when (val v = prefsObj.get(key)) {
                        is Boolean -> editor.putBoolean(key, v)
                        is Int -> editor.putInt(key, v)
                        is Long -> editor.putLong(key, v)
                        is Double -> editor.putFloat(key, v.toFloat())
                        is String -> editor.putString(key, v)
                    }
                }
                editor.apply()
                init(context)
            }
            // استرجاع تفضيلات الهوية/الموقع/القراءة/المظهر إلى qabas_prefs
            if (root.has("identity_preferences")) {
                val identityObj = root.getJSONObject("identity_preferences")
                val editor = context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE).edit()
                val keys = identityObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key !in CLOUD_SYNCED_QABAS_KEYS) continue
                    when (val v = identityObj.get(key)) {
                        is Boolean -> editor.putBoolean(key, v)
                        is Int -> editor.putInt(key, v)
                        is Long -> editor.putLong(key, v)
                        is Double -> editor.putFloat(key, v.toFloat())
                        is Float -> editor.putFloat(key, v)
                        is String -> editor.putString(key, v)
                    }
                }
                // commit متزامن مع تعطيل إطلاق المزامنة مؤقتاً (تجنّب الحلقة)
                isApplyingRemoteChanges = true
                try {
                    editor.commit()
                } finally {
                    isApplyingRemoteChanges = false
                }
            }
            true
        }.getOrDefault(false)
    }
}
