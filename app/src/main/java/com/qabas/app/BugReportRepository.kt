package com.qabas.app

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * المستودع المركزي الموثوق لإدارة وتخزين ومزامنة بلاغات الأعطال واقتراحات التحسين.
 * يضمن حفظ كل بلاغ محلياً أولاً (Offline-First) لمنع فقدان أي طلب، ثم يزامنه سحابياً مع Supabase.
 */
object BugReportRepository {
    private const val TAG = "BugReportRepository"
    private const val STORE_DIR = "bug_reports_store"
    private const val STORE_FILE = "reports.json"
    private const val IMAGES_DIR = "images"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Serializable
    data class BugReportItem(
        val id: String = UUID.randomUUID().toString(),
        @SerialName("screen_name") val screenName: String = "الرئيسية",
        val description: String = "",
        @SerialName("report_type") val reportType: String = "BUG", // BUG, IMPROVEMENT, CONTENT
        val status: String = "open", // open, in_progress, resolved
        @SerialName("user_email") val userEmail: String = "anonymous",
        @SerialName("local_image_path") val localImagePath: String? = null,
        @SerialName("image_url") val imageUrl: String? = null,
        @SerialName("app_version") val appVersion: String = "",
        @SerialName("device_model") val deviceModel: String = "",
        @SerialName("created_at_millis") val createdAtMillis: Long = System.currentTimeMillis(),
        @SerialName("developer_reply") val developerReply: String? = null,
        @SerialName("synced_to_cloud") val syncedToCloud: Boolean = false
    ) {
        val formattedDate: String
            get() {
                val sdf = SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale("ar"))
                return runCatching { sdf.format(Date(createdAtMillis)) }.getOrDefault("")
            }

        val typeLabel: String
            get() = when (reportType.uppercase()) {
                "BUG" -> "خلل برمجي 🐞"
                "UI_OVERFLOW" -> "تداخل عناصر واجهة 📐"
                "IMPROVEMENT" -> "اقتراح تحسين 💡"
                "CONTENT" -> "ملاحظة محتوى 📜"
                else -> "بلاغ عام 📝"
            }

        val statusLabel: String
            get() = when (status.lowercase()) {
                "open" -> "قيد المراجعة"
                "in_progress" -> "جاري الإصلاح"
                "resolved" -> "تم الحل"
                else -> "جديد"
            }
    }

    data class SubmissionResult(
        val isSuccess: Boolean,
        val isSynced: Boolean,
        val message: String,
        val report: BugReportItem
    )

    private val _reports = MutableStateFlow<List<BugReportItem>>(emptyList())
    val reports: StateFlow<List<BugReportItem>> = _reports.asStateFlow()

    private var isInitialized = false

    /**
     * تهيئة المستودع وتحميل البلاغات المحفوظة محلياً بشكل فوري.
     */
    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        scope.launch {
            loadLocalReports(context)
            // مزامنة خلفية هادئة إذا كان الإنترنت متاحاً
            syncPendingReports(context)
        }
    }

    suspend fun ensureLoaded(context: Context) = withContext(Dispatchers.IO) {
        if (_reports.value.isEmpty()) {
            loadLocalReports(context)
        }
    }

    private fun getStorageFile(context: Context): File {
        val dir = File(context.filesDir, STORE_DIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, STORE_FILE)
    }

    private fun getImagesDir(context: Context): File {
        val dir = File(File(context.filesDir, STORE_DIR), IMAGES_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun loadLocalReports(context: Context): List<BugReportItem> = withContext(Dispatchers.IO) {
        try {
            val file = getStorageFile(context)
            if (file.exists()) {
                val content = file.readText()
                if (content.isNotBlank()) {
                    val list = json.decodeFromString<List<BugReportItem>>(content)
                    val sorted = list.sortedByDescending { it.createdAtMillis }
                    _reports.value = sorted
                    return@withContext sorted
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading local reports: ${e.message}")
        }
        val empty = emptyList<BugReportItem>()
        if (_reports.value.isEmpty()) {
            _reports.value = empty
        }
        empty
    }

    suspend fun saveLocalReports(context: Context, list: List<BugReportItem>) = withContext(Dispatchers.IO) {
        try {
            val file = getStorageFile(context)
            val sorted = list.sortedByDescending { it.createdAtMillis }
            file.writeText(json.encodeToString(sorted))
            _reports.value = sorted
        } catch (e: Exception) {
            Log.e(TAG, "Error saving local reports: ${e.message}")
        }
    }

    /**
     * حفظ لقطة الشاشة بشكل دائم في مجلد التطبيق الداخلي.
     */
    suspend fun saveScreenshotPermanently(context: Context, bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        try {
            val imagesDir = getImagesDir(context)
            val imageFile = File(imagesDir, "snap_${UUID.randomUUID()}.jpg")
            val fos = FileOutputStream(imageFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos)
            fos.flush()
            fos.close()
            imageFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving permanent screenshot: ${e.message}")
            null
        }
    }

    /**
     * إرسال وحفظ بلاغ جديد (حفظ محلي فوري مضمون 100% + محاولة مزامنة سحابية).
     */
    suspend fun submitReport(
        context: Context,
        screenName: String,
        description: String,
        reportType: String,
        screenshotBitmap: Bitmap? = null,
        screenshotFile: File? = null
    ): SubmissionResult = withContext(Dispatchers.IO) {
        // التأكد أولاً من تحميل البلاغات المحفوظة مسبقاً لمنع أي استبدال للبيانات
        ensureLoaded(context)

        val id = UUID.randomUUID().toString()
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        val androidVer = Build.VERSION.RELEASE
        val fullDeviceModel = "$manufacturer $model (Android $androidVer)"
        val currentVer = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val userEmail = SupabaseServices.Auth.currentUser.value?.email?.takeIf { it.isNotBlank() && it.contains("@") }
            ?: prefs.getString("user_email", null)?.takeIf { it.isNotBlank() && it.contains("@") }
            ?: "user@qabas.app"
        val userName = prefs.getString("user_name", "")?.takeIf { it.isNotBlank() } ?: userEmail.substringBefore('@')
        val accountNumber = UserIdentifierManager.getAccountNumber(context, userEmail).ifBlank {
            UserIdentifierManager.getOrCreateAccountNumber(context, userEmail, userName)
        }
        val enrichedDescription = if (accountNumber.isNotBlank() && !description.contains(accountNumber)) {
            "• معرّف الحساب: $accountNumber | المستخدم: $userName\n" + description.trim()
        } else {
            description.trim()
        }

        // 1. حفظ الصورة محلياً بشكل دائم
        var localImagePath: String? = null
        if (screenshotBitmap != null) {
            localImagePath = saveScreenshotPermanently(context, screenshotBitmap)
        } else if (screenshotFile != null && screenshotFile.exists()) {
            val destFile = File(getImagesDir(context), "snap_${id}.jpg")
            screenshotFile.copyTo(destFile, overwrite = true)
            localImagePath = destFile.absolutePath
        }

        // 2. إنشاء الكائن الأولي
        var reportItem = BugReportItem(
            id = id,
            screenName = screenName.ifBlank { "الرئيسية" },
            description = enrichedDescription,
            reportType = reportType.ifBlank { "BUG" },
            status = "open",
            userEmail = userEmail,
            localImagePath = localImagePath,
            imageUrl = null,
            appVersion = currentVer,
            deviceModel = fullDeviceModel,
            createdAtMillis = System.currentTimeMillis(),
            developerReply = null,
            syncedToCloud = false
        )

        // 3. الحفظ المحلي فوراً (ضمان عدم فقدان البلاغ إطلاقاً)
        val currentList = _reports.value.toMutableList()
        currentList.removeAll { it.id == id }
        currentList.add(0, reportItem)
        saveLocalReports(context, currentList)

        // 4. محاولة المزامنة السحابية مع Supabase
        var isSynced = false
        var uploadedImageUrl: String? = null

        if (SupabaseServices.isSupabaseAvailable) {
            try {
                // أ. رفع الصورة إلى Storage إن وُجدت
                if (localImagePath != null) {
                    val imgFile = File(localImagePath)
                    if (imgFile.exists()) {
                        val remoteFileName = "bug_${id}.jpg"
                        uploadedImageUrl = SupabaseServices.Storage.uploadFile(
                            bucketName = "bug_reports",
                            path = remoteFileName,
                            data = imgFile.readBytes()
                        )
                    }
                }

                // ب. تسجيل في قاعدة بيانات Supabase
                val dbSuccess = SupabaseServices.Database.recordBugReport(
                    userEmail = userEmail,
                    screenName = reportItem.screenName,
                    description = reportItem.description,
                    imageUrl = uploadedImageUrl ?: "",
                    reportType = reportItem.reportType,
                    appVersion = currentVer,
                    deviceModel = fullDeviceModel,
                    id = id,
                    accountNumber = accountNumber
                )

                if (dbSuccess) {
                    isSynced = true
                    reportItem = reportItem.copy(
                        imageUrl = uploadedImageUrl,
                        syncedToCloud = true
                    )
                    // تحديث القائمة المحلية بالحالة المزامنة
                    val updatedList = _reports.value.map { if (it.id == id) reportItem else it }
                    saveLocalReports(context, updatedList)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cloud sync failed during submit (saved locally): ${e.message}")
            }
        }

        val message = if (isSynced) {
            "تم إرسال بلاغك بنجاح إلى المطور وسحابة قبس! شكراً لمساعدتك 🚀"
        } else {
            "تم تسجيل بلاغك بنجاح وحفظه على جهازك! سيتم رفعه تلقائياً فور توفر الاتصال 🚀"
        }

        SubmissionResult(
            isSuccess = true,
            isSynced = isSynced,
            message = message,
            report = reportItem
        )
    }

    /**
     * تحديث حالة البلاغ (للإدارة والمطور) مع إمكانية إضافة رد نصي للمستخدم.
     */
    suspend fun updateReportStatus(
        context: Context,
        reportId: String,
        newStatus: String,
        developerReply: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val currentList = _reports.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == reportId }
        if (index != -1) {
            val updated = currentList[index].copy(
                status = newStatus,
                developerReply = developerReply ?: currentList[index].developerReply
            )
            currentList[index] = updated
            saveLocalReports(context, currentList)

            // محاولة التحديث في سحابة Supabase
            if (SupabaseServices.isSupabaseAvailable) {
                SupabaseServices.Database.updateBugReportStatus(
                    id = reportId,
                    status = newStatus,
                    developerReply = developerReply
                )
            }
            true
        } else {
            false
        }
    }

    /**
     * حذف بلاغ من السجل المحلي والسحابي.
     */
    suspend fun deleteReport(context: Context, reportId: String): Boolean = withContext(Dispatchers.IO) {
        val currentList = _reports.value.toMutableList()
        val toDelete = currentList.find { it.id == reportId }
        if (toDelete != null) {
            // حذف الصورة المحلية
            toDelete.localImagePath?.let { path ->
                runCatching { File(path).delete() }
            }
            currentList.removeAll { it.id == reportId }
            saveLocalReports(context, currentList)

            // حذف من السحابة إذا كان متاحاً
            if (SupabaseServices.isSupabaseAvailable) {
                SupabaseServices.Database.deleteBugReport(reportId)
            }
            true
        } else {
            false
        }
    }

    /**
     * مزامنة أي بلاغات محفوظة محلياً لم تُرفع بعد إلى السحابة.
     */
    suspend fun syncPendingReports(context: Context) = withContext(Dispatchers.IO) {
        if (!SupabaseServices.isSupabaseAvailable) return@withContext
        try {
            val pending = _reports.value.filter { !it.syncedToCloud }
            if (pending.isEmpty()) return@withContext

            var updatedAny = false
            val currentList = _reports.value.toMutableList()

            for (report in pending) {
                var cloudImageUrl = report.imageUrl
                if (cloudImageUrl.isNullOrBlank() && report.localImagePath != null) {
                    val file = File(report.localImagePath)
                    if (file.exists()) {
                        cloudImageUrl = SupabaseServices.Storage.uploadFile(
                            bucketName = "bug_reports",
                            path = "bug_${report.id}.jpg",
                            data = file.readBytes()
                        )
                    }
                }

                val dbSuccess = SupabaseServices.Database.recordBugReport(
                    userEmail = report.userEmail,
                    screenName = report.screenName,
                    description = report.description,
                    imageUrl = cloudImageUrl ?: "",
                    reportType = report.reportType,
                    appVersion = report.appVersion,
                    deviceModel = report.deviceModel,
                    developerReply = report.developerReply,
                    id = report.id
                )

                if (dbSuccess) {
                    val idx = currentList.indexOfFirst { it.id == report.id }
                    if (idx != -1) {
                        currentList[idx] = report.copy(
                            imageUrl = cloudImageUrl,
                            syncedToCloud = true
                        )
                        updatedAny = true
                    }
                }
            }

            if (updatedAny) {
                saveLocalReports(context, currentList)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error in background syncPendingReports: ${e.message}")
        }
    }

    /**
     * جلب جميع البلاغات المسجلة في هذا الجهاز.
     */
    fun getLocalReports(): List<BugReportItem> = _reports.value
}
