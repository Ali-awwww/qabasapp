package com.qabas.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 🛡️ نظام توثيق صنّاع المحتوى (شارة التوثيق) — بيد المطوّر حصراً.
 *
 * القواعد:
 * - لا يُمنح التوثيق إلا باستيفاء كل مهام الريلز أدناه (وهي مقصودة أن تكون صعبة قليلاً).
 * - القرار النهائي لمنح/سحب الشارة بيد المطوّر فقط.
 * - قائمة الموثّقين تُخزَّن محلياً وتُزامَن مع جدول verified_creators في Supabase
 *   لتظهر الشارة لكل المستخدمين لا على جهاز المطوّر وحده.
 */
object VerificationManager {

    private const val TAG = "VerificationManager"
    private const val QABAS_PREFS = "qabas_prefs"
    private const val REELS_PREFS = "qabas_reels_preferences"
    private const val KEY_STATS = "reels_stats_json"
    private const val VERIFIED_IDS_KEY = "verified_user_ids"
    private const val PUBLISHED_COUNT_KEY = "verif_reels_published"

    data class VerificationTask(
        val id: String,
        val title: String,
        val description: String,
        val target: Int,
        val unit: String
    )

    /** قواعد التوثيق المعروضة للمستخدم. */
    val RULES: List<String> = listOf(
        "أن يكون المحتوى أصيلاً شرعياً خالياً من المخالفات والنسخ.",
        "استيفاء كل مهام الريلز الصعبة أدناه بالكامل.",
        "الانتظام والمواظبة دون مخالفات خلال فترة التقييم.",
        "القرار النهائي لمنح أو سحب شارة التوثيق بيد المطوّر حصراً."
    )

    /** مهام الريلز المؤهِّلة للتوثيق — مقصودة أن تكون صعبة قليلاً. */
    val TASKS: List<VerificationTask> = listOf(
        VerificationTask("publish10", "نشر المحتوى", "انشر ١٠ مقاطع هادفة", 10, "مقطع"),
        VerificationTask("watch50", "المواظبة والمشاهدة", "شاهد ٥٠ مقطعاً", 50, "مقطع"),
        VerificationTask("like30", "التفاعل الإيجابي", "أعجب بـ٣٠ مقطعاً", 30, "إعجاب"),
        VerificationTask("save20", "الحفظ والفائدة", "احفظ ٢٠ مقطعاً", 20, "مقطع"),
        VerificationTask("share20", "نشر الخير", "شارك ٢٠ مقطعاً", 20, "مشاركة")
    )

    /** التقدم الحالي لمهمة معينة (يُقرأ من إحصاءات الريلز المحلية). */
    fun progressFor(context: Context, task: VerificationTask): Int {
        val qp = context.getSharedPreferences(QABAS_PREFS, Context.MODE_PRIVATE)
        if (task.id == "publish10") return qp.getInt(PUBLISHED_COUNT_KEY, 0)
        val rp = context.getSharedPreferences(REELS_PREFS, Context.MODE_PRIVATE)
        return runCatching {
            val obj = JSONObject(rp.getString(KEY_STATS, "{}") ?: "{}")
            when (task.id) {
                "watch50" -> obj.optInt("watched", 0)
                "like30" -> obj.optInt("likes", 0)
                "save20" -> obj.optInt("saved", 0)
                "share20" -> obj.optInt("shares", 0)
                else -> 0
            }
        }.getOrDefault(0)
    }

    fun completedTasks(context: Context): Int = TASKS.count { progressFor(context, it) >= it.target }

    fun areTasksComplete(context: Context): Boolean = completedTasks(context) >= TASKS.size

    /** يُسجّل نشر مقطع جديد ضمن عدّاد التوثيق. */
    fun recordReelPublished(context: Context) {
        val qp = context.getSharedPreferences(QABAS_PREFS, Context.MODE_PRIVATE)
        qp.edit().putInt(PUBLISHED_COUNT_KEY, qp.getInt(PUBLISHED_COUNT_KEY, 0) + 1).apply()
    }

    fun isDeveloper(context: Context): Boolean {
        val email = context.getSharedPreferences(QABAS_PREFS, Context.MODE_PRIVATE)
            .getString("user_email", null)
        return ReelPublishManager.isDeveloperUser(context, email)
    }

    fun localVerifiedIds(context: Context): Set<String> =
        context.getSharedPreferences(QABAS_PREFS, Context.MODE_PRIVATE)
            .getStringSet(VERIFIED_IDS_KEY, emptySet()) ?: emptySet()

    /** هل هذا المستخدم/الناشر موثّق؟ */
    fun isVerified(context: Context, userId: String): Boolean {
        val cleanId = userId.trim()
        if (cleanId.isBlank()) return false
        if (isDeveloper(context)) return true
        val ids = localVerifiedIds(context)
        val email = context.getSharedPreferences(QABAS_PREFS, Context.MODE_PRIVATE)
            .getString("user_email", null)
        return ids.any { it.equals(cleanId, true) } ||
            (!email.isNullOrBlank() && ids.any { it.equals(email, true) })
    }

    fun isSelfVerified(context: Context): Boolean {
        val email = context.getSharedPreferences(QABAS_PREFS, Context.MODE_PRIVATE)
            .getString("user_email", null)
        val id = SupabaseServices.Auth.getCurrentUserId() ?: email ?: ""
        return isVerified(context, id)
    }

    /** منح/سحب التوثيق — للمطوّر فقط. يُزامَن مع السحابة. */
    fun setVerified(context: Context, target: String, verified: Boolean): Boolean {
        if (!isDeveloper(context)) {
            Log.w(TAG, "setVerified rejected: caller is not a developer")
            return false
        }
        val clean = target.trim()
        if (clean.isBlank()) return false
        val prefs = context.getSharedPreferences(QABAS_PREFS, Context.MODE_PRIVATE)
        val ids = (prefs.getStringSet(VERIFIED_IDS_KEY, emptySet()) ?: emptySet()).toMutableSet()
        if (verified) ids.add(clean) else ids.removeAll { it.equals(clean, true) }
        prefs.edit().putStringSet(VERIFIED_IDS_KEY, ids).apply()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                if (verified) SupabaseServices.VerifiedCreators.addVerified(clean)
                else SupabaseServices.VerifiedCreators.removeVerified(clean)
            }.onFailure { Log.w(TAG, "cloud verify sync failed: ${it.message}") }
        }
        return true
    }

    /** تحميل قائمة الموثّقين عالمياً من السحابة إلى الذاكرة المحلية. */
    suspend fun refreshFromCloud(context: Context) = withContext(Dispatchers.IO) {
        if (!SupabaseServices.isSupabaseAvailable) return@withContext
        runCatching {
            val ids = SupabaseServices.VerifiedCreators.getVerifiedIds()
            if (ids.isNotEmpty()) {
                context.getSharedPreferences(QABAS_PREFS, Context.MODE_PRIVATE)
                    .edit().putStringSet(VERIFIED_IDS_KEY, ids.toSet()).apply()
            }
        }.onFailure { Log.w(TAG, "refreshFromCloud failed: ${it.message}") }
    }
}
