package com.qabas.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * بطاقة اليوم + «أكمل من حيث توقفت» + البحث العام في الرئيسية (الدفعة 3).
 *
 * قواعد صريحة:
 * - لا رقم صفحة قرآن مختلق: آخر موضع قراءة يأتي فقط مما سجّله قارئ
 *   المصحف (UserPreferencesManager.getLastReadQuran)؛ وبدونه تعرض البطاقة
 *   حالة ورد اليوم (daily_wird_*) لا رقم صفحة.
 * - تأكيد أداء الصلاة إدخال مستخدم فقط (مفتاح daily_prayer_done_* في
 *   qabas_prefs)؛ لا تعبئة تلقائية أبداً.
 * - كل جزء غير متاح يُحذف من الواجهة لا يُختلق بديله.
 */

private fun todayKey(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

/** حديث اليوم: من hadith_presets أولاً، ورجوع محلي للصحيحين دون اتصال. */
data class HomeHadithOfDay(
    val title: String,
    val text: String,
    val source: String
)

/** حالة بطاقة اليوم؛ تُحدَّث من التفضيلات المحلية والسحابة. */
class HomeTodayState(private val appContext: Context) {
    var nextPrayerName by mutableStateOf("")
        private set
    var nextPrayerTime by mutableStateOf("")
        private set
    var nextPrayerCountdown by mutableStateOf("")
        private set
    var verseOfDay by mutableStateOf<QuranVerseDetail?>(null)
        private set
    var hadithOfDay by mutableStateOf<HomeHadithOfDay?>(null)
        private set
    var prayedToday by mutableStateOf(false)
        private set
    var morningAzkarDone by mutableStateOf(false)
        private set
    var eveningAzkarDone by mutableStateOf(false)
        private set
    var wirdDone by mutableStateOf(false)
        private set
    var tasbeehCount by mutableStateOf(0)
        private set
    /** آخر موضع قراءة في المصحف (الدفعة 5 — الجزء C)؛ null إن لم يُسجَّل قط. */
    var lastRead by mutableStateOf<UserPreferencesManager.LastReadQuran?>(null)
        private set

    val azkarDone: Boolean get() = morningAzkarDone && eveningAzkarDone
    val goalDoneCount: Int
        get() = listOf(prayedToday, azkarDone, wirdDone).count { it }
    val goalProgress: Float get() = goalDoneCount / 3f

    /** تسمية ورد اليوم (تُعرض عند غياب آخر قراءة مسجلة). */
    val wirdLabel: String
        get() = if (wirdDone) "ورد اليوم مكتمل" else "ورد اليوم بانتظارك"

    /** تسمية آخر قراءة مسجلة، أو null لعرض حالة الورد بدلها. */
    val lastReadLabel: String?
        get() = lastRead?.let { read ->
            val surahName = QuranDataProvider.surahNameOf(read.surah)
            if (read.page != null) "المصحف — سورة $surahName، صفحة ${read.page}"
            else "المصحف — سورة $surahName، آية ${read.ayah}"
        }

    fun updatePrayerCountdown() {
        runCatching {
            val city = PrayerCalculationHelper.getSavedCity(appContext)
            val cal = Calendar.getInstance()
            val times = PrayerCalculationHelper.calculatePrayerTimesForUser(
                appContext, cal, city
            )
            nextPrayerName = times.nextPrayerName
            nextPrayerTime = times.nextPrayerTimeFormatted
            val totalSec = (times.millisUntilNextPrayer / 1000L).coerceAtLeast(0L)
            val h = totalSec / 3600L
            val m = (totalSec % 3600L) / 60L
            val s = totalSec % 60L
            nextPrayerCountdown = String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
        }
    }

    /** قراءة أعلام اليوم من qabas_prefs (نفس مفاتيح المشكاة والأذكار). */
    fun refreshFlags() {
        val prefs = appContext.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val key = todayKey()
        prayedToday = prefs.getBoolean("daily_prayer_done_$key", false)
        morningAzkarDone = prefs.getBoolean("daily_morning_azkar_$key", false)
        eveningAzkarDone = prefs.getBoolean("daily_evening_azkar_$key", false)
        wirdDone = prefs.getBoolean("daily_wird_$key", false)
        lastRead = UserPreferencesManager.getLastReadQuran(appContext)
        tasbeehCount = if (prefs.getString("mishkat_tasbeeh_date", key) != key) {
            prefs.edit().putInt("mishkat_tasbeeh_count", 0)
                .putString("mishkat_tasbeeh_date", key).apply()
            0
        } else {
            prefs.getInt("mishkat_tasbeeh_count", 0)
        }
        updatePrayerCountdown()
    }

    /** تأكيد أداء الصلاة (إدخال مستخدم فقط). */
    fun confirmPrayerDone() {
        val prefs = appContext.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("daily_prayer_done_${todayKey()}", true).apply()
        prayedToday = true
    }

    /** التراجع عن تأكيد أداء الصلاة (ضغطة خاطئة لا تُحبس ليوم كامل). */
    fun unconfirmPrayerDone() {
        val prefs = appContext.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("daily_prayer_done_${todayKey()}").apply()
        prayedToday = false
    }

    /** زيادة عدّاد التسبيح (مفاتيح المشكاة نفسها). */
    fun incrementTasbeeh() {
        val prefs = appContext.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val key = todayKey()
        if (prefs.getString("mishkat_tasbeeh_date", key) != key) {
            prefs.edit().putInt("mishkat_tasbeeh_count", 1)
                .putString("mishkat_tasbeeh_date", key).apply()
            tasbeehCount = 1
        } else {
            val next = (prefs.getInt("mishkat_tasbeeh_count", 0) + 1)
            prefs.edit().putInt("mishkat_tasbeeh_count", next).apply()
            tasbeehCount = next
        }
    }

    /** تحميل آية اليوم وحديث اليوم (آية من uthmani.json، وحديث من السحابة أو محلياً). */
    suspend fun loadDailyContent() {
        val (verse, hadith) = withContext(Dispatchers.IO) {
            runCatching { QuranDataProvider.ensureQuranLoaded(appContext) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            val loadedVerse = runCatching { QuranDataProvider.getVerseOfDay() }.getOrNull()
            val presets = runCatching {
                SupabaseServices.Database.getHadithPresets()
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull().orEmpty()
            val loadedHadith = if (presets.isNotEmpty()) {
                val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                val preset = presets[dayOfYear % presets.size]
                HomeHadithOfDay(title = preset.title, text = preset.text, source = preset.source)
            } else {
                runCatching {
                    val local = SahihHadithsRepository.getRandomDailyHadith()
                    HomeHadithOfDay(title = local.title, text = local.matn, source = local.reference)
                }.getOrNull()
            }
            loadedVerse to loadedHadith
        }
        verseOfDay = verse
        hadithOfDay = hadith
    }
}

@Composable
fun rememberHomeTodayState(): HomeTodayState {
    val context = LocalContext.current
    val state = remember(context) { HomeTodayState(context.applicationContext) }
    LaunchedEffect(state) {
        state.refreshFlags()
        state.loadDailyContent()
        // review-r13: العدّاد وحده كان يتحدث كل ثانية، أما أعلام اليوم
        // (صلاة/أذكار/ورد/تسبيح) وآية اليوم وحديث اليوم فكانت تتجمد على يوم
        // أمس إن بقي التطبيق مفتوحاً بعد منتصف الليل. الآن يُكتشف تبدّل
        // التاريخ في الحلقة نفسها فتُحدَّث الأعلام والمحتوى اليومي معاً.
        var currentDayKey = todayKey()
        while (true) {
            delay(1000L)
            state.updatePrayerCountdown()
            val newDayKey = todayKey()
            if (newDayKey != currentDayKey) {
                currentDayKey = newDayKey
                state.refreshFlags()
                state.loadDailyContent()
            }
        }
    }
    return state
}

/** بطاقة اليوم: الصلاة القادمة بعدّادها + آية اليوم + حديث اليوم + هدف اليوم. */
@Composable
fun HomeTodayCard(
    state: HomeTodayState,
    onOpenPrayerTimes: () -> Unit = {},
    onOpenAzkar: () -> Unit = {},
    onOpenMishkatHub: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
    AppleCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "بطاقة اليوم",
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = SimpleDateFormat("EEEE، d MMMM", Locale("ar")).format(Date()),
                    color = theme.textSecondary,
                    fontFamily = CairoFont,
                    fontSize = 12.sp
                )
            }

            // الصلاة القادمة بعدّادها الحالي (حساب فلكي محلي).
            if (state.nextPrayerName.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPrayerTimes() }
                        .background(
                            color = theme.primary.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "الصلاة القادمة: ${state.nextPrayerName}",
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "الأذان ${state.nextPrayerTime}",
                            color = theme.textSecondary,
                            fontFamily = CairoFont,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = state.nextPrayerCountdown,
                        color = theme.primary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            // آية اليوم (من uthmani.json نفسه عبر QuranDataProvider).
            state.verseOfDay?.let { verse ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "آية اليوم",
                        color = theme.primary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = verse.verseText,
                        color = theme.textPrimary,
                        fontFamily = AmiriFont,
                        fontSize = 19.sp,
                        lineHeight = 32.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "سورة ${verse.surahName} • الآية ${verse.verseNumber}",
                        color = theme.textSecondary,
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // حديث اليوم (من hadith_presets، ورجوع محلي للصحيحين).
            state.hadithOfDay?.let { hadith ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "حديث اليوم",
                        color = theme.primary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = hadith.title,
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = hadith.text,
                        color = theme.textPrimary,
                        fontFamily = AmiriFont,
                        fontSize = 16.sp,
                        lineHeight = 28.sp
                    )
                    Text(
                        text = hadith.source,
                        color = theme.textSecondary,
                        fontFamily = CairoFont,
                        fontSize = 12.sp
                    )
                }
            }

            // هدف اليوم: صلاة + أذكار + ورد، مع زر تأكيد أداء الصلاة.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "هدف اليوم",
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${state.goalDoneCount} من 3",
                        color = theme.textSecondary,
                        fontFamily = CairoFont,
                        fontSize = 12.sp
                    )
                }
                LinearProgressIndicator(
                    progress = { state.goalProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = theme.primary,
                    trackColor = theme.primary.copy(alpha = 0.15f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HomeGoalChip(
                        label = "الصلاة",
                        done = state.prayedToday,
                        modifier = Modifier.weight(1f),
                        // review-r13: كان الشريحة الوحيدة بلا نقر بين أخواتها
                        // (الأذكار والورد يفتحان قسميهما) — الآن تفتح المواقيت.
                        onClick = onOpenPrayerTimes
                    )
                    HomeGoalChip(
                        label = "الأذكار",
                        done = state.azkarDone,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenAzkar
                    )
                    HomeGoalChip(
                        label = "الورد",
                        done = state.wirdDone,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenMishkatHub
                    )
                }
                if (!state.prayedToday) {
                    OutlinedButton(
                        onClick = { state.confirmPrayerDone() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("تأكيد أداء الصلاة", fontFamily = CairoFont, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    // review-r13: التأكيد كان بلا تراجع — ضغطة خاطئة تُحبس
                    // ليوم كامل. الآن النص نفسه زر تراجع.
                    TextButton(
                        onClick = { state.unconfirmPrayerDone() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF86D6A6))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "تم تأكيد أداء الصلاة لهذا اليوم — اضغط للتراجع",
                            color = Color(0xFF86D6A6),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeGoalChip(
    label: String,
    done: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val theme = QabasTheme.current
    val chipModifier = if (onClick != null) modifier.clickable { onClick() } else modifier
    Row(
        modifier = chipModifier
            .background(
                color = if (done) Color(0xFF86D6A6).copy(alpha = 0.18f)
                else theme.primary.copy(alpha = 0.08f),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (done) Color(0xFF86D6A6) else theme.textSecondary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            color = theme.textPrimary,
            fontFamily = CairoFont,
            fontSize = 13.sp,
            fontWeight = if (done) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/** صف «أكمل من حيث توقفت»: آخر مشروع + أذكار اليوم + تسبيح + آخر قراءة. */
@Composable
fun HomeContinueRow(
    state: HomeTodayState,
    recentProjects: List<ProjectService.Project>,
    onOpenProject: (ProjectService.Project) -> Unit,
    onOpenMishkatHub: () -> Unit = {},
    onOpenAzkar: () -> Unit = {},
    onOpenMushaf: (Int, Int, Int?) -> Unit = { _, _, _ -> onOpenMishkatHub() },
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
    val lastProject = recentProjects.firstOrNull()
    val lastRead = state.lastRead
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "أكمل من حيث توقفت",
            color = theme.textPrimary,
            fontFamily = CairoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            if (lastProject != null) {
                item {
                    HomeContinueChip(
                        icon = Icons.Default.VideoLibrary,
                        title = lastProject.title.ifBlank { "مشروع بلا عنوان" },
                        subtitle = "آخر مشروع",
                        onClick = { onOpenProject(lastProject) }
                    )
                }
            }
            item {
                if (lastRead != null) {
                    HomeContinueChip(
                        icon = Icons.Default.MenuBook,
                        title = state.lastReadLabel ?: state.wirdLabel,
                        subtitle = "أكمل من حيث توقفت",
                        onClick = { onOpenMushaf(lastRead.surah, lastRead.ayah, lastRead.page) }
                    )
                } else {
                    HomeContinueChip(
                        icon = Icons.Default.MenuBook,
                        title = state.wirdLabel,
                        subtitle = "القرآن الكريم",
                        onClick = onOpenMishkatHub
                    )
                }
            }
            item {
                HomeContinueChip(
                    icon = Icons.Default.AutoStories,
                    title = if (state.azkarDone) "أذكار اليوم مكتملة"
                    else if (state.morningAzkarDone || state.eveningAzkarDone) "أذكار اليوم جزئياً"
                    else "أذكار اليوم",
                    subtitle = "الصباح والمساء",
                    onClick = onOpenAzkar
                )
            }
            item {
                HomeContinueChip(
                    icon = Icons.Default.Fingerprint,
                    title = "التسبيح: ${state.tasbeehCount} / 33",
                    subtitle = "اضغط للزيادة",
                    onClick = { state.incrementTasbeeh() }
                )
            }
        }
    }
}

@Composable
private fun HomeContinueChip(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val theme = QabasTheme.current
    AppleCard(modifier = Modifier.width(220.dp).clickable { onClick() }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = theme.primary, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = theme.textSecondary,
                    fontFamily = CairoFont,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ==========================================================================
// البحث العام
// ==========================================================================

private data class HomeSearchResults(
    val quran: List<QuranVerseDetail> = emptyList(),
    val hadith: List<SahihHadithItem> = emptyList(),
    val library: List<IslamicBook> = emptyList(),
    val reels: List<SupabaseServices.ReelRow> = emptyList(),
    val creators: List<Pair<String, String>> = emptyList()
) {
    val isEmpty: Boolean
        get() = quran.isEmpty() && hadith.isEmpty() && library.isEmpty() &&
            reels.isEmpty() && creators.isEmpty()
}

/**
 * بحث عام حقيقي: تأخير 350ms، حد أدنى حرفان، نتائج مجمّعة (حد 4 لكل مجموعة).
 * - قرآن عبر QuranDataProvider.searchVerses (يحتاج 3 أحرف فأكثر حسب المزوّد).
 * - حديث عبر SahihHadithsRepository (القائمة المنقّاة المحلية).
 * - مكتبة بنفس محدد IslamicLibraryScreen (العنوان/المؤلف/الوصف/العنوان التشويقي).
 * - ريلز من الذاكرة المحلية أولاً ثم جلب سحابي مع تعقيم ReelSafetyGuardrails.
 * - صنّاع من صانعي الريلز المطابقين للاستعلام.
 * الروابط العميقة 5 دوال اختيارية افتراضها فتح القسم المالك؛ الربط الحقيقي
 * لصفحة محددة عمل في AppNavigation (خارج نطاق ملفات الرئيسية).
 */
@Composable
fun HomeGlobalSearch(
    onOpenQuranVerse: (Int, Int) -> Unit = { _, _ -> },
    onOpenHadith: (String) -> Unit = {},
    onOpenLibraryBook: (String) -> Unit = {},
    onOpenReel: (String) -> Unit = {},
    onOpenCreator: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf(HomeSearchResults()) }
    var hasSearched by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            results = HomeSearchResults()
            hasSearched = false
            isSearching = false
            return@LaunchedEffect
        }
        delay(350L)
        isSearching = true
        val found = withContext(Dispatchers.IO) {
            runCatching { searchHomeContent(context, trimmed) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
                ?: HomeSearchResults()
        }
        results = found
        hasSearched = true
        isSearching = false
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("ابحث في القرآن والحديث والمكتبة والريلز…", fontFamily = CairoFont) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "مسح البحث")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )

        if (isSearching) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("جارٍ البحث…", color = theme.textSecondary, fontFamily = CairoFont, fontSize = 12.sp)
            }
        } else if (hasSearched && results.isEmpty) {
            Text(
                text = "لا توجد نتائج مطابقة لـ «${query.trim()}»",
                color = theme.textSecondary,
                fontFamily = CairoFont,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        } else if (hasSearched) {
            if (results.quran.isNotEmpty()) {
                HomeSearchGroup(title = "القرآن الكريم (${results.quran.size})") {
                    results.quran.forEach { verse ->
                        HomeSearchRow(
                            title = verse.verseText,
                            subtitle = "سورة ${verse.surahName} • الآية ${verse.verseNumber}",
                            onClick = { onOpenQuranVerse(verse.surahNumber, verse.verseNumber) }
                        )
                    }
                }
            }
            if (results.hadith.isNotEmpty()) {
                HomeSearchGroup(title = "الحديث (${results.hadith.size})") {
                    results.hadith.forEach { hadith ->
                        HomeSearchRow(
                            title = hadith.title,
                            subtitle = hadith.reference,
                            onClick = { onOpenHadith(hadith.id) }
                        )
                    }
                }
            }
            if (results.library.isNotEmpty()) {
                HomeSearchGroup(title = "المكتبة (${results.library.size})") {
                    results.library.forEach { book ->
                        HomeSearchRow(
                            title = book.title,
                            subtitle = book.authorName,
                            onClick = { onOpenLibraryBook(book.id) }
                        )
                    }
                }
            }
            if (results.reels.isNotEmpty()) {
                HomeSearchGroup(title = "الريلز (${results.reels.size})") {
                    results.reels.forEach { reel ->
                        HomeSearchRow(
                            title = reel.title.ifBlank { "مقطع" },
                            subtitle = reel.userName,
                            onClick = { onOpenReel(reel.id) }
                        )
                    }
                }
            }
            if (results.creators.isNotEmpty()) {
                HomeSearchGroup(title = "الصنّاع (${results.creators.size})") {
                    results.creators.forEach { (creatorId, creatorName) ->
                        HomeSearchRow(
                            title = creatorName,
                            subtitle = "صانع محتوى",
                            onClick = { onOpenCreator(creatorId, creatorName) }
                        )
                    }
                }
            }
        }
    }
}

private suspend fun searchHomeContent(context: Context, query: String): HomeSearchResults {
    // قرآن: المزوّد يحتاج 3 أحرف فأكثر؛ دونها تبقى المجموعة فارغة.
    val quranResults = if (query.length >= 3) {
        runCatching { QuranDataProvider.searchVerses(query, limit = 4) }.getOrNull().orEmpty()
    } else {
        emptyList()
    }
    val hadithResults = runCatching {
        SahihHadithsRepository.searchHadiths(null, null, query).take(4)
    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull().orEmpty()
    val libraryResults = IslamicLibraryRepository.books.filter { book ->
        book.title.contains(query, ignoreCase = true) ||
            book.authorName.contains(query, ignoreCase = true) ||
            book.description.contains(query, ignoreCase = true) ||
            book.hookTitle.contains(query, ignoreCase = true)
    }.take(4)

    // ريلز: الذاكرة المحلية أولاً، ثم جلب سحابي عند الحاجة.
    // review-r13: كان الجلب السحابي يقتصر على أول 20 مقطعاً، فالبحث عن
    // مقطع أقدم لا يجد شيئاً أبداً رغم وجوده. الآن 100 مقطع للبحث فقط
    // (خلاصة الريلز نفسها لم تتغير).
    val localReels = runCatching { ReelPreferencesManager.getCachedReels(context) }.getOrNull().orEmpty()
    val localMatches = localReels.filter { reelMatches(it, query) }
    val cloudMatches = if (localMatches.size < 4) {
        val cloud = runCatching {
            SupabaseServices.Profiles.getActiveReels(limit = 100, offset = 0).orEmpty()
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull().orEmpty()
            .mapNotNull { ReelSafetyGuardrails.validateAndSanitizeReel(it) }
        cloud.filter { reelMatches(it, query) }
            .filter { cloudReel -> localMatches.none { it.id == cloudReel.id } }
    } else {
        emptyList()
    }
    val reelResults = (localMatches + cloudMatches).take(4)
    val creatorResults = (localMatches + cloudMatches)
        .filter { it.userName.contains(query, ignoreCase = true) }
        .distinctBy { it.userId.ifBlank { it.userName } }
        .map { it.userId to it.userName }
        .take(4)

    return HomeSearchResults(
        quran = quranResults,
        hadith = hadithResults,
        library = libraryResults,
        reels = reelResults,
        creators = creatorResults
    )
}

private fun reelMatches(reel: SupabaseServices.ReelRow, query: String): Boolean =
    reel.title.contains(query, ignoreCase = true) ||
        (reel.description?.contains(query, ignoreCase = true) == true) ||
        (reel.userName?.contains(query, ignoreCase = true) == true) ||
        reel.tags.any { it.contains(query, ignoreCase = true) }

@Composable
private fun HomeSearchGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val theme = QabasTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            color = theme.primary,
            fontFamily = CairoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        AppleCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun HomeSearchRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val theme = QabasTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = theme.textSecondary,
            modifier = Modifier.size(16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = theme.textSecondary,
                    fontFamily = CairoFont,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
