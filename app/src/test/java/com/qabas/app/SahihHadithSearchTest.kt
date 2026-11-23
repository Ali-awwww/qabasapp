package com.qabas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * بحث الأحاديث المنقّاة مطبَّع عبر HadithSearchNormalizer:
 * نص الحديث مشكول («بِالنِّيَّاتِ») فلا يجده contains خام على
 * استعلام بلا تشكيل («النيات»).
 */
class SahihHadithSearchTest {

    @Test
    fun `plain query finds vocalized matn`() {
        val results = SahihHadithsRepository.searchHadiths(null, null, "النيات")
        assertTrue(results.any { it.id == "bukhari_1" })
    }

    @Test
    fun `alef variant query matches`() {
        // الوسم «الإخلاص»؛ استعلام بألف بلا همزة يجده بعد التطبيع.
        val results = SahihHadithsRepository.searchHadiths(null, null, "الاخلاص")
        assertTrue(results.any { it.id == "bukhari_1" })
    }

    @Test
    fun `empty query lists everything`() {
        val all = SahihHadithsRepository.searchHadiths(null, null, "")
        assertEquals(SahihHadithsRepository.getAllHadiths().size, all.size)
    }

    @Test
    fun `unmatched query returns empty`() {
        assertTrue(SahihHadithsRepository.searchHadiths(null, null, "xyzqqq").isEmpty())
    }

    @Test
    fun `hadith number still matches exactly`() {
        val results = SahihHadithsRepository.searchHadiths(null, null, "1")
        assertTrue(results.any { it.id == "bukhari_1" })
    }

    @Test
    fun `multi-word query matches non-contiguous words in same matn`() {
        // ق82: «الأعمال» و«هجرته» في متن حديث النية لكن غير متجاورتين —
        // contains على العبارة كاملة كان يعيد صفراً.
        val results = SahihHadithsRepository.searchHadiths(null, null, "الأعمال هجرته")
        assertTrue(results.any { it.id == "bukhari_1" })
    }

    @Test
    fun `multi-word query with one absent word returns empty`() {
        val results = SahihHadithsRepository.searchHadiths(null, null, "الأعمال كلمةغيرموجودة")
        assertTrue(results.isEmpty())
    }
}
