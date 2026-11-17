package com.qabas.app.ui.theme

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.qabas.app.R

private val AmiriFontBase = FontFamily(
    Font(R.font.amiri_regular, weight = FontWeight.Normal),
    Font(R.font.amiri_regular, weight = FontWeight.Bold)
)

private val CairoFontBase = FontFamily(
    Font(R.font.cairo_regular, weight = FontWeight.Normal),
    Font(R.font.cairo_regular, weight = FontWeight.Bold)
)

private val TajawalFontBase = FontFamily(
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

val AmiriFont = AmiriFontBase
val RobotoMonoFont = FontFamily.Monospace

/** خيارات الخط العام لواجهة قبس، وليست خطوط المصحف المتخصصة. */
enum class AppFontChoice(
    val id: String,
    val title: String,
    val description: String,
    val family: FontFamily
) {
    TAJAWAL("tajawal", "تجوال", "حديث وواضح ومريح للقراءة اليومية", TajawalFontBase),
    CAIRO("cairo", "القاهرة", "متوازن وأنيق للعناوين والمحتوى", CairoFontBase),
    AMIRI("amiri", "أميري", "طابع عربي أصيل ولمسة أدبية", AmiriFontBase)
}

object FontManager {
    private const val PREF_KEY = "app_font_choice"

    var current by mutableStateOf(AppFontChoice.TAJAWAL)
        private set

    fun init(context: Context) {
        val savedId = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .getString(PREF_KEY, AppFontChoice.TAJAWAL.id)
        current = AppFontChoice.entries.firstOrNull { it.id == savedId } ?: AppFontChoice.TAJAWAL
    }

    fun apply(context: Context, choice: AppFontChoice) {
        current = choice
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_KEY, choice.id)
            .apply()
    }
}

val CairoFont: FontFamily get() = FontManager.current.family
val TajawalFont: FontFamily get() = FontManager.current.family
val NotoSansFont: FontFamily get() = FontManager.current.family

// Apple HIG Dynamic Type Hierarchy calibrated with generous Arabic line heights (prevents tashkeel clipping)
val Typography: Typography
    get() = Typography(
        displayLarge = TextStyle(fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 46.sp),
        displayMedium = TextStyle(fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 38.sp),
        displaySmall = TextStyle(fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 34.sp),
        headlineLarge = TextStyle(fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 32.sp),
        headlineMedium = TextStyle(fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 29.sp),
        headlineSmall = TextStyle(fontFamily = CairoFont, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 26.sp),
        titleLarge = TextStyle(fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = TajawalFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
        titleSmall = TextStyle(fontFamily = NotoSansFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 21.sp),
        bodyLarge = TextStyle(fontFamily = TajawalFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 25.sp),
        bodyMedium = TextStyle(fontFamily = NotoSansFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
        bodySmall = TextStyle(fontFamily = NotoSansFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontFamily = NotoSansFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium = TextStyle(fontFamily = NotoSansFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = TextStyle(fontFamily = NotoSansFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp)
    )
