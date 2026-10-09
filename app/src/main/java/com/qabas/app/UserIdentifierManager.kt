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
     * استرجاع المعرّف الخاص للمستخدم الحالي أو إنشاؤه وتخزينه إن لم يكن متوفراً.
     */
    fun getCustomId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedId = prefs.getString(KEY_CUSTOM_ID, null)
        if (!savedId.isNullOrBlank()) {
            return savedId
        }

        // فحص بديل من user_tag القديم
        val oldTag = prefs.getString(KEY_USER_TAG, null)?.removePrefix("@")
        if (!oldTag.isNullOrBlank() && oldTag != "qabas_creator" && oldTag != "user") {
            val normalized = normalize(oldTag)
            prefs.edit().putString(KEY_CUSTOM_ID, normalized).apply()
            return normalized
        }

        // توليد معرّف جديد بناءً على البريد أو الاسم
        val email = prefs.getString("user_email", null)
        val name = prefs.getString("user_name", null)
        val newId = generateUniqueId(name ?: email)

        saveCustomId(context, newId, email)
        return newId
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
            editor.putString(KEY_MAP_PREFIX + clean, resolvedEmail.trim().lowercase(Locale.ROOT))
        }
        editor.apply()
    }

    /**
     * إنشاء أو استعادة المعرّف مع ربطه ببريد Gmail المحدد.
     */
    fun getOrCreateCustomId(context: Context, email: String?, displayName: String? = null): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_CUSTOM_ID, null)
        if (!existing.isNullOrBlank()) {
            if (!email.isNullOrBlank()) {
                prefs.edit().putString(KEY_MAP_PREFIX + existing, email.trim().lowercase(Locale.ROOT)).apply()
            }
            return existing
        }

        val generated = generateUniqueId(displayName ?: email)
        saveCustomId(context, generated, email)
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
