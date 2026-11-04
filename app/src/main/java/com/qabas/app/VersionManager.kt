package com.qabas.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object VersionManager {
    private const val TAG = "VersionManager"

    /** حالة التحديث المكتشفة */
    sealed class UpdateStatus {
        object NoUpdate : UpdateStatus()
        data class ForceUpdateRequired(val info: UpdateManager.UpdateInfo) : UpdateStatus()
        data class OptionalUpdateAvailable(val info: UpdateManager.UpdateInfo) : UpdateStatus()
    }

    /** فحص حالة التحديث بالتواصل مع UpdateManager */
    suspend fun checkUpdateStatus(context: Context, force: Boolean = false): UpdateStatus {
        val update = UpdateManager.checkForUpdate(context, force) ?: return UpdateStatus.NoUpdate
        
        return if (update.isForce) {
            UpdateStatus.ForceUpdateRequired(update)
        } else {
            UpdateStatus.OptionalUpdateAvailable(update)
        }
    }

    /** فحص وجود تحديث جديد (للتوافق مع الكود القديم) */
    suspend fun checkRemoteVersion(context: Context, force: Boolean = false): UpdateManager.UpdateInfo? {
        return UpdateManager.checkForUpdate(context, force)
    }

    /** 
     * التحقق من سلامة نسخة محملة محلياً.
     */
    fun getLocalUpdateApk(context: Context, update: UpdateManager.UpdateInfo): File? {
        return UpdateManager.getValidatedDownloadedApk(
            context, 
            update.versionName, 
            update.versionCode, 
            update.apkSize
        )
    }

    /**
     * تنفيذ عملية التحديث:
     */
    fun startUpdateFlow(context: Context, update: UpdateManager.UpdateInfo, forceFull: Boolean = false) {
        PatchManager.reset()
        UpdateDownloadService.start(context, update, forceFull)
    }

    /**
     * منطق تطبيق الرقعة.
     */
    suspend fun applyUpdatePatch(
        context: Context,
        update: UpdateManager.UpdateInfo,
        deltaFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "⚡ Applying xdelta patch via PatchManager: ${deltaFile.name}")
            return@withContext PatchManager.applyXdeltaPatch(context, update, deltaFile)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Patch application failed: ${e.message}")
            false
        }
    }

    /** هل الإصدار الحالي قيد التشغيل هو الأحدث؟ */
    fun isUpToDate(context: Context, remoteVersionCode: Int): Boolean {
        val current = UpdateManager.getCurrentVersionCode(context)
        return current >= remoteVersionCode
    }
}
