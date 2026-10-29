package com.qabas.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import io.github.jan.supabase.postgrest.postgrest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val DEFAULT_REPO = "Ali-awwww/qabasapp"

    // الفحص التلقائي: ساعتان كحد أدنى مطلق، والمدة قابلة للتخصيص من مركز التنزيلات.
    // لا يمكن لأي قيمة محفوظة أو واردة من الواجهة أن تنزل تحت الحد الأدنى.
    const val MIN_AUTO_CHECK_INTERVAL_HOURS = 2
    const val DEFAULT_AUTO_CHECK_INTERVAL_HOURS = 2
    private const val PREF_AUTO_CHECK_INTERVAL_HOURS = "update_check_interval_hours"
    private const val PREF_LAST_CHECK = "update_last_check"

    fun getAutoCheckIntervalHours(context: Context): Int {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        return prefs.getInt(PREF_AUTO_CHECK_INTERVAL_HOURS, DEFAULT_AUTO_CHECK_INTERVAL_HOURS)
            .coerceAtLeast(MIN_AUTO_CHECK_INTERVAL_HOURS)
    }

    fun getAutoCheckIntervalMs(context: Context): Long =
        getAutoCheckIntervalHours(context) * 60L * 60L * 1000L

    fun setAutoCheckIntervalHours(context: Context, hours: Int): Int {
        val clamped = hours.coerceAtLeast(MIN_AUTO_CHECK_INTERVAL_HOURS)
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_AUTO_CHECK_INTERVAL_HOURS, clamped)
            .apply()
        return clamped
    }

    /** وقت آخر فحص ناجح أو «لا تحديث» (0 = لم يُفحص بعد). */
    fun getLastCheckTime(context: Context): Long =
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .getLong(PREF_LAST_CHECK, 0L)

    // توكن مسرّب متقاعد: يُرفض في كل المسارات ولا يُرسل أبداً.
    // (أبطل مفعوله من إعدادات GitHub أيضاً)
    private const val RETIRED_LEAKED_TOKEN = "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"

    fun isRetiredToken(token: String): Boolean = token.trim() == RETIRED_LEAKED_TOKEN

    fun getEffectiveRepo(context: Context): String {
        // قناة التحديث الرسمية إجبارية لغير المطور — لا يمكن لأي مستخدم
        // توجيه محدّث التطبيق نحو مستودع آخر (حماية من مستودعات خبيثة)
        if (!AdminGuard.isDashboardAccessAllowed(context)) {
            return DEFAULT_REPO
        }
        val active = runCatching { RepoVaultManager.getActiveRepo(context) }.getOrNull()
        if (active != null && active.owner.isNotBlank() && active.repo.isNotBlank()) {
            return "${active.owner.trim()}/${active.repo.trim()}"
        }
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val owner = prefs.getString("build_center_owner", null)?.trim()
        val repo = prefs.getString("build_center_repo", null)?.trim()
        if (!owner.isNullOrBlank() && !repo.isNullOrBlank()) {
            return "$owner/$repo"
        }
        return DEFAULT_REPO
    }

    suspend fun getEffectiveToken(context: Context): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            val cachedToken = prefs.getString("github_token_cached", null)?.trim()
            if (!cachedToken.isNullOrBlank()) {
                if (isRetiredToken(cachedToken)) {
                    Log.w(TAG, "⚠️ Retired leaked token found in cache — purging")
                    prefs.edit().remove("github_token_cached").apply()
                } else {
                    Log.d(TAG, "✅ Using cached GitHub token")
                    return@withContext cachedToken
                }
            }

            val token = fetchGitHubTokenFromSupabase()
            if (!token.isNullOrBlank() && !isRetiredToken(token)) {
                Log.d(TAG, "✅ GitHub token fetched from Supabase")
                prefs.edit().putString("github_token_cached", token).apply()
                return@withContext token
            }

            Log.w(TAG, "⚠️ Could not fetch token, continuing without auth")
            null
        } catch (e: Exception) {
            Log.e(TAG, "❌ getEffectiveToken error: ${e.message}")
            null
        }
    }

    private suspend fun fetchGitHubTokenFromSupabase(): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val supabase = SupabaseServices.client ?: return@withContext null

            val response = supabase.postgrest
                .from("app_secrets")
                .select {
                    filter {
                        eq("secret_key", "github_token")
                        eq("is_active", true)
                    }
                    limit(1)
                }
                .decodeSingleOrNull<AppSecret>()

            if (response != null && response.secret_value.isNotBlank()) {
                Log.d(TAG, "🔐 Token retrieved from Supabase")
                return@withContext response.secret_value
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "❌ fetchGitHubTokenFromSupabase: ${e.message}")
            null
        }
    }

    @Serializable
    data class AppSecret(
        val id: Long = 0,
        val secret_key: String = "",
        val secret_value: String = "",
        val is_active: Boolean = true
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    // عميل مخصص للتنزيلات الكبيرة (APK ~140MB): مهلات سخية حتى لا تموت
    // التنزيلات على الشبكات البطيئة دون أن يعرف المستخدم السبب.
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(10, TimeUnit.MINUTES)
        .build()

    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()

    private val _pendingUpdate = MutableStateFlow<UpdateInfo?>(null)
    val pendingUpdate: StateFlow<UpdateInfo?> = _pendingUpdate.asStateFlow()

    fun dismissUpdate() { 
        _pendingUpdate.value = null 
        isUpdateDismissedForSession = true
    }
    fun setPendingUpdate(update: UpdateInfo?) { _pendingUpdate.value = update }

    /** هل تم تجاهل التحديث في هذه الجلسة؟ (تُصفر عند إعادة التشغيل) */
    @Volatile var isUpdateDismissedForSession: Boolean = false
        private set

    private val _downloadResult = MutableStateFlow<Triple<String, Boolean, String>?>(null)
    val downloadResult: StateFlow<Triple<String, Boolean, String>?> = _downloadResult.asStateFlow()

    private val activeHandles = ConcurrentHashMap<String, DownloadHandle>()

    // ─── تثبيت معلّق: يُطلب صراحة أو يُستأنف بعد منح الصلاحية، ويُحفظ على القرص ───

    /**
     * سجل تثبيت معلّق: هوية النسخة (اسم + رقم بناء) + الحجم المتوقع للحزمة
     * + هل عُرض مسار الصلاحية على المستخدم مرة واحدة بالفعل.
     */
    private data class PendingInstallRecord(
        val versionName: String,
        val versionCode: Int,
        val apkSize: Long = 0L,
        val presented: Boolean = false
    )

    @Volatile private var pendingInstall: PendingInstallRecord? = null

    /** هل الواجهة في المقدمة؟ تُحدّث من دورة حياة MainActivity */
    @Volatile var appInForeground: Boolean = false
        private set

    fun onAppForeground() { appInForeground = true }
    fun onAppBackground() { appInForeground = false }

    private fun persistPendingInstall(context: Context, value: PendingInstallRecord?) {
        val p = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val e = p.edit()
        if (value != null) {
            e.putString("pending_install_version_name", value.versionName)
            e.putInt("pending_install_version_code", value.versionCode)
            e.putLong("pending_install_apk_size", value.apkSize)
            e.putBoolean("pending_install_presented", value.presented)
        } else {
            e.remove("pending_install_version_name")
                .remove("pending_install_version_code")
                .remove("pending_install_apk_size")
                .remove("pending_install_presented")
        }
        e.apply()
    }

    /** استرجاع التثبيت المعلّق من القرص (بعد إعادة تشغيل العملية) */
    private fun restorePendingInstall(context: Context): PendingInstallRecord? {
        val p = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val name = p.getString("pending_install_version_name", null) ?: return null
        val code = p.getInt("pending_install_version_code", -1)
        if (name.isBlank() || code <= 0) return null
        return PendingInstallRecord(
            versionName = name,
            versionCode = code,
            apkSize = p.getLong("pending_install_apk_size", 0L),
            presented = p.getBoolean("pending_install_presented", false)
        )
    }

    /** تسجيل نسخة جاهزة للتثبيت — تُحفظ في الذاكرة وعلى القرص معاً */
    fun setPendingInstall(
        context: Context,
        versionName: String,
        versionCode: Int,
        apkSize: Long = 0L
    ) {
        val record = PendingInstallRecord(versionName, versionCode, apkSize)
        pendingInstall = record
        persistPendingInstall(context, record)
    }

    fun clearPendingInstall(context: Context) {
        pendingInstall = null
        persistPendingInstall(context, null)
    }

    /**
     * محاولة تثبيت نسخة معلّقة — تُستدعى فقط عند نية صريحة من المستخدم
     * (نقرة «تثبيت الآن» والواجهة في المقدمة): إن كانت الصلاحية ممنوحة
     * يفتح المثبّت فوراً ويُلغي التعليق، وإلا يفتح شاشة منح الصلاحية
     * (ويظل التعليق قائماً ليُستأنف تلقائياً عند العودة).
     */
    fun attemptPendingInstall(context: Context) {
        val pending = pendingInstall ?: restorePendingInstall(context) ?: return
        pendingInstall = pending
        val apk = getValidatedDownloadedApk(
            context, pending.versionName, pending.versionCode, pending.apkSize
        ) ?: run {
            clearPendingInstall(context)
            return
        }
        launchInstaller(context, apk)
    }

    /**
     * استئناف التثبيت المعلّق — يُستدعى من دورة حياة الواجهة (onResume).
     * - الصلاحية ممنوحة → يفتح المثبّت مرة واحدة (تُستهلك قبل الفتح).
     * - الصلاحية غير ممنوحة ولم يُعرض مسارها بعد → يفتح شاشة المنح مرة واحدة فقط.
     * - الصلاحية غير ممنوحة وعُرض المسار سابقاً → لا شيء (لا يتكرر الإزعاج).
     */
    fun maybeResumePendingInstall(context: Context) {
        val pending = pendingInstall ?: restorePendingInstall(context) ?: return
        pendingInstall = pending
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

        if (!permissionGranted && pending.presented) return

        val apk = getValidatedDownloadedApk(
            context, pending.versionName, pending.versionCode, pending.apkSize
        ) ?: run {
            clearPendingInstall(context)
            return
        }

        launchInstaller(context, apk)
    }

    /**
     * تنظيف حالة التثبيت عند الإقلاع:
     * - إن أصبح الإصدار المثبّت >= النسخة المعلّقة فالتثبيت نجح → امسح السجل
     *   واحذف ملفات التحديث القديمة.
     * - إن بقي أقدم والحزمة سليمة → أعِد إشعار «تثبيت الآن» (لا نفتح المثبّت
     *   من الخلفية لأن أندرويد يحجب ذلك).
     * يعيد true إذا كانت هناك حزمة جاهزة للتثبيت فعلاً.
     */
    fun reconcilePendingInstall(context: Context): Boolean {
        val pending = pendingInstall ?: restorePendingInstall(context) ?: return false
        pendingInstall = pending
        val installedVersionCode = getCurrentVersionCode(context)

        if (installedVersionCode >= pending.versionCode) {
            clearPendingInstall(context)
            File(context.cacheDir, "updates").listFiles()?.forEach { file ->
                if (file.isFile) runCatching { file.delete() }
            }
            Log.d(
                TAG,
                "✅ Install reconciled: ${pending.versionName}+${pending.versionCode} " +
                    "already installed (current=$installedVersionCode)"
            )
            return false
        }

        val apk = getValidatedDownloadedApk(
            context, pending.versionName, pending.versionCode, pending.apkSize
        )
        if (apk == null) {
            clearPendingInstall(context)
            return false
        }

        UpdateDownloadService.notifyInstallReady(
            context, pending.versionName, pending.versionCode, pending.apkSize
        )
        return true
    }

    fun cancelDownload(key: String) {
        activeHandles[key]?.cancel()
    }

    @Serializable
    data class UpdateInfo(
        val versionName: String,
        val versionCode: Int,
        val deltaUrl: String?,
        val apkUrl: String,
        val releaseNotes: String,
        val deltaSize: Long,
        val apkSize: Long,
        val xdeltaUrls: Map<String, String> = emptyMap(),
        val isForce: Boolean = false,
        val checksum: String? = null
    ) {
        /** الدلتا تُستخدم فقط عندما تكون موجودة وأصغر من الحزمة الكاملة. */
        val deltaUsable: Boolean
            get() = !deltaUrl.isNullOrBlank() && deltaSize > 0L &&
                (apkSize <= 0L || deltaSize < apkSize)
    }

    data class GitHubReleaseInspection(
        val tagName: String,
        val releaseName: String,
        val publishedAt: String,
        val releaseNotes: String,
        val deltaAsset: String?,
        val deltaSize: Long,
        val deltaUrl: String?,
        val apkAsset: String?,
        val apkSize: Long,
        val apkUrl: String?,
        val xdeltaBinaries: List<String>,
        val xdeltaUrls: Map<String, String>,
        val totalAssetsCount: Int,
        val isNewerThanCurrent: Boolean
    )

    enum class Phase { DOWNLOADING_DELTA, APPLYING_PATCH, DOWNLOADING_FULL }

    data class DownloadProgress(
        val percent: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val phase: Phase,
        val speedBytesPerSec: Long
    )

    class DownloadHandle {
        @Volatile var cancelled: Boolean = false
            private set
        fun cancel() { cancelled = true }
    }

    private class DownloadCancelledException : Exception("أُلغي التحديث بواسطة المستخدم")

    suspend fun checkForUpdate(context: Context, force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val lastCheck = getLastCheckTime(context)
            val intervalMs = getAutoCheckIntervalMs(context)
            if (!force && System.currentTimeMillis() - lastCheck < intervalMs) {
                Log.d(TAG, "⏭️ Skipping auto check (interval=${intervalMs / 3_600_000L}h)")
                return@withContext null
            }

            val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            val currentVersionCode = getCurrentVersionCode(context)
            val currentVersionName = getCurrentVersionName(context)
            Log.d(TAG, "🔍 Checking updates… currentCode=$currentVersionCode, currentName=$currentVersionName")

            // GitHub Releases هي القناة الرسمية: إن فُحصت بنجاح فقرارها نهائي
            // (وجد تحديثاً → استُخدم، أو قال محدّث → لا تحديث).
            val githubUpdate = runCatching { checkGitHubLatestRelease(context) }
            if (githubUpdate.isSuccess) {
                return@withContext githubUpdate.getOrThrow()
            }
            Log.w(
                TAG,
                "⚠️ GitHub check failed (${githubUpdate.exceptionOrNull()?.message}) — Supabase fallback"
            )

            // فشل فحص GitHub نفسه (شبكة/API) — قناة Supabase احتياطية فقط عندئذٍ
            val supabaseUpdate = runCatching {
                SupabaseServices.Database.getLatestActiveUpdate()
            }.getOrNull()

            if (supabaseUpdate != null && supabaseUpdate.isActive) {
                // القاعدة الذهبية: التحديث يظهر فقط إذا زاد رقم الإصدار (versionName) لأعلى
                val nameComparison = compareVersionNames(supabaseUpdate.versionName, currentVersionName)
                val isNewer = nameComparison > 0

                if (isNewer) {
                    prefs.edit()
                        .putLong("update_last_check", System.currentTimeMillis())
                        .putInt("update_last_version_code", supabaseUpdate.versionCode)
                        .apply()

                    return@withContext UpdateInfo(
                        versionName = supabaseUpdate.versionName,
                        versionCode = supabaseUpdate.versionCode,
                        deltaUrl = null,
                        apkUrl = supabaseUpdate.apkUrl,
                        releaseNotes = supabaseUpdate.releaseNotes.ifBlank { "إصدار جديد متاح للتحميل والتثبيت الفوري 🚀" },
                        deltaSize = 0L,
                        apkSize = supabaseUpdate.fileSizeBytes,
                        xdeltaUrls = emptyMap()
                    )
                }
            }

            // لا تحديث في قناة الاحتياط أيضاً
            null
        } catch (e: Exception) {
            Log.e(TAG, "❌ check failed: ${e.message}")
            null
        }
    }

    /**
     * يمثّل دلتا واحدة مرفقة بالريليز مع الأساس الذي بُنيت منه.
     * الأسماء الجديدة: qabas-<target>-from-<source>.delta
     */
    private data class DeltaAsset(val name: String, val url: String, val size: Long)

    /**
     * يختار الدلتا المطابقة للإصدار المثبّت حالياً بدقة.
     * - لا يعتمد أبداً على «أول/آخر» دلتا في القائمة.
     * - يدعم الصيغة القديمة فقط عندما يكون هناك دلتا واحدة والبناء السابق مباشرة.
     */
    private fun selectDeltaForInstalled(
        deltas: List<DeltaAsset>,
        currentVersionName: String,
        currentVersionCode: Int,
        targetVersionCode: Int
    ): DeltaAsset? {
        if (deltas.isEmpty()) return null
        val sourceTag = "$currentVersionName+$currentVersionCode"
        val exact = deltas.filter { delta ->
            delta.name.contains("from-$sourceTag") ||
                delta.name.contains("from-$currentVersionCode.") ||
                delta.name.contains("from-$currentVersionCode-")
        }
        if (exact.isNotEmpty()) return exact.maxByOrNull { it.size }

        val legacy = deltas.filter { !it.name.contains("-from-") }
        if (legacy.size == 1 && currentVersionCode == targetVersionCode - 1) {
            return legacy.first()
        }
        return null
    }

    internal fun parseReleaseVersionCode(tag: String): Int = parseVersionCode(tag)

    /**
     * فحص أحدث ريليز على GitHub Releases — القناة الرسمية للتحديث التلقائي.
     * يقرأ قائمة الإصدارات (وليست /latest فقط) ويختار أعلى رقم بناء فعلي،
     * حتى لو كان أحدث إصدار منشوراً ليس هو الأعلى أو كانت هناك إصدارات متعددة.
     * يرمي استثناء عند فشل الفحص نفسه (شبكة/API) ويعيد null عند «لا تحديث».
     */
    private suspend fun checkGitHubLatestRelease(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val currentVersionCode = getCurrentVersionCode(context)
        val currentVersionName = getCurrentVersionName(context)

        val effectiveRepo = getEffectiveRepo(context)
        val effectiveToken = getEffectiveToken(context)

        val requestBuilder = Request.Builder()
            .url("https://api.github.com/repos/$effectiveRepo/releases?per_page=20")
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "Qabas-Studio")
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")

        if (!effectiveToken.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $effectiveToken")
        }

        val request = requestBuilder.build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            // فشل الفحص نفسه — يُرمى ليتيح قناة الاحتياط
            throw IllegalStateException("GitHub API ${response.code}")
        }

        val body = response.body?.string() ?: throw IllegalStateException("استجابة فارغة من GitHub")
        val releases = JSONArray(body)
        if (releases.length() == 0) {
            prefs.edit().putLong("update_last_check", System.currentTimeMillis()).apply()
            return@withContext null
        }

        data class Candidate(
            val tagName: String,
            val versionName: String,
            val versionCode: Int,
            val releaseNotes: String,
            val apkUrl: String,
            val apkSize: Long,
            val deltas: List<DeltaAsset>,
            val xdeltaUrls: Map<String, String>,
            val isForce: Boolean = false,
            val checksum: String? = null
        )

        var best: Candidate? = null
        for (i in 0 until releases.length()) {
            val release = releases.optJSONObject(i) ?: continue
            if (release.optBoolean("draft", false)) continue

            val tagName = release.optString("tag_name", "")
            val releaseVersionCode = parseReleaseVersionCode(tagName)
            val releaseVersionName = tagName.removePrefix("v").trim().substringBefore("+")

            // القاعدة الذهبية: نعتبر الإصدار جديداً فقط إذا كان اسم الإصدار (versionName) أعلى برمجياً (مثل 1.3.4 > 1.3.3)
            // هذا يمنح المطور السيطرة التامة، ويمنع ظهور أي تحديث لمجرد بناء جديد على نفس الإصدار.
            val nameComparison = compareVersionNames(releaseVersionName, currentVersionName)
            val isNewer = nameComparison > 0

            Log.d(TAG, "Comparing: remote=$releaseVersionName vs local=$currentVersionName => isNewer=$isNewer")

            if (!isNewer) continue

            val assets = release.optJSONArray("assets") ?: continue
            var apkUrl: String? = null
            var apkSize = 0L
            val deltas = mutableListOf<DeltaAsset>()
            val xdeltaUrls = mutableMapOf<String, String>()
            var releaseChecksum: String? = null

            for (j in 0 until assets.length()) {
                val asset = assets.optJSONObject(j) ?: continue
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                val size = asset.optLong("size", 0L)
                when {
                    name.endsWith(".delta") -> deltas += DeltaAsset(name, url, size)
                    name.endsWith(".apk") -> {
                        if (apkUrl == null || size > apkSize) {
                            apkUrl = url
                            apkSize = size
                        }
                    }
                    name.startsWith("xdelta3-") -> xdeltaUrls[name] = url
                    name.endsWith(".sha256") || name.endsWith(".md5") -> {
                        // محاولة جلب البصمة لو كانت نصية قصيرة في اسم الملف أو سنحتاج لتحميلها
                        // للتبسيط، نفترض أن البصمة قد تكون في الوصف أو اسم الملف
                        if (name.contains("delta")) releaseChecksum = name.substringBefore(".")
                    }
                }
            }

            if (apkUrl == null) continue

            val releaseNotes = release.optString("body", "")
            val isForce = releaseNotes.contains("[FORCE]", ignoreCase = true) || 
                         release.optString("name", "").contains("[FORCE]", ignoreCase = true)

            val candidate = Candidate(
                tagName = tagName,
                versionName = releaseVersionName,
                versionCode = releaseVersionCode,
                releaseNotes = releaseNotes,
                apkUrl = apkUrl,
                apkSize = apkSize,
                deltas = deltas,
                xdeltaUrls = xdeltaUrls,
                isForce = isForce,
                checksum = releaseChecksum
            )
            
            // اختيار الإصدار الأفضل (الأعلى اسماً، ثم الأعلى رقم بناء عند تساوي الأسماء)
            val currentBest = best
            val nameComparisonWithBest = compareVersionNames(candidate.versionName, currentBest?.versionName ?: "")
            
            val isBetter = when {
                currentBest == null -> true
                nameComparisonWithBest > 0 -> true
                nameComparisonWithBest < 0 -> false
                else -> candidate.versionCode > currentBest.versionCode
            }

            if (isBetter) {
                best = candidate
            }
        }

        val candidate = best
        if (candidate == null) {
            Log.d(TAG, "ℹ️ No newer version found on GitHub (current=$currentVersionName+$currentVersionCode)")
            prefs.edit().putLong("update_last_check", System.currentTimeMillis()).apply()
            return@withContext null
        }

        val chosenDelta = selectDeltaForInstalled(
            candidate.deltas,
            currentVersionName,
            currentVersionCode,
            candidate.versionCode
        )

        prefs.edit()
            .putLong("update_last_check", System.currentTimeMillis())
            .putInt("update_last_version_code", candidate.versionCode)
            .apply()

        Log.d(
            TAG,
            "✅ Update found: ${candidate.tagName} (${candidate.versionCode}) | delta=" +
                (chosenDelta?.name ?: "none (full APK)") +
                " | installed=$currentVersionName+$currentVersionCode"
        )

        UpdateInfo(
            versionName = candidate.versionName,
            versionCode = candidate.versionCode,
            deltaUrl = chosenDelta?.url,
            apkUrl = candidate.apkUrl,
            releaseNotes = candidate.releaseNotes,
            deltaSize = chosenDelta?.size ?: 0L,
            apkSize = candidate.apkSize,
            xdeltaUrls = candidate.xdeltaUrls,
            isForce = candidate.isForce,
            checksum = candidate.checksum
        )
    }

    suspend fun getUpdateHistory(context: Context): List<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val effectiveRepo = getEffectiveRepo(context)
            val effectiveToken = getEffectiveToken(context)

            val requestBuilder = Request.Builder()
                .url("https://api.github.com/repos/$effectiveRepo/releases?per_page=30")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Qabas-Studio")

            if (!effectiveToken.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $effectiveToken")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string() ?: return@withContext emptyList()
            val releases = JSONArray(body)
            val history = mutableListOf<UpdateInfo>()

            for (i in 0 until releases.length()) {
                val release = releases.optJSONObject(i) ?: continue
                if (release.optBoolean("draft", false)) continue

                val tagName = release.optString("tag_name", "")
                val releaseVersionCode = parseReleaseVersionCode(tagName)
                val releaseVersionName = tagName.removePrefix("v").trim().substringBefore("+")

                val assets = release.optJSONArray("assets") ?: continue
                var apkUrl = ""
                var apkSize = 0L
                val xdeltaUrls = mutableMapOf<String, String>()
                var releaseChecksum: String? = null

                for (j in 0 until assets.length()) {
                    val asset = assets.optJSONObject(j) ?: continue
                    val name = asset.optString("name")
                    val url = asset.optString("browser_download_url")
                    val size = asset.optLong("size", 0L)
                    when {
                        name.endsWith(".apk") -> {
                            if (apkUrl.isEmpty() || size > apkSize) {
                                apkUrl = url
                                apkSize = size
                            }
                        }
                        name.startsWith("xdelta3-") -> xdeltaUrls[name] = url
                        name.endsWith(".sha256") -> {
                            if (name.contains("delta")) releaseChecksum = name.substringBefore(".")
                        }
                    }
                }

                if (apkUrl.isEmpty()) continue

                val releaseNotes = release.optString("body", "")
                val isForce = releaseNotes.contains("[FORCE]", ignoreCase = true) || 
                             release.optString("name", "").contains("[FORCE]", ignoreCase = true)

                history.add(UpdateInfo(
                    versionName = releaseVersionName,
                    versionCode = releaseVersionCode,
                    deltaUrl = null, // لا نعرض الدلتا في السجل عادة
                    apkUrl = apkUrl,
                    releaseNotes = releaseNotes,
                    deltaSize = 0L,
                    apkSize = apkSize,
                    xdeltaUrls = xdeltaUrls,
                    isForce = isForce,
                    checksum = releaseChecksum
                ))
            }
            history.sortedByDescending { it.versionCode }
        } catch (e: Exception) {
            Log.e(TAG, "❌ getUpdateHistory failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun inspectGitHubRelease(
        context: Context,
        customRepo: String? = null,
        customToken: String? = null
    ): Result<GitHubReleaseInspection> = withContext(Dispatchers.IO) {
        runCatching {
            val targetRepo = customRepo?.trim()?.ifBlank { null } ?: getEffectiveRepo(context)
            val targetToken = customToken?.trim()?.ifBlank { null } ?: getEffectiveToken(context)

            val requestBuilder = Request.Builder()
                .url("https://api.github.com/repos/$targetRepo/releases/latest")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Qabas-Studio")

            if (!targetToken.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $targetToken")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("فشل الاتصال بـ GitHub (${response.code}): ${response.message}")
            }

            val body = response.body?.string() ?: throw IllegalStateException("استجابة فارغة من خادم GitHub")
            val json = org.json.JSONObject(body)
            val tagName = json.optString("tag_name", "")
            val name = json.optString("name", tagName)
            val publishedAt = json.optString("published_at", "")
            val releaseNotes = json.optString("body", "")
            val assets = json.optJSONArray("assets") ?: org.json.JSONArray()

            var deltaAsset: String? = null
            var deltaSize = 0L
            var deltaUrl: String? = null
            var apkAsset: String? = null
            var apkSize = 0L
            var apkUrl: String? = null
            val xdeltaBinaries = mutableListOf<String>()
            val xdeltaUrls = mutableMapOf<String, String>()

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val aName = asset.optString("name", "")
                val aSize = asset.optLong("size", 0L)
                val aUrl = asset.optString("browser_download_url", "")
                when {
                    aName.endsWith(".delta") -> {
                        deltaAsset = aName
                        deltaSize = aSize
                        deltaUrl = aUrl
                    }
                    aName.endsWith(".apk") -> {
                        apkAsset = aName
                        apkSize = aSize
                        apkUrl = aUrl
                    }
                    aName.startsWith("xdelta3-") -> {
                        xdeltaBinaries.add(aName)
                        xdeltaUrls[aName] = aUrl
                    }
                }
            }
            val currentVersionCode = getCurrentVersionCode(context)
            val currentVersionName = getCurrentVersionName(context)
            val releaseVersionCode = parseVersionCode(tagName)
            val releaseVersionName = tagName.removePrefix("v").trim().substringBefore("+")

            val nameComparison = compareVersionNames(releaseVersionName, currentVersionName)
            val finalIsNewer = nameComparison > 0

            GitHubReleaseInspection(
                tagName = tagName,
                releaseName = name,
                publishedAt = publishedAt,
                releaseNotes = releaseNotes,
                deltaAsset = deltaAsset,
                deltaSize = deltaSize,
                deltaUrl = deltaUrl,
                apkAsset = apkAsset,
                apkSize = apkSize,
                apkUrl = apkUrl,
                xdeltaBinaries = xdeltaBinaries,
                xdeltaUrls = xdeltaUrls,
                totalAssetsCount = assets.length(),
                isNewerThanCurrent = finalIsNewer
            )
        }
    }

    /** أحدث وسمين للإصدارات (الهدف + السابق) لتوليد الديلتا — للمطور فقط. */
    suspend fun fetchLatestTwoReleaseTags(
        owner: String,
        repo: String,
        token: String?
    ): Result<Pair<String, String?>> = withContext(Dispatchers.IO) {
        runCatching {
            val builder = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/releases?per_page=5")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Qabas-Studio")
            if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
            client.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("GitHub: ${resp.code}")
                val arr = JSONArray(resp.body?.string().orEmpty())
                if (arr.length() == 0) throw IllegalStateException("لا توجد إصدارات في المستودع")
                val target = arr.getJSONObject(0).optString("tag_name", "")
                if (target.isBlank()) throw IllegalStateException("تعذر قراءة وسم الإصدار الأخير")
                val prev = if (arr.length() > 1) {
                    arr.getJSONObject(1).optString("tag_name", "").ifBlank { null }
                } else null
                target to prev
            }
        }
    }

    /** حالة أحدث تشغيل لسير توليد الديلتا — لمنع التكرار وعرض التقدم الحي. */
    data class XdeltaRunInfo(
        val runId: Long,
        val status: String,
        val conclusion: String?,
        val createdAt: String,
        val event: String
    ) {
        fun isActive(): Boolean = status == "queued" || status == "in_progress" ||
            status == "waiting" || status == "requested" || status == "pending"
        fun isSuccess(): Boolean = status == "completed" && conclusion == "success"
        fun isFailed(): Boolean = status == "completed" && conclusion != null && conclusion != "success"
    }

    /** أحدث تشغيل لسير xdelta (الأول = الأحدث) — null عند غيابه أو تعذر القراءة. */
    suspend fun fetchLatestXdeltaRun(
        owner: String,
        repo: String,
        token: String?
    ): Result<XdeltaRunInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val builder = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/actions/workflows/xdelta--updates.yml/runs?per_page=5")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Qabas-Studio")
            if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
            client.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("GitHub: ${resp.code}")
                val runs = JSONObject(resp.body?.string().orEmpty()).optJSONArray("workflow_runs")
                    ?: return@runCatching null
                if (runs.length() == 0) return@runCatching null
                val r = runs.getJSONObject(0)
                XdeltaRunInfo(
                    runId = r.optLong("id", 0),
                    status = r.optString("status", ""),
                    conclusion = r.optString("conclusion", "").ifBlank { null },
                    createdAt = r.optString("created_at", ""),
                    event = r.optString("event", "")
                )
            }
        }
    }

    /**
     * تشغيل سير توليد الديلتا على GitHub (workflow_dispatch) — للمطور فقط.
     * يتطلب PAT بصلاحية actions:write. يرمي 204 عند القبول.
     */
    suspend fun dispatchDeltaWorkflow(        owner: String,
        repo: String,
        token: String,
        targetTag: String,
        prevTag: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("ref", "main")
                .put("inputs", JSONObject()
                    .put("target_tag", targetTag)
                    .put("previous_tag", prevTag ?: ""))
                .toString()
                .toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/actions/workflows/xdelta--updates.yml/dispatches")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Qabas-Studio")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.code == 204) return@withContext Result.success(Unit)
                val errBody = resp.body?.string().orEmpty().take(200)
                throw IllegalStateException("GitHub: ${resp.code} $errBody")
            }
        }
    }

    fun downloadAndInstall(
        context: Context,
        update: UpdateInfo,
        onProgress: (DownloadProgress) -> Unit = {},
        onDone: (Boolean, String) -> Unit = { _, _ -> },
        authToken: String? = null,
        forceFull: Boolean = false
    ): DownloadHandle {
        val handle = DownloadHandle()
        val key = "${update.versionName}|${update.versionCode}"
        activeHandles[key] = handle
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val emitProgress: (DownloadProgress) -> Unit = { p ->
            _downloadProgress.value = p
            mainHandler.post { onProgress(p) }
        }
        val emitDone: (Boolean, String) -> Unit = { ok, msg ->
            activeHandles.remove(key)
            _downloadResult.value = Triple(key, ok, msg)
            onDone(ok, msg)
        }
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val resolvedAuthToken = authToken ?: getEffectiveToken(context)
                val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }

                if (!forceFull && update.deltaUsable) {
                    Log.d(TAG, "⚡ Trying delta (${formatSize(update.deltaSize)})…")
                    val patched = tryDeltaUpdate(context, update, updatesDir, resolvedAuthToken, handle, emitProgress)
                    if (patched != null && patched.exists() && patched.length() > 1_000_000) {
                        if (isApkValid(context, patched, update.versionCode)) {
                            withContext(Dispatchers.Main) {
                                if (appInForeground) {
                                    // المقدمة: سجّل النسخة ثم افتح المثبّت. تُستهلك
                                    // النسخة فقط إذا فُتح المثبّت فعلاً، وإلا تبقى
                                    // قابلة للاستئناف (صلاحية/فشل فتح).
                                    setPendingInstall(context, update.versionName, update.versionCode, update.apkSize)
                                    launchInstaller(context, patched)
                                } else {
                                    // الخلفية: سجّل التثبيت الجاهز ليُعرض عند عودة
                                    // المستخدم (الإشعار متاح أيضاً إن لم تُرفض الإشعارات)
                                    setPendingInstall(context, update.versionName, update.versionCode, update.apkSize)
                                }
                                emitDone(
                                    true,
                                    "تحديث صغير ${update.versionName} (${formatSize(update.deltaSize)} بدل ${formatSize(update.apkSize)})"
                                )
                            }
                            return@launch
                        }
                        // الحزمة الناتجة عن الديلتا غير سليمة — تُحذف حتى لا تحجب
                        // الحزمة الكاملة عند البحث لاحقاً بالاسم ورقم البناء
                        runCatching { patched.delete() }
                    }
                    if (handle.cancelled) throw DownloadCancelledException()
                    Log.w(TAG, "⚠️ Delta failed — full APK fallback")
                }

                if (handle.cancelled) throw DownloadCancelledException()

                Log.d(TAG, "📥 Downloading full APK (${formatSize(update.apkSize)})…")
                val apkFile = downloadFile(
                    update.apkUrl, updatesDir,
                    "qabas-${update.versionName}-${update.versionCode}.apk", resolvedAuthToken, handle,
                    expectedSize = update.apkSize
                ) { bytes, total, speed ->
                    val pct = if (total > 0) (bytes * 100 / total).toInt().coerceIn(0, 100) else 0
                    emitProgress(DownloadProgress(pct, bytes, total, Phase.DOWNLOADING_FULL, speed))
                }

                if (handle.cancelled) throw DownloadCancelledException()

                if (apkFile != null && apkFile.exists() && apkFile.length() > 1_000_000) {
                    // الحجم الكلي معروف من الريليز — تطابق تام يضمن اكتمال التنزيل
                    val sizeMatches = update.apkSize <= 0L || apkFile.length() == update.apkSize
                    if (!sizeMatches || !isApkValid(context, apkFile, update.versionCode)) {
                        Log.e(TAG, "❌ Downloaded APK failed validation (size=${apkFile.length()}, expected=${update.apkSize})")
                        runCatching { apkFile.delete() }
                        withContext(Dispatchers.Main) { emitDone(false, "الحزمة المنزّلة غير سليمة — أعد المحاولة") }
                    } else {
                        withContext(Dispatchers.Main) {
                            if (appInForeground) {
                                // المقدمة: سجّل النسخة ثم افتح المثبّت. تُستهلك
                                // النسخة فقط إذا فُتح المثبّت فعلاً.
                                setPendingInstall(context, update.versionName, update.versionCode, update.apkSize)
                                launchInstaller(context, apkFile)
                            } else {
                                // الخلفية: سجّل التثبيت الجاهز ليُعرض عند عودة
                                // المستخدم (الإشعار متاح أيضاً إن لم تُرفض الإشعارات)
                                setPendingInstall(context, update.versionName, update.versionCode, update.apkSize)
                            }
                            emitDone(true, "تم تحميل ${update.versionName} (${formatSize(update.apkSize)})")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) { emitDone(false, "فشل تحميل التحديث") }
                }
            } catch (e: DownloadCancelledException) {
                withContext(Dispatchers.Main) { emitDone(false, "أُلغي التحديث") }
            } catch (e: Exception) {
                Log.e(TAG, "❌ downloadAndInstall: ${e.message}", e)
                val hint = if ((e.message ?: "").contains("401")) {
                    " — رمز GitHub المحفوظ مرفوض، حدّثه من الإعدادات ثم أعد المحاولة"
                } else ""
                withContext(Dispatchers.Main) { emitDone(false, "خطأ: ${e.message}$hint") }
            }
        }
        return handle
    }

    private suspend fun tryDeltaUpdate(
        context: Context,
        update: UpdateInfo,
        updatesDir: File,
        authToken: String?,
        handle: DownloadHandle,
        onProgress: (DownloadProgress) -> Unit
    ): File? {
        return try {
            val binary = ensureXdeltaBinary(context, update, authToken, handle)
                ?: run {
                    Log.w(TAG, "⚠️ No xdelta3 binary available")
                    UpdateTelemetryManager.recordFailure(
                        context = context,
                        update = update,
                        phase = "BINARY_RESOLVE",
                        throwable = null,
                        customMessage = "تعذر توفير أو تشغيل محرك xdelta3 للمعمارية الحالية"
                    )
                    return null
                }
            if (handle.cancelled) return null

            val deltaFile = downloadFile(
                update.deltaUrl!!, updatesDir,
                "qabas-${update.versionName}-${update.versionCode}.delta", authToken, handle,
                expectedSize = update.deltaSize
            ) { bytes, total, speed ->
                val pct = if (total > 0) (bytes * 100 / total).toInt().coerceIn(0, 100) else 0
                onProgress(DownloadProgress(pct, bytes, total, Phase.DOWNLOADING_DELTA, speed))
            } ?: run {
                if (!handle.cancelled) {
                    UpdateTelemetryManager.recordFailure(
                        context = context,
                        update = update,
                        phase = "DOWNLOAD_DELTA",
                        throwable = null,
                        customMessage = "فشل تنزيل ملف الدلتا (${formatSize(update.deltaSize)}) من المستودع"
                    )
                }
                return null
            }

            if (handle.cancelled) { deltaFile.delete(); return null }
            if (deltaFile.length() < 100) {
                UpdateTelemetryManager.recordFailure(
                    context = context,
                    update = update,
                    phase = "VALIDATE_DELTA_FILE",
                    throwable = null,
                    customMessage = "ملف الدلتا الذي تم تنزيله فارغ أو تالف (الحجم: ${deltaFile.length()} بايت)"
                )
                deltaFile.delete()
                return null
            }

            val sourceApk = File(context.applicationInfo.sourceDir)
            if (!sourceApk.exists()) {
                Log.w(TAG, "⚠️ source APK missing")
                UpdateTelemetryManager.recordFailure(
                    context = context,
                    update = update,
                    phase = "LOCATE_SOURCE_APK",
                    throwable = null,
                    customMessage = "تعذر الوصول إلى حزمة التطبيق المصدرية المثبتة (${sourceApk.absolutePath})"
                )
                return null
            }

            val outApk = File(updatesDir, "qabas-${update.versionName}-${update.versionCode}-patched.apk.part")
            if (outApk.exists()) outApk.delete()

            onProgress(DownloadProgress(0, 0, update.deltaSize, Phase.APPLYING_PATCH, 0))
            
            // استخدام PatchManager لمعالجة الرقعة والتحقق من سلامتها
            val patchSuccess = PatchManager.applyXdeltaPatch(context, update, deltaFile)
            
            if (handle.cancelled) return null
            onProgress(DownloadProgress(100, update.deltaSize, update.deltaSize, Phase.APPLYING_PATCH, 0))

            if (patchSuccess) {
                return UpdateManager.getDownloadedApkFile(context, update.versionName, update.versionCode)
            } else {
                val state = PatchManager.patchState.value
                val errorMsg = if (state is PatchManager.PatchState.Error) state.message else "فشلت معالجة الرقعة"
                Log.w(TAG, "⚠️ PatchManager failed: $errorMsg")
                
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "فشل التحديث التفاضلي: $errorMsg", android.widget.Toast.LENGTH_LONG).show()
                }
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ tryDeltaUpdate: ${e.message}", e)
            UpdateTelemetryManager.recordFailure(
                context = context,
                update = update,
                phase = "DELTA_PIPELINE",
                throwable = e,
                customMessage = "استثناء غير معالج في مسار الدلتا: ${e.message}"
            )
            null
        }
    }
    internal fun ensureXdeltaBinary(
        context: Context,
        update: UpdateInfo,
        authToken: String?,
        handle: DownloadHandle
    ): File? {
        val dest = File(context.filesDir, "bin/libxdelta3.so")
        if (dest.exists() && dest.canExecute() && dest.length() > 10_000) {
            return dest
        }

        val abi = preferredAbi()
        val assetName = "xdelta3-$abi"
        val url = update.xdeltaUrls[assetName]
            ?: update.xdeltaUrls.entries.firstOrNull {
                val key = it.key.lowercase()
                key.contains(abi.lowercase()) || 
                (abi == "arm64-v8a" && (key.contains("arm64") || key.contains("aarch64"))) ||
                (abi == "armeabi-v7a" && (key.contains("armv7") || key.contains("armeabi"))) ||
                (abi == "x86_64" && key.contains("amd64"))
            }?.value

        if (url.isNullOrBlank()) {
            Log.w(TAG, "⚠️ No xdelta3 asset for ABI=$abi in release (have: ${update.xdeltaUrls.keys})")
            return null
        }

        Log.d(TAG, "📥 Downloading $assetName…")
        val tmp = downloadFile(url, context.cacheDir, "xdelta3.download", authToken, handle) { _, _, _ -> }
            ?: return null

        dest.parentFile?.mkdirs()
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
        dest.setExecutable(true, false)
        dest.setReadable(true, false)

        return if (dest.exists() && dest.length() > 10_000) dest else null
    }

    private fun preferredAbi(): String {
        val abis = Build.SUPPORTED_ABIS
        return when {
            abis.any { it == "arm64-v8a" } -> "arm64-v8a"
            abis.any { it == "armeabi-v7a" } -> "armeabi-v7a"
            abis.any { it == "x86_64" } -> "x86_64"
            abis.any { it == "x86" } -> "x86"
            else -> abis.firstOrNull() ?: "arm64-v8a"
        }
    }

    internal fun applyXdelta(binary: File, source: File, delta: File, output: File): Result<String> {
        val args = listOf(
            "-d",
            "-s", source.absolutePath,
            delta.absolutePath,
            output.absolutePath
        )
        // Android 10+ (targetSdk ≥ 29): SELinux يمنع execve المباشرة من مجلد التطبيق
        // (قيد W^X). الحل المعتمد: تشغيل الملف عبر ملتفّ النظام الذي يحمله نيابةً عنه.
        // Android < 10 ينجح فيه التنفيذ المباشر، والباقي عبر /system/bin/linker[64].
        val runners = listOf(
            listOf(binary.absolutePath),
            listOf("/system/bin/linker64", binary.absolutePath),
            listOf("/system/bin/linker", binary.absolutePath)
        )
        var lastFailure = "لم يُشغَّل"
        var fullLog = ""
        
        for (prefix in runners) {
            try {
                val cmd = prefix + args
                Log.d(TAG, "exec: ${cmd.joinToString(" ")}")
                val process = ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start()
                
                val log = process.inputStream.bufferedReader().readText()
                fullLog = log
                val code = process.waitFor()
                
                if (code == 0) return Result.success(log)
                
                lastFailure = "exit=$code via ${prefix.first()} — ${log.take(300)}"
                Log.w(TAG, "⚠️ xdelta3 runner failed: $lastFailure")
            } catch (e: Exception) {
                lastFailure = "${prefix.first()}: ${e.message}"
                Log.w(TAG, "⚠️ applyXdelta runner threw: $lastFailure")
            }
        }
        
        Log.e(TAG, "❌ applyXdelta all runners failed — $lastFailure")
        return Result.failure(Exception(lastFailure + "\nLog: " + fullLog.take(200)))
    }

    private fun downloadFile(
        url: String,
        destDir: File,
        fileName: String,
        authToken: String? = null,
        handle: DownloadHandle,
        expectedSize: Long? = null,
        onProgress: (bytesRead: Long, total: Long, speedBps: Long) -> Unit
    ): File? {
        val builder = Request.Builder().url(url)
        if (!authToken.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $authToken")
            if (url.contains("/releases/assets/")) {
                builder.header("Accept", "application/octet-stream")
            }
        }
        val call = downloadClient.newCall(builder.build())
        val file = File(destDir, fileName)
        // كتابة إلى ملف مؤقت ثم إعادة تسمية عند الاكتمال — حتى لا يُعامل
        // تنزيل جزئي مقطوع على أنه حزمة كاملة قابلة للتثبيت.
        val partFile = File(destDir, "$fileName.part")
        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                val detail = "HTTP ${response.code} ${response.message.ifBlank { "فشل الاتصال" }}"
                Log.w(TAG, "❌ $detail $url")
                throw IllegalStateException("تعذر التنزيل ($detail)")
            }
            val body = response.body
                ?: throw IllegalStateException("تعذر التنزيل (استجابة فارغة من الخادم)")
            val total = body.contentLength()

            var windowStart = System.currentTimeMillis()
            var windowBytes = 0L
            var lastSpeed = 0L

            body.byteStream().use { input ->
                partFile.outputStream().use { output ->
                    val buf = ByteArray(65536)
                    var n: Int
                    var read = 0L
                    while (true) {
                        if (handle.cancelled) {
                            call.cancel()
                            throw DownloadCancelledException()
                        }
                        n = input.read(buf)
                        if (n == -1) break
                        output.write(buf, 0, n)
                        read += n
                        windowBytes += n

                        val now = System.currentTimeMillis()
                        val elapsed = now - windowStart
                        if (elapsed >= 200) {
                            lastSpeed = (windowBytes * 1000L / elapsed)
                            windowStart = now
                            windowBytes = 0
                        }
                        onProgress(read, total, lastSpeed)
                    }
                    onProgress(read, total, lastSpeed)
                    output.flush()
                }
            }
            // الاكتمال: تحقق من الحجم المعروف قبل الترويج إلى الاسم النهائي —
            // مطابقة بيانات الحزمة وحدها لا تثبت اكتمال التنزيل بالبايتات
            if (expectedSize != null && expectedSize > 0 && partFile.length() != expectedSize) {
                throw IllegalStateException(
                    "تنزيل غير مكتمل (${partFile.length()} من $expectedSize بايت)"
                )
            }
            // الاكتمال: إعادة تسمية آلية إلى الاسم النهائي
            if (file.exists()) file.delete()
            if (!partFile.renameTo(file)) {
                throw IllegalStateException("تعذر إنهاء التنزيل (فشل إعادة التسمية)")
            }
            return file
        } catch (e: DownloadCancelledException) {
            try { partFile.delete() } catch (_: Exception) {}
            try { file.delete() } catch (_: Exception) {}
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "❌ download: ${e.message}")
            try { partFile.delete() } catch (_: Exception) {}
            try { file.delete() } catch (_: Exception) {}
            return null
        }
    }

    private fun signatureHashes(pkgInfo: PackageInfo?): List<String> {
        if (pkgInfo == null) return emptyList()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signing = pkgInfo.signingInfo ?: return emptyList()
                val certs = if (signing.hasMultipleSigners()) {
                    signing.apkContentsSigners
                } else {
                    signing.signingCertificateHistory
                }
                certs?.map { cert ->
                    java.security.MessageDigest.getInstance("SHA-256")
                        .digest(cert.toByteArray())
                        .joinToString("") { "%02X".format(it) }
                } ?: emptyList()
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.signatures?.map { cert ->
                    java.security.MessageDigest.getInstance("SHA-256")
                        .digest(cert.toByteArray())
                        .joinToString("") { "%02X".format(it) }
                } ?: emptyList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "signatureHashes failed: ${e.message}")
            emptyList()
        }
    }

    /** true إذا كان الحزمة المثبتة بنفس الاسم لكن بتوقيع مختلف عن الـ APK الجديد */
    private fun hasSignatureConflict(context: Context, apkFile: File): Boolean {
        return try {
            val installed = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            val archive = context.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.GET_SIGNING_CERTIFICATES
            ) ?: return false
            val installedHashes = signatureHashes(installed)
            val archiveHashes = signatureHashes(archive)
            if (installedHashes.isEmpty() || archiveHashes.isEmpty()) return false
            installedHashes.none { it in archiveHashes }
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            Log.w(TAG, "hasSignatureConflict failed: ${e.message}")
            false
        }
    }

    /**
     * يفتح مثبّت النظام لحزمة محددة.
     * يعيد true فقط إذا فُتح المثبّت فعلاً (وتُستهلك النسخة المعلّقة عندها).
     * عند غياب صلاحية المصادر المجهولة يفتح شاشة المنح ويسجّل النسخة
     * للاستئناف عند العودة، ويعيد false.
     */
    fun launchInstaller(context: Context, apkFile: File): Boolean {
        val archiveInfo = runCatching {
            context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
        }.getOrNull()
        val archiveName = archiveInfo?.versionName ?: ""
        val archiveCode = archiveInfo
            ?.let { PackageInfoCompat.getLongVersionCode(it).toInt() } ?: -1

        fun recordPending(presented: Boolean) {
            if (archiveName.isBlank() || archiveCode <= 0) return
            val existing = pendingInstall ?: restorePendingInstall(context)
            val size = if (existing != null && existing.versionName == archiveName &&
                existing.versionCode == archiveCode
            ) existing.apkSize else 0L
            val record = PendingInstallRecord(archiveName, archiveCode, size, presented)
            pendingInstall = record
            persistPendingInstall(context, record)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                // سجّل النسخة المعلّقة من بيانات الحزمة نفسها (اسم + رقم بناء)،
                // وعلّم «عُرض مسار الصلاحية» فوراً — شاشة المنح تُفتح مرة واحدة فقط
                recordPending(presented = true)
                try {
                    context.startActivity(
                        Intent(
                            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    android.widget.Toast.makeText(
                        context,
                        "فعّل «السماح من هذا المصدر» وسيُستأنف التثبيت تلقائياً",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Could not open unknown-sources settings: ${e.message}")
                }
                return false
            }
        }
        // كشف تعارض التوقيع قبل فتح المثبّت: النسخة المثبتة موقّعة بمفتاح مختلف
        if (hasSignatureConflict(context, apkFile)) {
            android.widget.Toast.makeText(
                context,
                "النسخة المثبتة موقّعة بمفتاح مختلف — لا يمكن التحديث فوقها، أزل النسخة القديمة يدوياً ثم ثبّت الجديدة",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return false
        }

        // سجّل أولاً حتى لا تضيع الفرصة إذا رفض النظام فتح المثبّت
        if (pendingInstall == null && archiveName.isNotBlank() && archiveCode > 0) {
            recordPending(presented = false)
        }

        return try {
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", apkFile
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            // تُستهلك قبل أن يلغيها المستخدم حتى لا يُعاد فتح المثبّت تلقائياً
            clearPendingInstall(context)
            true
        } catch (e: Exception) {
            Log.e(TAG, "launchInstaller failed: ${e.message}", e)
            android.widget.Toast.makeText(
                context,
                "تعذّر فتح مثبّت النظام — استخدم زر «تثبيت» من مركز التنزيلات",
                android.widget.Toast.LENGTH_LONG
            ).show()
            false
        }
    }

    /**
     * الحزمة المنزّلة لإصدار محدد — يُبحث بالاسم ورقم البناء معاً (versionName+versionCode)
     * لأن عدة ريليزات قد تتشارك versionName نفسه بأرقام بناء مختلفة.
     */
    fun getDownloadedApkFile(context: Context, versionName: String, versionCode: Int): File? {
        val updatesDir = File(context.cacheDir, "updates")
        val patched = File(updatesDir, "qabas-$versionName-$versionCode-patched.apk")
        if (patched.exists() && patched.length() > 1_000_000) return patched
        val full = File(updatesDir, "qabas-$versionName-$versionCode.apk")
        if (full.exists() && full.length() > 1_000_000) return full
        return null
    }

    /**
     * تحقق من سلامة الحزمة المنزّلة: مطابقة اسم الحزمة ورقم البناء المتوقع تماماً
     * (عقد الوسم v{X.Y.Z}+{N}). يمنع تثبيت ملف تالف أو جزئي أو حزمة إصدار آخر.
     */
    fun isApkValid(context: Context, file: File, expectedVersionCode: Int): Boolean {
        return try {
            val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0) ?: return false
            if (info.packageName != context.packageName) return false
            PackageInfoCompat.getLongVersionCode(info).toInt() == expectedVersionCode
        } catch (_: Exception) {
            false
        }
    }

    /**
     * الحزمة المنزّلة لإصدار محدد إن كانت سليمة ومطابقة — وتُحذف تلقائياً إن كانت تالفة.
     * يتحقق من كل مرشح على حدة: المُرقّعة أولاً ثم الكاملة، حتى لا تحجب
     * حزمة تالفة حزمة سليمة تحمل نفس الاسم ورقم البناء. عند معرفة الحجم
     * المتوقع (من الريليز أو سجل التثبيت المعلّق) يُشترط تطابقه تماماً.
     */
    fun getValidatedDownloadedApk(
        context: Context,
        versionName: String,
        versionCode: Int,
        expectedSize: Long = 0L
    ): File? {
        val updatesDir = File(context.cacheDir, "updates")
        val patched = File(updatesDir, "qabas-$versionName-$versionCode-patched.apk")
        if (patched.exists() && patched.length() > 1_000_000) {
            val sizeOk = expectedSize <= 0L || patched.length() == expectedSize
            if (sizeOk && isApkValid(context, patched, versionCode)) return patched
            Log.w(TAG, "⚠️ Patched APK failed validation — deleting: ${patched.name}")
            runCatching { patched.delete() }
        }
        val full = File(updatesDir, "qabas-$versionName-$versionCode.apk")
        if (full.exists() && full.length() > 1_000_000) {
            val sizeOk = expectedSize <= 0L || full.length() == expectedSize
            if (sizeOk && isApkValid(context, full, versionCode)) return full
            Log.w(TAG, "⚠️ Downloaded APK failed validation — deleting: ${full.name}")
            runCatching { full.delete() }
        }
        return null
    }

    /** أحدث حزمة تحديث منزّلة في مجلد التحديثات — للواجهات التشخيصية. */
    fun getLatestDownloadedUpdateApk(context: Context): File? {
        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.isDirectory) return null
        return updatesDir.listFiles()
            ?.filter { it.name.startsWith("qabas-") && it.name.endsWith(".apk") && it.length() > 1_000_000 }
            ?.maxByOrNull { it.lastModified() }
    }

    fun getCurrentVersionCode(context: Context): Int = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        PackageInfoCompat.getLongVersionCode(info).toInt()
    } catch (_: PackageManager.NameNotFoundException) {
        0
    }

    fun getCurrentVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (_: PackageManager.NameNotFoundException) {
        ""
    }

    private fun compareVersionNames(a: String, b: String): Int {
        val pa = a.removePrefix("v").trim().split(".")
        val pb = b.removePrefix("v").trim().split(".")
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrNull(i)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
            val y = pb.getOrNull(i)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
            if (x != y) return x - y
        }
        return 0
    }

    private fun parseVersionCode(tag: String): Int {
        val buildPart = tag.substringAfter("+", "")
        if (buildPart.isNotEmpty()) {
            return buildPart.filter { it.isDigit() }.toIntOrNull() ?: 0
        }
        return 0
    }

    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "${bytes} بايت"
        bytes < 1024 * 1024 -> "${bytes / 1024} ك.ب"
        else -> "${"%.1f".format(bytes / 1024.0 / 1024.0)} م.ب"
    }

    fun formatSpeed(bytesPerSec: Long): String = "${formatSize(bytesPerSec)}/ث"
}
