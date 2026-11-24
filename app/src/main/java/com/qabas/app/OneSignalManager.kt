package com.qabas.app

import android.content.Context
import android.util.Log
import com.onesignal.OneSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * OneSignal — قناة الإشعارات الجديدة لتطبيق مساعد المسلم.
 *
 * يحل OneSignal محل FCM المباشر كقناة إرسال: يُرسل المطوّر الإشعارات من
 * لوحة OneSignal (مجانية)، ويستلمها التطبيق عبر OneSignal SDK 5.
 *
 * مهم:
 * - لا يعمل OneSignal قبل إنشاء تطبيق في لوحة OneSignal ووضع معرّفه
 *   (App ID، 36 حرفاً) في `ONESIGNAL_APP_ID` في ملف `.env` أو خاصية Gradle.
 *   ما دام المعرّف فارغاً يبقى المدير معطّلاً بهدوء ويستمر FCM كقناة احتياطية.
 * - لم يُحذف FCM: OneSignal نفسه يسلّم الإشعارات على أجهزة Google Play
 *   عبر FCM، و`MyFirebaseMessagingService` و`PushTokenManager` باقيان
 *   كاحتياطي حتى يُختبر OneSignal فعلياً. لا تحذف FCM قبل ذلك.
 * - خطوات التفعيل بعد الدمج: إنشاء تطبيق OneSignal (Android) وربط
 *   `google-services.json` وFCM Server Key فيه، ثم وضع App ID في
 *   `.env`، ثم إرسال إشعار تجريبي من لوحة OneSignal والتحقق من وصوله.
 */
object OneSignalManager {
    private const val TAG = "OneSignalManager"

    /** نطاق لطلب إذن الإشعارات (دالة suspend) دون جعل init دالة suspend. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** true فقط عندما يملك التطبيق معرّف OneSignal صالحاً. */
    val isConfigured: Boolean
        get() = BuildConfig.ONESIGNAL_APP_ID.isNotBlank()

    /** تهيئة OneSignal مرة واحدة عند إقلاع التطبيق. لا تفعل شيئاً إن لم يُضبط المعرّف. */
    fun init(context: Context) {
        if (!isConfigured) {
            Log.w(TAG, "OneSignal is not configured (ONESIGNAL_APP_ID is empty); FCM remains the push channel.")
            return
        }
        runCatching {
            OneSignal.initWithContext(context, BuildConfig.ONESIGNAL_APP_ID)
            // طلب إذن الإشعارات من النظام (Android 13+) عند أول إقلاع بعد التهيئة.
            scope.launch { OneSignal.Notifications.requestPermission(false) }
            Log.d(TAG, "OneSignal initialized")
        }.onFailure { Log.w(TAG, "OneSignal init failed: ${it.message}") }
    }

    /** ربط الجهاز بالمستخدم بعد تسجيل الدخول، ليصل الإشعار للمستخدم لا للجهاز فقط. */
    fun login(userId: String) {
        if (!isConfigured || userId.isBlank()) return
        runCatching { OneSignal.login(userId) }
            .onFailure { Log.w(TAG, "OneSignal login failed: ${it.message}") }
    }

    /** فك ربط المستخدم عند تسجيل الخروج. */
    fun logout() {
        if (!isConfigured) return
        runCatching { OneSignal.logout() }
            .onFailure { Log.w(TAG, "OneSignal logout failed: ${it.message}") }
    }
}
