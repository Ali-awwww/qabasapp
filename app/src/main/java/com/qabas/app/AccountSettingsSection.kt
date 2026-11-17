package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.ApplePrimaryButton
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
    var email by remember {
        mutableStateOf(prefs.getString("user_email", "") ?: "")
    }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var is2FAEnabled by remember { mutableStateOf(prefs.getBoolean("dev_2fa_enabled", true)) }
    var isTransactionNotificationsEnabled by remember {
        mutableStateOf(prefs.getBoolean("dev_transaction_notifications", false))
    }
    var isSyncingRole by remember { mutableStateOf(false) }
    val cloudUser by CloudServices.Auth.currentUser.collectAsState()
    val isAdmin = cloudUser?.isAdmin == true
    val syncCtx = LocalContext.current
    val syncScope = rememberCoroutineScope()

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = theme.primary,
        unfocusedBorderColor = theme.separator,
        focusedTextColor = theme.textPrimary,
        unfocusedTextColor = theme.textPrimary,
        focusedLabelColor = theme.primary,
        unfocusedLabelColor = theme.textSecondary
    )

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

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("البريد الإلكتروني للمطور", fontFamily = CairoFont) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = fieldColors,
                shape = RoundedCornerShape(12.dp)
            )

            Text(
                text = "تغيير رمز القفل المحلي (اختياري)",
                color = theme.primary,
                fontFamily = TajawalFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = "اترك الحقول فارغة إذا لم ترد تغيير الرمز. الحد الأدنى 8 أحرف.",
                color = theme.textSecondary,
                fontFamily = NotoSansFont,
                fontSize = 12.sp
            )

            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("الرمز الجديد", fontFamily = CairoFont) },
                visualTransformation = if (showNewPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { showNewPassword = !showNewPassword }) {
                        Icon(
                            if (showNewPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "إظهار أو إخفاء الرمز",
                            tint = theme.textSecondary
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = fieldColors,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("تأكيد الرمز الجديد", fontFamily = CairoFont) },
                visualTransformation = if (showConfirmPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                        Icon(
                            if (showConfirmPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "إظهار أو إخفاء التأكيد",
                            tint = theme.textSecondary
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = fieldColors,
                shape = RoundedCornerShape(12.dp)
            )

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
                        text = "المصادقة الثنائية (2FA)",
                        color = theme.textPrimary,
                        fontFamily = TajawalFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "تذكير محلي بتفعيل المصادقة الثنائية على حساب السحابة والبريد.",
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = is2FAEnabled,
                    onCheckedChange = {
                        is2FAEnabled = it
                        prefs.edit().putBoolean("dev_2fa_enabled", it).apply()
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
                        } catch (e: Exception) {
                            Toast.makeText(syncCtx, "تعذرت المزامنة: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally {
                            isSyncingRole = false
                        }
                    }
                }
            )

            ApplePrimaryButton(
                text = "حفظ التغييرات",
                onClick = {
                    val emailTrim = email.trim()
                    if (emailTrim.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(emailTrim).matches()) {
                        Toast.makeText(context, "صيغة البريد الإلكتروني غير صحيحة", Toast.LENGTH_SHORT).show()
                        return@ApplePrimaryButton
                    }

                    val wantsPasswordChange = newPassword.isNotEmpty() || confirmPassword.isNotEmpty()
                    if (wantsPasswordChange) {
                        when {
                            newPassword.length < 8 -> {
                                Toast.makeText(context, "الرمز يجب أن يكون 8 أحرف على الأقل", Toast.LENGTH_SHORT).show()
                                return@ApplePrimaryButton
                            }
                            newPassword != confirmPassword -> {
                                Toast.makeText(context, "الرمزان غير متطابقين", Toast.LENGTH_SHORT).show()
                                return@ApplePrimaryButton
                            }
                        }
                        val salt = java.util.UUID.randomUUID().toString().take(16)
                        val digest = java.security.MessageDigest.getInstance("SHA-256")
                        val hashBytes = digest.digest((salt + newPassword).toByteArray(Charsets.UTF_8))
                        val hashHex = hashBytes.joinToString("") { "%02x".format(it) }
                        prefs.edit()
                            .putString("dev_password_salt", salt)
                            .putString("dev_password_hash", hashHex)
                            .apply()
                        newPassword = ""
                        confirmPassword = ""
                    }

                    prefs.edit().putString("user_email", emailTrim).apply()
                    Toast.makeText(
                        context,
                        if (wantsPasswordChange) "تم حفظ البريد وتحديث الرمز محلياً" else "تم حفظ بيانات الحساب",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }
}
