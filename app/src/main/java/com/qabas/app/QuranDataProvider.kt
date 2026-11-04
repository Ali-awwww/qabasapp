package com.qabas.app

import android.content.Context
import android.util.Log
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

data class QuranChallenge(
    val id: String,
    val title: String,
    val goal: String,
    val progress: Float,
    val rewardHasanat: Int,
    val icon: String,
    val type: ChallengeType
)

enum class ChallengeType {
    READING, MEMORIZATION, TAJWEED
}

data class TajweedRuleItem(
    val title: String,
    val category: String,
    val description: String,
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
 * مزود بيانات القرآن الكريم وأكاديمية التجويد الشاملة في تطبيق قبس (Qabas).
 * يحتوي على القائمة الكاملة لكافة سور القرآن الكريم الـ 114 مع معلوماتها التوثيقية،
 * ونظام استرجاع الآيات الأصيلة والأحكام التجويدية.
 */

object QuranDataProvider {

    private val datasetLoadMutex = kotlinx.coroutines.sync.Mutex()

    /**
     * نقطة الدخول الموحدة لتحميل النص. تمنع قارئ السور وقارئ الصفحات من
     * بدء تنزيلين متزامنين للبيانات نفسها عند فتحهما بسرعة.
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

    /** ملاذ شبكي صادق: تفسير الميسر عربي من spa5k/tafsir_api عبر CDN (بلا مفتاح). null عند الفشل */
    suspend fun fetchRemoteTafsir(surahId: Int, ayah: Int): String? {
        if (surahId !in 1..114 || ayah !in 1..286) return null
        val key = "$surahId:$ayah"
        tafsirMap?.get(key)?.takeIf { it.isNotBlank() }?.let { return it }
        remoteTafsirCache[key]?.let { return it }

        return try {
            // المصدر الرسمي لواجهة التفسير الميسر: QuranEnc، مفتاح arabic_moyassar.
            val url = "https://quranenc.com/api/v1/tafsir/aya/arabic_moyassar/$surahId/$ayah"
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
                val mutable = (tafsirMap ?: emptyMap()).toMutableMap()
                mutable[key] = text
                tafsirMap = mutable
                text
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Unable to load Tafsir Muyassar for $key", error)
            null
        }
    }

    /** لا نعتمد نسخة نصية إلا إذا حملت إشعار Tanzil المطلوب كاملاً. */
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
     * تفحص المسارات المحتملة: "quran/uthmani.json" ثم "quran/quran.json"
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
                    if (!hasTanzilNotice(jsonString)) {
                        Log.w(TAG, "Skipping Quran asset without the required Tanzil notice: $path")
                        continue
                    }
                    val parsed = parseQuranJson(jsonString)
                    if (isCompleteQuran(parsed)) {
                        versesMap = parsed.groupBy { it.surah }
                        Log.i(TAG, "Successfully loaded ${parsed.size} ayahs from asset: $path")
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

    /** العدد الفعلي للآيات من ملف المصحف المحمّل، لا رقم ثابت في الواجهة. */
    fun getTotalAyahCount(): Int = versesMap?.values?.sumOf { it.size } ?: 0

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

    /** اسم السورة علناً (يُستخدم في قارئ الصفحات). */
    fun surahNameOf(surahId: Int): String = getSurahName(surahId)

    /**
     * تحديات القرآن اليومية (نظام ربيع القلوب)
     */
    val dailyChallenges = listOf(
        QuranChallenge(
            id = "daily_reading",
            title = "هدف قراءة مقترح 📖",
            goal = "قراءة صفحتين من سورة البقرة",
            progress = 0.0f,
            rewardHasanat = 500,
            icon = "MenuBook",
            type = ChallengeType.READING
        ),
        QuranChallenge(
            id = "memorization_intro",
            title = "هدف حفظ مقترح 🧠",
            goal = "حفظ أول 5 آيات من سورة الملك",
            progress = 0.0f,
            rewardHasanat = 1000,
            icon = "AutoAwesome",
            type = ChallengeType.MEMORIZATION
        ),
        QuranChallenge(
            id = "virtue_explorer",
            title = "هدف تدبّر مقترح ✨",
            goal = "قراءة فضل سورة الكهف والعمل به",
            progress = 0.0f,
            rewardHasanat = 300,
            icon = "Lightbulb",
            type = ChallengeType.READING
        )
    )

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
}
