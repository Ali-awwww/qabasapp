package com.qabas.app

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class QabasApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        PerfTracker.onAppCreate()
        QabasCrashGuard.install(this)

        // عدّاد الإقلاعات لصحة الإصدار (انهيارات / إقلاعات)
        runCatching {
            val prefs = getSharedPreferences("qabas_prefs", MODE_PRIVATE)
            prefs.edit().putInt("app_launch_count", prefs.getInt("app_launch_count", 0) + 1).apply()
        }

        // رتبة «مطور» تلقائية لبريد المالك فقط — ولو كان مسجل الدخول مسبقاً (بطلب من المالك)
        try {
            val prefs = getSharedPreferences("qabas_prefs", MODE_PRIVATE)
            val email = prefs.getString("user_email", null)
            if (prefs.getBoolean("is_logged_in", false) && CloudServices.isOwnerAccount(email)) {
                prefs.edit().putBoolean("is_admin", true).apply()
                Log.d("QabasApplication", "Owner account detected — developer rank granted")
            }
        } catch (_: Exception) {
        }

        // Initialize core application services in background to avoid blocking the splash screen
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { AgentPrefs.init(this@QabasApplication) }
            runCatching { AppServices.init(this@QabasApplication) }
            runCatching { LocalAccountRegistry.init(this@QabasApplication) }
            
            runCatching { UpdateManager.reconcilePendingInstall(this@QabasApplication) }

            runCatching { AppRemoteConfig.refreshFromCloud(this@QabasApplication) }
            runCatching { RemoteNotificationsManager.startInbox(this@QabasApplication) }
            runCatching { DoctorMonitor.maybeRun(this@QabasApplication) }
            runCatching {
                DownloadsHubManager.init(this@QabasApplication)
                DownloadsHubManager.checkAndStartAutoUpdate(this@QabasApplication, force = false)
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        try {
            VideoCacheService.release()
        } catch (e: Exception) {
            Log.w("QabasApplication", "Error releasing VideoCacheService on terminate: ${e.message}")
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            VideoCacheService.release()
        } catch (e: Exception) {
            Log.w("QabasApplication", "Error releasing VideoCacheService on low memory: ${e.message}")
        }
    }
}
