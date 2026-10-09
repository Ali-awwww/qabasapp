package com.qabas.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * طبقة البيانات المركزية لربط 'العقل' (Qabas Brain) بقاعدة بيانات Firestore
 * مسؤولة عن استرجاع كائنات الأنماط (Style Objects) المحفوظة،
 * تحويلها لذاكرة مرجعية ديناميكية (Dynamic Few-Shot Reference Memory)،
 * وحقنها في ردود وقرارات الذكاء الاصطناعي (Gemini).
 */
class QabasBrainRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("qabas_brain_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "QabasBrainRepository"

        @Volatile
        private var INSTANCE: QabasBrainRepository? = null

        fun getInstance(context: Context): QabasBrainRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: QabasBrainRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * استرجاع كائنات الأنماط المخزنة للمستخدم والمجتمع من Firestore
     * مع التدهور الأنيق للذاكرة التراكمية المحلية إذا لم تتوفر السحابة
     */
    suspend fun getReferenceStyleObjects(userId: String? = null): List<StyleObject> = withContext(Dispatchers.IO) {
        val cloudStyles = if (CloudServices.isFirebaseInitialized) {
            try {
                CloudServices.Database.getCloudStyleObjects(userId)
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching from Firestore, falling back to local: ${e.message}")
                emptyList()
            }
        } else {
            emptyList()
        }

        if (cloudStyles.isNotEmpty()) {
            return@withContext cloudStyles
        }

        // استخدام الأنماط المخزنة في ذاكرة StyleBrain المحلية كاحتياطي موثوق
        val localAbsorbed = StyleBrain.absorbedStyles.value
        return@withContext localAbsorbed.map { StyleObject.fromAbsorbedStyle(it) }
    }

    /**
     * تدفق مراقبة حي لكائنات الأنماط من Firestore
     */
    fun observeStyleObjects(): Flow<List<StyleObject>> {
        return CloudServices.Database.observeCloudStyleObjects().flowOn(Dispatchers.IO)
    }

    /**
     * حفظ كائن نمط فني في سحابة Firestore وتحديث الذاكرة التراكمية للعقل
     */
    suspend fun saveStyleObject(styleObject: StyleObject): Boolean = withContext(Dispatchers.IO) {
        var cloudSaved = false
        if (CloudServices.isFirebaseInitialized) {
            cloudSaved = CloudServices.Database.saveStyleObjectToCloud(styleObject)
        }
        
        // حفظ في الذاكرة التراكمية وتطوير العقل
        StyleBrain.absorbStyleObject(context, styleObject)
        return@withContext cloudSaved || StyleBrain.absorbedStyles.value.isNotEmpty()
    }

    /**
     * صياغة ذاكرة مرجعية ديناميكية (Dynamic Few-Shot Reference Memory Context)
     * تستخرج الأنماط الأعلى تقييماً والأقرب للمحتوى المطلوب لحقنها في الـ Prompt الموجه إلى Gemini
     */
    suspend fun buildFewShotStyleMemory(
        targetIdea: String,
        targetTone: String,
        limit: Int = 3
    ): String = withContext(Dispatchers.IO) {
        val allStyles = getReferenceStyleObjects()
        if (allStyles.isEmpty()) {
            return@withContext """
                [الذاكرة المرجعية الأساسية لهوية قبس]:
                - النمط الأساسي: أسلوب وثائقي سينمائي وقور
                - الألوان المسيطرة: خلفية داكنة فاخرة Deep Slate (#0B0F19)، لمسات ذهبية متوهجة Gold Primary (#E8C547)، نصوص ناصعة (#F8FAFC)
                - إيقاع الحركة: زووم بطيء متصاعد (Slow Cinematic Zoom-in 1.05x)، انتقالات تلاشي ناعمة (Smooth Dissolve)
                - التيبوغرافيا: خط كوفي عريض في المنطقة الآمنة، ظهور كلمة بكلمة مع تمييز الكلمات المفتاحية بالذهبي
                - النبرة الدعوية: روحانية ملهمة، وقورة، موثوقة ومحفزة للتأمل
            """.trimIndent()
        }

        // ترتيب الأنماط بناءً على التوافق الدلالي والتقييم الإجمالي
        val rankedStyles = allStyles.sortedWith(
            compareByDescending<StyleObject> { style ->
                var relevance = 0
                if (style.contentTone.tone.contains(targetTone, ignoreCase = true)) relevance += 20
                if (targetIdea.isNotBlank() && style.tags.any { tag -> targetIdea.contains(tag, ignoreCase = true) }) relevance += 30
                if (style.visualStyle.visualTraits.any { vt -> vt.contains("ذهب") || vt.contains("داكن") }) relevance += 15
                relevance + style.overallScore
            }
        ).take(limit)

        val memoryBuilder = StringBuilder()
        memoryBuilder.append("=== ذاكرة العقل الإخراجي لكائنات الأنماط المحفوظة في Firestore (Reference Style Memory) ===\n")
        memoryBuilder.append("استخدم هذه الأنماط المرجعية كقواعد صارمة لتوجيه القرارات الإخراجية وتشكيل السيناريو والفلاتر:\n\n")

        rankedStyles.forEachIndexed { index, style ->
            memoryBuilder.append("【النمط المرجعي ${index + 1}: ${style.name} (تقييم العقل: ${style.overallScore}/100)】\n")
            memoryBuilder.append("- النبرة والهدف: ${style.contentTone.tone} | الجمهور: ${style.contentTone.targetAudience}\n")
            memoryBuilder.append("- الهوية البصرية: ألوان [${style.visualStyle.dominantColors}]، لون رئيسي: ${style.visualStyle.primaryColorHex}، خلفية: ${style.visualStyle.backgroundColorHex}\n")
            memoryBuilder.append("- السمات البصرية: ${style.visualStyle.visualTraits.joinToString("، ")}\n")
            memoryBuilder.append("- إيقاع المونتاج: ${style.motionRhythm.overallRhythm} (سرعة: ${style.motionRhythm.transitionSpeed}، انتقال: ${style.motionRhythm.transitionType})\n")
            memoryBuilder.append("- التيبوغرافيا والكابشن: ${style.contentTone.typographyStyle}، تحريك: ${style.contentTone.captionAnimation}\n")
            if (style.analysisSummary.isNotBlank()) {
                memoryBuilder.append("- التوجيه الإخراجي: ${style.analysisSummary}\n")
            }
            memoryBuilder.append("\n")
        }

        return@withContext memoryBuilder.toString().trim()
    }

    /**
     * تحديث أوزان النمط في Firestore بناءً على تغذية المستخدم الراجعة (Reinforcement)
     */
    suspend fun recordStyleFeedback(styleNameOrId: String, isPositive: Boolean): Unit = withContext(Dispatchers.IO) {
        try {
            val allStyles = getReferenceStyleObjects()
            val target = allStyles.find { it.id == styleNameOrId || it.name.equals(styleNameOrId, ignoreCase = true) }
            if (target != null) {
                val updatedScore = if (isPositive) {
                    (target.overallScore + 2).coerceIn(0, 100)
                } else {
                    (target.overallScore - 3).coerceIn(0, 100)
                }
                val updatedStyle = target.copy(overallScore = updatedScore)
                saveStyleObject(updatedStyle)
                StyleBrain.recordUserFeedbackOnStyle(context, target.name, isPositive, updatedStyle)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error recording style feedback in repository: ${e.message}")
        }
    }

    /**
     * حفظ عنصر جديد في سجل قرارات العقل
     */
    suspend fun saveDecisionHistoryItem(item: BrainDecisionHistoryItem): Unit = withContext(Dispatchers.IO) {
        try {
            val currentList = getDecisionHistory().toMutableList()
            // إزالة التكرار إن وجد
            currentList.removeAll { it.id == item.id }
            currentList.add(0, item) // إضافة في المقدمة

            val jsonArr = org.json.JSONArray()
            // الاحتفاظ بأحدث 50 قراراً فقط لضمان كفاءة التخزين والذاكرة
            currentList.take(50).forEach {
                jsonArr.put(it.toJsonObject())
            }

            prefs.edit().putString("qabas_brain_decision_history_json", jsonArr.toString()).apply()
            Log.d(TAG, "Saved decision history item: ${item.decision.title}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving decision history: ${e.message}", e)
        }
    }

    /**
     * جلب كافة السجلات المحفوظة لقرارات العقل
     */
    suspend fun getDecisionHistory(): List<BrainDecisionHistoryItem> = withContext(Dispatchers.IO) {
        try {
            val savedJson = prefs.getString("qabas_brain_decision_history_json", null)
            if (!savedJson.isNullOrBlank()) {
                val jsonArr = org.json.JSONArray(savedJson)
                val items = mutableListOf<BrainDecisionHistoryItem>()
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    items.add(BrainDecisionHistoryItem.fromJsonObject(obj))
                }
                return@withContext items
            }

            // العقل يبدأ فارغاً ومحايداً تماماً — لا سجلات وهمية من أي نوع.
            // يُبنى السجل حصرياً من قرارات حقيقية تُحفظ عبر saveDecisionHistoryItem.
            return@withContext emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading decision history: ${e.message}", e)
            return@withContext emptyList()
        }
    }

    /**
     * حذف عنصر محدد من السجل
     */
    suspend fun deleteDecisionHistoryItem(id: String): Unit = withContext(Dispatchers.IO) {
        try {
            val currentList = getDecisionHistory().filter { it.id != id }
            val jsonArr = org.json.JSONArray()
            currentList.forEach { jsonArr.put(it.toJsonObject()) }
            prefs.edit().putString("qabas_brain_decision_history_json", jsonArr.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting decision history item: ${e.message}")
        }
    }

    /**
     * مسح كافة عناصر السجل
     */
    suspend fun clearAllDecisionHistory(): Unit = withContext(Dispatchers.IO) {
        prefs.edit().remove("qabas_brain_decision_history_json").apply()
    }

    /**
     * تحديث تقييم المستخدم للقرار وتعزيز كائن النمط المرتبط
     */
    suspend fun updateDecisionFeedback(id: String, isPositive: Boolean): Unit = withContext(Dispatchers.IO) {
        try {
            val currentList = getDecisionHistory().toMutableList()
            val index = currentList.indexOfFirst { it.id == id }
            if (index != -1) {
                val oldItem = currentList[index]
                val updatedItem = oldItem.copy(userFeedback = isPositive)
                currentList[index] = updatedItem

                val jsonArr = org.json.JSONArray()
                currentList.forEach { jsonArr.put(it.toJsonObject()) }
                prefs.edit().putString("qabas_brain_decision_history_json", jsonArr.toString()).apply()

                // تعزيز كائن النمط المرتبط
                val styleToReinforce = oldItem.influencingStyleObject?.name ?: oldItem.decision.selectedStyleName
                if (styleToReinforce.isNotBlank()) {
                    recordStyleFeedback(styleToReinforce, isPositive)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating decision feedback: ${e.message}")
        }
    }

}
