package com.qabas.app

import android.content.Context
import android.net.Uri
import android.system.Os
import android.util.Log
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
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
    // The pinned AhmedBaset Bukhari book JSON is about 12 MB.
    private const val MAX_IMPORTED_BYTES = 32L * 1024 * 1024
    private const val MAX_DECOMPRESSED_BYTES = 24L * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Load both bundled Sahih datasets. A valid user-imported book overrides the
     * bundled tips data; an invalid import never masks the bundled books.
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

    /** Maps the app's bundled FullSahih schema to the tips model. */
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

    /**
     * Imports either the legacy array format or AhmedBaset/hadith-json v1.2.0
     * by-book format. Validation completes before the existing import is replaced.
     */
    fun importAndLoad(context: Context, uri: Uri): List<BukhariHadith> {
        val destination = File(context.filesDir, IMPORT_FILE_NAME)
        val temporary = File(context.filesDir, "$IMPORT_FILE_NAME.importing")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("تعذر فتح الملف المحدد")
            input.use { source ->
                temporary.outputStream().use { target ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_IMPORTED_BYTES) {
                            "ملف الأحاديث أكبر من الحد المسموح (32 MB)"
                        }
                        target.write(buffer, 0, count)
                    }
                }
            }

            val parsed = parse(temporary.readText(Charsets.UTF_8))
            require(parsed.isNotEmpty()) { "ملف الأحاديث لا يحتوي على أحاديث عربية صالحة" }

            // Same-directory rename atomically replaces the prior valid import.
            Os.rename(temporary.absolutePath, destination.absolutePath)
            Log.i(TAG, "Imported ${parsed.size} hadiths from selected JSON file")
            return parsed
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    /** Parses legacy row arrays and the pinned AhmedBaset by-book JSON schema. */
    internal fun parse(jsonText: String): List<BukhariHadith> {
        val trimmed = jsonText.trim()
        require(trimmed.isNotEmpty()) { "ملف JSON فارغ" }
        return when (trimmed.first()) {
            '[' -> parseLegacyRows(JSONArray(trimmed))
            '{' -> parseAhmedBasetBook(JSONObject(trimmed))
            else -> error("صيغة JSON غير مدعومة")
        }
    }

    private fun parseLegacyRows(rows: JSONArray): List<BukhariHadith> =
        buildList(rows.length()) {
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

    private fun parseAhmedBasetBook(root: JSONObject): List<BukhariHadith> {
        val metadata = root.optJSONObject("metadata")
            ?: error("ملف الكتاب لا يحتوي على metadata")
        val hadiths = root.optJSONArray("hadiths")
            ?: error("ملف الكتاب لا يحتوي على قائمة hadiths")
        val chapters = root.optJSONArray("chapters")
            ?: error("ملف الكتاب لا يحتوي على قائمة chapters")

        val arabicMetadata = metadata.optJSONObject("arabic")
        val title = arabicMetadata?.optString("title")
            ?.takeIf { !it.isNullOrBlank() && it != "null" }
            ?: metadata.optString("arabic_title").takeIf { it.isNotBlank() && it != "null" }
            ?: "صحيح البخاري"

        val chapterTitles = buildMap<Int, String> {
            for (index in 0 until chapters.length()) {
                val chapter = chapters.optJSONObject(index) ?: continue
                val chapterId = chapter.optInt("id", -1)
                val chapterTitle = chapter.optString("arabic")
                if (chapterId >= 0 && chapterTitle.isNotBlank() && chapterTitle != "null") {
                    put(chapterId, chapterTitle)
                }
            }
        }

        val result = ArrayList<BukhariHadith>(hadiths.length())
        for (index in 0 until hadiths.length()) {
            val hadith = hadiths.optJSONObject(index) ?: continue
            val arabic = hadith.optString("arabic").takeUnless { it == "null" }.orEmpty()
            if (arabic.isBlank()) continue

            val globalId = hadith.optInt("id", index + 1)
            val hadithNumber = hadith.optInt("idInBook", globalId)
            val chapterId = hadith.optInt("chapterId", -1)
            val english = hadith.optJSONObject("english")
            val narrator = english?.optString("narrator").orEmpty()
                .takeUnless { it == "null" }.orEmpty()
            val englishBody = english?.optString("text").orEmpty()
                .takeUnless { it == "null" }.orEmpty()
            val englishText = listOf(narrator, englishBody)
                .filter { it.isNotBlank() }
                .joinToString("\n")

            result += BukhariHadith(
                id = "bukhari-$globalId",
                book = title,
                chapterNumber = chapterId.takeIf { it >= 0 }?.toString().orEmpty(),
                chapterTitleArabic = chapterTitles[chapterId].orEmpty(),
                arabicText = arabic,
                englishText = englishText,
                grade = "",
                reference = "$title — $hadithNumber",
                inBookReference = "رقم الحديث $hadithNumber"
            )
        }

        require(result.isNotEmpty()) { "لم يتم العثور على أحاديث عربية في الملف" }
        val declaredLength = metadata.optInt("length", -1)
        require(declaredLength <= 0 || result.size == declaredLength) {
            "عدد الأحاديث المستوردة (${result.size}) لا يطابق العدد المعلن في metadata ($declaredLength)"
        }
        return result
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
}
