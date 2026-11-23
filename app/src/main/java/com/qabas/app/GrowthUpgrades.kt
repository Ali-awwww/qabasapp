package com.qabas.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * حزم النمو 2026: ستريك يومي + محرك خطافات + حزمة نشر.
 * مبنية على: RevenueCat 2026 (freemium + سنوي + أسعار منخفضة) ومعمارية Duolingo للستريك،
 * وخوارزمية Shorts 2026 (عتبة 65%، الذهبي 30-45ث، القرار 1.3ث، الحلقة loop، 70% صامت، دعوة تعليق، 3 هاشتاقات).
 */
object StreakManager {
    private const val PREFS = "qabas_prefs"
    private const val KEY_COUNT = "streak_count"
    private const val KEY_LAST = "streak_last_date"
    private const val KEY_FREEZES = "streak_freezes"

    private fun day(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun getStreak(context: Context): Int {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = p.getString(KEY_LAST, "") ?: ""
        val count = p.getInt(KEY_COUNT, 0)
        if (count == 0) return 0
        // انقطاع يوم واحد = تجميد تلقائي إن وجد، وإلا تصفير
        val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis() - 86400000L))
        if (last == day() || last == yesterday) return count
        val freezes = p.getInt(KEY_FREEZES, 1)
        return if (freezes > 0) {
            p.edit().putInt(KEY_FREEZES, freezes - 1).putString(KEY_LAST, yesterday).apply()
            count
        } else 0
    }

    fun recordProduction(context: Context): Int {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = p.getString(KEY_LAST, "") ?: ""
        var count = p.getInt(KEY_COUNT, 0)
        if (last == day()) return count
        val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis() - 86400000L))
        count = if (last == yesterday) count + 1 else 1
        // مكافأة التجميد كل 7 أيام متتالية — على طريقة Duolingo
        var freezes = p.getInt(KEY_FREEZES, 1)
        if (count % 7 == 0) freezes++
        p.edit().putInt(KEY_COUNT, count).putString(KEY_LAST, day()).putInt(KEY_FREEZES, freezes).apply()
        return count
    }

}

object HookEngine {
    data class HookPack(val hooks: List<String>, val loopEnding: String, val commentCta: String)

    fun localPack(idea: String): HookPack {
        val short = idea.take(60)
        return HookPack(
            hooks = listOf(
                "هل تعلم أن $short؟",
                "كل الناس مخطئة في فهم $short — إليك الحقيقة",
                "قف لحظة: $short قد يغير يومك"
            ),
            loopEnding = "والآن أعد المشاهدة: ما أول كلمة خطفتك؟",
            commentCta = "اكتب كلمة واحدة وصفت شعورك 👇"
        )
    }

}

object SharePack {
    const val HASHTAGS_3 = "#خادم_المسلم #ريلز_إسلامي #تدبر"

}
