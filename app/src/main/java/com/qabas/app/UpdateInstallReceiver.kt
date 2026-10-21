package com.qabas.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * 📦 UpdateInstallReceiver — أدوات بناء Intent التثبيت.
 *
 * ملاحظة أندرويد 12+: يُمنع بدء نشاط من إشعار عبر BroadcastReceiver
 * (notification trampoline)، لذا يبني [pendingIntent] نشاط MainActivity
 * مباشرةً، ويعالجه MainActivity في onNewIntent/onResume ثم يفتح المثبّت.
 * صنف BroadcastReceiver نفسه باقٍ فقط للتوافق مع الإشعارات القديمة.
 */
class UpdateInstallReceiver : android.content.BroadcastReceiver() {

    companion object {
        const val ACTION_INSTALL = "com.qabas.app.ACTION_INSTALL_UPDATE"
        const val EXTRA_VERSION_NAME = "extra_version_name"
        const val EXTRA_VERSION_CODE = "extra_version_code"
        const val EXTRA_APK_SIZE = "extra_apk_size"
        const val REQUEST_CODE = 7711

        /** Intent نشاط MainActivity يفتح واجهة تثبيت نسخة محددة */
        fun installActivityIntent(
            context: Context,
            versionName: String,
            versionCode: Int,
            apkSize: Long = 0L
        ): Intent =
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_INSTALL
                putExtra(EXTRA_VERSION_NAME, versionName)
                putExtra(EXTRA_VERSION_CODE, versionCode)
                putExtra(EXTRA_APK_SIZE, apkSize)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

        /** PendingIntent نشاطي صالح من الإشعارات (بدون trampoline) */
        fun pendingIntent(
            context: Context,
            versionName: String,
            versionCode: Int,
            apkSize: Long = 0L
        ): PendingIntent {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getActivity(
                context, REQUEST_CODE,
                installActivityIntent(context, versionName, versionCode, apkSize), flags
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        // مسار التوافق مع الإشعارات القديمة فقط — دون معلومات نسخة دقيقة
        // لا نثبّت حزمة غير محددة الهوية بدقة.
    }
}
