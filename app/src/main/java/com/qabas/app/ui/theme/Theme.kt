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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.qabas.app.AppSoundManager
import com.qabas.app.AppSoundPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode(
    val id: String,
    val title: String,
    val subtitle: String
) {
    SYSTEM(
        id = "system",
        title = "تلقائي (النظام)",
        subtitle = "يتبع مظهر الجهاز بين الوضع الفاتح والداكن"
    ),
    LIGHT(
        id = "light",
        title = "الوضع الفاتح",
        subtitle = "تصميم Apple نظيف ومشرق للقراءة النهارية"
    ),
    DARK(
        id = "dark",
        title = "الوضع الداكن",
        subtitle = "خلفية داكنة مريحة للعين في الإضاءة المنخفضة"
    );

    companion object {
        fun fromId(id: String?): AppThemeMode =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: SYSTEM
    }
}

// Theme Accent Presets — All adapt seamlessly to both Light and Dark Apple HIG surfaces
enum class AppThemePreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val darkPrimaryColor: Color,
    val darkAccentColor: Color,
    val lightPrimaryColor: Color,
    val lightAccentColor: Color,
    val soundPreset: AppSoundPreset,
    val isNeon: Boolean = false
) {
    CYBER_HUD_BLUE(
        id = "cyber_hud_blue",
        title = "أزرق Apple النقي",
        subtitle = "اللون الافتراضي النظيف وفق معايير Apple HIG",
        darkPrimaryColor = AppleDarkBlue,
        darkAccentColor = Color(0xFF5AC8FA),
        lightPrimaryColor = AppleLightBlue,
        lightAccentColor = Color(0xFF0A84FF),
        soundPreset = AppSoundPreset.SOFT_MINIMAL
    ),
    GOLD_ISLAMIC(
        id = "gold_islamic",
        title = "الذهبي التراثي",
        subtitle = "درجات ذهبية متزنة تناسب القراءة والمصحف",
        darkPrimaryColor = AppleDarkGold,
        darkAccentColor = Color(0xFFF5D76E),
        lightPrimaryColor = AppleLightGold,
        lightAccentColor = Color(0xFFB8860B),
        soundPreset = AppSoundPreset.SPIRITUAL_ECHO
    ),
    ROYAL_AMETHYST(
        id = "royal_amethyst",
        title = "البنفسجي الراقي",
        subtitle = "درجات نيلية هادئة لأدوات الإنتاج والذكاء",
        darkPrimaryColor = AppleDarkIndigo,
        darkAccentColor = Color(0xFFA5A3FF),
        lightPrimaryColor = AppleLightIndigo,
        lightAccentColor = Color(0xFF7D7AFF),
        soundPreset = AppSoundPreset.COSMIC_SYNTH
    ),
    EMERALD_OASIS(
        id = "emerald_oasis",
        title = "الزمرد النبوي",
        subtitle = "أخضر طبيعي مريح مستوحى من السكينة الإيمانية",
        darkPrimaryColor = AppleDarkGreen,
        darkAccentColor = Color(0xFF63E686),
        lightPrimaryColor = AppleLightGreen,
        lightAccentColor = Color(0xFF28A745),
        soundPreset = AppSoundPreset.SOFT_MINIMAL
    );

    val primaryColor: Color
        get() = if (ThemeManager.isDarkState) darkPrimaryColor else lightPrimaryColor

    val accentColor: Color
        get() = if (ThemeManager.isDarkState) darkAccentColor else lightAccentColor

    val backgroundColor: Color
        get() = if (ThemeManager.isDarkState) AppleDarkBackground else AppleLightBackground

    val cardSurfaceColor: Color
        get() = if (ThemeManager.isDarkState) AppleDarkSurface else AppleLightSurface

    val isDark: Boolean
        get() = ThemeManager.isDarkState

    val soundEmoji: String get() = soundPreset.iconEmoji
    val soundTitle: String get() = soundPreset.title
}

object ThemeManager {
    private val _currentPreset = MutableStateFlow(AppThemePreset.CYBER_HUD_BLUE)
    val currentPreset: StateFlow<AppThemePreset> = _currentPreset.asStateFlow()

    var activePreset by mutableStateOf(AppThemePreset.CYBER_HUD_BLUE)
        private set

    private val _themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    var activeThemeMode by mutableStateOf(AppThemeMode.SYSTEM)
        private set

    private val _isDarkTheme = MutableStateFlow(false)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    var isDarkState by mutableStateOf(false)
        private set

    fun init(context: Context) {
        FontManager.init(context)
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val savedPresetId = prefs.getString("app_theme_preset", null)
        val preset = if (savedPresetId != null) {
            AppThemePreset.entries.find { it.id == savedPresetId } ?: AppThemePreset.CYBER_HUD_BLUE
        } else {
            AppThemePreset.CYBER_HUD_BLUE
        }

        val savedModeId = prefs.getString("app_theme_mode", null)
        val mode = AppThemeMode.fromId(savedModeId)

        _currentPreset.value = preset
        activePreset = preset
        _themeMode.value = mode
        activeThemeMode = mode

        val initialDark = when (mode) {
            AppThemeMode.DARK -> true
            AppThemeMode.LIGHT -> false
            AppThemeMode.SYSTEM -> {
                val uiMask = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                uiMask == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
        _isDarkTheme.value = initialDark
        isDarkState = initialDark
        AppSoundManager.setPreset(context, preset.soundPreset)
    }

    fun applyPreset(context: Context, preset: AppThemePreset, playSfx: Boolean = true) {
        _currentPreset.value = preset
        activePreset = preset
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("app_theme_preset", preset.id)
            .apply()

        AppSoundManager.setPreset(context, preset.soundPreset)
        if (playSfx) {
            AppSoundManager.playActivate()
        }
    }

    fun setThemeMode(context: Context, mode: AppThemeMode, playSfx: Boolean = true) {
        _themeMode.value = mode
        activeThemeMode = mode
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("app_theme_mode", mode.id)
            .apply()

        if (mode != AppThemeMode.SYSTEM) {
            val resolved = (mode == AppThemeMode.DARK)
            _isDarkTheme.value = resolved
            isDarkState = resolved
            prefs.edit().putBoolean("app_dark_theme", resolved).apply()
        }
        if (playSfx) {
            AppSoundManager.playClick()
        }
    }

    fun syncResolvedDark(isDark: Boolean) {
        if (isDarkState != isDark) {
            isDarkState = isDark
        }
        if (_isDarkTheme.value != isDark) {
            _isDarkTheme.value = isDark
        }
    }

    fun toggleTheme(context: Context) {
        val nextMode = if (isDarkState) AppThemeMode.LIGHT else AppThemeMode.DARK
        setThemeMode(context, nextMode)
    }

    fun setDarkTheme(context: Context, isDark: Boolean) {
        setThemeMode(context, if (isDark) AppThemeMode.DARK else AppThemeMode.LIGHT)
    }
}

private val QabasShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun animateColorScheme(targetColorScheme: ColorScheme): ColorScheme {
    val animationSpec = tween<Color>(durationMillis = 280)

    val primary by animateColorAsState(targetColorScheme.primary, animationSpec, label = "primary")
    val secondary by animateColorAsState(targetColorScheme.secondary, animationSpec, label = "secondary")
    val tertiary by animateColorAsState(targetColorScheme.tertiary, animationSpec, label = "tertiary")
    val background by animateColorAsState(targetColorScheme.background, animationSpec, label = "background")
    val surface by animateColorAsState(targetColorScheme.surface, animationSpec, label = "surface")
    val surfaceVariant by animateColorAsState(targetColorScheme.surfaceVariant, animationSpec, label = "surfaceVariant")
    val onPrimary by animateColorAsState(targetColorScheme.onPrimary, animationSpec, label = "onPrimary")
    val onSecondary by animateColorAsState(targetColorScheme.onSecondary, animationSpec, label = "onSecondary")
    val onTertiary by animateColorAsState(targetColorScheme.onTertiary, animationSpec, label = "onTertiary")
    val onBackground by animateColorAsState(targetColorScheme.onBackground, animationSpec, label = "onBackground")
    val onSurface by animateColorAsState(targetColorScheme.onSurface, animationSpec, label = "onSurface")
    val onSurfaceVariant by animateColorAsState(targetColorScheme.onSurfaceVariant, animationSpec, label = "onSurfaceVariant")
    val outline by animateColorAsState(targetColorScheme.outline, animationSpec, label = "outline")
    val outlineVariant by animateColorAsState(targetColorScheme.outlineVariant, animationSpec, label = "outlineVariant")

    return targetColorScheme.copy(
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        background = background,
        surface = surface,
        surfaceVariant = surfaceVariant,
        onPrimary = onPrimary,
        onSecondary = onSecondary,
        onTertiary = onTertiary,
        onBackground = onBackground,
        onSurface = onSurface,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        outlineVariant = outlineVariant
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
    val isDark: Boolean = false,
    val isCyberHud: Boolean = false,
    val isNeon: Boolean = false,
    val secondarySurface: Color = AppleLightSecondarySurface,
    val separator: Color = AppleLightSeparator,
    val onPrimary: Color = Color.White,
    val success: Color = AppleLightGreen,
    val warning: Color = AppleLightOrange,
    val destructive: Color = AppleLightRed
)

val LocalQabasTheme = staticCompositionLocalOf<QabasDesignTheme> {
    val defaultPreset = AppThemePreset.CYBER_HUD_BLUE
    QabasDesignTheme(
        preset = defaultPreset,
        primary = AppleLightBlue,
        accent = Color(0xFF0A84FF),
        background = AppleLightBackground,
        cardSurface = AppleLightSurface,
        textPrimary = AppleLightLabel,
        textSecondary = AppleLightSecondaryLabel,
        isDark = false,
        isCyberHud = false,
        isNeon = false
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
    highContrastMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val preset = ThemeManager.currentPreset.collectAsState().value
    val themeMode = ThemeManager.themeMode.collectAsState().value
    val systemInDark = isSystemInDarkTheme()

    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> systemInDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    // Keep global state synchronized for legacy top-level Color getters & non-Compose readers
    ThemeManager.syncResolvedDark(darkTheme)

    val designTheme = remember(preset, darkTheme, highContrastMode) {
        val primary = if (highContrastMode) {
            if (darkTheme) Color.White else Color.Black
        } else if (darkTheme) preset.darkPrimaryColor else preset.lightPrimaryColor

        val accent = if (highContrastMode) {
            if (darkTheme) Color.White else Color.Black
        } else if (darkTheme) preset.darkAccentColor else preset.lightAccentColor

        val bg = when {
            highContrastMode && darkTheme -> Color.Black
            highContrastMode && !darkTheme -> Color.White
            darkTheme -> AppleDarkBackground
            else -> AppleLightBackground
        }

        val cardBg = when {
            highContrastMode && darkTheme -> Color(0xFF141416)
            highContrastMode && !darkTheme -> Color(0xFFF5F5F7)
            darkTheme -> AppleDarkSurface
            else -> AppleLightSurface
        }

        val secBg = if (darkTheme) AppleDarkSecondarySurface else AppleLightSecondarySurface
        val sep = if (darkTheme) AppleDarkSeparator else AppleLightSeparator

        QabasDesignTheme(
            preset = preset,
            primary = primary,
            accent = accent,
            background = bg,
            cardSurface = cardBg,
            textPrimary = if (darkTheme) AppleDarkLabel else AppleLightLabel,
            textSecondary = if (darkTheme) {
                if (highContrastMode) Color.White.copy(alpha = 0.88f) else AppleDarkSecondaryLabel
            } else {
                if (highContrastMode) Color.Black.copy(alpha = 0.85f) else AppleLightSecondaryLabel
            },
            isDark = darkTheme,
            isCyberHud = false,
            isNeon = false,
            secondarySurface = secBg,
            separator = sep,
            onPrimary = if (highContrastMode && darkTheme) Color.Black else Color.White,
            success = if (darkTheme) AppleDarkGreen else AppleLightGreen,
            warning = if (darkTheme) AppleDarkOrange else AppleLightOrange,
            destructive = if (darkTheme) AppleDarkRed else AppleLightRed
        )
    }

    val dynamicColorScheme = if (darkTheme) {
        darkColorScheme(
            primary = designTheme.primary,
            secondary = designTheme.accent,
            tertiary = designTheme.success,
            background = designTheme.background,
            surface = designTheme.cardSurface,
            surfaceVariant = designTheme.secondarySurface,
            onPrimary = designTheme.onPrimary,
            onSecondary = Color.Black,
            onTertiary = Color.Black,
            onBackground = designTheme.textPrimary,
            onSurface = designTheme.textPrimary,
            onSurfaceVariant = designTheme.textSecondary,
            outline = designTheme.separator,
            outlineVariant = designTheme.separator.copy(alpha = 0.6f),
            error = designTheme.destructive,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = designTheme.primary,
            secondary = designTheme.accent,
            tertiary = designTheme.success,
            background = designTheme.background,
            surface = designTheme.cardSurface,
            surfaceVariant = designTheme.secondarySurface,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.White,
            onBackground = designTheme.textPrimary,
            onSurface = designTheme.textPrimary,
            onSurfaceVariant = designTheme.textSecondary,
            outline = designTheme.separator,
            outlineVariant = designTheme.separator.copy(alpha = 0.65f),
            error = designTheme.destructive,
            onError = Color.White
        )
    }

    val animatedColorScheme = animateColorScheme(dynamicColorScheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
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
