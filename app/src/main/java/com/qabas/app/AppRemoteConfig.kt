package com.qabas.app

import android.content.Context
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * إعدادات التحكم البعيد (Remote Config) — قلب «لوحة التحكم الكاملة».
 *
 * حقيقة التنفيذ الحالية (موثقة هنا لأن الأسماء القديمة كانت تكذب):
 * التخزين محلي فقط في `qabas_prefs` — لا يوجد مستند Firestore
 * `app_config/config` ولا جدول Supabase app_config، فلا مزامنة سحابية
 * إطلاقاً: زر المطور يحفظ على هذا الجهاز وحده، ولا تتأثر الأجهزة
 * الأخرى. المزامنة السحابية تحتاج جدول app_config أولاً.
 * - كاش محلي: `qabas_prefs` (يعمل دون اتصال).
 * - القواعد المشروطة (تجارب A/B) محفوظة محلياً كـ JSON وتُقيَّم في
 *   current() عند كل قراءة (الأعلى أولوية أولاً).
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

    /** اللقطة الفعالة: القيم العامة بعد تطبيق القواعد المشروطة المطابقة. */
    fun current(context: Context): ConfigData = cached ?: resolve(context)

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
        // لا يُخزَّن الخام في الكاش: القراءة التالية تعيد الحساب مع القواعد.
        cached = null
    }

    /** إعادة تحميل الإعدادات من التخزين المحلي (لا جلب سحابي — لا مصدر سحابي). */
    suspend fun reloadLocal(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            cached = null
            resolve(context)
            true
        }
    }

    /** حفظ الإعدادات محلياً على هذا الجهاز فقط (لا مزامنة سحابية — لا مصدر سحابي). */
    suspend fun saveLocal(context: Context, data: ConfigData): Boolean {
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

    private const val KEY_OVERRIDES_JSON = "sys_config_overrides_json"

    /** جلب القواعد المحفوظة محلياً (من كاش الذاكرة أو من التخزين المحلي). */
    suspend fun fetchOverrides(context: Context): List<OverrideRule> {
        cachedOverrides?.let { return it }
        val loaded = loadOverridesFromPrefs(context)
        cachedOverrides = loaded
        return loaded
    }

    fun invalidateOverrides() {
        cachedOverrides = null
        cached = null
    }

    private fun loadOverridesFromPrefs(context: Context): List<OverrideRule> {
        val raw = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .getString(KEY_OVERRIDES_JSON, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                OverrideRule(
                    id = o.optString("id", ""),
                    key = o.optString("key", KEY_MAINTENANCE),
                    value = o.optString("value", "true"),
                    userType = o.optString("userType", "all"),
                    minVersion = o.optInt("minVersion", 0),
                    percent = o.optInt("percent", 100),
                    enabled = o.optBoolean("enabled", true),
                    priority = o.optInt("priority", 0)
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun persistOverrides(context: Context, rules: List<OverrideRule>) {
        val array = JSONArray()
        rules.forEach { rule ->
            array.put(JSONObject().apply {
                put("id", rule.id)
                put("key", rule.key)
                put("value", rule.value)
                put("userType", rule.userType)
                put("minVersion", rule.minVersion)
                put("percent", rule.percent)
                put("enabled", rule.enabled)
                put("priority", rule.priority)
            })
        }
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE).edit()
            .putString(KEY_OVERRIDES_JSON, array.toString()).apply()
    }

    /** إضافة/تحديث قاعدة من لوحة المطور — تُحفظ محلياً فتنجو من إعادة التشغيل. */
    suspend fun saveOverride(context: Context, rule: OverrideRule): Boolean {
        val currentList = loadOverridesFromPrefs(context).filterNot { it.id == rule.id }.toMutableList()
        val ruleToSave = if (rule.id.isBlank()) rule.copy(id = java.util.UUID.randomUUID().toString()) else rule
        currentList.add(ruleToSave)
        cachedOverrides = currentList
        persistOverrides(context, currentList)
        cached = null
        AuditLogger.log(context, "config_override_save", "${rule.key}=${rule.value} (${rule.userType} ${rule.percent}٪)")
        return true
    }

    /** حذف قاعدة — يُحفظ الحذف محلياً أيضاً. */
    suspend fun deleteOverride(context: Context, ruleId: String): Boolean {
        val currentList = loadOverridesFromPrefs(context).filterNot { it.id == ruleId }
        cachedOverrides = currentList
        persistOverrides(context, currentList)
        cached = null
        return true
    }

    /**
     * اللقطة الفعالة: القيم العامة ثم القواعد المطابقة مرتبة بالأولوية
     * تنازلياً — أول قاعدة مطابقة لكل مفتاح تفوز. المطابقة: مفعّلة،
     * والشريحة (الكل أو نوع المستخدم الحالي)، وأدنى إصدار، ونسبة
     * الطرح بدلو ثابت لكل جهاز.
     */
    private fun resolve(context: Context): ConfigData {
        val base = readLocal(context)
        val rules = loadOverridesFromPrefs(context)
            .filter { it.enabled }
            .sortedByDescending { it.priority }
        if (rules.isEmpty()) {
            cached = base
            return base
        }
        val userType = currentUserType(context)
        val version = currentVersionCode(context)
        val deviceBucket = stableBucket(context)
        var maintenance = base.maintenanceMode
        var accept = base.acceptRequests
        var aiReply = base.autoAiReply
        var message = base.maintenanceMessage
        var minVersion = base.minVersionCode
        val overriddenKeys = mutableSetOf<String>()
        for (rule in rules) {
            if (rule.key in overriddenKeys) continue
            if (rule.userType != "all" && rule.userType != userType) continue
            if (rule.minVersion > version) continue
            if (rule.percent < 100) {
                val bucket = (deviceBucket + rule.id.hashCode()) and 0x7FFFFFFF
                if (bucket % 100 >= rule.percent.coerceIn(0, 100)) continue
            }
            when (rule.key) {
                KEY_MAINTENANCE -> rule.value.toBooleanStrictOrNull()?.let { maintenance = it }
                KEY_ACCEPT_REQUESTS -> rule.value.toBooleanStrictOrNull()?.let { accept = it }
                KEY_AUTO_AI_REPLY -> rule.value.toBooleanStrictOrNull()?.let { aiReply = it }
                KEY_MAINTENANCE_MESSAGE -> message = rule.value
                KEY_MIN_VERSION -> rule.value.toIntOrNull()?.let { minVersion = it }
                else -> continue
            }
            overriddenKeys.add(rule.key)
        }
        val resolved = base.copy(
            maintenanceMode = maintenance,
            acceptRequests = accept,
            autoAiReply = aiReply,
            maintenanceMessage = message,
            minVersionCode = minVersion
        )
        cached = resolved
        return resolved
    }

    /** نوع المستخدم الحالي لشريحة القواعد (مطور/مميز/مجاني) — من المصادر المحلية نفسها. */
    private fun currentUserType(context: Context): String {
        return runCatching {
            val role = SupabaseServices.Auth.getUserRoleFromMetadata()
            val email = SupabaseServices.Auth.getCurrentUserEmail()
            val isDev = SupabaseServices.Auth.isAdminRole(role) ||
                (email != null && CloudServices.isOwnerAccount(email))
            if (isDev) return "developers"
            val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("is_premium", false)) "premium" else "free"
        }.getOrDefault("free")
    }

    /** دلو ثابت 0..99 لهذا الجهاز لنسب الطرح (ANDROID_ID). */
    private fun stableBucket(context: Context): Int {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        return (androidId.hashCode() and 0x7FFFFFFF) % 100
    }
}
