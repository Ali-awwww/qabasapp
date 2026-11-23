package com.qabas.app

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleKeyRequiredBanner
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.components.AppleSecondaryButton
import com.qabas.app.ui.components.AppleSegmentedControl
import com.qabas.app.ui.components.AppleTopBar
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FlipMemoryCard(
    val id: String,
    val category: String,
    val title: String,
    val questionOrFrontText: String,
    val answerOrBackText: String,
    val sourceBook: String,
    val viralReelScriptIdea: String
)

data class PodcastSnippet(
    val id: String,
    val showName: String,
    val title: String,
    val speaker: String,
    val durationText: String,
    val keyTakeaways: List<String>,
    val audioDurationSeconds: Int = 180
)

data class KnowledgeBattleQuiz(
    val id: Int,
    val questionText: String,
    val options: List<String>,
    val correctAnswerIndex: Int,
    val explanationText: String,
    val dalilText: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeHubScreen(
    onBack: () -> Unit,
    onStartScriptWithText: (String) -> Unit,
    onOpenTeleprompterWithText: (String) -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val hasAiEngine = remember { ServiceAvailabilityRegistry.hasAnyAiAssistantEngine(context) }

    var selectedTab by remember { mutableIntStateOf(0) }

    val sharedPrefs = remember { context.getSharedPreferences("qabas_knowledge_prefs", Context.MODE_PRIVATE) }
    val todayKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val savedDate = remember { sharedPrefs.getString("last_task_date", "") ?: "" }
    val isSameDay = savedDate == todayKey

    var streakDays by remember { mutableIntStateOf(sharedPrefs.getInt("streak_days", 0)) }
    var taskReadToday by remember { mutableStateOf(isSameDay && sharedPrefs.getBoolean("task_read_today", false)) }
    var taskQuizToday by remember { mutableStateOf(isSameDay && sharedPrefs.getBoolean("task_quiz_today", false)) }
    var taskScriptCreatedToday by remember { mutableStateOf(isSameDay && sharedPrefs.getBoolean("task_script_today", false)) }

    // مفتاح أمس لحساب تتابع السلسلة؛ والعرض قبل أي إنجاز اليوم قد يعرض
    // سلسلة قديمة منقطعة، فيُصفَّر المخزن إن كان آخر يوم مسجل ليس اليوم ولا أمس.
    val yesterdayKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis() - 86400000L)) }
    LaunchedEffect(Unit) {
        val last = sharedPrefs.getString("last_streak_date", "") ?: ""
        if (streakDays > 0 && last != todayKey && last != yesterdayKey) {
            streakDays = 0
            sharedPrefs.edit().putInt("streak_days", 0).apply()
        }
    }

    fun markTaskDone(key: String) {
        val lastCountedDay = sharedPrefs.getString("last_streak_date", "") ?: ""
        val editor = sharedPrefs.edit()
            .putString("last_task_date", todayKey)
            .putBoolean(key, true)
        if (lastCountedDay != todayKey) {
            // السلسلة تُحسب بالأيام المتتالية فعلاً: فجوة يوم أو أكثر تُعيد
            // البدء من 1 بدل مواصلة العدّ القديم (كان يكبر بلا تصفير مهما
            // طال الغياب — نفس فئة خلل سلسلة الورد في الجولة 12).
            streakDays = if (lastCountedDay == yesterdayKey) streakDays + 1 else 1
            editor.putInt("streak_days", streakDays).putString("last_streak_date", todayKey)
        }
        editor.apply()
    }

    val completedTasksCount = (if (taskReadToday) 1 else 0) +
        (if (taskQuizToday) 1 else 0) +
        (if (taskScriptCreatedToday) 1 else 0)

    var isDailySparkSaved by remember { mutableStateOf(taskReadToday) }

    var selectedCategory by remember { mutableStateOf("الكل") }
    val categories = listOf("الكل", "غريب القرآن", "لطائف الأحاديث", "سير الصحابة", "أسرار اللغة", "قواعد الدعوة")

    val memoryCards = remember {
        listOf(
            FlipMemoryCard(
                id = "c1",
                category = "غريب القرآن",
                title = "سر قوله تعالى: {فَلَا اقْتَحَمَ الْعَقَبَةَ}",
                questionOrFrontText = "ما هي «الْعَقَبَةُ» التي أمر اللهُ الإنسانَ باقتحامها في سورة البلد؟ وما معنى هذا التعبير؟",
                answerOrBackText = "العقبة هي الطريق الشديد المتعرج في الجبل. والمراد بها مجاهدة النفس في بذل المال وإطعام الجائع وفك العاني، وسُميت عقبة لأن مجاهدة شح النفس وتجاوزه أشد على الإنسان من صعود الجبل!",
                sourceBook = "تفسير القرطبي وابن كثير",
                viralReelScriptIdea = "خطاف الفكرة: «تعرف الآية التي تحداك الله أن تقتحم فيها أصعب جبل في حياتك؟ تعال تعرف معناها السرّي!»"
            ),
            FlipMemoryCard(
                id = "c2",
                category = "لطائف الأحاديث",
                title = "الحَوَر بعد الكَوْر",
                questionOrFrontText = "كان النبي ﷺ يتعوذ في سفره من «الحَوْرِ بعد الكَوْرِ»، فماذا يعني هذا الدُّعاء العجيب؟",
                answerOrBackText = "الكَوْرُ هو لف العمامة بإتقان، والحَوْرُ هو نقضها وحلها بعد إحكامها. والمراد التعوذ من النقصان والانتكاس بعد الزيادة والاستقامة، والنكوص عن الحق بعد الفهم!",
                sourceBook = "جامع العلوم والحكم - ابن رجب",
                viralReelScriptIdea = "خطاف الفكرة: «دعاء نبوي شريف لو دعيت به يومياً حماك الله من الانتكاسة والنكوص بعد الالتزام!»"
            ),
            FlipMemoryCard(
                id = "c3",
                category = "سير الصحابة",
                title = "الصحابي الذي اهتز لعرش الرحمن",
                questionOrFrontText = "من هو الصحابي الجليل الذي اهتز لموته عرش الرحمن؟ وكم كانت مدة إسلامه؟",
                answerOrBackText = "هو سعد بن معاذ رضي الله عنه، سيد الأوس. أسلم وعمره 31 سنة ومات وعمره 37 سنة! ست سنوات فقط في الإسلام كانت كافية لكي يهتز لموته عرش الرحمن لجليل أعماله بنصرة الدين.",
                sourceBook = "سير أعلام النبلاء - الذهبي",
                viralReelScriptIdea = "خطاف الفكرة: «6 سنوات فقط كانت كافية ليهتز عرش الرحمن لموت هذا الرجل! ما سر الإنجاز؟»"
            ),
            FlipMemoryCard(
                id = "c4",
                category = "أسرار اللغة",
                title = "الفرق بين «الحِلم» و«الحُلْم» و«الحَلَم»",
                questionOrFrontText = "كيف يُغير تشكيل حرف واحد المعنى بالكامل في اللغة العربية؟",
                answerOrBackText = "1) الحِلْم (بكسر الحاء): ضبط النفس والتأنّي.\n2) الحُلْم (بضم الحاء): ما يراه النائم.\n3) الحَلَم (بفتح الحاء واللام): صغار القراد أو الثقوب. إعجاز وبلاغة لغوية فريدة!",
                sourceBook = "الفروق اللغوية - أبو هلال العسكري",
                viralReelScriptIdea = "خطاف الفكرة: «حركة واحدة فقط تشقلب معنى الكلمة من قمة الحكمة إلى نوم الليل!»"
            ),
            FlipMemoryCard(
                id = "c5",
                category = "قواعد الدعوة",
                title = "قاعدة «الرفق ما كان في شيء إلا زانه»",
                questionOrFrontText = "كيف تبني أسلوباً دعوياً جذّاباً يمنع تنفير الجمهور في السوشيال ميديا؟",
                answerOrBackText = "الخطاب الدعوي المحبوب يعتمد على التلطف والرحمة وتبيان البديل الإيجابي قبل تحريم الفاسد، واختيار الألفاظ التي تحبب العباد لربهم وتفتح أبواب التوبة.",
                sourceBook = "صيد الخاطر - ابن الجوزي",
                viralReelScriptIdea = "خطاف الفكرة: «3 أسرار تجعل مقاطعك الإسلامية محبوبة وتلمس القلوب بدون هجوم تنفيري!»"
            )
        )
    }

    val filteredCards = if (selectedCategory == "الكل") memoryCards else memoryCards.filter { it.category == selectedCategory }

    var selectedQuickTopic by remember { mutableStateOf("تفسير غريب آية قرآنية") }
    val quickTopics = listOf(
        "تفسير غريب آية قرآنية",
        "قصة صحابي غير مشهورة",
        "حديث شريف يغير نظرتك للحياة",
        "رد هادئ على شبهة منتشرة",
        "لطيفة لغوية وإعجاز بياني"
    )
    var customConverterTopic by remember { mutableStateOf("") }
    var selectedToneStyle by remember { mutableStateOf("وثائقي غامض ومكثف") }
    val toneStyles = listOf("وثائقي غامض ومكثف", "قصصي درامي مشوق", "سؤال وجواب مفاجئ")

    var isGeneratingScript by remember { mutableStateOf(false) }
    var generatedScriptOutput by remember { mutableStateOf("") }
    var scriptErrorMessage by remember { mutableStateOf<String?>(null) }

    val podcastSnippets = remember {
        listOf(
            PodcastSnippet(
                id = "pod_1",
                showName = "بودكاست وعي",
                title = "كيف تحمي قلبك من فتنة الشهوات العابرة؟",
                speaker = "د. أحمد عبد المنعم والمهندس أيمن عبد الرحيم",
                durationText = "خلاصة صوتية",
                keyTakeaways = listOf(
                    "العقل يتبع التغذية البصرية؛ ما تراه عيناك يطبعه قلبك.",
                    "الوقاية من الشبهات تبدأ ببناء حصيلة علمية رصينة.",
                    "صناعة المحتوى الهادف هي صدقة جارية تبقى أثرها."
                )
            ),
            PodcastSnippet(
                id = "pod_2",
                showName = "بودكاست ثمانية (فنجان)",
                title = "فلسفة التدبر وبناء الشخصية القرآنية",
                speaker = "الشيخ محمد الحسن الددو",
                durationText = "خلاصة صوتية",
                keyTakeaways = listOf(
                    "التدبر ليس قراءة سطور، بل تنزيل الآيات على واقعك اليومي.",
                    "اللغة العربية هي مفتاح فهم القرآن بدون وسائط.",
                    "الاستمرار ولو بقليل هو القوة الحقيقية لصانع المحتوى."
                )
            ),
            PodcastSnippet(
                id = "pod_3",
                showName = "بودكاست نقطة",
                title = "معالم النجاة في زمن التغيرات المتسارعة",
                speaker = "د. مطلق الجاسر",
                durationText = "خلاصة صوتية",
                keyTakeaways = listOf(
                    "الثبات يحتاج لصحبة صالحة وبيئة تعين على الحق.",
                    "الصمت عند عدم العلم نصف العقل والحكمة.",
                    "صانع المحتوى الذكي يستثمر في محتوى يدوم أثره."
                )
            )
        )
    }

    val quizQuestions = remember {
        listOf(
            KnowledgeBattleQuiz(
                id = 1,
                questionText = "ما هي الآية التي عُدّت أرجى آية في القرآن الكريم عند جمهور المفسرين؟",
                options = listOf(
                    "آية الكرسي في سورة البقرة",
                    "{قُلْ يَا عِبَادِيَ الَّذِينَ أَسْرَفُوا عَلَىٰ أَنفُسِهِمْ لَا تَقْنَطُوا مِن رَّحْمَةِ اللَّهِ}",
                    "{وَإِذَا سَأَلَكَ عِبَادِي عَنِّي فَإِنِّي قَرِيبٌ}",
                    "{فَإِنَّ مَعَ الْعُسْرِ يُسْرًا}"
                ),
                correctAnswerIndex = 1,
                explanationText = "لأنها تفتح باب الأمل والتوبة الكلية لجميع العباد مهما عظمت ذنوبهم.",
                dalilText = "سورة الزمر - الآية 53"
            ),
            KnowledgeBattleQuiz(
                id = 2,
                questionText = "ما اسم الصحابي الذي اهتز لوفاته عرش الرحمن سبحانه وتعالى؟",
                options = listOf(
                    "حمزة بن عبد المطلب رضي الله عنه",
                    "مصعب بن عمير رضي الله عنه",
                    "سعد بن معاذ رضي الله عنه",
                    "أبو بكر الصديق رضي الله عنه"
                ),
                correctAnswerIndex = 2,
                explanationText = "أسلم وعمره 31 سنة وتوفي وعمره 37 سنة، وجاهد في الله حق جهاده فنصر الدين.",
                dalilText = "صحيح البخاري ومسلم"
            ),
            KnowledgeBattleQuiz(
                id = 3,
                questionText = "ما معنى كلمة «العَقَبَة» في قوله تعالى: {فَلَا اقْتَحَمَ الْعَقَبَةَ}؟",
                options = listOf(
                    "عقوبة الذنب في الآخرة",
                    "الطريق الصعب المرتفع ومجاهدة الشح في بذل الخير",
                    "نهاية الأجل والوفاة",
                    "الحصن المنيع في الحرب"
                ),
                correctAnswerIndex = 1,
                explanationText = "سُميت عقبة لأن مجاهدة النفس في الإطعام وفك العاني أشد من صعود الجبال.",
                dalilText = "تفسير ابن كثير والقرطبي"
            )
        )
    }

    var currentQuizIdx by remember { mutableIntStateOf(0) }
    var selectedAnswerIdx by remember { mutableStateOf<Int?>(null) }
    var isAnswerSubmitted by remember { mutableStateOf(false) }
    var isQuizCompleted by remember { mutableStateOf(false) }

    var audienceTopicInput by remember { mutableStateOf("غزوة بدر والدروس الاستراتيجية") }
    var generatedPollQuestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isGeneratingPolls by remember { mutableStateOf(false) }
    var pollErrorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = theme.background,
        topBar = {
            Column(modifier = Modifier.background(theme.background)) {
                AppleTopBar(
                    title = "روضة طلب العلم والإنتاج",
                    subtitle = "تزود بالمعرفة وحوّلها إلى أثر يدوم",
                    onBack = onBack
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    AppleSegmentedControl(
                        items = listOf(
                            0 to "البطاقات",
                            1 to "المختبر",
                            2 to "البودكاست",
                            3 to "المسابقة"
                        ),
                        selectedItem = selectedTab,
                        onSelect = { selectedTab = it }
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AppleCard(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                cornerRadius = 16.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(theme.warning.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = theme.warning, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    "سلسلة الورد اليومي: $streakDays أيام",
                                    color = theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "إنجاز اليوم: $completedTasksCount من 3 مهام",
                                    color = theme.textSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Surface(
                            color = theme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                "+${completedTasksCount * PointsManager.DAILY_TASK_POINTS} XP",
                                color = theme.primary,
                                fontFamily = RobotoMonoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { (completedTasksCount / 3f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = theme.primary,
                        trackColor = theme.secondarySurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (selectedTab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 28.dp)
                    ) {
                        item {
                            DailySparkCard(
                                isSaved = isDailySparkSaved,
                                onSave = {
                                    isDailySparkSaved = !isDailySparkSaved
                                    if (!taskReadToday) {
                                        taskReadToday = true
                                        markTaskDone("task_read_today")
                                        PointsManager.awardCustomPoints(
                                            context,
                                            PointsManager.DAILY_TASK_POINTS,
                                            "مهمة يومية: قراءة فائدة اليوم"
                                        )
                                        Toast.makeText(context, "تم إنجاز قراءة فائدة اليوم! +10 XP", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onStartScript = {
                                    val script = """
                                        سيناريو فائدة اليوم:
                                        الخطاف: «تعرف الآية التي اختصرت أسرار الطمأنينة والنفسية السوية؟»
                                        
                                        الفائدة: قال تعالى: {أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ}. الذكر ليس مجرد كلمات باللسان، بل هو وصل القلب بمصدر النور والأمان.
                                        
                                        الرسالة: اجعل لسانك رطباً بذكر الله في كل أوقاتك.
                                    """.trimIndent()
                                    onStartScriptWithText(script)
                                }
                            )
                        }

                        item {
                            Column {
                                Text("بطاقات الذاكرة التفاعلية", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("اضغط على أي بطاقة لكشف الشرح وفكرة السيناريو", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(8.dp))

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(categories) { cat ->
                                        val isSelected = selectedCategory == cat
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedCategory = cat },
                                            label = { Text(cat, fontFamily = NotoSansFont, fontSize = 12.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = theme.primary,
                                                selectedLabelColor = Color.White,
                                                containerColor = theme.cardSurface,
                                                labelColor = theme.textPrimary
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        items(filteredCards) { memoryCard ->
                            InteractiveFlipMemoryCardItem(
                                memoryCard = memoryCard,
                                onStartScript = {
                                    val script = """
                                        سيناريو مشتق من: ${memoryCard.title}
                                        
                                        ${memoryCard.viralReelScriptIdea}
                                        
                                        التفسير والمعنى الشرعي:
                                        ${memoryCard.answerOrBackText}
                                        
                                        المصدر: ${memoryCard.sourceBook}
                                    """.trimIndent()
                                    onStartScriptWithText(script)
                                    if (!taskScriptCreatedToday) {
                                        taskScriptCreatedToday = true
                                        markTaskDone("task_script_today")
                                        PointsManager.awardCustomPoints(
                                            context,
                                            PointsManager.DAILY_TASK_POINTS,
                                            "مهمة يومية: إنشاء سيناريو"
                                        )
                                    }
                                },
                                onOpenTeleprompter = {
                                    onOpenTeleprompterWithText("${memoryCard.questionOrFrontText}\n\n${memoryCard.answerOrBackText}")
                                },
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString("${memoryCard.title}\n${memoryCard.answerOrBackText}\nالمصدر: ${memoryCard.sourceBook}"))
                                    Toast.makeText(context, "تم نسخ الفائدة للحافظة", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                1 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 28.dp)
                    ) {
                        if (!hasAiEngine) {
                            item {
                                AppleKeyRequiredBanner(
                                    serviceName = "محرك الذكاء الاصطناعي للمختبر",
                                    reason = "لم يتم إعداد مفتاح API سحابي أو نموذج محلي GGUF؛ سيعمل التوليد عبر المزود المتاح عند الاتصال، وإلا ستظهر رسالة خطأ واضحة دون اختلاق نتائج."
                                )
                            }
                        }

                        item {
                            AppleCard(cornerRadius = 18.dp) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(theme.primary.copy(alpha = 0.14f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Science, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text("مختبر تحويل العلوم إلى ريلز", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Text("اختر الموضوع والأسلوب لتوليد سيناريو احترافي منظم.", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp, lineHeight = 17.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Text("1. اختر أو اكتب الموضوع:", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.height(6.dp))

                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(quickTopics) { qTopic ->
                                            val isSelected = selectedQuickTopic == qTopic
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { selectedQuickTopic = qTopic },
                                                label = { Text(qTopic, fontFamily = NotoSansFont, fontSize = 12.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = theme.primary,
                                                    selectedLabelColor = Color.White,
                                                    containerColor = theme.secondarySurface,
                                                    labelColor = theme.textPrimary
                                                )
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    OutlinedTextField(
                                        value = customConverterTopic,
                                        onValueChange = { customConverterTopic = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("أو اكتب موضوعاً مخصصاً (مثال: أسرار التوكل)...", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = theme.primary,
                                            unfocusedBorderColor = theme.separator.copy(alpha = 0.6f),
                                            focusedTextColor = theme.textPrimary,
                                            unfocusedTextColor = theme.textPrimary,
                                            focusedContainerColor = theme.secondarySurface,
                                            unfocusedContainerColor = theme.secondarySurface
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Text("2. النبرة والأسلوب الإخراجي:", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.height(6.dp))

                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(toneStyles) { tStyle ->
                                            val isSelected = selectedToneStyle == tStyle
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { selectedToneStyle = tStyle },
                                                label = { Text(tStyle, fontFamily = NotoSansFont, fontSize = 12.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = theme.primary,
                                                    selectedLabelColor = Color.White,
                                                    containerColor = theme.secondarySurface,
                                                    labelColor = theme.textPrimary
                                                )
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    ApplePrimaryButton(
                                        text = if (isGeneratingScript) "جاري توليد السيناريو..." else "تحويل إلى سيناريو ريلز",
                                        icon = Icons.Default.AutoAwesome,
                                        isLoading = isGeneratingScript,
                                        enabled = !isGeneratingScript,
                                        onClick = {
                                            val topicToUse = customConverterTopic.ifBlank { selectedQuickTopic }
                                            isGeneratingScript = true
                                            scriptErrorMessage = null
                                            scope.launch {
                                                try {
                                                    val prompt = """
                                                        أنت خبير إخراج وصناعة محتوى إسلامي.
                                                        قم بتحويل الموضوع التالي إلى سيناريو فيديو قصير دعوي (45 ثانية): "$topicToUse".
                                                        أسلوب الإخراج: "$selectedToneStyle".
                                                        صغ النتائج بالشكل التالي:
                                                        1) الخطاف الجاذب (0-3 ثوان).
                                                        2) الشرح والمفهوم العلمي الميسر.
                                                        3) المحور البصري المقترح للمونتاج.
                                                        4) التوثيق الشرعي والمصدر الصحيح.
                                                    """.trimIndent()
                                                    val result = AppServices.chatWithAssistant(listOf(Pair(true, prompt)))
                                                    if (result.isBlank()) {
                                                        scriptErrorMessage = "لم يرجع محرك الذكاء الاصطناعي نتيجة صالحة. تحقق من الاتصال ومفاتيح API."
                                                    } else {
                                                        generatedScriptOutput = result
                                                        if (!taskScriptCreatedToday) {
                                                            taskScriptCreatedToday = true
                                                            markTaskDone("task_script_today")
                                                            PointsManager.awardCustomPoints(
                                                                context,
                                                                PointsManager.DAILY_TASK_POINTS,
                                                                "مهمة يومية: إنشاء سيناريو"
                                                            )
                                                        }
                                                    }
                                                } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                                                    scriptErrorMessage = "تعذر توليد السيناريو: ${e.message ?: "تحقق من اتصال الإنترنت أو مفاتيح AI"}"
                                                } finally {
                                                    isGeneratingScript = false
                                                }
                                            }
                                        }
                                    )

                                    if (!scriptErrorMessage.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = scriptErrorMessage!!,
                                            color = theme.destructive,
                                            fontFamily = NotoSansFont,
                                            fontSize = 12.sp
                                        )
                                    }

                                    if (generatedScriptOutput.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Surface(
                                            color = theme.secondarySurface,
                                            shape = RoundedCornerShape(14.dp),
                                            border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.5f))
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Text(generatedScriptOutput, color = theme.textPrimary, fontFamily = NotoSansFont, fontSize = 13.5.sp, lineHeight = 21.sp)
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Box(modifier = Modifier.weight(1f)) {
                                                        AppleSecondaryButton(
                                                            text = "تسجيل بالمُلقن",
                                                            icon = Icons.Default.Mic,
                                                            onClick = { onOpenTeleprompterWithText(generatedScriptOutput) }
                                                        )
                                                    }
                                                    Box(modifier = Modifier.weight(1f)) {
                                                        ApplePrimaryButton(
                                                            text = "بدء المونتاج",
                                                            icon = Icons.Default.Movie,
                                                            onClick = { onStartScriptWithText(generatedScriptOutput) }
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

                2 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 28.dp)
                    ) {
                        item {
                            Column {
                                Text("عصارة البودكاست والاستماع الصوتي", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("استمع لقراءة الفوائد عبر ناطق الجهاز أو استخرج سيناريو جاهزاً.", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                            }
                        }

                        items(podcastSnippets, key = { it.id }) { snippet ->
                            PodcastSoundbiteItem(
                                snippet = snippet,
                                onExtractScript = {
                                    val script = """
                                        سيناريو مستخرج من ${snippet.showName} - ${snippet.title}:
                                        
                                        الخطاف: «فائدة مركزة من حلقة ${snippet.title}»
                                        
                                        الفوائد الرئيسية:
                                        • ${snippet.keyTakeaways.joinToString("\n• ")}
                                        
                                        المتحدث: ${snippet.speaker}
                                    """.trimIndent()
                                    onStartScriptWithText(script)
                                }
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 28.dp)
                    ) {
                        item {
                            AppleCard(cornerRadius = 18.dp) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Psychology, contentDescription = null, tint = theme.primary, modifier = Modifier.size(22.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("تحدي الفرسان العلمي", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        }
                                        Text("سؤال ${currentQuizIdx + 1} من ${quizQuestions.size}", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    if (!isQuizCompleted) {
                                        val question = quizQuestions[currentQuizIdx]
                                        Text(question.questionText, color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.5.sp, lineHeight = 23.sp)
                                        Spacer(modifier = Modifier.height(12.dp))

                                        question.options.forEachIndexed { optIndex, optionText ->
                                            val isSelected = selectedAnswerIdx == optIndex
                                            val isCorrect = optIndex == question.correctAnswerIndex

                                            val optionColor = when {
                                                isAnswerSubmitted && isCorrect -> theme.success.copy(alpha = 0.16f)
                                                isAnswerSubmitted && isSelected && !isCorrect -> theme.destructive.copy(alpha = 0.16f)
                                                isSelected -> theme.primary.copy(alpha = 0.14f)
                                                else -> theme.secondarySurface
                                            }
                                            val optionBorder = when {
                                                isAnswerSubmitted && isCorrect -> theme.success
                                                isAnswerSubmitted && isSelected && !isCorrect -> theme.destructive
                                                isSelected -> theme.primary
                                                else -> theme.separator.copy(alpha = 0.45f)
                                            }

                                            Surface(
                                                color = optionColor,
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, optionBorder),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                                    .defaultMinSize(minHeight = 48.dp)
                                                    .clickable(enabled = !isAnswerSubmitted) {
                                                        selectedAnswerIdx = optIndex
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    RadioButton(
                                                        selected = isSelected,
                                                        onClick = null,
                                                        colors = RadioButtonDefaults.colors(selectedColor = theme.primary)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(optionText, color = theme.textPrimary, fontFamily = NotoSansFont, fontSize = 13.5.sp)
                                                }
                                            }
                                        }

                                        if (isAnswerSubmitted) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Surface(
                                                color = theme.secondarySurface,
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Text("الشرح والتوثيق الشرعي:", color = theme.primary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(question.explanationText, color = theme.textPrimary, fontFamily = NotoSansFont, fontSize = 12.5.sp, lineHeight = 19.sp)
                                                    Text("المصدر: ${question.dalilText}", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        if (!isAnswerSubmitted) {
                                            ApplePrimaryButton(
                                                text = "تأكيد الإجابة",
                                                onClick = {
                                                    if (selectedAnswerIdx != null) {
                                                        isAnswerSubmitted = true
                                                    } else {
                                                        Toast.makeText(context, "يرجى اختيار إجابة أولاً", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            )
                                        } else {
                                            ApplePrimaryButton(
                                                text = if (currentQuizIdx < quizQuestions.size - 1) "السؤال التالي" else "عرض النتيجة",
                                                onClick = {
                                                    if (currentQuizIdx < quizQuestions.size - 1) {
                                                        currentQuizIdx++
                                                        selectedAnswerIdx = null
                                                        isAnswerSubmitted = false
                                                    } else {
                                                        isQuizCompleted = true
                                                        if (!taskQuizToday) {
                                                            taskQuizToday = true
                                                            markTaskDone("task_quiz_today")
                                                            PointsManager.awardCustomPoints(
                                                                context,
                                                                PointsManager.DAILY_TASK_POINTS,
                                                                "مهمة يومية: تحدي الفرسان العلمي"
                                                            )
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    } else {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp)
                                        ) {
                                            Text("أحسنت! أتممت تحدي الفرسان العلمي", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("حصلت على +${PointsManager.DAILY_TASK_POINTS} XP", color = theme.textPrimary, fontFamily = NotoSansFont, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.height(14.dp))
                                            ApplePrimaryButton(
                                                text = "إعادة التحدي",
                                                icon = Icons.Default.Refresh,
                                                onClick = {
                                                    currentQuizIdx = 0
                                                    selectedAnswerIdx = null
                                                    isAnswerSubmitted = false
                                                    isQuizCompleted = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            AppleCard(cornerRadius = 18.dp) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Poll, contentDescription = null, tint = theme.primary, modifier = Modifier.size(22.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("مولد مسابقات الستوري (Story Polls)", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("أدخل موضوعاً لتوليد أسئلة تفاعلية جاهزة للنسخ والنشر.", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp, lineHeight = 17.sp)
                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedTextField(
                                        value = audienceTopicInput,
                                        onValueChange = { audienceTopicInput = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("الموضوع (مثال: قصص الأنبياء، الصدق)...", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = theme.primary,
                                            unfocusedBorderColor = theme.separator.copy(alpha = 0.6f),
                                            focusedTextColor = theme.textPrimary,
                                            unfocusedTextColor = theme.textPrimary,
                                            focusedContainerColor = theme.secondarySurface,
                                            unfocusedContainerColor = theme.secondarySurface
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    ApplePrimaryButton(
                                        text = if (isGeneratingPolls) "جاري توليد المسابقات..." else "توليد مسابقات الستوري",
                                        icon = Icons.Default.AutoAwesome,
                                        isLoading = isGeneratingPolls,
                                        enabled = !isGeneratingPolls,
                                        onClick = {
                                            if (audienceTopicInput.isBlank()) return@ApplePrimaryButton
                                            isGeneratingPolls = true
                                            pollErrorMessage = null
                                            scope.launch {
                                                try {
                                                    val prompt = "ولد 3 أسئلة مسابقات قصيرة جداً ومثيرة للجمهور لتفاعلات ستوري انستغرام وتيك توك حول موضوع: '$audienceTopicInput' مع خيارين نعم/لا أو خيارات قصيرة."
                                                    val result = AppServices.chatWithAssistant(listOf(Pair(true, prompt)))
                                                    val lines = result.lines().filter { it.isNotBlank() }
                                                    if (lines.isEmpty()) {
                                                        pollErrorMessage = "لم تتوفر استجابة من محرك الذكاء الاصطناعي. تحقق من الاتصال ومفاتيح API."
                                                    } else {
                                                        generatedPollQuestions = lines
                                                    }
                                                } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                                                    pollErrorMessage = "تعذر توليد الأسئلة: ${e.message ?: "تحقق من اتصال الإنترنت"}"
                                                } finally {
                                                    isGeneratingPolls = false
                                                }
                                            }
                                        }
                                    )

                                    if (!pollErrorMessage.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(pollErrorMessage!!, color = theme.destructive, fontFamily = NotoSansFont, fontSize = 12.sp)
                                    }

                                    if (generatedPollQuestions.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            generatedPollQuestions.forEach { pollText ->
                                                Surface(
                                                    color = theme.secondarySurface,
                                                    shape = RoundedCornerShape(10.dp),
                                                    border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.5f))
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(pollText, color = theme.textPrimary, fontFamily = NotoSansFont, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                                                        IconButton(
                                                            onClick = {
                                                                clipboardManager.setText(AnnotatedString(pollText))
                                                                Toast.makeText(context, "تم نسخ السؤال", Toast.LENGTH_SHORT).show()
                                                            },
                                                            modifier = Modifier.size(48.dp)
                                                        ) {
                                                            Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = theme.primary, modifier = Modifier.size(18.dp))
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
        }
    }
}

@Composable
fun DailySparkCard(
    isSaved: Boolean,
    onSave: () -> Unit,
    onStartScript: () -> Unit
) {
    val theme = QabasTheme.current
    AppleCard(cornerRadius = 18.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("فائدة اليوم", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = theme.success.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "جديد",
                            color = theme.success,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onSave,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "حفظ",
                        tint = theme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                "«سر قوله تعالى: {أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ} — الطمأنينة ليست في اعتزال الحياة بل في وصل القلب بالله وسط تزاحم المشاغل.»",
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.5.sp,
                lineHeight = 23.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            ApplePrimaryButton(
                text = "تحويل إلى سيناريو فيديو",
                icon = Icons.Default.Movie,
                onClick = onStartScript
            )
        }
    }
}

@Composable
fun InteractiveFlipMemoryCardItem(
    memoryCard: FlipMemoryCard,
    onStartScript: () -> Unit,
    onOpenTeleprompter: () -> Unit,
    onCopy: () -> Unit
) {
    val theme = QabasTheme.current
    var isFlipped by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "cardFlip"
    )

    AppleCard(
        cornerRadius = 16.dp,
        containerColor = if (isFlipped) theme.primary.copy(alpha = if (theme.isDark) 0.16f else 0.08f) else theme.cardSurface,
        modifier = Modifier
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            },
        onClick = { isFlipped = !isFlipped }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .graphicsLayer {
                    if (rotation > 90f) {
                        rotationY = 180f
                    }
                }
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = theme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            memoryCard.category,
                            color = theme.primary,
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onCopy,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = theme.textSecondary, modifier = Modifier.size(16.dp))
                        }
                        Icon(Icons.Default.Flip, contentDescription = "انقر للقلب", tint = theme.textSecondary, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (rotation <= 90f) {
                    Text(memoryCard.title, color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(memoryCard.questionOrFrontText, color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 13.sp, lineHeight = 19.sp)
                } else {
                    Text("المعنى والتفسير:", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(memoryCard.answerOrBackText, color = theme.textPrimary, fontFamily = NotoSansFont, fontSize = 13.sp, lineHeight = 19.sp)

                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = theme.secondarySurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            memoryCard.viralReelScriptIdea,
                            color = theme.primary,
                            fontFamily = NotoSansFont,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            AppleSecondaryButton(text = "المُلقن", onClick = onOpenTeleprompter)
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            ApplePrimaryButton(text = "مونتاج", onClick = onStartScript)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PodcastSoundbiteItem(
    snippet: PodcastSnippet,
    onExtractScript: () -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    var isPlaying by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    var speedText by remember { mutableStateOf("1.0x") }

    val speechRateFloat = when (speedText) {
        "1.25x" -> 1.25f
        "1.5x" -> 1.5f
        "2.0x" -> 1.85f
        else -> 1.0f
    }

    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(snippet.id) {
        var localTts: TextToSpeech? = null
        localTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val res = localTts?.setLanguage(Locale("ar"))
                ttsReady = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
                localTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isPlaying = true
                    }
                    override fun onDone(utteranceId: String?) {
                        isPlaying = false
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        isPlaying = false
                    }
                })
            }
        }
        ttsInstance = localTts
        onDispose {
            try {
                localTts?.stop()
                localTts?.shutdown()
            } catch (_: Exception) {
            }
            ttsInstance = null
        }
    }

    fun toggleRealTtsPlayback() {
        val tts = ttsInstance
        if (tts == null || !ttsReady) {
            Toast.makeText(context, "محرك النطق العربي غير متاح على هذا الجهاز", Toast.LENGTH_SHORT).show()
            return
        }
        if (isPlaying) {
            tts.stop()
            isPlaying = false
        } else {
            val spokenScript = buildString {
                append(snippet.title)
                append(". ")
                append("المتحدث: ")
                append(snippet.speaker)
                append(". ")
                snippet.keyTakeaways.forEach { append(it).append(". ") }
            }
            tts.setSpeechRate(speechRateFloat)
            tts.speak(spokenScript, TextToSpeech.QUEUE_FLUSH, null, "pod_${snippet.id}")
            isPlaying = true
        }
    }

    AppleCard(cornerRadius = 16.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(snippet.showName, color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                Text(snippet.durationText, color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(snippet.title, color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
            Text("الضيف: ${snippet.speaker}", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                color = theme.secondarySurface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(
                            onClick = { toggleRealTtsPlayback() },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(theme.primary)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "إيقاف القراءة" else "قراءة صوتية للخلاصة",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                if (isPlaying) "جاري قراءة الخلاصة الصوتية..." else "استمع لقراءة الخلاصة",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (ttsReady) "نطق عربي • السرعة $speedText" else "يتطلب محرك نطق عربي في الجهاز",
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            speedText = when (speedText) {
                                "1.0x" -> "1.25x"
                                "1.25x" -> "1.5x"
                                "1.5x" -> "2.0x"
                                else -> "1.0x"
                            }
                            if (isPlaying) {
                                ttsInstance?.setSpeechRate(
                                    when (speedText) {
                                        "1.25x" -> 1.25f
                                        "1.5x" -> 1.5f
                                        "2.0x" -> 1.85f
                                        else -> 1.0f
                                    }
                                )
                            }
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(speedText, color = theme.primary, fontFamily = RobotoMonoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("أهم الفوائد المستخلصة:", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))

            snippet.keyTakeaways.forEach { takeaway ->
                Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                    Text("• ", color = theme.primary, fontWeight = FontWeight.Bold)
                    Text(takeaway, color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.5.sp, lineHeight = 19.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            ApplePrimaryButton(
                text = "استخراج سيناريو فيديو",
                icon = Icons.Default.ContentCut,
                onClick = onExtractScript
            )
        }
    }
}
