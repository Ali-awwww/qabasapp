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

    enum class CoreRepairStatus {
        NO_STORED_CORE,
        NO_REPAIR_NEEDED,
        REPAIRED,
        INVALID_STORED_DATA,
        PERSISTENCE_FAILED,
        PERSISTED_RELOAD_FAILED,
        FAILED
    }

    data class CoreRepairResult(
        val status: CoreRepairStatus,
        val detail: String? = null
    )

    /**
     * Repairs only recognized persisted-core inconsistencies and reports the
     * actual persistence/reload outcome. Unknown or incomplete JSON is left intact.
     */
    fun repairPersistedEmptyCore(context: Context): CoreRepairResult {
        val prefs = context.getSharedPreferences("qabas_style_brain_prefs", Context.MODE_PRIVATE)
        return try {
            val coreJson = prefs.getString("qabas_core_style_json", null)
                ?: return CoreRepairResult(CoreRepairStatus.NO_STORED_CORE)
            val obj = JSONObject(coreJson)

            val visualTraits = obj.optJSONArray("visualTraits")
                ?: return CoreRepairResult(CoreRepairStatus.INVALID_STORED_DATA, "visualTraits")
            val motionTraits = obj.optJSONArray("motionTraits")
                ?: return CoreRepairResult(CoreRepairStatus.INVALID_STORED_DATA, "motionTraits")
            val textTraits = obj.optJSONArray("textTraits")
                ?: return CoreRepairResult(CoreRepairStatus.INVALID_STORED_DATA, "textTraits")

            val rawStrength = obj.opt("strengthScore")
            val storedStrength = when (rawStrength) {
                null -> 0
                is Number -> rawStrength.toInt()
                else -> return CoreRepairResult(CoreRepairStatus.INVALID_STORED_DATA, "strengthScore")
            }

            val hasNoTraits = visualTraits.length() == 0 &&
                motionTraits.length() == 0 &&
                textTraits.length() == 0
            val corrected = JSONObject(obj.toString())
            val needsRepair = if (hasNoTraits) {
                if (storedStrength != 0) {
                    corrected.put("strengthScore", 0)
                    corrected.put("analysis", "عقل فارغ — يرجى إضافة فيديوهات مرجعية للتعلم والاستفادة")
                    corrected.put("lastUpdated", System.currentTimeMillis())
                }
                storedStrength != 0
            } else {
                if (storedStrength !in 0..100) {
                    corrected.put("strengthScore", storedStrength.coerceIn(0, 100))
                }
                storedStrength !in 0..100
            }

            if (!needsRepair) {
                return CoreRepairResult(CoreRepairStatus.NO_REPAIR_NEEDED)
            }

            val persisted = prefs.edit()
                .putString("qabas_core_style_json", corrected.toString())
                .commit()
            if (!persisted) {
                return CoreRepairResult(CoreRepairStatus.PERSISTENCE_FAILED)
            }

            try {
                StyleBrain.init(context)
                Log.d("StyleBrainLoadFix", "Persisted core integrity repair completed")
                CoreRepairResult(CoreRepairStatus.REPAIRED)
            } catch (e: Exception) {
                Log.w("StyleBrainLoadFix", "Core repaired but in-memory reload failed: ${e.javaClass.simpleName}")
                CoreRepairResult(CoreRepairStatus.PERSISTED_RELOAD_FAILED, e.javaClass.simpleName)
            }
        } catch (e: Exception) {
            Log.w("StyleBrainLoadFix", "Core repair failed: ${e.javaClass.simpleName}")
            CoreRepairResult(CoreRepairStatus.FAILED, e.javaClass.simpleName)
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
