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
     * الحساب الأول في النظام (أو حساب المالك الأساسي) يحصل دائماً على #QBS-0001
     */
    fun getOrCreateAccountNumber(context: Context, email: String?, displayName: String? = null): String {
        if (email.isNullOrBlank() || email.lowercase(Locale.ROOT) == "guest@qabas.studio" || email.lowercase(Locale.ROOT) == "guest") {
            return ""
        }
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. فحص هل يوجد رقم حساب محفوظ مسبقاً لهذا البريد تحديداً
        val savedForEmail = prefs.getString(KEY_ACCOUNT_MAP_PREFIX + cleanEmail, null)
        if (!savedForEmail.isNullOrBlank()) {
            val formatted = formatAccountNumber(savedForEmail)
            prefs.edit().putString(KEY_ACCOUNT_NUMBER, formatted).apply()
            return formatted
        }

        // 2. إذا كان حساب المالك الأساسي للمنصة، يحصل حصرياً على الحساب رقم 1
        if (CloudServices.isOwnerAccount(cleanEmail) || cleanEmail.contains("mousa.alharbi.pro@gmail.com")) {
            val firstAcc = "#QBS-0001"
            prefs.edit()
                .putString(KEY_ACCOUNT_NUMBER, firstAcc)
                .putString(KEY_ACCOUNT_MAP_PREFIX + cleanEmail, firstAcc)
                .apply()
            return firstAcc
        }

        // 3. فحص عداد الحسابات المتسلسل في التطبيق لتعيين الرقم الحقيقي التالي
        val existingAccounts = LocalAccountRegistry.getAllAccounts(context)

        // 3أ. إن كان الحساب مسجلاً مسبقاً برقم معتمد، أعده كما هو (يمنع تضخم الترقيم بعد حذف البيانات)
        val existingNumber = existingAccounts
            .firstOrNull { it.email.equals(cleanEmail, ignoreCase = true) }
            ?.accountNumber?.takeIf { it.isNotBlank() }
            ?.let { formatAccountNumber(it) }
        if (!existingNumber.isNullOrBlank()) {
            prefs.edit()
                .putString(KEY_ACCOUNT_NUMBER, existingNumber)
                .putString(KEY_ACCOUNT_MAP_PREFIX + cleanEmail, existingNumber)
                .apply()
            return existingNumber
        }

        val isFirstInRegistry = existingAccounts.isEmpty() || (existingAccounts.size == 1 && existingAccounts.first().email.equals(cleanEmail, ignoreCase = true))

        val currentSeq = prefs.getInt(KEY_NEXT_ACCOUNT_SEQ, 0)
        val assignedSeq = if (isFirstInRegistry && currentSeq <= 1) {
            1
        } else {
            val next = if (currentSeq < 1) 2 else currentSeq + 1
            next
        }

        prefs.edit().putInt(KEY_NEXT_ACCOUNT_SEQ, maxOf(currentSeq, assignedSeq)).apply()

        val generatedAcc = "#QBS-" + String.format(Locale.ROOT, "%04d", assignedSeq)
        prefs.edit()
            .putString(KEY_ACCOUNT_NUMBER, generatedAcc)
            .putString(KEY_ACCOUNT_MAP_PREFIX + cleanEmail, generatedAcc)
            .apply()
        return generatedAcc
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
        val cleanSeed = seed?.substringBefore("@")
            ?.lowercase(Locale.ROOT)
            ?.replace("[^a-zA-Z0-9]".toRegex(), "")
            ?.take(10)

        return if (!cleanSeed.isNullOrBlank() && cleanSeed.length >= 3) {
            val randomSuffix = (1000..9999).random()
            "${cleanSeed}_$randomSuffix"
        } else {
            val randomSuffix = (10000..99999).random()
            "qbs_$randomSuffix"
        }
    }

    /**
     * استعراض رقم الحساب الرسمي القادم المتوقع للمستخدم الجديد دون تثبيته في العداد بعد.
     */
    fun peekNextAccountNumber(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingAccounts = LocalAccountRegistry.getAllAccounts(context)
        val currentSeq = prefs.getInt(KEY_NEXT_ACCOUNT_SEQ, 0)
        val candidateSeq = if (existingAccounts.isEmpty() && currentSeq <= 1) {
            1
        } else {
            if (currentSeq < 1) 2 else currentSeq + 1
        }
        return "#QBS-" + String.format(Locale.ROOT, "%04d", candidateSeq)
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
        if (email.isBlank() || email.lowercase(Locale.ROOT) == "guest@qabas.studio") return@withContext null
        if (!SupabaseServices.isSupabaseAvailable) return@withContext null

        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val cloudUser = SupabaseServices.Database.getUserByEmail(cleanEmail)

        val localCustomId = getCustomId(context, cleanEmail).ifBlank { getOrCreateCustomId(context, cleanEmail, displayName) }
        val localAccountNumber = getOrCreateAccountNumber(context, cleanEmail, displayName)

        if (cloudUser != null) {
            val resolvedCustomId = cloudUser.customId?.takeIf { it.isNotBlank() } ?: localCustomId
            val resolvedAccountNumber = cloudUser.accountNumber?.takeIf { it.isNotBlank() } ?: localAccountNumber

            saveCustomId(context, resolvedCustomId, cleanEmail)
            saveAccountNumber(context, cleanEmail, resolvedAccountNumber)

            // إثراء السجل السحابي إذا كانت الحقول فارغة فيه
            if (cloudUser.customId.isNullOrBlank() || cloudUser.accountNumber.isNullOrBlank()) {
                val userId = cloudUser.id.ifBlank { SupabaseServices.Auth.getCurrentUserId() ?: cleanEmail }
                SupabaseServices.Database.saveUser(
                    id = userId,
                    name = cloudUser.name ?: displayName ?: cleanEmail.substringBefore("@"),
                    email = cleanEmail,
                    type = cloudUser.type,
                    customId = resolvedCustomId,
                    accountNumber = resolvedAccountNumber
                )
            }

            Pair(resolvedCustomId, resolvedAccountNumber)
        } else {
            // مستخدم جديد في السحابة
            val userId = SupabaseServices.Auth.getCurrentUserId() ?: cleanEmail
            SupabaseServices.Database.saveUser(
                id = userId,
                name = displayName ?: cleanEmail.substringBefore("@"),
                email = cleanEmail,
                type = "مجاني",
                customId = localCustomId,
                accountNumber = localAccountNumber
            )
            Pair(localCustomId, localAccountNumber)
        }
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
        if (email.isNullOrBlank() || email.lowercase(Locale.ROOT) == "guest@qabas.studio" || email.lowercase(Locale.ROOT) == "guest") {
            return@withContext ""
        }
        val cleanEmail = email.trim().lowercase(Locale.ROOT)

        // 1. حساب المالك الرئيسي دائماً #QBS-0001
        if (CloudServices.isOwnerAccount(cleanEmail) || cleanEmail.contains("mousa.alharbi.pro@gmail.com")) {
            val firstAcc = "#QBS-0001"
            saveAccountNumber(context, cleanEmail, firstAcc)
            return@withContext firstAcc
        }

        // 2. التحقق من السحابة أولاً
        if (SupabaseServices.isSupabaseAvailable) {
            val cloudUser = SupabaseServices.Database.getUserByEmail(cleanEmail)
            if (cloudUser != null && !cloudUser.accountNumber.isNullOrBlank()) {
                val cloudAcc = formatAccountNumber(cloudUser.accountNumber)
                saveAccountNumber(context, cleanEmail, cloudAcc)
                return@withContext cloudAcc
            }

            // مستخدم ليس لديه رقم مسجل في السحابة -> استدعاء تسلسل العداد السحابي مباشرة
            val cloudSeq = SupabaseServices.Database.getNextAccountSequenceFromCloud()
            val generatedAcc = "#QBS-" + String.format(Locale.ROOT, "%04d", cloudSeq)
            saveAccountNumber(context, cleanEmail, generatedAcc)

            // تثبيته في السحابة في جدول المستخدمين
            val userId = cloudUser?.id ?: SupabaseServices.Auth.getCurrentUserId() ?: cleanEmail
            val currentCustomId = cloudUser?.customId ?: getCustomId(context, cleanEmail).ifBlank { generateUniqueId(cleanEmail) }
            SupabaseServices.Database.saveUser(
                id = userId,
                name = cloudUser?.name ?: displayName ?: cleanEmail.substringBefore("@"),
                email = cleanEmail,
                type = cloudUser?.type ?: "مجاني",
                customId = currentCustomId,
                accountNumber = generatedAcc
            )
            return@withContext generatedAcc
        }

        // 3. كاش محلي كخيار احتياطي في حال عدم الاتصال
        getOrCreateAccountNumber(context, cleanEmail, displayName)
    }

    /**
     * استرجاع المعرّف الخاص للمستخدم الحالي المسجل فقط.
     * لا يقوم بتوليد يوزر وهمي عشوائي للمستخدمين غير المسجلين.
     */
    fun getCustomId(context: Context, email: String? = null): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cleanEmail = email?.trim()?.lowercase(Locale.ROOT)
        if (!cleanEmail.isNullOrBlank()) {
            // المعرّف مربوط بالبريد أولاً: لا نعيد معرّف حساب آخر مسجّل على نفس الجهاز
            val perEmail = prefs.getString(KEY_CUSTOM_ID_MAP_PREFIX + cleanEmail, null)
            if (!perEmail.isNullOrBlank() && !perEmail.equals("guest", ignoreCase = true) && !perEmail.equals("ضيف", ignoreCase = true)) {
                return normalize(perEmail)
            }
        }
        val savedId = prefs.getString(KEY_CUSTOM_ID, null)
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        val sessionEmail = cleanEmail ?: prefs.getString("user_email", null)?.trim()?.lowercase(Locale.ROOT)
        val isGuestSession = sessionEmail.isNullOrBlank() || sessionEmail.contains("guest") || sessionEmail == "guest@qabas.studio"
        val isSavedIdGuest = savedId.equals("guest", ignoreCase = true) || savedId.equals("ضيف", ignoreCase = true)

        if (!savedId.isNullOrBlank() && (!isSavedIdGuest || isGuestSession) && (isLoggedIn || !sessionEmail.isNullOrBlank())) {
            if (cleanEmail.isNullOrBlank() && !isSavedIdGuest) return savedId
            val ownerEmail = prefs.getString(KEY_MAP_PREFIX + normalize(savedId), null)
                ?.trim()?.lowercase(Locale.ROOT)
            if (ownerEmail.isNullOrBlank() || ownerEmail == cleanEmail) {
                if (!isSavedIdGuest || isGuestSession) return savedId
            }
        }

        // فحص بديل من user_tag القديم فقط إذا كان هناك جلسة نشطة
        if (isLoggedIn || !sessionEmail.isNullOrBlank()) {
            if (!isGuestSession) {
                val oldTag = prefs.getString(KEY_USER_TAG, null)?.removePrefix("@")
                if (!oldTag.isNullOrBlank() && oldTag != "qabas_creator" && oldTag != "user" && !oldTag.equals("guest", ignoreCase = true) && !oldTag.equals("ضيف", ignoreCase = true)) {
                    val normalized = normalize(oldTag)
                    prefs.edit().putString(KEY_CUSTOM_ID, normalized).apply()
                    return normalized
                }

                val name = prefs.getString("user_name", null)
                val newId = generateUniqueId(name ?: sessionEmail)
                saveCustomId(context, newId, sessionEmail)
                return newId
            } else {
                return "guest"
            }
        }

        // المستخدم غير مسجل ولا توجد جلسة: الحقل فارغ تماماً ولا يولد اسماً عشوائياً
        return ""
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
    fun getOrCreateCustomId(context: Context, email: String?, displayName: String? = null): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cleanEmail = email?.trim()?.lowercase(Locale.ROOT)

        if (!cleanEmail.isNullOrBlank()) {
            val perEmail = prefs.getString(KEY_CUSTOM_ID_MAP_PREFIX + cleanEmail, null)
            if (!perEmail.isNullOrBlank()) {
                val normalized = normalize(perEmail)
                prefs.edit()
                    .putString(KEY_CUSTOM_ID, normalized)
                    .apply()
                return normalized
            }
        }

        val existing = prefs.getString(KEY_CUSTOM_ID, null)
        if (!existing.isNullOrBlank()) {
            if (cleanEmail.isNullOrBlank()) return existing
            val ownerEmail = prefs.getString(KEY_MAP_PREFIX + existing, null)
                ?.trim()?.lowercase(Locale.ROOT)
            if (ownerEmail.isNullOrBlank() || ownerEmail == cleanEmail) {
                saveCustomId(context, existing, cleanEmail)
                return existing
            }
        }

        val generated = generateUniqueId(displayName ?: cleanEmail)
        saveCustomId(context, generated, cleanEmail)
        return generated
    }

    /**
     * فك شفرة المدخل (سواء كان بريداً أو معرّفاً خاصاً) إلى البريد الإلكتروني الفعلي للمصادقة.
     * يعيد Pair(البريد الإلكتروني, المعرّف الخاص المكتشف).
     */
    suspend fun resolveIdentifierToEmail(input: String, context: Context): Pair<String, String>? = withContext(Dispatchers.IO) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return@withContext null

        // 1. إذا كان بريداً إلكترونياً صريحاً (مثل user@gmail.com)
        if (isEmail(trimmed)) {
            val lower = trimmed.lowercase(Locale.ROOT)
            return@withContext Pair(lower, "")
        }

        val cleanId = normalize(trimmed)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 2. التحقق من التخزين المؤقت المحلي (Local Map Cache)
        val localEmail = prefs.getString(KEY_MAP_PREFIX + cleanId, null)
        if (!localEmail.isNullOrBlank() && isEmail(localEmail)) {
            return@withContext Pair(localEmail, cleanId)
        }

        // فحص هل هو معرّف الحساب الحالي المخزن في التطبيق
        val currentSavedId = prefs.getString(KEY_CUSTOM_ID, null)
        val currentEmail = prefs.getString("user_email", null)
        if (currentSavedId.equals(cleanId, ignoreCase = true) && !currentEmail.isNullOrBlank()) {
            prefs.edit().putString(KEY_MAP_PREFIX + cleanId, currentEmail).apply()
            return@withContext Pair(currentEmail, cleanId)
        }

        // 3. التحقق السحابي عبر Supabase Database
        if (SupabaseServices.isSupabaseAvailable) {
            val remoteEmail = SupabaseServices.Database.findEmailByCustomId(cleanId)
            if (!remoteEmail.isNullOrBlank()) {
                // حفظ في الكاش المحلي للمرات القادمة
                prefs.edit().putString(KEY_MAP_PREFIX + cleanId, remoteEmail).apply()
                return@withContext Pair(remoteEmail, cleanId)
            }
        }

        // لم يتم العثور على بريد مرتبط بالمعرّف
        return@withContext null
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
