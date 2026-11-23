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
    EXPORT_VIDEO(50, "إنشاء فيديو"),
    USE_AI_HOOK(10, "توليد فكرة/خطاف بالذكاء الاصطناعي"),
    SEARCH_DALEEL(5, "البحث عن دليل شرعي بالمساعد الدعوي"),
    DAILY_LOGIN(5, "دخول يومي"),
    WEEKLY_CHALLENGE(100, "تنفيذ مهمة الأسبوع الطارئة"),
    SAHIH_QUIZ_CORRECT(2, "إجابة صحيحة في اختبار الصحيحين"),
    SAHIH_QUIZ_PERFECT(50, "علامة كاملة في اختبار الصحيحين (100/100)")
}

object PointsManager {
    private const val TAG = "PointsManager"

    /**
     * نقاط كل مهمة من مهام الورد اليومي الثلاث في KnowledgeHubScreen
     * (قراءة فائدة اليوم، تحدي الفرسان، إنشاء سيناريو) — قيمة واحدة
     * موحّدة يستعملها المنح والعرض معاً، فلا يعود العنوان («×30»)
     * ولا التنبيه («+10») ولا نتيجة الاختبار («+النتيجة») أرقاماً
     * مخترعة لا تطابق المنح الفعلي.
     */
    const val DAILY_TASK_POINTS = 10

    fun awardPoints(context: Context, action: PointAction) {
        awardCustomPoints(context, action.points, action.name)
    }

    /** منح نقاط بمقدار مخصص (لنتائج الاختبارات) مع مزامنة الصدارة */
    fun awardCustomPoints(context: Context, points: Int, reason: String) {
        if (points <= 0) return
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val currentLocal = prefs.getInt("local_user_points", 0)
        val newTotal = currentLocal + points
        prefs.edit().putInt("local_user_points", newTotal).apply()
        Log.d(TAG, "Awarded $points custom points for $reason. New local total: $newTotal")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // السحابة: صف تدقيق في points_ledger + تحديث إجمالي user_points.
                // أفضل جهد ويفشل بصمت عند غياب الجدولين (قبل تطبيق الترحيل).
                // لا نكتب النقاط إطلاقاً في عمود `strikes` القديم في جدول
                // `users` — ذلك العمود إرث متروك ولا يُستخدم للنقاط بعد الآن.
                val userId = SupabaseServices.Auth.getCurrentUserId()
                    ?: prefs.getString("local_user_id", "") ?: ""
                if (userId.isNotBlank() && SupabaseServices.isSupabaseAvailable) {
                    SupabaseServices.Database.recordPointsAward(
                        userId = userId,
                        pointsDelta = points,
                        reason = reason
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
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

    /**
     * هل أُنجزت مهمة الأسبوع الحالية؟ يُخزَّن مفتاح أسبوع إنجازها
     * (سنة×100 + رقم الأسبوع) لا رقم الأسبوع وحده — المعرّف نفسه يعود
     * كل 4 أسابيع، ورقم الأسبوع وحده يتكرر كل سنة فيعيد إنجازاً قديماً
     * لمهمة متجددة (ق26). القيم القديمة (رقم أسبوع 1..53) لا تطابق أي
     * مفتاح سنة فتُعاد المهمة مرة واحدة بعد هذا الإصلاح.
     */
    fun isWeeklyMissionCompleted(context: Context, missionId: String): Boolean {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        return prefs.getInt("completed_week_$missionId", -1) == currentWeekKey()
    }

    /** مفتاح أسبوع الإنجاز: سنة×100 + رقم الأسبوع في السنة. */
    private fun currentWeekKey(): Int {
        val calendar = java.util.Calendar.getInstance()
        return calendar.get(java.util.Calendar.YEAR) * 100 +
            calendar.get(java.util.Calendar.WEEK_OF_YEAR)
    }

    fun verifyAndAwardChallenge(context: Context, script: String) {
        // ق26: بلا سكربت لا نقاط أصلاً — كان منح التصدير (50) يسبق
        // فحص الفراغ فيُمنح على تصدير بلا نص.
        if (script.isBlank()) return
        // نمنح نقاط الإنشاء الأساسية أولاً في جميع الأحوال
        awardPoints(context, PointAction.EXPORT_VIDEO)

        // مهمة الأسبوع الحالية (تدور بين 4 مواضيع) — كان التوجيه
        // مثبّتاً على موضوع الأسبوع الأول («فتنة المظاهر») فتُرفض
        // سكربتات الأسابيع 2–4 المطابقة لمواضيعها فعلاً.
        val mission = getCurrentWeeklyMission()
        // ق169: مكافأة المهمة (100) مرة واحدة في الأسبوع — كان كل
        // تصدير مطابق يمنحها من جديد بلا قيد، فتُحصد بتكرار التصدير.
        // نقاط الإنشاء (50) أعلاه تبقى لكل تصدير؛ القيد هنا لمكافأة
        // المهمة وحدها، ويُعاد فحصه بعد التحقق داخل القفل أدناه
        // لتصديرين متزامنين.
        if (isWeeklyMissionCompleted(context, mission.id)) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // التحقق من الذكاء الاصطناعي إذا كان المحتوى يتعلق بموضوع مهمة هذا الأسبوع
                val prompt = """
                    أنت حكم في مسابقة لصناعة المحتوى الإسلامي. مهمة هذا الأسبوع هي: "${mission.topic}" — ${mission.description}.
                    اقرأ السكربت التالي، وأجب بكلمة "YES" فقط إذا كان السكربت يخدم هذه المهمة ويعالج موضوعها، أو بكلمة "NO" إذا كان يتحدث عن موضوع آخر.

                    السكربت:
                    $script
                """.trimIndent()

                val geminiResponse = AppServices.chatWithAssistant(listOf(Pair(true, prompt)))

                // «YES» حرفية وحدها بعد قصّ الفراغ وعلامة الترقيم —
                // contains كان يقبل أي رد يحوي الكلمة عرضاً في شرح أو نفي.
                val verdictYes = geminiResponse.trim().trimEnd('.', '!', '؟')
                    .equals("YES", ignoreCase = true)
                if (verdictYes) {
                    // ق169: الفحص والمنح والتوثيق ذرّياً داخل قفل واحد —
                    // تصديران متزامنان قد يجتازا فحص ما قبل الإطلاق معاً؛
                    // الثاني يجد المهمة منجزة هنا فلا تُمنح مرتين.
                    synchronized(PointsManager) {
                        if (isWeeklyMissionCompleted(context, mission.id)) {
                            Log.d(TAG, "Weekly challenge already completed this week; skipping duplicate award.")
                        } else {
                            Log.d(TAG, "AI Verified Challenge Completion! Awarding 100 points.")
                            awardPoints(context, PointAction.WEEKLY_CHALLENGE)
                            // توثيق الإنجاز لأسبوعه: كان الإنجاز يُمنح بلا حفظ
                            // إطلاقاً فلا تظهر المهمة منجزة أبداً في لوحة الشرف.
                            context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE).edit()
                                .putInt("completed_week_${mission.id}", currentWeekKey())
                                .apply()
                        }
                    }
                } else {
                    Log.d(TAG, "AI decided the script does not match the weekly challenge: $geminiResponse")
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                Log.e(TAG, "Error verifying challenge with Gemini: ${e.message}")
            }
        }
    }
}
