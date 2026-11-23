package com.qabas.app

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.components.AppleSecondaryButton
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.QabasTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Batch 6 Priority 3 — email verification.
 *
 * The state comes only from the real Supabase Auth user object
 * (`emailConfirmedAt`, see `SupabaseServices.Auth.isEmailConfirmed`): an
 * address is "confirmed" only when Supabase says so. The UI never shows a
 * fake "verified" state.
 *
 * Server-side note: the client cannot read the project-side "Confirm email"
 * switch. If the owner disabled confirmation server-side, Supabase confirms
 * every new address immediately, so `isEmailConfirmed()` simply always
 * reports true and no prompt ever appears. That is the intended degrade —
 * not a state the app can or should claim to detect.
 *
 * While unconfirmed: local use continues normally, cloud sync is blocked by
 * the gate in `UserPreferencesManager.isCloudSyncBlockedByUnconfirmedEmail`,
 * and this banner/dialog is the only place that talks about verification.
 */
/**
 * Honest "your email is not confirmed yet" banner. Shown to signed-in users
 * with an unconfirmed address (e.g. in Settings). Local use continues;
 * cloud sync stays blocked until confirmation.
 */
@Composable
fun EmailVerificationBanner(
    email: String,
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isResending by remember { mutableStateOf(false) }
    var isRechecking by remember { mutableStateOf(false) }
    var lastResendAtMillis by remember { mutableStateOf(0L) }
    var resendSucceeded by remember { mutableStateOf(false) }
    val nowMillis = System.currentTimeMillis()
    val resendCooldownActive = nowMillis - lastResendAtMillis < 60_000L

    Surface(
        color = theme.warning.copy(alpha = if (theme.isDark) 0.16f else 0.10f),
        border = BorderStroke(0.5.dp, theme.warning.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MarkEmailRead, contentDescription = null, tint = theme.warning, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "تحقق من بريدك الإلكتروني",
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                )
            }
            Text(
                text = "بريدك ($email) غير مؤكد بعد. يمكنك استخدام التطبيق محلياً بشكل طبيعي، لكن المزامنة السحابية متوقفة حتى تؤكد بريدك من الرسالة التي أرسلناها إليك.",
                color = theme.textPrimary,
                fontFamily = NotoSansFont,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        if (isResending || resendCooldownActive) return@OutlinedButton
                        isResending = true
                        scope.launch {
                            val sent = SupabaseServices.Auth.resendSignupConfirmation(email)
                            isResending = false
                            if (sent) {
                                lastResendAtMillis = System.currentTimeMillis()
                                resendSucceeded = true
                                Toast.makeText(context, "أُرسلت رسالة التحقق إلى بريدك", Toast.LENGTH_LONG).show()
                                // مهلة الإعادة 60 ثانية: تُعيد تفعيل الزر تلقائياً عند
                                // انتهائها، لأن الشاشة لا يعاد تركيبها من تلقاء نفسها
                                // فتبقى «resendCooldownActive» عالقة على «true» إلى الأبد.
                                delay(60_000)
                                lastResendAtMillis = 0L
                            } else {
                                Toast.makeText(context, "تعذرت إعادة الإرسال. انتظر دقيقة ثم حاول مجدداً.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isResending && !resendCooldownActive,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                    border = BorderStroke(1.dp, theme.warning.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = when {
                            isResending -> "جاري الإرسال..."
                            resendCooldownActive -> "أُرسلت — أعد المحاولة بعد دقيقة"
                            resendSucceeded -> "أُرسلت — إعادة الإرسال"
                            else -> "إعادة إرسال رسالة التحقق"
                        },
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                OutlinedButton(
                    onClick = {
                        if (isRechecking) return@OutlinedButton
                        isRechecking = true
                        scope.launch {
                            val confirmed = SupabaseServices.Auth.refreshEmailConfirmationState()
                            isRechecking = false
                            Toast.makeText(
                                context,
                                if (confirmed == true) "تم تأكيد بريدك — المزامنة السحابية مفعّلة الآن"
                                else "لم يظهر تأكيد بعد — افتح رابط الرسالة في بريدك ثم أعد الفحص",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    enabled = !isRechecking,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                    border = BorderStroke(1.dp, theme.warning.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (isRechecking) "جاري الفحص..." else "تحققت — أعد الفحص",
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Post-registration verification dialog. Shown when registration completed
 * (or was requested) but the address is still unconfirmed: the user must
 * open the confirmation link in their email before signing in.
 */
@Composable
fun EmailVerificationPromptDialog(
    email: String,
    onDismiss: () -> Unit,
    onGoToLogin: () -> Unit
) {
    val theme = QabasTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isResending by remember { mutableStateOf(false) }
    var lastResendAtMillis by remember { mutableStateOf(0L) }
    val resendCooldownActive = System.currentTimeMillis() - lastResendAtMillis < 60_000L

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Email, contentDescription = null, tint = theme.primary, modifier = Modifier.size(28.dp)) },
        title = {
            Text(
                text = "تحقق من بريدك الإلكتروني",
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "أرسلنا رسالة تأكيد إلى $email. افتحها واضغط رابط التأكيد، ثم سجّل الدخول. لن تكتمل المزامنة السحابية قبل التأكيد.",
                    color = theme.textPrimary,
                    fontFamily = NotoSansFont,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "إن لم تصلك الرسالة، تحقق من مجلد الرسائل غير المرغوبة أو أعد إرسالها من هنا.",
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            ApplePrimaryButton(
                // ق85: أثناء مهلة الـ 60 ثانية كان الزر معطلاً بلافتة
                // «إعادة إرسال» نفسها — بلا أي تفسير للتعطيل؛ اللافتة
                // الآن كالبانر («أُرسلت — أعد المحاولة بعد دقيقة»).
                text = when {
                    isResending -> "جاري الإرسال..."
                    resendCooldownActive -> "أُرسلت — أعد المحاولة بعد دقيقة"
                    else -> "إعادة إرسال رسالة التحقق"
                },
                isLoading = isResending,
                enabled = !isResending && !resendCooldownActive,
                onClick = {
                    isResending = true
                    scope.launch {
                        val sent = SupabaseServices.Auth.resendSignupConfirmation(email)
                        isResending = false
                        if (sent) {
                            lastResendAtMillis = System.currentTimeMillis()
                            Toast.makeText(context, "أُرسلت رسالة التحقق إلى بريدك", Toast.LENGTH_LONG).show()
                            // مهلة الإعادة 60 ثانية: تُعيد تفعيل الزر تلقائياً عند
                            // انتهائها، لأن الحوار لا يعاد تركيبه من تلقاء نفسه
                            // فتبقى «resendCooldownActive» عالقة على «true» إلى الأبد.
                            delay(60_000)
                            lastResendAtMillis = 0L
                        } else {
                            Toast.makeText(context, "تعذرت إعادة الإرسال. انتظر دقيقة ثم حاول مجدداً.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        },
        dismissButton = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppleSecondaryButton(
                    text = "الانتقال لتسجيل الدخول",
                    onClick = onGoToLogin
                )
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("إغلاق", color = theme.textSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                }
            }
        },
        containerColor = theme.cardSurface
    )
}
