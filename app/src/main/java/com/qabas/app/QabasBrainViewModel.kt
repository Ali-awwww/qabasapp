package com.qabas.app

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import java.util.concurrent.TimeUnit

/**
 * حالات حالة عمل العقل الإخراجي (Brain Neural States)
 */
enum class BrainStatus {
    IDLE,                      // جاهز للإنتاج
    FETCHING_MEMORY,           // استرجاع كائنات الأنماط من Firestore
    ANALYZING_IDEA,            // تحليل واستيعاب الفكرة والدلالات
    MATCHING_STYLE,            // مطابقة وصهر كائنات الأنماط المرجعية
    GEMINI_REASONING,          // تفكير وتوليد بالذكاء الاصطناعي مع ذاكرة الأنماط
    SYNTHESIZING_DIRECTIVES,   // صياغة توجيهات المونتاج وفلاتر FFmpeg
    SUCCESS,                   // تم اتخاذ القرار الإخراجي بنجاح
    ERROR                      // تنبيه مع تفعيل التدهور الأنيق
}

/**
 * نتيجة القرار الإخراجي المتخذ من قبل العقل
 */
@Immutable
data class BrainDecision(
    val title: String = "",
    val hook: String = "",
    val scriptScenes: List<Scene> = emptyList(),
    val voiceOverTone: String = "وقور وملهم",
    val visualDirectives: String = "",
    val primaryColorHex: String = "#E8C547",
    val backgroundColorHex: String = "#0B0F19",
    val ffmpegFilterSnippet: String = "",
    val audioSfxSuggestions: List<String> = emptyList(),
    val selectedStyleName: String = "",
    val compatibilityScore: Int = 0,
    val aiRationale: String = "",
    val targetAudience: String = "الجمهور العام",
    val callToAction: String = "شارك المقطع لتنال الأجر المبارك"
)

/**
 * الحالة الكاملة لواجهة العقل
 */
@Immutable
data class BrainUiState(
    val status: BrainStatus = BrainStatus.IDLE,
    val statusMessage: String = "العقل الإخراجي جاهز لاستقبال الأفكار والأنماط",
    val activeStyleObjectsCount: Int = 0,
    val activeReferenceStyles: List<StyleObject> = emptyList(),
    val currentDecision: BrainDecision? = null,
    val brainStrengthScore: Int = 0,
    val lastProcessedIdea: String = "",
    val errorMessage: String? = null
)

/**
 * مراحل استخراج وامتصاص النمط الفني بدقة (Style Absorption Stages)
 */
enum class StyleAbsorptionStage(val displayName: String, val baseStartPercent: Int, val baseEndPercent: Int) {
    IDLE("في الانتظار", 0, 0),
    FRAME_EXTRACTION("استخراج الإطارات", 0, 33),
    COLOR_ANALYSIS("تحليل الألوان والتباين", 34, 66),
    AI_QUERY("استعلام الذكاء الاصطناعي", 67, 95),
    SAVING_AND_EVOLVING("الحفظ وتطوير العقل", 96, 100),
    COMPLETED("اكتمل الاستخراج", 100, 100),
    FAILED("تعذر الاستخراج", 0, 0)
}

/**
 * حالة واجهة استخراج وامتصاص الأنماط في عقل قبس
 */
@Immutable
data class StyleAbsorptionUiState(
    val stage: StyleAbsorptionStage = StyleAbsorptionStage.IDLE,
    val overallProgress: Float = 0f,
    val overallPercentage: Int = 0,
    val stagePercentage: Int = 0,
    val statusMessage: String = "",
    val isAbsorbing: Boolean = false,
    val isApiKeyAvailable: Boolean = false,
    val extractedStyle: StyleObject? = null,
    val strengthGain: Int = 0,
    val errorMessage: String? = null
)

/**
 * الهيكل البرمجي المركزي (ViewModel) الذي يمثل 'العقل' (Brain)
 * المسؤول عن إدارة ومعالجة المدخلات، استدعاء ذاكرة كائنات الأنماط من Firestore،
 * والتواصل مع Gemini API لاتخاذ القرارات الإخراجية والمونتاجية بناءً على الأنماط المخزنة.
 */
class QabasBrainViewModel(
    private val context: Context,
    private val repository: QabasBrainRepository = QabasBrainRepository.getInstance(context)
) : ViewModel() {

    private val TAG = "QabasBrainViewModel"

    private val _uiState = MutableStateFlow(BrainUiState())
    val uiState: StateFlow<BrainUiState> = _uiState.asStateFlow()

    private val _absorptionState = MutableStateFlow(StyleAbsorptionUiState())
    val absorptionState: StateFlow<StyleAbsorptionUiState> = _absorptionState.asStateFlow()

    private val _isApiKeyAvailable = MutableStateFlow(false)
    val isApiKeyAvailable: StateFlow<Boolean> = _isApiKeyAvailable.asStateFlow()

    private val _decisionHistory = MutableStateFlow<List<BrainDecisionHistoryItem>>(emptyList())
    val decisionHistory: StateFlow<List<BrainDecisionHistoryItem>> = _decisionHistory.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    init {
        checkApiKeyAvailability()
        syncBrainMemory()
        loadDecisionHistory()
    }

    /**
     * التحقق من توفر مفتاح ذكاء اصطناعي صالح (Gemini أو Groq)
     */
    fun checkApiKeyAvailability(): Boolean {
        val hasKey = StyleBrain.hasValidAiApiKey(context)
        _isApiKeyAvailable.value = hasKey
        _absorptionState.value = _absorptionState.value.copy(isApiKeyAvailable = hasKey)
        return hasKey
    }

    /**
     * بدء عملية استخراج وامتصاص النمط الفني مع تقارير دقيقة للمراحل
     */
    fun startAbsorption(
        videoPath: String,
        styleName: String,
        saveToCloud: Boolean,
        onSuccess: ((StyleObject) -> Unit)? = null
    ) {
        if (!checkApiKeyAvailability()) {
            _absorptionState.value = _absorptionState.value.copy(
                stage = StyleAbsorptionStage.FAILED,
                isAbsorbing = false,
                errorMessage = "يتطلب استخراج الأنماط توفير مفتاح Gemini أو Groq في الإعدادات. يرجى إضافة المفتاح أولاً من شاشة الإعدادات."
            )
            return
        }

        val trimmed = videoPath.trim()
        if (trimmed.isBlank()) {
            _absorptionState.value = _absorptionState.value.copy(
                stage = StyleAbsorptionStage.FAILED,
                isAbsorbing = false,
                errorMessage = "يرجى اختيار فيديو أو إدخال مسار صالح قبل البدء."
            )
            return
        }

        viewModelScope.launch {
            _absorptionState.value = StyleAbsorptionUiState(
                stage = StyleAbsorptionStage.FRAME_EXTRACTION,
                overallProgress = 0.05f,
                overallPercentage = 5,
                stagePercentage = 15,
                statusMessage = "بدء قراءة المصدر وتهيئة المستخرج...",
                isAbsorbing = true,
                isApiKeyAvailable = true,
                errorMessage = null
            )

            try {
                val extracted = withContext(Dispatchers.IO) {
                    try {
                        StyleBrain.extractAndStoreStyleObject(
                            context = context,
                            videoPathOrUrl = trimmed,
                            styleName = styleName,
                            saveToCloud = saveToCloud,
                            onProgress = { p, msg ->
                                val stage = when {
                                    p <= 0.33f -> StyleAbsorptionStage.FRAME_EXTRACTION
                                    p <= 0.66f -> StyleAbsorptionStage.COLOR_ANALYSIS
                                    p <= 0.95f -> StyleAbsorptionStage.AI_QUERY
                                    p < 1.0f -> StyleAbsorptionStage.SAVING_AND_EVOLVING
                                    else -> StyleAbsorptionStage.COMPLETED
                                }

                                val stagePct = when (stage) {
                                    StyleAbsorptionStage.FRAME_EXTRACTION -> ((p / 0.33f) * 100).toInt().coerceIn(0, 100)
                                    StyleAbsorptionStage.COLOR_ANALYSIS -> (((p - 0.33f) / 0.33f) * 100).toInt().coerceIn(0, 100)
                                    StyleAbsorptionStage.AI_QUERY -> (((p - 0.66f) / 0.29f) * 100).toInt().coerceIn(0, 100)
                                    StyleAbsorptionStage.SAVING_AND_EVOLVING -> (((p - 0.95f) / 0.05f) * 100).toInt().coerceIn(0, 100)
                                    StyleAbsorptionStage.COMPLETED -> 100
                                    else -> 0
                                }

                                _absorptionState.value = _absorptionState.value.copy(
                                    stage = stage,
                                    overallProgress = p.coerceIn(0f, 1f),
                                    overallPercentage = (p * 100).toInt().coerceIn(0, 100),
                                    stagePercentage = stagePct,
                                    statusMessage = msg,
                                    isAbsorbing = p < 1.0f
                                )
                            }
                        )
                    } catch (oom: OutOfMemoryError) {
                        System.gc()
                        throw Exception("نفدت الذاكرة أثناء التحليل — يرجى اختيار مقطع فيديو أقصر أو دقة أقل.")
                    }
                }

                val gain = withContext(Dispatchers.IO) {
                    StyleBrainCrashGuard.applyTinyStrengthGain(context, extracted.overallScore)
                    StyleBrainLoadFix.computeStrengthGain(extracted.overallScore, StyleBrain.getCoreStyleStrength())
                }

                _absorptionState.value = _absorptionState.value.copy(
                    stage = StyleAbsorptionStage.COMPLETED,
                    overallProgress = 1.0f,
                    overallPercentage = 100,
                    stagePercentage = 100,
                    statusMessage = "اكتمل استخراج النمط وتغذية العقل بنجاح! (+${gain} لقوة العقل)",
                    isAbsorbing = false,
                    extractedStyle = extracted,
                    strengthGain = gain,
                    errorMessage = null
                )

                syncBrainMemory()
                onSuccess?.invoke(extracted)

            } catch (e: Exception) {
                Log.e(TAG, "Extraction failed: ${e.message}", e)
                _absorptionState.value = _absorptionState.value.copy(
                    stage = StyleAbsorptionStage.FAILED,
                    overallProgress = 0f,
                    overallPercentage = 0,
                    stagePercentage = 0,
                    statusMessage = "فشل استخراج النمط",
                    isAbsorbing = false,
                    errorMessage = e.message ?: "حدث خطأ غير متوقع أثناء استخراج النمط."
                )
            }
        }
    }

    /**
     * إعادة تعيين حالة واجهة الاستخراج
     */
    fun resetAbsorption() {
        _absorptionState.value = StyleAbsorptionUiState(
            isApiKeyAvailable = checkApiKeyAvailability()
        )
    }

    /**
     * تحميل سجل القرارات الإخراجية المحفوظة
     */
    fun loadDecisionHistory() {
        viewModelScope.launch {
            try {
                val history = repository.getDecisionHistory()
                _decisionHistory.value = history
            } catch (e: Exception) {
                Log.e(TAG, "Error loading decision history: ${e.message}")
            }
        }
    }

    /**
     * حذف عنصر محدد من السجل
     */
    fun deleteHistoryItem(id: String) {
        viewModelScope.launch {
            repository.deleteDecisionHistoryItem(id)
            _decisionHistory.value = _decisionHistory.value.filter { it.id != id }
        }
    }

    /**
     * مسح كافة عناصر السجل
     */
    fun clearDecisionHistory() {
        viewModelScope.launch {
            repository.clearAllDecisionHistory()
            _decisionHistory.value = emptyList()
        }
    }

    /**
     * تقييم عنصر في السجل وتعزيز كائن النمط المرتبط به
     */
    fun recordHistoryFeedback(itemId: String, isPositive: Boolean) {
        viewModelScope.launch {
            repository.updateDecisionFeedback(itemId, isPositive)
            _decisionHistory.value = _decisionHistory.value.map {
                if (it.id == itemId) it.copy(userFeedback = isPositive) else it
            }
            // تحديث الرسالة
            _uiState.value = _uiState.value.copy(
                statusMessage = if (isPositive) "تم تعزيز كائن النمط بنجاح!" else "تم تسجيل الملاحظة لخفض وزن النمط."
            )
        }
    }

    /**
     * مزامنة واسترجاع كائنات الأنماط من سحابة Firestore لتحديث الذاكرة المرجعية للعقل
     */
    fun syncBrainMemory() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    status = BrainStatus.FETCHING_MEMORY,
                    statusMessage = "جاري استرجاع كائنات الأنماط من سحابة Firestore..."
                )

                val styles = repository.getReferenceStyleObjects()
                val brainStrength = StyleBrain.getCoreStyleStrength()

                _uiState.value = _uiState.value.copy(
                    status = BrainStatus.IDLE,
                    statusMessage = "تم تحديث ذاكرة العقل بنجاح (${styles.size} نمط مرجعي)",
                    activeStyleObjectsCount = styles.size,
                    activeReferenceStyles = styles,
                    brainStrengthScore = brainStrength
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing brain memory: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    status = BrainStatus.IDLE,
                    statusMessage = "العقل جاهز مع الذاكرة المحلية الاحتياطية",
                    brainStrengthScore = StyleBrain.getCoreStyleStrength()
                )
            }
        }
    }

    /**
     * معالجة الفكرة واتخاذ القرارات الإخراجية بناءً على كائنات الأنماط المسترجعة من Firestore
     */
    fun processIdeaWithBrain(
        idea: String,
        durationSeconds: Int = 30,
        tonePreference: String = "وقور وملهم",
        targetAudience: String = "الجمهور العام",
        overrideStyleId: String? = null,
        onSuccess: ((BrainDecision) -> Unit)? = null
    ) {
        if (idea.isBlank()) return

        viewModelScope.launch {
            try {
                // 1. استرجاع ذاكرة الأنماط
                _uiState.value = _uiState.value.copy(
                    status = BrainStatus.FETCHING_MEMORY,
                    statusMessage = "استرجاع كائنات الأنماط وتفضيلات الهوية من Firestore...",
                    lastProcessedIdea = idea,
                    errorMessage = null
                )
                delay(300)

                val referenceMemoryPrompt = repository.buildFewShotStyleMemory(idea, tonePreference)
                val allStyles = repository.getReferenceStyleObjects()

                // 2. تحليل الفكرة دلالياً
                _uiState.value = _uiState.value.copy(
                    status = BrainStatus.ANALYZING_IDEA,
                    statusMessage = "تحليل الفكرة واستخراج الخطاف والجوهر الإيماني..."
                )
                delay(400)

                // 3. مطابقة الأسلوب الفني
                _uiState.value = _uiState.value.copy(
                    status = BrainStatus.MATCHING_STYLE,
                    statusMessage = "مطابقة النمط الفني الأمثل وصهر موازين الإخراج..."
                )
                val chosenStyle = if (!overrideStyleId.isNullOrBlank()) {
                    allStyles.find { it.id == overrideStyleId } ?: allStyles.firstOrNull()
                } else {
                    StyleBrain.chooseBestStyleForIdea(idea, durationSeconds, tonePreference, targetAudience)?.let {
                        StyleObject.fromAbsorbedStyle(it)
                    } ?: allStyles.firstOrNull()
                }
                delay(400)

                // 4. استدعاء الذكاء الاصطناعي (Gemini) مع الذاكرة المرجعية
                _uiState.value = _uiState.value.copy(
                    status = BrainStatus.GEMINI_REASONING,
                    statusMessage = "تفكير الذكاء الاصطناعي (Gemini) بتوجيه من ذاكرة الأنماط..."
                )

                val decision = executeGeminiDirectorialReasoning(
                    idea = idea,
                    durationSeconds = durationSeconds,
                    tonePreference = tonePreference,
                    targetAudience = targetAudience,
                    referenceMemory = referenceMemoryPrompt,
                    chosenStyle = chosenStyle
                )

                // 5. صياغة توجيهات المونتاج وفلاتر FFmpeg
                _uiState.value = _uiState.value.copy(
                    status = BrainStatus.SYNTHESIZING_DIRECTIVES,
                    statusMessage = "صياغة مصفوفة فلاتر FFmpeg والتيبوغرافيا والمؤثرات الصوتية..."
                )
                delay(400)

                // 6. اكتمال العملية بنجاح وحفظ القرار في السجل
                _uiState.value = _uiState.value.copy(
                    status = BrainStatus.SUCCESS,
                    statusMessage = "تم اتخاذ القرار الإخراجي بنجاح بدقة (${decision.compatibilityScore}%)",
                    currentDecision = decision
                )

                // حفظ في السجل مع كائن النمط المؤثر
                val historyEntry = BrainDecisionHistoryItem(
                    ideaInput = idea,
                    tone = tonePreference,
                    targetAudience = targetAudience,
                    decision = decision,
                    influencingStyleObject = chosenStyle
                )
                repository.saveDecisionHistoryItem(historyEntry)
                loadDecisionHistory()

                onSuccess?.invoke(decision)

            } catch (e: Exception) {
                Log.e(TAG, "Error in processIdeaWithBrain: ${e.message}", e)
                val fallbackDecision = buildFallbackDecision(idea, tonePreference, targetAudience)
                _uiState.value = _uiState.value.copy(
                    status = BrainStatus.ERROR,
                    statusMessage = "تعذر الاتصال بالذكاء الاصطناعي — تم استخدام قرار احتياطي محلي",
                    currentDecision = fallbackDecision
                )

                val historyEntry = BrainDecisionHistoryItem(
                    ideaInput = idea,
                    tone = tonePreference,
                    targetAudience = targetAudience,
                    decision = fallbackDecision,
                    influencingStyleObject = null
                )
                repository.saveDecisionHistoryItem(historyEntry)
                loadDecisionHistory()

                onSuccess?.invoke(fallbackDecision)
            }
        }
    }

    /**
     * تنفيذ طلب الاستدلال الإخراجي لـ Gemini مع حقن الذاكرة المرجعية المسترجعة من Firestore
     */
    private suspend fun executeGeminiDirectorialReasoning(
        idea: String,
        durationSeconds: Int,
        tonePreference: String,
        targetAudience: String,
        referenceMemory: String,
        chosenStyle: StyleObject?
    ): BrainDecision = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_key", "")?.trim().orEmpty()

        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY") {
            return@withContext buildFallbackDecision(idea, tonePreference, targetAudience, chosenStyle)
        }

        val styleGuidance = chosenStyle?.let {
            """
            النمط المختار للتطبيق الإلزامي: [${it.name}]
            - الألوان: ${it.visualStyle.dominantColors} (لون رئيسي: ${it.visualStyle.primaryColorHex}، خلفية: ${it.visualStyle.backgroundColorHex})
            - إيقاع الحركة: ${it.motionRhythm.overallRhythm} (سرعة الانتقال: ${it.motionRhythm.transitionSpeed})
            - الخط والكابشن: ${it.contentTone.typographyStyle}، تحريك: ${it.contentTone.captionAnimation}
            - السمات البصرية: ${it.visualStyle.visualTraits.joinToString(", ")}
            """.trimIndent()
        } ?: "الالتزام بهوية قبس الملكية الداكنة (Deep Slate #0B0F19 مع الذهب #E8C547)"

        val prompt = """
            أنت "العقل الإخراجي" لمنصة "قبس" (Qabas Director Brain) لإنتاج مقاطع الفيديو الدعوية والإسلامية الفاخرة عالية التأثير.
            
            $referenceMemory
            
            التوجيه الفني المختار للمشروع:
            $styleGuidance
            
            مواصفات الطلب الحالي:
            - الفكرة: "$idea"
            - المدة المقدرة: $durationSeconds ثانية
            - النبرة المستهدفة: $tonePreference
            - الجمهور: $targetAudience
            
            المطلوب: اتخاذ قرار إخراجي كامل ومتكامل وإنتاج سيناريو مقسم لمشاهد متسلسلة (Scenes).
            يجب أن تكون الاستجابة بصيغة JSON فقط وحصراً بدون أي markdown code blocks، بهذا التنسيق:
            {
                "title": "عنوان جذاب ومؤثر للفيديو",
                "hook": "الخطاف البصري والسمعي في أول 3 ثواني",
                "targetAudience": "$targetAudience",
                "voiceOverTone": "$tonePreference",
                "primaryColorHex": "${chosenStyle?.visualStyle?.primaryColorHex ?: "#E8C547"}",
                "backgroundColorHex": "${chosenStyle?.visualStyle?.backgroundColorHex ?: "#0B0F19"}",
                "visualDirectives": "توجيهات بصرية محددة للإضاءة والكاميرا وتدرج الألوان",
                "ffmpegFilterSnippet": "eq=contrast=1.15:brightness=0.02:saturation=1.1,drawtext=fontfile=font.ttf:text='قَبَس':fontcolor=#E8C547",
                "audioSfxSuggestions": ["دوي عميق للبداية", "حفيف رياح هادئ", "مؤثر تصاعدي عند الخاتمة"],
                "selectedStyleName": "${chosenStyle?.name ?: "النمط الوثائقي الإيماني"}",
                "compatibilityScore": 96,
                "aiRationale": "شرح موجز لسبب اختيار هذا الأسلوب الإخراجي والتوزيع الزمني للمشاهد",
                "callToAction": "شارك المقطع ليكون صدقة جارية لك ولأحبابك",
                "scenes": [
                    {
                        "title": "المشهد 1: الخطاف البصري",
                        "description": "وصف دقيق للمشهد والنص المكتوب",
                        "duration": 5,
                        "visualEffect": "Cinematic Zoom In",
                        "tempo": "سريع وجذاب",
                        "transition": "Smooth Fade"
                    },
                    {
                        "title": "المشهد 2: جوهر الرسالة والتدبر",
                        "description": "وصف المشهد وتفاصيل التعليق الصوتي والآية أو الدليل",
                        "duration": 15,
                        "visualEffect": "Parallax Ambient Drift",
                        "tempo": "وقور ومتزن",
                        "transition": "Dissolve"
                    },
                    {
                        "title": "المشهد 3: الأثر والدعوة للعمل",
                        "description": "الخاتمة الملهمة مع الشعار والنداء النهائي",
                        "duration": 10,
                        "visualEffect": "Slow Zoom Out",
                        "tempo": "هادئ ومؤثر",
                        "transition": "Fade to Black"
                    }
                ]
            }
        """.trimIndent()

        val contentsArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", prompt) })
                })
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
            Log.e(TAG, "Gemini call failed: HTTP ${response.code}")
            return@withContext buildFallbackDecision(idea, tonePreference, targetAudience, chosenStyle)
        }

        val responseBody = response.body?.string().orEmpty()
        val responseJson = JSONObject(responseBody)
        val candidates = responseJson.optJSONArray("candidates")
        if (candidates != null && candidates.length() > 0) {
            val content = candidates.getJSONObject(0).optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            if (parts != null && parts.length() > 0) {
                val rawText = parts.getJSONObject(0).optString("text")
                val cleanJson = rawText.replace("```json", "").replace("```", "").trim()
                val parsed = JSONObject(cleanJson)

                val scenesList = mutableListOf<Scene>()
                val scenesArr = parsed.optJSONArray("scenes")
                if (scenesArr != null) {
                    for (i in 0 until scenesArr.length()) {
                        val sObj = scenesArr.getJSONObject(i)
                        scenesList.add(
                            Scene(
                                title = sObj.optString("title", "مشهد ${i + 1}"),
                                description = sObj.optString("description", ""),
                                durationInSeconds = sObj.optInt("duration", 5),
                                visualEffect = sObj.optString("visualEffect", "Cinematic Zoom In"),
                                tempo = sObj.optString("tempo", "متزن"),
                                transitionType = sObj.optString("transition", "Fade")
                            )
                        )
                    }
                }

                val sfxList = mutableListOf<String>()
                val sfxArr = parsed.optJSONArray("audioSfxSuggestions")
                if (sfxArr != null) {
                    for (i in 0 until sfxArr.length()) {
                        sfxList.add(sfxArr.optString(i))
                    }
                }

                return@withContext BrainDecision(
                    title = parsed.optString("title", "رؤية إخراجية: $idea"),
                    hook = parsed.optString("hook", "هل تساءلت يوماً عن سر هذا الأثر؟"),
                    scriptScenes = if (scenesList.isNotEmpty()) scenesList else buildDefaultScenes(idea),
                    voiceOverTone = parsed.optString("voiceOverTone", tonePreference),
                    visualDirectives = parsed.optString("visualDirectives", "إضاءة دافئة مع تباين سينمائي وتوهج ذهبي متوازن"),
                    primaryColorHex = parsed.optString("primaryColorHex", chosenStyle?.visualStyle?.primaryColorHex ?: "#E8C547"),
                    backgroundColorHex = parsed.optString("backgroundColorHex", chosenStyle?.visualStyle?.backgroundColorHex ?: "#0B0F19"),
                    ffmpegFilterSnippet = parsed.optString("ffmpegFilterSnippet", "eq=contrast=1.15:brightness=0.02:saturation=1.1"),
                    audioSfxSuggestions = if (sfxList.isNotEmpty()) sfxList else listOf("دوي عميق", "حفيف هادئ", "مؤثر تصاعدي"),
                    selectedStyleName = parsed.optString("selectedStyleName", chosenStyle?.name ?: "النمط الوثائقي الإيماني"),
                    compatibilityScore = parsed.optInt("compatibilityScore", 0).coerceIn(0, 100),
                    aiRationale = parsed.optString("aiRationale", "تم توجيه الإخراج ليتوافق مع معايير الهيبة والوقار وشد الانتباه الهادف"),
                    targetAudience = parsed.optString("targetAudience", targetAudience),
                    callToAction = parsed.optString("callToAction", "شارك لتنال الأجر المبارك")
                )
            }
        }

        return@withContext buildFallbackDecision(idea, tonePreference, targetAudience, chosenStyle)
    }

    private fun buildFallbackDecision(
        idea: String,
        tone: String,
        audience: String,
        chosenStyle: StyleObject? = null
    ): BrainDecision {
        val styleName = chosenStyle?.name ?: "النمط الوثائقي الإيماني الماستر"
        val primaryHex = chosenStyle?.visualStyle?.primaryColorHex ?: "#E8C547"
        val bgHex = chosenStyle?.visualStyle?.backgroundColorHex ?: "#0B0F19"

        return BrainDecision(
            title = "قبس: $idea",
            hook = "رسالة تستحق أن تقف عندها وتتأملها في زحام يومك...",
            scriptScenes = buildDefaultScenes(idea),
            voiceOverTone = tone,
            visualDirectives = "سينمائية وقورة: خلفية داكنة فاخرة Deep Slate (#0B0F19) مع لمسات ذهبية متوهجة وتكبير بطيء للكاميرا",
            primaryColorHex = primaryHex,
            backgroundColorHex = bgHex,
            ffmpegFilterSnippet = "eq=contrast=1.12:brightness=0.02:saturation=1.08,scale=1080:1920:force_original_aspect_ratio=decrease,pad=1080:1920:(ow-iw)/2:(oh-ih)/2",
            audioSfxSuggestions = listOf("نبرة صوت عميقة ومطمئنة", "مؤثر طبيعي هادئ", "دوي سينمائي خافت عند النهاية"),
            selectedStyleName = styleName,
            compatibilityScore = chosenStyle?.overallScore ?: 0,
            aiRationale = "تم اختيار هذا الأسلوب الإخراجي بناءً على السمات المحفوظة في ذاكرة العقل التراكمية لتوفير أقصى درجات التأثير الإيماني والوقار.",
            targetAudience = audience,
            callToAction = "شارك المقطع ليكون نافذة خير وأجر جاري"
        )
    }

    private fun buildDefaultScenes(idea: String): List<Scene> {
        return listOf(
            Scene(
                title = "المشهد 1: الخطاف الاستفتاحي",
                description = "بداية قوية تأسر المشاهد وتطرح تساؤلاً وجدانياً حول: $idea",
                durationInSeconds = 5,
                visualEffect = "Cinematic Slow Zoom In",
                tempo = "سريع وجذاب",
                transitionType = "Fade"
            ),
            Scene(
                title = "المشهد 2: جوهر المعنى والدليل",
                description = "عرض الفكرة بأسلوب سردي وقور مع استحضار المعنى القرآني والنبوي بنقاء فائق.",
                durationInSeconds = 15,
                visualEffect = "Parallax Subtle Ambient",
                tempo = "وقور ومتزن",
                transitionType = "Dissolve"
            ),
            Scene(
                title = "المشهد 3: الأثر والخاتمة والنداء",
                description = "تثبيت الأثر في قلب المشاهد مع خاتمة ذهبية أنيقة تحث على النشر والعمل الصالح.",
                durationInSeconds = 10,
                visualEffect = "Slow Pull Out",
                tempo = "هادئ ومؤثر",
                transitionType = "Fade to Black"
            )
        )
    }

    /**
     * تسجيل تغذية المستخدم الراجعة على قرار العقل
     */
    fun recordDecisionFeedback(isPositive: Boolean) {
        val current = _uiState.value.currentDecision ?: return
        viewModelScope.launch {
            repository.recordStyleFeedback(current.selectedStyleName, isPositive)
            _uiState.value = _uiState.value.copy(
                statusMessage = if (isPositive) "تم تعزيز وزن النمط وتطوير ذكاء العقل!" else "تم تسجيل الملاحظة لتعديل الموازين القادمة."
            )
        }
    }
}

class QabasBrainViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(QabasBrainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return QabasBrainViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
