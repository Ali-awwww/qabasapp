package com.qabas.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.Font
import com.qabas.app.R

val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val NotoSansFont = FontFamily(
    Font(googleFont = GoogleFont("Noto Sans Arabic"), fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("Noto Sans Arabic"), fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = GoogleFont("Noto Sans"), fontProvider = provider, weight = FontWeight.Normal), // Fallback
    Font(googleFont = GoogleFont("Noto Sans"), fontProvider = provider, weight = FontWeight.Medium)
)

val TajawalFont = FontFamily(
    Font(googleFont = GoogleFont("Tajawal"), fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("Tajawal"), fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = GoogleFont("Tajawal"), fontProvider = provider, weight = FontWeight.Bold)
)

val CairoFont = FontFamily(
    Font(googleFont = GoogleFont("Cairo"), fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("Cairo"), fontProvider = provider, weight = FontWeight.Bold)
)

val AmiriFont = FontFamily(
    Font(googleFont = GoogleFont("Amiri"), fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("Amiri"), fontProvider = provider, weight = FontWeight.Bold)
)

val ReemKufiFont = FontFamily(
    Font(googleFont = GoogleFont("Reem Kufi"), fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("Reem Kufi"), fontProvider = provider, weight = FontWeight.Bold)
)

val RobotoMonoFont = FontFamily(
    Font(googleFont = GoogleFont("Roboto Mono"), fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("Roboto Mono"), fontProvider = provider, weight = FontWeight.Medium)
)

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = CairoFont, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontFamily = CairoFont, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = CairoFont, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = TajawalFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = NotoSansFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = NotoSansFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = NotoSansFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = NotoSansFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = NotoSansFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
)
