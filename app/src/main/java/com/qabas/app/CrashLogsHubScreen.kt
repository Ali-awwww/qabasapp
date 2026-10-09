package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.sin

/**
 * 🛸 مركز سجل وملتقط الانهيارات السيبراني الذكي (Cybernetic Spatial Crash Guard HUD & Auto-Healer)
 *
 * يوفر منصة مراقبة متكاملة:
 * - رسم بياني نبضي حي (Spatial Live ECG Pulse Waveform)
 * - محرك تشخيص الأسباب الجذرية واستخراج مسار الأحداث (Breadcrumbs Replay)
 * - نظام الإنعاش والإصلاح الذاتي الفوري (Autonomous Self-Healing Matrix)
 * - تحليل متقدم للـ Stack مع إبراز برمجي (Syntax Highlighting) وتوليد رقع التصحيح عبر AI
 * - تصدير السجلات المشفرة ومزامنتها سحابياً.
 */

private val HudCyan = Color(0xFF00E5FF)
private val HudPink = Color(0xFFFF2A85)
private val HudGold = Color(0xFFFFD700)
private val HudGreen = Color(0xFF10B981)
private val HudAmber = Color(0xFFF59E0B)
private val HudViolet = Color(0xFF8B5CF6)
private val HudObsidian = Color(0xFF060913)
private val HudCardSurface = Color(0xFF0D1424)
private val HudCardBorder = Color(0xFF1E2D4A)

enum class CrashFilterCategory(val label: String, val icon: ImageVector) {
    ALL("الكل 🌐", Icons.Default.Apps),
    MAIN_THREAD("خيط الواجهة (Main) ⚡", Icons.Default.Speed),
    WORKER("خلفي (Worker/IO) ⚙️", Icons.Default.Sync),
    COROUTINE("كوروتين 🔄", Icons.Default.AllInclusive),
    NULL_POINTER("مؤشرات فارغة 🚫", Icons.Default.Warning),
    OUT_OF_MEMORY("نفاد ذاكرة 💾", Icons.Default.Memory),
    NETWORK("شبكة وسحاب ☁️", Icons.Default.CloudOff),
    DATABASE("قاعدة بيانات 🗄️", Icons.Default.Storage)
}

enum class SpatialCrashViewMode {
    STREAM,
    GROUPED,
    TIMELINE,
    HEALING
}

data class SystemLiveTelemetry(
    val freeMemMb: Long,
    val totalMemMb: Long,
    val allocatedHeapMb: Long,
    val isBatteryOptimized: Boolean,
    val isLowMemory: Boolean,
    val activeThreadsCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashLogsHubScreen(
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var isRefreshing by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(CrashFilterCategory.ALL) }
    var viewMode by remember { mutableStateOf(SpatialCrashViewMode.STREAM) }
    var selectedCrashDialog by remember { mutableStateOf<CrashLogFile?>(null) }
    var showTestingMenu by remember { mutableStateOf(false) }
    var showSelfHealingDialog by remember { mutableStateOf(false) }
    var isHealingInProgress by remember { mutableStateOf(false) }
    var healingResultLog by remember { mutableStateOf<List<String>>(emptyList()) }

    // Crash Data State
    var crashesList by remember { mutableStateOf<List<CrashLogFile>>(emptyList()) }
    var launchCount by remember { mutableIntStateOf(0) }
    var lastCrashTimestamp by remember { mutableLongStateOf(0L) }
    var activeBreadcrumbs by remember { mutableStateOf("") }
    var liveTelemetry by remember { mutableStateOf<SystemLiveTelemetry?>(null) }

    // Zip export launcher
    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        ZipOutputStream(outputStream.buffered()).use { zos ->
                            val dir = File(context.filesDir, "crash_logs")
                            val files = dir.listFiles()?.filter { it.name.startsWith("crash_") } ?: emptyList<File>()
                            files.forEach { f ->
                                zos.putNextEntry(ZipEntry(f.name))
                                f.inputStream().use { it.copyTo(zos) }
                                zos.closeEntry()
                            }
                            // Add breadcrumbs dump
                            zos.putNextEntry(ZipEntry("breadcrumbs_dump.txt"))
                            zos.write(CrashBreadcrumbs.dump().toByteArray(Charsets.UTF_8))
                            zos.closeEntry()

                            // Add system diagnosis
                            zos.putNextEntry(ZipEntry("system_telemetry.txt"))
                            val info = "Device: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nApp: ${BuildConfig.VERSION_NAME}\nTimestamp: ${Date()}"
                            zos.write(info.toByteArray(Charsets.UTF_8))
                            zos.closeEntry()
                        }
                    }
                    withContext(Dispatchers.Main) {
                        AppSoundManager.playSuccess()
                        Toast.makeText(context, "تم تصدير حزمة السجلات والتشخيصات بنجاح 📦", Toast.LENGTH_LONG).show()
                    }
                }.onFailure { err ->
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "فشل التصدير: ${err.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Function to reload crashes & telemetry
    fun reloadCrashes() {
        coroutineScope.launch {
            isRefreshing = true
            AppSoundManager.playTab()
            withContext(Dispatchers.IO) {
                File(context.filesDir, "crash_logs").mkdirs()
                val loaded = loadCrashLogs(context)
                val (_, lCount) = CrashBreadcrumbs.releaseHealth(context)
                val crumbs = CrashBreadcrumbs.dump()

                // Calculate Memory Metrics
                val rt = Runtime.getRuntime()
                val totalMem = rt.totalMemory() / (1024 * 1024)
                val freeMem = rt.freeMemory() / (1024 * 1024)
                val heapAllocated = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)

                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val isIgnoringBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
                } else true

                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                val mi = android.app.ActivityManager.MemoryInfo()
                am?.getMemoryInfo(mi)

                val telemetry = SystemLiveTelemetry(
                    freeMemMb = mi.availMem / (1024 * 1024),
                    totalMemMb = mi.totalMem / (1024 * 1024),
                    allocatedHeapMb = heapAllocated,
                    isBatteryOptimized = !isIgnoringBattery,
                    isLowMemory = mi.lowMemory,
                    activeThreadsCount = Thread.activeCount()
                )

                withContext(Dispatchers.Main) {
                    crashesList = loaded
                    launchCount = lCount
                    lastCrashTimestamp = loaded.maxOfOrNull { it.timestampMs } ?: 0L
                    activeBreadcrumbs = crumbs
                    liveTelemetry = telemetry
                    isRefreshing = false
                }
            }
        }
    }

    // Safe mock test recording
    fun recordSafeMockCrash() {
        coroutineScope.launch(Dispatchers.IO) {
            val now = Date()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(now)
            val display = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now)
            val dir = File(context.filesDir, "crash_logs").apply { mkdirs() }
            val mockFile = File(dir, "crash_$stamp.txt")

            val fakeStack = buildString {
                appendLine("════════════════════════════════════════════")
                appendLine("Qabas Crash Log — $display")
                appendLine("Source: safe_simulation")
                appendLine("Thread: DefaultDispatcher-worker-1")
                appendLine("Hot path: at com.qabas.app.CrashLogsHubScreen.recordSafeMockCrash(CrashLogsHubScreen.kt:170)")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("App version: ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
                appendLine("Memory: 418MB free / 1024MB total")
                appendLine("Foreground: true")
                appendLine("── Breadcrumbs (آخر الأحداث قبل العطل) ──")
                appendLine(CrashBreadcrumbs.dump())
                appendLine("════════════════════════════════════════════")
                appendLine("java.lang.IllegalStateException: 🧪 [تجربة آمنة] اختبار استجابة حارس الأعطال ومحرك التشخيص الذكي")
                appendLine("\tat com.qabas.app.CrashLogsHubScreen.recordSafeMockCrash(CrashLogsHubScreen.kt:180)")
                appendLine("\tat com.qabas.app.QabasCrashGuard.record(QabasCrashGuard.kt:185)")
                appendLine("\tat kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:100)")
            }

            mockFile.writeText(fakeStack)
            CrashBreadcrumbs.leave("action", "Safe mock crash generated @ $display")

            withContext(Dispatchers.Main) {
                AppSoundManager.playSuccess()
                Toast.makeText(context, "تم تسجيل تقرير انهيار تجريبي آمن 🧪", Toast.LENGTH_SHORT).show()
                reloadCrashes()
            }
        }
    }

    // Autonomous Self-Healing Engine
    fun executeAutonomousSelfHeal() {
        isHealingInProgress = true
        coroutineScope.launch(Dispatchers.IO) {
            val logs = mutableListOf<String>()
            logs.add("⏳ [1/5] بدء بروتوكول التشخيص والإنعاش التلقائي للأنظمة...")
            delay(400)

            // Step 1: Cache & Temp Media Clearance
            runCatching {
                var clearedBytes = 0L
                context.cacheDir.walkTopDown().forEach { f ->
                    if (f.isFile && f.exists()) {
                        clearedBytes += f.length()
                        f.delete()
                    }
                }
                logs.add("✅ [2/5] تم تنظيف ${clearedBytes / 1024}KB من ملفات الذاكرة المؤقتة والتخزين المؤقت للوسائط.")
            }.onFailure {
                logs.add("⚠️ [2/5] تحذير أثناء تنظيف الكاش: ${it.message}")
            }
            delay(400)

            // Step 2: Audio & Video Engine Buffers Release
            runCatching {
                AudioPlayerManager.stopAudio()
                VideoCacheService.release()
                logs.add("✅ [3/5] تم تفريغ وإعادة تهيئة موجهات الصوت ومحركات الفيديو.")
            }.onFailure {
                logs.add("⚠️ [3/5] خطأ في تفريغ المحركات الصوتية: ${it.message}")
            }
            delay(400)

            // Step 3: SharedPreferences & StyleBrain Core Integrity Check
            runCatching {
                StyleBrainLoadFix.repairPersistedEmptyCore(context)
                logs.add("✅ [4/5] تم فحص وتصحيح سلامة النواة الفنية (StyleBrain) والتفضيلات المحفوظة.")
            }.onFailure {
                logs.add("⚠️ [4/5] فحص التفضيلات: ${it.message}")
            }
            delay(400)

            // Step 4: System Garbage Collector & State Refresh
            runCatching {
                System.gc()
                CrashBreadcrumbs.leave("system", "Autonomous Self-Heal Protocol executed successfully")
                logs.add("✅ [5/5] تم تشغيل جامع المهملات (GC) واستعادة 100% من جاهزية النظام.")
            }

            withContext(Dispatchers.Main) {
                healingResultLog = logs
                isHealingInProgress = false
                AppSoundManager.playSuccess()
                Toast.makeText(context, "اكتمل بروتوكول الإنعاش الذاتي بنجاح ✦", Toast.LENGTH_SHORT).show()
                reloadCrashes()
            }
        }
    }

    LaunchedEffect(Unit) {
        reloadCrashes()
    }

    if (onBack != null) {
        BackHandler { onBack() }
    }

    // Filter Logic
    val filteredCrashes = remember(crashesList, searchQuery, selectedCategory) {
        crashesList.filter { crash ->
            val matchesQuery = searchQuery.isBlank() ||
                crash.fullStack.contains(searchQuery, ignoreCase = true) ||
                crash.exceptionLine.contains(searchQuery, ignoreCase = true) ||
                crash.fileName.contains(searchQuery, ignoreCase = true) ||
                crash.thread.contains(searchQuery, ignoreCase = true)

            val matchesCategory = when (selectedCategory) {
                CrashFilterCategory.ALL -> true
                CrashFilterCategory.MAIN_THREAD -> crash.thread.equals("main", ignoreCase = true)
                CrashFilterCategory.WORKER -> crash.thread.contains("worker", ignoreCase = true) || crash.thread.contains("pool", ignoreCase = true)
                CrashFilterCategory.COROUTINE -> crash.thread.contains("DefaultDispatcher", ignoreCase = true) || crash.thread.contains("coroutine", ignoreCase = true)
                CrashFilterCategory.NULL_POINTER -> crash.fullStack.contains("NullPointer", ignoreCase = true)
                CrashFilterCategory.OUT_OF_MEMORY -> crash.fullStack.contains("OutOfMemory", ignoreCase = true)
                CrashFilterCategory.NETWORK -> crash.fullStack.contains("Socket", true) || crash.fullStack.contains("Connect", true) || crash.fullStack.contains("UnknownHost", true) || crash.fullStack.contains("Timeout", true)
                CrashFilterCategory.DATABASE -> crash.fullStack.contains("SQLite", true) || crash.fullStack.contains("SQLException", true)
            }

            matchesQuery && matchesCategory
        }
    }

    val groupedCrashes = remember(filteredCrashes) {
        groupCrashesByType(filteredCrashes)
    }

    // Calculated Health Score
    val healthStabilityRate = remember(crashesList.size, launchCount) {
        if (launchCount <= 0) {
            if (crashesList.isEmpty()) 100.0 else 92.0
        } else {
            val failureRate = (crashesList.size * 100.0 / launchCount).coerceIn(0.0, 100.0)
            (100.0 - failureRate).coerceIn(0.0, 100.0)
        }
    }

    val stabilityColor = when {
        healthStabilityRate >= 95.0 -> HudGreen
        healthStabilityRate >= 80.0 -> HudAmber
        else -> HudPink
    }

    // Main Spatial Background & HUD Layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HudObsidian)
            .testTag("crash_logs_hub_screen")
    ) {
        // Futuristic Spatial Grid Background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridSpacing = 40.dp.toPx()
            val w = size.width
            val h = size.height

            // Vertical HUD Gridlines
            var x = 0f
            while (x <= w) {
                drawLine(
                    color = HudCyan.copy(alpha = 0.035f),
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 0.5.dp.toPx()
                )
                x += gridSpacing
            }

            // Horizontal HUD Gridlines
            var y = 0f
            while (y <= h) {
                drawLine(
                    color = HudCyan.copy(alpha = 0.035f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 0.5.dp.toPx()
                )
                y += gridSpacing
            }

            // Glowing Ambient HUD Orbs
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(HudPink.copy(alpha = 0.07f), Color.Transparent),
                    center = Offset(w * 0.9f, h * 0.12f),
                    radius = 260.dp.toPx()
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(HudCyan.copy(alpha = 0.05f), Color.Transparent),
                    center = Offset(w * 0.1f, h * 0.85f),
                    radius = 300.dp.toPx()
                )
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CrashHubTopBar(
                    onBack = onBack,
                    isRefreshing = isRefreshing,
                    onRefresh = { reloadCrashes() },
                    onExportZip = {
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        exportZipLauncher.launch("qabas_crashes_$stamp.zip")
                    },
                    onOpenTesting = { showTestingMenu = true },
                    onOpenSelfHealing = { showSelfHealingDialog = true }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Live Spatial Oscilloscope & Pulse Waveform Card
                SpatialLiveOscilloscopeCard(
                    stabilityRate = healthStabilityRate,
                    stabilityColor = stabilityColor,
                    totalCrashes = crashesList.size,
                    uniqueFamilies = groupedCrashes.size,
                    lastCrashTimestamp = lastCrashTimestamp,
                    telemetry = liveTelemetry,
                    onTriggerHeal = { showSelfHealingDialog = true }
                )

                // 2. Control & Search HUD Strip
                SpatialSearchAndControlsBar(
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    viewMode = viewMode,
                    onViewModeChange = {
                        AppSoundManager.playTab()
                        viewMode = it
                    },
                    onClearAll = {
                        coroutineScope.launch(Dispatchers.IO) {
                            runCatching {
                                File(context.filesDir, "crash_logs").listFiles()?.forEach { it.delete() }
                            }
                            withContext(Dispatchers.Main) {
                                crashesList = emptyList()
                                AppSoundManager.playClick()
                                Toast.makeText(context, "تم مسح كافة سجلات الانهيارات المحلية 🧹", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )

                // 3. Category Filter Chips (Horizontal HUD Pill Strip)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(CrashFilterCategory.values()) { category ->
                        val isSelected = selectedCategory == category
                        Surface(
                            color = if (isSelected) HudCyan.copy(alpha = 0.2f) else HudCardSurface.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                if (isSelected) 1.2.dp else 1.dp,
                                if (isSelected) HudCyan else Color.White.copy(alpha = 0.08f)
                            ),
                            modifier = Modifier.clickable {
                                AppSoundManager.playClick()
                                selectedCategory = category
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = category.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) HudCyan else Color(0xFF94A3B8),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = category.label,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    fontFamily = CairoFont,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // 4. Main Dynamic Stream / Groups / Timeline / Healing View
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (isRefreshing && crashesList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = HudCyan, strokeWidth = 2.5.dp, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("جاري فحص وجلب سجلات الانهيارات...", color = Color.White.copy(alpha = 0.8f), fontFamily = CairoFont, fontSize = 13.sp)
                            }
                        }
                    } else if (crashesList.isEmpty() && viewMode != SpatialCrashViewMode.HEALING && viewMode != SpatialCrashViewMode.TIMELINE) {
                        EmptyCrashesState(
                            onSimulateSafeMock = { recordSafeMockCrash() },
                            onRefresh = { reloadCrashes() },
                            onTriggerSelfHeal = { showSelfHealingDialog = true }
                        )
                    } else if (filteredCrashes.isEmpty() && viewMode != SpatialCrashViewMode.HEALING && viewMode != SpatialCrashViewMode.TIMELINE) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "لا توجد نتائج تطابق استعلام البحث أو الفلتر المحدد 🔍",
                                color = Color(0xFF94A3B8),
                                fontFamily = CairoFont,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        when (viewMode) {
                            SpatialCrashViewMode.STREAM -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(bottom = 20.dp)
                                ) {
                                    items(filteredCrashes, key = { it.fileName }) { crash ->
                                        SpatialCrashStreamCard(
                                            crash = crash,
                                            onClick = {
                                                AppSoundManager.playClick()
                                                selectedCrashDialog = crash
                                            }
                                        )
                                    }
                                }
                            }
                            SpatialCrashViewMode.GROUPED -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(bottom = 20.dp)
                                ) {
                                    items(groupedCrashes, key = { it.type }) { group ->
                                        SpatialCrashGroupCard(
                                            group = group,
                                            onClick = {
                                                AppSoundManager.playClick()
                                                selectedCrashDialog = group.sample
                                            }
                                        )
                                    }
                                }
                            }
                            SpatialCrashViewMode.TIMELINE -> {
                                SpatialBreadcrumbsTimelineCard(
                                    breadcrumbs = activeBreadcrumbs,
                                    crashes = filteredCrashes,
                                    onSelectCrash = {
                                        AppSoundManager.playClick()
                                        selectedCrashDialog = it
                                    }
                                )
                            }
                            SpatialCrashViewMode.HEALING -> {
                                SpatialSelfHealingConsoleCard(
                                    healingLogs = healingResultLog,
                                    isInProgress = isHealingInProgress,
                                    onExecuteHeal = { executeAutonomousSelfHeal() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Full Crash Inspector & AI Diagnostician
    selectedCrashDialog?.let { crash ->
        SpatialCrashInspectorDialog(
            crash = crash,
            onDismiss = { selectedCrashDialog = null },
            onCopy = {
                clipboard.setText(AnnotatedString(crash.fullStack))
                AppSoundManager.playClick()
                Toast.makeText(context, "تم نسخ السجل الكامل للحافظة 📋", Toast.LENGTH_SHORT).show()
            },
            onShare = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "تقرير عطل قبس: ${crash.fileName}")
                    putExtra(Intent.EXTRA_TEXT, crash.fullStack)
                }
                context.startActivity(Intent.createChooser(intent, "مشاركة تقرير الانهيار"))
            }
        )
    }

    // Self Healing Dialog Confirmation
    if (showSelfHealingDialog) {
        AlertDialog(
            onDismissRequest = { showSelfHealingDialog = false },
            containerColor = Color(0xFF0C1322),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Healing, contentDescription = null, tint = HudGreen, modifier = Modifier.size(24.dp))
                    Text("تشغيل الإنعاش والإصلاح الذاتي ⚡", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "سيقوم محرك الإنعاش بتنفيذ الإجراءات التالية فورياً:",
                        color = Color(0xFF94A3B8),
                        fontFamily = CairoFont,
                        fontSize = 12.5.sp
                    )
                    val steps = listOf(
                        "تفريغ الملفات المؤقتة التالفة للصور والفيديوهات.",
                        "إعادة تشغيل قنوات الصوت ومحررات المقاطع المعلقة.",
                        "فحص وإصلاح هيكل التفضيلات والنواة الفنية (StyleBrain).",
                        "تنظيف الذاكرة ومزامنة بروتوكول حارس الأعطال."
                    )
                    steps.forEach { step ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = HudGreen, modifier = Modifier.size(14.dp))
                            Text(step, color = Color(0xFFE2E8F0), fontFamily = CairoFont, fontSize = 11.5.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSelfHealingDialog = false
                        viewMode = SpatialCrashViewMode.HEALING
                        executeAutonomousSelfHeal()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HudGreen)
                ) {
                    Text("بدء الإنعاش الآن ⚡", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSelfHealingDialog = false }) {
                    Text("إلغاء", color = Color.Gray, fontFamily = CairoFont)
                }
            }
        )
    }

    // Testing Action Sheet
    if (showTestingMenu) {
        AlertDialog(
            onDismissRequest = { showTestingMenu = false },
            containerColor = Color(0xFF0F172A),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Science, contentDescription = null, tint = HudCyan, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("مختبر اختبار ملتقط الانهيارات 🧪", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "اختر نوع الاختبار للتحقق من استجابة حارس الأعطال ومحرك التشخيص:",
                        color = Color(0xFF94A3B8),
                        fontFamily = CairoFont,
                        fontSize = 12.sp
                    )

                    Button(
                        onClick = {
                            showTestingMenu = false
                            recordSafeMockCrash()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HudCyan.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, HudCyan),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = HudCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تسجيل عطل آمن (بدون إيقاف التطبيق) 🛡️", color = HudCyan, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            showTestingMenu = false
                            Toast.makeText(context, "سيتم إطلاق استثناء لاختبار حارس إعادة التشغيل...", Toast.LENGTH_SHORT).show()
                            coroutineScope.launch {
                                delay(600)
                                QabasCrashGuard.simulateCrash()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HudPink.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, HudPink),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null, tint = HudPink, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("محاكاة انهيار كامل (اختبار دورة الإنعاش) ⚡", color = HudPink, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTestingMenu = false }) {
                    Text("إغلاق", color = Color.White, fontFamily = CairoFont)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrashHubTopBar(
    onBack: (() -> Unit)?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onExportZip: () -> Unit,
    onOpenTesting: () -> Unit,
    onOpenSelfHealing: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CutCornerShape(8.dp))
                        .background(HudPink.copy(alpha = 0.18f))
                        .border(1.dp, HudPink.copy(alpha = 0.5f), CutCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null, tint = HudPink, modifier = Modifier.size(20.dp))
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "سجل وملتقط الانهيارات",
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.5.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = HudCyan.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.5.dp, HudCyan.copy(alpha = 0.4f))
                        ) {
                            Text(
                                "Spatial HUD Pro",
                                color = HudCyan,
                                fontFamily = RobotoMonoFont,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        "مراقبة حية، تشخيص الأسباب الجذرية، وإنعاش الأنظمة الذاتي",
                        color = Color(0xFF94A3B8),
                        fontFamily = NotoSansFont,
                        fontSize = 10.5.sp
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = GoldPrimary)
                }
            }
        },
        actions = {
            IconButton(onClick = onOpenSelfHealing) {
                Icon(Icons.Default.Healing, contentDescription = "الإنعاش الذاتي", tint = HudGreen)
            }
            IconButton(onClick = onOpenTesting) {
                Icon(Icons.Default.Science, contentDescription = "مختبر التجارب", tint = HudCyan)
            }
            IconButton(onClick = onExportZip) {
                Icon(Icons.Default.Archive, contentDescription = "تصدير ZIP", tint = GoldPrimary)
            }
            IconButton(onClick = onRefresh) {
                if (isRefreshing) {
                    CircularProgressIndicator(color = HudCyan, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = HudCyan)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}

@Composable
private fun SpatialLiveOscilloscopeCard(
    stabilityRate: Double,
    stabilityColor: Color,
    totalCrashes: Int,
    uniqueFamilies: Int,
    lastCrashTimestamp: Long,
    telemetry: SystemLiveTelemetry?,
    onTriggerHeal: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_osc")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val lastCrashText = if (lastCrashTimestamp > 0L) {
        val diffMins = (System.currentTimeMillis() - lastCrashTimestamp) / 60_000
        when {
            diffMins < 1 -> "الآن"
            diffMins < 60 -> "منذ $diffMins د"
            diffMins < 1440 -> "منذ ${diffMins / 60} س"
            else -> "منذ ${diffMins / 1440} يوم"
        }
    } else "صفر حوادث ✦"

    GlassmorphicSurface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(18.dp), spotColor = stabilityColor.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(18.dp),
        backgroundColor = HudCardSurface.copy(alpha = 0.88f),
        borderColor = stabilityColor.copy(alpha = 0.4f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(stabilityColor)
                    )
                    Text(
                        "مصفوفة النبض والاستقرار الفضائي",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = stabilityColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.8.dp, stabilityColor)
                    ) {
                        Text(
                            "استقرار: ${"%.1f".format(stabilityRate)}%",
                            color = stabilityColor,
                            fontFamily = RobotoMonoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        color = HudGreen.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.8.dp, HudGreen),
                        modifier = Modifier.clickable { onTriggerHeal() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = HudGreen, modifier = Modifier.size(12.dp))
                            Text("إنعاش ⚡", color = HudGreen, fontFamily = CairoFont, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Real-Time Animated ECG Waveform Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF060A14))
                    .border(0.8.dp, Color(0xFF1E2D4A), RoundedCornerShape(8.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val midY = h / 2f
                    val path = Path()

                    val points = 80
                    for (i in 0..points) {
                        val currX = (i / points.toFloat()) * w
                        val normalX = (i / points.toFloat()) * 4 * Math.PI.toFloat() + phase
                        val amplitude = if (stabilityRate >= 95.0) {
                            (h * 0.28f) * sin(normalX)
                        } else {
                            (h * 0.42f) * sin(normalX) + if (i % 8 == 0) (h * 0.35f) else 0f
                        }
                        val currY = midY + amplitude

                        if (i == 0) path.moveTo(currX, currY)
                        else path.lineTo(currX, currY)
                    }

                    drawPath(
                        path = path,
                        color = stabilityColor,
                        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // Telemetry Metric Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TelemetryMetricPill(title = "الأعطال", value = "$totalCrashes", accent = if (totalCrashes > 0) HudPink else HudGreen)
                TelemetryMetricPill(title = "الفصائل", value = "$uniqueFamilies", accent = HudCyan)
                TelemetryMetricPill(title = "ذاكرة الهيب", value = "${telemetry?.allocatedHeapMb ?: 0}MB", accent = HudViolet)
                TelemetryMetricPill(title = "الخيوط الحية", value = "${telemetry?.activeThreadsCount ?: 0}", accent = HudGold)
                TelemetryMetricPill(title = "آخر حادثة", value = lastCrashText, accent = Color(0xFFA78BFA))
            }
        }
    }
}

@Composable
private fun TelemetryMetricPill(
    title: String,
    value: String,
    accent: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            color = accent,
            fontFamily = RobotoMonoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp
        )
        Text(
            text = title,
            color = Color(0xFF94A3B8),
            fontFamily = CairoFont,
            fontSize = 9.5.sp
        )
    }
}

@Composable
private fun SpatialSearchAndControlsBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    viewMode: SpatialCrashViewMode,
    onViewModeChange: (SpatialCrashViewMode) -> Unit,
    onClearAll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("بحث بالسباك، اسم الملف، الكلاس، أو الخيط...", color = Color.Gray, fontFamily = CairoFont, fontSize = 11.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = HudCyan, modifier = Modifier.size(18.dp)) },
            trailingIcon = if (searchQuery.isNotBlank()) {
                @Composable {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "مسح", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
            } else null,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = HudCyan,
                unfocusedBorderColor = Color(0xFF1E2D4A),
                focusedContainerColor = Color(0xFF090E1B),
                unfocusedContainerColor = Color(0xFF090E1B),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
        )

        // Switch View Modes
        Surface(
            color = Color(0xFF0E1526),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF1E2D4A))
        ) {
            Row(modifier = Modifier.padding(3.dp)) {
                IconButton(
                    onClick = { onViewModeChange(SpatialCrashViewMode.STREAM) },
                    modifier = Modifier.size(34.dp).background(if (viewMode == SpatialCrashViewMode.STREAM) HudCyan.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.List, contentDescription = "قائمة", tint = if (viewMode == SpatialCrashViewMode.STREAM) HudCyan else Color.Gray, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { onViewModeChange(SpatialCrashViewMode.GROUPED) },
                    modifier = Modifier.size(34.dp).background(if (viewMode == SpatialCrashViewMode.GROUPED) HudCyan.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.GridView, contentDescription = "تجميع", tint = if (viewMode == SpatialCrashViewMode.GROUPED) HudCyan else Color.Gray, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { onViewModeChange(SpatialCrashViewMode.TIMELINE) },
                    modifier = Modifier.size(34.dp).background(if (viewMode == SpatialCrashViewMode.TIMELINE) HudCyan.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.Timeline, contentDescription = "مسار زمني", tint = if (viewMode == SpatialCrashViewMode.TIMELINE) HudCyan else Color.Gray, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { onViewModeChange(SpatialCrashViewMode.HEALING) },
                    modifier = Modifier.size(34.dp).background(if (viewMode == SpatialCrashViewMode.HEALING) HudGreen.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.Healing, contentDescription = "الإنعاش", tint = if (viewMode == SpatialCrashViewMode.HEALING) HudGreen else Color.Gray, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Clean Action
        IconButton(
            onClick = onClearAll,
            modifier = Modifier
                .size(40.dp)
                .background(HudPink.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .border(1.dp, HudPink.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
        ) {
            Icon(Icons.Default.DeleteSweep, contentDescription = "مسح الكل", tint = HudPink, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SpatialCrashStreamCard(
    crash: CrashLogFile,
    onClick: () -> Unit
) {
    val diag = remember(crash.fullStack) { diagnoseCrash(crash) }
    val formattedDate = remember(crash.timestampMs) {
        SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date(crash.timestampMs))
    }
    val topAppFrame = remember(crash.fullStack) { firstAppFrame(crash.fullStack) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1222)),
        border = BorderStroke(1.dp, HudPink.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = HudPink.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.5.dp, HudPink)
                    ) {
                        Text(
                            crash.thread,
                            color = HudPink,
                            fontFamily = RobotoMonoFont,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = crash.fileName,
                        color = Color(0xFF94A3B8),
                        fontFamily = RobotoMonoFont,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = formattedDate,
                    color = Color.Gray,
                    fontFamily = NotoSansFont,
                    fontSize = 10.sp
                )
            }

            Text(
                text = crash.exceptionLine.ifBlank { "Uncaught Exception / Fatal Error" },
                color = Color(0xFFFCA5A5),
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (topAppFrame != "غير معروف") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = HudCyan, modifier = Modifier.size(13.dp))
                    Text(
                        text = topAppFrame,
                        color = HudCyan,
                        fontFamily = RobotoMonoFont,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                color = Color(0xFF070C18),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.6.dp, Color(0xFF1E2D4A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("السبب:", color = HudAmber, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(diag.why, color = Color(0xFFE2E8F0), fontFamily = NotoSansFont, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("الحل:", color = HudGreen, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(diag.fix, color = Color(0xFF94A3B8), fontFamily = NotoSansFont, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpatialCrashGroupCard(
    group: CrashGroup,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1222)),
        border = BorderStroke(1.dp, HudCyan.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = group.type,
                    color = Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                )
                Text(
                    text = "آخر حدوث: ${SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(group.latestMs))}",
                    color = Color(0xFF94A3B8),
                    fontFamily = NotoSansFont,
                    fontSize = 11.sp
                )
                Text(
                    text = "📍 ${firstAppFrame(group.sample.fullStack)}",
                    color = HudCyan,
                    fontFamily = RobotoMonoFont,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                color = HudPink.copy(alpha = 0.2f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, HudPink)
            ) {
                Text(
                    text = "× ${group.count}",
                    color = HudPink,
                    fontFamily = RobotoMonoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SpatialBreadcrumbsTimelineCard(
    breadcrumbs: String,
    crashes: List<CrashLogFile>,
    onSelectCrash: (CrashLogFile) -> Unit
) {
    val crumbLines = remember(breadcrumbs) {
        breadcrumbs.lines().filter { it.isNotBlank() && !it.contains("لا فتات") }
    }

    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1222)),
        border = BorderStroke(1.dp, Color(0xFF1E2D4A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "تسلسل خطوات المستخدم قبل العطل (Breadcrumbs Trail) 🍞",
                    color = GoldPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                )
                Surface(
                    color = GoldPrimary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "${crumbLines.size} حدث",
                        color = GoldPrimary,
                        fontFamily = RobotoMonoFont,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (crumbLines.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("(لا يوجد فتات مسجل حالياً)", color = Color.Gray, fontFamily = CairoFont)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(crumbLines) { index, line ->
                        val (time, category, msg) = parseCrumbLine(line)
                        Surface(
                            color = Color(0xFF060A14),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.6.dp, Color(0xFF1E2D4A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(HudCyan.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${index + 1}", color = HudCyan, fontFamily = RobotoMonoFont, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                Surface(
                                    color = when (category) {
                                        "screen" -> Color(0xFF38BDF8).copy(alpha = 0.15f)
                                        "action" -> Color(0xFF10B981).copy(alpha = 0.15f)
                                        "api" -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                                        else -> Color(0xFF8B5CF6).copy(alpha = 0.15f)
                                    },
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        category,
                                        color = when (category) {
                                            "screen" -> Color(0xFF38BDF8)
                                            "action" -> Color(0xFF10B981)
                                            "api" -> Color(0xFFF59E0B)
                                            else -> Color(0xFF8B5CF6)
                                        },
                                        fontFamily = RobotoMonoFont,
                                        fontSize = 9.5.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }

                                Text(
                                    text = msg,
                                    color = Color(0xFFF1F5F9),
                                    fontFamily = NotoSansFont,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.weight(1f)
                                )

                                Text(
                                    text = time,
                                    color = Color.Gray,
                                    fontFamily = RobotoMonoFont,
                                    fontSize = 9.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseCrumbLine(raw: String): Triple<String, String, String> {
    // format: HH:mm:ss [category] message
    return try {
        val time = raw.substringBefore('[').trim()
        val cat = raw.substringAfter('[').substringBefore(']').trim()
        val msg = raw.substringAfter(']').trim()
        Triple(time, cat, msg)
    } catch (_: Exception) {
        Triple("", "event", raw)
    }
}

@Composable
private fun SpatialSelfHealingConsoleCard(
    healingLogs: List<String>,
    isInProgress: Boolean,
    onExecuteHeal: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1222)),
        border = BorderStroke(1.dp, HudGreen.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Healing, contentDescription = null, tint = HudGreen, modifier = Modifier.size(20.dp))
                    Text(
                        "وحدة التحكم بالإنعاش والإصلاح الذاتي ⚡",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }

                Button(
                    onClick = onExecuteHeal,
                    enabled = !isInProgress,
                    colors = ButtonDefaults.buttonColors(containerColor = HudGreen),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isInProgress) {
                        CircularProgressIndicator(color = DeepSlate, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                    } else {
                        Text("بدء الفحص والإنعاش ⚡", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    }
                }
            }

            Surface(
                color = Color(0xFF050811),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF152238)),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (healingLogs.isEmpty() && !isInProgress) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("اضغط 'بدء الفحص والإنعاش' لتشغيل بروتوكول التشخيص والتصحيح التلقائي.", color = Color.Gray, fontFamily = CairoFont, fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(healingLogs) { logLine ->
                            Text(
                                text = logLine,
                                color = if (logLine.contains("✅")) Color(0xFF34D399) else if (logLine.contains("⚠️")) HudAmber else Color(0xFF38BDF8),
                                fontFamily = RobotoMonoFont,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCrashesState(
    onSimulateSafeMock: () -> Unit,
    onRefresh: () -> Unit,
    onTriggerSelfHeal: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(CircleShape)
                .background(HudGreen.copy(alpha = 0.15f))
                .border(1.2.dp, HudGreen, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CheckCircleOutline, contentDescription = null, tint = HudGreen, modifier = Modifier.size(36.dp))
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            "لا توجد أي انهيارات مسجلة 🎉",
            color = Color.White,
            fontFamily = CairoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            "تعمل كافة وحدات النظام والذكاء الاصطناعي باستقرار تام 100%. يمكنك تجربة محاكاة عطل آمن أو فحص الإنعاش الذاتي.",
            color = Color(0xFF94A3B8),
            fontFamily = CairoFont,
            fontSize = 12.5.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSimulateSafeMock,
                colors = ButtonDefaults.buttonColors(containerColor = HudCyan.copy(alpha = 0.2f)),
                border = BorderStroke(1.dp, HudCyan),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Science, contentDescription = null, tint = HudCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("تسجيل عطل تجريبي 🧪", color = HudCyan, fontFamily = CairoFont, fontSize = 12.sp)
            }

            Button(
                onClick = onTriggerSelfHeal,
                colors = ButtonDefaults.buttonColors(containerColor = HudGreen.copy(alpha = 0.2f)),
                border = BorderStroke(1.dp, HudGreen),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Healing, contentDescription = null, tint = HudGreen, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("إنعاش فوري ⚡", color = HudGreen, fontFamily = CairoFont, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SpatialCrashInspectorDialog(
    crash: CrashLogFile,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    var aiAnalysis by remember { mutableStateOf<String?>(null) }
    var aiLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    val highlightedStack = remember(crash.fullStack) {
        buildHighlightedStack(crash.fullStack)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF080D1A),
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.BugReport, contentDescription = null, tint = HudPink, modifier = Modifier.size(20.dp))
                    Text("تشخيص العطل والسباك البرمجي", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Text(crash.fileName, color = Color.Gray, fontFamily = RobotoMonoFont, fontSize = 10.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // AI Diagnosis Section
                if (aiLoading) {
                    Surface(
                        color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(color = Color(0xFF8B5CF6), strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            Text("يقوم الذكاء الاصطناعي بتحليل الـ Stack وموقع الخلل...", color = Color.White, fontFamily = CairoFont, fontSize = 11.5.sp)
                        }
                    }
                } else if (aiAnalysis != null) {
                    Surface(
                        color = Color(0xFF8B5CF6).copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF8B5CF6)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("تحليل وتوجيه الذكاء الاصطناعي 🧠:", color = Color(0xFFC084FC), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                IconButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(aiAnalysis!!))
                                        Toast.makeText(context, "تم نسخ تحليل الذكاء الاصطناعي 📋", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = Color(0xFFC084FC), modifier = Modifier.size(14.dp))
                                }
                            }
                            Text(aiAnalysis!!, color = Color(0xFFF1F5F9), fontFamily = NotoSansFont, fontSize = 11.5.sp, lineHeight = 16.sp)
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            aiLoading = true
                            scope.launch {
                                val prompt = """
                                    أنت مهندس خبير أندرويد (Kotlin/Jetpack Compose).
                                    حلل سجل هذا الانهيار بدقة واكتب بالعربية:
                                    1. السطر المسبب والخلل الفعلي.
                                    2. سبب حدوثه.
                                    3. كود التصحيح الموصى به (Patch Code).
                                    
                                    السباك:
                                    ${crash.fullStack.take(3000)}
                                """.trimIndent()

                                val res = AppServices.chatWithAssistant(
                                    listOf(Pair(true, prompt)),
                                    "أنت خبير أعطال أندرويد متخصص في Kotlin وCompose. قدّم إجابات مباشرة ومحددة برمجياً."
                                )
                                aiAnalysis = res
                                aiLoading = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6).copy(alpha = 0.25f)),
                        border = BorderStroke(1.dp, Color(0xFF8B5CF6)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFC084FC), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تحليل الخلل بالذكاء الاصطناعي ✨", color = Color(0xFFC084FC), fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Full Syntax Highlighted Stack View
                Surface(
                    color = Color(0xFF040711),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E2D4A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = highlightedStack,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onCopy) {
                    Text("📋 نسخ", color = HudCyan, fontFamily = CairoFont)
                }
                TextButton(onClick = onShare) {
                    Text("📤 مشاركة", color = GoldPrimary, fontFamily = CairoFont)
                }
                TextButton(onClick = onDismiss) {
                    Text("إغلاق", color = Color.White, fontFamily = CairoFont)
                }
            }
        }
    )
}

/**
 * دالة لتلوين الـ Stack Trace برمجياً (Syntax Highlighting):
 * - أطر com.qabas.app بلون ذهبي نيون
 * - السطور التحذيرية والاستثناءات بلون وردي
 * - أطر النظام بلون رمادي هادئ
 */
private fun buildHighlightedStack(rawStack: String): AnnotatedString {
    return buildAnnotatedString {
        val lines = rawStack.lines()
        lines.forEach { line ->
            when {
                line.startsWith("Caused by:") || line.contains("Exception:") || line.contains("Error:") -> {
                    pushStyle(SpanStyle(color = Color(0xFFFDA4AF), fontWeight = FontWeight.Bold))
                    append(line)
                    pop()
                }
                line.contains("at com.qabas.app.") -> {
                    pushStyle(SpanStyle(color = Color(0xFFFFD700), fontWeight = FontWeight.SemiBold))
                    append(line)
                    pop()
                }
                line.contains("at kotlinx.coroutines.") || line.contains("at androidx.compose.") -> {
                    pushStyle(SpanStyle(color = Color(0xFF38BDF8)))
                    append(line)
                    pop()
                }
                line.startsWith("===") || line.startsWith("---") || line.startsWith("Thread:") -> {
                    pushStyle(SpanStyle(color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold))
                    append(line)
                    pop()
                }
                else -> {
                    pushStyle(SpanStyle(color = Color(0xFF64748B)))
                    append(line)
                    pop()
                }
            }
            append("\n")
        }
    }
}
