package com.qabas.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * تتبع تكلفة واستهلاك نماذج الذكاء الاصطناعي (12.14).
 *
 * الأسعار: دولار لكل مليون رمز (إدخال/إخراج)، مُتحقق منها حياً من
 * صفحات التسعير الرسمية للمزودين ومن واجهة OpenRouter العامة
 * (api/v1/models) بتاريخ [VibeModelPrices.VERIFIED_ON]، وكل سعر معروض
 * في الواجهة موسوم بـ«تقريبي». أي نموذج بلا سعر موثق تبقى تكلفته
 * «غير مقاسة» — لا رقم مخمّن أبداً. كذلك أي استدعاء رموزه تقديرية
 * (بثّ بلا usage حقيقي، أو مسار Firebase الذي يعيد أعداداً ثابتة
 * 500/250/250) يبقى «غير مقاس» مهما كان سعر النموذج معروفاً.
 *
 * التخزين: SharedPreferences عادية «qabas_vibe_cost_v1» (بيانات
 * استهلاك محلية لا أسرار فيها)، مصفوفة JSON بحد أقصى 300 قيد.
 * لا يُعرض Project.cost هنا — يبقى «غير مسجلة» حتى قياس حقيقي.
 */
data class ModelPrice(
    val inputUsdPerMillion: Double,
    val outputUsdPerMillion: Double,
    val source: String
)

object VibeModelPrices {
    /** تاريخ التحقق الحي من الأسعار. */
    const val VERIFIED_ON = "2026-11-23"

    val PRICES: Map<String, ModelPrice> = mapOf(
        // Google AI — تحقق حي 2026-11-23.
        "gemini-2.5-flash" to ModelPrice(0.30, 2.50, "Google AI"),
        // Google AI — حتى 2026-12-31، ثم 1.50/7.50 من 2027-01-01.
        "gemini-3.8-flash" to ModelPrice(0.75, 3.75, "Google AI (حتى 2026-12-31)"),
        // Google AI — مُهمل؛ الطلبات تُحوَّل تلقائياً إلى gemini-3.6-flash.
        "gemini-3.5-flash" to ModelPrice(1.50, 9.00, "Google AI"),
        // OpenAI — تحقق حي 2026-11-23 (وأكده OpenRouter).
        "gpt-4o" to ModelPrice(2.50, 10.00, "OpenAI"),
        "gpt-4o-mini" to ModelPrice(0.15, 0.60, "OpenAI"),
        // Anthropic — تحقق حي 2026-11-23 (وأكده OpenRouter لـ claude-sonnet-4.5).
        "claude-sonnet-4.5" to ModelPrice(3.00, 15.00, "Anthropic"),
        "claude-3.5-sonnet" to ModelPrice(3.00, 15.00, "Anthropic"),
        "claude-3.5-haiku" to ModelPrice(0.80, 4.00, "Anthropic"),
        // AWS Bedrock — الصفحة الرسمية لذلك المعرف الدقيق؛ صفحة
        // Anthropic نفسها لم تعد تسرد هذا المعرف.
        "claude-3-5-sonnet-20241022" to ModelPrice(3.00, 15.00, "AWS Bedrock"),
        // OpenRouter — تحقق حي عبر api/v1/models بتاريخ 2026-11-22.
        "anthropic/claude-sonnet-4.5" to ModelPrice(3.00, 15.00, "OpenRouter"),
        "openai/gpt-4o" to ModelPrice(2.50, 10.00, "OpenRouter")
    )

    fun priceFor(model: String): ModelPrice? = PRICES[model]
}

data class VibeUsageEntry(
    val id: String,
    val timestamp: Long,
    val providerId: String,
    val model: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val tokensEstimated: Boolean,
    val costUsd: Double?,
    val costUnknownReason: String?,
    val missionTitle: String?
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("timestamp", timestamp)
        put("providerId", providerId)
        put("model", model)
        put("promptTokens", promptTokens)
        put("completionTokens", completionTokens)
        put("totalTokens", totalTokens)
        put("tokensEstimated", tokensEstimated)
        if (costUsd != null) put("costUsd", costUsd) else put("costUsd", JSONObject.NULL)
        if (costUnknownReason != null) put("costUnknownReason", costUnknownReason)
        if (missionTitle != null) put("missionTitle", missionTitle)
    }

    companion object {
        fun fromJson(json: JSONObject): VibeUsageEntry = VibeUsageEntry(
            id = json.optString("id"),
            timestamp = json.optLong("timestamp"),
            providerId = json.optString("providerId"),
            model = json.optString("model"),
            promptTokens = json.optInt("promptTokens"),
            completionTokens = json.optInt("completionTokens"),
            totalTokens = json.optInt("totalTokens"),
            tokensEstimated = json.optBoolean("tokensEstimated"),
            costUsd = if (json.isNull("costUsd")) null else json.optDouble("costUsd"),
            costUnknownReason = json.optString("costUnknownReason").takeIf { it.isNotBlank() },
            missionTitle = json.optString("missionTitle").takeIf { it.isNotBlank() }
        )
    }
}

data class VibeCostAggregate(
    val calls: Int,
    val tokens: Int,
    val costUsd: Double,
    val unmeasuredCalls: Int
)

data class VibeCostSnapshot(
    val totalCalls: Int,
    val totalTokens: Int,
    val measuredCalls: Int,
    val unmeasuredCalls: Int,
    val unmeasuredByReason: Map<String, Int>,
    val spentUsd: Double,
    val byProvider: Map<String, VibeCostAggregate>,
    val byModel: Map<String, VibeCostAggregate>,
    val byMission: Map<String, VibeCostAggregate>
)

object VibeCostTracker {
    private const val PREFS_NAME = "qabas_vibe_cost_v1"
    private const val KEY_ENTRIES = "entries_json"
    private const val MAX_ENTRIES = 300

    fun formatUsd(value: Double): String =
        String.format(java.util.Locale.US, "$%.4f", value)

    /**
     * تسجيل استدعاء واحد. التكلفة تُحسب فقط عند توفر سعر موثق للنموذج
     * ورموز حقيقية مفصلة (إدخال/إخراج)؛ وإلا تبقى null مع سبب صادق.
     */
    fun record(
        context: Context,
        providerId: String,
        model: String,
        response: RealAgentResponse,
        missionTitle: String?
    ): VibeUsageEntry {
        val price = VibeModelPrices.priceFor(model)
        val (cost, reason) = when {
            response.tokensEstimated -> null to "رموز تقديرية"
            response.promptTokens + response.completionTokens <= 0 -> null to "بلا تفصيل رموز"
            price == null -> null to "بلا سعر موثق"
            else -> (
                (response.promptTokens * price.inputUsdPerMillion +
                    response.completionTokens * price.outputUsdPerMillion) / 1_000_000.0
                ) to null
        }
        val entry = VibeUsageEntry(
            id = java.util.UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            providerId = providerId,
            model = model,
            promptTokens = response.promptTokens,
            completionTokens = response.completionTokens,
            totalTokens = response.totalTokens,
            tokensEstimated = response.tokensEstimated,
            costUsd = cost,
            costUnknownReason = reason,
            missionTitle = missionTitle?.take(120)
        )
        save(context, (listOf(entry) + entries(context)).take(MAX_ENTRIES))
        return entry
    }

    fun entries(context: Context): List<VibeUsageEntry> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                runCatching { VibeUsageEntry.fromJson(arr.getJSONObject(i)) }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    fun snapshot(context: Context): VibeCostSnapshot {
        val all = entries(context)
        fun aggregate(items: List<VibeUsageEntry>): VibeCostAggregate = VibeCostAggregate(
            calls = items.size,
            tokens = items.sumOf { it.totalTokens },
            costUsd = items.sumOf { it.costUsd ?: 0.0 },
            unmeasuredCalls = items.count { it.costUsd == null }
        )
        return VibeCostSnapshot(
            totalCalls = all.size,
            totalTokens = all.sumOf { it.totalTokens },
            measuredCalls = all.count { it.costUsd != null },
            unmeasuredCalls = all.count { it.costUsd == null },
            unmeasuredByReason = all.mapNotNull { it.costUnknownReason }.groupingBy { it }.eachCount(),
            spentUsd = all.sumOf { it.costUsd ?: 0.0 },
            byProvider = all.groupBy { it.providerId }.mapValues { (_, v) -> aggregate(v) },
            byModel = all.groupBy { it.model }.mapValues { (_, v) -> aggregate(v) },
            byMission = all.filter { !it.missionTitle.isNullOrBlank() }
                .groupBy { it.missionTitle!! }.mapValues { (_, v) -> aggregate(v) }
        )
    }

    /** مجموع التكلفة المقاسة فقط (دولار). */
    fun spentUsd(context: Context): Double = snapshot(context).spentUsd

    /**
     * فحص حد التكلفة (AgentPrefs.getCostLimitUsd): يُعيد رسالة الرفض
     * عند بلوغ الحد أو تجاوزه، وإلا null. الحد 0.0 يوقف فوراً بالتصميم.
     * الحد يطبق على الاستهلاك المقاس فقط — نماذج بلا سعر موثق أو رموز
     * تقديرية لا تُحتسب فيه (وتُعرض «غير مقاسة» في اللوحة).
     */
    fun checkLimit(context: Context): String? {
        val limit = AgentPrefs.getCostLimitUsd(context) ?: return null
        val spent = spentUsd(context)
        return if (spent >= limit) {
            "بلغت التكلفة المقاسة ${formatUsd(spent)} دولاراً، والحد المضبوط ${formatUsd(limit)} دولاراً — " +
                "توقف التنفيذ. ارفع الحد من تبويب التحكم للمتابعة. " +
                "(الحد يطبق على الاستهلاك المقاس فقط — استدعاءات «غير مقاسة» لا تُحتسب فيه.)"
        } else {
            null
        }
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_ENTRIES).apply()
    }

    private fun save(context: Context, all: List<VibeUsageEntry>) {
        val arr = JSONArray()
        all.forEach { arr.put(it.toJson()) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_ENTRIES, arr.toString()).apply()
    }
}
