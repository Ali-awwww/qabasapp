package com.qabas.app

import android.content.Context

object VersionManager {
    private const val TAG = "VersionManager"

    /** فحص وجود تحديث جديد (للتوافق مع الكود القديم) */
    suspend fun checkRemoteVersion(context: Context, force: Boolean = false): UpdateManager.UpdateInfo? {
        return UpdateManager.checkForUpdate(context, force)
    }

    /**
     * فحص وجود تحديث جديد مع تمييز الفشل عن «لا يوجد تحديث» — للشاشات التي
     * تعرض نتيجة الفحص للمستخدم (عن التطبيق)، حتى لا تدّعي «أحدث إصدار ✓»
     * عند انقطاع الشبكة أو تعطل الخادم.
     */
    suspend fun checkRemoteVersionDetailed(context: Context, force: Boolean = false): UpdateManager.UpdateCheckResult {
        return UpdateManager.checkForUpdateDetailed(context, force)
    }

}
