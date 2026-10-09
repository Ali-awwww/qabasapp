package com.qabas.app

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast

/**
 * مساعدات تجربة المستخدم — اهتزاز خفيف، رسائل واضحة، تغذية راجعة فورية.
 */
object QabasUx {
    /** اهتزاز نجاح خفيف عند اكتمال مهمة (تصدير، حفظ، امتصاص أسلوب) */
    fun successHaptic(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(35)
            }
        } catch (_: Exception) { }
    }

    /** اهتزاز تحذير قصير (إلغاء، خطأ غير فادح) */
    fun warnHaptic(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(18)
            }
        } catch (_: Exception) { }
    }

    fun toast(context: Context, msg: String, long: Boolean = false) {
        Toast.makeText(
            context,
            Translator.tr(msg),
            if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        ).show()
    }
}
