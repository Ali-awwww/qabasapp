package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleSecondaryButton
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.QabasTheme
import com.qabas.app.ui.theme.TajawalFont
import kotlinx.coroutines.launch

/**
 * Secure Account Settings — Apple HIG Light & Dark.
 * Passwords are stored locally only as salted SHA-256 hashes; cloud roles come exclusively from Supabase.
 */
@Composable
fun AccountSettingsSection(context: Context) {
    val theme = QabasTheme.current
    val prefs = remember { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }
    // Batch 6 Priority 3: the "2FA" switch was removed entirely. It was only
    // a local boolean preference that admitted it was just a reminder — a
    // fake security control. No MFA is claimed anywhere now.
    // Real Supabase TOTP enrolment is a separate follow-up, not half-built here.
    // ق20: وحُذف معها حقل البريد (كان يكتب أي بريد في مفتاح الهوية العام
    // user_email بلا لمس الجلسة فينشطر الهوية) وحقلا «رمز القفل المحلي»
    // (كانا يحفظان تجزئة لا يقرؤها شيء في الريبو كله — ضبط أمني بلا أثر).
    var isTransactionNotificationsEnabled by remember {
        mutableStateOf(prefs.getBoolean("dev_transaction_notifications", false))
    }
    var isSyncingRole by remember { mutableStateOf(false) }
    val cloudUser by CloudServices.Auth.currentUser.collectAsState()
    val isAdmin = cloudUser?.isAdmin == true
    val syncCtx = LocalContext.current
    val syncScope = rememberCoroutineScope()

    AppleCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "بيانات حساب المطور الرئيسي",
                color = theme.textPrimary,
                fontFamily = TajawalFont,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            Surface(
                color = theme.destructive.copy(alpha = if (theme.isDark) 0.14f else 0.08f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, theme.destructive.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = theme.destructive,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "تنبيه أمني",
                            color = theme.destructive,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "لا تُخزَّن كلمات المرور في الكود المصدري. يُفضّل الاعتماد على حساب Supabase المصادق عليه لإدارة الصلاحيات السحابية.",
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = theme.separator.copy(alpha = 0.5f))

            Text(
                text = "الأمان والحماية",
                color = theme.primary,
                fontFamily = TajawalFont,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "إشعارات العمليات الجديدة",
                        color = theme.textPrimary,
                        fontFamily = TajawalFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "إشعار فوري عند تسجيل معاملة جديدة داخل التطبيق.",
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = isTransactionNotificationsEnabled,
                    onCheckedChange = {
                        isTransactionNotificationsEnabled = it
                        prefs.edit().putBoolean("dev_transaction_notifications", it).apply()
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "الرتبة الإدارية والتحقق",
                        color = theme.textPrimary,
                        fontFamily = TajawalFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "الصلاحيات الإدارية مرتبطة حصرياً بجلسة Supabase الموثقة.",
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp
                    )
                }
                Surface(
                    color = if (isAdmin) theme.primary.copy(alpha = 0.14f) else theme.secondarySurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.5.dp, if (isAdmin) theme.primary.copy(alpha = 0.5f) else theme.separator)
                ) {
                    Text(
                        text = if (isAdmin) "مسؤول (Admin)" else "مستخدم قياسي",
                        color = if (isAdmin) theme.primary else theme.textSecondary,
                        fontFamily = TajawalFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            AppleSecondaryButton(
                text = if (isSyncingRole) "جاري التحقق..." else "تحديث الصلاحيات من سحابة Supabase",
                icon = Icons.Default.CloudSync,
                enabled = !isSyncingRole && SupabaseConfig.isConfigured,
                onClick = {
                    syncScope.launch {
                        isSyncingRole = true
                        try {
                            CloudServices.syncAdminClaim(syncCtx)
                            Toast.makeText(syncCtx, "تمت مزامنة الصلاحيات السحابية", Toast.LENGTH_SHORT).show()
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                            Toast.makeText(syncCtx, "تعذرت المزامنة: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally {
                            isSyncingRole = false
                        }
                    }
                }
            )


            HorizontalDivider(thickness = 0.5.dp, color = theme.separator.copy(alpha = 0.5f))

            // Batch 6 Priority 3 — real account deletion (export-first,
            // confirmation, server-side Auth-user deletion). The About screen
            // claim now matches this: deletion is implemented, not asserted.
            // ق20: تُعرض للمسجَّلين فقط (عقد البطاقة نفسها) — كما في
            // SettingsScreen؛ ولوحة المطور إدارية أصلاً لكن البوابة هنا صريحة.
            if (SupabaseServices.Auth.getCurrentUserId() != null) {
                AccountDeletionCard()
            }
        }
    }
}
