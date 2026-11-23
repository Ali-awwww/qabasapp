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
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.agent.*
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** قائمة النماذج لكل مزود مصدرها الوحيد [DetectedProvider.models] — لا قائمة متوازية هنا. */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiStudioAgentScreen(
    onBack: () -> Unit,
    onOpenTeamConfig: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    initialTab: Int = 0,
    /** يفتح المحادثة مباشرة بدل Hub — يستخدمه تبويب «المحادثة» في قسم فايب كودينج. */
    startInChat: Boolean = false
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = AgentPrefs.instance(context)

    var showExitConfirmation by remember { mutableStateOf(false) }
    var topTab by remember { mutableStateOf(initialTab.coerceIn(0, 5)) }
    var showHub by remember { mutableStateOf(!startInChat) }
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
        if (showHub) {
            showHub = false
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
    // (12.15) أوامر محفوظة يضيفها المستخدم — تُحفظ في SharedPreferences.
    var savedCommands by remember { mutableStateOf(VibeSavedCommands.list(context)) }
    var newCommandLabel by remember { mutableStateOf("") }
    var newCommandPrompt by remember { mutableStateOf("") }
    var commandMessage by remember { mutableStateOf("") }
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

    // حفظ/استعادة/تصدير/استيراد المهمة عبر مرمّز AgentMission الموحد
    // (12.12): الخطوات تُحفظ كاملة (عنوان + وصف + أداة + مخرجات) لا
    // معرفات فقط كما كان — فالاستعادة كانت تعيد عناوين وأوصافاً فارغة.
    fun saveMissionState() {
        currentMission?.let { mission ->
            prefs.edit().putString("active_mission_json", encodeMissionSession(mission)).apply()
        }
    }

    fun restoreMissionState(): AgentMissionSession? {
        val json = prefs.getString("active_mission_json", null)
        if (json.isNullOrBlank()) return null
        val restored = decodeMissionSession(json)
        if (restored == null) {
            Log.e(TAG, "Failed to restore mission state: invalid JSON")
            prefs.edit().remove("active_mission_json").apply()
        }
        return restored
    }

    fun exportMissionJson(): String {
        return currentMission?.let { encodeMissionSession(it) } ?: ""
    }

    fun importMissionFromJson(jsonString: String): Boolean {
        val decoded = decodeMissionSession(jsonString) ?: return false
        currentMission = decoded
        missionHistory = missionHistory + listOf(decoded)
        return true
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
        val models = keyResolution.first.models
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

    LaunchedEffect(currentMission) {
        if (currentMission == null) return@LaunchedEffect
        while (isActive) {
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
                        AgentPlanStep(1, "التحليل والفحص المعماري 🔍", "تحليل ملفات كود مشروع خادم المسلم المستهدفة وتحديد التغييرات المطلوبة للطلب: \"$clean\"."),
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
                            sender = if (report.overallSuccess) AgentChatSender.AGENT else AgentChatSender.TOOL_SYSTEM,
                            text = report.leaderSynthesis.ifBlank {
                                if (report.overallSuccess) {
                                    "اكتمل تشغيل فريق النماذج، لكن لم يصدر تقرير تلخيصي."
                                } else {
                                    "تعذر إكمال المهمة عبر فريق النماذج. راجع تفاصيل الخطوات والأخطاء المسجلة."
                                }
                            },
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
                    var missionFailure: String? = null
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
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                        missionFailure = e.message
                        Log.e(TAG, "Execution error: ${e.message}")
                    }

                    withContext(Dispatchers.Main) {
                        val reportText = finalMissionResult?.finalReport?.takeIf { it.isNotBlank() }
                            ?: missionFailure?.let { "تعذر إكمال المهمة: $it" }
                            ?: "انتهى التنفيذ دون تقرير نهائي؛ لم يتم تأكيد نجاح المهمة."
                        val diff = finalMissionResult?.proposedDiffs?.firstOrNull()?.let { d ->
                            FileDiff(
                                filePath = d.filePath,
                                additions = d.additions,
                                deletions = d.deletions,
                                diffText = d.diffText
                            )
                        }
                        val agentMsg = AgentChatMessage(
                            sender = if (finalMissionResult?.status == AgentTaskStatus.COMPLETED) AgentChatSender.AGENT else AgentChatSender.TOOL_SYSTEM,
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
            var missionFailure: String? = null
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
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                missionFailure = e.message
                Log.e(TAG, "Plan execution error: ${e.message}")
            }
            withContext(Dispatchers.Main) {
                val reportText = finalMissionResult?.finalReport?.takeIf { it.isNotBlank() }
                    ?: missionFailure?.let { "تعذر تنفيذ الخطة المعتمدة: $it" }
                    ?: "انتهى مسار الخطة دون تقرير نهائي؛ لم يتم تأكيد نجاح التنفيذ."
                val diff = finalMissionResult?.proposedDiffs?.firstOrNull()?.let { d ->
                    FileDiff(filePath = d.filePath, additions = d.additions, deletions = d.deletions, diffText = d.diffText)
                }
                val agentMsg = AgentChatMessage(
                    sender = if (finalMissionResult?.status == AgentTaskStatus.COMPLETED) AgentChatSender.AGENT else AgentChatSender.TOOL_SYSTEM,
                    text = reportText,
                    diff = diff
                )
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

    Scaffold(
        bottomBar = bottomBar,
        containerColor = QabasTheme.current.background,
        topBar = {
            if (showHub) {
                GlassmorphicSurface(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                    backgroundColor = QabasTheme.current.cardSurface.copy(alpha = 0.85f),
                    borderColor = QabasTheme.current.primary.copy(alpha = 0.25f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.size(38.dp).clip(CircleShape).background(QabasTheme.current.secondarySurface).testTag("agent_hub_back")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = QabasTheme.current.primary, modifier = Modifier.size(18.dp))
                            }
                            Text(
                                "مركز التطوير الذكي",
                                fontWeight = FontWeight.Bold,
                                color = QabasTheme.current.textPrimary,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            IconButton(
                                onClick = { showHub = false },
                                modifier = Modifier.size(38.dp).clip(CircleShape).background(QabasTheme.current.success.copy(alpha = 0.14f)).testTag("agent_return_chat")
                            ) {
                                Icon(Icons.Default.ChatBubbleOutline, contentDescription = "فتح المحادثة", tint = QabasTheme.current.success, modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { showSettings = true },
                                modifier = Modifier.size(38.dp).clip(CircleShape).background(QabasTheme.current.secondarySurface).border(1.dp, QabasTheme.current.primary.copy(alpha = 0.5f), CircleShape).testTag("agent_open_settings")
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = "إعدادات الوكيل", tint = QabasTheme.current.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            } else {
                // Top Bar شاشة المحادثة الرئيسية الذكية
                GlassmorphicSurface(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                    backgroundColor = QabasTheme.current.cardSurface.copy(alpha = 0.92f),
                    borderColor = QabasTheme.current.primary.copy(alpha = 0.35f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.size(38.dp).clip(CircleShape).background(QabasTheme.current.secondarySurface)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = QabasTheme.current.primary, modifier = Modifier.size(18.dp))
                            }

                            Spacer(Modifier.width(6.dp))

                            // سهم فتح لوحة الجلسات الجانبية
                            IconButton(
                                onClick = { showSidePanel = !showSidePanel },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (showSidePanel) QabasTheme.current.success.copy(alpha = 0.25f) else QabasTheme.current.secondarySurface)
                                    .border(1.dp, if (showSidePanel) QabasTheme.current.success else QabasTheme.current.primary.copy(alpha = 0.45f), CircleShape)
                                    .testTag("agent_toggle_side_panel")
                            ) {
                                Icon(
                                    imageVector = if (showSidePanel) Icons.Default.MenuOpen else Icons.Default.Menu,
                                    contentDescription = "قائمة المحادثات",
                                    tint = if (showSidePanel) QabasTheme.current.success else QabasTheme.current.primary,
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
                                    color = QabasTheme.current.textPrimary,
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
                                    modifier = Modifier.size(34.dp).clip(CircleShape).background(QabasTheme.current.cardSurface)
                                ) {
                                    Icon(
                                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                        contentDescription = "Pause/Resume",
                                        tint = if (isPaused) QabasTheme.current.success else QabasTheme.current.primary,
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
                                    modifier = Modifier.size(34.dp).clip(CircleShape).background(QabasTheme.current.destructive.copy(alpha = 0.15f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = "Stop",
                                        tint = QabasTheme.current.destructive,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.width(4.dp))

                            IconButton(
                                onClick = { showHub = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(QabasTheme.current.secondarySurface)
                                    .border(1.dp, QabasTheme.current.accent.copy(alpha = 0.5f), CircleShape)
                                    .testTag("agent_open_hub")
                            ) {
                                Icon(Icons.Default.Dashboard, contentDescription = "مركز التطوير", tint = QabasTheme.current.accent, modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = { showSettings = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(QabasTheme.current.secondarySurface)
                                    .border(1.dp, QabasTheme.current.primary.copy(alpha = 0.5f), CircleShape)
                                    .testTag("agent_open_settings")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "إعدادات الوكيل",
                                    tint = QabasTheme.current.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (showHub) {
            Column(
                Modifier.fillMaxSize().padding(innerPadding).background(QabasTheme.current.background)
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
                    isThinking || (isRunning && !isPaused) -> "ANALYZE"
                    else -> "IDLE"
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
                        isOfflineModelReady = OnDeviceModelManager.isModelInstalled(context, activeLocalModel),
                        isProviderReady = keyResolution.third != KeySource.NONE,
                        isGitHubReady = githubHealth.isConnected,
                        isChecking = isPinging,
                        isExecuting = isExecuting,
                        isChatRunning = isRunning,
                        activeMissionTitle = currentMission?.userPrompt,
                        activeMissionStatus = currentMission?.status?.name,
                        historyCount = AgentRunLog.load(context).size,
                        onSelectTab = { topTab = it },
                        onOpenRepository = { showRepoDialog = true },
                        onOpenKeys = { keyDialogInitialTab = 0; showKeyDialog = true },
                        onRefresh = { refreshDiagnostics() }
                    )
                    1 -> LazyColumn(
                        Modifier.fillMaxSize().cyberGridBackground().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            AgentHubPageIntro(
                                index = "02",
                                title = "مساحة العمل",
                                description = "تهيئة المهمة والتخصص والتنفيذ ومراجعة التغييرات المقترحة",
                                icon = Icons.Default.Code
                            )
                        }
                        item { Spacer(Modifier.height(4.dp)) }

                        // Container 1: Mode & Team
                        item {
                        AgentContainer(index = "01", title = "وضع التشغيل", icon = Icons.Default.Sensors, accent = QabasTheme.current.primary) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isTeamMode) QabasTheme.current.success.copy(alpha = 0.22f) else QabasTheme.current.secondarySurface,
                                        border = BorderStroke(1.dp, if (isTeamMode) QabasTheme.current.success.copy(alpha = 0.7f) else QabasTheme.current.separator),
                                        modifier = Modifier.clickable { isTeamMode = !isTeamMode }.testTag("agent_toggle_team_mode")
                                    ) {
                                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Groups, contentDescription = null, tint = if (isTeamMode) QabasTheme.current.success else QabasTheme.current.textSecondary, modifier = Modifier.size(13.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = if (isTeamMode) "فريق متعدد" else "وضع فردي",
                                                fontSize = 12.sp, fontFamily = CairoFont, fontWeight = FontWeight.Bold,
                                                color = if (isTeamMode) QabasTheme.current.success else QabasTheme.current.textSecondary
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth().background(QabasTheme.current.background, RoundedCornerShape(8.dp)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (!isOfflineMode) QabasTheme.current.primary.copy(alpha = 0.25f) else QabasTheme.current.background,
                                    border = if (!isOfflineMode) BorderStroke(1.dp, QabasTheme.current.primary) else null,
                                    modifier = Modifier.weight(1f).clickable { isOfflineMode = false; OnDeviceModelManager.setOfflineModeActive(context, false) }
                                ) {
                                    Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Cloud, contentDescription = null, tint = if (!isOfflineMode) QabasTheme.current.primary else QabasTheme.current.textSecondary, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("سحابي", fontSize = 12.sp, fontWeight = if (!isOfflineMode) FontWeight.Bold else FontWeight.Normal, color = if (!isOfflineMode) QabasTheme.current.primary else QabasTheme.current.textSecondary)
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isOfflineMode) QabasTheme.current.primary.copy(alpha = 0.25f) else QabasTheme.current.background,
                                    border = if (isOfflineMode) BorderStroke(1.dp, QabasTheme.current.primary) else null,
                                    modifier = Modifier.weight(1f).clickable { isOfflineMode = true; OnDeviceModelManager.setOfflineModeActive(context, true) }
                                ) {
                                    Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Memory, contentDescription = null, tint = if (isOfflineMode) QabasTheme.current.primary else QabasTheme.current.textSecondary, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("محلي", fontSize = 12.sp, fontWeight = if (isOfflineMode) FontWeight.Bold else FontWeight.Normal, color = if (isOfflineMode) QabasTheme.current.primary else QabasTheme.current.textSecondary)
                                    }
                                }
                            }
                            if (isOfflineMode) {
                                Spacer(Modifier.height(8.dp))
                                val isLocalInstalled = OnDeviceModelManager.isModelInstalled(context, activeLocalModel)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isLocalInstalled) QabasTheme.current.success.copy(alpha = 0.12f) else QabasTheme.current.warning.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, if (isLocalInstalled) QabasTheme.current.success.copy(alpha = 0.5f) else QabasTheme.current.warning.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth().clickable { showOnDeviceDialog = true }
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("النموذج: ${activeLocalModel.name}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = QabasTheme.current.textPrimary)
                                            Text(
                                                text = if (isLocalInstalled) "جاهز" else "يلزم التنزيل",
                                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                                color = if (isLocalInstalled) QabasTheme.current.success else QabasTheme.current.warning,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp).background(
                                                    if (isLocalInstalled) QabasTheme.current.success.copy(alpha = 0.2f) else QabasTheme.current.warning.copy(alpha = 0.2f),
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
                        AgentContainer(index = "02", title = "تخصص الوكيل", icon = Icons.Default.Psychology, accent = QabasTheme.current.accent) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                items(AgentSpecialization.entries.toTypedArray()) { spec ->
                                    val isSelected = selectedSpec == spec
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) spec.color.copy(alpha = 0.2f) else QabasTheme.current.secondarySurface,
                                        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) spec.color else QabasTheme.current.separator),
                                        modifier = Modifier.heightIn(min = 48.dp).clickable { selectedSpec = spec }.wrapContentWidth()
                                    ) {
                                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(spec.icon, contentDescription = null, tint = spec.color, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                text = spec.title, fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) QabasTheme.current.textPrimary else QabasTheme.current.textSecondary, maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        }

                        // Container 3: Prompt & Execute
                        item {
                            AgentContainer(index = "03", title = "أمر الوكيل", icon = Icons.Default.Terminal, accent = QabasTheme.current.success) {
                                // أوامر محفوظة (12.15): تُحفظ في SharedPreferences —
                                // إضافة/حذف حقيقية؛ الأوامر الأربعة السابقة مزروعة أول مرة.
                                Text(
                                    "أوامر محفوظة (${savedCommands.size}):",
                                    color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (savedCommands.isEmpty()) {
                                    Text(
                                        "لا أوامر محفوظة بعد — أضف أمراً أدناه.",
                                        color = QabasTheme.current.textTertiary, fontFamily = CairoFont, fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                } else {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                    ) {
                                        items(savedCommands, key = { it.id }) { command ->
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    onClick = { promptInput = command.prompt },
                                                    color = QabasTheme.current.secondarySurface,
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = BorderStroke(0.8.dp, QabasTheme.current.primary.copy(alpha = 0.4f))
                                                ) {
                                                    Text(
                                                        text = command.label,
                                                        color = QabasTheme.current.primary,
                                                        fontFamily = CairoFont,
                                                        fontSize = 11.sp,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                                    )
                                                }
                                                Text(
                                                    text = " ✕",
                                                    color = QabasTheme.current.destructive,
                                                    fontFamily = CairoFont,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.clickable {
                                                        VibeSavedCommands.delete(context, command.id)
                                                        savedCommands = VibeSavedCommands.list(context)
                                                    }.padding(horizontal = 2.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = newCommandLabel,
                                    onValueChange = { newCommandLabel = it },
                                    label = { Text("تسمية الأمر الجديد", fontFamily = CairoFont, fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = newCommandPrompt,
                                    onValueChange = { newCommandPrompt = it },
                                    label = { Text("نص الأمر الجديد", fontFamily = CairoFont, fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        val added = VibeSavedCommands.add(context, newCommandLabel, newCommandPrompt)
                                        if (added != null) {
                                            savedCommands = VibeSavedCommands.list(context)
                                            newCommandLabel = ""
                                            newCommandPrompt = ""
                                            commandMessage = "تم حفظ الأمر «${added.label}»."
                                        } else {
                                            commandMessage = "أدخل تسمية ونصاً للأمر أولاً."
                                        }
                                    }) {
                                        Text("حفظ الأمر", fontFamily = CairoFont, fontSize = 11.sp)
                                    }
                                    if (commandMessage.isNotBlank()) {
                                        Text(commandMessage, color = QabasTheme.current.success, fontFamily = CairoFont, fontSize = 11.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = promptInput,
                                onValueChange = { promptInput = it },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 140.dp).testTag("agent_studio_prompt_input"),
                                placeholder = { Text("اكتب أمرك هنا...", color = QabasTheme.current.textTertiary, fontSize = 12.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = QabasTheme.current.primary, unfocusedBorderColor = QabasTheme.current.separator,
                                    focusedTextColor = QabasTheme.current.textPrimary, unfocusedTextColor = QabasTheme.current.textPrimary, cursorColor = QabasTheme.current.primary
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
                                        color = QabasTheme.current.primary.copy(alpha = 0.08f),
                                        border = BorderStroke(1.dp, QabasTheme.current.primary.copy(alpha = 0.3f))
                                    ) {
                                        Row(
                                            Modifier.padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Default.Tune, null, tint = QabasTheme.current.primary, modifier = Modifier.size(16.dp))
                                            Text(
                                                text = selectedModel.take(22).ifBlank { "اختر النموذج" },
                                                color = QabasTheme.current.textPrimary,
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
                                        color = QabasTheme.current.accent.copy(alpha = 0.08f),
                                        border = BorderStroke(1.dp, QabasTheme.current.accent.copy(alpha = 0.3f))
                                    ) {
                                        Row(
                                            Modifier.padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Default.Groups, null, tint = QabasTheme.current.accent, modifier = Modifier.size(16.dp))
                                            Text(
                                                text = "الفريق",
                                                color = QabasTheme.current.textPrimary,
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
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = QabasTheme.current.textTertiary,
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
                                        color = QabasTheme.current.secondarySurface,
                                        border = BorderStroke(1.dp, QabasTheme.current.separator),
                                        modifier = Modifier.height(38.dp)
                                    ) {
                                        Row(
                                            Modifier.padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(icon, null, tint = QabasTheme.current.textSecondary, modifier = Modifier.size(14.dp))
                                            Text(qp, fontSize = 11.sp, color = QabasTheme.current.textPrimary, fontFamily = CairoFont)
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
                                        colors = ButtonDefaults.buttonColors(containerColor = QabasTheme.current.destructive, contentColor = QabasTheme.current.textPrimary),
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
                                            containerColor = if (!canExecute) QabasTheme.current.destructive else if (isOfflineMode) QabasTheme.current.primary else QabasTheme.current.primary,
                                            contentColor = if (!canExecute) QabasTheme.current.textPrimary else QabasTheme.current.background
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
                                        border = BorderStroke(1.dp, QabasTheme.current.success.copy(alpha = 0.5f))
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = QabasTheme.current.success, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("إعادة", color = QabasTheme.current.success, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        }

                        // Container 4: Active Mission
                        currentMission?.let { mission ->
                            item {
                                AgentContainer(title = "المهمة الحالية", icon = Icons.Default.Rocket, accent = QabasTheme.current.accent) {
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
                                            } else if (activeRepo.defaultBranch.isBlank()) {
                                                Toast.makeText(context, "تعذر حفظ المقترح: الفرع الافتراضي غير محدد.", Toast.LENGTH_LONG).show()
                                            } else {
                                                applyingDiffPath = diff.filePath
                                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                    val client = GitHubRepoClient(activeRepo.owner, activeRepo.repo, activeRepo.token)
                                                    // 🔑 نكتب المقترح في مجلد منفصل — لا نستبدل ملف الكود الأصلي بمقتطف جزئي
                                                    val safePath = AgentPatchKit.proposalPath(diff.filePath)
                                                    val ok = client.updateFile(safePath, activeRepo.defaultBranch, "chore(agent): save proposal for ${diff.filePath.substringAfterLast('/')}", diff.diffText)
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
                                AgentContainer(index = "05", title = "سجل العمليات", icon = Icons.Default.History, accent = QabasTheme.current.textSecondary) {
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
                        onOpenRepoSetup = { showRepoDialog = true }
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
                    .background(QabasTheme.current.background)
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
                                    color = QabasTheme.current.cardSurface,
                                    border = BorderStroke(1.2.dp, QabasTheme.current.primary.copy(alpha = 0.35f)),
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
                                                .background(QabasTheme.current.primary.copy(alpha = 0.12f))
                                                .border(1.5.dp, QabasTheme.current.primary.copy(alpha = 0.5f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SmartToy,
                                                contentDescription = null,
                                                tint = QabasTheme.current.primary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                        Text(
                                            text = "مرحباً بك في غرفة الوكيل البرمجي الذكي 🤖",
                                            color = QabasTheme.current.textPrimary,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = "تستطيع إرسال أي طلب تعديل، فحص الكود، إصلاح أخطاء، أو بناء ميزات جديدة مباشرة. استخدم الأوامر السريعة أدناه أو اكتب طلبك باللغة العربية.",
                                            color = QabasTheme.current.textSecondary,
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
                                    color = QabasTheme.current.secondarySurface,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, QabasTheme.current.primary.copy(alpha = 0.5f)),
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
                                            color = QabasTheme.current.primary,
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            text = "الوكيل يفكر ويولّد التعديلات البرمجية... 🧠⚡",
                                            color = QabasTheme.current.primary,
                                            fontFamily = CairoFont,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = QabasTheme.current.separator)

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
                                color = QabasTheme.current.primary.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, QabasTheme.current.primary.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(icon, null, tint = QabasTheme.current.primary, modifier = Modifier.size(13.dp))
                                    Text(
                                        text = prompt,
                                        color = QabasTheme.current.textPrimary,
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
                        color = QabasTheme.current.cardSurface,
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
                                        color = QabasTheme.current.textTertiary,
                                        fontFamily = CairoFont,
                                        fontSize = 12.sp
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = QabasTheme.current.secondarySurface,
                                    unfocusedContainerColor = QabasTheme.current.cardSurface,
                                    focusedBorderColor = QabasTheme.current.success,
                                    unfocusedBorderColor = QabasTheme.current.separator,
                                    focusedTextColor = QabasTheme.current.textPrimary,
                                    unfocusedTextColor = QabasTheme.current.textPrimary
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
                                            .background(if (isTeamMode) QabasTheme.current.warning.copy(alpha = 0.15f) else QabasTheme.current.background)
                                            .border(
                                                width = if (isTeamMode) 1.5.dp else 1.dp,
                                                color = if (isTeamMode) QabasTheme.current.warning.copy(alpha = teamGlowAlpha) else QabasTheme.current.primary.copy(alpha = 0.4f),
                                                shape = CircleShape
                                            )
                                            .testTag("agent_chat_toggle_team")
                                    ) {
                                        Icon(
                                            imageVector = if (isTeamMode) Icons.Default.Groups else Icons.Default.SmartToy,
                                            contentDescription = "تبديل وضع الفريق",
                                            tint = if (isTeamMode) QabasTheme.current.warning.copy(alpha = teamGlowAlpha.coerceAtLeast(0.7f)) else QabasTheme.current.primary,
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
                                    containerColor = QabasTheme.current.success,
                                    disabledContainerColor = QabasTheme.current.separator
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier.height(46.dp).testTag("agent_chat_send_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "إرسال",
                                    tint = if (chatInputText.isNotBlank()) QabasTheme.current.textPrimary else QabasTheme.current.textTertiary,
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
                            .background(QabasTheme.current.background.copy(alpha = 0.55f))
                            .clickable { showSidePanel = false }
                    )

                    // اللوحة الزجاجية المنزلقة
                    Surface(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.84f)
                            .align(Alignment.CenterStart)
                            .clickable(enabled = false) {},
                        color = QabasTheme.current.cardSurface,
                        border = BorderStroke(1.2.dp, QabasTheme.current.primary.copy(alpha = 0.45f)),
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
                                            .background(QabasTheme.current.primary.copy(alpha = 0.15f))
                                            .border(1.dp, QabasTheme.current.primary.copy(alpha = 0.4f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.ChatBubbleOutline,
                                            contentDescription = null,
                                            tint = QabasTheme.current.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Text(
                                        text = "جلسات المحادثة 💬",
                                        color = QabasTheme.current.textPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }

                                IconButton(
                                    onClick = { showSidePanel = false },
                                    modifier = Modifier.size(34.dp).clip(CircleShape).background(QabasTheme.current.cardSurface)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "إغلاق اللوحة",
                                        tint = QabasTheme.current.primary,
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
                                color = QabasTheme.current.success.copy(alpha = 0.18f),
                                border = BorderStroke(1.5.dp, QabasTheme.current.success.copy(alpha = 0.8f)),
                                modifier = Modifier.fillMaxWidth().height(46.dp).testTag("agent_new_chat_btn")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.AddComment, contentDescription = null, tint = QabasTheme.current.success, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "+ محادثة جديدة 💬",
                                        color = QabasTheme.current.textPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            Text(
                                text = "المحادثات السابقة:",
                                color = QabasTheme.current.textSecondary,
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
                                    Text("لا توجد محادثات سابقة", color = QabasTheme.current.textTertiary, fontFamily = CairoFont, fontSize = 12.sp)
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
                                            color = if (isCurrent) QabasTheme.current.success.copy(alpha = 0.12f) else QabasTheme.current.background,
                                            border = BorderStroke(
                                                1.dp,
                                                if (isCurrent) QabasTheme.current.success.copy(alpha = 0.65f) else QabasTheme.current.separator
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
                                                    tint = if (isCurrent) QabasTheme.current.success else QabasTheme.current.textTertiary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = session.title,
                                                        color = if (isCurrent) QabasTheme.current.success else QabasTheme.current.textPrimary,
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
                                                            color = QabasTheme.current.textTertiary,
                                                            fontSize = 10.sp
                                                        )
                                                        Text(
                                                            text = "• $dateStr",
                                                            color = QabasTheme.current.textTertiary,
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
                                                        tint = QabasTheme.current.destructive.copy(alpha = 0.7f),
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
                showHub = true
            }
        )
    }
    if (showSpecChooser) {
        Dialog(onDismissRequest = { showSpecChooser = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.92f).padding(16.dp),
                shape = RoundedCornerShape(18.dp),
                color = QabasTheme.current.cardSurface,
                border = BorderStroke(1.2.dp, QabasTheme.current.primary.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "اختر تخصص الوكيل البرمجي 🧠",
                        color = QabasTheme.current.textPrimary,
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
                                color = if (isSelected) spec.color.copy(alpha = 0.18f) else QabasTheme.current.secondarySurface,
                                border = BorderStroke(1.dp, if (isSelected) spec.color else QabasTheme.current.separator),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(spec.icon, contentDescription = null, tint = spec.color, modifier = Modifier.size(20.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(spec.title, color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                        Text(spec.description, color = QabasTheme.current.textSecondary, fontFamily = CairoFont, fontSize = 10.5.sp)
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
        val models = provider.models
        Dialog(
            onDismissRequest = { showModelPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.88f).padding(16.dp),
                color = QabasTheme.current.cardSurface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, QabasTheme.current.primary.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(QabasTheme.current.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, tint = QabasTheme.current.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("محرك الذكاء", color = QabasTheme.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = CairoFont)
                            Text(provider.name, color = QabasTheme.current.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { showModelPicker = false }) {
                            Icon(Icons.Default.Close, null, tint = QabasTheme.current.textTertiary)
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
                                color = if (isSelected) QabasTheme.current.primary.copy(alpha = 0.12f) else QabasTheme.current.secondarySurface,
                                border = BorderStroke(1.dp, if (isSelected) QabasTheme.current.primary.copy(alpha = 0.6f) else QabasTheme.current.separator),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        modelIcon, null, 
                                        tint = if (isSelected) QabasTheme.current.primary else QabasTheme.current.textTertiary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = m,
                                        color = if (isSelected) QabasTheme.current.textPrimary else QabasTheme.current.textSecondary,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, null, tint = QabasTheme.current.primary, modifier = Modifier.size(16.dp))
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
            title = { Text("تأكيد الخروج", color = QabasTheme.current.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold) },
            text = { Text("هل تريد حقاً الخروج من التطبيق؟", color = QabasTheme.current.textPrimary, fontFamily = NotoSansFont) },
            confirmButton = {
                TextButton(onClick = { showExitConfirmation = false; onBack() }) { Text("نعم، خروج", color = QabasTheme.current.destructive, fontFamily = CairoFont, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) { Text("إلغاء", color = QabasTheme.current.textSecondary, fontFamily = CairoFont) }
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
        color = QabasTheme.current.cardSurface,
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
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = QabasTheme.current.textPrimary, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.fillMaxWidth().height(1.dp).background(
                    Brush.horizontalGradient(listOf(accent.copy(alpha = 0.5f), accent.copy(alpha = 0.1f), accent.copy(alpha = 0f)))
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
        color = if (isConnected) QabasTheme.current.success.copy(alpha = 0.12f) else QabasTheme.current.warning.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, if (isConnected) QabasTheme.current.success.copy(alpha = 0.45f) else QabasTheme.current.warning.copy(alpha = 0.45f)),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (isConnected) QabasTheme.current.success else QabasTheme.current.warning))
                Spacer(Modifier.width(5.dp))
                Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = QabasTheme.current.textPrimary)
                Spacer(Modifier.weight(1f))
                if (latencyMs > 0) Text("${latencyMs}ms", fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = if (isConnected) QabasTheme.current.success else QabasTheme.current.warning)
            }
            Spacer(Modifier.height(3.dp))
            Text(detail.take(50), fontSize = 12.sp, color = QabasTheme.current.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun UnifiedToolsAndReadinessSection(
    context: Context,
    scope: CoroutineScope,
    onJumpKeys: () -> Unit,
    onOpenRepoSetup: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var subTab by remember { mutableStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(QabasTheme.current.background)
    ) {
        AgentHubPageIntro(
            index = "06",
            title = "الأدوات والجاهزية",
            description = "إدارة قدرات الوكيل والتحقق من الاتصالات قبل التنفيذ",
            icon = Icons.Default.Build,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(QabasTheme.current.background)
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(
                                QabasTheme.current.primary.copy(alpha = 0.4f),
                                QabasTheme.current.success.copy(alpha = 0.4f)
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
                color = if (subTab == 0) QabasTheme.current.primary.copy(alpha = 0.22f) else QabasTheme.current.background,
                border = if (subTab == 0) BorderStroke(1.dp, QabasTheme.current.primary.copy(alpha = 0.7f)) else null,
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
                        tint = if (subTab == 0) QabasTheme.current.primary else QabasTheme.current.textSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "فحص الجاهزية والربط",
                        fontSize = 12.sp,
                        fontFamily = CairoFont,
                        fontWeight = if (subTab == 0) FontWeight.Bold else FontWeight.Medium,
                        color = if (subTab == 0) QabasTheme.current.textPrimary else QabasTheme.current.textSecondary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (subTab == 1) QabasTheme.current.success.copy(alpha = 0.22f) else QabasTheme.current.background,
                border = if (subTab == 1) BorderStroke(1.dp, QabasTheme.current.success.copy(alpha = 0.7f)) else null,
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
                        tint = if (subTab == 1) QabasTheme.current.success else QabasTheme.current.textSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "ترسانة الأدوات والـ Git",
                        fontSize = 12.sp,
                        fontFamily = CairoFont,
                        fontWeight = if (subTab == 1) FontWeight.Bold else FontWeight.Medium,
                        color = if (subTab == 1) QabasTheme.current.textPrimary else QabasTheme.current.textSecondary,
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
                    onOpenRepoSetup = onOpenRepoSetup,
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
        // (12.9) الافتراضي متوقف — كان true (تنفيذ بلا تأكيد لكل خطوة).
        mutableStateOf(AgentPrefs.isAutonomousMode(context))
    }
    var isPlanningMode by remember {
        mutableStateOf(AgentPrefs.isPlanningMode(context))
    }

    val activeEnginePair = UniversalKeyBrain.getActiveEngine()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = QabasTheme.current.background,
        scrimColor = QabasTheme.current.background.copy(alpha = 0.65f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = QabasTheme.current.separator) }
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
                            .background(QabasTheme.current.primary.copy(alpha = 0.15f))
                            .border(1.dp, QabasTheme.current.primary.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = QabasTheme.current.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "مركز إعدادات وتحكم الوكيل ⚙️",
                            color = QabasTheme.current.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "تخصيص نماذج الذكاء، ضبط النواة، والمستودع",
                            color = QabasTheme.current.textSecondary,
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
                        tint = QabasTheme.current.textSecondary
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
                color = QabasTheme.current.secondarySurface,
                border = BorderStroke(1.2.dp, QabasTheme.current.primary.copy(alpha = 0.7f)),
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
                            .background(QabasTheme.current.primary.copy(alpha = 0.15f))
                            .border(1.dp, QabasTheme.current.primary.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = QabasTheme.current.primary, modifier = Modifier.size(18.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "الكونسول المباشر وأدوات المطور 🛠️⚡",
                            color = QabasTheme.current.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "تشخيص المحرك الحي، سجل العمليات، وإعدادات الوكيل",
                            color = QabasTheme.current.textSecondary,
                            fontFamily = CairoFont,
                            fontSize = 10.5.sp
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = QabasTheme.current.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 🧠 Active Planning Tasks Counter Badge (عداد المهام المخططة النشطة)
            val activeTasksCount = AgentPrefs.activePlanningTasksCount(context)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = QabasTheme.current.cardSurface,
                border = BorderStroke(
                    1.dp,
                    if (activeTasksCount > 0) QabasTheme.current.accent.copy(alpha = 0.8f) else QabasTheme.current.separator
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
                                .background(if (activeTasksCount > 0) QabasTheme.current.accent else QabasTheme.current.textTertiary)
                        )
                        Column {
                            Text(
                                text = "مهام التخطيط النشطة (Active Planning Tasks)",
                                color = QabasTheme.current.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = if (activeTasksCount > 0) "يوجد $activeTasksCount مهمة تخطيط معلقة في وضع Planning Mode" else "لا توجد مهام تخطيط معلقة حالياً",
                                color = QabasTheme.current.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (activeTasksCount > 0) QabasTheme.current.accent.copy(alpha = 0.2f) else QabasTheme.current.separator,
                        border = BorderStroke(
                            1.dp,
                            if (activeTasksCount > 0) QabasTheme.current.accent else QabasTheme.current.separator
                        )
                    ) {
                        Text(
                            text = "🧠 $activeTasksCount",
                            color = if (activeTasksCount > 0) QabasTheme.current.textPrimary else QabasTheme.current.textSecondary,
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
                    .background(QabasTheme.current.cardSurface)
                    .border(1.dp, QabasTheme.current.separator, RoundedCornerShape(12.dp))
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
                            color = QabasTheme.current.primary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(QabasTheme.current.success.copy(alpha = 0.15f))
                                .border(1.dp, QabasTheme.current.success.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "المزود: ${activeEnginePair.first.name}",
                                color = QabasTheme.current.success,
                                fontFamily = CairoFont,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "اختر النموذج الذي يقود خطوات التفكير البرمجي وتوليد الكود:",
                        color = QabasTheme.current.textPrimary,
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
                                color = if (isSelected) QabasTheme.current.success.copy(alpha = 0.15f) else QabasTheme.current.secondarySurface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) QabasTheme.current.success else QabasTheme.current.separator
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
                                        color = if (isSelected) QabasTheme.current.success else QabasTheme.current.textPrimary,
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (isSelected) {
                                        Text(
                                            text = "✓ نشط",
                                            color = QabasTheme.current.success,
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
                    .background(QabasTheme.current.cardSurface)
                    .border(1.dp, QabasTheme.current.separator, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "02 | معاملات النواة والجولات البرمجية",
                        color = QabasTheme.current.warning,
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
                                color = QabasTheme.current.textPrimary,
                                fontFamily = CairoFont,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "$maxTurns جولة",
                                color = QabasTheme.current.warning,
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
                                thumbColor = QabasTheme.current.warning,
                                activeTrackColor = QabasTheme.current.warning,
                                inactiveTrackColor = QabasTheme.current.separator
                            )
                        )
                    }

                    HorizontalDivider(color = QabasTheme.current.separator)

                    // Self-Repair Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "الإصلاح الذاتي للأخطاء البرمجية (Self-Repair)",
                                color = QabasTheme.current.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "يفحص أخطاء البناء والـ Lint ويصلحها تلقائياً قبل تسليم الكود.",
                                color = QabasTheme.current.textSecondary,
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
                                checkedThumbColor = QabasTheme.current.onPrimary,
                                checkedTrackColor = QabasTheme.current.success
                            )
                        )
                    }

                    HorizontalDivider(color = QabasTheme.current.separator)

                    // Autonomous Execution Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "النمط المستقل التلقائي (Autonomous Mode)",
                                color = QabasTheme.current.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "متابعة تنفيذ مراحل المهمة المتتالية دون طلب تأكيد لكل خطوة.",
                                color = QabasTheme.current.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 10.sp
                            )
                        }
                        Switch(
                            checked = isAutonomousMode,
                            onCheckedChange = {
                                isAutonomousMode = it
                                AgentPrefs.putAutonomousMode(context, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = QabasTheme.current.onPrimary,
                                checkedTrackColor = QabasTheme.current.primary
                            )
                        )
                    }

                    HorizontalDivider(color = QabasTheme.current.separator)

                    // Planning / Architect Mode Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "وضع التخطيط الهيكلي (Architect / Planning Mode) 🧠",
                                color = QabasTheme.current.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "إنشاء بطاقة خطة تفاعلية والموافقة عليها قبل البدء بكتابة وتطبيق الكود.",
                                color = QabasTheme.current.textSecondary,
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
                                checkedThumbColor = QabasTheme.current.onPrimary,
                                checkedTrackColor = QabasTheme.current.accent
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
                    .background(QabasTheme.current.cardSurface)
                    .border(1.dp, QabasTheme.current.separator, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "03 | مفاتيح API ومستودع GitHub",
                        color = QabasTheme.current.accent,
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
                                containerColor = QabasTheme.current.accent.copy(alpha = 0.2f),
                                contentColor = QabasTheme.current.accent
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, QabasTheme.current.accent.copy(alpha = 0.5f)),
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
                                containerColor = QabasTheme.current.success.copy(alpha = 0.2f),
                                contentColor = QabasTheme.current.success
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, QabasTheme.current.success.copy(alpha = 0.5f)),
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
                            .background(QabasTheme.current.background)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "المستودع المتصل:",
                            color = QabasTheme.current.textSecondary,
                            fontFamily = CairoFont,
                            fontSize = 10.sp
                        )
                        Text(
                            text = activeRepo?.fullName ?: "Ali-awwww/qabasapp",
                            color = QabasTheme.current.primary,
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
                    containerColor = QabasTheme.current.primary,
                    contentColor = QabasTheme.current.onPrimary
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
