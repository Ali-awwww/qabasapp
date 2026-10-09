package com.qabas.app

import android.Manifest
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * 🛡️ مدير أذونات وصلاحيات العمل بالخلفية وحماية الأذان والتنبيهات
 * (Background Execution, Battery Optimization, Exact Alarms, & Autostart Manager)
 */
object BackgroundPermissionManager {

    /**
     * هل تم استثناء التطبيق من تحسين البطارية (Unrestricted Background Power)؟
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        } else {
            true
        }
    }

    /**
     * هل يمتلك التطبيق إذن جدولة المنبهات الدقيقة في الوقت المحدد؟
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() ?: true
        } else {
            true
        }
    }

    /**
     * هل تم منح إذن الإشعارات؟
     */
    fun areNotificationsEnabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * التحقق الشامل: هل كل أذونات العمل بالخلفية والأذان ممنوحة بنجاح؟
     */
    fun isAllBackgroundPermissionsGranted(context: Context): Boolean {
        return isBatteryOptimizationIgnored(context) &&
                canScheduleExactAlarms(context) &&
                areNotificationsEnabled(context)
    }

    /**
     * طلب استثناء التطبيق من تحسين استهلاك البطارية لضمان دقة الأذان والتنبيهات
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                if (!isBatteryOptimizationIgnored(context)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } else {
                    Toast.makeText(context, "صلاحية العمل بالخلفية دون قيود مفعّلة بالفعل ✅", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                try {
                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                } catch (ex: Exception) {
                    openAppSettings(context)
                }
            }
        }
    }

    /**
     * طلب إذن جدولة المنبهات الدقيقة (Android 12+)
     */
    fun requestScheduleExactAlarm(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (!canScheduleExactAlarms(context)) {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } else {
                    Toast.makeText(context, "إذن المنبهات الدقيقة مفعّل بالفعل ✅", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                openAppSettings(context)
            }
        }
    }

    /**
     * فتح إعدادات إشعارات التطبيق
     */
    fun openNotificationSettings(context: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppSettings(context)
        }
    }

    /**
     * فتح صفحة إعدادات التطبيق العامة
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر فتح الإعدادات تلقائياً", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * فتح إعدادات التشغيل التلقائي وحماية الخلفية الخاصة بالشركات المصنعة (Xiaomi, Huawei, Samsung, Oppo, Vivo)
     */
    fun openManufacturerAutostartSettings(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.US)
        val intents = mutableListOf<Intent>()

        when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
                intents.add(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
                intents.add(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")))
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                intents.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")))
                intents.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")))
            }
            manufacturer.contains("samsung") -> {
                intents.add(Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")))
                intents.add(Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")))
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> {
                intents.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")))
                intents.add(Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")))
            }
            manufacturer.contains("vivo") -> {
                intents.add(Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")))
                intents.add(Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")))
            }
        }

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}
        }

        openAppSettings(context)
        return false
    }

    /**
     * إعادة جدولة كل مواقيت الأذان والخدمات فور منح الصلاحيات
     */
    fun refreshBackgroundSchedulers(context: Context) {
        try {
            PrayerAlarmManager.scheduleAllPrayers(context)
            SpiritualNotificationManager.initialize(context)
        } catch (e: Exception) {
            // Safe execution
        }
    }
}
