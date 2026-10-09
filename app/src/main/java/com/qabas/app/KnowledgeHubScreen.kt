package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// --- Data Models for Interactive Knowledge Vault ---
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
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "🃏 بطاقات الذاكرة",
        "🧪 مختبر المحتوى",
        "🎙️ عصارة البودكاست",
        "🏆 تحدي الفرسان"
    )

    // --- Streak & Daily Progress State ---
    val sharedPrefs = remember { context.getSharedPreferences("qabas_knowledge_prefs", Context.MODE_PRIVATE) }
    var streakDays by remember { mutableIntStateOf(sharedPrefs.getInt("streak_days", 4)) }
    var taskReadToday by remember { mutableStateOf(sharedPrefs.getBoolean("task_read_today", false)) }
    var taskQuizToday by remember { mutableStateOf(sharedPrefs.getBoolean("task_quiz_today", false)) }
    var taskScriptCreatedToday by remember { mutableStateOf(sharedPrefs.getBoolean("task_script_today", false)) }

    val completedTasksCount = (if (taskReadToday) 1 else 0) + (if (taskQuizToday) 1 else 0) + (if (taskScriptCreatedToday) 1 else 0)

    // --- Daily Spark Quote Data ---
    var isDailySparkSaved by remember { mutableStateOf(false) }

    // --- Flip Cards Data ---
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
                viralReelScriptIdea = "🔥 خطاف الفكرة: «تعرف الآية التي تحداك الله أن تقتحم فيها أصعب جبل في حياتك؟ تعال تعرف معناها السرّي!»"
            ),
            FlipMemoryCard(
                id = "c2",
                category = "لطائف الأحاديث",
                title = "الحَوَر بعد الكَوْر",
                questionOrFrontText = "كان النبي ﷺ يتعوذ في سفره من «الحَوْرِ بعد الكَوْرِ»، فماذا يعني هذا الدُّعاء العجيب؟",
                answerOrBackText = "الكَوْرُ هو لف العمامة بإتقان، والحَوْرُ هو نقضها وحلها بعد إحكامها. والمراد التعوذ من النقصان والانتكاس بعد الزيادة والاستقامة، والنكوص عن الحق بعد الفهم!",
                sourceBook = "جامع العلوم والحكم - ابن رجب",
                viralReelScriptIdea = "🎬 خطاف الفكرة: «دعاء نبوي شريف لو دعيت به يومياً حماك الله من الانتكاسة والنكوص بعد الالتزام!»"
            ),
            FlipMemoryCard(
                id = "c3",
                category = "سير الصحابة",
                title = "الصحابي الذي اهتز لعرش الرحمن",
                questionOrFrontText = "من هو الصحابي الجليل الذي اهتز لموته عرش الرحمن؟ وكم كانت مدة إسلامه؟",
                answerOrBackText = "هو سعد بن معاذ رضي الله عنه، سيد الأوس. أسلم وعمره 31 سنة ومات وعمره 37 سنة! ست سنوات فقط في الإسلام كانت كافية لكي ياهتز لموته عرش الرحمن لجليل أعماله بنصرة الدين.",
                sourceBook = "سير أعلام النبلاء - الذهبي",
                viralReelScriptIdea = "⚡ خطاف الفكرة: «6 سنوات فقط كانت كافية ليهتز عرش الرحمن لموت هذا الرجل! ما سر الإنجاز؟»"
            ),
            FlipMemoryCard(
                id = "c4",
                category = "أسرار اللغة",
                title = "الفرق بين «الحِلم» و«الحُلْم» و«الحَلَم»",
                questionOrFrontText = "كيف يُغير تشكيل حرف واحد المعنى بالكامل في اللغة العربية؟",
                answerOrBackText = "1) الحِلْم (بكسر الحاء): ضبط النفس والتأنّي.\n2) الحُلْم (بضم الحاء): ما يراه النائم.\n3) الحَلَم (بفتح الحاء والحاء): صغار القراد أو الثقوب. إعجاز وبلاغة لغوية فريدة!",
                sourceBook = "الفروق اللغوية - أبو هلال العسكري",
                viralReelScriptIdea = "💡 خطاف الفكرة: «حركة واحدة فقط تشقلب معنى الكلمة من قمة الحكمة إلى نوم الليل!»"
            ),
            FlipMemoryCard(
                id = "c5",
                category = "قواعد الدعوة",
                title = "قاعدة «الرفق ما كان في شيء إلا زانه»",
                questionOrFrontText = "كيف تبني أسلوباً دعوياً جذّاباً يمنع تنفير الجمهور في السوشيال ميديا؟",
                answerOrBackText = "الخطاب الدعوي المحبوب يعتمد على التلطف والرحمة وتبيان البديل الإيجابي قبل تحريم الفاسد، واختيار الألفاظ التي تحبب العباد لربهم وتفتح أبواب التوبة.",
                sourceBook = "صيد الخاطر - ابن الجوزي",
                viralReelScriptIdea = "✨ خطاف الفكرة: «3 أسرار تجعل مقاطعك الإسلامية محبوبة وتلمس القلوب بدون هجوم تنفيري!»"
            )
        )
    }

    val filteredCards = if (selectedCategory == "الكل") memoryCards else memoryCards.filter { it.category == selectedCategory }

    // --- Knowledge-to-Reels Converter State ---
    var selectedQuickTopic by remember { mutableStateOf("تفسير غريب آية قرآنية") }
    val quickTopics = listOf(
        "تفسير غريب آية قرآنية",
        "قصة صحابي غير مشهورة ⚔️",
        "حديث شريف يغير نظرتك للحياة 💫",
        "رد هادئ على شبهة منتشرة 🛡️",
        "لطيفة لغوية وإعجاز بياني 📖"
    )

    var customConverterTopic by remember { mutableStateOf("") }
    var selectedToneStyle by remember { mutableStateOf("وثائقي غامض ومكثف 🎙️") }
    val toneStyles = listOf("وثائقي غامض ومكثف 🎙️", "قصصي درامي مشوق 🎭", "سؤال وجواب مفاجئ ❓")

    var isGeneratingScript by remember { mutableStateOf(false) }
    var generatedScriptOutput by remember { mutableStateOf("") }

    // --- Podcast Soundbites State ---
    val podcastSnippets = remember {
        listOf(
            PodcastSnippet(
                id = "pod_1",
                showName = "بودكاست وعي",
                title = "كيف تحمي قلبك من فتنة الشهوات العابرة؟",
                speaker = "د. أحمد عبد المنعم والمهندس أيمن عبد الرحيم",
                durationText = "3:15 دقيقة مقتطعة",
                keyTakeaways = listOf(
                    "العقل يتبع التغذية البصرية؛ ما تراه عيناك يطبعه قلبك.",
                    "الوقاية من الشبهات تبدأ ببناء حصيلة علمية رصينة.",
                    "صناعة المحتوى الهادف هي صدقة جارية تلاحقك في قبرك."
                )
            ),
            PodcastSnippet(
                id = "pod_2",
                showName = "بودكاست ثمانية (فنجان)",
                title = "فلسفة التدبر وبناء الشخصية القرأنية",
                speaker = "الشيخ محمد الحسن الددو",
                durationText = "4:40 دقيقة مقتطعة",
                keyTakeaways = listOf(
                    "التدبر ليس قراءة سطور، بل تنزيل الآيات على واقعكDaily.",
                    "اللغة العربية هي مفتاح فهم القرآن بدون وسائط.",
                    "الاستمرار ولو بقليل هو القوة الحقيقية للصانع."
                )
            ),
            PodcastSnippet(
                id = "pod_3",
                showName = "بودكاست نقطة",
                title = "معالم النجاة في زمن التغيرات المتسارعة",
                speaker = "د. مطلق الجاسر",
                durationText = "2:50 دقيقة مقتطعة",
                keyTakeaways = listOf(
                    "الثبات يحتاج لصحبة صالحة وبيئة تعين على الحق.",
                    "الصمت عند عدم العلم نصف العقل والحكمة.",
                    "صانع المحتوى الذكي يستثمر في محتوى يدوم أثره."
                )
            )
        )
    }

    // --- Quiz Battle State ---
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
    var quizScore by remember { mutableIntStateOf(0) }
    var isQuizCompleted by remember { mutableStateOf(false) }

    // --- Audience Poll Generator State ---
    var audienceTopicInput by remember { mutableStateOf("غزوة بدر والدروس الاستراتيجية") }
    var generatedPollQuestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isGeneratingPolls by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DeepSlate,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.horizontalGradient(listOf(GoldSecondary, GoldPrimary))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.School, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("روضة طلب العلم والإنتاج", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("✨", fontSize = 12.sp)
                            }
                            Text("تزود بالمعرفة وحوّلها إلى أثر يدوم ⚡", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = GoldPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSlate)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // --- DAILY STREAK & HABIT BANNER ---
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFFEA580C).copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🔥", fontSize = 16.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("سلسلة الورد اليومي: $streakDays أيام متتالية", color = Color.White, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("إنجاز اليوم: $completedTasksCount من 3 مهام إسلامية", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .background(Brush.horizontalGradient(listOf(Color(0xFF151B2B), Color(0xFF0B0F19))), RoundedCornerShape(10.dp))
                                .border(1.dp, GoldPrimary.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("+${completedTasksCount * 30} XP 🏆", color = GoldPrimary, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Progress Bar
                    LinearProgressIndicator(
                        progress = { (completedTasksCount / 3f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = GoldPrimary,
                        trackColor = Color(0xFF151B2B)
                    )
                }
            }

            // --- TABS SELECTION ---
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = CardSurface,
                contentColor = GoldPrimary,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = GoldPrimary,
                            height = 3.dp
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                color = if (selectedTab == index) GoldPrimary else TextSecondary,
                                fontFamily = CairoFont,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {
                0 -> {
                    // TAB 1: Interactive Flip Cards & Memory Vault
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 28.dp)
                    ) {
                        // Daily Spark Feature
                        item {
                            DailySparkCard(
                                isSaved = isDailySparkSaved,
                                onSave = {
                                    isDailySparkSaved = !isDailySparkSaved
                                    if (!taskReadToday) {
                                        taskReadToday = true
                                        sharedPrefs.edit().putBoolean("task_read_today", true).apply()
                                        PointsManager.awardPoints(context, PointAction.DAILY_LOGIN)
                                        Toast.makeText(context, "تم إنجاز قراءة فائدة اليوم! +10 XP 🎉", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onStartScript = {
                                    val script = """
                                        🎬 سيناريو فائدة اليوم:
                                        🔥 الخطاف: «تعرف الآية التي اختصرت أسرار الطمأنينة والنفسية السوية؟»
                                        
                                        💡 الفائدة: قال تعالى: {أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ}. الذكر ليس مجرد كلمات باللسان، بل هو وصل القلب بمصدر النور والأمان.
                                        
                                        ✨ الرسالة: اجعل لسانك رطباً بذكر الله في كل أوقاتك!
                                    """.trimIndent()
                                    onStartScriptWithText(script)
                                }
                            )
                        }

                        // Filter Categories Chips
                        item {
                            Column {
                                Text("تصفح بطاقات الذاكرة التفاعلية 🃏", color = Color.White, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("اضغط على أي بطاقة لكشف المعنى السرّي وفكرة الريلز الفيروسية!", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(8.dp))

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(categories) { cat ->
                                        val isSelected = selectedCategory == cat
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedCategory = cat },
                                            label = { Text(cat, fontFamily = NotoSansFont, fontSize = 12.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = GoldPrimary,
                                                selectedLabelColor = DeepSlate,
                                                containerColor = CardSurface,
                                                labelColor = TextPrimary
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Flip Cards Items
                        items(filteredCards) { memoryCard ->
                            InteractiveFlipMemoryCardItem(
                                memoryCard = memoryCard,
                                onStartScript = {
                                    val script = """
                                        🎬 سيناريو ريلز مشتق من: ${memoryCard.title}
                                        
                                        ${memoryCard.viralReelScriptIdea}
                                        
                                        💡 التفسير والمعنى الشرعي:
                                        ${memoryCard.answerOrBackText}
                                        
                                        📖 المصدر والتوثيق:
                                        المصدر: ${memoryCard.sourceBook}
                                    """.trimIndent()
                                    onStartScriptWithText(script)
                                    if (!taskScriptCreatedToday) {
                                        taskScriptCreatedToday = true
                                        sharedPrefs.edit().putBoolean("task_script_today", true).apply()
                                    }
                                },
                                onOpenTeleprompter = {
                                    val promptText = "${memoryCard.questionOrFrontText}\n\n${memoryCard.answerOrBackText}"
                                    onOpenTeleprompterWithText(promptText)
                                },
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString("${memoryCard.title}\n${memoryCard.answerOrBackText}\nالمصدر: ${memoryCard.sourceBook}"))
                                    Toast.makeText(context, "تم نسخ الفائدة للحافظة 📋", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
                1 -> {
                    // TAB 2: Knowledge-to-Reels Converter Engine
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 28.dp)
                    ) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardSurface),
                                shape = RoundedCornerShape(20.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(GoldPrimary.copy(alpha = 0.2f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Science, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text("مختبر تحويل العلوم إلى ريلز فيروسي 🧪", color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Text("اختر الموضوع والأسلوب وسيصمم لك الذكاء الاصطناعي سيناريو احترافياً مع اللقطات البصرية.", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp, lineHeight = 16.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text("1. اختر أو اكتب الموضوع العلمي:", color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(6.dp))

                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(quickTopics) { qTopic ->
                                            val isSelected = selectedQuickTopic == qTopic
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { selectedQuickTopic = qTopic },
                                                label = { Text(qTopic, fontFamily = NotoSansFont, fontSize = 12.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = GoldPrimary,
                                                    selectedLabelColor = DeepSlate,
                                                    containerColor = Color(0xFF151B2B),
                                                    labelColor = TextPrimary
                                                )
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    OutlinedTextField(
                                        value = customConverterTopic,
                                        onValueChange = { customConverterTopic = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("أو اكتب موضوعاً مخصصاً (مثال: أسرار التوكل في سورة التوبة)...", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = GoldPrimary,
                                            unfocusedBorderColor = Color(0xFF1E293B),
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary,
                                            focusedContainerColor = Color(0xFF141C27),
                                            unfocusedContainerColor = Color(0xFF141C27)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Text("2. اختر النبرة والأسلوب الإخراجي:", color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(6.dp))

                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(toneStyles) { tStyle ->
                                            val isSelected = selectedToneStyle == tStyle
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { selectedToneStyle = tStyle },
                                                label = { Text(tStyle, fontFamily = NotoSansFont, fontSize = 12.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = GoldPrimary,
                                                    selectedLabelColor = DeepSlate,
                                                    containerColor = Color(0xFF151B2B),
                                                    labelColor = TextPrimary
                                                )
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = {
                                            val topicToUse = customConverterTopic.ifBlank { selectedQuickTopic }
                                            isGeneratingScript = true
                                            scope.launch {
                                                try {
                                                    val prompt = """
                                                        أنت خبير إخراج وصناعة محتوى إسلامي ورئيس تحرير.
                                                        قم بتحويل الموضوع الشرعي التالي إلى سيناريو فيديو قصير دعوي (45 ثانية): "$topicToUse".
                                                        أسلوب الإخراج المطلوب: "$selectedToneStyle".
                                                        
                                                        صغ النتائج بالشكل التالي:
                                                        1) 🎬 الخطاف الفيروسي (0-3 ثوان): جملة تشد الانتباه وتفتح التساؤل.
                                                        2) 💡 الشرح والمفهوم العلمي الميسر.
                                                        3) 🎥 المحور البصري والمشهد المقترح للمونتاج.
                                                        4) 📖 التوثيق الشرعي والمصدر.
                                                    """.trimIndent()

                                                    val result = AppServices.chatWithAssistant(listOf(Pair(true, prompt)))
                                                    generatedScriptOutput = result
                                                    if (!taskScriptCreatedToday) {
                                                        taskScriptCreatedToday = true
                                                        sharedPrefs.edit().putBoolean("task_script_today", true).apply()
                                                    }
                                                } catch (e: Exception) {
                                                    generatedScriptOutput = """
                                                        🎬 الخطاف الفيروسي (0-3 ثوان):
                                                        «هل تعلم أن هناك آية في القرآن تعيد ترتيب كل أفكارك وتلخص سر الطمأنينة؟»
                                                        
                                                        💡 الشرح العلمي:
                                                        التدبر الحقيقي للقرآن ليس مجرد تلاوة، بل استشعار المعاني وتطبيقها في قراراتك اليومية لترتقي بروحك.
                                                        
                                                        🎥 اللقطة البصرية:
                                                        مشهد سينمائي بطيء لضوء الشروق يتسلل بين أوراق الشجر مع صوت تلاوة هادئة خاشعة.
                                                        
                                                        📖 المصدر:
                                                        تفسير السعدي - سورة البقرة.
                                                    """.trimIndent()
                                                } finally {
                                                    isGeneratingScript = false
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                        shape = RoundedCornerShape(12.dp),
                                        enabled = !isGeneratingScript
                                    ) {
                                        if (isGeneratingScript) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = DeepSlate, strokeWidth = 2.dp)
                                            Spacer(Modifier.width(8.dp))
                                            Text("جاري تحويل العلم إلى سيناريو...", color = DeepSlate, fontFamily = CairoFont)
                                        } else {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("تحويل إلى سيناريو ريلز احترافي ✨", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    if (generatedScriptOutput.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF0B0F19), RoundedCornerShape(14.dp))
                                                .border(1.dp, GoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                                .padding(16.dp)
                                        ) {
                                            Column {
                                                Text(generatedScriptOutput, color = Color.White, fontFamily = NotoSansFont, fontSize = 14.sp, lineHeight = 20.sp)
                                                Spacer(modifier = Modifier.height(14.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            onOpenTeleprompterWithText(generatedScriptOutput)
                                                        },
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Icon(Icons.Default.Mic, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("تسجيل بالمُلقن 🎙️", color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp)
                                                    }

                                                    Button(
                                                        onClick = {
                                                            onStartScriptWithText(generatedScriptOutput)
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Icon(Icons.Default.Movie, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("بدء المونتاج 🎬", color = DeepSlate, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    // TAB 3: Interactive Podcast Soundbites & Audio Player Simulator
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 28.dp)
                    ) {
                        item {
                            Text("عصارة البودكاست واستخراج المقاطع 🎙️", color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("استمع لأهم الفوائد المقتطعة واستخرج منها سيناريوهات جاهزة بنقرة واحدة.", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                        }

                        items(podcastSnippets) { snippet ->
                            PodcastSoundbiteItem(
                                snippet = snippet,
                                onExtractScript = {
                                    val script = """
                                        🎬 سيناريو مستخرج من ${snippet.showName} - ${snippet.title}:
                                        
                                        🔥 الخطاف: «سر نفسي وفكري مهم جداً من حلقة ${snippet.title}!»
                                        
                                        💡 الفوائد الرئيسية:
                                        ${snippet.keyTakeaways.joinToString("\n• ")}
                                        
                                        ✨ الضيف المتحدث: ${snippet.speaker}
                                    """.trimIndent()
                                    onStartScriptWithText(script)
                                }
                            )
                        }
                    }
                }
                3 -> {
                    // TAB 4: Gamified Daily Speed Quiz & Audience Story Polls
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 28.dp)
                    ) {
                        // Interactive Speed Quiz Box
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardSurface),
                                shape = RoundedCornerShape(20.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Psychology, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(24.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("تحدي الفرسان العلمي اليومي ⚔️", color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        }

                                        Text("سؤال ${currentQuizIdx + 1} من ${quizQuestions.size}", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    if (!isQuizCompleted) {
                                        val question = quizQuestions[currentQuizIdx]

                                        Text(question.questionText, color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp)

                                        Spacer(modifier = Modifier.height(14.dp))

                                        question.options.forEachIndexed { optIndex, optionText ->
                                            val isSelected = selectedAnswerIdx == optIndex
                                            val isCorrect = optIndex == question.correctAnswerIndex

                                            val btnBg = when {
                                                isAnswerSubmitted && isCorrect -> Color(0xFF15803D)
                                                isAnswerSubmitted && isSelected && !isCorrect -> Color(0xFFB91C1C)
                                                isSelected -> GoldPrimary.copy(alpha = 0.3f)
                                                else -> Color(0xFF151B2B)
                                            }

                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = btnBg),
                                                shape = RoundedCornerShape(12.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) GoldPrimary else Color(0xFF1E293B)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                                    .clickable(enabled = !isAnswerSubmitted) {
                                                        selectedAnswerIdx = optIndex
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    RadioButton(
                                                        selected = isSelected,
                                                        onClick = null,
                                                        colors = RadioButtonDefaults.colors(selectedColor = GoldPrimary)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(optionText, color = Color.White, fontFamily = NotoSansFont, fontSize = 14.sp)
                                                }
                                            }
                                        }

                                        if (isAnswerSubmitted) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFF0B0F19), RoundedCornerShape(10.dp))
                                                    .padding(12.dp)
                                            ) {
                                                Column {
                                                    Text("💡 الشرح والتوثيق الشرعي:", color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    Spacer(Modifier.height(2.dp))
                                                    Text(question.explanationText, color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp)
                                                    Text("📖 المصدر: ${question.dalilText}", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                            if (!isAnswerSubmitted) {
                                                Button(
                                                    onClick = {
                                                        if (selectedAnswerIdx != null) {
                                                            isAnswerSubmitted = true
                                                            if (selectedAnswerIdx == question.correctAnswerIndex) {
                                                                quizScore += 30
                                                            }
                                                        } else {
                                                            Toast.makeText(context, "يرجى اختيار إجابة أولاً", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Text("تاكيد الإجابة ✅", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                                                }
                                            } else {
                                                Button(
                                                    onClick = {
                                                        if (currentQuizIdx < quizQuestions.size - 1) {
                                                            currentQuizIdx++
                                                            selectedAnswerIdx = null
                                                            isAnswerSubmitted = false
                                                        } else {
                                                            isQuizCompleted = true
                                                            if (!taskQuizToday) {
                                                                taskQuizToday = true
                                                                sharedPrefs.edit().putBoolean("task_quiz_today", true).apply()
                                                                PointsManager.awardPoints(context, PointAction.DAILY_LOGIN)
                                                            }
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Text(if (currentQuizIdx < quizQuestions.size - 1) "السؤال التالي ⬅️" else "عرض النتيجة والإنجاز 🏆", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    } else {
                                        // Quiz Completed Dialog Screen
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.fillMaxWidth().padding(12.dp)
                                        ) {
                                            Text("🎉 أحسنت! أتممت تحدي الفرسان العلمي اليومي", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("حصلت على +$quizScore XP تمت إضافتها للوحة المتصدرين!", color = Color.White, fontFamily = NotoSansFont, fontSize = 14.sp)

                                            Spacer(modifier = Modifier.height(16.dp))

                                            Button(
                                                onClick = {
                                                    currentQuizIdx = 0
                                                    selectedAnswerIdx = null
                                                    isAnswerSubmitted = false
                                                    isQuizCompleted = false
                                                    quizScore = 0
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text("إعادة التحدي 🔄", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Audience Story Poll Generator
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardSurface),
                                shape = RoundedCornerShape(20.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Poll, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(22.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("مولد مسابقات وتصويتات الجمهور (Story Polls) 📲", color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("أدخل أي موضوع وولد أسئلة تفاعلية جاهزة للنسخ والنشر في ستوري انستغرام وتيك توك لتنشيط الجمهور.", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp, lineHeight = 16.sp)

                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedTextField(
                                        value = audienceTopicInput,
                                        onValueChange = { audienceTopicInput = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("الموضوع (مثال: قصص الأنبياء، الصدق)...", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = GoldPrimary,
                                            unfocusedBorderColor = Color(0xFF1E293B),
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary,
                                            focusedContainerColor = Color(0xFF141C27),
                                            unfocusedContainerColor = Color(0xFF141C27)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Button(
                                        onClick = {
                                            if (audienceTopicInput.isBlank()) return@Button
                                            isGeneratingPolls = true
                                            scope.launch {
                                                try {
                                                    val prompt = "ولد 3 أسئلة مسابقات قصيرة جداً ومثيرة للجمهور لتفاعلات ستوري انستغرام وتيك توك حول موضوع: '$audienceTopicInput' مع خيارين نعم/لا أو خيارات قصيرة."
                                                    val result = AppServices.chatWithAssistant(listOf(Pair(true, prompt)))
                                                    generatedPollQuestions = result.lines().filter { it.isNotBlank() }
                                                } catch (e: Exception) {
                                                    generatedPollQuestions = listOf(
                                                        "❓ هل تعلم ما هي الآية التي نُزلت في جوف الكعبة؟ (1. نعم / 2. لا)",
                                                        "💡 من هو الصحابي الملقب بترجمان القرآن؟ (1. ابن عباس / 2. ابن مسعود)",
                                                        "🔥 سورة تُسمى سنام القرآن، هل تقرأها يومياً؟ (1. نعم دائماً / 2. سأقرأها الآن)"
                                                    )
                                                } finally {
                                                    isGeneratingPolls = false
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(44.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                        shape = RoundedCornerShape(10.dp),
                                        enabled = !isGeneratingPolls
                                    ) {
                                        if (isGeneratingPolls) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = DeepSlate, strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("توليد مسابقات الستوري ✨", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    if (generatedPollQuestions.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            generatedPollQuestions.forEach { pollText ->
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color(0xFF0B0F19), RoundedCornerShape(10.dp))
                                                        .border(1.dp, GoldPrimary.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                                        .padding(10.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(pollText, color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                                        IconButton(onClick = {
                                                            clipboardManager.setText(AnnotatedString(pollText))
                                                            Toast.makeText(context, "تم نسخ السؤال لنشره بالستوري 📋", Toast.LENGTH_SHORT).show()
                                                        }) {
                                                            Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = GoldPrimary, modifier = Modifier.size(18.dp))
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

// --- SUB-COMPONENTS ---

@Composable
fun DailySparkCard(
    isSaved: Boolean,
    onSave: () -> Unit,
    onStartScript: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Color(0xFF151B2B), Color(0xFF0B0F19))))
                .padding(18.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✨ فائدة اليوم الفريدة", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF15803D), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("جديد", color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp)
                        }
                    }

                    IconButton(onClick = onSave) {
                        Icon(
                            if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "حفظ",
                            tint = GoldPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "«سر قوله تعالى: {أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ} — الطمأنينة ليست في اعتزال الحياة بل في وصل القلب بالله وسط تزاحم الفتن والمشغلات.»",
                    color = Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onStartScript,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تحويل لسيناريو فيديو 🎬", color = DeepSlate, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
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
    var isFlipped by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "cardFlip"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isFlipped) Color(0xFF1E1B4B) else CardSurface
        ),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isFlipped) GoldPrimary else Color(0xFF1E293B)),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .clickable { isFlipped = !isFlipped }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
                .graphicsLayer {
                    // Mirror back side text so it reads correctly when flipped
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
                    Text(memoryCard.category, color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Flip, contentDescription = "انقر للقلب", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isFlipped) "وجه البطاقة 🔄" else "اضغط لكشف السر 🔄", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (rotation <= 90f) {
                    // FRONT SIDE
                    Text(memoryCard.title, color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(memoryCard.questionOrFrontText, color = TextPrimary, fontFamily = NotoSansFont, fontSize = 14.sp, lineHeight = 20.sp)
                } else {
                    // BACK SIDE (EXPLANATION & REEL IDEA)
                    Text("💡 المعنى والتفسير العلمي:", color = GoldPrimary, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(memoryCard.answerOrBackText, color = Color.White, fontFamily = NotoSansFont, fontSize = 14.sp, lineHeight = 20.sp)

                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0B0F19), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(memoryCard.viralReelScriptIdea, color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenTeleprompter,
                            modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("المُلقن 🎙️", color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp)
                        }

                        Button(
                            onClick = onStartScript,
                            modifier = Modifier.weight(1.2f),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("بدء المونتاج 🎬", color = DeepSlate, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
    var isPlaying by remember { mutableStateOf(false) }
    var speedText by remember { mutableStateOf("1.0x") }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(snippet.showName, color = GoldPrimary, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(snippet.durationText, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(snippet.title, color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("الضيف: ${snippet.speaker}", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(12.dp))

            // Simulated Audio Player Player Controls
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0B0F19), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { isPlaying = !isPlaying },
                            modifier = Modifier
                                .size(38.dp)
                                .background(GoldPrimary, CircleShape)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "تشغيل",
                                tint = DeepSlate
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(if (isPlaying) "جاري تشغيل المعاينة الصوتية..." else "انقر للاستماع للمقطع الصوتي", color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp)
                            Text("السرعة الحالية: $speedText", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                        }
                    }

                    TextButton(onClick = {
                        speedText = when (speedText) {
                            "1.0x" -> "1.25x"
                            "1.25x" -> "1.5x"
                            "1.5x" -> "2.0x"
                            else -> "1.0x"
                        }
                    }) {
                        Text(speedText, color = GoldPrimary, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("💡 أهم الفوائد المستخلصة:", color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))

            snippet.keyTakeaways.forEach { takeaway ->
                Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                    Text("• ", color = GoldPrimary, fontWeight = FontWeight.Bold)
                    Text(takeaway, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onExtractScript,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.ContentCut, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("استخراج سيناريو فيديو من المقطع ✂️", color = DeepSlate, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
