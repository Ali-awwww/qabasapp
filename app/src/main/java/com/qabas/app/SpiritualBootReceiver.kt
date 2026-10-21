package com.qabas.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SpiritualBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action
        if (a == Intent.ACTION_BOOT_COMPLETED || a == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            a == "android.intent.action.QUICKBOOT_POWERON" || a == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            NotificationPreferencesManager.scheduleAllConfiguredAlarms(context)
            if (NotificationPreferencesManager.masterEnabled.value &&
                NotificationPreferencesManager.prayerAzanEnabled.value
            ) {
                try { AzanCountdownService.start(context) } catch (_: Exception) {}
            }
        }
    }
}
