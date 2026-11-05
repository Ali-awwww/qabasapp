package com.qabas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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
        apkSize = apkSize,
        checksum = "a".repeat(64),
        baseApkSha256 = "b".repeat(64)
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
    fun `delta without integrity metadata is skipped`() {
        assertFalse(info().copy(checksum = null).deltaUsable)
        assertFalse(info().copy(baseApkSha256 = null).deltaUsable)
        assertFalse(info().copy(checksum = "").deltaUsable)
    }

    @Test
    fun `missing or empty delta is skipped`() {
        assertFalse(info(deltaUrl = null).deltaUsable)
        assertFalse(info(deltaSize = 0L).deltaUsable)
    }
}


/** مقارنة إصدار التطبيق تشمل رقم البناء حتى عند ثبات versionName. */
class UpdateReleaseIdentityTest {

    @Test
    fun `new build with same version name is newer`() {
        assertTrue(UpdateManager.compareReleaseVersions("1.3.6", 227, "1.3.6", 226) > 0)
    }

    @Test
    fun `older build with same version name is not newer`() {
        assertTrue(UpdateManager.compareReleaseVersions("1.3.6", 225, "1.3.6", 226) < 0)
    }

    @Test
    fun `same name and build is identical`() {
        assertEquals(0, UpdateManager.compareReleaseVersions("1.3.6", 226, "1.3.6", 226))
    }

    @Test
    fun `higher semantic version wins regardless of build code`() {
        assertTrue(UpdateManager.compareReleaseVersions("1.3.7", 1, "1.3.6", 999) > 0)
    }

    @Test
    fun `lower semantic version stays older despite higher build code`() {
        assertTrue(UpdateManager.compareReleaseVersions("1.3.5", 999, "1.3.6", 1) < 0)
    }
}


class UpdateDeltaBaseSelectionTest {

    @Test
    fun `explicit patch is accepted only for exact installed source`() {
        assertTrue(
            UpdateManager.isDeltaCompatibleWithInstalledBuild(
                "qabas-1.3.6+227-from-1.3.6+226.delta", "1.3.6", 226, 227
            )
        )
        assertFalse(
            UpdateManager.isDeltaCompatibleWithInstalledBuild(
                "qabas-1.3.6+227-from-1.3.6+225.delta", "1.3.6", 226, 227
            )
        )
    }

    @Test
    fun `legacy patch is accepted only for immediately previous build`() {
        assertTrue(
            UpdateManager.isDeltaCompatibleWithInstalledBuild(
                "qabas-1.3.6+227.delta", "1.3.6", 226, 227
            )
        )
        assertFalse(
            UpdateManager.isDeltaCompatibleWithInstalledBuild(
                "qabas-1.3.6+227.delta", "1.3.6", 225, 227
            )
        )
    }

    @Test
    fun `explicit base rejects same code with a different version name`() {
        assertFalse(
            UpdateManager.isDeltaCompatibleWithInstalledBuild(
                "qabas-1.3.6+227-from-1.3.5+226.delta", "1.3.6", 226, 227
            )
        )
    }
}
