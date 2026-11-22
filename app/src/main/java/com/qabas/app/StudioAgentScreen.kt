package com.qabas.app

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

/**
 * وكيل الاستوديو الموحّد — محادثة واحدة بحلقة كاملة:
 * فهم ← توضيح (3 أسئلة كحد أقصى بأزرار سريعة) ← خطة قابلة للموافقة
 * (مشاهد + أصول + تكلفة تقديرية موسومة «تقريبي» + مدة متوقعة)
 * ← تنفيذ مرئي بخطوات (سكريبت ← صور ← صوت ← فيديو)
 * ← نتائج داخل المحادثة كبطاقات (معاينة المسار + إعادة توليد موضعية
 * للصورة والصوت + إنشاء مشروع من السكريبت + نشر الفيديو كريلز)
 * ← حفظ/نشر.
 *
 * حدود صريحة:
 * - توليد الفيديو متزامن داخل أداة `generate_video` (استطلاع Higgsfield
 *   حتى ~6 دقائق) — الطابور السحابي يسجّل النتيجة المنتهية فقط،
 *   ولا يحرّك تنفيذاً غير متزامن بعد (لا WorkManager ولا إشعار اكتمال
 *   في التطبيق).
 * - تكلفة الخطة تقديرات ثابتة موسومة «تقريبي»؛ التكلفة الفعلية لكل توليد
 *   تُسجَّل 0 في `ai_generations.cost_usd` حتى تُقاس من المزود.
 * - وكيل البرمجة (AiStudioAgentScreen) منفصل تماماً خلف «وضع المطوّر»
 *   وبوابة [AdminGuard]؛ هذا الوكيل للمحتوى فقط ولا يعدّل كود التطبيق.
 */
private enum class AgentPhase { IDLE, CLARIFYING, PLAN_PENDING, EXECUTING, DONE }

private enum class ChatKind {
    USER, AGENT_TEXT, QUESTIONS, PLAN, STEP, RESULT_SCRIPT, RESULT_IMAGE, RESULT_VOICE, RESULT_VIDEO
}

private data class StudioPlan(
    val scenes: Int,
    val voice: String,
    val style: String,
    val durationSeconds: Int,
    val estimatedCostText: String,
    val expectedMinutes: Int
)

private data class ChatItem(
    val id: String = UUID.randomUUID().toString(),
    val kind: ChatKind,
    val text: String = "",
    val plan: StudioPlan? = null,
    val stepState: String = "",
    val filePath: String? = null,
    val prompt: String? = null
)

private val VOICE_OPTIONS = listOf("حامد", "راوٍ هادئ", "حماسي")
private val STYLE_OPTIONS = listOf("مسجد", "مخطوطة", "طبيعة")
private val DURATION_OPTIONS = listOf("15 ثانية", "30 ثانية", "60 ثانية")

private suspend fun runStudioTool(context: Context, name: String, args: JSONObject): String {
    val def = AiTools.definitions(context).firstOrNull { it.name == name }
        ?: return "الأداة $name غير متوفرة."
    return try {
        def.run(args)
    } catch (e: Exception) {
        "فشل تنفيذ $name: ${e.message ?: "خطأ غير معروف"}"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioAgentScreen(
    onBack: () -> Unit,
    onOpenCodingAgent: () -> Unit = {},
    onOpenKeys: () -> Unit = {},
    onOpenProject: (ProjectService.Project) -> Unit = {},
    initialBrief: String? = null,
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatItem>() }
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf(AgentPhase.IDLE) }
    var brief by remember { mutableStateOf("") }
    var voiceAnswer by remember { mutableStateOf<String?>(null) }
    var styleAnswer by remember { mutableStateOf<String?>(null) }
    var durationAnswer by remember { mutableStateOf<String?>(null) }
    var pendingPlan by remember { mutableStateOf<StudioPlan?>(null) }
    var executing by remember { mutableStateOf(false) }
    var showDevModeDialog by remember { mutableStateOf(false) }
    var statusLine by remember { mutableStateOf("") }

    LaunchedEffect(initialBrief) {
        if (!initialBrief.isNullOrBlank() && input.isBlank() && messages.isEmpty()) {
            input = initialBrief
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun askClarifyingQuestions() {
        phase = AgentPhase.CLARIFYING
        messages.add(
            ChatItem(
                kind = ChatKind.QUESTIONS,
                text = "قبل أن أبدأ، ثلاثة أسئلة سريعة — أجب بما يناسبك أو تخطَّ:"
            )
        )
    }

    fun buildPlan(): StudioPlan {
        val durationSeconds = when (durationAnswer) {
            "15 ثانية" -> 15
            "60 ثانية" -> 60
            else -> 30
        }
        val scenes = when (durationSeconds) {
            15 -> 3
            60 -> 5
            else -> 4
        }
        return StudioPlan(
            scenes = scenes,
            voice = voiceAnswer ?: "حامد",
            style = styleAnswer ?: "مسجد",
            durationSeconds = durationSeconds,
            estimatedCostText = "التكلفة التقديرية: مجاني بالبدائل المجانية " +
                "(Pollinations/FLUX وEdge TTS)؛ وبالمدفوع حسب مزودك — تقريبي، " +
                "راجع شاشة «مفاتيحي» لأسعار مزودك.",
            expectedMinutes = 3 + scenes
        )
    }

    fun submitBrief() {
        val text = input.trim()
        if (text.isBlank() || phase == AgentPhase.EXECUTING) return
        brief = text
        input = ""
        voiceAnswer = null
        styleAnswer = null
        durationAnswer = null
        messages.add(ChatItem(kind = ChatKind.USER, text = text))
        messages.add(
            ChatItem(
                kind = ChatKind.AGENT_TEXT,
                text = "فهمت: «$text». سأصنع لك ريلز دعوياً بهذا الموضوع."
            )
        )
        askClarifyingQuestions()
    }

    fun maybeShowPlan() {
        if (voiceAnswer != null && styleAnswer != null && durationAnswer != null) {
            val plan = buildPlan()
            pendingPlan = plan
            phase = AgentPhase.PLAN_PENDING
            messages.add(ChatItem(kind = ChatKind.PLAN, plan = plan))
        }
    }

    fun executePlan(plan: StudioPlan) {
        if (executing) return
        executing = true
        phase = AgentPhase.EXECUTING
        scope.launch {
            // 1) السكربت
            val scriptStep = ChatItem(kind = ChatKind.STEP, text = "يكتب السكربت…", stepState = "جارٍ")
            messages.add(scriptStep)
            val script = runStudioTool(
                context, "generate_script",
                JSONObject().put("topic", brief)
            )
            replaceItem(messages, scriptStep.id) {
                it.copy(stepState = if (script.isBlank()) "فشل" else "اكتمل")
            }
            var scriptText = script
            if (script.isNotBlank()) {
                messages.add(ChatItem(kind = ChatKind.RESULT_SCRIPT, text = script))
            } else {
                scriptText = brief
                messages.add(
                    ChatItem(
                        kind = ChatKind.AGENT_TEXT,
                        text = "تعذّر توليد السكربت تلقائياً — سأكمل بالموضوع نفسه كنص أساسي."
                    )
                )
            }

            // 2) الصور — مشهداً مشهداً (3 كحد أقصى في التنفيذ الواحد)
            val imageCount = minOf(plan.scenes, 3)
            for (scene in 1..imageCount) {
                val step = ChatItem(
                    kind = ChatKind.STEP,
                    text = "يولّد الصورة $scene من $imageCount…",
                    stepState = "جارٍ"
                )
                messages.add(step)
                val prompt = "$brief — مشهد $scene بأسلوب ${plan.style}، إضاءة سينمائية هادئة"
                val imagePath = runStudioTool(
                    context, "generate_image",
                    JSONObject().put("prompt", prompt)
                )
                val ok = imagePath.isNotBlank() && !imagePath.startsWith("تعذّر")
                replaceItem(messages, step.id) {
                    it.copy(stepState = if (ok) "اكتمل" else "فشل")
                }
                if (ok) {
                    messages.add(
                        ChatItem(
                            kind = ChatKind.RESULT_IMAGE,
                            text = "صورة المشهد $scene",
                            filePath = imagePath,
                            prompt = prompt
                        )
                    )
                } else {
                    messages.add(
                        ChatItem(
                            kind = ChatKind.AGENT_TEXT,
                            text = "تعذّر توليد صورة المشهد $scene: $imagePath"
                        )
                    )
                }
            }

            // 3) التعليق الصوتي
            val voiceStep = ChatItem(kind = ChatKind.STEP, text = "يسجّل التعليق الصوتي…", stepState = "جارٍ")
            messages.add(voiceStep)
            val voicePath = runStudioTool(
                context, "generate_voice",
                JSONObject()
                    .put("text", scriptText.take(2000))
                    .put("tone", plan.voice)
            )
            val voiceOk = voicePath.isNotBlank() && !voicePath.startsWith("تعذّر")
            replaceItem(messages, voiceStep.id) {
                it.copy(stepState = if (voiceOk) "اكتمل" else "فشل")
            }
            if (voiceOk) {
                messages.add(
                    ChatItem(
                        kind = ChatKind.RESULT_VOICE,
                        text = "التعليق الصوتي (${plan.voice})",
                        filePath = voicePath,
                        prompt = scriptText.take(2000)
                    )
                )
            } else {
                messages.add(
                    ChatItem(
                        kind = ChatKind.AGENT_TEXT,
                        text = "تعذّر توليد التعليق الصوتي: $voicePath"
                    )
                )
            }

            // 4) الفيديو — متزامن داخل الأداة (استطلاع حتى ~6 دقائق)
            val videoStep = ChatItem(
                kind = ChatKind.STEP,
                text = "يولّد الفيديو عبر Kling (قد يستغرق دقائق)…",
                stepState = "جارٍ"
            )
            messages.add(videoStep)
            val videoPath = runStudioTool(
                context, "generate_video",
                JSONObject()
                    .put("prompt", "$brief — أسلوب ${plan.style}")
                    .put("duration_seconds", plan.durationSeconds.coerceIn(5, 30))
            )
            val videoOk = videoPath.isNotBlank() && !videoPath.startsWith("تعذّر")
            replaceItem(messages, videoStep.id) {
                it.copy(stepState = if (videoOk) "اكتمل" else "فشل")
            }
            if (videoOk) {
                messages.add(
                    ChatItem(
                        kind = ChatKind.RESULT_VIDEO,
                        text = "الفيديو النهائي",
                        filePath = videoPath,
                        prompt = brief
                    )
                )
            } else {
                messages.add(
                    ChatItem(
                        kind = ChatKind.AGENT_TEXT,
                        text = "تعذّر توليد الفيديو: $videoPath — الصور والصوت أعلاه محفوظان ويمكنك إكمال المونتاج يدوياً."
                    )
                )
            }

            messages.add(
                ChatItem(
                    kind = ChatKind.AGENT_TEXT,
                    text = "اكتمل التنفيذ. يمكنك إعادة توليد أي عنصر، أو حفظ العمل كمشروع، أو نشر الفيديو كريلز."
                )
            )
            executing = false
            phase = AgentPhase.DONE
        }
    }

    fun regenerate(item: ChatItem) {
        scope.launch {
            statusLine = "إعادة توليد…"
            val toolName = if (item.kind == ChatKind.RESULT_IMAGE) "generate_image" else "generate_voice"
            val args = if (item.kind == ChatKind.RESULT_IMAGE) {
                JSONObject().put("prompt", item.prompt ?: brief)
            } else {
                JSONObject().put("text", item.prompt ?: brief).put("tone", pendingPlan?.voice ?: "حامد")
            }
            val result = runStudioTool(context, toolName, args)
            val ok = result.isNotBlank() && !result.startsWith("تعذّر")
            if (ok) {
                replaceItem(messages, item.id) { it.copy(filePath = result) }
                statusLine = "تمت إعادة التوليد."
            } else {
                statusLine = "فشلت إعادة التوليد: $result"
            }
        }
    }

    fun saveAsProject(script: String) {
        scope.launch {
            val now = System.currentTimeMillis()
            val project = ProjectService.Project(
                id = UUID.randomUUID().toString(),
                title = brief.take(80).ifBlank { "مشروع جديد" },
                idea = brief,
                script = script,
                status = "draft",
                lastScreen = "INPUT",
                createdAt = now,
                updatedAt = now
            )
            runCatching { ProjectService(context).saveProject(project) }
            statusLine = "تم حفظ المشروع."
            onOpenProject(project)
        }
    }

    fun publishVideo(item: ChatItem) {
        val path = item.filePath ?: return
        scope.launch {
            statusLine = "جارٍ نشر الفيديو كريلز…"
            val result = runStudioTool(
                context, "publish_to_reels",
                JSONObject()
                    .put("video_path", path)
                    .put("title", brief.take(100).ifBlank { "ريلز دعوي" })
                    .put("description", brief)
            )
            statusLine = result
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("وكيل الاستوديو") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    TextButton(onClick = { showDevModeDialog = true }) { Text("وضع المطوّر") }
                    TextButton(onClick = onOpenKeys) { Text("مفاتيحي") }
                }
            )
        },
        bottomBar = bottomBar
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            if (messages.isEmpty()) {
                Text(
                    "قل لي ماذا تصنع — مثال: «اصنع لي ريلز 30 ثانية عن الصبر».",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { item ->
                    when (item.kind) {
                        ChatKind.USER -> MessageCard(item.text, isUser = true)
                        ChatKind.AGENT_TEXT -> MessageCard(item.text, isUser = false)
                        ChatKind.QUESTIONS -> QuestionsCard(
                            voiceAnswer = voiceAnswer,
                            styleAnswer = styleAnswer,
                            durationAnswer = durationAnswer,
                            onVoice = { voiceAnswer = it; maybeShowPlan() },
                            onStyle = { styleAnswer = it; maybeShowPlan() },
                            onDuration = { durationAnswer = it; maybeShowPlan() },
                            onSkip = {
                                voiceAnswer = voiceAnswer ?: "حامد"
                                styleAnswer = styleAnswer ?: "مسجد"
                                durationAnswer = durationAnswer ?: "30 ثانية"
                                maybeShowPlan()
                            }
                        )
                        ChatKind.PLAN -> {
                            val plan = item.plan ?: return@items
                            PlanCard(
                                plan = plan,
                                enabled = phase == AgentPhase.PLAN_PENDING && !executing,
                                onApprove = { executePlan(plan) },
                                onEdit = {
                                    messages.add(
                                        ChatItem(
                                            kind = ChatKind.AGENT_TEXT,
                                            text = "غيّر إجاباتك أعلاه (الصوت/الأسلوب/المدة) ثم أرسل موضوعاً جديداً لخطة معدّلة."
                                        )
                                    )
                                }
                            )
                        }
                        ChatKind.STEP -> StepRow(item.text, item.stepState)
                        ChatKind.RESULT_SCRIPT -> ResultCard(
                            title = "السكريبت",
                            body = item.text,
                            filePath = null,
                            onRegenerate = null,
                            onSaveProject = { saveAsProject(item.text) },
                            onPublish = null
                        )
                        ChatKind.RESULT_IMAGE -> ResultCard(
                            title = item.text,
                            body = "مسار الملف: ${item.filePath}",
                            filePath = item.filePath,
                            onRegenerate = { regenerate(item) },
                            onSaveProject = null,
                            onPublish = null
                        )
                        ChatKind.RESULT_VOICE -> ResultCard(
                            title = item.text,
                            body = "مسار الملف: ${item.filePath}",
                            filePath = item.filePath,
                            onRegenerate = { regenerate(item) },
                            onSaveProject = null,
                            onPublish = null
                        )
                        ChatKind.RESULT_VIDEO -> ResultCard(
                            title = item.text,
                            body = "مسار الملف: ${item.filePath}",
                            filePath = item.filePath,
                            onRegenerate = null,
                            onSaveProject = null,
                            onPublish = { publishVideo(item) }
                        )
                    }
                }
            }
            if (statusLine.isNotBlank()) {
                Text(statusLine, style = MaterialTheme.typography.bodyMedium)
            }
            if (executing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("اكتب موضوع الريلز…") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(
                    onClick = { submitBrief() },
                    enabled = input.isNotBlank() && !executing
                ) {
                    Icon(Icons.Default.Send, contentDescription = "إرسال")
                }
            }
        }
    }

    if (showDevModeDialog) {
        AlertDialog(
            onDismissRequest = { showDevModeDialog = false },
            title = { Text("وضع المطوّر — ما الفرق؟") },
            text = {
                Text(
                    "وكيل الاستوديو هذا وكيل محتوى: يكتب السكربت ويولّد الصور والصوت " +
                        "والفيديو وينشر الريلز — ولا يمسّ كود التطبيق أبداً.\n\n" +
                        "وكيل البرمجة (وضع المطوّر) وكيل مختلف تماماً: يعدّل كود التطبيق " +
                        "نفسه (مهام وخطط وفروقات مقترحة وبناء). وهو محروس بصلاحية المطوّر " +
                        "من Supabase، فلا يظهر إلا لمن يملك الرتبة."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDevModeDialog = false
                        onOpenCodingAgent()
                    }
                ) { Text("فتح وكيل البرمجة") }
            },
            dismissButton = {
                TextButton(onClick = { showDevModeDialog = false }) { Text("إغلاق") }
            }
        )
    }
}

private fun replaceItem(
    messages: MutableList<ChatItem>,
    id: String,
    transform: (ChatItem) -> ChatItem
) {
    val index = messages.indexOfFirst { it.id == id }
    if (index >= 0) messages[index] = transform(messages[index])
}

@Composable
private fun MessageCard(text: String, isUser: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(text, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun QuestionsCard(
    voiceAnswer: String?,
    styleAnswer: String?,
    durationAnswer: String?,
    onVoice: (String) -> Unit,
    onStyle: (String) -> Unit,
    onDuration: (String) -> Unit,
    onSkip: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("أسئلة توضيحية (3 كحد أقصى)", style = MaterialTheme.typography.titleSmall)
            Text("أي صوت؟", style = MaterialTheme.typography.bodyMedium)
            QuickOptions(VOICE_OPTIONS, voiceAnswer, onVoice)
            Text("أي أسلوب بصري؟", style = MaterialTheme.typography.bodyMedium)
            QuickOptions(STYLE_OPTIONS, styleAnswer, onStyle)
            Text("ما المدة؟", style = MaterialTheme.typography.bodyMedium)
            QuickOptions(DURATION_OPTIONS, durationAnswer, onDuration)
            TextButton(onClick = onSkip) { Text("تخطَّ — استخدم الافتراضي") }
        }
    }
}

@Composable
private fun QuickOptions(
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            OutlinedButton(
                onClick = { onSelect(option) },
                enabled = selected != option
            ) { Text(option) }
        }
    }
}

@Composable
private fun PlanCard(
    plan: StudioPlan,
    enabled: Boolean,
    onApprove: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("خطة الإنتاج — قابلة للموافقة أو التعديل", style = MaterialTheme.typography.titleSmall)
            Text("المشاهد: ${plan.scenes} مشاهد")
            Text("الأصول المطلوبة: ${plan.scenes} صور + تعليق صوتي + فيديو واحد")
            Text("الصوت: ${plan.voice} — الأسلوب: ${plan.style}")
            Text("المدة المتوقعة: نحو ${plan.expectedMinutes} دقائق تنفيذ")
            Text(plan.estimatedCostText)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApprove, enabled = enabled) { Text("وافق ونفّذ") }
                OutlinedButton(onClick = onEdit) { Text("عدّل") }
            }
        }
    }
}

@Composable
private fun StepRow(text: String, state: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state == "جارٍ") CircularProgressIndicator()
        Text("$text — $state", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ResultCard(
    title: String,
    body: String,
    filePath: String?,
    onRegenerate: (() -> Unit)?,
    onSaveProject: (() -> Unit)?,
    onPublish: (() -> Unit)?
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (filePath != null) {
                Text("المعاينة: $filePath", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onRegenerate?.let {
                    OutlinedButton(onClick = it) { Text("إعادة توليد") }
                }
                onSaveProject?.let {
                    Button(onClick = it) { Text("إنشاء مشروع من السكريبت") }
                }
                onPublish?.let {
                    Button(onClick = it) { Text("انشر كريلز") }
                }
            }
        }
    }
}

/**
 * شاشة قفل وضع المطوّر: تظهر لمن لا يملك رتبة المطوّر السحابية
 * (app_metadata فقط عبر [AdminGuard.isDashboardAccessAllowed]) عند محاولة
 * فتح وكيل البرمجة. وكيل المحتوى (هذه الشاشة) غير مقيّد.
 */
@Composable
fun DeveloperModeLockedScreen(
    onBack: () -> Unit,
    onOpenStudioAgent: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, contentDescription = null)
        Spacer(modifier = Modifier.height(12.dp))
        Text("وضع المطوّر مقفل", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "وكيل البرمجة يعدّل كود التطبيق نفسه، وهو متاح فقط لمن يملك رتبة " +
                "المطوّر في حسابه (تُمنح من لوحة المطور في Supabase). " +
                "وكيل الاستوديو للمحتوى متاح لك بالكامل — اصنع الريلز والصور والصوت من هناك.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpenStudioAgent) { Text("فتح وكيل الاستوديو") }
            OutlinedButton(onClick = onBack) { Text("رجوع") }
        }
    }
}
