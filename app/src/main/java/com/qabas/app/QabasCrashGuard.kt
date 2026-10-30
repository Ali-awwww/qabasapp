package com.qabas.app

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * نظام اكتشاف وتشخيص الانهيارات والتجمّد المتقدم (Qabas Advanced Crash & ANR Diagnostic Engine)
 * 
 * يوفر تشخيصاً فعلياً للأسباب الجذرية (Root Cause Analysis)، ويتضمن:
 * 1. تشخيص ANR: لالتقاط Stack Trace للـ Main Thread وجميع الخيوط النشطة، واكتشاف Deadlocks وحالات الانتظار والعمليات المتزامنة.
 * 2. تحليل أسباب الانهيارات: استخراج الاستثناء الأصلي والسبب الجذري واستجلاء سلسلة الاستثناءات المتداخلة (Caused By)، مع التمييز بين أخطاء التطبيق والنظام.
 * 3. تتبع سياق الحادثة: الاستفادة من CrashBreadcrumbs لاسترجاع الشاشات السابقة والطلبات وقواعد البيانات والذاكرة.
 * 4. تجميع البصمات (Fingerprinting): تجميع الأعطال المتطابقة دون التأثر برقم السطر مع حساب عدد التكرارات وتأريخ أول وآخر حدوث.
 * 5. تقرير تشخيصي متكامل من 8 أجزاء يوفر التشخيص الدقيق، نسبة الثقة، والإصلاح البرمجي المقترح.
 */
object QabasCrashGuard {
    private const val TAG = "QabasCrashGuard"
    private const val MAX_LOG_FILES = 100
    private const val MAX_FILE_BYTES = 256 * 1024L
    private const val INDEX_FILE = ".fingerprints.json"
    private const val MAX_INDEX_ENTRIES = 500
    private const val RECOVERY_WINDOW_MS = 15_000L
    private const val RELAUNCH_LOOP_GUARD_MS = 6_000L

    // ─── إعدادات Watchdog المتقدم لـ ANR ───
    private const val ANR_TIMEOUT_MS = 4_000L
    private const val ANR_CHECK_INTERVAL_MS = 1_500L
    private const val ANR_COOLDOWN_MS = 30_000L

    private const val PREF_LAST_TS = "crash_guard_last_ts"
    private const val PREF_COUNT = "crash_guard_count"
    private const val PREF_LAST_RELAUNCH_TS = "crash_guard_last_relaunch_ts"
    private const val PREF_LAST_EXIT_SCAN_TS = "crash_guard_last_exit_scan_ts"

    @Volatile private var installed = false
    @Volatile private var anrWatchdog: Thread? = null
    @Volatile private var lastAnrReportMs = 0L
    private val persistLock = Any()

    @Volatile private var appContextRef: Context? = null

    /** Scope جذر لالتقاط استثناءات Coroutines غير المحصنة */
    val rootScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Default + rootHandler)
    }

    val rootHandler: CoroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        runCatching {
            val ctx = appContextRef ?: return@CoroutineExceptionHandler
            record(ctx, Thread.currentThread().name, throwable, "coroutine")
        }
    }

    /** مستوى الثقة في التقرير التشخيصي */
    enum class ConfidenceLevel(val label: String, val badgeColorHex: String) {
        CONFIRMED("السبب مؤكد 🎯", "#10B981"),
        PROBABLE("السبب مرجح 🔍", "#F59E0B"),
        UNDETERMINED("السبب غير محدد بسبب نقص الأدلة ❓", "#6B7280")
    }

    /** بيانات البصمة والتجميع */
    data class FingerprintEntry(
        var count: Int,
        var firstMs: Long,
        var lastMs: Long,
        var file: String,
        var screen: String = "غير محددة",
        var action: String = "غير محددة"
    )

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        appContextRef = appContext

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            var relaunched = false
            try {
                val message = record(appContext, thread.name, throwable, "thread")
                if (thread.name == "main") {
                    val prefs = appContext.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
                    val now = System.currentTimeMillis()
                    val lastTs = prefs.getLong(PREF_LAST_TS, 0L)
                    val lastRelaunch = prefs.getLong(PREF_LAST_RELAUNCH_TS, 0L)
                    prefs.edit()
                        .putLong(PREF_LAST_TS, now)
                        .putLong(PREF_COUNT, prefs.getLong(PREF_COUNT, 0L) + 1)
                        .apply()

                    val isInRelaunchLoop = lastRelaunch > 0 && (now - lastRelaunch) < RELAUNCH_LOOP_GUARD_MS
                    if (now - lastTs > RECOVERY_WINDOW_MS && !isInRelaunchLoop) {
                        runCatching {
                            val intent = appContext.packageManager
                                .getLaunchIntentForPackage(appContext.packageName)
                                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            if (intent != null) {
                                appContext.startActivity(intent)
                                prefs.edit().putLong(PREF_LAST_RELAUNCH_TS, now).apply()
                                relaunched = true
                                Handler(Looper.getMainLooper()).postDelayed({
                                    android.os.Process.killProcess(android.os.Process.myPid())
                                }, 500)
                            }
                        }
                    }
                    runCatching {
                        SystemLogsManager.addLog(
                            "خطأ",
                            "انهيار تم التعافي منه: $message",
                            Color(0xFFF44336)
                        )
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "معالج الانهيار فشل", t)
            }
            if (!relaunched) {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        startAnrWatchdog(appContext)

        Thread({
            runCatching { scanPreviousExits(appContext) }
        }, "QabasExitScan").apply { isDaemon = true }.start()
    }

    /**
     * يفحص أسباب إغلاق العملية السابقة من النظام (API 30+)
     */
    private fun scanPreviousExits(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(PREF_LAST_EXIT_SCAN_TS, 0L) < 60_000L) return

        val exits = runCatching {
            am.getHistoricalProcessExitReasons(context.packageName, 0, 5)
        }.getOrNull() ?: return

        val dayAgo = now - 24L * 60 * 60 * 1000
        var lastRecorded = prefs.getLong("crash_guard_last_exit_recorded_ts", 0L)
        for (info in exits) {
            if (info.timestamp < dayAgo || info.timestamp <= lastRecorded) continue
            val severe = info.reason == ApplicationExitInfo.REASON_CRASH ||
                    info.reason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
                    info.reason == ApplicationExitInfo.REASON_ANR ||
                    info.reason == ApplicationExitInfo.REASON_LOW_MEMORY
            if (!severe) continue
            val label = when (info.reason) {
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "💥 انهيار Native في العملية السابقة"
                ApplicationExitInfo.REASON_ANR -> "🧊 ANR مكتشف من النظام في العملية السابقة"
                ApplicationExitInfo.REASON_LOW_MEMORY -> "🧯 نفدت الذاكرة (OOM) في العملية السابقة"
                else -> "💥 انهيار Java في العملية السابقة"
            }
            val desc = info.description?.take(200)?.let { " — $it" } ?: ""
            record(context, "system_process", RuntimeException("$label$desc"), "system_exit_scan")
            lastRecorded = info.timestamp
        }
        if (lastRecorded > 0L) {
            prefs.edit()
                .putLong("crash_guard_last_exit_recorded_ts", lastRecorded)
                .putLong(PREF_LAST_EXIT_SCAN_TS, now)
                .apply()
        }
    }

    /**
     * ─── Watchdog المتقدم لتشخيص حالات ANR ───
     */
    private fun startAnrWatchdog(context: Context) {
        if (anrWatchdog != null) return
        val mainHandler = Handler(Looper.getMainLooper())
        anrWatchdog = Thread({
            while (!Thread.currentThread().isInterrupted) {
                try {
                    val touchedAt = System.currentTimeMillis()
                    val received = AtomicBoolean(false)

                    mainHandler.post { received.set(true) }
                    Thread.sleep(ANR_TIMEOUT_MS)

                    if (!received.get()) {
                        val now = System.currentTimeMillis()
                        if (now - lastAnrReportMs < ANR_COOLDOWN_MS) {
                            Thread.sleep(ANR_CHECK_INTERVAL_MS)
                            continue
                        }

                        // مهلة تأكيدية لمنع الإبلاغ المفرط عن التأخير العابر
                        Thread.sleep(500)
                        if (received.get()) continue

                        lastAnrReportMs = now
                        val freezeDurationMs = now - touchedAt

                        // التقاط حالة الخيوط بالكامل لحظة التجمّد
                        val mainThread = Looper.getMainLooper().thread
                        val mainStack = mainThread.stackTrace
                        val allThreads = Thread.getAllStackTraces()

                        val deadlockAnalysis = analyzeDeadlocksAndLocks(mainThread, allThreads)
                        val concurrentOps = analyzeConcurrentOperations(allThreads)

                        val anrThrowable = ANRDiagnosticException(
                            freezeDurationMs = freezeDurationMs,
                            mainThreadState = mainThread.state.name,
                            deadlockSummary = deadlockAnalysis,
                            concurrentOpsSummary = concurrentOps,
                            mainThreadStack = mainStack,
                            allThreadsDump = formatThreadsDump(allThreads)
                        )

                        record(context, "main", anrThrowable, "anr_watchdog")
                        Thread.sleep(ANR_CHECK_INTERVAL_MS * 2)
                    } else {
                        Thread.sleep(ANR_CHECK_INTERVAL_MS)
                    }
                } catch (_: InterruptedException) {
                    return@Thread
                } catch (_: Throwable) {
                }
            }
        }, "QabasANRWatchdog").apply {
            isDaemon = true
            start()
        }
    }

    /** تحليل حالات Deadlock والانتظار المتبادل وقفل Monitor */
    private fun analyzeDeadlocksAndLocks(mainThread: Thread, allThreads: Map<Thread, Array<StackTraceElement>>): String {
        val sb = StringBuilder()
        val mainState = mainThread.state

        if (mainState == Thread.State.BLOCKED) {
            sb.appendLine("⚠️ Main Thread محجوب (BLOCKED): ينتظر قفل Synchronization مملوك لخيط آخر!")
        } else if (mainState == Thread.State.WAITING || mainState == Thread.State.TIMED_WAITING) {
            sb.appendLine("⌛ Main Thread في حالة انتظار ($mainState): قد يكون محبوساً بانتظار Lock/CountDownLatch/Join.")
        } else {
            sb.appendLine("⚡ Main Thread في حالة تشغيل ($mainState): ينفذ عملية ثقيلة ومكثفة على خيط الواجهة.")
        }

        // البحث عن خيوط محجوبة أخرى تشير إلى Deadlock
        val blockedThreads = allThreads.keys.filter { it.state == Thread.State.BLOCKED }
        if (blockedThreads.isNotEmpty()) {
            sb.appendLine("🔴 تم رصد ${blockedThreads.size} خيط/خيوط محجوبة (BLOCKED): ${blockedThreads.joinToString { it.name }}")
        }

        val waitingThreadsCount = allThreads.keys.count { it.state == Thread.State.WAITING || it.state == Thread.State.TIMED_WAITING }
        sb.appendLine("📊 إجمالي الخيوط المنتظرة في التطبيق: $waitingThreadsCount من أصل ${allThreads.size}")

        return sb.toString().trimEnd()
    }

    /** تحليل العمليات المتزامنة التي قد تسبب ضغطاً على المعالج أو تجويع الخيوط */
    private fun analyzeConcurrentOperations(allThreads: Map<Thread, Array<StackTraceElement>>): String {
        val runnableThreads = allThreads.entries.filter { (t, _) ->
            t.state == Thread.State.RUNNABLE && t.name != "QabasANRWatchdog"
        }
        val sb = StringBuilder()
        sb.appendLine("🔄 عدد الخيوط النشطة التي تستهلك المعالج وقت التجمّد: ${runnableThreads.size}")
        for ((t, stack) in runnableThreads.take(5)) {
            val topFrame = stack.firstOrNull()?.let { "${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" } ?: "غير معروف"
            sb.appendLine("  • [${t.name}]: $topFrame")
        }
        return sb.toString().trimEnd()
    }

    /** تحويل جميع الخيوط النشطة إلى نص تقريري منظّم */
    private fun formatThreadsDump(allThreads: Map<Thread, Array<StackTraceElement>>): String {
        val sb = StringBuilder()
        sb.appendLine("── Liveness Dump لجميع الخيوط النشطة (${allThreads.size} Threads) ──")
        for ((thread, stack) in allThreads) {
            sb.appendLine("\nThread: [${thread.name}] (State: ${thread.state}, Priority: ${thread.priority}, Daemon: ${thread.isDaemon})")
            val maxFrames = if (thread.name == "main") 15 else 6
            for (i in 0 until minOf(stack.size, maxFrames)) {
                sb.appendLine("    at ${stack[i]}")
            }
            if (stack.size > maxFrames) {
                sb.appendLine("    ... ${stack.size - maxFrames} frames omitted")
            }
        }
        return sb.toString()
    }

    /**
     * استثناء مخصص لحمل تفاصيل تشخيص ANR دون اصطناع كراش
     */
    class ANRDiagnosticException(
        val freezeDurationMs: Long,
        val mainThreadState: String,
        val deadlockSummary: String,
        val concurrentOpsSummary: String,
        val mainThreadStack: Array<StackTraceElement>,
        val allThreadsDump: String
    ) : RuntimeException("🧊 ANR Diagnostic: Main thread non-responsive for ${freezeDurationMs}ms (State: $mainThreadState)") {
        init {
            if (mainThreadStack.isNotEmpty()) {
                stackTrace = mainThreadStack
            }
        }
    }

    /**
     * التسجيل التشخيصي الشامل واستخراج التقرير المنظم من 8 أجزاء
     */
    fun record(context: Context, thread: String, throwable: Throwable, source: String = "thread"): String {
        val now = Date()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(now)
        val display = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now)
        val env = buildEnvHeader(context)
        val diag = diagnose(throwable)
        val key = fingerprintOf(diag)
        var occurrences = 1

        try {
            synchronized(persistLock) {
                val dir = File(context.filesDir, "crash_logs").apply { mkdirs() }
                val index = loadIndex(dir)
                val entry = index[key]
                occurrences = (entry?.count ?: 0) + 1
                val firstMs = entry?.firstMs?.takeIf { it > 0L } ?: now.time
                val firstDisplay = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(firstMs))

                val existing = entry?.file?.let { File(dir, it) }?.takeIf { it.exists() && it.length() < MAX_FILE_BYTES }
                val target = existing ?: File(dir, "crash_$stamp.txt")

                val report = buildStructuredReport(
                    display = display,
                    source = source,
                    thread = thread,
                    occurrences = occurrences,
                    firstDisplay = firstDisplay,
                    fingerprint = key,
                    diag = diag,
                    throwable = throwable,
                    env = env,
                    context = context
                )

                if (existing != null) {
                    existing.appendText("\n\n──────── تكرار رقم #$occurrences @ $display ────────\n" + report)
                } else {
                    target.writeText(report)
                }

                index[key] = FingerprintEntry(
                    count = occurrences,
                    firstMs = firstMs,
                    lastMs = now.time,
                    file = target.name,
                    screen = CrashBreadcrumbs.currentScreen,
                    action = CrashBreadcrumbs.lastAction
                )
                saveIndex(dir, index)
                prune(dir, index)
            }
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "فشل تسجيل الانهيار", t)
        }

        notifyDeveloper(context, throwable, source, occurrences)
        val rootMsg = throwable.message ?: throwable.javaClass.simpleName
        return rootMsg.take(120)
    }

    /**
     * بناء تقرير التشخيص النهائي المنظم المكون من 8 أسئلة أساسية
     */
    private fun buildStructuredReport(
        display: String,
        source: String,
        thread: String,
        occurrences: Int,
        firstDisplay: String,
        fingerprint: String,
        diag: CrashDiagnosis,
        throwable: Throwable,
        env: Map<String, String>,
        context: Context
    ): String {
        val sw = StringWriter()
        PrintWriter(sw).use { pw ->
            pw.println("════════════════════════════════════════════════════════════")
            pw.println("🛸 QABAS ADVANCED DIAGNOSTIC REPORT — $display")
            pw.println("════════════════════════════════════════════════════════════")

            pw.println("\n1. ماذا حدث؟ (What Happened)")
            pw.println("   • نوع الحادثة: ${if (throwable is ANRDiagnosticException) "🧊 ANR تجمّد خيط الواجهة" else "💥 انهيار برجمي (${diag.category})"}")
            pw.println("   • المصدر: $source | الخيط: $thread")
            pw.println("   • بصمة الحادثة (Fingerprint): $fingerprint")
            pw.println("   • عدد التكرارات: $occurrences (أول مرة: $firstDisplay)")

            pw.println("\n2. ما السبب الجذري المحتمل؟ (Root Cause Analysis)")
            pw.println("   • التصنيف: ${diag.category}")
            pw.println("   • الاستثناء الرئيسي: ${diag.exceptionLine}")
            pw.println("   • السبب الجذري الأعمق: ${diag.rootCauseLine}")
            if (diag.nestedCauses.isNotEmpty()) {
                pw.println("   • سلسلة الاستثناءات المتداخلة (Caused By):")
                diag.nestedCauses.forEach { pw.println("      └── $it") }
            }

            pw.println("\n3. ما الدالة التي كانت تنفذ لحظة الحادثة؟ (Executing Function)")
            pw.println("   • موقع الحادثة: ${diag.crashSite}")
            pw.println("   • النطاق البرمجي: ${diag.blame}")

            pw.println("\n4. ما الشاشة التي كان المستخدم يتفاعل معها؟ (Active Screen)")
            pw.println("   • الشاشة الحالية: ${CrashBreadcrumbs.currentScreen}")
            pw.println("   • الشاشة السابقة: ${CrashBreadcrumbs.previousScreen}")

            pw.println("\n5. ما العملية التي تسببت في المشكلة؟ (Triggering Operation)")
            pw.println("   • آخر تفاعل مسجل: ${CrashBreadcrumbs.lastAction}")

            pw.println("\n6. ما الأدلة التقنية التي تدعم التشخيص؟ (Technical Evidence)")
            env.forEach { (k, v) -> pw.println("   • $k: $v") }
            pw.println()
            pw.println(CrashBreadcrumbs.dumpContextSnapshot(context))

            if (throwable is ANRDiagnosticException) {
                pw.println("\n── تفاصيل ANR وتحليل الخيوط ──")
                pw.println("• مدة التجمّد الدقيقة: ${throwable.freezeDurationMs}ms")
                pw.println("• حالة Main Thread: ${throwable.mainThreadState}")
                pw.println("• تحليل القفل والانتظار (Deadlocks & Locks):\n${throwable.deadlockSummary}")
                pw.println("• العمليات المتزامنة:\n${throwable.concurrentOpsSummary}")
                pw.println("\n${throwable.allThreadsDump}")
            }

            pw.println("\n── سجل الأحداث المتعاقبة (Breadcrumbs Sequence) ──")
            pw.println(CrashBreadcrumbs.dump())

            pw.println("\n7. ما مدى الثقة في تحديد السبب؟ (Confidence Level)")
            pw.println("   • المستوى: ${diag.confidence.label}")

            pw.println("\n8. ما الإصلاح البرمجي المقترح؟ (Suggested Developer Fix)")
            pw.println("   • ${diag.suggestedFix}")

            pw.println("════════════════════════════════════════════════════════════")
            pw.println("── Stack Trace الكامل ──")
            throwable.printStackTrace(pw)
        }
        return sw.toString()
    }

    /**
     * تشخيص تفصيلي مع استخراج الثقة والإصلاح المقترح
     */
    data class CrashDiagnosis(
        val rootCause: Throwable,
        val exceptionType: String,
        val exceptionLine: String,
        val rootCauseLine: String,
        val crashSite: String,
        val crashSiteKey: String,
        val blame: String,
        val category: String,
        val confidence: ConfidenceLevel,
        val suggestedFix: String,
        val nestedCauses: List<String>
    )

    private fun diagnose(throwable: Throwable): CrashDiagnosis {
        val root = deepestCause(throwable)
        val exceptionType = throwable.javaClass.name
        val exceptionLine = "$exceptionType: ${(throwable.message ?: "").take(200)}"
        val rootCauseLine = if (root === throwable) exceptionLine
        else "${root.javaClass.name}: ${(root.message ?: "").take(200)}"

        val appFrame = firstAppFrame(throwable)
        val crashSite = appFrame ?: firstFrame(root) ?: "غير معروف"

        val crashSiteKey = appFrame
            ?.let { Regex("""(at [\w.$]+\.\w+)\(""").find(it)?.groupValues?.get(1) }
            ?: "unknown"

        val isAppCode = appFrame != null
        val blame = if (isAppCode) "أصل الخطأ من كود التطبيق نفسه (com.qabas.app)"
        else "أصل الخطأ من مكتبة خارجية أو من نظام Android"

        val confidence = when {
            throwable is ANRDiagnosticException -> ConfidenceLevel.CONFIRMED
            isAppCode && (root is NullPointerException || root is IndexOutOfBoundsException || root is ClassCastException || root is IllegalStateException) -> ConfidenceLevel.CONFIRMED
            isAppCode -> ConfidenceLevel.PROBABLE
            else -> ConfidenceLevel.UNDETERMINED
        }

        val category = categorize(throwable)
        val fix = generateSuggestedFix(throwable, root, isAppCode, crashSite)
        val nested = extractNestedCauses(throwable)

        return CrashDiagnosis(
            rootCause = root,
            exceptionType = exceptionType,
            exceptionLine = exceptionLine,
            rootCauseLine = rootCauseLine,
            crashSite = crashSite,
            crashSiteKey = crashSiteKey,
            blame = blame,
            category = category,
            confidence = confidence,
            suggestedFix = fix,
            nestedCauses = nested
        )
    }

    private fun generateSuggestedFix(throwable: Throwable, root: Throwable, isAppCode: Boolean, crashSite: String): String {
        val msg = (root.message ?: "").lowercase()
        return when {
            throwable is ANRDiagnosticException ->
                "قم بنقل العمليات الثقيلة (I/O, Parsing, SQLite, Bitmap manipulation) خارج خيط الواجهة المباشر باستخدام withContext(Dispatchers.IO) أو viewModelScope.launch(Dispatchers.IO)."

            root is NullPointerException ->
                "تحقق من الكائنات الخالية عند $crashSite واستخدم Safe Call (?.) أو Elvis Operator (?:) للقيم الافتراضية."

            root is IndexOutOfBoundsException ->
                "تحقق من حدود القوائم والمصفوفات قبل الوصول إلى العناصر (تأكد من أن index < list.size) أو استخدم getOrNull(index)."

            root is OutOfMemoryError ->
                "قم بضغط الصور وتخفيض دقة البتخارطات المحملة، وتجنب الاحتفاظ بمرجع ثابت (static reference) للأنشطة أو الواجهات."

            root is IllegalStateException ->
                "تأكد من أن الكائن في الحالة الصحيحة قبل استدعاء الدالة، وتحقق من الملاءمة في إدارات حالات Jetpack Compose والـ StateFlow."

            root is ClassCastException ->
                "استخدم التحويل الآمن as? مع فحص النوع is قبل إجراء التحويل القسري."

            msg.contains("sqlite") || msg.contains("room") ->
                "تأكد من تنفيذ استعلامات قاعدة البيانات على Dispatchers.IO والتحقق من إصدار التحديث (Migration)."

            msg.contains("socket") || msg.contains("unknownhost") || msg.contains("timeout") ->
                "حط طلبات الشبكة بـ runCatching أو try-catch لضمان معالجة حالات انقطاع الاتصال وسرعة الاستجابة."

            !isAppCode ->
                "الخطأ في مكتبة خارجية أو النظام. قم بتحصين مكان استدعاء المكتبة في $crashSite بكتلة try-catch آمنة."

            else ->
                "افحص السجل البرمجي لموقع العطل عند $crashSite وقم بتطويق المسار الحرج بكتلة معالجة استثناءات آمنة."
        }
    }

    private fun extractNestedCauses(throwable: Throwable): List<String> {
        val list = mutableListOf<String>()
        var current: Throwable? = throwable.cause
        var guard = 0
        while (current != null && guard < 10) {
            val topFrame = current.stackTrace.firstOrNull()?.let { " at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" } ?: ""
            list.add("${current.javaClass.name}: ${(current.message ?: "").take(150)}$topFrame")
            current = current.cause
            guard++
        }
        return list
    }

    private fun deepestCause(throwable: Throwable): Throwable {
        var current = throwable
        var guard = 0
        while (guard < 20) {
            val cause = current.cause ?: break
            if (cause === current) break
            current = cause
            guard++
        }
        return current
    }

    private fun formatFrame(frame: StackTraceElement): String =
        "at ${frame.className}.${frame.methodName}(${frame.fileName}:${frame.lineNumber})"

    private fun firstAppFrame(throwable: Throwable): String? {
        var current: Throwable? = throwable
        var guard = 0
        while (current != null && guard < 20) {
            current.stackTrace.firstOrNull { it.className.startsWith("com.qabas.app") }
                ?.let { return formatFrame(it) }
            current = current.cause
            guard++
        }
        return null
    }

    private fun firstFrame(throwable: Throwable): String? =
        throwable.stackTrace.firstOrNull()?.let { formatFrame(it) }

    private fun categorize(throwable: Throwable): String {
        val name = throwable.javaClass.name
        val message = throwable.message.orEmpty()
        return when {
            throwable is ANRDiagnosticException -> "ANR / Frozen UI"
            throwable is OutOfMemoryError -> "OOM / Memory"
            throwable is StackOverflowError -> "StackOverflow"
            name.contains("SQLite") || name.contains("SQLException") || message.contains("ENOSPC") -> "Database / Storage"
            throwable is java.net.UnknownHostException || throwable is java.net.SocketTimeoutException -> "Network / Connectivity"
            throwable is SecurityException -> "Permission / Security"
            throwable is NullPointerException -> "NullPointer"
            throwable is IllegalArgumentException -> "Logic / Argument"
            throwable is IllegalStateException -> "State / Lifecycle"
            else -> throwable.javaClass.simpleName
        }
    }

    private fun fingerprintOf(diag: CrashDiagnosis): String = "${diag.exceptionType}|${diag.crashSiteKey}"

    private fun loadIndex(dir: File): MutableMap<String, FingerprintEntry> {
        val map = LinkedHashMap<String, FingerprintEntry>()
        runCatching {
            val f = File(dir, INDEX_FILE)
            if (!f.exists()) return@runCatching
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                map[o.getString("key")] = FingerprintEntry(
                    count = o.optInt("count", 1),
                    firstMs = o.optLong("first", 0L),
                    lastMs = o.optLong("last", 0L),
                    file = o.optString("file", ""),
                    screen = o.optString("screen", "غير محددة"),
                    action = o.optString("action", "غير محددة")
                )
            }
        }
        return map
    }

    private fun saveIndex(dir: File, map: Map<String, FingerprintEntry>) {
        runCatching {
            val arr = JSONArray()
            map.forEach { (key, e) ->
                arr.put(JSONObject().apply {
                    put("key", key)
                    put("count", e.count)
                    put("first", e.firstMs)
                    put("last", e.lastMs)
                    put("file", e.file)
                    put("screen", e.screen)
                    put("action", e.action)
                })
            }
            File(dir, INDEX_FILE).writeText(arr.toString())
        }
    }

    private fun prune(dir: File, index: MutableMap<String, FingerprintEntry>) {
        runCatching {
            val logs = dir.listFiles()?.filter { it.name.startsWith("crash_") }?.sortedByDescending { it.lastModified() }
                ?: emptyList()
            if (logs.size > MAX_LOG_FILES) logs.drop(MAX_LOG_FILES).forEach { it.delete() }
            if (index.size > MAX_INDEX_ENTRIES) {
                val keep = index.entries.sortedByDescending { it.value.lastMs }.take(MAX_INDEX_ENTRIES)
                index.clear()
                keep.forEach { (k, v) -> index[k] = v }
            }
        }
    }

    private fun buildEnvHeader(context: Context): Map<String, String> {
        val app = context.applicationContext as? Application
        val pkg = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val versionName = pkg?.versionName ?: "?"
        val versionCode = if (Build.VERSION.SDK_INT >= 28) pkg?.longVersionCode?.toString() ?: "?" else pkg?.versionCode?.toString() ?: "?"
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)
        val freeMb = (mi?.availMem ?: 0) / (1024 * 1024)
        val totalMb = (mi?.totalMem ?: 0) / (1024 * 1024)

        return mapOf(
            "Device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "App Version" to "$versionName (code $versionCode)",
            "System Memory" to "${freeMb}MB free / ${totalMb}MB total",
            "Supported ABIs" to Build.SUPPORTED_ABIS.joinToString(",")
        )
    }

    /**
     * محاكاة اختبارية آمنة للانهيار
     */
    fun simulateCrash(context: Context) {
        val ctx = context.applicationContext
        val throwable = IllegalStateException("🧪 [Self-Test] محاكاة انهيار تجريبي لاختبار محرك التشخيص الذكي")
        record(ctx, "main", throwable, "self_test_crash")
    }

    /**
     * محاكاة اختبارية آمنة لـ ANR
     */
    fun simulateAnr(context: Context) {
        val ctx = context.applicationContext
        val mainThread = Looper.getMainLooper().thread
        val anr = ANRDiagnosticException(
            freezeDurationMs = 5200L,
            mainThreadState = mainThread.state.name,
            deadlockSummary = "🧪 [Self-Test] محاكاة تجريبية لقفل الخيوط والمراقبة (Lock Contention Verification)",
            concurrentOpsSummary = "🧪 [Self-Test] محاكاة تشغيل 3 خيوط معالجة متزامنة",
            mainThreadStack = mainThread.stackTrace,
            allThreadsDump = formatThreadsDump(Thread.getAllStackTraces())
        )
        record(ctx, "main", anr, "self_test_anr")
    }

    private fun notifyDeveloper(context: Context, throwable: Throwable, source: String, occurrences: Int) {
        runCatching {
            val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            val url = prefs.getString("developer_webhook_url", null)?.trim().orEmpty()
            if (url.isEmpty() || !url.startsWith("http")) return

            val sessionKey = "webhook_sent_${source}_${throwable.javaClass.simpleName}"
            if (prefs.getBoolean(sessionKey, false)) return
            prefs.edit().putBoolean(sessionKey, true).apply()

            val pkg = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            val versionName = pkg?.versionName ?: "?"
            val diag = diagnose(throwable)
            val summary = """
                🚨 *Qabas Diagnostic Alert on ${Build.MANUFACTURER} ${Build.MODEL}*
                Version: $versionName • Android ${Build.VERSION.RELEASE}
                Type: ${diag.category} • Source: $source
                Site: ${diag.crashSite}
                Confidence: ${diag.confidence.label}
                Occurrences: $occurrences
                Suggested Fix: ${diag.suggestedFix}
            """.trimIndent()

            Thread({
                runCatching {
                    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    val payload = JSONObject().put("text", summary).toString()
                    conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                    conn.responseCode
                }
            }, "QabasWebhook").apply { isDaemon = true }.start()
        }
    }
}
