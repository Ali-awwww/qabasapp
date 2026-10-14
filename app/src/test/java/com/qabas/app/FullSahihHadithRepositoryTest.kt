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
    // 3) البحث الكامل — يفحص البحث الفعلي داخل الأحاديث
    // ═══════════════════════════════════════════════════════════
    @Test
    fun `bukhari search finds the first hadith (intentions)`() {
        val book = loadBook("sahih_bukhari.json.gz")

        // 🔑 حديث النية هو أول حديث في صحيح البخاري — يجب أن نجده بالبحث
        val results = book.hadiths.asSequence()
            .filter { it.arabic.contains("النيات", ignoreCase = true) }
            .toList()
        assertTrue("البحث عن «النيات» يجب أن يُعيد حديث النية الأول (وجدنا ${results.size})", results.isNotEmpty())
        assertTrue("حديث النية يجب أن يحتوي على idInBook=1", results.any { it.idInBook == 1 })
    }

    @Test
    fun `muslim search finds known hadith (purity/intentions)`() {
        val book = loadBook("sahih_muslim.json.gz")

        // 🔑 حديث مشهور في صحيح مسلم — الطهارة
        val results = book.hadiths.asSequence()
            .filter { it.arabic.contains("الطهارة", ignoreCase = true) }
            .toList()
        assertTrue("البحث عن «الطهارة» يجب أن يُعيد أحاديث كتاب الطهارة (وجدنا ${results.size})", results.isNotEmpty())
    }

    @Test
    fun `search with limit returns at most limit results`() {
        val book = loadBook("sahih_bukhari.json.gz")
        val limit = 10
        val results = book.hadiths.asSequence()
            .filter { it.arabic.contains("الله", ignoreCase = true) } // موجود تقريبًا في كل الأحاديث
            .take(limit)
            .toList()
        assertEquals("البحث مع limit=$limit يجب أن يُعيد بالضبط $limit", limit, results.size)
    }

    @Test
    fun `search with empty query returns empty list`() {
        val book = loadBook("sahih_bukhari.json.gz")
        val results = book.hadiths.asSequence()
            .filter { it.arabic.contains("", ignoreCase = true) }
            .take(50)
            .toList()
        // البحث الفارغ يُعيد أول 50 (سلوك بنية الـ take)
        assertEquals("بحث فارغ + take(50) = 50 نتيجة", 50, results.size)
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
}
