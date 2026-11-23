package com.qabas.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.qabas.app.ui.components.*
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * نموذج بيانات التكوين لدعم واستدامة منصة خادم المسلم.
 *
 * ق21: حقلا التبرع كانا يحملان قيمتين كاذبتين — آيبان يفشل في فحص mod-97
 * وعنوان USDT نائب (43 حرفاً فيه حروف خارج base58) — فأُفرغا: القيم
 * الحقيقية يزوّدها مالك التطبيق حصراً، ولا تُخترع أبداً. ورابط واتساب
 * الافتراضي من المصدر الموحد QabasWebConfig حتى لا ينجرف مجدداً.
 */
data class SupportConfig(
    val whatsappGroupUrl: String = QabasWebConfig.DEFAULT_COMMUNITY_GROUP_URL,
    val whatsappGroupName: String = "مجتمع خادم المسلم الرسمي",
    val paypalUrl: String = "https://paypal.me/qabas_studio",
    val usdtTronAddress: String = "",
    val bankName: String = "مصرف الراجحي / حساب المبادرات التقنية",
    val bankIban: String = "",
    val hadithQuote: String = "«مَنْ دَلَّ عَلَى خَيْرٍ فَلَهُ مِثْلُ أَجْرِ فَاعِلِهِ» — مساهمتكم تضمن بقاء الخوادم مجانية ونقية لكافة المسلمين.",
    val coffeeTierPrice: String = "$3 / 10 ر.س",
    val coffeeTierDesc: String = "مساهمة رمزية تسعد فريق العمل وتدعم شغف التطوير المستمر.",
    val aiTierPrice: String = "$10 / 35 ر.س",
    val aiTierDesc: String = "تغطية تكاليف نماذج الذكاء الاصطناعي ومعالجة الفيديوهات الدعوية.",
    val serverTierPrice: String = "$25 / 95 ر.س",
    val serverTierDesc: String = "كفالة تشغيل خوادم قواعد البيانات وتخزين الصوتيات للمستخدمين.",
    val waqfTierPrice: String = "$100+ / 375 ر.س",
    val waqfTierDesc: String = "دعم استراتيجي مستدام يخلد اسمك في لوحة شرف رعاة منصة خادم المسلم.",
    val shareMessage: String = "✨ أنصحكم بالانضمام لتطبيق ومجتمع «خادم المسلم» الإسلامي الشامل بدون إعلانات:\n📲 ${QabasWebConfig.DEFAULT_COMMUNITY_GROUP_URL}",
    val lastUpdated: Long = 0L,
    val updatedBy: String = "admin"
)

/**
 * مدير تكوين الدعم (SupportConfigManager) — يقرأ ويحفظ في SharedPreferences
 * محلياً فقط: لا يوجد مصدر سحابي لهذا التكوين، فتعديل الروابط/المحافظ هنا
 * يظهر على هذا الجهاز وحده ولا يصل لباقي المستخدمين. النشر السحابي
 * يحتاج مصدراً سحابياً (جدول app_config مثلاً) أولاً.
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

    @Volatile
    private var cachedConfig: SupportConfig? = null

    /**
     * ق21: حارسان يمنعان عودة قيم تبرع كاذبة — الآيبان المعروض سابقاً
     * فشل في mod-97، وعنوان USDT كان نائباً (43 حرفاً). فحص شكلي فقط:
     * لا يثبت ملكية الحساب ولا وجوده، لكنه يرفض ما يستحيل أن يكون صحيحاً.
     */
    fun isValidIban(iban: String): Boolean {
        val clean = iban.filter { !it.isWhitespace() }.uppercase(Locale.ROOT)
        if (clean.length < 15 || clean.length > 34) return false
        if (!clean.all { it.isLetterOrDigit() }) return false
        if (!clean.substring(0, 2).all { it.isLetter() } || !clean.substring(2, 4).all { it.isDigit() }) return false
        val rearranged = clean.substring(4) + clean.substring(0, 4)
        val digits = StringBuilder()
        for (ch in rearranged) {
            if (ch.isDigit()) digits.append(ch) else digits.append(ch - 'A' + 10)
        }
        var mod = 0
        val s = digits.toString()
        var i = 0
        while (i < s.length) {
            val chunk = s.substring(i, minOf(i + 7, s.length))
            mod = ("$mod$chunk").toLong().rem(97).toInt()
            i += chunk.length
        }
        return mod == 1
    }

    /** عنوان TRON ‏(TRC20): 34 حرفاً من أبجدية base58 يبدأ بـ T. */
    fun isValidTronAddress(address: String): Boolean {
        val clean = address.trim()
        if (clean.length != 34 || !clean.startsWith("T")) return false
        val base58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        return clean.all { it in base58 }
    }

    fun readLocal(context: Context): SupportConfig {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val default = SupportConfig()
        val raw = SupportConfig(
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
        // ق21: تنظيف عند القراءة — القيم الكاذبة قد تكون محفوظة في
        // تفضيلات الأجهزة الحالية أصلاً، فتغيير الافتراضيات وحده لا يقتلها.
        // رابط واتساب يُصحَّح فقط إذا كان نسخة قديمة من المجموعة نفسها
        // (معرّف الدعوة نفسه)؛ رابط مجموعة مختلفة يبقى كما هو.
        val staleWhatsapp = "https://chat.whatsapp.com/FwpPfcgETYX2qw2XZE9QAF?s=cl&p=a&ilr=4&iam=2"
        return raw.copy(
            whatsappGroupUrl = if (raw.whatsappGroupUrl.contains("FwpPfcgETYX2qw2XZE9QAF") && raw.whatsappGroupUrl != QabasWebConfig.DEFAULT_COMMUNITY_GROUP_URL) QabasWebConfig.DEFAULT_COMMUNITY_GROUP_URL else raw.whatsappGroupUrl,
            usdtTronAddress = if (raw.usdtTronAddress.isNotBlank() && !isValidTronAddress(raw.usdtTronAddress)) "" else raw.usdtTronAddress,
            bankIban = if (raw.bankIban.isNotBlank() && !isValidIban(raw.bankIban)) "" else raw.bankIban,
            shareMessage = raw.shareMessage.replace(staleWhatsapp, QabasWebConfig.DEFAULT_COMMUNITY_GROUP_URL)
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

    /** إعادة تحميل التكوين من التخزين المحلي (لا جلب سحابي — لا مصدر سحابي). */
    suspend fun reloadLocal(context: Context): SupportConfig = withContext(Dispatchers.IO) {
        readLocal(context)
    }

    /** حفظ التكوين محلياً على هذا الجهاز فقط (لا نشر سحابي — لا مصدر سحابي). */
    suspend fun saveLocal(context: Context, newConfig: SupportConfig, adminIdentity: String): Boolean = withContext(Dispatchers.IO) {
        val stampedConfig = newConfig.copy(
            lastUpdated = System.currentTimeMillis(),
            updatedBy = adminIdentity
        )
        writeLocal(context, stampedConfig)
        true
    }

}
