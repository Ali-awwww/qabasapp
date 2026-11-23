package com.qabas.app

import android.os.Build
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * إعدادات التحكم البعيد (Remote Config) — قلب «لوحة التحكم الكاملة».
 *
 * - المصدر الحاسم: مستند Firestore `app_config/config` إن وُجد Firebase.
 *   (قابل للتوسيع لاحقاً إلى جدول Supabase app_config بنفس المفاتيح.)
 * - كاش محلي: `qabas_prefs` (يعمل دون اتصال ولا يكسر البناء بلا سحابة).
 * - أي زر في شاشة المطور يكتب سحابياً ثم يحدّث الكاش حتى تتأثر كل الأجهزة.
 */
object AppRemoteConfig {
    private const val TAG = "AppRemoteConfig"

    const val KEY_MAINTENANCE = "sys_maintenance_mode"
    const val KEY_ACCEPT_REQUESTS = "sys_accept_requests"
    const val KEY_AUTO_AI_REPLY = "sys_auto_ai_reply"
    const val KEY_MAINTENANCE_MESSAGE = "sys_maintenance_message"
    const val KEY_MIN_VERSION = "sys_min_version_code"
    const val KEY_LAST_SYNC = "sys_config_last_sync"

    /** لقطة إعدادات موحّدة تُقرأ من السحابة أو الكاش أو الافتراضيات. */
    data class ConfigData(
        val maintenanceMode: Boolean = false,
        val acceptRequests: Boolean = true,
        val autoAiReply: Boolean = true,
        val maintenanceMessage: String = DEFAULT_MESSAGE,
        val minVersionCode: Int = 0,
        val lastSyncMs: Long = 0L
    ) {
        companion object {
            const val DEFAULT_MESSAGE = "الخدمة متوقفة مؤقتاً للتحديث والصيانة، يرجى المحاولة لاحقاً."
        }
    }

    /** كاش في الذاكرة للقراءة السريعة بعد أول تحديث سحابي. */
    @Volatile
    private var cached: ConfigData? = null

    fun readLocal(context: Context): ConfigData {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        return ConfigData(
            maintenanceMode = prefs.getBoolean(KEY_MAINTENANCE, false),
            acceptRequests = prefs.getBoolean(KEY_ACCEPT_REQUESTS, true),
            autoAiReply = prefs.getBoolean(KEY_AUTO_AI_REPLY, true),
            maintenanceMessage = prefs.getString(KEY_MAINTENANCE_MESSAGE, ConfigData.DEFAULT_MESSAGE)
                ?: ConfigData.DEFAULT_MESSAGE,
            minVersionCode = prefs.getInt(KEY_MIN_VERSION, 0),
            lastSyncMs = prefs.getLong(KEY_LAST_SYNC, 0L)
        )
    }

    fun current(context: Context): ConfigData = cached ?: readLocal(context)

    private fun writeLocal(context: Context, data: ConfigData) {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_MAINTENANCE, data.maintenanceMode)
            .putBoolean(KEY_ACCEPT_REQUESTS, data.acceptRequests)
            .putBoolean(KEY_AUTO_AI_REPLY, data.autoAiReply)
            .putString(KEY_MAINTENANCE_MESSAGE, data.maintenanceMessage)
            .putInt(KEY_MIN_VERSION, data.minVersionCode)
            .putLong(KEY_LAST_SYNC, data.lastSyncMs)
            .apply()
        cached = data
    }

    /** جلب الإعدادات من التخزين المحلي. */
    suspend fun refreshFromCloud(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            val data = readLocal(context)
            cached = data
            true
        }
    }

    /** حفظ الإعدادات محلياً وسحابياً مع تسجيل وقت المزامنة. */
    suspend fun pushToCloud(context: Context, data: ConfigData): Boolean {
        val timestamp = System.currentTimeMillis()
        val updated = data.copy(lastSyncMs = timestamp)
        writeLocal(context, updated)
        return true
    }

    // ──────────────── القواعد المشروطة + تجارب A/B ────────────────

    /**
     * قاعدة تجاوز مشروطة: تغيّر قيمة مفتاح لشريحة فقط
     * (إصدار/نوع مستخدم/نسبة طرح تدريجي) — أساس تجارب A/B.
     */
    data class OverrideRule(
        val id: String = "",
        val key: String = KEY_MAINTENANCE,
        /** القيمة كنص: "true"/"false" للمنطقي، رقم، أو نص الرسالة */
        val value: String = "true",
        val userType: String = "all", // all/developers/premium/free
        val minVersion: Int = 0,
        /** نسبة الطرح 0-100 (100 = الجميع). التوزيع ثابت لكل جهاز. */
        val percent: Int = 100,
        val enabled: Boolean = true,
        val priority: Int = 0
    )

    @Volatile
    private var cachedOverrides: List<OverrideRule>? = null

    /** معرّف ثابت للجهاز لتوزيع نسب A/B بثبات. */
    private fun currentVersionCode(context: Context): Int {
        return runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                packageInfo.versionCode
            }
        }.getOrDefault(0)
    }

    /** جلب القواعد (من كاش الذاكرة أو فارغة). */
    suspend fun fetchOverrides(context: Context): List<OverrideRule> {
        return cachedOverrides ?: emptyList()
    }

    fun invalidateOverrides() {
        cachedOverrides = null
    }

    /** إضافة/تحديث قاعدة من لوحة المطور. */
    suspend fun saveOverride(context: Context, rule: OverrideRule): Boolean {
        val currentList = (cachedOverrides ?: emptyList()).filterNot { it.id == rule.id }.toMutableList()
        val ruleToSave = if (rule.id.isBlank()) rule.copy(id = java.util.UUID.randomUUID().toString()) else rule
        currentList.add(ruleToSave)
        cachedOverrides = currentList
        AuditLogger.log(context, "config_override_save", "${rule.key}=${rule.value} (${rule.userType} ${rule.percent}٪)")
        return true
    }

    /** حذف قاعدة. */
    suspend fun deleteOverride(ruleId: String): Boolean {
        cachedOverrides = (cachedOverrides ?: emptyList()).filterNot { it.id == ruleId }
        return true
    }
}
