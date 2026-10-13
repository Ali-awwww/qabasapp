package com.qabas.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log

/**
 * مستقبل إعادة تشغيل الجهاز - يعيد جدولة جميع مواقيت الصلاة والأذان بعد إعادة تشغيل النظام
 * (Reschedules all prayer alarms after device reboot)
 */
class PrayerAzanBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PrayerAzanBootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        val isBootAction = action == Intent.ACTION_BOOT_COMPLETED ||
                action == "android.intent.action.LOCKED_BOOT_COMPLETED" ||
                action == "android.intent.action.QUICKBOOT_POWERON" ||
                action == "com.htc.intent.action.QUICKBOOT_POWERON"

        val isTimeChange = action == Intent.ACTION_TIME_CHANGED ||
                action == Intent.ACTION_TIMEZONE_CHANGED

        if (!isBootAction && !isTimeChange) return

        Log.d(TAG, "Re-scheduling prayer alarms after: $action")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Qabas:PrayerAzanBootWakeLock"
        )
        wakeLock?.acquire(15000L)

        try {
            PrayerAlarmManager.scheduleAllPrayers(context)
            try { AzanCountdownService.start(context) } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.e(TAG, "Failed to re-schedule prayers: ${e.message}")
        } finally {
            if (wakeLock?.isHeld == true) {
                try { wakeLock.release() } catch (_: Exception) {}
            }
        }
    }
}
