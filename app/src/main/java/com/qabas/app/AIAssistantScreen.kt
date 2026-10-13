package com.qabas.app

/**
 * AIAssistantScreen.kt
 * المساعد الدعوي والإنتاجي الذكي بتقنية (Cybernetic Glassmorphism + Linear Minimalist - Spatial HUD Tech)
 * يدعم 4 أوضاع تخصصية (مخرج سكريبتات، باحث شرعي، خبير فيرال، توجيه صوتي)
 * مع أدوات OpenRouter الحقيقية، سياق ذوق صانع المحتوى (Taste Profile)،
 * وشبكة هولوغرافية تفاعلية مع إمكانية تحويل أي سكريبت فوراً إلى مشروع ومونتاج سينمائي.
 */

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch

data class AssistantModePreset(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val systemInstruction: String,
    val initialMessage: String
)

data class AssistantSuggestionCategory(
    val id: String,
    val name: String,
    val icon: String,
    val suggestions: List<String>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIAssistantScreen(
    onBack: () -> Unit,
    onCreateProjectFromScript: (String) -> Unit = {}
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val primaryColor = qabasPrimary()
    val accentColor = qabasAccent()

    var query by remember { mutableStateOf("") }
    var showSystemInstructionDialog by remember { mutableStateOf(false) }

    val presets = remember {
        listOf(
            AssistantModePreset(
                id = "director",
                title = "🎬 مخرج سكريبتات",
                icon = Icons.Default.MovieFilter,
                systemInstruction = "أنت مخرج سكريبتات قبس السينمائي. ركز على تقسيم السكريبت لمشاهد مع الوصف البصري (B-Roll) والخطاف البصري (Hook) في أول 3 ثوانٍ والمؤثرات الصوتية.",
                initialMessage = "أهلاً بك في استوديو قبس الفضائي! أنا مخرج السكريبتات الذكي.\n• صياغة سكريبت سينمائي 9:16 مقسم لمشاهد.\n• تحديد B-Roll ومؤثرات الصوت بدقة.\n• كتابة خطاف (Hook) فيروسي جاذب للمنصات."
            ),
            AssistantModePreset(
                id = "scholar",
                title = "📖 باحث شرعي",
                icon = Icons.Default.AutoStories,
                systemInstruction = "أنت باحث شرعي معتمد في استوديو قبس. مهمتك التحقق من صحة الأحاديث وعزوها لمصادرها المعتمدة (البخاري، مسلم، السنن) وصياغة الفوائد بأسلوب دعوي رصين ومؤثر.",
                initialMessage = "السلام عليكم ورحمة الله. أنا الباحث الشرعي الذكي.\n• توثيق وتخريج الأحاديث النبوية الشريفة.\n• صياغة الفوائد الإيمانية بأسلوب معاصر رصين.\n• استخراج آيات قرآنية مناسبة لموضوع الفيديو."
            ),
            AssistantModePreset(
                id = "viral",
                title = "🚀 خبير فيرال وSEO",
                icon = Icons.Default.TrendingUp,
                systemInstruction = "أنت خبير في الانتشار الفيروسي (Viral Growth) وخوارزميات تيك توك وريلز ويوتيوب شورتس. ركز على خطافات التوقف عن التمرير، الهاشتاجات الموجهة، والعناوين عالية النقرات (CTR).",
                initialMessage = "جاهز لمضاعفة مشاهداتك! أنا خبير الانتشار الفيروسي.\n• هندسة خطافات بصرية ونصية تمنع التمرير (Thumb-stopping).\n• توليد عناوين جذابة عالية النقرات (CTR).\n• حزم هاشتاجات ذكية متوافقة مع الخوارزمية."
            ),
            AssistantModePreset(
                id = "voice",
                title = "🎙️ توجيه صوتي ونبرة",
                icon = Icons.Default.Mic,
                systemInstruction = "أنت مدرب التعليق الصوتي لاستوديو قبس. وجّه صانع المحتوى حول نبرة الإلقاء، الوقفات التأثيرية، وتوزيع الطاقة الصوتية المناسبة لنوع المقطع.",
                initialMessage = "مرحباً بك! أنا مستشار التوجيه الصوتي والهندسة اللفظية.\n• ضبط النبرة المناسبة (خشوع، هيبة، حماس، تحفيز).\n• توضيح علامات الوقف الإجباري ومواضع التنفس.\n• اقتراح نغمات ومؤثرات صوتية محيطية ملائمة."
            )
        )
    }

    val suggestionCategories = remember {
        listOf(
            AssistantSuggestionCategory(
                id = "ideas",
                name = "أفكار 💡",
                icon = "💡",
                suggestions = listOf(
                    "فكرة ريلز دعوي مؤثر عن قيام الليل وعظمة السحر",
                    "3 خطافات فيروسية عن فضل بر الوالدين وأثره",
                    "فكرة فيديو قصير عن الاستغفار وتفريج الكروب",
                    "سلسلة قصص التابعين وأثرها في دقيقة واحدة"
                )
            ),
            AssistantSuggestionCategory(
                id = "correction",
                name = "تصحيح ✍️",
                icon = "✍️",
                suggestions = listOf(
                    "راجع هذا النص لغوياً وأضف التشكيل الدقيق للآيات",
                    "دقق صحة الحديث الشريف وتخريجه المعتمد",
                    "اختصر هذا النص ليكون ريلز مركزاً مدته 30 ثانية",
                    "حسّن أسلوب النص ليكون بنبرة وثائقية مهيبة ومؤثرة"
                )
            ),
            AssistantSuggestionCategory(
                id = "script",
                name = "سكريبت 🎬",
                icon = "🎬",
                suggestions = listOf(
                    "صيغ سكريبت ريلز 45 ثانية مع B-Roll عن الصبر",
                    "سكريبت مشهد سينمائي عن قصة موسى والخضر مع العبر",
                    "اكتب سكريبت شورتس سريع مع خطاف في أول 3 ثوان",
                    "سكريبت دعوي بنبرة خاشعة مع فواصل هادئة"
                )
            ),
            AssistantSuggestionCategory(
                id = "sharia",
                name = "دليل شرعي 📖",
                icon = "📖",
                suggestions = listOf(
                    "ابحث عن أحاديث صحيحة في فضل الصدقة مع التخريج",
                    "آيات قرآنية عن طمأنينة القلب والسكينة بالتشكيل",
                    "شرح موثق ومختصر لحديث (إنما الأعمال بالنيات)",
                    "أقوال أئمة السلف في الإخلاص وحقيقة التوكل"
                )
            )
        )
    }

    var selectedSuggestionCategory by remember { mutableStateOf(suggestionCategories[0].id) }
    var selectedPresetIndex by remember { mutableIntStateOf(0) }
    val currentPreset = presets[selectedPresetIndex]
    val prefs = remember { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }

    var selectedOrModel by remember { mutableStateOf<String?>(null) }
    val hasOrKey = remember {
        val k = prefs.getString("openrouter_key", "") ?: ""
        k.isNotBlank()
    }
    var toolsMode by remember(hasOrKey) { mutableStateOf(hasOrKey) }
    val orModelLabels = remember {
        mapOf(
            "poolside/laguna-s-2.1:free" to "🦙 Laguna Code",
            "google/gemma-4-31b-it:free" to "💎 Gemma 4",
            "cohere/north-mini-code:free" to "🌊 North Code"
        )
    }
    var customInstructionText by remember { mutableStateOf("") }

    val messages = remember {
        mutableStateListOf(
            Pair(true, currentPreset.initialMessage)
        )
    }

    var isTyping by remember { mutableStateOf(false) }
    var lastFailedPrompt by remember { mutableStateOf<String?>(null) }

    var hasGeminiKey by remember {
        val key = prefs.getString("gemini_key", "") ?: ""
        mutableStateOf(key.isNotBlank() && key != "YOUR_GEMINI_API_KEY")
    }

    // انيميشن النبض السيبراني وشعاع المسح الفضائي
    val infiniteTransition = rememberInfiniteTransition(label = "hud_cyber_matrix")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val scanlineY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanline_y"
    )

    // مسجل الصوت السريع
    val recorderHelper = remember { AudioRecorderHelper(context) }
    var isRecordingAudio by remember { mutableStateOf(false) }
    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val ok = recorderHelper.startRecording()
            if (ok) {
                isRecordingAudio = true
                Toast.makeText(context, "بدأ الالتقاط الصوتي... تحدث بفكرتك 🎙️", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "يلزم إذن الميكروفون للالتقاط الصوتي", Toast.LENGTH_SHORT).show()
        }
    }
    DisposableEffect(Unit) {
        onDispose { recorderHelper.release() }
    }

    fun sendMessage(textToSend: String) {
        if (textToSend.isBlank() || isTyping) return
        messages.add(Pair(false, textToSend))
        query = ""
        isTyping = true
        lastFailedPrompt = null

        coroutineScope.launch {
            listState.animateScrollToItem(0)
        }

        coroutineScope.launch {
            try {
                val tasteContext = TasteManager.getTasteContext(context)
                val fullSystemInstruction = buildString {
                    append(currentPreset.systemInstruction)
                    append("\n\n[إرشادات عامة للمحتوى الإسلامي]:")
                    append("\n- استشهد بالآيات القرآنية بالرسم والتشكيل السليم.")
                    append("\n- وثق الأحاديث النبوية بذكر المخرج (البخاري، مسلم، إلخ) وتجنب الأحاديث الضعيفة أو المنكرة.")
                    append("\n- عند كتابة سكريبت، قسّمه إلى مشاهد 9:16 مع B-Roll وخطاف جذاب في أول 3 ثوان.")
                    if (tasteContext.isNotBlank()) {
                        append("\n\n[سياق أسلوب وهوية صانع المحتوى (Taste Profile)]:\n")
                        append(tasteContext)
                    }
                    if (customInstructionText.isNotBlank()) {
                        append("\n\n[تعليمات إضافية خاصة بالمستخدم]:\n")
                        append(customInstructionText)
                    }
                }

                val contextHistory = messages.takeLast(10).map { Pair(it.first, it.second) }
                val orKey = prefs.getString("openrouter_key", "") ?: ""
                val response = if (toolsMode && orKey.isNotBlank()) {
                    val (text, used) = OpenRouterService.chatWithToolsPooled(
                        fullSystemInstruction, contextHistory,
                        AiTools.definitions(context), selectedOrModel
                    )
                    val finalText = text.ifBlank { "تعذر التنفيذ بالأدوات — أُعيد التوجيه للمسار العادي." }
                    if (text.isBlank()) {
                        AppServices.chatWithAssistant(contextHistory, fullSystemInstruction, selectedOrModel)
                    } else if (used.isNotEmpty()) {
                        "🛠️ نُفذت الأدوات: ${used.joinToString("، ")}\n\n$finalText"
                    } else finalText
                } else {
                    AppServices.chatWithAssistant(contextHistory, fullSystemInstruction, selectedOrModel)
                }
                isTyping = false
                messages.add(Pair(true, response))
            } catch (e: Exception) {
                isTyping = false
                lastFailedPrompt = textToSend
                val errorMsg = "⚠️ حدث خطأ أثناء الاتصال بالذكاء الاصطناعي: ${e.localizedMessage ?: "يرجى التحقق من الاتصال بالإنترنت ومفاتيح الـ API في الإعدادات."}"
                messages.add(Pair(true, errorMsg))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030712))
    ) {
        // ── شبكة القيادة الفضائية الكوانتية (Spatial HUD Grid Canvas) ──
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 34.dp.toPx()
            val gridColor = primaryColor.copy(alpha = 0.04f)
            val accentGridColor = accentColor.copy(alpha = 0.025f)

            for (x in 0..(size.width / step).toInt()) {
                val c = if (x % 4 == 0) accentGridColor else gridColor
                drawLine(
                    color = c,
                    start = Offset(x * step, 0f),
                    end = Offset(x * step, size.height),
                    strokeWidth = if (x % 4 == 0) 1.5f else 0.8f
                )
            }
            for (y in 0..(size.height / step).toInt()) {
                val c = if (y % 4 == 0) accentGridColor else gridColor
                drawLine(
                    color = c,
                    start = Offset(0f, y * step),
                    end = Offset(size.width, y * step),
                    strokeWidth = if (y % 4 == 0) 1.5f else 0.8f
                )
            }

            // شعاع المسح الليزري السيبراني الأفقي
            val currentScanY = size.height * scanlineY
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        primaryColor.copy(alpha = 0.18f),
                        accentColor.copy(alpha = 0.45f),
                        primaryColor.copy(alpha = 0.18f),
                        Color.Transparent
                    )
                ),
                start = Offset(0f, currentScanY),
                end = Offset(size.width, currentScanY),
                strokeWidth = 2.5f
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                // شريط علوي سيبراني زجاجي بهندسة فضائية متقاطعة
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF090D1A).copy(alpha = 0.95f),
                                    Color(0xFF030712).copy(alpha = 0.85f)
                                )
                            )
                        )
                        .drawBehind {
                            drawLine(
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        primaryColor.copy(alpha = 0.75f),
                                        accentColor.copy(alpha = 0.9f),
                                        Color.Transparent
                                    )
                                ),
                                start = Offset(0f, size.height),
                                end = Offset(size.width, size.height),
                                strokeWidth = 1.5.dp.toPx()
                            )
                        }
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CutCornerShape(8.dp))
                                    .background(primaryColor.copy(alpha = 0.12f))
                                    .border(1.dp, primaryColor.copy(alpha = 0.45f), CutCornerShape(8.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "العودة",
                                    tint = primaryColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(primaryColor.copy(alpha = pulseAlpha))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "المساعد الدعوي والإنتاجي الذكي",
                                        color = Color.White,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                                Text(
                                    text = "AI NEURAL DIRECTIVE HUD // GEMINI & OPENROUTER",
                                    color = primaryColor,
                                    fontFamily = RobotoMonoFont,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.6.sp
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // زر تعديل تعليمات النظام (System Instruction)
                            IconButton(
                                onClick = { showSystemInstructionDialog = true },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CutCornerShape(6.dp))
                                    .background(Color(0xFF111827))
                                    .border(0.8.dp, primaryColor.copy(alpha = 0.4f), CutCornerShape(6.dp))
                            ) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = "تخصيص",
                                    tint = primaryColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // زر مسح المحادثة
                            IconButton(
                                onClick = {
                                    messages.clear()
                                    messages.add(Pair(true, currentPreset.initialMessage))
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CutCornerShape(6.dp))
                                    .background(Color(0xFF111827))
                                    .border(0.8.dp, Color(0xFFEF4444).copy(alpha = 0.35f), CutCornerShape(6.dp))
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "مسح المحادثة",
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                // ── لوحة معلومات الاتصال والتيليمتري (Connection Telemetry Badge) ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassmorphicSurface(
                        shape = CutCornerShape(6.dp),
                        backgroundColor = if (hasGeminiKey) Color(0xFF064E3B).copy(alpha = 0.35f) else Color(0xFF78350F).copy(alpha = 0.35f),
                        borderColor = if (hasGeminiKey) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFFF59E0B).copy(alpha = 0.5f),
                        borderWidth = 0.8.dp,
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (hasGeminiKey) Color(0xFF10B981) else Color(0xFFF59E0B))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasGeminiKey) "محرك Gemini 2.5 متصل بالرادار ✦" else "وضع الاستجابة السريعة (مفتاح الإعدادات)",
                                color = if (hasGeminiKey) Color(0xFF6EE7B7) else Color(0xFFFCD34D),
                                fontSize = 10.5.sp,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        color = Color(0xFF111827).copy(alpha = 0.7f),
                        shape = CutCornerShape(6.dp),
                        border = BorderStroke(0.6.dp, primaryColor.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "${messages.size} رسائل // سجل نشط",
                            color = primaryColor.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            fontFamily = RobotoMonoFont,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // ── 1. محدد الأنماط الفضائية (Spatial Mode Selector Chips) ──
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    items(
                        count = presets.size,
                        key = { index -> presets[index].id }
                    ) { index ->
                        val preset = presets[index]
                        val isSelected = index == selectedPresetIndex

                        GlassmorphicSurface(
                            shape = CutCornerShape(8.dp),
                            backgroundColor = if (isSelected) primaryColor.copy(alpha = 0.22f) else Color(0xFF0F172A).copy(alpha = 0.65f),
                            borderColor = if (isSelected) primaryColor else Color(0xFF1E293B),
                            borderWidth = if (isSelected) 1.2.dp else 0.7.dp,
                            modifier = Modifier.clickable {
                                if (selectedPresetIndex != index) {
                                    selectedPresetIndex = index
                                    messages.clear()
                                    messages.add(Pair(true, preset.initialMessage))
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    preset.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) primaryColor else TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    preset.title,
                                    fontFamily = CairoFont,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else TextSecondary
                                )
                            }
                        }
                    }
                }

                // ── 1.5 شريط OpenRouter والأدوات الحقيقية ──
                if (hasOrKey) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        item {
                            GlassmorphicSurface(
                                shape = CutCornerShape(6.dp),
                                backgroundColor = if (toolsMode) primaryColor.copy(alpha = 0.2f) else Color(0xFF0B1120),
                                borderColor = if (toolsMode) primaryColor else Color(0xFF1E293B),
                                borderWidth = 0.8.dp,
                                modifier = Modifier.clickable { toolsMode = !toolsMode }
                            ) {
                                Text(
                                    "🛠️ أدوات حقيقية ${if (toolsMode) "مفعلة ✓" else ""}",
                                    fontFamily = CairoFont,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (toolsMode) primaryColor else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                        item {
                            GlassmorphicSurface(
                                shape = CutCornerShape(6.dp),
                                backgroundColor = if (selectedOrModel == null) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF0B1120),
                                borderColor = if (selectedOrModel == null) Color(0xFF10B981) else Color(0xFF1E293B),
                                borderWidth = 0.8.dp,
                                modifier = Modifier.clickable { selectedOrModel = null }
                            ) {
                                Text(
                                    "⚡ نموذج تلقائي",
                                    fontFamily = CairoFont,
                                    fontSize = 11.sp,
                                    color = if (selectedOrModel == null) Color(0xFF6EE7B7) else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                        items(
                            count = orModelLabels.size,
                            key = { i -> orModelLabels.keys.elementAt(i) }
                        ) { i ->
                            val modelId = orModelLabels.keys.elementAt(i)
                            val isChosen = selectedOrModel == modelId
                            GlassmorphicSurface(
                                shape = CutCornerShape(6.dp),
                                backgroundColor = if (isChosen) Color(0xFF38BDF8).copy(alpha = 0.2f) else Color(0xFF0B1120),
                                borderColor = if (isChosen) Color(0xFF38BDF8) else Color(0xFF1E293B),
                                borderWidth = 0.8.dp,
                                modifier = Modifier.clickable {
                                    selectedOrModel = if (isChosen) null else modelId
                                }
                            ) {
                                Text(
                                    orModelLabels[modelId] ?: modelId,
                                    fontFamily = CairoFont,
                                    fontSize = 11.sp,
                                    color = if (isChosen) Color(0xFF7DD3FC) else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                // ── 2. منطقة المحادثة ورسائل الذكاء الاصطناعي (Spatial Chat Surface) ──
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (isTyping) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                GlassmorphicSurface(
                                    shape = CutCornerShape(12.dp),
                                    backgroundColor = Color(0xFF0A0F1D).copy(alpha = 0.85f),
                                    borderColor = primaryColor.copy(alpha = 0.6f),
                                    borderWidth = 1.dp,
                                    modifier = Modifier.fillMaxWidth(0.92f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            QabasGlowingSpinner(size = 20.dp, strokeWidth = 2.dp, color = primaryColor)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                "جاري التحليل والمعالجة العصبية الكوانتية... ✦",
                                                color = primaryColor,
                                                fontFamily = CairoFont,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        QabasIndeterminateProgressBar(height = 3.dp)
                                    }
                                }
                            }
                        }
                    }

                    items(messages.size) { i ->
                        val msgIndex = messages.size - 1 - i
                        val msg = messages[msgIndex]
                        val isAi = msg.first

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isAi) Arrangement.Start else Arrangement.End
                        ) {
                            if (isAi) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CutCornerShape(8.dp))
                                        .background(primaryColor.copy(alpha = 0.18f))
                                        .border(1.dp, primaryColor.copy(alpha = 0.5f), CutCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = primaryColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Column(
                                horizontalAlignment = if (isAi) Alignment.Start else Alignment.End,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth(if (isAi) 0.94f else 0.86f)
                                ) {
                                    GlassmorphicSurface(
                                        shape = CutCornerShape(
                                            topStart = 12.dp,
                                            topEnd = 12.dp,
                                            bottomStart = if (isAi) 2.dp else 12.dp,
                                            bottomEnd = if (isAi) 12.dp else 2.dp
                                        ),
                                        backgroundColor = if (isAi) Color(0xFF090E1C).copy(alpha = 0.92f) else primaryColor.copy(alpha = 0.16f),
                                        borderColor = if (isAi) primaryColor.copy(alpha = 0.35f) else primaryColor.copy(alpha = 0.7f),
                                        borderWidth = 1.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(modifier = Modifier.padding(14.dp)) {
                                            if (isAi) {
                                                HudCornerDecor(
                                                    modifier = Modifier.matchParentSize(),
                                                    color = primaryColor.copy(alpha = 0.12f)
                                                )
                                            }
                                            Text(
                                                text = msg.second,
                                                color = if (isAi) Color(0xFFF8FAFC) else primaryColor,
                                                fontFamily = CairoFont,
                                                fontSize = 13.5.sp,
                                                lineHeight = 22.sp
                                            )
                                        }
                                    }
                                }

                                // ── شريط الإجراءات التكتيكي للرسالة ──
                                if (isAi) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (msg.second.startsWith("⚠️") && lastFailedPrompt != null) {
                                            Surface(
                                                onClick = { lastFailedPrompt?.let { sendMessage(it) } },
                                                color = Color(0xFFEF4444).copy(alpha = 0.2f),
                                                shape = CutCornerShape(6.dp),
                                                border = BorderStroke(0.6.dp, Color(0xFFEF4444))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.Refresh, contentDescription = "إعادة", tint = Color(0xFFFCA5A5), modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("إعادة المحاولة 🔄", color = Color(0xFFFCA5A5), fontSize = 11.sp, fontFamily = CairoFont)
                                                }
                                            }
                                        }

                                        // نسخ النص كاملاً
                                        Surface(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                val clip = ClipData.newPlainText("Qabas Script", msg.second)
                                                clipboard?.setPrimaryClip(clip)
                                                Toast.makeText(context, "تم نسخ النص كاملاً إلى الحافظة 📋", Toast.LENGTH_SHORT).show()
                                            },
                                            color = Color(0xFF111827),
                                            shape = CutCornerShape(6.dp),
                                            border = BorderStroke(0.6.dp, Color(0xFF1E293B))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = primaryColor, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("نسخ 📋", color = Color.White, fontSize = 11.sp, fontFamily = CairoFont)
                                            }
                                        }

                                        // نسخ الشاهد القرآني أو الحديث
                                        val containsQuranOrHadith = msg.second.contains("﴿") || msg.second.contains("قال رسول الله") || msg.second.contains("حديث")
                                        if (containsQuranOrHadith) {
                                            Surface(
                                                onClick = {
                                                    val text = msg.second
                                                    val quote = if (text.contains("﴿") && text.contains("﴾")) {
                                                        val start = text.indexOf("﴿")
                                                        val end = text.indexOf("﴾", start) + 1
                                                        if (end > start) text.substring(start, end) else text
                                                    } else {
                                                        text
                                                    }
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                    val clip = ClipData.newPlainText("Qabas Quote", quote)
                                                    clipboard?.setPrimaryClip(clip)
                                                    Toast.makeText(context, "تم نسخ الشاهد بدقة 📖✨", Toast.LENGTH_SHORT).show()
                                                },
                                                color = Color(0xFF064E3B).copy(alpha = 0.35f),
                                                shape = CutCornerShape(6.dp),
                                                border = BorderStroke(0.6.dp, Color(0xFF10B981))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.AutoStories, contentDescription = "الشاهد", tint = Color(0xFF34D399), modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("نسخ الشاهد 📖", color = Color(0xFF6EE7B7), fontSize = 11.sp, fontFamily = CairoFont)
                                                }
                                            }
                                        }

                                        // تحويل لمشروع ومونتاج فوري
                                        Surface(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                val clip = ClipData.newPlainText("Qabas Script", msg.second)
                                                clipboard?.setPrimaryClip(clip)

                                                onCreateProjectFromScript(msg.second)
                                                Toast.makeText(context, "جاري تحويل السكريبت إلى استوديو المونتاج 🎬✨", Toast.LENGTH_LONG).show()
                                            },
                                            color = primaryColor.copy(alpha = 0.22f),
                                            shape = CutCornerShape(6.dp),
                                            border = BorderStroke(0.8.dp, primaryColor)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Movie, contentDescription = "Video", tint = primaryColor, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("إنشاء فيديو 🎬", color = primaryColor, fontSize = 11.sp, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        // تعديل في حقل الإدخال
                                        Surface(
                                            onClick = {
                                                query = "طور هذا النص: ${msg.second.take(80)}..."
                                            },
                                            color = Color(0xFF111827),
                                            shape = CutCornerShape(6.dp),
                                            border = BorderStroke(0.6.dp, Color(0xFF1E293B))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = TextSecondary, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("تعديل ✍️", color = TextSecondary, fontSize = 11.sp, fontFamily = CairoFont)
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        onClick = {
                                            onCreateProjectFromScript(msg.second)
                                            Toast.makeText(context, "جاري تحويل فكرتك إلى مشروع مونتاج جديد 🎬", Toast.LENGTH_SHORT).show()
                                        },
                                        color = Color(0xFF111827),
                                        shape = CutCornerShape(6.dp),
                                        border = BorderStroke(0.6.dp, primaryColor.copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = primaryColor, modifier = Modifier.size(11.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("بدء مشروع بهذه الفكرة", color = primaryColor, fontSize = 10.5.sp, fontFamily = CairoFont)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── 3. مصفوفة الاقتراحات السريعة المصنفة (HUD Suggestions Matrix) ──
                GlassmorphicSurface(
                    shape = CutCornerShape(10.dp),
                    backgroundColor = Color(0xFF090E1A).copy(alpha = 0.88f),
                    borderColor = Color(0xFF1E293B),
                    borderWidth = 0.8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        // تبويبات التصنيفات
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(suggestionCategories) { cat ->
                                val isCatSelected = selectedSuggestionCategory == cat.id
                                Surface(
                                    onClick = { selectedSuggestionCategory = cat.id },
                                    color = if (isCatSelected) primaryColor else Color(0xFF111827),
                                    shape = CutCornerShape(6.dp),
                                    border = BorderStroke(0.8.dp, if (isCatSelected) primaryColor else Color(0xFF1E293B))
                                ) {
                                    Text(
                                        text = cat.name,
                                        color = if (isCatSelected) Color(0xFF030712) else TextPrimary,
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp,
                                        fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // أزرار الاقتراحات في التصنيف النشط
                        val activeCategory = suggestionCategories.find { it.id == selectedSuggestionCategory } ?: suggestionCategories[0]
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(activeCategory.suggestions) { suggestionText ->
                                Surface(
                                    onClick = { sendMessage(suggestionText) },
                                    color = Color(0xFF0D1424),
                                    shape = CutCornerShape(6.dp),
                                    border = BorderStroke(0.6.dp, primaryColor.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                    ) {
                                        Icon(Icons.Default.NorthWest, contentDescription = null, tint = primaryColor, modifier = Modifier.size(11.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = suggestionText,
                                            color = Color(0xFFF1F5F9),
                                            fontFamily = CairoFont,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── 4. كونسول الإدخال التكتيكي (Tactical HUD Input Console) ──
                GlassmorphicSurface(
                    shape = CutCornerShape(12.dp),
                    backgroundColor = Color(0xFF070B14).copy(alpha = 0.95f),
                    borderColor = primaryColor.copy(alpha = 0.45f),
                    borderWidth = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // زر الالتقاط الصوتي
                        IconButton(
                            onClick = {
                                if (isRecordingAudio) {
                                    isRecordingAudio = false
                                    val file = recorderHelper.stopRecording()
                                    if (file != null && file.exists()) {
                                        Toast.makeText(context, "تم حفظ التسجيل! جاري بدء المشروع الصوتي...", Toast.LENGTH_SHORT).show()
                                        onCreateProjectFromScript("تسجيل صوتي من المساعد")
                                    }
                                } else {
                                    micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CutCornerShape(8.dp))
                                .background(if (isRecordingAudio) Color(0xFFEF4444).copy(alpha = 0.25f) else Color(0xFF111827))
                                .border(
                                    1.dp,
                                    if (isRecordingAudio) Color(0xFFEF4444) else primaryColor.copy(alpha = 0.3f),
                                    CutCornerShape(8.dp)
                                )
                        ) {
                            Icon(
                                if (isRecordingAudio) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "تسجيل صوتي",
                                tint = if (isRecordingAudio) Color(0xFFEF4444) else primaryColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    "اطلب سكريبت، تدقيق حديث، أو خطافات ريلز...",
                                    color = Color.Gray,
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            maxLines = 4
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        val isSendEnabled = query.isNotBlank() && !isTyping

                        IconButton(
                            onClick = { sendMessage(query) },
                            enabled = isSendEnabled,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CutCornerShape(8.dp))
                                .background(
                                    if (isSendEnabled) {
                                        Brush.linearGradient(
                                            listOf(primaryColor, accentColor)
                                        )
                                    } else {
                                        Brush.linearGradient(
                                            listOf(Color(0xFF1F2937), Color(0xFF111827))
                                        )
                                    }
                                )
                                .border(
                                    1.dp,
                                    if (isSendEnabled) primaryColor else Color(0xFF374151),
                                    CutCornerShape(8.dp)
                                )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "إرسال",
                                tint = if (isSendEnabled) Color(0xFF030712) else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // ── حوار تخصيص تعليمات النظام (System Instruction HUD Dialog) ──
            if (showSystemInstructionDialog) {
                AlertDialog(
                    onDismissRequest = { showSystemInstructionDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = primaryColor)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "تخصيص تعليمات النظام (System Directive)",
                                color = primaryColor,
                                fontFamily = CairoFont,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    text = {
                        Column {
                            Text(
                                "تتيح لك تعليمات النظام توجيه الذكاء الاصطناعي (Gemini / OpenRouter) بالأسلوب والدور المطلوب لمخرجات استوديو قبس:",
                                color = TextSecondary,
                                fontFamily = CairoFont,
                                fontSize = 11.5.sp,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Text("التعليمات الافتراضية للنمط المختار:", color = primaryColor, fontFamily = CairoFont, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF080C16), CutCornerShape(6.dp))
                                    .border(0.8.dp, Color(0xFF1E293B), CutCornerShape(6.dp))
                                    .padding(10.dp)
                            ) {
                                Text(currentPreset.systemInstruction, color = TextSecondary, fontSize = 11.5.sp, fontFamily = CairoFont)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text("أضف تعليمات وتوجيهات خاصة بك (اختياري):", color = primaryColor, fontFamily = CairoFont, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = customInstructionText,
                                onValueChange = { customInstructionText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp),
                                placeholder = {
                                    Text(
                                        "مثال: اجعل الأسلوب وثائقياً موجزاً، أو ركز على سكريبتات الشورتس السريعة...",
                                        color = Color.DarkGray,
                                        fontSize = 11.5.sp,
                                        fontFamily = CairoFont
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = Color(0xFF1E293B),
                                    focusedBorderColor = primaryColor,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0xFF080C16),
                                    unfocusedContainerColor = Color(0xFF080C16)
                                ),
                                shape = CutCornerShape(8.dp)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showSystemInstructionDialog = false
                                Toast.makeText(context, "تم حفظ وتطبيق توجيهات النظام بنجاح ✦", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            shape = CutCornerShape(8.dp)
                        ) {
                            Text("حفظ وتطبيق", color = Color(0xFF030712), fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            customInstructionText = ""
                            showSystemInstructionDialog = false
                        }) {
                            Text("إعادة الضبط", color = Color.Gray, fontFamily = CairoFont)
                        }
                    },
                    containerColor = Color(0xFF0F172A),
                    shape = CutCornerShape(12.dp)
                )
            }
        }
    }
}
