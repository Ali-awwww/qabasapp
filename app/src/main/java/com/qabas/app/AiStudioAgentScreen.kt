package com.qabas.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 🛸 استوديو الوكيل الذكي (Autonomous AI Agent Studio)
 * بيئة تطوير متكاملة قائمة على إعطاء الأوامر للوكيل باللغة الطبيعية
 * يقوم الوكيل بالتفكير، استدعاء الأدوات، فحص وكتابة الكود، وتشغيل البناء السحابي
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiStudioAgentScreen(
    onBack: () -> Unit,
    onOpenDeveloperDashboard: () -> Unit = {},
    onOpenTeamConfig: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var showExitConfirmation by remember { mutableStateOf(false) }

    BackHandler {
        if (showExitConfirmation) {
            showExitConfirmation = false
            return@BackHandler
        }
        showExitConfirmation = true
    }

    // State
    var promptInput by remember { mutableStateOf("") }
    var selectedSpec by remember { mutableStateOf(AgentSpecialization.TURBO) }
    var isExecuting by remember { mutableStateOf(false) }
    var currentMission by remember { mutableStateOf<AgentMissionSession?>(null) }
    var missionHistory by remember { mutableStateOf<List<AgentMissionSession>>(emptyList()) }
    var isTeamMode by remember { mutableStateOf(false) }   // 🔑 وضع الفريق (Multi-Model)
    var teamProgressText by remember { mutableStateOf("") } // نص تقدم الفريق الحي
    var showKeyDialog by remember { mutableStateOf(false) }
    var keyDialogInitialTab by remember { mutableStateOf(0) }
    var showRepoDialog by remember { mutableStateOf(false) }
    var isOfflineMode by remember { mutableStateOf(OnDeviceModelManager.isOfflineModeActive(context)) }
    var activeLocalModel by remember { mutableStateOf(OnDeviceModelManager.getSelectedModel(context)) }
    var showOnDeviceDialog by remember { mutableStateOf(false) }
    var executionJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var lastExecutedPrompt by remember { mutableStateOf<String>("") }
    var showModelPicker by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf<String>("gemini-3.5-flash") }
    var applyingDiffPath by remember { mutableStateOf<String?>(null) }
    val TAG = "AiStudioAgentScreen"
    val prefs: SharedPreferences = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)

    // Mission State Management
    var missionAutoSwitched by remember { mutableStateOf(false) }

    // Save current mission state to SharedPreferences
    fun saveMissionState() {
        currentMission?.let { mission ->
            val json = JSONObject().apply {
                put("missionId", mission.id)
                put("userPrompt", mission.userPrompt)
                put("specialization", mission.specialization.name)
                val stepsArray = org.json.JSONArray()
                mission.steps.forEach { step -> stepsArray.put(step.id) }
                put("steps", stepsArray)
                val modifiedArray = org.json.JSONArray()
                mission.modifiedFiles.forEach { modifiedArray.put(it) }
                put("modifiedFiles", modifiedArray)
                put("buildTriggered", mission.buildTriggered)
                put("apkUrl", mission.apkUrl)
                put("timestamp", mission.timestamp)
            }
            prefs.edit().putString("active_mission_json", json.toString()).apply()
        }
    }

    // Restore mission state from SharedPreferences
    fun restoreMissionState(): AgentMissionSession? {
        val json = prefs.getString("active_mission_json", null)
        if (json.isNullOrBlank()) return null

        try {
            val obj = JSONObject(json)
            val stepsList = obj.getJSONArray("steps")
            val steps = (0 until stepsList.length()).map { idx ->
                AgentExecutionStep(
                    id = stepsList.getString(idx),
                    title = "", // would need to store titles too
                    description = "",
                    toolName = "",
                    outputSnippet = ""
                )
            }

            return AgentMissionSession(
                id = obj.getString("missionId"),
                userPrompt = obj.getString("userPrompt"),
                specialization = AgentSpecialization.valueOf(obj.getString("specialization")),
                status = AgentTaskStatus.PLANNING,
                steps = steps,
                modifiedFiles = if (obj.has("modifiedFiles")) {
                    val arr = obj.getJSONArray("modifiedFiles")
                    (0 until arr.length()).map { arr.getString(it) }
                } else emptyList(),
                buildTriggered = obj.getBoolean("buildTriggered"),
                apkUrl = if (obj.has("apkUrl")) obj.getString("apkUrl") else null,
                timestamp = if (obj.has("timestamp")) obj.getLong("timestamp") else System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore mission state: ${e.message}")
            prefs.edit().remove("active_mission_json").apply()
            return null
        }
    }

    // Export mission as JSON string for backup/sharing
    fun exportMissionJson(): String {
        return currentMission?.let { mission ->
            JSONObject().apply {
                put("missionId", mission.id)
                put("userPrompt", mission.userPrompt)
                put("specialization", mission.specialization.name)
                val stepsArray = org.json.JSONArray()
                mission.steps.forEach { step -> stepsArray.put(step.id) }
                put("steps", stepsArray)
                val modifiedArray = org.json.JSONArray()
                mission.modifiedFiles.forEach { modifiedArray.put(it) }
                put("modifiedFiles", modifiedArray)
                put("buildTriggered", mission.buildTriggered)
                put("apkUrl", mission.apkUrl)
                put("timestamp", mission.timestamp)
            }.toString()
        } ?: ""
    }

    // Import mission from JSON string
    fun importMissionFromJson(jsonString: String): Boolean {
        try {
            val obj = JSONObject(jsonString)
            val stepsList = obj.getJSONArray("steps")
            val steps = (0 until stepsList.length()).map { idx ->
                AgentExecutionStep(
                    id = stepsList.getString(idx),
                    title = "",
                    description = "",
                    toolName = "",
                    outputSnippet = ""
                )
            }

            currentMission = AgentMissionSession(
                id = obj.optString("missionId", obj.optString("id", "")),
                userPrompt = obj.getString("userPrompt"),
                specialization = AgentSpecialization.valueOf(obj.getString("specialization")),
                status = AgentTaskStatus.PLANNING,
                steps = steps,
                modifiedFiles = if (obj.has("modifiedFiles")) {
                    val arr = obj.getJSONArray("modifiedFiles")
                    (0 until arr.length()).map { arr.getString(it) }
                } else emptyList(),
                buildTriggered = obj.getBoolean("buildTriggered"),
                apkUrl = if (obj.has("apkUrl")) obj.getString("apkUrl") else null,
                timestamp = if (obj.has("timestamp")) obj.getLong("timestamp") else System.currentTimeMillis()
            )

            missionHistory = missionHistory + listOf(currentMission!!)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import mission: ${e.message}")
            return false
        }
    }
    // Key Resolution & Real Live Diagnostics
    var keyResolution by remember { mutableStateOf(AgentConnectivityEngine.resolveKeyWithSource(context)) }
    var isPinging by remember { mutableStateOf(false) }
    var geminiHealth by remember {
        mutableStateOf(
            LiveHealthStatus(
                serviceName = "Google Gemini",
                isConnected = false,
                latencyMs = 0L,
                statusDetail = "جاري الفحص المباشر...",
                keySource = KeySource.NONE,
                modelName = "gemini-3.5-flash"
            )
        )
    }
    var githubHealth by remember {
        mutableStateOf(
            LiveHealthStatus(
                serviceName = "GitHub API",
                isConnected = false,
                latencyMs = 0L,
                statusDetail = "جاري الفحص المباشر...",
                keySource = KeySource.NONE,
                modelName = "REST API v3"
            )
        )
    }

    val refreshDiagnostics: () -> Unit = {
        coroutineScope.launch {
            isPinging = true
            val resolved = AgentConnectivityEngine.resolveKeyWithSource(context)
            keyResolution = resolved
            val activeRepoItem = RepoVaultManager.getActiveRepo(context)
            val ghToken = activeRepoItem?.token ?: ""
            val ghRepo = activeRepoItem?.fullName ?: ""

            geminiHealth = AgentConnectivityEngine.pingGeminiLive(resolved.second, resolved.third)
            githubHealth = AgentConnectivityEngine.pingGitHubLive(ghToken, ghRepo)
            isPinging = false
        }
    }

    LaunchedEffect(Unit) {
        refreshDiagnostics()
    }

    // Mission State Auto-Save & Restore + Auto-Switch Logic
LaunchedEffect(currentMission, isOfflineMode) {
        // Save mission state whenever it changes
        saveMissionState()

        // If offline mode is active but no local model is available, auto-switch to cloud
        if (isOfflineMode && currentMission != null && !OnDeviceModelManager.hasAnyInstalledModel(context)) {
            if (!missionAutoSwitched) {
                missionAutoSwitched = true
                // Save current mission before switching
                saveMissionState()
                // Switch to cloud mode - show key dialog or use default key
                showKeyDialog = true
                keyDialogInitialTab = 1  // Switch to API keys tab
            }
        } else if (missionAutoSwitched && OnDeviceModelManager.hasAnyInstalledModel(context)) {
            // User returned to local mode with a model installed
            missionAutoSwitched = false
            // Could restore mission state here - currently manual
        }
    }

    // Periodic auto-save every 30 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(30000) // 30 seconds
            saveMissionState()
        }
    }

    // Active Engine & Repo
    val activeEnginePair = remember { derivedStateOf { UniversalKeyBrain.getActiveEngine() } }
    val activeRepo = remember(context) { RepoVaultManager.getActiveRepo(context) }

    val listState = rememberLazyListState()

    // Quick Prompts
    val quickPrompts = remember {
        listOf(
            "✨ أضف شاشة جديدة لمواقيت الصلاة وبوصلة القبلة",
            "🩺 افحص أخطاء البناء والـ CI وصلح ملفات سير العمل",
            "⚡ حسن أداء مشغل الصوت واستهلاك الذاكرة في الخلفية",
            "🚀 ابدأ تشغيل بناء حزمة APK جديدة على GitHub Actions",
            "🛡️ تدقيق معماري شامل لملفات التطبيق وحذف الأكواد المهملة"
        )
    }

    Scaffold(
        bottomBar = bottomBar,
        containerColor = Color(0xFF070B14),
        topBar = {
            GlassmorphicSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                backgroundColor = Color(0xFF0B132B).copy(alpha = 0.85f),
                borderColor = Color(0xFF00E5FF).copy(alpha = 0.25f)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B))
                                .testTag("agent_studio_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع",
                                tint = Color(0xFFE5B842)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f, fill = true)) {
                            Text(
                                text = "منصة استوديو الوكيل الذكي",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .clickable {
                                        if (isOfflineMode) showOnDeviceDialog = true else { keyDialogInitialTab = 0; showKeyDialog = true }
                                    }
                            ) {
                                val isLocalInstalled = OnDeviceModelManager.isModelInstalled(context, activeLocalModel)
                                val dotColor = if (isOfflineMode) {
                                    if (isLocalInstalled) Color(0xFF10B981) else Color(0xFFF59E0B)
                                } else {
                                    if (geminiHealth.isConnected) Color(0xFF10B981) else Color(0xFFEF4444)
                                }
                                val statusText = if (isOfflineMode) {
                                    if (isLocalInstalled) "ON-DEVICE • ${activeLocalModel.name} ✅" else "ON-DEVICE • يلزم تنزيل النموذج 📥"
                                } else {
                                    if (geminiHealth.isConnected) "LIVE CONNECTED (${geminiHealth.latencyMs}ms) • SPATIAL HUD" else "OFFLINE • يلزم مفتاح API"
                                }
                                val textColor = if (isOfflineMode) {
                                    if (isLocalInstalled) Color(0xFF10B981) else Color(0xFFF59E0B)
                                } else {
                                    if (geminiHealth.isConnected) Color(0xFF00E5FF) else Color(0xFFEF4444)
                                }

                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = statusText,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Repo Badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E293B).copy(alpha = 0.8f),
                            border = BorderStroke(1.dp, Color(0xFFE5B842).copy(alpha = 0.4f)),
                            modifier = Modifier
                                .clickable { showRepoDialog = true }
                                .wrapContentWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Source,
                                    contentDescription = null,
                                    tint = Color(0xFFE5B842),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = activeRepo?.repo ?: "بدون مستودع",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Engine Info Strip
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isOfflineMode) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFE5B842).copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, Color(0xFFE5B842).copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .clickable { showOnDeviceDialog = true }
                                        .wrapContentWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Memory,
                                            contentDescription = null,
                                            tint = Color(0xFFE5B842),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "محلي: ${activeLocalModel.name}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE5B842),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = activeEnginePair.value.first.color.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, activeEnginePair.value.first.color.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .clickable {
                                            keyDialogInitialTab = 0
                                            showKeyDialog = true
                                        }
                                        .wrapContentWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = null,
                                            tint = activeEnginePair.value.first.color,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = activeEnginePair.value.first.displayName,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = activeEnginePair.value.first.color,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // (أُزيل اختصار النماذج المكرر — بطاقة الرادار أدناه تفتح نفس الحوار)
                        }

                        Text(
                            text = when {
                                isExecuting && isTeamMode && teamProgressText.isNotBlank() -> teamProgressText
                                isExecuting -> "⚡ الوكيل يعمل ذاتياً..."
                                else -> "جاهز لتلقي الأمر 🎯"
                            },
                            fontSize = 11.sp,
                            color = if (isExecuting) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // 0. Live Network Telemetry & Diagnostics Radar (رادار الفحص الحي والاستجابة)
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0D1527).copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sensors,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "رادار الفحص الحي 📡",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Re-ping button
                            IconButton(
                                onClick = { refreshDiagnostics() },
                                enabled = !isPinging,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E293B))
                                    .testTag("agent_refresh_ping_btn")
                            ) {
                                if (isPinging) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color(0xFF00E5FF),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "إعادة الفحص الحي",
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Direct Mode Switcher (مفتاح التحويل المباشر بين السحابي والنموذج المحلي)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF070B14), RoundedCornerShape(8.dp))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (!isOfflineMode) Color(0xFF00E5FF).copy(alpha = 0.25f) else Color.Transparent,
                                border = if (!isOfflineMode) BorderStroke(1.dp, Color(0xFF00E5FF)) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        isOfflineMode = false
                                        OnDeviceModelManager.setOfflineModeActive(context, false)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cloud,
                                        contentDescription = null,
                                        tint = if (!isOfflineMode) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "الوضع السحابي (Cloud API)",
                                        fontSize = 11.sp,
                                        fontWeight = if (!isOfflineMode) FontWeight.Bold else FontWeight.Normal,
                                        color = if (!isOfflineMode) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isOfflineMode) Color(0xFFE5B842).copy(alpha = 0.25f) else Color.Transparent,
                                border = if (isOfflineMode) BorderStroke(1.dp, Color(0xFFE5B842)) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        isOfflineMode = true
                                        OnDeviceModelManager.setOfflineModeActive(context, true)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Memory,
                                        contentDescription = null,
                                        tint = if (isOfflineMode) Color(0xFFE5B842) else Color(0xFF94A3B8),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "الوضع المحلي (On-Device)",
                                        fontSize = 11.sp,
                                        fontWeight = if (isOfflineMode) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isOfflineMode) Color(0xFFE5B842) else Color(0xFF94A3B8),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (isOfflineMode) {
                            // On-Device Model Telemetry Card
                            val isLocalInstalled = OnDeviceModelManager.isModelInstalled(context, activeLocalModel)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isLocalInstalled) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFFF59E0B).copy(alpha = 0.12f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isLocalInstalled) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFFF59E0B).copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showOnDeviceDialog = true }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f, fill = false)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isLocalInstalled) Color(0xFF10B981) else Color(0xFFF59E0B))
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "النموذج المحلي: ${activeLocalModel.name}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isLocalInstalled) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFF59E0B).copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = if (isLocalInstalled) "جاهز ومثبت (${activeLocalModel.sizeFormatted}) ✅" else "يلزم التنزيل (${activeLocalModel.sizeFormatted}) 📥",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isLocalInstalled) Color(0xFF10B981) else Color(0xFFF59E0B),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "المتطلبات: ${activeLocalModel.ramRequirement} • خصوصية تامة بدون إنترنت",
                                            fontSize = 10.sp,
                                            color = Color(0xFFCBD5E1)
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF00E5FF).copy(alpha = 0.2f),
                                            border = BorderStroke(0.6.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = "فتح مركز التنزيل 📥",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF00E5FF),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Dual Health Pills (Gemini & GitHub)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Gemini Status Pill
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (geminiHealth.isConnected) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFFEF4444).copy(alpha = 0.12f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (geminiHealth.isConnected) Color(0xFF10B981).copy(alpha = 0.45f) else Color(0xFFEF4444).copy(alpha = 0.45f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            keyDialogInitialTab = 0
                                            showKeyDialog = true
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(if (geminiHealth.isConnected) Color(0xFF10B981) else Color(0xFFEF4444))
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Google Gemini",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                            if (geminiHealth.isConnected) {
                                                Text(
                                                    text = "${geminiHealth.latencyMs}ms",
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF10B981)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = geminiHealth.statusDetail,
                                            fontSize = 10.sp,
                                            color = if (geminiHealth.isConnected) Color(0xFFCBD5E1) else Color(0xFFFCA5A5),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // GitHub Status Pill
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (githubHealth.isConnected) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFFF59E0B).copy(alpha = 0.12f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (githubHealth.isConnected) Color(0xFF10B981).copy(alpha = 0.45f) else Color(0xFFF59E0B).copy(alpha = 0.45f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { showRepoDialog = true }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(if (githubHealth.isConnected) Color(0xFF10B981) else Color(0xFFF59E0B))
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "GitHub API",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                            if (githubHealth.latencyMs > 0) {
                                                Text(
                                                    text = "${githubHealth.latencyMs}ms",
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (githubHealth.isConnected) Color(0xFF10B981) else Color(0xFFF59E0B)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = githubHealth.statusDetail,
                                            fontSize = 10.sp,
                                            color = Color(0xFFCBD5E1),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Key Source Resolution Badge Line
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        keyDialogInitialTab = if (keyResolution.third == KeySource.NONE) 1 else 0
                                        showKeyDialog = true
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Text(
                                        text = "مصدر الاعتماد:",
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = keyResolution.third.color.copy(alpha = 0.2f),
                                        border = BorderStroke(0.6.dp, keyResolution.third.color.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = keyResolution.third.label,
                                            fontSize = 9.sp,
                                            color = keyResolution.third.color,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }

                                Text(
                                    text = "الموديل: gemini-3.5-flash",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF00E5FF)
                                )
                            }
                        }
                    }
                }
            }

            // 1. Mission Mode Selection
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "اختر تخصص الوكيل للعملية (Agent Specialization):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE5B842),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(AgentSpecialization.entries.toTypedArray()) { spec ->
                            val isSelected = selectedSpec == spec
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) spec.color.copy(alpha = 0.2f) else Color(0xFF0F172A),
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) spec.color else Color(0xFF334155)
                                ),
                                modifier = Modifier
                                    .heightIn(min = 44.dp)
                                    .clickable { selectedSpec = spec }
                                    .wrapContentWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = spec.icon,
                                        contentDescription = null,
                                        tint = spec.color,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = spec.title,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 1.5 Studio Engine Architecture & Memory Shield (مناعة الذاكرة ومنع OOM)
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0B172A).copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "درع المنصة 🛡️",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE2E8F0),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                border = BorderStroke(0.8.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                                modifier = Modifier.wrapContentWidth()
                            ) {
                                Text(
                                    text = "HEALTHY 100%",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    maxLines = 1
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // شريط حالة مدمج: سليم + توقيع ثابت — بدل 4 شارات بمعلومات بناء لا حية
                        Text(
                            text = "سليم 100% ✅ • توقيع ثابت ومتزامن 🔏 • حماية ذاكرة OOM 🛡️",
                            fontSize = 10.5.sp,
                            color = Color(0xFF6EE7B7),
                            fontFamily = CairoFont,
                            maxLines = 2,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // 2. Command Prompt Cockpit
            item {
                GlassmorphicSurface(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFF0D1527).copy(alpha = 0.85f),
                    borderColor = Color(0xFF00E5FF).copy(alpha = 0.35f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "أعطِ أمرك للوكيل البرمجي:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF1E293B),
                                modifier = Modifier.wrapContentWidth()
                            ) {
                                Text(
                                    text = "⚡ PROMPT",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E5FF),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = promptInput,
                            onValueChange = { promptInput = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 90.dp, max = 160.dp)
                                .testTag("agent_studio_prompt_input"),
                            placeholder = {
                                Text(
                                    text = "اكتب باللغة العربية ما تريده، مثل:\n«أضف شاشة جديدة...» أو «أصلح مشكلة تعليق المشغل...»",
                                    color = Color(0xFF64748B),
                                    fontSize = 12.sp
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color(0xFF00E5FF)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isExecuting,
                            maxLines = 8
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Model Selector + Token Budget
                        if (!isOfflineMode) {
                            val provider = AgentConnectivityEngine.resolveKeyWithSource(context).first
                            val models = when (provider) {
                                DetectedProvider.GEMINI -> listOf("gemini-3.5-flash", "gemini-3.5-pro", "gemini-2.0-flash")
                                DetectedProvider.GROQ -> listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b")
                                DetectedProvider.OPENAI -> listOf("gpt-4o", "gpt-4o-mini", "o1-preview")
                                DetectedProvider.OPENROUTER -> listOf("anthropic/claude-sonnet-4.5", "anthropic/claude-3.5-sonnet", "openai/gpt-4o", "meta-llama/llama-3.3-70b")
                                else -> listOf("gemini-3.5-flash")
                            }
                            Column(modifier = Modifier.fillMaxWidth()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    item {
                                        OutlinedButton(
                                            onClick = { showModelPicker = true },
                                            modifier = Modifier.wrapContentWidth().height(36.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF00E5FF).copy(alpha = 0.1f),
                                                contentColor = Color(0xFF00E5FF)
                                            )
                                        ) {
                                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("النموذج: $selectedModel", fontSize = 11.sp, fontFamily = CairoFont, maxLines = 1, softWrap = false)
                                        }
                                    }
                                    // 🔑 زر فتح تكوين فريق النماذج المتعددة
                                    item {
                                        OutlinedButton(
                                            onClick = onOpenTeamConfig,
                                            modifier = Modifier.wrapContentWidth().height(36.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, Color(0xFFD4AF37).copy(alpha = 0.5f)),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFD4AF37).copy(alpha = 0.1f),
                                                contentColor = Color(0xFFD4AF37)
                                            )
                                        ) {
                                            Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("👥 فريق النماذج", fontSize = 11.sp, fontFamily = CairoFont, maxLines = 1, softWrap = false)
                                        }
                                    }
                                    // 🔑 Toggle تشغيل وضع الفريق (Multi-Model)
                                    item {
                                        FilterChip(
                                            selected = isTeamMode,
                                            onClick = { isTeamMode = !isTeamMode },
                                            label = {
                                                Text(
                                                    if (isTeamMode) "👑 وضع الفريق مُفعّل" else "فردي",
                                                    fontSize = 11.sp, fontFamily = CairoFont, maxLines = 1, softWrap = false
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFFD4AF37).copy(alpha = 0.25f),
                                                selectedLabelColor = Color(0xFFD4AF37)
                                            )
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "ميزانية التوكن: ~4000 توكن/طلب • تكلفة تقريبية: \$0.01–0.05",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF64748B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            // قوالب العمل الموصى بها — توصية لا إجبار، مع فحص توافر النماذج قبل التطبيق
                            if (isTeamMode) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "قوالب موصى بها للبدء:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFD4AF37)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    MultiModelAgentOrchestrator.teamTemplates().forEach { (name, members) ->
                                        item {
                                            val missing = MultiModelAgentOrchestrator.templateMissingProviders(members)
                                            val ready = missing.isEmpty()
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (ready) Color(0xFFD4AF37).copy(alpha = 0.12f) else Color(0xFF1E293B),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (ready) Color(0xFFD4AF37).copy(alpha = 0.6f) else Color(0xFF475569)
                                                ),
                                                modifier = Modifier.clickable {
                                                    if (!ready) {
                                                        Toast.makeText(
                                                            context,
                                                            "⛔ قالب $name ينقصه: ${missing.joinToString("، ")} — أدخل مفاتيحها أولاً",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                        keyDialogInitialTab = 0
                                                        showKeyDialog = true
                                                    } else {
                                                        MultiModelAgentOrchestrator.saveTeam(context, members.map { it.copy() })
                                                        Toast.makeText(context, "تم تجهيز $name ✅ — اكتب أمرك وابدأ", Toast.LENGTH_SHORT).show()
                                                    }
                                                }.wrapContentWidth()
                                            ) {
                                                Text(
                                                    text = (if (ready) "✅ " else "⚠️ ") + name,
                                                    fontSize = 10.sp,
                                                    color = if (ready) Color(0xFFD4AF37) else Color(0xFF94A3B8),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        // Quick Chips
                        Text(
                            text = "أوامر سريعة مقترحة:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(quickPrompts) { qp ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF1E293B),
                                    border = BorderStroke(1.dp, Color(0xFF475569)),
                                    modifier = Modifier
                                        .clickable(enabled = !isExecuting) { promptInput = qp }
                                        .wrapContentWidth()
                                ) {
                                    Text(
                                        text = qp,
                                        fontSize = 10.sp,
                                        color = Color(0xFFCBD5E1),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        // Missing Key / Offline Model Warning Banners
                        if (isOfflineMode) {
                            val isLocalInstalled = OnDeviceModelManager.isModelInstalled(context, activeLocalModel)
                            Spacer(modifier = Modifier.height(10.dp))
                            if (isLocalInstalled) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showOnDeviceDialog = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "الوكيل المحلي جاهز (On-Device): ${activeLocalModel.name} ✅",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "يعمل 100% بدون إنترنت وبدون مفاتيح API عبر معالج الهاتف مباشرة.",
                                                fontSize = 10.sp,
                                                color = Color(0xFF6EE7B7)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showOnDeviceDialog = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudDownload,
                                            contentDescription = null,
                                            tint = Color(0xFFF59E0B),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "يلزم تنزيل النموذج المحلي (${activeLocalModel.sizeFormatted})",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "اضغط هنا لفتح مركز التنزيل وتحميل ${activeLocalModel.name} للعمل بدون إنترنت.",
                                                fontSize = 10.sp,
                                                color = Color(0xFFFDE68A)
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFFE5B842).copy(alpha = 0.2f),
                                                border = BorderStroke(0.8.dp, Color(0xFFE5B842).copy(alpha = 0.6f)),
                                                modifier = Modifier.wrapContentWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = Color(0xFFE5B842), modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Text(text = "فتح مركز التنزيل الآن 📥", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE5B842))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (keyResolution.third == KeySource.NONE) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFEF4444).copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        keyDialogInitialTab = 0
                                        showKeyDialog = true
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyOff,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "الوكيل غير متصل - يلزم إدخال مفتاح API",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "يلتزم الوكيل بدستور الصدق التام ولا ينفذ أوامر وهمية. اضغط هنا لإدخال مفتاحك وفحصه حياً.",
                                            fontSize = 10.sp,
                                            color = Color(0xFFFCA5A5)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF00E5FF).copy(alpha = 0.2f),
                                            border = BorderStroke(0.8.dp, Color(0xFF00E5FF).copy(alpha = 0.6f)),
                                            modifier = Modifier
                                                .clickable {
                                                    keyDialogInitialTab = 1
                                                    showKeyDialog = true
                                                }
                                                .wrapContentWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CardGiftcard,
                                                    contentDescription = null,
                                                    tint = Color(0xFF00E5FF),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = "تصفح المنصات المجانية للحصول على مفتاح برمجة 🎁",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF00E5FF)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isLocalReady = isOfflineMode && OnDeviceModelManager.isModelInstalled(context, activeLocalModel)
                            val isCloudReady = !isOfflineMode && keyResolution.third != KeySource.NONE
                            val canExecute = if (isOfflineMode) isLocalReady else isCloudReady

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isExecuting) {
                                    Button(
                                        onClick = {
                                            executionJob?.cancel()
                                            executionJob = null
                                            isExecuting = false
                                            Toast.makeText(context, "تم إلغاء المهمة ⏹️", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("إيقاف المهمة ⏹️", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            if (isOfflineMode && !isLocalReady) {
                                                showOnDeviceDialog = true
                                                Toast.makeText(context, "الرجاء تنزيل النموذج المحلي أولاً للعمل بدون إنترنت", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            if (!isOfflineMode && !isCloudReady) {
                                                showKeyDialog = true
                                                Toast.makeText(context, "يلزم إدخال مفتاح API وفحصه حياً أولاً", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            // 🔑 منع تعارض وضع الفريق مع وضع النموذج المحلي
                                            if (isTeamMode && isOfflineMode) {
                                                Toast.makeText(context, "وضع الفريق يتطلب اتصالاً سحابياً — عطّل وضع النموذج المحلي أولاً", Toast.LENGTH_LONG).show()
                                                return@Button
                                            }
                                            // 🔗 بوابة المستودع: الفريق يعمل على مستودع حقيقي يراه — لا عمل بلا ربط
                                            if (isTeamMode && RepoVaultManager.getActiveRepo(context) == null) {
                                                showRepoDialog = true
                                                Toast.makeText(context, "اربط مستودع GitHub أولاً — القائد يحتاج رؤية الشجرة ووجهة الدفع 🔗", Toast.LENGTH_LONG).show()
                                                return@Button
                                            }
                                            val prompt = promptInput.trim()
                                            if (prompt.isBlank()) {
                                                Toast.makeText(context, "يرجى كتابة أمر للوكيل أولاً", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }

                                            lastExecutedPrompt = prompt
                                            isExecuting = true
                                            val job = coroutineScope.launch {
                                                try {
                                                    if (isTeamMode) {
                                                        // 🔑 وضع الفريق متعدد النماذج
                                                        val mission = executeAgentMissionMultiModel(
                                                            context = context,
                                                            userPrompt = prompt,
                                                            specialization = selectedSpec,
                                                            onTeamProgress = { progressText ->
                                                                teamProgressText = progressText
                                                            }
                                                        )
                                                        currentMission = mission
                                                        missionHistory = listOf(mission) + missionHistory
                                                    } else {
                                                        executeAgentMission(
                                                        context = context,
                                                        prompt = prompt,
                                                        spec = selectedSpec,
                                                        repo = activeRepo,
                                                        selectedModel = selectedModel,
                                                        onUpdate = { mission ->
                                                            currentMission = mission
                                                        },
                                                        onFinished = { finalMission ->
                                                            currentMission = finalMission
                                                            missionHistory = listOf(finalMission) + missionHistory
                                                        }
                                                    )
                                                    }
                                                } catch (e: kotlinx.coroutines.CancellationException) {
                                                    // do nothing — finally resets state
                                                } finally {
                                                    isExecuting = false
                                                    executionJob = null
                                                    promptInput = ""
                                                    teamProgressText = ""
                                                }
                                            }
                                            executionJob = job
                                        },
                                        enabled = !isExecuting && (promptInput.isNotBlank() || !canExecute),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("agent_studio_execute_btn"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (!canExecute) Color(0xFFEF4444) else if (isOfflineMode) Color(0xFFE5B842) else Color(0xFF00E5FF),
                                            contentColor = if (!canExecute) Color.White else Color(0xFF070B14)
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        if (!canExecute) {
                                            Icon(
                                                imageVector = if (isOfflineMode) Icons.Default.Download else Icons.Default.Key,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (isOfflineMode) "تنزيل النموذج المحلي 📥" else "إدخال وتفعيل المفتاح 🔑",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (isOfflineMode) Icons.Default.Memory else Icons.AutoMirrored.Filled.Send,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (isOfflineMode) "تنفيذ الأمر محلياً 📴" else "تنفيذ المهمة عبر الوكيل 🚀",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }

                                    if (lastExecutedPrompt.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = {
                                                val prompt = lastExecutedPrompt
                                                promptInput = prompt
                                                isExecuting = true
                                                val job = coroutineScope.launch {
                                                    try {
                                                        if (isTeamMode) {
                                                            // 🔑 نحفظ وضع الفريق عند إعادة المحاولة
                                                            val mission = executeAgentMissionMultiModel(
                                                                context = context,
                                                                userPrompt = prompt,
                                                                specialization = selectedSpec,
                                                                onTeamProgress = { progressText ->
                                                                    teamProgressText = progressText
                                                                }
                                                            )
                                                            currentMission = mission
                                                            missionHistory = listOf(mission) + missionHistory
                                                        } else {
                                                        executeAgentMission(
                                                            context = context,
                                                            prompt = prompt,
                                                            spec = selectedSpec,
                                                            repo = activeRepo,
                                                            selectedModel = selectedModel,
                                                            onUpdate = { mission -> currentMission = mission },
                                                            onFinished = { finalMission ->
                                                                currentMission = finalMission
                                                                missionHistory = listOf(finalMission) + missionHistory
                                                            }
                                                        )
                                                        }
                                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                                        // do nothing — finally will reset
                                                    } finally {
                                                        isExecuting = false
                                                        executionJob = null
                                                        teamProgressText = ""
                                                    }
                                                }
                                                executionJob = job
                                            },
                                            modifier = Modifier.wrapContentWidth().height(48.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("إعادة المحاولة 🔁", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }

                                if (promptInput.isNotBlank() && !isExecuting) {
                                    OutlinedButton(
                                        onClick = { promptInput = "" },
                                        modifier = Modifier.height(48.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, Color(0xFF64748B))
                                    ) {
                                        Text("مسح", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                    }
                                }
                            }

                            if (promptInput.isNotBlank() && !isExecuting) {
                                OutlinedButton(
                                    onClick = { promptInput = "" },
                                    modifier = Modifier.height(48.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFF64748B))
                                ) {
                                    Text("مسح", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 3. Current Live Mission Activity Stream
            currentMission?.let { mission ->
                item {
                    LiveMissionExecutionCard(
                        mission = mission,
                        teamProgress = teamProgressText,
                        isTeamMode = isTeamMode,
                        onTriggerBuild = {
                            coroutineScope.launch {
                                triggerBuildDirectly(context, activeRepo) { step ->
                                    val updatedSteps = mission.steps + step
                                    currentMission = mission.copy(
                                        steps = updatedSteps,
                                        status = if (step.isSuccess) AgentTaskStatus.COMPLETED else AgentTaskStatus.FAILED,
                                        buildTriggered = step.isSuccess
                                    )
                                }
                            }
                        },
                        onApplyDiff = { diff ->
                            if (activeRepo == null || activeRepo.token.isBlank()) {
                                Toast.makeText(context, "اربط مستودع GitHub برمز صالح أولاً 🔗", Toast.LENGTH_LONG).show()
                            } else {
                                applyingDiffPath = diff.filePath
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    val client = GitHubRepoClient(activeRepo.owner, activeRepo.repo, activeRepo.token)
                                    val ok = client.updateFile(
                                        diff.filePath,
                                        activeRepo.defaultBranch.ifBlank { "main" },
                                        "feat(agent): update ${diff.filePath.substringAfterLast("/")}",
                                        diff.diffText
                                    )
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        applyingDiffPath = null
                                        if (ok) {
                                            currentMission = mission.copy(
                                                appliedPaths = mission.appliedPaths + diff.filePath
                                            )
                                            Toast.makeText(context, "تم الاعتماد والدفع ✅", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "تعذر الدفع — تحقق من الصلاحيات والفرع", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        },
                        onDiscardDiff = { diff ->
                            currentMission = mission.copy(
                                proposedDiffs = mission.proposedDiffs.filterNot { it.filePath == diff.filePath }
                            )
                            Toast.makeText(context, "تم تجاهل المقترح (تراجع) 🗑️", Toast.LENGTH_SHORT).show()
                        },
                        applyingPath = applyingDiffPath
                    )
                }
            }

            // 4. Mission History
            if (missionHistory.isNotEmpty()) {
                item {
                    Text(
                        text = "سجل العمليات المنجزة حديثاً:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE5B842),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(missionHistory) { itemMission ->
                    HistoricalMissionCard(itemMission)
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    // Dialogs
    if (showKeyDialog) {
        ApiKeySetupDialog(
            context = context,
            initialTab = keyDialogInitialTab,
            onKeySaved = { refreshDiagnostics() },
            onOpenOnDeviceModels = { showOnDeviceDialog = true },
            onExportMission = { exportMissionJson() },
            onImportMission = {
                val json = prefs.getString("active_mission_json", "") ?: ""
                if (json.isNotBlank()) importMissionFromJson(json)
            },
            onDismiss = { showKeyDialog = false }
        )
    }

    if (showRepoDialog) {
        RepoSetupDialog(
            context = context,
            activeRepo = activeRepo,
            onDismiss = { showRepoDialog = false }
        )
    }

    if (showOnDeviceDialog) {
        OnDeviceModelDownloadDialog(
            context = context,
            onModelActivated = {
                isOfflineMode = OnDeviceModelManager.isOfflineModeActive(context)
                activeLocalModel = OnDeviceModelManager.getSelectedModel(context)
            },
            onDismiss = { showOnDeviceDialog = false }
        )
    }

    // Model Picker Dialog
    if (showModelPicker) {
        val provider = AgentConnectivityEngine.resolveKeyWithSource(context).first
        val models = when (provider) {
            DetectedProvider.GEMINI -> listOf("gemini-3.5-flash", "gemini-3.5-pro", "gemini-2.0-flash")
            DetectedProvider.GROQ -> listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b")
            DetectedProvider.OPENAI -> listOf("gpt-4o", "gpt-4o-mini", "o1-preview")
            DetectedProvider.OPENROUTER -> listOf("anthropic/claude-sonnet-4.5", "anthropic/claude-3.5-sonnet", "openai/gpt-4o", "meta-llama/llama-3.3-70b")
            else -> listOf("gemini-3.5-flash")
        }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showModelPicker = false },
            title = { Text("اختر النموذج", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    models.forEach { m ->
                        Text(text = m, color = Color.White, fontFamily = CairoFont, modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clickable { selectedModel = m; showModelPicker = false }
                            .background(if (selectedModel == m) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color.Transparent))
                    }
                }
            },
            dismissButton = { TextButton(onClick = { showModelPicker = false }) { Text("إغلاق", color = Color(0xFF00E5FF), fontFamily = CairoFont) } },
            confirmButton = {},
            containerColor = Color(0xFF0B132B)
        )
    }

    if (showExitConfirmation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("تأكيد الخروج", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold) },
            text = { Text("هل تريد حقاً الخروج من التطبيق؟", color = Color(0xFFE2E8F0), fontFamily = NotoSansFont) },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirmation = false
                    onBack()
                }) {
                    Text("نعم، خروج", color = Color(0xFFEF4444), fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) {
                    Text("إلغاء", color = Color(0xFF94A3B8), fontFamily = CairoFont)
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        )
    }
}