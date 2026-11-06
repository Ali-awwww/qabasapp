package com.qabas.app

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.serialization.json.Json
import org.json.JSONArray
import java.io.BufferedReader
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/** Hadith source fields adapted for the Qabas tips carousel without rewriting hadith text. */
data class BukhariHadith(
    val id: String,
    val book: String,
    val chapterNumber: String,
    val chapterTitleArabic: String,
    val arabicText: String,
    val englishText: String,
    val grade: String,
    val reference: String,
    val inBookReference: String
) {
    val referenceLabel: String
        get() = inBookReference.ifBlank { reference.ifBlank { book } }
}

object QabasBukhariRepository {
    private const val TAG = "QabasHadithTipsRepo"
    private const val IMPORT_FILE_NAME = "sahih_al_bukhari.json"
    private const val BUKHARI_ASSET = "sahih_bukhari.json.gz"
    private const val MUSLIM_ASSET = "sahih_muslim.json.gz"
    private const val MAX_IMPORTED_BYTES = 8L * 1024 * 1024
    private const val MAX_DECOMPRESSED_BYTES = 64L * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Load both bundled Sahih datasets. The old uncompressed JSON is only an optional
     * user-imported override; its empty placeholder must not mask the bundled books.
     */
    fun load(context: Context): List<BukhariHadith> {
        val importedFile = File(context.filesDir, IMPORT_FILE_NAME)
        if (importedFile.isFile && importedFile.length() in 1..MAX_IMPORTED_BYTES) {
            val imported = runCatching { parse(importedFile.readText(Charsets.UTF_8)) }
                .onFailure { Log.w(TAG, "Unable to parse imported hadith file; falling back to bundled Sahihain", it) }
                .getOrDefault(emptyList())
            if (imported.isNotEmpty()) return imported
        }

        val bukhari = loadBundledBook(context, BUKHARI_ASSET, "صحيح البخاري", "bukhari")
        val muslim = loadBundledBook(context, MUSLIM_ASSET, "صحيح مسلم", "muslim")
        val combined = bukhari + muslim
        Log.i(TAG, "Loaded ${bukhari.size} Bukhari and ${muslim.size} Muslim hadiths for tips")
        return combined
    }

    private fun loadBundledBook(
        context: Context,
        fileName: String,
        fallbackTitle: String,
        idPrefix: String
    ): List<BukhariHadith> = runCatching {
        context.assets.open("hadith/$fileName").use { asset ->
            GZIPInputStream(asset).use { gzip ->
                LimitedInputStream(gzip, MAX_DECOMPRESSED_BYTES).use { limited ->
                    BufferedReader(InputStreamReader(limited, Charsets.UTF_8)).use { reader ->
                        val book = json.decodeFromString<FullSahihBook>(reader.readText())
                        mapFullBook(book, fallbackTitle, idPrefix)
                    }
                }
            }
        }
    }.onFailure {
        Log.e(TAG, "Failed loading hadith asset hadith/$fileName", it)
    }.getOrDefault(emptyList())

    /** Maps the canonical FullSahih schema to the tips model while retaining source text. */
    internal fun mapFullBook(
        book: FullSahihBook,
        fallbackTitle: String,
        idPrefix: String
    ): List<BukhariHadith> {
        val chaptersById = book.chapters.associateBy { it.id }
        val title = book.metadata.arabic_title.ifBlank { fallbackTitle }
        return book.hadiths.map { hadith ->
            val chapter = chaptersById[hadith.chapterId]
            val hadithNumber = hadith.idInBook.toString()
            BukhariHadith(
                id = "$idPrefix-${hadith.id}",
                book = title,
                chapterNumber = hadith.chapterId.toString(),
                chapterTitleArabic = chapter?.arabic.orEmpty(),
                arabicText = hadith.arabic,
                englishText = "",
                grade = "",
                reference = "$title — $hadithNumber",
                inBookReference = "رقم الحديث $hadithNumber"
            )
        }
    }

    fun importAndLoad(context: Context, uri: Uri): List<BukhariHadith> {
        val input = context.contentResolver.openInputStream(uri) ?: error("تعذر فتح الملف المحدد")
        input.use { source ->
            val destination = File(context.filesDir, IMPORT_FILE_NAME)
            destination.outputStream().use { target ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_IMPORTED_BYTES) { "ملف الأحاديث أكبر من الحد المسموح (8 MB)" }
                    target.write(buffer, 0, count)
                }
            }
        }
        return load(context)
    }

    /** Rejects unexpectedly large decompressed data before it can exhaust process memory. */
    private class LimitedInputStream(input: InputStream, private val maxBytes: Long) : FilterInputStream(input) {
        private var bytesRead = 0L

        @Throws(IOException::class)
        override fun read(): Int {
            val value = super.read()
            if (value >= 0) account(1)
            return value
        }

        @Throws(IOException::class)
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            val allowed = (maxBytes - bytesRead + 1).coerceAtMost(length.toLong()).toInt()
            if (allowed <= 0) throw IOException("Hadith asset exceeds decompressed size limit")
            val count = super.read(buffer, offset, allowed)
            if (count > 0) account(count.toLong())
            return count
        }

        private fun account(count: Long) {
            bytesRead += count
            if (bytesRead > maxBytes) throw IOException("Hadith asset exceeds decompressed size limit")
        }
    }

    internal fun parse(jsonText: String): List<BukhariHadith> {
        val rows = JSONArray(jsonText)
        return buildList(rows.length()) {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val arabic = row.optString("Arabic_Text").takeUnless { it == "null" }.orEmpty()
                if (arabic.isBlank()) continue
                add(BukhariHadith(
                    id = "imported-$index",
                    book = row.optString("Book").takeUnless { it == "null" }.orEmpty(),
                    chapterNumber = row.optString("Chapter_Number").takeUnless { it == "null" }.orEmpty(),
                    chapterTitleArabic = row.optString("Chapter_Title_Arabic").takeUnless { it == "null" }.orEmpty(),
                    arabicText = arabic,
                    englishText = row.optString("English_Text").takeUnless { it == "null" }.orEmpty(),
                    grade = row.optString("Grade").takeUnless { it == "null" }.orEmpty(),
                    reference = row.optString("Reference").takeUnless { it == "null" }.orEmpty(),
                    inBookReference = row.optString("In-book reference").takeUnless { it == "null" }.orEmpty()
                ))
            }
        }
    }
}
