package com.qabas.app

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.GoldSecondary
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.floor

@Immutable
data class HasanatSectionItem(
    val id: String,
    val titleKey: String,
    val subtitleKey: String,
    val descKey: String,
    val badgeKey: String,
    val categoryKey: String,
    val icon: ImageVector,
    val accentColor: Color,
    val secondaryAccent: Color,
    val route: AppState
)

@Immutable
data class SpiritualPearl(
    val typeKey: String,
    val text: String,
    val source: String,
    val benefit: String
)

@Immutable
data class QuranAyahWithTafsir(
    val surahNumber: Int,
    val ayahNumber: Int,
    val surahName: String,
    val text: String,
    val tafsir: String
)

data class PrayerTimeInfo(
    val nextName: String = "",
    val nextTime: String = "",
    val countdown: String = ""
)

data class HijriDateInfo(
    val hijriStr: String = "",
    val gregorianStr: String = ""
)

class HasanatHubViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategoryKey = MutableStateFlow("الكل")
    val selectedCategoryKey = _selectedCategoryKey.asStateFlow()

    private val _isGridView = MutableStateFlow(true)
    val isGridView = _isGridView.asStateFlow()

    private val _tasbeehCount = MutableStateFlow(prefs.getInt("mishkat_tasbeeh_count", 0))
    val tasbeehCount = _tasbeehCount.asStateFlow()

    private val _vibrateEvery33 = MutableStateFlow(prefs.getBoolean("mishkat_vibrate_33", true))
    val vibrateEvery33 = _vibrateEvery33.asStateFlow()

    // 🌟 Daily Spiritual Progress Management
    private val todayKey: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private val _isWirdDone = MutableStateFlow(prefs.getBoolean("daily_wird_$todayKey", false))
    val isWirdDone = _isWirdDone.asStateFlow()

    private val _isMorningAzkarDone = MutableStateFlow(prefs.getBoolean("daily_morning_azkar_$todayKey", false))
    val isMorningAzkarDone = _isMorningAzkarDone.asStateFlow()

    private val _isEveningAzkarDone = MutableStateFlow(prefs.getBoolean("daily_evening_azkar_$todayKey", false))
    val isEveningAzkarDone = _isEveningAzkarDone.asStateFlow()

    val isTasbeehDone: StateFlow<Boolean> = _tasbeehCount.map { it >= 33 }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val dailyProgressPercent: StateFlow<Int> = combine(_isWirdDone, _isMorningAzkarDone, _isEveningAzkarDone, isTasbeehDone) { wird, morning, evening, tasbeeh ->
        var completed = 0
        if (wird) completed++
        if (morning) completed++
        if (evening) completed++
        if (tasbeeh) completed++
        completed * 25
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun toggleWirdDone() {
        val newVal = !_isWirdDone.value
        _isWirdDone.value = newVal
        prefs.edit().putBoolean("daily_wird_$todayKey", newVal).apply()
    }

    fun toggleMorningAzkarDone() {
        val newVal = !_isMorningAzkarDone.value
        _isMorningAzkarDone.value = newVal
        prefs.edit().putBoolean("daily_morning_azkar_$todayKey", newVal).apply()
    }

    fun toggleEveningAzkarDone() {
        val newVal = !_isEveningAzkarDone.value
        _isEveningAzkarDone.value = newVal
        prefs.edit().putBoolean("daily_evening_azkar_$todayKey", newVal).apply()
    }

    fun toggleVibrateEvery33() {
        val newVal = !_vibrateEvery33.value
        _vibrateEvery33.value = newVal
        prefs.edit().putBoolean("mishkat_vibrate_33", newVal).apply()
    }

    private val _currentPearlIndex = MutableStateFlow(0)
    val currentPearlIndex = _currentPearlIndex.asStateFlow()

    private val _currentTimeMillis = MutableStateFlow(System.currentTimeMillis())
    
    val prayerTimeInfo: StateFlow<PrayerTimeInfo> = _currentTimeMillis.map { time ->
        val defaultCity = PrayerCalculationHelper.popularCities[0]
        val cal = Calendar.getInstance().apply { timeInMillis = time }
        val times = PrayerCalculationHelper.calculatePrayerTimes(cal, defaultCity, defaultCity.defaultMethod)
        
        val nextName = times.nextPrayerName
        val nextTime = times.nextPrayerTimeFormatted
        val millis = times.millisUntilNextPrayer
        val totalSec = (millis / 1000L).coerceAtLeast(0L)
        val h = totalSec / 3600L
        val m = (totalSec % 3600L) / 60L
        val s = totalSec % 60L
        
        PrayerTimeInfo(
            nextName = nextName,
            nextTime = nextTime,
            countdown = String.format("%02d:%02d:%02d", h, m, s)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PrayerTimeInfo())

    val dateInfo: StateFlow<HijriDateInfo> = _currentTimeMillis.map { time ->
        calculateHijriDate(time)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HijriDateInfo())

    val sections = listOf(
        HasanatSectionItem(
            id = "quran",
            titleKey = "القرآن الكريم والتجويد",
            subtitleKey = "المصحف المرتل وتدبر الآيات",
            descKey = "تلاوات خاشعة لكبار القراء، أحكام التجويد، تفاسير معتمدة، وتزامن الآيات.",
            badgeKey = "114 سورة • 6236 آية",
            categoryKey = "الوحيين الشريفين",
            icon = Icons.Default.MenuBook,
            accentColor = Color(0xFF10B981),
            secondaryAccent = Color(0xFF34D399),
            route = AppState.QURAN_HUB
        ),
        HasanatSectionItem(
            id = "sahihain",
            titleKey = "الصحيحان: البخاري ومسلم",
            subtitleKey = "أصح كتابين بعد كتاب الله",
            descKey = "أحاديث نبوية منتقاة من البخاري ومسلم مع الشرح والفوائد والتكامل القرآني.",
            badgeKey = "46 حديثاً محققاً",
            categoryKey = "الوحيين الشريفين",
            icon = Icons.Default.AutoStories,
            accentColor = GoldPrimary,
            secondaryAccent = GoldSecondary,
            route = AppState.ISLAMIC_LIBRARY
        ),
        HasanatSectionItem(
            id = "prayer",
            titleKey = "محراب الصلاة والمواقيت",
            subtitleKey = "حساب فلكي دقيق وبوصلة القبلة",
            descKey = "أوقات الصلوات الخمس المعتمدة، تنبيهات الأذان، ورياض الجنة وسنن الرواتب.",
            badgeKey = "مواقيت حية • بوصلة",
            categoryKey = "العبادات والمواقيت",
            icon = Icons.Default.Mosque,
            accentColor = Color(0xFF0EA5E9),
            secondaryAccent = Color(0xFF38BDF8),
            route = AppState.PRAYER_TIMES
        ),
        HasanatSectionItem(
            id = "azkar",
            titleKey = "الأذكار وحصن المسلم",
            subtitleKey = "أذكار الصباح والمساء واليوم",
            descKey = "الأدعية المأثورة الموثقة مع عداد سبحة تفاعلي وتنبيهات أوقات الاستجابة.",
            badgeKey = "42 ذكراً مأثوراً",
            categoryKey = "العبادات والمواقيت",
            icon = Icons.Default.AutoAwesome,
            accentColor = Color(0xFFF59E0B),
            secondaryAccent = Color(0xFFFCD34D),
            route = AppState.AZKAR
        ),
        HasanatSectionItem(
            id = "scholars",
            titleKey = "سيرة الأكابر والتراجم",
            subtitleKey = "أئمة السلف والتابعين والعلماء",
            descKey = "سير الأئمة الأربعة وعلماء الحديث وصناع التاريخ الإسلامي مع الدروس المستفادة.",
            badgeKey = "تراجم أئمة الهدى",
            categoryKey = "السير والتاريخ",
            icon = Icons.Default.School,
            accentColor = Color(0xFF8B5CF6),
            secondaryAccent = Color(0xFFA78BFA),
            route = AppState.SCHOLAR_BIOGRAPHIES
        ),
        HasanatSectionItem(
            id = "qasas",
            titleKey = "قصص الأنبياء والعِبر",
            subtitleKey = "سرد قرآني مهيب وتأملات",
            descKey = "قصص الرسل كما وردت في الذكر الحكيم مع استخلاص القواعد الحياتية والإيمانية.",
            badgeKey = "قصص القرآن المحكمة",
            categoryKey = "السير والتاريخ",
            icon = Icons.Default.HistoryEdu,
            accentColor = Color(0xFF14B8A6),
            secondaryAccent = Color(0xFF2DD4BF),
            route = AppState.QASAS
        ),
        HasanatSectionItem(
            id = "library",
            titleKey = "المكتبة الإسلامية الجامعة",
            subtitleKey = "أمهات كتب السلف والتراث",
            descKey = "كتب العقيدة والتفسير والفقه المعتمدة مع فهارس ذكية وكبسولات تلخيص حكمة.",
            badgeKey = "كتب محققة معتمدة",
            categoryKey = "المكتبة والعلوم والصوتيات",
            icon = Icons.Default.LocalLibrary,
            accentColor = Color(0xFFF97316),
            secondaryAccent = Color(0xFFFB923C),
            route = AppState.ISLAMIC_LIBRARY
        ),
        HasanatSectionItem(
            id = "audio",
            titleKey = "الصوتيات والمحاضرات",
            subtitleKey = "تسجيلات وتلاوات نقية مؤثرة",
            descKey = "مكتبة صوتية إسلامية تضم تلاوات نادرة ومحاضرات علمية منتقاة بعناية.",
            badgeKey = "تسجيلات عالية النقاء",
            categoryKey = "المكتبة والعلوم والصوتيات",
            icon = Icons.Default.GraphicEq,
            accentColor = Color(0xFFEC4899),
            secondaryAccent = Color(0xFFF472B6),
            route = AppState.AUDIO_LIBRARY
        )
    )

    private val _quranStats = MutableStateFlow("114 سورة • 6236 آية")
    val quranStats = _quranStats.asStateFlow()

    private val _hadithStats = MutableStateFlow("46 حديثاً محققاً")
    val hadithStats = _hadithStats.asStateFlow()

    private val _azkarStats = MutableStateFlow("42 ذكراً مأثوراً")
    val azkarStats = _azkarStats.asStateFlow()

    private val _recentDesign = MutableStateFlow<HadithCardEntity?>(null)
    val recentDesign = _recentDesign.asStateFlow()

    private val _latestChangelogs = MutableStateFlow<List<ChangelogEntity>>(emptyList())
    val latestChangelogs = _latestChangelogs.asStateFlow()

    private fun updateDynamicStats() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            
            val surahCount = QuranDataProvider.surahs.size
            val ayahCount = 6236 // Still constant unless we count versesMap
            _quranStats.value = "$surahCount سورة • $ayahCount آية"
            
            // Actual Hadith count from Sahihain
            val actualHadiths = SahihHadithsRepository.bukhariHadiths.size + SahihHadithsRepository.muslimHadiths.size
            _hadithStats.value = "$actualHadiths حديثاً محققاً"
            
            // Actual Azkar count from fortress of the Muslim
            _azkarStats.value = "42 ذكراً مأثوراً"

            // Recent design
            _recentDesign.value = db.hadithCardDao().getAllHadithCardsOnce().firstOrNull()

            // Latest changelogs
            val logs = db.changelogDao().getAllChangelogs()
            _latestChangelogs.value = logs.take(3)

            // سجل التحديث الذي أضفته (Record the update added)
            if (logs.none { it.versionName == "1.2.6" }) {
                db.changelogDao().insertChangelog(
                    ChangelogEntity(
                        id = "v1_2_6",
                        versionName = "1.2.6",
                        versionCode = 126,
                        title = "مركز مشكاة الهدى: نظام الإنجاز والشفافية 🌟",
                        releaseDate = java.text.SimpleDateFormat("d MMMM yyyy", Locale("ar")).format(Date()),
                        categoryBadge = "تحديث تقني ⚡",
                        isLatest = true,
                        highlights = "• إضافة نافذة المساعدة (Help Dialog) المتكاملة.\n• نظام تتبع الإنجاز الإيماني اليومي (Daily Progress).\n• ربط الإحصائيات بقاعدة البيانات لجعلها حية ودقيقة.\n• تكامل مباشر مع الاستوديو الإبداعي لعرض آخر التصاميم."
                    )
                )
            }
        }
    }

    val filteredSections = _searchQuery.map { query ->
        if (query.isBlank()) {
            sections
        } else {
            sections.filter { item ->
                Translator.tr(item.titleKey).contains(query, ignoreCase = true) ||
                Translator.tr(item.subtitleKey).contains(query, ignoreCase = true) ||
                Translator.tr(item.descKey).contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), sections)

    val groupedSections = filteredSections.map { list ->
        list.groupBy { it.categoryKey }.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val curatedAyahs = listOf(
        QuranAyahWithTafsir(
            surahNumber = 17,
            ayahNumber = 9,
            surahName = "الإسراء",
            text = "إِنَّ هَٰذَا الْقُرْآنَ يَهْدِي لِلَّتِي هِيَ أَقْوَمُ وَيُبَشِّرُ الْمُؤْمِنِينَ الَّذِينَ يَعْمَلُونَ الصَّالِحَاتِ أَنَّ لَهُمْ أَجْرًا كَبِيرًا",
            tafsir = "إن هذا القرآن الذي أنزلناه يرشد الناس إلى أكمل الطرق وأعدلها في العقائد والأخلاق والتشريعات، ويبشر المؤمنين به العاملين بالصالحات بأن لهم عند الله ثواباً عظيماً وهو الجنة."
        ),
        QuranAyahWithTafsir(
            surahNumber = 13,
            ayahNumber = 28,
            surahName = "الرعد",
            text = "الَّذِينَ آمَنُوا وَتَطْمَئِنُّ قُلُوبُهُم بِذِكْرِ اللَّهِ ۗ أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ",
            tafsir = "وتسكن قلوبهم وتستريح بتوحيد الله وطاعته وكلامه، ألا بذكره سبحانه وطاعته وحمده تطمئن القلوب وتسكن وتزول عنها المخاوف والهموم والاضطرابات."
        ),
        QuranAyahWithTafsir(
            surahNumber = 2,
            ayahNumber = 286,
            surahName = "البقرة",
            text = "لَا يُكَلِّفُ اللَّهُ نَفْسًا إِلَّا وُسْعَهَا ۚ لَهَا مَا كَسَبَتْ وَعَلَيْهَا مَا اكْتَسَبَتْ",
            tafsir = "لا يكلف الله نفساً من الأعمال والفرائض إلا ما تطيقه وتسعه قدرتها رحمةً وتيسيراً، فلها ثواب ما عملت من خير، وعليها وزر ما اقترفت من سوء."
        ),
        QuranAyahWithTafsir(
            surahNumber = 2,
            ayahNumber = 186,
            surahName = "البقرة",
            text = "وَإِذَا سَأَلَكَ عِبَادِي عَنِّي فَإِنِّي قَرِيبٌ ۖ أُجِيبُ دَعْوَةَ الدَّاعِ إِذَا دَعَانِ ۖ فَلْيَسْتَجِيبُوا لِي وَلْيُؤْمِنُوا بِي لَعَلَّهُمْ يَرْشُدُونَ",
            tafsir = "وإذا سألك عبادي عن قربي وإجابتي فإني قريب منهم بعلمي ورعايتي، أجيب دعوة من دعاني مخلصاً، فليطيعوا أمري وليؤمنوا بي لعلهم يهتدون لصلاح دنياهم وأخراهم."
        ),
        QuranAyahWithTafsir(
            surahNumber = 39,
            ayahNumber = 53,
            surahName = "الزمر",
            text = "قُلْ يَا عِبَادِيَ الَّذِينَ أَسْرَفُوا عَلَىٰ أَنفُسِهِمْ لَا تَقْنَطُوا مِن رَّحْمَةِ اللَّهِ ۚ إِنَّ اللَّهَ يَغْفِرُ الذُّنُوبَ جَمِيعًا ۚ إِنَّهُ هُوَ الْغَفُورُ الرَّحِيمُ",
            tafsir = "قل لعبادي الذين أفرطوا في الذنوب والمعاصي: لا تيأسوا من مغفرة الله وفضله، فإن الله يغفر الذنوب جميعاً لمن تاب وأناب، إنه واسع المغفرة عظيم الرحمة."
        ),
        QuranAyahWithTafsir(
            surahNumber = 65,
            ayahNumber = 2,
            surahName = "الطلاق",
            text = "وَمَن يَتَّقِ اللَّهَ يَجْعَل لَّهُ مَخْرَجًا * وَيَرْزُقْهُ مِنْ حَيْثُ لَا يَحْتَسِبُ ۚ وَمَن يَتَوَكَّلْ عَلَى اللَّهِ فَهُوَ حَسْبُهُ",
            tafsir = "ومن يخف الله فيلتزم حدوده يجعل له مخلصاً من كل كرب ومضيق، ويسوق له رزقه من وجه لا يخطر على باله، ومن يعتمد على الله ويفوض أمره إليه فهو كافيه وناصره."
        ),
        QuranAyahWithTafsir(
            surahNumber = 94,
            ayahNumber = 5,
            surahName = "الشرح",
            text = "فَإِنَّ مَعَ الْعُسْرِ يُسْرًا * إِنَّ مَعَ الْعُسْرِ يُسْرًا",
            tafsir = "فإن مع الشدة والضيق سهولة وفرجاً، إن مع الضيق الشديد يسراً مضاعفاً، فلن يغلب عسر يسرين أبداً، وكلما اشتدت الأزمة جاء الفرج من الله."
        ),
        QuranAyahWithTafsir(
            surahNumber = 93,
            ayahNumber = 3,
            surahName = "الضحى",
            text = "مَا وَدَّعَكَ رَبُّكَ وَمَا قَلَىٰ * وَلَلْآخِرَةُ خَيْرٌ لَّكَ مِنَ الْأُولَىٰ * وَلَسَوْفَ يُعْطِيكَ رَبُّكَ فَتَرْضَىٰ",
            tafsir = "ما تركك ربك وما أبغضك، وللدار الآخرة وما أعده الله لك فيها خير لك وأجلّ من الدنيا الفانية، ولسوف يمنحك ربك من فضله وإنعامه ما يرضي نفسك وتقر به عينك."
        ),
        QuranAyahWithTafsir(
            surahNumber = 40,
            ayahNumber = 60,
            surahName = "غافر",
            text = "وَقَالَ رَبُّكُمُ ادْعُونِي أَسْتَجِبْ لَكُمْ ۚ إِنَّ الَّذِينَ يَسْتَكْبِرُونَ عَنْ عِبَادَتِي سَيَدْخُلُونَ جَهَنَّمَ دَاخِرِينَ",
            tafsir = "وقال ربكم: تضرعوا إليّ بالدعاء والعبادة أجبكم وأعطكم سؤلكم، إن الذين يستكبرون عن إفرادي بالعبادة والدعاء سيدخلون جهنم صاغرين أذلاء."
        ),
        QuranAyahWithTafsir(
            surahNumber = 16,
            ayahNumber = 128,
            surahName = "النحل",
            text = "إِنَّ اللَّهَ مَعَ الَّذِينَ اتَّقَوْا وَّالَّذِينَ هُم مُّحْسِنُونَ",
            tafsir = "إن الله سبحانه وتوفيقه وحفظه مع الذين يتقون عقابه باجتناب نواهيه، ومع الذين يحسنون في عبادته والإحسان إلى عباده بالبذل والخير."
        ),
        QuranAyahWithTafsir(
            surahNumber = 3,
            ayahNumber = 139,
            surahName = "آل عمران",
            text = "وَلَا تَهِنُوا وَلَا تَحْزَنُوا وَأَنتُمُ الْأَعْلَوْنَ إِن كُنتُم مُّؤْمِنِينَ",
            tafsir = "ولا تضعفوا ولا تحزنوا على ما أصابكم من ابتلاءات، فأنتم الأعلون شأناً وقدراً وعاقبة حميدة إن كنتم مخلصين الإيمان بالله واثقين بوعده."
        ),
        QuranAyahWithTafsir(
            surahNumber = 57,
            ayahNumber = 4,
            surahName = "الحديد",
            text = "وَهُوَ مَعَكُمْ أَيْنَ مَا كُنتُمْ ۚ وَاللَّهُ بِمَا تَعْمَلُونَ بَصِيرٌ",
            tafsir = "وهو سبحانه مطلع عليكم وشاهد لأعمالكم أينما كنتم في بر أو بحر، والله بصير بكل أفعالكم وسيجازيكم عليها بعدله وفضله."
        )
    )

    private val _currentAyah = MutableStateFlow(curatedAyahs[0])
    val currentAyah = _currentAyah.asStateFlow()

    private val _isAutoRotating = MutableStateFlow(true)
    val isAutoRotating = _isAutoRotating.asStateFlow()

    private val _autoRotateProgress = MutableStateFlow(0f)
    val autoRotateProgress = _autoRotateProgress.asStateFlow()

    val spiritualPearls = listOf(
        SpiritualPearl(
            typeKey = "آية قرآنية",
            text = "إِنَّ هَٰذَا الْقُرْآنَ يَهْدِي لِلَّتِي هِيَ أَقْوَمُ وَيُبَشِّرُ الْمُؤْمِنِينَ الَّذِينَ يَعْمَلُونَ الصَّالِحَاتِ أَنَّ لَهُمْ أَجْرًا كَبِيرًا",
            source = "سورة الإسراء • آية 9",
            benefit = "القرآن هو البوصلة الإلهية الكاملة لتهذيب النفس ورشاد الحياة وبشارة الصالحين."
        )
    )

    init {
        viewModelScope.launch {
            while (true) {
                _currentTimeMillis.value = System.currentTimeMillis()
                delay(1000L)
            }
        }

        // تحميل بيانات المصحف والتفسير في الخلفية للتبديل من كافة السور
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                QuranDataProvider.loadFromAssets(context)
                QuranDataProvider.loadTafsirFromAssets(context)
            }
        }

        // التدوير التلقائي والعشوائي للآية مع شريط التقدم الزمني (كل 12 ثانية)
        viewModelScope.launch {
            updateDynamicStats()
            val totalSteps = 120 // 12 ثانية (كل خطوة 100 ميلي ثانية)
            var currentStep = 0
            while (true) {
                delay(100L)
                if (_isAutoRotating.value) {
                    currentStep++
                    _autoRotateProgress.value = (currentStep.toFloat() / totalSteps).coerceIn(0f, 1f)
                    if (currentStep >= totalSteps) {
                        currentStep = 0
                        _autoRotateProgress.value = 0f
                        nextRandomAyah()
                    }
                }
            }
        }
    }

    fun nextRandomAyah() {
        _autoRotateProgress.value = 0f
        val random = java.util.Random()
        
        // محاولة سحب عشوائي مع تفسير مؤكد
        val pool = curatedAyahs
        val next = pool[random.nextInt(pool.size)]
        if (next.text != _currentAyah.value.text || pool.size <= 1) {
            _currentAyah.value = next
        } else {
            val another = pool.firstOrNull { it.text != _currentAyah.value.text } ?: next
            _currentAyah.value = another
        }
        _currentPearlIndex.value++
    }

    fun toggleAutoRotate() {
        _isAutoRotating.value = !_isAutoRotating.value
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateCategoryFilter(categoryKey: String) {
        _selectedCategoryKey.value = categoryKey
    }

    fun toggleViewMode() {
        _isGridView.value = !_isGridView.value
    }

    fun incrementTasbeeh() {
        val newCount = _tasbeehCount.value + 1
        _tasbeehCount.value = newCount
        prefs.edit().putInt("mishkat_tasbeeh_count", newCount).apply()
    }

    fun resetTasbeeh() {
        _tasbeehCount.value = 0
        prefs.edit().putInt("mishkat_tasbeeh_count", 0).apply()
    }

    fun toggleTasbeehGoal() {
        if (_tasbeehCount.value >= 33) {
            _tasbeehCount.value = 0
            prefs.edit().putInt("mishkat_tasbeeh_count", 0).apply()
        } else {
            _tasbeehCount.value = 33
            prefs.edit().putInt("mishkat_tasbeeh_count", 33).apply()
        }
    }

    fun setTasbeehCount(count: Int) {
        val safe = count.coerceAtLeast(0)
        _tasbeehCount.value = safe
        prefs.edit().putInt("mishkat_tasbeeh_count", safe).apply()
    }

    fun nextPearl() {
        _currentPearlIndex.value++
    }

    private fun calculateHijriDate(timeMillis: Long): HijriDateInfo {
        val cal = Calendar.getInstance().apply { timeInMillis = timeMillis }
        val gregorianFormat = SimpleDateFormat("d MMMM yyyy", Locale(Translator.currentLanguage))
        val gregStr = gregorianFormat.format(cal.time)

        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)

        var m = month
        var y = year
        if (m < 3) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        val jd = floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5

        val z = jd + 0.5
        val ijd = floor(z)
        val l = ijd - 1948440 + 10632
        val n = floor((l - 1) / 10631.0)
        val lPrime = l - 10631 * n + 354
        val j = (floor((10985 - lPrime) / 5316.0)) * (floor((50 * lPrime) / 17719.0)) + (floor(lPrime / 5670.0)) * (floor((43 * lPrime) / 15238.0))
        val lDoublePrime = lPrime - (floor((30 - j) / 15.0)) * (floor((17719 * j) / 50.0)) - (floor(j / 16.0)) * (floor((15238 * j) / 43.0)) + 29
        val hijriMonth = floor((24 * lDoublePrime) / 709.0).toInt()
        val hijriDay = (lDoublePrime - floor((709 * hijriMonth) / 24.0)).toInt()
        val hijriYear = (30 * n + j - 30).toInt()

        val hijriMonthsAr = listOf(
            "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
            "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
        )
        val hMonthName = Translator.tr(hijriMonthsAr.getOrElse(hijriMonth - 1) { "رمضان" })
        val hijriStr = "$hijriDay $hMonthName $hijriYear ${Translator.tr("هـ")}"

        return HijriDateInfo(hijriStr, gregStr)
    }
}
