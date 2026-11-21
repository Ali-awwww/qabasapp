@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.qabas.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.qabas.app.ui.components.AppleGroupedSection
import com.qabas.app.ui.components.AppleInsetDivider
import com.qabas.app.ui.components.AppleSegmentedControl
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.QabasTheme

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
    val theme = QabasTheme.current
    val prefs = remember(context) { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }
    var speed by remember { mutableFloatStateOf(prefs.getFloat("unified_video_speed", 1f).coerceIn(0.5f, 2f)) }
    var resize by remember { mutableIntStateOf(prefs.getInt("unified_video_resize_mode", AspectRatioFrameLayout.RESIZE_MODE_FIT)) }
    var controls by remember { mutableStateOf(prefs.getBoolean("unified_video_controls_enabled", true)) }

    SubScreenScaffold(
        title = "إعدادات مشغل الفيديو",
        icon = Icons.Default.PlayCircle,
        onBack = onBack,
        bottomBar = bottomBar
    ) {
        AppleGroupedSection(
            header = "سرعة التشغيل",
            footer = "سرعة افتراضية للمشغلات الداعمة؛ يمكن للمونتاج والريلز فرض سرعة المقطع."
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("السرعة الافتراضية", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${"%.2f".format(speed)}×", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Slider(
                    value = speed,
                    onValueChange = { speed = it },
                    valueRange = 0.5f..2f,
                    steps = 5,
                    onValueChangeFinished = { UnifiedVideoPlayerSettings.saveSpeed(context, speed) },
                    colors = SliderDefaults.colors(
                        thumbColor = theme.primary,
                        activeTrackColor = theme.primary,
                        inactiveTrackColor = theme.secondarySurface
                    )
                )
            }
        }

        AppleGroupedSection(
            header = "ملاءمة الإطار والتحكم",
            footer = "الاحتواء يحافظ على كامل الإطار، والملء يقص الأطراف، والتمديد يملأ الشاشة."
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppleSegmentedControl(
                    items = listOf(
                        AspectRatioFrameLayout.RESIZE_MODE_FIT to "احتواء",
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "ملء/قص",
                        AspectRatioFrameLayout.RESIZE_MODE_FILL to "تمديد"
                    ),
                    selectedItem = resize,
                    onSelect = { mode ->
                        resize = mode
                        UnifiedVideoPlayerSettings.saveResizeMode(context, mode)
                    }
                )
            }
            AppleInsetDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("إظهار أزرار المشغل", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("إظهار عناصر التحكم القياسية للمشغلات التي تستخدم PlayerView.", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.5.sp)
                }
                Switch(
                    checked = controls,
                    onCheckedChange = {
                        controls = it
                        UnifiedVideoPlayerSettings.saveControlsEnabled(context, it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = theme.onPrimary,
                        checkedTrackColor = theme.success
                    )
                )
            }
        }
    }
}
