package com.qabas.app

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 🛰️ محرك التشخيص الدقيق الشامل (Precision Diagnostics Engine)
 * الركيزة الأساسية لعدسة الكاميرا الفضائية:
 * يرصد ويجمع ويحلل بدقة بكسلية وبرمجية حالة النظام والواجهة وموارد الجهاز
 * لتزويد المطور والمستخدم بتشخيص فوري مكتمل الأركان بنسبة 100%.
 */
object PrecisionDiagnosticEngine {

    data class DiagnosticTelemetry(
        val timestamp: Long = System.currentTimeMillis(),
        val screenName: String,
        val widthDp: Int,
        val heightDp: Int,
        val densityDpi: Int,
        val fontScale: Float,
        val windowSizeClass: String,
        val orientation: String,
        val usedRamMb: Long,
        val maxRamMb: Long,
        val freeInternalStorageMb: Long,
        val isLowMemory: Boolean,
        val networkType: String,
        val isNetworkConnected: Boolean,
        val isAudioPlaying: Boolean,
        val activeAudioTrack: String?,
        val recentBreadcrumbs: List<String>,
        val cpuAbi: String,
        val deviceModel: String,
        val androidSdk: Int,
        val appVersion: String,
        val detectedProbableIssue: ProbableIssue
    )

    enum class ProbableIssue(val code: String, val title: String, val reason: String, val iconEmoji: String) {
        UI_OVERFLOW(
            "UI_OVERFLOW",
            "تداخل عناصر واجهة 📐",
            "تم رصد خط نظام كبير (FontScale > 1.15) أو شاشة ضيقة قد تسبب تدافع العناصر.",
            "📐"
        ),
        MEMORY_PRESSURE(
            "MEMORY_PRESSURE",
            "ضغط ذاكرة مرتفع ⚠️",
            "استهلاك الذاكرة يقترب من الحد الأقصى مع مؤشر انخفاض الموارد.",
            "🧠"
        ),
        NETWORK_UNAVAILABLE(
            "NETWORK_OFFLINE",
            "انقطاع أو ضعف الشبكة 🌐",
            "لا يوجد اتصال إنترنت نشط حالياً.",
            "🌐"
        ),
        AUDIO_STREAM_ISSUE(
            "AUDIO_ISSUE",
            "تعثر مشغل الصوت 🔊",
            "تم تسجيل مسار صوتي بدون تشغيل نشط أو انقطاع في التدفق.",
            "🔊"
        ),
        GENERAL_BUG(
            "BUG",
            "خلل برمجي عام 🐞",
            "رصد سلوك غير متوقع أو استثناء في الواجهة.",
            "🐞"
        )
    }

    /**
     * جمع البصمة التشخيصية المباشرة من سياق الشاشة الحالي
     */
    fun harvestTelemetry(
        context: Context,
        screenName: String,
        widthDp: Float,
        heightDp: Float
    ): DiagnosticTelemetry {
        val config = context.resources.configuration
        val fontScale = config.fontScale
        val densityDpi = (context.resources.displayMetrics.density * 160).toInt()
        val orientation = if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) "أفقي (Landscape)" else "عمودي (Portrait)"

        val windowSizeClass = when {
            widthDp < 600 -> "Compact (هاتف قياسي)"
            widthDp < 840 -> "Medium (شاشة متوسطة / مطوي)"
            else -> "Expanded (تابلت / شاشة واسعة)"
        }

        // الذاكرة
        val runtime = Runtime.getRuntime()
        val usedRamMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxRamMb = runtime.maxMemory() / (1024 * 1024)
        val memInfo = ActivityManager.MemoryInfo()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.getMemoryInfo(memInfo)
        val isLowMemory = memInfo.lowMemory

        // التخزين الداخلي
        val freeInternalStorageMb = runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)
        }.getOrDefault(0L)

        // الشبكة
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = cm?.activeNetwork
        val caps = activeNet?.let { cm.getNetworkCapabilities(it) }
        val isNetworkConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val networkType = when {
            caps == null -> "غير متصل (Offline)"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "واي فاي (Wi-Fi 📶)"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "بيانات الهاتف (4G/5G 📱)"
            else -> "متصل (أخرى)"
        }

        // حالة الصوت
        val isAudioPlaying = runCatching { AudioPlaybackManager.isPlaying.value }.getOrDefault(false)
        val activeAudioTrack = runCatching { AudioPlaybackManager.currentTrack.value?.title }.getOrNull()

        // فتات الخبز
        val recentBreadcrumbs = runCatching {
            CrashBreadcrumbs.dump().split("\n").filter { it.isNotBlank() }.takeLast(8)
        }.getOrDefault(emptyList())

        // معمارية المعالج والجهاز
        val cpuAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})"

        // الكشف التلقائي عن الخلل المرجح
        val probableIssue = when {
            fontScale >= 1.15f || (widthDp < 360 && fontScale > 1.0f) -> ProbableIssue.UI_OVERFLOW
            isLowMemory || (maxRamMb > 0 && usedRamMb > (maxRamMb * 0.88f)) -> ProbableIssue.MEMORY_PRESSURE
            !isNetworkConnected -> ProbableIssue.NETWORK_UNAVAILABLE
            !isAudioPlaying && !activeAudioTrack.isNullOrBlank() -> ProbableIssue.AUDIO_STREAM_ISSUE
            else -> ProbableIssue.GENERAL_BUG
        }

        return DiagnosticTelemetry(
            screenName = screenName,
            widthDp = widthDp.toInt(),
            heightDp = heightDp.toInt(),
            densityDpi = densityDpi,
            fontScale = fontScale,
            windowSizeClass = windowSizeClass,
            orientation = orientation,
            usedRamMb = usedRamMb,
            maxRamMb = maxRamMb,
            freeInternalStorageMb = freeInternalStorageMb,
            isLowMemory = isLowMemory,
            networkType = networkType,
            isNetworkConnected = isNetworkConnected,
            isAudioPlaying = isAudioPlaying,
            activeAudioTrack = activeAudioTrack,
            recentBreadcrumbs = recentBreadcrumbs,
            cpuAbi = cpuAbi,
            deviceModel = deviceModel,
            androidSdk = Build.VERSION.SDK_INT,
            appVersion = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            detectedProbableIssue = probableIssue
        )
    }

    /**
     * صياغة تقرير تشخيصي بصيغة Markdown تقنية جاهزة للمطور و GitHub Issues
     */
    fun formatDiagnosticMarkdown(telemetry: DiagnosticTelemetry, userNotes: String? = null): String {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.ENGLISH)
        return buildString {
            appendLine("### 🛰️ تقرير التشخيص الدقيق (Precision Diagnostic Signature)")
            appendLine("───────────────────────────────────────────────")
            appendLine("📍 **الشاشة الحالية:** `${telemetry.screenName}`")
            appendLine("⏱️ **وقت الرصد:** `${sdf.format(Date(telemetry.timestamp))}`")
            appendLine("📱 **الجهاز:** `${telemetry.deviceModel}` | المعمارية: `${telemetry.cpuAbi}`")
            appendLine("📦 **إصدار التطبيق:** `${telemetry.appVersion}`")
            appendLine()
            appendLine("📐 **مصفوفة أبعاد الواجهة (Layout Metrics):**")
            appendLine("- الأبعاد: `${telemetry.widthDp}×${telemetry.heightDp} dp` (${telemetry.densityDpi} DPI)")
            appendLine("- مقياس خط النظام (Font Scale): `${telemetry.fontScale}x` ${if (telemetry.fontScale > 1.15f) "⚠️ (مكبّر - سبب رئيسي لتداخل النصوص)" else "✓ (طبيعي)"}")
            appendLine("- تصنيف النافذة: `${telemetry.windowSizeClass}` (${telemetry.orientation})")
            appendLine()
            appendLine("🧠 **حالة النظام والذاكرة (System Health):**")
            appendLine("- الذاكرة المستهلكة: `${telemetry.usedRamMb} MB` من أصل `${telemetry.maxRamMb} MB` ${if (telemetry.isLowMemory) "⚠️ (Low Memory Warning)" else "✓"}")
            appendLine("- المساحة التخزينية الحرة: `${telemetry.freeInternalStorageMb} MB`")
            appendLine("- الشبكة: `${telemetry.networkType}`")
            appendLine()
            appendLine("🎵 **حالة الوسائط:**")
            appendLine("- المشغل: ${if (telemetry.isAudioPlaying) "يعمل بنشاط 🔊" else "متوقف ⏸️"}")
            if (!telemetry.activeAudioTrack.isNullOrBlank()) {
                appendLine("- المسار الحالي: `${telemetry.activeAudioTrack}`")
            }
            appendLine()
            appendLine("🔍 **التشخيص الآلي المرجح:** `${telemetry.detectedProbableIssue.title}`")
            appendLine("> ${telemetry.detectedProbableIssue.reason}")
            appendLine()
            if (!userNotes.isNullOrBlank()) {
                appendLine("📝 **ملاحظة إضافية:**")
                appendLine("> $userNotes")
                appendLine()
            }
            if (telemetry.recentBreadcrumbs.isNotEmpty()) {
                appendLine("🐾 **آخر أحداث التفاعل المسجلة (Recent Breadcrumbs):**")
                appendLine("```text")
                telemetry.recentBreadcrumbs.forEach { appendLine(it) }
                appendLine("```")
            }
        }
    }
}
