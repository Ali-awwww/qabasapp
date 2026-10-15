package com.qabas.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * LlamaCppService — LLM محلي (Gemma3 1B / Qwen3 0.5B / SmolLM2 135M) عبر llama.cpp JNI.
 * موديل GGUF يوضع في assets/models/llama/ ثم ينسخ لأول مرة إلى filesDir.
 * لا يحتاج مفتاح، يعمل offline تماماً.
 * الرخصة: MIT (llama.cpp) + رخصة الموديل (غالباً Apache-2.0 أو MIT).
 */
object LlamaCppService {
    private const val TAG = "LlamaCppService"
    // توحيد المسار مع OnDeviceModelManager: filesDir/models/local
    private const val MODEL_SUBDIR = "models/local"

    // JNI declarations — تتوفر عند بناء مكتبة llama_jni (app/src/main/cpp)
    external fun llamaInit(modelPath: String, nCtx: Int, nThreads: Int): Long
    external fun llamaGenerate(ctxPtr: Long, prompt: String, nPredict: Int, temp: Float, topP: Float, stopSequences: Array<String>): String
    external fun llamaFree(ctxPtr: Long)

    @Volatile
    private var ctxPtr: Long = 0
    @Volatile
    private var isInitialized = false
    @Volatile
    private var currentModelPath: String? = null
    @Volatile
    private var jniAvailable: Boolean? = null
    @Volatile
    private var jniLoadError: String? = null
    @Volatile
    var lastError: String? = null
        private set

    private fun fail(reason: String): String? {
        lastError = reason
        Log.e(TAG, reason)
        return null
    }

    /** هل مكتبة JNI موجودة فعلاً على الجهاز؟ */
    fun isEngineAvailable(): Boolean {
        if (jniAvailable != null) return jniAvailable!!
        return try {
            System.loadLibrary("llama_jni")
            jniAvailable = true
            true
        } catch (e: UnsatisfiedLinkError) {
            jniLoadError = e.message ?: "مكتبة llama_jni غير موجودة في APK"
            val reason = "تعذر تحميل مكتبة llama_jni: $jniLoadError (تأكد أن البناء مع NDK تم و LLAMA_REAL=ON)"
            lastError = reason
            Log.w(TAG, reason)
            jniAvailable = false
            false
        }
    }

    init {
        isEngineAvailable()
    }

    /** يحدد ملف النموذج من مدير النماذج المحلية مباشرة (لا assets) */
    private suspend fun ensureModelFile(context: Context, modelName: String): File? = withContext(Dispatchers.IO) {
        // modelName قد يكون fileName مثل qwen2.5-coder-0.5b-instruct-q4_k_m.gguf
        val match = OnDeviceModelManager.availableModels.find { it.fileName == modelName }
        val target: File = if (match != null) {
            OnDeviceModelManager.getModelFile(context, match)
        } else {
            File(File(context.filesDir, MODEL_SUBDIR), modelName)
        }
        if (target.exists() && target.length() >= 100_000_000L) return@withContext target
        val detail = when {
            !target.exists() -> "الملف غير موجود في ${target.absolutePath} — أعد التنزيل من مركز النماذج"
            else -> "الملف ناقص: الموجود ${target.length() / 1_048_576}MB في ${target.absolutePath} — أعد التنزيل أو الاستيراد"
        }
        fail(detail)
        null
    }

    /** تهيئة llama.cpp مع الموديل المحدد */
    suspend fun initialize(context: Context, modelName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isEngineAvailable()) {
            lastError = lastError ?: "المحرك غير متاح: $jniLoadError"
            return@withContext false
        }
        if (isInitialized && ctxPtr != 0L && currentModelPath == modelName) return@withContext true
        val modelFile = ensureModelFile(context, modelName) ?: return@withContext false
        try {
            // تحرير السياق السابق إن وجد
            if (ctxPtr != 0L) {
                llamaFree(ctxPtr)
                ctxPtr = 0L
            }
            val nCtx = 2048 // سياق كافٍ للسكربت القصير
            val nThreads = Math.max(2, Runtime.getRuntime().availableProcessors() - 1)
            ctxPtr = llamaInit(modelFile.absolutePath, nCtx, nThreads)
            if (ctxPtr == 0L) {
                fail("فشل llamaInit (أعاد 0) للملف ${modelFile.name} (${modelFile.length() / 1_048_576}MB) — غالباً ذاكرة غير كافية أو ملف GGUF تالف. المسار: ${modelFile.absolutePath}")
                return@withContext false
            }
            currentModelPath = modelName
            isInitialized = true
            lastError = null
            Log.i(TAG, "llama.cpp initialized with $modelName (ctx=$nCtx, threads=$nThreads)")
            true
        } catch (e: Exception) {
            fail("استثناء أثناء التهيئة: ${e.javaClass.simpleName}: ${e.message}")
            // تفريغ السياق الجزئي حتى لا يبقى مؤشر ميت
            ctxPtr = 0L
            isInitialized = false
            false
        } catch (e: UnsatisfiedLinkError) {
            fail("دالة JNI غير مربوطة (وقع ABI): ${e.message} — أعد بناء APK مع NDK")
            jniAvailable = false
            ctxPtr = 0L
            isInitialized = false
            false
        }
    }

    /** توليد نص (سكربت، حوار، أي مهمة نصية) */
    suspend fun generate(
        prompt: String,
        maxTokens: Int = 512,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        stopSequences: Array<String> = arrayOf("</s>", "<|endoftext|>")
    ): String? = withContext(Dispatchers.IO) {
        if (!isInitialized || ctxPtr == 0L) {
            fail("التوليد مستحيل: السياق غير مهيأ (ctx=0) — سبب التهيئة: ${lastError ?: "غير معروف"}")
            return@withContext null
        }
        if (prompt.isBlank()) {
            fail("التوليد مستحيل: البرومبت فارغ")
            return@withContext null
        }
        try {
            val result = llamaGenerate(ctxPtr, prompt, maxTokens, temperature, topP, stopSequences)
            if (result.isNotBlank()) {
                lastError = null
                Log.d(TAG, "Generated ${result.length} chars")
                return@withContext result.trim()
            }
            fail("النموذج أعاد نصاً فارغاً (نافذة السياق 2048 قد تكون ممتلئة أو توقف مبكر) — جرّب رسالة أقصر أو maxTokens أكبر")
            null
        } catch (e: UnsatisfiedLinkError) {
            fail("انقطع ربط JNI أثناء التوليد: ${e.message}")
            null
        } catch (e: Exception) {
            fail("فشل llamaGenerate: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /** بناء برومبت محادثة عامة — يسمح بمحادثة طبيعية مثل أي نموذج */
    fun buildChatPrompt(userMessage: String, modelName: String): String {
        return """
            <|im_start|>system
            You are $modelName running on-device inside Android. Answer naturally and concisely.
            Detect language: reply in the user's language (Arabic if user writes Arabic).
            <|im_end|>
            <|im_start|>user
            $userMessage
            <|im_end|>
            <|im_start|>assistant
        """.trimIndent()
    }

    /** محادثة طبيعية عبر النموذج المحلي — null تعني المحرك غير جاهز */
    suspend fun chatLocal(context: Context, modelFileName: String, userMessage: String, maxTokens: Int = 512): String? {
        if (!initialize(context, modelFileName)) return null
        return generate(buildChatPrompt(userMessage, modelFileName), maxTokens = maxTokens, temperature = 0.7f)
    }

    /** بناء برومبت مخصص للسكربت/المشاهد بالعربية */
    fun buildScriptPrompt(idea: String, styleDescription: String, contentType: String, contentTone: String): String {
        return """
            <|startoftext|>أنت مخرج سينمائي محترف للغة العربية، متخصص في صناعة محتوى إسلامي مؤثّر.
            اكتب سكربت فيديو مقسم لمشاهد واضحة، مع وصف بصري دقيق لكل مشهد، ونص التعليق الصوتي، ومدة تقريبية.
            الفكرة: $idea
            الأسلوب: ${styleDescription.ifBlank { "سينمائي هادئ، ألوان داكنة مع ذهبي" }}
            النوع: ${contentType.ifBlank { "وثائقي قصير" }}
            النبرة: ${contentTone.ifBlank { "متزنة، مؤثرة" }}

            أخرج JSON فقط بهذا التنسيق:
            {
              "scenes": [
                {
                  "title": "عنوان المشهد",
                  "description": "وصف بصري مفصل",
                  "voiceover": "نص التعليق الصوتي",
                  "duration": 8.0,
                  "keywords": ["كلمة1", "كلمة2"]
                }
              ]
            }
            <|endoftext|>
        """.trimIndent()
    }

    fun release() {
        if (ctxPtr != 0L) {
            llamaFree(ctxPtr)
            ctxPtr = 0L
            isInitialized = false
            currentModelPath = null
        }
    }

    /** توليد سكربت محلي عبر llama.cpp وإرجاع List<Scene> */
    suspend fun generateScriptLocal(
        idea: String,
        styleDescription: String,
        contentType: String,
        contentTone: String
    ): List<Scene>? = withContext(Dispatchers.IO) {
        val ctx = AppServices.appContext
        val modelName = OnDeviceModelManager.getActiveReadyModel(ctx)?.fileName ?: return@withContext null
        if (!initialize(ctx, modelName)) return@withContext null
        val prompt = buildScriptPrompt(idea, styleDescription, contentType, contentTone)
        val response = generate(prompt, maxTokens = 1024, temperature = 0.6f)
        if (response.isNullOrBlank()) return@withContext null
        // محاولة تحليل JSON
        try {
            val json = extractJson(response)
            val scenesArray = json.optJSONArray("scenes")
            if (scenesArray == null || scenesArray.length() == 0) return@withContext null
            val scenes = mutableListOf<Scene>()
            for (i in 0 until scenesArray.length()) {
                val obj = scenesArray.getJSONObject(i)
                val scene = Scene(
                    title = obj.optString("title", "مشهد ${i + 1}"),
                    description = obj.optString("description", ""),
                    durationInSeconds = obj.optDouble("duration", 8.0).toInt()
                )
                scenes.add(scene)
            }
            if (scenes.isNotEmpty()) return@withContext scenes
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse local script JSON: ${e.message}")
        }
        null
    }

    private fun extractJson(text: String): org.json.JSONObject {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return org.json.JSONObject(text.substring(start, end + 1))
        }
        return org.json.JSONObject(text)
    }
}