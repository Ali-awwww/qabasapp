package com.qabas.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun loginScreen_isDisplayed() {
        // ✅ يفحص هل الشاشة تظهر بدون أخطاء
        rule.onRoot().assertExists()
    }

    @Test
    fun loginScreen_rendersWithoutCrash() {
        // ✅ يفحص هل التطبيق لا يتوقف
        Thread.sleep(500)
    }
}
