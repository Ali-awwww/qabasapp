package com.qabas.app

import java.time.Instant
import kotlin.math.ln

/**
 * ترتيب «لك» في الريلز بالنقاط (الدفعة 3؛ رُوجع في ق42).
 *
 * النقاط = إكمال ×3 + ln(1+مشاركات) ×5 + حفظ ×4 + ln(1+تعليقات) ×2
 *   + ln(1+إعجابات) ×1، ثم تقادم زمني 1/(1+ageDays/7) ينصّف قيمة
 *   المقطع بعد 7 أيام، ثم تنويع: لا مقطعان متتاليان من الصانع نفسه
 *   أو الموضوع نفسه.
 *
 * لماذا اللوغاريتم (ق42): الصيغة الخطية السابقة (إعجاب ×1 لكل إعجاب)
 * كانت تجعل العدادات المجمعة تبتلع إشارات المشاهد كلها — مقطع بـ 500
 * إعجاب يساوي 500 نقطة مقابل 4 نقاط لحفظ المشاهد نفسه — فصار ترتيب
 * «لك» شعبيةً بحتة لا تخصيصاً. بضغط ln(1+n) يبقى ترتيب الأوزان
 * (مشاركة > حفظ > إكمال > تعليق > إعجاب) لكن مقطعاً حفظه المشاهد
 * وشاهده كاملاً (7 نقاط) يتفوق على مقطع بـ 100 إعجاب بلا إشارة
 * شخصية (4.6)، ولا يتفوق عليه إلا مقطع شبه فيروسي (~10 آلاف إعجاب).
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
 * - عدّاد المشاركة حي منذ ق42: دالة الخادم record_reel_share (مرة
 *   واحدة لكل مستخدم/مقطع، على غرار record_reel_view) تزيده عند
 *   المشاركة، ويُعرض في السكة الجانبية؛ يبقى متجمداً على قيمة الصف
 *   حتى يُطبَّق ترحيلها في Supabase.
 */
object ReelFeedScoring {

    const val COMPLETION_WEIGHT = 3.0
    const val SHARE_WEIGHT = 5.0
    const val SAVE_WEIGHT = 4.0
    const val COMMENT_WEIGHT = 2.0
    const val LIKE_WEIGHT = 1.0

    /** أيام التقادم التي تنصّف عندها قيمة المقطع. */
    const val DECAY_HALF_LIFE_DAYS = 7.0

    /** إشارات المشاهد نفسه لمقطع واحد (لا شيء منها من خادم آخر).
     *
     * ملاحظة (ق42): لا حقل «أعجب به» هنا — إعجاب المشاهد يصل أصلاً إلى
     * النقاط عبر likesCount نفسه (التحديث المتفائل في ReelsScreen يزيده
     * فوراً ويعيده عند فشل الحفظ السحابي)، فحقل منفصل كان سيضاعف
     * احتساب الإعجاب نفسه. */
    data class ViewerSignals(
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
     * ضغط لوغاريتمي للعدادات المجمعة غير المحدودة (ق42): ln(1+n) —
     * 0 عند الصفر، و~4.6 عند 100، و~9.2 عند 10 آلاف — فيبقى وزن
     * الإشارة الشخصية (إكمال ≤3، حفظ 4) ذا معنى مهما كبرت العدادات.
     */
    fun compressedCount(count: Int): Double =
        ln(1.0 + count.coerceAtLeast(0).toDouble())

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
            compressedCount(reel.sharesCount) * SHARE_WEIGHT +
            (if (signals.saved) 1.0 else 0.0) * SAVE_WEIGHT +
            compressedCount(reel.commentsCount + signals.commentsAdded.coerceAtLeast(0)) * COMMENT_WEIGHT +
            compressedCount(reel.likesCount) * LIKE_WEIGHT
        return raw * decayFactor(ageDays(reel, nowMillis))
    }

    /** مفتاح الصانع للتنويع: معرّف المستخدم، أو الاسم عند غيابه.
     *
     * ق42: الاسم الافتراضي «صانع محتوى» ليس هوية — صانعان مجهولا
     * المعرّف يحملانه معاً كانا يُعاملان صانعاً واحداً فيُبعَد أحدهما
     * ظلماً؛ فعند غياب معرّف حقيقي واسم حقيقي يُعطى كل مقطع مفتاحاً
     * خاصاً به (لا تنويع خاطئ خير من تنويع على هوية وهمية). */
    fun creatorKey(reel: SupabaseServices.ReelRow): String {
        if (reel.userId.isNotBlank()) return reel.userId
        val name = reel.userName.trim()
        if (name.isNotBlank() && name != "صانع محتوى") return name
        return "§${reel.id}"
    }

    /** مفتاح الموضوع للتنويع: أول وسم، أو فئة مستنتجة من العنوان عند غياب الوسوم.
     *
     * ق42: موضوع مجهول («») كان يجمع كل المقاطع بلا وسم وبلا كلمة
     * مفتاحية في موضوع واحد فيمنع تتابعها ظلماً؛ فالمجهول الآن مفتاح
     * خاص بكل مقطع — لا يمنع تتابع مقطعين إلا موضوع معروف مشترك. */
    fun topicKey(reel: SupabaseServices.ReelRow): String {
        val firstTag = reel.tags.firstOrNull()?.trim().orEmpty()
        if (firstTag.isNotBlank()) return firstTag
        val topic = when {
            reel.title.contains("قرآن") || reel.title.contains("تلاوة") -> "قرآن"
            reel.title.contains("حديث") || reel.title.contains("سنة") -> "حديث"
            reel.title.contains("قصة") || reel.title.contains("عبرة") -> "قصص"
            else -> ""
        }
        if (topic.isNotBlank()) return topic
        return "§${reel.id}"
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

    /**
     * تثبيت المقطع الحالي في موضعه بعد إعادة ترتيب حية (ق42).
     *
     * السبب الجذري: المتصفح يعرض بالفهرس، وإعادة الترتيب على إشارات
     * حية (إعجاب/حفظ/ثواني مشاهدة) قد تنقل المقطع الذي يشاهده المستخدم
     * الآن إلى فهرس آخر — فيتبدل المقطع على الشاشة بلا تمرير منه،
     * ويُثبَّت إثبات مشاهدة زائف للمقطع القديم عبر أثر تغيير القائمة.
     * الحل: بعد كل ترتيب يُعاد المقطع الحالي ([currentId]) إلى موضعه
     * في الترتيب المعروض سابقاً ([previousOrderIds])؛ بلا موضع سابق
     * أو بلا انتقال تُرجع القائمة كما هي.
     */
    fun pinCurrentReel(
        sorted: List<SupabaseServices.ReelRow>,
        previousOrderIds: List<String>,
        currentId: String?
    ): List<SupabaseServices.ReelRow> {
        if (currentId == null) return sorted
        val previousIndex = previousOrderIds.indexOf(currentId)
        val currentIndex = sorted.indexOfFirst { it.id == currentId }
        if (previousIndex !in sorted.indices || currentIndex < 0 || previousIndex == currentIndex) {
            return sorted
        }
        val pinned = sorted.toMutableList()
        val reel = pinned.removeAt(currentIndex)
        pinned.add(previousIndex, reel)
        return pinned
    }
}
