package com.qabas.app

import android.util.Log
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * المخرج السينمائي لقبس — يحول الفكرة إلى خطة مونتاج قابلة للتنفيذ.
 * يقرأ توجيه StyleDirective (بما فيه motionType من Optical Flow / Farneback)
 * ويحقنه في كل مشهد ليصل إلى VideoEngineManager → VideoProcessor.
 */
object MontageDirector {

    private const val TAG = "MontageDirector"

    data class ScenePlan(
        val purpose: String,
        val durationSec: Int,
        val onScreenText: String,
        val visualQuery: String,
        val visualQueryAr: String,
        val transition: String,
        val motion: String,
        val tempo: String,
        val primaryColorHex: String = "#8B5CF6",
        val backgroundColorHex: String = "#0B0F19",
        val captionAnimation: String = "FadeIn",
        val captionPosition: String = "bottom",
        val filterHint: String = "ai_violet"
    )

    data class EditPlan(
        val title: String,
        val mood: String,
        val totalDurationSec: Int,
        val styleName: String,
        val styleBrief: String,
        val ambientHint: String,
        val scenes: List<ScenePlan>,
        val directive: StyleDirective? = null,
        val promptBoost: String = ""
    )

    suspend fun createPlan(
        idea: String,
        tone: String = "خاشع",
        targetDurationSec: Int = 15,
        audience: String = "الجمهور العام",
        styleDescription: String = "",
        preferredStyleId: String? = null,
        directStyle: AbsorbedStyle? = null
    ): EditPlan = withContext(Dispatchers.IO) {
        val safeDuration = targetDurationSec.coerceIn(10, 90)
        val cleanIdea = idea.trim().ifBlank { "محتوى إسلامي خاشع" }

        val absorbed = try {
            if (directStyle != null) {
                directStyle
            } else {
                val preferred = preferredStyleId?.let { id ->
                    StyleBrain.absorbedStyles.value.find { it.id == id }
                }
                preferred ?: StyleBrain.chooseBestStyleForIdea(cleanIdea, safeDuration, tone, audience)
            }
        } catch (e: Exception) {
            Log.w(TAG, "StyleBrain chooseBestStyle failed: ${e.message}")
            directStyle ?: StyleBrain.getDefaultStyle()
        } ?: directStyle ?: StyleBrain.getDefaultStyle()

        val directive = absorbed.toStyleDirective()
        val styleBrief = buildStyleBrief(absorbed, styleDescription, tone)
        val transition = directive.transitionType.ifBlank { pickTransition(absorbed, tone) }
        val tempo = if (tone.isNotBlank() && (tone.contains("خاشع") || tone.contains("ملحمي"))) {
            pickTempo(tone, absorbed)
        } else {
            directive.tempo
        }
        val ambient = pickAmbient(tone)
        val ofMotion = normalizeMotion(directive.motionType)

        val topic = detectTopic(cleanIdea)
        val scenePlans = buildScenePlans(
            idea = cleanIdea,
            topic = topic,
            totalDuration = safeDuration,
            tone = tone,
            transition = transition,
            tempo = tempo,
            directive = directive,
            ofMotion = ofMotion
        )

        val plan = EditPlan(
            title = cleanIdea.take(48),
            mood = tone.ifBlank { directive.tempo },
            totalDurationSec = safeDuration,
            styleName = absorbed.name,
            styleBrief = styleBrief,
            ambientHint = ambient,
            scenes = scenePlans,
            directive = directive,
            promptBoost = directive.promptBoost
        )

        SystemLogsManager.addLog(
            "DIRECTOR",
            "المخرج طبّق نمطاً ملموساً: ${absorbed.name} | ${directive.filterHint} | ${directive.primaryColorHex} | حركة $ofMotion | انتقال ${directive.transitionType} | ${scenePlans.size} مشاهد",
            Color(0xFFE8C547)
        )
        Log.d(TAG, "OF motion=$ofMotion | filter=${directive.filterHint} | primary=${directive.primaryColorHex}")
        plan
    }

    fun toScenes(plan: EditPlan): List<Scene> {
        val d = plan.directive
        return plan.scenes.map { sp ->
            val styleTag = buildString {
                append(sp.visualQuery)
                append(" | ")
                append(sp.visualQueryAr)
                append(" | purpose=")
                append(sp.purpose)
                append(" | motion=")
                append(sp.motion)
                append(" | color=")
                append(sp.primaryColorHex)
                append(" | bg=")
                append(sp.backgroundColorHex)
                append(" | filter=")
                append(sp.filterHint)
                append(" | caption=")
                append(sp.captionAnimation)
                append("@")
                append(sp.captionPosition)
                if (d != null) {
                    append(" | primary:")
                    append(d.primaryColorHex)
                    append(" | bg:")
                    append(d.backgroundColorHex)
                }
                if (plan.promptBoost.isNotBlank()) {
                    append(" | ")
                    append(plan.promptBoost.take(180))
                }
            }
            val estimatedDuration = if (sp.onScreenText.isNotBlank()) {
                ProductionPowerKit.estimateArabicSpeechDuration(sp.onScreenText)
            } else {
                sp.durationSec
            }

            Scene(
                title = sp.onScreenText,
                description = styleTag,
                durationInSeconds = estimatedDuration.coerceIn(3, 30),
                visualEffect = sp.motion,
                tempo = sp.tempo,
                transitionType = sp.transition,
                mediaUrl = null
            )
        }
    }

    suspend fun directToScenes(
        idea: String,
        tone: String = "خاشع",
        targetDurationSec: Int = 15,
        audience: String = "الجمهور العام",
        styleDescription: String = "",
        preferredStyleId: String? = null,
        directStyle: AbsorbedStyle? = null
    ): List<Scene> {
        val plan = createPlan(idea, tone, targetDurationSec, audience, styleDescription, preferredStyleId, directStyle)
        val directive = plan.directive
        val ofMotion = normalizeMotion(directive?.motionType ?: "slow_zoom")
        val filter = directive?.filterHint ?: "ai_violet"

        val geminiScenes = try {
            AppServices.generateScript(
                idea = idea,
                styleDescription = plan.styleBrief,
                contentType = "Reels/Shorts",
                contentTone = tone
            )
        } catch (e: Exception) {
            Log.w(TAG, "Gemini failed in directToScenes, fallback to hardcoded", e)
            emptyList()
        }

        if (geminiScenes.isNotEmpty() && geminiScenes.size >= 2) {
            // Enhance Gemini scenes with our directed style (optical flow, filter, etc.)
            return geminiScenes.map { scene ->
                val enhancedQuery = buildString {
                    append(scene.description)
                    if (!directive?.visualKeywordsEn.isNullOrEmpty()) append(" ${directive?.visualKeywordsEn?.joinToString(" ")}")
                    append(" | motion=$ofMotion")
                    append(" | filter=$filter")
                    append(" | caption=${directive?.captionAnimation ?: "FadeIn"}")
                }
                scene.copy(
                    description = enhancedQuery,
                    transitionType = if (scene.transitionType.isBlank() || scene.transitionType == "Fade") plan.scenes.firstOrNull()?.transition ?: "Dissolve" else scene.transitionType,
                    tempo = if (scene.tempo.isBlank() || scene.tempo == "متوسط") plan.scenes.firstOrNull()?.tempo ?: "متوسط" else scene.tempo,
                    visualEffect = if (scene.visualEffect.isBlank()) filter else scene.visualEffect
                )
            }
        }

        // Fallback to hardcoded plan if Gemini fails
        val scenes = toScenes(plan)
        if (scenes.isEmpty()) {
            return listOf(
                Scene(
                    title = idea.take(40).ifBlank { "قبس" },
                    description = "mosque peaceful prayer soft light cinematic | motion=$ofMotion | filter=$filter",
                    durationInSeconds = 5,
                    transitionType = "Dissolve",
                    tempo = "خاشع",
                    visualEffect = filter
                )
            )
        }
        return scenes
    }

    private fun normalizeMotion(raw: String): String {
        val m = raw.trim().lowercase()
        return when {
            m.contains("punch") || m == "punch_in" -> "punch_in"
            m.contains("pan") -> "pan"
            m.contains("static") || m.contains("ثابت") -> "static"
            m.contains("zoom") || m.contains("زووم") || m.contains("slow") -> "slow_zoom"
            else -> "slow_zoom"
        }
    }

    private fun motionForPurpose(purpose: String, ofMotion: String): String {
        return when (ofMotion) {
            "static" -> "static"
            "pan" -> when (purpose) {
                "core" -> "static"
                else -> "pan"
            }
            "punch_in" -> when (purpose) {
                "core" -> "punch_in"
                "hook" -> "slow_zoom"
                else -> "static"
            }
            else -> when (purpose) {
                "core" -> "static"
                else -> "slow_zoom"
            }
        }
    }

    private fun buildStyleBrief(style: AbsorbedStyle, extraDescription: String, tone: String): String {
        val visuals = style.visualTraits.take(4).joinToString(", ").ifBlank { "Dark Slate, Gold accents" }
        val motion = style.motionTraits.take(3).joinToString(", ").ifBlank { "slow cinematic" }
        val text = style.textTraits.take(2).joinToString(", ").ifBlank { "Arabic elegant captions" }
        val extra = extraDescription.trim().take(120)
        return buildString {
            append("أسلوب: ${style.name}. ")
            append("بصري: $visuals. ")
            append("حركة: $motion. ")
            append("نص: $text. ")
            append("نبرة: $tone.")
            if (extra.isNotBlank()) append(" توجيه إضافي: $extra")
        }
    }

    private fun pickTransition(style: AbsorbedStyle, tone: String): String {
        val motions = style.motionTraits.joinToString(" ").lowercase()
        return when {
            motions.contains("glitch") -> "Glitch"
            motions.contains("zoom") || motions.contains("زووم") -> "ZoomIn"
            motions.contains("slide") || motions.contains("سحب") -> "SlideLeft"
            tone.contains("خاشع") || tone.contains("هادئ") || tone.contains("تأملي") -> "Dissolve"
            tone.contains("ملحمي") || tone.contains("حماسي") -> "ZoomIn"
            else -> "Dissolve"
        }
    }

    private fun pickTempo(tone: String, style: AbsorbedStyle): String {
        if (tone.contains("خاشع") || tone.contains("هادئ") || tone.contains("تأملي")) return "خاشع"
        if (tone.contains("ملحمي") || tone.contains("حماسي")) return "سريع"
        val m = style.motionTraits.joinToString(" ")
        if (m.contains("بطيء") || m.contains("slow") || m.contains("static")) return "خاشع"
        if (m.contains("punch") || m.contains("سريع")) return "سريع"
        return "متوسط"
    }

    private fun pickAmbient(tone: String): String {
        return when {
            tone.contains("خاشع") || tone.contains("هادئ") || tone.contains("تأملي") -> "هدوء روحاني"
            tone.contains("ملحمي") || tone.contains("حماسي") -> "ملحمي خفيف"
            else -> "بدون"
        }
    }

    private enum class Topic { PRAYER, QURAN, SALAWAT, DHIKR, GENERAL }

    private fun detectTopic(idea: String): Topic {
        val t = idea.lowercase()
        return when {
            listOf("صلاة", "الصلاة", "صلّ", "راكع", "ساجد", "أقم الصلاة", "المصلين").any { t.contains(it) } -> Topic.PRAYER
            listOf("قرآن", "آية", "سورة", "مصحف", "تلاوة", "كتاب الله").any { t.contains(it) } -> Topic.QURAN
            listOf("صلوا عليه", "الصلاة على النبي", "اللهم صل", "ملائكته يصلون", "النبي", "الرسول").any { t.contains(it) } -> Topic.SALAWAT
            listOf("ذكر", "استغفار", "تسبيح", "لا إله إلا الله", "سبحان").any { t.contains(it) } -> Topic.DHIKR
            else -> Topic.GENERAL
        }
    }

    private fun buildScenePlans(
        idea: String,
        topic: Topic,
        totalDuration: Int,
        tone: String,
        transition: String,
        tempo: String,
        directive: StyleDirective?,
        ofMotion: String
    ): List<ScenePlan> {
        val primary = directive?.primaryColorHex ?: "#8B5CF6"
        val bg = directive?.backgroundColorHex ?: "#0B0F19"
        val capAnim = directive?.captionAnimation ?: "FadeIn"
        val capPos = directive?.captionPosition ?: "bottom"
        val filter = directive?.filterHint ?: "ai_violet"
        val boostEn = directive?.visualKeywordsEn?.take(3)?.joinToString(" ") ?: ""
        val boostAr = directive?.visualKeywordsAr?.take(2)?.joinToString(" ") ?: ""

        val d1 = (totalDuration * 0.28).toInt().coerceIn(3, 20)
        val d3 = (totalDuration * 0.28).toInt().coerceIn(3, 20)
        val d2 = (totalDuration - d1 - d3).coerceIn(4, 40)
        val shortIdea = idea.replace("\n", " ").trim().take(60)

        fun scene(
            purpose: String, dur: Int, text: String, qEn: String, qAr: String, trans: String
        ) = ScenePlan(
            purpose = purpose,
            durationSec = dur,
            onScreenText = text,
            visualQuery = qEn,
            visualQueryAr = qAr,
            transition = trans,
            motion = motionForPurpose(purpose, ofMotion),
            tempo = tempo
        )

        val rawPlans = when (topic) {
            Topic.PRAYER -> listOf(
                scene("hook", d1, "الصلاة… سكينة لا تُعوض", "mosque interior soft violet light peaceful cinematic", "مسجد إضاءة بنفسجية خاشعة", transition),
                scene("core", d2, shortIdea.ifBlank { "أقم الصلاة لذكره" }, "person praying silhouette peaceful mosque soft light", "مصلٍ بخشوع ضوء هادئ", "Dissolve"),
                scene("close", d3, "فيها طمأنينة القلب", "sunset sky mosque dome peaceful islamic architecture", "قبة مسجد غروب هادئ", "Fade")
            )
            Topic.QURAN -> listOf(
                scene("hook", d1, "آية… تلامس القلب", "open quran pages soft violet light calligraphy cinematic", "مصحف مفتوح ضوء بنفسجي هادئ", transition),
                scene("core", d2, shortIdea.ifBlank { "كتاب أحكمت آياته" }, "arabic calligraphy violet ink manuscript close up", "خط عربي بنفسجي مخطوطة", "Dissolve"),
                scene("close", d3, "اجعل للقرآن نصيباً من يومك", "sun rays through clouds peaceful nature islamic mood", "أشعة شمس سحب سكينة", "Fade")
            )
            Topic.SALAWAT -> listOf(
                scene("hook", d1, "إن الله وملائكته يصلون على النبي", "prophet mosque green dome night lights cinematic", "المسجد النبوي قبة خضراء ليل", transition),
                scene("core", d2, shortIdea.ifBlank { "صلوا عليه وسلموا تسليماً" }, "mosque illuminated night violet and cyan glow islamic", "مسجد أنوار بنفسجية وسيان ليلية", "Dissolve"),
                scene("close", d3, "وبها ترتفع الدرجات", "islamic architecture lantern soft bokeh night", "فوانيس إسلامية بوكيه ليلي", "Fade")
            )
            Topic.DHIKR -> listOf(
                scene("hook", d1, "الذكر… حياة القلوب", "prayer beads tasbih soft focus cool light", "مسبحة ضوء بنفسجي هادئ", transition),
                scene("core", d2, shortIdea.ifBlank { "سبحان الله والحمد لله" }, "peaceful nature river forest soft sunlight islamic calm", "طبيعة هادئة نور لطيف", "Dissolve"),
                scene("close", d3, "أدم الذكر تأنس الروح", "night sky stars long exposure peaceful", "سماء نجوم سكينة", "Fade")
            )
            Topic.GENERAL -> listOf(
                scene("hook", d1, shortIdea.take(32).ifBlank { "رسالة تلامس القلوب" }, "islamic architecture violet dusk cinematic wide", "عمارة إسلامية شفق بنفسجي", transition),
                scene("core", d2, shortIdea.ifBlank { "محتوى هادف بنور الإيمان" }, "mosque courtyard soft daylight peaceful people silhouette", "صحن مسجد ضوء نهاري", "Dissolve"),
                scene("close", d3, "قبس… نورٌ يُنشر", "cyan light through islamic arch window cinematic", "ضوء سيان من قوس إسلامي", "Fade")
            )
        }

        return rawPlans.map { sp ->
            val qEn = if (boostEn.isNotBlank()) "${sp.visualQuery} $boostEn".trim() else sp.visualQuery
            val qAr = if (boostAr.isNotBlank()) "${sp.visualQueryAr} $boostAr".trim() else sp.visualQueryAr
            sp.copy(
                visualQuery = qEn,
                visualQueryAr = qAr,
                transition = transition,
                tempo = tempo,
                primaryColorHex = primary,
                backgroundColorHex = bg,
                captionAnimation = capAnim,
                captionPosition = capPos,
                filterHint = filter
            )
        }
    }
}
