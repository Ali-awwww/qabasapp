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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.delay

/**
 * 🌟 التصنيفات المتنوعة لشريط النصائح والحِكم الذكية (Diverse Tip Categories)
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
        title = "هدايات قرآنية",
        iconEmoji = "📖",
        icon = Icons.Default.MenuBook,
        accentColor = Color(0xFF10B981), // زمردي قرآني
        isDeveloperOnly = false
    ),
    SUNNAH(
        id = "sunnah",
        title = "حِكم نبوية",
        iconEmoji = "🕌",
        icon = Icons.Default.Mosque,
        accentColor = Color(0xFFF59E0B), // ذهبي عنبري
        isDeveloperOnly = false
    ),
    CREATIVE_STUDIO(
        id = "creative_studio",
        title = "صناعة المحتوى",
        iconEmoji = "🎬",
        icon = Icons.Default.MovieFilter,
        accentColor = Color(0xFFA855F7), // بنفسجي إبداعي
        isDeveloperOnly = false
    ),
    TAZKIYAH(
        id = "tazkiyah",
        title = "تزكية وسكينة",
        iconEmoji = "🌿",
        icon = Icons.Default.Spa,
        accentColor = Color(0xFF14B8A6), // تيركواز طمأنينة
        isDeveloperOnly = false
    ),
    PRODUCTIVITY(
        id = "productivity",
        title = "إنتاجية وتقنية",
        iconEmoji = "⚡",
        icon = Icons.Default.Bolt,
        accentColor = Color(0xFFFB923C), // برتقالي حيوي
        isDeveloperOnly = false
    ),
    DEVELOPER(
        id = "developer",
        title = "أسرار المطور",
        iconEmoji = "🛠️",
        icon = Icons.Default.Code,
        accentColor = Color(0xFF00E5FF), // أزرق كوانتي سيبراني
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
 * 📚 المستودع الشامل للنصائح المتنوعة والغزيرة (Quran, Sunnah, Content Creation, Tazkiyah, Tech, Developer)
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
        QabasTipItem(
            id = "q8",
            category = TipCategory.QURAN,
            text = "«وَقُل رَّبِّ زِدْنِي عِلْمًا»",
            sourceOrBadge = "سورة طه : 114",
            actionableHint = "طلب الزيادة في العلم وتطوير المهارات النافعة ديدن الأنبياء والمصلحين."
        ),
        QabasTipItem(
            id = "q9",
            category = TipCategory.QURAN,
            text = "«إِنَّ مَعَ الْعُسْرِ يُسْرًا»",
            sourceOrBadge = "سورة الشرح : 6",
            actionableHint = "مع كل ضيق ومشقة يسر مضاعف، فاستبشر خيراً وواصل السعي."
        ),
        QabasTipItem(
            id = "q10",
            category = TipCategory.QURAN,
            text = "«وَأَحْسِنُوا ۛ إِنَّ اللَّهَ يُحِبُّ الْمُحْسِنِينَ»",
            sourceOrBadge = "سورة البقرة : 195",
            actionableHint = "الإحسان في أدق تفاصيل العمل يفتح لك محبة الله وتوفيقه."
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
        QabasTipItem(
            id = "s8",
            category = TipCategory.SUNNAH,
            text = "«عَجَبًا لأَمْرِ الْمُؤْمِنِ إِنَّ أَمْرَهُ كُلَّهُ خَيْرٌ»",
            sourceOrBadge = "صحيح مسلم",
            actionableHint = "شكر الله عند السراء وصبر جميل عند الضراء يضمن لك الخير في كل حال."
        ),
        QabasTipItem(
            id = "s9",
            category = TipCategory.SUNNAH,
            text = "«اتَّقِ اللَّهَ حَيْثُمَا كُنْتَ، وَأَتْبِعِ السَّيِّئَةَ الْحَسَنَةَ تَمْحُهَا»",
            sourceOrBadge = "جامع الترمذي",
            actionableHint = "مراقبة الله في السر والعلن وحسن الخلق مع الناس أصل الفلاح."
        ),
        QabasTipItem(
            id = "s10",
            category = TipCategory.SUNNAH,
            text = "«الدَّالُّ عَلَى الْخَيْرِ كَفَاعِلِهِ»",
            sourceOrBadge = "صحيح مسلم",
            actionableHint = "كل مقطع دعوي أو فائدة تنشرها يُكتب لك أجر كل من اهتدى بها."
        ),

        // ── 3. قسم صناعة المحتوى والمونتاج (Content Creation & Video 🎬✨) ──
        QabasTipItem(
            id = "c1",
            category = TipCategory.CREATIVE_STUDIO,
            text = "الثواني الثلاث الأولى هي كل شيء: ابدأ بسؤال جوهري أو لقطة آسرة تشد الانتباه فوراً.",
            sourceOrBadge = "قاعدة الهوك (Hook) 🪝",
            actionableHint = "تجنب المقدمات البطيئة في الريلز، واطرح الفكرة المشوقة في أول لحظة."
        ),
        QabasTipItem(
            id = "c2",
            category = TipCategory.CREATIVE_STUDIO,
            text = "وضوح الصوت ونقاؤه أهم من دقة الصورة: المشاهد يتجاوز جودة الفيديو لكنه يغلق إذا كان الصوت سيئاً.",
            sourceOrBadge = "هندسة الصوت 🎙️",
            actionableHint = "احرص على عزل الضوضاء وموازنة صوت التلاوة مع الخلفية الهادئة باعتدال."
        ),
        QabasTipItem(
            id = "c3",
            category = TipCategory.CREATIVE_STUDIO,
            text = "اختر لقطات B-Roll تدعم المشاعر والمعنى البصري، ولا تكتفِ بالترجمة الحرفية للكلمات.",
            sourceOrBadge = "اللقطات الداعمة 🎥",
            actionableHint = "تناغم حركة الكاميرا والضوء يضاعف من عمق التأثير ووصول الرسالة للقلوب."
        ),
        QabasTipItem(
            id = "c4",
            category = TipCategory.CREATIVE_STUDIO,
            text = "استخدم خطوطاً عربية واضحة ومقروءة (كخط القاهرة أو الأميري) مع تباين قوي عن الخلفية.",
            sourceOrBadge = "تايبوغرافي الفيديو ✍️",
            actionableHint = "الخط المقروء يتيح للمشاهد فهم الآية والحديث حتى دون تشغيل الصوت."
        ),
        QabasTipItem(
            id = "c5",
            category = TipCategory.CREATIVE_STUDIO,
            text = "غيّر زاوية العرض أو أضف حركة طفيفة كل 4 إلى 6 ثوانٍ للحفاظ على تفاعل المشاهد.",
            sourceOrBadge = "إيقاع المونتاج ⏱️",
            actionableHint = "الإيقاع البصري الديناميكي يمنع الملل ويزيد من معدل إكمال الفيديو للنهاية."
        ),
        QabasTipItem(
            id = "c6",
            category = TipCategory.CREATIVE_STUDIO,
            text = "ركّز في الفيديو على رسالة واحدة محددة وقوية بدلاً من حشو أفكار متعددة في مقطع قصير.",
            sourceOrBadge = "تركيز الفكرة 🎯",
            actionableHint = "الفكرة الواحدة المركزة تعلق في ذهن المشاهد ويسهل عليه مشاركتها."
        ),
        QabasTipItem(
            id = "c7",
            category = TipCategory.CREATIVE_STUDIO,
            text = "استخدم الألوان الدافئة لنقل الطمأنينة والألفة، والألوان الباردة للتأمل والوقار.",
            sourceOrBadge = "سيكولوجيا الألوان 🎨",
            actionableHint = "تدريج الألوان (Color Grading) يصنع هوية سينمائية مميزة لمشاريعك."
        ),
        QabasTipItem(
            id = "c8",
            category = TipCategory.CREATIVE_STUDIO,
            text = "اجعل خاتمة المقطع (Call to Action) دعوة للذكر أو الصلاة على النبي أو مشاركة الأجر.",
            sourceOrBadge = "الخاتمة المؤثرة 🌟",
            actionableHint = "الخاتمة الطيبة تترك أثراً روحانياً باقياً في نفس المشاهد."
        ),

        // ── 4. قسم التزكية والسكينة (Tazkiyah & Inner Peace 🌿🤍) ──
        QabasTipItem(
            id = "t1",
            category = TipCategory.TAZKIYAH,
            text = "اجعل لك خبيئة عمل صالح بينك وبين الله لا يعلمها أحد من خلقه؛ فهي حصن القلب.",
            sourceOrBadge = "سر الإخلاص 🕊️",
            actionableHint = "أعمال السر أعظم أسباب الثبات، ومغفرة الزلات، وتزكية النفس."
        ),
        QabasTipItem(
            id = "t2",
            category = TipCategory.TAZKIYAH,
            text = "كلما ضاق صدرك فافرغ دقائق للتسبيح والاستغفار؛ فإن الذكر يجلو صدأ القلوب.",
            sourceOrBadge = "دواء القلوب 💧",
            actionableHint = "«سُبْحَانَ اللَّهِ وَبِحَمْدِهِ» مائة مرة تمحو الخطايا وتملأ النفس سكينة."
        ),
        QabasTipItem(
            id = "t3",
            category = TipCategory.TAZKIYAH,
            text = "لا تحزن على ما فاتك، فما أصابك لم يكن ليخطئك، وما أخطأك لم يكن ليصيبك.",
            sourceOrBadge = "سكينة الرضا 🌸",
            actionableHint = "راحة البال الحقيقية في تمام اليقين بحكمة الله وتدبيره الخفي."
        ),
        QabasTipItem(
            id = "t4",
            category = TipCategory.TAZKIYAH,
            text = "ركعتا الضحى صدقة عن كل مفصل في جسدك، وصلاة الأوابين حين يغفل الناس.",
            sourceOrBadge = "صلاة الأوابين ☀️",
            actionableHint = "نافلة يسيرة في صدر النهار تفتح لك أبواب التوفيق والبركة."
        ),
        QabasTipItem(
            id = "t5",
            category = TipCategory.TAZKIYAH,
            text = "طهّر قلبك من الغل والحسد، وتمنَّ الخير للجميع تسعد ويهنأ عيشك وتستريح سريرتك.",
            sourceOrBadge = "سلامة الصدر 🤍",
            actionableHint = "نقاء الصدر من نعيم الدنيا المعجل وسبب لرفع الدرجات في الآخرة."
        ),
        QabasTipItem(
            id = "t6",
            category = TipCategory.TAZKIYAH,
            text = "قراءة وجه واحد من القرآن بتدبر وتأنٍّ خير من قراءة أجزاء بلا وعي ولا تأثر.",
            sourceOrBadge = "بركة التدبر 📖",
            actionableHint = "قف عند الآيات وتأمل خطاب الله لك كأنك المعني بها."
        ),
        QabasTipItem(
            id = "t7",
            category = TipCategory.TAZKIYAH,
            text = "الاستغفار بالأسحار وقت التنزل الإلهي يفتح الأقفال ويجلب الرزق ويكشف الغموم.",
            sourceOrBadge = "خلوة السحر 🌙",
            actionableHint = "دقائق قليلة قبل الفجر بالدعاء والاستغفار تصنع في حياتك العجائب."
        ),

        // ── 5. قسم الإنتاجية والتقنية (Productivity & Tech ⚡🚀) ──
        QabasTipItem(
            id = "p1",
            category = TipCategory.PRODUCTIVITY,
            text = "قاعدة الدقيقتين: أي مهمة دعوية أو كتابية تأخذ أقل من دقيقتين أنجزها فوراً ولا تؤجلها.",
            sourceOrBadge = "إدارة الوقت ⏳",
            actionableHint = "المبادرة بالمهام السريعة تصفي ذهنك وتمنع تراكم الأعباء المؤجلة."
        ),
        QabasTipItem(
            id = "p2",
            category = TipCategory.PRODUCTIVITY,
            text = "صيّد الخواطر: سجّل فكرة المحتوى أو الآية فور ورودها لذهنك قبل أن تنساها.",
            sourceOrBadge = "قيد الأفكار 💡",
            actionableHint = "استخدم زر التسجيل الصوتي أو المفكرة السريعة لاقتناص الأفكار الإبداعية."
        ),
        QabasTipItem(
            id = "p3",
            category = TipCategory.PRODUCTIVITY,
            text = "استثمر الأوقات البينية في المواصلات والانتظار بالاستماع للقرآن أو المحاضرات النافعة.",
            sourceOrBadge = "استثمار الفراغ 🎧",
            actionableHint = "ساعة يومياً في الأوقات الضائعة تعادل قراءة عشرات الكتب سنوياً."
        ),
        QabasTipItem(
            id = "p4",
            category = TipCategory.PRODUCTIVITY,
            text = "قسّم المشاريع الكبيرة إلى خطوات صغيرة قابلة للإنجاز في جلسات مدتها 25 دقيقة.",
            sourceOrBadge = "تقنية بومودورو 🍅",
            actionableHint = "التركيز المكثف بدون مشتتات يرفع جودة الإنتاج ويقلل الإجهاد."
        ),
        QabasTipItem(
            id = "p5",
            category = TipCategory.PRODUCTIVITY,
            text = "التوقف للصلاة وأخذ استراحة قصيرة ليس تضييعاً للوقت، بل إعادة شحن لطاقة الإبداع.",
            sourceOrBadge = "تجديد الطاقة 🔋",
            actionableHint = "الصلوات الخمس فواصل ربانية تعيد ضبط التركيز الذهني والنشاط الجسدي."
        ),
        QabasTipItem(
            id = "p6",
            category = TipCategory.PRODUCTIVITY,
            text = "احرص على أخذ نسخ احتياطية لمشاريعك بانتظام لتجنب فقدان جهد ساعات طويلة.",
            sourceOrBadge = "أمان البيانات 💾",
            actionableHint = "راحة البال تبدأ بالحفظ التلقائي والمزامنة السحابية الدورية."
        ),

        // ── 6. قسم أسرار المطور (Developer Pro Tips 🛠️⚡) ──
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
        ),
        QabasTipItem(
            id = "d8",
            category = TipCategory.DEVELOPER,
            text = "محرك FFmpegKit مدمج لإنتاج مقاطع الريلز بالأبعاد القياسية 9:16 و 16:9 بجودة سينمائية.",
            sourceOrBadge = "رندرة الفيديو 🎬",
            actionableHint = "يدعم تركيب الكابشن المتحرك ودمج الانتقالات ثلاثية الأبعاد دون الحاجة لخوادم سحابية."
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
 * 🛸 شريط النصائح والحِكم الذكي فائق التجميل (Qabas Smart Tips & Wisdom Bar)
 * مصمم بنمط Cybernetic Glassmorphic HUD يجمع بين الفخامة الإسلامية والأناقة الكوانتية.
 */
@Composable
fun QabasSmartTipsBar(
    modifier: Modifier = Modifier,
    isDeveloper: Boolean = false,
    onNavigateToDeveloperDashboard: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val primaryColor = qabasPrimary()

    // الأقسام المتاحة بحسب رتبة المستخدم
    val availableCategories = remember(isDeveloper) {
        if (isDeveloper) {
            TipCategory.values().toList()
        } else {
            TipCategory.values().filter { !it.isDeveloperOnly }
        }
    }

    var selectedCategoryFilter by remember { mutableStateOf<TipCategory?>(null) }
    
    val filteredTips = remember(isDeveloper, selectedCategoryFilter) {
        QabasTipsRepository.getTipsForUser(isDeveloper, selectedCategoryFilter)
    }

    var currentTipIndex by remember { mutableIntStateOf(0) }
    var isAutoRotating by remember { mutableStateOf(true) }
    var selectedTipForDialog by remember { mutableStateOf<QabasTipItem?>(null) }

    LaunchedEffect(filteredTips) {
        if (currentTipIndex >= filteredTips.size) {
            currentTipIndex = 0
        }
    }

    // مؤقت التدوير التلقائي السلس
    LaunchedEffect(isAutoRotating, filteredTips.size, selectedCategoryFilter) {
        if (isAutoRotating && filteredTips.isNotEmpty()) {
            while (true) {
                delay(6500L)
                currentTipIndex = (currentTipIndex + 1) % filteredTips.size
            }
        }
    }

    val currentTip = filteredTips.getOrNull(currentTipIndex) ?: filteredTips.firstOrNull()

    val pulseTransition = rememberInfiniteTransition(label = "tipsPulse")
    val glowAnim by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Reverse),
        label = "glowAnim"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── 1. شريط أزرار التصنيفات فائق الجمال (Modern Pill Filter Ribbon) ──
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
                // كبسولة "الكل"
                item {
                    val isAllSelected = selectedCategoryFilter == null
                    Surface(
                        onClick = {
                            AppSoundManager.playClick()
                            selectedCategoryFilter = null
                            currentTipIndex = 0
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isAllSelected) GoldPrimary.copy(alpha = 0.22f) else Color(0xFF0F172A).copy(alpha = 0.6f),
                        border = BorderStroke(
                            1.dp,
                            if (isAllSelected) GoldPrimary else Color(0xFF334155).copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 9.dp)
                        ) {
                            Text(
                                "✦ الكل",
                                color = if (isAllSelected) GoldPrimary else TextSecondary,
                                fontFamily = CairoFont,
                                fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 10.5.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = CircleShape,
                                color = if (isAllSelected) GoldPrimary else Color(0xFF1E293B)
                            ) {
                                Text(
                                    text = "${QabasTipsRepository.getTipsForUser(isDeveloper, null).size}",
                                    color = if (isAllSelected) DeepSlate else TextSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                // كبسولات التصنيفات الستة المتنوعة
                items(availableCategories) { cat ->
                    val isSelected = selectedCategoryFilter == cat
                    val catTipsCount = remember(cat, isDeveloper) {
                        QabasTipsRepository.getTipsForUser(isDeveloper, cat).size
                    }

                    Surface(
                        onClick = {
                            AppSoundManager.playClick()
                            selectedCategoryFilter = cat
                            currentTipIndex = 0
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) cat.accentColor.copy(alpha = 0.22f) else Color(0xFF0F172A).copy(alpha = 0.6f),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) cat.accentColor else Color(0xFF334155).copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(cat.iconEmoji, fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = cat.title,
                                color = if (isSelected) cat.accentColor else TextSecondary,
                                fontFamily = CairoFont,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) cat.accentColor else Color(0xFF1E293B)
                            ) {
                                Text(
                                    text = "$catTipsCount",
                                    color = if (isSelected) DeepSlate else TextSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // زر التدوير التلقائي الذكي والأنيق
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
                shape = RoundedCornerShape(16.dp),
                color = if (isAutoRotating) primaryColor.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, if (isAutoRotating) primaryColor.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.height(28.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 7.dp)
                ) {
                    Icon(
                        imageVector = if (isAutoRotating) Icons.Default.Autorenew else Icons.Default.PauseCircle,
                        contentDescription = "تبديل التدوير التلقائي",
                        tint = if (isAutoRotating) primaryColor else Color.Gray,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (isAutoRotating) "تدوير حي" else "موقوف",
                        color = if (isAutoRotating) primaryColor else Color.Gray,
                        fontFamily = CairoFont,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ── 2. بطاقة شريط النصيحة الكبسولي الفاخر (Holographic Glassmorphic Bar) ──
        if (currentTip != null) {
            val tipColor = currentTip.category.accentColor

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(16.dp), spotColor = tipColor.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF090E1D).copy(alpha = 0.94f),
                border = BorderStroke(
                    1.2.dp,
                    Brush.horizontalGradient(
                        listOf(
                            tipColor.copy(alpha = glowAnim * 0.8f),
                            tipColor.copy(alpha = 0.3f),
                            GoldPrimary.copy(alpha = 0.4f)
                        )
                    )
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            AppSoundManager.playClick()
                            // النقر على الشريط ينقل للنصيحة التالية بسلاسة
                            currentTipIndex = (currentTipIndex + 1) % filteredTips.size
                        }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // الشارة التصنيفية الجذابة على اليمين مع أيقونة ونقطة نابضة
                    Surface(
                        color = tipColor.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.9.dp, tipColor.copy(alpha = 0.6f)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(currentTip.category.iconEmoji, fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = currentTip.category.title,
                                color = tipColor,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp
                            )
                        }
                    }

                    // المحتوى النصي مع حركة انزلاقية ناعمة
                    Box(modifier = Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = currentTip,
                            transitionSpec = {
                                (slideInVertically { height -> height / 2 } + fadeIn(tween(200))).togetherWith(
                                    slideOutVertically { height -> -height / 2 } + fadeOut(tween(180))
                                )
                            },
                            label = "tipBarTextAnimation"
                        ) { tip ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = tip.text,
                                    color = Color.White,
                                    fontFamily = if (tip.category == TipCategory.QURAN || tip.category == TipCategory.SUNNAH) AmiriFont else CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF1E293B).copy(alpha = 0.8f)
                                ) {
                                    Text(
                                        text = tip.sourceOrBadge,
                                        color = tipColor,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }

                    // أزرار التحكم السريعة في أقصى اليسار (السابق / التالي / التفاصيل)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // زر النصيحة السابقة
                        IconButton(
                            onClick = {
                                AppSoundManager.playClick()
                                currentTipIndex = if (currentTipIndex - 1 < 0) filteredTips.size - 1 else currentTipIndex - 1
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "السابق",
                                tint = TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        // زر النصيحة التالية
                        IconButton(
                            onClick = {
                                AppSoundManager.playClick()
                                currentTipIndex = (currentTipIndex + 1) % filteredTips.size
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "التالي",
                                tint = TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        // زر فتح تفاصيل النصيحة والنسخ
                        IconButton(
                            onClick = {
                                AppSoundManager.playClick()
                                selectedTipForDialog = currentTip
                            },
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(tipColor.copy(alpha = 0.14f))
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
    }

    // ── 3. نافذة تفاصيل النصيحة الفاخرة (Expanded Tip Dialog) ──
    if (selectedTipForDialog != null) {
        val tip = selectedTipForDialog!!
        val tipColor = tip.category.accentColor

        Dialog(onDismissRequest = { selectedTipForDialog = null }) {
            GlassmorphicSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
                    .shadow(28.dp, RoundedCornerShape(24.dp), spotColor = tipColor.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(24.dp),
                backgroundColor = Color(0xFF070B14).copy(alpha = 0.97f),
                borderColor = tipColor.copy(alpha = 0.65f)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
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
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, tipColor.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(tip.category.iconEmoji, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = tip.category.title,
                                        color = tipColor,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            if (tip.category.isDeveloperOnly) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "مخصص للمطور 🔒",
                                        color = Color(0xFF00E5FF),
                                        fontFamily = CairoFont,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { selectedTipForDialog = null },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B).copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.Gray, modifier = Modifier.size(18.dp))
                        }
                    }

                    // بطاقة نص الآية / الحديث / النصيحة
                    Surface(
                        color = Color(0xFF0F172A).copy(alpha = 0.85f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, tipColor.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = tip.text,
                                color = Color.White,
                                fontFamily = if (tip.category == TipCategory.QURAN || tip.category == TipCategory.SUNNAH) AmiriFont else CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (tip.category == TipCategory.QURAN || tip.category == TipCategory.SUNNAH) 17.sp else 14.sp,
                                lineHeight = 24.sp,
                                textAlign = TextAlign.Start
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.BookmarkBorder,
                                    contentDescription = null,
                                    tint = tipColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "المصدر والتصنيف: ${tip.sourceOrBadge}",
                                    color = tipColor,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }

                    // التوجيه والفائدة العملية
                    if (tip.actionableHint != null) {
                        Surface(
                            color = Color(0xFF030712),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.8.dp, Color(0xFF334155).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "التوجيه والفائدة العملية:",
                                        color = GoldPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                }
                                Text(
                                    text = tip.actionableHint,
                                    color = Color(0xFFCBD5E1),
                                    fontFamily = CairoFont,
                                    fontSize = 11.5.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }

                    // أزرار العمليات (نسخ النص / الانتقال للوحة المطور)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                AppSoundManager.playSuccess()
                                val shareText = "${tip.text}\n— ${tip.sourceOrBadge}\n\n${tip.actionableHint ?: ""}\n(عبر استوديو قبس ✨)"
                                clipboardManager.setText(AnnotatedString(shareText))
                                Toast.makeText(context, "تم نسخ النص إلى الحافظة بنجاح! 📋", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = tipColor)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "نسخ النص 📋",
                                color = DeepSlate,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                        }

                        if (tip.category == TipCategory.DEVELOPER && onNavigateToDeveloperDashboard != null) {
                            OutlinedButton(
                                onClick = {
                                    AppSoundManager.playClick()
                                    selectedTipForDialog = null
                                    onNavigateToDeveloperDashboard()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.2.dp, Color(0xFF00E5FF)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF))
                            ) {
                                Icon(Icons.Default.Dashboard, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "لوحة المطور 🛠️",
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
