package com.qabas.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/**
 * متتبع حقيقي لاستهلاك وزمن استجابة خدمات API مع توثيق حالات الاتصال وأسباب النجاح والفشل.
 * يسجل الطلبات الفعلية، زمن الاستجابة (Latency ms)، كود الاستجابة (HTTP Status)،
 * أسباب النجاح وأسباب الفشل بالتفصيل وسجل الطلبات الحية.
 */
object ApiUsageTracker {
    private const val TAG = "ApiUsageTracker"
    private const val PREFS = "qabas_prefs"
    private const val USAGE_JSON_KEY = "api_usage_tracker_json_v3"
    private const val LOGS_JSON_KEY = "api_recent_logs_json_v1"
    private const val MAX_LOGS = 60

    val SUPPORTED: List<String> = listOf(
        "Gemini", "Groq", "OpenAI", "OpenRouter", "Azure TTS", "ElevenLabs",
        "HuggingFace", "Pexels", "Pixabay", "Firebase", "Quran Foundation"
    )

    data class ApiStat(
        val totalCalls: Long = 0L,
        val successCalls: Long = 0L,
        val totalLatencyMs: Long = 0L,
        val lastLatencyMs: Long = 0L,
        val lastStatusCode: Int? = null,
        val lastStatusText: String? = null,
        val lastSuccessReason: String? = null,
        val lastFailureReason: String? = null,
        val lastTimestamp: Long = 0L,
    ) {
        val failureCalls: Long
            get() = (totalCalls - successCalls).coerceAtLeast(0L)
        val avgLatencyMs: Long
            get() = if (totalCalls > 0) totalLatencyMs / totalCalls else 0L
        val successRate: Float
            get() = if (totalCalls > 0) successCalls.toFloat() / totalCalls else 0f
    }

    data class RequestLogItem(
        val id: String = java.util.UUID.randomUUID().toString(),
        val apiName: String,
        val timestamp: Long = System.currentTimeMillis(),
        val latencyMs: Long,
        val isSuccess: Boolean,
        val statusCode: Int? = null,
        val statusText: String = "",
        val reason: String = "",
        val diagnostic: String = ""
    )

    private var _stats: Map<String, ApiStat> = emptyMap()
    private val _recentLogs: MutableList<RequestLogItem> = mutableListOf()
    private var loaded = false

    @Synchronized
    private fun load(context: Context) {
        if (loaded) return
        try {
            val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val rawStats = sp.getString(USAGE_JSON_KEY, null)
                ?: sp.getString("api_usage_tracker_json_v2", null)

            if (rawStats.isNullOrBlank()) {
                _stats = defaultEmptyStats()
            } else {
                val arr = JSONArray(rawStats)
                val map = LinkedHashMap<String, ApiStat>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString("name")
                    if (name.isBlank()) continue
                    map[name] = ApiStat(
                        totalCalls = o.optLong("totalCalls"),
                        successCalls = o.optLong("successCalls"),
                        totalLatencyMs = o.optLong("totalLatencyMs"),
                        lastLatencyMs = o.optLong("lastLatencyMs"),
                        lastStatusCode = if (o.has("lastStatusCode")) o.optInt("lastStatusCode") else null,
                        lastStatusText = o.optString("lastStatusText", null),
                        lastSuccessReason = o.optString("lastSuccessReason", null),
                        lastFailureReason = o.optString("lastFailureReason", null),
                        lastTimestamp = o.optLong("lastTimestamp", 0L)
                    )
                }
                _stats = defaultEmptyStats() + map
            }

            val rawLogs = sp.getString(LOGS_JSON_KEY, null)
            _recentLogs.clear()
            if (!rawLogs.isNullOrBlank()) {
                val arrLogs = JSONArray(rawLogs)
                for (i in 0 until arrLogs.length()) {
                    val o = arrLogs.optJSONObject(i) ?: continue
                    _recentLogs.add(
                        RequestLogItem(
                            id = o.optString("id", java.util.UUID.randomUUID().toString()),
                            apiName = o.optString("apiName"),
                            timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                            latencyMs = o.optLong("latencyMs"),
                            isSuccess = o.optBoolean("isSuccess"),
                            statusCode = if (o.has("statusCode")) o.optInt("statusCode") else null,
                            statusText = o.optString("statusText"),
                            reason = o.optString("reason"),
                            diagnostic = o.optString("diagnostic")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "load failed: ${e.message}")
            _stats = defaultEmptyStats()
        }
        loaded = true
    }

    private fun defaultEmptyStats(): Map<String, ApiStat> =
        LinkedHashMap<String, ApiStat>().apply { SUPPORTED.forEach { put(it, ApiStat()) } }

    @Synchronized
    private fun persist(context: Context) {
        try {
            val arr = JSONArray()
            _stats.forEach { (name, stat) ->
                arr.put(JSONObject().apply {
                    put("name", name)
                    put("totalCalls", stat.totalCalls)
                    put("successCalls", stat.successCalls)
                    put("totalLatencyMs", stat.totalLatencyMs)
                    put("lastLatencyMs", stat.lastLatencyMs)
                    stat.lastStatusCode?.let { put("lastStatusCode", it) }
                    stat.lastStatusText?.let { put("lastStatusText", it) }
                    stat.lastSuccessReason?.let { put("lastSuccessReason", it) }
                    stat.lastFailureReason?.let { put("lastFailureReason", it) }
                    put("lastTimestamp", stat.lastTimestamp)
                })
            }

            val logsArr = JSONArray()
            _recentLogs.take(MAX_LOGS).forEach { item ->
                logsArr.put(JSONObject().apply {
                    put("id", item.id)
                    put("apiName", item.apiName)
                    put("timestamp", item.timestamp)
                    put("latencyMs", item.latencyMs)
                    put("isSuccess", item.isSuccess)
                    item.statusCode?.let { put("statusCode", it) }
                    put("statusText", item.statusText)
                    put("reason", item.reason)
                    put("diagnostic", item.diagnostic)
                })
            }

            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(USAGE_JSON_KEY, arr.toString())
                .putString(LOGS_JSON_KEY, logsArr.toString())
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "persist failed: ${e.message}")
        }
    }

    @Synchronized
    fun recordCall(
        context: Context,
        apiName: String,
        latencyMs: Long,
        success: Boolean,
        statusCode: Int? = null,
        statusText: String? = null,
        reason: String? = null,
        diagnostic: String? = null
    ) {
        load(context)
        val now = System.currentTimeMillis()
        val current = _stats[apiName] ?: ApiStat()
        val effStatusText = statusText ?: (if (success) "200 OK" else (statusCode?.toString() ?: "Error"))
        val effReason = reason ?: if (success) "تم استقبال الاستجابة بنجاح" else "فشل الطلب"

        val updated = ApiStat(
            totalCalls = current.totalCalls + 1,
            successCalls = current.successCalls + if (success) 1 else 0,
            totalLatencyMs = current.totalLatencyMs + latencyMs,
            lastLatencyMs = latencyMs,
            lastStatusCode = statusCode ?: if (success) 200 else current.lastStatusCode,
            lastStatusText = effStatusText,
            lastSuccessReason = if (success) effReason else current.lastSuccessReason,
            lastFailureReason = if (!success) effReason else current.lastFailureReason,
            lastTimestamp = now
        )

        _stats = _stats + (apiName to updated)

        val logItem = RequestLogItem(
            apiName = apiName,
            timestamp = now,
            latencyMs = latencyMs,
            isSuccess = success,
            statusCode = statusCode,
            statusText = effStatusText,
            reason = effReason,
            diagnostic = diagnostic.orEmpty()
        )
        _recentLogs.add(0, logItem)
        if (_recentLogs.size > MAX_LOGS) {
            _recentLogs.removeAt(_recentLogs.lastIndex)
        }

        persist(context)
        CrashBreadcrumbs.api(apiName, latencyMs, success)
    }

    @Synchronized
    fun recordCall(context: Context, apiName: String, latencyMs: Long, success: Boolean) {
        val defaultCode = if (success) 200 else null
        val defaultReason = if (success) "استجابة ناجحة (200 OK) وسريعة" else "فشل إكمال الطلب"
        recordCall(context, apiName, latencyMs, success, defaultCode, null, defaultReason, null)
    }

    /**
     * تسجيل نتيجة فحص مباشر لمفتاح API مع حفظ الأسباب الدقيقة للنجاح أو الفشل.
     */
    @Synchronized
    fun recordValidationResult(
        context: Context,
        apiName: String,
        latencyMs: Long,
        result: KeyValidationResult
    ) {
        val code = result.errorCode ?: (if (result.isValid) 200 else 400)
        val statusText = if (result.isValid) "200 OK" else "HTTP $code"
        val reason = if (result.isValid) {
            result.explanation.ifBlank { "تم التحقق الفعلي من صحة المفتاح واتصال الخادم بنجاح (200 OK)." }
        } else {
            result.explanation.ifBlank { result.summary }
        }
        val diag = if (result.isValid) {
            result.suggestedFix.ifBlank { "المفتاح جاهز للعمل الفوري واستقبال كافة الطلبات." }
        } else {
            result.suggestedFix.ifBlank { result.rawError ?: "تحقق من إعدادات المفتاح وصلاحيته." }
        }

        recordCall(
            context = context,
            apiName = apiName,
            latencyMs = latencyMs,
            success = result.isValid,
            statusCode = code,
            statusText = statusText,
            reason = reason,
            diagnostic = diag
        )
    }

    /**
     * يغلف طلباً حقيقياً ويحتسب فشل HTTP كفشل حتى لو لم يقع استثناء شبكي،
     * مع استخراج كود الخطأ وتوصيف أسباب النجاح والفشل بدقة.
     */
    suspend fun <T> track(
        context: Context,
        apiName: String,
        block: suspend () -> T,
    ): T = withContext(Dispatchers.IO) {
        val start = System.nanoTime()
        try {
            val result = block()
            val latencyMs = (System.nanoTime() - start) / 1_000_000L
            val response = result as? Response
            val success = response?.isSuccessful ?: true
            val statusCode = response?.code ?: if (success) 200 else null
            val statusMessage = response?.message.orEmpty()

            val (reason, diag) = if (success) {
                Pair(
                    "تم الاتصال بالخادم بنجاح واستلام الاستجابة السليمة (${statusCode ?: 200} OK)",
                    "المفتاح يعمل بكفاءة والاتصال نشط وسريع."
                )
            } else {
                val failureExplanation = when (statusCode) {
                    400 -> "خطأ في تركيبة الطلب أو المعاملات (400 Bad Request)"
                    401 -> "المفتاح غير مصرح به أو منتهي الصلاحية (401 Unauthorized)"
                    403 -> "تم رفض الوصول للمفتاح أو نقص الصلاحيات (403 Forbidden)"
                    404 -> "المورد أو النموذج المطلوب غير موجود (404 Not Found)"
                    429 -> "تم استنفاد الحصة المسموحة أو تجاوز معدل الطلبات (429 Rate Limit)"
                    500 -> "خطأ داخلي في خوادم المزود البعيد (500 Internal Server Error)"
                    503 -> "خوادم الخدمة تحت الصيانة أو الضغط المرتفع (503 Service Unavailable)"
                    else -> "فشل الطلب مع كود الاستجابة ($statusCode)"
                }
                Pair(failureExplanation, "تحقق من صحة المفتاح ورصيد الحساب المتاح لدى المزود.")
            }

            recordCall(
                context = context,
                apiName = apiName,
                latencyMs = latencyMs,
                success = success,
                statusCode = statusCode,
                statusText = if (statusMessage.isNotBlank()) "$statusCode $statusMessage" else "HTTP $statusCode",
                reason = reason,
                diagnostic = diag
            )
            result
        } catch (e: Exception) {
            val latencyMs = (System.nanoTime() - start) / 1_000_000L
            val netErr = e.localizedMessage ?: e.message ?: "خطأ انقطاع الشبكة"
            recordCall(
                context = context,
                apiName = apiName,
                latencyMs = latencyMs,
                success = false,
                statusCode = null,
                statusText = "Network Error",
                reason = "فشل الاتصال بالشبكة: $netErr",
                diagnostic = "تأكد من توفر اتصال الإنترنت وسلامة إعدادات DNS والشبكة."
            )
            throw e
        }
    }

    suspend fun snapshot(context: Context): Map<String, ApiStat> = withContext(Dispatchers.IO) {
        load(context)
        _stats.toMap()
    }

    suspend fun getRecentLogs(context: Context): List<RequestLogItem> = withContext(Dispatchers.IO) {
        load(context)
        _recentLogs.toList()
    }

    suspend fun reset(context: Context) = withContext(Dispatchers.IO) {
        _stats = defaultEmptyStats()
        _recentLogs.clear()
        persist(context)
    }

    suspend fun clearLogs(context: Context) = withContext(Dispatchers.IO) {
        load(context)
        _recentLogs.clear()
        persist(context)
    }
}
