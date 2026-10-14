package com.qabas.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/**
 * 🔑 مستودع صحيح البخاري وصحيح مسلم الكامل (offline-first)
 * يقرأ من assets/hadith/*.json.gz ويحمّل كتابًا واحدًا في كل مرة.
 *
 * ⚠️ ملاحظة: هذا تحميل كامل لكتاب (لا تدريجي) — النص المفكوك ~8MB لكل كتاب
 * قبل الذاكرة الكائنية. البحث يعمل على فهرس مُطبّع مسبقًا (HadithSearchNormalizer).
 */

@Serializable
data class FullSahihHadith(
    val id: Int,
    val idInBook: Int,
    val chapterId: Int,
    val bookId: Int = 1,
    val arabic: String
)

@Serializable
data class FullSahihChapter(
    val id: Int,
    val bookId: Int,
    val arabic: String,
    val english: String = ""
)

@Serializable
data class FullSahihBook(
    val metadata: Metadata,
    val chapters: List<FullSahihChapter>,
    val hadiths: List<FullSahihHadith>
) {
    @Serializable
    data class Metadata(val length: Int, val arabic_title: String, val author: String)
}

object FullSahihHadithRepository {
    private const val TAG = "FullSahihRepo"
    private const val BUKHARI_FILE = "sahih_bukhari.json.gz"
    private const val MUSLIM_FILE = "sahih_muslim.json.gz"

    // 🔑 مدخل واحد للتخزين المؤقت (identity + data) مع Mutex لمنع التحميل المتزامن المكرر
    private data class CachedBook(val bookType: SahihBookType, val book: FullSahihBook, val searchIndex: Map<Int, String>)

    private val cacheMutex = Mutex()
    @Volatile private var cached: CachedBook? = null

    /**
     * تحميل كتاب كامل من الـ assets بشكل غير متزامن (Main thread safe).
     * يبني فهرس بحث مُطبّع مسبقًا لتسريع البحث التالي.
     */
    suspend fun loadBook(context: Context, bookType: SahihBookType): FullSahihBook? = withContext(Dispatchers.IO) {
        val fileName = when (bookType) {
            SahihBookType.BUKHARI -> BUKHARI_FILE
            SahihBookType.MUSLIM -> MUSLIM_FILE
        }

        // 🔑 Mutex يمنع التحميل المتزامن المكرر + يضمن ذرّية cache-vs-load
        cacheMutex.withLock {
            cached?.let { if (it.bookType == bookType) return@withContext it.book }
        }

        try {
            val t0 = System.currentTimeMillis()
            val book = context.assets.open("hadith/$fileName").use { stream ->
                GZIPInputStream(stream).use { gz ->
                    BufferedReader(InputStreamReader(gz)).use { reader ->
                        val jsonStr = reader.readText()
                        val json = Json { ignoreUnknownKeys = true }
                        json.decodeFromString<FullSahihBook>(jsonStr)
                    }
                }
            }

            // 🔑 بناء فهرس بحث مُطبّع مسبقًا (تسريع كبير للبحث المتتالي)
            val searchIndex = HadithSearchNormalizer.buildSearchIndex(book.hadiths)

            Log.d(TAG, "Loaded $fileName: ${book.hadiths.size} hadiths, ${book.chapters.size} chapters in ${System.currentTimeMillis() - t0}ms")

            cacheMutex.withLock {
                cached = CachedBook(bookType, book, searchIndex)
            }
            return@withContext book
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // 🔑 لا نبتلع الإلغاء
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $fileName: ${e.message}")
            null
        }
    }

    /**
     * تفريغ الذاكرة المؤقتة (عند مغادرة الشاشة).
     * 🔑 فقط إذا لم يكن هناك تحميل جارٍ — Mutex يضمن عدم السباق.
     */
    suspend fun clearCache() {
        cacheMutex.withLock {
            cached = null
        }
    }

    /**
     * الحصول على فصول كتاب
     */
    suspend fun getChapters(context: Context, bookType: SahihBookType): List<FullSahihChapter> {
        return loadBook(context, bookType)?.chapters ?: emptyList()
    }

    /**
     * الحصول على أحاديث فصل محدد — 🔑 تصفية على Dispatchers.Default (خلفي)
     */
    suspend fun getHadithsForChapter(context: Context, bookType: SahihBookType, chapterId: Int): List<FullSahihHadith> = withContext(Dispatchers.Default) {
        val book = loadBook(context, bookType) ?: return@withContext emptyList()
        book.hadiths.filter { it.chapterId == chapterId }
    }

    /**
     * البحث في الأحاديث — 🔑 يعمل على Dispatchers.Default + فهرس مُطبّع مسبقًا
     * limit=50 لمنع حمل نتائج ضخمة (المستخدم يرى «أول 50 نتيجة» عند بلوغ الحد)
     */
    suspend fun searchHadiths(context: Context, bookType: SahihBookType, query: String, limit: Int = 50): List<FullSahihHadith> = withContext(Dispatchers.Default) {
        if (query.isBlank()) return@withContext emptyList()

        val book = loadBook(context, bookType) ?: return@withContext emptyList()
        val searchIndex = cacheMutex.withLock { cached?.takeIf { it.bookType == bookType }?.searchIndex } ?: return@withContext emptyList()

        // 🔑 البحث عبر الفهرس المُطبّع (يزيل التشكيل ويوحد الألف/الهمزة)
        val matchedIds = HadithSearchNormalizer.searchWithIndex(searchIndex, query, limit)
        val idSet = matchedIds.toSet()
        book.hadiths.filter { idSet.contains(it.id) }.take(limit)
    }

    /**
     * الحصول على حديث واحد بالمعرف
     */
    suspend fun getHadithById(context: Context, bookType: SahihBookType, id: Int): FullSahihHadith? {
        val book = loadBook(context, bookType) ?: return null
        return book.hadiths.firstOrNull { it.id == id }
    }

    /**
     * الحصول على إجمالي عدد الأحاديث
     */
    suspend fun getTotalCount(context: Context, bookType: SahihBookType): Int {
        return loadBook(context, bookType)?.hadiths?.size ?: 0
    }
}
