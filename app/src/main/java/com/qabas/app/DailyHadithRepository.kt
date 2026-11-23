package com.qabas.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class DailyHadith(
    val dayId: Int, // 1-365
    val text: String,
    val narrator: String,
    val benefit: String,
    val book: String, // Sahih Bukhari or Sahih Muslim
    /** عنوان الحديث المضمّن (قد يغيب في ذاكرة قديمة — يُستبدل باسم الكتاب عند العرض). */
    val title: String = ""
)

/**
 * مستودع الحديث اليومي: يقرأ من التخزين المحلي المجمّع (أو المولّد)
 * مع الرجوع الحتمي إلى أحاديث الصحيحين المضمّنة في التطبيق بدون الاعتماد على Firestore.
 */
object DailyHadithRepository {
    private const val TAG = "DailyHadithRepo"
    // v2: ملف الجيل الأول كان يُملأ بنصوص مولَّدة بالذكاء الاصطناعي غير
    // موثقة؛ يُهجر ويُحذف (انظر readLocalCache)، فلا يُرسَل منه حديث
    // بعد اليوم. الملف الجديد لا يكتبه إلا نصوص موثقة (فوائد AI فقط).
    private const val CACHE_FILE = "daily_hadiths_cache_v2.json"
    private const val LEGACY_CACHE_FILE = "daily_hadiths_cache.json"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun readLocalCache(context: Context): List<DailyHadith> {
        return try {
            val file = File(context.filesDir, CACHE_FILE)
            if (!file.exists()) {
                // حجر صحي للذاكرة القديمة: أي إدخال فيها مصدره المولّد
                // وحده (uploadHadiths لا يستدعيها إلا زر التوليد)،
                // فتُحذف ويُرجع الاحتياطي المضمّن الموثّق بدلاً منها.
                File(context.filesDir, LEGACY_CACHE_FILE).takeIf { it.exists() }?.delete()
                return emptyList()
            }
            val raw = file.readText()
            if (raw.isBlank()) return emptyList()
            json.decodeFromString<List<DailyHadith>>(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading daily hadith cache: ${e.message}")
            emptyList()
        }
    }

    suspend fun getHadithForDay(context: Context, dayId: Int): DailyHadith? = withContext(Dispatchers.IO) {
        try {
            val normalizedDay = ((dayId - 1).coerceAtLeast(0) % 365) + 1
            val cached = readLocalCache(context).firstOrNull { it.dayId == normalizedDay }
            if (cached != null && cached.text.isNotBlank()) return@withContext cached

            val bundled = SahihHadithsRepository.getAllHadiths()
            if (bundled.isEmpty()) return@withContext null

            val item = bundled[(normalizedDay - 1) % bundled.size]
            DailyHadith(
                dayId = normalizedDay,
                text = item.matn,
                narrator = item.narrator,
                benefit = item.wisdomCapsule.ifBlank { item.explanation.take(160) },
                book = item.bookType.titleAr,
                title = item.title
            )
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve daily hadith for day $dayId", e)
            null
        }
    }

    /**
     * ق118: عنصر الصحيحين الكامل لحديث اليوم — من المصدر الموحد نفسه
     * الذي يقرأ منه إشعار اليوم وبطاقة الرئيسية (getHadithForDay)،
     * فيطابق إبراز القارئ حديث اليوم في كل مكان. المطابقة بنص المتن
     * أولاً (ذاكرة v2 مبنية من العنصر المضمّن نفسه)، والرجوع الحتمي
     * لمؤشر اليوم في القائمة المضمّنة إن لم يُطابق نص — فلا يعود
     * الإبراز لمحدد سنة×366.
     */
    suspend fun getDailyHadithItem(context: Context, dayId: Int): SahihHadithItem? = withContext(Dispatchers.IO) {
        try {
            val all = SahihHadithsRepository.getAllHadiths()
            if (all.isEmpty()) return@withContext null
            val daily = getHadithForDay(context, dayId) ?: return@withContext null
            all.firstOrNull { it.matn == daily.text }
                ?: all.getOrNull((daily.dayId - 1).coerceAtLeast(0) % all.size)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve daily hadith item for day $dayId", e)
            null
        }
    }

    suspend fun uploadHadiths(context: Context, hadiths: List<DailyHadith>): Boolean = withContext(Dispatchers.IO) {
        try {
            val existing = readLocalCache(context).associateBy { it.dayId }.toMutableMap()
            hadiths.forEach { existing[it.dayId] = it }
            val merged = existing.values.sortedBy { it.dayId }
            val file = File(context.filesDir, CACHE_FILE)
            file.writeText(json.encodeToString(merged))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save batch of daily hadiths locally", e)
            false
        }
    }
}
