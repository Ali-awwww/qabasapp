package com.qabas.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * متطلبات المشروع الإخراجية المحددة من المستخدم (Project Style Requirements)
 */
data class ProjectStyleRequirements(
    val ideaOrTopic: String = "",
    val durationSeconds: Int = 30,
    val aspectRatio: String = "9:16",
    val targetAudience: String = "الجمهور العام",
    val desiredTone: String = "حماسي ووقور",
    val pacingPreference: String = "متوازن",
    val visualPalettePreference: String = "ألوان داكنة سينمائية وذهب قبس",
    val customDirectives: String = "",
    val priorityFactor: String = "توازن بصري وحركي" // "بصري", "حركي", "نبرة ومحتوى", "توازن بصري وحركي"
) {
    fun toMap(): Map<String, Any> = mapOf(
        "ideaOrTopic" to ideaOrTopic,
        "durationSeconds" to durationSeconds,
        "aspectRatio" to aspectRatio,
        "targetAudience" to targetAudience,
        "desiredTone" to desiredTone,
        "pacingPreference" to pacingPreference,
        "visualPalettePreference" to visualPalettePreference,
        "customDirectives" to customDirectives,
        "priorityFactor" to priorityFactor
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): ProjectStyleRequirements {
            return ProjectStyleRequirements(
                ideaOrTopic = map["ideaOrTopic"]?.toString() ?: "",
                durationSeconds = (map["durationSeconds"] as? Number)?.toInt() ?: 30,
                aspectRatio = map["aspectRatio"]?.toString() ?: "9:16",
                targetAudience = map["targetAudience"]?.toString() ?: "الجمهور العام",
                desiredTone = map["desiredTone"]?.toString() ?: "حماسي ووقور",
                pacingPreference = map["pacingPreference"]?.toString() ?: "متوازن",
                visualPalettePreference = map["visualPalettePreference"]?.toString() ?: "ألوان داكنة وذهب قبس",
                customDirectives = map["customDirectives"]?.toString() ?: "",
                priorityFactor = map["priorityFactor"]?.toString() ?: "توازن بصري وحركي"
            )
        }
    }
}

/**
 * ملف النمط الرئيسي المركب (Master Style Profile)
 * الناتج عن دمج عدة أنماط فنية (Style Objects) وفق متطلبات المشروع.
 */
data class MasterStyle(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "النمط الإخراجي الماستر (Master Style)",
    val description: String = "",
    val sourceStyleIds: List<String> = emptyList(),
    val sourceStyleNames: List<String> = emptyList(),
    val visualStyle: VisualStylePattern = VisualStylePattern(),
    val motionRhythm: MotionRhythmPattern = MotionRhythmPattern(),
    val contentTone: ContentTonePattern = ContentTonePattern(),
    val ffmpegFilterDirective: String = "eq=contrast=1.12:brightness=0.01:saturation=1.1,vignette=PI/4",
    val islamicAestheticComplianceScore: Int = 95, // 0 - 100
    val targetAudience: String = "الجمهور العام",
    val aspectRatio: String = "9:16",
    val recommendedDurationSeconds: Int = 30,
    val fusionSummary: String = "",
    val confidenceScore: Float = 0.92f,
    val createdAt: Long = System.currentTimeMillis(),
    val isCloudSynced: Boolean = false
) {
    fun toVideoStyleAnalysis(): VideoStyleAnalysis {
        return VideoStyleAnalysis(
            detectedStyle = name,
            dominantColors = visualStyle.dominantColors,
            transitionSpeed = motionRhythm.transitionSpeed,
            movementPatterns = motionRhythm.movementPatterns,
            overallRhythm = motionRhythm.overallRhythm,
            audioStyle = "مؤثرات صوتية هادفة وتدرج وقور متناسق مع النمط الماستر",
            typographyStyle = contentTone.typographyStyle,
            contentTone = contentTone.tone,
            targetAudience = targetAudience,
            keywords = visualStyle.visualTraits + motionRhythm.motionTraits + listOf("MasterStyle", "QabasComposite")
        )
    }

    fun toStyleObject(): StyleObject {
        return StyleObject(
            id = id,
            name = name,
            userId = "master_engine",
            sourceVideoPathOrUrl = sourceStyleNames.joinToString(", "),
            visualStyle = visualStyle,
            motionRhythm = motionRhythm,
            contentTone = contentTone,
            overallScore = islamicAestheticComplianceScore,
            analysisSummary = description.ifBlank { fusionSummary },
            tags = listOf("MasterStyle", "CompositeProfile", aspectRatio) + visualStyle.visualTraits,
            createdAt = createdAt,
            isCloudSynced = isCloudSynced
        )
    }

    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "name" to name,
        "description" to description,
        "sourceStyleIds" to sourceStyleIds,
        "sourceStyleNames" to sourceStyleNames,
        "visualStyle" to visualStyle.toMap(),
        "motionRhythm" to motionRhythm.toMap(),
        "contentTone" to contentTone.toMap(),
        "ffmpegFilterDirective" to ffmpegFilterDirective,
        "islamicAestheticComplianceScore" to islamicAestheticComplianceScore,
        "targetAudience" to targetAudience,
        "aspectRatio" to aspectRatio,
        "recommendedDurationSeconds" to recommendedDurationSeconds,
        "fusionSummary" to fusionSummary,
        "confidenceScore" to confidenceScore,
        "createdAt" to createdAt,
        "isCloudSynced" to isCloudSynced
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): MasterStyle {
            @Suppress("UNCHECKED_CAST")
            val vMap = (map["visualStyle"] as? Map<String, Any?>) ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val mMap = (map["motionRhythm"] as? Map<String, Any?>) ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val tMap = (map["contentTone"] as? Map<String, Any?>) ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val ids = (map["sourceStyleIds"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val names = (map["sourceStyleNames"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

            return MasterStyle(
                id = map["id"]?.toString() ?: UUID.randomUUID().toString(),
                name = map["name"]?.toString() ?: "نمط ماستر مركب",
                description = map["description"]?.toString() ?: "",
                sourceStyleIds = ids,
                sourceStyleNames = names,
                visualStyle = VisualStylePattern.fromMap(vMap),
                motionRhythm = MotionRhythmPattern.fromMap(mMap),
                contentTone = ContentTonePattern.fromMap(tMap),
                ffmpegFilterDirective = map["ffmpegFilterDirective"]?.toString() ?: "eq=contrast=1.12:brightness=0.01:saturation=1.1",
                islamicAestheticComplianceScore = (map["islamicAestheticComplianceScore"] as? Number)?.toInt() ?: 95,
                targetAudience = map["targetAudience"]?.toString() ?: "الجمهور العام",
                aspectRatio = map["aspectRatio"]?.toString() ?: "9:16",
                recommendedDurationSeconds = (map["recommendedDurationSeconds"] as? Number)?.toInt() ?: 30,
                fusionSummary = map["fusionSummary"]?.toString() ?: "",
                confidenceScore = (map["confidenceScore"] as? Number)?.toFloat() ?: 0.9f,
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                isCloudSynced = true
            )
        }
    }
}

/**
 * خدمة إدارة وصهر الأنماط الرئيسية (StyleManager Service)
 * تتيح لـ Brain Engine استدعاء عدة Style Objects ودمجها بذكاء لإنتاج Master Style profile
 * يلبي متطلبات المشروع المحددة بدقة متناهية.
 */
object StyleManager {
    private const val TAG = "StyleManager"
    private const val PREFS_NAME = "qabas_master_styles_prefs"
    private const val KEY_MASTER_STYLES = "saved_master_styles"
    private const val KEY_ACTIVE_MASTER_STYLE = "active_master_style_id"

    private val _masterStyles = MutableStateFlow<List<MasterStyle>>(emptyList())
    val masterStyles: StateFlow<List<MasterStyle>> = _masterStyles.asStateFlow()

    private val _activeMasterStyle = MutableStateFlow<MasterStyle?>(null)
    val activeMasterStyle: StateFlow<MasterStyle?> = _activeMasterStyle.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    fun init(context: Context) {
        loadLocalMasterStyles(context)
    }

    private fun loadLocalMasterStyles(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_MASTER_STYLES, null)
            val list = mutableListOf<MasterStyle>()

            if (!jsonStr.isNullOrBlank()) {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(jsonToMasterStyle(obj))
                }
            }

            // إذا كانت القائمة فارغة، ننشئ نمط ماستر قبس الافتراضي الفاخر
            if (list.isEmpty()) {
                val defaultMaster = createDefaultQabasMasterStyle()
                list.add(defaultMaster)
                saveLocalMasterStyles(prefs, list)
            }

            _masterStyles.value = list

            // استعادة النمط النشط
            val activeId = prefs.getString(KEY_ACTIVE_MASTER_STYLE, null)
            val matchedActive = list.find { it.id == activeId } ?: list.firstOrNull()
            _activeMasterStyle.value = matchedActive
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load master styles: ${e.message}", e)
        }
    }

    private fun saveLocalMasterStyles(prefs: SharedPreferences, list: List<MasterStyle>) {
        try {
            val array = JSONArray()
            list.forEach { style ->
                array.put(masterStyleToJson(style))
            }
            prefs.edit().putString(KEY_MASTER_STYLES, array.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save master styles locally: ${e.message}", e)
        }
    }

    /**
     * الدمج والتوليد الذكي لنمط ماستر مركب (Composite Master Style)
     * بناءً على متطلبات المشروع المحددة والأنماط المخزنة في عقل قبس وسحابة Firestore.
     */
    suspend fun createCompositeMasterStyle(
        context: Context,
        requirements: ProjectStyleRequirements,
        selectedCandidateStyleObjects: List<StyleObject> = emptyList(),
        customProfileName: String? = null,
        syncToCloud: Boolean = true
    ): MasterStyle = withContext(Dispatchers.IO) {
        // 1. تجميع الأنماط المرشحة (من المدخلات، أو الذاكرة التراكمية في StyleBrain، أو Firestore)
        val availableStyles = if (selectedCandidateStyleObjects.isNotEmpty()) {
            selectedCandidateStyleObjects
        } else {
            val localAbsorbed = StyleBrain.getAllAbsorbedStyles().map { StyleObject.fromAbsorbedStyle(it) }
            val supabaseStyles = if (SupabaseServices.isSupabaseAvailable) {
                try {
                    SupabaseServices.Database.getStyleObjects().mapNotNull { row ->
                        try {
                            val payloadStr = row.payload.toString()
                            val jsonMap = org.json.JSONObject(payloadStr)
                            val map = mutableMapOf<String, Any?>()
                            val keys = jsonMap.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                map[k] = jsonMap.opt(k)
                            }
                            map["id"] = row.id
                            map["name"] = row.name
                            map["userId"] = row.userId ?: "creator"
                            map["sourceVideoPathOrUrl"] = row.sourceVideo ?: ""
                            map["overallScore"] = row.score
                            StyleObject.fromMap(map)
                        } catch (_: Exception) { null }
                    }
                } catch (e: Exception) { emptyList() }
            } else emptyList()

            val cloudStyles = if (supabaseStyles.isNotEmpty()) {
                supabaseStyles
            } else emptyList()

            (localAbsorbed + cloudStyles).distinctBy { it.id }
        }

        // 2. تقييم وحساب أوزان الأنماط المرشحة وفق متطلبات المشروع
        val weightedCandidates = evaluateAndWeightCandidates(availableStyles, requirements)

        // 3. دمج السمات البصرية، إيقاع الحركة، ونبرة المحتوى
        val mergedVisualStyle = blendVisualPatterns(weightedCandidates, requirements)
        val mergedMotionRhythm = blendMotionPatterns(weightedCandidates, requirements)
        val mergedContentTone = blendTonePatterns(weightedCandidates, requirements)

        // 4. صياغة أوامر وفلاتر FFmpeg المخصصة
        val ffmpegFilter = synthesizeFfmpegFilterDirective(mergedVisualStyle, mergedMotionRhythm, requirements)

        // 5. حساب مؤشر التوافق مع المعايير الجمالية الإسلامية لقَبَس (Islamic Aesthetic Compliance Score)
        val complianceScore = calculateIslamicComplianceScore(mergedVisualStyle, mergedMotionRhythm, mergedContentTone)

        // 6. بناء ملف الماستر المركب
        val sourceIds = weightedCandidates.map { it.first.id }
        val sourceNames = weightedCandidates.map { it.first.name }
        val generatedName = customProfileName?.ifBlank { null }
            ?: "ماستر [${requirements.ideaOrTopic.take(16).ifBlank { "مشروع دعوي" }}] • ${requirements.desiredTone}"

        val summaryText = "ملف ماستر مركب تم توليده بدمج ${weightedCandidates.size} أنماط فنية بنسب ذكية " +
                "ليناسب فكرة (${requirements.ideaOrTopic}) بنبرة (${requirements.desiredTone}) ووتيرة (${requirements.pacingPreference})."

        val masterProfile = MasterStyle(
            id = "master_${UUID.randomUUID().toString().take(8)}",
            name = generatedName,
            description = summaryText,
            sourceStyleIds = sourceIds,
            sourceStyleNames = sourceNames,
            visualStyle = mergedVisualStyle,
            motionRhythm = mergedMotionRhythm,
            contentTone = mergedContentTone,
            ffmpegFilterDirective = ffmpegFilter,
            islamicAestheticComplianceScore = complianceScore,
            targetAudience = requirements.targetAudience,
            aspectRatio = requirements.aspectRatio,
            recommendedDurationSeconds = requirements.durationSeconds,
            fusionSummary = summaryText,
            confidenceScore = 0.94f,
            createdAt = System.currentTimeMillis(),
            isCloudSynced = syncToCloud && SupabaseServices.isSupabaseAvailable
        )

        // 7. حفظ محلياً وتعيين كنمط نشط
        val currentList = _masterStyles.value.toMutableList()
        currentList.removeAll { it.id == masterProfile.id }
        currentList.add(0, masterProfile)
        _masterStyles.value = currentList
        _activeMasterStyle.value = masterProfile

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        saveLocalMasterStyles(prefs, currentList)
        prefs.edit().putString(KEY_ACTIVE_MASTER_STYLE, masterProfile.id).apply()

        // 8. حفظ في سحابة Supabase كأساس، وFirestore كنسخة إضافية
        if (syncToCloud) {
            if (SupabaseServices.isSupabaseAvailable) {
                try {
                    val payloadJson = masterStyleToJson(masterProfile).toString()
                    SupabaseServices.Database.saveMasterStyle(
                        id = masterProfile.id,
                        name = masterProfile.name,
                        description = masterProfile.description,
                        userId = "creator",
                        compliance = complianceScore,
                        payloadJson = payloadJson
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Could not sync master style to Supabase: ${e.message}")
                }
            }
        }

        Log.d(TAG, "MasterStyle synthesized successfully: ${masterProfile.name} with compliance score $complianceScore%")
        return@withContext masterProfile
    }

    /**
     * حساب ومعاينة ملف النمط الرئيسي المركب بشكل فوري (Live Preview) دون حفظ
     */
    fun previewMerge(
        weightedStyles: List<Pair<StyleObject, Float>>,
        requirements: ProjectStyleRequirements = ProjectStyleRequirements(),
        customName: String = ""
    ): MasterStyle {
        if (weightedStyles.isEmpty()) {
            return createDefaultQabasMasterStyle()
        }
        val totalWeight = weightedStyles.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(0.001f)
        val normalized = weightedStyles.map { it.first to (it.second / totalWeight) }

        val mergedVisual = blendVisualPatterns(normalized, requirements)
        val mergedMotion = blendMotionPatterns(normalized, requirements)
        val mergedTone = blendTonePatterns(normalized, requirements)
        val ffmpegFilter = synthesizeFfmpegFilterDirective(mergedVisual, mergedMotion, requirements)
        val complianceScore = calculateIslamicComplianceScore(mergedVisual, mergedMotion, mergedTone)

        val generatedName = if (customName.isNotBlank()) {
            customName
        } else {
            val topNames = weightedStyles.sortedByDescending { it.second }.take(2).joinToString(" × ") { it.first.name.take(12) }
            "ماستر مدمج: $topNames"
        }

        return MasterStyle(
            id = "master_preview_${UUID.randomUUID().toString().take(8)}",
            name = generatedName,
            description = "نمط ماستر تم توليده فورياً بدمج (${weightedStyles.size}) أنماط فنية.",
            sourceStyleIds = weightedStyles.map { it.first.id },
            sourceStyleNames = weightedStyles.map { it.first.name },
            visualStyle = mergedVisual,
            motionRhythm = mergedMotion,
            contentTone = mergedTone,
            ffmpegFilterDirective = ffmpegFilter,
            islamicAestheticComplianceScore = complianceScore,
            targetAudience = requirements.targetAudience,
            aspectRatio = requirements.aspectRatio,
            recommendedDurationSeconds = requirements.durationSeconds,
            fusionSummary = "تم الدمج الفوري لـ (${weightedStyles.size}) أنماط فنية بنجاح.",
            confidenceScore = 0.95f,
            createdAt = System.currentTimeMillis(),
            isCloudSynced = false
        )
    }

    /**
     * دمج قائمة صريحة من الأنماط الفنية مع أوزانها المخصصة
     */
    suspend fun mergeExplicitStyleObjects(
        context: Context,
        name: String,
        weightedStyles: List<Pair<StyleObject, Float>>,
        requirements: ProjectStyleRequirements? = null
    ): MasterStyle = withContext(Dispatchers.IO) {
        val reqs = requirements ?: ProjectStyleRequirements()
        val totalWeight = weightedStyles.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(0.001f)
        val normalized = weightedStyles.map { it.first to (it.second / totalWeight) }

        val mergedVisual = blendVisualPatterns(normalized, reqs)
        val mergedMotion = blendMotionPatterns(normalized, reqs)
        val mergedTone = blendTonePatterns(normalized, reqs)
        val ffmpegFilter = synthesizeFfmpegFilterDirective(mergedVisual, mergedMotion, reqs)
        val complianceScore = calculateIslamicComplianceScore(mergedVisual, mergedMotion, mergedTone)

        val master = MasterStyle(
            id = "master_fused_${UUID.randomUUID().toString().take(8)}",
            name = name.ifBlank { "ماستر مدمج يدوي" },
            description = "نمط ماستر تم صهره يدوياً من ${weightedStyles.size} أنماط فنية.",
            sourceStyleIds = weightedStyles.map { it.first.id },
            sourceStyleNames = weightedStyles.map { it.first.name },
            visualStyle = mergedVisual,
            motionRhythm = mergedMotion,
            contentTone = mergedTone,
            ffmpegFilterDirective = ffmpegFilter,
            islamicAestheticComplianceScore = complianceScore,
            targetAudience = reqs.targetAudience,
            aspectRatio = reqs.aspectRatio,
            recommendedDurationSeconds = reqs.durationSeconds,
            fusionSummary = "تم الدمج اليدوي لـ (${weightedStyles.size}) أنماط بنجاح.",
            confidenceScore = 0.95f,
            createdAt = System.currentTimeMillis(),
            isCloudSynced = false
        )

        val currentList = _masterStyles.value.toMutableList()
        currentList.add(0, master)
        _masterStyles.value = currentList
        _activeMasterStyle.value = master

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        saveLocalMasterStyles(prefs, currentList)

        return@withContext master
    }

    /**
     * تقييم الأنماط وحساب درجة توافقها (Affinity Scoring) مع المتطلبات
     */
    private fun evaluateAndWeightCandidates(
        candidates: List<StyleObject>,
        reqs: ProjectStyleRequirements
    ): List<Pair<StyleObject, Float>> {
        if (candidates.isEmpty()) {
            val core = StyleBrain.getCoreStyle()
            val defaultObj = StyleObject(
                id = "core_default",
                name = "هوية قبس الأساسية",
                visualStyle = VisualStylePattern(visualTraits = core.visualTraits),
                motionRhythm = MotionRhythmPattern(motionTraits = core.motionTraits),
                contentTone = ContentTonePattern(textTraits = core.textTraits)
            )
            return listOf(defaultObj to 1.0f)
        }

        val scored = candidates.map { candidate ->
            var score = 50f

            // 1. تقارب النبرة
            if (candidate.contentTone.tone.contains(reqs.desiredTone, ignoreCase = true) ||
                reqs.desiredTone.contains(candidate.contentTone.tone, ignoreCase = true)) {
                score += 25f
            }

            // 2. تقارب الفكرة والكلمات المفتاحية
            val ideaWords = reqs.ideaOrTopic.split(" ").filter { it.length > 2 }
            val matchedTags = candidate.tags.count { tag ->
                ideaWords.any { tag.contains(it, ignoreCase = true) }
            }
            score += (matchedTags * 8f).coerceAtMost(20f)

            // 3. تقارب وتيرة الحركة
            if (candidate.motionRhythm.transitionSpeed.contains(reqs.pacingPreference, ignoreCase = true) ||
                candidate.motionRhythm.overallRhythm.contains(reqs.pacingPreference, ignoreCase = true)) {
                score += 15f
            }

            // 4. تقارب الجمهور المستهدف
            if (candidate.contentTone.targetAudience.contains(reqs.targetAudience, ignoreCase = true)) {
                score += 10f
            }

            // 5. تقييم الجودة الإجمالي لكائن النمط
            score += (candidate.overallScore * 0.2f)

            candidate to score
        }

        // أخذ أعلى 3 أنماط مطابقة وتطبيع أوزانها
        val topCandidates = scored.sortedByDescending { it.second }.take(3)
        val totalScore = topCandidates.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(1f)

        return topCandidates.map { it.first to (it.second / totalScore) }
    }

    /**
     * دمج السمات البصرية
     */
    private fun blendVisualPatterns(
        weighted: List<Pair<StyleObject, Float>>,
        reqs: ProjectStyleRequirements
    ): VisualStylePattern {
        val allVisualTraits = mutableListOf<String>()
        weighted.forEach { (obj, _) ->
            allVisualTraits.addAll(obj.visualStyle.visualTraits)
        }
        // إضافة السمات البصرية الأساسية لقبس
        allVisualTraits.addAll(StyleBrain.getCoreStyle().visualTraits.take(3))

        val dominantHex = if (reqs.visualPalettePreference.contains("ذهب") || reqs.visualPalettePreference.contains("Gold")) {
            "#E8C547"
        } else {
            weighted.maxByOrNull { it.second }?.first?.visualStyle?.primaryColorHex ?: "#E8C547"
        }

        val dominantColors = "أسطح داكنة فاخرة (#0B0F19) مع لمسات ذهب قبس المشعة ($dominantHex)"

        return VisualStylePattern(
            dominantColors = dominantColors,
            primaryColorHex = dominantHex,
            backgroundColorHex = "#0B0F19",
            lightingAndContrast = "إضاءة سينمائية دافئة وتباين عالي 1:10 مخصص للوضوح على الهواتف",
            visualTraits = allVisualTraits.distinct().take(6)
        )
    }

    /**
     * دمج إيقاع الحركة
     */
    private fun blendMotionPatterns(
        weighted: List<Pair<StyleObject, Float>>,
        reqs: ProjectStyleRequirements
    ): MotionRhythmPattern {
        val allMotionTraits = mutableListOf<String>()
        weighted.forEach { (obj, _) ->
            allMotionTraits.addAll(obj.motionRhythm.motionTraits)
        }
        allMotionTraits.addAll(StyleBrain.getCoreStyle().motionTraits.take(2))

        val transitionSpeed = when {
            reqs.durationSeconds <= 20 || reqs.pacingPreference.contains("سريع") -> "سريعة وحيوية (0.4 ثانية)"
            reqs.durationSeconds > 60 || reqs.pacingPreference.contains("هادئ") -> "وقورة وسينمائية متمهلة (0.9 ثانية)"
            else -> "متوازنة وانسيابية (0.6 ثانية)"
        }

        val topObj = weighted.maxByOrNull { it.second }?.first
        val transitionType = topObj?.motionRhythm?.transitionType?.ifBlank { "Dissolve" } ?: "Dissolve"

        return MotionRhythmPattern(
            transitionSpeed = transitionSpeed,
            transitionType = transitionType,
            movementPatterns = "زووم بطيء سينمائي متدرج (Slow Dynamic ZoomIn 1.0 -> 1.08)",
            overallRhythm = "إيقاع تصاعدي متزن يخدم المعنى القرآني والدعوي",
            motionTraits = allMotionTraits.distinct().take(5)
        )
    }

    /**
     * دمج نبرة المحتوى والتيبوغرافيا
     */
    private fun blendTonePatterns(
        weighted: List<Pair<StyleObject, Float>>,
        reqs: ProjectStyleRequirements
    ): ContentTonePattern {
        val allTextTraits = mutableListOf<String>()
        weighted.forEach { (obj, _) ->
            allTextTraits.addAll(obj.contentTone.textTraits)
        }
        allTextTraits.addAll(StyleBrain.getCoreStyle().textTraits.take(2))

        val typographyStyle = "خط عربي كوفي عريض وواضح في الثلث السفلي، مع إبراز الكلمات المحورية باللون الذهبي وتظليل أسود خلفي"

        val captionAnimation = when {
            reqs.durationSeconds <= 30 -> "WordByWord" // كلمة بكلمة للريلز
            else -> "LineByLine" // سطر بسطر للفيديوهات الهادئة
        }

        return ContentTonePattern(
            tone = reqs.desiredTone.ifBlank { "وقور ومؤثر وهادف" },
            targetAudience = reqs.targetAudience,
            typographyStyle = typographyStyle,
            captionAnimation = captionAnimation,
            textTraits = allTextTraits.distinct().take(5)
        )
    }

    /**
     * توليد معايير فلاتر FFmpeg الحقيقية
     */
    private fun synthesizeFfmpegFilterDirective(
        visual: VisualStylePattern,
        motion: MotionRhythmPattern,
        reqs: ProjectStyleRequirements
    ): String {
        val contrast = if (visual.lightingAndContrast.contains("عالي")) "1.14" else "1.08"
        val saturation = if (visual.dominantColors.contains("ذهب")) "1.15" else "1.05"
        val vignette = if (reqs.aspectRatio == "9:16") "vignette=PI/4" else "vignette=PI/5"
        
        return "eq=contrast=$contrast:brightness=0.01:saturation=$saturation,$vignette"
    }

    /**
     * حساب درجة التوافق مع المعايير الجمالية الإسلامية
     */
    private fun calculateIslamicComplianceScore(
        visual: VisualStylePattern,
        motion: MotionRhythmPattern,
        tone: ContentTonePattern
    ): Int {
        var score = 88

        // خط واضح ووقور
        if (tone.typographyStyle.contains("كوفي") || tone.typographyStyle.contains("نسخ") || tone.typographyStyle.contains("واضح")) {
            score += 4
        }

        // ألوان داكنة راقية تمنع تشتيت الانتباه
        if (visual.backgroundColorHex.contains("0B0F19") || visual.dominantColors.contains("داكن")) {
            score += 4
        }

        // إيقاع غير صاخب متزن
        if (!motion.overallRhythm.contains("عنيف") && !motion.overallRhythm.contains("صاخب")) {
            score += 4
        }

        return score.coerceIn(80, 100)
    }

    private fun createDefaultQabasMasterStyle(): MasterStyle {
        val core = StyleBrain.getCoreStyle()
        return MasterStyle(
            id = "master_qabas_default",
            name = "ماستر قبس الإخراجي الفاخر (Master Cinematic)",
            description = "الهوية الإخراجية المرجعية الأساسية لتطبيق قبس: ألوان داكنة #0B0F19، تباين ذهبي #E8C547، وزووم سينمائي متزن.",
            sourceStyleIds = listOf("core_style"),
            sourceStyleNames = listOf("الهوية المرجعية لقبس"),
            visualStyle = VisualStylePattern(
                dominantColors = "DeepSlate (#0B0F19) مع لمسات ذهبية ملكية (#E8C547)",
                primaryColorHex = "#E8C547",
                backgroundColorHex = "#0B0F19",
                lightingAndContrast = "إضاءة استوديو دافئة وتباين عالي 1:10",
                visualTraits = core.visualTraits
            ),
            motionRhythm = MotionRhythmPattern(
                transitionSpeed = "انسيابية متوازنة (0.6 ثانية)",
                transitionType = "Dissolve",
                movementPatterns = "زووم بطيء سينمائي متدرج",
                overallRhythm = "تصاعدي وقور وهادف",
                motionTraits = core.motionTraits
            ),
            contentTone = ContentTonePattern(
                tone = "وقور ومؤثر ودعوي هادف",
                targetAudience = "الجمهور العام والشباب",
                typographyStyle = "خط كوفي عريض في المنتصف مع توهج ذهبي للكلمات المفتاحية",
                captionAnimation = "WordByWord",
                textTraits = core.textTraits
            ),
            ffmpegFilterDirective = "eq=contrast=1.12:brightness=0.01:saturation=1.1,vignette=PI/4",
            islamicAestheticComplianceScore = 98,
            targetAudience = "الجمهور العام",
            aspectRatio = "9:16",
            recommendedDurationSeconds = 30,
            fusionSummary = "النمط الإخراجي المرجعي المحفوظ في قلب العقل الإخراجي.",
            confidenceScore = 0.98f,
            createdAt = System.currentTimeMillis(),
            isCloudSynced = false
        )
    }

    /**
     * تعيين نمط ماستر كنمط نشط للإنتاج
     */
    fun setActiveMasterStyle(context: Context, masterStyle: MasterStyle) {
        _activeMasterStyle.value = masterStyle
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACTIVE_MASTER_STYLE, masterStyle.id).apply()
        Log.d(TAG, "Active master style set to: ${masterStyle.name}")
    }

    /**
     * حذف نمط ماستر
     */
    fun deleteMasterStyle(context: Context, masterStyleId: String) {
        val currentList = _masterStyles.value.toMutableList()
        currentList.removeAll { it.id == masterStyleId }
        _masterStyles.value = currentList

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        saveLocalMasterStyles(prefs, currentList)

        if (_activeMasterStyle.value?.id == masterStyleId) {
            _activeMasterStyle.value = currentList.firstOrNull()
            prefs.edit().putString(KEY_ACTIVE_MASTER_STYLE, _activeMasterStyle.value?.id).apply()
        }

        if (SupabaseServices.isSupabaseAvailable) {
            scope.launch {
                try {
                    SupabaseServices.Database.deleteMasterStyle(masterStyleId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting master style from Supabase: ${e.message}")
                }
            }
        }
    }

    /**
     * استرجاع النمط النشط أو النمط الافتراضي
     */
    fun getActiveOrFallbackMasterStyle(): MasterStyle {
        return _activeMasterStyle.value ?: _masterStyles.value.firstOrNull() ?: createDefaultQabasMasterStyle()
    }

    /**
     * مزامنة أنماط الماستر المخزنة في السحابة (Supabase كأساس، وFirestore كبديل) مع الذاكرة المحلية
     */
    suspend fun syncWithCloud(context: Context): List<MasterStyle> = withContext(Dispatchers.IO) {
        val localMasters = _masterStyles.value.toMutableList()

        if (SupabaseServices.isSupabaseAvailable) {
            try {
                val supabaseRows = SupabaseServices.Database.getMasterStyles()
                if (supabaseRows.isNotEmpty()) {
                    supabaseRows.forEach { row ->
                        try {
                            val json = JSONObject(row.payload.toString())
                            val master = jsonToMasterStyle(json)
                            val index = localMasters.indexOfFirst { it.id == master.id }
                            if (index >= 0) {
                                localMasters[index] = master
                            } else {
                                localMasters.add(master)
                            }
                        } catch (_: Exception) {}
                    }
                    _masterStyles.value = localMasters
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    saveLocalMasterStyles(prefs, localMasters)
                    return@withContext localMasters
                }
            } catch (e: Exception) {
                Log.w(TAG, "Supabase master style sync error: ${e.message}")
            }
        }

        return@withContext _masterStyles.value
    }

    /**
     * تسجيل كائن نمط فني مخصص (StyleObject) محلياً وفي السحابة (Supabase كأساس)
     */
    suspend fun registerOrUpdateStyleObject(
        context: Context,
        styleObject: StyleObject,
        syncToCloud: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. الحفظ في الذاكرة التراكمية المحلية StyleBrain
            StyleBrain.absorbStyleObject(context, styleObject)

            // 2. الحفظ في Supabase السحابي كأساس
            if (syncToCloud && SupabaseServices.isSupabaseAvailable) {
                try {
                    val payloadJson = JSONObject(styleObject.toMap()).toString()
                    SupabaseServices.Database.saveStyleObject(
                        id = styleObject.id,
                        name = styleObject.name,
                        userId = styleObject.userId,
                        sourceVideo = styleObject.sourceVideoPathOrUrl,
                        score = styleObject.overallScore,
                        tags = styleObject.tags,
                        payloadJson = payloadJson
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Could not save StyleObject to Supabase: ${e.message}")
                }
            }

            Log.d(TAG, "StyleObject registered successfully: ${styleObject.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error registering StyleObject: ${e.message}", e)
            false
        }
    }

    // JSON Serializers
    private fun masterStyleToJson(style: MasterStyle): JSONObject {
        val json = JSONObject()
        json.put("id", style.id)
        json.put("name", style.name)
        json.put("description", style.description)
        json.put("ffmpegFilterDirective", style.ffmpegFilterDirective)
        json.put("islamicAestheticComplianceScore", style.islamicAestheticComplianceScore)
        json.put("targetAudience", style.targetAudience)
        json.put("aspectRatio", style.aspectRatio)
        json.put("recommendedDurationSeconds", style.recommendedDurationSeconds)
        json.put("fusionSummary", style.fusionSummary)
        json.put("confidenceScore", style.confidenceScore.toDouble())
        json.put("createdAt", style.createdAt)
        json.put("isCloudSynced", style.isCloudSynced)

        val idsArr = JSONArray(); style.sourceStyleIds.forEach { idsArr.put(it) }; json.put("sourceStyleIds", idsArr)
        val namesArr = JSONArray(); style.sourceStyleNames.forEach { namesArr.put(it) }; json.put("sourceStyleNames", namesArr)

        // Visual
        val vObj = JSONObject()
        vObj.put("dominantColors", style.visualStyle.dominantColors)
        vObj.put("primaryColorHex", style.visualStyle.primaryColorHex)
        vObj.put("backgroundColorHex", style.visualStyle.backgroundColorHex)
        vObj.put("lightingAndContrast", style.visualStyle.lightingAndContrast)
        val vtArr = JSONArray(); style.visualStyle.visualTraits.forEach { vtArr.put(it) }; vObj.put("visualTraits", vtArr)
        json.put("visualStyle", vObj)

        // Motion
        val mObj = JSONObject()
        mObj.put("transitionSpeed", style.motionRhythm.transitionSpeed)
        mObj.put("transitionType", style.motionRhythm.transitionType)
        mObj.put("movementPatterns", style.motionRhythm.movementPatterns)
        mObj.put("overallRhythm", style.motionRhythm.overallRhythm)
        val mtArr = JSONArray(); style.motionRhythm.motionTraits.forEach { mtArr.put(it) }; mObj.put("motionTraits", mtArr)
        json.put("motionRhythm", mObj)

        // Tone
        val tObj = JSONObject()
        tObj.put("tone", style.contentTone.tone)
        tObj.put("targetAudience", style.contentTone.targetAudience)
        tObj.put("typographyStyle", style.contentTone.typographyStyle)
        tObj.put("captionAnimation", style.contentTone.captionAnimation)
        val ttArr = JSONArray(); style.contentTone.textTraits.forEach { ttArr.put(it) }; tObj.put("textTraits", ttArr)
        json.put("contentTone", tObj)

        return json
    }

    private fun jsonToMasterStyle(json: JSONObject): MasterStyle {
        val ids = mutableListOf<String>()
        val idsArr = json.optJSONArray("sourceStyleIds")
        if (idsArr != null) {
            for (i in 0 until idsArr.length()) ids.add(idsArr.getString(i))
        }

        val names = mutableListOf<String>()
        val namesArr = json.optJSONArray("sourceStyleNames")
        if (namesArr != null) {
            for (i in 0 until namesArr.length()) names.add(namesArr.getString(i))
        }

        val vObj = json.optJSONObject("visualStyle") ?: JSONObject()
        val vtList = mutableListOf<String>()
        val vtArr = vObj.optJSONArray("visualTraits")
        if (vtArr != null) {
            for (i in 0 until vtArr.length()) vtList.add(vtArr.getString(i))
        }
        val visual = VisualStylePattern(
            dominantColors = vObj.optString("dominantColors", "ألوان داكنة وذهب قبس"),
            primaryColorHex = vObj.optString("primaryColorHex", "#E8C547"),
            backgroundColorHex = vObj.optString("backgroundColorHex", "#0B0F19"),
            lightingAndContrast = vObj.optString("lightingAndContrast", "تباين سينمائي عالي"),
            visualTraits = vtList
        )

        val mObj = json.optJSONObject("motionRhythm") ?: JSONObject()
        val mtList = mutableListOf<String>()
        val mtArr = mObj.optJSONArray("motionTraits")
        if (mtArr != null) {
            for (i in 0 until mtArr.length()) mtList.add(mtArr.getString(i))
        }
        val motion = MotionRhythmPattern(
            transitionSpeed = mObj.optString("transitionSpeed", "متوازنة"),
            transitionType = mObj.optString("transitionType", "Dissolve"),
            movementPatterns = mObj.optString("movementPatterns", "زووم بطيء"),
            overallRhythm = mObj.optString("overallRhythm", "تصاعدي وقور"),
            motionTraits = mtList
        )

        val tObj = json.optJSONObject("contentTone") ?: JSONObject()
        val ttList = mutableListOf<String>()
        val ttArr = tObj.optJSONArray("textTraits")
        if (ttArr != null) {
            for (i in 0 until ttArr.length()) ttList.add(ttArr.getString(i))
        }
        val tone = ContentTonePattern(
            tone = tObj.optString("tone", "وقور ومؤثر"),
            targetAudience = tObj.optString("targetAudience", "الجمهور العام"),
            typographyStyle = tObj.optString("typographyStyle", "خط كوفي عريض"),
            captionAnimation = tObj.optString("captionAnimation", "WordByWord"),
            textTraits = ttList
        )

        return MasterStyle(
            id = json.optString("id", UUID.randomUUID().toString()),
            name = json.optString("name", "نمط ماستر"),
            description = json.optString("description", ""),
            sourceStyleIds = ids,
            sourceStyleNames = names,
            visualStyle = visual,
            motionRhythm = motion,
            contentTone = tone,
            ffmpegFilterDirective = json.optString("ffmpegFilterDirective", "eq=contrast=1.12:brightness=0.01:saturation=1.1,vignette=PI/4"),
            islamicAestheticComplianceScore = json.optInt("islamicAestheticComplianceScore", 95),
            targetAudience = json.optString("targetAudience", "الجمهور العام"),
            aspectRatio = json.optString("aspectRatio", "9:16"),
            recommendedDurationSeconds = json.optInt("recommendedDurationSeconds", 30),
            fusionSummary = json.optString("fusionSummary", ""),
            confidenceScore = json.optDouble("confidenceScore", 0.92).toFloat(),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            isCloudSynced = json.optBoolean("isCloudSynced", false)
        )
    }
}
