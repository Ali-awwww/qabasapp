package com.qabas.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * كنوز مجانية بلا مفاتيح: NASA + Wikimedia + Openverse.
 * تعمل دائماً حتى بدون Pixabay. لا وهم: فشل الشبكة = قائمة فارغة.
 */
object FreeStockSources {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun searchWikimedia(queryEn: String, limit: Int = 5): List<BRollItem> = withContext(Dispatchers.IO) {
        try {
            val q = java.net.URLEncoder.encode(queryEn, "UTF-8")
            val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrsearch=${q}%20filetype:video&gsrlimit=$limit&prop=imageinfo&iiprop=url&iiurlwidth=640"
            val req = Request.Builder().url(url).header("User-Agent", "QabasStudio/1.0").build()
            val body = client.newCall(req).execute().use { it.body?.string() ?: return@withContext emptyList() }
            val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages") ?: return@withContext emptyList()
            val out = mutableListOf<BRollItem>()
            val keys = pages.keys()
            while (keys.hasNext()) {
                val p = pages.optJSONObject(keys.next()) ?: continue
                val info = p.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                val vUrl = info.optString("url", "")
                val thumb = info.optString("thumburl", "")
                if (vUrl.isBlank()) continue
                out.add(BRollItem("wiki_${p.optInt("pageid")}", p.optString("title", "Wikimedia"), "طبيعة وكون وتفكر", listOf("wikimedia"), thumb, vUrl, "Fade", "مصدر حر Wikimedia Commons"))
            }
            out
        } catch (_: Exception) { emptyList() }
    }

    suspend fun searchNasa(queryEn: String, limit: Int = 3): List<BRollItem> = withContext(Dispatchers.IO) {
        try {
            val q = java.net.URLEncoder.encode(queryEn, "UTF-8")
            val url = "https://images-api.nasa.gov/search?q=$q&media_type=video&page_size=$limit"
            val req = Request.Builder().url(url).build()
            val body = client.newCall(req).execute().use { it.body?.string() ?: return@withContext emptyList() }
            val items = JSONObject(body).optJSONObject("collection")?.optJSONArray("items") ?: return@withContext emptyList()
            val out = mutableListOf<BRollItem>()
            for (i in 0 until minOf(items.length(), limit)) {
                val it = items.optJSONObject(i) ?: continue
                val data = it.optJSONArray("data")?.optJSONObject(0) ?: continue
                val title = data.optString("title", "NASA")
                val href = it.optString("href", "")
                if (href.isBlank()) continue
                // href هو manifest JSON: نحلّه لـ mp4 حقيقي، وإلا نتخطى بصدق
                try {
                    val mBody = client.newCall(Request.Builder().url(href).build()).execute().use { r -> r.body?.string() ?: "" }
                    val mp4 = Regex("\"(https:[^\"]+\\.mp4)\"").find(mBody)?.groupValues?.getOrNull(1) ?: continue
                    out.add(BRollItem("nasa_$i", title, "طبيعة وكون وتفكر", listOf("nasa", "space"), "", mp4, "Fade", "أرشيف NASA العام"))
                } catch (_: Exception) { continue }
            }
            out
        } catch (_: Exception) { emptyList() }
    }


    /** بحث موحد بلا مفتاح: Wikimedia أولاً ثم NASA، ثم مكتبة Mixkit المحلية كملاذ أخير */
    suspend fun searchFree(queryAr: String): List<BRollItem> {
        val en = BRollEngine.mapArabicToVisualKeywords(queryAr).split(" ").take(3).joinToString(" ")
        val wiki = searchWikimedia(en)
        if (wiki.isNotEmpty()) return wiki
        val nasa = searchNasa(en)
        if (nasa.isNotEmpty()) return nasa
        return BRollEngine.searchBRoll(queryAr).take(3)
    }
}
