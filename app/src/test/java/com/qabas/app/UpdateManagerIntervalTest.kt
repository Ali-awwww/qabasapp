package com.qabas.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * الفحص التلقائي لا ينزل أبداً تحت ساعتين، وأي قيمة محفوظة تُقصّ عند الحد الأدنى.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpdateManagerIntervalTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .edit().remove("update_check_interval_hours").commit()
    }

    @Test
    fun `default interval is the two hour floor`() {
        assertEquals(2, UpdateManager.getAutoCheckIntervalHours(context))
        assertEquals(2L * 60 * 60 * 1000, UpdateManager.getAutoCheckIntervalMs(context))
    }

    @Test
    fun `values below the floor are clamped to two hours`() {
        assertEquals(2, UpdateManager.setAutoCheckIntervalHours(context, 0))
        assertEquals(2, UpdateManager.getAutoCheckIntervalHours(context))

        assertEquals(2, UpdateManager.setAutoCheckIntervalHours(context, -5))
        assertEquals(2, UpdateManager.getAutoCheckIntervalHours(context))
    }

    @Test
    fun `chosen interval persists and converts to milliseconds`() {
        assertEquals(6, UpdateManager.setAutoCheckIntervalHours(context, 6))
        assertEquals(6, UpdateManager.getAutoCheckIntervalHours(context))
        assertEquals(6L * 60 * 60 * 1000, UpdateManager.getAutoCheckIntervalMs(context))
    }

    @Test
    fun `stored value below the floor is clamped on read`() {
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .edit().putInt("update_check_interval_hours", 1).commit()
        assertEquals(2, UpdateManager.getAutoCheckIntervalHours(context))
    }
}
