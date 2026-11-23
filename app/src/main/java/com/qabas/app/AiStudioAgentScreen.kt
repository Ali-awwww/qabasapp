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
    onOpenKeys: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = AgentPrefs.instance(context)

    var showExitConfirmation by remember { mutableStateOf(false) }
    var showSidePanel by remember { mutableStateOf(false) }
    val sessions by AgentChatSessionManager.sessions.collectAsState()
    val activeSessionId by AgentChatSessionManager.activeSessionId.collectAsState()
    val chatMessages = remember { mutableStateListOf<AgentChatMessage>() }
    var isThinking by remember { mutableStateOf(false) }
    var isRunning by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    val chatListState = rememberLazyListState()
    var chatInputText by remember { mutableStateOf("") }

    BackHandler {
        if (showSidePanel) {
            showSidePanel = false
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
    // التخصص المكتشف تلقائياً من نص طلب المستخدم (المرحلة 3) — لا اختيار يدوي.
    // يُحدَّث عند كل إرسال عبر detectSpecialization، ويُعرض كشريحة غير قابلة للنقر.
    var detectedSpec by remember { mutableStateOf(AgentSpecialization.TURBO) }
    var isExecuting by remember { mutableStateOf(false) }
    var currentMission by remember { mutableStateOf<AgentMissionSession?>(null) }
    var missionHistory by remember { mutableStateOf<List<AgentMissionSession>>(emptyList()) }
    // (المرحلة 4) وضع الفريق محفوظ في AgentPrefs — يبقى كما تركه المستخدم.
    var isTeamMode by remember { mutableStateOf(AgentPrefs.isTeamMode(context)) }
    var teamProgressText by remember { mutableStateOf("") }
    // (المرحلة 4) حالة الفريق والنوع المكتشف.
    var teamMembers by remember { mutableStateOf(MultiModelAgentOrchestrator.loadTeam(context, onlyEnabled = true)) }
    val teamConfigured = isTeamConfigured(teamMembers)
    var detectedModelType by remember { mutableStateOf<AgentModelType?>(null) }
    var detectedModelReason by remember { mutableStateOf("") }
    var effectiveModel by remember { mutableStateOf("") }
    var verifyingBuild by remember { mutableStateOf(false) }
    var isOfflineMode by remember { mutableStateOf(OnDeviceModelManager.isOfflineModeActive(context)) }
    var activeLocalModel by remember { mutableStateOf(OnDeviceModelManager.getSelectedModel(context)) }
    var executionJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var lastExecutedPrompt by remember { mutableStateOf("") }
    // ق93: مصدر الحقيقة الوحيد للنموذج هو AgentPrefs.model — كان
    // remember("") فيُنسى الاختيار بمغادرة الشاشة، بينما منتقي
    // الإعدادات يحفظ في AgentPrefs ولا يقرؤه أحد فكان زراً ميتاً.
    var selectedModel by remember { mutableStateOf(AgentPrefs.model(context) ?: "") }
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


    val refreshDiagnostics: () -> Unit = {
        coroutineScope.launch {
            val resolved = AgentConnectivityEngine.resolveKeyWithSource(context)
            keyResolution = resolved
        }
    }

    LaunchedEffect(Unit) { refreshDiagnostics() }

    // 🔑 مزامنة النموذج المختار مع المزود النشط (يمنع إرسال نموذج Gemini لمزود OpenRouter مثلاً)
    LaunchedEffect(keyResolution.first) {
        val models = keyResolution.first.models
        if (selectedModel.isBlank() || selectedModel !in models) {
            // ق93: التصحيح لمزود آخر يُحفظ أيضاً — وإلا عاد منتقي
            // الإعدادات يعرض نموذجاً لا يعمل مع المفتاح الحالي.
            models.firstOrNull()?.let { selectedModel = it; AgentPrefs.putModel(context, it) }
        }
    }

    // (المرحلة 4) تبديل وضع الفريق مع حفظ الاختيار وتحديث حالة الفريق.
    fun setTeamMode(enabled: Boolean) {
        isTeamMode = enabled
        AgentPrefs.putTeamMode(context, enabled)
        teamMembers = MultiModelAgentOrchestrator.loadTeam(context, onlyEnabled = true)
    }

    // (المرحلة 4) اختيار نوع النموذج للنص المُرسل وتحديث العرض؛
    // يعيد معرّف النموذج الفعّال. يُستدعى من كل مسار إرسال.
    // وضع الفريق المفعّل يفرض TEAM إن كان الفريق مهيأً، وإلا يرجع
    // للقوي مع تنبيه؛ وفي الوضع الفردي يقرر الكشف التلقائي، وقد
    // يختار TEAM لمهمة معقدة وفريق مهيأ (يُعرض ذلك في التسمية).
    fun chooseModelForSend(prompt: String): String {
        teamMembers = MultiModelAgentOrchestrator.loadTeam(context, onlyEnabled = true)
        val configuredNow = isTeamConfigured(teamMembers)
        val (autoType, autoReason) = detectModelType(prompt, isOfflineMode, configuredNow)
        val (chosenType, chosenReason) = when {
            isTeamMode && configuredNow -> AgentModelType.TEAM to
                "وضع الفريق — القائد يقسّم والمُساعدون ينفذون بالتوازي"
            isTeamMode -> AgentModelType.POWERFUL to
                "لا يوجد فريق مهيأ — النموذج القوي بدلاً منه"
            else -> autoType to autoReason
        }
        detectedModelType = chosenType
        detectedModelReason = chosenReason
        val resolved = resolveModelForType(
            chosenType,
            keyResolution.first,
            selectedModel.ifBlank { keyResolution.first.defaultModel }
        )
        effectiveModel = resolved
        if (isTeamMode && !configuredNow) {
            Toast.makeText(
                context,
                "لا يوجد فريق مهيأ — سأعمل بالنموذج القوي. أعدّ الفريق من زر «إعداد الفريق».",
                Toast.LENGTH_LONG
            ).show()
        }
        return resolved
    }

    // (المرحلة 4) «المزود • الاسم المقروء» للنموذج — لا معرّف خام أبداً.
    fun modelNameWithProvider(modelId: String): String {
        val providerLabel = providerShortName(keyResolution.first)
        val name = readableModelName(modelId)
        return if (providerLabel.isBlank()) name else "$providerLabel • $name"
    }

    // (المرحلة 4) تسمية النموذج النشط — تُحسب من الحالة الحية عند كل إعادة تركيب.
    val modelLabel: String = when {
        isOfflineMode -> "على الجهاز • ${activeLocalModel.name}"
        isTeamMode && teamConfigured -> {
            val assistants = teamMembers.count { it.enabled && it.role != AgentTeamRole.LEADER }
            "فريق • القائد + $assistants مساعد"
        }
        isTeamMode -> "فريق (غير مهيأ) — سيُستخدم القوي"
        else -> {
            val type = detectedModelType
            if (type != null) "${type.title} — ${modelNameWithProvider(effectiveModel)} · $detectedModelReason"
            else modelNameWithProvider(selectedModel.ifBlank { keyResolution.first.defaultModel })
        }
    }

    LaunchedEffect(currentMission, isOfflineMode) {
        saveMissionState()
        if (isOfflineMode && currentMission != null && !OnDeviceModelManager.hasAnyInstalledModel(context)) {
            if (!missionAutoSwitched) {
                missionAutoSwitched = true
                saveMissionState()
                // (المرحلة 5) نافذة المفاتيح حُذفت — الفتح التلقائي صار تنقلاً
                // إلى القسم الموحد «مفاتيحي ونماذجي» حيث النماذج المحلية.
                onOpenKeys()
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

    // مصدر الحقيقة لحالة التهيئة: مفتاح + مستودع بمفتاح PAT.
    // «جاهز» لا تظهر إلا عند توفرهما معاً — وإلا يظهر ما ينقص.
    val agentHasKey = keyResolution.third != KeySource.NONE
    val agentHasRepoWithPat = activeRepo != null && activeRepo.token.isNotBlank()

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

        // (المرحلة 3) إعادة كشف التخصص من نص الخطة المعتمدة.
        detectedSpec = detectSpecialization(planCard.prompt)
        // (المرحلة 4) اختيار نوع النموذج للخطة المعتمدة.
        val sendModel = chooseModelForSend(planCard.prompt)

        val job = coroutineScope.launch(Dispatchers.IO) {
            var finalMissionResult: AgentMissionSession? = null
            var missionFailure: String? = null
            try {
                executeAgentMission(
                    context = context,
                    prompt = planCard.prompt,
                    spec = detectedSpec,
                    repo = activeRepo,
                    // ق93: الملاذ نموذج المزود النشط لا Gemini ثابتاً (كما في مسار الإرسال).
                    selectedModel = sendModel,
                    // السجل السابق لملاحظة الاعتماد (أُضيفت للتو فتُستثنى).
                    history = chatMessages.dropLast(1).toList(),
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

        // (المرحلة 3) كشف التخصص تلقائياً من نص الطلب — قبل بدء أي مسار تنفيذ.
        detectedSpec = detectSpecialization(clean)
        // (المرحلة 4) اختيار نوع النموذج للنص المُرسل.
        val sendModel = chooseModelForSend(clean)

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
                    // (المرحلة 8) الوضع المستقل: عند تشغيله تُعتمَد الخطة تلقائياً
                    // ويبدأ التنفيذ بلا انتظار نقرة الاعتماد؛ وعند إيقافه (الافتراضي)
                    // تبقى الخطة معلقة حتى يضغط المستخدم زر الاعتماد في البطاقة.
                    val autonomous = AgentPrefs.isAutonomousMode(context)
                    withContext(Dispatchers.Main) {
                        val currentActive = AgentPrefs.activePlanningTasksCount(context)
                        AgentPrefs.putActivePlanningTasksCount(context, currentActive + 1)
                        val agentMsg = AgentChatMessage(
                            sender = AgentChatSender.AGENT,
                            text = if (autonomous) "تم تفعيل \"وضع التخطيط الهيكلي\". تم إعداد خطة العمل المعمارية التالية لطلبك. الوضع المستقل مفعّل — اعتُمدت الخطة تلقائياً وبدأ التنفيذ المباشر:"
                            else "تم تفعيل \"وضع التخطيط الهيكلي\". تم إعداد خطة العمل المعمارية التالية لطلبك. يرجى مراجعتها واضغط على زر الاعتماد للبدء بالتنفيذ المباشر:",
                            planCard = planCardData
                        )
                        chatMessages.add(agentMsg)
                        AgentChatSessionManager.saveSessionMessages(context, curId, chatMessages.toList())
                        isThinking = false
                        isRunning = false
                    }
                    if (autonomous) {
                        withContext(Dispatchers.Main) {
                            handleApprovePlan(planCardData)
                        }
                    }
                } else if (isTeamMode) {
                    val team = MultiModelAgentOrchestrator.loadTeam(context, onlyEnabled = true)
                    // (ق37) السجل السابق للطلب الحالي (أُضيفت رسالة المستخدم للتو
                    // فتُستثنى — وقبل رسالة «جاري تفعيل الفريق» الآتية) — كما في
                    // المسار الفردي في الفرع المقابل أدناه.
                    val teamHistory = chatMessages.dropLast(1).toList()
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
                        history = teamHistory,
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
                            spec = detectedSpec,
                            repo = activeRepo,
                            // ق93: الملاذ نموذج المزود النشط لا Gemini ثابتاً —
                            // كان مفتاح Groq بلا اختيار يرسل نموذج Gemini.
                            selectedModel = sendModel,
                            // السجل السابق للطلب الحالي (أُضيفت رسالة المستخدم للتو فتُستثنى).
                            history = chatMessages.dropLast(1).toList(),
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


    Scaffold(
        bottomBar = bottomBar,
        containerColor = QabasTheme.current.background,
        topBar = {
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

                            // عنوان الجلسة والتخصص المكتشف تلقائياً (عرض فقط — بلا نقر)
                            Column(
                                modifier = Modifier.weight(1f)
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
                                    Icon(detectedSpec.icon, contentDescription = null, tint = detectedSpec.color, modifier = Modifier.size(12.dp))
                                    Text(
                                        text = "التخصص المكتشف: ${detectedSpec.title}",
                                        color = detectedSpec.color,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = CairoFont
                                    )
                                }
                            }

                            // مؤشر الحالة المباشر — «جاهز» فقط عند مفتاح
                            // + مستودع بمفتاح PAT؛ وإلا يظهر ما ينقص.
                            AgentStatusChip(
                                kind = when {
                                    isThinking || (isRunning && !isPaused) -> AgentStatusKind.RUNNING
                                    isPaused -> AgentStatusKind.NEEDS_ACTION
                                    !agentHasKey || !agentHasRepoWithPat -> AgentStatusKind.NEEDS_ACTION
                                    else -> AgentStatusKind.READY
                                },
                                text = when {
                                    isThinking -> "يفكر..."
                                    isRunning && !isPaused -> "يعمل"
                                    isPaused -> "موقف"
                                    !agentHasKey -> "ينقصه مفتاح"
                                    activeRepo == null -> "بلا مستودع"
                                    !agentHasRepoWithPat -> "بلا مفتاح PAT"
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


                        }
                    }
                }
        }
    ) { innerPadding ->
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
                                            // لا وعد ببناء ميزات قبل التهيئة:
                                            // النص يسرد ما ينقص حتى يبدأ الوكيل.
                                            text = when {
                                                agentHasKey && agentHasRepoWithPat -> "تستطيع إرسال أي طلب تعديل، فحص الكود، إصلاح أخطاء، أو بناء ميزات جديدة مباشرة. استخدم الأوامر السريعة أدناه أو اكتب طلبك باللغة العربية."
                                                !agentHasKey && activeRepo == null -> "قبل أن يبدأ الوكيل، يلزمك: حفظ مفتاح API من «مفاتيحي»، وربط مستودع GitHub. بعدها تستطيع إرسال أي طلب تعديل أو إصلاح بالعربية."
                                                !agentHasKey -> "قبل أن يبدأ الوكيل، يلزمك: حفظ مفتاح API من «مفاتيحي». بعدها تستطيع إرسال أي طلب تعديل أو إصلاح بالعربية."
                                                activeRepo == null -> "قبل أن يبدأ الوكيل، يلزمك: ربط مستودع GitHub. بعدها تستطيع إرسال أي طلب تعديل أو إصلاح بالعربية."
                                                else -> "قبل أن يبدأ الوكيل، يلزمك: حفظ مفتاح PAT للمستودع المربوط. بعدها تستطيع إرسال أي طلب تعديل أو إصلاح بالعربية."
                                            },
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

                    // شريط الوضع والنموذج (المرحلة 4) — فوق شريط الإدخال مباشرة:
                    // محدد فردي/فريق موسوم + تسمية النموذج النشط + إعداد الفريق.
                    // التسمية سطر واحد ellipsis ومربوطة بالحالة الحية، والضغط عليها
                    // ينقّل إلى «مفاتيحي» حيث تُدار المفاتيح والنماذج من مصدر واحد.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // محدد موسوم فردي/فريق (بدل أيقونة الروبوت المجردة)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, QabasTheme.current.primary.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        ) {
                            ModeSegmentChip(
                                label = "فردي",
                                icon = Icons.Default.SmartToy,
                                selected = !isTeamMode,
                                selectedColor = QabasTheme.current.primary,
                                onClick = { setTeamMode(false) }
                            )
                            ModeSegmentChip(
                                label = "فريق",
                                icon = Icons.Default.Groups,
                                selected = isTeamMode,
                                selectedColor = QabasTheme.current.warning,
                                onClick = { setTeamMode(true) },
                                modifier = Modifier.testTag("agent_chat_toggle_team")
                            )
                        }
                        Text(
                            text = modelLabel,
                            color = QabasTheme.current.textSecondary,
                            fontFamily = CairoFont,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenKeys() }
                        )
                        if (isTeamMode) {
                            TextButton(onClick = onOpenTeamConfig) {
                                Text(
                                    "إعداد الفريق",
                                    color = QabasTheme.current.primary,
                                    fontFamily = CairoFont,
                                    fontSize = 11.sp
                                )
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
                                maxLines = 3
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

                            // (المرحلة 5) إدارة المهمة — نُقلت من نافذة المفاتيح
                            // المحذوفة إلى اللوحة الجانبية: تصدير/استيراد المهمة.
                            Text(
                                text = "إدارة المهمة:",
                                color = QabasTheme.current.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val json = exportMissionJson()
                                        if (json.isNotBlank()) {
                                            clipboardManager.setText(AnnotatedString(json))
                                            Toast.makeText(context, "تم نسخ المهمة إلى الحافظة", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "لا توجد مهمة للتصدير", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("تصدير المهمة", fontSize = 11.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val json = prefs.getString("active_mission_json", "") ?: ""
                                        if (json.isNotBlank() && importMissionFromJson(json)) {
                                            Toast.makeText(context, "تم استيراد المهمة من الحالة المحفوظة", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "لا توجد مهمة محفوظة للاستيراد", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("استيراد مهمة", fontSize = 11.sp)
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

/**
 * شريحة خيار في محدد الوضع (المرحلة 4): «فردي» / «فريق».
 */
@Composable
private fun ModeSegmentChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(if (selected) selectedColor.copy(alpha = 0.18f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) selectedColor else QabasTheme.current.textSecondary,
            modifier = Modifier.size(14.dp)
        )
        Text(
            label,
            color = if (selected) selectedColor else QabasTheme.current.textSecondary,
            fontFamily = CairoFont,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
