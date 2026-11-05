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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
        title = "آية وتفسير",
        iconEmoji = "📖",
        icon = Icons.Default.MenuBook,
        accentColor = Color(0xFF10B981), // زمردي قرآني
        isDeveloperOnly = false
    ),
    SUNNAH(
        id = "sunnah",
        title = "أحاديث نبوية",
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

    fun getTipsForUser(
        isDeveloper: Boolean,
        selectedCategory: TipCategory?,
        quranTips: List<QabasTipItem>
    ): List<QabasTipItem> {
        val combinedTips = allTips + quranTips
        val allowedTips = if (isDeveloper) {
            combinedTips
        } else {
            combinedTips.filter { !it.category.isDeveloperOnly }
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
    val quranTips by produceState(initialValue = emptyList<QabasTipItem>(), context) {
        value = withContext(Dispatchers.IO) { QabasQuranData.load(context) }
    }

    // الأقسام المتاحة بحسب رتبة المستخدم
    val availableCategories = remember(isDeveloper) {
        if (isDeveloper) {
            TipCategory.values().toList()
        } else {
            TipCategory.values().filter { !it.isDeveloperOnly }
        }
    }

    var selectedCategoryFilter by remember { mutableStateOf<TipCategory?>(null) }
    var isCategoryMenuExpanded by remember { mutableStateOf(false) }
    
    val filteredTips = remember(isDeveloper, selectedCategoryFilter, quranTips) {
        QabasTipsRepository.getTipsForUser(isDeveloper, selectedCategoryFilter, quranTips)
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
        // ── 1. تصنيفات واضحة ومتناسقة (Modern Category Filters) ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                val selectedCategory = selectedCategoryFilter
                val selectedCategoryTitle = selectedCategory?.title ?: "كل التصنيفات"
                val selectedCategoryColor = selectedCategory?.accentColor ?: primaryColor
                val selectedCategoryCount = QabasTipsRepository
                    .getTipsForUser(isDeveloper, selectedCategory, quranTips)
                    .size

                Surface(
                    onClick = {
                        AppSoundManager.playClick()
                        isCategoryMenuExpanded = true
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = selectedCategoryColor.copy(alpha = 0.13f),
                    border = BorderStroke(1.dp, selectedCategoryColor.copy(alpha = 0.58f)),
                    modifier = Modifier
                        .height(36.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = null,
                            tint = selectedCategoryColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = selectedCategoryTitle,
                            color = selectedCategoryColor,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            shape = CircleShape,
                            color = selectedCategoryColor.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = selectedCategoryCount.toString(),
                                color = selectedCategoryColor,
                                fontFamily = CairoFont,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Icon(
                            imageVector = if (isCategoryMenuExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isCategoryMenuExpanded) "إخفاء التصنيفات" else "عرض التصنيفات",
                            tint = selectedCategoryColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = isCategoryMenuExpanded,
                    onDismissRequest = { isCategoryMenuExpanded = false },
                    modifier = Modifier
                        .widthIn(min = 210.dp)
                        .background(
                            Color(0xF218263A),
                            RoundedCornerShape(16.dp)
                        )
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.16f),
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("✦", fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(9.dp))
                                Text(
                                    "كل التصنيفات",
                                    color = if (selectedCategoryFilter == null) primaryColor else Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = if (selectedCategoryFilter == null) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    QabasTipsRepository.getTipsForUser(isDeveloper, null, quranTips).size.toString(),
                                    color = TextSecondary,
                                    fontSize = 9.sp
                                )
                            }
                        },
                        onClick = {
                            AppSoundManager.playClick()
                            selectedCategoryFilter = null
                            currentTipIndex = 0
                            isCategoryMenuExpanded = false
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 5.dp)
                    )

                    availableCategories.forEach { cat ->
                        val isSelected = selectedCategoryFilter == cat
                        val catTipsCount = QabasTipsRepository.getTipsForUser(isDeveloper, cat, quranTips).size

                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(cat.iconEmoji, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.width(9.dp))
                                    Text(
                                        cat.title,
                                        color = if (isSelected) cat.accentColor else Color.White,
                                        fontFamily = CairoFont,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        catTipsCount.toString(),
                                        color = if (isSelected) cat.accentColor else TextSecondary,
                                        fontSize = 9.sp
                                    )
                                }
                            },
                            onClick = {
                                AppSoundManager.playClick()
                                selectedCategoryFilter = cat
                                currentTipIndex = 0
                                isCategoryMenuExpanded = false
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Surface(
                onClick = {
                    AppSoundManager.playToggle()
                    val shouldRotate = !isAutoRotating
                    isAutoRotating = shouldRotate
                    Toast.makeText(
                        context,
                        if (shouldRotate) "تم استئناف التدوير التلقائي ⏱️" else "تم إيقاف التدوير مؤقتاً ⏸️",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                shape = RoundedCornerShape(18.dp),
                color = if (isAutoRotating) primaryColor.copy(alpha = 0.14f) else Color(0xFF111827),
                border = BorderStroke(
                    1.dp,
                    if (isAutoRotating) primaryColor.copy(alpha = 0.75f) else Color(0xFF475569).copy(alpha = 0.7f)
                ),
                modifier = Modifier.height(36.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 9.dp)
                ) {
                    Icon(
                        imageVector = if (isAutoRotating) Icons.Default.Autorenew else Icons.Default.PauseCircle,
                        contentDescription = if (isAutoRotating) "التدوير التلقائي يعمل" else "التدوير التلقائي متوقف",
                        tint = if (isAutoRotating) primaryColor else TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isAutoRotating) "تدوير حي" else "متوقف",
                        color = if (isAutoRotating) primaryColor else TextSecondary,
                        fontFamily = CairoFont,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ── 2. شريط حكمة أوضح بهوية قبس (Refined Wisdom Ribbon) ──
        if (currentTip != null) {
            val tipColor = currentTip.category.accentColor

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(10.dp, RoundedCornerShape(18.dp), spotColor = tipColor.copy(alpha = 0.18f)),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF081426).copy(alpha = 0.97f),
                border = BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            tipColor.copy(alpha = glowAnim * 0.72f),
                            tipColor.copy(alpha = 0.28f),
                            GoldPrimary.copy(alpha = 0.5f)
                        )
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(currentTip.category.iconEmoji, fontSize = 10.sp)
                            Text(
                                text = currentTip.category.title,
                                color = tipColor.copy(alpha = 0.82f),
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Medium,
                                fontSize = 8.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        IconButton(
                            onClick = {
                                AppSoundManager.playClick()
                                selectedTipForDialog = currentTip
                            },
                            modifier = Modifier
                                .size(25.dp)
                                .clip(CircleShape)
                                .background(tipColor.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "فتح تفاصيل النصيحة",
                                tint = tipColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    AnimatedContent(
                        targetState = currentTip,
                        modifier = Modifier.fillMaxWidth(),
                        transitionSpec = {
                            (slideInVertically { height -> height / 3 } + fadeIn(tween(220))).togetherWith(
                                slideOutVertically { height -> -height / 3 } + fadeOut(tween(180))
                            )
                        },
                        label = "tipBarTextAnimation"
                    ) { tip ->
                        Column(
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = tip.text,
                                color = Color.White,
                                fontFamily = if (tip.category == TipCategory.QURAN || tip.category == TipCategory.SUNNAH) AmiriFont else CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (tip.category == TipCategory.QURAN || tip.category == TipCategory.SUNNAH) 13.sp else 11.5.sp,
                                lineHeight = 18.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.BookmarkBorder,
                                    contentDescription = null,
                                    tint = tip.category.accentColor,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = tip.sourceOrBadge,
                                    color = tip.category.accentColor,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 3. نافذة التفاصيل (Compact, Scrollable Wisdom Dialog) ──
    if (selectedTipForDialog != null) {
        val tip = selectedTipForDialog!!
        val tipColor = tip.category.accentColor
        val isScripture = tip.category == TipCategory.QURAN || tip.category == TipCategory.SUNNAH

        Dialog(onDismissRequest = { selectedTipForDialog = null }) {
            GlassmorphicSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp)
                    .shadow(24.dp, RoundedCornerShape(24.dp), spotColor = tipColor.copy(alpha = 0.24f)),
                shape = RoundedCornerShape(24.dp),
                backgroundColor = Color(0xFF071326).copy(alpha = 0.98f),
                borderColor = tipColor.copy(alpha = 0.52f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 620.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            color = tipColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, tipColor.copy(alpha = 0.62f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(tip.category.iconEmoji, fontSize = 15.sp)
                                Spacer(modifier = Modifier.width(7.dp))
                                Text(
                                    text = tip.category.title,
                                    color = tipColor,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                )
                            }
                        }

                        if (tip.category.isDeveloperOnly) {
                            Surface(
                                color = Color(0xFF00E5FF).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.32f))
                            ) {
                                Text(
                                    text = "للمطور 🔒",
                                    color = Color(0xFF00E5FF),
                                    fontFamily = CairoFont,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { selectedTipForDialog = null },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF172338))
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "إغلاق التفاصيل",
                                tint = TextSecondary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Surface(
                        color = Color(0xFF0B1930),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, tipColor.copy(alpha = 0.34f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(13.dp)
                        ) {
                            Text(
                                text = tip.text,
                                color = Color(0xFFF8FAFC),
                                fontFamily = if (isScripture) AmiriFont else CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (isScripture) 20.sp else 16.sp,
                                lineHeight = if (isScripture) 31.sp else 25.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.Transparent,
                                                tipColor.copy(alpha = 0.5f),
                                                GoldPrimary.copy(alpha = 0.5f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.BookmarkBorder,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = (if (tip.category == TipCategory.QURAN) "موضع الآية: " else "المصدر والتصنيف: ") + tip.sourceOrBadge,
                                    color = GoldPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    if (!tip.actionableHint.isNullOrBlank()) {
                        Surface(
                            color = Color(0xFF071C2C),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFF00CFE8).copy(alpha = 0.46f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Surface(
                                        color = GoldPrimary.copy(alpha = 0.14f),
                                        shape = CircleShape
                                    ) {
                                        Icon(
                                            Icons.Default.Lightbulb,
                                            contentDescription = null,
                                            tint = GoldPrimary,
                                            modifier = Modifier.padding(6.dp).size(18.dp)
                                        )
                                    }
                                    Text(
                                        text = if (tip.category == TipCategory.QURAN) "التفسير الميسر" else "التوجيه والفائدة العملية",
                                        color = Color(0xFF23D7E8),
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Text(
                                    text = tip.actionableHint,
                                    color = Color(0xFFE2E8F0),
                                    fontFamily = CairoFont,
                                    fontSize = 13.sp,
                                    lineHeight = 22.sp,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                AppSoundManager.playSuccess()
                                val copyText = listOfNotNull(
                                    tip.text,
                                    "— " + tip.sourceOrBadge,
                                    tip.actionableHint?.takeIf { it.isNotBlank() },
                                    "(عبر استوديو قبس ✨)"
                                ).joinToString("\n\n")
                                clipboardManager.setText(AnnotatedString(copyText))
                                Toast.makeText(context, "تم نسخ النص إلى الحافظة بنجاح! 📋", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = DeepSlate,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                "نسخ النص",
                                color = DeepSlate,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
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
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.2.dp, Color(0xFF00D8EA)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00D8EA))
                            ) {
                                Icon(Icons.Default.Dashboard, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "لوحة المطور",
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
