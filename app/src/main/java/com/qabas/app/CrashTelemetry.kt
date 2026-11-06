package com.qabas.app

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Durable crash telemetry queue. Reports are persisted atomically before
 * WorkManager is scheduled. The PostgREST-only client always uses the anon role.
 */
internal object CrashTelemetry {
    private const val TAG = "CrashTelemetry"
    private const val QUEUE_DIR = "crash_telemetry_queue"
    private const val UNIQUE_WORK = "qabas_crash_telemetry_upload"
    private const val MAX_DESCRIPTION_CHARS = 32_000
    private const val MAX_BATCH_SIZE = 25

    private val credentialPattern = Regex(
        "(?i)(password|pass|secret|token|bearer|api_key|apikey|authorization|jwt|access_token)(\\s*[=:]\\s*)([^\\s,;]+)"
    )
    private val bearerPattern = Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+")
    private val emailPattern = Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b")

    @Volatile
    private var uploadClient: SupabaseClient? = null

    fun enqueue(
        context: Context,
        thread: String,
        throwable: Throwable,
        source: String,
        occurredAtMs: Long
    ) {
        val appContext = context.applicationContext
        val eventId = UUID.randomUUID().toString()
        val prefs = appContext.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val isPreviousProcessExit = source == "system_exit_scan"
        val stageKey = if (isPreviousProcessExit) {
            "crash_guard_previous_startup_stage"
        } else {
            "crash_guard_last_startup_stage"
        }
        val stageTimestampKey = if (isPreviousProcessExit) {
            "crash_guard_previous_startup_stage_ts"
        } else {
            "crash_guard_last_startup_stage_ts"
        }
        val startupStage = prefs.getString(stageKey, "unknown") ?: "unknown"
        val startupStageTimestamp = prefs.getLong(stageTimestampKey, 0L)
        // Include the bounded in-memory event history and runtime snapshot so a
        // cloud report explains what happened immediately before the failure.
        // Both sections are sanitized again at the upload boundary.
        val recentBreadcrumbs = runCatching { CrashBreadcrumbs.dump() }
            .getOrDefault("breadcrumb_snapshot_unavailable")
        val incidentContext = runCatching { CrashBreadcrumbs.dumpContextSnapshot(appContext) }
            .getOrDefault("incident_context_unavailable")
        val occurredAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(occurredAtMs))

        val description = buildString {
            appendLine("QABAS crash telemetry")
            appendLine("event_id=$eventId")
            appendLine("occurred_at=$occurredAt")
            appendLine("source=${sanitize(source, 120)}")
            appendLine("report_category=CRASH_TELEMETRY")
            appendLine("thread=${sanitize(thread, 120)}")
            appendLine("startup_stage=${sanitize(startupStage, 120)}")
            appendLine("startup_stage_recorded_at_ms=$startupStageTimestamp")
            appendLine("exception_type=${throwable.javaClass.name.take(240)}")
            appendLine("message=${sanitize(throwable.message.orEmpty(), 1200)}")
            appendLine("current_screen=${sanitize(CrashBreadcrumbs.currentScreen, 120)}")
            appendLine("previous_screen=${sanitize(CrashBreadcrumbs.previousScreen, 120)}")
            appendLine("last_action=${sanitize(CrashBreadcrumbs.lastAction, 240)}")
            appendLine("last_action_age_ms=${(System.currentTimeMillis() - CrashBreadcrumbs.lastActionTimeMs).coerceAtLeast(0L)}")
            appendLine()
            appendLine("recent_breadcrumbs:")
            appendLine(sanitize(recentBreadcrumbs, 8_000))
            appendLine()
            appendLine("incident_context:")
            appendLine(sanitize(incidentContext, 2_500))
            appendLine()
            appendLine("stack_trace:")
            throwable.stackTrace.take(100).forEach { appendLine("  at $it") }

            var cause = throwable.cause
            var causeDepth = 0
            while (cause != null && cause !== throwable && causeDepth < 8) {
                appendLine("caused_by[${causeDepth + 1}]=${cause.javaClass.name}: ${sanitize(cause.message.orEmpty(), 500)}")
                cause.stackTrace.take(20).forEach { appendLine("  at $it") }
                val next = cause.cause
                if (next === cause) break
                cause = next
                causeDepth++
            }

            if (throwable is QabasCrashGuard.ANRDiagnosticException) {
                appendLine()
                appendLine("anr_freeze_duration_ms=${throwable.freezeDurationMs}")
                appendLine("main_thread_state=${throwable.mainThreadState}")
                appendLine("deadlock_summary=${sanitize(throwable.deadlockSummary, 1600)}")
                appendLine("concurrent_ops=${sanitize(throwable.concurrentOpsSummary, 1600)}")
                appendLine("all_threads_dump:")
                appendLine(sanitize(throwable.allThreadsDump, 8_000))
            }
        }.let { sanitize(it, MAX_DESCRIPTION_CHARS) }

        val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val record = JSONObject()
            .put("id", eventId)
            .put("occurred_at_ms", occurredAtMs)
            .put("occurred_at", occurredAt)
            .put("screen_name", sanitize(CrashBreadcrumbs.currentScreen, 120))
            .put("description", description)
            .put("app_version", "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            .put("device_info", sanitize(deviceInfo, 240))

        persistRecord(appContext, record)
        scheduleUpload(appContext)
    }

    /**
     * Atomically persists a queued report. Kept separate so the durable-write
     * guarantee can be verified without starting WorkManager in unit tests.
     */
    internal fun persistRecord(context: Context, record: JSONObject): File {
        val eventId = record.getString("id")
        require(eventId.matches(Regex("[A-Za-z0-9-]{1,80}"))) { "Invalid telemetry event id" }
        val directory = File(context.applicationContext.filesDir, QUEUE_DIR)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("تعذر إنشاء مجلد طابور تقارير الانهيارات")
        }
        val target = File(directory, "$eventId.json")
        val temporary = File(directory, "$eventId.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(record.toString().toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            if (!temporary.renameTo(target)) {
                throw IllegalStateException("تعذر تثبيت تقرير الانهيار في التخزين المحلي")
            }
            return target
        } catch (failure: Throwable) {
            temporary.delete()
            throw failure
        }
    }

    fun scheduleIfPending(context: Context) {
        val directory = File(context.applicationContext.filesDir, QUEUE_DIR)
        if (directory.listFiles { file -> file.isFile && file.extension == "json" }.isNullOrEmpty()) return
        scheduleUpload(context.applicationContext)
    }

    private fun scheduleUpload(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<CrashTelemetryUploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        try {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        } catch (failure: IllegalStateException) {
            // The report is already persisted. If WorkManager is not initialized
            // yet (for example during very early startup), leave it queued and
            // let the next startup retry scheduling instead of failing the app.
            Log.w(TAG, "WorkManager is not ready; crash report remains queued", failure)
        }
    }

    internal fun pendingFiles(context: Context): List<File> =
        File(context.applicationContext.filesDir, QUEUE_DIR)
            .listFiles { file -> file.isFile && file.extension == "json" }
            ?.sortedBy { it.lastModified() }
            .orEmpty()

    internal fun buildPayload(record: JSONObject) = buildJsonObject {
        put("id", record.getString("id"))
        put("user_email", "anonymous")
        put("screen_name", record.optString("screen_name", "غير محددة").take(120))
        put("description", record.optString("description", "").take(MAX_DESCRIPTION_CHARS))
        put("image_url", "")
        // Keep the existing support console classification; the description carries the telemetry subtype.
        put("report_type", "BUG")
        record.optString("occurred_at").takeIf { it.isNotBlank() }?.let { put("created_at", it) }
        put("status", "open")
        put("app_version", record.optString("app_version", "").take(80))
        put("device_info", record.optString("device_info", "").take(240))
        put("developer_reply", "")
        // user_id is deliberately omitted; the anonymous INSERT policy requires NULL.
    }

    internal suspend fun upload(record: JSONObject): Boolean {
        if (!SupabaseConfig.isConfigured) return false
        return try {
            val client = getUploadClient()
            val payload = buildPayload(record)
            client.postgrest.from("dev_bug_reports").upsert(payload) {
                onConflict = "id"
                ignoreDuplicates = true
            }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(TAG, "Crash telemetry upload failed: ${failure.message}")
            false
        }
    }

    private fun getUploadClient(): SupabaseClient {
        uploadClient?.let { return it }
        return synchronized(this) {
            uploadClient ?: createSupabaseClient(
                supabaseUrl = SupabaseConfig.url,
                supabaseKey = SupabaseConfig.key
            ) {
                install(Postgrest) {
                    defaultSchema = "public"
                }
            }.also { uploadClient = it }
        }
    }

    internal fun sanitize(value: String, maxChars: Int): String {
        if (value.isBlank()) return value
        return value
            .replace(credentialPattern) { match ->
                "${match.groupValues[1]}${match.groupValues[2]}[REDACTED]"
            }
            .replace(bearerPattern, "Bearer [REDACTED]")
            .replace(emailPattern, "[EMAIL_REDACTED]")
            .take(maxChars)
    }

    internal val batchSize: Int
        get() = MAX_BATCH_SIZE
}

/** WorkManager retries when a connected network is available. */
internal class CrashTelemetryUploadWorker(
    appContext: Context,
    params: androidx.work.WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val pending = CrashTelemetry.pendingFiles(applicationContext)
        if (pending.isEmpty()) return Result.success()
        if (!SupabaseConfig.isConfigured) return Result.retry()

        var failed = false
        for (file in pending.take(CrashTelemetry.batchSize)) {
            val uploaded = try {
                CrashTelemetry.upload(JSONObject(file.readText(Charsets.UTF_8)))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w("CrashTelemetry", "Unable to process queued report ${file.name}: ${failure.message}")
                false
            }
            if (uploaded) {
                if (!file.delete()) {
                    Log.w("CrashTelemetry", "Cloud accepted report but local queue file remains: ${file.name}")
                    failed = true
                }
            } else {
                failed = true
            }
        }

        val hasMore = CrashTelemetry.pendingFiles(applicationContext).isNotEmpty()
        return when {
            failed -> Result.retry()
            hasMore -> {
                CrashTelemetry.scheduleIfPending(applicationContext)
                Result.success()
            }
            else -> Result.success()
        }
    }
}
