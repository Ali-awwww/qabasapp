package com.qabas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ق83: بحث الأذكار عبر المطبّع — نصوص الأذكار مشكولة، فاستعلام
 * بلا تشكيل كان يعيد صفراً مع contains الخام.
 */
class AzkarSearchTest {

    @Test
    fun `plain query finds vocalized zikr text`() {
        assertTrue(
            HadithSearchNormalizer.matches(
                "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ",
                "اللهم انت ربي"
            )
        )
    }

    @Test
    fun `plain query finds vocalized zikr title`() {
        assertTrue(
            HadithSearchNormalizer.matches("سيّد الاستغفار", "سيد الاستغفار")
        )
    }

    @Test
    fun `multi-word query matches non-contiguous words in zikr text`() {
        assertTrue(
            HadithSearchNormalizer.matches(
                "أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ",
                "اصبحنا الحمد"
            )
        )
    }

    @Test
    fun `absent word keeps zikr search empty`() {
        assertFalse(
            HadithSearchNormalizer.matches(
                "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ",
                "اللهم كلمةغيرموجودة"
            )
        )
    }

    @Test
    fun `fadl-only phrase matches via normalizer`() {
        // ق125: المرشحات الستة تبحث في الفضل أيضاً كما يعد شريط
        // البحث — فعبارة لا توجد إلا في الفضل يجب أن تطابق.
        assertTrue(
            HadithSearchNormalizer.matches(
                "من قالها حين يصبح أُجير من الجن والشياطين حتى يمسي.",
                "اجير من الجن"
            )
        )
        assertTrue(
            HadithSearchNormalizer.matches(
                "قال النبي ﷺ: «قُلْ هُوَ اللَّهُ أَحَدٌ، والمُعَوِّذَتَيْنِ حِينَ تُمْسِي وحِينَ تُصْبِحُ ثَلاثَ مَرَّاتٍ، تَكْفِيكَ مِنْ كُلِّ شَيْءٍ».",
                "تكفيك من كل شيء"
            )
        )
    }
}
