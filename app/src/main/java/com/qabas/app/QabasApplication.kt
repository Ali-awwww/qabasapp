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
        runCatching { UpdateCheckWorker.schedule(this) }

        // عدّاد الإقلاعات لصحة الإصدار (انهيارات / إقلاعات)
        runCatching {
            val prefs = getSharedPreferences("qabas_prefs", MODE_PRIVATE)
            prefs.edit().putInt("app_launch_count", prefs.getInt("app_launch_count", 0) + 1).apply()
        }

        // Initialize core application services in background to avoid blocking the splash screen
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            QabasCrashGuard.markStartupStage(this@QabasApplication, "AgentPrefs.init")
            runCatching { AgentPrefs.init(this@QabasApplication) }
                .onFailure { recordStartupFailure("AgentPrefs.init", it) }

            QabasCrashGuard.markStartupStage(this@QabasApplication, "AppServices.init")
            runCatching { AppServices.init(this@QabasApplication) }
                .onFailure { recordStartupFailure("AppServices.init", it) }

            QabasCrashGuard.markStartupStage(this@QabasApplication, "LocalAccountRegistry.init")
            runCatching { LocalAccountRegistry.init(this@QabasApplication) }
                .onFailure { recordStartupFailure("LocalAccountRegistry.init", it) }

            QabasCrashGuard.markStartupStage(this@QabasApplication, "SupabaseRepository.ensureAuthenticatedSession")
            runCatching { SupabaseRepository.getInstance().ensureAuthenticatedSession() }
                .onFailure { recordStartupFailure("SupabaseRepository.ensureAuthenticatedSession", it) }

            QabasCrashGuard.markStartupStage(this@QabasApplication, "UpdateManager.reconcilePendingInstall")
            runCatching { UpdateManager.reconcilePendingInstall(this@QabasApplication) }
                .onFailure { recordStartupFailure("UpdateManager.reconcilePendingInstall", it) }

            QabasCrashGuard.markStartupStage(this@QabasApplication, "AppRemoteConfig.refreshFromCloud")
            runCatching { AppRemoteConfig.refreshFromCloud(this@QabasApplication) }
                .onFailure { recordStartupFailure("AppRemoteConfig.refreshFromCloud", it) }

            QabasCrashGuard.markStartupStage(this@QabasApplication, "RemoteNotificationsManager.startInbox")
            runCatching { RemoteNotificationsManager.startInbox(this@QabasApplication) }
                .onFailure { recordStartupFailure("RemoteNotificationsManager.startInbox", it) }

            QabasCrashGuard.markStartupStage(this@QabasApplication, "DoctorMonitor.maybeRun")
            runCatching { DoctorMonitor.maybeRun(this@QabasApplication) }
                .onFailure { recordStartupFailure("DoctorMonitor.maybeRun", it) }

            QabasCrashGuard.markStartupStage(this@QabasApplication, "DownloadsHubManager.init+checkAndStartAutoUpdate")
            runCatching {
                DownloadsHubManager.init(this@QabasApplication)
                DownloadsHubManager.checkAndStartAutoUpdate(this@QabasApplication, force = false)
            }.onFailure {
                recordStartupFailure("DownloadsHubManager", it)
            }

            QabasCrashGuard.markStartupStage(this@QabasApplication, "startup.completed")
            QabasCrashGuard.schedulePendingUploads(this@QabasApplication)
        }
    }


    /**
     * A diagnostic failure must never escape the startup coroutine and become
     * a second application failure while handling the original startup error.
     */
    private fun recordStartupFailure(stage: String, failure: Throwable) {
        runCatching {
            QabasCrashGuard.record(
                context = this,
                thread = Thread.currentThread().name,
                throwable = failure,
                source = "startup:$stage",
                notifyOwner = false
            )
        }.onFailure { reportingFailure ->
            Log.e("QabasApplication", "Unable to persist startup diagnostic for $stage", reportingFailure)
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
