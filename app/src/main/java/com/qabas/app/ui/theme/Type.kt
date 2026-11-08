package com.qabas.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.Font
import com.qabas.app.R

val AmiriFont = FontFamily(
    Font(R.font.amiri_regular, weight = FontWeight.Normal),
    Font(R.font.amiri_regular, weight = FontWeight.Bold)
)

val CairoFont = FontFamily(
    Font(R.font.cairo_regular, weight = FontWeight.Normal),
    Font(R.font.cairo_regular, weight = FontWeight.Bold)
)

val TajawalFont = FontFamily(
    Font(R.font.tajawal_regular, weight = FontWeight.Normal),
    Font(R.font.tajawal_regular, weight = FontWeight.Bold)
)

val ReemKufiFont = FontFamily(
    Font(R.font.reem_kufi_regular, weight = FontWeight.Normal),
    Font(R.font.reem_kufi_regular, weight = FontWeight.Bold)
)

val ScheherazadeFont = FontFamily(
    Font(R.font.scheherazade_regular, weight = FontWeight.Normal),
    Font(R.font.scheherazade_regular, weight = FontWeight.Bold)
)

val ArefRuqaaFont = FontFamily(
    Font(R.font.aref_ruqaa_regular, weight = FontWeight.Normal),
    Font(R.font.aref_ruqaa_regular, weight = FontWeight.Bold)
)

val RobotoMonoFont = FontFamily.Monospace
val NotoSansFont = CairoFont

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
