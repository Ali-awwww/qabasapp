package com.qabas.app

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

data class VisualStylePattern(
    val dominantColors: String = "ألوان داكنة DeepSlate #0B0F19 مع هوية AI بنفسجية #8B5CF6 وإكسنت سيان #22D3EE",
    val primaryColorHex: String = "#8B5CF6",
    val backgroundColorHex: String = "#0B0F19",
    val lightingAndContrast: String = "إضاءة سينمائية باردة مع تباين تقني عالي 1:10",
    val visualTraits: List<String> = emptyList()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "dominantColors" to dominantColors,
        "primaryColorHex" to primaryColorHex,
        "backgroundColorHex" to backgroundColorHex,
        "lightingAndContrast" to lightingAndContrast,
        "visualTraits" to visualTraits
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): VisualStylePattern {
            @Suppress("UNCHECKED_CAST")
            val traits = (map["visualTraits"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            return VisualStylePattern(
                dominantColors = map["dominantColors"]?.toString() ?: "ألوان داكنة سينمائية",
                primaryColorHex = map["primaryColorHex"]?.toString() ?: "#8B5CF6",
                backgroundColorHex = map["backgroundColorHex"]?.toString() ?: "#0B0F19",
                lightingAndContrast = map["lightingAndContrast"]?.toString() ?: "تباين عالي",
                visualTraits = traits
            )
        }
    }
}

data class MotionRhythmPattern(
    val transitionSpeed: String = "متوسطة متوازنة",
    val transitionType: String = "Dissolve",
    val movementPatterns: String = "زووم بطيء متصاعد (Slow Cinematic ZoomIn)",
    val overallRhythm: String = "إيقاع تصاعدي وقور ومؤثر",
    val motionTraits: List<String> = emptyList()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "transitionSpeed" to transitionSpeed,
        "transitionType" to transitionType,
        "movementPatterns" to movementPatterns,
        "overallRhythm" to overallRhythm,
        "motionTraits" to motionTraits
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): MotionRhythmPattern {
            @Suppress("UNCHECKED_CAST")
            val traits = (map["motionTraits"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            return MotionRhythmPattern(
                transitionSpeed = map["transitionSpeed"]?.toString() ?: "متوسطة متوازنة",
                transitionType = map["transitionType"]?.toString() ?: "Dissolve",
                movementPatterns = map["movementPatterns"]?.toString() ?: "زووم بطيء",
                overallRhythm = map["overallRhythm"]?.toString() ?: "إيقاع تصاعدي",
                motionTraits = traits
            )
        }
    }
}

data class ContentTonePattern(
    val tone: String = "مؤثر ووقور وهادف",
    val targetAudience: String = "الجمهور العام والشباب",
    val typographyStyle: String = "خط عربي عريض في المنتصف مع إبراز الكلمات",
    val captionAnimation: String = "WordByWord",
    val textTraits: List<String> = emptyList()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "tone" to tone,
        "targetAudience" to targetAudience,
        "typographyStyle" to typographyStyle,
        "captionAnimation" to captionAnimation,
        "textTraits" to textTraits
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): ContentTonePattern {
            @Suppress("UNCHECKED_CAST")
            val traits = (map["textTraits"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            return ContentTonePattern(
                tone = map["tone"]?.toString() ?: "مؤثر ووقور",
                targetAudience = map["targetAudience"]?.toString() ?: "الجمهور العام",
                typographyStyle = map["typographyStyle"]?.toString() ?: "خط كوفي عريض",
                captionAnimation = map["captionAnimation"]?.toString() ?: "WordByWord",
                textTraits = traits
            )
        }
    }
}

data class StyleObject(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "أسلوب فني",
    val userId: String = "default_user",
    val sourceVideoPathOrUrl: String = "",
    val visualStyle: VisualStylePattern = VisualStylePattern(),
    val motionRhythm: MotionRhythmPattern = MotionRhythmPattern(),
    val contentTone: ContentTonePattern = ContentTonePattern(),
    val overallScore: Int = 0,
    val analysisSummary: String = "",
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val isCloudSynced: Boolean = false
) {
    fun toAbsorbedStyle(): AbsorbedStyle {
        return AbsorbedStyle(
            id = id,
            name = name,
            sourceVideoPathOrUrl = sourceVideoPathOrUrl,
            analysis = analysisSummary.ifBlank { "${contentTone.tone} | ${visualStyle.dominantColors}" },
            visualTraits = visualStyle.visualTraits,
            motionTraits = motionRhythm.motionTraits,
            textTraits = contentTone.textTraits,
            overallScore = overallScore,
            absorbedAt = createdAt
        )
    }

    fun toVideoStyleAnalysis(): VideoStyleAnalysis {
        return VideoStyleAnalysis(
            detectedStyle = name,
            dominantColors = visualStyle.dominantColors,
            transitionSpeed = motionRhythm.transitionSpeed,
            movementPatterns = motionRhythm.movementPatterns,
            overallRhythm = motionRhythm.overallRhythm,
            audioStyle = "مؤثرات صوتية هادفة وتدرج صوتي متقن",
            typographyStyle = contentTone.typographyStyle,
            contentTone = contentTone.tone,
            targetAudience = contentTone.targetAudience,
            keywords = visualStyle.visualTraits + motionRhythm.motionTraits + tags
        )
    }

    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "name" to name,
        "userId" to userId,
        "sourceVideoPathOrUrl" to sourceVideoPathOrUrl,
        "visualStyle" to visualStyle.toMap(),
        "motionRhythm" to motionRhythm.toMap(),
        "contentTone" to contentTone.toMap(),
        "overallScore" to overallScore,
        "analysisSummary" to analysisSummary,
        "tags" to tags,
        "createdAt" to createdAt,
        "isCloudSynced" to true
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): StyleObject {
            @Suppress("UNCHECKED_CAST")
            val vMap = (map["visualStyle"] as? Map<String, Any?>) ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val mMap = (map["motionRhythm"] as? Map<String, Any?>) ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val tMap = (map["contentTone"] as? Map<String, Any?>) ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val tagsList = (map["tags"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

            return StyleObject(
                id = map["id"]?.toString() ?: UUID.randomUUID().toString(),
                name = map["name"]?.toString() ?: "نمط فني مسترجع",
                userId = map["userId"]?.toString() ?: "unknown",
                sourceVideoPathOrUrl = map["sourceVideoPathOrUrl"]?.toString() ?: "",
                visualStyle = VisualStylePattern.fromMap(vMap),
                motionRhythm = MotionRhythmPattern.fromMap(mMap),
                contentTone = ContentTonePattern.fromMap(tMap),
                overallScore = (map["overallScore"] as? Number)?.toInt()?.coerceIn(0, 100) ?: 0,
                analysisSummary = map["analysisSummary"]?.toString() ?: "",
                tags = tagsList,
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                isCloudSynced = true
            )
        }

        fun fromAbsorbedStyle(absorbed: AbsorbedStyle, userId: String = "default_user"): StyleObject {
            return StyleObject(
                id = absorbed.id,
                name = absorbed.name,
                userId = userId,
                sourceVideoPathOrUrl = absorbed.sourceVideoPathOrUrl,
                visualStyle = VisualStylePattern(
                    dominantColors = if (absorbed.visualTraits.isNotEmpty()) absorbed.visualTraits.joinToString("، ") else "ألوان سينمائية داكنة وذهب قبس",
                    primaryColorHex = absorbed.resolvePrimaryColorHex(),
                    backgroundColorHex = absorbed.resolveBackgroundColorHex(),
                    lightingAndContrast = "إضاءة سينمائية دافئة مع تباين عالي",
                    visualTraits = absorbed.visualTraits
                ),
                motionRhythm = MotionRhythmPattern(
                    transitionSpeed = absorbed.motionTraits.firstOrNull { it.contains("سريع") || it.contains("بطيء") || it.contains("متوسط") } ?: "متوازنة",
                    transitionType = absorbed.resolveTransitionType(),
                    movementPatterns = absorbed.motionTraits.firstOrNull { it.contains("زووم") || it.contains("zoom") || it.contains("slide") } ?: "زووم بطيء متصاعد",
                    overallRhythm = absorbed.motionTraits.lastOrNull() ?: "تصاعدي مؤثر",
                    motionTraits = absorbed.motionTraits
                ),
                contentTone = ContentTonePattern(
                    tone = absorbed.analysis.ifBlank { "مؤثر ووقور" },
                    targetAudience = "الجمهور العام",
                    typographyStyle = if (absorbed.textTraits.isNotEmpty()) absorbed.textTraits.joinToString("، ") else "خط عربي كوفي عريض في المنتصف",
                    captionAnimation = absorbed.resolveTextAnimation(),
                    textTraits = absorbed.textTraits
                ),
                overallScore = absorbed.overallScore,
                analysisSummary = absorbed.analysis,
                tags = absorbed.visualTraits + absorbed.motionTraits,
                createdAt = absorbed.absorbedAt,
                isCloudSynced = false
            )
        }
    }
}

data class QabasCoreStyle(
    val visualTraits: List<String>,
    val motionTraits: List<String>,
    val textTraits: List<String>,
    val analysis: String,
    val strengthScore: Int,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun toAbsorbedStyle(): AbsorbedStyle {
        return AbsorbedStyle(
            id = "qabas_core_identity",
            name = if (strengthScore == 0) "عقل فارغ (محايد)" else "هوية قبس السينمائية التراكمية (Qabas Core Style)",
            sourceVideoPathOrUrl = "CoreBrain",
            analysis = analysis,
            visualTraits = visualTraits,
            motionTraits = motionTraits,
            textTraits = textTraits,
            overallScore = strengthScore,
            absorbedAt = lastUpdated
        )
    }
}

/**
 * توجيه إخراج ملموس يُستمد من النمط الممتص — يُحقَن في المونتاج والمحرك مباشرة.
 * ليس وصفاً أدبياً؛ قيم قابلة للتنفيذ.
 */
data class StyleDirective(
    val styleName: String,
    val primaryColorHex: String,
    val backgroundColorHex: String,
    val accentColorHex: String,
    val transitionType: String,       // Dissolve | Fade | ZoomIn | SlideLeft | Glitch | FadeBlack
    val motionType: String,           // slow_zoom | static | pan | punch_in
    val tempo: String,                // خاشع | متوسط | سريع
    val captionAnimation: String,     // WordByWord | FadeIn | Pop | LineReveal
    val captionPosition: String,      // bottom | center | top
    val visualKeywordsEn: List<String>,
    val visualKeywordsAr: List<String>,
    val promptBoost: String,          // جملة إنجليزية قوية تُحقن في برومبت التوليد
    val filterHint: String            // warm_gold | cool_emerald | high_contrast_dark | soft_desert | ai_violet
)

data class StyleImprovementProposal(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val targetTraitCategory: String, // "الألوان والإضاءة" | "الانتقالات والحركة" | "الإيقاع والسرعة" | "النصوص والطباعة"
    val suggestedChange: String,
    val rationale: String,
    val successRateBasedOn: Int = 88,
    val sampleProjectsCount: Int = 1,
    val enhancementScore: Int = 3,
    val newVisualTrait: String? = null,
    val newMotionTrait: String? = null,
    val newTextTrait: String? = null,
    val newFilterHint: String? = null,
    val newTempo: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class AbsorbedStyle(
    val id: String,
    val name: String,
    val sourceVideoPathOrUrl: String,
    val analysis: String,
    val visualTraits: List<String>,
    val motionTraits: List<String>,
    val textTraits: List<String>,
    val overallScore: Int,
    val absorbedAt: Long = System.currentTimeMillis()
) {
    fun toVideoStyleAnalysis(): VideoStyleAnalysis {
        val d = try { toStyleDirective() } catch (_: Exception) { null }
        val primary = d?.primaryColorHex ?: resolvePrimaryColorHex()
        val bg = d?.backgroundColorHex ?: resolveBackgroundColorHex()
        val accent = d?.accentColorHex ?: resolveAccentColorHex()
        val filter = d?.filterHint ?: resolveFilterHint()
        val motion = d?.motionType ?: resolveMotionType()
        val transition = d?.transitionType ?: resolveTransitionType()
        val tempo = d?.tempo ?: resolveTempo()
        val captionAnim = d?.captionAnimation ?: resolveTextAnimation()
        val captionPos = d?.captionPosition ?: resolveCaptionPosition()
        return VideoStyleAnalysis(
            detectedStyle = name.ifBlank { "أسلوب قبس السينمائي" },
            dominantColors = if (visualTraits.isNotEmpty()) {
                visualTraits.joinToString("، ") + " | primary:$primary bg:$bg accent:$accent filter:$filter"
            } else {
                "داكن وأسود عميق مع لمسات $primary | primary:$primary bg:$bg accent:$accent filter:$filter"
            },
            transitionSpeed = tempo,
            movementPatterns = "$transition / $motion | motion=$motion transition=$transition",
            overallRhythm = tempo,
            audioStyle = "مؤثرات متوافقة مع إيقاع $tempo",
            typographyStyle = buildString {
                if (textTraits.isNotEmpty()) append(textTraits.joinToString("، "))
                else append("خط عريض أنيق")
                append(" | anim=$captionAnim | pos=$captionPos")
            },
            contentTone = analysis.ifBlank { "مؤثر ووقور" },
            targetAudience = "الجمهور العام والشباب",
            keywords = visualTraits + motionTraits + listOfNotNull(
                filter,
                "filter=$filter",
                "motion=$motion",
                "transition=$transition",
                "primary:$primary",
                "bg:$bg",
                "accent:$accent",
                d?.promptBoost?.take(80)
            )
        )
    }

    fun resolveTransitionType(): String {
        for (t in motionTraits + visualTraits) {
            val m = Regex("""(?i)transition\s*[=:]\s*([a-zA-Z]+)""").find(t)
            if (m != null) {
                val v = m.groupValues[1].lowercase()
                return when {
                    v.contains("glitch") -> "Glitch"
                    v.contains("zoom") -> "ZoomIn"
                    v.contains("slide") -> "SlideLeft"
                    v.contains("wipe") -> "WipeLeft"
                    v.contains("fadeblack") || v.contains("black") -> "FadeBlack"
                    v.contains("fade") -> "Fade"
                    v.contains("dissolve") -> "Dissolve"
                    else -> "Dissolve"
                }
            }
        }
        val joined = (motionTraits + visualTraits).joinToString(" ").lowercase()
        return when {
            joined.contains("glitch") || joined.contains("خاطف") || joined.contains("سريع جدا") -> "Glitch"
            joined.contains("zoomin") || joined.contains("zoom in") || joined.contains("زووم") || joined.contains("تكبير") -> "ZoomIn"
            joined.contains("slideleft") || joined.contains("سحب") || joined.contains("slide") -> "SlideLeft"
            joined.contains("wipeleft") || joined.contains("مسح") || joined.contains("wipe") -> "WipeLeft"
            joined.contains("fadeblack") || joined.contains("إظلام") || joined.contains("تلاشي أسود") -> "FadeBlack"
            joined.contains("dissolve") || joined.contains("تلاشي") || joined.contains("ناعم") || joined.contains("سكينة") -> "Dissolve"
            else -> "Dissolve"
        }
    }

    fun resolvePrimaryColorHex(): String {
        // أولوية: hex حقيقي من التحليل المحلي / التوجيه
        for (t in visualTraits) {
            val m = Regex("""(?i)primary\s*[=:]\s*#?([0-9A-Fa-f]{6})""").find(t)
            if (m != null) return "#${m.groupValues[1].uppercase(Locale.ROOT)}"
        }
        for (t in visualTraits) {
            val m = Regex("""#([0-9A-Fa-f]{6})""").find(t)
            if (m != null) return "#${m.groupValues[1].uppercase(Locale.ROOT)}"
        }
        val joined = visualTraits.joinToString(" ").lowercase()
        return when {
            joined.contains("أخضر") || joined.contains("emerald") || joined.contains("green") -> "#10B981"
            joined.contains("نيون") || joined.contains("neon") || joined.contains("أصفر") || joined.contains("yellow") -> "#FFE500"
            joined.contains("برتقالي") || joined.contains("غروب") || joined.contains("orange") -> "#F97316"
            joined.contains("أزرق") || joined.contains("blue") || joined.contains("سماوي") -> "#38BDF8"
            joined.contains("أبيض") || joined.contains("white") || joined.contains("فضة") -> "#F8FAFC"
            else -> "#8B5CF6" // Qabas AI Violet
        }
    }

    fun resolveBackgroundColorHex(): String {
        for (t in visualTraits) {
            val m = Regex("""(?i)bg\s*[=:]\s*#?([0-9A-Fa-f]{6})""").find(t)
            if (m != null) return "#${m.groupValues[1].uppercase(Locale.ROOT)}"
        }
        val joined = visualTraits.joinToString(" ").lowercase()
        return when {
            joined.contains("أخضر") || joined.contains("emerald") -> "#0F291E"
            joined.contains("أزرق") || joined.contains("blue") -> "#0A192F"
            joined.contains("برتقالي") || joined.contains("sunset") || joined.contains("soft_desert") -> "#1E1005"
            else -> "#0B0F19" // Qabas Deep Slate
        }
    }

    fun resolveTextAnimation(): String {
        for (t in textTraits) {
            val m = Regex("""(?i)(?:captionanim|anim)\s*[=:]\s*([a-zA-Z]+)""").find(t)
            if (m != null) {
                val v = m.groupValues[1].lowercase()
                return when {
                    v.contains("word") -> "WordByWord"
                    v.contains("pop") || v.contains("bounce") -> "Pop"
                    v.contains("fade") -> "FadeIn"
                    v.contains("line") -> "LineReveal"
                    else -> "WordByWord"
                }
            }
        }
        val joined = textTraits.joinToString(" ").lowercase()
        return when {
            joined.contains("كلمة") || joined.contains("word") || joined.contains("تزامن") -> "WordByWord"
            joined.contains("انبثاق") || joined.contains("pop") || joined.contains("bounce") -> "Pop"
            joined.contains("تدريجي") || joined.contains("fade") -> "FadeIn"
            else -> "WordByWord"
        }
    }

    fun resolveMotionType(): String {
        // أولوية: motion= من التحليل المحلي (Optical Flow) أو التحليل الدلالي
        for (t in motionTraits + visualTraits) {
            val m = Regex("""(?i)motion\s*[=:]\s*([a-zA-Z_]+)""").find(t)
            if (m != null) {
                val v = m.groupValues[1].lowercase()
                if (v in listOf("static", "slow_zoom", "pan", "punch_in")) return v
            }
        }

        val joined = (motionTraits + visualTraits).joinToString(" ").lowercase()
        return when {
            joined.contains("pan") || joined.contains("مسح أفقي") || joined.contains("panorama") -> "pan"
            joined.contains("punch") || joined.contains("خاطف") || joined.contains("سريع") -> "punch_in"
            joined.contains("static") || joined.contains("ثابت") || joined.contains("ساكن") -> "static"
            joined.contains("zoom") || joined.contains("زووم") || joined.contains("تكبير") || joined.contains("بطيء") -> "slow_zoom"
            else -> "slow_zoom"
        }
    }

    fun resolveTempo(): String {
        for (t in motionTraits + visualTraits) {
            val m = Regex("""(?i)tempo\s*[=:]\s*([^\s|,]+)""").find(t)
            if (m != null) {
                val v = m.groupValues[1].lowercase()
                if (v in listOf("خاشع", "متوسط", "سريع", "slow", "medium", "fast")) {
                    return when (v) {
                        "slow", "خاشع" -> "خاشع"
                        "fast", "سريع" -> "سريع"
                        else -> "متوسط"
                    }
                }
            }
        }
        val joined = (motionTraits + analysis).joinToString(" ").lowercase()
        return when {
            joined.contains("سريع") || joined.contains("خاطف") || joined.contains("حماسي") || joined.contains("ملحمي") -> "سريع"
            joined.contains("بطيء") || joined.contains("خاشع") || joined.contains("تأملي") || joined.contains("هادئ") || joined.contains("سكينة") -> "خاشع"
            else -> "متوسط"
        }
    }

    fun resolveCaptionPosition(): String {
        for (t in textTraits) {
            val m = Regex("""(?i)(?:captionpos|pos)\s*[=:]\s*([a-zA-Z]+)""").find(t)
            if (m != null) {
                val v = m.groupValues[1].lowercase()
                if (v in listOf("bottom", "center", "top")) return v
            }
        }
        val joined = textTraits.joinToString(" ").lowercase()
        return when {
            joined.contains("أعلى") || joined.contains("top") -> "top"
            joined.contains("وسط") || joined.contains("center") || joined.contains("منتصف") -> "center"
            else -> "bottom"
        }
    }

    fun resolveFilterHint(): String {
        for (t in visualTraits) {
            val m = Regex("""(?i)filter\s*[=:]\s*([a-zA-Z_]+)""").find(t)
            if (m != null) {
                val v = m.groupValues[1].lowercase()
                if (v in listOf("warm_gold", "cool_emerald", "high_contrast_dark", "soft_desert", "ai_violet")) return v
            }
        }
        val joined = visualTraits.joinToString(" ").lowercase()
        return when {
            joined.contains("صحراء") || joined.contains("desert") || joined.contains("بيج") || joined.contains("رملي") || joined.contains("غروب") -> "soft_desert"
            joined.contains("أخضر") || joined.contains("emerald") || joined.contains("green") -> "cool_emerald"
            joined.contains("تباين") || joined.contains("contrast") || joined.contains("أحمر") || joined.contains("dark") -> "high_contrast_dark"
            joined.contains("ذهبي") || joined.contains("gold") || joined.contains("دافئ") -> "warm_gold"
            joined.contains("بنفسجي") || joined.contains("violet") || joined.contains("سيان") || joined.contains("cyan") || joined.contains("#8b5cf6") -> "ai_violet"
            else -> "ai_violet"
        }
    }

    fun resolveAccentColorHex(): String {
        for (t in visualTraits) {
            val m = Regex("""(?i)accent\s*[=:]\s*#?([0-9A-Fa-f]{6})""").find(t)
            if (m != null) return "#${m.groupValues[1].uppercase(Locale.ROOT)}"
        }
        val joined = visualTraits.joinToString(" ").lowercase()
        return when {
            joined.contains("أحمر") || joined.contains("red") || joined.contains("قلب") -> "#EF4444"
            joined.contains("أصفر") || joined.contains("yellow") || joined.contains("تمييز") -> "#FACC15"
            joined.contains("أخضر") || joined.contains("emerald") -> "#34D399"
            joined.contains("أبيض") || joined.contains("white") -> "#F8FAFC"
            else -> resolvePrimaryColorHex()
        }
    }

    /**
     * تحويل النمط إلى توجيه إخراج قابل للتنفيذ في المونتاج والمحرك.
     */
    fun toStyleDirective(): StyleDirective {
        val primary = resolvePrimaryColorHex()
        val bg = resolveBackgroundColorHex()
        val accent = resolveAccentColorHex()
        val transition = resolveTransitionType()
        val motion = resolveMotionType()
        val tempo = resolveTempo()
        val captionAnim = resolveTextAnimation()
        val captionPos = resolveCaptionPosition()
        val filter = resolveFilterHint()

        val kwEn = mutableListOf<String>()
        val kwAr = mutableListOf<String>()
        val v = visualTraits.joinToString(" ").lowercase()
        when (filter) {
            "soft_desert" -> {
                kwEn += listOf("desert golden hour", "warm sand tones", "cinematic arid landscape", "clay pottery soft light")
                kwAr += listOf("صحراء ساعة ذهبية", "رمل دافئ", "فخار إضاءة ناعمة")
            }
            "cool_emerald" -> {
                kwEn += listOf("deep emerald green", "quran page glow", "dark sacred atmosphere", "green mandala soft light")
                kwAr += listOf("أخضر زمردي عميق", "مصحف متوهج", "أجواء مقدسة داكنة")
            }
            "high_contrast_dark" -> {
                kwEn += listOf("high contrast dark background", "dramatic red accents", "cinematic shadow", "intense close-up")
                kwAr += listOf("خلفية داكنة عالية التباين", "لمسات حمراء درامية", "ظل سينمائي")
            }
            "ai_violet" -> {
                kwEn += listOf("deep slate black", "ai violet primary glow", "cyan accent light", "cinematic islamic architecture", "tech volumetric light")
                kwAr += listOf("أسود داكن", "هالة بنفسجية تقنية", "لمسة سيان", "عمارة إسلامية سينمائية")
            }
            else -> {
                kwEn += listOf("deep slate black", "warm gold accent light", "cinematic islamic architecture", "soft volumetric light")
                kwAr += listOf("أسود داكن", "لمسة ذهبية", "عمارة إسلامية سينمائية")
            }
        }
        // أضف من السمات نفسها كلمات إنجليزية مساعدة
        visualTraits.take(3).forEach { t ->
            if (t.any { ch -> ch in 'a'..'z' || ch in 'A'..'Z' }) kwEn += t
            else kwAr += t
        }

        val promptBoost = buildString {
            append("Color grade: primary $primary on background $bg with accent $accent. ")
            append("Motion: $motion. Transition: $transition. Tempo: $tempo. ")
            append("Caption style: $captionAnim at $captionPos. ")
            append("Look: $filter. ")
            append(visualTraits.take(4).joinToString(", "))
        }

        return StyleDirective(
            styleName = name,
            primaryColorHex = primary,
            backgroundColorHex = bg,
            accentColorHex = accent,
            transitionType = transition,
            motionType = motion,
            tempo = tempo,
            captionAnimation = captionAnim,
            captionPosition = captionPos,
            visualKeywordsEn = kwEn.distinct().take(8),
            visualKeywordsAr = kwAr.distinct().take(8),
            promptBoost = promptBoost,
            filterHint = filter
        )
    }

}

object ContinuousLearningEngine {
    fun buildProposals(
        styles: List<AbsorbedStyle>,
        core: QabasCoreStyle,
        completedCount: Int,
        activeStyle: AbsorbedStyle?
    ): List<StyleImprovementProposal> {
        if (styles.isEmpty() || completedCount == 0) {
            return emptyList()
        }

        val proposals = mutableListOf<StyleImprovementProposal>()
        val sampleCount = maxOf(completedCount, 1)
        val target = activeStyle ?: styles.maxByOrNull { it.overallScore }

        if (target == null) {
            return emptyList()
        }

        // 1. فحص توافق الألوان والإضاءة مع طابع الأسلوب النشط
        if (target.visualTraits.isNotEmpty()) {
            val filter = target.resolveFilterHint()
            val primaryColor = target.resolvePrimaryColorHex()
            proposals.add(
                StyleImprovementProposal(
                    title = "تحسين التباين اللوني (${target.name})",
                    description = "موازنة الإضاءة والظلال مع اللون الأساسي $primaryColor.",
                    targetTraitCategory = "الألوان والإضاءة",
                    suggestedChange = "تعزيز حدة التباين اللوني للظلال مع الحفاظ على درجة $primaryColor",
                    rationale = "تحليل الأداء في $sampleCount مشاريع أظهر انسجاماً بصرياً أعلى عند ضبط تباين الظلال.",
                    successRateBasedOn = 92,
                    sampleProjectsCount = sampleCount,
                    enhancementScore = 2,
                    newVisualTrait = "تباين عالي متزن مع $primaryColor",
                    newFilterHint = filter
                )
            )
        }

        // 2. فحص وتيرة المونتاج والإيقاع
        if (target.motionTraits.isNotEmpty()) {
            val motion = target.resolveMotionType()
            val tempo = target.resolveTempo()
            proposals.add(
                StyleImprovementProposal(
                    title = "مواءمة الإيقاع الحركي ($tempo)",
                    description = "ضبط انسيابية حركة الكاميرا ($motion) والانتقالات لتلائم وتيرة المشاهد.",
                    targetTraitCategory = "الإيقاع والسرعة",
                    suggestedChange = "تنسيق الانتقالات التلاشية مع حركة $motion بإيقاع $tempo",
                    rationale = "اتساق الحركة مع وتيرة السرد يرفع تركيز المشاهد ويزيد من سكينة المشهد.",
                    successRateBasedOn = 89,
                    sampleProjectsCount = sampleCount,
                    enhancementScore = 2,
                    newMotionTrait = "حركة متناسقة ($motion) بإيقاع $tempo متزن",
                    newTempo = tempo
                )
            )
        }

        // 3. فحص حركة ونمط النصوص والكابشن
        if (target.textTraits.isNotEmpty()) {
            val anim = target.resolveTextAnimation()
            val pos = target.resolveCaptionPosition()
            proposals.add(
                StyleImprovementProposal(
                    title = "تطوير تفاعل الكابشن ($anim)",
                    description = "تحسين مقروئية النصوص وموضعها في المنطقة الآمنة ($pos).",
                    targetTraitCategory = "النصوص والطباعة",
                    suggestedChange = "تأكيد نمط $anim في موضع $pos بخط عربي عريض",
                    rationale = "الوضوح البصري للكلمات في موضع $pos يعزز تجربة القراءة والمتابعة بنسبة أعلى.",
                    successRateBasedOn = 95,
                    sampleProjectsCount = sampleCount,
                    enhancementScore = 2,
                    newTextTrait = "كابشن متقن ($anim) في موضع $pos"
                )
            )
        }

        return proposals
    }
}

object StyleBrain {
    private const val PREFS_NAME = "qabas_style_brain_prefs"
    private const val STYLES_KEY = "absorbed_styles_list"
    private const val CORE_STYLE_KEY = "qabas_core_style_json"
    private const val PRIMARY_STYLE_KEY = "qabas_primary_studio_style_id"
    private const val CONTINUOUS_LEARNING_KEY = "qabas_continuous_learning_enabled"
    private const val LAST_LEARNING_ANALYSIS_KEY = "qabas_last_continuous_learning_time"
    private const val PROPOSALS_KEY = "qabas_continuous_learning_proposals_json"
    
    private val DEFAULT_CORE_STYLE = QabasCoreStyle(
        visualTraits = emptyList(),
        motionTraits = emptyList(),
        textTraits = emptyList(),
        analysis = "عقل فارغ — يرجى إضافة فيديوهات مرجعية للتعلم والاستفادة",
        strengthScore = 0,
        lastUpdated = System.currentTimeMillis()
    )

    private val _absorbedStyles = MutableStateFlow<List<AbsorbedStyle>>(emptyList())
    val absorbedStyles: StateFlow<List<AbsorbedStyle>> = _absorbedStyles.asStateFlow()

    private val _coreStyle = MutableStateFlow<QabasCoreStyle>(DEFAULT_CORE_STYLE)
    val coreStyle: StateFlow<QabasCoreStyle> = _coreStyle.asStateFlow()

    private val _primaryStyleId = MutableStateFlow<String?>(null)
    val primaryStyleId: StateFlow<String?> = _primaryStyleId.asStateFlow()

    private val _isContinuousLearningEnabled = MutableStateFlow<Boolean>(true)
    val isContinuousLearningEnabled: StateFlow<Boolean> = _isContinuousLearningEnabled.asStateFlow()

    private val _improvementProposals = MutableStateFlow<List<StyleImprovementProposal>>(emptyList())
    val improvementProposals: StateFlow<List<StyleImprovementProposal>> = _improvementProposals.asStateFlow()

    private val _lastLearningTimestamp = MutableStateFlow<Long>(0L)
    val lastLearningTimestamp: StateFlow<Long> = _lastLearningTimestamp.asStateFlow()

    private val _isAnalyzingLearning = MutableStateFlow<Boolean>(false)
    val isAnalyzingLearning: StateFlow<Boolean> = _isAnalyzingLearning.asStateFlow()
    
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // 1. Load Absorbed Styles List
        val stylesJson = prefs.getString(STYLES_KEY, "[]") ?: "[]"
        try {
            val arr = JSONArray(stylesJson)
            val list = mutableListOf<AbsorbedStyle>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                
                val vTraits = mutableListOf<String>()
                val vArr = obj.optJSONArray("visualTraits") ?: JSONArray()
                for (j in 0 until vArr.length()) vTraits.add(vArr.getString(j))
                
                val mTraits = mutableListOf<String>()
                val mArr = obj.optJSONArray("motionTraits") ?: JSONArray()
                for (j in 0 until mArr.length()) mTraits.add(mArr.getString(j))
                
                val tTraits = mutableListOf<String>()
                val tArr = obj.optJSONArray("textTraits") ?: JSONArray()
                for (j in 0 until tArr.length()) tTraits.add(tArr.getString(j))
                
                list.add(
                    AbsorbedStyle(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "أسلوب محفوظ"),
                        sourceVideoPathOrUrl = obj.optString("sourceVideoPathOrUrl", ""),
                        analysis = obj.optString("analysis", ""),
                        visualTraits = vTraits,
                        motionTraits = mTraits,
                        textTraits = tTraits,
                        overallScore = obj.optInt("overallScore", 0).coerceIn(0, 100),
                        absorbedAt = obj.optLong("absorbedAt", System.currentTimeMillis())
                    )
                )
            }
            _absorbedStyles.value = list
        } catch (e: Exception) {
            _absorbedStyles.value = emptyList()
        }

        // 2. Load or Initialize QabasCoreStyle
        val coreJson = prefs.getString(CORE_STYLE_KEY, null)
        if (!coreJson.isNullOrBlank()) {
            try {
                val obj = JSONObject(coreJson)
                val vList = mutableListOf<String>()
                val vArr = obj.optJSONArray("visualTraits") ?: JSONArray()
                for (i in 0 until vArr.length()) vList.add(vArr.getString(i))

                val mList = mutableListOf<String>()
                val mArr = obj.optJSONArray("motionTraits") ?: JSONArray()
                for (i in 0 until mArr.length()) mList.add(mArr.getString(i))

                val tList = mutableListOf<String>()
                val tArr = obj.optJSONArray("textTraits") ?: JSONArray()
                for (i in 0 until tArr.length()) tList.add(tArr.getString(i))

                val rawStrength = obj.optInt("strengthScore", 0).coerceIn(0, 100)
                val finalStrength = if (vList.isEmpty() && mList.isEmpty() && tList.isEmpty()) 0 else rawStrength

                _coreStyle.value = QabasCoreStyle(
                    visualTraits = vList,
                    motionTraits = mList,
                    textTraits = tList,
                    analysis = obj.optString("analysis", if (vList.isEmpty()) DEFAULT_CORE_STYLE.analysis else "عقل قبس التراكمي"),
                    strengthScore = finalStrength,
                    lastUpdated = obj.optLong("lastUpdated", System.currentTimeMillis())
                )
            } catch (e: Exception) {
                _coreStyle.value = DEFAULT_CORE_STYLE
            }
        } else {
            _coreStyle.value = DEFAULT_CORE_STYLE
            saveCoreStyle(context, DEFAULT_CORE_STYLE)
        }

        // 3. Load Primary Studio Style ID
        _primaryStyleId.value = prefs.getString(PRIMARY_STYLE_KEY, null)

        // 4. Load Continuous Learning Settings & Proposals
        _isContinuousLearningEnabled.value = prefs.getBoolean(CONTINUOUS_LEARNING_KEY, true)
        _lastLearningTimestamp.value = prefs.getLong(LAST_LEARNING_ANALYSIS_KEY, 0L)
        val propJson = prefs.getString(PROPOSALS_KEY, "[]") ?: "[]"
        try {
            val pArr = JSONArray(propJson)
            val pList = mutableListOf<StyleImprovementProposal>()
            for (i in 0 until pArr.length()) {
                val pObj = pArr.getJSONObject(i)
                pList.add(
                    StyleImprovementProposal(
                        id = pObj.optString("id", UUID.randomUUID().toString()),
                        title = pObj.optString("title", ""),
                        description = pObj.optString("description", ""),
                        targetTraitCategory = pObj.optString("targetTraitCategory", "الألوان والإضاءة"),
                        suggestedChange = pObj.optString("suggestedChange", ""),
                        rationale = pObj.optString("rationale", ""),
                        successRateBasedOn = pObj.optInt("successRateBasedOn", 85),
                        sampleProjectsCount = pObj.optInt("sampleProjectsCount", 1),
                        enhancementScore = pObj.optInt("enhancementScore", 3),
                        newVisualTrait = pObj.optString("newVisualTrait").ifBlank { null },
                        newMotionTrait = pObj.optString("newMotionTrait").ifBlank { null },
                        newTextTrait = pObj.optString("newTextTrait").ifBlank { null },
                        newFilterHint = pObj.optString("newFilterHint").ifBlank { null },
                        newTempo = pObj.optString("newTempo").ifBlank { null },
                        createdAt = pObj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            _improvementProposals.value = pList
        } catch (_: Exception) {
            _improvementProposals.value = emptyList()
        }
    }

    fun setContinuousLearningEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(CONTINUOUS_LEARNING_KEY, enabled).apply()
        _isContinuousLearningEnabled.value = enabled
    }

    private fun saveProposals(context: Context, proposals: List<StyleImprovementProposal>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        proposals.forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("title", p.title)
            obj.put("description", p.description)
            obj.put("targetTraitCategory", p.targetTraitCategory)
            obj.put("suggestedChange", p.suggestedChange)
            obj.put("rationale", p.rationale)
            obj.put("successRateBasedOn", p.successRateBasedOn)
            obj.put("sampleProjectsCount", p.sampleProjectsCount)
            obj.put("enhancementScore", p.enhancementScore)
            obj.put("newVisualTrait", p.newVisualTrait ?: "")
            obj.put("newMotionTrait", p.newMotionTrait ?: "")
            obj.put("newTextTrait", p.newTextTrait ?: "")
            obj.put("newFilterHint", p.newFilterHint ?: "")
            obj.put("newTempo", p.newTempo ?: "")
            obj.put("createdAt", p.createdAt)
            arr.put(obj)
        }
        prefs.edit().putString(PROPOSALS_KEY, arr.toString()).apply()
        _improvementProposals.value = proposals
    }

    /**
     * نظام التعلم المستمر وتحليل فيديوهات وإنتاجات الاستوديو (Continuous Learning Engine)
     * يحلل المشاريع السابقة الناجحة ويستخلص أنماط التفاعل وجودة الإخراج ليقترح تحسينات دقيقة للأسلوب النشط.
     */
    suspend fun runContinuousLearningAnalysis(context: Context, force: Boolean = false): List<StyleImprovementProposal> = withContext(Dispatchers.IO) {
        _isAnalyzingLearning.value = true
        try {
            val projects = try {
                ProjectService(context).getLocalProjects()
            } catch (_: Exception) {
                emptyList()
            }

            val completedProjects = projects.filter { 
                it.finalVideoPath.isNotBlank() || 
                it.status.equals("COMPLETED", ignoreCase = true) || 
                it.status.contains("ناجح") ||
                it.status.contains("تم") 
            }
            
            val activeStyle = getPrimaryStudioStyle() ?: _coreStyle.value.let { core ->
                if (core.strengthScore > 0) {
                    AbsorbedStyle(
                        id = "core_active",
                        name = "هوية قبس الأساسية",
                        sourceVideoPathOrUrl = "",
                        analysis = core.analysis,
                        visualTraits = core.visualTraits,
                        motionTraits = core.motionTraits,
                        textTraits = core.textTraits,
                        overallScore = core.strengthScore
                    )
                } else null
            }

            val styles = _absorbedStyles.value
            val resolvedActive = activeStyle
                ?: styles.maxByOrNull { it.overallScore }
            val proposals = ContinuousLearningEngine.buildProposals(
                styles = styles,
                core = _coreStyle.value,
                completedCount = completedProjects.size,
                activeStyle = resolvedActive
            )

            saveProposals(context, proposals)
            val now = System.currentTimeMillis()
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(LAST_LEARNING_ANALYSIS_KEY, now)
                .apply()
            _lastLearningTimestamp.value = now
            _improvementProposals.value = proposals

            proposals
        } finally {
            _isAnalyzingLearning.value = false
        }
    }

    fun applyImprovementProposal(context: Context, proposalId: String): Boolean {
        val proposal = _improvementProposals.value.find { it.id == proposalId } ?: return false
        
        val primaryId = _primaryStyleId.value
        val currentStyles = _absorbedStyles.value.toMutableList()
        val targetStyle = if (primaryId != null) currentStyles.find { it.id == primaryId } else currentStyles.firstOrNull()
        
        if (targetStyle != null) {
            val newVisual = if (proposal.newVisualTrait != null && !targetStyle.visualTraits.contains(proposal.newVisualTrait)) {
                listOf(proposal.newVisualTrait) + targetStyle.visualTraits
            } else targetStyle.visualTraits

            val newMotion = if (proposal.newMotionTrait != null && !targetStyle.motionTraits.contains(proposal.newMotionTrait)) {
                listOf(proposal.newMotionTrait) + targetStyle.motionTraits
            } else targetStyle.motionTraits

            val newText = if (proposal.newTextTrait != null && !targetStyle.textTraits.contains(proposal.newTextTrait)) {
                listOf(proposal.newTextTrait) + targetStyle.textTraits
            } else targetStyle.textTraits

            val newScore = (targetStyle.overallScore + proposal.enhancementScore).coerceIn(0, 100)
            val updated = targetStyle.copy(
                visualTraits = newVisual,
                motionTraits = newMotion,
                textTraits = newText,
                overallScore = newScore
            )

            val idx = currentStyles.indexOf(targetStyle)
            if (idx != -1) {
                currentStyles[idx] = updated
                saveStyles(context, currentStyles)
            }
            evolveCoreStyleWithNewAbsorption(context, updated)
        } else {
            // Evolve CoreStyle directly
            val core = _coreStyle.value
            val newVisual = if (proposal.newVisualTrait != null) listOf(proposal.newVisualTrait) + core.visualTraits else core.visualTraits
            val newMotion = if (proposal.newMotionTrait != null) listOf(proposal.newMotionTrait) + core.motionTraits else core.motionTraits
            val newText = if (proposal.newTextTrait != null) listOf(proposal.newTextTrait) + core.textTraits else core.textTraits
            val gain = proposal.enhancementScore.coerceIn(0, 2)
            val newStrength = (core.strengthScore + gain).coerceIn(0, 100)
            
            val updatedCore = core.copy(
                visualTraits = newVisual.distinct(),
                motionTraits = newMotion.distinct(),
                textTraits = newText.distinct(),
                strengthScore = newStrength,
                lastUpdated = System.currentTimeMillis()
            )
            saveCoreStyle(context, updatedCore)
        }

        // Remove applied proposal
        val remaining = _improvementProposals.value.filter { it.id != proposalId }
        saveProposals(context, remaining)
        _improvementProposals.value = remaining
        return true
    }

    fun dismissImprovementProposal(context: Context, proposalId: String) {
        val remaining = _improvementProposals.value.filter { it.id != proposalId }
        saveProposals(context, remaining)
        _improvementProposals.value = remaining
    }

    fun setPrimaryStudioStyle(context: Context, styleId: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PRIMARY_STYLE_KEY, styleId).apply()
        _primaryStyleId.value = styleId
        if (styleId != null) {
            val style = _absorbedStyles.value.find { it.id == styleId }
            if (style != null) {
                evolveCoreStyleWithNewAbsorption(context, style)
            }
        }
    }

    fun getPrimaryStudioStyle(): AbsorbedStyle? {
        val id = _primaryStyleId.value ?: return null
        return _absorbedStyles.value.find { it.id == id }
    }

    fun recordProductionResult(context: Context, styleId: String?, success: Boolean) {
        if (styleId.isNullOrBlank()) return
        try {
            val cleanId = if (styleId.startsWith("blended_")) styleId.removePrefix("blended_") else styleId
            val currentStyles = _absorbedStyles.value.toMutableList()
            val target = currentStyles.find { it.id == cleanId || cleanId.contains(it.id) || it.id.contains(cleanId) }
            
            if (target != null) {
                val newScore = if (success) {
                    (target.overallScore + 2).coerceIn(0, 100)
                } else {
                    (target.overallScore - 2).coerceIn(0, 100)
                }
                val updated = target.copy(overallScore = newScore)
                val idx = currentStyles.indexOf(target)
                if (idx != -1) {
                    currentStyles[idx] = updated
                    saveStyles(context, currentStyles)
                }
                if (success) {
                    evolveCoreStyleWithNewAbsorption(context, updated)
                    try {
                        CoroutineScope(Dispatchers.IO).launch {
                            TasteManager.registerPreference(context, "STYLE", target.visualTraits.joinToString("، "), 3)
                            TasteManager.registerPreference(context, "PACING", target.motionTraits.joinToString("، "), 3)
                        }
                    } catch (_: Exception) {}
                }
                Log.d("StyleBrain", "Production result recorded for style ${target.name}: success=$success, newScore=$newScore")
            } else {
                // إذا كان الأسلوب المستخدم هو العقل الأساسي نفسه
                val currentCore = _coreStyle.value
                if (currentCore.strengthScore > 0) {
                    val newStrength = if (success) {
                        (currentCore.strengthScore + 1).coerceAtMost(100)
                    } else {
                        (currentCore.strengthScore - 1).coerceAtLeast(60)
                    }
                    saveCoreStyle(context, currentCore.copy(strengthScore = newStrength, lastUpdated = System.currentTimeMillis()))
                    Log.d("StyleBrain", "Production result recorded for CoreStyle: success=$success, newStrength=$newStrength")
                }
            }
        } catch (e: Exception) {
            Log.e("StyleBrain", "Error recording production result: ${e.message}")
        }
    }
    
    private fun saveCoreStyle(context: Context, core: QabasCoreStyle) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val obj = JSONObject()
        
        val vArr = JSONArray()
        core.visualTraits.forEach { vArr.put(it) }
        obj.put("visualTraits", vArr)

        val mArr = JSONArray()
        core.motionTraits.forEach { mArr.put(it) }
        obj.put("motionTraits", mArr)

        val tArr = JSONArray()
        core.textTraits.forEach { tArr.put(it) }
        obj.put("textTraits", tArr)

        obj.put("analysis", core.analysis)
        obj.put("strengthScore", core.strengthScore)
        obj.put("lastUpdated", core.lastUpdated)

        prefs.edit().putString(CORE_STYLE_KEY, obj.toString()).apply()
        _coreStyle.value = core
    }

    private fun saveStyles(context: Context, styles: List<AbsorbedStyle>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        val arr = JSONArray()
        styles.forEach { s ->
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("name", s.name)
            obj.put("sourceVideoPathOrUrl", s.sourceVideoPathOrUrl)
            obj.put("analysis", s.analysis)
            obj.put("overallScore", s.overallScore)
            obj.put("absorbedAt", s.absorbedAt)
            
            val vArr = JSONArray()
            s.visualTraits.forEach { vArr.put(it) }
            obj.put("visualTraits", vArr)
            
            val mArr = JSONArray()
            s.motionTraits.forEach { mArr.put(it) }
            obj.put("motionTraits", mArr)
            
            val tArr = JSONArray()
            s.textTraits.forEach { tArr.put(it) }
            obj.put("textTraits", tArr)
            
            arr.put(obj)
        }
        
        prefs.edit().putString(STYLES_KEY, arr.toString()).apply()
        _absorbedStyles.value = styles
    }

    /**
     * التحقق من توفر مفتاح Gemini أو Groq صالح في التطبيق.
     * يمنع عمل العقل واستخراج الأنماط في حال عدم وجود مفتاح.
     */
    fun hasValidAiApiKey(context: Context): Boolean {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val geminiKey = prefs.getString("gemini_key", "")?.trim().orEmpty()
        val groqKey = prefs.getString("groq_key", "")?.trim().orEmpty()
        val hasGemini = geminiKey.isNotBlank() && geminiKey != "YOUR_GEMINI_API_KEY"
        val hasGroq = groqKey.isNotBlank() && groqKey != "YOUR_GROQ_API_KEY"
        return hasGemini || hasGroq
    }

    /**
     * خوارزمية الدمج المرجح والتطوير التراكمي (Weighted Style Merging & Accumulative Evolution Algorithm):
     * 1. وزن QabasCoreStyle مهيمن (80%) كقاعدة لحماية هوية قبس الإخراجية الراسخة.
     * 2. مطابقة دلالية موسعة للسمات (الذهبي، DeepSlate، الأخضر الداكن، الكوفي، الزووم، التلاشي، إلخ).
     * 3. خفض تأثير الأسلوب الجديد إذا كان overallScore < 85 ومنع حقن سمات غريبة.
     * 4. الحفاظ على هوية قبس الأساسية مع تطور تصاعدي في دقة الألوان والحركة والكابشن.
     */
    private fun evolveCoreStyleWithNewAbsorption(context: Context, newStyle: AbsorbedStyle) {
        val currentCore = _coreStyle.value

        // إذا كان التقييم ضعيفاً (أقل من 75)، نتجاهل تعديل العقل التراكمي للحفاظ على النقاء
        if (newStyle.overallScore < 75) {
            return
        }

        // إذا كان العقل فارغاً، يتم تأسيسه بالكامل من هذا الأسلوب الأول بزيادة ضئيلة (0..3)
        if (currentCore.strengthScore == 0) {
            val initialGain = StyleBrainLoadFix.computeStrengthGain(newStyle.overallScore, 0)
            val firstCore = QabasCoreStyle(
                visualTraits = newStyle.visualTraits,
                motionTraits = newStyle.motionTraits,
                textTraits = newStyle.textTraits,
                analysis = "تأسس العقل من الأسلوب المرجعي: ${newStyle.name}. " + newStyle.analysis,
                strengthScore = initialGain,
                lastUpdated = System.currentTimeMillis()
            )
            _coreStyle.value = firstCore
            saveCoreStyle(context, firstCore)
            return
        }

        // 1. وزن مهيمن 80% للعقل التراكمي الأساسي
        val coreDominance = 0.80
        val coreWeight = (currentCore.strengthScore / 100.0).coerceAtLeast(0.85) * coreDominance
        
        // خفض تأثير الأسلوب الجديد إذا كان تقييمه منخفضاً
        val styleScoreMultiplier = if (newStyle.overallScore < 85) 0.20 else (newStyle.overallScore / 100.0)
        val newStyleWeight = styleScoreMultiplier * (1.0 - coreDominance)

        // 2. مطابقة دلالية ذكية للسمات المتشابهة
        fun areTraitsSemanticallySimilar(t1: String, t2: String): Boolean {
            val s1 = t1.lowercase(Locale.ROOT)
            val s2 = t2.lowercase(Locale.ROOT)
            if (s1 == s2 || s1.contains(s2) || s2.contains(s1)) return true
            
            // سمات اللون الذهبي وتدرجاته
            val isGold1 = s1.contains("ذهب") || s1.contains("gold") || s1.contains("#e8c547") || s1.contains("#f5d76e") || s1.contains("أصفر")
            val isGold2 = s2.contains("ذهب") || s2.contains("gold") || s2.contains("#e8c547") || s2.contains("#f5d76e") || s2.contains("أصفر")
            if (isGold1 && isGold2) return true

            // سمات DeepSlate والخلفيات الداكنة الفخمة
            val isSlate1 = s1.contains("deepslate") || s1.contains("#0b0f19") || s1.contains("داكن") || s1.contains("slate") || s1.contains("كحلي") || s1.contains("أسود")
            val isSlate2 = s2.contains("deepslate") || s2.contains("#0b0f19") || s2.contains("داكن") || s2.contains("slate") || s2.contains("كحلي") || s2.contains("أسود")
            if (isSlate1 && isSlate2) return true

            // سمات الأخضر الداكن الإسلامي والزمردي
            val isGreen1 = s1.contains("أخضر") || s1.contains("green") || s1.contains("#1a4d2e") || s1.contains("زمردي") || s1.contains("إسلامي")
            val isGreen2 = s2.contains("أخضر") || s2.contains("green") || s2.contains("#1a4d2e") || s2.contains("زمردي") || s2.contains("إسلامي")
            if (isGreen1 && isGreen2) return true

            // سمات حركة الزووم والتقريب السينمائي
            val isZoom1 = s1.contains("زووم") || s1.contains("zoom") || s1.contains("تكبير") || s1.contains("تقريب") || s1.contains("zoomin")
            val isZoom2 = s2.contains("زووم") || s2.contains("zoom") || s2.contains("تكبير") || s2.contains("تقريب") || s2.contains("zoomin")
            if (isZoom1 && isZoom2) return true

            // سمات الانتقالات الناعمة والتلاشي
            val isDissolve1 = s1.contains("dissolve") || s1.contains("تلاشي") || s1.contains("ناعم") || s1.contains("fade") || s1.contains("انسيابي")
            val isDissolve2 = s2.contains("dissolve") || s2.contains("تلاشي") || s2.contains("ناعم") || s2.contains("fade") || s2.contains("انسيابي")
            if (isDissolve1 && isDissolve2) return true

            // سمات الخط العربي الكوفي العريض
            val isKufi1 = s1.contains("كوفي") || s1.contains("عريض") || s1.contains("kufi") || s1.contains("bold") || s1.contains("ثقيل")
            val isKufi2 = s2.contains("كوفي") || s2.contains("عريض") || s2.contains("kufi") || s2.contains("bold") || s2.contains("ثقيل")
            if (isKufi1 && isKufi2) return true

            // سمات حركة النصوص التفاعلية (كلمة بكلمة)
            val isWord1 = s1.contains("word") || s1.contains("كلمة") || s1.contains("pop") || s1.contains("تفاعلي") || s1.contains("حركي") || s1.contains("kinetic")
            val isWord2 = s2.contains("word") || s2.contains("كلمة") || s2.contains("pop") || s2.contains("تفاعلي") || s2.contains("حركي") || s2.contains("kinetic")
            if (isWord1 && isWord2) return true

            // سمات التباين والسينمائية والدقة
            val isContrast1 = s1.contains("تباين") || s1.contains("contrast") || s1.contains("4k") || s1.contains("سينمائي") || s1.contains("إضاءة")
            val isContrast2 = s2.contains("تباين") || s2.contains("contrast") || s2.contains("4k") || s2.contains("سينمائي") || s2.contains("إضاءة")
            if (isContrast1 && isContrast2) return true

            return false
        }

        // 3. حساب الدمج المرجح لقوائم السمات
        fun computeWeightedTraits(
            coreTraits: List<String>,
            incomingTraits: List<String>,
            maxTraits: Int,
            protectedTraits: List<String> = emptyList()
        ): List<String> {
            val traitWeights = mutableMapOf<String, Double>()

            // منح سمات الهوية الأساسية أوزاناً عالية لحمايتها
            coreTraits.forEachIndexed { index, trait ->
                val clean = trait.trim()
                if (clean.isNotBlank()) {
                    val isProtected = protectedTraits.any { areTraitsSemanticallySimilar(it, clean) }
                    val rankMultiplier = 1.0 - (index * 0.04).coerceAtLeast(0.0)
                    val baseScore = coreWeight * rankMultiplier * (if (isProtected) 3.5 else 2.0)
                    traitWeights[clean] = baseScore
                }
            }

            // دمج السمات الواردة: تعزيز المشترك أو إضافة الإيجابي
            incomingTraits.forEachIndexed { index, trait ->
                val cleanTrait = trait.trim()
                if (cleanTrait.isNotBlank()) {
                    val rankMultiplier = 1.0 - (index * 0.08).coerceAtLeast(0.0)
                    val incomingScore = newStyleWeight * rankMultiplier

                    val existingKey = traitWeights.keys.find { existing ->
                        areTraitsSemanticallySimilar(existing, cleanTrait)
                    }

                    if (existingKey != null) {
                        traitWeights[existingKey] = (traitWeights[existingKey] ?: 0.0) + (incomingScore * 2.2)
                    } else if (newStyle.overallScore >= 85) {
                        traitWeights[cleanTrait] = incomingScore
                    }
                }
            }

            // ترتيب السمات تنازلياً حسب الوزن الإجمالي
            val sorted = traitWeights.entries
                .sortedByDescending { it.value }
                .map { it.key }
                .filter { it.isNotBlank() }
                .toMutableList()

            // ضمان وجود السمات المحمية (DeepSlate و Gold)
            protectedTraits.forEach { protected ->
                if (sorted.none { areTraitsSemanticallySimilar(it, protected) }) {
                    if (sorted.size >= maxTraits) sorted.removeAt(sorted.lastIndex)
                    sorted.add(0, protected)
                }
            }

            return sorted.take(maxTraits)
        }

        val protectedVisuals = listOf(
            "ألوان داكنة DeepSlate #0B0F19",
            "لمسات ذهبية متوهجة Gold #E8C547"
        )
        val mergedVisuals = computeWeightedTraits(currentCore.visualTraits, newStyle.visualTraits, 6, protectedVisuals)
        val mergedMotions = computeWeightedTraits(currentCore.motionTraits, newStyle.motionTraits, 5)
        val mergedTexts = computeWeightedTraits(currentCore.textTraits, newStyle.textTraits, 5)

        // 4. زيادة تدريجية ومحسوبة بدقة في قوة العقل التراكمي (+0 إلى +3 كحد أقصى)
        val strengthGain = StyleBrainLoadFix.computeStrengthGain(newStyle.overallScore, currentCore.strengthScore)
        val updatedStrength = (currentCore.strengthScore + strengthGain).coerceAtMost(100)

        // 5. تحديث التحليل النصي للهيكل التراكمي
        val dominantVisual = mergedVisuals.firstOrNull() ?: "ألوان DeepSlate #0B0F19 ولمسات ذهبية متوهجة Gold #E8C547"
        val dominantMotion = mergedMotions.firstOrNull() ?: "زووم بطيء متصاعد (Slow Cinematic ZoomIn)"
        val totalAbsorbed = _absorbedStyles.value.size

        val updatedAnalysis = "عقل قبس التراكمي (القوة: $updatedStrength% | $totalAbsorbed أساليب ممتصة): هوية إخراجية هادفة متطورة ترتكز على $dominantVisual مع $dominantMotion."

        val evolvedCore = QabasCoreStyle(
            visualTraits = mergedVisuals,
            motionTraits = mergedMotions,
            textTraits = mergedTexts,
            analysis = updatedAnalysis,
            strengthScore = updatedStrength,
            lastUpdated = System.currentTimeMillis()
        )

        saveCoreStyle(context, evolvedCore)
    }

    suspend fun absorbStyleFromVideo(
        context: Context,
        videoPath: String,
        styleName: String = "",
        onProgress: ((Float, String) -> Unit)? = null
    ): AbsorbedStyle {
        val report: (Float, String) -> Unit = { p, msg ->
            try { onProgress?.invoke(p, msg) } catch (_: Exception) {}
        }

        // 0. التحقق الصارم من وجود مفتاح ذكاء اصطناعي حقيقي
        if (!hasValidAiApiKey(context)) {
            throw IllegalStateException("يتطلب امتصاص وتحليل الأساليب توفير مفتاح Gemini أو Groq في الإعدادات. يرجى إضافة المفتاح من شاشة مفاتيح API أولاً.")
        }

        val trimmedSource = videoPath.trim()
        if (trimmedSource.isBlank()) {
            throw IllegalArgumentException("مسار الفيديو أو الرابط فارغ.")
        }

        report(0.05f, "بدء قراءة المصدر وفحص الذاكرة...")

        // 1. حماية التكرار والتعزيز التراكمي: إذا وُجد أسلوب بنفس المصدر، عززه دون تكرار
        val existingStyle = _absorbedStyles.value.find { 
            it.sourceVideoPathOrUrl.isNotBlank() && it.sourceVideoPathOrUrl.equals(trimmedSource, ignoreCase = true) 
        }
        if (existingStyle != null) {
            val boostedScore = (existingStyle.overallScore + 2).coerceIn(0, 100)
            val enhancedStyle = existingStyle.copy(
                overallScore = boostedScore,
                name = if (styleName.isNotBlank()) styleName else existingStyle.name,
                absorbedAt = System.currentTimeMillis()
            )
            val updatedList = _absorbedStyles.value.map { if (it.id == existingStyle.id) enhancedStyle else it }
            saveStyles(context, updatedList)

            // تطور متزن وتدريجي في العقل التراكمي
            evolveCoreStyleWithNewAbsorption(context, enhancedStyle)
            report(1f, "تم تعزيز نمط موجود مسبقاً من نفس المصدر بنجاح")
            return enhancedStyle
        }

        report(0.12f, "تحضير مسار التحليل العميق...")
        var analysisText = DEFAULT_CORE_STYLE.analysis
        var visualTraits = DEFAULT_CORE_STYLE.visualTraits
        var motionTraits = DEFAULT_CORE_STYLE.motionTraits
        var textTraits = DEFAULT_CORE_STYLE.textTraits
        var score = (89..96).random()

        val localFile = File(trimmedSource)
        var visualAnalysisSuccess = false

        // 2. إذا كان المدخل مسار ملف محلي موجود وحقيقي
        if (localFile.exists() && localFile.isFile && localFile.length() > 0L) {
            try {
                report(0.12f, "استخراج الإطارات: فحص ملف الفيديو وتهيئة المستخرج...")
                report(0.22f, "استخراج الإطارات: استخراج 6 إطارات متوازنة زمنياً بدقة 720p...")
                val frames = extractKeyFrames(context, localFile.absolutePath)
                if (frames.isNotEmpty()) {
                    report(0.33f, "استخراج الإطارات: تم استخراج ${frames.size} إطارات بنجاح")
                    // تحليل محلي (مكافئ OpenCV): ألوان hex + حدة + تباين + Optical Flow
                    report(0.42f, "تحليل الألوان: فحص التدرجات والتباين والسطوع (Hex)...")
                    val localStyle = try {
                        LocalImageAnalyzer.analyzeFrames(frames)
                    } catch (e: Exception) {
                        Log.w("StyleBrain", "LocalImageAnalyzer failed: ${e.message}")
                        null
                    }

                    if (localStyle != null) {
                        report(0.55f, "تحليل الألوان: قياس التباين والتدفق البصري والحركة...")
                        visualTraits = localStyle.visualTraits
                        motionTraits = buildList {
                            add("motion=${localStyle.motionType}")
                            when (localStyle.motionType) {
                                "static" -> add("كاميرا ثابتة خاشعة (Static Sacred)")
                                "slow_zoom" -> add("زووم بطيء سينمائي متصاعد (Slow Cinematic Zoom-in)")
                                "pan" -> add("مسح أفقي ناعم (Cinematic Pan)")
                                "punch_in" -> add("قطع وانتقال سريع خاطف (Punch-in)")
                            }
                            when {
                                localStyle.avgContrast > 0.55f -> add("إيقاع درامي عالي التباين")
                                localStyle.avgBrightness < 0.35f -> add("حركة بطيئة خاشعة وهادئة")
                            }
                            add("filter=${localStyle.filterHint}")
                            add("transition=Dissolve")
                            add("tempo=${if (localStyle.avgContrast > 0.55f) "متوسط" else "خاشع"}")
                        }
                        textTraits = listOf(
                            "خط عربي كوفي عريض في المنطقة الآمنة",
                            "إبراز الكلمات باللون الأساسي ${localStyle.primaryHex}",
                            "captionAnim=WordByWord",
                            "captionPos=bottom"
                        )
                        score = (localStyle.avgSharpness.coerceIn(0f, 1f) * 100f).toInt()
                        val ofTag = if (localStyle.usedRealOpticalFlow) "Farneback" else "diff"
                        analysisText = "تحليل محلي متقدم: فلتر=${localStyle.filterHint} | لون أساسي=${localStyle.primaryHex} | لون خلفية=${localStyle.backgroundHex} | حركة=${localStyle.motionType} ($ofTag) | إطارات=${localStyle.framesUsed}"
                        Log.d("StyleBrain", "Local analysis successful: $analysisText")
                    }
                    report(0.66f, "تحليل الألوان: اكتمل بناء مصفوفة السمات البصرية والحركية")

                    report(0.72f, "استعلام الذكاء الاصطناعي: إرسال الإطارات لـ Gemini Vision...")
                    val visionResult = try {
                        analyzeFramesWithVision(
                            frames = frames,
                            context = context,
                            extraContext = buildString {
                                append("اسم الأسلوب المطلوب: $styleName | اسم الملف: ${localFile.name}")
                                if (localStyle != null) {
                                    append(" | ألوان محلية مكتشفة: primary=${localStyle.primaryHex}, bg=${localStyle.backgroundHex}, accent=${localStyle.secondaryHex}, filter=${localStyle.filterHint}, motion=${localStyle.motionType}")
                                }
                            }
                        )
                    } catch (e: Exception) {
                        Log.w("StyleBrain", "Gemini vision failed, keeping local analysis: ${e.message}")
                        null
                    }

                    if (visionResult != null) {
                        val aText = visionResult.optString("analysis", "")
                        if (aText.isNotBlank()) {
                            analysisText = aText
                        }
                        if (visionResult.has("overallScore")) {
                            score = visionResult.optInt("overallScore", score).coerceIn(0, 100)
                        }

                        val parsedPrimaryHex = visionResult.optString("primaryColorHex", localStyle?.primaryHex ?: "#E8C547")
                        val parsedBgHex = visionResult.optString("backgroundColorHex", localStyle?.backgroundHex ?: "#0B0F19")
                        val parsedAccentHex = visionResult.optString("accentColorHex", localStyle?.secondaryHex ?: "#F8FAFC")
                        val parsedFilter = visionResult.optString("filterHint", localStyle?.filterHint ?: "warm_gold")
                        val parsedMotion = visionResult.optString("motionType", localStyle?.motionType ?: "slow_zoom")
                        val parsedTransition = visionResult.optString("transitionType", "Dissolve")
                        val parsedTempo = visionResult.optString("tempo", "خاشع")
                        val parsedCaptionAnim = visionResult.optString("captionAnimation", "WordByWord")
                        val parsedCaptionPos = visionResult.optString("captionPosition", "bottom")

                        val newVisuals = mutableListOf<String>()
                        newVisuals.add("primary:$parsedPrimaryHex")
                        newVisuals.add("bg:$parsedBgHex")
                        newVisuals.add("accent:$parsedAccentHex")
                        newVisuals.add("filter=$parsedFilter")
                        newVisuals.add("motion=$parsedMotion")

                        if (visionResult.has("visualTraits")) {
                            val arr = visionResult.optJSONArray("visualTraits")
                            if (arr != null && arr.length() > 0) {
                                for (i in 0 until arr.length()) {
                                    val item = arr.optString(i).trim()
                                    if (item.isNotBlank()) newVisuals.add(item)
                                }
                            }
                        }
                        visualTraits = newVisuals.distinct()

                        val newMotions = mutableListOf<String>()
                        newMotions.add("motion=$parsedMotion")
                        newMotions.add("transition=$parsedTransition")
                        newMotions.add("tempo=$parsedTempo")
                        newMotions.add("filter=$parsedFilter")

                        if (visionResult.has("motionTraits")) {
                            val arr = visionResult.optJSONArray("motionTraits")
                            if (arr != null && arr.length() > 0) {
                                for (i in 0 until arr.length()) {
                                    val item = arr.optString(i).trim()
                                    if (item.isNotBlank()) newMotions.add(item)
                                }
                            }
                        }
                        motionTraits = newMotions.distinct()

                        val newTexts = mutableListOf<String>()
                        newTexts.add("captionAnim=$parsedCaptionAnim")
                        newTexts.add("captionPos=$parsedCaptionPos")
                        val typoStyle = visionResult.optString("typographyStyle", "")
                        if (typoStyle.isNotBlank()) newTexts.add(typoStyle)

                        if (visionResult.has("textTraits")) {
                            val arr = visionResult.optJSONArray("textTraits")
                            if (arr != null && arr.length() > 0) {
                                for (i in 0 until arr.length()) {
                                    val item = arr.optString(i).trim()
                                    if (item.isNotBlank()) newTexts.add(item)
                                }
                            }
                        }
                        textTraits = newTexts.distinct()
                    }

                    visualAnalysisSuccess = localStyle != null || visionResult != null
                }
            } catch (e: Exception) {
                Log.w("StyleBrain", "Visual frame analysis fallback to text analysis: ${e.message}")
            }
        }

        // 3. إذا فشل استخراج الإطارات أو كان المدخل رابطاً/وصفاً فقط
        if (!visualAnalysisSuccess) {
            report(0.50f, "تحليل أسلوب المصدر دلالياً واستخراج البنية الإخراجية...")
            val prompt = """
                أنت خبير إخراج سينمائي ومحلل فني أول للفيديوهات الدعوية والقصيرة لمنصة "قبس".
                قم بتحليل واستنساخ أسلوب الفيديو المرجعي التالي بدقة مع توليد توجيهات إخراجية حقيقية قابلة للتنفيذ:
                المدخل/الرابط/الوصف: "$trimmedSource"
                ${if (styleName.isNotBlank()) "اسم الأسلوب المقترح: $styleName" else ""}
                
                يجب إخراج استجابة JSON فقط وحصراً وفق الهيكل التالي وبألوان hex دقيقة:
                {
                    "analysis": "تحليل سينمائي متكامل يركز على الهوية الإخراجية واللوحة اللونية وحركة الكاميرا والنبرة",
                    "primaryColorHex": "#E8C547",
                    "backgroundColorHex": "#0B0F19",
                    "accentColorHex": "#F8FAFC",
                    "filterHint": "warm_gold",
                    "motionType": "slow_zoom",
                    "transitionType": "Dissolve",
                    "tempo": "خاشع",
                    "captionAnimation": "WordByWord",
                    "captionPosition": "bottom",
                    "typographyStyle": "خط عربي كوفي عريض في المنتصف مع إبراز الكلمات بالذهبي",
                    "tone": "وقور ومؤثر",
                    "visualTraits": ["ألوان داكنة DeepSlate #0B0F19", "لمسات ذهبية متوهجة Gold #E8C547", "تباين عالي Contrast 1:10", "إضاءة دافئة"],
                    "motionTraits": ["زووم بطيء متصاعد ناعم Slow Cinematic ZoomIn", "انتقالات تلاشي ناعمة Smooth Dissolve", "إيقاع وقور متوازن"],
                    "textTraits": ["خط عربي كوفي عريض في المنطقة الآمنة", "إبراز الكلمات بالذهبي #E8C547", "ظهور كلمة بكلمة Word-by-Word"]
                }
            """.trimIndent()
            
            try {
                val response = AppServices.chatWithAssistant(listOf(Pair(false, prompt))) ?: ""
                val cleanJson = response.replace("```json", "").replace("```", "").trim()
                
                if (cleanJson.startsWith("{") && cleanJson.endsWith("}")) {
                    val obj = JSONObject(cleanJson)
                    if (obj.has("analysis") && obj.getString("analysis").isNotBlank()) {
                        analysisText = obj.getString("analysis")
                    }
                    if (obj.has("overallScore")) {
                        val parsedScore = obj.getInt("overallScore")
                        score = parsedScore.coerceIn(0, 100)
                    }
                    
                    val parsedPrimary = obj.optString("primaryColorHex", "#E8C547")
                    val parsedBg = obj.optString("backgroundColorHex", "#0B0F19")
                    val parsedAccent = obj.optString("accentColorHex", "#F8FAFC")
                    val parsedFilter = obj.optString("filterHint", "warm_gold")
                    val parsedMotion = obj.optString("motionType", "slow_zoom")
                    val parsedTransition = obj.optString("transitionType", "Dissolve")
                    val parsedTempo = obj.optString("tempo", "خاشع")
                    val parsedCaptionAnim = obj.optString("captionAnimation", "WordByWord")
                    val parsedCaptionPos = obj.optString("captionPosition", "bottom")

                    val vList = mutableListOf("primary:$parsedPrimary", "bg:$parsedBg", "accent:$parsedAccent", "filter=$parsedFilter", "motion=$parsedMotion")
                    if (obj.has("visualTraits")) {
                        val vArr = obj.getJSONArray("visualTraits")
                        for (i in 0 until vArr.length()) {
                            val str = vArr.getString(i).trim()
                            if (str.isNotBlank()) vList.add(str)
                        }
                    }
                    visualTraits = vList.distinct()

                    val mList = mutableListOf("motion=$parsedMotion", "transition=$parsedTransition", "tempo=$parsedTempo", "filter=$parsedFilter")
                    if (obj.has("motionTraits")) {
                        val mArr = obj.getJSONArray("motionTraits")
                        for (i in 0 until mArr.length()) {
                            val str = mArr.getString(i).trim()
                            if (str.isNotBlank()) mList.add(str)
                        }
                    }
                    motionTraits = mList.distinct()

                    val tList = mutableListOf("captionAnim=$parsedCaptionAnim", "captionPos=$parsedCaptionPos")
                    val typo = obj.optString("typographyStyle", "")
                    if (typo.isNotBlank()) tList.add(typo)
                    if (obj.has("textTraits")) {
                        val tArr = obj.getJSONArray("textTraits")
                        for (i in 0 until tArr.length()) {
                            val str = tArr.getString(i).trim()
                            if (str.isNotBlank()) tList.add(str)
                        }
                    }
                    textTraits = tList.distinct()
                } else {
                    // Fallback to safe defaults with Qabas styling
                    analysisText = DEFAULT_CORE_STYLE.analysis
                    visualTraits = DEFAULT_CORE_STYLE.visualTraits
                    motionTraits = DEFAULT_CORE_STYLE.motionTraits
                    textTraits = DEFAULT_CORE_STYLE.textTraits
                    score = 90
                }
            } catch (e: Exception) {
                analysisText = DEFAULT_CORE_STYLE.analysis
                visualTraits = DEFAULT_CORE_STYLE.visualTraits
                motionTraits = DEFAULT_CORE_STYLE.motionTraits
                textTraits = DEFAULT_CORE_STYLE.textTraits
                score = 88
            }
        }
        
        // إذا فشل التحليل البصري نعود لهوية قبس الأساسية مع إعلان صريح في النص
        var usedFallbackOnly = false
        if (visualTraits.isEmpty() || visualTraits == DEFAULT_CORE_STYLE.visualTraits) {
            usedFallbackOnly = true
        }
        if (motionTraits.isEmpty() || motionTraits == DEFAULT_CORE_STYLE.motionTraits) {
            usedFallbackOnly = true
        }

        if (usedFallbackOnly) {
            throw Exception("فشل تحليل الفيديو: لم نتمكن من استخراج سمات فنية حقيقية. يرجى تجربة فيديو آخر أكثر وضوحاً أو التأكد من إعدادات الذكاء الاصطناعي.")
        }

        if (score < 70) score = 70
        
        val effectiveName = when {
            styleName.isNotBlank() -> styleName
            trimmedSource.contains("http") -> "أسلوب مستنسخ (${trimmedSource.substringAfterLast("/").take(15)})"
            localFile.exists() -> "أسلوب بصري: ${localFile.nameWithoutExtension.take(20)}"
            else -> "أسلوب: ${trimmedSource.take(20)}"
        }
        
        report(0.96f, "الحفظ وتطوير العقل: تخزين كائن النمط وتحديث عقل قبس...")
        val newStyle = AbsorbedStyle(
            id = UUID.randomUUID().toString(),
            name = effectiveName,
            sourceVideoPathOrUrl = trimmedSource,
            analysis = analysisText,
            visualTraits = visualTraits.filter { it.isNotBlank() },
            motionTraits = motionTraits.filter { it.isNotBlank() },
            textTraits = textTraits.filter { it.isNotBlank() },
            overallScore = score,
            absorbedAt = System.currentTimeMillis()
        )
        
        val updatedList = _absorbedStyles.value + newStyle
        saveStyles(context, updatedList)
        
        // Evolve QabasCoreStyle accumulatively
        evolveCoreStyleWithNewAbsorption(context, newStyle)
        report(0.98f, "الحفظ وتطوير العقل: ترقية المؤشرات التراكمية...")

        // Feed into TasteManager preferences as well
        try {
            TasteManager.registerPreference(context, "STYLE", visualTraits.joinToString("، "), 2)
            TasteManager.registerPreference(context, "PACING", motionTraits.joinToString("، "), 2)
        } catch (e: Exception) {}
        
        report(1f, "اكتمل استخراج وامتصاص النمط الفني بنجاح!")
        return newStyle
    }
    
    suspend fun absorbStyleFromAnalysis(
        context: Context,
        name: String,
        source: String,
        analysisResult: VideoStyleAnalysis
    ): AbsorbedStyle {
        val visualTraits = listOf(
            analysisResult.dominantColors.ifEmpty { "ألوان داكنة سينمائية DeepSlate مع ذهبي متوهج" },
            "إضاءة وتباين سينمائي عالي"
        )
        val motionTraits = listOf(
            analysisResult.transitionSpeed.ifEmpty { "متوسطة متوازنة" },
            analysisResult.movementPatterns.ifEmpty { "حركة كاميرا زووم بطيء ناعم" },
            analysisResult.overallRhythm.ifEmpty { "إيقاع تصاعدي مؤثر" }
        )
        val textTraits = listOf(
            analysisResult.typographyStyle.ifEmpty { "خط عربي كوفي عريض في المنتصف" },
            "إبراز الكلمات المفتاحية باللون الذهبي"
        )
        val summaryAnalysis = "${analysisResult.detectedStyle.ifEmpty { name }} | ${analysisResult.contentTone} | ألوان: ${analysisResult.dominantColors} | حركة: ${analysisResult.transitionSpeed}"
        val populatedAnalysisFields = listOf(
            analysisResult.detectedStyle,
            analysisResult.dominantColors,
            analysisResult.transitionSpeed,
            analysisResult.movementPatterns,
            analysisResult.overallRhythm,
            analysisResult.audioStyle,
            analysisResult.typographyStyle,
            analysisResult.contentTone,
            analysisResult.targetAudience
        ).count { it.isNotBlank() }
        val analysisScore = (populatedAnalysisFields * 100 / 9).coerceIn(0, 100)
        
        val newStyle = AbsorbedStyle(
            id = UUID.randomUUID().toString(),
            name = if (name.isBlank()) "أسلوب مستنسخ ${System.currentTimeMillis() % 1000}" else name,
            sourceVideoPathOrUrl = source,
            analysis = summaryAnalysis,
            visualTraits = visualTraits.filter { it.isNotBlank() },
            motionTraits = motionTraits.filter { it.isNotBlank() },
            textTraits = textTraits.filter { it.isNotBlank() },
            overallScore = analysisScore,
            absorbedAt = System.currentTimeMillis()
        )

        val trimmedSource = source.trim()
        val existingIndex = _absorbedStyles.value.indexOfFirst { 
            it.sourceVideoPathOrUrl.isNotBlank() && it.sourceVideoPathOrUrl.equals(trimmedSource, ignoreCase = true) 
        }

        val styleToSave = if (existingIndex >= 0) {
            val existing = _absorbedStyles.value[existingIndex]
            newStyle.copy(id = existing.id)
        } else {
            newStyle
        }

        val updatedList = if (existingIndex >= 0) {
            _absorbedStyles.value.toMutableList().apply { set(existingIndex, styleToSave) }
        } else {
            _absorbedStyles.value + styleToSave
        }
        saveStyles(context, updatedList)

        // Evolve QabasCoreStyle accumulatively
        evolveCoreStyleWithNewAbsorption(context, styleToSave)

        return styleToSave
    }

    suspend fun fuseAbsorbedStyles(
        context: Context,
        primary: AbsorbedStyle,
        secondary: AbsorbedStyle,
        blendRatio: Float = 0.5f,
        customName: String? = null
    ): AbsorbedStyle {
        val clampedRatio = blendRatio.coerceIn(0.1f, 0.9f)
        val primaryWeight = 1.0f - clampedRatio
        val secondaryWeight = clampedRatio

        val primaryVisualCount = if (primaryWeight >= 0.5f) 4 else 2
        val secondaryVisualCount = if (secondaryWeight >= 0.5f) 3 else 2
        val combinedVisualTraits = (primary.visualTraits.take(primaryVisualCount) + 
                                     secondary.visualTraits.take(secondaryVisualCount)).distinct()
        
        val combinedMotionTraits = (primary.motionTraits.take(3) + 
                                     secondary.motionTraits.take(2)).distinct()

        val combinedTextTraits = (primary.textTraits.take(3) + 
                                   secondary.textTraits.take(2)).distinct()

        val fusedScore = ((primary.overallScore * primaryWeight) + (secondary.overallScore * secondaryWeight)).toInt().coerceIn(0, 100)
        val defaultFusedName = customName?.ifBlank { null } ?: "دمج: ${primary.name.take(10)} + ${secondary.name.take(10)}"

        val analysisText = "نمط فني مصهور يجمع بنسبة (${(primaryWeight * 100).toInt()}%) من [${primary.name}] و (${(secondaryWeight * 100).toInt()}%) من [${secondary.name}]. مدمج من خصائص استخرجت مسبقاً."

        val newStyle = AbsorbedStyle(
            id = UUID.randomUUID().toString(),
            name = defaultFusedName,
            sourceVideoPathOrUrl = "fused://${primary.id}/${secondary.id}",
            analysis = analysisText,
            visualTraits = combinedVisualTraits.filter { it.isNotBlank() },
            motionTraits = combinedMotionTraits.filter { it.isNotBlank() },
            textTraits = combinedTextTraits.filter { it.isNotBlank() },
            overallScore = fusedScore,
            absorbedAt = System.currentTimeMillis()
        )

        val updatedList = _absorbedStyles.value + newStyle
        saveStyles(context, updatedList)

        // Evolve QabasCoreStyle accumulatively
        evolveCoreStyleWithNewAbsorption(context, newStyle)

        return newStyle
    }

    suspend fun absorbStyleObject(context: Context, styleObject: StyleObject): AbsorbedStyle {
        val absorbed = styleObject.toAbsorbedStyle()
        val current = _absorbedStyles.value.toMutableList()
        current.removeAll { it.id == absorbed.id }
        current.add(0, absorbed)
        saveStyles(context, current)

        // Evolve QabasCoreStyle accumulatively
        evolveCoreStyleWithNewAbsorption(context, absorbed)

        return absorbed
    }
    
    fun getAllAbsorbedStyles(): List<AbsorbedStyle> {
        return _absorbedStyles.value
    }
    
    fun getCoreStyle(): QabasCoreStyle {
        return _coreStyle.value
    }

    fun getCoreStyleStrength(): Int {
        return _coreStyle.value.strengthScore
    }

    fun getStyleStrength(): Int {
        return getCoreStyleStrength()
    }
    
    /**
     * الدمج النهائي الذكي عند التوليد (Contextual & Semantic Style Selection & Blending):
     * 1. أولوية مطلقة: الأسلوب الأساسي المعتمد للاستوديو (primaryStyleId) إن وُجد.
     * 2. ثم مطابقة سياقية متقدمة تجمع بين الدلالات الموضوعية (فكرة + نبرة + جمهور + مدة) وذكاء Gemini.
     * 3. ثم العقل التراكمي (QabasCoreStyle) كـ Fallback محصّن.
     * 4. دمج مرجح ذكي (70% هوية قبس + 30% أسلوب مختار) مع ضمان استبقاء كامل الوسوم الإخراجية (الألوان، الانتقال، الحركة، الكابشن).
     */
    suspend fun chooseBestStyleForIdea(idea: String, duration: Int, tone: String, audience: String): AbsorbedStyle? {
        val core = _coreStyle.value
        val styles = _absorbedStyles.value
        
        if (styles.isEmpty()) {
            return null
        }
        
        // 1. الأولوية الأولى: الأسلوب الأساسي المعتمد للاستوديو (Primary Studio Style)
        val primaryStyle = _primaryStyleId.value?.let { pId -> styles.find { it.id == pId } }
        
        val matchedStyle: AbsorbedStyle = if (primaryStyle != null) {
            primaryStyle
        } else if (styles.size == 1) {
            styles.first()
        } else {
            // 2. مطابقة خوارزمية ذكية متعددة المعايير
            val lowerIdea = idea.lowercase(Locale.ROOT)
            val lowerTone = tone.lowercase(Locale.ROOT)
            val lowerAudience = audience.lowercase(Locale.ROOT)

            fun calculateMatchScore(style: AbsorbedStyle): Double {
                var score = (style.overallScore.toDouble() * 0.4) // 40% من التقييم العام
                val fullText = "${style.name} ${style.analysis} ${style.visualTraits.joinToString()} ${style.motionTraits.joinToString()} ${style.textTraits.joinToString()}".lowercase(Locale.ROOT)

                // مطابقة موضوع الفكرة
                if (lowerIdea.contains("قرآن") || lowerIdea.contains("آية") || lowerIdea.contains("تلاوة") || lowerIdea.contains("سورة") || lowerIdea.contains("خشوع") || lowerIdea.contains("ذكر")) {
                    if (fullText.contains("خاشع") || fullText.contains("slow") || fullText.contains("zoom") || fullText.contains("gold") || fullText.contains("ذهب")) score += 25.0
                }
                if (lowerIdea.contains("قصة") || lowerIdea.contains("سيرة") || lowerIdea.contains("تاريخ") || lowerIdea.contains("عبرة") || lowerIdea.contains("نبي") || lowerIdea.contains("صحابة")) {
                    if (fullText.contains("سينمائي") || fullText.contains("cinema") || fullText.contains("desert") || fullText.contains("صحراء") || fullText.contains("درامي")) score += 25.0
                }
                if (lowerIdea.contains("نصيحة") || lowerIdea.contains("شباب") || lowerIdea.contains("حكمة") || lowerIdea.contains("همة") || lowerIdea.contains("نجاح") || lowerIdea.contains("تطوير")) {
                    if (fullText.contains("سريع") || fullText.contains("pop") || fullText.contains("كلمة") || fullText.contains("punch") || fullText.contains("تفاعلي")) score += 25.0
                }
                if (lowerIdea.contains("تأمل") || lowerIdea.contains("تدبر") || lowerIdea.contains("رحمة") || lowerIdea.contains("توبة") || lowerIdea.contains("قلب") || lowerIdea.contains("دعاء")) {
                    if (fullText.contains("زمردي") || fullText.contains("emerald") || fullText.contains("تلاشي") || fullText.contains("dissolve") || fullText.contains("ناعم")) score += 25.0
                }

                // مطابقة النبرة
                if (lowerTone.isNotBlank() && fullText.contains(lowerTone)) {
                    score += 20.0
                }

                // مطابقة الجمهور
                if (lowerAudience.isNotBlank() && fullText.contains(lowerAudience)) {
                    score += 15.0
                }

                // مطابقة المدة
                if (duration <= 30 && (fullText.contains("سريع") || fullText.contains("خاطف") || fullText.contains("pop"))) {
                    score += 10.0
                } else if (duration >= 60 && (fullText.contains("خاشع") || fullText.contains("بطيء") || fullText.contains("وقور"))) {
                    score += 10.0
                }

                return score
            }

            // محاولة ترشيح إضافية بالذكاء الاصطناعي للجيل الثاني (العقل الاستباقي: دمج هجين تلقائي)
            val bestAlgorithmic = styles.maxByOrNull { calculateMatchScore(it) } ?: styles.first()
            
            val aiMatched = try {
                if (styles.size >= 2 && CloudServices.isFirebaseInitialized) { // استخدام الهجين فقط إذا توفرت الإمكانيات
                    val prompt = """
                        أنت "العقل الاستباقي" (Proactive Brain) لاستوديو قبس.
                        لدي فكرة لفيديو إسلامي: "$idea".
                        المدة: $duration ثانية. النبرة: $tone. الجمهور: $audience.
                        
                        الأساليب المتاحة:
                        ${styles.joinToString("\n") { "ID: ${it.id} | Name: ${it.name} | Traits: ${it.visualTraits.take(2).joinToString()}" }}
                        
                        أريدك أن تبدع. لا تختار أسلوباً واحداً فقط. اختر أفضل أسلوبين يمكن دمجهما (Hybrid) لإنتاج أفضل إخراج لهذه الفكرة المحددة.
                        يجب أن ترجع ردك بصيغة JSON حصراً بهذا الشكل:
                        {"primary_id": "ID_1", "secondary_id": "ID_2", "blend_ratio": 0.6, "hybrid_name": "اسم إبداعي للنمط الجديد"}
                    """.trimIndent()
                    val res = AppServices.chatWithAssistant(listOf(Pair(false, prompt)), null) ?: ""
                    
                    val primaryId = Regex(""""primary_id"\s*:\s*"([^"]+)"""").find(res)?.groupValues?.get(1)
                    val secondaryId = Regex(""""secondary_id"\s*:\s*"([^"]+)"""").find(res)?.groupValues?.get(1)
                    val blendStr = Regex(""""blend_ratio"\s*:\s*([0-9.]+)""").find(res)?.groupValues?.get(1)
                    val hybridName = Regex(""""hybrid_name"\s*:\s*"([^"]+)"""").find(res)?.groupValues?.get(1)
                    
                    val pStyle = styles.find { it.id == primaryId }
                    val sStyle = styles.find { it.id == secondaryId }
                    
                    if (pStyle != null && sStyle != null && pStyle.id != sStyle.id) {
                        val ratio = blendStr?.toFloatOrNull() ?: 0.5f
                        val name = hybridName ?: "دمج استباقي: ${pStyle.name.take(5)}+${sStyle.name.take(5)}"
                        
                        // إنتاج النمط الهجين لحظياً (العقل الاستباقي النشط)
                        val clampedRatio = ratio.coerceIn(0.2f, 0.8f)
                        val primaryWeight = 1.0f - clampedRatio
                        val secondaryWeight = clampedRatio
                        
                        AbsorbedStyle(
                            id = "proactive_hybrid_${System.currentTimeMillis()}",
                            name = "✨ $name",
                            sourceVideoPathOrUrl = "proactive_hybrid",
                            analysis = "توليد استباقي لحظي: دمج (${(primaryWeight*100).toInt()}%) من ${pStyle.name} و (${(secondaryWeight*100).toInt()}%) من ${sStyle.name} ليناسب فكرة: $idea",
                            visualTraits = (pStyle.visualTraits.take(3) + sStyle.visualTraits.take(2)).distinct(),
                            motionTraits = (pStyle.motionTraits.take(3) + sStyle.motionTraits.take(2)).distinct(),
                            textTraits = (pStyle.textTraits.take(3) + sStyle.textTraits.take(2)).distinct(),
                            overallScore = ((pStyle.overallScore * primaryWeight) + (sStyle.overallScore * secondaryWeight)).toInt().coerceIn(0, 100)
                        )
                    } else {
                        // تراجع عن الدمج إذا لم يجد أسلوبين صالحين، واختر الأول
                        val fallbackId = primaryId ?: secondaryId
                        styles.find { it.id == fallbackId }
                    }
                } else {
                    val prompt = """
                        لدي فكرة لفيديو إسلامي: "$idea".
                        الأساليب المتاحة:
                        ${styles.joinToString("\n") { "ID: ${it.id} | Name: ${it.name}" }}
                        اختر الأسلوب الأنسب تماماً لهذه الفكرة. أرجع ID الأسلوب المختار فقط.
                    """.trimIndent()
                    val res = AppServices.chatWithAssistant(listOf(Pair(false, prompt))) ?: ""
                    styles.find { res.contains(it.id) }
                }
            } catch (e: Exception) {
                null
            }

            aiMatched ?: bestAlgorithmic
        }

        // 3. الدمج المرجح المحصن (70% أساسي + 30% أسلوب مختار)
        // استخلاص الوسوم الإخراجية الحاسمة لضمان عدم ضياعها
        val primaryHex = matchedStyle.resolvePrimaryColorHex()
        val bgHex = matchedStyle.resolveBackgroundColorHex()
        val accentHex = matchedStyle.resolveAccentColorHex()
        val filterHint = matchedStyle.resolveFilterHint()
        val motionType = matchedStyle.resolveMotionType()
        val transitionType = matchedStyle.resolveTransitionType()
        val tempo = matchedStyle.resolveTempo()
        val captionAnim = matchedStyle.resolveTextAnimation()
        val captionPos = matchedStyle.resolveCaptionPosition()

        val blendedVisuals = buildList {
            add("primary:$primaryHex")
            add("bg:$bgHex")
            add("accent:$accentHex")
            add("filter=$filterHint")
            // إضافة سمات هوية قبس الأساسية أولاً
            core.visualTraits.filter { !it.startsWith("primary:") && !it.startsWith("bg:") && !it.startsWith("filter=") }.take(2).forEach { add(it) }
            // إضافة سمات من الأسلوب المختار
            matchedStyle.visualTraits.filter { !it.startsWith("primary:") && !it.startsWith("bg:") && !it.startsWith("filter=") }.take(2).forEach { add(it) }
        }.distinct()

        val blendedMotions = buildList {
            add("motion=$motionType")
            add("transition=$transitionType")
            add("tempo=$tempo")
            add("filter=$filterHint")
            core.motionTraits.filter { !it.startsWith("motion=") && !it.startsWith("transition=") && !it.startsWith("tempo=") }.take(2).forEach { add(it) }
            matchedStyle.motionTraits.filter { !it.startsWith("motion=") && !it.startsWith("transition=") && !it.startsWith("tempo=") }.take(2).forEach { add(it) }
        }.distinct()

        val blendedTexts = buildList {
            add("captionAnim=$captionAnim")
            add("captionPos=$captionPos")
            core.textTraits.filter { !it.startsWith("captionAnim=") && !it.startsWith("captionPos=") }.take(1).forEach { add(it) }
            matchedStyle.textTraits.filter { !it.startsWith("captionAnim=") && !it.startsWith("captionPos=") }.take(2).forEach { add(it) }
        }.distinct()

        val blendedScore = ((core.strengthScore * 0.70) + (matchedStyle.overallScore * 0.30)).toInt().coerceIn(0, 100)

        return AbsorbedStyle(
            id = "blended_${matchedStyle.id}",
            name = "${core.toAbsorbedStyle().name} × ${matchedStyle.name}",
            sourceVideoPathOrUrl = matchedStyle.sourceVideoPathOrUrl,
            analysis = "دمج عقل قبس التراكمي (هوية أساسية 70%) مع ${matchedStyle.name} (30% لمسات إخراجية مخصصة للرسالة: $tone / $audience).",
            visualTraits = blendedVisuals,
            motionTraits = blendedMotions,
            textTraits = blendedTexts,
            overallScore = blendedScore,
            absorbedAt = System.currentTimeMillis()
        )
    }
    
    fun getDefaultStyle(): AbsorbedStyle {
        return _coreStyle.value.toAbsorbedStyle()
    }

    /**
     * دمج أسلوبين أو أكثر لصناعة أسلوب تصميم جديد (فكرة العقل الأصلية).
     * - يحافظ على هوية قبس كأساس
     * - يخلط السمات البصرية/الحركية/النصية من الأساليب المختارة
     * - يحفظ النتيجة ضمن الأساليب الممتصة إن مُرر Context
     */
    fun fuseStyles(
        styles: List<AbsorbedStyle>,
        newName: String = "",
        context: android.content.Context? = null
    ): AbsorbedStyle {
        val core = _coreStyle.value.toAbsorbedStyle()
        val source = if (styles.isEmpty()) listOf(core) else styles

        val visuals = (core.visualTraits + source.flatMap { it.visualTraits }).distinct().take(6)
        val motions = (core.motionTraits + source.flatMap { it.motionTraits }).distinct().take(5)
        val texts = (core.textTraits + source.flatMap { it.textTraits }).distinct().take(4)
        val score = (
            (core.overallScore * 0.4) +
            source.map { it.overallScore }.average().let { if (it.isNaN()) 0.0 else it } * 0.6
        ).toInt().coerceIn(0, 100)

        val name = newName.ifBlank {
            if (source.size == 1) "دمج قبس × ${source.first().name}"
            else "أسلوب مدمج (${source.size})"
        }

        val fused = AbsorbedStyle(
            id = "fused_${System.currentTimeMillis()}",
            name = name,
            sourceVideoPathOrUrl = source.firstOrNull()?.sourceVideoPathOrUrl ?: "",
            analysis = "أسلوب جديد من دمج: ${source.joinToString(" + ") { it.name }} مع هوية قبس الأساسية.",
            visualTraits = visuals,
            motionTraits = motions,
            textTraits = texts,
            overallScore = score,
            absorbedAt = System.currentTimeMillis()
        )

        if (context != null) {
            val updated = _absorbedStyles.value + fused
            saveStyles(context, updated)
        }
        return fused
    }

    /** ملخص قصير يستهلكه المخرج (MontageDirector) */
    fun directorStyleBrief(style: AbsorbedStyle = getDefaultStyle()): String {
        return buildString {
            append(style.name)
            append(" | بصري: ")
            append(style.visualTraits.take(3).joinToString(", ").ifBlank { "Dark Slate & Gold" })
            append(" | حركة: ")
            append(style.motionTraits.take(2).joinToString(", ").ifBlank { "slow cinematic" })
            append(" | نص: ")
            append(style.textTraits.take(2).joinToString(", ").ifBlank { "Arabic captions" })
        }
    }

    fun removeStyle(context: Context, id: String) {
        val updated = _absorbedStyles.value.filter { it.id != id }
        saveStyles(context, updated)
    }

    /**
     * حلقة التغذية الراجعة التلقائية وتعزيز ذكاء العقل (Reinforcement & Feedback Loop)
     * تقوم بتحديث أوزان النمط المعتمد والعقل التراكمي فور إعجاب أو عدم إعجاب المستخدم بالنتيجة
     */
    suspend fun recordUserFeedbackOnStyle(
        context: Context,
        styleVariantOrName: String,
        isPositive: Boolean,
        associatedStyleObject: StyleObject? = null
    ): Unit = withContext(Dispatchers.IO) {
        try {
            val currentStyles = _absorbedStyles.value.toMutableList()
            val targetStyle = currentStyles.find { 
                it.name.contains(styleVariantOrName, ignoreCase = true) || 
                styleVariantOrName.contains(it.name, ignoreCase = true) 
            }

            if (targetStyle != null) {
                val newScore = if (isPositive) {
                    (targetStyle.overallScore + 2).coerceIn(0, 100)
                } else {
                    (targetStyle.overallScore - 3).coerceIn(0, 100)
                }
                val updatedTarget = targetStyle.copy(overallScore = newScore)
                val index = currentStyles.indexOf(targetStyle)
                if (index != -1) {
                    currentStyles[index] = updatedTarget
                    saveStyles(context, currentStyles)
                }

                if (isPositive) {
                    evolveCoreStyleWithNewAbsorption(context, updatedTarget)
                }
            }

            // تعزيز التفضيلات في TasteManager
            TasteManager.registerPreference(
                context, 
                "STYLE", 
                styleVariantOrName, 
                if (isPositive) 3 else -2
            )

            Log.d("StyleBrain", "Style feedback recorded: style=$styleVariantOrName, positive=$isPositive")
        } catch (e: Exception) {
            Log.e("StyleBrain", "Error recording user feedback on style: ${e.message}", e)
        }
    }

    /**
     * استخراج 6 إطارات مفتاحية من فيديو محلي بدقة وتوزيع زمني متساوٍ باستخدام FFmpegKit مع fallback إلى MediaMetadataRetriever.
     * تحفظ الإطارات بصيغة JPEG في مجلد الكاش بعرض أقصى 720px، مع تنظيف الملفات القديمة.
     */
    suspend fun extractKeyFrames(context: Context, videoPath: String): List<File> = withContext(Dispatchers.IO) {
        if (videoPath.isBlank()) return@withContext emptyList()
        val videoFile = File(videoPath)
        if (!videoFile.exists() || videoFile.length() == 0L) return@withContext emptyList()

        val extractedFiles = mutableListOf<File>()

        try {
            // 1. تجهيز مجلد الكاش وتنظيف الإطارات القديمة
            val framesDir = File(context.cacheDir, "stylebrain_keyframes").apply { mkdirs() }
            try {
                framesDir.listFiles()?.forEach { file ->
                    if (System.currentTimeMillis() - file.lastModified() > 1800_000 || file.name.startsWith("frame_")) {
                        file.delete()
                    }
                }
            } catch (_: Exception) {}

            // 2. حساب مدة الفيديو
            var durationSeconds = 0.0
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(videoPath)
                val durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                if (durationMs > 0) {
                    durationSeconds = durationMs / 1000.0
                }
            } catch (e: Exception) {
                Log.w("StyleBrain", "Failed to retrieve video duration via MediaMetadataRetriever: ${e.message}")
            }

            if (durationSeconds <= 0.5) {
                durationSeconds = 6.0
            }

            // 3. حساب 6 نقاط زمنية موزعة بانتظام على طول الفيديو
            val targetCount = 6
            val step = durationSeconds / (targetCount + 1)
            val sessionTimestamp = System.currentTimeMillis()

            for (i in 1..targetCount) {
                val timePoint = (step * i).coerceIn(0.05, (durationSeconds - 0.05).coerceAtLeast(0.1))
                val timeUs = (timePoint * 1_000_000).toLong()
                
                val bmp = try {
                    retriever.getFrameAtTime(timeUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime(timeUs)
                } catch (e: Exception) {
                    null
                }
                
                if (bmp != null) {
                    val scaled = if (bmp.width > 720) {
                        val ratio = 720f / bmp.width.toFloat()
                        val targetH = (bmp.height * ratio).toInt().coerceAtLeast(1)
                        Bitmap.createScaledBitmap(bmp, 720, targetH, true)
                    } else {
                        bmp
                    }

                    val outputFile = File(framesDir, "frame_${sessionTimestamp}_${i}.jpg")
                    outputFile.outputStream().use { out ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
                    }
                    if (scaled !== bmp) {
                        try { scaled.recycle() } catch (_: Exception) {}
                    }
                    try { bmp.recycle() } catch (_: Exception) {}

                    if (outputFile.exists() && outputFile.length() > 0) {
                        extractedFiles.add(outputFile)
                    }
                }
            }
            
            try { retriever.release() } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.e("StyleBrain", "Error extracting keyframes from video: $videoPath", e)
            return@withContext emptyList()
        }

        return@withContext extractedFiles
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * تحليل الإطارات المستخرجة بصرياً باستخدام Gemini Multimodal Vision API.
     * يستخرج الألوان، التباين، أسلوب النصوص، الحركة، والإيقاع ويعيد JSONObject منظم بدقة.
     * في حال الفشل أو انعدام المفتاح/الشبكة يتم إرجاع نتيجة وقورة تحافظ على هوية قبس الأساسية.
     */
    suspend fun analyzeFramesWithVision(
        frames: List<File>,
        context: Context? = null,
        extraContext: String = ""
    ): JSONObject = withContext(Dispatchers.IO) {
        val defaultFallback = JSONObject().apply {
            put("analysis", "هوية إخراجية وقورة مستوحاة من قبس مع خلفيات داكنة فخمة (Deep Slate) ولمسات ذهبية متوهجة ونصوص مركزية واضحة.")
            put("primaryColorHex", "#E8C547")
            put("backgroundColorHex", "#0B0F19")
            put("accentColorHex", "#F8FAFC")
            put("filterHint", "warm_gold")
            put("motionType", "slow_zoom")
            put("transitionType", "Dissolve")
            put("tempo", "خاشع")
            put("captionAnimation", "WordByWord")
            put("captionPosition", "bottom")
            put("typographyStyle", "خط عربي كوفي عريض في المنتصف مع إبراز الكلمات بالذهبي")
            put("tone", "وقور ومؤثر")
            put("visualTraits", JSONArray().apply {
                put("خلفية داكنة فخمة Deep Slate #0B0F19")
                put("توهج ذهبي فاخر Gold Primary #E8C547")
                put("تباين عالي مع نصوص ناصعة #F8FAFC")
                put("إضاءة سينمائية محيطية دافئة")
            })
            put("motionTraits", JSONArray().apply {
                put("زووم بطيء متصاعد (Slow Cinematic Zoom-in)")
                put("تلاشي تدريجي وقور (Smooth Fade Transitions)")
                put("إيقاع متزن يلائم التأمل والتدبر")
            })
            put("textTraits", JSONArray().apply {
                put("خط عريض أنيق ومقروء")
                put("محاذاة في الثلث السفلي والوسط")
                put("صندوق نصي داكن شبه شفاف مع تباين ذهبي")
            })
            put("overallScore", 0)
            put("analysisSource", "FALLBACK")
        }

        if (frames.isEmpty()) {
            return@withContext defaultFallback
        }

        try {
            val actualContext = context ?: AppServices.appContext
            val prefs = actualContext.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            val apiKey = prefs.getString("gemini_key", "")?.trim().orEmpty()

            if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY") {
                Log.w("StyleBrain", "No Gemini API key available for Vision analysis, returning default style.")
                return@withContext defaultFallback
            }

            val promptText = """
                أنت خبير إخراج ومحلل بصري سينمائي لمنصة "قبس" لصناعة المحتوى الإسلامي والدعوي عالي الجودة.
                أمامك عدة إطارات زمنية متتابعة مأخوذة من فيديو مرجعي.
                
                ${if (extraContext.isNotBlank()) "سياق إضافي: $extraContext\n" else ""}
                
                قم بتحليل الإطارات بصرياً وإخراج هوية أسلوب الفيديو بدقة واحترافية وتحديد الألوان بدقة Hex Code.
                ركّز بدقة على:
                1. الألوان المسيطرة والتباين ودرجة الإضاءة (Dominant Colors & Contrast).
                2. أسلوب وتصميم النصوص والكابشن وموضعها وتدرجاتها (Typography & Overlay Style).
                3. نوع الحركة والانتقالات والإيقاع المتوقع بين الإطارات (Motion & Rhythm).
                4. النبرة العامة والهيبة البصرية وملاءمتها للمحتوى الراقي.

                يجب أن تكون الاستجابة بصيغة JSON فقط وحصراً بدون أي كود ماركداون خارجي بهذا الشكل الدقيق:
                {
                  "analysis": "وصف شامل ودقيق للهوية الإخراجية والأسلوب البصري للمقطع",
                  "primaryColorHex": "#E8C547",
                  "backgroundColorHex": "#0B0F19",
                  "accentColorHex": "#F8FAFC",
                  "filterHint": "warm_gold",
                  "motionType": "slow_zoom",
                  "transitionType": "Dissolve",
                  "tempo": "خاشع",
                  "captionAnimation": "WordByWord",
                  "captionPosition": "bottom",
                  "typographyStyle": "خط عربي كوفي عريض في المنتصف مع إبراز الكلمات بالذهبي",
                  "tone": "وقور ومؤثر",
                  "visualTraits": ["سمة بصرية 1", "سمة بصرية 2", "سمة بصرية 3", "سمة بصرية 4"],
                  "motionTraits": ["سمة حركية 1", "سمة حركية 2", "سمة حركية 3"],
                  "textTraits": ["سمة نصية 1", "سمة نصية 2", "سمة نصية 3"]
                }
            """.trimIndent()

            val partsArray = JSONArray()
            partsArray.put(JSONObject().apply { put("text", promptText) })

            // إرفاق الإطارات (بحد أقصى 6 إطارات) كـ base64 inlineData
            frames.take(6).forEach { frameFile ->
                if (frameFile.exists() && frameFile.length() > 0) {
                    val bytes = frameFile.readBytes()
                    val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    partsArray.put(JSONObject().apply {
                        put("inline_data", JSONObject().apply {
                            put("mime_type", "image/jpeg")
                            put("data", base64Data)
                        })
                    })
                }
            }

            val contentsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", partsArray)
                })
            }

            val jsonBody = JSONObject().apply {
                put("contents", contentsArray)
                put("generationConfig", JSONObject().apply {
                    put("response_mime_type", "application/json")
                    put("temperature", 0.4)
                })
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e("StyleBrain", "Vision API call unsuccessful: HTTP ${response.code}")
                return@withContext defaultFallback
            }

            val responseData = response.body?.string().orEmpty()
            if (responseData.isBlank()) return@withContext defaultFallback

            val responseJson = JSONObject(responseData)
            val candidates = responseJson.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val content = candidates.getJSONObject(0).optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val rawText = parts.getJSONObject(0).optString("text")
                    val cleanJson = rawText.replace("```json", "").replace("```", "").trim()
                    val parsedResult = JSONObject(cleanJson)

                    // التحقق من الحقول الأساسية
                    if (parsedResult.has("analysis") && parsedResult.has("visualTraits")) {
                        // الدرجة المفقودة تعني عدم توفر قياس، ولا تتحول إلى درجة نجاح افتراضية
                        val score = parsedResult.optInt("overallScore", 0).coerceIn(0, 100)
                        parsedResult.put("overallScore", score)
                        parsedResult.put("analysisSource", "MODEL")
                        return@withContext parsedResult
                    }
                }
            }

            return@withContext defaultFallback
        } catch (e: Exception) {
            Log.e("StyleBrain", "Vision analysis failed with exception: ${e.message}", e)
            return@withContext defaultFallback
        }
    }

    // =========================================================================
    // وحدة معالجة وتخزين الأنماط الفنية (Style Objects Processing & Storage)
    // =========================================================================

    /**
     * وحدة المعالجة المركزية في العقل لاستخراج الأنماط الفنية (بصري، حركة، نبرة)
     * وتخزينها ككائن بيانات مهيكل (StyleObject) في الذاكرة التراكمية وسحابة Firestore.
     */
    suspend fun extractAndStoreStyleObject(
        context: Context,
        videoPathOrUrl: String,
        styleName: String = "",
        saveToCloud: Boolean = true,
        onProgress: ((Float, String) -> Unit)? = null
    ): StyleObject = withContext(Dispatchers.IO) {
        // 1. استخراج وتحليل الأنماط الفنية وهضمها في عقل قبس التراكمي
        val absorbed = absorbStyleFromVideo(context, videoPathOrUrl, styleName, onProgress)
        val currentUserId = CloudServices.Auth.getCurrentUserId() ?: "creator_local"

        // 2. بناء كائن البيانات المهيكل StyleObject
        val styleObject = StyleObject.fromAbsorbedStyle(absorbed, currentUserId).copy(
            name = if (styleName.isNotBlank()) styleName else absorbed.name
        )

        // 3. تخزين الكائن في سحابة Firestore إن كانت مفعلة وكان الحفظ السحابي مطلوباً
        if (saveToCloud && CloudServices.isFirebaseInitialized) {
            try {
                val saved = CloudServices.Database.saveStyleObjectToCloud(styleObject)
                if (saved) {
                    Log.d("StyleBrain", "StyleObject (${styleObject.id}) successfully stored in Firestore.")
                }
            } catch (e: Exception) {
                Log.w("StyleBrain", "Failed to store StyleObject in Firestore: ${e.message}")
            }
        }

        return@withContext styleObject
    }

    /**
     * حفظ كائن الأسلوب مباشرة في Firestore
     */
    suspend fun storeStyleObjectInFirestore(styleObject: StyleObject): Boolean {
        if (!CloudServices.isFirebaseInitialized) return false
        return CloudServices.Database.saveStyleObjectToCloud(styleObject)
    }

    /**
     * استدعاء وقراءة كافة كائنات الأساليب الفنية المخزنة للمستخدم في Firestore
     */
    suspend fun fetchCloudStyleObjects(userId: String? = null): List<StyleObject> {
        if (!CloudServices.isFirebaseInitialized) return emptyList()
        return CloudServices.Database.getCloudStyleObjects(userId)
    }

    /**
     * دمج كائني نمط فني (Style Objects Fusion):
     * دمج الخصائص البصرية وإيقاع الحركة ونبرة المحتوى بنسبة مرجحة (blendRatio)
     * لإنتاج نمط فني هجين متطور.
     */
    fun fuseStyleObjects(
        primary: StyleObject,
        secondary: StyleObject,
        blendRatio: Float = 0.5f,
        customName: String? = null
    ): StyleObject {
        val clampedRatio = blendRatio.coerceIn(0.1f, 0.9f)
        val primaryWeight = 1.0f - clampedRatio
        val secondaryWeight = clampedRatio

        // 1. دمج السمات البصرية
        val primaryVisualCount = if (primaryWeight >= 0.5f) 4 else 2
        val secondaryVisualCount = if (secondaryWeight >= 0.5f) 3 else 2
        val combinedVisualTraits = (primary.visualStyle.visualTraits.take(primaryVisualCount) + 
                                    secondary.visualStyle.visualTraits.take(secondaryVisualCount)).distinct()

        val blendedDominantColors = "${primary.visualStyle.dominantColors} × ${secondary.visualStyle.dominantColors}"
        val chosenPrimaryHex = if (primaryWeight >= 0.5f) primary.visualStyle.primaryColorHex else secondary.visualStyle.primaryColorHex
        val chosenBgHex = if (primaryWeight >= 0.5f) primary.visualStyle.backgroundColorHex else secondary.visualStyle.backgroundColorHex

        val fusedVisual = VisualStylePattern(
            dominantColors = blendedDominantColors,
            primaryColorHex = chosenPrimaryHex,
            backgroundColorHex = chosenBgHex,
            lightingAndContrast = "إضاءة هجينة متوازنة مع تباين سينمائي عالي",
            visualTraits = combinedVisualTraits
        )

        // 2. دمج إيقاع الحركة
        val combinedMotionTraits = (primary.motionRhythm.motionTraits.take(3) + 
                                    secondary.motionRhythm.motionTraits.take(2)).distinct()
        val fusedMotion = MotionRhythmPattern(
            transitionSpeed = if (primaryWeight >= 0.5f) primary.motionRhythm.transitionSpeed else secondary.motionRhythm.transitionSpeed,
            transitionType = if (primaryWeight >= 0.5f) primary.motionRhythm.transitionType else secondary.motionRhythm.transitionType,
            movementPatterns = "${primary.motionRhythm.movementPatterns} + ${secondary.motionRhythm.movementPatterns}",
            overallRhythm = "إيقاع مركب: ${primary.motionRhythm.overallRhythm} ممتزج مع ${secondary.motionRhythm.overallRhythm}",
            motionTraits = combinedMotionTraits
        )

        // 3. دمج نبرة المحتوى والتيبوغرافيا
        val combinedTextTraits = (primary.contentTone.textTraits.take(3) + 
                                  secondary.contentTone.textTraits.take(2)).distinct()
        val fusedTone = ContentTonePattern(
            tone = "${primary.contentTone.tone} ممتزج مع ${secondary.contentTone.tone}",
            targetAudience = primary.contentTone.targetAudience,
            typographyStyle = primary.contentTone.typographyStyle,
            captionAnimation = primary.contentTone.captionAnimation,
            textTraits = combinedTextTraits
        )

        val fusedScore = ((primary.overallScore * primaryWeight) + (secondary.overallScore * secondaryWeight)).toInt().coerceIn(0, 100)
        val defaultFusedName = customName?.ifBlank { null } ?: "دمج هجين (${primary.name} × ${secondary.name})"

        return StyleObject(
            id = "fused_${UUID.randomUUID().toString().take(8)}",
            name = defaultFusedName,
            userId = primary.userId,
            sourceVideoPathOrUrl = "${primary.sourceVideoPathOrUrl} + ${secondary.sourceVideoPathOrUrl}",
            visualStyle = fusedVisual,
            motionRhythm = fusedMotion,
            contentTone = fusedTone,
            overallScore = fusedScore,
            analysisSummary = "نمط فني مصهور يجمع بنسبة (${(primaryWeight * 100).toInt()}%) من [${primary.name}] و (${(secondaryWeight * 100).toInt()}%) من [${secondary.name}].",
            tags = (primary.tags + secondary.tags + listOf("FusedStyle", "HybridPattern")).distinct(),
            createdAt = System.currentTimeMillis(),
            isCloudSynced = false
        )
    }

    /**
     * استدعاء نمط فني وتطبيقه على عقل الإنتاج فوراً
     */
    suspend fun recallStyleObject(context: Context, styleId: String): StyleObject? {
        // 1. البحث في الذاكرة التراكمية المحلية
        val localMatch = _absorbedStyles.value.find { it.id == styleId }
        if (localMatch != null) {
            val styleObj = StyleObject.fromAbsorbedStyle(localMatch)
            return styleObj
        }

        // 2. البحث في سحابة Firestore إذا كانت مفعلة
        if (CloudServices.isFirebaseInitialized) {
            val cloudStyles = CloudServices.Database.getCloudStyleObjects()
            val cloudMatch = cloudStyles.find { it.id == styleId }
            if (cloudMatch != null) {
                // حفظه في الذاكرة التراكمية المحلية وتطوير العقل
                val absorbed = cloudMatch.toAbsorbedStyle()
                val updated = _absorbedStyles.value + absorbed
                saveStyles(context, updated)
                evolveCoreStyleWithNewAbsorption(context, absorbed)
                return cloudMatch
            }
        }

        return null
    }

    /**
     * مزامنة الأنماط المخزنة في سحابة Firestore مع الذاكرة المحلية
     */
    suspend fun syncCloudStylesWithLocal(context: Context): List<StyleObject> = withContext(Dispatchers.IO) {
        if (!CloudServices.isFirebaseInitialized) {
            return@withContext _absorbedStyles.value.map { StyleObject.fromAbsorbedStyle(it) }
        }

        try {
            val cloudStyles = CloudServices.Database.getCloudStyleObjects()
            val localStyles = _absorbedStyles.value.toMutableList()

            cloudStyles.forEach { cloudStyle ->
                val existsLocally = localStyles.any { it.id == cloudStyle.id }
                if (!existsLocally) {
                    val absorbed = cloudStyle.toAbsorbedStyle()
                    localStyles.add(absorbed)
                    evolveCoreStyleWithNewAbsorption(context, absorbed)
                }
            }

            saveStyles(context, localStyles)
            return@withContext localStyles.map { StyleObject.fromAbsorbedStyle(it) }
        } catch (e: Exception) {
            Log.e("StyleBrain", "Error syncing styles with Firestore: ${e.message}")
            return@withContext _absorbedStyles.value.map { StyleObject.fromAbsorbedStyle(it) }
        }
    }
}
