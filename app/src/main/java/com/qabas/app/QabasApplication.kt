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

        // Make the application context available before crash upload or Supabase clients initialize.
        AppServices.appContext = this
        QabasCrashGuard.install(this)
        QabasCrashGuard.markStartupStage(this, "QabasApplication.onCreate")
        QabasCrashGuard.schedulePendingUploads(this)

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
            QabasCrashGuard.markStartupStage(this@QabasApplication, "AgentPrefs.init")
            runCatching { AgentPrefs.init(this@QabasApplication) }
                .onFailure { QabasCrashGuard.record(this@QabasApplication, Thread.currentThread().name, it, "startup:AgentPrefs.init") }

            QabasCrashGuard.markStartupStage(this@QabasApplication, "AppServices.init")
            runCatching { AppServices.init(this@QabasApplication) }
                .onFailure { QabasCrashGuard.record(this@QabasApplication, Thread.currentThread().name, it, "startup:AppServices.init") }

            QabasCrashGuard.markStartupStage(this@QabasApplication, "LocalAccountRegistry.init")
            runCatching { LocalAccountRegistry.init(this@QabasApplication) }
                .onFailure { QabasCrashGuard.record(this@QabasApplication, Thread.currentThread().name, it, "startup:LocalAccountRegistry.init") }

            QabasCrashGuard.markStartupStage(this@QabasApplication, "SupabaseRepository.ensureAuthenticatedSession")
            runCatching { SupabaseRepository.getInstance().ensureAuthenticatedSession() }
                .onFailure { QabasCrashGuard.record(this@QabasApplication, Thread.currentThread().name, it, "startup:SupabaseRepository.ensureAuthenticatedSession") }

            QabasCrashGuard.markStartupStage(this@QabasApplication, "UpdateManager.reconcilePendingInstall")
            runCatching { UpdateManager.reconcilePendingInstall(this@QabasApplication) }
                .onFailure { QabasCrashGuard.record(this@QabasApplication, Thread.currentThread().name, it, "startup:UpdateManager.reconcilePendingInstall") }

            QabasCrashGuard.markStartupStage(this@QabasApplication, "AppRemoteConfig.refreshFromCloud")
            runCatching { AppRemoteConfig.refreshFromCloud(this@QabasApplication) }
                .onFailure { QabasCrashGuard.record(this@QabasApplication, Thread.currentThread().name, it, "startup:AppRemoteConfig.refreshFromCloud") }

            QabasCrashGuard.markStartupStage(this@QabasApplication, "RemoteNotificationsManager.startInbox")
            runCatching { RemoteNotificationsManager.startInbox(this@QabasApplication) }
                .onFailure { QabasCrashGuard.record(this@QabasApplication, Thread.currentThread().name, it, "startup:RemoteNotificationsManager.startInbox") }

            QabasCrashGuard.markStartupStage(this@QabasApplication, "DoctorMonitor.maybeRun")
            runCatching { DoctorMonitor.maybeRun(this@QabasApplication) }
                .onFailure { QabasCrashGuard.record(this@QabasApplication, Thread.currentThread().name, it, "startup:DoctorMonitor.maybeRun") }

            QabasCrashGuard.markStartupStage(this@QabasApplication, "DownloadsHubManager.init+checkAndStartAutoUpdate")
            runCatching {
                DownloadsHubManager.init(this@QabasApplication)
                DownloadsHubManager.checkAndStartAutoUpdate(this@QabasApplication, force = false)
            }.onFailure {
                QabasCrashGuard.record(this@QabasApplication, Thread.currentThread().name, it, "startup:DownloadsHubManager")
            }

            QabasCrashGuard.markStartupStage(this@QabasApplication, "startup.completed")
            QabasCrashGuard.schedulePendingUploads(this@QabasApplication)
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
