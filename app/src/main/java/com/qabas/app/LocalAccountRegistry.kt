package com.qabas.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 🛰️ مدير وسجل الحسابات الموحد (LocalAccountRegistry)
 * يحفظ ويوثق جميع الحسابات المنشأة على هذا الجهاز محلياً وسحابياً،
 * ويضمن ظهور أي حساب جديد فوراً في عداد الحسابات ولوحة القيادة وقسم إدارة المستخدمين.
 */
object LocalAccountRegistry {

    private const val TAG = "LocalAccountRegistry"
    private const val PREFS_NAME = "qabas_prefs"
    private const val KEY_ACCOUNTS_JSON = "key_registered_accounts_json"

    data class AccountRecord(
        val id: String,
        val name: String,
        val email: String,
        val customId: String,
        val accountNumber: String = "",
        val type: String = "مجاني", // "مطور", "خاص", "مجاني"
        val status: String = "active", // "active", "suspended"
        val regDateMillis: Long = System.currentTimeMillis(),
        val strikes: Int = 0,
        val projectCount: Int = 0,
        val avatarUri: String? = null
    ) {
        fun toDevUser(): DevUser {
            return DevUser(
                id = id,
                name = name.ifBlank { email.substringBefore('@') },
                email = email,
                type = when (type.lowercase(java.util.Locale.ROOT)) {
                    "developer", "مطور" -> "مطور"
                    "special", "خاص" -> "خاص"
                    else -> if (type.isNotBlank()) type else "مجاني"
                },
                projectCount = projectCount,
                regDate = DevDashboardFormatters.formatRegDate(regDateMillis),
                isSuspended = status == "suspended" || strikes >= 5,
                customId = customId.ifBlank { email.substringBefore('@') },
                accountNumber = accountNumber.takeIf { it.isNotBlank() }
            )
        }

        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("id", id)
                put("name", name)
                put("email", email)
                put("custom_id", customId)
                put("account_number", accountNumber)
                put("type", type)
                put("status", status)
                put("reg_date_millis", regDateMillis)
                put("strikes", strikes)
                put("project_count", projectCount)
                if (avatarUri != null) put("avatar_uri", avatarUri)
            }
        }

        companion object {
            fun fromJson(json: JSONObject): AccountRecord {
                val email = json.optString("email", "")
                val name = json.optString("name", "").ifBlank { email.substringBefore('@') }
                val customId = json.optString("custom_id", "").ifBlank { email.substringBefore('@') }
                val accountNumber = json.optString("account_number", "")
                return AccountRecord(
                    id = json.optString("id", UUID.randomUUID().toString()),
                    name = name,
                    email = email,
                    customId = customId,
                    accountNumber = accountNumber,
                    type = json.optString("type", "مجاني"),
                    status = json.optString("status", "active"),
                    regDateMillis = json.optLong("reg_date_millis", System.currentTimeMillis()),
                    strikes = json.optInt("strikes", 0),
                    projectCount = json.optInt("project_count", 0),
                    avatarUri = json.optString("avatar_uri", null)
                )
            }
        }
    }

    private val _accountsFlow = MutableStateFlow<List<AccountRecord>>(emptyList())
    val accountsFlow: StateFlow<List<AccountRecord>> = _accountsFlow.asStateFlow()

    private var isInitialized = false

    fun init(context: Context) {
        if (!isInitialized) {
            isInitialized = true
            syncFromPrefs(context)
        }
    }

    /**
     * قراءة الحسابات المخزنة محلياً وضمان احتساب الحساب الحالي المسجل للدخول
     */
    @Synchronized
    fun getAllAccounts(context: Context): List<AccountRecord> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_ACCOUNTS_JSON, null)
        val list = mutableListOf<AccountRecord>()

        if (!rawJson.isNullOrBlank()) {
            try {
                val array = JSONArray(rawJson)
                var droppedZombies = false
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val rec = AccountRecord.fromJson(obj)
                    // إسقاط السجلات الوهمية (بريد فارغ أو بدون @ مثل معرّفات UUID) — تنظيف ذاتي
                    if (rec.email.trim().contains("@")) {
                        list.add(rec)
                    } else {
                        droppedZombies = true
                        Log.i(TAG, "Dropped zombie account record (id=${rec.id})")
                    }
                }
                if (droppedZombies) {
                    saveListToPrefs(prefs, list)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse accounts JSON: ${e.message}")
            }
        }

        // 🌟 التأكد التام من إدراج الحساب الحالي المسجل على الهاتف إن لم يكن موجوداً في القائمة
        val currentEmail = prefs.getString("user_email", "")?.trim().orEmpty()
        val currentName = prefs.getString("user_name", "")?.trim()
            ?.ifBlank { prefs.getString("user_display_name", "")?.trim().orEmpty() }
            .orEmpty()
        val currentCustomId = prefs.getString("user_custom_id", "")?.trim()
            ?.ifBlank { UserIdentifierManager.getCustomId(context) }
            .orEmpty()
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        val isAdmin = prefs.getBoolean("is_admin", false) || CloudServices.isOwnerAccount(currentEmail)
        val isDev = prefs.getBoolean("is_developer", false) || isAdmin

        if (isLoggedIn && currentEmail.isNotBlank() && currentEmail != "guest@qabas.studio") {
            val exists = list.any { it.email.equals(currentEmail, ignoreCase = true) }
            val accNum = UserIdentifierManager.getOrCreateAccountNumber(context, currentEmail, currentName)
            if (!exists) {
                val role = if (isDev) "مطور" else "مجاني"
                val currentRec = AccountRecord(
                    id = currentEmail,
                    name = currentName.ifBlank { currentEmail.substringBefore('@') },
                    email = currentEmail,
                    customId = currentCustomId.ifBlank { UserIdentifierManager.normalize(currentName.ifBlank { currentEmail }) },
                    accountNumber = accNum,
                    type = role,
                    status = "active",
                    regDateMillis = System.currentTimeMillis()
                )
                list.add(0, currentRec)
                saveListToPrefs(prefs, list)
            } else {
                // تحديث رقم الحساب إن كان مفقوداً
                val idx = list.indexOfFirst { it.email.equals(currentEmail, ignoreCase = true) }
                if (idx != -1 && list[idx].accountNumber.isBlank() && accNum.isNotBlank()) {
                    list[idx] = list[idx].copy(accountNumber = accNum)
                    saveListToPrefs(prefs, list)
                }
            }
        }

        _accountsFlow.value = list
        return list
    }

    /**
     * تسجيل أو تحديث حساب مستخدم جديد وتوثيقه في السجل المحلي فوراً
     */
    @Synchronized
    fun registerOrUpdateAccount(
        context: Context,
        account: AccountRecord
    ): AccountRecord {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentList = getAllAccounts(context).toMutableList()

        // فحص هل الحساب موجود مسبقاً بنفس البريد أو الـ ID
        val index = currentList.indexOfFirst {
            it.email.equals(account.email, ignoreCase = true) || it.id == account.id
        }

        val updatedRecord = if (index != -1) {
            val existing = currentList[index]
            existing.copy(
                name = account.name.ifBlank { existing.name },
                customId = account.customId.ifBlank { existing.customId },
                type = if (account.type.isNotBlank()) account.type else existing.type,
                status = account.status,
                projectCount = if (account.projectCount > 0) account.projectCount else existing.projectCount
            ).also { currentList[index] = it }
        } else {
            account.also { currentList.add(0, it) }
        }

        saveListToPrefs(prefs, currentList)
        _accountsFlow.value = currentList
        Log.i(TAG, "Account registered/updated: ${account.email} (total accounts: ${currentList.size})")
        return updatedRecord
    }

    /**
     * حذف حساب من السجل المحلي
     */
    @Synchronized
    fun removeAccount(context: Context, emailOrId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentList = getAllAccounts(context).toMutableList()
        val removed = currentList.removeAll {
            it.email.equals(emailOrId, ignoreCase = true) || it.id == emailOrId
        }
        if (removed) {
            saveListToPrefs(prefs, currentList)
            _accountsFlow.value = currentList
        }
        return removed
    }

    /**
     * جلب الحساب حسب البريد الإلكتروني
     */
    fun findByEmail(context: Context, email: String): AccountRecord? {
        return getAllAccounts(context).find { it.email.equals(email.trim(), ignoreCase = true) }
    }

    /**
     * مزامنة وتحديث حالة الحساب الحالي المسجل بالهاتف
     */
    fun syncCurrentLoggedInUser(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val email = prefs.getString("user_email", "")?.trim().orEmpty()
        val name = prefs.getString("user_name", "")?.trim()
            ?.ifBlank { prefs.getString("user_display_name", "")?.trim().orEmpty() }
            .orEmpty()
        val customId = prefs.getString("user_custom_id", "")?.trim().orEmpty()
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        val isAdmin = prefs.getBoolean("is_admin", false) || CloudServices.isOwnerAccount(email)

        if (isLoggedIn && email.isNotBlank() && email != "guest@qabas.studio") {
            val accNum = UserIdentifierManager.getOrCreateAccountNumber(context, email, name)
            registerOrUpdateAccount(
                context = context,
                account = AccountRecord(
                    id = email,
                    name = name.ifBlank { email.substringBefore('@') },
                    email = email,
                    customId = customId.ifBlank { UserIdentifierManager.normalize(name.ifBlank { email }) },
                    accountNumber = accNum,
                    type = if (isAdmin) "مطور" else "مجاني",
                    status = "active"
                )
            )
        }
    }

    private fun syncFromPrefs(context: Context) {
        getAllAccounts(context)
    }

    private fun saveListToPrefs(prefs: SharedPreferences, list: List<AccountRecord>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_ACCOUNTS_JSON, array.toString()).apply()
    }
}
