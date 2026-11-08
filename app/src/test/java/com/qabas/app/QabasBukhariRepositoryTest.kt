package com.qabas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QabasBukhariRepositoryTest {
    @Test
    fun `rejects empty or unsupported imported JSON instead of returning an empty hadith list`() {
        for (input in listOf("", "{}", "null")) {
            val result = runCatching { QabasBukhariRepository.parse(input) }
            assertTrue("Expected invalid input to be rejected: $input", result.isFailure)
        }
    }

    @Test
    fun `maps canonical hadith and chapter metadata into tips model`() {
        val book = FullSahihBook(
            metadata = FullSahihBook.Metadata(
                length = 1,
                arabic_title = "صحيح البخاري",
                author = "الإمام البخاري"
            ),
            chapters = listOf(
                FullSahihChapter(
                    id = 10,
                    bookId = 1,
                    arabic = "كتاب بدء الوحي",
                    english = "Revelation"
                )
            ),
            hadiths = listOf(
                FullSahihHadith(
                    id = 1,
                    idInBook = 1,
                    chapterId = 10,
                    bookId = 1,
                    arabic = "إنما الأعمال بالنيات"
                )
            )
        )

        val mapped = QabasBukhariRepository.mapFullBook(book, "صحيح البخاري", "bukhari")

        assertEquals(1, mapped.size)
        assertEquals("bukhari-1", mapped.single().id)
        assertEquals("صحيح البخاري", mapped.single().book)
        assertEquals("كتاب بدء الوحي", mapped.single().chapterTitleArabic)
        assertEquals("إنما الأعمال بالنيات", mapped.single().arabicText)
        assertEquals("رقم الحديث 1", mapped.single().referenceLabel)
    }

    @Test
    fun `bukhari and muslim mapping prefixes keep ids distinct`() {
        val book = FullSahihBook(
            metadata = FullSahihBook.Metadata(1, "صحيح", "مؤلف"),
            chapters = emptyList(),
            hadiths = listOf(FullSahihHadith(1, 1, 99, 1, "نص الحديث"))
        )

        val bukhari = QabasBukhariRepository.mapFullBook(book, "صحيح البخاري", "bukhari")
        val muslim = QabasBukhariRepository.mapFullBook(book, "صحيح مسلم", "muslim")

        assertEquals("bukhari-1", bukhari.single().id)
        assertEquals("muslim-1", muslim.single().id)
        assertTrue(bukhari.single().id != muslim.single().id)
    }
}
