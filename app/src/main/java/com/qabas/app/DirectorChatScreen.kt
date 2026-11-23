package com.qabas.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class DirectorBrief(
    val idea: String = "",
    val contentType: String = "",
    val contentTone: String = "",
    val videoDuration: String = "",
    val selectedRatio: String = "9:16"
) {
    fun missingSlots(): List<String> {
        val m = mutableListOf<String>()
        if (idea.isBlank()) m.add("idea")
        if (contentType.isBlank()) m.add("type")
        if (contentTone.isBlank()) m.add("tone")
        if (videoDuration.isBlank()) m.add("duration")
        return m
    }
}

internal data class DirectorMsg(val fromDirector: Boolean, val text: String)

private val SlotOptions = mapOf(
    "type" to listOf("قصة", "تدبر قرآني", "حديث شريف", "موعظة", "سيرة"),
    "tone" to listOf("خاشع", "حماسي", "ملحمي", "هادئ"),
    "duration" to listOf("15 ثانية", "30 ثانية", "60 ثانية"),
    "ratio" to listOf("9:16", "16:9", "1:1")
)

private val SlotQuestion = mapOf(
    "idea" to "احكِ لي الفكرة بكلماتك — عن ماذا الفيديو؟ وما الرسالة التي تريد أن تبقى في قلب المشاهد؟",
    "type" to "فهمت. من تخاطب بهذا؟ وما الشعور الذي تريد أن يخرج به المشاهد؟",
    "tone" to "جميل. أي لحظة هي ذروة التأثير؟ وكيف تريد أن تبدأ أول ثانيتين؟",
    "duration" to "بقي شيء واحد عملي: المدة التقريبية والنسبة (عمودي/أفقي)؟"
)

// ق70: حالة حوار المخرج مرفوعة إلى الوالد (UnifiedBriefScreen) —
// تبديل التبويب كان يُخرج DirectorChatScreen من التركيب فيسقط حتى
// rememberSaveable (ق53 أصلح التدوير فقط). الحامل يعيش في الوالد
// فينجو من تبديل التبويبات، ويُحفظ بـ Saver فينجو من التدوير أيضاً،
// ويموت مع مغادرة تدفق الموجز فيبدأ مشروع جديد بحوار نظيف.
internal class DirectorChatState(
    brief: DirectorBrief,
    messages: List<DirectorMsg>,
    input: String,
    treatment: String
) {
    val briefState = mutableStateOf(brief)
    val messagesState = mutableStateOf(messages)
    val inputState = mutableStateOf(input)
    val treatmentState = mutableStateOf(treatment)
    /**
     * ق180: علامة «دور ذكاء جارٍ» مرفوعة مع الحالة لا في الشاشة الابنة —
     * الإرسال صار يُطلق من نطاق الوالد (ينجو من تبديل التبويب) فلو
     * بقيت العلامة remember في الابنة لانطفأت عند التبديل وقبلت
     * الشاشة إرسالاً ثانياً متزامناً فوق الدور الجاري فيُسقط أحد
     * الردين عند الكتابة المتزامنة على القائمة. لا تُحفظ في Saver
     * (عابرة؛ والمستعادة تبدأ بلا دور جارٍ لأن الدور مات معها أصلاً).
     */
    val thinkingState = mutableStateOf(false)

    /** بلا أي رسالة مستخدم بعد — آمن لبذر فكرة جديدة. */
    fun isFresh(): Boolean = messagesState.value.none { !it.fromDirector }

    fun seedIdea(text: String) {
        briefState.value = briefState.value.copy(idea = text)
    }
}

private val directorChatStateSaver = Saver<DirectorChatState, List<String>>(
    save = { s ->
        listOf(
            s.briefState.value.idea,
            s.briefState.value.contentType,
            s.briefState.value.contentTone,
            s.briefState.value.videoDuration,
            s.briefState.value.selectedRatio,
            s.inputState.value,
            s.treatmentState.value
        ) + s.messagesState.value.map { (if (it.fromDirector) "D" else "U") + "\u0001" + it.text }
    },
    restore = { list ->
        DirectorChatState(
            brief = DirectorBrief(
                idea = list.getOrElse(0) { "" },
                contentType = list.getOrElse(1) { "" },
                contentTone = list.getOrElse(2) { "" },
                videoDuration = list.getOrElse(3) { "" },
                selectedRatio = list.getOrElse(4) { "9:16" }
            ),
            messages = list.drop(7).mapNotNull { raw ->
                val sep = raw.indexOf('\u0001')
                if (sep < 0) null else DirectorMsg(raw[0] == 'D', raw.substring(sep + 1))
            }.ifEmpty {
                listOf(DirectorMsg(true, "أهلاً بك في غرفة المخرج. احكِ لي فكرتك وسأجهز كل شيء."))
            },
            input = list.getOrElse(5) { "" },
            treatment = list.getOrElse(6) { "" }
        )
    }
)

@Composable
internal fun rememberDirectorChatState(initialText: String): DirectorChatState =
    rememberSaveable(saver = directorChatStateSaver) {
        DirectorChatState(
            brief = DirectorBrief(idea = initialText),
            messages = listOf(
                DirectorMsg(
                    true,
                    if (initialText.isBlank()) "أهلاً بك في غرفة المخرج. احكِ لي فكرتك وسأجهز كل شيء."
                    else "ممتاز، استلمت هذه البداية. أخبرني المزيد أو أجب عن أسئلتي لأكمل الموجز."
                )
            ),
            input = "",
            treatment = ""
        )
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectorChatScreen(
    state: DirectorChatState,
    onComplete: (DirectorBrief) -> Unit,
    onBack: () -> Unit,
    /**
     * ق180: نطاق الوالد (UnifiedBriefScreen) لا نطاق الابنة — إطلاق
     * دور الذكاء من rememberCoroutineScope() الخاص بالابنة كان
     * يُلغى عند تبديل التبويب (الابنة تخرج من التركيب) فتبقى رسالة
     * المستخدم بلا رد وبلا خطأ، رغم أن الحالة نفسها تنجو (ق70).
     * نطاق الوالد يبقى مركّباً عند التبديل فيكمل الدور ويُلحق الرد،
     * ويموت مع مغادرة تدفق الموجز فيُلغى الدور كما يجب. التدوير
     * ما زال يلغي الدور (الوالد نفسه يُعاد إنشاؤه) كما في ق53.
     */
    sendScope: kotlinx.coroutines.CoroutineScope
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // ق70: الحالة من الحامل المرفوع للوالد (تنجو من تبديل التبويب
    // والتدوير معاً)؛ وthinking مرفوعة في الحامل نفسه (ق180)؛ و
    // buildingTreatment عابرة فتبقى remember — تنطفئ عند العودة
    // من تبديل التبويب بلا ضرر لأن العلاج نفسه يُحفظ في الحالة
    // المرفوعة فيظهر عند العودة، والكوروتين صار في نطاق الوالد
    // فيكمل بناءه حتى والتبويب مبدَّل.
    var brief by state.briefState
    var messages by state.messagesState
    var input by state.inputState
    var treatment by state.treatmentState
    var thinking by state.thinkingState
    var buildingTreatment by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { AppServices.getAnalyticsService(context).logScreenView("DirectorChat") }

    fun currentChips(): List<String> {
        val missing = brief.missingSlots().firstOrNull() ?: return emptyList()
        return SlotOptions[missing] ?: emptyList()
    }

    fun localExtract(text: String, b: DirectorBrief): DirectorBrief {
        var out = b
        // دمج الفكرة: تراكم بدل استبدال — أول رسالة طويلة تؤسس، واللاحقة تُلحق إن أضافت معنى
        if (text.length > 5) {
            out = if (out.idea.isBlank()) out.copy(idea = text.take(600))
            else if (!out.idea.contains(text.take(20))) out.copy(idea = (out.idea + " " + text).take(600))
            else out
        }
        val t = text
        if (out.contentTone.isBlank()) {
            listOf("خاشع", "حماسي", "ملحمي", "هادئ", "حزين", "مؤثر").firstOrNull { t.contains(it) }
                ?.let { out = out.copy(contentTone = it) }
        }
        if (out.contentType.isBlank()) {
            when {
                t.contains("قرآن") || t.contains("آية") || t.contains("تدبر") -> out = out.copy(contentType = "تدبر قرآني")
                t.contains("حديث") -> out = out.copy(contentType = "حديث شريف")
                t.contains("قصة") || t.contains("سيرة") || t.contains("نبي") -> out = out.copy(contentType = "قصة")
                t.contains("موعظة") || t.contains("خطبة") -> out = out.copy(contentType = "موعظة")
            }
        }
        if (out.videoDuration.isBlank()) {
            when {
                t.contains("15") -> out = out.copy(videoDuration = "15 ثانية")
                t.contains("30") -> out = out.copy(videoDuration = "30 ثانية")
                t.contains("60") || t.contains("دقيقة") -> out = out.copy(videoDuration = "60 ثانية")
            }
        }
        if (t.contains("16:9") || t.contains("يوتيوب") || t.contains("عرضي")) out = out.copy(selectedRatio = "16:9")
        else if (t.contains("1:1") || t.contains("مربع")) out = out.copy(selectedRatio = "1:1")
        return out
    }

    suspend fun aiTurn(userText: String, b: DirectorBrief, history: String): Pair<DirectorBrief, String> = withContext(Dispatchers.IO) {
        val extractPrompt = """
            أنت مخرج سينمائي محترف يحاور صانع محتوى عربي ليفهم فكرته بعمق — لست نموذج تعبئة خانات.
            سجل الحوار حتى الآن:
            $history
            رسالة المستخدم الأخيرة: "$userText".
            الموجز الحالي: فكرة="${b.idea.take(300)}" نوع="${b.contentType}" نبرة="${b.contentTone}" مدة="${b.videoDuration}" نسبة="${b.selectedRatio}".
            مهمتك:
            1. افهم الفكرة الحقيقية من كل الحوار (ادمج رسائل المستخدم في فكرة واحدة غنية 2-3 جمل، لا تنسخ رسالة واحدة فقط).
            2. استنتج النوع والنبرة من السياق إن أمكن بدل سؤالهما حرفياً (مثلاً حديث عن الصبر → موعظة/خاشع).
            3. اسأل سؤالاً واحداً فقط، ذكياً ومحدداً، يعمّق الفهم (الجمهور؟ الشعور المستهدف؟ البداية الخطافة؟ النهاية والدعوة؟) — ممنوع سؤال عام مثل "ما نوع المحتوى؟".
            4. إن كانت الفكرة واضحة (تعرف الرسالة + الجمهور أو الشعور) اكتب reply تأكيداً يعيد صياغة فهمك بجملة ثم يقول إن الموجز جاهز.
            أخرج JSON فقط: {"idea":"الفكرة المدمجة الغنية","contentType":"...","contentTone":"...","videoDuration":"...","selectedRatio":"...","reply":"..."}.
            القيم الفارغة null. النسبة الافتراضية 9:16. reply بالعربية الفصيحة المبسطة، جملتان كحد أقصى.
        """.trimIndent()
        val raw = try { RealGroqService.chatOrGenerate(extractPrompt) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
            ?: try { geminiChat(context, extractPrompt) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
        if (raw.isNullOrBlank() || !raw.contains("{")) return@withContext b to ""
        try {
            val json = JSONObject(raw.substring(raw.indexOf("{"), raw.lastIndexOf("}") + 1))
            fun s(k: String) = json.optString(k, "").takeIf { it.isNotBlank() && it != "null" } ?: ""
            val nb = b.copy(
                idea = s("idea").ifBlank { b.idea },
                contentType = s("contentType").ifBlank { b.contentType },
                contentTone = s("contentTone").ifBlank { b.contentTone },
                videoDuration = s("videoDuration").ifBlank { b.videoDuration },
                selectedRatio = s("selectedRatio").ifBlank { b.selectedRatio }
            )
            nb to json.optString("reply", "").take(200)
        } catch (_: Exception) { b to "" }
    }

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isBlank() || thinking) return
        input = ""
        messages = messages + DirectorMsg(false, clean)
        thinking = true
        // ق180: يُطلق في نطاق الوالد فينجو من تبديل التبويب؛ وfinally
        // تُطفئ thinking حتى عند الإلغاء (مغادرة التدفق) فلا تبقى
        // العلامة المرفوعة عالقة تمنع كل إرسال لاحق.
        sendScope.launch {
            try {
            var nb = localExtract(clean, brief)
            var reply = ""
            // ق53: الرسالة أُضيفت إلى messages قبل إطلاق الكوروتين، فلا
            // تُلحق مرة ثانية هنا — كانت تظهر مكررة في السجل المرسل للنموذج.
            val history = messages.takeLast(8).joinToString("\n") {
                (if (it.fromDirector) "المخرج: " else "المستخدم: ") + it.text
            }
            val ai = aiTurn(clean, nb, history)
            if (ai.first != nb) nb = ai.first
            reply = ai.second
            if (reply.isBlank()) {
                // أسئلة عميقة متدرجة بلا شبكة: رسالة/جمهور/شعور/بداية — لا تسأل "ما النوع؟" حرفياً
                val turns = messages.count { !it.fromDirector }
                reply = when {
                    nb.idea.length < 15 -> SlotQuestion["idea"] ?: "حدثني أكثر."
                    nb.contentType.isBlank() && turns >= 2 -> "لمن توجّه هذا؟ وما الشعور الذي تريد أن يبقى معه بعد المشاهدة؟"
                    nb.contentTone.isBlank() && turns >= 3 -> "كيف تتخيل أول ثانيتين تخطفان العين؟ وماذا تقول الجملة الأخيرة؟"
                    nb.videoDuration.isBlank() && turns >= 4 -> SlotQuestion["duration"] ?: "حدثني أكثر."
                    nb.missingSlots().isEmpty() -> "فهمتك: «${nb.idea.take(90)}». الموجز اكتمل — راجعه بالأعلى واضغط «اعتمد وابدأ»."
                    else -> "حدثني أكثر عن زاويتك الخاصة — ما الذي يجعل فكرتك مختلفة؟"
                }
            }
            brief = nb
            messages = messages + DirectorMsg(true, reply)
            thinking = false
            // ق180: التمرير فقط إن كانت القائمة مركّبة — بعد تبديل
            // التبويب تكون listState منفصلة (totalItemsCount == 0)
            // وanimateScrollToItem يعلّق الكوروتين إلى الأبد منتظراً
            // تخطيطاً لن يأتي، فيموت بناء العلاج الحي بعده.
            if (listState.layoutInfo.totalItemsCount > 0) {
                try { listState.animateScrollToItem(messages.size) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {}
            }
            // معاينة حية: ابنِ العلاج السينمائي أمام عين المستخدم بعد كل دور
            if (nb.idea.length >= 10) {
                buildingTreatment = true
                val tPrompt = """
                    أنت مخرج خادم المسلم. من هذا الحوار: $history
                    الفكرة: "${nb.idea.take(400)}" النوع:${nb.contentType} النبرة:${nb.contentTone}
                    اكتب علاجاً سينمائياً حياً بالعربية، 6 أسطر كحد أقصى، بالشكل:
                    الخطاف: جملة أول ثانيتين.
                    المشهد 1: صوت + صورة بالإنجليزية قصيرة.
                    المشهد 2: صوت + صورة.
                    المشهد 3: صوت + صورة.
                    الخاتمة: الدعوة.
                    بلا مقدمات.
                """.trimIndent()
                val t = try { RealGroqService.chatOrGenerate(tPrompt) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
                    ?: try { geminiChat(context, tPrompt) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
                if (!t.isNullOrBlank()) treatment = t.take(900)
                else if (treatment.isBlank()) treatment = "الخطاف: «${nb.idea.take(50)}…»\nالمشهد 1: صوت يطرح السؤال + صورة سماء واسعة\nالمشهد 2: صوت يعمّق المعنى + صورة نور يخترق غيوماً\nالمشهد 3: صوت الذروة + صورة مسجد وقت الغروب\nالخاتمة: دعوة للمشاركة والنشر"
                buildingTreatment = false
            }
            } finally {
                thinking = false
                buildingTreatment = false
            }
        }
    }

    Scaffold(
        containerColor = DeepSlate,
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("غرفة المخرج", color = PresetPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        com.qabas.app.ui.components.ModelIndicator()
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardSurface)
            )
        },
        bottomBar = {
            Column(Modifier.background(CardSurface).padding(12.dp)) {
                val chips = currentChips()
                if (chips.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        items(chips) { c ->
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF8B5CF6).copy(alpha = 0.15f))
                                    .border(1.dp, Color(0xFF8B5CF6), RoundedCornerShape(16.dp))
                                    .clickable { send(c) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(c, color = Color(0xFFC4B5FD), fontFamily = CairoFont, fontSize = 13.sp)
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("احكِ للمخرج...", fontFamily = CairoFont, color = TextSecondary) },
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PresetPrimary, unfocusedBorderColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = PresetPrimary
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { send(input) },
                        modifier = Modifier.size(48.dp).background(
                            Brush.horizontalGradient(listOf(PresetAccent, PresetPrimary)), CircleShape
                        )
                    ) {
                        if (thinking) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = DeepSlate, strokeWidth = 2.dp)
                        else Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال", tint = DeepSlate)
                    }
                }
                Spacer(Modifier.height(8.dp))
                ProdPrimaryButton(
                    text = if (brief.idea.isBlank()) "اكتب الفكرة أولاً" else "اعتمد وابدأ",
                    enabled = brief.idea.isNotBlank(),
                    onClick = {
                        if (brief.idea.isNotBlank()) {
                            onComplete(brief.copy(videoDuration = brief.videoDuration.ifBlank { "30 ثانية" }))
                        }
                    }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.35f))
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("موجز المشروع — يُبنى حياً", color = PresetPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        BriefRow("الفكرة", brief.idea.take(80).ifBlank { "—" })
                        BriefRow("النوع", brief.contentType.ifBlank { "—" })
                        BriefRow("النبرة", brief.contentTone.ifBlank { "—" })
                        BriefRow("المدة", brief.videoDuration.ifBlank { "—" })
                        if (buildingTreatment) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = PresetPrimary)
                                Spacer(Modifier.width(6.dp))
                                Text("المخرج يبني المشاهد حياً…", color = TextSecondary, fontFamily = CairoFont, fontSize = 11.sp)
                            }
                        } else if (treatment.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.fillMaxWidth().background(Color(0xFF0B0F19), RoundedCornerShape(10.dp)).padding(10.dp)) {
                                Text(treatment, color = Color(0xFFE2E8F0), fontFamily = CairoFont, fontSize = 12.sp, lineHeight = 18.sp)
                            }
                        }
                    }
                }
            }
            items(messages) { m ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (m.fromDirector) Arrangement.Start else Arrangement.End
                ) {
                    if (m.fromDirector) {
                        Box(
                            modifier = Modifier.size(28.dp).background(Color(0xFF8B5CF6).copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFFC4B5FD), modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Box(
                        modifier = Modifier.widthIn(max = 280.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (m.fromDirector) Color(0xFF151B2B) else PresetPrimary.copy(alpha = 0.9f))
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Text(
                            m.text, color = if (m.fromDirector) Color.White else DeepSlate,
                            fontFamily = CairoFont, fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

private suspend fun geminiChat(context: android.content.Context, prompt: String): String? =
    withContext(Dispatchers.IO) {
        // 1. Proactively try official Firebase AI Logic (Gemini 3.8 Flash via App Check)
        try {
            val fbText = FirebaseAiService.generateText(prompt)
            if (fbText.isNotBlank()) {
                // ق44: تسجيل الاستهلاك — كان هذا المسار يمر بلا أثر في
                // تتبع التكلفة (رموز تقديرية فتبقى «غير مقاسة»).
                VibeCostTracker.recordEstimated(context, "gemini", "gemini-3.8-flash", prompt, fbText, "دردشة المخرج")
                return@withContext fbText
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { }

        val key = KeyVault.gemini
        if (key.isBlank()) return@withContext null
        try {
            val body = JSONObject().apply {
                put("contents", org.json.JSONArray().put(JSONObject().apply {
                    put("parts", org.json.JSONArray().put(JSONObject().apply { put("text", prompt) }))
                }))
            }
            val req = okhttp3.Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent")
                .header("x-goog-api-key", key)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val resp = ApiUsageTracker.track(context, "Gemini") { SharedHttpClient.instance.newCall(req).execute() }
            if (!resp.isSuccessful) return@withContext null
            val json = JSONObject(resp.body?.string() ?: "")
            val text = json.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
                ?.optJSONObject(0)?.optString("text")
            if (!text.isNullOrBlank()) {
                // ق44: تسجيل الاستهلاك — بتفصيل الرموز الحقيقي عند
                // إرساله، وإلا تقديرياً («غير مقاسة»).
                val usage = json.optJSONObject("usageMetadata")
                val promptTokens = usage?.optInt("promptTokenCount", 0) ?: 0
                val completionTokens = usage?.optInt("candidatesTokenCount", 0) ?: 0
                if (promptTokens + completionTokens > 0) {
                    val total = usage?.optInt("totalTokenCount", 0)?.takeIf { it > 0 }
                        ?: (promptTokens + completionTokens)
                    VibeCostTracker.record(
                        context, "gemini", "gemini-2.0-flash",
                        RealAgentResponse(text, total, promptTokens, completionTokens),
                        "دردشة المخرج"
                    )
                } else {
                    VibeCostTracker.recordEstimated(context, "gemini", "gemini-2.0-flash", prompt, text, "دردشة المخرج")
                }
            }
            text
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
    }

@Composable
private fun BriefRow(label: String, value: String) {    Row {
        Text("$label: ", color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp)
        Text(value, color = Color.White, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
