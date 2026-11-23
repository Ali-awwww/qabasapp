package com.qabas.app

import java.time.Instant

/**
 * ترتيب «لك» في الريلز بالنقاط (الدفعة 3).
 *
 * النقاط = إكمال ×3 + مشاركة ×5 + حفظ ×4 + تعليق ×2 + إعجاب ×1،
 * ثم تقادم زمني 1/(1+ageDays/7) ينصّف قيمة المقطع بعد 7 أيام،
 * ثم تنويع: لا مقطعان متتاليان من الصانع نفسه أو الموضوع نفسه.
 *
 * حدود صريحة موثقة (لا تُخفى):
 * - لا عمود عدّاد حفظ إجمالي ولا معدل إكمال في قاعدة البيانات حتى الآن،
 *   فهذان الوزنان يستخدمان بيانات المشاهد نفسه فقط: محفوظ؟ 1/0،
 *   وثواني مشاهدته هو (مقيسة نسبياً لأطول مشاهدة في الصفحات المحمّلة).
 * - الترتيب على العميل فوق الصفحات المحمّلة فقط؛ الخادم ما زال يرجع
 *   المقاطع زمنياً 20/صفحة. مع 7 مقاطع حالياً المجموعة المحمّلة هي
 *   الخلاصة كلها، فالترتيب كامل عملياً.
 * - كل العدادات (إعجاب/تعليق/مشاركة/مشاهدة) من صف المقطع في قاعدة
 *   البيانات؛ لا رقم مختلق في أي مكان.
 * - استثناء موثق (review-r15): عدّاد المشاركة في الصف لا يزيده أي فعل
 *   في التطبيق حتى الآن — لا مسار خادم للمشاركة (لا RPC ولا جدول علاقة
 *   كما في الإعجاب/المشاهدة) — فوزن المشاركة ×5 خامل عملياً (كل القيم
 *   صفر) حتى تُضاف دالة record_reel_share؛ لذلك لا يُعرض عدّاد
 *   المشاركة في واجهة المقطع.
 */
object ReelFeedScoring {

    const val COMPLETION_WEIGHT = 3.0
    const val SHARE_WEIGHT = 5.0
    const val SAVE_WEIGHT = 4.0
    const val COMMENT_WEIGHT = 2.0
    const val LIKE_WEIGHT = 1.0

    /** أيام التقادم التي تنصّف عندها قيمة المقطع. */
    const val DECAY_HALF_LIFE_DAYS = 7.0

    /** إشارات المشاهد نفسه لمقطع واحد (لا شيء منها من خادم آخر). */
    data class ViewerSignals(
        /** أعجب به المشاهد (من مزامنة الإعجابات السحابية). */
        val liked: Boolean = false,
        /** حفظه المشاهد (من المحفوظات السحابية). */
        val saved: Boolean = false,
        /** ثواني التشغيل الفعلية التي شاهدها هو لهذا المقطع. */
        val watchSeconds: Int = 0,
        /** تعليقات أضافها هو في هذه الجلسة (تُدمج مع عدّاد الصف). */
        val commentsAdded: Int = 0
    )

    /** عمر المقطع بالأيام من created_at؛ غير معروف التاريخ = 0 (بلا تقادم). */
    fun ageDays(reel: SupabaseServices.ReelRow, nowMillis: Long = System.currentTimeMillis()): Double {
        val created = runCatching { Instant.parse(reel.createdAt) }.getOrNull() ?: return 0.0
        return ((nowMillis - created.toEpochMilli()).coerceAtLeast(0L) / 86_400_000.0)
    }

    /** معامل التقادم: 1 عند النشر، وينصف بعد [DECAY_HALF_LIFE_DAYS] أيام. */
    fun decayFactor(ageDays: Double): Double = 1.0 / (1.0 + ageDays / DECAY_HALF_LIFE_DAYS)

    /**
     * نقاط مقطع واحد قبل التنويع.
     * الإكمال كسر نسبي 0..1: ثواني مشاهدة المشاهد لهذا المقطع مقسومة على
     * أطول مشاهدة له في الصفحات المحمّلة ([maxWatchSeconds])؛ بلا مدة
     * مقطع في قاعدة البيانات لا نسبة إكمال مطلقة صادقة.
     */
    fun scoreReel(
        reel: SupabaseServices.ReelRow,
        signals: ViewerSignals = ViewerSignals(),
        maxWatchSeconds: Int = 0,
        nowMillis: Long = System.currentTimeMillis()
    ): Double {
        val completion = if (maxWatchSeconds > 0) {
            (signals.watchSeconds.toDouble() / maxWatchSeconds).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        val raw = completion * COMPLETION_WEIGHT +
            reel.sharesCount.coerceAtLeast(0) * SHARE_WEIGHT +
            (if (signals.saved) 1.0 else 0.0) * SAVE_WEIGHT +
            (reel.commentsCount.coerceAtLeast(0) + signals.commentsAdded.coerceAtLeast(0)) * COMMENT_WEIGHT +
            reel.likesCount.coerceAtLeast(0) * LIKE_WEIGHT
        return raw * decayFactor(ageDays(reel, nowMillis))
    }

    /** مفتاح الصانع للتنويع: معرّف المستخدم، أو الاسم عند غيابه. */
    fun creatorKey(reel: SupabaseServices.ReelRow): String =
        reel.userId.ifBlank { reel.userName }

    /** مفتاح الموضوع للتنويع: أول وسم، أو فئة مستنتجة من العنوان عند غياب الوسوم. */
    fun topicKey(reel: SupabaseServices.ReelRow): String {
        val firstTag = reel.tags.firstOrNull()?.trim().orEmpty()
        if (firstTag.isNotBlank()) return firstTag
        return when {
            reel.title.contains("قرآن") || reel.title.contains("تلاوة") -> "قرآن"
            reel.title.contains("حديث") || reel.title.contains("سنة") -> "حديث"
            reel.title.contains("قصة") || reel.title.contains("عبرة") -> "قصص"
            else -> ""
        }
    }

    /**
     * ترتيب «لك»: الأعلى نقاطاً أولاً، مع تنويع جشع يمنع مقطعين متتاليين
     * من الصانع نفسه أو الموضوع نفسه متى أمكن (عندما تبقّى بديل مختلف).
     * الترتيب حتمي لنفس المدخلات: كسر التعادل بمعرّف المقطع.
     */
    fun sortForYou(
        reels: List<SupabaseServices.ReelRow>,
        signalsById: Map<String, ViewerSignals> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis()
    ): List<SupabaseServices.ReelRow> {
        if (reels.size <= 1) return reels
        val maxWatch = signalsById.values.maxOfOrNull { it.watchSeconds } ?: 0
        val scored = reels.map { reel ->
            reel to scoreReel(reel, signalsById[reel.id] ?: ViewerSignals(), maxWatch, nowMillis)
        }.sortedWith(
            compareByDescending<Pair<SupabaseServices.ReelRow, Double>> { it.second }
                .thenBy { it.first.id }
        )
        val remaining = scored.toMutableList()
        val ordered = ArrayList<SupabaseServices.ReelRow>(reels.size)
        var lastCreator: String? = null
        var lastTopic: String? = null
        while (remaining.isNotEmpty()) {
            val pickIndex = remaining.indexOfFirst { (reel, _) ->
                creatorKey(reel) != lastCreator && topicKey(reel) != lastTopic
            }.takeIf { it >= 0 } ?: 0
            val (picked, _) = remaining.removeAt(pickIndex)
            ordered.add(picked)
            lastCreator = creatorKey(picked)
            lastTopic = topicKey(picked)
        }
        return ordered
    }
}
