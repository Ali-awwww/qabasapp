package com.qabas.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ق83: تصفير ذكر مكتمل واحداً كان يُبقي شارة إكمال الصباح
 * (daily_morning_azkar) — فتظل الرئيسية والحسنات تدّعيان اكتمال
 * أذكار الصباح وذكرٌ منها صفر.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AzkarResetFlagTest {

    private val app: Application
        get() = ApplicationProvider.getApplicationContext()

    private val todayKey: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    @Test
    fun `resetting one completed morning zikr retracts the daily flag`() {
        val viewModel = AzkarViewModel(app)
        val prefs = app.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val morning = viewModel.morningAzkar.value
        assertTrue("قائمة الصباح يجب ألا تكون فارغة", morning.isNotEmpty())

        // أكمل كل أذكار الصباح نقرةً نقرة.
        morning.forEach { item ->
            repeat(item.targetCount) { viewModel.incrementCount(item.id, item.targetCount) }
        }
        assertTrue(
            "إكمال الصباح يجب أن يرفع الشارة",
            prefs.getBoolean("daily_morning_azkar_$todayKey", false)
        )

        // صفّر ذكراً واحداً.
        val first = morning.first()
        viewModel.resetCount(first.id)

        assertFalse(
            "تصفير ذكر مكتمل يجب أن يسحب شارة إكمال الصباح",
            prefs.getBoolean("daily_morning_azkar_$todayKey", false)
        )
        assertEquals(0, viewModel.progressMap.value[first.id] ?: 0)
    }

    @Test
    fun `resetting a zikr in another category leaves the morning flag alone`() {
        val viewModel = AzkarViewModel(app)
        val prefs = app.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val morning = viewModel.morningAzkar.value
        morning.forEach { item ->
            repeat(item.targetCount) { viewModel.incrementCount(item.id, item.targetCount) }
        }
        assertTrue(prefs.getBoolean("daily_morning_azkar_$todayKey", false))

        // صفّر ذكر نوم — شارة الصباح لا شأن لها به.
        val sleepItem = viewModel.sleepAzkar.value.firstOrNull()
        assertTrue(sleepItem != null)
        viewModel.resetCount(sleepItem!!.id)

        assertTrue(
            "تصفير ذكر نوم يجب ألا يمس شارة الصباح",
            prefs.getBoolean("daily_morning_azkar_$todayKey", false)
        )
    }
}
