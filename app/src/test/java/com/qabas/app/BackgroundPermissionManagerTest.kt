package com.qabas.app

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackgroundPermissionManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun batteryOptimizationStatusReflectsTheSystemService() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        assertEquals(
            powerManager.isIgnoringBatteryOptimizations(context.packageName),
            BackgroundPermissionManager.isBatteryOptimizationIgnored(context)
        )
    }

    @Test
    fun exactAlarmStatusReflectsTheSystemService() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        assertEquals(
            alarmManager.canScheduleExactAlarms(),
            BackgroundPermissionManager.canScheduleExactAlarms(context)
        )
    }

    @Test
    fun fullScreenIntentStatusReflectsTheSystemService() {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        assertEquals(
            notificationManager.canUseFullScreenIntent(),
            BackgroundPermissionManager.canUseFullScreenIntent(context)
        )
    }

    @Test
    fun aggregateReadinessMatchesTheIndividualPermissionStates() {
        val expected =
            BackgroundPermissionManager.isBatteryOptimizationIgnored(context) &&
                BackgroundPermissionManager.canScheduleExactAlarms(context) &&
                BackgroundPermissionManager.areNotificationsEnabled(context) &&
                BackgroundPermissionManager.canUseFullScreenIntent(context)

        assertEquals(expected, BackgroundPermissionManager.isAllBackgroundPermissionsGranted(context))
    }
}
