package com.qabas.app

import android.content.Context
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.qabas.app.ui.theme.AmiriFont
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.TajawalFont
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicLong

/**
 * مدير التفضيلات والإعدادات المركزية لتطبيق خادم المسلم (مشكاة الهدى).
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
        "last_read_ayah",
        "last_read_page",
        "last_read_at",
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

    // review-r12: رسم المصحف هو مصدر الحقيقة الوحيد للتشكيل — "uthmani" يعرض
    // التشكيل الكامل، و"uthmani_simple"/"imlaei" بلا تشكيل. مفتاح show_tashkeel
    // القديم حُذف: كان يُحفظ ولا يقرأه قارئ المصحف أبداً.
    private val _mushafScript = MutableStateFlow("uthmani") // "uthmani", "uthmani_simple", "imlaei"
    val mushafScript: StateFlow<String> = _mushafScript.asStateFlow()

    // ── 2. Prayer & Spiritual Reminders ──
    private val _calculationMethod = MutableStateFlow("MAKKAH") // "MAKKAH", "MWL", "EGYPT", "KARACHI", "ISNA"
    val calculationMethod: StateFlow<String> = _calculationMethod.asStateFlow()
    private val _asrMadhab = MutableStateFlow("SHAFI") // "SHAFI" (shadow 1) or "HANAFI" (shadow 2)
    val asrMadhab: StateFlow<String> = _asrMadhab.asStateFlow()

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

    private val _appLanguage = MutableStateFlow("ar")
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    // ── 5. Accessibility ──
    private val _highContrastMode = MutableStateFlow(false)
    val highContrastMode: StateFlow<Boolean> = _highContrastMode.asStateFlow()

    private val _reducedMotion = MutableStateFlow(false)
    val reducedMotion: StateFlow<Boolean> = _reducedMotion.asStateFlow()

    private val _globalTextScale = MutableStateFlow(1.0f)
    val globalTextScale: StateFlow<Float> = _globalTextScale.asStateFlow()

    // ── 6. Supabase Cloud Sync States ──
    private val _cloudSyncEnabled = MutableStateFlow(true)
    val cloudSyncEnabled: StateFlow<Boolean> = _cloudSyncEnabled.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _networkStatus = MutableStateFlow(ConnectivityObserver.Status.Available)
    val networkStatus: StateFlow<ConnectivityObserver.Status> = _networkStatus.asStateFlow()
    private val _networkStatusReady = MutableStateFlow(false)
    val networkStatusReady: StateFlow<Boolean> = _networkStatusReady.asStateFlow()

    private val syncMutex = Mutex()
    private val pendingSyncRevision = AtomicLong(0L)
    @Volatile
    private var hasPendingChanges = false
    private var networkObserverStarted = false

    /** قراءة حالة الاتصال وتسجيل المراقب قبل أي تهيئة قد تنتظر المصادقة أو السحابة. */
    fun initNetworkStatus(context: Context) {
        if (!_networkStatusReady.value) {
            _networkStatus.value = if (NetworkUtils.isNetworkAvailable(context)) {
                ConnectivityObserver.Status.Available
            } else {
                ConnectivityObserver.Status.Unavailable
            }
            // Consumers must not interpret the StateFlow's bootstrap value as a real reading.
            _networkStatusReady.value = true
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
    }

    fun init(context: Context) {
        initNetworkStatus(context)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _quranFontSize.value = prefs.getFloat("quran_font_size", 20f)
        _readingFontFamily.value = prefs.getString("reading_font_family", "amiri") ?: "amiri"
        _readingTheme.value = prefs.getString("reading_theme", "dark_luxury") ?: "dark_luxury"
        _mushafScript.value = getMushafScript(context)

        _calculationMethod.value = prefs.getString("calculation_method", "MAKKAH") ?: "MAKKAH"
        _asrMadhab.value = prefs.getString("asr_madhab", "SHAFI") ?: "SHAFI"

        _watermarkEnabled.value = prefs.getBoolean("watermark_enabled", true)
        _watermarkText.value = prefs.getString("watermark_text", "@qabas.studio") ?: "@qabas.studio"
        _watermarkPosition.value = prefs.getString("watermark_position", "BOTTOM_RIGHT") ?: "BOTTOM_RIGHT"
        _defaultAspectRatio.value = prefs.getString("default_aspect_ratio", "9:16") ?: "9:16"
        _autoSaveToGallery.value = prefs.getBoolean("auto_save_to_gallery", true)

        _dataSaverMode.value = prefs.getBoolean("data_saver_mode", false)

        val lang = prefs.getString("app_language", "ar") ?: "ar"
        _appLanguage.value = lang
        Translator.setLanguage(lang)

        _highContrastMode.value = prefs.getBoolean("high_contrast_mode", false)
        // تقليل الحركة: اختيار المستخدم المحفوظ أولاً؛ وإن لم يختر بعد، يُورَث من
        // إعداد النظام «إزالة الحركات» (TRANSITION_ANIMATION_SCALE = 0) بدل
        // الافتراض الأعمى false — وإلا بقي مستخدم فعّلها في النظام يواجه كل
        // انتقالات التطبيق المتحركة حتى يكتشف مفتاحاً داخل التطبيق.
        _reducedMotion.value = if (prefs.contains("reduced_motion")) {
            prefs.getBoolean("reduced_motion", false)
        } else {
            runCatching {
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.TRANSITION_ANIMATION_SCALE,
                    1f
                ) == 0f
            }.getOrDefault(false)
        }
        _globalTextScale.value = prefs.getFloat("global_text_scale", 1.0f)

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
                    .onFailure { if (it is CancellationException) throw it }
            }
        }
    }

    // ── Supabase Cloud Sync Operations ──

    /**
     * Batch 6 Priority 3 — email verification gate.
     *
     * Cloud sync is blocked only while the signed-in account's email is
     * explicitly unconfirmed (`email_confirmed == false` in `qabas_prefs`,
     * recorded by [SupabaseServices.Auth.refreshEmailConfirmationState] at
     * login/registration). An absent flag means "unknown" (older installs, no
     * session) and sync stays allowed, so this changes nothing for existing
     * users. Local use is unaffected: preferences keep working on-device and
     * pending changes are kept for the next allowed sync.
     */
    fun isCloudSyncBlockedByUnconfirmedEmail(context: Context): Boolean {
        if (SupabaseServices.Auth.getCurrentUserId().isNullOrBlank()) return false
        val confirmed = context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("email_confirmed", true)
        return !confirmed
    }

    fun setCloudSyncEnabled(context: Context, enabled: Boolean) {
        _cloudSyncEnabled.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("cloud_sync_enabled", enabled).apply()
        if (enabled) {
            triggerAutoSync(context)
        }
    }

    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var deviceId = prefs.getString("device_sync_id", null)
        if (deviceId.isNullOrBlank()) {
            deviceId = "dev_" + UUID.randomUUID().toString().take(12)
            prefs.edit().putString("device_sync_id", deviceId).apply()
        }
        return deviceId
    }

    fun getEffectiveUserId(context: Context): String {
        val authUid = SupabaseServices.Auth.getCurrentUserId() ?: CloudServices.Auth.getCurrentUserId()
        if (!authUid.isNullOrBlank()) return authUid
        return getDeviceId(context)
    }

    internal suspend fun <T> withSyncLock(block: suspend () -> T): T {
        syncMutex.lock()
        return try {
            block()
        } finally {
            syncMutex.unlock()
        }
    }

    internal fun shouldKeepPendingChanges(
        saveSucceeded: Boolean,
        revisionAtSyncStart: Long,
        currentRevision: Long
    ): Boolean = !saveSucceeded || currentRevision != revisionAtSyncStart

    internal fun shouldReportSyncSuccess(
        saveSucceeded: Boolean,
        hasPendingChanges: Boolean,
        revisionAtSyncStart: Long,
        currentRevision: Long
    ): Boolean = saveSucceeded && !hasPendingChanges && currentRevision == revisionAtSyncStart

    suspend fun syncToSupabase(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!_cloudSyncEnabled.value || !SupabaseServices.isSupabaseAvailable) {
            return@withContext false
        }
        if (isCloudSyncBlockedByUnconfirmedEmail(context)) {
            Log.d("UserPreferencesManager", "cloud sync blocked: email not confirmed yet")
            return@withContext false
        }
        withSyncLock {
            _isSyncing.value = true
            try {
                val userId = getEffectiveUserId(context)
                val syncRevision = pendingSyncRevision.get()

                // ارفع الصورة الشخصية المحلية أولاً حتى يتضمّن الملف المصدّر رابطها السحابي
                val avatarUrl = uploadLocalAvatarIfNeeded(context, userId)
                val jsonPayload = exportBackupJson(context)
                if (jsonPayload == null) {
                    hasPendingChanges = true
                    false
                } else {
                    val success = SupabaseServices.UserSettings.saveUserSettings(userId, jsonPayload)
                    hasPendingChanges = shouldKeepPendingChanges(
                        saveSucceeded = success,
                        revisionAtSyncStart = syncRevision,
                        currentRevision = pendingSyncRevision.get()
                    )
                    if (success && pendingSyncRevision.get() != syncRevision) {
                        hasPendingChanges = true
                    }

                    // مزامنة الملف الشخصي محاولة مستقلة؛ لا تجعل فشلها يعلق مؤشر المزامنة.
                    val identityPrefs = AccountScopedPreferences.forCurrentAccount(context)
                    val legacyPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val savedName = identityPrefs?.getString("user_name", null)
                        ?: legacyPrefs.getString("user_name", null)
                        ?: legacyPrefs.getString("saved_user_name", null)
                    val savedEmail = identityPrefs?.getString("user_email", null)
                        ?: legacyPrefs.getString("user_email", null)
                        ?: legacyPrefs.getString("saved_login_email", null)
                    runCatching {
                        SupabaseRepository.getInstance().syncUserProfile(
                            userId = userId,
                            displayName = savedName,
                            email = savedEmail,
                            avatarUrl = avatarUrl ?: identityPrefs?.getString("user_avatar_uri", null),
                            preferencesJson = jsonPayload
                        )
                    }.onFailure {
                        // الإلغاء ليس خطأً ولا يُبتلَع (نمط الجولة 87): syncUserProfile
                        // دالة suspend، فإلغاء المزامنة أثناءها كان يُسجَّل فشلاً
                        // للملف الشخصي فقط وتتابع المزامنة كأن شيئاً لم يكن.
                        if (it is CancellationException) throw it
                        Log.e("UserPreferencesManager", "profile sync failed: ${it.message}")
                    }

                    if (success) {
                        val now = System.currentTimeMillis()
                        _lastSyncTime.value = now
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit().putLong("last_sync_timestamp", now).apply()
                    }
                    shouldReportSyncSuccess(
                        saveSucceeded = success,
                        hasPendingChanges = hasPendingChanges,
                        revisionAtSyncStart = syncRevision,
                        currentRevision = pendingSyncRevision.get()
                    )
                }
            } catch (cancelled: CancellationException) {
                hasPendingChanges = true
                throw cancelled
            } catch (error: Exception) {
                hasPendingChanges = true
                Log.e("UserPreferencesManager", "syncToSupabase failed: ${error.message}", error)
                false
            } finally {
                _isSyncing.value = false
            }
        }
    }

    suspend fun fetchAndApplyFromSupabase(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!SupabaseServices.isSupabaseAvailable) {
            return@withContext false
        }
        _isSyncing.value = true
        try {
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
                val identityPrefs = AccountScopedPreferences.forEmail(
                    context,
                    profile.email ?: SupabaseServices.Auth.getCurrentUserEmail().orEmpty()
                )
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
            }.onFailure {
                // الإلغاء ليس خطأً ولا يُبتلَع (نمط الجولة 87): getUserProfile
                // دالة suspend، فإلغاء الجلب أثناءها كان يُسجَّل فشلاً للاستعادة
                // فقط وتُعاد applied=false كأن السحابة بلا ملف.
                if (it is CancellationException) throw it
                Log.e("UserPreferencesManager", "profile restore failed: ${it.message}")
            }
            applied
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * إن كانت الصورة الشخصية محلياً (content:// أو ملف) ترفع إلى Supabase Storage
     * وتُستبدل في التفضيلات بالرابط العام. تُعيد الرابط السحابي أو null.
     */
    private suspend fun uploadLocalAvatarIfNeeded(context: Context, userId: String): String? =
        withContext(Dispatchers.IO) {
            val identityPrefs = AccountScopedPreferences.forCurrentAccount(context) ?: return@withContext null
            val uriStr = identityPrefs.getString("user_avatar_uri", null)?.takeIf { it.isNotBlank() }
                ?: return@withContext null
            if (uriStr.startsWith("http://") || uriStr.startsWith("https://")) return@withContext uriStr
            val storageUserId = SupabaseServices.Auth.getCurrentUserId()
                ?.takeIf { it == userId }
                ?: return@withContext null
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
                    path = "$storageUserId/avatar.$ext",
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
            }.onFailure {
                // الإلغاء ليس خطأً ولا يُبتلَع (نمط الجولة 87): uploadFile
                // دالة suspend، فإلغاء الرفع أثناءه كان يُسجَّل فشلاً فقط
                // وتتابع المزامنة بلا رابط صورة كأن الرفع فشل فحسب.
                if (it is CancellationException) throw it
                Log.e("UserPreferencesManager", "uploadLocalAvatarIfNeeded failed: ${it.message}")
            }
                .getOrNull()
        }

    fun triggerAutoSync(context: Context) {
        if (isCloudSyncBlockedByUnconfirmedEmail(context)) return
        if (_cloudSyncEnabled.value && SupabaseServices.isSupabaseAvailable) {
            pendingSyncRevision.incrementAndGet()
            hasPendingChanges = true
            if (_networkStatus.value == ConnectivityObserver.Status.Available) {
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    runCatching { syncToSupabase(context) }
                        .onFailure { if (it is CancellationException) throw it }
                }
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

    fun setCalculationMethod(context: Context, method: String) {
        _calculationMethod.value = method
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("calculation_method", method).apply()
        triggerAutoSync(context)
    }

    /** مذهب العصر: "SHAFI" (الجمهور، ظلّ مثله) أو "HANAFI" (ظلّ مثليه). */
    fun setAsrMadhab(context: Context, madhab: String) {
        val normalized = if (madhab.trim().uppercase(Locale.US) == "HANAFI") "HANAFI" else "SHAFI"
        _asrMadhab.value = normalized
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("asr_madhab", normalized).apply()
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
        // review-r12: مفتاحا توفير البيانات (العام هنا + مفتاح ريلز) كانا
        // مستقلين ويعِدان بالشيء نفسه بينما لا يقرأهما أي مستهلِك — الآن
        // يكتب كل منهما الآخر فيبقيان متفقين دائماً، والمستهلِك (المزامن
        // الصامت للأثر) يقرأ أياً منهما.
        runCatching { ReelPreferencesManager.setDataSaver(context, enable) }
        triggerAutoSync(context)
    }

    fun setAppLanguage(context: Context, lang: String) {
        _appLanguage.value = lang
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("app_language", lang).apply()
        Translator.setLanguage(lang)
        triggerAutoSync(context)
    }

    /**
     * آخر موضع قراءة في المصحف (الدفعة 5 — الجزء C، مُنقَل هنا لتكملة صف
     * «أكمل من حيث توقفت» في الدفعة 3). لا رقم صفحة مختلق أبداً: الصفحة
     * null عند جهلها، ويكتبها قارئ المصحف فقط عند القراءة الفعلية.
     */
    data class LastReadQuran(
        val surah: Int,
        val ayah: Int,
        val page: Int?,
        val atMillis: Long
    )

    fun getLastReadQuran(context: Context): LastReadQuran? {
        val prefs = context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE)
        val surah = prefs.getInt("last_read_surah", 0)
        if (surah !in 1..114) return null
        val ayah = prefs.getInt("last_read_ayah", 1).coerceAtLeast(1)
        val page = prefs.getInt("last_read_page", -1)
            .takeIf { it in 1..MushafPageData.PAGE_COUNT }
        return LastReadQuran(
            surah = surah,
            ayah = ayah,
            page = page,
            atMillis = prefs.getLong("last_read_at", 0L)
        )
    }

    fun saveLastReadQuran(context: Context, surah: Int, ayah: Int, page: Int?) {
        if (surah !in 1..114 || ayah < 1) return
        val validPage = page?.takeIf { it in 1..MushafPageData.PAGE_COUNT }
        context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt("last_read_surah", surah)
            .putInt("last_read_ayah", ayah)
            .apply { if (validPage != null) putInt("last_read_page", validPage) else remove("last_read_page") }
            .putLong("last_read_at", System.currentTimeMillis())
            .apply()
        // الكتابة على مفتاح مُزامَن تطلق المزامنة السحابية تلقائياً عبر
        // المستمع المسجّل في init() — لا استدعاء صريح هنا.
    }

    /**
     * علامات المصحف (خارطة المصحف — المرحلة 1): آية + لون + ملاحظة اختيارية.
     * تخزين محلي فقط في SharedPreferences (مصفوفة JSON تحت مفتاح
     * "mushaf_bookmarks") — المفتاح غير مدرج في CLOUD_SYNCED_QABAS_KEYS
     * عمداً فلا مزامنة سحابية ولا ادعاء بها: جدول library_reading_state
     * في Supabase مخصص لحالات قراءة كتب المكتبة (مفتاحه معرّف كتاب)
     * ولا يصلح لعلامات آيات قرآنية، فلا نعيد استخدامه هنا.
     */
    data class MushafBookmark(
        val surah: Int,
        val ayah: Int,
        val colorArgb: Int,
        val note: String,
        val createdAtMillis: Long
    )

    /** لوحة ألوان العلامات الثابتة: (قيمة ARGB، الاسم العربي). */
    val MUSHAF_BOOKMARK_COLORS: List<Pair<Int, String>> = listOf(
        0xFFD4AF37.toInt() to "ذهبي",
        0xFF10B981.toInt() to "أخضر",
        0xFF0EA5E9.toInt() to "أزرق",
        0xFF8B5CF6.toInt() to "بنفسجي",
        0xFFEC4899.toInt() to "وردي"
    )

    val MUSHAF_BOOKMARK_DEFAULT_COLOR: Int = 0xFFD4AF37.toInt()

    /** أقصى طول لملاحظة العلامة — يُقصّ الزائد عند الحفظ لا عند العرض. */
    const val MUSHAF_BOOKMARK_MAX_NOTE_LENGTH = 200

    fun getMushafBookmarks(context: Context): List<MushafBookmark> {
        val prefs = context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString("mushaf_bookmarks", null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val result = ArrayList<MushafBookmark>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val surah = obj.optInt("s", 0)
                val ayah = obj.optInt("a", 0)
                if (surah !in 1..114 || ayah < 1) continue
                result += MushafBookmark(
                    surah = surah,
                    ayah = ayah,
                    colorArgb = obj.optInt("c", MUSHAF_BOOKMARK_DEFAULT_COLOR),
                    note = obj.optString("n", "").take(MUSHAF_BOOKMARK_MAX_NOTE_LENGTH),
                    createdAtMillis = obj.optLong("t", 0L)
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** إضافة أو تحديث علامة (مفتاحها السورة:الآية)؛ الأحدث أولاً. */
    fun saveMushafBookmark(context: Context, bookmark: MushafBookmark) {
        if (bookmark.surah !in 1..114 || bookmark.ayah < 1) return
        val cleaned = bookmark.copy(
            note = bookmark.note.trim().take(MUSHAF_BOOKMARK_MAX_NOTE_LENGTH)
        )
        val kept = getMushafBookmarks(context)
            .filterNot { it.surah == cleaned.surah && it.ayah == cleaned.ayah }
        writeMushafBookmarks(context, listOf(cleaned) + kept)
    }

    fun removeMushafBookmark(context: Context, surah: Int, ayah: Int) {
        val kept = getMushafBookmarks(context)
            .filterNot { it.surah == surah && it.ayah == ayah }
        writeMushafBookmarks(context, kept)
    }

    private fun writeMushafBookmarks(context: Context, bookmarks: List<MushafBookmark>) {
        val array = JSONArray()
        for (bookmark in bookmarks) {
            array.put(
                JSONObject()
                    .put("s", bookmark.surah)
                    .put("a", bookmark.ayah)
                    .put("c", bookmark.colorArgb)
                    .put("n", bookmark.note)
                    .put("t", bookmark.createdAtMillis)
            )
        }
        context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("mushaf_bookmarks", array.toString())
            .apply()
    }

    /**
     * تفضيلات صوت المصحف (خارطة المصحف — المرحلة 2): سرعة التشغيل
     * (0.5–2.0، النطاق نفسه في UnifiedVideoPlayerSettings)، ووضع التكرار،
     * وآخر قارئ مختار — محلية فقط، مفاتيحها غير مدرجة في
     * CLOUD_SYNCED_QABAS_KEYS فلا مزامنة سحابية.
     */
    fun getMushafAudioSpeed(context: Context): Float =
        context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat("mushaf_audio_speed", 1f).coerceIn(0.5f, 2f)

    fun saveMushafAudioSpeed(context: Context, speed: Float) {
        context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat("mushaf_audio_speed", speed.coerceIn(0.5f, 2f)).apply()
    }

    fun getMushafAudioRepeat(context: Context): String =
        context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE)
            .getString("mushaf_audio_repeat", "OFF") ?: "OFF"

    fun saveMushafAudioRepeat(context: Context, mode: String) {
        context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("mushaf_audio_repeat", mode).apply()
    }

    fun getMushafAudioReciter(context: Context): String =
        context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE)
            .getString("mushaf_audio_reciter", "alafasy") ?: "alafasy"

    fun saveMushafAudioReciter(context: Context, reciterId: String) {
        context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("mushaf_audio_reciter", reciterId).apply()
    }

    /**
     * كتاب التفسير الافتراضي في لوحة دراسة الآية ومكتبة الكتب
     * (قسم القرآن — الكتب): محلي فقط، مفتاحه غير مدرج في
     * CLOUD_SYNCED_QABAS_KEYS فلا مزامنة سحابية. الافتراضي "muyassar"
     * (التفسير الميسر المحلي)؛ ومعرّف غير معروف يرجع إليه.
     */
    fun getMushafTafsirBook(context: Context): String =
        context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE)
            .getString("mushaf_tafsir_book", "muyassar")
            ?.takeIf { it in setOf("muyassar", "saadi", "ibn-kathir", "tabari", "jalalayn", "qurtubi", "baghawi", "wasit") }
            ?: "muyassar"

    fun saveMushafTafsirBook(context: Context, bookId: String) {
        val valid = bookId.takeIf { it in setOf("muyassar", "saadi", "ibn-kathir", "tabari", "jalalayn", "qurtubi", "baghawi", "wasit") }
            ?: "muyassar"
        context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("mushaf_tafsir_book", valid).apply()
    }

    /**
     * كتاب الإعراب الافتراضي في لوحة دراسة الآية (قسم القرآن — الكتب):
     * محلي فقط، مفتاحه غير مدرج في CLOUD_SYNCED_QABAS_KEYS فلا مزامنة
     * سحابية. الافتراضي "muyassar" (الإعراب الميسر)؛ ومعرّف غير معروف
     * يرجع إليه.
     */
    fun getMushafIrabBook(context: Context): String =
        context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE)
            .getString("mushaf_irab_book", "muyassar")
            ?.takeIf { it in setOf("muyassar", "darwish") }
            ?: "muyassar"

    fun saveMushafIrabBook(context: Context, bookId: String) {
        val valid = bookId.takeIf { it in setOf("muyassar", "darwish") } ?: "muyassar"
        context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("mushaf_irab_book", valid).apply()
    }

    /**
     * الترجمة الإنجليزية الافتراضية في القارئ ولوحة الدراسة
     * (قسم القرآن — الكتب): محلية فقط، مفتاحها غير مدرج في
     * CLOUD_SYNCED_QABAS_KEYS فلا مزامنة سحابية. الافتراضي
     * "english_saheeh"؛ ومعرّف غير معروف يرجع إليه.
     */
    fun getMushafTranslation(context: Context): String =
        context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE)
            .getString("mushaf_translation", "english_saheeh")
            ?.takeIf { it in setOf("english_saheeh", "english_hilali_khan", "english_rwwad") }
            ?: "english_saheeh"

    fun saveMushafTranslation(context: Context, translationId: String) {
        val valid = translationId.takeIf {
            it in setOf("english_saheeh", "english_hilali_khan", "english_rwwad")
        } ?: "english_saheeh"
        context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("mushaf_translation", valid).apply()
    }

    /**
     * إظهار الترجمة الإنجليزية تحت آيات المصحف (خارطة المصحف — المرحلة 3):
     * محلي فقط، مفتاحه غير مدرج في CLOUD_SYNCED_QABAS_KEYS فلا مزامنة
     * سحابية. الترجمة نفسها لا تُحفظ محلياً — تُجلب من QuranEnc عند
     * الطلب وتُخزَّن في ذاكرة QuranDataProvider فقط.
     */
    fun getMushafShowTranslation(context: Context): Boolean =
        context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("mushaf_show_translation", false)

    fun saveMushafShowTranslation(context: Context, show: Boolean) {
        context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("mushaf_show_translation", show).apply()
    }

    /**
     * عرض المصحف — المرحلة 5: ألوان التجويد، والرسم، والصفحتان.
     * محلية فقط، مفاتيحها غير مدرجة في CLOUD_SYNCED_QABAS_KEYS فلا
     * مزامنة سحابية. الرسم الافتراضي "uthmani" (نص التطبيق المحلي)؛
     * والرسم البديل يُجلب من QF العام عند اختياره فقط.
     */
    fun getMushafTajweed(context: Context): Boolean =
        context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("mushaf_tajweed", false)

    fun saveMushafTajweed(context: Context, show: Boolean) {
        context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("mushaf_tajweed", show).apply()
    }

    fun getMushafScript(context: Context): String =
        context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE)
            .getString("mushaf_script", "uthmani")
            ?.takeIf { it in setOf("uthmani", "uthmani_simple", "imlaei") }
            ?: "uthmani"

    fun saveMushafScript(context: Context, script: String) {
        val valid = script.takeIf { it in setOf("uthmani", "uthmani_simple", "imlaei") } ?: "uthmani"
        _mushafScript.value = valid
        context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("mushaf_script", valid).apply()
    }

    fun getMushafTwoPageSpread(context: Context): Boolean =
        context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("mushaf_two_page_spread", false)

    fun saveMushafTwoPageSpread(context: Context, enabled: Boolean) {
        context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("mushaf_two_page_spread", enabled).apply()
    }

    /**
     * خطة الختمة (خارطة المصحف — المرحلة 4): بداية الخطة وهدفها الزمني.
     * تخزين محلي فقط (كائن JSON تحت مفتاح "mushaf_khatmah_plan") — المفتاح
     * غير مدرج في CLOUD_SYNCED_QABAS_KEYS عمداً فلا مزامنة سحابية:
     * التقدّم يُحسب دائماً من موضع القراءة الفعلي (last_read_*) لا من
     * عدّاد مستقل، فلا يوجد ما يُزامَن أصلاً. startPage قد تكون null
     * إن أُنشئت الخطة قبل تحميل خريطة الصفحات؛ عندها يُحسب التقدّم
     * بعدد الآيات من startSurah/startAyah.
     */
    data class MushafKhatmahPlan(
        val startSurah: Int,
        val startAyah: Int,
        val startPage: Int?,
        val startMillis: Long,
        val targetDays: Int,
        val createdAtMillis: Long
    )

    fun getMushafKhatmahPlan(context: Context): MushafKhatmahPlan? {
        val prefs = context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString("mushaf_khatmah_plan", null) ?: return null
        return try {
            val obj = JSONObject(raw)
            val startSurah = obj.optInt("ss", 0)
            val startAyah = obj.optInt("sa", 0)
            val maxAyah = QuranDataProvider.surahs
                .firstOrNull { it.id == startSurah }?.versesCount ?: 0
            val startPage = obj.optInt("sp", -1).takeIf { it in 1..MushafPageData.PAGE_COUNT }
            val startMillis = obj.optLong("sm", 0L)
            val targetDays = obj.optInt("td", 0)
            if (startSurah !in 1..114 || startAyah !in 1..maxOf(1, maxAyah) ||
                startMillis <= 0L || targetDays !in 1..3650
            ) {
                return null
            }
            MushafKhatmahPlan(
                startSurah = startSurah,
                startAyah = startAyah,
                startPage = startPage,
                startMillis = startMillis,
                targetDays = targetDays,
                createdAtMillis = obj.optLong("ct", startMillis)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun saveMushafKhatmahPlan(context: Context, plan: MushafKhatmahPlan) {
        if (plan.startSurah !in 1..114 || plan.startAyah < 1 ||
            plan.startMillis <= 0L || plan.targetDays !in 1..3650
        ) {
            return
        }
        val obj = JSONObject()
            .put("ss", plan.startSurah)
            .put("sa", plan.startAyah)
            .put("sp", plan.startPage ?: -1)
            .put("sm", plan.startMillis)
            .put("td", plan.targetDays)
            .put("ct", plan.createdAtMillis)
        context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("mushaf_khatmah_plan", obj.toString()).apply()
    }

    fun clearMushafKhatmahPlan(context: Context) {
        context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove("mushaf_khatmah_plan").apply()
    }

    /**
     * وضع الحفظ (إخفاء نص الآيات للتسميع) — محلي فقط، مفتاحه غير مدرج
     * في CLOUD_SYNCED_QABAS_KEYS فلا مزامنة سحابية. الإخفاء عرض فقط:
     * النص نفسه لا يُغيَّر أبداً، والآيات المكشوفة في الجلسة تُحفظ في
     * ذاكرة الشاشة لا هنا.
     */
    fun getMushafMemorizationMode(context: Context): Boolean =
        context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("mushaf_memorization_mode", false)

    fun saveMushafMemorizationMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(QABAS_PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("mushaf_memorization_mode", enabled).apply()
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
            root.put("app", "Muslim AI Assistant Islamic Studio")
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
            // الإلغاء ليس خطأً ولا يُبتلَع (نمط الجولة 87): getAllHadithCardsOnce
            // وgetAllAudioTracksOnce دالتان suspend، فإلغاء التصدير أثناءهما
            // كان يعيد null فتُعامل المزامنة الإلغاء كفشل تصدير عادي.
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()
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
                // Update local StateFlows directly instead of recursive init() call
                _quranFontSize.value = prefsObj.optDouble("quran_font_size", 20.0).toFloat()
                _readingFontFamily.value = prefsObj.optString("reading_font_family", "amiri")
                _readingTheme.value = prefsObj.optString("reading_theme", "dark_luxury")
                _appLanguage.value = prefsObj.optString("app_language", "ar")
                Translator.setLanguage(_appLanguage.value)
                _highContrastMode.value = prefsObj.optBoolean("high_contrast_mode", false)
                // لا تُصفّر تقليل الحركة عند استعادة نسخة بلا المفتاح — قد يكون
                // موروثاً من إعداد النظام (انظر init)؛ يُكتب فقط إن ورد في النسخة.
                if (prefsObj.has("reduced_motion")) {
                    _reducedMotion.value = prefsObj.optBoolean("reduced_motion", false)
                }
                _globalTextScale.value = prefsObj.optDouble("global_text_scale", 1.0).toFloat()
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
    fun setHighContrastMode(context: Context, enabled: Boolean) {
        _highContrastMode.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("high_contrast_mode", enabled).apply()
        triggerAutoSync(context)
    }

    fun setReducedMotion(context: Context, enabled: Boolean) {
        _reducedMotion.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("reduced_motion", enabled).apply()
        triggerAutoSync(context)
    }

    fun setGlobalTextScale(context: Context, scale: Float) {
        _globalTextScale.value = scale
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putFloat("global_text_scale", scale).apply()
        triggerAutoSync(context)
    }

}
