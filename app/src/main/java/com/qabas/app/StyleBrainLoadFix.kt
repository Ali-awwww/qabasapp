package com.qabas.app

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * إصلاحات العقل:
 * 1) القوة لا تُفرض ≥50 عندما السمات فارغة (كانت تظهر 100/50 بسبب RTL).
 * 2) تصفير كامل للعقل والأنماط.
 * 3) حساب تقدم ضئيل: 0..3 حسب قوة الأسلوب الممتص.
 */
object StyleBrainLoadFix {

    fun normalizeCore(core: QabasCoreStyle): QabasCoreStyle {
        val emptyTraits = core.visualTraits.isEmpty() &&
            core.motionTraits.isEmpty() &&
            core.textTraits.isEmpty()
        return if (emptyTraits) {
            QabasCoreStyle(
                visualTraits = emptyList(),
                motionTraits = emptyList(),
                textTraits = emptyList(),
                analysis = "عقل فارغ — يرجى إضافة فيديوهات مرجعية للتعلم والاستفادة",
                strengthScore = 0,
                lastUpdated = core.lastUpdated
            )
        } else {
            core.copy(strengthScore = core.strengthScore.coerceIn(0, 100))
        }
    }

    /** يصحّح الـ prefs إن كانت القوة مخزّنة خطأً ≥50 مع سمات فارغة */
    fun repairPersistedEmptyCore(context: Context) {
        try {
            val prefs = context.getSharedPreferences("qabas_style_brain_prefs", Context.MODE_PRIVATE)
            val coreJson = prefs.getString("qabas_core_style_json", null) ?: return
            val obj = JSONObject(coreJson)
            val vArr = obj.optJSONArray("visualTraits") ?: JSONArray()
            val mArr = obj.optJSONArray("motionTraits") ?: JSONArray()
            val tArr = obj.optJSONArray("textTraits") ?: JSONArray()
            val storedStrength = obj.optInt("strengthScore", 0)
            if (vArr.length() == 0 && mArr.length() == 0 && tArr.length() == 0) {
                val fixed = JSONObject()
                fixed.put("visualTraits", JSONArray())
                fixed.put("motionTraits", JSONArray())
                fixed.put("textTraits", JSONArray())
                fixed.put("analysis", "عقل فارغ — يرجى إضافة فيديوهات مرجعية للتعلم والاستفادة")
                fixed.put("strengthScore", 0)
                fixed.put("lastUpdated", System.currentTimeMillis())
                prefs.edit().putString("qabas_core_style_json", fixed.toString()).apply()
                StyleBrain.init(context)
                Log.d("StyleBrainLoadFix", "Repaired empty core (was strength=$storedStrength)")
            } else if (storedStrength < 0 || storedStrength > 100) {
                obj.put("strengthScore", storedStrength.coerceIn(0, 100))
                prefs.edit().putString("qabas_core_style_json", obj.toString()).apply()
                StyleBrain.init(context)
            }
        } catch (e: Exception) {
            Log.w("StyleBrainLoadFix", "repair failed: ${e.message}")
        }
    }

    /**
     * تصفير نهائي كامل: يمسح كل الأنماط الممتصة + العقل التراكمي إلى 0.
     */
    fun hardResetBrain(context: Context) {
        try {
            val prefs = context.getSharedPreferences("qabas_style_brain_prefs", Context.MODE_PRIVATE)
            val emptyCore = JSONObject().apply {
                put("visualTraits", JSONArray())
                put("motionTraits", JSONArray())
                put("textTraits", JSONArray())
                put("analysis", "عقل فارغ — يرجى إضافة فيديوهات مرجعية للتعلم والاستفادة")
                put("strengthScore", 0)
                put("lastUpdated", System.currentTimeMillis())
            }
            prefs.edit()
                .putString("qabas_core_style_json", emptyCore.toString())
                .putString("absorbed_styles_list", "[]")
                .remove("qabas_primary_studio_style_id")
                .apply()
            StyleBrain.init(context)
            Log.d("StyleBrainLoadFix", "Hard reset completed — strength=0, styles=0")
        } catch (e: Exception) {
            Log.e("StyleBrainLoadFix", "hardReset failed: ${e.message}", e)
        }
    }

    /**
     * حساب الزيادة الضئيلة في قوة العقل حسب قوة الأسلوب:
     * overallScore 95–100 → +3
     * overallScore 90–94  → +2
     * overallScore 80–89  → +1
     * أقل من 80          → +0
     * أول امتصاص (من 0)  → نفس القاعدة (لا يقفز إلى 80)
     */
    fun computeStrengthGain(overallScore: Int, currentStrength: Int): Int {
        if (overallScore < 80) return 0
        val gain = when {
            overallScore >= 95 -> 3
            overallScore >= 90 -> 2
            overallScore >= 80 -> 1
            else -> 0
        }
        return gain.coerceIn(0, 3).coerceAtMost(100 - currentStrength)
    }
}
