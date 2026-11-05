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
import androidx.compose.ui.platform.LocalConfiguration
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
import kotlinx.coroutines.launch

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
        title = "دليل استوديو قبس",
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
        title = "الذكاء الاصطناعي للمسلم",
        iconEmoji = "🤖",
        icon = Icons.Default.Bolt,
        accentColor = Color(0xFF60A5FA), // أزرق تقني
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
    val actionableHint: String? = null,
    val bukhariHadith: BukhariHadith? = null
)

/**
 * 📚 المستودع الشامل للنصائح المتنوعة والغزيرة (Quran, Sunnah, Content Creation, Tazkiyah, Tech, Developer)
 */
object QabasTipsRepository {
    val allTips: List<QabasTipItem> = listOf(
        // صحيح البخاري يُحمّل من ملف JSON المستورد محلياً.

        // ── 3. دليل استوديو قبس (Qabas Studio Guide) ──
        QabasTipItem(
            id = "sg1",
            category = TipCategory.CREATIVE_STUDIO,
            text = "ابدأ من بوابة الاستوديو الموحّد، ثم اختر مسار الفيديو أو الصور أو الصوت بحسب المخرج الذي تريد إنتاجه.",
            sourceOrBadge = "التعرّف على الاستوديو 🛸",
            actionableHint = "الفيديوهات تضم أدوات التوليد والمونتاج، والصور تضم أدوات التصميم والوسائط، والصوت يضم أدوات التعليق والدبلجة والملقّن."
        ),
        QabasTipItem(
            id = "sg2",
            category = TipCategory.CREATIVE_STUDIO,
            text = "في مسار الفيديو، يتيح Higgsfield AI إنشاء مقاطع انطلاقاً من النصوص أو الصور.",
            sourceOrBadge = "توليد الفيديو 🎬",
            actionableHint = "جهّز وصف المشهد أو الصورة المرجعية قبل بدء التوليد، ثم راجع الناتج قبل استخدامه."
        ),
        QabasTipItem(
            id = "sg3",
            category = TipCategory.CREATIVE_STUDIO,
            text = "استخدم MoneyPrinter Turbo لإعداد مقاطع قصيرة عبر مسار يجمع السكريبت والصوت ولقطات B-Roll والرندر.",
            sourceOrBadge = "إنتاج الريلز آلياً ⚡",
            actionableHint = "راجع النص والصوت واللقطات الناتجة قبل اعتماد الفيديو النهائي."
        ),
        QabasTipItem(
            id = "sg4",
            category = TipCategory.CREATIVE_STUDIO,
            text = "انتقل إلى Pro Editor عندما تحتاج إلى تحكم أدق في المقاطع والمسارات والطبقات والنصوص والصوت.",
            sourceOrBadge = "المحرر الاحترافي ✂️",
            actionableHint = "رتّب عناصر الفيديو على الخط الزمني، ثم راجع المعاينة واحفظ المشروع أثناء العمل."
        ),
        QabasTipItem(
            id = "sg5",
            category = TipCategory.CREATIVE_STUDIO,
            text = "في استوديو الصور، يمكنك الاختيار بين تصميمات دعوية مثل القرآن والأذكار والحديث والقصة، أو البدء بتصميم حر.",
            sourceOrBadge = "تصميم الصور 📸",
            actionableHint = "بعد اختيار الصور أو القالب، عدّل النص والعناصر البصرية ثم راجع إعدادات التصدير."
        ),
        QabasTipItem(
            id = "sg6",
            category = TipCategory.CREATIVE_STUDIO,
            text = "استوديو الصوت يوفّر مسارات لاستنساخ الأصوات وتوليد الكلام ودبلجة الفيديوهات.",
            sourceOrBadge = "VoiceStudio 🎙️",
            actionableHint = "اختر المسار المناسب، وجهّز العينة الصوتية أو النص أو الفيديو المطلوب، ثم استمع إلى الناتج وراجعه."
        ),
        QabasTipItem(
            id = "sg7",
            category = TipCategory.CREATIVE_STUDIO,
            text = "استخدم ملقّن الكاميرا لقراءة النص أثناء التصوير بدلاً من حفظ السيناريو كاملاً.",
            sourceOrBadge = "Teleprompter 📹",
            actionableHint = "حضّر النص مسبقاً وقسّمه إلى جمل واضحة لتسهيل الإلقاء أمام الكاميرا."
        ),
        QabasTipItem(
            id = "sg8",
            category = TipCategory.CREATIVE_STUDIO,
            text = "استوديو يوتيوب يتضمن أدوات لمعالجة الفيديو مثل إزالة الصمت وتنقية الصوت وتوليد الفصول الزمنية وإضافة الترجمة.",
            sourceOrBadge = "تحسين الفيديو 📺",
            actionableHint = "اختر الأداة المناسبة للفيديو، ثم راجع الملف الناتج أو الفصول التي تم إنشاؤها قبل النشر."
        ),

        // ── 4. قسم التزكية والسكينة (Tazkiyah & Inner Peace) ──
        QabasTipItem(
            id = "t1",
            category = TipCategory.TAZKIYAH,
            text = "اجعل لك عملًا صالحًا خفيًا لا تطّلع عليه أعين الناس، وابتغِ به وجه الله وحده.",
            sourceOrBadge = "خبيئة الإخلاص 🕊️",
            actionableHint = "اختر عبادة تستطيع المداومة عليها في السر، كصدقة أو ركعتين أو دعاء، ولا تجعل إخفاء العمل سببًا لترك العمل المعلن المشروع."
        ),
        QabasTipItem(
            id = "t2",
            category = TipCategory.TAZKIYAH,
            text = "إذا ضاق صدرك، فالجأ إلى ذكر الله، وخذ من يومك دقائق للتسبيح والاستغفار والدعاء.",
            sourceOrBadge = "دواء القلوب 💧",
            actionableHint = "ردد أذكارًا ثابتة تعرف معناها، مثل سبحان الله والحمد لله ولا إله إلا الله والله أكبر، واجعل الذكر حاضرًا دون اعتقاد عدد أو فضل لم يثبت."
        ),
        QabasTipItem(
            id = "t3",
            category = TipCategory.TAZKIYAH,
            text = "ارضَ بقضاء الله، ولا تجعل ما فاتك يحجب عنك ما تستطيع إصلاحه والعمل له.",
            sourceOrBadge = "سكينة الرضا 🌸",
            actionableHint = "عند وقوع أمر لا تحبه، سلّم لله فيما لا تملك تغييره، وخذ بالأسباب المشروعة فيما تستطيع تغييره."
        ),
        QabasTipItem(
            id = "t4",
            category = TipCategory.TAZKIYAH,
            text = "حافظ على صلاة الضحى، واجعل لك نصيبًا من النوافل إلى جانب الفرائض.",
            sourceOrBadge = "صلاة الضحى ☀️",
            actionableHint = "ابدأ بركعتين في وقت الضحى، من بعد ارتفاع الشمس إلى قبيل الزوال، ثم زد إذا تيسر لك."
        ),
        QabasTipItem(
            id = "t5",
            category = TipCategory.TAZKIYAH,
            text = "طهّر قلبك من الحقد والحسد، ودرّب نفسك على محبة الخير للناس.",
            sourceOrBadge = "سلامة الصدر 🤍",
            actionableHint = "إذا رأيت نعمة عند غيرك فادعُ له بالبركة، واسأل الله من فضله، ولا تتبع ذلك بتمني زوال النعمة عنه."
        ),
        QabasTipItem(
            id = "t6",
            category = TipCategory.TAZKIYAH,
            text = "لا تجعل قراءتك للقرآن عادة لسان فقط؛ اقرأ بتأنٍّ، وتوقف عند المعاني، واسأل نفسك كيف تعمل بالآية.",
            sourceOrBadge = "بركة التدبر 📖",
            actionableHint = "اختر آية واحدة بعد القراءة، وافهم معناها من تفسير موثوق، ثم دوّن عملًا واحدًا تهديك إليه."
        ),
        QabasTipItem(
            id = "t7",
            category = TipCategory.TAZKIYAH,
            text = "اجعل للسحر نصيبًا من دعائك واستغفارك، ولو بدقائق يسيرة قبل الفجر.",
            sourceOrBadge = "خلوة السحر 🌙",
            actionableHint = "ابدأ بالاستيقاظ قبل الفجر بوقت قصير، وصلّ ما تيسر، واستغفر وادعُ الله؛ دون الجزم بفضائل مخصوصة لم يثبت دليلها."
        ),
        QabasTipItem(
            id = "t8",
            category = TipCategory.TAZKIYAH,
            text = "حاسب نفسك قبل النوم، ولا تنتقل إلى يوم جديد دون أن تتأمل ما أحسنت فيه وما قصّرت.",
            sourceOrBadge = "محاسبة النفس 🪞",
            actionableHint = "اسأل نفسك: ما العمل الصالح الذي أداوم عليه؟ وما الخطأ الذي أحتاج إلى إصلاحه؟ ثم ضع خطوة عملية للغد."
        ),
        QabasTipItem(
            id = "t9",
            category = TipCategory.TAZKIYAH,
            text = "إذا أخطأت، فلا تجعل التبرير يحول بينك وبين الاعتراف بالخطأ وإصلاح أثره.",
            sourceOrBadge = "الشجاعة في الاعتراف ⚖️",
            actionableHint = "سمِّ الخطأ بوضوح، واعتذر لمن تضرر، وأعد الحق إلى صاحبه إن تعلق الأمر بحقوق الناس."
        ),
        QabasTipItem(
            id = "t10",
            category = TipCategory.TAZKIYAH,
            text = "جدّد نيتك قبل أعمالك وأثناءها؛ فالعمل الواحد قد يختلف أجره باختلاف القصد.",
            sourceOrBadge = "مراقبة النية 🎯",
            actionableHint = "قبل أن تبدأ، اسأل نفسك: ما الغاية من هذا العمل؟ واجعل قصدك صالحًا، مع أداء العمل بإتقان."
        ),
        QabasTipItem(
            id = "t11",
            category = TipCategory.TAZKIYAH,
            text = "قاوم الحسد بأن تتمنى للمسلمين الخير، وأن تنشغل بإصلاح حالك بدل مقارنة أرزاقك بأرزاقهم.",
            sourceOrBadge = "مجاهدة الحسد 🌱",
            actionableHint = "عندما تقارن نفسك بغيرك، اذكر نعمة لديك، وادعُ لصاحب النعمة بالبركة، ثم اعمل على هدفك المشروع."
        ),
        QabasTipItem(
            id = "t12",
            category = TipCategory.TAZKIYAH,
            text = "التواضع لا يعني إنكار النعمة أو التقليل من قدراتك؛ بل أن تعرف الحق ولا تتعالى على الناس.",
            sourceOrBadge = "خلق التواضع 🌾",
            actionableHint = "اقبل النصيحة إذا كانت صحيحة، وانسب الفضل إلى الله، ولا تسخر من أحد أو تحتقره."
        ),
        QabasTipItem(
            id = "t13",
            category = TipCategory.TAZKIYAH,
            text = "احذر أن يصبح ثناء الناس هو الدافع الأساسي لعملك الصالح؛ واطلب بعملك رضا الله.",
            sourceOrBadge = "مقاومة الرياء 🕯️",
            actionableHint = "لا تترك العمل خوفًا من الرياء، بل صحح نيتك، واستمر في الطاعة، واستعن بالله على الإخلاص."
        ),
        QabasTipItem(
            id = "t14",
            category = TipCategory.TAZKIYAH,
            text = "لا تؤجل الطاعة أو التوبة التي تقدر عليها الآن؛ فالتسويف يراكم الأعمال ويضعف العزم.",
            sourceOrBadge = "مقاومة التسويف ⏳",
            actionableHint = "حوّل العمل إلى خطوة صغيرة محددة، وحدد وقتًا لتنفيذها اليوم بدل انتظار الوقت المثالي."
        ),
        QabasTipItem(
            id = "t15",
            category = TipCategory.TAZKIYAH,
            text = "غض البصر يبدأ بقرار واعٍ بصرف النظر عما لا يحل، والابتعاد عن أسباب تكرار النظر المحرم.",
            sourceOrBadge = "حفظ البصر 👁️",
            actionableHint = "أغلق المصدر الذي يعرض عليك المحرمات، ولا تواصل التصفح بدافع الفضول، واستبدل به محتوى نافعًا."
        ),
        QabasTipItem(
            id = "t16",
            category = TipCategory.TAZKIYAH,
            text = "احفظ لسانك من الغيبة والسخرية ونقل الكلام؛ فالكلمة مسؤولية وليست أمرًا عابرًا.",
            sourceOrBadge = "حفظ اللسان 🤐",
            actionableHint = "قبل أن تتكلم، اسأل: هل في كلامي خير؟ وإذا بدأ مجلس بالغيبة فغيّر الموضوع أو أنكر بلطف إن استطعت."
        ),
        QabasTipItem(
            id = "t17",
            category = TipCategory.TAZKIYAH,
            text = "عند الغضب، أبطئ ردّك ولا تتخذ قرارًا أو ترسل رسالة وأنت في ذروة الانفعال.",
            sourceOrBadge = "ضبط الغضب 🌊",
            actionableHint = "اسكت حتى تهدأ، واستعذ بالله، وغيّر وضعك أو ابتعد قليلًا، ثم عالج الموقف بكلام منضبط."
        ),
        QabasTipItem(
            id = "t18",
            category = TipCategory.TAZKIYAH,
            text = "أحسن الظن بالله، ولا تيأس من رحمته عند التعثر؛ واجمع بين الرجاء والعمل والتوبة.",
            sourceOrBadge = "حسن الظن بالله 🌤️",
            actionableHint = "إذا أخفقت، لا تستسلم ولا تتكل على الأماني؛ استغفر، وتعلّم من السبب، وابدأ من جديد."
        ),
        QabasTipItem(
            id = "t19",
            category = TipCategory.TAZKIYAH,
            text = "عوّد قلبك ملاحظة النعم التي اعتدت وجودها، واشكر الله عليها قولًا وعملًا.",
            sourceOrBadge = "شكر النعم 🌼",
            actionableHint = "اكتب كل يوم ثلاث نعم، واختر طريقة عملية لشكر واحدة منها، كاستعمال صحتك في نفع أو مساعدة محتاج."
        ),
        QabasTipItem(
            id = "t20",
            category = TipCategory.TAZKIYAH,
            text = "لا تجعل تكرار الذنب يدفعك إلى ترك التوبة؛ بادر بالرجوع إلى الله كلما أذنبت.",
            sourceOrBadge = "باب التوبة مفتوح 🤲",
            actionableHint = "أقلع عن الذنب، واندم عليه، واعزم بصدق ألا تعود، وردّ حقوق الناس إن تعلّق الذنب بها. وإذا عدت فجدّد التوبة."
        ),

        // ── 5. الذكاء الاصطناعي للمسلم (AI for Muslims) ──
        QabasTipItem(
            id = "ai1",
            category = TipCategory.PRODUCTIVITY,
            text = "الذكاء الاصطناعي التوليدي لا يقتصر على كتابة النصوص؛ فالنماذج متعددة الوسائط تستطيع فهم النص والصورة والصوت والفيديو ضمن تجربة واحدة.",
            sourceOrBadge = "افهم تعدد الوسائط 🧩",
            actionableHint = "يمكنك مثلاً الاستفادة منه في تلخيص محاضرة مسجلة، واستخراج أفكارها، أو شرح رسم تعليمي؛ مع مراجعة النتائج."
        ),
        QabasTipItem(
            id = "ai2",
            category = TipCategory.PRODUCTIVITY,
            text = "تتجه تطبيقات الذكاء الاصطناعي إلى الوكلاء الذكيين (AI Agents) القادرين على التخطيط وتنفيذ خطوات متعددة باستخدام الأدوات.",
            sourceOrBadge = "الوكلاء الذكيون 🤖",
            actionableHint = "كلّف الوكيل بمهمة محددة مثل تنظيم مصادر بحث أو إعداد مسودة خطة، وحدد له ما يُسمح له بفعله وراجع كل إجراء مهم قبل اعتماده."
        ),
        QabasTipItem(
            id = "ai3",
            category = TipCategory.PRODUCTIVITY,
            text = "تقنية التوليد المعزز بالاسترجاع (RAG) تربط إجابة النموذج بمصادر وملفات محددة بدلاً من الاعتماد على ذاكرته التدريبية وحدها.",
            sourceOrBadge = "الإجابة المدعومة بالمصادر 📚",
            actionableHint = "عند البحث في مسألة شرعية، اطلب النص من مصدر موثوق ورابطه وموضعه، ثم ارجع إلى المصدر الأصلي؛ وجود مرجع في الإجابة لا يضمن صحته."
        ),
        QabasTipItem(
            id = "ai4",
            category = TipCategory.PRODUCTIVITY,
            text = "اكتب طلبك للذكاء الاصطناعي بوضوح: حدّد الهدف، والسياق، والجمهور، والقيود، وشكل النتيجة المطلوبة.",
            sourceOrBadge = "هندسة الأوامر ✍️",
            actionableHint = "بدلاً من قول: اكتب عن الصبر، قل: أعد مخططاً موجزاً للشباب عن الصبر، وافصل بين الآيات والأحاديث الصحيحة، ولا تخترع مراجع."
        ),
        QabasTipItem(
            id = "ai5",
            category = TipCategory.PRODUCTIVITY,
            text = "يمكن توظيف AI في خدمة التعلم: تبسيط المفاهيم، إعداد أسئلة مراجعة، بناء خطة حفظ، والتدرب على لغة جديدة.",
            sourceOrBadge = "التعلم بمساعدة AI 🎓",
            actionableHint = "اطلب منه أن يختبر فهمك سؤالاً بعد سؤال، وأن يشرح مواضع الخطأ، ولا تجعل الإجابات الجاهزة بديلاً عن التعلم والمراجعة."
        ),
        QabasTipItem(
            id = "ai6",
            category = TipCategory.PRODUCTIVITY,
            text = "استفد من الذكاء الاصطناعي في الأعمال النافعة: تفريغ الدروس، الترجمة الأولية، إعداد وصف المحتوى، وتحسين الوصول لذوي الإعاقة.",
            sourceOrBadge = "تقنية في خدمة الخير 🌍",
            actionableHint = "راجع التفريغ والترجمة، خصوصاً أسماء الأعلام والمصطلحات الشرعية والآيات والأحاديث، قبل النشر أو المشاركة."
        ),
        QabasTipItem(
            id = "ai7",
            category = TipCategory.PRODUCTIVITY,
            text = "لا تعتمد على إجابة AI في فتوى أو حكم شرعي؛ فقد يخلط بين الأقوال أو ينسب حديثاً أو قولاً إلى غير قائله.",
            sourceOrBadge = "تحقق قبل النشر 🔎",
            actionableHint = "ارجع إلى القرآن ومصادر السنة المعتمدة وكلام أهل العلم الموثوقين، واسأل عالماً مؤهلاً في النوازل والفتاوى."
        ),
        QabasTipItem(
            id = "ai8",
            category = TipCategory.PRODUCTIVITY,
            text = "النماذج قد تنتج معلومات مختلقة بصياغة واثقة؛ وهذه الظاهرة تُعرف بالهلوسة (Hallucination).",
            sourceOrBadge = "اعرف حدود النموذج ⚠️",
            actionableHint = "تحقق من كل معلومة مؤثرة عبر مصدر مستقل، وافتح الروابط بنفسك، ولا تعتبر الاقتباس أو رقم الحديث صحيحاً لمجرد أن النموذج أورده."
        ),
        QabasTipItem(
            id = "ai9",
            category = TipCategory.PRODUCTIVITY,
            text = "قبل رفع ملف أو صورة أو تسجيل إلى خدمة AI، انتبه إلى الخصوصية وبيانات الأشخاص الموجودة فيه.",
            sourceOrBadge = "خصوصيتك أمانة 🔐",
            actionableHint = "لا ترفع كلمات المرور أو الوثائق الحساسة أو بيانات الآخرين دون إذن، وراجع إعدادات الاحتفاظ بالبيانات واستخدامها لدى الخدمة."
        ),
        QabasTipItem(
            id = "ai10",
            category = TipCategory.PRODUCTIVITY,
            text = "قارن بين أدوات الذكاء الاصطناعي بحسب المهمة، لا بحسب شهرتها؛ فقد يتفوق نموذج في البحث أو البرمجة بينما يتميز آخر بالصوت أو الصور.",
            sourceOrBadge = "اختر الأداة المناسبة 🧭",
            actionableHint = "اختبر الأدوات بعينة واحدة ومعيار واضح للدقة والجودة والخصوصية والتكلفة، وتذكر أن قدراتها وأسعارها تتغير باستمرار."
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
        quranTips: List<QabasTipItem>,
        bukhariHadiths: List<BukhariHadith> = emptyList()
    ): List<QabasTipItem> {
        val hadithTips = bukhariHadiths.map { hadith ->
            QabasTipItem(id = hadith.id, category = TipCategory.SUNNAH, text = hadith.arabicText, sourceOrBadge = hadith.referenceLabel, bukhariHadith = hadith)
        }
        val combinedTips = allTips + quranTips + hadithTips
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
    var currentTipIndex by remember { mutableIntStateOf(0) }
    val quranTips by produceState(initialValue = emptyList<QabasTipItem>(), context) {
        value = withContext(Dispatchers.IO) { QabasQuranData.load(context) }
    }
    var bukhariHadiths by remember { mutableStateOf(emptyList<BukhariHadith>()) }
    LaunchedEffect(context) {
        bukhariHadiths = withContext(Dispatchers.IO) { QabasBukhariRepository.load(context) }
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
    
    val filteredTips = remember(isDeveloper, selectedCategoryFilter, quranTips, bukhariHadiths) {
        QabasTipsRepository.getTipsForUser(isDeveloper, selectedCategoryFilter, quranTips, bukhariHadiths)
    }

    var isAutoRotating by remember { mutableStateOf(true) }
    var selectedTipForDialog by remember { mutableStateOf<QabasTipItem?>(null) }
    var selectedBukhariHadith by remember { mutableStateOf<BukhariHadith?>(null) }

    LaunchedEffect(filteredTips) {
        if (currentTipIndex >= filteredTips.size) {
            currentTipIndex = 0
        }
    }

    val currentTip = filteredTips.getOrNull(currentTipIndex) ?: filteredTips.firstOrNull()
    val rotationDelay = when (currentTip?.category) {
        TipCategory.QURAN, TipCategory.SUNNAH -> 12_000L
        else -> 8_000L
    }

    // مؤقت التدوير التلقائي: النصوص الشرعية 12 ثانية، وبقية النصائح 8 ثوانٍ.
    LaunchedEffect(isAutoRotating, filteredTips.size, selectedCategoryFilter, rotationDelay) {
        if (isAutoRotating && filteredTips.isNotEmpty()) {
            while (true) {
                delay(rotationDelay)
                currentTipIndex = (currentTipIndex + 1) % filteredTips.size
            }
        }
    }

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
                    .getTipsForUser(isDeveloper, selectedCategory, quranTips, bukhariHadiths)
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
                                    QabasTipsRepository.getTipsForUser(isDeveloper, null, quranTips, bukhariHadiths).size.toString(),
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
                        val catTipsCount = QabasTipsRepository.getTipsForUser(isDeveloper, cat, quranTips, bukhariHadiths).size

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
                                if (currentTip.bukhariHadith != null) selectedBukhariHadith = currentTip.bukhariHadith
                                else selectedTipForDialog = currentTip
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

    selectedBukhariHadith?.let { hadith ->
        val screenConfiguration = LocalConfiguration.current
        Dialog(onDismissRequest = { selectedBukhariHadith = null }) {
            GlassmorphicSurface(
                modifier = Modifier
                    .width((screenConfiguration.screenWidthDp * 0.92f).dp)
                    .padding(vertical = 10.dp),
                shape = RoundedCornerShape(24.dp),
                backgroundColor = Color(0xFF071326).copy(alpha = 0.94f),
                borderColor = TipCategory.SUNNAH.accentColor.copy(alpha = 0.6f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = (screenConfiguration.screenHeightDp * 0.85f).dp)
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("صحيح البخاري", color = TipCategory.SUNNAH.accentColor, fontFamily = CairoFont, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { selectedBukhariHadith = null }) { Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondary) }
                    }
                    Text(hadith.arabicText, color = Color.White, fontFamily = AmiriFont, fontSize = 21.sp, lineHeight = 34.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    hadith.book.takeIf { it.isNotBlank() }?.let { Text("الكتاب: $it", color = Color.White, fontFamily = CairoFont) }
                    hadith.chapterNumber.takeIf { it.isNotBlank() }?.let { Text("رقم الباب: $it", color = Color.White, fontFamily = CairoFont) }
                    hadith.chapterTitleArabic.takeIf { it.isNotBlank() }?.let { Text("عنوان الباب: $it", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold) }
                    hadith.englishText.takeIf { it.isNotBlank() }?.let { Text(it, color = Color(0xFFDCE5F1), fontSize = 13.sp, lineHeight = 21.sp) }
                    hadith.grade.takeIf { it.isNotBlank() }?.let { Text("الدرجة: $it", color = GoldPrimary, fontFamily = CairoFont) }
                    hadith.inBookReference.takeIf { it.isNotBlank() }?.let { Text("المرجع داخل الكتاب: $it", color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp) }
                    hadith.reference.takeIf { it.isNotBlank() }?.let { Text("المرجع: $it", color = TextSecondary, fontSize = 11.sp) }
                    OutlinedButton(onClick = {
                        clipboardManager.setText(AnnotatedString(hadith.arabicText))
                        Toast.makeText(context, "تم نسخ نص الحديث", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.fillMaxWidth()) { Text("نسخ نص الحديث", fontFamily = CairoFont) }
                }
            }
        }
    }

    // ── 3. نافذة التفاصيل (Compact, Scrollable Wisdom Dialog) ──
    if (selectedTipForDialog != null) {
        val tip = selectedTipForDialog!!
        val tipColor = tip.category.accentColor
        val isScripture = tip.category == TipCategory.QURAN || tip.category == TipCategory.SUNNAH
        val screenConfiguration = LocalConfiguration.current

        Dialog(onDismissRequest = { selectedTipForDialog = null }) {
            GlassmorphicSurface(
                modifier = Modifier
                    .width((screenConfiguration.screenWidthDp * 0.92f).dp)
                    .padding(vertical = 10.dp)
                    .shadow(24.dp, RoundedCornerShape(24.dp), spotColor = tipColor.copy(alpha = 0.24f)),
                shape = RoundedCornerShape(24.dp),
                backgroundColor = Color(0xFF071326).copy(alpha = 0.94f),
                borderColor = tipColor.copy(alpha = 0.52f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = (screenConfiguration.screenHeightDp * 0.85f).dp)
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
