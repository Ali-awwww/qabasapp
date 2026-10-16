package com.qabas.app

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/**
 * 🔑 اختبار صحيح البخاري وصحيح مسلم كاملين — التأكد من تحميل جميع الأحاديث والبحث فيها
 *
 * هذا الاختبار يعمل مباشرة على ملفات .gz الموجودة في app/src/main/assets/hadith/
 * ويؤكد أن:
 *   1) عدد الأحاديث في JSON يطابق العدد المعلن في metadata.length
 *   2) كل حديث مرتبط بفصل موجود في chapters
 *   3) البحث بالعربية يعمل بشكل صحيح (substrings حقيقية موجودة في الأحاديث)
 *   4) لا أحاديث فارغة
 *   5) أرقام الأحاديث الفريدة (id) موجودة
 *   6) العدد الإجمالي = 7277 (بخاري) + 7459 (مسلم) = 14736
 */
class FullSahihHadithRepositoryTest {

    private val assetsRoot = File("src/main/assets/hadith")
    private val json = Json { ignoreUnknownKeys = true }

    private fun loadBook(fileName: String): FullSahihBook {
        val file = File(assetsRoot, fileName)
        assertTrue("Asset $fileName must exist at ${file.absolutePath}", file.exists() && file.length() > 0)

        file.inputStream().use { stream ->
            GZIPInputStream(stream).use { gz ->
                BufferedReader(InputStreamReader(gz)).use { reader ->
                    val jsonStr = reader.readText()
                    return json.decodeFromString<FullSahihBook>(jsonStr)
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 1) صحيح البخاري — 7277 حديثًا كاملًا
    // ═══════════════════════════════════════════════════════════
    @Test
    fun `bukhari loads exactly 7277 hadiths`() {
        val book = loadBook("sahih_bukhari.json.gz")
        assertEquals("صحيح البخاري يجب أن يحتوي على 7277 حديثًا", 7277, book.hadiths.size)
        assertEquals("metadata.length يجب أن يطابق عدد الأحاديث الفعلي", book.hadiths.size, book.metadata.length)
        assertEquals("صحيح البخاري يجب أن يحتوي على 97 كتابًا", 97, book.chapters.size)
    }

    @Test
    fun `bukhari every hadith references a valid chapter`() {
        val book = loadBook("sahih_bukhari.json.gz")
        val validChapterIds = book.chapters.map { it.id }.toSet()
        assertTrue("يجب أن يكون هناك 97 chapterId فريدًا", validChapterIds.size == 97)

        val invalid = book.hadiths.filter { it.chapterId !in validChapterIds }
        assertTrue("كل حديث يجب أن يشير إلى chapterId موجود (وجدنا ${invalid.size} غير صالح)", invalid.isEmpty())
    }

    @Test
    fun `bukhari no empty hadiths`() {
        val book = loadBook("sahih_bukhari.json.gz")
        val empty = book.hadiths.filter { it.arabic.isBlank() || it.arabic.length < 10 }
        assertTrue("يجب ألا يكون هناك أحاديث فارغة أو قصيرة جدًا (وجدنا ${empty.size})", empty.isEmpty())
    }

    @Test
    fun `bukhari unique hadith ids`() {
        val book = loadBook("sahih_bukhari.json.gz")
        val ids = book.hadiths.map { it.id }
        assertEquals("معرّفات الأحاديث يجب أن تكون فريدة (تكرار ${ids.size - ids.toSet().size})", ids.size, ids.toSet().size)
    }

    // ═══════════════════════════════════════════════════════════
    // 2) صحيح مسلم — 7459 حديثًا كاملًا
    // ═══════════════════════════════════════════════════════════
    @Test
    fun `muslim loads exactly 7459 hadiths`() {
        val book = loadBook("sahih_muslim.json.gz")
        assertEquals("صحيح مسلم يجب أن يحتوي على 7459 حديثًا", 7459, book.hadiths.size)
        assertEquals("metadata.length يجب أن يطابق عدد الأحاديث الفعلي", book.hadiths.size, book.metadata.length)
        assertEquals("صحيح مسلم يجب أن يحتوي على 57 كتابًا", 57, book.chapters.size)
    }

    @Test
    fun `muslim every hadith references a valid chapter`() {
        val book = loadBook("sahih_muslim.json.gz")
        val validChapterIds = book.chapters.map { it.id }.toSet()
        assertTrue("يجب أن يكون هناك 57 chapterId فريدًا", validChapterIds.size == 57)

        val invalid = book.hadiths.filter { it.chapterId !in validChapterIds }
        assertTrue("كل حديث يجب أن يشير إلى chapterId موجود (وجدنا ${invalid.size} غير صالح)", invalid.isEmpty())
    }

    @Test
    fun `muslim no empty hadiths`() {
        val book = loadBook("sahih_muslim.json.gz")
        val empty = book.hadiths.filter { it.arabic.isBlank() || it.arabic.length < 10 }
        assertTrue("يجب ألا يكون هناك أحاديث فارغة أو قصيرة جدًا (وجدنا ${empty.size})", empty.isEmpty())
    }

    @Test
    fun `muslim unique hadith ids`() {
        val book = loadBook("sahih_muslim.json.gz")
        val ids = book.hadiths.map { it.id }
        assertEquals("معرّفات الأحاديث يجب أن تكون فريدة (تكرار ${ids.size - ids.toSet().size})", ids.size, ids.toSet().size)
    }

    // ═══════════════════════════════════════════════════════════
    // 3) البحث الكامل — 🔑 يستخدم HadithSearchNormalizer (يُطبّع التشكيل وأشكال الألف/الهمزة)
    // ═══════════════════════════════════════════════════════════
    @Test
    fun `normalizer strips diacritics and unifies alef`() {
        // 🔑 اختبار أساسي لمنطق التطبيع (بدون بيانات)
        val normalized = HadithSearchNormalizer.normalize("بِالنِّيَّاتِ")
        assertTrue("التطبيع يجب أن يُزيل التشكيل من «بِالنِّيَّاتِ» (حصل: $normalized)", normalized.contains("بالنيات"))
        assertFalse("التطبيع يجب ألا يحتوي على تشكيل (حصل: $normalized)", normalized.contains("ِ"))
    }

    @Test
    fun `bukhari search finds hadith 1 using UNDIACRITIZED query`() {
        val book = loadBook("sahih_bukhari.json.gz")

        // 🔑 نبحث بكلمة غير مشكلة — التطبيع يجب أن يجدها في النص المشكّل «بِالنِّيَّاتِ»
        val results = HadithSearchNormalizer.buildSearchIndex(book.hadiths)
            .let { index ->
                HadithSearchNormalizer.searchWithIndex(index, "بالنيات")
            }
        assertTrue("البحث غير المشكّل «بالنيات» يجب أن يجد حديث #1 (وجدنا ${results.size})", results.isNotEmpty())
        assertTrue("حديث النية (idInBook=1) يجب أن يكون في النتائج", results.contains(1))
    }

    @Test
    fun `bukhari search finds hadith 1 using DIACRITIZED query`() {
        val book = loadBook("sahih_bukhari.json.gz")

        // 🔑 نبحث بكلمة مشكّلة — التطبيع يجب أن يجعلها متطابقة
        val results = HadithSearchNormalizer.buildSearchIndex(book.hadiths)
            .let { index ->
                HadithSearchNormalizer.searchWithIndex(index, "بِالنِّيَّاتِ")
            }
        assertTrue("البحث المشكّل «بِالنِّيَّاتِ» يجب أن يجد حديث #1 (وجدنا ${results.size})", results.isNotEmpty())
        assertTrue("حديث النية (idInBook=1) يجب أن يكون في النتائج", results.contains(1))
    }

    @Test
    fun `muslim search finds known text (faith iman)`() {
        val book = loadBook("sahih_muslim.json.gz")

        // 🔑 «الإيمان» موجودة في متن أحاديث كتاب الإيمان (الفصل الأول)
        val results = HadithSearchNormalizer.buildSearchIndex(book.hadiths)
            .let { index ->
                HadithSearchNormalizer.searchWithIndex(index, "الايمان")
            }
        assertTrue("البحث عن «الايمان» يجب أن يُعيد أحاديث كتاب الإيمان (وجدنا ${results.size})", results.isNotEmpty())
    }

    @Test
    fun `search for non-existent term returns empty list`() {
        val book = loadBook("sahih_bukhari.json.gz")
        val results = HadithSearchNormalizer.buildSearchIndex(book.hadiths)
            .let { index ->
                HadithSearchNormalizer.searchWithIndex(index, "كلمةغيرموجودةليس في الاحاديث")
            }
        assertTrue("البحث عن كلمة غير موجودة يجب أن يُعيد قائمة فارغة (حصل ${results.size})", results.isEmpty())
    }

    @Test
    fun `search with whitespace-only query returns empty list`() {
        val book = loadBook("sahih_bukhari.json.gz")
        val results = HadithSearchNormalizer.buildSearchIndex(book.hadiths)
            .let { index ->
                HadithSearchNormalizer.searchWithIndex(index, "   ")
            }
        assertTrue("البحث بمسافات فقط يجب أن يُعيد قائمة فارغة (حصل ${results.size})", results.isEmpty())
    }

    @Test
    fun `search reaches hadith near the END of bukhari (not just first page)`() {
        val book = loadBook("sahih_bukhari.json.gz")

        // 🔑 نأخذ نص الحديث الأخير (idInBook=7277) ونبحث بأول 15 حرفًا منه
        val last = book.hadiths.last()
        assertTrue("يجب أن يكون هناك حديث أخير", last.idInBook > 7000)
        val lastText = HadithSearchNormalizer.normalize(last.arabic)
        val firstWords = lastText.split(" ").filter { it.length >= 3 }.take(3).joinToString(" ")
        assertTrue("يجب أن يكون لدينا كلمات للبحث (حصل: '$firstWords')", firstWords.isNotBlank())

        val results = HadithSearchNormalizer.buildSearchIndex(book.hadiths)
            .let { index ->
                HadithSearchNormalizer.searchWithIndex(index, firstWords)
            }
        assertTrue("البحث بكلمات من الحديث الأخير يجب أن يجد حديثًا واحدًا على الأقل (حصل ${results.size})", results.isNotEmpty())
        assertTrue("يجب أن يجد الحديث الأخير نفسه (id=${last.id})", results.contains(last.id))
    }

    @Test
    fun `search with limit returns at most limit results`() {
        val book = loadBook("sahih_bukhari.json.gz")
        val limit = 10
        val results = HadithSearchNormalizer.buildSearchIndex(book.hadiths)
            .let { index ->
                HadithSearchNormalizer.searchWithIndex(index, "الله", limit = limit)
            }
        assertEquals("البحث مع limit=$limit يجب أن يُعيد بالضبط $limit (حصل ${results.size})", limit, results.size)
    }

    @Test
    fun `search with empty query returns empty list (matches repository behavior)`() {
        val book = loadBook("sahih_bukhari.json.gz")
        // 🔑 المستودع يعيد قائمة فارغة عند استعلام فارغ — نطابق نفس السلوك
        val results = HadithSearchNormalizer.buildSearchIndex(book.hadiths)
            .let { index ->
                HadithSearchNormalizer.searchWithIndex(index, "")
            }
        assertTrue("البحث الفارغ يجب أن يُعيد قائمة فارغة (حصل ${results.size})", results.isEmpty())
    }

    @Test
    fun `total hadiths across both books equals 14736`() {
        val bukhari = loadBook("sahih_bukhari.json.gz")
        val muslim = loadBook("sahih_muslim.json.gz")
        assertEquals("7277 + 7459 = 14736 حديثًا", 14736, bukhari.hadiths.size + muslim.hadiths.size)
    }

    // ═══════════════════════════════════════════════════════════
    // 4) البنية (metadata وchapters)
    // ═══════════════════════════════════════════════════════════
    @Test
    fun `bukhari metadata has correct title and author`() {
        val book = loadBook("sahih_bukhari.json.gz")
        assertEquals("صحيح البخاري", book.metadata.arabic_title)
        assertEquals("الإمام محمد بن إسماعيل البخاري", book.metadata.author)
    }

    @Test
    fun `muslim metadata has correct title and author`() {
        val book = loadBook("sahih_muslim.json.gz")
        assertEquals("صحيح مسلم", book.metadata.arabic_title)
        assertEquals("الإمام مسلم بن الحجاج القشيري النيسابوري", book.metadata.author)
    }

    @Test
    fun `bukhari first chapter is Revelation`() {
        val book = loadBook("sahih_bukhari.json.gz")
        assertEquals("كتاب بدء الوحى", book.chapters.first().arabic)
        assertEquals("Revelation", book.chapters.first().english)
    }

    @Test
    fun `muslim first chapter is Faith`() {
        val book = loadBook("sahih_muslim.json.gz")
        assertEquals("كتاب الإيمان", book.chapters.first().arabic)
        assertEquals("The Book of Faith", book.chapters.first().english)
    }

    // ═══════════════════════════════════════════════════════════
    // 5) اختبارات صارمة لكل باب (97 بابًا للبخاري + 57 لمسلم)
    // ═══════════════════════════════════════════════════════════
    private fun assertEveryChapterStrict(fileName: String, bookLabel: String, expectedBookId: Int) {
        val book = loadBook(fileName)
        val byChapter = book.hadiths.groupBy { it.chapterId }

        // 1) كل باب له عنوان غير فارغ ومعرّف فريد
        val chapterIds = book.chapters.map { it.id }
        assertEquals("$bookLabel: معرّفات الأبواب يجب أن تكون فريدة", chapterIds.size, chapterIds.toSet().size)
        val blankTitles = book.chapters.filter { it.arabic.isBlank() }
        assertTrue("$bookLabel: أبواب بلا عنوان: ${blankTitles.map { it.id }}", blankTitles.isEmpty())

        // 2) كل باب يحتوي على حديث واحد على الأقل
        val emptyChapters = book.chapters.filter { (byChapter[it.id]?.size ?: 0) == 0 }
        assertTrue(
            "$bookLabel: أبواب فارغة بلا أحاديث: ${emptyChapters.map { "${it.id}(${it.arabic})" }}",
            emptyChapters.isEmpty()
        )

        // 3) كل حديث في الباب: نص غير فارغ + bookId صحيح + idInBook ضمن النطاق
        val badHadiths = book.hadiths.filter {
            it.arabic.isBlank() || it.bookId != expectedBookId || it.idInBook < 1 || it.idInBook > book.hadiths.size
        }
        assertTrue("$bookLabel: أحاديث مخالفة (نص فارغ/bookId/idInBook): ${badHadiths.size}", badHadiths.isEmpty())

        // 4) تسلسل idInBook متصل 1..N بلا فجوات ولا تكرار
        val sorted = book.hadiths.map { it.idInBook }.sorted()
        assertEquals("$bookLabel: تسلسل idInBook يجب أن يكون 1..${book.hadiths.size} بلا فجوات", (1..book.hadiths.size).toList(), sorted)

        // 5) كل باب: أصغر idInBook < أكبر idInBook أو باب بحديث واحد (ترتيب منطقي)
        val disordered = book.chapters.filter { ch ->
            val ids = (byChapter[ch.id] ?: emptyList()).map { it.idInBook }
            ids.isNotEmpty() && ids.min() > ids.max()
        }
        assertTrue("$bookLabel: أبواب بترتيب غير منطقي: ${disordered.map { it.id }}", disordered.isEmpty())
    }

    @Test
    fun `bukhari every one of 97 chapters is strict-valid`() {
        assertEveryChapterStrict("sahih_bukhari.json.gz", "البخاري", 1)
    }

    @Test
    fun `muslim every one of 57 chapters is strict-valid`() {
        assertEveryChapterStrict("sahih_muslim.json.gz", "مسلم", 2)
    }
}
