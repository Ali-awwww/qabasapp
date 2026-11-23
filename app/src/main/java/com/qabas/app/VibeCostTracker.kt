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
 *
 * إجمالي العمر (ق44): السجل الـ 300 قيد هو سجل العرض والتفصيل فقط؛
 * أما حد التكلفة و«الإنفاق المقاس» فيقرآن عدادات تراكمية لا تُمحى
 * بتقادم السجل (تُبذَر مرة واحدة من القيود المحفوظة عند أول قراءة
 * بعد التحديث، ثم تزيد مع كل استدعاء). بلا هذا كان بلوغ 300
 * استدعاء يُسقط الإنفاق القديم من المجموع فيتوقف الحد عن الإيقاف.
 * إعادة التعيين من لوحة التحكم تصفّر القيود والعدادات معاً — فعل
 * صريح من مالك الحد، لا محو صامت.
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
        // Google AI — آخر سعر رسمي منشور (أُزيل من قائمة الأسعار الرسمية
        // في 2026-10، والسعر $0.10/$0.40 ثابت في كل المتتبعات).
        "gemini-2.0-flash" to ModelPrice(0.10, 0.40, "Google AI (آخر سعر رسمي منشور)"),
        // Google AI — حتى 2026-12-31، ثم 1.50/7.50 من 2027-01-01.
        "gemini-3.8-flash" to ModelPrice(0.75, 3.75, "Google AI (حتى 2026-12-31)"),
        // Google AI — مُهمل؛ الطلبات تُحوَّل تلقائياً إلى gemini-3.6-flash.
        "gemini-3.5-flash" to ModelPrice(1.50, 9.00, "Google AI"),
        // OpenRouter — تحقق حي عبر api/v1/models بتاريخ 2026-11-22.
        "anthropic/claude-sonnet-4.5" to ModelPrice(3.00, 15.00, "OpenRouter"),
        "openai/gpt-4o" to ModelPrice(2.50, 10.00, "OpenRouter"),
        // ق44 — تحقق حي 2026-11-23: Groq من صفحة تسعيرها الرسمية،
        // وOpenRouter من api/v1/models. بلا هذه الأسعار كانت كل
        // استدعاءات Groq (المزود الافتراضي السريع) وgpt-4o-mini
        // وllama-3.3-70b-instruct «غير مقاسة» وخارج الحد تماماً.
        // ملاحظة Groq: النموذجان متقاعدان لمطوري Groq منذ 2026-08-16
        // (Contact Sales) فالسعر هو آخر سعر رسمي منشور لهما.
        "llama-3.3-70b-versatile" to ModelPrice(0.59, 0.79, "Groq (آخر سعر رسمي منشور)"),
        "llama-3.1-8b-instant" to ModelPrice(0.05, 0.08, "Groq (آخر سعر رسمي منشور)"),
        "openai/gpt-4o-mini" to ModelPrice(0.15, 0.60, "OpenRouter"),
        "meta-llama/llama-3.3-70b-instruct" to ModelPrice(0.71, 0.71, "OpenRouter")
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
    val byMission: Map<String, VibeCostAggregate>,
    /** إجمالي العمر التراكمي (ق44): لا يتأثر بحد الـ 300 قيد. */
    val lifetimeCalls: Int,
    val lifetimeTokens: Long,
    val lifetimeSpentUsd: Double,
    val lifetimeUnmeasuredCalls: Int
)

/** عدادات تراكمية لا تُمحى بتقادم السجل (ق44). */
data class VibeLifetimeTotals(
    val calls: Int,
    val tokens: Long,
    val spentUsd: Double,
    val unmeasuredCalls: Int
)

object VibeCostTracker {
    private const val PREFS_NAME = "qabas_vibe_cost_v1"
    private const val KEY_ENTRIES = "entries_json"
    private const val MAX_ENTRIES = 300

    /** مفاتيح عدادات العمر التراكمية (ق44). */
    private const val KEY_LIFETIME_SPENT = "lifetime_spent_usd"
    private const val KEY_LIFETIME_CALLS = "lifetime_calls"
    private const val KEY_LIFETIME_TOKENS = "lifetime_tokens"
    private const val KEY_LIFETIME_UNMEASURED = "lifetime_unmeasured"
    private const val KEY_LIFETIME_SEEDED = "lifetime_seeded_v1"

    /** قفل القراءة-التعديل-الكتابة: record() يُستدعى من خيوط IO متزامنة. */
    private val lock = Any()

    fun formatUsd(value: Double): String =
        String.format(java.util.Locale.US, "$%.4f", value)

    /**
     * تسجيل استدعاء واحد. التكلفة تُحسب فقط عند توفر سعر موثق للنموذج
     * ورموز حقيقية مفصلة (إدخال/إخراج)؛ وإلا تبقى null مع سبب صادق.
     * يزيد عدادات العمر التراكمية مع كل قيد (ق44) فلا يسقط الإنفاق
     * القديم من الحد ولا من اللوحة عند تقادم السجل الـ 300.
     */
    fun record(
        context: Context,
        providerId: String,
        model: String,
        response: RealAgentResponse,
        missionTitle: String?
    ): VibeUsageEntry {
        synchronized(lock) {
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
            ensureLifetimeSeededLocked(context)
            save(context, (listOf(entry) + entries(context)).take(MAX_ENTRIES))
            addToLifetimeLocked(context, entry)
            return entry
        }
    }

    /**
     * تسجيل استدعاء خارج مسار الوكيل (Firebase AI Logic أو بديل مباشر)
     * بلا usage حقيقي: الرموز تقدير طول/4 فتبقى «غير مقاسة» مهما كان
     * سعر النموذج معروفاً — لكن الاستدعاء نفسه يُحصى في اللوحة بدل
     * أن يمر بلا أثر (ق44).
     */
    fun recordEstimated(
        context: Context,
        providerId: String,
        model: String,
        prompt: String,
        text: String,
        missionTitle: String?
    ): VibeUsageEntry {
        val total = ((prompt.length + text.length) / 4).coerceAtLeast(150)
        return record(context, providerId, model, RealAgentResponse(text, total, tokensEstimated = true), missionTitle)
    }

    /** عدادات العمر التراكمية؛ تُبذَر مرة واحدة من القيود المحفوظة. */
    fun lifetimeTotals(context: Context): VibeLifetimeTotals {
        synchronized(lock) {
            ensureLifetimeSeededLocked(context)
            return readLifetimeLocked(context)
        }
    }

    private fun readLifetimeLocked(context: Context): VibeLifetimeTotals {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return VibeLifetimeTotals(
            calls = prefs.getInt(KEY_LIFETIME_CALLS, 0),
            tokens = prefs.getLong(KEY_LIFETIME_TOKENS, 0L),
            spentUsd = prefs.getString(KEY_LIFETIME_SPENT, null)?.toDoubleOrNull() ?: 0.0,
            unmeasuredCalls = prefs.getInt(KEY_LIFETIME_UNMEASURED, 0)
        )
    }

    private fun ensureLifetimeSeededLocked(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_LIFETIME_SEEDED, false)) return
        // بذر لمرة واحدة من القيود المحفوظة (أفضل استرداد ممكن — ما
        // تقادم قبل التحديث لا سبيل لاستعادته).
        val all = entries(context)
        prefs.edit()
            .putInt(KEY_LIFETIME_CALLS, all.size)
            .putLong(KEY_LIFETIME_TOKENS, all.sumOf { it.totalTokens.toLong() })
            .putString(KEY_LIFETIME_SPENT, all.sumOf { it.costUsd ?: 0.0 }.toString())
            .putInt(KEY_LIFETIME_UNMEASURED, all.count { it.costUsd == null })
            .putBoolean(KEY_LIFETIME_SEEDED, true)
            .apply()
    }

    private fun addToLifetimeLocked(context: Context, entry: VibeUsageEntry) {
        val current = readLifetimeLocked(context)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_LIFETIME_CALLS, current.calls + 1)
            .putLong(KEY_LIFETIME_TOKENS, current.tokens + entry.totalTokens)
            .putString(KEY_LIFETIME_SPENT, (current.spentUsd + (entry.costUsd ?: 0.0)).toString())
            .putInt(KEY_LIFETIME_UNMEASURED, current.unmeasuredCalls + if (entry.costUsd == null) 1 else 0)
            .apply()
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
        synchronized(lock) {
            val all = entries(context)
            fun aggregate(items: List<VibeUsageEntry>): VibeCostAggregate = VibeCostAggregate(
                calls = items.size,
                tokens = items.sumOf { it.totalTokens },
                costUsd = items.sumOf { it.costUsd ?: 0.0 },
                unmeasuredCalls = items.count { it.costUsd == null }
            )
            ensureLifetimeSeededLocked(context)
            val lifetime = readLifetimeLocked(context)
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
                    .groupBy { it.missionTitle!! }.mapValues { (_, v) -> aggregate(v) },
                lifetimeCalls = lifetime.calls,
                lifetimeTokens = lifetime.tokens,
                lifetimeSpentUsd = lifetime.spentUsd,
                lifetimeUnmeasuredCalls = lifetime.unmeasuredCalls
            )
        }
    }

    /** مجموع التكلفة المقاسة فقط (دولار) — إجمالي العمر (ق44). */
    fun spentUsd(context: Context): Double = lifetimeTotals(context).spentUsd

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

    /**
     * إعادة تعيين سجل الاستهلاك كاملاً: القيود والعدادات التراكمية
     * معاً — فعل صريح من مالك الحد من لوحة التحكم (ق44).
     */
    fun reset(context: Context) {
        synchronized(lock) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_ENTRIES)
                .remove(KEY_LIFETIME_SPENT)
                .remove(KEY_LIFETIME_CALLS)
                .remove(KEY_LIFETIME_TOKENS)
                .remove(KEY_LIFETIME_UNMEASURED)
                .remove(KEY_LIFETIME_SEEDED)
                .apply()
        }
    }

    private fun save(context: Context, all: List<VibeUsageEntry>) {
        val arr = JSONArray()
        all.forEach { arr.put(it.toJson()) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_ENTRIES, arr.toString()).apply()
    }
}
