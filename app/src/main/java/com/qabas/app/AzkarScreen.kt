package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.app.Application
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleSearchBar
import com.qabas.app.ui.components.AppleTopBar
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class AzkarTimeCategory(val label: String, val icon: String) {
    MORNING("أذكار الصباح", "☀️"),
    EVENING("أذكار المساء", "🌙"),
    SLEEP("أذكار النوم", "😴"),
    WAKING("أذكار الاستيقاظ", "🌅"),
    AFTER_PRAYER("أذكار بعد الصلاة", "🕌"),
    MOSQUE("أذكار المسجد", "🕋")
}

data class AzkarItem(
    val id: String,
    val title: String,
    val text: String,
    val fadl: String,
    val source: String,
    val targetCount: Int,
    val category: AzkarTimeCategory,
    val timeHint: String
)

class AzkarViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
    private val todayKey: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private val _morningAzkar = MutableStateFlow<List<AzkarItem>>(emptyList())
    val morningAzkar: StateFlow<List<AzkarItem>> = _morningAzkar.asStateFlow()

    private val _eveningAzkar = MutableStateFlow<List<AzkarItem>>(emptyList())
    val eveningAzkar: StateFlow<List<AzkarItem>> = _eveningAzkar.asStateFlow()

    private val _sleepAzkar = MutableStateFlow<List<AzkarItem>>(emptyList())
    val sleepAzkar: StateFlow<List<AzkarItem>> = _sleepAzkar.asStateFlow()

    private val _wakingAzkar = MutableStateFlow<List<AzkarItem>>(emptyList())
    val wakingAzkar: StateFlow<List<AzkarItem>> = _wakingAzkar.asStateFlow()

    private val _prayerAzkar = MutableStateFlow<List<AzkarItem>>(emptyList())
    val prayerAzkar: StateFlow<List<AzkarItem>> = _prayerAzkar.asStateFlow()

    private val _mosqueAzkar = MutableStateFlow<List<AzkarItem>>(emptyList())
    val mosqueAzkar: StateFlow<List<AzkarItem>> = _mosqueAzkar.asStateFlow()

    // Map of zikr id to current count
    private val _progressMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val progressMap: StateFlow<Map<String, Int>> = _progressMap.asStateFlow()

    // Selected tab: 0 = Morning, 1 = Evening, 2 = Sleep, 3 = Waking,
    // 4 = After Prayer, 5 = Mosque, 6 = All (AzkarTabSelector)
    // ق140: الافتراضي حسب وقت اليوم — كان الصباح (0) دائماً حتى لمن
    // يفتح الأذكار ليلاً فيجد أذكار الصباح أولاً ويضطر للتبديل يدوياً
    // كل مرة. القاعدة تقريبية بالساعة (الفجر–الظهر صباح، والعصر–الفجر
    // مساءً): 05:00–14:59 صباح، وما عداها مساءً؛ واختيار المستخدم بعد
    // الفتح يبقى كما هو (تُحسب مرة عند إنشاء النموذج فقط).
    private val _selectedTab = MutableStateFlow(
        if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) in 5..14) 0 else 1
    )
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // TTS Speaking ID
    private val _speakingId = MutableStateFlow<String?>(null)
    val speakingId: StateFlow<String?> = _speakingId.asStateFlow()

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    // التهيئة غير متزامنة: بين إنشاء المحرك وonInit قد يأتي نقر جديد —
    // بلا هذا العلم يُعامل كفشل سابق فيُكذَّب التنبيه ويُسقَط الطلب.
    private var isTtsInitializing = false

    // سرعة الاستماع: محفوظة محلياً (0.75×–1.5×)، وتُطبَّق عند كل نطق.
    private val _ttsRate = MutableStateFlow(
        prefs.getFloat("azkar_tts_rate", 1.0f).coerceIn(0.5f, 2.0f)
    )
    val ttsRate: StateFlow<Float> = _ttsRate.asStateFlow()

    /** تدوير سرعة الاستماع 0.75× ← 1× ← 1.25× ← 1.5×، مع الحفظ والتطبيق الفوري. */
    fun cycleTtsRate() {
        val next = when (_ttsRate.value) {
            0.75f -> 1.0f
            1.0f -> 1.25f
            1.25f -> 1.5f
            else -> 0.75f
        }
        _ttsRate.value = next
        prefs.edit().putFloat("azkar_tts_rate", next).apply()
        tts?.setSpeechRate(next)
    }

    init {
        loadAllAzkar()
        loadSavedProgress()
        // حمّل نص القرآن الموثّق للختمات والأذكار التي تتضمن آيات.
        viewModelScope.launch {
            if (QuranDataProvider.ensureQuranLoaded(application)) {
                loadAllAzkar()
            }
        }
    }

    private fun quranAyah(surah: Int, ayah: Int): String =
        QuranDataProvider.getVerseText(surah, ayah)
            ?: "نص الآية غير متاح حالياً؛ افتح المصحف وحاول بعد تحميل البيانات."

    private fun quranSurahs(vararg surahIds: Int): String {
        val basmala = QuranDataProvider.getVerseText(1, 1).orEmpty()
        val surahTexts = surahIds.toList().mapNotNull { id ->
            QuranDataProvider.getVersesForSurah(id, QuranDataProvider.surahNameOf(id))
                .takeIf { it.isNotEmpty() }
                ?.joinToString(" ") { it.verseText }
        }
        return surahTexts.joinToString("\n\n") { surahText ->
            listOf(basmala, surahText).filter { it.isNotBlank() }.joinToString("\n")
        }.ifBlank { "نص السور غير متاح حالياً؛ افتح المصحف وحاول بعد تحميل البيانات." }
    }

    private fun lastTwoBaqarahAyat(): String =
        listOf(285, 286).mapNotNull { QuranDataProvider.getVerseText(2, it) }
            .joinToString(" ")
            .ifBlank { "نص الآيتين غير متاح حالياً؛ افتح المصحف وحاول بعد تحميل البيانات." }

    private fun loadSavedProgress() {
        val allIds = (_morningAzkar.value + _eveningAzkar.value + _sleepAzkar.value + _wakingAzkar.value + _prayerAzkar.value + _mosqueAzkar.value).map { it.id }
        val map = mutableMapOf<String, Int>()
        for (id in allIds) {
            val count = prefs.getInt("azkar_count_${todayKey}_$id", 0)
            if (count > 0) {
                map[id] = count
            }
        }
        _progressMap.value = map
    }

    /**
     * إعادة تحميل تقدّم اليوم من التفضيلات. يُستدعى عند تبدّل التاريخ
     * والتطبيق مفتوح (حلقة الدقيقة في الشاشة): بلا هذا يبقى تقدّم أمس
     * في الذاكرة، فتُبنى نقرات اليوم الجديد على عدّ أمس وتُكتب في
     * مفاتيح اليوم الجديد.
     */
    fun refreshDailyProgress() {
        loadSavedProgress()
    }

    fun initTts(context: Context) {
        if (tts == null) {
            isTtsInitializing = true
            tts = TextToSpeech(context.applicationContext) { status ->
                isTtsInitializing = false
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale("ar"))
                    isTtsReady = (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED)
                    if (isTtsReady) {
                        pendingSpeakItem?.let { item ->
                            pendingSpeakItem = null
                            speakNow(item)
                        }
                    } else {
                        pendingSpeakItem = null
                        Toast.makeText(context.applicationContext, "تعذّر تحضير المحرك الصوتي العربي على هذا الجهاز", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    pendingSpeakItem = null
                    Toast.makeText(context.applicationContext, "تعذّر تحضير المحرك الصوتي العربي على هذا الجهاز", Toast.LENGTH_SHORT).show()
                }
            }
            // يُسجَّل المستمع مرة واحدة عند الإنشاء وقبل أي نطق: التسجيل
            // بعد speak() كان يفوته اكتمال النصوص القصيرة فيبقى speakingId
            // عالقاً (زر الإيقاف وأيقونة التحدث لا يزولان).
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (_speakingId.value == utteranceId) _speakingId.value = null
                }

                override fun onError(utteranceId: String?) {
                    if (_speakingId.value == utteranceId) _speakingId.value = null
                }
            })
        }
    }

    fun setSelectedTab(index: Int) {
        _selectedTab.value = index
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * يزيد العدّاد نقرة واحدة إن لم يبلغ الهدف، ويعيد true عند الزيادة
     * الفعلية وfalse عند بلوغ الهدف — ليصدر صوت النقرة عن زيادة حقيقية
     * فقط، لا عن نقرة على ذكر مكتمل لا يتحرك عدّاده.
     */
    fun incrementCount(id: String, target: Int): Boolean {
        val current = _progressMap.value[id] ?: 0
        if (current < target) {
            val next = current + 1
            val newMap = _progressMap.value + (id to next)
            _progressMap.value = newMap
            prefs.edit().putInt("azkar_count_${todayKey}_$id", next).apply()

            // 🌟 Auto sync with daily spiritual progress keys
            val morning = _morningAzkar.value
            val morningDone = morning.isNotEmpty() && morning.all { (newMap[it.id] ?: 0) >= it.targetCount }
            if (morningDone) {
                prefs.edit().putBoolean("daily_morning_azkar_$todayKey", true).apply()
            }
            val evening = _eveningAzkar.value
            val eveningDone = evening.isNotEmpty() && evening.all { (newMap[it.id] ?: 0) >= it.targetCount }
            if (eveningDone) {
                prefs.edit().putBoolean("daily_evening_azkar_$todayKey", true).apply()
            }
            // ق121: إكمال ورد حقيقي (صباح أو مساء) يسجّل سلسلة الورد —
            // كان التسجيل من زر «تم إنجاز الورد» في الإشعار وحده، فبقيت
            // السلسلة صفراً لمن يكمل الأذكار داخل التطبيق كل يوم، بينما
            // نقرة إشعار بلا أذكار كانت ترفعها. مرة واحدة في اليوم داخل
            // recordWirdCompleted فلا تضخيم بتكرار الإكمال.
            if (morningDone || eveningDone) {
                NotificationPreferencesManager.recordWirdCompleted(getApplication<Application>())
            }
            return true
        }
        return false
    }

    fun resetCount(id: String) {
        // ق83: تصفير ذكر مكتمل كان يُبقي شارة إكمال الصباح/المساء
        // (daily_morning/evening_azkar) في الرئيسية والحسنات — فتظل
        // البطاقة تدّعي اكتمال الأذكار وذكرٌ منها صفر. التراجع عن
        // نقرة وتصفير الكل يسحبان الشارة؛ وهذا المسار الثالث كان
        // منسياً. يُسحب العلم هنا كما فيهما.
        val item = (_morningAzkar.value + _eveningAzkar.value + _sleepAzkar.value +
            _wakingAzkar.value + _prayerAzkar.value + _mosqueAzkar.value)
            .firstOrNull { it.id == id }
        _progressMap.value = _progressMap.value + (id to 0)
        val editor = prefs.edit().remove("azkar_count_${todayKey}_$id")
        if (item != null) {
            when (item.category) {
                AzkarTimeCategory.MORNING -> editor.remove("daily_morning_azkar_$todayKey")
                AzkarTimeCategory.EVENING -> editor.remove("daily_evening_azkar_$todayKey")
                else -> {}
            }
        }
        editor.apply()
    }

    /**
     * التراجع عن آخر نقرة (خطأ النقر شائع في العدّاد، وأفضل تطبيقات
     * الأذكار تقدّم التراجع؛ البديل الوحيد قبله كان تصفير الذكر كاملاً).
     * ينقص 1 بحد أدنى 0 ويحفظ، ويسحب شارة إكمال الصباح/المساء
     * إن نزل العدّ دون الهدف بعد اكتماله.
     */
    fun decrementCount(id: String) {
        val current = _progressMap.value[id] ?: 0
        if (current <= 0) return
        val next = current - 1
        _progressMap.value = _progressMap.value + (id to next)
        val editor = prefs.edit()
        if (next == 0) editor.remove("azkar_count_${todayKey}_$id")
        else editor.putInt("azkar_count_${todayKey}_$id", next)
        val all = _morningAzkar.value + _eveningAzkar.value + _sleepAzkar.value +
            _wakingAzkar.value + _prayerAzkar.value + _mosqueAzkar.value
        val item = all.firstOrNull { it.id == id }
        if (item != null && next < item.targetCount) {
            when (item.category) {
                AzkarTimeCategory.MORNING -> editor.remove("daily_morning_azkar_$todayKey")
                AzkarTimeCategory.EVENING -> editor.remove("daily_evening_azkar_$todayKey")
                else -> {}
            }
        }
        editor.apply()
    }

    fun resetAllCounters(category: AzkarTimeCategory? = null) {
        if (category == null) {
            _progressMap.value = emptyMap()
            val allIds = (_morningAzkar.value + _eveningAzkar.value + _sleepAzkar.value + _wakingAzkar.value + _prayerAzkar.value + _mosqueAzkar.value).map { it.id }
            val editor = prefs.edit()
            allIds.forEach { editor.remove("azkar_count_${todayKey}_$it") }
            editor.remove("daily_morning_azkar_$todayKey")
            editor.remove("daily_evening_azkar_$todayKey")
            editor.apply()
        } else {
            val listToReset = when(category) {
                AzkarTimeCategory.MORNING -> _morningAzkar.value
                AzkarTimeCategory.EVENING -> _eveningAzkar.value
                AzkarTimeCategory.SLEEP -> _sleepAzkar.value
                AzkarTimeCategory.WAKING -> _wakingAzkar.value
                AzkarTimeCategory.AFTER_PRAYER -> _prayerAzkar.value
                AzkarTimeCategory.MOSQUE -> _mosqueAzkar.value
            }
            val idsToReset = listToReset.map { it.id }.toSet()
            _progressMap.value = _progressMap.value.filterKeys { it !in idsToReset }
            val editor = prefs.edit()
            idsToReset.forEach { editor.remove("azkar_count_${todayKey}_$it") }
            if (category == AzkarTimeCategory.MORNING) {
                editor.remove("daily_morning_azkar_$todayKey")
            } else if (category == AzkarTimeCategory.EVENING) {
                editor.remove("daily_evening_azkar_$todayKey")
            }
            editor.apply()
        }
    }

    // طلب نطق معلّق ريثما يجهز محرك TTS: النقرة الأولى أثناء التهيئة
    // كانت تُسقَط مع تنبيه «جاري تحضير...» فيضطر المستخدم للنقر ثانية.
    private var pendingSpeakItem: AzkarItem? = null

    fun speakZikr(item: AzkarItem, context: Context) {
        if (_speakingId.value == item.id) {
            stopSpeaking()
            return
        }
        initTts(context)
        if (isTtsReady && tts != null) {
            speakNow(item)
        } else if (isTtsInitializing) {
            // التهيئة ما زالت جارية: يُعلَّق الطلب فيُنطق عند جهوز
            // المحرك، بدل تنبيه فشل كاذب وإسقاط الطلب — كان النقر
            // أثناء التهيئة على ذكر آخر يسلك فرع الفشل أدناه.
            pendingSpeakItem = item
        } else {
            // تهيئة سابقة فشلت ولن يعيد onInit إطلاقاً: لا تعليق صامت.
            Toast.makeText(context, "تعذّر تحضير المحرك الصوتي العربي على هذا الجهاز", Toast.LENGTH_SHORT).show()
        }
    }

    private fun speakNow(item: AzkarItem) {
        val cleanText = item.text.replace("•", " ").trim()
        if (cleanText.isBlank()) return
        val engine = tts ?: return
        engine.setSpeechRate(_ttsRate.value)
        _speakingId.value = item.id
        val result = engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, item.id)
        if (result == TextToSpeech.ERROR) {
            // فشل متزامن قد لا يصل معه onError أبداً: لا يبقى زر
            // الإيقاف وأيقونة التحدث عالقين على ذكر لم يُنطق.
            _speakingId.value = null
            Toast.makeText(
                getApplication<Application>().applicationContext,
                "تعذّر تشغيل الصوت لهذا الذكر",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun stopSpeaking() {
        pendingSpeakItem = null
        tts?.stop()
        _speakingId.value = null
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
    }

    private fun loadAllAzkar() {
        _morningAzkar.value = listOf(
            AzkarItem(
                id = "m1",
                title = "آية الكرسي",
                text = quranAyah(2, 255),
                fadl = "من قالها حين يصبح أُجير من الجن والشياطين حتى يمسي.",
                source = "سورة البقرة: 255 (صحيح الترغيب)",
                targetCount = 1,
                category = AzkarTimeCategory.MORNING,
                timeHint = "تقال صباحاً بعد صلاة الفجر"
            ),
            AzkarItem(
                id = "m2",
                title = "المعوذات الثلاث (الإخلاص والفلق والناس)",
                text = quranSurahs(112, 113, 114),
                fadl = "قال النبي ﷺ: «قُلْ هُوَ اللَّهُ أَحَدٌ، والمُعَوِّذَتَيْنِ حِينَ تُمْسِي وحِينَ تُصْبِحُ ثَلاثَ مَرَّاتٍ، تَكْفِيكَ مِنْ كُلِّ شَيْءٍ».",
                source = "رواه أبو داود والترمذي وحسنه الألباني",
                targetCount = 3,
                category = AzkarTimeCategory.MORNING,
                timeHint = "تقال 3 مرات حين تصبح"
            ),
            AzkarItem(
                id = "m3",
                title = "سيّد الاستغفار",
                text = "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي فَاغْفِرْ لِي، فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ.",
                fadl = "من قالها موقناً بها حين يصبح فمات من يومه دخل الجنة.",
                source = "رواه البخاري في صحيحه",
                targetCount = 1,
                category = AzkarTimeCategory.MORNING,
                timeHint = "دعاء التوحيد والاستغفار الأعظم"
            ),
            AzkarItem(
                id = "m4",
                title = "أصبحنا وأصبح الملك لله",
                text = "أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ، رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذَا الْيَوْمِ وَخَيْرَ مَا بَعْدَهُ، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذَا الْيَوْمِ وَشَرِّ مَا بَعْدَهُ، رَبِّ أَعُوذُ بِكَ مِنَ الْكَسَلِ وَسُوءِ الْكِبَرِ، رَبِّ أَعُوذُ بِكَ مِنْ عَذَابٍ فِي النَّارِ وَعَذَابٍ فِي الْقَبْرِ.",
                fadl = "افتاح اليوم بالاعتراف بملك الله والتماس خيره والاستعاذة من كسله وشروره.",
                source = "رواه الإمام مسلم في صحيحه",
                targetCount = 1,
                category = AzkarTimeCategory.MORNING,
                timeHint = "تقال مرة واحدة مع إشراقة الصباح"
            ),
            AzkarItem(
                id = "m5",
                title = "أصبحنا على فطرة الإسلام",
                text = "أَصْبَحْنَا عَلَى فِطْرَةِ الْإِسْلَامِ، وَعَلَى كَلِمَةِ الْإِخْلَاصِ، وَعَلَى دِينِ نَبِيِّنَا مُحَمَّدٍ صَلَّى اللَّهُ عَلَيْهِ وَسَلَّمَ، وَعَلَى مِلَّةِ أَبِينَا إِبْرَاهِيمَ حَنِيفًا مُسْلِمًا وَمَا كَانَ مِنَ الْمُشْرِكِينَ.",
                fadl = "تجديد البيعة الإيمانية على ملة التوحيد الخالص وسنة النبي المصطفى ﷺ.",
                source = "رواه أحمد وصححه الألباني",
                targetCount = 1,
                category = AzkarTimeCategory.MORNING,
                timeHint = "تثبيت الهوية الدينية كل فجر"
            ),
            AzkarItem(
                id = "m6",
                title = "اللهم بك أصبحنا وبك أمسينا",
                text = "اللَّهُمَّ بِكَ أَصْبَحْنَا، وَبِكَ أَمْسَيْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ النُّشُورُ.",
                fadl = "تفويض الحياة والموت والنشور لمشيئة الله ورحمته في مطلع النهار.",
                source = "رواه الترمذي وصححه الألباني",
                targetCount = 1,
                category = AzkarTimeCategory.MORNING,
                timeHint = "تقال مرة واحدة"
            ),
            AzkarItem(
                id = "m7",
                title = "بسم الله الذي لا يضر مع اسمه شيء",
                text = "بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ، وَهُوَ السَّمِيعُ الْعَلِيمُ.",
                fadl = "قال النبي ﷺ: «لم يضره شيء حتى يمسي، وفي المساء حتى يصبح».",
                source = "رواه أبو داود والترمذي وقال: حسن صحيح",
                targetCount = 3,
                category = AzkarTimeCategory.MORNING,
                timeHint = "حصن مانع من المكاره والسموم"
            ),
            AzkarItem(
                id = "m8",
                title = "رضيت بالله رباً وبالإسلام ديناً",
                text = "رَضِيتُ بِاللَّهِ رَبًّا، وَبِالْإِسْلَامِ دِينًا، وَبِمُحَمَّدٍ صَلَّى اللَّهُ عَلَيْهِ وَسَلَّمَ نَبِيًّا وَرَسُولًا.",
                fadl = "قال النبي ﷺ: «كان حقاً على الله أن يرضيه يوم القيامة».",
                source = "رواه أحمد وأبو داود والترمذي",
                targetCount = 3,
                category = AzkarTimeCategory.MORNING,
                timeHint = "تكرر 3 مرات"
            ),
            AzkarItem(
                id = "m9",
                title = "دعاء العافية في السمع والبصر والبدن",
                text = "اللَّهُمَّ عَافِنِي فِي بَدَنِي، اللَّهُمَّ عَافِنِي فِي سَمْعِي، اللَّهُمَّ عَافِنِي فِي بَصَرِي، لَا إِلَهَ إِلَّا أَنْتَ. اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنَ الْكُفْرِ وَالْفَقْرِ، وَأَعُوذُ بِكَ مِنْ عَذَابِ الْقَبْرِ، لَا إِلَهَ إِلَّا أَنْتَ.",
                fadl = "حفظ النعم الظاهرة والباطنة والسلامة من الفتن والفقر وعذاب البرزخ.",
                source = "رواه أبو داود وأحمد وحسنه الألباني",
                targetCount = 3,
                category = AzkarTimeCategory.MORNING,
                timeHint = "تكرر 3 مرات صباحاً"
            ),
            AzkarItem(
                id = "m10",
                title = "سؤال العفو والعافية والستر",
                text = "اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي الدُّنْيَا وَالْآخِرَةِ، اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي دِينِي وَدُنْيَايَ وَأَهْلِي وَمَالِي، اللَّهُمَّ اسْتُرْ عَوْرَاتِي، وَآمِنْ رَوْعَاتِي، اللَّهُمَّ احْفَظْنِي مِنْ بَيْنِ يَدَيَّ، وَمِنْ خَلْفِي، وَعَنْ يَمِينِي، وَعَنْ شِمَالِي، وَمِنْ فَوْقِي، وَأَعُوذُ بِعَظَمَتِكَ أَنْ أُغْتَالَ مِنْ تَحْتِي.",
                fadl = "لم يكن رسول الله ﷺ يدعهن حين يصبح وحين يمسي؛ حفظ شامل من الجهات الست.",
                source = "رواه أبو داود وابن ماجه وصححه الحاكم",
                targetCount = 1,
                category = AzkarTimeCategory.MORNING,
                timeHint = "تقال مرة واحدة"
            ),
            AzkarItem(
                id = "m11",
                title = "يا حي يا قيوم برحمتك أستغيث",
                text = "يَا حَيُّ يَا قَيُّومُ بِرَحْمَتِكَ أَسْتَغِيثُ، أَصْلِحْ لِي شَأْنِي كُلَّهُ، وَلَا تَكِلْنِي إِلَى نَفْسِي طَرْفَةَ عَيْنٍ.",
                fadl = "دعاء الكفاية والافتقار إلى الله في سائر تفاصيل الحياة دون حول منا ولا قوة.",
                source = "رواه النسائي والحاكم وحسنه الألباني",
                targetCount = 1,
                category = AzkarTimeCategory.MORNING,
                timeHint = "التجاء خالص لرحمة الحي القيوم"
            ),
            AzkarItem(
                id = "m12",
                title = "حسبي الله لا إله إلا هو",
                text = quranAyah(9, 129),
                fadl = "من قالها سبع مرات حين يصبح وحين يمسي كفاه الله ما أهمه من أمر دنياه وآخرته.",
                source = "رواه أبو داود موقوفاً وصححه جماعة من أهل الحديث",
                targetCount = 7,
                category = AzkarTimeCategory.MORNING,
                timeHint = "تكرر 7 مرات"
            ),
            AzkarItem(
                id = "m13",
                title = "تسبيح عدد الخلق ورضا النفس",
                text = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ: عَدَدَ خَلْقِهِ، وَرِضَا نَفْسِهِ، وَزِنَةَ عَرْشِهِ، وَمِدَادَ كَلِمَاتِهِ.",
                fadl = "قال النبي ﷺ لأم المؤمنين جويرية: «لقد قُلتُ بَعدَكِ أربَعَ كَلِماتٍ ثَلاثَ مَرَّاتٍ لَو وُزِنَتْ بما قُلتِ مُنذُ اليَومِ لَوَزَنَتْهُنَّ».",
                source = "رواه الإمام مسلم في صحيحه",
                targetCount = 3,
                category = AzkarTimeCategory.MORNING,
                timeHint = "تكرر 3 مرات"
            ),
            AzkarItem(
                id = "m14",
                title = "سبحان الله وبحمده (مئة مرة)",
                text = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ.",
                fadl = "من قالها مئة مرة حُطّت خطاياه وإن كانت مثل زبد البحر، ولم يأتِ أحد يوم القيامة بأفضل مما جاء به إلا أحد قال مثل ما قال أو زاد عليه.",
                source = "متفق عليه (البخاري ومسلم)",
                targetCount = 100,
                category = AzkarTimeCategory.MORNING,
                timeHint = "مئة تسبيحة من أعظم الكنوز"
            ),
            AzkarItem(
                id = "m15",
                title = "التهليل والتوحيد الجامع",
                text = "لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ، وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ.",
                fadl = "كانت له عدل عشر رقاب، وكُتبت له مائة حسنة، ومُحيت عنه مائة سيئة، وكانت له حرزاً من الشيطان يومه ذلك حتى يمسي.",
                source = "رواه البخاري ومسلم",
                targetCount = 100,
                category = AzkarTimeCategory.MORNING,
                timeHint = "تقال 100 مرة"
            ),
            AzkarItem(
                id = "m16",
                title = "الصلاة والسلام على النبي المصطفى ﷺ",
                text = "اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَى نَبِيِّنَا مُحَمَّدٍ.",
                fadl = "قال النبي ﷺ: «مَنْ صَلَّى عَلَيَّ حِينَ يُصْبِحُ عَشْرًا، وَحِينَ يُمْسِي عَشْرًا؛ أَدْرَكَتْهُ شَفَاعَتِي يَوْمَ الْقِيَامَةِ».",
                source = "رواه الطبراني وحسنه الألباني",
                targetCount = 10,
                category = AzkarTimeCategory.MORNING,
                timeHint = "تكرر 10 مرات لنيل الشفاعة"
            )
        )

        _eveningAzkar.value = listOf(
            AzkarItem(
                id = "e1",
                title = "آية الكرسي",
                text = quranAyah(2, 255),
                fadl = "من قرأها إذا أمسى أُجير من الشياطين والجن حتى يصبح.",
                source = "سورة البقرة: 255 (صحيح الترغيب)",
                targetCount = 1,
                category = AzkarTimeCategory.EVENING,
                timeHint = "تقال مساءً بعد صلاة العصر أو المغرب"
            ),
            AzkarItem(
                id = "e2",
                title = "خواتيم سورة البقرة",
                text = lastTwoBaqarahAyat(),
                fadl = "قال رسول الله ﷺ: «مَنْ قَرَأَ بِالْآيَتَيْنِ مِنْ آخِرِ سُورَةِ الْبَقَرَةِ فِي لَيْلَةٍ كَفَتَاهُ».",
                source = "متفق عليه (البخاري ومسلم)",
                targetCount = 1,
                category = AzkarTimeCategory.EVENING,
                timeHint = "تقال كل ليلة؛ كفتاه من الشرور والآفات"
            ),
            AzkarItem(
                id = "e3",
                title = "المعوذات الثلاث (الإخلاص والفلق والناس)",
                text = quranSurahs(112, 113, 114),
                fadl = "«تَكْفِيكَ مِنْ كُلِّ شَيْءٍ» حين تمسي وتصبح.",
                source = "رواه أبو داود والترمذي وحسنه الألباني",
                targetCount = 3,
                category = AzkarTimeCategory.EVENING,
                timeHint = "تكرر 3 مرات كل مساء"
            ),
            AzkarItem(
                id = "e4",
                title = "سيّد الاستغفار",
                text = "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي فَاغْفِرْ لِي، فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ.",
                fadl = "من قالها موقناً بها حين يمسي فمات من ليلته دخل الجنة.",
                source = "رواه البخاري في صحيحه",
                targetCount = 1,
                category = AzkarTimeCategory.EVENING,
                timeHint = "ضمان الجنة لمن وافته المنية في ليلته"
            ),
            AzkarItem(
                id = "e5",
                title = "أمسينا وأمسى الملك لله",
                text = "أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهو عَلَى كُلِّ شَيْءٍ قَدِيرٌ، رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذِهِ اللَّيْلَةِ وَخَيْرَ مَا بَعْدَهَا، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذِهِ اللَّيْلَةِ وَشَرِّ مَا بَعْدَهَا، رَبِّ أَعُوذُ بِكَ مِنَ الْكَسَلِ وَسُوءِ الْكِبَرِ، رَبِّ أَعُوذُ بِكَ مِنْ عَذَابٍ فِي النَّارِ وَعَذَابٍ فِي الْقَبْرِ.",
                fadl = "استيداع النفس والليل لرب العالمين واستعاذة من عذاب القبر والنار.",
                source = "رواه مسلم في صحيحه",
                targetCount = 1,
                category = AzkarTimeCategory.EVENING,
                timeHint = "تقال مع حلول المساء"
            ),
            AzkarItem(
                id = "e6",
                title = "اللهم بك أمسينا وبك أصبحنا",
                text = "اللَّهُمَّ بِكَ أَمْسَيْنَا، وَبِكَ أَصْبَحْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ الْمَصِيرُ.",
                fadl = "إقرار بأن الحياة والموت والرجوع إلى الله وحده.",
                source = "رواه أبو داود والترمذي وصححه الألباني",
                targetCount = 1,
                category = AzkarTimeCategory.EVENING,
                timeHint = "تقال مرة واحدة"
            ),
            AzkarItem(
                id = "e7",
                title = "أمسينا على فطرة الإسلام",
                text = "أَمْسَيْنَا عَلَى فِطْرَةِ الْإِسْلَامِ، وَعَلَى كَلِمَةِ الْإِخْلَاصِ، وَعَلَى دِينِ نَبِيِّنَا مُحَمَّدٍ صَلَّى اللَّهُ عَلَيْهِ وَسَلَّمَ، وَعَلَى مِلَّةِ أَبِينَا إِبْرَاهِيمَ حَنِيفًا مُسْلِمًا وَمَا كَانَ مِنَ الْمُشْرِكِينَ.",
                fadl = "الاستمساك بالعروة الوثقى وسنة الحنيفية عند إقبال الظلام.",
                source = "رواه أحمد وصححه الألباني",
                targetCount = 1,
                category = AzkarTimeCategory.EVENING,
                timeHint = "تثبيت الفطرة والتوحيد"
            ),
            AzkarItem(
                id = "e8",
                title = "الاستعاذة بكلمات الله التامات",
                text = "أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ.",
                fadl = "من قالها حين يمسي ثلاث مرات لم تضره حُمة ولا لدغة هوام تلك الليلة.",
                source = "رواه مسلم والترمذي",
                targetCount = 3,
                category = AzkarTimeCategory.EVENING,
                timeHint = "تكرر 3 مرات كل مساء (حصن ليلي)"
            ),
            AzkarItem(
                id = "e9",
                title = "بسم الله الذي لا يضر مع اسمه شيء",
                text = "بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ، وَهو السَّمِيعُ الْعَلِيمُ.",
                fadl = "«لم يضره شيء حتى يصبح».",
                source = "رواه أبو داود والترمذي وحسنه",
                targetCount = 3,
                category = AzkarTimeCategory.EVENING,
                timeHint = "تكرر 3 مرات لحفظ الأهل والنفس"
            ),
            AzkarItem(
                id = "e10",
                title = "رضيت بالله رباً وبالإسلام ديناً",
                text = "رَضِيتُ بِاللَّهِ رَبًّا، وَبِالْإِسْلَامِ دِينًا، وَبِمُحَمَّدٍ صَلَّى اللَّهُ عَلَيْهِ وَسَلَّمَ نَبِيًّا وَرَسُولًا.",
                fadl = "كان حقاً على الله تعالى أن يرضيه يوم القيامة.",
                source = "رواه الإمام أحمد وأبو داود",
                targetCount = 3,
                category = AzkarTimeCategory.EVENING,
                timeHint = "تكرر 3 مرات"
            ),
            AzkarItem(
                id = "e11",
                title = "دعاء العافية في البدن والسمع والبصر",
                text = "اللَّهُمَّ عَافِنِي فِي بَدَنِي، اللَّهُمَّ عَافِنِي فِي سَمْعِي، اللَّهُمَّ عَافِنِي فِي بَصَرِي، لَا إِلَهَ إِلَّا أَنْتَ. اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنَ الْكُفْرِ وَالْفَقْرِ، وَأَعُوذُ بِكَ مِنْ عَذَابِ الْقَبْرِ، لَا إِلَهَ إِلَّا أَنْتَ.",
                fadl = "دعاء نبوي مبارك لحفظ الصحة والسمع والبصر وعافية الجسد.",
                source = "رواه أبو داود وأحمد",
                targetCount = 3,
                category = AzkarTimeCategory.EVENING,
                timeHint = "تكرر 3 مرات مساءً"
            ),
            AzkarItem(
                id = "e12",
                title = "سؤال العفو والعافية والأمن من الروعات",
                text = "اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي الدُّنْيَا وَالْآخِرَةِ، اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي دِينِي وَدُنْيَايَ وَأَهْلِي وَمَالِي، اللَّهُمَّ اسْتُرْ عَوْرَاتِي، وَآمِنْ رَوْعَاتِي، اللَّهُمَّ احْفَظْنِي مِنْ بَيْنِ يَدَيَّ، وَمِنْ خَلْفِي، وَعَنْ يَمِينِي، وَعَنْ شِمَالِي، وَمِنْ فَوْقِي، وَأَعُوذُ بِعَظَمَتِكَ أَنْ أُغْتَالَ مِنْ تَحْتِي.",
                fadl = "حرز شامل يحرس العبد طيلة ليلته حتى يستيقظ في معافاة.",
                source = "رواه أبو داود وابن ماجه",
                targetCount = 1,
                category = AzkarTimeCategory.EVENING,
                timeHint = "تقال مرة واحدة"
            ),
            AzkarItem(
                id = "e13",
                title = "يا حي يا قيوم برحمتك أستغيث",
                text = "يَا حَيُّ يَا قَيُّومُ بِرَحْمَتِكَ أَسْتَغِيثُ، أَصْلِحْ لِي شَأْنِي كُلَّهُ، وَلَا تَكِلْنِي إِلَى نَفْسِي طَرْفَةَ عَيْنٍ.",
                fadl = "صلاح الأمور كلها وعدم التوكل على النفس البشرية الضعيفة.",
                source = "رواه النسائي والحاكم وصححه",
                targetCount = 1,
                category = AzkarTimeCategory.EVENING,
                timeHint = "تقال مرة واحدة"
            ),
            AzkarItem(
                id = "e14",
                title = "حسبي الله لا إله إلا هو",
                text = quranAyah(9, 129),
                fadl = "كفاه الله ما أهمه من أمر دنياه وآخرته.",
                source = "رواه أبو داود",
                targetCount = 7,
                category = AzkarTimeCategory.EVENING,
                timeHint = "تكرر 7 مرات"
            ),
            AzkarItem(
                id = "e15",
                title = "سبحان الله وبحمده (مئة مرة)",
                text = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ.",
                fadl = "حط الخطايا ورفعة الدرجات في الميزان يوم القيامة.",
                source = "رواه البخاري ومسلم",
                targetCount = 100,
                category = AzkarTimeCategory.EVENING,
                timeHint = "مئة تسبيحة نورانية"
            ),
            AzkarItem(
                id = "e16",
                title = "التهليل والتوحيد الجامع",
                text = "لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ، وَهو عَلَى كُلِّ شَيْءٍ قَدِيرٌ.",
                fadl = "حرز من الشيطان وعتق رقاب ومحو مئة سيئة وكتابة مئة حسنة.",
                source = "رواه البخاري ومسلم",
                targetCount = 100,
                category = AzkarTimeCategory.EVENING,
                timeHint = "تقال 100 مرة"
            ),
            AzkarItem(
                id = "e17",
                title = "الصلاة والسلام على نبينا محمد ﷺ",
                text = "اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَى نَبِيِّنَا مُحَمَّدٍ.",
                fadl = "«مَنْ صَلَّى عَلَيَّ حِينَ يُمْسِي عَشْرًا أَدْرَكَتْهُ شَفَاعَتِي يَوْمَ الْقِيَامَةِ».",
                source = "رواه الطبراني وحسنه الألباني",
                targetCount = 10,
                category = AzkarTimeCategory.EVENING,
                timeHint = "تكرر 10 مرات لنيل شفاعة الحبيب ﷺ"
            )
        )

        _sleepAzkar.value = listOf(
            AzkarItem(
                id = "s1",
                title = "آية الكرسي",
                text = quranAyah(2, 255),
                fadl = "من قرأها إذا أوى إلى فراشه لم يزل عليه من الله حافظ ولا يقربه شيطان حتى يصبح.",
                source = "رواه البخاري",
                targetCount = 1,
                category = AzkarTimeCategory.SLEEP,
                timeHint = "تقال عند النوم"
            ),
            AzkarItem(
                id = "s2",
                title = "باسمك ربي وضعت جنبي",
                text = "بِاسْمِكَ رَبِّي وَضَعْتُ جَنْبِي، وَبِكَ أَرْفَعُهُ، فَإِنْ أَمْسَكْتَ نَفْسِي فَارْحَمْهَا، وَإِنْ أَرْسَلْتَهَا فَاحْفَظْهَا بِمَا تَحْفَظُ بِهِ عِبَادَكَ الصَّالِحِينَ.",
                fadl = "من أذكار النوم المأثورة للوقاية والحفظ.",
                source = "متفق عليه",
                targetCount = 1,
                category = AzkarTimeCategory.SLEEP,
                timeHint = "تقال عند الاضطجاع"
            ),
            AzkarItem(
                id = "s3",
                title = "المعوذات (الإخلاص والفلق والناس)",
                text = quranSurahs(112, 113, 114),
                fadl = "كان النبي ﷺ إذا أوى إلى فراشه كل ليلة جمع كفيه ثم نفث فيهما فقرأ فيهما: {قل هو الله أحد} و {قل أعوذ برب الفلق} و {قل أعوذ برب الناس}، ثم يمسح بهما ما استطاع من جسده.",
                source = "رواه البخاري",
                targetCount = 3,
                category = AzkarTimeCategory.SLEEP,
                timeHint = "تقرأ وتمسح على الجسد 3 مرات"
            )
        )

        _wakingAzkar.value = listOf(
            AzkarItem(
                id = "w1",
                title = "الحمد لله الذي أحيانا",
                text = "الْحَمْدُ لِلَّهِ الَّذِي أَحْيَانَا بَعْدَ مَا أَمَاتَنَا وَإِلَيْهِ النُّشُورُ.",
                fadl = "شكر الله على نعمة الاستيقاظ وتجدد الحياة.",
                source = "رواه البخاري",
                targetCount = 1,
                category = AzkarTimeCategory.WAKING,
                timeHint = "تقال فور الاستيقاظ"
            )
        )

        _prayerAzkar.value = listOf(
            AzkarItem(
                id = "p1",
                title = "الاستغفار والتحميد بعد الصلاة",
                text = "أَسْتَغْفِرُ اللَّهَ (3 مرات). اللَّهُمَّ أَنْتَ السَّلَامُ وَمِنْكَ السَّلَامُ، تَبَارَكْتَ يَا ذَا الْجَلَالِ وَالْإِكْرَامِ.",
                fadl = "كان رسول الله ﷺ إذا انصرف من صلاته استغفر ثلاثاً.",
                source = "رواه مسلم",
                targetCount = 4,
                category = AzkarTimeCategory.AFTER_PRAYER,
                timeHint = "الاستغفار 3 مرات ثم دعاء السلام مرة"
            ),
            AzkarItem(
                id = "p2",
                title = "التسبيح والتحميد والتكبير",
                text = "سُبْحَانَ اللَّهِ (33 مرة)\nالْحَمْدُ لِلَّهِ (33 مرة)\nاللَّهُ أَكْبَرُ (33 مرة)\nلَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهو عَلَى كُلِّ شَيْءٍ قَدِيرٌ.",
                fadl = "من فعل ذلك غفرت خطاياه وإن كانت مثل زبد البحر.",
                source = "رواه مسلم",
                targetCount = 100,
                category = AzkarTimeCategory.AFTER_PRAYER,
                timeHint = "33 تسبيحة + 33 تحميدة + 33 تكبيرة + تهليلة = 100"
            )
        )

        _mosqueAzkar.value = listOf(
            AzkarItem(
                id = "mq1",
                title = "دعاء دخول المسجد",
                text = "بِسْمِ اللَّهِ، وَالصَّلَاةُ وَالسَّلَامُ عَلَى رَسُولِ اللَّهِ، اللَّهُمَّ افْتَحْ لِي أَبْوَابَ رَحْمَتِكَ.",
                fadl = "التماس الرحمة الإلهية عند دخول بيت من بيوت الله.",
                source = "رواه مسلم",
                targetCount = 1,
                category = AzkarTimeCategory.MOSQUE,
                timeHint = "تقال عند دخول المسجد بالرجل اليمنى"
            ),
            AzkarItem(
                id = "mq2",
                title = "دعاء الخروج من المسجد",
                text = "بِسْمِ اللَّهِ، وَالصَّلَاةُ وَالسَّلَامُ عَلَى رَسُولِ اللَّهِ، اللَّهُمَّ إِنِّي أَسْأَلُكَ مِنْ فَضْلِكَ.",
                fadl = "طلب الفضل والرزق عند الخروج للسعي في الأرض.",
                source = "رواه مسلم",
                targetCount = 1,
                category = AzkarTimeCategory.MOSQUE,
                timeHint = "تقال عند الخروج بالرجل اليسرى"
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AzkarScreen(
    onBack: () -> Unit,
    viewModel: AzkarViewModel = viewModel()
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val morningList by viewModel.morningAzkar.collectAsState()
    val eveningList by viewModel.eveningAzkar.collectAsState()
    val sleepList by viewModel.sleepAzkar.collectAsState()
    val wakingList by viewModel.wakingAzkar.collectAsState()
    val prayerList by viewModel.prayerAzkar.collectAsState()
    val mosqueList by viewModel.mosqueAzkar.collectAsState()
    val progressMap by viewModel.progressMap.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val speakingId by viewModel.speakingId.collectAsState()
    val ttsRate by viewModel.ttsRate.collectAsState()
    val rateLabel = when (ttsRate) {
        0.75f -> "0.75"
        1.0f -> "1"
        1.25f -> "1.25"
        1.5f -> "1.5"
        else -> ttsRate.toString()
    }

    // مغادرة الشاشة توقف الاستماع: بلا هذا يستمر الصوت بعد الانتقال
    // لتبويب آخر بلا أي زر إيقاف ظاهر — على نمط شاشتي الحسنات والحديث.
    DisposableEffect(Unit) {
        onDispose { viewModel.stopSpeaking() }
    }

    val morningCompleted = morningList.count { (progressMap[it.id] ?: 0) >= it.targetCount }
    val eveningCompleted = eveningList.count { (progressMap[it.id] ?: 0) >= it.targetCount }
    val sleepCompleted = sleepList.count { (progressMap[it.id] ?: 0) >= it.targetCount }
    val wakingCompleted = wakingList.count { (progressMap[it.id] ?: 0) >= it.targetCount }
    val prayerCompleted = prayerList.count { (progressMap[it.id] ?: 0) >= it.targetCount }
    val mosqueCompleted = mosqueList.count { (progressMap[it.id] ?: 0) >= it.targetCount }

    var todayKey by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var showResetAllConfirm by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }

    LaunchedEffect(Unit) {
        while (isActive) {
            val newKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            if (newKey != todayKey) {
                todayKey = newKey
                viewModel.refreshDailyProgress()
            }
            delay(60_000L)
        }
    }

    LaunchedEffect(morningCompleted, morningList.size) {
        if (morningList.isNotEmpty() && morningCompleted >= morningList.size) {
            prefs.edit().putBoolean("daily_morning_azkar_$todayKey", true).apply()
        }
    }

    LaunchedEffect(eveningCompleted, eveningList.size) {
        if (eveningList.isNotEmpty() && eveningCompleted >= eveningList.size) {
            prefs.edit().putBoolean("daily_evening_azkar_$todayKey", true).apply()
        }
    }

    fun copyZikrToClipboard(item: AzkarItem) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(
            "Zikr",
            "${item.title}\n\n${item.text}\n\nفضله: ${item.fadl}\nالمصدر: ${item.source}"
        )
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "تم نسخ الذكر إلى الحافظة", Toast.LENGTH_SHORT).show()
    }

    // ق83: البحث عبر المطبّع لا contains الخام — نصوص الأذكار
    // مشكولة («اللَّهُمَّ»، «سيّد») فاستعلام بلا تشكيل كان يعيد صفراً.
    val filteredMorning = remember(morningList, searchQuery) {
        morningList.filter {
            // ق125: البحث يشمل الفضل أيضاً كما يعد شريط البحث
            // («ابحث في الأذكار أو الفضائل») — كان العنوان والنص
            // وحدهما فيُعيد استعلام من الفضل صفراً.
            searchQuery.isBlank() || HadithSearchNormalizer.matches(it.title, searchQuery) || HadithSearchNormalizer.matches(it.text, searchQuery) || HadithSearchNormalizer.matches(it.fadl, searchQuery)
        }
    }
    val filteredEvening = remember(eveningList, searchQuery) {
        eveningList.filter {
            searchQuery.isBlank() || HadithSearchNormalizer.matches(it.title, searchQuery) || HadithSearchNormalizer.matches(it.text, searchQuery) || HadithSearchNormalizer.matches(it.fadl, searchQuery)
        }
    }
    val filteredSleep = remember(sleepList, searchQuery) {
        sleepList.filter {
            searchQuery.isBlank() || HadithSearchNormalizer.matches(it.title, searchQuery) || HadithSearchNormalizer.matches(it.text, searchQuery) || HadithSearchNormalizer.matches(it.fadl, searchQuery)
        }
    }
    val filteredWaking = remember(wakingList, searchQuery) {
        wakingList.filter {
            searchQuery.isBlank() || HadithSearchNormalizer.matches(it.title, searchQuery) || HadithSearchNormalizer.matches(it.text, searchQuery) || HadithSearchNormalizer.matches(it.fadl, searchQuery)
        }
    }
    val filteredPrayer = remember(prayerList, searchQuery) {
        prayerList.filter {
            searchQuery.isBlank() || HadithSearchNormalizer.matches(it.title, searchQuery) || HadithSearchNormalizer.matches(it.text, searchQuery) || HadithSearchNormalizer.matches(it.fadl, searchQuery)
        }
    }
    val filteredMosque = remember(mosqueList, searchQuery) {
        mosqueList.filter {
            searchQuery.isBlank() || HadithSearchNormalizer.matches(it.title, searchQuery) || HadithSearchNormalizer.matches(it.text, searchQuery) || HadithSearchNormalizer.matches(it.fadl, searchQuery)
        }
    }

    val anySearchMatches = filteredMorning.isNotEmpty() || filteredEvening.isNotEmpty() ||
        filteredSleep.isNotEmpty() || filteredWaking.isNotEmpty() ||
        filteredPrayer.isNotEmpty() || filteredMosque.isNotEmpty()

    // في تبويب «الكل» مع بحث: تُخفى الأقسام بلا نتائج، فلا يظهر عنوان
    // فارغ «0/ن» بلا بطاقات تحته.
    val showMorning = selectedTab == 0 || (selectedTab == 6 && (searchQuery.isBlank() || filteredMorning.isNotEmpty()))
    val showEvening = selectedTab == 1 || (selectedTab == 6 && (searchQuery.isBlank() || filteredEvening.isNotEmpty()))
    val showSleep = selectedTab == 2 || (selectedTab == 6 && (searchQuery.isBlank() || filteredSleep.isNotEmpty()))
    val showWaking = selectedTab == 3 || (selectedTab == 6 && (searchQuery.isBlank() || filteredWaking.isNotEmpty()))
    val showPrayer = selectedTab == 4 || (selectedTab == 6 && (searchQuery.isBlank() || filteredPrayer.isNotEmpty()))
    val showMosque = selectedTab == 5 || (selectedTab == 6 && (searchQuery.isBlank() || filteredMosque.isNotEmpty()))

    Scaffold(
        containerColor = theme.background,
        topBar = {
            AppleTopBar(
                title = "الأذكار اليومية",
                subtitle = "حصن المؤمن وبركة اليوم والسكينة",
                onBack = onBack,
                actions = {
                    TextButton(onClick = { viewModel.cycleTtsRate() }) {
                        Text(
                            text = "السرعة ${rateLabel}×",
                            color = theme.primary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    if (speakingId != null) {
                        IconButton(
                            onClick = { viewModel.stopSpeaking() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.VolumeOff,
                                contentDescription = "إيقاف القراءة الصوتية للأذكار",
                                tint = theme.primary
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    AzkarHeaderSummary(
                        morningTotal = morningList.size,
                        morningDone = morningCompleted,
                        eveningTotal = eveningList.size,
                        eveningDone = eveningCompleted,
                        sleepTotal = sleepList.size,
                        sleepDone = sleepCompleted,
                        wakingTotal = wakingList.size,
                        wakingDone = wakingCompleted,
                        prayerTotal = prayerList.size,
                        prayerDone = prayerCompleted,
                        mosqueTotal = mosqueList.size,
                        mosqueDone = mosqueCompleted,
                        onResetAll = { showResetAllConfirm = true }
                    )
                }

                item {
                    AzkarTabSelector(
                        selectedTab = selectedTab,
                        onSelectTab = { viewModel.setSelectedTab(it) }
                    )
                }

                item {
                    AzkarSearchBar(
                        query = searchQuery,
                        onQueryChange = { viewModel.setSearchQuery(it) }
                    )
                }

                if (searchQuery.isNotBlank() && !anySearchMatches) {
                    item { EmptySearchResultCard(query = searchQuery) }
                }

                // TAB 0: Morning
                if (showMorning) {
                    item(key = "header_morning") {
                        AzkarSectionHeaderBanner(
                            title = "أذكار الصباح",
                            subtitle = "حصن المؤمن وبركة اليوم",
                            icon = Icons.Default.WbSunny,
                            badgeColor = theme.warning,
                            totalCount = morningList.size,
                            completedCount = morningCompleted
                        )
                    }
                    if (selectedTab == 0 && filteredMorning.isEmpty() && searchQuery.isNotBlank()) {
                        item { EmptySearchResultCard(query = searchQuery, sectionTitle = "أذكار الصباح") }
                    } else {
                        items(filteredMorning, key = { "morning_${it.id}" }) { item ->
                            AzkarManuscriptCard(
                                item = item,
                                currentCount = progressMap[item.id] ?: 0,
                                isSpeaking = speakingId == item.id,
                                onIncrement = {
                                    if (viewModel.incrementCount(item.id, item.targetCount)) AppSoundManager.playDhikr()
                                },
                                onReset = { viewModel.resetCount(item.id) },
                                onDecrement = { viewModel.decrementCount(item.id) },
                                onSpeak = { viewModel.speakZikr(item, context) },
                                onShare = { copyZikrToClipboard(item) }
                            )
                        }
                    }
                }

                if (selectedTab == 6 && filteredMorning.isNotEmpty() && filteredEvening.isNotEmpty()) {
                    item(key = "sep_morning_evening") {
                        AzkarSectionSeparator()
                    }
                }

                // TAB 1: Evening
                if (showEvening) {
                    item(key = "header_evening") {
                        AzkarSectionHeaderBanner(
                            title = "أذكار المساء",
                            subtitle = "سكينة الليل وحفظ الرحمن",
                            icon = Icons.Default.NightsStay,
                            badgeColor = Color(0xFF5856D6),
                            totalCount = eveningList.size,
                            completedCount = eveningCompleted
                        )
                    }
                    if (selectedTab == 1 && filteredEvening.isEmpty() && searchQuery.isNotBlank()) {
                        item { EmptySearchResultCard(query = searchQuery, sectionTitle = "أذكار المساء") }
                    } else {
                        items(filteredEvening, key = { "evening_${it.id}" }) { item ->
                            AzkarManuscriptCard(
                                item = item,
                                currentCount = progressMap[item.id] ?: 0,
                                isSpeaking = speakingId == item.id,
                                onIncrement = {
                                    if (viewModel.incrementCount(item.id, item.targetCount)) AppSoundManager.playDhikr()
                                },
                                onReset = { viewModel.resetCount(item.id) },
                                onDecrement = { viewModel.decrementCount(item.id) },
                                onSpeak = { viewModel.speakZikr(item, context) },
                                onShare = { copyZikrToClipboard(item) }
                            )
                        }
                    }
                }

                // TAB 2: Sleep
                if (showSleep) {
                    item(key = "header_sleep") {
                        AzkarSectionHeaderBanner(
                            title = "أذكار النوم",
                            subtitle = "استيداع النفس لرب العالمين",
                            icon = Icons.Default.Bedtime,
                            badgeColor = theme.primary,
                            totalCount = sleepList.size,
                            completedCount = sleepCompleted
                        )
                    }
                    if (selectedTab == 2 && filteredSleep.isEmpty() && searchQuery.isNotBlank()) {
                        item { EmptySearchResultCard(query = searchQuery, sectionTitle = "أذكار النوم") }
                    } else {
                        items(filteredSleep, key = { "sleep_${it.id}" }) { item ->
                            AzkarManuscriptCard(
                                item = item,
                                currentCount = progressMap[item.id] ?: 0,
                                isSpeaking = speakingId == item.id,
                                onIncrement = {
                                    if (viewModel.incrementCount(item.id, item.targetCount)) AppSoundManager.playDhikr()
                                },
                                onReset = { viewModel.resetCount(item.id) },
                                onDecrement = { viewModel.decrementCount(item.id) },
                                onSpeak = { viewModel.speakZikr(item, context) },
                                onShare = { copyZikrToClipboard(item) }
                            )
                        }
                    }
                }

                // TAB 3: Waking
                if (showWaking) {
                    item(key = "header_waking") {
                        AzkarSectionHeaderBanner(
                            title = "أذكار الاستيقاظ",
                            subtitle = "شكر الله على تجدد الحياة",
                            icon = Icons.Default.WbTwilight,
                            badgeColor = theme.warning,
                            totalCount = wakingList.size,
                            completedCount = wakingCompleted
                        )
                    }
                    if (selectedTab == 3 && filteredWaking.isEmpty() && searchQuery.isNotBlank()) {
                        item { EmptySearchResultCard(query = searchQuery, sectionTitle = "أذكار الاستيقاظ") }
                    } else {
                        items(filteredWaking, key = { "waking_${it.id}" }) { item ->
                            AzkarManuscriptCard(
                                item = item,
                                currentCount = progressMap[item.id] ?: 0,
                                isSpeaking = speakingId == item.id,
                                onIncrement = {
                                    if (viewModel.incrementCount(item.id, item.targetCount)) AppSoundManager.playDhikr()
                                },
                                onReset = { viewModel.resetCount(item.id) },
                                onDecrement = { viewModel.decrementCount(item.id) },
                                onSpeak = { viewModel.speakZikr(item, context) },
                                onShare = { copyZikrToClipboard(item) }
                            )
                        }
                    }
                }

                // TAB 4: After Prayer
                if (showPrayer) {
                    item(key = "header_prayer") {
                        AzkarSectionHeaderBanner(
                            title = "أذكار بعد الصلاة",
                            subtitle = "تمام العبادة وزيادة الحسنات",
                            icon = Icons.Default.Mosque,
                            badgeColor = theme.success,
                            totalCount = prayerList.size,
                            completedCount = prayerCompleted
                        )
                    }
                    if (selectedTab == 4 && filteredPrayer.isEmpty() && searchQuery.isNotBlank()) {
                        item { EmptySearchResultCard(query = searchQuery, sectionTitle = "أذكار بعد الصلاة") }
                    } else {
                        items(filteredPrayer, key = { "prayer_${it.id}" }) { item ->
                            AzkarManuscriptCard(
                                item = item,
                                currentCount = progressMap[item.id] ?: 0,
                                isSpeaking = speakingId == item.id,
                                onIncrement = {
                                    if (viewModel.incrementCount(item.id, item.targetCount)) AppSoundManager.playDhikr()
                                },
                                onReset = { viewModel.resetCount(item.id) },
                                onDecrement = { viewModel.decrementCount(item.id) },
                                onSpeak = { viewModel.speakZikr(item, context) },
                                onShare = { copyZikrToClipboard(item) }
                            )
                        }
                    }
                }

                // TAB 5: Mosque
                if (showMosque) {
                    item(key = "header_mosque") {
                        AzkarSectionHeaderBanner(
                            title = "أذكار المسجد",
                            subtitle = "أدب الدخول والتعلق ببيوت الله",
                            icon = Icons.Default.MeetingRoom,
                            badgeColor = theme.accent,
                            totalCount = mosqueList.size,
                            completedCount = mosqueCompleted
                        )
                    }
                    if (selectedTab == 5 && filteredMosque.isEmpty() && searchQuery.isNotBlank()) {
                        item { EmptySearchResultCard(query = searchQuery, sectionTitle = "أذكار المسجد") }
                    } else {
                        items(filteredMosque, key = { "mosque_${it.id}" }) { item ->
                            AzkarManuscriptCard(
                                item = item,
                                currentCount = progressMap[item.id] ?: 0,
                                isSpeaking = speakingId == item.id,
                                onIncrement = {
                                    if (viewModel.incrementCount(item.id, item.targetCount)) AppSoundManager.playDhikr()
                                },
                                onReset = { viewModel.resetCount(item.id) },
                                onDecrement = { viewModel.decrementCount(item.id) },
                                onSpeak = { viewModel.speakZikr(item, context) },
                                onShare = { copyZikrToClipboard(item) }
                            )
                        }
                    }
                }

                // Footer Note
                item {
                    AppleCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "تحقيق الأذكار المعتمدة",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "جميع الأذكار منقولة بنصوصها المحققة وتشكيلها الكامل من الصحيحين ومسند الإمام أحمد والسنن المعتمدة، ليداوم عليها المسلم صباحاً ومساءً.",
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }
            }

            // تأكيد تصفير كل العدّادات: نقرة واحدة بلا تأكيد كانت تمسح
            // تقدّم اليوم كاملاً في الأقسام الستة بلا تراجع.
            if (showResetAllConfirm) {
                AlertDialog(
                    onDismissRequest = { showResetAllConfirm = false },
                    title = {
                        Text(
                            "تصفير كل عدّادات اليوم؟",
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            "سيُمسح تقدّمك في أذكار الصباح والمساء والنوم والاستيقاظ وبعد الصلاة والمسجد لهذا اليوم، ولا يمكن التراجع.",
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showResetAllConfirm = false
                                viewModel.resetAllCounters()
                            }
                        ) {
                            Text("تصفير الكل", color = theme.destructive, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetAllConfirm = false }) {
                            Text("إلغاء", color = theme.primary, fontFamily = CairoFont)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AzkarHeaderSummary(
    morningTotal: Int,
    morningDone: Int,
    eveningTotal: Int,
    eveningDone: Int,
    sleepTotal: Int,
    sleepDone: Int,
    wakingTotal: Int,
    wakingDone: Int,
    prayerTotal: Int,
    prayerDone: Int,
    mosqueTotal: Int,
    mosqueDone: Int,
    onResetAll: () -> Unit
) {
    val theme = QabasTheme.current
    AppleCard(cornerRadius = 18.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = theme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "حصن المسلم اليومي",
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            "طريق الحسنات والسكينة",
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 11.5.sp
                        )
                    }
                }

                TextButton(
                    onClick = onResetAll,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = theme.primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تصفير", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.secondarySurface.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(vertical = 12.dp, horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    QuranDataProvider.getVerseText(13, 28) ?: "افتح المصحف لقراءة الآية.",
                    color = theme.textPrimary,
                    fontFamily = AmiriFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    lineHeight = 32.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { ProgressPillCard("الصباح", morningDone, morningTotal, modifier = Modifier.width(134.dp)) }
                item { ProgressPillCard("المساء", eveningDone, eveningTotal, modifier = Modifier.width(134.dp)) }
                item { ProgressPillCard("النوم", sleepDone, sleepTotal, modifier = Modifier.width(134.dp)) }
                item { ProgressPillCard("الاستيقاظ", wakingDone, wakingTotal, modifier = Modifier.width(134.dp)) }
                item { ProgressPillCard("الصلاة", prayerDone, prayerTotal, modifier = Modifier.width(134.dp)) }
                item { ProgressPillCard("المسجد", mosqueDone, mosqueTotal, modifier = Modifier.width(134.dp)) }
            }
        }
    }
}

@Composable
fun ProgressPillCard(
    title: String,
    done: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
    val progress = if (total > 0) done.toFloat() / total.toFloat() else 0f
    Surface(
        modifier = modifier,
        color = theme.secondarySurface.copy(alpha = if (theme.isDark) 0.7f else 0.5f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                Text(
                    "$done/$total",
                    color = theme.primary,
                    fontFamily = RobotoMonoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape),
                color = theme.primary,
                trackColor = theme.separator.copy(alpha = 0.45f)
            )
        }
    }
}

@Composable
fun AzkarTabSelector(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    val theme = QabasTheme.current
    val tabs = remember {
        listOf(
            0 to "الصباح",
            1 to "المساء",
            2 to "النوم",
            3 to "الاستيقاظ",
            4 to "بعد الصلاة",
            5 to "المسجد",
            6 to "الكل"
        )
    }

    // ق140: الصف يتمرير للتبويب المختار — كان بلا حالة تمرير، فالتبويبات
    // الأخيرة («المسجد»، «الكل») خارج الشاشة في الهواتف الضيقة ولا يصلها
    // التمرير التلقائي عند اختيارها؛ والتمرير اليدوي لا يغيّر selectedTab
    // فلا يصارع هذا الأثرُ تمريرَ المستخدم (نمط ق138 نفسه).
    val tabRowState = rememberLazyListState()
    LaunchedEffect(selectedTab) {
        tabRowState.scrollToItem(selectedTab)
    }

    LazyRow(
        state = tabRowState,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tabs, key = { it.first }) { (index, title) ->
            val isSelected = selectedTab == index
            Surface(
                onClick = { onSelectTab(index) },
                modifier = Modifier.defaultMinSize(minHeight = 42.dp),
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) theme.primary else theme.cardSurface,
                border = BorderStroke(
                    0.75.dp,
                    if (isSelected) theme.primary else theme.separator.copy(alpha = 0.5f)
                )
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (isSelected) theme.onPrimary else theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun AzkarSearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    AppleSearchBar(
        query = query,
        onQueryChange = onQueryChange,
        placeholder = "ابحث في الأذكار أو الفضائل..."
    )
}

@Composable
fun AzkarSectionHeaderBanner(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeColor: Color,
    totalCount: Int,
    completedCount: Int
) {
    val theme = QabasTheme.current
    AppleCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = badgeColor.copy(alpha = 0.14f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = subtitle,
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 11.5.sp
                )
            }

            val allDone = completedCount >= totalCount && totalCount > 0
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (allDone) theme.success else theme.secondarySurface
            ) {
                Text(
                    text = "$completedCount / $totalCount",
                    color = if (allDone) Color.White else theme.textPrimary,
                    fontFamily = RobotoMonoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
fun AzkarSectionSeparator() {
    val theme = QabasTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = theme.separator.copy(alpha = 0.5f),
            thickness = 0.75.dp
        )
        Surface(
            modifier = Modifier.padding(horizontal = 12.dp),
            color = theme.cardSurface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.5f))
        ) {
            Text(
                "أذكار المساء",
                color = theme.textSecondary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
            )
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = theme.separator.copy(alpha = 0.5f),
            thickness = 0.75.dp
        )
    }
}

@Composable
fun AzkarManuscriptCard(
    item: AzkarItem,
    currentCount: Int,
    isSpeaking: Boolean,
    onIncrement: () -> Unit,
    onReset: () -> Unit,
    onDecrement: () -> Unit,
    onSpeak: () -> Unit,
    onShare: () -> Unit
) {
    val theme = QabasTheme.current
    val isDone = currentCount >= item.targetCount

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = theme.cardSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (isDone) 1.2.dp else 0.5.dp,
            color = if (isDone) theme.success else theme.separator.copy(alpha = 0.45f)
        ),
        shadowElevation = if (theme.isDark) 0.dp else 1.5.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = theme.primary.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = item.category.label,
                        color = theme.primary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Surface(
                    color = theme.secondarySurface.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "التكرار: ${item.targetCount} ${if (item.targetCount == 1) "مرة" else if (item.targetCount <= 10) "مرات" else "مرة"}",
                        color = theme.textSecondary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = item.title,
                color = theme.textPrimary,
                fontFamily = AmiriFont,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp
            )

            Text(
                text = item.timeHint,
                color = theme.primary,
                fontFamily = CairoFont,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        theme.secondarySurface.copy(alpha = if (theme.isDark) 0.55f else 0.35f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(14.dp)
            ) {
                Text(
                    text = item.text,
                    color = theme.textPrimary,
                    fontFamily = AmiriFont,
                    fontSize = 19.sp,
                    lineHeight = 34.sp,
                    textAlign = TextAlign.Start
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = theme.secondarySurface.copy(alpha = if (theme.isDark) 0.38f else 0.28f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "فضله وأثره",
                            color = theme.primary,
                            fontFamily = CairoFont,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.fadl,
                        color = theme.textPrimary,
                        fontFamily = NotoSansFont,
                        fontSize = 13.sp,
                        lineHeight = 21.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "المصدر: ${item.source}",
                        color = theme.textSecondary,
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = onSpeak,
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = if (isSpeaking) theme.primary else theme.secondarySurface
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isSpeaking) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                                contentDescription = "استماع للذكر",
                                tint = if (isSpeaking) theme.onPrimary else theme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = onShare,
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = theme.secondarySurface
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "نسخ الذكر",
                                tint = theme.textPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    if (currentCount > 0) {
                        Surface(
                            onClick = onDecrement,
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            color = theme.secondarySurface
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Undo,
                                    contentDescription = "التراجع عن آخر نقرة",
                                    tint = theme.textSecondary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                        Surface(
                            onClick = onReset,
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            color = theme.secondarySurface
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "إعادة البدء",
                                    tint = theme.textSecondary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }

                Surface(
                    onClick = onIncrement,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = if (isDone) theme.success else theme.primary,
                    shadowElevation = if (isDone || !theme.isDark) 2.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isDone) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "تم بحمد الله",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        } else {
                            Icon(
                                Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = theme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "$currentCount / ${item.targetCount}",
                                color = theme.onPrimary,
                                fontFamily = RobotoMonoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptySearchResultCard(query: String, sectionTitle: String? = null) {
    val theme = QabasTheme.current
    AppleCard(modifier = Modifier.padding(vertical = 12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.SearchOff,
                contentDescription = null,
                tint = theme.textSecondary,
                modifier = Modifier.size(38.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                // ق125: البطاقة مشتركة بين الفراغ العام وفراغ تبويب
                // واحد — كان نصها المطلق «لم يتم العثور على أذكار
                // تطابق» يظهر في تبويب بلا نتائج بينما توجد نتائج
                // في تبويبات أخرى؛ فصار النص مقيّداً بالقسم حين
                // يُمرَّر عنوانه، ويشير التلميح لتبويب «الكل».
                if (sectionTitle != null) "لم يتم العثور على أذكار تطابق \"$query\" في $sectionTitle"
                else "لم يتم العثور على أذكار تطابق: \"$query\"",
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (sectionTitle != null) "جرّب تبويب «الكل» للبحث في جميع الأذكار، أو كلمة بحث أخرى."
                else "يرجى تجربة كلمة بحث أخرى أو تصفح الأذكار عبر التبويبات أعلاه.",
                color = theme.textSecondary,
                fontFamily = NotoSansFont,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
