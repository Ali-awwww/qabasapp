package com.qabas.app.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Apple iOS HIG Light Palette (Clean Minimal Light)
val AppleLightBackground = Color(0xFFF2F2F7)        // iOS systemGroupedBackground
val AppleLightSurface = Color(0xFFFFFFFF)           // iOS secondarySystemGroupedBackground
val AppleLightSecondarySurface = Color(0xFFE5E5EA)  // iOS tertiaryFill / systemGray5
val AppleLightSeparator = Color(0xFFC6C6C8)         // iOS opaqueSeparator
val AppleLightLabel = Color(0xFF000000)             // iOS label
val AppleLightSecondaryLabel = Color(0xFF6C6C70)    // iOS secondaryLabel
val AppleLightTertiaryLabel = Color(0xFF8E8E93)     // iOS systemGray
val AppleLightBlue = Color(0xFF006EE6)              // WCAG-AA accessible Apple Blue
val AppleLightGreen = Color(0xFF1E7E34)             // Accessible Apple Green
val AppleLightGold = Color(0xFF9A6B00)              // Accessible Warm Islamic Gold
val AppleLightIndigo = Color(0xFF5856D6)            // Apple Indigo
val AppleLightOrange = Color(0xFFC96A00)            // Accessible Apple Orange
val AppleLightRed = Color(0xFFD92D20)               // Apple Red

// Apple iOS HIG Dark Palette (True Depth OLED & Elevated Grouped Surfaces)
val AppleDarkBackground = Color(0xFF000000)         // iOS dark systemBackground
val AppleDarkSurface = Color(0xFF1C1C1E)            // iOS dark secondarySystemGroupedBackground
val AppleDarkSecondarySurface = Color(0xFF2C2C2E)   // iOS dark tertiarySystemGroupedBackground
val AppleDarkSeparator = Color(0xFF38383A)          // iOS dark separator
val AppleDarkLabel = Color(0xFFFFFFFF)              // iOS dark label
val AppleDarkSecondaryLabel = Color(0xFF98989F)     // iOS dark secondaryLabel
val AppleDarkBlue = Color(0xFF0A84FF)               // iOS dark systemBlue
val AppleDarkGreen = Color(0xFF30D158)              // iOS dark systemGreen
val AppleDarkGold = Color(0xFFE8C547)               // Warm Qabas Dark Gold
val AppleDarkIndigo = Color(0xFF7D7AFF)             // iOS dark systemIndigo
val AppleDarkOrange = Color(0xFFFF9F0A)             // iOS dark systemOrange
val AppleDarkRed = Color(0xFFFF453A)                // iOS dark systemRed

// Cyberpunk Neon palette — shared by the default product identity.
val CyberDarkBackground = Color(0xFF050816)
val CyberDarkSurface = Color(0xFF0B1224)
val CyberDarkSecondarySurface = Color(0xFF111C35)
val CyberDarkSeparator = Color(0xFF23406D)
val CyberDarkLabel = Color(0xFFF3F7FF)
val CyberDarkSecondaryLabel = Color(0xFFA8B7D6)
val CyberDarkPrimary = Color(0xFF00F5D4)
val CyberDarkAccent = Color(0xFFFF2BD6)
val CyberDarkBlue = Color(0xFF35A7FF)
val CyberDarkGreen = Color(0xFF00E5A8)
val CyberDarkOrange = Color(0xFFFFB000)
val CyberDarkRed = Color(0xFFFF4D6D)

val CyberLightBackground = Color(0xFFF5F7FF)
val CyberLightSurface = Color(0xFFFFFFFF)
val CyberLightSecondarySurface = Color(0xFFE9EEFF)
val CyberLightSeparator = Color(0xFFB8C7E6)
val CyberLightLabel = Color(0xFF111827)
val CyberLightSecondaryLabel = Color(0xFF42516F)
val CyberLightPrimary = Color(0xFF007C73)
val CyberLightAccent = Color(0xFFB01890)

// Static Fallbacks
val StaticDeepSlate = Color(0xFF000000)
val StaticCardSurface = Color(0xFF1C1C1E)
val StaticGoldPrimary = CyberDarkPrimary
val StaticGoldSecondary = CyberDarkAccent

// Dynamic Palette (Bound reactively to ThemeManager.activePreset & isDarkState across all Compose & non-Compose contexts)
val DeepSlate: Color
    get() = ThemeManager.activePreset.backgroundColor

val CardSurface: Color
    get() = ThemeManager.activePreset.cardSurfaceColor

val ThemeSecondarySurface: Color
    get() = if (ThemeManager.activePreset == AppThemePreset.CYBER_HUD_BLUE) {
        if (ThemeManager.isDarkState) CyberDarkSecondarySurface else CyberLightSecondarySurface
    } else if (ThemeManager.isDarkState) AppleDarkSecondarySurface else AppleLightSecondarySurface

val ThemeSeparator: Color
    get() = if (ThemeManager.activePreset == AppThemePreset.CYBER_HUD_BLUE) {
        if (ThemeManager.isDarkState) CyberDarkSeparator else CyberLightSeparator
    } else if (ThemeManager.isDarkState) AppleDarkSeparator else AppleLightSeparator

val GoldPrimary: Color
    get() = ThemeManager.activePreset.primaryColor

val GoldSecondary: Color
    get() = ThemeManager.activePreset.accentColor

val ThemeSuccess: Color
    get() = if (ThemeManager.activePreset == AppThemePreset.CYBER_HUD_BLUE) CyberDarkGreen else if (ThemeManager.isDarkState) AppleDarkGreen else AppleLightGreen

val ThemeWarning: Color
    get() = if (ThemeManager.activePreset == AppThemePreset.CYBER_HUD_BLUE) CyberDarkOrange else if (ThemeManager.isDarkState) AppleDarkOrange else AppleLightOrange

val ThemeDestructive: Color
    get() = if (ThemeManager.activePreset == AppThemePreset.CYBER_HUD_BLUE) CyberDarkRed else if (ThemeManager.isDarkState) AppleDarkRed else AppleLightRed

val TextPrimary: Color
    get() = if (ThemeManager.activePreset == AppThemePreset.CYBER_HUD_BLUE) {
        if (ThemeManager.isDarkState) CyberDarkLabel else CyberLightLabel
    } else if (ThemeManager.isDarkState) AppleDarkLabel else AppleLightLabel

val TextSecondary: Color
    get() = if (ThemeManager.activePreset == AppThemePreset.CYBER_HUD_BLUE) {
        if (ThemeManager.isDarkState) CyberDarkSecondaryLabel else CyberLightSecondaryLabel
    } else if (ThemeManager.isDarkState) AppleDarkSecondaryLabel else AppleLightSecondaryLabel

val MushafCream = Color(0xFFFFFEF7)
val MushafBorder = Color(0xFFD4AF37).copy(alpha = 0.45f)
val MushafHeaderGold = Color(0xFFB8860B)
val MushafDeepRed = Color(0xFF8B0000)

val AntiqueParchment = Color(0xFFF4EAD5)
val OldInk = Color(0xFF2C1E12)
val ManuscriptGold = Color(0xFFB8860B)
val AgedPaper = Color(0xFFE8DCC4)
val DeepLibraryBlue = Color(0xFF1A2238)

// Adaptive Accent Tokens (Cyberpunk-aware while preserving non-Cyber presets)
val AiCyan: Color
    get() = if (ThemeManager.activePreset == AppThemePreset.CYBER_HUD_BLUE) {
        if (ThemeManager.isDarkState) CyberDarkBlue else CyberLightPrimary
    } else if (ThemeManager.isDarkState) AppleDarkBlue else AppleLightBlue
val AiCyanLight: Color
    get() = if (ThemeManager.activePreset == AppThemePreset.CYBER_HUD_BLUE) {
        if (ThemeManager.isDarkState) CyberDarkBlue else CyberLightPrimary
    } else if (ThemeManager.isDarkState) Color(0xFF5AC8FA) else AppleLightBlue
val AiTeal: Color
    get() = if (ThemeManager.activePreset == AppThemePreset.CYBER_HUD_BLUE) {
        if (ThemeManager.isDarkState) CyberDarkPrimary else CyberLightPrimary
    } else if (ThemeManager.isDarkState) Color(0xFF40CBE0) else Color(0xFF0A8499)
val AiViolet: Color
    get() = if (ThemeManager.activePreset == AppThemePreset.CYBER_HUD_BLUE) {
        if (ThemeManager.isDarkState) CyberDarkAccent else CyberLightAccent
    } else if (ThemeManager.isDarkState) AppleDarkIndigo else AppleLightIndigo
val AiVioletLight: Color
    get() = if (ThemeManager.activePreset == AppThemePreset.CYBER_HUD_BLUE) {
        if (ThemeManager.isDarkState) CyberDarkAccent else CyberLightAccent
    } else if (ThemeManager.isDarkState) Color(0xFFA5A3FF) else Color(0xFF5856D6)
val AiVioletDeep: Color
    get() = Color(0xFF4B49B6)
val AiGlowBlue: Color
    get() = AiCyan
val AiNeonGreen: Color
    get() = ThemeSuccess
val AiDanger: Color
    get() = ThemeDestructive

// Light Palette Constants (Preserved for compatibility)
val LightBackground = AppleLightBackground
val LightSurface = AppleLightSurface
val LightCardSurface = AppleLightSecondarySurface
val LightTextPrimary = AppleLightLabel
val LightTextSecondary = AppleLightSecondaryLabel
val LightBorder = AppleLightSeparator
