package com.qabas.app

enum class GuardVerdict(val title: String, val badge: String) {
    APPROPRIATE("مناسب للنشر والدعوة", "مناسب ✅"),
    NEEDS_REVIEW("يحتاج مراجعة وتدقيق", "يحتاج مراجعة ⚠️"),
    REJECTED("مرفوض - مخالف للضوابط", "مرفوض ❌")
}

data class ContentInspectionResult(
    val verdict: GuardVerdict,
    val score: Int, // 0 to 100
    val title: String,
    val reason: String, // سبب مختصر ومباشر
    val warnings: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val improvedScript: String? = null
)

object ContentFilterService {
    // Explicitly forbidden profanities, sexual words, and severe anti-religious slurs
    private val explicitForbidden = listOf(
        "sexy", "nude", "adult", "erotic", "porn", "sex", "nsfw", "xxx",
        "جنس", "عاري", "إباحي", "بذيء", "شتم", "لعن", "عري", "سكس",
        "إساءة للإسلام", "إساءة للدين", "إساءة للأنبياء", "إساءة للذات الإلهية",
        "سب الله", "سب النبي", "سب الدين", "كفر", "إلحاد صريح"
    )

    // Suspicious phrases, superstitious chains, unverified promises
    private val reviewTriggers = listOf(
        "أقسم عليك أن تنشرها", "إذا لم تنشرها فاعلم أن", "انشرها لعشرة أشخاص وستسمع خبراً",
        "معجزة حدثت اليوم بعد قراءة", "حلف بالطلاق", "تكفير المسلمين", "لعن الصحابة",
        "تحدي الرقص", "أغاني وموسيقى صاخبة", "بدعة منكرة", "سحر وشعوذة"
    )

    // Positive indicators
    private val positiveIslamicKeywords = listOf(
        "قال الله", "قال رسول الله", "صلى الله عليه وسلم", "سورة", "آية", "حديث",
        "تدبر", "الصحابة", "الجنة", "الاستغفار", "الدعاء", "التوبة", "القرآن",
        "حكمة", "تزكية", "أخلاق", "بر الوالدين", "الصلاة", "رمضان", "الصبر", "الإيمان"
    )

    private val audioExclusions = listOf(
        "music", "song", "melody", "instrumental", "beat",
        "موسيقى", "أغنية", "لحن", "إيقاع", "عزف"
    )

    /**
     * Fast local rule-based inspection (instant, offline)
     */
    fun quickInspect(text: String): ContentInspectionResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return ContentInspectionResult(
                verdict = GuardVerdict.NEEDS_REVIEW,
                score = 50,
                title = "النص فارغ",
                reason = "لم يتم إدخال أي نص للفحص بعد.",
                warnings = listOf("يرجى كتابة فكرة أو سكريبت لإجراء الفحص."),
                suggestions = listOf("أدخل فكرة دعوية، آية قرآنية، أو حديث شريف.")
            )
        }

        val lower = trimmed.lowercase()

        // 0. Fast-pass: clear Islamic / Quranic / Prophetic content is always APPROPRIATE
        val islamicSafeSignals = listOf(
            "الله", "رسول", "النبي", "صلى الله", "صلوا عليه", "ملائكته", "يصلون على",
            "القرآن", "آية", "سورة", "حديث", "الصلاة", "الدعاء", "استغفار",
            "إن الله", "يا أيها الذين آمنوا", "سبحان", "الحمد لله", "لا إله إلا",
            "بسم الله", "آمين", "اللهم", "رضي الله"
        )
        if (islamicSafeSignals.any { lower.contains(it) }) {
            val positiveCount = positiveIslamicKeywords.count { lower.contains(it.lowercase()) }
            return ContentInspectionResult(
                verdict = GuardVerdict.APPROPRIATE,
                score = (90 + (positiveCount * 2)).coerceAtMost(100),
                title = "محتوى إسلامي مناسب ومجاز",
                reason = "نص دعوي/قرآني/نبوي منضبط ومناسب للإنتاج والنشر.",
                warnings = emptyList(),
                suggestions = listOf("أضف خطافاً سريعاً في أول 3 ثوان لجذب انتباه المشاهد."),
                improvedScript = trimmed
            )
        }

        // 1. Check for severe violations (REJECTED)
        val matchedForbidden = explicitForbidden.filter { lower.contains(it.lowercase()) }
        if (matchedForbidden.isNotEmpty()) {
            return ContentInspectionResult(
                verdict = GuardVerdict.REJECTED,
                score = 15,
                title = "مخالفة صريحة للضوابط الأخلاقية والدعوية",
                reason = "يحتوي النص على كلمات أو دلالات غير لائقة تتنافى مع رسالة المحتوى الإسلامي الهادف (${matchedForbidden.take(2).joinToString("، ")}).",
                warnings = matchedForbidden.map { "تم رصد لفظ محظور: $it" },
                suggestions = listOf("إعادة صياغة الفكرة بالكامل باستخدام أسلوب لائق ورسالة دعوية إيجابية."),
                improvedScript = null
            )
        }

        // 2. Check for suspicious / superstitious / aggressive patterns (NEEDS_REVIEW)
        val matchedReview = reviewTriggers.filter { lower.contains(it.lowercase()) }
        if (matchedReview.isNotEmpty()) {
            return ContentInspectionResult(
                verdict = GuardVerdict.NEEDS_REVIEW,
                score = 55,
                title = "يحتاج مراجعة الأسلوب والتوثيق الشرعي",
                reason = "يتضمن النص عبارات شائعة غير موثقة شرعياً أو أسلوب نشر قسري ينبغي تجنبه.",
                warnings = matchedReview.map { "تم رصد عبارة تحتاج تدقيق: $it" },
                suggestions = listOf(
                    "تجنب إلزام المتابعين بالحلف أو النشر القسري.",
                    "توثيق الأدلة بنسبتها لمصادرها الصحيحة من الكتاب والسنة.",
                    "استبدال النبرة الترهيبية بنبرة الرجاء والترغيب بالحسنى."
                ),
                improvedScript = cleanReviewText(trimmed)
            )
        }

        // 3. Positive evaluation (APPROPRIATE)
        val positiveCount = positiveIslamicKeywords.count { lower.contains(it.lowercase()) }
        val score = (85 + (positiveCount * 3)).coerceAtMost(100)

        return ContentInspectionResult(
            verdict = GuardVerdict.APPROPRIATE,
            score = score,
            title = "محتوى مناسب ومطابق للمعايير الدعوية",
            reason = "النص منضبط وخالٍ من المحاذير، ومناسب للإنتاج والنشر في المنصات الإسلامية.",
            warnings = emptyList(),
            suggestions = listOf(
                "احرص على إتقان الإلقاء الصوتي واختيار لقطات مرئية معبرة وذات جودة عالية.",
                "أضف خطافاً سريعاً في أول 3 ثوان لجذب انتباه المشاهد."
            ),
            improvedScript = trimmed
        )
    }

    /**
     * Deep AI inspection using Gemini / Assistant
     */
    suspend fun inspectWithAI(text: String): ContentInspectionResult {
        if (text.isBlank()) return quickInspect(text)

        val quickResult = quickInspect(text)
        if (quickResult.verdict == GuardVerdict.REJECTED) {
            return quickResult // Instant reject without wasting API calls
        }

        return try {
            val prompt = """
                أنت "حارس المحتوى" (Content Guard) المتخصص في تدقيق وتصنيف النصوص والسيناريوهات لمنصة إنتاج الفيديوهات الإسلامية والدعوية "قبس".
                
                قم بفحص النص التالي بعناية:
                "$text"
                
                المطلوب منك تقييم النص بدقة وإرجاع النتيجة بالصيغة التالية تماماً بدون مقدمات إضافية:
                الحكم: [مناسب / يحتاج مراجعة / مرفوض]
                الدرجة: [رقم من 0 إلى 100]
                السبب: [سبب مختصر ومباشر في جملة واحدة]
                ملاحظات: [ملاحظة 1 | ملاحظة 2]
                اقتراحات: [اقتراح 1 | اقتراح 2]
                النص المحسن: [السيناريو المصوب والمنقح بجودة عالية وجاذبية دعوية]
            """.trimIndent()

            val response = AppServices.chatWithAssistant(listOf(Pair(true, prompt)))
            parseAiInspection(response, text, quickResult)
        } catch (e: Exception) {
            quickResult
        }
    }

    private fun parseAiInspection(aiResponse: String, originalText: String, fallback: ContentInspectionResult): ContentInspectionResult {
        return try {
            var verdict = fallback.verdict
            var score = fallback.score
            var reason = fallback.reason
            val warnings = mutableListOf<String>()
            val suggestions = mutableListOf<String>()
            var improvedScript: String? = null

            val lines = aiResponse.lines()
            for (line in lines) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("الحكم:") -> {
                        val vText = trimmed.removePrefix("الحكم:").trim()
                        verdict = when {
                            vText.contains("مرفوض") -> GuardVerdict.REJECTED
                            vText.contains("مراجعة") -> GuardVerdict.NEEDS_REVIEW
                            else -> GuardVerdict.APPROPRIATE
                        }
                    }
                    trimmed.startsWith("الدرجة:") -> {
                        val scoreText = trimmed.removePrefix("الدرجة:").replace(Regex("[^0-9]"), "")
                        scoreText.toIntOrNull()?.let { score = it.coerceIn(0, 100) }
                    }
                    trimmed.startsWith("السبب:") -> {
                        reason = trimmed.removePrefix("السبب:").trim()
                    }
                    trimmed.startsWith("ملاحظات:") -> {
                        val notes = trimmed.removePrefix("ملاحظات:").split("|").map { it.trim() }.filter { it.isNotEmpty() }
                        warnings.addAll(notes)
                    }
                    trimmed.startsWith("اقتراحات:") -> {
                        val sugs = trimmed.removePrefix("اقتراحات:").split("|").map { it.trim() }.filter { it.isNotEmpty() }
                        suggestions.addAll(sugs)
                    }
                    trimmed.startsWith("النص المحسن:") -> {
                        improvedScript = trimmed.removePrefix("النص المحسن:").trim()
                    }
                }
            }

            // If improvedScript was multiline at the end
            if (improvedScript == null && aiResponse.contains("النص المحسن:")) {
                improvedScript = aiResponse.substringAfter("النص المحسن:").trim()
            }

            ContentInspectionResult(
                verdict = verdict,
                score = score,
                title = when (verdict) {
                    GuardVerdict.APPROPRIATE -> "محتوى مناسب ومجاز للنشر ✨"
                    GuardVerdict.NEEDS_REVIEW -> "يحتاج مراجعة وتعديل ⚠️"
                    GuardVerdict.REJECTED -> "محتوى مرفوض لمخالفة المعايير ❌"
                },
                reason = reason.ifBlank { fallback.reason },
                warnings = if (warnings.isNotEmpty()) warnings else fallback.warnings,
                suggestions = if (suggestions.isNotEmpty()) suggestions else fallback.suggestions,
                improvedScript = improvedScript ?: fallback.improvedScript
            )
        } catch (e: Exception) {
            fallback
        }
    }

    private fun cleanReviewText(text: String): String {
        var result = text
        reviewTriggers.forEach { trigger ->
            result = result.replace(trigger, "")
        }
        return result.replace(Regex("\\s+"), " ").trim()
    }

    fun filterText(text: String): Boolean {
        if (text.isBlank()) return true
        val lower = text.lowercase()

        // Never block clear Islamic / Quranic / Prophetic content
        val islamicSafeSignals = listOf(
            "الله", "رسول", "النبي", "صلى الله", "صلوا عليه", "ملائكته",
            "القرآن", "آية", "سورة", "حديث", "الصلاة", "الدعاء", "استغفار",
            "إن الله", "يا أيها الذين آمنوا", "سبحان", "الحمد لله", "لا إله إلا"
        )
        if (islamicSafeSignals.any { lower.contains(it) }) {
            return true
        }

        val res = quickInspect(text)
        return res.verdict != GuardVerdict.REJECTED
    }

    fun filterImageQuery(query: String): String {
        var filteredQuery = query.lowercase()
        explicitForbidden.forEach { word ->
            filteredQuery = filteredQuery.replace(word, "")
        }
        return filteredQuery.trim()
    }
    
    fun filterAudioQuery(query: String): String {
        var filteredQuery = query.lowercase()
        audioExclusions.forEach { word ->
            filteredQuery = filteredQuery.replace(word, "")
        }
        return filteredQuery.trim()
    }
    
    fun getFallbackAudio(query: String): String {
        val q = query.lowercase()
        return when {
            q.contains("nature") || q.contains("طبيعة") -> "assets/audio/nature.mp3"
            q.contains("city") || q.contains("مدينة") -> "assets/audio/city.mp3"
            q.contains("calm") || q.contains("هادئ") -> "assets/audio/calm.mp3"
            q.contains("activity") || q.contains("نشاط") -> "assets/audio/activity.mp3"
            q.contains("dramatic") || q.contains("درامي") -> "assets/audio/dramatic.mp3"
            else -> "assets/audio/calm.mp3"
        }
    }
}

