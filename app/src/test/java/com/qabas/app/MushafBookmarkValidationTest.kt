package com.qabas.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ق87: علامات المصحف — تأكيد الحذف + قيد علوي لرقم الآية.
 * الحذف كان فورياً بلا تأكيد من القائمة والمحرر، وعلامة تالفة
 * (آية فوق عدد آيات سورتها) كانت تُعرض برقم كاذب ثم يقفز jumpTo
 * مقيّداً لآية أخرى بصمت. هذه الاختبارات تربط القراءة والحفظ
 * بعدد آيات كل سورة الحقيقي (versesCount).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MushafBookmarkValidationTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun bookmark(surah: Int, ayah: Int) =
        UserPreferencesManager.MushafBookmark(
            surah = surah,
            ayah = ayah,
            colorArgb = UserPreferencesManager.MUSHAF_BOOKMARK_DEFAULT_COLOR,
            note = "",
            createdAtMillis = 1L
        )

    @Test
    fun `valid bookmark at surah boundary round-trips`() {
        // الفاتحة 7 آيات: الآية 7 صالحة وتُحفظ وتُقرأ.
        UserPreferencesManager.saveMushafBookmark(context, bookmark(1, 7))
        val kept = UserPreferencesManager.getMushafBookmarks(context)
        assertTrue(kept.any { it.surah == 1 && it.ayah == 7 })
        UserPreferencesManager.removeMushafBookmark(context, 1, 7)
    }

    @Test
    fun `ayah beyond surah count is refused on save`() {
        // الفاتحة 7 آيات: الآية 8 تُرفض عند الحفظ فلا تُخزَّن أبداً.
        UserPreferencesManager.saveMushafBookmark(context, bookmark(1, 8))
        val kept = UserPreferencesManager.getMushafBookmarks(context)
        assertTrue(kept.none { it.surah == 1 && it.ayah == 8 })
    }

    @Test
    fun `corrupt stored bookmark beyond surah count is dropped on read`() {
        // بذرة تخزين تالف مباشرة (المفتاح "qabas_prefs" هو نفسه مفتاح
        // UserPreferencesManager الخاص): آية 300 في الفاتحة + آية
        // صالحة في البقرة — تُسقط التالفة وتُقرأ الصالحة.
        val array = JSONArray()
        array.put(JSONObject().put("s", 1).put("a", 300).put("c", -1).put("n", "").put("t", 1L))
        array.put(JSONObject().put("s", 2).put("a", 255).put("c", -1).put("n", "").put("t", 1L))
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .edit().putString("mushaf_bookmarks", array.toString()).apply()
        val kept = UserPreferencesManager.getMushafBookmarks(context)
        assertTrue(kept.none { it.surah == 1 && it.ayah == 300 })
        assertTrue(kept.any { it.surah == 2 && it.ayah == 255 })
        // من البذرتين معاً لا يبقى إلا الصالحة.
        assertEquals(1, kept.count { it.surah == 1 || it.surah == 2 })
        UserPreferencesManager.removeMushafBookmark(context, 2, 255)
    }

    @Test
    fun `longest surah boundary is enforced`() {
        // البقرة 286 آية: 286 تُقبل و287 تُرفض.
        UserPreferencesManager.saveMushafBookmark(context, bookmark(2, 286))
        assertTrue(
            UserPreferencesManager.getMushafBookmarks(context)
                .any { it.surah == 2 && it.ayah == 286 }
        )
        UserPreferencesManager.saveMushafBookmark(context, bookmark(2, 287))
        assertTrue(
            UserPreferencesManager.getMushafBookmarks(context)
                .none { it.surah == 2 && it.ayah == 287 }
        )
        UserPreferencesManager.removeMushafBookmark(context, 2, 286)
    }
}
