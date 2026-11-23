package com.qabas.app

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs

/**
 * قياس قدرة الجهاز الحقيقية واقتراح النموذج المحلي (12.4).
 *
 * قواعد صدق ملزمة هنا:
 * - ذاكرة الجهاز الكلية تُقرأ من [ActivityManager.MemoryInfo] — لا من
 *   `Runtime.getRuntime().maxMemory()` فهي heap التطبيق لا ذاكرة الجهاز
 *   (كان هذا الخطأ في بوابة الذاكرة في AgentMission؛ أُصلح هناك).
 * - التخزين الحر والكلي من [StatFs] على filesDir، وعدد النوى من
 *   `Runtime.availableProcessors()`.
 * - لا تُعرض أرقام قياس لم تُقس: كل رقم في البطاقة يأتي من هذا القياس.
 * - الاقتراح قاعدة معلنة: أكبر نموذج (بمتطلبات RAM) يعمل على الجهاز الآن.
 *   «يعمل» = متطلبات RAM ≤ الذاكرة المتاحة الآن + حجم الملف + 32MB هامش
 *   ≤ التخزين الحر. إن لم يعمل أي نموذج فلا اقتراح ولا وهم.
 */
data class DeviceCapability(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val freeStorageBytes: Long,
    val totalStorageBytes: Long,
    val cpuCores: Int,
)

fun formatDeviceBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val gb = bytes.toDouble() / (1024 * 1024 * 1024)
    if (gb >= 1.0) return "%.1f GB".format(gb)
    val mb = bytes.toDouble() / (1024 * 1024)
    if (mb >= 1.0) return "%.0f MB".format(mb)
    val kb = bytes.toDouble() / 1024
    return "%.0f KB".format(kb)
}

object VibeDeviceCapability {

    /** هامش أمان فوق حجم ملف النموذج عند فحص التخزين (مطابق لهامش التنزيل). */
    const val STORAGE_RESERVE_BYTES: Long = 32L * 1024 * 1024

    /** قياس حي واحد: RAM كلية/متاحة + تخزين حر/كلي + نوى. */
    fun measure(context: Context): DeviceCapability {
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val statFs = StatFs(context.filesDir.path)
        return DeviceCapability(
            totalRamBytes = memInfo.totalMem,
            availableRamBytes = memInfo.availMem,
            freeStorageBytes = statFs.availableBytes,
            totalStorageBytes = statFs.totalBytes,
            cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        )
    }

    /** هل يعمل النموذج على هذا الجهاز الآن (ذاكرة متاحة + تخزين كافٍ). */
    fun fitsModel(capability: DeviceCapability, model: OnDeviceModelInfo): Boolean {
        val ramOk =
            OnDeviceModelManager.ramRequirementBytes(model) <= capability.availableRamBytes
        val storageOk =
            model.sizeBytes + STORAGE_RESERVE_BYTES <= capability.freeStorageBytes
        return ramOk && storageOk
    }

    /** سبب صادق لعدم عمل النموذج، أو null إن كان يعمل. */
    fun fitReason(capability: DeviceCapability, model: OnDeviceModelInfo): String? {
        val needRam = OnDeviceModelManager.ramRequirementBytes(model)
        if (needRam > capability.availableRamBytes) {
            return "يحتاج ${formatDeviceBytes(needRam)} ذاكرة والمتاح الآن " +
                "${formatDeviceBytes(capability.availableRamBytes)} " +
                "(من أصل ${formatDeviceBytes(capability.totalRamBytes)})."
        }
        if (model.sizeBytes + STORAGE_RESERVE_BYTES > capability.freeStorageBytes) {
            return "يحتاج ${formatDeviceBytes(model.sizeBytes)} تخزيناً والحر الآن " +
                "${formatDeviceBytes(capability.freeStorageBytes)}."
        }
        return null
    }

    /**
     * النموذج المقترح: الأكبر (الأقدر) بين النماذج التي تعمل على الجهاز الآن،
     * مقاساً برقم ramRequirement الثابت. null إن لم يعمل أي نموذج.
     */
    fun recommendModel(capability: DeviceCapability): OnDeviceModelInfo? =
        OnDeviceModelManager.availableModels
            .filter { fitsModel(capability, it) }
            .maxByOrNull { OnDeviceModelManager.ramRequirementBytes(it) }
}
