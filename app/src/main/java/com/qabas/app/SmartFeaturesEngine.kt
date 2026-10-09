package com.qabas.app

import androidx.compose.ui.graphics.Color
import java.util.UUID

/**
 * SmartFeaturesEngine - The "Brain" behind the pro features.
 * Simulates high-end AI capabilities found in apps like OpusClip & Captions.ai.
 */
object SmartFeaturesEngine {

    /**
     * Simulates AI-powered viral potential scoring for a script.
     */
    fun predictViralPotential(script: String): ViralScore {
        val length = script.length
        val hookStrength = if (script.startsWith("هل تعلم") || script.startsWith("لماذا")) 90 else 65
        val baseScore = (hookStrength + (length % 20)).coerceIn(40, 98)
        
        return ViralScore(
            overallScore = baseScore,
            hookGrade = if (hookStrength > 80) "A+" else "B",
            retentionEstimate = "85%",
            trendingTopics = listOf("تطوير الذات", "قصص الأنبياء", "عمارة الأرض"),
            improvementTips = listOf(
                "اجعل الجملة الأولى أكثر إثارة للتساؤل.",
                "أضف كلمات مفتاحية مثل 'سر' أو 'حقيقة'.",
                "استخدم نبرة صوت حماسية في المنتصف."
            )
        )
    }

    /**
     * Interpolates property values based on keyframes.
     */
    fun interpolateProperty(keyframes: List<EditorKeyframe>, currentTimeMs: Long, property: String, defaultValue: Float): Float {
        val relevantKeyframes = keyframes.filter { it.property == property }.sortedBy { it.timeMs }
        if (relevantKeyframes.isEmpty()) return defaultValue
        
        // Find surrounding keyframes
        val before = relevantKeyframes.lastOrNull { it.timeMs <= currentTimeMs }
        val after = relevantKeyframes.firstOrNull { it.timeMs > currentTimeMs }
        
        return when {
            before != null && after != null -> {
                val progress = (currentTimeMs - before.timeMs).toFloat() / (after.timeMs - before.timeMs).toFloat()
                before.value + (after.value - before.value) * progress // Linear interpolation
            }
            before != null -> before.value
            after != null -> after.value
            else -> defaultValue
        }
    }

    /**
     * Generates simulated Kinetic Captions for a clip.
     */
    fun generateKineticCaptions(text: String, durationMs: Long): List<KineticCaption> {
        val words = text.split(" ").filter { it.isNotBlank() }
        val timePerWord = durationMs / words.size.coerceAtLeast(1)
        
        return words.mapIndexed { index, word ->
            KineticCaption(
                text = word,
                startTimeMs = index * timePerWord,
                endTimeMs = (index + 1) * timePerWord,
                style = KineticCaptionStyle.values().random(),
                color = if (index % 3 == 0) Color(0xFFFACC15) else Color.White
            )
        }
    }

    /**
     * Generates a deterministic pseudo-random waveform for an audio URI.
     * In a real app, this would analyze the actual audio file.
     */
    fun generateWaveformData(uri: String, points: Int): List<Float> {
        val seed = uri.hashCode().toLong()
        val random = java.util.Random(seed)
        return List(points) {
            // Generates values between 0.1 and 1.0 with some "clumping" for realism
            (0.2f + random.nextFloat() * 0.8f) * (if (it % 5 == 0) 0.4f else 1.0f)
        }
    }
}

data class ViralScore(
    val overallScore: Int,
    val hookGrade: String,
    val retentionEstimate: String,
    val trendingTopics: List<String>,
    val improvementTips: List<String>
)

data class KineticCaption(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val style: KineticCaptionStyle,
    val color: Color
)

enum class KineticCaptionStyle {
    BOUNCE, SLIDE, GLOW, SHAKE, POP
}
