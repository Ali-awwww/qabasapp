package com.qabas.app.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Static Fallbacks
val StaticDeepSlate = Color(0xFF0B0F19)
val StaticCardSurface = Color(0xFF151B2B)
val StaticGoldPrimary = Color(0xFFE8C547)
val StaticGoldSecondary = Color(0xFFF5D76E)

// Dynamic Palette (Bound reactively to ThemeManager.activePreset across all Compose & Non-Compose contexts)
val DeepSlate: Color
    get() = ThemeManager.activePreset.backgroundColor

val CardSurface: Color
    get() = ThemeManager.activePreset.cardSurfaceColor

val GoldPrimary: Color
    get() = ThemeManager.activePreset.primaryColor

val GoldSecondary: Color
    get() = ThemeManager.activePreset.accentColor

val TextPrimary: Color
    get() = if (ThemeManager.activePreset.isDark) Color(0xFFF8FAFC) else Color(0xFF0B0F19)

val TextSecondary: Color
    get() = if (ThemeManager.activePreset.isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

val MushafCream = Color(0xFFFFFEF7)
val MushafBorder = Color(0xFFE8C547).copy(alpha = 0.5f)
val MushafHeaderGold = Color(0xFFD4AF37)
val MushafDeepRed = Color(0xFF8B0000)

val AntiqueParchment = Color(0xFFF4EAD5)
val OldInk = Color(0xFF2C1E12)
val ManuscriptGold = Color(0xFFB8860B)
val AgedPaper = Color(0xFFE8DCC4)
val DeepLibraryBlue = Color(0xFF1A2238)

// AI Tech Accents (Sept 2026)
val AiCyan = Color(0xFF22D3EE)
val AiCyanLight = Color(0xFF67E8F9)
val AiTeal = Color(0xFF2DD4BF)
val AiViolet = Color(0xFF8B5CF6)
val AiVioletLight = Color(0xFFA78BFA)
val AiVioletDeep = Color(0xFF6D28D9)
val AiGlowBlue = Color(0xFF3B82F6)
val AiNeonGreen = Color(0xFF10B981)
val AiDanger = Color(0xFFF87171)

// Light Palette (Refined Light Mode with Islamic Warm Neutral / Gold Accents)
val LightBackground = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightCardSurface = Color(0xFFF1F5F9)
val LightTextPrimary = Color(0xFF0B0F19)
val LightTextSecondary = Color(0xFF64748B)
val LightBorder = Color(0xFFE2E8F0)
