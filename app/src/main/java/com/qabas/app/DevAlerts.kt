package com.qabas.app

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.qabas.app.ui.theme.DeepSlate
import com.qabas.app.ui.theme.TextPrimary
import com.qabas.app.ui.theme.TextSecondary
import com.qabas.app.ui.theme.ThemeSecondarySurface
import com.qabas.app.ui.theme.ThemeSuccess
import com.qabas.app.ui.theme.ThemeWarning
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * تنبيهات لوحة المطور (الدفعة 5 — قسم 3-ز من تقرير الرئيسية ولوحة المطور).
 *
 * قناة التوصيل: **إشعار داخل التطبيق فقط** (قائمة التنبيهات أدناه + سجل
 * التدقيق). لا يوجد في التطبيق اليوم أي مسار دفع (Push) أو بريد مرسل
 * للمطور يمكن ربط التنبيه به — جدول `user_push_tokens` موجود لكن لا يوجد
 * مرسل إشعارات خادم يستدعيه — فلا يُدَّعى إرسال خارجي هنا. إن أُضيف لاحقاً
 * مسار دفع/بريد، تُستدعى نقطة التوصيل من [onAlertFired] لا من المقيِّم.
 *
 * كل تنبيه له حدٌّ قابل للتعديل في [DevAlertThresholds] (ثوابت كود فقط،
 * بلا واجهة تعديل في هذه الدفعة — صفحة «التنبيهات» تعرض الحدود الحالية).
 * كل تقييم يُسجَّل عبر [recordAlertEvaluation] سواء أطلق تنبيهاً أم لا،
 * فيُعرف متى فُحص ماذا ومتى أُطلق ماذا.
 */

/** حدود التنبيه — عدِّلها هنا فقط؛ كل حد موثَّق بمصدره. */
object DevAlertThresholds {
    /** قفزة أعطال: نسبة الجلسات الخالية من الأعطال دون 99% (أي أكثر من 1% */
    /** جلسات متعطلة) في الساعة الأخيرة — حد تقرير التدقيق (قسم 3-ز). */
    const val MIN_CRASH_FREE_RATE_PERCENT = 99.0

    /** لا يُقيَّم حد الأعطال إلا فوق هذا العدد من الجلسات في الساعة، حتى */
    /** لا يُطلق تنبيه من جلسة واحدة متعطلة في تطبيق صغير (3 مستخدمين). */
    const val MIN_SESSIONS_FOR_CRASH_ALERT = 10

    /** فشل مفتاح API: أي مفتاح مزود يفشل فحصه الحي يُطلق تنبيهاً (العدد ≥ 1). */
    const val FAILED_API_KEYS_TO_ALERT = 1

    /** خطأ بوابة: فشل فحص اتصال Supabase. الشدة «حرجة» عند فشلين متتاليين. */
    const val GATEWAY_CONSECUTIVE_FAILURES_FOR_CRITICAL = 2

    /** موجة تسجيلات: تسجيلات الساعة الأخيرة أكثر من هذا المضاعف من متوسط */
    /** الساعة الواحدة خلال الـ 24 ساعة السابقة… */
    const val SIGNUP_SURGE_MULTIPLIER = 3.0

    /** …ولا تُحتسب الموجة إلا فوق هذا العدد المطلق من التسجيلات في الساعة، */
    /** حتى لا تُطلق موجة من تسجيل واحد حين يكون المتوسط صفراً. */
    const val SIGNUP_SURGE_MIN_NEW_SIGNUPS = 5
}

enum class AlertType(val label: String) {
    CRASH_SPIKE("قفزة أعطال"),
    API_KEY_FAILURE("فشل مفتاح API"),
    GATEWAY_ERROR("خطأ بوابة"),
    SIGNUP_SURGE("موجة تسجيلات")
}

enum class AlertSeverity(val label: String) { WARNING("تحذير"), CRITICAL("حرج") }

/** تنبيه واحد أُطلق عند تقييمٍ ما. */
data class DevAlert(
    val type: AlertType,
    val severity: AlertSeverity,
    val message: String,
    val measuredValue: String,
    val thresholdValue: String,
    val timeMs: Long = System.currentTimeMillis()
)

/** مدخلات التقييم — كلها قياسات حقيقية أو null حين لا يوجد مصدر قياس. */
/** لا يُختلق أي رقم: الحقل null يعني «لا يمكن القياس» فيُتخطى حده. */
data class DevAlertInputs(
    /** جلسات متعطلة في الساعة الأخيرة (من تقارير قياس الأعطال السحابية —
     * تقارير لا جلسات مميزة، فتقارير الأعطال بلا session_id؛ العدّ متحفظ:
     * جلسة تتعطل مرتين تُحسب مرتين). */
    val crashedSessionsLastHour: Int? = null,
    /** إجمالي الجلسات في الساعة الأخيرة — session_id مميز من analytics_events
     * (يُقاس في collectAndEvaluate)؛ null عند غياب الجدول فيُتخطى حد القفزة. */
    val totalSessionsLastHour: Int? = null,
    /** أسماء مفاتيح المزودين التي فشل فحصها الحي — تُجمع حياً في
     * [collectAndEvaluate] من المفاتيح المهيأة، ويُضاف إليها أي تجاوز يُمرَّر. */
    val failedApiKeys: List<String> = emptyList(),
    /** نتيجة آخر فحص اتصال بالبوابة (Supabase ping). */
    val gatewayPingOk: Boolean? = null,
    /** عدد فحوصات البوابة الفاشلة المتتالية (من سجل التقييمات). */
    val consecutiveGatewayFailures: Int = 0,
    /** تسجيلات جديدة في الساعة الأخيرة (من users.created_at). */
    val signupsLastHour: Int? = null,
    /** متوسط التسجيلات في الساعة خلال الـ 24 ساعة السابقة. */
    val baselineSignupsPerHour: Double? = null
)

/** نتيجة تقييم كامل: التنبيهات المُطلقة + ما لم يمكن قياسه. */
data class DevAlertEvaluation(
    val timeMs: Long = System.currentTimeMillis(),
    val fired: List<DevAlert> = emptyList(),
    /** حدود لم تُقيَّم لانعدام مصدر القياس (تُعرض كـ «لا بيانات» لا كصفر). */
    val unevaluated: List<String> = emptyList()
)

/**
 * المقيِّم — دالة خالصة (بلا شبكة ولا تخزين) ليسهل اختبارها: تُدخل
 * القياسات فتُخرج التنبيهات. لا تُرسل شيئاً؛ التوصيل في [recordAlertEvaluation].
 */
object DevAlertEvaluator {
    fun evaluate(inputs: DevAlertInputs, nowMs: Long = System.currentTimeMillis()): DevAlertEvaluation {
        val fired = mutableListOf<DevAlert>()
        val unevaluated = mutableListOf<String>()

        // 1) قفزة أعطال: نسبة خالية من الأعطال < 99% في الساعة الأخيرة.
        val crashed = inputs.crashedSessionsLastHour
        val total = inputs.totalSessionsLastHour
        if (crashed == null || total == null) {
            unevaluated += "قفزة الأعطال: لا مصدر لإجمالي الجلسات (يلزم جدول أحداث/جلسات)"
        } else if (total >= DevAlertThresholds.MIN_SESSIONS_FOR_CRASH_ALERT) {
            val crashFree = 100.0 * (total - crashed).coerceAtLeast(0) / total
            if (crashFree < DevAlertThresholds.MIN_CRASH_FREE_RATE_PERCENT) {
                fired += DevAlert(
                    type = AlertType.CRASH_SPIKE,
                    severity = AlertSeverity.CRITICAL,
                    message = "نسبة الجلسات الخالية من الأعطال ${"%.1f".format(crashFree)}% في الساعة الأخيرة",
                    measuredValue = "${"%.1f".format(crashFree)}% خالية ($crashed متعطلة من $total)",
                    thresholdValue = "≥ ${DevAlertThresholds.MIN_CRASH_FREE_RATE_PERCENT}%",
                    timeMs = nowMs
                )
            }
        }

        // 2) فشل مفتاح API: أي مفتاح فشل فحصه الحي.
        if (inputs.failedApiKeys.size >= DevAlertThresholds.FAILED_API_KEYS_TO_ALERT) {
            fired += DevAlert(
                type = AlertType.API_KEY_FAILURE,
                severity = AlertSeverity.CRITICAL,
                message = "فشل الفحص الحي لمفاتيح: ${inputs.failedApiKeys.joinToString("، ")}",
                measuredValue = "${inputs.failedApiKeys.size} مفتاح فاشل",
                thresholdValue = "0 فاشل",
                timeMs = nowMs
            )
        }

        // 3) خطأ بوابة: فشل فحص الاتصال بـ Supabase.
        if (inputs.gatewayPingOk == false) {
            fired += DevAlert(
                type = AlertType.GATEWAY_ERROR,
                severity = if (inputs.consecutiveGatewayFailures >=
                    DevAlertThresholds.GATEWAY_CONSECUTIVE_FAILURES_FOR_CRITICAL
                ) AlertSeverity.CRITICAL else AlertSeverity.WARNING,
                message = "فشل الاتصال ببوابة Supabase (${inputs.consecutiveGatewayFailures} فشل متتالي)",
                measuredValue = "ping فاشل",
                thresholdValue = "ping ناجح",
                timeMs = nowMs
            )
        } else if (inputs.gatewayPingOk == null) {
            unevaluated += "خطأ البوابة: لم يُجرَ فحص اتصال في هذا التقييم"
        }

        // 4) موجة تسجيلات: أكثر من 3× المتوسط وفوق الحد الأدنى المطلق.
        val signups = inputs.signupsLastHour
        val baseline = inputs.baselineSignupsPerHour
        if (signups == null || baseline == null) {
            unevaluated += "موجة التسجيلات: لا بيانات تسجيل كافية"
        } else if (signups >= DevAlertThresholds.SIGNUP_SURGE_MIN_NEW_SIGNUPS &&
            signups > DevAlertThresholds.SIGNUP_SURGE_MULTIPLIER * baseline
        ) {
            fired += DevAlert(
                type = AlertType.SIGNUP_SURGE,
                severity = AlertSeverity.WARNING,
                message = "$signups تسجيلاً جديداً في الساعة الأخيرة (المتوسط ${"%.1f".format(baseline)}/ساعة)",
                measuredValue = "$signups/ساعة",
                thresholdValue = "> ${DevAlertThresholds.SIGNUP_SURGE_MULTIPLIER}× المتوسط وبحد أدنى ${DevAlertThresholds.SIGNUP_SURGE_MIN_NEW_SIGNUPS}",
                timeMs = nowMs
            )
        }

        return DevAlertEvaluation(timeMs = nowMs, fired = fired, unevaluated = unevaluated)
    }
}

private const val ALERTS_TAG = "DevAlerts"
private const val ALERTS_PREFS = "qabas_dev_alerts"
private const val ALERTS_KEY = "alert_evaluations"
private const val MAX_STORED_EVALUATIONS = 100

/**
 * يجمع ما يمكن قياسه اليوم، ثم يقيِّم ويسجِّل. ما لا مصدر له يبقى null
 * ويظهر في التقييم كـ «لا بيانات»:
 * - الأعطال: عدد تقارير قياس الأعطال السحابية في الساعة الأخيرة (مقاس)؛
 *   وإجمالي الجلسات مقاس أيضاً (session_id مميز من analytics_events
 *   في الساعة الأخيرة) فيُقيَّم حد القفزة فعلاً — كان null دائماً قبل ق24
 *   رغم وجود الجدول وكتابته، فلم يُقيَّم الحد أبداً.
 * - المفاتيح: فحص حي متوازٍ للمفاتيح المهيأة (ق24) — كان المدخل فارغاً
 *   دائماً لأن الفحص كان محبوساً داخل DeveloperKeyStatusHub؛ ويُضاف
 *   إليه أي تجاوز يُمرَّر عبر [failedApiKeysOverride].
 * - البوابة: فحص Supabase ping الحي.
 * - التسجيلات: من users.created_at (الساعة الأخيرة مقابل متوسط 24 ساعة).
 */
object DevAlertsRepository {
    suspend fun collectAndEvaluate(
        context: Context,
        failedApiKeysOverride: List<String> = emptyList()
    ): DevAlertEvaluation {
        val now = System.currentTimeMillis()
        val hourAgo = now - 3_600_000L
        val dayAgo = now - 86_400_000L

        val gatewayOk = runCatching { SupabaseServices.Database.ping() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(false)
        val prevEvaluations = readEvaluations(context)
        val consecutiveFailures = if (!gatewayOk) {
            (prevEvaluations.firstOrNull()?.consecutiveGatewayFailures ?: 0) + 1
        } else 0

        // ق55: الفشل هنا null لا صفر — كان getOrDefault(0) يجعل تعذّر
        // قراءة تقارير الأعطال «صفر عطل» فيُخفي قفزة حقيقية، خلاف قاعدة
        // الملف نفسه (null تعني «لا يمكن القياس» فيُتخطى الحد).
        val crashedLastHour = runCatching {
            SupabaseServices.Database.getCrashTelemetryReports().count { report ->
                val ts = report.createdAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
                ts in hourAgo..now
            }
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()

        val totalSessionsLastHour = countSessionsLastHour(now)

        // فشل المفاتيح: فحص حي للمفاتيح المهيأة + أي تجاوز مُمرَّر.
        val liveFailedKeys = collectFailedApiKeys(context)
        val failedApiKeys = (failedApiKeysOverride + liveFailedKeys).distinct()

        // ق55: عدّ التسجيلات باستعلام محدود منذ الأمس (getUserCreatedAtsSince)
        // بدل سحب جدول users كاملاً بـ getAllUsers؛ والفشل null لا (0, 0.0)
        // حتى لا تُقرأ «لا تسجيلات» عند تعذّر القياس.
        val (signupsLastHour, baselinePerHour) = runCatching {
            val createdAts = SupabaseServices.Database
                .getUserCreatedAtsSince(Instant.ofEpochMilli(dayAgo).toString())
                ?.mapNotNull { raw ->
                    runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
                } ?: return@runCatching null to null
            val lastHour = createdAts.count { it in hourAgo..now }
            lastHour to (createdAts.size / 24.0)
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
            ?: (null to null)

        val inputs = DevAlertInputs(
            crashedSessionsLastHour = crashedLastHour,
            totalSessionsLastHour = totalSessionsLastHour,
            failedApiKeys = failedApiKeys,
            gatewayPingOk = gatewayOk,
            consecutiveGatewayFailures = consecutiveFailures,
            signupsLastHour = signupsLastHour,
            baselineSignupsPerHour = baselinePerHour
        )
        val evaluation = DevAlertEvaluator.evaluate(inputs, now)
        recordAlertEvaluation(context, evaluation, consecutiveFailures)
        return evaluation
    }

    /**
     * إجمالي الجلسات في الساعة الأخيرة: session_id مميز من analytics_events.
     * null عند غياب الجدول أو فشل القراءة فيبقى حد القفزة «لا بيانات»
     * (لا صفر مختلق). السقف 2000 صف — التطبيق صغير؛ وإن تجاوزته الأحداث
     * فالعدد حد أدنى صادق (لا ادعاء اكتمال).
     */
    private suspend fun countSessionsLastHour(now: Long): Int? = runCatching {
        val client = SupabaseServices.client ?: return null
        val hourAgoIso = Instant.ofEpochMilli(now - 3_600_000L).toString()
        val rows = client.postgrest.from("analytics_events").select {
            filter { gte("created_at", hourAgoIso) }
            limit(2000)
        }.decodeList<AnalyticsEventRow>()
        rows.mapNotNull { it.sessionId?.takeIf { id -> id.isNotBlank() } }.toSet().size
    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()

    /**
     * أسماء المفاتيح المهيأة التي فشل فحصها الحي — نفس فحص «فحص الكل» في
     * لوحة المفاتيح (ApiKeyValidator + قيم secretKeySpecs)، متوازياً.
     * المفتاح غير المهيأ لا يُفحص أصلاً فلا يُعدّ فاشلاً. أي فشل (شبكة أو
     * مفتاح) يُعاد فارغاً لا استثناءً، حتى لا يُسقط التقييم كاملاً.
     */
    private suspend fun collectFailedApiKeys(context: Context): List<String> = runCatching {
        kotlinx.coroutines.coroutineScope {
            secretKeySpecs.map { spec ->
                kotlinx.coroutines.async {
                    val value = getSecretKeyActualValue(context, spec)
                    if (!isKeyConfigured(value)) return@async null
                    val result = ApiKeyValidator.validateKey(
                        context, spec.serviceType, value, secretKeyHintFor(context, spec)
                    )
                    if (result.isValid) null else spec.envName
                }
            }.awaitAll().filterNotNull()
        }
    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(emptyList())

    /** يسجِّل كل تقييم (أطلق تنبيهاً أم لا) محلياً + التنبيهات المُطلقة في التدقيق. */
    fun recordAlertEvaluation(
        context: Context,
        evaluation: DevAlertEvaluation,
        consecutiveGatewayFailures: Int = 0
    ) {
        runCatching {
            val prefs = context.getSharedPreferences(ALERTS_PREFS, Context.MODE_PRIVATE)
            val arr = JSONArray(prefs.getString(ALERTS_KEY, "[]") ?: "[]")
            arr.put(
                JSONObject()
                    .put("timeMs", evaluation.timeMs)
                    .put("consecutiveGatewayFailures", consecutiveGatewayFailures)
                    .put("fired", JSONArray(evaluation.fired.map { alert ->
                        JSONObject()
                            .put("type", alert.type.name)
                            .put("severity", alert.severity.name)
                            .put("message", alert.message)
                            .put("measured", alert.measuredValue)
                            .put("threshold", alert.thresholdValue)
                    }))
                    .put("unevaluated", JSONArray(evaluation.unevaluated))
            )
            while (arr.length() > MAX_STORED_EVALUATIONS) arr.remove(0)
            prefs.edit().putString(ALERTS_KEY, arr.toString()).apply()
        }.onFailure { Log.w(ALERTS_TAG, "recordAlertEvaluation failed: ${it.message}") }
        evaluation.fired.forEach { alert ->
            AuditLogger.log(context, "dev_alert_fired", "${alert.type.name} [${alert.severity.label}] ${alert.message}")
        }
        Log.d(ALERTS_TAG, "evaluation recorded: ${evaluation.fired.size} fired, ${evaluation.unevaluated.size} unevaluated")
    }

    /** آخر التقييمات المسجلة، الأحدث أولاً. يُستخدم لسلسلة فشل البوابة وللعرض. */
    fun readEvaluations(context: Context): List<StoredAlertEvaluation> {
        return runCatching {
            val prefs = context.getSharedPreferences(ALERTS_PREFS, Context.MODE_PRIVATE)
            val arr = JSONArray(prefs.getString(ALERTS_KEY, "[]") ?: "[]")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val fired = (0 until (o.optJSONArray("fired")?.length() ?: 0)).map { j ->
                    val a = o.getJSONArray("fired").getJSONObject(j)
                    DevAlert(
                        type = runCatching { AlertType.valueOf(a.optString("type")) }.getOrDefault(AlertType.GATEWAY_ERROR),
                        severity = runCatching { AlertSeverity.valueOf(a.optString("severity")) }.getOrDefault(AlertSeverity.WARNING),
                        message = a.optString("message"),
                        measuredValue = a.optString("measured"),
                        thresholdValue = a.optString("threshold"),
                        timeMs = o.optLong("timeMs")
                    )
                }
                val unevaluated = (0 until (o.optJSONArray("unevaluated")?.length() ?: 0)).map { j ->
                    o.getJSONArray("unevaluated").getString(j)
                }
                StoredAlertEvaluation(
                    timeMs = o.optLong("timeMs"),
                    consecutiveGatewayFailures = o.optInt("consecutiveGatewayFailures"),
                    fired = fired,
                    unevaluated = unevaluated
                )
            }.sortedByDescending { it.timeMs }
        }.getOrDefault(emptyList())
    }
}

data class StoredAlertEvaluation(
    val timeMs: Long,
    val consecutiveGatewayFailures: Int,
    val fired: List<DevAlert>,
    val unevaluated: List<String>
)

/**
 * ق90: ملخص اتجاهات سجل التنبيهات — دالة خالصة ليسهل اختبارها:
 * عدد التقييمات والتنبيهات، والمُطلق في آخر 24 ساعة، وعدد كل نوع.
 */
data class AlertHistorySummary(
    val totalEvaluations: Int,
    val totalFired: Int,
    val firedLast24h: Int,
    val perType: Map<AlertType, Int>
)

fun summarizeAlertHistory(
    stored: List<StoredAlertEvaluation>,
    nowMs: Long = System.currentTimeMillis()
): AlertHistorySummary {
    val allFired = stored.flatMap { it.fired }
    val dayAgo = nowMs - 86_400_000L
    return AlertHistorySummary(
        totalEvaluations = stored.size,
        totalFired = allFired.size,
        firedLast24h = allFired.count { it.timeMs in dayAgo..nowMs },
        perType = AlertType.entries.associateWith { type -> allFired.count { it.type == type } }
    )
}

/**
 * ق90: السجل المرئي بعد مرشح الشدة — دالة خالصة: بلا مرشح تُبقى كل
 * تقييم (حتى بلا تنبيهات، فيُرى أنه جرى)؛ ومع مرشح شدة تُبقى
 * تنبيهات تلك الشدة فقط وتُسقط التقييمات التي لم يبقَ فيها شيء.
 */
fun visibleAlertHistory(
    stored: List<StoredAlertEvaluation>,
    severity: AlertSeverity?
): List<Pair<StoredAlertEvaluation, List<DevAlert>>> =
    stored.mapNotNull { evaluation ->
        val shown = if (severity == null) evaluation.fired
            else evaluation.fired.filter { it.severity == severity }
        if (severity != null && shown.isEmpty()) null else evaluation to shown
    }

/** شاشة «التنبيهات»: الحدود الحالية + زر تقييم + قائمة التنبيهات المُطلقة. */
@Composable
fun DevAlertsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lastEvaluation by remember { mutableStateOf<DevAlertEvaluation?>(null) }
    var stored by remember { mutableStateOf(DevAlertsRepository.readEvaluations(context)) }
    var evaluating by remember { mutableStateOf(false) }
    // ق90: مرشح شدة لسجل التنبيهات — كان السجل قائمة مسطحة بلا أي
    // تصفية، فلا يرى المطور الحرجة وحدها وسط التحذيرات.
    var severityFilter by remember { mutableStateOf<AlertSeverity?>(null) }
    // ق80: التقييم عند فتح الشاشة — كان «قيِّم الآن» هو المشغّل
    // الوحيد، فمن يفتح الصفحة يرى سجلاً قديماً (أو «لا تنبيهات
    // مسجلة بعد») بلا تقييمٍ للحالة الراهنة أصلاً. الآن تُقيَّم
    // الحالة مرة واحدة عند الفتح، ويبقى الزر تحديثاً يدوياً.
    // أما التقييم الدوري في الخلفية (WorkManager) فلم يُوصَل عمداً:
    // (1) التوصيل داخل التطبيق فقط بلا مسار دفع/بريد — فلا أحد يرى
    // نتيجة تقييم خلفي حتى يفتح هذه الشاشة، وهي تقيِّم عند فتحها
    // أصلاً؛ (2) التقييم يفحص مفاتيح API حياً ضد مزوديها، فجدولته
    // في الخلفية تستهلك حصة الشبكة والبطارية وتخاطر بحدود معدل
    // المزودين بلا مستفيد؛ (3) كل مستهلِك للتقييم هو هذه الشاشة.
    // إن أُضيف مسار دفع/بريد لاحقاً أُعيد النظر في الجدولة حينها.
    val allowed = AdminGuard.isDashboardAccessAllowed(context)
    var autoEvaluated by remember { mutableStateOf(false) }
    LaunchedEffect(allowed) {
        if (!allowed || autoEvaluated) return@LaunchedEffect
        autoEvaluated = true
        evaluating = true
        try {
            lastEvaluation = DevAlertsRepository.collectAndEvaluate(context)
            stored = DevAlertsRepository.readEvaluations(context)
        } finally {
            evaluating = false
        }
    }

    if (!allowed) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("التنبيهات مقفلة", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
            Text("هذه الصفحة للمطورين المعتمدين فقط (رتبة سحابية).", color = TextSecondary)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("← رجوع") }
            Text("تنبيهات المطور", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = TextPrimary)
            Text(
                "التوصيل داخل التطبيق فقط — لا يوجد مسار دفع أو بريد للمطور في التطبيق اليوم.",
                color = TextSecondary, fontSize = 12.sp
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = DeepSlate)) {
                Column(Modifier.padding(12.dp)) {
                    Text("الحدود الحالية (تُعدَّل من ثوابت DevAlertThresholds في الكود)", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("• قفزة أعطال: جلسات خالية < ${DevAlertThresholds.MIN_CRASH_FREE_RATE_PERCENT}% في ساعة (بحد أدنى ${DevAlertThresholds.MIN_SESSIONS_FOR_CRASH_ALERT} جلسة)", color = TextPrimary, fontSize = 12.sp)
                    Text("• فشل مفتاح API: أي مفتاح يفشل فحصه الحي", color = TextPrimary, fontSize = 12.sp)
                    Text("• خطأ بوابة: فشل ping (حرج عند ${DevAlertThresholds.GATEWAY_CONSECUTIVE_FAILURES_FOR_CRITICAL} فشل متتالٍ)", color = TextPrimary, fontSize = 12.sp)
                    Text("• موجة تسجيلات: > ${DevAlertThresholds.SIGNUP_SURGE_MULTIPLIER}× متوسط الساعة + بحد أدنى ${DevAlertThresholds.SIGNUP_SURGE_MIN_NEW_SIGNUPS} تسجيلات/ساعة", color = TextPrimary, fontSize = 12.sp)
                }
            }
        }
        item {
            Button(
                onClick = {
                    evaluating = true
                    scope.launch {
                        lastEvaluation = DevAlertsRepository.collectAndEvaluate(context)
                        stored = DevAlertsRepository.readEvaluations(context)
                        evaluating = false
                    }
                },
                enabled = !evaluating,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (evaluating) "جارٍ التقييم…" else "قيِّم الآن") }
        }
        lastEvaluation?.let { evaluation ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = DeepSlate)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("آخر تقييم", color = TextPrimary, fontWeight = FontWeight.Bold)
                        // ق55: وقت التقييم كان غائباً — بلا وقت لا يُعرف حداثة النتيجة.
                        Text("الوقت: ${AuditLogger.formatTime(evaluation.timeMs)}", color = TextSecondary, fontSize = 12.sp)
                        if (evaluation.fired.isEmpty()) Text("لا تنبيهات مُطلقة.", color = ThemeSuccess)
                        evaluation.fired.forEach { alert ->
                            Text("• [${alert.severity.label}] ${alert.type.label}: ${alert.message}", color = ThemeWarning, fontSize = 13.sp)
                        }
                        evaluation.unevaluated.forEach { note ->
                            Text("• لا بيانات: $note", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item { Text("سجل التقييمات والتنبيهات", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        // ق90: ملخص الاتجاهات + مرشح الشدة — كان السجل بلا ملخص ولا
        // تصفية، فلا يرى المطور تكرار كل نوع ولا الحرجة وحدها.
        if (stored.isNotEmpty()) {
            item {
                val summary = summarizeAlertHistory(stored)
                Card(colors = CardDefaults.cardColors(containerColor = DeepSlate)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("الملخص", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            "${summary.totalEvaluations} تقييماً مسجلاً • ${summary.totalFired} تنبيهاً مُطلقاً • آخر 24 ساعة: ${summary.firedLast24h}",
                            color = TextSecondary, fontSize = 12.sp
                        )
                        Text(
                            AlertType.entries.joinToString(" • ") { type ->
                                "${type.label}: ${summary.perType[type] ?: 0}"
                            },
                            color = TextSecondary, fontSize = 12.sp
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = severityFilter == null,
                        onClick = { severityFilter = null },
                        label = { Text("الكل") }
                    )
                    FilterChip(
                        selected = severityFilter == AlertSeverity.CRITICAL,
                        onClick = { severityFilter = AlertSeverity.CRITICAL },
                        label = { Text("حرج") }
                    )
                    FilterChip(
                        selected = severityFilter == AlertSeverity.WARNING,
                        onClick = { severityFilter = AlertSeverity.WARNING },
                        label = { Text("تحذير") }
                    )
                }
            }
        }
        // ق90: السجل كان قائمة مسطحة للتنبيهات المُطلقة فقط — التقييم
        // الذي لم يُطلق شيئاً اختفى تماماً فلا يُعرف أنه جرى أصلاً،
        // رغم أن عنوان القسم «سجل التقييمات والتنبيهات»؛ الآن كل
        // تقييم صف بوقته وحالته، والمرشح يخفي التقييمات بلا تنبيه
        // مطابق لشدته فقط.
        val visibleHistory = visibleAlertHistory(stored, severityFilter)
        if (stored.isEmpty()) {
            item { Text("لا تقييمات مسجلة بعد.", color = TextSecondary) }
        } else if (visibleHistory.isEmpty()) {
            item { Text("لا تنبيهات بهذه الشدة في السجل.", color = TextSecondary) }
        } else {
            items(
                visibleHistory,
                key = { (evaluation, _) -> "${evaluation.timeMs}-${evaluation.fired.hashCode()}" }
            ) { (evaluation, shown) ->
                Card(colors = CardDefaults.cardColors(containerColor = ThemeSecondarySurface)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(
                            "تقييم ${AuditLogger.formatTime(evaluation.timeMs)}",
                            color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp
                        )
                        if (shown.isEmpty()) {
                            Text("لا تنبيهات مُطلقة في هذا التقييم.", color = ThemeSuccess, fontSize = 12.sp)
                        }
                        shown.forEach { alert ->
                            Text("[${alert.severity.label}] ${alert.type.label}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(alert.message, color = TextPrimary, fontSize = 12.sp)
                            Text("القياس: ${alert.measuredValue} — الحد: ${alert.thresholdValue}", color = TextSecondary, fontSize = 11.sp)
                        }
                        evaluation.unevaluated.forEach { note ->
                            Text("• لا بيانات: $note", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
