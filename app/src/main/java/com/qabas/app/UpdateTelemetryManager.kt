package com.qabas.app

import android.content.Context
import android.os.Build
import android.util.Log
import com.arthenica.smartexception.java.Exceptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * UpdateTelemetryManager — وحدة الرصد الذكي لأخطاء التحديث والدلتا.
 * تحفظ التقارير محلياً بانتظام وترسلها عبر Supabase الحصري عند توفر جلسة مصادق عليها.
 */
object UpdateTelemetryManager {

    private const val TAG = "UpdateTelemetry"
    private const val PREFS_FAILURE_COUNTS = "qabas_update_failure_counts"
    private const val LOCAL_REPORTS_FILE = "qabas_update_failures.json"
    private const val REPORT_TYPE_DELTA = "update_delta_failure"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val fileLock = Any()

    init {
        try {
            Exceptions.registerRootPackage("com.qabas.app")
            Exceptions.registerGroupPackage("com.arthenica")
            Exceptions.registerGroupPackage("io.github.jan.supabase")
            Exceptions.registerGroupPackage("okhttp3")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to register root packages for smart-exception: ${e.message}")
        }
    }

    data class UpdateErrorReport(
        val failureId: String,
        val deviceId: String,
        val userId: String?,
        val currentVersionCode: Int,
        val currentVersionName: String,
        val targetVersionCode: Int,
        val targetVersionName: String,
        val phase: String,
        val errorMessage: String,
        val cleanStackTrace: String,
        val exceptionClass: String?,
        val deviceAbi: String,
        val failureCount: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class DeltaErrorSummary(
        val signature: String,
        val phase: String,
        val count: Int,
        val deviceCount: Int,
        val lastSeen: Date,
        val sampleErrorMessage: String,
        val sampleStackTrace: String,
        val affectedTargetVersions: Set<String>
    )

    private val _latestFailureReport = MutableStateFlow<UpdateErrorReport?>(null)
    val latestFailureReport: StateFlow<UpdateErrorReport?> = _latestFailureReport.asStateFlow()

    private val _showErrorDialog = MutableStateFlow(false)
    val showErrorDialog: StateFlow<Boolean> = _showErrorDialog.asStateFlow()

    fun dismissErrorDialog() {
        _showErrorDialog.value = false
    }

    /**
     * تسجيل خطأ تحديث وتحليله بواسطة smart-exception ثم حفظه محلياً ومزامنته مع Supabase.
     */
    fun recordFailure(
        context: Context,
        update: UpdateManager.UpdateInfo?,
        phase: String,
        throwable: Throwable?,
        customMessage: String? = null,
        showDialog: Boolean = false
    ): UpdateErrorReport {
        val deviceId = UserPreferencesManager.getDeviceId(context)
        val currentCode = UpdateManager.getCurrentVersionCode(context)
        val currentName = UpdateManager.getCurrentVersionName(context)
        val targetCode = update?.versionCode ?: (currentCode + 1)
        val targetName = update?.versionName ?: "unknown"

        val rootMessage = when {
            throwable != null -> {
                val allMsgs = try {
                    Exceptions.getAllMessages(throwable).takeIf { it.isNotBlank() }
                } catch (_: Throwable) { null }
                allMsgs ?: throwable.localizedMessage ?: throwable.message ?: throwable.javaClass.simpleName
            }
            !customMessage.isNullOrBlank() -> customMessage
            else -> "فشل غير محدد أثناء مرحلة $phase"
        }

        val cleanTrace = when {
            throwable != null -> {
                try {
                    Exceptions.getStackTraceString(throwable).take(9500)
                } catch (_: Throwable) {
                    val sw = StringWriter()
                    throwable.printStackTrace(PrintWriter(sw))
                    sw.toString().take(9500)
                }
            }
            !customMessage.isNullOrBlank() -> customMessage.take(5000)
            else -> "No stack trace available for phase: $phase"
        }

        val prefs = context.getSharedPreferences(PREFS_FAILURE_COUNTS, Context.MODE_PRIVATE)
        val failureKey = "fail_${targetCode}_${phase.take(20)}"
        val newCount = prefs.getInt(failureKey, 0) + 1
        prefs.edit().putInt(failureKey, newCount).apply()

        val sanitizedDeviceId = deviceId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(12)
        val safeFailureId = "fail_${System.currentTimeMillis()}_${sanitizedDeviceId}"

        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: (Build.CPU_ABI ?: "unknown")
        val userId = SupabaseServices.Auth.getCurrentUserId()

        val report = UpdateErrorReport(
            failureId = safeFailureId,
            deviceId = deviceId,
            userId = userId,
            currentVersionCode = currentCode,
            currentVersionName = currentName,
            targetVersionCode = targetCode,
            targetVersionName = targetName,
            phase = phase,
            errorMessage = rootMessage.take(4500),
            cleanStackTrace = cleanTrace,
            exceptionClass = throwable?.javaClass?.name?.take(180),
            deviceAbi = abi.take(40),
            failureCount = newCount
        )

        _latestFailureReport.value = report
        if (showDialog) {
            _showErrorDialog.value = true
        }

        scope.launch {
            persistLocally(context, report)
            uploadReportToSupabase(report)
        }

        Log.e(TAG, "Update Failure Captured [$phase]: ${report.errorMessage}")
        return report
    }

    private fun persistLocally(context: Context, report: UpdateErrorReport) {
        synchronized(fileLock) {
            try {
                val list = readLocalReportsLocked(context).toMutableList()
                list.add(0, report)
                val trimmed = list.take(100)
                val arr = JSONArray()
                trimmed.forEach { item ->
                    arr.put(JSONObject().apply {
                        put("failureId", item.failureId)
                        put("deviceId", item.deviceId)
                        put("userId", item.userId ?: "")
                        put("currentVersionCode", item.currentVersionCode)
                        put("currentVersionName", item.currentVersionName)
                        put("targetVersionCode", item.targetVersionCode)
                        put("targetVersionName", item.targetVersionName)
                        put("phase", item.phase)
                        put("errorMessage", item.errorMessage)
                        put("cleanStackTrace", item.cleanStackTrace)
                        put("exceptionClass", item.exceptionClass ?: "")
                        put("deviceAbi", item.deviceAbi)
                        put("failureCount", item.failureCount)
                        put("timestamp", item.timestamp)
                    })
                }
                File(context.filesDir, LOCAL_REPORTS_FILE).writeText(arr.toString())
            } catch (e: Exception) {
                Log.w(TAG, "Failed persisting local update failure report: ${e.message}")
            }
        }
    }

    private fun readLocalReportsLocked(context: Context): List<UpdateErrorReport> {
        return try {
            val file = File(context.filesDir, LOCAL_REPORTS_FILE)
            if (!file.exists()) return emptyList()
            val arr = JSONArray(file.readText())
            val out = mutableListOf<UpdateErrorReport>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                out.add(
                    UpdateErrorReport(
                        failureId = obj.optString("failureId", "local_$i"),
                        deviceId = obj.optString("deviceId", "unknown"),
                        userId = obj.optString("userId", "").takeIf { it.isNotBlank() },
                        currentVersionCode = obj.optInt("currentVersionCode", 0),
                        currentVersionName = obj.optString("currentVersionName", ""),
                        targetVersionCode = obj.optInt("targetVersionCode", 0),
                        targetVersionName = obj.optString("targetVersionName", ""),
                        phase = obj.optString("phase", "UNKNOWN"),
                        errorMessage = obj.optString("errorMessage", "No error message"),
                        cleanStackTrace = obj.optString("cleanStackTrace", ""),
                        exceptionClass = obj.optString("exceptionClass", "").takeIf { it.isNotBlank() },
                        deviceAbi = obj.optString("deviceAbi", "unknown"),
                        failureCount = obj.optInt("failureCount", 1),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            out
        } catch (e: Exception) {
            Log.w(TAG, "Error reading local update failure logs: ${e.message}")
            emptyList()
        }
    }

    /**
     * مزامنة تقرير فشل التحديث عبر Supabase عند توفر مستخدم مصادق عليه.
     */
    suspend fun uploadReportToSupabase(report: UpdateErrorReport): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = SupabaseServices.Auth.getCurrentUserId() ?: return@withContext false
            val email = SupabaseServices.Auth.getCurrentUserEmail().orEmpty().ifBlank { "telemetry@qabas.app" }
            SupabaseServices.Database.recordBugReport(
                userEmail = email,
                screenName = "OTA:${report.phase}",
                description = "${report.errorMessage}\n\nABI=${report.deviceAbi}, Target=v${report.targetVersionName}(${report.targetVersionCode})\n${report.cleanStackTrace.take(3500)}",
                imageUrl = "",
                reportType = REPORT_TYPE_DELTA,
                appVersion = "${report.currentVersionName} (${report.currentVersionCode})",
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                userId = userId
            )
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Log.w(TAG, "Failed to sync update failure to Supabase: ${e.message}")
            false
        }
    }

    /**
     * جلب آخر تقارير الفشل من السجل المحلي و Supabase.
     */
    suspend fun fetchRecentFailures(context: Context, limitCount: Long = 30): Result<List<UpdateErrorReport>> = withContext(Dispatchers.IO) {
        runCatching {
            val localList = synchronized(fileLock) { readLocalReportsLocked(context) }
            val remoteList = if (SupabaseServices.isSupabaseAvailable && SupabaseServices.Auth.getCurrentUserId() != null) {
                runCatching {
                    SupabaseServices.Database.getAllBugReports()
                        .filter { it.reportType == REPORT_TYPE_DELTA }
                        .take(limitCount.toInt())
                        .map { row ->
                            UpdateErrorReport(
                                failureId = row.id,
                                deviceId = row.userId ?: row.userEmail,
                                userId = row.userId,
                                currentVersionCode = 0,
                                currentVersionName = row.appVersion,
                                targetVersionCode = 0,
                                targetVersionName = "",
                                phase = row.screenName.removePrefix("OTA:").ifBlank { "UPDATE" },
                                errorMessage = row.description.lines().firstOrNull() ?: row.description,
                                cleanStackTrace = row.description,
                                exceptionClass = null,
                                deviceAbi = row.deviceInfo,
                                failureCount = 1,
                                timestamp = System.currentTimeMillis()
                            )
                        }
                }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(emptyList())
            } else {
                emptyList()
            }

            (localList + remoteList)
                .distinctBy { it.failureId }
                .sortedByDescending { it.timestamp }
                .take(limitCount.toInt())
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
    }

    /**
     * جلب ملخص وإحصاءات الأخطاء الشائعة أثناء تطبيق الدلتا مجمعة حسب النوع والطور.
     */
    suspend fun fetchCommonDeltaFailures(context: Context): Result<List<DeltaErrorSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            val list = fetchRecentFailures(context, limitCount = 60).getOrThrow()

            val grouped = list.groupBy { item ->
                val primaryLine = item.errorMessage.lines().firstOrNull()?.take(80) ?: "Unknown Error"
                "${item.phase}::${primaryLine}"
            }

            grouped.map { (signature, items) ->
                val first = items.first()
                val devices = items.map { it.deviceId }.toSet()
                val versions = items.map { "v${it.targetVersionName} (${it.targetVersionCode})" }.toSet()
                val totalOccurrences = items.sumOf { it.failureCount.coerceAtLeast(1) }
                val maxDate = items.maxOfOrNull { it.timestamp }?.let { Date(it) } ?: Date()

                DeltaErrorSummary(
                    signature = signature,
                    phase = first.phase,
                    count = totalOccurrences,
                    deviceCount = devices.size,
                    lastSeen = maxDate,
                    sampleErrorMessage = first.errorMessage,
                    sampleStackTrace = first.cleanStackTrace,
                    affectedTargetVersions = versions
                )
            }.sortedByDescending { it.count }
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
    }

    fun formatMarkdownReport(report: UpdateErrorReport): String {
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(report.timestamp))
        return """
            ### تقرير تشخيص عطل التحديث (Qabas OTA Diagnostic)
            * **معرّف الخطأ:** `${report.failureId}`
            * **الوقت:** $dateFmt
            * **معرّف الجهاز:** `${report.deviceId}`
            * **معمارية المعالج:** `${report.deviceAbi}`
            * **الطور:** `${report.phase}`
            * **الإصدار الحالي:** v${report.currentVersionName} (${report.currentVersionCode})
            * **الإصدار المستهدف:** v${report.targetVersionName} (${report.targetVersionCode})
            * **تكرار الفشل للجهاز:** ${report.failureCount}x
            * **فئة الاستثناء:** `${report.exceptionClass ?: "Unknown"}`

            #### نص الخطأ الأساسي:
            ```text
            ${report.errorMessage}
            ```

            #### المسار المنقّى:
            ```text
            ${report.cleanStackTrace}
            ```
        """.trimIndent()
    }
}
