package com.qabas.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * حالة قراءة كتاب في المكتبة الإسلامية — على نمط Kindle:
 * آخر موضع (فصل + مقطع)، نسبة التقدم، وإشارة مرجعية على مستوى الكتاب.
 *
 * التخزين المحلي أولاً في SharedPreferences على نمط إشارات الأحاديث
 * في `SahihHadithData` (ملف `qabas_sahih_bookmarks`)، ثم مزامنة صامتة
 * إلى جدول `library_reading_state` في Supabase عند تسجيل الدخول.
 * غياب الجدول أو انقطاع الشبكة لا يكسر القراءة: المزامنة تفشل بصمت
 * وتبقى الحالة المحلية هي مصدر الحقيقة (نمط `getMishkatReminderContent`
 * في `SupabaseServices.Database`: فشل آمن يعيد فراغاً).
 */
data class LibraryReadingState(
    val bookId: String,
    val lastChapterIndex: Int,
    val lastSectionIndex: Int,
    val progressPercent: Int,
    val bookmarked: Boolean,
    val updatedAtMillis: Long
)

object LibraryReadingStateStore {

    private const val TAG = "LibraryReadingState"
    private const val PREFS_NAME = "qabas_library_reading"
    private const val KEY_BOOKMARKS = "bookmarked_book_ids"
    private const val KEY_READER_FONT_SIZE_SP = "reader_font_size_sp"
    private const val DEFAULT_READER_FONT_SIZE_SP = 15.5f

    /** حجم خط القارئ المحفوظ (ق48): كان يُصفَّر إلى 15.5 مع كل فتح كتاب. */
    fun getReaderFontSizeSp(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_READER_FONT_SIZE_SP, DEFAULT_READER_FONT_SIZE_SP)
            .coerceIn(12f, 23f)
    }

    fun saveReaderFontSizeSp(context: Context, sizeSp: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_READER_FONT_SIZE_SP, sizeSp.coerceIn(12f, 23f)).apply()
    }

    private fun positionKey(bookId: String) = "pos_$bookId"

    /** الموضع المحفوظ كسلسلة "chapter:section:progress:updatedAtMillis". */
    private fun encode(state: LibraryReadingState): String =
        "${state.lastChapterIndex}:${state.lastSectionIndex}:${state.progressPercent}:${state.updatedAtMillis}"

    private fun decode(bookId: String, raw: String?, bookmarked: Boolean): LibraryReadingState? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(":")
        if (parts.size < 4) return null
        val chapter = parts[0].toIntOrNull() ?: return null
        val section = parts[1].toIntOrNull() ?: return null
        val progress = parts[2].toIntOrNull() ?: return null
        val updatedAt = parts[3].toLongOrNull() ?: return null
        return LibraryReadingState(
            bookId = bookId,
            lastChapterIndex = chapter,
            lastSectionIndex = section,
            progressPercent = progress.coerceIn(0, 100),
            bookmarked = bookmarked,
            updatedAtMillis = updatedAt
        )
    }

    fun isBookmarked(context: Context, bookId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_BOOKMARKS, emptySet()) ?: emptySet()
        return set.contains(bookId)
    }

    fun getBookmarkedBookIds(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_BOOKMARKS, emptySet())?.toSet() ?: emptySet()
    }

    fun getState(context: Context, bookId: String): LibraryReadingState? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val bookmarked = isBookmarked(context, bookId)
        val raw = prefs.getString(positionKey(bookId), null)
        if (raw == null && !bookmarked) return null
        return decode(bookId, raw, bookmarked)
            ?: LibraryReadingState(
                bookId = bookId,
                lastChapterIndex = 0,
                lastSectionIndex = 0,
                progressPercent = 0,
                bookmarked = bookmarked,
                updatedAtMillis = System.currentTimeMillis()
            )
    }

    /** آخر باب فُتح في قارئ الصحيح الكامل (بخاري/مسلم) — مفتاح مستقل عن
     * مواضع المقتطفات `pos_` فلا يظهر في «أكمل القراءة» ولا يلوثها. */
    private fun sahihChapterKey(bookId: String) = "sahih_chapter_$bookId"

    fun getSahihChapterId(context: Context, bookId: String): Int? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(sahihChapterKey(bookId), -1).takeIf { it >= 0 }
    }

    fun saveSahihChapterId(context: Context, bookId: String, chapterId: Int?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (chapterId == null) editor.remove(sahihChapterKey(bookId))
        else editor.putInt(sahihChapterKey(bookId), chapterId)
        editor.apply()
    }

    /** آخر باب فُتح في قارئ الصحيح المنسّق (بخاري/مسلم) — معرّفات الأبواب
     * هنا سلاسل نصية، بخلاف القارئ الكامل الذي يستخدم أرقاماً، لذا مفتاح
     * مستقل `sahih_formatted_chapter_` فلا يختلط به ولا يظهر في
     * «أكمل القراءة» ولا يلوثها. */
    private fun sahihFormattedChapterKey(bookId: String) = "sahih_formatted_chapter_$bookId"

    fun getSahihFormattedChapterId(context: Context, bookId: String): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(sahihFormattedChapterKey(bookId), null)
    }

    fun saveSahihFormattedChapterId(context: Context, bookId: String, chapterId: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (chapterId == null) editor.remove(sahihFormattedChapterKey(bookId))
        else editor.putString(sahihFormattedChapterKey(bookId), chapterId)
        editor.apply()
    }

    /** كل حالات القراءة المحفوظة، الأحدث أولاً (لصف «أكمل القراءة»). */
    fun getAllStates(context: Context): List<LibraryReadingState> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val bookmarkedIds = getBookmarkedBookIds(context)
        val states = ArrayList<LibraryReadingState>()
        for ((key, value) in prefs.all) {
            if (!key.startsWith("pos_")) continue
            val bookId = key.removePrefix("pos_")
            decode(bookId, value as? String, bookmarkedIds.contains(bookId))?.let { states.add(it) }
        }
        // كتب مُشارة فقط بلا موضع قراءة: تظهر في القائمة بنسبة 0٪.
        for (bookId in bookmarkedIds) {
            if (states.none { it.bookId == bookId }) {
                states.add(
                    LibraryReadingState(
                        bookId = bookId,
                        lastChapterIndex = 0,
                        lastSectionIndex = 0,
                        progressPercent = 0,
                        bookmarked = true,
                        updatedAtMillis = 0L
                    )
                )
            }
        }
        return states.sortedByDescending { it.updatedAtMillis }
    }

    /**
     * يحفظ آخر موضع قراءة. يحتفظ بأبعد موضع وصل إليه القارئ (لا يرجع
     * التقدم إلى الوراء إذا عاد القارئ لمقطع سابق)، ثم يزامن إلى السحابة.
     * ق26: كان التقدم وحده يُحفظ بالأبعد بينما الفصل/المقطع يُكتبان
     * بالقيم الجديدة دائماً — فعودة القارئ لفصل سابق كانت تُرجع
     * «أكمل القراءة» إلى ذلك الفصل مع نسبة تقدم أعلى منه، فالموضع
     * والنسبة متناقضان. الآن الفصل/المقطع يبقيان مع الأبعد أيضاً.
     * تُحسب `progressPercent` من المتصل عبر
     * `IslamicLibraryRepository.progressPercent` (موضع عام عبر كل الفصول).
     */
    fun savePosition(
        context: Context,
        bookId: String,
        chapterIndex: Int,
        sectionIndex: Int,
        progressPercent: Int
    ): LibraryReadingState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val previous = getState(context, bookId)
        val prevProgress = previous?.progressPercent ?: 0
        val newProgress = progressPercent.coerceIn(0, 100)
        val progress = maxOf(prevProgress, newProgress)
        val (chapter, section) = if (newProgress < prevProgress && previous != null) {
            previous.lastChapterIndex to previous.lastSectionIndex
        } else {
            chapterIndex to sectionIndex
        }
        val state = LibraryReadingState(
            bookId = bookId,
            lastChapterIndex = chapter,
            lastSectionIndex = section,
            progressPercent = progress,
            bookmarked = previous?.bookmarked ?: isBookmarked(context, bookId),
            updatedAtMillis = now
        )
        prefs.edit().putString(positionKey(bookId), encode(state)).apply()
        syncToCloud(state)
        return state
    }

    /**
     * يبدّل إشارة الكتاب المرجعية (على نمط `toggleBookmark` للأحاديث)،
     * ثم يزامن إلى السحابة. يعيد الحالة الجديدة للإشارة.
     */
    fun toggleBookmark(context: Context, bookId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentSet = prefs.getStringSet(KEY_BOOKMARKS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val willBookmark = !currentSet.contains(bookId)
        if (willBookmark) currentSet.add(bookId) else currentSet.remove(bookId)
        prefs.edit().putStringSet(KEY_BOOKMARKS, currentSet).apply()
        // مزامنة الحالة الكاملة (مع موضعها إن وجد) حتى لا يضيع الموضع.
        // ق26: بلا موضع محلي لا تُزامن إطلاقاً — الحالة المختلقة
        // (0/0/0 بطابع «الآن») كانت تُرفع فتمحو موضع السحابة الحقيقي
        // (من جهاز آخر) بصف 0٪؛ والإشارة نفسها تلحقها أول مزامنة
        // موضع لاحقة.
        val raw = prefs.getString(positionKey(bookId), null)
        raw?.let { decode(bookId, it, willBookmark) }?.let { syncToCloud(it) }
        return willBookmark
    }

    /**
     * يجلب حالات القراءة السحابية للمستخدم الحالي ويدمجها في المحلية
     * بمقارنة الطوابع الزمنية (ق26): كان الدمج يملأ الكتب الغائبة محلياً
     * فقط ويتجاهل صفاً سحابياً أحدث (قراءة أبعد على جهاز آخر) — فيضيع
     * تقدّم الجهاز الآخر بصمت؛ وكان «المحلي الأحدث» لا يُدفع للسحابة
     * أبداً فتبقى السحابة قديمة. الآن: سحابي أحدث يُعتمد، ومحلي أحدث
     * يُدفع، والإشارة اتحاد (لا تُلغى إشارة محلية بصف سحابي).
     * يعيد قائمة فارغة عند غياب الجدول أو الجلسة أو الشبكة.
     */
    suspend fun fetchAndMergeCloudStates(context: Context): List<LibraryReadingState> {
        val cloudRows = SupabaseServices.Database.getLibraryReadingState()
        if (cloudRows.isEmpty()) return getAllStates(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        var changed = false
        for (row in cloudRows) {
            // موضع محلي حقيقي فقط (سلسلة pos_ موجودة): حالة getState
            // المختلقة لكتاب مُشار بلا موضع لا تحجب دمج السحابة.
            val local = prefs.getString(positionKey(row.bookId), null)
                ?.let { decode(row.bookId, it, isBookmarked(context, row.bookId)) }
            val cloudTs = row.updatedAt
                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
            when {
                local == null -> {
                    val merged = LibraryReadingState(
                        bookId = row.bookId,
                        lastChapterIndex = row.lastChapter,
                        lastSectionIndex = row.lastSection,
                        progressPercent = row.progress.coerceIn(0, 100),
                        bookmarked = row.bookmarked || isBookmarked(context, row.bookId),
                        updatedAtMillis = System.currentTimeMillis()
                    )
                    editor.putString(positionKey(row.bookId), encode(merged))
                    changed = true
                    if (row.bookmarked) {
                        val set = prefs.getStringSet(KEY_BOOKMARKS, emptySet())?.toMutableSet() ?: mutableSetOf()
                        if (set.add(row.bookId)) editor.putStringSet(KEY_BOOKMARKS, set)
                    }
                }
                cloudTs > local.updatedAtMillis -> {
                    val merged = local.copy(
                        lastChapterIndex = row.lastChapter,
                        lastSectionIndex = row.lastSection,
                        progressPercent = row.progress.coerceIn(0, 100),
                        bookmarked = local.bookmarked || row.bookmarked,
                        updatedAtMillis = System.currentTimeMillis()
                    )
                    editor.putString(positionKey(row.bookId), encode(merged))
                    changed = true
                    if (row.bookmarked) {
                        val set = prefs.getStringSet(KEY_BOOKMARKS, emptySet())?.toMutableSet() ?: mutableSetOf()
                        if (set.add(row.bookId)) editor.putStringSet(KEY_BOOKMARKS, set)
                    }
                }
                local.updatedAtMillis > cloudTs -> {
                    // المحلي أحدث: ادفعه حتى لا تبقى السحابة قديمة.
                    syncToCloud(local)
                }
            }
        }
        if (changed) editor.apply()
        return getAllStates(context)
    }

    /** مزامنة صامتة (fire-and-forget): بلا جلسة دخول أو بلا جدول تفشل بصمت. */
    private fun syncToCloud(state: LibraryReadingState) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val userId = SupabaseServices.Auth.getCurrentUserId() ?: return@launch
                SupabaseServices.Database.upsertLibraryReadingState(
                    SupabaseServices.Database.LibraryReadingStateRow(
                        userId = userId,
                        bookId = state.bookId,
                        lastChapter = state.lastChapterIndex,
                        lastSection = state.lastSectionIndex,
                        progress = state.progressPercent,
                        bookmarked = state.bookmarked
                    )
                )
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.onFailure { Log.w(TAG, "library reading sync failed: ${it.message}") }
        }
    }
}
