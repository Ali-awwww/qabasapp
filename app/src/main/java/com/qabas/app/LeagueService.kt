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

                // التحقق من تصدر المستخدم الحالي ومكافأته
                val userPoints = prefs.getInt("local_user_points", 0)
                if (userPoints > 100) {
                    val rewardExpiry = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }.timeInMillis
                    prefs.edit()
                        .putBoolean("has_league_reward", true)
                        .putBoolean("is_relative", true)
                        .putLong("league_reward_expiry_ms", rewardExpiry)
                        .putInt("local_user_points", 0)
                        .apply()
                }
            }

            // فحص صلاحية المكافأة
            checkCurrentUserReward(context)

            // مزامنة الدوري مع Firebase Firestore بالتوازي
            if (FirebaseManager.isConfigured(context)) {
                val userId = LocalAccountRegistry.getEffectiveUserId(context)
                val userPoints = prefs.getInt("local_user_points", 0)
                val userName = prefs.getString("user_name", "مبدع قبس") ?: "مبدع قبس"
                FirebaseManager.syncLeaderboardToFirestore(
                    context = context,
                    userId = userId,
                    name = userName,
                    points = userPoints,
                    rank = 1,
                    badge = "صانع محتوى"
                )
            }

        } catch (e: Exception) {
            Log.w(TAG, "Note on processing weekly league: ${e.message}")
        }
    }

    private fun checkCurrentUserReward(context: Context) {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val hasReward = prefs.getBoolean("has_league_reward", false)
        val expiryMs = prefs.getLong("league_reward_expiry_ms", 0L)
        val now = System.currentTimeMillis()

        if (hasReward && expiryMs > 0L) {
            if (now < expiryMs) {
                Log.d(TAG, "User has active league reward until $expiryMs")
            } else {
                Log.d(TAG, "User league reward expired.")
                val isActuallyRelative = prefs.getBoolean("is_relative_permanent", false)
                prefs.edit()
                    .putBoolean("has_league_reward", false)
                    .putBoolean("is_relative", isActuallyRelative)
                    .apply()
            }
        }
    }
}

