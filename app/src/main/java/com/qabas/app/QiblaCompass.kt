package com.qabas.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * بوصلة قبلة حية (الدفعة 6 — الأولوية 1).
 *
 * تقرأ اتجاه الجهاز من حساس متجه الدوران (TYPE_ROTATION_VECTOR) وتحوّل
 * اتجاه القبلة الثابت — المحسوب في [PrayerCalculationHelper.calculateQibla]
 * من موقع المدينة / GPS المحفوظ في التطبيق فقط، دون طلب موقع جديد هنا —
 * إلى دوران للإبرة: (اتجاه القبلة − سمت الجهاز). فإذا وجّه المستخدم أعلى
 * الهاتف نحو القبلة استقامت الإبرة للأعلى.
 *
 * حساس TYPE_ORIENTATION المهمل لا يُستخدم أبداً. وعند غياب حساس متجه
 * الدوران في الجهاز تُعيد الحالة [QiblaCompassState.isSensorAvailable]
 * بقيمة false، وعلى الواجهة حينها عرض الاتجاه الثابت بالتسمية الصادقة
 * «اتجاه القبلة» لا «بوصلة»، بدل التظاهر بوجود بوصلة.
 * قراءة حساس الدوران لا تحتاج أي إذن تشغيل.
 */
data class QiblaCompassState(
    /** False عند غياب حساس متجه الدوران في الجهاز أصلاً. */
    val isSensorAvailable: Boolean,
    /** سمت الجهاز بالدرجات، 0 = الشمال، وnull قبل أول قراءة. */
    val azimuthDeg: Float?,
    /** True ما دامت دقة الحساس غير موثوقة/منخفضة: يُطلب من المستخدم المعايرة. */
    val isCalibrationNeeded: Boolean
)

/**
 * دوران إبرة القرص: حي (الاتجاه − السمت) عند توفر قراءة، والاتجاه الثابت
 * أثناء انتظار أول قراءة أو عند غياب الحساس.
 */
fun qiblaNeedleRotationDeg(qiblaBearingDeg: Float, azimuthDeg: Float?): Float =
    if (azimuthDeg == null) qiblaBearingDeg
    else ((qiblaBearingDeg - azimuthDeg) % 360f + 360f) % 360f

@Composable
fun rememberQiblaCompassState(): QiblaCompassState {
    val context = LocalContext.current
    var azimuthDeg by remember { mutableStateOf<Float?>(null) }
    var isCalibrationNeeded by remember { mutableStateOf(false) }
    val sensorAvailable = remember(context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
    }

    DisposableEffect(context, sensorAvailable) {
        if (!sensorAvailable) return@DisposableEffect onDispose {}
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (manager == null || sensor == null) return@DisposableEffect onDispose {}

        val listener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val orientationAngles = FloatArray(3)

            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                val heading = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                azimuthDeg = ((heading % 360f) + 360f) % 360f
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                isCalibrationNeeded = accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
                    accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW
            }
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager.unregisterListener(listener) }
    }

    return QiblaCompassState(
        isSensorAvailable = sensorAvailable,
        azimuthDeg = azimuthDeg,
        isCalibrationNeeded = isCalibrationNeeded
    )
}
