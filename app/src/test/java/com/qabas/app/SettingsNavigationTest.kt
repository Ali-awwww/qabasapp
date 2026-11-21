package com.qabas.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNavigationTest {

    private fun item(
        title: String,
        screen: SettingsSubScreen
    ) = SearchableSetting(
        title = title,
        subtitle = "اختبار",
        icon = Icons.Default.Settings,
        subScreen = screen
    )

    @Test
    fun developerContactItemOpensUnifiedContactCenter() {
        val action = settingsItemAction(
            item("مركز التواصل مع المطور", SettingsSubScreen.MAIN)
        )

        assertTrue(action is SettingsItemAction.OpenContact)
    }

    @Test
    fun contactSearchLabelStillOpensContactAfterTextChanges() {
        val action = settingsItemAction(
            item("التواصل والمراسلة مع فريق مساعد المسلم", SettingsSubScreen.MAIN)
        )

        assertTrue(action is SettingsItemAction.OpenContact)
    }

    @Test
    fun ordinarySettingsItemOpensItsDeclaredSubScreen() {
        val action = settingsItemAction(
            item("إعدادات الإشعارات والتنبيهات", SettingsSubScreen.NOTIFICATION_SETTINGS)
        )

        assertEquals(
            SettingsItemAction.OpenSubScreen(SettingsSubScreen.NOTIFICATION_SETTINGS),
            action
        )
    }

    @Test
    fun xdeltaItemKeepsItsDeveloperSubScreen() {
        val action = settingsItemAction(
            item("أدوات المطور وتحديثات xdelta", SettingsSubScreen.GITHUB_XDELTA_HUB)
        )

        assertEquals(
            SettingsItemAction.OpenSubScreen(SettingsSubScreen.GITHUB_XDELTA_HUB),
            action
        )
    }

    @Test
    fun aMainItemWithoutContactLabelDoesNotHijackNavigation() {
        val action = settingsItemAction(item("الإعدادات العامة", SettingsSubScreen.MAIN))

        assertEquals(SettingsItemAction.OpenSubScreen(SettingsSubScreen.MAIN), action)
    }
}
