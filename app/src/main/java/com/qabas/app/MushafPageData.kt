package com.qabas.app

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * MushafPageData.kt
 *
 * طبقة بيانات قارئ المصحف بالصفحات (تصميم أصلي لقبس — لا كود منقول).
 *
 * - حدود الصفحات الـ 604 (مصحف المدينة) تُقرأ من ملف مولّد محلياً:
 *   `assets/quran/madani_pages.json` بصيغة:
 *   {"pages":[[[سورة,آية],...],...], "juzFirstVerse":[[سورة,آية],...]}
 * - مصدر التوليد: fawazahmed0/quran-api (رخصة Unlicense — ملكية عامة،
 *   تسمح بالاستخدام التجاري) — نفس مصدر `uthmani.json` المستخدم أصلاً.
 * - نصوص الآيات نفسها من `QuranDataProvider` (ملف `uthmani.json` المحلي) —
 *   لا يُحمَّل أي نص من الشبكة.
 */
object MushafPageData {

    private const val TAG = "MushafPageData"

    const val PAGE_COUNT = 604
    const val JUZ_COUNT = 30

    @Volatile
    private var pageRefs: List<List<Pair<Int, Int>>>? = null

    @Volatile
    private var juzFirstRefs: List<Pair<Int, Int>>? = null

    @Volatile
    private var hizbFirstRefs: List<Pair<Int, Int>>? = null

    const val HIZB_COUNT = 60

    /**
     * تحميل خريطة الصفحات من assets (مرة واحدة). يُرجع true عند الجاهزية.
     */
    fun loadPageMap(context: Context): Boolean {
        if (pageRefs != null && pageRefs!!.size == PAGE_COUNT) return true
        val candidates = listOf("quran/madani_pages.json", "madani_pages.json")
        for (path in candidates) {
            try {
                val json = context.assets.open(path).use { it.bufferedReader(Charsets.UTF_8).use { r -> r.readText() } }
                if (json.isBlank()) continue
                val root = JSONObject(json)
                val pagesArray = root.optJSONArray("pages") ?: continue
                if (pagesArray.length() != PAGE_COUNT) {
                    Log.w(TAG, "Unexpected page count in $path: ${pagesArray.length()}")
                    continue
                }
                val pages = ArrayList<List<Pair<Int, Int>>>(PAGE_COUNT)
                for (p in 0 until pagesArray.length()) {
                    val arr = pagesArray.optJSONArray(p) ?: continue
                    val refs = ArrayList<Pair<Int, Int>>(arr.length())
                    for (i in 0 until arr.length()) {
                        val pair = arr.optJSONArray(i) ?: continue
                        val s = pair.optInt(0, 0)
                        val a = pair.optInt(1, 0)
                        if (s > 0 && a > 0) refs.add(s to a)
                    }
                    pages.add(refs)
                }
                val juzArray = root.optJSONArray("juzFirstVerse")
                val juzFirst = ArrayList<Pair<Int, Int>>(JUZ_COUNT)
                if (juzArray != null) {
                    for (j in 0 until juzArray.length()) {
                        val pair = juzArray.optJSONArray(j) ?: continue
                        juzFirst.add(pair.optInt(0, 1) to pair.optInt(1, 1))
                    }
                }
                if (pages.size == PAGE_COUNT) {
                    pageRefs = pages
                    juzFirstRefs = juzFirst
                    val hizbArray = root.optJSONArray("hizbFirstVerse")
                    if (hizbArray != null) {
                        val hizbFirst = ArrayList<Pair<Int, Int>>(HIZB_COUNT)
                        for (j in 0 until hizbArray.length()) {
                            val pair = hizbArray.optJSONArray(j) ?: continue
                            hizbFirst.add(pair.optInt(0, 1) to pair.optInt(1, 1))
                        }
                        hizbFirstRefs = hizbFirst
                    }
                    Log.i(TAG, "Loaded $PAGE_COUNT madani pages from $path")
                    return true
                }
            } catch (_: java.io.FileNotFoundException) {
                // جرّب المسار التالي
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading $path: ${e.message}")
            }
        }
        return false
    }

    fun isLoaded(): Boolean = pageRefs?.size == PAGE_COUNT

    /** مراجع (سورة، آية) لصفحة معينة (1-based). قائمة فارغة عند غياب التحميل. */
    fun getPageRefs(page: Int): List<Pair<Int, Int>> {
        val pages = pageRefs ?: return emptyList()
        if (page < 1 || page > PAGE_COUNT) return emptyList()
        return pages[page - 1]
    }

    /** أرقام السور المميزة الظاهرة في الصفحة (بالترتيب). */
    fun getSurahsOnPage(page: Int): List<Int> =
        getPageRefs(page).map { it.first }.distinct()

    private fun refOrder(ref: Pair<Int, Int>): Int = ref.first * 1000 + ref.second

    /** رقم الجزء (1-30) الذي تنتمي إليه الصفحة حسب أول آية فيها. */
    fun getJuzForPage(page: Int): Int {
        val juzFirst = juzFirstRefs ?: return -1
        if (juzFirst.isEmpty()) return -1
        val refs = getPageRefs(page)
        if (refs.isEmpty()) return -1
        val order = refOrder(refs.first())
        var juz = 1
        for (j in juzFirst.indices) {
            if (refOrder(juzFirst[j]) <= order) juz = j + 1 else break
        }
        return juz
    }

    /** رقم الحزب (1-60) الذي تنتمي إليه الصفحة حسب أول آية فيها. -1 عند غياب البيانات. */
    fun getHizbForPage(page: Int): Int {
        val hizbFirst = hizbFirstRefs ?: return -1
        if (hizbFirst.isEmpty()) return -1
        val refs = getPageRefs(page)
        if (refs.isEmpty()) return -1
        val order = refOrder(refs.first())
        var hizb = 1
        for (j in hizbFirst.indices) {
            if (refOrder(hizbFirst[j]) <= order) hizb = j + 1 else break
        }
        return hizb
    }

    /** أول صفحة تبدأ منها سورة معينة (أول ظهور لآيتها الأولى). -1 عند الغياب. */
    fun getPageForSurah(surahId: Int): Int {
        val pages = pageRefs ?: return -1
        for (p in pages.indices) {
            if (pages[p].any { it.first == surahId && it.second == 1 }) return p + 1
            // سور تُستكمل من صفحة سابقة: أول صفحة تذكر السورة إطلاقاً
            if (pages[p].any { it.first == surahId }) {
                // تحقق أنها البداية الفعلية: الصفحة السابقة لا تحويها
                val prevHas = p > 0 && pages[p - 1].any { it.first == surahId }
                if (!prevHas) return p + 1
            }
        }
        return -1
    }

    /** أول صفحة في جزء معين (1-30). -1 عند الغياب. */
    fun getPageForJuz(juz: Int): Int {
        val juzFirst = juzFirstRefs ?: return -1
        if (juz < 1 || juz > juzFirst.size) return -1
        val target = juzFirst[juz - 1]
        val pages = pageRefs ?: return -1
        for (p in pages.indices) {
            if (pages[p].any { it == target }) return p + 1
        }
        // احتياطي: أول صفحة يتجاوز ترتيبها ترتيب أول آية في الجزء
        val order = refOrder(target)
        for (p in pages.indices) {
            val refs = pages[p]
            if (refs.isNotEmpty() && refOrder(refs.first()) >= order) return p + 1
        }
        return -1
    }

    /** الصفحة التي تقع فيها آية (سورة، آية) محددة. -1 عند الغياب. */
    fun getPageForAyah(surahId: Int, ayah: Int): Int {
        val pages = pageRefs ?: return -1
        val target = surahId to ayah
        for (p in pages.indices) {
            if (pages[p].any { it == target }) return p + 1
        }
        return -1
    }
}
