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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    /** جلسات متعطلة في الساعة الأخيرة (من تقارير قياس الأعطال السحابية). */
    val crashedSessionsLastHour: Int? = null,
    /** إجمالي الجلسات في الساعة الأخيرة — لا يوجد مصدر جلسات في التطبيق */
    /** (لا جدول analytics_events ولا عدّاد جلسات)، فيبقى null حتى يُضاف. */
    val totalSessionsLastHour: Int? = null,
    /** أسماء مفاتيح المزودين التي فشل فحصها الحي (تُمرَّر من فاحص المفاتيح). */
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
 *   إجمالي الجلسات غير مقاس (لا مصدر) فلا يُقيَّم حد القفزة.
 * - المفاتيح: لا مجمِّع برمجي لنتائج الفحص الحي اليوم — يُمرَّر عبر
 *   [failedApiKeysOverride] من شاشة حالة المفاتيح عند توفره.
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

        val crashedLastHour = runCatching {
            SupabaseServices.Database.getCrashTelemetryReports().count { report ->
                val ts = report.createdAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
                ts in hourAgo..now
            }
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(0)

        val (signupsLastHour, baselinePerHour) = runCatching {
            val createdAts = SupabaseServices.Database.getAllUsers().mapNotNull { row ->
                row.createdAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            }
            val lastHour = createdAts.count { it in hourAgo..now }
            val lastDay = createdAts.count { it in dayAgo..now }
            lastHour to (lastDay / 24.0)
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(0 to 0.0)

        val inputs = DevAlertInputs(
            crashedSessionsLastHour = crashedLastHour,
            totalSessionsLastHour = null, // لا مصدر لإجمالي الجلسات — انظر تعليق الحقل.
            failedApiKeys = failedApiKeysOverride,
            gatewayPingOk = gatewayOk,
            consecutiveGatewayFailures = consecutiveFailures,
            signupsLastHour = signupsLastHour,
            baselineSignupsPerHour = baselinePerHour
        )
        val evaluation = DevAlertEvaluator.evaluate(inputs, now)
        recordAlertEvaluation(context, evaluation, consecutiveFailures)
        return evaluation
    }

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

/** شاشة «التنبيهات»: الحدود الحالية + زر تقييم + قائمة التنبيهات المُطلقة. */
@Composable
fun DevAlertsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lastEvaluation by remember { mutableStateOf<DevAlertEvaluation?>(null) }
    var stored by remember { mutableStateOf(DevAlertsRepository.readEvaluations(context)) }
    var evaluating by remember { mutableStateOf(false) }

    if (!AdminGuard.isDashboardAccessAllowed(context)) {
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
        val allFired = stored.flatMap { it.fired }
        if (allFired.isEmpty()) {
            item { Text("لا تنبيهات مسجلة بعد.", color = TextSecondary) }
        } else {
            items(allFired) { alert ->
                Card(colors = CardDefaults.cardColors(containerColor = ThemeSecondarySurface)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("[${alert.severity.label}] ${alert.type.label}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(alert.message, color = TextPrimary, fontSize = 12.sp)
                            Text("القياس: ${alert.measuredValue} — الحد: ${alert.thresholdValue}", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
