package com.qabas.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PointAction(val points: Int, val description: String) {
    EXPORT_VIDEO(50, "تصدير فيديو نهائي"),
    USE_AI_HOOK(10, "توليد فكرة/خطاف بالذكاء الاصطناعي"),
    SEARCH_DALEEL(5, "البحث عن دليل شرعي بالمساعد الدعوي"),
    SHARE_APP(20, "مشاركة الفيديو والتطبيق"),
    DAILY_LOGIN(5, "دخول يومي"),
    WEEKLY_CHALLENGE(100, "تنفيذ مهمة الأسبوع الطارئة")
}

object PointsManager {
    private const val TAG = "PointsManager"

    fun awardPoints(context: Context, action: PointAction) {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val currentLocal = prefs.getInt("local_user_points", 0)
        val newTotal = currentLocal + action.points
        prefs.edit().putInt("local_user_points", newTotal).apply()
        Log.d(TAG, "Awarded ${action.points} points for ${action.name}. New local total: $newTotal")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = prefs.getString("local_user_id", "") ?: ""
                val userName = prefs.getString("local_user_name", "صانع محتوى") ?: "صانع محتوى"
                if (userId.isNotBlank() && SupabaseServices.isSupabaseAvailable) {
                    SupabaseServices.Database.upsertLeaderboardScore(
                        userId = userId,
                        name = userName,
                        points = newTotal.toLong(),
                        isEligible = true
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Note syncing points with cloud: ${e.message}")
            }
        }
    }
    
    fun checkAndAwardDailyLogin(context: Context) {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = dateFormat.format(Date())
        val lastLoginDate = prefs.getString("last_login_date", "")
        
        if (lastLoginDate != todayStr) {
            prefs.edit().putString("last_login_date", todayStr).apply()
            awardPoints(context, PointAction.DAILY_LOGIN)
            Log.d(TAG, "Awarded daily login points for today: $todayStr")
        }
    }

    data class WeeklyMission(
        val id: String,
        val weekNumber: Int,
        val topic: String,
        val description: String,
        val prompt: String,
        val points: Int = 100
    )

    private val missionsList = listOf(
        WeeklyMission(
            id = "mission_week_1",
            weekNumber = 1,
            topic = "إدمان المظاهر في السوشيال ميديا",
            description = "عالمنا اليوم يغرق في المقارنات المادية، والفتنة بالمظاهر الخادعة. مهمتنا هي تذكير الناس بكنز القناعة والرضا بما قسم الله.",
            prompt = "اصنع سكريبت فيديو قصير مؤثر وقوي عن خطورة فتنة المظاهر والمقارنات القاتلة في السوشيال ميديا، مع الدعوة للقناعة وراحة القلب بالذكر."
        ),
        WeeklyMission(
            id = "mission_week_2",
            weekNumber = 2,
            topic = "بر الوالدين وأثره في تفريج الكروب",
            description = "دعوة الأبناء لإدراك قيمة الوالدين قبل فوات الأوان، وأثر البر في فتح أبواب الرزق والبركة.",
            prompt = "اصنع سكريبت فيديو ريلز دعوي مؤثر ومبكي عن بر الوالدين، وقصص واقعية عن تفريج الهموم ببرهما."
        ),
        WeeklyMission(
            id = "mission_week_3",
            weekNumber = 3,
            topic = "أثر الصدقة الخفية في دفع البلاء",
            description = "إحياء سنة صدقة السر وأثرها العظيم في طمأنينة القلب وإطفاء غضب الرب ودفع مصائب الدهر.",
            prompt = "اصنع سكريبت فيديو دعوي حماسي عن أسرار الصدقة الخفية وبركتها العجيبة في الرزق والشفاء وتفريج الكرب."
        ),
        WeeklyMission(
            id = "mission_week_4",
            weekNumber = 4,
            topic = "الاستغفار: مفتاح الأقفال وتفريج الهموم",
            description = "تسليط الضوء على معجزات لزوم الاستغفار في زيادة القوة، والمال، وراحة البال، ومغفرة الذنوب.",
            prompt = "اصنع سكريبت فيديو دعوي ملهم وسريع عن أثر لزوم الاستغفار بالأسحار وتفريج أصعب الأزمات بالدليل الشرعي."
        )
    )

    fun getCurrentWeeklyMission(): WeeklyMission {
        val calendar = java.util.Calendar.getInstance()
        val weekOfYear = calendar.get(java.util.Calendar.WEEK_OF_YEAR)
        val index = (weekOfYear - 1) % missionsList.size
        return missionsList[index].copy(weekNumber = weekOfYear)
    }

    fun isWeeklyMissionCompleted(context: Context, missionId: String): Boolean {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("completed_$missionId", false)
    }

    fun markWeeklyMissionCompleted(context: Context, missionId: String) {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("completed_$missionId", true).apply()
        awardPoints(context, PointAction.WEEKLY_CHALLENGE)
    }

    fun verifyAndAwardChallenge(context: Context, script: String) {
        // نمنح نقاط التصدير الأساسية أولاً في جميع الأحوال
        awardPoints(context, PointAction.EXPORT_VIDEO)
        
        if (script.isBlank()) return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // التحقق من الذكاء الاصطناعي إذا كان المحتوى يتعلق بفتنة المظاهر والقناعة
                val prompt = """
                    أنت حكم في مسابقة لصناعة المحتوى الإسلامي. مهمة هذا الأسبوع هي: "محاربة فتنة المظاهر والمقارنات في السوشيال ميديا والدعوة للقناعة".
                    اقرأ السكربت التالي، وأجب بكلمة "YES" فقط إذا كان السكربت يخدم هذه المهمة ويعالج هذه الفتنة، أو بكلمة "NO" إذا كان يتحدث عن موضوع آخر.
                    
                    السكربت:
                    $script
                """.trimIndent()
                
                val geminiResponse = AppServices.chatWithAssistant(listOf(Pair(true, prompt)))
                
                if (geminiResponse.contains("YES", ignoreCase = true)) {
                    Log.d(TAG, "AI Verified Challenge Completion! Awarding 100 points.")
                    awardPoints(context, PointAction.WEEKLY_CHALLENGE)
                } else {
                    Log.d(TAG, "AI decided the script does not match the weekly challenge: $geminiResponse")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error verifying challenge with Gemini: ${e.message}")
            }
        }
    }
}
