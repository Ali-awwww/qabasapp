package com.qabas.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * نتيجة الاستدلال والتنفيذ المحلي على الجهاز
 */
data class LocalInferenceResult(
    val outputText: String,
    val modelName: String,
    val tokensGenerated: Int,
    val executionLatencyMs: Long,
    val isNativeJni: Boolean
)

/**
 * محرك الاستدلال المحلي على الجهاز (On-Device Local Inference Engine)
 * ينفذ مهام الوكيل الذكي بدون إنترنت وبدون أي مفاتيح API عبر النماذج المنزلة محلياً
 */
object LocalAgentInferenceEngine {
    private const val TAG = "LocalAgentEngine"

    /**
     * محادثة طبيعية عبر النموذج المحلي — تعيد failure صادقاً إذا كان المحرك غير جاهز،
     * بدل الرد بقالب وهمي. بهذا "مرحباً" ترد من Qwen فعلاً أو برسالة حالة واضحة.
     */
    suspend fun executeLocalChat(
        context: Context,
        userMessage: String,
        maxTokens: Int = 512
    ): Result<LocalInferenceResult> = withContext(Dispatchers.IO) {
        val readyModel = OnDeviceModelManager.getActiveReadyModel(context)
            ?: return@withContext Result.failure(
                IllegalStateException("لا يوجد نموذج محلي مثبت. نزّل Qwen 2.5 أولاً من مركز التنزيل.")
            )
        if (!LlamaCppService.isEngineAvailable()) {
            return@withContext Result.failure(
                IllegalStateException(LlamaCppService.lastError ?: "محرك llama_jni غير متاح لسبب غير معروف.")
            )
        }
        val startTime = System.currentTimeMillis()
        val reply = LlamaCppService.chatLocal(context, readyModel.fileName, userMessage, maxTokens)
            ?: return@withContext Result.failure(
                IllegalStateException(LlamaCppService.lastError ?: "تعذر التوليد لسبب غير معروف.")
            )
        if (reply.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException(LlamaCppService.lastError ?: "النموذج أعاد رداً فارغاً لسبب غير معروف.")
            )
        }
        val latencyMs = System.currentTimeMillis() - startTime
        Result.success(
            LocalInferenceResult(
                outputText = reply,
                modelName = readyModel.name,
                tokensGenerated = (reply.length / 3.8).toInt().coerceAtLeast(1),
                executionLatencyMs = latencyMs,
                isNativeJni = true
            )
        )
    }

    /**
     * تنفيذ توليد الكود المحلي عبر النموذج المثبت
     */
    suspend fun executeLocalCodeSynthesis(
        context: Context,
        userPrompt: String,
        specialization: AgentSpecialization
    ): Result<LocalInferenceResult> = withContext(Dispatchers.IO) {
        val readyModel = OnDeviceModelManager.getActiveReadyModel(context)
            ?: return@withContext Result.failure(
                IllegalStateException("لا يوجد نموذج محلي مثبت في الهاتف. يرجى تنزيل أحد النماذج من مركز التنزيل أولاً.")
            )

        val startTime = System.currentTimeMillis()
        val modelFile = OnDeviceModelManager.getModelFile(context, readyModel)

        if (!modelFile.exists()) {
            return@withContext Result.failure(
                IllegalStateException("ملف النموذج ${readyModel.name} غير موجود في الذاكرة المحلية.")
            )
        }

        // لا يوجد أي توليد وهمي: إن فشل الاستدلال الأصلي نعيد failure صادقاً مع السبب الحقيقي.
        // (حُذف مسار synthesizeLocalCode القالبي نهائياً — أي رد ناجح هو من النموذج عبر JNI حصراً)
        var nativeOutput: String? = null

        try {
            val initSuccess = LlamaCppService.initialize(context, readyModel.fileName)
            if (initSuccess) {
                val fullPrompt = buildSystemPrompt(userPrompt, specialization, readyModel.name)
                nativeOutput = LlamaCppService.generate(
                    prompt = fullPrompt,
                    maxTokens = 600,
                    temperature = 0.4f
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "LlamaCpp JNI execution failed: ${e.message}")
        }

        if (nativeOutput.isNullOrBlank()) {
            return@withContext Result.failure(
                IllegalStateException(
                    LlamaCppService.lastError
                        ?: "فشل الاستدلال المحلي لسبب غير معروف — لم يتم توليد أي قالب بديل."
                )
            )
        }
        val finalCode = nativeOutput
        val isNative = true

        val latencyMs = System.currentTimeMillis() - startTime
        val estimatedTokens = (finalCode.length / 3.8).toInt().coerceAtLeast(45)

        Result.success(
            LocalInferenceResult(
                outputText = finalCode,
                modelName = readyModel.name,
                tokensGenerated = estimatedTokens,
                executionLatencyMs = latencyMs,
                isNativeJni = isNative
            )
        )
    }

    // ملاحظة: حُذفت دالة synthesizeLocalCode القالبية نهائياً بقرار صريح
    // لضمان أن أي رد ناجح صادر من النموذج المحلي عبر JNI حصراً — لا محاكاة.

    private fun buildSystemPrompt(userPrompt: String, spec: AgentSpecialization, modelName: String): String {
        return """
            <|im_start|>system
            You are $modelName running on-device inside Android. Specialization: ${spec.title}.
            Generate concise, production-ready Kotlin Jetpack Compose code with zero extra conversational filler.
            <|im_end|>
            <|im_start|>user
            $userPrompt
            <|im_end|>
            <|im_start|>assistant
        """.trimIndent()
    }
}
