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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.qabas.app.R
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

class AzkarViewModel : ViewModel() {
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

    // Selected tab: 0 = Morning, 1 = Evening, 2 = Both (Separated)
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // TTS Speaking ID
    private val _speakingId = MutableStateFlow<String?>(null)
    val speakingId: StateFlow<String?> = _speakingId.asStateFlow()

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    init {
        loadAllAzkar()
    }

    fun initTts(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale("ar"))
                    isTtsReady = (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED)
                }
            }
        }
    }

    fun setSelectedTab(index: Int) {
        _selectedTab.value = index
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun incrementCount(id: String, target: Int) {
        val current = _progressMap.value[id] ?: 0
        if (current < target) {
            val next = current + 1
            _progressMap.value = _progressMap.value + (id to next)
        }
    }

    fun resetCount(id: String) {
        _progressMap.value = _progressMap.value + (id to 0)
    }

    fun resetAllCounters(category: AzkarTimeCategory? = null) {
        if (category == null) {
            _progressMap.value = emptyMap()
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
        }
    }

    fun speakZikr(item: AzkarItem, context: Context) {
        if (_speakingId.value == item.id) {
            stopSpeaking()
            return
        }
        initTts(context)
        if (isTtsReady && tts != null) {
            _speakingId.value = item.id
            val cleanText = item.text.replace("•", " ").trim()
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, item.id)
            tts?.setOnUtteranceCompletedListener {
                _speakingId.value = null
            }
        } else {
            Toast.makeText(context, "جاري تحضير المحرك الصوتي العربي...", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopSpeaking() {
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
                text = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ ۚ لَّهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ ۗ مَن ذَا الَّذِي يَشْفَعُ عِندَهُ إِلَّا بِإِذْنِهِ ۚ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ ۖ وَلَا يُحِيطُونَ بِشَيْءٍ مِّنْ عِلْمِهِ إِلَّا بِمَا شَاءَ ۚ وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالْأَرْضَ ۖ وَلَا يَئُودُهُ حِفْظُهُمَا ۚ وَهُوَ الْعَلِيُّ الْعَظِيمُ",
                fadl = "من قالها حين يصبح أُجير من الجن والشياطين حتى يمسي.",
                source = "سورة البقرة: 255 (صحيح الترغيب)",
                targetCount = 1,
                category = AzkarTimeCategory.MORNING,
                timeHint = "تقال صباحاً بعد صلاة الفجر"
            ),
            AzkarItem(
                id = "m2",
                title = "المعوذات الثلاث (الإخلاص والفلق والناس)",
                text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ\nقُلْ هُوَ اللَّهُ أَحَدٌ ﴿١﴾ اللَّهُ الصَّمَدُ ﴿٢﴾ لَمْ يَلِدْ وَلَمْ يُولَدْ ﴿٣﴾ وَلَمْ يَكُن لَّهُ كُفُوًا أَحَدٌ ﴿٤﴾\n\nبِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ\nقُلْ أَعُوذُ بِرَبِّ الْفَلَقِ ﴿١﴾ مِن شَرِّ مَا خَلَقَ ﴿٢﴾ وَمِن شَرِّ غَاسِقٍ إِذَا وَقَبَ ﴿٣﴾ وَمِن شَرِّ النَّفَّاثَاتِ فِي الْعُقَدِ ﴿٤﴾ وَمِن شَرِّ حَاسِدٍ إِذَا حَسَدَ ﴿٥﴾\n\nبِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ\nقُلْ أَعُوذُ بِرَبِّ النَّاسِ ﴿١﴾ مَلِكِ النَّاسِ ﴿٢﴾ إِلَٰهِ النَّاسِ ﴿٣﴾ مِن شَرِّ الْوَسْوَاسِ الْخَنَّاسِ ﴿٤﴾ الَّذِي يُوَسْوِسُ فِي صُدُورِ النَّاسِ ﴿٥﴾ مِنَ الْجِنَّةِ وَالنَّاسِ ﴿٦﴾",
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
                text = "حَسْبِيَ اللَّهُ لَا إِلَهَ إِلَّا هُوَ ۖ عَلَيْهِ تَوَكَّلْتُ ۖ وَهُوَ رَبُّ الْعَرْشِ الْعَظِيمِ.",
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
                targetCount = 10,
                category = AzkarTimeCategory.MORNING,
                timeHint = "تقال 10 مرات أو 100 مرة"
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
                text = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ ۚ لَّهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ ۗ مَن ذَا الَّذِي يَشْفَعُ عِندَهُ إِلَّا بِإِذْنِهِ ۚ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ ۖ وَلَا يُحِيطُونَ بِشَيْءٍ مِّنْ عِلْمِهِ إِلَّا بِمَا شَاءَ ۚ وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالْأَرْضَ ۖ وَلَا يَئُودُهُ حِفْظُهُمَا ۚ وَهو الْعَلِيُّ الْعَظِيمُ",
                fadl = "من قرأها إذا أمسى أُجير من الشياطين والجن حتى يصبح.",
                source = "سورة البقرة: 255 (صحيح الترغيب)",
                targetCount = 1,
                category = AzkarTimeCategory.EVENING,
                timeHint = "تقال مساءً بعد صلاة العصر أو المغرب"
            ),
            AzkarItem(
                id = "e2",
                title = "خواتيم سورة البقرة",
                text = "آمَنَ الرَّسُولُ بِمَا أُنزِلَ إِلَيْهِ مِن رَّبِّهِ وَالْمُؤْمِنُونَ ۚ كُلٌّ آمَنَ بِاللَّهِ وَمَلَائِكَتِهِ وَكُتُبِهِ وَرُسُلِهِ لَا نُفَرِّقُ بَيْنَ أَحَدٍ مِّن رُّسُلِهِ ۚ وَقَالُوا سَمِعْنَا وَأَطَعْنَا ۖ غُفْرَانَكَ رَبَّنَا وَإِلَيْكَ الْمَصِيرُ ﴿٢٨٥﴾ لَا يُكَلِّفُ اللَّهُ نَفْسًا إِلَّا وُسْعَهَا ۚ لَهَا مَا كَسَبَتْ وَعَلَيْهَا مَا اكْتَسَبَتْ ۗ رَبَّنَا لَا تُؤَاخِذْنَا إِن نَّسِينَا أَوْ أَخْطَأْنَا ۚ رَبَّنَا وَلَا تَحْمِلْ عَلَيْنَا إِصْرًا كَمَا حَمَلْتَهُ عَلَى الَّذِينَ مِن قَبْلِنَا ۚ رَبَّنَا وَلَا تُحَمِّلْنَا مَا لَا طَاقَةَ لَنَا بِهِ ۖ وَاعْفُ عَنَّا وَاغْفِرْ لَنَا وَارْحَمْنَا ۚ أَنتَ مَوْلَانَا فَانصُرْنَا عَلَى الْقَوْمِ الْكَافِرِينَ ﴿٢٨٦﴾",
                fadl = "قال رسول الله ﷺ: «مَنْ قَرَأَ بِالْآيَتَيْنِ مِنْ آخِرِ سُورَةِ الْبَقَرَةِ فِي لَيْلَةٍ كَفَتَاهُ».",
                source = "متفق عليه (البخاري ومسلم)",
                targetCount = 1,
                category = AzkarTimeCategory.EVENING,
                timeHint = "تقال كل ليلة؛ كفتاه من الشرور والآفات"
            ),
            AzkarItem(
                id = "e3",
                title = "المعوذات الثلاث (الإخلاص والفلق والناس)",
                text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ\nقُلْ هُوَ اللَّهُ أَحَدٌ ﴿١﴾ اللَّهُ الصَّمَدُ ﴿٢﴾ لَمْ يَلِدْ وَلَمْ يُولَدْ ﴿٣﴾ وَلَمْ يَكُن لَّهُ كُفُوًا أَحَدٌ ﴿٤﴾\n\nبِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ\nقُلْ أَعُوذُ بِرَبِّ الْفَلَقِ ﴿١﴾ مِن شَرِّ مَا خَلَقَ ﴿٢﴾ وَمِن شَرِّ غَاسِقٍ إِذَا وَقَبَ ﴿٣﴾ وَمِن شَرِّ النَّفَّاثَاتِ فِي الْعُقَدِ ﴿٤﴾ وَمِن شَرِّ حَاسِدٍ إِذَا حَسَدَ ﴿٥﴾\n\nبِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ\nقُلْ أَعُوذُ بِرَبِّ النَّاسِ ﴿١﴾ مَلِكِ النَّاسِ ﴿٢﴾ إِلَٰهِ النَّاسِ ﴿٣﴾ مِن شَرِّ الْوَسْوَاسِ الْخَنَّاسِ ﴿٤﴾ الَّذِي يُوَسْوِسُ فِي صُدُورِ النَّاسِ ﴿٥﴾ مِنَ الْجِنَّةِ وَالنَّاسِ ﴿٦﴾",
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
                text = "حَسْبِيَ اللَّهُ لَا إِلَهَ إِلَّا هُوَ ۖ عَلَيْهِ تَوَكَّلْتُ ۖ وَهو رَبُّ الْعَرْشِ الْعَظِيمِ.",
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
                targetCount = 10,
                category = AzkarTimeCategory.EVENING,
                timeHint = "تقال 10 مرات أو 100 مرة"
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
                text = "اللَّهُ لَا إِلَهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ ۚ لَّهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ ۗ مَن ذَا الَّذِي يَشْفَعُ عِندَهُ إِلَّا بِإِذْنِهِ ۚ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ ۖ وَلَا يُحِيطُونَ بِشَيْءٍ مِّنْ عِلْمِهِ إِلَّا بِمَا شَاءَ ۚ وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالْأَرْضَ ۖ وَلَا يَئُودُهُ حِفْظُهُمَا ۚ وَهو الْعَلِيُّ الْعَظِيمُ",
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
                text = "كان النبي ﷺ إذا أوى إلى فراشه كل ليلة جمع كفيه ثم نفث فيهما فقرأ فيهما: {قل هو الله أحد} و {قل أعوذ برب الفلق} و {قل أعوذ برب الناس}، ثم يمسح بهما ما استطاع من جسده.",
                fadl = "سنة نبوية للحفظ والبركة قبل النوم.",
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
                targetCount = 3,
                category = AzkarTimeCategory.AFTER_PRAYER,
                timeHint = "بعد التسليم من الصلاة مباشرة"
            ),
            AzkarItem(
                id = "p2",
                title = "التسبيح والتحميد والتكبير",
                text = "سُبْحَانَ اللَّهِ (33 مرة)\nالْحَمْدُ لِلَّهِ (33 مرة)\nاللَّهُ أَكْبَرُ (33 مرة)\nلَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهو عَلَى كُلِّ شَيْءٍ قَدِيرٌ.",
                fadl = "من فعل ذلك غفرت خطاياه وإن كانت مثل زبد البحر.",
                source = "رواه مسلم",
                targetCount = 33,
                category = AzkarTimeCategory.AFTER_PRAYER,
                timeHint = "تقال 33 مرة لكل ذكر ثم التهليل"
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

    // Counts completion
    val morningCompleted = morningList.count { (progressMap[it.id] ?: 0) >= it.targetCount }
    val eveningCompleted = eveningList.count { (progressMap[it.id] ?: 0) >= it.targetCount }
    val sleepCompleted = sleepList.count { (progressMap[it.id] ?: 0) >= it.targetCount }
    val wakingCompleted = wakingList.count { (progressMap[it.id] ?: 0) >= it.targetCount }
    val prayerCompleted = prayerList.count { (progressMap[it.id] ?: 0) >= it.targetCount }
    val mosqueCompleted = mosqueList.count { (progressMap[it.id] ?: 0) >= it.targetCount }

    Box(modifier = Modifier.fillMaxSize()) {
        // Manuscript Texture Background (Identical to ScholarBiographiesScreen)
        AsyncImage(
            model = R.drawable.scholar_manuscript_bg_1790092377371,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.15f
        )

        // Parchment Tint Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AntiqueParchment.copy(alpha = 0.88f))
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                "أذكار الصباح والمساء",
                                color = OldInk,
                                fontFamily = AmiriFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 26.sp
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "رجوع",
                                    tint = OldInk
                                )
                            }
                        },
                        actions = {
                            if (speakingId != null) {
                                IconButton(onClick = { viewModel.stopSpeaking() }) {
                                    Icon(
                                        Icons.Default.VolumeOff,
                                        contentDescription = "إيقاف القراءة",
                                        tint = ManuscriptGold
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                    )
                    // Decorative Gold Divider (Zakhrafa)
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 32.dp),
                        color = ManuscriptGold.copy(alpha = 0.5f),
                        thickness = 1.dp
                    )
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 40.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Intro & Spiritual Verse
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
                        onResetAll = { viewModel.resetAllCounters() }
                    )
                }

                // Separation & Tab Switcher (Styled like vintage leather / parchment tabs)
                item {
                    AzkarTabSelector(
                        selectedTab = selectedTab,
                        onSelectTab = { viewModel.setSelectedTab(it) }
                    )
                }

                // Search Bar
                item {
                    AzkarSearchBar(
                        query = searchQuery,
                        onQueryChange = { viewModel.setSearchQuery(it) }
                    )
                }

                // Filter lists based on search
                val filteredMorning = morningList.filter {
                    searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) || it.text.contains(searchQuery, ignoreCase = true)
                }
                val filteredEvening = eveningList.filter {
                    searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) || it.text.contains(searchQuery, ignoreCase = true)
                }
                val filteredSleep = sleepList.filter {
                    searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) || it.text.contains(searchQuery, ignoreCase = true)
                }
                val filteredWaking = wakingList.filter {
                    searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) || it.text.contains(searchQuery, ignoreCase = true)
                }
                val filteredPrayer = prayerList.filter {
                    searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) || it.text.contains(searchQuery, ignoreCase = true)
                }
                val filteredMosque = mosqueList.filter {
                    searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) || it.text.contains(searchQuery, ignoreCase = true)
                }

                // TAB 0: Morning
                if (selectedTab == 0 || selectedTab == 6) {
                    item {
                        AzkarSectionHeaderBanner(
                            title = "أذكار الصباح",
                            subtitle = "حصن المؤمن وبركة اليوم",
                            icon = Icons.Default.WbSunny,
                            badgeColor = ManuscriptGold,
                            totalCount = morningList.size,
                            completedCount = morningCompleted
                        )
                    }
                    items(filteredMorning) { item ->
                        AzkarManuscriptCard(
                            item = item,
                            currentCount = progressMap[item.id] ?: 0,
                            isSpeaking = speakingId == item.id,
                            onIncrement = {
                                AppSoundManager.playDhikr()
                                viewModel.incrementCount(item.id, item.targetCount)
                            },
                            onReset = { viewModel.resetCount(item.id) },
                            onSpeak = { viewModel.speakZikr(item, context) },
                            onShare = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Zikr", "${item.title}\n\n${item.text}\n\nفضله: ${item.fadl}\nالمصدر: ${item.source}")
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "تم نسخ الذكر إلى الحافظة ✨", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                // TAB 1: Evening
                if (selectedTab == 1 || selectedTab == 6) {
                    item {
                        AzkarSectionHeaderBanner(
                            title = "أذكار المساء",
                            subtitle = "سكينة الليل وحفظ الرحمن",
                            icon = Icons.Default.NightsStay,
                            badgeColor = Color(0xFF8B5CF6),
                            totalCount = eveningList.size,
                            completedCount = eveningCompleted
                        )
                    }
                    items(filteredEvening) { item ->
                        AzkarManuscriptCard(
                            item = item,
                            currentCount = progressMap[item.id] ?: 0,
                            isSpeaking = speakingId == item.id,
                            onIncrement = {
                                AppSoundManager.playDhikr()
                                viewModel.incrementCount(item.id, item.targetCount)
                            },
                            onReset = { viewModel.resetCount(item.id) },
                            onSpeak = { viewModel.speakZikr(item, context) },
                            onShare = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Zikr", "${item.title}\n\n${item.text}\n\nفضله: ${item.fadl}\nالمصدر: ${item.source}")
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "تم نسخ الذكر إلى الحافظة ✨", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                // TAB 2: Sleep
                if (selectedTab == 2 || selectedTab == 6) {
                    item {
                        AzkarSectionHeaderBanner(
                            title = "أذكار النوم",
                            subtitle = "استيداع النفس لرب العالمين",
                            icon = Icons.Default.Bedtime,
                            badgeColor = Color(0xFF1E293B),
                            totalCount = sleepList.size,
                            completedCount = sleepCompleted
                        )
                    }
                    items(filteredSleep) { item ->
                        AzkarManuscriptCard(
                            item = item,
                            currentCount = progressMap[item.id] ?: 0,
                            isSpeaking = speakingId == item.id,
                            onIncrement = {
                                AppSoundManager.playDhikr()
                                viewModel.incrementCount(item.id, item.targetCount)
                            },
                            onReset = { viewModel.resetCount(item.id) },
                            onSpeak = { viewModel.speakZikr(item, context) },
                            onShare = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Zikr", "${item.title}\n\n${item.text}\n\nفضله: ${item.fadl}\nالمصدر: ${item.source}")
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "تم نسخ الذكر إلى الحافظة ✨", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                // TAB 3: Waking
                if (selectedTab == 3 || selectedTab == 6) {
                    item {
                        AzkarSectionHeaderBanner(
                            title = "أذكار الاستيقاظ",
                            subtitle = "شكر الله على تجدد الحياة",
                            icon = Icons.Default.WbTwilight,
                            badgeColor = Color(0xFFF59E0B),
                            totalCount = wakingList.size,
                            completedCount = wakingCompleted
                        )
                    }
                    items(filteredWaking) { item ->
                        AzkarManuscriptCard(
                            item = item,
                            currentCount = progressMap[item.id] ?: 0,
                            isSpeaking = speakingId == item.id,
                            onIncrement = {
                                AppSoundManager.playDhikr()
                                viewModel.incrementCount(item.id, item.targetCount)
                            },
                            onReset = { viewModel.resetCount(item.id) },
                            onSpeak = { viewModel.speakZikr(item, context) },
                            onShare = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Zikr", "${item.title}\n\n${item.text}\n\nفضله: ${item.fadl}\nالمصدر: ${item.source}")
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "تم نسخ الذكر إلى الحافظة ✨", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                // TAB 4: After Prayer
                if (selectedTab == 4 || selectedTab == 6) {
                    item {
                        AzkarSectionHeaderBanner(
                            title = "أذكار بعد الصلاة",
                            subtitle = "تمام العبادة وزيادة الحسنات",
                            icon = Icons.Default.Mosque,
                            badgeColor = Color(0xFF10B981),
                            totalCount = prayerList.size,
                            completedCount = prayerCompleted
                        )
                    }
                    items(filteredPrayer) { item ->
                        AzkarManuscriptCard(
                            item = item,
                            currentCount = progressMap[item.id] ?: 0,
                            isSpeaking = speakingId == item.id,
                            onIncrement = {
                                AppSoundManager.playDhikr()
                                viewModel.incrementCount(item.id, item.targetCount)
                            },
                            onReset = { viewModel.resetCount(item.id) },
                            onSpeak = { viewModel.speakZikr(item, context) },
                            onShare = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Zikr", "${item.title}\n\n${item.text}\n\nفضله: ${item.fadl}\nالمصدر: ${item.source}")
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "تم نسخ الذكر إلى الحافظة ✨", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                // TAB 5: Mosque
                if (selectedTab == 5 || selectedTab == 6) {
                    item {
                        AzkarSectionHeaderBanner(
                            title = "أذكار المسجد",
                            subtitle = "أدب الدخول والتعلق ببيوت الله",
                            icon = Icons.Default.MeetingRoom,
                            badgeColor = Color(0xFF0EA5E9),
                            totalCount = mosqueList.size,
                            completedCount = mosqueCompleted
                        )
                    }
                    items(filteredMosque) { item ->
                        AzkarManuscriptCard(
                            item = item,
                            currentCount = progressMap[item.id] ?: 0,
                            isSpeaking = speakingId == item.id,
                            onIncrement = {
                                AppSoundManager.playDhikr()
                                viewModel.incrementCount(item.id, item.targetCount)
                            },
                            onReset = { viewModel.resetCount(item.id) },
                            onSpeak = { viewModel.speakZikr(item, context) },
                            onShare = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Zikr", "${item.title}\n\n${item.text}\n\nفضله: ${item.fadl}\nالمصدر: ${item.source}")
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "تم نسخ الذكر إلى الحافظة ✨", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                // Distinct Visual Separator when viewing "Both" (الفصل بين أذكار الصباح وأذكار المساء)
                if (selectedTab == 2) {
                    item {
                        AzkarSectionSeparator()
                    }
                }

                // TAB 1: Evening Remembrances (أذكار المساء)
                if (selectedTab == 1 || selectedTab == 2) {
                    item {
                        AzkarSectionHeaderBanner(
                            title = "أذكار المساء المباركة",
                            subtitle = "سكينة النفس وحفظ الليل • يُقال بعد صلاة العصر حتى غروب الشمس أو الليل",
                            icon = Icons.Default.NightsStay,
                            badgeColor = Color(0xFF935116),
                            totalCount = filteredEvening.size,
                            completedCount = eveningCompleted
                        )
                    }

                    if (filteredEvening.isEmpty()) {
                        item {
                            EmptySearchResultCard(query = searchQuery)
                        }
                    } else {
                        items(filteredEvening, key = { it.id }) { item ->
                            val currentCount = progressMap[item.id] ?: 0
                            AzkarManuscriptCard(
                                item = item,
                                currentCount = currentCount,
                                isSpeaking = speakingId == item.id,
                                onIncrement = { viewModel.incrementCount(item.id, item.targetCount) },
                                onReset = { viewModel.resetCount(item.id) },
                                onSpeak = { viewModel.speakZikr(item, context) },
                                onShare = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Zikr", "${item.title}\n\n${item.text}\n\nفضله: ${item.fadl}\nالمصدر: ${item.source}")
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "تم نسخ الذكر إلى الحافظة ✨", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                // Footer Note
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = AgedPaper.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = ManuscriptGold,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "تحقيق الأذكار المعتمدة",
                                color = ManuscriptGold,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "جميع الأذكار منقولة بنصوصها المحققة وتشكيلها الكامل من الصحيحين ومسند الإمام أحمد والسنن المعتمدة، ليداوم عليها المسلم صباحاً ومساءً.",
                                color = OldInk.copy(alpha = 0.75f),
                                fontFamily = NotoSansFont,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Top Summary card with Islamic verse and progress counters
 */
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AgedPaper,
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 32.dp, bottomStart = 32.dp, bottomEnd = 4.dp),
        border = BorderStroke(1.5.dp, ManuscriptGold.copy(alpha = 0.35f)),
        shadowElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = ManuscriptGold.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, ManuscriptGold),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = ManuscriptGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "حصن المسلم اليومي",
                            color = ManuscriptGold,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            "طريق الحسنات والسكينة",
                            color = OldInk.copy(alpha = 0.65f),
                            fontFamily = NotoSansFont,
                            fontSize = 12.sp
                        )
                    }
                }

                TextButton(
                    onClick = onResetAll,
                    colors = ButtonDefaults.textButtonColors(contentColor = OldInk)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تصفير العدادات", fontFamily = CairoFont, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quranic ayah
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AntiqueParchment.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .border(1.dp, ManuscriptGold.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(vertical = 10.dp, horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "﴿ أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ ﴾",
                    color = OldInk,
                    fontFamily = AmiriFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Progress counters (Scrollable Row)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { ProgressPillCard("الصباح", "☀️", morningDone, morningTotal, modifier = Modifier.width(140.dp)) }
                item { ProgressPillCard("المساء", "🌙", eveningDone, eveningTotal, modifier = Modifier.width(140.dp)) }
                item { ProgressPillCard("النوم", "😴", sleepDone, sleepTotal, modifier = Modifier.width(140.dp)) }
                item { ProgressPillCard("الاستيقاظ", "🌅", wakingDone, wakingTotal, modifier = Modifier.width(140.dp)) }
                item { ProgressPillCard("الصلاة", "🕌", prayerDone, prayerTotal, modifier = Modifier.width(140.dp)) }
                item { ProgressPillCard("المسجد", "🕋", mosqueDone, mosqueTotal, modifier = Modifier.width(140.dp)) }
            }
        }
    }
}

@Composable
fun ProgressPillCard(
    title: String,
    icon: String,
    done: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val progress = if (total > 0) done.toFloat() / total.toFloat() else 0f
    Surface(
        modifier = modifier,
        color = AntiqueParchment.copy(alpha = 0.6f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$icon $title",
                    color = OldInk,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    "$done / $total",
                    color = ManuscriptGold,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = ManuscriptGold,
                trackColor = OldInk.copy(alpha = 0.1f)
            )
        }
    }
}

/**
 * Tab switcher with distinct morning/evening tabs + both separated view
 */
@Composable
fun AzkarTabSelector(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AgedPaper,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, ManuscriptGold.copy(alpha = 0.35f))
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val tabs = listOf(
                Triple(0, "الصباح", "☀️"),
                Triple(1, "المساء", "🌙"),
                Triple(2, "النوم", "😴"),
                Triple(3, "الاستيقاظ", "🌅"),
                Triple(4, "الصلاة", "🕌"),
                Triple(5, "المسجد", "🕋"),
                Triple(6, "الفهرس الكامل", "📜")
            )

            items(tabs) { (index, title, icon) ->
                val isSelected = selectedTab == index
                Surface(
                    modifier = Modifier
                        .clickable { onSelectTab(index) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) ManuscriptGold else Color.Transparent,
                    border = if (isSelected) BorderStroke(1.dp, ManuscriptGold) else null
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$icon $title",
                            color = if (isSelected) Color.White else OldInk,
                            fontFamily = CairoFont,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Vintage parchment styled search bar
 */
@Composable
fun AzkarSearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                "ابحث في الأذكار أو الفضائل (مثال: الاستغفار، الكرسي، فطرة)...",
                color = OldInk.copy(alpha = 0.5f),
                fontFamily = NotoSansFont,
                fontSize = 13.sp
            )
        },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = ManuscriptGold)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "مسح", tint = OldInk.copy(alpha = 0.6f))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = AgedPaper.copy(alpha = 0.7f),
            unfocusedContainerColor = AgedPaper.copy(alpha = 0.5f),
            focusedBorderColor = ManuscriptGold,
            unfocusedBorderColor = ManuscriptGold.copy(alpha = 0.3f),
            focusedTextColor = OldInk,
            unfocusedTextColor = OldInk
        )
    )
}

/**
 * Banner header for morning and evening sections
 */
@Composable
fun AzkarSectionHeaderBanner(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeColor: Color,
    totalCount: Int,
    completedCount: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AgedPaper,
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 4.dp),
        border = BorderStroke(1.5.dp, ManuscriptGold.copy(alpha = 0.4f)),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = badgeColor.copy(alpha = 0.15f),
                border = BorderStroke(2.dp, badgeColor)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = OldInk,
                    fontFamily = AmiriFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = OldInk.copy(alpha = 0.7f),
                    fontFamily = NotoSansFont,
                    fontSize = 11.5.sp
                )
            }

            // Status chip
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (completedCount >= totalCount && totalCount > 0) ManuscriptGold else AntiqueParchment,
                border = BorderStroke(1.dp, ManuscriptGold)
            ) {
                Text(
                    text = "$completedCount / $totalCount",
                    color = if (completedCount >= totalCount && totalCount > 0) Color.White else OldInk,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Islamic decorative separator between Morning and Evening sections
 */
@Composable
fun AzkarSectionSeparator() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = ManuscriptGold.copy(alpha = 0.4f),
                thickness = 1.dp
            )
            Surface(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = AgedPaper,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, ManuscriptGold)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("☀️", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "فصل بين أذكار الصباح والمساء",
                        color = ManuscriptGold,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("🌙", fontSize = 14.sp)
                }
            }
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = ManuscriptGold.copy(alpha = 0.4f),
                thickness = 1.dp
            )
        }
    }
}

/**
 * The Azkar Card: Perfectly matching the visual design, shape, and colors of ScholarBiographyCard
 */
@Composable
fun AzkarManuscriptCard(
    item: AzkarItem,
    currentCount: Int,
    isSpeaking: Boolean,
    onIncrement: () -> Unit,
    onReset: () -> Unit,
    onSpeak: () -> Unit,
    onShare: () -> Unit
) {
    val isDone = currentCount >= item.targetCount

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AgedPaper,
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 36.dp, bottomStart = 36.dp, bottomEnd = 4.dp),
        border = BorderStroke(
            width = if (isDone) 2.dp else 1.5.dp,
            color = if (isDone) ManuscriptGold else ManuscriptGold.copy(alpha = 0.35f)
        ),
        shadowElevation = if (isDone) 5.dp else 3.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header: Category, source, and repetition badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Category Chip
                Surface(
                    color = ManuscriptGold.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "${item.category.icon} ${item.category.label}",
                        color = ManuscriptGold,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                // Target Count Chip
                Surface(
                    color = AntiqueParchment,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "التكرار: ${item.targetCount} ${if (item.targetCount == 1) "مرة" else if (item.targetCount <= 10) "مرات" else "مرة"}",
                        color = OldInk,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dhikr Title
            Text(
                text = item.title,
                color = OldInk,
                fontFamily = AmiriFont,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )

            // Time note
            Text(
                text = item.timeHint,
                color = ManuscriptGold,
                fontFamily = CairoFont,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Main Text (Authentic Arabic with Tashkeel in Amiri font)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AntiqueParchment.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .border(1.dp, ManuscriptGold.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = item.text,
                    color = OldInk,
                    fontFamily = AmiriFont,
                    fontSize = 19.sp,
                    lineHeight = 34.sp,
                    textAlign = TextAlign.Start
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Virtue & Reward Box (Styled like "محنة وثبات الأجداد" in ScholarBiographiesScreen)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = AntiqueParchment.copy(alpha = 0.7f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = ManuscriptGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "فضله وأثره في الحفظ والسكينة",
                            color = ManuscriptGold,
                            fontFamily = CairoFont,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.fadl,
                        color = OldInk,
                        fontFamily = NotoSansFont,
                        fontSize = 13.5.sp,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "المصدر: ${item.source}",
                        color = OldInk.copy(alpha = 0.65f),
                        fontFamily = CairoFont,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Interactive Bottom Row: Counter (Antique Seal Button) + Audio TTS + Share + Reset
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Secondary actions: Audio Listen, Share, Reset
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Audio Speech Button
                    Surface(
                        onClick = onSpeak,
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        color = if (isSpeaking) ManuscriptGold else AntiqueParchment,
                        border = BorderStroke(1.dp, ManuscriptGold)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isSpeaking) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                                contentDescription = "استماع للذكر",
                                tint = if (isSpeaking) Color.White else ManuscriptGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Share / Copy Button
                    Surface(
                        onClick = onShare,
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        color = AntiqueParchment,
                        border = BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.4f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "نسخ الذكر",
                                tint = OldInk.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (currentCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        // Reset button
                        Surface(
                            onClick = onReset,
                            modifier = Modifier.size(42.dp),
                            shape = CircleShape,
                            color = AntiqueParchment,
                            border = BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.4f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "إعادة البدء",
                                    tint = OldInk.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Interactive Counter Seal (Stamp Button)
                Surface(
                    onClick = onIncrement,
                    modifier = Modifier
                        .height(50.dp)
                        .clip(RoundedCornerShape(25.dp)),
                    shape = RoundedCornerShape(25.dp),
                    color = if (isDone) ManuscriptGold else Color.White.copy(alpha = 0.85f),
                    border = BorderStroke(2.dp, ManuscriptGold),
                    shadowElevation = if (isDone) 6.dp else 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isDone) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
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
                                tint = ManuscriptGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "$currentCount / ${item.targetCount}",
                                color = OldInk,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptySearchResultCard(query: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        color = AgedPaper.copy(alpha = 0.7f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ManuscriptGold.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.SearchOff,
                contentDescription = null,
                tint = ManuscriptGold,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "لم يتم العثور على أذكار تطابق: \"$query\"",
                color = OldInk,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "يرجى تجربة كلمة بحث أخرى أو تصفح الأذكار عبر التبويبات أعلاه.",
                color = OldInk.copy(alpha = 0.7f),
                fontFamily = NotoSansFont,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
