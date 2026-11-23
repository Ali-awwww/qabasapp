package com.qabas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ق100: مطابقة B-Roll بحدود الكلمة — كانت contains() الخام تمنح
 * نقاطاً زائفة: «ماء» داخل «سماء» تجعل لقطة المطر تفوز بمشهد
 * سماء صافية، و«ليل» داخل «دليل»، و«خط» داخل «خطوة»؛ والعيب
 * نفسه كان يلوّث استعلام Pixabay في mapArabicToVisualKeywords.
 */
class BRollEngineTest {

    @Test
    fun `sky scene matches sky not rain`() {
        assertEquals("broll_4", BRollEngine.matchBRoll("سماء صافية").id)
    }

    @Test
    fun `guide word does not match night`() {
        // «دليل» تحوي «ليل» حرفياً لكنها ليست كلمة ليل.
        assertEquals("broll_1", BRollEngine.matchBRoll("دليل المؤمن").id)
    }

    @Test
    fun `step word does not match calligraphy`() {
        // «خطوة» تحوي «خط» حرفياً لكنها ليست كلمة خط.
        assertEquals("broll_5", BRollEngine.matchBRoll("خطوة نحو النور").id)
    }

    @Test
    fun `definite article and prefixes still match`() {
        assertEquals("broll_1", BRollEngine.matchBRoll("المسجد الحرام").id)
        assertEquals("broll_2", BRollEngine.matchBRoll("والمسجد النبوي").id)
        assertEquals("broll_7", BRollEngine.matchBRoll("بالدعاء والتضرع").id)
    }

    @Test
    fun `diacritics do not split words into false matches`() {
        // التشكيل على «السَّماء» كان يفتتها فيفوز المطر بـ «ماء».
        assertEquals("broll_4", BRollEngine.matchBRoll("السَّماءُ صافية").id)
    }

    @Test
    fun `rain scene still matches rain`() {
        assertEquals("broll_9", BRollEngine.matchBRoll("مطر وغيث").id)
    }

    @Test
    fun `sky keywords do not pollute pixabay query with rain`() {
        val keywords = BRollEngine.mapArabicToVisualKeywords("سماء صافية")
        assertTrue(keywords.contains("night sky"))
        assertFalse(keywords.contains("rain"))
    }

    @Test
    fun `rain keywords still map to rain`() {
        val keywords = BRollEngine.mapArabicToVisualKeywords("مطر وغيث")
        assertTrue(keywords.contains("rain"))
    }

    @Test
    fun `empty description falls back to first item`() {
        assertEquals("broll_1", BRollEngine.matchBRoll("").id)
    }
}
