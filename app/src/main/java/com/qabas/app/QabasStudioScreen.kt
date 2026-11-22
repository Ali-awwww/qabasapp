package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleKeyRequiredBanner
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.components.AppleSecondaryButton
import com.qabas.app.ui.components.AppleSegmentedControl
import com.qabas.app.ui.components.AppleTopBar
import com.qabas.app.ui.dialogs.HadithShareDialog
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

enum class HadithCategory(val label: String, val icon: String) {
    ALL("الكل", "✨"),
    SAHIH("صحيح وسنن", "📜"),
    QUDSI("أحاديث قدسية", "👑"),
    MORAL("أخلاق ومعاملات", "💎"),
    ESCHATOLOGY("أشراط الساعة والفتن", "🕰️")
}

data class HadithPreset(
    val title: String,
    val narrator: String,
    val text: String,
    val status: String,
    val source: String,
    val themeBg: String,
    val suggestedAspect: String = "9:16",
    val category: HadithCategory = HadithCategory.SAHIH
)

data class HadithTheme(
    val id: String,
    val name: String,
    val icon: String,
    val bgColors: List<Color>,
    val textColor: Color,
    val accentColor: Color,
    val cardBg: Color,
    val overlayAlpha: Float = 0.55f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QabasStudioScreen(
    onBack: () -> Unit,
    initialVerseText: String? = null,
    initialSurahName: String? = null,
    initialVerseNumber: Int? = null,
    onExportAsVideo: (String, String) -> Unit = { _, _ -> },
    onViewProjects: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val scope = rememberCoroutineScope()
    val hasAiEngine = remember { ServiceAvailabilityRegistry.hasAnyAiAssistantEngine(context) }

    val isQuranMode = initialVerseText != null

    val localPresets = remember {
        listOf(
            HadithPreset(
                title = "إنما الأعمال بالنيات",
                narrator = "عَنْ أَمِيرِ المُؤْمِنِينَ عُمَرَ بْنِ الخَطَّابِ رَضِيَ اللَّهُ عَنْهُ قَالَ: سَمِعْتُ رَسُولَ اللَّهِ ﷺ يَقُولُ:",
                text = "(إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ، وَإِنَّمَا لِكُلِّ امْرِئٍ مَا نَوَى، فَمَنْ كَانَتْ هِجْرَتُهُ إِلَى اللَّهِ وَرَسُولِهِ فَهِجْرَتُهُ إِلَى اللَّهِ وَرَسُولِهِ).",
                status = "متفق عليه",
                source = "صحيح البخاري رقم 1 ومسلم رقم 1907",
                themeBg = "black_gold",
                category = HadithCategory.SAHIH
            ),
            HadithPreset(
                title = "يا عبادي إني حرمت الظلم",
                narrator = "عَنْ أَبِي ذَرٍّ الغِفَارِيِّ رَضِيَ اللَّهُ عَنْهُ عَنِ النَّبِيِّ ﷺ فِيمَا يَرْوِيهِ عَنْ رَبِّهِ تَبَارَكَ وَتَعَالَى أَنَّهُ قَالَ:",
                text = "(يَا عِبَادِي إِنِّي حَرَّمْتُ الظُّلْمَ عَلَى نَفْسِي، وَجَعَلْتُهُ بَيْنَكُمْ مُحَرَّمًا، فَلَا تَظَالَمُوا).",
                status = "حديث قدسي صحيح",
                source = "صحيح مسلم - رقم 2577",
                themeBg = "black_gold",
                category = HadithCategory.QUDSI
            ),
            HadithPreset(
                title = "خيركم من تعلم القرآن",
                narrator = "عَنْ عُثْمَانَ بْنِ عَفَّانَ رَضِيَ اللَّهُ عَنْهُ عَنِ النَّبِيِّ ﷺ قَالَ:",
                text = "(خَيْرُكُمْ مَنْ تَعَلَّمَ القُرْآنَ وَعَلَّمَهُ).",
                status = "صحيح البخاري",
                source = "صحيح البخاري - رقم 5027",
                themeBg = "manuscript",
                category = HadithCategory.SAHIH
            ),
            HadithPreset(
                title = "كلمتان خفيفتان",
                narrator = "عَنْ أَبِي هُرَيْرَةَ رَضِيَ اللَّهُ عَنْهُ عَنِ النَّبِيِّ ﷺ قَالَ:",
                text = "(كَلِمَتَانِ خَفِيفَتَانِ عَلَى اللِّسَانِ، ثَقِيلَتَانِ فِي المِيزَانِ، حَبِيبَتَانِ إِلَى الرَّحْمَٰنِ: سُبْحَانَ اللَّهِ وَبِحَمْدِهِ، سُبْحَانَ اللَّهِ العَظِيمِ).",
                status = "متفق عليه",
                source = "صحيح البخاري رقم 6406 ومسلم رقم 2694",
                themeBg = "night",
                category = HadithCategory.SAHIH
            ),
            HadithPreset(
                title = "لا يؤمن أحدكم",
                narrator = "عَنْ أَنَسٍ رَضِيَ اللَّهُ عَنْهُ عَنِ النَّبِيِّ ﷺ قَالَ:",
                text = "(لَا يُؤْمِنُ أَحَدُكُمْ حَتَّى يُحِبَّ لِأَخِيهِ مَا يُحِبُّ لِنَفْسِهِ).",
                status = "متفق عليه",
                source = "صحيح البخاري رقم 13 ومسلم رقم 45",
                themeBg = "mosque",
                category = HadithCategory.MORAL
            ),
            HadithPreset(
                title = "اتق الله حيثما كنت",
                narrator = "عَنْ أَبِي ذَرٍّ وَمُعَاذِ بْنِ جَبَلٍ رَضِيَ اللَّهُ عَنْهُمَا عَنْ رَسُولِ اللَّهِ ﷺ قَالَ:",
                text = "(اتَّقِ اللَّهَ حَيْثُمَا كُنْتَ، وَأَتْبِعِ السَّيِّئَةَ الحَسَنَةَ تَمْحُهَا، وَخَالِقِ النَّاسَ بِخُلُقٍ حَسَنٍ).",
                status = "حسن صحيح",
                source = "سنن الترمذي - رقم 1987",
                themeBg = "black_gold",
                category = HadithCategory.MORAL
            ),
            HadithPreset(
                title = "تقارب الزمان والفتن",
                narrator = "عَنْ أَبِي هُرَيْرَةَ رَضِيَ اللَّهُ عَنْهُ عَنْ رَسُولِ اللَّهِ ﷺ قَالَ:",
                text = "(يُوشِكُ أَنْ لا تَقُومَ السَّاعَةُ؛ حَتَّى يُقْبَضَ العِلْمُ، وَتَظْهَرَ الفِتَنُ، وَيَكْثُرَ الكَذِبُ، وَيَتَقَارَبَ الزَّمَانُ، وَتَتَقَارَبَ الأَسْوَاقُ).",
                status = "صحيح",
                source = "الصحيح المسند من أحاديث الفتن ص 434",
                themeBg = "clocks",
                category = HadithCategory.ESCHATOLOGY
            ),
            HadithPreset(
                title = "الكلمة الطيبة والصدقة",
                narrator = "عَنْ أَبِي هُرَيْرَةَ رَضِيَ اللَّهُ عَنْهُ قَالَ: قَالَ رَسُولُ اللَّهِ ﷺ:",
                text = "(وَالْكَلِمَةُ الطَّيِّبَةُ صَدَقَةٌ، وَكُلُّ خُطْوَةٍ تَمْشِيهَا إِلَى الصَّلَاةِ صَدَقَةٌ، وَتُمِيطُ الْأَذَى عَنِ الطَّرِيقِ صَدَقَةٌ).",
                status = "صحيح",
                source = "رياض الصالحين - رقم 204",
                themeBg = "mosque",
                category = HadithCategory.MORAL
            )
        )
    }

    var presets by remember { mutableStateOf<List<HadithPreset>>(localPresets) }
    var presetsSource by remember { mutableStateOf("محلي") }

    LaunchedEffect(Unit) {
        val cloud = SupabaseServices.Database.getHadithPresets()
        if (cloud.isNotEmpty()) {
            presets = cloud.map { row ->
                val cat = when (row.category?.lowercase()?.trim()) {
                    "qudsi", "قدسي", "أحاديث قدسية" -> HadithCategory.QUDSI
                    "moral", "أخلاق", "أخلاق ومعاملات" -> HadithCategory.MORAL
                    "eschatology", "فتن", "أشراط الساعة" -> HadithCategory.ESCHATOLOGY
                    else -> HadithCategory.SAHIH
                }
                HadithPreset(
                    title = row.title,
                    narrator = row.narrator,
                    text = row.text,
                    status = row.status,
                    source = row.source,
                    themeBg = row.themeBg,
                    suggestedAspect = row.suggestedAspect,
                    category = cat
                )
            }
            presetsSource = "سحابي (${cloud.size})"
        }
    }

    // Export card artwork themes — preserved for rich Islamic Hadith card bitmap generation
    val cardThemes = remember {
        listOf(
            HadithTheme(
                id = "black_gold",
                name = "الذهبي الملكي",
                icon = "👑",
                bgColors = listOf(Color(0xFF18181B), Color(0xFF09090B), Color(0xFF000000)),
                textColor = Color(0xFFFAFAFA),
                accentColor = Color(0xFFE8C547),
                cardBg = Color(0xCC27272A)
            ),
            HadithTheme(
                id = "manuscript",
                name = "مخطوطة أندلسية",
                icon = "📜",
                bgColors = listOf(Color(0xFF3B2818), Color(0xFF1E1309), Color(0xFF0B0F19)),
                textColor = Color(0xFFFFFBEB),
                accentColor = Color(0xFFF5D76E),
                cardBg = Color(0xCC2A1B0E)
            ),
            HadithTheme(
                id = "night",
                name = "ليل الحرمين",
                icon = "🌌",
                bgColors = listOf(Color(0xFF0B0F19), Color(0xFF0B132B), Color(0xFF030712)),
                textColor = Color.White,
                accentColor = Color(0xFFE8C547),
                cardBg = Color(0xCC151B2B)
            ),
            HadithTheme(
                id = "mosque",
                name = "محراب الزمرد",
                icon = "🕌",
                bgColors = listOf(Color(0xFF064E3B), Color(0xFF022C22), Color(0xFF0B0F19)),
                textColor = Color(0xFFECFDF5),
                accentColor = Color(0xFF34D399),
                cardBg = Color(0xCC065F46)
            ),
            HadithTheme(
                id = "clocks",
                name = "ساعات الزمان",
                icon = "🕰️",
                bgColors = listOf(Color(0xFF2C1E12), Color(0xFF140D07), Color(0xFF0B0F19)),
                textColor = Color(0xFFF1F5F9),
                accentColor = Color(0xFFE8C547),
                cardBg = Color(0xCC1A110B)
            )
        )
    }

    var inputText by rememberSaveable { mutableStateOf(initialVerseText ?: presets[0].text) }
    var narratorText by remember {
        mutableStateOf(
            if (isQuranMode) {
                if (initialSurahName != null) "﴿ سورة $initialSurahName " + (if (initialVerseNumber != null) "- الآية $initialVerseNumber " else "") + "﴾"
                else ""
            } else presets[0].narrator
        )
    }
    var hadithStatus by remember { mutableStateOf(if (isQuranMode) "آية قرآنية" else presets[0].status) }
    var hadithSource by remember {
        mutableStateOf(
            if (isQuranMode) {
                if (initialSurahName != null) "المصحف الشريف - سورة $initialSurahName" else "القرآن الكريم"
            } else presets[0].source
        )
    }
    var selectedThemeId by remember { mutableStateOf(if (isQuranMode) "black_gold" else presets[0].themeBg) }
    var selectedAspect by remember { mutableStateOf("9:16") }
    val userHandle by remember { mutableStateOf("@qabas_studio") }
    var showBadge by remember { mutableStateOf(true) }
    var showSocialIcons by remember { mutableStateOf(true) }
    var fontSizeSp by remember { mutableIntStateOf(20) }
    var selectedFont by remember { mutableStateOf(DesignFont.AMIRI) }
    var selectedAlign by remember { mutableStateOf(DesignAlign.CENTER) }
    val textRotation by remember { mutableFloatStateOf(0f) }
    var ornamentId by remember { mutableStateOf("none") }
    var showSeoDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var pendingShareImagePath by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf(HadithCategory.ALL) }
    var isGeneratingAi by remember { mutableStateOf(false) }
    var showCustomInputSheet by remember { mutableStateOf(false) }
    var customBgImage by remember { mutableStateOf<String?>(null) }
    var isGeneratingAiImage by remember { mutableStateOf(false) }

    var selectedQuality by remember { mutableStateOf("4K") }
    var isExportingImage by rememberSaveable { mutableStateOf(false) }
    var activeTab by rememberSaveable { mutableIntStateOf(0) }

    val currentCardTheme = cardThemes.firstOrNull { it.id == selectedThemeId } ?: cardThemes[0]

    Scaffold(
        containerColor = theme.background,
        topBar = {
            AppleTopBar(
                title = "استوديو بطاقات الحديث",
                subtitle = if (isQuranMode) "إنتاج آية قرآنية • $selectedQuality • $presetsSource" else "تصميم دعوي • $selectedQuality • $presetsSource",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = { showSeoDialog = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Tag, contentDescription = "هاشتاقات", tint = theme.primary)
                    }
                    IconButton(
                        onClick = onViewProjects,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "المشاريع", tint = theme.primary)
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = theme.cardSurface,
                border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                shadowElevation = if (theme.isDark) 0.dp else 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    isExportingImage = true
                                    try {
                                        val saved = withContext(Dispatchers.IO) {
                                            saveHadithCardBitmapToStorage(
                                                context = context,
                                                title = "حديث شريف",
                                                narrator = narratorText,
                                                text = inputText,
                                                status = hadithStatus,
                                                source = hadithSource,
                                                userHandle = if (showSocialIcons) userHandle else "",
                                                theme = currentCardTheme,
                                                aspectRatio = selectedAspect,
                                                quality = selectedQuality,
                                                font = selectedFont,
                                                fontSizeSp = fontSizeSp,
                                                align = selectedAlign,
                                                rotation = textRotation,
                                                ornament = designOrnamentById(ornamentId)
                                            )
                                        }
                                        if (saved != null) {
                                            withContext(Dispatchers.IO) {
                                                try {
                                                    val cardEntity = HadithCardEntity(
                                                        id = "HADITH_${System.currentTimeMillis()}",
                                                        text = inputText,
                                                        narrator = narratorText,
                                                        source = hadithSource,
                                                        styleName = currentCardTheme.name,
                                                        aspectRatio = selectedAspect,
                                                        imagePath = saved,
                                                        isFavorite = false,
                                                        createdAt = System.currentTimeMillis()
                                                    )
                                                    AppDatabase.getDatabase(context).hadithCardDao().insertCard(cardEntity)
                                                } catch (_: Throwable) {
                                                }
                                            }
                                            Toast.makeText(context, "تم حفظ البطاقة في المعرض بدقة $selectedQuality", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "تعذر حفظ بطاقة الحديث في المعرض", Toast.LENGTH_LONG).show()
                                        }
                                    } finally {
                                        isExportingImage = false
                                    }
                                }
                            },
                            enabled = !isExportingImage,
                            modifier = Modifier
                                .weight(1.3f)
                                .heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isExportingImage) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("حفظ $selectedQuality...", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("حفظ ($selectedQuality)", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            AppleSecondaryButton(
                                text = "ريلز",
                                icon = Icons.Default.Movie,
                                onClick = {
                                    onExportAsVideo(inputText, selectedAspect)
                                    Toast.makeText(context, "جاري الانتقال لإعداد الريلز ($selectedAspect)...", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        IconButton(
                            onClick = {
                                scope.launch {
                                    val saved = withContext(Dispatchers.IO) {
                                        saveHadithCardBitmapToStorage(
                                            context = context,
                                            title = "حديث شريف",
                                            narrator = narratorText,
                                            text = inputText,
                                            status = hadithStatus,
                                            source = hadithSource,
                                            userHandle = if (showSocialIcons) userHandle else "",
                                            theme = currentCardTheme,
                                            aspectRatio = selectedAspect,
                                            quality = selectedQuality,
                                            font = selectedFont,
                                            fontSizeSp = fontSizeSp,
                                            align = selectedAlign,
                                            rotation = textRotation,
                                            ornament = designOrnamentById(ornamentId)
                                        )
                                    }
                                    pendingShareImagePath = saved
                                    showShareDialog = true
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.secondarySurface)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = theme.primary, modifier = Modifier.size(19.dp))
                        }
                    }
                    bottomBar()
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("المعاينة المباشرة", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("9:16", "1:1", "4:5", "16:9").forEach { aspect ->
                        val selected = selectedAspect == aspect
                        Surface(
                            onClick = { selectedAspect = aspect },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) theme.primary else theme.secondarySurface,
                            modifier = Modifier.heightIn(min = 36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                                Text(
                                    aspect,
                                    color = if (selected) Color.White else theme.textSecondary,
                                    fontFamily = RobotoMonoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            val canvasHeight = when (selectedAspect) {
                "1:1" -> 310.dp
                "4:5" -> 360.dp
                "16:9" -> 210.dp
                else -> 410.dp
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(canvasHeight)
                    .shadow(10.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.5.dp, currentCardTheme.accentColor.copy(alpha = 0.8f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(currentCardTheme.bgColors))
                ) {
                    if (customBgImage != null) {
                        AsyncImage(
                            model = customBgImage,
                            contentDescription = "خلفية الحديث",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.55f))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(currentCardTheme.accentColor.copy(alpha = 0.22f), Color.Transparent)
                                    )
                                )
                        )
                    }

                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.let { native ->
                                DesignOrnamentRenderer.draw(
                                    native,
                                    size.width.roundToInt(),
                                    size.height.roundToInt(),
                                    designOrnamentById(ornamentId),
                                    "#E8C547"
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                color = currentCardTheme.accentColor.copy(alpha = 0.15f),
                                shape = CircleShape,
                                border = BorderStroke(1.dp, currentCardTheme.accentColor.copy(alpha = 0.6f)),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = currentCardTheme.accentColor, modifier = Modifier.size(17.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("خادم المسلم", color = currentCardTheme.accentColor, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        ) {
                            if (narratorText.isNotBlank()) {
                                Text(
                                    text = narratorText,
                                    color = currentCardTheme.textColor.copy(alpha = 0.85f),
                                    fontFamily = CairoFont,
                                    fontSize = (fontSizeSp - 6).coerceAtLeast(10).sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            Text(
                                text = inputText,
                                color = currentCardTheme.textColor,
                                fontFamily = designFontFamily(selectedFont),
                                fontWeight = FontWeight.Bold,
                                fontSize = fontSizeSp.sp,
                                textAlign = designTextAlign(selectedAlign),
                                lineHeight = (fontSizeSp * 1.5).sp,
                                modifier = Modifier.rotate(textRotation)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                color = currentCardTheme.cardBg,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, currentCardTheme.accentColor.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (showBadge && hadithStatus.isNotBlank()) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                            Text("خلاصة الحكم: ", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                            Surface(color = currentCardTheme.accentColor, shape = RoundedCornerShape(4.dp)) {
                                                Text(
                                                    hadithStatus,
                                                    color = Color(0xFF0F172A),
                                                    fontFamily = CairoFont,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }

                                    if (hadithSource.isNotBlank()) {
                                        Text(
                                            hadithSource,
                                            color = currentCardTheme.textColor.copy(alpha = 0.78f),
                                            fontFamily = CairoFont,
                                            fontSize = 10.sp,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    } else {
                                        IslamicSourceAttributionBadge(
                                            text = "$narratorText $inputText",
                                            explicitSource = null,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }

                            if (showSocialIcons) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(userHandle, color = currentCardTheme.accentColor.copy(alpha = 0.85f), fontFamily = CairoFont, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            AppleSegmentedControl(
                items = listOf(0 to "النص", 1 to "المظهر", 2 to "الخطوط", 3 to "الذكاء"),
                selectedItem = activeTab,
                onSelect = { activeTab = it }
            )

            when (activeTab) {
                0 -> {
                    AppleCard(cornerRadius = 16.dp) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (isQuranMode) "الآيات القرآنية المختارة:" else "الأحاديث النبوية المختارة:",
                                    color = theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                TextButton(
                                    onClick = { showCustomInputSheet = !showCustomInputSheet },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text(
                                        if (showCustomInputSheet) "إغلاق المحرر" else if (isQuranMode) "محرر الآية" else "محرر الحديث",
                                        color = theme.primary,
                                        fontFamily = CairoFont,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(HadithCategory.entries) { category ->
                                    val isCategorySelected = selectedCategory == category
                                    FilterChip(
                                        selected = isCategorySelected,
                                        onClick = { selectedCategory = category },
                                        label = { Text("${category.icon} ${category.label}", fontFamily = CairoFont, fontSize = 11.5.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = theme.primary,
                                            selectedLabelColor = Color.White,
                                            containerColor = theme.secondarySurface,
                                            labelColor = theme.textPrimary
                                        )
                                    )
                                }
                            }

                            val filteredPresets = if (selectedCategory == HadithCategory.ALL) presets else presets.filter { it.category == selectedCategory }

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(filteredPresets) { preset ->
                                    val isSelected = inputText == preset.text
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            inputText = preset.text
                                            narratorText = preset.narrator
                                            hadithStatus = preset.status
                                            hadithSource = preset.source
                                            selectedThemeId = preset.themeBg
                                        },
                                        label = {
                                            Text(
                                                preset.title,
                                                fontFamily = CairoFont,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = theme.primary,
                                            selectedLabelColor = Color.White,
                                            containerColor = theme.secondarySurface,
                                            labelColor = theme.textPrimary
                                        )
                                    )
                                }
                            }

                            AnimatedVisibility(visible = showCustomInputSheet) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    HorizontalDivider(color = theme.separator.copy(alpha = 0.5f), thickness = 0.5.dp)
                                    OutlinedTextField(
                                        value = inputText,
                                        onValueChange = { inputText = it },
                                        label = { Text("نص الحديث الشريف", fontFamily = CairoFont) },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 3,
                                        maxLines = 5,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = theme.primary,
                                            unfocusedBorderColor = theme.separator.copy(alpha = 0.6f),
                                            focusedTextColor = theme.textPrimary,
                                            unfocusedTextColor = theme.textPrimary,
                                            focusedContainerColor = theme.secondarySurface,
                                            unfocusedContainerColor = theme.secondarySurface
                                        )
                                    )
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = narratorText,
                                            onValueChange = { narratorText = it },
                                            label = { Text("الراوي", fontFamily = NotoSansFont, fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = theme.primary,
                                                unfocusedBorderColor = theme.separator.copy(alpha = 0.6f),
                                                focusedTextColor = theme.textPrimary,
                                                unfocusedTextColor = theme.textPrimary
                                            )
                                        )
                                        OutlinedTextField(
                                            value = hadithStatus,
                                            onValueChange = { hadithStatus = it },
                                            label = { Text("الحكم (صحيح)", fontFamily = NotoSansFont, fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = theme.primary,
                                                unfocusedBorderColor = theme.separator.copy(alpha = 0.6f),
                                                focusedTextColor = theme.textPrimary,
                                                unfocusedTextColor = theme.textPrimary
                                            )
                                        )
                                    }
                                    OutlinedTextField(
                                        value = hadithSource,
                                        onValueChange = { hadithSource = it },
                                        label = { Text("المصدر والكتاب", fontFamily = CairoFont) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = theme.primary,
                                            unfocusedBorderColor = theme.separator.copy(alpha = 0.6f),
                                            focusedTextColor = theme.textPrimary,
                                            unfocusedTextColor = theme.textPrimary
                                        )
                                    )

                                    if (!hasAiEngine) {
                                        AppleKeyRequiredBanner(
                                            serviceName = "تشكيل وتخريج الحديث بالذكاء الاصطناعي",
                                            reason = "يتطلب إعداد مفتاح API سحابي أو نموذج محلي لتخريج الأحاديث آلياً."
                                        )
                                    } else {
                                        ApplePrimaryButton(
                                            text = if (isGeneratingAi) "جاري التحقيق والتشكيل..." else "تشكيل وتخريج آلي بالذكاء الاصطناعي",
                                            icon = Icons.Default.AutoAwesome,
                                            isLoading = isGeneratingAi,
                                            enabled = !isGeneratingAi && inputText.isNotBlank(),
                                            onClick = {
                                                scope.launch {
                                                    isGeneratingAi = true
                                                    try {
                                                        val prompt = """
                                                            أنت خبير في علم الحديث الشريف وتحقيق النصوص الإسلامية.
                                                            الحديث أو النص المدخل هو: "$inputText"
                                                            قم بتخريجه وتشكيله بدقة وأرجع النتيجة بصيغة JSON حصراً:
                                                            {"tashkeelText":"...","narrator":"...","status":"...","source":"..."}
                                                        """.trimIndent()
                                                        val response = RealGeminiService.chatWithAssistant(listOf(Pair(true, prompt)), null)
                                                        if (response.isNotBlank()) {
                                                            val cleaned = response.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                                                            val json = org.json.JSONObject(cleaned)
                                                            if (json.has("tashkeelText")) inputText = json.getString("tashkeelText")
                                                            if (json.has("narrator")) narratorText = json.getString("narrator")
                                                            if (json.has("status")) hadithStatus = json.getString("status")
                                                            if (json.has("source")) hadithSource = json.getString("source")
                                                            Toast.makeText(context, "تم ضبط وتخريج الحديث بنجاح", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            Toast.makeText(context, "لم يتم إرجاع نتيجة من محرك الذكاء", Toast.LENGTH_LONG).show()
                                                        }
                                                    } catch (_: Exception) {
                                                        Toast.makeText(context, "تعذر التدقيق والتنسيق، تحقق من الاتصال وحاول مرة أخرى", Toast.LENGTH_LONG).show()
                                                    } finally {
                                                        isGeneratingAi = false
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    AppleCard(cornerRadius = 16.dp) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("قالب البطاقة والتصميم:", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(cardThemes) { t ->
                                    val isSelected = selectedThemeId == t.id && customBgImage == null
                                    Surface(
                                        onClick = { selectedThemeId = t.id },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) theme.primary.copy(alpha = 0.14f) else theme.secondarySurface,
                                        border = BorderStroke(if (isSelected) 1.5.dp else 0.5.dp, if (isSelected) theme.primary else theme.separator.copy(alpha = 0.5f)),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(t.icon, fontSize = 15.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                t.name,
                                                color = if (isSelected) theme.primary else theme.textPrimary,
                                                fontFamily = CairoFont,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = theme.separator.copy(alpha = 0.5f), thickness = 0.5.dp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("دقة التصدير:", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(
                                        when (selectedQuality) {
                                            "4K" -> "فائقة 4K UHD (2160p)"
                                            "2K" -> "عالية 2K QHD (1440p)"
                                            else -> "عادية HD (1080p)"
                                        },
                                        color = theme.textSecondary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 11.sp
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("HD", "2K", "4K").forEach { q ->
                                        val isSel = selectedQuality == q
                                        Surface(
                                            onClick = { selectedQuality = q },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSel) theme.primary else theme.secondarySurface,
                                            modifier = Modifier.heightIn(min = 40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                                                Text(
                                                    q,
                                                    color = if (isSel) Color.White else theme.textPrimary,
                                                    fontFamily = RobotoMonoFont,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.5.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { showBadge = !showBadge }
                                ) {
                                    Checkbox(
                                        checked = showBadge,
                                        onCheckedChange = { showBadge = it },
                                        colors = CheckboxDefaults.colors(checkedColor = theme.primary)
                                    )
                                    Text("شارة الحكم الشرعي", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 12.sp)
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { showSocialIcons = !showSocialIcons }
                                ) {
                                    Checkbox(
                                        checked = showSocialIcons,
                                        onCheckedChange = { showSocialIcons = it },
                                        colors = CheckboxDefaults.colors(checkedColor = theme.primary)
                                    )
                                    Text("معرف الحساب", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                2 -> {
                    AppleCard(cornerRadius = 16.dp) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("الخط العربي المفضل:", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(DesignFont.entries) { font ->
                                    val isFontSel = selectedFont == font
                                    FilterChip(
                                        selected = isFontSel,
                                        onClick = { selectedFont = font },
                                        label = { Text(font.displayName, fontFamily = designFontFamily(font), fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = theme.primary,
                                            selectedLabelColor = Color.White,
                                            containerColor = theme.secondarySurface,
                                            labelColor = theme.textPrimary
                                        )
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("حجم الخط (${fontSizeSp}sp)", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 13.sp)
                                Slider(
                                    value = fontSizeSp.toFloat(),
                                    onValueChange = { fontSizeSp = it.toInt() },
                                    valueRange = 14f..32f,
                                    modifier = Modifier.width(170.dp),
                                    colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("المحاذاة:", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    DesignAlign.entries.forEach { align ->
                                        val isAlignSel = selectedAlign == align
                                        FilterChip(
                                            selected = isAlignSel,
                                            onClick = { selectedAlign = align },
                                            label = { Text(align.label, fontFamily = CairoFont, fontSize = 11.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = theme.primary,
                                                selectedLabelColor = Color.White,
                                                containerColor = theme.secondarySurface,
                                                labelColor = theme.textPrimary
                                            )
                                        )
                                    }
                                }
                            }

                            Text("إطارات وزخارف إسلامية:", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(DesignOrnament.entries) { orn ->
                                    val isOrnSel = ornamentId == orn.id
                                    FilterChip(
                                        selected = isOrnSel,
                                        onClick = { ornamentId = orn.id },
                                        label = { Text("${orn.icon} ${orn.label}", fontFamily = CairoFont, fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = theme.primary,
                                            selectedLabelColor = Color.White,
                                            containerColor = theme.secondarySurface,
                                            labelColor = theme.textPrimary
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    AppleCard(cornerRadius = 16.dp) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "توليد خلفية سينمائية بالذكاء الاصطناعي",
                                    color = theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                if (customBgImage != null) {
                                    TextButton(
                                        onClick = { customBgImage = null },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("إزالة", color = theme.destructive, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Text(
                                "يحلل سياق النص ويولد أو يجلب خلفية إسلامية هادئة مناسبة للبطاقة.",
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )

                            ApplePrimaryButton(
                                text = if (isGeneratingAiImage) "جاري توليد الخلفية..." else if (customBgImage != null) "توليد خلفية جديدة" else "توليد خلفية سينمائية",
                                icon = Icons.Default.Palette,
                                isLoading = isGeneratingAiImage,
                                enabled = !isGeneratingAiImage,
                                onClick = {
                                    scope.launch {
                                        isGeneratingAiImage = true
                                        try {
                                            val prompt = "Cinematic atmospheric background for Islamic Hadith, $inputText, photorealistic, warm ambient lighting, islamic architecture, soft bokeh"
                                            val generatedImage = AppServices.generateAiImage(prompt)
                                            if (!generatedImage.isNullOrBlank()) {
                                                customBgImage = generatedImage
                                                Toast.makeText(context, "تم تطبيق الخلفية المولدة بنجاح", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "تعذر توليد خلفية جديدة: تحقق من الاتصال أو مفاتيح الصور", Toast.LENGTH_LONG).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "تعذر توليد الخلفية: ${e.message ?: "تحقق من الاتصال"}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isGeneratingAiImage = false
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    AppleSecondaryButton(
                        text = "نسخ",
                        icon = Icons.Default.ContentCopy,
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val label = if (narratorText.isNotBlank()) "$narratorText\n$inputText" else inputText
                            val fullText = "$label\n\nحكم المحدث: ${hadithStatus.ifBlank { "-" }}\nالمصدر: ${hadithSource.ifBlank { "-" }}\nتصميم عبر تطبيق خادم المسلم"
                            cm.setPrimaryClip(ClipData.newPlainText("hadith", fullText))
                            Toast.makeText(context, "تم نسخ نص الحديث والمصدر", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    AppleSecondaryButton(
                        text = "مشاركة",
                        icon = Icons.Default.Share,
                        onClick = {
                            if (pendingShareImagePath.isNullOrBlank()) {
                                Toast.makeText(context, "احفظ صورة البطاقة أو اضغط زر المشاركة بالأسفل أولاً", Toast.LENGTH_SHORT).show()
                            } else {
                                showShareDialog = true
                            }
                        }
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    AppleSecondaryButton(
                        text = "هاشتاقات",
                        icon = Icons.Default.Tag,
                        tint = theme.accent,
                        onClick = { showSeoDialog = true }
                    )
                }
            }

            if (showShareDialog) {
                HadithShareDialog(
                    narrator = narratorText,
                    hadithText = inputText,
                    hadithStatus = hadithStatus,
                    hadithSource = hadithSource,
                    userHandle = if (showSocialIcons) userHandle else "",
                    imagePath = pendingShareImagePath,
                    onDismiss = { showShareDialog = false }
                )
            }

            if (showSeoDialog) {
                ViralSeoHashtagsDialog(
                    context = context,
                    initialTopicOrScript = inputText,
                    onDismiss = { showSeoDialog = false },
                    onApplySeoData = { _, hashtagsText ->
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("hashtags", hashtagsText))
                        Toast.makeText(context, "تم نسخ الهاشتاقات المقترحة", Toast.LENGTH_SHORT).show()
                        showSeoDialog = false
                    }
                )
            }
        }
    }
}

private fun Color.toAndroidColor(alphaScale: Float = 1f): Int = android.graphics.Color.argb(
    (alpha * alphaScale * 255f).coerceIn(0f, 255f).roundToInt(),
    (red * 255f).coerceIn(0f, 255f).roundToInt(),
    (green * 255f).coerceIn(0f, 255f).roundToInt(),
    (blue * 255f).coerceIn(0f, 255f).roundToInt()
)

private fun saveHadithCardBitmapToStorage(
    context: Context,
    title: String,
    narrator: String,
    text: String,
    status: String,
    source: String,
    userHandle: String,
    theme: HadithTheme,
    aspectRatio: String,
    quality: String = "4K",
    font: DesignFont = DesignFont.AMIRI,
    fontSizeSp: Int = 20,
    align: DesignAlign = DesignAlign.CENTER,
    rotation: Float = 0f,
    ornament: DesignOrnament = DesignOrnament.NONE
): String? {
    var bitmap: android.graphics.Bitmap? = null
    return try {
        val scale = when (quality) {
            "4K" -> 2.0f
            "2K" -> 1.334f
            else -> 1.0f
        }

        val (baseW, baseH) = when (aspectRatio) {
            "9:16" -> 1080 to 1920
            "16:9" -> 1920 to 1080
            "4:5" -> 1080 to 1350
            else -> 1080 to 1080
        }

        val width = (baseW * scale).toInt()
        val height = (baseH * scale).toInt()

        bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        val bgColorsInts = if (theme.bgColors.isNotEmpty()) {
            theme.bgColors.map {
                android.graphics.Color.argb(
                    (it.alpha * 255).toInt(),
                    (it.red * 255).toInt(),
                    (it.green * 255).toInt(),
                    (it.blue * 255).toInt()
                )
            }.toIntArray()
        } else {
            intArrayOf(android.graphics.Color.parseColor("#0B0F19"), android.graphics.Color.parseColor("#030712"))
        }

        val gradient = android.graphics.LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            bgColorsInts, null, android.graphics.Shader.TileMode.CLAMP
        )
        val bgPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            shader = gradient
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val framePaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = theme.accentColor.toAndroidColor()
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 6f * scale
        }
        val inset = 36f * scale
        canvas.drawRoundRect(android.graphics.RectF(inset, inset, width - inset, height - inset), 32f * scale, 32f * scale, framePaint)

        val cardPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = theme.cardBg.toAndroidColor()
            style = android.graphics.Paint.Style.FILL
        }
        val cardInset = 64f * scale
        canvas.drawRoundRect(android.graphics.RectF(cardInset, cardInset, width - cardInset, height - cardInset), 26f * scale, 26f * scale, cardPaint)

        val cardBorderPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = theme.accentColor.toAndroidColor(alphaScale = 0.4f)
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2f * scale
        }
        canvas.drawRoundRect(android.graphics.RectF(cardInset, cardInset, width - cardInset, height - cardInset), 26f * scale, 26f * scale, cardBorderPaint)

        val headerPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = theme.accentColor.toAndroidColor()
            textSize = 28f * scale
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        canvas.drawText("✦ خادم المسلم | بَطَاقَة حَدِيث شَرِيف ✦", width / 2f, 130f * scale, headerPaint)

        val titlePaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = theme.textColor.toAndroidColor()
            textSize = 36f * scale
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        canvas.drawText(title.ifBlank { "حديث نبوي شريف" }, width / 2f, 200f * scale, titlePaint)

        val narratorPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = theme.textColor.toAndroidColor(alphaScale = 0.85f)
            textSize = 24f * scale
            textAlign = android.graphics.Paint.Align.CENTER
        }
        if (narrator.isNotBlank()) {
            canvas.drawText(narrator, width / 2f, 260f * scale, narratorPaint)
        }

        val bodyPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            typeface = designFontTypeface(context, font)
            color = theme.textColor.toAndroidColor()
            textSize = fontSizeSp * 3f * scale
        }

        val safeText = if (text.length > 350) text.take(345) + "..." else text
        val maxLineWidth = width - (240f * scale)
        val bodyTextPaint = TextPaint(bodyPaint).apply { isSubpixelText = true }
        val bodyLayout = StaticLayout.Builder
            .obtain(safeText, 0, safeText.length, bodyTextPaint, maxLineWidth.toInt())
            .setAlignment(
                when (align) {
                    DesignAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
                    DesignAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                    DesignAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
                }
            )
            .setTextDirection(
                if (safeText.any { it in '\u0600'..'\u06FF' }) {
                    TextDirectionHeuristics.FIRSTSTRONG_RTL
                } else {
                    TextDirectionHeuristics.FIRSTSTRONG_LTR
                }
            )
            .setIncludePad(false)
            .setLineSpacing(0f, 1.65f)
            .build()

        val centerX = width / 2f
        val centerY = height / 2f
        val leftEdge = (110f * scale)
        val rightEdge = width - (110f * scale)
        val layoutX = when (align) {
            DesignAlign.CENTER -> centerX - (maxLineWidth / 2f)
            DesignAlign.RIGHT -> rightEdge - maxLineWidth
            DesignAlign.LEFT -> leftEdge
        }
        val layoutY = centerY - (bodyLayout.height / 2f)
        canvas.save()
        canvas.rotate(rotation, centerX, centerY)
        canvas.translate(layoutX, layoutY)
        bodyLayout.draw(canvas)
        canvas.restore()

        if (status.isNotBlank()) {
            val badgeText = "حكم المحدث: $status"
            val badgePaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = theme.cardBg.toAndroidColor()
                textSize = 20f * scale
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val badgeBgPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = theme.accentColor.toAndroidColor()
                style = android.graphics.Paint.Style.FILL
            }

            val badgeW = badgePaint.measureText(badgeText) + (36f * scale)
            val badgeH = 42f * scale
            val badgeY = height - (200f * scale)
            val badgeRect = android.graphics.RectF(
                (width / 2f) - (badgeW / 2f),
                badgeY - (badgeH / 1.5f),
                (width / 2f) + (badgeW / 2f),
                badgeY + (badgeH / 2.5f)
            )
            canvas.drawRoundRect(badgeRect, 14f * scale, 14f * scale, badgeBgPaint)
            canvas.drawText(badgeText, width / 2f, badgeY + (2f * scale), badgePaint)
        }

        val footerPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = theme.textColor.toAndroidColor(alphaScale = 0.75f)
            textSize = 20f * scale
            textAlign = android.graphics.Paint.Align.CENTER
        }
        if (source.isNotBlank()) {
            canvas.drawText("المصدر: $source", width / 2f, height - (140f * scale), footerPaint)
        }

        if (userHandle.isNotBlank()) {
            val handlePaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = theme.accentColor.toAndroidColor(alphaScale = 0.85f)
                textSize = 20f * scale
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            canvas.drawText(userHandle, width / 2f, height - (95f * scale), handlePaint)
        }

        DesignOrnamentRenderer.draw(canvas, width, height, ornament, "#E8C547")

        val fileName = "QABAS_HADITH_${quality}_${System.currentTimeMillis()}.png"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Qabas_Hadith_Cards")
            }
            val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                val stream = context.contentResolver.openOutputStream(it)
                val compressed = stream?.use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                } ?: false
                if (compressed) {
                    return uri.toString()
                }
                context.contentResolver.delete(uri, null, null)
            }
        } else {
            val imagesDir = context.getExternalFilesDir(null)
            val imageFile = java.io.File(imagesDir, fileName)
            java.io.FileOutputStream(imageFile).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
            return imageFile.absolutePath
        }
        null
    } catch (e: Throwable) {
        android.util.Log.e("HadithCardStudio", "Error saving hadith card bitmap: ${e.message}")
        null
    } finally {
        bitmap?.recycle()
    }
}
