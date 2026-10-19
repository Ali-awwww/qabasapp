package com.qabas.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qabas.app.ui.agent.AgentNeonButton
import com.qabas.app.ui.agent.AgentStatusChip
import com.qabas.app.ui.agent.AgentStatusKind
import com.qabas.app.ui.agent.AgentNeonCard
import com.qabas.app.ui.agent.AgentSectionHeader
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** قائمة النماذج المتاحة لكل مزود — مصدر واحد يمنع إرسال نموذج مزودٍ إلى مزود آخر */
private fun agentModelsFor(provider: DetectedProvider): List<String> = when (provider) {
    DetectedProvider.GEMINI -> listOf("gemini-3.5-flash", "gemini-3.5-pro")
    DetectedProvider.GROQ -> listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b")
    DetectedProvider.OPENAI -> listOf("gpt-4o", "gpt-4o-mini", "o1-preview")
    DetectedProvider.OPENROUTER -> listOf("anthropic/claude-sonnet-4.5", "anthropic/claude-3.5-sonnet", "openai/gpt-4o", "meta-llama/llama-3.3-70b")
    DetectedProvider.ANTHROPIC -> listOf("claude-sonnet-4.5", "claude-3.5-sonnet", "claude-3.5-haiku")
    DetectedProvider.HUGGINGFACE -> listOf("meta-llama/Llama-3.3-70B-Instruct")
    DetectedProvider.OPENCODE -> listOf("opencode/grok-code")
    else -> emptyList()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiStudioAgentScreen(
    onBack: () -> Unit,
    onOpenDeveloperDashboard: () -> Unit = {},
    onOpenTeamConfig: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    initialTab: Int = 0
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = AgentPrefs.instance(context)

    var showExitConfirmation by remember { mutableStateOf(false) }
    var topTab by remember { mutableStateOf(initialTab) }

    BackHandler {
        if (showExitConfirmation) {
            showExitConfirmation = false
            return@BackHandler
        }
        showExitConfirmation = true
    }

    var promptInput by remember { mutableStateOf("") }
    var selectedSpec by remember { mutableStateOf(AgentSpecialization.TURBO) }
    var isExecuting by remember { mutableStateOf(false) }
    var currentMission by remember { mutableStateOf<AgentMissionSession?>(null) }
    var missionHistory by remember { mutableStateOf<List<AgentMissionSession>>(emptyList()) }
    var isTeamMode by remember { mutableStateOf(false) }
    var teamProgressText by remember { mutableStateOf("") }
    var verifyingBuild by remember { mutableStateOf(false) }
    var showKeyDialog by remember { mutableStateOf(false) }
    var keyDialogInitialTab by remember { mutableStateOf(0) }
    var showRepoDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var isOfflineMode by remember { mutableStateOf(OnDeviceModelManager.isOfflineModeActive(context)) }
    var activeLocalModel by remember { mutableStateOf(OnDeviceModelManager.getSelectedModel(context)) }
    var showOnDeviceDialog by remember { mutableStateOf(false) }
    var executionJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var lastExecutedPrompt by remember { mutableStateOf("") }
    var showModelPicker by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf("") }
    var applyingDiffPath by remember { mutableStateOf<String?>(null) }

    val TAG = "AiStudioAgentScreen"

    var missionAutoSwitched by remember { mutableStateOf(false) }

    fun saveMissionState() {
        currentMission?.let { mission ->
            val json = org.json.JSONObject().apply {
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

    fun restoreMissionState(): AgentMissionSession? {
        val json = prefs.getString("active_mission_json", null)
        if (json.isNullOrBlank()) return null
        return try {
            val obj = org.json.JSONObject(json)
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
            AgentMissionSession(
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
            null
        }
    }

    fun exportMissionJson(): String {
        return currentMission?.let { mission ->
            org.json.JSONObject().apply {
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

    fun importMissionFromJson(jsonString: String): Boolean {
        return try {
            val obj = org.json.JSONObject(jsonString)
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
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import mission: ${e.message}")
            false
        }
    }

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

    val runtime = remember { Runtime.getRuntime() }
    var liveUsedMemoryMb by remember {
        mutableStateOf((runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024))
    }
    val liveMaxMemoryMb = remember { runtime.maxMemory() / (1024 * 1024) }

    val refreshDiagnostics: () -> Unit = {
        coroutineScope.launch {
            isPinging = true
            liveUsedMemoryMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)
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

    LaunchedEffect(Unit) { refreshDiagnostics() }

    // 🔑 مزامنة النموذج المختار مع المزود النشط (يمنع إرسال نموذج Gemini لمزود OpenRouter مثلاً)
    LaunchedEffect(keyResolution.first) {
        val models = agentModelsFor(keyResolution.first)
        if (selectedModel.isBlank() || selectedModel !in models) {
            models.firstOrNull()?.let { selectedModel = it }
        }
    }

    LaunchedEffect(currentMission, isOfflineMode) {
        saveMissionState()
        if (isOfflineMode && currentMission != null && !OnDeviceModelManager.hasAnyInstalledModel(context)) {
            if (!missionAutoSwitched) {
                missionAutoSwitched = true
                saveMissionState()
                showKeyDialog = true
                keyDialogInitialTab = 1
            }
        } else if (missionAutoSwitched && OnDeviceModelManager.hasAnyInstalledModel(context)) {
            missionAutoSwitched = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30000)
            saveMissionState()
        }
    }

    // 🔑 استعادة المهمة النشطة عند فتح الشاشة (كانت تُحفظ ولا تُستعاد أبداً)
    LaunchedEffect(Unit) {
        if (currentMission == null) {
            val restored = restoreMissionState()
            if (restored != null) {
                currentMission = restored
                missionHistory = missionHistory + listOf(restored)
            }
        }
    }

    val activeEnginePair = remember { derivedStateOf { UniversalKeyBrain.getActiveEngine() } }
    val activeRepo = remember(context) { RepoVaultManager.getActiveRepo(context) }

    val quickPrompts = remember {
        listOf(
            "أضف شاشة جديدة لمواقيت الصلاة وبوصلة القبلة",
            "افحص أخطاء البناء والـ CI وصلح ملفات سير العمل",
            "حسن أداء مشغل الصوت واستهلاك الذاكرة في الخلفية",
            "ابدأ تشغيل بناء حزمة APK جديدة على GitHub Actions",
            "تدقيق معماري شامل لملفات التطبيق وحذف الأكواد المهملة"
        )
    }

    Scaffold(
        bottomBar = bottomBar,
        containerColor = Color(0xFF05070F),
        topBar = {
            GlassmorphicSurface(
                modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                backgroundColor = Color(0xFF0B1220).copy(alpha = 0.85f),
                borderColor = Color(0xFF22D3EE).copy(alpha = 0.25f)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF0F1626))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color(0xFF22D3EE))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "مركز الوكيل الذكي",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { showSettings = true },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0F1626))
                                .testTag("agent_open_settings")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "إعدادات الوكيل", tint = Color(0xFF8A94A6))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    val isLocalInstalled = OnDeviceModelManager.isModelInstalled(context, activeLocalModel)
                    val isHealthy = if (isOfflineMode) isLocalInstalled else geminiHealth.isConnected
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AgentStatusChip(
                            kind = if (isHealthy) AgentStatusKind.READY else AgentStatusKind.NEEDS_ACTION,
                            text = if (isOfflineMode) {
                                if (isLocalInstalled) "ON-DEVICE • ${activeLocalModel.name}" else "ON-DEVICE • يلزم التنزيل"
                            } else {
                                if (geminiHealth.isConnected) "LIVE • ${geminiHealth.latencyMs}ms" else "OFFLINE • يلزم مفتاح"
                            },
                            onClick = { if (isOfflineMode) showOnDeviceDialog = true else { keyDialogInitialTab = 0; showKeyDialog = true } }
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = when {
                                isExecuting && isTeamMode && teamProgressText.isNotBlank() -> teamProgressText
                                isExecuting -> "الوكيل يعمل ذاتياً..."
                                else -> "جاهز لتلقي الأمر"
                            },
                            fontSize = 12.sp,
                            color = if (isExecuting) Color(0xFF22D3EE) else Color(0xFF8A94A6),
                            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier.fillMaxWidth().height(1.dp).background(
                            Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFF22D3EE).copy(alpha = 0.7f), Color(0xFF22D3EE).copy(alpha = 0.7f), Color.Transparent))
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding).background(Color(0xFF05070F))
        ) {
            LiveTelemetryHudStrip(
                latencyMs = geminiHealth.latencyMs,
                isConnected = geminiHealth.isConnected,
                isPinging = isPinging,
                activeModelName = if (isOfflineMode) activeLocalModel.name else selectedModel,
                isOfflineMode = isOfflineMode,
                usedMemoryMb = liveUsedMemoryMb,
                maxMemoryMb = liveMaxMemoryMb,
                onRefresh = { refreshDiagnostics() },
                onModelClick = { if (isOfflineMode) showOnDeviceDialog = true else showModelPicker = true },
                onMemoryClick = {
                    System.gc()
                    liveUsedMemoryMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)
                    Toast.makeText(context, "تم تحسين الذاكرة الحية", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )

            AgentGeometricControlDeck(
                selectedIndex = topTab,
                onSelect = { topTab = it },
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (topTab) {
                    1 -> AgentLiveConsoleTab(
                        context = context,
                        scope = coroutineScope,
                        onJumpKeys = { keyDialogInitialTab = 0; showKeyDialog = true },
                        onJumpBuildCenter = { showRepoDialog = true }
                    )
                    2 -> UnifiedToolsAndReadinessSection(
                        context = context,
                        scope = coroutineScope,
                        onJumpKeys = { keyDialogInitialTab = 0; showKeyDialog = true },
                        onJumpBuildCenter = { showRepoDialog = true }
                    )
                    3 -> TaskHistoryLogTab(context)
                    else -> LazyColumn(
                        Modifier.fillMaxSize().cyberGridBackground().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item { Spacer(Modifier.height(4.dp)) }

                        // Container 1: Mode & Team
                        item {
                        AgentContainer(index = "01", title = "وضع التشغيل", icon = Icons.Default.Sensors, accent = Color(0xFF22D3EE)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isTeamMode) Color(0xFF10B981).copy(alpha = 0.22f) else Color(0xFF0F1626),
                                        border = BorderStroke(1.dp, if (isTeamMode) Color(0xFF10B981).copy(alpha = 0.7f) else Color(0xFF1E2D4A)),
                                        modifier = Modifier.clickable { isTeamMode = !isTeamMode }.testTag("agent_toggle_team_mode")
                                    ) {
                                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Groups, contentDescription = null, tint = if (isTeamMode) Color(0xFF10B981) else Color(0xFF8A94A6), modifier = Modifier.size(13.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = if (isTeamMode) "فريق متعدد" else "وضع فردي",
                                                fontSize = 12.sp, fontFamily = CairoFont, fontWeight = FontWeight.Bold,
                                                color = if (isTeamMode) Color(0xFF10B981) else Color(0xFF8A94A6)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth().background(Color(0xFF05070F), RoundedCornerShape(8.dp)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (!isOfflineMode) Color(0xFF22D3EE).copy(alpha = 0.25f) else Color.Transparent,
                                    border = if (!isOfflineMode) BorderStroke(1.dp, Color(0xFF22D3EE)) else null,
                                    modifier = Modifier.weight(1f).clickable { isOfflineMode = false; OnDeviceModelManager.setOfflineModeActive(context, false) }
                                ) {
                                    Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Cloud, contentDescription = null, tint = if (!isOfflineMode) Color(0xFF22D3EE) else Color(0xFF8A94A6), modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("سحابي", fontSize = 12.sp, fontWeight = if (!isOfflineMode) FontWeight.Bold else FontWeight.Normal, color = if (!isOfflineMode) Color(0xFF22D3EE) else Color(0xFF8A94A6))
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isOfflineMode) Color(0xFF22D3EE).copy(alpha = 0.25f) else Color.Transparent,
                                    border = if (isOfflineMode) BorderStroke(1.dp, Color(0xFF22D3EE)) else null,
                                    modifier = Modifier.weight(1f).clickable { isOfflineMode = true; OnDeviceModelManager.setOfflineModeActive(context, true) }
                                ) {
                                    Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Memory, contentDescription = null, tint = if (isOfflineMode) Color(0xFF22D3EE) else Color(0xFF8A94A6), modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("محلي", fontSize = 12.sp, fontWeight = if (isOfflineMode) FontWeight.Bold else FontWeight.Normal, color = if (isOfflineMode) Color(0xFF22D3EE) else Color(0xFF8A94A6))
                                    }
                                }
                            }
                            if (isOfflineMode) {
                                Spacer(Modifier.height(8.dp))
                                val isLocalInstalled = OnDeviceModelManager.isModelInstalled(context, activeLocalModel)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isLocalInstalled) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFFF59E0B).copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, if (isLocalInstalled) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFFF59E0B).copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth().clickable { showOnDeviceDialog = true }
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("النموذج: ${activeLocalModel.name}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            Text(
                                                text = if (isLocalInstalled) "جاهز" else "يلزم التنزيل",
                                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                                color = if (isLocalInstalled) Color(0xFF10B981) else Color(0xFFF59E0B),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp).background(
                                                    if (isLocalInstalled) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFF59E0B).copy(alpha = 0.2f),
                                                    RoundedCornerShape(4.dp)
                                                )
                                            )
                                        }
                                    }
                                }
                            } else {
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    HealthPill(
                                        name = "Gemini",
                                        isConnected = geminiHealth.isConnected,
                                        latencyMs = geminiHealth.latencyMs,
                                        detail = geminiHealth.statusDetail,
                                        onClick = { keyDialogInitialTab = 0; showKeyDialog = true },
                                        modifier = Modifier.weight(1f)
                                    )
                                    HealthPill(
                                        name = "GitHub",
                                        isConnected = githubHealth.isConnected,
                                        latencyMs = githubHealth.latencyMs,
                                        detail = githubHealth.statusDetail,
                                        onClick = { showRepoDialog = true },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        }

                        // Container 2: Specialization
                        item {
                        AgentContainer(index = "02", title = "تخصص الوكيل", icon = Icons.Default.Psychology, accent = Color(0xFF8B5CF6)) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                items(AgentSpecialization.entries.toTypedArray()) { spec ->
                                    val isSelected = selectedSpec == spec
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) spec.color.copy(alpha = 0.2f) else Color(0xFF070B16),
                                        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) spec.color else Color(0xFF1E2D4A)),
                                        modifier = Modifier.heightIn(min = 48.dp).clickable { selectedSpec = spec }.wrapContentWidth()
                                    ) {
                                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(spec.icon, contentDescription = null, tint = spec.color, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                text = spec.title, fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color.White else Color(0xFF8A94A6), maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        }

                        // Container 3: Prompt & Execute
                        item {
                        AgentContainer(index = "03", title = "أمر الوكيل", icon = Icons.Default.Terminal, accent = Color(0xFF10B981)) {
                            OutlinedTextField(
                                value = promptInput,
                                onValueChange = { promptInput = it },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 140.dp).testTag("agent_studio_prompt_input"),
                                placeholder = { Text("اكتب أمرك هنا...", color = Color(0xFF64748B), fontSize = 12.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF22D3EE), unfocusedBorderColor = Color(0xFF1E2D4A),
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color(0xFF22D3EE)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                enabled = !isExecuting,
                                maxLines = 6
                            )
                            Spacer(Modifier.height(8.dp))
                            if (!isOfflineMode) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                    item {
                                        OutlinedButton(
                                            onClick = { showModelPicker = true },
                                            modifier = Modifier.height(48.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, Color(0xFF22D3EE).copy(alpha = 0.5f)),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE).copy(alpha = 0.1f), contentColor = Color(0xFF22D3EE))
                                        ) {
                                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(selectedModel.take(20), fontSize = 12.sp, fontFamily = CairoFont, maxLines = 1)
                                        }
                                    }
                                    item {
                                        OutlinedButton(
                                            onClick = onOpenTeamConfig,
                                            modifier = Modifier.height(48.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f)),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6).copy(alpha = 0.1f), contentColor = Color(0xFF8B5CF6))
                                        ) {
                                            Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("فريق النماذج", fontSize = 12.sp, fontFamily = CairoFont, maxLines = 1)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                            Text(
                                text = "أوامر سريعة:",
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF8A94A6)
                            )
                            Spacer(Modifier.height(4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                items(quickPrompts) { qp ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF0F1626),
                                        border = BorderStroke(1.dp, Color(0xFF1E2D4A)),
                                        modifier = Modifier.clickable(enabled = !isExecuting) { promptInput = qp }.wrapContentWidth()
                                    ) {
                                        Text(qp.take(30) + "...", fontSize = 12.sp, color = Color(0xFFE6EDF7), modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), maxLines = 1)
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            val isLocalReady = isOfflineMode && OnDeviceModelManager.isModelInstalled(context, activeLocalModel)
                            val isCloudReady = !isOfflineMode && keyResolution.third != KeySource.NONE
                            val canExecute = if (isOfflineMode) isLocalReady else isCloudReady
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (isExecuting) {
                                    Button(
                                        onClick = { executionJob?.cancel(); executionJob = null; isExecuting = false; Toast.makeText(context, "تم إلغاء المهمة", Toast.LENGTH_SHORT).show() },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("إيقاف", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            if (isOfflineMode && !isLocalReady) { showOnDeviceDialog = true; return@Button }
                                            if (!isOfflineMode && !isCloudReady) { showKeyDialog = true; return@Button }
                                            if (isTeamMode && isOfflineMode) { Toast.makeText(context, "وضع الفريق يتطلب اتصالاً سحابياً", Toast.LENGTH_LONG).show(); return@Button }
                                            if (isTeamMode && RepoVaultManager.getActiveRepo(context) == null) { showRepoDialog = true; Toast.makeText(context, "اربط مستودع GitHub أولاً", Toast.LENGTH_LONG).show(); return@Button }
                                            val prompt = promptInput.trim()
                                            if (prompt.isBlank()) { Toast.makeText(context, "اكتب أمراً أولاً", Toast.LENGTH_SHORT).show(); return@Button }
                                            lastExecutedPrompt = prompt
                                            isExecuting = true
                                            val job = coroutineScope.launch {
                                                try {
                                                    if (isTeamMode) {
                                                        val mission = executeAgentMissionMultiModel(context, prompt, selectedSpec, onUpdate = { currentMission = it }, onTeamProgress = { teamProgressText = it })
                                                        currentMission = mission
                                                        missionHistory = listOf(mission) + missionHistory
                                                    } else {
                                                        executeAgentMission(context, prompt, selectedSpec, activeRepo, selectedModel, { currentMission = it }, { finalMission ->
                                                            currentMission = finalMission
                                                            missionHistory = listOf(finalMission) + missionHistory
                                                        })
                                                    }
                                                } catch (_: kotlinx.coroutines.CancellationException) {
                                                } finally {
                                                    isExecuting = false
                                                    executionJob = null
                                                    promptInput = ""
                                                    teamProgressText = ""
                                                }
                                            }
                                            executionJob = job
                                        },
                                        enabled = !isExecuting,
                                        modifier = Modifier.weight(1f).height(48.dp).testTag("agent_studio_execute_btn"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (!canExecute) Color(0xFFEF4444) else if (isOfflineMode) Color(0xFF22D3EE) else Color(0xFF22D3EE),
                                            contentColor = if (!canExecute) Color.White else Color(0xFF05070F)
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (!canExecute) Icons.Default.Key else if (isOfflineMode) Icons.Default.Memory else Icons.AutoMirrored.Filled.Send,
                                            contentDescription = null, modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (!canExecute) "يلزم مفتاح" else if (isOfflineMode) "تنفيذ محلي" else "تنفيذ المهمة",
                                            fontWeight = FontWeight.Bold, fontSize = 12.sp
                                        )
                                    }
                                }
                                if (lastExecutedPrompt.isNotBlank() && !isExecuting) {
                                    OutlinedButton(
                                        onClick = {
                                            val prompt = lastExecutedPrompt
                                            promptInput = prompt
                                            isExecuting = true
                                            val job = coroutineScope.launch {
                                                try {
                                                    if (isTeamMode) {
                                                        val mission = executeAgentMissionMultiModel(context, prompt, selectedSpec, onUpdate = { currentMission = it }, onTeamProgress = { teamProgressText = it })
                                                        currentMission = mission
                                                        missionHistory = listOf(mission) + missionHistory
                                                    } else {
                                                        executeAgentMission(context, prompt, selectedSpec, activeRepo, selectedModel, { currentMission = it }, { finalMission ->
                                                            currentMission = finalMission
                                                            missionHistory = listOf(finalMission) + missionHistory
                                                        })
                                                    }
                                                } catch (_: kotlinx.coroutines.CancellationException) {
                                                } finally {
                                                    isExecuting = false
                                                    executionJob = null
                                                    teamProgressText = ""
                                                }
                                            }
                                            executionJob = job
                                        },
                                        modifier = Modifier.height(46.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("إعادة", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        }

                        // Container 4: Active Mission
                        currentMission?.let { mission ->
                            item {
                                AgentContainer(title = "المهمة الحالية", icon = Icons.Default.Rocket, accent = Color(0xFF8B5CF6)) {
                                    LiveMissionExecutionCard(
                                        mission = mission,
                                        teamProgress = teamProgressText,
                                        isTeamMode = isTeamMode,
                                        verifyingBuild = verifyingBuild,
                                        onVerifyBuild = {
                                            if (activeRepo == null || activeRepo.token.isBlank()) {
                                                Toast.makeText(context, "اربط مستودع GitHub أولاً", Toast.LENGTH_LONG).show()
                                            } else if (!verifyingBuild) {
                                                verifyingBuild = true
                                                coroutineScope.launch {
                                                    try {
                                                        val gate = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { fetchTeamBuildFeedback(context) }
                                                        when (gate.state) {
                                                            BuildVerificationEngine.BuildGateState.PASSED -> {
                                                                currentMission = mission.copy(buildTriggered = true, status = AgentTaskStatus.COMPLETED)
                                                                Toast.makeText(context, "البناء ناجح", Toast.LENGTH_LONG).show()
                                                            }
                                                            BuildVerificationEngine.BuildGateState.PENDING -> Toast.makeText(context, "البناء قيد التشغيل — انتظر", Toast.LENGTH_LONG).show()
                                                            BuildVerificationEngine.BuildGateState.FAILED -> Toast.makeText(context, "فشل البناء — يعيد الفريق الإصلاح", Toast.LENGTH_LONG).show()
                                                            else -> Toast.makeText(context, "تعذر تحديد حالة البناء", Toast.LENGTH_LONG).show()
                                                        }
                                                    } catch (_: kotlinx.coroutines.CancellationException) {
                                                    } finally { verifyingBuild = false }
                                                }
                                            }
                                        },
                                        onTriggerBuild = {
                                            val base = currentMission ?: mission
                                            coroutineScope.launch { runBuildVerification(context, base) { currentMission = it } }
                                        },
                                        onApplyDiff = { diff ->
                                            if (activeRepo == null || activeRepo.token.isBlank()) {
                                                Toast.makeText(context, "اربط مستودع GitHub أولاً", Toast.LENGTH_LONG).show()
                                            } else {
                                                applyingDiffPath = diff.filePath
                                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                    val client = GitHubRepoClient(activeRepo.owner, activeRepo.repo, activeRepo.token)
                                                    // 🔑 نكتب المقترح في مجلد منفصل — لا نستبدل ملف الكود الأصلي بمقتطف جزئي
                                                    val safePath = AgentPatchKit.proposalPath(diff.filePath)
                                                    val ok = client.updateFile(safePath, activeRepo.defaultBranch.ifBlank { "main" }, "chore(agent): save proposal for ${diff.filePath.substringAfterLast('/')}", diff.diffText)
                                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                        applyingDiffPath = null
                                                        if (ok) {
                                                            val pushed = (currentMission ?: mission).copy(appliedPaths = (currentMission ?: mission).appliedPaths + safePath)
                                                            currentMission = pushed
                                                            Toast.makeText(context, "تم حفظ المقترح في المستودع (مجلد qabas-agent-proposals)", Toast.LENGTH_LONG).show()
                                                        } else {
                                                            Toast.makeText(context, "تعذر الحفظ — تأكد من الصلاحيات والفرع", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        onDiscardDiff = { diff ->
                                            val base = currentMission ?: mission
                                            currentMission = base.copy(proposedDiffs = base.proposedDiffs.filterNot { it.filePath == diff.filePath })
                                            Toast.makeText(context, "تم التجاهل", Toast.LENGTH_SHORT).show()
                                        },
                                        applyingPath = applyingDiffPath
                                    )
                                }
                            }
                        }

                        // Container 5: History
                        if (missionHistory.isNotEmpty()) {
                            item {
                                AgentContainer(index = "05", title = "سجل العمليات", icon = Icons.Default.History, accent = Color(0xFF8A94A6)) {
                                    missionHistory.take(5).forEach { itemMission ->
                                        HistoricalMissionCard(itemMission)
                                        Spacer(Modifier.height(6.dp))
                                    }
                                }
                            }
                        }

                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }

    if (showKeyDialog) {
        ApiKeySetupDialog(
            context = context, initialTab = keyDialogInitialTab,
            onKeySaved = { refreshDiagnostics() },
            onOpenOnDeviceModels = { showOnDeviceDialog = true },
            onExportMission = { exportMissionJson() },
            onImportMission = { val json = prefs.getString("active_mission_json", "") ?: ""; if (json.isNotBlank()) importMissionFromJson(json) },
            onDismiss = { showKeyDialog = false }
        )
    }
    if (showRepoDialog) { RepoSetupDialog(context = context, activeRepo = activeRepo, onDismiss = { showRepoDialog = false }) }
    if (showSettings) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = sheetState,
            containerColor = Color(0xFF0B1220),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF1E2D4A)) }
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF22D3EE), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("إعدادات الوكيل", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showSettings = false }) {
                        Text("إغلاق", color = Color(0xFF22D3EE), fontFamily = CairoFont, fontSize = 12.sp)
                    }
                }
                Text(
                    "نموذج التنفيذ، حد الجولات، والإصلاح الذاتي — تُحفظ فوراً وتُطبّق على التشغيل القادم.",
                    color = Color(0xFF8A94A6), fontFamily = CairoFont, fontSize = 12.sp
                )
                AgentKernelSettingsCard(context = context)
                AgentNeonCard(accent = Color(0xFF8B5CF6)) {
                    AgentSectionHeader(index = "02", title = "مفاتيح ومستودع", accent = Color(0xFF8B5CF6))
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AgentNeonButton(text = "مفاتيح API", onClick = { showSettings = false; keyDialogInitialTab = 0; showKeyDialog = true }, accent = Color(0xFF8B5CF6), icon = Icons.Default.VpnKey)
                        AgentNeonButton(text = "ربط المستودع", onClick = { showSettings = false; showRepoDialog = true }, accent = Color(0xFF22D3EE), icon = Icons.Default.Source)
                    }
                }
            }
        }
    }
    if (showOnDeviceDialog) {
        OnDeviceModelDownloadDialog(
            context = context,
            onModelActivated = { isOfflineMode = OnDeviceModelManager.isOfflineModeActive(context); activeLocalModel = OnDeviceModelManager.getSelectedModel(context) },
            onDismiss = { showOnDeviceDialog = false }
        )
    }
    if (showModelPicker) {
        val provider = AgentConnectivityEngine.resolveKeyWithSource(context).first
        val models = agentModelsFor(provider)
        AlertDialog(
            onDismissRequest = { showModelPicker = false },
            title = { Text("اختر النموذج", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    models.forEach { m ->
                        Text(text = m, color = Color.White, fontFamily = CairoFont, modifier = Modifier.fillMaxWidth().padding(14.dp).clickable { selectedModel = m; showModelPicker = false }.background(if (selectedModel == m) Color(0xFF22D3EE).copy(alpha = 0.15f) else Color.Transparent))
                    }
                }
            },
            dismissButton = { TextButton(onClick = { showModelPicker = false }) { Text("إغلاق", color = Color(0xFF22D3EE), fontFamily = CairoFont) } },
            confirmButton = {},
            containerColor = Color(0xFF0B1220)
        )
    }
    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("تأكيد الخروج", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold) },
            text = { Text("هل تريد حقاً الخروج من التطبيق؟", color = Color(0xFFE2E8F0), fontFamily = NotoSansFont) },
            confirmButton = {
                TextButton(onClick = { showExitConfirmation = false; onBack() }) { Text("نعم، خروج", color = Color(0xFFEF4444), fontFamily = CairoFont, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) { Text("إلغاء", color = Color(0xFF8A94A6), fontFamily = CairoFont) }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        )
    }
}

@Composable
private fun AgentContainer(
    title: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    index: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xEE0B1220),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index != null) {
                    Text(
                        "[$index]",
                        color = accent,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF7), maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.fillMaxWidth().height(1.dp).background(
                    Brush.horizontalGradient(listOf(accent.copy(alpha = 0.5f), accent.copy(alpha = 0.1f), Color.Transparent))
                )
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun HealthPill(
    name: String,
    isConnected: Boolean,
    latencyMs: Long,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isConnected) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFFF59E0B).copy(alpha = 0.12f),
        border = BorderStroke(1.dp, if (isConnected) Color(0xFF10B981).copy(alpha = 0.45f) else Color(0xFFF59E0B).copy(alpha = 0.45f)),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (isConnected) Color(0xFF10B981) else Color(0xFFF59E0B)))
                Spacer(Modifier.width(5.dp))
                Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.weight(1f))
                if (latencyMs > 0) Text("${latencyMs}ms", fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = if (isConnected) Color(0xFF10B981) else Color(0xFFF59E0B))
            }
            Spacer(Modifier.height(3.dp))
            Text(detail.take(50), fontSize = 12.sp, color = Color(0xFFE6EDF7), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun UnifiedToolsAndReadinessSection(
    context: Context,
    scope: CoroutineScope,
    onJumpKeys: () -> Unit,
    onJumpBuildCenter: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var subTab by remember { mutableStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF05070F))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0A101E))
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF22D3EE).copy(alpha = 0.4f),
                                Color(0xFF10B981).copy(alpha = 0.4f)
                            )
                        )
                    ),
                    RoundedCornerShape(10.dp)
                )
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (subTab == 0) Color(0xFF22D3EE).copy(alpha = 0.22f) else Color.Transparent,
                border = if (subTab == 0) BorderStroke(1.dp, Color(0xFF22D3EE).copy(alpha = 0.7f)) else null,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable { subTab = 0 }
                    .testTag("sub_tab_readiness")
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (subTab == 0) Color(0xFF22D3EE) else Color(0xFF8A94A6),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "فحص الجاهزية والربط",
                        fontSize = 12.sp,
                        fontFamily = CairoFont,
                        fontWeight = if (subTab == 0) FontWeight.Bold else FontWeight.Medium,
                        color = if (subTab == 0) Color.White else Color(0xFF8A94A6),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (subTab == 1) Color(0xFF10B981).copy(alpha = 0.22f) else Color.Transparent,
                border = if (subTab == 1) BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.7f)) else null,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable { subTab = 1 }
                    .testTag("sub_tab_tools")
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Handyman,
                        contentDescription = null,
                        tint = if (subTab == 1) Color(0xFF10B981) else Color(0xFF8A94A6),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "ترسانة الأدوات والـ Git",
                        fontSize = 12.sp,
                        fontFamily = CairoFont,
                        fontWeight = if (subTab == 1) FontWeight.Bold else FontWeight.Medium,
                        color = if (subTab == 1) Color.White else Color(0xFF8A94A6),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (subTab == 0) {
                RequirementsTab(
                    context = context,
                    scope = scope,
                    onJumpBuildCenter = onJumpBuildCenter,
                    onJumpKeys = onJumpKeys
                )
            } else {
                ToolsTab(context = context)
            }
        }
    }
}
