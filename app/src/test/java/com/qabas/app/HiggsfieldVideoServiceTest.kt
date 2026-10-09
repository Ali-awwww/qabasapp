package com.qabas.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HiggsfieldVideoServiceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun testIsConfigured_returnsFalse_whenNoKeysSet() {
        assertFalse(HiggsfieldVideoService.isConfigured(context))
    }

    @Test
    fun testIsConfigured_returnsTrue_whenApiKeySet() {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("higgsfield_api_key", "hfld_test_key_1234567890").commit()

        assertTrue(HiggsfieldVideoService.isConfigured(context))
    }

    @Test
    fun testIsConfigured_returnsTrue_whenKeyIdAndSecretSet() {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("higgsfield_key_id", "my_id")
            .putString("higgsfield_key_secret", "my_secret")
            .commit()

        assertTrue(HiggsfieldVideoService.isConfigured(context))
    }

    @Test
    fun testGetAuthHeader_formatsBearerCorrectly() {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        
        // Without Bearer prefix
        prefs.edit().putString("higgsfield_api_key", "my_secret_token").commit()
        assertEquals("Bearer my_secret_token", HiggsfieldVideoService.getAuthHeader(context))

        // Already with Bearer prefix
        prefs.edit().putString("higgsfield_api_key", "Bearer my_secret_token").commit()
        assertEquals("Bearer my_secret_token", HiggsfieldVideoService.getAuthHeader(context))
    }

    @Test
    fun testApiKeyValidator_handlesPlaceholderCorrectly() = runBlocking {
        val result = ApiKeyValidator.validateKey(context, "higgsfield", "your_api_key_here")
        assertFalse(result.isValid)
        assertTrue(result.explanation.contains("المفتاح غير مضبوط") || result.explanation.contains("افتراضية"))
    }

    @Test
    fun testApiKeyValidator_handlesEmptyCorrectly() = runBlocking {
        val result = ApiKeyValidator.validateKey(context, "higgsfield", "")
        assertFalse(result.isValid)
    }

    @Test
    fun testDetectKeysForAutoFill_detectsHiggsfieldKey() {
        val clipboardText = "Here is my token: hfld_live_token_9876543210abcdef and some other text"
        val detected = detectKeysForAutoFill(clipboardText)
        assertTrue(detected.containsKey("higgsfield"))
        assertEquals("hfld_live_token_9876543210abcdef", detected["higgsfield"])
    }

    @Test
    fun testOperationalReadinessManager_includesHiggsfield() {
        val report = OperationalReadinessManager.calculateReadiness(context)
        assertNotNull(report)
        assertTrue(report.services.any { it.name.contains("Higgsfield") })
    }
}
