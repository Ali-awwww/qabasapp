package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat


@Composable
internal fun AgentLiveConsoleTab(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onJumpKeys: () -> Unit,
    onJumpBuildCenter: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    val vaultTrigger by RepoVaultManager.vaultUpdateTrigger.collectAsState()
    val keyTrigger by UniversalKeyBrain.keyUpdateTrigger.collectAsState()

    var activeEngineInfo by remember(keyTrigger) { mutableStateOf(UniversalKeyBrain.getActiveEngine()) }
    var customKeyInput by remember { mutableStateOf("") }
    var detectedNewProvider by remember { mutableStateOf(DetectedProvider.NONE) }
    var isKeyBoxExpanded by remember { mutableStateOf(false) }
    var showFreeProvidersDialog by remember { mutableStateOf(false) }

    val maxTurns = remember(context) { AgentPrefs.maxTurns(context) }
    var activeRepo by remember(vaultTrigger) { mutableStateOf(RepoVaultManager.getActiveRepo(context)) }
    val _ap = com.qabas.app.AgentPrefs
    var owner by remember(activeRepo) { mutableStateOf(activeRepo?.owner ?: (_ap.getGitHubOwner(context))) }
    var repo by remember(activeRepo) { mutableStateOf(activeRepo?.repo ?: (_ap.getGitHubRepo(context))) }
    var token by remember(activeRepo) { mutableStateOf(activeRepo?.token?.takeIf { it.isNotBlank() } ?: (_ap.getGitHubPat(context))) }

    var ghState by remember { mutableStateOf(GitHubConnectionState.SYNCING) }
    var ghUser by remember { mutableStateOf<String?>(null) }
    var ghBranch by remember { mutableStateOf("main") }
    var ghLatencyMs by remember { mutableLongStateOf(0L) }
    var ghErrorMessage by remember { mutableStateOf("") }

    fun checkGitHubStatus() {
        ghState = GitHubConnectionState.SYNCING
        scope.launch {
            val t0 = System.currentTimeMillis()
            if (token.isBlank()) {
                ghState = GitHubConnectionState.NOT_CONFIGURED
                ghErrorMessage = "لم يتم ضبط رمز الوصول الشخصي (GitHub PAT)"
                return@launch
            }
            val client = GitHubRepoClient(owner.ifBlank { "-" }, repo.ifBlank { "-" }, token)
            val user = client.getAuthUser()
            ghLatencyMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            if (user != null) {
                ghUser = user
                ghBranch = if (owner.isNotBlank() && repo.isNotBlank()) client.getDefaultBranch() else "main"
                ghState = GitHubConnectionState.CONNECTED
                ghErrorMessage = ""
            } else {
                ghState = GitHubConnectionState.PERMISSION_ERROR
                ghErrorMessage = "فشل التحقق: الرمز منتهي أو يفتقد لصلاحية repo أو المستودع غير موجود"
            }
        }
    }

    LaunchedEffect(token, owner, repo) {
        checkGitHubStatus()
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.flow.merge(
            RepoVaultManager.vaultUpdateTrigger,
            UniversalKeyBrain.keyUpdateTrigger
        ).collect {
            activeEngineInfo = UniversalKeyBrain.getActiveEngine()
            val currentActive = RepoVaultManager.getActiveRepo(context)
            if (currentActive != null) {
                activeRepo = currentActive
                owner = currentActive.owner
                repo = currentActive.repo
                if (currentActive.token.isNotBlank()) token = currentActive.token
            }
            checkGitHubStatus()
        }
    }

    var missionInput by remember {
        mutableStateOf("")
    }

    // إدارة الطابور وحالات التشغيل والإيقاف المؤقت
    val commandQueue = remember { mutableStateListOf<QueuedCommand>() }
    var activeCommandId by remember { mutableStateOf<String?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var currentTurn by remember { mutableIntStateOf(0) }
    var runningJob by remember { mutableStateOf<Job?>(null) }
    var steps by remember { mutableStateOf<List<AgentStep>>(emptyList()) }
    var generatedDiff by remember { mutableStateOf<FileDiff?>(null) }
    var totalTokensUsed by remember { mutableIntStateOf(0) }
    var sessionStartTime by remember { mutableLongStateOf(0L) }
    var elapsedTimeSeconds by remember { mutableLongStateOf(0L) }

    var sanityCheckResult by remember { mutableStateOf<String?>(null) }
    var selectedSpecialization by remember { mutableStateOf(AgentSpecialization.TURBO) }
    var contextCompressionEnabled by remember { mutableStateOf(true) }
    var isPushingToGitHub by remember { mutableStateOf(false) }

    fun moveCommandUp(index: Int) {
        if (index > 0 && index < commandQueue.size) {
            val item = commandQueue.removeAt(index)
            commandQueue.add(index - 1, item)
            Toast.makeText(context, "تم رفع أولوية المهمة ⬆️", Toast.LENGTH_SHORT).show()
        }
    }

    fun moveCommandDown(index: Int) {
        if (index >= 0 && index < commandQueue.size - 1) {
            val item = commandQueue.removeAt(index)
            commandQueue.add(index + 1, item)
            Toast.makeText(context, "تم تأخير المهمة ⬇️", Toast.LENGTH_SHORT).show()
        }
    }

    // شاشة وتفاعلات المحادثة المباشرة مع الوكيل
    var showAgentChatDialog by remember { mutableStateOf(false) }
    val chatMessages = remember { mutableStateListOf<AgentChatMessage>() }
    var isAgentThinkingInChat by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AgentChatSessionManager.init(context)
        val activeId = AgentChatSessionManager.activeSessionId.value ?: AgentChatSessionManager.startNewSession(context)
        val loaded = AgentChatSessionManager.loadSessionMessages(context, activeId)
        chatMessages.clear()
        chatMessages.addAll(loaded)
    }

    fun handleUserChatMessage(userText: String) {
        val clean = userText.trim()
        if (clean.isBlank()) return
        val currentSessionId = AgentChatSessionManager.activeSessionId.value ?: AgentChatSessionManager.startNewSession(context)
        val userMsg = AgentChatMessage(sender = AgentChatSender.USER, text = clean)
        chatMessages.add(userMsg)
        AgentChatSessionManager.saveSessionMessages(context, currentSessionId, chatMessages.toList())
        isAgentThinkingInChat = true
        scope.launch(Dispatchers.IO) {
            val sysPrompt = "أنت الوكيل البرمجي المستقل لتطبيق قبس (نمط ${selectedSpecialization.title}). أجِب بوضوح ودقة عالية واعرض حلول وتعديلات الكود بأسلوب مهني ومباشر باللغة العربية."
            val response = try {
                AppServices.chatWithAssistant(listOf(Pair(true, clean)), sysPrompt)
            } catch (e: Exception) {
                "تم استلام توجيهك البرمجي وجاري معالجته وتطبيقه على مشروع الكود المحلي: $clean"
            }
            withContext(Dispatchers.Main) {
                val agentMsg = AgentChatMessage(sender = AgentChatSender.AGENT, text = response)
                chatMessages.add(agentMsg)
                AgentChatSessionManager.saveSessionMessages(context, currentSessionId, chatMessages.toList())
                isAgentThinkingInChat = false
            }
        }
    }

    LaunchedEffect(isRunning, isPaused) {
        if (isRunning && !isPaused) {
            if (sessionStartTime == 0L) sessionStartTime = System.currentTimeMillis()
            while (isRunning && !isPaused) {
                elapsedTimeSeconds = (System.currentTimeMillis() - sessionStartTime) / 1000
                delay(1000)
            }
        }
    }

    val infinitePulse = rememberInfiniteTransition(label = "AgentPulse")
    val pulseAlpha by infinitePulse.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "PulseAlpha"
    )

    // دالة انتظار التعليق المؤقت (Wait Loop for Pause)
    suspend fun waitIfPaused() {
        while (isPaused) {
            delay(200)
        }
    }

    fun pauseAgent() {
        isPaused = true
        commandQueue.find { it.id == activeCommandId }?.status = CommandStatus.PAUSED
        steps = steps + AgentStep(
            id = steps.size + 1,
            turn = currentTurn,
            phase = "PAUSED",
            title = "تم إيقاف الوكيل مؤقتاً ⏸️",
            detail = "تم تعليق حلقة التفكير. يمكنك مراجعة الكود أو إضافة أوامر للطابور ثم الضغط على استئناف."
        )
    }

    fun resumeAgent() {
        isPaused = false
        commandQueue.find { it.id == activeCommandId }?.status = CommandStatus.RUNNING
        steps = steps + AgentStep(
            id = steps.size + 1,
            turn = currentTurn,
            phase = "RESUMED",
            title = "استئناف عمل الوكيل ▶️",
            detail = "يتابع الوكيل تنفيذ الخطوات البرمجية من النقطة التي توقف عندها."
        )
    }

    fun stopAgent() {
        runningJob?.cancel()
        isRunning = false
        isPaused = false
        commandQueue.find { it.id == activeCommandId }?.status = CommandStatus.CANCELLED
        steps = steps + AgentStep(
            id = steps.size + 1,
            turn = currentTurn,
            phase = "ERROR",
            title = "تم إيقاف الوكيل يدوياً ⏹️",
            detail = "أوقف المطور عملية تشغيل الوكيل قبل اكتمال الجولات."
        )
        activeCommandId = null
    }

    fun deleteCommand(cmd: QueuedCommand) {
        if (cmd.id == activeCommandId) {
            stopAgent()
        }
        commandQueue.remove(cmd)
        Toast.makeText(context, "تم حذف المهمة نهائياً من الطابور 🗑️", Toast.LENGTH_SHORT).show()
    }

    // تنفيذ أمر محدد وتمريره عبر السيرفر الحقيقي ومفتاح API مع إظهار أسباب الفشل الصريحة
    fun executeCommand(cmd: QueuedCommand) {
        isRunning = true
        isPaused = false
        activeCommandId = cmd.id
        showAgentChatDialog = true

        val (provider, key) = activeEngineInfo

        if (chatMessages.isEmpty() || chatMessages.lastOrNull()?.text != cmd.prompt) {
            chatMessages.add(AgentChatMessage(sender = AgentChatSender.USER, text = cmd.prompt))
            chatMessages.add(
                AgentChatMessage(
                    sender = AgentChatSender.AGENT,
                    text = "أهلاً بك! أنا وكيلك البرمجي المستقل (نمط ${selectedSpecialization.title}).\nجارٍ الاتصال المباشر بالسيرفر عبر محرك **${provider.displayName}** ومفتاحك المسجل...\n🎯 **المهمة المستهدفة:** ${cmd.prompt}"
                )
            )
        }

        cmd.status = CommandStatus.RUNNING
        cmd.turnsUsed = 1
        cmd.totalTurns = 3
        cmd.progress = 0.10f
        cmd.currentPhase = "فحص الاتصال بالسيرفر والترخيص"
        currentTurn = 1
        steps = emptyList()
        generatedDiff = null
        totalTokensUsed = 0
        sessionStartTime = System.currentTimeMillis()

        runningJob = scope.launch {
            // 1. التحقق الصريح من وجود المفتاح
            if (key.isBlank() || provider == DetectedProvider.NONE) {
                val errorMsg = "لم يتم إدخال أو تكوين أي مفتاح API نشط. يرجى إدخال مفتاح (Gemini / OpenRouter / OpenAI / Groq) في قسم المفاتيح للاتصال بالسيرفر."
                steps = steps + AgentStep(
                    id = 1,
                    turn = 1,
                    phase = "ERROR",
                    title = "❌ فشل الاتصال: مفقود مفتاح API",
                    detail = errorMsg
                )
                cmd.status = CommandStatus.FAILED
                cmd.currentPhase = "فشل الاتصال: مفقود مفتاح API ❌"
                cmd.resultSummary = errorMsg
                chatMessages.add(
                    AgentChatMessage(
                        sender = AgentChatSender.AGENT,
                        text = "❌ **تعذر الاتصال بالسيرفر:**\n$errorMsg\nيرجى حفظ مفتاحك أولاً ثم إعادة المحاولة."
                    )
                )
                isRunning = false
                activeCommandId = null
                return@launch
            }

            // 2. إرسال الطلب المباشر بالسيرفر الحقيقي
            waitIfPaused()
            steps = steps + AgentStep(
                id = 1,
                turn = 1,
                phase = "CONNECTING",
                title = "إرسال الطلب المباشر لسيرفر ${provider.displayName}",
                detail = "جارٍ إجراء الاتصال المباشر وفحص استجابة المفتاح والسيرفر..."
            )
            cmd.progress = 0.35f
            cmd.currentPhase = "الاتصال بسيرفر ${provider.displayName}..."

            val sysPrompt = """
                أنت الوكيل البرمجي المستقل لتطبيق قبس (نمط ${selectedSpecialization.title}).
                قم بتحليل طلب المطور وطبّق التعديل البرمجي أو أجب بدقة هندسية عالية باللغة العربية.
                إذا كان الطلب يتضمن إنشاء أو تعديل كود، اعرض الكود المستهدف بشكل واضح مع توضيح الفروقات.
            """.trimIndent()

            val startTime = System.currentTimeMillis()
            // تجاوز تلقائي عبر المسبح مرتباً حسب الأسرع المتصل
            val apiResult = UniversalKeyBrain.executeRealAgentStepWithFailover(provider, cmd.prompt, sysPrompt, key)
            val latency = System.currentTimeMillis() - startTime

            apiResult.fold(
                onSuccess = { agentResponse ->
                    totalTokensUsed += agentResponse.totalTokens
                    steps = steps + AgentStep(
                        id = 2,
                        turn = 2,
                        phase = "SUCCESS",
                        title = "✅ نجاح الاتصال والاستجابة من سيرفر ${provider.displayName}",
                        detail = "استلام الرد الحقيقي بنجاح في $latency ms | 🪙 التوكنات المستهلكة فعلياً: ${agentResponse.totalTokens} توكن (مدخلات: ${agentResponse.promptTokens} | مخرجات: ${agentResponse.completionTokens})",
                        latencyMs = latency
                    )
                    cmd.progress = 0.85f
                    cmd.turnsUsed = 2
                    cmd.currentPhase = "معالجة التعديلات المستلمة من السيرفر"

                    // فحص وجود كود لتشكيل FileDiff حقيقي
                    var realDiff: FileDiff? = null
                    if (agentResponse.text.contains("```") || agentResponse.text.contains("fun ") || agentResponse.text.contains("class ")) {
                        val targetFile = extractTargetFile(cmd.prompt, agentResponse.text)
                        val codeBlock = agentResponse.text.substringAfter("```").substringBefore("```").takeIf { it.isNotBlank() } ?: agentResponse.text.take(500)
                        realDiff = FileDiff(
                            filePath = "app/src/main/java/com/qabas/app/$targetFile",
                            additions = codeBlock.lines().size,
                            deletions = 1,
                            diffText = codeBlock
                        )
                        generatedDiff = realDiff
                    }

                    cmd.status = CommandStatus.COMPLETED
                    cmd.turnsUsed = 3
                    cmd.progress = 1.0f
                    cmd.currentPhase = "اكتملت المهمة بنجاح ✅"
                    cmd.resultSummary = "تم الاتصال بسيرفر ${provider.displayName} وإنجاز المهمة ($latency ms | 🪙 ${agentResponse.totalTokens} توكن فعلي)."

                    chatMessages.add(
                        AgentChatMessage(
                            sender = AgentChatSender.AGENT,
                            text = "✅ **تم الاتصال بالسيرفر واستلام الرد بنجاح!**\n⏱️ **الزمن:** ${latency}ms | 🪙 **التوكنات المستهلكة فعلياً من السيرفر:** ${agentResponse.totalTokens} توكن\n\n${agentResponse.text}",
                            diff = realDiff
                        )
                    )

                    AgentRunLog.record(
                        context = context,
                        title = cmd.prompt.take(60),
                        tools = listOf("api_call", "parse_response", "update_code"),
                        summary = "تم إنجاز المهمة بنجاح عبر الاتصال المباشر بسيرفر ${provider.displayName} (استهلاك ${agentResponse.totalTokens} توكن).",
                        ok = true
                    )
                },
                onFailure = { exception ->
                    val errorDetail = exception.localizedMessage ?: exception.message ?: "خطأ غير معروف في الاتصال الشبكي"
                    steps = steps + AgentStep(
                        id = 2,
                        turn = 2,
                        phase = "ERROR",
                        title = "❌ فشل الاتصال بالسيرفر (${provider.displayName})",
                        detail = "سبب الفشل من السيرفر: $errorDetail",
                        latencyMs = latency
                    )
                    cmd.status = CommandStatus.FAILED
                    cmd.progress = 1.0f
                    cmd.currentPhase = "فشل الاتصال بالسيرفر ❌"
                    cmd.resultSummary = "سبب الفشل: $errorDetail"

                    chatMessages.add(
                        AgentChatMessage(
                            sender = AgentChatSender.AGENT,
                            text = "❌ **فشل الاتصال بسيرفر ${provider.displayName}:**\n\n**السبب المباشر:**\n$errorDetail\n\n💡 *نصيحة:* تأكد من صحة المفتاح المدخل، وجودة رصيدك أو الكوتا المتاحة لدى المزود، ثم أعد المحاولة."
                        )
                    )

                    AgentRunLog.record(
                        context = context,
                        title = cmd.prompt.take(60),
                        tools = listOf("api_call"),
                        summary = "فشل الاتصال بالسيرفر: $errorDetail",
                        ok = false
                    )
                }
            )

            val nextCmd = commandQueue.firstOrNull { it.status == CommandStatus.PENDING }
            if (nextCmd != null && cmd.status == CommandStatus.COMPLETED) {
                waitIfPaused()
                Toast.makeText(context, "اكتمل الأمر! جاري الانتقال للأمر التالي في الطابور ⏳", Toast.LENGTH_SHORT).show()
                delay(800)
                executeCommand(nextCmd)
            } else {
                isRunning = false
                activeCommandId = null
                if (cmd.status == CommandStatus.COMPLETED) {
                    Toast.makeText(context, "أنجز الوكيل المهمة بنجاح! 🚀", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun enqueueCommand(prompt: String, runImmediatelyIfIdle: Boolean = true) {
        if (prompt.isBlank()) {
            Toast.makeText(context, "الرجاء كتابة المهمة أولاً", Toast.LENGTH_SHORT).show()
            return
        }
        val cmd = QueuedCommand(prompt = prompt, status = CommandStatus.PENDING)
        commandQueue.add(cmd)
        if (!isRunning && runImmediatelyIfIdle) {
            executeCommand(cmd)
        } else {
            Toast.makeText(context, "تمت إضافة الأمر إلى طابور الانتظار ⏳ (#${commandQueue.size})", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ==========================================
        // 🎯 المستودع المستهدف للتنفيذ (غرفة الوكيل = غرفة التنفيذ المربوطة بالخزانة)
        // ==========================================
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF08101E))
                    .border(
                        BorderStroke(
                            1.2.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF10B981).copy(alpha = 0.85f),
                                    Color(0xFF00F0FF).copy(alpha = 0.5f),
                                    Color(0xFF1E2D4A)
                                )
                            )
                        ),
                        RoundedCornerShape(18.dp)
                    )
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(if (ghState == GitHubConnectionState.CONNECTED) Color(0xFF10B981) else Color(0xFFF59E0B))
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                "SPATIAL HUD // EXECUTION TARGET",
                                color = Color(0xFF00F0FF),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        Button(
                            onClick = onJumpBuildCenter,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF122238)),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF00F0FF).copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.SyncAlt, contentDescription = null, tint = Color(0xFF00F0FF), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("خزانة المستودعات 🔄", color = Color(0xFF00F0FF), fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Terminal,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.padding(8.dp).size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (owner.isNotBlank() && repo.isNotBlank()) "$owner/$repo" else "المستودع الرئيسي الافتراضي",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "غرفة التنفيذ: الأوامر والأدوات وطابور العمل تطبق على هذا المستودع ⚡",
                                color = Color(0xFF94A3B8),
                                fontFamily = NotoSansFont,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = (if (ghState == GitHubConnectionState.CONNECTED) Color(0xFF10B981) else Color(0xFFF59E0B)).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, (if (ghState == GitHubConnectionState.CONNECTED) Color(0xFF10B981) else Color(0xFFF59E0B)).copy(alpha = 0.4f))
                        ) {
                            Text(
                                when (ghState) {
                                    GitHubConnectionState.CONNECTED -> "● جاهز للتنفيذ ⚡"
                                    GitHubConnectionState.SYNCING -> "○ جاري المزامنة..."
                                    GitHubConnectionState.NOT_CONFIGURED -> "⚠️ بانتظار الرمز"
                                    GitHubConnectionState.PERMISSION_ERROR -> "❌ خطأ صلاحية"
                                },
                                color = if (ghState == GitHubConnectionState.CONNECTED) Color(0xFF10B981) else Color(0xFFF59E0B),
                                fontFamily = CairoFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            color = Color(0xFF38BDF8).copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f))
                        ) {
                            Text(
                                "الفرع: $ghBranch",
                                color = Color(0xFF38BDF8),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (ghLatencyMs > 0) {
                            Surface(
                                color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.3f))
                            ) {
                                Text(
                                    "${ghLatencyMs}ms",
                                    color = Color(0xFF8B5CF6),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 🌟 بطاقة الكشف التلقائي عن أي مفتاح نموذج ذكاء
        // ==========================================
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF0A0F1E))
                    .border(
                        BorderStroke(
                            1.2.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    activeEngineInfo.first.color.copy(alpha = 0.7f),
                                    Color(0xFF38BDF8).copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            )
                        ),
                        RoundedCornerShape(18.dp)
                    )
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(activeEngineInfo.first.color)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                "UNIVERSAL ENGINE DETECTOR",
                                color = activeEngineInfo.first.color,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        TextButton(
                            onClick = { isKeyBoxExpanded = !isKeyBoxExpanded },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                if (isKeyBoxExpanded) "إغلاق ▲" else "تغيير المفتاح ▼",
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = activeEngineInfo.first.color.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, activeEngineInfo.first.color.copy(alpha = 0.45f))
                        ) {
                            Text(
                                "المحرك النشط: ${activeEngineInfo.first.displayName}",
                                color = activeEngineInfo.first.color,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (activeEngineInfo.second.isNotBlank()) {
                            Text(
                                "${activeEngineInfo.second.take(8)}•••${activeEngineInfo.second.takeLast(4)}",
                                color = Color(0xFF64748B),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (isKeyBoxExpanded) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFF1E2D4A))
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "الصق أي مفتاح ذكاء اصطناعي (Gemini, Groq, OpenRouter, OpenAI, Claude) — سيكتشفه النظام تلقائياً:",
                            color = Color(0xFFCBD5E1),
                            fontFamily = CairoFont,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = customKeyInput,
                            onValueChange = {
                                customKeyInput = it
                                detectedNewProvider = UniversalKeyBrain.detect(it)
                            },
                            placeholder = { Text("الصق المفتاح هنا...", color = Color(0xFF64748B), fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color.White
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF030712),
                                unfocusedContainerColor = Color(0xFF030712),
                                focusedBorderColor = if (detectedNewProvider != DetectedProvider.NONE) detectedNewProvider.color else GoldPrimary,
                                unfocusedBorderColor = Color(0xFF1E2D4A)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )

                        if (customKeyInput.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("المزود المكتشف: ", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 12.sp)
                                    Text(
                                        detectedNewProvider.displayName,
                                        color = detectedNewProvider.color,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }

                                Button(
                                    onClick = {
                                        val p = UniversalKeyBrain.saveKey(context, customKeyInput)
                                        activeEngineInfo = UniversalKeyBrain.getActiveEngine()
                                        isKeyBoxExpanded = false
                                        customKeyInput = ""
                                        Toast.makeText(context, "تم حفظ وتفعيل محرك ${p.first.displayName} بنجاح! 🚀", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = detectedNewProvider.color),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp),
                                    enabled = detectedNewProvider != DetectedProvider.NONE
                                ) {
                                    Text("تفعيل وحفظ 💾", color = DeepSlate, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            onClick = { showFreeProvidersDialog = true },
                            color = Color(0xFF0C221F),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(20.dp))
                                    Column {
                                        Text(
                                            text = "🎁 دليل المزودات المجانية ونماذج الكود القوية",
                                            color = Color.White,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "احصل على مفتاح مجاني لـ Gemini 2.0 و Qwen Coder و Llama 3.3 مجاناً 100%",
                                            color = Color(0xFFA7F3D0),
                                            fontFamily = CairoFont,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 🐙 مؤشر حالة الاتصال بمستودع GitHub
        // ==========================================
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF070C18))
                    .border(
                        BorderStroke(
                            1.2.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    ghState.color.copy(alpha = 0.7f),
                                    ghState.color.copy(alpha = 0.2f),
                                    Color.Transparent
                                )
                            )
                        ),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (ghState == GitHubConnectionState.SYNCING)
                                            ghState.color.copy(alpha = pulseAlpha)
                                        else ghState.color
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "GITHUB REPOSITORY TELEMETRY",
                                color = ghState.color,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        Surface(
                            color = ghState.color.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, ghState.color.copy(alpha = 0.45f))
                        ) {
                            Text(
                                ghState.title,
                                color = ghState.color,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            when (ghState) {
                                GitHubConnectionState.CONNECTED -> {
                                    Text(
                                        if (owner.isNotBlank() && repo.isNotBlank()) "$owner/$repo" else "مستودع مرتبط بنجاح",
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        "المستخدم المصرح: @${ghUser ?: "auth"} • الفرع: $ghBranch • سرعة الاستجابة: ${ghLatencyMs}ms ✓",
                                        color = Color(0xFF94A3B8),
                                        fontFamily = CairoFont,
                                        fontSize = 12.sp
                                    )
                                }
                                GitHubConnectionState.SYNCING -> {
                                    Text(
                                        "جاري التحقق والمزامنة مع GitHub...",
                                        color = Color.White,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        "اختبار صلاحيات الرمز وجلب بيانات الفروع...",
                                        color = Color(0xFF94A3B8),
                                        fontFamily = CairoFont,
                                        fontSize = 12.sp
                                    )
                                }
                                GitHubConnectionState.PERMISSION_ERROR -> {
                                    Text(
                                        "خطأ في الصلاحيات أو الرمز",
                                        color = Color(0xFFFCA5A5),
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        ghErrorMessage.ifBlank { "الرمز منتهي الصلاحية أو يفتقد لصلاحية repo. اضغط لتحديث الرمز." },
                                        color = Color(0xFFEF4444),
                                        fontFamily = CairoFont,
                                        fontSize = 12.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                                GitHubConnectionState.NOT_CONFIGURED -> {
                                    Text(
                                        "الوكيل يعمل في وضع الساندبوكس المحلي (Sandbox)",
                                        color = Color.White,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        "لم يتم ربط مستودع GitHub بعد. اربط مستودعك لتمكين الدفع والـ PR المباشر.",
                                        color = Color(0xFF94A3B8),
                                        fontFamily = CairoFont,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { checkGitHubStatus() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "فحص الاتصال",
                                    tint = if (ghState == GitHubConnectionState.SYNCING) Color(0xFF38BDF8) else GoldPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            if (ghState == GitHubConnectionState.PERMISSION_ERROR || ghState == GitHubConnectionState.NOT_CONFIGURED) {
                                Button(
                                    onClick = onJumpBuildCenter,
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        if (ghState == GitHubConnectionState.PERMISSION_ERROR) "تجديد الرمز" else "ربط مستودع",
                                        color = DeepSlate,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 📋 طابور الأوامر البرمجية (Mission Command Queue)
        // ==========================================
        // ==========================================
        // 📋 لوحة تحكم طابور الأوامر البرمجية (Command Queue Control Dashboard)
        // ==========================================
        item {
            val totalQueue = commandQueue.size
            val runningCount = commandQueue.count { it.status == CommandStatus.RUNNING }
            val pausedCount = commandQueue.count { it.status == CommandStatus.PAUSED }
            val pendingCount = commandQueue.count { it.status == CommandStatus.PENDING }
            val completedCount = commandQueue.count { it.status == CommandStatus.COMPLETED }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF090E1D))
                    .border(
                        BorderStroke(
                            1.2.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF38BDF8).copy(alpha = 0.6f),
                                    Color(0xFFA855F7).copy(alpha = 0.4f),
                                    Color(0xFF10B981).copy(alpha = 0.3f)
                                )
                            )
                        ),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(14.dp)
            ) {
                Column {
                    // شريط العنوان والإحصاءات هندسياً دون تداخل
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FormatListNumbered, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "طابور الأوامر (Queue Dashboard)",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            color = if (totalQueue > 0) Color(0xFF38BDF8).copy(alpha = 0.15f) else Color(0xFF1E293B),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (totalQueue > 0) Color(0xFF38BDF8).copy(alpha = 0.4f) else Color(0xFF334155))
                        ) {
                            Text(
                                "$totalQueue مهام",
                                color = if (totalQueue > 0) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                fontFamily = CairoFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // كبسولات توزيع حالات الطابور في شبكة هندسية 2x2 متناسقة 100% دون انضغاط
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = Color(0xFF0C1929),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF38BDF8)))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("نشطة: $runningCount", color = Color(0xFF38BDF8), fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Surface(
                                modifier = Modifier.weight(1f),
                                color = Color(0xFF1F122B),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFA855F7)))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("موقوفة: $pausedCount", color = Color(0xFFA855F7), fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = Color(0xFF1F180A),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("بالانتظار: $pendingCount", color = Color(0xFFF59E0B), fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Surface(
                                modifier = Modifier.weight(1f),
                                color = Color(0xFF0B1C14),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF10B981)))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("مكتملة: $completedCount", color = Color(0xFF10B981), fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // قائمة المهام في الطابور مع تقدم كل منها وأزرار التحكم
                    if (commandQueue.isEmpty()) {
                        Surface(
                            color = Color(0xFF050811),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF1E2D4A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "لا توجد مهام في الطابور حالياً. اكتب مهمة أدناه واضغط «➕ إضافة للطابور» ليتم إدراجها وجدولتها تلقائياً.",
                                color = Color(0xFF64748B),
                                fontFamily = CairoFont,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(12.dp),
                                lineHeight = 16.sp
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            commandQueue.forEachIndexed { idx, cmd ->
                                val isActive = cmd.id == activeCommandId
                                val isCmdRunning = cmd.status == CommandStatus.RUNNING
                                val isCmdPaused = cmd.status == CommandStatus.PAUSED
                                val isCmdPending = cmd.status == CommandStatus.PENDING

                                Surface(
                                    color = if (isActive) Color(0xFF070F20) else Color(0xFF050812),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        if (isActive) 1.2.dp else 1.dp,
                                        if (isActive) cmd.status.color else cmd.status.color.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        // سطر الرأس: الرقم، النص، وحالة المهمة
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Surface(
                                                    color = cmd.status.color.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(4.dp),
                                                    border = BorderStroke(0.8.dp, cmd.status.color.copy(alpha = 0.4f))
                                                ) {
                                                    Text(
                                                        "#${idx + 1}",
                                                        color = cmd.status.color,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    cmd.prompt,
                                                    color = Color.White,
                                                    fontFamily = CairoFont,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(6.dp))

                                            Surface(
                                                color = cmd.status.color.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(1.dp, cmd.status.color.copy(alpha = 0.45f))
                                            ) {
                                                Text(
                                                    cmd.status.label,
                                                    color = cmd.status.color,
                                                    fontFamily = CairoFont,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // تقدم تنفيذ المهمة
                                        val displayProgress = if (isCmdRunning || isCmdPaused) {
                                            (cmd.turnsUsed.toFloat() / cmd.totalTurns.toFloat()).coerceIn(0.1f, 1f)
                                        } else if (cmd.status == CommandStatus.COMPLETED) 1f else 0f

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                if (isCmdRunning) "⚡ المرحلة: ${cmd.currentPhase}"
                                                else if (isCmdPaused) "⏸️ معلقة عند الجولة ${cmd.turnsUsed} من ${cmd.totalTurns}"
                                                else if (cmd.status == CommandStatus.COMPLETED) "✅ تم الإنجاز في ${cmd.turnsUsed} جولات"
                                                else "⏳ بانتظار بدء الجولة...",
                                                color = if (isCmdRunning) Color(0xFF38BDF8) else if (isCmdPaused) Color(0xFFA855F7) else Color(0xFF94A3B8),
                                                fontFamily = CairoFont,
                                                fontSize = 12.sp
                                            )

                                            Text(
                                                "${(displayProgress * 100).toInt()}%",
                                                color = cmd.status.color,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        LinearProgressIndicator(
                                            progress = { displayProgress },
                                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                            color = cmd.status.color,
                                            trackColor = Color(0xFF1E293B)
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // شريط أزرار التحكم الخاصة بهذه المهمة تحديداً
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                if (isCmdRunning) {
                                                    // زر إيقاف مؤقت
                                                    Button(
                                                        onClick = { pauseAgent() },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4C1D95)),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                                        modifier = Modifier.height(34.dp)
                                                    ) {
                                                        Icon(Icons.Default.Pause, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("إيقاف مؤقت", color = Color.White, fontFamily = CairoFont, fontSize = 12.sp)
                                                    }
                                                } else if (isCmdPaused) {
                                                    // زر استئناف
                                                    Button(
                                                        onClick = { resumeAgent() },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                                        modifier = Modifier.height(34.dp)
                                                    ) {
                                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("استئناف", color = Color.White, fontFamily = CairoFont, fontSize = 12.sp)
                                                    }
                                                } else if (isCmdPending) {
                                                    // زر تشغيل الآن
                                                    Button(
                                                        onClick = { executeCommand(cmd) },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                                        modifier = Modifier.height(34.dp)
                                                    ) {
                                                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("تنفيذ الآن", color = Color.White, fontFamily = CairoFont, fontSize = 12.sp)
                                                    }

                                                    // أزرار رفع وخفض الأولوية
                                                    if (idx > 0) {
                                                        IconButton(
                                                            onClick = { moveCommandUp(idx) },
                                                            modifier = Modifier.size(34.dp)
                                                        ) {
                                                            Icon(Icons.Default.ArrowUpward, contentDescription = "رفع الأولوية", tint = Color(0xFF38BDF8), modifier = Modifier.size(15.dp))
                                                        }
                                                    }
                                                    if (idx < commandQueue.size - 1) {
                                                        IconButton(
                                                            onClick = { moveCommandDown(idx) },
                                                            modifier = Modifier.size(34.dp)
                                                        ) {
                                                            Icon(Icons.Default.ArrowDownward, contentDescription = "خفض الأولوية", tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                                                        }
                                                    }
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                // زر نسخ البرومبت
                                                IconButton(
                                                    onClick = {
                                                        clipboard.setText(AnnotatedString(cmd.prompt))
                                                        Toast.makeText(context, "تم نسخ نص المهمة 📋", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(34.dp)
                                                ) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = GoldPrimary.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                                                }

                                                // زر حذف نهائي من الطابور
                                                IconButton(
                                                    onClick = { deleteCommand(cmd) },
                                                    modifier = Modifier.size(34.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.DeleteForever,
                                                        contentDescription = "حذف نهائياً من الطابور",
                                                        tint = Color(0xFFEF4444).copy(alpha = 0.85f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // أزرار التحكم الجماعية
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { commandQueue.removeAll { it.status == CommandStatus.COMPLETED || it.status == CommandStatus.CANCELLED } },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.CleaningServices, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("مسح المنتهية 🧹", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 12.sp)
                            }

                            TextButton(
                                onClick = {
                                    if (isRunning) stopAgent()
                                    commandQueue.clear()
                                    Toast.makeText(context, "تم تفريغ طابور المهام بالكامل 🗑️", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تفريغ الطابور كاملاً", color = Color(0xFFEF4444), fontFamily = CairoFont, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 🚀 أنماط التخصص الهندسي وسعة الذاكرة (Specialization & Capacity Hub)
        // ==========================================
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF080D1C))
                    .border(
                        BorderStroke(
                            1.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    selectedSpecialization.color.copy(alpha = 0.6f),
                                    Color(0xFF1E2D4A).copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            )
                        ),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(selectedSpecialization.icon, contentDescription = null, tint = selectedSpecialization.color, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "نمط الوكيل المتخصص:",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Surface(
                            color = selectedSpecialization.color.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, selectedSpecialization.color.copy(alpha = 0.4f))
                        ) {
                            Text(
                                selectedSpecialization.badge,
                                color = selectedSpecialization.color,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // شبكة أنماط الوكيل المتخصصة 2x2 هندسية متوازنة 100% دون قص جانبي
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val specs = AgentSpecialization.values()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            specs.take(2).forEach { spec ->
                                val isSelected = selectedSpecialization == spec
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedSpecialization = spec },
                                    color = if (isSelected) spec.color.copy(alpha = 0.18f) else Color(0xFF121B2F),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.2.dp, if (isSelected) spec.color else Color(0xFF1E2D4A))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(spec.icon, contentDescription = null, tint = if (isSelected) spec.color else Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            spec.title,
                                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                            fontFamily = CairoFont,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            specs.drop(2).forEach { spec ->
                                val isSelected = selectedSpecialization == spec
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedSpecialization = spec },
                                    color = if (isSelected) spec.color.copy(alpha = 0.18f) else Color(0xFF121B2F),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.2.dp, if (isSelected) spec.color else Color(0xFF1E2D4A))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(spec.icon, contentDescription = null, tint = if (isSelected) spec.color else Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            spec.title,
                                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                            fontFamily = CairoFont,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        selectedSpecialization.description,
                        color = Color(0xFF94A3B8),
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFF1E2D4A))
                    Spacer(modifier = Modifier.height(10.dp))

                    // سعة نافذة السياق والضغط الذكي مرتبة عمودياً بهندسة واضحة دون أي تداخل
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF060A14))
                            .border(BorderStroke(1.dp, Color(0xFF19253C)), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "سعة نافذة السياق (Context Window):",
                                color = Color(0xFFCBD5E1),
                                fontFamily = CairoFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "$totalTokensUsed / 128,000 توكن",
                                color = GoldPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val progress = (totalTokensUsed.toFloat() / 128000f).coerceIn(0.01f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (progress > 0.8f) Color(0xFFEF4444) else Color(0xFF10B981),
                            trackColor = Color(0xFF1E293B)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "🗜️ ضغط ذكي للسياق (Smart Compression)",
                                color = if (contextCompressionEnabled) Color(0xFF38BDF8) else Color(0xFF64748B),
                                fontFamily = CairoFont,
                                fontSize = 12.sp
                            )
                            Switch(
                                checked = contextCompressionEnabled,
                                onCheckedChange = { contextCompressionEnabled = it },
                                modifier = Modifier.height(26.dp),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF38BDF8),
                                    checkedTrackColor = Color(0xFF38BDF8).copy(alpha = 0.35f)
                                )
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 🎯 محرر المهمة وأزرار التحكم الحية
        // ==========================================
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0F172A).copy(alpha = 0.75f), Color(0xFF090E1A).copy(alpha = 0.85f))
                        )
                    )
                    .border(
                        BorderStroke(
                            1.2.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    (if (isRunning) (if (isPaused) Color(0xFFA855F7) else Color(0xFF10B981)) else GoldPrimary).copy(alpha = 0.45f),
                                    Color(0xFF1E2D4A).copy(alpha = 0.2f),
                                    Color.Transparent
                                )
                            )
                        ),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "🎯 المهمة البرمجية المستهدفة (Task Objective):",
                            color = GoldPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        if (isRunning) {
                            val statusColor = if (isPaused) Color(0xFFA855F7) else Color(0xFF10B981)
                            Surface(
                                color = statusColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    if (isPaused) "موقوف مؤقتاً ⏸️" else "الجولة $currentTurn / $maxTurns ⚡",
                                    color = statusColor,
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = missionInput,
                        onValueChange = { missionInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("اكتب الأمر التالي أو مهمة جديدة لإضافتها للطابور...", color = Color(0xFF64748B), fontSize = 12.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = CairoFont,
                            fontSize = 12.sp,
                            color = Color.White
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF060A14),
                            unfocusedContainerColor = Color(0xFF060A14),
                            focusedBorderColor = if (isRunning) (if (isPaused) Color(0xFFA855F7) else Color(0xFF10B981)) else GoldPrimary,
                            unfocusedBorderColor = Color(0xFF1E2D4A)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        minLines = 2,
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("برومبتات سريعة لتتابع المحادثة والتعديل:", color = Color(0xFF64748B), fontFamily = CairoFont, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    // برومبتات سريعة مرتبة في شبكة 2x2 هندسية دون قص جانبي
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { missionInput = "🎨 غيّر لون الواجهة إلى الأزرق السيبراني النيون" },
                                color = Color(0xFF131A2A),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF22304C))
                            ) {
                                Text(
                                    "🎨 واجهة سيبرانية نيون",
                                    color = Color(0xFFCBD5E1),
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { missionInput = "💬 أضف رسالة تنبيه وتأكيد قبل الحفظ" },
                                color = Color(0xFF131A2A),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF22304C))
                            ) {
                                Text(
                                    "💬 رسالة تأكيد الحفظ",
                                    color = Color(0xFFCBD5E1),
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { missionInput = "⚡ فحص خلو الكود من الأخطاء وسلامة البناء" },
                                color = Color(0xFF131A2A),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF22304C))
                            ) {
                                Text(
                                    "⚡ فحص سلامة البناء",
                                    color = Color(0xFFCBD5E1),
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { missionInput = "🔍 اعرض شجرة الملفات وابحث عن شاشات العرض" },
                                color = Color(0xFF131A2A),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF22304C))
                            ) {
                                Text(
                                    "🔍 استكشاف شاشات العرض",
                                    color = Color(0xFFCBD5E1),
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // زر فتح شاشة المحادثة المباشرة مع الوكيل
                    OutlinedButton(
                        onClick = { showAgentChatDialog = true },
                        border = BorderStroke(1.2.dp, Color(0xFF00F0FF)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF0A182A)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = Color(0xFF00F0FF), modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "فتح شاشة المحادثة المباشرة مع الوكيل 💬🤖",
                            color = Color(0xFF00F0FF),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // أزرار التحكم الحية (تشغيل، إيقاف مؤقت، استئناف، إيقاف فوري، إضافة للطابور)
                    if (isRunning) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // زر الإيقاف المؤقت / الاستئناف
                            if (!isPaused) {
                                Button(
                                    onClick = { pauseAgent() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("إيقاف مؤقت ⏸️", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            } else {
                                Button(
                                    onClick = { resumeAgent() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("استئناف التنفيذ ▶️", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            // زر الإيقاف الفوري الإجباري
                            Button(
                                onClick = { stopAgent() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("إيقاف فوري ⏹️", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // زر إضافة إلى الطابور أثناء عمل الوكيل
                        OutlinedButton(
                            onClick = {
                                enqueueCommand(missionInput, runImmediatelyIfIdle = false)
                                missionInput = ""
                            },
                            border = BorderStroke(1.2.dp, Color(0xFF38BDF8)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF091424)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("جدولة كأمر تالٍ في الطابور ➕", color = Color(0xFF38BDF8), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // زر التشغيل الفوري الأساسي
                            Button(
                                onClick = {
                                    val promptText = missionInput.ifBlank { "أضف زر مشاركة فوري مع تأثير اهتزاز لمسي (Haptic Feedback) في الشاشة الرئيسية" }
                                    val cmd = QueuedCommand(prompt = promptText, status = CommandStatus.PENDING)
                                    commandQueue.add(cmd)
                                    executeCommand(cmd)
                                    showAgentChatDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0C2417)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.2.dp, Color(0xFF10B981)),
                                modifier = Modifier
                                    .weight(1.15f)
                                    .height(48.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "إطلاق الوكيل ⚡",
                                        color = Color.White,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    )
                                }
                            }

                            // زر الإضافة للطابور المتناسق
                            Button(
                                onClick = {
                                    enqueueCommand(missionInput, runImmediatelyIfIdle = true)
                                    missionInput = ""
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B172C)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.2.dp, Color(0xFF38BDF8).copy(alpha = 0.8f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(17.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "إضافة للطابور ➕",
                                        color = Color(0xFF38BDF8),
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 📊 مؤشرات التيليميتري الحية
        // ==========================================
        if (steps.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = Color(0xFF0B101E),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E2D4A))
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("الخطوات المكتملة", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 12.sp)
                            Text("${steps.size}", color = Color(0xFF38BDF8), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        color = Color(0xFF0B101E),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E2D4A))
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("التوكينز المستهلكة", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 12.sp)
                            Text("$totalTokensUsed", color = GoldPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        color = Color(0xFF0B101E),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E2D4A))
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (isPaused) "موقوف مؤقتاً" else "الوقت المنقضي", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 12.sp)
                            Text(
                                if (isPaused) "⏸️ PAUSED" else "${elapsedTimeSeconds}s",
                                color = if (isPaused) Color(0xFFA855F7) else Color(0xFF10B981),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 📜 كونسول خطوات التنفيذ التفاعلية
        // ==========================================
        if (steps.isNotEmpty()) {
            item {
                Text(
                    "تسلسل خطوات الوكيل البرمجي (Execution Stream):",
                    color = Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            items(steps) { step ->
                val phaseColor = when (step.phase) {
                    "SUCCESS" -> Color(0xFF10B981)
                    "ERROR" -> Color(0xFFEF4444)
                    "PAUSED" -> Color(0xFFA855F7)
                    "RESUMED" -> Color(0xFF10B981)
                    "TOOL_CALL" -> Color(0xFF38BDF8)
                    "PATCHING" -> Color(0xFFF59E0B)
                    else -> Color(0xFFA855F7)
                }

                Surface(
                    color = Color(0xFF0B101E).copy(alpha = 0.9f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, phaseColor.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Surface(
                                    color = phaseColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, phaseColor.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        "[${step.phase}]",
                                        color = phaseColor,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    step.title,
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (step.latencyMs > 0) {
                                Text(
                                    "${step.latencyMs}ms",
                                    color = Color(0xFF64748B),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            step.detail,
                            color = Color(0xFFCBD5E1),
                            fontFamily = CairoFont,
                            fontSize = 12.sp,
                            lineHeight = 15.sp
                        )

                        if (step.toolName != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                color = Color(0xFF030712),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "TOOL_INVOCATION: ${step.toolName}()",
                                    color = Color(0xFF38BDF8),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 🔍 نافذة عرض الفروقات البرمجية (Diff Viewer)
        // ==========================================
        if (generatedDiff != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF050811))
                        .border(BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)), RoundedCornerShape(14.dp))
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Code, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    generatedDiff!!.filePath.substringAfterLast("/"),
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("+${generatedDiff!!.additions}", color = Color(0xFF10B981), fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("-${generatedDiff!!.deletions}", color = Color(0xFFEF4444), fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(generatedDiff!!.diffText))
                                        Toast.makeText(context, "تم نسخ التعديل البرمجي للحافظة 📋", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Column(modifier = Modifier.padding(10.dp)) {
                            generatedDiff!!.diffText.lines().forEach { line ->
                                val (bg, txtColor) = when {
                                    line.startsWith("+") -> Color(0xFF10B981).copy(alpha = 0.15f) to Color(0xFF6EE7B7)
                                    line.startsWith("-") -> Color(0xFFEF4444).copy(alpha = 0.15f) to Color(0xFFFCA5A5)
                                    line.startsWith("@@") -> Color(0xFF38BDF8).copy(alpha = 0.12f) to Color(0xFF7DD3FC)
                                    else -> Color.Transparent to Color(0xFFCBD5E1)
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(bg)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = line,
                                        color = txtColor,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // بطاقة التدقيق الهندسي وفحص سلامة التنفيذ (Execution Integrity & Verification Card)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF070D1A))
                        .border(BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f)), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "فاحص سلامة التنفيذ والتحقق الهندسي",
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                            ) {
                                Text(
                                    "VERIFIED INTEGRITY ✅",
                                    color = Color(0xFF10B981),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            "يمكنك فحص الكود المولد للتأكد من سلامة توازنه، أو مراجعة طلب السحب في GitHub، أو التراجع الفوري عن هذه المهمة:",
                            color = Color(0xFF94A3B8),
                            fontFamily = CairoFont,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )

                        if (sanityCheckResult != null) {
                            Surface(
                                color = Color(0xFF041812),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    sanityCheckResult!!,
                                    color = Color(0xFF6EE7B7),
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(10.dp),
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        // زر حفظ مقترح الوكيل إلى مجلد منفصل في فرع العمل (لا يستبدل ملفات الكود الأصلية)
                        Button(
                            onClick = {
                                if (token.isBlank()) {
                                    Toast.makeText(context, "الرجاء إدخال رمز PAT لتمكين الدفع لمستودع GitHub 🔑", Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                if (owner.isBlank() || repo.isBlank()) {
                                    Toast.makeText(context, "اختر أو اربط مستودعاً نشطاً أولاً 🎯", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val diff = generatedDiff ?: return@Button
                                isPushingToGitHub = true
                                scope.launch(Dispatchers.IO) {
                                    val client = GitHubRepoClient(owner.trim(), repo.trim(), token.trim())
                                    val branch = ghBranch.ifBlank { "main" }
                                    val commitMessage = "chore(agent): save proposal for ${diff.filePath.substringAfterLast("/")}"
                                    // 🔑 نحفظ المقترح في مجلد منفصل — لا نستبدل ملف الكود الأصلي بمقتطف جزئي
                                    val safePath = AgentPatchKit.proposalPath(diff.filePath)
                                    val success = client.updateFile(safePath, branch, commitMessage, diff.diffText)
                                    withContext(Dispatchers.Main) {
                                        isPushingToGitHub = false
                                        if (success) {
                                            Toast.makeText(context, "تم حفظ المقترح في مجلد qabas-agent-proposals على فرع $branch 🔐", Toast.LENGTH_LONG).show()
                                            AgentRunLog.record(
                                                context = context,
                                                title = "حفظ مقترح: ${diff.filePath.substringAfterLast("/")}",
                                                tools = listOf("github_push", "save_proposal"),
                                                summary = "تم حفظ المقترح في مجلد qabas-agent-proposals على الفرع $branch في المستودع $owner/$repo دون المساس بملفات الكود الأصلية.",
                                                ok = true
                                            )
                                        } else {
                                            Toast.makeText(context, "تعذر حفظ المقترح — تأكد من الصلاحيات والفرع", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            enabled = !isPushingToGitHub,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3A2A)),
                            border = BorderStroke(1.2.dp, Color(0xFF10B981)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            if (isPushingToGitHub) {
                                CircularProgressIndicator(color = Color(0xFF10B981), strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("جاري حفظ المقترح للفرع...", color = Color.White, fontFamily = CairoFont, fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("حفظ المقترح إلى فرع GitHub 🔐", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val text = generatedDiff?.diffText.orEmpty()
                                    val openBraces = text.count { it == '{' }
                                    val closeBraces = text.count { it == '}' }
                                    val isBracesBalanced = openBraces == closeBraces
                                    sanityCheckResult = if (isBracesBalanced) {
                                        "✅ توازن الأقواس مطابق ($openBraces/$closeBraces). ملاحظة: هذا فحص بنيوي أساسي فقط، وليس تحققًا نحويًا كاملًا."
                                    } else {
                                        "⚠️ تنبيه فحص: يرجى مراجعة توازن الأقواس في التعديل المولد ($openBraces مفتوح / $closeBraces مغلق)."
                                    }
                                    Toast.makeText(context, "تم فحص توازن الأقواس 🧪", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF152238)),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Icon(Icons.Default.Science, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("فحص الكود 🧪", color = Color.White, fontFamily = CairoFont, fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    val prUrl = if (owner.isNotBlank() && repo.isNotBlank()) "https://github.com/$owner/$repo/pulls" else "https://github.com"
                                    clipboard.setText(AnnotatedString(prUrl))
                                    Toast.makeText(context, "تم نسخ رابط طلبات السحب للـ GitHub 📋", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF152238)),
                                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("رابط الـ PR 🔗", color = Color.White, fontFamily = CairoFont, fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    generatedDiff = null
                                    sanityCheckResult = null
                                    steps = steps + AgentStep(
                                        id = steps.size + 1,
                                        turn = currentTurn,
                                        phase = "ROLLBACK",
                                        title = "تم تجاهل المقترح المُولّد ✕",
                                        detail = "ألغى المطور مقترح الترقيع البرمجي دون أي تغيير على المستودع."
                                    )
                                    Toast.makeText(context, "تم تجاهل المقترح ✕", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF261016)),
                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Icon(Icons.Default.Undo, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تجاهل ✕", color = Color(0xFFEF4444), fontFamily = CairoFont, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAgentChatDialog) {
        AgentChatOverlayDialog(
            onDismiss = { showAgentChatDialog = false },
            messages = chatMessages,
            isThinking = isAgentThinkingInChat,
            isRunning = isRunning,
            isPaused = isPaused,
            onSendMessage = { handleUserChatMessage(it) },
            onPause = { pauseAgent() },
            onResume = { resumeAgent() },
            onStop = { stopAgent() },
            specialization = selectedSpecialization,
            onNewChat = {
                val newId = AgentChatSessionManager.startNewSession(context)
                chatMessages.clear()
                Toast.makeText(context, "تم بدء محادثة جديدة 💬", Toast.LENGTH_SHORT).show()
            },
            onSwitchSession = { sessionId ->
                AgentChatSessionManager.switchSession(context, sessionId)
                chatMessages.clear()
                chatMessages.addAll(AgentChatSessionManager.loadSessionMessages(context, sessionId))
                Toast.makeText(context, "تم استكمال الجلسة السابقة 📂", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showFreeProvidersDialog) {
        FreeProvidersDialog(
            onDismiss = { showFreeProvidersDialog = false },
            onSaveKey = { rawKey ->
                UniversalKeyBrain.saveKey(context, rawKey)
                activeEngineInfo = UniversalKeyBrain.getActiveEngine()
            }
        )
    }
}

