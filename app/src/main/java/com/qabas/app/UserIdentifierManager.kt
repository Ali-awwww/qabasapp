package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

/**
 * مدير المعرّف الخاص والموحد للمستخدمين (User Identifier Manager).
 * يتيح لكل مستخدم امتلاك معرّف فريد خاص به (مثل @username أو QBS-1082)
 * ويدعم تسجيل الدخول المزدوج: إما بالمعرّف الخاص أو بحساب Gmail / البريد الإلكتروني.
 */
object UserIdentifierManager {

    private const val PREFS_NAME = "qabas_prefs"
    private const val KEY_CUSTOM_ID = "user_custom_id"
    private const val KEY_USER_TAG = "user_tag"
    private const val KEY_MAP_PREFIX = "user_id_map_"
    private const val KEY_CUSTOM_ID_MAP_PREFIX = "user_custom_id_map_"
    const val KEY_ACCOUNT_NUMBER = "user_account_number"
    private const val KEY_ACCOUNT_MAP_PREFIX = "user_acc_map_"
    private const val KEY_NEXT_ACCOUNT_SEQ = "next_account_seq_num"

    /**
     * تنسيق رقم الحساب الرسمي للمستخدم (مثل: #QBS-0001)
     */
    fun formatAccountNumber(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        return if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    }

    /**
     * فحص هل هذا الحساب هو أول حساب تم إنشاؤه في التطبيق (#QBS-0001)
     */
    fun isFirstAccount(accNumber: String?): Boolean {
        if (accNumber.isNullOrBlank()) return false
        val clean = accNumber.removePrefix("#").uppercase(Locale.ROOT)
        return clean == "QBS-0001" || clean == "QBS-1" || clean == "0001" || clean == "1"
    }

    /**
     * استرجاع أو إنشاء رقم الحساب الرسمي الحقيقي والمتسلسل للمستخدم.
     * ملاحظة: يفضل استخدام ensureCloudAccountNumber لضمان المزامنة مع السحابة.
     */
    /**
     * Legacy compatibility accessor. This value is only a cache of a cloud-issued
     * number; a cache miss never allocates or guesses an account number.
     */
    fun getOrCreateAccountNumber(context: Context, email: String?, displayName: String? = null): String {
        if (email.isNullOrBlank()) return ""
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getString(KEY_ACCOUNT_MAP_PREFIX + cleanEmail, null)
            ?.takeIf { it.isNotBlank() }
            ?: return ""
        return formatAccountNumber(cached)
    }

    /**
     * جلب رقم الحساب الحالي للمستخدم النشط
     */
    fun getAccountNumber(context: Context, email: String? = null): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!email.isNullOrBlank()) {
            val cleanEmail = email.trim().lowercase(Locale.ROOT)
            val mapped = prefs.getString(KEY_ACCOUNT_MAP_PREFIX + cleanEmail, null)
            if (!mapped.isNullOrBlank()) return formatAccountNumber(mapped)
        }
        // A caller asking for a specific account must never receive the device-wide
        // "current" number: it may belong to the previously signed-in account.
        if (!email.isNullOrBlank()) return ""
        val current = prefs.getString(KEY_ACCOUNT_NUMBER, "") ?: ""
        return if (current.isNotBlank()) formatAccountNumber(current) else ""
    }

    /**
     * حفظ وتثبيت رقم الحساب لمستخدم معين
     */
    fun saveAccountNumber(context: Context, email: String, accountNumber: String) {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val formatted = formatAccountNumber(accountNumber)
        if (cleanEmail.isBlank() || formatted.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_ACCOUNT_NUMBER, formatted)
            .putString(KEY_ACCOUNT_MAP_PREFIX + cleanEmail, formatted)
            .apply()
    }

    /**
     * تنسيق اسم المستخدم ليحتوي على رقم الحساب المعتمد في التطبيق
     * مثال: "موسى الحربي (#QBS-0001)"
     */
    fun formatDisplayNameWithAccount(displayName: String, accountNumber: String?): String {
        val cleanName = displayName.trim()
        if (accountNumber.isNullOrBlank()) return cleanName
        val formattedAcc = formatAccountNumber(accountNumber)
        if (cleanName.contains(formattedAcc)) return cleanName
        return "$cleanName ($formattedAcc)"
    }

    /**
     * نسخ رقم الحساب للحافظة
     */
    fun copyAccountNumberToClipboard(context: Context, accountNumber: String, showToast: Boolean = true) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val display = formatAccountNumber(accountNumber)
        val clip = ClipData.newPlainText("Qabas Account Number", display)
        clipboard?.setPrimaryClip(clip)
        if (showToast) {
            Toast.makeText(context, "تم نسخ رقم الحساب ($display) إلى الحافظة 📋", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * تنظيف وضبط المعرّف لإزالة الفراغات والرموز الزائدة وتحويله إلى حروف صغيرة موحدة.
     */
    fun normalize(raw: String): String {
        return raw.trim()
            .removePrefix("@")
            .lowercase(Locale.ROOT)
            .replace("\\s+".toRegex(), "_")
            .replace("[^a-zA-Z0-9_\\-]".toRegex(), "")
    }

    /**
     * تنسيق المعرّف للعرض المباشر في الواجهات (مثل @username).
     */
    fun formatDisplay(identifier: String): String {
        val trimmed = identifier.trim()
        if (trimmed.isBlank()) return "@qabas_user"
        return if (trimmed.startsWith("@")) trimmed else "@$trimmed"
    }

    /**
     * فحص هل المدخل عبارة عن بريد إلكتروني صريح (Gmail أو غيره) أم معرّف خاص.
     */
    fun isEmail(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.startsWith("@")) return false // معرّف يبدأ بـ @
        val atIndex = trimmed.indexOf('@')
        if (atIndex <= 0) return false
        val domain = trimmed.substring(atIndex + 1)
        return domain.contains(".") && !domain.startsWith(".") && !domain.endsWith(".")
    }

    /**
     * توليد معرّف فريد تلقائي للمستخدم اعتماداً على البريد أو الاسم أو عشوائياً.
     */
    fun generateUniqueId(seed: String? = null): String {
        val cleanSeed = if (seed?.contains("@") == true) {
            seed.substringBefore("@")
        } else seed
        val normalizedSeed = cleanSeed?.lowercase(Locale.ROOT)
            ?.replace("[^a-zA-Z0-9]".toRegex(), "")
            ?.take(10)

        return if (!normalizedSeed.isNullOrBlank() && normalizedSeed.length >= 3) {
            val randomSuffix = (1000..9999).random()
            "${normalizedSeed}_$randomSuffix"
        } else {
            val randomSuffix = (10000..99999).random()
            "qbs_$randomSuffix"
        }
    }

    /**
     * (ملغى) كان يستخدم للتنبؤ برقم الحساب محلياً.
     * تم إيقافه لضمان أن رقم الحساب يأتي من السحابة فقط بعد نجاح العملية.
     */
    fun peekNextAccountNumber(context: Context): String {
        return "يُنشأ بعد التسجيل"
    }

    /**
     * استعلام ومزامنة الهوية السحابية للمستخدم (اليوزر ورقم الحساب).
     * يقوم بجلب بيانات الحساب الموثقة من السحابة وتثبيتها محلياً إن وُجدت، أو رفعها إلى السحابة إن كانت جديدة.
     */
    suspend fun syncIdentityWithCloud(
        context: Context,
        email: String,
        displayName: String? = null
    ): Pair<String, String>? = withContext(Dispatchers.IO) {
        if (email.isBlank() || !SupabaseServices.isSupabaseAvailable) return@withContext null

        val authUid = SupabaseServices.Auth.awaitCurrentUserId() ?: return@withContext null
        val authEmail = SupabaseServices.Auth.getCurrentUserEmail()
            ?.trim()?.lowercase(Locale.ROOT)
            ?: return@withContext null
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        if (cleanEmail != authEmail) return@withContext null

        val cloudUser = SupabaseServices.Database.ensureUser(
            externalId = authUid,
            email = authEmail,
            name = displayName
        ) ?: return@withContext null

        val resolvedCustomId = cloudUser.customId?.takeIf { it.isNotBlank() }
            ?: return@withContext null
        val resolvedAccountNumber = cloudUser.accountNumber?.takeIf { it.isNotBlank() }
            ?: return@withContext null

        saveCustomId(context, resolvedCustomId, authEmail)
        saveAccountNumber(context, authEmail, resolvedAccountNumber)
        Pair(resolvedCustomId, resolvedAccountNumber)
    }

    sealed class CustomIdAvailabilityResult {
        object Available : CustomIdAvailabilityResult()
        object CurrentUserOwnsIt : CustomIdAvailabilityResult()
        data class Taken(val reason: String) : CustomIdAvailabilityResult()
        data class Invalid(val reason: String) : CustomIdAvailabilityResult()
    }

    /**
     * فحص هل المعرّف متاح أم مستخدم بالفعل من قبل شخص آخر لمنع تكرار المعرفات.
     */
    suspend fun checkCustomIdAvailability(
        context: Context,
        customId: String,
        currentEmail: String? = null
    ): CustomIdAvailabilityResult = withContext(Dispatchers.IO) {
        val clean = normalize(customId)
        if (clean.isBlank()) {
            return@withContext CustomIdAvailabilityResult.Invalid("يرجى إدخال معرّف صحيح")
        }
        if (clean.length < 3) {
            return@withContext CustomIdAvailabilityResult.Invalid("المعرّف يجب أن يكون 3 أحرف أو أرقام على الأقل")
        }
        if (clean.length > 25) {
            return@withContext CustomIdAvailabilityResult.Invalid("المعرّف يجب ألا يتجاوز 25 حرفاً")
        }

        val cleanEmail = currentEmail?.trim()?.lowercase(Locale.ROOT)
        val mySavedId = getCustomId(context, currentEmail)
        if (clean.equals(normalize(mySavedId), ignoreCase = true)) {
            return@withContext CustomIdAvailabilityResult.CurrentUserOwnsIt
        }

        // 1. التحقق من السجل المحلي
        val localMatches = LocalAccountRegistry.getAllAccounts(context)
        val localDuplicate = localMatches.firstOrNull {
            it.customId.equals(clean, ignoreCase = true) &&
            (cleanEmail.isNullOrBlank() || !it.email.equals(cleanEmail, ignoreCase = true))
        }
        if (localDuplicate != null) {
            return@withContext CustomIdAvailabilityResult.Taken("المعرّف @$clean مستخدم بالفعل على هذا الجهاز")
        }

        // 2. التحقق من السحابة في قاعدة بيانات Supabase
        if (SupabaseServices.isSupabaseAvailable) {
            val cloudUser = SupabaseServices.Database.getUserByCustomId(clean)
            if (cloudUser != null) {
                val cloudEmail = cloudUser.email?.trim()?.lowercase(Locale.ROOT)
                if (!cleanEmail.isNullOrBlank() && cloudEmail == cleanEmail) {
                    return@withContext CustomIdAvailabilityResult.CurrentUserOwnsIt
                }
                return@withContext CustomIdAvailabilityResult.Taken("المعرّف @$clean محجوز بالفعل لمستخدم آخر في السحابة")
            }
        }

        CustomIdAvailabilityResult.Available
    }

    /**
     * جلب وتثبيت رقم الحساب الحقيقي من السحابة (Supabase Cloud).
     * يضمن أن عداد الحسابات يجلب أرقام الحسابات من السحابة حصراً ولا يعتمد فقط على الذاكرة المحلية.
     */
    suspend fun ensureCloudAccountNumber(
        context: Context,
        email: String?,
        displayName: String? = null
    ): String = withContext(Dispatchers.IO) {
        val cleanEmail = email?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (cleanEmail.isBlank() || !SupabaseServices.isSupabaseAvailable) return@withContext ""

        val authUid = SupabaseServices.Auth.awaitCurrentUserId() ?: return@withContext ""
        val authEmail = SupabaseServices.Auth.getCurrentUserEmail()
            ?.trim()?.lowercase(Locale.ROOT)
            ?: return@withContext ""
        if (authEmail != cleanEmail) return@withContext ""

        val cloudUser = SupabaseServices.Database.ensureUser(
            externalId = authUid,
            email = authEmail,
            name = displayName
        ) ?: return@withContext ""

        val accountNumber = cloudUser.accountNumber?.takeIf { it.isNotBlank() }
            ?: return@withContext ""
        val customId = cloudUser.customId?.takeIf { it.isNotBlank() }
        if (customId != null) saveCustomId(context, customId, authEmail)
        saveAccountNumber(context, authEmail, accountNumber)
        accountNumber
    }

    /**
     * استرجاع المعرّف الخاص للمستخدم الحالي المسجل فقط.
     * لا يقوم بتوليد يوزر وهمي عشوائي للمستخدمين غير المسجلين.
     */
    fun getCustomId(context: Context, email: String? = null): String {
        val cleanEmail = email?.trim()?.lowercase(Locale.ROOT)
            ?: return ""
        if (cleanEmail.isBlank()) return ""

        // This is a cache lookup only. A missing value must be fetched from
        // the authenticated cloud profile; the device never invents a handle.
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getString(KEY_CUSTOM_ID_MAP_PREFIX + cleanEmail, null)
            ?.takeIf { it.isNotBlank() && !it.equals("guest", ignoreCase = true) && !it.equals("ضيف", ignoreCase = true) }
            ?: return ""
        return normalize(cached)
    }

    /**
     * حفظ المعرّف الخاص وتحديث الخريطة المحلية والمزامنة.
     */
    fun saveCustomId(context: Context, customId: String, email: String? = null) {
        val clean = normalize(customId)
        if (clean.isBlank()) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString(KEY_CUSTOM_ID, clean)
        editor.putString(KEY_USER_TAG, formatDisplay(clean))

        val resolvedEmail = email ?: prefs.getString("user_email", null)
        if (!resolvedEmail.isNullOrBlank()) {
            val cleanEmail = resolvedEmail.trim().lowercase(Locale.ROOT)
            editor.putString(KEY_MAP_PREFIX + clean, cleanEmail)
            editor.putString(KEY_CUSTOM_ID_MAP_PREFIX + cleanEmail, clean)
        }
        editor.apply()
    }

    /**
     * إنشاء أو استعادة المعرّف مع ربطه ببريد Gmail المحدد.
     * المعرّف يُحفظ لكل بريد على حدة حتى لا يتغيّر عند تسجيل الدخول مجدداً.
     */
    /**
     * Returns the last cloud-issued handle cached for this account.
     * It intentionally does not generate an identifier on the device.
     */
    fun getOrCreateCustomId(context: Context, email: String): String =
        getCustomId(context, email)



    /**
     * فك شفرة المدخل (سواء كان بريداً أو معرّفاً خاصاً) إلى البريد الإلكتروني الفعلي للمصادقة.
     * يعيد Pair(البريد الإلكتروني, المعرّف الخاص المكتشف).
     */
    /**
     * Email inputs are returned unchanged. A username/account number must never
     * be resolved to an email on the client before authentication; the server-side
     * qabas-auth-identifier-login function performs that lookup and issues the Auth session.
     */
    suspend fun resolveIdentifierToEmail(input: String, context: Context): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            val trimmed = input.trim()
            if (!isEmail(trimmed)) return@withContext null
            Pair(trimmed.lowercase(Locale.ROOT), "")
        }

    /**
     * نسخ المعرّف الخاص للحافظة مع إشعار للمستخدم.
     */
    fun copyToClipboard(context: Context, customId: String, showToast: Boolean = true) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val display = formatDisplay(customId)
        val clip = ClipData.newPlainText("Qabas User ID", display)
        clipboard?.setPrimaryClip(clip)
        if (showToast) {
            Toast.makeText(context, "تم نسخ المعرّف الخاص ($display) إلى الحافظة 📋", Toast.LENGTH_SHORT).show()
        }
    }
}
