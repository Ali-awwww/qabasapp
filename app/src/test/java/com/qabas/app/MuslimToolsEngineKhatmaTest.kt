package com.qabas.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
    }

    @Test
    fun `saved khatma plan survives a new engine instance`() {
        val writer = MuslimToolsEngine(context)
        assertTrue(writer.saveKhatmaPlan(15))

        val restored = MuslimToolsEngine(context).getSavedKhatmaPlan()

        assertEquals(writer.generateKhatmaPlan(15), restored)
    }

    @Test
    fun `no saved khatma plan is reported as absent`() {
        assertNull(MuslimToolsEngine(context).getSavedKhatmaPlan())
    }

    @Test
    fun `invalid saved khatma data is ignored safely`() {
        context.getSharedPreferences("muslim_tools_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("saved_khatma_plan", "{invalid")
            .commit()

        assertNull(MuslimToolsEngine(context).getSavedKhatmaPlan())
    }

    @Test
    fun `non positive duration is normalized before saving`() {
        val engine = MuslimToolsEngine(context)

        assertTrue(engine.saveKhatmaPlan(0))
        assertEquals(engine.generateKhatmaPlan(1), engine.getSavedKhatmaPlan())
        assertFalse(engine.getSavedKhatmaPlan()!!.days <= 0)
    }
}
