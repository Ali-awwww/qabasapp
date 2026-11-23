package com.qabas.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ق84: منتقي ترجمات المكتبة — مصدر واحد للحقيقة. القائمة كانت
 * منسوخة يدوياً في 3 مواضع (تفضيلات: قراءة وحفظ، وجلب) فأي
 * ترجمة رابعة تُضاف للمنتقي كانت ستُرفض بصمت. هذه الاختبارات
 * تربط التفضيلات والجلب بقائمة المنتقي نفسها.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MushafTranslationPickerTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `picker list and known-ids set agree`() {
        assertEquals(
            QuranDataProvider.MUSHAF_TRANSLATIONS.map { it.id }.toSet(),
            QuranDataProvider.MUSHAF_TRANSLATION_IDS
        )
        assertTrue(QuranDataProvider.MUSHAF_TRANSLATION_IDS.isNotEmpty())
        assertEquals(
            QuranDataProvider.MUSHAF_TRANSLATIONS.size,
            QuranDataProvider.MUSHAF_TRANSLATION_IDS.size
        )
    }

    @Test
    fun `every picker translation round-trips through preferences`() {
        for (translation in QuranDataProvider.MUSHAF_TRANSLATIONS) {
            UserPreferencesManager.saveMushafTranslation(context, translation.id)
            assertEquals(
                "الترجمة ${translation.id} يجب أن تحفظ وتُقرأ كما هي",
                translation.id,
                UserPreferencesManager.getMushafTranslation(context)
            )
        }
    }

    @Test
    fun `unknown translation id falls back to saheeh on save`() {
        UserPreferencesManager.saveMushafTranslation(context, "english_unknown_xyz")
        assertEquals(
            "english_saheeh",
            UserPreferencesManager.getMushafTranslation(context)
        )
    }

    @Test
    fun `unknown stored value falls back to saheeh on read`() {
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .edit().putString("mushaf_translation", "english_unknown_xyz").commit()
        assertEquals(
            "english_saheeh",
            UserPreferencesManager.getMushafTranslation(context)
        )
    }

    @Test
    fun `default translation is saheeh when nothing stored`() {
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .edit().remove("mushaf_translation").commit()
        assertEquals(
            "english_saheeh",
            UserPreferencesManager.getMushafTranslation(context)
        )
    }
}
