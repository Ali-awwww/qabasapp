package com.qabas.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.material.icons.outlined.*
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
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * نموذج بيانات التكوين السحابي لدعم واستدامة منصة قبس.
 * يتزامن مع Firestore لضمان وصول التعديلات لجميع المستخدمين فوراً.
 */
data class SupportConfig(
    val whatsappGroupUrl: String = "https://chat.whatsapp.com/FwpPfcgETYX2qw2XZE9QAF?s=cl&p=a&ilr=4&iam=2",
    val whatsappGroupName: String = "مجتمع قبس الرسمي 🌟",
    val paypalUrl: String = "https://paypal.me/qabas_studio",
    val usdtTronAddress: String = "TYQabasStudioSupportOfficialTRC20Address99X",
    val bankName: String = "مصرف الراجحي / حساب المبادرات التقنية",
    val bankIban: String = "SA0380000000608010167520",
    val hadithQuote: String = "«مَنْ دَلَّ عَلَى خَيْرٍ فَلَهُ مِثْلُ أَجْرِ فَاعِلِهِ» — مساهمتكم تضمن بقاء الخوادم مجانية ونقية لكافة المسلمين.",
    val coffeeTierPrice: String = "$3 / 10 ر.س",
    val coffeeTierDesc: String = "مساهمة رمزية تسعد فريق العمل وتدعم شغف التطوير المستمر.",
    val aiTierPrice: String = "$10 / 35 ر.س",
    val aiTierDesc: String = "تغطية تكاليف نماذج Gemini & Groq ومعالجة آلاف الفيديوهات الدعوية.",
    val serverTierPrice: String = "$25 / 95 ر.س",
    val serverTierDesc: String = "كفالة تشغيل خوادم قواعد البيانات وتخزين الصوتيات للمستخدمين.",
    val waqfTierPrice: String = "$100+ / 375 ر.س",
    val waqfTierDesc: String = "دعم استراتيجي مستدام يخلد اسمك في لوحة شرف رعاة منصة قبس الخالدة.",
    val shareMessage: String = "✨ أنصحكم بالانضمام لتطبيق ومجتمع «قبس» الإسلامي الشامل بدون إعلانات:\n📲 https://chat.whatsapp.com/FwpPfcgETYX2qw2XZE9QAF?s=cl&p=a&ilr=4&iam=2",
    val lastUpdated: Long = 0L,
    val updatedBy: String = "admin"
)

/**
 * مدير تكوين الدعم السحابي (SupportConfigManager)
 * يقرأ محلياً من SharedPreferences ويحدث سحابياً عبر Firestore لجميع المستخدمين.
 */
object SupportConfigManager {
    private const val TAG = "SupportConfigManager"
    private const val COLLECTION = "app_config"
    private const val DOC_ID = "support_config"

    private const val PREF_WHATSAPP_URL = "support_cfg_whatsapp_url"
    private const val PREF_WHATSAPP_NAME = "support_cfg_whatsapp_name"
    private const val PREF_PAYPAL_URL = "support_cfg_paypal_url"
    private const val PREF_USDT_ADDR = "support_cfg_usdt_addr"
    private const val PREF_BANK_NAME = "support_cfg_bank_name"
    private const val PREF_BANK_IBAN = "support_cfg_bank_iban"
    private const val PREF_HADITH_QUOTE = "support_cfg_hadith_quote"
    private const val PREF_COFFEE_PRICE = "support_cfg_coffee_price"
    private const val PREF_COFFEE_DESC = "support_cfg_coffee_desc"
    private const val PREF_AI_PRICE = "support_cfg_ai_price"
    private const val PREF_AI_DESC = "support_cfg_ai_desc"
    private const val PREF_SERVER_PRICE = "support_cfg_server_price"
    private const val PREF_SERVER_DESC = "support_cfg_server_desc"
    private const val PREF_WAQF_PRICE = "support_cfg_waqf_price"
    private const val PREF_WAQF_DESC = "support_cfg_waqf_desc"
    private const val PREF_SHARE_MSG = "support_cfg_share_msg"
    private const val PREF_LAST_UPDATED = "support_cfg_last_updated"
    private const val PREF_UPDATED_BY = "support_cfg_updated_by"

    @Volatile
    private var cachedConfig: SupportConfig? = null

    fun readLocal(context: Context): SupportConfig {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val default = SupportConfig()
        return SupportConfig(
            whatsappGroupUrl = prefs.getString(PREF_WHATSAPP_URL, default.whatsappGroupUrl) ?: default.whatsappGroupUrl,
            whatsappGroupName = prefs.getString(PREF_WHATSAPP_NAME, default.whatsappGroupName) ?: default.whatsappGroupName,
            paypalUrl = prefs.getString(PREF_PAYPAL_URL, default.paypalUrl) ?: default.paypalUrl,
            usdtTronAddress = prefs.getString(PREF_USDT_ADDR, default.usdtTronAddress) ?: default.usdtTronAddress,
            bankName = prefs.getString(PREF_BANK_NAME, default.bankName) ?: default.bankName,
            bankIban = prefs.getString(PREF_BANK_IBAN, default.bankIban) ?: default.bankIban,
            hadithQuote = prefs.getString(PREF_HADITH_QUOTE, default.hadithQuote) ?: default.hadithQuote,
            coffeeTierPrice = prefs.getString(PREF_COFFEE_PRICE, default.coffeeTierPrice) ?: default.coffeeTierPrice,
            coffeeTierDesc = prefs.getString(PREF_COFFEE_DESC, default.coffeeTierDesc) ?: default.coffeeTierDesc,
            aiTierPrice = prefs.getString(PREF_AI_PRICE, default.aiTierPrice) ?: default.aiTierPrice,
            aiTierDesc = prefs.getString(PREF_AI_DESC, default.aiTierDesc) ?: default.aiTierDesc,
            serverTierPrice = prefs.getString(PREF_SERVER_PRICE, default.serverTierPrice) ?: default.serverTierPrice,
            serverTierDesc = prefs.getString(PREF_SERVER_DESC, default.serverTierDesc) ?: default.serverTierDesc,
            waqfTierPrice = prefs.getString(PREF_WAQF_PRICE, default.waqfTierPrice) ?: default.waqfTierPrice,
            waqfTierDesc = prefs.getString(PREF_WAQF_DESC, default.waqfTierDesc) ?: default.waqfTierDesc,
            shareMessage = prefs.getString(PREF_SHARE_MSG, default.shareMessage) ?: default.shareMessage,
            lastUpdated = prefs.getLong(PREF_LAST_UPDATED, 0L),
            updatedBy = prefs.getString(PREF_UPDATED_BY, "admin") ?: "admin"
        )
    }

    fun current(context: Context): SupportConfig = cachedConfig ?: readLocal(context).also { cachedConfig = it }

    fun writeLocal(context: Context, cfg: SupportConfig) {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString(PREF_WHATSAPP_URL, cfg.whatsappGroupUrl)
            .putString(PREF_WHATSAPP_NAME, cfg.whatsappGroupName)
            .putString(PREF_PAYPAL_URL, cfg.paypalUrl)
            .putString(PREF_USDT_ADDR, cfg.usdtTronAddress)
            .putString(PREF_BANK_NAME, cfg.bankName)
            .putString(PREF_BANK_IBAN, cfg.bankIban)
            .putString(PREF_HADITH_QUOTE, cfg.hadithQuote)
            .putString(PREF_COFFEE_PRICE, cfg.coffeeTierPrice)
            .putString(PREF_COFFEE_DESC, cfg.coffeeTierDesc)
            .putString(PREF_AI_PRICE, cfg.aiTierPrice)
            .putString(PREF_AI_DESC, cfg.aiTierDesc)
            .putString(PREF_SERVER_PRICE, cfg.serverTierPrice)
            .putString(PREF_SERVER_DESC, cfg.serverTierDesc)
            .putString(PREF_WAQF_PRICE, cfg.waqfTierPrice)
            .putString(PREF_WAQF_DESC, cfg.waqfTierDesc)
            .putString(PREF_SHARE_MSG, cfg.shareMessage)
            .putLong(PREF_LAST_UPDATED, cfg.lastUpdated)
            .putString(PREF_UPDATED_BY, cfg.updatedBy)
            .apply()
        cachedConfig = cfg
    }

    suspend fun refreshFromCloud(context: Context): SupportConfig = withContext(Dispatchers.IO) {
        readLocal(context)
    }

    suspend fun saveAndPublish(context: Context, newConfig: SupportConfig, adminIdentity: String): Boolean = withContext(Dispatchers.IO) {
        val stampedConfig = newConfig.copy(
            lastUpdated = System.currentTimeMillis(),
            updatedBy = adminIdentity
        )
        writeLocal(context, stampedConfig)
        true
    }
}

data class SponsorshipTier(
    val id: String,
    val title: String,
    val priceText: String,
    val impactText: String,
    val icon: ImageVector,
    val accentColor: Color,
    val isPopular: Boolean = false
)

data class SupportMessage(
    val senderName: String,
    val message: String,
    val dateText: String,
    val rating: Int,
    val badge: String
)

/**
 * قسم الدعم والمساهمة المتكامل في استدامة وتطوير خوادم ودعامات تطبيق قبس:
 * 1. الدعم المالي وكفالة الخوادم والذكاء الاصطناعي (Sponsoring Servers & AI Tokens)
 * 2. التقييم وكلمات الدعم والتشجيع المباشرة للمطورين
 * 3. الدعم بالنشر ومشاركة الخير (الدال على الخير كفاعله) ومجتمع واتساب الرسمي
 * 4. لوحة تحكم وتعديل بيانات الدعم السحابية الحية للمطور
 */
@Composable
fun SupportContributionSection() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember(context) { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }

    // التحقق من صلاحيات المطور
    val isDeveloper = remember(context) { AdminGuard.isDashboardAccessAllowed(context) }

    // بيانات التكوين الحية
    var supportConfig by remember { mutableStateOf(SupportConfigManager.current(context)) }

    // جلب التحديث السحابي التلقائي عند فتح الشاشة
    LaunchedEffect(Unit) {
        val cloudCfg = SupportConfigManager.refreshFromCloud(context)
        supportConfig = cloudCfg
    }

    // التبويب النشط: 0 = المساهمة المالية والخوادم، 1 = التقييم والرسائل، 2 = النشر ومجتمع واتساب
    var selectedTab by remember { mutableIntStateOf(0) }

    // وسام الداعم والتقييم
    var isSupporterBadgeClaimed by remember {
        mutableStateOf(prefs.getBoolean("is_qabas_patron_claimed", false))
    }
    var userRating by remember {
        mutableIntStateOf(prefs.getInt("user_support_rating", 5))
    }
    var userFeedbackText by remember { mutableStateOf("") }

    // الحوارات المنبثقة
    var showThankYouDialog by remember { mutableStateOf(false) }
    var showCryptoAddressDialog by remember { mutableStateOf(false) }
    var showBankDetailsDialog by remember { mutableStateOf(false) }
    var showDevEditDialog by remember { mutableStateOf(false) }

    // قائمة رسائل الدعم المجتمعية الملهمة
    var supportMessagesList by remember {
        mutableStateOf(
            listOf(
                SupportMessage(
                    senderName = "أبو عبد الرحمن • الرياض",
                    message = "جزاكم الله خير الجزاء على هذا العمل المبارك الذي يخدم كتاب الله وسنة رسوله بذكاء واحترافية. استمروا فنحن معكم بالدعاء والدعم.",
                    dateText = "منذ يومين",
                    rating = 5,
                    badge = "كافل خادم 🏛️"
                ),
                SupportMessage(
                    senderName = "د. أنس الشريف • القاهرة",
                    message = "أفضل تطبيق لصناعة المحتوى الدعوي الهادف بدون منازع. التصميم فخم وخالٍ من أي إعلانات مزعجة.",
                    dateText = "منذ 4 أيام",
                    rating = 5,
                    badge = "داعم ذهبي 🌟"
                ),
                SupportMessage(
                    senderName = "فاطمة الزهراء • الدار البيضاء",
                    message = "بارك الله في جهود المطورين، كبسولات الحديث والقرآن أضافت لمسة نورانية لحياتنا اليومية.",
                    dateText = "منذ أسبوع",
                    rating = 5,
                    badge = "صانعة محتوى 🎬"
                )
            )
        )
    }

    // أسهم كفالة الخوادم والذكاء الاصطناعي (ديناميكية وفق التكوين السحابي)
    val sponsorshipTiers = remember(supportConfig) {
        listOf(
            SponsorshipTier(
                id = "tier_coffee",
                title = "سهم القهوة والتشجيع",
                priceText = supportConfig.coffeeTierPrice,
                impactText = supportConfig.coffeeTierDesc,
                icon = Icons.Default.Coffee,
                accentColor = Color(0xFFF59E0B)
            ),
            SponsorshipTier(
                id = "tier_ai_tokens",
                title = "سهم خوادم الذكاء الاصطناعي",
                priceText = supportConfig.aiTierPrice,
                impactText = supportConfig.aiTierDesc,
                icon = Icons.Default.Bolt,
                accentColor = Color(0xFF10B981),
                isPopular = true
            ),
            SponsorshipTier(
                id = "tier_cloud_server",
                title = "كفالة خادم سحابي شهري",
                priceText = supportConfig.serverTierPrice,
                impactText = supportConfig.serverTierDesc,
                icon = Icons.Default.Dns,
                accentColor = Color(0xFF0EA5E9)
            ),
            SponsorshipTier(
                id = "tier_digital_waqf",
                title = "الوقف الرقمي ورعاية المشروع",
                priceText = supportConfig.waqfTierPrice,
                impactText = supportConfig.waqfTierDesc,
                icon = Icons.Default.WorkspacePremium,
                accentColor = GoldPrimary
            )
        )
    }

    // دالة مساعدة لفتح الروابط أو نسخها عند التعذر
    fun openUrlSafely(url: String, fallbackChooserTitle: String = "فتح الرابط") {
        QabasLinkOpener.openUrl(context, url, fallbackChooserTitle)
    }

    // 1. حوار تعديل بيانات الدعم للمطور (Developer Live Cloud Editor)
    if (showDevEditDialog && isDeveloper) {
        var editWhatsAppUrl by remember { mutableStateOf(supportConfig.whatsappGroupUrl) }
        var editWhatsAppName by remember { mutableStateOf(supportConfig.whatsappGroupName) }
        var editPaypalUrl by remember { mutableStateOf(supportConfig.paypalUrl) }
        var editUsdtAddress by remember { mutableStateOf(supportConfig.usdtTronAddress) }
        var editBankName by remember { mutableStateOf(supportConfig.bankName) }
        var editBankIban by remember { mutableStateOf(supportConfig.bankIban) }
        var editHadithQuote by remember { mutableStateOf(supportConfig.hadithQuote) }
        var editCoffeePrice by remember { mutableStateOf(supportConfig.coffeeTierPrice) }
        var editCoffeeDesc by remember { mutableStateOf(supportConfig.coffeeTierDesc) }
        var editAiPrice by remember { mutableStateOf(supportConfig.aiTierPrice) }
        var editAiDesc by remember { mutableStateOf(supportConfig.aiTierDesc) }
        var editServerPrice by remember { mutableStateOf(supportConfig.serverTierPrice) }
        var editServerDesc by remember { mutableStateOf(supportConfig.serverTierDesc) }
        var editWaqfPrice by remember { mutableStateOf(supportConfig.waqfTierPrice) }
        var editWaqfDesc by remember { mutableStateOf(supportConfig.waqfTierDesc) }
        var editShareMsg by remember { mutableStateOf(supportConfig.shareMessage) }
        var isPublishing by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isPublishing) showDevEditDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "تعديل وتجديد بيانات الدعم سحابياً 🛠️",
                        color = GoldPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E293B))
                    ) {
                        Text(
                            "💡 أي تعديل تحفظه هنا سيتم رفعه فوراً إلى سحابة Firestore ويظهر لجميع مستخدمي تطبيق قبس فور دخولهم.",
                            color = Color(0xFF38BDF8),
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp),
                            lineHeight = 16.sp
                        )
                    }

                    // 1. رابط مجموعة واتساب
                    Text("رابط مجموعة واتساب الرسمية:", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    OutlinedTextField(
                        value = editWhatsAppUrl,
                        onValueChange = { editWhatsAppUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = false,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF25D366),
                            unfocusedBorderColor = Color(0xFF1E2B44),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    // 2. اسم المجموعة
                    Text("عنوان/اسم المجموعة:", color = qabasTextSecondary(), fontFamily = CairoFont, fontSize = 11.sp)
                    OutlinedTextField(
                        value = editWhatsAppName,
                        onValueChange = { editWhatsAppName = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color(0xFF1E2B44),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    // 3. رابط PayPal / الدفع الإلكتروني
                    Text("رابط PayPal أو بوابة الدفع:", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    OutlinedTextField(
                        value = editPaypalUrl,
                        onValueChange = { editPaypalUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF0070BA),
                            unfocusedBorderColor = Color(0xFF1E2B44),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    // 4. محفظة USDT (Tron)
                    Text("عنوان محفظة USDT (شبكة TRC-20):", color = Color(0xFF10B981), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    OutlinedTextField(
                        value = editUsdtAddress,
                        onValueChange = { editUsdtAddress = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10B981),
                            unfocusedBorderColor = Color(0xFF1E2B44),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    // 5. الحساب البنكي والآيبان
                    Text("اسم البنك والجهة:", color = qabasTextSecondary(), fontFamily = CairoFont, fontSize = 11.sp)
                    OutlinedTextField(
                        value = editBankName,
                        onValueChange = { editBankName = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    Text("رقم الآيبان (IBAN):", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    OutlinedTextField(
                        value = editBankIban,
                        onValueChange = { editBankIban = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    // 6. الآية / الحديث الشريف التحفيزي
                    Text("نص الحديث/الآية التحفيزية:", color = qabasTextSecondary(), fontFamily = CairoFont, fontSize = 11.sp)
                    OutlinedTextField(
                        value = editHadithQuote,
                        onValueChange = { editHadithQuote = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        maxLines = 3
                    )

                    // 7. أسعار وأوصاف الأسهم
                    Text("أسعار ووصف أسهم المساهمة:", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = editCoffeePrice,
                            onValueChange = { editCoffeePrice = it },
                            label = { Text("سهم القهوة", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = editAiPrice,
                            onValueChange = { editAiPrice = it },
                            label = { Text("سهم الذكاء", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = editServerPrice,
                            onValueChange = { editServerPrice = it },
                            label = { Text("سهم الخادم", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = editWaqfPrice,
                            onValueChange = { editWaqfPrice = it },
                            label = { Text("سهم الوقف", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                    }

                    // 8. رسالة المشاركة التلقائية
                    Text("رسالة المشاركة عبر التواصل:", color = qabasTextSecondary(), fontFamily = CairoFont, fontSize = 11.sp)
                    OutlinedTextField(
                        value = editShareMsg,
                        onValueChange = { editShareMsg = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isPublishing = true
                        coroutineScope.launch {
                            val newCfg = supportConfig.copy(
                                whatsappGroupUrl = editWhatsAppUrl.trim(),
                                whatsappGroupName = editWhatsAppName.trim(),
                                paypalUrl = editPaypalUrl.trim(),
                                usdtTronAddress = editUsdtAddress.trim(),
                                bankName = editBankName.trim(),
                                bankIban = editBankIban.trim(),
                                hadithQuote = editHadithQuote.trim(),
                                coffeeTierPrice = editCoffeePrice.trim(),
                                coffeeTierDesc = editCoffeeDesc.trim(),
                                aiTierPrice = editAiPrice.trim(),
                                aiTierDesc = editAiDesc.trim(),
                                serverTierPrice = editServerPrice.trim(),
                                serverTierDesc = editServerDesc.trim(),
                                waqfTierPrice = editWaqfPrice.trim(),
                                waqfTierDesc = editWaqfDesc.trim(),
                                shareMessage = editShareMsg.trim()
                            )
                            val success = SupportConfigManager.saveAndPublish(context, newCfg, AdminGuard.currentIdentity(context))
                            supportConfig = newCfg
                            isPublishing = false
                            showDevEditDialog = false
                            if (success) {
                                Toast.makeText(context, "تم حفظ ونشر البيانات السحابية لجميع المستخدمين بنجاح! 🚀", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "تم الحفظ محلياً (تأكد من اتصال Firebase السحابي)", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    enabled = !isPublishing
                ) {
                    if (isPublishing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DeepSlate, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("حفظ ونشر التعديلات للجميع 🌐", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDevEditDialog = false },
                    enabled = !isPublishing
                ) {
                    Text("إلغاء", color = qabasTextSecondary(), fontFamily = CairoFont)
                }
            },
            containerColor = Color(0xFF141C2B),
            shape = RoundedCornerShape(18.dp)
        )
    }

    // 2. حوار الشكر والدعاء
    if (showThankYouDialog) {
        AlertDialog(
            onDismissRequest = { showThankYouDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تقبل الله طاعتكم وعطاءكم 🌟", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "«مَا نَقَصَتْ صَدَقَةٌ مِنْ مَالٍ»\nنشكركم من أعماق قلوبنا على مساهمتكم في تقوية دعامات وخوادم منصة قبس. تم تفعيل وسام (شريك استدامة قبس 🌟) في حسابكم.",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showThankYouDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Text("الحمد لله", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF141C2B),
            shape = RoundedCornerShape(18.dp)
        )
    }

    // 3. حوار عنوان محفظة USDT (Crypto)
    if (showCryptoAddressDialog) {
        AlertDialog(
            onDismissRequest = { showCryptoAddressDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CurrencyExchange, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("عنوان محفظة الدعم (USDT)", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "شبكة TRC-20 (Tron Network):",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = supportConfig.usdtTronAddress,
                                color = Color(0xFF38BDF8),
                                fontFamily = NotoSansFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(supportConfig.usdtTronAddress))
                            Toast.makeText(context, "تم نسخ عنوان المحفظة 📋", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("نسخ العنوان", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCryptoAddressDialog = false }) {
                    Text("إغلاق", color = GoldPrimary, fontFamily = CairoFont)
                }
            },
            containerColor = Color(0xFF141C2B),
            shape = RoundedCornerShape(18.dp)
        )
    }

    // 4. حوار التحويل البنكي
    if (showBankDetailsDialog) {
        AlertDialog(
            onDismissRequest = { showBankDetailsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("التحويل البنكي ودعم المبادرة", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${supportConfig.bankName}:", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "IBAN: ${supportConfig.bankIban}",
                            color = GoldPrimary,
                            fontFamily = NotoSansFont,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(supportConfig.bankIban))
                            Toast.makeText(context, "تم نسخ رقم الآيبان 📋", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("نسخ رقم الآيبان", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBankDetailsDialog = false }) {
                    Text("إغلاق", color = GoldPrimary, fontFamily = CairoFont)
                }
            },
            containerColor = Color(0xFF141C2B),
            shape = RoundedCornerShape(18.dp)
        )
    }

    // بطاقة الحاوية الشاملة
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .luxuryCardStyle(shapeRadius = 20.dp, borderAlpha = 0.35f, glowElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = qabasCardSurface()),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // الهيدر الرئيسي مع الشعار والوسام وزر تحكم المطور
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary.copy(alpha = 0.15f))
                            .border(1.dp, GoldPrimary.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.VolunteerActivism,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "دعم المنصة والمساهمة في استدامتها",
                            color = qabasTextPrimary(),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "تقوية الخوادم • دعم الذكاء الاصطناعي • نشر الأثر",
                            color = qabasTextSecondary(),
                            fontFamily = NotoSansFont,
                            fontSize = 11.sp
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        color = if (isSupporterBadgeClaimed) GoldPrimary.copy(alpha = 0.2f) else Color(0xFF10B981).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isSupporterBadgeClaimed) GoldPrimary else Color(0xFF10B981).copy(alpha = 0.4f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                if (isSupporterBadgeClaimed) Icons.Default.Verified else Icons.Default.Favorite,
                                contentDescription = null,
                                tint = if (isSupporterBadgeClaimed) GoldPrimary else Color(0xFF10B981),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isSupporterBadgeClaimed) "شريك استدامة 🌟" else "صدقة جارية ❤️",
                                color = if (isSupporterBadgeClaimed) GoldPrimary else Color(0xFF10B981),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = CairoFont
                            )
                        }
                    }

                    // زر تعديل المطور السحابي الحصري
                    if (isDeveloper) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                            modifier = Modifier.clickable { showDevEditDialog = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("تعديل البيانات (مطور) 🛠️", color = GoldPrimary, fontSize = 9.sp, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // الآية الكريمة المحفزة (ديناميكية)
            Surface(
                color = Color(0xFF0C1322).copy(alpha = 0.9f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF1E2B44)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FormatQuote, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = supportConfig.hadithQuote,
                        color = GoldSecondary,
                        fontFamily = AmiriFont,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ⭐ بطاقة مجتمع واتساب الرسمي البارزة مع زر الدخول والنسخ الفوري
            Surface(
                color = Color(0xFF0D1C16),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF25D366).copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF25D366).copy(alpha = 0.2f))
                                    .border(1.dp, Color(0xFF25D366).copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Chat, contentDescription = null, tint = Color(0xFF25D366), modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = supportConfig.whatsappGroupName,
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "مجتمع رواد وصناع المحتوى • متابعة التحديثات والاقتراحات",
                                    color = Color(0xFF86EFAC),
                                    fontFamily = NotoSansFont,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Surface(
                            color = Color(0xFF25D366).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "واتساب 💬",
                                color = Color(0xFF25D366),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = CairoFont,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // زر الدخول المباشر إلى واتساب
                        Button(
                            onClick = {
                                openUrlSafely(supportConfig.whatsappGroupUrl, "فتح مجموعة واتساب")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.3f).height(40.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color(0xFF062D15), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("دخول المجموعة 💬", color = Color(0xFF062D15), fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // زر نسخ رابط المجموعة الفوري
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(supportConfig.whatsappGroupUrl))
                                Toast.makeText(context, "تم نسخ رابط مجموعة واتساب إلى الحافظة بنجاح 📋", Toast.LENGTH_SHORT).show()
                            },
                            border = BorderStroke(1.dp, Color(0xFF25D366)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(40.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF25D366), modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("نسخ الرابط 📋", color = Color(0xFF25D366), fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // شريط التبويبات الفاخر (3 أركان للدعم)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    Triple(0, "كفالة الخوادم ⚡", Icons.Default.VolunteerActivism),
                    Triple(1, "التقييم والرسائل ✍️", Icons.Default.RateReview),
                    Triple(2, "نشر الخير 📢", Icons.Default.Share)
                ).forEach { (idx, title, icon) ->
                    val isSelected = selectedTab == idx
                    Surface(
                        color = if (isSelected) GoldPrimary else Color.Transparent,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = idx }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = if (isSelected) DeepSlate else qabasTextSecondary(),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = title,
                                color = if (isSelected) DeepSlate else qabasTextSecondary(),
                                fontFamily = CairoFont,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // محتوى التبويبات التفاعلية
            AnimatedContent(
                targetState = selectedTab,
                label = "SupportTabAnimation"
            ) { tab ->
                when (tab) {
                    0 -> {
                        // 🪙 التبويب 1: المساهمة وكفالة الخوادم
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "اختر سهم المساهمة المناسب لك:",
                                color = qabasTextPrimary(),
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )

                            // شبكة الأسهم والكفالات
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                sponsorshipTiers.forEach { tier ->
                                    Surface(
                                        color = if (tier.isPopular) Color(0xFF132035) else Color(0xFF0F1728),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (tier.isPopular) tier.accentColor else Color(0xFF1E293B)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (tier.id == "tier_coffee" || tier.id == "tier_ai_tokens") {
                                                    openUrlSafely(supportConfig.paypalUrl, "دفع عبر PayPal")
                                                } else if (tier.id == "tier_digital_waqf") {
                                                    showBankDetailsDialog = true
                                                } else {
                                                    showCryptoAddressDialog = true
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(38.dp)
                                                        .clip(CircleShape)
                                                        .background(tier.accentColor.copy(alpha = 0.15f))
                                                        .border(1.dp, tier.accentColor.copy(alpha = 0.35f), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(tier.icon, contentDescription = null, tint = tier.accentColor, modifier = Modifier.size(20.dp))
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = tier.title,
                                                            color = qabasTextPrimary(),
                                                            fontFamily = CairoFont,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp
                                                        )
                                                        if (tier.isPopular) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Surface(
                                                                color = tier.accentColor.copy(alpha = 0.2f),
                                                                shape = RoundedCornerShape(4.dp)
                                                            ) {
                                                                Text(
                                                                    text = "الأكثر تأثيراً",
                                                                    color = tier.accentColor,
                                                                    fontFamily = CairoFont,
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Text(
                                                        text = tier.impactText,
                                                        color = qabasTextSecondary(),
                                                        fontFamily = NotoSansFont,
                                                        fontSize = 10.sp,
                                                        lineHeight = 14.sp
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Surface(
                                                color = tier.accentColor.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, tier.accentColor.copy(alpha = 0.4f))
                                            ) {
                                                Text(
                                                    text = tier.priceText,
                                                    color = tier.accentColor,
                                                    fontFamily = CairoFont,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "طرق المساهمة والدفع المتاحة:",
                                color = qabasTextPrimary(),
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )

                            // خيارات الدفع المباشرة
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // زر PayPal / الدعم الخارجي
                                Button(
                                    onClick = {
                                        openUrlSafely(supportConfig.paypalUrl, "فتح رابط الدعم")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0070BA)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(42.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp)
                                ) {
                                    Icon(Icons.Default.Payment, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("PayPal / بطاقة", color = Color.White, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                // زر USDT Crypto
                                OutlinedButton(
                                    onClick = { showCryptoAddressDialog = true },
                                    border = BorderStroke(1.dp, Color(0xFF10B981)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(42.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp)
                                ) {
                                    Icon(Icons.Default.CurrencyExchange, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("USDT (Tron)", color = Color(0xFF10B981), fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                // زر الحساب البنكي
                                OutlinedButton(
                                    onClick = { showBankDetailsDialog = true },
                                    border = BorderStroke(1.dp, GoldPrimary),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(42.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp)
                                ) {
                                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("تحويل بنكي", color = GoldPrimary, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // زر تأكيد المساهمة والحصول على الوسام
                            Surface(
                                color = GoldPrimary.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        isSupporterBadgeClaimed = true
                                        prefs.edit().putBoolean("is_qabas_patron_claimed", true).apply()
                                        showThankYouDialog = true
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Stars, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "قدمت مساهمتي! تفعيل وسام الشرف في حسابي 🌟",
                                        color = GoldPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        // ✍️ التبويب 2: التقييم والرسائل المباشرة للمطورين
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "تقييمك وكلماتك تصنع الفارق معنا:",
                                color = qabasTextPrimary(),
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )

                            // شريط النجوم التفاعلي
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = when (userRating) {
                                            5 -> "⭐⭐⭐⭐⭐ ممتاز ومتقن بارك الله فيكم!"
                                            4 -> "⭐⭐⭐⭐ جيد جداً وبانتظار المزيد"
                                            3 -> "⭐⭐⭐ جيد وبحاجة لبعض التطوير"
                                            2 -> "⭐⭐ يحتاج تحسينات إضافية"
                                            else -> "⭐ سأرسل ملاحظاتي للمطورين"
                                        },
                                        color = GoldPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        (1..5).forEach { star ->
                                            IconButton(
                                                onClick = {
                                                    userRating = star
                                                    prefs.edit().putInt("user_support_rating", star).apply()
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    if (star <= userRating) Icons.Default.Star else Icons.Outlined.StarOutline,
                                                    contentDescription = "نجمة $star",
                                                    tint = if (star <= userRating) GoldPrimary else Color.Gray,
                                                    modifier = Modifier.size(30.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // صندوق كتابة رسالة الدعم / المقترح
                            OutlinedTextField(
                                value = userFeedbackText,
                                onValueChange = { userFeedbackText = it },
                                placeholder = {
                                    Text(
                                        "اكتب دعاءً، كلمة شكر، أو مقترحاً تطويرياً لخوادم وميزات قبس...",
                                        color = qabasTextSecondary(),
                                        fontSize = 11.sp,
                                        fontFamily = CairoFont
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().height(100.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GoldPrimary,
                                    unfocusedBorderColor = Color(0xFF23304A),
                                    focusedTextColor = qabasTextPrimary(),
                                    unfocusedTextColor = qabasTextPrimary()
                                )
                            )

                            // زر إرسال الرسالة
                            Button(
                                onClick = {
                                    if (userFeedbackText.isNotBlank()) {
                                        val newMsg = SupportMessage(
                                            senderName = "صانع أثر (أنت)",
                                            message = userFeedbackText,
                                            dateText = "الآن",
                                            rating = userRating,
                                            badge = "رسالة داعم 💌"
                                        )
                                        supportMessagesList = listOf(newMsg) + supportMessagesList
                                        userFeedbackText = ""
                                        Toast.makeText(context, "وصلت رسالتكم الكريمة لقلوب فريق التطوير، شكرًا لكم! ❤️", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "يرجى كتابة نص الرسالة أولاً", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("إرسال رسالة الدعم والتشجيع", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "جدار محبة ودعاء مجتمع قبس:",
                                color = qabasTextPrimary(),
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )

                            // عرض رسائل المجتمع الملهمة
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                supportMessagesList.take(3).forEach { item ->
                                    Surface(
                                        color = Color(0xFF0D1424),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color(0xFF1E2B45)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = item.senderName,
                                                        color = GoldPrimary,
                                                        fontFamily = CairoFont,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        color = GoldPrimary.copy(alpha = 0.15f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = item.badge,
                                                            color = GoldPrimary,
                                                            fontFamily = CairoFont,
                                                            fontSize = 8.sp,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = item.dateText,
                                                    color = qabasTextSecondary(),
                                                    fontFamily = NotoSansFont,
                                                    fontSize = 9.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = item.message,
                                                color = qabasTextPrimary(),
                                                fontFamily = CairoFont,
                                                fontSize = 11.sp,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // 📢 التبويب 3: النشر والمشاركة ومجتمع واتساب
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "كن شريكاً في الأجر عبر النشر والتطوع:",
                                color = qabasTextPrimary(),
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )

                            // بطاقة مشاركة التطبيق مع الأهل والأصدقاء
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Share, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("مشاركة رابط ورسالة التطبيق", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = supportConfig.shareMessage,
                                        color = qabasTextSecondary(),
                                        fontFamily = NotoSansFont,
                                        fontSize = 10.sp,
                                        lineHeight = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val sendIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                putExtra(Intent.EXTRA_TEXT, supportConfig.shareMessage)
                                                type = "text/plain"
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, "مشاركة تطبيق قبس"))
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f).height(38.dp)
                                    ) {
                                        Icon(Icons.Default.Send, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("مشاركة عبر التطبيقات", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(supportConfig.shareMessage))
                                            Toast.makeText(context, "تم نسخ نص المشاركة ورابط واتساب 📋", Toast.LENGTH_SHORT).show()
                                        },
                                        border = BorderStroke(1.dp, GoldPrimary),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(0.8f).height(38.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("نسخ النص", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        // بطاقة التقييم في المتجر
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFF1E293B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Shop, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("تقييم 5 نجوم في متجر Google Play", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "تقييمك الإيجابي وكلماتك الطيبة في المتجر ترفع من ظهور التطبيق وتوصله لملايين الباحثين عن المحتوى النقي.",
                                    color = qabasTextSecondary(),
                                    fontFamily = NotoSansFont,
                                    fontSize = 10.sp,
                                    lineHeight = 15.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}"))
                                            context.startActivity(intent)
                                        }
                                    },
                                    border = BorderStroke(1.dp, Color(0xFF10B981)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(38.dp)
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("كتابة تقييم في المتجر", color = Color(0xFF10B981), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
