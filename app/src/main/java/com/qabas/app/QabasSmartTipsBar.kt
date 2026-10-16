package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.delay

/**
 * أقسام شريط النصائح والحِكم (Tip Categories)
 */
enum class TipCategory(
    val id: String,
    val title: String,
    val iconEmoji: String,
    val icon: ImageVector,
    val accentColor: Color,
    val isDeveloperOnly: Boolean
) {
    QURAN(
        id = "quran",
        title = "قرآن",
        iconEmoji = "📖",
        icon = Icons.Default.MenuBook,
        accentColor = Color(0xFF10B981), // Emerald Green
        isDeveloperOnly = false
    ),
    SUNNAH(
        id = "sunnah",
        title = "سُنّة",
        iconEmoji = "🕌",
        icon = Icons.Default.Mosque,
        accentColor = Color(0xFFEAB308), // Warm Gold / Amber
        isDeveloperOnly = false
    ),
    DEVELOPER(
        id = "developer",
        title = "مطور",
        iconEmoji = "🛠️",
        icon = Icons.Default.Code,
        accentColor = Color(0xFF00E5FF), // Cyber Cyan / Electric Blue
        isDeveloperOnly = true
    )
}

/**
 * نموذج عنصر النصيحة أو الحكمة
 */
data class QabasTipItem(
    val id: String,
    val category: TipCategory,
    val text: String,
    val sourceOrBadge: String,
    val actionableHint: String? = null
)

/**
 * بنك النصائح والحِكم المصنفة (Quran, Sunnah, Developer)
 */
object QabasTipsRepository {
    val allTips: List<QabasTipItem> = listOf(
        // ── 1. قسم القرآن الكريم (Quran Insights 📖) ──
        QabasTipItem(
            id = "q1",
            category = TipCategory.QURAN,
            text = "«إِنَّ هَذَا الْقُرْآنَ يَهْدِي لِلَّتِي هِيَ أَقْوَمُ»",
            sourceOrBadge = "سورة الإسراء : 9",
            actionableHint = "القرآن الكريم هو البوصلة الهادية لأقوم السبل في حياتك وفكرك وإنتاجك."
        ),
        QabasTipItem(
            id = "q2",
            category = TipCategory.QURAN,
            text = "«وَقُولُوا لِلنَّاسِ حُسْنًا»",
            sourceOrBadge = "سورة البقرة : 83",
            actionableHint = "اجعل كل كلمة وفيديو وتصميم تنشره شاهداً لك بالحسنى والجمال والخير."
        ),
        QabasTipItem(
            id = "q3",
            category = TipCategory.QURAN,
            text = "«فَاذْكُرُونِي أَذْكُرْكُمْ وَاشْكُرُوا لِي وَلَا تَكْفُرُونِ»",
            sourceOrBadge = "سورة البقرة : 152",
            actionableHint = "ذكر الله طمأنينة للقلب، سعة في الصدر، وبركة مضاعفة في الوقت والعمل."
        ),
        QabasTipItem(
            id = "q4",
            category = TipCategory.QURAN,
            text = "«وَتَوَكَّلْ عَلَى الْحَيِّ الَّذِي لَا يَمُوتُ وَسَبِّحْ بِحَمْدِهِ»",
            sourceOrBadge = "سورة الفرقان : 58",
            actionableHint = "التوكل الصادق يقترن ببذل الجهد والتسبيح الدائم طلباً للتوفيق."
        ),
        QabasTipItem(
            id = "q5",
            category = TipCategory.QURAN,
            text = "«وَمَن يَتَّقِ اللَّهَ يَجْعَل لَّهُ مَخْرَجًا وَيَرْزُقْهُ مِنْ حَيْثُ لَا يَحْتَسِبُ»",
            sourceOrBadge = "سورة الطلاق : 2-3",
            actionableHint = "تقوى الله مفتاح الفرج وسبب تيسير الأرزاق والأعمال من حيث لا تحتسب."
        ),
        QabasTipItem(
            id = "q6",
            category = TipCategory.QURAN,
            text = "«أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ»",
            sourceOrBadge = "سورة الرعد : 28",
            actionableHint = "سكينة النفس وراحة البال لا تُنال إلا بدوام صلة القلب بخالقه سبحانه."
        ),
        QabasTipItem(
            id = "q7",
            category = TipCategory.QURAN,
            text = "«ادْعُ إِلَىٰ سَبِيلِ رَبِّكَ بِالْحِكْمَةِ وَالْمَوْعِظَةِ الْحَسَنَةِ»",
            sourceOrBadge = "سورة النحل : 125",
            actionableHint = "الحكمة والرفق والأسلوب الحسن أعظم ركائز التأثير وبناء المحتوى الهادف."
        ),

        // ── 2. قسم السنة النبوية (Sunnah & Hadith Wisdom 🕌) ──
        QabasTipItem(
            id = "s1",
            category = TipCategory.SUNNAH,
            text = "«خَيْرُ النَّاسِ أَنْفَعُهُمْ لِلنَّاسِ»",
            sourceOrBadge = "صحيح الجامع",
            actionableHint = "احرص على أن تكون أعمالك اليومية ومحتواك الرقمي نافعاً يترك أثراً مباركاً."
        ),
        QabasTipItem(
            id = "s2",
            category = TipCategory.SUNNAH,
            text = "«إِنَّ اللَّهَ يُحِبُّ إِذَا عَمِلَ أَحَدُكُمْ عَمَلًا أَنْ يُتْقِنَهُ»",
            sourceOrBadge = "شعب الإيمان",
            actionableHint = "الإتقان في التصميم، الإنتاج، والبرمجة عبادة وسلوك يُحبه الله ورسوله."
        ),
        QabasTipItem(
            id = "s3",
            category = TipCategory.SUNNAH,
            text = "«تَبَسُّمُكَ فِي وَجْهِ أَخِيكَ لَكَ صَدَقَةٌ»",
            sourceOrBadge = "جامع الترمذي",
            actionableHint = "اللطف وبشاشة الوجه صدقة سهلة تجلب البركة وتزرع الألفة بين الناس."
        ),
        QabasTipItem(
            id = "s4",
            category = TipCategory.SUNNAH,
            text = "«مَنْ كَانَ يُؤْمِنُ بِاللَّهِ وَالْيَوْمِ الآخِرِ فَلْيَقُلْ خَيْرًا أَوْ لِيَصْمُتْ»",
            sourceOrBadge = "متفق عليه",
            actionableHint = "انتقِ ألفاظك ومنشوراتك بعناية، فكل كلمة مسجلة في صحيفتك."
        ),
        QabasTipItem(
            id = "s5",
            category = TipCategory.SUNNAH,
            text = "«أَحَبُّ الأَعْمَالِ إِلَى اللَّهِ أَدْوَمُهَا وَإِنْ قَلَّ»",
            sourceOrBadge = "صحيح البخاري",
            actionableHint = "الاستمرار في وردك اليومي وإنتاجك ولو بجهد يسير خير من الانقطاع."
        ),
        QabasTipItem(
            id = "s6",
            category = TipCategory.SUNNAH,
            text = "«الْمُسْلِمُ مَنْ سَلِمَ الْمُسْلِمُونَ مِنْ لِسَانِهِ وَيَدِهِ»",
            sourceOrBadge = "صحيح مسلم",
            actionableHint = "كف الأذى وحفظ اللسان واليد عنوان كمال الإيمان واستقامة السيرة."
        ),
        QabasTipItem(
            id = "s7",
            category = TipCategory.SUNNAH,
            text = "«مَنْ سَلَكَ طَرِيقًا يَلْتَمِسُ فِيهِ عِلْمًا سَهَّلَ اللَّهُ لَهُ طَرِيقًا إِلَى الْجَنَّةِ»",
            sourceOrBadge = "صحيح مسلم",
            actionableHint = "طلب العلم وتطوير المهارات النافعة مسلك ميسر إلى رضوان الله وجنته."
        ),

        // ── 3. قسم نصائح المطور (Developer Pro Tips 🛠️⚡) ──
        QabasTipItem(
            id = "d1",
            category = TipCategory.DEVELOPER,
            text = "كاش PCM الصوتي يضمن استجابة فورية دون الاعتماد على برامج فك التشفير الخارجية.",
            sourceOrBadge = "محرك الصوت ⚡",
            actionableHint = "يتم توليد العينات الصوتية بالمعادلات الإجرائية لحظياً لتفادي أي تأخير أو استهلاك غير ضروري."
        ),
        QabasTipItem(
            id = "d2",
            category = TipCategory.DEVELOPER,
            text = "تحقق من رصيد مفاتيح Gemini و Groq عبر لوحة مفاتيح المطور لتفادي توقف الـ API.",
            sourceOrBadge = "إدارة المفاتيح 🔑",
            actionableHint = "يمكنك إدخال مفتاح مخصص من لوحة Developer Keys لزيادة الكوتا وسرعة الاستجابة."
        ),
        QabasTipItem(
            id = "d3",
            category = TipCategory.DEVELOPER,
            text = "قاعدة بيانات Room تحفظ المشاريع والتلاوات محلياً لضمان عمل التطبيق بدون إنترنت 100%.",
            sourceOrBadge = "الاستقرار والكاش 💾",
            actionableHint = "يتم مزامنة البيانات السحابية مع Supabase في الخلفية بسلاسة عند توفر الشبكة."
        ),
        QabasTipItem(
            id = "d4",
            category = TipCategory.DEVELOPER,
            text = "استخدم لوحة التشخيصات Spatial Diagnostics لمراقبة استهلاك الذاكرة وزمن استجابة الخوادم.",
            sourceOrBadge = "التشخيص الذكي 🛰️",
            actionableHint = "تتوفر أدوات ping وفحص استقرار الـ RPC والخوادم اللحظية داخل مركز المطور."
        ),
        QabasTipItem(
            id = "d5",
            category = TipCategory.DEVELOPER,
            text = "محرك StyleBrain يحلل مقاطع الفيديو لإنتاج أنماط بصرية متوافقة مع الهوية الرقمية.",
            sourceOrBadge = "ذكاء الاستوديو 🧠",
            actionableHint = "يدعم استخراج مقاطع B-Roll الذكية وتطبيق المؤثرات الصوتية الميكانيكية بدقة عالية."
        ),
        QabasTipItem(
            id = "d6",
            category = TipCategory.DEVELOPER,
            text = "استخدم وضع Pure OLED في استوديو الألوان لتقليل استهلاك البطارية على الشاشات الحديثة.",
            sourceOrBadge = "توفير الطاقة 🔋",
            actionableHint = "يتم تحويل الخلفيات إلى سواد مطلق RGB #000000 مما يطفئ بكسلات شاشات OLED تماماً."
        ),
        QabasTipItem(
            id = "d7",
            category = TipCategory.DEVELOPER,
            text = "ميزة Auto-Save تحفظ مسودات الذكاء تلقائياً حتى في حال خروج التطبيق المفاجئ.",
            sourceOrBadge = "سلامة المسودات 🛡️",
            actionableHint = "يمكنك استرجاع مسودة العمل بنقرة واحدة من شريط استعادة المسودات بالشاشة الرئيسية."
        )
    )

    fun getTipsForUser(isDeveloper: Boolean, selectedCategory: TipCategory?): List<QabasTipItem> {
        val allowedTips = if (isDeveloper) {
            allTips
        } else {
            allTips.filter { !it.category.isDeveloperOnly }
        }

        return if (selectedCategory == null) {
            allowedTips
        } else {
            allowedTips.filter { it.category == selectedCategory }
        }
    }
}

/**
 * شريط النصائح والحِكم الذكي المتطور (Qabas Smart Tips & Wisdom Bar)
 * يدعم 3 أقسام: قرآن 📖، سُنّة 🕌، نصائح المطور 🛠️.
 * يظهر القسم الثالث للمطورين فقط، بينما يظهر أول قسمين للمستخدم العادي.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun QabasSmartTipsBar(
    modifier: Modifier = Modifier,
    isDeveloper: Boolean = false,
    onNavigateToDeveloperDashboard: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val primaryColor = qabasPrimary()

    // الأقسام المتاحة بحسب صلاحية المستخدم
    val availableCategories = remember(isDeveloper) {
        if (isDeveloper) {
            listOf(TipCategory.QURAN, TipCategory.SUNNAH, TipCategory.DEVELOPER)
        } else {
            listOf(TipCategory.QURAN, TipCategory.SUNNAH)
        }
    }

    // القسم المختار للتصفية (null يعني عرض الكل بالتدوير)
    var selectedCategoryFilter by remember { mutableStateOf<TipCategory?>(null) }
    
    // قائمة النصائح الفعالة الحالية
    val filteredTips = remember(isDeveloper, selectedCategoryFilter) {
        QabasTipsRepository.getTipsForUser(isDeveloper, selectedCategoryFilter)
    }

    var currentTipIndex by remember { mutableIntStateOf(0) }
    var isAutoRotating by remember { mutableStateOf(true) }
    var selectedTipForDialog by remember { mutableStateOf<QabasTipItem?>(null) }

    // إعادة ضبط المؤشر إذا تغيرت القائمة
    LaunchedEffect(filteredTips) {
        if (currentTipIndex >= filteredTips.size) {
            currentTipIndex = 0
        }
    }

    // مؤقت التدوير التلقائي كل 6 ثوانٍ
    LaunchedEffect(isAutoRotating, filteredTips.size, selectedCategoryFilter) {
        if (isAutoRotating && filteredTips.isNotEmpty()) {
            while (true) {
                delay(6000L)
                currentTipIndex = (currentTipIndex + 1) % filteredTips.size
            }
        }
    }

    val currentTip = filteredTips.getOrNull(currentTipIndex) ?: filteredTips.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ── 1. شريط أزرار تصفية الأقسام (Filter Chips Strip) ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LazyRow(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    // زر "الكل"
                    TipFilterChip(
                        title = "الكل ✦",
                        icon = Icons.Default.AllInclusive,
                        accentColor = GoldPrimary,
                        isSelected = selectedCategoryFilter == null,
                        onClick = {
                            AppSoundManager.playClick()
                            selectedCategoryFilter = null
                            currentTipIndex = 0
                        }
                    )
                }

                items(availableCategories) { category ->
                    TipFilterChip(
                        title = "${category.iconEmoji} ${category.title}",
                        icon = category.icon,
                        accentColor = category.accentColor,
                        isSelected = selectedCategoryFilter == category,
                        onClick = {
                            AppSoundManager.playClick()
                            selectedCategoryFilter = category
                            currentTipIndex = 0
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // كبسولة التدوير التلقائي الفضائية الأنيقة (تستغل المساحة الفارغة بفعالية)
            Surface(
                onClick = {
                    AppSoundManager.playToggle()
                    isAutoRotating = !isAutoRotating
                    Toast.makeText(
                        context,
                        if (isAutoRotating) "تم استئناف التدوير التلقائي ⏱️" else "تم إيقاف التدوير مؤقتاً ⏸️",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                shape = CutCornerShape(5.dp),
                color = if (isAutoRotating) primaryColor.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f),
                border = BorderStroke(0.8.dp, if (isAutoRotating) primaryColor.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = if (isAutoRotating) Icons.Default.Autorenew else Icons.Default.PauseCircle,
                        contentDescription = "تبديل التدوير التلقائي",
                        tint = if (isAutoRotating) primaryColor else Color.Gray,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isAutoRotating) "تدوير حي" else "موقوف",
                        color = if (isAutoRotating) primaryColor else Color.Gray,
                        fontFamily = CairoFont,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        softWrap = false,
                        maxLines = 1
                    )
                }
            }
        }

        // ── 2. بطاقة شريط النصيحة الحية مع أنميشن التحول ──
        if (currentTip != null) {
            val tipColor = currentTip.category.accentColor

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CutCornerShape(8.dp))
                    .clickable {
                        AppSoundManager.playClick()
                        // الانتقال للنصيحة التالية عند النقر السريع
                        currentTipIndex = (currentTipIndex + 1) % filteredTips.size
                    },
                shape = CutCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B132B).copy(alpha = 0.95f)),
                border = BorderStroke(1.dp, tipColor.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // الشارة التصنيفية للقسم
                    Surface(
                        color = tipColor.copy(alpha = 0.18f),
                        shape = CutCornerShape(4.dp),
                        border = BorderStroke(0.8.dp, tipColor.copy(alpha = 0.5f)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(currentTip.category.iconEmoji, fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = currentTip.category.title,
                                color = tipColor,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp
                            )
                        }
                    }

                    // المحتوى النصي المتحرك
                    Box(modifier = Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = currentTip,
                            transitionSpec = {
                                (slideInVertically { height -> height } + fadeIn()).togetherWith(
                                    slideOutVertically { height -> -height } + fadeOut()
                                )
                            },
                            label = "TipTransitionAnimation"
                        ) { tip ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = tip.text,
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                Text(
                                    text = "• ${tip.sourceOrBadge}",
                                    color = tipColor.copy(alpha = 0.85f),
                                    fontFamily = NotoSansFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // زر فتح التفاصيل والمشاركة
                    IconButton(
                        onClick = {
                            AppSoundManager.playClick()
                            selectedTipForDialog = currentTip
                        },
                        modifier = Modifier.size(24.dp).padding(start = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "تفاصيل النصيحة",
                            tint = tipColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }

    // ── 3. نافذة تفاصيل النصيحة مع إمكانية النسخ والمشاركة ──
    if (selectedTipForDialog != null) {
        val tip = selectedTipForDialog!!
        val tipColor = tip.category.accentColor

        Dialog(onDismissRequest = { selectedTipForDialog = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = CutCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(1.5.dp, tipColor.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // رأس النافذة
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = tipColor.copy(alpha = 0.2f),
                                shape = CutCornerShape(6.dp),
                                border = BorderStroke(1.dp, tipColor.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(tip.category.iconEmoji, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "قسم " + tip.category.title,
                                        color = tipColor,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }

                            if (tip.category.isDeveloperOnly) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "مخصص للمطور 🔒",
                                        color = Color(0xFF00E5FF),
                                        fontFamily = CairoFont,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { selectedTipForDialog = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.Gray, modifier = Modifier.size(18.dp))
                        }
                    }

                    // نص الآية / الحديث / النصيحة
                    Surface(
                        color = Color(0xFF1E293B).copy(alpha = 0.6f),
                        shape = CutCornerShape(10.dp),
                        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = tip.text,
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                lineHeight = 21.sp,
                                textAlign = TextAlign.Start
                            )
                            Text(
                                text = "المصدر / التصنيف: ${tip.sourceOrBadge}",
                                color = tipColor,
                                fontFamily = NotoSansFont,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // الشرح والتوجيه العملي
                    if (tip.actionableHint != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "التوجيه والفائدة:",
                                color = tipColor,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = tip.actionableHint,
                                color = Color.White.copy(alpha = 0.85f),
                                fontFamily = CairoFont,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    // أزرار العمليات (نسخ النص / فتح لوحة المطور إذا كان متاحاً)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                AppSoundManager.playSuccess()
                                val shareText = "${tip.text}\n— ${tip.sourceOrBadge}\n\n${tip.actionableHint ?: ""}\n(عبر تطبيق قبس ✨)"
                                clipboardManager.setText(AnnotatedString(shareText))
                                Toast.makeText(context, "تم نسخ النص إلى الحافظة بنجاح! 📋", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = CutCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = tipColor)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("نسخ النص 📋", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        if (tip.category == TipCategory.DEVELOPER && onNavigateToDeveloperDashboard != null) {
                            OutlinedButton(
                                onClick = {
                                    AppSoundManager.playClick()
                                    selectedTipForDialog = null
                                    onNavigateToDeveloperDashboard()
                                },
                                modifier = Modifier.weight(1f).height(42.dp),
                                shape = CutCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF))
                            ) {
                                Icon(Icons.Default.Dashboard, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("لوحة المطور 🛠️", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * زر مصغر لتصفية أقسام النصائح (Filter Chip)
 */
@Composable
private fun TipFilterChip(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CutCornerShape(5.dp),
        color = if (isSelected) accentColor.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.04f),
        border = BorderStroke(
            0.8.dp,
            if (isSelected) accentColor else Color.White.copy(alpha = 0.1f)
        )
    ) {
        Text(
            text = title,
            color = if (isSelected) accentColor else Color.Gray,
            fontFamily = CairoFont,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 9.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
