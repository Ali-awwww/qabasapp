package com.qabas.app

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * تقنيع البريد الإلكتروني في لوحة المطور (الدفعة 5 — قسم 3-ي من تقرير
 * الرئيسية ولوحة المطور).
 *
 * القاعدة: لا بريد كامل ظاهر أبداً في أي قائمة أو لقطة شاشة أو تصدير إلا
 * بطلب صريح ومسجَّل في التدقيق. التقنيع افتراضي، والإظهار مسجَّل.
 *
 * الصيغة: الحرف الأول من الجزء المحلي + "•••" + "@" + النطاق، مثلاً:
 *   "ali@example.com" -> "a•••@example.com"
 * النطاق نفسه لا يُخفى: يكفي لتمييز الحساب دون كشف هوية صاحبه كاملة،
 * وهو ما تفعله لوحات مثل Stripe Dashboard.
 */

/** الحرف الأول + ••• + @النطاق. أي بريد بلا @ أو فارغ يُقنَّع كاملاً. */
fun maskEmail(email: String?): String {
    val clean = email?.trim().orEmpty()
    if (clean.isBlank()) return "•••"
    val at = clean.indexOf('@')
    if (at <= 0) return "•••"
    val domain = clean.substring(at + 1).trim()
    if (domain.isBlank()) return "•••"
    return "${clean.first()}•••@$domain"
}

/**
 * يكشف البريد الكامل بعد تسجيل الكشف في التدقيق — ويجب استدعاؤه قبل إظهار
 * البريد بأي شكل (نص، نسخ، تصدير).
 *
 * ما يُسجَّل في التدقيق هو البريد *المقنَّع* + السبب فقط، لا البريد الكامل:
 * تسجيل البريد الكامل في السجل كان سيهزم الغرض من التقنيع. الفاعل يُؤخذ
 * من [AuditLogger] نفسه (هوية Supabase الحالية).
 *
 * @param reason سبب الإظهار الحر (مثال: "الرد على رسالة دعم"، "مراجعة حساب").
 * @return البريد الكامل كما هو (أو "" إن كان فارغاً).
 */
fun revealEmail(context: Context, email: String?, reason: String): String {
    val clean = email?.trim().orEmpty()
    AuditLogger.log(
        context = context,
        action = "email_reveal",
        detail = "reason=$reason :: ${maskEmail(clean)}"
    )
    return clean
}

/**
 * نص بريد مقنَّع مع زر «إظهار/إخفاء». النقر على «إظهار» يسجِّل الكشف في
 * التدقيق عبر [revealEmail] قبل عرض البريد الكامل، والنقر على «إخفاء»
 * يعيده مقنَّعاً فوراً. استخدم هذا المكوّن في كل قائمة تعرض بريد مستخدم.
 */
@Composable
fun MaskedEmailText(
    email: String?,
    reason: String,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF94A3B8),
    fontFamily: FontFamily? = null,
    fontSize: TextUnit = 11.sp
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var revealed by remember(email) { mutableStateOf<String?>(null) }
    Row(modifier = modifier) {
        Text(
            text = revealed ?: maskEmail(email),
            color = color,
            fontFamily = fontFamily,
            fontSize = fontSize,
            maxLines = 1
        )
        Text(
            text = if (revealed == null) "  إظهار" else "  إخفاء",
            color = Color(0xFFD4A017),
            fontFamily = fontFamily,
            fontSize = fontSize,
            maxLines = 1,
            modifier = Modifier.clickable {
                revealed = if (revealed == null) {
                    revealEmail(context, email, reason)
                } else {
                    null
                }
            }
        )
    }
}
