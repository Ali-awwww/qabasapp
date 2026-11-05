package com.qabas.app

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import java.io.File

/** Exact source fields from the supplied JSON. No editorial classifications are generated. */
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
        get() = inBookReference.ifBlank { reference.ifBlank { "صحيح البخاري" } }
}

object QabasBukhariRepository {
    private const val FILE_NAME = "sahih_al_bukhari.json"

    fun load(context: Context): List<BukhariHadith> {
        // Optional user-imported copy takes precedence over the bundled asset.
        val importedFile = File(context.filesDir, FILE_NAME)
        if (importedFile.exists()) {
            return parse(importedFile.readText(Charsets.UTF_8))
        }

        // Bundle the dataset at: app/src/main/assets/sahih_al_bukhari.json
        return runCatching {
            context.assets.open(FILE_NAME).bufferedReader(Charsets.UTF_8).use { reader ->
                parse(reader.readText())
            }
        }.getOrElse { emptyList() }
    }

    fun importAndLoad(context: Context, uri: Uri): List<BukhariHadith> {
        val input = context.contentResolver.openInputStream(uri) ?: error("تعذر فتح الملف المحدد")
        input.use { source ->
            File(context.filesDir, FILE_NAME).outputStream().use { target -> source.copyTo(target) }
        }
        return load(context)
    }

    internal fun parse(json: String): List<BukhariHadith> {
        val rows = JSONArray(json)
        return buildList(rows.length()) {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val arabic = row.optString("Arabic_Text").takeUnless { it == "null" }.orEmpty()
                if (arabic.isBlank()) continue
                add(BukhariHadith(
                    id = "bukhari-$index",
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
