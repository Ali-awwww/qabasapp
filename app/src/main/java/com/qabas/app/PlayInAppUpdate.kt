package com.qabas.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * ▶️ PlayInAppUpdate — مسار التحديث السلس عبر Google Play
 *
 * لماذا؟
 * عند تنزيل APK وتثبيته يدوياً، يفرض أندرويد 8+:
 *   1) إذن «تثبيت من مصادر غير معروفة»
 *   2) نافذة تثبيت نظام إلزامية
 * عبر Play In-App Update يختفي كل ذلك: نافذة واحدة من Google، نقرة واحدة،
 * ولا حاجة لأي أذونات إضافية.
 *
 * الشرط: أن يكون التطبيق منشوراً على Google Play. إن لم يكن، تعمل المسارات
 * الأخرى (xdelta / APK) كما هي — PlayInAppUpdate هنا just يتجاهل ذلك بأمان.
 */
object PlayInAppUpdate {

    private const val TAG = "PlayInAppUpdate"
    private const val REQUEST_CODE_FLEXIBLE = 8801
    private const val REQUEST_CODE_IMMEDIATE = 8802

    /** هل التطبيق قادم من Google Play؟ (الطريق السلس متاح) */
    fun isPlayInstalled(context: Context): Boolean = runCatching {
        val installer = context.packageManager
            .getInstallSourceInfo(context.packageName)
            .installingPackageName
        installer == "com.android.vending"
    }.getOrDefault(false)

    /**
     * هل يوجد تحديث فوري متاح عبر Play؟
     * يستدعي onResult(true) إن وُجد تحديث قابل للتطبيق فوراً.
     */
    fun checkImmediateUpdate(activity: Activity, onResult: (Boolean) -> Unit) {
        if (!isPlayInstalled(activity)) {
            onResult(false)
            return
        }
        runCatching {
            val manager = AppUpdateManagerFactory.create(activity)
            manager.appUpdateInfo
                .addOnSuccessListener { info ->
                    val available = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                        info.installStatus() == InstallStatus.DOWNLOADING ||
                        info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                    onResult(available)
                }
                .addOnFailureListener {
                    Log.d(TAG, "تعذّر فحص تحديث Play: ${it.message}")
                    onResult(false)
                }
        }.onFailure {
            onResult(false)
        }
    }

    /**
     * تشغيل التحديث الفوري من Google Play (تحديث إلزامي داخل التطبيق).
     */
    fun startImmediateUpdate(activity: Activity) {
        if (!isPlayInstalled(activity)) return
        runCatching {
            val manager = AppUpdateManagerFactory.create(activity)
            manager.appUpdateInfo
                .addOnSuccessListener { info ->
                    if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                        manager.startUpdateFlowForResult(
                            activity,
                            AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                            REQUEST_CODE_IMMEDIATE
                        )
                    }
                }
                .addOnFailureListener { Log.d(TAG, "فشل تشغيل تحديث Play: ${it.message}") }
        }
    }

    /**
     * تشغيل التحديث المرن (نافذة اختيار التحديث) — يُستخدم عند فشل تثبيت APK.
     */
    fun startFlexibleUpdate(activity: Activity) {
        if (!isPlayInstalled(activity)) return
        runCatching {
            val manager = AppUpdateManagerFactory.create(activity)
            manager.appUpdateInfo
                .addOnSuccessListener { info ->
                    if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                        manager.startUpdateFlowForResult(
                            activity,
                            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                            REQUEST_CODE_FLEXIBLE
                        )
                    }
                }
                .addOnFailureListener { Log.d(TAG, "فشل تشغيل تحديث Play المرن: ${it.message}") }
        }
    }

    /**
     * هل اكتمل تثبيت تحديث Play المرن (يحتاج إعادة تشغيل)؟
     */
    fun isFlexibleInstallComplete(context: Context): Boolean = runCatching {
        AppUpdateManagerFactory.create(context).appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    AppUpdateManagerFactory.create(context).completeUpdate()
                }
            }
        false
    }.getOrDefault(false)

    /** هل التطبيق على Play؟ يُستخدم لعرض مسار التحديث الأنسب في الواجهة. */
    fun preferredChannelLabel(context: Context): String =
        if (isPlayInstalled(context)) "Google Play" else "قناة التحديث الرسمية"
}
