package com.qabas.app

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.qabas.app.ui.theme.*
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.ensureActive

/**
 * منطق المهام (Agent Mission Logic): التنفيذ + البناء السحابي + حفظ/استرجاع الحالة.
 */
/**
 * تنفيذ مهمة الوكيل البرمجي المستقل بالتفكير واستدعاء الأدوات
 */
suspend fun executeAgentMission(
    context: Context,
    prompt: String,
    spec: AgentSpecialization,
    repo: RepoVaultEntry?,
    selectedModel: String = "gemini-2.5-flash",
    history: List<AgentChatMessage> = emptyList(),
    onUpdate: (AgentMissionSession) -> Unit,
    onFinished: (AgentMissionSession) -> Unit
) = withContext(Dispatchers.IO) {
    // 📒 كل مهمة نهائية تُسجَّل في سجل العمليات — لا نجاح ولا فشل صامت
    fun recordRun(session: AgentMissionSession) {
        val ok = session.status == AgentTaskStatus.COMPLETED
        val tools = session.steps.map { it.toolName }.filter { it.isNotBlank() }.distinct()
            .ifEmpty { listOf("plan_only") }
        AgentRunLog.record(
            context = context,
            title = session.userPrompt.ifBlank { "مهمة وكيل" },
            tools = tools,
            summary = session.finalReport.ifBlank { "انتهت المهمة بحالة: ${session.status.name}" },
            ok = ok,
            error = if (ok) "" else session.finalReport
        )
    }
    try {
        executeAgentMissionInner(context, prompt, spec, repo, selectedModel, history, onUpdate) { s ->
            recordRun(s)
            onFinished(s)
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        val cancelled = AgentMissionSession(
            userPrompt = prompt,
            specialization = spec,
            status = AgentTaskStatus.CANCELLED,
            finalReport = "أُلغيت المهمة بواسطتك ⏹️ — لم يُرسل أي طلب جديد بعد الإلغاء."
        )
        recordRun(cancelled)
        onFinished(cancelled)
        throw e
    }
}

private suspend fun executeAgentMissionInner(
    context: Context,
    prompt: String,
    spec: AgentSpecialization,
    repo: RepoVaultEntry?,
    selectedModel: String,
    history: List<AgentChatMessage> = emptyList(),
    onUpdate: (AgentMissionSession) -> Unit,
    onFinished: (AgentMissionSession) -> Unit
) {
    val initialSteps = mutableListOf<AgentExecutionStep>()
    var mission = AgentMissionSession(
        userPrompt = prompt,
        specialization = spec,
        status = AgentTaskStatus.PLANNING,
        steps = initialSteps
    )
    onUpdate(mission)
    coroutineContext.ensureActive()

    // (12.12) ذاكرة الوكيل: سياق المشروع المحفوظ للجلسة النشطة (إن وجد)،
    // يُلحق بموجه التخطيط في المسارين (المحلي والسحابي). التركيب يرثه
    // عبر الخطة المولدة. لا ذاكرة = لا إلحاق، ولا وهم سياق.
    val memoryBlock = runCatching {
        VibeAgentMemoryStore.loadForActiveSession(context)
            ?.let { VibeAgentMemoryStore.contextBlock(it) }
    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull().orEmpty()

    // (ق31) سجل المحادثة: كان كل استدعاء بلا أي سجل (دور منفصل تماماً)
    // فينسى النموذج ما قيل قبله في الجلسة نفسها؛ يُلحق السجل الأخير
    // المحدود بموجه التخطيط في المسارين أيضاً.
    val historyBlock = AgentChatSessionManager.historyBlock(history)

    val isOffline = OnDeviceModelManager.isOfflineModeActive(context)
    if (isOffline) {
        val readyModel = OnDeviceModelManager.getActiveReadyModel(context)
        if (readyModel == null) {
            val errSnippet = "❌ تعذر البدء: تم تفعيل الوضع المحلي ولكن لا يوجد نموذج مثبت في الذاكرة. يرجى تنزيل أحد النماذج من مركز التنزيل أولاً."
            val stepFailed = AgentExecutionStep(
                title = "فحص النموذج المحلي المنزّل",
                description = "التحقق من وجود ملف النموذج في الذاكرة المحلية...",
                isRunning = false,
                isSuccess = false,
                outputSnippet = errSnippet
            )
            initialSteps.add(stepFailed)
            mission = mission.copy(
                steps = initialSteps.toList(),
                status = AgentTaskStatus.FAILED,
                finalReport = "يلزم تنزيل نموذج محلي أولاً لتشغيل الوكيل بدون إنترنت."
            )
            onUpdate(mission)
    coroutineContext.ensureActive()
            onFinished(mission)
            return
        }

        // بوابة الذاكرة (12.4): قارن متطلبات RAM للنموذج بالذاكرة المتاحة
        // الآن في الجهاز عبر ActivityManager.MemoryInfo — لا بـ
        // Runtime.getRuntime().maxMemory() فهي heap التطبيق لا ذاكرة الجهاز.
        val modelRamBytes = OnDeviceModelManager.ramRequirementBytes(readyModel)
        val capability = VibeDeviceCapability.measure(context)
        val deviceAvailableRamBytes = capability.availableRamBytes
        if (modelRamBytes > deviceAvailableRamBytes) {
            val modelRamMb = (modelRamBytes / 1024 / 1024)
            val deviceRamMb = (deviceAvailableRamBytes / 1024 / 1024)
            val deviceTotalMb = (capability.totalRamBytes / 1024 / 1024)
            val errSnippet = "❌ تعذر البدء: النموذج «${readyModel.name}» يحتاج ${modelRamMb}MB من ذاكرة RAM بينما المتاح الآن في الجهاز ${deviceRamMb}MB فقط (من أصل ${deviceTotalMb}MB). الذاكرة غير كافية — اختر نموذجاً أخف أو أغلق تطبيقات لتحرير ذاكرة."
            val stepFailed = AgentExecutionStep(
                title = "فحص متطلبات ذاكرة النموذج المحلي",
                description = "مقارنة ذاكرة النموذج المطلوبة بالذاكرة المتاحة الآن في الجهاز قبل أي تنفيذ...",
                isRunning = false,
                isSuccess = false,
                outputSnippet = errSnippet
            )
            initialSteps.add(stepFailed)
            mission = mission.copy(
                steps = initialSteps.toList(),
                status = AgentTaskStatus.FAILED,
                finalReport = "توقفت المهمة لعدم كفاية ذاكرة RAM: ${readyModel.name} يحتاج ${modelRamMb}MB والمتاح الآن في الجهاز ${deviceRamMb}MB (من أصل ${deviceTotalMb}MB)."
            )
            onUpdate(mission)
    coroutineContext.ensureActive()
            onFinished(mission)
            return
        }

        // بوابة الصدق: لا محرك أصلي = لا مهمة وهمية. نفشل مبكراً بالسبب الحقيقي.
        if (!LlamaCppService.isEngineAvailable()) {
            val reason = LlamaCppService.lastError ?: "محرك llama_jni غير متاح."
            val stepFailed = AgentExecutionStep(
                title = "فحص محرك الاستدلال المحلي",
                description = "التحقق من جاهزية الجسر الأصلي قبل أي تنفيذ...",
                isRunning = false,
                isSuccess = false,
                outputSnippet = "❌ تعذر البدء: $reason"
            )
            initialSteps.add(stepFailed)
            mission = mission.copy(
                steps = initialSteps.toList(),
                status = AgentTaskStatus.FAILED,
                finalReport = "توقفت المهمة بصدق: $reason لا يوجد أي مخرج مولّد — لم يُستخدم أي قالب محاكاة."
            )
            onUpdate(mission)
            coroutineContext.ensureActive()
            onFinished(mission)
            return
        }

        // الخطوة 1: التحليل والتخطيط المعماري المحلي
        val step1 = AgentExecutionStep(
            title = "التحليل والتخطيط المعماري المحلي (${readyModel.name}) 🧠",
            description = "تحليل الأمر عبر النموذج المحلي المثبت بدون إنترنت وبدون مفتاح API...",
            toolName = "local_reasoning",
            isRunning = true
        )
        initialSteps.add(step1)
        mission = mission.copy(steps = initialSteps.toList(), status = AgentTaskStatus.PLANNING)
        onUpdate(mission)
    coroutineContext.ensureActive()

        val startTime1 = System.currentTimeMillis()
        // تخطيط حقيقي من النموذج — لا نص جاهز مسبقاً.
        val planText = LlamaCppService.chatLocal(
            context, readyModel.fileName,
            "حلل هذا الأمر واكتب خطة عمل معمارية موجزة: $prompt (التخصص: ${spec.title})" +
                if (memoryBlock.isNotBlank()) "\n\n$memoryBlock" else "" +
                if (historyBlock.isNotBlank()) "\n\n$historyBlock" else "",
            maxTokens = 400
        )
        val latencyMs1 = System.currentTimeMillis() - startTime1

        if (planText.isNullOrBlank()) {
            val reason = LlamaCppService.lastError ?: "فشل التخطيط لسبب غير معروف."
            initialSteps[0] = step1.copy(
                isRunning = false,
                isSuccess = false,
                outputSnippet = "❌ $reason"
            )
            mission = mission.copy(
                steps = initialSteps.toList(),
                status = AgentTaskStatus.FAILED,
                finalReport = "فشلت خطوة التخطيط: $reason"
            )
            onUpdate(mission)
            coroutineContext.ensureActive()
            onFinished(mission)
            return
        }

        initialSteps[0] = step1.copy(
            isRunning = false,
            isSuccess = true,
            outputSnippet = planText + " [رد حقيقي من ${readyModel.name} • ${latencyMs1}ms • JNI]"
        )
        mission = mission.copy(steps = initialSteps.toList(), status = AgentTaskStatus.INSPECTING)
        onUpdate(mission)
    coroutineContext.ensureActive()

        // الخطوة 2: توثيق سياق النموذج المحلي دون الادعاء بفحص ملفات المشروع
        val step2 = AgentExecutionStep(
            title = "التحقق من بيئة النموذج المحلي",
            description = "قراءة بيانات النموذج والخطة الناتجة؛ هذا المسار لا يقرأ شجرة ملفات المشروع.",
            toolName = "local_runtime_context",
            isRunning = true
        )
        initialSteps.add(step2)
        mission = mission.copy(steps = initialSteps.toList())
        onUpdate(mission)
    coroutineContext.ensureActive()

        val modelFileSizeMb = OnDeviceModelManager.getModelFile(context, readyModel).length() / 1_048_576
        val localInspection = "النموذج الفعلي: ${readyModel.name} — الملف ${readyModel.fileName} (${modelFileSizeMb}MB على القرص).\n" +
            "الخطة المستلمة من النموذج (مقتطف): ${planText.take(300)}"
        initialSteps[1] = step2.copy(
            isRunning = false,
            isSuccess = true,
            outputSnippet = localInspection
        )
        mission = mission.copy(steps = initialSteps.toList(), status = AgentTaskStatus.WRITING)
        onUpdate(mission)
    coroutineContext.ensureActive()

        // الخطوة 3: صياغة وتوليد الكود البرمجي محلياً
        val step3 = AgentExecutionStep(
            title = "توليد وصياغة الكود البرمجي محلياً ✍️",
            description = "تشغيل استدلال النموذج المحلي ${readyModel.name} لصياغة كود Kotlin و Compose...",
            toolName = "local_synthesis",
            isRunning = true
        )
        initialSteps.add(step3)
        mission = mission.copy(steps = initialSteps.toList())
        onUpdate(mission)
    coroutineContext.ensureActive()

        val localResult = LocalAgentInferenceEngine.executeLocalCodeSynthesis(
            context = context,
            userPrompt = prompt,
            specialization = spec
        )

        if (localResult.isFailure) {
            val err = localResult.exceptionOrNull()?.localizedMessage ?: "فشل الاستدلال المحلي"
            initialSteps[2] = step3.copy(
                isRunning = false,
                isSuccess = false,
                outputSnippet = "❌ فشل التوليد المحلي: $err"
            )
            mission = mission.copy(
                steps = initialSteps.toList(),
                status = AgentTaskStatus.FAILED,
                finalReport = "تعذر إكمال التوليد المحلي: $err"
            )
            onUpdate(mission)
    coroutineContext.ensureActive()
            onFinished(mission)
            return
        }

        val inferenceData = localResult.getOrNull()!!
        val generatedCode = inferenceData.outputText
        val info3 = " [الرموز: ${inferenceData.tokensGenerated} • زمن المعالجة: ${inferenceData.executionLatencyMs}ms • النمط: ${if (inferenceData.isNativeJni) "تسريع JNI" else "معالجة محلية On-Device"}]"

        initialSteps[2] = step3.copy(
            isRunning = false,
            isSuccess = true,
            outputSnippet = generatedCode + info3
        )
        mission = mission.copy(steps = initialSteps.toList(), status = AgentTaskStatus.COMPLETED)
        onUpdate(mission)

        // الخطوة 4: التقرير النهائي
        val finalReport = """
            اكتمل توليد مسودة الكود محلياً عبر النموذج المثبت، بدون إنترنت أو مفتاح API.
            • النموذج المستخدم: ${readyModel.name} (${readyModel.sizeFormatted})
            • بيئة التشغيل: On-Device Local Inference (${if (inferenceData.isNativeJni) "تسريع المعالج المحلي JNI" else "معالجة محلية داخلية"})
            • زمن المعالجة الفعلي: ${inferenceData.executionLatencyMs}ms
            • عدد الرموز المستخرجة: ${inferenceData.tokensGenerated}
            • تم توليد مسودة كود من النموذج المحلي للطلب: «$prompt».
            • لم يتم تعديل ملفات المشروع تلقائياً في هذا المسار؛ راجع المخرجات وطبّق التغييرات بعد مراجعتها.
        """.trimIndent()

        mission = mission.copy(
            finalReport = finalReport,
            status = AgentTaskStatus.COMPLETED
        )
        onFinished(mission)
        return
    }

    // التحقق الصادق من توفر مفتاح حقيقي
    val (provider, apiKey, keySource) = AgentConnectivityEngine.resolveKeyWithSource(context)
    if (provider == DetectedProvider.NONE || apiKey.isBlank()) {
        val errSnippet = "❌ تعذر البدء: لا يوجد مفتاح API نشط. يلتزم الوكيل بدستور الصدق التام ولا يستخدم أي محاكاة وهمية. يرجى إدخال مفتاح صالح وفحصه حياً قبل التنفيذ."
        val stepFailed = AgentExecutionStep(
            title = "فحص اعتمادات النموذج والاتصال",
            description = "التحقق من توفر المفتاح وصلاحية الاتصال بالسيرفر...",
            isRunning = false,
            isSuccess = false,
            outputSnippet = errSnippet
        )
        initialSteps.add(stepFailed)
        mission = mission.copy(
            steps = initialSteps.toList(),
            status = AgentTaskStatus.FAILED,
            finalReport = "تم إيقاف التنفيذ لعدم وجود مفتاح API نشط. الوكيل لا يقوم بأي محاكاة وهمية."
        )
        onUpdate(mission)
        onFinished(mission)
        return
    }

    // الخطوة 1: التحليل المعماري الحقيقي عبر الذكاء الاصطناعي (Agent Reasoning & Planning)
    val step1 = AgentExecutionStep(
        title = "تحليل الأمر والتخطيط المعماري 🧠",
        description = "إرسال الأمر لنموذج $selectedModel (${provider.displayName}) وتوليد خطة العمل...",
        toolName = "generate_plan",
        isRunning = true
    )
    initialSteps.add(step1)
    mission = mission.copy(steps = initialSteps.toList(), status = AgentTaskStatus.PLANNING)
    onUpdate(mission)

    val sysInstruction = """
        أنت مهندس وكيل ذكي مستقل لتطبيق «استوديو خادم المسلم» لنظام Android (Kotlin + Jetpack Compose + Material 3).
        المهمة المطلوبة من المستخدم: $prompt
        نمط تخصص الوكيل: ${spec.title}
        
        قم بصياغة خطة عمل برمجية تفصيلية، وحدد الملفات المستهدفة بالتعديل أو الإنشاء، واشرح الخطوات الهندسية اللازمة.
    """.trimIndent() + if (memoryBlock.isNotBlank()) "\n\n$memoryBlock" else "" +
        if (historyBlock.isNotBlank()) "\n\n$historyBlock" else ""

    val startTime1 = System.currentTimeMillis()
    // (12.14) إيقاف عند حد التكلفة قبل أي استدعاء سحابي مدفوع — بعد
    // المسار المحلي (بلا تكلفة API) وقبل خطوة التخطيط.
    val limitMessage = VibeCostTracker.checkLimit(context)
    if (limitMessage != null) {
        initialSteps[0] = step1.copy(
            isRunning = false,
            isSuccess = false,
            outputSnippet = "⛔ $limitMessage"
        )
        mission = mission.copy(
            steps = initialSteps.toList(),
            status = AgentTaskStatus.FAILED,
            finalReport = "توقفت المهمة عند حد التكلفة: $limitMessage"
        )
        onUpdate(mission)
        onFinished(mission)
        return
    }
    // بثّ تدريجي حي + تجاوز تلقائي للمفتاح البديل عند الفشل
    val planResult = UniversalKeyBrain.executeRealAgentStepWithFailover(
        provider, prompt, sysInstruction, apiKey, selectedModel,
        stream = true,
        onPartial = { partial ->
            initialSteps[0] = step1.copy(
                outputSnippet = (partial.take(350) + " ▍")
            )
            mission = mission.copy(steps = initialSteps.toList())
            onUpdate(mission)
        },
        context = context,
        missionTitle = prompt
    )
    val latencyMs1 = System.currentTimeMillis() - startTime1

    if (planResult.isFailure) {
        val errorMsg = planResult.exceptionOrNull()?.localizedMessage ?: "فشل الاتصال الحي بالنموذج"
        initialSteps[0] = step1.copy(
            isRunning = false,
            isSuccess = false,
            outputSnippet = "❌ فشل الاتصال الحي ($latencyMs1 ms): $errorMsg"
        )
        mission = mission.copy(
            steps = initialSteps.toList(),
            status = AgentTaskStatus.FAILED,
            finalReport = "فشل تنفيذ خطوة التخطيط المعماري: $errorMsg"
        )
        onUpdate(mission)
        onFinished(mission)
        return
    }

    val planData = planResult.getOrNull()
    val planText = planData?.text?.takeIf { it.isNotBlank() }
    if (planText == null) {
        val errorMsg = "أعاد مزود الذكاء استجابة تخطيط فارغة؛ لم يتم اعتماد خطة للمهمة."
        initialSteps[0] = step1.copy(
            isRunning = false,
            isSuccess = false,
            outputSnippet = "❌ $errorMsg"
        )
        mission = mission.copy(
            steps = initialSteps.toList(),
            status = AgentTaskStatus.FAILED,
            finalReport = errorMsg
        )
        onUpdate(mission)
        onFinished(mission)
        return
    }
    val tokensInfo1 = " [الرموز المستهلكة: ${planData?.totalTokens ?: 0} • زمن الاستجابة: ${latencyMs1}ms]"

    initialSteps[0] = step1.copy(
        isRunning = false,
        isSuccess = true,
        outputSnippet = (planText.take(350) + if (planText.length > 350) "..." else "") + tokensInfo1
    )
    mission = mission.copy(steps = initialSteps.toList(), status = AgentTaskStatus.INSPECTING)
    onUpdate(mission)

    // الخطوة 2: الفحص الحقيقي لشجرة المشروع أو مستودع GitHub (Code Inspection)
    val step2 = AgentExecutionStep(
        title = "فحص شجرة المستودع والملفات المستهدفة 🔍",
        description = "قراءة مسارات المشروع وتحديد ملفات التطبيق المعنية بالعملية...",
        toolName = "list_tree",
        isRunning = true
    )
    initialSteps.add(step2)
    mission = mission.copy(steps = initialSteps.toList())
    onUpdate(mission)

    val targetRepo = repo
    val inspectionResult: Result<List<RepoFile>?> = when {
        targetRepo == null -> Result.failure(IllegalStateException("لا يوجد مستودع مرتبط بالمهمة."))
        targetRepo.token.isBlank() -> Result.failure(IllegalStateException("رمز الوصول للمستودع غير متوفر."))
        targetRepo.defaultBranch.isBlank() -> Result.failure(IllegalStateException("الفرع الافتراضي للمستودع غير محدد."))
        else -> runCatching {
            GitHubRepoClient(targetRepo.owner, targetRepo.repo, targetRepo.token)
                .getTree(targetRepo.defaultBranch)
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
    }
    val tree = inspectionResult.getOrNull()
    val relevantFiles = tree
        ?.filter { it.path.endsWith(".kt") || it.path.endsWith(".xml") }
        ?.take(8)
        ?.map { it.path }
    if (tree.isNullOrEmpty() || relevantFiles.isNullOrEmpty()) {
        val reason = inspectionResult.exceptionOrNull()?.localizedMessage
            ?: "لم تُرجع شجرة المستودع ملفات Kotlin أو XML قابلة للفحص."
        initialSteps[1] = step2.copy(
            isRunning = false,
            isSuccess = false,
            outputSnippet = "❌ تعذر فحص المستودع: $reason"
        )
        mission = mission.copy(
            steps = initialSteps.toList(),
            status = AgentTaskStatus.FAILED,
            finalReport = "أُوقفت المهمة قبل توليد الكود لأن فحص المستودع لم ينجح: $reason"
        )
        onUpdate(mission)
        onFinished(mission)
        return
    }
    val inspectionSummary = "ملفات تم التحقق من وجودها فعلياً على فرع (${targetRepo?.defaultBranch ?: "غير محدد"}):\n" +
        relevantFiles.joinToString("\n• ", prefix = "• ")

    initialSteps[1] = step2.copy(
        isRunning = false,
        isSuccess = true,
        outputSnippet = inspectionSummary
    )
    mission = mission.copy(steps = initialSteps.toList(), status = AgentTaskStatus.WRITING)
    onUpdate(mission)

    // الخطوة 3: توليد الكود الحقيقي وصياغة التعديل البرمجي (Real Code Synthesis)
    val step3 = AgentExecutionStep(
        title = "صياغة التعديل البرمجي وتوليد الكود الفعلي ✍️",
        description = "استدعاء النموذج لتوليد كود Kotlin و Compose المباشر وتنسيق التعديلات...",
        toolName = "code_synthesis",
        isRunning = true
    )
    initialSteps.add(step3)
    mission = mission.copy(steps = initialSteps.toList())
    onUpdate(mission)

    val startTime3 = System.currentTimeMillis()
    val codeResult = AgentConnectivityEngine.executeRealCodeSynthesis(
        provider = provider,
        apiKey = apiKey,
        userPrompt = prompt,
        architecturalPlan = planText,
        model = selectedModel,
        stream = true,
        onPartial = { partial ->
            initialSteps[2] = step3.copy(
                outputSnippet = (partial.take(350) + " ▍")
            )
            mission = mission.copy(steps = initialSteps.toList())
            onUpdate(mission)
        },
        context = context,
        missionTitle = prompt
    )
    val latencyMs3 = System.currentTimeMillis() - startTime3

    if (codeResult.isFailure) {
        val errorMsg = codeResult.exceptionOrNull()?.localizedMessage ?: "تعذر توليد الكود البرمجي"
        initialSteps[2] = step3.copy(
            isRunning = false,
            isSuccess = false,
            outputSnippet = "❌ تعذر توليد الكود ($latencyMs3 ms): $errorMsg"
        )
        mission = mission.copy(
            steps = initialSteps.toList(),
            status = AgentTaskStatus.FAILED,
            finalReport = "تعذر إكمال توليد الكود: $errorMsg"
        )
        onUpdate(mission)
        onFinished(mission)
        return
    }

    val codeData = codeResult.getOrNull()
    val generatedCode = codeData?.text?.takeIf { it.isNotBlank() }
    if (generatedCode == null) {
        val errorMsg = "أعاد مزود الذكاء استجابة كود فارغة؛ لم يتم إنشاء مقترح قابل للمراجعة."
        initialSteps[2] = step3.copy(
            isRunning = false,
            isSuccess = false,
            outputSnippet = "❌ $errorMsg"
        )
        mission = mission.copy(
            steps = initialSteps.toList(),
            status = AgentTaskStatus.FAILED,
            finalReport = errorMsg
        )
        onUpdate(mission)
        onFinished(mission)
        return
    }
    val tokensInfo3 = " [الرموز: ${codeData?.totalTokens ?: 0} • زمن التوليد: ${latencyMs3}ms]"

    initialSteps[2] = step3.copy(
        isRunning = false,
        isSuccess = true,
        outputSnippet = generatedCode + tokensInfo3
    )
    // استخراج مقترحات الفروقات من كتل الكود لمعاينتها واعتمادها أو تجاهلها
    val diffs = AgentPatchKit.extractDiffs(prompt, generatedCode)
    mission = mission.copy(
        steps = initialSteps.toList(),
        status = AgentTaskStatus.COMPLETED,
        proposedDiffs = diffs
    )
    onUpdate(mission)

    // الخطوة 4: التقرير يميّز بين توليد مقترح كود وتطبيقه أو بنائه فعلياً.
    val totalTime = latencyMs1 + latencyMs3
    val totalTokens = (planData?.totalTokens ?: 0) + (codeData?.totalTokens ?: 0)
    val proposalStatus = if (diffs.isNotEmpty()) {
        "تم استخراج ${diffs.size} مقترح تغيير للمراجعة؛ لم تُطبّق على ملفات المشروع تلقائياً."
    } else {
        "لم تُستخرج فروقات ملفات قابلة للمراجعة؛ لم تُسجّل تغييرات على المستودع."
    }
    val report = """
        اكتملت مرحلة التحليل وتوليد المخرجات عبر استدعاءات حية.
        • المزود النشط: ${provider.displayName}
        • مصدر المفتاح: ${keySource.label}
        • إجمالي زمن الاستجابة الفعلي: ${totalTime}ms
        • إجمالي الرموز المستهلكة (Tokens): $totalTokens
        • تم فحص شجرة المستودع وتوليد المخرجات البرمجية للطلب: «$prompt».
        • $proposalStatus
        • لم يتم دفع أي تغييرات أو تشغيل بناء APK في هذه المرحلة.
    """.trimIndent()

    mission = mission.copy(
        finalReport = report,
        status = AgentTaskStatus.COMPLETED
    )
    onFinished(mission)
}

/**
 * 🔬 حلقة التنفيذ الفعلي: تُطلق بناءً حقيقياً على GitHub Actions وتراقبه حتى الاكتمال،
 * ثم تُلحق خطوة تحقق حقيقية بالمهمة (نجاح/فشل + السجل عند الفشل) — لا زر فقط بلا متابعة.
 * تُحدّث الواجهة حياً عبر onUpdate عند كل استطلاع.
 */
suspend fun runBuildVerification(
    context: Context,
    baseMission: AgentMissionSession,
    pollIntervalMs: Long = 15_000L,
    timeoutMs: Long = 480_000L,
    onUpdate: (AgentMissionSession) -> Unit
): AgentMissionSession {
    val repo = RepoVaultManager.getActiveRepo(context)
    val stepId = "ci_exec_verify"

    fun emitStep(
        running: Boolean,
        success: Boolean,
        snippet: String
    ): AgentMissionSession {
        val step = AgentExecutionStep(
            id = stepId,
            title = "🔬 التحقق من البناء الفعلي على GitHub Actions",
            description = "إطلاق سير android-ci.yml ومتابعته حتى الاكتمال...",
            toolName = "ci_execution_loop",
            outputSnippet = snippet,
            isRunning = running,
            isSuccess = success
        )
        val steps = baseMission.steps.filterNot { it.id == stepId } + step
        return baseMission.copy(steps = steps, status = if (running) AgentTaskStatus.BUILDING else baseMission.status)
    }

    if (repo == null || repo.token.isBlank() || repo.defaultBranch.isBlank()) {
        val reason = when {
            repo == null -> "لا يوجد مستودع مرتبط."
            repo.token.isBlank() -> "رمز الوصول للمستودع غير متوفر."
            else -> "الفرع الافتراضي للمستودع غير محدد."
        }
        val failed = emitStep(false, false, "🔗 تعذر بدء البناء: $reason اربط مستودعاً صالحاً وحدد فرعه أولاً.")
            .copy(status = AgentTaskStatus.FAILED)
        onUpdate(failed)
        return failed
    }

    var mission = emitStep(true, true, "⏳ جاري إطلاق البناء على ${repo.fullName}...")
    onUpdate(mission)

    val result = AgentBuildVerifier.triggerAndAwait(
        owner = repo.owner,
        repo = repo.repo,
        token = repo.token,
        branch = repo.defaultBranch,
        pollIntervalMs = pollIntervalMs,
        timeoutMs = timeoutMs,
        onTick = { run ->
            val snippet = buildString {
                append(AgentBuildVerifier.describe(run))
                run?.htmlUrl?.takeIf { it.isNotBlank() }?.let { append("\n🔗 $it") }
            }
            mission = emitStep(true, true, snippet)
            onUpdate(mission)
        }
    )

    val finalSnippet = buildString {
        append(result.message)
        if (result.commitSha.isNotBlank()) append("\n• الكوميت: ${result.commitSha.take(8)}")
        result.run?.htmlUrl?.takeIf { it.isNotBlank() }?.let { append("\n🔗 $it") }
        if (result.failedLogs.isNotBlank()) append("\n\n— مقتطف من سجل الفشل —\n${result.failedLogs}")
    }

    val completed = emitStep(false, result.isSuccess, finalSnippet).copy(
        buildTriggered = result.isSuccess || result.run != null,
        apkUrl = result.run?.htmlUrl ?: baseMission.apkUrl,
        status = when {
            result.isSuccess -> AgentTaskStatus.COMPLETED
            result.timedOut -> baseMission.status
            else -> AgentTaskStatus.FAILED
        },
        finalReport = baseMission.finalReport + "\n\n" + if (result.isSuccess) {
            "✅ تحقق فعلي: نجح بناء الكود على GitHub Actions — الكوميت ${result.commitSha.take(8)}."
        } else {
            "🔬 تحقق فعلي: ${result.message}"
        }
    )
    onUpdate(completed)
    return completed
}

// ═══════════════════════════════════════════════════════════════
// ترميز/فك ترميز جلسة المهمة النشطة (12.12 — إصلاح عيب الاستعادة).
//
// العيب الموثق: كان يُحفظ معرف الخطوة فقط (`steps` كمصفوفة معرفات)
// فتُستعاد الخطوات بعناوين وأوصاف فارغة. هنا تُحفظ الخطوة كاملة
// (المعرف + العنوان + الوصف + الأداة + حالة النجاح + مقتطف المخرجات
// حتى 2000 محرف) + الحالة + التقرير النهائي + الفروقات المقترحة
// (حتى 30 ألف محرف لكل فرق) + المسارات المطبقة.
// فك الترميز يقبل الصيغة القديمة (خطوات كمعرفات نصية) للتوافق الرجعي.
// ═══════════════════════════════════════════════════════════════

private const val MISSION_STEP_SNIPPET_MAX_CHARS = 2000
private const val MISSION_DIFF_TEXT_MAX_CHARS = 30_000

fun encodeMissionSession(mission: AgentMissionSession): String {
    val stepsArray = JSONArray()
    mission.steps.forEach { step ->
        stepsArray.put(
            JSONObject().apply {
                put("id", step.id)
                put("title", step.title)
                put("description", step.description)
                put("toolName", step.toolName)
                put("outputSnippet", step.outputSnippet.take(MISSION_STEP_SNIPPET_MAX_CHARS))
                put("isRunning", step.isRunning)
                put("isSuccess", step.isSuccess)
            }
        )
    }
    val diffsArray = JSONArray()
    mission.proposedDiffs.forEach { diff ->
        diffsArray.put(
            JSONObject().apply {
                put("filePath", diff.filePath)
                put("additions", diff.additions)
                put("deletions", diff.deletions)
                put("diffText", diff.diffText.take(MISSION_DIFF_TEXT_MAX_CHARS))
            }
        )
    }
    return JSONObject().apply {
        put("missionId", mission.id)
        put("userPrompt", mission.userPrompt)
        put("specialization", mission.specialization.name)
        put("status", mission.status.name)
        put("steps", stepsArray)
        put("finalReport", mission.finalReport)
        put("modifiedFiles", JSONArray(mission.modifiedFiles))
        put("proposedDiffs", diffsArray)
        put("appliedPaths", JSONArray(mission.appliedPaths))
        put("buildTriggered", mission.buildTriggered)
        mission.apkUrl?.let { put("apkUrl", it) }
        put("timestamp", mission.timestamp)
    }.toString()
}

fun decodeMissionSession(jsonString: String): AgentMissionSession? {
    fun strings(obj: JSONObject, key: String): List<String> {
        val arr = obj.optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    }
    return try {
        val obj = JSONObject(jsonString)
        val stepsList = obj.optJSONArray("steps") ?: JSONArray()
        val steps = (0 until stepsList.length()).mapNotNull { idx ->
            when (val raw = stepsList.opt(idx)) {
                // الصيغة القديمة: معرف نصي فقط — تُستعاد بلا عنوان/وصف (توافق رجعي).
                is String -> AgentExecutionStep(
                    id = raw, title = "", description = "", toolName = "", outputSnippet = ""
                )
                is JSONObject -> AgentExecutionStep(
                    id = raw.optString("id"),
                    title = raw.optString("title"),
                    description = raw.optString("description"),
                    toolName = raw.optString("toolName"),
                    outputSnippet = raw.optString("outputSnippet"),
                    isRunning = raw.optBoolean("isRunning", false),
                    isSuccess = raw.optBoolean("isSuccess", true)
                )
                else -> null
            }
        }
        val diffsArr = obj.optJSONArray("proposedDiffs")
        val diffs = (0 until (diffsArr?.length() ?: 0)).mapNotNull { idx ->
            val d = diffsArr?.optJSONObject(idx) ?: return@mapNotNull null
            FileDiff(
                filePath = d.optString("filePath"),
                additions = d.optInt("additions"),
                deletions = d.optInt("deletions"),
                diffText = d.optString("diffText")
            )
        }
        AgentMissionSession(
            id = obj.optString("missionId", obj.optString("id", "")),
            userPrompt = obj.optString("userPrompt", ""),
            specialization = runCatching {
                AgentSpecialization.valueOf(obj.optString("specialization", "TURBO"))
            }.getOrDefault(AgentSpecialization.TURBO),
            status = runCatching {
                AgentTaskStatus.valueOf(obj.optString("status", "PLANNING"))
            }.getOrDefault(AgentTaskStatus.PLANNING),
            steps = steps,
            finalReport = obj.optString("finalReport", ""),
            modifiedFiles = strings(obj, "modifiedFiles"),
            proposedDiffs = diffs,
            appliedPaths = strings(obj, "appliedPaths"),
            buildTriggered = obj.optBoolean("buildTriggered", false),
            apkUrl = obj.optString("apkUrl").takeIf { it.isNotBlank() },
            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
        )
    } catch (_: Exception) {
        null
    }
}

@Composable
fun exportMissionJson(currentMission: AgentMissionSession?): String =
    currentMission?.let { encodeMissionSession(it) } ?: ""

fun importMissionFromJson(currentMission: MutableState<AgentMissionSession?>, jsonString: String): Boolean {
    val decoded = decodeMissionSession(jsonString) ?: return false
    currentMission.value = decoded
    return true
}

fun saveMissionState(currentMission: MutableState<AgentMissionSession?>, context: Context) {
    currentMission.value?.let { mission ->
        AgentPrefs.putString(context, AgentPrefs.ACTIVE_MISSION_JSON, encodeMissionSession(mission))
    }
}

fun restoreMissionState(currentMission: MutableState<AgentMissionSession?>, context: Context): AgentMissionSession? {
    val json = AgentPrefs.loadActiveMission(context)
    if (json.isBlank()) return null
    return decodeMissionSession(json)
}

// ═══════════════════════════════════════════════════════════════
// 🔑 Multi-Model Path: تنفيذ المهمة عبر فريق نماذج (قائد + مساعدين)
// ═══════════════════════════════════════════════════════════════

/**
 * ينفّذ المهمة باستخدام فريق متعدد النماذج (Multi-Model Team) بنمط «قائد + مساعدين».
 * يعيد AgentMissionSession متوافقة مع نظام الجلسة الحالي ليعمل الكود الحالي بلا تعديلات.
 */
suspend fun executeAgentMissionMultiModel(
    context: Context,
    userPrompt: String,
    specialization: AgentSpecialization,
    onUpdate: (AgentMissionSession) -> Unit = {},
    onStatusChange: (AgentTaskStatus) -> Unit = {},
    onTeamProgress: (String) -> Unit = {},
    buildFeedback: String = "",
    history: List<AgentChatMessage> = emptyList()
): AgentMissionSession {
    val team = MultiModelAgentOrchestrator.loadTeam(context, onlyEnabled = true)

    // 🔑 بثّ حيّ: نبني الجلسة خطوة بخطوة من أحداث الـOrchestrator لتظهر البطاقة
    // والأنميشن أثناء التنفيذ الفعلي (لا ننتظر الانتهاء).
    val stepsByKey = linkedMapOf<String, AgentExecutionStep>()
    var session = AgentMissionSession(
        userPrompt = userPrompt,
        specialization = specialization,
        status = AgentTaskStatus.PLANNING,
        steps = emptyList()
    )
    onStatusChange(AgentTaskStatus.PLANNING)
    onUpdate(session)

    fun emit() {
        session = session.copy(steps = stepsByKey.values.toList())
        onUpdate(session)
    }

    // 📒 سجل العمليات: كل نهاية مسار (نجاح/فشل/فريق فارغ) تُسجَّل — لا فشل صامت
    fun recordRun(s: AgentMissionSession) {
        val ok = s.status == AgentTaskStatus.COMPLETED
        val tools = s.steps.map { it.toolName }.filter { it.isNotBlank() }.distinct()
            .ifEmpty { listOf("team_orchestrator") }
        AgentRunLog.record(
            context = context,
            title = s.userPrompt.ifBlank { "مهمة فريق النماذج" },
            tools = tools,
            summary = s.finalReport.ifBlank { "انتهت مهمة الفريق بحالة: ${s.status.name}" },
            ok = ok,
            error = if (ok) "" else s.finalReport
        )
    }

    // 🔑 نتحقق فقط من مزود/مفتاح أعضاء الفريق الفعليين (لا المزود العالمي غير ذي الصلة)
    if (team.isEmpty()) {
        val failed = AgentMissionSession(
            id = session.id,
            userPrompt = userPrompt,
            specialization = specialization,
            status = AgentTaskStatus.FAILED,
            finalReport = "🚫 لا يوجد فريق مفعّل! افتح '👥 فريق النماذج' وقم بتعيين قائد ومساعد واحد على الأقل."
        )
        onStatusChange(AgentTaskStatus.FAILED)
        onUpdate(failed)
        recordRun(failed)
        return failed
    }

    // تشغيل الـ Orchestrator (يُدير القائد والمساعدين) — يحل مفتاح كل مزود داخليًا
    val report = MultiModelAgentOrchestrator.executeWithTeam(
        context = context,
        prompt = userPrompt,
        team = team,
        onUpdate = onTeamProgress,
        buildFeedback = buildFeedback,
        history = history,
        onStep = { ev ->            val prev = stepsByKey[ev.key]
            stepsByKey[ev.key] = AgentExecutionStep(
                id = prev?.id ?: ev.key,
                title = ev.title,
                description = ev.description,
                toolName = ev.toolName,
                outputSnippet = ev.outputSnippet,
                isRunning = ev.isRunning,
                isSuccess = ev.isSuccess,
                timestamp = prev?.timestamp ?: System.currentTimeMillis()
            )
            // تحديث حالة المهمة الحيّة بحسب طور الخطوة
            val liveStatus = when {
                ev.isRunning && ev.toolName == "team_leader_plan" -> AgentTaskStatus.PLANNING
                ev.isRunning && ev.toolName.startsWith("team_member") -> AgentTaskStatus.WRITING
                ev.isRunning -> session.status
                else -> session.status
            }
            if (liveStatus != session.status) {
                session = session.copy(status = liveStatus)
                onStatusChange(liveStatus)
            }
            emit()
        }
    )

    if (!report.overallSuccess) {
        onStatusChange(AgentTaskStatus.FAILED)
        // نموذج المطلوبات أولاً: إن تعثر البدء يُعرض ما ينقص بدل خطأ مبهم
        if (!report.preflight.ready && (report.preflight.missing.isNotEmpty() || report.preflight.questions.isNotEmpty())) {
            val failed = session.copy(
                status = AgentTaskStatus.FAILED,
                steps = stepsByKey.values.toList(),
                finalReport = "📋 تعذّر بدء العمل — المطلوبات:\n" +
                    report.preflight.missing.joinToString("\n") { "• $it" } +
                    report.preflight.questions.joinToString("\n") { "• $it" } +
                    "\n\nاستوفِ ما أعلاه ثم أعد التنفيذ."
            )
            onUpdate(failed)
            recordRun(failed)
            return failed
        }
        val errorText = report.memberResults.firstOrNull { !it.isSuccess && it.errorMessage != null }?.errorMessage ?: "فشل غير معروف"
        val failed = session.copy(
            status = AgentTaskStatus.FAILED,
            steps = stepsByKey.values.toList(),
            finalReport = "❌ فشل فريق النماذج في إكمال المهمة.\n\nأول خطأ: $errorText\n\nتحذيرات: ${report.warnings.joinToString("، ")}"
        )
        onUpdate(failed)
        recordRun(failed)
        return failed
    }

    // 🔑 الخريطة الحيّة تحتوي مسبقاً: preflight + خطة القائد + كل مساعد + الخلاصة
    // (بُثّت أثناء التنفيذ) — لا نبنيها مجدداً حتى لا تتكرر الخطوات.

    onStatusChange(AgentTaskStatus.COMPLETED)

    // الدفع حكر على القائد وبإذنك: مقترحاته فقط تظهر للاعتماد، ومخرجات
    // المساعدين نتائج تُسلَّم للقائد ولا تُدفع — لا دفع صامت أبداً.
    val pushNote = when {
        report.pushRequested && report.leaderDiffs.isNotEmpty() ->
            "\n\n👑 القائد يطلب إذنك بالدفع لـ ${report.leaderDiffs.size} ملفات: " +
                report.leaderDiffs.joinToString("، ") { it.filePath.substringAfterLast("/") } +
                " — راجع المقترحات أدناه واعتمد أو تجاهل."
        report.pushRequested ->
            "\n\n👑 القائد طلب الدفع لكن لم تُستخرج ملفات صالحة من خلاصته — لا يوجد ما يُدفع."
        else -> ""
    }

    // 🔁 إن حدثت إعادة بعد التحقق، فالبناء السابق لم يعد مرجعاً صالحاً للملفات المُعدَّلة
    val didRework = stepsByKey.keys.any { it.startsWith("rework_") }
    val reworkNote = if (didRework && report.leaderDiffs.isNotEmpty()) {
        if (stepsByKey["verify_redo"]?.isSuccess == false) {
            "\n\n⚠️ أُعيد تنفيذ بعض المهام وما زالت دون معايير القبول حتى بعد إعادة التحقق — راجع بعناية قبل الدفع."
        } else {
            "\n\n🔁 أُعيد تنفيذ بعض المهام بعد التحقق — بعد اعتماد الدفع، أعد تشغيل البناء وأعد فحص البوابة للتأكد من نجاح CI على النسخة المُعدَّلة."
        }
    } else ""

    val finalSession = session.copy(
        status = AgentTaskStatus.COMPLETED,
        steps = stepsByKey.values.toList(),
        finalReport = report.leaderSynthesis.ifBlank {
            "تم إكمال المهمة عبر ${report.memberResults.size} مشارك بنجاح.\n\nمخرجات الفريق:\n" +
                    report.memberResults.joinToString("\n\n") { "[${it.role.label}] ${it.outputText.take(300)}" }
        } + pushNote + reworkNote,
        modifiedFiles = report.leaderDiffs.map { it.filePath },
        proposedDiffs = report.leaderDiffs,
        buildTriggered = false
    )
    session = finalSession
    onUpdate(finalSession)
    recordRun(finalSession)
    return finalSession
}

/**
 * 🔗 بوابة البناء: تجلب نتيجة بناء الفرع المثبّتة على الكوميت المدفوع.
 * تُميّز PENDING/PASSED/FAILED/UNKNOWN — فلا إعادة إصلاح على بناء قديم أو لم يبدأ،
 * وعند الفشل فقط تحمل `feedback` سجلًا منقّحًا جاهزًا لبرومبت الفريق.
 */
suspend fun fetchTeamBuildFeedback(context: Context): BuildVerificationEngine.BuildGateResult {
    val repo = RepoVaultManager.getActiveRepo(context)
        ?: return BuildVerificationEngine.BuildGateResult(BuildVerificationEngine.BuildGateState.UNKNOWN)
    if (repo.token.isBlank()) {
        return BuildVerificationEngine.BuildGateResult(BuildVerificationEngine.BuildGateState.UNKNOWN)
    }
    return BuildVerificationEngine.evaluateBuildGate(
        repo.owner, repo.repo, repo.token, repo.defaultBranch
    )
}
