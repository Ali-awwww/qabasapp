package com.qabas.app

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

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
 *
 * تصحيح الانحراف المغناطيسي (الجولة 103): سمت حساس الدوران مقاس من
 * الشمال المغناطيسي، بينما اتجاه القبلة المحسوب في
 * [PrayerCalculationHelper.calculateQibla] مقاس من الشمال الحقيقي —
 * والفرق بينهما (الانحراف المغناطيسي) يبلغ نحو +4° في الرياض والقاهرة
 * ويصل إلى ±13° في أجزاء من أمريكا، فكانت الإبرة تنحرف بهذا القدر.
 * عند تمرير الإحداثيات تُصحَّح القراءة عبر [GeomagneticField] (نموذج
 * المجال المغناطيسي العالمي، بلا إذن وبلا شبكة) فيصير [QiblaCompassState.azimuthDeg]
 * سمتاً من الشمال الحقيقي؛ وبلا إحداثيات يبقى مغناطيسياً كما كان.
 *
 * تصحيح دوران الشاشة (الجولة ق47): مصفوفة الدوران تُعاد رسمها على
 * محاور الشاشة حسب دورانها الحالي، فالسمت سمتُ أعلى الشاشة لا الحافة
 * الفيزيائية العلوية — بلا هذا يخطئ المؤشر 90° في الوضع الأفقي.
 * ومهلة أول قراءة (5 ثوانٍ) تُسقط ادعاء «جارٍ قراءة البوصلة…» عند
 * حساس صامت، فتعرض الواجهة الاتجاه الثابت بتسمية صادقة.
 */
data class QiblaCompassState(
    /** False عند غياب حساس متجه الدوران في الجهاز أصلاً. */
    val isSensorAvailable: Boolean,
    /** سمت الجهاز بالدرجات، 0 = الشمال الحقيقي عند توفر الإحداثيات
     * (بعد تصحيح الانحراف المغناطيسي)، والشمال المغناطيسي عند غيابها،
     * وnull قبل أول قراءة. */
    val azimuthDeg: Float?,
    /** True ما دامت دقة الحساس غير موثوقة/منخفضة: يُطلب من المستخدم المعايرة. */
    val isCalibrationNeeded: Boolean,
    /**
     * True إذا مرّت 5 ثوانٍ على تسجيل المستمع بلا أي قراءة: الحساس موجود
     * لكنه صامت، فتعرض الواجهة الاتجاه الثابت بتسمية صادقة بدل
     * «جارٍ قراءة البوصلة…» إلى الأبد. يُحسب مع azimuthDeg == null،
     * فأول قراءة متأخرة تُسقطه تلقائياً.
     */
    val isSensorTimedOut: Boolean = false
)

/**
 * دوران إبرة القرص: حي (الاتجاه − السمت) عند توفر قراءة، والاتجاه الثابت
 * أثناء انتظار أول قراءة أو عند غياب الحساس.
 */
fun qiblaNeedleRotationDeg(qiblaBearingDeg: Float, azimuthDeg: Float?): Float =
    if (azimuthDeg == null) qiblaBearingDeg
    else ((qiblaBearingDeg - azimuthDeg) % 360f + 360f) % 360f

/**
 * دوران الشاشة الحالي (Surface.ROTATION_*)؛ يُقرأ عند كل حدث حساس لأن
 * المستخدم قد يدوّر الهاتف والتبويب مفتوح. بلا هذا التصحيح يكون السمت
 * سمتَ الحافة العلوية الفيزيائية للجهاز، فيخطئ المؤشر 90° في الوضع
 * الأفقي حيث «أعلى الشاشة» حافة جانبية فيزيائية.
 */
private fun displayRotation(context: Context): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display?.rotation ?: Surface.ROTATION_0
    } else {
        @Suppress("DEPRECATION")
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
    }
}

@Composable
fun rememberQiblaCompassState(
    latitude: Double? = null,
    longitude: Double? = null
): QiblaCompassState {
    val context = LocalContext.current
    var azimuthDeg by remember { mutableStateOf<Float?>(null) }
    var isCalibrationNeeded by remember { mutableStateOf(false) }
    var sensorTimedOut by remember { mutableStateOf(false) }
    val sensorAvailable = remember(context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
    }
    // مهلة أول قراءة: حساس صامت لا يبقي الواجهة على «جارٍ قراءة البوصلة…»
    // إلى الأبد؛ يُصفَّر العلم مع كل إعادة تسجيل فيُقاس من جديد.
    LaunchedEffect(sensorAvailable) {
        sensorTimedOut = false
        if (!sensorAvailable) return@LaunchedEffect
        delay(5_000)
        if (azimuthDeg == null) sensorTimedOut = true
    }
    // الانحراف المغناطيسي عند الموقع (درجات، موجب شرقاً)؛ صفر بلا إحداثيات.
    // حساب خالص بلا نقطة تعليق، فلا يبتلع إلغاءً (معيار الجولات 87/99).
    val declinationDeg = remember(latitude, longitude) {
        if (latitude == null || longitude == null) 0f
        else runCatching {
            GeomagneticField(
                latitude.toFloat(), longitude.toFloat(), 0f,
                System.currentTimeMillis()
            ).declination
        }.getOrDefault(0f)
    }

    DisposableEffect(context, sensorAvailable, declinationDeg) {
        if (!sensorAvailable) return@DisposableEffect onDispose {}
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (manager == null || sensor == null) return@DisposableEffect onDispose {}

        val listener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val remappedMatrix = FloatArray(9)
            private val orientationAngles = FloatArray(3)

            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                // إعادة رسم المصفوفة على محاور الشاشة حسب دورانها: بلا هذا
                // يقيس getOrientation سمتَ الحافة الفيزيائية العلوية،
                // فيخطئ المؤشر 90° في الوضع الأفقي. الجدول القياسي
                // (ROTATION_0: X/Y، و90: Y/‎−X، و180: ‎−X/‎−Y، و270: ‎−Y/X).
                val (axisX, axisY) = when (displayRotation(context)) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                val matrix =
                    if (SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedMatrix)) remappedMatrix
                    else rotationMatrix
                SensorManager.getOrientation(matrix, orientationAngles)
                val heading = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                // السمت المغناطيسي + الانحراف = سمت من الشمال الحقيقي،
                // ليوافق اتجاه القبلة المحسوب من الشمال الحقيقي.
                azimuthDeg = ((heading + declinationDeg) % 360f + 360f) % 360f
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
        isCalibrationNeeded = isCalibrationNeeded,
        isSensorTimedOut = sensorTimedOut && azimuthDeg == null
    )
}
