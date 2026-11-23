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

/**
 * نموذج بيانات التكوين لدعم واستدامة منصة خادم المسلم.
 */
data class SupportConfig(
    val whatsappGroupUrl: String = "https://chat.whatsapp.com/FwpPfcgETYX2qw2XZE9QAF?s=cl&p=a&ilr=4&iam=2",
    val whatsappGroupName: String = "مجتمع خادم المسلم الرسمي",
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
    val waqfTierDesc: String = "دعم استراتيجي مستدام يخلد اسمك في لوحة شرف رعاة منصة خادم المسلم.",
    val shareMessage: String = "✨ أنصحكم بالانضمام لتطبيق ومجتمع «خادم المسلم» الإسلامي الشامل بدون إعلانات:\n📲 https://chat.whatsapp.com/FwpPfcgETYX2qw2XZE9QAF?s=cl&p=a&ilr=4&iam=2",
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
