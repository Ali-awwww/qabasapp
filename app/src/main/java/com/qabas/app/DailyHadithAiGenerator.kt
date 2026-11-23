package com.qabas.app

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

object DailyHadithAiGenerator {
    private const val TAG = "DailyHadithAiGen"

    /**
     * نصوص الأحاديث ورواتها ومصادرها من المضمّن الموثّق وحده
     * (SahihHadithsRepository) — النموذج لا يُسأل عن نص حديث أبداً.
     * الصيغة السابقة كانت تطلب من النموذج «توليد» النص الكامل مع
     * الراوي والمصدر، فيخترع النموذج ألفاظاً تُحفظ في الذاكرة المحلية
     * وتُرسَل للمستخدمين إشعاراً منسوبةً للنبي ﷺ («صحيح البخاري») —
     * نص شرعي مختلَق. دور النموذج الوحيد الآن: فائدة عملية قصيرة
     * (شرح لا نص) لكل حديث موثّق؛ وعند فشل فائدة يوم يُستعمل
     * wisdomCapsule المحلي المنسّق، لا نص مولَّد.
     */
    private val benefitSchema = Schema.obj(
        properties = mapOf(
            "benefits" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "dayId" to Schema.integer(description = "Day of the year (1-365)"),
                        "benefit" to Schema.string(description = "A very short practical benefit or wisdom in Arabic, one sentence")
                    )
                )
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = benefitSchema
            }
        )
    }

    suspend fun generateBatch(startDay: Int, count: Int): List<DailyHadith> {
        val bundled = SahihHadithsRepository.getAllHadiths()
        if (bundled.isEmpty()) return emptyList()
        // نفس تعيين اليوم→حديث المستعمل في DailyHadithRepository
        // (احتياطي الأحاديث المضمّنة)، فيطابق الإشعار اليومي.
        val days = (startDay until startDay + count).map { dayId ->
            val normalizedDay = ((dayId - 1).coerceAtLeast(0) % 365) + 1
            normalizedDay to bundled[(normalizedDay - 1) % bundled.size]
        }

        val benefits: Map<Int, String> = try {
            val prompt = buildString {
                append("For each numbered Hadith below, write ONE very short practical benefit or wisdom in Arabic (one sentence, no Hadith text, no narrator). Return JSON only.\n")
                days.forEach { (dayId, item) ->
                    append("Day $dayId: ${item.matn.take(400)}\n")
                }
            }
            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }
            // بلا نص من النموذج فلا فوائد — يُستعمل البديل المحلي في
            // الأسفل. (العودة من try بتسميته غير صالحة في Kotlin:
            // try كلمة محجوزة لا تصلح تسميةً، فكان السطر القديم
            // يكسر إعلان jsonString كله ويُسقط معه benefits وcatch.)
            val jsonString = response.text
            val map: Map<Int, String> = if (jsonString == null) {
                emptyMap()
            } else {
                val json = JSONObject(jsonString)
                val arr = json.getJSONArray("benefits")
                val requested = days.map { it.first }.toSet()
                val built = mutableMapOf<Int, String>()
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val dayId = item.getInt("dayId")
                    if (dayId in requested) {
                        built[dayId] = item.getString("benefit")
                    }
                }
                // ق160: توليد فوائد الأحاديث (Gemini 3.8 عبر Firebase) كان يمر
                // بلا أثر في تتبع التكلفة — والسياق AppServices.appContext
                // كمسار Groq (التسجيل runCatching فلا يسقط التوليد أبداً).
                runCatching {
                    VibeCostTracker.recordEstimated(AppServices.appContext, "gemini", "gemini-3.8-flash", prompt, jsonString, "فوائد الأحاديث اليومية")
                }
                built
            }
            map
        } catch (e: Exception) {
            // الإلغاء الخارجي ليس فشل توليد ولا يُبتلَع (نمط الجولة 87): أما
            // مهلة withTimeout نفسها (TimeoutCancellationException) فتبقى
            // فشلاً عادياً يعيد فوائد فارغة فيُستعمل البديل المحلي — عقدة
            // هذه الدالة.
            if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
            Log.e(TAG, "Benefit generation failed for days $startDay to ${startDay + count - 1}", e)
            emptyMap()
        }

        return days.map { (dayId, item) ->
            DailyHadith(
                dayId = dayId,
                text = item.matn,
                narrator = item.narrator,
                benefit = benefits[dayId]?.takeIf { it.isNotBlank() }
                    ?: item.wisdomCapsule.ifBlank { item.explanation.take(160) },
                book = item.bookType.titleAr,
                title = item.title
            )
        }
    }
}
