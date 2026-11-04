package com.qabas.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.floor

/**
 * MuslimToolsEngine: The brain for Islamic productivity tools in Qabas.
 * Handles computational logic and local persistence for 15+ Islamic tools.
 */
class MuslimToolsEngine(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("muslim_tools_prefs", Context.MODE_PRIVATE)

    // --- Zakat Calculator Logic ---
    @Serializable
    data class ZakatResult(
        val totalWealth: Double,
        val zakatAmount: Double,
        val isNisabReached: Boolean,
        val nisabValueGold: Double
    )

    fun calculateZakat(
        cash: Double,
        goldGrams: Double,
        goldCarat: Int,
        silverGrams: Double,
        tradeGoods: Double,
        goldPricePerGram24k: Double = 300.0
    ): ZakatResult {
        val nisabGold = 85.0 * goldPricePerGram24k
        val goldWeight24k = when (goldCarat) {
            24 -> goldGrams
            22 -> goldGrams * (22.0 / 24.0)
            21 -> goldGrams * (21.0 / 24.0)
            18 -> goldGrams * (18.0 / 24.0)
            else -> goldGrams
        }
        val totalWealth = cash + (goldWeight24k * goldPricePerGram24k) + (silverGrams * (goldPricePerGram24k / 70.0)) + tradeGoods
        val isNisabReached = totalWealth >= nisabGold
        val zakatAmount = if (isNisabReached) totalWealth * 0.025 else 0.0
        return ZakatResult(totalWealth, zakatAmount, isNisabReached, nisabGold)
    }

    // --- Fasting Tracker Logic ---
    fun getMissedFastingDays(): Int = prefs.getInt("missed_fasting_total", 0).coerceAtLeast(0)
    fun getCompletedFastingDays(): Int =
        prefs.getInt("missed_fasting_completed", 0).coerceIn(0, getMissedFastingDays())
    fun setMissedFastingDays(days: Int) {
        val safeTotal = days.coerceAtLeast(0)
        val safeCompleted = getCompletedFastingDays().coerceAtMost(safeTotal)
        prefs.edit()
            .putInt("missed_fasting_total", safeTotal)
            .putInt("missed_fasting_completed", safeCompleted)
            .apply()
    }
    fun incrementCompletedFastingDays(): Boolean {
        val total = getMissedFastingDays()
        val completed = getCompletedFastingDays()
        if (completed < total) {
            prefs.edit().putInt("missed_fasting_completed", completed + 1).apply()
            return true
        }
        return false
    }
    fun resetFastingTracker() {
        prefs.edit().putInt("missed_fasting_total", 0).putInt("missed_fasting_completed", 0).apply()
    }

    // --- Quran Khatma Planner Logic ---
    @Serializable
    data class KhatmaPlan(val days: Int, val pagesPerDay: Double, val portionsPerDay: String)
    fun generateKhatmaPlan(days: Int): KhatmaPlan {
        val safeDays = days.coerceAtLeast(1)
        val totalPages = 604.0
        val pagesPerDay = totalPages / safeDays
        val completeJuz = (pagesPerDay / 20.0).toInt()
        val remainingPages = pagesPerDay - (completeJuz * 20.0)
        val portions = when {
            completeJuz > 0 && remainingPages >= 0.05 ->
                "$completeJuz جزء و ${String.format(Locale.US, "%.1f", remainingPages)} صفحة"
            completeJuz > 0 -> "$completeJuz جزء"
            else -> "${String.format(Locale.US, "%.1f", pagesPerDay)} صفحة"
        }
        return KhatmaPlan(safeDays, pagesPerDay, portions)
    }

    // --- Smart Tasbeeh Logic ---
    fun getTasbeehCount(type: String): Int = prefs.getInt("tasbeeh_$type", 0)
    fun incrementTasbeeh(type: String) {
        prefs.edit().putInt("tasbeeh_$type", getTasbeehCount(type) + 1).apply()
        prefs.edit().putInt("total_tasbeeh", prefs.getInt("total_tasbeeh", 0) + 1).apply()
    }
    fun resetTasbeeh(type: String) { prefs.edit().putInt("tasbeeh_$type", 0).apply() }

    // --- Habits Tracker Logic ---
    @Serializable
    data class HabitItem(val id: String, val name: String, val lastDoneDate: String = "")
    fun getHabits(): List<HabitItem> {
        val raw = prefs.getString("habits_list", null) ?: return listOf(
            HabitItem("1", "قراءة ورد القرآن"),
            HabitItem("2", "أذكار الصباح والمساء"),
            HabitItem("3", "صلاة الضحى"),
            HabitItem("4", "صلاة الوتر"),
            HabitItem("5", "قيام الليل")
        )
        return try { Json.decodeFromString(raw) } catch (e: Exception) { emptyList() }
    }
    fun toggleHabit(habitId: String) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val currentHabits = getHabits()
        if (currentHabits.none { it.id == habitId }) return
        val wasDoneToday = currentHabits.any { it.id == habitId && it.lastDoneDate == today }
        val updatedHabits = currentHabits.map { habit ->
            if (habit.id == habitId) {
                if (wasDoneToday) habit.copy(lastDoneDate = "") else habit.copy(lastDoneDate = today)
            } else {
                habit
            }
        }

        val rewardDateKey = "habit_reward_date_$habitId"
        val editor = prefs.edit().putString("habits_list", Json.encodeToString(updatedHabits))

        if (wasDoneToday) {
            // Preserve an already-earned reward when the user unticks the habit.
            // This also prevents legacy completions from being rewarded again after migration.
            editor.putString(rewardDateKey, today)
        } else if (prefs.getString(rewardDateKey, null) != today) {
            editor.putString(rewardDateKey, today)
            editor.putLong(
                "total_hasanat_earned",
                prefs.getLong("total_hasanat_earned", 0L) + 50L
            )
        }
        editor.apply()
    }

    // --- Hijri Calendar Logic ---
    fun getHijriDate(timeMillis: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timeMillis }
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)
        var m = month
        var y = year
        if (m < 3) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        val jd = floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
        val ijd = floor(jd + 0.5)
        val l = ijd - 1948440 + 10632
        val n = floor((l - 1) / 10631.0)
        val lPrime = l - 10631 * n + 354
        val j = (floor((10985 - lPrime) / 5316.0)) * (floor((50 * lPrime) / 17719.0)) + (floor(lPrime / 5670.0)) * (floor((43 * lPrime) / 15238.0))
        val lDoublePrime = lPrime - (floor((30 - j) / 15.0)) * (floor((17719 * j) / 50.0)) - (floor(j / 16.0)) * (floor((15238 * j) / 43.0)) + 29
        val hMonth = floor((24 * lDoublePrime) / 709.0).toInt()
        val hDay = (lDoublePrime - floor((709 * hMonth) / 24.0)).toInt()
        val hYear = (30 * n + j - 30).toInt()
        val hijriMonthsAr = listOf("محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة")
        return "$hDay ${hijriMonthsAr.getOrElse(hMonth - 1) { "رمضان" }} $hYear هـ"
    }

    // --- Islamic Events ---
    @Serializable
    data class IslamicEvent(val name: String, val daysRemaining: Int, val dateHijri: String)

    fun getUpcomingEvents(nowMillis: Long = System.currentTimeMillis()): List<IslamicEvent> {
        val targets = listOf(
            Triple("شهر رمضان المبارك", 1, "رمضان"),
            Triple("عيد الفطر السعيد", 1, "شوال"),
            Triple("يوم عرفة", 9, "ذو الحجة"),
            Triple("عيد الأضحى المبارك", 10, "ذو الحجة")
        )
        val start = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return targets.mapNotNull { (name, targetDay, targetMonth) ->
            val offset = (0..370).firstOrNull { dayOffset ->
                val candidate = (start.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, dayOffset)
                }
                getHijriDate(candidate.timeInMillis).startsWith("$targetDay $targetMonth ")
            } ?: return@mapNotNull null

            IslamicEvent(name, offset, "$targetDay $targetMonth")
        }.sortedBy { it.daysRemaining }
    }

    // --- Worship Stats ---
    data class WorshipStats(val totalTasbeeh: Int, val fastingProgress: Float, val khatmaProgress: Float, val totalHasanat: Long)
    fun getWorshipStats(): WorshipStats {
        val totalTasbeeh = prefs.getInt("total_tasbeeh", 0)
        val fTotal = getMissedFastingDays()
        val fDone = getCompletedFastingDays()
        val fProgress = if (fTotal > 0) (fDone.toFloat() / fTotal).coerceIn(0f, 1f) else 0f
        // No khatma reading-progress source is connected yet; report neutral progress, not a fabricated 45%.
        return WorshipStats(totalTasbeeh, fProgress, 0f, prefs.getLong("total_hasanat_earned", 0))
    }
}
