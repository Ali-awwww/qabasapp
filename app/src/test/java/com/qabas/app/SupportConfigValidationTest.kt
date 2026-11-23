package com.qabas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ق21: حارسا قيم التبرع — الآيبان المعروض سابقاً فشل في فحص mod-97
 * وعنوان USDT كان نائباً (43 حرفاً فيه حروف خارج base58).
 * الفحص شكلي فقط: لا يثبت ملكية الحساب ولا وجوده.
 */
class SupportConfigValidationTest {

    @Test
    fun `textbook GB IBAN passes`() {
        // مثال ISO 13616 القياسي (بفراغات وأحرف صغيرة — يُطبَّع قبل الفحص).
        assertTrue(SupportConfigManager.isValidIban("gb29 nwbk 6016 1331 9268 19"))
    }

    @Test
    fun `previously displayed SA IBAN fails mod-97`() {
        // الآيبان الذي كان معروضاً كحساب التبرعات الرسمي: mod-97 = 28 لا 1.
        assertFalse(SupportConfigManager.isValidIban("SA0380000000608010167520"))
        assertFalse(SupportConfigManager.isValidIban("SA0380000000608010167520 "))
    }

    @Test
    fun `short or malformed IBAN fails`() {
        assertFalse(SupportConfigManager.isValidIban("SA03"))
        assertFalse(SupportConfigManager.isValidIban("SA038000000060801016752!"))
        assertFalse(SupportConfigManager.isValidIban(""))
    }

    @Test
    fun `well-formed TRON address shape passes`() {
        assertTrue(SupportConfigManager.isValidTronAddress("T" + "A".repeat(33)))
    }

    @Test
    fun `placeholder USDT address fails`() {
        // العنوان النائب السابق: 43 حرفاً وفيه 'l' و'0' خارج base58.
        assertFalse(SupportConfigManager.isValidTronAddress("TYQabasStudioSupportOfficialTRC20Address99X"))
    }

    @Test
    fun `malformed TRON addresses fail`() {
        assertFalse(SupportConfigManager.isValidTronAddress("T" + "0".repeat(33)))
        assertFalse(SupportConfigManager.isValidTronAddress("T" + "l".repeat(33)))
        assertFalse(SupportConfigManager.isValidTronAddress("T" + "A".repeat(32)))
        assertFalse(SupportConfigManager.isValidTronAddress("X" + "A".repeat(33)))
        assertFalse(SupportConfigManager.isValidTronAddress(""))
    }
}
