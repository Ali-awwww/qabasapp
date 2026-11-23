package com.qabas.app

import android.content.Context
import android.util.Log
import java.util.Calendar
import java.util.Date

/**
 * نظام المكافآت والدوري الأسبوعي المستقل عبر Supabase وقاعدة البيانات المحلية
 */
object LeagueService {
    private const val TAG = "LeagueService"

    // فحص وتحديث حالة دوري المتصدرين
    suspend fun checkAndProcessWeeklyLeague(context: Context) {
        try {
            val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            val now = Date()
            val endMillis = prefs.getLong("league_end_date_ms", 0L)

            // ق61 — إصلاح أثر خطأ قديم: المكافأة الميتة أدناه كانت تكتب
            // has_league_reward=true وتقلب علم is_relative (مفتاح يخص
            // مسار تسجيل الدخول) لأي مستخدم تجاوزت نقاطه 100. العلم لا
            // يضعه أحد غيرها، وhas_league_reward=true بصمتها وحدها،
            // فتُمسح مع العلم مرة واحدة.
            if (prefs.getBoolean("has_league_reward", false)) {
                prefs.edit()
                    .remove("has_league_reward")
                    .remove("league_reward_expiry_ms")
                    .putBoolean("is_relative", false)
                    .apply()
                Log.d(TAG, "Repaired legacy league-reward flag flip (q61).")
            }

            // إذا لم يكن هناك دوري مسبق (أول مرة يعمل فيها النظام)، نقوم بإنشائه لمدة 7 أيام
            if (endMillis == 0L) {
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.DAY_OF_YEAR, 7)
                prefs.edit().putLong("league_end_date_ms", calendar.timeInMillis).apply()
                return
            }

            // التحقق مما إذا كان الدوري قد انتهى
            if (now.time > endMillis) {
                Log.d(TAG, "League ended! Resetting for new week...")
                val newCalendar = Calendar.getInstance()
                newCalendar.add(Calendar.DAY_OF_YEAR, 7)
                prefs.edit().putLong("league_end_date_ms", newCalendar.timeInMillis).apply()
                // ق61: لا شيء آخر هنا. المنافسة الأسبوعية الحقيقية
                // تُحسب من سجل التدقيق (ISO) في لوحة الشرف؛ أما هذا
                // الفرع فكان يُصفّر إجمالي النقاط المحلية (كل الأوقات
                // لا نقاط الأسبوع) كل 7 أيام من أول تشغيل، فيختفي
                // رصيد المستخدم من ملفه ولوحة الشرف بلا سبب، ويكتب
                // مكافأة ميتة لا قارئ لها (has_league_reward).
            }

            // Score updates are handled by PointsManager through Supabase.

        } catch (e: Exception) {
            Log.w(TAG, "Note on processing weekly league: ${e.message}")
        }
    }
}

