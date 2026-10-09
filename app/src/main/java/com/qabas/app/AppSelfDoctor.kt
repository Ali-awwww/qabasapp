package com.qabas.app

import android.content.Context
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

data class DoctorReport(
    val id: String = UUID.randomUUID().toString(),
    val severity: String,          // "حرج" | "مهم" | "تحسين" | "معلومة"
    val title: String,
    val message: String,
    val category: String,          // "مفاتيح API" | "خدمات" | "واجهة" | "أداء" | "تكامل" | "أمان"
    val suggestedAction: String,
    val suggestedFixCode: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * نتيجة اختبار اتصال حقيقي للخدمة (وليس مجرد وجود نص المفتاح).
 * الصدق أولاً: نفرّق بين "مفتاح مرفوض" و"الشبكة غير متاحة" ولا نتهم أحدهما بذنب الآخر.
 */
data class LiveCheck(
    val ok: Boolean,
    val rejected: Boolean,      // الخدمة نفسها رفضت المفتاح (منتهٍ/خاطئ)
    val unreachable: Boolean   // تعذر الوصول للخدمة (شبكة/مهلة)
)

object AppSelfDoctor {

    private const val HISTORY_FILE = "doctor_history.json"
    private const val HISTORY_LIMIT = 50

    private val history = mutableListOf<DoctorReport>()
    private var historyLoaded = false

    /** عميل فحص سريع — مهل قصيرة حتى لا يعلّق الطبيب الواجهة */
    private val healthClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    fun getHistory(): List<DoctorReport> {
        loadHistoryIfNeeded()
        return history.sortedByDescending { it.timestamp }
    }

    // ---------------------------------------------------------------
    // اختبارات الاتصال الحقيقية — كل طلب قراءة بيانات فقط (بلا تكلفة رموز)
    // ---------------------------------------------------------------

    private suspend fun httpCheck(request: Request): LiveCheck = withContext(Dispatchers.IO) {
        try {
            healthClient.newCall(request).execute().use { resp ->
                when {
                    resp.code in 200..299 -> LiveCheck(ok = true, rejected = false, unreachable = false)
                    resp.code == 401 || resp.code == 403 || resp.code == 400 ->
                        LiveCheck(ok = false, rejected = true, unreachable = false)
                    else -> LiveCheck(ok = false, rejected = false, unreachable = false)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            LiveCheck(ok = false, rejected = false, unreachable = true)
        }
    }

    private suspend fun geminiCheck(key: String) = withTimeoutOrNull(12_000) {
        httpCheck(
            Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?key=$key&pageSize=1")
                .build()
        )
    } ?: LiveCheck(ok = false, rejected = false, unreachable = true)

    private suspend fun groqCheck(key: String) = withTimeoutOrNull(12_000) {
        val url = if (key.startsWith("xai-", ignoreCase = true)) "https://api.x.ai/v1/models"
        else "https://api.groq.com/openai/v1/models"
        httpCheck(
            Request.Builder().url(url).header("Authorization", "Bearer $key").build()
        )
    } ?: LiveCheck(ok = false, rejected = false, unreachable = true)

    private suspend fun azureCheck(key: String, region: String) = withTimeoutOrNull(12_000) {
        httpCheck(
            Request.Builder()
                .url("https://$region.tts.speech.microsoft.com/cognitiveservices/voices/list")
                .header("Ocp-Apim-Subscription-Key", key)
                .build()
        )
    } ?: LiveCheck(ok = false, rejected = false, unreachable = true)

    private suspend fun elevenLabsCheck(key: String) = withTimeoutOrNull(12_000) {
        httpCheck(Request.Builder().url("https://api.elevenlabs.io/v1/user").header("xi-api-key", key).build())
    } ?: LiveCheck(ok = false, rejected = false, unreachable = true)

    private suspend fun huggingFaceCheck(key: String) = withTimeoutOrNull(12_000) {
        httpCheck(
            Request.Builder().url("https://huggingface.co/api/whoami-v2")
                .header("Authorization", "Bearer $key").build()
        )
    } ?: LiveCheck(ok = false, rejected = false, unreachable = true)

    private suspend fun pexelsCheck(key: String) = withTimeoutOrNull(12_000) {
        httpCheck(
            Request.Builder()
                .url("https://api.pexels.com/videos/search?query=nature&per_page=1")
                .header("Authorization", key)
                .build()
        )
    } ?: LiveCheck(ok = false, rejected = false, unreachable = true)

    private suspend fun pixabayCheck(key: String) = withTimeoutOrNull(12_000) {
        httpCheck(
            Request.Builder()
                .url("https://pixabay.com/api/?key=$key&q=mosque&per_page=3&image_type=photo")
                .build()
        )
    } ?: LiveCheck(ok = false, rejected = false, unreachable = true)

    private suspend fun supabaseCheck(url: String, key: String) = withTimeoutOrNull(12_000) {
        httpCheck(
            Request.Builder()
                .url("${url.trimEnd('/')}/rest/v1/")
                .header("apikey", key)
                .header("Authorization", "Bearer $key")
                .build()
        )
    } ?: LiveCheck(ok = false, rejected = false, unreachable = true)

    private suspend fun higgsfieldCheck(key: String) = withTimeoutOrNull(12_000) {
        httpCheck(
            Request.Builder()
                .url("https://api.higgsfield.ai/v1/models")
                .header("Authorization", "Bearer $key")
                .build()
        )
    } ?: LiveCheck(ok = false, rejected = false, unreachable = true)

    /** يبني تقريراً صادقاً بناءً على اختبار حقيقي — وجود المفتاح وحده لا يكفي */
    private fun liveStatusReport(
        serviceName: String,
        category: String,
        check: LiveCheck,
        onOk: String,
        actionOk: String
    ): DoctorReport = when {
        check.ok -> DoctorReport(
            severity = "معلومة",
            title = "$serviceName متصل ويعمل — تم التحقق فعلياً ✦",
            message = onOk,
            category = category,
            suggestedAction = actionOk
        )
        check.rejected -> DoctorReport(
            severity = "حرج",
            title = "خدمة $serviceName رفضت المفتاح",
            message = "المفتاح موجود لكن الخدمة ردّت برفض الاعتماد (منتهٍ الصلاحية أو خاطئ). الميزات المعتمدة عليه ستعمل بالبديل المحلي الصادق.",
            category = category,
            suggestedAction = "أعد توليد المفتاح من مزود $serviceName وحدّثه في شاشة مفاتيح API أو GitHub Secrets."
        )
        check.unreachable -> DoctorReport(
            severity = "تحسين",
            title = "تعذر الوصول لخدمة $serviceName (شبكة)",
            message = "المفتاح موجود لكن لم يمكن التحقق من الخدمة الآن — انقطاع شبكة أو جدار حماية. لا يُعرف من هذا إن كان المفتاح صالحاً.",
            category = category,
            suggestedAction = "أعد الفحص عند توفر الإنترنت."
        )
        else -> DoctorReport(
            severity = "تحسين",
            title = "خدمة $serviceName ردّت باستجابة غير متوقعة",
            message = "الخدمة متاحة لكنها لم تؤكد صحة المفتاح بشكل قاطع — أعد الفحص لاحقاً.",
            category = category,
            suggestedAction = "راقب السجلات، وأعد التشخيص إن تكررت."
        )
    }

    // ---------------------------------------------------------------
    // التشخيص الشامل
    // ---------------------------------------------------------------

    suspend fun runFullDiagnosis(context: Context): List<DoctorReport> {
        val reports = mutableListOf<DoctorReport>()
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)

        fun isKeyValid(key: String): Boolean {
            val trimmed = key.trim()
            return trimmed.isNotBlank() &&
                trimmed != "YOUR_GEMINI_API_KEY" &&
                !trimmed.equals("placeholder", ignoreCase = true) &&
                !trimmed.startsWith("YOUR_") &&
                !trimmed.lowercase().startsWith("your_")
        }

        // المفاتيح الفعّالة عبر KeyVault (شاشة المفاتيح أولاً ثم BuildConfig من Secrets)
        val geminiKey = KeyVault.gemini
        val groqKey = KeyVault.groq
        val elevenLabsKey = KeyVault.elevenlabs
        val azureKey = KeyVault.azureSpeechKey
        val azureRegion = KeyVault.azureSpeechRegion.ifBlank { "eastus" }
        val huggingFaceKey = KeyVault.huggingface
        val pexelsKey = KeyVault.pexels
        val pixabayKey = KeyVault.pixabay
        val higgsfieldKey = KeyVault.higgsfield

        // 1) فحوص الاتصال الحقيقية بالتوازي — طلب واحد خفيف لكل خدمة
        val liveResults = mutableMapOf<String, LiveCheck>()
        coroutineScope {
            val jobs = mutableListOf<kotlinx.coroutines.Deferred<Pair<String, LiveCheck>>>()
            if (isKeyValid(geminiKey))
                jobs += async { "gemini" to geminiCheck(geminiKey) }
            if (isKeyValid(groqKey))
                jobs += async { "groq" to groqCheck(groqKey) }
            if (isKeyValid(azureKey))
                jobs += async { "azure" to azureCheck(azureKey, azureRegion) }
            if (isKeyValid(elevenLabsKey))
                jobs += async { "elevenlabs" to elevenLabsCheck(elevenLabsKey) }
            if (isKeyValid(huggingFaceKey))
                jobs += async { "huggingface" to huggingFaceCheck(huggingFaceKey) }
            if (isKeyValid(pexelsKey))
                jobs += async { "pexels" to pexelsCheck(pexelsKey) }
            if (isKeyValid(pixabayKey))
                jobs += async { "pixabay" to pixabayCheck(pixabayKey) }
            if (isKeyValid(higgsfieldKey))
                jobs += async { "higgsfield" to higgsfieldCheck(higgsfieldKey) }
            if (SupabaseConfig.isConfigured)
                jobs += async { "supabase" to supabaseCheck(SupabaseConfig.url, SupabaseConfig.key) }
            jobs.awaitAll().forEach { (name, check) -> liveResults[name] = check }
        }

        // 2) تقارير المفاتيح — وجود + اختبار حقيقي
        if (!isKeyValid(geminiKey)) {
            reports.add(
                DoctorReport(
                    severity = "حرج",
                    title = "محرك الذكاء الاصطناعي معطل (Gemini)",
                    message = "لا يوجد مفتاح Gemini فعال (لا من شاشة المفاتيح ولا من GitHub Secrets). التطبيق يعمل بالتحليل المحلي البديل — بلا بيانات وهمية، لكن جودة الصياغة والتحليل أقل.",
                    category = "مفاتيح API",
                    suggestedAction = "أضف مفتاح Gemini API المجاني من Google AI Studio في شاشة المفاتيح أو في GitHub Secrets (GEMINI_API_KEY).",
                    suggestedFixCode = """
// لإضافة المفتاح برمجياً:
val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
prefs.edit().putString("gemini_key", "YOUR_ACTUAL_KEY_HERE").apply()
                    """.trimIndent()
                )
            )
        } else {
            reports.add(
                liveStatusReport(
                    serviceName = "Gemini",
                    category = "مفاتيح API",
                    check = liveResults["gemini"] ?: LiveCheck(false, false, true),
                    onOk = "محرك الذكاء الاصطناعي الأساسي يستجيب فعلياً الآن — جاهز لصياغة المحتوى وتحليل الأفكار.",
                    actionOk = "جاهز للإنتاج وصناعة المحتوى."
                )
            )
        }

        if (!isKeyValid(groqKey)) {
            reports.add(
                DoctorReport(
                    severity = "تحسين",
                    title = "تحسين سرعة الاستجابة (Groq)",
                    message = "مفتاح Groq غير متوفر. يتم الاعتماد حالياً على Gemini في كل العمليات. إضافة Groq ستسرع توليد الخطافات والوسوم الفورية (~300ms).",
                    category = "أداء",
                    suggestedAction = "أضف مفتاح Groq المجاني لفتح مزايا السرعة الفائقة.",
                    suggestedFixCode = null
                )
            )
        } else {
            reports.add(
                liveStatusReport(
                    serviceName = "Groq",
                    category = "أداء",
                    check = liveResults["groq"] ?: LiveCheck(false, false, true),
                    onOk = "محرك Groq يستجيب فعلياً — توليد الخطافات والوسوم بالسرعة الفائقة متاح.",
                    actionOk = "استفد من Groq في الميزات الفورية."
                )
            )
        }

        if (!isKeyValid(elevenLabsKey) && !isKeyValid(azureKey)) {
            reports.add(
                DoctorReport(
                    severity = "مهم",
                    title = "التعليق الصوتي العصبي غير مفعل",
                    message = "لا يوجد أي محرك صوتي سحابي (Azure Speech أو ElevenLabs) مفعل. يتم استخدام محرك الصوت المحلي الافتراضي (TTS أندرويد) — يعمل بلا مفاتيح لكن بجودة أقل.",
                    category = "خدمات",
                    suggestedAction = "أضف مفتاح Azure Speech (نوصي به للفصحى) أو ElevenLabs في إعدادات التطبيق أو GitHub Secrets.",
                    suggestedFixCode = null
                )
            )
        } else {
            if (isKeyValid(azureKey)) {
                reports.add(
                    liveStatusReport(
                        serviceName = "Azure Speech",
                        category = "خدمات",
                        check = liveResults["azure"] ?: LiveCheck(false, false, true),
                        onOk = "خدمة الصوت العصبي من Azure تستجيب فعلياً — التعليق العربي الفصيح متاح.",
                        actionOk = "جاهز لإنتاج التعليق الصوتي."
                    )
                )
            }
            if (isKeyValid(elevenLabsKey)) {
                reports.add(
                    liveStatusReport(
                        serviceName = "ElevenLabs",
                        category = "خدمات",
                        check = liveResults["elevenlabs"] ?: LiveCheck(false, false, true),
                        onOk = "خدمة ElevenLabs تستجيب فعلياً — الأصوات الجبارة متاحة.",
                        actionOk = "يمكن اختيار أصوات ElevenLabs في إعدادات الصوت."
                    )
                )
            }
        }

        if (!isKeyValid(pexelsKey) && !isKeyValid(pixabayKey)) {
            reports.add(
                DoctorReport(
                    severity = "مهم",
                    title = "مكتبة الوسائط الحية متوقفة",
                    message = "لم يتم إضافة مفاتيح Pexels أو Pixabay. التطبيق يعتمد على الإطارات السينمائية المحلية (المولدة بعنوان كل مشهد) بدل مقاطع B-Roll حقيقية.",
                    category = "تكامل",
                    suggestedAction = "أضف مفاتيح Pexels أو Pixabay المجانية لتمكين جلب مقاطع B-Roll وصور المعالم الإسلامية بجودة HD.",
                    suggestedFixCode = null
                )
            )
        } else {
            if (isKeyValid(pexelsKey)) {
                reports.add(
                    liveStatusReport(
                        serviceName = "Pexels",
                        category = "تكامل",
                        check = liveResults["pexels"] ?: LiveCheck(false, false, true),
                        onOk = "مكتبة Pexels تستجيب فعلياً — جلب B-Roll حقيقي متاح.",
                        actionOk = "جاهز لجلب الوسائط."
                    )
                )
            }
            if (isKeyValid(pixabayKey)) {
                reports.add(
                    liveStatusReport(
                        serviceName = "Pixabay",
                        category = "تكامل",
                        check = liveResults["pixabay"] ?: LiveCheck(false, false, true),
                        onOk = "مكتبة Pixabay تستجيب فعلياً — جلب الصور والمؤثرات متاح.",
                        actionOk = "جاهز لجلب الوسائط."
                    )
                )
            }
        }

        if (isKeyValid(huggingFaceKey)) {
            reports.add(
                liveStatusReport(
                    serviceName = "HuggingFace",
                    category = "تكامل",
                    check = liveResults["huggingface"] ?: LiveCheck(false, false, true),
                    onOk = "توليد الصور الذكي من HuggingFace يستجيب فعلياً.",
                    actionOk = "جاهز لتوليد الخلفيات."
                )
            )
        }

        if (isKeyValid(higgsfieldKey)) {
            reports.add(
                liveStatusReport(
                    serviceName = "Higgsfield AI Video",
                    category = "خدمات",
                    check = liveResults["higgsfield"] ?: LiveCheck(false, false, true),
                    onOk = "محرك توليد الفيديو السينمائي الفائق وتحريك الصور من Higgsfield AI يستجيب فعلياً.",
                    actionOk = "جاهز لإنتاج الفيديو الذكي والتحريك السينمائي."
                )
            )
        }

        // 3) بيئة الإنتاج — مساحة التخزين والذاكرة (شروط فيديو حقيقية)
        try {
            val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: File(context.filesDir, "movies")
            val stat = StatFs(moviesDir.absolutePath)
            val freeGB = stat.availableBytes / (1024.0 * 1024.0 * 1024.0)
            reports.add(
                when {
                    freeGB < 0.5 -> DoctorReport(
                        severity = "حرج",
                        title = "مساحة التخزين شبه منتهية (${String.format("%.1f", freeGB)} GB)",
                        message = "إنتاج الفيديو يستهلك مساحة مؤقتة كبيرة قبل التصدير — المساحة الحالية قد تفشل التصدير في منتصف الطريق.",
                        category = "أداء",
                        suggestedAction = "احذف ملفات لا تحتاجها من مجلد أفلام قبس أو حرر مساحة من الجهاز.",
                        suggestedFixCode = null
                    )
                    freeGB < 2.0 -> DoctorReport(
                        severity = "مهم",
                        title = "مساحة التخزين منخفضة (${String.format("%.1f", freeGB)} GB)",
                        message = "تكفي لبعض المشاريع القصيرة، لكن المشاريع الطويلة أو عالية الجودة قد تحتاج أكثر.",
                        category = "أداء",
                        suggestedAction = "راجع مساحتك قبل إنتاج مقاطع طويلة.",
                        suggestedFixCode = null
                    )
                    else -> DoctorReport(
                        severity = "معلومة",
                        title = "مساحة التخزين كافية (${String.format("%.1f", freeGB)} GB)",
                        message = "المساحة المتاحة تكفي لإنتاج المشاريع الحالية بأمان.",
                        category = "أداء",
                        suggestedAction = "لا إجراء مطلوب.",
                        suggestedFixCode = null
                    )
                }
            )
        } catch (_: Exception) {
            // StatFs غير متاح على بعض الأنظمة القديمة — نتجاهل بصمت
        }

        try {
            val device = DevicePerformanceGuardian.inspectDevice(context)
            if (device.isLowEnd) {
                reports.add(
                    DoctorReport(
                        severity = "تحسين",
                        title = "وضع الأداء المضمون مفعّل تلقائياً",
                        message = "الجهاز (${device.reason}) — سيتم استخدام مسار الإنتاج المضمون: دمج مباشر بلا انتقالات ثقيلة + صوت TTS أندرويد المحلي (بدون شبكة). النتيجة أقل فخامة لكنها أسرع وأثبت نجاحاً.",
                        category = "أداء",
                        suggestedAction = "لا إجراء مطلوب — هذا اختيار صادق لجهازك.",
                        suggestedFixCode = null
                    )
                )
            }
        } catch (_: Exception) {
        }

        // 4) المزامنة السحابية — Supabase
        if (SupabaseConfig.isConfigured) {
            reports.add(
                liveStatusReport(
                    serviceName = "Supabase",
                    category = "أمان",
                    check = liveResults["supabase"] ?: LiveCheck(false, false, true),
                    onOk = "المزامنة السحابية متصلة فعلياً — المشاريع والحسابات محفوظة على السحابة.",
                    actionOk = "المزامنة السحابية جاهزة."
                )
            )
        } else {
            reports.add(
                DoctorReport(
                    severity = "تحسين",
                    title = "المزامنة السحابية غير مفعلة (حفظ محلي)",
                    message = "SUPABASE_URL وSUPABASE_ANON_KEY غير مضبوطين. يتم حفظ جميع المشاريع محلياً وبأمان داخل جهازك.",
                    category = "أمان",
                    suggestedAction = "أضف SUPABASE_URL وSUPABASE_ANON_KEY في GitHub Secrets لتفعيل المزامنة السحابية.",
                    suggestedFixCode = null
                )
            )
        }

        // 5) الجاهزية التشغيلية
        val readiness = OperationalReadinessManager.calculateReadiness(context)
        if (readiness.percentage < 50) {
            reports.add(
                DoctorReport(
                    severity = "حرج",
                    title = "الجاهزية التشغيلية منخفضة (${readiness.percentage}%)",
                    message = "أغلب الخدمات السحابية غير موصولة — التطبيق يعمل بالبدائل المحلية الصادقة (بلا بيانات وهمية)، لكن بجودة أقل.",
                    category = "خدمات",
                    suggestedAction = "راجع شاشة مفاتيح API وأضف المفاتيح الناقصة لتشغيل الخدمات السحابية.",
                    suggestedFixCode = null
                )
            )
        } else if (readiness.percentage < 80) {
            reports.add(
                DoctorReport(
                    severity = "تحسين",
                    title = "الجاهزية التشغيلية متوسطة (${readiness.percentage}%)",
                    message = "أغلب الأنظمة الحيوية تعمل، مع بعض الخدمات التي يمكن تحسينها.",
                    category = "خدمات",
                    suggestedAction = "أكمل إعداد المفاتيح المتبقية لرفع الجاهزية.",
                    suggestedFixCode = null
                )
            )
        }

        // 6) أخطاء النظام الأخيرة
        val lastError = OperationalReadinessManager.getLastError()
        if (lastError != null) {
            reports.add(
                DoctorReport(
                    severity = "مهم",
                    title = "أخطاء مسجلة مؤخراً في النظام",
                    message = "تم رصد أخيرة في سجلات النظام (Logs): $lastError. هذا قد يشير إلى انهيار خدمة معينة أو مشكلة اتصال.",
                    category = "أداء",
                    suggestedAction = "راجع شاشة 'سجلات النظام' لمعرفة تفاصيل الخطأ ومعالجته.",
                    suggestedFixCode = null
                )
            )
        }

        // رسالة إغلاق صادقة
        if (reports.none { it.severity == "حرج" }) {
            reports.add(
                DoctorReport(
                    severity = "معلومة",
                    title = "التشخيص الشامل مكتمل",
                    message = "فحص المفاتيح تم باختبار اتصال حقيقي لكل خدمة (وليس مجرد وجود النص). لا مشاكل حرجة الآن.",
                    category = "تكامل",
                    suggestedAction = "استمر في مراقبة الأداء وتحديث المفاتيح عند الحاجة.",
                    suggestedFixCode = null
                )
            )
        }

        // إشعار عند وجود مشكلة حرجة
        if (reports.any { it.severity == "حرج" }) {
            val criticalCount = reports.count { it.severity == "حرج" }
            try {
                NotificationHelper.showNotification(
                    context = context,
                    title = "طبيب التطبيق 🩺",
                    message = "تم اكتشاف $criticalCount مشكلة حرجة تحتاج انتباهك. راجع لوحة المطور."
                )
            } catch (_: Exception) {
            }
        }

        // حفظ السجل (يصرّف على القرص — يبقى بعد إغلاق التطبيق)
        loadHistoryIfNeeded()
        synchronized(history) {
            reports.forEach { history.add(0, it) }
            if (history.size > HISTORY_LIMIT) {
                history.subList(HISTORY_LIMIT, history.size).clear()
            }
            persistHistory(context)
        }

        return sortReports(reports)
    }

    private fun sortReports(reports: List<DoctorReport>): List<DoctorReport> {
        val severityWeight = mapOf("حرج" to 4, "مهم" to 3, "تحسين" to 2, "معلومة" to 1)
        return reports.sortedByDescending { severityWeight[it.severity] ?: 0 }
    }

    // ---------------------------------------------------------------
    // استمرارية السجل — JSON على القرص بدل الذاكرة المؤقتة فقط
    // ---------------------------------------------------------------

    private fun historyFile(context: Context): File = File(context.filesDir, HISTORY_FILE)

    private fun loadHistoryIfNeeded() {
        if (historyLoaded) return
        synchronized(history) {
            if (historyLoaded) return
            try {
                val ctx = AppServices.appContext
                val file = historyFile(ctx)
                if (file.exists()) {
                    val arr = JSONArray(file.readText())
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        history.add(
                            DoctorReport(
                                id = o.optString("id", UUID.randomUUID().toString()),
                                severity = o.optString("severity", "معلومة"),
                                title = o.optString("title", ""),
                                message = o.optString("message", ""),
                                category = o.optString("category", ""),
                                suggestedAction = o.optString("suggestedAction", ""),
                                suggestedFixCode = if (o.has("suggestedFixCode") && !o.isNull("suggestedFixCode")) o.getString("suggestedFixCode") else null,
                                timestamp = o.optLong("timestamp", System.currentTimeMillis())
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                // سجل تالف = نبدأ من جديد بلا إنهيار
            }
            historyLoaded = true
        }
    }

    private fun persistHistory(context: Context) {
        try {
            val arr = JSONArray()
            history.take(HISTORY_LIMIT).forEach { r ->
                arr.put(
                    JSONObject()
                        .put("id", r.id)
                        .put("severity", r.severity)
                        .put("title", r.title)
                        .put("message", r.message)
                        .put("category", r.category)
                        .put("suggestedAction", r.suggestedAction)
                        .put("timestamp", r.timestamp)
                        .put("suggestedFixCode", r.suggestedFixCode ?: JSONObject.NULL)
                )
            }
            historyFile(context).writeText(arr.toString())
        } catch (_: Exception) {
            // تعذر الحفظ لا يفسد التشخيص
        }
    }
}
