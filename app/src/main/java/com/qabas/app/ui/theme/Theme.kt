package com.qabas.app.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.qabas.app.AppSoundManager
import com.qabas.app.AppSoundPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Theme Mode Manager for Global State (3 Presets Selected: Spatial Cyber HUD, Islamic Dark Luxury, Cosmic Violet)
enum class AppThemePreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val primaryColor: Color,
    val accentColor: Color,
    val backgroundColor: Color,
    val cardSurfaceColor: Color,
    val soundPreset: AppSoundPreset,
    val isDark: Boolean = true,
    val isNeon: Boolean = false
) {
    CYBER_HUD_BLUE(
        id = "cyber_hud_blue",
        title = "الأزرق والأسود السيبراني (Spatial Cyber HUD)",
        subtitle = "النمط المستقبلي: سواد فاحم ميتاليك وأزرق نيوني متوهج وهولوجرام كوانتي",
        primaryColor = Color(0xFF00E5FF),
        accentColor = Color(0xFF0084FF),
        backgroundColor = Color(0xFF030712),
        cardSurfaceColor = Color(0xFF0B172E),
        soundPreset = AppSoundPreset.CYBER_TECH,
        isDark = true
    ),
    GOLD_ISLAMIC(
        id = "gold_islamic",
        title = "الذهبي الملكي (Islamic / Dark Luxury)",
        subtitle = "الهوية الكلاسيكية الفاخرة: ذهبي إسلامي راقٍ مع كحلي وسليت عميق",
        primaryColor = Color(0xFFE8C547),
        accentColor = Color(0xFFF5D76E),
        backgroundColor = Color(0xFF0B0F19),
        cardSurfaceColor = Color(0xFF151B2B),
        soundPreset = AppSoundPreset.SPIRITUAL_ECHO,
        isDark = true
    ),
    ROYAL_AMETHYST(
        id = "royal_amethyst",
        title = "البنفسج الكوني (Cosmic Violet)",
        subtitle = "نمط استوديو الذكاء: بنفسجي عصبي وتوهج كوانتومي جذاب",
        primaryColor = Color(0xFFA855F7),
        accentColor = Color(0xFFC084FC),
        backgroundColor = Color(0xFF0A0618),
        cardSurfaceColor = Color(0xFF160E2E),
        soundPreset = AppSoundPreset.COSMIC_SYNTH,
        isDark = true
    ),
    EMERALD_OASIS(
        id = "emerald_oasis",
        title = "الزمرد النبوي (Emerald Oasis)",
        subtitle = "أخضر زمردي هادئ مستوحى من الروضة الشريفة مع درجات النقاء الإيماني",
        primaryColor = Color(0xFF10B981),
        accentColor = Color(0xFF34D399),
        backgroundColor = Color(0xFF04140D),
        cardSurfaceColor = Color(0xFF0A2218),
        soundPreset = AppSoundPreset.SOFT_MINIMAL,
        isDark = true
    );

    val soundEmoji: String get() = soundPreset.iconEmoji
    val soundTitle: String get() = soundPreset.title
}

object ThemeManager {
    private val _currentPreset = MutableStateFlow(AppThemePreset.CYBER_HUD_BLUE)
    val currentPreset: StateFlow<AppThemePreset> = _currentPreset.asStateFlow()

    // Compose snapshot states for instant reactive recomposition across all Composables
    var activePreset by mutableStateOf(AppThemePreset.CYBER_HUD_BLUE)
        private set

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    var isDarkState by mutableStateOf(true)
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val savedPresetId = prefs.getString("app_theme_preset", null)
        val preset = if (savedPresetId != null) {
            AppThemePreset.entries.find { it.id == savedPresetId } ?: AppThemePreset.CYBER_HUD_BLUE
        } else {
            AppThemePreset.CYBER_HUD_BLUE
        }
        _currentPreset.value = preset
        activePreset = preset
        _isDarkTheme.value = true
        isDarkState = true
        AppSoundManager.setPreset(context, preset.soundPreset)
    }

    fun applyPreset(context: Context, preset: AppThemePreset, playSfx: Boolean = true) {
        _currentPreset.value = preset
        activePreset = preset
        _isDarkTheme.value = true
        isDarkState = true
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("app_theme_preset", preset.id)
            .putBoolean("app_dark_theme", true)
            .apply()

        // ربط النمط البصري بالمؤثرات الصوتية فورياً
        AppSoundManager.setPreset(context, preset.soundPreset)
        if (playSfx) {
            AppSoundManager.playActivate()
        }
    }

    fun toggleTheme(context: Context) {
        val all = AppThemePreset.entries
        val nextIndex = (all.indexOf(activePreset) + 1) % all.size
        applyPreset(context, all[nextIndex])
    }

    fun setDarkTheme(context: Context, isDark: Boolean) {
        // All active presets are dark/cyber luxury
        applyPreset(context, activePreset)
    }
}

private fun getDarkColorScheme(): ColorScheme {
    val preset = ThemeManager.currentPreset.value
    return darkColorScheme(
        primary = preset.primaryColor,
        secondary = preset.accentColor,
        tertiary = preset.primaryColor.copy(alpha = 0.8f),
        background = preset.backgroundColor,
        surface = preset.cardSurfaceColor,
        onPrimary = Color.White,
        onSecondary = preset.backgroundColor,
        onTertiary = preset.backgroundColor,
        onBackground = if (preset.isDark) Color(0xFFF8FAFC) else Color(0xFF0B0F19),
        onSurface = if (preset.isDark) Color(0xFFF8FAFC) else Color(0xFF0B0F19)
    )
}

private fun getLightColorScheme(): ColorScheme {
    val preset = ThemeManager.currentPreset.value
    return lightColorScheme(
        primary = preset.primaryColor,
        secondary = preset.accentColor,
        tertiary = preset.primaryColor.copy(alpha = 0.8f),
        background = preset.backgroundColor,
        surface = preset.cardSurfaceColor,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onTertiary = preset.backgroundColor,
        onBackground = LightTextPrimary,
        onSurface = LightTextPrimary
    )
}

private val QabasShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun animateColorScheme(targetColorScheme: ColorScheme): ColorScheme {
    val animationSpec = tween<Color>(durationMillis = 400)

    val primary by animateColorAsState(targetColorScheme.primary, animationSpec, label = "primary")
    val secondary by animateColorAsState(targetColorScheme.secondary, animationSpec, label = "secondary")
    val tertiary by animateColorAsState(targetColorScheme.tertiary, animationSpec, label = "tertiary")
    val background by animateColorAsState(targetColorScheme.background, animationSpec, label = "background")
    val surface by animateColorAsState(targetColorScheme.surface, animationSpec, label = "surface")
    val onPrimary by animateColorAsState(targetColorScheme.onPrimary, animationSpec, label = "onPrimary")
    val onSecondary by animateColorAsState(targetColorScheme.onSecondary, animationSpec, label = "onSecondary")
    val onTertiary by animateColorAsState(targetColorScheme.onTertiary, animationSpec, label = "onTertiary")
    val onBackground by animateColorAsState(targetColorScheme.onBackground, animationSpec, label = "onBackground")
    val onSurface by animateColorAsState(targetColorScheme.onSurface, animationSpec, label = "onSurface")

    return targetColorScheme.copy(
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        background = background,
        surface = surface,
        onPrimary = onPrimary,
        onSecondary = onSecondary,
        onTertiary = onTertiary,
        onBackground = onBackground,
        onSurface = onSurface
    )
}

@Immutable
data class QabasDesignTheme(
    val preset: AppThemePreset,
    val primary: Color,
    val accent: Color,
    val background: Color,
    val cardSurface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val isDark: Boolean = true,
    val isCyberHud: Boolean = (preset == AppThemePreset.CYBER_HUD_BLUE),
    val isNeon: Boolean = false
)

val LocalQabasTheme = staticCompositionLocalOf<QabasDesignTheme> {
    val defaultPreset = AppThemePreset.CYBER_HUD_BLUE
    QabasDesignTheme(
        preset = defaultPreset,
        primary = defaultPreset.primaryColor,
        accent = defaultPreset.accentColor,
        background = defaultPreset.backgroundColor,
        cardSurface = defaultPreset.cardSurfaceColor,
        textPrimary = Color(0xFFF8FAFC),
        textSecondary = Color(0xFF94A3B8),
        isDark = true,
        isCyberHud = true
    )
}

val LocalThemePreset = staticCompositionLocalOf<AppThemePreset> {
    AppThemePreset.CYBER_HUD_BLUE
}

object QabasTheme {
    val current: QabasDesignTheme
        @Composable
        @ReadOnlyComposable
        get() = LocalQabasTheme.current

    val preset: AppThemePreset
        @Composable
        @ReadOnlyComposable
        get() = LocalThemePreset.current

    val colors: QabasDesignTheme
        @Composable
        @ReadOnlyComposable
        get() = LocalQabasTheme.current
}

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    val preset = ThemeManager.currentPreset.collectAsState().value
    val darkTheme = preset.isDark

    val designTheme = remember(preset, darkTheme) {
        QabasDesignTheme(
            preset = preset,
            primary = preset.primaryColor,
            accent = preset.accentColor,
            background = preset.backgroundColor,
            cardSurface = preset.cardSurfaceColor,
            textPrimary = if (darkTheme) Color(0xFFF8FAFC) else Color(0xFF0B0F19),
            textSecondary = if (darkTheme) Color(0xFF94A3B8) else Color(0xFF64748B),
            isDark = darkTheme,
            isCyberHud = (preset == AppThemePreset.CYBER_HUD_BLUE),
            isNeon = preset.isNeon
        )
    }

    val dynamicColorScheme = if (darkTheme) {
        darkColorScheme(
            primary = preset.primaryColor,
            secondary = preset.accentColor,
            tertiary = preset.primaryColor.copy(alpha = 0.8f),
            background = preset.backgroundColor,
            surface = preset.cardSurfaceColor,
            onPrimary = Color.White,
            onSecondary = preset.backgroundColor,
            onTertiary = preset.backgroundColor,
            onBackground = designTheme.textPrimary,
            onSurface = designTheme.textPrimary
        )
    } else {
        lightColorScheme(
            primary = preset.primaryColor,
            secondary = preset.accentColor,
            tertiary = preset.primaryColor.copy(alpha = 0.8f),
            background = preset.backgroundColor,
            surface = preset.cardSurfaceColor,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = DeepSlate,
            onBackground = LightTextPrimary,
            onSurface = LightTextPrimary
        )
    }

    val animatedColorScheme = animateColorScheme(dynamicColorScheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalQabasTheme provides designTheme,
        LocalThemePreset provides preset
    ) {
        MaterialTheme(
            colorScheme = animatedColorScheme,
            typography = Typography,
            shapes = QabasShapes,
            content = content
        )
    }
}

@Composable
fun isDarkAppTheme(): Boolean {
    return LocalQabasTheme.current.isDark
}

@Composable
fun qabasPrimary(): Color {
    return LocalQabasTheme.current.primary
}

@Composable
fun qabasAccent(): Color {
    return LocalQabasTheme.current.accent
}

@Composable
fun qabasBackground(): Color {
    return LocalQabasTheme.current.background
}

@Composable
fun qabasCardSurface(): Color {
    return LocalQabasTheme.current.cardSurface
}

@Composable
fun qabasSurface(): Color {
    return LocalQabasTheme.current.cardSurface
}

@Composable
fun qabasTextPrimary(): Color {
    return LocalQabasTheme.current.textPrimary
}

@Composable
fun qabasTextSecondary(): Color {
    return LocalQabasTheme.current.textSecondary
}
