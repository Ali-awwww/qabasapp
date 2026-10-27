package com.qabas.app

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "FirebaseAiService"
private const val TIMEOUT_MS = 60_000L

/**
 * Official Firebase AI Logic & Gemini API Client for Qabas Studio.
 * Routes all generation and multimodal inference through Firebase.ai
 * secured by Firebase App Check attestation without requiring client-side API keys.
 */
object FirebaseAiService {

    // Lazy model initialization adhering to Firebase AI Logic skill guidelines (gemini-3.8-flash)
    private val standardModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash"
        )
    }

    private val jsonModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
            }
        )
    }

    /**
     * Generate text with realistic coroutine timeout (60s) and transparent error reporting
     */
    suspend fun generateText(prompt: String, systemInstruction: String? = null): String = withContext(Dispatchers.IO) {
        val fullPrompt = if (systemInstruction.isNullOrBlank()) {
            prompt
        } else {
            "System Instruction: $systemInstruction\n\nUser Request: $prompt"
        }

        try {
            val response = withTimeout(TIMEOUT_MS) {
                standardModel.generateContent(fullPrompt)
            }
            val text = response.text
            if (!text.isNullOrBlank()) {
                return@withContext text
            } else {
                throw IllegalStateException("النموذج أرجع استجابة فارغة.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Generation timed out after ${TIMEOUT_MS / 1000}s", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI generation failed: ${e.message}", e)
            throw e
        }
    }

    /**
     * Stream text chunks to minimize Time-to-First-Token (TTFT)
     */
    fun generateContentStream(prompt: String, systemInstruction: String? = null): Flow<String> = flow {
        val fullPrompt = if (systemInstruction.isNullOrBlank()) {
            prompt
        } else {
            "System Instruction: $systemInstruction\n\nUser Request: $prompt"
        }

        withTimeout(TIMEOUT_MS) {
            standardModel.generateContentStream(fullPrompt).collect { chunk ->
                val chunkText = chunk.text.orEmpty()
                if (chunkText.isNotEmpty()) {
                    emit(chunkText)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Multi-turn chat conversation powered by Gemini 3.8 Flash
     */
    suspend fun chat(
        messages: List<Pair<Boolean, String>>,
        systemInstruction: String? = null
    ): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        if (!systemInstruction.isNullOrBlank()) {
            sb.append("System Instructions:\n").append(systemInstruction).append("\n\n")
        }
        sb.append("Conversation History:\n")
        for (m in messages) {
            val role = if (m.first) "User" else "Assistant"
            sb.append("$role: ").append(m.second).append("\n")
        }
        sb.append("\nAssistant:")

        generateText(sb.toString())
    }

    /**
     * Direct idea analysis using Gemini 3.8 Flash
     */
    suspend fun analyzeIdea(idea: String): IdeaAnalysis = withContext(Dispatchers.IO) {
        val prompt = """
            أنت محلل محتوى دعوي إخراجي متخصص وكبير المخرجين الفنيين (Art Director).
            يجب عليك استخراج النبرة والمحاور والمشاهد والخطاف من نص المستخدم.
            هام جداً في المشاهد (Visual Metaphors & Pacing): لا تستخدم ترجمة حرفية للمشاهد. استخدم استعارات بصرية طبيعية (رمال الصحراء، قطرة ماء، غيوم، نور).
            اجعل مدد المشاهد (durationHintSeconds) متغيرة: 2-3 ثوانٍ للمقدمة السريعة والخطاف، 5-7 ثوانٍ للتأمل والرسائل العميقة.
            
            الفكرة: $idea
            
            أخرج النتيجة بصيغة JSON حصراً بهذا الشكل:
            {
                "hook": "جملة افتتاحية قوية وجذابة",
                "tone": "روحاني خاشع",
                "confidence": 0.95,
                "themes": ["محور 1", "محور 2"],
                "scenes": [
                    { "title": "عنوان المشهد", "description": "وصف المشهد البصري السينمائي", "durationHintSeconds": 5 }
                ]
            }
        """.trimIndent()

        val response = withTimeout(TIMEOUT_MS) {
            jsonModel.generateContent(prompt)
        }
        val text = response.text ?: throw IllegalStateException("Empty response")
        val cleanJson = text.replace("```json", "").replace("```", "").trim()
        val resultObj = JSONObject(cleanJson)

        val themesList = mutableListOf<String>()
        val themesArr = resultObj.optJSONArray("themes")
        if (themesArr != null) {
            for (i in 0 until themesArr.length()) themesList.add(themesArr.getString(i))
        }

        val scenesList = mutableListOf<String>()
        val scenesArr = resultObj.optJSONArray("scenes")
        if (scenesArr != null) {
            for (i in 0 until scenesArr.length()) {
                val sObj = scenesArr.optJSONObject(i)
                if (sObj != null) {
                    scenesList.add(sObj.optString("title", "مشهد") + ": " + sObj.optString("description", ""))
                }
            }
        }

        val hook = resultObj.optString("hook", "")
        val confidence = resultObj.optDouble("confidence", 0.95).coerceIn(0.1, 1.0)

        IdeaAnalysis(
            summary = idea.take(100),
            suggestedStyle = "سينمائي وقور",
            goal = "رسالة إيمانية",
            targetAudience = "الجمهور العام",
            tone = resultObj.optString("tone", "روحاني"),
            keywords = themesList,
            proposedScenes = scenesList.takeIf { it.isNotEmpty() } ?: listOf("مشهد 1: عرض الفكرة الأساسية"),
            viralityScore = (confidence * 100).toInt(),
            hookSuggestions = if (hook.isNotBlank()) listOf(hook) else emptyList(),
            ctaSuggestions = emptyList(),
            confidence = confidence,
            themes = themesList,
            source = "GEMINI_3_8_FLASH"
        )
    }

    /**
     * Direct script scenes generation using Gemini 3.8 Flash
     */
    suspend fun generateScriptScenes(
        idea: String,
        styleDescription: String = "",
        contentType: String = "",
        contentTone: String = ""
    ): List<Scene> = withContext(Dispatchers.IO) {
        val prompt = """
            أنت المخرج الفني وكبير مونتيري منصة «قَبَس» (QABAS AI Studio) - المتخصص في صناعة المحتوى الإسلامي والقرآني القصير (Reels/Shorts/TikTok) بهوية بصرية وروحية فاخرة.
            الموضوع: $idea
            نوع المحتوى: $contentType
            النبرة: $contentTone
            التوجيهات الأسلوبية: $styleDescription

            أخرج النتيجة حصرياً كـ JSON Array لمشاهد سينمائية [ { ... } ] بدون أي نص إضافي:
            [
              {
                "title": "عنوان المشهد بالعربية",
                "description": "Cinematic visual description in English for b-roll generation",
                "durationInSeconds": 4,
                "transitionType": "Fade",
                "visualEffect": "Cinematic Golden Light",
                "tempo": "هادئ"
              }
            ]
        """.trimIndent()

        val response = withTimeout(TIMEOUT_MS) {
            jsonModel.generateContent(prompt)
        }
        val text = response.text ?: throw IllegalStateException("Empty response")
        val cleanJson = if (text.contains("[")) text.substring(text.indexOf("["), text.lastIndexOf("]") + 1) else text
        val jsonArray = JSONArray(cleanJson)
        val scenes = mutableListOf<Scene>()
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            scenes.add(
                Scene(
                    title = item.optString("title", "مشهد ${i + 1}"),
                    description = item.optString("description", ""),
                    durationInSeconds = item.optInt("durationInSeconds", 4),
                    transitionType = item.optString("transitionType", "Fade"),
                    visualEffect = item.optString("visualEffect", "Cinematic"),
                    tempo = item.optString("tempo", "متوسط")
                )
            )
        }
        scenes
    }
}
