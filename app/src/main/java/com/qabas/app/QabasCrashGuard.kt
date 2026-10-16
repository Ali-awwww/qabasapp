package com.qabas.app

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * حارس الانهيارات: يلتقط:
 *  - استثناءات الخيوط (Thread.UncaughtExceptionHandler) + يعيد تشغيل التطبيق لو main
 *  - استثناءات Coroutines (CoroutineExceptionHandler على scope جذر)
 *  - ANR عبر Watchdog يرسل انهياراً اصطناعياً لو main لم يستجب خلال [ANR_TIMEOUT_MS]
 *
 * كل انهيار يُحفظ في filesDir/crash_logs/crash_<timestamp>.txt مع:
 *  - رأس يحتوي: thread, time, device, version, memory, foreground, hot-path
 *  - الـ stack الكامل
 *
 * حماية loop: لو الانهيار حدث داخل onCreate قبل 6 ثوانٍ من آخر إعادة تشغيل،
 * لا يعيد فتح التطبيق (وإلا يدخل في loop لا نهائي).
 */
object QabasCrashGuard {
    private const val TAG = "QabasCrashGuard"
    private const val MAX_LOG_FILES = 100
    private const val MAX_FILE_BYTES = 256 * 1024L
    private const val EXIT_TRACE_MAX_BYTES = 64 * 1024
    private const val INDEX_FILE = ".fingerprints.json"
    private const val MAX_INDEX_ENTRIES = 500
    private const val RECOVERY_WINDOW_MS = 15_000L
    private const val RELAUNCH_LOOP_GUARD_MS = 6_000L
    private const val ANR_TIMEOUT_MS = 5_000L
    private const val ANR_CHECK_INTERVAL_MS = 1_500L
    private const val PREF_LAST_TS = "crash_guard_last_ts"
    private const val PREF_COUNT = "crash_guard_count"
    private const val PREF_LAST_RELAUNCH_TS = "crash_guard_last_relaunch_ts"
    private const val PREF_LAST_EXIT_SCAN_TS = "crash_guard_last_exit_scan_ts"

    @Volatile private var installed = false
    @Volatile private var anrWatchdog: Thread? = null
    private val processStartMs = System.currentTimeMillis()
    private val persistLock = Any()

    /**
     * Scope جذر لالتقاط انهيارات coroutines. يُستخدم من قبل بقية التطبيق
     * عبر [rootHandler] عند بناء scope جديد.
     */
    val rootScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Default + rootHandler)
    }

    /**
     * معالج استثناءات coroutines — يستدعي نفس record() لضمان تسجيل الانهيار
     * حتى لو لم يمر عبر Thread.setDefaultUncaughtExceptionHandler.
     */
    val rootHandler: CoroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        try {
            val ctx = appContextRef ?: return@CoroutineExceptionHandler
            record(ctx, Thread.currentThread().name, throwable, "coroutine")
        } catch (_: Throwable) {
        }
    }

    @Volatile private var appContextRef: Context? = null

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

                    // منع loop: لو الانهيار بعد إعادة تشغيل خلال 6 ثوانٍ، لا نعيد فتح.
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
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    android.os.Process.killProcess(android.os.Process.myPid())
                                }, 500)
                            }
                        }
                    }
                    try {
                        SystemLogsManager.addLog(
                            "خطأ",
                            "انهيار تم التعافي منه: $message",
                            Color(0xFFF44336)
                        )
                    } catch (_: Throwable) {
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "معالج الانهيار فشل", t)
            }
            if (!relaunched) {
                // ترك defaultHandler يقتل العملية في حالة loop أو انهيار worker
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        startAnrWatchdog(appContext)
        // فحص أسباب إغلاق العمليات السابقة (native/ANR/OOM) على خيط منفصل — لا يعطّل الإقلاع.
        Thread({
            runCatching { scanPreviousExits(appContext) }
        }, "QabasExitScan").apply { isDaemon = true }.start()
    }

    /**
     * يفحص أسباب إغلاق العملية السابقة (انهيار native / ANR / OOM) عبر
     * [ActivityManager.getHistoricalProcessExitReasons] (API 30+) ويسجّلها كعطل جديد.
     * يتجاهل الإغلاق الطبيعي، ولا يعيد تسجيل نفس الحادثة بفضل طابعها الزمني.
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
                info.reason == ApplicationExitInfo.REASON_OOM
            if (!severe) continue
            val label = when (info.reason) {
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "💥 انهيار native في العملية السابقة"
                ApplicationExitInfo.REASON_ANR -> "🧊 ANR في العملية السابقة"
                ApplicationExitInfo.REASON_OOM -> "🧯 نفدت الذاكرة (OOM) في العملية السابقة"
                else -> "💥 انهيار Java في العملية السابقة"
            }
            val desc = info.description?.take(200)?.let { " — $it" } ?: ""
            record(context, "process", RuntimeException("$label$desc"), "previous-exit")
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
     * Watchdog بسيط: كل [ANR_CHECK_INTERVAL_MS] يلامس خيط main عبر Handler.
     * لو لم يستقبل main اللمسة خلال [ANR_TIMEOUT_MS] → يسجل انهيار ANR اصطناعي.
     */
    private fun startAnrWatchdog(context: Context) {
        if (anrWatchdog != null) return
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        anrWatchdog = Thread({
            while (!Thread.currentThread().isInterrupted) {
                try {
                    val touchedAt = System.currentTimeMillis()
                    val received = java.util.concurrent.atomic.AtomicBoolean(false)
                    mainHandler.post { received.set(true) }
                    Thread.sleep(ANR_TIMEOUT_MS)
                    if (!received.get()) {
                        // نلتقط stack خيط main الحقيقي وقت الجمود — أدق دليل على سبب ANR
                        val mainStack = android.os.Looper.getMainLooper().thread.stackTrace
                        val anr = RuntimeException(
                            "🧊 ANR: main thread did not respond within ${ANR_TIMEOUT_MS}ms " +
                            "(last touch ${System.currentTimeMillis() - touchedAt}ms ago)"
                        ).apply {
                            if (mainStack.isNotEmpty()) stackTrace = mainStack
                        }
                        record(context, "main", anr, "anr")
                        // بعد تسجيل ANR، لا نرسل أكثر من واحد كل دقيقة لتجنب spam
                        Thread.sleep(60_000L)
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

    /**
     * يسجل انهياراً: يستخرج موقع العطل، يبني رأس معلومات البيئة، ثم:
     *  - يحسب بصمة (fingerprint) للانهيار ويجمّع التكرارات في ملف واحد.
     *  - يحفظ الـ stack الكامل + الـ breadcrumbs.
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

                // أعد استخدام ملف البصمة نفسه حتى يتضخم؛ بعده ابدأ ملفاً جديداً.
                val existing = entry?.file?.let { File(dir, it) }?.takeIf { it.exists() && it.length() < MAX_FILE_BYTES }
                val target = existing ?: File(dir, "crash_$stamp.txt")

                val stack = StringWriter().also { sw ->
                    PrintWriter(sw).use { pw ->
                        pw.println("════════════════════════════════════════════")
                        pw.println("Qabas Crash Log — $display")
                        pw.println("Source: $source")
                        pw.println("Thread: $thread")
                        pw.println("Category: ${diag.category}")
                        pw.println("Fingerprint: $key")
                        pw.println("Occurrences: $occurrences (first seen $firstDisplay)")
                        pw.println("Blame: ${diag.blame}")
                        pw.println("Exception: ${diag.exceptionLine}")
                        if (diag.rootCauseLine != diag.exceptionLine) {
                            pw.println("Root cause: ${diag.rootCauseLine}")
                        }
                        pw.println("Crash site: ${diag.crashSite}")
                        if (diag.hotPath != diag.crashSite) {
                            pw.println("Hot path: ${diag.hotPath}")
                        }
                        env.forEach { (k, v) -> pw.println("$k: $v") }
                        pw.println("── Breadcrumbs (آخر الأحداث قبل العطل) ──")
                        pw.println(CrashBreadcrumbs.dump())
                        pw.println("════════════════════════════════════════════")
                        throwable.printStackTrace(pw)
                    }
                }

                if (existing != null) {
                    existing.appendText("\n\n──────── occurrence #$occurrences @ $display ────────\n" + stack)
                } else {
                    target.writeText(stack.toString())
                }

                index[key] = FingerprintEntry(
                    count = occurrences,
                    firstMs = firstMs,
                    lastMs = now.time,
                    file = target.name
                )
                saveIndex(dir, index)
                prune(dir, index)
            }
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "فشل تسجيل الانهيار", t)
        }
        notifyDeveloper(context, throwable, source, occurrences)
        val root = throwable.message ?: throwable.javaClass.simpleName
        return root.take(120)
    }

    /** بيانات بصمة انهيار مجمّعة. */
    private data class FingerprintEntry(
        var count: Int,
        var firstMs: Long,
        var lastMs: Long,
        var file: String
    )

    private fun fingerprintOf(diag: CrashDiagnosis): String = "${diag.exceptionType}|${diag.crashSiteKey}"

    private fun loadIndex(dir: File): MutableMap<String, FingerprintEntry> {
        val map = LinkedHashMap<String, FingerprintEntry>()
        runCatching {
            val f = File(dir, INDEX_FILE)
            if (!f.exists()) return@runCatching
            val arr = org.json.JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                map[o.getString("key")] = FingerprintEntry(
                    count = o.optInt("count", 1),
                    firstMs = o.optLong("first", 0L),
                    lastMs = o.optLong("last", 0L),
                    file = o.optString("file", "")
                )
            }
        }
        return map
    }

    private fun saveIndex(dir: File, map: Map<String, FingerprintEntry>) {
        runCatching {
            val arr = org.json.JSONArray()
            map.forEach { (key, e) ->
                arr.put(org.json.JSONObject().apply {
                    put("key", key)
                    put("count", e.count)
                    put("first", e.firstMs)
                    put("last", e.lastMs)
                    put("file", e.file)
                })
            }
            File(dir, INDEX_FILE).writeText(arr.toString())
        }
    }

    /** يحذف ملفات السجلات الزائدة ويقصّ فهرس البصمات القديمة. */
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

    /**
     * يبني رأس معلومات البيئة: اسم الجهاز، Android API، versionName/Code،
     * الذاكرة المتاحة، هل التطبيق في المقدمة.
     */
    private fun buildEnvHeader(context: Context): Map<String, String> {
        val app = context.applicationContext as? Application
        val pkg = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val versionName = pkg?.versionName ?: "?"
        val versionCode = if (Build.VERSION.SDK_INT >= 28) pkg?.longVersionCode?.toString() ?: "?" else pkg?.versionCode?.toString() ?: "?"
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)
        val freeMb = mi.availMem / (1024 * 1024)
        val totalMb = mi.totalMem / (1024 * 1024)
        val foreground = app?.let {
            val running = android.app.ActivityManager.RunningAppProcessInfo()
            android.app.ActivityManager.getMyMemoryState(running)
            running.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        } ?: false
        return mapOf(
            "Device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "App version" to "$versionName (code $versionCode)",
            "Memory" to "${freeMb}MB free / ${totalMb}MB total",
            "Foreground" to foreground.toString(),
            "Abis" to Build.SUPPORTED_ABIS.joinToString(",")
        )
    }

    /**
     * يشخّص الانهيار بدقة: يميّز الاستثناء المُبلَّغ عن السبب الجذري (deepest cause)،
     * ويحدد موقع الانهيار داخل كود التطبيق نفسه، ونوع العطل، وإن وُجد إطار متكرر (loop).
     */
    private data class CrashDiagnosis(
        val rootCause: Throwable,
        val exceptionType: String,
        val exceptionLine: String,
        val rootCauseLine: String,
        val crashSite: String,
        val crashSiteKey: String,
        val hotPath: String,
        val blame: String,
        val category: String
    )

    private fun diagnose(throwable: Throwable): CrashDiagnosis {
        val root = deepestCause(throwable)
        val exceptionType = throwable.javaClass.name
        val exceptionLine = "$exceptionType: ${(throwable.message ?: "").take(200)}"
        val rootCauseLine = if (root === throwable) exceptionLine
            else "${root.javaClass.name}: ${(root.message ?: "").take(200)}"
        val appFrame = firstAppFrame(throwable)
        val crashSite = appFrame
            ?: firstFrame(root)
            ?: "غير معروف"
        val crashSiteKey = appFrame
            ?.let { Regex("""(at [\w.$]+\.\w+)\(""").find(it)?.groupValues?.get(1) }
            ?: "unknown"
        val blame = if (appFrame != null) {
            "عطل داخل كود التطبيق: $appFrame"
        } else {
            "عطل في مكتبة خارجية/نظام — راجع السبب الجذري"
        }
        val hot = detectRepeatedFrame(throwable)
        return CrashDiagnosis(
            rootCause = root,
            exceptionType = exceptionType,
            exceptionLine = exceptionLine,
            rootCauseLine = rootCauseLine,
            crashSite = crashSite,
            crashSiteKey = crashSiteKey,
            hotPath = hot ?: crashSite,
            blame = blame,
            category = categorize(throwable)
        )
    }

    /** يتتبع سلسلة الأسباب حتى أعمق سبب جذري (بحد أقصى آمن ضد الحلقات). */
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

    /** أول إطار داخل كود تطبيقنا — أدق مؤشر على موقع العطل الفعلي. */
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

    /**
     * يبحث عن إطار مكرّر داخل كامل سلسلة الأسباب — يدل على حلقة/تكرار لانهائي.
     * يعيد null لو لا يوجد تكرار واضح (≥ 3 مرات).
     */
    private fun detectRepeatedFrame(throwable: Throwable): String? {
        val frames = ArrayList<String>()
        var current: Throwable? = throwable
        var guard = 0
        while (current != null && guard < 20) {
            current.stackTrace.forEach { frames.add(formatFrame(it)) }
            current = current.cause
            guard++
        }
        val top = frames.groupingBy { it }.eachCount().maxByOrNull { it.value }
        return if (top != null && top.value >= 3) "${top.key} × ${top.value}" else null
    }

    /** تصنيف سريع لنوع العطل لتصفية الأسباب بسرعة. */
    private fun categorize(throwable: Throwable): String {
        val name = throwable.javaClass.name
        val message = throwable.message.orEmpty()
        return when {
            throwable is OutOfMemoryError -> "OOM"
            throwable is StackOverflowError -> "StackOverflow"
            name.contains("SQLite") || name.contains("SQLException") || message.contains("ENOSPC") || message.contains("disk") -> "Storage"
            throwable is java.net.UnknownHostException ||
                throwable is java.net.ConnectException ||
                throwable is java.net.SocketTimeoutException ||
                throwable is java.net.SocketException ||
                name.contains("ConnectivityManager") || name.contains("TooManyRequests") -> "Network"
            throwable is SecurityException -> "Permission"
            throwable is NullPointerException -> "NullPointer"
            throwable is IllegalArgumentException -> "Logic"
            throwable is IllegalStateException -> "State"
            throwable is RuntimeException -> "Runtime"
            else -> throwable.javaClass.simpleName
        }
    }

    /**
     * يحاكي انهياراً اختبارياً بأمان: يمرّ عبر نفس مسار الإنتاج [record] ليُسجَّل
     * بنفس الترويسة والـbreadcrumbs، لكنه **لا يرمي استثناءً غير ملتقط** ولا يقتل
     * العملية — فلا يظهر حوار «توقف التطبيق» ولا يتعطّل التطبيق. يُستدعى من لوحة المطور.
     */
    fun simulateCrash(context: Context) {
        val ctx = context.applicationContext
        val throwable = IllegalStateException("🧪 [Dev] simulated crash for QabasCrashGuard self-test")
        record(ctx, "main", throwable, "self-test")
    }

    /**
     * يُعلم المطور عبر Webhook (Slack/Discord/IFTTT/Webhook.site...).
     * الـ URL يُخزَّن في SharedPreferences تحت developer_webhook_url.
     * يعمل فقط لو: (1) URL موجود، (2) أول انهيار من نوعه في الجلسة (لتجنب spam).
     *
     * يُستدعى من [record] فوراً بعد حفظ الملف — لا يوقف الحارس لو فشل.
     */
    private fun notifyDeveloper(context: Context, throwable: Throwable, source: String, occurrences: Int) {
        try {
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
                🚨 *Qabas crash on ${Build.MANUFACTURER} ${Build.MODEL}*
                App: $versionName • Android ${Build.VERSION.RELEASE}
                Source: $source • Thread: ${Thread.currentThread().name}
                Category: ${diag.category}
                Exception: ${diag.exceptionLine.take(200)}
                Crash site: ${diag.crashSite}
                Hot path: ${diag.hotPath}
                Occurrences: $occurrences
            """.trimIndent()

            // fire-and-forget — لا نمنع الحارس لو الـ webhook بطيء أو معطل
            Thread({
                try {
                    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    val payload = org.json.JSONObject().put("text", summary).toString()
                    conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                    conn.responseCode
                } catch (t: Throwable) {
                    android.util.Log.w(TAG, "فشل إرسال webhook المطور", t)
                }
            }, "QabasWebhook").apply { isDaemon = true }.start()
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "notifyDeveloper failed", t)
        }
    }
}
