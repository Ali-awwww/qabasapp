package com.qabas.app

import android.content.Context
import android.os.Build
import android.util.Log
import com.arthenica.smartexception.java.Exceptions
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 📡 UpdateTelemetryManager — وحدة الرصد الذكي لأخطاء التحديث والدلتا.
 * يدمج مكتبة smart-exception لتنقية وتلخيص مسارات الأخطاء دون فشل صامت،
 * مع رفع القياسات عن بعد إلى Firestore لتتبع versionCode وتكرار الفشل للأجهزة.
 */
object UpdateTelemetryManager {

    private const val TAG = "UpdateTelemetry"
    private const val PREFS_FAILURE_COUNTS = "qabas_update_failure_counts"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        try {
            Exceptions.registerRootPackage("com.qabas.app")
            Exceptions.registerGroupPackage("com.arthenica")
            Exceptions.registerGroupPackage("io.github.jan.supabase")
            Exceptions.registerGroupPackage("com.google.firebase")
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
     * تسجيل خطأ تحديث وتحليله بواسطة smart-exception ثم رفعه سحابياً وعرضه في الواجهة.
     */
    fun recordFailure(
        context: Context,
        update: UpdateManager.UpdateInfo?,
        phase: String,
        throwable: Throwable?,
        customMessage: String? = null
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

        // حساب تكرار الفشل لنفس الجهاز ونفس الإصدار
        val prefs = context.getSharedPreferences(PREFS_FAILURE_COUNTS, Context.MODE_PRIVATE)
        val failureKey = "fail_${targetCode}_${phase.take(20)}"
        val newCount = prefs.getInt(failureKey, 0) + 1
        prefs.edit().putInt(failureKey, newCount).apply()

        val sanitizedDeviceId = deviceId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(12)
        val safeFailureId = "fail_${System.currentTimeMillis()}_${sanitizedDeviceId}"

        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: (Build.CPU_ABI ?: "unknown")
        val userId = SupabaseServices.Auth.getCurrentUserId() ?: CloudServices.Auth.getCurrentUserId()

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
        _showErrorDialog.value = true

        // الرفع في الخلفية إلى Firestore
        scope.launch {
            uploadReportToFirestore(context, report)
        }

        Log.e(TAG, "🚨 Update Failure Captured [$phase]: ${report.errorMessage}")
        return report
    }

    /**
     * رفع تقرير الفشل إلى Firestore في مجموعة /update_failures/{failureId}
     */
    suspend fun uploadReportToFirestore(context: Context, report: UpdateErrorReport): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val firestore = FirebaseManager.getFirestore(context)
            val docRef = firestore.collection("update_failures").document(report.failureId)

            val payload = hashMapOf<String, Any>(
                "failureId" to report.failureId,
                "deviceId" to report.deviceId,
                "currentVersionCode" to report.currentVersionCode,
                "currentVersionName" to report.currentVersionName,
                "targetVersionCode" to report.targetVersionCode,
                "targetVersionName" to report.targetVersionName,
                "phase" to report.phase,
                "errorMessage" to report.errorMessage,
                "cleanStackTrace" to report.cleanStackTrace,
                "failureCount" to report.failureCount,
                "deviceAbi" to report.deviceAbi,
                "createdAt" to Timestamp(Date(report.timestamp))
            )

            if (!report.userId.isNullOrBlank()) {
                payload["userId"] = report.userId
            }
            if (!report.exceptionClass.isNullOrBlank()) {
                payload["exceptionClass"] = report.exceptionClass
            }

            docRef.set(payload, SetOptions.merge()).await()
            Log.d(TAG, "📡 Uploaded update failure telemetry to Firestore: ${report.failureId}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to upload update failure report to Firestore: ${e.message}")
            false
        }
    }

    /**
     * جلب آخر تقارير الفشل من Firestore (للمطور فقط).
     */
    suspend fun fetchRecentFailures(context: Context, limitCount: Long = 30): Result<List<UpdateErrorReport>> = withContext(Dispatchers.IO) {
        runCatching {
            val firestore = FirebaseManager.getFirestore(context)
            val snapshot = firestore.collection("update_failures")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limitCount)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                try {
                    val createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
                    UpdateErrorReport(
                        failureId = doc.getString("failureId") ?: doc.id,
                        deviceId = doc.getString("deviceId") ?: "unknown",
                        userId = doc.getString("userId"),
                        currentVersionCode = doc.getLong("currentVersionCode")?.toInt() ?: 0,
                        currentVersionName = doc.getString("currentVersionName") ?: "",
                        targetVersionCode = doc.getLong("targetVersionCode")?.toInt() ?: 0,
                        targetVersionName = doc.getString("targetVersionName") ?: "",
                        phase = doc.getString("phase") ?: "UNKNOWN",
                        errorMessage = doc.getString("errorMessage") ?: "No error message",
                        cleanStackTrace = doc.getString("cleanStackTrace") ?: "",
                        exceptionClass = doc.getString("exceptionClass"),
                        deviceAbi = doc.getString("deviceAbi") ?: "unknown",
                        failureCount = doc.getLong("failureCount")?.toInt() ?: 1,
                        timestamp = createdAt
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing failure doc: ${doc.id}", e)
                    null
                }
            }
        }
    }

    /**
     * جلب ملخص وإحصاءات الأخطاء الشائعة أثناء تطبيق الدلتا مجمعة حسب النوع والطور (للوحة تحكم المطور).
     */
    suspend fun fetchCommonDeltaFailures(context: Context): Result<List<DeltaErrorSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            val recentListResult = fetchRecentFailures(context, limitCount = 60)
            val list = recentListResult.getOrThrow()

            // Group by phase and primary error line
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
        }
    }

    /**
     * تحويل التقرير إلى تنسيق Markdown تفصيلي وقابل للنسخ أو الإرسال.
     */
    fun formatMarkdownReport(report: UpdateErrorReport): String {
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(report.timestamp))
        return """
            ### 🛠️ تقرير تشخيص عطل التحديث (Qabas OTA Diagnostic)
            * **معرّف الخطأ:** `${report.failureId}`
            * **الوقت:** $dateFmt
            * **معرّف الجهاز (Device ID):** `${report.deviceId}`
            * **معمارية المعالج (ABI):** `${report.deviceAbi}`
            * **الطور (Phase):** `${report.phase}`
            * **الإصدار الحالي:** v${report.currentVersionName} (${report.currentVersionCode})
            * **الإصدار المستهدف:** v${report.targetVersionName} (${report.targetVersionCode})
            * **تكرار الفشل للجهاز:** ${report.failureCount}x
            * **فئة الاستثناء (Exception):** `${report.exceptionClass ?: "Unknown"}`

            #### ⚠️ نص الخطأ الأساسي:
            ```text
            ${report.errorMessage}
            ```

            #### 📋 المسار المنقّى (Smart Stack Trace):
            ```text
            ${report.cleanStackTrace}
            ```
        """.trimIndent()
    }
}
