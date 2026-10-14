package com.qabas.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/**
 * 🔑 مستودع صحيح البخاري وصحيح مسلم الكامل (offline-first)
 * يقرأ من assets/hadith/*.json.gz ويحمّل الأحاديث بشكل تدريجي (Lazy) لتجنب OOM
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

    // 🔑 نُخزّن كتابًا واحدًا فقط في الذاكرة (نُفرغ الآخر عند التبديل)
    @Volatile private var cachedBook: FullSahihBook? = null
    @Volatile private var cachedFileName: String? = null

    /**
     * تحميل كتاب كامل من الـ assets بشكل غير متزامن (Main thread safe)
     */
    suspend fun loadBook(context: Context, bookType: SahihBookType): FullSahihBook? = withContext(Dispatchers.IO) {
        val fileName = when (bookType) {
            SahihBookType.BUKHARI -> BUKHARI_FILE
            SahihBookType.MUSLIM -> MUSLIM_FILE
            SahihBookType.NAWAWIS40 -> return@withContext null // نستخدم SahihHadithsRepository للأربعين النووية
        }

        if (cachedFileName == fileName && cachedBook != null) return@withContext cachedBook

        try {
            val t0 = System.currentTimeMillis()
            context.assets.open("hadith/$fileName").use { stream ->
                GZIPInputStream(stream).use { gz ->
                    BufferedReader(InputStreamReader(gz)).use { reader ->
                        val jsonStr = reader.readText()
                        val json = Json { ignoreUnknownKeys = true }
                        val book = json.decodeFromString<FullSahihBook>(jsonStr)
                        Log.d(TAG, "Loaded $fileName: ${book.hadiths.size} hadiths, ${book.chapters.size} chapters in ${System.currentTimeMillis() - t0}ms")
                        cachedBook = book
                        cachedFileName = fileName
                        return@withContext book
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $fileName: ${e.message}")
            null
        }
    }

    /**
     * تفريغ الذاكرة المؤقتة (عند مغادرة الشاشة)
     */
    fun clearCache() {
        cachedBook = null
        cachedFileName = null
    }

    /**
     * الحصول على فصول كتاب (نحفظ الفصول فقط في الذاكرة بشكل دائم)
     */
    suspend fun getChapters(context: Context, bookType: SahihBookType): List<FullSahihChapter> {
        return loadBook(context, bookType)?.chapters ?: emptyList()
    }

    /**
     * الحصول على أحاديث فصل محدد
     */
    suspend fun getHadithsForChapter(context: Context, bookType: SahihBookType, chapterId: Int): List<FullSahihHadith> {
        val book = loadBook(context, bookType) ?: return emptyList()
        return book.hadiths.filter { it.chapterId == chapterId }
    }

    /**
     * البحث في الأحاديث (limit=50 لمنع حمل نتائج ضخمة)
     */
    suspend fun searchHadiths(context: Context, bookType: SahihBookType, query: String, limit: Int = 50): List<FullSahihHadith> {
        if (query.isBlank()) return emptyList()
        val book = loadBook(context, bookType) ?: return emptyList()
        val q = query.trim()
        return book.hadiths.asSequence()
            .filter { it.arabic.contains(q, ignoreCase = true) }
            .take(limit)
            .toList()
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
