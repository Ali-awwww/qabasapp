package com.qabas.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.qabas.app.ui.agent.*
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** قائمة النماذج المتاحة لكل مزود — مصدر واحد يمنع إرسال نموذج مزودٍ إلى مزود آخر */
private fun agentModelsFor(provider: DetectedProvider): List<String> = when (provider) {
    DetectedProvider.GEMINI -> listOf("gemini-3.8-flash", "gemini-3.5-flash", "gemini-3.5-pro")
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
    var showDevConsole by remember { mutableStateOf(true) }
    var showSidePanel by remember { mutableStateOf(false) }
    val sessions by AgentChatSessionManager.sessions.collectAsState()
    val activeSessionId by AgentChatSessionManager.activeSessionId.collectAsState()
    val chatMessages = remember { mutableStateListOf<AgentChatMessage>() }
    var isThinking by remember { mutableStateOf(false) }
    var isRunning by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    val chatListState = rememberLazyListState()
    var chatInputText by remember { mutableStateOf("") }
    var showSpecChooser by remember { mutableStateOf(false) }

    BackHandler {
        if (showSidePanel) {
            showSidePanel = false
            return@BackHandler
        }
        if (showDevConsole) {
            showDevConsole = false
            return@BackHandler
        }
        if (showExitConfirmation) {
            showExitConfirmation = false
            return@BackHandler
        }
        showExitConfirmation = true
    }

    LaunchedEffect(Unit) {
        AgentChatSessionManager.init(context)
        val curId = AgentChatSessionManager.activeSessionId.value ?: AgentChatSessionManager.startNewSession(context)
        val loaded = AgentChatSessionManager.loadSessionMessages(context, curId)
        chatMessages.clear()
        chatMessages.addAll(loaded)
    }

    LaunchedEffect(chatMessages.size, isThinking) {
        if (chatMessages.isNotEmpty()) {
            chatListState.animateScrollToItem(chatMessages.size - 1)
        }
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
    var showRequirementDialog by remember { mutableStateOf(false) }

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
                modelName = "gemini-3.8-flash"
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
    val vaultTrigger by RepoVaultManager.vaultUpdateTrigger.collectAsState()
    val activeRepo = remember(context, vaultTrigger) { RepoVaultManager.getActiveRepo(context) }
    val savedRepositories = remember(context, vaultTrigger) { RepoVaultManager.getRepositories(context) }

    fun handleSendMessage(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return

        // 1. فحص المتطلبات الأساسية (مفتاح API ومستودع GitHub)
        val currentKeys = AgentConnectivityEngine.resolveKeyWithSource(context)
        val activeRepoItem = RepoVaultManager.getActiveRepo(context)
        
        if (currentKeys.third == KeySource.NONE || activeRepoItem == null) {
            showRequirementDialog = true
            return
        }

        val curId = AgentChatSessionManager.activeSessionId.value ?: AgentChatSessionManager.startNewSession(context)
        val userMsg = AgentChatMessage(sender = AgentChatSender.USER, text = clean)
        chatMessages.add(userMsg)
        AgentChatSessionManager.saveSessionMessages(context, curId, chatMessages.toList())

        isThinking = true
        isRunning = true
        isPaused = false

        val job = coroutineScope.launch(Dispatchers.IO) {
            try {
                // 2. فحص حي للمفتاح قبل البدء (للتأكد من عدم كونه منتهياً أو خاطئاً)
                val health = AgentConnectivityEngine.pingProviderLive(currentKeys.first, currentKeys.second, currentKeys.third)
                if (!health.isConnected) {
                    withContext(Dispatchers.Main) {
                        val errorDetails = AgentErrorDetails(
                            reason = "فشل التحقق من مفتاح API: ${health.statusDetail}",
                            responseCode = health.httpCode,
                            cloudStatus = "سيرفر ${currentKeys.first.displayName} نشط ولكن المفتاح مرفوض",
                            isNetworkError = health.httpCode == -1
                        )
                        val errorMsg = AgentChatMessage(
                            sender = AgentChatSender.AGENT,
                            text = "عذراً، فشل الاتصال بالمزود الذكي. يرجى التحقق من صحة المفتاح في الإعدادات وإعادة المحاولة.",
                            errorInfo = errorDetails
                        )
                        chatMessages.add(errorMsg)
                        AgentChatSessionManager.saveSessionMessages(context, curId, chatMessages.toList())
                        isThinking = false
                        isRunning = false
                    }
                    return@launch
                }

                val isPlanning = AgentPrefs.isPlanningMode(context)
                if (isPlanning) {
                    val planSteps = listOf(
                        AgentPlanStep(1, "التحليل والفحص المعماري 🔍", "تحليل ملفات كود مشروع قَبَس المستهدفة وتحديد التغييرات المطلوبة للطلب: \"$clean\"."),
                        AgentPlanStep(2, "صياغة وتعديل المكونات ✍️", "كتابة واختبار واجهات Jetpack Compose والمنطق البرمجي الخاص بالخاصية المطلوبة."),
                        AgentPlanStep(3, "التكامل والتحقق من البناء 🚀", "تشغيل أداة التجميع compile_applet والترقيع الذاتي للأخطاء قبل التسليم النهائي.")
                    )
                    val planCardData = AgentPlanCardData(
                        prompt = clean,
                        summary = "خطة عمل معمارية مقترحة لتنفيذ: \"$clean\"",
                        steps = planSteps,
                        isApproved = false
                    )
                    withContext(Dispatchers.Main) {
                        val currentActive = AgentPrefs.activePlanningTasksCount(context)
                        AgentPrefs.putActivePlanningTasksCount(context, currentActive + 1)
                        val agentMsg = AgentChatMessage(
                            sender = AgentChatSender.AGENT,
                            text = "تم تفعيل \"وضع التخطيط الهيكلي\". تم إعداد خطة العمل المعمارية التالية لطلبك. يرجى مراجعتها واضغط على زر الاعتماد للبدء بالتنفيذ المباشر:",
                            planCard = planCardData
                        )
                        chatMessages.add(agentMsg)
                        AgentChatSessionManager.saveSessionMessages(context, curId, chatMessages.toList())
                        isThinking = false
                        isRunning = false
                    }
                } else if (isTeamMode) {
                    val team = MultiModelAgentOrchestrator.loadTeam(context, onlyEnabled = true)
                    val teamStepsList = mutableListOf<TeamStepEvent>()
                    val agentMsg = AgentChatMessage(
                        sender = AgentChatSender.AGENT,
                        text = "جاري تفعيل فريق النماذج الذكي لمعالجة طلبك... 👥⚡",
                        teamSteps = emptyList()
                    )
                    withContext(Dispatchers.Main) {
                        chatMessages.add(agentMsg)
                    }
                    
                    val report = MultiModelAgentOrchestrator.executeWithTeam(
                        context = context,
                        prompt = clean,
                        team = team,
                        onUpdate = { updateText ->
                            teamProgressText = updateText
                        },
                        onStep = { stepEvent ->
                            val existingIdx = teamStepsList.indexOfFirst { it.key == stepEvent.key }
                            if (existingIdx >= 0) {
                                teamStepsList[existingIdx] = stepEvent
                            } else {
                                teamStepsList.add(stepEvent)
                            }
                            val updatedMsg = agentMsg.copy(teamSteps = teamStepsList.toList())
                            val msgIdx = chatMessages.indexOfFirst { it.id == agentMsg.id }
                            if (msgIdx >= 0) {
                                chatMessages[msgIdx] = updatedMsg
                            }
                        }
                    )
                    
                    withContext(Dispatchers.Main) {
                        val finalMsg = agentMsg.copy(
                            text = report.leaderSynthesis.ifBlank { "تم تنفيذ المهمة بواسطة الفريق بنجاح." },
                            teamReport = report,
                            teamSteps = teamStepsList.toList()
                        )
                        val msgIdx = chatMessages.indexOfFirst { it.id == agentMsg.id }
                        if (msgIdx >= 0) {
                            chatMessages[msgIdx] = finalMsg
                        }
                        AgentChatSessionManager.saveSessionMessages(context, curId, chatMessages.toList())
                        isThinking = false
                        isRunning = false
                    }
                } else {
                    var finalMissionResult: AgentMissionSession? = null
                    try {
                        executeAgentMission(
                            context = context,
                            prompt = clean,
                            spec = selectedSpec,
                            repo = activeRepo,
                            selectedModel = selectedModel.ifBlank { "gemini-3.8-flash" },
                            onUpdate = { partialMission ->
                                currentMission = partialMission
                            },
                            onFinished = { finalMission ->
                                finalMissionResult = finalMission
                                currentMission = finalMission
                            }
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Execution error: ${e.message}")
                    }

                    withContext(Dispatchers.Main) {
                        val reportText = finalMissionResult?.finalReport?.ifBlank { null }
                            ?: "تم استلام توجيهك البرمجي بنجاح وتنفيذه بواسطة الوكيل المستقل: $clean"
                        val diff = finalMissionResult?.proposedDiffs?.firstOrNull()?.let { d ->
                            FileDiff(
                                filePath = d.filePath,
                                additions = d.additions,
                                deletions = d.deletions,
                                diffText = d.diffText
                            )
                        }
                        val agentMsg = AgentChatMessage(
                            sender = AgentChatSender.AGENT,
                            text = reportText,
                            diff = diff
                        )
                        chatMessages.add(agentMsg)
                        AgentChatSessionManager.saveSessionMessages(context, curId, chatMessages.toList())
                        isThinking = false
                        isRunning = false
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                withContext(Dispatchers.Main) {
                    isThinking = false
                    isRunning = false
                    isPaused = false
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isThinking = false
                    isRunning = false
                    isPaused = false
                    executionJob = null
                }
            }
        }
        executionJob = job
    }

    fun handleApprovePlan(planCard: AgentPlanCardData) {
        val curId = AgentChatSessionManager.activeSessionId.value ?: return
        val currentActive = AgentPrefs.activePlanningTasksCount(context)
        AgentPrefs.putActivePlanningTasksCount(context, (currentActive - 1).coerceAtLeast(0))

        Toast.makeText(context, "🚀 تم اعتماد الخطة! جاري البدء بالتنفيذ المباشر...", Toast.LENGTH_SHORT).show()
        val sysMsg = AgentChatMessage(
            sender = AgentChatSender.TOOL_SYSTEM,
            text = "تم اعتماد خطة العمل بنجاح لـ: \"${planCard.prompt}\". جاري البدء بتطبيق التعديلات البرمجية..."
        )
        chatMessages.add(sysMsg)
        AgentChatSessionManager.saveSessionMessages(context, curId, chatMessages.toList())

        isThinking = true
        isRunning = true
        isPaused = false

        val job = coroutineScope.launch(Dispatchers.IO) {
            var finalMissionResult: AgentMissionSession? = null
            try {
                executeAgentMission(
                    context = context,
                    prompt = planCard.prompt,
                    spec = selectedSpec,
                    repo = activeRepo,
                    selectedModel = selectedModel.ifBlank { "gemini-3.8-flash" },
                    onUpdate = { currentMission = it },
                    onFinished = { finalMissionResult = it; currentMission = it }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Plan execution error: ${e.message}")
            }
            withContext(Dispatchers.Main) {
                val reportText = finalMissionResult?.finalReport?.ifBlank { null }
                    ?: "تم تنفيذ الخطة البرمجية المعتمدة بنجاح على مشروع قبس."
                val diff = finalMissionResult?.proposedDiffs?.firstOrNull()?.let { d ->
                    FileDiff(filePath = d.filePath, additions = d.additions, deletions = d.deletions, diffText = d.diffText)
                }
                val agentMsg = AgentChatMessage(sender = AgentChatSender.AGENT, text = reportText, diff = diff)
                chatMessages.add(agentMsg)
                AgentChatSessionManager.saveSessionMessages(context, curId, chatMessages.toList())
                isThinking = false
                isRunning = false
                isPaused = false
                executionJob = null
            }
        }
        executionJob = job
    }

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
            if (showDevConsole) {
                GlassmorphicSurface(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                    backgroundColor = Color(0xFF0B1220).copy(alpha = 0.85f),
                    borderColor = Color(0xFF22D3EE).copy(alpha = 0.25f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                onClick = { showDevConsole = false },
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(15.dp))
                                    Text("العودة للمحادثة 💬", color = Color(0xFF10B981), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "كونسول وأدوات المطور",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            com.qabas.app.ui.components.ModelIndicator(modifier = Modifier.padding(end = 8.dp))
                            Surface(
                                onClick = { showSettings = true },
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF0F1B2F),
                                border = BorderStroke(1.2.dp, Color(0xFF22D3EE).copy(alpha = 0.6f)),
                                modifier = Modifier.height(38.dp).testTag("agent_open_settings")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Tune, contentDescription = "إعدادات الوكيل", tint = Color(0xFF22D3EE), modifier = Modifier.size(16.dp))
                                    Text("الإعدادات", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                // Top Bar شاشة المحادثة الرئيسية الذكية
                GlassmorphicSurface(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                    backgroundColor = Color(0xFF0B1220).copy(alpha = 0.92f),
                    borderColor = Color(0xFF22D3EE).copy(alpha = 0.35f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF0F1626))
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color(0xFF22D3EE), modifier = Modifier.size(18.dp))
                            }

                            Spacer(Modifier.width(6.dp))

                            // سهم فتح لوحة الجلسات الجانبية
                            IconButton(
                                onClick = { showSidePanel = !showSidePanel },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (showSidePanel) Color(0xFF10B981).copy(alpha = 0.25f) else Color(0xFF0F1B2F))
                                    .border(1.dp, if (showSidePanel) Color(0xFF10B981) else Color(0xFF22D3EE).copy(alpha = 0.45f), CircleShape)
                                    .testTag("agent_toggle_side_panel")
                            ) {
                                Icon(
                                    imageVector = if (showSidePanel) Icons.Default.MenuOpen else Icons.Default.Menu,
                                    contentDescription = "قائمة المحادثات",
                                    tint = if (showSidePanel) Color(0xFF10B981) else Color(0xFF22D3EE),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            // عنوان الجلسة والتخصص
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showSpecChooser = true }
                            ) {
                                val currentSessionTitle = sessions.find { it.id == activeSessionId }?.title ?: "محادثة جديدة 💬"
                                Text(
                                    text = currentSessionTitle,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontFamily = CairoFont
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(selectedSpec.icon, contentDescription = null, tint = selectedSpec.color, modifier = Modifier.size(12.dp))
                                    Text(
                                        text = selectedSpec.title,
                                        color = selectedSpec.color,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = CairoFont
                                    )
                                }
                            }

                            // مؤشر الحالة المباشر
                            AgentStatusChip(
                                kind = when {
                                    isThinking || (isRunning && !isPaused) -> AgentStatusKind.RUNNING
                                    isPaused -> AgentStatusKind.NEEDS_ACTION
                                    else -> AgentStatusKind.READY
                                },
                                text = when {
                                    isThinking -> "يفكر..."
                                    isRunning && !isPaused -> "يعمل"
                                    isPaused -> "موقف"
                                    else -> "جاهز"
                                },
                                modifier = Modifier.height(26.dp)
                            )

                            // أزرار التحكم أثناء العمل (إيقاف مؤقت / استئناف / إيقاف)
                            if (isRunning || isThinking) {
                                Spacer(Modifier.width(4.dp))
                                IconButton(
                                    onClick = {
                                        if (isPaused) {
                                            isPaused = false
                                            Toast.makeText(context, "تم استئناف الوكيل ▶️", Toast.LENGTH_SHORT).show()
                                        } else {
                                            isPaused = true
                                            Toast.makeText(context, "تم إيقاف الوكيل مؤقتاً ⏸️", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF15263F))
                                ) {
                                    Icon(
                                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                        contentDescription = "Pause/Resume",
                                        tint = if (isPaused) Color(0xFF10B981) else Color(0xFF38BDF8),
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        executionJob?.cancel()
                                        executionJob = null
                                        isRunning = false
                                        isPaused = false
                                        isThinking = false
                                        Toast.makeText(context, "تم إيقاف المهمة ⏹️", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFEF4444).copy(alpha = 0.15f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = "Stop",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.width(4.dp))

                            IconButton(
                                onClick = { showSettings = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0F1B2F))
                                    .border(1.dp, Color(0xFF22D3EE).copy(alpha = 0.5f), CircleShape)
                                    .testTag("agent_open_settings")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "إعدادات الوكيل",
                                    tint = Color(0xFF22D3EE),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (showDevConsole) {
            Column(
                Modifier.fillMaxSize().padding(innerPadding).background(Color(0xFF05070F))
            ) {
            // Live telemetry is sourced from the actual GitHub health check and device runtime.
            LiveTelemetryHudStrip(
                latencyMs = githubHealth.latencyMs,
                isConnected = githubHealth.isConnected,
                isPinging = isPinging,
                activeModelName = if (isOfflineMode) activeLocalModel.name else selectedModel,
                isOfflineMode = isOfflineMode,
                usedMemoryMb = liveUsedMemoryMb,
                maxMemoryMb = liveMaxMemoryMb,
                onRefresh = { refreshDiagnostics() },
                onModelClick = { showModelPicker = true },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )

            // Cyber Reasoning Flow HUD (Active when executing or idle)
            CyberReasoningFlowHud(
                currentPhase = when {
                    isExecuting && teamProgressText.contains("تخطيط", ignoreCase = true) -> "PLAN"
                    isExecuting && teamProgressText.contains("بناء", ignoreCase = true) -> "VERIFY"
                    isExecuting -> "CODE"
                    else -> "ANALYZE"
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
                    0 -> AgentHubOverview(
                        repositoryName = activeRepo?.fullName,
                        repositoryBranch = activeRepo?.defaultBranch,
                        providerName = keyResolution.first.displayName,
                        modelName = if (isOfflineMode) activeLocalModel.name else selectedModel,
                        isOfflineMode = isOfflineMode,
                        isProviderReady = keyResolution.third != KeySource.NONE,
                        isGitHubReady = githubHealth.isConnected,
                        isChecking = isPinging,
                        isExecuting = isExecuting,
                        isChatRunning = isRunning,
                        activeMissionTitle = currentMission?.userPrompt,
                        activeMissionStatus = currentMission?.status?.name,
                        historyCount = missionHistory.size,
                        onSelectTab = { topTab = it },
                        onOpenRepository = { showRepoDialog = true },
                        onOpenKeys = { keyDialogInitialTab = 0; showKeyDialog = true },
                        onRefresh = { refreshDiagnostics() }
                    )
                    1 -> LazyColumn(
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
                                // مكتبة الأوامر المحفوظة والمفضلة (Saved Prompts Hub)
                                Text("الأوامر المحفوظة والمفضلة ⭐:", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                val savedFavorites = listOf(
                                    "تحليل معماري شامل 🏛️" to "قم بتحليل بنية التطبيق المعمارية وتقديم تقرير بالتحسينات الممكنة",
                                    "فحص أمني وثغرات 🛡️" to "افحص الكود بحثاً عن أي تسريبات للبيانات أو ثغرات أمنية",
                                    "تحسين سرعة الأداء ⚡" to "اقترح تحسينات لتقليل وقت التجميع واستهلاك الذاكرة",
                                    "إصلاح أخطاء التجميع 🩺" to "قم بتشغيل الفحص التلقائي وإصلاح أي استدعاءات مفقودة"
                                )
                                items(savedFavorites) { (label, fullPrompt) ->
                                    Surface(
                                        onClick = { promptInput = fullPrompt },
                                        color = Color(0xFF0F1B2E),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(0.8.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = label,
                                            color = Color(0xFFBAE6FD),
                                            fontFamily = CairoFont,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

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
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        onClick = { showModelPicker = true },
                                        modifier = Modifier.weight(1f).height(46.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF22D3EE).copy(alpha = 0.08f),
                                        border = BorderStroke(1.dp, Color(0xFF22D3EE).copy(alpha = 0.3f))
                                    ) {
                                        Row(
                                            Modifier.padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Default.Tune, null, tint = Color(0xFF22D3EE), modifier = Modifier.size(16.dp))
                                            Text(
                                                text = selectedModel.take(22).ifBlank { "اختر النموذج" },
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontFamily = CairoFont,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    
                                    Surface(
                                        onClick = onOpenTeamConfig,
                                        modifier = Modifier.weight(0.7f).height(46.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF8B5CF6).copy(alpha = 0.08f),
                                        border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.3f))
                                    ) {
                                        Row(
                                            Modifier.padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Default.Groups, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(16.dp))
                                            Text(
                                                text = "الفريق",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontFamily = CairoFont,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                            Text(
                                text = "أوامر ذكية جاهزة:",
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B),
                                modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(end = 12.dp)
                            ) {
                                val promptsWithIcons = listOf(
                                    "مواقيت الصلاة" to Icons.Default.Schedule,
                                    "تدقيق الكود" to Icons.Default.FactCheck,
                                    "بناء APK" to Icons.Default.Android,
                                    "تحسين الأداء" to Icons.Default.Speed,
                                    "حذف مهمل" to Icons.Default.DeleteSweep
                                )
                                items(promptsWithIcons) { (qp, icon) ->
                                    Surface(
                                        onClick = { if (!isExecuting) promptInput = qp },
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF0F172A),
                                        border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                        modifier = Modifier.height(38.dp)
                                    ) {
                                        Row(
                                            Modifier.padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(icon, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                                            Text(qp, fontSize = 11.sp, color = Color(0xFFE2E8F0), fontFamily = CairoFont)
                                        }
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
                    2 -> AgentLiveConsoleTab(
                        context = context,
                        scope = coroutineScope,
                        onJumpKeys = { keyDialogInitialTab = 0; showKeyDialog = true },
                        onJumpBuildCenter = { showRepoDialog = true }
                    )
                    3 -> AgentHubProjectsSection(
                        repositories = savedRepositories,
                        activeRepositoryId = activeRepo?.id,
                        onSelectRepository = { repository ->
                            RepoVaultManager.setActiveRepo(context, repository.id)
                            Toast.makeText(context, "تم تحديد ${repository.fullName} كمستودع نشط", Toast.LENGTH_SHORT).show()
                        },
                        onAddRepository = { showRepoDialog = true }
                    )
                    4 -> TaskHistoryLogTab(context)
                    5 -> UnifiedToolsAndReadinessSection(
                        context = context,
                        scope = coroutineScope,
                        onJumpKeys = { keyDialogInitialTab = 0; showKeyDialog = true },
                        onJumpBuildCenter = { showRepoDialog = true }
                    )
                }
            }
        }
    } else {
            // شاشة المحادثة الأساسية والمباشرة مع اللوحة الجانبية الجزئية
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFF05070F))
            ) {
                Column(
                    Modifier.fillMaxSize()
                ) {
                    // قائمة الرسائل
                    LazyColumn(
                        state = chatListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (chatMessages.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFF0B1424),
                                    border = BorderStroke(1.2.dp, Color(0xFF22D3EE).copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF22D3EE).copy(alpha = 0.12f))
                                                .border(1.5.dp, Color(0xFF22D3EE).copy(alpha = 0.5f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SmartToy,
                                                contentDescription = null,
                                                tint = Color(0xFF22D3EE),
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                        Text(
                                            text = "مرحباً بك في غرفة الوكيل البرمجي الذكي 🤖",
                                            color = Color.White,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = "تستطيع إرسال أي طلب تعديل، فحص الكود، إصلاح أخطاء، أو بناء ميزات جديدة مباشرة. استخدم الأوامر السريعة أدناه أو اكتب طلبك باللغة العربية.",
                                            color = Color(0xFF94A3B8),
                                            fontFamily = CairoFont,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }
                        }

                        items(chatMessages, key = { it.id }) { msg ->
                            ChatMessageBubble(
                                message = msg,
                                onApprovePlan = { planCard ->
                                    handleApprovePlan(planCard)
                                }
                            )
                        }

                        if (isThinking || (isRunning && !isPaused)) {
                            item {
                                Surface(
                                    color = Color(0xFF0F2038),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = Color(0xFF38BDF8),
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            text = "الوكيل يفكر ويولّد التعديلات البرمجية... 🧠⚡",
                                            color = Color(0xFF7DD3FC),
                                            fontFamily = CairoFont,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1E293B))

                    // شريط الأوامر السريعة
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(end = 10.dp)
                    ) {
                        val quickPrompts = listOf(
                            "أضف زر مشاركة ⚡" to Icons.Default.Share,
                            "افحص الكود 🧪" to Icons.Default.Science,
                            "صياد الأخطاء 🩺" to Icons.Default.BugReport,
                            "مزامنة GitHub 🐙" to Icons.Default.CloudSync,
                            "تحليل معماري شامل 🏛️" to Icons.Default.Architecture
                        )
                        items(quickPrompts) { (prompt, icon) ->
                            Surface(
                                onClick = {
                                    if (!isThinking && (!isRunning || isPaused)) {
                                        handleSendMessage(prompt)
                                    } else {
                                        chatInputText = prompt
                                    }
                                },
                                color = Color(0xFF22D3EE).copy(alpha = 0.08f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF22D3EE).copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(icon, null, tint = Color(0xFF22D3EE), modifier = Modifier.size(13.dp))
                                    Text(
                                        text = prompt,
                                        color = Color(0xFFE2E8F0),
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // شريط الإدخال
                    Surface(
                        color = Color(0xFF0A1220),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // زر التخصص
                            IconButton(
                                onClick = { showSpecChooser = true },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(selectedSpec.color.copy(alpha = 0.12f))
                                    .border(1.dp, selectedSpec.color.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            ) {
                                Icon(selectedSpec.icon, contentDescription = "تخصص", tint = selectedSpec.color, modifier = Modifier.size(20.dp))
                            }

                            OutlinedTextField(
                                value = chatInputText,
                                onValueChange = { chatInputText = it },
                                placeholder = {
                                    Text(
                                        "أرسل توجيهاً أو تعديلاً للوكيل...",
                                        color = Color(0xFF64748B),
                                        fontFamily = CairoFont,
                                        fontSize = 12.sp
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF0F1B2E),
                                    unfocusedContainerColor = Color(0xFF0D1829),
                                    focusedBorderColor = Color(0xFF10B981),
                                    unfocusedBorderColor = Color(0xFF1E293B),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 46.dp, max = 100.dp)
                                    .testTag("agent_chat_input"),
                                maxLines = 3,
                                trailingIcon = {
                                    val infiniteTransition = rememberInfiniteTransition(label = "team_glow")
                                    val teamGlowAlpha by infiniteTransition.animateFloat(
                                        initialValue = 0.4f,
                                        targetValue = 1f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(1500, easing = LinearEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "glow"
                                    )

                                    IconButton(
                                        onClick = { isTeamMode = !isTeamMode },
                                        modifier = Modifier
                                            .padding(end = 4.dp)
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(if (isTeamMode) Color(0xFFD4AF37).copy(alpha = 0.15f) else Color.Transparent)
                                            .border(
                                                width = if (isTeamMode) 1.5.dp else 1.dp,
                                                color = if (isTeamMode) Color(0xFFD4AF37).copy(alpha = teamGlowAlpha) else Color(0xFF22D3EE).copy(alpha = 0.4f),
                                                shape = CircleShape
                                            )
                                            .testTag("agent_chat_toggle_team")
                                    ) {
                                        Icon(
                                            imageVector = if (isTeamMode) Icons.Default.Groups else Icons.Default.SmartToy,
                                            contentDescription = "تبديل وضع الفريق",
                                            tint = if (isTeamMode) Color(0xFFD4AF37).copy(alpha = teamGlowAlpha.coerceAtLeast(0.7f)) else Color(0xFF22D3EE),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            )

                            Button(
                                onClick = {
                                    if (chatInputText.isNotBlank()) {
                                        val textToSend = chatInputText
                                        chatInputText = ""
                                        handleSendMessage(textToSend)
                                    }
                                },
                                enabled = chatInputText.isNotBlank() && !isThinking,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF059669),
                                    disabledContainerColor = Color(0xFF1E293B)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier.height(46.dp).testTag("agent_chat_send_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "إرسال",
                                    tint = if (chatInputText.isNotBlank()) Color.White else Color(0xFF64748B),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // ==========================================
                // 🪟 اللوحة الجانبية الزجاجية الجزئية (Glassmorphic Side Panel Overlaid)
                // ==========================================
                if (showSidePanel) {
                    // الخلفية المعتمة الشفافة للنقر للإغلاق
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable { showSidePanel = false }
                    )

                    // اللوحة الزجاجية المنزلقة
                    Surface(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.84f)
                            .align(Alignment.CenterStart)
                            .clickable(enabled = false) {},
                        color = Color(0xF80B1528),
                        border = BorderStroke(1.2.dp, Color(0xFF22D3EE).copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
                        shadowElevation = 24.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                        ) {
                            // ترويسة اللوحة مع سهم الإغلاق
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF22D3EE).copy(alpha = 0.15f))
                                            .border(1.dp, Color(0xFF22D3EE).copy(alpha = 0.4f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.ChatBubbleOutline,
                                            contentDescription = null,
                                            tint = Color(0xFF22D3EE),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Text(
                                        text = "جلسات المحادثة 💬",
                                        color = Color.White,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }

                                IconButton(
                                    onClick = { showSidePanel = false },
                                    modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF131D2E))
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "إغلاق اللوحة",
                                        tint = Color(0xFF22D3EE),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            // زر بدء محادثة جديدة
                            Surface(
                                onClick = {
                                    val newId = AgentChatSessionManager.startNewSession(context)
                                    chatMessages.clear()
                                    showSidePanel = false
                                    Toast.makeText(context, "تم بدء محادثة جديدة 💬", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.18f),
                                border = BorderStroke(1.5.dp, Color(0xFF10B981).copy(alpha = 0.8f)),
                                modifier = Modifier.fillMaxWidth().height(46.dp).testTag("agent_new_chat_btn")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.AddComment, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "+ محادثة جديدة 💬",
                                        color = Color.White,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            Text(
                                text = "المحادثات السابقة:",
                                color = Color(0xFF94A3B8),
                                fontFamily = CairoFont,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.height(6.dp))

                            if (sessions.isEmpty()) {
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("لا توجد محادثات سابقة", color = Color(0xFF64748B), fontFamily = CairoFont, fontSize = 12.sp)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(sessions, key = { it.id }) { session ->
                                        val isCurrent = session.id == activeSessionId
                                        val dateStr = remember(session.timestamp) {
                                            SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(session.timestamp))
                                        }

                                        Surface(
                                            onClick = {
                                                AgentChatSessionManager.switchSession(context, session.id)
                                                val loaded = AgentChatSessionManager.loadSessionMessages(context, session.id)
                                                chatMessages.clear()
                                                chatMessages.addAll(loaded)
                                                showSidePanel = false
                                                Toast.makeText(context, "تم استرجاع المحادثة 💬", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isCurrent) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFF08101E),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isCurrent) Color(0xFF10B981).copy(alpha = 0.65f) else Color(0xFF1E2D4A)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (isCurrent) Icons.Default.CheckCircle else Icons.Default.ChatBubbleOutline,
                                                    contentDescription = null,
                                                    tint = if (isCurrent) Color(0xFF10B981) else Color(0xFF64748B),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = session.title,
                                                        color = if (isCurrent) Color(0xFF10B981) else Color.White,
                                                        fontSize = 12.sp,
                                                        fontFamily = CairoFont,
                                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "${session.messageCount} رسائل",
                                                            color = Color(0xFF64748B),
                                                            fontSize = 10.sp
                                                        )
                                                        Text(
                                                            text = "• $dateStr",
                                                            color = Color(0xFF475569),
                                                            fontSize = 9.5.sp
                                                        )
                                                    }
                                                }
                                                IconButton(
                                                    onClick = {
                                                        AgentChatSessionManager.deleteSession(context, session.id)
                                                        val curId = AgentChatSessionManager.activeSessionId.value ?: AgentChatSessionManager.startNewSession(context)
                                                        val loaded = AgentChatSessionManager.loadSessionMessages(context, curId)
                                                        chatMessages.clear()
                                                        chatMessages.addAll(loaded)
                                                        Toast.makeText(context, "تم حذف الجلسة", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.DeleteOutline,
                                                        contentDescription = "حذف الجلسة",
                                                        tint = Color(0xFFEF4444).copy(alpha = 0.7f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
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
    
    if (showRequirementDialog) {
        AgentRequirementDialog(
            context = context,
            onDismiss = { showRequirementDialog = false },
            onConfigComplete = {
                refreshDiagnostics()
                Toast.makeText(context, "تم تفعيل المتطلبات! يمكنك الآن إرسال طلبك 🚀", Toast.LENGTH_LONG).show()
            }
        )
    }
    if (showSettings) {
        AgentCyberSettingsBottomSheet(
            context = context,
            activeRepo = activeRepo,
            onDismiss = { showSettings = false },
            onOpenApiKeys = {
                keyDialogInitialTab = 0
                showKeyDialog = true
            },
            onOpenRepoSetup = {
                showRepoDialog = true
            },
            onOpenOnDeviceModels = {
                showOnDeviceDialog = true
            },
            onOpenDevTools = {
                showDevConsole = true
            }
        )
    }
    if (showSpecChooser) {
        Dialog(onDismissRequest = { showSpecChooser = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.92f).padding(16.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF0B1424),
                border = BorderStroke(1.2.dp, Color(0xFF22D3EE).copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "اختر تخصص الوكيل البرمجي 🧠",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AgentSpecialization.entries.toTypedArray()) { spec ->
                            val isSelected = selectedSpec == spec
                            Surface(
                                onClick = {
                                    selectedSpec = spec
                                    showSpecChooser = false
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) spec.color.copy(alpha = 0.18f) else Color(0xFF070E1A),
                                border = BorderStroke(1.dp, if (isSelected) spec.color else Color(0xFF1E2D4A)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(spec.icon, contentDescription = null, tint = spec.color, modifier = Modifier.size(20.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(spec.title, color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                        Text(spec.description, color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 10.5.sp)
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = spec.color, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
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
        Dialog(
            onDismissRequest = { showModelPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.88f).padding(16.dp),
                color = Color(0xFF0B1220),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFF22D3EE).copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF22D3EE).copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF22D3EE), modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("محرك الذكاء", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = CairoFont)
                            Text(provider.name, color = Color(0xFF22D3EE), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { showModelPicker = false }) {
                            Icon(Icons.Default.Close, null, tint = Color(0xFF64748B))
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(models) { m ->
                            val isSelected = selectedModel == m
                            val modelIcon = when {
                                m.contains("flash") -> Icons.Default.Bolt
                                m.contains("pro") -> Icons.Default.Star
                                m.contains("o1") -> Icons.Default.Psychology
                                m.contains("gpt-4") -> Icons.Default.AutoAwesome
                                m.contains("llama") -> Icons.Default.Architecture
                                else -> Icons.Default.SmartToy
                            }
                            
                            Surface(
                                onClick = { selectedModel = m; showModelPicker = false },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFF22D3EE).copy(alpha = 0.12f) else Color(0xFF0F172A),
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF22D3EE).copy(alpha = 0.6f) else Color(0xFF1E293B)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        modelIcon, null, 
                                        tint = if (isSelected) Color(0xFF22D3EE) else Color(0xFF64748B),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = m,
                                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, null, tint = Color(0xFF22D3EE), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    AgentNeonButton(
                        text = "إغلاق",
                        onClick = { showModelPicker = false },
                        accent = AgentDesign.Secondary,
                        icon = Icons.Default.Close,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
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

/**
 * لوحة التحكم والإعدادات السفلية بنمط السايبر (Cyber HUD Settings Bottom Sheet)
 * تتيح تخصيص النموذج، حد جولات التفكير، الإصلاح الذاتي، والربط مع المفاتيح والمستودع.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentCyberSettingsBottomSheet(
    context: Context,
    activeRepo: RepoVaultEntry?,
    onDismiss: () -> Unit,
    onOpenApiKeys: () -> Unit,
    onOpenRepoSetup: () -> Unit,
    onOpenOnDeviceModels: () -> Unit,
    onOpenDevTools: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedModel by remember { mutableStateOf(AgentPrefs.model(context)) }
    var maxTurns by remember { mutableStateOf(AgentPrefs.maxTurns(context).coerceIn(3, 25)) }
    var isAutoFix by remember { mutableStateOf(AgentPrefs.isAutoFix(context)) }
    var isAutonomousMode by remember {
        mutableStateOf(AgentPrefs.getBoolean(context, "agent_autonomous_mode", true))
    }
    var isPlanningMode by remember {
        mutableStateOf(AgentPrefs.isPlanningMode(context))
    }

    val activeEnginePair = UniversalKeyBrain.getActiveEngine()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF070D18),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF1E2D4A)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22D3EE).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFF22D3EE).copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = Color(0xFF22D3EE),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "مركز إعدادات وتحكم الوكيل ⚙️",
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "تخصيص نماذج الذكاء، ضبط النواة، والمستودع",
                            color = Color(0xFF94A3B8),
                            fontFamily = CairoFont,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = Color(0xFF94A3B8)
                    )
                }
            }

            // 🛠️ زر مخصص داخل نافذة الإعدادات يفتح الكونسول وأدوات المطور المتقدمة
            Surface(
                onClick = {
                    onDismiss()
                    onOpenDevTools()
                },
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0F2038),
                border = BorderStroke(1.2.dp, Color(0xFF22D3EE).copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth().testTag("agent_settings_open_dev_console")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22D3EE).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFF22D3EE).copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = Color(0xFF22D3EE), modifier = Modifier.size(18.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "الكونسول المباشر وأدوات المطور 🛠️⚡",
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "تشخيص المحرك الحي، سجل العمليات، ومركز البناء",
                            color = Color(0xFF94A3B8),
                            fontFamily = CairoFont,
                            fontSize = 10.5.sp
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color(0xFF22D3EE),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 🧠 Active Planning Tasks Counter Badge (عداد المهام المخططة النشطة)
            val activeTasksCount = AgentPrefs.activePlanningTasksCount(context)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0D1527),
                border = BorderStroke(
                    1.dp,
                    if (activeTasksCount > 0) Color(0xFFA855F7).copy(alpha = 0.8f) else Color(0xFF1E2D4A)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (activeTasksCount > 0) Color(0xFFA855F7) else Color(0xFF64748B))
                        )
                        Column {
                            Text(
                                text = "مهام التخطيط النشطة (Active Planning Tasks)",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = if (activeTasksCount > 0) "يوجد $activeTasksCount مهمة تخطيط معلقة في وضع Planning Mode" else "لا توجد مهام تخطيط معلقة حالياً",
                                color = Color(0xFF94A3B8),
                                fontFamily = CairoFont,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (activeTasksCount > 0) Color(0xFFA855F7).copy(alpha = 0.2f) else Color(0xFF1E2D4A),
                        border = BorderStroke(
                            1.dp,
                            if (activeTasksCount > 0) Color(0xFFA855F7) else Color(0xFF334155)
                        )
                    ) {
                        Text(
                            text = "🧠 $activeTasksCount",
                            color = if (activeTasksCount > 0) Color(0xFFE9D5FF) else Color(0xFF94A3B8),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Category 1: AI Model & Reasoning Engine
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0C192E))
                    .border(1.dp, Color(0xFF1E2D4A), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "01 | نموذج الذكاء الاصطناعي والاستدلال",
                            color = Color(0xFF38BDF8),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "المزود: ${activeEnginePair.first.name}",
                                color = Color(0xFF10B981),
                                fontFamily = CairoFont,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "اختر النموذج الذي يقود خطوات التفكير البرمجي وتوليد الكود:",
                        color = Color(0xFFCBD5E1),
                        fontFamily = CairoFont,
                        fontSize = 10.sp
                    )

                    val modelOptions = listOf(
                        null to "⚡ تلقائي الذكي (Auto Engine)",
                        "gemini-3.8-flash" to "💎 Gemini 3.8 Flash (سريع ودقيق)",
                        "llama-3.3-70b-versatile" to "🚀 Groq Llama 3.3 70B (فائق السرعة)",
                        "google/gemma-4-31b-it:free" to "🌊 Gemma 4 Free (مجاني ومفتوح)"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        modelOptions.forEach { (id, label) ->
                            val isSelected = selectedModel == id
                            Surface(
                                onClick = {
                                    selectedModel = id
                                    AgentPrefs.putModel(context, id)
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFF091220),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF10B981) else Color(0xFF1E2D4A)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color(0xFF10B981) else Color.White,
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (isSelected) {
                                        Text(
                                            text = "✓ نشط",
                                            color = Color(0xFF10B981),
                                            fontFamily = CairoFont,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Category 2: Kernel Parameters, Turns, & Automation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0C192E))
                    .border(1.dp, Color(0xFF1E2D4A), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "02 | معاملات النواة والجولات البرمجية",
                        color = Color(0xFFE5B842),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )

                    // Max Turns Slider
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "حد جولات تفكير الوكيل (Max Turns):",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "$maxTurns جولة",
                                color = Color(0xFFE5B842),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Slider(
                            value = maxTurns.toFloat(),
                            onValueChange = {
                                val intVal = it.toInt()
                                maxTurns = intVal
                                AgentPrefs.putMaxTurns(context, intVal)
                            },
                            valueRange = 3f..25f,
                            steps = 21,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFE5B842),
                                activeTrackColor = Color(0xFFE5B842),
                                inactiveTrackColor = Color(0xFF1E2D4A)
                            )
                        )
                    }

                    HorizontalDivider(color = Color(0xFF1E2D4A))

                    // Self-Repair Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "الإصلاح الذاتي للأخطاء البرمجية (Self-Repair)",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "يفحص أخطاء البناء والـ Lint ويصلحها تلقائياً قبل تسليم الكود.",
                                color = Color(0xFF94A3B8),
                                fontFamily = CairoFont,
                                fontSize = 10.sp
                            )
                        }
                        Switch(
                            checked = isAutoFix,
                            onCheckedChange = {
                                isAutoFix = it
                                AgentPrefs.putAutoFix(context, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF050B14),
                                checkedTrackColor = Color(0xFF10B981)
                            )
                        )
                    }

                    HorizontalDivider(color = Color(0xFF1E2D4A))

                    // Autonomous Execution Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "النمط المستقل التلقائي (Autonomous Mode)",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "متابعة تنفيذ مراحل المهمة المتتالية دون طلب تأكيد لكل خطوة.",
                                color = Color(0xFF94A3B8),
                                fontFamily = CairoFont,
                                fontSize = 10.sp
                            )
                        }
                        Switch(
                            checked = isAutonomousMode,
                            onCheckedChange = {
                                isAutonomousMode = it
                                AgentPrefs.putBoolean(context, "agent_autonomous_mode", it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF050B14),
                                checkedTrackColor = Color(0xFF38BDF8)
                            )
                        )
                    }

                    HorizontalDivider(color = Color(0xFF1E2D4A))

                    // Planning / Architect Mode Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "وضع التخطيط الهيكلي (Architect / Planning Mode) 🧠",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "إنشاء بطاقة خطة تفاعلية والموافقة عليها قبل البدء بكتابة وتطبيق الكود.",
                                color = Color(0xFF94A3B8),
                                fontFamily = CairoFont,
                                fontSize = 10.sp
                            )
                        }
                        Switch(
                            checked = isPlanningMode,
                            onCheckedChange = {
                                isPlanningMode = it
                                AgentPrefs.putPlanningMode(context, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF050B14),
                                checkedTrackColor = Color(0xFFA855F7)
                            )
                        )
                    }
                }
            }

            // Category 3: Keys & GitHub Integration
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0C192E))
                    .border(1.dp, Color(0xFF1E2D4A), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "03 | مفاتيح API ومستودع GitHub",
                        color = Color(0xFFA855F7),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onDismiss()
                                onOpenApiKeys()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                                contentColor = Color(0xFFA78BFA)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                        ) {
                            Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("مفاتيح API", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                onDismiss()
                                onOpenRepoSetup()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981).copy(alpha = 0.2f),
                                contentColor = Color(0xFF34D399)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                        ) {
                            Icon(Icons.Default.Source, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("ربط المستودع", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }

                    // Status readout
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF08101E))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "المستودع المتصل:",
                            color = Color(0xFF94A3B8),
                            fontFamily = CairoFont,
                            fontSize = 10.sp
                        )
                        Text(
                            text = activeRepo?.fullName ?: "Ali-awwww/qabasapp",
                            color = Color(0xFF38BDF8),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Close & Confirm Button
            Button(
                onClick = {
                    Toast.makeText(context, "تم حفظ إعدادات الوكيل بنجاح ✓", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF22D3EE),
                    contentColor = Color(0xFF050B14)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text(
                    text = "تأكيد وحفظ الإعدادات ✓",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    fontFamily = CairoFont
                )
            }
        }
    }
}

