package com.qabas.app

import android.app.Activity
import android.content.Context
import android.util.Log
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
 * الأخرى (xdelta / APK) كما هي — وكل دالة هنا تتراجع بأمان.
 */
object PlayInAppUpdate {

    private const val TAG = "PlayInAppUpdate"

    /** هل التطبيق قادم من Google Play؟ (الطريق السلس متاح) */
    fun isPlayInstalled(context: Context): Boolean = runCatching {
        context.packageManager
            .getInstallSourceInfo(context.packageName)
            .installingPackageName == "com.android.vending"
    }.getOrDefault(false)

    /**
     * هل يوجد تحديث متاح عبر Play؟ (غير حاجب للتشغيل)
     */
    fun checkUpdateAvailable(context: Context, onResult: (Boolean) -> Unit) {
        if (!isPlayInstalled(context)) {
            onResult(false)
            return
        }
        runCatching {
            AppUpdateManagerFactory.create(context).appUpdateInfo
                .addOnSuccessListener { info ->
                    onResult(info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE)
                }
                .addOnFailureListener {
                    Log.d(TAG, "تعذّر فحص تحديث Play: ${it.message}")
                    onResult(false)
                }
        }.onFailure { onResult(false) }
    }

    /**
     * تشغيل التحديث الفوري من Google Play (نافذة إلزامية داخل التطبيق).
     * يحتاج Activity لأن Play يشغّل/update sheet على Activity الخاص بك.
     */
    fun startImmediateUpdate(context: Context, onResult: (Boolean) -> Unit = {}) {
        startUpdateFlow(context, AppUpdateType.IMMEDIATE, onResult)
    }

    /**
     * تشغيل التحديث المرن (نافذة اختيار التحديث) — يُستخدم عند فشل تثبيت APK.
     */
    fun startFlexibleUpdate(context: Context, onResult: (Boolean) -> Unit = {}) {
        startUpdateFlow(context, AppUpdateType.FLEXIBLE, onResult)
    }

    private fun startUpdateFlow(context: Context, type: AppUpdateType, onResult: (Boolean) -> Unit) {
        if (!isPlayInstalled(context)) {
            onResult(false)
            return
        }
        val activity = context as? Activity
        if (activity == null) {
            Log.w(TAG, "لا يوجد Activity — تعذّر تشغيل تحديث Play")
            onResult(false)
            return
        }
        runCatching {
            val manager = AppUpdateManagerFactory.create(activity)
            manager.appUpdateInfo
                .addOnSuccessListener { info ->
                    if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) {
                        onResult(false)
                        return@addOnSuccessListener
                    }
                    val options = AppUpdateOptions.newBuilder(type).build()
                    manager.startUpdateFlow(info, activity, options)
                        .addOnSuccessListener { onResult(true) }
                        .addOnFailureListener {
                            Log.d(TAG, "فشل تشغيل تحديث Play: ${it.message}")
                            onResult(false)
                        }
                }
                .addOnFailureListener {
                    Log.d(TAG, "تعذّر جلب معلومات التحديث: ${it.message}")
                    onResult(false)
                }
        }.onFailure {
            Log.d(TAG, "PlayInAppUpdate: ${it.message}")
            onResult(false)
        }
    }

    /**
     * هل اكتمل تنزيل التحديث المرن وجاهز للتثبيت؟ (يحتاج completeUpdate)
     */
    fun isFlexibleInstallReady(context: Context, onResult: (Boolean) -> Unit) {
        if (!isPlayInstalled(context)) {
            onResult(false)
            return
        }
        runCatching {
            AppUpdateManagerFactory.create(context).appUpdateInfo
                .addOnSuccessListener { info ->
                    onResult(info.installStatus() == InstallStatus.DOWNLOADED)
                }
                .addOnFailureListener { onResult(false) }
        }.onFailure { onResult(false) }
    }

    /** إنهاء التثبيت المرن (بعد اكتمال التنزيل) */
    fun completeFlexibleInstall(context: Context) {
        if (!isPlayInstalled(context)) return
        runCatching {
            AppUpdateManagerFactory.create(context).appUpdateInfo
                .addOnSuccessListener { info ->
                    if (info.installStatus() == InstallStatus.DOWNLOADED) {
                        AppUpdateManagerFactory.create(context).completeUpdate()
                    }
                }
        }
    }

    /** اسم القناة المناسب للعرض في الواجهة */
    fun preferredChannelLabel(context: Context): String =
        if (isPlayInstalled(context)) "Google Play" else "قناة التحديث الرسمية"
}
