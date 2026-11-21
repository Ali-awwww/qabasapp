package com.qabas.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.*
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * نموذج بيانات التكوين لدعم واستدامة منصة مساعد المسلم.
 */
data class SupportConfig(
    val whatsappGroupUrl: String = "https://chat.whatsapp.com/FwpPfcgETYX2qw2XZE9QAF?s=cl&p=a&ilr=4&iam=2",
    val whatsappGroupName: String = "مجتمع مساعد المسلم الرسمي",
    val paypalUrl: String = "https://paypal.me/qabas_studio",
    val usdtTronAddress: String = "TYQabasStudioSupportOfficialTRC20Address99X",
    val bankName: String = "مصرف الراجحي / حساب المبادرات التقنية",
    val bankIban: String = "SA0380000000608010167520",
    val hadithQuote: String = "«مَنْ دَلَّ عَلَى خَيْرٍ فَلَهُ مِثْلُ أَجْرِ فَاعِلِهِ» — مساهمتكم تضمن بقاء الخوادم مجانية ونقية لكافة المسلمين.",
    val coffeeTierPrice: String = "$3 / 10 ر.س",
    val coffeeTierDesc: String = "مساهمة رمزية تسعد فريق العمل وتدعم شغف التطوير المستمر.",
    val aiTierPrice: String = "$10 / 35 ر.س",
    val aiTierDesc: String = "تغطية تكاليف نماذج الذكاء الاصطناعي ومعالجة الفيديوهات الدعوية.",
    val serverTierPrice: String = "$25 / 95 ر.س",
    val serverTierDesc: String = "كفالة تشغيل خوادم قواعد البيانات وتخزين الصوتيات للمستخدمين.",
    val waqfTierPrice: String = "$100+ / 375 ر.س",
    val waqfTierDesc: String = "دعم استراتيجي مستدام يخلد اسمك في لوحة شرف رعاة منصة مساعد المسلم.",
    val shareMessage: String = "✨ أنصحكم بالانضمام لتطبيق ومجتمع «مساعد المسلم» الإسلامي الشامل بدون إعلانات:\n📲 https://chat.whatsapp.com/FwpPfcgETYX2qw2XZE9QAF?s=cl&p=a&ilr=4&iam=2",
    val lastUpdated: Long = 0L,
    val updatedBy: String = "admin"
)

/**
 * مدير تكوين الدعم (SupportConfigManager) — يقرأ ويحفظ في SharedPreferences محلياً
 */
object SupportConfigManager {
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
    private const val PREF_COMMUNITY_MESSAGES = "support_community_messages_json"

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

    fun loadSavedUserMessages(context: Context): List<SupportMessage> {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString(PREF_COMMUNITY_MESSAGES, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        SupportMessage(
                            senderName = obj.optString("senderName", "صانع أثر"),
                            message = obj.optString("message", ""),
                            dateText = obj.optString("dateText", "محفوظ محلياً"),
                            rating = obj.optInt("rating", 5),
                            badge = obj.optString("badge", "رسالة داعم")
                        )
                    )
                }
            }.filter { it.message.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    fun saveUserMessage(context: Context, msg: SupportMessage) {
        val existing = loadSavedUserMessages(context)
        val updated = (listOf(msg) + existing).take(20)
        val arr = JSONArray()
        updated.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("senderName", item.senderName)
                    put("message", item.message)
                    put("dateText", item.dateText)
                    put("rating", item.rating)
                    put("badge", item.badge)
                }
            )
        }
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_COMMUNITY_MESSAGES, arr.toString())
            .apply()
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

private val DEFAULT_COMMUNITY_MESSAGES = listOf(
    SupportMessage(
        senderName = "أبو عبد الرحمن • الرياض",
        message = "جزاكم الله خير الجزاء على هذا العمل المبارك الذي يخدم كتاب الله وسنة رسوله بذكاء واحترافية. استمروا فنحن معكم بالدعاء والدعم.",
        dateText = "منذ يومين",
        rating = 5,
        badge = "كافل خادم"
    ),
    SupportMessage(
        senderName = "د. أنس الشريف • القاهرة",
        message = "أفضل تطبيق لصناعة المحتوى الدعوي الهادف بدون منازع. التصميم فخم وخالٍ من أي إعلانات مزعجة.",
        dateText = "منذ 4 أيام",
        rating = 5,
        badge = "داعم ذهبي"
    ),
    SupportMessage(
        senderName = "فاطمة الزهراء • الدار البيضاء",
        message = "بارك الله في جهود المطورين، كبسولات الحديث والقرآن أضافت لمسة نورانية لحياتنا اليومية.",
        dateText = "منذ أسبوع",
        rating = 5,
        badge = "صانعة محتوى"
    )
)

/**
 * قسم الدعم والمساهمة في استدامة وتطوير منصة مساعد المسلم — بتصميم Apple HIG للوضعين الفاتح والداكن
 */
@Composable
fun SupportContributionSection() {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember(context) { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }

    val isDeveloper = remember(context) { AdminGuard.isDashboardAccessAllowed(context) }

    var supportConfig by remember { mutableStateOf(SupportConfigManager.current(context)) }
    LaunchedEffect(Unit) {
        supportConfig = SupportConfigManager.refreshFromCloud(context)
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var isSupporterBadgeClaimed by remember {
        mutableStateOf(prefs.getBoolean("is_qabas_patron_claimed", false))
    }
    var userRating by remember {
        mutableIntStateOf(prefs.getInt("user_support_rating", 5))
    }
    var userFeedbackText by remember { mutableStateOf("") }
    var isSendingFeedback by remember { mutableStateOf(false) }

    var showThankYouDialog by remember { mutableStateOf(false) }
    var showCryptoAddressDialog by remember { mutableStateOf(false) }
    var showBankDetailsDialog by remember { mutableStateOf(false) }
    var showDevEditDialog by remember { mutableStateOf(false) }

    var supportMessagesList by remember {
        mutableStateOf(
            SupportConfigManager.loadSavedUserMessages(context) + DEFAULT_COMMUNITY_MESSAGES
        )
    }

    val goldAccent = GoldPrimary
    val greenAccent = theme.success
    val blueAccent = theme.primary

    val sponsorshipTiers = remember(supportConfig, goldAccent, greenAccent, blueAccent) {
        listOf(
            SponsorshipTier(
                id = "tier_coffee",
                title = "سهم القهوة والتشجيع",
                priceText = supportConfig.coffeeTierPrice,
                impactText = supportConfig.coffeeTierDesc,
                icon = Icons.Default.Coffee,
                accentColor = GoldSecondary
            ),
            SponsorshipTier(
                id = "tier_ai_tokens",
                title = "سهم خوادم الذكاء الاصطناعي",
                priceText = supportConfig.aiTierPrice,
                impactText = supportConfig.aiTierDesc,
                icon = Icons.Default.Bolt,
                accentColor = greenAccent,
                isPopular = true
            ),
            SponsorshipTier(
                id = "tier_cloud_server",
                title = "كفالة خادم سحابي شهري",
                priceText = supportConfig.serverTierPrice,
                impactText = supportConfig.serverTierDesc,
                icon = Icons.Default.Dns,
                accentColor = blueAccent
            ),
            SponsorshipTier(
                id = "tier_digital_waqf",
                title = "الوقف الرقمي ورعاية المشروع",
                priceText = supportConfig.waqfTierPrice,
                impactText = supportConfig.waqfTierDesc,
                icon = Icons.Default.WorkspacePremium,
                accentColor = goldAccent
            )
        )
    }

    fun openUrlSafely(url: String, fallbackChooserTitle: String = "فتح الرابط") {
        QabasLinkOpener.openUrl(context, url, fallbackChooserTitle)
    }

    if (showDevEditDialog && isDeveloper) {
        var editWhatsAppUrl by remember { mutableStateOf(supportConfig.whatsappGroupUrl) }
        var editWhatsAppName by remember { mutableStateOf(supportConfig.whatsappGroupName) }
        var editPaypalUrl by remember { mutableStateOf(supportConfig.paypalUrl) }
        var editUsdtAddress by remember { mutableStateOf(supportConfig.usdtTronAddress) }
        var editBankName by remember { mutableStateOf(supportConfig.bankName) }
        var editBankIban by remember { mutableStateOf(supportConfig.bankIban) }
        var editHadithQuote by remember { mutableStateOf(supportConfig.hadithQuote) }
        var editCoffeePrice by remember { mutableStateOf(supportConfig.coffeeTierPrice) }
        var editAiPrice by remember { mutableStateOf(supportConfig.aiTierPrice) }
        var editServerPrice by remember { mutableStateOf(supportConfig.serverTierPrice) }
        var editWaqfPrice by remember { mutableStateOf(supportConfig.waqfTierPrice) }
        var editShareMsg by remember { mutableStateOf(supportConfig.shareMessage) }
        var isPublishing by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isPublishing) showDevEditDialog = false },
            containerColor = theme.cardSurface,
            shape = RoundedCornerShape(18.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(theme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "تعديل بيانات الدعم والمساهمة",
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = theme.secondarySurface,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            "تُحفظ التعديلات محلياً وتُطبّق فوراً على شاشة الدعم على هذا الجهاز.",
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp),
                            lineHeight = 17.sp
                        )
                    }

                    Text("رابط مجموعة واتساب الرسمية:", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    OutlinedTextField(
                        value = editWhatsAppUrl,
                        onValueChange = { editWhatsAppUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        maxLines = 3
                    )

                    Text("اسم المجموعة:", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    OutlinedTextField(
                        value = editWhatsAppName,
                        onValueChange = { editWhatsAppName = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Text("رابط PayPal أو بوابة الدعم:", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    OutlinedTextField(
                        value = editPaypalUrl,
                        onValueChange = { editPaypalUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Text("عنوان محفظة USDT (TRC-20):", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    OutlinedTextField(
                        value = editUsdtAddress,
                        onValueChange = { editUsdtAddress = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Text("اسم البنك:", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    OutlinedTextField(
                        value = editBankName,
                        onValueChange = { editBankName = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Text("رقم الآيبان (IBAN):", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    OutlinedTextField(
                        value = editBankIban,
                        onValueChange = { editBankIban = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Text("النص التحفيزي:", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    OutlinedTextField(
                        value = editHadithQuote,
                        onValueChange = { editHadithQuote = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        maxLines = 3
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editCoffeePrice,
                            onValueChange = { editCoffeePrice = it },
                            label = { Text("سهم القهوة", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = editAiPrice,
                            onValueChange = { editAiPrice = it },
                            label = { Text("سهم الذكاء", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editServerPrice,
                            onValueChange = { editServerPrice = it },
                            label = { Text("سهم الخادم", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = editWaqfPrice,
                            onValueChange = { editWaqfPrice = it },
                            label = { Text("سهم الوقف", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }

                    Text("رسالة المشاركة:", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    OutlinedTextField(
                        value = editShareMsg,
                        onValueChange = { editShareMsg = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
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
                                aiTierPrice = editAiPrice.trim(),
                                serverTierPrice = editServerPrice.trim(),
                                waqfTierPrice = editWaqfPrice.trim(),
                                shareMessage = editShareMsg.trim()
                            )
                            SupportConfigManager.saveAndPublish(context, newCfg, AdminGuard.currentIdentity(context))
                            supportConfig = newCfg
                            isPublishing = false
                            showDevEditDialog = false
                            Toast.makeText(context, "تم حفظ بيانات الدعم وتطبيقها بنجاح", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isPublishing,
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("حفظ التعديلات", fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDevEditDialog = false }, enabled = !isPublishing) {
                    Text("إلغاء", color = theme.textSecondary, fontFamily = CairoFont)
                }
            }
        )
    }

    if (showThankYouDialog) {
        AlertDialog(
            onDismissRequest = { showThankYouDialog = false },
            containerColor = theme.cardSurface,
            shape = RoundedCornerShape(18.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = goldAccent, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تقبل الله عطاءكم", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    "«مَا نَقَصَتْ صَدَقَةٌ مِنْ مَالٍ»\nنشكركم على مساهمتكم في استدامة خوادم منصة مساعد المسلم. تم تفعيل وسام (شريك استدامة مساعد المسلم) في حسابكم.",
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showThankYouDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("الحمد لله", fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showCryptoAddressDialog) {
        AlertDialog(
            onDismissRequest = { showCryptoAddressDialog = false },
            containerColor = theme.cardSurface,
            shape = RoundedCornerShape(18.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CurrencyExchange, contentDescription = null, tint = greenAccent, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("محفظة الدعم (USDT)", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "شبكة TRC-20 (Tron Network):",
                        color = theme.textSecondary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Surface(
                        color = theme.secondarySurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = supportConfig.usdtTronAddress,
                            color = theme.primary,
                            fontFamily = RobotoMonoFont,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    ApplePrimaryButton(
                        text = "نسخ عنوان المحفظة",
                        icon = Icons.Default.ContentCopy,
                        onClick = {
                            clipboardManager.setText(AnnotatedString(supportConfig.usdtTronAddress))
                            Toast.makeText(context, "تم نسخ عنوان المحفظة", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showCryptoAddressDialog = false }) {
                    Text("إغلاق", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    if (showBankDetailsDialog) {
        AlertDialog(
            onDismissRequest = { showBankDetailsDialog = false },
            containerColor = theme.cardSurface,
            shape = RoundedCornerShape(18.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = theme.primary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("التحويل البنكي", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(supportConfig.bankName, color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Surface(
                        color = theme.secondarySurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "IBAN: ${supportConfig.bankIban}",
                            color = theme.primary,
                            fontFamily = RobotoMonoFont,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    ApplePrimaryButton(
                        text = "نسخ رقم الآيبان",
                        icon = Icons.Default.ContentCopy,
                        onClick = {
                            clipboardManager.setText(AnnotatedString(supportConfig.bankIban))
                            Toast.makeText(context, "تم نسخ رقم الآيبان", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showBankDetailsDialog = false }) {
                    Text("إغلاق", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    AppleCard(cornerRadius = 18.dp) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(goldAccent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.VolunteerActivism,
                            contentDescription = null,
                            tint = goldAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "دعم واستدامة منصة مساعد المسلم",
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "الخوادم • الذكاء الاصطناعي • نشر الأثر",
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 12.sp
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val badgeColor = if (isSupporterBadgeClaimed) goldAccent else greenAccent
                    Surface(
                        color = badgeColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                if (isSupporterBadgeClaimed) Icons.Default.Verified else Icons.Default.Favorite,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isSupporterBadgeClaimed) "شريك استدامة" else "مساهمة خيرية",
                                color = badgeColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = CairoFont
                            )
                        }
                    }

                    if (isDeveloper) {
                        Surface(
                            color = theme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(7.dp),
                            modifier = Modifier.clickable { showDevEditDialog = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "تعديل البيانات", tint = theme.primary, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تعديل (مطور)", color = theme.primary, fontSize = 10.sp, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Quote Card
            Surface(
                color = theme.secondarySurface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FormatQuote, contentDescription = null, tint = goldAccent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = supportConfig.hadithQuote,
                        color = theme.textPrimary,
                        fontFamily = AmiriFont,
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    )
                }
            }

            // Official WhatsApp Community Card
            val whatsappGreen = Color(0xFF25D366)
            Surface(
                color = whatsappGreen.copy(alpha = if (theme.isDark) 0.12f else 0.08f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(0.5.dp, whatsappGreen.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(whatsappGreen.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null, tint = whatsappGreen, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = supportConfig.whatsappGroupName,
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "مجتمع رواد وصناع المحتوى • متابعة التحديثات",
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { openUrlSafely(supportConfig.whatsappGroupUrl, "فتح مجموعة واتساب") },
                            colors = ButtonDefaults.buttonColors(containerColor = whatsappGreen, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.3f).heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("دخول المجموعة", fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        AppleSecondaryButton(
                            text = "نسخ الرابط",
                            icon = Icons.Default.ContentCopy,
                            tint = whatsappGreen,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                clipboardManager.setText(AnnotatedString(supportConfig.whatsappGroupUrl))
                                Toast.makeText(context, "تم نسخ رابط مجموعة واتساب", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // Segmented Tabs
            AppleSegmentedControl(
                items = listOf(
                    0 to "كفالة الخوادم",
                    1 to "التقييم والرسائل",
                    2 to "نشر الخير"
                ),
                selectedItem = selectedTab,
                onSelect = { selectedTab = it }
            )

            AnimatedContent(
                targetState = selectedTab,
                label = "SupportTabAnimation"
            ) { tab ->
                when (tab) {
                    0 -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "اختر سهم المساهمة المناسب:",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )

                            sponsorshipTiers.forEach { tier ->
                                Surface(
                                    color = if (tier.isPopular) tier.accentColor.copy(alpha = 0.08f) else theme.secondarySurface,
                                    shape = RoundedCornerShape(14.dp),
                                    border = if (tier.isPopular) BorderStroke(1.dp, tier.accentColor.copy(alpha = 0.45f)) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            when (tier.id) {
                                                "tier_coffee", "tier_ai_tokens" -> openUrlSafely(supportConfig.paypalUrl, "دفع عبر PayPal")
                                                "tier_digital_waqf" -> showBankDetailsDialog = true
                                                else -> showCryptoAddressDialog = true
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(tier.accentColor.copy(alpha = 0.16f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(tier.icon, contentDescription = null, tint = tier.accentColor, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = tier.title,
                                                    color = theme.textPrimary,
                                                    fontFamily = CairoFont,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.5.sp
                                                )
                                                if (tier.isPopular) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        color = tier.accentColor.copy(alpha = 0.18f),
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "الأكثر تأثيراً",
                                                            color = tier.accentColor,
                                                            fontFamily = CairoFont,
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = tier.impactText,
                                                color = theme.textSecondary,
                                                fontFamily = NotoSansFont,
                                                fontSize = 11.5.sp,
                                                lineHeight = 16.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            color = tier.accentColor.copy(alpha = 0.14f),
                                            shape = RoundedCornerShape(8.dp)
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

                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "طرق المساهمة المتاحة:",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AppleSecondaryButton(
                                    text = "PayPal",
                                    icon = Icons.Default.Payment,
                                    tint = theme.primary,
                                    modifier = Modifier.weight(1f),
                                    onClick = { openUrlSafely(supportConfig.paypalUrl, "فتح رابط الدعم") }
                                )
                                AppleSecondaryButton(
                                    text = "USDT",
                                    icon = Icons.Default.CurrencyExchange,
                                    tint = greenAccent,
                                    modifier = Modifier.weight(1f),
                                    onClick = { showCryptoAddressDialog = true }
                                )
                                AppleSecondaryButton(
                                    text = "تحويل بنكي",
                                    icon = Icons.Default.AccountBalance,
                                    tint = goldAccent,
                                    modifier = Modifier.weight(1f),
                                    onClick = { showBankDetailsDialog = true }
                                )
                            }

                            AppleSecondaryButton(
                                text = if (isSupporterBadgeClaimed) "وسام شريك الاستدامة مفعّل" else "قدمت مساهمتي — تفعيل وسام الشرف",
                                icon = Icons.Default.Stars,
                                tint = goldAccent,
                                onClick = {
                                    isSupporterBadgeClaimed = true
                                    prefs.edit().putBoolean("is_qabas_patron_claimed", true).apply()
                                    showThankYouDialog = true
                                }
                            )
                        }
                    }

                    1 -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "تقييمك وكلماتك تصنع الفارق:",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )

                            Surface(
                                color = theme.secondarySurface,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = when (userRating) {
                                            5 -> "ممتاز ومتقن بارك الله فيكم"
                                            4 -> "جيد جداً وبانتظار المزيد"
                                            3 -> "جيد وبحاجة لبعض التطوير"
                                            2 -> "يحتاج تحسينات إضافية"
                                            else -> "سأرسل ملاحظاتي للمطورين"
                                        },
                                        color = goldAccent,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        (1..5).forEach { star ->
                                            IconButton(
                                                onClick = {
                                                    userRating = star
                                                    prefs.edit().putInt("user_support_rating", star).apply()
                                                },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    if (star <= userRating) Icons.Default.Star else Icons.Outlined.StarOutline,
                                                    contentDescription = "تقييم $star نجوم",
                                                    tint = if (star <= userRating) goldAccent else theme.textSecondary,
                                                    modifier = Modifier.size(30.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = userFeedbackText,
                                onValueChange = { userFeedbackText = it },
                                placeholder = {
                                    Text(
                                        "اكتب كلمة شكر، دعاءً، أو مقترحاً تطويرياً لمنصة مساعد المسلم...",
                                        color = theme.textSecondary,
                                        fontSize = 12.sp,
                                        fontFamily = NotoSansFont
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                                shape = RoundedCornerShape(12.dp)
                            )

                            ApplePrimaryButton(
                                text = "إرسال رسالة الدعم والتشجيع",
                                icon = Icons.Default.Send,
                                isLoading = isSendingFeedback,
                                onClick = {
                                    val trimmed = userFeedbackText.trim()
                                    if (trimmed.isBlank()) {
                                        Toast.makeText(context, "يرجى كتابة نص الرسالة أولاً", Toast.LENGTH_SHORT).show()
                                        return@ApplePrimaryButton
                                    }
                                    isSendingFeedback = true
                                    val newMsg = SupportMessage(
                                        senderName = "صانع أثر (أنت)",
                                        message = trimmed,
                                        dateText = "الآن",
                                        rating = userRating,
                                        badge = "رسالة داعم"
                                    )
                                    SupportConfigManager.saveUserMessage(context, newMsg)
                                    supportMessagesList = SupportConfigManager.loadSavedUserMessages(context) + DEFAULT_COMMUNITY_MESSAGES
                                    userFeedbackText = ""

                                    coroutineScope.launch {
                                        val sentToCloud = if (ServiceAvailabilityRegistry.isSupabaseReady()) {
                                            withContext(Dispatchers.IO) {
                                                SupabaseServices.Feedback.submitFeedback(
                                                    type = "support_message_rating_$userRating",
                                                    message = trimmed,
                                                    userName = prefs.getString("user_display_name", "مستخدم مساعد المسلم") ?: "مستخدم مساعد المسلم"
                                                ).isSuccess
                                            }
                                        } else false
                                        isSendingFeedback = false
                                        val toastText = if (sentToCloud) {
                                            "تم حفظ رسالتك وإرسالها لفريق مساعد المسلم بنجاح"
                                        } else {
                                            "تم حفظ رسالتك الكريمة محلياً بنجاح"
                                        }
                                        Toast.makeText(context, toastText, Toast.LENGTH_LONG).show()
                                    }
                                }
                            )

                            Text(
                                text = "كلمات مجتمع مساعد المسلم:",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                supportMessagesList.take(4).forEach { item ->
                                    Surface(
                                        color = theme.secondarySurface,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = item.senderName,
                                                        color = theme.primary,
                                                        fontFamily = CairoFont,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        color = theme.primary.copy(alpha = 0.12f),
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = item.badge,
                                                            color = theme.primary,
                                                            fontFamily = CairoFont,
                                                            fontSize = 9.5.sp,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = item.dateText,
                                                    color = theme.textSecondary,
                                                    fontFamily = NotoSansFont,
                                                    fontSize = 10.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = item.message,
                                                color = theme.textPrimary,
                                                fontFamily = NotoSansFont,
                                                fontSize = 12.5.sp,
                                                lineHeight = 18.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "كن شريكاً في الأجر عبر النشر:",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )

                            Surface(
                                color = theme.secondarySurface,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Share, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("مشاركة رابط ورسالة التطبيق", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                    }
                                    Text(
                                        text = supportConfig.shareMessage,
                                        color = theme.textSecondary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ApplePrimaryButton(
                                            text = "مشاركة",
                                            icon = Icons.Default.Send,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                val sendIntent = Intent().apply {
                                                    action = Intent.ACTION_SEND
                                                    putExtra(Intent.EXTRA_TEXT, supportConfig.shareMessage)
                                                    type = "text/plain"
                                                }
                                                context.startActivity(Intent.createChooser(sendIntent, "مشاركة تطبيق مساعد المسلم"))
                                            }
                                        )
                                        AppleSecondaryButton(
                                            text = "نسخ النص",
                                            icon = Icons.Default.ContentCopy,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(supportConfig.shareMessage))
                                                Toast.makeText(context, "تم نسخ نص المشاركة", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }

                            Surface(
                                color = theme.secondarySurface,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Shop, contentDescription = null, tint = greenAccent, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("تقييم التطبيق في المتجر", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                    }
                                    Text(
                                        "تقييمك الإيجابي وكلماتك الطيبة تساعد في وصول التطبيق للمزيد من الباحثين عن المحتوى الهادف.",
                                        color = theme.textSecondary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp
                                    )
                                    AppleSecondaryButton(
                                        text = "كتابة تقييم في Google Play",
                                        icon = Icons.Default.Star,
                                        tint = greenAccent,
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}"))
                                                context.startActivity(intent)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
