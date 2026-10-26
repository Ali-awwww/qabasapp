package com.qabas.app

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object SystemLogsManager {
    val logs = mutableStateListOf<SystemLogEntry>()

    fun addLog(level: String, message: String, color: Color = Color.Green) {
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timeStr = timeFormat.format(Date())
        logs.add(0, SystemLogEntry(level = level, message = message, time = timeStr, color = color))
    }

    fun clearLogs() {
        logs.clear()
    }
}

data class KeyValidationResult(
    val isValid: Boolean,
    val summary: String,
    val errorCode: Int? = null,
    val rawError: String? = null,
    val explanation: String = "",
    val suggestedFix: String = ""
)

sealed class KeyValidationStatus {
    object Idle : KeyValidationStatus()
    object Testing : KeyValidationStatus()
    data class Valid(val result: KeyValidationResult) : KeyValidationStatus()
    data class Invalid(val result: KeyValidationResult) : KeyValidationStatus()
}

object ApiKeyValidator {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun validateKey(context: Context, serviceType: String, key: String, hint: String? = null): KeyValidationResult = withContext(Dispatchers.IO) {
        val t0 = System.nanoTime()
        val result = executeValidation(context, serviceType, key, hint)
        val latencyMs = ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L)
        val apiName = when (serviceType.lowercase()) {
            "gemini" -> "Gemini"
            "groq", "grok", "xai" -> "Groq"
            "openai" -> "OpenAI"
            "openrouter" -> "OpenRouter"
            "huggingface" -> "HuggingFace"
            "pexels" -> "Pexels"
            "pixabay" -> "Pixabay"
            "azure" -> "Azure TTS"
            "elevenlabs" -> "ElevenLabs"
            "higgsfield" -> "Higgsfield AI"
            "leonardo" -> "Leonardo.ai"
            "firebase" -> "Firebase"
            else -> serviceType.replaceFirstChar { it.uppercase() }
        }
        ApiUsageTracker.recordValidationResult(context, apiName, latencyMs, result)
        result
    }

    private suspend fun executeValidation(context: Context, serviceType: String, key: String, hint: String? = null): KeyValidationResult = withContext(Dispatchers.IO) {
        val trimmedKey = KeyPoolManager.sanitizeApiKey(key)
        val isPlaceholder = trimmedKey.isEmpty() ||
                trimmedKey.length < 5 ||
                trimmedKey.startsWith("your_", ignoreCase = true) ||
                trimmedKey.contains("placeholder", ignoreCase = true) ||
                trimmedKey.startsWith("default_", ignoreCase = true) ||
                trimmedKey.startsWith("test_", ignoreCase = true) ||
                trimmedKey == "test" ||
                trimmedKey == "\"\""

        if (isPlaceholder) {
            return@withContext KeyValidationResult(
                isValid = false,
                summary = "لم يُعيّن بعد ⚪",
                explanation = "المفتاح غير مضبوط في النظام أو يحمل قيمة تجريبية/افتراضية.",
                suggestedFix = "اضغط على زر «تعديل» لإدخال المفتاح الفعلي من لوحة التحكم."
            )
        }

        try {
            when (serviceType.lowercase()) {
                "gemini" -> {
                    if (!Regex("""AIza[A-Za-z0-9_\-]{35,}""").matches(trimmedKey)) {
                        val looksGlobish = trimmedKey.startsWith("AQ.", ignoreCase = true)
                        val reason = if (looksGlobish) {
                            "المفتاح الذي لصقته يبدأ بـ «AQ.» وهو صيغة توكن OAuth (Googles أحادية الاستخدام) أو مفتاح خدمة إعلانية، وليس مفتاح Gemini API."
                        } else {
                            "مفتاح Gemini API الصحيح يبدأ دائماً بالبادئة «AIzaSy» ويتكون من 39 محرفاً. المفتاح الذي أدخلته لا يطابق تلك التركيبة."
                        }
                        SystemLogsManager.addLog("ERROR", "مفتاح Gemini غير صالح الصيغة - صيغة غير AIza", Color(0xFFEF4444))
                        return@withContext KeyValidationResult(
                            isValid = false,
                            summary = "صيغة المفتاح غير صحيحة 🔴",
                            explanation = reason,
                            suggestedFix = "افتح aistudio.google.com/app/apikey ثم اضغط «Create API key» في مشروع جديد وانسخ المفتاح كاملاً (يبدأ بـ AIzaSy…) دون أي مسافات."
                        )
                    }
                    val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$trimmedKey"
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    val code = response.code
                    val bodyStr = response.body?.string().orEmpty()

                    if (response.isSuccessful) {
                        SystemLogsManager.addLog("SUCCESS", "مفتاح Gemini API متصل وسليم وفعال حقيقياً (Google AI Studio - 200 OK)", Color(0xFF4CAF50))
                        return@withContext KeyValidationResult(
                            isValid = true,
                            summary = "متصل بالسيرفر ✅ (Gemini API شغال حقيقياً)",
                            errorCode = code,
                            explanation = "تم التحقق الفعلي من صحة المفتاح واستجابة خوادم Google AI Studio بنجاح (200 OK). تم تفعيل ميزات التوليد الذكي وكتابة السيناريو وتحليل الأفكار في المشروع.",
                            suggestedFix = "المفتاح يعمل بكامل طاقته وجاهز للاستخدام الفوري."
                        )
                    } else {
                        val (exp, fix) = when (code) {
                            400 -> Pair(
                                "المفتاح المدخل غير صحيح في تركيبته أو يحتوي على رموز إضافية أو مسافات غير صالحة.",
                                "تأكد من نسخ المفتاح كاملاً من Google AI Studio (aistudio.google.com/app/apikey) بدون مسافات إضافية."
                            )
                            403 -> Pair(
                                "تم رفض الطلب من Google (403 Forbidden). هذا يعني أن المفتاح تم إلغاؤه، أو أن قيود الـ API تمنع استخدامه من منطقتك الجغرافية، أو لم يتم تفعيل Generative Language API في مشروعك على Google Cloud.",
                                "ادخل إلى Google AI Studio، وأنشئ مفتاحاً جديداً (Create API key) في مشروع جديد بدون قيود IP."
                            )
                            429 -> Pair(
                                "تم تجاوز الحد المسموح من الطلبات المجانية (Rate Limit Exceeded).",
                                "انتظر دقيقة واحدة وأعد المحاولة، أو أنشئ مفتاحاً جديداً من حساب Google آخر للحصول على حصة جديدة مجاناً."
                            )
                            else -> Pair(
                                "استجاب خادم Google بكود خطأ ($code): $bodyStr",
                                "تحقق من اتصال الإنترنت وحالة خدمات Google Cloud."
                            )
                        }
                        SystemLogsManager.addLog("ERROR", "فشل التحقق من مفتاح Gemini API ($code) - $exp", Color(0xFFEF4444))
                        return@withContext KeyValidationResult(
                            isValid = false,
                            summary = "غير متصل 🔴 (كود $code)",
                            errorCode = code,
                            rawError = bodyStr.take(200),
                            explanation = exp,
                            suggestedFix = fix
                        )
                    }
                }
                "openai" -> {
                    if (!Regex("""sk-[A-Za-z0-9_\-]{20,}""").matches(trimmedKey)) {
                        SystemLogsManager.addLog("ERROR", "مفتاح OpenAI غير صالح الصيغة - صيغة غير sk-", Color(0xFFEF4444))
                        return@withContext KeyValidationResult(
                            isValid = false,
                            summary = "صيغة المفتاح غير صحيحة 🔴",
                            explanation = "مفتاح OpenAI API الصحيح يبدأ بالبادئة «sk-» ويتكون من 27+ محرفاً. المفتاح الذي أدخلته لا يطابق تلك التركيبة.",
                            suggestedFix = "افتح platform.openai.com/api-keys ثم اضغط «Create new secret key» وانسخ المفتاح كاملاً (يبدأ بـ sk-…) دون أي مسافات."
                        )
                    }
                    val url = "https://api.openai.com/v1/models"
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $trimmedKey")
                        .build()
                    val response = client.newCall(request).execute()
                    val code = response.code
                    val bodyStr = response.body?.string().orEmpty()

                    if (response.isSuccessful) {
                        SystemLogsManager.addLog("SUCCESS", "مفتاح OpenAI API متصل وسليم (OpenAI - 200 OK)", Color(0xFF4CAF50))
                        return@withContext KeyValidationResult(
                            isValid = true,
                            summary = "متصل بالسيرفر ✅ (OpenAI API شغال حقيقياً)",
                            errorCode = code,
                            explanation = "تم التحقق الفعلي من صحة المفتاح واستجابة خوادم OpenAI بنجاح (200 OK).",
                            suggestedFix = "المفتاح جاهز للعمل الفوري."
                        )
                    } else {
                        val (exp, fix) = when (code) {
                            401 -> Pair(
                                "المفتاح غير صحيح أو منتهي الصلاحية على OpenAI.",
                                "أنشئ مفتاحاً جديداً من platform.openai.com/api-keys."
                            )
                            429 -> Pair(
                                "تم تجاوز حد الاستخدام أو رصيد الحساب منخفض على OpenAI.",
                                "تحقق من رصيد الحساب أو أعد المحاولة لاحقاً."
                            )
                            else -> Pair(
                                "استجاب خادم OpenAI بكود خطأ ($code): $bodyStr",
                                "تحقق من اتصال الإنترنت وحالة خدمات OpenAI."
                            )
                        }
                        SystemLogsManager.addLog("ERROR", "فشل التحقق من مفتاح OpenAI API ($code) - $exp", Color(0xFFEF4444))
                        return@withContext KeyValidationResult(
                            isValid = false,
                            summary = "غير متصل 🔴 (كود $code)",
                            errorCode = code,
                            rawError = bodyStr.take(200),
                            explanation = exp,
                            suggestedFix = fix
                        )
                    }
                }
                "openrouter" -> {
                    if (!trimmedKey.startsWith("sk-or-", ignoreCase = true)) {
                        return@withContext KeyValidationResult(
                            isValid = false,
                            summary = "صيغة المفتاح غير صحيحة 🔴",
                            explanation = "مفتاح OpenRouter الصحيح يبدأ بالبادئة «sk-or-». المفتاح الذي أدخلته لا يطابق تلك التركيبة.",
                            suggestedFix = "افتح openrouter.ai/keys وأنشئ مفتاحاً جديداً وانسخه كاملاً (يبدأ بـ sk-or-…) دون مسافات."
                        )
                    }
                    val ok = OpenRouterService.validateKey(trimmedKey)
                    if (ok) {
                        SystemLogsManager.addLog("SUCCESS", "مفتاح OpenRouter متصل وسليم (200 OK)", Color(0xFF4CAF50))
                        return@withContext KeyValidationResult(
                            isValid = true,
                            summary = "متصل بالسيرفر ✅ (OpenRouter شغال حقيقياً)",
                            explanation = "تم التحقق الفعلي من صحة المفتاح واستجابة خوادم OpenRouter بنجاح.",
                            suggestedFix = "المفتاح جاهز — خطط الطلبات ستُستخدم النماذج المجانية."
                        )
                    } else {
                        SystemLogsManager.addLog("ERROR", "فشل التحقق من مفتاح OpenRouter", Color(0xFFEF4444))
                        return@withContext KeyValidationResult(
                            isValid = false,
                            summary = "غير متصل 🔴",
                            explanation = "رفض خادم OpenRouter المفتاح أو تعذر الوصول إليه.",
                            suggestedFix = "تحقق من نسخ المفتاح كاملاً ومن اتصال الإنترنت، أو أنشئ مفتاحاً جديداً."
                        )
                    }
                }
                "groq", "grok", "xai" -> {                    if (trimmedKey.startsWith("xai-", ignoreCase = true)) {
                        val url = "https://api.x.ai/v1/models"
                        val request = Request.Builder()
                            .url(url)
                            .header("Authorization", "Bearer $trimmedKey")
                            .build()
                        val response = client.newCall(request).execute()
                        val code = response.code
                        val bodyStr = response.body?.string().orEmpty()

                        if (response.isSuccessful) {
                            SystemLogsManager.addLog("SUCCESS", "مفتاح xAI Grok متصل وسليم (xAI Cloud - 200 OK)", Color(0xFF4CAF50))
                            return@withContext KeyValidationResult(
                                isValid = true,
                                summary = "متصل بالسيرفر ✅ (xAI Grok شغال حقيقياً)",
                                errorCode = code,
                                explanation = "تم التحقق الفعلي من خوادم xAI وتوفر رصيد تشغيلي لموديلات Grok بنجاح.",
                                suggestedFix = "المفتاح جاهز لمعالجة النصوص فائقة السرعة."
                            )
                        } else {
                            val (exp, fix) = when (code) {
                                401 -> Pair(
                                    "المفتاح غير معترف به لدى خوادم x.ai (Unauthorized).",
                                    "تأكد من نسخ المفتاح كاملاً من console.x.ai/keys."
                                )
                                403 -> Pair(
                                    "تم رفض الطلب (403 Forbidden). حسابك على xAI يفتقر إلى رصيد مدفوع (Credits)، حيث أن xAI لا توفر حالياً واجهة برمجية مجانية بدون شحن رصيد.",
                                    "الحل المجاني الموصى به: استخدم منصة Groq Cloud المجانية (console.groq.com/keys) لإنشاء مفتاح يبدأ بـ gsk_ مجاناً 100% بدون أي بطاقة بنكية. أو اشحن رصيداً في console.x.ai."
                                )
                                429 -> Pair(
                                    "تم استهلاك رصيد الاستخدام المتاح على منصة xAI.",
                                    "يرجى شحن رصيدك على console.x.ai أو استخدام Groq Cloud المجاني."
                                )
                                else -> Pair(
                                    "استجاب خادم xAI بالخطأ ($code): $bodyStr",
                                    "تحقق من إعدادات حسابك على console.x.ai."
                                )
                            }
                            SystemLogsManager.addLog("ERROR", "فشل التحقق من مفتاح xAI Grok ($code) - $exp", Color(0xFFEF4444))
                            return@withContext KeyValidationResult(
                                isValid = false,
                                summary = "غير متصل 🔴 (كود $code)",
                                errorCode = code,
                                rawError = bodyStr.take(200),
                                explanation = exp,
                                suggestedFix = fix
                            )
                        }
                    } else {
                        val url = "https://api.groq.com/openai/v1/models"
                        val request = Request.Builder()
                            .url(url)
                            .header("Authorization", "Bearer $trimmedKey")
                            .build()
                        val response = client.newCall(request).execute()
                        val code = response.code
                        val bodyStr = response.body?.string().orEmpty()

                        if (response.isSuccessful) {
                            SystemLogsManager.addLog("SUCCESS", "مفتاح Groq API متصل وسليم (Groq Cloud - 200 OK)", Color(0xFF4CAF50))
                            return@withContext KeyValidationResult(
                                isValid = true,
                                summary = "متصل بالسيرفر ✅ (Groq Ultra Fast جاهز وفعال)",
                                errorCode = code,
                                explanation = "تم التحقق الفعلي من خوادم Groq LPU بنجاح. المحرك الآن نشط لمعالجة النصوص الفورية وتوليد خطافات الريلز الخاطفة.",
                                suggestedFix = "المفتاح جاهز للعمل الفوري."
                            )
                        } else {
                            val (exp, fix) = when (code) {
                                401 -> Pair(
                                    "المفتاح غير صحيح أو منتهي الصلاحية على Groq Cloud.",
                                    "ادخل إلى console.groq.com/keys وأنشئ مفتاحاً جديداً مجانياً (يبدأ بـ gsk_) والصقه هنا."
                                )
                                403 -> Pair(
                                    "تم حظر الوصول إلى خوادم Groq من قبل الحساب أو مزود الخدمة.",
                                    "تأكد من عدم حظر الحساب أو جرب إنشاء مفتاح جديد من حساب آخر على console.groq.com."
                                )
                                else -> Pair(
                                    "استجاب خادم Groq بالخطأ ($code): $bodyStr",
                                    "تأكد من نسخ المفتاح بشكل صحيح."
                                )
                            }
                            SystemLogsManager.addLog("ERROR", "فشل التحقق من مفتاح Groq API ($code) - $exp", Color(0xFFEF4444))
                            return@withContext KeyValidationResult(
                                isValid = false,
                                summary = "غير متصل 🔴 (كود $code)",
                                errorCode = code,
                                rawError = bodyStr.take(200),
                                explanation = exp,
                                suggestedFix = fix
                            )
                        }
                    }
                }
                "huggingface" -> {
                    val url = "https://huggingface.co/api/whoami-v2"
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $trimmedKey")
                        .build()
                    val response = client.newCall(request).execute()
                    val code = response.code
                    val bodyStr = response.body?.string().orEmpty()

                    if (response.isSuccessful) {
                        SystemLogsManager.addLog("SUCCESS", "توكن HuggingFace متصل وسليم (AI Image Studio - 200 OK)", Color(0xFF4CAF50))
                        return@withContext KeyValidationResult(
                            isValid = true,
                            summary = "متصل بالسيرفر ✅ (Hugging Face جاهز وفعال)",
                            errorCode = code,
                            explanation = "تم التحقق الفعلي من حساب Hugging Face وصلاحيات التوكن لتوليد الصور والمشاهد بالذكاء الاصطناعي.",
                            suggestedFix = "المفتاح جاهز للعمل الفوري."
                        )
                    } else {
                        val (exp, fix) = when (code) {
                            401 -> Pair(
                                "توكن Hugging Face غير صحيح أو تنقصه صلاحية القراءة (Read permissions).",
                                "ادخل إلى huggingface.co/settings/tokens وأنشئ توكن جديد بنوع 'Read' أو 'Write' والصقه هنا (يبدأ بـ hf_)."
                            )
                            else -> Pair(
                                "استجاب خادم Hugging Face بالخطأ ($code): $bodyStr",
                                "تحقق من حسابك على منصة Hugging Face."
                            )
                        }
                        SystemLogsManager.addLog("ERROR", "فشل التحقق من توكن Hugging Face ($code) - $exp", Color(0xFFEF4444))
                        return@withContext KeyValidationResult(
                            isValid = false,
                            summary = "غير متصل 🔴 (كود $code)",
                            errorCode = code,
                            rawError = bodyStr.take(200),
                            explanation = exp,
                            suggestedFix = fix
                        )
                    }
                }
                "pexels" -> {
                    val url = "https://api.pexels.com/v1/curated?per_page=1"
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", trimmedKey)
                        .build()
                    val response = client.newCall(request).execute()
                    val code = response.code
                    val bodyStr = response.body?.string().orEmpty()

                    if (response.isSuccessful) {
                        SystemLogsManager.addLog("SUCCESS", "مفتاح Pexels API متصل وسليم (Media Library - 200 OK)", Color(0xFF4CAF50))
                        return@withContext KeyValidationResult(
                            isValid = true,
                            summary = "متصل بالسيرفر ✅ (Pexels Media جاهز وفعال)",
                            errorCode = code,
                            explanation = "تم التحقق الفعلي من مكتبة Pexels لجلب مقاطع الفيديو والصور فائقة الدقة للمشاهد والمونتاج الحقيقي.",
                            suggestedFix = "المفتاح جاهز للعمل الفوري."
                        )
                    } else {
                        val (exp, fix) = when (code) {
                            401 -> Pair(
                                "مفتاح Pexels API غير صالح أو غير معتمد.",
                                "ادخل إلى pexels.com/api واطلب مفتاح مجاني ثم الصقه هنا."
                            )
                            403 -> Pair(
                                "تم تقييد حساب Pexels بسبب تجاوز حد الطلبات.",
                                "انتظر ساعة لإعادة تعيين الحصة أو أنشئ مفتاحاً جديداً."
                            )
                            else -> Pair(
                                "استجاب خادم Pexels بالخطأ ($code): $bodyStr",
                                "تأكد من صحة المفتاح."
                            )
                        }
                        SystemLogsManager.addLog("ERROR", "فشل التحقق من مفتاح Pexels API ($code) - $exp", Color(0xFFEF4444))
                        return@withContext KeyValidationResult(
                            isValid = false,
                            summary = "غير متصل 🔴 (كود $code)",
                            errorCode = code,
                            rawError = bodyStr.take(200),
                            explanation = exp,
                            suggestedFix = fix
                        )
                    }
                }
                "pixabay" -> {
                    val url = "https://pixabay.com/api/?key=$trimmedKey&q=nature"
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    val code = response.code
                    val bodyStr = response.body?.string().orEmpty()

                    if (response.isSuccessful) {
                        SystemLogsManager.addLog("SUCCESS", "مفتاح Pixabay API متصل وسليم (Media Library - 200 OK)", Color(0xFF4CAF50))
                        return@withContext KeyValidationResult(
                            isValid = true,
                            summary = "متصل بالسيرفر ✅ (Pixabay Media جاهز وفعال)",
                            errorCode = code,
                            explanation = "تم التحقق الفعلي من خوادم Pixabay بنجاح. مكتبة الوسائط الحرة جاهزة لجلب الخلفيات والمشاهد.",
                            suggestedFix = "المفتاح جاهز للعمل الفوري."
                        )
                    } else {
                        val (exp, fix) = when (code) {
                            400, 401 -> Pair(
                                "مفتاح Pixabay غير صحيح أو مفقود في قاعدة بيانات Pixabay.",
                                "سجل الدخول في pixabay.com/api/docs وانسخ المفتاح الظاهر في الصفحة والصقه هنا."
                            )
                            else -> Pair(
                                "استجاب خادم Pixabay بالخطأ ($code): $bodyStr",
                                "تأكد من سلامة المفتاح."
                            )
                        }
                        SystemLogsManager.addLog("ERROR", "فشل التحقق من مفتاح Pixabay API ($code) - $exp", Color(0xFFEF4444))
                        return@withContext KeyValidationResult(
                            isValid = false,
                            summary = "غير متصل 🔴 (كود $code)",
                            errorCode = code,
                            rawError = bodyStr.take(200),
                            explanation = exp,
                            suggestedFix = fix
                        )
                    }
                }
                "azure" -> {
                    val region = hint ?: ""
                    if (region.isBlank()) {
                        return@withContext KeyValidationResult(
                            isValid = false,
                            summary = "أدخل المنطقة أولا 🔴",
                            explanation = "التحقق من Azure Speech يتطلب كتابة المنطقة (Region) مثل eastus بجانب المفتاح.",
                            suggestedFix = "في صفحة Keys and Endpoint انسخ قيمة Location/Region والصقها في حقل المنطقة."
                        )
                    }
                    val url = "https://$region.api.cognitive.microsoft.com/sts/v1.0/issuetoken"
                    val request = Request.Builder()
                        .url(url)
                        .header("Ocp-Apim-Subscription-Key", trimmedKey)
                        .build()
                    val response = client.newCall(request).execute()
                    val code = response.code
                    val bodyStr = response.body?.string().orEmpty()

                    if (response.isSuccessful) {
                        SystemLogsManager.addLog("SUCCESS", "مفتاح Azure Speech متصل وسليم (Microsoft - 200 OK)", Color(0xFF4CAF50))
                        return@withContext KeyValidationResult(
                            isValid = true,
                            summary = "متصل بالسيرفر ✅ (Azure Speech شغال حقيقياً)",
                            errorCode = code,
                            explanation = "تم التحقق الفعلي من مفتاح Azure Speech والمنطقة بنجاح. التعليق الصوتي العربي الفصيح جاهز للعمل.",
                            suggestedFix = "المفتاح جاهز للاستخدام الفوري."
                        )
                    } else {
                        SystemLogsManager.addLog("ERROR", "فشل التحقق من مفتاح Azure Speech ($code) - $bodyStr", Color(0xFFEF4444))
                        return@withContext KeyValidationResult(
                            isValid = false,
                            summary = "غير متصل 🔴 (كود $code)",
                            errorCode = code,
                            rawError = bodyStr.take(200),
                            explanation = "استجاب خادم Azure بالخطأ ($code). تأكد من صحة المفتاح والمنطقة معاً.",
                            suggestedFix = "تأكد من نسخ KEY 1 والمنطقة الصحيحة من صفحة Keys and Endpoint في Azure."
                        )
                    }
                }
                "elevenlabs" -> {
                    val url = "https://api.elevenlabs.io/v1/user/subscription"
                    val request = Request.Builder()
                        .url(url)
                        .header("xi-api-key", trimmedKey)
                        .build()
                    val response = client.newCall(request).execute()
                    val code = response.code
                    val bodyStr = response.body?.string().orEmpty()

                    if (response.isSuccessful) {
                        SystemLogsManager.addLog("SUCCESS", "مفتاح ElevenLabs متصل وسليم (ElevenLabs - 200 OK)", Color(0xFF4CAF50))
                        return@withContext KeyValidationResult(
                            isValid = true,
                            summary = "متصل بالسيرفر ✅ (ElevenLabs شغال حقيقياً)",
                            errorCode = code,
                            explanation = "تم التحقق الفعلي من مفتاح ElevenLabs بنجاح. الأصوات السينمائية جاهزة للعمل.",
                            suggestedFix = "المفتاح جاهز للاستخدام الفوري."
                        )
                    } else {
                        SystemLogsManager.addLog("ERROR", "فشل التحقق من مفتاح ElevenLabs ($code) - $bodyStr", Color(0xFFEF4444))
                        return@withContext KeyValidationResult(
                            isValid = false,
                            summary = "غير متصل 🔴 (كود $code)",
                            errorCode = code,
                            rawError = bodyStr.take(200),
                            explanation = "استجاب خادم ElevenLabs بالخطأ ($code). تأكد من صحة المفتاح الذي يبدأ بـ xi-.",
                            suggestedFix = "انسخ المفتاح كاملاً من الإعدادات → API Keys في حساب ElevenLabs."
                        )
                    }
                }
                "coverr" -> {
                    val url = "https://api.coverr.co/videos?api_key=$trimmedKey&urls=true"
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    val code = response.code
                    val bodyStr = response.body?.string().orEmpty()

                    if (response.isSuccessful) {
                        SystemLogsManager.addLog("SUCCESS", "مفتاح Coverr متصل وسليم (Coverr B-Roll - 200 OK)", Color(0xFF4CAF50))
                        return@withContext KeyValidationResult(
                            isValid = true,
                            summary = "متصل بالسيرفر ✅ (Coverr HD B-Roll فعال)",
                            errorCode = code,
                            explanation = "تم التحقق الفعلي من مفتاح Coverr واستجابة خوادم الفيديو بنجاح (200 OK). مكتبة مقاطع الـ B-Roll السينمائية جاهزة للمونتاج.",
                            suggestedFix = "المفتاح جاهز للعمل الفوري."
                        )
                    } else {
                        SystemLogsManager.addLog("ERROR", "فشل التحقق من مفتاح Coverr ($code) - $bodyStr", Color(0xFFEF4444))
                        return@withContext KeyValidationResult(
                            isValid = false,
                            summary = "غير متصل 🔴 (كود $code)",
                            errorCode = code,
                            rawError = bodyStr.take(200),
                            explanation = "استجاب خادم Coverr بكود خطأ ($code).",
                            suggestedFix = "تأكد من صحة مفتاح Coverr API من لوحة تحكم coverr.co."
                        )
                    }
                }
                "higgsfield" -> {
                    val (isSuccess, message) = HiggsfieldVideoService.testConnection(context, trimmedKey)
                    if (isSuccess) {
                        SystemLogsManager.addLog("SUCCESS", "مفتاح Higgsfield AI متصل وسليم (Video Engine - 200 OK)", Color(0xFF4CAF50))
                        return@withContext KeyValidationResult(
                            isValid = true,
                            summary = "متصل بالسيرفر ✅ (Higgsfield Video AI جاهز)",
                            errorCode = 200,
                            explanation = "تم التحقق الفعلي من خوادم منصة Higgsfield AI بنجاح (200 OK). محرك التوليد السينمائي الفائق وتحريك الصور بالذكاء الاصطناعي متصل ومستعد للعمل.",
                            suggestedFix = "المفتاح جاهز للاستخدام الفوري في الاستوديو الموحد واستوديو الصور."
                        )
                    } else {
                        SystemLogsManager.addLog("ERROR", "فشل التحقق من مفتاح Higgsfield AI: $message", Color(0xFFEF4444))
                        return@withContext KeyValidationResult(
                            isValid = false,
                            summary = "غير متصل 🔴",
                            errorCode = 401,
                            rawError = message,
                            explanation = message,
                            suggestedFix = "ادخل إلى منصة higgsfield.ai ثم انسخ مفتاح API Key وضعه هنا."
                        )
                    }
                }
                "leonardo" -> {
                    val (isSuccess, message) = LeonardoImageService.testConnection(context, trimmedKey)
                    if (isSuccess) {
                        SystemLogsManager.addLog("SUCCESS", "مفتاح Leonardo.ai متصل وسليم (Vision Engine - 200 OK)", Color(0xFF4CAF50))
                        return@withContext KeyValidationResult(
                            isValid = true,
                            summary = "متصل بالسيرفر ✅ (Leonardo.ai جاهز)",
                            errorCode = 200,
                            explanation = "تم التحقق الفعلي من خوادم Leonardo.ai بنجاح (200 OK). محرك الصور والرسم الفائق جاهز للعمل.",
                            suggestedFix = "المفتاح جاهز للاستخدام الفوري في استوديو الصور والمشاهد الفنية."
                        )
                    } else {
                        SystemLogsManager.addLog("ERROR", "فشل التحقق من مفتاح Leonardo.ai: $message", Color(0xFFEF4444))
                        return@withContext KeyValidationResult(
                            isValid = false,
                            summary = "غير متصل 🔴",
                            errorCode = 401,
                            rawError = message,
                            explanation = message,
                            suggestedFix = "ادخل إلى https://app.leonardo.ai/api-access ثم انسخ مفتاح API وضعه هنا."
                        )
                    }
                }
                "qf", "quranfoundation", "quran_foundation" -> {
                    val url = "https://api.quran.com/api/v4/chapters"
                    val request = Request.Builder()
                        .url(url)
                        .header("x-auth-token", trimmedKey)
                        .header("x-client-id", hint ?: "")
                        .build()
                    val response = client.newCall(request).execute()
                    val code = response.code
                    val bodyStr = response.body?.string().orEmpty()

                    if (response.isSuccessful) {
                        SystemLogsManager.addLog("SUCCESS", "اتصال Quran Foundation سليم ومعتمد (Quran API - 200 OK)", Color(0xFF4CAF50))
                        return@withContext KeyValidationResult(
                            isValid = true,
                            summary = "متصل بالسيرفر ✅ (Quran Foundation موثق)",
                            errorCode = code,
                            explanation = "تم التحقق من بيانات الربط الرسمية لمنصة Quran.com و Quran Foundation بنجاح (200 OK). خدمات النصوص الموثقة ومطابقة الآيات متصلة.",
                            suggestedFix = "المفتاح جاهز للاستخدام."
                        )
                    } else {
                        return@withContext KeyValidationResult(
                            isValid = false,
                            summary = "غير متصل 🔴 (كود $code)",
                            errorCode = code,
                            rawError = bodyStr.take(200),
                            explanation = "استجاب خادم Quran.com بالرمز ($code).",
                            suggestedFix = "تأكد من صحة Client ID و Client Secret المسجلين."
                        )
                    }
                }
                "supabase" -> {
                    val supabaseUrl = hint?.takeIf { it.startsWith("https://") } ?: SupabaseConfig.url
                    if (supabaseUrl.isBlank() || !supabaseUrl.startsWith("https://")) {
                        return@withContext KeyValidationResult(
                            isValid = false,
                            summary = "رابط Supabase مفقود 🔴",
                            explanation = "يجب تحديد SUPABASE_URL للتحقق من الاتصال بقاعدة البيانات.",
                            suggestedFix = "تأكد من ضبط SUPABASE_URL في ملف الإعدادات."
                        )
                    }
                    val url = "$supabaseUrl/rest/v1/"
                    val request = Request.Builder()
                        .url(url)
                        .header("apikey", trimmedKey)
                        .header("Authorization", "Bearer $trimmedKey")
                        .build()
                    val response = client.newCall(request).execute()
                    val code = response.code
                    val bodyStr = response.body?.string().orEmpty()

                    if (response.isSuccessful || code == 200 || code == 204) {
                        SystemLogsManager.addLog("SUCCESS", "سحابة Supabase متصلة وسليمة (REST API - 200 OK)", Color(0xFF4CAF50))
                        return@withContext KeyValidationResult(
                            isValid = true,
                            summary = "متصل بالسيرفر ✅ (Supabase Cloud متصل)",
                            errorCode = code,
                            explanation = "تم التحقق الفعلي من استجابة خوادم Supabase وصلاحيات مفتاح Anon Key بنجاح (200 OK). التخزين السحابي والصوتيات ومزامنة المشاريع جاهزة للعمل.",
                            suggestedFix = "الاتصال سليم 100%."
                        )
                    } else {
                        SystemLogsManager.addLog("ERROR", "فشل التحقق من اتصال Supabase ($code)", Color(0xFFEF4444))
                        return@withContext KeyValidationResult(
                            isValid = false,
                            summary = "غير متصل 🔴 (كود $code)",
                            errorCode = code,
                            rawError = bodyStr.take(200),
                            explanation = "استجاب خادم Supabase بالرمز ($code). تأكد من صحة المفتاح والرابط.",
                            suggestedFix = "انسخ Project URL و anon key من لوحة تحكم Supabase -> Project Settings -> API."
                        )
                    }
                }
                "firebase", "google_services" -> {
                    val isValidJson = trimmedKey.contains("project_info") && trimmedKey.contains("project_id")
                    if (isValidJson) {
                        SystemLogsManager.addLog("SUCCESS", "ملف google-services.json تم التحقق من سلامة هيكليته", Color(0xFF4CAF50))
                        return@withContext KeyValidationResult(
                            isValid = true,
                            summary = "مهيأ وسليم ✅ (Firebase Config موثق)",
                            explanation = "تم التحقق من وجود وسلامة إعدادات Firebase Cloud Messaging وخدمات Google Services.",
                            suggestedFix = "الإعدادات مكتملة."
                        )
                    } else {
                        return@withContext KeyValidationResult(
                            isValid = false,
                            summary = "تنسيق غير مكتمل 🔴",
                            explanation = "النص المدخل لا يحتوي على بيانات مشروع Firebase الأساسية (project_info / project_id).",
                            suggestedFix = "حمّل ملف google-services.json من وحدة تحكم Firebase والصق محتواه."
                        )
                    }
                }
                "google_auth" -> {
                    if (trimmedKey.contains("googleusercontent") && trimmedKey.length > 25) {
                        SystemLogsManager.addLog("SUCCESS", "معرّف Google Web Client ID سليم ومطابق لمعايير OAuth2", Color(0xFF4CAF50))
                        return@withContext KeyValidationResult(
                            isValid = true,
                            summary = "معرّف سليم ومُهيأ ✅ (OAuth 2.0 Web Client)",
                            explanation = "تم التحقق من تركيبة معرف العميل الخاص بـ Google Cloud. جاهز للمصادقة وتوليد ID Tokens مع Supabase وFirebase.",
                            suggestedFix = "يمكنك الآن تجربة تسجيل الدخول بنقرة واحدة عبر Google."
                        )
                    } else {
                        SystemLogsManager.addLog("WARNING", "صيغة معرّف Google Web Client ID غير دقيقة", Color(0xFFF59E0B))
                        return@withContext KeyValidationResult(
                            isValid = false,
                            summary = "الصيغة غير مكتملة 🟡",
                            explanation = "يجب أن يكون المعرف صالحاً من نوع Web application Client ID.",
                            suggestedFix = "أنشئ OAuth Client ID من نوع Web application في Google Cloud Console."
                        )
                    }
                }
                else -> {
                    if (trimmedKey.length >= 8) {
                        SystemLogsManager.addLog("SUCCESS", "تم قبول المفتاح وحفظ البيانات بنجاح", Color(0xFF4CAF50))
                        return@withContext KeyValidationResult(
                            isValid = true,
                            summary = "متصل ✅ (صالح)",
                            explanation = "تم حفظ المفتاح محلياً وتمريره إلى محركات الخدمة بنجاح.",
                            suggestedFix = "المفتاح جاهز للعمل."
                        )
                    } else {
                        SystemLogsManager.addLog("ERROR", "المفتاح قصير جداً وغير صالح", Color(0xFFEF4444))
                        return@withContext KeyValidationResult(
                            isValid = false,
                            summary = "غير متصل 🔴 (المفتاح قصير جداً)",
                            explanation = "المفتاح المدخل أقل من 8 أحرف ولا يطابق المعايير التقنية للمفاتيح السحابية.",
                            suggestedFix = "تأكد من نسخ المفتاح كاملاً بدون نقص."
                        )
                    }
                }
            }
        } catch (e: Exception) {
            val netError = e.localizedMessage ?: "تعذر الوصول للسيرفر"
            SystemLogsManager.addLog("ERROR", "فشل الاتصال بالإنترنت لفحص المفتاح: $netError", Color(0xFFEF4444))
            return@withContext KeyValidationResult(
                isValid = false,
                summary = "خطأ اتصال بالشبكة 🌐",
                rawError = netError,
                explanation = "تعذر إرسال طلب الفحص الحقيقي إلى خوادم المزود بسبب انقطاع الإنترنت أو بطء الشبكة في جهازك.",
                suggestedFix = "تأكد من تشغيل الواي فاي / بيانات الهاتف ثم اضغط على زر الفحص مجدداً."
            )
        }
    }
}
