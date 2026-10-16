package com.qabas.app

import org.junit.Test
import org.junit.Assert.*

/**
 * 🎓 اختبارات محرك أسئلة الفهم والحفظ — بيانات اصطناعية (بلا assets).
 */
class SahihQuizEngineTest {

    private fun fakeBook(chapterCount: Int = 5, hadithsPerChapter: Int = 6): FullSahihBook {
        val chapters = (1..chapterCount).map {
            FullSahihChapter(id = it, bookId = 1, arabic = "كتاب الاختبار رقم $it", english = "Test $it")
        }
        val hadiths = mutableListOf<FullSahihHadith>()
        var id = 1
        chapters.forEach { ch ->
            repeat(hadithsPerChapter) { n ->
                hadiths.add(
                    FullSahihHadith(
                        id = id,
                        idInBook = id,
                        chapterId = ch.id,
                        bookId = 1,
                        arabic = "حدثنا عبد الله بن الزبير قال حدثنا سفيان عن يحيى بن سعيد عن محمد بن إبراهيم أنه سمع علقمة بن وقاص يقول سمعت عمر بن الخطاب رضي الله عنه في المنبر يقول حديث رقم $id في الباب $n ويكمل المتن الشريف بالكلمات المتتابعة للاختبار"
                    )
                )
                id++
            }
        }
        return FullSahihBook(
            metadata = FullSahihBook.Metadata(length = hadiths.size, arabic_title = "كتاب اختبار", author = "مؤلف"),
            chapters = chapters,
            hadiths = hadiths
        )
    }

    @Test
    fun `round generates exactly 100 questions by default`() {
        val round = SahihQuizEngine.generateRound(fakeBook(), random = kotlin.random.Random(42))
        assertEquals(100, round.size)
    }

    @Test
    fun `every question has 4 distinct options and valid correct index`() {
        val round = SahihQuizEngine.generateRound(fakeBook(), random = kotlin.random.Random(7))
        assertTrue(round.isNotEmpty())
        round.forEach { q ->
            assertEquals("4 خيارات لكل سؤال", 4, q.options.size)
            assertEquals("الخيارات مميزة", 4, q.options.toSet().size)
            assertTrue("correctIndex صالح", q.correctIndex in 0..3)
            assertTrue("المقطع غير فارغ", q.passage.isNotBlank())
            assertTrue("الشرح غير فارغ", q.explanation.isNotBlank())
        }
    }

    @Test
    fun `round contains both comprehension and memorization types`() {
        // عدة جولات ببذور مختلفة يجب أن تنتج النوعين معاً
        val all = (1..10).flatMap { seed ->
            SahihQuizEngine.generateRound(fakeBook(), random = kotlin.random.Random(seed))
        }
        assertTrue("يجب وجود أسئلة فهم", all.any { it.type == SahihQuizType.COMPREHENSION })
        assertTrue("يجب وجود أسئلة حفظ", all.any { it.type == SahihQuizType.MEMORIZATION })
    }

    @Test
    fun `comprehension correct answer matches a real chapter`() {
        val book = fakeBook()
        val names = book.chapters.map { it.arabic }.toSet()
        val round = SahihQuizEngine.generateRound(book, count = 20, random = kotlin.random.Random(3))
        val comp = round.filter { it.type == SahihQuizType.COMPREHENSION }
        assertTrue(comp.isNotEmpty())
        comp.forEach { q ->
            assertTrue("الإجابة الصحيحة باب حقيقي", names.contains(q.options[q.correctIndex]))
        }
    }

    @Test
    fun `empty book returns empty round`() {
        val empty = FullSahihBook(
            metadata = FullSahihBook.Metadata(0, "فارغ", "لا أحد"),
            chapters = emptyList(),
            hadiths = emptyList()
        )
        assertTrue(SahihQuizEngine.generateRound(empty).isEmpty())
    }

    @Test
    fun `generation is deterministic with same seed`() {
        val book = fakeBook()
        val a = SahihQuizEngine.generateRound(book, random = kotlin.random.Random(99))
        val b = SahihQuizEngine.generateRound(book, random = kotlin.random.Random(99))
        assertEquals(a.map { it.passage }, b.map { it.passage })
        assertEquals(a.map { it.correctIndex }, b.map { it.correctIndex })
    }
}
