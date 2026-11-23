package com.qabas.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

/**
 * تقدّم الختمة من الخطة الموحّدة في المصحف وآخر قراءة فعلية —
 * لا مخزن ثانٍ في MuslimToolsEngine. (كانت هذه الاختبارات تغطي
 * مخططاً موازياً لا يقرأ تقدّمه أحد، فحُذف معه.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MuslimToolsEngineKhatmaTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("muslim_tools_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun startOfToday(): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun savePlanFromStart(targetDays: Int = 30) {
        UserPreferencesManager.saveMushafKhatmahPlan(
            context,
            UserPreferencesManager.MushafKhatmahPlan(
                startSurah = 1,
                startAyah = 1,
                startPage = 1,
                startMillis = startOfToday(),
                targetDays = targetDays,
                createdAtMillis = System.currentTimeMillis()
            )
        )
    }

    @Test
    fun `no khatma plan means zero progress in worship stats`() {
        assertNull(UserPreferencesManager.getKhatmahProgress(context))
        assertEquals(0f, MuslimToolsEngine(context).getWorshipStats().khatmaProgress, 0f)
    }

    @Test
    fun `progress follows actual reading position in pages`() {
        savePlanFromStart(30)
        // آخر قراءة: أول آية في البقرة، صفحة 8 — قُرئت 7 صفحات من 604.
        UserPreferencesManager.saveLastReadQuran(context, 2, 1, 8)

        val progress = UserPreferencesManager.getKhatmahProgress(context)!!
        assertEquals(7f / 604f, progress.fraction, 0.001f)
        assertEquals("صفحة", progress.unitWord)
        assertEquals(7, progress.readUnits)
        assertEquals(604, progress.totalUnits)
        assertEquals(597, progress.leftUnits)
        assertEquals(30, progress.daysLeft)
        assertEquals(20, progress.suggestedDailyUnits)

        assertEquals(
            7f / 604f,
            MuslimToolsEngine(context).getWorshipStats().khatmaProgress,
            0.001f
        )
    }

    @Test
    fun `progress falls back to ayahs when no page is known`() {
        UserPreferencesManager.saveMushafKhatmahPlan(
            context,
            UserPreferencesManager.MushafKhatmahPlan(
                startSurah = 1,
                startAyah = 1,
                startPage = null,
                startMillis = startOfToday(),
                targetDays = 30,
                createdAtMillis = System.currentTimeMillis()
            )
        )
        // أول آية في البقرة بلا صفحة — قُرئت 7 آيات من 6236.
        UserPreferencesManager.saveLastReadQuran(context, 2, 1, null)

        val progress = UserPreferencesManager.getKhatmahProgress(context)!!
        assertEquals(7f / 6236f, progress.fraction, 0.0001f)
        assertEquals("آية", progress.unitWord)
        assertEquals(7, progress.readUnits)
        assertEquals(6236, progress.totalUnits)
    }

    @Test
    fun `reaching the last ayah completes the plan`() {
        savePlanFromStart(30)
        UserPreferencesManager.saveLastReadQuran(context, 114, 6, 604)

        val progress = UserPreferencesManager.getKhatmahProgress(context)!!
        assertEquals(1f, progress.fraction, 0f)
        assertTrue(progress.completed)
        assertEquals(1f, MuslimToolsEngine(context).getWorshipStats().khatmaProgress, 0f)
    }

    @Test
    fun `invalid stored plan is ignored safely`() {
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("mushaf_khatmah_plan", "{invalid")
            .commit()

        assertNull(UserPreferencesManager.getKhatmahProgress(context))
        assertEquals(0f, MuslimToolsEngine(context).getWorshipStats().khatmaProgress, 0f)
    }
}
