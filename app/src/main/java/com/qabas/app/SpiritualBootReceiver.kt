package com.qabas.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SpiritualBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            SpiritualNotificationManager.initialize(context)
            PrayerAlarmManager.scheduleAllPrayers(context)
        }
    }
}
