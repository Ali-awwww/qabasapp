package com.qabas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * تحليل وسوم التجويد من QF (verses/uthmani_tajweed):
 * الوسم بصيغة HTML القياسية class="ham_wasl" بعلامتي تنصيص —
 * إبقاء التنصيص في اسم القاعدة كان يجعل كل الألوان بلا أثر صامتاً
 * (المفتاح "ham_wasl" لا يطابق خريطة الألوان أبداً).
 */
class TajweedMarkupTest {

    @Test
    fun `quoted class attribute parses to bare rule name`() {
        val segments = QuranDataProvider.parseTajweedMarkup(
            "بِسْمِ <tajweed class=\"ham_wasl\">ٱ</tajweed>للَّهِ"
        )
        assertEquals(listOf("ham_wasl"), segments.mapNotNull { it.ruleClass }.distinct())
        assertEquals("بِسْمِ ٱللَّهِ", segments.joinToString("") { it.text })
        assertNotNull(QuranDataProvider.TAJWEED_RULE_COLORS["ham_wasl"])
    }

    @Test
    fun `unquoted and single-quoted class attributes still parse`() {
        val unquoted = QuranDataProvider.parseTajweedMarkup(
            "x<tajweed class=ghunnah>نّ</tajweed>y"
        )
        assertEquals("ghunnah", unquoted.firstOrNull { it.ruleClass != null }?.ruleClass)
        val singleQuoted = QuranDataProvider.parseTajweedMarkup(
            "x<tajweed class='qalaqah'>قْ</tajweed>y"
        )
        assertEquals("qalaqah", singleQuoted.firstOrNull { it.ruleClass != null }?.ruleClass)
    }

    @Test
    fun `nested rules keep innermost color and end span is dropped`() {
        // قاعدة داخل قاعدة + علامة نهاية الآية <span class="end">.
        val segments = QuranDataProvider.parseTajweedMarkup(
            "أَ<tajweed class=\"ikhafa\">نْ<tajweed class=\"qalaqah\">تَ</tajweed>مْ</tajweed> " +
                "<span class=\"end\">١</span>"
        )
        assertEquals("أَنْتَمْ ", segments.joinToString("") { it.text })
        assertEquals("qalaqah", segments.firstOrNull { it.text == "تَ" }?.ruleClass)
        assertEquals("ikhafa", segments.firstOrNull { it.text == "مْ" }?.ruleClass)
    }

    @Test
    fun `every named rule has a color in both palettes`() {
        for (rule in QuranDataProvider.TAJWEED_RULE_NAMES_AR.keys) {
            assertNotNull("dark palette missing $rule", QuranDataProvider.TAJWEED_RULE_COLORS[rule])
            assertNotNull(
                "light palette missing $rule",
                QuranDataProvider.TAJWEED_RULE_COLORS_LIGHT[rule]
            )
        }
        assertEquals(
            QuranDataProvider.TAJWEED_RULE_NAMES_AR.keys,
            QuranDataProvider.TAJWEED_RULE_COLORS.keys
        )
        assertEquals(
            QuranDataProvider.TAJWEED_RULE_NAMES_AR.keys,
            QuranDataProvider.TAJWEED_RULE_COLORS_LIGHT.keys
        )
    }

    @Test
    fun `palette resolver picks light map for light backgrounds`() {
        assertEquals(
            QuranDataProvider.TAJWEED_RULE_COLORS_LIGHT,
            QuranDataProvider.tajweedRuleColors(isLightBackground = true)
        )
        assertEquals(
            QuranDataProvider.TAJWEED_RULE_COLORS,
            QuranDataProvider.tajweedRuleColors(isLightBackground = false)
        )
    }

    @Test
    fun `unknown rule class keeps its text but has no color`() {
        val segments = QuranDataProvider.parseTajweedMarkup(
            "x<tajweed class=\"some_future_rule\">ن</tajweed>y"
        )
        assertEquals("xنy", segments.joinToString("") { it.text })
        val rule = segments.first { it.text == "ن" }.ruleClass
        assertEquals("some_future_rule", rule)
        assertNull(QuranDataProvider.TAJWEED_RULE_COLORS[rule])
        assertNull(QuranDataProvider.TAJWEED_RULE_COLORS_LIGHT[rule])
    }
}
