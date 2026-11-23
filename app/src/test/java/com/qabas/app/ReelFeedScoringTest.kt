package com.qabas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * ترتيب «لك» في الريلز (ق42):
 * - العدادات المجمعة مضغوطة لوغاريتمياً فلا تبتلع إشارات المشاهد.
 * - الموضوع/الصانع المجهولان لا يمنعان تتابع مقطعين ظلماً.
 * - تثبيت المقطع الحالي يمنع تبدّله على الشاشة عند إعادة الترتيب الحية.
 */
class ReelFeedScoringTest {

    private val nowMillis = Instant.parse("2026-11-20T00:00:00Z").toEpochMilli()

    private fun reel(
        id: String,
        userId: String = "u-$id",
        userName: String = "صانع $id",
        title: String = "مقطع $id",
        tags: List<String> = emptyList(),
        likesCount: Int = 0,
        commentsCount: Int = 0,
        sharesCount: Int = 0,
        createdAt: String = "2026-11-20T00:00:00Z"
    ) = SupabaseServices.ReelRow(
        id = id,
        userId = userId,
        userName = userName,
        title = title,
        videoUrl = "https://example.com/$id.mp4",
        tags = tags,
        likesCount = likesCount,
        commentsCount = commentsCount,
        sharesCount = sharesCount,
        createdAt = createdAt
    )

    @Test
    fun `personal signals beat raw popularity`() {
        // محفوظ + مشاهَد كاملاً (3 + 4 = 7) يتفوق على 100 إعجاب بلا إشارة (ln101 ≈ 4.6).
        val personal = reel("a", tags = listOf("قرآن"))
        val popular = reel("b", userId = "u-b", tags = listOf("حديث"), likesCount = 100)
        val signals = mapOf(
            "a" to ReelFeedScoring.ViewerSignals(saved = true, watchSeconds = 10)
        )
        val ordered = ReelFeedScoring.sortForYou(listOf(popular, personal), signals, nowMillis)
        assertEquals(listOf("a", "b"), ordered.map { it.id })
    }

    @Test
    fun `mega viral still outranks personal max`() {
        // 10 آلاف إعجاب (ln10001 ≈ 9.2) تتفوق على الحد الشخصي الأقصى (~7) — توازن لا إلغاء للشعبية.
        val personal = reel("a", tags = listOf("قرآن"))
        val viral = reel("b", userId = "u-b", tags = listOf("حديث"), likesCount = 10_000)
        val signals = mapOf(
            "a" to ReelFeedScoring.ViewerSignals(saved = true, watchSeconds = 10)
        )
        val ordered = ReelFeedScoring.sortForYou(listOf(personal, viral), signals, nowMillis)
        assertEquals(listOf("b", "a"), ordered.map { it.id })
    }

    @Test
    fun `decay halves score after seven days`() {
        val fresh = reel("a", likesCount = 100)
        val aged = reel("b", likesCount = 100, createdAt = "2026-11-13T00:00:00Z")
        val freshScore = ReelFeedScoring.scoreReel(fresh, nowMillis = nowMillis)
        val agedScore = ReelFeedScoring.scoreReel(aged, nowMillis = nowMillis)
        assertEquals(freshScore * 0.5, agedScore, 1e-9)
    }

    @Test
    fun `compressed count is zero at zero and logarithmic`() {
        assertEquals(0.0, ReelFeedScoring.compressedCount(0), 1e-9)
        assertEquals(kotlin.math.ln(101.0), ReelFeedScoring.compressedCount(100), 1e-9)
    }

    @Test
    fun `untagged reels do not block each other on empty topic`() {
        // قبل ق42 كان topicKey «» للثلاثة فيُبعَد الثاني خلف الثالث رغم اختلاف الصانع والموضوع.
        val r1 = reel("r1", title = "مقطع أول", likesCount = 10)
        val r2 = reel("r2", title = "مقطع ثان", likesCount = 9)
        val r3 = reel("r3", title = "تلاوة خاشعة", likesCount = 8)
        val ordered = ReelFeedScoring.sortForYou(listOf(r1, r2, r3), nowMillis = nowMillis)
        assertEquals(listOf("r1", "r2", "r3"), ordered.map { it.id })
    }

    @Test
    fun `anonymous creators with default name do not block each other`() {
        // ثلاثة صنّاع بلا معرّف وبالاسم الافتراضي «صانع محتوى» كانوا يُعاملون صانعاً واحداً.
        val r1 = reel("r1", userId = "", userName = "صانع محتوى", tags = listOf("قرآن"), likesCount = 10)
        val r2 = reel("r2", userId = "", userName = "صانع محتوى", tags = listOf("حديث"), likesCount = 9)
        val r3 = reel("r3", userId = "", userName = "صانع محتوى", tags = listOf("قصص"), likesCount = 8)
        val r4 = reel("r4", userId = "u9", tags = listOf("مواعظ"), likesCount = 1)
        val ordered = ReelFeedScoring.sortForYou(listOf(r1, r2, r3, r4), nowMillis = nowMillis)
        assertEquals(listOf("r1", "r2", "r3", "r4"), ordered.map { it.id })
    }

    @Test
    fun `same creator still diversified when alternative exists`() {
        val r1 = reel("r1", userId = "u1", tags = listOf("قرآن"), likesCount = 10)
        val r2 = reel("r2", userId = "u1", tags = listOf("حديث"), likesCount = 9)
        val r3 = reel("r3", userId = "u2", tags = listOf("قصص"), likesCount = 8)
        val ordered = ReelFeedScoring.sortForYou(listOf(r1, r2, r3), nowMillis = nowMillis)
        assertEquals(listOf("r1", "r3", "r2"), ordered.map { it.id })
    }

    @Test
    fun `pinCurrentReel keeps current reel at displayed index`() {
        val a = reel("a")
        val b = reel("b")
        val c = reel("c")
        // الترتيب الجديد نقل c من الأول إلى الأخير؛ التثبيت يعيده إلى الأول.
        val pinned = ReelFeedScoring.pinCurrentReel(
            sorted = listOf(a, b, c),
            previousOrderIds = listOf("c", "a", "b"),
            currentId = "c"
        )
        assertEquals(listOf("c", "a", "b"), pinned.map { it.id })
    }

    @Test
    fun `pinCurrentReel is a no-op without a previous position`() {
        val a = reel("a")
        val b = reel("b")
        assertEquals(
            listOf("a", "b"),
            ReelFeedScoring.pinCurrentReel(listOf(a, b), listOf("a", "b"), "a").map { it.id }
        )
        assertEquals(
            listOf("a", "b"),
            ReelFeedScoring.pinCurrentReel(listOf(a, b), emptyList(), "a").map { it.id }
        )
        assertEquals(
            listOf("a", "b"),
            ReelFeedScoring.pinCurrentReel(listOf(a, b), listOf("a", "b"), null).map { it.id }
        )
        assertTrue(
            ReelFeedScoring.pinCurrentReel(listOf(a, b), listOf("a", "b"), "zzz").map { it.id } ==
                listOf("a", "b")
        )
    }
}
