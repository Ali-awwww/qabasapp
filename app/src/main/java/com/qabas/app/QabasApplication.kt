package com.qabas.app

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
            runCatching { ReelPreferencesManager.init(this@QabasApplication) }
                .onFailure { recordStartupFailure("ReelPreferencesManager.init", it) }
            QabasCrashGuard.markStartupStage(this@QabasApplication, "NetworkStatus.init")
            runCatching { UserPreferencesManager.initNetworkStatus(this@QabasApplication) }
                .onFailure { recordStartupFailure("UserPreferencesManager.initNetworkStatus", it) }

            QabasCrashGuard.markStartupStage(this@QabasApplication, "AgentPrefs.init")
            runCatching { AgentPrefs.init(this@QabasApplication) }
                .onFailure { recordStartupFailure("AgentPrefs.init", it) }

            QabasCrashGuard.markStartupStage(this@QabasApplication, "AppServices.init")
            runCatching { AppServices.init(this@QabasApplication) }
                .onFailure { recordStartupFailure("AppServices.init", it) }

            QabasCrashGuard.markStartupStage(this@QabasApplication, "LocalAccountRegistry.init")
            runCatching { LocalAccountRegistry.init(this@QabasApplication) }
                .onFailure { recordStartupFailure("LocalAccountRegistry.init", it) }

            // ق78: استعادة الجلسة شبكية وقد تنتظر ثواني على شبكة
            // بطيئة (مهلة 8ث لانتظار التهيئة + تحديث بلا مهلة صريحة)
            // — وكانت في سلسلة متتابعة واحدة تحجب كل التهيئات
            // المحلية بعدها (التفضيلات، المزامنة الصامتة، التحديث،
            // الإعدادات المحلية، الفاحص، التنزيلات). الآن تُطلق
            // بالتوازي عبر async وتمضي التهيئات المحلية فوراً،
            // ولا تُنتظر إلا قبل الخطوات التي تقرأ الجلسة فعلاً
            // (الهوية، رمز الدفع، صندوق البث) فيحافظ ترتيبها.
            QabasCrashGuard.markStartupStage(this@QabasApplication, "SupabaseRepository.ensureAuthenticatedSession")
            val authSession = async {
                runCatching { SupabaseRepository.getInstance().ensureAuthenticatedSession() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                    .onFailure { recordStartupFailure("SupabaseRepository.ensureAuthenticatedSession", it) }
            }

            QabasCrashGuard.markStartupStage(this@QabasApplication, "OfflineReels.init")
            runCatching { UserPreferencesManager.init(this@QabasApplication) }
                .onFailure { recordStartupFailure("UserPreferencesManager.init", it) }
            runCatching { SilentReelPrefetcher.startSync(this@QabasApplication) }
                .onFailure { recordStartupFailure("SilentReelPrefetcher.startSync", it) }

            QabasCrashGuard.markStartupStage(this@QabasApplication, "UpdateManager.reconcilePendingInstall")
            runCatching { UpdateManager.reconcilePendingInstall(this@QabasApplication) }
                .onFailure { recordStartupFailure("UpdateManager.reconcilePendingInstall", it) }

            QabasCrashGuard.markStartupStage(this@QabasApplication, "AppRemoteConfig.reloadLocal")
            runCatching { AppRemoteConfig.reloadLocal(this@QabasApplication) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                .onFailure { recordStartupFailure("AppRemoteConfig.reloadLocal", it) }

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

            // انتظار الجلسة هنا فقط: ما بعدها يقرأ المستخدم الحالي
            // (الهوية من بيانات الجلسة، ومزامنة الرمز تتوقف على
            // المعرّف، وصندوق البث يقيّم الاستهداف بالدور).
            authSession.await()

            QabasCrashGuard.markStartupStage(this@QabasApplication, "AccountIdentityManager.refresh")
            runCatching { AccountIdentityManager.refresh(this@QabasApplication) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                .onFailure { recordStartupFailure("AccountIdentityManager.refresh", it) }

            QabasCrashGuard.markStartupStage(this@QabasApplication, "PushTokenManager.captureAndSync")
            runCatching { PushTokenManager.captureAndSync(this@QabasApplication) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                .onFailure { recordStartupFailure("PushTokenManager.captureAndSync", it) }

            QabasCrashGuard.markStartupStage(this@QabasApplication, "RemoteNotificationsManager.startInbox")
            runCatching { RemoteNotificationsManager.startInbox(this@QabasApplication) }
                .onFailure { recordStartupFailure("RemoteNotificationsManager.startInbox", it) }

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
