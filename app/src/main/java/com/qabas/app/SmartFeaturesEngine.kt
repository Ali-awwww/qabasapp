package com.qabas.app

import androidx.compose.ui.graphics.Color

/**
 * SmartFeaturesEngine — deterministic keyframe interpolation & script hook/readability heuristics.
 */
object SmartFeaturesEngine {

    /**
     * Evaluates script pacing, hook clarity, and call-to-action quality deterministically (no random/static fake topics).
     */
    fun predictViralPotential(script: String): ViralScore {
        val cleaned = script.trim()
        if (cleaned.isBlank()) {
            return ViralScore(
                overallScore = 0,
                hookGrade = "-",
                retentionEstimate = "0%",
                trendingTopics = emptyList(),
                improvementTips = listOf("أدخل نصاً أو فكرة ليتم تحليل قوة الخطاف والإيقاع.")
            )
        }

        val words = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
        val firstLine = cleaned.lines().firstOrNull()?.trim().orEmpty()
        val hasQuestionHook = firstLine.contains("؟") ||
            firstLine.startsWith("هل") ||
            firstLine.startsWith("لماذا") ||
            firstLine.startsWith("كيف") ||
            firstLine.startsWith("ما")
        val hasCta = cleaned.contains("شارك") ||
            cleaned.contains("تابع") ||
            cleaned.contains("احفظ") ||
            cleaned.contains("تعليق")
        val optimalWordCount = words.size in 35..140

        var score = 55
        if (hasQuestionHook) score += 20
        if (optimalWordCount) score += 15 else if (words.size in 15..200) score += 8
        if (hasCta) score += 8
        score = score.coerceIn(30, 96)

        val grade = when {
            score >= 88 -> "A+"
            score >= 76 -> "A"
            score >= 65 -> "B"
            else -> "C"
        }

        val tips = mutableListOf<String>()
        if (!hasQuestionHook) tips.add("ابدأ بسؤال أو عبارة مفتاحية جاذبة في أول ٣ ثوانٍ.")
        if (!optimalWordCount) tips.add("الطول المثالي للمقطع القصير بين ٤٠ و١٢٠ كلمة (الحالي: ${words.size} كلمة).")
        if (!hasCta) tips.add("اختم بدعوة واضحة للتفاعل مثل الحفظ أو المشاركة.")
        if (tips.isEmpty()) tips.add("توازن ممتاز بين قوة البداية وطول النص والخاتمة.")

        val topics = words
            .map { it.trim('،', '.', '؟', '!', ':', '"') }
            .filter { it.length >= 4 }
            .distinct()
            .take(3)

        val estimatedRetention = ((score * 0.85f).toInt().coerceIn(45, 92))

        return ViralScore(
            overallScore = score,
            hookGrade = grade,
            retentionEstimate = "$estimatedRetention%",
            trendingTopics = topics,
            improvementTips = tips
        )
    }

    /**
     * Linearly interpolates property values between surrounding keyframes.
     */
    fun interpolateProperty(
        keyframes: List<EditorKeyframe>,
        currentTimeMs: Long,
        property: String,
        defaultValue: Float
    ): Float {
        val relevantKeyframes = keyframes.filter { it.property == property }.sortedBy { it.timeMs }
        if (relevantKeyframes.isEmpty()) return defaultValue

        val before = relevantKeyframes.lastOrNull { it.timeMs <= currentTimeMs }
        val after = relevantKeyframes.firstOrNull { it.timeMs > currentTimeMs }

        return when {
            before != null && after != null -> {
                val delta = (after.timeMs - before.timeMs).coerceAtLeast(1L)
                val progress = (currentTimeMs - before.timeMs).toFloat() / delta.toFloat()
                before.value + (after.value - before.value) * progress
            }
            before != null -> before.value
            after != null -> after.value
            else -> defaultValue
        }
    }

    /**
     * Generates deterministic, time-synced kinetic captions from text.
     */
    fun generateKineticCaptions(text: String, durationMs: Long): List<KineticCaption> {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val timePerWord = (durationMs.coerceAtLeast(1000L)) / words.size
        val styles = KineticCaptionStyle.entries

        return words.mapIndexed { index, word ->
            KineticCaption(
                text = word,
                startTimeMs = index * timePerWord,
                endTimeMs = (index + 1) * timePerWord,
                style = styles[index % styles.size],
                color = if (index % 3 == 0) Color(0xFFFFD60A) else Color.White
            )
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
