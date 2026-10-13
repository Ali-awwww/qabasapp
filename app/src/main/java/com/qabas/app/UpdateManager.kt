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
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

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

    fun dismissUpdate() { _pendingUpdate.value = null }
    fun setPendingUpdate(update: UpdateInfo?) { _pendingUpdate.value = update }

    private val _downloadResult = MutableStateFlow<Triple<String, Boolean, String>?>(null)
    val downloadResult: StateFlow<Triple<String, Boolean, String>?> = _downloadResult.asStateFlow()

    private val activeHandles = ConcurrentHashMap<String, DownloadHandle>()

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
        val xdeltaUrls: Map<String, String> = emptyMap()
    )

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
            val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            val lastCheck = prefs.getLong("update_last_check", 0)
            if (!force && System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) {
                Log.d(TAG, "⏭️ Skipping check (last check too recent)")
                return@withContext null
            }

            val currentVersionCode = getCurrentVersionCode(context)
            val currentVersionName = getCurrentVersionName(context)
            Log.d(TAG, "🔍 Checking updates… currentCode=$currentVersionCode, currentName=$currentVersionName")

            val supabaseUpdate = runCatching {
                SupabaseServices.Database.getLatestActiveUpdate()
            }.getOrNull()

            if (supabaseUpdate != null && supabaseUpdate.isActive) {
                val isNewerCode = supabaseUpdate.versionCode > currentVersionCode
                val isNewerName = compareVersionNames(supabaseUpdate.versionName, currentVersionName) > 0
                if (isNewerCode || isNewerName) {
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

            val effectiveRepo = getEffectiveRepo(context)
            val effectiveToken = getEffectiveToken(context)

            val requestBuilder = Request.Builder()
                .url("https://api.github.com/repos/$effectiveRepo/releases/latest")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Qabas-Studio")

            if (!effectiveToken.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $effectiveToken")
            }

            val request = requestBuilder.build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.w(TAG, "⚠️ GitHub API ${response.code}")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val json = org.json.JSONObject(body)
            val tagName = json.optString("tag_name", "")
            val releaseNotes = json.optString("body", "")
            val assets = json.getJSONArray("assets")

            val releaseVersionCode = parseVersionCode(tagName)
            val tagHasBuildNumber = tagName.contains("+")
            val releaseVersionName = tagName.removePrefix("v").trim().substringBefore("+")

            if (tagHasBuildNumber && releaseVersionCode <= currentVersionCode) {
                prefs.edit().putLong("update_last_check", System.currentTimeMillis()).apply()
                return@withContext null
            }
            if (!tagHasBuildNumber && compareVersionNames(releaseVersionName, currentVersionName) <= 0) {
                prefs.edit().putLong("update_last_check", System.currentTimeMillis()).apply()
                return@withContext null
            }

            var deltaUrl: String? = null
            var deltaSize = 0L
            var apkUrl: String? = null
            var apkSize = 0L
            val xdeltaUrls = mutableMapOf<String, String>()

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                val url = asset.getString("browser_download_url")
                val size = asset.getLong("size")
                when {
                    name.endsWith(".delta") -> {
                        deltaUrl = url; deltaSize = size
                    }
                    name.endsWith(".apk") -> {
                        apkUrl = url; apkSize = size
                    }
                    name.startsWith("xdelta3-") -> {
                        xdeltaUrls[name] = url
                    }
                }
            }

            if (apkUrl == null) {
                Log.w(TAG, "⚠️ No APK in release")
                return@withContext null
            }

            prefs.edit()
                .putLong("update_last_check", System.currentTimeMillis())
                .putInt("update_last_version_code", releaseVersionCode)
                .apply()

            Log.d(TAG, "✅ Update found: $tagName")
            UpdateInfo(
                versionName = tagName.removePrefix("v").trim().substringBefore("+"),
                versionCode = releaseVersionCode,
                deltaUrl = deltaUrl,
                apkUrl = apkUrl!!,
                releaseNotes = releaseNotes,
                deltaSize = deltaSize,
                apkSize = apkSize,
                xdeltaUrls = xdeltaUrls
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ check failed: ${e.message}")
            null
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
            val tagHasBuildNumber = tagName.contains("+")
            val releaseVersionName = tagName.removePrefix("v").trim().substringBefore("+")

            val isNewer = if (tagHasBuildNumber) {
                releaseVersionCode > currentVersionCode
            } else {
                compareVersionNames(releaseVersionName, currentVersionName) > 0
            }

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
                isNewerThanCurrent = isNewer
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

    /**
     * تشغيل سير توليد الديلتا على GitHub (workflow_dispatch) — للمطور فقط.
     * يتطلب PAT بصلاحية actions:write. يرمي 204 عند القبول.
     */
    suspend fun dispatchDeltaWorkflow(
        owner: String,
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

                if (!forceFull && !update.deltaUrl.isNullOrBlank() && update.deltaSize > 0) {
                    Log.d(TAG, "⚡ Trying delta (${formatSize(update.deltaSize)})…")
                    val patched = tryDeltaUpdate(context, update, updatesDir, resolvedAuthToken, handle, emitProgress)
                    if (patched != null && patched.exists() && patched.length() > 1_000_000) {
                        withContext(Dispatchers.Main) {
                            launchInstaller(context, patched)
                            emitDone(
                                true,
                                "تحديث صغير ${update.versionName} (${formatSize(update.deltaSize)} بدل ${formatSize(update.apkSize)})"
                            )
                        }
                        return@launch
                    }
                    if (handle.cancelled) throw DownloadCancelledException()
                    Log.w(TAG, "⚠️ Delta failed — full APK fallback")
                }

                if (handle.cancelled) throw DownloadCancelledException()

                Log.d(TAG, "📥 Downloading full APK (${formatSize(update.apkSize)})…")
                val apkFile = downloadFile(
                    update.apkUrl, updatesDir,
                    "qabas-${update.versionName}.apk", resolvedAuthToken, handle
                ) { bytes, total, speed ->
                    val pct = if (total > 0) (bytes * 100 / total).toInt().coerceIn(0, 100) else 0
                    emitProgress(DownloadProgress(pct, bytes, total, Phase.DOWNLOADING_FULL, speed))
                }

                if (handle.cancelled) throw DownloadCancelledException()

                if (apkFile != null && apkFile.exists() && apkFile.length() > 1_000_000) {
                    withContext(Dispatchers.Main) {
                        launchInstaller(context, apkFile)
                        emitDone(true, "تم تحميل ${update.versionName} (${formatSize(update.apkSize)})")
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

    private fun tryDeltaUpdate(
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
                    return null
                }
            if (handle.cancelled) return null

            val deltaFile = downloadFile(
                update.deltaUrl!!, updatesDir,
                "qabas-${update.versionName}.delta", authToken, handle
            ) { bytes, total, speed ->
                val pct = if (total > 0) (bytes * 100 / total).toInt().coerceIn(0, 100) else 0
                onProgress(DownloadProgress(pct, bytes, total, Phase.DOWNLOADING_DELTA, speed))
            } ?: return null

            if (handle.cancelled) { deltaFile.delete(); return null }
            if (deltaFile.length() < 100) return null

            val sourceApk = File(context.applicationInfo.sourceDir)
            if (!sourceApk.exists()) {
                Log.w(TAG, "⚠️ source APK missing")
                return null
            }

            val outApk = File(updatesDir, "qabas-${update.versionName}-patched.apk")
            if (outApk.exists()) outApk.delete()

            onProgress(DownloadProgress(0, 0, update.deltaSize, Phase.APPLYING_PATCH, 0))
            val ok = applyXdelta(binary, sourceApk, deltaFile, outApk)
            if (handle.cancelled) { outApk.delete(); return null }
            onProgress(DownloadProgress(100, update.deltaSize, update.deltaSize, Phase.APPLYING_PATCH, 0))

            if (ok && outApk.exists() && outApk.length() > 1_000_000) {
                deltaFile.delete()
                outApk
            } else {
                outApk.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ tryDeltaUpdate: ${e.message}", e)
            null
        }
    }

    private fun ensureXdeltaBinary(
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
                it.key.contains(abi) || (abi == "arm64-v8a" && it.key.contains("arm64"))
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

    private fun applyXdelta(binary: File, source: File, delta: File, output: File): Boolean {
        return try {
            val cmd = listOf(
                binary.absolutePath,
                "-d",
                "-s", source.absolutePath,
                delta.absolutePath,
                output.absolutePath
            )
            Log.d(TAG, "exec: ${cmd.joinToString(" ")}")
            val process = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
            val log = process.inputStream.bufferedReader().readText()
            val code = process.waitFor()
            if (code != 0) {
                Log.e(TAG, "❌ xdelta3 exit=$code log=$log")
                return false
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ applyXdelta: ${e.message}", e)
            false
        }
    }

    private fun downloadFile(
        url: String,
        destDir: File,
        fileName: String,
        authToken: String? = null,
        handle: DownloadHandle,
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
                file.outputStream().use { output ->
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
                }
            }
            return file
        } catch (e: DownloadCancelledException) {
            try { file.delete() } catch (_: Exception) {}
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "❌ download: ${e.message}")
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
    private fun hasSignatureConflict(context: Context, apkFile: File): Boolean = try {
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

    private fun launchInstaller(context: Context, apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                try {
                    context.startActivity(
                        Intent(
                            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    android.widget.Toast.makeText(
                        context,
                        "فعّل «السماح من هذا المصدر» ثم أعد المحاولة",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } catch (_: Exception) {
                }
                return
            }
        }
        // كشف تعارض التوقيع قبل فتح المثبّت: النسخة المثبتة موقّعة بمفتاح مختلف
        if (hasSignatureConflict(context, apkFile)) {
            android.widget.Toast.makeText(
                context,
                "النسخة المثبتة موقّعة بمفتاح مختلف — احذفها ثم ثبّت من جديد",
                android.widget.Toast.LENGTH_LONG
            ).show()
            try {
                context.startActivity(
                    Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
            return
        }
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apkFile
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun getCurrentVersionCode(context: Context): Int = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        PackageInfoCompat.getLongVersionCode(info).toInt()
    } catch (_: PackageManager.NameNotFoundException) {
        0
    }

    private fun getCurrentVersionName(context: Context): String = try {
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
        val parts = tag.removePrefix("v").trim().split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: return 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return major * 10000 + minor * 100 + patch
    }

    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> "${"%.1f".format(bytes / 1024.0 / 1024.0)}MB"
    }

    fun formatSpeed(bytesPerSec: Long): String = "${formatSize(bytesPerSec)}/ث"
}
