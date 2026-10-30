package com.qabas.app

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

/**
 * فتات الخبز (Crash Breadcrumbs & Ring Buffer):
 * نظام تتبع متسلسل لحوادث وسياق التشغيل قبل وقوع أي عطل أو ANR.
 * 
 * يحتفظ بآخر 100 حدث داخل Ring Buffer منخفض الاستهلاك للذاكرة،
 * مع تعقيم كامل وحذف البيانات الحساسة (كلمات المرور، الرموز، المفاتيح).
 */
object CrashBreadcrumbs {
    private const val TAG = "CrashBreadcrumbs"
    private const val MAX_CRUMBS = 100

    data class Crumb(
        val timeMs: Long,
        val category: String,
        val message: String,
        val threadName: String = Thread.currentThread().name,
        val details: Map<String, String>? = null
    )

    private val crumbs = ArrayDeque<Crumb>(MAX_CRUMBS)
    private val processStartMs = System.currentTimeMillis()

    // ─── سياق الشاشات والعمليات الجارية ───
    @Volatile var currentScreen: String = "غير محددة"
        private set
    @Volatile var previousScreen: String = "لا توجد"
        private set
    @Volatile var lastAction: String = "إقلاع التطبيق"
        private set
    @Volatile var lastActionTimeMs: Long = System.currentTimeMillis()
        private set

    val activeNetworkRequests = AtomicInteger(0)
    val activeDbOperations = AtomicInteger(0)
    val activeBackgroundTasks = AtomicInteger(0)

    // ─── حارس الخصوصية لتعقيم البيانات الحساسة ───
    private val SANITIZE_PATTERNS = listOf(
        Pattern.compile("(?i)(password|pass|secret|token|bearer|api_key|apikey|authorization|auth|jwt|access_token)[=:\\s]+\"?([^\"\\s&,;]+)\"?"),
        Pattern.compile("(?i)(key=AIzaSy)[A-Za-z0-9_-]{25,}")
    )

    fun sanitize(input: String): String {
        if (input.isBlank()) return input
        var sanitized = input
        for (pattern in SANITIZE_PATTERNS) {
            val matcher = pattern.matcher(sanitized)
            if (matcher.find()) {
                sanitized = matcher.replaceAll("$1=[REDACTED]")
            }
        }
        return sanitized.take(300)
    }

    @Synchronized
    fun leave(category: String, message: String, details: Map<String, String>? = null) {
        runCatching {
            val cleanMsg = sanitize(message)
            val cleanDetails = details?.mapValues { sanitize(it.value) }
            if (crumbs.size >= MAX_CRUMBS) crumbs.removeFirst()
            crumbs.addLast(
                Crumb(
                    timeMs = System.currentTimeMillis(),
                    category = category,
                    message = cleanMsg,
                    threadName = Thread.currentThread().name,
                    details = cleanDetails
                )
            )
            Log.v(TAG, "[$category] $cleanMsg")
        }
    }

    // ─── دالات تسهيلية لتسجيل مختلف أنواع الأحداث ───
    fun screen(name: String) {
        if (name != currentScreen) {
            previousScreen = currentScreen
            currentScreen = name
            leave("screen", "انتقال للشاشة: $name (من: $previousScreen)")
        }
    }

    fun action(name: String) {
        lastAction = name
        lastActionTimeMs = System.currentTimeMillis()
        leave("action", "تفاعل مستخدم: $name")
    }

    fun api(name: String, ms: Long, ok: Boolean, endpoint: String? = null) {
        val extra = if (endpoint.isNullOrBlank()) "" else " ($endpoint)"
        leave("api", "طلب شبكة $name$extra - ${ms}ms - ${if (ok) "نجاح ✅" else "فشل ❌"}")
    }

    fun database(opName: String, queryOrTable: String? = null) {
        val extra = if (queryOrTable.isNullOrBlank()) "" else " - $queryOrTable"
        leave("database", "عملية قاعدة بيانات: $opName$extra")
    }

    fun background(taskName: String, state: String) {
        leave("background", "مهمة خلفية: $taskName ($state)")
    }

    fun network(state: String) {
        leave("network", "حالة الشبكة: $state")
    }

    fun memory(level: String) {
        leave("memory", "تحذير الذاكرة: $level")
    }

    fun gesture(name: String) = action("إيماءة: $name")
    fun route(name: String, id: String?) = leave("route", if (id.isNullOrBlank()) name else "$name=$id")
    fun note(category: String, message: String) = leave(category, message)

    fun processUptimeSec(): Long = (System.currentTimeMillis() - processStartMs) / 1000

    @Synchronized
    fun dump(): String {
        if (crumbs.isEmpty()) return "(لا توجد أحداث مسجلة في فتات الخبز)"
        val fmt = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
        val sb = StringBuilder()
        sb.appendLine("· مدة التشغيل: ${processUptimeSec()} ثانية | إجمالي الأحداث: ${crumbs.size} ·")
        for (c in crumbs) {
            val detailsStr = if (!c.details.isNullOrEmpty()) " ${c.details}" else ""
            sb.appendLine("${fmt.format(java.util.Date(c.timeMs))} [${c.threadName}] [${c.category}] ${c.message}$detailsStr")
        }
        return sb.toString().trimEnd()
    }

    /**
     * يلتقط ملخصاً كاملاً لسياق الحادثة للحفظ والتدميج في تقارير ANR والانهيارات.
     */
    fun dumpContextSnapshot(context: Context? = null): String {
        val sb = StringBuilder()
        sb.appendLine("── سياق الحادثة التشغيلي (Incident Context) ──")
        sb.appendLine("• الشاشة الحالية: $currentScreen")
        sb.appendLine("• الشاشة السابقة: $previousScreen")
        sb.appendLine("• آخر عملية منفذة: $lastAction (منذ ${(System.currentTimeMillis() - lastActionTimeMs) / 1000}s)")
        sb.appendLine("• مدة تشغيل التطبيق (Uptime): ${processUptimeSec()} ثانية")
        sb.appendLine("• العمليات النشطة: شبكة=${activeNetworkRequests.get()} | قواعد بيانات=${activeDbOperations.get()} | مهام خلفية=${activeBackgroundTasks.get()}")

        if (context != null) {
            runCatching {
                val runtime = Runtime.getRuntime()
                val usedRamMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                val maxRamMb = runtime.maxMemory() / (1024 * 1024)

                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val mi = ActivityManager.MemoryInfo()
                am?.getMemoryInfo(mi)

                val sysFreeMb = (mi?.availMem ?: 0L) / (1024 * 1024)
                val isLowMem = mi?.lowMemory == true

                val running = ActivityManager.RunningAppProcessInfo()
                ActivityManager.getMyMemoryState(running)
                val isForeground = running.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND

                sb.appendLine("• حالة الذاكرة (App Heap): ${usedRamMb}MB مستخدمة / ${maxRamMb}MB الحد الأقصى")
                sb.appendLine("• ذاكرة النظام الحرة: ${sysFreeMb}MB (Low Memory Flag: $isLowMem)")
                sb.appendLine("• وضع التطبيق: ${if (isForeground) "في المقدمة (Foreground 🟢)" else "في الخلفية (Background 🌙)"}")
            }
        }
        return sb.toString().trimEnd()
    }

    /** سجل الانهيارات المحلي. */
    fun reportNonFatal(context: Context, throwable: Throwable, contextInfo: String = "") {
        runCatching {
            QabasCrashGuard.record(context, Thread.currentThread().name, throwable, "nonfatal:$contextInfo")
        }
    }

    /** صحة الإصدار الحالي: انهيارات / إجمالي إقلاعات. */
    fun releaseHealth(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val launches = prefs.getInt("app_launch_count", 0)
        val dir = java.io.File(context.filesDir, "crash_logs")
        val crashes = dir.listFiles()?.count { it.name.startsWith("crash_") } ?: 0
        return crashes to launches
    }
}
