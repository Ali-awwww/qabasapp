package com.qabas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختيار الدلتا «حسب الحال»: تُستخدم فقط عند وجودها وكانت أصغر من الحزمة الكاملة.
 */
class UpdateInfoDeltaTest {

    private fun info(
        deltaUrl: String? = "https://example.com/qabas.delta",
        deltaSize: Long = 4_000_000L,
        apkSize: Long = 140_000_000L
    ) = UpdateManager.UpdateInfo(
        versionName = "1.4.0",
        versionCode = 103,
        deltaUrl = deltaUrl,
        apkUrl = "https://example.com/qabas.apk",
        releaseNotes = "",
        deltaSize = deltaSize,
        apkSize = apkSize
    )

    @Test
    fun `smaller delta is used`() {
        assertTrue(info().deltaUsable)
    }

    @Test
    fun `delta larger or equal to the full apk is skipped`() {
        assertFalse(info(deltaSize = 150_000_000L).deltaUsable)
        assertFalse(info(deltaSize = 140_000_000L).deltaUsable)
    }

    @Test
    fun `missing or empty delta is skipped`() {
        assertFalse(info(deltaUrl = null).deltaUsable)
        assertFalse(info(deltaSize = 0L).deltaUsable)
    }
}
