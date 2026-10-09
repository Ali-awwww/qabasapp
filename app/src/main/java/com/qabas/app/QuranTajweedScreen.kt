package com.qabas.app

/**
 * QuranTajweedScreen.kt
 * تم تحسين هذا الملف لدعم التصفح السلس للقرآن الكريم، ومحرك فحص التجويد الصوتي،
 * وموسوعة أحكام التجويد الشاملة مع إمكانية تصدير شهادات الإتقان وربط الآيات مباشرة
 * بمسار إنتاج الريلز والفيديوهات القرآنية (9:16) بضغطة زر واحدة.
 */

import android.Manifest
import android.os.Build
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.qabas.app.ui.theme.AmiriFont
import com.qabas.app.ui.theme.*
import com.qabas.app.ui.theme.DeepSlate
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.GoldSecondary
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.TextPrimary
import com.qabas.app.ui.theme.TextSecondary

data class QuranSurahItem(
    val id: Int,
    val name: String,
    val englishName: String,
    val type: String, // مكية / مدنية
    val versesCount: Int,
    val juz: Int,
    val isLocked: Boolean = false,
    val isMastered: Boolean = false,
    val score: Int = 0,
    val tajweedFocus: String,
    val famousVerses: List<String> = emptyList(),
    val virtue: String? = null // فضل السورة
)

data class QuranChallenge(
    val id: String,
    val title: String,
    val goal: String,
    val progress: Float,
    val rewardHasanat: Int,
    val icon: String,
    val type: ChallengeType
)

enum class ChallengeType {
    READING, MEMORIZATION, TAJWEED
}

data class TajweedRuleItem(
    val title: String,
    val category: String,
    val description: String,
    val exampleVerse: String,
    val audioNote: String
)

data class QuranVerseDetail(
    val surahNumber: Int,
    val surahName: String,
    val verseNumber: Int,
    val verseText: String,
    val tafseer: String,
    val tajweedNotes: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranTajweedScreen(
    onBack: () -> Unit,
    onCreateVideoFromVerse: (verseText: String, surahName: String, verseNumber: Int?) -> Unit = { _, _, _ -> },
    onNavigateToAzkar: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorderHelper = remember { AudioRecorderHelper(context) }
    var isPlayingRecordedAudio by remember { mutableStateOf(false) }

    // State for AI Voice Recitation Test
    var isRecordingRecitation by remember { mutableStateOf(false) }
    var isAnalyzingVoice by remember { mutableStateOf(false) }
    var lastTestScore by remember { mutableStateOf<Int?>(null) }
    var testFeedbackList by remember { mutableStateOf<List<Pair<String, Boolean>>>(emptyList()) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val ok = recorderHelper.startRecording()
            if (ok) {
                isRecordingRecitation = true
                lastTestScore = null
                Toast.makeText(context, "بدأ التسجيل الصوتي الحقيقي... اقرأ الآن 🎙️", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "تعذر بدء الميكروفون", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "يلزم السماح بالميكروفون للتسجيل الصوتي", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorderHelper.release()
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            QuranDataProvider.loadFromAssets(context)
        }
    }

    // المصحف الذهبي Hub: أقسام رئيسية (المصحف / القراء والروايات / التفسير والمصادر / الأذكار / أكاديمية التجويد)
    val qabasPrefs = remember { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }
    var quranSection by remember { mutableIntStateOf(0) } // 0: المصحف الشريف, 1: القراء والروايات, 2: التفسير والمصادر, 3: الأذكار, 4: أكاديمية التجويد
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterCategory by remember { mutableStateOf("الكل") } // الكل، مكية، مدنية، الأكثر تلاوة، جزء عمّ

    // State for selected Surah in Golden Quran Reader
    var activeSurahId by remember { mutableIntStateOf(qabasPrefs.getInt("last_read_surah", 1)) }
    var isReadingMode by remember { mutableStateOf(false) }
    var isPageReaderMode by remember { mutableStateOf(false) } // قارئ الصفحات الـ 604 (مصحف المدينة)
    var readerTargetVerseNumber by remember { mutableIntStateOf(-1) }
    var isPlayingAudio by remember { mutableStateOf(false) }
    var selectedReciter by remember { mutableStateOf(qabasPrefs.getString("selected_reciter", "الشيخ محمود خليل الحصري") ?: "الشيخ محمود خليل الحصري") }
    var selectedRiwaya by remember { mutableStateOf(qabasPrefs.getString("selected_riwaya", "حفص عن عاصم") ?: "حفص عن عاصم") } // رواية التلاوة
    var academySubTab by remember { mutableIntStateOf(0) } // داخل الأكاديمية: 0 المراحل, 1 اختبار الصوت, 2 الموسوعة
    var showTafseerDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showTadabburDialog by remember { mutableStateOf<QuranVerseDetail?>(null) }
    var tadabburText by remember { mutableStateOf("") }
    var isSakinaMode by remember { mutableStateOf(qabasPrefs.getBoolean("sakina_mode", false)) }
    var quranListType by remember { mutableIntStateOf(0) } // 0: السور, 1: الأجزاء

    // Handling Back Button for sub-screens (The identified "Back Point" weak point)
    BackHandler(enabled = isReadingMode || isPageReaderMode || quranSection == 6) {
        if (isReadingMode) {
            isReadingMode = false
            readerTargetVerseNumber = -1
        } else if (isPageReaderMode) {
            isPageReaderMode = false
        } else if (quranSection == 6) {
            quranSection = 0
        }
    }

    // Full 114 Surahs provided by QuranDataProvider
    val surahList = remember {
        mutableStateListOf<QuranSurahItem>().apply {
            addAll(QuranDataProvider.surahs)
        }
    }

    var showIntroAnimation by remember { mutableStateOf(true) }

    val tajweedRulesList = remember {
        listOf(
            TajweedRuleItem(
                title = "الإظهار الحلقي",
                category = "أحكام النون الساكنة والتنوين",
                description = "إخراج النون الساكنة أو التنوين من مخرجها نطقاً صريحاً بدون غنة زائدة إذا تلاها أحد حروف الحلق الستة: (الهمزة، الهاء، العين، الحاء، الغين، الخاء).",
                exampleVerse = "﴿ مَنْ آمَنَ ﴾ - ﴿ فَرِيقًا هَدَىٰ ﴾ - ﴿ سَلَامٌ هِيَ حَتَّىٰ مَطْلَعِ الْفَجْرِ ﴾",
                audioNote = "استمع للتلاوة النموذجية للإظهار الحلقي"
            ),
            TajweedRuleItem(
                title = "الإدغام بغنة",
                category = "أحكام النون الساكنة والتنوين",
                description = "دمج النون الساكنة أو التنوين في الحرف التالي إذا كان من حروف كلمة (يَنْمُو) ليصبحا حرفاً واحداً مشدداً مع مد الغنة حركتين.",
                exampleVerse = "﴿ فَمَن يَعْمَلْ مِثْقَالَ ذَرَّةٍ ﴾ - ﴿ مِن نِّعْمَةٍ ﴾ - ﴿ هُدًى وَرَحْمَةً ﴾",
                audioNote = "استمع للتلاوة النموذجية للإدغام بغنة"
            ),
            TajweedRuleItem(
                title = "الإدغام بغير غنة",
                category = "أحكام النون الساكنة والتنوين",
                description = "إدخال النون الساكنة أو التنوين إدخالاً كاملاً في حرفي (اللام والراء) بدون غنة مع تشديد الحرف التالي.",
                exampleVerse = "﴿ هُدًى لِّلْمُتَّقِينَ ﴾ - ﴿ مِن رَّبِّهِمْ ﴾ - ﴿ غَفُورٌ رَّحِيمٌ ﴾",
                audioNote = "استمع للتلاوة النموذجية للإدغام بغير غنة"
            ),
            TajweedRuleItem(
                title = "الإقلاب",
                category = "أحكام النون الساكنة والتنوين",
                description = "قلب النون الساكنة أو التنوين ميماً مخفاة بغنة حركتين عند ملاقاة حرف (الباء) مع تلامس خفيف للشفتين دون كز.",
                exampleVerse = "﴿ مِن بَعْدِ مَا جَاءَتْهُمُ ﴾ - ﴿ سَمِيعٌ بَصِيرٌ ﴾ - ﴿ كَلَّا لَيُنبَذَنَّ فِي الْحُطَمَةِ ﴾",
                audioNote = "استمع للتلاوة النموذجية للإقلاب"
            ),
            TajweedRuleItem(
                title = "الإخفاء الحقيقي",
                category = "أحكام النون الساكنة والتنوين",
                description = "نطق النون الساكنة أو التنوين بصفة بين الإظهار والإدغام عارية عن التشديد مع بقاء الغنة عند حروف الإخفاء الـ 15 (ص، ذ، ث، ك، ج، ش، ق، س، د، ط، ز، ف، ت، ض، ظ).",
                exampleVerse = "﴿ كَأْسًا دِهَاقًا ﴾ - ﴿ مِن شَرِّ مَا خَلَقَ ﴾ - ﴿ وَأَنتُمْ تَعْلَمُونَ ﴾",
                audioNote = "استمع للتلاوة النموذجية للإخفاء الحقيقي"
            ),
            TajweedRuleItem(
                title = "الإخفاء الشفوي",
                category = "أحكام الميم الساكنة",
                description = "إخفاء الميم الساكنة مع الغنة بمقدار حركتين إذا أتى بعدها حرف (الباء) فقط.",
                exampleVerse = "﴿ تَرْمِيهِم بِحِجَارَةٍ مِّن سِجِّيلٍ ﴾ - ﴿ وَمَا هُم بِمُؤْمِنِينَ ﴾",
                audioNote = "استمع للتلاوة النموذجية للإخفاء الشفوي"
            ),
            TajweedRuleItem(
                title = "إدغام المتماثلين الصغير",
                category = "أحكام الميم الساكنة",
                description = "إدغام الميم الساكنة في ميم متحركة بعدها لتصبحا ميماً مشددة واحدة مع غنة كاملة حركتين.",
                exampleVerse = "﴿ لَهُم مَّا يَشَاءُونَ ﴾ - ﴿ الَّذِي أَطْعَمَهُم مِّن جُوعٍ ﴾",
                audioNote = "استمع للتلاوة النموذجية للإدغام المتماثل"
            ),
            TajweedRuleItem(
                title = "الإظهار الشفوي",
                category = "أحكام الميم الساكنة",
                description = "نطق الميم الساكنة ظاهرة واضحة بدون غنة عند باقي حروف الهجاء الـ 26، وتتأكد الشدة عند حذري الواو والفاء.",
                exampleVerse = "﴿ أَلَمْ تَرَ كَيْفَ فَعَلَ رَبُّكَ ﴾ - ﴿ عَلَيْهِمْ وَلَا الضَّالِّينَ ﴾",
                audioNote = "استمع للتلاوة النموذجية للإظهار الشفوي"
            ),
            TajweedRuleItem(
                title = "الغنة في النون والميم المشددتين",
                category = "أحكام الشدة والتجويف",
                description = "وجوب إخراج الغنة من الخيشوم بأكمل درجاتها (حركتان) في النون والميم المشددتين وصلاً ووقفاً.",
                exampleVerse = "﴿ إِنَّا أَعْطَيْنَاكَ الْكَوْثَرَ ﴾ - ﴿ قُلْ أَعُوذُ بِرَبِّ النَّاسِ ﴾ - ﴿ ثُمَّ كَلَّا سَوْفَ تَعْلَمُونَ ﴾",
                audioNote = "استمع لتطبيق الغنة المشددة"
            ),
            TajweedRuleItem(
                title = "مراتب القلقلة",
                category = "صفات الحروف ومخارجها",
                description = "اضطراب المخرج عند النطق بحروف (ق، ط، ب، ج، د) ساكنة. كبرى (عند الوقف على حرف مشدد)، وسطى (عند الوقف على ساكن مخفف)، وصغرى (في وسط الكلمة).",
                exampleVerse = "﴿ تَبَّتْ يَدَا أَبِي لَهَبٍ وَتَبَّ ﴾ [كبرى] - ﴿ قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ ﴾ [وسطى] - ﴿ لَمْ يَلِدْ وَلَمْ يُولَدْ ﴾ [صغرى]",
                audioNote = "استمع لمراتب القلقلة النموذجية"
            ),
            TajweedRuleItem(
                title = "المد المتصل",
                category = "أحكام المدود",
                description = "أن يجتمع حرف المد مع همزة بعده في كلمة واحدة، وحكمه واجب بمقدار 4 أو 5 حركات.",
                exampleVerse = "﴿ إِذَا جَاءَ نَصْرُ اللَّهِ وَالْفَتْحُ ﴾ - ﴿ وَالسَّمَاءِ وَالطَّارِقِ ﴾ - ﴿ سِيئَتْ وُجُوهُ ﴾",
                audioNote = "استمع للتلاوة النموذجية للمد المتصل"
            ),
            TajweedRuleItem(
                title = "المد المنفصل",
                category = "أحكام المدود",
                description = "أن يكون حرف المد في آخر الكلمة والهمزة في أول الكلمة التي تليها، وحكمه جائز بمقدار 4 أو 5 حركات (ويمد حركتين في قصر المنفصل).",
                exampleVerse = "﴿ إِنَّا أَنزَلْنَاهُ فِي لَيْلَةِ الْقَدْرِ ﴾ - ﴿ قُلْ يَا أَيُّهَا الْكَافِرُونَ ﴾ - ﴿ تُوبُوا إِلَى اللَّهِ ﴾",
                audioNote = "استمع للتلاوة النموذجية للمد المنفصل"
            ),
            TajweedRuleItem(
                title = "المد اللازم الكلمي والحرفي",
                category = "أحكام المدود",
                description = "أن يأتي بعد حرف المد سكون أصلي ثابت وصلاً ووقفاً، ومقداره 6 حركات لازمة قاطعة.",
                exampleVerse = "﴿ وَلَا الضَّالِّينَ ﴾ [كلمي مثقل] - ﴿ الْحَاقَّةُ ﴾ [كلمي مثقل] - ﴿ آلْآنَ ﴾ [كلمي مخفف] - ﴿ ق ۚ وَالْقُرْآنِ ﴾ [حرفي]",
                audioNote = "استمع لتطبيق المد اللازم"
            ),
            TajweedRuleItem(
                title = "المد العارض للسكون ومد اللين",
                category = "أحكام المدود",
                description = "أن يأتي بعد حرف المد أو اللين حرف متحرك سكن بسبب الوقف، ويمد بمقدار 2 أو 4 أو 6 حركات.",
                exampleVerse = "﴿ الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ ﴾ - ﴿ لِإِيلَافِ قُرَيْشٍ ﴾ - ﴿ وَآمَنَهُم مِّنْ خَوْفٍ ﴾",
                audioNote = "استمع للمد العارض للسكون ومد اللين"
            ),
            TajweedRuleItem(
                title = "أحكام الراء تفخيماً وترقيقاً",
                category = "صفات الحروف ومخارجها",
                description = "تفخم الراء إذا كانت مفتوحة أو مضمومة أو ساكنة إثر فتح/ضم، وترقق إذا كانت مكسورة أو ساكنة إثر كسر أصلي لازم.",
                exampleVerse = "﴿ رَبَّنَا آتِنَا ﴾ [تفخيم] - ﴿ فِرْعَوْنَ ﴾ [ترقيق] - ﴿ إِنَّا أَنزَلْنَاهُ فِي لَيْلَةِ الْقَدْرِ ﴾ [تفخيم وصلاً]",
                audioNote = "استمع لتفخيم وترقيق الراء"
            ),
            TajweedRuleItem(
                title = "تفخيم وترقيق لام لفظ الجلالة",
                category = "أحكام لفظ الجلالة",
                description = "تغلظ وتفخم اللام في اسم الجلالة (اللَّه) إذا سبقت بفتح أو ضم، وترقق إذا سبقت بكسر أصلي أو عارض.",
                exampleVerse = "﴿ قُلْ هُوَ اللَّهُ أَحَدٌ ﴾ [تفخيم] - ﴿ شَهِدَ اللَّهُ ﴾ [تفخيم] - ﴿ بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ ﴾ [ترقيق]",
                audioNote = "استمع لتفخيم وترقيق اسم الجلالة"
            )
        )
    }

    // Filtered Surahs based on search query and category
    val filteredSurahs = remember(searchQuery, selectedFilterCategory, surahList) {
        surahList.filter { surah ->
            val matchesCategory = when (selectedFilterCategory) {
                "مكية" -> surah.type == "مكية"
                "مدنية" -> surah.type == "مدنية"
                "الأكثر تلاوة" -> surah.id in listOf(1, 2, 18, 36, 55, 67, 112, 113, 114)
                "جزء عمّ" -> surah.juz == 30
                else -> true
            }

            val query = searchQuery.trim()
            val matchesSearch = if (query.isEmpty()) {
                true
            } else {
                surah.name.contains(query) ||
                        surah.englishName.contains(query, ignoreCase = true) ||
                        surah.id.toString() == query ||
                        "جزء ${surah.juz}".contains(query) ||
                        surah.tajweedFocus.contains(query) ||
                        surah.famousVerses.any { it.contains(query) }
            }

            matchesCategory && matchesSearch
        }
    }

    if (showIntroAnimation) {
        QuranOpeningAnimation(onFinish = { showIntroAnimation = false })
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = ManuscriptGold.copy(alpha = 0.2f),
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MenuBook, contentDescription = null, tint = ManuscriptGold, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "المصحف وأكاديمية التجويد 📖",
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = OldInk
                            )
                            Text(
                                text = "تراث الأمة المحفوظ - صناعة ريلز قرآني بعمق تاريخي",
                                fontFamily = CairoFont,
                                fontSize = 10.sp,
                                color = OldInk.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "العودة", tint = OldInk)
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        isSakinaMode = !isSakinaMode
                        qabasPrefs.edit().putBoolean("sakina_mode", isSakinaMode).apply()
                        Toast.makeText(context, if (isSakinaMode) "تم تفعيل وضع السكينة 🕯️" else "تم إيقاف وضع السكينة", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            if (isSakinaMode) Icons.Default.NightsStay else Icons.Default.LightMode,
                            contentDescription = "وضع السكينة",
                            tint = if (isSakinaMode) Color(0xFFFFD700) else OldInk
                        )
                    }
                    IconButton(onClick = { quranSection = 6 }) { // New section for Tadabbur Journal
                        Icon(Icons.Default.HistoryEdu, contentDescription = "سجل التدبر", tint = ManuscriptGold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AgedPaper)
            )
        },
        bottomBar = bottomBar,
        containerColor = AntiqueParchment
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // شريط "مستوى النور" (نظام ربيع القلوب)
            Surface(
                color = AgedPaper,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "مستوى النور ✨",
                                color = ManuscriptGold,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "المرتبة: التالِي المثابر",
                                color = OldInk.copy(alpha = 0.6f),
                                fontFamily = NotoSansFont,
                                fontSize = 10.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { 0.65f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = ManuscriptGold,
                            trackColor = ManuscriptGold.copy(alpha = 0.1f)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "8,420",
                            color = Color(0xFF065F46),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            "حسنة مقدّرة",
                            color = OldInk.copy(alpha = 0.5f),
                            fontFamily = CairoFont,
                            fontSize = 8.sp
                        )
                    }
                }
            }

            // شريط أقسام المصحف الذهبي (Hub)
            ScrollableTabRow(
                selectedTabIndex = quranSection,
                containerColor = AgedPaper,
                contentColor = ManuscriptGold,
                edgePadding = 12.dp,
                divider = {}
            ) {
                Tab(
                    selected = quranSection == 0,
                    onClick = { quranSection = 0; isReadingMode = false; isPageReaderMode = false; showTafseerDialog = null },
                    text = { Text("المصحف 📖", fontFamily = CairoFont, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = quranSection == 1,
                    onClick = { quranSection = 1; isReadingMode = false; isPageReaderMode = false; showTafseerDialog = null },
                    text = { Text("القرّاء 🎙️", fontFamily = CairoFont, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = quranSection == 2,
                    onClick = { quranSection = 2; isReadingMode = false; isPageReaderMode = false; showTafseerDialog = null },
                    text = { Text("التفسير 📚", fontFamily = CairoFont, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = quranSection == 3,
                    onClick = { quranSection = 3; isReadingMode = false; isPageReaderMode = false; showTafseerDialog = null },
                    text = { Text("الأذكار 🤲", fontFamily = CairoFont, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = quranSection == 4,
                    onClick = { quranSection = 4; isReadingMode = false; isPageReaderMode = false; showTafseerDialog = null },
                    text = { Text("ربيع القلوب 🎓", fontFamily = CairoFont, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = quranSection == 5,
                    onClick = { quranSection = 5; isReadingMode = false; isPageReaderMode = false; showTafseerDialog = null },
                    text = { Text("التحفيظ والورد 🕋", fontFamily = CairoFont, fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (quranSection) {
                0 -> {
                    if (isPageReaderMode) {
                        MushafReaderScreen(
                            onClose = { isPageReaderMode = false }
                        )
                    } else if (isReadingMode) {
                        val activeSurah = surahList.find { it.id == activeSurahId } ?: surahList[0]
                        GoldenMushafReaderView(
                            surah = activeSurah,
                            selectedReciter = selectedReciter,
                            isPlayingAudio = isPlayingAudio,
                            highlightVerse = readerTargetVerseNumber,
                            isSakinaMode = isSakinaMode,
                            onToggleAudio = {
                                isPlayingAudio = !isPlayingAudio
                                Toast.makeText(context, "التلاوة الصوتية عبر الإنترنت قيد التجهيز — اختر قارئك من قسم «القراء والروايات»", Toast.LENGTH_SHORT).show()
                            },
                            onSelectReciter = { selectedReciter = it; qabasPrefs.edit().putString("selected_reciter", it).apply() },
                            onCloseReader = {
                                isReadingMode = false
                                readerTargetVerseNumber = -1
                                qabasPrefs.edit().putInt("last_read_surah", activeSurahId).apply()
                            },
                            onShowTafseer = { verse, tafseer -> showTafseerDialog = Pair(verse, tafseer) },
                            onShowTadabbur = { detail -> 
                                showTadabburDialog = detail
                                scope.launch {
                                    val db = AppDatabase.getDatabase(context)
                                    val existing = db.tadabburDao().getReflectionForVerse(detail.surahNumber, detail.verseNumber)
                                    tadabburText = existing?.reflectionText ?: ""
                                }
                            },
                            onCreateVideoFromVerse = onCreateVideoFromVerse
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // دخول قارئ الصفحات (604 — مصحف المدينة): تصميم أصلي لقبس
                            val lastPage = qabasPrefs.getInt("last_read_page", 1)
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { isPageReaderMode = true },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF151B2B)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "📖 المصحف بالصفحات",
                                            color = Color.White, fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold, fontSize = 16.sp
                                        )
                                        Text(
                                            "تصفح الـ ٦٠٤ صفحات بالسحب — آخر صفحة: ${lastPage}",
                                            color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp
                                        )
                                    }
                                    Text("‹", color = GoldPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            val lastReadSurah = surahList.find { it.id == activeSurahId }
                            if (lastReadSurah != null) {
                                GoldenContinueReadingCard(
                                    surah = lastReadSurah,
                                    selectedReciter = selectedReciter,
                                    onResume = { isReadingMode = true },
                                    onClear = {
                                        qabasPrefs.edit().remove("last_read_surah").apply()
                                        activeSurahId = 1
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            GoldenQuranSurahListView(
                                surahList = filteredSurahs,
                                searchQuery = searchQuery,
                                selectedCategory = selectedFilterCategory,
                                onSelectCategory = { selectedFilterCategory = it },
                                onSearchChange = { searchQuery = it },
                                onOpenSurah = { surahId ->
                                    activeSurahId = surahId
                                    readerTargetVerseNumber = -1
                                    qabasPrefs.edit().putInt("last_read_surah", surahId).apply()
                                    isReadingMode = true
                                },
                                onCreateVideoFromVerse = onCreateVideoFromVerse,
                                onOpenVerse = { surahNumber, verseNumber ->
                                    activeSurahId = surahNumber
                                    qabasPrefs.edit().putInt("last_read_surah", surahNumber).apply()
                                    readerTargetVerseNumber = verseNumber
                                    isReadingMode = true
                                },
                                isSakinaMode = isSakinaMode,
                                listType = quranListType,
                                onListTypeChange = { quranListType = it }
                            )
                        }
                    }
                }
                1 -> GoldenQuranRecitersView(
                    selectedReciter = selectedReciter,
                    onSelectReciter = { selectedReciter = it; qabasPrefs.edit().putString("selected_reciter", it).apply() },
                    selectedRiwaya = selectedRiwaya,
                    onSelectRiwaya = { selectedRiwaya = it; qabasPrefs.edit().putString("selected_riwaya", it).apply() }
                )
                2 -> GoldenTafsirSourcesView(
                    onBackToMushaf = { quranSection = 0 }
                )
                3 -> GoldenAdhkarView(onOpenFullAzkar = onNavigateToAzkar)
                4 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        ScrollableTabRow(
                            selectedTabIndex = academySubTab,
                            containerColor = Color(0xFF0B0F19),
                            contentColor = GoldPrimary,
                            edgePadding = 12.dp
                        ) {
                            Tab(
                                selected = academySubTab == 0,
                                onClick = { academySubTab = 0 },
                                text = { Text("المراحل 🎯", fontFamily = CairoFont, fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = academySubTab == 1,
                                onClick = { academySubTab = 1 },
                                text = { Text("اختبار التلاوة 🎙️", fontFamily = CairoFont, fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = academySubTab == 2,
                                onClick = { academySubTab = 2 },
                                text = { Text("موسوعة الأحكام 📚", fontFamily = CairoFont, fontWeight = FontWeight.Bold) }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        when (academySubTab) {
                            0 -> TajweedAcademyStagesView(
                                surahList = surahList,
                                onStartTest = { surahId ->
                                    activeSurahId = surahId
                                    qabasPrefs.edit().putInt("last_read_surah", surahId).apply()
                                    academySubTab = 1
                                }
                            )
                            1 -> AiVoiceRecitationTestView(
                                activeSurahName = surahList.find { it.id == activeSurahId }?.name ?: "الفاتحة",
                                isRecording = isRecordingRecitation,
                                isAnalyzing = isAnalyzingVoice,
                                testScore = lastTestScore,
                                feedbackList = testFeedbackList,
                                isPlayingAudio = isPlayingRecordedAudio,
                                hasRecordedAudio = recorderHelper.outputFile?.let { it.exists() && it.length() > 0 } ?: false,
                                onPlayRecordedAudio = {
                                    if (isPlayingRecordedAudio) {
                                        recorderHelper.stopPlayback()
                                        isPlayingRecordedAudio = false
                                    } else {
                                        isPlayingRecordedAudio = true
                                        val started = recorderHelper.startPlayback {
                                            isPlayingRecordedAudio = false
                                        }
                                        if (!started) {
                                            isPlayingRecordedAudio = false
                                            Toast.makeText(context, "تعذر تشغيل التسجيل الصوتي", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onStartRecording = {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                onStopRecordingAndAnalyze = {
                                    isRecordingRecitation = false
                                    val file = recorderHelper.stopRecording()
                                    isAnalyzingVoice = true

                                    val fileSizeKb = (file?.length() ?: 0L) / 1024

                                    scope.launch {
                                        isAnalyzingVoice = false
                                        lastTestScore = 0
                                        testFeedbackList = listOf(
                                            "تم حفظ التسجيل الصوتي (${fileSizeKb} KB)" to true,
                                            "تحليل التجويد الصوتي غير متاح حالياً — حُفظ التسجيل للمراجعة اليدوية" to false
                                        )
                                        Toast.makeText(context, "تم حفظ التسجيل. تحليل التجويد غير متاح حالياً.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            )
                            2 -> TajweedRulesEncyclopediaView(rules = tajweedRulesList)
                        }
                    }
                }
                5 -> {
                    MemorizationSystemView(
                        surahList = surahList
                    )
                }
                6 -> GoldenTadabburJournalView(onBack = { quranSection = 0 }, isSakinaMode = isSakinaMode)
            }
        }
    }

    if (showTafseerDialog != null) {
        val (verse, tafseer) = showTafseerDialog ?: Pair("", "")
        AlertDialog(
            onDismissRequest = { showTafseerDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = GoldPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("التفسير الميسر وأسرار التلاوة", fontFamily = TajawalFont, fontWeight = FontWeight.Bold, color = GoldPrimary, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0B0F19), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = verse,
                            fontFamily = AmiriFont,
                            fontSize = 18.sp,
                            color = GoldPrimary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        text = tafseer.ifEmpty {
                            "لا يتوفر تفسير ميسر لهذه الآية في النسخة المدمجة حالياً."
                        },
                        fontFamily = NotoSansFont,
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 22.sp
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showTafseerDialog = null }, colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)) {
                    Text("إغلاق", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF151B2B)
        )
    }

    if (showTadabburDialog != null) {
        val detail = showTadabburDialog!!
        AlertDialog(
            onDismissRequest = { showTadabburDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.HistoryEdu, contentDescription = null, tint = GoldPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("خاطرة تدبر ✍️", fontFamily = TajawalFont, fontWeight = FontWeight.Bold, color = GoldPrimary, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "﴿${detail.surahName}: ${detail.verseNumber}﴾",
                        fontFamily = AmiriFont,
                        fontSize = 16.sp,
                        color = GoldPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tadabburText,
                        onValueChange = { tadabburText = it },
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        placeholder = { Text("اكتب ما فتح الله به عليك من تدبر في هذه الآية...", color = TextSecondary.copy(alpha = 0.5f), fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = GoldPrimary.copy(alpha = 0.3f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val db = AppDatabase.getDatabase(context)
                            db.tadabburDao().insertReflection(
                                TadabburRecord(
                                    surahId = detail.surahNumber,
                                    surahName = detail.surahName,
                                    verseNumber = detail.verseNumber,
                                    reflectionText = tadabburText
                                )
                            )
                            showTadabburDialog = null
                            Toast.makeText(context, "تم حفظ الخاطرة في سجل النور ✨", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Text("حفظ الخاطرة", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTadabburDialog = null }) {
                    Text("إلغاء", color = TextSecondary, fontFamily = CairoFont)
                }
            },
            containerColor = Color(0xFF151B2B)
        )
    }
    }
}

@Composable
fun GoldenQuranSurahListView(
    surahList: List<QuranSurahItem>,
    searchQuery: String,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onOpenSurah: (Int) -> Unit,
    onCreateVideoFromVerse: (verseText: String, surahName: String, verseNumber: Int?) -> Unit,
    onOpenVerse: (Int, Int) -> Unit,
    isSakinaMode: Boolean = false,
    listType: Int = 0,
    onListTypeChange: (Int) -> Unit = {}
) {
    val categories = listOf("الكل", "مكية", "مدنية", "الأكثر تلاوة", "جزء عمّ")
    val paperColor = if (isSakinaMode) Color(0xFFFDF4E3) else AgedPaper
    val textColor = if (isSakinaMode) Color(0xFF4A3728) else OldInk
    val goldColor = if (isSakinaMode) Color(0xFFB8860B) else ManuscriptGold

    val context = LocalContext.current
    val trimmedSearch = searchQuery.trim()
    val verseResults = remember(trimmedSearch) {
        if (trimmedSearch.length >= 2) {
            QuranDataProvider.loadFromAssets(context)
            QuranDataProvider.searchVerses(trimmedSearch)
        } else emptyList()
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // Tab Selector: Surah vs Juz
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(goldColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            listOf("السور", "الأجزاء").forEachIndexed { index, title ->
                val isSelected = listType == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) goldColor else Color.Transparent)
                        .clickable { onListTypeChange(index) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        title,
                        fontFamily = CairoFont,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else textColor.copy(alpha = 0.7f)
                    )
                }
            }
        }

        if (listType == 0) {
            // Search Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("بحث عن سورة، آية (مثال: الكرسي، الصراط)، أو رقم...", color = textColor.copy(alpha = 0.5f), fontFamily = NotoSansFont, fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = goldColor) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "مسح", tint = textColor.copy(alpha = 0.5f))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = goldColor,
                    unfocusedBorderColor = goldColor.copy(alpha = 0.2f),
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    focusedContainerColor = paperColor.copy(alpha = 0.5f),
                    unfocusedContainerColor = paperColor.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            )

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Category Filter Chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { category ->
                val isSelected = selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectCategory(category) },
                        label = {
                            Text(
                                category,
                                fontFamily = CairoFont,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = goldColor,
                            selectedLabelColor = Color.White,
                            containerColor = paperColor,
                            labelColor = textColor
                        ),
                        shape = RoundedCornerShape(20.dp),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = goldColor.copy(alpha = 0.3f),
                            selectedBorderColor = goldColor
                        )
                    )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (surahList.isEmpty() && verseResults.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.SearchOff, contentDescription = null, tint = OldInk.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("لم يتم العثور على نتائج تطابق \"$searchQuery\"", color = OldInk.copy(alpha = 0.5f), fontFamily = NotoSansFont, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (verseResults.isNotEmpty()) {
                    item(key = "verse_search_header") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "نتائج البحث في الآيات ✨",
                                color = ManuscriptGold,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "انقر على آية لفتحها في المصحف مع تظليلها",
                                color = OldInk.copy(alpha = 0.6f),
                                fontFamily = CairoFont,
                                fontSize = 11.sp
                            )
                        }
                    }
                    items(verseResults, key = { "verse_${it.surahNumber}_${it.verseNumber}" }) { verse ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AgedPaper),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenVerse(verse.surahNumber, verse.verseNumber) }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = verse.verseText,
                                    color = OldInk,
                                    fontFamily = AmiriFont,
                                    fontSize = 16.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "﴿${verse.surahName}: ${verse.verseNumber}﴾",
                                        color = ManuscriptGold,
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "فتح الآية 📖",
                                        color = ManuscriptGold.copy(alpha = 0.7f),
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                    if (surahList.isNotEmpty()) {
                        item(key = "verse_search_surah_divider") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                HorizontalDivider(color = ManuscriptGold.copy(alpha = 0.1f))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "السور المطابقة",
                                    color = OldInk.copy(alpha = 0.5f),
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
                items(surahList, key = { it.id }) { surah ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AgedPaper),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (surah.isMastered) ManuscriptGold else ManuscriptGold.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSurah(surah.id) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Surah Number Stamp
                                Surface(
                                    color = if (surah.isMastered) ManuscriptGold else AntiqueParchment,
                                    shape = CircleShape,
                                    modifier = Modifier.size(38.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.3f))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${surah.id}",
                                            color = if (surah.isMastered) Color.White else OldInk,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "سورة ${surah.name}",
                                            color = OldInk,
                                            fontFamily = AmiriFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 19.sp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = AntiqueParchment,
                                            shape = RoundedCornerShape(6.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.2f))
                                        ) {
                                            Text(
                                                text = surah.type,
                                                color = ManuscriptGold,
                                                fontFamily = CairoFont,
                                                fontSize = 9.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "آياتها: ${surah.versesCount} | الجزء ${surah.juz} • ${surah.tajweedFocus}",
                                        color = OldInk.copy(alpha = 0.6f),
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(onClick = { onOpenSurah(surah.id) }) {
                                    Icon(Icons.Default.MenuBook, contentDescription = "قراءة", tint = ManuscriptGold, modifier = Modifier.size(22.dp))
                                }
                            }

                            // Famous verses quick-snippet if present
                            if (surah.famousVerses.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = ManuscriptGold.copy(alpha = 0.1f))
                                Spacer(modifier = Modifier.height(8.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    surah.famousVerses.take(2).forEach { verseSnippet ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(AntiqueParchment.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                                .border(0.5.dp, ManuscriptGold.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = verseSnippet,
                                                color = OldInk.copy(alpha = 0.9f),
                                                fontFamily = AmiriFont,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )

                                            Spacer(modifier = Modifier.width(6.dp))

                                            Surface(
                                                onClick = {
                                                    onCreateVideoFromVerse(verseSnippet, surah.name, null)
                                                },
                                                color = ManuscriptGold.copy(alpha = 0.1f),
                                                shape = RoundedCornerShape(8.dp),
                                                border = androidx.compose.foundation.BorderStroke(0.5.dp, ManuscriptGold)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                ) {
                                                    Icon(Icons.Default.Movie, contentDescription = null, tint = ManuscriptGold, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("إنشاء ريلز 🎬", color = ManuscriptGold, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
        } else {
            // Juz list
            Spacer(modifier = Modifier.height(10.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(30) { index ->
                    val juzNum = index + 1
                    val startSurah = surahList.firstOrNull { it.juz == juzNum } ?: surahList[0]
                    Card(
                        colors = CardDefaults.cardColors(containerColor = paperColor),
                        border = androidx.compose.foundation.BorderStroke(1.dp, goldColor.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.clickable { onOpenSurah(startSurah.id) }
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("الجزء $juzNum", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = goldColor, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("يبدأ من: ${startSurah.name}", fontFamily = AmiriFont, fontSize = 12.sp, color = textColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GoldenMushafReaderView(
    surah: QuranSurahItem,
    selectedReciter: String,
    isPlayingAudio: Boolean,
    onToggleAudio: () -> Unit,
    onSelectReciter: (String) -> Unit,
    onCloseReader: () -> Unit,
    onShowTafseer: (String, String) -> Unit,
    onCreateVideoFromVerse: (verseText: String, surahName: String, verseNumber: Int?) -> Unit,
    highlightVerse: Int = -1,
    isSakinaMode: Boolean = false,
    onShowTadabbur: (QuranVerseDetail) -> Unit = {}
) {
    val context = LocalContext.current
    val qabasPrefs = remember { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }
    var isTajweedColored by remember { mutableStateOf(true) }
    var isMemorizationMode by remember { mutableStateOf(false) }
    var isBookmarked by remember { mutableStateOf(qabasPrefs.getBoolean("bookmarked_surah_${surah.id}", false)) }

    val paperColor = if (isSakinaMode) Color(0xFFFDF4E3) else AgedPaper
    val textColor = if (isSakinaMode) Color(0xFF4A3728) else OldInk
    val goldColor = if (isSakinaMode) Color(0xFFB8860B) else ManuscriptGold

    // Expanded Authentic Quran Verses for the Surah
    val versesList = remember(surah.id) {
        QuranDataProvider.loadFromAssets(context)
        QuranDataProvider.loadTafsirFromAssets(context)
        getAuthenticVersesForSurah(surah.id, surah.name)
    }

    val listState = rememberLazyListState()

    LaunchedEffect(surah.id, versesList, highlightVerse) {
        if (highlightVerse > 0) {
            val verseIndex = versesList.indexOfFirst { it.verseNumber == highlightVerse }
            if (verseIndex >= 0) {
                val basmalaOffset = if (surah.id == 9) 0 else 1
                listState.animateScrollToItem(verseIndex + basmalaOffset, scrollOffset = 0)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AntiqueParchment)
            .padding(12.dp)
    ) {
        // Gilded Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AgedPaper, RoundedCornerShape(16.dp))
                .border(1.dp, ManuscriptGold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onCloseReader) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = OldInk)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "سورة ${surah.name} 📖",
                    color = OldInk,
                    fontFamily = AmiriFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                Text(
                    "${surah.type} • ${surah.versesCount} آيات • الجزء ${surah.juz}",
                    color = OldInk.copy(alpha = 0.6f),
                    fontFamily = CairoFont,
                    fontSize = 10.sp
                )
            }
            Row {
                IconButton(onClick = {
                    isBookmarked = !isBookmarked
                    qabasPrefs.edit().putBoolean("bookmarked_surah_${surah.id}", isBookmarked).apply()
                    Toast.makeText(context, if (isBookmarked) "تم وضع علامة الفاصل القرآني 🔖" else "تم إزالة العلامة", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "فاصل القراءة",
                        tint = ManuscriptGold
                    )
                }
                IconButton(onClick = onToggleAudio) {
                    Icon(
                        imageVector = if (isPlayingAudio) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                        contentDescription = "تلاوة صوتية",
                        tint = ManuscriptGold,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Reciter Selection & Controls Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AgedPaper.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                .border(0.5.dp, ManuscriptGold.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = ManuscriptGold, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(selectedReciter, color = OldInk, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Row {
                FilterChip(
                    selected = isTajweedColored,
                    onClick = { isTajweedColored = !isTajweedColored },
                    label = { Text("ألوان التجويد 🎨", fontFamily = NotoSansFont, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ManuscriptGold.copy(alpha = 0.2f),
                        selectedLabelColor = ManuscriptGold,
                        containerColor = AntiqueParchment,
                        labelColor = OldInk
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                FilterChip(
                    selected = isMemorizationMode,
                    onClick = { isMemorizationMode = !isMemorizationMode },
                    label = { Text("مساعد التسميع 🧠", fontFamily = NotoSansFont, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF10B981).copy(alpha = 0.2f),
                        selectedLabelColor = Color(0xFF065F46),
                        containerColor = AntiqueParchment,
                        labelColor = OldInk
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Mushaf Frame Card
        Card(
            colors = CardDefaults.cardColors(containerColor = AgedPaper),
            border = androidx.compose.foundation.BorderStroke(2.dp, ManuscriptGold.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            if (versesList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = ManuscriptGold.copy(alpha = 0.7f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "سورة ${surah.name}",
                            color = ManuscriptGold,
                            fontFamily = AmiriFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "النص غير متوفر حالياً (جاري تجهيز نص هذه السورة بالرسم العثماني الموثوق).",
                            color = OldInk.copy(alpha = 0.7f),
                            fontFamily = NotoSansFont,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            color = AgedPaper,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.2f))
                        ) {
                            Text(
                                text = "عدد الآيات: ${surah.versesCount} • النزول: ${surah.type} • الجزء: ${surah.juz}",
                                color = ManuscriptGold,
                                fontFamily = NotoSansFont,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        OutlinedButton(
                            onClick = onCloseReader,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ManuscriptGold),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("العودة لقائمة السور", fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        if (surah.virtue != null) {
                            VirtueHighlightCard(surah = surah)
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // Basmala header (except Surah At-Tawbah)
                    if (surah.id != 9) {
                        item {
                            Text(
                                "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                                color = ManuscriptGold,
                                fontFamily = AmiriFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }

                    itemsIndexed(versesList) { idx, verseItem ->
                        val displayVerse = if (isMemorizationMode) {
                            verseItem.verseText.split(" ").mapIndexed { i, word ->
                                if (i % 2 == 1 && !word.startsWith("﴿")) " 🙈 " else word
                            }.joinToString(" ")
                        } else verseItem.verseText

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (verseItem.verseNumber == highlightVerse) {
                                    ManuscriptGold.copy(alpha = 0.1f)
                                } else {
                                    AgedPaper.copy(alpha = 0.5f)
                                }
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                if (verseItem.verseNumber == highlightVerse) 2.dp else 1.dp,
                                if (verseItem.verseNumber == highlightVerse) ManuscriptGold
                                else if (isPlayingAudio && idx == 0) ManuscriptGold else ManuscriptGold.copy(alpha = 0.1f)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = displayVerse,
                                        color = OldInk,
                                        fontFamily = AmiriFont,
                                        fontSize = 21.sp,
                                        lineHeight = 38.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Action Row Under Verse: Create Video + Tafseer + Audio
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Create Video Button with Authentic Verse Text
                                    Button(
                                        onClick = { onCreateVideoFromVerse(verseItem.verseText, surah.name, verseItem.verseNumber) },
                                        colors = ButtonDefaults.buttonColors(containerColor = ManuscriptGold),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.MovieFilter, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("إنشاء ريلز من الآية 🎬", color = Color.White, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }

                                    Row {
                                        IconButton(onClick = { onShowTafseer(verseItem.verseText, verseItem.tafseer) }) {
                                            Icon(Icons.Default.MenuBook, contentDescription = "التفسير", tint = ManuscriptGold, modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(onClick = {
                                            Toast.makeText(context, "جاري الاستماع للآية ${verseItem.verseNumber} بصوت $selectedReciter 🎙️", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Default.VolumeUp, contentDescription = "استماع", tint = ManuscriptGold, modifier = Modifier.size(18.dp))
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

// Authentic Verses helper delegating to QuranDataProvider
private fun getAuthenticVersesForSurah(surahId: Int, surahName: String): List<QuranVerseDetail> {
    return QuranDataProvider.getVersesForSurah(surahId, surahName)
}

@Composable
fun TajweedRuleCard(rule: TajweedRuleItem) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = AgedPaper),
        border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rule.title,
                    color = ManuscriptGold,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Surface(
                    color = ManuscriptGold.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = rule.category,
                        color = ManuscriptGold,
                        fontFamily = CairoFont,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = rule.description,
                color = OldInk.copy(alpha = 0.8f),
                fontFamily = NotoSansFont,
                fontSize = 13.sp,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AntiqueParchment, RoundedCornerShape(10.dp))
                    .border(0.5.dp, ManuscriptGold.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Text("أمثلة تطبيقية من القرآن الكريم:", color = ManuscriptGold, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(rule.exampleVerse, color = OldInk, fontFamily = AmiriFont, fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        Toast.makeText(context, "🔊 استمع للتطبيق العملي لحكم: ${rule.title}", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = ManuscriptGold, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(rule.audioNote, color = ManuscriptGold, fontFamily = NotoSansFont, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun QuranChallengeCard(challenge: QuranChallenge) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AgedPaper),
        border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .width(260.dp)
            .padding(end = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = ManuscriptGold.copy(alpha = 0.1f),
                    shape = CircleShape,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            when(challenge.icon) {
                                "MenuBook" -> "📖"
                                "AutoAwesome" -> "✨"
                                "Lightbulb" -> "💡"
                                else -> "🌟"
                            },
                            fontSize = 16.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    challenge.title,
                    color = ManuscriptGold,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                challenge.goal,
                color = OldInk,
                fontFamily = NotoSansFont,
                fontSize = 11.sp,
                maxLines = 2,
                minLines = 2
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = challenge.progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = ManuscriptGold,
                trackColor = ManuscriptGold.copy(alpha = 0.1f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "+${challenge.rewardHasanat} حسنة",
                    color = Color(0xFF065F46),
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
                Text(
                    "${(challenge.progress * 100).toInt()}%",
                    color = OldInk.copy(alpha = 0.5f),
                    fontFamily = NotoSansFont,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun VirtueHighlightCard(surah: QuranSurahItem) {
    if (surah.virtue == null) return
    
    Card(
        colors = CardDefaults.cardColors(containerColor = ManuscriptGold.copy(alpha = 0.05f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("✨", fontSize = 20.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "لماذا نعشق سورة ${surah.name}؟",
                    color = ManuscriptGold,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    surah.virtue!!,
                    color = OldInk.copy(alpha = 0.8f),
                    fontFamily = AmiriFont,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = ManuscriptGold,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { 
                        // logic to add to hifz plan
                    }
            ) {
                Text(
                    "حفظ 🧠",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun SpiritualNotificationSettingsCard() {
    val context = LocalContext.current
    val qabasPrefs = remember { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }
    var isEnabled by remember { mutableStateOf(qabasPrefs.getBoolean("spiritual_notifications_enabled", true)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isEnabled = true
            qabasPrefs.edit().putBoolean("spiritual_notifications_enabled", true).apply()
            SpiritualNotificationManager.scheduleNotification(context, "suhur")
            SpiritualNotificationManager.scheduleNotification(context, "fajr")
            Toast.makeText(context, "تم تفعيل أنوار السحر والفجر ✨", Toast.LENGTH_SHORT).show()
        } else {
            isEnabled = false
            qabasPrefs.edit().putBoolean("spiritual_notifications_enabled", false).apply()
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = AgedPaper),
        border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "أنوار السحر والفجر 🕰️",
                        color = ManuscriptGold,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        "استقبل قبسات من آيات وفضائل السور في أوقات الاستجابة",
                        color = OldInk.copy(alpha = 0.6f),
                        fontFamily = NotoSansFont,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                isEnabled = true
                                qabasPrefs.edit().putBoolean("spiritual_notifications_enabled", true).apply()
                                SpiritualNotificationManager.scheduleNotification(context, "suhur")
                                SpiritualNotificationManager.scheduleNotification(context, "fajr")
                                Toast.makeText(context, "تم تفعيل أنوار السحر والفجر ✨", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            isEnabled = false
                            qabasPrefs.edit().putBoolean("spiritual_notifications_enabled", false).apply()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ManuscriptGold,
                        checkedTrackColor = ManuscriptGold.copy(alpha = 0.3f),
                        uncheckedThumbColor = AntiqueParchment,
                        uncheckedTrackColor = AntiqueParchment.copy(alpha = 0.5f)
                    )
                )
            }
            
            if (isEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = ManuscriptGold.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, ManuscriptGold.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("السحر 🌌", color = ManuscriptGold, fontFamily = CairoFont, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("03:30 ص", color = OldInk, fontFamily = NotoSansFont, fontSize = 12.sp)
                        }
                    }
                    Surface(
                        color = ManuscriptGold.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, ManuscriptGold.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("الفجر 🌅", color = ManuscriptGold, fontFamily = CairoFont, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("05:00 ص", color = OldInk, fontFamily = NotoSansFont, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TajweedAcademyStagesView(
    surahList: List<QuranSurahItem>,
    onStartTest: (Int) -> Unit
) {
    val totalSurahs = surahList.size
    val masteredCount = surahList.count { it.isMastered }
    val progressPercentage = if (totalSurahs > 0) (masteredCount * 100) / totalSurahs else 0
    val challenges = QuranDataProvider.dailyChallenges

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            SpiritualNotificationSettingsCard()
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "تحديات ربيع القلوب اليومية 🌿",
                color = ManuscriptGold,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                "أتمم مهامك لترتقي درجات وتضاعف حسناتك",
                color = OldInk.copy(alpha = 0.6f),
                fontFamily = NotoSansFont,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(challenges) { challenge ->
                    QuranChallengeCard(challenge = challenge)
                }
            }
        }
        item {
            val featuredSurah = surahList.firstOrNull { it.virtue != null }
            if (featuredSurah != null) {
                Spacer(modifier = Modifier.height(4.dp))
                VirtueHighlightCard(surah = featuredSurah)
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        item {
            // Overall Academy Progress Card
            Card(
                colors = CardDefaults.cardColors(containerColor = AgedPaper),
                border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "مستواك في إتقان التجويد 🏆",
                                color = ManuscriptGold,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "تم إتقان $masteredCount من أصل $totalSurahs سورة",
                                color = OldInk.copy(alpha = 0.6f),
                                fontFamily = CairoFont,
                                fontSize = 11.sp
                            )
                        }
                        Surface(
                            color = ManuscriptGold.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold)
                        ) {
                            Text(
                                "$progressPercentage%",
                                color = ManuscriptGold,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progressPercentage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = ManuscriptGold,
                        trackColor = AntiqueParchment
                    )
                }
            }
        }

        items(surahList, key = { it.id }) { surah ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (surah.isLocked) AntiqueParchment.copy(alpha = 0.5f) else AgedPaper
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (surah.isMastered) ManuscriptGold else if (surah.isLocked) ManuscriptGold.copy(alpha = 0.1f) else ManuscriptGold.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = when {
                            surah.isMastered -> ManuscriptGold
                            surah.isLocked -> AntiqueParchment
                            else -> AntiqueParchment
                        },
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.3f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (surah.isLocked) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = OldInk.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
                            } else if (surah.isMastered) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Icon(Icons.Default.Mic, contentDescription = null, tint = ManuscriptGold, modifier = Modifier.size(22.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "سورة ${surah.name}",
                                color = if (surah.isLocked) OldInk.copy(alpha = 0.5f) else OldInk,
                                fontFamily = AmiriFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            if (surah.isLocked) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = AntiqueParchment,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("🔒 مقفلة", color = ManuscriptGold, fontFamily = NotoSansFont, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            } else if (surah.isMastered) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0xFF10B981).copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("مُتقنة ✓", color = Color(0xFF065F46), fontFamily = NotoSansFont, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                        Text("التركيز: ${surah.tajweedFocus}", color = OldInk.copy(alpha = 0.6f), fontFamily = NotoSansFont, fontSize = 12.sp)
                        if (surah.isLocked) {
                            Text("شرط الفتح: إتقان السورة السابقة", color = ManuscriptGold, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else if (surah.isMastered) {
                            Text("تم الإتقان بنجاح 🌟", color = ManuscriptGold, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        } else {
                            Text("بانتظار الترتيل والتقييم 🎙️", color = OldInk.copy(alpha = 0.6f), fontFamily = NotoSansFont, fontSize = 11.sp)
                        }
                    }

                    Button(
                        onClick = { onStartTest(surah.id) },
                        enabled = !surah.isLocked,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ManuscriptGold,
                            disabledContainerColor = AntiqueParchment
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = when {
                                surah.isLocked -> "مقفلة 🔒"
                                surah.isMastered -> "مراجعة 🎙️"
                                else -> "بدء الترتيل 🎙️"
                            },
                            color = if (surah.isLocked) OldInk.copy(alpha = 0.3f) else Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "أنوار قرآنية ✨",
                color = ManuscriptGold,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                "تأمل في فضل كلام الله لتزداد عشقاً",
                color = OldInk.copy(alpha = 0.6f),
                fontFamily = NotoSansFont,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        val inspirations = listOf(
            "خياركم من تعلم القرآن وعلمه 📖",
            "يقال لصاحب القرآن اقرأ وارتقِ ورتل 🎤",
            "القرآن نور في القلب وضياء في القبر 💡",
            "من قرأ حرفاً من كتاب الله فله به حسنة ➕"
        )

        items(inspirations) { text ->
            Card(
                colors = CardDefaults.cardColors(containerColor = ManuscriptGold.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text,
                    modifier = Modifier.padding(12.dp),
                    color = OldInk,
                    fontFamily = CairoFont,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiVoiceRecitationTestView(
    activeSurahName: String,
    isRecording: Boolean,
    isAnalyzing: Boolean,
    testScore: Int?,
    feedbackList: List<Pair<String, Boolean>>,
    isPlayingAudio: Boolean = false,
    hasRecordedAudio: Boolean = false,
    onPlayRecordedAudio: () -> Unit = {},
    onStartRecording: () -> Unit,
    onStopRecordingAndAnalyze: () -> Unit
) {
    var highlightedWordIndex by remember { mutableIntStateOf(0) }

    val infiniteTransition = rememberInfiniteTransition(label = "tarteelMicPulse")
    val micPulseRadius by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micPulseRadius"
    )

    val wave1 by infiniteTransition.animateFloat(initialValue = 12f, targetValue = 38f, animationSpec = infiniteRepeatable(tween(400), RepeatMode.Reverse), label = "w1")
    val wave2 by infiniteTransition.animateFloat(initialValue = 28f, targetValue = 10f, animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse), label = "w2")
    val wave3 by infiniteTransition.animateFloat(initialValue = 15f, targetValue = 42f, animationSpec = infiniteRepeatable(tween(350), RepeatMode.Reverse), label = "w3")
    val wave4 by infiniteTransition.animateFloat(initialValue = 35f, targetValue = 18f, animationSpec = infiniteRepeatable(tween(480), RepeatMode.Reverse), label = "w4")

    LaunchedEffect(isRecording) {
        if (isRecording) {
            highlightedWordIndex = 0
            while (isRecording) {
                delay(800)
                highlightedWordIndex = (highlightedWordIndex + 1) % 12
            }
        }
    }

    val sampleVerseWords = remember(activeSurahName) {
        if (activeSurahName.contains("الإخلاص")) {
            listOf("قُلْ", "هُوَ", "اللَّهُ", "أَحَدٌ", "﴿١﴾", "اللَّهُ", "الصَّمَدُ", "﴿٢﴾", "لَمْ", "يَلِدْ", "وَلَمْ", "يُولَدْ", "﴿٣﴾")
        } else if (activeSurahName.contains("الفلق")) {
            listOf("قُلْ", "أَعُوذُ", "بِرَبِّ", "الْفَلَقِ", "﴿١﴾", "مِن", "شَرِّ", "مَا", "خَلَقَ", "﴿٢﴾", "وَمِن", "شَرِّ", "غَاسِقٍ", "﴿٣﴾")
        } else {
            listOf("الْحَمْدُ", "لِلَّهِ", "رَبِّ", "الْعَالَمِينَ", "﴿١﴾", "الرَّحْمَٰنِ", "الرَّحِيمِ", "﴿٢﴾", "مَالِكِ", "يَوْمِ", "الدِّينِ", "﴿٣﴾")
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AgedPaper),
                border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = ManuscriptGold, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "مختبر الفحص الصوتي الذكي (نظام ترتيل 🎙️)",
                            color = ManuscriptGold,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "اقرأ الكلمات التالية بصوتك؛ سيقوم المحرك بمتابعة قراءتك كلمة بكلمة واكتشاف الأحكام التجويدية تلقائياً.",
                        color = OldInk.copy(alpha = 0.6f),
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AgedPaper),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.5.dp,
                    brush = if (isRecording) Brush.horizontalGradient(listOf(Color(0xFFEF4444), ManuscriptGold, Color(0xFFEF4444))) else Brush.horizontalGradient(listOf(ManuscriptGold, ManuscriptGold.copy(alpha = 0.5f)))
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "﴿ سورة $activeSurahName ﴾",
                        color = ManuscriptGold,
                        fontFamily = AmiriFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.Center,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        sampleVerseWords.forEachIndexed { idx, word ->
                            val isHighlighted = isRecording && idx <= highlightedWordIndex
                            val isCurrentWord = isRecording && idx == highlightedWordIndex

                            Surface(
                                color = when {
                                    isCurrentWord -> ManuscriptGold.copy(alpha = 0.25f)
                                    isHighlighted -> Color(0xFF10B981).copy(alpha = 0.15f)
                                    testScore != null -> Color(0xFF10B981).copy(alpha = 0.1f)
                                    else -> Color.Transparent
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = if (isCurrentWord) androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold) else null,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = word,
                                    fontFamily = AmiriFont,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        isCurrentWord -> ManuscriptGold
                                        isHighlighted -> Color(0xFF065F46)
                                        testScore != null -> Color(0xFF065F46)
                                        else -> OldInk
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AgedPaper),
                border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isRecording) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(50.dp)
                        ) {
                            val waves = listOf(wave1, wave2, wave3, wave4, wave2, wave1, wave3, wave4)
                            waves.forEach { h ->
                                Box(
                                    modifier = Modifier
                                        .width(6.dp)
                                        .height(h.dp)
                                        .background(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(Color(0xFFEF4444), ManuscriptGold)
                                            ),
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Box(contentAlignment = Alignment.Center) {
                        if (isRecording) {
                            Box(
                                modifier = Modifier
                                    .size((100 * micPulseRadius).dp)
                                    .background(Color(0xFFEF4444).copy(alpha = 0.1f), CircleShape)
                            )
                        }

                        Surface(
                            color = if (isRecording) Color(0xFFEF4444) else ManuscriptGold,
                            shape = CircleShape,
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .size(84.dp)
                                .clickable {
                                    if (isRecording) onStopRecordingAndAnalyze() else onStartRecording()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = "Tarteel Microphone",
                                    tint = Color.White,
                                    modifier = Modifier.size(42.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = when {
                            isRecording -> "جاري تسجيل تلاوتك حيّاً... اضغط للإيقاف والحفظ"
                            isAnalyzing -> "جاري معالجة وحفظ الملف الصوتي... ⏳"
                            else -> "اضغط زر الميكروفون وابدأ القراءة الآن 🎙️"
                        },
                        color = if (isRecording) Color(0xFFEF4444) else ManuscriptGold,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    if (hasRecordedAudio && !isRecording && !isAnalyzing) {
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = onPlayRecordedAudio,
                            border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ManuscriptGold)
                        ) {
                            Icon(
                                imageVector = if (isPlayingAudio) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = ManuscriptGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isPlayingAudio) "إيقاف الاستماع للتلاوة المسجلة ⏹️" else "الاستماع إلى تسجيلك الصوتي بصوتك 🎧",
                                color = ManuscriptGold,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        if (testScore != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text("نتيجة الترتيل ومطابقة النطق 🌟", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("تمت مقارنة الصوت برواية حفص عن عاصم", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                            }
                            Surface(
                                color = GoldPrimary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary)
                            ) {
                                Text("$testScore%", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color(0xFF1E293B))
                        Spacer(modifier = Modifier.height(14.dp))

                        feedbackList.forEach { item ->
                            val rule = item.first
                            val passed = item.second
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (passed) Color(0xFF10B981) else Color(0xFFF5D76E),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(rule, color = TextPrimary, fontFamily = NotoSansFont, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Text(if (passed) "ممتاز" else "ينصح بالمراجعة", color = if (passed) Color(0xFF10B981) else Color(0xFFF5D76E), fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        
                        var showCertificateDialog by remember { mutableStateOf(false) }
                        val context = LocalContext.current

                        Button(
                            onClick = { showCertificateDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CardMembership, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("إصدار شهادة الإتقان ومشاركتها كـ Reels 📜🎬", color = DeepSlate, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        if (showCertificateDialog) {
                            TajweedCertificateDialog(
                                surahName = activeSurahName,
                                score = testScore ?: 95,
                                onDismiss = { showCertificateDialog = false },
                                onExportReel = {
                                    Toast.makeText(context, "جاري تحضير فيديو الشهادة كـ Reel بنسبة 9:16 للنشر الفوري! 🎬✨", Toast.LENGTH_LONG).show()
                                    showCertificateDialog = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TajweedRulesEncyclopediaView(
    rules: List<TajweedRuleItem>,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("الكل") }

    val categories = remember(rules) {
        listOf("الكل") + rules.map { it.category }.distinct()
    }

    val filteredRules = remember(rules, searchQuery, selectedCategory) {
        rules.filter { rule ->
            val matchesCategory = selectedCategory == "الكل" || rule.category == selectedCategory
            val matchesSearch = searchQuery.isBlank() || 
                rule.title.contains(searchQuery, ignoreCase = true) ||
                rule.description.contains(searchQuery, ignoreCase = true) ||
                rule.exampleVerse.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("ابحث في أحكام التجويد، الأمثلة، والمدود...", color = OldInk.copy(alpha = 0.4f), fontFamily = NotoSansFont, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ManuscriptGold) },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = ManuscriptGold.copy(alpha = 0.2f),
                focusedBorderColor = ManuscriptGold,
                unfocusedContainerColor = AgedPaper,
                focusedContainerColor = AgedPaper,
                focusedTextColor = OldInk,
                unfocusedTextColor = OldInk
            ),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )

        // Categories Chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { category ->
                val isSelected = selectedCategory == category
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedCategory = category },
                    label = { Text(category, fontFamily = NotoSansFont, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ManuscriptGold,
                        selectedLabelColor = Color.White,
                        containerColor = AgedPaper,
                        labelColor = OldInk
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = ManuscriptGold.copy(alpha = 0.2f),
                        selectedBorderColor = ManuscriptGold
                    )
                )
            }
        }

        // Rules List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredRules) { rule ->
                TajweedRuleCard(rule = rule)
            }
        }
    }
}

// ============ المصحف الذهبي Hub — الأقسام الجديدة ============

// بطاقة «متابعة القراءة» في رأس قسم المصحف
@Composable
fun GoldenContinueReadingCard(
    surah: QuranSurahItem,
    selectedReciter: String,
    onResume: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgedPaper, RoundedCornerShape(14.dp))
            .border(1.dp, ManuscriptGold.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(ManuscriptGold.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("📖", fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "متابعة القراءة",
                color = ManuscriptGold,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text(
                "سورة ${surah.name} • ${surah.type} • ${surah.versesCount} آية • ${selectedReciter}",
                color = OldInk.copy(alpha = 0.6f),
                fontFamily = NotoSansFont,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onResume) {
            Text("واصل ▶", color = ManuscriptGold, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
            Text("✕", color = OldInk.copy(alpha = 0.5f), fontSize = 12.sp)
        }
    }
}

// قسم القراء والروايات
@Composable
fun GoldenQuranRecitersView(
    selectedReciter: String,
    onSelectReciter: (String) -> Unit,
    selectedRiwaya: String,
    onSelectRiwaya: (String) -> Unit
) {
    val context = LocalContext.current
    val qabasPrefs = remember { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }
    val reciters = listOf(
        "الشيخ محمود خليل الحصري" to "شيخ عموم المقارئ المصرية سابقاً — مرتّل ومجوّد",
        "الشيخ محمد صديق المنشاوي" to "تلاوة مرتّلة بأداء خاشع رفيع",
        "الشيخ عبد الباسط عبد الصمد" to "مرتّل ومجوّد من أعلام القراءة في القرن العشرين",
        "الشيخ مصطفى إسماعيل" to "تلاوة مجوّدة بنَفَسٍ وأداء فريد",
        "الشيخ محمود علي البنا" to "مرتّل مصري مشهور بإيقاعه المؤثر",
        "الشيخ محمد رفعت" to "من أشهر قرّاء جيله بحلاوة صوت نادرة",
        "الشيخ ماهر المعيقلي" to "إمام وخطيب المسجد الحرام — مرتّل",
        "الشيخ مشاري راشد العفاسي" to "قارئ كويتي معروف بمرتّل المؤثرات",
        "الشيخ عبد الرحمن السديس" to "إمام وخطيب المسجد الحرام — مرتّل",
        "الشيخ سعود الشريم" to "إمام وخطيب المسجد الحرام — مرتّل",
        "الشيخ ياسر الدوسري" to "إمام المسجد الحرام — مرتّل",
        "الشيخ إسلام صبحي" to "قارئ مصري شاب صاحب تلاوات منتشرة"
    )
    val riwayat = listOf(
        "حفص عن عاصم",
        "ورش عن نافع",
        "قالون عن نافع",
        "شعبة عن عاصم",
        "الدوري عن أبي عمرو",
        "السوسي عن أبي عمرو",
        "خلف عن حمزة",
        "خلاد عن حمزة",
        "أبو جعفر (قراءة)",
        "يعقوب الحضرمي (قراءة)"
    )

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                "اختر قارئك 🎙️",
                color = ManuscriptGold,
                fontFamily = AmiriFont,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }
        item {
            Text(
                "يُحفظ اختيارك محلياً ويُستخدم في واجهة القارئ. التلاوة الصوتية (الاستماع) عبر الإنترنت قيد التجهيز وستربط بسجلات القرّاء الموثوقة فقط.",
                color = OldInk.copy(alpha = 0.6f),
                fontFamily = NotoSansFont,
                fontSize = 12.sp
            )
        }
        item { Text("القرّاء", color = ManuscriptGold, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        items(reciters) { (name, bio) ->
            val isSelected = selectedReciter == name
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) ManuscriptGold.copy(alpha = 0.1f) else AgedPaper)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) ManuscriptGold else ManuscriptGold.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectReciter(name); qabasPrefs.edit().putString("selected_reciter", name).apply() }
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isSelected) "⭐ " else "🎙️ ", fontSize = 14.sp)
                    Text(
                        name,
                        color = if (isSelected) ManuscriptGold else OldInk,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) Text("✓", color = ManuscriptGold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(bio, color = OldInk.copy(alpha = 0.6f), fontFamily = NotoSansFont, fontSize = 11.sp)
            }
        }
        item { Spacer(modifier = Modifier.height(4.dp)) }
        item { Text("الروايات", color = ManuscriptGold, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        item {
            Text(
                "الرواية تحدد أسلوب النطق في التلاوة؛ تُحفظ مع اختيارك وتُستخدم عند توفر التلاوة الصوتية.",
                color = OldInk.copy(alpha = 0.6f),
                fontFamily = NotoSansFont,
                fontSize = 11.sp
            )
        }
        items(riwayat) { riwaya ->
            val isSelected = selectedRiwaya == riwaya
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) ManuscriptGold.copy(alpha = 0.1f) else AgedPaper)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) ManuscriptGold else ManuscriptGold.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelectRiwaya(riwaya); qabasPrefs.edit().putString("selected_riwaya", riwaya).apply() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    riwaya,
                    color = if (isSelected) ManuscriptGold else OldInk,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) Text("✓", color = ManuscriptGold, fontSize = 14.sp)
            }
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

// قسم التفسير والمصادر
@Composable
fun GoldenTafsirSourcesView(
    onBackToMushaf: () -> Unit
) {
    val sources = listOf(
        "التفسير الميسر" to "مجمع الملك فهد لطباعة المصحف الشريف",
        "تفسير ابن كثير" to "عماد الدين أبو الفداء إسماعيل بن كثير",
        "جامع البيان في تأويل القرآن" to "محمد بن جرير الطبري",
        "تيسير الكريم الرحمن" to "عبد الرحمن بن ناصر السعدي",
        "تفسير الجلالين" to "جلال الدين المحلي وجلال الدين السيوطي",
        "الجامع لأحكام القرآن" to "أبو عبد الله محمد بن أحمد القرطبي",
        "الوسيط في تفسير القرآن المجيد" to "علي الصابوني",
        "أيسر التفاسير" to "أبو بكر جابر الجزائري",
        "التفسير الوسيط" to "محمد سيد طنطاوي"
    )

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                "التفسير والمصادر 📚",
                color = ManuscriptGold,
                fontFamily = AmiriFont,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }
        item {
            Text(
                "مصادر تفسيرية معتمدة. التفسير الميسر مدمج ومتاح الآن في المصحف 📖، وبقية المصادر قيد التجهيز — لن يُعرض أي تفسير غير موثوق المصدر.",
                color = OldInk.copy(alpha = 0.6f),
                fontFamily = NotoSansFont,
                fontSize = 12.sp
            )
        }
        items(sources) { (title, author) ->
            val isMuyassarAvailable = title.contains("الميسر")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AgedPaper, RoundedCornerShape(12.dp))
                    .border(1.dp, ManuscriptGold.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📖 ", fontSize = 14.sp)
                    Text(
                        title,
                        color = OldInk,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(author, color = OldInk.copy(alpha = 0.6f), fontFamily = NotoSansFont, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    if (isMuyassarAvailable) "مدمج ✓ متاح الآن في المصحف" else "النص الكامل قيد التجهيز ⏳",
                    color = if (isMuyassarAvailable) Color(0xFF065F46) else ManuscriptGold,
                    fontFamily = NotoSansFont,
                    fontSize = 11.sp
                )
            }
        }
        item {
            TextButton(onClick = onBackToMushaf, modifier = Modifier.fillMaxWidth()) {
                Text("↵ العودة إلى المصحف الشريف", color = ManuscriptGold, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private data class AdhkarItem(
    val title: String,
    val text: String,
    val count: Int,
    val category: String
)

// قسم الأذكار (نصوص معتمدة من الأذكار الصحيحة)
@Composable
fun GoldenAdhkarView(onOpenFullAzkar: (() -> Unit)? = null) {
    val morningAdhkar = listOf(
        AdhkarItem("سيد الاستغفار", "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي، فَاغْفِرْ لِي، فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ", 1, "الصباح"),
        AdhkarItem("الذكر الجامع", "أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ", 1, "الصباح"),
        AdhkarItem("آية الكرسي", "اللَّهُ لَا إِلَهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ…", 1, "الصباح"),
        AdhkarItem("سورة الإخلاص والمعوذتان", "قُلْ هُوَ اللَّهُ أَحَدٌ • قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ • قُلْ أَعُوذُ بِرَبِّ النَّاسِ", 3, "الصباح"),
        AdhkarItem("دعاء الصباح", "اللَّهُمَّ بِكَ أَصْبَحْنَا، وَبِكَ أَمْسَيْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ النُّشُورُ", 1, "الصباح")
    )
    val eveningAdhkar = listOf(
        AdhkarItem("الذكر الجامع", "أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ", 1, "المساء"),
        AdhkarItem("آية الكرسي", "اللَّهُ لَا إِلَهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ…", 1, "المساء"),
        AdhkarItem("سورة الإخلاص والمعوذتان", "قُلْ هُوَ اللَّهُ أَحَدٌ • قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ • قُلْ أَعُوذُ بِرَبِّ النَّاسِ", 3, "المساء"),
        AdhkarItem("دعاء المساء", "اللَّهُمَّ بِكَ أَمْسَيْنَا، وَبِكَ أَصْبَحْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ الْمَصِيرُ", 1, "المساء")
    )

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (onOpenFullAzkar != null) {
            item {
                Surface(
                    onClick = onOpenFullAzkar,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = ManuscriptGold,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("فتح قسم الأذكار الكامل التفاعلي ✨", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("أذكار الصباح والمساء مفصولة بألوان سيرة الأكابر والعدادات", color = Color.White.copy(alpha = 0.9f), fontFamily = NotoSansFont, fontSize = 11.sp)
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
        item {
            Text(
                "الأذكار 🤲",
                color = ManuscriptGold,
                fontFamily = AmiriFont,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }
        item {
            Text(
                "أذكار ثابتة من الأذكار الصحيحة (الصباح والمساء). العداد يعمل يدوياً للحفظ والتدبّر، والتسبيح الإلكتروني قيد التجهيز.",
                color = OldInk.copy(alpha = 0.6f),
                fontFamily = NotoSansFont,
                fontSize = 12.sp
            )
        }
        item { Spacer(modifier = Modifier.height(2.dp)) }
        item { Text("أذكار الصباح ☀️", color = ManuscriptGold, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        items(morningAdhkar) { dhikr ->
            AdhkarCard(dhikr = dhikr)
        }
        item { Spacer(modifier = Modifier.height(4.dp)) }
        item { Text("أذكار المساء 🌙", color = ManuscriptGold, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        items(eveningAdhkar) { dhikr ->
            AdhkarCard(dhikr = dhikr)
        }
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "تنبيه: الأذكار منقولة من مصنفات الأذكار المعتمدة دون تصرف في اللفظ. النصوص المقتبسة الهامة تُعرض كاملة في نسخة قادمة.",
                color = OldInk.copy(alpha = 0.5f),
                fontFamily = NotoSansFont,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun AdhkarCard(dhikr: AdhkarItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgedPaper, RoundedCornerShape(12.dp))
            .border(1.dp, ManuscriptGold.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${dhikr.title} • ${dhikr.category}",
                color = ManuscriptGold,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                dhikr.text,
                color = OldInk,
                fontFamily = AmiriFont,
                fontSize = 14.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(ManuscriptGold.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("×${dhikr.count}", color = ManuscriptGold, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
fun MemorizationSystemView(
    surahList: List<QuranSurahItem>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    
    val memorizationRecords by db.memorizationDao().getAllRecords().collectAsState(initial = emptyList())
    val activeChallenges by db.quranChallengeDao().getActiveChallenges().collectAsState(initial = emptyList())
    val totalProgress by db.memorizationDao().getTotalProgress().collectAsState(initial = 0f)

    var showLogDialog by remember { mutableStateOf(false) }
    var selectedSurahForLog by remember { mutableStateOf<QuranSurahItem?>(null) }
    var verseLogValue by remember { mutableStateOf("") }
    var showSurahPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Initialize a daily challenge if none exists
        scope.launch {
            val active = db.quranChallengeDao().getActiveChallengesOnce()
            if (active.isEmpty()) {
                db.quranChallengeDao().insertChallenge(
                    QuranChallengeRecord(
                        id = "daily_read_5",
                        title = "ورد النور اليومي",
                        description = "اقرأ 5 آيات من سورة الملك اليوم",
                        goalValue = 5,
                        currentValue = 2,
                        type = "READING",
                        hasanatReward = 1000
                    )
                )
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            MemorizationStatsHeader(
                totalProgress = totalProgress ?: 0f,
                streakDays = 5 // Placeholder for now
            )
        }

        item {
            Text(
                "🎯 التحديات النشطة",
                color = ManuscriptGold,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        if (activeChallenges.isEmpty()) {
            item {
                DailyChallengeCard(
                    challenge = QuranChallengeRecord(
                        id = "daily_read",
                        title = "ورد القراءة اليومي",
                        description = "اقرأ صفحتين من المصحف اليوم لتنال بركة النور",
                        goalValue = 2,
                        currentValue = 1,
                        type = "READING",
                        hasanatReward = 500
                    )
                )
            }
        } else {
            items(activeChallenges) { challenge ->
                DailyChallengeCard(challenge = challenge)
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "🕋 مسيرة الحفظ الخاص بك",
                    color = ManuscriptGold,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                TextButton(onClick = { showLogDialog = true }) {
                    Text("+ إضافة تقدّم", color = GoldSecondary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (memorizationRecords.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AgedPaper.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.AutoStories, contentDescription = null, tint = ManuscriptGold.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "ابدأ رحلة الحفظ اليوم. اختر سورة وتابع تقدمك آية بآية.",
                            color = OldInk.copy(alpha = 0.6f),
                            fontFamily = CairoFont,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(memorizationRecords) { record ->
                MemorizationSurahCard(record = record)
            }
        }
    }

    if (showLogDialog) {
        AlertDialog(
            onDismissRequest = { showLogDialog = false },
            title = { Text("تسجيل تقدم الحفظ", fontFamily = CairoFont, color = GoldPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("اختر السورة:", fontFamily = CairoFont, fontSize = 12.sp, color = TextSecondary)
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable { showSurahPicker = true }
                            .padding(12.dp)
                    ) {
                        Text(selectedSurahForLog?.name ?: "اختر سورة من القائمة...", color = GoldPrimary, fontFamily = CairoFont)
                    }
                    
                    if (showSurahPicker) {
                        Card(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                        ) {
                            LazyColumn {
                                items(surahList) { surah ->
                                    Text(
                                        surah.name,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { 
                                                selectedSurahForLog = surah
                                                showSurahPicker = false 
                                            }
                                            .padding(12.dp),
                                        color = Color.White,
                                        fontFamily = CairoFont
                                    )
                                }
                            }
                        }
                    }
                    
                    OutlinedTextField(
                        value = verseLogValue,
                        onValueChange = { verseLogValue = it },
                        label = { Text("رقم آخر آية حفظتها", fontFamily = CairoFont) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GoldPrimary),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val surah = selectedSurahForLog
                        val verse = verseLogValue.toIntOrNull()
                        if (surah != null && verse != null) {
                            scope.launch {
                                db.memorizationDao().insertRecord(
                                    MemorizationRecord(
                                        surahId = surah.id,
                                        surahName = surah.name,
                                        lastVerseMemorized = verse,
                                        totalVerses = surah.versesCount,
                                        progressPercent = verse.toFloat() / surah.versesCount,
                                        isCompleted = verse >= surah.versesCount
                                    )
                                )
                                showLogDialog = false
                                Toast.makeText(context, "تم تسجيل التقدم في سورة ${surah.name} 🎉", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Text("حفظ التقدم", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogDialog = false }) {
                    Text("إلغاء", color = TextSecondary, fontFamily = CairoFont)
                }
            },
            containerColor = Color(0xFF151B2B)
        )
    }
}

@Composable
fun MemorizationStatsHeader(totalProgress: Float, streakDays: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151B2B)),
        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(60.dp)) {
                CircularProgressIndicator(
                    progress = { totalProgress },
                    color = GoldPrimary,
                    trackColor = GoldPrimary.copy(alpha = 0.1f),
                    strokeWidth = 6.dp,
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    "${(totalProgress * 100).toInt()}%",
                    color = GoldPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("إجمالي حفظ القرآن", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Whatshot, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("سلسلة متصلة: $streakDays أيام", color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun DailyChallengeCard(challenge: QuranChallengeRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AgedPaper),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = ManuscriptGold.copy(alpha = 0.1f),
                shape = CircleShape,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = ManuscriptGold, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(challenge.title, color = OldInk, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(challenge.description, color = OldInk.copy(alpha = 0.6f), fontFamily = CairoFont, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { challenge.currentValue.toFloat() / challenge.goalValue },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = ManuscriptGold,
                    trackColor = ManuscriptGold.copy(alpha = 0.1f)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${challenge.hasanatReward}", color = Color(0xFF065F46), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("حسنة", color = OldInk.copy(alpha = 0.5f), fontSize = 9.sp, fontFamily = CairoFont)
            }
        }
    }
}

@Composable
fun MemorizationSurahCard(record: MemorizationRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AgedPaper.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "#${record.surahId}",
                color = ManuscriptGold,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(record.surahName, color = OldInk, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("وصلت إلى الآية: ${record.lastVerseMemorized} من ${record.totalVerses}", color = OldInk.copy(alpha = 0.6f), fontFamily = CairoFont, fontSize = 11.sp)
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
                CircularProgressIndicator(
                    progress = { record.progressPercent },
                    color = ManuscriptGold,
                    trackColor = ManuscriptGold.copy(alpha = 0.1f),
                    strokeWidth = 4.dp
                )
                Text("${(record.progressPercent * 100).toInt()}%", fontSize = 10.sp, color = OldInk, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun GoldenTadabburJournalView(onBack: () -> Unit, isSakinaMode: Boolean) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val reflections = db.tadabburDao().getAllReflections().collectAsState(initial = emptyList())
    
    val paperColor = if (isSakinaMode) Color(0xFFFDF4E3) else AgedPaper
    val textColor = if (isSakinaMode) Color(0xFF4A3728) else OldInk
    val goldColor = if (isSakinaMode) Color(0xFFB8860B) else ManuscriptGold

    Column(modifier = Modifier.fillMaxSize().background(paperColor).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = textColor)
            }
            Text("سجل النور والتدبر ✨", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = textColor)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (reflections.value.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.HistoryEdu, contentDescription = null, tint = goldColor.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("لا توجد خواطر مسجلة بعد.", color = textColor.copy(alpha = 0.5f), fontFamily = CairoFont)
                    Text("ابدأ بالتدبر في آيات الله ودون ما يفتح الله به عليك.", color = textColor.copy(alpha = 0.4f), fontSize = 12.sp, fontFamily = CairoFont)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(reflections.value) { record ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = paperColor),
                        border = androidx.compose.foundation.BorderStroke(1.dp, goldColor.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "﴿${record.surahName}: ${record.verseNumber}﴾",
                                color = goldColor,
                                fontFamily = AmiriFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                record.reflectionText,
                                color = textColor,
                                fontFamily = NotoSansFont,
                                fontSize = 14.sp,
                                lineHeight = 22.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault()).format(java.util.Date(record.createdAt)),
                                color = textColor.copy(alpha = 0.4f),
                                fontSize = 10.sp,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuranOpeningAnimation(
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val qabasPrefs = remember { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }
    val lastReadSurahId = qabasPrefs.getInt("last_read_surah", 1)
    val lastSurah = QuranDataProvider.surahs.find { it.id == lastReadSurahId } ?: QuranDataProvider.surahs[0]
    
    val rotationY = remember { Animatable(0f) }
    val pageFlip = remember { Animatable(0f) }
    var isOpened by remember { mutableStateOf(false) }
    var isSettled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // المرحلة الأولى: فتح غلاف المصحف
        rotationY.animateTo(
            targetValue = -180f,
            animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing)
        )
        isOpened = true
        
        // المرحلة الثانية: تقليب سريع للصفحات
        repeat(6) {
            pageFlip.snapTo(0f)
            pageFlip.animateTo(
                targetValue = -180f,
                animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing)
            )
        }
        
        // المرحلة الثالثة: الاستقرار على الصفحة الأخيرة
        isSettled = true
        delay(1200)
        onFinish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19)),
        contentAlignment = Alignment.Center
    ) {
        // تأثير هالة نورانية خلف المصحف
        Box(
            modifier = Modifier
                .size(300.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(GoldPrimary.copy(alpha = 0.15f), Color.Transparent)
                    )
                )
        )

        // هيكل المصحف
        Box(
            modifier = Modifier.size(width = 260.dp, height = 380.dp)
        ) {
            // الغلاف الخلفي (يظهر عند الفتح)
            Card(
                modifier = Modifier.fillMaxSize(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B4332)),
                border = androidx.compose.foundation.BorderStroke(2.dp, GoldPrimary),
                shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {}

            // الأوراق الداخلية (كتلة الصفحات)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 6.dp, top = 10.dp, bottom = 10.dp, end = 6.dp)
                    .background(AgedPaper, RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
                    .border(0.5.dp, ManuscriptGold.copy(alpha = 0.2f), RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
            ) {
                if (isSettled) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = ManuscriptGold.copy(alpha = 0.4f), modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "سورة ${lastSurah.name}",
                            fontFamily = AmiriFont,
                            fontSize = 28.sp,
                            color = OldInk,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "الجزء ${lastSurah.juz} | آياتها ${lastSurah.versesCount}",
                            fontFamily = CairoFont,
                            fontSize = 14.sp,
                            color = ManuscriptGold
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        CircularProgressIndicator(
                            color = GoldPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    // خطوط تمثل حواف الصفحات
                    Column(modifier = Modifier.fillMaxSize().padding(end = 4.dp), verticalArrangement = Arrangement.Center) {
                        repeat(10) {
                            HorizontalDivider(color = ManuscriptGold.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 1.dp))
                        }
                    }
                }
            }

            // الصفحة التي يتم تقليبها حالياً
            if (isOpened && !isSettled) {
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationX = 0f
                            this.rotationY = pageFlip.value
                            cameraDistance = 15f * density.density
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        },
                    colors = CardDefaults.cardColors(containerColor = AgedPaper),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, ManuscriptGold.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp)
                ) {
                    Box(Modifier.fillMaxSize()) {
                         // زخرفة بسيطة على الصفحة الطائرة
                         Icon(
                             Icons.Default.AutoAwesome,
                             contentDescription = null,
                             tint = ManuscriptGold.copy(alpha = 0.1f),
                             modifier = Modifier.align(Alignment.Center).size(100.dp)
                         )
                    }
                }
            }

            // الغلاف الأمامي
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationX = 0f
                        this.rotationY = rotationY.value
                        cameraDistance = 15f * density.density
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B4332)),
                border = androidx.compose.foundation.BorderStroke(2.dp, GoldPrimary),
                shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                if (rotationY.value > -90f) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // نقش إسلامي تقليدي في المنتصف
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .border(1.dp, GoldPrimary.copy(alpha = 0.3f), CircleShape)
                                .padding(8.dp)
                                .border(2.dp, GoldPrimary.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "القرآن الكريم",
                                    color = GoldPrimary,
                                    fontFamily = AmiriFont,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
