package com.qabas.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 📦 UpdateInstallReceiver — يفتح مثبّت أندرويد مباشرة بعد اكتمال تنزيل التحديث.
 *
 * الهدف: جعل تجربة التحديث أقرب ما يمكن للتلقائية:
 *   تنزيل في الخلفية ← إشعار دائم ← عند الاكتمال: زر «تثبيت الآن» في الإشعار
 *   يفتح نافذة تثبيت النظام مباشرة (بلا رحلة عبر الواجهة).
 *
 * ملاحظة: أندرويد 8+ يفرض عرض نافذة النظام — لا يمكن تجاوزها إلا عبر
 * Play In-App Update (انظر [PlayInAppUpdate]).
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "UpdateInstallReceiver"
        const val ACTION_INSTALL = "com.qabas.app.ACTION_INSTALL_UPDATE"
        const val EXTRA_VERSION_NAME = "extra_version_name"
        const val REQUEST_CODE = 7711

        /** يبني Intent بث intent لتثبيت نسخة محددة */
        fun intent(context: Context, versionName: String): Intent =
            Intent(context, UpdateInstallReceiver::class.java).apply {
                action = ACTION_INSTALL
                putExtra(EXTRA_VERSION_NAME, versionName)
            }

        /** PendingIntent صالح للبدء من إشعار أو خدمة */
        fun pendingIntent(context: Context, versionName: String): PendingIntent {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getBroadcast(context, REQUEST_CODE, intent(context, versionName), flags)
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_INSTALL) return

        val versionName = intent.getStringExtra(EXTRA_VERSION_NAME).orEmpty()
        val apk = if (versionName.isNotBlank()) {
            UpdateManager.getDownloadedApkFile(context, versionName)
        } else {
            null
        }

        if (apk == null || !apk.exists()) {
            Log.w(TAG, "لا توجد حزمة منزّلة للتثبيت (version=$versionName)")
            return
        }

        Log.d(TAG, "فتح المثبّت للحزمة: ${apk.absolutePath}")
        UpdateManager.launchInstaller(context, apk)
    }
}
