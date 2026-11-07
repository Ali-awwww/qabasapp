package com.qabas.app

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.GoldPrimary

/**
 * Central preferences and shared configuration for Qabas video playback surfaces.
 * Values are persisted in qabas_prefs and intentionally remain device-local.
 */
object UnifiedVideoPlayerSettings {
    private const val PREFS = "qabas_prefs"
    private const val SPEED_KEY = "unified_video_speed"
    private const val RESIZE_KEY = "unified_video_resize_mode"
    private const val CONTROLS_KEY = "unified_video_controls_enabled"

    fun speed(context: Context): Float =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(SPEED_KEY, 1f).coerceIn(0.5f, 2f)

    fun resizeMode(context: Context): Int =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(RESIZE_KEY, AspectRatioFrameLayout.RESIZE_MODE_FIT)
            .takeIf { it in listOf(AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_ZOOM, AspectRatioFrameLayout.RESIZE_MODE_FILL) }
            ?: AspectRatioFrameLayout.RESIZE_MODE_FIT

    fun controlsEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(CONTROLS_KEY, true)

    fun configure(view: PlayerView, context: Context) {
        view.resizeMode = resizeMode(context)
        view.useController = controlsEnabled(context)
        view.controllerShowTimeoutMs = 3_000
        view.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
    }

    fun saveSpeed(context: Context, speed: Float) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putFloat(SPEED_KEY, speed.coerceIn(0.5f, 2f)).apply()
    }

    fun saveResizeMode(context: Context, mode: Int) {
        if (mode !in listOf(AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_ZOOM, AspectRatioFrameLayout.RESIZE_MODE_FILL)) return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(RESIZE_KEY, mode).apply()
    }

    fun saveControlsEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(CONTROLS_KEY, enabled).apply()
    }
}

@Composable
fun UnifiedVideoPlayerSettingsScreen(onBack: () -> Unit, bottomBar: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }
    var speed by remember { mutableFloatStateOf(prefs.getFloat("unified_video_speed", 1f).coerceIn(0.5f, 2f)) }
    var resize by remember { mutableStateOf(prefs.getInt("unified_video_resize_mode", AspectRatioFrameLayout.RESIZE_MODE_FIT)) }
    var controls by remember { mutableStateOf(prefs.getBoolean("unified_video_controls_enabled", true)) }

    SubScreenScaffold(
        title = "إعدادات مشغل الفيديو",
        icon = Icons.Default.PlayCircle,
        onBack = onBack,
        bottomBar = bottomBar
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("سرعة التشغيل: ${"%.2f".format(speed)}×", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Slider(
                    value = speed,
                    onValueChange = { speed = it },
                    valueRange = 0.5f..2f,
                    steps = 5,
                    onValueChangeFinished = { UnifiedVideoPlayerSettings.saveSpeed(context, speed) }
                )
                Text("سرعة افتراضية للمشغلات الداعمة؛ يمكن للمونتاج والريلز فرض سرعة المقطع.", fontFamily = CairoFont, fontSize = 12.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("طريقة ملاءمة الصورة", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(
                        AspectRatioFrameLayout.RESIZE_MODE_FIT to "احتواء",
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "ملء/قص",
                        AspectRatioFrameLayout.RESIZE_MODE_FILL to "تمديد"
                    ).forEach { (mode, label) ->
                        androidx.compose.material3.FilterChip(
                            selected = resize == mode,
                            onClick = { resize = mode; UnifiedVideoPlayerSettings.saveResizeMode(context, mode) },
                            label = { Text(label, fontFamily = CairoFont) }
                        )
                    }
                }
                Text("الاحتواء يحافظ على كامل الإطار، والملء قد يقص الأطراف، والتمديد قد يغير النسب.", fontFamily = CairoFont, fontSize = 12.sp)
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("إظهار أزرار المشغل", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("إظهار عناصر التحكم القياسية للمشغلات التي تستخدم PlayerView.", fontFamily = CairoFont, fontSize = 12.sp)
                }
                Switch(checked = controls, onCheckedChange = { controls = it; UnifiedVideoPlayerSettings.saveControlsEnabled(context, it) })
            }
        }
    }
}
