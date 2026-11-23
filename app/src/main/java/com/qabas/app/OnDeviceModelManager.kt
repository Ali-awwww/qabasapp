package com.qabas.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * معلومات النموذج المحلي المخصص للتنزيل والتشغيل بدون إنترنت
 */
data class OnDeviceModelInfo(
    val id: String,
    val name: String,
    val tag: String,
    val sizeBytes: Long,
    val sizeFormatted: String,
    val downloadUrl: String,
    val fileName: String,
    val ramRequirement: String,
    val description: String,
    val isRecommended: Boolean,
    val badgeColorHex: Long = 0xFF00E5FF
)

/**
 * حالة تنزيل النموذج
 */
sealed class ModelDownloadState {
    object Idle : ModelDownloadState()
    data class Downloading(
        val modelId: String,
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedKbps: Long = 0
    ) : ModelDownloadState()
    data class Success(val modelId: String) : ModelDownloadState()
    data class Error(val modelId: String, val message: String) : ModelDownloadState()
    data class Interrupted(   // حالة جديدة: تم إيقاف التنزيل (قطعconnection) والحفظ للتجدد
        val modelId: String,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : ModelDownloadState()
}

/**
 * مدير النماذج المحلية والتنزيل الفعلي
 */
object OnDeviceModelManager {
    private const val TAG = "OnDeviceModelManager"
    private const val PREFS_NAME = "qabas_prefs"
    private const val KEY_RESUME_BYTES_PREFIX = "model_resume_"
    private const val KEY_RESUME_TOTAL_PREFIX = "model_total_"
    private const val KEY_RESUME_URL_PREFIX = "model_resume_url_"
    private const val KEY_RESUME_ETAG_PREFIX = "model_resume_etag_"
    private const val KEY_RESUME_LASTMOD_PREFIX = "model_resume_lastmod_"
    private const val KEY_SELECTED_MODEL = "selected_model"
    private const val KEY_OFFLINE_MODE_ACTIVE = "offline_mode_active"
    private const val MODEL_SUBDIR = "models/local"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .build()
    }

    private val _downloadState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    @Volatile
    private var isCancelled = false

    // قائمة النماذج البرمجية المتوازنة والمختارة بدقة
    val availableModels: List<OnDeviceModelInfo> = listOf(
        OnDeviceModelInfo(
            id = "qwen_2_5_coder_0_5b",
            name = "Qwen 2.5 Coder 0.5B",
            tag = "الأسرع والأخف برمجياً ⚡",
            sizeBytes = 398_458_880L,
            sizeFormatted = "390 MB",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-0.5b-instruct-q4_k_m.gguf",
            fileName = "qwen2.5-coder-0.5b-instruct-q4_k_m.gguf",
            ramRequirement = "1.2 GB RAM",
            description = "أخف وأسرع نموذج برمجي مخصص للأجهزة الذكية. ممتاز لإكمال دوال Kotlin، واجهات Jetpack Compose، وتوليد الشيفرات البرمجية فورياً.",
            isRecommended = true,
            badgeColorHex = 0xFF00E5FF
        ),
        OnDeviceModelInfo(
            id = "gemma_3_1b_it",
            name = "Google Gemma 3 1B",
            tag = "الأعلى ذكاءً ومنطقاً 🧠",
            sizeBytes = 806_058_496L,
            sizeFormatted = "769 MB",
            downloadUrl = "https://huggingface.co/bartowski/google_gemma-3-1b-it-GGUF/resolve/main/google_gemma-3-1b-it-Q4_K_M.gguf",
            fileName = "google_gemma-3-1b-it-Q4_K_M.gguf",
            ramRequirement = "2.0 GB RAM",
            description = "نموذج رسمي عالي الدقة من Google. يتميز بالفهم العميق للأوامر العربية، التحليل المنطقي للهياكل البرمجية، وصياغة الحلول المتكاملة.",
            isRecommended = false,
            badgeColorHex = 0xFFE5B842
        ),
        OnDeviceModelInfo(
            id = "smollm2_coder_1_7b",
            name = "SmolLM2 Coder 1.7B",
            tag = "شامل ومتقدم 💻",
            sizeBytes = 891_289_600L,
            sizeFormatted = "850 MB",
            downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF/resolve/main/smollm2-1.7b-instruct-q4_k_m.gguf",
            fileName = "smollm2-1.7b-instruct-q4_k_m.gguf",
            ramRequirement = "2.2 GB RAM",
            description = "نموذج قوي مدرب على مليارات الأسطر البرمجية. مخصص لفحص الأخطاء المعقدة، بناء وحدات معمارية متكاملة، واستنباط الحلول التقنية.",
            isRecommended = false,
            badgeColorHex = 0xFFA855F7
        ),
        OnDeviceModelInfo(
            id = "llama_3_2_1b_instruct",
            name = "Meta Llama 3.2 1B",
            tag = "فائق المرونة والسرعة 🦙",
            sizeBytes = 815_000_000L,
            sizeFormatted = "775 MB",
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            fileName = "llama-3.2-1b-instruct-q4_k_m.gguf",
            ramRequirement = "1.5 GB RAM",
            description = "أحدث نموذج خفيف من Meta مخصص للهواتف المحمولة. كفاءة استثنائية في فهم المحادثات وتوليد الاستجابات الدقيقة.",
            isRecommended = false,
            badgeColorHex = 0xFF3B82F6
        ),
        OnDeviceModelInfo(
            id = "deepseek_r1_distill_1_5b",
            name = "DeepSeek R1 Distill 1.5B",
            tag = "تفكير استدلالي عميق 🔬",
            sizeBytes = 1_180_000_000L,
            sizeFormatted = "1.12 GB",
            downloadUrl = "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
            fileName = "deepseek-r1-distill-qwen-1.5b-q4_k_m.gguf",
            ramRequirement = "2.5 GB RAM",
            description = "نموذج التفكير المنطقي الثوري بتقنية Reasoning Tokens. يبني سلاسل أفكار متسلسلة ويحل المشكلات البرمجية المعقدة خطوة بخطوة.",
            isRecommended = false,
            badgeColorHex = 0xFF10B981
        )
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * المسار المحلي لمجلد النماذج
     */
    fun getModelsDirectory(context: Context): File {
        val dir = File(context.filesDir, MODEL_SUBDIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * ملف النموذج في الذاكرة المحلية
     */
    fun getModelFile(context: Context, modelInfo: OnDeviceModelInfo): File {
        return File(getModelsDirectory(context), modelInfo.fileName)
    }

    /**
     * هل النموذج منزّل وجاهز في الهاتف؟
     */
    fun isModelInstalled(context: Context, modelInfo: OnDeviceModelInfo): Boolean {
        val file = getModelFile(context, modelInfo)
        // التحقق من وجود الملف وأن حجمه حقيقي يتجاوز الحد الأدنى (على الأقل 100 ميجابايت)
        return file.exists() && file.length() >= 100_000_000L
    }

    /**
     * الحجم الفعلي للملف على القرص
     */
    fun getInstalledFileSize(context: Context, modelInfo: OnDeviceModelInfo): Long {
        val file = getModelFile(context, modelInfo)
        return if (file.exists()) file.length() else 0L
    }

    /**
     * إجمالي حجم كافة النماذج المحلية المنزلة في الذاكرة
     */
    fun getTotalModelsSizeBytes(context: Context): Long {
        val dir = getModelsDirectory(context)
        if (!dir.exists() || !dir.isDirectory) return 0L
        return dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }

    /**
     * النموذج المختار حالياً
     */
    fun getSelectedModel(context: Context): OnDeviceModelInfo {
        val prefs = getPrefs(context)
        val selectedId = prefs.getString(KEY_SELECTED_MODEL, availableModels.first().id)
        return availableModels.find { it.id == selectedId } ?: availableModels.first()
    }

    /**
     * حفظ النموذج المختار
     */
    fun setSelectedModel(context: Context, modelId: String) {
        getPrefs(context).edit().putString(KEY_SELECTED_MODEL, modelId).apply()
    }

    /**
     * هل وضع الأوفلاين مفعل كوضع افتراضي للوكيل؟
     */
    fun isOfflineModeActive(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_OFFLINE_MODE_ACTIVE, false)
    }

    /**
     * تفعيل أو تعطيل وضع الأوفلاين
     */
    fun setOfflineModeActive(context: Context, active: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_OFFLINE_MODE_ACTIVE, active).apply()
    }

    /**
     * هل يوجد أي نموذج مثبت وجاهز للعمل بدون إنترنت؟
     */
    fun hasAnyInstalledModel(context: Context): Boolean {
        return availableModels.any { isModelInstalled(context, it) }
    }

    /**
     * الحصول على النموذج الجاهز الحالي (أو أول نموذج مثبت)
     */
    fun getActiveReadyModel(context: Context): OnDeviceModelInfo? {
        val selected = getSelectedModel(context)
        if (isModelInstalled(context, selected)) return selected
        return availableModels.firstOrNull { isModelInstalled(context, it) }
    }

    /**
     * متطلبات ذاكرة النموذج بالبايت، مستخلصة من الحقل التوضيحي (مثال "2.5 GB RAM").
     * تُستخدم للمقارنة مع ذاكرة الجهاز قبل البدء في الوضع المحلي.
     *
     * إصلاح (12.4): النسخة السابقة كانت تجمع الأرقام فقط
     * (`filter { it.isDigit() }`) فحوّلت "1.2 GB" إلى 12.0 GB و"2.5 GB"
     * إلى 25 GB — أي أن كل بوابة ذاكرة كانت تفشل دائماً. هنا يُحلل العدد
     * العشري كاملاً عبر تعبير نمطي.
     */
    fun ramRequirementBytes(modelInfo: OnDeviceModelInfo): Long {
        val text = modelInfo.ramRequirement.uppercase()
        val num = Regex("""(\d+(?:\.\d+)?)""").find(text)
            ?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        return when {
            text.contains("GB") -> (num * 1024L * 1024 * 1024).toLong()
            text.contains("MB") -> (num * 1024L * 1024).toLong()
            else -> num.toLong()
        }
    }

    /**
     * إلغاء التنزيل الجاري مع الإبقاء على الملف الجزئي (.tmp) وحالة الاستئناف،
     * ليستكمل التنزيل لاحقاً من حيث توقف بدل البدء من الصفر.
     * يُستدعى من dialog أو viewmodel بتمرير modelInfo.
     * حذف التقدم نهائياً يتم فقط عبر [deletePartialDownload] عند طلب المستخدم الصريح.
     */
    fun cancelDownload(context: Context, modelInfo: OnDeviceModelInfo) {
        isCancelled = true
        _downloadState.value = ModelDownloadState.Idle
    }

    /**
     * حذف الملف الجزئي وحالة الاستئناف نهائياً — إجراء صريح من المستخدم فقط
     * (زر «حذف التنزيل» لا زر الإلغاء). لا يُستدعى تلقائياً عند الإلغاء أو الخطأ.
     */
    fun deletePartialDownload(context: Context, modelInfo: OnDeviceModelInfo) {
        isCancelled = true
        val destFile = getModelFile(context, modelInfo)
        val tempFile = File(destFile.parentFile, "${destFile.name}.tmp")
        if (tempFile.exists()) tempFile.delete()
        clearResumeState(context, modelInfo)
        _downloadState.value = ModelDownloadState.Idle
    }

    /** حفظ حالة الاستئناف (الإزاحة + الحجم المتوقع + الرابط + بصمات السيرفر). */
    private fun saveResumeState(
        context: Context,
        modelInfo: OnDeviceModelInfo,
        downloadedBytes: Long,
        totalBytes: Long,
        etag: String? = null,
        lastModified: String? = null
    ) {
        getPrefs(context).edit()
            .putLong(KEY_RESUME_BYTES_PREFIX + modelInfo.id, downloadedBytes)
            .putLong(KEY_RESUME_TOTAL_PREFIX + modelInfo.id, totalBytes)
            .putString(KEY_RESUME_URL_PREFIX + modelInfo.id, modelInfo.downloadUrl)
            .putString(KEY_RESUME_ETAG_PREFIX + modelInfo.id, etag)
            .putString(KEY_RESUME_LASTMOD_PREFIX + modelInfo.id, lastModified)
            .apply()
    }

    private fun clearResumeState(context: Context, modelInfo: OnDeviceModelInfo) {
        getPrefs(context).edit()
            .remove(KEY_RESUME_BYTES_PREFIX + modelInfo.id)
            .remove(KEY_RESUME_TOTAL_PREFIX + modelInfo.id)
            .remove(KEY_RESUME_URL_PREFIX + modelInfo.id)
            .remove(KEY_RESUME_ETAG_PREFIX + modelInfo.id)
            .remove(KEY_RESUME_LASTMOD_PREFIX + modelInfo.id)
            .apply()
    }

    /**
     * تنزيل النموذج المختار مباشرة من Hugging Face مع تتبع البايتات الحقيقية
     */
    suspend fun downloadModel(
        context: Context,
        modelInfo: OnDeviceModelInfo
    ): Boolean = withContext(Dispatchers.IO) {
        isCancelled = false
        val destFile = getModelFile(context, modelInfo)
        val tempFile = File(destFile.parentFile, "${destFile.name}.tmp")

        // حالة الاستئناف المحفوظة: الإزاحة + الحجم المتوقع + الرابط + بصمات السيرفر،
        // فتبقى صالحة بعد موت العملية وإعادة تشغيل التطبيق.
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedResumeBytes = prefs.getLong(KEY_RESUME_BYTES_PREFIX + modelInfo.id, 0L)
        val savedTotalBytes = prefs.getLong(KEY_RESUME_TOTAL_PREFIX + modelInfo.id, modelInfo.sizeBytes)
        val savedUrl = prefs.getString(KEY_RESUME_URL_PREFIX + modelInfo.id, null)
        val savedEtag = prefs.getString(KEY_RESUME_ETAG_PREFIX + modelInfo.id, null)
        val savedLastModified = prefs.getString(KEY_RESUME_LASTMOD_PREFIX + modelInfo.id, null)

        // الإزاحة الحقيقية هي طول الملف الجزئي على القرص، لا الرقم المحفوظ وحده:
        // إن حُذف الملف الجزئي من خارج التطبيق فلا معنى للرقم المحفوظ.
        var resumeBytes = if (tempFile.exists()) tempFile.length() else 0L
        if (resumeBytes > 0 && savedResumeBytes > 0) resumeBytes = minOf(resumeBytes, savedResumeBytes)
        // إن تغيّر الرابط فالملف الجزئي يخص ملفاً آخر — لا يُستأنف.
        if (resumeBytes > 0 && savedUrl != null && savedUrl != modelInfo.downloadUrl) resumeBytes = 0L

        var expectedTotalBytes = savedTotalBytes.takeIf { it > 0 } ?: modelInfo.sizeBytes
        // إن اكتمل الملف الجزئي فعلاً (طوله يساوي الحجم المتوقع) ثبّته دون إعادة تنزيل.
        if (resumeBytes > 0 && expectedTotalBytes > 0 && resumeBytes >= expectedTotalBytes) {
            if (tempFile.length() == expectedTotalBytes) {
                if (destFile.exists()) destFile.delete()
                if (tempFile.renameTo(destFile)) {
                    setSelectedModel(context, modelInfo.id)
                    clearResumeState(context, modelInfo)
                    _downloadState.value = ModelDownloadState.Success(modelInfo.id)
                    return@withContext true
                }
            }
            // ملف جزئي تالف (أطول من المتوقع أو تعذّر تثبيته) — إعادة كاملة من الصفر.
            resumeBytes = 0L
        }

        var downloadedBytes = resumeBytes

        val availableBytes = android.os.StatFs(tempFile.parentFile?.absolutePath ?: context.filesDir.absolutePath).availableBytes
        val estimatedRemainingBytes = (expectedTotalBytes - resumeBytes).coerceAtLeast(0L)
        val requiredBytes = estimatedRemainingBytes + 32L * 1024L * 1024L
        if (availableBytes < requiredBytes) {
            val message = "المساحة الحرة غير كافية: يلزم نحو ${StorageMetrics.formatBytes(requiredBytes)} مع هامش أمان، والمتاح ${StorageMetrics.formatBytes(availableBytes)}."
            _downloadState.value = ModelDownloadState.Error(modelInfo.id, message)
            return@withContext false
        }

        try {
            _downloadState.value = ModelDownloadState.Downloading(
                modelId = modelInfo.id,
                progress = if (expectedTotalBytes > 0) resumeBytes.toFloat() / expectedTotalBytes else 0f,
                downloadedBytes = resumeBytes,
                totalBytes = expectedTotalBytes
            )

            // استئناف حقيقي عبر HTTP Range: يُطلب من السيرفر البايتات المتبقية فقط.
            // لا يُستخدم skip() على مجرى الشبكة إطلاقاً — فهو غير مضمون العدد،
            // بل يبدأ السيرفر نفسه من الإزاحة المطلوبة.
            val requestBuilder = Request.Builder().url(modelInfo.downloadUrl)
            if (resumeBytes > 0) {
                requestBuilder.header("Range", "bytes=$resumeBytes-")
                Log.i(TAG, "Resuming download of ${modelInfo.id} from $resumeBytes/$expectedTotalBytes")
            }
            val request = requestBuilder.build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val error = "فشل بدء التنزيل: كود الاستجابة ${response.code}"
                Log.e(TAG, error)
                // حفظ progress Interrupted قبل الخطأ — مع الإبقاء على الملف الجزئي.
                saveResumeState(context, modelInfo, resumeBytes, expectedTotalBytes, savedEtag, savedLastModified)
                _downloadState.value = ModelDownloadState.Error(modelInfo.id, error)
                return@withContext false
            }

            val body = response.body ?: run {
                val error = "لم يتم استلام محتوى من السيرفر"
                // حفظ progress Interrupted قبل الخطأ — مع الإبقاء على الملف الجزئي.
                saveResumeState(context, modelInfo, resumeBytes, expectedTotalBytes, savedEtag, savedLastModified)
                _downloadState.value = ModelDownloadState.Error(modelInfo.id, error)
                return@withContext false
            }

            val responseEtag = response.header("ETag")
            val responseLastModified = response.header("Last-Modified")
            val contentRange = response.header("Content-Range")

            // لا يُقبل الاستئناف إلا بردّ 206 Partial Content مع Content-Range
            // يبدأ فعلاً من الإزاحة المطلوبة، وبصمات مطابقة إن كانت محفوظة.
            // أي رفض من السيرفر (ردّ 200 أو غياب Content-Range أو تغيّر الملف)
            // يعني إعادة تنزيل كاملة صادقة من الصفر بعد حذف الجزئي — لا تخطٍّ صامت.
            var appendMode = false
            var totalBytes = expectedTotalBytes
            if (resumeBytes > 0) {
                val rangeAccepted = response.code == 206 &&
                    contentRange != null &&
                    contentRange.startsWith("bytes $resumeBytes-") &&
                    (savedEtag == null || responseEtag == null || responseEtag == savedEtag) &&
                    (savedLastModified == null || responseLastModified == null || responseLastModified == savedLastModified)
                if (rangeAccepted) {
                    appendMode = true
                    totalBytes = contentRange.substringAfterLast('/').toLongOrNull()?.takeIf { it > 0 }
                        ?: (body.contentLength().takeIf { it > 0 }?.plus(resumeBytes) ?: expectedTotalBytes)
                } else {
                    Log.w(TAG, "Server refused Range resume for ${modelInfo.id} (code ${response.code}); restarting full download from 0")
                    tempFile.delete()
                    resumeBytes = 0L
                    downloadedBytes = 0L
                    totalBytes = body.contentLength().takeIf { it > 0 } ?: expectedTotalBytes
                }
            } else {
                totalBytes = body.contentLength().takeIf { it > 0 } ?: expectedTotalBytes
            }
            expectedTotalBytes = totalBytes
            val startTime = System.currentTimeMillis()
            var lastUpdateTime = startTime

            // الكتابة بعد الملف الجزئي فقط عند قبول الاستئناف؛ وإلا كتابة من الصفر.
            // (ر21) مجرى الشبكة في use: كان يُترك مفتوحاً عند الإلغاء/الخطأ/
            // الانقطاع فيُحبس اتصال OkHttp خارج المسبح — كل مواضع byteStream
            // الأخرى في التطبيق تغلقه (DownloadsHubManager وUpdateManager).
            body.byteStream().use { byteStream ->
            FileOutputStream(tempFile, appendMode).use { output ->
                val buffer = ByteArray(256 * 1024) // 256KB buffer: fewer syscalls, faster writes
                var bytesRead: Int

                while (byteStream.read(buffer).also { bytesRead = it } != -1) {
                    if (isCancelled) {
                        // الإلغاء يُبقي الملف الجزئي وحالة الاستئناف ليُستكمل لاحقاً.
                        saveResumeState(context, modelInfo, downloadedBytes, totalBytes, responseEtag, responseLastModified)
                        _downloadState.value = ModelDownloadState.Idle
                        return@withContext false
                    }

                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastUpdateTime >= 250) { // update 4 times per second
                        val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                        val elapsedSec = (now - startTime) / 1000.0
                        val speedKbps = if (elapsedSec > 0) ((downloadedBytes / 1024) / elapsedSec).toLong() else 0L

                        _downloadState.value = ModelDownloadState.Downloading(
                            modelId = modelInfo.id,
                            progress = progress.coerceIn(0f, 1f),
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes,
                            speedKbps = speedKbps
                        )
                        lastUpdateTime = now
                    }
                }
            }
            }

            // مجرى منقطع قبل اكتمال الحجم المعلن: لا يُثبَّت ملف ناقص على أنه
            // مكتمل — يُحفظ التقدم وتُبلَّغ حالة انقطاع قابلة للاستئناف.
            if (totalBytes > 0 && downloadedBytes < totalBytes) {
                saveResumeState(context, modelInfo, downloadedBytes, totalBytes, responseEtag, responseLastModified)
                _downloadState.value = ModelDownloadState.Interrupted(modelInfo.id, downloadedBytes, totalBytes)
                return@withContext false
            }

            // التنزيل اكتمل بنجاح، استبدال الملف المؤقت
            if (tempFile.exists() && tempFile.length() > 0) {
                if (destFile.exists()) destFile.delete()
                val renamed = tempFile.renameTo(destFile)
                if (renamed || destFile.exists()) {
                    setSelectedModel(context, modelInfo.id)
                    // تنظيف saved resume state
                    clearResumeState(context, modelInfo)
                    _downloadState.value = ModelDownloadState.Success(modelInfo.id)
                    Log.i(TAG, "Model downloaded successfully to ${destFile.absolutePath}")
                    return@withContext true
                }
            }

            _downloadState.value = ModelDownloadState.Error(modelInfo.id, "تعذر تثبيت ملف النموذج النهائي")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}", e)
            // الانقطاع يُبقي الملف الجزئي وحالة الاستئناف ليُستكمل لاحقاً.
            saveResumeState(context, modelInfo, downloadedBytes, expectedTotalBytes)
            _downloadState.value = ModelDownloadState.Interrupted(
                modelId = modelInfo.id,
                downloadedBytes = downloadedBytes,
                totalBytes = expectedTotalBytes
            )
            false
        }
    }

    /**
     * استكمال التنزيل من حيث توقف (يقرأ الحالة المحفوظة ويعيد تشغيل التنزيل)
     */
    suspend fun resumeDownload(context: Context, modelInfo: OnDeviceModelInfo): Boolean {
        // التحقق مما لو كان هناك حالة إنقطاع محفوظة
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedResume = prefs.getLong(KEY_RESUME_BYTES_PREFIX + modelInfo.id, 0L)
        val savedTotal = prefs.getLong(KEY_RESUME_TOTAL_PREFIX + modelInfo.id, modelInfo.sizeBytes)

        if (savedResume <= 0) return false

        // إعادة تعيين الحالة إلى Downloading وتمهيد التنزيل من النقطة المحفوظة
        _downloadState.value = ModelDownloadState.Downloading(
            modelId = modelInfo.id,
            progress = savedResume.toFloat() / savedTotal,
            downloadedBytes = savedResume,
            totalBytes = savedTotal
        )

        return downloadModel(context, modelInfo)
    }

    /**
     * حذف النموذج من الذاكرة المحلية لتحرير المساحة
     */
    fun deleteModel(context: Context, modelInfo: OnDeviceModelInfo): Boolean {
        val file = getModelFile(context, modelInfo)
        return if (file.exists()) {
            val deleted = file.delete()
            if (deleted) {
                // إذا كان هو المحدد، عيّن نموذجاً بديلاً
                if (getSelectedModel(context).id == modelInfo.id) {
                    val remaining = availableModels.firstOrNull { isModelInstalled(context, it) }
                    if (remaining != null) {
                        setSelectedModel(context, remaining.id)
                    }
                }
            }
            deleted
        } else false
    }

    sealed interface ModelFileResult {
        data class Ok(val message: String) : ModelFileResult
        data class Err(val message: String) : ModelFileResult
    }

    /**
     * تصدير النموذج إلى مجلد Downloads (يبقى بعد حذف التطبيق) — نسخ بتقدم.
     * التحقق عند الاستيراد: تطابق الحجم بالبايت (لا بصمات sha256 في البيانات).
     */
    suspend fun exportModel(
        context: Context,
        modelInfo: OnDeviceModelInfo,
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> }
    ): ModelFileResult = withContext(Dispatchers.IO) {
        try {
            val src = getModelFile(context, modelInfo)
            if (!src.exists() || src.length() < 100_000_000L) {
                return@withContext ModelFileResult.Err("النموذج غير مكتمل — نزّله أولاً")
            }
            val total = src.length()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, modelInfo.fileName)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/QabasModels")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: return@withContext ModelFileResult.Err("تعذر إنشاء ملف التصدير")
                resolver.openOutputStream(uri)?.use { out ->
                    src.inputStream().use { inp ->
                        val buf = ByteArray(256 * 1024)
                        var done = 0L
                        while (true) {
                            val n = inp.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            onProgress(done, total)
                        }
                    }
                } ?: run {
                    resolver.delete(uri, null, null)
                    return@withContext ModelFileResult.Err("تعذر الكتابة لملف التصدير")
                }
                onProgress(total, total)
                ModelFileResult.Ok("تم التصدير إلى Downloads/QabasModels ✅")
            } else {
                @Suppress("DEPRECATION")
                val dest = java.io.File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS
                    ), "QabasModels/${modelInfo.fileName}"
                )
                dest.parentFile?.mkdirs()
                src.inputStream().use { inp ->
                    dest.outputStream().use { out ->
                        val buf = ByteArray(256 * 1024)
                        var done = 0L
                        while (true) {
                            val n = inp.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            onProgress(done, total)
                        }
                    }
                }
                onProgress(total, total)
                ModelFileResult.Ok("تم التصدير إلى Downloads/QabasModels ✅")
            }
        } catch (e: Exception) {
            Log.e(TAG, "exportModel failed: ${e.message}", e)
            ModelFileResult.Err("فشل التصدير: ${e.message}")
        }
    }

    /**
     * استيراد نموذج من ملف يختاره المستخدم — يُقبل فقط عند تطابق الحجم
     * بالبايت مع الحجم المعلن (حماية من الملفات الناقصة/الخاطئة).
     */
    suspend fun importModel(
        context: Context,
        modelInfo: OnDeviceModelInfo,
        sourceUri: android.net.Uri,
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> }
    ): ModelFileResult = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val srcSize = resolver.openFileDescriptor(sourceUri, "r")?.use { it.statSize } ?: -1L
            if (srcSize != modelInfo.sizeBytes) {
                return@withContext ModelFileResult.Err(
                    "الحجم غير متطابق (الموجود: ${formatBytes(srcSize)}، المطلوب: ${modelInfo.sizeFormatted}) — تأكد أن الملف هو ${modelInfo.fileName}"
                )
            }
            val destFile = getModelFile(context, modelInfo)
            val tmpFile = java.io.File(destFile.parentFile, "${destFile.name}.import.tmp")
            if (tmpFile.exists()) tmpFile.delete()
            resolver.openInputStream(sourceUri)?.use { inp ->
                tmpFile.outputStream().use { out ->
                    val buf = ByteArray(256 * 1024)
                    var done = 0L
                    while (true) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        onProgress(done, srcSize)
                    }
                }
            } ?: return@withContext ModelFileResult.Err("تعذر قراءة الملف المختار")
            if (tmpFile.length() != modelInfo.sizeBytes) {
                tmpFile.delete()
                return@withContext ModelFileResult.Err("اكتمل النسخ بحجم ناقص — أعد المحاولة")
            }
            if (destFile.exists()) destFile.delete()
            if (!tmpFile.renameTo(destFile)) {
                tmpFile.copyTo(destFile, overwrite = true)
                tmpFile.delete()
            }
            onProgress(srcSize, srcSize)
            setSelectedModel(context, modelInfo.id)
            prefsCleanupAfterImport(context, modelInfo)
            ModelFileResult.Ok("تم استيراد ${modelInfo.name} وتفعيله ✅")
        } catch (e: Exception) {
            Log.e(TAG, "importModel failed: ${e.message}", e)
            ModelFileResult.Err("فشل الاستيراد: ${e.message}")
        }
    }

    private fun prefsCleanupAfterImport(context: Context, modelInfo: OnDeviceModelInfo) {
        getPrefs(context).edit()
            .remove("model_resume_${modelInfo.id}")
            .remove("model_total_${modelInfo.id}")
            .apply()
        _downloadState.value = ModelDownloadState.Success(modelInfo.id)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "غير معروف"
        if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
        return "${bytes / (1024 * 1024)} MB"
    }
}
