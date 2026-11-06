package com.qabas.app

import android.content.Context
import android.text.Html
import org.json.JSONObject

/**
 * Loads the verified Quran text and Al-Tafsir Al-Muyassar dumps packaged in the APK.
 * Only verses with an actual tafsir record are exposed; no tafsir text is fabricated.
 */
object QabasQuranData {
    fun load(context: Context): List<QabasTipItem> {
        val versesByKey = HashMap<Pair<Int, Int>, QuranVerse>(6500)
        val mushafRoot = JSONObject(readAsset(context, "quran/mushafs-1.json"))
        val surahs = mushafRoot.getJSONObject("data").getJSONArray("surahs")

        for (surahIndex in 0 until surahs.length()) {
            val surah = surahs.getJSONObject(surahIndex)
            val surahNumber = surah.getInt("id")
            val surahName = surah.getString("name").removePrefix("سورة ").trim()
            val ayahs = surah.getJSONArray("ayahs")
            for (ayahIndex in 0 until ayahs.length()) {
                val ayah = ayahs.getJSONObject(ayahIndex)
                val ayahNumber = ayah.getInt("number")
                val text = ayah.getString("text").replace("\uFEFF", "").trim()
                if (text.isNotBlank()) {
                    versesByKey[surahNumber to ayahNumber] =
                        QuranVerse(surahName = surahName, text = text)
                }
            }
        }

        val tafsirRoot = JSONObject(readAsset(context, "quran/tafsir-book-32.json"))
        val tafsirRows = tafsirRoot.getJSONArray("ayahs")
        val result = ArrayList<QabasTipItem>(tafsirRows.length())

        for (index in 0 until tafsirRows.length()) {
            val row = tafsirRows.getJSONObject(index)
            val surahNumber = row.getInt("surah")
            val ayahNumber = row.getInt("ayah")
            val verse = versesByKey[surahNumber to ayahNumber] ?: continue
            val content = row.optJSONArray("content")?.optJSONObject(0) ?: continue
            val rawTafsir = content.optString("text").trim()
            if (rawTafsir.isBlank()) continue

            val tafsirText = Html.fromHtml(
                rawTafsir.replace(Regex("(?i)<br\\s*/?>"), "<br/>"),
                Html.FROM_HTML_MODE_LEGACY
            ).toString().trim()
            if (tafsirText.isBlank()) continue

            result += QabasTipItem(
                id = "quran_${surahNumber}_${ayahNumber}",
                category = TipCategory.QURAN,
                text = verse.text,
                sourceOrBadge = "سورة ${verse.surahName} • الآية ${ayahNumber}",
                actionableHint = "التفسير الميسر\n\n${tafsirText}\n\nالمصدر: مجمع الملك فهد لطباعة المصحف الشريف — عبر الموسوعة القرآنية (quranpedia.net)."
            )
        }

        // Shuffle once per load; the existing carousel advances without immediate repeats.
        return result.shuffled()
    }

    private fun readAsset(context: Context, assetPath: String): String =
        context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use {
            it.readText()
        }

    private data class QuranVerse(
        val surahName: String,
        val text: String
    )
}
