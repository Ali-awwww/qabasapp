package com.qabas.app

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock

data class QuranSurahItem(
    val id: Int,
    val name: String,
    val englishName: String,
    val type: String, // مكية / مدنية
    val versesCount: Int,
    val juz: Int,
    val isLocked: Boolean = false,
    val isMastered: Boolean = false,
    val score: Int = 0,
    val tajweedFocus: String,
    val famousVerses: List<String> = emptyList(),
    val virtue: String? = null // فضل السورة
)


data class QuranVerseDetail(
    val surahNumber: Int,
    val surahName: String,
    val verseNumber: Int,
    val verseText: String,
    val tafseer: String,
    val tajweedNotes: String
)


/**
 * RawAyah
 * نموذج بيانات يمثل الآية القرآنية المستخرجة من ملفات البيانات
 */
data class RawAyah(
    val surah: Int,
    val ayah: Int,
    val text: String
)

/**
 * QuranDataProvider.kt
 * مزود بيانات القرآن الكريم وأكاديمية التجويد الشاملة في تطبيق خادم المسلم (Qabas).
 * يحتوي على القائمة الكاملة لكافة سور القرآن الكريم الـ 114 مع معلوماتها التوثيقية،
 * ونظام استرجاع الآيات الأصيلة والأحكام التجويدية.
 */

object QuranDataProvider {

    private val datasetLoadMutex = kotlinx.coroutines.sync.Mutex()

    /**
     * نقطة الدخول الموحدة لتحميل النص. تمنع قارئ السور وقارئ الصفحات من
     * بدء تنزيلين متزامنين للبيانات نفسها عند فتحهما بسرعة.
     * الترتيب مقصود: الملف المضمّن أولاً ليعمل القرآن دون إنترنت في أول
     * تشغيل، ثم الذاكرة المؤقتة المنزّلة، ثم الشبكة كملاذ أخير فقط.
     */
    suspend fun ensureQuranLoaded(context: Context): Boolean =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            datasetLoadMutex.withLock {
                loadFromAssets(context) ||
                    loadFromCache(context) ||
                    downloadAndCacheFromNetwork(context)
            }
        }

    private const val TAG = "QuranDataProvider"

    /**
     * نسبة النص العثماني المضمّن إلى مصدره. الملف المشحون `quran/uthmani.json`
     * لا يحمل حقل إشعار أصلاً — جذره يحوي مصفوفة `quran` فقط — فلا تُنسب إليه
     * هنا أي صيغة ترخيص لا يحملها. النص العثماني نفسه مأخوذ من مشروع Tanzil،
     * والنسخة نفسها عند جلبها من الشبكة تحمل إشعار Tanzil الكامل الذي ما زال
     * مسار التنزيل يتحقق منه أدناه. تُعرض هذه النسبة في السجلات، ويمكن لأي
     * واجهة عرضها للمستخدم من هذا الثابت.
     */
    const val QURAN_TEXT_ATTRIBUTION = "Quran text from Tanzil.net, Uthmani script"

    // ذاكرة التخزين المؤقت للآيات المحملة مقسمة حسب رقم السورة
    @Volatile
    private var versesMap: Map<Int, List<RawAyah>>? = null

    // ذاكرة التخزين المؤقت للتفسير الميسر بمفتاح "سورة:آية"
    @Volatile
    private var tafsirMap: Map<String, String>? = null

    /**
     * يحاول تحميل نسخة تفسير محلية إن وُجدت وكانت صالحة؛ النسخة الحالية
     * تعتمد على جلب QuranEnc عند الطلب ولا تفترض وجود ملف مدمج.
     */
    fun loadTafsirFromAssets(context: Context): Boolean {
        if (tafsirMap != null && tafsirMap!!.isNotEmpty()) {
            return true
        }
        val assetPaths = listOf("quran/tafsir_muyassar.json", "tafsir_muyassar.json")
        for (path in assetPaths) {
            try {
                val jsonString = context.assets.open(path).use { inputStream ->
                    inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
                if (jsonString.isNotBlank()) {
                    val parsed = parseTafsirJson(jsonString)
                    if (parsed.isNotEmpty()) {
                        tafsirMap = parsed
                        Log.i(TAG, "Successfully loaded ${parsed.size} tafsir entries from asset: $path")
                        return true
                    }
                }
            } catch (_: java.io.FileNotFoundException) {
                // الملف غير موجود في هذا المسار، يتم فحص المسار التالي
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading $path from assets: ${e.message}")
            }
        }
        return false
    }

    /**
     * تحليل مرن للـ JSON للتفسير الميسر: يقرأ مصفوفة "tafsir" ببنية
     * {"s","a","t"} مع دعم بدائل {"surah","ayah","text"} في ملفات أخرى مستقبلاً.
     */
    private fun parseTafsirJson(jsonStr: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        try {
            val trimmed = jsonStr.trim()
            if (!trimmed.startsWith("{")) return result
            val root = JSONObject(trimmed)
            val array = root.optJSONArray("tafsir")
                ?: root.optJSONArray("verses")
                ?: root.optJSONArray("ayahs")
                ?: root.optJSONArray("data")
            if (array != null) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val surah = obj.optInt("surah", obj.optInt("surah_number", obj.optInt("chapter", obj.optInt("s", 0))))
                    val ayah = obj.optInt("ayah", obj.optInt("verse", obj.optInt("ayah_number", obj.optInt("a", 0))))
                    val text = obj.optString("text", obj.optString("arabic_tafsir", obj.optString("tafsir", obj.optString("t", ""))))
                    if (surah > 0 && ayah > 0 && text.isNotBlank()) {
                        result["$surah:$ayah"] = text
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing tafsir JSON: ${e.message}")
        }
        return result
    }

    /**
     * استرجاع نص التفسير الميسر لآية محددة. يُرجع null عند غياب التحميل
     * أو عدم وجود الآية في النسخة المدمجة — لا تفسير ملفّق.
     */
    fun getTafsirForVerse(surahId: Int, ayah: Int): String? =
        tafsirMap?.get("$surahId:$ayah")

    private val remoteTafsirCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * تفسير السعدي وحده من QuranEnc، مفتاح arabic_saadi (فُحص حياً
     * 2026-11-22: يعيد 200) — جلب شبكي صرف بلا رجوع إلى التفسير
     * الميسر المحلي، ليصحّ ملاذاً لكتاب السعدي في fetchTafsirForBook
     * دون خلط المصدرين. المفتاح السابق arabic_moyassar يعيد 404.
     * تنبيه: رد QuranEnc يحمل عنصرين — عنصر type="المقطع" وهو نص الآية
     * نفسها (لا تفسير)، وعنصر type="تفسير آية" وهو التفسير المطلوب؛
     * المحلل يتخطى "المقطع" صراحةً وإلا عُرض نص الآية على أنه تفسير.
     * null عند الفشل.
     */
    suspend fun fetchQuranEncSaadi(surahId: Int, ayah: Int): String? {
        if (surahId !in 1..114 || ayah !in 1..286) return null
        val key = "$surahId:$ayah"
        remoteTafsirCache[key]?.let { return it }
        return try {
            val url = "https://quranenc.com/api/v1/tafsir/aya/arabic_saadi/$surahId/$ayah"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(18, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val body = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext ""
                    response.body?.string().orEmpty()
                }
            }
            if (body.isBlank()) return null

            val blocks = try {
                val root = JSONObject(body)
                root.optJSONArray("result")
                    ?: root.optJSONArray("data")
                    ?: if (root.has("text")) JSONArray().put(root) else JSONArray()
            } catch (_: Exception) {
                try { JSONArray(body) } catch (_: Exception) { JSONArray() }
            }

            val passages = LinkedHashSet<String>()
            for (index in 0 until blocks.length()) {
                val block = blocks.optJSONObject(index) ?: continue
                // عنصر "المقطع" هو نص الآية نفسها لا تفسيرها — يُتخطى.
                if (block.optString("type") == "المقطع") continue
                val blockSurah = block.optInt("sura", surahId)
                val fromAyah = block.optInt("from_aya", ayah)
                val toAyah = block.optInt("to_aya", ayah)
                if (blockSurah != surahId || ayah !in fromAyah..toAyah) continue
                val passage = block.optString("text").trim()
                if (passage.isNotBlank()) passages.add(passage)
            }

            val text = passages.joinToString("\n\n")
            if (text.isBlank()) null else {
                remoteTafsirCache[key] = text
                text
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Unable to load Tafsir Saadi from QuranEnc for $key", error)
            null
        }
    }

    // ========================================================================
    // كتب التفسير والإعراب (قسم القرآن — الكتب): spa5k/tafsir_api (MIT)
    // عبر jsDelivr، وalquran.cloud للجلالين.
    //
    // كل ملف آية في spa5k بصيغة {"text","ayah","surah"} (فُحص حياً
    // 2026-11-22)؛ والنصوص الكلاسيكية الطويلة (ابن كثير، الطبري) تحمل
    // علامات تحقيق [[...]] تُنظَّف عند العرض فقط — التنظيف يمس نص
    // التفسير/الإعراب المنقول وحده، ولا يمس نص الآية أبداً.
    // استثناء: إشعار «اختُصِرَ كلام المؤلف لشدة طوله» يُستخرج قبل
    // التنظيف ويُعرض كملاحظة مع النص المختصر (تحقق حياً 2026-11-23:
    // ابن كثير 2:102 ينتهي به) — لا حذف صامت لإشعار يفيد الاختصار.
    // تنبيه السعدي: المعرّف الصحيح للعرض آيةً آيةً هو
    // ar-tafseer-al-saddi (id 24)؛ أما ar-tafsir-as-saadi (id 308)
    // فيعيد السورة كاملة في كل ملف آية فلا يُستعمل أبداً.
    // ========================================================================

    private val spa5kBookCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * نتيجة تنظيف نص تفسير: النص بعد نزع علامات التحقيق، وإشعار
     * الاختصار المستخرج قبل التنظيف (إن وجد) ليعرض كملاحظة صادقة
     * مع النص المختصر بدل حذفه صامتاً.
     */
    private data class CleanedTafsirText(val text: String, val abbreviationNotice: String?)

    /**
     * تنظيف علامات التحقيق [[...]] من نص تفسير/إعراب شبكي، ودمج الأسطر
     * الفارغة الزائدة. للنص المنقول فقط — لا يُستدعى على نص آية أبداً.
     * استثناء واحد: إشعار «اختُصِرَ كلام المؤلف لشدة طوله» لا يُحذف
     * صامتاً — يُستخرج قبل التنظيف (بنصه الحرفي من المصدر، بعد نزع
     * قوسي التحقيق وتوحيد الفراغات فقط) فيعيده المستدعي كملاحظة
     * مع النص المختصر، فالقارئ يعلم أن ما يقرؤه مختصر.
     */
    private fun cleanTafsirEditorialNotes(text: String): CleanedTafsirText {
        val editorialRegex = Regex("(?s)\\[\\[.*?\\]\\]")
        val abbreviationNotice = editorialRegex.findAll(text)
            .map { it.value.removePrefix("[[").removeSuffix("]]").trim() }
            .firstOrNull { inner ->
                val plain = inner.replace(Regex("[\\u064B-\\u0652\\u0640\\u0670]"), "")
                plain.contains("اختص") && plain.contains("طول")
            }
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val cleaned = text.replace(editorialRegex, "")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        return CleanedTafsirText(cleaned, abbreviationNotice)
    }

    /**
     * نص آية واحدة من كتاب في spa5k/tafsir_api (MIT) عبر jsDelivr
     * (الإصدار المثبّت v1.2.3): GET
     * cdn.jsdelivr.net/gh/spa5k/tafsir_api@v1.2.3/tafsir/{book}/{surah}/{ayah}.json
     * وحقل النص هو "text". ذاكرة مؤقتة بمفتاح "كتاب:سورة:آية".
     * null عند أي فشل (لا اتصال، 404، استجابة فارغة).
     */
    suspend fun fetchSpa5kBook(bookSlug: String, surahId: Int, ayah: Int): String? {
        if (bookSlug.isBlank() || surahId !in 1..114 || ayah !in 1..286) return null
        val key = "$bookSlug:$surahId:$ayah"
        spa5kBookCache[key]?.let { return it }
        return try {
            val url = "https://cdn.jsdelivr.net/gh/spa5k/tafsir_api@v1.2.3/tafsir/$bookSlug/$surahId/$ayah.json"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(18, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val body = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext ""
                    response.body?.string().orEmpty()
                }
            }
            if (body.isBlank()) return null
            val rawText = try {
                JSONObject(body).optString("text", "")
            } catch (_: Exception) {
                ""
            }
            val cleaned = cleanTafsirEditorialNotes(rawText)
            // إشعار الاختصار (إن وجد) يُلحق بنصه الحرفي من المصدر
            // كملاحظة لا كجزء من التفسير — لا حذف نصاً ولا إضافة نص
            // من عندنا؛ القارئ يعلم أن المعروض مختصر. تحقق حياً:
            // ابن كثير 2:102 ينتهي بـ [[اختُصِرَ كلام المؤلف لشدة طوله]].
            val text = if (cleaned.abbreviationNotice != null) {
                "${cleaned.text}\n\n— ملاحظة من المصدر: ${cleaned.abbreviationNotice}".trim()
            } else {
                cleaned.text
            }
            if (text.isBlank()) null else {
                spa5kBookCache[key] = text
                text
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w(TAG, "Unable to load spa5k book $bookSlug for $surahId:$ayah", e)
            null
        }
    }

    /** كتاب تفسير يُعرض في لوحة الدراسة ومكتبة الكتب. */
    data class MushafTafsirBook(
        val id: String,
        val arabicName: String,
        val author: String,
        val source: String,
        val offline: Boolean
    )

    /**
     * الكتب الثلاثة المضافة لاحقاً — كلها من spa5k/tafsir_api (MIT)،
     * وفُحصت عيناتها حياً 2026-11-22 للآية 1:1 (HTTP 200 + نص حقيقي
     * بصيغة {"text","ayah","surah"}): القرطبي (181,029 بايت)، البغوي
     * (53,576 بايت)، الوسيط (7,364 بايت). المؤلفون موثقون من فهرس
     * إصدارات spa5k (author_name) ومكتبة ترتيل القرآنية:
     * القرطبي — محمد بن أحمد القرطبي (الجامع لأحكام القرآن)؛ البغوي —
     * الحسين بن مسعود البغوي (معالم التنزيل)؛ الوسيط — محمد سيد طنطاوي.
     */
    val MUSHAF_EXTRA_TAFSIR_BOOKS: List<MushafTafsirBook> = listOf(
        MushafTafsirBook(
            id = "qurtubi",
            arabicName = "تفسير القرطبي — الجامع لأحكام القرآن",
            author = "محمد بن أحمد القرطبي",
            source = "spa5k/tafsir_api (MIT)",
            offline = false
        ),
        MushafTafsirBook(
            id = "baghawi",
            arabicName = "تفسير البغوي — معالم التنزيل",
            author = "الحسين بن مسعود البغوي",
            source = "spa5k/tafsir_api (MIT)",
            offline = false
        ),
        MushafTafsirBook(
            id = "wasit",
            arabicName = "التفسير الوسيط للقرآن الكريم",
            author = "محمد سيد طنطاوي",
            source = "spa5k/tafsir_api (MIT)",
            offline = false
        )
    )

    /**
     * كتب التفسير الثمانية — كلها فُحصت نصوصها حياً 2026-11-22:
     * الميسر ملف محلي (6,236 مدخلاً)؛ السعدي وابن كثير والطبري من
     * spa5k/tafsir_api (MIT)؛ الجلالين من alquran.cloud؛ والثلاثة
     * الأخيرة من MUSHAF_EXTRA_TAFSIR_BOOKS (فُحصت عينات 1:1 حياً).
     */
    val MUSHAF_TAFSIR_BOOKS: List<MushafTafsirBook> = listOf(
        MushafTafsirBook(
            id = "muyassar",
            arabicName = "التفسير الميسر",
            author = "مجمع الملك فهد لطباعة المصحف الشريف",
            source = "ملف محلي مضمّن في التطبيق",
            offline = true
        ),
        MushafTafsirBook(
            id = "saadi",
            arabicName = "تفسير السعدي — تيسير الكريم الرحمن",
            author = "عبد الرحمن بن ناصر السعدي",
            source = "spa5k/tafsir_api (MIT)",
            offline = false
        ),
        MushafTafsirBook(
            id = "ibn-kathir",
            arabicName = "تفسير ابن كثير",
            author = "الحافظ ابن كثير",
            source = "spa5k/tafsir_api (MIT)",
            offline = false
        ),
        MushafTafsirBook(
            id = "tabari",
            arabicName = "تفسير الطبري — جامع البيان",
            author = "أبو جعفر الطبري",
            source = "spa5k/tafsir_api (MIT)",
            offline = false
        ),
        MushafTafsirBook(
            id = "jalalayn",
            arabicName = "تفسير الجلالين",
            author = "جلال الدين المحلي وجلال الدين السيوطي",
            source = "alquran.cloud",
            offline = false
        )
    ) + MUSHAF_EXTRA_TAFSIR_BOOKS

    private val jalalaynCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * تفسير الجلالين لآية واحدة من alquran.cloud (فُحص حياً 2026-11-22):
     * GET api.alquran.cloud/v1/ayah/{surah}:{ayah}/ar.jalalayn،
     * والنص في data.text. null عند أي فشل.
     */
    suspend fun fetchJalalayn(surahId: Int, ayah: Int): String? {
        if (surahId !in 1..114 || ayah !in 1..286) return null
        val key = "$surahId:$ayah"
        jalalaynCache[key]?.let { return it }
        return try {
            val url = "https://api.alquran.cloud/v1/ayah/$surahId:$ayah/ar.jalalayn"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(18, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val body = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext ""
                    response.body?.string().orEmpty()
                }
            }
            if (body.isBlank()) return null
            val text = try {
                JSONObject(body).optJSONObject("data")?.optString("text", "")?.trim().orEmpty()
            } catch (_: Exception) {
                ""
            }
            if (text.isBlank()) null else {
                jalalaynCache[key] = text
                text
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w(TAG, "Unable to load Jalalayn for $surahId:$ayah", e)
            null
        }
    }

    /**
     * تفسير آية من كتاب محدد بالمعرّف:
     * - muyassar: الميسر المحلي وحده (getTafsirForVerse) — null عند
     *   الغياب، بلا ملاذ صامت لكتاب آخر.
     * - saadi: spa5k (ar-tafseer-al-saddi) وعند الفشل ملاذ QuranEnc
     *   (fetchQuranEncSaadi — السعدي وحده، بلا خلط بالميسر).
     * - ibn-kathir / tabari: spa5k.
     * - jalalayn: alquran.cloud.
     * - qurtubi / baghawi / wasit: spa5k (فُحصت عينات 1:1 حياً).
     * معرّف غير معروف يرجع الميسر المحلي. null عند الفشل أو الغياب.
     */
    suspend fun fetchTafsirForBook(bookId: String, surahId: Int, ayah: Int): String? {
        return when (bookId) {
            "muyassar" -> getTafsirForVerse(surahId, ayah)
            "saadi" -> fetchSpa5kBook("ar-tafseer-al-saddi", surahId, ayah)
                ?: fetchQuranEncSaadi(surahId, ayah)
            "ibn-kathir" -> fetchSpa5kBook("ar-tafsir-ibn-kathir", surahId, ayah)
            "tabari" -> fetchSpa5kBook("ar-tafsir-al-tabari", surahId, ayah)
            "jalalayn" -> fetchJalalayn(surahId, ayah)
            "qurtubi" -> fetchSpa5kBook("ar-tafseer-al-qurtubi", surahId, ayah)
            "baghawi" -> fetchSpa5kBook("ar-tafsir-al-baghawi", surahId, ayah)
            "wasit" -> fetchSpa5kBook("ar-tafsir-al-wasit", surahId, ayah)
            else -> getTafsirForVerse(surahId, ayah)
        }
    }

    /**
     * إعراب آية واحدة من spa5k/tafsir_api (MIT) — يعيد استعمال
     * fetchSpa5kBook مع التنظيف نفسه لعلامات التحقيق [[...]]:
     * - muyassar: الإعراب الميسر (al-i-rab-al-muyassar)
     * - darwish: إعراب القرآن للدرويش (i-rab-al-quran-li-al-darwish)
     * null عند الفشل أو الغياب.
     */
    suspend fun fetchIrab(bookId: String, surahId: Int, ayah: Int): String? {
        val slug = when (bookId) {
            "muyassar" -> "al-i-rab-al-muyassar"
            "darwish" -> "i-rab-al-quran-li-al-darwish"
            else -> return null
        }
        return fetchSpa5kBook(slug, surahId, ayah)
    }

    // ========================================================================
    // كتب كاملة من quranpedia (قسم القرآن — الكتب): تُجلب شبكياً عند
    // الطلب فقط، ولا يُضمَّن نص أي كتاب في التطبيق — رخص إعادة توزيع
    // quranpedia لم تُدقَّق لكل كتاب.
    //
    // الشكل الفعلي (فُحص حياً 2026-11-22 بجلْب book-1727 وbook-439
    // وbook-89 كاملة): JSON بمفتاحين — "chapters": مصفوفة {name, id,
    // parent_id, part, children}، و"contents": مصفوفة {id, chapter_id,
    // text, ayah_from, ayah_to, related_ayahs}. حقول ayah_from و
    // ayah_to وrelated_ayahs فارغة في هذه الكتب (بلا ربط بالآيات)،
    // والربط الوحيد هو chapter_id — وقد يشير إلى فصل فرعي في
    // children (كتاب 439)، فتُسطَّح الفروع مع اسم أبيها في المحلل،
    // وقد يكون أدقّ من الفصول المعرَضة أصلاً فيُلحق المقطع بآخر
    // فصل معروف سبقه في ترتيب الملف (انظر fetchQuranPediaBook).
    // (<br />, <span>, <strong>, <p>) تُنزع عند العرض فقط — النزع
    // يمس الوسوم وحدها ولا يغيّر كلمة من النص المنقول.
    // ========================================================================

    /** فصل في كتاب quranpedia كامل. */
    data class QuranPediaBookChapter(val id: Int, val name: String)

    /** مقطع نصي في كتاب quranpedia كامل، منسوب إلى فصله عبر chapterId. */
    data class QuranPediaBookContent(val id: Int, val chapterId: Int, val text: String)

    /** كتاب quranpedia كامل بعد التحليل: فصوله ومقاطعه بنصوص نظيفة. */
    data class QuranPediaBook(
        val chapters: List<QuranPediaBookChapter>,
        val contents: List<QuranPediaBookContent>
    )

    /** بطاقة كتاب كامل في quranpedia (البيانات الوصفية فقط، بلا نص). */
    data class QuranPediaBookInfo(
        val id: Int,
        val arabicTitle: String,
        val author: String,
        val source: String
    )

    /**
     * الكتب الكاملة الثلاثة — فُحصت روابط محتوياتها حياً 2026-11-22
     * (HTTP 200): 1727 (104,965 بايت، 5 فصول، 40 مقطعاً)، 439
     * (1,313,242 بايت، 6 فصول، 299 مقطعاً)، 89 (209,599 بايت،
     * 17 فصلاً، 61 مقطعاً).
     */
    val MUSHAF_QURANPEDIA_BOOKS: List<QuranPediaBookInfo> = listOf(
        QuranPediaBookInfo(
            id = 1727,
            arabicTitle = "فوائد منتقاة من تفسير ابن كثير",
            author = "عبد الله زقيل",
            source = "quranpedia.net"
        ),
        QuranPediaBookInfo(
            id = 439,
            arabicTitle = "من بلاغة القرآن",
            author = "أحمد أحمد بدوي",
            source = "quranpedia.net"
        ),
        QuranPediaBookInfo(
            id = 89,
            arabicTitle = "الوجيز في علم التجويد",
            author = "محمود سيبويه البدوي",
            source = "quranpedia.net"
        )
    )

    private val quranPediaBookCache =
        java.util.concurrent.ConcurrentHashMap<Int, QuranPediaBook>()

    /**
     * تنظيف وسوم HTML من نص مقطع quranpedia: <br> إلى سطر جديد، ثم
     * نزع سائر الوسوم، وفك الكيانات الشائعة، ودمج الأسطر الفارغة
     * الزائدة. للنص المنقول فقط — لا يُستدعى على نص آية أبداً.
     */
    private fun cleanQuranPediaHtml(text: String): String =
        text.replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

    /**
     * كتاب quranpedia كامل: GET api.quranpedia.net/books-contents/book-{id}.json
     * (الفهرس الكامل books.json.gz يؤكد المعرّفات). يُحلَّل على الشكل
     * الفعلي: chapters (name/id) وcontents (id/chapter_id/text).
     * ذاكرة مؤقتة لكل كتاب. null عند أي فشل أو فراغ.
     * ملاحظة: أكبر الكتب (439) 1.3 ميغابايت فمهلة القراءة 30 ثانية.
     */
    suspend fun fetchQuranPediaBook(bookId: Int): QuranPediaBook? {
        quranPediaBookCache[bookId]?.let { return it }
        return try {
            val url = "https://api.quranpedia.net/books-contents/book-$bookId.json"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val body = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext ""
                    response.body?.string().orEmpty()
                }
            }
            if (body.isBlank()) return null
            val chapters = ArrayList<QuranPediaBookChapter>()
            val contents = ArrayList<QuranPediaBookContent>()
            try {
                val root = JSONObject(body)
                val chaptersJson = root.optJSONArray("chapters") ?: JSONArray()
                // الفصول قد تتداخل: فصل أب يحمل children بمعرّفاتها
                // (كتاب 439: «الكتاب الأول» أب لخمسة فصول فرعية)،
                // والمقاطع مرتبطة بمعرّفات الفروع لا الأب — فتُسطَّح
                // الفروع بعد أبيها مباشرةً، باسم «الأب — الفرع»، وإلا
                // بقيت معظم مقاطع الكتاب بلا فصل يصل إليها.
                fun addChapter(chapterJson: JSONObject, parentName: String?) {
                    val name = chapterJson.optString("name", "").trim()
                    if (name.isBlank()) return
                    val displayName =
                        if (parentName.isNullOrBlank()) name else "$parentName — $name"
                    chapters.add(QuranPediaBookChapter(chapterJson.optInt("id"), displayName))
                    val childrenJson = chapterJson.optJSONArray("children") ?: JSONArray()
                    for (childIndex in 0 until childrenJson.length()) {
                        addChapter(
                            childrenJson.optJSONObject(childIndex) ?: continue,
                            displayName
                        )
                    }
                }
                for (index in 0 until chaptersJson.length()) {
                    addChapter(chaptersJson.optJSONObject(index) ?: continue, null)
                }
                val contentsJson = root.optJSONArray("contents") ?: JSONArray()
                // المقاطع مرتّبة ترتيب الكتاب، ومعرّف chapter_id فيها
                // أدقّ من بنية الفصول المعرَضة: فصل معروف يملك كل ما
                // يليه حتى الفصل المعروف التالي (كتاب 439: فصوله الـ13
                // المعرَضة تقابل ~60 معرّف مقطع). فيُلحق كل مقطع بآخر
                // فصل معروف سبقه في ترتيب الملف، وإلا بأول فصل؛ فلا
                // يبقى مقطع بلا فصل يصل إليه. هذا ربط موضعي لا موثّق:
                // قيس حياً 2026-11-23 أن 240 من 299 مقطعاً في كتاب 439
                // تحمل chapter_id غير وارد في قائمة فصوله (48 من 61
                // معرّفاً)، فتجميع معظم مقاطعه موضعي لا فصلي مؤكد؛
                // أما كتابا 1727 (0 من 40) و89 (0 من 61) فمطابقان
                // تماماً. وقارئ الكتب ينبّه لهذا في تسميته.
                val knownChapterIds = chapters.map { it.id }.toSet()
                var currentChapterId: Int? = chapters.firstOrNull()?.id
                for (index in 0 until contentsJson.length()) {
                    val content = contentsJson.optJSONObject(index) ?: continue
                    val text = cleanQuranPediaHtml(content.optString("text", ""))
                    if (text.isBlank()) continue
                    val contentChapterId = content.optInt("chapter_id")
                    if (contentChapterId in knownChapterIds) {
                        currentChapterId = contentChapterId
                    }
                    contents.add(
                        QuranPediaBookContent(
                            content.optInt("id"),
                            currentChapterId ?: contentChapterId,
                            text
                        )
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {
                return null
            }
            if (chapters.isEmpty() || contents.isEmpty()) null else {
                val book = QuranPediaBook(chapters, contents)
                quranPediaBookCache[bookId] = book
                book
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w(TAG, "Unable to load quranpedia book $bookId", e)
            null
        }
    }

    // ========================================================================
    // أسباب النزول (قسم القرآن — الكتب): مستودع
    // mostafaahmed97/asbab-al-nuzul-dataset (MIT، فرع main) — مصدره كتاب
    // «صحيح أسباب النزول دراسة حديثية» لإبراهيم محمد العلي. ملف JSON
    // لكل سورة (data/structured/json/NNN.json بصيغة 3 أرقام): مصفوفة
    // مدخلات {surah:int, ayahs:[int], occasions:[string]} (فُحص حياً
    // 2026-11-22: 50 سورة فقط، 200 مدخل، 277 آية، 303 أسباب). 64 سورة
    // بلا ملف أصلاً — الغياب يُميَّز عن الفشل: HTTP 404 يعني السورة
    // بلا ملف فيُعاد خريطة فارغة، وأي فشل آخر يعيد null.
    // ========================================================================

    private val asbabSurahCache = java.util.concurrent.ConcurrentHashMap<Int, Map<Int, List<String>>>()

    /**
     * خريطة أسباب نزول سورة كاملة (آية ← قائمة أسباب)؛ قد تظهر الآية
     * في أكثر من مدخل فتُجمع أسبابها. ذاكرة مؤقتة لكل سورة، وتُحفظ فيها
     * الخريطة الفارغة أيضاً للسور بلا ملف. null عند فشل الشبكة فقط.
     */
    suspend fun fetchAsbabForSurah(surahId: Int): Map<Int, List<String>>? {
        if (surahId !in 1..114) return null
        asbabSurahCache[surahId]?.let { return it }
        return try {
            val fileName = surahId.toString().padStart(3, '0')
            val url = "https://raw.githubusercontent.com/mostafaahmed97/asbab-al-nuzul-dataset/main/data/structured/json/$fileName.json"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(18, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val (code, body) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    response.code to
                        if (response.isSuccessful) response.body?.string().orEmpty() else ""
                }
            }
            // 404 = السورة بلا ملف أسباب نزول في المستودع (64 سورة) —
            // غياب بيانات لا فشل شبكة.
            if (code == 404) {
                val empty = emptyMap<Int, List<String>>()
                asbabSurahCache[surahId] = empty
                return empty
            }
            if (body.isBlank()) return null
            val array = try {
                JSONArray(body)
            } catch (_: Exception) {
                return null
            }
            val built = LinkedHashMap<Int, MutableList<String>>()
            for (index in 0 until array.length()) {
                val entry = array.optJSONObject(index) ?: continue
                if (entry.optInt("surah", -1) != surahId) continue
                val ayahs = entry.optJSONArray("ayahs") ?: continue
                val occasions = entry.optJSONArray("occasions") ?: continue
                for (a in 0 until ayahs.length()) {
                    val ayah = ayahs.optInt(a, 0)
                    if (ayah < 1) continue
                    val list = built.getOrPut(ayah) { ArrayList() }
                    for (o in 0 until occasions.length()) {
                        val occasion = occasions.optString(o, "").trim()
                        if (occasion.isNotBlank() && !list.contains(occasion)) {
                            list.add(occasion)
                        }
                    }
                }
            }
            val result: Map<Int, List<String>> = built
            asbabSurahCache[surahId] = result
            result
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w(TAG, "Unable to load asbab al-nuzul for surah $surahId", e)
            null
        }
    }

    /**
     * أسباب نزول آية واحدة: null عند فشل الشبكة، وقائمة فارغة عند
     * غياب سبب مسجّل لهذه الآية في المصدر.
     */
    suspend fun fetchAsbabForVerse(surahId: Int, ayah: Int): List<String>? {
        val map = fetchAsbabForSurah(surahId) ?: return null
        return map[ayah] ?: emptyList()
    }

    // ========================================================================
    // الآيات المشابهة — المتشابهات (قسم القرآن — الكتب): مستودع
    // Waqar144/Quran_Mutashabihat_Data (CC0 1.0، فرع master)، ملف واحد
    // mutashabiha_data.json (فُحص حياً 2026-11-22: 1,348 موضعاً).
    // الشكل: كائن مفاتيحه الأجزاء "1"–"30"؛ كل مدخل {src:{ayah},
    // muts:[{ayah}...], ctx?}، و"ayah" رقم آية عالمي (1–6236) أو نطاق
    // [a,b] يُفكّ إلى a..b. ليست قائمة شاملة — الأكثر إرباكاً للحفّاظ
    // فقط حسب README — ولا تغطي العالميات 6,201–6,236 إطلاقاً.
    // التحويل عالمي ← (سورة:آية) يُحسب من surahs (versesCount): بداية
    // السورة = 1 + مجموع آيات ما قبلها.
    // ========================================================================

    @Volatile
    private var mutashabihatMap: Map<Int, List<Int>>? = null

    /** فكّ قيمة آية في ملف المتشابهات: عدد صحيح أو نطاق [a,b]. */
    private fun expandMutashabihatAyah(value: Any?): List<Int> {
        return when (value) {
            is Number -> listOf(value.toInt())
            is JSONArray -> {
                if (value.length() == 2) {
                    val first = value.optInt(0, 0)
                    val last = value.optInt(1, 0)
                    if (first > 0 && last >= first) (first..last).toList() else emptyList()
                } else {
                    (0 until value.length())
                        .map { value.optInt(it, 0) }
                        .filter { it > 0 }
                }
            }
            else -> emptyList()
        }
    }

    /**
     * خريطة المتشابهات كاملة (آية عالمية ← قائمة آيات عالمية مشابهة،
     * مرتبة بلا تكرار)؛ تُجلب مرة واحدة وتُحفظ. null عند أي فشل.
     */
    suspend fun fetchMutashabihat(): Map<Int, List<Int>>? {
        mutashabihatMap?.let { return it }
        return try {
            val url = "https://raw.githubusercontent.com/Waqar144/Quran_Mutashabihat_Data/master/mutashabiha_data.json"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(18, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val body = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext ""
                    response.body?.string().orEmpty()
                }
            }
            if (body.isBlank()) return null
            val root = try {
                JSONObject(body)
            } catch (_: Exception) {
                return null
            }
            val built = HashMap<Int, LinkedHashSet<Int>>()
            val juzKeys = root.keys()
            while (juzKeys.hasNext()) {
                val entries = root.optJSONArray(juzKeys.next()) ?: continue
                for (index in 0 until entries.length()) {
                    val entry = entries.optJSONObject(index) ?: continue
                    val srcAyahs = expandMutashabihatAyah(entry.optJSONObject("src")?.opt("ayah"))
                    val muts = entry.optJSONArray("muts") ?: continue
                    val mutAyahs = ArrayList<Int>()
                    for (m in 0 until muts.length()) {
                        mutAyahs += expandMutashabihatAyah(muts.optJSONObject(m)?.opt("ayah"))
                    }
                    for (src in srcAyahs) {
                        if (src !in 1..6236) continue
                        val set = built.getOrPut(src) { LinkedHashSet() }
                        for (mut in mutAyahs) {
                            if (mut in 1..6236 && mut != src) set.add(mut)
                        }
                    }
                }
            }
            val result: Map<Int, List<Int>> = built.mapValues { it.value.sorted() }
            if (result.isEmpty()) null else {
                mutashabihatMap = result
                result
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w(TAG, "Unable to load mutashabihat data", e)
            null
        }
    }

    /**
     * تحويل رقم آية عالمي (1–6236) إلى (سورة، آية داخل السورة).
     * تحقق: 9 ← (2,2)، 1162 ← (8,2)، 3472 ← (31,3)، 6200 ← (107,3)،
     * 6236 ← (114,6). null خارج النطاق.
     */
    fun globalAyahToSurahAyah(global: Int): Pair<Int, Int>? {
        if (global < 1) return null
        var start = 1
        for (surah in surahs) {
            val end = start + surah.versesCount - 1
            if (global in start..end) return surah.id to (global - start + 1)
            start = end + 1
        }
        return null
    }

    /**
     * تحويل (سورة، آية) إلى رقم آية عالمي. -1 خارج النطاق.
     * تحقق: (2,2) ← 9، (8,2) ← 1162، (31,3) ← 3472، (107,3) ← 6200،
     * (114,6) ← 6236.
     */
    fun surahAyahToGlobal(surahId: Int, ayah: Int): Int {
        if (surahId !in 1..114 || ayah < 1) return -1
        // آية غير موجودة في سورتها (مثل 2:300) ليست رقماً عالمياً:
        // كانت تُحسب رقماً وهمياً (7 + 300 = 307) فيُحوَّل عكسياً
        // إلى آية أخرى تماماً — فتُرفض هنا.
        val versesCount = surahs.firstOrNull { it.id == surahId }?.versesCount ?: return -1
        if (ayah > versesCount) return -1
        var total = ayah
        for (surah in surahs) {
            if (surah.id >= surahId) break
            total += surah.versesCount
        }
        return total
    }

    /**
     * الآيات المشابهة لآية واحدة كأزواج (سورة، آية)، مرتبة: قائمة
     * فارغة عند غياب متشابهات مسجّلة أو فشل الجلب.
     */
    suspend fun fetchMutashabihatForVerse(surahId: Int, ayah: Int): List<Pair<Int, Int>> {
        val global = surahAyahToGlobal(surahId, ayah)
        if (global < 1) return emptyList()
        val map = fetchMutashabihat() ?: return emptyList()
        val similar = map[global] ?: return emptyList()
        return similar.mapNotNull { globalAyahToSurahAyah(it) }
    }

    /**
     * يتحقق من إشعار Tanzil الكامل في النسخ المجلوبة من الشبكة (التنزيل والذاكرة
     * المؤقتة المكتوبة منه). لا يُستخدم كبوابة للملف المضمّن، لأن
     * `quran/uthmani.json` لا يحمل حقل إشعار أصلاً؛ قبول الملف المضمّن يعتمد
     * على التحقق البنيوي الصارم ([isCompleteQuran]) مع نسبة المصدر أعلاه.
     */
    private fun hasTanzilNotice(jsonText: String): Boolean {
        return try {
            val root = JSONObject(jsonText)
            val notice = root.optString("sourceNotice", root.optString("notice", ""))
            notice.contains("Tanzil Quran Text") &&
                notice.contains("Copyright (C) 2007-2021 Tanzil Project") &&
                notice.contains("Creative Commons Attribution 3.0") &&
                notice.contains("CHANGING IT IS NOT ALLOWED") &&
                notice.contains("tanzil.net")
        } catch (_: Exception) {
            false
        }
    }

    /** تحقق بنيوي من اكتمال السور والآيات وتسلسل أرقامها دون تعديل النص. */
    private fun isCompleteQuran(ayahs: List<RawAyah>): Boolean {
        if (ayahs.size != 6236) return false
        val grouped = ayahs.groupBy { it.surah }
        if (grouped.keys != (1..114).toSet()) return false
        return grouped.all { (surah, verses) ->
            val numbers = verses.map { it.ayah }.sorted()
            numbers == (1..numbers.size).toList() && verses.all { it.text.isNotBlank() }
        }
    }

    /**
     * تحميل نسخة عثمانية جاهزة من Tanzil عبر حزمة quran-json.
     * لا يُنشأ نص قرآني هنا: كل نص آية يُنقل كما ورد في JSON المصدر.
     * تُحفظ النسخة محلياً بعد اكتمال التحقق لتعمل القراءة لاحقاً دون اتصال.
     */
    suspend fun downloadAndCacheFromNetwork(context: Context): Boolean =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                // تنزيل السور بالتوازي المحدود لتقليل زمن الإعداد دون إغراق الخادم.
                val semaphore = Semaphore(6)
                val chapters = coroutineScope {
                    (1..114).map { surahNumber ->
                        async {
                            semaphore.withPermit {
                                val url = "https://quran-json.risanb.com/text/uthmani/chapters/$surahNumber.json"
                                val request = okhttp3.Request.Builder().url(url).get().build()
                                val responseText = client.newCall(request).execute().use { response ->
                                    if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code} for surah $surahNumber")
                                    response.body?.string() ?: throw java.io.IOException("Empty response for surah $surahNumber")
                                }
                                val chapter = JSONObject(responseText)
                                if (chapter.optInt("id", -1) != surahNumber) {
                                    throw java.io.IOException("Unexpected surah identifier: $surahNumber")
                                }
                                val verses = chapter.optJSONArray("verses")
                                    ?: throw java.io.IOException("Missing verses for surah $surahNumber")
                                if (verses.length() == 0) throw java.io.IOException("No verses for surah $surahNumber")

                                for (index in 0 until verses.length()) {
                                    val verse = verses.optJSONObject(index)
                                        ?: throw java.io.IOException("Invalid verse object at $surahNumber:${index + 1}")
                                    if (verse.optInt("id", -1) != index + 1 || verse.optString("text").isBlank()) {
                                        throw java.io.IOException("Invalid verse sequence at $surahNumber:${index + 1}")
                                    }
                                }
                                Triple(surahNumber, verses, chapter.optString("notice"))
                            }
                        }
                    }.awaitAll().sortedBy { it.first }
                }

                val downloadedSurahs = JSONArray()
                var totalAyahs = 0
                val sourceNotice = chapters.firstOrNull()?.third.orEmpty()
                val noticeIsValid = sourceNotice.contains("Tanzil Quran Text") &&
                    sourceNotice.contains("Copyright (C) 2007-2021 Tanzil Project") &&
                    sourceNotice.contains("Creative Commons Attribution 3.0") &&
                    sourceNotice.contains("CHANGING IT IS NOT ALLOWED") &&
                    sourceNotice.contains("tanzil.net")
                if (!noticeIsValid || chapters.any { !it.third.contains("Tanzil Quran Text") }) {
                    throw java.io.IOException("Required Tanzil copyright notice is missing from the Quran dataset")
                }
                chapters.forEach { (surahNumber, verses, _) ->
                    // نحافظ على كائنات الآيات ونصوصها كما وصلت من المصدر دون إعادة صياغة.
                    downloadedSurahs.put(JSONObject().put("id", surahNumber).put("verses", verses))
                    totalAyahs += verses.length()
                }

                if (downloadedSurahs.length() != 114 || totalAyahs != 6236) {
                    throw java.io.IOException("Quran dataset validation failed: ${downloadedSurahs.length()} surahs, $totalAyahs ayahs")
                }

                val cachedDocument = JSONObject()
                    .put("sourceNotice", sourceNotice)
                    .put("surahs", downloadedSurahs)
                val parsed = parseQuranJson(cachedDocument.toString())
                val parsedCount = parsed.size
                if (!isCompleteQuran(parsed)) {
                    throw java.io.IOException("Parsed Quran validation failed: $parsedCount ayahs")
                }

                val cacheFile = java.io.File(context.filesDir, "quran_uthmani_tanzil.json")
                val temporaryFile = java.io.File(context.filesDir, "quran_uthmani_tanzil.tmp")
                temporaryFile.writeText(cachedDocument.toString(), Charsets.UTF_8)
                if (!temporaryFile.renameTo(cacheFile)) {
                    temporaryFile.copyTo(cacheFile, overwrite = true)
                    temporaryFile.delete()
                }

                versesMap = parsed.groupBy { it.surah }
                Log.i(TAG, "Downloaded and cached verified Uthmani text: 114 surahs, 6236 ayahs")
                true
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e(TAG, "Unable to download verified Quran dataset: ${e.message}", e)
                false
            }
        }

    /** تحميل النسخة التي سبق تنزيلها والتحقق من اكتمالها قبل استخدامها. */
    fun loadFromCache(context: Context): Boolean {
        if (versesMap?.values?.flatten()?.let { isCompleteQuran(it) } == true) return true
        return try {
            val cacheFile = java.io.File(context.filesDir, "quran_uthmani_tanzil.json")
            if (!cacheFile.isFile || cacheFile.length() == 0L) return false
            val cachedJson = cacheFile.readText(Charsets.UTF_8)
            if (!hasTanzilNotice(cachedJson)) {
                Log.w(TAG, "Cached Quran dataset is missing the required Tanzil notice")
                return false
            }
            val parsed = parseQuranJson(cachedJson)
            if (!isCompleteQuran(parsed)) {
                Log.w(TAG, "Cached Quran dataset failed validation")
                return false
            }
            versesMap = parsed.groupBy { it.surah }
            Log.i(TAG, "Loaded verified Uthmani text from local cache")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Unable to load Quran cache: ${e.message}")
            false
        }
    }

    /**
     * دالة تحميل آمنة من assets للقرآن الكريم كاملاً بالرسم العثماني.
     * تفحص المسارات المحتملة: "quran/uthmani.json" ثم "quran/quran.json".
     * البوابة هنا هي التحقق البنيوي الصارم ([isCompleteQuran]): 6,236 آية
     * بالضبط، وسور 1–114 متسلسلة بأرقام آيات مكتملة ونص غير فارغ. الملف
     * المضمّن لا يحمل حقل إشعار Tanzil، فاشتراط الإشعار كان يرفضه دائماً
     * ويُسقط أول تشغيل على الشبكة؛ الملف الذي يجتاز التحقق البنيوي يُقبل
     * وتُسجَّل نسبة مصدره، والذي يفشل يُتخطى ولا يُوثق به أبداً.
     */
    fun loadFromAssets(context: Context): Boolean {
        if (versesMap?.values?.flatten()?.let { isCompleteQuran(it) } == true) {
            return true
        }

        val assetPaths = listOf("quran/uthmani.json", "quran/quran.json", "uthmani.json", "quran.json")
        for (path in assetPaths) {
            try {
                val jsonString = context.assets.open(path).use { inputStream ->
                    inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
                if (jsonString.isNotBlank()) {
                    val parsed = parseQuranJson(jsonString)
                    if (isCompleteQuran(parsed)) {
                        versesMap = parsed.groupBy { it.surah }
                        Log.i(TAG, "Successfully loaded ${parsed.size} ayahs from asset: $path ($QURAN_TEXT_ATTRIBUTION)")
                        return true
                    } else {
                        Log.w(TAG, "Skipping Quran asset that failed structural validation: $path")
                    }
                }
            } catch (_: java.io.FileNotFoundException) {
                // الملف غير موجود في هذا المسار، يتم فحص المسار التالي
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading $path from assets: ${e.message}")
            }
        }
        return false
    }

    /**
     * تحليل مرن لمحتوى الـ JSON لدعم الهياكل المختلفة (مصفوفة آيات أو كائن مجمع)
     */
    private fun parseQuranJson(jsonStr: String): List<RawAyah> {
        val result = mutableListOf<RawAyah>()
        try {
            val trimmed = jsonStr.trim()
            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val surah = obj.optInt("surah", obj.optInt("surah_number", obj.optInt("chapter", obj.optInt("s", 0))))
                    val ayah = obj.optInt("ayah", obj.optInt("verse", obj.optInt("ayah_number", obj.optInt("a", 0))))
                    val text = obj.optString("text", obj.optString("verse_text", obj.optString("uthmani", obj.optString("ar", ""))))
                    if (surah > 0 && ayah > 0 && text.isNotBlank()) {
                        result.add(RawAyah(surah, ayah, text))
                    }
                }
            } else if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                val array = root.optJSONArray("verses")
                    ?: root.optJSONArray("ayahs")
                    ?: root.optJSONArray("quran")
                    ?: root.optJSONArray("data")
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val surah = obj.optInt("surah", obj.optInt("surah_number", obj.optInt("chapter", obj.optInt("s", 0))))
                        val ayah = obj.optInt("ayah", obj.optInt("verse", obj.optInt("ayah_number", obj.optInt("a", 0))))
                        val text = obj.optString("text", obj.optString("verse_text", obj.optString("uthmani", obj.optString("ar", ""))))
                        if (surah > 0 && ayah > 0 && text.isNotBlank()) {
                            result.add(RawAyah(surah, ayah, text))
                        }
                    }
                } else if (root.has("surahs")) {
                    val surahsArray = root.optJSONArray("surahs")
                    if (surahsArray != null) {
                        for (i in 0 until surahsArray.length()) {
                            val sObj = surahsArray.optJSONObject(i) ?: continue
                            val sId = sObj.optInt("id", sObj.optInt("number", i + 1))
                            val vArray = sObj.optJSONArray("verses") ?: sObj.optJSONArray("ayahs")
                            if (vArray != null) {
                                for (j in 0 until vArray.length()) {
                                    val vObj = vArray.optJSONObject(j)
                                    if (vObj != null) {
                                        val ayahNum = vObj.optInt("id", vObj.optInt("number", j + 1))
                                        val text = vObj.optString("text", vObj.optString("uthmani", ""))
                                        if (text.isNotBlank()) {
                                            result.add(RawAyah(sId, ayahNum, text))
                                        }
                                    } else {
                                        val text = vArray.optString(j, "")
                                        if (text.isNotBlank()) {
                                            result.add(RawAyah(sId, j + 1, text))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Quran JSON: ${e.message}")
        }
        return result
    }

    /**
     * نص آية محددة من الذاكرة المحملة (يُستخدم في قارئ الصفحات).
     * يُرجع null عند غياب التحميل — لا نص ملفّق.
     */
    fun getVerseText(surahId: Int, ayah: Int): String? =
        versesMap?.get(surahId)?.firstOrNull { it.ayah == ayah }?.text

    /**
     * إرجاع آية عشوائية من النص القرآني المحمّل مع اسم السورة والتفسير المتاح.
     * تُرجع null عند عدم تحميل ملف المصحف بدلاً من اختلاق نص بديل.
     */
    fun getRandomVerse(): QuranVerseDetail? {
        val loadedAyahs = versesMap?.values?.flatten()?.takeIf { it.isNotEmpty() } ?: return null
        val ayah = loadedAyahs.random()
        return QuranVerseDetail(
            surahNumber = ayah.surah,
            surahName = getSurahName(ayah.surah),
            verseNumber = ayah.ayah,
            verseText = ayah.text,
            tafseer = tafsirMap?.get("${ayah.surah}:${ayah.ayah}") ?: "",
            tajweedNotes = ""
        )
    }

    /**
     * آية اليوم (الدفعة 3): حتمية برقم اليوم — الآية نفسها لكل المستخدمين
     * في اليوم نفسه، وتتغير كل يوم، وتدور على المصحف كاملاً (6,236 آية)
     * خلال ~17 سنة. تُرجع null عند عدم تحميل ملف المصحف
     * بدلاً من اختلاق نص بديل.
     */
    fun getVerseOfDay(): QuranVerseDetail? {
        val loadedAyahs = versesMap?.values?.flatten()?.takeIf { it.isNotEmpty() } ?: return null
        val ordered = loadedAyahs.sortedWith(compareBy({ it.surah }, { it.ayah }))
        val cal = java.util.Calendar.getInstance()
        // رقم اليوم منذ بداية التقويم (سنة × 366 + يوم السنة): يتقدم
        // 1 كل يوم. أما رقم يوم السنة وحده (1..366) فكان يحصر «آية
        // اليوم» في أول 366 آية أبداً (الفاتحة والبقرة وبداية آل عمران).
        val dayIndex = cal.get(java.util.Calendar.YEAR) * 366 +
            cal.get(java.util.Calendar.DAY_OF_YEAR)
        val ayah = ordered[dayIndex % ordered.size]
        return QuranVerseDetail(
            surahNumber = ayah.surah,
            surahName = getSurahName(ayah.surah),
            verseNumber = ayah.ayah,
            verseText = ayah.text,
            tafseer = tafsirMap?.get("${ayah.surah}:${ayah.ayah}") ?: "",
            tajweedNotes = ""
        )
    }

    /** اسم السورة علناً (يُستخدم في قارئ الصفحات). */
    fun surahNameOf(surahId: Int): String = getSurahName(surahId)


    /**
     * قائمة الـ 114 سورة كاملة موثقة بالترتيب والتفاصيل
     */
    val surahs: List<QuranSurahItem> by lazy {
        listOf(
            QuranSurahItem(1, "الفاتحة", "Al-Fatiha", "مكية", 7, 1, isLocked = false, isMastered = false, score = 0, "أحكام المد الطبيعي والنون الساكنة", emptyList(), "أم الكتاب والسبع المثاني والشفاء النافع"),
            QuranSurahItem(2, "البقرة", "Al-Baqarah", "مدنية", 286, 1, isLocked = false, isMastered = false, score = 0, "أحكام الميم الساكنة والإدغام بغنة", emptyList(), "سنام القرآن وطاردة الشياطين، فيها آية الكرسي وخواتيمها كنز من تحت العرش"),
            QuranSurahItem(3, "آل عمران", "Ali 'Imran", "مدنية", 200, 3, isLocked = false, isMastered = false, score = 0, "المد المتصل والمنفصل", emptyList(), "إحدى الزهراوين، تحاج عن صاحبها يوم القيامة"),
            QuranSurahItem(4, "النساء", "An-Nisa", "مدنية", 176, 4, isLocked = false, isMastered = false, score = 0, "أحكام الوصل والوقف والمدود", emptyList()),
            QuranSurahItem(5, "المائدة", "Al-Ma'idah", "مدنية", 120, 6, isLocked = false, isMastered = false, score = 0, "أحكام الإظهار والترقيق", emptyList()),
            QuranSurahItem(6, "الأنعام", "Al-An'am", "مكية", 165, 7, isLocked = false, isMastered = false, score = 0, "أحكام القلقلة وتفخيم اللام", emptyList()),
            QuranSurahItem(7, "الأعراف", "Al-A'raf", "مكية", 206, 8, isLocked = false, isMastered = false, score = 0, "أحكام السجدات والمد اللازم", emptyList()),
            QuranSurahItem(8, "الأنفال", "Al-Anfal", "مدنية", 75, 9, isLocked = false, isMastered = false, score = 0, "أحكام التقاء الساكنين والغنة", emptyList()),
            QuranSurahItem(9, "التوبة", "At-Tawbah", "مدنية", 129, 10, isLocked = false, isMastered = false, score = 0, "أحكام ترك البسملة والاستعاذة", emptyList()),
            QuranSurahItem(10, "يونس", "Yunus", "مكية", 109, 11, isLocked = false, isMastered = false, score = 0, "الحروف المقطعة وأحكام الراء", emptyList()),
            QuranSurahItem(11, "هود", "Hud", "مكية", 123, 11, isLocked = false, isMastered = false, score = 0, "أحكام الإمالة في (مَجْرَاهَا)", emptyList()),
            QuranSurahItem(12, "يوسف", "Yusuf", "مكية", 111, 12, isLocked = false, isMastered = false, score = 0, "أحكام الإشمام والروم في (لَا تَأْمَنَّا)", emptyList()),
            QuranSurahItem(13, "الرعد", "Ar-Ra'd", "مدنية", 43, 13, isLocked = false, isMastered = false, score = 0, "أحكام تفخيم الراء وقلقلة الدال", emptyList()),
            QuranSurahItem(14, "إبراهيم", "Ibrahim", "مكية", 52, 13, isLocked = false, isMastered = false, score = 0, "المدود والوقف التام", emptyList()),
            QuranSurahItem(15, "الحجر", "Al-Hijr", "مكية", 99, 14, isLocked = false, isMastered = false, score = 0, "الإظهار المطلق والغنة", emptyList()),
            QuranSurahItem(16, "النحل", "An-Nahl", "مكية", 128, 14, isLocked = false, isMastered = false, score = 0, "أحكام الاستعاذة والنبر", emptyList()),
            QuranSurahItem(17, "الإسراء", "Al-Isra", "مكية", 111, 15, isLocked = false, isMastered = false, score = 0, "أحكام الوقف الكافي والتفخيم", emptyList()),
            QuranSurahItem(18, "الكهف", "Al-Kahf", "مكية", 110, 15, isLocked = false, isMastered = false, score = 0, "السكتات اللطيفة والإخفاء الشفوي", emptyList(), "نور ما بين الجمعتين وعصمة من فتنة الدجال لمن حفظ أوائلها"),
            QuranSurahItem(19, "مريم", "Maryam", "مكية", 98, 16, isLocked = false, isMastered = false, score = 0, "المد اللازم الحرفي في (كهيعص)", emptyList()),
            QuranSurahItem(20, "طه", "Ta-Ha", "مكية", 135, 16, isLocked = false, isMastered = false, score = 0, "أحكام الفواتح والإمالة الصغرى", emptyList()),
            QuranSurahItem(21, "الأنبياء", "Al-Anbiya", "مكية", 112, 17, isLocked = false, isMastered = false, score = 0, "أحكام القلقلة والإظهار", emptyList()),
            QuranSurahItem(22, "الحج", "Al-Hajj", "مدنية", 78, 17, isLocked = false, isMastered = false, score = 0, "سجدات التلاوة وأحكام المد", emptyList()),
            QuranSurahItem(23, "المؤمنون", "Al-Mu'minun", "مكية", 118, 18, isLocked = false, isMastered = false, score = 0, "ترقيق الميم والغنة", emptyList()),
            QuranSurahItem(24, "النور", "An-Nur", "مدنية", 64, 18, isLocked = false, isMastered = false, score = 0, "آية النور وأحكام المد المتصل", emptyList()),
            QuranSurahItem(25, "الفرقان", "Al-Furqan", "مكية", 77, 18, isLocked = false, isMastered = false, score = 0, "صفات عباد الرحمن والتفخيم", emptyList()),
            QuranSurahItem(26, "الشعراء", "Ash-Shu'ara", "مكية", 227, 19, isLocked = false, isMastered = false, score = 0, "الفواتح الحرفية والمدود", emptyList()),
            QuranSurahItem(27, "النمل", "An-Naml", "مكية", 93, 19, isLocked = false, isMastered = false, score = 0, "البسملة وسط السورة", emptyList()),
            QuranSurahItem(28, "القصص", "Al-Qasas", "مكية", 88, 20, isLocked = false, isMastered = false, score = 0, "أحكام التنوين والوقف", emptyList()),
            QuranSurahItem(29, "العنكبوت", "Al-'Ankabut", "مكية", 69, 20, isLocked = false, isMastered = false, score = 0, "أحكام الهمزات والتفخيم", emptyList()),
            QuranSurahItem(30, "الروم", "Ar-Rum", "مكية", 60, 21, isLocked = false, isMastered = false, score = 0, "تفخيم الراء وترقيقها", emptyList()),
            QuranSurahItem(31, "لقمان", "Luqman", "مكية", 34, 21, isLocked = false, isMastered = false, score = 0, "وصايا لقمان والمد العارض", emptyList()),
            QuranSurahItem(32, "السجدة", "As-Sajdah", "مكية", 30, 21, isLocked = false, isMastered = false, score = 0, "سجدة التلاوة والمد اللازم", emptyList()),
            QuranSurahItem(33, "الأحزاب", "Al-Ahzab", "مدنية", 73, 21, isLocked = false, isMastered = false, score = 0, "أحكام الصلاة على النبي ﷺ والمد", emptyList()),
            QuranSurahItem(34, "سبأ", "Saba", "مكية", 54, 22, isLocked = false, isMastered = false, score = 0, "أحكام الإظهار والترقيق", emptyList()),
            QuranSurahItem(35, "فاطر", "Fatir", "مكية", 45, 22, isLocked = false, isMastered = false, score = 0, "صفات الحروف والمدود", emptyList()),
            QuranSurahItem(36, "يس", "Ya-Sin", "مكية", 83, 22, isLocked = false, isMastered = false, score = 0, "أحكام الإظهار المطلق والغنة", emptyList(), "قلب القرآن، لا يقرؤها أحد يريد الله والدار الآخرة إلا غفر له"),
            QuranSurahItem(37, "الصافات", "As-Saffat", "مكية", 182, 23, isLocked = false, isMastered = false, score = 0, "المد اللازم الكلمي المثقل", emptyList()),
            QuranSurahItem(38, "ص", "Sad", "مكية", 88, 23, isLocked = false, isMastered = false, score = 0, "حرف الصاد وتفخيمه والاستعلاء", emptyList()),
            QuranSurahItem(39, "الزمر", "Az-Zumar", "مكية", 75, 23, isLocked = false, isMastered = false, score = 0, "أحكام الرجاء والاستغفار", emptyList()),
            QuranSurahItem(40, "غافر", "Ghafir", "مكية", 85, 24, isLocked = false, isMastered = false, score = 0, "الحواميم والمد الحرفي (حم)", emptyList()),
            QuranSurahItem(41, "فصلت", "Fussilat", "مكية", 54, 24, isLocked = false, isMastered = false, score = 0, "تسهيل الهمزة في (أَأَعْجَمِيٌّ)", emptyList()),
            QuranSurahItem(42, "الشورى", "Ash-Shura", "مكية", 53, 25, isLocked = false, isMastered = false, score = 0, "مد حرف (عسق) 4 أو 6 حركات", emptyList()),
            QuranSurahItem(43, "الزخرف", "Az-Zukhruf", "مكية", 89, 25, isLocked = false, isMastered = false, score = 0, "أحكام الوصل والفصل", emptyList()),
            QuranSurahItem(44, "الدخان", "Ad-Dukhan", "مكية", 59, 25, isLocked = false, isMastered = false, score = 0, "ليلة القدر والغنة", emptyList()),
            QuranSurahItem(45, "الجاثية", "Al-Jathiyah", "مكية", 37, 25, isLocked = false, isMastered = false, score = 0, "أحكام التنوين والوقف", emptyList()),
            QuranSurahItem(46, "الأحقاف", "Al-Ahqaf", "مكية", 35, 26, isLocked = false, isMastered = false, score = 0, "بر الوالدين وأحكام الإخفاء", emptyList()),
            QuranSurahItem(47, "محمد", "Muhammad", "مدنية", 38, 26, isLocked = false, isMastered = false, score = 0, "أحكام الغنة والهمس", emptyList()),
            QuranSurahItem(48, "الفتح", "Al-Fath", "مدنية", 29, 26, isLocked = false, isMastered = false, score = 0, "ضم الهاء في (عَلَيْهُ اللَّهَ)", emptyList()),
            QuranSurahItem(49, "الحجرات", "Al-Hujurat", "مدنية", 18, 26, isLocked = false, isMastered = false, score = 0, "أخلاق المؤمنين وأحكام النداء", emptyList()),
            QuranSurahItem(50, "ق", "Qaf", "مكية", 45, 26, isLocked = false, isMastered = false, score = 0, "حرف القاف وصفة القلقلة والاستعلاء", emptyList()),
            QuranSurahItem(51, "الذاريات", "Adh-Dhariyat", "مكية", 60, 26, isLocked = false, isMastered = false, score = 0, "القسم والمد العارض", emptyList()),
            QuranSurahItem(52, "الطور", "At-Tur", "مكية", 49, 27, isLocked = false, isMastered = false, score = 0, "الهمس في التاء والطاء", emptyList()),
            QuranSurahItem(53, "النجم", "An-Najm", "مكية", 62, 27, isLocked = false, isMastered = false, score = 0, "سجدة النجم والمدود", emptyList()),
            QuranSurahItem(54, "القمر", "Al-Qamar", "مكية", 55, 27, isLocked = false, isMastered = false, score = 0, "ترقيق الراء في (مُسْتَمِرّ)", emptyList()),
            QuranSurahItem(55, "الرحمن", "Ar-Rahman", "مدنية", 78, 27, isLocked = false, isMastered = false, score = 0, "نبر الآيات والمد العارض للسكون", emptyList(), "عروس القرآن، تذكر بآلاء الله ونعمه الظاهرة والباطنة"),
            QuranSurahItem(56, "الواقعة", "Al-Waqi'ah", "مكية", 96, 27, isLocked = false, isMastered = false, score = 0, "أحكام القلقلة والتفخيم والترقيق", emptyList()),
            QuranSurahItem(57, "الحديد", "Al-Hadid", "مدنية", 29, 27, isLocked = false, isMastered = false, score = 0, "المسبحات وأحكام النون", emptyList()),
            QuranSurahItem(58, "المجادلة", "Al-Mujadila", "مدنية", 22, 28, isLocked = false, isMastered = false, score = 0, "لفظ الجلالة في كل آية", emptyList()),
            QuranSurahItem(59, "الحشر", "Al-Hashr", "مدنية", 24, 28, isLocked = false, isMastered = false, score = 0, "أواخر الحشر وأسماء الله الحسنى", emptyList()),
            QuranSurahItem(60, "الممتحنة", "Al-Mumtahanah", "مدنية", 13, 28, isLocked = false, isMastered = false, score = 0, "الإدغام والإخفاء", emptyList()),
            QuranSurahItem(61, "الصف", "As-Saff", "مدنية", 14, 28, isLocked = false, isMastered = false, score = 0, "المد اللازم ونصر الله", emptyList()),
            QuranSurahItem(62, "الجمعة", "Al-Jumu'ah", "مدنية", 11, 28, isLocked = false, isMastered = false, score = 0, "أحكام نداء الجمعة والترتيل", emptyList()),
            QuranSurahItem(63, "المنافقون", "Al-Munafiqun", "مدنية", 11, 28, isLocked = false, isMastered = false, score = 0, "الإنفاق قبل الموت والهمس", emptyList()),
            QuranSurahItem(64, "التغابن", "At-Taghabun", "مدنية", 18, 28, isLocked = false, isMastered = false, score = 0, "التوكل على الله والمد", emptyList()),
            QuranSurahItem(65, "الطلاق", "At-Talaq", "مدنية", 12, 28, isLocked = false, isMastered = false, score = 0, "التقوى وتيسير الأمور", emptyList()),
            QuranSurahItem(66, "التحريم", "At-Tahrim", "مدنية", 12, 28, isLocked = false, isMastered = false, score = 0, "التوبة النصوح والإخفاء", emptyList()),
            QuranSurahItem(67, "الملك", "Al-Mulk", "مكية", 30, 29, isLocked = false, isMastered = false, score = 0, "أحكام الميم الساكنة والمد اللازم", emptyList(), "المنجية من عذاب القبر والشافعة لصاحبها حتى يغفر له"),
            QuranSurahItem(68, "القلم", "Al-Qalam", "مكية", 52, 29, isLocked = false, isMastered = false, score = 0, "إظهار النون في (ن ۚ وَالْقَلَمِ)", emptyList()),
            QuranSurahItem(69, "الحاقة", "Al-Haqqah", "مكية", 52, 29, isLocked = false, isMastered = false, score = 0, "المد اللازم الكلمي المثقل 6 حركات", emptyList()),
            QuranSurahItem(70, "المعارج", "Al-Ma'arij", "مكية", 44, 29, isLocked = false, isMastered = false, score = 0, "أحكام الصبر الجميل", emptyList()),
            QuranSurahItem(71, "نوح", "Nuh", "مكية", 28, 29, isLocked = false, isMastered = false, score = 0, "الاستغفار وثمراته", emptyList()),
            QuranSurahItem(72, "الجن", "Al-Jinn", "مكية", 28, 29, isLocked = false, isMastered = false, score = 0, "سماع القرآن والتوحيد", emptyList()),
            QuranSurahItem(73, "المزمل", "Al-Muzzammil", "مكية", 20, 29, isLocked = false, isMastered = false, score = 0, "أمر الترتيل (وَرَتِّلِ الْقُرْآنَ تَرْتِيلًا)", emptyList()),
            QuranSurahItem(74, "المدثر", "Al-Muddathir", "مكية", 56, 29, isLocked = false, isMastered = false, score = 0, "الدعوة والتكبير", emptyList()),
            QuranSurahItem(75, "القيامة", "Al-Qiyamah", "مكية", 40, 29, isLocked = false, isMastered = false, score = 0, "السكت اللطيف في (مَنْ ۜ رَاقٍ)", emptyList()),
            QuranSurahItem(76, "الإنسان", "Al-Insan", "مدنية", 31, 29, isLocked = false, isMastered = false, score = 0, "نعيم الجنة والألفات السبعة", emptyList()),
            QuranSurahItem(77, "المرسلات", "Al-Mursalat", "مكية", 50, 29, isLocked = false, isMastered = false, score = 0, "إدغام القاف في الكاف في (أَلَمْ نَخْلُقكُّم)", emptyList()),
            QuranSurahItem(78, "النبأ", "An-Naba", "مكية", 40, 30, isLocked = false, isMastered = false, score = 0, "المد العارض للسكون والقلقلة", emptyList()),
            QuranSurahItem(79, "النازعات", "An-Nazi'at", "مكية", 46, 30, isLocked = false, isMastered = false, score = 0, "الهمس والغنة", emptyList()),
            QuranSurahItem(80, "عبس", "'Abasa", "مكية", 42, 30, isLocked = false, isMastered = false, score = 0, "تذكرة القرآن والتفخيم", emptyList()),
            QuranSurahItem(81, "التكوير", "At-Takwir", "مكية", 29, 30, isLocked = false, isMastered = false, score = 0, "أحكام القسم وإذا الشمس كورت", emptyList()),
            QuranSurahItem(82, "الانفطار", "Al-Infitar", "مكية", 19, 30, isLocked = false, isMastered = false, score = 0, "الكرام الكاتبين والإخفاء", emptyList()),
            QuranSurahItem(83, "المطففين", "Al-Mutaffifin", "مكية", 36, 30, isLocked = false, isMastered = false, score = 0, "السكت اللطيف في (كَلَّا ۖ بَلْ ۜ رَانَ)", emptyList()),
            QuranSurahItem(84, "الانشقاق", "Al-Inshiqaq", "مكية", 25, 30, isLocked = false, isMastered = false, score = 0, "سجدة الانشقاق والحساب اليسير", emptyList()),
            QuranSurahItem(85, "البروج", "Al-Buruj", "مكية", 22, 30, isLocked = false, isMastered = false, score = 0, "القلقلة الكبرى في الوقف على (الْبُرُوجِ)", emptyList()),
            QuranSurahItem(86, "الطارق", "At-Tariq", "مكية", 17, 30, isLocked = false, isMastered = false, score = 0, "أحكام القلقلة وتفخيم الطاء والراء", emptyList()),
            QuranSurahItem(87, "الأعلى", "Al-A'la", "مكية", 19, 30, isLocked = false, isMastered = false, score = 0, "الإمالة والتسهيل والتفخيم", emptyList()),
            QuranSurahItem(88, "الغاشية", "Al-Ghashiyah", "مكية", 26, 30, isLocked = false, isMastered = false, score = 0, "نغم الآيات والتفكر في خلق الله", emptyList()),
            QuranSurahItem(89, "الفجر", "Al-Fajr", "مكية", 30, 30, isLocked = false, isMastered = false, score = 0, "تفخيم الراء وقلقلة الجيم", emptyList()),
            QuranSurahItem(90, "البلد", "Al-Balad", "مكية", 20, 30, isLocked = false, isMastered = false, score = 0, "القلقلة في (الْبَلَدِ) والتواصي بالمرحمة", emptyList()),
            QuranSurahItem(91, "الشمس", "Ash-Shams", "مكية", 15, 30, isLocked = false, isMastered = false, score = 0, "تزكية النفس وتفخيم الشين والصاد", emptyList()),
            QuranSurahItem(92, "الليل", "Al-Layl", "مكية", 21, 30, isLocked = false, isMastered = false, score = 0, "أحكام التيسير والإنفاق", emptyList()),
            QuranSurahItem(93, "الضحى", "Ad-Duha", "مكية", 11, 30, isLocked = false, isMastered = false, score = 0, "أحكام المد والوقف ورعاية اليتيم", emptyList()),
            QuranSurahItem(94, "الشرح", "Ash-Sharh", "مكية", 8, 30, isLocked = false, isMastered = false, score = 0, "أحكام الإخفاء وانشراح الصدر", emptyList()),
            QuranSurahItem(95, "التين", "At-Tin", "مكية", 8, 30, isLocked = false, isMastered = false, score = 0, "قلقلة الدال وخلق الإنسان", emptyList()),
            QuranSurahItem(96, "العلق", "Al-'Alaq", "مكية", 19, 30, isLocked = false, isMastered = false, score = 0, "أول ما نزل وسجدة التلاوة", emptyList()),
            QuranSurahItem(97, "القدر", "Al-Qadr", "مكية", 5, 30, isLocked = false, isMastered = false, score = 0, "أحكام التفخيم والترقيق في الراء", emptyList()),
            QuranSurahItem(98, "البينة", "Al-Bayyinah", "مدنية", 8, 30, isLocked = false, isMastered = false, score = 0, "إخلاص الدين وأحكام النون", emptyList()),
            QuranSurahItem(99, "الزلزلة", "Az-Zalzalah", "مدنية", 8, 30, isLocked = false, isMastered = false, score = 0, "الإدغام بغنة في (فَمَن يَعْمَلْ)", emptyList()),
            QuranSurahItem(100, "العاديات", "Al-'Adiyat", "مكية", 11, 30, isLocked = false, isMastered = false, score = 0, "القلقلة الكبرى في أواخر الآيات", emptyList()),
            QuranSurahItem(101, "القارعة", "Al-Qari'ah", "مكية", 11, 30, isLocked = false, isMastered = false, score = 0, "نبر القارعة وثقل الموازين", emptyList()),
            QuranSurahItem(102, "التكاثر", "At-Takathur", "مكية", 8, 30, isLocked = false, isMastered = false, score = 0, "عين اليقين والسؤال عن النعيم", emptyList()),
            QuranSurahItem(103, "العصر", "Al-'Asr", "مكية", 3, 30, isLocked = false, isMastered = false, score = 0, "ترقيق وتفخيم الراء وقيمة الوقت", emptyList()),
            QuranSurahItem(104, "الهمزة", "Al-Humazah", "مكية", 9, 30, isLocked = false, isMastered = false, score = 0, "الحذر من الغيبة والهمز", emptyList()),
            QuranSurahItem(105, "الفيل", "Al-Fil", "مكية", 5, 30, isLocked = false, isMastered = false, score = 0, "الإظهار الشفوي في (أَلَمْ تَرَ)", emptyList()),
            QuranSurahItem(106, "قريش", "Quraysh", "مكية", 4, 30, isLocked = false, isMastered = false, score = 0, "مد اللين في (قُرَيْشٍ - خَوْفٍ)", emptyList()),
            QuranSurahItem(107, "الماعون", "Al-Ma'un", "مكية", 7, 30, isLocked = false, isMastered = false, score = 0, "إخلاص العبادة وإعانة المحتاج", emptyList()),
            QuranSurahItem(108, "الكوثر", "Al-Kawthar", "مكية", 3, 30, isLocked = false, isMastered = false, score = 0, "أحكام قلقلة الدال والتفخيم", emptyList()),
            QuranSurahItem(109, "الكافرون", "Al-Kafirun", "مكية", 6, 30, isLocked = false, isMastered = false, score = 0, "المد المنفصل والبراءة من الشرك", emptyList()),
            QuranSurahItem(110, "النصر", "An-Nasr", "مدنية", 3, 30, isLocked = false, isMastered = false, score = 0, "المد المتصل في (جَاءَ) والاستغفار", emptyList()),
            QuranSurahItem(111, "المسد", "Al-Masad", "مكية", 5, 30, isLocked = false, isMastered = false, score = 0, "القلقلة الكبرى في (وَتَبَّ)", emptyList()),
            QuranSurahItem(112, "الإخلاص", "Al-Ikhlas", "مكية", 4, 30, isLocked = false, isMastered = false, score = 0, "القلقلة الكبرى والصغرى", emptyList()),
            QuranSurahItem(113, "الفلق", "Al-Falaq", "مكية", 5, 30, isLocked = false, isMastered = false, score = 0, "أحكام النون والميم المشددتين والقلقلة", emptyList()),
            QuranSurahItem(114, "الناس", "An-Nas", "مكية", 6, 30, isLocked = false, isMastered = false, score = 0, "الغنة في النون المشددة والهمس", emptyList())
        )
    }

    /**
     * استرجاع الآيات الأصيلة مع التفسير وأحكام التجويد للسور.
     * تحول الآيات المحملة من ملف الـ JSON إلى QuranVerseDetail مرتبة حسب رقم الآية.
     * إذا لم تكن السورة محملة ترجع قائمة فارغة للتعامل معها برمجياً في الواجهة.
     */
    fun getVersesForSurah(surahId: Int, surahName: String): List<QuranVerseDetail> {
        val loaded = versesMap?.get(surahId) ?: return emptyList()
        return loaded.sortedBy { it.ayah }.map { ayah ->
            QuranVerseDetail(
                surahNumber = surahId,
                surahName = surahName,
                verseNumber = ayah.ayah,
                verseText = ayah.text,
                tafseer = tafsirMap?.get("$surahId:${ayah.ayah}") ?: "",
                tajweedNotes = ""
            )
        }
    }

    private fun getSurahName(surahId: Int): String =
        surahs.firstOrNull { it.id == surahId }?.name ?: "سورة $surahId"

    /**
     * تطبيع النص العربي للبحث: حذف التشكيل والتطويل وألف الخنجرية،
     * ثم توحيد الهمزات والألف المقصورة والتاء المربوطة لتزداد دقة المطابقة.
     */
    private fun normalizeArabicText(text: String): String =
        text.replace(Regex("[\\u064B-\\u0652\\u0640\\u0670]"), "")
            .replace("أ", "ا").replace("إ", "ا").replace("آ", "ا").replace("ٱ", "ا")
            .replace("ى", "ي").replace("ة", "ه")

    /**
     * بحث نصي حقيقي في الآيات الـ 6236 المحملة من النص العثماني.
     * يشترط 3 أحرف على الأقل، ويمرر مطابقة ثانية بفكّ «ال» التعريف
     * (مثل «الكرسي» → «كرسيه» في آية الكرسي 2:255). تُرتب النتائج
     * حسب السورة ثم رقم الآية. تحميل النسخة العثمانية شرط مسبق
     * (loadFromAssets) وإلا تُرجع قائمة فارغة — لا بحث على لا شيء.
     */
    fun searchVerses(query: String, limit: Int = 50): List<QuranVerseDetail> {
        val trimmed = query.trim()
        if (trimmed.length < 3) return emptyList()
        val map = versesMap ?: return emptyList()
        val needle = normalizeArabicText(trimmed)
        val strippedNeedle =
            if (needle.length > 3 && needle.startsWith("ال")) needle.substring(2) else null
        val results = ArrayList<QuranVerseDetail>()
        for ((surahId, ayahs) in map) {
            val surahName = getSurahName(surahId)
            for (ayah in ayahs.sortedBy { it.ayah }) {
                val hay = normalizeArabicText(ayah.text)
                if (hay.contains(needle) || (strippedNeedle != null && hay.contains(strippedNeedle))) {
                    results.add(
                        QuranVerseDetail(
                            surahNumber = surahId,
                            surahName = surahName,
                            verseNumber = ayah.ayah,
                            verseText = ayah.text,
                            tafseer = tafsirMap?.get("$surahId:${ayah.ayah}") ?: "",
                            tajweedNotes = ""
                        )
                    )
                    if (results.size >= limit) break
                }
            }
            if (results.size >= limit) break
        }
        return results.sortedWith(compareBy({ it.surahNumber }, { it.verseNumber }))
    }

    /**
     * بحث أسماء السور بالعربية (خارطة المصحف — المرحلة 1): التطبيع نفسه
     * المستعمل في بحث الآيات (بلا تشكيل/تطويل، وتوحيد الهمزات)، فيجد
     * «البقره» و«البقرة» معاً. استعلام رقمي (أرقام عربية أو غربية) يطابق
     * رقم السورة مباشرة. استعلام فارغ يرجع السور الـ 114 بالترتيب.
     */
    fun searchSurahs(query: String): List<QuranSurahItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return surahs
        val westernDigits = trimmed
            .map { ch ->
                when (ch) {
                    in '٠'..'٩' -> ('0' + (ch - '٠'))
                    in '۰'..'۹' -> ('0' + (ch - '۰'))
                    else -> ch
                }
            }.joinToString("")
        westernDigits.toIntOrNull()?.let { number ->
            if (number in 1..114) {
                return surahs.filter { it.id == number }
            }
        }
        val needle = normalizeArabicText(trimmed)
        if (needle.isEmpty()) return emptyList()
        return surahs.filter { normalizeArabicText(it.name).contains(needle) }
    }

    // ========================================================================
    // دراسة الآية (خارطة المصحف — المرحلة 3): ترجمة السورة + كلمة بكلمة.
    //
    // مصدران حقيقيان فقط، كلاهما عام بلا مفاتيح، وكلاهما تحققنا منه
    // بطلبات حية قبل الكتابة:
    // - الترجمة: QuranEnc (العائلة نفسها المستعملة لتفسير الميسر أعلاه)،
    //   نقطة النهاية translation/sura/{translation}/{surah} — مكالمة
    //   واحدة للسورة كاملة؛ ثلاث ترجمات إنجليزية (Saheeh الافتراضية،
    //   وHilali-Khan، وRowwad — انظر MUSHAF_TRANSLATIONS) يختار
    //   المستخدم بينها، وتُحفظ الترجمة المختارة محلياً.
    //   أرقام الحواشي [2] [3] المضمّنة في النص تُحذف عند العرض فقط.
    // - الكلمات: واجهة Quran.com العامة api.quran.com (بلا توكن)، نقطة
    //   النهاية verses/by_key/{surah}:{ayah}?words=true — كل كلمة تحمل
    //   نصها العثماني ونقلها الصوتي (transliteration) وترجمتها الإنجليزية.
    //   وهي متميزة عن مسار apis.quran.foundation المقيّد بالتوكن المستعمل
    //   للتلاوة؛ لا توكن ولا مفاتيح هنا، فتعمل على المصحف كاملاً.
    // - الصرف: لا مصدر موثّق في التطبيق ولا في هذه الواجهات، فلم تُضف
    //   له لوحة إطلاقاً.
    // - الإعراب: مصدره الموثّق spa5k/tafsir_api (MIT) — كتابا الإعراب
    //   الميسر والدرويش (انظر fetchIrab)، ويُعرض في لوحة الدراسة.
    // - أسباب النزول: مصدرها الموثّق مستودع asbab-al-nuzul-dataset
    //   (MIT) — 50 سورة فقط (انظر fetchAsbabForSurah)، وتُعرض في
    //   لوحة الدراسة؛ والسور بلا ملف تعرض «لا يوجد سبب نزول مسجّل».
    // - الآيات المشابهة: مصدرها الموثّق Quran_Mutashabihat_Data (CC0)
    //   (انظر fetchMutashabihat)، وتُعرض في لوحة الدراسة — ليست شاملة
    //   ولا تغطي العالميات 6,201–6,236.
    // الفشل (لا اتصال، خطأ خادم، استجابة فارغة) يعيد null — لا نص ملفّق.
    // ========================================================================

    /** اسم الترجمة المعروضة تحت الآيات وفي لوحة الدراسة. */
    const val MUSHAF_TRANSLATION_LABEL = "Saheeh International"

    /** ترجمة إنجليزية تُعرض في القارئ ولوحة الدراسة ومكتبة الكتب. */
    data class MushafTranslation(
        val id: String,
        val arabicName: String,
        val label: String,
        val source: String
    )

    /**
     * الترجمات الإنجليزية الثلاث في QuranEnc (فُحصت حياً 2026-11-22:
     * Saheeh وHilali-Khan بعينة سورة كاملة؛ Rowwad موجود في قائمة
     * الترجمات الـ 76). كلها شبكية — لا ترجمة دون اتصال في التطبيق.
     */
    val MUSHAF_TRANSLATIONS: List<MushafTranslation> = listOf(
        MushafTranslation(
            id = "english_saheeh",
            arabicName = "سهيه إنترناشيونال",
            label = MUSHAF_TRANSLATION_LABEL,
            source = "QuranEnc"
        ),
        MushafTranslation(
            id = "english_hilali_khan",
            arabicName = "هلالي وخان",
            label = "Hilali & Khan",
            source = "QuranEnc"
        ),
        MushafTranslation(
            id = "english_rwwad",
            arabicName = "الروّاد",
            label = "Rowwad Translation Center",
            source = "QuranEnc"
        )
    )

    /** كلمة قرآنية مع نقلها الصوتي وترجمتها الإنجليزية (من QF العام). */
    data class QuranWordStudy(
        val position: Int,
        val textUthmani: String,
        val transliteration: String?,
        val translation: String?
    )

    private val surahTranslationCache = java.util.concurrent.ConcurrentHashMap<String, Map<Int, String>>()
    private val verseWordsCache = java.util.concurrent.ConcurrentHashMap<String, List<QuranWordStudy>>()

    /** خريطة ترجمة السورة المخزنة في الذاكرة (ترجمة:سورة ← آية ← ترجمة)، أو null قبل الجلب. */
    fun getCachedSurahTranslationFor(translationId: String, surahId: Int): Map<Int, String>? =
        surahTranslationCache["$translationId:$surahId"]

    /**
     * ترجمة سورة كاملة من QuranEnc بالمعرّف المطلوب (إنجليزية).
     * تُخزَّن في الذاكرة بعد أول جلب ناجح بمفتاح "ترجمة:سورة".
     * null عند أي فشل أو معرّف غير معروف.
     */
    suspend fun fetchSurahTranslationFor(translationId: String, surahId: Int): Map<Int, String>? {
        if (translationId !in setOf("english_saheeh", "english_hilali_khan", "english_rwwad")) return null
        if (surahId !in 1..114) return null
        val key = "$translationId:$surahId"
        surahTranslationCache[key]?.let { return it }
        return try {
            val url = "https://quranenc.com/api/v1/translation/sura/$translationId/$surahId"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(18, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val body = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext ""
                    response.body?.string().orEmpty()
                }
            }
            if (body.isBlank()) return null
            val array = try {
                JSONObject(body).optJSONArray("result")
            } catch (_: Exception) {
                null
            } ?: return null
            val result = LinkedHashMap<Int, String>()
            for (index in 0 until array.length()) {
                val row = array.optJSONObject(index) ?: continue
                val aya = row.optInt("aya", 0)
                // أرقام الحواشي [n] تبقى علامات ظاهرة في الترجمة — حذفها
                // صامتاً كان يخفي وجود حاشية؛ ونص الحاشية نفسه (حقل
                // footnotes في الرد نفسه، من المصدر حرفياً) يُلحق
                // كملاحظة بعد الترجمة ليربط الرقم بشرحه. تحقق حياً
                // 2026-11-23: Saheeh 1:1 تحمل [2][3] مع نصهما، وHilali-Khan
                // 1:2 تحمل [1][2] و1:7 تحمل [4]–[7] مع نصوصها.
                val translation = row.optString("translation", "")
                    .replace(Regex("[ \\t]+"), " ")
                    .trim()
                val footnotes = row.optString("footnotes", "")
                    .replace(Regex("[ \\t]+"), " ")
                    .replace(Regex("\n{3,}"), "\n\n")
                    .trim()
                val text = if (footnotes.isNotBlank()) {
                    "$translation\n\n— حواشي الترجمة:\n$footnotes"
                } else {
                    translation
                }
                if (aya > 0 && text.isNotBlank()) result[aya] = text
            }
            if (result.isEmpty()) null else {
                surahTranslationCache[key] = result
                result
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w(TAG, "Unable to load surah translation $translationId for $surahId", e)
            null
        }
    }

    // ── نص التجويد والرسم البديل (خارطة المصحف — المرحلة 5) ──
    // مصدر موثّق: واجهة Quran.com العامة api.quran.com (بلا توكن) —
    // verses/uthmani_tajweed (وسوم <tajweed class=...>) وverses/uthmani_simple
    // وverses/imlaei. كلها نص حفص نفسه برسم مختلف؛ لا رواية أخرى متاحة
    // في التطبيق أو في الواجهات التي فُحصت (alquran.cloud بلا ورش، وملف
    // المصحف المحلي مصحف حفص وحده) — فلا خيار رواية في القارئ.

    private val surahTajweedCache = java.util.concurrent.ConcurrentHashMap<Int, Map<Int, String>>()
    private val surahScriptCache = java.util.concurrent.ConcurrentHashMap<String, Map<Int, String>>()

    /** خريطة نص التجويد المخزنة (آية ← نص بوسوم tajweed)، أو null قبل الجلب. */
    fun getCachedSurahTajweed(surahId: Int): Map<Int, String>? =
        surahTajweedCache[surahId]

    /**
     * نص التجويد لسورة كاملة من QF العام (verses/uthmani_tajweed).
     * تُخزَّن في الذاكرة بعد أول جلب ناجح. null عند أي فشل.
     */
    suspend fun fetchSurahTajweed(surahId: Int): Map<Int, String>? {
        if (surahId !in 1..114) return null
        surahTajweedCache[surahId]?.let { return it }
        return try {
            val url = "https://api.quran.com/api/v4/quran/verses/uthmani_tajweed?chapter_number=$surahId&per_page=300"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(18, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val body = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext ""
                    response.body?.string().orEmpty()
                }
            }
            if (body.isBlank()) return null
            val array = try {
                JSONObject(body).optJSONArray("verses")
            } catch (_: Exception) {
                null
            } ?: return null
            val result = LinkedHashMap<Int, String>()
            for (index in 0 until array.length()) {
                val row = array.optJSONObject(index) ?: continue
                val key = row.optString("verse_key", "")
                val ayah = key.substringAfter(":", "").toIntOrNull() ?: 0
                val text = row.optString("text_uthmani_tajweed", "").trim()
                if (ayah > 0 && text.isNotBlank()) result[ayah] = text
            }
            if (result.isEmpty()) null else {
                surahTajweedCache[surahId] = result
                result
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w(TAG, "Unable to load surah tajweed for $surahId", e)
            null
        }
    }

    /** خريطة رسم بديل مخزنة (رسم:سورة ← آية ← نص)، أو null قبل الجلب. */
    fun getCachedSurahScript(script: String, surahId: Int): Map<Int, String>? =
        surahScriptCache["$script:$surahId"]

    /**
     * نص سورة كاملة برسم بديل ("uthmani_simple" أو "imlaei") من QF العام.
     * تُخزَّن في الذاكرة بعد أول جلب ناجح. null عند أي فشل.
     */
    suspend fun fetchSurahScript(script: String, surahId: Int): Map<Int, String>? {
        val field = when (script) {
            "imlaei" -> "text_imlaei"
            "uthmani_simple" -> "text_uthmani_simple"
            else -> return null
        }
        if (surahId !in 1..114) return null
        val key = "$script:$surahId"
        surahScriptCache[key]?.let { return it }
        return try {
            val url = "https://api.quran.com/api/v4/quran/verses/$script?chapter_number=$surahId&per_page=300"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(18, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val body = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext ""
                    response.body?.string().orEmpty()
                }
            }
            if (body.isBlank()) return null
            val array = try {
                JSONObject(body).optJSONArray("verses")
            } catch (_: Exception) {
                null
            } ?: return null
            val result = LinkedHashMap<Int, String>()
            for (index in 0 until array.length()) {
                val row = array.optJSONObject(index) ?: continue
                val verseKey = row.optString("verse_key", "")
                val ayah = verseKey.substringAfter(":", "").toIntOrNull() ?: 0
                val text = row.optString(field, "").trim()
                if (ayah > 0 && text.isNotBlank()) result[ayah] = text
            }
            if (result.isEmpty()) null else {
                surahScriptCache[key] = result
                result
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w(TAG, "Unable to load surah script $script for $surahId", e)
            null
        }
    }

    /** مقطع من نص التجويد: نصه وفئة الحكم (null للنص العادي). */
    data class TajweedSegment(val text: String, val ruleClass: String?)

    /**
     * تحليل وسوم <tajweed class=...> و<span class=end> من نص QF.
     * الوسوم متداخلة أحياناً (قاعدة داخل قاعدة) فيُحلَّل بمكدّس، والأعمق
     * لونه هو الساري على نصه. الوسم المجهول يُحذف ويبقى نصه كنص عادي؛
     * وعلامة نهاية الآية تُحذف بمحتواها (رقم الآية في شارة البطاقة أصلاً).
     * المقاطع المتجاورة من الفئة نفسها تُدمج لتقليل عدد الـ spans.
     */
    fun parseTajweedMarkup(rawMarkup: String): List<TajweedSegment> {
        val segments = ArrayList<TajweedSegment>()
        val plain = StringBuilder()
        val ruleStack = ArrayDeque<String?>()
        var currentRule: String? = null
        var skipSpanDepth = 0
        fun flushPlain() {
            if (plain.isNotEmpty()) {
                segments += TajweedSegment(plain.toString(), currentRule)
                plain.clear()
            }
        }
        var index = 0
        while (index < rawMarkup.length) {
            val open = rawMarkup.indexOf('<', index)
            if (open < 0) {
                if (skipSpanDepth == 0) plain.append(rawMarkup, index, rawMarkup.length)
                break
            }
            if (skipSpanDepth == 0) plain.append(rawMarkup, index, open)
            val close = rawMarkup.indexOf('>', open)
            if (close < 0) {
                if (skipSpanDepth == 0) plain.append(rawMarkup, open, rawMarkup.length)
                break
            }
            val tag = rawMarkup.substring(open + 1, close).trim()
            when {
                tag.startsWith("tajweed class=") -> {
                    val rule = tag.removePrefix("tajweed class=").trim()
                    flushPlain()
                    ruleStack.addLast(currentRule)
                    currentRule = rule
                    index = close + 1
                }
                tag == "/tajweed" -> {
                    flushPlain()
                    currentRule = if (ruleStack.isEmpty()) null else ruleStack.removeLast()
                    index = close + 1
                }
                tag.startsWith("span") -> {
                    skipSpanDepth++
                    index = close + 1
                }
                tag == "/span" -> {
                    if (skipSpanDepth > 0) skipSpanDepth--
                    index = close + 1
                }
                else -> index = close + 1
            }
        }
        flushPlain()
        val merged = ArrayList<TajweedSegment>()
        for (segment in segments) {
            val last = merged.lastOrNull()
            if (last != null && last.ruleClass == segment.ruleClass) {
                merged[merged.lastIndex] = TajweedSegment(last.text + segment.text, last.ruleClass)
            } else {
                merged += segment
            }
        }
        return merged
    }

    /**
     * ألوان أحكام التجويد في القارئ (لوحة التطبيق الخاصة؛ الفئات من QF).
     * كل فئة لون مميز يقرأ على الخلفيات الداكنة والفاتحة معاً.
     */
    val TAJWEED_RULE_COLORS: Map<String, Color> = mapOf(
        "ham_wasl" to Color(0xFF9AA0A6),
        "slnt" to Color(0xFF78909C),
        "laam_shamsiyah" to Color(0xFF8D6E63),
        "madda_normal" to Color(0xFF1E88E5),
        "madda_permissible" to Color(0xFF3949AB),
        "madda_obligatory" to Color(0xFF5E35B1),
        "madda_necessary" to Color(0xFF00897B),
        "qalaqah" to Color(0xFFD32F2F),
        "ikhafa" to Color(0xFF8E24AA),
        "ikhafa_shafawi" to Color(0xFFD81B60),
        "idgham_shafawi" to Color(0xFF388E3C),
        "idgham_ghunnah" to Color(0xFF43A047),
        "idgham_wo_ghunnah" to Color(0xFF7CB342),
        "idgham_mutajanisayn" to Color(0xFF6D4C41),
        "iqlab" to Color(0xFF00ACC1),
        "ghunnah" to Color(0xFFFB8C00)
    )

    /** أسماء أحكام التجويد العربية لدليل الألوان في القارئ. */
    val TAJWEED_RULE_NAMES_AR: Map<String, String> = mapOf(
        "ham_wasl" to "همزة الوصل",
        "slnt" to "حرف صامت",
        "laam_shamsiyah" to "اللام الشمسية",
        "madda_normal" to "مد طبيعي",
        "madda_permissible" to "مد جائز",
        "madda_obligatory" to "مد واجب متصل",
        "madda_necessary" to "مد لازم",
        "qalaqah" to "قلقلة",
        "ikhafa" to "إخفاء",
        "ikhafa_shafawi" to "إخفاء شفوي",
        "idgham_shafawi" to "إدغام شفوي",
        "idgham_ghunnah" to "إدغام بغنة",
        "idgham_wo_ghunnah" to "إدغام بلا غنة",
        "idgham_mutajanisayn" to "إدغام متجانسين",
        "iqlab" to "إقلاب",
        "ghunnah" to "غنة"
    )

    /**
     * كلمات آية واحدة مع النقل الصوتي والترجمة الإنجليزية (QF العام).
     * علامات نهاية الآية (char_type_name = "end") تُستبعد — ليست كلمات.
     * null عند أي فشل أو غياب كلمات حقيقية.
     */
    suspend fun fetchVerseWords(surahId: Int, ayah: Int): List<QuranWordStudy>? {
        if (surahId !in 1..114 || ayah < 1) return null
        val key = "$surahId:$ayah"
        verseWordsCache[key]?.let { return it }
        return try {
            val url = "https://api.quran.com/api/v4/verses/by_key/$surahId:$ayah?words=true&word_fields=text_uthmani"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(18, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val body = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext ""
                    response.body?.string().orEmpty()
                }
            }
            if (body.isBlank()) return null
            val words = try {
                JSONObject(body).optJSONObject("verse")?.optJSONArray("words")
            } catch (_: Exception) {
                null
            } ?: return null
            val result = ArrayList<QuranWordStudy>()
            for (index in 0 until words.length()) {
                val word = words.optJSONObject(index) ?: continue
                if (word.optString("char_type_name") != "word") continue
                val position = word.optInt("position", 0)
                val uthmani = word.optString("text_uthmani").trim()
                if (position < 1 || uthmani.isBlank()) continue
                val transliteration = word.optJSONObject("transliteration")
                    ?.optString("text")?.trim()?.takeIf { it.isNotBlank() }
                val translation = word.optJSONObject("translation")
                    ?.optString("text")?.trim()?.takeIf { it.isNotBlank() }
                result += QuranWordStudy(position, uthmani, transliteration, translation)
            }
            val sorted = result.sortedBy { it.position }
            if (sorted.isEmpty()) null else {
                verseWordsCache[key] = sorted
                sorted
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w(TAG, "Unable to load verse words for $key", e)
            null
        }
    }
}
